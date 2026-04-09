package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowInstance;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for workflow_instance table operations
 */
public class WorkflowInstanceDAO {

    /**
     * Create a new workflow instance
     */
    public int create(WorkflowInstance instance) throws SQLException {
        String sql = "INSERT INTO workflow_instance (Process_Definition_ID, ChangeRequest_ID, " +
                "Current_Bpmn_Node_Id, Status, Started_At) VALUES (?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, instance.getProcessDefinitionId());
            if (instance.getChangeRequestId() != null) {
                stmt.setInt(2, instance.getChangeRequestId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, instance.getCurrentBpmnNodeId());
            stmt.setString(4, instance.getStatus());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create workflow instance, no ID obtained");
    }

    /**
     * Update workflow instance
     */
    public void update(WorkflowInstance instance) throws SQLException {
        String sql = "UPDATE workflow_instance SET Current_Bpmn_Node_Id = ?, Status = ?, " +
                "Ended_At = ? WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, instance.getCurrentBpmnNodeId());
            stmt.setString(2, instance.getStatus());
            if (instance.getEndedAt() != null) {
                stmt.setTimestamp(3, instance.getEndedAt());
            } else {
                stmt.setNull(3, Types.TIMESTAMP);
            }
            stmt.setInt(4, instance.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Find workflow instance by ID
     */
    public WorkflowInstance findById(int id) throws SQLException {
        String sql = "SELECT * FROM workflow_instance WHERE ID = ?";

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
     * Find workflow instance by change request ID
     */
    public WorkflowInstance findByChangeRequestId(int crId) throws SQLException {
        String sql = "SELECT * FROM workflow_instance WHERE ChangeRequest_ID = ? ORDER BY Started_At DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, crId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find all instances for a process definition
     */
    public List<WorkflowInstance> findByProcessDefinitionId(int processDefId) throws SQLException {
        String sql = "SELECT * FROM workflow_instance WHERE Process_Definition_ID = ? ORDER BY Started_At DESC";
        List<WorkflowInstance> list = new ArrayList<>();

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
     * Map ResultSet to WorkflowInstance object
     */
    private WorkflowInstance mapResultSet(ResultSet rs) throws SQLException {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(rs.getInt("ID"));
        instance.setProcessDefinitionId(rs.getInt("Process_Definition_ID"));

        int crId = rs.getInt("ChangeRequest_ID");
        if (!rs.wasNull()) {
            instance.setChangeRequestId(crId);
        }

        instance.setCurrentBpmnNodeId(rs.getString("Current_Bpmn_Node_Id"));
        instance.setStatus(rs.getString("Status"));
        instance.setStartedAt(rs.getTimestamp("Started_At"));
        instance.setEndedAt(rs.getTimestamp("Ended_At"));
        return instance;
    }

    /**
     * Delete workflow instance by ID
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM workflow_instance WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }
}
