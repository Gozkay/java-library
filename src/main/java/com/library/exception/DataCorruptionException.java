package com.library.exception;

/** A data file exists but cannot be parsed. Main prints the message and exits cleanly (exit code 1). */
public class DataCorruptionException extends LibraryException {
    public DataCorruptionException(String message) { super(message); }
    public DataCorruptionException(String message, Throwable cause) { super(message, cause); }
}
