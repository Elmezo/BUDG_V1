package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "AppConfigServlet", urlPatterns = {"/admin/api/app-config/*"})
public class AppConfigServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Config key is required\"}");
                return;
            }

            String configKey = pathInfo.substring(1); // Remove leading slash
            //system.out.println("Fetching config for key: " + configKey);

            try (Connection conn = DatabaseConnection.getConnection()) {
                String sql = "SELECT config_key, definition FROM app_config WHERE config_key = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, configKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            JsonObject result = new JsonObject();
                            result.addProperty("config_key", rs.getString("config_key"));
                            result.addProperty("definition", rs.getString("definition"));
                            resp.getWriter().write(gson.toJson(result));
                        } else {
                            // Special handling for UNISON_FUZZY_DEFAULT and HIDE_NON_PUBLIC_OBJECTS - create with default value if not found
                            if ("UNISON_FUZZY_DEFAULT".equals(configKey) || "HIDE_NON_PUBLIC_OBJECTS".equals(configKey)) {
                                String insertSql = "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
                                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                                    insertPs.setString(1, configKey);
                                    insertPs.setString(2, "false"); // Default value
                                    int rowsAffected = insertPs.executeUpdate();
                                    if (rowsAffected > 0) {
                                        // Return the newly created config
                                        JsonObject result = new JsonObject();
                                        result.addProperty("config_key", configKey);
                                        result.addProperty("definition", "false");
                                        resp.getWriter().write(gson.toJson(result));
                                    } else {
                                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                        resp.getWriter().write("{\"error\":\"Failed to create default configuration\"}");
                                    }
                                }
                            } else {
                                // For other config keys (like QUICK_LINK), return 200 with null definition
                                // This is not an error - the config just hasn't been set yet
                                JsonObject result = new JsonObject();
                                result.addProperty("config_key", configKey);
                                result.add("definition", JsonNull.INSTANCE); // null JSON value
                                resp.getWriter().write(gson.toJson(result));
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Server error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            String pathInfo = req.getPathInfo();
            
            // Check if this is a batch update for Search Settings
            if (pathInfo != null && pathInfo.equals("/search-settings/batch")) {
                handleBatchSearchSettings(req, resp);
                return;
            }
            
            if (pathInfo == null || pathInfo.length() <= 1) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Config key is required\"}");
                return;
            }

            String configKey = pathInfo.substring(1); // Remove leading slash

            BufferedReader reader = req.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JsonObject requestData = JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();
            
            if (!requestData.has("definition")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing definition field\"}");
                return;
            }

            String definition = requestData.get("definition").getAsString();
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Capture old state before update
                String oldDefinition = null;
                String checkSql = "SELECT definition FROM app_config WHERE config_key = ?";
                boolean configExists = false;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, configKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            configExists = true;
                            oldDefinition = rs.getString("definition");
                        }
                    }
                }

                if (configExists) {
                    // Update existing config
                    String updateSql = "UPDATE app_config SET definition = ? WHERE config_key = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, definition);
                        ps.setString(2, configKey);
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected > 0) {
                            // Log activity - Update case
                            String component = getComponentNameForConfigKey(configKey);
                            if (component != null) {
                                // Build proper state maps based on component type
                                Map<String, Object> oldState = buildAppSettingsState(configKey, oldDefinition);
                                Map<String, Object> newState = buildAppSettingsState(configKey, definition);
                                
                                Map<String, Object> contextMap = new HashMap<>();
                                contextMap.put("component", component);
                                
                                ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_APP_SETTINGS,
                                    component, ActivityLogConstants.CHANGE_TYPE_UPDATE,
                                    oldState, newState, contextMap);
                            }
                            
                            resp.getWriter().write("{\"success\":true,\"message\":\"Configuration updated successfully\"}");
                        } else {
                            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            resp.getWriter().write("{\"error\":\"Failed to update configuration\"}");
                        }
                    }
                } else {
                    // Insert new config
                    String insertSql = "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setString(1, configKey);
                        ps.setString(2, definition);
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected > 0) {
                            // Log activity - Create case (for new app settings)
                            String component = getComponentNameForConfigKey(configKey);
                            if (component != null) {
                                Map<String, Object> oldState = new HashMap<>();
                                
                                // Build proper state map based on component type
                                Map<String, Object> newState = buildAppSettingsState(configKey, definition);
                                
                                Map<String, Object> contextMap = new HashMap<>();
                                contextMap.put("component", component);
                                
                                ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_APP_SETTINGS,
                                    component, ActivityLogConstants.CHANGE_TYPE_CREATE,
                                    oldState, newState, contextMap);
                            }
                            
                            resp.getWriter().write("{\"success\":true,\"message\":\"Configuration created successfully\"}");
                        } else {
                            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            resp.getWriter().write("{\"error\":\"Failed to create configuration\"}");
                        }
                    }
                }
            } catch (SQLException e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid JSON: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Map config key to component name for activity logging
     */
    private String getComponentNameForConfigKey(String configKey) {
        if (configKey == null) {
            return null;
        }
        
        // Map config keys to component names
        if (configKey.contains("QUICK_LINK") || configKey.contains("QUICKLINK")) {
            return ActivityLogConstants.COMPONENT_QUICK_LINKS;
        } else if (configKey.equals("UNISON_DEFAULTS")) {
            return ActivityLogConstants.COMPONENT_DISPLAY_SETTINGS;
        } else if (configKey.contains("SEARCH") || configKey.contains("FUZZY") || 
                   configKey.contains("HIDE_NON_PUBLIC")) {
            return ActivityLogConstants.COMPONENT_SEARCH_SETTINGS;
        } else if (configKey.contains("GLOSSARY") || configKey.contains("ROLLUP") || 
                   configKey.contains("ROLL_UP")) {
            return ActivityLogConstants.COMPONENT_GLOSSARY_ROLLUP;
        }
        
        // Default: return null if not a recognized app setting
        return null;
    }
    
    /**
     * Build state map for App Settings logging based on component type
     */
    private Map<String, Object> buildAppSettingsState(String configKey, String definition) {
        Map<String, Object> state = new HashMap<>();
        
        if (definition == null || definition.trim().isEmpty()) {
            return state;
        }
        
        try {
            // Parse definition as JSON if possible
            if (definition.trim().startsWith("{")) {
                JsonObject jsonDef = JsonParser.parseString(definition).getAsJsonObject();
                
                // Quick Links: Extract saved search info
                if (configKey.contains("QUICK_LINK") || configKey.contains("QUICKLINK")) {
                    if (jsonDef.has("savedSearchId")) {
                        state.put("Saved Search ID", jsonDef.get("savedSearchId").getAsString());
                    }
                    if (jsonDef.has("savedSearchName")) {
                        state.put("Saved Search Name", jsonDef.get("savedSearchName").getAsString());
                    }
                    if (jsonDef.has("description")) {
                        state.put("Description", jsonDef.get("description").getAsString());
                    }
                }
                // Search Settings: Extract search-related settings
                else if (configKey.contains("SEARCH") || configKey.contains("FUZZY") || 
                         configKey.contains("HIDE_NON_PUBLIC")) {
                    if (jsonDef.has("enabled")) {
                        state.put("Enabled", jsonDef.get("enabled").getAsString());
                    }
                    if (jsonDef.has("value")) {
                        state.put("Value", jsonDef.get("value").getAsString());
                    }
                    // For boolean configs stored as strings
                    if (jsonDef.has("definition")) {
                        state.put("Setting", jsonDef.get("definition").getAsString());
                    }
                }
                // Glossary RollUp: Extract enabled types
                else if (configKey.contains("GLOSSARY") || configKey.contains("ROLLUP") || 
                         configKey.contains("ROLL_UP")) {
                    if (jsonDef.has("enabled_types")) {
                        if (jsonDef.get("enabled_types").isJsonArray()) {
                            List<String> types = new ArrayList<>();
                            jsonDef.getAsJsonArray("enabled_types").forEach(elem -> 
                                types.add(elem.getAsString()));
                            state.put("Enabled Types", String.join(", ", types));
                        } else {
                            state.put("Enabled Types", jsonDef.get("enabled_types").getAsString());
                        }
                    }
                }
            } else {
                // For simple string/boolean values (like UNISON_FUZZY_DEFAULT, HIDE_NON_PUBLIC_OBJECTS)
                // Map to appropriate field name based on config key
                if (configKey.contains("SEARCH") || configKey.contains("FUZZY") || 
                    configKey.contains("HIDE_NON_PUBLIC")) {
                    // For Search Settings, use descriptive field names
                    if (configKey.contains("FUZZY")) {
                        state.put("Fuzzy Search Enabled", definition);
                    } else if (configKey.contains("HIDE_NON_PUBLIC")) {
                        state.put("Hide Non-Public Objects", definition);
                    } else {
                        state.put("Value", definition);
                    }
                } else {
                    state.put("Value", definition);
                }
            }
        } catch (Exception e) {
            // If parsing fails, just store the raw definition
            state.put("Definition", definition);
        }
        
        return state;
    }
    
    /**
     * Handle batch update for Search Settings (both UNISON_FUZZY_DEFAULT and HIDE_NON_PUBLIC_OBJECTS)
     * This prevents duplicate log entries
     */
    private void handleBatchSearchSettings(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            BufferedReader reader = req.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JsonObject requestData = JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();
            
            if (!requestData.has("fuzzySearch") || !requestData.has("hideNonPublic")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing fuzzySearch or hideNonPublic field\"}");
                return;
            }

            String fuzzyValue = requestData.get("fuzzySearch").getAsString();
            String hideNonPublicValue = requestData.has("hideNonPublic") ? 
                requestData.get("hideNonPublic").getAsString() : "false";
            
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Capture old states before update
                Map<String, Object> oldState = new HashMap<>();
                Map<String, Object> newState = new HashMap<>();
                
                // Get old values
                String oldFuzzy = getConfigValue(conn, "UNISON_FUZZY_DEFAULT");
                String oldHideNonPublic = getConfigValue(conn, "HIDE_NON_PUBLIC_OBJECTS");
                
                oldState.put("Fuzzy Search Enabled", oldFuzzy != null ? oldFuzzy : "false");
                oldState.put("Hide Non-Public Objects", oldHideNonPublic != null ? oldHideNonPublic : "false");
                
                // Update both settings
                updateConfigValue(conn, "UNISON_FUZZY_DEFAULT", fuzzyValue);
                updateConfigValue(conn, "HIDE_NON_PUBLIC_OBJECTS", hideNonPublicValue);
                
                // Build new state
                newState.put("Fuzzy Search Enabled", fuzzyValue);
                newState.put("Hide Non-Public Objects", hideNonPublicValue);
                
                // Log as single activity entry
                Map<String, Object> contextMap = new HashMap<>();
                contextMap.put("component", ActivityLogConstants.COMPONENT_SEARCH_SETTINGS);
                
                ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_APP_SETTINGS,
                    ActivityLogConstants.COMPONENT_SEARCH_SETTINGS, ActivityLogConstants.CHANGE_TYPE_UPDATE,
                    oldState, newState, contextMap);
                
                resp.getWriter().write("{\"success\":true,\"message\":\"Search settings updated successfully\"}");
            } catch (SQLException e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid JSON: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Get config value from database
     */
    private String getConfigValue(Connection conn, String configKey) throws SQLException {
        String sql = "SELECT definition FROM app_config WHERE config_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, configKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("definition");
                }
            }
        }
        return null;
    }
    
    /**
     * Update or insert config value
     */
    private void updateConfigValue(Connection conn, String configKey, String definition) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM app_config WHERE config_key = ?";
        boolean exists = false;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, configKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    exists = rs.getInt(1) > 0;
                }
            }
        }
        
        if (exists) {
            String updateSql = "UPDATE app_config SET definition = ? WHERE config_key = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, definition);
                ps.setString(2, configKey);
                ps.executeUpdate();
            }
        } else {
            String insertSql = "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, configKey);
                ps.setString(2, definition);
                ps.executeUpdate();
            }
        }
    }
}
