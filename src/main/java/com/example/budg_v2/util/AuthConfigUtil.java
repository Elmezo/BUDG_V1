package com.example.budg_v2.util;

import com.example.budg_v2.dao.SystemSettingsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class AuthConfigUtil {
    private static final Logger logger = LoggerFactory.getLogger(AuthConfigUtil.class);
    private static final SystemSettingsDAO systemSettingsDAO = new SystemSettingsDAO();

    public static class AuthConfig {
        public final long jwtValiditySeconds;
        public final long refreshValiditySeconds;

        public AuthConfig(long jwtValiditySeconds, long refreshValiditySeconds) {
            this.jwtValiditySeconds = jwtValiditySeconds;
            this.refreshValiditySeconds = refreshValiditySeconds;
        }
    }

    public static AuthConfig getConfig() {
        try {
            int jwtValidity = getIntSetting("JWT Settings", "jwt_validity_seconds", 7200);
            int refreshValidity = getIntSetting("JWT Settings", "refresh_validity_seconds", 2592000);
            return new AuthConfig(jwtValidity, refreshValidity);
        } catch (Exception e) {
            logger.error("Failed to load auth config from system_settings, using defaults", e);
            return new AuthConfig(7200, 2592000);
        }
    }

    private static int getIntSetting(String group, String key, int defaultValue) {
        try {
            com.example.budg_v2.model.SystemSettings setting = systemSettingsDAO.getSetting(group, key);
            if (setting != null && setting.getSettingValue() != null) {
                return Integer.parseInt(setting.getSettingValue());
            }
        } catch (SQLException | NumberFormatException e) {
            logger.warn("Could not load setting {} from group {}: {}", key, group, e.getMessage());
        }
        return defaultValue;
    }
}
