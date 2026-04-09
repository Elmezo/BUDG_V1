package com.example.budg_v2.service;

import com.example.budg_v2.bulk.relationships.dto.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * Service for resolving relationship type IDs with session-level caching
 * Handles lookup table queries for relationship types
 */
public class RelationshipTypeLookupService {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipTypeLookupService.class);
    
    /**
     * Resolve relationship type ID from lookup table
     * 
     * @param conn Database connection
     * @param lookupTable Lookup table name (e.g., "capability_x_client_relationtype")
     * @param primaryName Value to look up (e.g., "Owns")
     * @param rowNumber Row number for error reporting
     * @param cache Session cache
     * @param cacheStats Cache statistics tracker
     * @return Relationship type ID or null if not found
     */
    public Integer resolveRelationshipType(Connection conn, String lookupTable, String primaryName,
                                          int rowNumber, Map<String, Integer> cache, 
                                          CacheStats cacheStats) {
        try {
            // Validate input
            if (lookupTable == null || lookupTable.trim().isEmpty()) {
                logger.error("Row {}: Lookup table name is null or empty", rowNumber);
                return null;
            }
            
            if (primaryName == null || primaryName.trim().isEmpty()) {
                logger.warn("Row {}: Relationship type name is null or empty", rowNumber);
                return null;
            }
            
            // Build cache key and check cache
            String cacheKey = buildCacheKey(lookupTable, primaryName);
            if (cache.containsKey(cacheKey)) {
                // CRITICAL: Always check for null after cache.get() to prevent NullPointerException
                // Even though containsKey() returns true, the value might still be null
                Integer cachedId = cache.get(cacheKey);
                if (cachedId != null) {
                    // Additional defensive check: ensure cachedId is a valid positive integer
                    if (cachedId > 0) {
                        cacheStats.incrementRelationTypeCacheHits();
                        logger.debug("Row {}: Cache hit for relationship type '{}' - ID={}", 
                            rowNumber, primaryName, cachedId);
                        return cachedId;
                    } else {
                        // Cached ID is invalid (0 or negative) - log and continue to query
                        logger.warn("Row {}: Cache key '{}' exists but contains invalid ID value {} for relationship type '{}'. " +
                            "Proceeding to query database.",
                            rowNumber, cacheKey, cachedId, primaryName);
                    }
                } else {
                    // Cache key exists but value is null - this is unexpected, log and continue to query
                    logger.warn("Row {}: Cache key '{}' exists but value is null for relationship type '{}'. " +
                        "This may indicate a cache corruption issue. Proceeding to query database. " +
                        "This could cause NullPointerException if intValue() is called on the null value.",
                        rowNumber, cacheKey, primaryName);
                }
            }
            
            // Query lookup table
            Integer id = queryLookupTable(conn, lookupTable, primaryName);
            cacheStats.incrementTotalQueries();
            
            if (id != null) {
                // Add to cache
                cache.put(cacheKey, id);
                logger.debug("Row {}: Resolved relationship type '{}' - ID={}", 
                    rowNumber, primaryName, id);
            } else {
                logger.warn("Row {}: Relationship type '{}' not found in table '{}'", 
                    rowNumber, primaryName, lookupTable);
            }
            
            return id;
            
        } catch (SQLException e) {
            logger.error("Row {}: SQL error resolving relationship type '{}': {}", 
                rowNumber, primaryName, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Row {}: Unexpected error resolving relationship type '{}': {}", 
                rowNumber, primaryName, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Query lookup table for relationship type ID.
     * Uses case-insensitive and trimmed comparison so values like "project is affecting system"
     * match "Project is affecting System" in the database.
     * If not found, tries a fallback: match DB PrimaryName with underscores replaced by spaces
     * so "Is_related_to" in DB matches "Is related to" from Excel.
     */
    private Integer queryLookupTable(Connection conn, String lookupTable, String primaryName) throws SQLException {
        // Normalize: trim and collapse multiple spaces so Excel variants (e.g. double space) still match
        String normalized = primaryName != null ? primaryName.trim().replaceAll("\\s+", " ") : "";
        // Case-insensitive match: LOWER(TRIM(PrimaryName)) = LOWER(?)
        String sql = "SELECT ID FROM " + lookupTable + " WHERE LOWER(TRIM(PrimaryName)) = LOWER(?) LIMIT 1";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalized);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        
        // Fallback: match where DB PrimaryName has underscores replaced by spaces (e.g. "Is_related_to" matches "Is related to")
        if (normalized != null && !normalized.isEmpty()) {
            String sqlFallback = "SELECT ID FROM " + lookupTable + " WHERE LOWER(REPLACE(TRIM(PrimaryName), '_', ' ')) = LOWER(?) LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlFallback)) {
                ps.setString(1, normalized);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ID");
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Build cache key for relationship type
     */
    public String buildCacheKey(String lookupTable, String primaryName) {
        String normalized = primaryName != null ? primaryName.trim().replaceAll("\\s+", " ") : "";
        return lookupTable + ":" + normalized;
    }
    
    /**
     * Validate that relationship type exists (for pre-validation)
     */
    public boolean existsInLookupTable(Connection conn, String lookupTable, String primaryName) {
        try {
            Integer id = queryLookupTable(conn, lookupTable, primaryName);
            return id != null;
        } catch (SQLException e) {
            logger.error("Error checking existence in lookup table '{}': {}", 
                lookupTable, e.getMessage(), e);
            return false;
        }
    }
}

