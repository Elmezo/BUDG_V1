package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowNotification;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for workflow_notification table operations
 */
public class WorkflowNotificationDAO {

    /**
     * Create a new notification
     */
    public Long create(WorkflowNotification notification) throws SQLException {
        String sql = "INSERT INTO workflow_notification (notification_rule_id, workflow_task_id, " +
                "change_request_id, recipient_user_id, event_type, title, message, channel, category, object_id, facet_type, `read`, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (notification.getNotificationRuleId() != null) {
                stmt.setLong(1, notification.getNotificationRuleId());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }

            if (notification.getWorkflowTaskId() != null) {
                stmt.setInt(2, notification.getWorkflowTaskId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            if (notification.getChangeRequestId() != null) {
                stmt.setInt(3, notification.getChangeRequestId());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }

            stmt.setInt(4, notification.getRecipientUserId());
            stmt.setString(5, notification.getEventType());
            stmt.setString(6, notification.getTitle());
            stmt.setString(7, notification.getMessage());
            stmt.setString(8, notification.getChannel());
            stmt.setString(9, notification.getCategory() != null ? notification.getCategory() : "workflow");
            
            if (notification.getObjectId() != null) {
                stmt.setInt(10, notification.getObjectId());
            } else {
                stmt.setNull(10, Types.INTEGER);
            }
            
            if (notification.getFacetType() != null) {
                stmt.setString(11, notification.getFacetType());
            } else {
                stmt.setNull(11, Types.VARCHAR);
            }
            
            stmt.setBoolean(12, notification.getRead() != null ? notification.getRead() : false);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create notification, no ID obtained");
    }

    /**
     * Find unread notifications by user ID and category
     * Category can be: "workflow", "catalog", "roles", "bulk_upload"
     */
    public List<WorkflowNotification> findUnreadByUserId(int userId, String category) throws SQLException {
        String sql = "SELECT * FROM workflow_notification " +
                "WHERE recipient_user_id = ? AND `read` = FALSE AND channel = 'ui' AND category = ? " +
                "ORDER BY created_at DESC LIMIT 100";

        List<WorkflowNotification> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, category != null ? category : "workflow");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find all notifications by user ID and category (both read and unread)
     * Category can be: "workflow", "catalog", "roles", "bulk_upload"
     */
    public List<WorkflowNotification> findByUserId(int userId, String category) throws SQLException {
        String sql = "SELECT * FROM workflow_notification " +
                "WHERE recipient_user_id = ? AND channel = 'ui' AND category = ? " +
                "ORDER BY created_at DESC LIMIT 100";

        List<WorkflowNotification> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, category != null ? category : "workflow");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Mark notification as read
     */
    public void markAsRead(Long notificationId) throws SQLException {
        String sql = "UPDATE workflow_notification SET `read` = TRUE WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, notificationId);
            stmt.executeUpdate();
        }
    }

