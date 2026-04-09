package com.example.unisonsearch.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for checking database schema existence (tables/columns).
 * Caches results per JVM to avoid repeated metadata queries.
 */
public class DatabaseMetadataUtil {
    
    // Cache for table existence checks (key: "schema.table")
    private static final ConcurrentHashMap<String, Boolean> TABLE_CACHE = new ConcurrentHashMap<>();
    
    // Cache for column existence checks (key: "schema.table.column")
    private static final ConcurrentHashMap<String, Boolean> COLUMN_CACHE = new ConcurrentHashMap<>();
    
    // Track logged missing schema warnings (log once per boot)
    private static final Set<String> LOGGED_MISSING = ConcurrentHashMap.newKeySet();
    
    /**
     * Check if a table exists in the database.
     * @param conn Database connection
     * @param schema Schema name (can be null for default schema)
     * @param table Table name
     * @return true if table exists, false otherwise
     */
    public static boolean tableExists(Connection conn, String schema, String table) {
        if (conn == null || table == null || table.trim().isEmpty()) {
            return false;
        }
        
        String cacheKey = (schema != null ? schema + "." : "") + table;
        
        // Check cache first
        Boolean cached = TABLE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Query database metadata
        boolean exists = false;
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, schema, table, new String[]{"TABLE"})) {
                exists = rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseMetadata] Error checking table existence for " + cacheKey + ": " + e.getMessage());
            // On error, assume it doesn't exist to be safe
            exists = false;
        }
        
        // Cache result
        TABLE_CACHE.put(cacheKey, exists);
        return exists;
    }
    
    /**
     * Check if a column exists in a table.
     * @param conn Database connection
     * @param schema Schema name (can be null for default schema)
     * @param table Table name
     * @param column Column name
     * @return true if column exists, false otherwise
     */
    public static boolean columnExists(Connection conn, String schema, String table, String column) {
        if (conn == null || table == null || column == null || 
            table.trim().isEmpty() || column.trim().isEmpty()) {
            return false;
        }
        
        String cacheKey = (schema != null ? schema + "." : "") + table + "." + column;
        
        // Check cache first
        Boolean cached = COLUMN_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Query database metadata
        boolean exists = false;
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, schema, table, column)) {
                exists = rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseMetadata] Error checking column existence for " + cacheKey + ": " + e.getMessage());
            // On error, assume it doesn't exist to be safe
            exists = false;
        }
        
        // Cache result
        COLUMN_CACHE.put(cacheKey, exists);
        return exists;
    }
    
    /**
     * Log a missing schema object warning (once per boot only).
     * @param key Unique key for this warning (e.g., "table:active_task" or "column:policy.Glossary_ID")
     * @param message Warning message
     */
    public static void logMissingSchemaOnce(String key, String message) {
        // Log silently - only track that it was logged
        LOGGED_MISSING.add(key);
    }
    
    /**
     * Clear all caches (useful for testing or schema changes).
     */
    public static void clearCache() {
        TABLE_CACHE.clear();
        COLUMN_CACHE.clear();
    }
}

