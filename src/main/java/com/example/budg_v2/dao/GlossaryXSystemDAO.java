package com.example.budg_v2.dao;

import com.example.budg_v2.audit.AuditHistoryWriter;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class GlossaryXSystemDAO {

    private static final String GLOSSARY_AUDIT_TABLE = "glossary_audit_history";
    private static final String SYSTEM_AUDIT_TABLE = "system_audit_history";
    private static final String OBJECT_STRATEGIC = "Glossary X System";
    private static final String OBJECT_DATA_CONTENT = "System X Glossary";
    private static final String EVENT_LINK = "Strategic Source";
    private static final String EVENT_DATA_CONTENT = "Data Content Summary";

    public List<Map<String, Object>> getByGlossaryId(int glossaryId) throws SQLException {
        String sql = "SELECT gxs.ID, gxs.GlossaryID, gxs.SystemID, gxs.Strategic_DatasetID, gxs.Relation_TypeID, " +
                "s.Name AS systemName, d.PrimaryName AS datasetName, " +
                "rt.PrimaryName AS relationTypeName " +
                "FROM glossary_x_system gxs " +
                "LEFT JOIN system s ON s.id = gxs.SystemID " +
                "LEFT JOIN dataset d ON d.ID = gxs.Strategic_DatasetID " +
                "LEFT JOIN glossary_x_system_relationtype rt ON rt.ID = gxs.Relation_TypeID " +
                "WHERE gxs.GlossaryID = ? AND (gxs.Link_Source = 'glossary' OR gxs.Link_Source IS NULL) " +
                "ORDER BY gxs.ID";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("glossaryId", rs.getInt("GlossaryID"));
                    row.put("systemId", rs.getInt("SystemID"));
                    row.put("datasetId", rs.getInt("Strategic_DatasetID"));
                    row.put("relationTypeId", rs.getInt("Relation_TypeID"));
                    row.put("systemName", rs.getString("systemName"));
                    row.put("datasetName", rs.getString("datasetName"));
                    row.put("relationTypeName", rs.getString("relationTypeName"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * Check if an identical strategic source relationship already exists for this glossary.
     * Equality is based on the tuple (SystemID, Strategic_DatasetID, Relation_TypeID),
     * treating nulls as equal to nulls. Only considers relationships created from the
     * glossary side (Link_Source = 'glossary' or NULL).
     */
    public boolean existsDuplicate(int glossaryId, Integer systemId, Integer datasetId, Integer relationTypeId) throws SQLException {
        String sql = "SELECT 1 FROM glossary_x_system " +
                "WHERE GlossaryID = ? " +
                "AND (Link_Source = 'glossary' OR Link_Source IS NULL) " +
                "AND COALESCE(SystemID, -1) = COALESCE(?, -1) " +
                "AND COALESCE(Strategic_DatasetID, -1) = COALESCE(?, -1) " +
                "AND COALESCE(Relation_TypeID, -1) = COALESCE(?, -1) " +
                "LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.setObject(2, systemId);
            ps.setObject(3, datasetId);
            ps.setObject(4, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Same as existsDuplicate, but excludes a specific record id (for updates).
     */
    public boolean existsDuplicateExcludingId(int excludeId, int glossaryId, Integer systemId, Integer datasetId, Integer relationTypeId) throws SQLException {
        String sql = "SELECT 1 FROM glossary_x_system " +
                "WHERE ID <> ? AND GlossaryID = ? " +
                "AND (Link_Source = 'glossary' OR Link_Source IS NULL) " +
                "AND COALESCE(SystemID, -1) = COALESCE(?, -1) " +
                "AND COALESCE(Strategic_DatasetID, -1) = COALESCE(?, -1) " +
                "AND COALESCE(Relation_TypeID, -1) = COALESCE(?, -1) " +
                "LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, excludeId);
            ps.setInt(2, glossaryId);
            ps.setObject(3, systemId);
            ps.setObject(4, datasetId);
            ps.setObject(5, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Map<String, Object>> getRelationTypes() throws SQLException {
        String sql = "SELECT ID, PrimaryName, Description FROM glossary_x_system_relationtype " +
                "ORDER BY PrimaryName";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("PrimaryName"));
                    row.put("description", rs.getString("Description"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> getSystems() throws SQLException {
        String sql = "SELECT id, Name, Description FROM system ORDER BY Name";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("name", rs.getString("Name"));
                    row.put("description", rs.getString("Description"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * Returns systems that can be added as strategic source for the given glossary.
     * Excludes systems that already have this glossary linked from Data Content Summary (Link_Source = 'system').
     */
    public List<Map<String, Object>> getSystemsForStrategicSource(int glossaryId) throws SQLException {
        String sql = "SELECT s.id, s.Name, s.Description FROM system s " +
                "WHERE s.id NOT IN (" +
                "  SELECT gxs.SystemID FROM glossary_x_system gxs " +
                "  WHERE gxs.GlossaryID = ? AND gxs.Link_Source = 'system'" +
                ") ORDER BY s.Name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("name", rs.getString("Name"));
                    row.put("description", rs.getString("Description"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * Check if a row exists for this glossary+system with Link_Source = 'system' (data content side).
     * Used to reject adding the same link from strategic source when already added from system side.
     */
    public boolean existsWithLinkSourceSystem(int glossaryId, int systemId) throws SQLException {
        String sql = "SELECT 1 FROM glossary_x_system WHERE GlossaryID = ? AND SystemID = ? AND Link_Source = 'system' LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.setInt(2, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Map<String, Object>> getDatasetsBySystemId(int systemId) throws SQLException {
        String sql = "SELECT ID, PrimaryName, definition FROM dataset " +
                "WHERE MasterSource = ? AND DeletedDatetime IS NULL ORDER BY PrimaryName";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("PrimaryName"));
                    row.put("definition", rs.getString("definition"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    // Alternative method to get all datasets if no system-specific datasets found
    public List<Map<String, Object>> getAllDatasets() throws SQLException {
        String sql = "SELECT ID, PrimaryName, definition FROM dataset WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("ID", rs.getInt("ID"));
                    row.put("PrimaryName", rs.getString("PrimaryName"));
                    row.put("definition", rs.getString("definition"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * Get a strategic source record by its ID
     */
    public Map<String, Object> getById(int id) throws SQLException {
        String sql = "SELECT gxs.ID, gxs.GlossaryID, gxs.SystemID, gxs.Strategic_DatasetID, gxs.Relation_TypeID, " +
                "s.Name AS systemName, d.PrimaryName AS datasetName, " +
                "rt.PrimaryName AS relationTypeName " +
                "FROM glossary_x_system gxs " +
                "LEFT JOIN system s ON s.id = gxs.SystemID " +
                "LEFT JOIN dataset d ON d.ID = gxs.Strategic_DatasetID " +
                "LEFT JOIN glossary_x_system_relationtype rt ON rt.ID = gxs.Relation_TypeID " +
                "WHERE gxs.ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("glossaryId", rs.getInt("GlossaryID"));
                    row.put("systemId", rs.getInt("SystemID"));
                    row.put("datasetId", rs.getInt("Strategic_DatasetID"));
                    row.put("relationTypeId", rs.getInt("Relation_TypeID"));
                    row.put("systemName", rs.getString("systemName"));
                    row.put("datasetName", rs.getString("datasetName"));
                    row.put("relationTypeName", rs.getString("relationTypeName"));
                    return row;
                }
                return null;
            }
        }
    }

    public int insert(int glossaryId, Integer systemId, Integer datasetId, Integer relationTypeId) throws SQLException {
        return insert(glossaryId, systemId, datasetId, relationTypeId, "glossary");
    }

    /**
     * Insert with explicit Link_Source. Use "glossary" for strategic source, "system" for data content summary.
     */
    public int insert(int glossaryId, Integer systemId, Integer datasetId, Integer relationTypeId, String linkSource) throws SQLException {
        String sql = "INSERT INTO glossary_x_system (GlossaryID, SystemID, Strategic_DatasetID, Relation_TypeID, Link_Source, " +
                "LastUpdate_Datetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, ?, NULL, NULL)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, glossaryId);
            ps.setObject(2, systemId);
            ps.setObject(3, datasetId);
            ps.setObject(4, relationTypeId);
            ps.setString(5, linkSource);
            
            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating glossary_x_system failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating glossary_x_system failed, no ID obtained.");
                }
            }
        }
    }

    public boolean update(int id, Integer systemId, Integer datasetId, Integer relationTypeId) throws SQLException {
        String sql = "UPDATE glossary_x_system SET SystemID = ?, Strategic_DatasetID = ?, " +
                "Relation_TypeID = ?, Link_Source = 'glossary', LastUpdate_Datetime = NOW(), LastUpdate_UserID = NULL " +
                "WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, systemId);
            ps.setObject(2, datasetId);
            ps.setObject(3, relationTypeId);
            ps.setInt(4, id);
            
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM glossary_x_system WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteByGlossaryId(int glossaryId) throws SQLException {
        String sql = "DELETE FROM glossary_x_system WHERE GlossaryID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            return ps.executeUpdate() > 0;
        }
    }

    // ----- Audit helpers ---------------------------------------------------

    private String getSystemName(Connection conn, Integer systemId) throws SQLException {
        if (systemId == null) return null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT Name FROM system WHERE id = ?")) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String getDatasetName(Connection conn, Integer datasetId) throws SQLException {
        if (datasetId == null) return null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT PrimaryName FROM dataset WHERE ID = ?")) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String getGlossaryName(Connection conn, Integer glossaryId) throws SQLException {
        if (glossaryId == null) return null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT Name FROM glossary WHERE ID = ?")) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String getRelationTypeName(Connection conn, Integer relationTypeId) throws SQLException {
        if (relationTypeId == null) return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT PrimaryName FROM glossary_x_system_relationtype WHERE ID = ?")) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String getUserFullName(Connection conn, Integer userId) throws SQLException {
        if (userId == null || userId <= 0) return "System";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CONCAT(COALESCE(First_Name,''), ' ', COALESCE(Last_Name,'')) FROM people WHERE ID = ?")) {
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
     * Log Added audit rows for a new Strategic Source relationship on the source glossary's history.
     * One row per populated non-FK column on the link (System, Strategic Dataset, Relationship Type, Link Source).
     * Blank values are skipped, so a system-only link yields 3 rows and a system+dataset link yields 4 rows.
     */
    public void logStrategicSourceAdded(int glossaryId, Integer systemId, Integer datasetId,
                                         Integer relationTypeId, Integer userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String author = getUserFullName(conn, userId);
            AuditHistoryWriter.logAdded(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                    OBJECT_STRATEGIC, EVENT_LINK, "System", getSystemName(conn, systemId), author);
            AuditHistoryWriter.logAdded(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                    OBJECT_STRATEGIC, EVENT_LINK, "Strategic Dataset", getDatasetName(conn, datasetId), author);
            AuditHistoryWriter.logAdded(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                    OBJECT_STRATEGIC, EVENT_LINK, "Relationship Type", getRelationTypeName(conn, relationTypeId), author);
            AuditHistoryWriter.logAdded(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                    OBJECT_STRATEGIC, EVENT_LINK, "Link Source", "Glossary", author);
        } catch (SQLException e) {
            System.err.println("[GlossaryXSystemDAO] Failed to log strategic-source-added audit: " + e.getMessage());
        }
    }

    /**
     * Log per-field Updated audit rows for a Strategic Source relationship change.
     */
    public void logStrategicSourceUpdated(int glossaryId,
                                          Integer oldSystemId, Integer newSystemId,
                                          Integer oldDatasetId, Integer newDatasetId,
                                          Integer oldRelationTypeId, Integer newRelationTypeId,
                                          Integer userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String author = getUserFullName(conn, userId);
            if (!Objects.equals(oldSystemId, newSystemId)) {
                AuditHistoryWriter.logUpdated(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                        OBJECT_STRATEGIC, EVENT_LINK, "System",
                        getSystemName(conn, oldSystemId), getSystemName(conn, newSystemId), author);
            }
            if (!Objects.equals(oldDatasetId, newDatasetId)) {
                AuditHistoryWriter.logUpdated(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                        OBJECT_STRATEGIC, EVENT_LINK, "Strategic Dataset",
                        getDatasetName(conn, oldDatasetId), getDatasetName(conn, newDatasetId), author);
            }
            if (!Objects.equals(oldRelationTypeId, newRelationTypeId)) {
                AuditHistoryWriter.logUpdated(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                        OBJECT_STRATEGIC, EVENT_LINK, "Relationship Type",
                        getRelationTypeName(conn, oldRelationTypeId),
                        getRelationTypeName(conn, newRelationTypeId), author);
            }
        } catch (SQLException e) {
            System.err.println("[GlossaryXSystemDAO] Failed to log strategic-source-updated audit: " + e.getMessage());
        }
    }

    /**
     * Log Deleted audit rows when a Strategic Source relationship is removed.
     */
    public void logStrategicSourceDeleted(int glossaryId, Integer systemId, Integer datasetId,
                                          Integer relationTypeId, Integer userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String author = getUserFullName(conn, userId);
            AuditHistoryWriter.logDeleted(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                    OBJECT_STRATEGIC, EVENT_LINK, "System", getSystemName(conn, systemId), author);
            AuditHistoryWriter.logDeleted(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                    OBJECT_STRATEGIC, EVENT_LINK, "Strategic Dataset", getDatasetName(conn, datasetId), author);
            AuditHistoryWriter.logDeleted(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                    OBJECT_STRATEGIC, EVENT_LINK, "Relationship Type", getRelationTypeName(conn, relationTypeId), author);
            AuditHistoryWriter.logDeleted(conn, GLOSSARY_AUDIT_TABLE, glossaryId,
                    OBJECT_STRATEGIC, EVENT_LINK, "Link Source", "Glossary", author);
        } catch (SQLException e) {
            System.err.println("[GlossaryXSystemDAO] Failed to log strategic-source-deleted audit: " + e.getMessage());
        }
    }

    /**
     * Log Data Content Summary changes on the system's history (Object = System X Glossary).
     */
    public void logDataContentAdded(int systemId, Integer glossaryId, Integer datasetId,
                                     Integer relationTypeId, Integer userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String author = getUserFullName(conn, userId);
            AuditHistoryWriter.logAdded(conn, SYSTEM_AUDIT_TABLE, systemId,
                    OBJECT_DATA_CONTENT, EVENT_DATA_CONTENT, "Glossary", getGlossaryName(conn, glossaryId), author);
            AuditHistoryWriter.logAdded(conn, SYSTEM_AUDIT_TABLE, systemId,
                    OBJECT_DATA_CONTENT, EVENT_DATA_CONTENT, "Strategic Dataset", getDatasetName(conn, datasetId), author);
            AuditHistoryWriter.logAdded(conn, SYSTEM_AUDIT_TABLE, systemId,
                    OBJECT_DATA_CONTENT, EVENT_DATA_CONTENT, "Relationship Type", getRelationTypeName(conn, relationTypeId), author);
            AuditHistoryWriter.logAdded(conn, SYSTEM_AUDIT_TABLE, systemId,
                    OBJECT_DATA_CONTENT, EVENT_DATA_CONTENT, "Link Source", "System", author);
        } catch (SQLException e) {
            System.err.println("[GlossaryXSystemDAO] Failed to log data-content-added audit: " + e.getMessage());
        }
    }

    public void logDataContentDeleted(int systemId, Integer glossaryId, Integer datasetId,
                                      Integer relationTypeId, Integer userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String author = getUserFullName(conn, userId);
            AuditHistoryWriter.logDeleted(conn, SYSTEM_AUDIT_TABLE, systemId,
                    OBJECT_DATA_CONTENT, EVENT_DATA_CONTENT, "Glossary", getGlossaryName(conn, glossaryId), author);
            AuditHistoryWriter.logDeleted(conn, SYSTEM_AUDIT_TABLE, systemId,
                    OBJECT_DATA_CONTENT, EVENT_DATA_CONTENT, "Strategic Dataset", getDatasetName(conn, datasetId), author);
            AuditHistoryWriter.logDeleted(conn, SYSTEM_AUDIT_TABLE, systemId,
                    OBJECT_DATA_CONTENT, EVENT_DATA_CONTENT, "Relationship Type", getRelationTypeName(conn, relationTypeId), author);
            AuditHistoryWriter.logDeleted(conn, SYSTEM_AUDIT_TABLE, systemId,
                    OBJECT_DATA_CONTENT, EVENT_DATA_CONTENT, "Link Source", "System", author);
        } catch (SQLException e) {
            System.err.println("[GlossaryXSystemDAO] Failed to log data-content-deleted audit: " + e.getMessage());
        }
    }
}
