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

@WebServlet({ "/bulk/capability/fields", "/api/bulk/capability/fields" })
public class CapabilityBulkFieldsMetadataServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(CapabilityBulkFieldsMetadataServlet.class);
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
            logger.info("Fetching field metadata for Capability entity");
            String operation = request.getParameter("operation");

            JsonArray fields = new JsonArray();

            if (operation == null || "INSERT".equalsIgnoreCase(operation)) {
                // Required fields
                fields.add(field("PrimaryName", "PrimaryName", "Capability Name (required)", true, "STRING"));
                fields.add(field("Description", "Description", "Capability description (required)", true, "STRING"));
                fields.add(field("Lifecycle", "Lifecycle", "Capability lifecycle (lookup, required)", true, "STRING"));

                // Optional fields
                fields.add(field("RefNumber", "RefNumber", "Reference Number (optional)", false, "STRING"));
                fields.add(field("Parent Capability", "Parent Capability", "Parent Capability Name (optional)", false,
                        "STRING"));
                fields.add(
                        field("Parent Ref.", "Parent Ref.", "Parent Capability Reference (optional)", false, "STRING"));
                fields.add(field("BUDG Status", "Status", "Status (lookup, optional)", false, "STRING"));
                fields.add(field("Is_Public", "Is_Public", "Viewing (lookup, optional)", false, "STRING"));
                fields.add(field("Classification", "Classification", "Classification (lookup, optional)", false,
                        "STRING"));
                fields.add(field("Capability_Type", "Capability_Type", "Capability Type (lookup, optional)", false,
                        "STRING"));

                // Stakeholder fields
                fields.add(field("User Email", "User Email", "Stakeholder user email (optional)", false, "STRING"));
                fields.add(field("User First Name", "User First Name", "Stakeholder first name (optional)", false,
                        "STRING"));
                fields.add(
                        field("User Last Name", "User Last Name", "Stakeholder last name (optional)", false, "STRING"));
                fields.add(field("User Lan ID", "User Lan ID", "Stakeholder LAN ID (optional)", false, "STRING"));
                fields.add(field("Governance Role", "Governance Role", "Stakeholder governance role (lookup, optional)",
                        false, "STRING"));

                fields.add(field("Segment", "Segment", "Segment name (optional, depends on segment mode)", false,
                        "STRING"));
            } else if ("UPDATE".equalsIgnoreCase(operation)) {
                // Identity fields (At least one required)
                fields.add(field("ID", "ID", "Capability unique identifier (identity)", false, "INTEGER"));
                fields.add(field("Reference", "Reference", "Capability Reference (identity)", false, "STRING"));
                fields.add(field("Capability Name", "Capability Name", "Capability Name (identity)", false, "STRING"));

                // Fields to update
                fields.add(field("PrimaryName", "PrimaryName", "New Capability Name", false, "STRING"));
                fields.add(field("RefNumber", "RefNumber", "New Reference Number", false, "STRING"));
                fields.add(field("Description", "Description", "New Description", false, "STRING"));
                fields.add(field("Lifecycle", "Lifecycle", "New Lifecycle", false, "STRING"));
                fields.add(field("Parent Capability", "Parent Capability", "New Parent Name", false, "STRING"));
                fields.add(field("Parent Ref.", "Parent Ref.", "New Parent Reference", false, "STRING"));
                fields.add(field("BUDG Status", "Status", "New Status", false, "STRING"));
                fields.add(field("Is_Public", "Is_Public", "New Viewing", false, "STRING"));
                fields.add(field("Classification", "Classification", "New Classification", false, "STRING"));
                fields.add(field("Capability_Type", "Capability_Type", "New Capability Type", false, "STRING"));

                fields.add(field("User Email", "User Email", "Stakeholder user email", false, "STRING"));
                fields.add(field("User First Name", "User First Name", "Stakeholder first name", false, "STRING"));
                fields.add(field("User Last Name", "User Last Name", "Stakeholder last name", false, "STRING"));
                fields.add(field("User Lan ID", "User Lan ID", "Stakeholder LAN ID", false, "STRING"));
                fields.add(field("Governance Role", "Governance Role", "Stakeholder governance role", false, "STRING"));

                fields.add(field("Segment", "Segment", "Segment name", false, "STRING"));
            } else if ("DELETE".equalsIgnoreCase(operation)) {
                fields.add(field("ID", "ID", "Capability unique identifier to delete", true, "INTEGER"));
            } else {
                // Unknown operation: return basic fields
                fields.add(field("ID", "ID", "Capability identifier", false, "INTEGER"));
                fields.add(field("PrimaryName", "PrimaryName", "Capability name", true, "STRING"));
                fields.add(field("Description", "Description", "Capability description", false, "STRING"));
            }

            // Add custom fields for INSERT and UPDATE operations
            if (operation == null || "INSERT".equalsIgnoreCase(operation) || "UPDATE".equalsIgnoreCase(operation)) {
                try {
                    List<JsonObject> customFields = getCustomFieldsForEntity("Capability");
                    for (JsonObject customField : customFields) {
                        fields.add(customField);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch custom fields for Capability: {}", e.getMessage());
                    // Continue without custom fields
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(fields));
            logger.info("Successfully returned {} field definitions for Capability", fields.size());

        } catch (Exception e) {
            logger.error("Error fetching Capability field metadata", e);
            sendErrorResponse(response, "Failed to fetch field metadata: " + e.getMessage(), 500);
        }
    }

    private JsonObject field(String fieldName, String displayName, String description, boolean required,
            String dataType) {
        JsonObject f = new JsonObject();
        f.addProperty("fieldName", fieldName);
        f.addProperty("name", fieldName);
        f.addProperty("displayName", displayName);
        f.addProperty("description", description);
        f.addProperty("required", required);
        f.addProperty("dataType", dataType);
        return f;
    }

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

                    JsonObject field = field(
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

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
}
