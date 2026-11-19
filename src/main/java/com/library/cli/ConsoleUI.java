package com.library.cli;

import com.library.cli.CommandParser.ParsedCommand;
import com.library.exception.LibraryException;
import com.library.factory.ItemFactory;
import com.library.model.Fine;
import com.library.model.Item;
import com.library.model.ItemType;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.MembershipType;
import com.library.model.Notification;
import com.library.model.Reservation;
import com.library.model.Role;
import com.library.model.User;
import com.library.repository.DataContext;
import com.library.service.AuthService;
import com.library.service.CatalogueServiceApi;
import com.library.service.CirculationServiceApi;
import com.library.service.FineServiceApi;
import com.library.service.NotificationService;
import com.library.service.NotificationServiceApi;
import com.library.service.ReportService;
import com.library.service.ReportService.CollectionSummary;
import com.library.service.ReportService.MemberLoanHistoryEntry;
import com.library.service.ReportService.OverdueEntry;
import com.library.service.ReportService.PopularItemEntry;
import com.library.service.ReservationServiceApi;
import com.library.util.IdGenerator;

import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

/**
 * Role-aware command-line interface for librarians and members.
 * Application services are injected at startup and invoked by the command handlers.
 */
public class ConsoleUI {
    private final AuthService authService;
    private final ReportService reportService;
    private final Clock clock;
    private final IdGenerator ids;
    private final NotificationServiceApi notificationService;
    private final InputStream in;
    private final PrintStream out;

    private CatalogueServiceApi catalogueService;
    private CirculationServiceApi circulationService;
    private ReservationServiceApi reservationService;
    private FineServiceApi fineService;

    public ConsoleUI(DataContext ctx, AuthService authService, ReportService reportService, Clock clock) {
        this(ctx, authService, reportService, clock, System.in, System.out);
    }

    public ConsoleUI(DataContext ctx, AuthService authService, ReportService reportService,
                     Clock clock, InputStream in, PrintStream out) {
        this(Objects.requireNonNull(ctx, "DataContext required").ids(),
                new NotificationService(ctx.notifications()), authService, reportService, clock, in, out);
    }

    public ConsoleUI(IdGenerator ids, NotificationServiceApi notificationService,
                     AuthService authService, ReportService reportService, Clock clock,
                     InputStream in, PrintStream out) {
        this.ids = Objects.requireNonNull(ids, "ID generator required");
        this.notificationService = Objects.requireNonNull(notificationService, "Notification service required");
        this.authService = Objects.requireNonNull(authService, "AuthService required");
        this.reportService = Objects.requireNonNull(reportService, "ReportService required");
        this.clock = Objects.requireNonNull(clock, "Clock required");
        this.in = Objects.requireNonNull(in, "InputStream required");
        this.out = Objects.requireNonNull(out, "PrintStream required");
    }

    public ConsoleUI withServices(CatalogueServiceApi catalogue,
                                  CirculationServiceApi circulation,
                                  ReservationServiceApi reservation,
                                  FineServiceApi fine) {
        this.catalogueService = catalogue;
        this.circulationService = circulation;
        this.reservationService = reservation;
        this.fineService = fine;
        return this;
    }

    public void run() {
        out.println("=================================================================");
        out.println("            CONSOLE LIBRARY MANAGEMENT SYSTEM (v1.0)             ");
        out.println("=================================================================");
        out.println("Type 'help' for commands, 'login <userId> <password>', or 'exit'.");

        try (Scanner scanner = new Scanner(in)) {
            while (true) {
                String prompt = authService.getCurrentUser()
                        .map(u -> "[" + u.getUserId() + " (" + u.getRole() + ")]> ")
                        .orElse("[guest]> ");
                out.print(prompt);
                out.flush();

                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine();
                if (!handleLine(line)) {
                    break;
                }
            }
        }
        out.println("Goodbye.");
    }

