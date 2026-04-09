package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class CapabilityImpactDAO {
    
    // ==================== SYSTEM METHODS ====================
    
    /**
     * Get system relationships for a capability with owner information
     */
    public List<Map<String, Object>> getSystemRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = "SELECT cxs.ID as id, cxs.Capability_ID as capability_id, cxs.System_ID as system_id, " +
                     "cxs.RelationType as relation_type, cxs.Description as description, " +
                     "s.Name as systemName, s.Long_Name as systemLongName, " +
                     "rt.PrimaryName as relationTypeName " +
                     "FROM capability_x_system cxs " +
                     "LEFT JOIN system s ON cxs.System_ID = s.id " +
                     "LEFT JOIN capability_x_system_relationtype rt ON cxs.RelationType = rt.ID " +
                     "WHERE cxs.Capability_ID = ? " +
                     "ORDER BY cxs.ID";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("capabilityId", rs.getInt("capability_id"));
                    
                    int systemId = rs.getInt("system_id");
                    relationship.put("systemId", systemId);
                    
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("systemLongName", rs.getString("systemLongName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get system owners
                    if (systemId > 0) {
                        try {
                            String ownersName = getSystemOwnersString(systemId);
                            String ownersEmail = getSystemOwnersEmail(systemId);
                            relationship.put("systemOwnerName", ownersName);
                            relationship.put("systemOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("CapabilityImpactDAO: Error getting system owners: " + e.getMessage());
                            relationship.put("systemOwnerName", "No owner");
                            relationship.put("systemOwnerEmail", null);
                        }
                    } else {
                        relationship.put("systemOwnerName", "No owner");
                        relationship.put("systemOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        } catch (SQLException e) {
            System.err.println("CapabilityImpactDAO: SQL Error in getSystemRelationshipsByCapabilityId for capabilityId=" + capabilityId);
            System.err.println("SQL: " + sql);
            System.err.println("Error: " + e.getMessage());
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        return relationships;
    }
    
    /**
     * Get all system relation types
     */
    public List<Map<String, Object>> getSystemRelationTypes() throws SQLException {
        // Try without WHERE clause first (like PolicyImpactDAO does)
        String sql = "SELECT ID, PrimaryName, Description, Priority, ReverseName FROM capability_x_system_relationtype ORDER BY COALESCE(Priority, 9999), PrimaryName";
        
        //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: START - Executing query: " + sql);
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: Database connection obtained");
            //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: Database URL: " + conn.getMetaData().getURL());
            //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: Database name: " + conn.getCatalog());
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: PreparedStatement created");
                
                try (ResultSet rs = stmt.executeQuery()) {
                    //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: Query executed, processing results...");
                    
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        Map<String, Object> relationType = new HashMap<>();
                        relationType.put("id", rs.getInt("ID"));
                        relationType.put("primaryname", rs.getString("PrimaryName"));
                        relationType.put("description", rs.getString("Description"));
                        relationType.put("priority", rs.getObject("Priority") != null ? rs.getInt("Priority") : null);
                        relationType.put("reversename", rs.getString("ReverseName"));
                        relationTypes.add(relationType);
                        //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: Found relation type - ID: " + 
                       //     relationType.get("id") + ", Name: " + relationType.get("primaryname"));
                    }
                    //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: Retrieved " + count + " system relation types");
                    
                    // If no results, check if table exists and has any data
                    if (count == 0) {
                        //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: No results found, checking table...");
                        String checkSql = "SELECT COUNT(*) as cnt FROM capability_x_system_relationtype";
                        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                             ResultSet checkRs = checkStmt.executeQuery()) {
                            if (checkRs.next()) {
                                checkRs.getInt("cnt");
                                //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: Total records in table: " + total);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("CapabilityImpactDAO: Error in getSystemRelationTypes: " + e.getMessage());
            System.err.println("SQL: " + sql);
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        //system.out.println("CapabilityImpactDAO.getSystemRelationTypes: END - Returning " + relationTypes.size() + " relation types");
        return relationTypes;
    }
    
    /**
     * Get system owners string
     */
    public String getSystemOwnersString(int systemId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM system_x_objectxpeople sxop
            LEFT JOIN object_x_people oxp ON sxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE sxop.SystemID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, systemId);
            
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
     * Get system owners email
     */
    public String getSystemOwnersEmail(int systemId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM system_x_objectxpeople sxop
            LEFT JOIN object_x_people oxp ON sxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE sxop.SystemID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, systemId);
            
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
     * Get system owner by system ID
     */
    public Map<String, Object> getSystemOwnerBySystemId(int systemId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM system s
            LEFT JOIN system_x_objectxpeople sxoxp ON s.id = sxoxp.SystemID
            LEFT JOIN object_x_people oxp ON sxoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE s.id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, systemId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName == null || ownerName.trim().isEmpty()) {
                        ownerName = "No owner";
                    }
                    owner.put("ownerName", ownerName);
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", "No owner");
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }
    
    /**
     * Get next system capability ID
     */
    private int getNextSystemCapabilityId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM capability_x_system";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Save system relationships for a capability
     */
    public boolean saveSystemRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting (to compare with new ones)
                Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, capabilityId, "system");
                
                // Build new relationships map
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null && relationships.size() > 0) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
                        if (relationship.get("systemId") != null && !relationship.get("systemId").isJsonNull()) {
                            Integer systemId = null;
                            Integer relationType = null;
                            
                            Object systemIdObj = relationship.get("systemId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (systemIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) systemIdObj;
                                systemId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            if (systemId != null && systemId > 0 && relationType != null && relationType > 0) {
                                newRelationshipsMap.put(systemId, relationType);
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
                        logAuditHistoryForDelete(conn, capabilityId, "system", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, capabilityId, "system", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, capabilityId, "system", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM capability_x_system WHERE Capability_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, capabilityId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO capability_x_system (ID, Capability_ID, System_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextSystemCapabilityId(conn);
                    Set<String> seenRelationships = new HashSet<>();
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if systemId is null or empty
                            if (relationship.get("systemId") == null || relationship.get("systemId").isJsonNull()) {
                                continue;
                            }
                            
                            Integer systemId = null;
                            Integer relationType = null;
                            
                            Object systemIdObj = relationship.get("systemId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (systemIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) systemIdObj;
                                systemId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            // Check for duplicate (systemId + relationType combination)
                            if (systemId != null && systemId > 0 && relationType != null && relationType > 0) {
                                String uniqueKey = systemId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("CapabilityImpactDAO: Skipping duplicate system relationship - systemId=" + systemId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, capabilityId);
                                insertStmt.setInt(3, systemId);
                                insertStmt.setInt(4, relationType);
                                insertStmt.setString(5, relationship.has("description") ? relationship.get("description").getAsString() : null);
                                insertStmt.setInt(6, userId);
                                insertStmt.addBatch();
                            }
                        }
                        insertStmt.executeBatch();
                    }
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
    }
    
    // ==================== CLIENT METHODS ====================
    
    /**
     * Get client relationships for a capability with owner information
     */
    public List<Map<String, Object>> getClientRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxc.ID,
                cxc.Capability_ID,
                cxc.Client_ID,
                cxc.RelationType,
                cxc.Description,
                c.PrimaryName as clientName,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM capability_x_client cxc
            LEFT JOIN client c ON cxc.Client_ID = c.ID
            LEFT JOIN capability_x_client_relationtype rt ON cxc.RelationType = rt.ID
            WHERE cxc.Capability_ID = ?
            ORDER BY c.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int clientId = rs.getInt("Client_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("clientId", clientId);
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("clientName", rs.getString("clientName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get client owners
                    try {
                        String ownersName = getClientOwnersString(clientId);
                        String ownersEmail = getClientOwnersEmail(clientId);
                        relationship.put("clientOwnerName", ownersName);
                        relationship.put("clientOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("CapabilityImpactDAO: Error getting client owners: " + e.getMessage());
                        relationship.put("clientOwnerName", "No owner");
                        relationship.put("clientOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get all client relation types
     */
    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        // Try without WHERE clause first (like PolicyImpactDAO does)
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM capability_x_client_relationtype
            ORDER BY COALESCE(Priority, 9999), PrimaryName
        """;
        
        //system.out.println("CapabilityImpactDAO.getClientRelationTypes: Executing query: " + sql);
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationType.put("priority", rs.getObject("Priority") != null ? rs.getInt("Priority") : null);
                relationType.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(relationType);
                //system.out.println("CapabilityImpactDAO.getClientRelationTypes: Found relation type - ID: " + 
                   // relationType.get("id") + ", Name: " + relationType.get("primaryname"));
            }
            //system.out.println("CapabilityImpactDAO.getClientRelationTypes: Retrieved " + count + " client relation types");
        } catch (SQLException e) {
            System.err.println("CapabilityImpactDAO: Error in getClientRelationTypes: " + e.getMessage());
            System.err.println("SQL: " + sql);
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        return relationTypes;
    }
    
    /**
     * Get client owners string
     */
    public String getClientOwnersString(int clientId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM client_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.ClientID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, clientId);
            
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
     * Get client owners email
     */
    public String getClientOwnersEmail(int clientId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM client_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.ClientID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, clientId);
            
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
     * Get client owner by client ID
     */
    public Map<String, Object> getClientOwnerByClientId(int clientId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM client_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.ClientID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName == null || ownerName.trim().isEmpty()) {
                        ownerName = "No owner";
                    }
                    owner.put("ownerName", ownerName);
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", "No owner");
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }
    
    /**
     * Get next client capability ID
     */
    private int getNextClientCapabilityId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM capability_x_client";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Save client relationships for a capability
     */
    public boolean saveClientRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting (to compare with new ones)
                Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, capabilityId, "client");
                
                // Build new relationships map
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null && relationships.size() > 0) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
                        if (relationship.get("clientId") != null && !relationship.get("clientId").isJsonNull()) {
                            Integer clientId = null;
                            Integer relationType = null;
                            
                            Object clientIdObj = relationship.get("clientId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (clientIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) clientIdObj;
                                clientId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            if (clientId != null && clientId > 0 && relationType != null && relationType > 0) {
                                newRelationshipsMap.put(clientId, relationType);
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
                        logAuditHistoryForDelete(conn, capabilityId, "client", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, capabilityId, "client", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, capabilityId, "client", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM capability_x_client WHERE Capability_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, capabilityId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO capability_x_client (ID, Capability_ID, Client_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextClientCapabilityId(conn);
                    Set<String> seenRelationships = new HashSet<>();
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if clientId is null or empty
                            if (relationship.get("clientId") == null || relationship.get("clientId").isJsonNull()) {
                                continue;
                            }
                            
                            Integer clientId = null;
                            Integer relationType = null;
                            
                            Object clientIdObj = relationship.get("clientId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (clientIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) clientIdObj;
                                clientId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            // Check for duplicate (clientId + relationType combination)
                            if (clientId != null && clientId > 0 && relationType != null && relationType > 0) {
                                String uniqueKey = clientId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("CapabilityImpactDAO: Skipping duplicate client relationship - clientId=" + clientId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, capabilityId);
                                insertStmt.setInt(3, clientId);
                                insertStmt.setInt(4, relationType);
                                insertStmt.setString(5, relationship.has("description") ? relationship.get("description").getAsString() : null);
                                insertStmt.setInt(6, userId);
                                insertStmt.addBatch();
                            }
                        }
                        insertStmt.executeBatch();
                    }
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
    }
    
    // ==================== PRODUCT METHODS ====================
    
    /**
     * Get product relationships for a capability with owner information
     */
    public List<Map<String, Object>> getProductRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxp.ID,
                cxp.Capability_ID,
                cxp.Product_ID,
                cxp.RelationType,
                cxp.Description,
                p.primaryname as productName,
                p.refnumber as productRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM capability_x_product cxp
            LEFT JOIN product p ON cxp.Product_ID = p.id
            LEFT JOIN capability_x_product_relationtype rt ON cxp.RelationType = rt.ID
            WHERE cxp.Capability_ID = ? AND p.deleteddatetime IS NULL
            ORDER BY p.primaryname
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("Product_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("productId", productId);
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get product owners
                    try {
                        String ownersName = getProductOwnersString(productId);
                        String ownersEmail = getProductOwnersEmail(productId);
                        relationship.put("productOwnerName", ownersName);
                        relationship.put("productOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("CapabilityImpactDAO: Error getting product owners: " + e.getMessage());
                        relationship.put("productOwnerName", "No owner");
                        relationship.put("productOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get all product relation types
     */
    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        // Try without WHERE clause first (like PolicyImpactDAO does)
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM capability_x_product_relationtype
            ORDER BY COALESCE(Priority, 9999), PrimaryName
        """;
        
        //system.out.println("CapabilityImpactDAO.getProductRelationTypes: Executing query: " + sql);
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationType.put("priority", rs.getObject("Priority") != null ? rs.getInt("Priority") : null);
                relationType.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(relationType);
                //system.out.println("CapabilityImpactDAO.getProductRelationTypes: Found relation type - ID: " + 
                   // relationType.get("id") + ", Name: " + relationType.get("primaryname"));
            }
            //system.out.println("CapabilityImpactDAO.getProductRelationTypes: Retrieved " + count + " product relation types");
        } catch (SQLException e) {
            System.err.println("CapabilityImpactDAO: Error in getProductRelationTypes: " + e.getMessage());
            System.err.println("SQL: " + sql);
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        return relationTypes;
    }
    
    /**
     * Get product owners string
     */
    public String getProductOwnersString(int productId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM product_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.product_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, productId);
            
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
     * Get product owners email
     */
    public String getProductOwnersEmail(int productId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM product_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.product_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, productId);
            
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
     * Get product owner by product ID
     */
    public Map<String, Object> getProductOwnerByProductId(int productId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM product_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.product_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName == null || ownerName.trim().isEmpty()) {
                        ownerName = "No owner";
                    }
                    owner.put("ownerName", ownerName);
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", "No owner");
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }
    
    /**
     * Get next product capability ID
     */
    private int getNextProductCapabilityId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM capability_x_product";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Save product relationships for a capability
     */
    public boolean saveProductRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting (to compare with new ones)
                Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, capabilityId, "product");
                
                // Build new relationships map
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null && relationships.size() > 0) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
                        if (relationship.get("productId") != null && !relationship.get("productId").isJsonNull()) {
                            Integer productId = null;
                            Integer relationType = null;
                            
                            Object productIdObj = relationship.get("productId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (productIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) productIdObj;
                                productId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            if (productId != null && productId > 0 && relationType != null && relationType > 0) {
                                newRelationshipsMap.put(productId, relationType);
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
                        logAuditHistoryForDelete(conn, capabilityId, "product", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, capabilityId, "product", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, capabilityId, "product", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM capability_x_product WHERE Capability_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, capabilityId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO capability_x_product (ID, Capability_ID, Product_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextProductCapabilityId(conn);
                    Set<String> seenRelationships = new HashSet<>();
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if productId is null or empty
                            if (relationship.get("productId") == null || relationship.get("productId").isJsonNull()) {
                                continue;
                            }
                            
                            Integer productId = null;
                            Integer relationType = null;
                            
                            Object productIdObj = relationship.get("productId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (productIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) productIdObj;
                                productId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            // Check for duplicate (productId + relationType combination)
                            if (productId != null && productId > 0 && relationType != null && relationType > 0) {
                                String uniqueKey = productId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("CapabilityImpactDAO: Skipping duplicate product relationship - productId=" + productId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, capabilityId);
                                insertStmt.setInt(3, productId);
                                insertStmt.setInt(4, relationType);
                                insertStmt.setString(5, relationship.has("description") ? relationship.get("description").getAsString() : null);
                                insertStmt.setInt(6, userId);
                                insertStmt.addBatch();
                            }
                        }
                        insertStmt.executeBatch();
                    }
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
    }
    
    // ==================== PROCESS METHODS ====================
    
    /**
     * Get process relationships for a capability with owner information
     */
    public List<Map<String, Object>> getProcessRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxpr.ID,
                cxpr.Capability_ID,
                cxpr.Process_ID,
                cxpr.RelationType,
                cxpr.Description,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM capability_x_process cxpr
            LEFT JOIN process p ON cxpr.Process_ID = p.id
            LEFT JOIN capability_x_process_relationtype rt ON cxpr.RelationType = rt.ID
            WHERE cxpr.Capability_ID = ? AND p.deleteddatetime IS NULL
            ORDER BY p.primaryname
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int processId = rs.getInt("Process_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("processId", processId);
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get process owners
                    try {
                        String ownersName = getProcessOwnersString(processId);
                        String ownersEmail = getProcessOwnersEmail(processId);
                        relationship.put("processOwnerName", ownersName);
                        relationship.put("processOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("CapabilityImpactDAO: Error getting process owners: " + e.getMessage());
                        relationship.put("processOwnerName", "No owner");
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
        // Try without WHERE clause first (like PolicyImpactDAO does)
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM capability_x_process_relationtype
            ORDER BY COALESCE(Priority, 9999), PrimaryName
        """;
        
        //system.out.println("CapabilityImpactDAO.getProcessRelationTypes: Executing query: " + sql);
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationType.put("priority", rs.getObject("Priority") != null ? rs.getInt("Priority") : null);
                relationType.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(relationType);
                //system.out.println("CapabilityImpactDAO.getProcessRelationTypes: Found relation type - ID: " + 
                 //   relationType.get("id") + ", Name: " + relationType.get("primaryname"));
            }
            //system.out.println("CapabilityImpactDAO.getProcessRelationTypes: Retrieved " + count + " process relation types");
        } catch (SQLException e) {
            System.err.println("CapabilityImpactDAO: Error in getProcessRelationTypes: " + e.getMessage());
            System.err.println("SQL: " + sql);
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        return relationTypes;
    }
    
    /**
     * Get process owners string
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
     * Get process owners email
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
     * Get process owner by process ID
     */
    public Map<String, Object> getProcessOwnerByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM process p
            LEFT JOIN process_x_objectxpeople pxop ON p.id = pxop.process_id
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE p.id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, processId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName == null || ownerName.trim().isEmpty()) {
                        ownerName = "No owner";
                    }
                    owner.put("ownerName", ownerName);
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", "No owner");
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }
    
    /**
     * Get next process capability ID
     */
    private int getNextProcessCapabilityId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM capability_x_process";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Save process relationships for a capability
     */
    public boolean saveProcessRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting (to compare with new ones)
                Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, capabilityId, "process");
                
                // Build new relationships map
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null && relationships.size() > 0) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
                        if (relationship.get("processId") != null && !relationship.get("processId").isJsonNull()) {
                            Integer processId = null;
                            Integer relationType = null;
                            
                            Object processIdObj = relationship.get("processId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (processIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) processIdObj;
                                processId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            if (processId != null && processId > 0 && relationType != null && relationType > 0) {
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
                        logAuditHistoryForDelete(conn, capabilityId, "process", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, capabilityId, "process", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, capabilityId, "process", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM capability_x_process WHERE Capability_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, capabilityId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO capability_x_process (ID, Capability_ID, Process_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextProcessCapabilityId(conn);
                    Set<String> seenRelationships = new HashSet<>();
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if processId is null or empty
                            if (relationship.get("processId") == null || relationship.get("processId").isJsonNull()) {
                                continue;
                            }
                            
                            Integer processId = null;
                            Integer relationType = null;
                            
                            Object processIdObj = relationship.get("processId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (processIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) processIdObj;
                                processId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            // Check for duplicate (processId + relationType combination)
                            if (processId != null && processId > 0 && relationType != null && relationType > 0) {
                                String uniqueKey = processId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("CapabilityImpactDAO: Skipping duplicate process relationship - processId=" + processId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, capabilityId);
                                insertStmt.setInt(3, processId);
                                insertStmt.setInt(4, relationType);
                                insertStmt.setString(5, relationship.has("description") ? relationship.get("description").getAsString() : null);
                                insertStmt.setInt(6, userId);
                                insertStmt.addBatch();
                            }
                        }
                        insertStmt.executeBatch();
                    }
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
    }
    
    // ==================== GLOSSARY METHODS ====================
    
    /**
     * Get glossary relationships for a capability with owner information
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxg.ID,
                cxg.Capability_ID,
                cxg.Glossary_ID,
                cxg.RelationType,
                cxg.Description,
                g.Name as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                g.Type as glossaryTypeId,
                gt.Name as glossaryTypeName,
                rt.PrimaryName as relationTypeName
            FROM capability_x_glossary cxg
            LEFT JOIN glossary g ON cxg.Glossary_ID = g.ID
            LEFT JOIN glossary_type gt ON g.Type = gt.ID
            LEFT JOIN capability_x_glossary_relationtype rt ON cxg.RelationType = rt.ID
            WHERE cxg.Capability_ID = ?
            ORDER BY g.Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int glossaryId = rs.getInt("Glossary_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("glossaryId", glossaryId);
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
                    relationship.put("glossaryTypeId", rs.getInt("glossaryTypeId"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get owners
                    String ownerNames = getGlossaryOwnersString(glossaryId);
                    String ownerEmails = getGlossaryOwnersEmail(glossaryId);
                    relationship.put("glossaryOwnerName", ownerNames != null && !ownerNames.isEmpty() ? ownerNames : "No owner");
                    relationship.put("glossaryOwnerEmail", ownerEmails);
                    
                    // Get type name from JOIN with glossary_type
                    String typeName = rs.getString("glossaryTypeName");
                    relationship.put("glossaryTypeName", typeName != null ? typeName : null);
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get glossary relation types
     */
    public List<Map<String, Object>> getGlossaryRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        // Try without WHERE clause first (like PolicyImpactDAO does)
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM capability_x_glossary_relationtype
            ORDER BY COALESCE(Priority, 9999), PrimaryName
        """;
        
        //system.out.println("CapabilityImpactDAO.getGlossaryRelationTypes: Executing query: " + sql);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> rt = new HashMap<>();
                rt.put("id", rs.getInt("ID"));
                rt.put("primaryname", rs.getString("PrimaryName"));
                rt.put("description", rs.getString("Description"));
                rt.put("priority", rs.getObject("Priority") != null ? rs.getInt("Priority") : null);
                rt.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(rt);
                //system.out.println("CapabilityImpactDAO.getGlossaryRelationTypes: Found relation type - ID: " + 
                 //   rt.get("id") + ", Name: " + rt.get("primaryname"));
            }
            //system.out.println("CapabilityImpactDAO.getGlossaryRelationTypes: Retrieved " + count + " glossary relation types");
        } catch (SQLException e) {
            System.err.println("CapabilityImpactDAO: Error in getGlossaryRelationTypes: " + e.getMessage());
            System.err.println("SQL: " + sql);
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        return relationTypes;
    }
    
    /**
     * Get glossary owners string
     */
    public String getGlossaryOwnersString(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM glossary_x_objectxpeople gxop
            LEFT JOIN object_x_people oxp ON gxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE gxop.GlossaryID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, glossaryId);
            
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
     * Get glossary owners email
     */
    public String getGlossaryOwnersEmail(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM glossary_x_objectxpeople gxop
            LEFT JOIN object_x_people oxp ON gxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE gxop.GlossaryID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, glossaryId);
            
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
     * Get glossary owner by glossary ID
     */
    public Map<String, Object> getGlossaryOwnerByGlossaryId(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM glossary_x_objectxpeople gxop
            LEFT JOIN object_x_people oxp ON gxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE gxop.GlossaryID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, glossaryId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName == null || ownerName.trim().isEmpty()) {
                        ownerName = "No owner";
                    }
                    owner.put("ownerName", ownerName);
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", "No owner");
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }
    
    /**
     * Get next glossary capability ID
     */
    private int getNextGlossaryCapabilityId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM capability_x_glossary";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Save glossary relationships
     */
    public boolean saveGlossaryRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting (to compare with new ones)
                Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, capabilityId, "glossary");
                
                // Build new relationships map
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null && relationships.size() > 0) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
                        if (relationship.get("glossaryId") != null && !relationship.get("glossaryId").isJsonNull()) {
                            Integer glossaryId = null;
                            Integer relationType = null;
                            
                            Object glossaryIdObj = relationship.get("glossaryId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (glossaryIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) glossaryIdObj;
                                glossaryId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            if (glossaryId != null && glossaryId > 0 && relationType != null && relationType > 0) {
                                newRelationshipsMap.put(glossaryId, relationType);
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
                        logAuditHistoryForDelete(conn, capabilityId, "glossary", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, capabilityId, "glossary", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, capabilityId, "glossary", entityId, newRelationType, userId);
                    }
                }
                
                // Delete all existing relationships
                String deleteSql = "DELETE FROM capability_x_glossary WHERE Capability_ID = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, capabilityId);
                    ps.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = """
                        INSERT INTO capability_x_glossary (ID, Capability_ID, Glossary_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID)
                        VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)
                    """;
                    
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        int nextId = getNextGlossaryCapabilityId(conn);
                        Set<String> seenRelationships = new HashSet<>();
                        
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if glossaryId is null or empty
                            if (relationship.get("glossaryId") == null || relationship.get("glossaryId").isJsonNull()) {
                                continue;
                            }
                            
                            Integer glossaryId = null;
                            Integer relationType = null;
                            
                            Object glossaryIdObj = relationship.get("glossaryId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (glossaryIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) glossaryIdObj;
                                glossaryId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            // Check for duplicate (glossaryId + relationType combination)
                            if (glossaryId != null && glossaryId > 0 && relationType != null && relationType > 0) {
                                String uniqueKey = glossaryId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("CapabilityImpactDAO: Skipping duplicate glossary relationship - glossaryId=" + glossaryId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                ps.setInt(1, nextId++);
                                ps.setInt(2, capabilityId);
                                ps.setInt(3, glossaryId);
                                ps.setInt(4, relationType);
                                ps.setString(5, relationship.has("description") ? relationship.get("description").getAsString() : null);
                                ps.setInt(6, userId);
                                ps.addBatch();
                            }
                        }
                        ps.executeBatch();
                    }
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
    }
    
    // ==================== BUSINESS AREA METHODS ====================
    
    /**
     * Get business area relationships for a capability with owner information
     */
    public List<Map<String, Object>> getBusinessAreaRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxba.ID as id,
                cxba.Capability_ID as capability_id,
                cxba.BusinessArea_ID as businessarea_id,
                cxba.RelationType as relation_type,
                ba.PrimaryName as businessAreaName,
                rt.PrimaryName as relationTypeName
            FROM capability_x_businessarea cxba
            LEFT JOIN business_area ba ON cxba.BusinessArea_ID = ba.ID
            LEFT JOIN capability_x_businessarea_relationtype rt ON cxba.RelationType = rt.ID
            WHERE cxba.Capability_ID = ?
            ORDER BY ba.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, capabilityId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int businessAreaId = rs.getInt("businessarea_id");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("capabilityId", rs.getInt("capability_id"));
                    relationship.put("businessAreaId", businessAreaId);
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("businessAreaName", rs.getString("businessAreaName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get business area owners
                    try {
                        String ownersName = getBusinessAreaOwnersString(businessAreaId);
                        String ownersEmail = getBusinessAreaOwnersEmail(businessAreaId);
                        relationship.put("businessAreaOwnerName", ownersName);
                        relationship.put("businessAreaOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("CapabilityImpactDAO: Error getting business area owners: " + e.getMessage());
                        relationship.put("businessAreaOwnerName", "No owner");
                        relationship.put("businessAreaOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get all business area relation types
     */
    public List<Map<String, Object>> getBusinessAreaRelationTypes() throws SQLException {
        // Try without WHERE clause first (like PolicyImpactDAO does)
        String sql = "SELECT ID, PrimaryName, Description, Priority, ReverseName FROM capability_x_businessarea_relationtype ORDER BY COALESCE(Priority, 9999), PrimaryName";
        
        //system.out.println("CapabilityImpactDAO.getBusinessAreaRelationTypes: Executing query: " + sql);
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationType.put("priority", rs.getObject("Priority") != null ? rs.getInt("Priority") : null);
                relationType.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(relationType);
                //system.out.println("CapabilityImpactDAO.getBusinessAreaRelationTypes: Found relation type - ID: " + 
                   // relationType.get("id") + ", Name: " + relationType.get("primaryname"));
            }
            //system.out.println("CapabilityImpactDAO.getBusinessAreaRelationTypes: Retrieved " + count + " business area relation types");
        } catch (SQLException e) {
            System.err.println("CapabilityImpactDAO: Error in getBusinessAreaRelationTypes: " + e.getMessage());
            System.err.println("SQL: " + sql);
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        return relationTypes;
    }
    
    /**
     * Get business area owners string
     */
    public String getBusinessAreaOwnersString(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM businessarea_x_objectxpeople bxop
            LEFT JOIN object_x_people oxp ON bxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE bxop.BusinessAreaID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, businessAreaId);
            
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
     * Get business area owners email
     */
    public String getBusinessAreaOwnersEmail(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM businessarea_x_objectxpeople bxop
            LEFT JOIN object_x_people oxp ON bxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE bxop.BusinessAreaID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, businessAreaId);
            
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
     * Get business area owner by business area ID
     */
    public Map<String, Object> getBusinessAreaOwnerByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM businessarea_x_objectxpeople bxop
            LEFT JOIN object_x_people oxp ON bxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE bxop.BusinessAreaID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, businessAreaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName == null || ownerName.trim().isEmpty()) {
                        ownerName = "No owner";
                    }
                    owner.put("ownerName", ownerName);
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", "No owner");
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }
    
    /**
     * Get next business area capability ID
     */
    private int getNextBusinessAreaCapabilityId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM capability_x_businessarea";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Save business area relationships for a capability
     */
    public boolean saveBusinessAreaRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting (to compare with new ones)
                Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, capabilityId, "businessarea");
                
                // Build new relationships map
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null && relationships.size() > 0) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
                        if (relationship.get("businessAreaId") != null && !relationship.get("businessAreaId").isJsonNull()) {
                            Integer businessAreaId = null;
                            Integer relationType = null;
                            
                            Object businessAreaIdObj = relationship.get("businessAreaId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (businessAreaIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) businessAreaIdObj;
                                businessAreaId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            if (businessAreaId != null && businessAreaId > 0 && relationType != null && relationType > 0) {
                                newRelationshipsMap.put(businessAreaId, relationType);
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
                        logAuditHistoryForDelete(conn, capabilityId, "businessarea", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, capabilityId, "businessarea", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, capabilityId, "businessarea", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM capability_x_businessarea WHERE Capability_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, capabilityId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO capability_x_businessarea (ID, Capability_ID, BusinessArea_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextBusinessAreaCapabilityId(conn);
                    Set<String> seenRelationships = new HashSet<>();
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if businessAreaId is null or empty
                            if (relationship.get("businessAreaId") == null || relationship.get("businessAreaId").isJsonNull()) {
                                continue;
                            }
                            
                            Integer businessAreaId = null;
                            Integer relationType = null;
                            
                            Object businessAreaIdObj = relationship.get("businessAreaId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (businessAreaIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) businessAreaIdObj;
                                businessAreaId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            // Check for duplicate (businessAreaId + relationType combination)
                            if (businessAreaId != null && businessAreaId > 0 && relationType != null && relationType > 0) {
                                String uniqueKey = businessAreaId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("CapabilityImpactDAO: Skipping duplicate business area relationship - businessAreaId=" + businessAreaId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, capabilityId);
                                insertStmt.setInt(3, businessAreaId);
                                insertStmt.setInt(4, relationType);
                                insertStmt.setInt(5, userId);
                                insertStmt.addBatch();
                            }
                        }
                        insertStmt.executeBatch();
                    }
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
    }
    
    // ==================== LEGAL ENTITY METHODS ====================
    
    /**
     * Get legal entity relationships for a capability with owner information
     */
    public List<Map<String, Object>> getLegalRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxl.ID as id,
                cxl.Capability_ID as capability_id,
                cxl.Legal_ID as legal_id,
                cxl.RelationType as relation_type,
                l.ShortName as legalShortName,
                l.LongName as legalLongName,
                rt.PrimaryName as relationTypeName
            FROM capability_x_legal cxl
            LEFT JOIN legal l ON cxl.Legal_ID = l.ID
            LEFT JOIN capability_x_legal_relationtype rt ON cxl.RelationType = rt.ID
            WHERE cxl.Capability_ID = ?
            ORDER BY cxl.ID
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, capabilityId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int legalId = rs.getInt("legal_id");
                    if (rs.wasNull() || legalId == 0) {
                        // Skip if legal_id is NULL or 0
                        continue;
                    }
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("capabilityId", rs.getInt("capability_id"));
                    relationship.put("legalId", legalId);
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("legalShortName", rs.getString("legalShortName"));
                    relationship.put("legalLongName", rs.getString("legalLongName"));
                    relationship.put("legalName", rs.getString("legalShortName")); // For backward compatibility
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get legal entity owners
                    try {
                        String ownersName = getLegalOwnersString(legalId);
                        String ownersEmail = getLegalOwnersEmail(legalId);
                        relationship.put("legalOwnerName", ownersName);
                        relationship.put("legalOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("CapabilityImpactDAO: Error getting legal entity owners: " + e.getMessage());
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
     * Get all legal entity relation types
     */
    public List<Map<String, Object>> getLegalRelationTypes() throws SQLException {
        // Try without WHERE clause first (like PolicyImpactDAO does)
        String sql = "SELECT ID, PrimaryName, Description, Priority, ReverseName FROM capability_x_legal_relationtype ORDER BY COALESCE(Priority, 9999), PrimaryName";
        
        //system.out.println("CapabilityImpactDAO.getLegalRelationTypes: Executing query: " + sql);
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationType.put("priority", rs.getObject("Priority") != null ? rs.getInt("Priority") : null);
                relationType.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(relationType);
                //system.out.println("CapabilityImpactDAO.getLegalRelationTypes: Found relation type - ID: " + 
                   // relationType.get("id") + ", Name: " + relationType.get("primaryname"));
            }
            //system.out.println("CapabilityImpactDAO.getLegalRelationTypes: Retrieved " + count + " legal relation types");
        } catch (SQLException e) {
            System.err.println("CapabilityImpactDAO: Error in getLegalRelationTypes: " + e.getMessage());
            System.err.println("SQL: " + sql);
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        return relationTypes;
    }
    
    /**
     * Get legal entity owners string
     */
    public String getLegalOwnersString(int legalId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM legal_x_objectxpeople lxop
            LEFT JOIN object_x_people oxp ON lxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE lxop.Legal_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, legalId);
            
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
     * Get legal entity owners email
     */
    public String getLegalOwnersEmail(int legalId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM legal_x_objectxpeople lxop
            LEFT JOIN object_x_people oxp ON lxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE lxop.Legal_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, legalId);
            
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
     * Get legal entity owner by legal ID
     */
    public Map<String, Object> getLegalOwnerByLegalId(int legalId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM legal_x_objectxpeople lxop
            LEFT JOIN object_x_people oxp ON lxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE lxop.Legal_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, legalId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName == null || ownerName.trim().isEmpty()) {
                        ownerName = "No owner";
                    }
                    owner.put("ownerName", ownerName);
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", "No owner");
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }
    
    /**
     * Get next legal capability ID
     */
    private int getNextLegalCapabilityId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM capability_x_legal";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Save legal entity relationships for a capability
     */
    public boolean saveLegalRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting (to compare with new ones)
                Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, capabilityId, "legal");
                
                // Build new relationships map
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null && relationships.size() > 0) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
                        if (relationship.get("legalId") != null && !relationship.get("legalId").isJsonNull()) {
                            Integer legalId = null;
                            Integer relationType = null;
                            
                            Object legalIdObj = relationship.get("legalId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (legalIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) legalIdObj;
                                legalId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            if (legalId != null && legalId > 0 && relationType != null && relationType > 0) {
                                newRelationshipsMap.put(legalId, relationType);
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
                        logAuditHistoryForDelete(conn, capabilityId, "legal", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, capabilityId, "legal", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, capabilityId, "legal", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM capability_x_legal WHERE Capability_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, capabilityId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO capability_x_legal (ID, Capability_ID, Legal_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextLegalCapabilityId(conn);
                    Set<String> seenRelationships = new HashSet<>();
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if legalId is null or empty
                            if (relationship.get("legalId") == null || relationship.get("legalId").isJsonNull()) {
                                continue;
                            }
                            
                            Integer legalId = null;
                            Integer relationType = null;
                            
                            Object legalIdObj = relationship.get("legalId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (legalIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) legalIdObj;
                                legalId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            // Check for duplicate (legalId + relationType combination)
                            if (legalId != null && legalId > 0 && relationType != null && relationType > 0) {
                                String uniqueKey = legalId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("CapabilityImpactDAO: Skipping duplicate legal entity relationship - legalId=" + legalId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, capabilityId);
                                insertStmt.setInt(3, legalId);
                                insertStmt.setInt(4, relationType);
                                insertStmt.setInt(5, userId);
                                insertStmt.addBatch();
                            }
                        }
                        insertStmt.executeBatch();
                    }
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
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    /**
     * Get capability relationships for a system (reverse lookup)
     */
    public List<Map<String, Object>> getCapabilityRelationshipsBySystemId(int systemId) throws SQLException {
        String sql = """
            SELECT 
                cxs.ID as id,
                cxs.Capability_ID as capabilityId,
                cxs.System_ID as systemId,
                cxs.RelationType as relationType,
                cxs.Description as description,
                c.PrimaryName as capabilityName,
                c.RefNumber as capabilityRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM capability_x_system cxs
            LEFT JOIN capability c ON cxs.Capability_ID = c.ID
            LEFT JOIN capability_x_system_relationtype rt ON cxs.RelationType = rt.ID
            WHERE cxs.System_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY c.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, systemId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("capabilityId", rs.getInt("capabilityId"));
                    relationship.put("systemId", rs.getInt("systemId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("capabilityRefNumber", rs.getString("capabilityRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get capability relationships for a client (reverse lookup)
     */
    public List<Map<String, Object>> getCapabilityRelationshipsByClientId(int clientId) throws SQLException {
        String sql = """
            SELECT 
                cxc.ID,
                cxc.Capability_ID,
                cxc.Client_ID,
                cxc.RelationType,
                cxc.Description,
                c.PrimaryName as capabilityName,
                c.RefNumber as capabilityRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM capability_x_client cxc
            LEFT JOIN capability c ON cxc.Capability_ID = c.ID
            LEFT JOIN capability_x_client_relationtype rt ON cxc.RelationType = rt.ID
            WHERE cxc.Client_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY c.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("clientId", rs.getInt("Client_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("capabilityRefNumber", rs.getString("capabilityRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get capability relationships for a product (reverse lookup)
     */
    public List<Map<String, Object>> getCapabilityRelationshipsByProductId(int productId) throws SQLException {
        String sql = """
            SELECT 
                cxp.ID,
                cxp.Capability_ID,
                cxp.Product_ID,
                cxp.RelationType,
                cxp.Description,
                c.PrimaryName as capabilityName,
                c.RefNumber as capabilityRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM capability_x_product cxp
            LEFT JOIN capability c ON cxp.Capability_ID = c.ID
            LEFT JOIN capability_x_product_relationtype rt ON cxp.RelationType = rt.ID
            WHERE cxp.Product_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY c.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("productId", rs.getInt("Product_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("capabilityRefNumber", rs.getString("capabilityRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get owner information
                    int capabilityId = rs.getInt("Capability_ID");
                    String ownerName = getCapabilityOwnersString(capabilityId);
                    relationship.put("capabilityOwnerName", ownerName != null ? ownerName : "No owner");
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get capability relationships for a process (reverse lookup)
     */
    public List<Map<String, Object>> getCapabilityRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                cxp.ID,
                cxp.Capability_ID,
                cxp.Process_ID,
                cxp.RelationType,
                cxp.Description,
                c.PrimaryName as capabilityName,
                c.RefNumber as capabilityRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM capability_x_process cxp
            LEFT JOIN capability c ON cxp.Capability_ID = c.ID
            LEFT JOIN capability_x_process_relationtype rt ON cxp.RelationType = rt.ID
            WHERE cxp.Process_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY c.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("processId", rs.getInt("Process_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("capabilityRefNumber", rs.getString("capabilityRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get owner information
                    int capabilityId = rs.getInt("Capability_ID");
                    String ownerName = getCapabilityOwnersString(capabilityId);
                    relationship.put("capabilityOwnerName", ownerName != null ? ownerName : "No owner");
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get capability relationships for a glossary (reverse lookup)
     */
    public List<Map<String, Object>> getCapabilityRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                cxg.ID,
                cxg.Capability_ID,
                cxg.Glossary_ID,
                cxg.RelationType,
                cxg.Description,
                c.PrimaryName as capabilityName,
                c.RefNumber as capabilityRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM capability_x_glossary cxg
            LEFT JOIN capability c ON cxg.Capability_ID = c.ID
            LEFT JOIN capability_x_glossary_relationtype rt ON cxg.RelationType = rt.ID
            WHERE cxg.Glossary_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY c.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("glossaryId", rs.getInt("Glossary_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("capabilityRefNumber", rs.getString("capabilityRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get capability relationships for a business area (reverse lookup)
     */
    public List<Map<String, Object>> getCapabilityRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                cxba.ID,
                cxba.Capability_ID,
                cxba.BusinessArea_ID,
                cxba.RelationType,
                c.PrimaryName as capabilityName,
                c.RefNumber as capabilityRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM capability_x_businessarea cxba
            LEFT JOIN capability c ON cxba.Capability_ID = c.ID
            LEFT JOIN capability_x_businessarea_relationtype rt ON cxba.RelationType = rt.ID
            WHERE cxba.BusinessArea_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY c.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("businessAreaId", rs.getInt("BusinessArea_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", null);
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("capabilityRefNumber", rs.getString("capabilityRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get owner information
                    int capabilityId = rs.getInt("Capability_ID");
                    String ownerName = getCapabilityOwnersString(capabilityId);
                    relationship.put("capabilityOwnerName", ownerName != null ? ownerName : "No owner");
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get capability relationships for a legal entity (reverse lookup)
     */
    public List<Map<String, Object>> getCapabilityRelationshipsByLegalId(int legalId) throws SQLException {
        String sql = """
            SELECT 
                cxl.ID,
                cxl.Capability_ID,
                cxl.Legal_ID,
                cxl.RelationType,
                c.PrimaryName as capabilityName,
                c.RefNumber as capabilityRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM capability_x_legal cxl
            LEFT JOIN capability c ON cxl.Capability_ID = c.ID
            LEFT JOIN capability_x_legal_relationtype rt ON cxl.RelationType = rt.ID
            WHERE cxl.Legal_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY c.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, legalId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("legalId", rs.getInt("Legal_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("capabilityRefNumber", rs.getString("capabilityRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get owner information
                    int capabilityId = rs.getInt("Capability_ID");
                    String ownerName = getCapabilityOwnersString(capabilityId);
                    relationship.put("capabilityOwnerName", ownerName != null ? ownerName : "No owner");
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get capability owners string (concatenated names)
     */
    public String getCapabilityOwnersString(int capabilityId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM capability_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.CapabilityID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, capabilityId);
            
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
                
                return ownersList.length() > 0 ? ownersList.toString() : null;
            }
        }
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
        String sql = null;
        String columnName = null;

        switch (tableType.toLowerCase()) {
            case "glossary":
                sql = "SELECT Name FROM glossary WHERE ID = ?";
                columnName = "Name";
                break;
            case "system":
                sql = "SELECT Name FROM system WHERE id = ?";
                columnName = "Name";
                break;
            case "process":
                sql = "SELECT primaryname FROM process WHERE id = ?";
                columnName = "primaryname";
                break;
            case "client":
                sql = "SELECT PrimaryName FROM client WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "product":
                sql = "SELECT PrimaryName FROM product WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "businessarea":
                sql = "SELECT Name FROM businessarea WHERE ID = ?";
                columnName = "Name";
                break;
            case "legal":
                sql = "SELECT Name FROM legal WHERE ID = ?";
                columnName = "Name";
                break;
            default:
                System.err.println("Unknown table type: " + tableType);
                return null;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(columnName);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting entity name for " + tableType + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Get relation type name based on table type and relation type ID
     */
    private String getRelationTypeName(String tableType, int relationTypeId) {
        String tableName = null;

        switch (tableType.toLowerCase()) {
            case "glossary":
                tableName = "capability_x_glossary_relationtype";
                break;
            case "system":
                tableName = "capability_x_system_relationtype";
                break;
            case "process":
                tableName = "capability_x_process_relationtype";
                break;
            case "client":
                tableName = "capability_x_client_relationtype";
                break;
            case "product":
                tableName = "capability_x_product_relationtype";
                break;
            case "businessarea":
                tableName = "capability_x_businessarea_relationtype";
                break;
            case "legal":
                tableName = "capability_x_legal_relationtype";
                break;
            default:
                System.err.println("Unknown table type: " + tableType);
                return null;
        }

        String sql = "SELECT PrimaryName FROM " + tableName + " WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting relation type name for " + tableType + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Get existing relationships map before deletion (for audit tracking)
     */
    private Map<Integer, Integer> getExistingRelationshipsMap(Connection conn, int capabilityId, String tableType) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = null;
        String entityColumn = null;

        switch (tableType.toLowerCase()) {
            case "glossary":
                sql = "SELECT Glossary_ID, RelationType FROM capability_x_glossary WHERE Capability_ID = ?";
                entityColumn = "Glossary_ID";
                break;
            case "system":
                sql = "SELECT System_ID, RelationType FROM capability_x_system WHERE Capability_ID = ?";
                entityColumn = "System_ID";
                break;
            case "process":
                sql = "SELECT Process_ID, RelationType FROM capability_x_process WHERE Capability_ID = ?";
                entityColumn = "Process_ID";
                break;
            case "client":
                sql = "SELECT Client_ID, RelationType FROM capability_x_client WHERE Capability_ID = ?";
                entityColumn = "Client_ID";
                break;
            case "product":
                sql = "SELECT Product_ID, RelationType FROM capability_x_product WHERE Capability_ID = ?";
                entityColumn = "Product_ID";
                break;
            case "businessarea":
                sql = "SELECT BusinessArea_ID, RelationType FROM capability_x_businessarea WHERE Capability_ID = ?";
                entityColumn = "BusinessArea_ID";
                break;
            case "legal":
                sql = "SELECT Legal_ID, RelationType FROM capability_x_legal WHERE Capability_ID = ?";
                entityColumn = "Legal_ID";
                break;
            default:
                return existingRelationships;
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int entityId = rs.getInt(entityColumn);
                    int relationType = rs.getInt("RelationType");
                    if (entityId > 0 && relationType > 0) {
                        existingRelationships.put(entityId, relationType);
                    }
                }
            }
        }
        return existingRelationships;
    }

    /**
     * Log audit history for INSERT operation
     */
    private void logAuditHistoryForInsert(Connection conn, int capabilityId, String tableType,
                                          int entityId, int relationTypeId, int userId) {
        try {
            String objectName = "Capability X " + capitalizeFirstLetter(tableType);
            String entityFieldName = capitalizeFirstLetter(tableType);
            String entityName = getEntityName(tableType, entityId);
            String relationTypeName = getRelationTypeName(tableType, relationTypeId);
            String userName = getUserFullName(userId);

            String auditSql = "INSERT INTO capability_audit_history " +
                    "(id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                    "VALUES (?, ?, ?, ?, ?, NULL, ?, ?, NOW(), NOW())";

            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                // Log entity insertion
                auditStmt.setInt(1, capabilityId);
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
    private void logAuditHistoryForDelete(Connection conn, int capabilityId, String tableType,
                                          int entityId, int relationTypeId, int userId) {
        try {
            String objectName = "Capability X " + capitalizeFirstLetter(tableType);
            String entityFieldName = capitalizeFirstLetter(tableType);
            String entityName = getEntityName(tableType, entityId);
            String relationTypeName = getRelationTypeName(tableType, relationTypeId);
            String userName = getUserFullName(userId);

            String auditSql = "INSERT INTO capability_audit_history " +
                    "(id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                    "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, NOW(), NOW())";

            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                // Log entity deletion
                auditStmt.setInt(1, capabilityId);
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
    private void logAuditHistoryForUpdate(Connection conn, int capabilityId, String tableType,
                                          int entityId, int oldRelationTypeId, int newRelationTypeId, int userId) {
        try {
            String objectName = "Capability X " + capitalizeFirstLetter(tableType);
            String oldRelationTypeName = getRelationTypeName(tableType, oldRelationTypeId);
            String newRelationTypeName = getRelationTypeName(tableType, newRelationTypeId);
            String userName = getUserFullName(userId);

            String auditSql = "INSERT INTO capability_audit_history " +
                    "(id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                    "VALUES (?, ?, ?, ?, 'RelationType', ?, ?, ?, NOW(), NOW())";

            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                auditStmt.setInt(1, capabilityId);
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

    /**
     * Capitalize first letter of a string
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

}

