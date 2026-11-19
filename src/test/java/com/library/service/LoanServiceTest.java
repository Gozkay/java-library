
package com.library.service;

import com.library.event.EventBus;
import com.library.event.LibraryEvent;
import com.library.exception.LoanLimitExceededException;
import com.library.factory.MembershipPolicyFactory;
import com.library.model.*;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoanServiceTest {

    private DataContext ctx;
    private Clock clock;
    private EventBus eventBus;
    private FineService fineService;
    private ReservationService reservationService;
    private LoanService loanService;

    @BeforeEach
    void setUp() {
        ctx = DataContext.inMemory();

        SeedDataLoader.seedIfEmpty(ctx);

        clock = Clock.fixed(
                Instant.parse("2026-11-19T10:00:00Z"),
                ZoneOffset.UTC
        );

        eventBus = new EventBus();

        fineService = new FineService(
                ctx.fines(),
                ctx.loans(),
                ctx.ids(),
                clock
        );

        reservationService = new ReservationService(
                ctx.reservations(),
                ctx.items(),
                ctx.users(),
                ctx.ids(),
                clock,
                eventBus
        );

        loanService = new LoanService(
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
    }

    @Test
    void memberCanBorrowAvailableBook() {
        Loan loan = loanService.borrowItem("M-1", "I-1");

        assertNotNull(loan);
        assertEquals("M-1", loan.getMemberId());
        assertEquals("I-1", loan.getItemId());

        assertEquals(
                LocalDate.of(2026, 11, 19),
                loan.getBorrowDate()
        );

        assertEquals(
                LocalDate.of(2026, 12, 10),
                loan.getDueDate()
        );

        Item item = ctx.items().findById("I-1").orElseThrow();

        assertEquals(
                CopyStatus.ON_LOAN,
                item.findCopy(loan.getCopyId()).orElseThrow().getStatus()
        );
    }

    @Test
    void memberCannotExceedBorrowingLimit() {
        loanService.borrowItem("M-1", "I-1");
        loanService.borrowItem("M-1", "I-2");
        loanService.borrowItem("M-1", "I-3");

        assertThrows(
                LoanLimitExceededException.class,
                () -> loanService.borrowItem("M-1", "I-4")
        );
    }

    @Test
    void referenceBookCannotBeBorrowed() {
        Item referenceBook = ctx.items()
                .findByType(ItemType.REFERENCE_BOOK)
                .get(0);

        assertThrows(
                RuntimeException.class,
                () -> loanService.borrowItem(
                        "M-1",
                        referenceBook.getItemId()
                )
        );
    }

    @Test
    void returningLateBookChargesFineAndPublishesEvents() {
        Loan loan = loanService.borrowItem("M-1", "I-1");

        loan.setDueDate(LocalDate.now(clock).minusDays(2));
        ctx.loans().save(loan);

        List<LibraryEvent> events = new ArrayList<>();
        eventBus.subscribe(events::add);

        Loan returnedLoan = loanService.returnItem(loan.getLoanId());

        assertEquals(LoanStatus.RETURNED, returnedLoan.getStatus());

        assertEquals(
                LocalDate.of(2026, 11, 19),
                returnedLoan.getReturnDate()
        );

        Fine fine = ctx.fines()
                .findByLoanId(loan.getLoanId())
                .get(0);

        assertEquals(new BigDecimal("100.00"), fine.getAmount());
        assertEquals(FineReason.LATE, fine.getReason());

        assertTrue(events.stream().anyMatch(
                e -> e.type() == LibraryEvent.EventType.ITEM_RETURNED
        ));

        assertTrue(events.stream().anyMatch(
                e -> e.type() == LibraryEvent.EventType.LOAN_OVERDUE
        ));

        assertTrue(events.stream().anyMatch(
                e -> e.type() == LibraryEvent.EventType.FINE_CHARGED
        ));
    }

    @Test
    void memberCanRenewLoanWithinAllowedLimit() {
        Loan loan = loanService.borrowItem("M-1", "I-1");

        LocalDate originalDueDate = loan.getDueDate();

        Loan renewedLoan = loanService.renewLoan(loan.getLoanId());

        assertEquals(
                originalDueDate.plusDays(21),
                renewedLoan.getDueDate()
        );

        assertEquals(1, renewedLoan.getRenewalCount());
    }

    @Test
    void memberCannotRenewBeyondAllowedLimit() {
        Loan loan = loanService.borrowItem("M-1", "I-1");

        loanService.renewLoan(loan.getLoanId());

        assertThrows(
                IllegalStateException.class,
                () -> loanService.renewLoan(loan.getLoanId())
        );
    }

    @Test
    void overdueLoanCannotBeRenewed() {
        Loan loan = loanService.borrowItem("M-1", "I-1");

        loan.setDueDate(LocalDate.now(clock).minusDays(1));
        ctx.loans().save(loan);

        assertThrows(
                IllegalStateException.class,
                () -> loanService.renewLoan(loan.getLoanId())
        );
    }

    @Test
    void lostItemIsMarkedLostAndReplacementFineIsCharged() {
        Loan loan = loanService.borrowItem("M-1", "I-1");

        List<LibraryEvent> events = new ArrayList<>();
        eventBus.subscribe(events::add);

        Loan lostLoan = loanService.markLost(
                loan.getLoanId(),
                new BigDecimal("2500.00")
        );

        assertEquals(LoanStatus.LOST, lostLoan.getStatus());

        Item item = ctx.items().findById("I-1").orElseThrow();

        assertEquals(
                CopyStatus.LOST,
                item.findCopy(loan.getCopyId()).orElseThrow().getStatus()
        );

        Fine fine = ctx.fines()
                .findByLoanId(loan.getLoanId())
                .get(0);

        assertEquals(new BigDecimal("2500.00"), fine.getAmount());
        assertEquals(FineReason.LOST, fine.getReason());

        assertTrue(events.stream().anyMatch(
                e -> e.type() == LibraryEvent.EventType.FINE_CHARGED
        ));
    }
}
