package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ActivityLog;
import com.example.budg_v2.model.ActivityLogDetail;
import com.example.budg_v2.model.ActivityLogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for Admin Activity Logs
 * Handles all database operations for activity logs and their details
 */
public class AdminActivityLogDAO {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(AdminActivityLogDAO.class);
    
    /**
     * Insert a new activity log and return its generated ID
     */
    public Long insertActivityLog(ActivityLog log) throws SQLException {
        String sql = "INSERT INTO admin_activity_log " +
                    "(setting, component, user_id, user_name, user_email, change_type, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, log.getSetting());
            stmt.setString(2, log.getComponent());
            stmt.setInt(3, log.getUserId());
            stmt.setString(4, log.getUserName());
            stmt.setString(5, log.getUserEmail());
            stmt.setString(6, log.getChangeType());
            
            if (log.getTimestamp() != null) {
                stmt.setTimestamp(7, Timestamp.valueOf(log.getTimestamp()));
            } else {
                stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            }
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Failed to insert activity log");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                } else {
                    throw new SQLException("Failed to get generated ID for activity log");
                }
            }
        }
    }
    
    /**
     * Bulk insert activity log details
     */
    public void insertActivityLogDetails(List<ActivityLogDetail> details) throws SQLException {
        if (details == null || details.isEmpty()) {
            return;
        }
        
        String sql = "INSERT INTO admin_activity_log_details " +
                    "(activity_log_id, field_name, old_value, new_value) " +
                    "VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (ActivityLogDetail detail : details) {
                stmt.setLong(1, detail.getActivityLogId());
                stmt.setString(2, detail.getFieldName());
                stmt.setString(3, detail.getOldValue());
                stmt.setString(4, detail.getNewValue());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
        }
    }
    
    /**
     * Get activity logs with filters
     */
    public List<ActivityLog> getActivityLogs(ActivityLogFilter filter) throws SQLException {
        List<ActivityLog> logs = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT id, setting, component, user_id, user_name, user_email, change_type, timestamp " +
            "FROM admin_activity_log WHERE 1=1"
        );
        
        List<Object> params = new ArrayList<>();
        
        // Build WHERE clause dynamically
        if (filter.getSetting() != null && !filter.getSetting().isEmpty()) {
            sql.append(" AND setting = ?");
            params.add(filter.getSetting());
        }
        
        if (filter.getComponent() != null && !filter.getComponent().isEmpty()) {
            sql.append(" AND component = ?");
            params.add(filter.getComponent());
        }
        
        if (filter.getChangeType() != null && !filter.getChangeType().isEmpty()) {
            sql.append(" AND change_type = ?");
            params.add(filter.getChangeType());
        }
        
        if (filter.getUserId() != null) {
            sql.append(" AND user_id = ?");
            params.add(filter.getUserId());
        }
        
        if (filter.getUserName() != null && !filter.getUserName().isEmpty()) {
            sql.append(" AND user_name LIKE ?");
            params.add("%" + filter.getUserName() + "%");
        }
        
        if (filter.getUserEmail() != null && !filter.getUserEmail().isEmpty()) {
            sql.append(" AND user_email = ?");
            params.add(filter.getUserEmail());
        }
        
        if (filter.getFromDate() != null) {
            sql.append(" AND timestamp >= ?");
            params.add(Timestamp.valueOf(filter.getFromDate()));
        }
        
        if (filter.getToDate() != null) {
            sql.append(" AND timestamp <= ?");
            params.add(Timestamp.valueOf(filter.getToDate()));
        }
        
        // Search in details (requires JOIN)
        if (filter.getSearchDetails() != null && !filter.getSearchDetails().isEmpty()) {
            sql = new StringBuilder(
                "SELECT DISTINCT a.id, a.setting, a.component, a.user_id, a.user_name, " +
                "a.user_email, a.change_type, a.timestamp " +
                "FROM admin_activity_log a " +
                "LEFT JOIN admin_activity_log_details d ON a.id = d.activity_log_id " +
                "WHERE 1=1"
            );
            
            // Re-add all previous conditions
            if (filter.getSetting() != null && !filter.getSetting().isEmpty()) {
                sql.append(" AND a.setting = ?");
            }
            if (filter.getComponent() != null && !filter.getComponent().isEmpty()) {
                sql.append(" AND a.component = ?");
            }
            if (filter.getChangeType() != null && !filter.getChangeType().isEmpty()) {
                sql.append(" AND a.change_type = ?");
            }
            if (filter.getUserId() != null) {
                sql.append(" AND a.user_id = ?");
            }
            if (filter.getUserName() != null && !filter.getUserName().isEmpty()) {
                sql.append(" AND a.user_name LIKE ?");
            }
            if (filter.getUserEmail() != null && !filter.getUserEmail().isEmpty()) {
                sql.append(" AND a.user_email = ?");
            }
            if (filter.getFromDate() != null) {
                sql.append(" AND a.timestamp >= ?");
            }
            if (filter.getToDate() != null) {
                sql.append(" AND a.timestamp <= ?");
            }
            
            // Add search condition
            sql.append(" AND (a.setting LIKE ? OR a.component LIKE ? OR a.user_name LIKE ? " +
                      "OR a.user_email LIKE ? OR d.field_name LIKE ? " +
                      "OR d.old_value LIKE ? OR d.new_value LIKE ?)");
            String searchTerm = "%" + filter.getSearchDetails() + "%";
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
        }
        
        // Add ORDER BY
        sql.append(" ORDER BY ").append(filter.getSortBy() != null ? filter.getSortBy() : "timestamp");
        sql.append(" ").append(filter.getSortOrder() != null ? filter.getSortOrder() : "DESC");
        
        // Add LIMIT and OFFSET
        if (filter.getLimit() != null) {
            sql.append(" LIMIT ?");
            params.add(filter.getLimit());
            if (filter.getOffset() != null) {
                sql.append(" OFFSET ?");
                params.add(filter.getOffset());
            }
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            // Set parameters
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ActivityLog log = mapResultSetToActivityLog(rs);
                    logs.add(log);
                }
            }
        }
        
        return logs;
    }
    
    /**
     * Get total count of logs matching the filter (for pagination)
     */
    public int getActivityLogsCount(ActivityLogFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM admin_activity_log WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        // Build WHERE clause (same as getActivityLogs)
        if (filter.getSetting() != null && !filter.getSetting().isEmpty()) {
            sql.append(" AND setting = ?");
            params.add(filter.getSetting());
        }
        if (filter.getComponent() != null && !filter.getComponent().isEmpty()) {
            sql.append(" AND component = ?");
            params.add(filter.getComponent());
        }
        if (filter.getChangeType() != null && !filter.getChangeType().isEmpty()) {
            sql.append(" AND change_type = ?");
            params.add(filter.getChangeType());
        }
        if (filter.getUserId() != null) {
            sql.append(" AND user_id = ?");
            params.add(filter.getUserId());
        }
        if (filter.getUserName() != null && !filter.getUserName().isEmpty()) {
            sql.append(" AND user_name LIKE ?");
            params.add("%" + filter.getUserName() + "%");
        }
        if (filter.getUserEmail() != null && !filter.getUserEmail().isEmpty()) {
            sql.append(" AND user_email = ?");
            params.add(filter.getUserEmail());
        }
        if (filter.getFromDate() != null) {
            sql.append(" AND timestamp >= ?");
            params.add(Timestamp.valueOf(filter.getFromDate()));
        }
        if (filter.getToDate() != null) {
            sql.append(" AND timestamp <= ?");
            params.add(Timestamp.valueOf(filter.getToDate()));
        }
        
        // Handle search in details
        if (filter.getSearchDetails() != null && !filter.getSearchDetails().isEmpty()) {
            sql = new StringBuilder(
                "SELECT COUNT(DISTINCT a.id) FROM admin_activity_log a " +
                "LEFT JOIN admin_activity_log_details d ON a.id = d.activity_log_id WHERE 1=1"
            );
            
            // Re-add conditions with 'a.' prefix
            if (filter.getSetting() != null && !filter.getSetting().isEmpty()) {
                sql.append(" AND a.setting = ?");
            }
            if (filter.getComponent() != null && !filter.getComponent().isEmpty()) {
                sql.append(" AND a.component = ?");
            }
            if (filter.getChangeType() != null && !filter.getChangeType().isEmpty()) {
                sql.append(" AND a.change_type = ?");
            }
            if (filter.getUserId() != null) {
                sql.append(" AND a.user_id = ?");
            }
            if (filter.getUserName() != null && !filter.getUserName().isEmpty()) {
                sql.append(" AND a.user_name LIKE ?");
            }
            if (filter.getUserEmail() != null && !filter.getUserEmail().isEmpty()) {
                sql.append(" AND a.user_email = ?");
            }
            if (filter.getFromDate() != null) {
                sql.append(" AND a.timestamp >= ?");
            }
            if (filter.getToDate() != null) {
                sql.append(" AND a.timestamp <= ?");
            }
            
            sql.append(" AND (a.setting LIKE ? OR a.component LIKE ? OR a.user_name LIKE ? " +
                      "OR a.user_email LIKE ? OR d.field_name LIKE ? " +
                      "OR d.old_value LIKE ? OR d.new_value LIKE ?)");
            String searchTerm = "%" + filter.getSearchDetails() + "%";
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
            params.add(searchTerm);
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        return 0;
    }
    
    /**
     * Get details for a specific activity log
     */
    public List<ActivityLogDetail> getActivityLogDetails(Long activityLogId) throws SQLException {
        List<ActivityLogDetail> details = new ArrayList<>();
        String sql = "SELECT id, activity_log_id, field_name, old_value, new_value " +
                    "FROM admin_activity_log_details " +
                    "WHERE activity_log_id = ? " +
                    "ORDER BY id";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, activityLogId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ActivityLogDetail detail = new ActivityLogDetail();
                    detail.setId(rs.getLong("id"));
                    detail.setActivityLogId(rs.getLong("activity_log_id"));
                    detail.setFieldName(rs.getString("field_name"));
                    detail.setOldValue(rs.getString("old_value"));
                    detail.setNewValue(rs.getString("new_value"));
                    details.add(detail);
                }
            }
        }
        
        return details;
    }
    
    /**
     * Get distinct values for filter dropdowns
     */
    public List<String> getDistinctSettings() throws SQLException {
        return getDistinctValues("SELECT DISTINCT setting FROM admin_activity_log ORDER BY setting");
    }
    
    public List<String> getDistinctComponents() throws SQLException {
        return getDistinctValues("SELECT DISTINCT component FROM admin_activity_log ORDER BY component");
    }
    
    public List<String> getDistinctChangeTypes() throws SQLException {
        return getDistinctValues("SELECT DISTINCT change_type FROM admin_activity_log ORDER BY change_type");
    }
    
    public List<Map<String, Object>> getDistinctUsers() throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        String sql = "SELECT DISTINCT user_id, user_name, user_email " +
                    "FROM admin_activity_log " +
                    "ORDER BY user_name";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getInt("user_id"));
                user.put("name", rs.getString("user_name"));
                user.put("email", rs.getString("user_email"));
                users.add(user);
            }
        }
        
        return users;
    }
    
    private List<String> getDistinctValues(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        
        return values;
    }
    
    /**
     * Helper method to map ResultSet to ActivityLog
     */
    private ActivityLog mapResultSetToActivityLog(ResultSet rs) throws SQLException {
        ActivityLog log = new ActivityLog();
        log.setId(rs.getLong("id"));
        log.setSetting(rs.getString("setting"));
        log.setComponent(rs.getString("component"));
        log.setUserId(rs.getInt("user_id"));
        log.setUserName(rs.getString("user_name"));
        log.setUserEmail(rs.getString("user_email"));
        log.setChangeType(rs.getString("change_type"));
        
        Timestamp timestamp = rs.getTimestamp("timestamp");
        if (timestamp != null) {
            log.setTimestamp(timestamp.toLocalDateTime());
        }
        
        return log;
    }
}

