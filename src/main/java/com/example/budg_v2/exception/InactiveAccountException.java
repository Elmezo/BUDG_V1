package com.example.budg_v2.exception;

/**
 * Exception thrown when a user with inactive status attempts to log in.
 */
public class InactiveAccountException extends Exception {

    private final String statusName;

    public InactiveAccountException(String email, String statusName) {
        super("Login denied for user '" + email + "' — account status is: " + statusName);
        this.statusName = statusName;
    }

    public String getStatusName() {
        return statusName;
    }
}
