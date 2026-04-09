package com.example.budg_v2.exception;

/**
 * Exception thrown when LDAP connection fails
 */
public class LdapConnectionException extends Exception {
    
    public enum ErrorType {
        CONNECTION_REFUSED,
        SERVER_UNAVAILABLE,
        TIMEOUT,
        AUTHENTICATION_FAILED,
        UNKNOWN_ERROR
    }
    
    private final ErrorType errorType;
    private final String serverHost;
    private final int serverPort;
    
    public LdapConnectionException(ErrorType errorType, String message, String serverHost, int serverPort) {
        super(message);
        this.errorType = errorType;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }
    
    public LdapConnectionException(ErrorType errorType, String message, String serverHost, int serverPort, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }
    
    public ErrorType getErrorType() {
        return errorType;
    }
    
    public String getServerHost() {
        return serverHost;
    }
    
    public int getServerPort() {
        return serverPort;
    }
    
    /**
     * Get user-friendly error message based on error type
     */
    public String getUserFriendlyMessage() {
        switch (errorType) {
            case CONNECTION_REFUSED:
                return "Unable to connect to LDAP server. The server may not be running or the connection was refused. Please contact your system administrator.";
            case SERVER_UNAVAILABLE:
                return "LDAP server is not available. Please verify the server is running and accessible.";
            case TIMEOUT:
                return "Connection to LDAP server timed out. Please try again later or contact your system administrator.";
            case AUTHENTICATION_FAILED:
                return "LDAP authentication failed. Please verify your credentials.";
            case UNKNOWN_ERROR:
            default:
                return "An error occurred while connecting to LDAP server. Please contact your system administrator.";
        }
    }
}

