package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.EmailSettings;

import java.sql.*;

/**
 * DAO for email_settings table operations
 */
public class EmailSettingsDAO {

    /**
     * Get the current email settings (there should only be one record)
     */
    public EmailSettings getCurrentSettings() throws SQLException {
        String sql = "SELECT * FROM email_settings ORDER BY id LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Create or update email settings
     * Since there should only be one settings record, this upserts
     */
    public void save(EmailSettings settings) throws SQLException {
        EmailSettings existing = getCurrentSettings();
        
        if (existing != null && existing.getId() != null) {
            update(settings, existing.getId());
        } else {
            insert(settings);
        }
    }

    /**
     * Insert new email settings
     */
    private void insert(EmailSettings settings) throws SQLException {
        String sql = "INSERT INTO email_settings (smtp_host, smtp_port, smtp_username, " +
                "smtp_password, encryption, enabled, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, settings.getSmtpHost());
            stmt.setInt(2, settings.getSmtpPort());
            stmt.setString(3, settings.getSmtpUsername());
            stmt.setString(4, settings.getSmtpPassword());
            stmt.setString(5, settings.getEncryption());
            stmt.setBoolean(6, settings.getEnabled() != null ? settings.getEnabled() : false);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    settings.setId(rs.getInt(1));
                }
            }
        }
    }

    /**
     * Update existing email settings
     */
    private void update(EmailSettings settings, Integer id) throws SQLException {
        String sql = "UPDATE email_settings SET smtp_host = ?, smtp_port = ?, smtp_username = ?, " +
                "smtp_password = ?, encryption = ?, enabled = ?, updated_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, settings.getSmtpHost());
            stmt.setInt(2, settings.getSmtpPort());
            stmt.setString(3, settings.getSmtpUsername());
            stmt.setString(4, settings.getSmtpPassword());
            stmt.setString(5, settings.getEncryption());
            stmt.setBoolean(6, settings.getEnabled() != null ? settings.getEnabled() : false);
            stmt.setInt(7, id);

            stmt.executeUpdate();
        }
    }

    /**
     * Check if email is enabled globally
     */
    public boolean isEmailEnabled() throws SQLException {
        EmailSettings settings = getCurrentSettings();
        return settings != null && settings.getEnabled() != null && settings.getEnabled();
    }

    /**
     * Map ResultSet to EmailSettings object
     */
    private EmailSettings mapResultSet(ResultSet rs) throws SQLException {
        EmailSettings settings = new EmailSettings();
        settings.setId(rs.getInt("id"));
        settings.setSmtpHost(rs.getString("smtp_host"));
        settings.setSmtpPort(rs.getInt("smtp_port"));
        settings.setSmtpUsername(rs.getString("smtp_username"));
        settings.setSmtpPassword(rs.getString("smtp_password"));
        settings.setEncryption(rs.getString("encryption"));
        settings.setEnabled(rs.getBoolean("enabled"));
        settings.setCreatedAt(rs.getTimestamp("created_at"));
        settings.setUpdatedAt(rs.getTimestamp("updated_at"));
        return settings;
    }
}




















































