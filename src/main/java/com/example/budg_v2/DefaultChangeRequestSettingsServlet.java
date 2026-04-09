package com.example.budg_v2;

import com.example.budg_v2.dao.DFCRDao;
import com.example.budg_v2.dao.ProcessDefinitionDAO;
import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.DFCR;
import com.example.budg_v2.model.ProcessDefinition;
import com.example.budg_v2.util.ActivityLogHelper;
import com.google.gson.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.sql.Statement;

/**
 * Servlet for managing default change request settings per facet
 * Endpoint: /admin/api/default-change-requests
 * 
 * Settings are stored in DF_CR table (one row per facet)
 */
@WebServlet("/admin/api/default-change-requests")
public class DefaultChangeRequestSettingsServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DefaultChangeRequestSettingsServlet.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DFCRDao dfcrDao = new DFCRDao();
    private final ProcessDefinitionDAO processDefinitionDAO = new ProcessDefinitionDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String facet = req.getParameter("facet");

        try {
            if (facet != null && !facet.trim().isEmpty()) {
                logger.info("DFCR GET facet={} - loading settings", facet);
                // Get settings for a specific facet
                JsonObject facetSettings = getFacetSettings(facet);
                logger.info("DFCR GET facet={} - response: {}", facet, facetSettings);
                resp.getWriter().write(gson.toJson(facetSettings));
            } else {
                logger.info("DFCR GET all facets - loading settings");
                // Get all facets with their settings
                JsonObject allSettings = getAllSettings();
                logger.info("DFCR GET all facets - response keys: {}", allSettings.keySet());
                resp.getWriter().write(gson.toJson(allSettings));
            }
        } catch (SQLException e) {
            logger.error("Database error loading DF_CR settings", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try (BufferedReader reader = req.getReader()) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JsonObject requestData = JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();
            logger.info("DFCR POST payload: {}", requestData);

            if (!requestData.has("facet")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing facet parameter\"}");
                return;
            }

            String facet = requestData.get("facet").getAsString();

            // Get old state before update
            DFCR oldDfcr = dfcrDao.getByFacetName(facet);
            Map<String, Object> oldState = buildStateForLogging(oldDfcr, facet);
            logger.info("[DFCR Log] Old state keys: {}", oldState.keySet());
            logger.info("[DFCR Log] Old state: {}", oldState);

            saveFacetSettings(facet, requestData);
            
            // Clear cache for this facet to ensure changes are immediately reflected
            // This is critical for "Enable Workflow Approval for Administrators" setting
            // so that admins/super-admins immediately get the updated behavior
            com.example.budg_v2.service.DFCRService dfcrService = new com.example.budg_v2.service.DFCRService();
            dfcrService.clearCacheForFacet(facet);
            logger.info("[DFCR] Cleared cache for facet '{}' after settings update to ensure immediate effect", facet);
            
            // Get new state after update
            DFCR newDfcr = dfcrDao.getByFacetName(facet);
            Map<String, Object> newState = buildStateForLogging(newDfcr, facet);
            logger.info("[DFCR Log] New state keys: {}", newState.keySet());
            logger.info("[DFCR Log] New state: {}", newState);
            
            // Pass contextMap with facet name for component name
            Map<String, Object> contextMap = new HashMap<>();
            contextMap.put("facetName", facet);
            
            // Log activity
            ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_DEFAULT_CRS, facet,
                ActivityLogConstants.CHANGE_TYPE_UPDATE, oldState, newState, contextMap);
            
            logger.info("DFCR POST facet={} - saved successfully", facet);
            resp.getWriter().write("{\"success\":true,\"message\":\"Settings saved successfully\"}");

        } catch (SQLException e) {
            logger.error("Database error saving DF_CR settings", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            logger.error("Error processing request", e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid request: " + e.getMessage() + "\"}");
        }
    }

    private JsonObject getAllSettings() throws SQLException {
        JsonObject result = new JsonObject();
        JsonObject facets = new JsonObject();

        // Get all DF_CR records
        for (DFCR dfcr : dfcrDao.getAll()) {
            String facetName = dfcrDao.getFacetNameById(dfcr.getFacetId());
            if (facetName != null) {
                facets.add(facetName, dfcrToJson(dfcr));
            }
        }

        result.add("facets", facets);
        result.addProperty("lastUpdated", System.currentTimeMillis());
        return result;
    }

    private JsonObject getFacetSettings(String facet) throws SQLException {
        DFCR dfcr = dfcrDao.getByFacetName(facet);

        if (dfcr != null) {
            logger.info("[DFCR Load] Loaded DFCR for facet {}: {}", facet, dfcr.toString());
            logger.info("[DFCR Load] adminWorkflowBypass from DB: {}", dfcr.isAdminWorkflowBypass());
            return dfcrToJson(dfcr);
        }

        // Return default settings if facet not found
        return getDefaultSettings();
    }

    private void saveFacetSettings(String facet, JsonObject requestData) throws SQLException {
        // Ensure DF_CR table and required columns exist before attempting to upsert
        ensureDfcrSchema();

        Integer facetId = dfcrDao.getFacetIdByName(facet);
        if (facetId == null) {
            throw new SQLException("Unknown facet: " + facet);
        }

        DFCR dfcr = new DFCR();
        dfcr.setFacetId(facetId);

        // Parse workflow approval settings
        dfcr.setWorkflowApprovalEnabled(getBooleanValue(requestData, "workflowApprovalEnabled", false));
        dfcr.setWorkflowForTypesEnabled(getBooleanValue(requestData, "workflowForTypesEnabled", false));
        
        // Validate: Default Change Request System
        String crSystem = getStringValue(requestData, "defaultChangeRequestSystem", null);
        if (crSystem == null || crSystem.isEmpty()) {
            throw new IllegalArgumentException("Please select a Default Change Request System");
        }
        dfcr.setCrSystem(crSystem);

        // Parse workflow IDs
        Integer wfCreate = getIntegerValue(requestData, "defaultWorkflowForCreating");
        Integer wfEdit = getIntegerValue(requestData, "defaultWorkflowForEditing");
        
        // Validate: At least one workflow must be selected
        if (wfCreate == null && wfEdit == null) {
            throw new IllegalArgumentException("Please select at least one workflow (for creating or editing objects)");
        }
        
        dfcr.setWorkflowCreateId(wfCreate);
        dfcr.setWorkflowEditId(wfEdit);
        // Backward compatibility: also fill legacy process_definition_id with create workflow if provided
        dfcr.setProcessDefinitionId(wfCreate != null ? wfCreate : wfEdit);

        // Parse and validate status ID
        Integer statusId = getIntegerValue(requestData, "defaultBUDGStatusForCreating");
        if (statusId == null) {
            throw new IllegalArgumentException("Please select a Default BUDG Status for Creating Object");
        }
        dfcr.setStatusId(statusId);

        // Parse and validate lifecycle as combined string (e.g., "glossary_3")
        String lifecycleValue = getStringValue(requestData, "defaultLifecycleForCreating", null);
        if (lifecycleValue == null || lifecycleValue.isEmpty()) {
            throw new IllegalArgumentException("Please select a Default Lifecycle for Creating Object");
        }
        dfcr.setLifecycleTable(lifecycleValue);

        // Parse and validate CR defaults
        Integer crTypeId = getIntegerValue(requestData, "defaultChangeRequestType");
        if (crTypeId == null) {
            throw new IllegalArgumentException("Please select a Default Change Request Type");
        }
        dfcr.setCrTypeId(crTypeId);
        
        Integer crUrgencyId = getIntegerValue(requestData, "defaultChangeRequestUrgency");
        if (crUrgencyId == null) {
            throw new IllegalArgumentException("Please select a Default Change Request Urgency");
        }
        dfcr.setCrUrgencyId(crUrgencyId);
        
        Integer crSeverityId = getIntegerValue(requestData, "defaultChangeRequestSeverity");
        if (crSeverityId == null) {
            throw new IllegalArgumentException("Please select a Default Change Request Severity");
        }
        dfcr.setCrSeverityId(crSeverityId);

        // Parse admin bypass (inverted: UI shows "enable for admins", DB stores "bypass")
        // If "enableWorkflowApprovalForAdministrators" is true, admins follow workflow (bypass = false)
        // If "enableWorkflowApprovalForAdministrators" is false, admins bypass workflow (bypass = true)
        boolean enableForAdmins = getBooleanValue(requestData, "enableWorkflowApprovalForAdministrators", false);
        boolean adminBypass = !enableForAdmins;
        dfcr.setAdminWorkflowBypass(adminBypass);
        
        logger.info("[DFCR Save] enableWorkflowApprovalForAdministrators from UI: {}", enableForAdmins);
        logger.info("[DFCR Save] adminWorkflowBypass to save in DB: {}", adminBypass);

        // Legacy flags: set can_create/can_read based on presence of workflow selections
        dfcr.setCanCreate(wfCreate != null ? 1 : 0);
        dfcr.setCanRead(wfEdit != null ? 1 : 0);

        // Log the full DFCR object before saving
        logger.info("[DFCR Save] Full DFCR object to save: {}", dfcr.toString());

        // Upsert to database
        dfcrDao.upsert(dfcr);
        
        logger.info("[DFCR Save] Successfully saved DFCR settings for facet: {}", facet);
    }

    private JsonObject dfcrToJson(DFCR dfcr) {
        JsonObject json = new JsonObject();
        json.addProperty("workflowApprovalEnabled", dfcr.isWorkflowApprovalEnabled());
        json.addProperty("workflowForTypesEnabled", dfcr.isWorkflowForTypesEnabled());
        json.addProperty("defaultChangeRequestSystem", dfcr.getCrSystem() != null ? dfcr.getCrSystem() : "Native");
        json.addProperty("defaultWorkflowForCreating", dfcr.getWorkflowCreateId() != null ? String.valueOf(dfcr.getWorkflowCreateId()) : "");
        json.addProperty("defaultWorkflowForEditing", dfcr.getWorkflowEditId() != null ? String.valueOf(dfcr.getWorkflowEditId()) : "");
        json.addProperty("defaultBUDGStatusForCreating", dfcr.getStatusId() != null ? String.valueOf(dfcr.getStatusId()) : "");
        // Return lifecycle as stored (combined string e.g., "glossary_3")
        String lifecycleValue = dfcr.getLifecycleTable() != null ? dfcr.getLifecycleTable() : "";
        json.addProperty("defaultLifecycleForCreating", lifecycleValue);
        json.addProperty("defaultChangeRequestType", dfcr.getCrTypeId() != null ? String.valueOf(dfcr.getCrTypeId()) : "");
        json.addProperty("defaultChangeRequestUrgency", dfcr.getCrUrgencyId() != null ? String.valueOf(dfcr.getCrUrgencyId()) : "");
        json.addProperty("defaultChangeRequestSeverity", dfcr.getCrSeverityId() != null ? String.valueOf(dfcr.getCrSeverityId()) : "");
        // Invert bypass flag for UI display
        json.addProperty("enableWorkflowApprovalForAdministrators", !dfcr.isAdminWorkflowBypass());
        return json;
    }

    private JsonObject getDefaultSettings() {
        JsonObject defaultSettings = new JsonObject();
        defaultSettings.addProperty("workflowApprovalEnabled", false);
        defaultSettings.addProperty("workflowForTypesEnabled", false);
        defaultSettings.addProperty("defaultChangeRequestSystem", "Native");
        defaultSettings.addProperty("defaultWorkflowForCreating", "");
        defaultSettings.addProperty("defaultWorkflowForEditing", "");
        defaultSettings.addProperty("defaultBUDGStatusForCreating", "");
        defaultSettings.addProperty("defaultLifecycleForCreating", "");
        defaultSettings.addProperty("defaultChangeRequestType", "");
        defaultSettings.addProperty("defaultChangeRequestUrgency", "");
        defaultSettings.addProperty("defaultChangeRequestSeverity", "");
        defaultSettings.addProperty("enableWorkflowApprovalForAdministrators", false);
        return defaultSettings;
    }

    // Helper methods

    private boolean getBooleanValue(JsonObject json, String key, boolean defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsBoolean();
        }
        return defaultValue;
    }

    private String getStringValue(JsonObject json, String key, String defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            String value = json.get(key).getAsString();
            return value.isEmpty() ? defaultValue : value;
        }
        return defaultValue;
    }

    private Integer getIntegerValue(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            String value = json.get(key).getAsString();
            if (value != null && !value.isEmpty()) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Ensure DF_CR table and required columns exist.
     * This protects against 500s if migrations haven't been applied yet.
     */
    private void ensureDfcrSchema() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection(); Statement stmt = conn.createStatement()) {
            // Create table if missing (matches migration 005_create_df_cr_table.sql)
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS `DF_CR` (
                  `id` INT NOT NULL AUTO_INCREMENT,
                  `facet_id` INT NOT NULL,
                  `status_id` INT NULL,
                  `lifecycle_table` VARCHAR(255) NULL,
                  `lifecycle_id` INT NULL,
                  `cr_type_id` INT NULL,
                  `cr_urgency_id` INT NULL,
                  `cr_severity_id` INT NULL,
                  `workflow_create_id` INT NULL,
                  `workflow_edit_id` INT NULL,
                  `cr_system` ENUM('Native', 'ServiceNow', 'JIRA') NOT NULL DEFAULT 'Native',
                  `workflow_approval_enabled` TINYINT(1) NOT NULL DEFAULT 0,
                  `workflow_for_types_enabled` TINYINT(1) NOT NULL DEFAULT 0,
                  `admin_workflow_bypass` TINYINT(1) NOT NULL DEFAULT 1,
                  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uq_df_cr_facet` (`facet_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """;
            stmt.executeUpdate(createTableSql);

            // Ensure columns exist (works on MySQL 5.7/8; swallow "duplicate column" errors)
            addColumnIfMissing(conn, "workflow_create_id", "INT NULL");
            addColumnIfMissing(conn, "workflow_edit_id", "INT NULL");
            addColumnIfMissing(conn, "cr_system", "ENUM('Native','ServiceNow','JIRA') NOT NULL DEFAULT 'Native'");
            addColumnIfMissing(conn, "workflow_approval_enabled", "TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "workflow_for_types_enabled", "TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "admin_workflow_bypass", "TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing(conn, "process_definition_id", "INT NULL");
            addColumnIfMissing(conn, "can_create", "TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "can_read", "TINYINT(1) NOT NULL DEFAULT 0");
            
            // Ensure lifecycle column is wide enough and nullable
            modifyColumnIfNeeded(conn, "lifecycle_table", "VARCHAR(255) NULL");
            modifyColumnIfExists(conn, "lifecycle_id", "INT NULL");
        }
    }
    
    private void modifyColumnIfNeeded(Connection conn, String columnName, String definition) throws SQLException {
        // Try to modify the column to allow NULL (will fail silently if already correct or column doesn't exist)
        String sql = "ALTER TABLE DF_CR MODIFY COLUMN " + columnName + " " + definition;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            // Ignore errors - column might already be correct or might not exist yet
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("unknown column") || msg.contains("duplicate column")) {
                return;
            }
            // Log but don't throw - this is a best-effort fix
            logger.debug("Could not modify column {}: {}", columnName, e.getMessage());
        }
    }

    private void modifyColumnIfExists(Connection conn, String columnName, String definition) throws SQLException {
        String sql = "ALTER TABLE DF_CR MODIFY COLUMN " + columnName + " " + definition;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("unknown column") || msg.contains("doesn't exist")) {
                return;
            }
            if (msg.contains("duplicate column") || msg.contains("already exists")) {
                return;
            }
            logger.debug("Could not modify column (exists) {}: {}", columnName, e.getMessage());
        }
    }

    private void addColumnIfMissing(Connection conn, String columnName, String definition) throws SQLException {
        String sql = "ALTER TABLE DF_CR ADD COLUMN " + columnName + " " + definition;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            // Ignore if column already exists or syntax not supported with IF NOT EXISTS
            if (msg.contains("duplicate column") || msg.contains("already exists")) {
                return;
            }
            throw e;
        }
    }
    
    /**
     * Build state map for logging - converts IDs to names for better readability
     * Ensures ALL fields are included for comprehensive activity logging
     */
    private Map<String, Object> buildStateForLogging(DFCR dfcr, String facet) {
        Map<String, Object> state = new HashMap<>();
        if (dfcr == null) {
            // Return empty state with all fields set to empty/null for proper comparison
            state.put("workflowApprovalEnabled", false);
            state.put("workflowForTypesEnabled", false);
            state.put("defaultChangeRequestSystem", "Native");
            state.put("defaultWorkflowForCreating", "");
            state.put("defaultWorkflowForEditing", "");
            state.put("defaultBUDGStatusForCreating", "");
            state.put("defaultLifecycleForCreating", "");
            state.put("defaultChangeRequestType", "");
            state.put("defaultChangeRequestUrgency", "");
            state.put("defaultChangeRequestSeverity", "");
            state.put("enableWorkflowApprovalForAdministrators", false);
            return state;
        }
        
        // Boolean fields
        state.put("workflowApprovalEnabled", dfcr.isWorkflowApprovalEnabled());
        state.put("workflowForTypesEnabled", dfcr.isWorkflowForTypesEnabled());
        
        // System field
        state.put("defaultChangeRequestSystem", dfcr.getCrSystem() != null ? dfcr.getCrSystem() : "Native");
        
        // Convert workflow IDs to names
        String workflowForCreating = getWorkflowName(dfcr.getWorkflowCreateId());
        String workflowForEditing = getWorkflowName(dfcr.getWorkflowEditId());
        state.put("defaultWorkflowForCreating", workflowForCreating != null ? workflowForCreating : "");
        state.put("defaultWorkflowForEditing", workflowForEditing != null ? workflowForEditing : "");
        
        // Convert status ID to name
        String statusName = getStatusName(dfcr.getStatusId());
        state.put("defaultBUDGStatusForCreating", statusName != null ? statusName : "");
        
        // Convert lifecycle string (e.g., "glossary_3") to readable name
        String lifecycle = getLifecycleName(dfcr.getLifecycleTable(), facet);
        state.put("defaultLifecycleForCreating", lifecycle != null ? lifecycle : "");
        
        // Convert CR type/urgency/severity IDs to names
        String crTypeName = getCRTypeName(dfcr.getCrTypeId());
        String crUrgencyName = getCRUrgencyName(dfcr.getCrUrgencyId());
        String crSeverityName = getCRSeverityName(dfcr.getCrSeverityId());
        state.put("defaultChangeRequestType", crTypeName != null ? crTypeName : "");
        state.put("defaultChangeRequestUrgency", crUrgencyName != null ? crUrgencyName : "");
        state.put("defaultChangeRequestSeverity", crSeverityName != null ? crSeverityName : "");
        
        // Admin bypass (inverted for UI display)
        state.put("enableWorkflowApprovalForAdministrators", !dfcr.isAdminWorkflowBypass());
        
        return state;
    }
    
    /**
     * Get workflow name from ID, or "Select a workflow" if null/empty
     */
    private String getWorkflowName(Integer workflowId) {
        if (workflowId == null) {
            return "Select a workflow";
        }
        try {
            ProcessDefinition pd = processDefinitionDAO.findById(workflowId);
            if (pd != null && pd.getPrimaryName() != null) {
                return pd.getPrimaryName();
            }
        } catch (SQLException e) {
            logger.warn("Error getting workflow name for ID {}: {}", workflowId, e.getMessage());
        }
        return "Select a workflow";
    }
    
    /**
     * Get status name from ID
     */
    private String getStatusName(Integer statusId) {
        if (statusId == null) {
            return "";
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT PrimaryName FROM status WHERE ID = ?")) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting status name for ID {}: {}", statusId, e.getMessage());
        }
        return "";
    }
    
    /**
     * Get CR type name from ID
     */
    private String getCRTypeName(Integer typeId) {
        if (typeId == null) {
            return "";
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT PrimaryName FROM changerequest_type WHERE ID = ?")) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting CR type name for ID {}: {}", typeId, e.getMessage());
        }
        return "";
    }
    
    /**
     * Get CR urgency name from ID
     */
    private String getCRUrgencyName(Integer urgencyId) {
        if (urgencyId == null) {
            return "";
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT PrimaryName FROM changerequest_urgency WHERE ID = ?")) {
            ps.setInt(1, urgencyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting CR urgency name for ID {}: {}", urgencyId, e.getMessage());
        }
        return "";
    }
    
    /**
     * Get CR severity name from ID
     */
    private String getCRSeverityName(Integer severityId) {
        if (severityId == null) {
            return "";
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT PrimaryName FROM changerequest_severity WHERE ID = ?")) {
            ps.setInt(1, severityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting CR severity name for ID {}: {}", severityId, e.getMessage());
        }
        return "";
    }
    
    /**
     * Get lifecycle name from lifecycle table string (e.g., "glossary_3" -> "Lifecycle Name")
     * Returns readable name or the original string if name cannot be found
     */
    private String getLifecycleName(String lifecycleTable, String facet) {
        if (lifecycleTable == null || lifecycleTable.isEmpty()) {
            return "";
        }
        
        try {
            // Parse lifecycle string (format: "facetKey_id" or just "id")
            String[] parts = lifecycleTable.split("_");
            if (parts.length < 2) {
                // Try to parse as just ID
                try {
                    int lifecycleId = Integer.parseInt(lifecycleTable);
                    return getLifecycleNameById(lifecycleId, facet);
                } catch (NumberFormatException e) {
                    return lifecycleTable; // Return as-is if can't parse
                }
            }
            
            // Extract ID from "facetKey_id" format
            try {
                int lifecycleId = Integer.parseInt(parts[parts.length - 1]);
                return getLifecycleNameById(lifecycleId, facet);
            } catch (NumberFormatException e) {
                return lifecycleTable; // Return as-is if can't parse ID
            }
        } catch (Exception e) {
            logger.warn("Error parsing lifecycle string '{}': {}", lifecycleTable, e.getMessage());
            return lifecycleTable; // Return original string on error
        }
    }
    
    /**
     * Get lifecycle name by ID and facet
     */
    private String getLifecycleNameById(int lifecycleId, String facet) {
        try {
            // Map facet name to lifecycle table name
            String lifecycleTableName = getLifecycleTableName(facet);
            if (lifecycleTableName == null) {
                return "Lifecycle " + lifecycleId;
            }
            
            // Determine the correct column name based on the table
            String columnName = getLifecycleColumnName(lifecycleTableName);
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + columnName + " FROM " + lifecycleTableName + " WHERE ID = ?")) {
                ps.setInt(1, lifecycleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString(columnName);
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting lifecycle name for ID {} in facet {}: {}", lifecycleId, facet, e.getMessage());
        }
        return "Lifecycle " + lifecycleId;
    }
    
    /**
     * Get the correct column name for lifecycle table
     */
    private String getLifecycleColumnName(String tableName) {
        switch (tableName.toLowerCase()) {
            case "dataset_lifecycle":
                return "PrimaryName";
            case "glossary_lifecycle":
                return "Name";
            case "system_lifecycle":
                return "Name";
            case "process_lifecycle_status":
                return "primaryname";
            default:
                // Default to PrimaryName, fallback to Name if that fails
                return "PrimaryName";
        }
    }
    
    /**
     * Get lifecycle table name from facet name
     */
    private String getLifecycleTableName(String facet) {
        // Map facet names to their lifecycle table names
        switch (facet.toLowerCase()) {
            case "glossary":
                return "glossary_lifecycle";
            case "dataset":
            case "data set":
                return "dataset_lifecycle";
            case "system":
                return "system_lifecycle";
            case "process":
                return "process_lifecycle_status";
            case "capability":
                return "capability_lifecycle";
            case "client":
                return "client_lifecycle";
            case "product":
                return "product_lifecycle";
            case "system-interface":
            case "system interface":
                return "system_interface_lifecycle";
            case "policy":
                return "policy_lifecycle";
            case "committee":
                return "committee_lifecycle";
            case "business-area":
            case "business area":
                return "business_area_lifecycle";
            default:
                // Try to construct table name from facet
                String tableName = facet.toLowerCase().replace(" ", "_").replace("-", "_") + "_lifecycle";
                return tableName;
        }
    }
}
