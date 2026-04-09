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
 * Utility for checking if relationships exist before delete
 */
public class ExistenceChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(ExistenceChecker.class);
    
    /**
     * Check if relationship exists in database (no optional dataset).
     */
    public static DuplicateCheckResult checkExists(Connection conn, String tableName,
                                                   String entityAIdColumn, String entityBIdColumn,
                                                   String relationTypeColumn,
                                                   Map<String, Integer> entityIds,
                                                   Integer relationTypeId,
                                                   int rowNumber) {
        return checkExists(conn, tableName, entityAIdColumn, entityBIdColumn, relationTypeColumn,
            entityIds, relationTypeId, rowNumber, null);
    }
    
    /**
     * Check if relationship exists in database, with optional dataset ID for glossary_x_system.
     * For glossary_x_system, only a row matching (GlossaryID, SystemID, Strategic_DatasetID, Relation_TypeID) is considered "existing".
     *
     * @param optionalDatasetId For glossary_x_system only: resolved dataset ID, or null to match NULL Strategic_DatasetID. Ignored for other tables.
     */
    public static DuplicateCheckResult checkExists(Connection conn, String tableName,
                                                   String entityAIdColumn, String entityBIdColumn,
                                                   String relationTypeColumn,
                                                   Map<String, Integer> entityIds,
                                                   Integer relationTypeId,
                                                   int rowNumber,
                                                   Integer optionalDatasetId) {
        try {
            // Defensive null check for entityIds map
            if (entityIds == null) {
                logger.warn("Row {}: Cannot check existence - entityIds map is null", rowNumber);
                return DuplicateCheckResult.notFound();
            }
            
            Integer entityAId = entityIds.get("entityA");
            Integer entityBId = entityIds.get("entityB");
            
            // Product X Legal: Entity B (Legal) is required; Entity A (Product) is optional (can be NULL in DB)
            boolean isProductXLegal = "product_x_legal".equals(tableName);
            if (isProductXLegal) {
                if (entityBId == null) {
                    logger.warn("Row {}: Cannot check existence - Legal (entityB) ID is null for product_x_legal", rowNumber);
                    return DuplicateCheckResult.notFound();
                }
            } else {
                // All other tables: both entity IDs required
                if (entityAId == null || entityBId == null) {
                    logger.warn("Row {}: Cannot check existence - entity IDs are null (entityA={}, entityB={})", 
                        rowNumber, entityAId, entityBId);
                    return DuplicateCheckResult.notFound();
                }
            }
            
            // Build query
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ID FROM ").append(tableName);
            if (isProductXLegal && entityAId == null) {
                sql.append(" WHERE ").append(entityAIdColumn).append(" IS NULL")
                   .append(" AND ").append(entityBIdColumn).append(" = ?");
            } else {
                sql.append(" WHERE ").append(entityAIdColumn).append(" = ?")
                   .append(" AND ").append(entityBIdColumn).append(" = ?");
            }
            
            // Glossary X System: include Strategic_DatasetID so we only match the exact row (glossary, system, dataset, type)
            boolean isGlossaryXSystem = "glossary_x_system".equals(tableName);
            if (isGlossaryXSystem) {
                if (optionalDatasetId != null && optionalDatasetId > 0) {
                    sql.append(" AND Strategic_DatasetID = ?");
                } else {
                    sql.append(" AND Strategic_DatasetID IS NULL");
                }
            }
            
            // Add relationship type condition: align with DELETE (match by type when set, or only rows with no type when null)
            boolean hasRelationTypeColumn = relationTypeColumn != null && !relationTypeColumn.isEmpty();
            if (hasRelationTypeColumn) {
                if (relationTypeId != null && relationTypeId > 0) {
                    sql.append(" AND ").append(relationTypeColumn).append(" = ?");
                } else {
                    sql.append(" AND (").append(relationTypeColumn).append(" IS NULL OR ").append(relationTypeColumn).append(" = 0)");
                }
            }
            
            sql.append(" LIMIT 1");
            
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int param = 1;
                if (entityAId != null) {
                    ps.setInt(param++, entityAId.intValue());
                }
                ps.setInt(param++, entityBId.intValue());
                if (isGlossaryXSystem && optionalDatasetId != null && optionalDatasetId > 0) {
                    ps.setInt(param++, optionalDatasetId);
                }
                if (hasRelationTypeColumn && relationTypeId != null && relationTypeId > 0) {
                    ps.setInt(param++, relationTypeId);
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Integer existingId = rs.getInt("ID");
                        logger.debug("Row {}: Relationship exists - ID={}", rowNumber, existingId);
                        return DuplicateCheckResult.duplicate(existingId); // Reuse duplicate result to mean "exists"
                    }
                }
            }
            
            logger.debug("Row " + rowNumber + ": Relationship does not exist in database");
            return DuplicateCheckResult.notFound();
            
        } catch (SQLException e) {
            logger.error("Row {}: SQL error checking existence: {}", rowNumber, e.getMessage(), e);
            return DuplicateCheckResult.notFound();
        } catch (Exception e) {
            logger.error("Row {}: Unexpected error checking existence: {}", rowNumber, e.getMessage(), e);
            return DuplicateCheckResult.notFound();
        }
    }
}

