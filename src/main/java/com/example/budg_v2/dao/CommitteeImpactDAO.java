package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class CommitteeImpactDAO {
    
    // ===== CAPABILITY RELATIONSHIPS =====
    
    /**
     * Get capability relationships for a committee with owner information
     */
    public List<Map<String, Object>> getCapabilityRelationshipsByCommitteeId(int committeeId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxc.ID as id,
                cxc.Committee_ID as committeeId,
                cxc.Capability_ID as capabilityId,
                cxc.RelationType as relationType,
                c.PrimaryName as capabilityName,
                c.RefNumber as capabilityRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM committee_x_capability cxc
            LEFT JOIN capability c ON cxc.Capability_ID = c.ID
            LEFT JOIN committee_x_capability_relationtype rt ON cxc.RelationType = rt.ID
            WHERE cxc.Committee_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY c.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, committeeId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer capabilityId = rs.getObject("capabilityId") != null ? rs.getInt("capabilityId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("committeeId", rs.getInt("committeeId"));
                    relationship.put("capabilityId", capabilityId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("capabilityRefNumber", rs.getString("capabilityRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get capability owners
                    if (capabilityId != null) {
                        try {
                            String ownersName = getCapabilityOwnersString(capabilityId);
                            String ownersEmail = getCapabilityOwnersEmail(capabilityId);
                            relationship.put("capabilityOwnerName", ownersName);
                            relationship.put("capabilityOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("CommitteeImpactDAO: Error getting capability owners: " + e.getMessage());
                            relationship.put("capabilityOwnerName", "No owner");
                            relationship.put("capabilityOwnerEmail", null);
                        }
                    } else {
                        relationship.put("capabilityOwnerName", null);
                        relationship.put("capabilityOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get all capability relation types
     */
    public List<Map<String, Object>> getCapabilityRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM committee_x_capability_relationtype
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
     * Get committee relationships for a capability (reverse lookup)
     */
    public List<Map<String, Object>> getCommitteeRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxc.ID as id,
                cxc.Committee_ID as committeeId,
                cxc.Capability_ID as capabilityId,
                cxc.RelationType as relationType,
                com.PrimaryName as committeeName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM committee_x_capability cxc
            LEFT JOIN committee com ON cxc.Committee_ID = com.ID
            LEFT JOIN committee_x_capability_relationtype rt ON cxc.RelationType = rt.ID
            WHERE cxc.Capability_ID = ? AND (com.DeleteDatetime IS NULL OR com.DeleteDatetime = '1970-01-01 00:00:00')
            ORDER BY com.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer committeeId = rs.getObject("committeeId") != null ? rs.getInt("committeeId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("committeeId", committeeId);
                    relationship.put("capabilityId", rs.getInt("capabilityId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("committeeName", rs.getString("committeeName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get committee owners
                    if (committeeId != null) {
                        try {
                            String ownersName = getCommitteeOwnersString(committeeId);
                            relationship.put("committeeOwnerName", ownersName != null ? ownersName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("CommitteeImpactDAO: Error getting committee owners: " + e.getMessage());
                            relationship.put("committeeOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("committeeOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get committee owners string (concatenated names)
     */
    public String getCommitteeOwnersString(int committeeId) throws SQLException {
        String sql = """
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM committee_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_X_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.Committee_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, committeeId);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<String> owners = new ArrayList<>();
                while (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName != null && !ownerName.trim().isEmpty()) {
                        owners.add(ownerName.trim());
                    }
                }
                
                if (owners.isEmpty()) {
                    return null;
                }
                return String.join(", ", owners);
            }
        }
    }
    
    /**
     * Get capability owners string (concatenated names)
     */
    public String getCapabilityOwnersString(int capabilityId) throws SQLException {
        String sql = """
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM capability_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.CapabilityID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<String> owners = new ArrayList<>();
                while (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName != null && !ownerName.trim().isEmpty()) {
                        owners.add(ownerName.trim());
                    }
                }
                
                if (owners.isEmpty()) {
                    return null;
                }
                return String.join(", ", owners);
            }
        }
    }
    
    /**
     * Get capability owners email (concatenated)
     */
    public String getCapabilityOwnersEmail(int capabilityId) throws SQLException {
        String sql = """
            SELECT pe.Email as ownerEmail
            FROM capability_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.CapabilityID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<String> emails = new ArrayList<>();
                while (rs.next()) {
                    String email = rs.getString("ownerEmail");
                    if (email != null && !email.trim().isEmpty()) {
                        emails.add(email.trim());
                    }
                }
                
                if (emails.isEmpty()) {
                    return null;
                }
                return String.join(", ", emails);
            }
        }
    }
    
    /**
     * Save capability relationships for a committee
     */
    public boolean saveCapabilityRelationships(int committeeId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, committeeId, "capability");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object capabilityIdObj = rel.get("capabilityId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    if (capabilityIdObj != null && relationTypeObj != null) {
                        int capabilityId = ((Number) capabilityIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (capabilityId > 0 && relationType > 0) {
                            newRelationshipsMap.put(capabilityId, relationType);
                        }
                    }
                }
            }
            
            // Detect changes: DELETE, UPDATE, INSERT
            // DELETE: capabilities in existing but not in new
            for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int oldRelationType = entry.getValue();
                Integer newRelationType = newRelationshipsMap.get(entityId);
                
                if (newRelationType == null) {
                    // Entity was deleted
                    logAuditHistoryForDelete(conn, committeeId, "capability", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, committeeId, "capability", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: capabilities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, committeeId, "capability", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM committee_x_capability WHERE Committee_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, committeeId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (prevent duplicates)
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO committee_x_capability
                (Committee_ID, Capability_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID)
                VALUES (?, ?, ?, NULL, NOW(), NOW(), ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                List<Map<String, Object>> list = (relationships != null) ? relationships : java.util.Collections.emptyList();
                for (Map<String, Object> rel : list) {
                    Object capabilityIdObj = rel.get("capabilityId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    // Skip if missing required fields
                    if (capabilityIdObj == null || relationTypeObj == null) {
                        continue;
                    }
                    
                    int capabilityId = ((Number) capabilityIdObj).intValue();
                    int relationType = ((Number) relationTypeObj).intValue();
                    
                    // Check for duplicates
                    String key = capabilityId + "_" + relationType;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);
                    
                    ps.setInt(1, committeeId);
                    ps.setInt(2, capabilityId);
                    ps.setInt(3, relationType);
                    ps.setInt(4, userId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get existing relationships map before deletion (for audit tracking)
     */
    private Map<Integer, Integer> getExistingRelationshipsMap(Connection conn, int committeeId, String tableType) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT Capability_ID, RelationType FROM committee_x_capability WHERE Committee_ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, committeeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int capabilityId = rs.getInt("Capability_ID");
                    int relationType = rs.getInt("RelationType");
                    if (capabilityId > 0 && relationType > 0) {
                        existingRelationships.put(capabilityId, relationType);
                    }
                }
            }
        }
        return existingRelationships;
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
     * Get entity name based on table type and entity ID
     */
    private String getEntityName(String tableType, int entityId) {
        String sql = "SELECT PrimaryName FROM capability WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting entity name for capability: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get relation type name based on table type and relation type ID
     */
    private String getRelationTypeName(String tableType, int relationTypeId) {
        String sql = "SELECT PrimaryName FROM committee_x_capability_relationtype WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting relation type name: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Capitalize first letter of a string
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Log audit history for INSERT operation
     */
    private void logAuditHistoryForInsert(Connection conn, int committeeId, String tableType,
                                          int entityId, int relationTypeId, int userId) {
        try {
            String objectName = "Committee X Capability";
            String entityFieldName = "Capability";
            String entityName = getEntityName(tableType, entityId);
            String relationTypeName = getRelationTypeName(tableType, relationTypeId);
            String userName = getUserFullName(userId);

            String auditSql = "INSERT INTO committee_audit_history " +
                    "(id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                    "VALUES (?, ?, ?, ?, ?, NULL, ?, ?, NOW(), NOW())";

            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                // Log entity insertion
                auditStmt.setInt(1, committeeId);
                auditStmt.setString(2, objectName);
                auditStmt.setString(3, "link");
                auditStmt.setString(4, "Added");
                auditStmt.setString(5, entityFieldName);
                auditStmt.setString(6, entityName);
                auditStmt.setString(7, userName);
                auditStmt.executeUpdate();

                // Log relation type insertion
                auditStmt.setString(5, "RelationType");
                auditStmt.setString(6, relationTypeName);
                auditStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error logging audit history for INSERT: " + e.getMessage());
        }
    }
    
    /**
     * Log audit history for DELETE operation
     */
    private void logAuditHistoryForDelete(Connection conn, int committeeId, String tableType,
                                          int entityId, int relationTypeId, int userId) {
        try {
            String objectName = "Committee X Capability";
            String entityFieldName = "Capability";
            String entityName = getEntityName(tableType, entityId);
            String relationTypeName = getRelationTypeName(tableType, relationTypeId);
            String userName = getUserFullName(userId);

            String auditSql = "INSERT INTO committee_audit_history " +
                    "(id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                    "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, NOW(), NOW())";

            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                // Log entity deletion
                auditStmt.setInt(1, committeeId);
                auditStmt.setString(2, objectName);
                auditStmt.setString(3, "delete");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, entityFieldName);
                auditStmt.setString(6, entityName);
                auditStmt.setString(7, userName);
                auditStmt.executeUpdate();

                // Log relation type deletion
                auditStmt.setString(5, "RelationType");
                auditStmt.setString(6, relationTypeName);
                auditStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error logging audit history for DELETE: " + e.getMessage());
        }
    }
    
    /**
     * Log audit history for UPDATE operation (RelationType change)
     */
    private void logAuditHistoryForUpdate(Connection conn, int committeeId, String tableType,
                                          int entityId, int oldRelationTypeId, int newRelationTypeId, int userId) {
        try {
            String objectName = "Committee X Capability";
            String oldRelationTypeName = getRelationTypeName(tableType, oldRelationTypeId);
            String newRelationTypeName = getRelationTypeName(tableType, newRelationTypeId);
            String userName = getUserFullName(userId);

            String auditSql = "INSERT INTO committee_audit_history " +
                    "(id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                    "VALUES (?, ?, ?, ?, 'RelationType', ?, ?, ?, NOW(), NOW())";

            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                auditStmt.setInt(1, committeeId);
                auditStmt.setString(2, objectName);
                auditStmt.setString(3, "edit");
                auditStmt.setString(4, "Updated");
                auditStmt.setString(5, oldRelationTypeName);
                auditStmt.setString(6, newRelationTypeName);
                auditStmt.setString(7, userName);
                auditStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error logging audit history for UPDATE: " + e.getMessage());
        }
    }
}

