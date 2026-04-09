package com.example.unisonsearch.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request-scoped query cache to prevent duplicate query execution.
 * Uses ThreadLocal to ensure isolation between concurrent requests.
 */
public class QueryCache {
    
    // ThreadLocal cache for current request
    private static final ThreadLocal<Map<String, CachedResult>> CACHE = ThreadLocal.withInitial(HashMap::new);
    
    // ThreadLocal request ID for debugging
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    
    /**
     * Cached query result with metadata.
     */
    public static class CachedResult {
        private final List<Map<String, Object>> rows;
        private final long timestamp;
        
        public CachedResult(List<Map<String, Object>> rows) {
            this.rows = rows;
            this.timestamp = System.currentTimeMillis();
        }
        
        public List<Map<String, Object>> getRows() {
            return rows;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * Start a new request (clear cache and set request ID).
     * @param requestId Request identifier for logging
     */
    public static void startRequest(String requestId) {
        CACHE.get().clear();
        REQUEST_ID.set(requestId);
    }
    
    /**
     * End the current request (cleanup).
     */
    public static void endRequest() {
        CACHE.remove();
        REQUEST_ID.remove();
    }
    
    /**
     * Get cached query result.
     * @param cacheKey Unique key for this query (module + sqlHash + paramsHash)
     * @return Cached result or null if not found
     */
    public static CachedResult get(String cacheKey) {
        return CACHE.get().get(cacheKey);
    }
    
    /**
     * Put query result in cache.
     * @param cacheKey Unique key for this query
     * @param rows Query result rows
     */
    public static void put(String cacheKey, List<Map<String, Object>> rows) {
        CACHE.get().put(cacheKey, new CachedResult(rows));
    }
    
    /**
     * Check if query is cached.
     * @param cacheKey Unique key for this query
     * @return true if cached, false otherwise
     */
    public static boolean contains(String cacheKey) {
        return CACHE.get().containsKey(cacheKey);
    }
    
    /**
     * Get current request ID.
     * @return Request ID or null if not set
     */
    public static String getRequestId() {
        return REQUEST_ID.get();
    }
    
    /**
     * Generate cache key from module, SQL, and parameters.
     * @param module Module name
     * @param sql SQL query string
     * @param params Query parameters
     * @return Cache key
     */
    public static String generateKey(String module, String sql, List<Object> params) {
        int sqlHash = sql != null ? sql.hashCode() : 0;
        int paramsHash = params != null ? params.hashCode() : 0;
        return module + ":" + sqlHash + ":" + paramsHash;
    }
}

