package com.example.budg_v2.bulk.relationships.dto;

/**
 * Statistics about cache performance during upload
 */
public class CacheStats {
    private int entityCacheHits;
    private int relationTypeCacheHits;
    private int totalQueries;
    
    public CacheStats() {
        this.entityCacheHits = 0;
        this.relationTypeCacheHits = 0;
        this.totalQueries = 0;
    }
    
    public void incrementEntityCacheHits() {
        this.entityCacheHits++;
    }
    
    public void incrementRelationTypeCacheHits() {
        this.relationTypeCacheHits++;
    }
    
    public void incrementTotalQueries() {
        this.totalQueries++;
    }
    
    // Getters and Setters
    public int getEntityCacheHits() {
        return entityCacheHits;
    }
    
    public void setEntityCacheHits(int entityCacheHits) {
        this.entityCacheHits = entityCacheHits;
    }
    
    public int getRelationTypeCacheHits() {
        return relationTypeCacheHits;
    }
    
    public void setRelationTypeCacheHits(int relationTypeCacheHits) {
        this.relationTypeCacheHits = relationTypeCacheHits;
    }
    
    public int getTotalQueries() {
        return totalQueries;
    }
    
    public void setTotalQueries(int totalQueries) {
        this.totalQueries = totalQueries;
    }
    
    @Override
    public String toString() {
        return "CacheStats{" +
                "entityCacheHits=" + entityCacheHits +
                ", relationTypeCacheHits=" + relationTypeCacheHits +
                ", totalQueries=" + totalQueries +
                '}';
    }
}

