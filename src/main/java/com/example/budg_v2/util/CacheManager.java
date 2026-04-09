package com.example.budg_v2.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory cache manager with TTL (Time To Live) support
 * Used for caching frequently accessed data like segments, user roles, etc.
 */
public class CacheManager {
    
    private static final CacheManager instance = new CacheManager();
    
    // Cache storage: key -> CacheEntry
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    
    // Locks for thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Default TTL values (in milliseconds)
    public static final long SEGMENTS_TTL = 5 * 60 * 1000; // 5 minutes
    public static final long SUPER_ADMIN_TTL = 10 * 60 * 1000; // 10 minutes
    public static final long ROLE_QUERY_TTL = 60 * 1000; // 1 minute
    
    private CacheManager() {
        // Start background thread to clean expired entries
        startCleanupThread();
    }
    
    public static CacheManager getInstance() {
        return instance;
    }
    
    /**
     * Get value from cache if not expired
     * @param key Cache key
     * @return Cached value or null if not found/expired
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        lock.readLock().lock();
        try {
            CacheEntry<?> entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            
            // Check if expired
            if (entry.isExpired()) {
                cache.remove(key);
                return null;
            }
            
            return (T) entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Put value in cache with default TTL
     * @param key Cache key
     * @param value Value to cache
     * @param ttl Time to live in milliseconds
     */
    public <T> void put(String key, T value, long ttl) {
        lock.writeLock().lock();
        try {
            cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttl));
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove entry from cache
     * @param key Cache key
     */
    public void remove(String key) {
        lock.writeLock().lock();
        try {
            cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Invalidate all entries matching a prefix
     * Useful for invalidating all entries related to a user
     * @param prefix Key prefix
     */
    public void invalidateByPrefix(String prefix) {
        lock.writeLock().lock();
        try {
            cache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear all cache entries
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get cache size
     * @return Number of entries in cache
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Start background thread to periodically clean expired entries
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // Run every minute
                    cleanupExpiredEntries();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("CacheManager-Cleanup");
        cleanupThread.start();
    }
    
    /**
     * Remove expired entries from cache
     */
    private void cleanupExpiredEntries() {
        lock.writeLock().lock();
        try {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Cache entry with expiration time
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long expirationTime;
        
        public CacheEntry(T value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }
        
        public T getValue() {
            return value;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
    
    // Helper methods for common cache keys
    
    public static String getSegmentsKey(int userId) {
        return "segments:user:" + userId;
    }
    
    public static String getSuperAdminKey(int userId) {
        return "superadmin:user:" + userId;
    }
    
    public static String getRoleQueryKey(String query) {
        return "rolequery:" + query.hashCode();
    }

    public static String getSelectedSegmentsKey(int userId) {
        return "selected_segments:user:" + userId;
    }

    public static String getEffectiveSegmentsKey(int userId) {
        return "effective_segments:user:" + userId;
    }

    public static String getUserRoleKey(int userId) {
        return "user_role:" + userId;
    }

    /** TTL for per-request-safe caches (user segment selection, effective segments) */
    public static final long SELECTED_SEGMENTS_TTL = 30 * 1000; // 30 seconds
    public static final long USER_ROLE_TTL = 5 * 60 * 1000;    // 5 minutes
}










