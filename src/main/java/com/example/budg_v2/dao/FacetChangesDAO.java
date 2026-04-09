package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAO for facet-specific changes tables (glossary_changes, dataset_changes, system_changes, process_changes).
 * Handles mapping between original objects (object_id) and cloned rows (nobject_id) during active Change Requests.
 */
public class FacetChangesDAO {
    private static final Logger logger = LoggerFactory.getLogger(FacetChangesDAO.class);

    // Facet name to table name mapping
    private static final Map<String, String> FACET_TO_TABLE = new HashMap<>();
    private static final Map<String, Integer> FACET_NAME_TO_ID = new HashMap<>();
    
    static {
        FACET_TO_TABLE.put("glossary", "glossary_changes");
        FACET_TO_TABLE.put("dataset", "dataset_changes");
        FACET_TO_TABLE.put("system", "system_changes");
        FACET_TO_TABLE.put("process", "process_changes");
        
        FACET_NAME_TO_ID.put("glossary", 12);
        FACET_NAME_TO_ID.put("dataset", 11);
        FACET_NAME_TO_ID.put("data set", 11);
        FACET_NAME_TO_ID.put("system", 13);
        FACET_NAME_TO_ID.put("process", 4);
        FACET_NAME_TO_ID.put("people", 14);
        FACET_NAME_TO_ID.put("person", 14);
    }

    /**
     * Get facet ID from name
     */
    public Integer getFacetId(String facetName) {
        if (facetName == null) return null;
        return FACET_NAME_TO_ID.get(facetName.toLowerCase().trim());
    }

    /**
     * Get the changes table name for a facet
     */
    private String getChangesTableName(String facetName) {
        if (facetName == null) return null;
        return FACET_TO_TABLE.get(facetName.toLowerCase().trim());
    }

