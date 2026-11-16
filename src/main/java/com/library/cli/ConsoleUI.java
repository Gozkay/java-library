package com.library.cli;

import com.library.exception.LibraryException;
import com.library.exception.UnauthorizedActionException;
import com.library.model.*;
import com.library.service.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ConsoleUI {
    private final AuthService authService;
    private final CatalogueService catalogueService;
    private final LoanService loanService;
    private final ReservationService reservationService;
    private final FineService fineService;
    private final MemberService memberService;
    private final ReportService reportService;
    private final Clock clock;
    private final CommandParser parser;
    private final Scanner scanner;
    private boolean running = true;

    public ConsoleUI(AuthService authService, CatalogueService catalogueService, LoanService loanService,
                     ReservationService reservationService, FineService fineService, MemberService memberService,
                     ReportService reportService, Clock clock, Scanner scanner) {
        this.authService = Objects.requireNonNull(authService);
        this.catalogueService = Objects.requireNonNull(catalogueService);
        this.loanService = Objects.requireNonNull(loanService);
        this.reservationService = Objects.requireNonNull(reservationService);
        this.fineService = Objects.requireNonNull(fineService);
        this.memberService = Objects.requireNonNull(memberService);
        this.reportService = Objects.requireNonNull(reportService);
        this.clock = Objects.requireNonNull(clock);
        this.parser = new CommandParser();
        this.scanner = Objects.requireNonNull(scanner);
    }

    public void run() {
        System.out.println("\n=== CONSOLE LIBRARY MANAGEMENT SYSTEM ===");
        System.out.println("Type 'help' to see available commands.");
        reservationService.processExpiredHolds();
        while (running) {
            System.out.print("\nlibrary> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            try {
                reservationService.processExpiredHolds();
                List<String> args = parser.parse(line);
                if (!args.isEmpty()) handleCommand(args);
            } catch (LibraryException | IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (UnsupportedOperationException e) {
                System.out.println("Notice: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Library application closed.");
    }
    private void handleCommand(List<String> args) {
        String cmd = args.get(0).toLowerCase();
        switch (cmd) {
            case "help" -> showHelp();
            case "login" -> handleLogin(args);
            case "logout" -> { requireArgCount(args, 1); if (!authService.isLoggedIn()) System.out.println("You are not logged in."); else { authService.logout(); System.out.println("Logged out successfully."); } }
            case "exit" -> { requireArgCount(args, 1); running = false; }
            case "whoami" -> { requireArgCount(args, 1); User u = authService.getCurrentUser(); System.out.println(u == null ? "Not logged in." : "User ID: " + u.getUserId() + " | Role: " + u.getRole()); }
            case "search" -> handleSearch(args);
            case "borrow" -> { User u = requireRole(Role.MEMBER); requireArgCount(args, 2); Loan l = loanService.borrowItem(u.getUserId(), args.get(1)); System.out.println("Borrowed! Loan ID: " + l.getLoanId() + " | Due: " + l.getDueDate()); }
            case "return" -> { User u = requireRole(Role.MEMBER); requireArgCount(args, 2); Loan l = loanService.returnItem(u.getUserId(), args.get(1)); System.out.println("Returned item " + args.get(1) + " (Loan " + l.getLoanId() + ")."); }
            case "renew" -> { User u = requireRole(Role.MEMBER); requireArgCount(args, 2); Loan l = loanService.renewLoan(u.getUserId(), args.get(1)); System.out.println("Renewed! New due date: " + l.getDueDate()); }
            case "reserve" -> { User u = requireRole(Role.MEMBER); requireArgCount(args, 2); reservationService.reserveItem(u.getUserId(), args.get(1)); System.out.println("Reservation placed for " + args.get(1) + "."); }
            case "unreserve" -> { User u = requireRole(Role.MEMBER); requireArgCount(args, 2); reservationService.cancelReservation(u.getUserId(), args.get(1)); System.out.println("Reservation cancelled for " + args.get(1) + "."); }
            case "loans" -> { User u = requireRole(Role.MEMBER); requireArgCount(args, 1); showLoans(u.getUserId()); }
            case "fines" -> { User u = requireRole(Role.MEMBER); requireArgCount(args, 1); showFines(u.getUserId()); }
            case "inbox" -> { User u = requireRole(Role.MEMBER); requireArgCount(args, 1); showInbox(u.getUserId()); }
            case "item" -> handleItem(args);
            case "copy" -> { requireRole(Role.LIBRARIAN); if (args.size()!=4 || !"add".equalsIgnoreCase(args.get(1))) throw new IllegalArgumentException("Usage: copy add <itemId> <count>"); catalogueService.addCopies(args.get(2), Integer.parseInt(args.get(3))); System.out.println("Copies added."); }
            case "member" -> handleMember(args);
            case "unlock" -> { requireRole(Role.LIBRARIAN); requireArgCount(args, 2); authService.unlockAccount(args.get(1)); System.out.println("Account " + args.get(1) + " unlocked."); }
            case "fine" -> { requireRole(Role.LIBRARIAN); if (args.size()!=4 || !"pay".equalsIgnoreCase(args.get(1))) throw new IllegalArgumentException("Usage: fine pay <memberId> <amount>"); fineService.recordPayment(args.get(2), new BigDecimal(args.get(3))); System.out.println("Payment recorded."); }
            case "loan" -> { requireRole(Role.LIBRARIAN); if (args.size()!=3 || !"lost".equalsIgnoreCase(args.get(1))) throw new IllegalArgumentException("Usage: loan lost <loanId>"); loanService.markLoanLost(args.get(2)); System.out.println("Loan marked as LOST."); }
            case "report" -> handleReport(args);
            default -> System.out.println("Unknown command '" + cmd + "'. Type 'help' to see available commands.");
        }
    }

    private void handleLogin(List<String> args) {
        requireArgCount(args, 3);
        if (authService.isLoggedIn()) { System.out.println("Already logged in. Logout first."); return; }
        User user = authService.login(args.get(1), args.get(2));
        System.out.println("Login successful! Welcome, " + user.getUserId() + " (" + user.getRole() + ").");
        if (user.getRole() == Role.MEMBER) showInbox(user.getUserId());
    }

    private void handleSearch(List<String> args) {
        requireLoggedIn();
        String query = ""; Map<String, String> flags = new HashMap<>();
        for (int i = 1; i < args.size(); i++) {
            String t = args.get(i);
            if (t.startsWith("--") && t.contains("=")) { int eq = t.indexOf('='); flags.put(t.substring(2, eq).toLowerCase(), t.substring(eq + 1)); }
            else if (query.isEmpty()) query = t;
            else throw new IllegalArgumentException("Unexpected argument: " + t);
        }
        List<Item> items = catalogueService.search(query, flags.get("author"), flags.get("category"), flags.get("type"));
        if (items.isEmpty()) { System.out.println("No matching items found."); return; }
        for (Item item : items) {
            long total = item.getCopies().stream().filter(c -> c.getStatus() != CopyStatus.RETIRED).count();
            long avail = item.getCopies().stream().filter(c -> c.getStatus() == CopyStatus.AVAILABLE).count();
            System.out.printf("%s | %s (%s) | %s%n", item.getItemId(), item.getTitle(), item.getItemType(), item.isRetired() ? "RETIRED" : avail + " of " + total + " copies available");
        }
    }
    private void showLoans(String memberId) {
        List<Loan> loans = loanService.getMemberLoans(memberId);
        if (loans.isEmpty()) { System.out.println("You have no active loans."); return; }
        LocalDate today = LocalDate.now(clock);
        for (Loan l : loans) {
            String title = l.getItemId();
            try { title = catalogueService.getItemById(l.getItemId()).getTitle(); } catch (Exception ignored) {}
            String status = !l.isActive() ? (l.isLost() ? "LOST" : "RETURNED") : (l.getDueDate().isBefore(today) ? "OVERDUE (" + ChronoUnit.DAYS.between(l.getDueDate(), today) + " days)" : "ON TIME");
            System.out.printf("%s | %s | Borrowed: %s | Due: %s | %s%n", l.getLoanId(), title, l.getBorrowDate(), l.getDueDate(), status);
        }
    }

    private void showFines(String memberId) {
        List<Fine> fines = fineService.getMemberFines(memberId);
        if (fines.isEmpty()) { System.out.println("You have no fines on record."); return; }
        for (Fine f : fines) System.out.printf("%s | Loan: %s | Amount: %s | %s | %s%n", f.getFineId(), f.getLoanId(), f.getAmount(), f.getStatus(), f.getReason());
    }

    private void showInbox(String memberId) {
        List<String> msgs = memberService.getInboxMessages(memberId);
        if (msgs.isEmpty()) { System.out.println("Inbox: No notifications."); return; }
        for (String m : msgs) System.out.println(" - " + m);
    }

    private void handleItem(List<String> args) {
        requireRole(Role.LIBRARIAN);
        if (args.size() < 3) throw new IllegalArgumentException("Usage: item add <type> \"<title>\" [--flags] | item retire <itemId>");
        if ("add".equalsIgnoreCase(args.get(1))) {
            if (args.size() < 4) throw new IllegalArgumentException("Usage: item add <type> \"<title>\" [--author=..] [--isbn=..] [--copies=N]");
            Item created = catalogueService.addItem(args.get(2), args.get(3), parseFlags(args, 4));
            System.out.println("Added item " + created.getItemId() + ": " + created.getTitle());
        } else if ("retire".equalsIgnoreCase(args.get(1))) {
            requireArgCount(args, 3); catalogueService.retireItem(args.get(2)); System.out.println("Item " + args.get(2) + " retired.");
        } else throw new IllegalArgumentException("Unknown item subcommand.");
    }

    private void handleMember(List<String> args) {
        requireRole(Role.LIBRARIAN);
        if (args.size() < 3) throw new IllegalArgumentException("Usage: member add \"<name>\" <TYPE> [pwd] | member suspend|activate|unlock <id>");
        switch (args.get(1).toLowerCase()) {
            case "add" -> {
                if (args.size() < 4 || args.size() > 5) throw new IllegalArgumentException("Usage: member add \"<name>\" <STUDENT|STAFF|SENIOR> [password]");
                Member m = memberService.addMember(args.get(2), MembershipType.valueOf(args.get(3).toUpperCase()), args.size()==5 ? args.get(4) : "changeme123");
                System.out.println("Member created: " + m.getMemberId() + " (" + m.getName() + ")");
            }
            case "suspend" -> { requireArgCount(args, 3); memberService.suspendMember(args.get(2)); System.out.println("Member suspended."); }
            case "activate" -> { requireArgCount(args, 3); memberService.activateMember(args.get(2)); System.out.println("Member activated."); }
            case "unlock" -> { requireArgCount(args, 3); authService.unlockAccount(args.get(2)); System.out.println("Account unlocked."); }
            default -> throw new IllegalArgumentException("Unknown member subcommand.");
        }
    }

    private void handleReport(List<String> args) {
        requireRole(Role.LIBRARIAN);
        if (args.size() < 2) throw new IllegalArgumentException("Usage: report overdue | top [--from=..] [--to=..] | unpaid | never-borrowed");
        switch (args.get(1).toLowerCase()) {
            case "overdue" -> {
                var list = reportService.getOverdueLoans();
                if (list.isEmpty()) { System.out.println("No overdue loans."); return; }
                for (var e : list) System.out.printf("%s | %s | %s | %d days overdue | Fine: %s%n", e.loanId(), e.memberName(), e.itemTitle(), e.daysOverdue(), e.estimatedFine());
            }
            case "top" -> {
                Map<String, String> f = parseFlags(args, 2);
                LocalDate from = f.containsKey("from") ? parseDate(f.get("from")) : null;
                LocalDate to = f.containsKey("to") ? parseDate(f.get("to")) : null;
                var top = reportService.getTopBorrowedItems(from, to, 5);
                if (top.isEmpty()) { System.out.println("No borrowing records found."); return; }
                for (var e : top) System.out.printf("%s | %s (%s) | Borrows: %d%n", e.itemId(), e.title(), e.itemType(), e.borrowCount());
            }
            case "unpaid" -> {
                var unpaid = reportService.getMembersWithUnpaidFines();
                if (unpaid.isEmpty()) { System.out.println("No members have unpaid fines."); return; }
                for (var u : unpaid) System.out.printf("%s | %s | Unpaid count: %d | Total: %s%n", u.memberId(), u.memberName(), u.unpaidCount(), u.totalUnpaid());
            }
            case "never-borrowed" -> {
                var items = reportService.getItemsNeverBorrowed();
                if (items.isEmpty()) { System.out.println("Every item has been borrowed at least once."); return; }
                for (Item i : items) System.out.printf("%s | %s (%s)%n", i.getItemId(), i.getTitle(), i.getItemType());
            }
            default -> throw new IllegalArgumentException("Unknown report type.");
        }
    }

    private void showHelp() {
        System.out.println("\nAvailable commands:");
        if (!authService.isLoggedIn()) { System.out.println("  login <userId> <password> | help | exit"); return; }
        User u = authService.getCurrentUser();
        System.out.println("  search \"<text>\" [--author=..] [--category=..] [--type=BOOK|MAGAZINE|DVD]");
        if (u.getRole() == Role.MEMBER) System.out.println("  borrow <id> | return <id> | renew <id> | reserve <id> | unreserve <id> | loans | fines | inbox");
        else if (u.getRole() == Role.LIBRARIAN) System.out.println("  item add/retire | copy add | member add/suspend/activate/unlock | fine pay | loan lost | report overdue/top/unpaid/never-borrowed");
        System.out.println("  whoami | logout | help | exit");
    }

    private User requireLoggedIn() { User u = authService.getCurrentUser(); if (u == null) throw new AuthService.AuthenticationException("Please log in first."); return u; }
    private User requireRole(Role r) { User u = requireLoggedIn(); if (u.getRole() != r) throw new UnauthorizedActionException("Access denied: requires " + r + " role."); return u; }
    private void requireArgCount(List<String> args, int n) { if (args.size() != n) throw new IllegalArgumentException("Incorrect number of arguments. Type 'help' for usage."); }
    private Map<String, String> parseFlags(List<String> args, int start) {
        Map<String, String> flags = new HashMap<>();
        for (int i = start; i < args.size(); i++) {
            String t = args.get(i);
            if (!t.startsWith("--") || !t.contains("=")) throw new IllegalArgumentException("Invalid option: " + t);
            int eq = t.indexOf('='); flags.put(t.substring(2, eq).toLowerCase(), t.substring(eq + 1));
        }
        return flags;
    }
    private LocalDate parseDate(String raw) { try { return LocalDate.parse(raw); } catch (DateTimeParseException e) { throw new IllegalArgumentException("Invalid date: " + raw); } }
}
