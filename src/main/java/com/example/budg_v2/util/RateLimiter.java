package com.example.budg_v2.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Simple rate limiter to prevent brute force attacks
 */
public class RateLimiter {
    
    private static final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_TIME = 15 * 60 * 1000; // 15 minutes
    private static final long RESET_TIME = 60 * 60 * 1000; // 1 hour
    
    private static class LoginAttempt {
        private int attempts;
        private long firstAttemptTime;
        private long lastAttemptTime;
        private boolean locked;
        
        public LoginAttempt() {
            this.attempts = 1;
            this.firstAttemptTime = System.currentTimeMillis();
            this.lastAttemptTime = System.currentTimeMillis();
            this.locked = false;
        }
        
        public void incrementAttempt() {
            this.attempts++;
            this.lastAttemptTime = System.currentTimeMillis();
            
            if (this.attempts >= MAX_ATTEMPTS) {
                this.locked = true;
            }
        }
        
        public boolean isLocked() {
            if (!locked) return false;
            
            // Check if lockout period has expired
            if (System.currentTimeMillis() - lastAttemptTime > LOCKOUT_TIME) {
                reset();
                return false;
            }
            
            return true;
        }
        
        public boolean shouldReset() {
            return System.currentTimeMillis() - firstAttemptTime > RESET_TIME;
        }
        
        public void reset() {
            this.attempts = 0;
            this.firstAttemptTime = System.currentTimeMillis();
            this.lastAttemptTime = System.currentTimeMillis();
            this.locked = false;
        }
        
        public int getAttempts() {
            return attempts;
        }
        
        public long getRemainingLockoutTime() {
            if (!locked) return 0;
            long elapsed = System.currentTimeMillis() - lastAttemptTime;
            return Math.max(0, LOCKOUT_TIME - elapsed);
        }
    }
    
    /**
     * Check if login is allowed for the given IP/email
     */
    public static boolean isLoginAllowed(String identifier) {
        cleanExpiredEntries();
        
        LoginAttempt attempt = loginAttempts.get(identifier);
        if (attempt == null) {
            return true;
        }
        
        if (attempt.shouldReset()) {
            attempt.reset();
            return true;
        }
        
        return !attempt.isLocked();
    }
    
    /**
     * Record a failed login attempt
     */
    public static void recordFailedAttempt(String identifier) {
        LoginAttempt attempt = loginAttempts.get(identifier);
        if (attempt == null) {
            attempt = new LoginAttempt();
            loginAttempts.put(identifier, attempt);
        } else {
            attempt.incrementAttempt();
        }
    }
    
    /**
     * Record a successful login and reset attempts
     */
    public static void recordSuccessfulLogin(String identifier) {
        LoginAttempt attempt = loginAttempts.get(identifier);
        if (attempt != null) {
            attempt.reset();
        }
    }
    
    /**
     * Get remaining lockout time in minutes
     */
    public static long getRemainingLockoutTime(String identifier) {
        LoginAttempt attempt = loginAttempts.get(identifier);
        if (attempt == null) return 0;
        
        long remainingMs = attempt.getRemainingLockoutTime();
        return (remainingMs + 59999) / 60000; // Convert to minutes, rounding up
    }
    
    /**
     * Get current attempt count
     */
    public static int getAttemptCount(String identifier) {
        LoginAttempt attempt = loginAttempts.get(identifier);
        return attempt != null ? attempt.getAttempts() : 0;
    }
    
    /**
     * Clean up expired entries to prevent memory leaks
     */
    private static void cleanExpiredEntries() {
        long now = System.currentTimeMillis();
        loginAttempts.entrySet().removeIf(entry -> {
            LoginAttempt attempt = entry.getValue();
            return attempt.shouldReset() && now - attempt.lastAttemptTime > RESET_TIME;
        });
    }
    
    /**
     * Get client identifier (IP + User-Agent for better tracking)
     */
    public static String getClientIdentifier(String ipAddress, String userAgent) {
        if (userAgent == null) userAgent = "unknown";
        return ipAddress + ":" + userAgent.hashCode();
    }
}