    /** Processes a single command line. Returns false if the user requested exit. */
    public boolean handleLine(String line) {
        ParsedCommand cmd;
        try {
            cmd = CommandParser.parse(line);
        } catch (IllegalArgumentException e) {
            out.println("Syntax error: " + e.getMessage());
            return true;
        }

        if (cmd.command().isEmpty()) {
            return true;
        }

        try {
            if (reservationService != null) {
                reservationService.expireReservations();
            }
            return switch (cmd.command()) {
                case "exit", "quit" -> false;
                case "help" -> { printHelp(); yield true; }
                case "login" -> { handleLogin(cmd); yield true; }
                case "logout" -> { handleLogout(); yield true; }
                case "whoami" -> { handleWhoAmI(); yield true; }
                case "search", "catalog", "catalogue" -> { handleSearch(cmd); yield true; }
                case "item" -> { handleItem(cmd); yield true; }
                case "copy" -> { handleCopy(cmd); yield true; }
                case "member" -> { handleMember(cmd); yield true; }
                case "borrow" -> { handleBorrow(cmd); yield true; }
                case "return" -> { handleReturn(cmd); yield true; }
                case "renew" -> { handleRenew(cmd); yield true; }
                case "lost" -> { handleLost(cmd); yield true; }
                case "reserve" -> { handleReserve(cmd); yield true; }
                case "cancel", "unreserve" -> { handleCancelReservation(cmd); yield true; }
                case "collect" -> { handleCollectReservation(cmd); yield true; }
                case "fine", "fines" -> { handleFines(cmd); yield true; }
                case "my" -> { handleMy(cmd); yield true; }
                case "inbox" -> { handleInbox(); yield true; }
                case "report" -> { handleReport(cmd); yield true; }
                default -> {
                    out.println("Unknown command '" + cmd.command() + "'. Type 'help' to view available commands.");
                    yield true;
                }
            };
        } catch (LibraryException | IllegalArgumentException | IllegalStateException e) {
            out.println("Error: " + e.getMessage());
            return true;
        }
    }

    private void handleLogin(ParsedCommand cmd) {
        if (cmd.args().size() < 2) {
            out.println("Usage: login <userId> <password>");
            return;
        }
        User user = authService.login(cmd.arg(0), cmd.arg(1));
        out.printf("Welcome, %s (%s - %s).%n", user.getName(), user.getUserId(), user.getRole());

        if (user instanceof Member member) {
            List<Notification> unread = notificationService.getUnreadForMember(member.getUserId());
            if (!unread.isEmpty()) {
                out.printf("You have %d unread notification(s):%n", unread.size());
                for (Notification n : unread) {
                    out.printf("  [%s] %s%n", n.getCreatedAt(), n.getMessage());
                }
            }
        }
    }

    private void handleLogout() {
        if (!authService.isLoggedIn()) {
            out.println("No active session.");
            return;
        }
        authService.logout();
        out.println("Logged out.");
    }

    private void handleWhoAmI() {
        authService.getCurrentUser().ifPresentOrElse(
                u -> {
                    if (u instanceof Member m) {
                        out.printf("Logged in as %s (%s) | Role: %s | Tier: %s | Status: %s%n",
                                m.getName(), m.getUserId(), m.getRole(), m.getMembershipType(), m.getStatus());
                    } else {
                        out.printf("Logged in as %s (%s) | Role: %s%n", u.getName(), u.getUserId(), u.getRole());
                    }
                },
                () -> out.println("Not logged in (guest).")
        );
    }

    private void handleSearch(ParsedCommand cmd) {
        ParsedArguments parsed = parseArguments(cmd.args());
        ItemType type = parsed.options().containsKey("type")
                ? ItemType.valueOf(parsed.options().get("type").toUpperCase(Locale.ROOT).replace(' ', '_'))
                : null;
        boolean availableOnly = parsed.options().containsKey("available");
        String query = String.join(" ", parsed.positionals());
        requireService(catalogueService, "CatalogueService");
        printItems(catalogueService.search(query,
                parsed.options().get("author"), parsed.options().get("category"), type, availableOnly));
    }

