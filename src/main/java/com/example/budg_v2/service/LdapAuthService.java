package com.example.budg_v2.service;

import com.example.budg_v2.util.LdapConfigUtil;
import com.example.budg_v2.model.LdapSettings;
import com.example.budg_v2.exception.LdapConnectionException;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import com.unboundid.util.ssl.TrustStoreTrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class LdapAuthService {

    private static final Logger logger = LoggerFactory.getLogger(LdapAuthService.class);

    public LdapAuthService() {
        // Settings are loaded dynamically from LdapSettingsService
    }
    
    /**
     * Get LDAP settings (loaded dynamically with cache)
     */
    private LdapSettings getSettings() {
        return LdapConfigUtil.getLdapSettings();
    }

    /**
     * Authenticate user against LDAP server
     * @param username The username (uid) or email to authenticate
     * @param password The password to verify
     * @return Map containing user information if authentication successful, null otherwise
     * @throws LdapConnectionException if connection to LDAP server fails
     */
    public Map<String, Object> authenticateUser(String username, String password) throws LdapConnectionException {
        LdapSettings settings = getSettings();
        String maskedUsername = maskPrincipalForLog(username);
        
        if (!settings.isLdapEnabled()) {
            logger.debug("LDAP authentication is disabled");
            return null;
        }

        LDAPConnection connection = null;
        try {
            // Create connection to LDAP server with bind credentials
            connection = createConnection(settings);

            // Build user search filter using configured filter pattern
            String userSearchFilter = buildUserSearchFilter(settings.getUserSearchFilter(), username);
            logger.debug("Using LDAP search filter for user: {}", maskedUsername);

            SearchRequest searchRequest = new SearchRequest(
                    settings.getUserSearchBase(),
                    SearchScope.SUB,
                    userSearchFilter,
                    "uid", "cn", "sn", "givenName", "mail", "memberOf", "objectClass"
            );

            SearchResult searchResult = connection.search(searchRequest);

            if (searchResult.getEntryCount() == 0) {
                logger.warn("User not found in LDAP: {}", maskedUsername);
                return null;
            }

            SearchResultEntry userEntry = searchResult.getSearchEntries().get(0);
            String userDn = userEntry.getDN();

            // Attempt to bind with user credentials
            LDAPConnection userConnection = null;
            try {
                userConnection = createConnection(settings);
                BindRequest bindRequest = new SimpleBindRequest(userDn, password);
                BindResult bindResult = userConnection.bind(bindRequest);

                if (bindResult.getResultCode() == ResultCode.SUCCESS) {
                    // Authentication successful - extract user information
                    Map<String, Object> userInfo = extractUserInfo(userEntry, userConnection);
                    logger.info("LDAP authentication successful for user: {}", maskedUsername);
                    return userInfo;
                } else {
                    logger.warn("LDAP authentication failed for user: {} - {}", maskedUsername, bindResult.getResultString());
                    return null;
                }

            } catch (LDAPException e) {
                logger.warn("LDAP authentication failed for user: {} - {}", maskedUsername, e.getMessage());
                return null;
            } finally {
                if (userConnection != null) {
                    userConnection.close();
                }
            }

        } catch (LDAPException e) {
            // Check if it's a connection error - throw exception instead of returning null
            // Use settings from outer scope
            if (e.getResultCode() == ResultCode.CONNECT_ERROR || 
                e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                logger.error("Failed to connect to LDAP server at {}:{}. Error: {}",
                           settings.getHost(), settings.getPort(), e.getMessage(), e);
                throw new LdapConnectionException(
                    LdapConnectionException.ErrorType.CONNECTION_REFUSED,
                    String.format("Failed to connect to LDAP server at %s:%d. Connection refused.", settings.getHost(), settings.getPort()),
                    settings.getHost(),
                    settings.getPort(),
                    e
                );
            } else if (e.getResultCode() == ResultCode.TIMEOUT) {
                logger.error("LDAP connection timeout for user: {} - {}", maskedUsername, e.getMessage(), e);
                throw new LdapConnectionException(
                    LdapConnectionException.ErrorType.TIMEOUT,
                    String.format("Connection to LDAP server at %s:%d timed out.", settings.getHost(), settings.getPort()),
                    settings.getHost(),
                    settings.getPort(),
                    e
                );
            } else {
                // Other LDAP errors - could be server unavailable
                logger.error("LDAP authentication error for user: {} - {}", maskedUsername, e.getMessage(), e);
                throw new LdapConnectionException(
                    LdapConnectionException.ErrorType.SERVER_UNAVAILABLE,
                    String.format("LDAP server at %s:%d is not available. Error: %s", settings.getHost(), settings.getPort(), e.getMessage()),
                    settings.getHost(),
                    settings.getPort(),
                    e
                );
            }
        } catch (Exception e) {
            // Handle non-LDAP exceptions (e.g., network issues, connection refused)
            // Use settings from outer scope
            String errorMessage = e.getMessage();
            LdapConnectionException.ErrorType errorType;
            
            if (errorMessage != null && (errorMessage.contains("Connection refused") || 
                                         errorMessage.contains("connect"))) {
                errorType = LdapConnectionException.ErrorType.CONNECTION_REFUSED;
            } else if (errorMessage != null && errorMessage.contains("timeout")) {
                errorType = LdapConnectionException.ErrorType.TIMEOUT;
            } else {
                errorType = LdapConnectionException.ErrorType.UNKNOWN_ERROR;
            }
            
            logger.error("Unexpected error during LDAP authentication for user: {}", maskedUsername, e);
            throw new LdapConnectionException(
                errorType,
                String.format("Failed to connect to LDAP server at %s:%d. Error: %s", settings.getHost(), settings.getPort(), e.getMessage()),
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

    /**
     * Get user groups from LDAP
     * @param connection Active LDAP connection
     * @param userDn User's distinguished name
     * @return List of group names
     */
    public List<String> getUserGroups(LDAPConnection connection, String userDn) {
        List<String> groups = new ArrayList<>();
        LdapSettings settings = getSettings();

        try {
            // Skip if groupSearchBase is not configured
            if (settings.getGroupSearchBase() == null || settings.getGroupSearchBase().trim().isEmpty()) {
                logger.debug("Group search base not configured, skipping group retrieval");
                return groups;
            }
            
            // Search for groups that contain this user
            String groupSearchFilter = "(member=" + userDn + ")";
            SearchRequest groupSearchRequest = new SearchRequest(
                    settings.getGroupSearchBase(),
                    SearchScope.SUB,
                    groupSearchFilter,
                    "cn"
            );

            SearchResult groupSearchResult = connection.search(groupSearchRequest);

            for (SearchResultEntry groupEntry : groupSearchResult.getSearchEntries()) {
                Attribute cnAttr = groupEntry.getAttribute("cn");
                if (cnAttr != null) {
                    groups.add(cnAttr.getValue());
                }
            }

        } catch (Exception e) {
            logger.warn("Error retrieving groups for LDAP user DN", e);
        }

        return groups;
    }

    /**
     * Get user group DNs (Distinguished Names) from LDAP
     * Used for OrgUnit mapping - returns DNs instead of names
     * 
     * @param connection Active LDAP connection
     * @param userDn User's distinguished name
     * @return List of group DNs (Distinguished Names)
     */
    public List<String> getUserGroupDns(LDAPConnection connection, String userDn) {
        List<String> groupDns = new ArrayList<>();
        LdapSettings settings = getSettings();

        try {
            // Skip if groupSearchBase is not configured
            if (settings.getGroupSearchBase() == null || settings.getGroupSearchBase().trim().isEmpty()) {
                logger.debug("Group search base not configured, skipping group DN retrieval");
                return groupDns;
            }
            
            // Search for groups that contain this user
            String groupSearchFilter = "(member=" + userDn + ")";
            SearchRequest groupSearchRequest = new SearchRequest(
                    settings.getGroupSearchBase(),
                    SearchScope.SUB,
                    groupSearchFilter,
                    "1.1" // No attributes needed, we only need the DN
            );

            SearchResult groupSearchResult = connection.search(groupSearchRequest);

            for (SearchResultEntry groupEntry : groupSearchResult.getSearchEntries()) {
                // Get the DN directly from the entry
                String groupDn = groupEntry.getDN();
                if (groupDn != null && !groupDn.trim().isEmpty()) {
                    groupDns.add(groupDn);
                }
            }

        } catch (Exception e) {
            logger.warn("Error retrieving group DNs for LDAP user DN", e);
        }

        return groupDns;
    }

    /**
     * Build user search filter from pattern
     * Replaces {0} placeholder with username (sanitized to prevent LDAP injection)
     */
    private String buildUserSearchFilter(String filterPattern, String username) {
        if (filterPattern == null || filterPattern.trim().isEmpty()) {
            // Default filter with sanitization
            String sanitized = sanitizeLdapFilterValue(username);
            if (username.contains("@")) {
                String emailUsername = username.substring(0, username.indexOf("@"));
                sanitized = sanitizeLdapFilterValue(emailUsername);
                return "(uid=" + sanitized + ")";
            } else {
                return "(uid=" + sanitized + ")";
            }
        }
        
        // Sanitize username before replacement to prevent LDAP injection
        String sanitized = sanitizeLdapFilterValue(username);
        String searchFilter = filterPattern.replace("{0}", sanitized);
        
        // If username contains @, also try with extracted username
        if (username.contains("@") && !filterPattern.contains("{0}")) {
            String emailUsername = username.substring(0, username.indexOf("@"));
            sanitized = sanitizeLdapFilterValue(emailUsername);
            searchFilter = filterPattern.replace("{0}", sanitized);
        }
        
        return searchFilter;
    }
    
    /**
     * Sanitize LDAP filter value to prevent injection attacks
     * Escapes special characters: *, (, ), \, /, NUL
     * 
     * @param value The value to sanitize
     * @return Sanitized value safe for use in LDAP filters
     */
    private String sanitizeLdapFilterValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        
        // Escape special LDAP filter characters according to RFC 4515
        return value
            .replace("\\", "\\5c")  // Backslash
            .replace("*", "\\2a")    // Asterisk
            .replace("(", "\\28")    // Left parenthesis
            .replace(")", "\\29")    // Right parenthesis
            .replace("\u0000", "\\00") // NUL character
            .replace("/", "\\2f");   // Forward slash
    }
    
    /**
     * Create LDAP connection with bind credentials if configured
     * @param settings LDAP settings
     * @return LDAPConnection instance
     * @throws LDAPException if connection fails
     */
    private LDAPConnection createConnection(LdapSettings settings) throws LDAPException {
        LDAPConnection connection;

        try {
            String host = settings.getHost();
            int port = settings.getPort();
            boolean useSsl = settings.isUseSsl();
            
            // Configure SSL/TLS if using LDAPS
            if (useSsl) {
                SSLUtil sslUtil = createSSLUtil(settings);
                if (sslUtil != null) {
                    // Create connection with SSL socket factory
                    connection = new LDAPConnection(sslUtil.createSSLSocketFactory());
                } else {
                    // Fallback to default SSL connection
                    connection = new LDAPConnection();
                }
            } else {
                connection = new LDAPConnection();
            }
            
            // Connect to LDAP server
            logger.debug("Attempting to connect to LDAP server at {}:{} (SSL: {})", host, port, useSsl);
            connection.connect(host, port, settings.getConnectionTimeout());
            
            // If bindDn and bindPassword are configured, bind with them
            if (settings.getBindDn() != null && !settings.getBindDn().trim().isEmpty() &&
                settings.getBindPassword() != null && !settings.getBindPassword().trim().isEmpty()) {
                
                BindRequest bindRequest = new SimpleBindRequest(settings.getBindDn(), settings.getBindPassword());
                BindResult bindResult = connection.bind(bindRequest);
                
                if (bindResult.getResultCode() != ResultCode.SUCCESS) {
                    throw new LDAPException(bindResult.getResultCode(), 
                        "LDAP bind failed: " + bindResult.getResultString());
                }
                
                logger.debug("Successfully bound to LDAP server with configured bind DN");
            }
            
            logger.debug("Successfully connected to LDAP server at {}:{}", host, port);
        } catch (LDAPException e) {
            // Provide more detailed error information
            String errorMsg = String.format(
                "Failed to connect to LDAP server at %s:%d. " +
                "Error: %s (Result Code: %s). " +
                "Please verify: 1) LDAP server is running, 2) Host and port are correct, 3) Firewall allows connection.",
                settings.getHost(), settings.getPort(), e.getMessage(), e.getResultCode()
            );
            logger.error(errorMsg, e);
            throw new LDAPException(e.getResultCode(), errorMsg, e);
        } catch (Exception e) {
            // Handle non-LDAP exceptions (e.g., network issues)
            String errorMsg = String.format(
                "Failed to connect to LDAP server at %s:%d. " +
                "Connection refused - the server may not be running or is not accessible. " +
                "Error: %s",
                settings.getHost(), settings.getPort(), e.getMessage()
            );
            logger.error(errorMsg, e);
            throw new LDAPException(ResultCode.CONNECT_ERROR, errorMsg, e);
        }

        return connection;
    }
    
    /**
     * Create SSLUtil for LDAPS connections with truststore configuration
     */
    private SSLUtil createSSLUtil(LdapSettings settings) {
        try {
            // If trustAllCertificates is enabled, use TrustAllTrustManager (not recommended for production)
            if (settings.isTrustAllCertificates()) {
                logger.warn("TrustAllCertificates is enabled - accepting all SSL certificates (not recommended for production)");
                return new SSLUtil(new TrustAllTrustManager());
            }
            
            // If truststore is configured, use it
            if (settings.getTruststorePath() != null && !settings.getTruststorePath().trim().isEmpty()) {
                File truststoreFile = new File(settings.getTruststorePath());
                if (!truststoreFile.exists()) {
                    logger.error("Truststore file not found: {}", settings.getTruststorePath());
                    return null;
                }
                
                String truststorePassword = settings.getTruststorePassword();
                if (truststorePassword == null) {
                    truststorePassword = ""; // Empty password
                }
                
                logger.debug("Using truststore: {}", settings.getTruststorePath());
                TrustStoreTrustManager trustManager = new TrustStoreTrustManager(
                    truststoreFile.getAbsolutePath(),
                    truststorePassword.toCharArray(),
                    "JKS", // Default to JKS format
                    true   // Trust all certificates in the truststore
                );
                
                return new SSLUtil(trustManager);
            }
            
            // No truststore configured - use default Java truststore
            logger.debug("No truststore configured - using default Java truststore");
            return null;
            
        } catch (Exception e) {
            logger.error("Error creating SSLUtil for LDAPS connection", e);
            return null;
        }
    }

    /**
     * Extract user information from LDAP search result
     * @param userEntry LDAP search result entry
     * @param connection Active LDAP connection
     * @return Map containing user information
     */
    private Map<String, Object> extractUserInfo(SearchResultEntry userEntry, LDAPConnection connection) {
        Map<String, Object> userInfo = new HashMap<>();

        try {
            // Extract basic user attributes
            userInfo.put("uid", getAttributeValue(userEntry, "uid"));
            userInfo.put("cn", getAttributeValue(userEntry, "cn"));
            userInfo.put("sn", getAttributeValue(userEntry, "sn"));
            userInfo.put("givenName", getAttributeValue(userEntry, "givenName"));
            userInfo.put("mail", getAttributeValue(userEntry, "mail"));
            userInfo.put("dn", userEntry.getDN());

            // Get user groups
            List<String> groups = getUserGroups(connection, userEntry.getDN());
            userInfo.put("groups", groups);

            // Determine role based on groups
            String role = determineRole(groups);
            userInfo.put("role", role);

            // Set display name
            String displayName = getAttributeValue(userEntry, "cn");
            if (displayName == null || displayName.trim().isEmpty()) {
                String givenName = getAttributeValue(userEntry, "givenName");
                String sn = getAttributeValue(userEntry, "sn");
                if (givenName != null && sn != null) {
                    displayName = givenName + " " + sn;
                } else {
                    displayName = getAttributeValue(userEntry, "uid");
                }
            }
            userInfo.put("displayName", displayName);

            // Set email for compatibility with existing system
            String email = getAttributeValue(userEntry, "mail");
            if (email == null || email.trim().isEmpty()) {
                email = getAttributeValue(userEntry, "uid") + "@example.com";
            }
            userInfo.put("email", email);

        } catch (Exception e) {
            logger.error("Error extracting user information", e);
        }

        return userInfo;
    }

    /**
     * Get attribute value from LDAP search result entry
     * @param entry LDAP search result entry
     * @param attributeName Attribute name
     * @return Attribute value or null if not found
     */
    private String getAttributeValue(SearchResultEntry entry, String attributeName) {
        try {
            Attribute attr = entry.getAttribute(attributeName);
            if (attr != null) {
                return attr.getValue();
            }
        } catch (Exception e) {
            logger.debug("Attribute {} not found for user", attributeName);
        }
        return null;
    }

    /**
     * Determine user role based on LDAP groups
     * @param groups List of group names
     * @return Role string
     */
    private String determineRole(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return "User";
        }

        // Check for SuperAdmin group variations
        for (String group : groups) {
            String lowerGroup = group.toLowerCase();
            if (lowerGroup.contains("superadmin") ||
                    lowerGroup.contains("super_admin") ||
                    lowerGroup.contains("super-admin") ||
                    lowerGroup.contains("super admin") ||
                    lowerGroup.contains("suber admin")) {
                return "Super Admin";
            }
        }

        // Check for Admin group
        for (String group : groups) {
            if ("Admin".equalsIgnoreCase(group) || "admin".equalsIgnoreCase(group)) {
                return "Admin";
            }
        }

        // Check for WebUser group
        for (String group : groups) {
            if ("WebUser".equalsIgnoreCase(group) || "webuser".equalsIgnoreCase(group)) {
                return "WebUser";
            }
        }

        // Default to User if no specific role found
        return "WebUser";
    }

    /**
     * Test LDAP connection (deprecated - use LdapSettingsService.testConnection instead)
     * @return true if connection successful, false otherwise
     */
    @Deprecated
    public boolean testConnection() {
        LdapSettings settings = getSettings();
        if (!settings.isLdapEnabled()) {
            logger.info("LDAP is disabled, skipping connection test");
            return true;
        }

        LDAPConnection connection = null;
        try {
            connection = createConnection(settings);
            logger.info("LDAP connection test successful");
            return true;
        } catch (Exception e) {
            logger.error("LDAP connection test failed", e);
            return false;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static String maskPrincipalForLog(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        if (at > 0) {
            String local = trimmed.substring(0, at);
            String domain = trimmed.substring(at + 1);
            return local.substring(0, Math.min(1, local.length())) + "***@" + domain;
        }
        return trimmed.substring(0, Math.min(1, trimmed.length())) + "***";
    }
}