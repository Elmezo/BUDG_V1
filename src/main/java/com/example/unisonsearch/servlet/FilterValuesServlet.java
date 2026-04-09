package com.example.unisonsearch.servlet;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.unisonsearch.config.FilterMetadataConfig;
import com.example.unisonsearch.model.FilterField;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Servlet to return available values for a filter field.
 * Endpoint: /UnisonSearch/api/filter-values/{fieldId}?facetId=DATASET
 */
@WebServlet(name = "FilterValuesServlet", urlPatterns = {"/UnisonSearch/api/filter-values/*"})
public class FilterValuesServlet extends HttpServlet {
    
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
        
        // Extract field ID from path
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Field ID is required in path");
            resp.getWriter().write(gson.toJson(error));
            return;
        }
        
        String fieldId = pathInfo.substring(1); // Remove leading /
        String facetId = req.getParameter("facetId");
        
        if (facetId == null || facetId.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "facetId parameter is required");
            resp.getWriter().write(gson.toJson(error));
            return;
        }
        
        try {
            // Find the filter field configuration
            List<FilterField> filterFields = FilterMetadataConfig.getFiltersForFacet(facetId);
            FilterField targetField = null;
            
            for (FilterField field : filterFields) {
                if (field.getId().equals(fieldId)) {
                    targetField = field;
                    break;
                }
            }
            
            if (targetField == null) {
                returnError(resp, HttpServletResponse.SC_NOT_FOUND, "Filter field not found: " + fieldId);
                return;
            }
            
            // Handle different filter types
            switch (targetField.getType()) {
                case DROPDOWN:
                    // 1) Static values (enums without lookup tables)
                    if (targetField.getStaticValues() != null && !targetField.getStaticValues().isEmpty()) {
                        JsonArray staticValues = new JsonArray();
                        targetField.getStaticValues().forEach((key, value) -> {
                            staticValues.add(makeValue(key, value));
                        });
                        writeResponse(resp, fieldId, targetField.getName(), staticValues);
                        return;
                    }
                    
                    // 2) Lookup table
                    if (targetField.getLookupTable() != null) {
                        JsonArray lookupValues = fetchValuesFromLookupTable(targetField.getLookupTable());
                        writeResponse(resp, fieldId, targetField.getName(), lookupValues);
                        return;
                    }
                    
                    // 3) Error: no configuration
                    returnError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "No lookupTable/staticValues configured for DROPDOWN: " + fieldId);
                    return;
                
                case PEOPLE:
                    // Return empty - autocomplete handled by PeopleSearchServlet
                    writeResponse(resp, fieldId, targetField.getName(), new JsonArray());
                    return;
                
                case BOOLEAN:
                    // Return Yes/No
                    JsonArray booleanValues = new JsonArray();
                    booleanValues.add(makeValue("1", "Yes"));
                    booleanValues.add(makeValue("0", "No"));
                    writeResponse(resp, fieldId, targetField.getName(), booleanValues);
                    return;
                
                case DATE_RANGE:
                case TEXT:
                    // Return empty - no values needed
                    writeResponse(resp, fieldId, targetField.getName(), new JsonArray());
                    return;
                
                default:
                    returnError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Unsupported filter type: " + targetField.getType());
                    return;
            }
            
        } catch (Exception e) {
            System.err.println("[FilterValuesServlet] ERROR: " + e.getMessage());
            e.printStackTrace();
            
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to fetch filter values: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Write successful response with values
     */
    private void writeResponse(HttpServletResponse resp, String fieldId, String fieldName, JsonArray values) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("fieldId", fieldId);
        response.addProperty("fieldName", fieldName);
        response.add("values", values);
        resp.getWriter().write(gson.toJson(response));
    }
    
    /**
     * Write error response
     */
    private void returnError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        resp.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Create a value JSON object
     */
    private JsonObject makeValue(String id, String name) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("name", name);
        return value;
    }
    
    /**
     * Fetch values from a lookup table.
     * Handles different column name variations (ID/id, PrimaryName/primaryname/Name).
     */
    private JsonArray fetchValuesFromLookupTable(String tableName) throws SQLException {
        JsonArray values = new JsonArray();
        
        // Sanitize table name to prevent SQL injection
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        
        // Get column information from database
        String idColumn = null;
        String nameColumn = null;
        String deleteColumn = null;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                 "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                 "ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String colLower = colName.toLowerCase();
                    
                    // Find ID column
                    if (idColumn == null && colLower.equals("id")) {
                        idColumn = colName;
                    }
                    
                    // Find name column (PrimaryName, primaryname, Name, name)
                    if (nameColumn == null && (colLower.equals("primaryname") || colLower.equals("name"))) {
                        nameColumn = colName;
                    }
                    
                    // Find delete column
                    if (deleteColumn == null && (colLower.equals("deletedate") || 
                        colLower.equals("deleteddatetime") || colLower.equals("deleted_datetime"))) {
                        deleteColumn = colName;
                    }
                }
            }
        }
        
        // Fallback to default names if not found
        if (idColumn == null) idColumn = "ID";
        if (nameColumn == null) nameColumn = "PrimaryName";
        
        // Build SQL query
        String sql;
        if (deleteColumn != null) {
            sql = String.format(
                "SELECT %s, %s FROM %s WHERE (%s IS NULL OR %s = 0) ORDER BY %s",
                idColumn, nameColumn, tableName, deleteColumn, deleteColumn, nameColumn
            );
        } else {
            sql = String.format(
                "SELECT %s, %s FROM %s ORDER BY %s",
                idColumn, nameColumn, tableName, nameColumn
            );
        }
        
        System.out.println("[FilterValuesServlet] Query: " + sql);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                JsonObject value = new JsonObject();
                value.addProperty("id", rs.getInt(1));
                value.addProperty("name", rs.getString(2));
                values.add(value);
            }
        }
        
        return values;
    }
}
