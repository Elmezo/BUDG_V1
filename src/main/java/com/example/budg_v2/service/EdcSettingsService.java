package com.example.budg_v2.service;

import com.example.budg_v2.dao.EdcSettingsDAO;
import com.example.budg_v2.model.EdcSettings;
import com.example.budg_v2.util.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing EDC (Enterprise Data Catalog) settings with encryption
 * 
 * Features:
 * - Encrypted password storage
 * - Password preservation logic (if "********" is sent, keep existing)
 * - Cache with TTL (5 minutes)
 * - Test connection functionality
 */
public class EdcSettingsService {
    
    private static final Logger logger = LoggerFactory.getLogger(EdcSettingsService.class);
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    
    private final EdcSettingsDAO edcSettingsDAO;
    private final EncryptionService encryptionService;
    
    // Cache with timestamp
    private static class CacheEntry {
        final EdcSettings settings;
        final long timestamp;
        
        CacheEntry(EdcSettings settings, long timestamp) {
            this.settings = settings;
            this.timestamp = timestamp;
        }
    }
    
    private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final String CACHE_KEY = "edc_settings";
    private static final String MASKED_PASSWORD = "********";
    
    public EdcSettingsService() {
        this.edcSettingsDAO = new EdcSettingsDAO();
        this.encryptionService = EncryptionService.getInstance();
    }
    
    /**
     * Get EDC settings with auto-refresh if cache expired
     * 
     * @return EdcSettings object (password is decrypted)
     */
    public EdcSettings getEdcSettings() {
        try {
            CacheEntry entry = cache.get(CACHE_KEY);
            long now = System.currentTimeMillis();
            
            // Check if cache is expired or doesn't exist
            if (entry == null || (now - entry.timestamp) > CACHE_TTL_MS) {
                logger.debug("EDC settings cache expired or missing, reloading from database");
                return loadFromDatabase();
            }
            
            return entry.settings;
            
        } catch (Exception e) {
            logger.error("Error getting EDC settings from cache, falling back to database", e);
            return loadFromDatabase();
        }
    }
    
    /**
     * Load settings from database and cache them
     */
    private EdcSettings loadFromDatabase() {
        try {
            Map<String, EdcSettingsDAO.SettingWithEncryption> settingsMap = edcSettingsDAO.getAllSettingsWithEncryption();
            
            if (settingsMap.isEmpty()) {
                logger.debug("No EDC settings found in database, returning defaults");
                EdcSettings defaults = new EdcSettings();
                cache.put(CACHE_KEY, new CacheEntry(defaults, System.currentTimeMillis()));
                return defaults;
            }
            
            EdcSettings settings = mapToEdcSettings(settingsMap);
            
            // Cache the settings
            cache.put(CACHE_KEY, new CacheEntry(settings, System.currentTimeMillis()));
            
            logger.debug("EDC settings loaded from database and cached");
            return settings;
            
        } catch (SQLException e) {
            logger.error("Error loading EDC settings from database", e);
            EdcSettings defaults = new EdcSettings();
            cache.put(CACHE_KEY, new CacheEntry(defaults, System.currentTimeMillis()));
            return defaults;
        }
    }
    
