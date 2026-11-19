
package com.library.service;

import com.library.event.EventBus;
import com.library.event.LibraryEvent;
import com.library.model.Copy;
import com.library.model.CopyStatus;
import com.library.model.Item;
import com.library.model.Member;
import com.library.model.Reservation;
import com.library.model.ReservationStatus;
import com.library.repository.ItemRepository;
import com.library.repository.LoanRepository;
import com.library.repository.ReservationRepository;
import com.library.repository.UserRepository;
import com.library.util.IdGenerator;
import com.library.util.IdGenerator.Kind;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class ReservationService implements ReservationServiceApi {

    private static final int HOLD_PERIOD_DAYS = 3;

    private final ReservationRepository reservations;
    private final ItemRepository items;
    private final LoanRepository loans;
    private final UserRepository users;
    private final IdGenerator ids;
    private final Clock clock;
    private final EventBus eventBus;

    public ReservationService(
            ReservationRepository reservations,
            ItemRepository items,
            UserRepository users,
            IdGenerator ids,
            Clock clock,
            EventBus eventBus
    ) {
        this(reservations, items, null, users, ids, clock, eventBus);
    }

    public ReservationService(
            ReservationRepository reservations,
            ItemRepository items,
            LoanRepository loans,
            UserRepository users,
            IdGenerator ids,
            Clock clock,
            EventBus eventBus
    ) {
        this.reservations = Objects.requireNonNull(reservations);
        this.items = Objects.requireNonNull(items);
        this.loans = loans;
        this.users = Objects.requireNonNull(users);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * Adds a member to an item's reservation queue.
     */
    @Override
    public synchronized Reservation placeReservation(String memberId, String itemId) {
        return reserveItem(memberId, itemId);
    }

    public synchronized Reservation reserveItem(
            String memberId,
            String itemId
    ) {
        Member member = users.findMemberById(memberId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Member not found: " + memberId
                        )
                );

        if (!member.isActive() || member.isLocked()) {
            throw new IllegalStateException(
                    "Suspended members cannot make reservations."
            );
        }

        Item item = items.findById(itemId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Item not found: " + itemId
                        )
                );

        if (!item.isBorrowable()) {
            throw new IllegalStateException(
                    "This item cannot be reserved."
            );
        }

        boolean alreadyReserved = reservations
                .findActiveByMemberId(memberId)
                .stream()
                .anyMatch(r -> r.getItemId().equals(itemId));

        if (alreadyReserved) {
            throw new IllegalStateException(
                    "Member already has an active reservation for this item."
            );
        }

        boolean alreadyBorrowed = loans != null
                && loans.findActiveByMemberId(memberId).stream()
                .anyMatch(loan -> loan.getItemId().equals(itemId));
        if (alreadyBorrowed) {
            throw new IllegalStateException("A member cannot reserve an item they have already collected.");
        }

        if (item.findAvailableCopy().isPresent()) {
            throw new IllegalStateException(
                    "A copy is currently available. Borrow it instead."
            );
        }

        Reservation reservation = new Reservation(
                ids.next(Kind.RESERVATION),
                itemId,
                memberId,
                LocalDateTime.now(clock)
        );

        reservations.save(reservation);

        return reservation;
    }

    /** Marks a returned or released copy as held for the first waiting member. */
    public synchronized Reservation makeNextReservationReady(
            String itemId,
            String copyId
    ) {
        Item item = items.findById(itemId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Item not found: " + itemId
                        )
                );

        Copy copy = item.findCopy(copyId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Copy not found: " + copyId
                        )
                );

        if (copy.getStatus() != CopyStatus.ON_LOAN
                && copy.getStatus() != CopyStatus.ON_HOLD) {
            throw new IllegalStateException("The copy cannot be placed on hold from " + copy.getStatus() + ".");
        }

        List<Reservation> queue =
                reservations.findActiveQueueForItem(itemId);

        Reservation next = queue.stream()
                .filter(r -> r.getStatus() == ReservationStatus.WAITING)
                .findFirst()
                .orElse(null);

        if (next == null) {
            return null;
        }

        LocalDate today = LocalDate.now(clock);

        next.setStatus(ReservationStatus.READY);
        next.setCopyId(copyId);
        next.setHoldExpiresOn(
                today.plusDays(HOLD_PERIOD_DAYS)
        );

        if (copy.getStatus() != CopyStatus.ON_HOLD) {
            copy.moveTo(CopyStatus.ON_HOLD);
        }

        reservations.save(next);
        items.save(item);

        publishEvent(
                LibraryEvent.EventType.RESERVATION_READY,
                next,
                "Your reserved item is ready for collection. Please collect it within 3 days."
        );

        return next;
    }

    /**
     * Cancels a member's active reservation.
     */
    @Override
    public synchronized void cancelReservation(
            String reservationId,
            String actorUserId
    ) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Reservation not found: " + reservationId
                        )
                );

        var actor = users.findById(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + actorUserId));
        if (actor.getRole() != com.library.model.Role.LIBRARIAN
                && !reservation.getMemberId().equals(actorUserId)) {
            throw new IllegalStateException(
                    "You cannot cancel another member's reservation."
            );
        }

        if (!reservation.getStatus().isActive()) {
            throw new IllegalStateException(
                    "This reservation is no longer active."
            );
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservations.save(reservation);

        if (reservation.getCopyId() != null) {
            Item item = items.findById(reservation.getItemId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Item not found: " + reservation.getItemId()
                            )
                    );

            Copy copy = item.findCopy(reservation.getCopyId())
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Reserved copy could not be found."
                            )
                    );

            boolean nextWaiting = reservations.findActiveQueueForItem(reservation.getItemId()).stream()
                    .anyMatch(r -> r.getStatus() == ReservationStatus.WAITING);
            if (nextWaiting) {
                makeNextReservationReady(reservation.getItemId(), copy.getCopyId());
            } else {
                copy.moveTo(CopyStatus.AVAILABLE);
                items.save(item);
            }
        }
    }

    /**
     * Expires reservations whose collection period has passed.
     * Call this at application startup and before relevant commands.
     */
    public synchronized void expireReservations() {
        LocalDate today = LocalDate.now(clock);

        for (Reservation reservation : reservations.findReady()) {

            if (reservation.getHoldExpiresOn() == null
                    || !today.isAfter(reservation.getHoldExpiresOn())) {
                continue;
            }

            String itemId = reservation.getItemId();
            String copyId = reservation.getCopyId();

            reservation.setStatus(ReservationStatus.EXPIRED);
            reservations.save(reservation);

            publishEvent(
                    LibraryEvent.EventType.RESERVATION_EXPIRED,
                    reservation,
                    "Your reservation has expired because the item was not collected in time."
            );

            if (copyId != null) {
                Item item = items.findById(itemId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Item not found: " + itemId
                                )
                        );

                Copy copy = item.findCopy(copyId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Reserved copy could not be found."
                                )
                        );

                boolean nextWaiting = reservations.findActiveQueueForItem(itemId).stream()
                        .anyMatch(r -> r.getStatus() == ReservationStatus.WAITING);
                if (nextWaiting) {
                    makeNextReservationReady(itemId, copyId);
                } else {
                    copy.moveTo(CopyStatus.AVAILABLE);
                    items.save(item);
                }
            }
        }
    }

    @Override
    public List<Reservation> getMemberReservations(
            String memberId
    ) {
        return reservations.findByMemberId(memberId);
    }

    public List<Reservation> getItemReservations(
            String itemId
    ) {
        return reservations.findByItemId(itemId);
    }

    public List<Reservation> getActiveQueue(
            String itemId
    ) {
        return reservations.findActiveQueueForItem(itemId);
    }

    private void publishEvent(
            LibraryEvent.EventType type,
            Reservation reservation,
            String message
    ) {
        eventBus.publish(
                new LibraryEvent(
                        type,
                        reservation.getMemberId(),
                        reservation.getItemId(),
                        null,
                        message,
                        LocalDateTime.now(clock)
                )
        );
    }
}
