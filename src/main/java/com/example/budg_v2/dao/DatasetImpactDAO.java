package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class DatasetImpactDAO {
    
    /**
     * Get the next available ID for product relationship table
     */
    private int getNextProductDatasetId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM product_x_dataset";
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
    private int getNextClientDatasetId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM client_x_dataset";
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
    private int getNextLegalDatasetId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM dataset_x_legal";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    // ===== PRODUCT RELATIONSHIPS =====
    
    /**
     * Get product relationships for a dataset with owner information
     */
    public List<Map<String, Object>> getProductRelationshipsByDatasetId(int datasetId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                pxd.ID as id,
                pxd.Dataset_ID as datasetId,
                pxd.Product_ID as productId,
                pxd.Product_Dataset_Relation_Type as relationType,
                p.primaryname as productName,
                p.refnumber as productRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName,
                rt.Description as relationTypeDescription
            FROM product_x_dataset pxd
            LEFT JOIN product p ON pxd.Product_ID = p.id
            LEFT JOIN product_x_dataset_relationtype rt ON pxd.Product_Dataset_Relation_Type = rt.ID
            WHERE pxd.Dataset_ID = ? AND p.deleteddatetime IS NULL
            ORDER BY p.primaryname
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, datasetId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("productId");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("datasetId", rs.getInt("datasetId"));
                    relationship.put("productId", productId);
                    relationship.put("relationType", rs.getInt("relationType"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get all product owners for this product (concatenated)
                    try {
                        String ownersName = getProductOwnersString(productId);
                        String ownersEmail = getProductOwnersEmail(productId);
                        relationship.put("productOwnerName", ownersName);
                        relationship.put("productOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("DatasetImpactDAO: Error getting product owners: " + e.getMessage());
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
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM product_x_dataset_relationtype
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
                relationType.put("priority", rs.getInt("Priority"));
                relationType.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Get product owners string (concatenated names)
     */
    public String getProductOwnersString(int productId) throws SQLException {
        String sql = """
            SELECT CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName
            FROM product_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.product_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.First_Name, pe.Last_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, productId);
            
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
     * Get product owners email (concatenated emails)
     */
    public String getProductOwnersEmail(int productId) throws SQLException {
        String sql = """
            SELECT pe.Email
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
     * Save product relationships
     */
    public boolean saveProductRelationships(int datasetId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, datasetId, "product");
            
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
                    logAuditHistoryForDelete(conn, datasetId, "product", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, datasetId, "product", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, datasetId, "product", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM product_x_dataset WHERE Dataset_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, datasetId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO product_x_dataset 
                    (ID, Product_ID, Dataset_ID, Product_Dataset_Relation_Type, CreateDatetime, LastUpdateDatetime, LastUpdated_UserID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextProductDatasetId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> relationship : relationships) {
                        Integer productId = relationship.get("productId") != null ? 
                            (relationship.get("productId") instanceof Integer ? (Integer) relationship.get("productId") : 
                             Integer.parseInt(String.valueOf(relationship.get("productId")))) : null;
                        Integer relationType = relationship.get("relationType") != null ? 
                            (relationship.get("relationType") instanceof Integer ? (Integer) relationship.get("relationType") : 
                             Integer.parseInt(String.valueOf(relationship.get("relationType")))) : null;
                        
                        if (productId == null || relationType == null) {
                            continue;
                        }
                        
                        // Check for duplicate (productId + relationType combination)
                        String uniqueKey = productId + "_" + relationType;
                        if (seenRelationships.contains(uniqueKey)) {
                            //system.out.println("DatasetImpactDAO: Skipping duplicate product relationship - productId=" + productId + ", relationType=" + relationType);
                            continue;
                        }
                        seenRelationships.add(uniqueKey);
                        
                        ps.setInt(1, nextId++);
                        ps.setInt(2, productId);
                        ps.setInt(3, datasetId);
                        ps.setInt(4, relationType);
                        ps.setInt(5, userId);
                        ps.addBatch();
                    }
                    
                    ps.executeBatch();
                }
            }
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
    
    // ===== CLIENT RELATIONSHIPS =====
    
    /**
     * Get client relationships for a dataset with owner information
     */
    public List<Map<String, Object>> getClientRelationshipsByDatasetId(int datasetId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxd.ID,
                cxd.Dataset_ID,
                cxd.Client_ID,
                cxd.RelationType,
                c.PrimaryName as clientName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName,
                rt.Description as relationTypeDescription
            FROM client_x_dataset cxd
            LEFT JOIN client c ON cxd.Client_ID = c.ID
            LEFT JOIN client_x_dataset_relationtype rt ON cxd.RelationType = rt.ID
            WHERE cxd.Dataset_ID = ?
            ORDER BY c.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, datasetId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int clientId = rs.getInt("Client_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("datasetId", rs.getInt("Dataset_ID"));
                    relationship.put("clientId", clientId);
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("clientName", rs.getString("clientName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get all client owners for this client (concatenated)
                    try {
                        String ownersName = getClientOwnersString(clientId);
                        String ownersEmail = getClientOwnersEmail(clientId);
                        relationship.put("clientOwnerName", ownersName);
                        relationship.put("clientOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("DatasetImpactDAO: Error getting client owners: " + e.getMessage());
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
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM client_x_dataset_relationtype
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
                relationType.put("priority", rs.getInt("Priority"));
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
            SELECT 
                pe.First_Name,
                pe.Last_Name
            FROM client_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.ClientID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.First_Name, pe.Last_Name
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
     * Get client owners email (concatenated emails)
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
     * Save client relationships
     */
    public boolean saveClientRelationships(int datasetId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, datasetId, "client");
            
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
                    logAuditHistoryForDelete(conn, datasetId, "client", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, datasetId, "client", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, datasetId, "client", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM client_x_dataset WHERE Dataset_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, datasetId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO client_x_dataset 
                    (ID, Dataset_ID, Client_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextClientDatasetId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> relationship : relationships) {
                        Integer clientId = relationship.get("clientId") != null ? 
                            (relationship.get("clientId") instanceof Integer ? (Integer) relationship.get("clientId") : 
                             Integer.parseInt(String.valueOf(relationship.get("clientId")))) : null;
                        Integer relationType = relationship.get("relationType") != null ? 
                            (relationship.get("relationType") instanceof Integer ? (Integer) relationship.get("relationType") : 
                             Integer.parseInt(String.valueOf(relationship.get("relationType")))) : null;
                        
                        if (clientId == null || relationType == null) {
                            continue;
                        }
                        
                        // Check for duplicate (clientId + relationType combination)
                        String uniqueKey = clientId + "_" + relationType;
                        if (seenRelationships.contains(uniqueKey)) {
                            //system.out.println("DatasetImpactDAO: Skipping duplicate client relationship - clientId=" + clientId + ", relationType=" + relationType);
                            continue;
                        }
                        seenRelationships.add(uniqueKey);
                        
                        ps.setInt(1, nextId++);
                        ps.setInt(2, datasetId);
                        ps.setInt(3, clientId);
                        ps.setInt(4, relationType);
                        ps.setInt(5, userId);
                        ps.addBatch();
                    }
                    
                    ps.executeBatch();
                }
            }
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
    
    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    /**
     * Get legal relationships for a dataset with owner information
     */
    public List<Map<String, Object>> getLegalRelationshipsByDatasetId(int datasetId) throws SQLException {
        String sql = """
            SELECT 
                dxl.ID as id,
                dxl.Dataset_ID as dataset_id,
                dxl.Legal_ID as legal_id,
                dxl.RelationType as relation_type,
                l.ShortName as legalShortName,
                l.LongName as legalLongName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM dataset_x_legal dxl
            LEFT JOIN legal l ON dxl.Legal_ID = l.ID
            LEFT JOIN dataset_x_legal_relationtype rt ON dxl.RelationType = rt.ID
            WHERE dxl.Dataset_ID = ?
            ORDER BY dxl.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, datasetId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int legalId = rs.getInt("legal_id");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("datasetId", rs.getInt("dataset_id"));
                    relationship.put("legalId", legalId);
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("legalShortName", rs.getString("legalShortName"));
                    relationship.put("legalLongName", rs.getString("legalLongName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get all legal owners for this legal entity (concatenated)
                    try {
                        String ownersName = getLegalOwnersString(legalId);
                        String ownersEmail = getLegalOwnersEmail(legalId);
                        relationship.put("legalOwnerName", ownersName);
                        relationship.put("legalOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("DatasetImpactDAO: Error getting legal owners: " + e.getMessage());
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
     * Get all legal relation types
     */
    public List<Map<String, Object>> getLegalRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM dataset_x_legal_relationtype
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
                relationType.put("priority", rs.getInt("Priority"));
                relationType.put("reversename", rs.getString("ReverseName"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Get legal owners string (concatenated names)
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
     * Get legal owners email (concatenated emails)
     */
    public String getLegalOwnersEmail(int legalId) throws SQLException {
        String sql = """
            SELECT pe.Email
            FROM legal_x_objectxpeople lxoxp
            LEFT JOIN object_x_people oxp ON lxoxp.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE lxoxp.Legal_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
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
     * Save legal relationships
     */
    public boolean saveLegalRelationships(int datasetId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, datasetId, "legal");
            
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
                    logAuditHistoryForDelete(conn, datasetId, "legal", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, datasetId, "legal", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, datasetId, "legal", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM dataset_x_legal WHERE Dataset_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, datasetId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO dataset_x_legal 
                    (ID, Dataset_ID, Legal_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                int nextId = getNextLegalDatasetId(conn);
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> relationship : relationships) {
                        Integer legalId = relationship.get("legalId") != null ? 
                            (relationship.get("legalId") instanceof Integer ? (Integer) relationship.get("legalId") : 
                             Integer.parseInt(String.valueOf(relationship.get("legalId")))) : null;
                        Integer relationType = relationship.get("relationType") != null ? 
                            (relationship.get("relationType") instanceof Integer ? (Integer) relationship.get("relationType") : 
                             Integer.parseInt(String.valueOf(relationship.get("relationType")))) : null;
                        
                        if (legalId == null || relationType == null) {
                            continue;
                        }
                        
                        // Check for duplicate (legalId + relationType combination)
                        String uniqueKey = legalId + "_" + relationType;
                        if (seenRelationships.contains(uniqueKey)) {
                            //system.out.println("DatasetImpactDAO: Skipping duplicate legal relationship - legalId=" + legalId + ", relationType=" + relationType);
                            continue;
                        }
                        seenRelationships.add(uniqueKey);
                        
                        ps.setInt(1, nextId++);
                        ps.setInt(2, datasetId);
                        ps.setInt(3, legalId);
                        ps.setInt(4, relationType);
                        ps.setInt(5, userId);
                        ps.addBatch();
                    }
                    
                    ps.executeBatch();
                }
            }
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    /**
     * Get dataset relationships for a product (reverse lookup)
     */
    public List<Map<String, Object>> getDatasetRelationshipsByProductId(int productId) throws SQLException {
        String sql = """
            SELECT 
                pxd.ID as id,
                pxd.Dataset_ID as datasetId,
                pxd.Product_ID as productId,
                pxd.Product_Dataset_Relation_Type as relationType,
                d.PrimaryName as datasetName,
                d.RefNumber as datasetRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM product_x_dataset pxd
            LEFT JOIN dataset d ON pxd.Dataset_ID = d.ID
            LEFT JOIN product_x_dataset_relationtype rt ON pxd.Product_Dataset_Relation_Type = rt.ID
            WHERE pxd.Product_ID = ? AND (d.DeletedDatetime IS NULL OR d.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY d.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("datasetId", rs.getInt("datasetId"));
                    relationship.put("productId", rs.getInt("productId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get dataset relationships for a client (reverse lookup)
     */
    public List<Map<String, Object>> getDatasetRelationshipsByClientId(int clientId) throws SQLException {
        String sql = """
            SELECT 
                cxd.ID,
                cxd.Dataset_ID,
                cxd.Client_ID,
                cxd.RelationType,
                d.PrimaryName as datasetName,
                d.RefNumber as datasetRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM client_x_dataset cxd
            LEFT JOIN dataset d ON cxd.Dataset_ID = d.ID
            LEFT JOIN client_x_dataset_relationtype rt ON cxd.RelationType = rt.ID
            WHERE cxd.Client_ID = ? AND (d.DeletedDatetime IS NULL OR d.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY d.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int datasetId = rs.getInt("Dataset_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("datasetId", datasetId);
                    relationship.put("clientId", rs.getInt("Client_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get dataset owner information
                    try {
                        String ownerName = getDatasetOwnersString(datasetId);
                        relationship.put("datasetOwnerName", ownerName);
                    } catch (SQLException e) {
                        //system.out.println("DatasetImpactDAO: SQLException getting dataset owners for datasetId " + datasetId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("datasetOwnerName", "No owner");
                    } catch (Exception e) {
                        //system.out.println("DatasetImpactDAO: Exception getting dataset owners for datasetId " + datasetId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("datasetOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get dataset relationships for a legal entity (reverse lookup)
     */
    public List<Map<String, Object>> getDatasetRelationshipsByLegalId(int legalId) throws SQLException {
        String sql = """
            SELECT 
                dxl.id,
                dxl.Dataset_ID as dataset_id,
                dxl.Legal_ID as legal_id,
                dxl.RelationType as relation_type,
                d.PrimaryName as datasetName,
                d.RefNumber as datasetRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM dataset_x_legal dxl
            LEFT JOIN dataset d ON dxl.Dataset_ID = d.ID
            LEFT JOIN dataset_x_legal_relationtype rt ON dxl.RelationType = rt.ID
            WHERE dxl.Legal_ID = ? AND (d.DeletedDatetime IS NULL OR d.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY d.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, legalId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int datasetId = rs.getInt("dataset_id");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("datasetId", datasetId);
                    relationship.put("legalId", rs.getInt("legal_id"));
                    relationship.put("relationType", rs.getObject("relation_type"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get dataset owner information
                    try {
                        String ownerName = getDatasetOwnersString(datasetId);
                        relationship.put("datasetOwnerName", ownerName);
                    } catch (SQLException e) {
                        //system.out.println("DatasetImpactDAO: SQLException getting dataset owners for datasetId " + datasetId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("datasetOwnerName", "No owner");
                    } catch (Exception e) {
                        //system.out.println("DatasetImpactDAO: Exception getting dataset owners for datasetId " + datasetId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("datasetOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
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
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get existing relationships as a Map (entityId -> relationType)
     * Returns map for easier comparison
     */
    private Map<Integer, Integer> getExistingRelationshipsMap(Connection conn, int datasetId, String tableType) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = null;
        String entityColumn = null;
        
        switch (tableType.toLowerCase()) {
            case "product":
                sql = "SELECT Product_ID, Product_Dataset_Relation_Type FROM product_x_dataset WHERE Dataset_ID = ?";
                entityColumn = "Product_ID";
                break;
            case "client":
                sql = "SELECT Client_ID, RelationType FROM client_x_dataset WHERE Dataset_ID = ?";
                entityColumn = "Client_ID";
                break;
            case "legal":
                sql = "SELECT Legal_ID, RelationType FROM dataset_x_legal WHERE Dataset_ID = ?";
                entityColumn = "Legal_ID";
                break;
            default:
                return existingRelationships;
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int entityId = rs.getInt(entityColumn);
                    int relationType = tableType.toLowerCase().equals("product") ? 
                        rs.getInt("Product_Dataset_Relation_Type") : rs.getInt("RelationType");
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
            case "product":
                sql = "SELECT primaryname FROM product WHERE id = ?";
                columnName = "primaryname";
                break;
            case "client":
                sql = "SELECT PrimaryName FROM client WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "legal":
                sql = "SELECT ShortName, LongName FROM legal WHERE ID = ?";
                columnName = "ShortName";
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
                    if (tableType.toLowerCase().equals("legal")) {
                        String shortName = rs.getString("ShortName");
                        String longName = rs.getString("LongName");
                        return shortName != null ? shortName : (longName != null ? longName : null);
                    }
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
            case "product":
                tableName = "product_x_dataset_relationtype";
                break;
            case "client":
                tableName = "client_x_dataset_relationtype";
                break;
            case "legal":
                tableName = "dataset_x_legal_relationtype";
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
     * @param datasetId The dataset ID
     * @param tableType The type of relationship table ("product", "client", "legal")
     * @param entityId The ID of the related entity
     * @param relationTypeId The ID of the relation type
     * @param userId The ID of the user performing the action
     */
    private void logAuditHistoryForInsert(Connection conn, int datasetId, String tableType, 
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
                case "product":
                    objectName = "Dataset X Product";
                    entityFieldName = "Product";
                    break;
                case "client":
                    objectName = "Dataset X Client";
                    entityFieldName = "Client";
                    break;
                case "legal":
                    objectName = "Dataset X Legal";
                    entityFieldName = "Legal";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO dataset_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, datasetId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Added");
                ps.setString(5, entityFieldName);
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, entityName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type
                ps.setInt(1, datasetId);
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
    private void logAuditHistoryForDelete(Connection conn, int datasetId, String tableType, 
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
                case "product":
                    objectName = "Dataset X Product";
                    entityFieldName = "Product";
                    break;
                case "client":
                    objectName = "Dataset X Client";
                    entityFieldName = "Client";
                    break;
                case "legal":
                    objectName = "Dataset X Legal";
                    entityFieldName = "Legal";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO dataset_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, datasetId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Deleted");
                ps.setString(5, entityFieldName);
                ps.setString(6, entityName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type deleted
                ps.setInt(1, datasetId);
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
    private void logAuditHistoryForUpdate(Connection conn, int datasetId, String tableType, 
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
                case "product":
                    objectName = "Dataset X Product";
                    break;
                case "client":
                    objectName = "Dataset X Client";
                    break;
                case "legal":
                    objectName = "Dataset X Legal";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO dataset_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, datasetId);
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

