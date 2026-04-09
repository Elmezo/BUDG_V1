package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for cr_changes_review table
 * Stores snapshots of changes for review purposes, even after CR completion
 */
public class CRChangesReviewDAO {
    private static final Logger logger = LoggerFactory.getLogger(CRChangesReviewDAO.class);
    
    // Facet name to ID mapping
    private static final Map<String, Integer> FACET_NAME_TO_ID = new HashMap<>();
    static {
        FACET_NAME_TO_ID.put("glossary", 12);
        FACET_NAME_TO_ID.put("dataset", 11);
        FACET_NAME_TO_ID.put("data set", 11);
        FACET_NAME_TO_ID.put("system", 13);
        FACET_NAME_TO_ID.put("process", 4);
    }
    
    // Facet ID to name mapping (for reverse lookup)
    private static final Map<Integer, String> FACET_ID_TO_NAME = new HashMap<>();
    static {
        FACET_ID_TO_NAME.put(12, "Glossary");
        FACET_ID_TO_NAME.put(11, "Data Set");
        FACET_ID_TO_NAME.put(13, "System");
        FACET_ID_TO_NAME.put(4, "Process");
    }
    
    /**
     * Get facet ID from name
     */
    private Integer getFacetId(String facetName) {
        if (facetName == null) return null;
        return FACET_NAME_TO_ID.get(facetName.toLowerCase().trim());
    }
    
