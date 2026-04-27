package com.example.budg_v2.util;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Distributed rate limiter using database backend
 * Limits login attempts to MAX_ATTEMPTS_PER_MINUTE per minute per client identifier
 * Works across multiple servers in a distributed environment
 */
public class DistributedRateLimiter {
    
    private static final Logger logger = LoggerFactory.getLogger(DistributedRateLimiter.class);
    
    // Maximum login attempts allowed per minute
    private static final int MAX_ATTEMPTS_PER_MINUTE = 5;
    
    // Cleanup old records older than this (in minutes)
    private static final int CLEANUP_AGE_MINUTES = 5;
    
    /**
     * Check if login is allowed for the given client identifier
     * Implements sliding window: counts attempts in the last 1 minute
     * 
     * @param identifier Client identifier (IP + User-Agent hash)
     * @return true if login is allowed, false if rate limit exceeded
     */
    public static boolean isLoginAllowed(String identifier) {
        return isLoginAllowed(identifier, null);
    }
    
    /**
     * Check if login is allowed for the given client identifier and/or email
     * Implements sliding window: counts attempts in the last 1 minute
     * Checks both IP-based and email-based rate limiting
     * 
     * @param identifier Client identifier (IP + User-Agent hash)
     * @param email Email address (optional, for email-based rate limiting)
     * @return true if login is allowed, false if rate limit exceeded
     */
    public static boolean isLoginAllowed(String identifier, String email) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Clean up old records periodically
            cleanupOldRecords(conn);
            
            // Check IP-based rate limiting
            boolean ipAllowed = checkRateLimit(conn, identifier, null);
            
            // Check email-based rate limiting (if email provided)
            boolean emailAllowed = true;
            if (email != null && !email.trim().isEmpty() && !email.equals("unknown")) {
                emailAllowed = checkRateLimit(conn, null, email);
            }
            
            // Both must be allowed (AND condition)
            boolean allowed = ipAllowed && emailAllowed;
            
            if (!allowed) {
                if (!ipAllowed) {
                    logger.warn("Rate limit exceeded for client {}: {} attempts in last minute", 
                               identifier, getAttemptCount(identifier));
                }
                if (!emailAllowed && email != null) {
                    logger.warn("Rate limit exceeded for user {}: {} attempts in last minute",
                               maskEmailForLog(email), getAttemptCountByEmail(email));
                }
            }
            
