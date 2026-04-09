package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class RegulationImpactDAO {
    
    // ===== PRODUCT RELATIONSHIPS =====
    
    public List<Map<String, Object>> getProductRelationshipsByRegulationId(int regulationId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                rxp.ID as id,
                rxp.RegulationID as regulationId,
                rxp.ProductID as productId,
                rxp.RelationType as relationType,
                p.primaryname as productName,
                p.refnumber as productRefNumber,
                rt.primaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM regulation_x_product rxp
            LEFT JOIN product p ON rxp.ProductID = p.id
            LEFT JOIN regulation_x_product_relationtype rt ON rxp.RelationType = rt.ID
            WHERE rxp.RegulationID = ?
            ORDER BY p.primaryname
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, regulationId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer productId = rs.getObject("productId") != null ? rs.getInt("productId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("regulationId", rs.getInt("regulationId"));
                    relationship.put("productId", productId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get product owners
                    if (productId != null) {
                        try {
                            String ownersName = getProductOwnersString(productId);
                            String ownersEmail = getProductOwnersEmail(productId);
                            relationship.put("productOwnerName", ownersName);
                            relationship.put("productOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("RegulationImpactDAO: Error getting product owners: " + e.getMessage());
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
        
        return relationships;
    }
    
    // Reverse lookup: Get Regulation relationships by Product ID
    public List<Map<String, Object>> getRegulationRelationshipsByProductId(int productId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                rxp.ID as id,
                rxp.RegulationID as regulationId,
                rxp.ProductID as productId,
                rxp.RelationType as relationType,
                r.primaryName as regulationName,
                r.RefNumber as regulationRefNumber,
                rt.primaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM regulation_x_product rxp
            LEFT JOIN regulation r ON rxp.RegulationID = r.ID
            LEFT JOIN regulation_x_product_relationtype rt ON rxp.RelationType = rt.ID
            WHERE rxp.ProductID = ? AND (r.DeletedDatetime IS NULL OR r.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY r.primaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer regulationId = rs.getObject("regulationId") != null ? rs.getInt("regulationId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("regulationId", regulationId);
                    relationship.put("productId", rs.getInt("productId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("regulationName", rs.getString("regulationName"));
                    relationship.put("regulationRefNumber", rs.getString("regulationRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get regulation owners
                    if (regulationId != null) {
                        try {
                            String ownersName = getRegulationOwnersString(regulationId);
                            relationship.put("regulationOwnerName", ownersName);
                        } catch (Exception e) {
                            //system.out.println("RegulationImpactDAO: Error getting regulation owners: " + e.getMessage());
                            relationship.put("regulationOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("regulationOwnerName", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    // Reverse lookup: Get Regulation relationships by Policy ID
    public List<Map<String, Object>> getRegulationRelationshipsByPolicyId(int policyId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                rxp.ID as id,
                rxp.RegulationID as regulationId,
                rxp.PolicyID as policyId,
                rxp.RelationType as relationType,
                r.primaryName as regulationName,
                r.RefNumber as regulationRefNumber,
                rt.primaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM regulation_x_policy rxp
            LEFT JOIN regulation r ON rxp.RegulationID = r.ID
            LEFT JOIN regulation_x_policy_relationtype rt ON rxp.RelationType = rt.ID
            WHERE rxp.PolicyID = ? AND (r.DeletedDatetime IS NULL OR r.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY r.primaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, policyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer regulationId = rs.getObject("regulationId") != null ? rs.getInt("regulationId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("regulationId", regulationId);
                    relationship.put("policyId", rs.getInt("policyId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("regulationName", rs.getString("regulationName"));
                    relationship.put("regulationRefNumber", rs.getString("regulationRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get regulation owners
                    if (regulationId != null) {
                        try {
                            String ownersName = getRegulationOwnersString(regulationId);
                            relationship.put("regulationOwnerName", ownersName);
                        } catch (Exception e) {
                            //system.out.println("RegulationImpactDAO: Error getting regulation owners: " + e.getMessage());
                            relationship.put("regulationOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("regulationOwnerName", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    // Reverse lookup: Get Regulation relationships by Project ID
    public List<Map<String, Object>> getRegulationRelationshipsByProjectId(int projectId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                rxp.ID as id,
                rxp.RegulationID as regulationId,
                rxp.ProjectID as projectId,
                rxp.RelationType as relationType,
                r.primaryName as regulationName,
                r.RefNumber as regulationRefNumber,
                rt.primaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM regulation_x_project rxp
            LEFT JOIN regulation r ON rxp.RegulationID = r.ID
            LEFT JOIN regulation_x_project_relationtype rt ON rxp.RelationType = rt.ID
            WHERE rxp.ProjectID = ? AND (r.DeletedDatetime IS NULL OR r.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY r.primaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer regulationId = rs.getObject("regulationId") != null ? rs.getInt("regulationId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("regulationId", regulationId);
                    relationship.put("projectId", rs.getInt("projectId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("regulationName", rs.getString("regulationName"));
                    relationship.put("regulationRefNumber", rs.getString("regulationRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get regulation owners
                    if (regulationId != null) {
                        try {
                            String ownersName = getRegulationOwnersString(regulationId);
                            relationship.put("regulationOwnerName", ownersName);
                        } catch (Exception e) {
                            //system.out.println("RegulationImpactDAO: Error getting regulation owners: " + e.getMessage());
                            relationship.put("regulationOwnerName", "No owner");
                        }
                    } else {
                        relationship.put("regulationOwnerName", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    // Get regulation owners
    public String getRegulationOwnersString(int regulationId) throws SQLException {
        String sql = """
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM regulation_x_objectxpeople rxop
            LEFT JOIN object_x_people oxp ON rxop.Object_x_ipid = oxp.ID
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE rxop.RegulationID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, regulationId);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<String> owners = new ArrayList<>();
                while (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName != null && !ownerName.trim().isEmpty()) {
                        owners.add(ownerName);
                    }
                }
                if (owners.isEmpty()) {
                    return "No owner";
                }
                return String.join(", ", owners);
            }
        }
    }
    
    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID as id, primaryName as primaryname, Description as description, Priority as priority, ReverseName as reversename
            FROM regulation_x_product_relationtype
            WHERE DeleteDatetime IS NULL
            ORDER BY Priority, primaryName
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
                        owners.add(ownerName);
                    }
                }
                if (owners.isEmpty()) {
                    return "No owner";
                }
                return String.join(", ", owners);
            }
        }
    }
    
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
                        emails.add(email);
                    }
                }
                if (emails.isEmpty()) {
                    return null;
                }
                return String.join(", ", emails);
            }
        }
    }
    
    public boolean saveProductRelationships(int regulationId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, regulationId, "product");
            
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
                    logAuditHistoryForDelete(conn, regulationId, "product", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, regulationId, "product", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, regulationId, "product", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM regulation_x_product WHERE RegulationID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, regulationId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO regulation_x_product
                (RegulationID, ProductID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID)
                VALUES (?, ?, ?, NOW(), NOW(), ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                    Object productIdObj = rel.get("productId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    if (productIdObj == null || relationTypeObj == null) {
                        continue;
                    }
                    
                    int productId = ((Number) productIdObj).intValue();
                    int relationType = ((Number) relationTypeObj).intValue();
                    
                    String key = productId + "_" + relationType;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);
                    
                    ps.setInt(1, regulationId);
                    ps.setInt(2, productId);
                    ps.setInt(3, relationType);
                    ps.setInt(4, userId);
                    ps.addBatch();
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
    
    // ===== POLICY RELATIONSHIPS =====
    
    public List<Map<String, Object>> getPolicyRelationshipsByRegulationId(int regulationId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                rxp.ID as id,
                rxp.RegulationID as regulationId,
                rxp.PolicyID as policyId,
                rxp.RelationType as relationType,
                p.PrimaryName as policyName,
                p.RefNumber as policyRefNumber,
                rt.primaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM regulation_x_policy rxp
            LEFT JOIN policy p ON rxp.PolicyID = p.ID
            LEFT JOIN regulation_x_policy_relationtype rt ON rxp.RelationType = rt.ID
            WHERE rxp.RegulationID = ?
            ORDER BY p.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, regulationId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer policyId = rs.getObject("policyId") != null ? rs.getInt("policyId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("regulationId", rs.getInt("regulationId"));
                    relationship.put("policyId", policyId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("policyRefNumber", rs.getString("policyRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get policy owners
                    if (policyId != null) {
                        try {
                            String ownersName = getPolicyOwnersString(policyId);
                            String ownersEmail = getPolicyOwnersEmail(policyId);
                            relationship.put("policyOwnerName", ownersName);
                            relationship.put("policyOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("RegulationImpactDAO: Error getting policy owners: " + e.getMessage());
                            relationship.put("policyOwnerName", "No owner");
                            relationship.put("policyOwnerEmail", null);
                        }
                    } else {
                        relationship.put("policyOwnerName", null);
                        relationship.put("policyOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    public List<Map<String, Object>> getPolicyRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID as id, primaryName as primaryname, Description as description, Priority as priority, ReverseName as reversename
            FROM regulation_x_policy_relationtype
            WHERE DeleteDatetime IS NULL
            ORDER BY Priority, primaryName
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
    
    public String getPolicyOwnersString(int policyId) throws SQLException {
        String sql = """
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM policy_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.Policy_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, policyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<String> owners = new ArrayList<>();
                while (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName != null && !ownerName.trim().isEmpty()) {
                        owners.add(ownerName);
                    }
                }
                if (owners.isEmpty()) {
                    return "No owner";
                }
                return String.join(", ", owners);
            }
        }
    }
    
    public String getPolicyOwnersEmail(int policyId) throws SQLException {
        String sql = """
            SELECT pe.Email as ownerEmail
            FROM policy_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.Object_X_IP = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.Policy_ID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, policyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<String> emails = new ArrayList<>();
                while (rs.next()) {
                    String email = rs.getString("ownerEmail");
                    if (email != null && !email.trim().isEmpty()) {
                        emails.add(email);
                    }
                }
                if (emails.isEmpty()) {
                    return null;
                }
                return String.join(", ", emails);
            }
        }
    }
    
    public boolean savePolicyRelationships(int regulationId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, regulationId, "policy");
            
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
                    logAuditHistoryForDelete(conn, regulationId, "policy", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, regulationId, "policy", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, regulationId, "policy", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM regulation_x_policy WHERE RegulationID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, regulationId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO regulation_x_policy
                (RegulationID, PolicyID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID)
                VALUES (?, ?, ?, NOW(), NOW(), ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                    Object policyIdObj = rel.get("policyId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    if (policyIdObj == null || relationTypeObj == null) {
                        continue;
                    }
                    
                    int policyId = ((Number) policyIdObj).intValue();
                    int relationType = ((Number) relationTypeObj).intValue();
                    
                    String key = policyId + "_" + relationType;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);
                    
                    ps.setInt(1, regulationId);
                    ps.setInt(2, policyId);
                    ps.setInt(3, relationType);
                    ps.setInt(4, userId);
                    ps.addBatch();
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
    
    // ===== PROJECT RELATIONSHIPS =====
    
    public List<Map<String, Object>> getProjectRelationshipsByRegulationId(int regulationId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                rxp.ID as id,
                rxp.RegulationID as regulationId,
                rxp.ProjectID as projectId,
                rxp.RelationType as relationType,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.primaryName as relationTypeName,
                rt.Description as relationTypeDescription
            FROM regulation_x_project rxp
            LEFT JOIN project p ON rxp.ProjectID = p.id
            LEFT JOIN regulation_x_project_relationtype rt ON rxp.RelationType = rt.ID
            WHERE rxp.RegulationID = ?
            ORDER BY p.primaryname
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, regulationId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer projectId = rs.getObject("projectId") != null ? rs.getInt("projectId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("regulationId", rs.getInt("regulationId"));
                    relationship.put("projectId", projectId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    // Get project owners
                    if (projectId != null) {
                        try {
                            String ownersName = getProjectOwnersString(projectId);
                            String ownersEmail = getProjectOwnersEmail(projectId);
                            relationship.put("projectOwnerName", ownersName);
                            relationship.put("projectOwnerEmail", ownersEmail);
                        } catch (Exception e) {
                            //system.out.println("RegulationImpactDAO: Error getting project owners: " + e.getMessage());
                            relationship.put("projectOwnerName", "No owner");
                            relationship.put("projectOwnerEmail", null);
                        }
                    } else {
                        relationship.put("projectOwnerName", null);
                        relationship.put("projectOwnerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    public List<Map<String, Object>> getProjectRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID as id, primaryName as primaryname, Description as description, Priority as priority, ReverseName as reversename
            FROM regulation_x_project_relationtype
            WHERE DeleteDatetime IS NULL
            ORDER BY Priority, primaryName
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
    
    public String getProjectOwnersString(int projectId) throws SQLException {
        String sql = """
            SELECT CONCAT_WS(' ', pe.First_Name, pe.Last_Name) as ownerName
            FROM project_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.project_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<String> owners = new ArrayList<>();
                while (rs.next()) {
                    String ownerName = rs.getString("ownerName");
                    if (ownerName != null && !ownerName.trim().isEmpty()) {
                        owners.add(ownerName);
                    }
                }
                if (owners.isEmpty()) {
                    return "No owner";
                }
                return String.join(", ", owners);
            }
        }
    }
    
    public String getProjectOwnersEmail(int projectId) throws SQLException {
        String sql = """
            SELECT pe.Email as ownerEmail
            FROM project_x_objectxpeople pxop
            LEFT JOIN object_x_people oxp ON pxop.object_x_ip = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE pxop.project_id = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
            ORDER BY pe.Last_Name, pe.First_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<String> emails = new ArrayList<>();
                while (rs.next()) {
                    String email = rs.getString("ownerEmail");
                    if (email != null && !email.trim().isEmpty()) {
                        emails.add(email);
                    }
                }
                if (emails.isEmpty()) {
                    return null;
                }
                return String.join(", ", emails);
            }
        }
    }
    
    public boolean saveProjectRelationships(int regulationId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, regulationId, "project");
            
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
                    logAuditHistoryForDelete(conn, regulationId, "project", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, regulationId, "project", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, regulationId, "project", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM regulation_x_project WHERE RegulationID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, regulationId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO regulation_x_project
                (RegulationID, ProjectID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID)
                VALUES (?, ?, ?, NOW(), NOW(), ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                    Object projectIdObj = rel.get("projectId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    if (projectIdObj == null || relationTypeObj == null) {
                        continue;
                    }
                    
                    int projectId = ((Number) projectIdObj).intValue();
                    int relationType = ((Number) relationTypeObj).intValue();
                    
                    String key = projectId + "_" + relationType;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);
                    
                    ps.setInt(1, regulationId);
                    ps.setInt(2, projectId);
                    ps.setInt(3, relationType);
                    ps.setInt(4, userId);
                    ps.addBatch();
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
    
    // ===== REGULATORY THEME RELATIONSHIPS =====
    
    public List<Map<String, Object>> getRegulatoryThemeRelationshipsByRegulationId(int regulationId) throws SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        String sql = """
            SELECT 
                rxr.ID as id,
                rxr.Regulation_ID as regulationId,
                rxr.RegulatoryTheme_ID as regulatoryThemeId,
                rxr.RelationType as relationType,
                rt.PrimaryName as regulatoryThemeName,
                rt.RefNumber as regulatoryThemeRefNumber,
                rtt.PrimaryName as relationTypeName,
                rtt.Description as relationTypeDescription
            FROM regulation_x_regulatorytheme rxr
            LEFT JOIN regulatorytheme rt ON rxr.RegulatoryTheme_ID = rt.ID
            LEFT JOIN regulation_x_regulatorytheme_relationtype rtt ON rxr.RelationType = rtt.ID
            WHERE rxr.Regulation_ID = ?
            ORDER BY rt.PrimaryName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, regulationId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer regulatoryThemeId = rs.getObject("regulatoryThemeId") != null ? rs.getInt("regulatoryThemeId") : null;
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("regulationId", rs.getInt("regulationId"));
                    relationship.put("regulatoryThemeId", regulatoryThemeId);
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("regulatoryThemeName", rs.getString("regulatoryThemeName"));
                    relationship.put("regulatoryThemeRefNumber", rs.getString("regulatoryThemeRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    public List<Map<String, Object>> getRegulatoryThemeRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID as id, PrimaryName as primaryname, Description as description, Priority as priority, ReverseName as reversename
            FROM regulation_x_regulatorytheme_relationtype
            WHERE DeleteDatetime IS NULL
            ORDER BY Priority, PrimaryName
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
    
    public boolean saveRegulatoryThemeRelationships(int regulationId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, regulationId, "regulatorytheme");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object regulatoryThemeIdObj = rel.get("regulatoryThemeId");
                    Object relationTypeObj = rel.get("relationType");
                    if (regulatoryThemeIdObj != null && relationTypeObj != null) {
                        int regulatoryThemeId = ((Number) regulatoryThemeIdObj).intValue();
                        int relationType = ((Number) relationTypeObj).intValue();
                        if (regulatoryThemeId > 0 && relationType > 0) {
                            newRelationshipsMap.put(regulatoryThemeId, relationType);
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
                    logAuditHistoryForDelete(conn, regulationId, "regulatorytheme", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, regulationId, "regulatorytheme", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, regulationId, "regulatorytheme", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM regulation_x_regulatorytheme WHERE Regulation_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, regulationId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            Set<String> seen = new HashSet<>();
            String insertSql = """
                INSERT INTO regulation_x_regulatorytheme
                (Regulation_ID, RegulatoryTheme_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID)
                VALUES (?, ?, ?, NOW(), NOW(), ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                if (relationships != null) {
                    for (Map<String, Object> rel : relationships) {
                    Object regulatoryThemeIdObj = rel.get("regulatoryThemeId");
                    Object relationTypeObj = rel.get("relationType");
                    
                    if (regulatoryThemeIdObj == null || relationTypeObj == null) {
                        continue;
                    }
                    
                    int regulatoryThemeId = ((Number) regulatoryThemeIdObj).intValue();
                    int relationType = ((Number) relationTypeObj).intValue();
                    
                    String key = regulatoryThemeId + "_" + relationType;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);
                    
                    ps.setInt(1, regulationId);
                    ps.setInt(2, regulatoryThemeId);
                    ps.setInt(3, relationType);
                    ps.setInt(4, userId);
                    ps.addBatch();
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
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get next audit ID for regulation_audit_history table
     */
    private int getNextAuditId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(auditidpk), 0) + 1 FROM regulation_audit_history";
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
    private Map<Integer, Integer> getExistingRelationshipsMap(Connection conn, int regulationId, String tableType) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = null;
        String entityColumn = null;
        
        switch (tableType.toLowerCase()) {
            case "product":
                sql = "SELECT ProductID, RelationType FROM regulation_x_product WHERE RegulationID = ?";
                entityColumn = "ProductID";
                break;
            case "policy":
                sql = "SELECT PolicyID, RelationType FROM regulation_x_policy WHERE RegulationID = ?";
                entityColumn = "PolicyID";
                break;
            case "project":
                sql = "SELECT ProjectID, RelationType FROM regulation_x_project WHERE RegulationID = ?";
                entityColumn = "ProjectID";
                break;
            case "regulatorytheme":
                sql = "SELECT RegulatoryTheme_ID, RelationType FROM regulation_x_regulatorytheme WHERE Regulation_ID = ?";
                entityColumn = "RegulatoryTheme_ID";
                break;
            default:
                return existingRelationships;
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
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
            case "product":
                sql = "SELECT primaryname FROM product WHERE id = ?";
                columnName = "primaryname";
                break;
            case "policy":
                sql = "SELECT PrimaryName FROM policy WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "project":
                sql = "SELECT primaryname FROM project WHERE id = ?";
                columnName = "primaryname";
                break;
            case "regulatorytheme":
                sql = "SELECT PrimaryName FROM regulatorytheme WHERE ID = ?";
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
            case "product":
                tableName = "regulation_x_product_relationtype";
                break;
            case "policy":
                tableName = "regulation_x_policy_relationtype";
                break;
            case "project":
                tableName = "regulation_x_project_relationtype";
                break;
            case "regulatorytheme":
                tableName = "regulation_x_regulatorytheme_relationtype";
                break;
            default:
                System.err.println("Unknown table type: " + tableType);
                return null;
        }
        
        String columnName = "product".equalsIgnoreCase(tableType) || "policy".equalsIgnoreCase(tableType) || "project".equalsIgnoreCase(tableType) 
            ? "primaryName" : "PrimaryName";
        
        String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(columnName);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting relation type name for " + tableType + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Log audit history for insert operations in relationship tables
     */
    private void logAuditHistoryForInsert(Connection conn, int regulationId, String tableType, 
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
            
            // Determine object name and entity field name based on table type
            String objectName = null;
            String entityFieldName = null;
            switch (tableType.toLowerCase()) {
                case "product":
                    objectName = "Regulation X Product";
                    entityFieldName = "Product";
                    break;
                case "policy":
                    objectName = "Regulation X Policy";
                    entityFieldName = "Policy";
                    break;
                case "project":
                    objectName = "Regulation X Project";
                    entityFieldName = "Project";
                    break;
                case "regulatorytheme":
                    objectName = "Regulation X Regulatory Theme";
                    entityFieldName = "Regulatory Theme";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int nextAuditId = getNextAuditId(conn);
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO regulation_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, regulationId);
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
                int nextAuditId2 = getNextAuditId(conn);
                ps.setInt(1, regulationId);
                ps.setInt(2, nextAuditId2);
                ps.setString(3, objectName);
                ps.setString(4, "Links");
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
    private void logAuditHistoryForDelete(Connection conn, int regulationId, String tableType, 
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
            
            // Determine object name and entity field name based on table type
            String objectName = null;
            String entityFieldName = null;
            switch (tableType.toLowerCase()) {
                case "product":
                    objectName = "Regulation X Product";
                    entityFieldName = "Product";
                    break;
                case "policy":
                    objectName = "Regulation X Policy";
                    entityFieldName = "Policy";
                    break;
                case "project":
                    objectName = "Regulation X Project";
                    entityFieldName = "Project";
                    break;
                case "regulatorytheme":
                    objectName = "Regulation X Regulatory Theme";
                    entityFieldName = "Regulatory Theme";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int nextAuditId = getNextAuditId(conn);
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO regulation_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, regulationId);
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
                int nextAuditId2 = getNextAuditId(conn);
                ps.setInt(1, regulationId);
                ps.setInt(2, nextAuditId2);
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
    private void logAuditHistoryForUpdate(Connection conn, int regulationId, String tableType, 
                                         int entityId, int oldRelationTypeId, int newRelationTypeId, int userId) {
        try {
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
                    objectName = "Regulation X Product";
                    break;
                case "policy":
                    objectName = "Regulation X Policy";
                    break;
                case "project":
                    objectName = "Regulation X Project";
                    break;
                case "regulatorytheme":
                    objectName = "Regulation X Regulatory Theme";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Get next audit ID
            int nextAuditId = getNextAuditId(conn);
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO regulation_audit_history 
                (id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, regulationId);
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
}

