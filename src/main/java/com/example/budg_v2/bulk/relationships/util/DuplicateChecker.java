package com.example.budg_v2.bulk.relationships.util;

import com.example.budg_v2.bulk.relationships.dto.DuplicateCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * Utility for checking duplicate relationships before insert
 */
public class DuplicateChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(DuplicateChecker.class);
    
    /**
     * Check if relationship already exists
     * 
     * Uniqueness constraint: (EntityA_ID, EntityB_ID, RelationshipType_ID) must be unique.
     * This means if Policy 1 and Process 1 have a "related to" relationship, we cannot
     * add another "related to" between them, but we can add a "supports" relationship.
     * 
     * @param conn Database connection
     * @param tableName Relationship table name
     * @param entityAIdColumn Entity A ID column name
     * @param entityBIdColumn Entity B ID column name
     * @param relationTypeColumn Relationship type column name (nullable)
     * @param entityIds Map containing resolved entity IDs
     * @param relationTypeId Relationship type ID (nullable)
     * @param rowNumber Row number for logging
     * @return Duplicate check result
     */
    public static DuplicateCheckResult checkDuplicate(Connection conn, String tableName,
                                                      String entityAIdColumn, String entityBIdColumn,
                                                      String relationTypeColumn,
                                                      Map<String, Integer> entityIds,
                                                      Integer relationTypeId,
                                                      int rowNumber) {
        return checkDuplicate(conn, tableName, entityAIdColumn, entityBIdColumn, relationTypeColumn,
            entityIds, relationTypeId, rowNumber, null);
    }
    
    /**
     * Check if relationship already exists, with optional dataset ID for glossary_x_system.
     * For glossary_x_system, uniqueness is (GlossaryID, SystemID, Strategic_DatasetID, Relation_TypeID).
     *
     * @param optionalDatasetId For glossary_x_system only: resolved dataset ID, or null to check for NULL Strategic_DatasetID. Ignored for other tables.
     */
    public static DuplicateCheckResult checkDuplicate(Connection conn, String tableName,
                                                      String entityAIdColumn, String entityBIdColumn,
                                                      String relationTypeColumn,
                                                      Map<String, Integer> entityIds,
                                                      Integer relationTypeId,
                                                      int rowNumber,
                                                      Integer optionalDatasetId) {
        try {
            // Defensive null check for entityIds map
            if (entityIds == null) {
                logger.warn("Row {}: Cannot check duplicate - entityIds map is null", rowNumber);
                return DuplicateCheckResult.noDuplicate();
            }
            
            // CRITICAL: Always check for null after Map.get() to prevent NullPointerException when calling intValue()
            Integer entityAId = entityIds.get("entityA");
            Integer entityBId = entityIds.get("entityB");
            
            // Explicit null checks before using entity IDs
            // This prevents NullPointerException if intValue() is called on null values
            if (entityAId == null || entityBId == null) {
                logger.warn("Row {}: Cannot check duplicate - entity IDs are null (entityA={}, entityB={}). " +
                    "Available keys in entityIds map: {}. " +
                    "This could cause NullPointerException if intValue() is called on these null values.",
                    rowNumber, entityAId, entityBId, entityIds.keySet());
                return DuplicateCheckResult.noDuplicate();
            }
            
            // Additional defensive check: ensure entity IDs are valid positive integers
            if (entityAId <= 0 || entityBId <= 0) {
                logger.warn("Row {}: Invalid entity IDs detected (entityA={}, entityB={}). " +
                    "Entity IDs must be positive integers. Skipping duplicate check.",
                    rowNumber, entityAId, entityBId);
                return DuplicateCheckResult.noDuplicate();
            }
            
            // Build query
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ID FROM ").append(tableName)
               .append(" WHERE ").append(entityAIdColumn).append(" = ?")
               .append(" AND ").append(entityBIdColumn).append(" = ?");
            
            // Glossary X System: include Strategic_DatasetID in uniqueness (same glossary+system+dataset+type = duplicate)
            boolean isGlossaryXSystem = "glossary_x_system".equals(tableName);
            if (isGlossaryXSystem) {
                if (optionalDatasetId != null && optionalDatasetId > 0) {
                    sql.append(" AND Strategic_DatasetID = ?");
                } else {
                    sql.append(" AND Strategic_DatasetID IS NULL");
                }
            }
            
            // Always include relationship type in uniqueness check if the relationship supports it
            // This ensures (EntityA, EntityB, RelationshipType) is unique; same pair can have multiple rows when relationship type differs (e.g. parent vs related)
            boolean hasRelationTypeColumn = relationTypeColumn != null && !relationTypeColumn.isEmpty();
            if (hasRelationTypeColumn) {
                if (relationTypeId != null && relationTypeId > 0) {
                    // Relationship type is specified - check for exact match
                    sql.append(" AND ").append(relationTypeColumn).append(" = ?");
                } else {
                    // Relationship type is NULL - check for NULL values
                    sql.append(" AND ").append(relationTypeColumn).append(" IS NULL");
                }
            }
            
            sql.append(" LIMIT 1");
            
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int param = 1;
                ps.setInt(param++, entityAId);
                ps.setInt(param++, entityBId);
                if (isGlossaryXSystem && optionalDatasetId != null && optionalDatasetId > 0) {
                    ps.setInt(param++, optionalDatasetId);
                }
                if (hasRelationTypeColumn && relationTypeId != null && relationTypeId > 0) {
                    ps.setInt(param++, relationTypeId);
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Integer existingId = rs.getInt("ID");
                        String relationshipTypeInfo = hasRelationTypeColumn 
                            ? (relationTypeId != null && relationTypeId > 0 
                                ? " with Relationship Type ID: " + relationTypeId 
                                : " with NULL Relationship Type")
                            : "";
                        String message = String.format(
                            "Duplicate relationship found: A relationship already exists between Entity A ID: %d and Entity B ID: %d%s. " +
                            "The combination of (Entity A, Entity B, Relationship Type) must be unique.",
                            entityAId, entityBId, relationshipTypeInfo);
                        logger.debug("Row {}: Duplicate relationship found - ID={}, EntityA={}, EntityB={}, RelationType={}", 
                            rowNumber, existingId, entityAId, entityBId, relationTypeId);
                        return new DuplicateCheckResult(true, existingId, message);
                    }
                }
            }
            
            return DuplicateCheckResult.noDuplicate();
            
        } catch (SQLException e) {
            logger.error("Row {}: SQL error checking duplicate: {}", rowNumber, e.getMessage(), e);
            // Return no duplicate to allow processing to continue
            return DuplicateCheckResult.noDuplicate();
        } catch (Exception e) {
            logger.error("Row {}: Unexpected error checking duplicate: {}", rowNumber, e.getMessage(), e);
            return DuplicateCheckResult.noDuplicate();
        }
    }
    
    /**
     * Simplified check when column names follow standard convention
     * 
     * Note: This method assumes the relationship table has a RelationType_ID column.
     * Uniqueness constraint: (EntityA_ID, EntityB_ID, RelationType_ID) must be unique.
     */
    public static DuplicateCheckResult checkDuplicateSimple(Connection conn, String tableName,
                                                            Integer entityAId, Integer entityBId,
                                                            Integer relationTypeId,
                                                            int rowNumber) {
        try {
            // CRITICAL: Always check for null to prevent NullPointerException when calling intValue()
            if (entityAId == null || entityBId == null) {
                logger.warn("Row {}: Cannot check duplicate - entity IDs are null (entityA={}, entityB={}). " +
                    "This could cause NullPointerException if intValue() is called on these null values.",
                    rowNumber, entityAId, entityBId);
                return DuplicateCheckResult.noDuplicate();
            }
            
            // Additional defensive check: ensure entity IDs are valid positive integers
            if (entityAId <= 0 || entityBId <= 0) {
                logger.warn("Row {}: Invalid entity IDs detected (entityA={}, entityB={}). " +
                    "Entity IDs must be positive integers. Skipping duplicate check.",
                    rowNumber, entityAId, entityBId);
                return DuplicateCheckResult.noDuplicate();
            }
            
            // Build query - assumes standard column names
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ID FROM ").append(tableName)
               .append(" WHERE ");
            
            // Try to infer column names from table name
            // e.g., capability_x_client -> Capability_ID, Client_ID
            String[] parts = tableName.split("_x_");
            if (parts.length == 2) {
                String entityACol = capitalize(parts[0]) + "_ID";
                String entityBCol = capitalize(parts[1]) + "_ID";
                
                sql.append(entityACol).append(" = ? AND ").append(entityBCol).append(" = ?");
                
                // Always include relationship type in uniqueness check
                // Check if RelationType_ID column exists by trying to include it
                // If relationTypeId is provided, check for exact match; if NULL, check for NULL
                if (relationTypeId != null && relationTypeId > 0) {
                    sql.append(" AND RelationType_ID = ?");
                } else {
                    // Check for NULL relationship type
                    sql.append(" AND RelationType_ID IS NULL");
                }
            } else {
                logger.warn("Row {}: Cannot infer column names from table '{}'", rowNumber, tableName);
                return DuplicateCheckResult.noDuplicate();
            }
            
            sql.append(" LIMIT 1");
            
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                // entityAId and entityBId are guaranteed to be non-null and positive at this point due to earlier checks
                // Safe to call setInt() without risk of NullPointerException
                ps.setInt(1, entityAId);
                ps.setInt(2, entityBId);
                
                if (relationTypeId != null && relationTypeId > 0) {
                    ps.setInt(3, relationTypeId);
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Integer existingId = rs.getInt("ID");
                        String relationshipTypeInfo = relationTypeId != null && relationTypeId > 0
                            ? " with Relationship Type ID: " + relationTypeId
                            : " with NULL Relationship Type";
                        String message = String.format(
                            "Duplicate relationship found: A relationship already exists between Entity A ID: %d and Entity B ID: %d%s. " +
                            "The combination of (Entity A, Entity B, Relationship Type) must be unique.",
                            entityAId, entityBId, relationshipTypeInfo);
                        return new DuplicateCheckResult(true, existingId, message);
                    }
                }
            }
            
            return DuplicateCheckResult.noDuplicate();
            
        } catch (SQLException e) {
            logger.error("Row {}: SQL error in simple duplicate check: {}", rowNumber, e.getMessage(), e);
            return DuplicateCheckResult.noDuplicate();
        }
    }
    
    /**
     * Helper: Capitalize first letter
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

