package com.library.service;

import com.library.event.EventBus;
import com.library.event.LibraryEvent;
import com.library.factory.MembershipPolicyFactory;
import com.library.model.CopyStatus;
import com.library.model.Fine;
import com.library.model.LoanStatus;
import com.library.model.ReservationStatus;
import com.library.exception.UnauthorizedActionException;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryWorkflowTest {
    private DataContext context;
    private Clock clock;
    private ReservationService reservationService;
    private LoanService loanService;
    private List<LibraryEvent> events;

    @BeforeEach
    void setUp() {
        context = DataContext.inMemory();
        SeedDataLoader.seedIfEmpty(context);
        clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        events = new ArrayList<>();
        EventBus eventBus = new EventBus();
        eventBus.subscribe(events::add);
        reservationService = new ReservationService(
                context.reservations(), context.items(), context.loans(), context.users(),
                context.ids(), clock, eventBus);
        FineService fineService = new FineService(context.fines(), context.ids(), clock);
        loanService = new LoanService(
                context.loans(), context.items(), context.users(), context.fines(), context.reservations(),
                new MembershipPolicyFactory(), context.ids(), clock, fineService, reservationService, eventBus);
    }

    @Test
    void returnedCopyIsHeldForNextMemberAndCanBeCollected() {
        var first = loanService.borrowItem("M-1", "I-1");
        loanService.borrowItem("M-1", "I-1");
        loanService.borrowItem("M-1", "I-1");
        var reservation = reservationService.placeReservation("M-2", "I-1");

        loanService.returnCopy(first.getCopyId(), "M-1");

        assertEquals(ReservationStatus.READY,
                context.reservations().findById(reservation.getReservationId()).orElseThrow().getStatus());
        assertEquals(CopyStatus.ON_HOLD, context.items().findById("I-1").orElseThrow()
                .findCopy(first.getCopyId()).orElseThrow().getStatus());

        var collectedLoan = loanService.collectReservation(reservation.getReservationId(), "M-2");
        assertEquals(CopyStatus.ON_LOAN, context.items().findById("I-1").orElseThrow()
                .findCopy(first.getCopyId()).orElseThrow().getStatus());
        assertEquals("M-2", collectedLoan.getMemberId());
        assertEquals(ReservationStatus.COLLECTED,
                context.reservations().findById(reservation.getReservationId()).orElseThrow().getStatus());
    }

    @Test
    void lateReturnCreatesFineAndPublishesEvents() {
        var loan = loanService.borrowItem("M-1", "I-15");
        loan.setDueDate(clock.instant().atZone(clock.getZone()).toLocalDate().minusDays(3));
        context.loans().save(loan);

        loanService.returnCopy(loan.getCopyId(), "M-1");

        Fine fine = context.fines().findByLoanId(loan.getLoanId()).stream().findFirst().orElseThrow();
        assertEquals("150.00", fine.getAmount().toPlainString());
        assertNotNull(fine.getCreatedOn());
        assertEquals(List.of(LibraryEvent.EventType.FINE_CHARGED, LibraryEvent.EventType.ITEM_RETURNED),
                events.stream().map(LibraryEvent::type).toList());
    }

    @Test
    void memberCanOnlyReturnTheirOwnCopy() {
        var loan = loanService.borrowItem("M-1", "I-15");

        assertThrows(UnauthorizedActionException.class,
                () -> loanService.returnCopy(loan.getCopyId(), "M-2"));
        assertEquals(LoanStatus.ACTIVE, context.loans().findById(loan.getLoanId()).orElseThrow().getStatus());

        loanService.returnCopy(loan.getCopyId(), "M-1");
        assertEquals(LoanStatus.RETURNED, context.loans().findById(loan.getLoanId()).orElseThrow().getStatus());
    }

    @Test
    void lostItemRequiresLibrarianAndCreatesReplacementFine() {
        var loan = loanService.borrowItem("M-1", "I-15");

        assertThrows(UnauthorizedActionException.class, () -> loanService.markLost(loan.getLoanId(), "M-1"));

        loanService.markLost(loan.getLoanId(), "admin");
        assertEquals(CopyStatus.LOST, context.items().findById("I-15").orElseThrow()
                .findCopy(loan.getCopyId()).orElseThrow().getStatus());
        assertEquals("1000.00", context.fines().findByLoanId(loan.getLoanId()).get(0)
                .getAmount().toPlainString());
    }

    @Test
    void expiredHoldMovesTheNextWaiterIntoReadyState() {
        var firstLoan = loanService.borrowItem("M-1", "I-1");
        loanService.borrowItem("M-1", "I-1");
        loanService.borrowItem("M-1", "I-1");
        var firstReservation = reservationService.placeReservation("M-2", "I-1");
        loanService.returnCopy(firstLoan.getCopyId(), "M-1");
        var nextReservation = reservationService.placeReservation("M-3", "I-1");

        ReservationService afterHoldExpiry = new ReservationService(
                context.reservations(), context.items(), context.loans(), context.users(), context.ids(),
                Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC), new EventBus());
        afterHoldExpiry.expireReservations();

        assertEquals(ReservationStatus.EXPIRED, context.reservations()
                .findById(firstReservation.getReservationId()).orElseThrow().getStatus());
        assertEquals(ReservationStatus.READY, context.reservations()
                .findById(nextReservation.getReservationId()).orElseThrow().getStatus());
        assertEquals(CopyStatus.ON_HOLD, context.items().findById("I-1").orElseThrow()
                .findCopy(firstLoan.getCopyId()).orElseThrow().getStatus());
    }

    @Test
    void popularItemsHonorsInclusiveDateRange() {
        var loan = loanService.borrowItem("M-1", "I-15");
        ReportService reports = new ReportService(context, clock);

        assertTrue(reports.getPopularItems(5, loan.getBorrowDate(), loan.getBorrowDate()).stream()
                .anyMatch(item -> item.itemId().equals("I-15")));
        assertTrue(reports.getPopularItems(5, loan.getBorrowDate().plusDays(1), null).isEmpty());
    }
}