    /**
     * Mark all notifications as read for a user and category
     */
    public void markAllAsRead(int userId, String category) throws SQLException {
        String sql = "UPDATE workflow_notification SET `read` = TRUE " +
                "WHERE recipient_user_id = ? AND `read` = FALSE AND channel = 'ui' AND category = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, category != null ? category : "workflow");
            stmt.executeUpdate();
        }
    }

    /**
     * Get unread count for a user by category
     */
    public int getUnreadCount(int userId, String category) throws SQLException {
        String sql = "SELECT COUNT(*) FROM workflow_notification " +
                "WHERE recipient_user_id = ? AND `read` = FALSE AND channel = 'ui' AND category = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, category != null ? category : "workflow");

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Get unread counts for all categories
     */
    public Map<String, Integer> getUnreadCountsByCategory(int userId) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();

        // Get counts for each category
        counts.put("workflow", getUnreadCount(userId, "workflow"));
        counts.put("catalog", getUnreadCount(userId, "catalog"));
        counts.put("roles", getUnreadCount(userId, "roles"));
        counts.put("bulk_upload", getUnreadCount(userId, "bulk_upload"));

        return counts;
    }

    /**
     * Find notification by ID
     */
    public WorkflowNotification findById(Long notificationId) throws SQLException {
        String sql = "SELECT * FROM workflow_notification WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, notificationId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Update notification
     */
    public void update(WorkflowNotification notification) throws SQLException {
        String sql = "UPDATE workflow_notification SET email_sent = ?, email_sent_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (notification.getEmailSent() != null) {
                stmt.setBoolean(1, notification.getEmailSent());
            } else {
                stmt.setNull(1, Types.BOOLEAN);
            }

            if (notification.getEmailSentAt() != null) {
                stmt.setTimestamp(2, notification.getEmailSentAt());
            } else {
                stmt.setNull(2, Types.TIMESTAMP);
            }

            stmt.setLong(3, notification.getId());
            stmt.executeUpdate();
        }
    }

    /**
     * Delete a notification
     */
    public void delete(Long notificationId) throws SQLException {
        String sql = "DELETE FROM workflow_notification WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, notificationId);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete all notifications for a user and category
     * @param userId User ID
     * @param category Category (e.g., "workflow", "catalog", "roles", "bulk_upload")
     * @return Number of notifications deleted
     */
    public int deleteAllByUserIdAndCategory(int userId, String category) throws SQLException {
        String sql = "DELETE FROM workflow_notification " +
                "WHERE recipient_user_id = ? AND channel = 'ui' AND category = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, category != null ? category : "workflow");
            return stmt.executeUpdate();
        }
    }

    /**
     * Delete old notifications based on age and category
     * @param daysOld Number of days old (notifications older than this will be deleted)
     * @param categories List of categories to include in deletion (e.g., "catalog", "workflow", "bulk_upload")
     * @return Number of notifications deleted
     */
    public int deleteOldNotifications(int daysOld, List<String> categories) throws SQLException {
        if (categories == null || categories.isEmpty()) {
            return 0;
        }

        // Build the IN clause with placeholders
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < categories.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }

        String sql = "DELETE FROM workflow_notification " +
                "WHERE category IN (" + placeholders.toString() + ") " +
                "AND created_at < (CURRENT_TIMESTAMP - INTERVAL ? DAY)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Set category parameters
            for (int i = 0; i < categories.size(); i++) {
                stmt.setString(i + 1, categories.get(i));
            }
            // Set days parameter
            stmt.setInt(categories.size() + 1, daysOld);

            return stmt.executeUpdate();
        }
    }

    /**
     * Map ResultSet to WorkflowNotification object
     */
    private WorkflowNotification mapResultSet(ResultSet rs) throws SQLException {
        WorkflowNotification notification = new WorkflowNotification();
        notification.setId(rs.getLong("id"));

        Long ruleId = rs.getLong("notification_rule_id");
        if (!rs.wasNull()) {
            notification.setNotificationRuleId(ruleId);
        }

        notification.setWorkflowTaskId(rs.getInt("workflow_task_id"));

        int changeRequestId = rs.getInt("change_request_id");
        if (!rs.wasNull()) {
            notification.setChangeRequestId(changeRequestId);
        }

        notification.setRecipientUserId(rs.getInt("recipient_user_id"));
        notification.setEventType(rs.getString("event_type"));
        notification.setTitle(rs.getString("title"));
        notification.setMessage(rs.getString("message"));
        notification.setChannel(rs.getString("channel"));
        
        // Get category (may not exist in older databases, default to workflow)
        try {
            String category = rs.getString("category");
            notification.setCategory(category != null ? category : "workflow");
        } catch (SQLException e) {
            // Column may not exist yet, default to workflow
            notification.setCategory("workflow");
        }

        // Get email_sent and email_sent_at (may be null if column doesn't exist yet)
        try {
            boolean emailSent = rs.getBoolean("email_sent");
            if (!rs.wasNull()) {
                notification.setEmailSent(emailSent);
            }
        } catch (SQLException e) {
            // Column may not exist yet, ignore
        }

        try {
            Timestamp emailSentAt = rs.getTimestamp("email_sent_at");
            if (emailSentAt != null) {
                notification.setEmailSentAt(emailSentAt);
            }
        } catch (SQLException e) {
            // Column may not exist yet, ignore
        }

        // Get object_id and facet_type (may not exist in older databases)
        try {
            int objectId = rs.getInt("object_id");
            if (!rs.wasNull()) {
                notification.setObjectId(objectId);
            }
        } catch (SQLException e) {
            // Column may not exist yet, ignore
        }

        try {
            String facetType = rs.getString("facet_type");
            if (facetType != null) {
                notification.setFacetType(facetType);
            }
        } catch (SQLException e) {
            // Column may not exist yet, ignore
        }

        notification.setRead(rs.getBoolean("read"));
        notification.setCreatedAt(rs.getTimestamp("created_at"));
        return notification;
    }
}
