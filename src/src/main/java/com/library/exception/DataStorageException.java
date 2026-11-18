package com.library.exception;

/** A data file could not be read or written (I/O problem). */
public class DataStorageException extends LibraryException {
    public DataStorageException(String message, Throwable cause) { super(message, cause); }
}
