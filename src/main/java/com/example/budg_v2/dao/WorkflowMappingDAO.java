package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowMapping;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for changerequest_workflow_crtype table operations
 */
public class WorkflowMappingDAO {

    /**
     * Create workflow to CR type mapping
     * Note: For Type 2, caller should call this twice with different CR_Type values
     */
    public int create(WorkflowMapping mapping) throws SQLException {
        String sql = "INSERT INTO changerequest_workflow_crtype " +
                "(Process_Definition_ID, CR_Type, Entity_ID, LastUserChange, Created_At, Updated_At) " +
                "VALUES (?, ?, ?, ?, NOW(), NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, mapping.getProcessDefinitionId());
            stmt.setString(2, mapping.getCrType());
            stmt.setInt(3, mapping.getEntityId());
            stmt.setInt(4, mapping.getLastUserChange());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create workflow mapping, no ID obtained");
    }

    /**
     * Update workflow mapping
     */
    public void update(WorkflowMapping mapping) throws SQLException {
        String sql = "UPDATE changerequest_workflow_crtype SET CR_Type = ?, Entity_ID = ?, " +
                "LastUserChange = ?, Updated_At = NOW() WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, mapping.getCrType());
            stmt.setInt(2, mapping.getEntityId());
            stmt.setInt(3, mapping.getLastUserChange());
            stmt.setInt(4, mapping.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Find mappings by process definition ID
     */
    public List<WorkflowMapping> findByProcessDefinitionId(int processDefId) throws SQLException {
        String sql = "SELECT * FROM changerequest_workflow_crtype WHERE Process_Definition_ID = ?";
        List<WorkflowMapping> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, processDefId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find mapping by ID
     */
    public WorkflowMapping findById(int id) throws SQLException {
        String sql = "SELECT * FROM changerequest_workflow_crtype WHERE ID = ?";

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
     * Delete all mappings for a process definition
     */
    public void deleteByProcessDefinitionId(int processDefId) throws SQLException {
        String sql = "DELETE FROM changerequest_workflow_crtype WHERE Process_Definition_ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, processDefId);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete specific mapping
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM changerequest_workflow_crtype WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    /**
     * Map ResultSet to WorkflowMapping object
     */
    private WorkflowMapping mapResultSet(ResultSet rs) throws SQLException {
        WorkflowMapping mapping = new WorkflowMapping();
        mapping.setId(rs.getInt("ID"));
        mapping.setProcessDefinitionId(rs.getInt("Process_Definition_ID"));
        mapping.setCrType(rs.getString("CR_Type"));
        mapping.setEntityId(rs.getInt("Entity_ID"));
        mapping.setLastUserChange(rs.getInt("LastUserChange"));
        mapping.setCreatedAt(rs.getTimestamp("Created_At"));
        mapping.setUpdatedAt(rs.getTimestamp("Updated_At"));
        return mapping;
    }
}