    /**
     * Check if object has any active CRs (status is Pending Start or Running)
     * Returns true if there are any CRs with status "Pending Start" or "Running"
     */
    public boolean hasActiveCRs(int facetType, int objectId) throws SQLException {
        String[] facetVariations = getFacetNameVariations(facetType);
        
        StringBuilder whereClauses = new StringBuilder();
        for (int i = 0; i < facetVariations.length; i++) {
            String variation = facetVariations[i];
            String exactRef = variation + " " + objectId;
            String pattern = "%" + variation + "%" + objectId + "%";
            
            if (i > 0) whereClauses.append(" OR ");
            whereClauses.append("cr.Reference = '").append(exactRef.replace("'", "''")).append("'");
            whereClauses.append(" OR LOWER(cr.Reference) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
        }
        
        String sql = "SELECT COUNT(*) as cnt " +
                     "FROM changerequest cr " +
                     "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                     "WHERE (" + whereClauses.toString() + ") " +
                     "AND cr.Deleted_At IS NULL " +
                     "AND crs.ID IS NOT NULL " +
                     "AND (LOWER(crs.PrimaryName) LIKE '%pending start%' " +
                     "     OR LOWER(crs.PrimaryName) LIKE '%running%' " +
                     "     OR LOWER(crs.PrimaryName) LIKE '%in progress%')";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int count = rs.getInt("cnt");
                logger.debug("Found {} active CRs for facetType={}, objectId={}", count, facetType, objectId);
                return count > 0;
            }
        }
        return false;
    }

    /**
     * Check if a person has any active CRs (Running or Pending Start) that they created.
     * This checks the Created_By column in changerequest, not the Reference field.
     * Used to block deletion of a person who has raised a CR that is still in progress.
     * 
     * @param personId The people ID (which is also the user/creator ID)
     * @return true if the person has any Running or Pending Start CRs they created
     */
    public boolean hasActiveCRsCreatedByPerson(int personId) throws SQLException {
        String sql = "SELECT COUNT(*) as cnt " +
                     "FROM changerequest cr " +
                     "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                     "WHERE cr.Created_By = ? " +
                     "AND cr.Deleted_At IS NULL " +
                     "AND crs.ID IS NOT NULL " +
                     "AND (LOWER(crs.PrimaryName) LIKE '%pending start%' " +
                     "     OR LOWER(crs.PrimaryName) LIKE '%running%' " +
                     "     OR LOWER(crs.PrimaryName) LIKE '%in progress%')";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, personId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("cnt");
                    logger.debug("Found {} active CRs created by person {}", count, personId);
                    return count > 0;
                }
            }
        }
        return false;
    }

    /**
     * Check if object has any active CRs that would block stakeholder deletion.
     * For auto CRs (mandatory_workflow = 1) with "Pending Start" status, stakeholders can be modified.
     * Returns true if there are any CRs with status "Running" or "In Progress", 
     * or non-auto CRs with "Pending Start" status.
     */
    public boolean hasActiveCRsBlockingStakeholderDeletion(int facetType, int objectId) throws SQLException {
        String[] facetVariations = getFacetNameVariations(facetType);
        
        StringBuilder whereClauses = new StringBuilder();
        for (int i = 0; i < facetVariations.length; i++) {
            String variation = facetVariations[i];
            String exactRef = variation + " " + objectId;
            String pattern = "%" + variation + "%" + objectId + "%";
            
            if (i > 0) whereClauses.append(" OR ");
            whereClauses.append("cr.Reference = '").append(exactRef.replace("'", "''")).append("'");
            whereClauses.append(" OR LOWER(cr.Reference) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
        }
        
        // Check for CRs that would block deletion:
        // 1. Running or In Progress CRs (regardless of auto/manual)
        // 2. Pending Start CRs that are NOT auto CRs (mandatory_workflow != 1)
        // Auto CRs with Pending Start status are allowed (stakeholders can be modified)
        String sql = "SELECT COUNT(*) as cnt " +
                     "FROM changerequest cr " +
                     "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                     "WHERE (" + whereClauses.toString() + ") " +
                     "AND cr.Deleted_At IS NULL " +
                     "AND crs.ID IS NOT NULL " +
                     "AND ( " +
                     "    LOWER(crs.PrimaryName) LIKE '%running%' " +
                     "    OR LOWER(crs.PrimaryName) LIKE '%in progress%' " +
                     "    OR (LOWER(crs.PrimaryName) LIKE '%pending start%' AND (cr.Mandatory_Workflow IS NULL OR cr.Mandatory_Workflow = 0)) " +
                     ")";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int count = rs.getInt("cnt");
                logger.debug("Found {} blocking CRs for stakeholder deletion (facetType={}, objectId={})", count, facetType, objectId);
                return count > 0;
            }
        }
        return false;
    }

    /**
     * Check if object has a running CR (status is Running or In Progress only).
     * Returns true if there is any CR with status "Running" or "In Progress".
     * Does not include "Pending Start".
     */
    public boolean hasRunningCR(int facetType, int objectId) throws SQLException {
        String[] facetVariations = getFacetNameVariations(facetType);
        
        StringBuilder whereClauses = new StringBuilder();
        for (int i = 0; i < facetVariations.length; i++) {
            String variation = facetVariations[i];
            String exactRef = variation + " " + objectId;
            String pattern = "%" + variation + "%" + objectId + "%";
            
            if (i > 0) whereClauses.append(" OR ");
            whereClauses.append("cr.Reference = '").append(exactRef.replace("'", "''")).append("'");
            whereClauses.append(" OR LOWER(cr.Reference) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
        }
        
        String sql = "SELECT COUNT(*) as cnt " +
                     "FROM changerequest cr " +
                     "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                     "WHERE (" + whereClauses.toString() + ") " +
                     "AND cr.Deleted_At IS NULL " +
                     "AND crs.ID IS NOT NULL " +
                     "AND (LOWER(crs.PrimaryName) LIKE '%running%' " +
                     "     OR LOWER(crs.PrimaryName) LIKE '%in progress%')";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int count = rs.getInt("cnt");
                logger.debug("Found {} running CRs for facetType={}, objectId={}", count, facetType, objectId);
                return count > 0;
            }
        }
        return false;
    }

    /**
     * Get the active CR ID for an object.
     * An object is "under revision" if there's a Change Request referencing it
     * with a status that is NOT Completed or Cancelled.
     */
    public Integer getActiveChangeRequestId(int facetType, int objectId) throws SQLException {
        String[] facetVariations = getFacetNameVariations(facetType);
        
        //system.out.println("[FacetChangesDAO] getActiveChangeRequestId: facetType=" + facetType + 
        //    ", objectId=" + objectId + ", variations=" + String.join(", ", facetVariations));
        
        // Build dynamic query for all facet name variations
        StringBuilder whereClauses = new StringBuilder();
        for (int i = 0; i < facetVariations.length; i++) {
            String variation = facetVariations[i];
            String exactRef = variation + " " + objectId;
            String pattern = "%" + variation + "%" + objectId + "%";
            
            if (i > 0) whereClauses.append(" OR ");
            whereClauses.append("cr.Reference = '").append(exactRef.replace("'", "''")).append("'");
            whereClauses.append(" OR LOWER(cr.Reference) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
            whereClauses.append(" OR LOWER(cr.PrimaryName) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
            whereClauses.append(" OR LOWER(cr.Summary) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
        }
        
        // Build SQL query using StringBuilder to avoid text block concatenation issues
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT cr.ID, cr.Reference, cr.CR_StatusID, cr.PrimaryName, cr.Summary, cr.Mandatory_Workflow, ");
        sqlBuilder.append("crs.ID as status_id, crs.PrimaryName as status_name ");
        sqlBuilder.append("FROM changerequest cr ");
        sqlBuilder.append("LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID ");
        sqlBuilder.append("WHERE (").append(whereClauses.toString()).append(") ");
        sqlBuilder.append("AND cr.Deleted_At IS NULL ");
        sqlBuilder.append("AND ( ");
        sqlBuilder.append("    cr.CR_StatusID IS NULL ");
        sqlBuilder.append("    OR crs.ID IS NULL ");
        sqlBuilder.append("    OR ( ");
        sqlBuilder.append("        LOWER(crs.PrimaryName) NOT LIKE '%complete%' ");
        sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%cancelled%' ");
        sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%canceled%' ");
        sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%reject%' ");
        sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%closed%' ");
        sqlBuilder.append("    ) ");
        sqlBuilder.append(") ");
        sqlBuilder.append("ORDER BY cr.Mandatory_Workflow DESC, cr.Created_At DESC ");
        sqlBuilder.append("LIMIT 1");
        
        String sql = sqlBuilder.toString();

        //system.out.println("[FacetChangesDAO] Executing CR lookup SQL: " + sql.substring(0, Math.min(200, sql.length())) + "...");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int crId = rs.getInt("ID");
                String crRef = rs.getString("Reference");
                String crStatus = rs.getString("status_name");
                int mandatoryWorkflow = rs.getInt("Mandatory_Workflow");
                //system.out.println("[FacetChangesDAO] Found active CR: id=" + crId + ", ref=" + crRef + 
                  //  ", status=" + crStatus + ", mandatoryWorkflow=" + mandatoryWorkflow);
                return crId;
            } else {
                //system.out.println("[FacetChangesDAO] No active CR found for facetType=" + facetType + ", objectId=" + objectId);
            }
        }
        return null;
    }

    /**
     * Get the active automatic CR ID for an object (only CRs with mandatory_workflow = true).
     * This is used for pending changes functionality, which should only work with automatic CRs.
     * An object is "under revision" if there's an automatic Change Request referencing it
     * with a status that is NOT Completed or Cancelled.
     */
    public Integer getActiveAutomaticChangeRequestId(int facetType, int objectId) throws SQLException {
        String[] facetVariations = getFacetNameVariations(facetType);
        
        // Build dynamic query for all facet name variations
        StringBuilder whereClauses = new StringBuilder();
        for (int i = 0; i < facetVariations.length; i++) {
            String variation = facetVariations[i];
            String exactRef = variation + " " + objectId;
            String pattern = "%" + variation + "%" + objectId + "%";
            
            if (i > 0) whereClauses.append(" OR ");
            whereClauses.append("cr.Reference = '").append(exactRef.replace("'", "''")).append("'");
            whereClauses.append(" OR LOWER(cr.Reference) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
            whereClauses.append(" OR LOWER(cr.PrimaryName) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
            whereClauses.append(" OR LOWER(cr.Summary) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
        }
        
        // Build SQL query - only return CRs with mandatory_workflow = true
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT cr.ID, cr.Reference, cr.CR_StatusID, cr.PrimaryName, cr.Summary, cr.Mandatory_Workflow, ");
        sqlBuilder.append("crs.ID as status_id, crs.PrimaryName as status_name ");
        sqlBuilder.append("FROM changerequest cr ");
        sqlBuilder.append("LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID ");
        sqlBuilder.append("WHERE (").append(whereClauses.toString()).append(") ");
        sqlBuilder.append("AND cr.Deleted_At IS NULL ");
        sqlBuilder.append("AND cr.Mandatory_Workflow = 1 ");  // Only automatic CRs
        sqlBuilder.append("AND ( ");
        sqlBuilder.append("    cr.CR_StatusID IS NULL ");
        sqlBuilder.append("    OR crs.ID IS NULL ");
        sqlBuilder.append("    OR ( ");
        sqlBuilder.append("        LOWER(crs.PrimaryName) NOT LIKE '%complete%' ");
        sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%cancelled%' ");
        sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%canceled%' ");
        sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%reject%' ");
        sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%closed%' ");
        sqlBuilder.append("    ) ");
        sqlBuilder.append(") ");
        sqlBuilder.append("ORDER BY cr.Created_At DESC ");
        sqlBuilder.append("LIMIT 1");
        
        String sql = sqlBuilder.toString();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int crId = rs.getInt("ID");
                logger.debug("Found active automatic CR {} for facetType={}, objectId={}", crId, facetType, objectId);
                return crId;
            }
        }
        return null;
    }

    /**
     * Check if an object has any pending changes for a specific area.
     */
    public boolean hasPendingChanges(String facetName, int objectId, String areaKey, Integer changeRequestId) throws SQLException {
        String tableName = getChangesTableName(facetName);
        if (tableName == null) return false;

        String sql = "SELECT COUNT(*) FROM " + tableName + 
                     " WHERE object_id = ? AND area_key = ?";
        
        if (changeRequestId != null) {
            sql += " AND change_request_id = ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, objectId);
            stmt.setString(2, areaKey);
            if (changeRequestId != null) {
                stmt.setInt(3, changeRequestId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Get all pending relationship IDs (nobject_ids) for a specific area key and CR.
     * Used for "Viewing Original" mode to exclude pending relationships from the display.
     * Returns a Set of relationship IDs that are pending (added during the active CR).
     */
    public java.util.Set<Integer> getPendingRelationshipIds(String facetName, int objectId, String areaKey, int changeRequestId) throws SQLException {
        java.util.Set<Integer> pendingIds = new java.util.HashSet<>();
        String tableName = getChangesTableName(facetName);
        if (tableName == null) return pendingIds;

        String sql = "SELECT nobject_id FROM " + tableName + 
                     " WHERE object_id = ? AND area_key = ? AND change_request_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            stmt.setString(2, areaKey);
            stmt.setInt(3, changeRequestId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pendingIds.add(rs.getInt("nobject_id"));
                }
            }
        }
        
        logger.debug("[FacetChangesDAO] getPendingRelationshipIds: facet={}, objectId={}, areaKey={}, crId={}, found {} pending IDs", 
            facetName, objectId, areaKey, changeRequestId, pendingIds.size());
        
        return pendingIds;
    }

    /**
     * Get the nobject_id (cloned row ID) for a specific area.
     * Returns null if no mapping exists.
     */
    public Integer getNObjectId(String facetName, int objectId, String areaKey, Integer changeRequestId) throws SQLException {
        String tableName = getChangesTableName(facetName);
        if (tableName == null) return null;

        String sql = "SELECT nobject_id FROM " + tableName + 
                     " WHERE object_id = ? AND area_key = ?";
        
        if (changeRequestId != null) {
            sql += " AND change_request_id = ?";
        }
        
        sql += " LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, objectId);
            stmt.setString(2, areaKey);
            if (changeRequestId != null) {
                stmt.setInt(3, changeRequestId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("nobject_id");
                }
            }
        }
        return null;
    }

    /**
     * Create or update a mapping between object_id and nobject_id for an area.
     * 
     * IMPORTANT: Different behavior for different area types:
     * - Single-row areas (summary only): Only one mapping per (change_request_id, object_id, area_key)
     *   When nobject_id changes, update the existing row instead of creating a new one.
     * - Multi-row areas (impact#*, data-content, documents#*, relationships#*): Check-then-insert
     *   These areas can have multiple mappings with different nobject_id values.
     *   Each distinct relationship/impact/document gets its own row.
     * 
     * NOTE: stakeholders#* areas are NOT handled here - stakeholders changes are applied
     * immediately and do not use auto CR mappings.
     */
    public void saveMapping(String facetName, int objectId, int nobjectId, String areaKey, int changeRequestId) throws SQLException {
        String tableName = getChangesTableName(facetName);
        if (tableName == null) {
            throw new SQLException("Unknown facet: " + facetName);
        }

        // CRITICAL: Log the mapping being saved to help debug issues
        logger.info("💾 [FacetChangesDAO] saveMapping called: facet={}, objectId={}, nobjectId={}, areaKey={}, crId={}", 
            facetName, objectId, nobjectId, areaKey, changeRequestId);
        
        // CRITICAL: Validate that nobjectId is not equal to objectId for relationship areas
        // This helps catch bugs where object_id is mistakenly used instead of relationship ID
        if (areaKey != null && areaKey.startsWith("relationships#") && nobjectId == objectId) {
            logger.error("❌ CRITICAL ERROR: Attempting to save relationship mapping with nobjectId={} equal to objectId={} for areaKey={}. " +
                "This is likely a bug - nobjectId should be the relationship record ID, not the object_id!", 
                nobjectId, objectId, areaKey);
            throw new SQLException("Invalid mapping: nobjectId cannot equal objectId for relationship areas. " +
                "nobjectId should be the relationship record ID, not the object_id. " +
                "objectId=" + objectId + ", nobjectId=" + nobjectId + ", areaKey=" + areaKey);
        }

        // Check if this is a multi-row area (allows multiple nobject_id per area_key)
        // Multi-row areas: impact#*, data-content, documents#*, relationships#*, summary#attribute
        // Single-row areas: summary only (excluding summary#attribute)
        // NOTE: stakeholders#* are not handled here (applied immediately, no auto CR)
        boolean isMultiRowArea = areaKey != null && (
            areaKey.startsWith("impact#") ||
            areaKey.equals("data-content") || 
            areaKey.startsWith("documents#") || 
            areaKey.startsWith("relationships#") ||
            areaKey.equals("summary#attribute")
        );

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (isMultiRowArea) {
                // Multi-row area: Check if this specific (cr, obj, area, nobject) combination exists
                // If not, insert it. If yes, just update the timestamp.
                // The unique constraint is (cr, obj, area, nobject) so different nobject_id values
                // create separate rows - which is exactly what we want for multi-row areas.
                String checkSql = "SELECT id FROM " + tableName + 
                                 " WHERE change_request_id = ? AND object_id = ? AND area_key = ? AND nobject_id = ?";
                
                Integer existingId = null;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, changeRequestId);
                    checkStmt.setInt(2, objectId);
                    checkStmt.setString(3, areaKey);
                    checkStmt.setInt(4, nobjectId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            existingId = rs.getInt("id");
                        }
                    }
                }
                
                if (existingId != null) {
                    // Row already exists with same nobject_id - just update timestamp
                    String updateSql = "UPDATE " + tableName + " SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, existingId);
                        updateStmt.executeUpdate();
                        logger.info("✅ [FacetChangesDAO] Updated existing multi-row mapping id={}, facet={}, objectId={}, nobjectId={}, areaKey={}, crId={}", 
                            existingId, facetName, objectId, nobjectId, areaKey, changeRequestId);
                    }
                } else {
                    // Insert new row - different nobject_id creates a new entry
                    String insertSql = "INSERT INTO " + tableName + 
                                      " (change_request_id, object_id, nobject_id, area_key) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, changeRequestId);
                        insertStmt.setInt(2, objectId);
                        insertStmt.setInt(3, nobjectId);
                        insertStmt.setString(4, areaKey);
                        insertStmt.executeUpdate();
                        logger.info("✅ [FacetChangesDAO] Inserted new multi-row mapping: facet={}, objectId={}, nobjectId={}, areaKey={}, crId={}", 
                            facetName, objectId, nobjectId, areaKey, changeRequestId);
                    }
                }
            } else {
                // Single-row area (summary only):
                // Only one mapping should exist per (cr, obj, area).
                // Since the unique constraint now includes nobject_id, we can't rely on ON DUPLICATE KEY UPDATE
                // when nobject_id changes. Instead, check if a row exists for (cr, obj, area) and update or insert.
                // NOTE: stakeholders#* are not handled here (applied immediately, no auto CR)
                String checkSql = "SELECT id FROM " + tableName + 
                                 " WHERE change_request_id = ? AND object_id = ? AND area_key = ?";
                
                Integer existingId = null;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, changeRequestId);
                    checkStmt.setInt(2, objectId);
                    checkStmt.setString(3, areaKey);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            existingId = rs.getInt("id");
                        }
                    }
                }
                
                if (existingId != null) {
                    // Update existing row - update nobject_id and timestamp
                    String updateSql = "UPDATE " + tableName + " SET nobject_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, nobjectId);
                        updateStmt.setInt(2, existingId);
                        updateStmt.executeUpdate();
                        logger.info("✅ [FacetChangesDAO] Updated single-row mapping id={}, facet={}, objectId={}, nobjectId={}, areaKey={}, crId={}", 
                            existingId, facetName, objectId, nobjectId, areaKey, changeRequestId);
                    }
                } else {
                    // Insert new row
                    String insertSql = "INSERT INTO " + tableName + 
                                      " (change_request_id, object_id, nobject_id, area_key) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, changeRequestId);
                        insertStmt.setInt(2, objectId);
                        insertStmt.setInt(3, nobjectId);
                        insertStmt.setString(4, areaKey);
                        insertStmt.executeUpdate();
                        logger.info("✅ [FacetChangesDAO] Inserted new single-row mapping: facet={}, objectId={}, nobjectId={}, areaKey={}, crId={}", 
                            facetName, objectId, nobjectId, areaKey, changeRequestId);
                    }
                }
            }
        }
    }

    /**
     * Delete all mappings for a change request (on cancel).
     */
    public void deleteByChangeRequest(String facetName, int changeRequestId) throws SQLException {
        String tableName = getChangesTableName(facetName);
        if (tableName == null) return;

        String sql = "DELETE FROM " + tableName + " WHERE change_request_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            stmt.executeUpdate();
        }
    }

    /**
     * Get all mappings for an object (all areas).
     */
    public Map<String, Integer> getAllMappings(String facetName, int objectId, Integer changeRequestId) throws SQLException {
        String tableName = getChangesTableName(facetName);
        Map<String, Integer> mappings = new HashMap<>();
        
        if (tableName == null) return mappings;

        String sql = "SELECT area_key, nobject_id FROM " + tableName + 
                     " WHERE object_id = ?";
        
        if (changeRequestId != null) {
            sql += " AND change_request_id = ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, objectId);
            if (changeRequestId != null) {
                stmt.setInt(2, changeRequestId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String areaKey = rs.getString("area_key");
                    // Exclude stakeholders mappings - stakeholders are excluded from pending changes
                    // Stakeholders changes are applied immediately even with active CR
                    if (areaKey != null && areaKey.startsWith("stakeholders#")) {
                        continue;
                    }
                    mappings.put(areaKey, rs.getInt("nobject_id"));
                }
            }
        }
        return mappings;
    }
    
    /**
     * Get all nobject_id values for a specific object and area_key.
     * Used for relationship tracking where multiple relationships can be added.
     * @return List of nobject_id values for the given area key
     */
    public List<Integer> getAllNObjectIds(String facetName, int objectId, String areaKey, Integer changeRequestId) throws SQLException {
        String tableName = getChangesTableName(facetName);
        List<Integer> nobjectIds = new java.util.ArrayList<>();
        
        if (tableName == null) return nobjectIds;
        
        String sql = "SELECT nobject_id FROM " + tableName + 
                     " WHERE object_id = ? AND area_key = ?";
        
        if (changeRequestId != null) {
            sql += " AND change_request_id = ?";
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, objectId);
            stmt.setString(2, areaKey);
            if (changeRequestId != null) {
                stmt.setInt(3, changeRequestId);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    nobjectIds.add(rs.getInt("nobject_id"));
                }
            }
        }
        return nobjectIds;
    }

    /**
     * Get all mappings for a change request (all objects and areas).
     * Returns a map: facetName -> (objectId -> (areaKey -> nobjectId))
     */
    public Map<String, Map<Integer, Map<String, List<Integer>>>> getAllMappingsByChangeRequest(int changeRequestId) throws SQLException {
        Map<String, Map<Integer, Map<String, List<Integer>>>> result = new HashMap<>();
        
        logger.info("🔍 [FacetChangesDAO] Getting all mappings for CR {}", changeRequestId);
        
        // Check all facet changes tables
        for (Map.Entry<String, String> entry : FACET_TO_TABLE.entrySet()) {
            String facetName = entry.getKey();
            String tableName = entry.getValue();
            
            String sql = "SELECT object_id, area_key, nobject_id FROM " + tableName + 
                         " WHERE change_request_id = ?";
            
            logger.debug("   └─ Checking table {} for facet {}", tableName, facetName);
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, changeRequestId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    Map<Integer, Map<String, List<Integer>>> objectMappings = new HashMap<>();
                    
                    while (rs.next()) {
                        int objectId = rs.getInt("object_id");
                        String areaKey = rs.getString("area_key");
                        int nobjectId = rs.getInt("nobject_id");
                        
                        // Exclude stakeholders mappings - stakeholders are excluded from pending changes
                        // Stakeholders changes are applied immediately even with active CR
                        if (areaKey.startsWith("stakeholders#")) {
                            logger.debug("   └─ Skipping stakeholders mapping: object_id={}, area_key='{}' (stakeholders excluded from pending changes)", 
                                objectId, areaKey);
                            continue;
                        }
                        
                        logger.info("   └─ Found mapping in {}: object_id={}, area_key='{}', nobject_id={}", 
                            tableName, objectId, areaKey, nobjectId);
                        
                        objectMappings.computeIfAbsent(objectId, k -> new HashMap<>())
                                     .computeIfAbsent(areaKey, k -> new ArrayList<>())
                                     .add(nobjectId);
                    }
                    
                    if (!objectMappings.isEmpty()) {
                        result.put(facetName, objectMappings);
                        logger.info("   └─ ✅ Found {} object(s) with mappings in {}", objectMappings.size(), tableName);
                    } else {
                        logger.debug("   └─ No mappings found in {}", tableName);
                    }
                }
            }
        }
        
        logger.info("🔍 [FacetChangesDAO] Total facets with mappings for CR {}: {}", changeRequestId, result.size());
        return result;
    }

    /**
     * Get the original object_id from a cloned nobject_id.
     * If the given ID is a cloned row (nobject_id), returns the original object_id.
     * If the given ID is already an original object_id, returns null.
     * 
     * @param facetName The facet name (glossary, system, etc.)
     * @param nobjectId The ID to check - could be either original or cloned
     * @return The original object_id if found, null otherwise
     */
    public Integer getOriginalObjectId(String facetName, int nobjectId) throws SQLException {
        String tableName = getChangesTableName(facetName);
        if (tableName == null) return null;

        // Look for this ID as a nobject_id (cloned row)
        String sql = "SELECT object_id FROM " + tableName + 
                     " WHERE nobject_id = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, nobjectId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int originalId = rs.getInt("object_id");
                    logger.debug("Found original object_id {} for cloned nobject_id {} in {}", originalId, nobjectId, tableName);
                    return originalId;
                }
            }
        }
        
        logger.debug("No original object_id found for {} in {} - it may be an original object", nobjectId, tableName);
        return null;
    }

    /**
     * Get all active nobject_id values (cloned rows) for all facets.
     * These are temporary rows created during active Change Requests and should be hidden from users.
     * Returns a map: facetName -> Set of nobject_id values
     */
    public Map<String, Set<Integer>> getAllActiveNObjectIds() throws SQLException {
        Map<String, Set<Integer>> result = new HashMap<>();
        
        // Check all facet changes tables
        for (Map.Entry<String, String> entry : FACET_TO_TABLE.entrySet()) {
            String facetName = entry.getKey();
            String tableName = entry.getValue();
            Set<Integer> nobjectIds = new java.util.HashSet<>();
            
            // Get all nobject_id values that are associated with active CRs
            // An active CR is one that is not Completed, Cancelled, or Deleted
            String sql = "SELECT DISTINCT fc.nobject_id " +
                         "FROM " + tableName + " fc " +
                         "INNER JOIN changerequest cr ON fc.change_request_id = cr.ID " +
                         "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                         "WHERE cr.Deleted_At IS NULL " +
                         "AND ( " +
                         "    cr.CR_StatusID IS NULL " +
                         "    OR crs.ID IS NULL " +
                         "    OR ( " +
                         "        LOWER(crs.PrimaryName) NOT LIKE '%complete%' " +
                         "        AND LOWER(crs.PrimaryName) NOT LIKE '%cancelled%' " +
                         "        AND LOWER(crs.PrimaryName) NOT LIKE '%canceled%' " +
                         "        AND LOWER(crs.PrimaryName) NOT LIKE '%reject%' " +
                         "        AND LOWER(crs.PrimaryName) NOT LIKE '%closed%' " +
                         "        AND LOWER(crs.PrimaryName) NOT LIKE '%deleted%' " +
                         "    ) " +
                         ")";
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    nobjectIds.add(rs.getInt("nobject_id"));
                }
            }
            
            if (!nobjectIds.isEmpty()) {
                result.put(facetName, nobjectIds);
                logger.debug("Found {} active nobject_id values for facet {}", nobjectIds.size(), facetName);
            }
        }
        
        return result;
    }
    
    /**
     * Get all active nobject_id values for a specific facet.
     * @param facetName The facet name (glossary, dataset, system, process)
     * @return Set of nobject_id values that are temporary cloned rows
     */
    public Set<Integer> getActiveNObjectIdsForFacet(String facetName) throws SQLException {
        String tableName = getChangesTableName(facetName);
        Set<Integer> nobjectIds = new java.util.HashSet<>();
        
        if (tableName == null) return nobjectIds;
        
        // Get all nobject_id values that are associated with active CRs
        String sql = "SELECT DISTINCT fc.nobject_id " +
                     "FROM " + tableName + " fc " +
                     "INNER JOIN changerequest cr ON fc.change_request_id = cr.ID " +
                     "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                     "WHERE cr.Deleted_At IS NULL " +
                     "AND ( " +
                     "    cr.CR_StatusID IS NULL " +
                     "    OR crs.ID IS NULL " +
                     "    OR ( " +
                     "        LOWER(crs.PrimaryName) NOT LIKE '%complete%' " +
                     "        AND LOWER(crs.PrimaryName) NOT LIKE '%cancelled%' " +
                     "        AND LOWER(crs.PrimaryName) NOT LIKE '%canceled%' " +
                     "        AND LOWER(crs.PrimaryName) NOT LIKE '%reject%' " +
                     "        AND LOWER(crs.PrimaryName) NOT LIKE '%closed%' " +
                     "        AND LOWER(crs.PrimaryName) NOT LIKE '%deleted%' " +
                     "    ) " +
                     ")";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                nobjectIds.add(rs.getInt("nobject_id"));
            }
        }
        
        return nobjectIds;
    }

    /**
     * Helper to get facet name from ID
     * Returns the display name (e.g., "Data Set" instead of "dataset")
     */
    private String getFacetName(int facetId) {
        // Return the display name for each facet for proper CR reference matching
        switch (facetId) {
            case 11: return "Data Set";  // Dataset uses "Data Set" as display name
            case 12: return "Glossary";
            case 13: return "System";
            case 4: return "Process";
            default: return "Unknown";
        }
    }
    
    /**
     * Get all possible facet name variations for CR lookup
     */
    private String[] getFacetNameVariations(int facetId) {
        switch (facetId) {
            case 11: return new String[]{"Data Set", "Dataset", "data set", "dataset"};
            case 12: return new String[]{"Glossary", "glossary"};
            case 13: return new String[]{"System", "system"};
            case 4: return new String[]{"Process", "process"};
            case 14: return new String[]{"People", "people", "Person", "person"};
            default: return new String[]{"Unknown"};
        }
    }
}

