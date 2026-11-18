package com.library.exception;

/** Unpaid fines exceed the blocking limit. */
public class OutstandingFinesException extends LibraryException {
    public OutstandingFinesException(String message) { super(message); }
}