    private void printItems(List<Item> items) {
        if (items.isEmpty()) {
            out.println("No matching items found.");
            return;
        }
        out.printf("%-8s %-14s %-34s %-16s %-10s%n", "ID", "TYPE", "TITLE", "CATEGORY", "AVAIL");
        out.println("-".repeat(86));
        for (Item item : items) {
            String avail = item.countAvailable() + "/" + item.countActiveCopies();
            out.printf("%-8s %-14s %-34s %-16s %-10s%n",
                    item.getItemId(),
                    item.getType(),
                    truncate(item.getTitle(), 33),
                    truncate(item.getCategory(), 15),
                    avail);
        }
    }

    private void handleItem(ParsedCommand cmd) {
        authService.requireLibrarian();
        String sub = cmd.subcommand();
        switch (sub) {
            case "add" -> {
                if (cmd.args().size() < 3) {
                    out.println("Usage: item add <type> \"<title>\" [--category=..] [--author=..] [--isbn=..] [--copies=1]");
                    return;
                }
                ParsedArguments parsed = parseArguments(cmd.args().subList(1, cmd.args().size()));
                if (parsed.positionals().size() < 2) {
                    out.println("Usage: item add <type> \"<title>\" [options]");
                    return;
                }
                String type = parsed.positionals().get(0);
                String title = String.join(" ", parsed.positionals().subList(1, parsed.positionals().size()));
                Map<String, String> options = parsed.options();
                int year = parseIntOption(options, "year", LocalDate.now(clock).getYear());
                int copies = parseIntOption(options, "copies", 1);
                Item item = new ItemFactory().createItem(
                        type, ids.next(IdGenerator.Kind.ITEM), title,
                        options.getOrDefault("category", "Uncategorized"), year, copies,
                        options.getOrDefault("isbn", ""), options.getOrDefault("author", ""),
                        options.getOrDefault("publisher", ""), parseIntOption(options, "pages", 0),
                        parseIntOption(options, "issue", 0), options.getOrDefault("month", ""),
                        options.getOrDefault("director", ""), parseIntOption(options, "runtime", 0));
                requireService(catalogueService, "CatalogueService");
                catalogueService.addItem(item);
                out.println("Added " + item.getType() + " " + item.getItemId() + " (" + item.getTitle() + ").");
            }
            case "retire" -> {
                String itemId = cmd.arg(1);
                if (itemId.isEmpty()) {
                    out.println("Usage: item retire <itemId>");
                    return;
                }
                requireService(catalogueService, "CatalogueService");
                catalogueService.retireItem(itemId);
                out.println("Item " + itemId + " retired.");
            }
            case "edit" -> {
                if (cmd.args().size() < 2) {
                    out.println("Usage: item edit <itemId> [--title=..] [--category=..] [--year=..]");
                    return;
                }
                Map<String, String> options = parseArguments(cmd.args().subList(2, cmd.args().size())).options();
                requireService(catalogueService, "CatalogueService");
                catalogueService.updateItem(cmd.arg(1), options.get("title"), options.get("category"),
                        options.containsKey("year") ? Integer.parseInt(options.get("year")) : null);
                out.println("Item " + cmd.arg(1) + " updated.");
            }
            case "info" -> {
                String itemId = cmd.arg(1);
                requireService(catalogueService, "CatalogueService");
                Item item = catalogueService.findById(itemId)
                        .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
                out.printf("%s | %s (%d) | Type: %s | Category: %s | Retired: %s | Copies: %d/%d available%n",
                        item.getItemId(), item.getTitle(), item.getPublicationYear(),
                        item.getType(), item.getCategory(), item.isRetired(),
                        item.countAvailable(), item.countActiveCopies());
                item.getCopies().forEach(c -> out.printf("  Copy %s -> %s%n", c.getCopyId(), c.getStatus()));
            }
            default -> out.println("Usage: item <add|edit|info|retire> ...");
        }
    }

