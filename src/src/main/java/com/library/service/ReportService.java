package com.library.service;

import com.library.model.Copy;
import com.library.model.CopyStatus;
import com.library.model.Fine;
import com.library.model.Item;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.MembershipType;
import com.library.repository.DataContext;
import com.library.repository.FineRepository;
import com.library.repository.ItemRepository;
import com.library.repository.LoanRepository;
import com.library.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates analytical library reports: overdue loans, popular items,
 * member loan history, and collection & financial summaries.
 */
public class ReportService {
    private final ItemRepository items;
    private final UserRepository users;
    private final LoanRepository loans;
    private final FineRepository fines;
    private final Clock clock;

    public ReportService(DataContext ctx, Clock clock) {
        this(ctx.items(), ctx.users(), ctx.loans(), ctx.fines(), clock);
    }

    public ReportService(ItemRepository items, UserRepository users,
                         LoanRepository loans, FineRepository fines, Clock clock) {
        this.items = Objects.requireNonNull(items, "items repository required");
        this.users = Objects.requireNonNull(users, "users repository required");
        this.loans = Objects.requireNonNull(loans, "loans repository required");
        this.fines = Objects.requireNonNull(fines, "fines repository required");
        this.clock = Objects.requireNonNull(clock, "clock required");
    }

    public record OverdueEntry(
            String loanId,
            String memberId,
            String memberName,
            String itemId,
            String itemTitle,
            String copyId,
            LocalDate dueDate,
            long daysOverdue
    ) { }

    public record PopularItemEntry(
            String itemId,
            String title,
            String category,
            long borrowCount
    ) { }

    public record MemberLoanHistoryEntry(
            String loanId,
            String itemId,
            String itemTitle,
            String copyId,
            LocalDate borrowDate,
            LocalDate dueDate,
            LocalDate returnDate,
            String status
    ) { }

    public record CollectionSummary(
            long totalTitles,
            long activeTitles,
            long retiredTitles,
            long totalCopies,
            long availableCopies,
            long onLoanCopies,
            long onHoldCopies,
            long lostCopies,
            double utilizationPercent,
            long totalMembers,
            long activeMembers,
            long activeLoans,
            long overdueLoans,
            BigDecimal totalUnpaidFines,
            BigDecimal totalCollectedFines
    ) { }

    public List<OverdueEntry> getOverdueLoans() {
        LocalDate today = LocalDate.now(clock);
        return loans.findActive().stream()
                .filter(l -> l.getDueDate() != null && l.getDueDate().isBefore(today))
                .sorted(Comparator.comparing(Loan::getDueDate))
                .map(l -> {
                    String memberName = users.findById(l.getMemberId())
                            .map(u -> u.getName())
                            .orElse("Unknown");
                    String title = items.findById(l.getItemId())
                            .map(Item::getTitle)
                            .orElse("Unknown Item");
                    long days = ChronoUnit.DAYS.between(l.getDueDate(), today);
                    return new OverdueEntry(
                            l.getLoanId(), l.getMemberId(), memberName,
                            l.getItemId(), title, l.getCopyId(), l.getDueDate(), days
                    );
                })
                .toList();
    }

    public List<PopularItemEntry> getPopularItems(int topN) {
        if (topN <= 0) {
            return List.of();
        }
        Map<String, Long> countsByItem = loans.findAll().stream()
                .collect(Collectors.groupingBy(Loan::getItemId, Collectors.counting()));

        return countsByItem.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(topN)
                .map(e -> {
                    Item item = items.findById(e.getKey()).orElse(null);
                    String title = item != null ? item.getTitle() : "Unknown Item";
                    String category = item != null ? item.getCategory() : "-";
                    return new PopularItemEntry(e.getKey(), title, category, e.getValue());
                })
                .toList();
    }

    public List<MemberLoanHistoryEntry> getMemberLoanHistory(String memberId) {
        return loans.findByMemberId(memberId).stream()
                .sorted(Comparator.comparing(Loan::getBorrowDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(l -> {
                    String title = items.findById(l.getItemId())
                            .map(Item::getTitle)
                            .orElse("Unknown Item");
                    return new MemberLoanHistoryEntry(
                            l.getLoanId(), l.getItemId(), title, l.getCopyId(),
                            l.getBorrowDate(), l.getDueDate(), l.getReturnDate(),
                            l.getStatus().name()
                    );
                })
                .toList();
    }

    public Map<String, Long> getCategoryBreakdown() {
        return items.findAll().stream()
                .filter(i -> !i.isRetired())
                .collect(Collectors.groupingBy(
                        i -> i.getCategory() == null ? "Uncategorized" : i.getCategory(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public CollectionSummary getCollectionSummary() {
        LocalDate today = LocalDate.now(clock);
        List<Item> allItems = items.findAll();
        long totalTitles = allItems.size();
        long retiredTitles = allItems.stream().filter(Item::isRetired).count();
        long activeTitles = totalTitles - retiredTitles;

        List<Copy> allCopies = allItems.stream().flatMap(i -> i.getCopies().stream()).toList();
        long totalCopies = allCopies.size();
        long availableCopies = allCopies.stream().filter(c -> c.getStatus() == CopyStatus.AVAILABLE).count();
        long onLoanCopies = allCopies.stream().filter(c -> c.getStatus() == CopyStatus.ON_LOAN).count();
        long onHoldCopies = allCopies.stream().filter(c -> c.getStatus() == CopyStatus.ON_HOLD).count();
        long lostCopies = allCopies.stream().filter(c -> c.getStatus() == CopyStatus.LOST).count();

        long lendableCopies = totalCopies - lostCopies - allCopies.stream()
                .filter(c -> c.getStatus() == CopyStatus.RETIRED).count();
        double utilization = lendableCopies == 0 ? 0.0
                : BigDecimal.valueOf((onLoanCopies * 100.0) / lendableCopies)
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

        List<Member> members = users.findAllMembers();
        long totalMembers = members.size();
        long activeMembers = members.stream().filter(Member::isActive).count();

        List<Loan> activeLoansList = loans.findActive();
        long activeLoans = activeLoansList.size();
        long overdueLoans = activeLoansList.stream()
                .filter(l -> l.getDueDate() != null && l.getDueDate().isBefore(today))
                .count();

        List<Fine> allFines = fines.findAll();
        BigDecimal totalUnpaid = allFines.stream()
                .filter(Fine::isUnpaid)
                .map(Fine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollected = allFines.stream()
                .filter(f -> !f.isUnpaid())
                .map(Fine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CollectionSummary(
                totalTitles, activeTitles, retiredTitles,
                totalCopies, availableCopies, onLoanCopies, onHoldCopies, lostCopies,
                utilization, totalMembers, activeMembers, activeLoans, overdueLoans,
                totalUnpaid, totalCollected
        );
    }

    public static int maxLoansFor(MembershipType type) {
        if (type == null) return 3;
        return switch (type) {
            case STUDENT -> 5;
            case STAFF -> 10;
            case SENIOR -> 4;
        };
    }
}
