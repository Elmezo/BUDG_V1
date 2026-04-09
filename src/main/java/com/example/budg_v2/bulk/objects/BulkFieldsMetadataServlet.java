package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ModuleResolver;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet to provide field metadata for bulk upload entities
 * Returns field definitions including name, display name, description, required flag, and data type
 */
@WebServlet("/api/bulk/regulator/fields")
public class BulkFieldsMetadataServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkFieldsMetadataServlet.class);
    private static final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            logger.info("Fetching field metadata for Regulator entity");

            // Get operation type from query parameter (optional)
            String operation = request.getParameter("operation");
            
            JsonArray fields = new JsonArray();

            // Define field metadata based on operation
            // If no operation specified, return all possible fields
            if (operation == null || "INSERT".equalsIgnoreCase(operation)) {
                fields.add(createFieldDefinition(
                    "PrimaryName",
                    "Primary Name",
                    "Primary name of the regulator",
                    true,
                    "STRING"
                ));
                fields.add(createFieldDefinition(
                    "ShortName",
                    "Short Name",
                    "Short name or abbreviation of the regulator",
                    false,
                    "STRING"
                ));
                fields.add(createFieldDefinition(
                    "Description",
                    "Description",
                    "Detailed description of the regulator",
                    false,
                    "STRING"
                ));
            } else if ("UPDATE".equalsIgnoreCase(operation)) {
                fields.add(createFieldDefinition(
                    "ID",
                    "ID",
                    "Regulator unique identifier (required for updates)",
                    true,
                    "INTEGER"
                ));
                fields.add(createFieldDefinition(
                    "PrimaryName",
                    "Primary Name",
                    "Primary name of the regulator",
                    true,
                    "STRING"
                ));
                fields.add(createFieldDefinition(
                    "ShortName",
                    "Short Name",
                    "Short name or abbreviation of the regulator",
                    false,
                    "STRING"
                ));
                fields.add(createFieldDefinition(
                    "Description",
                    "Description",
                    "Detailed description of the regulator",
                    false,
                    "STRING"
                ));
            } else if ("DELETE".equalsIgnoreCase(operation)) {
                fields.add(createFieldDefinition(
                    "ID",
                    "ID",
                    "Regulator unique identifier to delete",
                    true,
                    "INTEGER"
                ));
            } else {
                // Return all fields for unknown operation
                fields.add(createFieldDefinition("ID", "ID", "Regulator unique identifier", false, "INTEGER"));
                fields.add(createFieldDefinition("PrimaryName", "Primary Name", "Primary name of the regulator", true, "STRING"));
                fields.add(createFieldDefinition("ShortName", "Short Name", "Short name or abbreviation", false, "STRING"));
                fields.add(createFieldDefinition("Description", "Description", "Detailed description", false, "STRING"));
            }

            // Add custom fields for INSERT and UPDATE operations
            if (operation == null || "INSERT".equalsIgnoreCase(operation) || "UPDATE".equalsIgnoreCase(operation)) {
                try {
                    List<JsonObject> customFields = getCustomFieldsForEntity("Regulator");
                    for (JsonObject customField : customFields) {
                        fields.add(customField);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch custom fields for Regulator: {}", e.getMessage());
                    // Continue without custom fields
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(fields));

            logger.info("Successfully returned {} field definitions", fields.size());

        } catch (Exception e) {
            logger.error("Error fetching field metadata", e);
            sendErrorResponse(response, "Failed to fetch field metadata: " + e.getMessage(), 500);
        }
    }

    /**
     * Create a field definition JSON object
     */
    private JsonObject createFieldDefinition(String fieldName, String displayName, 
                                            String description, boolean required, String dataType) {
        JsonObject field = new JsonObject();
        field.addProperty("fieldName", fieldName);
        field.addProperty("name", fieldName); // Alias for compatibility
        field.addProperty("displayName", displayName);
        field.addProperty("description", description);
        field.addProperty("required", required);
        field.addProperty("dataType", dataType);
        return field;
    }

    /**
     * Get custom fields for an entity
     * @param entityName The entity name (e.g., "Regulator", "Regulatory Theme")
     * @return List of custom field JSON objects
     */
    private List<JsonObject> getCustomFieldsForEntity(String entityName) throws SQLException {
        List<JsonObject> customFields = new ArrayList<>();
        
        // Get module ID for the entity
        int moduleId;
        try {
            moduleId = ModuleResolver.getModuleId(entityName);
        } catch (SQLException | IllegalArgumentException e) {
            logger.warn("Module not found for entity: {}", entityName);
            return customFields;
        }
        
        String sql = """
            SELECT DisplayName, CustomFieldName, DataType, is_Mandatory, 
                   Default_Value, Description, Placeholder_Text
            FROM Custom_Field_Metadata
            WHERE Module_ID = ?
            ORDER BY DisplayName
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String displayName = rs.getString("DisplayName");
                    String customFieldName = rs.getString("CustomFieldName");
                    String dataType = rs.getString("DataType");
                    boolean isMandatory = rs.getBoolean("is_Mandatory");
                    String description = rs.getString("Description");
                    if (description == null) {
                        description = "";
                    }

                    String mappedDataType = mapCustomFieldDataType(dataType);

                    String mappingKey = displayName;
                    if (customFieldName != null && !customFieldName.trim().isEmpty()) {
                        mappingKey = customFieldName.trim();
                    }

                    JsonObject field = createFieldDefinition(
                            mappingKey,
                            displayName,
                            description.isEmpty() ? "Custom field" : description,
                            isMandatory,
                            mappedDataType);

                    field.addProperty("isCustomField", true);
                    field.addProperty("customFieldDataType", dataType);
                    appendCustomFieldAliases(field, displayName, customFieldName);

                    customFields.add(field);
                }
            }
        }
        
        return customFields;
    }

    private void appendCustomFieldAliases(JsonObject field, String displayName, String customFieldName) {
        JsonArray aliases = new JsonArray();
        if (displayName != null && !displayName.trim().isEmpty()) {
            String dn = displayName.trim();
            aliases.add(dn);
            aliases.add(dn + " *");
        }
        if (customFieldName != null && !customFieldName.trim().isEmpty()) {
            String cf = customFieldName.trim();
            aliases.add(cf);
            aliases.add(cf + " *");
        }
        field.add("aliases", aliases);
    }

    /**
     * Map custom field data type to standard data type for frontend
     */
    private String mapCustomFieldDataType(String customFieldDataType) {
        if (customFieldDataType == null) {
            return "STRING";
        }
        
        String lower = customFieldDataType.toLowerCase();
        switch (lower) {
            case "number":
            case "integer":
                return "INTEGER";
            case "decimal":
            case "float":
            case "double":
                return "DECIMAL";
            case "date":
                return "DATE";
            case "time":
                return "TIME";
            case "checkbox":
            case "boolean":
                return "BOOLEAN";
            case "percentage":
                return "PERCENTAGE";
            case "dropdown":
            case "multiselect":
                return "DROPDOWN";
            case "text":
            default:
                return "STRING";
        }
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);

        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
}

