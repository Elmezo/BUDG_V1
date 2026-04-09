package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.model.EdcSettings;
import com.example.budg_v2.service.EdcClient;
import com.example.budg_v2.service.EdcSettingsService;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.EncryptionService;
import com.example.budg_v2.util.JwtUtil;
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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Servlet for EDC (Enterprise Data Catalog) settings management
 * 
 * Endpoints:
 * - GET /api/admin/settings/edc - Get EDC settings (password masked)
 * - POST /api/admin/settings/edc - Save EDC settings (password encrypted)
 * - POST /api/admin/settings/edc/test-connection - Test EDC connection
 * 
 * Security: SuperAdmin only
 */
@WebServlet("/api/admin/settings/edc/*")
public class EdcSettingsServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(EdcSettingsServlet.class);
    private static final String MASKED_PASSWORD = "********";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private final Gson gson = new Gson();
    private final EdcSettingsService edcSettingsService;
    private final EdcClient edcClient;
    
    public EdcSettingsServlet() {
        this.edcSettingsService = new EdcSettingsService();
        this.edcClient = new EdcClient();
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
            // GET /api/admin/settings/edc - Get settings
            EdcSettings settings = edcSettingsService.getEdcSettings();
            
            // Create response with masked password
            JsonObject responseJson = settingsToJson(settings, true);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            logger.error("Error getting EDC settings", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to get EDC settings: " + e.getMessage());
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
            // POST /api/admin/settings/edc - Save settings
            handleSaveSettings(request, response);
        } else {
            // Remove leading slash
            String action = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            
            if ("test-connection".equals(action)) {
                // POST /api/admin/settings/edc/test-connection - Test connection
                handleTestConnection(request, response);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unknown action: " + action);
                response.getWriter().write(gson.toJson(error));
            }
        }
    }
    
    /**
     * Handle save settings request
     */
    private void handleSaveSettings(HttpServletRequest request, HttpServletResponse response)
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
            
            if (requestData == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid request body");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Get current settings to preserve password if needed
            EdcSettings currentSettings = edcSettingsService.getEdcSettings();
            EdcSettings newSettings = jsonToSettings(requestData, currentSettings);
            
            // Validate settings
            String validationError = validateSettings(newSettings);
            if (validationError != null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", validationError);
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Get user ID for activity logging
            Integer userId = getUserId(request);
            
            // Save settings
            edcSettingsService.saveEdcSettings(newSettings, userId);
            
            // Log activity
            logActivity(request, ActivityLogConstants.CHANGE_TYPE_UPDATE, 
                currentSettings, newSettings, true, null);
            
            // Return updated settings with masked password
            JsonObject responseJson = settingsToJson(newSettings, true);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (SQLException e) {
            logger.error("Error saving EDC settings", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to save EDC settings: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Unexpected error saving EDC settings", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Handle test connection request
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
            EdcSettings currentSettings = edcSettingsService.getEdcSettings();
            
            // Override with test settings if provided
            EdcSettings testSettings = jsonToSettings(requestData, currentSettings);
            
            // Log SSL insecure flag for debugging
            logger.info("Test connection - SSL insecure flag: {}", testSettings.isSslInsecure());
            
            // Validate required fields for test
            if (testSettings.getServerHost() == null || testSettings.getServerHost().trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Server host is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            if (testSettings.getLoginUsername() == null || testSettings.getLoginUsername().trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Login username is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // If password is masked, use existing password
            if (testSettings.getLoginPassword() != null && 
                testSettings.getLoginPassword().equals(MASKED_PASSWORD)) {
                testSettings.setLoginPassword(currentSettings.getLoginPassword());
            }
            
            if (testSettings.getLoginPassword() == null || testSettings.getLoginPassword().trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Login password is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Test connection
            EdcClient.ProductInformation productInfo = edcClient.getProductInformation(testSettings);
            
            // Log successful test
            logActivity(request, ActivityLogConstants.CHANGE_TYPE_OTHER_ACTIONS, 
                null, testSettings, true, 
                "Test connection successful: " + productInfo.releaseVersion + " build " + productInfo.buildVersion);
            
            // Return success response
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("ok", true);
            responseJson.addProperty("releaseVersion", productInfo.releaseVersion);
            responseJson.addProperty("buildVersion", productInfo.buildVersion);
            responseJson.addProperty("buildDate", productInfo.buildDate);
            responseJson.addProperty("message", String.format(
                "Connected to EDC %s build %s (%s)",
                productInfo.releaseVersion, productInfo.buildVersion, productInfo.buildDate
            ));
            
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (EdcClient.EdcConnectionException e) {
            logger.error("EDC connection test failed", e);
            
            // Log failed test
            EdcSettings testSettings = null;
            try {
                BufferedReader reader = request.getReader();
                StringBuilder jsonBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }
                JsonObject requestData = gson.fromJson(jsonBody.toString(), JsonObject.class);
                EdcSettings currentSettings = edcSettingsService.getEdcSettings();
                testSettings = jsonToSettings(requestData, currentSettings);
            } catch (Exception ignored) {
                // Ignore parsing errors for logging
            }
            
            logActivity(request, ActivityLogConstants.CHANGE_TYPE_OTHER_ACTIONS, 
                null, testSettings, false, "Test connection failed: " + e.getUserFriendlyMessage());
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("ok", false);
            responseJson.addProperty("errorCode", "CONNECTION_FAILED");
            responseJson.addProperty("message", e.getUserFriendlyMessage());
            if (e.getHttpStatus() > 0) {
                responseJson.addProperty("httpStatus", e.getHttpStatus());
            }
            
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            logger.error("Unexpected error during connection test", e);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("ok", false);
            responseJson.addProperty("errorCode", "UNEXPECTED_ERROR");
            responseJson.addProperty("message", "Unexpected error: " + e.getMessage());
            
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(gson.toJson(responseJson));
        }
    }
    
    /**
     * Validate EDC settings
     */
    private String validateSettings(EdcSettings settings) {
        // Host validation
        if (settings.getServerHost() == null || settings.getServerHost().trim().isEmpty()) {
            return "Server host is required";
        }
        String host = settings.getServerHost().trim();
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            return "Server host must start with http:// or https://";
        }
        
        // Port validation
        if (settings.getServerPort() == null) {
            return "Server port is required";
        }
        if (settings.getServerPort() < 1 || settings.getServerPort() > 65535) {
            return "Server port must be between 1 and 65535";
        }
        
        // Username validation
        if (settings.getLoginUsername() == null || settings.getLoginUsername().trim().isEmpty()) {
            return "Login username is required";
        }
        
        // Super admin email validation
        if (settings.getAxonSuperAdminEmail() == null || settings.getAxonSuperAdminEmail().trim().isEmpty()) {
            return "BUDG Super Admin Email is required";
        }
        if (!EMAIL_PATTERN.matcher(settings.getAxonSuperAdminEmail().trim()).matches()) {
            return "BUDG Super Admin Email must be a valid email format";
        }
        
        // Timeout validation
        if (settings.getRequestTimeout() == null) {
            return "Request timeout is required";
        }
        if (settings.getRequestTimeout() < 1 || settings.getRequestTimeout() > 3600) {
            return "Request timeout must be between 1 and 3600 seconds";
        }
        
        // Proxy validation
        if (settings.getProxyHost() != null && !settings.getProxyHost().trim().isEmpty()) {
            if (settings.getProxyPort() == null || settings.getProxyPort() < 1 || settings.getProxyPort() > 65535) {
                return "Proxy port must be between 1 and 65535 when proxy host is provided";
            }
        }
        
        return null; // Valid
    }
    
    /**
     * Convert EdcSettings to JsonObject with optional password masking
     */
    private JsonObject settingsToJson(EdcSettings settings, boolean maskPassword) {
        JsonObject json = new JsonObject();
        json.addProperty("eic_server_host", settings.getServerHost());
        json.addProperty("eic_server_port", settings.getServerPort());
        json.addProperty("eic_server_login_username", settings.getLoginUsername());
        
        // Mask password: "********" if exists, null otherwise
        if (maskPassword) {
            boolean hasPassword = settings.getLoginPassword() != null && !settings.getLoginPassword().trim().isEmpty();
            json.addProperty("eic_server_login_password", EncryptionService.getMaskedPassword(hasPassword));
        } else {
            json.addProperty("eic_server_login_password", settings.getLoginPassword());
        }
        
        json.addProperty("eic_server_login_namespace", settings.getLoginNamespace());
        json.addProperty("eic_axon_resource_name", settings.getAxonResourceName());
        json.addProperty("eic_axon_super_admin_email", settings.getAxonSuperAdminEmail());
        json.addProperty("eic_enable_auto_lineage_recommendation", settings.isEnableAutoLineageRecommendation());
        json.addProperty("eic_enable_lineage_email_notification", settings.isEnableLineageEmailNotification());
        json.addProperty("eic_enable_custom_attributes", settings.isEnableCustomAttributes());
        json.addProperty("eic_enable_cleanup_lineage_recommendations", settings.isEnableCleanupLineageRecommendations());
        json.addProperty("eic_enable_filter", settings.isEnableFilter());
        json.addProperty("eic_update_onboarded_assets", settings.isUpdateOnboardedAssets());
        json.addProperty("eic_default_glossary", settings.getDefaultGlossary());
        json.addProperty("eic_request_timeout", settings.getRequestTimeout());
        json.addProperty("eic_proxy_host", settings.getProxyHost());
        json.addProperty("eic_proxy_port", settings.getProxyPort());
        json.addProperty("eic_ssl_insecure", settings.isSslInsecure());
        
        return json;
    }
    
    /**
     * Convert JsonObject to EdcSettings
     * If password is not provided or is "********", keep current password
     */
    private EdcSettings jsonToSettings(JsonObject json, EdcSettings currentSettings) {
        EdcSettings settings = new EdcSettings();
        
        if (json.has("eic_server_host")) {
            settings.setServerHost(getJsonString(json, "eic_server_host"));
        } else if (currentSettings != null) {
            settings.setServerHost(currentSettings.getServerHost());
        }
        
        if (json.has("eic_server_port")) {
            if (json.get("eic_server_port").isJsonNull()) {
                settings.setServerPort(null);
            } else {
                settings.setServerPort(json.get("eic_server_port").getAsInt());
            }
        } else if (currentSettings != null) {
            settings.setServerPort(currentSettings.getServerPort());
        }
        
        if (json.has("eic_server_login_username")) {
            settings.setLoginUsername(getJsonString(json, "eic_server_login_username"));
        } else if (currentSettings != null) {
            settings.setLoginUsername(currentSettings.getLoginUsername());
        }
        
        // Handle password: if not provided or is "********", keep current
        if (json.has("eic_server_login_password")) {
            String password = getJsonString(json, "eic_server_login_password");
            if (password != null && !password.trim().isEmpty() && !password.equals(MASKED_PASSWORD)) {
                settings.setLoginPassword(password);
            } else {
                // Keep current password
                settings.setLoginPassword(currentSettings != null ? currentSettings.getLoginPassword() : null);
            }
        } else {
            // Keep current password
            settings.setLoginPassword(currentSettings != null ? currentSettings.getLoginPassword() : null);
        }
        
        if (json.has("eic_server_login_namespace")) {
            settings.setLoginNamespace(getJsonString(json, "eic_server_login_namespace"));
        } else if (currentSettings != null) {
            settings.setLoginNamespace(currentSettings.getLoginNamespace());
        }
        
        if (json.has("eic_axon_resource_name")) {
            settings.setAxonResourceName(getJsonString(json, "eic_axon_resource_name"));
        } else if (currentSettings != null) {
            settings.setAxonResourceName(currentSettings.getAxonResourceName());
        }
        
        if (json.has("eic_axon_super_admin_email")) {
            settings.setAxonSuperAdminEmail(getJsonString(json, "eic_axon_super_admin_email"));
        } else if (currentSettings != null) {
            settings.setAxonSuperAdminEmail(currentSettings.getAxonSuperAdminEmail());
        }
        
        if (json.has("eic_enable_auto_lineage_recommendation")) {
            settings.setEnableAutoLineageRecommendation(json.get("eic_enable_auto_lineage_recommendation").getAsBoolean());
        } else if (currentSettings != null) {
            settings.setEnableAutoLineageRecommendation(currentSettings.isEnableAutoLineageRecommendation());
        }
        
        if (json.has("eic_enable_lineage_email_notification")) {
            settings.setEnableLineageEmailNotification(json.get("eic_enable_lineage_email_notification").getAsBoolean());
        } else if (currentSettings != null) {
            settings.setEnableLineageEmailNotification(currentSettings.isEnableLineageEmailNotification());
        }
        
        if (json.has("eic_enable_custom_attributes")) {
            settings.setEnableCustomAttributes(json.get("eic_enable_custom_attributes").getAsBoolean());
        } else if (currentSettings != null) {
            settings.setEnableCustomAttributes(currentSettings.isEnableCustomAttributes());
        }
        
        if (json.has("eic_enable_cleanup_lineage_recommendations")) {
            settings.setEnableCleanupLineageRecommendations(json.get("eic_enable_cleanup_lineage_recommendations").getAsBoolean());
        } else if (currentSettings != null) {
            settings.setEnableCleanupLineageRecommendations(currentSettings.isEnableCleanupLineageRecommendations());
        }
        
        if (json.has("eic_enable_filter")) {
            settings.setEnableFilter(json.get("eic_enable_filter").getAsBoolean());
        } else if (currentSettings != null) {
            settings.setEnableFilter(currentSettings.isEnableFilter());
        }
        
        if (json.has("eic_update_onboarded_assets")) {
            settings.setUpdateOnboardedAssets(json.get("eic_update_onboarded_assets").getAsBoolean());
        } else if (currentSettings != null) {
            settings.setUpdateOnboardedAssets(currentSettings.isUpdateOnboardedAssets());
        }
        
        if (json.has("eic_default_glossary")) {
            settings.setDefaultGlossary(getJsonString(json, "eic_default_glossary"));
        } else if (currentSettings != null) {
            settings.setDefaultGlossary(currentSettings.getDefaultGlossary());
        }
        
        if (json.has("eic_request_timeout")) {
            if (json.get("eic_request_timeout").isJsonNull()) {
                settings.setRequestTimeout(null);
            } else {
                settings.setRequestTimeout(json.get("eic_request_timeout").getAsInt());
            }
        } else if (currentSettings != null) {
            settings.setRequestTimeout(currentSettings.getRequestTimeout());
        }
        
        if (json.has("eic_proxy_host")) {
            settings.setProxyHost(getJsonString(json, "eic_proxy_host"));
        } else if (currentSettings != null) {
            settings.setProxyHost(currentSettings.getProxyHost());
        }
        
        if (json.has("eic_proxy_port")) {
            if (json.get("eic_proxy_port").isJsonNull() || json.get("eic_proxy_port").getAsString().trim().isEmpty()) {
                settings.setProxyPort(null);
            } else {
                settings.setProxyPort(json.get("eic_proxy_port").getAsInt());
            }
        } else if (currentSettings != null) {
            settings.setProxyPort(currentSettings.getProxyPort());
        }
        
        if (json.has("eic_ssl_insecure")) {
            settings.setSslInsecure(json.get("eic_ssl_insecure").getAsBoolean());
        } else if (currentSettings != null) {
            settings.setSslInsecure(currentSettings.isSslInsecure());
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
     * Get user ID from request
     */
    private Integer getUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            try {
                return (Integer) userIdObj;
            } catch (ClassCastException e) {
                // Try parsing from string
                try {
                    return Integer.parseInt(userIdObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        
        // Fallback: parse from JWT token
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JwtUtil.parseAndValidate(token);
                return JwtUtil.getUserIdFromToken(token);
            }
        } catch (Exception e) {
            logger.debug("Failed to get user ID from token", e);
        }
        
        return null;
    }
    
    /**
     * Log activity
     */
    private void logActivity(HttpServletRequest request, String changeType, 
                            EdcSettings oldSettings, EdcSettings newSettings,
                            boolean success, String additionalInfo) {
        try {
            Map<String, Object> oldState = null;
            Map<String, Object> newState = null;
            Map<String, Object> contextMap = new HashMap<>();
            
            if (oldSettings != null) {
                oldState = new HashMap<>();
                oldState.put("host", oldSettings.getServerHost());
                oldState.put("port", oldSettings.getServerPort());
                oldState.put("namespace", oldSettings.getLoginNamespace());
                oldState.put("resourceName", oldSettings.getAxonResourceName());
                // Do NOT include password
            }
            
            if (newSettings != null) {
                newState = new HashMap<>();
                newState.put("host", newSettings.getServerHost());
                newState.put("port", newSettings.getServerPort());
                newState.put("namespace", newSettings.getLoginNamespace());
                newState.put("resourceName", newSettings.getAxonResourceName());
                // Do NOT include password
            }
            
            contextMap.put("result", success ? "success" : "failure");
            if (additionalInfo != null) {
                contextMap.put("details", additionalInfo);
            }
            
            ActivityLogHelper.logActivity(
                request,
                ActivityLogConstants.SETTING_EDC_INTEGRATION,
                changeType.equals(ActivityLogConstants.CHANGE_TYPE_OTHER_ACTIONS) 
                    ? ActivityLogConstants.COMPONENT_EDC_TEST_CONNECTION 
                    : ActivityLogConstants.COMPONENT_EDC_SETTINGS,
                changeType,
                oldState,
                newState,
                contextMap
            );
        } catch (Exception e) {
            logger.error("Failed to log EDC activity", e);
        }
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

