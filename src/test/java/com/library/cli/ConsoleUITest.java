package com.library.cli;

import com.library.model.Notification;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;
import com.library.service.AuthService;
import com.library.service.ReportService;
import com.library.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleUITest {
    private DataContext ctx;
    private AuthService authService;
    private ReportService reportService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        ctx = DataContext.inMemory();
        SeedDataLoader.seedIfEmpty(ctx);
        clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        authService = new AuthService(ctx.users(), ctx.ids());
        reportService = new ReportService(ctx, clock);
    }

    @Test
    void memberLoginDisplaysUnreadInboxAndInboxCommandMarksRead() {
        ctx.notifications().save(new Notification(
                ctx.ids().next(IdGenerator.Kind.NOTIFICATION),
                "M-1",
                "Your reserved book Clean Code is ready for pickup.",
                LocalDateTime.now(clock)
        ));

        String script = String.join("\n",
                "login M-1 pass123",
                "inbox",
                "logout",
                "exit"
        ) + "\n";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleUI ui = new ConsoleUI(
                ctx, authService, reportService, clock,
                new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(buffer, true, StandardCharsets.UTF_8)
        );
        ui.run();

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("1 unread notification(s)"));
        assertTrue(output.contains("Clean Code is ready for pickup"));
        assertTrue(ctx.notifications().findUnreadByMemberId("M-1").isEmpty());
    }

    @Test
    void librarianCanRegisterMemberAndRunSummaryReport() {
        String script = String.join("\n",
                "login admin admin123",
                "member add \"Ada Lovelace\" STAFF ada2026",
                "report summary",
                "exit"
        ) + "\n";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleUI ui = new ConsoleUI(
                ctx, authService, reportService, clock,
                new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(buffer, true, StandardCharsets.UTF_8)
        );
        ui.run();

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Registered member M-6 (Ada Lovelace)"));
        assertTrue(output.contains("LIBRARY COLLECTION & OPERATIONS SUMMARY"));
    }
}
