package com.library.exception;

/** Member already holds or has reserved the item. */
public class DuplicateReservationException extends LibraryException {
    public DuplicateReservationException(String message) { super(message); }
}
