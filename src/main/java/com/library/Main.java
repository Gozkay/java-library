package com.library;

import com.library.cli.ConsoleUI;
import com.library.event.ActivityLog;
import com.library.event.EventBus;
import com.library.event.LibraryEvent;
import com.library.event.NotificationInbox;
import com.library.exception.LibraryException;
import com.library.factory.MembershipPolicyFactory;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;
import com.library.service.AuthService;
import com.library.service.CatalogueService;
import com.library.service.FineService;
import com.library.service.LoanService;
import com.library.service.NotificationService;
import com.library.service.ReportService;
import com.library.service.ReservationService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Application entry point and composition root for storage, services, events, and the CLI.
 */
public final class Main {
    public static void main(String[] args) {
        Path dataDir = Path.of(System.getProperty("library.data.dir", "data"));
        try {
            Clock clock = applicationClock();
            DataContext ctx = DataContext.json(dataDir);
            if (SeedDataLoader.seedIfEmpty(ctx)) {
                System.out.println("First run: seed data created in " + dataDir.toAbsolutePath());
                System.out.println("  Librarian login: " + SeedDataLoader.LIBRARIAN_ID + " / " + SeedDataLoader.LIBRARIAN_PASSWORD);
                System.out.println("  Member logins:   M-1 .. M-5 / " + SeedDataLoader.MEMBER_PASSWORD);
            }
            EventBus eventBus = new EventBus();
            eventBus.subscribe(new NotificationInbox(ctx.notifications(), ctx.ids()));
            eventBus.subscribe(new ActivityLog(dataDir));

            System.out.printf("Loaded %d items, %d users, %d loans, %d reservations, %d fines.%n",
                    ctx.items().count(), ctx.users().count(), ctx.loans().count(),
                    ctx.reservations().count(), ctx.fines().count());

            AuthService authService = new AuthService(ctx.users(), ctx.ids());
            ReportService reportService = new ReportService(ctx, clock);
            for (ReportService.OverdueEntry overdue : reportService.getOverdueLoans()) {
                String reminder = "Loan " + overdue.loanId() + " is overdue";
                boolean alreadyNotified = ctx.notifications().findByMemberId(overdue.memberId()).stream()
                        .anyMatch(notification -> notification.getMessage().contains(reminder));
                if (!alreadyNotified) {
                    eventBus.publish(new LibraryEvent(LibraryEvent.EventType.LOAN_OVERDUE,
                            overdue.memberId(), overdue.itemId(), overdue.loanId(),
                            reminder + " by " + overdue.daysOverdue() + " day(s).",
                            LocalDateTime.now(clock)));
                }
            }
            FineService fineService = new FineService(ctx.fines(), ctx.ids(), clock);
            NotificationService notificationService = new NotificationService(ctx.notifications());
            ReservationService reservationService = new ReservationService(
                    ctx.reservations(), ctx.items(), ctx.loans(), ctx.users(), ctx.ids(), clock, eventBus);
            LoanService loanService = new LoanService(
                    ctx.loans(), ctx.items(), ctx.users(), ctx.fines(), ctx.reservations(),
                    new MembershipPolicyFactory(), ctx.ids(), clock, fineService, reservationService, eventBus);
            CatalogueService catalogueService = new CatalogueService(ctx.items());
            reservationService.expireReservations();
            ConsoleUI ui = new ConsoleUI(ctx.ids(), notificationService, authService, reportService, clock,
                    System.in, System.out)
                    .withServices(catalogueService, loanService, reservationService, fineService);
            ui.run();
        } catch (LibraryException | IllegalArgumentException | IllegalStateException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Clock applicationClock() {
        String fixedInstant = System.getProperty("library.clock.fixed");
        if (fixedInstant == null) {
            return Clock.systemDefaultZone();
        }
        try {
            return Clock.fixed(Instant.parse(fixedInstant), ZoneId.systemDefault());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "library.clock.fixed must be an ISO-8601 instant (for example 2026-11-01T10:00:00Z).", e);
        }
    }
}
