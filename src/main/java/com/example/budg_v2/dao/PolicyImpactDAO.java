package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class PolicyImpactDAO {
    
    /**
     * Get the next available ID for product_x_policy table
     */
    private int getNextProductPolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM product_x_policy";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1; // Default to 1 if no records exist
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
                    owner.put("ownerName", rs.getString("ownerName"));
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", null);
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }

    /**
     * Get all product owners as concatenated string
     */
    public String getProductOwnersString(int productId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name,
                pe.ID as peopleId,
                oxp.id as objectXPeopleId,
                pxop.id as productXObjectXPeopleId
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
                
                if (ownersList.length() > 0) {
                    return ownersList.toString();
                } else {
                    return "No owner";
                }
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
                    owner.put("ownerName", rs.getString("ownerName"));
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", null);
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }

    /**
     * Get all client owners as concatenated string
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
     * Get all client owner emails as concatenated string
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
    
    public boolean validateSchema() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if required tables exist
            String[] requiredTables = {
                "product_x_policy", "product_x_policy_relationtype", "product_x_objectxpeople",
                "client_x_policy", "client_x_policy_relationtype", "client_x_objectxpeople",
                "object_x_people", "people", "product", "client"
            };
            
            for (String table : requiredTables) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + table + " LIMIT 1")) {
                    ps.executeQuery();
                } catch (SQLException e) {
                    System.err.println("PolicyImpactDAO: Table " + table + " not found or not accessible");
                    return false;
                }
            }
            
            return true;
        }
    }
    
    /**
     * Get product relationships for a policy with owner information
     */
    public List<Map<String, Object>> getProductRelationshipsByPolicyId(int policyId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                pxp.id,
                pxp.productid,
                pxp.policyid,
                pxp.relationtype,
                p.primaryname as productName,
                p.refnumber as productRefNumber,
                rt.primaryname as relationTypeName,
                rt.description as relationTypeDescription
            FROM product_x_policy pxp
            LEFT JOIN product p ON pxp.productid = p.id
            LEFT JOIN product_x_policy_relationtype rt ON pxp.relationtype = rt.id
            WHERE pxp.policyid = ? AND p.deleteddatetime IS NULL
            ORDER BY p.primaryname
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, policyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("productid");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("productId", productId);
                    relationship.put("policyId", rs.getInt("policyid"));
                    relationship.put("relationType", rs.getInt("relationtype"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get all product owners for this product (concatenated)
                    try {
                        String ownersName = getProductOwnersString(productId);
                        String ownersEmail = getProductOwnersEmail(productId);
                        relationship.put("productOwnerName", ownersName);
                        relationship.put("productOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("PolicyImpactDAO: Error getting product owners: " + e.getMessage());
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
     * Get client relationships for a policy with owner information
     */
    public List<Map<String, Object>> getClientRelationshipsByPolicyId(int policyId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxp.ID,
                cxp.Policy_ID,
                cxp.Client_ID,
                cxp.RelationType,
                c.PrimaryName as clientName,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM client_x_policy cxp
            LEFT JOIN client c ON cxp.Client_ID = c.ID
            LEFT JOIN client_x_policy_relationtype rt ON cxp.RelationType = rt.ID
            WHERE cxp.Policy_ID = ?
            ORDER BY c.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, policyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int clientId = rs.getInt("Client_ID");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("policyId", rs.getInt("Policy_ID"));
                    relationship.put("clientId", clientId);
                    relationship.put("relationType", rs.getInt("RelationType"));
                    relationship.put("clientName", rs.getString("clientName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get all client owners for this client (concatenated)
                    try {
                        String ownersName = getClientOwnersString(clientId);
                        String ownersEmail = getClientOwnersEmail(clientId);
                        relationship.put("clientOwnerName", ownersName);
                        relationship.put("clientOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("PolicyImpactDAO: Error getting client owners: " + e.getMessage());
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
     * Get all product relation types
     */
    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM product_x_policy_relationtype
            WHERE deleteddatetime IS NULL
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
                relationType.put("priority", rs.getInt("priority"));
                relationType.put("reversename", rs.getString("reversename"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Get all client relation types
     */
    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM client_x_policy_relationtype
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
     * Save product relationships (add/update/delete)
     */
    public boolean saveProductRelationships(int policyId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        //system.out.println("PolicyImpactDAO: saveProductRelationships called with policyId=" + policyId + ", userId=" + userId + ", relationships count=" + relationships.size());
        
        // Validate input
        if (policyId <= 0) {
            throw new IllegalArgumentException("Invalid policy ID");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingProductRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (Map<String, Object> relationship : relationships) {
                        if (relationship.get("productId") != null && relationship.get("relationType") != null) {
                            Integer productId = null;
                            Integer relationType = null;
                            
                            Object productIdObj = relationship.get("productId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (productIdObj instanceof Integer) {
                                productId = (Integer) productIdObj;
                            } else if (productIdObj instanceof Double) {
                                productId = ((Double) productIdObj).intValue();
                            } else if (productIdObj instanceof Number) {
                                productId = ((Number) productIdObj).intValue();
                            }
                            
                            if (relationTypeObj instanceof Integer) {
                                relationType = (Integer) relationTypeObj;
                            } else if (relationTypeObj instanceof Double) {
                                relationType = ((Double) relationTypeObj).intValue();
                            } else if (relationTypeObj instanceof Number) {
                                relationType = ((Number) relationTypeObj).intValue();
                            }
                            
                            if (productId != null && productId > 0 && relationType != null && relationType > 0) {
                                newRelationshipsMap.put(productId, relationType);
                            }
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "product", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "product", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "product", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM product_x_policy WHERE policyid = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, policyId);
                    ps.executeUpdate();
                }
                
                // Insert new relationships
                String insertSql = """
                    INSERT INTO product_x_policy (id, productid, policyid, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid)
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    int validRelationships = 0;
                    int nextId = getNextProductPolicyId(conn);
                    // Track unique combinations to prevent duplicates
                    Set<String> seenRelationships = new HashSet<>();
                    
                    for (Map<String, Object> relationship : (relationships != null ? relationships : Collections.<Map<String, Object>>emptyList())) {
                        //system.out.println("PolicyImpactDAO: Processing relationship: " + relationship);
                        if (relationship.get("productId") != null && relationship.get("relationType") != null) {
                            // Handle both Integer and Double types from JSON parsing
                            Integer productId = null;
                            Integer relationType = null;
                            
                            Object productIdObj = relationship.get("productId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (productIdObj instanceof Integer) {
                                productId = (Integer) productIdObj;
                            } else if (productIdObj instanceof Double) {
                                productId = ((Double) productIdObj).intValue();
                            } else if (productIdObj instanceof Number) {
                                productId = ((Number) productIdObj).intValue();
                            }
                            
                            if (relationTypeObj instanceof Integer) {
                                relationType = (Integer) relationTypeObj;
                            } else if (relationTypeObj instanceof Double) {
                                relationType = ((Double) relationTypeObj).intValue();
                            } else if (relationTypeObj instanceof Number) {
                                relationType = ((Number) relationTypeObj).intValue();
                            }
                            
                            // Validate product and relation type IDs
                            if (productId != null && productId > 0 && relationType != null && relationType > 0) {
                                // Check for duplicate (productId + relationType combination)
                                String uniqueKey = productId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("PolicyImpactDAO: Skipping duplicate relationship - productId=" + productId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                //system.out.println("PolicyImpactDAO: Adding valid relationship - productId=" + productId + ", relationType=" + relationType);
                                ps.setInt(1, nextId++);
                                ps.setInt(2, productId);
                                ps.setInt(3, policyId);
                                ps.setInt(4, relationType);
                                ps.setInt(5, userId);
                                ps.addBatch();
                                validRelationships++;
                            } else {
                                //system.out.println("PolicyImpactDAO: Skipping invalid relationship - productId=" + productId + ", relationType=" + relationType);
                            }
                        } else {
                            //system.out.println("PolicyImpactDAO: Skipping relationship with null values");
                        }
                    }
                    
                    if (validRelationships > 0) {
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
    
    /**
     * Save client relationships (add/update/delete)
     */
    public boolean saveClientRelationships(int policyId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        // Validate input
        if (policyId <= 0) {
            throw new IllegalArgumentException("Invalid policy ID");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingClientRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (Map<String, Object> relationship : relationships) {
                        if (relationship.get("clientId") != null && relationship.get("relationType") != null) {
                            Integer clientId = null;
                            Integer relationType = null;
                            
                            Object clientIdObj = relationship.get("clientId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (clientIdObj instanceof Integer) {
                                clientId = (Integer) clientIdObj;
                            } else if (clientIdObj instanceof Double) {
                                clientId = ((Double) clientIdObj).intValue();
                            } else if (clientIdObj instanceof Number) {
                                clientId = ((Number) clientIdObj).intValue();
                            }
                            
                            if (relationTypeObj instanceof Integer) {
                                relationType = (Integer) relationTypeObj;
                            } else if (relationTypeObj instanceof Double) {
                                relationType = ((Double) relationTypeObj).intValue();
                            } else if (relationTypeObj instanceof Number) {
                                relationType = ((Number) relationTypeObj).intValue();
                            }
                            
                            if (clientId != null && clientId > 0 && relationType != null && relationType > 0) {
                                newRelationshipsMap.put(clientId, relationType);
                            }
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "client", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "client", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "client", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM client_x_policy WHERE Policy_ID = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, policyId);
                    ps.executeUpdate();
                }
                
                // Insert new relationships
                String insertSql = """
                    INSERT INTO client_x_policy (Policy_ID, Client_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID)
                    VALUES (?, ?, ?, NOW(), NOW(), ?)
                """;
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    // Track unique combinations to prevent duplicates
                    Set<String> seenRelationships = new HashSet<>();
                    
                    if (relationships != null) {
                        for (Map<String, Object> relationship : relationships) {
                            if (relationship.get("clientId") != null && relationship.get("relationType") != null) {
                                // Handle both Integer and Double types from JSON parsing
                                Integer clientId = null;
                                Integer relationType = null;
                                
                                Object clientIdObj = relationship.get("clientId");
                                Object relationTypeObj = relationship.get("relationType");
                                
                                if (clientIdObj instanceof Integer) {
                                    clientId = (Integer) clientIdObj;
                                } else if (clientIdObj instanceof Double) {
                                    clientId = ((Double) clientIdObj).intValue();
                                } else if (clientIdObj instanceof Number) {
                                    clientId = ((Number) clientIdObj).intValue();
                                }
                                
                                if (relationTypeObj instanceof Integer) {
                                    relationType = (Integer) relationTypeObj;
                                } else if (relationTypeObj instanceof Double) {
                                    relationType = ((Double) relationTypeObj).intValue();
                                } else if (relationTypeObj instanceof Number) {
                                    relationType = ((Number) relationTypeObj).intValue();
                                }
                                
                                // Validate client and relation type IDs
                                if (clientId != null && clientId > 0 && relationType != null && relationType > 0) {
                                    // Check for duplicate (clientId + relationType combination)
                                    String uniqueKey = clientId + "_" + relationType;
                                    if (seenRelationships.contains(uniqueKey)) {
                                        //system.out.println("PolicyImpactDAO: Skipping duplicate client relationship - clientId=" + clientId + ", relationType=" + relationType);
                                        continue;
                                    }
                                    seenRelationships.add(uniqueKey);
                                    
                                    ps.setInt(1, policyId);
                                    ps.setInt(2, clientId);
                                    ps.setInt(3, relationType);
                                    ps.setInt(4, userId);
                                    ps.addBatch();
                                }
                            }
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
    }

    /**
     * Get process relationships for a policy with owner information
     */
    public List<Map<String, Object>> getProcessRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.policy_id,
                pxp.process_id,
                pxp.relation_type,
                pxp.description,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                rt.PrimaryName as relationTypeName
            FROM policy_x_process pxp
            LEFT JOIN process p ON pxp.process_id = p.id
            LEFT JOIN policy_x_process_relationtype rt ON pxp.relation_type = rt.ID
            WHERE pxp.policy_id = ?
            ORDER BY pxp.id
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> relationships = new ArrayList<>();
                
                while (rs.next()) {
                    int processId = rs.getInt("process_id");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("processId", processId);
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get all process owners for this process (concatenated)
                    try {
                        String ownersName = getProcessOwnersString(processId);
                        String ownersEmail = getProcessOwnersEmail(processId);
                        relationship.put("processOwnerName", ownersName);
                        relationship.put("processOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("PolicyImpactDAO: Error getting process owners: " + e.getMessage());
                        relationship.put("processOwnerName", "No owner");
                        relationship.put("processOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
                
                return relationships;
            }
        }
    }

    /**
     * Get project relationships for a policy with owner information
     */
    public List<Map<String, Object>> getProjectRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxproj.id,
                pxproj.policy_id,
                pxproj.project_id,
                pxproj.relation_type,
                pxproj.description,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.PrimaryName as relationTypeName
            FROM policy_x_project pxproj
            LEFT JOIN project p ON pxproj.project_id = p.id
            LEFT JOIN policy_x_project_relationtype rt ON pxproj.relation_type = rt.ID
            WHERE pxproj.policy_id = ? AND p.deletedatetime IS NULL
            ORDER BY pxproj.id
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> relationships = new ArrayList<>();
                
                while (rs.next()) {
                    int projectId = rs.getInt("project_id");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("projectId", projectId);
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get all project owners for this project (concatenated)
                    try {
                        String ownersName = getProjectOwnersString(projectId);
                        String ownersEmail = getProjectOwnersEmail(projectId);
                        relationship.put("projectOwnerName", ownersName);
                        relationship.put("projectOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("PolicyImpactDAO: Error getting project owners: " + e.getMessage());
                        relationship.put("projectOwnerName", "No owner");
                        relationship.put("projectOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
                
                return relationships;
            }
        }
    }

    /**
     * Get all process relation types
     */
    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        String sql = "SELECT ID as id, PrimaryName as primaryname, Description FROM policy_x_process_relationtype WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            List<Map<String, Object>> relationTypes = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryname", rs.getString("primaryname"));
                relationType.put("description", rs.getString("Description"));
                relationTypes.add(relationType);
            }
            
            return relationTypes;
        }
    }

    /**
     * Get all project relation types
     */
    public List<Map<String, Object>> getProjectRelationTypes() throws SQLException {
        String sql = "SELECT ID as id, PrimaryName as primaryname, Description FROM policy_x_project_relationtype WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            List<Map<String, Object>> relationTypes = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryname", rs.getString("primaryname"));
                relationType.put("description", rs.getString("Description"));
                relationTypes.add(relationType);
            }
            
            return relationTypes;
        }
    }

    /**
     * Save process relationships for a policy
     */
    public boolean saveProcessRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingProcessRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
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
                        
                        if (processId != null && processId > 0 && relationType != null && relationType > 0) {
                            newRelationshipsMap.put(processId, relationType);
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "process", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "process", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "process", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM policy_x_process WHERE policy_id = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, policyId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                String insertSql = "INSERT INTO policy_x_process (id, policy_id, process_id, relation_type, description, lastupdate_userid) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    int nextId = getNextProcessPolicyId(conn);
                    // Track unique combinations to prevent duplicates
                    Set<String> seenRelationships = new HashSet<>();
                    
                    if (relationships != null) {
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if processId is null or empty
                            if (relationship.get("processId") == null || relationship.get("processId").isJsonNull()) {
                                continue;
                            }
                            
                            // Handle both Integer and Double types from JSON parsing
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
                                    //system.out.println("PolicyImpactDAO: Skipping duplicate process relationship - processId=" + processId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                String description = relationship.has("description") ? relationship.get("description").getAsString() : null;
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, policyId);
                                insertStmt.setInt(3, processId);
                                insertStmt.setInt(4, relationType);
                                insertStmt.setString(5, description);
                                insertStmt.setInt(6, userId);
                                
                                insertStmt.addBatch();
                            }
                        }
                    }
                    
                    insertStmt.executeBatch();
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

    /**
     * Save project relationships for a policy
     */
    public boolean saveProjectRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingProjectRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
                        if (relationship.get("projectId") == null || relationship.get("projectId").isJsonNull()) {
                            continue;
                        }
                        
                        Integer projectId = null;
                        Integer relationType = null;
                        
                        Object projectIdObj = relationship.get("projectId");
                        Object relationTypeObj = relationship.get("relationType");
                        
                        if (projectIdObj instanceof com.google.gson.JsonPrimitive) {
                            com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) projectIdObj;
                            projectId = prim.getAsInt();
                        }
                        
                        if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                            com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                            relationType = prim.getAsInt();
                        }
                        
                        if (projectId != null && projectId > 0 && relationType != null && relationType > 0) {
                            newRelationshipsMap.put(projectId, relationType);
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "project", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "project", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "project", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM policy_x_project WHERE policy_id = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, policyId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                String insertSql = "INSERT INTO policy_x_project (id, policy_id, project_id, relation_type, description, lastupdate_userid) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    int nextId = getNextProjectPolicyId(conn);
                    // Track unique combinations to prevent duplicates
                    Set<String> seenRelationships = new HashSet<>();
                    
                    if (relationships != null) {
                        for (com.google.gson.JsonElement element : relationships) {
                            com.google.gson.JsonObject relationship = element.getAsJsonObject();
                            
                            // Skip if projectId is null or empty
                            if (relationship.get("projectId") == null || relationship.get("projectId").isJsonNull()) {
                                continue;
                            }
                            
                            // Handle both Integer and Double types from JSON parsing
                            Integer projectId = null;
                            Integer relationType = null;
                            
                            Object projectIdObj = relationship.get("projectId");
                            Object relationTypeObj = relationship.get("relationType");
                            
                            if (projectIdObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) projectIdObj;
                                projectId = prim.getAsInt();
                            }
                            
                            if (relationTypeObj instanceof com.google.gson.JsonPrimitive) {
                                com.google.gson.JsonPrimitive prim = (com.google.gson.JsonPrimitive) relationTypeObj;
                                relationType = prim.getAsInt();
                            }
                            
                            // Check for duplicate (projectId + relationType combination)
                            if (projectId != null && projectId > 0 && relationType != null && relationType > 0) {
                                String uniqueKey = projectId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("PolicyImpactDAO: Skipping duplicate project relationship - projectId=" + projectId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                String description = relationship.has("description") ? relationship.get("description").getAsString() : null;
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, policyId);
                                insertStmt.setInt(3, projectId);
                                insertStmt.setInt(4, relationType);
                                insertStmt.setString(5, description);
                                insertStmt.setInt(6, userId);
                                
                                insertStmt.addBatch();
                            }
                        }
                    }
                    
                    insertStmt.executeBatch();
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
     * Get all process owners as concatenated string
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
     * Get all process owner emails as concatenated string
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
     * Get project owner by project ID
     */
    public Map<String, Object> getProjectOwnerByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM project p
            LEFT JOIN project_x_objectxpeople pxop ON p.id = pxop.project_id
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE p.id = ? AND (r.primaryname LIKE '%Owner' OR r.primaryname = 'Project Manager' OR r.id IS NULL)
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, projectId);
            
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
            WHERE pxop.project_id = ? AND (r.primaryname LIKE '%Owner' OR r.primaryname = 'Project Manager' OR r.id IS NULL)
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
            WHERE pxop.project_id = ? AND (r.primaryname LIKE '%Owner' OR r.primaryname = 'Project Manager' OR r.id IS NULL)
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
     * Get next process policy ID
     */
    private int getNextProcessPolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM policy_x_process";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    /**
     * Get next project policy ID
     */
    private int getNextProjectPolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM policy_x_project";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    // System DAO methods
    /**
     * Get system relationships for a policy with owner information
     */
    public List<Map<String, Object>> getSystemRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxs.ID as id,
                pxs.Policy_ID as policy_id,
                pxs.System_ID as system_id,
                pxs.Relation_Type as relation_type,
                pxs.Description as description,
                s.Name as systemName,
                s.Long_Name as systemLongName,
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as systemOwnerName,
                pe.Email as systemOwnerEmail,
                rt.PrimaryName as relationTypeName
            FROM policy_x_system pxs
            LEFT JOIN system s ON pxs.System_ID = s.id
            LEFT JOIN system_x_objectxpeople sxoxp ON s.id = sxoxp.SystemID
            LEFT JOIN object_x_people oxp ON sxoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            LEFT JOIN policy_x_system_relationtype rt ON pxs.Relation_Type = rt.ID
            WHERE pxs.Policy_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pxs.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("systemId", rs.getInt("system_id"));
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("systemLongName", rs.getString("systemLongName"));
                    relationship.put("systemOwnerName", rs.getString("systemOwnerName"));
                    relationship.put("systemOwnerEmail", rs.getString("systemOwnerEmail"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }

    /**
     * Get all system relation types
     */
    public List<Map<String, Object>> getSystemRelationTypes() throws SQLException {
        String sql = "SELECT ID, PrimaryName, Description FROM policy_x_system_relationtype ORDER BY PrimaryName";
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }

    /**
     * Save system relationships for a policy
     */
    public boolean saveSystemRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingSystemRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
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
                        
                        if (systemId != null && systemId > 0 && relationType != null && relationType > 0) {
                            newRelationshipsMap.put(systemId, relationType);
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "system", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "system", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "system", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM policy_x_system WHERE Policy_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, policyId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO policy_x_system (ID, Policy_ID, System_ID, Relation_Type, Description, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextSystemPolicyId(conn);
                    // Track unique combinations to prevent duplicates
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
                                    //system.out.println("PolicyImpactDAO: Skipping duplicate system relationship - systemId=" + systemId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, policyId);
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
            WHERE s.id = ?
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, systemId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    owner.put("ownerName", rs.getString("ownerName"));
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", null);
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }

    /**
     * Get next system policy ID
     */
    private int getNextSystemPolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM policy_x_system";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    // Business Area DAO methods
    /**
     * Get business area relationships for a policy with owner information
     */
    public List<Map<String, Object>> getBusinessAreaRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxba.ID as id,
                pxba.Policy_ID as policy_id,
                pxba.BusinessArea_ID as businessarea_id,
                pxba.RelationType as relation_type,
                ba.PrimaryName as businessAreaName,
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as businessAreaOwnerName,
                pe.Email as businessAreaOwnerEmail,
                rt.PrimaryName as relationTypeName
            FROM policy_x_businessarea pxba
            LEFT JOIN business_area ba ON pxba.BusinessArea_ID = ba.ID
            LEFT JOIN businessarea_x_objectxpeople baxoxp ON ba.ID = baxoxp.BusinessAreaID
            LEFT JOIN object_x_people oxp ON baxoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN policy_x_businessarea_relationtype rt ON pxba.RelationType = rt.ID
            WHERE pxba.Policy_ID = ?
            ORDER BY pxba.ID
            """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("businessAreaId", rs.getInt("businessarea_id"));
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("businessAreaName", rs.getString("businessAreaName"));
                    relationship.put("businessAreaOwnerName", rs.getString("businessAreaOwnerName"));
                    relationship.put("businessAreaOwnerEmail", rs.getString("businessAreaOwnerEmail"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
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
        String sql = "SELECT ID, PrimaryName, Description FROM policy_x_businessarea_relationtype ORDER BY PrimaryName";
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }

    /**
     * Save business area relationships for a policy
     */
    public boolean saveBusinessAreaRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingBusinessAreaRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
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
                        
                        if (businessAreaId != null && businessAreaId > 0 && relationType != null && relationType > 0) {
                            newRelationshipsMap.put(businessAreaId, relationType);
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "businessarea", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "businessarea", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "businessarea", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM policy_x_businessarea WHERE Policy_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, policyId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO policy_x_businessarea (ID, Policy_ID, BusinessArea_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextBusinessAreaPolicyId(conn);
                    // Track unique combinations to prevent duplicates
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
                                    //system.out.println("PolicyImpactDAO: Skipping duplicate business area relationship - businessAreaId=" + businessAreaId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, policyId);
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

    /**
     * Get business area owner by business area ID
     */
    public Map<String, Object> getBusinessAreaOwnerByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM business_area ba
            LEFT JOIN businessarea_x_objectxpeople baxoxp ON ba.ID = baxoxp.BusinessArea_ID
            LEFT JOIN object_x_people oxp ON baxoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            WHERE ba.ID = ?
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, businessAreaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    owner.put("ownerName", rs.getString("ownerName"));
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", null);
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }

    /**
     * Get next business area policy ID
     */
    private int getNextBusinessAreaPolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM policy_x_businessarea";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    // Legal Entity DAO methods
    /**
     * Get legal relationships for a policy with owner information
     */
    public List<Map<String, Object>> getLegalRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxl.ID as id,
                pxl.Policy_ID as policy_id,
                pxl.Legal_ID as legal_id,
                pxl.RelationType as relation_type,
                l.ShortName as legalShortName,
                l.LongName as legalLongName,
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as legalOwnerName,
                pe.Email as legalOwnerEmail,
                rt.PrimaryName as relationTypeName
            FROM policy_x_legal pxl
            LEFT JOIN legal l ON pxl.Legal_ID = l.ID
            LEFT JOIN legal_x_objectxpeople lxoxp ON l.ID = lxoxp.Legal_ID
            LEFT JOIN object_x_people oxp ON lxoxp.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            LEFT JOIN policy_x_legal_relationtype rt ON pxl.RelationType = rt.ID
            WHERE pxl.Policy_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pxl.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("legalId", rs.getInt("legal_id"));
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("legalShortName", rs.getString("legalShortName"));
                    relationship.put("legalLongName", rs.getString("legalLongName"));
                    relationship.put("legalOwnerName", rs.getString("legalOwnerName"));
                    relationship.put("legalOwnerEmail", rs.getString("legalOwnerEmail"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
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
        String sql = "SELECT ID, PrimaryName, Description FROM policy_x_legal_relationtype ORDER BY PrimaryName";
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }

    /**
     * Save legal relationships for a policy
     */
    public boolean saveLegalRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingLegalRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (com.google.gson.JsonElement element : relationships) {
                        com.google.gson.JsonObject relationship = element.getAsJsonObject();
                        
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
                        
                        if (legalId != null && legalId > 0 && relationType != null && relationType > 0) {
                            newRelationshipsMap.put(legalId, relationType);
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "legal", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "legal", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "legal", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM policy_x_legal WHERE Policy_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, policyId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    String insertSql = "INSERT INTO policy_x_legal (ID, Policy_ID, Legal_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, NOW(), NOW(), ?)";
                    
                    int nextId = getNextLegalPolicyId(conn);
                    // Track unique combinations to prevent duplicates
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
                                    //system.out.println("PolicyImpactDAO: Skipping duplicate legal relationship - legalId=" + legalId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                insertStmt.setInt(1, nextId++);
                                insertStmt.setInt(2, policyId);
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

    /**
     * Get legal owner by legal ID
     */
    public Map<String, Object> getLegalOwnerByLegalId(int legalId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM legal l
            LEFT JOIN legal_x_objectxpeople lxoxp ON l.ID = lxoxp.Legal_ID
            LEFT JOIN object_x_people oxp ON lxoxp.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            WHERE l.ID = ?
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, legalId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    owner.put("ownerName", rs.getString("ownerName"));
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", null);
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }

    /**
     * Get next legal policy ID
     */
    private int getNextLegalPolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM policy_x_legal";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    // Dataset methods
    /**
     * Get dataset relationships for a policy with owner information
     */
    public List<Map<String, Object>> getDatasetRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxd.ID as id,
                pxd.PolicyID as policy_id,
                pxd.DatasetID as dataset_id,
                pxd.Relation_Type as relation_type,
                pxd.Description as description,
                d.PrimaryName as datasetName,
                d.RefNumber as datasetRefNumber,
                d.MasterSource as systemId,
                s.name as systemName,
                pxdrt.PrimaryName as relationTypeName
            FROM policy_x_dataset pxd
            LEFT JOIN dataset d ON pxd.DatasetID = d.ID
            LEFT JOIN system s ON d.MasterSource = s.id
            LEFT JOIN policy_x_dataset_relationtype pxdrt ON pxd.Relation_Type = pxdrt.ID
            WHERE pxd.PolicyID = ?
            ORDER BY pxd.ID
        """;
        
        //system.out.println("PolicyImpactDAO: Dataset query SQL: " + sql);
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            //system.out.println("PolicyImpactDAO: Executing dataset query for policy ID: " + policyId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int datasetId = rs.getInt("dataset_id");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("datasetId", datasetId);
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                    relationship.put("systemId", rs.getInt("systemId"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get all dataset owners for this dataset (concatenated)
                    try {
                        String ownersName = getDatasetOwnersString(datasetId);
                        String ownersEmail = getDatasetOwnersEmail(datasetId);
                        relationship.put("datasetOwnerName", ownersName);
                        relationship.put("datasetOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("PolicyImpactDAO: Error getting dataset owners: " + e.getMessage());
                        relationship.put("datasetOwnerName", "No owner");
                        relationship.put("datasetOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                    
                    //system.out.println("PolicyImpactDAO: Dataset relationship " + count + ": ID=" + rs.getInt("id") + 
                                //     ", DatasetID=" + datasetId +
                                 //    ", SystemID=" + rs.getInt("systemId") +
                               //      ", DatasetName=" + rs.getString("datasetName"));
                }
                //system.out.println("PolicyImpactDAO: Total dataset relationships found: " + count);
            }
        }
        
        return relationships;
    }

    /**
     * Get all dataset relation types
     */
    public List<Map<String, Object>> getDatasetRelationTypes() throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM policy_x_dataset_relationtype
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryName", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationType.put("priority", rs.getInt("Priority"));
                relationType.put("reverseName", rs.getString("ReverseName"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }

    /**
     * Save dataset relationships for a policy
     */
    public boolean saveDatasetRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingDatasetRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (int i = 0; i < relationships.size(); i++) {
                        com.google.gson.JsonObject rel = relationships.get(i).getAsJsonObject();
                        
                        if (rel.get("datasetId") == null || rel.get("datasetId").isJsonNull()) {
                            continue;
                        }
                        
                        int datasetId = rel.get("datasetId").getAsInt();
                        int relationType = rel.get("relationType").getAsInt();
                        
                        if (datasetId > 0 && relationType > 0) {
                            newRelationshipsMap.put(datasetId, relationType);
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "dataset", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "dataset", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "dataset", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM policy_x_dataset WHERE PolicyID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, policyId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    int nextId = getNextDatasetPolicyId(conn);
                    // Track unique combinations to prevent duplicates
                    Set<String> seenRelationships = new HashSet<>();
                    
                    String insertSql = """
                        INSERT INTO policy_x_dataset (ID, PolicyID, DatasetID, Relation_Type, Description, CreateDatetime, LastUpdateDatetime, LastUdpate_UserID)
                        VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)
                    """;
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (int i = 0; i < relationships.size(); i++) {
                            com.google.gson.JsonObject rel = relationships.get(i).getAsJsonObject();
                            
                            // Skip empty rows
                            if (rel.get("datasetId") == null || rel.get("datasetId").isJsonNull()) {
                                continue;
                            }
                            
                            int datasetId = rel.get("datasetId").getAsInt();
                            int relationType = rel.get("relationType").getAsInt();
                            
                            // Check for duplicate (datasetId + relationType combination)
                            String uniqueKey = datasetId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                //system.out.println("PolicyImpactDAO: Skipping duplicate dataset relationship - datasetId=" + datasetId + ", relationType=" + relationType);
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            String description = rel.has("description") && !rel.get("description").isJsonNull() 
                                ? rel.get("description").getAsString() : null;
                            
                            insertStmt.setInt(1, nextId + i);
                            insertStmt.setInt(2, policyId);
                            insertStmt.setInt(3, datasetId);
                            insertStmt.setInt(4, relationType);
                            insertStmt.setString(5, description);
                            insertStmt.setInt(6, userId);
                            insertStmt.addBatch();
                        }
                        insertStmt.executeBatch();
                    }
                }
                
                conn.commit();
                return true;
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Get dataset owner by dataset ID
     */
    public Map<String, Object> getDatasetOwnerByDatasetId(int datasetId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM dataset_x_objectxpeople dxoxp
            LEFT JOIN object_x_people oxp ON dxoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            WHERE dxoxp.Dataset_ID = ?
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, datasetId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    owner.put("ownerName", rs.getString("ownerName"));
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", null);
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }

    /**
     * Get all dataset owners as concatenated string
     */
    public String getDatasetOwnersString(int datasetId) throws SQLException {
          String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name,
                pe.ID as peopleId,
                oxp.id as objectXPeopleId,
                dxoxp.ID as datasetXObjectXPeopleId
            FROM dataset_x_objectxpeople dxoxp
            LEFT JOIN object_x_people oxp ON dxoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE dxoxp.Dataset_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, datasetId);
            
            StringBuilder ownersList = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String firstName = rs.getString("First_Name");
                    String lastName = rs.getString("Last_Name");
                    rs.getInt("peopleId");
                    rs.getInt("objectXPeopleId");
                    rs.getInt("datasetXObjectXPeopleId");
                    
                    //system.out.println("PolicyImpactDAO: Dataset owner for DatasetID=" + datasetId + 
                                 //    ": FirstName=" + firstName + ", LastName=" + lastName +
                               //      ", PeopleID=" + peopleId + ", ObjectXPeopleID=" + objectXPeopleId +
                                 //    ", DatasetXObjectXPeopleID=" + datasetXObjectXPeopleId);
                    
                    if (firstName != null && lastName != null) {
                        if (ownersList.length() > 0) {
                            ownersList.append(", ");
                        }
                        ownersList.append(firstName).append(" ").append(lastName);
                    }
                }
                
                //system.out.println("PolicyImpactDAO: Total owners found for DatasetID=" + datasetId + ": " + count);
                
                if (ownersList.length() > 0) {
                    //system.out.println("PolicyImpactDAO: Concatenated owners for DatasetID=" + datasetId + ": " + ownersList.toString());
                    return ownersList.toString();
                } else {
                    //system.out.println("PolicyImpactDAO: No owners found for DatasetID=" + datasetId);
                    return "No owner";
                }
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
            FROM dataset_x_objectxpeople dxoxp
            LEFT JOIN object_x_people oxp ON dxoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE dxoxp.Dataset_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
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
     * Get next dataset policy ID
     */
    private int getNextDatasetPolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM policy_x_dataset";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }

    // Attribute methods
    /**
     * Get attribute relationships for a policy with owner information
     */
    public List<Map<String, Object>> getAttributeRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxa.id as id,
                pxa.policyid as policy_id,
                pxa.attributeid as attribute_id,
                pxa.relation_type as relation_type,
                pxa.description as description,
                a.PrimaryName as attributeName,
                a.RefNumber as attributeRefNumber,
                a.Dataset_ID as datasetId,
                d.PrimaryName as datasetName,
                d.MasterSource as systemId,
                s.name as systemName,
                pxart.primaryname as relationTypeName
            FROM policy_x_attribute pxa
            LEFT JOIN attribute a ON pxa.attributeid = a.ID
            LEFT JOIN dataset d ON a.Dataset_ID = d.ID
            LEFT JOIN system s ON d.MasterSource = s.id
            LEFT JOIN policy_x_attribute_relationtype pxart ON pxa.relation_type = pxart.id
            WHERE pxa.policyid = ?
            ORDER BY pxa.id
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int attributeId = rs.getInt("attribute_id");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("attributeId", attributeId);
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("attributeName", rs.getString("attributeName"));
                    relationship.put("attributeRefNumber", rs.getString("attributeRefNumber"));
                    relationship.put("datasetId", rs.getInt("datasetId"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("systemId", rs.getInt("systemId"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get all attribute owners for this attribute (concatenated)
                    try {
                        String ownersName = getAttributeOwnersString(attributeId);
                        String ownersEmail = getAttributeOwnersEmail(attributeId);
                        relationship.put("attributeOwnerName", ownersName);
                        relationship.put("attributeOwnerEmail", ownersEmail);
                    } catch (Exception e) {
                        //system.out.println("PolicyImpactDAO: Error getting attribute owners: " + e.getMessage());
                        relationship.put("attributeOwnerName", "No owner");
                        relationship.put("attributeOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                    
                    //system.out.println("PolicyImpactDAO: Attribute relationship " + count + ": ID=" + rs.getInt("id") + 
                                 //    ", AttributeID=" + attributeId +
                                //     ", SystemID=" + rs.getInt("systemId") +
                                  //   ", DatasetID=" + rs.getInt("datasetId") +
                                //     ", AttributeName=" + rs.getString("attributeName"));
                }
                //system.out.println("PolicyImpactDAO: Total attribute relationships found: " + count);
            }
        }
        
        return relationships;
    }

    /**
     * Get all attribute relation types
     */
    public List<Map<String, Object>> getAttributeRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM policy_x_attribute_relationtype
            WHERE deleteddatetime IS NULL
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryname"));
                relationType.put("description", rs.getString("description"));
                relationType.put("priority", rs.getInt("priority"));
                relationType.put("reverseName", rs.getString("reversename"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }

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
     * Save attribute relationships for a policy
     */
    public boolean saveAttributeRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingAttributeRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                com.google.gson.JsonArray relationships = jsonData.getAsJsonArray("relationships");
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (int i = 0; i < relationships.size(); i++) {
                        com.google.gson.JsonObject rel = relationships.get(i).getAsJsonObject();
                        
                        if (rel.get("attributeId") == null || rel.get("attributeId").isJsonNull()) {
                            continue;
                        }
                        
                        int attributeId = rel.get("attributeId").getAsInt();
                        int relationType = rel.get("relationType").getAsInt();
                        
                        if (attributeId > 0 && relationType > 0) {
                            newRelationshipsMap.put(attributeId, relationType);
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "attribute", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "attribute", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "attribute", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM policy_x_attribute WHERE policyid = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, policyId);
                    deleteStmt.executeUpdate();
                }
                
                // Insert new relationships
                if (relationships != null && relationships.size() > 0) {
                    int nextId = getNextAttributePolicyId(conn);
                    // Track unique combinations to prevent duplicates
                    Set<String> seenRelationships = new HashSet<>();
                    
                    String insertSql = """
                        INSERT INTO policy_x_attribute (id, policyid, attributeid, relation_type, description, createdatetime, lastupdatedatetime, lastudpate_userid)
                        VALUES (?, ?, ?, ?, ?, CURDATE(), CURDATE(), ?)
                    """;
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (int i = 0; i < relationships.size(); i++) {
                            com.google.gson.JsonObject rel = relationships.get(i).getAsJsonObject();
                            
                            // Skip empty rows
                            if (rel.get("attributeId") == null || rel.get("attributeId").isJsonNull()) {
                                continue;
                            }
                            
                            int attributeId = rel.get("attributeId").getAsInt();
                            int relationType = rel.get("relationType").getAsInt();
                            
                            // Check for duplicate (attributeId + relationType combination)
                            String uniqueKey = attributeId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                //system.out.println("PolicyImpactDAO: Skipping duplicate attribute relationship - attributeId=" + attributeId + ", relationType=" + relationType);
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            String description = rel.has("description") && !rel.get("description").isJsonNull() 
                                ? rel.get("description").getAsString() : null;
                            
                            insertStmt.setInt(1, nextId + i);
                            insertStmt.setInt(2, policyId);
                            insertStmt.setInt(3, attributeId);
                            insertStmt.setInt(4, relationType);
                            insertStmt.setString(5, description);
                            insertStmt.setInt(6, userId);
                            insertStmt.addBatch();
                        }
                        insertStmt.executeBatch();
                    }
                }
                
                conn.commit();
                return true;
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Get attribute owner by attribute ID
     */
    public Map<String, Object> getAttributeOwnerByAttributeId(int attributeId) throws SQLException {
        String sql = """
            SELECT 
                CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName,
                pe.Email as ownerEmail
            FROM attribute_x_objectxpeople axoxp
            LEFT JOIN object_x_people oxp ON axoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            WHERE axoxp.AttributeID = ?
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, attributeId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Object> owner = new HashMap<>();
                if (rs.next()) {
                    owner.put("ownerName", rs.getString("ownerName"));
                    owner.put("ownerEmail", rs.getString("ownerEmail"));
                } else {
                    owner.put("ownerName", null);
                    owner.put("ownerEmail", null);
                }
                return owner;
            }
        }
    }

    /**
     * Get all attribute owners as concatenated string
     */
    public String getAttributeOwnersString(int attributeId) throws SQLException {
        String sql = """
            SELECT 
                pe.First_Name,
                pe.Last_Name,
                pe.ID as peopleId,
                oxp.id as objectXPeopleId,
                axoxp.ID as attributeXObjectXPeopleId
            FROM attribute_x_objectxpeople axoxp
            LEFT JOIN object_x_people oxp ON axoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE axoxp.AttributeID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, attributeId);
            
            StringBuilder ownersList = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String firstName = rs.getString("First_Name");
                    String lastName = rs.getString("Last_Name");
                    rs.getInt("peopleId");
                    rs.getInt("objectXPeopleId");
                    rs.getInt("attributeXObjectXPeopleId");
                    
                    //system.out.println("PolicyImpactDAO: Attribute owner for AttributeID=" + attributeId + 
                             //        ": FirstName=" + firstName + ", LastName=" + lastName +
                                //     ", PeopleID=" + peopleId + ", ObjectXPeopleID=" + objectXPeopleId +
                                     //", AttributeXObjectXPeopleID=" + attributeXObjectXPeopleId);
                    
                    if (firstName != null && lastName != null) {
                        if (ownersList.length() > 0) {
                            ownersList.append(", ");
                        }
                        ownersList.append(firstName).append(" ").append(lastName);
                    }
                }
                
                //system.out.println("PolicyImpactDAO: Total owners found for AttributeID=" + attributeId + ": " + count);
                
                if (ownersList.length() > 0) {
                    //system.out.println("PolicyImpactDAO: Concatenated owners for AttributeID=" + attributeId + ": " + ownersList.toString());
                    return ownersList.toString();
                } else {
                    //system.out.println("PolicyImpactDAO: No owners found for AttributeID=" + attributeId);
                    return "No owner";
                }
            }
        }
    }

    /**
     * Get all attribute owner emails as concatenated string
     */
    public String getAttributeOwnersEmail(int attributeId) throws SQLException {
        String sql = """
            SELECT 
                GROUP_CONCAT(pe.Email SEPARATOR ', ') as emails
            FROM attribute_x_objectxpeople axoxp
            LEFT JOIN object_x_people oxp ON axoxp.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE axoxp.AttributeID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, attributeId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("emails");
                } else {
                    return null;
                }
            }
        }
    }

    /**
     * Get next attribute policy ID
     */
    private int getNextAttributePolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM policy_x_attribute";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    // ==================== GLOSSARY METHODS ====================
    
    /**
     * Get glossary relationships by policy ID
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByPolicyId(int policyId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                pxg.ID,
                pxg.PolicyID,
                pxg.GlossaryID,
                pxg.Relation_Type as relationType,
                pxg.Description,
                g.Name as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                g.Type as glossaryTypeId,
                gt.Name as glossaryTypeName,
                rt.PrimaryName as relationTypeName
            FROM policy_x_glossary pxg
            LEFT JOIN glossary g ON pxg.GlossaryID = g.ID
            LEFT JOIN glossary_type gt ON g.Type = gt.ID
            LEFT JOIN policy_x_glossary_relationtype rt ON pxg.Relation_Type = rt.ID
            WHERE pxg.PolicyID = ?
            ORDER BY pxg.ID
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("policyId", rs.getInt("PolicyID"));
                    relationship.put("glossaryId", rs.getInt("GlossaryID"));
                    relationship.put("relationType", rs.getInt("relationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
                    relationship.put("glossaryTypeId", rs.getInt("glossaryTypeId"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get owners
                    int glossaryId = rs.getInt("GlossaryID");
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
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM policy_x_glossary_relationtype
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> rt = new HashMap<>();
                rt.put("ID", rs.getInt("ID"));
                rt.put("PrimaryName", rs.getString("PrimaryName"));
                rt.put("Description", rs.getString("Description"));
                rt.put("Priority", rs.getInt("Priority"));
                rt.put("ReverseName", rs.getString("ReverseName"));
                relationTypes.add(rt);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Save glossary relationships
     */
    public boolean saveGlossaryRelationships(int policyId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        //system.out.println("PolicyImpactDAO: saveGlossaryRelationships called with policyId=" + policyId + ", userId=" + userId + ", relationships count=" + relationships.size());
        
        if (policyId <= 0) {
            throw new IllegalArgumentException("Invalid policy ID");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting
                Map<Integer, Integer> existingRelationshipsMap = getExistingGlossaryRelationshipsMap(conn, policyId);
                
                // Build map of new relationships
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null) {
                    for (Map<String, Object> relationship : relationships) {
                        Integer glossaryId = null;
                        Integer relationType = null;
                        
                        Object glossaryIdObj = relationship.get("glossaryId");
                        Object relationTypeObj = relationship.get("relationType");
                        
                        if (glossaryIdObj instanceof Integer) {
                            glossaryId = (Integer) glossaryIdObj;
                        } else if (glossaryIdObj instanceof Double) {
                            glossaryId = ((Double) glossaryIdObj).intValue();
                        } else if (glossaryIdObj instanceof Number) {
                            glossaryId = ((Number) glossaryIdObj).intValue();
                        }
                        
                        if (relationTypeObj instanceof Integer) {
                            relationType = (Integer) relationTypeObj;
                        } else if (relationTypeObj instanceof Double) {
                            relationType = ((Double) relationTypeObj).intValue();
                        } else if (relationTypeObj instanceof Number) {
                            relationType = ((Number) relationTypeObj).intValue();
                        }
                        
                        if (glossaryId != null && glossaryId > 0 && relationType != null && relationType > 0) {
                            newRelationshipsMap.put(glossaryId, relationType);
                        }
                    }
                }
                
                // Detect changes and log audit history
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, policyId, "glossary", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, policyId, "glossary", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, policyId, "glossary", entityId, newRelationType, userId);
                    }
                }
                
                // Delete existing relationships
                String deleteSql = "DELETE FROM policy_x_glossary WHERE PolicyID = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, policyId);
                    ps.executeUpdate();
                }
                
                // Insert new relationships
                String insertSql = """
                    INSERT INTO policy_x_glossary (ID, PolicyID, GlossaryID, Relation_Type, Description, CreateDatetime, LastUpdateDatetime, LastUdpate_UserID)
                    VALUES (?, ?, ?, ?, NULL, NOW(), NOW(), ?)
                """;
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    int validRelationships = 0;
                    int nextId = getNextGlossaryPolicyId(conn);
                    Set<String> seenRelationships = new HashSet<>();
                    
                    for (Map<String, Object> relationship : (relationships != null ? relationships : Collections.<Map<String, Object>>emptyList())) {
                        Integer glossaryId = null;
                        Integer relationType = null;
                        
                        Object glossaryIdObj = relationship.get("glossaryId");
                        Object relationTypeObj = relationship.get("relationType");
                        
                        if (glossaryIdObj instanceof Integer) {
                            glossaryId = (Integer) glossaryIdObj;
                        } else if (glossaryIdObj instanceof Double) {
                            glossaryId = ((Double) glossaryIdObj).intValue();
                        } else if (glossaryIdObj instanceof Number) {
                            glossaryId = ((Number) glossaryIdObj).intValue();
                        }
                        
                        if (relationTypeObj instanceof Integer) {
                            relationType = (Integer) relationTypeObj;
                        } else if (relationTypeObj instanceof Double) {
                            relationType = ((Double) relationTypeObj).intValue();
                        } else if (relationTypeObj instanceof Number) {
                            relationType = ((Number) relationTypeObj).intValue();
                        }
                        
                        if (glossaryId != null && glossaryId > 0 && relationType != null && relationType > 0) {
                            String uniqueKey = glossaryId + "_" + relationType;
                            if (seenRelationships.contains(uniqueKey)) {
                                //system.out.println("PolicyImpactDAO: Skipping duplicate relationship - glossaryId=" + glossaryId + ", relationType=" + relationType);
                                continue;
                            }
                            seenRelationships.add(uniqueKey);
                            
                            ps.setInt(1, nextId++);
                            ps.setInt(2, policyId);
                            ps.setInt(3, glossaryId);
                            ps.setInt(4, relationType);
                            ps.setInt(5, userId);
                            ps.addBatch();
                            validRelationships++;
                        }
                    }
                    
                    if (validRelationships > 0) {
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
    
    /**
     * Get glossary owners as concatenated string
     */
    public String getGlossaryOwnersString(int glossaryId) throws SQLException {
        StringBuilder owners = new StringBuilder();
        
        String sql = """
            SELECT DISTINCT CONCAT(pe.First_Name, ' ', pe.Last_Name) as ownerName
            FROM glossary_x_objectxpeople gxoxp
            JOIN object_x_people oxp ON gxoxp.Object_x_ipid = oxp.id
            JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE gxoxp.GlossaryID = ?
            AND pe.First_Name IS NOT NULL
            AND pe.Last_Name IS NOT NULL
            AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName != null && !ownerName.trim().isEmpty()) {
                        if (!first) {
                            owners.append(", ");
                        }
                        owners.append(ownerName.trim());
                        first = false;
                    }
                }
            }
        }
        
        return owners.length() > 0 ? owners.toString() : null;
    }
    
    /**
     * Get glossary owners email as concatenated string
     */
    public String getGlossaryOwnersEmail(int glossaryId) throws SQLException {
        StringBuilder emails = new StringBuilder();
        
        String sql = """
            SELECT DISTINCT pe.Email
            FROM glossary_x_objectxpeople gxoxp
            JOIN object_x_people oxp ON gxoxp.Object_x_ipid = oxp.id
            JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE gxoxp.GlossaryID = ?
            AND pe.Email IS NOT NULL
            AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    String email = rs.getString("Email");
                    if (email != null && !email.trim().isEmpty()) {
                        if (!first) {
                            emails.append(", ");
                        }
                        emails.append(email.trim());
                        first = false;
                    }
                }
            }
        }
        
        return emails.length() > 0 ? emails.toString() : null;
    }
    
    /**
     * Get next glossary policy ID
     */
    private int getNextGlossaryPolicyId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM policy_x_glossary";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    /**
     * Get policy relationships for a product (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsByProductId(int productId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.productid,
                pxp.policyid,
                pxp.relationtype,
                p.PrimaryName as policyName,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM product_x_policy pxp
            LEFT JOIN policy p ON pxp.policyid = p.ID
            LEFT JOIN product_x_policy_relationtype rt ON pxp.relationtype = rt.id
            WHERE pxp.productid = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer policyId = rs.getObject("policyid") != null ? rs.getInt("policyid") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("productId", rs.getInt("productid"));
                    relationship.put("policyId", policyId);
                    relationship.put("relationType", rs.getObject("relationtype"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get policy owners
                    if (policyId != null) {
                        try {
                            com.example.budg_v2.dao.ProcessImpactDAO processDAO = new com.example.budg_v2.dao.ProcessImpactDAO();
                            String ownerName = processDAO.getPolicyOwnersString(policyId);
                            relationship.put("policyOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("PolicyImpactDAO: Error getting policy owners: " + e.getMessage());
                            relationship.put("policyOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("policyOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relationships for a client (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsByClientId(int clientId) throws SQLException {
        String sql = """
            SELECT 
                cxp.ID,
                cxp.Policy_ID,
                cxp.Client_ID,
                cxp.RelationType,
                p.PrimaryName as policyName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM client_x_policy cxp
            LEFT JOIN policy p ON cxp.Policy_ID = p.ID
            LEFT JOIN client_x_policy_relationtype rt ON cxp.RelationType = rt.ID
            WHERE cxp.Client_ID = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer policyId = rs.getObject("Policy_ID") != null ? rs.getInt("Policy_ID") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("policyId", policyId);
                    relationship.put("clientId", rs.getInt("Client_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get policy owners
                    if (policyId != null) {
                        try {
                            com.example.budg_v2.dao.ProcessImpactDAO processDAO = new com.example.budg_v2.dao.ProcessImpactDAO();
                            String ownerName = processDAO.getPolicyOwnersString(policyId);
                            relationship.put("policyOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("PolicyImpactDAO: Error getting policy owners: " + e.getMessage());
                            relationship.put("policyOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("policyOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relationships for a process (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.policy_id,
                pxp.process_id,
                pxp.relation_type,
                pxp.description,
                p.PrimaryName as policyName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM policy_x_process pxp
            LEFT JOIN policy p ON pxp.policy_id = p.ID
            LEFT JOIN policy_x_process_relationtype rt ON pxp.relation_type = rt.ID
            WHERE pxp.process_id = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("processId", rs.getInt("process_id"));
                    relationship.put("relationType", rs.getObject("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relationships for a project (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxproj.id,
                pxproj.policy_id,
                pxproj.project_id,
                pxproj.relation_type,
                pxproj.description,
                p.PrimaryName as policyName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM policy_x_project pxproj
            LEFT JOIN policy p ON pxproj.policy_id = p.ID
            LEFT JOIN policy_x_project_relationtype rt ON pxproj.relation_type = rt.ID
            WHERE pxproj.project_id = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("policy_id"));
                    relationship.put("projectId", rs.getInt("project_id"));
                    relationship.put("relationType", rs.getObject("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relationships for a system (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsBySystemId(int systemId) throws SQLException {
        String sql = """
            SELECT 
                pxs.ID,
                pxs.Policy_ID,
                pxs.System_ID,
                pxs.Relation_Type,
                pxs.Description,
                p.PrimaryName as policyName,
                p.refNumber as policyRefNumber,
                ptype.PrimaryName as policyTypeName,
                pst.PrimaryName as policyStatusName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM policy_x_system pxs
            LEFT JOIN policy p ON pxs.Policy_ID = p.ID
            LEFT JOIN policy_type ptype ON p.Policy_Type = ptype.ID
            LEFT JOIN status pst ON pst.ID = p.Status
            LEFT JOIN policy_x_system_relationtype rt ON pxs.Relation_Type = rt.ID
            WHERE pxs.System_ID = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, systemId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer policyId = rs.getObject("Policy_ID") != null ? rs.getInt("Policy_ID") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("policyId", policyId);
                    relationship.put("systemId", rs.getInt("System_ID"));
                    relationship.put("relationType", rs.getObject("Relation_Type"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("policyRefNumber", rs.getString("policyRefNumber"));
                    relationship.put("policyTypeName", rs.getString("policyTypeName"));
                    relationship.put("policyStatusName", rs.getString("policyStatusName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get policy owners
                    if (policyId != null) {
                        try {
                            com.example.budg_v2.dao.ProcessImpactDAO processDAO = new com.example.budg_v2.dao.ProcessImpactDAO();
                            String ownerName = processDAO.getPolicyOwnersString(policyId);
                            relationship.put("policyOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("PolicyImpactDAO: Error getting policy owners: " + e.getMessage());
                            relationship.put("policyOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("policyOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relationships for a business area (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                pxba.ID,
                pxba.Policy_ID,
                pxba.BusinessArea_ID,
                pxba.RelationType,
                p.PrimaryName as policyName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM policy_x_businessarea pxba
            LEFT JOIN policy p ON pxba.Policy_ID = p.ID
            LEFT JOIN policy_x_businessarea_relationtype rt ON pxba.RelationType = rt.ID
            WHERE pxba.BusinessArea_ID = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer policyId = rs.getObject("Policy_ID") != null ? rs.getInt("Policy_ID") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("policyId", policyId);
                    relationship.put("businessAreaId", rs.getInt("BusinessArea_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get policy owners
                    if (policyId != null) {
                        try {
                            com.example.budg_v2.dao.ProcessImpactDAO processDAO = new com.example.budg_v2.dao.ProcessImpactDAO();
                            String ownerName = processDAO.getPolicyOwnersString(policyId);
                            relationship.put("policyOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("PolicyImpactDAO: Error getting policy owners: " + e.getMessage());
                            relationship.put("policyOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("policyOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relationships for a legal entity (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsByLegalId(int legalId) throws SQLException {
        String sql = """
            SELECT 
                pxl.ID,
                pxl.Policy_ID,
                pxl.Legal_ID,
                pxl.RelationType,
                p.PrimaryName as policyName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM policy_x_legal pxl
            LEFT JOIN policy p ON pxl.Policy_ID = p.ID
            LEFT JOIN policy_x_legal_relationtype rt ON pxl.RelationType = rt.ID
            WHERE pxl.Legal_ID = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, legalId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer policyId = rs.getObject("Policy_ID") != null ? rs.getInt("Policy_ID") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("policyId", policyId);
                    relationship.put("legalId", rs.getInt("Legal_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get policy owners
                    if (policyId != null) {
                        try {
                            com.example.budg_v2.dao.ProcessImpactDAO processDAO = new com.example.budg_v2.dao.ProcessImpactDAO();
                            String ownerName = processDAO.getPolicyOwnersString(policyId);
                            relationship.put("policyOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("PolicyImpactDAO: Error getting policy owners: " + e.getMessage());
                            relationship.put("policyOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("policyOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relationships for a glossary (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                pxg.id,
                pxg.PolicyID,
                pxg.GlossaryID,
                pxg.Relation_Type,
                pxg.Description,
                p.PrimaryName as policyName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM policy_x_glossary pxg
            LEFT JOIN policy p ON pxg.PolicyID = p.ID
            LEFT JOIN policy_x_glossary_relationtype rt ON pxg.Relation_Type = rt.ID
            WHERE pxg.GlossaryID = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", rs.getInt("PolicyID"));
                    relationship.put("glossaryId", rs.getInt("GlossaryID"));
                    relationship.put("relationType", rs.getObject("Relation_Type"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy relationships for a dataset (reverse lookup)
     */
    public List<Map<String, Object>> getPolicyRelationshipsByDatasetId(int datasetId) throws SQLException {
        String sql = """
            SELECT 
                pxd.ID as id,
                pxd.PolicyID as policy_id,
                pxd.DatasetID as dataset_id,
                pxd.Relation_Type as relation_type,
                pxd.Description as description,
                p.PrimaryName as policyName,
                p.RefNumber as policyRefNumber,
                pxdrt.PrimaryName as relationTypeName,
                pxdrt.ReverseName as relationTypeReverseName
            FROM policy_x_dataset pxd
            LEFT JOIN policy p ON pxd.PolicyID = p.ID
            LEFT JOIN policy_x_dataset_relationtype pxdrt ON pxd.Relation_Type = pxdrt.ID
            WHERE pxd.DatasetID = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, datasetId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int policyId = rs.getInt("policy_id");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("policyId", policyId);
                    relationship.put("datasetId", rs.getInt("dataset_id"));
                    relationship.put("relationType", rs.getInt("relation_type"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("policyRefNumber", rs.getString("policyRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get all policy owners for this policy (concatenated)
                    try {
                        // Use ProcessImpactDAO's method if available, otherwise set to "No owner"
                        com.example.budg_v2.dao.ProcessImpactDAO processDAO = new com.example.budg_v2.dao.ProcessImpactDAO();
                        String ownersName = processDAO.getPolicyOwnersString(policyId);
                        relationship.put("policyOwnerName", ownersName != null ? ownersName : "No owner");
                    } catch (Exception e) {
                        //system.out.println("PolicyImpactDAO: Error getting policy owners: " + e.getMessage());
                        relationship.put("policyOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get existing product relationships as a Map (productId -> relationType)
     */
    private Map<Integer, Integer> getExistingProductRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT productid, relationtype FROM product_x_policy WHERE policyid = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("productid");
                    int relationType = rs.getInt("relationtype");
                    if (productId > 0 && relationType > 0) {
                        existingRelationships.put(productId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing client relationships as a Map (clientId -> relationType)
     */
    private Map<Integer, Integer> getExistingClientRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT Client_ID, RelationType FROM client_x_policy WHERE Policy_ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int clientId = rs.getInt("Client_ID");
                    int relationType = rs.getInt("RelationType");
                    if (clientId > 0 && relationType > 0) {
                        existingRelationships.put(clientId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing process relationships as a Map (processId -> relationType)
     */
    private Map<Integer, Integer> getExistingProcessRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT process_id, relation_type FROM policy_x_process WHERE policy_id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int processId = rs.getInt("process_id");
                    int relationType = rs.getInt("relation_type");
                    if (processId > 0 && relationType > 0) {
                        existingRelationships.put(processId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing project relationships as a Map (projectId -> relationType)
     */
    private Map<Integer, Integer> getExistingProjectRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT project_id, relation_type FROM policy_x_project WHERE policy_id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("project_id");
                    int relationType = rs.getInt("relation_type");
                    if (projectId > 0 && relationType > 0) {
                        existingRelationships.put(projectId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing system relationships as a Map (systemId -> relationType)
     */
    private Map<Integer, Integer> getExistingSystemRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT System_ID, Relation_Type FROM policy_x_system WHERE Policy_ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int systemId = rs.getInt("System_ID");
                    int relationType = rs.getInt("Relation_Type");
                    if (systemId > 0 && relationType > 0) {
                        existingRelationships.put(systemId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing business area relationships as a Map (businessAreaId -> relationType)
     */
    private Map<Integer, Integer> getExistingBusinessAreaRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT BusinessArea_ID, RelationType FROM policy_x_businessarea WHERE Policy_ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int businessAreaId = rs.getInt("BusinessArea_ID");
                    int relationType = rs.getInt("RelationType");
                    if (businessAreaId > 0 && relationType > 0) {
                        existingRelationships.put(businessAreaId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing legal relationships as a Map (legalId -> relationType)
     */
    private Map<Integer, Integer> getExistingLegalRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT Legal_ID, RelationType FROM policy_x_legal WHERE Policy_ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int legalId = rs.getInt("Legal_ID");
                    int relationType = rs.getInt("RelationType");
                    if (legalId > 0 && relationType > 0) {
                        existingRelationships.put(legalId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing dataset relationships as a Map (datasetId -> relationType)
     */
    private Map<Integer, Integer> getExistingDatasetRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT DatasetID, Relation_Type FROM policy_x_dataset WHERE PolicyID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int datasetId = rs.getInt("DatasetID");
                    int relationType = rs.getInt("Relation_Type");
                    if (datasetId > 0 && relationType > 0) {
                        existingRelationships.put(datasetId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing attribute relationships as a Map (attributeId -> relationType)
     */
    private Map<Integer, Integer> getExistingAttributeRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT attributeid, relation_type FROM policy_x_attribute WHERE policyid = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int attributeId = rs.getInt("attributeid");
                    int relationType = rs.getInt("relation_type");
                    if (attributeId > 0 && relationType > 0) {
                        existingRelationships.put(attributeId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get existing glossary relationships as a Map (glossaryId -> relationType)
     */
    private Map<Integer, Integer> getExistingGlossaryRelationshipsMap(Connection conn, int policyId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT GlossaryID, Relation_Type FROM policy_x_glossary WHERE PolicyID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int glossaryId = rs.getInt("GlossaryID");
                    int relationType = rs.getInt("Relation_Type");
                    if (glossaryId > 0 && relationType > 0) {
                        existingRelationships.put(glossaryId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Get product name from product ID
     */
    private String getProductName(int productId) {
        String sql = "SELECT PrimaryName FROM product WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting product name for productId " + productId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get client name from client ID
     */
    private String getClientName(int clientId) {
        String sql = "SELECT PrimaryName FROM client WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting client name for clientId " + clientId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get process name from process ID
     */
    private String getProcessName(int processId) {
        String sql = "SELECT PrimaryName FROM process WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting process name for processId " + processId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get project name from project ID
     */
    private String getProjectName(int projectId) {
        String sql = "SELECT PrimaryName FROM project WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting project name for projectId " + projectId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get system name from system ID
     */
    private String getSystemName(int systemId) {
        String sql = "SELECT Name FROM system WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting system name for systemId " + systemId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get business area name from business area ID
     */
    private String getBusinessAreaName(int businessAreaId) {
        String sql = "SELECT PrimaryName FROM business_area WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, businessAreaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting business area name for businessAreaId " + businessAreaId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get legal name from legal ID
     */
    private String getLegalName(int legalId) {
        String sql = "SELECT ShortName FROM legal WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ShortName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting legal name for legalId " + legalId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get dataset name from dataset ID
     */
    private String getDatasetName(int datasetId) {
        String sql = "SELECT PrimaryName FROM dataset WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting dataset name for datasetId " + datasetId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get attribute name from attribute ID
     */
    private String getAttributeName(int attributeId) {
        String sql = "SELECT PrimaryName FROM attribute WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting attribute name for attributeId " + attributeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get glossary name from glossary ID
     */
    private String getGlossaryName(int glossaryId) {
        String sql = "SELECT Name FROM glossary WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting glossary name for glossaryId " + glossaryId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get product relation type name
     */
    private String getProductRelationTypeName(int relationTypeId) {
        String sql = "SELECT primaryname FROM product_x_policy_relationtype WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting product relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get client relation type name
     */
    private String getClientRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM client_x_policy_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting client relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get process relation type name
     */
    private String getProcessRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM policy_x_process_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting process relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get project relation type name
     */
    private String getProjectRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM policy_x_project_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting project relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get system relation type name
     */
    private String getSystemRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM policy_x_system_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting system relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get business area relation type name
     */
    private String getBusinessAreaRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM policy_x_businessarea_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting business area relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get legal relation type name
     */
    private String getLegalRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM policy_x_legal_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting legal relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get dataset relation type name
     */
    private String getDatasetRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM policy_x_dataset_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting dataset relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get attribute relation type name
     */
    private String getAttributeRelationTypeName(int relationTypeId) {
        String sql = "SELECT primaryname FROM policy_x_attribute_relationtype WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting attribute relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get glossary relation type name
     */
    private String getGlossaryRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM policy_x_glossary_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting glossary relation type name for relationTypeId " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get user full name from people table
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
            System.err.println("Error getting user full name for userId " + userId + ": " + e.getMessage());
        }
        return "System";
    }
    
    /**
     * Log audit history for insert operations in relationship tables
     */
    private void logAuditHistoryForInsert(Connection conn, int policyId, String tableType, 
                                        int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = null;
            String entityFieldName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                entityName = getProductName(entityId);
                entityFieldName = "Product";
            } else if ("client".equalsIgnoreCase(tableType)) {
                entityName = getClientName(entityId);
                entityFieldName = "Client";
            } else if ("process".equalsIgnoreCase(tableType)) {
                entityName = getProcessName(entityId);
                entityFieldName = "Process";
            } else if ("project".equalsIgnoreCase(tableType)) {
                entityName = getProjectName(entityId);
                entityFieldName = "Project";
            } else if ("system".equalsIgnoreCase(tableType)) {
                entityName = getSystemName(entityId);
                entityFieldName = "System";
            } else if ("businessarea".equalsIgnoreCase(tableType)) {
                entityName = getBusinessAreaName(entityId);
                entityFieldName = "Business Area";
            } else if ("legal".equalsIgnoreCase(tableType)) {
                entityName = getLegalName(entityId);
                entityFieldName = "Legal";
            } else if ("dataset".equalsIgnoreCase(tableType)) {
                entityName = getDatasetName(entityId);
                entityFieldName = "Dataset";
            } else if ("attribute".equalsIgnoreCase(tableType)) {
                entityName = getAttributeName(entityId);
                entityFieldName = "Attribute";
            } else if ("glossary".equalsIgnoreCase(tableType)) {
                entityName = getGlossaryName(entityId);
                entityFieldName = "Glossary";
            }
            
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get relation type name
            String relationTypeName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                relationTypeName = getProductRelationTypeName(relationTypeId);
            } else if ("client".equalsIgnoreCase(tableType)) {
                relationTypeName = getClientRelationTypeName(relationTypeId);
            } else if ("process".equalsIgnoreCase(tableType)) {
                relationTypeName = getProcessRelationTypeName(relationTypeId);
            } else if ("project".equalsIgnoreCase(tableType)) {
                relationTypeName = getProjectRelationTypeName(relationTypeId);
            } else if ("system".equalsIgnoreCase(tableType)) {
                relationTypeName = getSystemRelationTypeName(relationTypeId);
            } else if ("businessarea".equalsIgnoreCase(tableType)) {
                relationTypeName = getBusinessAreaRelationTypeName(relationTypeId);
            } else if ("legal".equalsIgnoreCase(tableType)) {
                relationTypeName = getLegalRelationTypeName(relationTypeId);
            } else if ("dataset".equalsIgnoreCase(tableType)) {
                relationTypeName = getDatasetRelationTypeName(relationTypeId);
            } else if ("attribute".equalsIgnoreCase(tableType)) {
                relationTypeName = getAttributeRelationTypeName(relationTypeId);
            } else if ("glossary".equalsIgnoreCase(tableType)) {
                relationTypeName = getGlossaryRelationTypeName(relationTypeId);
            }
            
            if (relationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name based on table type
            String objectName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                objectName = "Product X Policy";
            } else if ("client".equalsIgnoreCase(tableType)) {
                objectName = "Client X Policy";
            } else if ("process".equalsIgnoreCase(tableType)) {
                objectName = "Process X Policy";
            } else if ("project".equalsIgnoreCase(tableType)) {
                objectName = "Project X Policy";
            } else if ("system".equalsIgnoreCase(tableType)) {
                objectName = "System X Policy";
            } else if ("businessarea".equalsIgnoreCase(tableType)) {
                objectName = "Business Area X Policy";
            } else if ("legal".equalsIgnoreCase(tableType)) {
                objectName = "Legal X Policy";
            } else if ("dataset".equalsIgnoreCase(tableType)) {
                objectName = "Dataset X Policy";
            } else if ("attribute".equalsIgnoreCase(tableType)) {
                objectName = "Attribute X Policy";
            } else if ("glossary".equalsIgnoreCase(tableType)) {
                objectName = "Glossary X Policy";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Get next audit ID
            int nextAuditId = getNextAuditId(conn);
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO policy_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, policyId);
                ps.setInt(2, nextAuditId);
                ps.setString(3, objectName);
                ps.setString(4, "Links");
                ps.setString(5, "Added");
                ps.setString(6, entityFieldName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, entityName);
                ps.setString(9, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type
                ps.setInt(1, policyId);
                ps.setInt(2, nextAuditId + 1);
                ps.setString(3, objectName);
                ps.setString(4, "Links");
                ps.setString(5, "Added");
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
    private void logAuditHistoryForDelete(Connection conn, int policyId, String tableType, 
                                        int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = null;
            String entityFieldName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                entityName = getProductName(entityId);
                entityFieldName = "Product";
            } else if ("client".equalsIgnoreCase(tableType)) {
                entityName = getClientName(entityId);
                entityFieldName = "Client";
            } else if ("process".equalsIgnoreCase(tableType)) {
                entityName = getProcessName(entityId);
                entityFieldName = "Process";
            } else if ("project".equalsIgnoreCase(tableType)) {
                entityName = getProjectName(entityId);
                entityFieldName = "Project";
            } else if ("system".equalsIgnoreCase(tableType)) {
                entityName = getSystemName(entityId);
                entityFieldName = "System";
            } else if ("businessarea".equalsIgnoreCase(tableType)) {
                entityName = getBusinessAreaName(entityId);
                entityFieldName = "Business Area";
            } else if ("legal".equalsIgnoreCase(tableType)) {
                entityName = getLegalName(entityId);
                entityFieldName = "Legal";
            } else if ("dataset".equalsIgnoreCase(tableType)) {
                entityName = getDatasetName(entityId);
                entityFieldName = "Dataset";
            } else if ("attribute".equalsIgnoreCase(tableType)) {
                entityName = getAttributeName(entityId);
                entityFieldName = "Attribute";
            } else if ("glossary".equalsIgnoreCase(tableType)) {
                entityName = getGlossaryName(entityId);
                entityFieldName = "Glossary";
            }
            
            if (entityName == null) {
                System.err.println("Could not get entity name for audit history");
                return;
            }
            
            // Get relation type name
            String relationTypeName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                relationTypeName = getProductRelationTypeName(relationTypeId);
            } else if ("client".equalsIgnoreCase(tableType)) {
                relationTypeName = getClientRelationTypeName(relationTypeId);
            } else if ("process".equalsIgnoreCase(tableType)) {
                relationTypeName = getProcessRelationTypeName(relationTypeId);
            } else if ("project".equalsIgnoreCase(tableType)) {
                relationTypeName = getProjectRelationTypeName(relationTypeId);
            } else if ("system".equalsIgnoreCase(tableType)) {
                relationTypeName = getSystemRelationTypeName(relationTypeId);
            } else if ("businessarea".equalsIgnoreCase(tableType)) {
                relationTypeName = getBusinessAreaRelationTypeName(relationTypeId);
            } else if ("legal".equalsIgnoreCase(tableType)) {
                relationTypeName = getLegalRelationTypeName(relationTypeId);
            } else if ("dataset".equalsIgnoreCase(tableType)) {
                relationTypeName = getDatasetRelationTypeName(relationTypeId);
            } else if ("attribute".equalsIgnoreCase(tableType)) {
                relationTypeName = getAttributeRelationTypeName(relationTypeId);
            } else if ("glossary".equalsIgnoreCase(tableType)) {
                relationTypeName = getGlossaryRelationTypeName(relationTypeId);
            }
            
            if (relationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name based on table type
            String objectName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                objectName = "Product X Policy";
            } else if ("client".equalsIgnoreCase(tableType)) {
                objectName = "Client X Policy";
            } else if ("process".equalsIgnoreCase(tableType)) {
                objectName = "Process X Policy";
            } else if ("project".equalsIgnoreCase(tableType)) {
                objectName = "Project X Policy";
            } else if ("system".equalsIgnoreCase(tableType)) {
                objectName = "System X Policy";
            } else if ("businessarea".equalsIgnoreCase(tableType)) {
                objectName = "Business Area X Policy";
            } else if ("legal".equalsIgnoreCase(tableType)) {
                objectName = "Legal X Policy";
            } else if ("dataset".equalsIgnoreCase(tableType)) {
                objectName = "Dataset X Policy";
            } else if ("attribute".equalsIgnoreCase(tableType)) {
                objectName = "Attribute X Policy";
            } else if ("glossary".equalsIgnoreCase(tableType)) {
                objectName = "Glossary X Policy";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Get next audit ID
            int nextAuditId = getNextAuditId(conn);
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO policy_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, policyId);
                ps.setInt(2, nextAuditId);
                ps.setString(3, objectName);
                ps.setString(4, "Links");
                ps.setString(5, "Deleted");
                ps.setString(6, entityFieldName);
                ps.setString(7, entityName);
                ps.setNull(8, Types.VARCHAR);
                ps.setString(9, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type deleted
                ps.setInt(1, policyId);
                ps.setInt(2, nextAuditId + 1);
                ps.setString(3, objectName);
                ps.setString(4, "Links");
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
    private void logAuditHistoryForUpdate(Connection conn, int policyId, String tableType, 
                                        int entityId, int oldRelationTypeId, int newRelationTypeId, int userId) {
        try {
            // Get old and new relation type names
            String oldRelationTypeName = null;
            String newRelationTypeName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getProductRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getProductRelationTypeName(newRelationTypeId);
            } else if ("client".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getClientRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getClientRelationTypeName(newRelationTypeId);
            } else if ("process".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getProcessRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getProcessRelationTypeName(newRelationTypeId);
            } else if ("project".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getProjectRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getProjectRelationTypeName(newRelationTypeId);
            } else if ("system".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getSystemRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getSystemRelationTypeName(newRelationTypeId);
            } else if ("businessarea".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getBusinessAreaRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getBusinessAreaRelationTypeName(newRelationTypeId);
            } else if ("legal".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getLegalRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getLegalRelationTypeName(newRelationTypeId);
            } else if ("dataset".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getDatasetRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getDatasetRelationTypeName(newRelationTypeId);
            } else if ("attribute".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getAttributeRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getAttributeRelationTypeName(newRelationTypeId);
            } else if ("glossary".equalsIgnoreCase(tableType)) {
                oldRelationTypeName = getGlossaryRelationTypeName(oldRelationTypeId);
                newRelationTypeName = getGlossaryRelationTypeName(newRelationTypeId);
            }
            
            if (oldRelationTypeName == null || newRelationTypeName == null) {
                System.err.println("Could not get relation type name for audit history");
                return;
            }
            
            // Get user full name
            String userFullName = getUserFullName(userId);
            
            // Determine object name based on table type
            String objectName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                objectName = "Product X Policy";
            } else if ("client".equalsIgnoreCase(tableType)) {
                objectName = "Client X Policy";
            } else if ("process".equalsIgnoreCase(tableType)) {
                objectName = "Process X Policy";
            } else if ("project".equalsIgnoreCase(tableType)) {
                objectName = "Project X Policy";
            } else if ("system".equalsIgnoreCase(tableType)) {
                objectName = "System X Policy";
            } else if ("businessarea".equalsIgnoreCase(tableType)) {
                objectName = "Business Area X Policy";
            } else if ("legal".equalsIgnoreCase(tableType)) {
                objectName = "Legal X Policy";
            } else if ("dataset".equalsIgnoreCase(tableType)) {
                objectName = "Dataset X Policy";
            } else if ("attribute".equalsIgnoreCase(tableType)) {
                objectName = "Attribute X Policy";
            } else if ("glossary".equalsIgnoreCase(tableType)) {
                objectName = "Glossary X Policy";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Get next audit ID
            int nextAuditId = getNextAuditId(conn);
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO policy_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, policyId);
                ps.setInt(2, nextAuditId);
                ps.setString(3, objectName);
                ps.setString(4, "Links");
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
     * Get next audit ID for policy_audit_history table
     */
    private int getNextAuditId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(auditidpk), 0) + 1 FROM policy_audit_history";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
}
