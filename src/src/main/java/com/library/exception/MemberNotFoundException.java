package com.library.exception;

/** No member with the given ID. */
public class MemberNotFoundException extends LibraryException {
    public MemberNotFoundException(String message) { super(message); }
}
