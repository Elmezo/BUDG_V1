package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.service.DefaultWorkflowService;
import com.example.budg_v2.service.NotificationSettingsService;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonParser;

/**
 * Servlet for system settings management in Admin Panel
 * Endpoints:
 * GET /api/system-settings/{group} - Get all settings for a group
 * GET /api/system-settings/{group}/{key} - Get a specific setting
 * PUT /api/system-settings/{group}/{key} - Update a specific setting
 * PUT /api/system-settings/{group} - Update multiple settings in a group (batch save)
 */
@WebServlet("/api/system-settings/*")
public class SystemSettingsServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SystemSettingsServlet.class);
    private final Gson gson = new Gson();
    private final SystemSettingsDAO systemSettingsDAO = new SystemSettingsDAO();

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

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing group or key in path");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            // Remove leading slash and split path
            String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            // URL decode to handle spaces and special characters
            trimmed = URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
            String[] parts = trimmed.split("/");
            
            // Check if this is a read-only public group (Dashboard settings can be read by all authenticated users)
            boolean isPublicReadGroup = parts.length > 0 && "Dashboard".equals(parts[0]);
            
            // Check SuperAdmin access (except for public read groups on GET requests)
            if (!isPublicReadGroup && !isSuperAdmin(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Forbidden: SuperAdmin access required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            if (parts.length == 1) {
                // GET /api/system-settings/{group} - Get all settings for a group
                String group = parts[0];
                
                // Handle DefaultWorkflows group specially
                if ("DefaultWorkflows".equals(group)) {
                    DefaultWorkflowService service = new DefaultWorkflowService();
                    String workflowsPath = service.getDefaultWorkflowsDirectoryPath();
                    
                    JsonObject result = new JsonObject();
                    result.addProperty("Default Workflows", workflowsPath);
                    response.getWriter().write(gson.toJson(result));
                    return;
                }
                
                Map<String, Object> settings = systemSettingsDAO.getSettingsByGroup(group);
                
                response.getWriter().write(gson.toJson(settings));
            } else if (parts.length == 2) {
                // GET /api/system-settings/{group}/{key} - Get a specific setting
                String group = parts[0];
                String key = parts[1];
                com.example.budg_v2.model.SystemSettings setting = systemSettingsDAO.getSetting(group, key);
                if (setting != null) {
                    JsonObject result = new JsonObject();
                    result.addProperty("group", setting.getSettingGroup());
                    result.addProperty("key", setting.getSettingKey());
                    result.addProperty("value", setting.getSettingValue());
                    result.addProperty("dataType", setting.getDataType());
                    response.getWriter().write(gson.toJson(result));
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Setting not found");
                    response.getWriter().write(gson.toJson(error));
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid path format");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
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

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing group or key in path");
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

            // Remove leading slash and split path
            String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            // URL decode to handle spaces and special characters
            trimmed = URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
            String[] parts = trimmed.split("/");

            if (parts.length == 1) {
                // PUT /api/system-settings/{group} - Batch update multiple settings
                String group = parts[0];
                JsonObject requestData = gson.fromJson(jsonBody.toString(), JsonObject.class);

                if (requestData == null || requestData.entrySet().isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid request body");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                // Capture old state before update
                Map<String, Object> oldSettings = systemSettingsDAO.getSettingsByGroup(group);
                Map<String, Object> oldState = new java.util.HashMap<>(oldSettings);
                
                // Update each setting in the request
                for (Map.Entry<String, com.google.gson.JsonElement> entry : requestData.entrySet()) {
                    String key = entry.getKey();
                    com.google.gson.JsonElement valueElement = entry.getValue();

                    String value = null;
                    String dataType = "string";

                    if (valueElement.isJsonPrimitive()) {
                        com.google.gson.JsonPrimitive primitive = valueElement.getAsJsonPrimitive();
                        if (primitive.isString()) {
                            value = primitive.getAsString();
                            dataType = "string";
                        } else if (primitive.isNumber()) {
                            value = String.valueOf(primitive.getAsInt());
                            dataType = "int";
                        } else if (primitive.isBoolean()) {
                            value = String.valueOf(primitive.getAsBoolean());
                            dataType = "boolean";
                        }
                    }

                    if (value != null) {
                        systemSettingsDAO.saveSetting(group, key, value, dataType);
                    }
                }

                // Return updated settings
                Map<String, Object> updatedSettings = systemSettingsDAO.getSettingsByGroup(group);
                Map<String, Object> newState = new java.util.HashMap<>(updatedSettings);

                // Invalidate notification settings cache so disable-toggle changes take effect immediately
                if ("Notifications".equalsIgnoreCase(group)) {
                    new NotificationSettingsService().invalidateCache();
                }

                // Log the activity
                // Special handling: GlossaryRollup is part of App Settings, not System Settings
                if ("GlossaryRollup".equalsIgnoreCase(group)) {
                    // Build proper state for Glossary RollUp
                    Map<String, Object> glossaryOldState = buildGlossaryRollupState(oldState);
                    Map<String, Object> glossaryNewState = buildGlossaryRollupState(newState);
                    
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("component", ActivityLogConstants.COMPONENT_GLOSSARY_ROLLUP);
                    
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_APP_SETTINGS, 
                                                 ActivityLogConstants.COMPONENT_GLOSSARY_ROLLUP, 
                                                 ActivityLogConstants.CHANGE_TYPE_UPDATE, 
                                                 glossaryOldState, glossaryNewState, contextMap);
                } else {
                    String component = group; // Component is the group name (e.g., "Environment", "Dashboard")
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_SYSTEM_SETTINGS, 
                                                 component, ActivityLogConstants.CHANGE_TYPE_UPDATE, 
                                                 oldState, newState);
                }
                
                response.getWriter().write(gson.toJson(updatedSettings));

            } else if (parts.length == 2) {
                // PUT /api/system-settings/{group}/{key} - Update a specific setting
                String group = parts[0];
                String key = parts[1];
                JsonObject requestData = gson.fromJson(jsonBody.toString(), JsonObject.class);

                if (requestData == null || !requestData.has("value")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Missing 'value' in request body");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                // Capture old state
                com.example.budg_v2.model.SystemSettings oldSetting = systemSettingsDAO.getSetting(group, key);
                Map<String, Object> oldState = new java.util.HashMap<>();
                if (oldSetting != null) {
                    oldState.put(key, oldSetting.getSettingValue());
                }
                
                String value = requestData.get("value").getAsString();
                String dataType = requestData.has("dataType") ? requestData.get("dataType").getAsString() : "string";

                systemSettingsDAO.saveSetting(group, key, value, dataType);

                // Invalidate notification settings cache so disable-toggle changes take effect immediately
                if ("Notifications".equalsIgnoreCase(group)) {
                    new NotificationSettingsService().invalidateCache();
                }

                // Return updated setting
                com.example.budg_v2.model.SystemSettings setting = systemSettingsDAO.getSetting(group, key);
                
   // Capture new state
                Map<String, Object> newState = new java.util.HashMap<>();
                newState.put(key, setting.getSettingValue());
                
                // Log the activity
                // Special handling: GlossaryRollup is part of App Settings, not System Settings
                if ("GlossaryRollup".equalsIgnoreCase(group)) {
                    // Build proper state for Glossary RollUp
                    Map<String, Object> glossaryOldState = buildGlossaryRollupState(oldState);
                    Map<String, Object> glossaryNewState = buildGlossaryRollupState(newState);
                    
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("component", ActivityLogConstants.COMPONENT_GLOSSARY_ROLLUP);
                    
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_APP_SETTINGS, 
                                                 ActivityLogConstants.COMPONENT_GLOSSARY_ROLLUP, 
                                                 ActivityLogConstants.CHANGE_TYPE_UPDATE, 
                                                 glossaryOldState, glossaryNewState, contextMap);
                } else {
                    String component = group; // Component is the group name
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_SYSTEM_SETTINGS, 
                                                 component, ActivityLogConstants.CHANGE_TYPE_UPDATE, 
                                                 oldState, newState);
                }

                // Return updated setting
               // com.example.budg_v2.model.SystemSettings setting = systemSettingsDAO.getSetting(group, key);
                JsonObject result = new JsonObject();
                result.addProperty("group", setting.getSettingGroup());
                result.addProperty("key", setting.getSettingKey());
                result.addProperty("value", setting.getSettingValue());
                result.addProperty("dataType", setting.getDataType());
                response.getWriter().write(gson.toJson(result));

            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid path format");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error processing request", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Check if the current user is a SuperAdmin
     * Checks request attributes first, then falls back to parsing token from cookie, then session
     */
    private boolean isSuperAdmin(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Object roleObj = request.getAttribute("userRole");
        if (roleObj != null && AppRoleNames.isSuperAdminName(roleObj.toString())) {
            return true;
        }
        
        // Fallback: parse ACCESS_TOKEN cookie directly if filter didn't set attributes
        // This is needed because AuthFilter allows GET requests without token,
        // but we need authentication for /api/system-settings endpoints
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                String role = claims.getStringClaim("role");
                if (role != null && AppRoleNames.isSuperAdminName(role)) {
                    request.setAttribute("userRole", role);
                    return true;
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, continue to check session
            logger.debug("Failed to parse token from cookie: " + e.getMessage());
        }
        
        // Last fallback: try to get from session
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionRole = session.getAttribute("userRole");
            if (sessionRole != null && AppRoleNames.isSuperAdminName(sessionRole.toString())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Build state map for Glossary RollUp logging
     * Extracts enabled types from the settings
     */
    private Map<String, Object> buildGlossaryRollupState(Map<String, Object> settings) {
        Map<String, Object> state = new HashMap<>();
        
        if (settings != null && settings.containsKey("enabled_types")) {
            Object enabledTypesValue = settings.get("enabled_types");
            if (enabledTypesValue != null) {
                try {
                    String valueStr = enabledTypesValue.toString();
                    // Parse JSON array if it's a JSON string
                    if (valueStr.trim().startsWith("[")) {
                        com.google.gson.JsonArray typesArray = JsonParser.parseString(valueStr).getAsJsonArray();
                        List<String> types = new ArrayList<>();
                        typesArray.forEach(elem -> types.add(elem.getAsString()));
                        state.put("Enabled Types", String.join(", ", types));
                    } else {
                        state.put("Enabled Types", valueStr);
                    }
                } catch (Exception e) {
                    // If parsing fails, just use the raw value
                    state.put("Enabled Types", enabledTypesValue.toString());
                }
            }
        }
        
        return state;
    }
    
    /**
     * Get cookie value from request
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
