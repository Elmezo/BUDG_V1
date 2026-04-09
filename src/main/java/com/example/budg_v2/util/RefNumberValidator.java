package com.example.budg_v2.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.budg_v2.database.DatabaseConnection;

/**
 * Utility class for validating reference number uniqueness across all facets.
 * 
 * Rules:
 * 1. Can repeat ref if it's the same object in the same facet (when updating, if the value is unchanged, don't treat it as an error)
 * 2. Cannot repeat ref for different objects in the same facet
 */
public class RefNumberValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(RefNumberValidator.class);
    
    // Mapping of facet names to their table names, ref column names, deleted column names, and id column names
    private static final Map<String, FacetConfig> FACET_CONFIGS = new HashMap<>();
    
    static {
        // Initialize facet configurations
        // Format: facetName -> {tableName, refColumn, deletedColumn, idColumn}
        // Note: Column names must match exactly as defined in schema.sql
        
        // Dataset - uses RefNumber (capital R, capital N), DeletedDatetime, ID
        FACET_CONFIGS.put("Dataset", new FacetConfig("dataset", "RefNumber", "DeletedDatetime", "ID"));
        FACET_CONFIGS.put("dataset", new FacetConfig("dataset", "RefNumber", "DeletedDatetime", "ID"));
        FACET_CONFIGS.put("Data Set", new FacetConfig("dataset", "RefNumber", "DeletedDatetime", "ID"));
        
        // Glossary - uses Ref_Number (with underscore), Deleted_datetime, ID
        FACET_CONFIGS.put("Glossary", new FacetConfig("glossary", "Ref_Number", "Deleted_datetime", "ID"));
        FACET_CONFIGS.put("glossary", new FacetConfig("glossary", "Ref_Number", "Deleted_datetime", "ID"));
        
        // System - uses AssetID (not RefNumber), Deleted_datetime, id (lowercase)
        FACET_CONFIGS.put("System", new FacetConfig("system", "AssetID", "Deleted_datetime", "id"));
        FACET_CONFIGS.put("system", new FacetConfig("system", "AssetID", "Deleted_datetime", "id"));
        
        // Process - uses refnumber (lowercase), deleteddatetime (lowercase), id (lowercase)
        FACET_CONFIGS.put("Process", new FacetConfig("process", "refnumber", "deleteddatetime", "id"));
        FACET_CONFIGS.put("process", new FacetConfig("process", "refnumber", "deleteddatetime", "id"));
        
        // Project - uses refnumber (lowercase), deletedatetime (lowercase), id (lowercase)
        FACET_CONFIGS.put("Project", new FacetConfig("project", "refnumber", "deletedatetime", "id"));
        FACET_CONFIGS.put("project", new FacetConfig("project", "refnumber", "deletedatetime", "id"));
        
        // Capability - uses RefNumber (capital R, capital N), DeletedDatetime, ID
        FACET_CONFIGS.put("Capability", new FacetConfig("capability", "RefNumber", "DeletedDatetime", "ID"));
        FACET_CONFIGS.put("capability", new FacetConfig("capability", "RefNumber", "DeletedDatetime", "ID"));
        
        // Committee - uses RefNumber (capital R, capital N), DeleteDatetime (no 'd'), ID
        FACET_CONFIGS.put("Committee", new FacetConfig("committee", "RefNumber", "DeleteDatetime", "ID"));
        FACET_CONFIGS.put("committee", new FacetConfig("committee", "RefNumber", "DeleteDatetime", "ID"));
        
        // Policy - uses refNumber (camelCase), DeletedDatetime, ID
        FACET_CONFIGS.put("Policy", new FacetConfig("policy", "refNumber", "DeletedDatetime", "ID"));
        FACET_CONFIGS.put("policy", new FacetConfig("policy", "refNumber", "DeletedDatetime", "ID"));
        
        // Product - uses refnumber (lowercase), deleteddatetime (lowercase), id (lowercase)
        FACET_CONFIGS.put("Product", new FacetConfig("product", "refnumber", "deleteddatetime", "id"));
        FACET_CONFIGS.put("product", new FacetConfig("product", "refnumber", "deleteddatetime", "id"));
        
        // RegulatoryTheme - uses RefNumber (capital R, capital N), DeletedDatetime, ID
        FACET_CONFIGS.put("RegulatoryTheme", new FacetConfig("regulatorytheme", "RefNumber", "DeletedDatetime", "ID"));
        FACET_CONFIGS.put("regulatorytheme", new FacetConfig("regulatorytheme", "RefNumber", "DeletedDatetime", "ID"));
        
        // Regulation - uses RefNumber (capital R, capital N), DeletedDatetime, ID
        FACET_CONFIGS.put("Regulation", new FacetConfig("regulation", "RefNumber", "DeletedDatetime", "ID"));
        FACET_CONFIGS.put("regulation", new FacetConfig("regulation", "RefNumber", "DeletedDatetime", "ID"));
        
        // Attribute - uses RefNumber (capital R, capital N), DeletedDatetime, ID
        FACET_CONFIGS.put("Attribute", new FacetConfig("attribute", "RefNumber", "DeletedDatetime", "ID"));
        FACET_CONFIGS.put("attribute", new FacetConfig("attribute", "RefNumber", "DeletedDatetime", "ID"));
        
        // Interface - uses Ref_number (capital R, underscore, lowercase n), deleted_datetime (lowercase), id (lowercase)
        FACET_CONFIGS.put("Interface", new FacetConfig("interface", "Ref_number", "deleted_datetime", "id"));
        FACET_CONFIGS.put("interface", new FacetConfig("interface", "Ref_number", "deleted_datetime", "id"));
    }
    
    /**
     * Facet configuration class
     */
    private static class FacetConfig {
        final String tableName;
        final String refColumn;
        final String deletedColumn;
        final String idColumn;
        
        FacetConfig(String tableName, String refColumn, String deletedColumn, String idColumn) {
            this.tableName = tableName;
            this.refColumn = refColumn;
            this.deletedColumn = deletedColumn;
            this.idColumn = idColumn;
        }
    }
    
    /**
     * Check if a reference number is unique for a given facet (for create operations).
     * 
     * @param facetName The facet name (e.g., "Dataset", "Glossary", "System", "Process")
     * @param refNumber The reference number to validate
     * @return true if unique, false if duplicate exists
     * @throws SQLException if database error occurs
     */
    public static boolean isRefNumberUnique(String facetName, String refNumber) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return true; // Empty references are allowed
        }
        
        FacetConfig config = FACET_CONFIGS.get(facetName);
        if (config == null) {
            logger.warn("[RefNumberValidator] Facet '{}' not configured, skipping validation", facetName);
            return true; // If facet not configured, allow it
        }
        
        String normalizedRef = refNumber.trim();
        String sql = buildCheckQuery(config, false, (Integer) null);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, normalizedRef);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean isUnique = !rs.next();
                logger.debug("[RefNumberValidator] RefNumber '{}' for facet '{}' is unique: {}", normalizedRef, facetName, isUnique);
                return isUnique;
            }
        }
    }
    
    /**
     * Check if a reference number is unique for a given facet (for update operations).
     * Excludes the current object ID from the check, allowing the same ref for the same object.
     * Also excludes all cloned rows (nobject_ids) linked to the same original object_id.
     * This prevents duplicate reference numbers when updating cloned rows during active CR.
     * 
     * @param facetName The facet name (e.g., "Dataset", "Glossary", "System", "Process")
     * @param refNumber The reference number to validate
     * @param excludeId The object ID to exclude from the check (current object being updated)
     * @return true if unique, false if duplicate exists
     * @throws SQLException if database error occurs
     */
    public static boolean isRefNumberUniqueForUpdate(String facetName, String refNumber, int excludeId) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return true; // Empty references are allowed
        }
        
        FacetConfig config = FACET_CONFIGS.get(facetName);
        if (config == null) {
            logger.warn("[RefNumberValidator] Facet '{}' not configured, skipping validation", facetName);
            return true; // If facet not configured, allow it
        }
        
        String normalizedRef = refNumber.trim();
        
        // First, get the current RefNumber for the excludeId to check if it has changed
        String currentRefNumber = getCurrentRefNumber(facetName, excludeId, config);
        
        // If the RefNumber hasn't changed, it's unique (same object, same ref)
        if (currentRefNumber != null && currentRefNumber.trim().equalsIgnoreCase(normalizedRef)) {
            logger.debug("[RefNumberValidator] RefNumber '{}' for facet '{}' (ID {}) unchanged, skipping check", 
                normalizedRef, facetName, excludeId);
            return true;
        }
        
        // Check if excludeId is a cloned row (nobject_id) or the original (object_id)
        // If it's a cloned row, we need to find the original object_id
        int originalObjectId = findOriginalObjectId(facetName, excludeId);
        boolean excludeIdIsCloned = (originalObjectId != excludeId);
        
        if (excludeIdIsCloned) {
            logger.debug("[RefNumberValidator] excludeId {} is a cloned row, original object_id is {}", 
                excludeId, originalObjectId);
        }
        
        // Get all cloned row IDs (nobject_ids) linked to the original object_id
        // This prevents duplicate reference numbers when updating cloned rows during active CR
        List<Integer> clonedRowIds = getClonedRowIdsForOriginal(facetName, originalObjectId);
        
        // Build SQL query excluding both the original ID and all cloned row IDs
        String sql = buildCheckQueryWithExclusions(config, originalObjectId, excludeId, clonedRowIds);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, normalizedRef);
            int paramIndex = 2;
            
            // Set original object_id parameter (always exclude it)
            pstmt.setInt(paramIndex++, originalObjectId);
            
            // Set excludeId parameter (the row being updated - may be same as original or different)
            if (excludeId != originalObjectId) {
                pstmt.setInt(paramIndex++, excludeId);
            }
            
            // Set all cloned row IDs as exclusions
            for (Integer clonedId : clonedRowIds) {
                // Don't add excludeId again if it's already in the list
                // Don't add originalObjectId if it's in the list (shouldn't happen, but be safe)
                if (clonedId != excludeId && clonedId != originalObjectId) {
                    pstmt.setInt(paramIndex++, clonedId);
                }
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean isUnique = !rs.next();
                logger.debug("[RefNumberValidator] RefNumber '{}' for facet '{}' (excluding original ID {} and {} cloned rows) is unique: {}", 
                    normalizedRef, facetName, originalObjectId, clonedRowIds.size(), isUnique);
                return isUnique;
            }
        }
    }
    
    /**
     * Check if a reference number is unique for a given facet (for update operations), excluding the current row
     * and any additional IDs (e.g. "twin" rows in original/clone dataset for Attribute).
     *
     * @param facetName The facet name
     * @param refNumber The reference number to validate
     * @param excludeId The current object ID being updated
     * @param additionalExcludeIds Optional list of additional IDs to exclude (e.g. twin attribute in other dataset); may be null or empty
     * @return true if unique, false if duplicate exists
     * @throws SQLException if database error occurs
     */
    public static boolean isRefNumberUniqueForUpdate(String facetName, String refNumber, int excludeId, java.util.List<Integer> additionalExcludeIds) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return true;
        }
        FacetConfig config = FACET_CONFIGS.get(facetName);
        if (config == null) {
            logger.warn("[RefNumberValidator] Facet '{}' not configured, skipping validation", facetName);
            return true;
        }
        String normalizedRef = refNumber.trim();
        if (additionalExcludeIds == null || additionalExcludeIds.isEmpty()) {
            return isRefNumberUniqueForUpdate(facetName, refNumber, excludeId);
        }
        String sql = buildCheckQueryWithExclusions(config, excludeId, excludeId, additionalExcludeIds);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, normalizedRef);
            int paramIndex = 2;
            pstmt.setInt(paramIndex++, excludeId);
            for (Integer aid : additionalExcludeIds) {
                if (aid != null && aid != excludeId) {
                    pstmt.setInt(paramIndex++, aid);
                }
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean isUnique = !rs.next();
                logger.debug("[RefNumberValidator] RefNumber '{}' for facet '{}' (excluding ID {} and {} additional) is unique: {}",
                    normalizedRef, facetName, excludeId, additionalExcludeIds.size(), isUnique);
                return isUnique;
            }
        }
    }

    /**
     * Check if a reference number is unique for a given facet (for update operations with multiple exclusions).
     * Useful when updating cloned objects (exclude both original and cloned IDs).
     * 
     * @param facetName The facet name
     * @param refNumber The reference number to validate
     * @param excludeId1 First object ID to exclude
     * @param excludeId2 Second object ID to exclude
     * @return true if unique, false if duplicate exists
     * @throws SQLException if database error occurs
     */
    public static boolean isRefNumberUniqueForUpdate(String facetName, String refNumber, int excludeId1, int excludeId2) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return true; // Empty references are allowed
        }
        
        FacetConfig config = FACET_CONFIGS.get(facetName);
        if (config == null) {
            logger.warn("[RefNumberValidator] Facet '{}' not configured, skipping validation", facetName);
            return true; // If facet not configured, allow it
        }
        
        String normalizedRef = refNumber.trim();
        String sql = buildCheckQuery(config, true, excludeId1, excludeId2);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, normalizedRef);
            pstmt.setInt(2, excludeId1);
            pstmt.setInt(3, excludeId2);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean isUnique = !rs.next();
                logger.debug("[RefNumberValidator] RefNumber '{}' for facet '{}' (excluding IDs {} and {}) is unique: {}", 
                    normalizedRef, facetName, excludeId1, excludeId2, isUnique);
                return isUnique;
            }
        }
    }
    
    /**
     * Build SQL query for checking reference number uniqueness
     */
    private static String buildCheckQuery(FacetConfig config, boolean isUpdate, Integer... excludeIds) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 1 FROM `").append(config.tableName).append("` ");
        sql.append("WHERE LOWER(`").append(config.refColumn).append("`) = LOWER(?) ");
        
        // Exclude deleted records
        if (config.deletedColumn != null) {
            sql.append("AND (`").append(config.deletedColumn).append("` IS NULL OR `").append(config.deletedColumn).append("` = '') ");
        }
        
        // Exclude current object(s) for update operations
        if (isUpdate && excludeIds != null) {
            if (excludeIds.length == 1 && excludeIds[0] != null && excludeIds[0] > 0) {
                sql.append("AND `").append(config.idColumn).append("` != ? ");
            } else if (excludeIds.length == 2 && excludeIds[0] != null && excludeIds[1] != null) {
                sql.append("AND `").append(config.idColumn).append("` != ? ");
                sql.append("AND `").append(config.idColumn).append("` != ? ");
            }
        }
        
        sql.append("LIMIT 1");
        
        return sql.toString();
    }
    
    /**
     * Get all cloned row IDs (nobject_ids) linked to an original object_id.
     * These are rows created during active CR that represent pending changes.
     * When an object has an active CR, edits are saved in cloned rows, and we need
     * to exclude these cloned rows from reference number uniqueness checks.
     * 
     * IMPORTANT: We need to exclude ALL cloned rows (even from cancelled/completed CRs)
     * because when a CR is cancelled, the cloned rows may still exist in the database
     * until they are explicitly deleted. This prevents false duplicate errors when
     * creating a new CR after cancelling a previous one.
     * 
     * @param facetName The facet name
     * @param originalObjectId The original object ID
     * @return List of cloned row IDs (nobject_ids) linked to this object
     */
    private static List<Integer> getClonedRowIdsForOriginal(String facetName, int originalObjectId) throws SQLException {
        List<Integer> clonedIds = new ArrayList<>();
        
        // Map facet names to their changes table names
        String changesTableName = null;
        switch (facetName.toLowerCase()) {
            case "dataset":
            case "data set":
                changesTableName = "dataset_changes";
                break;
            case "glossary":
                changesTableName = "glossary_changes";
                break;
            case "system":
                changesTableName = "system_changes";
                break;
            case "process":
                changesTableName = "process_changes";
                break;
            default:
                // Facet doesn't support cloned rows or not configured
                return clonedIds;
        }
        
        // Get ALL nobject_ids linked to the original object_id
        // IMPORTANT: Include ALL cloned rows, even from cancelled/completed CRs
        // This is because when a CR is cancelled, cloned rows may still exist in the database
        // and we need to exclude them to prevent false duplicate reference number errors
        String sql = "SELECT DISTINCT fc.nobject_id " +
                     "FROM `" + changesTableName + "` fc " +
                     "INNER JOIN `changerequest` cr ON fc.change_request_id = cr.ID " +
                     "WHERE fc.object_id = ? " +
                     "AND cr.Deleted_At IS NULL";
        
        // Note: We don't filter by CR status here because we want to exclude ALL cloned rows
        // including those from cancelled/completed CRs that may still exist in the database
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, originalObjectId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    clonedIds.add(rs.getInt("nobject_id"));
                }
            }
        }
        
        logger.debug("[RefNumberValidator] Found {} cloned row(s) for {} (original object_id: {})", 
            clonedIds.size(), facetName, originalObjectId);
        
        return clonedIds;
    }

    /**
     * Get the current RefNumber for an object.
     * This is used to check if the RefNumber has changed during an update.
     * 
     * @param facetName The facet name
     * @param objectId The object ID
     * @param config The facet configuration
     * @return The current RefNumber, or null if not found
     */
    private static String getCurrentRefNumber(String facetName, int objectId, FacetConfig config) throws SQLException {
        String sql = "SELECT `" + config.refColumn + "` FROM `" + config.tableName + "` WHERE `" + config.idColumn + "` = ? LIMIT 1";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, objectId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(config.refColumn);
                }
            }
        }
        
        return null;
    }

    /**
     * Find the original object_id for a cloned row (nobject_id).
     * 
     * @param facetName The facet name
     * @param nobjectId The cloned row ID (nobject_id)
     * @return The original object_id, or nobjectId if not found (meaning it's already the original)
     */
    private static int findOriginalObjectId(String facetName, int nobjectId) throws SQLException {
        String changesTableName = null;
        switch (facetName.toLowerCase()) {
            case "dataset":
            case "data set":
                changesTableName = "dataset_changes";
                break;
            case "glossary":
                changesTableName = "glossary_changes";
                break;
            case "system":
                changesTableName = "system_changes";
                break;
            case "process":
                changesTableName = "process_changes";
                break;
            default:
                return nobjectId; // Not a facet with cloned rows
        }
        
        // Find the original object_id for this cloned row
        // IMPORTANT: Include ALL CRs (even cancelled/completed) because cloned rows
        // from cancelled CRs may still exist in the database until explicitly deleted
        String sql = "SELECT fc.object_id " +
                     "FROM `" + changesTableName + "` fc " +
                     "INNER JOIN `changerequest` cr ON fc.change_request_id = cr.ID " +
                     "WHERE fc.nobject_id = ? " +
                     "AND cr.Deleted_At IS NULL " +
                     "LIMIT 1";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, nobjectId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("object_id");
                }
            }
        }
        
        return nobjectId; // Not found, assume it's already the original
    }

    /**
     * Build SQL query for checking reference number uniqueness with multiple exclusions.
     * This is used when we need to exclude both the original object ID and all cloned row IDs.
     * 
     * @param config The facet configuration
     * @param originalObjectId The original object ID to exclude
     * @param excludeId The current object ID being updated (may be same as original or a cloned row)
     * @param additionalExcludeIds List of additional IDs to exclude (cloned rows)
     * @return SQL query string
     */
    private static String buildCheckQueryWithExclusions(FacetConfig config, int originalObjectId, int excludeId, List<Integer> additionalExcludeIds) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 1 FROM `").append(config.tableName).append("` ");
        sql.append("WHERE LOWER(`").append(config.refColumn).append("`) = LOWER(?) ");
        
        // Exclude deleted records
        if (config.deletedColumn != null) {
            sql.append("AND (`").append(config.deletedColumn).append("` IS NULL OR `").append(config.deletedColumn).append("` = '') ");
        }
        
        // Exclude original object ID (always exclude it)
        sql.append("AND `").append(config.idColumn).append("` != ? ");
        
        // Exclude the current excludeId if it's different from original
        if (excludeId != originalObjectId) {
            sql.append("AND `").append(config.idColumn).append("` != ? ");
        }
        
        // Exclude all cloned row IDs
        if (additionalExcludeIds != null && !additionalExcludeIds.isEmpty()) {
            // Count how many cloned IDs we'll actually exclude (excluding duplicates)
            int validClonedIds = 0;
            for (Integer clonedId : additionalExcludeIds) {
                if (clonedId != excludeId && clonedId != originalObjectId) {
                    validClonedIds++;
                }
            }
            
            if (validClonedIds > 0) {
                sql.append("AND `").append(config.idColumn).append("` NOT IN (");
                boolean first = true;
                for (Integer clonedId : additionalExcludeIds) {
                    if (clonedId != excludeId && clonedId != originalObjectId) {
                        if (!first) sql.append(", ");
                        sql.append("?");
                        first = false;
                    }
                }
                sql.append(") ");
            }
        }
        
        sql.append("LIMIT 1");
        
        return sql.toString();
    }
}
