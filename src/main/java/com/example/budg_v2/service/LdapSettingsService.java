package com.example.budg_v2.service;

import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.model.LdapSettings;
import com.example.budg_v2.exception.LdapConnectionException;
import com.unboundid.ldap.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing LDAP settings with caching
 * 
 * Features:
 * - Cache with TTL (5 minutes) - auto-refresh when expired
 * - Plain text password storage (no encryption)
 * - Test connection functionality (ignores ldapEnabled)
 * - Fallback to environment variables if not in database
 */
public class LdapSettingsService {
    
    private static final Logger logger = LoggerFactory.getLogger(LdapSettingsService.class);
    private static final String SETTINGS_GROUP = "LDAP Settings";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    
    private final SystemSettingsDAO systemSettingsDAO;
    
    // Cache with timestamp
    private static class CacheEntry {
        final LdapSettings settings;
        final long timestamp;
        
        CacheEntry(LdapSettings settings, long timestamp) {
            this.settings = settings;
            this.timestamp = timestamp;
        }
    }
    
    private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final String CACHE_KEY = "ldap_settings";
    
    public LdapSettingsService() {
        this.systemSettingsDAO = new SystemSettingsDAO();
    }
    
    /**
     * Get LDAP settings with auto-refresh if cache expired
     * 
     * @return LdapSettings object
     */
    public LdapSettings getLdapSettings() {
        try {
            CacheEntry entry = cache.get(CACHE_KEY);
            long now = System.currentTimeMillis();
            
            // Check if cache is expired or doesn't exist
            if (entry == null || (now - entry.timestamp) > CACHE_TTL_MS) {
                logger.debug("LDAP settings cache expired or missing, reloading from database");
                return loadFromDatabase();
            }
            
            return entry.settings;
            
        } catch (Exception e) {
            logger.error("Error getting LDAP settings from cache, falling back to database", e);
            return loadFromDatabase();
        }
    }
    
    /**
     * Load settings from database and cache them
     */
    private LdapSettings loadFromDatabase() {
        try {
            Map<String, Object> settingsMap = systemSettingsDAO.getSettingsByGroup(SETTINGS_GROUP);
            
            if (settingsMap.isEmpty()) {
                logger.debug("No LDAP settings found in database, falling back to environment variables");
                return loadFromEnvironment();
            }
            
            LdapSettings settings = mapToLdapSettings(settingsMap);
            
            // Cache the settings
            cache.put(CACHE_KEY, new CacheEntry(settings, System.currentTimeMillis()));
            
            logger.debug("LDAP settings loaded from database and cached");
            return settings;
            
        } catch (SQLException e) {
            logger.error("Error loading LDAP settings from database, falling back to environment", e);
            return loadFromEnvironment();
        }
    }
    
    /**
     * Load settings from environment variables (fallback)
     */
    private LdapSettings loadFromEnvironment() {
        LdapSettings settings = new LdapSettings();
        
        // Build ldapUrl from host and port
        String host = System.getenv("LDAP_HOST");
        String portStr = System.getenv("LDAP_PORT");
        boolean useSsl = Boolean.parseBoolean(System.getenv("LDAP_USE_SSL"));
        
        if (host != null && portStr != null) {
            String protocol = useSsl ? "ldaps://" : "ldap://";
            settings.setLdapUrl(protocol + host + ":" + portStr);
        }
        
        settings.setLdapEnabled(Boolean.parseBoolean(System.getenv("LDAP_ENABLED")));
        settings.setBaseDn(System.getenv("LDAP_BASE_DN"));
        settings.setBindDn(System.getenv("LDAP_BIND_DN"));
        settings.setBindPassword(System.getenv("LDAP_BIND_PASSWORD")); // Not encrypted in env
        settings.setUserSearchBase(System.getenv("LDAP_USER_DN"));
        settings.setUserSearchFilter(System.getenv("LDAP_USER_SEARCH_FILTER"));
        settings.setGroupSearchBase(System.getenv("LDAP_GROUP_DN"));
        
        String timeoutStr = System.getenv("LDAP_CONNECTION_TIMEOUT");
        if (timeoutStr != null) {
            try {
                settings.setConnectionTimeout(Integer.parseInt(timeoutStr));
            } catch (NumberFormatException e) {
                settings.setConnectionTimeout(5000);
            }
        }
        
        // Cache the settings
        cache.put(CACHE_KEY, new CacheEntry(settings, System.currentTimeMillis()));
        
        logger.debug("LDAP settings loaded from environment variables");
        return settings;
    }
    
