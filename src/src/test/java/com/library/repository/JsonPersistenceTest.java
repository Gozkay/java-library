package com.library.repository;

import com.library.exception.DataCorruptionException;
import com.library.model.*;
import com.library.util.IdGenerator.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Restart / persistence tests: write with one DataContext, "restart" by building a new one on the same folder. */
class JsonPersistenceTest {
    @TempDir Path dir;

    @Test void missingFilesAreCreated() {
        DataContext.json(dir);
        for (String f : List.of("users", "items", "loans", "reservations", "fines", "notifications")) {
            assertTrue(Files.exists(dir.resolve(f + ".json")), f + ".json should be created");
        }
    }

    @Test void itemSubclassesSurviveRestart() {
        DataContext a = DataContext.json(dir);
        SeedDataLoader.seedIfEmpty(a);
        DataContext b = DataContext.json(dir);

        assertEquals(22, b.items().count());
        Item clean = b.items().findAll().get(0);
        assertInstanceOf(Book.class, clean);
        assertEquals("Robert C. Martin", clean.getAuthor());
        assertEquals("9780132350884", ((Book) clean).getIsbn());
        assertEquals(21, clean.getBaseLoanPeriodDays());
        assertEquals(3, clean.getCopies().size());

        for (ItemType t : ItemType.values()) assertFalse(b.items().findByType(t).isEmpty(), "missing type " + t);
        assertInstanceOf(ReferenceBook.class, b.items().findByType(ItemType.REFERENCE_BOOK).get(0));
        assertFalse(b.items().findByType(ItemType.REFERENCE_BOOK).get(0).isBorrowable());
        assertEquals(7, b.items().findByType(ItemType.MAGAZINE).get(0).getBaseLoanPeriodDays());
        assertEquals(5, b.items().findByType(ItemType.DVD).get(0).getBaseLoanPeriodDays());
    }

    @Test void usersKeepTheirSubclassAndPasswordsAreHashed() throws Exception {
        SeedDataLoader.seedIfEmpty(DataContext.json(dir));
        DataContext b = DataContext.json(dir);

        assertEquals(5, b.users().findAllMembers().size());
        User admin = b.users().findById("admin").orElseThrow();
        assertEquals(Role.LIBRARIAN, admin.getRole());
        assertFalse(admin instanceof Member);
        assertTrue(com.library.util.PasswordHasher.matches("admin123", admin.getSalt(), admin.getPasswordHash()));

        Member m = b.users().findMemberById("M-5").orElseThrow();
        assertEquals(MembershipType.SENIOR, m.getMembershipType());
        assertEquals(MemberStatus.ACTIVE, m.getStatus());

        String raw = Files.readString(dir.resolve("users.json"));
        assertFalse(raw.contains("admin123"), "plain-text password must never be stored");
        assertFalse(raw.contains("pass123"));
    }

