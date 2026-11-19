
package com.library;

import com.library.event.ActivityLog;
import com.library.event.EventBus;
import com.library.event.NotificationInbox;
import com.library.exception.LibraryException;
import com.library.factory.MembershipPolicyFactory;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;
import com.library.service.FineService;
import com.library.service.LoanService;
import com.library.service.ReservationService;

import java.nio.file.Path;
import java.time.Clock;

public final class Main {

    public static void main(String[] args) {

        Path dataDir = Path.of(
                System.getProperty("library.data.dir", "data")
        );

        try {
            DataContext ctx = DataContext.json(dataDir);

            if (SeedDataLoader.seedIfEmpty(ctx)) {
                System.out.println(
                        "First run: seed data created in "
                                + dataDir.toAbsolutePath()
                );

                System.out.println(
                        "Librarian login: "
                                + SeedDataLoader.LIBRARIAN_ID
                                + " / "
                                + SeedDataLoader.LIBRARIAN_PASSWORD
                );

                System.out.println(
                        "Member logins: M-1 .. M-5 / "
                                + SeedDataLoader.MEMBER_PASSWORD
                );
            }

            Clock clock = Clock.systemDefaultZone();

            // 1. Create the shared event bus.
            EventBus eventBus = new EventBus();

            // 2. Register event listeners.
            NotificationInbox notificationInbox =
                    new NotificationInbox(
                            ctx.notifications(),
                            ctx.ids()
                    );

            ActivityLog activityLog =
                    new ActivityLog(dataDir);

            eventBus.subscribe(notificationInbox);
            eventBus.subscribe(activityLog);

            // 3. Create the fine service.
            FineService fineService =
                    new FineService(
                            ctx.fines(),
                            ctx.loans(),
                            ctx.ids(),
                            clock
                    );

            // 4. Create the reservation service.
            ReservationService reservationService =
                    new ReservationService(
                            ctx.reservations(),
                            ctx.items(),
                            ctx.users(),
                            ctx.ids(),
                            clock,
                            eventBus
                    );

            // 5. Create the loan service.
            LoanService loanService =
                    new LoanService(
                            ctx.loans(),
                            ctx.items(),
                            ctx.users(),
                            ctx.fines(),
                            ctx.reservations(),
                            new MembershipPolicyFactory(),
                            ctx.ids(),
                            clock,
                            fineService,
                            reservationService,
                            eventBus
                    );

            // 6. Process expired reservations at startup.
            reservationService.expireReservations();

            System.out.printf(
                    "Loaded %d items, %d users, %d loans, "
                            + "%d reservations, %d fines.%n",
                    ctx.items().count(),
                    ctx.users().count(),
                    ctx.loans().count(),
                    ctx.reservations().count(),
                    ctx.fines().count()
            );

            System.out.println("Library services initialised successfully.");

            // TODO (Justina): Connect ReportService, CommandParser,
            // ConsoleUI and start the command loop here.

        } catch (LibraryException e) {

            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
