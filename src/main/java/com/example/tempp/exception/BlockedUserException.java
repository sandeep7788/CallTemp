package com.example.tempp.exception;

/**
 * Exception thrown when a blocked user attempts to access the system
 */
public class BlockedUserException extends RuntimeException {

    public BlockedUserException(String message) {
        super(message);
    }

    public BlockedUserException(String message, Throwable cause) {
        super(message, cause);
    }
}

