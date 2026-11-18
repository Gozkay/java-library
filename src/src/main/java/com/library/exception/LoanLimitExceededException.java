package com.library.exception;

/** Member is at their membership's max items on loan. */
public class LoanLimitExceededException extends LibraryException {
    public LoanLimitExceededException(String message) { super(message); }
}
