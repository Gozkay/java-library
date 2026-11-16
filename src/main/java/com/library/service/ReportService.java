package com.library.service;

import com.library.model.*;
import com.library.policy.MembershipPolicy;
import com.library.repository.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReportService {

    public record OverdueLoanEntry(
            String loanId,
            String memberId,
            String memberName,
            String itemId,
            String itemTitle,
            LocalDate dueDate,
            long daysOverdue,
            BigDecimal estimatedFine
    ) {}

    public record TopBorrowedEntry(
            String itemId,
            String title,
            String itemType,
            long borrowCount
    ) {}

    public record UnpaidFineSummary(
            String memberId,
            String memberName,
            int unpaidCount,
            BigDecimal totalUnpaid
    ) {}

    private final LoanRepository loanRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final FineRepository fineRepository;
    private final Function<MembershipType, MembershipPolicy> policyResolver;
    private final Clock clock;

    public ReportService(
            LoanRepository loanRepository,
            ItemRepository itemRepository,
            MemberRepository memberRepository,
            FineRepository fineRepository,
            Function<MembershipType, MembershipPolicy> policyResolver,
            Clock clock
    ) {
        this.loanRepository = Objects.requireNonNull(loanRepository);
        this.itemRepository = Objects.requireNonNull(itemRepository);
        this.memberRepository = Objects.requireNonNull(memberRepository);
        this.fineRepository = Objects.requireNonNull(fineRepository);
        this.policyResolver = Objects.requireNonNull(policyResolver);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<OverdueLoanEntry> getOverdueLoans() {
        LocalDate today = LocalDate.now(clock);
        Map<String, Item> itemsById = itemRepository.findAll().stream()
                .collect(Collectors.toMap(Item::getItemId, i -> i));
        Map<String, Member> membersById = memberRepository.findAll().stream()
                .collect(Collectors.toMap(Member::getMemberId, m -> m));

        List<OverdueLoanEntry> results = new ArrayList<>();
        for (Loan loan : loanRepository.findAll()) {
            if (loan.isActive() && loan.getDueDate().isBefore(today)) {
                long daysOverdue = ChronoUnit.DAYS.between(loan.getDueDate(), today);
                Member member = membersById.get(loan.getMemberId());
                Item item = itemsById.get(loan.getItemId());

                String memberName = member != null ? member.getName() : loan.getMemberId();
                String itemTitle = item != null ? item.getTitle() : loan.getItemId();

                BigDecimal dailyRate = (member != null)
                        ? policyResolver.apply(member.getMembershipType()).getFinePerDay()
                        : BigDecimal.ZERO;
                BigDecimal fineSoFar = dailyRate.multiply(BigDecimal.valueOf(daysOverdue));

                results.add(new OverdueLoanEntry(
                        loan.getLoanId(),
                        loan.getMemberId(),
                        memberName,
                        loan.getItemId(),
                        itemTitle,
                        loan.getDueDate(),
                        daysOverdue,
                        fineSoFar
                ));
            }
        }
        results.sort(Comparator.comparingLong(OverdueLoanEntry::daysOverdue).reversed());
        return results;
    }

    public List<TopBorrowedEntry> getTopBorrowedItems(LocalDate from, LocalDate to, int limit) {
        Map<String, Item> itemsById = itemRepository.findAll().stream()
                .collect(Collectors.toMap(Item::getItemId, i -> i));

        Map<String, Long> counts = loanRepository.findAll().stream()
                .filter(l -> from == null || !l.getBorrowDate().isBefore(from))
                .filter(l -> to == null || !l.getBorrowDate().isAfter(to))
                .collect(Collectors.groupingBy(Loan::getItemId, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Item item = itemsById.get(entry.getKey());
                    String title = item != null ? item.getTitle() : entry.getKey();
                    String type = item != null ? item.getItemType() : "UNKNOWN";
                    return new TopBorrowedEntry(entry.getKey(), title, type, entry.getValue());
                })
                .toList();
    }

    public List<UnpaidFineSummary> getMembersWithUnpaidFines() {
        Map<String, Member> membersById = memberRepository.findAll().stream()
                .collect(Collectors.toMap(Member::getMemberId, m -> m));

        Map<String, List<Fine>> unpaidByMember = fineRepository.findAll().stream()
                .filter(f -> f.getStatus() == FineStatus.UNPAID)
                .collect(Collectors.groupingBy(Fine::getMemberId));

        List<UnpaidFineSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<Fine>> entry : unpaidByMember.entrySet()) {
            BigDecimal total = entry.getValue().stream()
                    .map(Fine::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                Member member = membersById.get(entry.getKey());
                String name = member != null ? member.getName() : entry.getKey();
                summaries.add(new UnpaidFineSummary(entry.getKey(), name, entry.getValue().size(), total));
            }
        }
        summaries.sort(Comparator.comparing(UnpaidFineSummary::totalUnpaid).reversed());
        return summaries;
    }

    public List<Item> getItemsNeverBorrowed() {
        Set<String> borrowedItemIds = loanRepository.findAll().stream()
                .map(Loan::getItemId)
                .collect(Collectors.toSet());

        return itemRepository.findAll().stream()
                .filter(item -> !borrowedItemIds.contains(item.getItemId()))
                .toList();
    }
}