    private void handleCopy(ParsedCommand cmd) {
        authService.requireLibrarian();
        if (!"add".equals(cmd.subcommand()) || cmd.args().size() < 3) {
            out.println("Usage: copy add <itemId> <count>");
            return;
        }
        requireService(catalogueService, "CatalogueService");
        catalogueService.addCopies(cmd.arg(1), Integer.parseInt(cmd.arg(2)));
        out.println("Added " + cmd.arg(2) + " copy/copies to item " + cmd.arg(1) + ".");
    }

    private void handleMember(ParsedCommand cmd) {
        authService.requireLibrarian();
        String sub = cmd.subcommand();
        switch (sub) {
            case "add" -> {
                if (cmd.args().size() < 3) {
                    out.println("Usage: member add \"<Full Name>\" <STUDENT|STAFF|SENIOR> [initialPassword]");
                    return;
                }
                String name = cmd.arg(1);
                MembershipType tier = MembershipType.valueOf(cmd.arg(2).toUpperCase(Locale.ROOT));
                String pwd = cmd.args().size() >= 4 ? cmd.arg(3) : null;
                Member created = authService.registerMember(name, tier, pwd);
                String shownPwd = (pwd == null || pwd.isBlank()) ? created.getUserId() : pwd;
                out.printf("Registered member %s (%s) | Tier: %s | Initial Password: %s%n",
                        created.getUserId(), created.getName(), created.getMembershipType(), shownPwd);
            }
            case "list" -> {
                List<Member> members = authService.listAllMembers();
                if (members.isEmpty()) {
                    out.println("No members registered.");
                    return;
                }
                out.printf("%-8s %-24s %-12s %-12s %-8s%n", "ID", "NAME", "TIER", "STATUS", "LOCKED");
                out.println("-".repeat(68));
                for (Member m : members) {
                    out.printf("%-8s %-24s %-12s %-12s %-8s%n",
                            m.getUserId(), truncate(m.getName(), 23), m.getMembershipType(), m.getStatus(), m.isLocked());
                }
            }
            case "suspend" -> {
                String id = cmd.arg(1);
                if (id.isEmpty()) { out.println("Usage: member suspend <memberId>"); return; }
                Member m = authService.suspendMember(id);
                out.println("Suspended member " + m.getUserId() + " (" + m.getName() + ").");
            }
            case "activate" -> {
                String id = cmd.arg(1);
                if (id.isEmpty()) { out.println("Usage: member activate <memberId>"); return; }
                Member m = authService.activateMember(id);
                out.println("Activated member " + m.getUserId() + " (" + m.getName() + ").");
            }
            case "unlock" -> {
                String id = cmd.arg(1);
                if (id.isEmpty()) { out.println("Usage: member unlock <userId>"); return; }
                User u = authService.unlockAccount(id);
                out.println("Unlocked account " + u.getUserId() + ".");
            }
            default -> out.println("Usage: member <add|list|suspend|activate|unlock> ...");
        }
    }

    private void handleBorrow(ParsedCommand cmd) {
        User actor = authService.requireLoggedIn();
        String memberId;
        String itemId;
        if (actor.getRole() == Role.MEMBER) {
            if (cmd.args().isEmpty()) { out.println("Usage: borrow <itemId>"); return; }
            memberId = actor.getUserId();
            itemId = cmd.arg(0);
        } else {
            if (cmd.args().size() < 2) { out.println("Usage: borrow <memberId> <itemId>"); return; }
            memberId = cmd.arg(0);
            itemId = cmd.arg(1);
        }
        requireService(circulationService, "LoanService");
        Loan loan = circulationService.borrowItem(memberId, itemId);
        out.printf("Loan %s created: %s (copy %s) borrowed by %s, due %s.%n",
                loan.getLoanId(), loan.getItemId(), loan.getCopyId(), loan.getMemberId(), loan.getDueDate());
    }

