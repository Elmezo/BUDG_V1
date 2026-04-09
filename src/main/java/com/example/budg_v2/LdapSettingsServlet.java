package com.example.budg_v2;

import com.example.budg_v2.model.LdapSettings;
import com.example.budg_v2.service.LdapSettingsService;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.EncryptionService;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.exception.LdapConnectionException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Servlet for LDAP settings management
 * 
 * Endpoints:
 * - GET /api/admin/ldap/settings - Get LDAP settings (password masked)
 * - PUT /api/admin/ldap/settings - Save LDAP settings (password encrypted)
 * - POST /api/admin/ldap/settings/test - Test LDAP connection (ignores ldapEnabled)
 * - POST /api/admin/ldap/settings/refresh-cache - Refresh cache manually
 * 
 * Security: SuperAdmin only
 */
@WebServlet("/api/admin/ldap/settings/*")
public class LdapSettingsServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(LdapSettingsServlet.class);
    private final Gson gson = new Gson();
    private final LdapSettingsService ldapSettingsService;
    
    public LdapSettingsServlet() {
        this.ldapSettingsService = new LdapSettingsService();
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        
        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Check SuperAdmin access
        if (!isSuperAdmin(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: SuperAdmin access required");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        try {
            // GET /api/admin/ldap/settings - Get settings
            LdapSettings settings = ldapSettingsService.getLdapSettings();
            
            // Create response with masked password
            JsonObject responseJson = settingsToJson(settings, true);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            logger.error("Error getting LDAP settings", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to get LDAP settings: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        
        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Check SuperAdmin access
        if (!isSuperAdmin(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: SuperAdmin access required");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        try {
            // Read request body
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            JsonObject requestData = gson.fromJson(jsonBody.toString(), JsonObject.class);
            
            if (requestData == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid request body");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Get current settings to preserve password if not provided
            LdapSettings currentSettings = ldapSettingsService.getLdapSettings();
            LdapSettings newSettings = jsonToSettings(requestData, currentSettings);
            
            // Validate settings
            if (!newSettings.isValid() && newSettings.isLdapEnabled()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid LDAP settings: required fields are missing");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Save settings
            ldapSettingsService.saveLdapSettings(newSettings);
            
            // Return updated settings with masked password
            JsonObject responseJson = settingsToJson(newSettings, true);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (SQLException e) {
            logger.error("Error saving LDAP settings", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to save LDAP settings: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Unexpected error saving LDAP settings", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        
        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Check SuperAdmin access
        if (!isSuperAdmin(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: SuperAdmin access required");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid endpoint");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        try {
            // Remove leading slash
            String action = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            
            if ("test".equals(action)) {
                // POST /api/admin/ldap/settings/test - Test connection
                handleTestConnection(request, response);
            } else if ("refresh-cache".equals(action)) {
                // POST /api/admin/ldap/settings/refresh-cache - Refresh cache
                handleRefreshCache(response);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unknown action: " + action);
                response.getWriter().write(gson.toJson(error));
            }
            
        } catch (Exception e) {
            logger.error("Error handling POST request", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Handle test connection request
     * Test connection ignores ldapEnabled - works even if disabled
     */
    private void handleTestConnection(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        
        try {
            // Read request body
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            JsonObject requestData = gson.fromJson(jsonBody.toString(), JsonObject.class);
            
            // Get current settings as base
            LdapSettings currentSettings = ldapSettingsService.getLdapSettings();
            
            // Override with test settings if provided
            LdapSettings testSettings = jsonToSettings(requestData, currentSettings);
            
            // Test connection (ignores ldapEnabled)
            boolean success = ldapSettingsService.testConnection(testSettings);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            responseJson.addProperty("message", "LDAP connection test successful");
            
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (LdapConnectionException e) {
            logger.error("LDAP connection test failed", e);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", e.getUserFriendlyMessage());
            responseJson.addProperty("errorType", e.getErrorType().name());
            
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            logger.error("Unexpected error during connection test", e);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Unexpected error: " + e.getMessage());
            
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(gson.toJson(responseJson));
        }
    }
    
    /**
     * Handle refresh cache request
     */
    private void handleRefreshCache(HttpServletResponse response) throws IOException {
        try {
            ldapSettingsService.refreshCache();
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Cache refreshed successfully");
            
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            logger.error("Error refreshing cache", e);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Failed to refresh cache: " + e.getMessage());
            
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(gson.toJson(responseJson));
        }
    }
    
    /**
     * Convert LdapSettings to JsonObject with optional password masking
     */
    private JsonObject settingsToJson(LdapSettings settings, boolean maskPassword) {
        JsonObject json = new JsonObject();
        json.addProperty("ldapEnabled", settings.isLdapEnabled());
        json.addProperty("ldapUrl", settings.getLdapUrl());
        json.addProperty("baseDn", settings.getBaseDn());
        json.addProperty("bindDn", settings.getBindDn());
        
        // Mask password: "********" if exists, null otherwise
        if (maskPassword) {
            boolean hasPassword = settings.getBindPassword() != null && !settings.getBindPassword().trim().isEmpty();
            json.addProperty("bindPassword", EncryptionService.getMaskedPassword(hasPassword));
        } else {
            json.addProperty("bindPassword", settings.getBindPassword());
        }
        
        json.addProperty("userSearchBase", settings.getUserSearchBase());
        json.addProperty("userSearchFilter", settings.getUserSearchFilter());
        json.addProperty("groupSearchBase", settings.getGroupSearchBase());
        json.addProperty("connectionTimeout", settings.getConnectionTimeout());
        
        return json;
    }
    
    /**
     * Convert JsonObject to LdapSettings
     * If password is not provided or is "********", keep current password
     */
    private LdapSettings jsonToSettings(JsonObject json, LdapSettings currentSettings) {
        LdapSettings settings = new LdapSettings();
        
        if (json.has("ldapEnabled")) {
            settings.setLdapEnabled(json.get("ldapEnabled").getAsBoolean());
        } else {
            settings.setLdapEnabled(currentSettings != null ? currentSettings.isLdapEnabled() : false);
        }
        
        if (json.has("ldapUrl")) {
            settings.setLdapUrl(getJsonString(json, "ldapUrl"));
        } else {
            settings.setLdapUrl(currentSettings != null ? currentSettings.getLdapUrl() : null);
        }
        
        if (json.has("baseDn")) {
            settings.setBaseDn(getJsonString(json, "baseDn"));
        } else {
            settings.setBaseDn(currentSettings != null ? currentSettings.getBaseDn() : null);
        }
        
        if (json.has("bindDn")) {
            settings.setBindDn(getJsonString(json, "bindDn"));
        } else {
            settings.setBindDn(currentSettings != null ? currentSettings.getBindDn() : null);
        }
        
        // Handle password: if not provided or is "********", keep current
        if (json.has("bindPassword")) {
            String password = getJsonString(json, "bindPassword");
            if (password != null && !password.trim().isEmpty() && !password.equals("********")) {
                settings.setBindPassword(password);
            } else {
                // Keep current password
                settings.setBindPassword(currentSettings != null ? currentSettings.getBindPassword() : null);
            }
        } else {
            // Keep current password
            settings.setBindPassword(currentSettings != null ? currentSettings.getBindPassword() : null);
        }
        
        if (json.has("userSearchBase")) {
            settings.setUserSearchBase(getJsonString(json, "userSearchBase"));
        } else {
            settings.setUserSearchBase(currentSettings != null ? currentSettings.getUserSearchBase() : null);
        }
        
        if (json.has("userSearchFilter")) {
            settings.setUserSearchFilter(getJsonString(json, "userSearchFilter"));
        } else {
            settings.setUserSearchFilter(currentSettings != null ? currentSettings.getUserSearchFilter() : "(uid={0})");
        }
        
        if (json.has("groupSearchBase")) {
            settings.setGroupSearchBase(getJsonString(json, "groupSearchBase"));
        } else {
            settings.setGroupSearchBase(currentSettings != null ? currentSettings.getGroupSearchBase() : null);
        }
        
        if (json.has("connectionTimeout")) {
            settings.setConnectionTimeout(json.get("connectionTimeout").getAsInt());
        } else {
            settings.setConnectionTimeout(currentSettings != null ? currentSettings.getConnectionTimeout() : 5000);
        }
        
        return settings;
    }
    
    /**
     * Safely get string value from JsonObject, handling JsonNull
     */
    private String getJsonString(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        com.google.gson.JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }
    
    /**
     * Check if user is SuperAdmin
     */
    private boolean isSuperAdmin(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Object roleObj = request.getAttribute("userRole");
        if (roleObj != null && AppRoleNames.isSuperAdminName(roleObj.toString())) {
            return true;
        }
        
        // Fallback: parse ACCESS_TOKEN cookie directly
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                String role = claims.getStringClaim("role");
                if (role != null && AppRoleNames.isSuperAdminName(role)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("Error parsing token for SuperAdmin check", e);
        }
        
        return false;
    }
    
    /**
     * Get cookie value by name
     */
    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}

