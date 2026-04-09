package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class ProductImpactDAO {
    
    /**
     * Get the next available ID for legal relationship table
     */
    private int getNextLegalProductId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM product_x_legal";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get the next available ID for client relationship table
     */
    private int getNextClientProductId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM product_x_client";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get the next available ID for business area relationship table
     */
    private int getNextBusinessAreaProductId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM product_x_businessarea";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    /**
     * Get legal entity relationships for a product with owner information
     */
    public List<Map<String, Object>> getLegalRelationshipsByProductId(int productId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                pxl.ID as id,
                pxl.Product_ID as productId,
                pxl.Legal_ID as legalId,
                pxl.RelationType as relationType,
                l.ShortName as legalShortName,
                l.LongName as legalLongName,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM product_x_legal pxl
            LEFT JOIN legal l ON pxl.Legal_ID = l.ID
            LEFT JOIN product_x_legalentity_relationtype rt ON pxl.RelationType = rt.ID
            WHERE pxl.Product_ID = ?
            ORDER BY COALESCE(l.LongName, l.ShortName)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer legalId = rs.getObject("legalId") != null ? rs.getInt("legalId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("productId", rs.getInt("productId"));
                    relationship.put("legalId", legalId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("legalShortName", rs.getString("legalShortName"));
                    relationship.put("legalLongName", rs.getString("legalLongName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get legal entity owners
                    if (legalId != null) {
                        try {
                            String ownersName = getLegalOwnersString(legalId);
                            String ownersEmail = getLegalOwnersEmail(legalId);
                            relationship.put("legalOwnerName", ownersName);
                            relationship.put("legalOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("ProductImpactDAO: Error getting legal owners: " + e.getMessage());
                            relationship.put("legalOwnerName", "No owner");
                            relationship.put("legalOwnerEmail", null);
                        }
                    } else {
                        relationship.put("legalOwnerName", null);
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
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM product_x_legalentity_relationtype
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
     * Get legal entity owners string (concatenated names)
     */
    public String getLegalOwnersString(int legalId) throws SQLException {
        String sql = """
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM legal_x_objectxpeople lxop
            LEFT JOIN object_x_people oxp ON lxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE lxop.Legal_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, legalId);
            
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
     * Get legal entity owners email (concatenated)
     */
    public String getLegalOwnersEmail(int legalId) throws SQLException {
        String sql = """
            SELECT pe.Email as ownerEmail
            FROM legal_x_objectxpeople lxop
            LEFT JOIN object_x_people oxp ON lxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE lxop.Legal_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, legalId);
            
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
     * Save legal entity relationships for a product
     */
    public boolean saveLegalRelationships(int productId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, productId, "legal");
            
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
                    logAuditHistoryForDelete(conn, productId, "legal", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, productId, "legal", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, productId, "legal", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM product_x_legal WHERE Product_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, productId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (prevent duplicates)
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO product_x_legal
                (ID, Product_ID, Legal_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID)
                VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
            """;
            
            int nextId = getNextLegalProductId(conn);
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                    Object legalIdObj = rel.get("legalId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    // Skip if missing required fields
                    if (legalIdObj == null || relationTypeObj == null) {
                        continue;
                    }
                    
                    int legalId = ((Number) legalIdObj).intValue();
                    int relationType = ((Number) relationTypeObj).intValue();
                    
                    // Check for duplicates
                    String key = legalId + "_" + relationType;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);
                    
                    ps.setInt(1, nextId++);
                    ps.setInt(2, productId);
                    ps.setInt(3, legalId);
                    ps.setInt(4, relationType);
                    ps.setInt(5, userId);
                    ps.addBatch();
                    }
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
    
    // ===== CLIENT RELATIONSHIPS =====
    
    /**
     * Get client relationships for a product with owner information
     */
    public List<Map<String, Object>> getClientRelationshipsByProductId(int productId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                pxc.ID as id,
                pxc.Product_ID as productId,
                pxc.Client_ID as clientId,
                pxc.RelationType as relationType,
                c.PrimaryName as clientName,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM product_x_client pxc
            LEFT JOIN client c ON pxc.Client_ID = c.ID
            LEFT JOIN product_x_client_relationtype rt ON pxc.RelationType = rt.ID
            WHERE pxc.Product_ID = ?
            ORDER BY c.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer clientId = rs.getObject("clientId") != null ? rs.getInt("clientId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("productId", rs.getInt("productId"));
                    relationship.put("clientId", clientId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("clientName", rs.getString("clientName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get client owners
                    if (clientId != null) {
                        try {
                            String ownersName = getClientOwnersString(clientId);
                            String ownersEmail = getClientOwnersEmail(clientId);
                            relationship.put("clientOwnerName", ownersName);
                            relationship.put("clientOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("ProductImpactDAO: Error getting client owners: " + e.getMessage());
                            relationship.put("clientOwnerName", "No owner");
                            relationship.put("clientOwnerEmail", null);
                        }
                    } else {
                        relationship.put("clientOwnerName", null);
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
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM product_x_client_relationtype
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
     * Get client owners string (concatenated names)
     */
    public String getClientOwnersString(int clientId) throws SQLException {
        String sql = """
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM client_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.ClientID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
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
     * Get client owners email (concatenated)
     */
    public String getClientOwnersEmail(int clientId) throws SQLException {
        String sql = """
            SELECT pe.Email as ownerEmail
            FROM client_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.ClientID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
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
     * Save client relationships for a product
     */
    public boolean saveClientRelationships(int productId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, productId, "client");
            
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
                    logAuditHistoryForDelete(conn, productId, "client", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, productId, "client", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, productId, "client", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM product_x_client WHERE Product_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, productId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (prevent duplicates)
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO product_x_client
                (ID, Product_ID, Client_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID)
                VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
            """;
            
            int nextId = getNextClientProductId(conn);
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                    Object clientIdObj = rel.get("clientId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    // Skip if missing required fields
                    if (clientIdObj == null || relationTypeObj == null) {
                        continue;
                    }
                    
                    // Handle different Number types safely (Integer, Double, etc.)
                    Integer clientId = null;
                    Integer relationType = null;
                    
                    // Convert clientIdObj to Integer with type safety
                    if (clientIdObj instanceof Integer) {
                        clientId = (Integer) clientIdObj;
                    } else if (clientIdObj instanceof Double) {
                        clientId = ((Double) clientIdObj).intValue();
                    } else if (clientIdObj instanceof Number) {
                        clientId = ((Number) clientIdObj).intValue();
                    } else {
                        // Invalid type - skip this relationship and log error
                        System.err.println("ProductImpactDAO: Invalid clientId type for productId " + productId + 
                            ": expected Number, got " + (clientIdObj != null ? clientIdObj.getClass().getName() : "null"));
                        continue;
                    }
                    
                    // Convert relationTypeObj to Integer with type safety
                    if (relationTypeObj instanceof Integer) {
                        relationType = (Integer) relationTypeObj;
                    } else if (relationTypeObj instanceof Double) {
                        relationType = ((Double) relationTypeObj).intValue();
                    } else if (relationTypeObj instanceof Number) {
                        relationType = ((Number) relationTypeObj).intValue();
                    } else {
                        // Invalid type - skip this relationship and log error
                        System.err.println("ProductImpactDAO: Invalid relationType type for productId " + productId + 
                            ": expected Number, got " + (relationTypeObj != null ? relationTypeObj.getClass().getName() : "null"));
                        continue;
                    }
                    
                    // Validate that both IDs are valid (not null and > 0)
                    if (clientId == null || relationType == null || clientId <= 0 || relationType <= 0) {
                        continue;
                    }
                    
                    // Check for duplicates
                    String key = clientId + "_" + relationType;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);
                    
                    ps.setInt(1, nextId++);
                    ps.setInt(2, productId);
                    ps.setInt(3, clientId);
                    ps.setInt(4, relationType);
                    ps.setInt(5, userId);
                    ps.addBatch();
                    }
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
    
    // ===== BUSINESS AREA RELATIONSHIPS =====
    
    /**
     * Get business area relationships for a product with owner information
     */
    public List<Map<String, Object>> getBusinessAreaRelationshipsByProductId(int productId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                pxba.ID as id,
                pxba.Product_ID as productId,
                pxba.BusinessArea_ID as businessAreaId,
                pxba.RelationType as relationType,
                ba.PrimaryName as businessAreaName,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM product_x_businessarea pxba
            LEFT JOIN business_area ba ON pxba.BusinessArea_ID = ba.ID
            LEFT JOIN product_x_businessarea_relationtype rt ON pxba.RelationType = rt.ID
            WHERE pxba.Product_ID = ?
            ORDER BY ba.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer businessAreaId = rs.getObject("businessAreaId") != null ? rs.getInt("businessAreaId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("productId", rs.getInt("productId"));
                    relationship.put("businessAreaId", businessAreaId);
                    Object relationType = rs.getObject("relationType");
                    relationship.put("relationType", relationType);
                    relationship.put("relationTypeId", relationType); // Also include relationTypeId for compatibility (like project)
                    relationship.put("businessAreaName", rs.getString("businessAreaName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get business area owners
                    if (businessAreaId != null) {
                        try {
                            String ownersName = getBusinessAreaOwnersString(businessAreaId);
                            String ownersEmail = getBusinessAreaOwnersEmail(businessAreaId);
                            relationship.put("businessAreaOwnerName", ownersName);
                            relationship.put("businessAreaOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("ProductImpactDAO: Error getting business area owners: " + e.getMessage());
                            relationship.put("businessAreaOwnerName", "No owner");
                            relationship.put("businessAreaOwnerEmail", null);
                        }
                    } else {
                        relationship.put("businessAreaOwnerName", null);
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
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM product_x_businessarea_relationtype
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
     * Get business area owners string (concatenated names)
     */
    public String getBusinessAreaOwnersString(int businessAreaId) throws SQLException {
        String sql = """
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM businessarea_x_objectxpeople bxop
            LEFT JOIN object_x_people oxp ON bxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE bxop.BusinessAreaID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
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
     * Get business area owners email (concatenated)
     */
    public String getBusinessAreaOwnersEmail(int businessAreaId) throws SQLException {
        String sql = """
            SELECT pe.Email as ownerEmail
            FROM businessarea_x_objectxpeople bxop
            LEFT JOIN object_x_people oxp ON bxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE bxop.BusinessAreaID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
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
     * Save business area relationships for a product
     */
    public boolean saveBusinessAreaRelationships(int productId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        System.out.println("=== ProductImpactDAO.saveBusinessAreaRelationships START ===");
        System.out.println("productId: " + productId + ", userId: " + userId);
        System.out.println("relationships size: " + (relationships != null ? relationships.size() : 0));
        if (relationships != null && !relationships.isEmpty()) {
            System.out.println("First relationship: " + relationships.get(0));
        }
        
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, productId, "businessarea");
            System.out.println("Existing relationships count: " + existingRelationshipsMap.size());
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object businessAreaIdObj = rel.get("businessAreaId");
                    // Support both relationType and relationTypeId for compatibility (like project)
                    Object relationTypeObj = rel.get("relationTypeId");
                    if (relationTypeObj == null) {
                        relationTypeObj = rel.get("relationType");
                    }
                    System.out.println("Processing relationship - businessAreaId: " + businessAreaIdObj + ", relationTypeId: " + rel.get("relationTypeId") + ", relationType: " + rel.get("relationType") + ", final relationTypeObj: " + relationTypeObj);
                    if (businessAreaIdObj != null && relationTypeObj != null) {
                        int businessAreaId = ((Number) businessAreaIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (businessAreaId > 0 && relationType > 0) {
                            newRelationshipsMap.put(businessAreaId, relationType);
                            System.out.println("Added to newRelationshipsMap: businessAreaId=" + businessAreaId + ", relationType=" + relationType);
                        }
                    }
                }
            }
            System.out.println("New relationships map size: " + newRelationshipsMap.size());
            
            // Detect changes: DELETE, UPDATE, INSERT
            // DELETE: entities in existing but not in new
            for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int oldRelationType = entry.getValue();
                Integer newRelationType = newRelationshipsMap.get(entityId);
                
                if (newRelationType == null) {
                    // Entity was deleted
                    logAuditHistoryForDelete(conn, productId, "businessarea", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, productId, "businessarea", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, productId, "businessarea", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM product_x_businessarea WHERE Product_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, productId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (prevent duplicates)
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO product_x_businessarea
                (ID, Product_ID, BusinessArea_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID)
                VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
            """;
            
            int nextId = getNextBusinessAreaProductId(conn);
            int batchCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                    Object businessAreaIdObj = rel.get("businessAreaId");
                    // Support both relationType and relationTypeId for compatibility (like project)
                    Object relationTypeObj = rel.get("relationTypeId");
                    if (relationTypeObj == null) {
                        relationTypeObj = rel.get("relationType");
                    }
                    
                    System.out.println("Insert loop - businessAreaId: " + businessAreaIdObj + ", relationTypeId: " + rel.get("relationTypeId") + ", relationType: " + rel.get("relationType") + ", final relationTypeObj: " + relationTypeObj);
                    
                    // Skip if missing required fields
                    if (businessAreaIdObj == null || relationTypeObj == null) {
                        System.out.println("Skipping - missing required fields");
                        continue;
                    }
                    
                    int businessAreaId = ((Number) businessAreaIdObj).intValue();
                    int relationType = ((Number) relationTypeObj).intValue();
                    
                    // Check for duplicates
                    String key = businessAreaId + "_" + relationType;
                    if (seen.contains(key)) {
                        System.out.println("Skipping duplicate: " + key);
                        continue;
                    }
                    seen.add(key);
                    
                    ps.setInt(1, nextId++);
                    ps.setInt(2, productId);
                    ps.setInt(3, businessAreaId);
                    ps.setInt(4, relationType);
                    ps.setInt(5, userId);
                    ps.addBatch();
                    batchCount++;
                    System.out.println("Added to batch: ID=" + (nextId-1) + ", businessAreaId=" + businessAreaId + ", relationType=" + relationType);
                    }
                }
                System.out.println("Executing batch with " + batchCount + " items");
                int[] results = ps.executeBatch();
                System.out.println("Batch executed, results: " + java.util.Arrays.toString(results));
            }
            
            conn.commit();
            System.out.println("=== ProductImpactDAO.saveBusinessAreaRelationships SUCCESS ===");
            return true;
            
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    /**
     * Get product relationships for a client (reverse lookup)
     */
    public List<Map<String, Object>> getProductRelationshipsByClientId(int clientId) throws SQLException {
        String sql = """
            SELECT 
                pxc.ID,
                pxc.Product_ID,
                pxc.Client_ID,
                pxc.RelationType,
                p.primaryname as productName,
                p.refnumber as productRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM product_x_client pxc
            LEFT JOIN product p ON pxc.Product_ID = p.id
            LEFT JOIN product_x_client_relationtype rt ON pxc.RelationType = rt.ID
            WHERE pxc.Client_ID = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("Product_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("productId", productId);
                    relationship.put("clientId", rs.getInt("Client_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get product owner information
                    try {
                        String ownerName = getProductOwnersString(productId);
                        relationship.put("productOwnerName", ownerName);
                    } catch (SQLException e) {
                        //system.out.println("ProductImpactDAO: SQLException getting product owners for productId " + productId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("productOwnerName", "No owner");
                    } catch (Exception e) {
                        //system.out.println("ProductImpactDAO: Exception getting product owners for productId " + productId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("productOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get product owners string (concatenated names)
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
     * Get product relationships for a legal entity (reverse lookup)
     */
    public List<Map<String, Object>> getProductRelationshipsByLegalId(int legalId) throws SQLException {
        String sql = """
            SELECT 
                pxl.ID as id,
                pxl.Product_ID as productId,
                pxl.Legal_ID as legalId,
                pxl.RelationType as relationType,
                p.primaryname as productName,
                p.refnumber as productRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM product_x_legal pxl
            LEFT JOIN product p ON pxl.Product_ID = p.id
            LEFT JOIN product_x_legalentity_relationtype rt ON pxl.RelationType = rt.ID
            WHERE pxl.Legal_ID = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, legalId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("productId", rs.getInt("productId"));
                    relationship.put("legalId", rs.getInt("legalId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get product relationships for a business area (reverse lookup)
     */
    public List<Map<String, Object>> getProductRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                pxba.ID,
                pxba.Product_ID,
                pxba.BusinessArea_ID,
                pxba.RelationType,
                pxba.Description,
                p.primaryname as productName,
                p.refnumber as productRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM product_x_businessarea pxba
            LEFT JOIN product p ON pxba.Product_ID = p.id
            LEFT JOIN product_x_businessarea_relationtype rt ON pxba.RelationType = rt.ID
            WHERE pxba.BusinessArea_ID = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("productId", rs.getInt("Product_ID"));
                    relationship.put("businessAreaId", rs.getInt("BusinessArea_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get the next available audit ID for product_audit_history table
     */
    private int getNextAuditId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(auditidpk), 0) + 1 FROM product_audit_history";
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
    private Map<Integer, Integer> getExistingRelationshipsMap(Connection conn, int productId, String tableType) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = null;
        String entityColumn = null;
        
        switch (tableType.toLowerCase()) {
            case "legal":
                sql = "SELECT Legal_ID, RelationType FROM product_x_legal WHERE Product_ID = ?";
                entityColumn = "Legal_ID";
                break;
            case "client":
                sql = "SELECT Client_ID, RelationType FROM product_x_client WHERE Product_ID = ?";
                entityColumn = "Client_ID";
                break;
            case "businessarea":
                sql = "SELECT BusinessArea_ID, RelationType FROM product_x_businessarea WHERE Product_ID = ?";
                entityColumn = "BusinessArea_ID";
                break;
            default:
                return existingRelationships;
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
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
            case "legal":
                sql = "SELECT COALESCE(longname, shortname) as name FROM legal WHERE ID = ?";
                columnName = "name";
                break;
            case "client":
                sql = "SELECT PrimaryName FROM client WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "businessarea":
                sql = "SELECT PrimaryName FROM business_area WHERE ID = ?";
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
            case "legal":
                tableName = "product_x_legalentity_relationtype";
                break;
            case "client":
                tableName = "product_x_client_relationtype";
                break;
            case "businessarea":
                tableName = "product_x_businessarea_relationtype";
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
     * Log audit history for insert operations in relationship tables
     * 
     * @param conn The database connection (to use same transaction)
     * @param productId The product ID
     * @param tableType The type of relationship table ("legal", "client", "businessarea")
     * @param entityId The ID of the related entity
     * @param relationTypeId The ID of the relation type
     * @param userId The ID of the user performing the action
     */
    private void logAuditHistoryForInsert(Connection conn, int productId, String tableType, 
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
            
            // Determine object name based on table type
            String objectName = null;
            String entityFieldName = null;
            switch (tableType.toLowerCase()) {
                case "legal":
                    objectName = "Product X Legal";
                    entityFieldName = "Legal";
                    break;
                case "client":
                    objectName = "Product X Client";
                    entityFieldName = "Client";
                    break;
                case "businessarea":
                    objectName = "Product X Business Area";
                    entityFieldName = "Business Area";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int auditId = getNextAuditId(conn);
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO product_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, productId);
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
                ps.setInt(1, productId);
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
    private void logAuditHistoryForDelete(Connection conn, int productId, String tableType, 
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
            
            // Determine object name based on table type
            String objectName = null;
            String entityFieldName = null;
            switch (tableType.toLowerCase()) {
                case "legal":
                    objectName = "Product X Legal";
                    entityFieldName = "Legal";
                    break;
                case "client":
                    objectName = "Product X Client";
                    entityFieldName = "Client";
                    break;
                case "businessarea":
                    objectName = "Product X Business Area";
                    entityFieldName = "Business Area";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int auditId = getNextAuditId(conn);
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO product_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, productId);
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
                ps.setInt(1, productId);
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
    private void logAuditHistoryForUpdate(Connection conn, int productId, String tableType, 
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
                case "legal":
                    objectName = "Product X Legal";
                    break;
                case "client":
                    objectName = "Product X Client";
                    break;
                case "businessarea":
                    objectName = "Product X Business Area";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int nextAuditId = getNextAuditId(conn);
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO product_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, productId);
                ps.setInt(2, nextAuditId);
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
}

