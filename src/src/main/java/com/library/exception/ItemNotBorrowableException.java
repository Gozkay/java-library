package com.library.exception;

/** Reference or retired item cannot be borrowed. */
public class ItemNotBorrowableException extends LibraryException {
    public ItemNotBorrowableException(String message) { super(message); }
}