    private void handleReturn(ParsedCommand cmd) {
        User actor = authService.requireLoggedIn();
        if (cmd.args().isEmpty()) { out.println("Usage: return <copyId>"); return; }
        requireService(circulationService, "LoanService");
        Loan loan = circulationService.returnCopy(cmd.arg(0), actor.getUserId());
        out.printf("Copy %s returned (loan %s).%n", loan.getCopyId(), loan.getLoanId());
    }

    private void handleRenew(ParsedCommand cmd) {
        User actor = authService.requireLoggedIn();
        if (cmd.args().isEmpty()) { out.println("Usage: renew <loanId>"); return; }
        requireService(circulationService, "LoanService");
        Loan loan = circulationService.renewLoan(cmd.arg(0), actor.getUserId());
        out.printf("Loan %s renewed (count %d). New due date: %s.%n",
                loan.getLoanId(), loan.getRenewalCount(), loan.getDueDate());
    }

    private void handleLost(ParsedCommand cmd) {
        User actor = authService.requireLibrarian();
        if (cmd.args().isEmpty()) { out.println("Usage: lost <loanId>"); return; }
        requireService(circulationService, "LoanService");
        Loan loan = circulationService.markLost(cmd.arg(0), actor.getUserId());
        out.printf("Loan %s marked LOST.%n", loan.getLoanId());
    }

    private void handleReserve(ParsedCommand cmd) {
        User actor = authService.requireLoggedIn();
        String memberId;
        String itemId;
        if (actor.getRole() == Role.MEMBER) {
            if (cmd.args().isEmpty()) { out.println("Usage: reserve <itemId>"); return; }
            memberId = actor.getUserId();
            itemId = cmd.arg(0);
        } else {
            if (cmd.args().size() < 2) { out.println("Usage: reserve <memberId> <itemId>"); return; }
            memberId = cmd.arg(0);
            itemId = cmd.arg(1);
        }
        requireService(reservationService, "ReservationService");
        Reservation res = reservationService.placeReservation(memberId, itemId);
        out.printf("Reservation %s placed for item %s (status: %s).%n",
                res.getReservationId(), res.getItemId(), res.getStatus());
    }

    private void handleCancelReservation(ParsedCommand cmd) {
        User actor = authService.requireLoggedIn();
        if (cmd.args().isEmpty()) { out.println("Usage: cancel <reservationId>"); return; }
        requireService(reservationService, "ReservationService");
        reservationService.cancelReservation(cmd.arg(0), actor.getUserId());
        out.println("Reservation " + cmd.arg(0) + " cancelled.");
    }

    private void handleCollectReservation(ParsedCommand cmd) {
        Member member = authService.requireMember();
        if (cmd.args().isEmpty()) {
            out.println("Usage: collect <reservationId>");
            return;
        }
        requireService(circulationService, "LoanService");
        Loan loan = circulationService.collectReservation(cmd.arg(0), member.getUserId());
        out.printf("Reservation collected; loan %s created for copy %s, due %s.%n",
                loan.getLoanId(), loan.getCopyId(), loan.getDueDate());
    }

    private void handleFines(ParsedCommand cmd) {
        User actor = authService.requireLoggedIn();
        if ("pay".equals(cmd.subcommand())) {
            authService.requireLibrarian();
            if (cmd.args().size() < 3) {
                out.println("Usage: fine pay <fineId> <amount>");
                return;
            }
            requireService(fineService, "FineService");
            Fine paid = fineService.payFine(cmd.arg(1), new BigDecimal(cmd.arg(2)));
            out.printf("Fine %s updated (status: %s).%n", paid.getFineId(), paid.getStatus());
            return;
        }
        String memberId = actor.getRole() == Role.MEMBER ? actor.getUserId() : cmd.arg(0);
        if (memberId.isEmpty()) {
            out.println("Usage: fines <memberId>  OR  fine pay <fineId> <amount>");
            return;
        }
        requireService(fineService, "FineService");
        List<Fine> list = fineService.getMemberFines(memberId);
        if (list.isEmpty()) {
            out.println("No fines found for " + memberId + ".");
            return;
        }
        list.forEach(f -> out.printf("%s | Loan: %s | Amount: $%s | Reason: %s | Status: %s%n",
                f.getFineId(), f.getLoanId(), f.getAmount(), f.getReason(), f.getStatus()));
    }

