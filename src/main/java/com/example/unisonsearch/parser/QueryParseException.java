package com.example.unisonsearch.parser;

/**
 * Exception thrown when query parsing fails due to syntax errors.
 */
public class QueryParseException extends Exception {

    public QueryParseException(String message) {
        super(message);
    }

    public QueryParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
