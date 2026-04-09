package com.example.budg_v2.service;

import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.model.SystemSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Service for managing notification settings
 * Checks if notifications are disabled for Tasks (Workflows) or Change Requests
 */
public class NotificationSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationSettingsService.class);
    private final SystemSettingsDAO systemSettingsDAO;
    private Boolean cachedTasksDisabled;
    private Boolean cachedCRsDisabled;
    private long cacheTimestamp;
    private static final long CACHE_TTL = 60000; // 1 minute cache

    public NotificationSettingsService() {
        this.systemSettingsDAO = new SystemSettingsDAO();
    }

    /**
     * Check if notification emails are disabled for Tasks (Workflows)
     * @return true if disabled, false if enabled (default)
     */
    public boolean isNotificationDisabledForTasks() {
        try {
            long now = System.currentTimeMillis();
            if (cachedTasksDisabled != null && (now - cacheTimestamp) < CACHE_TTL) {
                return cachedTasksDisabled;
            }

            SystemSettings setting = systemSettingsDAO.getSetting("Notifications", "disable_notification_emails_for_tasks");
            if (setting != null && setting.getSettingValue() != null) {
                cachedTasksDisabled = Boolean.parseBoolean(setting.getSettingValue());
            } else {
                cachedTasksDisabled = false; // Default: enabled
            }
            cacheTimestamp = now;
            return cachedTasksDisabled;
        } catch (SQLException e) {
            logger.error("Error checking if notifications are disabled for tasks", e);
            return false; // Default to enabled on error
        }
    }

    /**
     * Check if notification emails are disabled for Change Requests
     * @return true if disabled, false if enabled (default)
     */
    public boolean isNotificationDisabledForCRs() {
        try {
            long now = System.currentTimeMillis();
            if (cachedCRsDisabled != null && (now - cacheTimestamp) < CACHE_TTL) {
                return cachedCRsDisabled;
            }

            SystemSettings setting = systemSettingsDAO.getSetting("Notifications", "disable_notification_emails_for_crs");
            if (setting != null && setting.getSettingValue() != null) {
                cachedCRsDisabled = Boolean.parseBoolean(setting.getSettingValue());
            } else {
                cachedCRsDisabled = false; // Default: enabled
            }
            cacheTimestamp = now;
            return cachedCRsDisabled;
        } catch (SQLException e) {
            logger.error("Error checking if notifications are disabled for CRs", e);
            return false; // Default to enabled on error
        }
    }

    /**
     * Invalidate cache (call this after updating settings)
     */
    public void invalidateCache() {
        cachedTasksDisabled = null;
        cachedCRsDisabled = null;
        cacheTimestamp = 0;
    }
}

