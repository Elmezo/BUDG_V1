package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet(name = "DropdownConfigServlet", urlPatterns = {"/admin/api/dropdown-config/*"})
public class DropdownConfigServlet extends HttpServlet {

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

        String pathInfo = req.getPathInfo();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // GET /admin/api/dropdown-config - Get all dropdown configs
            if (pathInfo == null || pathInfo.equals("/")) {
                String moduleId = req.getParameter("moduleId");
                String updatedByUserId = req.getParameter("updatedByUserId");
                List<Map<String, Object>> configs = getDropdownConfigs(conn, moduleId, updatedByUserId);
                sendSuccessResponse(resp, configs);
                return;
            }

            // GET /admin/api/dropdown-config/values?configId=X - Get dropdown values
            if (pathInfo.equals("/values")) {
                String configIdStr = req.getParameter("configId");
                if (configIdStr == null) {
                    sendErrorResponse(resp, "configId parameter is required", HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                
                int configId = Integer.parseInt(configIdStr);
                List<Map<String, Object>> values = getDropdownValues(conn, configId);
                sendSuccessResponse(resp, values);
                return;
            }

            sendErrorResponse(resp, "Invalid endpoint", HttpServletResponse.SC_NOT_FOUND);

        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(resp, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (NumberFormatException e) {
            sendErrorResponse(resp, "Invalid parameter format", HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // POST /admin/api/dropdown-config/values - Add new dropdown value
            if (pathInfo != null && pathInfo.equals("/values")) {
                JsonObject requestBody = parseRequestBody(req);
                boolean success = addDropdownValue(conn, requestBody, req);
                
                if (success) {
                    sendSuccessResponse(resp, Map.of("message", "Value added successfully"));
                } else {
                    sendErrorResponse(resp, "Failed to add value", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
                return;
            }

            sendErrorResponse(resp, "Invalid endpoint", HttpServletResponse.SC_NOT_FOUND);

        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(resp, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // PUT /admin/api/dropdown-config/values/{id} - Update dropdown value
            if (pathInfo != null && pathInfo.startsWith("/values/")) {
                String valueIdStr = pathInfo.substring("/values/".length());
                int valueId = Integer.parseInt(valueIdStr);
                
                JsonObject requestBody = parseRequestBody(req);
                boolean success = updateDropdownValue(conn, valueId, requestBody, req);
                
                if (success) {
                    sendSuccessResponse(resp, Map.of("message", "Value updated successfully"));
                } else {
                    sendErrorResponse(resp, "Failed to update value", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
                return;
            }

            sendErrorResponse(resp, "Invalid endpoint", HttpServletResponse.SC_NOT_FOUND);

        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(resp, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (NumberFormatException e) {
            sendErrorResponse(resp, "Invalid parameter format", HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // DELETE /admin/api/dropdown-config/values/{id}?configId=X - Delete dropdown value
            if (pathInfo != null && pathInfo.startsWith("/values/")) {
                String valueIdStr = pathInfo.substring("/values/".length());
                String configIdStr = req.getParameter("configId");
                
                if (configIdStr == null) {
                    sendErrorResponse(resp, "configId parameter is required", HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                
                int valueId = Integer.parseInt(valueIdStr);
                int configId = Integer.parseInt(configIdStr);
                
                Map<String, Object> result = deleteDropdownValue(conn, configId, valueId, req);
                
                if ((Boolean) result.get("success")) {
                    sendSuccessResponse(resp, result);
                } else {
                    sendErrorResponse(resp, (String) result.get("error"), HttpServletResponse.SC_BAD_REQUEST);
                }
                return;
            }

            sendErrorResponse(resp, "Invalid endpoint", HttpServletResponse.SC_NOT_FOUND);

        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(resp, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (NumberFormatException e) {
            sendErrorResponse(resp, "Invalid parameter format", HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    // ==================== Helper Methods ====================

    private List<Map<String, Object>> getDropdownConfigs(Connection conn, String moduleId, String updatedByUserId) throws SQLException {
        List<Map<String, Object>> configs = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder(
            "SELECT dc.id, dc.dropdown_display_name, dc.table_name, dc.column_id, " +
            "dc.column_name, dc.column_description, dc.is_relation_type, " +
            "dc.is_protected, dc.sort_order, dc.updated_at, " +
            "COALESCE(m.primaryname, 'General') as module_name " +
            "FROM dropdown_config dc " +
            "LEFT JOIN module m ON dc.module_id = m.id "
        );
        
        List<String> conditions = new ArrayList<>();
        if (moduleId != null && !moduleId.isEmpty()) {
            conditions.add("dc.module_id = ?");
        }
        
        if (conditions.size() > 0) {
            sql.append("WHERE ").append(String.join(" AND ", conditions));
        }
        
        sql.append(" ORDER BY COALESCE(m.primaryname, 'General'), dc.sort_order");
        
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            if (moduleId != null && !moduleId.isEmpty()) {
                stmt.setInt(paramIndex++, Integer.parseInt(moduleId));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    
                    // If filtering by user, check if this table has values updated by that user
                    if (updatedByUserId != null && !updatedByUserId.isEmpty()) {
                        if (!hasValuesUpdatedByUser(conn, tableName, Integer.parseInt(updatedByUserId))) {
                            continue; // Skip this config if no values were updated by this user
                        }
                    }
                    
                    Map<String, Object> config = new HashMap<>();
                    config.put("id", rs.getInt("id"));
                    config.put("dropdownDisplayName", rs.getString("dropdown_display_name"));
                    config.put("tableName", tableName);
                    config.put("columnId", rs.getString("column_id"));
                    config.put("columnName", rs.getString("column_name"));
                    config.put("columnDescription", rs.getString("column_description"));
                    config.put("moduleName", rs.getString("module_name"));
                    config.put("isRelationType", rs.getBoolean("is_relation_type"));
                    config.put("isProtected", rs.getBoolean("is_protected"));
                    config.put("sortOrder", rs.getInt("sort_order"));
                    config.put("updatedAt", rs.getTimestamp("updated_at"));
                    
                    configs.add(config);
                }
            }
        }
        
        return configs;
    }
    
    /**
     * Checks if a table has any values updated by a specific user
     */
    private boolean hasValuesUpdatedByUser(Connection conn, String tableName, int userId) throws SQLException {
        // Find the user ID column in the table
        String userIdColumn = findUserIdColumn(conn, tableName);
        
        // If table doesn't have a user ID column, return false
        if (userIdColumn == null) {
            return false;
        }
        
        // Check if any rows in the table have this user ID
        String sql = String.format("SELECT COUNT(*) as cnt FROM `%s` WHERE `%s` = ?", tableName, userIdColumn);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt") > 0;
                }
            }
        }
        
        return false;
    }

    private List<Map<String, Object>> getDropdownValues(Connection conn, int configId) throws SQLException {
        // First, get the dropdown config details
        Map<String, Object> config = getDropdownConfigById(conn, configId);
        if (config == null) {
            return new ArrayList<>();
        }
        
        String tableName = (String) config.get("tableName");
        String columnId = (String) config.get("columnId");
        String columnName = (String) config.get("columnName");
        String columnDescription = (String) config.get("columnDescription");
        
        // Find datetime and user ID columns if they exist
        String datetimeColumn = findDatetimeColumn(conn, tableName);
        String userIdColumn = findUserIdColumn(conn, tableName);
        
        // Build dynamic SELECT clause
        StringBuilder selectClause = new StringBuilder();
        selectClause.append("t.`").append(columnId).append("` as id, ");
        selectClause.append("t.`").append(columnName).append("` as name, ");
        selectClause.append(columnDescription != null && !columnDescription.equals("null") ? 
            "t.`" + columnDescription + "`" : "NULL").append(" as description");
        
        if (datetimeColumn != null) {
            selectClause.append(", t.`").append(datetimeColumn).append("` as lastUpdated");
        }
        if (userIdColumn != null) {
            selectClause.append(", t.`").append(userIdColumn).append("` as updatedByUserId");
        }
        
        // Build query with optional JOIN to people table for user name
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(selectClause);
        if (userIdColumn != null) {
            sql.append(", COALESCE(CONCAT(p.First_Name, ' ', p.Last_Name), CONCAT('User ', t.`").append(userIdColumn).append("`)) as updatedByName");
        }
        sql.append(" FROM `").append(tableName).append("` t");
        if (userIdColumn != null) {
            sql.append(" LEFT JOIN `people` p ON t.`").append(userIdColumn).append("` = p.ID");
        }
        sql.append(" ORDER BY t.`").append(columnName).append("`");
        
        List<Map<String, Object>> values = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString());
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> value = new HashMap<>();
                value.put("id", rs.getObject("id"));
                value.put("name", rs.getString("name"));
                value.put("description", rs.getString("description"));
                
                if (datetimeColumn != null) {
                    Timestamp ts = rs.getTimestamp("lastUpdated");
                    value.put("lastUpdated", ts != null ? ts.toString() : null);
                }
                if (userIdColumn != null) {
                    value.put("updatedByUserId", rs.getObject("updatedByUserId"));
                    value.put("updatedByName", rs.getString("updatedByName"));
                }
                
                // Get usage count
                int usageCount = getUsageCount(conn, tableName, rs.getObject("id"));
                value.put("usageCount", usageCount);
                
                values.add(value);
            }
        }
        
        return values;
    }

    private Map<String, Object> getDropdownConfigById(Connection conn, int configId) throws SQLException {
        String sql = "SELECT dc.table_name, dc.column_id, dc.column_name, dc.column_description, " +
                     "dc.is_relation_type, dc.is_protected, dc.dropdown_display_name, " +
                     "COALESCE(m.primaryname, 'General') as module_name " +
                     "FROM dropdown_config dc " +
                     "LEFT JOIN module m ON dc.module_id = m.id " +
                     "WHERE dc.id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, configId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> config = new HashMap<>();
                    config.put("tableName", rs.getString("table_name"));
                    config.put("columnId", rs.getString("column_id"));
                    config.put("columnName", rs.getString("column_name"));
                    config.put("columnDescription", rs.getString("column_description"));
                    config.put("isRelationType", rs.getBoolean("is_relation_type"));
                    config.put("isProtected", rs.getBoolean("is_protected"));
                    config.put("dropdownDisplayName", rs.getString("dropdown_display_name"));
                    config.put("moduleName", rs.getString("module_name"));
                    return config;
                }
            }
        }
        return null;
    }

    private int getUsageCount(Connection conn, String tableName, Object valueId) {
        try {
            // Query INFORMATION_SCHEMA to find foreign key references
            String fkQuery = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                           "WHERE REFERENCED_TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE() " +
                           "AND REFERENCED_TABLE_SCHEMA = DATABASE()";
            
            int totalCount = 0;
            try (PreparedStatement stmt = conn.prepareStatement(fkQuery)) {
                stmt.setString(1, tableName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String refTable = rs.getString("TABLE_NAME");
                        String refColumn = rs.getString("COLUMN_NAME");
                        
                        // Count usage in this table
                        String countSql = String.format("SELECT COUNT(*) as cnt FROM `%s` WHERE `%s` = ?", refTable, refColumn);
                        try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                            countStmt.setObject(1, valueId);
                            try (ResultSet countRs = countStmt.executeQuery()) {
                                if (countRs.next()) {
                                    totalCount += countRs.getInt("cnt");
                                }
                            }
                        } catch (SQLException e) {
                            // Table might not exist or column might be wrong, continue
                        }
                    }
                }
            }
            return totalCount;
        } catch (SQLException e) {
            return 0;
        }
    }

    private boolean addDropdownValue(Connection conn, JsonObject requestBody, HttpServletRequest req) throws SQLException {
        int configId = requestBody.get("configId").getAsInt();
        String name = requestBody.get("name").getAsString().trim();
        String description = requestBody.has("description") ? requestBody.get("description").getAsString().trim() : "";
        
        Map<String, Object> config = getDropdownConfigById(conn, configId);
        if (config == null) {
            return false;
        }
        
        String tableName = (String) config.get("tableName");
        String columnName = (String) config.get("columnName");
        String columnDescription = (String) config.get("columnDescription");
        String dropdownDisplayName = (String) config.get("dropdownDisplayName");
        
        // Get current user ID from session
        Integer userId = getUserIdFromRequest(req);
        
        // Check which user ID column exists in the table (if any)
        String userIdColumn = findUserIdColumn(conn, tableName);
        // Check which datetime column exists in the table (if any)
        String datetimeColumn = findDatetimeColumn(conn, tableName);
        
        // Build INSERT statement dynamically based on what columns exist
        StringBuilder columnsBuilder = new StringBuilder();
        StringBuilder valuesBuilder = new StringBuilder();
        
        columnsBuilder.append("`").append(columnName).append("`");
        valuesBuilder.append("?");
        
        // Add description column if it exists
        if (columnDescription != null && !columnDescription.equals("null")) {
            columnsBuilder.append(", `").append(columnDescription).append("`");
            valuesBuilder.append(", ?");
        }
        
        // Add datetime column if it exists
        if (datetimeColumn != null) {
            columnsBuilder.append(", `").append(datetimeColumn).append("`");
            valuesBuilder.append(", NOW()");
        }
        
        // Add user ID column if it exists
        if (userIdColumn != null) {
            columnsBuilder.append(", `").append(userIdColumn).append("`");
            valuesBuilder.append(", ?");
        }
        
        String sql = String.format(
            "INSERT INTO `%s` (%s) VALUES (%s)",
            tableName, columnsBuilder.toString(), valuesBuilder.toString()
        );
        
        boolean success = false;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, name);
            
            // Set description if column exists
            if (columnDescription != null && !columnDescription.equals("null")) {
                stmt.setString(paramIndex++, description);
            }
            
            // Set user ID if column exists
            if (userIdColumn != null) {
                stmt.setObject(paramIndex++, userId);
            }
            
            success = stmt.executeUpdate() > 0;
        }
        
        // Log activity - Create case
        if (success) {
            Map<String, Object> newState = new HashMap<>();
            newState.put("name", name);
            if (description != null && !description.isEmpty()) {
                newState.put("description", description);
            }
            
            // Component name format: "{dropdownDisplayName} - {valueName}"
            String componentName = dropdownDisplayName != null && !dropdownDisplayName.trim().isEmpty() 
                ? dropdownDisplayName + " - " + name
                : "Unknown Field";
            
            // Pass contextMap with fieldName for component name
            Map<String, Object> contextMap = new HashMap<>();
            contextMap.put("fieldName", componentName);
            
            ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_DROPDOWN_CONFIG,
                componentName, ActivityLogConstants.CHANGE_TYPE_CREATE,
                null, newState, contextMap);
        }
        
        return success;
    }

    private boolean updateDropdownValue(Connection conn, int valueId, JsonObject requestBody, HttpServletRequest req) throws SQLException {
        int configId = requestBody.get("configId").getAsInt();
        String name = requestBody.get("name").getAsString().trim();
        String description = requestBody.has("description") ? requestBody.get("description").getAsString().trim() : "";
        
        Map<String, Object> config = getDropdownConfigById(conn, configId);
        if (config == null) {
            return false;
        }
        
        String tableName = (String) config.get("tableName");
        String columnId = (String) config.get("columnId");
        String columnName = (String) config.get("columnName");
        String columnDescription = (String) config.get("columnDescription");
        String dropdownDisplayName = (String) config.get("dropdownDisplayName");
        
        // Capture old state before update
        Map<String, Object> oldState = getDropdownValueState(conn, tableName, columnId, columnName, columnDescription, valueId);
        
        Integer userId = getUserIdFromRequest(req);
        
        // Check which user ID column exists in the table (if any)
        String userIdColumn = findUserIdColumn(conn, tableName);
        // Check which datetime column exists in the table (if any)
        String datetimeColumn = findDatetimeColumn(conn, tableName);
        
        // Build UPDATE statement dynamically based on what columns exist
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("UPDATE `").append(tableName).append("` SET ");
        sqlBuilder.append("`").append(columnName).append("` = ?");
        
        // Add description column if it exists
        if (columnDescription != null && !columnDescription.equals("null")) {
            sqlBuilder.append(", `").append(columnDescription).append("` = ?");
        }
        
        // Add datetime column if it exists
        if (datetimeColumn != null) {
            sqlBuilder.append(", `").append(datetimeColumn).append("` = NOW()");
        }
        
        // Add user ID column if it exists
        if (userIdColumn != null) {
            sqlBuilder.append(", `").append(userIdColumn).append("` = ?");
        }
        
        sqlBuilder.append(" WHERE `").append(columnId).append("` = ?");
        
        String sql = sqlBuilder.toString();
        
        boolean success = false;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, name);
            
            // Set description if column exists
            if (columnDescription != null && !columnDescription.equals("null")) {
                stmt.setString(paramIndex++, description);
            }
            
            // Set user ID if column exists
            if (userIdColumn != null) {
                stmt.setObject(paramIndex++, userId);
            }
            
            stmt.setInt(paramIndex, valueId);
            success = stmt.executeUpdate() > 0;
        }
        
        // Log activity - Update case (only if there are actual changes)
        if (success && oldState != null && !oldState.isEmpty()) {
            Map<String, Object> newState = new HashMap<>();
            newState.put("name", name);
            if (description != null && !description.isEmpty()) {
                newState.put("description", description);
            }
            
            // Check if there are actual changes before logging
            boolean hasChanges = false;
            Object oldName = oldState.get("name");
            Object oldDescription = oldState.get("description");
            
            if (!areEqual(oldName, name)) {
                hasChanges = true;
            } else if (oldDescription != null || (description != null && !description.isEmpty())) {
                if (!areEqual(oldDescription, description)) {
                    hasChanges = true;
                }
            }
            
            // Only log if there are actual changes
            if (hasChanges) {
                // Component name format: "{dropdownDisplayName} - {valueName}"
                // Use new name for component (in case name was changed)
                String componentName = dropdownDisplayName != null && !dropdownDisplayName.trim().isEmpty() 
                    ? dropdownDisplayName + " - " + name
                    : "Unknown Field";
                
                // Pass contextMap with fieldName for component name
                Map<String, Object> contextMap = new HashMap<>();
                contextMap.put("fieldName", componentName);
                
                ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_DROPDOWN_CONFIG,
                    componentName, ActivityLogConstants.CHANGE_TYPE_UPDATE,
                    oldState, newState, contextMap);
            }
        }
        
