package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.SystemSettings;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * DAO for system_settings table operations
 */
public class SystemSettingsDAO {

    /**
     * Get a specific setting by group and key
     */
    public SystemSettings getSetting(String group, String key) throws SQLException {
        String sql = "SELECT * FROM system_settings WHERE setting_group = ? AND setting_key = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, group);
            stmt.setString(2, key);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Save or update a setting (upsert)
     */
    public void saveSetting(String group, String key, String value, String dataType) throws SQLException {
        SystemSettings existing = getSetting(group, key);

        if (existing != null && existing.getId() != null) {
            updateSetting(existing.getId(), value, dataType);
        } else {
            insertSetting(group, key, value, dataType);
        }
    }

    /**
     * Insert a new setting
     */
    private void insertSetting(String group, String key, String value, String dataType) throws SQLException {
        String sql = "INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(), NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, group);
            stmt.setString(2, key);
            stmt.setString(3, value);
            stmt.setString(4, dataType != null ? dataType : "string");

            stmt.executeUpdate();
        }
    }

    /**
     * Update an existing setting
     */
    private void updateSetting(Integer id, String value, String dataType) throws SQLException {
        String sql = "UPDATE system_settings SET setting_value = ?, data_type = ?, updated_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, value);
            stmt.setString(2, dataType != null ? dataType : "string");
            stmt.setInt(3, id);

            stmt.executeUpdate();
        }
    }

    /**
     * Get all settings for a specific group
     * Returns a Map where key is the setting_key and value is the setting_value (converted to appropriate type)
     */
    public Map<String, Object> getSettingsByGroup(String group) throws SQLException {
        Map<String, Object> settings = new HashMap<>();
        String sql = "SELECT * FROM system_settings WHERE setting_group = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, group);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SystemSettings setting = mapResultSet(rs);
                    String key = setting.getSettingKey();
                    Object value = convertValue(setting.getSettingValue(), setting.getDataType());
                    settings.put(key, value);
                }
            }
        }
        return settings;
    }

    /**
     * Get the configured number of days for clearing notifications
     * Returns 0 if not configured or if automatic clearing is disabled
     */
    public int getClearNotificationsDays() throws SQLException {
        SystemSettings setting = getSetting("Environment", "clear_notifications_days");
        if (setting != null && setting.getSettingValue() != null) {
            try {
                return Integer.parseInt(setting.getSettingValue());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Convert string value to appropriate type based on data_type
     */
    private Object convertValue(String value, String dataType) {
        if (value == null) {
            return null;
        }

        if (dataType == null || "string".equalsIgnoreCase(dataType)) {
            return value;
        } else if ("int".equalsIgnoreCase(dataType) || "integer".equalsIgnoreCase(dataType)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        } else if ("boolean".equalsIgnoreCase(dataType) || "bool".equalsIgnoreCase(dataType)) {
            return Boolean.parseBoolean(value) || "1".equals(value) || "true".equalsIgnoreCase(value);
        }

        return value;
    }

    /**
     * Map ResultSet to SystemSettings object
     */
    private SystemSettings mapResultSet(ResultSet rs) throws SQLException {
        SystemSettings settings = new SystemSettings();
        settings.setId(rs.getInt("id"));
        settings.setSettingGroup(rs.getString("setting_group"));
        settings.setSettingKey(rs.getString("setting_key"));
        settings.setSettingValue(rs.getString("setting_value"));
        settings.setDataType(rs.getString("data_type"));
        settings.setCreatedAt(rs.getTimestamp("created_at"));
        settings.setUpdatedAt(rs.getTimestamp("updated_at"));
        return settings;
    }
}
