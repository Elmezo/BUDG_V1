package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class GlossaryXSystemDAO {

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
}
