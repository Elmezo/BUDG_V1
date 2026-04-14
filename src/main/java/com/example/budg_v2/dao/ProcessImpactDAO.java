package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class ProcessImpactDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessImpactDAO.class);
    
    /**
     * Get the next available ID for system relationship table
     */
    private int getNextSystemProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM process_x_system";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get the next available ID for product relationship table
     */
    private int getNextProductProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM product_x_process";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get the next available ID for interface relationship table
     */
    private int getNextInterfaceProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM process_x_interface";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get the next available ID for dataset relationship table
     */
    private int getNextDatasetProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM process_x_dataset";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get the next available ID for attribute relationship table
     */
    private int getNextAttributeProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM process_x_attribute";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get the next available ID for legal relationship table
     */
    @SuppressWarnings("unused")
    private int getNextLegalProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM process_x_legal";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    // ===== SYSTEM RELATIONSHIPS =====
    
    /**
     * Get all system owners as concatenated string
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
     * Get all system owner emails as concatenated string
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
     * Get system relationships by process ID
     */
    public List<Map<String, Object>> getSystemRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.process_id,
                r.system_id,
                r.relationtype,
                rt.primaryname as relationTypeName,
                s.Name as systemName
            FROM process_x_system r
            LEFT JOIN system s ON r.system_id = s.id
            LEFT JOIN process_x_system_relationtype rt ON r.relationtype = rt.id
            WHERE r.process_id = ?
            ORDER BY r.id
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", rs.getInt("process_id"));
                    relationship.put("systemId", rs.getInt("system_id"));
                    relationship.put("relationType", rs.getInt("relationtype"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("systemName", rs.getString("systemName"));
                    
                    // Get owners
                    int systemId = rs.getInt("system_id");
                    if (systemId > 0) {
                        relationship.put("systemOwnerName", getSystemOwnersString(systemId));
                        relationship.put("systemOwnerEmail", getSystemOwnersEmail(systemId));
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get system relation types
     */
    public List<Map<String, Object>> getSystemRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM process_x_system_relationtype
            WHERE deleteddatetime = lastupdatedatetime
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("id"));
                type.put("primaryname", rs.getString("primaryname"));
                type.put("description", rs.getString("description"));
                
                int priority = rs.getInt("priority");
                if (rs.wasNull()) {
                    type.put("priority", null);
                } else {
                    type.put("priority", priority);
                }
                
                type.put("reverseName", rs.getString("reversename"));
                types.add(type);
                //system.out.println("ProcessImpactDAO.getSystemRelationTypes: Found relation type - id: " + 
                 //   type.get("id") + ", primaryname: " + type.get("primaryname"));
            }
            //system.out.println("ProcessImpactDAO.getSystemRelationTypes: Retrieved " + count + " system relation types");
        }
        
        return types;
    }
    
    /**
     * Save system relationships
     */
    public boolean saveSystemRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "system");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object systemIdObj = rel.get("systemId");
                    Object relationTypeObj = rel.get("relationType");
                    if (systemIdObj != null && relationTypeObj != null) {
                        int systemId = ((Number) systemIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (systemId > 0 && relationType > 0) {
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
                    logAuditHistoryForDelete(conn, processId, "system", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "system", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "system", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM process_x_system WHERE process_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO process_x_system 
                    (id, process_id, system_id, relationtype, created_datetime, lastupdatedatetime, last_update_userid) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextSystemProcessId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object systemIdObj = rel.get("systemId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        if (systemIdObj != null && relationTypeObj != null) {
                            int systemId = ((Number) systemIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = systemId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, nextId++);
                            ps.setInt(2, processId);
                            ps.setInt(3, systemId);
                            ps.setInt(4, relationType);
                            ps.setInt(5, userId);
                            ps.executeUpdate();
                        }
                    }
                }
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

    // ===== PRODUCT RELATIONSHIPS =====
    
    /**
     * Get all product owners as concatenated string
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
     * Get all product owner emails as concatenated string
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
     * Get product relationships by process ID
     */
    public List<Map<String, Object>> getProductRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.processid,
                r.productid,
                r.relationtype,
                rt.primaryname as relationTypeName,
                p.primaryname as productName,
                p.refnumber as productRefNumber
            FROM product_x_process r
            LEFT JOIN product p ON r.productid = p.id
            LEFT JOIN product_x_process_relationtype rt ON r.relationtype = rt.id
            WHERE r.processid = ?
            ORDER BY r.id
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", rs.getInt("processid"));
                    relationship.put("productId", rs.getInt("productid"));
                    relationship.put("relationType", rs.getInt("relationtype"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    
                    // Get owners
                    int productId = rs.getInt("productid");
                    if (productId > 0) {
                        relationship.put("productOwnerName", getProductOwnersString(productId));
                        relationship.put("productOwnerEmail", getProductOwnersEmail(productId));
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get product relation types
     */
    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM product_x_process_relationtype
            WHERE deleteddatetime IS NULL
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("id"));
                type.put("primaryName", rs.getString("primaryname"));
                type.put("description", rs.getString("description"));
                type.put("priority", rs.getInt("priority"));
                type.put("reverseName", rs.getString("reversename"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Save product relationships
     */
    public boolean saveProductRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "product");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object productIdObj = rel.get("productId");
                    Object relationTypeObj = rel.get("relationType");
                    if (productIdObj != null && relationTypeObj != null) {
                        int productId = ((Number) productIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (productId > 0 && relationType > 0) {
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
                    logAuditHistoryForDelete(conn, processId, "product", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "product", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "product", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM product_x_process WHERE processid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO product_x_process 
                    (id, processid, productid, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextProductProcessId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object productIdObj = rel.get("productId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        if (productIdObj != null && relationTypeObj != null) {
                            int productId = ((Number) productIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = productId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, nextId++);
                            ps.setInt(2, processId);
                            ps.setInt(3, productId);
                            ps.setInt(4, relationType);
                            ps.setInt(5, userId);
                            ps.executeUpdate();
                        }
                    }
                }
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

    // ===== CLIENT RELATIONSHIPS =====
    
    /**
     * Get all client owners as concatenated string
     */
    public String getClientOwnersString(int clientId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM client_x_objectxpeople cxo
            LEFT JOIN object_x_people oxp ON cxo.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxo.ClientID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
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
     * Get all client owner emails as concatenated string
     */
    public String getClientOwnersEmail(int clientId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM client_x_objectxpeople cxo
            LEFT JOIN object_x_people oxp ON cxo.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxo.ClientID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
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
     * Get client relationships by process ID
     */
    public List<Map<String, Object>> getClientRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.Process_ID,
                r.Client_ID,
                r.RelationType,
                rt.PrimaryName as relationTypeName,
                c.PrimaryName as clientName
            FROM client_x_process r
            LEFT JOIN client c ON r.Client_ID = c.ID
            LEFT JOIN client_x_process_relationtype rt ON r.RelationType = rt.ID
            WHERE r.Process_ID = ?
            ORDER BY r.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("processId", rs.getInt("Process_ID"));
                    relationship.put("clientId", rs.getInt("Client_ID"));
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("clientName", rs.getString("clientName"));
                    
                    // Get owners
                    int clientId = rs.getInt("Client_ID");
                    if (clientId > 0) {
                        relationship.put("clientOwnerName", getClientOwnersString(clientId));
                        relationship.put("clientOwnerEmail", getClientOwnersEmail(clientId));
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get process relationships for a client (reverse lookup)
     */
    public List<Map<String, Object>> getProcessRelationshipsByClientId(int clientId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.Process_ID,
                r.Client_ID,
                r.RelationType,
                p.PrimaryName as processName,
                p.RefNumber as processRefNumber,
                p.lifecycle_status as processLifecycleId,
                pls.PrimaryName as processLifecycleName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM client_x_process r
            LEFT JOIN process p ON r.Process_ID = p.ID
            LEFT JOIN process_lifecycle_status pls ON pls.id = p.lifecycle_status
            LEFT JOIN client_x_process_relationtype rt ON r.RelationType = rt.ID
            WHERE r.Client_ID = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int processId = rs.getInt("Process_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("processId", processId);
                    relationship.put("clientId", rs.getInt("Client_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("processLifecycleId", rs.getObject("processLifecycleId"));
                    relationship.put("processLifecycleName", rs.getString("processLifecycleName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get process owner information
                    try {
                        String ownerName = getProcessOwnersString(processId);
                        relationship.put("processOwnerName", ownerName);
                    } catch (SQLException e) {
                        //system.out.println("ProcessImpactDAO: SQLException getting process owners for processId " + processId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("processOwnerName", "No owner");
                    } catch (Exception e) {
                        //system.out.println("ProcessImpactDAO: Exception getting process owners for processId " + processId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("processOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
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
     * Get client relation types
     */
    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM client_x_process_relationtype
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("ID"));
                type.put("primaryName", rs.getString("PrimaryName"));
                type.put("description", rs.getString("Description"));
                type.put("priority", rs.getInt("Priority"));
                type.put("reverseName", rs.getString("ReverseName"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Save client relationships
     */
    public boolean saveClientRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "client");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object clientIdObj = rel.get("clientId");
                    Object relationTypeObj = rel.get("relationType");
                    if (clientIdObj != null && relationTypeObj != null) {
                        int clientId = ((Number) clientIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (clientId > 0 && relationType > 0) {
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
                    logAuditHistoryForDelete(conn, processId, "client", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "client", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "client", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM client_x_process WHERE Process_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (using AUTO_INCREMENT)
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO client_x_process 
                    (Process_ID, Client_ID, RelationType, LastUpdateDatetime, LastUpdate_UserID) 
                    VALUES (?, ?, ?, NOW(), ?)
                """;
                
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object clientIdObj = rel.get("clientId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        if (clientIdObj != null && relationTypeObj != null) {
                            int clientId = ((Number) clientIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = clientId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, processId);
                            ps.setInt(2, clientId);
                            ps.setInt(3, relationType);
                            ps.setInt(4, userId);
                            ps.executeUpdate();
                        }
                    }
                }
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

    // ===== GLOSSARY RELATIONSHIPS =====
    
    /**
     * Get all glossary owners as concatenated string
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
     * Get all glossary owner emails as concatenated string
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
     * Get glossary relationships by process ID
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.Glossary_ID,
                r.Process_ID,
                r.RelationType,
                rt.PrimaryName as relationTypeName,
                g.Name as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                gt.Name as glossaryType
            FROM glossary_x_process r
            LEFT JOIN glossary g ON r.Glossary_ID = g.ID
            LEFT JOIN glossary_x_process_relationtype rt ON r.RelationType = rt.ID
            LEFT JOIN glossary_type gt ON g.Type = gt.ID
            WHERE r.Process_ID = ?
            ORDER BY r.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("processId", rs.getInt("Process_ID"));
                    relationship.put("glossaryId", rs.getInt("Glossary_ID"));
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
                    relationship.put("glossaryType", rs.getString("glossaryType"));
                    
                    // Get owners
                    int glossaryId = rs.getInt("Glossary_ID");
                    if (glossaryId > 0) {
                        relationship.put("glossaryOwnerName", getGlossaryOwnersString(glossaryId));
                        relationship.put("glossaryOwnerEmail", getGlossaryOwnersEmail(glossaryId));
                    }
                    
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
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM glossary_x_process_relationtype
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("ID"));
                type.put("primaryName", rs.getString("PrimaryName"));
                type.put("description", rs.getString("Description"));
                type.put("priority", rs.getInt("Priority"));
                type.put("reverseName", rs.getString("ReverseName"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Save glossary relationships
     */
    public boolean saveGlossaryRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "glossary");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object glossaryIdObj = rel.get("glossaryId");
                    Object relationTypeObj = rel.get("relationType");
                    if (glossaryIdObj != null && relationTypeObj != null) {
                        int glossaryId = ((Number) glossaryIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (glossaryId > 0 && relationType > 0) {
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
                    logAuditHistoryForDelete(conn, processId, "glossary", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "glossary", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "glossary", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM glossary_x_process WHERE Process_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (using AUTO_INCREMENT)
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO glossary_x_process 
                    (Glossary_ID, Process_ID, RelationType, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, NOW(), ?)
                """;
                
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object glossaryIdObj = rel.get("glossaryId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        if (glossaryIdObj != null && relationTypeObj != null) {
                            int glossaryId = ((Number) glossaryIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = glossaryId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, glossaryId);
                            ps.setInt(2, processId);
                            ps.setInt(3, relationType);
                            ps.setInt(4, userId);
                            ps.executeUpdate();
                        }
                    }
                }
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

    // ===== PROJECT RELATIONSHIPS =====
    
    /**
     * Get all project owners as concatenated string
     */
    public String getProjectOwnersString(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM project_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.project_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, projectId);
            
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
     * Get all project owner emails as concatenated string
     */
    public String getProjectOwnersEmail(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM project_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.project_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, projectId);
            
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
     * Get project relationships by process ID
     */
    public List<Map<String, Object>> getProjectRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.projectid,
                r.process_id,
                r.relationtype,
                r.description,
                rt.primaryname as relationTypeName,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                p.description as projectDescription
            FROM project_x_process r
            LEFT JOIN project p ON r.projectid = p.id
            LEFT JOIN project_x_process_relationtype rt ON r.relationtype = rt.id
            WHERE r.process_id = ?
            ORDER BY r.id
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", rs.getInt("process_id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("relationType", rs.getInt("relationtype"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRef", rs.getString("projectRefNumber"));
                    relationship.put("projectDescription", rs.getString("projectDescription"));
                    
                    // Get owners
                    int projectId = rs.getInt("projectid");
                    if (projectId > 0) {
                        relationship.put("projectOwnerName", getProjectOwnersString(projectId));
                        relationship.put("projectOwnerEmail", getProjectOwnersEmail(projectId));
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relation types
     */
    public List<Map<String, Object>> getProjectRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM project_x_process_relationtype
            WHERE deleteddatetime IS NULL
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("id"));
                type.put("primaryName", rs.getString("primaryname"));
                type.put("description", rs.getString("description"));
                type.put("priority", rs.getInt("priority"));
                type.put("reverseName", rs.getString("reversename"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Save project relationships
     */
    public boolean saveProjectRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "project");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object projectIdObj = rel.get("projectId");
                    Object relationTypeObj = rel.get("relationType");
                    if (projectIdObj != null && relationTypeObj != null) {
                        int projectId = ((Number) projectIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (projectId > 0 && relationType > 0) {
                            newRelationshipsMap.put(projectId, relationType);
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
                    logAuditHistoryForDelete(conn, processId, "project", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "project", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "project", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM project_x_process WHERE process_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO project_x_process 
                    (id, projectid, process_id, relationtype, description, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextProjectProcessId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object projectIdObj = rel.get("projectId");
                        Object relationTypeObj = rel.get("relationType");
                        String description = (String) rel.get("description");
                        
                        if (projectIdObj != null && relationTypeObj != null) {
                            int projectId = ((Number) projectIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = projectId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, nextId++);
                            ps.setInt(2, projectId);
                            ps.setInt(3, processId);
                            ps.setInt(4, relationType);
                            ps.setString(5, description);
                            ps.setInt(6, userId);
                            ps.executeUpdate();
                        }
                    }
                }
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
    
    private int getNextProjectProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM project_x_process";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    // ===== POLICY RELATIONSHIPS =====
    
    /**
     * Get all policy owners as concatenated string
     */
    public String getPolicyOwnersString(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM policy_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.Policy_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
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
     * Get all policy owner emails as concatenated string
     */
    public String getPolicyOwnersEmail(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM policy_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.Policy_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
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
     * Get policy relationships by process ID
     */
    public List<Map<String, Object>> getPolicyRelationshipsByProcessId(int processId) throws SQLException {
        // Simplified query to ensure all rows are returned, even if JOINs fail
        String sql = """
            SELECT 
                r.id,
                r.policy_id,
                r.process_id,
                r.relation_type,
                rt.PrimaryName as relationTypeName,
                p.PrimaryName as policyName,
                p.refNumber as policyRefNumber,
                pt.PrimaryName as policyTypeName
            FROM policy_x_process r
            LEFT JOIN policy p ON r.policy_id = p.ID
            LEFT JOIN policy_x_process_relationtype rt ON r.relation_type = rt.ID
            LEFT JOIN policy_type pt ON p.Policy_Type = pt.ID
            WHERE r.process_id = ?
            ORDER BY r.id ASC
        """;
        
        //system.out.println("ProcessImpactDAO.getPolicyRelationshipsByProcessId: Executing query for processId: " + processId);
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    Map<String, Object> relationship = new HashMap<>();
                    
                    // Use wasNull() to properly handle NULL values
                    int id = rs.getInt("id");
                    relationship.put("id", rs.wasNull() ? null : id);
                    
                    int processIdFromRow = rs.getInt("process_id");
                    relationship.put("processId", rs.wasNull() ? null : processIdFromRow);
                    
                    int policyId = rs.getInt("policy_id");
                    boolean policyIdWasNull = rs.wasNull();
                    relationship.put("policyId", policyIdWasNull ? null : policyId);
                    
                    int relationType = rs.getInt("relation_type");
                    relationship.put("relationType", rs.wasNull() ? null : relationType);
                    
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("policyRefNumber", rs.getString("policyRefNumber"));
                    relationship.put("policyTypeName", rs.getString("policyTypeName"));
                    
                    // Get owners - only if policyId is valid
                    if (!policyIdWasNull && policyId > 0) {
                        try {
                            relationship.put("policyOwnerName", getPolicyOwnersString(policyId));
                            relationship.put("policyOwnerEmail", getPolicyOwnersEmail(policyId));
                        } catch (Exception e) {
                            //system.out.println("ProcessImpactDAO: Error getting policy owners for policyId " + policyId + ": " + e.getMessage());
                            relationship.put("policyOwnerName", "No owner");
                            relationship.put("policyOwnerEmail", null);
                        }
                    } else {
                        relationship.put("policyOwnerName", null);
                        relationship.put("policyOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
                //system.out.println("ProcessImpactDAO.getPolicyRelationshipsByProcessId: Retrieved " + rowCount + " policy relationships for processId: " + processId);
                if (rowCount > 0) {
                    System.out.println("ProcessImpactDAO.getPolicyRelationshipsByProcessId: Relationship IDs: " +
                        relationships.stream().map(r -> String.valueOf(r.get("id"))).collect(java.util.stream.Collectors.joining(", ")));
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relation types
     */
    public List<Map<String, Object>> getPolicyRelationTypes() throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM policy_x_process_relationtype
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("ID"));
                type.put("primaryName", rs.getString("PrimaryName"));
                type.put("description", rs.getString("Description"));
                type.put("priority", rs.getInt("Priority"));
                type.put("reverseName", rs.getString("ReverseName"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Save policy relationships
     */
    public boolean savePolicyRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "policy");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object policyIdObj = rel.get("policyId");
                    Object relationTypeObj = rel.get("relationType");
                    if (policyIdObj != null && relationTypeObj != null) {
                        int policyId = ((Number) policyIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (policyId > 0 && relationType > 0) {
                            newRelationshipsMap.put(policyId, relationType);
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
                    logAuditHistoryForDelete(conn, processId, "policy", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "policy", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "policy", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM policy_x_process WHERE process_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO policy_x_process 
                    (policy_id, process_id, relation_type, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, NOW(), NOW(), ?)
                """;
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object policyIdObj = rel.get("policyId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        if (policyIdObj != null && relationTypeObj != null) {
                            int policyId = ((Number) policyIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            ps.setInt(1, policyId);
                            ps.setInt(2, processId);
                            ps.setInt(3, relationType);
                            ps.setInt(4, userId);
                            ps.executeUpdate();
                        }
                    }
                }
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
    
    @SuppressWarnings("unused")
    private int getNextPolicyProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM policy_x_process";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    // ===== SYSTEM INTERFACE RELATIONSHIPS =====
    
    /**
     * Get all interface owners as concatenated string
     */
    public String getInterfaceOwnersString(int interfaceId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM interface_x_objectxpeople ixop
            LEFT JOIN object_x_people oxp ON ixop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE ixop.InterfaceID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, interfaceId);
            
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
     * Get all interface owner emails as concatenated string
     */
    public String getInterfaceOwnersEmail(int interfaceId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM interface_x_objectxpeople ixop
            LEFT JOIN object_x_people oxp ON ixop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE ixop.InterfaceID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, interfaceId);
            
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
     * Get interface relationships by process ID
     */
    public List<Map<String, Object>> getInterfaceRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.process_id,
                r.interface_id,
                r.relationtype,
                rt.primaryname as relationTypeName,
                i.Name as interfaceName,
                i.Ref_number as interfaceRefNumber
            FROM process_x_interface r
            LEFT JOIN interface i ON r.interface_id = i.id
            LEFT JOIN process_x_interface_relationtype rt ON r.relationtype = rt.id
            WHERE r.process_id = ?
            ORDER BY r.id
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    
                    int id = rs.getInt("id");
                    relationship.put("id", rs.wasNull() ? null : id);
                    
                    int processIdFromRow = rs.getInt("process_id");
                    relationship.put("processId", rs.wasNull() ? null : processIdFromRow);
                    
                    int interfaceId = rs.getInt("interface_id");
                    boolean interfaceIdWasNull = rs.wasNull();
                    relationship.put("interfaceId", interfaceIdWasNull ? null : interfaceId);
                    
                    int relationType = rs.getInt("relationtype");
                    relationship.put("relationType", rs.wasNull() ? null : relationType);
                    
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("interfaceName", rs.getString("interfaceName"));
                    relationship.put("interfaceRefNumber", rs.getString("interfaceRefNumber"));
                    
                    // Get owners only if interfaceId is valid
                    if (!interfaceIdWasNull && interfaceId > 0) {
                        try {
                            relationship.put("interfaceOwnerName", getInterfaceOwnersString(interfaceId));
                            relationship.put("interfaceOwnerEmail", getInterfaceOwnersEmail(interfaceId));
                        } catch (SQLException e) {
                            System.err.println("Error getting interface owners for interfaceId " + interfaceId + ": " + e.getMessage());
                            relationship.put("interfaceOwnerName", null);
                            relationship.put("interfaceOwnerEmail", null);
                        }
                    } else {
                        relationship.put("interfaceOwnerName", null);
                        relationship.put("interfaceOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get interface relation types
     */
    public List<Map<String, Object>> getInterfaceRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM process_x_interface_relationtype
            WHERE deleteddatetime = lastupdatedatetime
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("id"));
                type.put("primaryname", rs.getString("primaryname"));
                type.put("description", rs.getString("description"));
                
                int priority = rs.getInt("priority");
                if (rs.wasNull()) {
                    type.put("priority", null);
                } else {
                    type.put("priority", priority);
                }
                
                type.put("reverseName", rs.getString("reversename"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Get all interfaces for dropdown
     */
    public List<Map<String, Object>> getAllInterfaces() throws SQLException {
        String sql = """
            SELECT 
                id,
                Name,
                Ref_number,
                Description
            FROM interface
            WHERE deleted_datetime IS NULL
            ORDER BY Name
        """;
        
        List<Map<String, Object>> interfaces = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> iface = new HashMap<>();
                iface.put("id", rs.getInt("id"));
                iface.put("ID", rs.getInt("id"));
                iface.put("name", rs.getString("Name"));
                iface.put("Name", rs.getString("Name"));
                iface.put("refNumber", rs.getString("Ref_number"));
                iface.put("description", rs.getString("Description"));
                interfaces.add(iface);
            }
        }
        
        return interfaces;
    }
    
    /**
     * Save interface relationships
     */
    public boolean saveInterfaceRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            //system.out.println("ProcessImpactDAO.saveInterfaceRelationships: START for processId: " + processId + ", userId: " + userId);
            //system.out.println("ProcessImpactDAO.saveInterfaceRelationships: Number of relationships to save: " + (relationships != null ? relationships.size() : 0));
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "interface");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object interfaceIdObj = rel.get("interfaceId");
                    Object relationTypeObj = rel.get("relationType");
                    if (interfaceIdObj != null && relationTypeObj != null) {
                        int interfaceId = ((Number) interfaceIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (interfaceId > 0 && relationType > 0) {
                            newRelationshipsMap.put(interfaceId, relationType);
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
                    logAuditHistoryForDelete(conn, processId, "interface", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "interface", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "interface", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM process_x_interface WHERE process_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO process_x_interface 
                    (id, process_id, interface_id, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextInterfaceProcessId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object interfaceIdObj = rel.get("interfaceId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        //system.out.println("ProcessImpactDAO.saveInterfaceRelationships: Processing relationship - interfaceId: " + interfaceIdObj + ", relationType: " + relationTypeObj);
                        
                        if (interfaceIdObj != null && relationTypeObj != null) {
                            int interfaceId = ((Number) interfaceIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = interfaceId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, nextId++);
                            ps.setInt(2, processId);
                            ps.setInt(3, interfaceId);
                            ps.setInt(4, relationType);
                            ps.setInt(5, userId);
                            ps.executeUpdate();
                        }
                    }
                }
            } else {
                //system.out.println("ProcessImpactDAO.saveInterfaceRelationships: No relationships to insert");
            }
            
            conn.commit();
            //system.out.println("ProcessImpactDAO.saveInterfaceRelationships: COMMITTED successfully");
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

    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    /**
     * Get all legal entity owners as concatenated string
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
     * Get all legal entity owner emails as concatenated string
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
     * Get legal entity relationships by process ID
     */
    public List<Map<String, Object>> getLegalRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.Process_ID,
                r.Legal_ID,
                r.RelationType,
                rt.PrimaryName as relationTypeName,
                l.LongName as legalName,
                l.ShortName as legalShortName
            FROM process_x_legal r
            LEFT JOIN legal l ON r.Legal_ID = l.ID
            LEFT JOIN process_x_legal_relationtype rt ON r.RelationType = rt.ID
            WHERE r.Process_ID = ?
            ORDER BY r.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("processId", rs.getInt("Process_ID"));
                    relationship.put("legalId", rs.getInt("Legal_ID"));
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("legalName", rs.getString("legalName"));
                    relationship.put("legalShortName", rs.getString("legalShortName"));
                    
                    // Get owners
                    int legalId = rs.getInt("Legal_ID");
                    if (legalId > 0) {
                        relationship.put("legalOwnerName", getLegalOwnersString(legalId));
                        relationship.put("legalOwnerEmail", getLegalOwnersEmail(legalId));
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get legal entity relation types
     */
    public List<Map<String, Object>> getLegalRelationTypes() throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM process_x_legal_relationtype
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("ID"));
                type.put("primaryName", rs.getString("PrimaryName"));
                type.put("description", rs.getString("Description"));
                type.put("priority", rs.getInt("Priority"));
                type.put("reverseName", rs.getString("ReverseName"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Save legal entity relationships
     */
    public boolean saveLegalRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "legal");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object legalIdObj = rel.get("legalId");
                    Object relationTypeObj = rel.get("relationType");
                    if (legalIdObj != null && relationTypeObj != null) {
                        int legalId = ((Number) legalIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (legalId > 0 && relationType > 0) {
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
                    logAuditHistoryForDelete(conn, processId, "legal", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "legal", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "legal", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM process_x_legal WHERE Process_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO process_x_legal 
                    (Process_ID, Legal_ID, RelationType, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, NOW(), ?)
                """;
                
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object legalIdObj = rel.get("legalId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        if (legalIdObj != null && relationTypeObj != null) {
                            int legalId = ((Number) legalIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = legalId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, processId);
                            ps.setInt(2, legalId);
                            ps.setInt(3, relationType);
                            ps.setInt(4, userId);
                            ps.executeUpdate();
                        }
                    }
                }
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

    // ===== DATASET RELATIONSHIPS =====
    
    /**
     * Get all datasets list
     */
    public List<Map<String, Object>> getDatasetsList() throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, RefNumber, MasterSource, definition
            FROM dataset
            WHERE status IS NULL OR status != 0
            ORDER BY PrimaryName
        """;
        
        List<Map<String, Object>> datasets = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> dataset = new HashMap<>();
                dataset.put("ID", rs.getInt("ID"));
                dataset.put("PrimaryName", rs.getString("PrimaryName"));
                dataset.put("RefNumber", rs.getString("RefNumber"));
                dataset.put("MasterSource", rs.getInt("MasterSource"));
                dataset.put("definition", rs.getString("definition"));
                datasets.add(dataset);
            }
        }
        
        return datasets;
    }
    
    /**
     * Get all dataset owners as concatenated string
     */
    public String getDatasetOwnersString(int datasetId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM dataset_x_objectxpeople dxop
            LEFT JOIN object_x_people oxp ON dxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE dxop.Dataset_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, datasetId);
            
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
     * Get all dataset owner emails as concatenated string
     */
    public String getDatasetOwnersEmail(int datasetId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM dataset_x_objectxpeople dxop
            LEFT JOIN object_x_people oxp ON dxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE dxop.Dataset_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, datasetId);
            
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
     * Get dataset relationships by process ID
     */
    public List<Map<String, Object>> getDatasetRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.processid,
                r.datasetid,
                r.relation_type,
                rt.primaryname as relationTypeName,
                d.PrimaryName as datasetName,
                d.RefNumber as datasetRefNumber,
                s.Name as systemName,
                s.id as systemId
            FROM process_x_dataset r
            LEFT JOIN dataset d ON r.datasetid = d.ID
            LEFT JOIN process_x_dataset_relationtype rt ON r.relation_type = rt.id
            LEFT JOIN system s ON d.MasterSource = s.id
            WHERE r.processid = ?
            ORDER BY r.id
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", rs.getInt("processid"));
                    relationship.put("datasetId", rs.getInt("datasetid"));
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("systemId", rs.getInt("systemId"));
                    
                    // Get owners
                    int datasetId = rs.getInt("datasetid");
                    if (datasetId > 0) {
                        relationship.put("datasetOwnerName", getDatasetOwnersString(datasetId));
                        relationship.put("datasetOwnerEmail", getDatasetOwnersEmail(datasetId));
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get dataset relation types
     */
    public List<Map<String, Object>> getDatasetRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM process_x_dataset_relationtype
            WHERE deleteddatetime = lastupdatedatetime
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("id"));
                type.put("primaryName", rs.getString("primaryname"));
                type.put("description", rs.getString("description"));
                type.put("priority", rs.getInt("priority"));
                type.put("reverseName", rs.getString("reversename"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Save dataset relationships
     */
    public boolean saveDatasetRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "dataset");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object datasetIdObj = rel.get("datasetId");
                    Object relationTypeObj = rel.get("relationType");
                    if (datasetIdObj != null && relationTypeObj != null) {
                        int datasetId = ((Number) datasetIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (datasetId > 0 && relationType > 0) {
                            newRelationshipsMap.put(datasetId, relationType);
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
                    logAuditHistoryForDelete(conn, processId, "dataset", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "dataset", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "dataset", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM process_x_dataset WHERE processid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO process_x_dataset 
                    (id, processid, datasetid, relation_type, createdatetime, lastupdatedatetime, lastudpate_userid) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextDatasetProcessId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object datasetIdObj = rel.get("datasetId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        if (datasetIdObj != null && relationTypeObj != null) {
                            int datasetId = ((Number) datasetIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = datasetId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, nextId++);
                            ps.setInt(2, processId);
                            ps.setInt(3, datasetId);
                            ps.setInt(4, relationType);
                            ps.setInt(5, userId);
                            ps.executeUpdate();
                        }
                    }
                }
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

    // ===== DATA ATTRIBUTES RELATIONSHIPS =====
    
    /**
     * Get all attributes list
     */
    public List<Map<String, Object>> getAttributesList() throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, RefNumber, Dataset_ID, Definition
            FROM attribute
            ORDER BY PrimaryName
        """;
        
        List<Map<String, Object>> attributes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> attribute = new HashMap<>();
                attribute.put("ID", rs.getInt("ID"));
                attribute.put("PrimaryName", rs.getString("PrimaryName"));
                attribute.put("RefNumber", rs.getString("RefNumber"));
                attribute.put("Dataset_ID", rs.getInt("Dataset_ID"));
                attribute.put("Definition", rs.getString("Definition"));
                attributes.add(attribute);
            }
        }
        
        return attributes;
    }
    
    /**
     * Get all attribute owners as concatenated string
     */
    public String getAttributeOwnersString(int attributeId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM attribute_x_objectxpeople axop
            LEFT JOIN object_x_people oxp ON axop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE axop.AttributeID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, attributeId);
            
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
     * Get all attribute owner emails as concatenated string
     */
    public String getAttributeOwnersEmail(int attributeId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM attribute_x_objectxpeople axop
            LEFT JOIN object_x_people oxp ON axop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE axop.AttributeID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, attributeId);
            
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
     * Get attribute relationships by process ID
     */
    public List<Map<String, Object>> getAttributeRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.processid,
                r.attributeid,
                r.relation_type,
                rt.primaryname as relationTypeName,
                a.PrimaryName as attributeName,
                a.RefNumber as attributeRefNumber,
                d.PrimaryName as datasetName,
                d.ID as datasetId,
                s.Name as systemName,
                s.id as systemId
            FROM process_x_attribute r
            LEFT JOIN attribute a ON r.attributeid = a.ID
            LEFT JOIN dataset d ON a.Dataset_ID = d.ID
            LEFT JOIN system s ON d.MasterSource = s.id
            LEFT JOIN process_x_attribute_relationtype rt ON r.relation_type = rt.id
            WHERE r.processid = ?
            ORDER BY r.id
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", rs.getInt("processid"));
                    relationship.put("attributeId", rs.getInt("attributeid"));
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("attributeName", rs.getString("attributeName"));
                    relationship.put("attributeRefNumber", rs.getString("attributeRefNumber"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("datasetId", rs.getInt("datasetId"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("systemId", rs.getInt("systemId"));
                    
                    // Get owners
                    int attributeId = rs.getInt("attributeid");
                    if (attributeId > 0) {
                        relationship.put("attributeOwnerName", getAttributeOwnersString(attributeId));
                        relationship.put("attributeOwnerEmail", getAttributeOwnersEmail(attributeId));
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get attribute relation types
     */
    public List<Map<String, Object>> getAttributeRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM process_x_attribute_relationtype
            WHERE deleteddatetime = lastupdatedatetime
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("id"));
                type.put("primaryName", rs.getString("primaryname"));
                type.put("description", rs.getString("description"));
                type.put("priority", rs.getInt("priority"));
                type.put("reverseName", rs.getString("reversename"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    /**
     * Save attribute relationships
     */
    // ===== PREDECESSORS (PROCESS X PROCESS) =====
    
    public List<Map<String, Object>> getPredecessorRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.sourceprocess_id as sourceProcessId,
                pxp.targetprocess_id as targetProcessId,
                pxp.relationtype as relationType,
                pxp.rank,
                pxp.annotations,
                p.primaryname as targetProcessName,
                p.refnumber as targetProcessRef,
                pt.primaryname as targetProcessType,
                rt.primaryname as relationTypeName,
                p.lifecycle_status as targetProcessLifecycleId,
                ls_tgt.primaryname as targetProcessLifecycleName
            FROM process_x_process pxp
            LEFT JOIN process p ON pxp.targetprocess_id = p.id
            LEFT JOIN process_type pt ON pt.id = p.type
            LEFT JOIN process_x_process_relationtype rt ON pxp.relationtype = rt.id
            LEFT JOIN process_lifecycle_status ls_tgt ON ls_tgt.id = p.lifecycle_status
            WHERE pxp.sourceprocess_id = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY pxp.rank, p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("sourceProcessId", rs.getInt("sourceProcessId"));
                    relationship.put("targetProcessId", rs.getInt("targetProcessId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("rank", rs.getObject("rank"));
                    relationship.put("annotations", rs.getString("annotations"));
                    relationship.put("targetProcessName", rs.getString("targetProcessName"));
                    relationship.put("targetProcessRef", rs.getString("targetProcessRef"));
                    relationship.put("targetProcessType", rs.getString("targetProcessType"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    // Positions 11–12: MySQL JDBC often lowercases labels so getObject("targetProcessLifecycleId") fails
                    relationship.put("targetProcessLifecycleId", rs.getObject(11, Integer.class));
                    relationship.put("targetProcessLifecycleName", rs.getString(12));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get successor relationships for a process (relations where this process is the target).
     * Returns rows with source process info (the successor).
     */
    public List<Map<String, Object>> getSuccessorRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.sourceprocess_id as sourceProcessId,
                pxp.targetprocess_id as targetProcessId,
                pxp.relationtype as relationType,
                pxp.rank,
                pxp.annotations,
                p.primaryname as sourceProcessName,
                p.refnumber as sourceProcessRef,
                pt.primaryname as sourceProcessType,
                rt.primaryname as relationTypeName,
                p.lifecycle_status as sourceProcessLifecycleId,
                ls_src.primaryname as sourceProcessLifecycleName
            FROM process_x_process pxp
            LEFT JOIN process p ON pxp.sourceprocess_id = p.id
            LEFT JOIN process_type pt ON pt.id = p.type
            LEFT JOIN process_x_process_relationtype rt ON pxp.relationtype = rt.id
            LEFT JOIN process_lifecycle_status ls_src ON ls_src.id = p.lifecycle_status
            WHERE pxp.targetprocess_id = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY pxp.rank, p.primaryname
            """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("sourceProcessId", rs.getInt("sourceProcessId"));
                    relationship.put("targetProcessId", rs.getInt("targetProcessId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("rank", rs.getObject("rank"));
                    relationship.put("annotations", rs.getString("annotations"));
                    relationship.put("sourceProcessName", rs.getString("sourceProcessName"));
                    relationship.put("sourceProcessRef", rs.getString("sourceProcessRef"));
                    relationship.put("sourceProcessType", rs.getString("sourceProcessType"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    // Positions 11–12: MySQL JDBC / findColumn often does not match camelCase aliases
                    relationship.put("sourceProcessLifecycleId", rs.getObject(11, Integer.class));
                    relationship.put("sourceProcessLifecycleName", rs.getString(12));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        String sql = """
            SELECT 
                id,
                primaryname,
                reversename,
                description
            FROM process_x_process_relationtype
            ORDER BY primaryname
        """;
        
        List<Map<String, Object>> types = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("id"));
                type.put("primaryname", rs.getString("primaryname"));
                type.put("reversename", rs.getString("reversename"));
                type.put("description", rs.getString("description"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    public boolean savePredecessorRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships
            String selectSql = "SELECT id FROM process_x_process WHERE sourceprocess_id = ?";
            Set<Integer> existingIds = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, processId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        existingIds.add(rs.getInt("id"));
                    }
                }
            }
            
            // Determine which relationships to delete, update, and insert
            Set<Integer> incomingIds = new HashSet<>();
            for (Map<String, Object> rel : relationships) {
                Object idObj = rel.get("id");
                if (idObj != null) {
                    incomingIds.add(((Number) idObj).intValue());
                }
            }
            
            // Delete relationships that are no longer in the list
            Set<Integer> toDelete = new HashSet<>(existingIds);
            toDelete.removeAll(incomingIds);
            if (!toDelete.isEmpty()) {
                String deleteSql = "DELETE FROM process_x_process WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    for (Integer id : toDelete) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
            }
            
            // Update or insert relationships
            String updateSql = """
                UPDATE process_x_process 
                SET targetprocess_id = ?, relationtype = ?, annotations = ?, lastupdatedateime = NOW(), lastupdate_userid = ?
                WHERE id = ?
            """;
            
            String insertSql = """
                INSERT INTO process_x_process 
                (sourceprocess_id, targetprocess_id, relationtype, annotations, createdatetime, lastupdatedateime, lastupdate_userid)
                VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
            """;
            
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                
                for (Map<String, Object> rel : relationships) {
                    Object idObj = rel.get("id");
                    int targetProcessId = ((Number) rel.get("targetProcessId")).intValue();
                    int relationType = ((Number) rel.get("relationType")).intValue();
                    String annotations = (String) rel.getOrDefault("annotations", "");
                    
                    if (idObj != null && existingIds.contains(((Number) idObj).intValue())) {
                        // Update existing
                        updatePs.setInt(1, targetProcessId);
                        updatePs.setInt(2, relationType);
                        updatePs.setString(3, annotations);
                        updatePs.setInt(4, userId);
                        updatePs.setInt(5, ((Number) idObj).intValue());
                        updatePs.executeUpdate();
                    } else {
                        // Insert new
                        insertPs.setInt(1, processId);
                        insertPs.setInt(2, targetProcessId);
                        insertPs.setInt(3, relationType);
                        insertPs.setString(4, annotations);
                        insertPs.setInt(5, userId);
                        insertPs.executeUpdate();
                    }
                }
            }
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
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
    
    private int getNextProcessXProcessId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM process_x_process";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    public boolean saveAttributeRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, processId, "attribute");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object attributeIdObj = rel.get("attributeId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    // Defensive null checks and type validation
                    if (attributeIdObj != null && relationTypeObj != null) {
                        // Verify objects are Number instances before calling intValue()
                        if (attributeIdObj instanceof Number && relationTypeObj instanceof Number) {
                            try {
                                int attributeId = ((Number) attributeIdObj).intValue();
                                int relationType = ((Number) relationTypeObj).intValue();
                                if (attributeId > 0 && relationType > 0) {
                                    newRelationshipsMap.put(attributeId, relationType);
                                }
                            } catch (Exception e) {
                                // Log warning but continue processing other relationships
                                logger.warn("Error converting attributeId or relationType to int for processId {}: {}", 
                                    processId, e.getMessage());
                            }
                        } else {
                            logger.warn("Invalid data types for attributeId or relationType in relationship data. " +
                                "Expected Number, got attributeId: {}, relationType: {}", 
                                attributeIdObj != null ? attributeIdObj.getClass().getName() : "null",
                                relationTypeObj != null ? relationTypeObj.getClass().getName() : "null");
                        }
                    } else {
                        // Log when required fields are missing
                        logger.warn("Missing attributeId or relationType in relationship data for processId {}. " +
                            "attributeId: {}, relationType: {}", 
                            processId, attributeIdObj != null ? "present" : "null", 
                            relationTypeObj != null ? "present" : "null");
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
                    logAuditHistoryForDelete(conn, processId, "attribute", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, processId, "attribute", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, processId, "attribute", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM process_x_attribute WHERE processid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO process_x_attribute 
                    (id, processid, attributeid, relation_type, createdatetime, lastupdatedatetime, lastudpate_userid) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextAttributeProcessId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object attributeIdObj = rel.get("attributeId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        // Defensive null checks and type validation
                        if (attributeIdObj != null && relationTypeObj != null) {
                            // Verify objects are Number instances before calling intValue()
                            if (attributeIdObj instanceof Number && relationTypeObj instanceof Number) {
                                try {
                                    int attributeId = ((Number) attributeIdObj).intValue();
                                    int relationType = ((Number) relationTypeObj).intValue();
                                    
                                    // Validate positive values
                                    if (attributeId > 0 && relationType > 0) {
                                        // Prevent duplicates
                                        String uniqueKey = attributeId + "_" + relationType;
                                        if (seenRelationships.contains(uniqueKey)) {
                                            logger.debug("Skipping duplicate relationship: attributeId={}, relationType={}", 
                                                attributeId, relationType);
                                            continue;
                                        }
                                        seenRelationships.add(uniqueKey);
                                        
                                        ps.setInt(1, nextId++);
                                        ps.setInt(2, processId);
                                        ps.setInt(3, attributeId);
                                        ps.setInt(4, relationType);
                                        ps.setInt(5, userId);
                                        ps.executeUpdate();
                                    } else {
                                        logger.warn("Invalid attributeId or relationType values (must be > 0) for processId {}. " +
                                            "attributeId: {}, relationType: {}", processId, attributeId, relationType);
                                    }
                                } catch (Exception e) {
                                    // Log error but continue processing other relationships
                                    logger.error("Error processing relationship data for processId {}: {}", 
                                        processId, e.getMessage(), e);
                                }
                            } else {
                                logger.warn("Invalid data types for attributeId or relationType in relationship data for processId {}. " +
                                    "Expected Number, got attributeId: {}, relationType: {}", 
                                    processId,
                                    attributeIdObj != null ? attributeIdObj.getClass().getName() : "null",
                                    relationTypeObj != null ? relationTypeObj.getClass().getName() : "null");
                            }
                        } else {
                            // Log when required fields are missing
                            logger.warn("Missing attributeId or relationType in relationship data for processId {}. " +
                                "attributeId: {}, relationType: {}", 
                                processId, attributeIdObj != null ? "present" : "null", 
                                relationTypeObj != null ? "present" : "null");
                        }
                    }
                }
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
    
    // ===== REVERSE LOOKUP METHODS =====
    
    /**
     * Get process relationships for a system (reverse lookup)
     */
    public List<Map<String, Object>> getProcessRelationshipsBySystemId(int systemId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.process_id,
                r.system_id,
                r.relationtype,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                p.lifecycle_status as processLifecycleId,
                pls.primaryname as processLifecycleName
            FROM process_x_system r
            LEFT JOIN process p ON r.process_id = p.id
            LEFT JOIN process_lifecycle_status pls ON pls.id = p.lifecycle_status
            LEFT JOIN process_x_system_relationtype rt ON r.relationtype = rt.id
            WHERE r.system_id = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, systemId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", rs.getInt("process_id"));
                    relationship.put("systemId", rs.getInt("system_id"));
                    relationship.put("relationType", rs.getInt("relationtype"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("processLifecycleId", rs.getObject("processLifecycleId"));
                    relationship.put("processLifecycleName", rs.getString("processLifecycleName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get process relationships for a glossary (reverse lookup)
     */
    public List<Map<String, Object>> getProcessRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.Process_ID,
                r.Glossary_ID,
                r.RelationType,
                r.Description,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                p.lifecycle_status as processLifecycleId,
                pls.primaryname as processLifecycleName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM glossary_x_process r
            LEFT JOIN process p ON r.Process_ID = p.id
            LEFT JOIN process_lifecycle_status pls ON pls.id = p.lifecycle_status
            LEFT JOIN glossary_x_process_relationtype rt ON r.RelationType = rt.ID
            WHERE r.Glossary_ID = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer processId = rs.getObject("Process_ID") != null ? rs.getInt("Process_ID") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", processId);
                    relationship.put("glossaryId", rs.getInt("Glossary_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("processLifecycleId", rs.getObject("processLifecycleId"));
                    relationship.put("processLifecycleName", rs.getString("processLifecycleName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get process owners
                    if (processId != null) {
                        try {
                            String ownerName = getProcessOwnersString(processId);
                            relationship.put("processOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("ProcessImpactDAO: Error getting process owners: " + e.getMessage());
                            relationship.put("processOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("processOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get process relationships for a product (reverse lookup)
     */
    public List<Map<String, Object>> getProcessRelationshipsByProductId(int productId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.processid,
                r.productid,
                r.relationtype,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                p.lifecycle_status as processLifecycleId,
                pls.primaryname as processLifecycleName
            FROM product_x_process r
            LEFT JOIN process p ON r.processid = p.id
            LEFT JOIN process_lifecycle_status pls ON pls.id = p.lifecycle_status
            LEFT JOIN product_x_process_relationtype rt ON r.relationtype = rt.id
            WHERE r.productid = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer processId = rs.getObject("processid") != null ? rs.getInt("processid") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", processId);
                    relationship.put("productId", rs.getInt("productid"));
                    relationship.put("relationType", rs.getObject("relationtype"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("processLifecycleId", rs.getObject("processLifecycleId"));
                    relationship.put("processLifecycleName", rs.getString("processLifecycleName"));
                    
                    // Get process owners
                    if (processId != null) {
                        try {
                            String ownerName = getProcessOwnersString(processId);
                            relationship.put("processOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("ProcessImpactDAO: Error getting process owners: " + e.getMessage());
                            relationship.put("processOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("processOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get process relationships for a legal entity (reverse lookup)
     */
    public List<Map<String, Object>> getProcessRelationshipsByLegalId(int legalId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.Process_ID,
                r.Legal_ID,
                r.RelationType,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                p.lifecycle_status as processLifecycleId,
                pls.primaryname as processLifecycleName
            FROM process_x_legal r
            LEFT JOIN process p ON r.Process_ID = p.id
            LEFT JOIN process_lifecycle_status pls ON pls.id = p.lifecycle_status
            LEFT JOIN process_x_legal_relationtype rt ON r.RelationType = rt.ID
            WHERE r.Legal_ID = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, legalId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer processId = rs.getObject("Process_ID") != null ? rs.getInt("Process_ID") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("processId", processId);
                    relationship.put("legalId", rs.getInt("Legal_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("processLifecycleId", rs.getObject("processLifecycleId"));
                    relationship.put("processLifecycleName", rs.getString("processLifecycleName"));
                    
                    // Get process owners
                    if (processId != null) {
                        try {
                            String ownerName = getProcessOwnersString(processId);
                            relationship.put("processOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("ProcessImpactDAO: Error getting process owners: " + e.getMessage());
                            relationship.put("processOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("processOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get process relationships for a dataset (reverse lookup)
     */
    public List<Map<String, Object>> getProcessRelationshipsByDatasetId(int datasetId) throws SQLException {
        String sql = """
            SELECT 
                r.id,
                r.processid,
                r.datasetid,
                r.relation_type,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                p.lifecycle_status as processLifecycleId,
                pls.primaryname as processLifecycleName,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM process_x_dataset r
            LEFT JOIN process p ON r.processid = p.id
            LEFT JOIN process_lifecycle_status pls ON pls.id = p.lifecycle_status
            LEFT JOIN process_x_dataset_relationtype rt ON r.relation_type = rt.id
            WHERE r.datasetid = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, datasetId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int processId = rs.getInt("processid");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("processId", processId);
                    relationship.put("datasetId", rs.getInt("datasetid"));
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("processLifecycleId", rs.getObject("processLifecycleId"));
                    relationship.put("processLifecycleName", rs.getString("processLifecycleName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get all process owners for this process (concatenated)
                    try {
                        String ownersName = getProcessOwnersString(processId);
                        relationship.put("processOwnerName", ownersName != null ? ownersName : "No owner");
                    } catch (Exception e) {
                        //system.out.println("ProcessImpactDAO: Error getting process owners: " + e.getMessage());
                        relationship.put("processOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get the next available audit ID for process_audit_history table
     */
    private int getNextAuditId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(auditidpk), 0) + 1 FROM process_audit_history";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get existing relationships as a Map (entityId -> relationType)
     */
    private Map<Integer, Integer> getExistingRelationshipsMap(Connection conn, int processId, String tableType) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = null;
        String entityColumn = null;
        String relationTypeColumn = null;
        
        switch (tableType.toLowerCase()) {
            case "system":
                sql = "SELECT system_id, relationtype FROM process_x_system WHERE process_id = ?";
                entityColumn = "system_id";
                relationTypeColumn = "relationtype";
                break;
            case "product":
                sql = "SELECT productid, relationtype FROM product_x_process WHERE processid = ?";
                entityColumn = "productid";
                relationTypeColumn = "relationtype";
                break;
            case "client":
                sql = "SELECT Client_ID, RelationType FROM client_x_process WHERE Process_ID = ?";
                entityColumn = "Client_ID";
                relationTypeColumn = "RelationType";
                break;
            case "glossary":
                sql = "SELECT Glossary_ID, RelationType FROM glossary_x_process WHERE Process_ID = ?";
                entityColumn = "Glossary_ID";
                relationTypeColumn = "RelationType";
                break;
            case "project":
                sql = "SELECT projectid, relationtype FROM project_x_process WHERE process_id = ?";
                entityColumn = "projectid";
                relationTypeColumn = "relationtype";
                break;
            case "policy":
                sql = "SELECT policy_id, relation_type FROM policy_x_process WHERE process_id = ?";
                entityColumn = "policy_id";
                relationTypeColumn = "relation_type";
                break;
            case "interface":
                sql = "SELECT interface_id, relationtype FROM process_x_interface WHERE process_id = ?";
                entityColumn = "interface_id";
                relationTypeColumn = "relationtype";
                break;
            case "legal":
                sql = "SELECT Legal_ID, RelationType FROM process_x_legal WHERE Process_ID = ?";
                entityColumn = "Legal_ID";
                relationTypeColumn = "RelationType";
                break;
            case "dataset":
                sql = "SELECT datasetid, relation_type FROM process_x_dataset WHERE processid = ?";
                entityColumn = "datasetid";
                relationTypeColumn = "relation_type";
                break;
            case "attribute":
                sql = "SELECT attributeid, relation_type FROM process_x_attribute WHERE processid = ?";
                entityColumn = "attributeid";
                relationTypeColumn = "relation_type";
                break;
            default:
                return existingRelationships;
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int entityId = rs.getInt(entityColumn);
                    int relationType = rs.getInt(relationTypeColumn);
                    if (entityId > 0 && relationType > 0) {
                        existingRelationships.put(entityId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get relation type column name based on table type
     */
    @SuppressWarnings("unused")
    private String getRelationTypeColumnName(String tableType) {
        switch (tableType.toLowerCase()) {
            case "system":
            case "product":
            case "project":
            case "interface":
                return "relationtype";
            case "client":
            case "glossary":
            case "legal":
                return "RelationType";
            case "policy":
            case "dataset":
            case "attribute":
                return "relation_type";
            default:
                return "relationtype";
        }
    }
    
    /**
     * Log audit history for insert operations in relationship tables
     */
    private void logAuditHistoryForInsert(Connection conn, int processId, String tableType, 
                                          int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = getEntityName(tableType, entityId);
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get relation type name
            String relationTypeName = getRelationTypeName(tableType, relationTypeId);
            if (relationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name and field name based on table type
            String objectName = null;
            String entityFieldName = null;
            switch (tableType.toLowerCase()) {
                case "system":
                    objectName = "Process X System";
                    entityFieldName = "System";
                    break;
                case "product":
                    objectName = "Process X Product";
                    entityFieldName = "Product";
                    break;
                case "client":
                    objectName = "Process X Client";
                    entityFieldName = "Client";
                    break;
                case "glossary":
                    objectName = "Process X Glossary";
                    entityFieldName = "Glossary";
                    break;
                case "project":
                    objectName = "Process X Project";
                    entityFieldName = "Project";
                    break;
                case "policy":
                    objectName = "Process X Policy";
                    entityFieldName = "Policy";
                    break;
                case "interface":
                    objectName = "Process X Interface";
                    entityFieldName = "Interface";
                    break;
                case "legal":
                    objectName = "Process X Legal";
                    entityFieldName = "Legal Entity";
                    break;
                case "dataset":
                    objectName = "Process X Dataset";
                    entityFieldName = "Dataset";
                    break;
                case "attribute":
                    objectName = "Process X Attribute";
                    entityFieldName = "Attribute";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int auditId = getNextAuditId(conn);
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO process_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, processId);
                ps.setInt(2, auditId);
                ps.setString(3, objectName);
                ps.setString(4, "link");
                ps.setString(5, "Added");
                ps.setString(6, entityFieldName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, entityName);
                ps.setString(9, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type
                int auditId2 = getNextAuditId(conn);
                ps.setInt(1, processId);
                ps.setInt(2, auditId2);
                ps.setString(3, objectName);
                ps.setString(4, "link");
                ps.setString(5, "Status Change");
                ps.setString(6, "Relationship Type");
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, relationTypeName);
                ps.setString(9, userFullName);
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
    private void logAuditHistoryForDelete(Connection conn, int processId, String tableType, 
                                          int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = getEntityName(tableType, entityId);
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get relation type name
            String relationTypeName = getRelationTypeName(tableType, relationTypeId);
            if (relationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name and field name based on table type
            String objectName = null;
            String entityFieldName = null;
            switch (tableType.toLowerCase()) {
                case "system":
                    objectName = "Process X System";
                    entityFieldName = "System";
                    break;
                case "product":
                    objectName = "Process X Product";
                    entityFieldName = "Product";
                    break;
                case "client":
                    objectName = "Process X Client";
                    entityFieldName = "Client";
                    break;
                case "glossary":
                    objectName = "Process X Glossary";
                    entityFieldName = "Glossary";
                    break;
                case "project":
                    objectName = "Process X Project";
                    entityFieldName = "Project";
                    break;
                case "policy":
                    objectName = "Process X Policy";
                    entityFieldName = "Policy";
                    break;
                case "interface":
                    objectName = "Process X Interface";
                    entityFieldName = "Interface";
                    break;
                case "legal":
                    objectName = "Process X Legal";
                    entityFieldName = "Legal Entity";
                    break;
                case "dataset":
                    objectName = "Process X Dataset";
                    entityFieldName = "Dataset";
                    break;
                case "attribute":
                    objectName = "Process X Attribute";
                    entityFieldName = "Attribute";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int auditId = getNextAuditId(conn);
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO process_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, processId);
                ps.setInt(2, auditId);
                ps.setString(3, objectName);
                ps.setString(4, "link");
                ps.setString(5, "Deleted");
                ps.setString(6, entityFieldName);
                ps.setString(7, entityName);
                ps.setNull(8, Types.VARCHAR);
                ps.setString(9, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type deleted
                int auditId2 = getNextAuditId(conn);
                ps.setInt(1, processId);
                ps.setInt(2, auditId2);
                ps.setString(3, objectName);
                ps.setString(4, "link");
                ps.setString(5, "Deleted");
                ps.setString(6, "Relationship Type");
                ps.setString(7, relationTypeName);
                ps.setNull(8, Types.VARCHAR);
                ps.setString(9, userFullName);
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
    private void logAuditHistoryForUpdate(Connection conn, int processId, String tableType, 
                                         int entityId, int oldRelationTypeId, int newRelationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = getEntityName(tableType, entityId);
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get old and new relation type names
            String oldRelationTypeName = getRelationTypeName(tableType, oldRelationTypeId);
            String newRelationTypeName = getRelationTypeName(tableType, newRelationTypeId);
            if (oldRelationTypeName == null || newRelationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name based on table type
            String objectName = null;
            switch (tableType.toLowerCase()) {
                case "system":
                    objectName = "Process X System";
                    break;
                case "product":
                    objectName = "Process X Product";
                    break;
                case "client":
                    objectName = "Process X Client";
                    break;
                case "glossary":
                    objectName = "Process X Glossary";
                    break;
                case "project":
                    objectName = "Process X Project";
                    break;
                case "policy":
                    objectName = "Process X Policy";
                    break;
                case "interface":
                    objectName = "Process X Interface";
                    break;
                case "legal":
                    objectName = "Process X Legal";
                    break;
                case "dataset":
                    objectName = "Process X Dataset";
                    break;
                case "attribute":
                    objectName = "Process X Attribute";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int auditId = getNextAuditId(conn);
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO process_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, processId);
                ps.setInt(2, auditId);
                ps.setString(3, objectName);
                ps.setString(4, "link");
                ps.setString(5, "Status Change");
                ps.setString(6, "Relationship Type");
                ps.setString(7, oldRelationTypeName);
                ps.setString(8, newRelationTypeName);
                ps.setString(9, userFullName);
                ps.executeUpdate();
            }
            
        } catch (SQLException e) {
            // Don't throw exception - just log error to avoid breaking the main transaction
            System.err.println("Error logging audit history for update: " + e.getMessage());
            e.printStackTrace();
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
            case "system":
                sql = "SELECT Name FROM system WHERE id = ?";
                columnName = "Name";
                break;
            case "product":
                sql = "SELECT primaryname FROM product WHERE id = ?";
                columnName = "primaryname";
                break;
            case "client":
                sql = "SELECT PrimaryName FROM client WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "glossary":
                sql = "SELECT Name FROM glossary WHERE ID = ?";
                columnName = "Name";
                break;
            case "project":
                sql = "SELECT primaryname FROM project WHERE id = ?";
                columnName = "primaryname";
                break;
            case "policy":
                sql = "SELECT PrimaryName FROM policy WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "interface":
                sql = "SELECT Name FROM interface WHERE id = ?";
                columnName = "Name";
                break;
            case "legal":
                sql = "SELECT LongName FROM legal WHERE ID = ?";
                columnName = "LongName";
                break;
            case "dataset":
                sql = "SELECT PrimaryName FROM dataset WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "attribute":
                sql = "SELECT PrimaryName FROM attribute WHERE ID = ?";
                columnName = "PrimaryName";
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
            case "system":
                tableName = "process_x_system_relationtype";
                break;
            case "product":
                tableName = "product_x_process_relationtype";
                break;
            case "client":
                tableName = "client_x_process_relationtype";
                break;
            case "glossary":
                tableName = "glossary_x_process_relationtype";
                break;
            case "project":
                tableName = "project_x_process_relationtype";
                break;
            case "policy":
                tableName = "policy_x_process_relationtype";
                break;
            case "interface":
                tableName = "process_x_interface_relationtype";
                break;
            case "legal":
                tableName = "process_x_legal_relationtype";
                break;
            case "dataset":
                tableName = "process_x_dataset_relationtype";
                break;
            case "attribute":
                tableName = "process_x_attribute_relationtype";
                break;
            default:
                System.err.println("Unknown table type: " + tableType);
                return null;
        }
        
        String sql = "SELECT primaryname FROM " + tableName + " WHERE id = ?";
        // Handle case sensitivity for column names
        if (tableType.equalsIgnoreCase("client") || tableType.equalsIgnoreCase("glossary") || 
            tableType.equalsIgnoreCase("legal") || tableType.equalsIgnoreCase("policy")) {
            sql = "SELECT PrimaryName FROM " + tableName + " WHERE ID = ?";
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting relation type name for " + tableType + ": " + e.getMessage());
        }
        return null;
    }
}

