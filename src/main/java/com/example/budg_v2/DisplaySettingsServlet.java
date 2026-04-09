package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
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

@WebServlet(name = "DisplaySettingsServlet", urlPatterns = {"/admin/api/display-settings"})
public class DisplaySettingsServlet extends HttpServlet {

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

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get current configuration
            String configJson = getCurrentConfig(conn);
            //system.out.println("Current config from DB: " + configJson);
            
            // Parse the configuration and separate facets into active and inactive
            JsonObject config = JsonParser.parseString(configJson).getAsJsonObject();
            List<Map<String, Object>> allFacets = new ArrayList<>();
            List<Map<String, Object>> activeFacets = new ArrayList<>();
            
            if (config.has("facets") && config.get("facets").isJsonArray()) {
                config.getAsJsonArray("facets").forEach(facet -> {
                    JsonObject facetObj = facet.getAsJsonObject();
                    Map<String, Object> facetMap = new HashMap<>();
                    facetMap.put("id", facetObj.get("id").getAsString());
                    facetMap.put("visibility", facetObj.get("visibility").getAsBoolean());
                    facetMap.put("activeFields", facetObj.get("activeFields").getAsString());
                    
                    // Add name and category based on the id
                    String facetId = facetObj.get("id").getAsString();
                    facetMap.put("name", getFacetName(facetId));
                    facetMap.put("category", getFacetCategory(facetId));
                    
                    if (facetObj.get("visibility").getAsBoolean()) {
                        activeFacets.add(facetMap);
                    } else {
                        allFacets.add(facetMap);
                    }
                });
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("allFacets", allFacets);
            response.put("activeFacets", activeFacets);
            
            //system.out.println("Sending response - All facets: " + allFacets.size() + ", Active facets: " + activeFacets.size());
            //system.out.println("All facets: " + gson.toJson(allFacets));
            //system.out.println("Active facets: " + gson.toJson(activeFacets));
            
            resp.getWriter().write(gson.toJson(response));
            
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try (BufferedReader reader = req.getReader()) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            JsonObject requestData = JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();
            
            if (!requestData.has("facets")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing facets data\"}");
                return;
            }
            
        try (Connection conn = DatabaseConnection.getConnection()) {
            //system.out.println("Received facets to update: " + requestData.getAsJsonArray("facets"));
            
            // Capture old state before update
            String oldConfigJson = getCurrentConfig(conn);
            JsonObject oldConfig = JsonParser.parseString(oldConfigJson).getAsJsonObject();
            
            // Update the configuration
            updateConfig(conn, requestData.getAsJsonArray("facets"));
            
            // Capture new state after update
            String newConfigJson = getCurrentConfig(conn);
            JsonObject newConfig = JsonParser.parseString(newConfigJson).getAsJsonObject();
            
            // Build old and new states for logging
            Map<String, Object> oldState = buildDisplaySettingsState(oldConfig);
            Map<String, Object> newState = buildDisplaySettingsState(newConfig);
            
            // Create context map with component name for strategy
            Map<String, Object> contextMap = new HashMap<>();
            contextMap.put("component", ActivityLogConstants.COMPONENT_DISPLAY_SETTINGS);
            
            // Log activity - Update case
            ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_APP_SETTINGS,
                ActivityLogConstants.COMPONENT_DISPLAY_SETTINGS, ActivityLogConstants.CHANGE_TYPE_UPDATE,
                oldState, newState, contextMap);
                
