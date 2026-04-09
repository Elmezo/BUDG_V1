package com.example.budg_v2.service;

import com.example.budg_v2.model.EdcSettings;
import com.example.budg_v2.util.HttpClientUtil;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Client for EDC (Enterprise Data Catalog) API calls
 */
public class EdcClient {
    
    private static final Logger logger = LoggerFactory.getLogger(EdcClient.class);
    private static final String PRODUCT_INFO_ENDPOINT = "/access/2/catalog/data/productInformation";
    
    /**
     * Get product information from EDC
     * Calls: GET {host}:{port}/access/2/catalog/data/productInformation
     * 
     * @param settings EDC settings (host, port, credentials, etc.)
     * @return Product information with releaseVersion, buildVersion, buildDate
     * @throws EdcConnectionException if connection fails
     */
    public ProductInformation getProductInformation(EdcSettings settings) throws EdcConnectionException {
        if (settings == null) {
            throw new IllegalArgumentException("EDC settings cannot be null");
        }
        
        if (settings.getServerHost() == null || settings.getServerHost().trim().isEmpty()) {
            throw new IllegalArgumentException("EDC server host is required");
        }
        
        if (settings.getServerPort() == null || settings.getServerPort() <= 0) {
            throw new IllegalArgumentException("EDC server port is required");
        }
        
        if (settings.getLoginUsername() == null || settings.getLoginUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("EDC login username is required");
        }
        
        if (settings.getLoginPassword() == null || settings.getLoginPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("EDC login password is required");
        }
        
        try {
            // Build URL
            String baseUrl = settings.getServerUrl();
            if (baseUrl == null) {
                throw new IllegalArgumentException("Invalid EDC server URL");
            }
            
            // Remove trailing slash if present
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            
            String url = baseUrl + PRODUCT_INFO_ENDPOINT;
            
            logger.info("Calling EDC product information endpoint: {}", url);
            
            // Get timeout (convert seconds to milliseconds for HttpClientUtil)
            int timeout = settings.getRequestTimeout() != null ? settings.getRequestTimeout() : 120;
            
            // Get proxy settings
            String proxyHost = settings.getProxyHost();
            Integer proxyPort = settings.getProxyPort();
            if (proxyHost != null && (proxyHost.trim().isEmpty() || proxyPort == null || proxyPort <= 0)) {
                proxyHost = null;
                proxyPort = null;
            }
            
            // Get SSL insecure flag
            boolean sslInsecure = settings.isSslInsecure();
            logger.info("EDC SSL insecure mode: {}", sslInsecure);
            
            // Make GET request with Basic Auth
            JsonObject response = HttpClientUtil.getJsonObjectWithBasicAuth(
                url,
                settings.getLoginUsername(),
                settings.getLoginPassword(),
                timeout,
                proxyHost,
                proxyPort,
                sslInsecure
            );
            
            // Parse response
            if (response == null) {
                throw new EdcConnectionException("EDC API returned null response");
            }
            
            String releaseVersion = response.has("releaseVersion") ? response.get("releaseVersion").getAsString() : null;
            String buildVersion = response.has("buildVersion") ? response.get("buildVersion").getAsString() : null;
            String buildDate = response.has("buildDate") ? response.get("buildDate").getAsString() : null;
            
            ProductInformation productInfo = new ProductInformation();
            productInfo.releaseVersion = releaseVersion;
            productInfo.buildVersion = buildVersion;
            productInfo.buildDate = buildDate;
            
            logger.info("EDC product information retrieved successfully: {} build {} ({})", 
                releaseVersion, buildVersion, buildDate);
            
            return productInfo;
            
        } catch (IOException e) {
            logger.error("EDC connection failed", e);
            
            // Extract HTTP status if available
            int httpStatus = 0;
            String message = e.getMessage();
            
            // Try to parse status from error message
            if (message != null && message.contains("status")) {
                try {
                    String[] parts = message.split("status");
                    if (parts.length > 1) {
                        String statusPart = parts[1].trim();
                        int spaceIndex = statusPart.indexOf(' ');
                        if (spaceIndex > 0) {
                            httpStatus = Integer.parseInt(statusPart.substring(0, spaceIndex));
                        } else {
                            httpStatus = Integer.parseInt(statusPart);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore parsing errors
                }
            }
            
            throw new EdcConnectionException(
                "Failed to connect to EDC: " + e.getMessage(),
                httpStatus > 0 ? httpStatus : 0,
                e
            );
        } catch (Exception e) {
            logger.error("Unexpected error calling EDC API", e);
            throw new EdcConnectionException(
                "Unexpected error calling EDC API: " + e.getMessage(),
                0,
                e
            );
        }
    }
    
    /**
     * Product information response model
     */
    public static class ProductInformation {
        public String releaseVersion;
        public String buildVersion;
        public String buildDate;
    }
    
    /**
     * Exception for EDC connection errors
     */
    public static class EdcConnectionException extends Exception {
        private final int httpStatus;
        
        public EdcConnectionException(String message) {
            super(message);
            this.httpStatus = 0;
        }
        
        public EdcConnectionException(String message, int httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }
        
        public EdcConnectionException(String message, int httpStatus, Throwable cause) {
            super(message, cause);
            this.httpStatus = httpStatus;
        }
        
        public int getHttpStatus() {
            return httpStatus;
        }
        
        public String getUserFriendlyMessage() {
            if (httpStatus == 401) {
                return "Authentication failed: Invalid username or password";
            } else if (httpStatus == 403) {
                return "Access forbidden: User does not have permission to access EDC";
            } else if (httpStatus == 404) {
                return "EDC endpoint not found: Please verify the server URL and port";
            } else if (httpStatus >= 500) {
                return "EDC server error: The EDC server encountered an internal error";
            } else if (httpStatus > 0) {
                return String.format("EDC connection failed with HTTP status %d: %s", httpStatus, getMessage());
            } else {
                String msg = getMessage();
                if (msg != null && (msg.contains("SSL") || msg.contains("PKIX") || msg.contains("certificate"))) {
                    return "SSL certificate validation failed. The EDC server is using a self-signed or untrusted certificate. " +
                           "To fix this: (1) Import the EDC certificate into the JVM truststore (recommended for production), " +
                           "or (2) Enable 'Disable SSL Certificate Validation' option in EDC settings (for development/testing only).";
                } else if (msg != null && msg.contains("timeout")) {
                    return "Connection timeout: The EDC server did not respond within the configured timeout period";
                } else if (msg != null && msg.contains("refused")) {
                    return "Connection refused: Unable to connect to EDC server. Please verify the host and port.";
                } else {
                    return "Failed to connect to EDC: " + (msg != null ? msg : "Unknown error");
                }
            }
        }
    }
}

