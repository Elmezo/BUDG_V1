package com.example.unisonsearch.service;

import com.example.unisonsearch.model.SearchResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache manager for search results.
 * TTL: 5 minutes for normal results, 1 minute for dynamic data.
 */
public class SearchCacheManager {
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final long normalTtlMinutes = 5;
    private final long dynamicTtlMinutes = 1;
    
    public SearchCacheManager() {
        // Cleanup expired entries every minute
        scheduler.scheduleAtFixedRate(this::cleanupExpired, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * Get cached search result.
     */
    public SearchResponse getCachedSearch(String facet, int objectId) {
        String key = buildKey(facet, objectId);
        CacheEntry entry = cache.get(key);
        
        if (entry != null && !entry.isExpired()) {
            return entry.getResponse();
        }
        
        return null;
    }
    
    /**
     * Cache search result.
     */
    public void cacheSearch(String facet, int objectId, SearchResponse response, boolean isDynamic) {
        String key = buildKey(facet, objectId);
        long ttlMinutes = isDynamic ? dynamicTtlMinutes : normalTtlMinutes;
        cache.put(key, new CacheEntry(response, ttlMinutes));
    }
    
    /**
     * Evict cache for a specific search.
     */
    public void evictCache(String facet, int objectId) {
        String key = buildKey(facet, objectId);
        cache.remove(key);
    }
    
    /**
     * Clear all cache.
     */
    public void clearCache() {
        cache.clear();
    }
    
    private String buildKey(String facet, int objectId) {
        return facet + ":" + objectId;
    }
    
    private void cleanupExpired() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    private static class CacheEntry {
        private final SearchResponse response;
        private final long expireTime;
        
        CacheEntry(SearchResponse response, long ttlMinutes) {
            this.response = response;
            this.expireTime = System.currentTimeMillis() + (ttlMinutes * 60 * 1000);
        }
        
        SearchResponse getResponse() {
            return response;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}

