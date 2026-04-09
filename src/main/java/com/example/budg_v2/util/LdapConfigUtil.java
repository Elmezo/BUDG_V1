package com.example.budg_v2.util;

import java.util.Properties;
import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.model.LdapSettings;
import com.example.budg_v2.service.LdapSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdapConfigUtil {

    private static final Logger logger = LoggerFactory.getLogger(LdapConfigUtil.class);
    private static Properties properties;
    @SuppressWarnings("unused")
    private static final SystemSettingsDAO systemSettingsDAO = new SystemSettingsDAO();
    private static final LdapSettingsService ldapSettingsService = new LdapSettingsService();

    static {
        loadProperties();
    }

    public static class LdapConfig {
        public final String host;
        public final int port;
        public final String baseDn;
        public final String userDn;
        public final String groupDn;
        public final boolean useSsl;
        public final int connectionTimeout;
        public final int readTimeout;
        public final boolean enabled;

        public LdapConfig(String host, int port, String baseDn, String userDn, String groupDn,
                          boolean useSsl, int connectionTimeout, int readTimeout, boolean enabled) {
            this.host = host;
            this.port = port;
            this.baseDn = baseDn;
            this.userDn = userDn;
            this.groupDn = groupDn;
            this.useSsl = useSsl;
            this.connectionTimeout = connectionTimeout;
            this.readTimeout = readTimeout;
            this.enabled = enabled;
        }
    }

    private static void loadProperties() {
        properties = new Properties();

        // Try to load from environment variables first
        String host = System.getenv("LDAP_HOST");
        String portStr = System.getenv("LDAP_PORT");
        String baseDn = System.getenv("LDAP_BASE_DN");
        String userDn = System.getenv("LDAP_USER_DN");
        String groupDn = System.getenv("LDAP_GROUP_DN");
        String useSslStr = System.getenv("LDAP_USE_SSL");
        String enabledStr = System.getenv("LDAP_ENABLED");

        // Set defaults based on your Apache Directory Studio configuration
        properties.setProperty("ldap.host", host != null ? host : "localhost");
        properties.setProperty("ldap.port", portStr != null ? portStr : "10389");
        properties.setProperty("ldap.baseDn", baseDn != null ? baseDn : "dc=example,dc=com");
        properties.setProperty("ldap.userDn", userDn != null ? userDn : "ou=people,dc=example,dc=com");
        properties.setProperty("ldap.groupDn", groupDn != null ? groupDn : "ou=group,dc=example,dc=com");
        properties.setProperty("ldap.useSsl", useSslStr != null ? useSslStr : "false");
        properties.setProperty("ldap.enabled", enabledStr != null ? enabledStr : "true");
        properties.setProperty("ldap.connectionTimeout", "5000");
        properties.setProperty("ldap.readTimeout", "10000");

        logger.info("LDAP Configuration loaded - Host: {}, Port: {}, BaseDN: {}, Enabled: {}",
                properties.getProperty("ldap.host"),
                properties.getProperty("ldap.port"),
                properties.getProperty("ldap.baseDn"),
                properties.getProperty("ldap.enabled"));
    }

    /**
     * Get LDAP configuration from LdapSettingsService (database with cache)
     * Falls back to static properties if service fails
     */
    public static LdapConfig getConfig() {
        try {
            LdapSettings settings = ldapSettingsService.getLdapSettings();
            
            // Convert LdapSettings to LdapConfig
            return new LdapConfig(
                    settings.getHost(),
                    settings.getPort(),
                    settings.getBaseDn(),
                    settings.getUserSearchBase(), // userDn
                    settings.getGroupSearchBase(), // groupDn
                    settings.isUseSsl(),
                    settings.getConnectionTimeout(),
                    10000, // readTimeout (default, not in LdapSettings)
                    settings.isLdapEnabled()
            );
        } catch (Exception e) {
            logger.warn("Failed to get LDAP settings from service, falling back to static properties: {}", e.getMessage());
            // Fallback to static properties
            return new LdapConfig(
                    properties.getProperty("ldap.host"),
                    Integer.parseInt(properties.getProperty("ldap.port")),
                    properties.getProperty("ldap.baseDn"),
                    properties.getProperty("ldap.userDn"),
                    properties.getProperty("ldap.groupDn"),
                    Boolean.parseBoolean(properties.getProperty("ldap.useSsl")),
                    Integer.parseInt(properties.getProperty("ldap.connectionTimeout")),
                    Integer.parseInt(properties.getProperty("ldap.readTimeout")),
                    Boolean.parseBoolean(properties.getProperty("ldap.enabled"))
            );
        }
    }
    
    /**
     * Get LdapSettings directly (for services that need full settings including bindDn/bindPassword)
     */
    public static LdapSettings getLdapSettings() {
        try {
            return ldapSettingsService.getLdapSettings();
        } catch (Exception e) {
            logger.warn("Failed to get LDAP settings from service: {}", e.getMessage());
            // Return default settings
            LdapSettings settings = new LdapSettings();
            settings.setLdapEnabled(Boolean.parseBoolean(properties.getProperty("ldap.enabled")));
            return settings;
        }
    }
    
    /**
     * Refresh LDAP settings cache
     */
    public static void refreshCache() {
        ldapSettingsService.refreshCache();
    }

    /**
     * Check if LDAP is enabled by reading from LdapSettingsService (database with cache)
     * Falls back to environment variable if service fails
     * This ensures that changes made in System Settings UI are immediately effective.
     */
    public static boolean isLdapEnabled() {
        try {
            LdapSettings settings = ldapSettingsService.getLdapSettings();
            boolean enabled = settings.isLdapEnabled();
            logger.debug("LDAP enabled status from service: {}", enabled);
            return enabled;
        } catch (Exception e) {
            logger.warn("Failed to read LDAP enabled status from service, falling back to environment variable: {}", e.getMessage());
        }
        
        // Fall back to environment variable or system property (from .env file)
        String enabledStr = System.getenv("LDAP_ENABLED");
        if (enabledStr == null) {
            enabledStr = System.getProperty("LDAP_ENABLED");
        }
        if (enabledStr == null) {
            // Fall back to static properties loaded at startup
            enabledStr = properties.getProperty("ldap.enabled");
        }
        
        boolean enabled = Boolean.parseBoolean(enabledStr != null ? enabledStr : "true");
        logger.debug("LDAP enabled status from environment/properties: {}", enabled);
        return enabled;
    }
}
