package com.library.exception;

/** No catalogue item with the given ID. */
public class ItemNotFoundException extends LibraryException {
    public ItemNotFoundException(String message) { super(message); }
}
