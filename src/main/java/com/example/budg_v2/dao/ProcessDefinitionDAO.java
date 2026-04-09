package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProcessDefinition;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for process_definition table operations
 */
public class ProcessDefinitionDAO {

    /**
     * Create a new process definition
     */
    public int create(ProcessDefinition pd) throws SQLException {
        String sql = "INSERT INTO process_definition (PrimaryName, Reference, Is_Default, Description, " +
                "Status, LastUserChange, Entity_ID, Created_At, Updated_At) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, pd.getPrimaryName());
            stmt.setString(2, pd.getReference());
            stmt.setBoolean(3, pd.isDefault());
            stmt.setString(4, pd.getDescription());
            stmt.setString(5, pd.getStatus());
            stmt.setInt(6, pd.getLastUserChange());
            stmt.setInt(7, pd.getEntityId());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create process definition, no ID obtained");
    }

    /**
     * Update an existing process definition
     */
    public void update(ProcessDefinition pd) throws SQLException {
        String sql = "UPDATE process_definition SET PrimaryName = ?, Reference = ?, Is_Default = ?, " +
                "Description = ?, Status = ?, LastUserChange = ?, Entity_ID = ?, Updated_At = NOW() " +
                "WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pd.getPrimaryName());
            stmt.setString(2, pd.getReference());
            stmt.setBoolean(3, pd.isDefault());
            stmt.setString(4, pd.getDescription());
            stmt.setString(5, pd.getStatus());
            stmt.setInt(6, pd.getLastUserChange());
            stmt.setInt(7, pd.getEntityId());
            stmt.setInt(8, pd.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Find process definition by ID
     */
    public ProcessDefinition findById(int id) throws SQLException {
        String sql = "SELECT * FROM process_definition WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find all process definitions
     */
    public List<ProcessDefinition> findAll() throws SQLException {
        String sql = "SELECT * FROM process_definition ORDER BY Created_At DESC";
        List<ProcessDefinition> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Find process definitions by entity ID
     */
    public List<ProcessDefinition> findByEntityId(int entityId) throws SQLException {
        String sql = "SELECT * FROM process_definition WHERE Entity_ID = ? ORDER BY Created_At DESC";
        List<ProcessDefinition> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, entityId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Check if workflow name is unique for entity
     */
    public boolean isNameUnique(String name, int entityId, Integer excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM process_definition WHERE PrimaryName = ? AND Entity_ID = ?";
        if (excludeId != null) {
            sql += " AND ID != ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setInt(2, entityId);
            if (excludeId != null) {
                stmt.setInt(3, excludeId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        }
        return false;
    }

    /**
     * Delete process definition
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM process_definition WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    /**
     * Map ResultSet to ProcessDefinition object
     */
    private ProcessDefinition mapResultSet(ResultSet rs) throws SQLException {
        ProcessDefinition pd = new ProcessDefinition();
        pd.setId(rs.getInt("ID"));
        pd.setPrimaryName(rs.getString("PrimaryName"));
        pd.setReference(rs.getString("Reference"));
        pd.setDefault(rs.getBoolean("Is_Default"));
        pd.setDescription(rs.getString("Description"));
        pd.setStatus(rs.getString("Status"));
        pd.setLastUserChange(rs.getInt("LastUserChange"));
        pd.setEntityId(rs.getInt("Entity_ID"));
        pd.setCreatedAt(rs.getTimestamp("Created_At"));
        pd.setUpdatedAt(rs.getTimestamp("Updated_At"));
        return pd;
    }
}
