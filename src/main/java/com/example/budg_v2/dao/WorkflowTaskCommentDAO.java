package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowTaskComment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for workflow_task_comment table operations
 */
public class WorkflowTaskCommentDAO {

    /**
     * Create a new workflow task comment
     */
    public int create(WorkflowTaskComment comment) throws SQLException {
        String sql = "INSERT INTO workflow_task_comment (Workflow_Task_ID, Comment_Text, Created_By, Created_At) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, comment.getWorkflowTaskId());
            stmt.setString(2, comment.getCommentText());
            stmt.setInt(3, comment.getCreatedBy());
            
            if (comment.getCreatedAt() != null) {
                stmt.setTimestamp(4, comment.getCreatedAt());
            } else {
                stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create workflow task comment, no ID obtained");
    }

    /**
     * Find all comments for a specific task
     */
    public List<WorkflowTaskComment> findByTaskId(int taskId) throws SQLException {
        String sql = "SELECT * FROM workflow_task_comment WHERE Workflow_Task_ID = ? ORDER BY Created_At ASC";
        List<WorkflowTaskComment> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, taskId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find all comments for all tasks in a workflow instance
     */
    public List<WorkflowTaskComment> findByInstanceId(int instanceId) throws SQLException {
        String sql = "SELECT c.* FROM workflow_task_comment c " +
                "INNER JOIN workflow_instance_task t ON c.Workflow_Task_ID = t.ID " +
                "WHERE t.Workflow_Instance_ID = ? ORDER BY c.Created_At ASC";
        List<WorkflowTaskComment> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, instanceId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Map ResultSet to WorkflowTaskComment object
     */
    private WorkflowTaskComment mapResultSet(ResultSet rs) throws SQLException {
        WorkflowTaskComment comment = new WorkflowTaskComment();
        comment.setId(rs.getInt("ID"));
        comment.setWorkflowTaskId(rs.getInt("Workflow_Task_ID"));
        comment.setCommentText(rs.getString("Comment_Text"));
        comment.setCreatedBy(rs.getInt("Created_By"));
        comment.setCreatedAt(rs.getTimestamp("Created_At"));
        return comment;
    }
}