    private void handleMy(ParsedCommand cmd) {
        Member member = authService.requireMember();
        String sub = cmd.subcommand();
        switch (sub) {
            case "loans" -> {
                requireService(circulationService, "LoanService");
                requireService(catalogueService, "CatalogueService");
                List<Loan> active = circulationService.getMemberLoans(member.getUserId()).stream()
                        .filter(Loan::isActive)
                        .toList();
                if (active.isEmpty()) {
                    out.println("You have no active loans.");
                    return;
                }
                for (Loan l : active) {
                    String title = catalogueService.findById(l.getItemId()).map(Item::getTitle).orElse(l.getItemId());
                    out.printf("%s | %s (%s) | Borrowed: %s | Due: %s | Renewals: %d%n",
                            l.getLoanId(), title, l.getCopyId(), l.getBorrowDate(), l.getDueDate(), l.getRenewalCount());
                }
            }
            case "history" -> printMemberHistory(member.getUserId());
            case "reservations" -> {
                requireService(reservationService, "ReservationService");
                List<Reservation> res = reservationService.getMemberReservations(member.getUserId()).stream()
                        .filter(reservation -> reservation.getStatus().isActive())
                        .toList();
                if (res.isEmpty()) {
                    out.println("You have no active reservations.");
                    return;
                }
                res.forEach(r -> out.printf("%s | Item: %s | Status: %s | Hold Expires: %s%n",
                        r.getReservationId(), r.getItemId(), r.getStatus(), r.getHoldExpiresOn()));
            }
            case "fines" -> {
                requireService(fineService, "FineService");
                List<Fine> fines = fineService.getMemberFines(member.getUserId());
                if (fines.isEmpty()) {
                    out.println("You have no fines.");
                    return;
                }
                fines.forEach(f -> out.printf("%s | Loan: %s | $%s | %s | %s%n",
                        f.getFineId(), f.getLoanId(), f.getAmount(), f.getReason(), f.getStatus()));
            }
            default -> out.println("Usage: my <loans|history|reservations|fines>");
        }
    }

    private void handleInbox() {
        Member member = authService.requireMember();
        List<Notification> messages = notificationService.getForMember(member.getUserId());
        if (messages.isEmpty()) {
            out.println("Your inbox is empty.");
            return;
        }
        for (Notification n : messages) {
            String flag = n.isRead() ? "READ  " : "UNREAD";
            out.printf("[%s] %s (%s): %s%n", flag, n.getNotificationId(), n.getCreatedAt(), n.getMessage());
            if (!n.isRead()) {
                notificationService.markRead(n.getNotificationId());
            }
        }
    }

