package com.library.exception;

/** Bad credentials, or account locked after 3 failed logins. */
public class AuthenticationException extends LibraryException {
    public AuthenticationException(String message) { super(message); }
}
