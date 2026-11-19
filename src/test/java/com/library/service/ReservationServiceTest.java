
package com.library.service;

import com.library.event.EventBus;
import com.library.model.Copy;
import com.library.model.CopyStatus;
import com.library.model.Item;
import com.library.model.Reservation;
import com.library.model.ReservationStatus;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {

    private DataContext ctx;
    private ReservationService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        ctx = DataContext.inMemory();
        SeedDataLoader.seedIfEmpty(ctx);

        clock = Clock.fixed(
                Instant.parse("2026-11-19T09:00:00Z"),
                ZoneOffset.UTC
        );

        service = new ReservationService(
                ctx.reservations(),
                ctx.items(),
                ctx.users(),
                ctx.ids(),
                clock,
                new EventBus()
        );
    }

    private Item makeItemUnavailable() {
        Item item = ctx.items()
                .findById("I-1")
                .orElseThrow();

        for (Copy copy : item.getCopies()) {
            copy.moveTo(CopyStatus.ON_LOAN);
        }

        ctx.items().save(item);
        return item;
    }

    @Test
    void memberCanReserveUnavailableItem() {
        makeItemUnavailable();

        Reservation reservation =
                service.reserveItem("M-1", "I-1");

        assertNotNull(reservation);
        assertEquals(ReservationStatus.WAITING,
                reservation.getStatus());
        assertEquals("M-1", reservation.getMemberId());
        assertEquals("I-1", reservation.getItemId());
    }

    @Test
    void cannotReserveItemWhenCopyIsAvailable() {
        assertThrows(
                IllegalStateException.class,
                () -> service.reserveItem("M-1", "I-1")
        );
    }

    @Test
    void memberCannotMakeDuplicateActiveReservation() {
        makeItemUnavailable();

        service.reserveItem("M-1", "I-1");

        assertThrows(
                IllegalStateException.class,
                () -> service.reserveItem("M-1", "I-1")
        );
    }

    @Test
    void reservationsAreProcessedInFifoOrder() {
        Item item = makeItemUnavailable();

        Clock firstClock = Clock.fixed(
                Instant.parse("2026-11-19T09:00:00Z"),
                ZoneOffset.UTC
        );

        Clock secondClock = Clock.fixed(
                Instant.parse("2026-11-19T10:00:00Z"),
                ZoneOffset.UTC
        );

        ReservationService firstService =
                new ReservationService(
                        ctx.reservations(),
                        ctx.items(),
                        ctx.users(),
                        ctx.ids(),
                        firstClock,
                        new EventBus()
                );

        ReservationService secondService =
                new ReservationService(
                        ctx.reservations(),
                        ctx.items(),
                        ctx.users(),
                        ctx.ids(),
                        secondClock,
                        new EventBus()
                );

        Reservation first =
                firstService.reserveItem("M-1", "I-1");

        secondService.reserveItem("M-2", "I-1");

        Copy availableCopy = item.getCopies().get(0);
        availableCopy.moveTo(CopyStatus.AVAILABLE);
        ctx.items().save(item);

        Reservation ready =
                service.makeNextReservationReady(
                        "I-1",
                        availableCopy.getCopyId()
                );

        assertNotNull(ready);
        assertEquals(first.getReservationId(),
                ready.getReservationId());
        assertEquals(ReservationStatus.READY,
                ready.getStatus());
    }

    @Test
    void readyReservationCanBeCollected() {
        Item item = makeItemUnavailable();

        Reservation reservation =
                service.reserveItem("M-1", "I-1");

        Copy copy = item.getCopies().get(0);
        copy.moveTo(CopyStatus.AVAILABLE);
        ctx.items().save(item);

        service.makeNextReservationReady(
                "I-1",
                copy.getCopyId()
        );

        Reservation collected =
                service.collectReservation(
                        reservation.getReservationId()
                );

        assertEquals(
                ReservationStatus.COLLECTED,
                collected.getStatus()
        );
    }

    @Test
    void waitingReservationCannotBeCollected() {
        makeItemUnavailable();

        Reservation reservation =
                service.reserveItem("M-1", "I-1");

        assertThrows(
                IllegalStateException.class,
                () -> service.collectReservation(
                        reservation.getReservationId()
                )
        );
    }

    @Test
    void memberCanCancelOwnReservation() {
        makeItemUnavailable();

        Reservation reservation =
                service.reserveItem("M-1", "I-1");

        service.cancelReservation(
                reservation.getReservationId(),
                "M-1"
        );

        assertEquals(
                ReservationStatus.CANCELLED,
                ctx.reservations()
                        .findById(reservation.getReservationId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void expiredReservationReleasesCopy() {
        Item item = makeItemUnavailable();

        Copy copy = item.getCopies().get(0);
        copy.moveTo(CopyStatus.ON_HOLD);
        ctx.items().save(item);

        Reservation reservation = new Reservation(
                ctx.ids().next(
                        com.library.util.IdGenerator.Kind.RESERVATION
                ),
                "I-1",
                "M-1",
                LocalDateTime.now(clock)
        );

        reservation.setStatus(ReservationStatus.READY);
        reservation.setCopyId(copy.getCopyId());
        reservation.setHoldExpiresOn(
                LocalDate.now(clock).minusDays(1)
        );

        ctx.reservations().save(reservation);

        service.expireReservations();

        assertEquals(
                ReservationStatus.EXPIRED,
                ctx.reservations()
                        .findById(reservation.getReservationId())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                CopyStatus.AVAILABLE,
                item.findCopy(copy.getCopyId())
                        .orElseThrow()
                        .getStatus()
        );
    }
}