    /**
     * Map database settings to LdapSettings object
     */
    private LdapSettings mapToLdapSettings(Map<String, Object> settingsMap) {
        LdapSettings settings = new LdapSettings();
        
        settings.setLdapEnabled(getBooleanValue(settingsMap, "ldapEnabled", false));
        settings.setLdapUrl(getStringValue(settingsMap, "ldapUrl"));
        settings.setBaseDn(getStringValue(settingsMap, "baseDn"));
        settings.setBindDn(getStringValue(settingsMap, "bindDn"));
        
        // Get bindPassword as plain text (no encryption)
        String bindPassword = getStringValue(settingsMap, "bindPassword");
        if (bindPassword != null && !bindPassword.isEmpty()) {
            // Remove ENC: prefix if present (for backward compatibility with old encrypted data)
            if (bindPassword.startsWith("ENC:")) {
                // Old encrypted format - clear it
                logger.debug("Found old encrypted password format, clearing it");
                try {
                    systemSettingsDAO.saveSetting(SETTINGS_GROUP, "bindPassword", "", "string");
                } catch (SQLException sqlEx) {
                    logger.warn("Failed to clear old encrypted password format", sqlEx);
                }
                settings.setBindPassword(null);
            } else {
                // Plain text password
                settings.setBindPassword(bindPassword);
            }
        }
        
        settings.setUserSearchBase(getStringValue(settingsMap, "userSearchBase"));
        settings.setUserSearchFilter(getStringValue(settingsMap, "userSearchFilter"));
        settings.setGroupSearchBase(getStringValue(settingsMap, "groupSearchBase"));
        settings.setConnectionTimeout(getIntValue(settingsMap, "connectionTimeout", 5000));
        
        // Incremental sync settings
        settings.setIncrementalSyncEnabled(getBooleanValue(settingsMap, "incrementalSyncEnabled", false));
        settings.setLastSyncTimestamp(getStringValue(settingsMap, "lastSyncTimestamp"));
        
        // Truststore settings
        settings.setTruststorePath(getStringValue(settingsMap, "truststorePath"));
        settings.setTruststorePassword(getStringValue(settingsMap, "truststorePassword"));
        settings.setTrustAllCertificates(getBooleanValue(settingsMap, "trustAllCertificates", false));
        
        return settings;
    }
    
