package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProcessDefinitionObjectScope;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps process_definition IDs to a concrete facet object (object-private workflows).
 */
public class ProcessDefinitionObjectScopeDAO {

    public static final String TABLE = "process_definition_object_scope";

    /**
     * Create table if missing (MySQL).
     */
    public static void ensureTableExists(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "ID INT AUTO_INCREMENT PRIMARY KEY, " +
                "Process_Definition_ID INT NOT NULL, " +
                "Facet_Type VARCHAR(64) NOT NULL, " +
                "Object_ID INT NOT NULL, " +
                "Created_At TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "Updated_At TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uq_process_definition_object_scope_pd (Process_Definition_ID), " +
                "KEY idx_facet_object (Facet_Type, Object_ID)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    public void insert(int processDefinitionId, String facetType, int objectId) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (Process_Definition_ID, Facet_Type, Object_ID, Created_At, Updated_At) " +
                "VALUES (?, ?, ?, NOW(), NOW())";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, processDefinitionId);
            stmt.setString(2, facetType);
            stmt.setInt(3, objectId);
            stmt.executeUpdate();
        }
    }

    public Optional<ProcessDefinitionObjectScope> findByProcessDefinitionId(int processDefinitionId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE + " WHERE Process_Definition_ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, processDefinitionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Integer> findProcessDefinitionIdsForObject(String facetType, int objectId) throws SQLException {
        String sql = "SELECT Process_Definition_ID FROM " + TABLE +
                " WHERE Facet_Type = ? AND Object_ID = ? ORDER BY ID ASC";
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, facetType);
            stmt.setInt(2, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("Process_Definition_ID"));
                }
            }
        }
        return ids;
    }

    public void deleteByProcessDefinitionId(int processDefinitionId) throws SQLException {
        String sql = "DELETE FROM " + TABLE + " WHERE Process_Definition_ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, processDefinitionId);
            stmt.executeUpdate();
        }
    }

    private static ProcessDefinitionObjectScope map(ResultSet rs) throws SQLException {
        ProcessDefinitionObjectScope row = new ProcessDefinitionObjectScope();
        row.setId(rs.getInt("ID"));
        row.setProcessDefinitionId(rs.getInt("Process_Definition_ID"));
        row.setFacetType(rs.getString("Facet_Type"));
        row.setObjectId(rs.getInt("Object_ID"));
        row.setCreatedAt(rs.getTimestamp("Created_At"));
        row.setUpdatedAt(rs.getTimestamp("Updated_At"));
        return row;
    }
}
