package com.library.model;

public enum ReservationStatus {
    /** In the FIFO queue, no copy yet. */
    WAITING,
    /** A copy is on hold for this member (3-day collection window). */
    READY,
    /** The member collected the copy (became a loan). */
    COLLECTED,
    /** Not collected in time. */
    EXPIRED,
    /** Member cancelled (unreserve). */
    CANCELLED;

    /** Still occupying a place in the queue. */
    public boolean isActive() { return this == WAITING || this == READY; }
}
