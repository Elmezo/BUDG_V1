package com.example.budg_v2.service;

import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.OrgUnitDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.LdapSettings;
import com.example.budg_v2.model.OrgUnit;
import com.google.gson.JsonObject;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import com.unboundid.util.ssl.TrustStoreTrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Service for synchronizing LDAP users with database
 * Implements batch processing, error handling, and real-time progress updates
 */
public class LdapSyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(LdapSyncService.class);
    private static final int BATCH_SIZE = 200;
    private static final String TEMP_DIR = "/budg_ldap_synchronizer/tmp";
    private static final long TEMP_CLEANUP_EXPIRATION_MS = 60 * 60 * 1000; // 60 minutes
    // Special password value for LDAP users - must match LoginServlet.LDAP_USER_PASSWORD
    public static final String LDAP_USER_PASSWORD = "LDAP_AUTH_REQUIRED_#@!$%^&*()";
    
    private final LdapSettingsService ldapSettingsService;
    private final LdapAuthService ldapAuthService;
    private final PeopleService peopleService;
    private final JobDAO jobDAO;
    private final OrgUnitDAO orgUnitDAO;
    private final LdapSyncLockManager lockManager;
    private final BulkUploadBroadcaster broadcaster;
    private final LdapOrgUnitMappingService ldapOrgUnitMappingService;
    
    // Statistics
    private final AtomicInteger usersCreated = new AtomicInteger(0);
    private final AtomicInteger usersUpdated = new AtomicInteger(0);
    private final AtomicInteger orgUnitsCreated = new AtomicInteger(0);
    private final AtomicInteger orgUnitsUpdated = new AtomicInteger(0);
    private final AtomicInteger usersSkipped = new AtomicInteger(0);
    
    public LdapSyncService() {
        this.ldapSettingsService = new LdapSettingsService();
        this.ldapAuthService = new LdapAuthService();
        this.peopleService = new PeopleService();
        this.jobDAO = new JobDAO();
        this.orgUnitDAO = new OrgUnitDAO();
        this.lockManager = new LdapSyncLockManager();
        this.broadcaster = BulkUploadBroadcaster.getInstance();
        this.ldapOrgUnitMappingService = new LdapOrgUnitMappingService();
    }
    
    /**
     * Main synchronization method
     * @param userId User ID initiating the sync
     * @param jobId Job ID for tracking
     */
    public void synchronize(int userId, int jobId) {
        LDAPConnection connection = null;
        boolean lockAcquired = false;
        long startTime = System.currentTimeMillis();
        String correlationId = null;
        int historyId = 0;
        
        try {
            // Get correlation ID from MDC (set by CorrelationIdFilter) or generate new one
            correlationId = MDC.get("correlation_id");
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
                MDC.put("correlation_id", correlationId);
            }
            
            // Create sync history record
            historyId = createSyncHistory(jobId, userId);
            
            // Step 1: Convert LDAP settings to internal model
            logInfo(jobId, correlationId, "Started | Converting LDAP service configuration to internal model...");
            LdapSettings settings = ldapSettingsService.getLdapSettings();
            
            if (!settings.isLdapEnabled()) {
                String errorMessage = "LDAP is not enabled. Please enable LDAP in System Settings before synchronizing.";
                logError(jobId, correlationId, errorMessage);
                
                // Broadcast final failure message
                JsonObject finalUpdate = new JsonObject();
                finalUpdate.addProperty("status", "Failed");
                finalUpdate.addProperty("progress", 0);
                finalUpdate.addProperty("message", errorMessage);
                finalUpdate.addProperty("timestamp", System.currentTimeMillis());
                broadcaster.broadcast(jobId, finalUpdate);
                
                updateJobStatus(jobId, "Failed", true);
                // No lock acquired yet, so no need to release
                return;
            }
            
            if (!settings.isValid()) {
                String errorMessage = "LDAP configuration is invalid. Please check LDAP settings.";
                logError(jobId, correlationId, errorMessage);
                
                // Broadcast final failure message
                JsonObject finalUpdate = new JsonObject();
                finalUpdate.addProperty("status", "Failed");
                finalUpdate.addProperty("progress", 0);
                finalUpdate.addProperty("message", errorMessage);
                finalUpdate.addProperty("timestamp", System.currentTimeMillis());
                broadcaster.broadcast(jobId, finalUpdate);
                
                updateJobStatus(jobId, "Failed", true);
                // No lock acquired yet, so no need to release
                return;
            }
            
            // Step 2: Acquire lock file
            logInfo(jobId, correlationId, "Acquiring lock file to prevent concurrent execution...");
            if (!lockManager.acquireLock(userId)) {
                LdapSyncLockManager.LockInfo lockInfo = lockManager.getLockInfo();
                String errorMessage;
                if (lockInfo != null) {
                    errorMessage = String.format("LDAP sync is already running. Locked by user %d at %s", 
                        lockInfo.getUserId(), new java.util.Date(lockInfo.getStartTimestamp()));
                } else {
                    errorMessage = "Failed to acquire lock file. Another sync may be in progress.";
                }
                logError(jobId, correlationId, errorMessage);
                
                // Broadcast final failure message
                JsonObject finalUpdate = new JsonObject();
                finalUpdate.addProperty("status", "Failed");
                finalUpdate.addProperty("progress", 0);
                finalUpdate.addProperty("message", errorMessage);
                finalUpdate.addProperty("timestamp", System.currentTimeMillis());
                broadcaster.broadcast(jobId, finalUpdate);
                
                updateJobStatus(jobId, "Failed", true);
                return;
            }
            logInfo(jobId, correlationId, "Lock file acquired successfully.");
            lockAcquired = true;
            
            try {
                // Step 3: Fetch users from LDAP
                logInfo(jobId, correlationId, "Fetching users from LDAP server...");
                List<Map<String, Object>> ldapUsers = fetchAllUsersFromLdap(settings, jobId, correlationId);
                logInfo(jobId, correlationId, String.format("Fetched %d users from LDAP", ldapUsers.size()));
                
                if (ldapUsers.isEmpty()) {
                    logInfo(jobId, correlationId, "No users found in LDAP. Synchronization completed.");
                    
                    // Release lock before returning
                    lockManager.releaseLock();
                    lockAcquired = false;
                    logInfo(jobId, correlationId, "Lock file released.");
                    
                    // Broadcast final completion message
                    JsonObject finalUpdate = new JsonObject();
                    finalUpdate.addProperty("status", "Completed");
                    finalUpdate.addProperty("progress", 100);
                    finalUpdate.addProperty("message", "No users found in LDAP. Synchronization completed.");
                    finalUpdate.addProperty("inserted", 0);
                    finalUpdate.addProperty("updated", 0);
                    finalUpdate.addProperty("orgUnitsCreated", 0);
                    finalUpdate.addProperty("orgUnitsUpdated", 0);
                    finalUpdate.addProperty("failed", 0);
                    finalUpdate.addProperty("timestamp", System.currentTimeMillis());
                    broadcaster.broadcast(jobId, finalUpdate);
                    
                    updateJobStatus(jobId, "Completed", true);
                    return;
                }
                
                // Step 4: Batch-fetch existing BUDG users
                logInfo(jobId, correlationId, "Batch-fetching existing BUDG users (batch size: " + BATCH_SIZE + ")...");
                Map<String, Map<String, Object>> existingUsers = fetchExistingUsers(ldapUsers, jobId, correlationId);
                logInfo(jobId, correlationId, String.format("Found %d existing users in database", existingUsers.size()));
                
                // Step 5: Compare and identify new/updated users
                logInfo(jobId, correlationId, "Comparing LDAP users with database...");
                List<Map<String, Object>> newUsers = new ArrayList<>();
                List<Map<String, Object>> updatedUsers = new ArrayList<>();
                
                for (Map<String, Object> ldapUser : ldapUsers) {
                    String email = (String) ldapUser.get("mail");
                    if (email == null || email.trim().isEmpty()) {
                        usersSkipped.incrementAndGet();
                        logWarn(jobId, correlationId, String.format("Skipping user: missing email attribute (DN: %s)", ldapUser.get("dn")));
                        continue;
                    }
                    
                    email = email.toLowerCase().trim();
                    ldapUser.put("email", email);
                    ldapUser.put("mail", email); // Ensure mail is also normalized
                    
                    Map<String, Object> existingUser = existingUsers.get(email);
                    if (existingUser == null) {
                        logger.debug("User not found in DB, will be inserted: {}", email);
                        newUsers.add(ldapUser);
                    } else {
                        logger.debug("User found in DB (ID: {}), checking for changes: {}", existingUser.get("id"), email);
                        // Check if attributes have changed
                        if (hasUserChanged(ldapUser, existingUser)) {
                            logger.debug("User has changes, will be updated: {}", email);
                            ldapUser.put("id", existingUser.get("id"));
                            updatedUsers.add(ldapUser);
                        } else {
                            logger.debug("User has no changes, will be skipped: {}", email);
                        }
                    }
                }
                
                logInfo(jobId, correlationId, String.format("Identified %d new users, %d users to update", 
                    newUsers.size(), updatedUsers.size()));
                
                // Step 6: Create new users (batch insert)
                if (!newUsers.isEmpty()) {
                    logInfo(jobId, correlationId, String.format("Creating %d new users...", newUsers.size()));
                    createUsersBatch(newUsers, userId, jobId, correlationId);
                }
                
                // Step 7: Update existing users (batch update)
                if (!updatedUsers.isEmpty()) {
                    logInfo(jobId, correlationId, String.format("Updating %d existing users...", updatedUsers.size()));
                    updateUsersBatch(updatedUsers, userId, jobId, correlationId);
                }
                
                // Step 8: Handle org units
                logInfo(jobId, correlationId, "Processing organizational units...");
                processOrgUnits(ldapUsers, userId, jobId, correlationId);
                
                // Step 8.5: Handle missing/disabled LDAP users
                handleMissingLdapUsers(ldapUsers, userId, jobId, correlationId);
                
                // Step 9: Cleanup temporary directory
                logInfo(jobId, correlationId, "Cleaning up temporary directory (expiration: 60 minutes)...");
                cleanupTempDirectory();
                logInfo(jobId, correlationId, "Cleanup completed.");
                
                // Step 10: Release lock file
                lockManager.releaseLock();
                lockAcquired = false;
                logInfo(jobId, correlationId, "Lock file released.");
                
                // Calculate duration
                long duration = System.currentTimeMillis() - startTime;
                double durationSeconds = duration / 1000.0;
                
                // Final summary in BUDG format
                String summary = String.format(
                    "[LDAP-SYNC] correlationId=%s Completed | UsersFetched=%d Added=%d Updated=%d Skipped=%d Failed=0 Duration=%.1fs",
                    correlationId, ldapUsers.size(), usersCreated.get(), usersUpdated.get(), 
                    usersSkipped.get(), durationSeconds
                );
                logger.info(summary);
                
                // Also log human-readable summary
                String humanSummary = String.format(
                    "Synchronization completed successfully. " +
                    "Users created: %d, Users updated: %d, Org units created: %d, Org units updated: %d, Skipped: %d",
                    usersCreated.get(), usersUpdated.get(), orgUnitsCreated.get(), orgUnitsUpdated.get(), usersSkipped.get()
                );
                logInfo(jobId, correlationId, humanSummary);
                
                // Broadcast final summary
                JsonObject finalUpdate = new JsonObject();
                finalUpdate.addProperty("status", "Completed");
                finalUpdate.addProperty("progress", 100);
                finalUpdate.addProperty("message", summary);
                finalUpdate.addProperty("inserted", usersCreated.get());
                finalUpdate.addProperty("updated", usersUpdated.get());
                finalUpdate.addProperty("orgUnitsCreated", orgUnitsCreated.get());
                finalUpdate.addProperty("orgUnitsUpdated", orgUnitsUpdated.get());
                finalUpdate.addProperty("failed", usersSkipped.get());
                finalUpdate.addProperty("timestamp", System.currentTimeMillis());
                broadcaster.broadcast(jobId, finalUpdate);
                
                updateJobStatus(jobId, "Completed", true);
                
                // Save last sync timestamp for incremental sync (ISO 8601 format)
                String timestamp = Instant.now().toString();
                ldapSettingsService.saveLastSyncTimestamp(timestamp);
                logger.debug("Last sync timestamp saved: {}", timestamp);
                
                // Update sync history with completion status
                updateSyncHistory(historyId, "Completed", ldapUsers.size(), usersCreated.get(), 
                    usersUpdated.get(), usersSkipped.get(), 0, orgUnitsCreated.get(), orgUnitsUpdated.get());
                
            } catch (Exception e) {
                logger.error("Error during LDAP synchronization", e);
                
                // Get detailed error message
                String errorMessage = e.getMessage();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = e.getClass().getSimpleName() + " occurred during synchronization";
                }
                
                // Ensure error message is clear and user-friendly
                String fullErrorMessage = "Synchronization failed: " + errorMessage;
                
                // Log and broadcast error
                logError(jobId, correlationId, fullErrorMessage);
                
                // Also broadcast a final failure message with full details to ensure it's received
                JsonObject finalUpdate = new JsonObject();
                finalUpdate.addProperty("status", "Failed");
                finalUpdate.addProperty("progress", 0);
                finalUpdate.addProperty("message", fullErrorMessage);
                finalUpdate.addProperty("timestamp", System.currentTimeMillis());
                
                // Try to broadcast multiple times to ensure message is received
                try {
                    broadcaster.broadcast(jobId, finalUpdate);
                    // Also send via the logError broadcast method as backup
                    broadcaster.broadcast(jobId, "Failed", 0, "ERROR: " + fullErrorMessage);
                } catch (Exception broadcastError) {
                    logger.error("Error broadcasting failure message", broadcastError);
                }
                
                updateJobStatus(jobId, "Failed", true);
                
                // Update sync history with failure status
                if (historyId > 0) {
                    updateSyncHistory(historyId, "Failed", 0, 0, 0, 0, 0, 0, 0, errorMessage);
                }
                
                lockManager.releaseLock();
                lockAcquired = false;
            }
            
        } catch (Exception e) {
            logger.error("Fatal error during LDAP synchronization", e);
            
            // Get detailed error message
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.getClass().getSimpleName() + " occurred during synchronization";
            }
            
            // Get correlation ID from MDC if available, otherwise use N/A
            String fatalCorrelationId = MDC.get("correlation_id");
            if (fatalCorrelationId == null || fatalCorrelationId.isEmpty()) {
                fatalCorrelationId = correlationId != null ? correlationId : "N/A";
            }
            
            String fullErrorMessage = "Fatal error: " + errorMessage;
            logError(jobId, fatalCorrelationId, fullErrorMessage);
            
            // Broadcast final failure message with full details to ensure it's received
            JsonObject finalUpdate = new JsonObject();
            finalUpdate.addProperty("status", "Failed");
            finalUpdate.addProperty("progress", 0);
            finalUpdate.addProperty("message", fullErrorMessage);
            finalUpdate.addProperty("timestamp", System.currentTimeMillis());
            
            // Try to broadcast multiple times to ensure message is received
            try {
                broadcaster.broadcast(jobId, finalUpdate);
                // Also send via the logError broadcast method as backup
                broadcaster.broadcast(jobId, "Failed", 0, "ERROR: " + fullErrorMessage);
            } catch (Exception broadcastError) {
                logger.error("Error broadcasting failure message", broadcastError);
            }
            
            updateJobStatus(jobId, "Failed", true);
            
            // Update sync history with failure status
            if (historyId > 0) {
                updateSyncHistory(historyId, "Failed", 0, 0, 0, 0, 0, 0, 0, errorMessage);
            }
            
            if (lockAcquired) {
                lockManager.releaseLock();
                lockAcquired = false;
            }
        } finally {
            // Ensure lock is released even if something unexpected happens
            if (lockAcquired) {
                logger.warn("Lock was still held in finally block, releasing it");
                lockManager.releaseLock();
            }
            
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.warn("Error closing LDAP connection", e);
                }
            }
        }
    }
    
    /**
     * Fetch users from LDAP server (supports full and incremental sync)
     */
    private List<Map<String, Object>> fetchAllUsersFromLdap(LdapSettings settings, int jobId, String correlationId) throws LDAPException {
        List<Map<String, Object>> users = new ArrayList<>();
        LDAPConnection connection = null;
        
        try {
            // Create connection
            connection = createLdapConnection(settings);
            
            // Build search filter - search for all users (objectClass=person or inetOrgPerson)
            String baseFilter = "(|(objectClass=person)(objectClass=inetOrgPerson)(objectClass=user))";
            String searchFilter = baseFilter;
            
            // If incremental sync is enabled and we have a last sync timestamp, add time filter
            if (settings.isIncrementalSyncEnabled() && settings.getLastSyncTimestamp() != null) {
                try {
                    String ldapTime = convertToLdapGeneralizedTime(settings.getLastSyncTimestamp());
                    searchFilter = "(&" + baseFilter + "(modifyTimestamp>=" + ldapTime + "))";
                    logInfo(jobId, correlationId, String.format("Incremental sync enabled - fetching users modified since %s", settings.getLastSyncTimestamp()));
                } catch (Exception e) {
                    logger.warn("Failed to convert timestamp for incremental sync, falling back to full sync", e);
                    logWarn(jobId, correlationId, "Failed to parse last sync timestamp, performing full sync instead");
                }
            }
            
            // Request all relevant attributes (including modifyTimestamp for incremental sync)
            String[] attributes = {
                "uid", "cn", "sn", "givenName", "mail", "memberOf", "objectClass",
                "department", "departmentName", "description", "title",
                "telephoneNumber", "mobile", "physicalDeliveryOfficeName",
                "modifyTimestamp", "whenChanged", // For incremental sync support
                "userAccountControl", "accountDisabled", "accountExpires" // For disabled user detection
            };
            
            SearchRequest searchRequest = new SearchRequest(
                settings.getUserSearchBase(),
                SearchScope.SUB,
                searchFilter,
                attributes
            );
            
            // Set size limit to a very large value to fetch all users
            // Using 0 means no limit, but some LDAP servers may reject this, so use a very large number
            searchRequest.setSizeLimit(1000000);
            
            logInfo(jobId, correlationId, String.format("Searching LDAP with filter: %s in base: %s", 
                searchFilter, settings.getUserSearchBase()));
            
            SearchResult searchResult = connection.search(searchRequest);
            
            logInfo(jobId, correlationId, String.format("LDAP search returned %d entries", searchResult.getEntryCount()));
            
            for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                try {
                    Map<String, Object> userInfo = extractUserInfo(entry, connection, settings, jobId);
                    if (userInfo != null) {
                        users.add(userInfo);
                    }
                } catch (IllegalArgumentException e) {
                    // Missing required attributes - log the reason
                    logWarn(jobId, correlationId, e.getMessage());
                    usersSkipped.incrementAndGet();
                } catch (Exception e) {
                    logger.warn("Error extracting user info from LDAP entry: {}", entry.getDN(), e);
                    logWarn(jobId, correlationId, String.format("Error processing entry %s: %s", entry.getDN(), e.getMessage()));
                    usersSkipped.incrementAndGet();
                }
            }
            
        } catch (LDAPException e) {
            logger.error("LDAP search error", e);
            
            // Provide clearer error messages for common issues
            String errorMessage;
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                errorMessage = String.format(
                    "The search base '%s' does not exist in the LDAP server. " +
                    "Please verify the 'User Search Base' setting in LDAP configuration. " +
                    "The base DN should be a valid entry in your LDAP directory.",
                    settings.getUserSearchBase()
                );
            } else if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
                errorMessage = "LDAP authentication failed. Please check the bind DN and password in LDAP settings.";
            } else if (e.getResultCode() == ResultCode.CONNECT_ERROR) {
                errorMessage = "Failed to connect to LDAP server. Please check the LDAP URL and ensure the server is reachable.";
            } else {
                errorMessage = "Failed to fetch users from LDAP: " + e.getMessage();
            }
            
            throw new LDAPException(e.getResultCode(), errorMessage, e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        
        return users;
    }
    
    /**
     * Extract user information from LDAP entry
     */
    private Map<String, Object> extractUserInfo(SearchResultEntry entry, LDAPConnection connection, 
                                                 LdapSettings settings, int jobId) {
        Map<String, Object> userInfo = new HashMap<>();
        
        try {
            // Get attributes
            String givenName = getAttributeValue(entry, "givenName");
            String sn = getAttributeValue(entry, "sn");
            String mail = getAttributeValue(entry, "mail");
            String cn = getAttributeValue(entry, "cn");
            String uid = getAttributeValue(entry, "uid");
            
            // Email is the only truly mandatory attribute (used as unique identifier)
            if (mail == null || mail.trim().isEmpty()) {
                // Log available attributes for debugging
                StringBuilder availableAttrs = new StringBuilder();
                for (Attribute attr : entry.getAttributes()) {
                    if (availableAttrs.length() > 0) availableAttrs.append(", ");
                    availableAttrs.append(attr.getName());
                }
                String skipReason = String.format(
                    "Skipped entry %s: missing required 'mail' attribute. Available attributes: %s",
                    entry.getDN(), availableAttrs.toString()
                );
                logger.debug(skipReason);
                // Return null with reason for logging
                throw new IllegalArgumentException(skipReason);
            }
            
            // If givenName or sn are missing, try to use cn or uid as fallback
            if ((givenName == null || givenName.trim().isEmpty()) && 
                (sn == null || sn.trim().isEmpty())) {
                // Try to split cn if available
                if (cn != null && !cn.trim().isEmpty()) {
                    String[] nameParts = cn.trim().split("\\s+", 2);
                    if (nameParts.length >= 2) {
                        givenName = nameParts[0];
                        sn = nameParts[1];
                    } else if (nameParts.length == 1) {
                        // Use cn as givenName, uid as sn if available
                        givenName = nameParts[0];
                        sn = uid != null && !uid.trim().isEmpty() ? uid : cn;
                    } else {
                        // Last resort: use uid or email
                        givenName = uid != null && !uid.trim().isEmpty() ? uid : mail.split("@")[0];
                        sn = mail.split("@")[0];
                    }
                } else if (uid != null && !uid.trim().isEmpty()) {
                    givenName = uid;
                    sn = mail.split("@")[0];
                } else {
                    // Use email username as name
                    String emailUsername = mail.split("@")[0];
                    givenName = emailUsername;
                    sn = emailUsername;
                }
            } else if (givenName == null || givenName.trim().isEmpty()) {
                // Only givenName is missing
                if (cn != null && !cn.trim().isEmpty()) {
                    givenName = cn.split("\\s+")[0];
                } else if (uid != null && !uid.trim().isEmpty()) {
                    givenName = uid;
                } else {
                    givenName = mail.split("@")[0];
                }
            } else if (sn == null || sn.trim().isEmpty()) {
                // Only sn is missing
                if (cn != null && !cn.trim().isEmpty()) {
                    String[] nameParts = cn.trim().split("\\s+");
                    sn = nameParts.length > 1 ? nameParts[nameParts.length - 1] : cn;
                } else if (uid != null && !uid.trim().isEmpty()) {
                    sn = uid;
                } else {
                    sn = mail.split("@")[0];
                }
            }
            
            userInfo.put("givenName", givenName);
            userInfo.put("sn", sn);
            userInfo.put("mail", mail.toLowerCase().trim());
            userInfo.put("dn", entry.getDN());
            
            // Optional attributes
            userInfo.put("uid", getAttributeValue(entry, "uid"));
            userInfo.put("cn", getAttributeValue(entry, "cn"));
            userInfo.put("description", getAttributeValue(entry, "description"));
            userInfo.put("title", getAttributeValue(entry, "title"));
            userInfo.put("department", getAttributeValue(entry, "department"));
            userInfo.put("departmentName", getAttributeValue(entry, "departmentName"));
            userInfo.put("telephoneNumber", getAttributeValue(entry, "telephoneNumber"));
            userInfo.put("mobile", getAttributeValue(entry, "mobile"));
            userInfo.put("physicalDeliveryOfficeName", getAttributeValue(entry, "physicalDeliveryOfficeName"));
            
            // Check if account is disabled
            boolean accountDisabled = isAccountDisabled(entry);
            userInfo.put("accountDisabled", accountDisabled);
            
            // Get user groups (names for role determination)
            try {
                List<String> groups = ldapAuthService.getUserGroups(connection, entry.getDN());
                userInfo.put("groups", groups);
                String role = determineRole(groups);
                userInfo.put("role", role);
            } catch (Exception e) {
                logger.debug("Error getting user groups: {}", e.getMessage());
                userInfo.put("groups", new ArrayList<>());
                userInfo.put("role", "User"); // Default role
            }
            
            // Get user group DNs (for OrgUnit mapping)
            try {
                List<String> groupDns = ldapAuthService.getUserGroupDns(connection, entry.getDN());
                userInfo.put("groupDns", groupDns);
            } catch (Exception e) {
                logger.debug("Error getting user group DNs: {}", e.getMessage());
                userInfo.put("groupDns", new ArrayList<>());
            }
            
        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException (e.g., missing mail attribute) 
            // so it can be properly handled upstream as a skipped entry
            throw e;
        } catch (Exception e) {
            logger.error("Error extracting user info", e);
            return null;
        }
        
        return userInfo;
    }
    
    /**
     * Get attribute value from LDAP entry
     */
    private String getAttributeValue(SearchResultEntry entry, String attributeName) {
        Attribute attr = entry.getAttribute(attributeName);
        if (attr != null && attr.hasValue()) {
            return attr.getValue();
        }
        return null;
    }
    
    /**
     * Check if account is disabled based on LDAP attributes
     * Supports both Active Directory (userAccountControl) and standard LDAP (accountDisabled)
     * 
     * @param entry LDAP entry
     * @return true if account is disabled, false otherwise
     */
    private boolean isAccountDisabled(SearchResultEntry entry) {
        // Check accountDisabled attribute (standard LDAP)
        String accountDisabled = getAttributeValue(entry, "accountDisabled");
        if (accountDisabled != null) {
            return Boolean.parseBoolean(accountDisabled) || "TRUE".equalsIgnoreCase(accountDisabled);
        }
        
        // Check userAccountControl (Active Directory - bitmask)
        // Bit 2 (0x0002) = ACCOUNTDISABLE
        String userAccountControl = getAttributeValue(entry, "userAccountControl");
        if (userAccountControl != null) {
            try {
                int uac = Integer.parseInt(userAccountControl);
                // Check if ACCOUNTDISABLE bit (0x0002) is set
                return (uac & 0x0002) != 0;
            } catch (NumberFormatException e) {
                logger.debug("Invalid userAccountControl value: {}", userAccountControl);
            }
        }
        
        // Check accountExpires (Active Directory)
        // 0 or 9223372036854775807 (max) means never expires
        // Any other value means account is expired/disabled
        String accountExpires = getAttributeValue(entry, "accountExpires");
        if (accountExpires != null) {
            try {
                long expires = Long.parseLong(accountExpires);
                // 0 or max value means never expires, anything else means expired
                if (expires != 0 && expires != 9223372036854775807L) {
                    long currentTime = System.currentTimeMillis() / 10000; // Convert to 100-nanosecond intervals
                    if (expires < currentTime) {
                        return true; // Account has expired
                    }
                }
            } catch (NumberFormatException e) {
                logger.debug("Invalid accountExpires value: {}", accountExpires);
            }
        }
        
        return false; // Default: account is enabled
    }
    
    /**
     * Determine role from LDAP groups
     */
    private String determineRole(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return "User";
        }
        
        // Check for admin/superadmin groups (case-insensitive)
        for (String group : groups) {
            String groupLower = group.toLowerCase();
            if (groupLower.contains("admin") || groupLower.contains("superadmin")) {
                return "SuperAdmin";
            }
        }
        
        return "User";
    }
    
    /**
     * Create LDAP connection with bind credentials
     */
    private LDAPConnection createLdapConnection(LdapSettings settings) throws LDAPException {
        LDAPConnection connection;
        boolean useSsl = settings.isUseSsl();
        
        try {
            String host = settings.getHost();
            int port = settings.getPort();
            
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
            
            connection.connect(host, port, settings.getConnectionTimeout());
            
            // Bind with credentials if configured
            if (settings.getBindDn() != null && !settings.getBindDn().trim().isEmpty() &&
                settings.getBindPassword() != null && !settings.getBindPassword().trim().isEmpty()) {
                
                // Use bind password as plain text (no encryption)
                String bindPassword = settings.getBindPassword();
                
                // Remove ENC: prefix if present (for backward compatibility with old encrypted data)
                if (bindPassword.startsWith("ENC:")) {
                    logger.warn("Found old encrypted password format. Please re-enter the password in LDAP settings.");
                    throw new LDAPException(ResultCode.CONNECT_ERROR, 
                        "Password is in old encrypted format. Please re-enter the password in LDAP settings.");
                }
                
                BindRequest bindRequest = new SimpleBindRequest(settings.getBindDn(), bindPassword);
                BindResult bindResult = connection.bind(bindRequest);
                
                if (bindResult.getResultCode() != ResultCode.SUCCESS) {
                    throw new LDAPException(bindResult.getResultCode(), 
                        "LDAP bind failed: " + bindResult.getResultString());
                }
            }
            
        } catch (LDAPException e) {
            throw e;
        } catch (Exception e) {
            throw new LDAPException(ResultCode.CONNECT_ERROR, 
                "Failed to connect to LDAP server: " + e.getMessage(), e);
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
                java.io.File truststoreFile = new java.io.File(settings.getTruststorePath());
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
     * Fetch existing users from database (batch)
     */
    private Map<String, Map<String, Object>> fetchExistingUsers(List<Map<String, Object>> ldapUsers, int jobId, String correlationId) 
            throws SQLException {
        Map<String, Map<String, Object>> existingUsers = new HashMap<>();
        
        // Extract emails from LDAP users
        Set<String> emails = new HashSet<>();
        for (Map<String, Object> ldapUser : ldapUsers) {
            String email = (String) ldapUser.get("mail");
            if (email != null && !email.trim().isEmpty()) {
                emails.add(email.toLowerCase().trim());
            }
        }
        
        if (emails.isEmpty()) {
            return existingUsers;
        }
        
        // Batch fetch in chunks
        List<String> emailList = new ArrayList<>(emails);
        int totalBatches = (int) Math.ceil((double) emailList.size() / BATCH_SIZE);
        
        for (int i = 0; i < emailList.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, emailList.size());
            List<String> batch = emailList.subList(i, endIndex);
            int batchNum = (i / BATCH_SIZE) + 1;
            
            logInfo(jobId, correlationId, String.format("Fetching batch %d/%d (%d users)...", batchNum, totalBatches, batch.size()));
            
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            String sql = String.format(
                "SELECT p.ID, p.First_Name, p.Last_Name, p.Email, p.Org_Unit_ID, p.Function_Name, p.Function_Description, " +
                "p.Description, p.System_Role, p.status_id, ou.Name AS Org_Unit_Name, " +
                "pd.office_telephone, pd.mobile_telephone, pd.office_location " +
                "FROM people p " +
                "LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID " +
                "LEFT JOIN people_details pd ON p.ip_details = pd.id " +
                "WHERE p.Email IS NOT NULL AND LOWER(p.Email) IN (%s)",
                placeholders
            );
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                for (int j = 0; j < batch.size(); j++) {
                    pstmt.setString(j + 1, batch.get(j));
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", rs.getInt("ID"));
                        user.put("First_Name", rs.getString("First_Name"));
                        user.put("Last_Name", rs.getString("Last_Name"));
                        String dbEmail = rs.getString("Email");
                        user.put("Email", dbEmail);
                        user.put("Org_Unit_ID", rs.getObject("Org_Unit_ID"));
                        user.put("Org_Unit_Name", rs.getString("Org_Unit_Name")); // Store org unit name for comparison
                        user.put("Function_Name", rs.getString("Function_Name"));
                        user.put("Function_Description", rs.getString("Function_Description"));
                        user.put("Description", rs.getString("Description"));
                        user.put("System_Role", rs.getObject("System_Role"));
                        user.put("status_id", rs.getInt("status_id"));
                        // People details for comparison
                        user.put("office_telephone", rs.getString("office_telephone"));
                        user.put("mobile_telephone", rs.getString("mobile_telephone"));
                        user.put("office_location", rs.getString("office_location"));
                        
                        String emailKey = (dbEmail != null) ? dbEmail.toLowerCase().trim() : null;
                        if (emailKey != null && !emailKey.isEmpty()) {
                            existingUsers.put(emailKey, user);
                            logger.debug("Found existing user in DB: {} (ID: {})", emailKey, user.get("id"));
                        }
                    }
                }
            }
        }
        
        return existingUsers;
    }
    
    /**
     * Check if user attributes have changed
     * Compares all fields that can be updated from LDAP
     * Compares by name (not ID) for OrgUnit to handle cases where IDs differ but names match
     */
    private boolean hasUserChanged(Map<String, Object> ldapUser, Map<String, Object> dbUser) {
        boolean hasChanges = false;
        
        // Compare first name
        String ldapGivenName = normalizeString((String) ldapUser.get("givenName"));
        String dbFirstName = normalizeString((String) dbUser.get("First_Name"));
        if (!Objects.equals(ldapGivenName, dbFirstName)) {
            logger.debug("First name changed: LDAP='{}' vs DB='{}'", ldapGivenName, dbFirstName);
            hasChanges = true;
        }
        
        // Compare last name
        String ldapSn = normalizeString((String) ldapUser.get("sn"));
        String dbLastName = normalizeString((String) dbUser.get("Last_Name"));
        if (!Objects.equals(ldapSn, dbLastName)) {
            logger.debug("Last name changed: LDAP='{}' vs DB='{}'", ldapSn, dbLastName);
            hasChanges = true;
        }
        
        // Compare OrgUnit by name (not ID) - LDAP department name vs DB org unit name
        // This handles cases where:
        // 1. LDAP has department but DB has no orgunit (null) - should update
        // 2. DB has orgunit but LDAP has no department (null) - should update
        // 3. Both have values but different - should update
        // 4. Both are null - no update needed
        String ldapDepartment = (String) ldapUser.getOrDefault("department", 
            ldapUser.getOrDefault("departmentName", null));
        String dbOrgUnitName = (String) dbUser.get("Org_Unit_Name");
        String ldapDeptNormalized = normalizeString(ldapDepartment);
        String dbOrgUnitNormalized = normalizeString(dbOrgUnitName);
        
        // Check if orgunit changed (handles null cases correctly)
        if (!Objects.equals(ldapDeptNormalized, dbOrgUnitNormalized)) {
            logger.debug("OrgUnit changed: LDAP='{}' vs DB='{}' (LDAP has dept: {}, DB has orgunit: {})", 
                ldapDeptNormalized, dbOrgUnitNormalized, 
                ldapDepartment != null, dbOrgUnitName != null);
            hasChanges = true;
        }
        
        // Compare Function_Name (title)
        String ldapTitle = normalizeString((String) ldapUser.get("title"));
        String dbFunctionName = normalizeString((String) dbUser.get("Function_Name"));
        if (!Objects.equals(ldapTitle, dbFunctionName)) {
            logger.debug("Function name changed: LDAP='{}' vs DB='{}'", ldapTitle, dbFunctionName);
            hasChanges = true;
        }
        
        // Compare Description
        String ldapDescription = normalizeString((String) ldapUser.get("description"));
        String dbDescription = normalizeString((String) dbUser.get("Description"));
        // Default description for LDAP users is "LDAP User"
        if (ldapDescription == null) {
            ldapDescription = "LDAP User";
        }
        if (!Objects.equals(ldapDescription, dbDescription)) {
            logger.debug("Description changed: LDAP='{}' vs DB='{}'", ldapDescription, dbDescription);
            hasChanges = true;
        }
        
        // Compare System_Role (role from LDAP groups)
        // Get role from LDAP (default to "User" if not found)
        String ldapRole = (String) ldapUser.getOrDefault("role", "User");
        Integer ldapRoleId = getRoleIdFromName(ldapRole);
        Integer dbSystemRole = (Integer) dbUser.get("System_Role");
        
        // Compare role IDs - if LDAP role ID differs from DB role, update is needed
        if (!Objects.equals(ldapRoleId, dbSystemRole)) {
            logger.debug("System role changed: LDAP='{}' (ID: {}) vs DB (ID: {})", ldapRole, ldapRoleId, dbSystemRole);
            hasChanges = true;
        }
        
        // Compare office telephone
        String ldapTelephone = normalizeString((String) ldapUser.get("telephoneNumber"));
        String dbTelephone = normalizeString((String) dbUser.get("office_telephone"));
        if (!Objects.equals(ldapTelephone, dbTelephone)) {
            logger.debug("Office telephone changed: LDAP='{}' vs DB='{}'", ldapTelephone, dbTelephone);
            hasChanges = true;
        }
        
        // Compare mobile telephone
        String ldapMobile = normalizeString((String) ldapUser.get("mobile"));
        String dbMobile = normalizeString((String) dbUser.get("mobile_telephone"));
        if (!Objects.equals(ldapMobile, dbMobile)) {
            logger.debug("Mobile telephone changed: LDAP='{}' vs DB='{}'", ldapMobile, dbMobile);
            hasChanges = true;
        }
        
        // Compare office location
        String ldapLocation = normalizeString((String) ldapUser.get("physicalDeliveryOfficeName"));
        String dbLocation = normalizeString((String) dbUser.get("office_location"));
        if (!Objects.equals(ldapLocation, dbLocation)) {
            logger.debug("Office location changed: LDAP='{}' vs DB='{}'", ldapLocation, dbLocation);
            hasChanges = true;
        }
        
        if (!hasChanges) {
            logger.debug("No changes detected for user");
        }
        return hasChanges;
    }
    
    /**
     * Normalize string for comparison (trim and convert null/empty to null)
     */
    private String normalizeString(String str) {
        if (str == null) return null;
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    
    /**
     * Create users in batch
     */
    private void createUsersBatch(List<Map<String, Object>> users, int userId, int jobId, String correlationId) {
        int totalBatches = (int) Math.ceil((double) users.size() / BATCH_SIZE);
        
        for (int i = 0; i < users.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, users.size());
            List<Map<String, Object>> batch = users.subList(i, endIndex);
            int batchNum = (i / BATCH_SIZE) + 1;
            
            logInfo(jobId, correlationId, String.format("Processing batch %d/%d (%d users)...", batchNum, totalBatches, batch.size()));
            
            for (Map<String, Object> ldapUser : batch) {
                try {
                    Map<String, Object> personData = mapLdapUserToPerson(ldapUser, userId, jobId);
                    peopleService.createPerson(personData, userId);
                    usersCreated.incrementAndGet();
                    
                    int progress = 10 + (int) ((usersCreated.get() / (double) users.size()) * 40);
                    updateProgress(jobId, progress, String.format("Created user: %s", personData.get("email")));
                    
                } catch (Exception e) {
                    String userEmail = (String) ldapUser.get("mail");
                    String errorMessage = e.getMessage();
                    
                    // Check if error is related to missing orgunit
                    if (errorMessage != null && errorMessage.contains("orgunit")) {
                        String skipMessage = String.format("Skipped user %s: User does not have an orgunit in our system", 
                            userEmail != null ? userEmail : "unknown");
                        logger.warn("Skipped user {}: missing orgunit", userEmail);
                        logWarn(jobId, correlationId, skipMessage);
                    } else {
                        logger.error("Error creating user: {}", userEmail, e);
                        logWarn(jobId, correlationId, String.format("Skipped user %s: %s", userEmail, errorMessage));
                    }
                    usersSkipped.incrementAndGet();
                }
            }
        }
    }
    
    /**
     * Update users in batch
     */
    private void updateUsersBatch(List<Map<String, Object>> users, int userId, int jobId, String correlationId) {
        int totalBatches = (int) Math.ceil((double) users.size() / BATCH_SIZE);
        
        for (int i = 0; i < users.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, users.size());
            List<Map<String, Object>> batch = users.subList(i, endIndex);
            int batchNum = (i / BATCH_SIZE) + 1;
            
            logInfo(jobId, correlationId, String.format("Processing batch %d/%d (%d users)...", batchNum, totalBatches, batch.size()));
            
            for (Map<String, Object> ldapUser : batch) {
                try {
                    Map<String, Object> personData = mapLdapUserToPerson(ldapUser, userId, jobId);
                    personData.put("id", ldapUser.get("id"));
                    peopleService.updatePerson(personData, userId);
                    usersUpdated.incrementAndGet();
                    
                    int progress = 50 + (int) ((usersUpdated.get() / (double) users.size()) * 30);
                    updateProgress(jobId, progress, String.format("Updated user: %s", personData.get("email")));
                    
                } catch (Exception e) {
                    String userEmail = (String) ldapUser.get("mail");
                    String errorMessage = e.getMessage();
                    
                    // Check if error is related to missing orgunit
                    if (errorMessage != null && errorMessage.contains("orgunit")) {
                        String skipMessage = String.format("Skipped user %s: User does not have an orgunit in our system", 
                            userEmail != null ? userEmail : "unknown");
                        logger.warn("Skipped user {}: missing orgunit", userEmail);
                        logWarn(jobId, correlationId, skipMessage);
                    } else {
                        logger.error("Error updating user: {}", userEmail, e);
                        logWarn(jobId, correlationId, String.format("Skipped user %s: %s", userEmail, errorMessage));
                    }
                    usersSkipped.incrementAndGet();
                }
            }
        }
    }
    
    /**
     * Map LDAP user to Person data structure
     */
    private Map<String, Object> mapLdapUserToPerson(Map<String, Object> ldapUser, int userId, int jobId) 
            throws SQLException {
        Map<String, Object> personData = new HashMap<>();
        
        // Mandatory fields - use snake_case keys as expected by PeopleService
        personData.put("first_name", ldapUser.get("givenName"));
        personData.put("last_name", ldapUser.get("sn"));
        personData.put("email", ldapUser.get("mail"));
        personData.put("password", LDAP_USER_PASSWORD); // Special password for LDAP users - requires LDAP authentication
        
        // Optional fields
        personData.put("description", ldapUser.getOrDefault("description", "LDAP User"));
        personData.put("function_name", ldapUser.getOrDefault("title", ""));
        personData.put("function_description", "");
        
        // Org Unit - Resolve from LDAP Group DNs (BUDG-style)
        @SuppressWarnings("unchecked")
        List<String> groupDns = (List<String>) ldapUser.get("groupDns");
        Integer resolvedOrgUnitId = null;
        
        if (groupDns != null && !groupDns.isEmpty()) {
            resolvedOrgUnitId = ldapOrgUnitMappingService.resolveOrgUnitFromGroups(groupDns);
            if (resolvedOrgUnitId != null) {
                personData.put("org_unit_id", resolvedOrgUnitId);
                logger.debug("Resolved OrgUnit ID {} from {} group DNs for user: {}", 
                    resolvedOrgUnitId, groupDns.size(), ldapUser.get("mail"));
            } else {
                // User does not have an orgunit mapped in our system - throw exception to skip this user
                String userEmail = (String) ldapUser.get("mail");
                String errorMessage = String.format(
                    "User does not have an orgunit mapped in our system. User: %s, Group DNs: %s", 
                    userEmail != null ? userEmail : "unknown", groupDns
                );
                logger.warn("No OrgUnit mapping found for {} group DNs (user: {})", 
                    groupDns.size(), userEmail);
                throw new SQLException(errorMessage);
            }
        } else {
            // User has no group DNs - throw exception to skip this user
            String userEmail = (String) ldapUser.get("mail");
            String errorMessage = String.format(
                "User does not have an orgunit mapped in our system. User: %s has no group DNs", 
                userEmail != null ? userEmail : "unknown"
            );
            logger.warn("No group DNs found for user: {}, cannot determine orgunit", userEmail);
            throw new SQLException(errorMessage);
        }
        
        // Default values
        personData.put("status_id", 1); // Active
        personData.put("system_role", getRoleIdFromName((String) ldapUser.getOrDefault("role", "User")));
        
        // People details - add directly to personData (not nested)
        // Use correct keys as expected by PeopleService
        personData.put("lifecycle_id", 1); // Working (default)
        personData.put("employment_type_id", 1); // Internal (default)
        personData.put("office_phone", ldapUser.get("telephoneNumber"));
        personData.put("mobile_phone", ldapUser.get("mobile"));
        personData.put("office_location", ldapUser.get("physicalDeliveryOfficeName"));
        
        // LDAP-specific fields
        personData.put("external_id", ldapUser.get("dn")); // Store LDAP DN
        personData.put("auth_source", "LDAP"); // Mark as LDAP user
        personData.put("last_synced_at", new java.sql.Timestamp(System.currentTimeMillis())); // Current sync time
        
        return personData;
    }
    
    /**
     * Get or create org unit
     * Uses case-insensitive search to find existing org units
     */
    private Integer getOrCreateOrgUnit(String departmentName, int userId) throws SQLException {
        if (departmentName == null || departmentName.trim().isEmpty()) {
            return null;
        }
        
        String name = departmentName.trim();
        
        // First try exact match (case-sensitive)
        OrgUnit existing = orgUnitDAO.getOrgUnitByName(name);
        if (existing != null) {
            logger.debug("Found org unit by exact match: {} (ID: {})", name, existing.getId());
            return existing.getId();
        }
        
        // If not found, try case-insensitive search
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT * FROM org_unit WHERE LOWER(Name) = LOWER(?) LIMIT 1")) {
            
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int orgUnitId = rs.getInt("ID");
                    String actualName = rs.getString("Name");
                    logger.debug("Found org unit by case-insensitive match: '{}' matches '{}' (ID: {})", 
                        name, actualName, orgUnitId);
                    return orgUnitId;
                }
            }
        }
        
        // If still not found, create new org unit
        logger.info("Org unit '{}' not found, creating new one", name);
        OrgUnit orgUnit = new OrgUnit();
        orgUnit.setName(name);
        orgUnit.setDescription("Created from LDAP sync");
        orgUnit.setStatusId(1); // Active
        orgUnit.setParentId(null);
        
        int orgUnitId = orgUnitDAO.createOrgUnit(orgUnit, userId);
        orgUnitsCreated.incrementAndGet();
        logger.info("Created new org unit: {} (ID: {})", name, orgUnitId);
        
        return orgUnitId;
    }
    
    /**
     * Process org units from LDAP users
     */
    private void processOrgUnits(List<Map<String, Object>> ldapUsers, int userId, int jobId, String correlationId) {
        Set<String> departments = new HashSet<>();
        
        for (Map<String, Object> ldapUser : ldapUsers) {
            String department = (String) ldapUser.getOrDefault("department", 
                ldapUser.getOrDefault("departmentName", null));
            if (department != null && !department.trim().isEmpty()) {
                departments.add(department.trim());
            }
        }
        
        logInfo(jobId, correlationId, String.format("Processing %d unique departments...", departments.size()));
        
        for (String department : departments) {
            try {
                getOrCreateOrgUnit(department, userId);
            } catch (Exception e) {
                logger.error("Error processing org unit: {}", department, e);
                logWarn(jobId, correlationId, String.format("Skipped org unit %s: %s", department, e.getMessage()));
            }
        }
    }
    
    /**
     * Get role ID from role name
     */
    private Integer getRoleIdFromName(String roleName) {
        if (roleName == null) {
            roleName = "User";
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT id FROM role WHERE primaryname = ? LIMIT 1")) {
            
            pstmt.setString(1, roleName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting role ID for: {}", roleName, e);
        }
        
        // Default to User role (ID 2, adjust as needed)
        return 2;
    }
    
    /**
     * Cleanup temporary directory
     */
    private void cleanupTempDirectory() {
        try {
            Path tempDir = Paths.get(TEMP_DIR);
            if (!Files.exists(tempDir)) {
                return;
            }
            
            File[] files = tempDir.toFile().listFiles();
            if (files == null) {
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            int deletedCount = 0;
            
            for (File file : files) {
                if (file.isFile()) {
                    long fileAge = currentTime - file.lastModified();
                    if (fileAge > TEMP_CLEANUP_EXPIRATION_MS) {
                        if (file.delete()) {
                            deletedCount++;
                        }
                    }
                }
            }
            
            logger.info("Cleaned up {} expired files from temp directory", deletedCount);
            
        } catch (Exception e) {
            logger.warn("Error cleaning up temp directory: {}", e.getMessage());
        }
    }
    
    /**
     * Log info message and broadcast (BUDG format)
     */
    private void logInfo(int jobId, String correlationId, String message) {
        logger.info("[LDAP-SYNC] correlationId={} jobId={} {}", 
            correlationId != null ? correlationId : "N/A", jobId, message);
        broadcaster.broadcast(jobId, "Running", 0, message);
    }
    
    /**
     * Log info message and broadcast (backward compatibility - uses correlation ID from MDC)
     */
    @SuppressWarnings("unused")
    private void logInfo(int jobId, String message) {
        String correlationId = MDC.get("correlation_id");
        logInfo(jobId, correlationId, message);
    }
    
    /**
     * Log warning message and broadcast (BUDG format)
     */
    private void logWarn(int jobId, String correlationId, String message) {
        logger.warn("[LDAP-SYNC] correlationId={} jobId={} WARNING: {}", 
            correlationId != null ? correlationId : "N/A", jobId, message);
        broadcaster.broadcast(jobId, "Running", 0, "WARNING: " + message);
    }
    
    /**
     * Log warning message and broadcast (backward compatibility)
     */
    @SuppressWarnings("unused")
    private void logWarn(int jobId, String message) {
        String correlationId = MDC.get("correlation_id");
        logWarn(jobId, correlationId, message);
    }
    
    /**
     * Log error message and broadcast (BUDG format)
     */
    private void logError(int jobId, String correlationId, String message) {
        logger.error("[LDAP-SYNC] correlationId={} jobId={} ERROR: {}", 
            correlationId != null ? correlationId : "N/A", jobId, message);
        broadcaster.broadcast(jobId, "Failed", 0, "ERROR: " + message);
    }
    
    /**
     * Log error message and broadcast (backward compatibility)
     */
    @SuppressWarnings("unused")
    private void logError(int jobId, String message) {
        String correlationId = MDC.get("correlation_id");
        logError(jobId, correlationId, message);
    }
    
    /**
     * Update progress
     */
    private void updateProgress(int jobId, int progress, String message) {
        try {
            jobDAO.createJobProgress(jobId, progress, "Running", message);
            broadcaster.broadcast(jobId, "Running", progress, message);
        } catch (SQLException e) {
            logger.error("Error updating job progress", e);
        }
    }
    
    /**
     * Update job status
     */
    private void updateJobStatus(int jobId, String status, boolean setCompletedDate) {
        try {
            jobDAO.updateJobStatus(jobId, status, setCompletedDate);
        } catch (SQLException e) {
            logger.error("Error updating job status", e);
        }
    }
    
    /**
     * Handle users that are missing from LDAP or disabled in LDAP
     * Disables them in the local database if autoDisableMissingUsers is enabled
     */
    private void handleMissingLdapUsers(List<Map<String, Object>> ldapUsers, int userId, int jobId, String correlationId) {
        try {
            // Check if auto-disable is enabled
            Map<String, Object> settings = new com.example.budg_v2.dao.SystemSettingsDAO().getSettingsByGroup("LDAP Settings");
            boolean autoDisable = getBooleanValue(settings, "autoDisableMissingUsers", false);
            
            if (!autoDisable) {
                logger.debug("Auto-disable missing users is disabled - skipping");
                return;
            }
            
            // Collect all external_ids (LDAP DNs) from current sync
            Set<String> ldapExternalIds = new HashSet<>();
            Set<String> disabledLdapExternalIds = new HashSet<>();
            
            for (Map<String, Object> ldapUser : ldapUsers) {
                String externalId = (String) ldapUser.get("dn");
                if (externalId != null && !externalId.trim().isEmpty()) {
                    ldapExternalIds.add(externalId);
                    
                    // Check if user is disabled in LDAP
                    Boolean accountDisabled = (Boolean) ldapUser.get("accountDisabled");
                    if (accountDisabled != null && accountDisabled) {
                        disabledLdapExternalIds.add(externalId);
                    }
                }
            }
            
            logInfo(jobId, correlationId, String.format("Checking for missing/disabled LDAP users (found %d in LDAP, %d disabled)", 
                ldapExternalIds.size(), disabledLdapExternalIds.size()));
            
            // Find users in DB with auth_source='LDAP' that are not in current LDAP result
            String sql = "SELECT p.ID, p.Email, p.external_id, p.is_locked " +
                        "FROM people p " +
                        "WHERE p.auth_source = 'LDAP' " +
                        "AND p.external_id IS NOT NULL " +
                        "AND p.Deleted_date IS NULL " +
                        "AND (p.external_id NOT IN (" + 
                        String.join(",", Collections.nCopies(ldapExternalIds.size(), "?")) + ") " +
                        "OR p.external_id IN (" + 
                        String.join(",", Collections.nCopies(disabledLdapExternalIds.size(), "?")) + "))";
            
            List<Integer> usersToDisable = new ArrayList<>();
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                int paramIndex = 1;
                // Set parameters for external_ids NOT IN clause
                for (String externalId : ldapExternalIds) {
                    pstmt.setString(paramIndex++, externalId);
                }
                // Set parameters for disabled external_ids IN clause
                for (String externalId : disabledLdapExternalIds) {
                    pstmt.setString(paramIndex++, externalId);
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int userIdToDisable = rs.getInt("ID");
                        String email = rs.getString("Email");
                        String externalId = rs.getString("external_id");
                        int isLocked = rs.getInt("is_locked");
                        
                        // Only disable if not already locked
                        if (isLocked == 0) {
                            usersToDisable.add(userIdToDisable);
                            logger.debug("User {} ({}) will be disabled - missing from LDAP or disabled in LDAP", email, externalId);
                        }
                    }
                }
            }
            
            if (usersToDisable.isEmpty()) {
                logInfo(jobId, correlationId, "No missing/disabled LDAP users found to disable");
                return;
            }
            
            // Disable users in batch
            logInfo(jobId, correlationId, String.format("Disabling %d users missing from LDAP or disabled in LDAP...", usersToDisable.size()));
            
            String placeholders = String.join(",", Collections.nCopies(usersToDisable.size(), "?"));
            String disableSql = String.format(
                "UPDATE people " +
                "SET is_locked = 1, " +
                "    locked_date = NOW(), " +
                "    lock_reason = 'User missing from LDAP or disabled in LDAP', " +
                "    lastupdateuser_id = ? " +
                "WHERE ID IN (%s) " +
                "AND Deleted_date IS NULL",
                placeholders
            );
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(disableSql)) {
                
                pstmt.setInt(1, userId);
                for (int i = 0; i < usersToDisable.size(); i++) {
                    pstmt.setInt(i + 2, usersToDisable.get(i));
                }
                
                int rowsUpdated = pstmt.executeUpdate();
                logInfo(jobId, correlationId, String.format("Disabled %d users missing from LDAP or disabled in LDAP", rowsUpdated));
                logger.info("Disabled {} LDAP users that are missing or disabled in LDAP", rowsUpdated);
            }
            
        } catch (Exception e) {
            logger.error("Error handling missing LDAP users", e);
            logWarn(jobId, correlationId, "Error handling missing LDAP users: " + e.getMessage());
            // Don't fail the entire sync if this step fails
        }
    }
    
    /**
     * Create sync history record
     */
    private int createSyncHistory(int jobId, int userId) {
        try {
            String sql = "INSERT INTO ldap_sync_history " +
                        "(job_id, started_at, status, created_by) " +
                        "VALUES (?, NOW(), 'Running', ?)";
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                pstmt.setInt(1, jobId);
                pstmt.setInt(2, userId);
                
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error creating sync history record", e);
        }
        return 0;
    }
    
    /**
     * Update sync history record with completion status and statistics
     */
    private void updateSyncHistory(int historyId, String status, int usersFetched, int usersAdded, 
                                   int usersUpdated, int usersSkipped, int usersDisabled,
                                   int orgUnitsCreated, int orgUnitsUpdated) {
        updateSyncHistory(historyId, status, usersFetched, usersAdded, usersUpdated, usersSkipped, 
            usersDisabled, orgUnitsCreated, orgUnitsUpdated, null);
    }
    
    /**
     * Update sync history record with completion status, statistics, and optional error message
     */
    private void updateSyncHistory(int historyId, String status, int usersFetched, int usersAdded, 
                                   int usersUpdated, int usersSkipped, int usersDisabled,
                                   int orgUnitsCreated, int orgUnitsUpdated, String errorMessage) {
        if (historyId <= 0) {
            return;
        }
        
        try {
            String sql = "UPDATE ldap_sync_history SET " +
                        "completed_at = NOW(), " +
                        "status = ?, " +
                        "users_fetched = ?, " +
                        "users_added = ?, " +
                        "users_updated = ?, " +
                        "users_skipped = ?, " +
                        "users_disabled = ?, " +
                        "org_units_created = ?, " +
                        "org_units_updated = ?, " +
                        "error_message = ? " +
                        "WHERE id = ?";
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, status);
                pstmt.setInt(2, usersFetched);
                pstmt.setInt(3, usersAdded);
                pstmt.setInt(4, usersUpdated);
                pstmt.setInt(5, usersSkipped);
                pstmt.setInt(6, usersDisabled);
                pstmt.setInt(7, orgUnitsCreated);
                pstmt.setInt(8, orgUnitsUpdated);
                pstmt.setString(9, errorMessage);
                pstmt.setInt(10, historyId);
                
                pstmt.executeUpdate();
                logger.debug("Sync history updated: historyId={}, status={}", historyId, status);
            }
        } catch (SQLException e) {
            logger.error("Error updating sync history record", e);
        }
    }
    
    /**
     * Helper method to get boolean value from settings map
     */
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
    
    /**
     * Convert ISO 8601 timestamp to LDAP generalized time format
     * LDAP generalized time format: YYYYMMDDHHmmssZ (e.g., 20240115103000Z)
     * 
     * @param iso8601Timestamp ISO 8601 format timestamp (e.g., "2024-01-15T10:30:00Z")
     * @return LDAP generalized time string (e.g., "20240115103000Z")
     */
    private String convertToLdapGeneralizedTime(String iso8601Timestamp) {
        if (iso8601Timestamp == null || iso8601Timestamp.trim().isEmpty()) {
            throw new IllegalArgumentException("Timestamp cannot be null or empty");
        }
        
        try {
            // Parse ISO 8601 timestamp
            Instant instant = Instant.parse(iso8601Timestamp);
            
            // Format as LDAP generalized time: YYYYMMDDHHmmssZ
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .withZone(ZoneOffset.UTC);
            String ldapTime = formatter.format(instant) + "Z";
            
            return ldapTime;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO 8601 timestamp format: " + iso8601Timestamp, e);
        }
    }
}

