package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class GlossaryImpactDAO {
    
    // ===== PRODUCT RELATIONSHIPS =====
    
    /**
     * Get product relationships for a glossary with owner information
     * Includes products directly linked to the glossary (blue rows) and products linked to child glossaries (orange rows)
     */
    public List<Map<String, Object>> getProductRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        // Get all child glossary IDs in the hierarchy
        Set<Integer> childGlossaryIds = getAllChildGlossaryIds(glossaryId);
        
        // Get products directly linked to the glossary (blue rows - no child glossary info)
        String directSql = """
            SELECT 
                pxg.productid as productId,
                pxg.glossaryid as glossaryId,
                pxg.relationtype as relationType,
                p.PrimaryName as productName,
                p.RefNumber as productRefNumber,
                rt.primaryname as relationTypeName,
                rt.description as relationTypeDescription,
                NULL as childGlossaryId,
                NULL as childGlossaryName,
                NULL as childGlossaryType
            FROM product_x_glossary pxg
            LEFT JOIN product p ON pxg.productid = p.id
            LEFT JOIN product_x_glossary_relationtype rt ON pxg.relationtype = rt.id
            WHERE pxg.glossaryid = ? AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(directSql)) {
            
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer productId = rs.getObject("productId") != null ? rs.getInt("productId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("productId", productId);
                    relationship.put("glossaryId", rs.getInt("glossaryId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    relationship.put("childGlossaryId", null);
                    relationship.put("childGlossaryName", null);
                    relationship.put("childGlossaryType", null);
                    relationship.put("isDirectLink", true);
                    
                    // Get product owners
                    if (productId != null) {
                        try {
                            String ownersName = getProductOwnersString(productId);
                            String ownersEmail = getProductOwnersEmail(productId);
                            relationship.put("productOwnerName", ownersName);
                            relationship.put("productOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("GlossaryImpactDAO: Error getting product owners: " + e.getMessage());
                            relationship.put("productOwnerName", "No owner");
                            relationship.put("productOwnerEmail", null);
                        }
                    } else {
                        relationship.put("productOwnerName", null);
                        relationship.put("productOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        // Get products linked to child glossaries (orange rows - with child glossary info)
        if (!childGlossaryIds.isEmpty()) {
            // Build IN clause with placeholders
            String placeholders = String.join(",", Collections.nCopies(childGlossaryIds.size(), "?"));
            String childSql = "SELECT " +
                "pxg.productid as productId, " +
                "pxg.glossaryid as childGlossaryId, " +
                "pxg.relationtype as relationType, " +
                "p.PrimaryName as productName, " +
                "p.RefNumber as productRefNumber, " +
                "rt.primaryname as relationTypeName, " +
                "rt.description as relationTypeDescription, " +
                "g.Name as childGlossaryName, " +
                "gt.Name as childGlossaryType " +
                "FROM product_x_glossary pxg " +
                "LEFT JOIN product p ON pxg.productid = p.id " +
                "LEFT JOIN product_x_glossary_relationtype rt ON pxg.relationtype = rt.id " +
                "LEFT JOIN glossary g ON pxg.glossaryid = g.ID " +
                "LEFT JOIN glossary_type gt ON g.Type = gt.ID " +
                "WHERE pxg.glossaryid IN (" + placeholders + ") AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')";
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(childSql)) {
                
                // Set parameters for IN clause
                int paramIndex = 1;
                for (Integer childId : childGlossaryIds) {
                    ps.setInt(paramIndex++, childId);
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Integer productId = rs.getObject("productId") != null ? rs.getInt("productId") : null;
                        Integer childGlossaryId = rs.getObject("childGlossaryId") != null ? rs.getInt("childGlossaryId") : null;
                        String childGlossaryName = rs.getString("childGlossaryName");
                        String childGlossaryType = rs.getString("childGlossaryType");
                        
                        Map<String, Object> relationship = new HashMap<>();
                        relationship.put("productId", productId);
                        relationship.put("glossaryId", glossaryId); // Parent glossary ID
                        relationship.put("relationType", rs.getObject("relationType"));
                        relationship.put("productName", rs.getString("productName"));
                        relationship.put("productRefNumber", rs.getString("productRefNumber"));
                        relationship.put("relationTypeName", rs.getString("relationTypeName"));
                        relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                        relationship.put("childGlossaryId", childGlossaryId);
                        relationship.put("childGlossaryName", childGlossaryName);
                        relationship.put("childGlossaryType", childGlossaryType);
                        relationship.put("isDirectLink", false);
                        
                        // Get product owners
                        if (productId != null) {
                            try {
                                String ownersName = getProductOwnersString(productId);
                                String ownersEmail = getProductOwnersEmail(productId);
                                relationship.put("productOwnerName", ownersName);
                                relationship.put("productOwnerEmail", ownersEmail);
                            } catch (Exception e) {
                                //system.out.println("GlossaryImpactDAO: Error getting product owners: " + e.getMessage());
                                relationship.put("productOwnerName", "No owner");
                                relationship.put("productOwnerEmail", null);
                            }
                        } else {
                            relationship.put("productOwnerName", null);
                            relationship.put("productOwnerEmail", null);
                        }
                        
                        relationships.add(relationship);
                    }
                }
            }
        }
        
        // Sort by product name
        relationships.sort((a, b) -> {
            String nameA = (String) a.getOrDefault("productName", "");
            String nameB = (String) b.getOrDefault("productName", "");
            return nameA.compareToIgnoreCase(nameB);
        });
        
        return relationships;
    }
    
    /**
     * Get all child glossary IDs in the hierarchy (recursively)
     */
    private Set<Integer> getAllChildGlossaryIds(int glossaryId) throws SQLException {
        Set<Integer> childIds = new HashSet<>();
        
        // First, check if MySQL version supports recursive CTEs (MySQL 8.0+)
        // If not, use iterative approach
        String sql = """
            WITH RECURSIVE descendants AS (
                SELECT g.ID
                FROM glossary g
                WHERE g.Parent_ID = ? AND g.Deleted_datetime IS NULL
                UNION ALL
                SELECT g.ID
                FROM glossary g
                INNER JOIN descendants d ON g.Parent_ID = d.ID
                WHERE d.ID IS NOT NULL AND g.Deleted_datetime IS NULL
            )
            SELECT ID FROM descendants
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    childIds.add(rs.getInt("ID"));
                }
            }
        } catch (SQLException e) {
            // If recursive CTE fails (MySQL < 8.0), use iterative approach
            childIds = getAllChildGlossaryIdsIterative(glossaryId);
        }
        
        return childIds;
    }
    
    // Fallback method for MySQL versions that don't support recursive CTEs
    private Set<Integer> getAllChildGlossaryIdsIterative(int glossaryId) throws SQLException {
        Set<Integer> allChildIds = new HashSet<>();
        Set<Integer> currentLevel = new HashSet<>();
        
        // Start with direct children of the glossary
        String firstLevelSql = "SELECT ID FROM glossary WHERE Parent_ID = ? AND Deleted_datetime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(firstLevelSql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int childId = rs.getInt("ID");
                    allChildIds.add(childId);
                    currentLevel.add(childId);
                }
            }
        }
        
        // Continue with deeper levels
        int maxDepth = 100; // Safety limit to prevent infinite loops
        int depth = 0;
        
        while (!currentLevel.isEmpty() && depth < maxDepth) {
            Set<Integer> nextLevel = new HashSet<>();
            
            // Build IN clause for current level
            String placeholders = String.join(",", Collections.nCopies(currentLevel.size(), "?"));
            String sql = "SELECT ID FROM glossary WHERE Parent_ID IN (" + placeholders + ") AND Deleted_datetime IS NULL";
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                int paramIndex = 1;
                for (Integer parentId : currentLevel) {
                    ps.setInt(paramIndex++, parentId);
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int childId = rs.getInt("ID");
                        if (!allChildIds.contains(childId)) {
                            allChildIds.add(childId);
                            nextLevel.add(childId);
                        }
                    }
                }
            }
            
            currentLevel = nextLevel;
            depth++;
        }
        
        return allChildIds;
    }
    
    /**
     * Get all product relation types for glossary
     */
    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT id, primaryname, description, priority, reversename
            FROM product_x_glossary_relationtype
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
                relationType.put("priority", rs.getObject("priority"));
                relationType.put("reversename", rs.getString("reversename"));
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
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM product_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.product_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
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
     * Get product owners email (concatenated)
     */
    public String getProductOwnersEmail(int productId) throws SQLException {
        String sql = """
            SELECT pe.Email as ownerEmail
            FROM product_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.product_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
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
     * Save product relationships for a glossary
     * Note: product_x_glossary doesn't have an ID column (no PRIMARY KEY)
     */
    public boolean saveProductRelationships(int glossaryId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingProductRelationshipsMap(conn, glossaryId);
            
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
                    logAuditHistoryForDelete(conn, glossaryId, "product", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, glossaryId, "product", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, glossaryId, "product", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM product_x_glossary WHERE glossaryid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, glossaryId);
                int deletedCount = ps.executeUpdate();
                System.out.println("🔗 [RELATIONSHIPS] [GlossaryImpactDAO] Deleted " + deletedCount + " existing product relationships for glossaryId " + glossaryId);
            }
            
            // Insert new relationships (prevent duplicates)
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO product_x_glossary
                (productid, glossaryid, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid)
                VALUES (?, ?, ?, NOW(), NOW(), ?)
            """;
            
            int insertedCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                        Object productIdObj = rel.get("productId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        // Skip if missing required fields
                        if (productIdObj == null || relationTypeObj == null) {
                            continue;
                        }
                        
                        int productId = ((Number) productIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        
                        // Check for duplicates
                        if (productId > 0 && relationType > 0) {
                            String key = productId + "_" + relationType;
                            if (seen.contains(key)) {
                                //system.out.println("GlossaryImpactDAO: Skipping duplicate product relationship - productId=" + productId + ", relationType=" + relationType);
                                continue;
                            }
                            seen.add(key);
                            
                            ps.setInt(1, productId);
                            ps.setInt(2, glossaryId);
                            ps.setInt(3, relationType);
                            ps.setInt(4, userId);
                            ps.addBatch();
                            insertedCount++;
                        }
                    }
                }
                ps.executeBatch();
            }
            
            System.out.println("🔗 [RELATIONSHIPS] [GlossaryImpactDAO] Inserted " + insertedCount + " product relationships for glossaryId " + glossaryId);
            
            // Verify the relationships were actually saved
            String verifySql = "SELECT COUNT(*) as cnt FROM product_x_glossary WHERE glossaryid = ?";
            try (PreparedStatement verifyStmt = conn.prepareStatement(verifySql)) {
                verifyStmt.setInt(1, glossaryId);
                try (ResultSet rs = verifyStmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt("cnt");
                        System.out.println("🔗 [RELATIONSHIPS] [GlossaryImpactDAO] Verification: Found " + count + " product relationships in database for glossaryId " + glossaryId);
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
     * Get client relationships for a glossary with owner information
     */
    public List<Map<String, Object>> getClientRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                cxg.ID as id,
                cxg.Glossary_ID as glossaryId,
                cxg.Client_ID as clientId,
                cxg.RelationType as relationType,
                c.PrimaryName as clientName,
                rt.PrimaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM client_x_glossary cxg
            LEFT JOIN client c ON cxg.Client_ID = c.ID
            LEFT JOIN client_x_glossary_relationtype rt ON cxg.RelationType = rt.ID
            WHERE cxg.Glossary_ID = ?
            ORDER BY c.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        Integer id = rs.getObject("id") != null ? rs.getInt("id") : null;
                        Integer clientId = rs.getObject("clientId") != null ? rs.getInt("clientId") : null;
                        
                        Map<String, Object> relationship = new HashMap<>();
                        if (id != null) {
                            relationship.put("id", id);
                        }
                        relationship.put("glossaryId", rs.getObject("glossaryId") != null ? rs.getInt("glossaryId") : null);
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
                                //system.out.println("GlossaryImpactDAO: Error getting client owners for clientId " + clientId + ": " + e.getMessage());
                                e.printStackTrace();
                                relationship.put("clientOwnerName", "No owner");
                                relationship.put("clientOwnerEmail", null);
                            }
                        } else {
                            relationship.put("clientOwnerName", null);
                            relationship.put("clientOwnerEmail", null);
                        }
                        
                        relationships.add(relationship);
                    } catch (Exception e) {
                        //system.out.println("GlossaryImpactDAO: Error processing client relationship row: " + e.getMessage());
                        e.printStackTrace();
                        // Continue to next row instead of failing completely
                    }
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get glossary relationships for a client (reverse lookup)
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByClientId(int clientId) throws SQLException {
        String sql = """
            SELECT 
                cxg.ID as id,
                cxg.Glossary_ID as glossaryId,
                cxg.Client_ID as clientId,
                cxg.RelationType as relationType,
                g.Name as glossaryName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM client_x_glossary cxg
            LEFT JOIN glossary g ON cxg.Glossary_ID = g.ID
            LEFT JOIN client_x_glossary_relationtype rt ON cxg.RelationType = rt.ID
            WHERE cxg.Client_ID = ? AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '1970-01-01 00:00:00')
            ORDER BY g.Name
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int glossaryId = rs.getInt("glossaryId");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("glossaryId", glossaryId);
                    relationship.put("clientId", rs.getInt("clientId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get glossary owner information
                    try {
                        String ownerName = getGlossaryOwnersString(glossaryId);
                        relationship.put("glossaryOwnerName", ownerName);
                    } catch (SQLException e) {
                        //system.out.println("GlossaryImpactDAO: SQLException getting glossary owners for glossaryId " + glossaryId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("glossaryOwnerName", "No owner");
                    } catch (Exception e) {
                        //system.out.println("GlossaryImpactDAO: Exception getting glossary owners for glossaryId " + glossaryId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("glossaryOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get glossary owners string (concatenated names)
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
     * Get all client relation types for glossary
     */
    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM client_x_glossary_relationtype
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
     * Get the next available ID for client_x_glossary table
     */
    private int getNextClientGlossaryId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 FROM client_x_glossary";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Save client relationships for a glossary
     */
    public boolean saveClientRelationships(int glossaryId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingClientRelationshipsMap(conn, glossaryId);
            
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
                    logAuditHistoryForDelete(conn, glossaryId, "client", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, glossaryId, "client", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, glossaryId, "client", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM client_x_glossary WHERE Glossary_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, glossaryId);
                int deletedCount = ps.executeUpdate();
                System.out.println("🔗 [RELATIONSHIPS] [GlossaryImpactDAO] Deleted " + deletedCount + " existing client relationships for glossaryId " + glossaryId);
            }
            
            // Insert new relationships (prevent duplicates)
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO client_x_glossary
                (ID, Glossary_ID, Client_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID)
                VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
            """;
            
            int nextId = getNextClientGlossaryId(conn); // Get starting ID once
            int insertedCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                        Object clientIdObj = rel.get("clientId");
                        Object relationTypeObj = rel.get("relationType");
                        
                        // Skip if missing required fields
                        if (clientIdObj == null || relationTypeObj == null) {
                            continue;
                        }
                        
                        int clientId = ((Number) clientIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        
                        // Check for duplicates
                        if (clientId > 0 && relationType > 0) {
                            String key = clientId + "_" + relationType;
                            if (seen.contains(key)) {
                                //system.out.println("GlossaryImpactDAO: Skipping duplicate client relationship - clientId=" + clientId + ", relationType=" + relationType);
                                continue;
                            }
                            seen.add(key);
                            
                            ps.setInt(1, nextId++); // Use and increment ID
                            ps.setInt(2, glossaryId);
                            ps.setInt(3, clientId);
                            ps.setInt(4, relationType);
                            ps.setInt(5, userId);
                            ps.addBatch();
                            insertedCount++;
                        }
                    }
                }
                ps.executeBatch();
            }
            
            System.out.println("🔗 [RELATIONSHIPS] [GlossaryImpactDAO] Inserted " + insertedCount + " client relationships for glossaryId " + glossaryId);
            
            // Verify the relationships were actually saved
            String verifySql = "SELECT COUNT(*) as cnt FROM client_x_glossary WHERE Glossary_ID = ?";
            try (PreparedStatement verifyStmt = conn.prepareStatement(verifySql)) {
                verifyStmt.setInt(1, glossaryId);
                try (ResultSet rs = verifyStmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt("cnt");
                        System.out.println("🔗 [RELATIONSHIPS] [GlossaryImpactDAO] Verification: Found " + count + " client relationships in database for glossaryId " + glossaryId);
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
     * Get glossary relationships for a business area (reverse lookup)
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.BusinessArea_ID,
                r.Glossary_ID,
                r.RelationType,
                r.Description,
                g.Name as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM businessarea_x_glossary r
            LEFT JOIN glossary g ON r.Glossary_ID = g.ID
            LEFT JOIN businessarea_x_glossary_relationtype rt ON r.RelationType = rt.ID
            WHERE r.BusinessArea_ID = ? AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '1970-01-01 00:00:00')
            ORDER BY g.Name
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("businessAreaId", rs.getInt("BusinessArea_ID"));
                    relationship.put("glossaryId", rs.getInt("Glossary_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get glossary relationships for a process (reverse lookup)
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByProcessId(int processId) throws SQLException {
        // Check if process_x_glossary table exists
        String sql = """
            SELECT 
                r.ID,
                r.Process_ID,
                r.Glossary_ID,
                r.RelationType,
                r.Description,
                g.Name as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM process_x_glossary r
            LEFT JOIN glossary g ON r.Glossary_ID = g.ID
            LEFT JOIN process_x_glossary_relationtype rt ON r.RelationType = rt.ID
            WHERE r.Process_ID = ? AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '1970-01-01 00:00:00')
            ORDER BY g.Name
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
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        } catch (SQLException e) {
            // If table doesn't exist, return empty list
            if (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table")) {
                //system.out.println("GlossaryImpactDAO: process_x_glossary table not found, returning empty list");
                return relationships;
            }
            throw e;
        }
        
        return relationships;
    }
    
    /**
     * Get glossary relationships for a policy (reverse lookup)
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxg.ID,
                pxg.Policy_ID,
                pxg.Glossary_ID,
                pxg.RelationType,
                pxg.Description,
                g.Name as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM policy_x_glossary pxg
            LEFT JOIN glossary g ON pxg.Glossary_ID = g.ID
            LEFT JOIN policy_x_glossary_relationtype rt ON pxg.RelationType = rt.ID
            WHERE pxg.Policy_ID = ? AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '1970-01-01 00:00:00')
            ORDER BY g.Name
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, policyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("policyId", rs.getInt("Policy_ID"));
                    relationship.put("glossaryId", rs.getInt("Glossary_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get glossary relationships for a product (reverse lookup)
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByProductId(int productId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                pxg.productid as productId,
                pxg.glossaryid as glossaryId,
                pxg.relationtype as relationType,
                g.Name as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM product_x_glossary pxg
            LEFT JOIN glossary g ON pxg.glossaryid = g.id
            LEFT JOIN product_x_glossary_relationtype rt ON pxg.relationtype = rt.id
            WHERE pxg.productid = ? AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '1970-01-01 00:00:00')
            ORDER BY g.Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer glossaryId = rs.getObject("glossaryId") != null ? rs.getInt("glossaryId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("productId", rs.getInt("productId"));
                    relationship.put("glossaryId", glossaryId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get glossary owners
                    if (glossaryId != null) {
                        try {
                            String ownerName = getGlossaryOwnersString(glossaryId);
                            relationship.put("glossaryOwnerName", ownerName != null ? ownerName : "No owner");
                        } catch (Exception e) {
                            //system.out.println("GlossaryImpactDAO: Error getting glossary owners: " + e.getMessage());
                            relationship.put("glossaryOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("glossaryOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get glossary relationships for a capability (reverse lookup)
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        String sql = """
            SELECT 
                cxg.ID,
                cxg.Capability_ID,
                cxg.Glossary_ID,
                cxg.RelationType,
                cxg.Description,
                g.Name as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM capability_x_glossary cxg
            LEFT JOIN glossary g ON cxg.Glossary_ID = g.ID
            LEFT JOIN capability_x_glossary_relationtype rt ON cxg.RelationType = rt.ID
            WHERE cxg.Capability_ID = ? AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '1970-01-01 00:00:00')
            ORDER BY g.Name
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("glossaryId", rs.getInt("Glossary_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
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
     * Get existing product relationships as a Map (productId -> relationType)
     */
    private Map<Integer, Integer> getExistingProductRelationshipsMap(Connection conn, int glossaryId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT productid, relationtype FROM product_x_glossary WHERE glossaryid = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
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
    private Map<Integer, Integer> getExistingClientRelationshipsMap(Connection conn, int glossaryId) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = "SELECT Client_ID, RelationType FROM client_x_glossary WHERE Glossary_ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
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
     * Get glossary name for target audit mirror (uses same connection for transaction).
     */
    private String getGlossaryName(Connection conn, int glossaryId) {
        String sql = "SELECT Name FROM glossary WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        } catch (SQLException e) {
            System.err.println("Error getting glossary name: " + e.getMessage());
        }
        return null;
    }

    /**
     * Insert mirror audit record into the target entity's audit table (client/product) so the relationship appears in that facet's history tab.
     */
    private void insertTargetAuditMirror(Connection conn, String tableType, int entityId, int glossaryId,
            String updateType, String fieldValueFrom, String fieldValueTo, String author) {
        String glossaryName = getGlossaryName(conn, glossaryId);
        if (glossaryName == null) glossaryName = "Glossary #" + glossaryId;
        String targetTable;
        String objectName;
        if ("product".equalsIgnoreCase(tableType)) {
            targetTable = "product_audit_history";
            objectName = "Glossary X Product";
        } else if ("client".equalsIgnoreCase(tableType)) {
            targetTable = "client_audit_history";
            objectName = "Glossary X Client";
        } else {
            return;
        }
        try {
            String sql = "INSERT INTO " + targetTable + " (id, object, event, updateType, field, `from`, `to`, author) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, entityId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, updateType);
                ps.setString(5, "Glossary");
                ps.setString(6, fieldValueFrom);
                ps.setString(7, fieldValueTo);
                ps.setString(8, author);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error inserting target audit mirror: " + e.getMessage());
        }
    }
    
    /**
     * Get product relation type name from relation type ID
     */
    private String getProductRelationTypeName(int relationTypeId) {
        String sql = "SELECT primaryname FROM product_x_glossary_relationtype WHERE id = ?";
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
     * Get client relation type name from relation type ID
     */
    private String getClientRelationTypeName(int relationTypeId) {
        String sql = "SELECT PrimaryName FROM client_x_glossary_relationtype WHERE ID = ?";
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
     * @param glossaryId The glossary ID
     * @param tableType The type of relationship table ("product" or "client")
     * @param entityId The ID of the related entity
     * @param relationTypeId The ID of the relation type
     * @param userId The ID of the user performing the action
     */
    private void logAuditHistoryForInsert(Connection conn, int glossaryId, String tableType, 
                                         int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                entityName = getProductName(entityId);
            } else if ("client".equalsIgnoreCase(tableType)) {
                entityName = getClientName(entityId);
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
            if ("product".equalsIgnoreCase(tableType)) {
                objectName = "Product X Glossary";
                entityFieldName = "Product";
            } else if ("client".equalsIgnoreCase(tableType)) {
                objectName = "Client X Glossary";
                entityFieldName = "Client";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO glossary_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, glossaryId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Added");
                ps.setString(5, entityFieldName);
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, entityName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type
                ps.setInt(1, glossaryId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Status Change");
                ps.setString(5, "Relationship Type");
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, relationTypeName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
            }
            // Mirror: write to target entity's audit table (client/product) for history tab
            String glossaryName = getGlossaryName(conn, glossaryId);
            if (glossaryName != null) {
                insertTargetAuditMirror(conn, tableType, entityId, glossaryId, "Added", null, glossaryName, userFullName);
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
    private void logAuditHistoryForDelete(Connection conn, int glossaryId, String tableType, 
                                         int entityId, int relationTypeId, int userId) {
        try {
            // Get entity name
            String entityName = null;
            if ("product".equalsIgnoreCase(tableType)) {
                entityName = getProductName(entityId);
            } else if ("client".equalsIgnoreCase(tableType)) {
                entityName = getClientName(entityId);
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
            if ("product".equalsIgnoreCase(tableType)) {
                objectName = "Product X Glossary";
                entityFieldName = "Product";
            } else if ("client".equalsIgnoreCase(tableType)) {
                objectName = "Client X Glossary";
                entityFieldName = "Client";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO glossary_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, glossaryId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Deleted");
                ps.setString(5, entityFieldName);
                ps.setString(6, entityName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type deleted
                ps.setInt(1, glossaryId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Deleted");
                ps.setString(5, "Relationship Type");
                ps.setString(6, relationTypeName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, userFullName);
                ps.executeUpdate();
            }
            // Mirror: write to target entity's audit table
            String glossaryName = getGlossaryName(conn, glossaryId);
            if (glossaryName != null) {
                insertTargetAuditMirror(conn, tableType, entityId, glossaryId, "Deleted", glossaryName, null, userFullName);
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
    private void logAuditHistoryForUpdate(Connection conn, int glossaryId, String tableType, 
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
                objectName = "Product X Glossary";
            } else if ("client".equalsIgnoreCase(tableType)) {
                objectName = "Client X Glossary";
            } else {
                System.err.println("Unknown table type for audit history: " + tableType);
                return;
            }
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO glossary_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, glossaryId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Status Change");
                ps.setString(5, "Relationship Type");
                ps.setString(6, oldRelationTypeName);
                ps.setString(7, newRelationTypeName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
            }
            // Mirror: write to target entity's audit table (relationship type change)
            insertTargetAuditMirror(conn, tableType, entityId, glossaryId, "Status Change", oldRelationTypeName, newRelationTypeName, userFullName);
        } catch (SQLException e) {
            // Don't throw exception - just log error to avoid breaking the main transaction
            System.err.println("Error logging audit history for update: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

