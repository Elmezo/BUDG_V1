package com.example.budg_v2.model;

/**
 * EDC (Enterprise Data Catalog) Settings model
 * Represents all EDC integration configuration settings
 */
public class EdcSettings {
    
    // Server connection settings
    private String serverHost; // eic_server_host
    private Integer serverPort; // eic_server_port
    private String loginUsername; // eic_server_login_username
    private String loginPassword; // eic_server_login_password (encrypted in DB)
    private String loginNamespace; // eic_server_login_namespace
    
    // Resource and configuration
    private String axonResourceName; // eic_axon_resource_name
    private String axonSuperAdminEmail; // eic_axon_super_admin_email
    
    // Feature flags
    private boolean enableAutoLineageRecommendation; // eic_enable_auto_lineage_recommendation
    private boolean enableLineageEmailNotification; // eic_enable_lineage_email_notification
    private boolean enableCustomAttributes; // eic_enable_custom_attributes
    private boolean enableCleanupLineageRecommendations; // eic_enable_cleanup_lineage_recommendations
    private boolean enableFilter; // eic_enable_filter
    private boolean updateOnboardedAssets; // eic_update_onboarded_assets
    
    // Additional settings
    private String defaultGlossary; // eic_default_glossary
    private Integer requestTimeout; // eic_request_timeout (seconds)
    private String proxyHost; // eic_proxy_host
    private Integer proxyPort; // eic_proxy_port
    
    // SSL/TLS settings
    private boolean sslInsecure; // eic_ssl_insecure (dev/testing only)
    
    // Default constructor
    public EdcSettings() {
        this.serverHost = "https://edc.local";
        this.serverPort = 9185;
        this.loginUsername = "Administrator";
        this.loginPassword = null;
        this.loginNamespace = "Native";
        this.axonResourceName = "BUDG_Resource_copy";
        this.axonSuperAdminEmail = "admin@budg.com";
        this.enableAutoLineageRecommendation = true;
        this.enableLineageEmailNotification = true;
        this.enableCustomAttributes = true;
        this.enableCleanupLineageRecommendations = true;
        this.enableFilter = false;
        this.updateOnboardedAssets = true;
        this.defaultGlossary = "";
        this.requestTimeout = 120;
        this.proxyHost = "";
        this.proxyPort = null;
        this.sslInsecure = false;
    }
    
