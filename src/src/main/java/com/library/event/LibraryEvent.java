
package com.library.event;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an event that has occurred in the library system.
 */
public record LibraryEvent(
        EventType type,
        String memberId,
        String itemId,
        String loanId,
        String message,
        LocalDateTime occurredAt
) {

    public LibraryEvent {
        Objects.requireNonNull(type, "Event type cannot be null");
        Objects.requireNonNull(message, "Event message cannot be null");
        Objects.requireNonNull(occurredAt, "Event time cannot be null");
    }

    public enum EventType {
        ITEM_RETURNED,
        RESERVATION_READY,
        RESERVATION_EXPIRED,
        LOAN_OVERDUE,
        FINE_CHARGED
    }
}