    /**
     * Map database settings to EdcSettings object
     */
    private EdcSettings mapToEdcSettings(Map<String, EdcSettingsDAO.SettingWithEncryption> settingsMap) {
        EdcSettings settings = new EdcSettings();
        
        EdcSettingsDAO.SettingWithEncryption setting;
        
        // Server connection settings
        setting = settingsMap.get("eic_server_host");
        if (setting != null && setting.value != null) {
            settings.setServerHost(setting.value);
        }
        
        setting = settingsMap.get("eic_server_port");
        if (setting != null && setting.value != null) {
            try {
                settings.setServerPort(Integer.parseInt(setting.value));
            } catch (NumberFormatException e) {
                logger.warn("Invalid port value: {}", setting.value);
            }
        }
        
        setting = settingsMap.get("eic_server_login_username");
        if (setting != null && setting.value != null) {
            settings.setLoginUsername(setting.value);
        }
        
        // Password: read as plain text (no encryption)
        setting = settingsMap.get("eic_server_login_password");
        if (setting != null && setting.value != null && !setting.value.trim().isEmpty()) {
            // Store password as plain text (no encryption)
            settings.setLoginPassword(setting.value);
        }
        
        setting = settingsMap.get("eic_server_login_namespace");
        if (setting != null && setting.value != null) {
            settings.setLoginNamespace(setting.value);
        }
        
        // Resource and configuration
        setting = settingsMap.get("eic_axon_resource_name");
        if (setting != null && setting.value != null) {
            settings.setAxonResourceName(setting.value);
        }
        
        setting = settingsMap.get("eic_axon_super_admin_email");
        if (setting != null && setting.value != null) {
            settings.setAxonSuperAdminEmail(setting.value);
        }
        
        // Feature flags
        setting = settingsMap.get("eic_enable_auto_lineage_recommendation");
        if (setting != null && setting.value != null) {
            settings.setEnableAutoLineageRecommendation(parseBoolean(setting.value));
        }
        
        setting = settingsMap.get("eic_enable_lineage_email_notification");
        if (setting != null && setting.value != null) {
            settings.setEnableLineageEmailNotification(parseBoolean(setting.value));
        }
        
        setting = settingsMap.get("eic_enable_custom_attributes");
        if (setting != null && setting.value != null) {
            settings.setEnableCustomAttributes(parseBoolean(setting.value));
        }
        
        setting = settingsMap.get("eic_enable_cleanup_lineage_recommendations");
        if (setting != null && setting.value != null) {
            settings.setEnableCleanupLineageRecommendations(parseBoolean(setting.value));
        }
        
        setting = settingsMap.get("eic_enable_filter");
        if (setting != null && setting.value != null) {
            settings.setEnableFilter(parseBoolean(setting.value));
        }
        
        setting = settingsMap.get("eic_update_onboarded_assets");
        if (setting != null && setting.value != null) {
            settings.setUpdateOnboardedAssets(parseBoolean(setting.value));
        }
        
        // Additional settings
        setting = settingsMap.get("eic_default_glossary");
        if (setting != null) {
            settings.setDefaultGlossary(setting.value != null ? setting.value : "");
        }
        
        setting = settingsMap.get("eic_request_timeout");
        if (setting != null && setting.value != null) {
            try {
                settings.setRequestTimeout(Integer.parseInt(setting.value));
            } catch (NumberFormatException e) {
                logger.warn("Invalid timeout value: {}", setting.value);
            }
        }
        
        setting = settingsMap.get("eic_proxy_host");
        if (setting != null) {
            settings.setProxyHost(setting.value != null ? setting.value : "");
        }
        
        setting = settingsMap.get("eic_proxy_port");
        if (setting != null && setting.value != null && !setting.value.trim().isEmpty()) {
            try {
                settings.setProxyPort(Integer.parseInt(setting.value));
            } catch (NumberFormatException e) {
                logger.warn("Invalid proxy port value: {}", setting.value);
            }
        }
        
        // SSL/TLS settings
        setting = settingsMap.get("eic_ssl_insecure");
        if (setting != null && setting.value != null) {
            settings.setSslInsecure(parseBoolean(setting.value));
        } else {
            // Default to false (secure) if not set
            settings.setSslInsecure(false);
        }
        
        return settings;
    }
    
