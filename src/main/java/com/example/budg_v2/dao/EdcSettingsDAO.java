package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * DAO for edc_integration_settings table operations
 */
public class EdcSettingsDAO {

    /**
     * Get a specific setting by key
     */
    public String getSetting(String key) throws SQLException {
        String sql = "SELECT setting_value, is_encrypted FROM edc_integration_settings WHERE setting_key = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, key);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("setting_value");
                }
            }
        }
        return null;
    }

    /**
     * Get a setting with encryption flag
     */
    public SettingWithEncryption getSettingWithEncryption(String key) throws SQLException {
        String sql = "SELECT setting_value, is_encrypted FROM edc_integration_settings WHERE setting_key = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, key);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SettingWithEncryption result = new SettingWithEncryption();
                    result.value = rs.getString("setting_value");
                    result.isEncrypted = rs.getBoolean("is_encrypted");
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * Save or update a setting (upsert)
     */
    public void saveSetting(String key, String value, boolean isEncrypted, Integer updatedBy) throws SQLException {
        SettingWithEncryption existing = getSettingWithEncryption(key);

        if (existing != null) {
            updateSetting(key, value, isEncrypted, updatedBy);
        } else {
            insertSetting(key, value, isEncrypted, updatedBy);
        }
    }

    /**
     * Insert a new setting
     */
    private void insertSetting(String key, String value, boolean isEncrypted, Integer updatedBy) throws SQLException {
        String sql = "INSERT INTO edc_integration_settings (setting_key, setting_value, is_encrypted, updated_by, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.setBoolean(3, isEncrypted);
            if (updatedBy != null) {
                stmt.setInt(4, updatedBy);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }

            stmt.executeUpdate();
        }
    }

    /**
     * Update an existing setting
     */
    private void updateSetting(String key, String value, boolean isEncrypted, Integer updatedBy) throws SQLException {
        String sql = "UPDATE edc_integration_settings SET setting_value = ?, is_encrypted = ?, updated_by = ?, updated_at = NOW() WHERE setting_key = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, value);
            stmt.setBoolean(2, isEncrypted);
            if (updatedBy != null) {
                stmt.setInt(3, updatedBy);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setString(4, key);

            stmt.executeUpdate();
        }
    }

    /**
     * Get all EDC settings
     * Returns a Map where key is the setting_key and value is the setting_value
     */
    public Map<String, String> getAllSettings() throws SQLException {
        Map<String, String> settings = new HashMap<>();
        String sql = "SELECT setting_key, setting_value, is_encrypted FROM edc_integration_settings";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("setting_key");
                    String value = rs.getString("setting_value");
                    settings.put(key, value);
                }
            }
        }
        return settings;
    }

    /**
     * Get all EDC settings with encryption flags
     */
    public Map<String, SettingWithEncryption> getAllSettingsWithEncryption() throws SQLException {
        Map<String, SettingWithEncryption> settings = new HashMap<>();
        String sql = "SELECT setting_key, setting_value, is_encrypted FROM edc_integration_settings";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("setting_key");
                    SettingWithEncryption setting = new SettingWithEncryption();
                    setting.value = rs.getString("setting_value");
                    setting.isEncrypted = rs.getBoolean("is_encrypted");
                    settings.put(key, setting);
                }
            }
        }
        return settings;
    }

    /**
     * Helper class to hold setting value and encryption flag
     */
    public static class SettingWithEncryption {
        public String value;
        public boolean isEncrypted;
    }
}

