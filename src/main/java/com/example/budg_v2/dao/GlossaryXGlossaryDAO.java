package com.example.budg_v2.dao;

import com.example.budg_v2.audit.AuditHistoryWriter;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.GlossaryXGlossary;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class GlossaryXGlossaryDAO {

    private static final String AUDIT_TABLE = "glossary_audit_history";
    private static final String AUDIT_OBJECT = "Glossary X Glossary";
    private static final String AUDIT_EVENT = "Relationships";

    /**
     * Resolve the display name of a glossary.
     */
    private String getGlossaryName(Connection conn, Integer glossaryId) throws SQLException {
        if (glossaryId == null) return null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT Name FROM glossary WHERE ID = ?")) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Resolve the display name of a glossary_x_glossary relation type.
     */
    private String getRelationTypeName(Connection conn, Integer relationTypeId) throws SQLException {
        if (relationTypeId == null) return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT PrimaryName FROM glossary_x_glossary_reltype WHERE ID = ?")) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Resolve the display name of a user (for the Author column).
     */
    private String getUserFullName(Connection conn, Integer userId) throws SQLException {
        if (userId == null || userId <= 0) return "System";
        String sql = "SELECT CONCAT(COALESCE(First_Name,''), ' ', COALESCE(Last_Name,'')) AS fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.trim().isEmpty()) return name.trim();
                }
            }
        }
        return "User #" + userId;
    }

    /**
     * Write one Added audit row per populated field on the source glossary's
     * history for a newly-created glossary_x_glossary relationship.
     */
    public void logRelationshipAdded(Integer sourceGlossaryId, Integer targetGlossaryId,
                                      Integer relationTypeId, Integer userId) {
        if (sourceGlossaryId == null || sourceGlossaryId <= 0) return;
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sourceName = getGlossaryName(conn, sourceGlossaryId);
            String targetName = getGlossaryName(conn, targetGlossaryId);
            String relationTypeName = getRelationTypeName(conn, relationTypeId);
            String author = getUserFullName(conn, userId);

            AuditHistoryWriter.logAdded(conn, AUDIT_TABLE, sourceGlossaryId,
                    AUDIT_OBJECT, AUDIT_EVENT, "Source Glossary", sourceName, author);
            AuditHistoryWriter.logAdded(conn, AUDIT_TABLE, sourceGlossaryId,
                    AUDIT_OBJECT, AUDIT_EVENT, "Target Glossary", targetName, author);
            AuditHistoryWriter.logAdded(conn, AUDIT_TABLE, sourceGlossaryId,
                    AUDIT_OBJECT, AUDIT_EVENT, "Relationship Type", relationTypeName, author);
        } catch (SQLException e) {
            // Do not break the main transaction over audit failures
            System.err.println("[GlossaryXGlossaryDAO] Failed to log relationship-added audit: " + e.getMessage());
        }
    }

    /**
     * Write per-field Updated audit rows for an existing glossary relationship.
     */
    public void logRelationshipUpdated(Integer relationshipParentSourceId,
                                       Integer oldSourceId, Integer newSourceId,
                                       Integer oldTargetId, Integer newTargetId,
                                       Integer oldRelationTypeId, Integer newRelationTypeId,
                                       Integer userId) {
        if (relationshipParentSourceId == null || relationshipParentSourceId <= 0) return;
        try (Connection conn = DatabaseConnection.getConnection()) {
            String author = getUserFullName(conn, userId);

            if (!Objects.equals(oldSourceId, newSourceId)) {
                AuditHistoryWriter.logUpdated(conn, AUDIT_TABLE, relationshipParentSourceId,
                        AUDIT_OBJECT, AUDIT_EVENT, "Source Glossary",
                        getGlossaryName(conn, oldSourceId), getGlossaryName(conn, newSourceId), author);
            }
            if (!Objects.equals(oldTargetId, newTargetId)) {
                AuditHistoryWriter.logUpdated(conn, AUDIT_TABLE, relationshipParentSourceId,
                        AUDIT_OBJECT, AUDIT_EVENT, "Target Glossary",
                        getGlossaryName(conn, oldTargetId), getGlossaryName(conn, newTargetId), author);
            }
            if (!Objects.equals(oldRelationTypeId, newRelationTypeId)) {
                AuditHistoryWriter.logUpdated(conn, AUDIT_TABLE, relationshipParentSourceId,
                        AUDIT_OBJECT, AUDIT_EVENT, "Relationship Type",
                        getRelationTypeName(conn, oldRelationTypeId),
                        getRelationTypeName(conn, newRelationTypeId), author);
            }
        } catch (SQLException e) {
            System.err.println("[GlossaryXGlossaryDAO] Failed to log relationship-updated audit: " + e.getMessage());
        }
    }

    /**
     * Write one Deleted audit row per populated field when a relationship is removed.
     */
    public void logRelationshipDeleted(Integer sourceGlossaryId, Integer targetGlossaryId,
                                        Integer relationTypeId, Integer userId) {
        if (sourceGlossaryId == null || sourceGlossaryId <= 0) return;
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sourceName = getGlossaryName(conn, sourceGlossaryId);
            String targetName = getGlossaryName(conn, targetGlossaryId);
            String relationTypeName = getRelationTypeName(conn, relationTypeId);
            String author = getUserFullName(conn, userId);

            AuditHistoryWriter.logDeleted(conn, AUDIT_TABLE, sourceGlossaryId,
                    AUDIT_OBJECT, AUDIT_EVENT, "Source Glossary", sourceName, author);
            AuditHistoryWriter.logDeleted(conn, AUDIT_TABLE, sourceGlossaryId,
                    AUDIT_OBJECT, AUDIT_EVENT, "Target Glossary", targetName, author);
            AuditHistoryWriter.logDeleted(conn, AUDIT_TABLE, sourceGlossaryId,
                    AUDIT_OBJECT, AUDIT_EVENT, "Relationship Type", relationTypeName, author);
        } catch (SQLException e) {
            System.err.println("[GlossaryXGlossaryDAO] Failed to log relationship-deleted audit: " + e.getMessage());
        }
    }

    /**
     * Get glossary relationship by ID
     */
    public GlossaryXGlossary getById(int id) throws SQLException {
        String sql = "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                "gxg.CreateDatetime, gxg.LastUpdateDatetime, gxg.LastUpdateUser_ID, " +
                "rt.PrimaryName AS relationTypeName " +
                "FROM glossary_x_glossary gxg " +
                "LEFT JOIN glossary_x_glossary_reltype rt ON rt.ID = gxg.RelationType " +
                "WHERE gxg.ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapResultSetToGlossaryXGlossary(rs);
            }
        }
    }

    /**
     * Get all glossary relationships
     */
    public List<Map<String, Object>> getAllRelationships() throws SQLException {
        String sql = "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                "gxg.CreateDatetime, gxg.LastUpdateDatetime, gxg.LastUpdateUser_ID, " +
                "rt.PrimaryName AS relationTypeName, " +
                "sg.Name AS sourceGlossaryName, tg.Name AS targetGlossaryName " +
                "FROM glossary_x_glossary gxg " +
                "LEFT JOIN glossary_x_glossary_reltype rt ON rt.ID = gxg.RelationType " +
                "LEFT JOIN glossary sg ON sg.ID = gxg.SourceGlossaryID " +
                "LEFT JOIN glossary tg ON tg.ID = gxg.TargetGlossaryID " +
                "WHERE (sg.Deleted_datetime IS NULL) AND (tg.Deleted_datetime IS NULL) " +
                "ORDER BY gxg.ID";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("sourceGlossaryId", rs.getInt("SourceGlossaryID"));
                row.put("targetGlossaryId", rs.getInt("TargetGlossaryID"));
                row.put("relationType", rs.getInt("RelationType"));
                row.put("relationTypeName", rs.getString("relationTypeName"));
                row.put("sourceGlossaryName", rs.getString("sourceGlossaryName"));
                row.put("targetGlossaryName", rs.getString("targetGlossaryName"));
                row.put("createDatetime", rs.getTimestamp("CreateDatetime"));
                row.put("lastUpdateDatetime", rs.getTimestamp("LastUpdateDatetime"));
                row.put("lastUpdateUserId", rs.getInt("LastUpdateUser_ID"));
                results.add(row);
            }
            return results;
        }
    }

    /**
     * Get relationships by source glossary ID
     */
    public List<Map<String, Object>> getRelationshipsBySourceId(int sourceGlossaryId) throws SQLException {
        System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] getRelationshipsBySourceId called with sourceGlossaryId: " + sourceGlossaryId);
        
        String sql = "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                "gxg.CreateDatetime, gxg.LastUpdateDatetime, gxg.LastUpdateUser_ID, " +
                "rt.PrimaryName AS relationTypeName, " +
                "tg.Name AS targetGlossaryName, " +
                "gt.Name AS targetGlossaryType " +
                "FROM glossary_x_glossary gxg " +
                "LEFT JOIN glossary_x_glossary_reltype rt ON rt.ID = gxg.RelationType " +
                "LEFT JOIN glossary tg ON tg.ID = gxg.TargetGlossaryID " +
                "LEFT JOIN glossary_type gt ON gt.ID = tg.Type " +
                "WHERE gxg.SourceGlossaryID = ? " +
                "AND (tg.Deleted_datetime IS NULL) " +
                "ORDER BY gxg.ID";
        
        System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] SQL Query: " + sql);
        System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] Parameter: sourceGlossaryId = " + sourceGlossaryId);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sourceGlossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    Map<String, Object> row = new HashMap<>();
                    int id = rs.getInt("ID");
                    int sourceId = rs.getInt("SourceGlossaryID");
                    int targetId = rs.getInt("TargetGlossaryID");
                    int relationType = rs.getInt("RelationType");
                    String relationTypeName = rs.getString("relationTypeName");
                    String targetGlossaryName = rs.getString("targetGlossaryName");
                    String targetGlossaryType = rs.getString("targetGlossaryType");
                    
                    row.put("id", id);
                    row.put("sourceGlossaryId", sourceId);
                    row.put("targetGlossaryId", targetId);
                    row.put("relationType", relationType);
                    row.put("relationTypeName", relationTypeName);
                    row.put("targetGlossaryName", targetGlossaryName);
                    row.put("targetGlossaryType", targetGlossaryType);
                    row.put("createDatetime", rs.getTimestamp("CreateDatetime"));
                    row.put("lastUpdateDatetime", rs.getTimestamp("LastUpdateDatetime"));
                    row.put("lastUpdateUserId", rs.getInt("LastUpdateUser_ID"));
                    results.add(row);
                    
                    System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] Row " + rowCount + ": id=" + id + 
                        ", sourceGlossaryId=" + sourceId + 
                        ", targetGlossaryId=" + targetId + 
                        ", relationType=" + relationType + 
                        ", relationTypeName=" + relationTypeName + 
                        ", targetGlossaryName=" + targetGlossaryName + 
                        ", targetGlossaryType=" + targetGlossaryType);
                }
                System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] Total rows returned: " + rowCount);
                return results;
            }
        }
    }

    /**
     * Get relationships by target glossary ID
     */
    public List<Map<String, Object>> getRelationshipsByTargetId(int targetGlossaryId) throws SQLException {
        String sql = "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                "gxg.CreateDatetime, gxg.LastUpdateDatetime, gxg.LastUpdateUser_ID, " +
                "rt.PrimaryName AS relationTypeName, " +
                "sg.Name AS sourceGlossaryName " +
                "FROM glossary_x_glossary gxg " +
                "LEFT JOIN glossary_x_glossary_reltype rt ON rt.ID = gxg.RelationType " +
                "LEFT JOIN glossary sg ON sg.ID = gxg.SourceGlossaryID " +
                "WHERE gxg.TargetGlossaryID = ? " +
                "AND (sg.Deleted_datetime IS NULL) " +
                "ORDER BY gxg.ID";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, targetGlossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("sourceGlossaryId", rs.getInt("SourceGlossaryID"));
                    row.put("targetGlossaryId", rs.getInt("TargetGlossaryID"));
                    row.put("relationType", rs.getInt("RelationType"));
                    row.put("relationTypeName", rs.getString("relationTypeName"));
                    row.put("sourceGlossaryName", rs.getString("sourceGlossaryName"));
                    row.put("createDatetime", rs.getTimestamp("CreateDatetime"));
                    row.put("lastUpdateDatetime", rs.getTimestamp("LastUpdateDatetime"));
                    row.put("lastUpdateUserId", rs.getInt("LastUpdateUser_ID"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * Get bidirectional relationships for a glossary (both as source and target)
     * Returns relationships where the glossary is either source or target,
     * with direction indicator and related glossary information
     */
    public List<Map<String, Object>> getBidirectionalRelationships(int glossaryId) throws SQLException {
        System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] getBidirectionalRelationships called with glossaryId: " + glossaryId);
        
        // Use UNION to combine relationships where glossary is source and where it's target
        // For source relationships: glossary is source, show target info
        // For target relationships: glossary is target, show source info
        String sql = "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                "gxg.CreateDatetime, gxg.LastUpdateDatetime, gxg.LastUpdateUser_ID, " +
                "rt.PrimaryName AS relationTypeName, " +
                "'outgoing' AS direction, " +
                "tg.Name AS relatedGlossaryName, " +
                "tg.ID AS relatedGlossaryId, " +
                "gt.Name AS relatedGlossaryType " +
                "FROM glossary_x_glossary gxg " +
                "LEFT JOIN glossary_x_glossary_reltype rt ON rt.ID = gxg.RelationType " +
                "LEFT JOIN glossary tg ON tg.ID = gxg.TargetGlossaryID " +
                "LEFT JOIN glossary_type gt ON gt.ID = tg.Type " +
                "WHERE gxg.SourceGlossaryID = ? " +
                "AND (tg.Deleted_datetime IS NULL) " +
                "UNION ALL " +
                "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                "gxg.CreateDatetime, gxg.LastUpdateDatetime, gxg.LastUpdateUser_ID, " +
                "rt.PrimaryName AS relationTypeName, " +
                "'incoming' AS direction, " +
                "sg.Name AS relatedGlossaryName, " +
                "sg.ID AS relatedGlossaryId, " +
                "gt.Name AS relatedGlossaryType " +
                "FROM glossary_x_glossary gxg " +
                "LEFT JOIN glossary_x_glossary_reltype rt ON rt.ID = gxg.RelationType " +
                "LEFT JOIN glossary sg ON sg.ID = gxg.SourceGlossaryID " +
                "LEFT JOIN glossary_type gt ON gt.ID = sg.Type " +
                "WHERE gxg.TargetGlossaryID = ? " +
                "AND (sg.Deleted_datetime IS NULL) " +
                "ORDER BY ID";
        
        System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] SQL Query: " + sql);
        System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] Parameters: glossaryId = " + glossaryId);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.setInt(2, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    Map<String, Object> row = new HashMap<>();
                    int id = rs.getInt("ID");
                    int sourceId = rs.getInt("SourceGlossaryID");
                    int targetId = rs.getInt("TargetGlossaryID");
                    int relationType = rs.getInt("RelationType");
                    String relationTypeName = rs.getString("relationTypeName");
                    String direction = rs.getString("direction");
                    String relatedGlossaryName = rs.getString("relatedGlossaryName");
                    int relatedGlossaryId = rs.getInt("relatedGlossaryId");
                    String relatedGlossaryType = rs.getString("relatedGlossaryType");
                    
                    row.put("id", id);
                    row.put("sourceGlossaryId", sourceId);
                    row.put("targetGlossaryId", targetId);
                    row.put("relationType", relationType);
                    row.put("relationTypeName", relationTypeName);
                    row.put("direction", direction); // "outgoing" or "incoming"
                    row.put("relatedGlossaryId", relatedGlossaryId);
                    row.put("relatedGlossaryName", relatedGlossaryName);
                    row.put("relatedGlossaryType", relatedGlossaryType);
                    row.put("createDatetime", rs.getTimestamp("CreateDatetime"));
                    row.put("lastUpdateDatetime", rs.getTimestamp("LastUpdateDatetime"));
                    row.put("lastUpdateUserId", rs.getInt("LastUpdateUser_ID"));
                    results.add(row);
                    
                    System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] Row " + rowCount + ": id=" + id + 
                        ", direction=" + direction + 
                        ", relatedGlossaryId=" + relatedGlossaryId + 
                        ", relatedGlossaryName=" + relatedGlossaryName + 
                        ", relationTypeName=" + relationTypeName);
                }
                System.out.println("[GLOSSARY-RELATIONSHIPS-DAO] Total rows returned: " + rowCount);
                return results;
            }
        }
    }

    /**
     * Create new glossary relationship
     */
    public int create(GlossaryXGlossary relationship) throws SQLException {
        // First, get the next available ID
        int nextId = getNextId();
        
        String sql = "INSERT INTO glossary_x_glossary (ID, SourceGlossaryID, TargetGlossaryID, RelationType, " +
                "CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nextId);
            ps.setObject(2, relationship.getSourceGlossaryId());
            ps.setObject(3, relationship.getTargetGlossaryId());
            ps.setObject(4, relationship.getRelationType());
            ps.setTimestamp(5, relationship.getCreateDatetime() != null ? 
                Timestamp.valueOf(relationship.getCreateDatetime()) : Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(6, relationship.getLastUpdateDatetime() != null ? 
                Timestamp.valueOf(relationship.getLastUpdateDatetime()) : Timestamp.valueOf(LocalDateTime.now()));
            ps.setObject(7, relationship.getLastUpdateUserId());
            
            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating glossary relationship failed, no rows affected.");
            }
            
            return nextId;
        }
    }
    
    /**
     * Get the next available ID for glossary_x_glossary table
     */
    private int getNextId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 AS nextId FROM glossary_x_glossary";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("nextId");
            } else {
                return 1; // Default to 1 if no records exist
            }
        }
    }

    /**
     * Update glossary relationship
     */
    public boolean update(GlossaryXGlossary relationship) throws SQLException {
        String sql = "UPDATE glossary_x_glossary SET SourceGlossaryID = ?, TargetGlossaryID = ?, " +
                "RelationType = ?, LastUpdateDatetime = ?, LastUpdateUser_ID = ? WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, relationship.getSourceGlossaryId());
            ps.setObject(2, relationship.getTargetGlossaryId());
            ps.setObject(3, relationship.getRelationType());
            ps.setTimestamp(4, relationship.getLastUpdateDatetime() != null ? 
                Timestamp.valueOf(relationship.getLastUpdateDatetime()) : Timestamp.valueOf(LocalDateTime.now()));
            ps.setObject(5, relationship.getLastUpdateUserId());
            ps.setInt(6, relationship.getId());
            
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Delete glossary relationship
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM glossary_x_glossary WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Get all relation types
     */
    public List<Map<String, Object>> getRelationTypes() throws SQLException {
        String sql = "SELECT ID, PrimaryName, Description FROM glossary_x_glossary_reltype ORDER BY ID";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("primaryName", rs.getString("PrimaryName"));
                row.put("description", rs.getString("Description"));
                results.add(row);
            }
            return results;
        }
    }

    /**
     * Check if relationship already exists
     */
    public boolean relationshipExists(int sourceGlossaryId, int targetGlossaryId, int relationType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM glossary_x_glossary " +
                "WHERE SourceGlossaryID = ? AND TargetGlossaryID = ? AND RelationType = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sourceGlossaryId);
            ps.setInt(2, targetGlossaryId);
            ps.setInt(3, relationType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Helper method to map ResultSet to GlossaryXGlossary object
     */
    private GlossaryXGlossary mapResultSetToGlossaryXGlossary(ResultSet rs) throws SQLException {
        GlossaryXGlossary relationship = new GlossaryXGlossary();
        relationship.setId(rs.getInt("ID"));
        relationship.setSourceGlossaryId(rs.getObject("SourceGlossaryID", Integer.class));
        relationship.setTargetGlossaryId(rs.getObject("TargetGlossaryID", Integer.class));
        relationship.setRelationType(rs.getObject("RelationType", Integer.class));
        
        Timestamp createTimestamp = rs.getTimestamp("CreateDatetime");
        if (createTimestamp != null) {
            relationship.setCreateDatetime(createTimestamp.toLocalDateTime());
        }
        
        Timestamp updateTimestamp = rs.getTimestamp("LastUpdateDatetime");
        if (updateTimestamp != null) {
            relationship.setLastUpdateDatetime(updateTimestamp.toLocalDateTime());
        }
        
        relationship.setLastUpdateUserId(rs.getObject("LastUpdateUser_ID", Integer.class));
        return relationship;
    }
}