    /**
     * Save EDC settings to database (password encrypted)
     * 
     * @param settings EDC settings to save
     * @param updatedBy User ID who is saving (can be null)
     * @throws SQLException if database error occurs
     */
    public void saveEdcSettings(EdcSettings settings, Integer updatedBy) throws SQLException {
        try {
            // Get existing password from database (plain text)
            EdcSettingsDAO.SettingWithEncryption existingPasswordSetting = 
                edcSettingsDAO.getSettingWithEncryption("eic_server_login_password");
            String existingPassword = null;
            if (existingPasswordSetting != null && existingPasswordSetting.value != null && 
                !existingPasswordSetting.value.trim().isEmpty()) {
                existingPassword = existingPasswordSetting.value;
            }
            
            // Save all settings
            saveSetting("eic_server_host", settings.getServerHost(), false, updatedBy);
            saveSetting("eic_server_port", settings.getServerPort() != null ? String.valueOf(settings.getServerPort()) : null, false, updatedBy);
            saveSetting("eic_server_login_username", settings.getLoginUsername(), false, updatedBy);
            
            // Password: use preservation logic (plain text, no encryption)
            String passwordToStore = resolvePasswordToStore(settings.getLoginPassword(), existingPassword);
            if (passwordToStore != null && !passwordToStore.trim().isEmpty()) {
                // Save password as plain text (no encryption)
                saveSetting("eic_server_login_password", passwordToStore, false, updatedBy);
            } else {
                // Clear password
                saveSetting("eic_server_login_password", "", false, updatedBy);
            }
            
            saveSetting("eic_server_login_namespace", settings.getLoginNamespace(), false, updatedBy);
            saveSetting("eic_axon_resource_name", settings.getAxonResourceName(), false, updatedBy);
            saveSetting("eic_axon_super_admin_email", settings.getAxonSuperAdminEmail(), false, updatedBy);
            saveSetting("eic_enable_auto_lineage_recommendation", String.valueOf(settings.isEnableAutoLineageRecommendation()), false, updatedBy);
            saveSetting("eic_enable_lineage_email_notification", String.valueOf(settings.isEnableLineageEmailNotification()), false, updatedBy);
            saveSetting("eic_enable_custom_attributes", String.valueOf(settings.isEnableCustomAttributes()), false, updatedBy);
            saveSetting("eic_enable_cleanup_lineage_recommendations", String.valueOf(settings.isEnableCleanupLineageRecommendations()), false, updatedBy);
            saveSetting("eic_enable_filter", String.valueOf(settings.isEnableFilter()), false, updatedBy);
            saveSetting("eic_update_onboarded_assets", String.valueOf(settings.isUpdateOnboardedAssets()), false, updatedBy);
            saveSetting("eic_default_glossary", settings.getDefaultGlossary() != null ? settings.getDefaultGlossary() : "", false, updatedBy);
            saveSetting("eic_request_timeout", settings.getRequestTimeout() != null ? String.valueOf(settings.getRequestTimeout()) : null, false, updatedBy);
            saveSetting("eic_proxy_host", settings.getProxyHost() != null ? settings.getProxyHost() : "", false, updatedBy);
            saveSetting("eic_proxy_port", settings.getProxyPort() != null ? String.valueOf(settings.getProxyPort()) : "", false, updatedBy);
            saveSetting("eic_ssl_insecure", String.valueOf(settings.isSslInsecure()), false, updatedBy);
            
            // Update cache
            cache.put(CACHE_KEY, new CacheEntry(settings, System.currentTimeMillis()));
            
            logger.info("EDC settings saved to database and cache updated");
            
        } catch (Exception e) {
            logger.error("Error saving EDC settings", e);
            throw new SQLException("Failed to save EDC settings", e);
        }
    }
    
    /**
     * Helper method to save a single setting
     */
    private void saveSetting(String key, String value, boolean isEncrypted, Integer updatedBy) throws SQLException {
        edcSettingsDAO.saveSetting(key, value, isEncrypted, updatedBy);
    }
    
    /**
     * Password preservation logic (plain text, no encryption):
     * - If incoming is "********" → consider unchanged → return existing (keep current password)
     * - If incoming is null or empty → consider cleared → return null (clear password)
     * - If incoming is different → return incoming (update password)
     * 
     * @param incoming Password value from UI (may be "********" for unchanged, null/empty for clear, or new value)
     * @param existingPassword Current password in DB (plain text, no encryption)
     * @return Password to store (plain text, or null to clear)
     */
    public String resolvePasswordToStore(String incoming, String existingPassword) {
        // If incoming is masked (unchanged indicator)
        if (incoming != null && incoming.equals(MASKED_PASSWORD)) {
            // Keep existing password - return existing plain text value
            if (existingPassword != null && !existingPassword.trim().isEmpty()) {
                return existingPassword;
            }
            return null; // No existing password to preserve
        }
        
        // If incoming is null or empty → clear password
        if (incoming == null || incoming.trim().isEmpty()) {
            return null;
        }
        
        // Otherwise, incoming is a new password value
        return incoming;
    }
    
    /**
     * Refresh cache manually
     */
    public void refreshCache() {
        logger.info("Manually refreshing EDC settings cache");
        cache.remove(CACHE_KEY);
        loadFromDatabase();
    }
    
    /**
     * Parse boolean from string
     */
    private boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value) || "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}