    // Getters and Setters
    public String getServerHost() {
        return serverHost;
    }
    
    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }
    
    public Integer getServerPort() {
        return serverPort;
    }
    
    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }
    
    public String getLoginUsername() {
        return loginUsername;
    }
    
    public void setLoginUsername(String loginUsername) {
        this.loginUsername = loginUsername;
    }
    
    public String getLoginPassword() {
        return loginPassword;
    }
    
    public void setLoginPassword(String loginPassword) {
        this.loginPassword = loginPassword;
    }
    
    public String getLoginNamespace() {
        return loginNamespace;
    }
    
    public void setLoginNamespace(String loginNamespace) {
        this.loginNamespace = loginNamespace;
    }
    
    public String getAxonResourceName() {
        return axonResourceName;
    }
    
    public void setAxonResourceName(String axonResourceName) {
        this.axonResourceName = axonResourceName;
    }
    
    public String getAxonSuperAdminEmail() {
        return axonSuperAdminEmail;
    }
    
    public void setAxonSuperAdminEmail(String axonSuperAdminEmail) {
        this.axonSuperAdminEmail = axonSuperAdminEmail;
    }
    
    public boolean isEnableAutoLineageRecommendation() {
        return enableAutoLineageRecommendation;
    }
    
    public void setEnableAutoLineageRecommendation(boolean enableAutoLineageRecommendation) {
        this.enableAutoLineageRecommendation = enableAutoLineageRecommendation;
    }
    
    public boolean isEnableLineageEmailNotification() {
        return enableLineageEmailNotification;
    }
    
    public void setEnableLineageEmailNotification(boolean enableLineageEmailNotification) {
        this.enableLineageEmailNotification = enableLineageEmailNotification;
    }
    
    public boolean isEnableCustomAttributes() {
        return enableCustomAttributes;
    }
    
    public void setEnableCustomAttributes(boolean enableCustomAttributes) {
        this.enableCustomAttributes = enableCustomAttributes;
    }
    
    public boolean isEnableCleanupLineageRecommendations() {
        return enableCleanupLineageRecommendations;
    }
    
    public void setEnableCleanupLineageRecommendations(boolean enableCleanupLineageRecommendations) {
        this.enableCleanupLineageRecommendations = enableCleanupLineageRecommendations;
    }
    
    public boolean isEnableFilter() {
        return enableFilter;
    }
    
    public void setEnableFilter(boolean enableFilter) {
        this.enableFilter = enableFilter;
    }
    
    public boolean isUpdateOnboardedAssets() {
        return updateOnboardedAssets;
    }
    
    public void setUpdateOnboardedAssets(boolean updateOnboardedAssets) {
        this.updateOnboardedAssets = updateOnboardedAssets;
    }
    
    public String getDefaultGlossary() {
        return defaultGlossary;
    }
    
    public void setDefaultGlossary(String defaultGlossary) {
        this.defaultGlossary = defaultGlossary != null ? defaultGlossary : "";
    }
    
    public Integer getRequestTimeout() {
        return requestTimeout;
    }
    
    public void setRequestTimeout(Integer requestTimeout) {
        this.requestTimeout = requestTimeout != null && requestTimeout > 0 ? requestTimeout : 120;
    }
    
    public String getProxyHost() {
        return proxyHost;
    }
    
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost != null ? proxyHost : "";
    }
    
    public Integer getProxyPort() {
        return proxyPort;
    }
    
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }
    
    public boolean isSslInsecure() {
        return sslInsecure;
    }
    
    public void setSslInsecure(boolean sslInsecure) {
        this.sslInsecure = sslInsecure;
    }
    
    /**
     * Get full server URL (host:port)
     */
    public String getServerUrl() {
        if (serverHost == null || serverHost.trim().isEmpty()) {
            return null;
        }
        String host = serverHost.trim();
        // Remove trailing slash if present
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        // Add port if not already in host
        if (serverPort != null && serverPort > 0) {
            // Check if port is already in host (e.g., https://host:port)
            if (!host.contains(":" + serverPort) && !host.matches(".*:\\d+$")) {
                // Extract scheme and hostname
                if (host.startsWith("http://") || host.startsWith("https://")) {
                    String scheme = host.startsWith("https://") ? "https://" : "http://";
                    String hostname = host.substring(scheme.length());
                    int colonIndex = hostname.indexOf(':');
                    if (colonIndex > 0) {
                        hostname = hostname.substring(0, colonIndex);
                    }
                    int slashIndex = hostname.indexOf('/');
                    if (slashIndex > 0) {
                        hostname = hostname.substring(0, slashIndex);
                    }
                    return scheme + hostname + ":" + serverPort;
                }
            }
        }
        return host;
    }
    
    /**
     * Validate required fields
     */
    public boolean isValid() {
        return serverHost != null && !serverHost.trim().isEmpty() &&
               (serverHost.startsWith("http://") || serverHost.startsWith("https://")) &&
               serverPort != null && serverPort > 0 && serverPort <= 65535 &&
               loginUsername != null && !loginUsername.trim().isEmpty() &&
               axonSuperAdminEmail != null && !axonSuperAdminEmail.trim().isEmpty() &&
               isValidEmail(axonSuperAdminEmail) &&
               requestTimeout != null && requestTimeout > 0 && requestTimeout <= 3600 &&
               (proxyHost == null || proxyHost.trim().isEmpty() || 
                (proxyPort != null && proxyPort > 0 && proxyPort <= 65535));
    }
    
    /**
     * Basic email validation
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}

