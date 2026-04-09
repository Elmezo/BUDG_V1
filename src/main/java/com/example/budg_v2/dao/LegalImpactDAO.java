package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class LegalImpactDAO {
    
    // ===== GEOGRAPHY RELATIONSHIPS =====
    
    /**
     * Get geography relationships for a legal entity
     */
    public List<Map<String, Object>> getGeographyRelationshipsByLegalId(int legalId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                lxg.ID as id,
                lxg.Legal_ID as legalId,
                lxg.Geography_ID as geographyId,
                lxg.Relation_Type as relationType,
                g.PrimaryName as geographyName,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM legal_x_geography lxg
            LEFT JOIN geography g ON lxg.Geography_ID = g.ID
            LEFT JOIN legal_x_geo_relationtype rt ON lxg.Relation_Type = rt.ID
            WHERE lxg.Legal_ID = ? AND (g.DeletedDatetime IS NULL OR g.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY g.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, legalId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("legalId", rs.getInt("legalId"));
                    relationship.put("geographyId", rs.getObject("geographyId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("geographyName", rs.getString("geographyName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get all geography relation types
     */
    public List<Map<String, Object>> getGeographyRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM legal_x_geo_relationtype
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationType.put("priority", rs.getObject("Priority"));
                relationType.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Get legal entity relationships for a geography (reverse lookup)
     */
    public List<Map<String, Object>> getLegalEntityRelationshipsByGeographyId(int geographyId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                lxg.ID as id,
                lxg.Legal_ID as legalId,
                lxg.Geography_ID as geographyId,
                lxg.Relation_Type as relationType,
                l.LongName as legalEntityName,
                l.ShortName as legalEntityShortName,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription,
                rt.ReverseName as relationTypeReverseName
            FROM legal_x_geography lxg
            LEFT JOIN legal l ON lxg.Legal_ID = l.ID
            LEFT JOIN legal_x_geo_relationtype rt ON lxg.Relation_Type = rt.ID
            WHERE lxg.Geography_ID = ? AND (l.DeleteDatetime IS NULL OR l.DeleteDatetime = '1970-01-01 00:00:00')
            ORDER BY l.LongName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, geographyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    int legalId = rs.getInt("legalId");
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("legalId", legalId);
                    relationship.put("geographyId", rs.getObject("geographyId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("legalEntityName", rs.getString("legalEntityName"));
                    relationship.put("legalEntityShortName", rs.getString("legalEntityShortName"));
                    relationship.put("legalLongName", rs.getString("legalEntityName"));
                    relationship.put("legalShortName", rs.getString("legalEntityShortName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get legal entity owners
                    try {
                        String ownersName = getLegalOwnersString(legalId);
                        String ownersEmail = getLegalOwnersEmail(legalId);
                        relationship.put("legalOwnerName", ownersName);
                        relationship.put("legalOwnerEmail", ownersEmail);
                    } catch (SQLException e) {
                        System.err.println("LegalImpactDAO: Error getting legal entity owners: " + e.getMessage());
                        relationship.put("legalOwnerName", "No owner");
                        relationship.put("legalOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get legal entity owners string (concatenated names)
     */
    public String getLegalOwnersString(int legalId) throws SQLException {
        String sql = """
            SELECT CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName
            FROM legal_x_objectxpeople lxoxp
            LEFT JOIN object_x_people oxp ON lxoxp.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE lxoxp.Legal_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.First_Name, pe.Last_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, legalId);
            
            StringBuilder ownersList = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName != null && !ownerName.trim().isEmpty()) {
                        if (ownersList.length() > 0) {
                            ownersList.append(", ");
                        }
                        ownersList.append(ownerName);
                    }
                }
                
                return ownersList.length() > 0 ? ownersList.toString() : "No owner";
            }
        }
    }
    
    /**
     * Get legal entity owners email (concatenated emails)
     */
    public String getLegalOwnersEmail(int legalId) throws SQLException {
        String sql = """
            SELECT pe.Email as ownerEmail
            FROM legal_x_objectxpeople lxoxp
            LEFT JOIN object_x_people oxp ON lxoxp.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE lxoxp.Legal_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Email
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, legalId);
            
            StringBuilder ownersEmail = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String email = rs.getString("ownerEmail");
                    if (email != null && !email.trim().isEmpty()) {
                        if (ownersEmail.length() > 0) {
                            ownersEmail.append(", ");
                        }
                        ownersEmail.append(email);
                    }
                }
                
                return ownersEmail.length() > 0 ? ownersEmail.toString() : null;
            }
        }
    }
    
    /**
     * Save geography relationships for a legal entity
     */
    public boolean saveGeographyRelationships(int legalId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingGeographyRelationshipsMap(conn, legalId);
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object geographyIdObj = rel.get("geographyId");
                    Object relationTypeObj = rel.get("relationType");
                    if (geographyIdObj != null && relationTypeObj != null) {
                        int geographyId = ((Number) geographyIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (geographyId > 0 && relationType > 0) {
                            newRelationshipsMap.put(geographyId, relationType);
                        }
                    }
                }
            }
            
            // Detect changes: DELETE, UPDATE, INSERT
            // DELETE: entities in existing but not in new
            for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int oldRelationType = entry.getValue();
                Integer newRelationType = newRelationshipsMap.get(entityId);
                
                if (newRelationType == null) {
                    // Entity was deleted
                    logAuditHistoryForDelete(conn, legalId, "geography", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, legalId, "geography", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, legalId, "geography", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM legal_x_geography WHERE Legal_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, legalId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (prevent duplicates)
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO legal_x_geography
                (Legal_ID, Geography_ID, Relation_Type, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID)
                VALUES (?, ?, ?, NOW(), NOW(), ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                        Object geographyIdObj = rel.get("geographyId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        // Skip if missing required fields
                        if (geographyIdObj == null || relationTypeObj == null) {
                            continue;
                        }
                        
                        int geographyId = ((Number) geographyIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        
                        // Check for duplicates
                        if (geographyId > 0 && relationType > 0) {
                            String key = geographyId + "_" + relationType;
                            if (seen.contains(key)) {
                                //system.out.println("LegalImpactDAO: Skipping duplicate geography relationship - geographyId=" + geographyId + ", relationType=" + relationType);
                                continue;
                            }
                            seen.add(key);
                            
                            ps.setInt(1, legalId);
                            ps.setInt(2, geographyId);
                            ps.setInt(3, relationType);
                            ps.setInt(4, userId);
                            ps.addBatch();
                        }
                    }
                }
                ps.executeBatch();
            }
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get existing geography relationships as a Map (geographyId -> relationType)
     */
    private Map<Integer, Integer> getExistingGeographyRelationshipsMap(Connection conn, int legalId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT Geography_ID, Relation_Type FROM legal_x_geography WHERE Legal_ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int geographyId = rs.getInt("Geography_ID");
                    int relationType = rs.getInt("Relation_Type");
                    if (geographyId > 0 && relationType > 0) {
                        existingRelationships.put(geographyId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get geography name from geography ID
     */
    private String getGeographyName(int geographyId) {
        String sql = "SELECT PrimaryName FROM geography WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, geographyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting geography name for geographyId " + geographyId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get geography relation type name from relation type ID
     */
    private String getGeographyRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM legal_x_geo_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting geography relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get user full name from user ID
     */
    private String getUserFullName(int userId) {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fullName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user full name: " + e.getMessage());
        }
        return "Unknown User";
    }
    
    /**
     * Log audit history for insert operations in relationship tables
     * 
     * @param conn The database connection (to use same transaction)
     * @param legalId The legal entity ID
     * @param tableType The type of relationship table ("geography")
     * @param entityId The ID of the related entity
     * @param relationTypeId The ID of the relation type
     * @param userId The ID of the user performing the action
     */
    private void logAuditHistoryForInsert(Connection conn, int legalId, String tableType, 
                                         int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = null;
            if ("geography".equalsIgnoreCase(tableType)) {
                entityName = getGeographyName(entityId);
            }
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get relation type name
            String relationTypeName = null;
            if ("geography".equalsIgnoreCase(tableType)) {
                relationTypeName = getGeographyRelationTypeName(relationTypeId);
            }
            if (relationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name based on table type
            String objectName = null;
            String entityFieldName = null;
            if ("geography".equalsIgnoreCase(tableType)) {
                objectName = "Geography X Legal";
                entityFieldName = "Geography";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO legal_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, legalId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Added");
                ps.setString(5, entityFieldName);
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, entityName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type
                ps.setInt(1, legalId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Status Change");
                ps.setString(5, "Relationship Type");
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, relationTypeName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
            }
            
        } catch (SQLException e) {
            // Don't throw exception - just log error to avoid breaking the main transaction
            System.err.println("Error logging audit history: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Log audit history for delete operations in relationship tables
     */
    private void logAuditHistoryForDelete(Connection conn, int legalId, String tableType, 
                                         int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = null;
            if ("geography".equalsIgnoreCase(tableType)) {
                entityName = getGeographyName(entityId);
            }
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get relation type name
            String relationTypeName = null;
            if ("geography".equalsIgnoreCase(tableType)) {
                relationTypeName = getGeographyRelationTypeName(relationTypeId);
            }
            if (relationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name based on table type
            String objectName = null;
            String entityFieldName = null;
            if ("geography".equalsIgnoreCase(tableType)) {
                objectName = "Geography X Legal";
                entityFieldName = "Geography";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO legal_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, legalId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Deleted");
                ps.setString(5, entityFieldName);
                ps.setString(6, entityName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type deleted
                ps.setInt(1, legalId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Deleted");
                ps.setString(5, "Relationship Type");
                ps.setString(6, relationTypeName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, userFullName);
                ps.executeUpdate();
            }
            
        } catch (SQLException e) {
            // Don't throw exception - just log error to avoid breaking the main transaction
            System.err.println("Error logging audit history for delete: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Log audit history for update operations (when relation type changes for same entity)
     */
    private void logAuditHistoryForUpdate(Connection conn, int legalId, String tableType, 
                                        int entityId, int oldRelationTypeId, int newRelationTypeId, int userId) {
        try {
            // Get old and new relation type names
            String oldRelationTypeName = null;
            String newRelationTypeName = null;
            if ("geography".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getGeographyRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getGeographyRelationTypeName(newRelationTypeId);
            }
            if (oldRelationTypeName == null || newRelationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name based on table type
            String objectName = null;
            if ("geography".equalsIgnoreCase(tableType)) {
                objectName = "Geography X Legal";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO legal_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, legalId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Status Change");
                ps.setString(5, "Relationship Type");
                ps.setString(6, oldRelationTypeName);
                ps.setString(7, newRelationTypeName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
            }
            
        } catch (SQLException e) {
            // Don't throw exception - just log error to avoid breaking the main transaction
            System.err.println("Error logging audit history for update: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

