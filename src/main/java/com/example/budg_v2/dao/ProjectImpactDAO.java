package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class ProjectImpactDAO {
    
    // ==================== SYSTEM SUB-TAB ====================
    
    /**
     * Get system relationships by project ID
     */
    public List<Map<String, Object>> getSystemRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxs.id,
                pxs.projectid,
                pxs.systemid,
                pxs.relationtype,
                pxs.description,
                s.name as systemName,
                rt.primaryname as relationTypeName
            FROM project_x_system pxs
            LEFT JOIN system s ON pxs.systemid = s.id AND s.Deleted_Datetime IS NULL
            LEFT JOIN project_x_system_relationtype rt ON pxs.relationtype = rt.id
            WHERE pxs.projectid = ? AND s.id IS NOT NULL
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("systemId", rs.getInt("systemid"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("relationTypeId", rs.getObject("relationtype"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    // Get system owner
                    int systemId = rs.getInt("systemid");
                    relationship.put("systemOwnerName", getSystemOwnersString(systemId));
                    relationship.put("ownerName", getSystemOwnersString(systemId)); // Keep for backward compatibility
                    relationship.put("ownerEmail", getSystemOwnersEmail(systemId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
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
     * Get system relation types
     */
    public List<Map<String, Object>> getSystemRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description 
            FROM project_x_system_relationtype 
            WHERE deleteddatetime IS NULL
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryname"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Save system relationships
     */
    public boolean saveSystemRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        //system.out.println("=== ProjectImpactDAO.saveSystemRelationships START ===");
        //system.out.println("Project ID: " + projectId);
        //system.out.println("User ID: " + userId);
        //system.out.println("Relationships count: " + (relationships != null ? relationships.size() : 0));
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "system");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object systemIdObj = rel.get("systemId");
                    Object relationTypeObj = rel.get("relationTypeId");
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
                    logAuditHistoryForDelete(conn, projectId, "system", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "system", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "system", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM project_x_system WHERE projectid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO project_x_system 
                    (projectid, systemid, relationtype, description, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        //system.out.println("Processing relationship: " + rel);
                        Object systemIdObj = rel.get("systemId");
                        Object relationTypeObj = rel.get("relationTypeId");
                        
                        //system.out.println("systemId: " + systemIdObj + ", relationTypeId: " + relationTypeObj);
                        
                        if (systemIdObj != null && relationTypeObj != null) {
                            int systemId = ((Number) systemIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = systemId + "_" + relationType;
                            if (!uniqueKeys.add(uniqueKey)) {
                                //system.out.println("Skipping duplicate: " + uniqueKey);
                                continue;
                            }
                            
                            ps.setInt(1, projectId);
                            ps.setInt(2, systemId);
                            ps.setInt(3, relationType);
                            ps.setString(4, (String) rel.get("description"));
                            ps.setInt(5, userId);
                            ps.addBatch();
                        } else {
                            //system.out.println("Skipping relationship - missing systemId or relationTypeId");
                        }
                    }
                    int[] results = ps.executeBatch();
                    //system.out.println("Batch executed. Inserted " + results.length + " rows");
                    for (int i = 0; i < results.length; i++) {
                        //system.out.println("Batch result " + i + ": " + results[i]);
                    }
                }
            } else {
                //system.out.println("No relationships to insert");
            }
            
            conn.commit();
            //system.out.println("=== ProjectImpactDAO.saveSystemRelationships SUCCESS ===");
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
    
    // ==================== PROCESS SUB-TAB ====================
    
    /**
     * Get process relationships by project ID
     */
    public List<Map<String, Object>> getProcessRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.projectid,
                pxp.process_id,
                pxp.relationtype,
                pxp.description,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                rt.primaryname as relationTypeName
            FROM project_x_process pxp
            LEFT JOIN process p ON pxp.process_id = p.id
            LEFT JOIN project_x_process_relationtype rt ON pxp.relationtype = rt.id
            WHERE pxp.projectid = ?
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("processId", rs.getInt("process_id"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("relationTypeId", rs.getObject("relationtype"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    // Get process owner
                    int processId = rs.getInt("process_id");
                    relationship.put("processOwnerName", getProcessOwnersString(processId));
                    relationship.put("ownerName", getProcessOwnersString(processId)); // Keep for backward compatibility
                    relationship.put("ownerEmail", getProcessOwnersEmail(processId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
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
     * Get process relation types
     */
    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        String sql = """
            SELECT id, primaryname, description 
            FROM project_x_process_relationtype 
            WHERE deleteddatetime IS NULL
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryname"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Save process relationships
     */
    public boolean saveProcessRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "process");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object processIdObj = rel.get("processId");
                    Object relationTypeObj = rel.get("relationTypeId");
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
                    logAuditHistoryForDelete(conn, projectId, "process", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "process", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "process", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM project_x_process WHERE projectid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO project_x_process 
                    (projectid, process_id, relationtype, description, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object processIdObj = rel.get("processId");
                        Object relationTypeObj = rel.get("relationTypeId");
                        
                        if (processIdObj != null && relationTypeObj != null) {
                            int processId = ((Number) processIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = processId + "_" + relationType;
                            if (!uniqueKeys.add(uniqueKey)) {
                                continue;
                            }
                            
                            ps.setInt(1, projectId);
                            ps.setInt(2, processId);
                            ps.setInt(3, relationType);
                            ps.setString(4, (String) rel.get("description"));
                            ps.setInt(5, userId);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
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
    
    // ==================== GLOSSARY SUB-TAB ====================
    
    /**
     * Get glossary relationships by project ID
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                gxp.ID as id,
                gxp.Project_ID as projectId,
                gxp.Glossary_ID as glossaryId,
                gxp.RelationType as relationTypeId,
                gxp.Description as description,
                g.Name as glossaryName,
                g.Type as glossaryType,
                gt.Name as glossaryTypeName,
                rt.PrimaryName as relationTypeName
            FROM glossary_x_project gxp
            LEFT JOIN glossary g ON gxp.Glossary_ID = g.ID
            LEFT JOIN glossary_type gt ON g.Type = gt.ID
            LEFT JOIN glossary_x_project_relationtype rt ON gxp.RelationType = rt.ID
            WHERE gxp.Project_ID = ?
                AND (g.Deleted_datetime IS NULL OR g.ID IS NULL)
                AND (rt.DeletedDatetime IS NULL OR rt.ID IS NULL)
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectId"));
                    relationship.put("glossaryId", rs.getInt("glossaryId"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryType", rs.getObject("glossaryType"));
                    relationship.put("glossaryTypeName", rs.getString("glossaryTypeName"));
                    relationship.put("relationTypeId", rs.getObject("relationTypeId"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    // Get glossary owner
                    int glossaryId = rs.getInt("glossaryId");
                    relationship.put("glossaryOwnerName", getGlossaryOwnersString(glossaryId));
                    relationship.put("ownerName", getGlossaryOwnersString(glossaryId)); // Keep for backward compatibility
                    relationship.put("ownerEmail", getGlossaryOwnersEmail(glossaryId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
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
     * Get glossary relation types
     */
    public List<Map<String, Object>> getGlossaryRelationTypes() throws SQLException {
        String sql = """
            SELECT ID as id, PrimaryName as primaryName, Description as description 
            FROM glossary_x_project_relationtype 
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryName"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Save glossary relationships
     */
    public boolean saveGlossaryRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "glossary");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object glossaryIdObj = rel.get("glossaryId");
                    Object relationTypeObj = rel.get("relationTypeId");
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
                    logAuditHistoryForDelete(conn, projectId, "glossary", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "glossary", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "glossary", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM glossary_x_project WHERE Project_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO glossary_x_project 
                    (Project_ID, Glossary_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object glossaryIdObj = rel.get("glossaryId");
                        Object relationTypeObj = rel.get("relationTypeId");
                        
                        if (glossaryIdObj != null && relationTypeObj != null) {
                            int glossaryId = ((Number) glossaryIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = glossaryId + "_" + relationType;
                            if (!uniqueKeys.add(uniqueKey)) {
                                continue;
                            }
                            
                            ps.setInt(1, projectId);
                            ps.setInt(2, glossaryId);
                            ps.setInt(3, relationType);
                            ps.setString(4, (String) rel.get("description"));
                            ps.setInt(5, userId);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
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
    
    // ==================== POLICY SUB-TAB ====================
    
    /**
     * Get policy relationships by project ID
     */
    public List<Map<String, Object>> getPolicyRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.project_id as projectId,
                pxp.policy_id as policyId,
                pxp.relation_type as relationTypeId,
                pxp.description,
                p.PrimaryName as policyName,
                p.RefNumber as policyRefNumber,
                p.Policy_Type as policyTypeId,
                pt.PrimaryName as policyTypeName,
                rt.PrimaryName as relationTypeName
            FROM policy_x_project pxp
            LEFT JOIN policy p ON pxp.policy_id = p.ID
            LEFT JOIN policy_type pt ON p.Policy_Type = pt.ID
            LEFT JOIN policy_x_project_relationtype rt ON pxp.relation_type = rt.ID
            WHERE pxp.project_id = ?
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectId"));
                    relationship.put("policyId", rs.getInt("policyId"));
                    relationship.put("policyName", rs.getString("policyName"));
                    relationship.put("policyRefNumber", rs.getString("policyRefNumber"));
                    relationship.put("policyTypeId", rs.getObject("policyTypeId"));
                    relationship.put("policyTypeName", rs.getString("policyTypeName"));
                    relationship.put("relationTypeId", rs.getObject("relationTypeId"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    // Get policy owner
                    int policyId = rs.getInt("policyId");
                    relationship.put("policyOwnerName", getPolicyOwnersString(policyId));
                    relationship.put("ownerName", getPolicyOwnersString(policyId)); // Keep for backward compatibility
                    relationship.put("ownerEmail", getPolicyOwnersEmail(policyId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get policy owners string
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
     * Get policy owners email
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
     * Get policy relation types
     */
    public List<Map<String, Object>> getPolicyRelationTypes() throws SQLException {
        String sql = """
            SELECT ID as id, PrimaryName as primaryName, Description as description 
            FROM policy_x_project_relationtype 
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryName"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Save policy relationships
     */
    public boolean savePolicyRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "policy");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object policyIdObj = rel.get("policyId");
                    Object relationTypeObj = rel.get("relationTypeId");
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
                    logAuditHistoryForDelete(conn, projectId, "policy", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "policy", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "policy", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM policy_x_project WHERE project_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO policy_x_project 
                    (policy_id, project_id, relation_type, description, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object policyIdObj = rel.get("policyId");
                        Object relationTypeObj = rel.get("relationTypeId");
                        
                        if (policyIdObj != null && relationTypeObj != null) {
                            int policyId = ((Number) policyIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = policyId + "_" + relationType;
                            if (!uniqueKeys.add(uniqueKey)) {
                                continue;
                            }
                            
                            ps.setInt(1, policyId);
                            ps.setInt(2, projectId);
                            ps.setInt(3, relationType);
                            ps.setString(4, (String) rel.get("description"));
                            ps.setInt(5, userId);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
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
    
    // ==================== PRODUCT SUB-TAB ====================
    
    /**
     * Get product relationships by project ID
     */
    public List<Map<String, Object>> getProductRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.productid,
                pxp.projectid,
                pxp.relationtype,
                pxp.description,
                p.primaryname as productName,
                p.refnumber as productRefNumber,
                rt.primaryname as relationTypeName
            FROM product_x_project pxp
            LEFT JOIN product p ON pxp.productid = p.id
            LEFT JOIN product_x_project_relationtype rt ON pxp.relationtype = rt.ID
            WHERE pxp.projectid = ?
                AND (p.deleteddatetime IS NULL OR p.id IS NULL)
                AND (rt.deleteddatetime IS NULL OR rt.ID IS NULL)
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("productId", rs.getInt("productid"));
                    relationship.put("productName", rs.getString("productName"));
                    relationship.put("productRefNumber", rs.getString("productRefNumber"));
                    relationship.put("relationTypeId", rs.getObject("relationtype"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    // Get product owner
                    int productId = rs.getInt("productid");
                    relationship.put("productOwnerName", getProductOwnersString(productId));
                    relationship.put("ownerName", getProductOwnersString(productId)); // Keep for backward compatibility
                    relationship.put("ownerEmail", getProductOwnersEmail(productId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
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
    
    private int getNextProductProjectId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM product_x_project";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    /**
     * Get product relation types
     */
    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        String sql = """
            SELECT ID as id, primaryname, description 
            FROM product_x_project_relationtype 
            WHERE deleteddatetime IS NULL
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryname"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    /**
     * Save product relationships
     */
    public boolean saveProductRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "product");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object productIdObj = rel.get("productId");
                    Object relationTypeObj = rel.get("relationTypeId");
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
                    logAuditHistoryForDelete(conn, projectId, "product", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "product", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "product", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM product_x_project WHERE projectid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            // Insert new relationships
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO product_x_project 
                    (id, productid, projectid, relationtype, description, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object productIdObj = rel.get("productId");
                        Object relationTypeObj = rel.get("relationTypeId");
                        
                        if (productIdObj != null && relationTypeObj != null) {
                            int productId = ((Number) productIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Prevent duplicates
                            String uniqueKey = productId + "_" + relationType;
                            if (!uniqueKeys.add(uniqueKey)) {
                                continue;
                            }
                            
                            int nextId = getNextProductProjectId(conn);
                            ps.setInt(1, nextId);
                            ps.setInt(2, productId);
                            ps.setInt(3, projectId);
                            ps.setInt(4, relationType);
                            ps.setString(5, (String) rel.get("description"));
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
    
    // ==================== CLIENT SUB-TAB ====================
    
    /**
     * Get client relationships by project ID
     */
    public List<Map<String, Object>> getClientRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                cxp.ID as id,
                cxp.Project_ID as projectId,
                cxp.Client_ID as clientId,
                cxp.RelationType as relationType,
                cxp.Description as description,
                c.PrimaryName as clientName,
                rt.PrimaryName as relationTypeName
            FROM client_x_project cxp
            LEFT JOIN client c ON cxp.Client_ID = c.ID
            LEFT JOIN client_x_project_relationtype rt ON cxp.RelationType = rt.ID
            WHERE cxp.Project_ID = ?
                AND (rt.DeletedDatetime IS NULL OR rt.ID IS NULL)
            ORDER BY c.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int clientId = rs.getInt("clientId");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectId"));
                    relationship.put("clientId", clientId);
                    relationship.put("clientName", rs.getString("clientName"));
                    relationship.put("relationType", rs.getInt("relationType"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    // Get client owner
                    if (clientId > 0) {
                        try {
                            String ownerName = getClientOwnersString(clientId);
                            relationship.put("clientOwnerName", ownerName);
                            relationship.put("ownerName", ownerName); // Keep for backward compatibility
                            relationship.put("ownerEmail", getClientOwnersEmail(clientId));
                        } catch (Exception e) {
                            relationship.put("clientOwnerName", "No owner");
                            relationship.put("ownerName", "No owner"); // Keep for backward compatibility
                            relationship.put("ownerEmail", null);
                        }
                    } else {
                        relationship.put("clientOwnerName", "No owner");
                        relationship.put("ownerName", "No owner"); // Keep for backward compatibility
                        relationship.put("ownerEmail", null);
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
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
     * Get client relation types
     */
    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, Description, Priority
            FROM client_x_project_relationtype 
            WHERE DeletedDatetime IS NULL
            ORDER BY COALESCE(Priority, 9999), PrimaryName
        """;
        
        //system.out.println("ProjectImpactDAO: Executing query: " + sql);
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            //system.out.println("ProjectImpactDAO: Query executed successfully");
            int rowCount = 0;
            while (rs.next()) {
                rowCount++;
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryname", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));
                relationTypes.add(relationType);
                //system.out.println("ProjectImpactDAO: Found relation type - ID: " + rs.getInt("ID") + ", Name: " + rs.getString("PrimaryName"));
            }
            //system.out.println("ProjectImpactDAO: Total rows found: " + rowCount);
            
            // If no rows found, try a query without the WHERE clause to see if data exists
            if (rowCount == 0) {
                //system.out.println("ProjectImpactDAO: No rows found with DeletedDatetime IS NULL, checking if table has any data...");
                String checkSql = "SELECT COUNT(*) as total FROM client_x_project_relationtype";
                try (PreparedStatement checkPs = conn.prepareStatement(checkSql);
                     ResultSet checkRs = checkPs.executeQuery()) {
                    if (checkRs.next()) {
                        int total = checkRs.getInt("total");
                        //system.out.println("ProjectImpactDAO: Total rows in table (including deleted): " + total);
                        
                        if (total > 0) {
                            // Check what DeletedDatetime values exist
                            String checkDeletedSql = "SELECT ID, PrimaryName, DeletedDatetime FROM client_x_project_relationtype LIMIT 5";
                            try (PreparedStatement checkDeletedPs = conn.prepareStatement(checkDeletedSql);
                                 ResultSet checkDeletedRs = checkDeletedPs.executeQuery()) {
                                //system.out.println("ProjectImpactDAO: Sample rows:");
                                while (checkDeletedRs.next()) {
                                    //system.out.println("  ID: " + checkDeletedRs.getInt("ID") +
                                                 //    ", Name: " + checkDeletedRs.getString("PrimaryName") +
                                                  //   ", DeletedDatetime: " + checkDeletedRs.getString("DeletedDatetime"));
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("ProjectImpactDAO: SQLException in getClientRelationTypes: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        
        return relationTypes;
    }
    
    /**
     * Save client relationships
     */
    public boolean saveClientRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        // Validate input
        if (projectId <= 0) {
            throw new IllegalArgumentException("Invalid project ID");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get existing relationships before deleting (to compare with new ones)
                Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "client");
                
                // Build new relationships map
                Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
                if (relationships != null && !relationships.isEmpty()) {
                    for (Map<String, Object> rel : relationships) {
                        Object relationTypeObj = rel.get("relationType") != null ? 
                            rel.get("relationType") : rel.get("relationTypeId");
                        Object clientIdObj = rel.get("clientId");
                        
                        if (clientIdObj != null && relationTypeObj != null) {
                            Integer clientId = null;
                            Integer relationType = null;
                            
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
                
                // Detect changes: DELETE, UPDATE, INSERT
                // DELETE: entities in existing but not in new
                for (Map.Entry<Integer, Integer> entry : existingRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int oldRelationType = entry.getValue();
                    Integer newRelationType = newRelationshipsMap.get(entityId);
                    
                    if (newRelationType == null) {
                        // Entity was deleted
                        logAuditHistoryForDelete(conn, projectId, "client", entityId, oldRelationType, userId);
                    } else if (newRelationType != oldRelationType) {
                        // Relation type was updated
                        logAuditHistoryForUpdate(conn, projectId, "client", entityId, oldRelationType, newRelationType, userId);
                    }
                }
                
                // INSERT: entities in new but not in existing
                for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                    int entityId = entry.getKey();
                    int newRelationType = entry.getValue();
                    
                    if (!existingRelationshipsMap.containsKey(entityId)) {
                        // New entity added
                        logAuditHistoryForInsert(conn, projectId, "client", entityId, newRelationType, userId);
                    }
                }
                
                // First, delete all existing relationships for this project
                String deleteSql = "DELETE FROM client_x_project WHERE Project_ID = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, projectId);
                    ps.executeUpdate();
                }
                
                // Then insert new relationships
                if (relationships != null && !relationships.isEmpty()) {
                    String insertSql = """
                        INSERT INTO client_x_project 
                        (Project_ID, Client_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) 
                        VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                    """;
                    
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        // Track unique combinations to prevent duplicates
                        Set<String> seenRelationships = new HashSet<>();
                        
                        for (Map<String, Object> relationship : relationships) {
                            // Check for both "relationType" and "relationTypeId" to handle different formats
                            Object relationTypeObj = relationship.get("relationType") != null ? 
                                relationship.get("relationType") : relationship.get("relationTypeId");
                            
                            if (relationship.get("clientId") != null && relationTypeObj != null) {
                                // Handle both Integer and Double types from JSON parsing
                                Integer clientId = null;
                                Integer relationType = null;
                                
                                Object clientIdObj = relationship.get("clientId");
                                
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
                                        continue;
                                    }
                                    seenRelationships.add(uniqueKey);
                                    
                                    ps.setInt(1, projectId);
                                    ps.setInt(2, clientId);
                                    ps.setInt(3, relationType);
                                    ps.setString(4, (String) relationship.get("description"));
                                    ps.setInt(5, userId);
                                    ps.addBatch();
                                }
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
            }
        }
    }
    
    // ==================== CAPABILITY SUB-TAB ====================
    
    public List<Map<String, Object>> getCapabilityRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxc.ID as id,
                pxc.Project_ID as projectId,
                pxc.Capability_ID as capabilityId,
                pxc.RelationType as relationTypeId,
                pxc.Description as description,
                c.PrimaryName as capabilityName,
                rt.PrimaryName as relationTypeName
            FROM project_x_capability pxc
            LEFT JOIN capability c ON pxc.Capability_ID = c.ID
            LEFT JOIN project_x_capability_relationtype rt ON pxc.RelationType = rt.ID
            WHERE pxc.Project_ID = ?
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectId"));
                    relationship.put("capabilityId", rs.getInt("capabilityId"));
                    relationship.put("capabilityName", rs.getString("capabilityName"));
                    relationship.put("relationTypeId", rs.getObject("relationTypeId"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    // Get capability owner
                    int capabilityId = rs.getInt("capabilityId");
                    relationship.put("ownerName", getCapabilityOwnersString(capabilityId));
                    relationship.put("ownerEmail", getCapabilityOwnersEmail(capabilityId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
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
                
                return ownersList.length() > 0 ? ownersList.toString() : "No owner";
            }
        }
    }
    
    public String getCapabilityOwnersEmail(int capabilityId) throws SQLException {
        String sql = """
            SELECT 
                pe.Email
            FROM capability_x_objectxpeople cxop
            LEFT JOIN object_x_people oxp ON cxop.Object_x_ipid = oxp.id
            LEFT JOIN people pe ON oxp.ipid = pe.ID
            LEFT JOIN object_role r ON oxp.RoleID = r.ID
            WHERE cxop.CapabilityID = ? AND (r.primaryname LIKE '%Owner' OR r.id IS NULL)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, capabilityId);
            
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
    
    public List<Map<String, Object>> getCapabilityRelationTypes() throws SQLException {
        String sql = """
            SELECT ID as id, PrimaryName as primaryName, Description as description 
            FROM project_x_capability_relationtype 
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryName"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    public boolean saveCapabilityRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "capability");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object capIdObj = rel.get("capabilityId");
                    Object relTypeObj = rel.get("relationTypeId");
                    if (capIdObj != null && relTypeObj != null) {
                        int capId = ((Number) capIdObj).intValue();
                        int relType = ((Number) relTypeObj).intValue();
                        if (capId > 0 && relType > 0) {
                            newRelationshipsMap.put(capId, relType);
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
                    logAuditHistoryForDelete(conn, projectId, "capability", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "capability", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "capability", entityId, newRelationType, userId);
                }
            }
            
            String deleteSql = "DELETE FROM project_x_capability WHERE Project_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO project_x_capability 
                    (Project_ID, Capability_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object capIdObj = rel.get("capabilityId");
                        Object relTypeObj = rel.get("relationTypeId");
                        
                        if (capIdObj != null && relTypeObj != null) {
                            int capId = ((Number) capIdObj).intValue();
                            int relType = ((Number) relTypeObj).intValue();
                            
                            String uniqueKey = capId + "_" + relType;
                            if (!uniqueKeys.add(uniqueKey)) {
                                continue;
                            }
                            
                            ps.setInt(1, projectId);
                            ps.setInt(2, capId);
                            ps.setInt(3, relType);
                            ps.setString(4, (String) rel.get("description"));
                            ps.setInt(5, userId);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
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
    
    // ==================== BUSINESS AREA SUB-TAB ====================
    
    public List<Map<String, Object>> getBusinessAreaRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxb.ID as id,
                pxb.Project_ID as projectId,
                pxb.BusinessArea_ID as businessAreaId,
                pxb.RelationType as relationTypeId,
                pxb.Description as description,
                ba.PrimaryName as businessAreaName,
                rt.PrimaryName as relationTypeName
            FROM project_x_businessarea pxb
            LEFT JOIN business_area ba ON pxb.BusinessArea_ID = ba.ID
            LEFT JOIN project_x_businessarea_relationtype rt ON pxb.RelationType = rt.ID
            WHERE pxb.Project_ID = ?
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectId"));
                    relationship.put("businessAreaId", rs.getInt("businessAreaId"));
                    relationship.put("businessAreaName", rs.getString("businessAreaName"));
                    relationship.put("relationTypeId", rs.getObject("relationTypeId"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    int businessAreaId = rs.getInt("businessAreaId");
                    relationship.put("ownerName", getBusinessAreaOwnersString(businessAreaId));
                    relationship.put("ownerEmail", getBusinessAreaOwnersEmail(businessAreaId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
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
    
    public List<Map<String, Object>> getBusinessAreaRelationTypes() throws SQLException {
        String sql = """
            SELECT ID as id, PrimaryName as primaryName, Description as description 
            FROM project_x_businessarea_relationtype 
            WHERE DeletedDatetime IS NULL
            ORDER BY Priority, PrimaryName
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryName"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    public boolean saveBusinessAreaRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "businessarea");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object baIdObj = rel.get("businessAreaId");
                    Object relTypeObj = rel.get("relationTypeId");
                    if (baIdObj != null && relTypeObj != null) {
                        int baId = ((Number) baIdObj).intValue();
                        int relType = ((Number) relTypeObj).intValue();
                        if (baId > 0 && relType > 0) {
                            newRelationshipsMap.put(baId, relType);
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
                    logAuditHistoryForDelete(conn, projectId, "businessarea", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "businessarea", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "businessarea", entityId, newRelationType, userId);
                }
            }
            
            String deleteSql = "DELETE FROM project_x_businessarea WHERE Project_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO project_x_businessarea 
                    (Project_ID, BusinessArea_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object baIdObj = rel.get("businessAreaId");
                        Object relTypeObj = rel.get("relationTypeId");
                        
                        if (baIdObj != null && relTypeObj != null) {
                            int baId = ((Number) baIdObj).intValue();
                            int relType = ((Number) relTypeObj).intValue();
                            
                            String uniqueKey = baId + "_" + relType;
                            if (!uniqueKeys.add(uniqueKey)) {
                                continue;
                            }
                            
                            ps.setInt(1, projectId);
                            ps.setInt(2, baId);
                            ps.setInt(3, relType);
                            ps.setString(4, (String) rel.get("description"));
                            ps.setInt(5, userId);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
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
    
    // ==================== DATASET SUB-TAB ====================
    
    public List<Map<String, Object>> getDatasetRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxd.id,
                pxd.projectid,
                pxd.dataset_id as datasetId,
                pxd.relationtype as relationTypeId,
                pxd.description,
                d.PrimaryName as datasetName,
                d.RefNumber as datasetRefNumber,
                d.MasterSource as systemId,
                s.name as systemName,
                rt.primaryname as relationTypeName
            FROM project_x_dataset pxd
            LEFT JOIN dataset d ON pxd.dataset_id = d.ID
            LEFT JOIN system s ON d.MasterSource = s.id AND s.Deleted_Datetime IS NULL
            LEFT JOIN project_x_dataset_relationtype rt ON pxd.relationtype = rt.ID
            WHERE pxd.projectid = ? AND (d.MasterSource IS NULL OR s.id IS NOT NULL)
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("datasetId", rs.getInt("datasetId"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                    relationship.put("systemId", rs.getObject("systemId"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("relationTypeId", rs.getObject("relationTypeId"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    int datasetId = rs.getInt("datasetId");
                    relationship.put("datasetOwnerName", getDatasetOwnersString(datasetId));
                    relationship.put("datasetOwnerEmail", getDatasetOwnersEmail(datasetId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
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
    
    private int getNextProjectDatasetId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM project_x_dataset";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    public List<Map<String, Object>> getDatasetRelationTypes() throws SQLException {
        String sql = """
            SELECT ID as id, primaryname, description 
            FROM project_x_dataset_relationtype 
            WHERE deleteddatetime = lastupdatedatetime
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryname"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    public boolean saveDatasetRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "dataset");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object dsIdObj = rel.get("datasetId");
                    Object relTypeObj = rel.get("relationTypeId");
                    if (dsIdObj != null && relTypeObj != null) {
                        int dsId = ((Number) dsIdObj).intValue();
                        int relType = ((Number) relTypeObj).intValue();
                        if (dsId > 0 && relType > 0) {
                            newRelationshipsMap.put(dsId, relType);
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
                    logAuditHistoryForDelete(conn, projectId, "dataset", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "dataset", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "dataset", entityId, newRelationType, userId);
                }
            }
            
            String deleteSql = "DELETE FROM project_x_dataset WHERE projectid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO project_x_dataset 
                    (id, projectid, dataset_id, relationtype, description, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object dsIdObj = rel.get("datasetId");
                        Object relTypeObj = rel.get("relationTypeId");
                        
                        if (dsIdObj != null && relTypeObj != null) {
                            int dsId = ((Number) dsIdObj).intValue();
                            int relType = ((Number) relTypeObj).intValue();
                            
                            String uniqueKey = dsId + "_" + relType;
                            if (!uniqueKeys.add(uniqueKey)) {
                                continue;
                            }
                            
                            int nextId = getNextProjectDatasetId(conn);
                            ps.setInt(1, nextId);
                            ps.setInt(2, projectId);
                            ps.setInt(3, dsId);
                            ps.setInt(4, relType);
                            ps.setString(5, (String) rel.get("description"));
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
    
    // ==================== DATA ATTRIBUTES SUB-TAB ====================
    
    public List<Map<String, Object>> getAttributeRelationshipsByProjectId(int projectId) throws SQLException {
        String sql = """
            SELECT 
                pxa.id,
                pxa.projectid,
                pxa.attribute_id as attributeId,
                pxa.relationtype as relationTypeId,
                pxa.description,
                a.PrimaryName as attributeName,
                a.RefNumber as attributeRefNumber,
                a.Dataset_ID as datasetId,
                d.PrimaryName as datasetName,
                d.MasterSource as systemId,
                s.name as systemName,
                rt.primaryname as relationTypeName
            FROM project_x_attribute pxa
            LEFT JOIN attribute a ON pxa.attribute_id = a.ID
            LEFT JOIN dataset d ON a.Dataset_ID = d.ID
            LEFT JOIN system s ON d.MasterSource = s.id AND s.Deleted_Datetime IS NULL
            LEFT JOIN project_x_attribute_relationtype rt ON pxa.relationtype = rt.ID
            WHERE pxa.projectid = ? AND (d.MasterSource IS NULL OR s.id IS NOT NULL)
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, projectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("attributeId", rs.getInt("attributeId"));
                    relationship.put("attributeName", rs.getString("attributeName"));
                    relationship.put("attributeRefNumber", rs.getString("attributeRefNumber"));
                    relationship.put("datasetId", rs.getObject("datasetId"));
                    relationship.put("datasetName", rs.getString("datasetName"));
                    relationship.put("systemId", rs.getObject("systemId"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("relationTypeId", rs.getObject("relationTypeId"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("description", rs.getString("description"));
                    
                    int attributeId = rs.getInt("attributeId");
                    relationship.put("attributeOwnerName", getAttributeOwnersString(attributeId));
                    relationship.put("attributeOwnerEmail", getAttributeOwnersEmail(attributeId));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
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
    
    private int getNextProjectAttributeId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM project_x_attribute";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        }
    }
    
    public List<Map<String, Object>> getAttributeRelationTypes() throws SQLException {
        String sql = """
            SELECT ID as id, primaryname, description 
            FROM project_x_attribute_relationtype 
            WHERE deleteddatetime IS NULL
            ORDER BY priority, primaryname
        """;
        
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryName", rs.getString("primaryname"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    public boolean saveAttributeRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, projectId, "attribute");
            
            // Build new relationships map
            Map<Integer, Integer> newRelationshipsMap = new HashMap<>();
            if (relationships != null && !relationships.isEmpty()) {
                for (Map<String, Object> rel : relationships) {
                    Object attrIdObj = rel.get("attributeId");
                    // Check for both "relationType" and "relationTypeId" keys to handle different Map structures
                    Object relTypeObj = rel.get("relationType") != null ? 
                        rel.get("relationType") : rel.get("relationTypeId");
                    
                    if (attrIdObj != null && relTypeObj != null) {
                        // Add type validation before calling intValue() to prevent ClassCastException
                        if (attrIdObj instanceof Number && relTypeObj instanceof Number) {
                            try {
                                int attrId = ((Number) attrIdObj).intValue();
                                int relType = ((Number) relTypeObj).intValue();
                                if (attrId > 0 && relType > 0) {
                                    newRelationshipsMap.put(attrId, relType);
                                }
                            } catch (Exception e) {
                                System.err.println("ProjectImpactDAO: Error converting attributeId or relationType to int: " + e.getMessage());
                            }
                        } else {
                            System.err.println("ProjectImpactDAO: Invalid data types for attributeId or relationType. " +
                                "Expected Number, got attributeId: " + 
                                (attrIdObj != null ? attrIdObj.getClass().getName() : "null") +
                                ", relationType: " + 
                                (relTypeObj != null ? relTypeObj.getClass().getName() : "null"));
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
                    logAuditHistoryForDelete(conn, projectId, "attribute", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, projectId, "attribute", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, projectId, "attribute", entityId, newRelationType, userId);
                }
            }
            
            String deleteSql = "DELETE FROM project_x_attribute WHERE projectid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, projectId);
                ps.executeUpdate();
            }
            
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO project_x_attribute 
                    (id, projectid, attribute_id, relationtype, description, createdatetime, lastupdatedatetime, lastupdate_userid) 
                    VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                Set<String> uniqueKeys = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object attrIdObj = rel.get("attributeId");
                        // Check for both "relationType" and "relationTypeId" keys to handle different Map structures
                        Object relTypeObj = rel.get("relationType") != null ? 
                            rel.get("relationType") : rel.get("relationTypeId");
                        
                        if (attrIdObj != null && relTypeObj != null) {
                            // Add type validation before calling intValue() to prevent ClassCastException
                            if (attrIdObj instanceof Number && relTypeObj instanceof Number) {
                                try {
                                    int attrId = ((Number) attrIdObj).intValue();
                                    int relType = ((Number) relTypeObj).intValue();
                                    
                                    if (attrId > 0 && relType > 0) {
                                        String uniqueKey = attrId + "_" + relType;
                                        if (!uniqueKeys.add(uniqueKey)) {
                                            continue;
                                        }
                                        
                                        int nextId = getNextProjectAttributeId(conn);
                                        ps.setInt(1, nextId);
                                        ps.setInt(2, projectId);
                                        ps.setInt(3, attrId);
                                        ps.setInt(4, relType);
                                        ps.setString(5, (String) rel.get("description"));
                                        ps.setInt(6, userId);
                                        ps.addBatch();
                                    }
                                } catch (Exception e) {
                                    System.err.println("ProjectImpactDAO: Error converting attributeId or relationType to int during insert: " + e.getMessage());
                                }
                            } else {
                                System.err.println("ProjectImpactDAO: Invalid data types for attributeId or relationType during insert. " +
                                    "Expected Number, got attributeId: " + 
                                    (attrIdObj != null ? attrIdObj.getClass().getName() : "null") +
                                    ", relationType: " + 
                                    (relTypeObj != null ? relTypeObj.getClass().getName() : "null"));
                            }
                        }
                    }
                    ps.executeBatch();
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
    
    // ===== REVERSE LOOKUP METHODS =====
    
    /**
     * Get project relationships for a system (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsBySystemId(int systemId) throws SQLException {
        String sql = """
            SELECT 
                pxs.id,
                pxs.projectid,
                pxs.systemid,
                pxs.relationtype,
                pxs.description,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM project_x_system pxs
            LEFT JOIN project p ON pxs.projectid = p.id
            LEFT JOIN project_x_system_relationtype rt ON pxs.relationtype = rt.id
            WHERE pxs.systemid = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
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
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("systemId", rs.getInt("systemid"));
                    relationship.put("relationType", rs.getObject("relationtype"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relationships for a process (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.projectid,
                pxp.process_id,
                pxp.relationtype,
                pxp.description,
                p.PrimaryName as projectName,
                p.RefNumber as projectRefNumber,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM project_x_process pxp
            LEFT JOIN project proj ON pxp.projectid = proj.ID
            LEFT JOIN project_x_process_relationtype rt ON pxp.relationtype = rt.id
            WHERE pxp.process_id = ? AND (proj.deletedatetime IS NULL OR proj.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY proj.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("processId", rs.getInt("process_id"));
                    relationship.put("relationType", rs.getObject("relationtype"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relationships for a glossary (reverse lookup)
     * Note: Table is glossary_x_project (reversed)
     */
    public List<Map<String, Object>> getProjectRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                gxp.ID as id,
                gxp.Project_ID as projectId,
                gxp.Glossary_ID as glossaryId,
                gxp.RelationType as relationType,
                gxp.Description as description,
                p.PrimaryName as projectName,
                p.RefNumber as projectRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM glossary_x_project gxp
            LEFT JOIN project p ON gxp.Project_ID = p.ID
            LEFT JOIN glossary_x_project_relationtype rt ON gxp.RelationType = rt.ID
            WHERE gxp.Glossary_ID = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
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
                    relationship.put("projectId", rs.getInt("projectId"));
                    relationship.put("glossaryId", rs.getInt("glossaryId"));
                    relationship.put("relationType", rs.getObject("relationType"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relationships for a policy (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsByPolicyId(int policyId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.projectid,
                pxp.policyid,
                pxp.relationtype,
                pxp.description,
                p.PrimaryName as projectName,
                p.RefNumber as projectRefNumber,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM project_x_policy pxp
            LEFT JOIN project p ON pxp.projectid = p.ID
            LEFT JOIN project_x_policy_relationtype rt ON pxp.relationtype = rt.id
            WHERE pxp.policyid = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY p.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, policyId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("policyId", rs.getInt("policyid"));
                    relationship.put("relationType", rs.getObject("relationtype"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relationships for a product (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsByProductId(int productId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.projectid,
                pxp.productid,
                pxp.relationtype,
                pxp.description,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM product_x_project pxp
            LEFT JOIN project p ON pxp.projectid = p.id
            LEFT JOIN product_x_project_relationtype rt ON pxp.relationtype = rt.ID
            WHERE pxp.productid = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, productId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", rs.getInt("projectid"));
                    relationship.put("productId", rs.getInt("productid"));
                    relationship.put("relationType", rs.getObject("relationtype"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relationships for a client (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsByClientId(int clientId) throws SQLException {
        String sql = """
            SELECT 
                cxp.ID as id,
                cxp.Project_ID as projectid,
                cxp.Client_ID as clientid,
                cxp.RelationType as relationtype,
                cxp.Description as description,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM client_x_project cxp
            LEFT JOIN project p ON cxp.Project_ID = p.id
            LEFT JOIN client_x_project_relationtype rt ON cxp.RelationType = rt.ID
            WHERE cxp.Client_ID = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, clientId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("projectid");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", projectId);
                    relationship.put("clientId", rs.getInt("clientid"));
                    relationship.put("relationType", rs.getObject("relationtype"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get project owner information
                    try {
                        String ownerName = getProjectOwnersString(projectId);
                        relationship.put("projectOwnerName", ownerName);
                    } catch (SQLException e) {
                        //system.out.println("ProjectImpactDAO: SQLException getting project owners for projectId " + projectId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("projectOwnerName", "No owner");
                    } catch (Exception e) {
                        //system.out.println("ProjectImpactDAO: Exception getting project owners for projectId " + projectId + ": " + e.getMessage());
                        e.printStackTrace();
                        relationship.put("projectOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project owners string (concatenated names)
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
     * Get project relationships for a capability (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        String sql = """
            SELECT 
                pxc.ID,
                pxc.Project_ID,
                pxc.Capability_ID,
                pxc.RelationType,
                pxc.Description,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM project_x_capability pxc
            LEFT JOIN project p ON pxc.Project_ID = p.id
            LEFT JOIN project_x_capability_relationtype rt ON pxc.RelationType = rt.ID
            WHERE pxc.Capability_ID = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, capabilityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("projectId", rs.getInt("Project_ID"));
                    relationship.put("capabilityId", rs.getInt("Capability_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relationships for a business area (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                pxb.ID,
                pxb.Project_ID,
                pxb.BusinessArea_ID,
                pxb.RelationType,
                pxb.Description,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM project_x_businessarea pxb
            LEFT JOIN project p ON pxb.Project_ID = p.id
            LEFT JOIN project_x_businessarea_relationtype rt ON pxb.RelationType = rt.ID
            WHERE pxb.BusinessArea_ID = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
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
                    relationship.put("projectId", rs.getInt("Project_ID"));
                    relationship.put("businessAreaId", rs.getInt("BusinessArea_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relationships for a dataset (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsByDatasetId(int datasetId) throws SQLException {
        String sql = """
            SELECT 
                pxd.id,
                pxd.projectid,
                pxd.dataset_id as datasetId,
                pxd.relationtype as relationType,
                pxd.description,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM project_x_dataset pxd
            LEFT JOIN project p ON pxd.projectid = p.id
            LEFT JOIN project_x_dataset_relationtype rt ON pxd.relationtype = rt.ID
            WHERE pxd.dataset_id = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, datasetId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("projectid");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", projectId);
                    relationship.put("datasetId", rs.getInt("datasetId"));
                    relationship.put("relationType", rs.getInt("relationType"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get all project owners for this project (concatenated)
                    try {
                        String ownersName = getProjectOwnersString(projectId);
                        relationship.put("projectOwnerName", ownersName != null ? ownersName : "No owner");
                    } catch (Exception e) {
                        //system.out.println("ProjectImpactDAO: Error getting project owners: " + e.getMessage());
                        relationship.put("projectOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get project relationships for an attribute (reverse lookup)
     */
    public List<Map<String, Object>> getProjectRelationshipsByAttributeId(int attributeId) throws SQLException {
        String sql = """
            SELECT 
                pxa.id,
                pxa.projectid,
                pxa.attribute_id as attributeId,
                pxa.relationtype as relationType,
                pxa.description,
                p.primaryname as projectName,
                p.refnumber as projectRefNumber,
                rt.primaryname as relationTypeName,
                rt.reversename as relationTypeReverseName
            FROM project_x_attribute pxa
            LEFT JOIN project p ON pxa.projectid = p.id
            LEFT JOIN project_x_attribute_relationtype rt ON pxa.relationtype = rt.id
            WHERE pxa.attribute_id = ? AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY p.primaryname
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, attributeId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("projectid");
                    
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("projectId", projectId);
                    relationship.put("attributeId", rs.getInt("attributeId"));
                    relationship.put("relationType", rs.getInt("relationType"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("projectName", rs.getString("projectName"));
                    relationship.put("projectRefNumber", rs.getString("projectRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get all project owners for this project (concatenated)
                    try {
                        String ownersName = getProjectOwnersString(projectId);
                        relationship.put("projectOwnerName", ownersName != null ? ownersName : "No owner");
                    } catch (Exception e) {
                        //system.out.println("ProjectImpactDAO: Error getting project owners: " + e.getMessage());
                        relationship.put("projectOwnerName", "No owner");
                    }
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get existing relationships as a Map (entityId -> relationType)
     * Returns map for easier comparison
     */
    private Map<Integer, Integer> getExistingRelationshipsMap(Connection conn, int projectId, String tableType) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = null;
        String entityColumn = null;
        
        switch (tableType.toLowerCase()) {
            case "system":
                sql = "SELECT systemid, relationtype FROM project_x_system WHERE projectid = ?";
                entityColumn = "systemid";
                break;
            case "process":
                sql = "SELECT process_id, relationtype FROM project_x_process WHERE projectid = ?";
                entityColumn = "process_id";
                break;
            case "glossary":
                sql = "SELECT Glossary_ID, RelationType FROM glossary_x_project WHERE Project_ID = ?";
                entityColumn = "Glossary_ID";
                break;
            case "policy":
                sql = "SELECT policy_id, relation_type FROM policy_x_project WHERE project_id = ?";
                entityColumn = "policy_id";
                break;
            case "product":
                sql = "SELECT productid, relationtype FROM product_x_project WHERE projectid = ?";
                entityColumn = "productid";
                break;
            case "client":
                sql = "SELECT Client_ID, RelationType FROM client_x_project WHERE Project_ID = ?";
                entityColumn = "Client_ID";
                break;
            case "capability":
                sql = "SELECT Capability_ID, RelationType FROM project_x_capability WHERE Project_ID = ?";
                entityColumn = "Capability_ID";
                break;
            case "businessarea":
                sql = "SELECT BusinessArea_ID, RelationType FROM project_x_businessarea WHERE Project_ID = ?";
                entityColumn = "BusinessArea_ID";
                break;
            case "dataset":
                sql = "SELECT dataset_id, relationtype FROM project_x_dataset WHERE projectid = ?";
                entityColumn = "dataset_id";
                break;
            case "attribute":
                sql = "SELECT attribute_id, relationtype FROM project_x_attribute WHERE projectid = ?";
                entityColumn = "attribute_id";
                break;
            default:
                return existingRelationships;
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int entityId = rs.getInt(entityColumn);
                    int relationType = rs.getInt(tableType.equals("policy") ? "relation_type" : 
                                                (tableType.equals("glossary") || tableType.equals("client") || 
                                                 tableType.equals("capability") || tableType.equals("businessarea")) ? 
                                                "RelationType" : "relationtype");
                    if (entityId > 0 && relationType > 0) {
                        existingRelationships.put(entityId, relationType);
                    }
                }
            }
        }
        
        return existingRelationships;
    }
    
    /**
     * Log audit history for insert operations in relationship tables
     * 
     * @param conn The database connection (to use same transaction)
     * @param projectId The project ID
     * @param tableType The type of relationship table
     * @param entityId The ID of the related entity
     * @param relationTypeId The ID of the relation type
     * @param userId The ID of the user performing the action
     */
    private void logAuditHistoryForInsert(Connection conn, int projectId, String tableType, 
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
                case "system":
                    objectName = "Project X System";
                    entityFieldName = "System";
                    break;
                case "process":
                    objectName = "Project X Process";
                    entityFieldName = "Process";
                    break;
                case "glossary":
                    objectName = "Project X Glossary";
                    entityFieldName = "Glossary";
                    break;
                case "policy":
                    objectName = "Project X Policy";
                    entityFieldName = "Policy";
                    break;
                case "product":
                    objectName = "Project X Product";
                    entityFieldName = "Product";
                    break;
                case "client":
                    objectName = "Project X Client";
                    entityFieldName = "Client";
                    break;
                case "capability":
                    objectName = "Project X Capability";
                    entityFieldName = "Capability";
                    break;
                case "businessarea":
                    objectName = "Project X Business Area";
                    entityFieldName = "Business Area";
                    break;
                case "dataset":
                    objectName = "Project X Dataset";
                    entityFieldName = "Dataset";
                    break;
                case "attribute":
                    objectName = "Project X Attribute";
                    entityFieldName = "Attribute";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO project_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, projectId);
                ps.setString(2, objectName);
                ps.setString(3, "link");
                ps.setString(4, "Added");
                ps.setString(5, entityFieldName);
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, entityName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type
                ps.setInt(1, projectId);
                ps.setString(2, objectName);
                ps.setString(3, "link");
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
    private void logAuditHistoryForDelete(Connection conn, int projectId, String tableType, 
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
                case "system":
                    objectName = "Project X System";
                    entityFieldName = "System";
                    break;
                case "process":
                    objectName = "Project X Process";
                    entityFieldName = "Process";
                    break;
                case "glossary":
                    objectName = "Project X Glossary";
                    entityFieldName = "Glossary";
                    break;
                case "policy":
                    objectName = "Project X Policy";
                    entityFieldName = "Policy";
                    break;
                case "product":
                    objectName = "Project X Product";
                    entityFieldName = "Product";
                    break;
                case "client":
                    objectName = "Project X Client";
                    entityFieldName = "Client";
                    break;
                case "capability":
                    objectName = "Project X Capability";
                    entityFieldName = "Capability";
                    break;
                case "businessarea":
                    objectName = "Project X Business Area";
                    entityFieldName = "Business Area";
                    break;
                case "dataset":
                    objectName = "Project X Dataset";
                    entityFieldName = "Dataset";
                    break;
                case "attribute":
                    objectName = "Project X Attribute";
                    entityFieldName = "Attribute";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO project_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, projectId);
                ps.setString(2, objectName);
                ps.setString(3, "link");
                ps.setString(4, "Deleted");
                ps.setString(5, entityFieldName);
                ps.setString(6, entityName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type deleted
                ps.setInt(1, projectId);
                ps.setString(2, objectName);
                ps.setString(3, "link");
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
    private void logAuditHistoryForUpdate(Connection conn, int projectId, String tableType, 
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
                    objectName = "Project X System";
                    break;
                case "process":
                    objectName = "Project X Process";
                    break;
                case "glossary":
                    objectName = "Project X Glossary";
                    break;
                case "policy":
                    objectName = "Project X Policy";
                    break;
                case "product":
                    objectName = "Project X Product";
                    break;
                case "client":
                    objectName = "Project X Client";
                    break;
                case "capability":
                    objectName = "Project X Capability";
                    break;
                case "businessarea":
                    objectName = "Project X Business Area";
                    break;
                case "dataset":
                    objectName = "Project X Dataset";
                    break;
                case "attribute":
                    objectName = "Project X Attribute";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO project_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, projectId);
                ps.setString(2, objectName);
                ps.setString(3, "link");
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
                sql = "SELECT name FROM system WHERE id = ?";
                columnName = "name";
                break;
            case "process":
                sql = "SELECT primaryname FROM process WHERE id = ?";
                columnName = "primaryname";
                break;
            case "glossary":
                sql = "SELECT Name FROM glossary WHERE ID = ?";
                columnName = "Name";
                break;
            case "policy":
                sql = "SELECT PrimaryName FROM policy WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "product":
                sql = "SELECT primaryname FROM product WHERE id = ?";
                columnName = "primaryname";
                break;
            case "client":
                sql = "SELECT PrimaryName FROM client WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "capability":
                sql = "SELECT PrimaryName FROM capability WHERE ID = ?";
                columnName = "PrimaryName";
                break;
            case "businessarea":
                sql = "SELECT PrimaryName FROM business_area WHERE ID = ?";
                columnName = "PrimaryName";
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
                tableName = "project_x_system_relationtype";
                break;
            case "process":
                tableName = "project_x_process_relationtype";
                break;
            case "glossary":
                tableName = "glossary_x_project_relationtype";
                break;
            case "policy":
                tableName = "policy_x_project_relationtype";
                break;
            case "product":
                tableName = "product_x_project_relationtype";
                break;
            case "client":
                tableName = "client_x_project_relationtype";
                break;
            case "capability":
                tableName = "project_x_capability_relationtype";
                break;
            case "businessarea":
                tableName = "project_x_businessarea_relationtype";
                break;
            case "dataset":
                tableName = "project_x_dataset_relationtype";
                break;
            case "attribute":
                tableName = "project_x_attribute_relationtype";
                break;
            default:
                System.err.println("Unknown table type: " + tableType);
                return null;
        }
        
        String sql = "SELECT primaryname FROM " + tableName + " WHERE id = ?";
        // Handle different column name cases
        if (tableType.equalsIgnoreCase("glossary") || tableType.equalsIgnoreCase("client") || 
            tableType.equalsIgnoreCase("capability") || tableType.equalsIgnoreCase("businessarea") ||
            tableType.equalsIgnoreCase("policy")) {
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


