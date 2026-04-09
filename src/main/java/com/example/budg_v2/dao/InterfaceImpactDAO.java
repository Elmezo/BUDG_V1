package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class InterfaceImpactDAO {
    
    // ===== PROCESS RELATIONSHIPS =====
    
    /**
     * Get process relationships for an interface with owner information
     */
    public List<Map<String, Object>> getProcessRelationshipsByInterfaceId(int interfaceId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                pxi.id,
                pxi.interface_id as interfaceId,
                pxi.process_id as processId,
                pxi.relationtype as relationType,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                rt.primaryname as relationTypeName,
                rt.description as relationTypeDescription
            FROM process_x_interface pxi
            LEFT JOIN process p ON pxi.process_id = p.id
            LEFT JOIN process_x_interface_relationtype rt ON pxi.relationtype = rt.id
            WHERE pxi.interface_id = ?
            ORDER BY p.primaryname
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, interfaceId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer processId = rs.getObject("processId") != null ? rs.getInt("processId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("interfaceId", rs.getInt("interfaceId"));
                    relationship.put("processId", processId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get process owners
                    if (processId != null) {
                        try {
                            String ownersName = getProcessOwnersString(processId);
                            String ownersEmail = getProcessOwnersEmail(processId);
                            relationship.put("processOwnerName", ownersName);
                            relationship.put("processOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("InterfaceImpactDAO: Error getting process owners: " + e.getMessage());
                            relationship.put("processOwnerName", "No owner");
                            relationship.put("processOwnerEmail", null);
                        }
                    } else {
                        relationship.put("processOwnerName", null);
                        relationship.put("processOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get all process relation types
     */
    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM process_x_interface_relationtype
            WHERE deleteddatetime = lastupdatedatetime
            ORDER BY priority, primaryname
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryname", rs.getString("primaryname"));
                relationType.put("description", rs.getString("description"));
                relationType.put("priority", rs.getObject("priority"));
                relationType.put("reversename", rs.getString("reversename"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Get process owners string (concatenated names)
     */
    public String getProcessOwnersString(int processId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM process_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.process_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, processId);
            
            StringBuilder ownersList = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String firstName = rs.getString("First_Name");
                    String lastName = rs.getString("Last_Name");
                    
                    if (firstName != null && lastName != null) {
                        if (ownersList.length() > 0) {
                            ownersList.append(", ");
                        }
                        ownersList.append(firstName).append(" ").append(lastName);
                    }
                }
                
                return ownersList.length() > 0 ? ownersList.toString() : "No owner";
            }
        }
    }
    
    /**
     * Get process owners email (concatenated)
     */
    public String getProcessOwnersEmail(int processId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM process_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.process_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, processId);
            
            StringBuilder emailsList = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String email = rs.getString("Email");
                    if (email != null && !email.isEmpty()) {
                        if (emailsList.length() > 0) {
                            emailsList.append(", ");
                        }
                        emailsList.append(email);
                    }
                }
                
                return emailsList.length() > 0 ? emailsList.toString() : null;
            }
        }
    }
    
    /**
     * Save process relationships for an interface
     */
    public boolean saveProcessRelationships(int interfaceId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingProcessRelationshipsMap(conn, interfaceId);
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object processIdObj = rel.get("processId");
                    Object relationTypeObj = rel.get("relationType");
                    if (processIdObj != null && relationTypeObj != null) {
                        int processId = ((Number) processIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (processId > 0 && relationType > 0) {
                            newRelationshipsMap.put(processId, relationType);
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
                    logAuditHistoryForDelete(conn, interfaceId, "process", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, interfaceId, "process", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, interfaceId, "process", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM process_x_interface WHERE interface_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, interfaceId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (prevent duplicates)
            // Note: id is AUTO_INCREMENT, so we don't need to generate it
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO process_x_interface
                (interface_id, process_id, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid)
                VALUES (?, ?, ?, NOW(), NOW(), ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                        Object processIdObj = rel.get("processId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        // Skip if missing required fields
                        if (processIdObj == null || relationTypeObj == null) {
                            continue;
                        }
                        
                        int processId = ((Number) processIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        
                        // Check for duplicates
                        if (processId > 0 && relationType > 0) {
                            String key = processId + "_" + relationType;
                            if (seen.contains(key)) {
                                //system.out.println("InterfaceImpactDAO: Skipping duplicate process relationship - processId=" + processId + ", relationType=" + relationType);
                                continue;
                            }
                            seen.add(key);
                            
                            ps.setInt(1, interfaceId);
                            ps.setInt(2, processId);
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
     * Get existing process relationships as a Map (processId -> relationType)
     */
    private Map<Integer, Integer> getExistingProcessRelationshipsMap(Connection conn, int interfaceId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT process_id, relationtype FROM process_x_interface WHERE interface_id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int processId = rs.getInt("process_id");
                    int relationType = rs.getInt("relationtype");
                    if (processId > 0 && relationType > 0) {
                        existingRelationships.put(processId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get process name from process ID
     */
    private String getProcessName(int processId) {
        String sql = "SELECT primaryname FROM process WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting process name for processId " + processId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get process relation type name from relation type ID
     */
    private String getProcessRelationTypeName(int relationTypeId) {
        String sql = "SELECT primaryname FROM process_x_interface_relationtype WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting process relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
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
     * @param interfaceId The interface ID
     * @param tableType The type of relationship table ("process")
     * @param entityId The ID of the related entity
     * @param relationTypeId The ID of the relation type
     * @param userId The ID of the user performing the action
     */
    private void logAuditHistoryForInsert(Connection conn, int interfaceId, String tableType, 
                                         int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = null;
            if ("process".equalsIgnoreCase(tableType)) {
                entityName = getProcessName(entityId);
            }
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get relation type name
            String relationTypeName = null;
            if ("process".equalsIgnoreCase(tableType)) {
                relationTypeName = getProcessRelationTypeName(relationTypeId);
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
            if ("process".equalsIgnoreCase(tableType)) {
                objectName = "Process X Interface";
                entityFieldName = "Process";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO interface_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, interfaceId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Added");
                ps.setString(5, entityFieldName);
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, entityName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type
                ps.setInt(1, interfaceId);
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
    private void logAuditHistoryForDelete(Connection conn, int interfaceId, String tableType, 
                                         int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = null;
            if ("process".equalsIgnoreCase(tableType)) {
                entityName = getProcessName(entityId);
            }
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get relation type name
            String relationTypeName = null;
            if ("process".equalsIgnoreCase(tableType)) {
                relationTypeName = getProcessRelationTypeName(relationTypeId);
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
            if ("process".equalsIgnoreCase(tableType)) {
                objectName = "Process X Interface";
                entityFieldName = "Process";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO interface_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, interfaceId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Deleted");
                ps.setString(5, entityFieldName);
                ps.setString(6, entityName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type deleted
                ps.setInt(1, interfaceId);
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
    private void logAuditHistoryForUpdate(Connection conn, int interfaceId, String tableType, 
                                        int entityId, int oldRelationTypeId, int newRelationTypeId, int userId) {
        try {
            // Get old and new relation type names
            String oldRelationTypeName = null;
            String newRelationTypeName = null;
            if ("process".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getProcessRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getProcessRelationTypeName(newRelationTypeId);
            }
            if (oldRelationTypeName == null || newRelationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name based on table type
            String objectName = null;
            if ("process".equalsIgnoreCase(tableType)) {
                objectName = "Process X Interface";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO interface_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, interfaceId);
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