                resp.getWriter().write("{\"success\":true,\"message\":\"Display settings updated successfully\"}");
                
            } catch (SQLException e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid JSON: " + e.getMessage() + "\"}");
        }
    }

    // Remove createAppConfigTableIfNotExists method - table already exists

    private String getCurrentConfig(Connection conn) throws SQLException {
        String sql = "SELECT definition FROM app_config WHERE config_key = 'UNISON_DEFAULTS'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            if (rs.next()) {
                return rs.getString("definition");
            } else {
                // If no configuration exists, create it with default values
                String defaultConfig = createDefaultConfig();
                insertDefaultConfig(conn, defaultConfig);
                return defaultConfig;
            }
        }
    }
    
    private String createDefaultConfig() {
        return "{\n" +
                "  \"facets\" : [ {\n" +
                "    \"id\" : \"DATASET\",\n" +
                "    \"visibility\" : true,\n" +
                "    \"activeFields\" : \"refNumber, name, definition, lifecycle, systemId, systemName\"\n" +
                "  }, {\n" +
                "    \"id\" : \"ATTRIBUTE\",\n" +
                "    \"visibility\" : true,\n" +
                "    \"activeFields\" : \"refNumber, name, definition, dataSetId, dataSetName, systemId, systemName\"\n" +
                "  }, {\n" +
                "    \"id\" : \"SYSTEM\",\n" +
                "    \"visibility\" : true,\n" +
                "    \"activeFields\" : \"name, description, type, lifecycle, classification, ciarating\"\n" +
                "  }, {\n" +
                "    \"id\" : \"GLOSSARY\",\n" +
                "    \"visibility\" : true,\n" +
                "    \"activeFields\" : \"name, type, parentName, parentType, kde, description, lifecycle\"\n" +
                "  }, {\n" +
                "    \"id\" : \"DATAQUALITY\",\n" +
                "    \"visibility\" : true,\n" +
                "    \"activeFields\" : \"refNumber, name, description, attributeName, measuredInId, measuredInName, type, criticality, result\"\n" +
                "  }, {\n" +
                "    \"id\" : \"PEOPLE\",\n" +
                "    \"visibility\" : true,\n" +
                "    \"activeFields\" : \"firstName, lastName, email, function, orgUnitId, orgUnitName\"\n" +
                "  }, {\n" +
                "    \"id\" : \"ROLE\",\n" +
                "    \"visibility\" : true,\n" +
                "    \"activeFields\" : \"roleId, roleName, peopleId, fullName, objectType, objectId, objectName, roleAccepted\"\n" +
                "  }, {\n" +
                "    \"id\" : \"PROCESS\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, definition, parentId, parentName\"\n" +
                "  }, {\n" +
                "    \"id\" : \"PROJECT\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, description, parentId, parentName, lifecycle\"\n" +
                "  }, {\n" +
                "    \"id\" : \"POLICY\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, description, parentId, parentName, lifecycle\"\n" +
                "  }, {\n" +
                "    \"id\" : \"PRODUCT\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, description, parentId, parentName, BUDGStatus\"\n" +
                "  }, {\n" +
                "    \"id\" : \"LEGALENTITY\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"shortName, description, parentId, parentShortName\"\n" +
                "  }, {\n" +
                "    \"id\" : \"SYSTEM_INTERFACE\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, sourceName, targetName, description, automation, frequency, lifecycle\"\n" +
                "  }, {\n" +
                "    \"id\" : \"BUSINESS_AREA\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"name, parentName, description\"\n" +
                "  }, {\n" +
                "    \"id\" : \"COMMITTEE\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, parentName, description\"\n" +
                "  }, {\n" +
                "    \"id\" : \"CLIENT\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"name, parentName, description, lifecycle\"\n" +
                "  }, {\n" +
                "    \"id\" : \"CAPABILITY\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, parentName, description\"\n" +
                "  }, {\n" +
                "    \"id\" : \"ORG_UNIT\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, parentName, description, BUDGStatus\"\n" +
                "  }, {\n" +
                "    \"id\" : \"CHANGE_REQUEST\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"id, subject, type, objectName, status, summary\"\n" +
                "  }, {\n" +
                "    \"id\" : \"REGULATION\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, parentName, description\"\n" +
                "  }, {\n" +
                "    \"id\" : \"REGULATOR\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"name, shortName, description\"\n" +
                "  }, {\n" +
                "    \"id\" : \"JURISDICTION\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"name, description, parentId, parentName\"\n" +
                "  }, {\n" +
                "    \"id\" : \"REGULATORY_THEME\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"refNumber, name, parentName, description\"\n" +
                "  }, {\n" +
                "    \"id\" : \"FIELD\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"name, type\"\n" +
                "  }, {\n" +
                "    \"id\" : \"TASK\",\n" +
                "    \"visibility\" : false,\n" +
                "    \"activeFields\" : \"name, crName, objectId, objectName, objectType, dueDate, dueDays, owners\"\n" +
                "  }, {\n" +
                "    \"id\" : \"ACTIVE_TASKS\",\n" +
                "    \"visibility\" : true,\n" +
                "    \"activeFields\" : \"id, name, title, objectType, object, assignDate, dueDate, dueInDays, owner, segments\"\n" +
                "  } ]\n" +
                "}";
    }
    
    private void insertDefaultConfig(Connection conn, String defaultConfig) throws SQLException {
        String sql = "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "UNISON_DEFAULTS");
            ps.setString(2, defaultConfig);
            ps.executeUpdate();
            //system.out.println("Created default UNISON_DEFAULTS configuration");
        }
    }

    private void updateConfig(Connection conn, com.google.gson.JsonArray facets) throws SQLException {
        // Get the current configuration first
        String currentConfigJson = getCurrentConfig(conn);
        //system.out.println("Current config before update: " + currentConfigJson);
        
        JsonObject currentConfig = JsonParser.parseString(currentConfigJson).getAsJsonObject();
        
        // Create a map of existing facets by ID
        Map<String, JsonObject> existingFacets = new HashMap<>();
        if (currentConfig.has("facets") && currentConfig.get("facets").isJsonArray()) {
            com.google.gson.JsonArray currentFacets = currentConfig.getAsJsonArray("facets");
            for (int i = 0; i < currentFacets.size(); i++) {
                JsonObject facet = currentFacets.get(i).getAsJsonObject();
                String facetId = facet.get("id").getAsString();
                existingFacets.put(facetId, facet);
            }
        }
        
        // Create new facets array with updated order and visibility
        com.google.gson.JsonArray newFacets = new com.google.gson.JsonArray();
        for (com.google.gson.JsonElement facet : facets) {
            JsonObject facetObj = facet.getAsJsonObject();
            String facetId = facetObj.get("id").getAsString();
            boolean visibility = facetObj.get("visibility").getAsBoolean();
            
            // Get existing facet data
            JsonObject existingFacet = existingFacets.get(facetId);
            if (existingFacet != null) {
                // Keep all existing data, only update visibility
                existingFacet.addProperty("visibility", visibility);
                newFacets.add(existingFacet);
                //system.out.println("Updated facet " + facetId + " visibility to " + visibility);
            }
        }
        
        // Update the configuration with new facets array
        currentConfig.add("facets", newFacets);
        currentConfig.addProperty("lastUpdated", System.currentTimeMillis());
        
        String updatedConfigJson = gson.toJson(currentConfig);
        //system.out.println("Updated config to save: " + updatedConfigJson);
        
        // Update the existing configuration
        String sql = "UPDATE app_config SET definition = ? WHERE config_key = 'UNISON_DEFAULTS'";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, updatedConfigJson);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected == 0) {
                throw new SQLException("No configuration found with key 'UNISON_DEFAULTS'");
            }
        }
    }

    private String getFacetName(String facetId) {
        // The facetId is actually the name from the database
        return facetId;
    }

    private String getFacetCategory(String facetId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Map the facet ID to the correct module name
            String moduleName = mapFacetIdToModuleName(facetId);
            if (moduleName == null) {
                return "Other";
            }
            
            // Get the module group for this facet
            String sql = "SELECT mg.primaryname FROM module m " +
                       "JOIN module_group mg ON m.group_id = mg.id " +
                       "WHERE m.primaryname = ?";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, moduleName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("primaryname");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching module group for facet: " + facetId + " - " + e.getMessage());
        }
        
        return "Other";
    }
    
    private String mapFacetIdToModuleName(String facetId) {
        // Map the facet IDs from app_config to the actual module names in the database
        switch (facetId) {
            case "DATASET": return "Data Sets";
            case "ATTRIBUTE": return "Attribute";
            case "SYSTEM": return "System";
            case "GLOSSARY": return "Glossary";
            case "DATAQUALITY": return "Data Quality";
            case "PEOPLE": return "People";
            case "ROLE": return "Role";
            case "PROCESS": return "Process";
            case "PROJECT": return "Project";
            case "POLICY": return "Policy";
            case "PRODUCT": return "Product";
            case "LEGALENTITY": return "Legal Entity";
            case "LEGAL_ENTITY": return "Legal Entity";
            case "SYSTEM_INTERFACE": return "Interface";
            case "INTERFACE": return "Interface";
            case "BUSINESS_AREA": return "Business Area";
            case "COMMITTEE": return "Committee";
            case "CLIENT": return "Client";
            case "CAPABILITY": return "Capability";
            case "ORG_UNIT": return "Org Unit";
            case "CHANGE_REQUEST": return "Change Requests";
            case "REGULATION": return "Regulation";
            case "REGULATOR": return "Regulator";
            case "JURISDICTION": return "Geography";
            case "GEOGRAPHY": return "Geography";
            case "REGULATORY_THEME": return "Regulatory Theme";
            case "FIELD": return "Physical Fields";
            case "TASK": return "Active Tasks";
            case "ACTIVE_TASKS": return "Active Tasks";
            case "ACTIVETASKS": return "Active Tasks";
            default: return null;
        }
    }
    
    /**
     * Build state map for Display Settings logging
     * Shows only "Active Facets for Unison" with list of active facet names
     */
    private Map<String, Object> buildDisplaySettingsState(JsonObject config) {
        Map<String, Object> state = new HashMap<>();
        
        if (config.has("facets") && config.get("facets").isJsonArray()) {
            com.google.gson.JsonArray facets = config.getAsJsonArray("facets");
            List<String> activeFacetNames = new ArrayList<>();
            
            for (com.google.gson.JsonElement facet : facets) {
                JsonObject facetObj = facet.getAsJsonObject();
                String facetId = facetObj.get("id").getAsString();
                boolean visibility = facetObj.get("visibility").getAsBoolean();
                
                // Only include active facets (visibility = true)
                if (visibility) {
                    String facetName = mapFacetIdToModuleName(facetId);
                    if (facetName == null) {
                        facetName = facetId;
                    }
                    activeFacetNames.add(facetName);
                }
            }
            
            // Store as single field: "Active Facets for Unison" with comma-separated list
            state.put("Active Facets for Unison", String.join(", ", activeFacetNames));
        }
        
        return state;
    }

}