        return success;
    }

    private Map<String, Object> deleteDropdownValue(Connection conn, int configId, int valueId, HttpServletRequest req) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        
        Map<String, Object> config = getDropdownConfigById(conn, configId);
        if (config == null) {
            result.put("success", false);
            result.put("error", "Configuration not found");
            return result;
        }
        
        String tableName = (String) config.get("tableName");
        String columnId = (String) config.get("columnId");
        String columnName = (String) config.get("columnName");
        String columnDescription = (String) config.get("columnDescription");
        String dropdownDisplayName = (String) config.get("dropdownDisplayName");
        boolean isRelationType = (Boolean) config.get("isRelationType");
        boolean isProtected = (Boolean) config.get("isProtected");
        
        // Capture old state before deletion
        Map<String, Object> oldState = getDropdownValueState(conn, tableName, columnId, columnName, columnDescription, valueId);
        
        // Check if it's the last value
        int totalCount = getTotalValuesCount(conn, tableName);
        if (totalCount <= 1) {
            result.put("success", false);
            result.put("error", "Cannot delete the last value in this dropdown");
            return result;
        }
        
        // Check usage
        int usageCount = getUsageCount(conn, tableName, valueId);
        
        if (usageCount > 0) {
            if (isRelationType) {
                // Relation type: warn but allow (return warning flag)
                result.put("warning", true);
                result.put("usageCount", usageCount);
                result.put("message", "This relation type is used in " + usageCount + " relationship(s). " +
                                     "If you delete it, you'll get errors when editing those relationships without changing the type.");
            } else if (isProtected) {
                // Protected: block deletion
                result.put("success", false);
                result.put("error", "Cannot delete values in use in object(s). Remove the value from the object(s) first.");
                result.put("usageCount", usageCount);
                return result;
            }
        }
        
        // Proceed with deletion
        String sql = String.format("DELETE FROM `%s` WHERE `%s` = ?", tableName, columnId);
        boolean deleted = false;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, valueId);
            deleted = stmt.executeUpdate() > 0;
            result.put("success", deleted);
            if (deleted) {
                result.put("message", "Value deleted successfully");
            }
        }
        
        // Log activity - Delete case
        if (deleted && req != null) {
            // Component name format: "{dropdownDisplayName} - {valueName}"
            // Get value name from oldState
            String valueName = oldState != null && oldState.containsKey("name") 
                ? oldState.get("name").toString() 
                : "Unknown";
            String componentName = dropdownDisplayName != null && !dropdownDisplayName.trim().isEmpty() 
                ? dropdownDisplayName + " - " + valueName
                : "Unknown Field";
            
            // Pass contextMap with fieldName for component name
            Map<String, Object> contextMap = new HashMap<>();
            contextMap.put("fieldName", componentName);
            
            ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_DROPDOWN_CONFIG,
                componentName, ActivityLogConstants.CHANGE_TYPE_DELETE,
                oldState, null, contextMap);
        }
        
        return result;
    }
    
    /**
     * Get the state of a dropdown value for activity logging
     */
    private Map<String, Object> getDropdownValueState(Connection conn, String tableName, 
                                                      String columnId, String columnName, 
                                                      String columnDescription, int valueId) throws SQLException {
        Map<String, Object> state = new HashMap<>();
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT `").append(columnName).append("` as name");
        if (columnDescription != null && !columnDescription.equals("null")) {
            sql.append(", `").append(columnDescription).append("` as description");
        }
        sql.append(" FROM `").append(tableName).append("` WHERE `").append(columnId).append("` = ?");
        
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setInt(1, valueId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    state.put("name", rs.getString("name"));
                    if (columnDescription != null && !columnDescription.equals("null")) {
                        state.put("description", rs.getString("description"));
                    }
                }
            }
        }
        
        return state;
    }

    private int getTotalValuesCount(Connection conn, String tableName) throws SQLException {
        String sql = String.format("SELECT COUNT(*) as cnt FROM `%s`", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("cnt");
            }
        }
        return 0;
    }

    private Integer getUserIdFromRequest(HttpServletRequest req) {
        // Try to get user ID from request attributes first (set by AuthFilter)
        try {
            Object userIdObj = req.getAttribute("userId");
            //system.out.println("DEBUG: userId from request attribute: " + userIdObj + " (type: " + (userIdObj != null ? userIdObj.getClass().getName() : "null") + ")");
            
            if (userIdObj != null) {
                if (userIdObj instanceof Integer) {
                    Integer userId = (Integer) userIdObj;
                    //system.out.println("DEBUG: Returning userId from request attribute: " + userId);
                    return userId;
                } else if (userIdObj instanceof Number) {
                    Integer userId = ((Number) userIdObj).intValue();
                    //system.out.println("DEBUG: Returning userId from request attribute (Number): " + userId);
                    return userId;
                } else if (userIdObj instanceof String) {
                    try {
                        Integer userId = Integer.parseInt((String) userIdObj);
                        //system.out.println("DEBUG: Returning userId from request attribute (String): " + userId);
                        return userId;
                    } catch (NumberFormatException e) {
                        System.err.println("DEBUG: Failed to parse userId from String: " + userIdObj);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Exception getting userId from request attribute: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Fallback to session attribute
        try {
            Object userIdObj = req.getSession().getAttribute("userId");
            //system.out.println("DEBUG: userId from session: " + userIdObj + " (type: " + (userIdObj != null ? userIdObj.getClass().getName() : "null") + ")");
            
            if (userIdObj != null) {
                if (userIdObj instanceof Integer) {
                    Integer userId = (Integer) userIdObj;
                    //system.out.println("DEBUG: Returning userId from session: " + userId);
                    return userId;
                } else if (userIdObj instanceof Number) {
                    Integer userId = ((Number) userIdObj).intValue();
                    //system.out.println("DEBUG: Returning userId from session (Number): " + userId);
                    return userId;
                } else if (userIdObj instanceof String) {
                    try {
                        Integer userId = Integer.parseInt((String) userIdObj);
                        //system.out.println("DEBUG: Returning userId from session (String): " + userId);
                        return userId;
                    } catch (NumberFormatException e) {
                        System.err.println("DEBUG: Failed to parse userId from String: " + userIdObj);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Exception getting userId from session: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Last resort: try to get from UserContextUtil
        try {
            int userId = UserContextUtil.getCurrentUserId(req);
            //system.out.println("DEBUG: userId from UserContextUtil: " + userId);
            if (userId > 0) {
                return userId;
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Exception getting userId from UserContextUtil: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Default to user ID 1 if not found (should not happen in production)
        System.err.println("WARNING: Could not determine user ID from request, defaulting to 1");
        System.err.println("DEBUG: Request URI: " + req.getRequestURI());
        System.err.println("DEBUG: Request method: " + req.getMethod());
        return 1;
    }
    
    /**
     * Finds the user ID column name in the given table (if it exists)
     * Different tables use different naming conventions for the user ID column
     */
    private String findUserIdColumn(Connection conn, String tableName) throws SQLException {
        // List of possible user ID column names (in order of preference)
        String[] possibleColumns = {
            "Last_updated_UserID",
            "LastUpdated_UserID", 
            "LastUpdateUser_ID",
            "Last_UpdateUser_ID",
            "last_updated_userID",
            "lastupdateuser_id",
            "Last_updated_userID",
            "LastUpdate_UserID"
        };
        
        // Query INFORMATION_SCHEMA to get actual column names in the table
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        
        Set<String> actualColumns = new HashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    actualColumns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        
        // Find the first matching column (case-insensitive)
        for (String possibleColumn : possibleColumns) {
            for (String actualColumn : actualColumns) {
                if (actualColumn.equalsIgnoreCase(possibleColumn)) {
                    return actualColumn; // Return the actual column name from the table
                }
            }
        }
        
        return null; // No user ID column found
    }
    
    /**
     * Finds the datetime column name in the given table (if it exists)
     * Different tables use different naming conventions for the last updated datetime column
     */
    private String findDatetimeColumn(Connection conn, String tableName) throws SQLException {
        // List of possible datetime column names (in order of preference)
        String[] possibleColumns = {
            "Last_UpdateDatetime",
            "lastupdatedatetime",
            "Last_Updated_Datetime",
            "LastUpdated_Datetime",
            "Last_Update_Datetime",
            "LastUpdateDatetime",
            "last_updatedatetime",
            "last_updated_datetime",
            "lastupdated_datetime",
            "LastUpdatedDatetime",
            "Last_Updated_DateTime",
            "LastUpdated_DateTime",
            "Last_Updated_Date",
            "LastUpdated_Date",
            "Last_Update_Date",
            "last_updated_date",
            "lastupdated_date",
            "LastUpdate_Date",
            "Last_updated_Date",
            "Updated_At",
            "updated_at",
            "UpdatedAt",
            "modified_date",
            "Modified_Date",
            "ModifiedDate"
        };
        
        // Query INFORMATION_SCHEMA to get actual column names in the table
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        
        Set<String> actualColumns = new HashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    actualColumns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        
        // Find the first matching column (case-insensitive exact match)
        for (String possibleColumn : possibleColumns) {
            for (String actualColumn : actualColumns) {
                if (actualColumn.equalsIgnoreCase(possibleColumn)) {
                    return actualColumn;
                }
            }
        }
        
        // Fallback: find any column whose name contains "update" and ("date" or "time")
        for (String actualColumn : actualColumns) {
            String lower = actualColumn.toLowerCase();
            if (lower.contains("update") && (lower.contains("date") || lower.contains("time"))) {
                System.out.println("[DropdownConfig] Found datetime column via fallback (update+date/time): " + actualColumn + " in table " + tableName);
                return actualColumn;
            }
        }
        
        // Fallback: find any column whose name contains "modified" and ("date" or "time")
        for (String actualColumn : actualColumns) {
            String lower = actualColumn.toLowerCase();
            if (lower.contains("modif") && (lower.contains("date") || lower.contains("time"))) {
                System.out.println("[DropdownConfig] Found datetime column via fallback (modif+date/time): " + actualColumn + " in table " + tableName);
                return actualColumn;
            }
        }
        
        System.out.println("[DropdownConfig] WARNING: No datetime column found in table '" + tableName + "'. All columns: " + actualColumns);
        return null; // No datetime column found
    }

    private JsonObject parseRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = req.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private void sendSuccessResponse(HttpServletResponse resp, Object data) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        resp.getWriter().write(gson.toJson(response));
    }

    private void sendErrorResponse(HttpServletResponse resp, String error, int statusCode) throws IOException {
        resp.setStatus(statusCode);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        resp.getWriter().write(gson.toJson(response));
    }
    
    /**
     * Helper method to check if two values are equal
     */
    private boolean areEqual(Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) return true;
        if (oldValue == null || newValue == null) return false;
        String oldStr = oldValue.toString().trim();
        String newStr = newValue.toString().trim();
        return oldStr.equals(newStr);
    }
}