    @Test void loansFinesReservationsRoundTripWithIsoDatesAndStringMoney() throws Exception {
        DataContext a = DataContext.json(dir);
        a.loans().save(new Loan("L-31", "I-1", "I-1-C1", "M-1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22)));
        a.fines().save(new Fine("F-1", "M-1", "L-31", new BigDecimal("200.00"), FineReason.LATE, LocalDate.of(2026, 10, 26)));
        a.reservations().save(new Reservation("R-1", "I-1", "M-2", LocalDateTime.of(2026, 10, 2, 9, 30)));

        String loansJson = Files.readString(dir.resolve("loans.json"));
        assertTrue(loansJson.contains("\"2026-10-22\""), "dates must be ISO-8601 strings");
        assertTrue(Files.readString(dir.resolve("fines.json")).contains("\"200.00\""), "money must be a JSON string");

        DataContext b = DataContext.json(dir);
        Loan l = b.loans().findById("L-31").orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 22), l.getDueDate());
        assertNull(l.getReturnDate());
        Fine f = b.fines().findById("F-1").orElseThrow();
        assertEquals(new BigDecimal("200.00"), f.getAmount());
        assertEquals(FineStatus.UNPAID, f.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 2, 9, 30), b.reservations().findById("R-1").orElseThrow().getCreatedAt());
    }

    @Test void updatesPersistAfterSaveAndCopyStatusSurvives() {
        DataContext a = DataContext.json(dir);
        SeedDataLoader.seedIfEmpty(a);
        Item item = a.items().findById("I-1").orElseThrow();
        item.getCopies().get(0).moveTo(CopyStatus.ON_LOAN);
        a.items().save(item);

        DataContext b = DataContext.json(dir);
        Item reloaded = b.items().findById("I-1").orElseThrow();
        assertEquals(CopyStatus.ON_LOAN, reloaded.getCopies().get(0).getStatus());
        assertEquals(2, reloaded.countAvailable());
    }

    @Test void idsContinueAfterRestart() {
        DataContext a = DataContext.json(dir);
        SeedDataLoader.seedIfEmpty(a);
        a.loans().save(new Loan(a.ids().next(Kind.LOAN), "I-1", "I-1-C1", "M-1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22)));
        a.loans().save(new Loan(a.ids().next(Kind.LOAN), "I-2", "I-2-C1", "M-2", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22)));

        DataContext b = DataContext.json(dir);
        assertEquals("L-3", b.ids().next(Kind.LOAN));
        assertEquals("I-23", b.ids().next(Kind.ITEM));
        assertEquals("M-6", b.ids().next(Kind.MEMBER));
    }

    @Test void seedIsIdempotent() {
        DataContext a = DataContext.json(dir);
        assertTrue(SeedDataLoader.seedIfEmpty(a));
        assertFalse(SeedDataLoader.seedIfEmpty(a));
        DataContext b = DataContext.json(dir);
        assertFalse(SeedDataLoader.seedIfEmpty(b));
        assertEquals(22, b.items().count());
        assertEquals(6, b.users().count()); // 1 librarian + 5 members
    }

    @Test void seedMeetsBriefMinimums() {
        DataContext ctx = DataContext.inMemory();
        SeedDataLoader.seedIfEmpty(ctx);
        assertTrue(ctx.items().count() >= 20);
        assertEquals(5, ctx.users().findAllMembers().size());
        assertEquals(Set.of(MembershipType.values()), new HashSet<>(ctx.users().findAllMembers().stream().map(Member::getMembershipType).toList()));
    }

    @Test void corruptFileGivesClearMessageAndIsNotModified() throws Exception {
        Files.writeString(dir.resolve("items.json"), "[ { \"itemId\": \"I-1\", broken");
        String before = Files.readString(dir.resolve("items.json"));
        DataCorruptionException e = assertThrows(DataCorruptionException.class, () -> DataContext.json(dir));
        assertTrue(e.getMessage().contains("items.json"));
        assertTrue(e.getMessage().contains("corrupt"));
        assertEquals(before, Files.readString(dir.resolve("items.json")));
    }

    @Test void emptyFileUnknownTypeAndDuplicateIdsAreCorruption() throws Exception {
        Files.writeString(dir.resolve("items.json"), "");
        assertThrows(DataCorruptionException.class, () -> DataContext.json(dir));

        Files.writeString(dir.resolve("items.json"), "[{\"itemId\":\"I-1\",\"type\":\"VINYL\"}]");
        assertThrows(DataCorruptionException.class, () -> DataContext.json(dir));

        Files.writeString(dir.resolve("items.json"), "[]");
        String loan = "{\"loanId\":\"L-1\",\"itemId\":\"I-1\",\"copyId\":\"c\",\"memberId\":\"M-1\",\"borrowDate\":\"2026-10-01\",\"dueDate\":\"2026-10-22\"}";
        Files.writeString(dir.resolve("loans.json"), "[" + loan + "," + loan + "]");
        DataCorruptionException e = assertThrows(DataCorruptionException.class, () -> DataContext.json(dir));
        assertTrue(e.getMessage().contains("duplicate"));

        Files.writeString(dir.resolve("loans.json"), "[" + loan.replace("2026-10-01", "not-a-date") + "]");
        assertThrows(DataCorruptionException.class, () -> DataContext.json(dir));
    }

    @Test void concurrentSavesNeverCorruptTheFile() throws Exception {
        DataContext a = DataContext.json(dir);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            fs.add(pool.submit(() -> {
                for (int i = 0; i < 25; i++) {
                    a.loans().save(new Loan(a.ids().next(Kind.LOAN), "I-1", "c", "M-1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22)));
                }
            }));
        }
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals(200, DataContext.json(dir).loans().count());
    }

    @Test void queryHelpers() {
        DataContext ctx = DataContext.inMemory();
        LocalDate d = LocalDate.of(2026, 10, 1);
        Loan l1 = new Loan("L-1", "I-1", "c1", "M-1", d, d.plusDays(21));
        Loan l2 = new Loan("L-2", "I-2", "c2", "M-1", d.plusDays(10), d.plusDays(31));
        l1.setStatus(LoanStatus.RETURNED);
        ctx.loans().saveAll(List.of(l1, l2));
        assertEquals(1, ctx.loans().findActiveByMemberId("M-1").size());
        assertEquals(2, ctx.loans().findByMemberId("M-1").size());
        assertTrue(ctx.loans().findActiveByCopyId("c2").isPresent());
        assertEquals(1, ctx.loans().findBorrowedBetween(d.plusDays(5), d.plusDays(20)).size());

        // FIFO queue: same instant -> tie broken by numeric id (R-2 before R-10)
        LocalDateTime t = LocalDateTime.of(2026, 10, 2, 9, 0);
        Reservation r10 = new Reservation("R-10", "I-1", "M-2", t);
        Reservation r2 = new Reservation("R-2", "I-1", "M-3", t);
        Reservation old = new Reservation("R-5", "I-1", "M-4", t.minusHours(1));
        Reservation cancelled = new Reservation("R-6", "I-1", "M-5", t.minusHours(2));
        cancelled.setStatus(ReservationStatus.CANCELLED);
        ctx.reservations().saveAll(List.of(r10, r2, old, cancelled));
        assertEquals(List.of("R-5", "R-2", "R-10"),
                ctx.reservations().findActiveQueueForItem("I-1").stream().map(Reservation::getReservationId).toList());

        Fine paid = new Fine("F-1", "M-1", "L-1", new BigDecimal("50"), FineReason.LATE, d);
        paid.markPaid(d.plusDays(1));
        ctx.fines().saveAll(List.of(paid, new Fine("F-2", "M-1", "L-2", new BigDecimal("100"), FineReason.LATE, d)));
        assertEquals(1, ctx.fines().findUnpaidByMemberId("M-1").size());
    }

    @Test void copyLifecycleRulesFromTheBrief() {
        Book b = new Book("I-1", "T", "C", 2000, 1, "x", "a", "p", 1);
        Copy c = b.getCopies().get(0);
        assertEquals("I-1-C1", c.getCopyId());
        c.moveTo(CopyStatus.ON_LOAN);
        assertThrows(IllegalStateException.class, () -> c.moveTo(CopyStatus.RETIRED), "ON_LOAN cannot be retired");
        c.moveTo(CopyStatus.ON_HOLD);
        c.moveTo(CopyStatus.ON_HOLD);
        c.moveTo(CopyStatus.AVAILABLE);
        b.retire();
        assertEquals(CopyStatus.RETIRED, c.getStatus());
        assertFalse(b.isBorrowable());
        assertThrows(IllegalStateException.class, () -> c.moveTo(CopyStatus.AVAILABLE));
    }
}
