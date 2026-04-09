package com.example.budg_v2.model;

/**
 * LDAP Settings model
 * Represents all LDAP configuration settings
 */
public class LdapSettings {
    
    private boolean ldapEnabled;
    private String ldapUrl; // Format: ldap://host:port or ldaps://host:port
    private String baseDn;
    private String bindDn;
    private String bindPassword; // Encrypted in DB, decrypted in memory only
    private String userSearchBase;
    private String userSearchFilter; // Default: (uid={0})
    private String groupSearchBase; // Optional
    private int connectionTimeout; // milliseconds
    private boolean incrementalSyncEnabled; // Enable incremental sync using modifyTimestamp
    private String lastSyncTimestamp; // ISO 8601 format timestamp of last successful sync
    private String truststorePath; // Path to truststore file for LDAPS
    private String truststorePassword; // Password for truststore file
    private boolean trustAllCertificates; // Accept all certificates (not recommended for production)
    
    // Default constructor
    public LdapSettings() {
        this.ldapEnabled = false;
        this.userSearchFilter = "(uid={0})";
        this.connectionTimeout = 5000; // 5 seconds default
    }
    
    // Full constructor
    public LdapSettings(boolean ldapEnabled, String ldapUrl, String baseDn, String bindDn,
                       String bindPassword, String userSearchBase, String userSearchFilter,
                       String groupSearchBase, int connectionTimeout) {
        this.ldapEnabled = ldapEnabled;
        this.ldapUrl = ldapUrl;
        this.baseDn = baseDn;
        this.bindDn = bindDn;
        this.bindPassword = bindPassword;
        this.userSearchBase = userSearchBase;
        this.userSearchFilter = userSearchFilter != null ? userSearchFilter : "(uid={0})";
        this.groupSearchBase = groupSearchBase;
        this.connectionTimeout = connectionTimeout > 0 ? connectionTimeout : 5000;
        this.incrementalSyncEnabled = false;
        this.lastSyncTimestamp = null;
        this.truststorePath = null;
        this.truststorePassword = null;
        this.trustAllCertificates = false;
    }
    
    // Getters and Setters
    public boolean isLdapEnabled() {
        return ldapEnabled;
    }
    
    public void setLdapEnabled(boolean ldapEnabled) {
        this.ldapEnabled = ldapEnabled;
    }
    
    public String getLdapUrl() {
        return ldapUrl;
    }
    
    public void setLdapUrl(String ldapUrl) {
        this.ldapUrl = ldapUrl;
    }
    
    public String getBaseDn() {
        return baseDn;
    }
    
    public void setBaseDn(String baseDn) {
        this.baseDn = baseDn;
    }
    
    public String getBindDn() {
        return bindDn;
    }
    
    public void setBindDn(String bindDn) {
        this.bindDn = bindDn;
    }
    
    public String getBindPassword() {
        return bindPassword;
    }
    
    public void setBindPassword(String bindPassword) {
        this.bindPassword = bindPassword;
    }
    
    public String getUserSearchBase() {
        return userSearchBase;
    }
    
    public void setUserSearchBase(String userSearchBase) {
        this.userSearchBase = userSearchBase;
    }
    
    public String getUserSearchFilter() {
        return userSearchFilter;
    }
    
    public void setUserSearchFilter(String userSearchFilter) {
        this.userSearchFilter = userSearchFilter != null ? userSearchFilter : "(uid={0})";
    }
    
    public String getGroupSearchBase() {
        return groupSearchBase;
    }
    
    public void setGroupSearchBase(String groupSearchBase) {
        this.groupSearchBase = groupSearchBase;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout > 0 ? connectionTimeout : 5000;
    }
    
    public boolean isIncrementalSyncEnabled() {
        return incrementalSyncEnabled;
    }
    
    public void setIncrementalSyncEnabled(boolean incrementalSyncEnabled) {
        this.incrementalSyncEnabled = incrementalSyncEnabled;
    }
    
    public String getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }
    
    public void setLastSyncTimestamp(String lastSyncTimestamp) {
        this.lastSyncTimestamp = lastSyncTimestamp;
    }
    
    public String getTruststorePath() {
        return truststorePath;
    }
    
    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }
    
    public String getTruststorePassword() {
        return truststorePassword;
    }
    
    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }
    
    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }
    
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }
    
    /**
     * Extract host from ldapUrl
     */
    public String getHost() {
        if (ldapUrl == null || ldapUrl.isEmpty()) {
            return null;
        }
        try {
            // Remove protocol (ldap:// or ldaps://)
            String url = ldapUrl.replaceFirst("^ldaps?://", "");
            // Extract host (before : or /)
            int colonIndex = url.indexOf(':');
            if (colonIndex > 0) {
                return url.substring(0, colonIndex);
            }
            int slashIndex = url.indexOf('/');
            if (slashIndex > 0) {
                return url.substring(0, slashIndex);
            }
            return url;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract port from ldapUrl
     */
    public int getPort() {
        if (ldapUrl == null || ldapUrl.isEmpty()) {
            return 389; // Default LDAP port
        }
        try {
            // Remove protocol
            String url = ldapUrl.replaceFirst("^ldaps?://", "");
            // Extract port
            int colonIndex = url.indexOf(':');
            if (colonIndex > 0) {
                String portStr = url.substring(colonIndex + 1);
                int slashIndex = portStr.indexOf('/');
                if (slashIndex > 0) {
                    portStr = portStr.substring(0, slashIndex);
                }
                return Integer.parseInt(portStr);
            }
            // Default ports based on protocol
            if (ldapUrl.startsWith("ldaps://")) {
                return 636; // Default LDAPS port
            }
            return 389; // Default LDAP port
        } catch (Exception e) {
            return ldapUrl.startsWith("ldaps://") ? 636 : 389;
        }
    }
    
    /**
     * Check if SSL/TLS is enabled
     */
    public boolean isUseSsl() {
        return ldapUrl != null && ldapUrl.startsWith("ldaps://");
    }
    
    /**
     * Validate required fields when LDAP is enabled
     */
    public boolean isValid() {
        if (!ldapEnabled) {
            return true; // Valid if disabled
        }
        return ldapUrl != null && !ldapUrl.trim().isEmpty() &&
               baseDn != null && !baseDn.trim().isEmpty() &&
               bindDn != null && !bindDn.trim().isEmpty() &&
               bindPassword != null && !bindPassword.trim().isEmpty() &&
               userSearchBase != null && !userSearchBase.trim().isEmpty() &&
               userSearchFilter != null && !userSearchFilter.trim().isEmpty();
    }
}

