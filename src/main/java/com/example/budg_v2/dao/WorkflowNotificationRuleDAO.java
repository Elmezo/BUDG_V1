package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowNotificationRule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for workflow_notification_rule table operations
 */
public class WorkflowNotificationRuleDAO {

    private static final Gson gson = new Gson();
    private static final Type listStringType = new TypeToken<List<String>>(){}.getType();

    /**
     * Create a new notification rule
     */
    public Long create(WorkflowNotificationRule rule) throws SQLException {
        String sql = "INSERT INTO workflow_notification_rule (module, event_type, recipient_role, " +
                "recipient_user_id, channels, delivery_mode, active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, rule.getModule());
            stmt.setString(2, rule.getEventType());
            
            if (rule.getRecipientRole() != null) {
                stmt.setString(3, rule.getRecipientRole());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            
            if (rule.getRecipientUserId() != null) {
                stmt.setLong(4, rule.getRecipientUserId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }
            
            // Convert List<String> to JSON string
            String channelsJson = gson.toJson(rule.getChannels());
            stmt.setString(5, channelsJson);
            
            // Set delivery_mode (default to IMMEDIATE if not specified)
            String deliveryMode = rule.getDeliveryMode();
            if (deliveryMode == null || deliveryMode.isEmpty()) {
                deliveryMode = "IMMEDIATE";
            }
            stmt.setString(6, deliveryMode);
            
            stmt.setBoolean(7, rule.getActive() != null ? rule.getActive() : true);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create notification rule, no ID obtained");
    }

    /**
     * Update an existing notification rule
     */
    public void update(WorkflowNotificationRule rule) throws SQLException {
        String sql = "UPDATE workflow_notification_rule SET module = ?, event_type = ?, " +
                "recipient_role = ?, recipient_user_id = ?, channels = ?, delivery_mode = ?, active = ?, " +
                "updated_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, rule.getModule());
            stmt.setString(2, rule.getEventType());
            
            if (rule.getRecipientRole() != null) {
                stmt.setString(3, rule.getRecipientRole());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            
            if (rule.getRecipientUserId() != null) {
                stmt.setLong(4, rule.getRecipientUserId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }
            
            // Convert List<String> to JSON string
            String channelsJson = gson.toJson(rule.getChannels());
            stmt.setString(5, channelsJson);
            
            // Set delivery_mode (default to IMMEDIATE if not specified)
            String deliveryMode = rule.getDeliveryMode();
            if (deliveryMode == null || deliveryMode.isEmpty()) {
                deliveryMode = "IMMEDIATE";
            }
            stmt.setString(6, deliveryMode);
            
            stmt.setBoolean(7, rule.getActive() != null ? rule.getActive() : true);
            stmt.setLong(8, rule.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Delete a notification rule
     */
    public void delete(Long id) throws SQLException {
        String sql = "DELETE FROM workflow_notification_rule WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    /**
     * Find notification rule by ID
     */
    public WorkflowNotificationRule findById(Long id) throws SQLException {
        String sql = "SELECT * FROM workflow_notification_rule WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find all notification rules
     */
    public List<WorkflowNotificationRule> findAll() throws SQLException {
        String sql = "SELECT * FROM workflow_notification_rule ORDER BY created_at DESC";
        List<WorkflowNotificationRule> list = new ArrayList<>();

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
     * Find rules by module and event type
     */
    public List<WorkflowNotificationRule> findByModuleAndEventType(String module, String eventType) throws SQLException {
        String sql = "SELECT * FROM workflow_notification_rule WHERE module = ? AND event_type = ? ORDER BY created_at DESC";
        List<WorkflowNotificationRule> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, module);
            stmt.setString(2, eventType);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find active rules for a specific event
     * Returns rules that match the module (or "*") and event type, ordered by specificity
     */
    public List<WorkflowNotificationRule> findActiveRulesForEvent(String module, String eventType) throws SQLException {
        String sql = "SELECT * FROM workflow_notification_rule " +
                "WHERE active = TRUE AND event_type = ? " +
                "AND (module = ? OR module = '*') " +
                "ORDER BY CASE WHEN module = '*' THEN 1 ELSE 0 END, created_at DESC";
        List<WorkflowNotificationRule> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, eventType);
            stmt.setString(2, module);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Map ResultSet to WorkflowNotificationRule object
     */
    private WorkflowNotificationRule mapResultSet(ResultSet rs) throws SQLException {
        WorkflowNotificationRule rule = new WorkflowNotificationRule();
        rule.setId(rs.getLong("id"));
        rule.setModule(rs.getString("module"));
        rule.setEventType(rs.getString("event_type"));
        rule.setRecipientRole(rs.getString("recipient_role"));
        
        Long recipientUserId = rs.getLong("recipient_user_id");
        if (!rs.wasNull()) {
            rule.setRecipientUserId(recipientUserId);
        }
        
        // Parse JSON channels to List<String>
        String channelsJson = rs.getString("channels");
        if (channelsJson != null && !channelsJson.isEmpty()) {
            try {
                List<String> channels = gson.fromJson(channelsJson, listStringType);
                rule.setChannels(channels);
            } catch (Exception e) {
                // Fallback: treat as empty list
                rule.setChannels(new ArrayList<>());
            }
        } else {
            rule.setChannels(new ArrayList<>());
        }
        
        // Get delivery_mode (default to IMMEDIATE if null)
        String deliveryMode = rs.getString("delivery_mode");
        if (deliveryMode == null || deliveryMode.isEmpty()) {
            deliveryMode = "IMMEDIATE";
        }
        rule.setDeliveryMode(deliveryMode);
        
        rule.setActive(rs.getBoolean("active"));
        rule.setCreatedAt(rs.getTimestamp("created_at"));
        rule.setUpdatedAt(rs.getTimestamp("updated_at"));
        return rule;
    }
}