    private void handleReport(ParsedCommand cmd) {
        authService.requireLibrarian();
        String sub = cmd.subcommand();
        switch (sub) {
            case "overdue" -> {
                List<OverdueEntry> overdue = reportService.getOverdueLoans();
                if (overdue.isEmpty()) {
                    out.println("No overdue loans.");
                    return;
                }
                out.printf("%-8s %-8s %-20s %-30s %-12s %-6s %-10s%n",
                        "LOAN", "MEMBER", "NAME", "ITEM", "DUE", "DAYS", "FINE");
                out.println("-".repeat(100));
                for (OverdueEntry e : overdue) {
                    out.printf("%-8s %-8s %-20s %-30s %-12s %-6d $%-9s%n",
                            e.loanId(), e.memberId(), truncate(e.memberName(), 19),
                            truncate(e.itemTitle(), 29), e.dueDate(), e.daysOverdue(), e.fineSoFar());
                }
            }
            case "popular" -> {
                ParsedArguments parsed = parseArguments(cmd.args().subList(1, cmd.args().size()));
                int topN = parsed.positionals().isEmpty() ? 5 : Integer.parseInt(parsed.positionals().get(0));
                LocalDate from = parseDateOption(parsed.options().get("from"));
                LocalDate to = parseDateOption(parsed.options().get("to"));
                List<PopularItemEntry> popular = reportService.getPopularItems(topN, from, to);
                if (popular.isEmpty()) {
                    out.println("No loan data available yet.");
                    return;
                }
                out.printf("%-8s %-36s %-16s %-8s%n", "ITEM", "TITLE", "CATEGORY", "LOANS");
                out.println("-".repeat(72));
                for (PopularItemEntry p : popular) {
                    out.printf("%-8s %-36s %-16s %-8d%n",
                            p.itemId(), truncate(p.title(), 35), truncate(p.category(), 15), p.borrowCount());
                }
            }
            case "member" -> {
                String memberId = cmd.arg(1);
                if (memberId.isEmpty()) {
                    out.println("Usage: report member <memberId>");
                    return;
                }
                authService.getMember(memberId);
                printMemberHistory(memberId);
            }
            case "summary" -> {
                CollectionSummary s = reportService.getCollectionSummary();
                out.println("=== LIBRARY COLLECTION & OPERATIONS SUMMARY ===");
                out.printf("Titles:        %d total (%d active, %d retired)%n",
                        s.totalTitles(), s.activeTitles(), s.retiredTitles());
                out.printf("Copies:        %d total (%d available, %d on loan, %d on hold, %d lost)%n",
                        s.totalCopies(), s.availableCopies(), s.onLoanCopies(), s.onHoldCopies(), s.lostCopies());
                out.printf("Utilization:   %.1f%%%n", s.utilizationPercent());
                out.printf("Members:       %d total (%d active)%n", s.totalMembers(), s.activeMembers());
                out.printf("Loans:         %d active (%d overdue)%n", s.activeLoans(), s.overdueLoans());
                out.printf("Fines:         $%s unpaid | $%s collected%n",
                        s.totalUnpaidFines(), s.totalCollectedFines());
                Map<String, Long> cats = reportService.getCategoryBreakdown();
                if (!cats.isEmpty()) {
                    out.println("By Category:   " + cats);
                }
            }
            case "unpaid" -> {
                var entries = reportService.getMembersWithUnpaidFines();
                if (entries.isEmpty()) {
                    out.println("No unpaid fines.");
                    return;
                }
                for (var entry : entries) {
                    out.printf("%s | %s | Unpaid: $%s%n",
                            entry.memberId(), entry.memberName(), entry.amount());
                }
            }
            case "unused" -> {
                List<Item> unused = reportService.getItemsNeverBorrowed();
                if (unused.isEmpty()) {
                    out.println("Every active item has been borrowed.");
                    return;
                }
                unused.forEach(item -> out.printf("%s | %s | %s%n",
                        item.getItemId(), item.getTitle(), item.getType()));
            }
            default -> out.println("Usage: report <overdue|popular [N] [--from=..] [--to=..]|unpaid|unused|member <memberId>|summary>");
        }
    }

    private void printMemberHistory(String memberId) {
        List<MemberLoanHistoryEntry> history = reportService.getMemberLoanHistory(memberId);
        if (history.isEmpty()) {
            out.println("No loan history found for " + memberId + ".");
            return;
        }
        out.printf("%-8s %-32s %-12s %-12s %-12s %-10s%n",
                "LOAN", "TITLE", "BORROWED", "DUE", "RETURNED", "STATUS");
        out.println("-".repeat(90));
        for (MemberLoanHistoryEntry h : history) {
            out.printf("%-8s %-32s %-12s %-12s %-12s %-10s%n",
                    h.loanId(), truncate(h.itemTitle(), 31),
                    h.borrowDate(), h.dueDate(),
                    h.returnDate() == null ? "-" : h.returnDate(),
                    h.status());
        }
    }

    private void requireService(Object service, String name) {
        if (service == null) {
            throw new IllegalStateException(name + " is not wired yet in Main.");
        }
    }