            return allowed;
        } catch (SQLException e) {
            logger.error("Error checking rate limit for client: {}, user: {}", identifier, maskEmailForLog(email), e);
            // On error, allow the request (fail open) to prevent DoS on database
            return true;
        }
    }

    private static String maskEmailForLog(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return trimmed.substring(0, 1) + "***" + trimmed.substring(at);
    }
    
    /**
     * Check rate limit for either identifier or email
     */
    private static boolean checkRateLimit(Connection conn, String identifier, String email) throws SQLException {
        String sql;
        if (identifier != null) {
            // IP-based rate limiting
            sql = """
                SELECT COUNT(*) as attempt_count 
                FROM login_attempts 
                WHERE client_identifier = ? 
                AND attempt_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                AND success = FALSE
            """;
        } else if (email != null) {
            // Email-based rate limiting
            sql = """
                SELECT COUNT(*) as attempt_count 
                FROM login_attempts 
                WHERE email = ? 
                AND attempt_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                AND success = FALSE
            """;
        } else {
            return true; // No criteria, allow
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (identifier != null) {
                ps.setString(1, identifier);
            } else {
                ps.setString(1, email);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int attemptCount = rs.getInt("attempt_count");
                    return attemptCount < MAX_ATTEMPTS_PER_MINUTE;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Record a failed login attempt
     * 
     * @param identifier Client identifier
     * @param email Email/username attempted (optional, for logging)
     * @param ipAddress Client IP address
     */
    public static void recordFailedAttempt(String identifier, String email, String ipAddress) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                INSERT INTO login_attempts (client_identifier, email, attempt_time, success, ip_address)
                VALUES (?, ?, NOW(), FALSE, ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, identifier);
                ps.setString(2, email != null ? email : "unknown");
                ps.setString(3, ipAddress != null ? ipAddress : extractIpFromIdentifier(identifier));
                ps.executeUpdate();
                
                logger.debug("Recorded failed login attempt for client: {}, user: {}, ip: {}", identifier, maskEmailForLog(email), ipAddress);
            }
        } catch (SQLException e) {
            logger.error("Error recording failed login attempt for client: {}", identifier, e);
            // Don't throw - logging failure shouldn't block login
        }
    }
    
    /**
     * Record a successful login attempt
     * This helps with analytics and can be used to clear rate limits on success
     * 
     * @param identifier Client identifier
     * @param email Email/username that succeeded
     * @param ipAddress Client IP address
     */
    public static void recordSuccessfulLogin(String identifier, String email, String ipAddress) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                INSERT INTO login_attempts (client_identifier, email, attempt_time, success, ip_address)
                VALUES (?, ?, NOW(), TRUE, ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, identifier);
                ps.setString(2, email != null ? email : "unknown");
                ps.setString(3, ipAddress != null ? ipAddress : extractIpFromIdentifier(identifier));
                ps.executeUpdate();
                
                logger.debug("Recorded successful login for client: {}, user: {}, ip: {}", identifier, maskEmailForLog(email), ipAddress);
            }
        } catch (SQLException e) {
            logger.error("Error recording successful login for client: {}", identifier, e);
            // Don't throw - logging failure shouldn't block login
        }
    }
    
    /**
     * Get the number of attempts in the last minute for a client
     * 
     * @param identifier Client identifier
     * @return Number of failed attempts in the last minute
     */
    public static int getAttemptCount(String identifier) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT COUNT(*) as attempt_count 
                FROM login_attempts 
                WHERE client_identifier = ? 
                AND attempt_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                AND success = FALSE
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, identifier);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("attempt_count");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting attempt count for client: {}", identifier, e);
        }
        
        return 0;
    }
    
    /**
     * Get the number of attempts in the last minute for a specific email
     * 
     * @param email Email address
     * @return Number of failed attempts in the last minute for this email
     */
    public static int getAttemptCountByEmail(String email) {
        if (email == null || email.trim().isEmpty() || email.equals("unknown")) {
            return 0;
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT COUNT(*) as attempt_count 
                FROM login_attempts 
                WHERE email = ? 
                AND attempt_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                AND success = FALSE
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("attempt_count");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting attempt count for user: {}", maskEmailForLog(email), e);
        }
        
        return 0;
    }
    
    /**
     * Get remaining time until rate limit resets (in seconds)
     * Returns the time until the oldest attempt in the last minute expires
     * 
     * @param identifier Client identifier
     * @return Seconds until rate limit resets, or 0 if not rate limited
     */
    public static long getRemainingSeconds(String identifier) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT MIN(attempt_time) as oldest_attempt
                FROM login_attempts 
                WHERE client_identifier = ? 
                AND attempt_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                AND success = FALSE
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, identifier);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Timestamp oldestAttempt = rs.getTimestamp("oldest_attempt");
                        if (oldestAttempt != null) {
                            long oldestTime = oldestAttempt.getTime();
                            long currentTime = System.currentTimeMillis();
                            // Calculate remaining seconds until the oldest attempt expires (1 minute window)
                            long remaining = (oldestTime + 60000) - currentTime;
                            return Math.max(0, remaining / 1000); // Convert to seconds
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting remaining time for client: {}", identifier, e);
        }
        
        return 0;
    }
    
    /**
     * Clean up old login attempt records
     * Removes records older than CLEANUP_AGE_MINUTES to prevent database bloat
     * 
     * @param conn Database connection
     */
    private static void cleanupOldRecords(Connection conn) {
        try {
            // Only cleanup occasionally (10% chance) to reduce database load
            if (Math.random() > 0.1) {
                return;
            }
            
            String sql = """
                DELETE FROM login_attempts 
                WHERE attempt_time < DATE_SUB(NOW(), INTERVAL ? MINUTE)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, CLEANUP_AGE_MINUTES);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    logger.debug("Cleaned up {} old login attempt records", deleted);
                }
            }
        } catch (SQLException e) {
            logger.error("Error cleaning up old login attempt records", e);
            // Don't throw - cleanup failure shouldn't affect functionality
        }
    }
    
    /**
     * Extract IP address from identifier (fallback method)
     * Used when IP address is not directly provided
     * 
     * @param identifier Client identifier (format: IP:hash)
     * @return Extracted IP address or identifier if extraction fails
     */
    private static String extractIpFromIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return "unknown";
        }
        // Try to extract IP from identifier (format: IP:hash)
        // For IPv4: IP is before the first colon
        // For IPv6: IP is enclosed in brackets or uses colons, so we need a different approach
        int colonIndex = identifier.indexOf(':');
        if (colonIndex > 0) {
            String potentialIp = identifier.substring(0, colonIndex);
            // If it looks like an IP (contains dots for IPv4 or valid IPv6 format), return it
            if (potentialIp.contains(".") || potentialIp.contains(":")) {
                return potentialIp;
            }
        }
        // If extraction fails, return the identifier as fallback
        return identifier;
    }
    
    /**
     * Normalize IP address: convert IPv6 localhost (::1) to IPv4 (127.0.0.1)
     * This ensures consistent rate limiting regardless of IP version
     * 
     * @param ipAddress The IP address to normalize
     * @return Normalized IP address (IPv6 localhost becomes IPv4 localhost)
     */
    private static String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return "unknown";
        }
        // Convert IPv6 localhost to IPv4 localhost for consistency
        if ("::1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return "127.0.0.1";
        }
        return ipAddress;
    }
    
    /**
     * Get client identifier (IP + User-Agent for better tracking)
     * Same format as the old RateLimiter for compatibility
     * Normalizes IPv6 localhost to IPv4 for consistent rate limiting
     * 
     * @param ipAddress Client IP address
     * @param userAgent User-Agent header
     * @return Client identifier string
     */
    public static String getClientIdentifier(String ipAddress, String userAgent) {
        if (userAgent == null) userAgent = "unknown";
        String normalizedIp = normalizeIpAddress(ipAddress);
        return normalizedIp + ":" + userAgent.hashCode();
    }
    
    /**
     * Initialize the login_attempts table if it doesn't exist
     * This should be called during application startup
     */
    public static void initializeTable() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS login_attempts (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    client_identifier VARCHAR(255) NOT NULL,
                    email VARCHAR(255),
                    ip_address VARCHAR(45),
                    attempt_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    success BOOLEAN NOT NULL DEFAULT FALSE,
                    INDEX idx_client_time (client_identifier, attempt_time),
                    INDEX idx_attempt_time (attempt_time),
                    INDEX idx_client_identifier (client_identifier),
                    INDEX idx_email_time (email, attempt_time),
                    INDEX idx_email (email)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
                logger.info("Login attempts table initialized successfully");
            }
        } catch (SQLException e) {
            logger.error("Error initializing login_attempts table", e);
            throw new RuntimeException("Failed to initialize rate limiting table", e);
        }
    }
}

