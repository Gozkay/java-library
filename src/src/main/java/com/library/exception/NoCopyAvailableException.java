package com.library.exception;

/** All copies are out; the CLI may suggest a reservation. */
public class NoCopyAvailableException extends LibraryException {
    public NoCopyAvailableException(String message) { super(message); }
}
