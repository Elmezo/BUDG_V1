package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentValidationService;

import java.sql.*;
import java.util.*;

public class BusinessAreaImpactDAO {

    private final SegmentValidationService segmentValidationService = new SegmentValidationService();
    
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
     * Get glossary relationships by business area ID
     */
    public List<Map<String, Object>> getGlossaryRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.BusinessArea_ID,
                r.Glossary_ID,
                r.RelationType,
                r.Description,
                COALESCE(g.Name, g.Ref_Number) as glossaryName,
                g.Ref_Number as glossaryRefNumber,
                gt.Name as glossaryType,
                rt.PrimaryName as relationTypeName
            FROM businessarea_x_glossary r
            LEFT JOIN glossary g ON r.Glossary_ID = g.ID AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '')
            LEFT JOIN glossary_type gt ON g.Type = gt.ID
            LEFT JOIN businessarea_x_glossary_relationtype rt ON r.RelationType = rt.ID
            WHERE r.BusinessArea_ID = ?
            ORDER BY r.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    
                    int id = rs.getInt("ID");
                    relationship.put("id", rs.wasNull() ? null : id);
                    
                    int businessAreaIdFromRow = rs.getInt("BusinessArea_ID");
                    relationship.put("businessAreaId", rs.wasNull() ? null : businessAreaIdFromRow);
                    
                    int glossaryId = rs.getInt("Glossary_ID");
                    boolean glossaryIdWasNull = rs.wasNull();
                    relationship.put("glossaryId", glossaryIdWasNull ? null : glossaryId);
                    
                    int relationType = rs.getInt("RelationType");
                    relationship.put("relationType", rs.wasNull() ? null : relationType);
                    
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("glossaryName", rs.getString("glossaryName"));
                    relationship.put("glossaryRefNumber", rs.getString("glossaryRefNumber"));
                    String glossaryType = rs.getString("glossaryType");
                    relationship.put("glossaryType", glossaryType);
                    relationship.put("glossaryTypeName", glossaryType);
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get owners only if glossaryId is valid
                    if (!glossaryIdWasNull && glossaryId > 0) {
                        try {
                            relationship.put("glossaryOwnerName", getGlossaryOwnersString(glossaryId));
                            relationship.put("glossaryOwnerEmail", getGlossaryOwnersEmail(glossaryId));
                        } catch (SQLException e) {
                            System.err.println("Error getting glossary owners for glossaryId " + glossaryId + ": " + e.getMessage());
                            relationship.put("glossaryOwnerName", null);
                            relationship.put("glossaryOwnerEmail", null);
                        }
                    } else {
                        relationship.put("glossaryOwnerName", null);
                        relationship.put("glossaryOwnerEmail", null);
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
            FROM businessarea_x_glossary_relationtype
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
                type.put("primaryname", rs.getString("PrimaryName"));
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
    public boolean saveGlossaryRelationships(int businessAreaId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, businessAreaId, "glossary");
            
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
                    logAuditHistoryForDelete(conn, businessAreaId, "glossary", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, businessAreaId, "glossary", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, businessAreaId, "glossary", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM businessarea_x_glossary WHERE BusinessArea_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, businessAreaId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (using AUTO_INCREMENT)
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO businessarea_x_glossary 
                    (BusinessArea_ID, Glossary_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                // Track unique combinations to prevent duplicates
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object glossaryIdObj = rel.get("glossaryId");
                        Object relationTypeObj = rel.get("relationType");
                        Object descriptionObj = rel.get("description");
                        
                        if (glossaryIdObj != null && relationTypeObj != null) {
                            int glossaryId = ((Number) glossaryIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Check for duplicate (glossaryId + relationType combination)
                            if (glossaryId > 0 && relationType > 0) {
                                String uniqueKey = glossaryId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("BusinessAreaImpactDAO: Skipping duplicate glossary relationship - glossaryId=" + glossaryId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                // Prevent relationship between Business Area and Glossary in different private segments
                                SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                                    businessAreaId, "BusinessArea", glossaryId, "Glossary", conn);
                                if (!segmentResult.isValid) {
                                    throw new SQLException(segmentResult.message != null ? segmentResult.message
                                        : "Business Area and Glossary must be in the same segment or one must be in Enterprise.");
                                }
                                
                                String description = descriptionObj != null ? descriptionObj.toString() : null;
                                
                                ps.setInt(1, businessAreaId);
                                ps.setInt(2, glossaryId);
                                ps.setInt(3, relationType);
                                if (description != null && !description.isEmpty()) {
                                    ps.setString(4, description);
                                } else {
                                    ps.setNull(4, Types.LONGVARCHAR);
                                }
                                ps.setInt(5, userId);
                                ps.executeUpdate();
                            }
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
     * Get system relationships by business area ID
     */
    public List<Map<String, Object>> getSystemRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.BusinessArea_ID,
                r.System_ID,
                r.RelationType,
                r.Description,
                s.Name as systemName,
                rt.PrimaryName as relationTypeName
            FROM businessarea_x_system r
            LEFT JOIN system s ON r.System_ID = s.id
            LEFT JOIN businessarea_x_system_relationtype rt ON r.RelationType = rt.ID
            WHERE r.BusinessArea_ID = ?
            ORDER BY r.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    
                    int id = rs.getInt("ID");
                    relationship.put("id", rs.wasNull() ? null : id);
                    
                    int businessAreaIdFromRow = rs.getInt("BusinessArea_ID");
                    relationship.put("businessAreaId", rs.wasNull() ? null : businessAreaIdFromRow);
                    
                    int systemId = rs.getInt("System_ID");
                    boolean systemIdWasNull = rs.wasNull();
                    relationship.put("systemId", systemIdWasNull ? null : systemId);
                    
                    int relationType = rs.getInt("RelationType");
                    relationship.put("relationType", rs.wasNull() ? null : relationType);
                    
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("systemName", rs.getString("systemName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get owners only if systemId is valid
                    if (!systemIdWasNull && systemId > 0) {
                        try {
                            relationship.put("systemOwnerName", getSystemOwnersString(systemId));
                            relationship.put("systemOwnerEmail", getSystemOwnersEmail(systemId));
                        } catch (SQLException e) {
                            System.err.println("Error getting system owners for systemId " + systemId + ": " + e.getMessage());
                            relationship.put("systemOwnerName", null);
                            relationship.put("systemOwnerEmail", null);
                        }
                    } else {
                        relationship.put("systemOwnerName", null);
                        relationship.put("systemOwnerEmail", null);
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
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM businessarea_x_system_relationtype
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
                type.put("primaryname", rs.getString("PrimaryName"));
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
     * Save system relationships
     */
    public boolean saveSystemRelationships(int businessAreaId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, businessAreaId, "system");
            
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
                    logAuditHistoryForDelete(conn, businessAreaId, "system", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, businessAreaId, "system", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, businessAreaId, "system", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM businessarea_x_system WHERE BusinessArea_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, businessAreaId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (using AUTO_INCREMENT)
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO businessarea_x_system 
                    (BusinessArea_ID, System_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                // Track unique combinations to prevent duplicates
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object systemIdObj = rel.get("systemId");
                        Object relationTypeObj = rel.get("relationType");
                        Object descriptionObj = rel.get("description");
                        
                        if (systemIdObj != null && relationTypeObj != null) {
                            int systemId = ((Number) systemIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Check for duplicate (systemId + relationType combination)
                            if (systemId > 0 && relationType > 0) {
                                String uniqueKey = systemId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("BusinessAreaImpactDAO: Skipping duplicate system relationship - systemId=" + systemId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                // Prevent relationship between Business Area and System in different private segments
                                SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                                    businessAreaId, "BusinessArea", systemId, "System", conn);
                                if (!segmentResult.isValid) {
                                    throw new SQLException(segmentResult.message != null ? segmentResult.message
                                        : "Business Area and System must be in the same segment or one must be in Enterprise.");
                                }
                                
                                String description = descriptionObj != null ? descriptionObj.toString() : null;
                                
                                ps.setInt(1, businessAreaId);
                                ps.setInt(2, systemId);
                                ps.setInt(3, relationType);
                                if (description != null && !description.isEmpty()) {
                                    ps.setString(4, description);
                                } else {
                                    ps.setNull(4, Types.LONGVARCHAR);
                                }
                                ps.setInt(5, userId);
                                ps.executeUpdate();
                            }
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

    // ===== PROCESS RELATIONSHIPS =====
    
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
     * Get process relationships by business area ID
     */
    public List<Map<String, Object>> getProcessRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.BusinessArea_ID,
                r.Process_ID,
                r.RelationType,
                r.Description,
                p.primaryname as processName,
                p.refnumber as processRefNumber,
                rt.PrimaryName as relationTypeName
            FROM businessarea_x_process r
            LEFT JOIN process p ON r.Process_ID = p.id
            LEFT JOIN businessarea_x_process_relationtype rt ON r.RelationType = rt.ID
            WHERE r.BusinessArea_ID = ?
            ORDER BY r.ID
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, businessAreaId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    
                    int id = rs.getInt("ID");
                    relationship.put("id", rs.wasNull() ? null : id);
                    
                    int businessAreaIdFromRow = rs.getInt("BusinessArea_ID");
                    relationship.put("businessAreaId", rs.wasNull() ? null : businessAreaIdFromRow);
                    
                    int processId = rs.getInt("Process_ID");
                    boolean processIdWasNull = rs.wasNull();
                    relationship.put("processId", processIdWasNull ? null : processId);
                    
                    int relationType = rs.getInt("RelationType");
                    relationship.put("relationType", rs.wasNull() ? null : relationType);
                    
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("processName", rs.getString("processName"));
                    relationship.put("processRefNumber", rs.getString("processRefNumber"));
                    relationship.put("processRef", rs.getString("processRefNumber"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    
                    // Get owners only if processId is valid
                    if (!processIdWasNull && processId > 0) {
                        try {
                            relationship.put("processOwnerName", getProcessOwnersString(processId));
                            relationship.put("processOwnerEmail", getProcessOwnersEmail(processId));
                        } catch (SQLException e) {
                            System.err.println("Error getting process owners for processId " + processId + ": " + e.getMessage());
                            relationship.put("processOwnerName", null);
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
     * Get process relation types
     */
    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, Description, Priority, ReverseName
            FROM businessarea_x_process_relationtype
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
                type.put("primaryname", rs.getString("PrimaryName"));
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
     * Save process relationships
     */
    public boolean saveProcessRelationships(int businessAreaId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get existing relationships before deleting (to compare with new ones)
            Map<Integer, Integer> existingRelationshipsMap = getExistingRelationshipsMap(conn, businessAreaId, "process");
            
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
                    logAuditHistoryForDelete(conn, businessAreaId, "process", entityId, oldRelationType, userId);
                } else if (newRelationType != oldRelationType) {
                    // Relation type was updated
                    logAuditHistoryForUpdate(conn, businessAreaId, "process", entityId, oldRelationType, newRelationType, userId);
                }
            }
            
            // INSERT: entities in new but not in existing
            for (Map.Entry<Integer, Integer> entry : newRelationshipsMap.entrySet()) {
                int entityId = entry.getKey();
                int newRelationType = entry.getValue();
                
                if (!existingRelationshipsMap.containsKey(entityId)) {
                    // New entity added
                    logAuditHistoryForInsert(conn, businessAreaId, "process", entityId, newRelationType, userId);
                }
            }
            
            // Delete existing relationships
            String deleteSql = "DELETE FROM businessarea_x_process WHERE BusinessArea_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, businessAreaId);
                ps.executeUpdate();
            }
            
            // Insert new relationships (using AUTO_INCREMENT)
            if (relationships != null && !relationships.isEmpty()) {
                String insertSql = """
                    INSERT INTO businessarea_x_process 
                    (BusinessArea_ID, Process_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) 
                    VALUES (?, ?, ?, ?, NOW(), NOW(), ?)
                """;
                
                // Track unique combinations to prevent duplicates
                Set<String> seenRelationships = new HashSet<>();
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rel : relationships) {
                        Object processIdObj = rel.get("processId");
                        Object relationTypeObj = rel.get("relationType");
                        Object descriptionObj = rel.get("description");
                        
                        if (processIdObj != null && relationTypeObj != null) {
                            int processId = ((Number) processIdObj).intValue();
                            int relationType = ((Number) relationTypeObj).intValue();
                            
                            // Check for duplicate (processId + relationType combination)
                            if (processId > 0 && relationType > 0) {
                                String uniqueKey = processId + "_" + relationType;
                                if (seenRelationships.contains(uniqueKey)) {
                                    //system.out.println("BusinessAreaImpactDAO: Skipping duplicate process relationship - processId=" + processId + ", relationType=" + relationType);
                                    continue;
                                }
                                seenRelationships.add(uniqueKey);
                                
                                // Prevent relationship between Business Area and Process in different private segments
                                SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                                    businessAreaId, "BusinessArea", processId, "Process", conn);
                                if (!segmentResult.isValid) {
                                    throw new SQLException(segmentResult.message != null ? segmentResult.message
                                        : "Business Area and Process must be in the same segment or one must be in Enterprise.");
                                }
                                
                                String description = descriptionObj != null ? descriptionObj.toString() : null;
                                
                                ps.setInt(1, businessAreaId);
                                ps.setInt(2, processId);
                                ps.setInt(3, relationType);
                                if (description != null && !description.isEmpty()) {
                                    ps.setString(4, description);
                                } else {
                                    ps.setNull(4, Types.LONGVARCHAR);
                                }
                                ps.setInt(5, userId);
                                ps.executeUpdate();
                            }
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
     * Get business area relationships for a glossary (reverse lookup)
     */
    public List<Map<String, Object>> getBusinessAreaRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.BusinessArea_ID,
                r.Glossary_ID,
                r.RelationType,
                r.Description,
                ba.PrimaryName as businessAreaName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM businessarea_x_glossary r
            LEFT JOIN business_area ba ON r.BusinessArea_ID = ba.ID
            LEFT JOIN businessarea_x_glossary_relationtype rt ON r.RelationType = rt.ID
            WHERE r.Glossary_ID = ? AND (ba.deletedatetime IS NULL OR ba.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY ba.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("businessAreaId", rs.getInt("BusinessArea_ID"));
                    relationship.put("glossaryId", rs.getInt("Glossary_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("businessAreaName", rs.getString("businessAreaName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get business area relationships for a system (reverse lookup)
     */
    public List<Map<String, Object>> getBusinessAreaRelationshipsBySystemId(int systemId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.BusinessArea_ID,
                r.System_ID,
                r.RelationType,
                r.Description,
                ba.PrimaryName as businessAreaName,
                ba.`Reference` as businessAreaReference,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM businessarea_x_system r
            LEFT JOIN business_area ba ON r.BusinessArea_ID = ba.ID
            LEFT JOIN businessarea_x_system_relationtype rt ON r.RelationType = rt.ID
            WHERE r.System_ID = ? AND (ba.deletedatetime IS NULL OR ba.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY ba.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, systemId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    int businessAreaId = rs.getInt("BusinessArea_ID");
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("businessAreaId", businessAreaId);
                    relationship.put("systemId", rs.getInt("System_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("businessAreaName", rs.getString("businessAreaName"));
                    relationship.put("businessAreaReference", rs.getString("businessAreaReference"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get owner information
                    String ownerName = getBusinessAreaOwnersString(businessAreaId);
                    relationship.put("businessAreaOwnerName", ownerName != null ? ownerName : "No owner");
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get business area relationships for a process (reverse lookup)
     */
    public List<Map<String, Object>> getBusinessAreaRelationshipsByProcessId(int processId) throws SQLException {
        String sql = """
            SELECT 
                r.ID,
                r.BusinessArea_ID,
                r.Process_ID,
                r.RelationType,
                r.Description,
                ba.PrimaryName as businessAreaName,
                rt.PrimaryName as relationTypeName,
                rt.ReverseName as relationTypeReverseName
            FROM businessarea_x_process r
            LEFT JOIN business_area ba ON r.BusinessArea_ID = ba.ID
            LEFT JOIN businessarea_x_process_relationtype rt ON r.RelationType = rt.ID
            WHERE r.Process_ID = ? AND (ba.deletedatetime IS NULL OR ba.deletedatetime = '1970-01-01 00:00:00')
            ORDER BY ba.PrimaryName
        """;
        
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, processId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relationship = new HashMap<>();
                    int businessAreaId = rs.getInt("BusinessArea_ID");
                    relationship.put("id", rs.getInt("ID"));
                    relationship.put("businessAreaId", businessAreaId);
                    relationship.put("processId", rs.getInt("Process_ID"));
                    relationship.put("relationType", rs.getObject("RelationType"));
                    relationship.put("description", rs.getString("Description"));
                    relationship.put("businessAreaName", rs.getString("businessAreaName"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));
                    relationship.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Get owner information
                    String ownerName = getBusinessAreaOwnersString(businessAreaId);
                    relationship.put("businessAreaOwnerName", ownerName != null ? ownerName : "No owner");
                    
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Get business area owners string (concatenated names)
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
                
                return ownersList.length() > 0 ? ownersList.toString() : null;
            }
        }
    }
    
    // ===== AUDIT HISTORY HELPER METHODS =====
    
    /**
     * Get existing relationships as a Map (entityId -> relationType) and Set of unique keys
     * Returns both for easier comparison
     */
    private Map<Integer, Integer> getExistingRelationshipsMap(Connection conn, int businessAreaId, String tableType) throws SQLException {
        Map<Integer, Integer> existingRelationships = new HashMap<>();
        String sql = null;
        String entityColumn = null;
        
        switch (tableType.toLowerCase()) {
            case "glossary":
                sql = "SELECT Glossary_ID, RelationType FROM businessarea_x_glossary WHERE BusinessArea_ID = ?";
                entityColumn = "Glossary_ID";
                break;
            case "system":
                sql = "SELECT System_ID, RelationType FROM businessarea_x_system WHERE BusinessArea_ID = ?";
                entityColumn = "System_ID";
                break;
            case "process":
                sql = "SELECT Process_ID, RelationType FROM businessarea_x_process WHERE BusinessArea_ID = ?";
                entityColumn = "Process_ID";
                break;
            default:
                return existingRelationships;
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, businessAreaId);
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
     * Log audit history for insert operations in relationship tables
     * 
     * @param conn The database connection (to use same transaction)
     * @param businessAreaId The business area ID
     * @param tableType The type of relationship table ("glossary", "system", "process")
     * @param entityId The ID of the related entity
     * @param relationTypeId The ID of the relation type
     * @param userId The ID of the user performing the action
     */
    private void logAuditHistoryForInsert(Connection conn, int businessAreaId, String tableType, 
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
                case "glossary":
                    objectName = "Business Area X Glossary";
                    entityFieldName = "Glossary";
                    break;
                case "system":
                    objectName = "Business Area X System";
                    entityFieldName = "System";
                    break;
                case "process":
                    objectName = "Business Area X Process";
                    entityFieldName = "Process";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO business_area_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity linked
                ps.setInt(1, businessAreaId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Added");
                ps.setString(5, entityFieldName);
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, entityName);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type
                ps.setInt(1, businessAreaId);
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
    private void logAuditHistoryForDelete(Connection conn, int businessAreaId, String tableType, 
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
                case "glossary":
                    objectName = "Business Area X Glossary";
                    entityFieldName = "Glossary";
                    break;
                case "system":
                    objectName = "Business Area X System";
                    entityFieldName = "System";
                    break;
                case "process":
                    objectName = "Business Area X Process";
                    entityFieldName = "Process";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history records
            String insertSql = """
                INSERT INTO business_area_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                // Record 1: Entity deleted
                ps.setInt(1, businessAreaId);
                ps.setString(2, objectName);
                ps.setString(3, "Links");
                ps.setString(4, "Deleted");
                ps.setString(5, entityFieldName);
                ps.setString(6, entityName);
                ps.setNull(7, Types.VARCHAR);
                ps.setString(8, userFullName);
                ps.executeUpdate();
                
                // Record 2: Relationship Type deleted
                ps.setInt(1, businessAreaId);
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
    private void logAuditHistoryForUpdate(Connection conn, int businessAreaId, String tableType, 
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
                case "glossary":
                    objectName = "Business Area X Glossary";
                    break;
                case "system":
                    objectName = "Business Area X System";
                    break;
                case "process":
                    objectName = "Business Area X Process";
                    break;
                default:
                    System.err.println("Unknown table type for audit history: " + tableType);
                    return;
            }
            
            // Insert audit history record for relationship type change
            String insertSql = """
                INSERT INTO business_area_audit_history 
                (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, businessAreaId);
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
                tableName = "businessarea_x_glossary_relationtype";
                break;
            case "system":
                tableName = "businessarea_x_system_relationtype";
                break;
            case "process":
                tableName = "businessarea_x_process_relationtype";
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
}