    private static ParsedArguments parseArguments(List<String> arguments) {
        List<String> positionals = new java.util.ArrayList<>();
        Map<String, String> options = new java.util.LinkedHashMap<>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (!argument.startsWith("--")) {
                positionals.add(argument);
                continue;
            }
            int equals = argument.indexOf('=');
            String key = equals < 0 ? argument.substring(2) : argument.substring(2, equals);
            String value;
            if (equals >= 0) {
                value = argument.substring(equals + 1);
            } else if (i + 1 < arguments.size() && !arguments.get(i + 1).startsWith("--")) {
                value = arguments.get(++i);
            } else {
                value = "true";
            }
            options.put(key.toLowerCase(Locale.ROOT), value);
        }
        return new ParsedArguments(List.copyOf(positionals), Map.copyOf(options));
    }

    private static int parseIntOption(Map<String, String> options, String key, int fallback) {
        String value = options.get(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static LocalDate parseDateOption(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private record ParsedArguments(List<String> positionals, Map<String, String> options) { }

    private static String truncate(String value, int max) {
        if (value == null) return "-";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private void printHelp() {
        out.println("General Commands:");
        out.println("  login <userId> <password>           Sign in as librarian or member");
        out.println("  logout                              Sign out of current session");
        out.println("  whoami                              Show current logged-in user");
        out.println("  search [text] [--author=..] [--category=..] [--type=TYPE] [--available]");
        out.println("  help                                Show this help message");
        out.println("  exit                                Exit application");

        authService.getCurrentUser().ifPresent(u -> {
            if (u.getRole() == Role.LIBRARIAN) {
                out.println("\nLibrarian Commands:");
                out.println("  item add <type> \"<title>\" [options] Add a catalogue item");
                out.println("  member add \"<Name>\" <TIER> [pwd]    Register new member (STUDENT|STAFF|SENIOR)");
                out.println("  member list                         List all registered members");
                out.println("  member suspend <memberId>           Suspend a member account");
                out.println("  member activate <memberId>          Reactivate a suspended member");
                out.println("  member unlock <userId>              Unlock a locked account");
                out.println("  item info <itemId>                  View item details & copy statuses");
                out.println("  item edit <itemId> [options]        Edit title, category or publication year");
                out.println("  item retire <itemId>                Retire a catalogue item");
                out.println("  copy add <itemId> <count>           Add physical copies");
                out.println("  borrow <memberId> <itemId>          Issue a loan to a member");
                out.println("  return <copyId>                     Process copy return");
                out.println("  renew <loanId>                      Renew an active loan");
                out.println("  return <copyId>                     Return a copy");
                out.println("  lost <loanId>                       Mark an active loan as lost");
                out.println("  reserve <memberId> <itemId>         Place reservation for a member");
                out.println("  cancel <reservationId>              Cancel a reservation");
                out.println("  fines <memberId>                    View member fines");
                out.println("  fine pay <fineId> <amount>          Record fine payment");
                out.println("  report overdue                      List all overdue loans");
                out.println("  report popular [N] [--from=..] [--to=..]");
                out.println("  report unpaid                       List members with unpaid fines");
                out.println("  report unused                       List items never borrowed");
                out.println("  report member <memberId>            Member borrowing history");
                out.println("  report summary                      Collection & financial overview");
            } else {
                out.println("\nMember Commands:");
                out.println("  borrow <itemId>                     Borrow an available item");
                out.println("  return <copyId>                     Return one of your copies");
                out.println("  renew <loanId>                      Renew one of your active loans");
                out.println("  reserve <itemId>                    Reserve an item");
                out.println("  collect <reservationId>             Collect an item on hold");
                out.println("  cancel <reservationId>              Cancel your reservation");
                out.println("  my loans                            View your active loans");
                out.println("  my history                          View your complete loan history");
                out.println("  my reservations                     View your active reservations");
                out.println("  my fines                            View your fines");
                out.println("  inbox                               View & mark notifications read");
            }
        });
    }
}