    /**
     * Save a change review entry
     * If a change for the same field already exists, update the new_value only (keep original old_value)
     * 
     * IMPORTANT: For tables that support multiple rows (documents, data-content), 
     * we need to include a unique identifier (nobjectId or relatedName) to distinguish between different rows.
     * Otherwise, multiple new rows will be treated as updates to the same row.
     */
    public void saveChangeReview(int changeRequestId, String facetName, int objectId, Integer nobjectId,
                                String areaKey, String tabName, String componentName, String objectName,
                                String operation, String fieldName, String oldValue, String newValue,
                                String relatedType, String relatedName, String relationTypeName, int authorId, String authorName,
                                String snapshotData) throws SQLException {
        Integer facetId = getFacetId(facetName);
        if (facetId == null) {
            logger.warn("Unknown facet name: {}, skipping save", facetName);
            return;
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if this is a multi-row table (documents, data-content, impact, relationships, attributes)
            // These tables can have multiple rows, so we need to distinguish them
            boolean isMultiRowTable = (areaKey != null && 
                (areaKey.startsWith("documents#") || areaKey.equals("data-content") || 
                 areaKey.startsWith("impact#") || areaKey.startsWith("relationships#") ||
                 areaKey.equals("summary#attribute")));
            
            // Check if a change for this field already exists
            // IMPORTANT: For multi-row tables, we must check by areaKey + nobjectId/relatedName + fieldName
            // to distinguish between different rows, even if fieldName is present.
            // For summary fields (non-multi-row with fieldName), match by change_request_id, facet_id, object_id, and field_name
            // For other areas (relationships, impact), match by change_request_id, facet_id, object_id, and area_key
            String checkSql;
            if (isMultiRowTable) {
                // Multi-row table - MUST distinguish rows by nobjectId or relatedName
                // For new inserts (operation = "Inserted" or "Created" or empty/null), we should always create a new entry
                // For updates, match by area_key + nobjectId/relatedName + fieldName to find the specific row
                // IMPORTANT: For multi-row tables, each fieldName should be a separate record, so we always insert
                // unless operation is explicitly "Updated" or "Deleted"
                if ("Inserted".equals(operation) || "Created".equals(operation) || 
                    operation == null || operation.isEmpty() || 
                    (!"Updated".equals(operation) && !"Deleted".equals(operation))) {
                    // For new rows or unknown operations, always insert - don't check for existing
                    // This ensures multiple new rows are all saved separately, even if they have the same nobjectId
                    // Each fieldName gets its own record
                    checkSql = null; // Will skip the check and insert directly
                } else {
                    // For updates/deletes, match by area_key + nobjectId/relatedName + fieldName to find the specific row
                    // This ensures we update the correct row even if multiple rows have the same fieldName
                    if (nobjectId != null) {
                        if (fieldName != null && !fieldName.isEmpty()) {
                            // Match by area_key + nobjectId + fieldName
                            checkSql = "SELECT id, old_value FROM cr_changes_review " +
                                      "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                                      "AND nobject_id = ? AND field_name = ? " +
                                      "LIMIT 1";
                        } else {
                            // Match by area_key + nobjectId (no fieldName)
                            checkSql = "SELECT id, old_value FROM cr_changes_review " +
                                      "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                                      "AND nobject_id = ? " +
                                      "LIMIT 1";
                        }
                    } else if (relatedName != null && !relatedName.isEmpty()) {
                        if (fieldName != null && !fieldName.isEmpty()) {
                            // Match by area_key + relatedName + fieldName
                            checkSql = "SELECT id, old_value FROM cr_changes_review " +
                                      "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                                      "AND related_name = ? AND field_name = ? " +
                                      "LIMIT 1";
                        } else {
                            // Match by area_key + relatedName (no fieldName)
                            checkSql = "SELECT id, old_value FROM cr_changes_review " +
                                      "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                                      "AND related_name = ? " +
                                      "LIMIT 1";
                        }
                    } else {
                        // Fallback: match by area_key + fieldName (if available) or area_key only
                        if (fieldName != null && !fieldName.isEmpty()) {
                            checkSql = "SELECT id, old_value FROM cr_changes_review " +
                                      "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                                      "AND field_name = ? " +
                                      "LIMIT 1";
                        } else {
                            checkSql = "SELECT id, old_value FROM cr_changes_review " +
                                      "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                                      "LIMIT 1";
                        }
                    }
                }
            } else if (fieldName != null && !fieldName.isEmpty()) {
                // Summary field (non-multi-row) - match by field_name
                checkSql = "SELECT id, old_value FROM cr_changes_review " +
                          "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND field_name = ? " +
                          "LIMIT 1";
            } else {
                // Relationship/Impact field - match by area_key + relatedName + fieldName (if available)
                // For impact, we need to distinguish between different related objects even with same area_key
                if (relatedName != null && !relatedName.isEmpty() && fieldName != null && !fieldName.isEmpty()) {
                    // Match by area_key + relatedName + fieldName to distinguish different impact entries
                    checkSql = "SELECT id, old_value FROM cr_changes_review " +
                              "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                              "AND related_name = ? AND field_name = ? " +
                              "LIMIT 1";
                } else if (relatedName != null && !relatedName.isEmpty()) {
                    // Match by area_key + relatedName
                    checkSql = "SELECT id, old_value FROM cr_changes_review " +
                              "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                              "AND related_name = ? " +
                              "LIMIT 1";
                } else {
                    // Fallback: match by area_key only
                    checkSql = "SELECT id, old_value FROM cr_changes_review " +
                              "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? AND area_key = ? " +
                              "LIMIT 1";
                }
            }
            
            Integer existingId = null;
            String existingOldValue = null;
            
            // Only check for existing entry if checkSql is not null (skip for new inserts in multi-row tables)
            if (checkSql != null) {
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, changeRequestId);
                    checkStmt.setInt(2, facetId);
                    checkStmt.setInt(3, objectId);
                    
                    // Set remaining parameters based on the query structure
                    if (isMultiRowTable && !"Inserted".equals(operation) && !"Created".equals(operation)) {
                        // Multi-row update: area_key is always parameter 4
                        checkStmt.setString(4, areaKey);
                        
                        if (nobjectId != null) {
                            // area_key + nobjectId + (possibly fieldName)
                            checkStmt.setInt(5, nobjectId);
                            if (fieldName != null && !fieldName.isEmpty() && checkSql.contains("AND field_name = ?")) {
                                checkStmt.setString(6, fieldName);
                            }
                        } else if (relatedName != null && !relatedName.isEmpty()) {
                            // area_key + relatedName + (possibly fieldName)
                            checkStmt.setString(5, relatedName);
                            if (fieldName != null && !fieldName.isEmpty() && checkSql.contains("AND field_name = ?")) {
                                checkStmt.setString(6, fieldName);
                            }
                        } else {
                            // area_key + (possibly fieldName)
                            if (fieldName != null && !fieldName.isEmpty() && checkSql.contains("AND field_name = ?")) {
                                checkStmt.setString(5, fieldName);
                            }
                        }
                    } else if (fieldName != null && !fieldName.isEmpty()) {
                        // Summary field (non-multi-row): fieldName is parameter 4
                        checkStmt.setString(4, fieldName);
                    } else {
                        // Relationship/Impact field: areaKey is parameter 4
                        checkStmt.setString(4, areaKey);
                        // If relatedName and fieldName are used for matching, set them
                        if (relatedName != null && !relatedName.isEmpty() && fieldName != null && !fieldName.isEmpty()) {
                            checkStmt.setString(5, relatedName);
                            checkStmt.setString(6, fieldName);
                        } else if (relatedName != null && !relatedName.isEmpty()) {
                            checkStmt.setString(5, relatedName);
                        }
                    }
                    
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            existingId = rs.getInt("id");
                            existingOldValue = rs.getString("old_value");
                            logger.debug("Found existing change review entry ID {} for CR {} field {} (nobjectId: {}, relatedName: {})", 
                                       existingId, changeRequestId, fieldName != null ? fieldName : areaKey, nobjectId, relatedName);
                        }
                    }
                }
            }
            
            if (existingId != null) {
                // Check if old_value needs to be converted (if it's a number and field is KDE or Security Classification)
                String convertedOldValue = convertValueIfNeeded(conn, facetName, fieldName, existingOldValue);
                
                // Also convert newValue if it's a number
                String convertedNewValue = convertValueIfNeeded(conn, facetName, fieldName, newValue);
                
                // Update existing entry - update new_value and old_value (if converted)
                String updateSql = "UPDATE cr_changes_review SET " +
                                 "old_value = ?, new_value = ?, nobject_id = ?, author_id = ?, author_name = ?, updated_at = CURRENT_TIMESTAMP " +
                                 "WHERE id = ?";
                
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, convertedOldValue);
                    updateStmt.setString(2, convertedNewValue);
                    if (nobjectId != null) {
                        updateStmt.setInt(3, nobjectId);
                    } else {
                        updateStmt.setNull(3, Types.INTEGER);
                    }
                    updateStmt.setInt(4, authorId);
                    updateStmt.setString(5, authorName);
                    updateStmt.setInt(6, existingId);
                    
                    updateStmt.executeUpdate();
                    logger.debug("Updated change review entry ID {} for CR {}: {} {} (operation: {}, nobjectId: {}, fieldName: {})", 
                               existingId, changeRequestId, facetName, objectId, operation, nobjectId, fieldName);
                }
            } else {
                // Insert new entry - convert oldValue and newValue if they're numbers for KDE or Security Classification
                String convertedOldValue = convertValueIfNeeded(conn, facetName, fieldName, oldValue);
                String convertedNewValue = convertValueIfNeeded(conn, facetName, fieldName, newValue);
                
                String insertSql = "INSERT INTO cr_changes_review (" +
                                "change_request_id, facet_id, object_id, nobject_id, area_key, " +
                                "tab_name, component_name, object_name, operation, field_name, " +
                                "old_value, new_value, related_type, related_name, relation_type_name, author_id, author_name, snapshot_data" +
                                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, changeRequestId);
                    insertStmt.setInt(2, facetId);
                    insertStmt.setInt(3, objectId);
                    if (nobjectId != null) {
                        insertStmt.setInt(4, nobjectId);
                    } else {
                        insertStmt.setNull(4, Types.INTEGER);
                    }
                    insertStmt.setString(5, areaKey);
                    insertStmt.setString(6, tabName);
                    insertStmt.setString(7, componentName);
                    insertStmt.setString(8, objectName);
                    insertStmt.setString(9, operation);
                    insertStmt.setString(10, fieldName);
                    insertStmt.setString(11, convertedOldValue);
                    insertStmt.setString(12, convertedNewValue);
                    insertStmt.setString(13, relatedType);
                    insertStmt.setString(14, relatedName);
                    insertStmt.setString(15, relationTypeName);
                    insertStmt.setInt(16, authorId);
                    insertStmt.setString(17, authorName);
                    if (snapshotData != null) {
                        insertStmt.setString(18, snapshotData);
                    } else {
                        insertStmt.setNull(18, Types.VARCHAR);
                    }
                    
                    insertStmt.executeUpdate();
                    logger.debug("Inserted new change review entry for CR {}: {} {} {} (operation: {}, nobjectId: {}, fieldName: {})", 
                               changeRequestId, facetName, objectId, areaKey, operation, nobjectId, fieldName);
                }
            }
        }
    }
    
    /**
     * Get all change review entries for a CR
     */
    public List<Map<String, Object>> getChangesForCR(int changeRequestId) throws SQLException {
        List<Map<String, Object>> changes = new ArrayList<>();
        
        String sql = "SELECT * FROM cr_changes_review WHERE change_request_id = ? ORDER BY created_at ASC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> change = new HashMap<>();
                    int facetId = rs.getInt("facet_id");
                    String facetName = getFacetNameFromId(facetId).toLowerCase();
                    String fieldName = rs.getString("field_name");
                    String oldValue = rs.getString("old_value");
                    String newValue = rs.getString("new_value");
                    
                    // Convert oldValue and newValue if they're numbers for KDE or Security Classification
                    String convertedOldValue = convertValueIfNeeded(conn, facetName, fieldName, oldValue);
                    String convertedNewValue = convertValueIfNeeded(conn, facetName, fieldName, newValue);
                    
                    change.put("facetType", getFacetNameFromId(facetId));
                    change.put("objectId", rs.getInt("object_id"));
                    change.put("objectName", rs.getString("object_name"));
                    change.put("tabName", rs.getString("tab_name"));
                    change.put("operation", rs.getString("operation"));
                    change.put("fieldName", fieldName);
                    change.put("oldValue", convertedOldValue);
                    change.put("newValue", convertedNewValue);
                    change.put("userName", rs.getString("author_name"));
                    change.put("componentName", rs.getString("component_name"));
                    change.put("relatedType", rs.getString("related_type"));
                    change.put("relatedName", rs.getString("related_name"));
                    change.put("relationTypeName", rs.getString("relation_type_name"));
                    changes.add(change);
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Delete all change review entries for a CR (when CR is cancelled/deleted)
     */
    public void deleteChangesForCR(int changeRequestId) throws SQLException {
        String sql = "DELETE FROM cr_changes_review WHERE change_request_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            int deleted = stmt.executeUpdate();
            logger.info("Deleted {} change review entries for CR {}", deleted, changeRequestId);
        }
    }
    
    /**
     * Check if changes exist for a CR in the review table
     */
    public boolean hasChangesForCR(int changeRequestId) throws SQLException {
        String sql = "SELECT COUNT(*) as cnt FROM cr_changes_review WHERE change_request_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt") > 0;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get facet name from ID
     */
    private String getFacetNameFromId(int facetId) {
        return FACET_ID_TO_NAME.getOrDefault(facetId, "Unknown");
    }
    
    /**
     * Convert a value if it's a number for KDE or Security Classification fields
     * Returns the converted display value, or the original value if no conversion is needed
     */
    private String convertValueIfNeeded(Connection conn, String facetName, String fieldName, String value) {
        if (value == null || value.isEmpty() || fieldName == null) {
            return value;
        }
        
        String fieldNameLower = fieldName.toLowerCase().trim();
        boolean isKdeField = fieldNameLower.equals("kde");
        boolean isSecurityClassificationField = (fieldNameLower.contains("security") && fieldNameLower.contains("classification")) ||
                                                 fieldNameLower.equals("security_classification") ||
                                                 fieldNameLower.equals("securityclassification");
        
        // Only convert for Glossary facet and KDE/Security Classification fields
        if (!"glossary".equals(facetName != null ? facetName.toLowerCase() : "")) {
            return value;
        }
        
        if ((isKdeField || isSecurityClassificationField) && value.matches("\\d+")) {
            // Value is a number, convert it to display value
            try {
                int id = Integer.parseInt(value);
                String tableName = isKdeField ? "glossary_kde_type" : "security_classification";
                String converted = lookupValue(conn, tableName, id, "Name", value);
                logger.debug("Converted value from {} to {} for field {} in facet {}", value, converted, fieldName, facetName);
                return converted;
            } catch (NumberFormatException e) {
                // Not a number, keep as-is
                return value;
            }
        }
        
        return value;
    }
    
    /**
     * Lookup a value from a reference table
     * Handles both ID (uppercase) and id (lowercase) column names
     */
    private String lookupValue(Connection conn, String tableName, int id, String nameColumn, String defaultValue) {
        try {
            // Try ID (uppercase) first, then id (lowercase) for tables like cia_rating
            String sql = "SELECT " + nameColumn + " FROM " + tableName + " WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // If ID (uppercase) fails, try id (lowercase) for tables like cia_rating
            try {
                String sql = "SELECT " + nameColumn + " FROM " + tableName + " WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String name = rs.getString(1);
                            if (name != null && !name.isEmpty()) {
                                return name;
                            }
                        }
                    }
                }
            } catch (SQLException e2) {
                logger.debug("Could not lookup {} ID {}: {}", tableName, id, e2.getMessage());
            }
        }
        return defaultValue;
    }
}
