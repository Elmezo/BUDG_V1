package com.example.budg_v2.service;

import com.example.budg_v2.dao.EmailSettingsDAO;
import com.example.budg_v2.model.EmailSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Service for managing email settings (SMTP configuration)
 */
public class EmailSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(EmailSettingsService.class);
    private final EmailSettingsDAO emailSettingsDAO;
    private EmailSettings cachedSettings;
    private long cacheTimestamp;
    private static final long CACHE_TTL = 60000; // 1 minute cache

    public EmailSettingsService() {
        this.emailSettingsDAO = new EmailSettingsDAO();
    }

    /**
     * Get current email settings (with caching)
     */
    public EmailSettings getCurrentSettings() throws SQLException {
        long now = System.currentTimeMillis();
        if (cachedSettings != null && (now - cacheTimestamp) < CACHE_TTL) {
            return cachedSettings;
        }

        cachedSettings = emailSettingsDAO.getCurrentSettings();
        cacheTimestamp = now;
        return cachedSettings;
    }

    /**
     * Save email settings
     */
    public void saveSettings(EmailSettings settings) throws SQLException {
        emailSettingsDAO.save(settings);
        // Invalidate cache
        cachedSettings = null;
        cacheTimestamp = 0;
        logger.info("Email settings saved");
    }

    /**
     * Check if email is enabled globally
     */
    public boolean isEmailEnabled() {
        try {
            EmailSettings settings = getCurrentSettings();
            return settings != null && settings.getEnabled() != null && settings.getEnabled();
        } catch (SQLException e) {
            logger.error("Error checking if email is enabled", e);
            return false;
        }
    }

    /**
     * Validate email settings (check if all required fields are present)
     */
    public boolean validateSettings(EmailSettings settings) {
        if (settings == null) {
            return false;
        }
        if (settings.getSmtpHost() == null || settings.getSmtpHost().trim().isEmpty()) {
            return false;
        }
        if (settings.getSmtpPort() == null || settings.getSmtpPort() <= 0) {
            return false;
        }
        if (settings.getSmtpUsername() == null || settings.getSmtpUsername().trim().isEmpty()) {
            return false;
        }
        if (settings.getSmtpPassword() == null || settings.getSmtpPassword().trim().isEmpty()) {
            return false;
        }
        return true;
    }
}

















