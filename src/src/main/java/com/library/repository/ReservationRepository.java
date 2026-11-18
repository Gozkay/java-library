package com.library.repository;

import com.library.model.Reservation;
import com.library.util.IdGenerator;

import java.util.Comparator;
import java.util.List;

public interface ReservationRepository extends CrudRepository<Reservation, String> {

    /** FIFO order: oldest first; ties (same instant) broken by the number in the reservation ID. */
    Comparator<Reservation> FIFO = Comparator
            .comparing(Reservation::getCreatedAt)
            .thenComparingLong(r -> IdGenerator.numericPart(r.getReservationId()));

    /** Every reservation ever made for the item (any status), FIFO order. */
    default List<Reservation> findByItemId(String itemId) {
        return findAll().stream().filter(r -> r.getItemId().equals(itemId)).sorted(FIFO).toList();
    }

    /** The live queue for an item: WAITING and READY reservations, FIFO order. Position 1 = first element. */
    default List<Reservation> findActiveQueueForItem(String itemId) {
        return findAll().stream()
                .filter(r -> r.getItemId().equals(itemId) && r.getStatus().isActive())
                .sorted(FIFO).toList();
    }

    default List<Reservation> findByMemberId(String memberId) {
        return findAll().stream().filter(r -> r.getMemberId().equals(memberId)).sorted(FIFO).toList();
    }

    default List<Reservation> findActiveByMemberId(String memberId) {
        return findAll().stream()
                .filter(r -> r.getMemberId().equals(memberId) && r.getStatus().isActive())
                .sorted(FIFO).toList();
    }

    /** All READY reservations across items (used by the startup / per-command expiry check). */
    default List<Reservation> findReady() {
        return findAll().stream().filter(r -> r.getStatus() == com.library.model.ReservationStatus.READY).sorted(FIFO).toList();
    }
}
