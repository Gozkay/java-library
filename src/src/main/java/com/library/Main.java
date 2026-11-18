package com.library;

import com.library.cli.ConsoleUI;
import com.library.exception.LibraryException;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;
import com.library.service.AuthService;
import com.library.service.ReportService;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Application entry point (composition root). Wires storage (DataContext),
 * AuthService, ReportService, and the interactive ConsoleUI.
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
            Clock clock = Clock.systemDefaultZone();

            System.out.printf("Loaded %d items, %d users, %d loans, %d reservations, %d fines.%n",
                    ctx.items().count(), ctx.users().count(), ctx.loans().count(),
                    ctx.reservations().count(), ctx.fines().count());

            AuthService authService = new AuthService(ctx.users(), ctx.ids());
            ReportService reportService = new ReportService(ctx, clock);
            ConsoleUI ui = new ConsoleUI(ctx, authService, reportService, clock);
            ui.run();
        } catch (LibraryException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
