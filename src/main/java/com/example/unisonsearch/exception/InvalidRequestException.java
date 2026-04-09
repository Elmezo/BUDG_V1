package com.example.unisonsearch.exception;

/**
 * Thrown when request parameters are invalid.
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}


