package com.library.exception;

/** Action not permitted for the current role. */
public class UnauthorizedActionException extends LibraryException {
    public UnauthorizedActionException(String message) { super(message); }
}
