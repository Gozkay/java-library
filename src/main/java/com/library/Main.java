package com.library;

import com.library.exception.LibraryException;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Application entry point (composition root). Gozie wired the storage part; Justina plugs in the services and
 * the ConsoleUI where marked TODO.
 */
public final class Main {
    public static void main(String[] args) {
        Path dataDir = Path.of(System.getProperty("library.data.dir", "data"));
        try {
            DataContext ctx = DataContext.json(dataDir);
            if (SeedDataLoader.seedIfEmpty(ctx)) {
                System.out.println("First run: seed data created in " + dataDir.toAbsolutePath());
                System.out.println("  Librarian login: " + SeedDataLoader.LIBRARIAN_ID + " / " + SeedDataLoader.LIBRARIAN_PASSWORD);
                System.out.println("  Member logins:   M-1 .. M-5 / " + SeedDataLoader.MEMBER_PASSWORD);
            }
            Clock clock = Clock.systemDefaultZone(); // the ONLY place the real clock is created; services get it injected

            System.out.printf("Loaded %d items, %d users, %d loans, %d reservations, %d fines.%n",
                    ctx.items().count(), ctx.users().count(), ctx.loans().count(),
                    ctx.reservations().count(), ctx.fines().count());

            // TODO (Sommy): create AuthService/LoanService/ReservationService/FineService with ctx.* repositories, ctx.ids(), clock
            // TODO (Justina): create ReportService, CommandParser, ConsoleUI and start the command loop here
            System.out.println("CLI not wired yet - see docs/REPOSITORY_CONTRACT.md for how to plug in.");
        } catch (LibraryException e) { // includes DataCorruptionException: clear message, clean exit
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