    /**
     * Save LDAP settings to database (password stored as plain text)
     */
    public void saveLdapSettings(LdapSettings settings) throws SQLException {
        try {
            // Save all settings
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "ldapEnabled", 
                String.valueOf(settings.isLdapEnabled()), "boolean");
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "ldapUrl", 
                settings.getLdapUrl(), "string");
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "baseDn", 
                settings.getBaseDn(), "string");
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "bindDn", 
                settings.getBindDn(), "string");
            
            // Save bindPassword as plain text
            if (settings.getBindPassword() != null && !settings.getBindPassword().trim().isEmpty()) {
                systemSettingsDAO.saveSetting(SETTINGS_GROUP, "bindPassword", 
                    settings.getBindPassword(), "string");
            } else {
                // Clear password if empty
                systemSettingsDAO.saveSetting(SETTINGS_GROUP, "bindPassword", "", "string");
            }
            
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "userSearchBase", 
                settings.getUserSearchBase(), "string");
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "userSearchFilter", 
                settings.getUserSearchFilter() != null ? settings.getUserSearchFilter() : "(uid={0})", "string");
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "groupSearchBase", 
                settings.getGroupSearchBase(), "string");
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "connectionTimeout", 
                String.valueOf(settings.getConnectionTimeout()), "int");
            
            // Save incremental sync settings
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "incrementalSyncEnabled", 
                String.valueOf(settings.isIncrementalSyncEnabled()), "boolean");
            if (settings.getLastSyncTimestamp() != null) {
                systemSettingsDAO.saveSetting(SETTINGS_GROUP, "lastSyncTimestamp", 
                    settings.getLastSyncTimestamp(), "string");
            }
            
            // Save truststore settings
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "truststorePath", 
                settings.getTruststorePath() != null ? settings.getTruststorePath() : "", "string");
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "truststorePassword", 
                settings.getTruststorePassword() != null ? settings.getTruststorePassword() : "", "string");
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "trustAllCertificates", 
                String.valueOf(settings.isTrustAllCertificates()), "boolean");
            
            // Update cache
            cache.put(CACHE_KEY, new CacheEntry(settings, System.currentTimeMillis()));
            
            logger.info("LDAP settings saved to database and cache updated");
            
        } catch (Exception e) {
            logger.error("Error saving LDAP settings", e);
            throw new SQLException("Failed to save LDAP settings", e);
        }
    }
    
    /**
     * Refresh cache manually
     */
    public void refreshCache() {
        logger.info("Manually refreshing LDAP settings cache");
        cache.remove(CACHE_KEY);
        loadFromDatabase();
    }
    
    /**
     * Save last sync timestamp (ISO 8601 format)
     * This is called after a successful sync to enable incremental sync
     * 
     * @param timestamp ISO 8601 format timestamp (e.g., "2024-01-15T10:30:00Z")
     */
    public void saveLastSyncTimestamp(String timestamp) {
        try {
            systemSettingsDAO.saveSetting(SETTINGS_GROUP, "lastSyncTimestamp", timestamp, "string");
            
            // Update cache if it exists
            CacheEntry entry = cache.get(CACHE_KEY);
            if (entry != null) {
                entry.settings.setLastSyncTimestamp(timestamp);
            }
            
            logger.debug("Last sync timestamp saved: {}", timestamp);
        } catch (SQLException e) {
            logger.error("Error saving last sync timestamp", e);
        }
    }
    
    /**
     * Test LDAP connection (ignores ldapEnabled - works even if disabled)
     * 
     * @param settings LDAP settings to test
     * @return true if connection successful, false otherwise
     * @throws LdapConnectionException if connection fails
     */
    public boolean testConnection(LdapSettings settings) throws LdapConnectionException {
        if (settings == null) {
            throw new IllegalArgumentException("LDAP settings cannot be null");
        }
        
        if (settings.getLdapUrl() == null || settings.getLdapUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("LDAP URL is required");
        }
        
        LDAPConnection connection = null;
        try {
            String host = settings.getHost();
            int port = settings.getPort();
            
            logger.info("Testing LDAP connection to {}:{}", host, port);
            
            connection = new LDAPConnection();
            connection.connect(host, port, settings.getConnectionTimeout());
            
            // If bindDn and bindPassword are provided, test bind
            if (settings.getBindDn() != null && !settings.getBindDn().trim().isEmpty() &&
                settings.getBindPassword() != null && !settings.getBindPassword().trim().isEmpty()) {
                
                BindRequest bindRequest = new SimpleBindRequest(settings.getBindDn(), settings.getBindPassword());
                BindResult bindResult = connection.bind(bindRequest);
                
                if (bindResult.getResultCode() != ResultCode.SUCCESS) {
                    throw new LdapConnectionException(
                        LdapConnectionException.ErrorType.AUTHENTICATION_FAILED,
                        "LDAP bind failed: " + bindResult.getResultString(),
                        host,
                        port
                    );
                }
                
                logger.info("LDAP bind successful");
            }
            
            // Test a simple search if baseDn is provided
            if (settings.getBaseDn() != null && !settings.getBaseDn().trim().isEmpty()) {
                SearchRequest searchRequest = new SearchRequest(
                    settings.getBaseDn(),
                    SearchScope.BASE,
                    "(objectClass=*)",
                    "1.1" // No attributes needed
                );
                SearchResult searchResult = connection.search(searchRequest);
                
                if (searchResult.getResultCode() != ResultCode.SUCCESS) {
                    throw new LdapConnectionException(
                        LdapConnectionException.ErrorType.SERVER_UNAVAILABLE,
                        "LDAP search failed: " + searchResult.getResultString(),
                        host,
                        port
                    );
                }
                
                logger.info("LDAP search successful");
            }
            
            logger.info("LDAP connection test successful");
            return true;
            
        } catch (LDAPException e) {
            logger.error("LDAP connection test failed", e);
            
            LdapConnectionException.ErrorType errorType;
            if (e.getResultCode() == ResultCode.CONNECT_ERROR) {
                errorType = LdapConnectionException.ErrorType.CONNECTION_REFUSED;
            } else if (e.getResultCode() == ResultCode.TIMEOUT) {
                errorType = LdapConnectionException.ErrorType.TIMEOUT;
            } else if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
                errorType = LdapConnectionException.ErrorType.AUTHENTICATION_FAILED;
            } else {
                errorType = LdapConnectionException.ErrorType.SERVER_UNAVAILABLE;
            }
            
            throw new LdapConnectionException(
                errorType,
                "LDAP connection test failed: " + e.getMessage(),
                settings.getHost(),
                settings.getPort(),
                e
            );
            
        } catch (Exception e) {
            logger.error("Unexpected error during LDAP connection test", e);
            throw new LdapConnectionException(
                LdapConnectionException.ErrorType.UNKNOWN_ERROR,
                "Unexpected error during LDAP connection test: " + e.getMessage(),
                settings.getHost(),
                settings.getPort(),
                e
            );
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    // Helper methods
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString()) || "1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString());
    }
    
    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

