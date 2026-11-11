package com.library.exception;

/** Overdue, reservation waiting, or renewal limit reached. */
public class RenewalNotAllowedException extends LibraryException {
    public RenewalNotAllowedException(String message) { super(message); }
}
