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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal servlet to provide field metadata for ALL bulk upload entities
 * Handles all entities dynamically based on URL path
 * URL pattern: /api/bulk/{entity}/fields
 * Example: /api/bulk/regulatorytheme/fields
 */
@WebServlet("/api/bulk/*/fields")
public class UniversalBulkFieldsMetadataServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(UniversalBulkFieldsMetadataServlet.class);
    private static final Gson gson = new Gson();
    
    // Map from URL path entity name (lowercase, no spaces) to display entity name
    private static final Map<String, String> ENTITY_NAME_MAP = new HashMap<>();
    
    static {
        // Initialize entity name mapping
        ENTITY_NAME_MAP.put("regulator", "Regulator");
        ENTITY_NAME_MAP.put("regulatorytheme", "Regulatory Theme");
        ENTITY_NAME_MAP.put("regulatory", "Regulatory Theme"); // Alternative
        ENTITY_NAME_MAP.put("geography", "Geography");
        ENTITY_NAME_MAP.put("regulation", "Regulation");
        ENTITY_NAME_MAP.put("policy", "Policy");
        ENTITY_NAME_MAP.put("process", "Process");
        ENTITY_NAME_MAP.put("project", "Project");
        ENTITY_NAME_MAP.put("committee", "Committee");
        ENTITY_NAME_MAP.put("businessarea", "Business Area");
        ENTITY_NAME_MAP.put("business", "Business Area"); // Alternative
        ENTITY_NAME_MAP.put("capability", "Capability");
        ENTITY_NAME_MAP.put("client", "Client");
        ENTITY_NAME_MAP.put("legal", "Legal Entity");
        ENTITY_NAME_MAP.put("orgunit", "Org Unit");
        ENTITY_NAME_MAP.put("org", "Org Unit"); // Alternative
        ENTITY_NAME_MAP.put("people", "People");
        ENTITY_NAME_MAP.put("product", "Product");
        ENTITY_NAME_MAP.put("system", "System");
        ENTITY_NAME_MAP.put("dataset", "Dataset");
        ENTITY_NAME_MAP.put("datasets", "Dataset"); // "Data Sets" normalizes to datasets in URL
        ENTITY_NAME_MAP.put("attribute", "Attribute");
        ENTITY_NAME_MAP.put("interface", "Interface");
        ENTITY_NAME_MAP.put("glossary", "Glossary");
    }

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
            // Extract entity name from URL path
            // URL: /api/bulk/{entity}/fields
            // pathInfo will be: /{entity}/fields
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.isEmpty()) {
                sendErrorResponse(response, "Entity name not found in URL path", 400);
                return;
            }
            
            // Remove leading slash
            String path = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            
            // Remove trailing /fields if present
            String entityPath;
            if (path.endsWith("/fields")) {
                entityPath = path.substring(0, path.length() - "/fields".length());
            } else {
                // If no /fields, try to extract from path segments
                // pathInfo format: /{entity}/fields
                String[] segments = path.split("/");
                entityPath = segments.length > 0 ? segments[0] : path;
            }
            
            // Normalize entity path (lowercase, no spaces)
            entityPath = entityPath.toLowerCase().replaceAll("\\s+", "");
            
            // Get display entity name from map
            String entityName = ENTITY_NAME_MAP.get(entityPath);
            if (entityName == null) {
                // Try to convert from path (e.g., "regulatorytheme" -> "Regulatory Theme")
                entityName = convertPathToEntityName(entityPath);
            }
            
            if (entityName == null) {
                // Use the path as entity name (capitalize first letter)
                entityName = entityPath.substring(0, 1).toUpperCase() + entityPath.substring(1);
                logger.info("Entity name not in map, using converted name: {}", entityName);
            }
            
            logger.info("Fetching field metadata for entity: {} (from path: {})", entityName, entityPath);

            // Get operation type from query parameter (optional)
            String operation = request.getParameter("operation");
            if (operation != null) {
                operation = operation.toUpperCase();
            }
            
            JsonArray fields = getFieldsForEntity(entityName, operation);
            
            // Add custom fields for INSERT and UPDATE operations
            if (operation == null || "INSERT".equals(operation) || "UPDATE".equals(operation)) {
                try {
                    List<JsonObject> customFields = getCustomFieldsForEntity(entityName);
                    for (JsonObject customField : customFields) {
                        fields.add(customField);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch custom fields for {}: {}", entityName, e.getMessage());
                    // Continue without custom fields
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(fields));

            logger.info("Successfully returned {} field definitions for {}", fields.size(), entityName);

        } catch (Exception e) {
            logger.error("Error fetching field metadata", e);
            sendErrorResponse(response, "Failed to fetch field metadata: " + e.getMessage(), 500);
        }
    }

    /**
     * Convert URL path to entity name (fallback method)
     * Example: "regulatorytheme" -> "Regulatory Theme"
     */
    private String convertPathToEntityName(String path) {
        // Try common patterns
        if (path.contains("regulatory") && path.contains("theme")) {
            return "Regulatory Theme";
        }
        if (path.contains("business") && path.contains("area")) {
            return "Business Area";
        }
        if (path.contains("org") && path.contains("unit")) {
            return "Org. Unit";
        }
        // Default: capitalize first letter of each word
        String[] parts = path.split("(?=[A-Z])|_|-");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(part.substring(0, 1).toUpperCase())
                      .append(part.substring(1).toLowerCase());
            }
        }
        return result.length() > 0 ? result.toString() : null;
    }

    /**
     * Get fields for an entity based on operation
     * This method contains all field definitions for all entities
     */
    private JsonArray getFieldsForEntity(String entityName, String operation) {
        JsonArray fields = new JsonArray();
        
        // Normalize operation
        if (operation == null) {
            operation = "INSERT";
        }
        
        switch (entityName) {
            case "Regulator":
                if ("INSERT".equals(operation)) {
                    fields.add(createFieldDefinition("PrimaryName", "Primary Name", "Primary name of the regulator", true, "STRING"));
                    fields.add(createFieldDefinition("ShortName", "Short Name", "Short name or abbreviation", false, "STRING"));
                    fields.add(createFieldDefinition("Description", "Description", "Detailed description", false, "STRING"));
                } else if ("UPDATE".equals(operation)) {
                    fields.add(createFieldDefinition("ID", "ID", "Regulator unique identifier (required for updates)", true, "INTEGER"));
                    fields.add(createFieldDefinition("PrimaryName", "Primary Name", "Primary name of the regulator", true, "STRING"));
                    fields.add(createFieldDefinition("ShortName", "Short Name", "Short name or abbreviation", false, "STRING"));
                    fields.add(createFieldDefinition("Description", "Description", "Detailed description", false, "STRING"));
                } else if ("DELETE".equals(operation)) {
                    fields.add(createFieldDefinition("ID", "ID", "Regulator unique identifier to delete", true, "INTEGER"));
                }
                break;
                
            case "Regulatory Theme":
                if ("INSERT".equals(operation)) {
                    fields.add(createFieldDefinition("Regulatory Theme Long Name", "Regulatory Theme Long Name", "Primary name (required, must be unique)", true, "STRING"));
                    fields.add(createFieldDefinition("Reference", "Reference", "Reference number", false, "STRING"));
                    fields.add(createFieldDefinition("Short Name", "Short Name", "Short name or abbreviation", false, "STRING"));
                    fields.add(createFieldDefinition("Description", "Description", "Detailed description", false, "STRING"));
                    fields.add(createFieldDefinition("Parent Regulatory Theme Name", "Parent Regulatory Theme Name", "Name of parent regulatory theme", false, "STRING"));
                    fields.add(createFieldDefinition("Parent Ref.", "Parent Ref.", "Reference number of parent", false, "STRING"));
                    fields.add(createFieldDefinition("BUDG Status", "BUDG Status", "Status (Active, Inactive, etc.)", false, "STRING"));
                } else if ("UPDATE".equals(operation)) {
                    fields.add(createFieldDefinition("Regulatory Theme ID", "Regulatory Theme ID", "Unique identifier (required)", true, "INTEGER"));
                    fields.add(createFieldDefinition("Reference", "Reference", "Reference number", false, "STRING"));
                    fields.add(createFieldDefinition("Short Name", "Short Name", "Short name or abbreviation", false, "STRING"));
                    fields.add(createFieldDefinition("Regulatory Theme Long Name", "Regulatory Theme Long Name", "Primary name (required)", true, "STRING"));
                    fields.add(createFieldDefinition("Description", "Description", "Detailed description (required)", true, "STRING"));
                    fields.add(createFieldDefinition("Parent Regulatory Theme Name", "Parent Regulatory Theme Name", "Name of parent regulatory theme", false, "STRING"));
                    fields.add(createFieldDefinition("Parent Ref.", "Parent Ref.", "Reference number of parent", false, "STRING"));
                    fields.add(createFieldDefinition("BUDG Status", "BUDG Status", "Status (dropdown from status table)", false, "LIST"));
                } else if ("DELETE".equals(operation)) {
                    fields.add(createFieldDefinition("Regulatory Theme ID", "Regulatory Theme ID", "Unique identifier to delete", true, "INTEGER"));
                }
                break;
                
            case "Process":
                if ("INSERT".equals(operation)) {
                    fields.add(createFieldDefinition("Name", "Name", "Process name", true, "STRING"));
                    fields.add(createFieldDefinition("Description", "Description", "Process description", true, "STRING"));
                    fields.add(createFieldDefinition("Step Type", "Step Type", "Process step type (lookup)", true, "STRING"));
                    fields.add(createFieldDefinition("Type", "Type", "Process type (lookup)", true, "STRING"));
                    fields.add(createFieldDefinition("Lifecycle", "Lifecycle", "Process lifecycle (lookup)", true, "STRING"));
                    fields.add(createFieldDefinition("Ref.", "Ref.", "Reference code (auto if empty)", false, "STRING"));
                } else if ("UPDATE".equals(operation)) {
                    fields.add(createFieldDefinition("ID", "ID", "Process unique identifier (required)", true, "INTEGER"));
                    fields.add(createFieldDefinition("Name", "Name", "Process name", false, "STRING"));
                    fields.add(createFieldDefinition("Ref.", "Ref.", "Reference code", false, "STRING"));
                    fields.add(createFieldDefinition("Description", "Description", "Process description", false, "STRING"));
                } else if ("DELETE".equals(operation)) {
                    fields.add(createFieldDefinition("ID", "ID", "Process unique identifier to delete", true, "INTEGER"));
                }
                break;
                
            case "Business Area":
                if ("INSERT".equals(operation)) {
                    fields.add(createFieldDefinition("Business Area Name", "Business Area Name", "Primary name (required)", true, "STRING"));
                    fields.add(createFieldDefinition("Description", "Description", "Detailed description", false, "STRING"));
                    fields.add(createFieldDefinition("BUDG Viewing", "BUDG Viewing", "Viewing access level", false, "LIST"));
                    fields.add(createFieldDefinition("BUDG Status", "BUDG Status", "Status (dropdown)", false, "LIST"));
                    fields.add(createFieldDefinition("Lifecycle", "Lifecycle", "Lifecycle status (required)", true, "LIST"));
                } else if ("UPDATE".equals(operation)) {
                    fields.add(createFieldDefinition("Business Area ID", "Business Area ID", "ID (required)", true, "INTEGER"));
                    fields.add(createFieldDefinition("Business Area Name", "Business Area Name", "Primary name (required)", true, "STRING"));
                    fields.add(createFieldDefinition("Description", "Description", "Detailed description (required)", true, "STRING"));
                    fields.add(createFieldDefinition("BUDG Viewing", "BUDG Viewing", "Viewing access level", false, "LIST"));
                    fields.add(createFieldDefinition("BUDG Status", "BUDG Status", "Status (dropdown)", false, "LIST"));
                    fields.add(createFieldDefinition("Lifecycle", "Lifecycle", "Lifecycle status (required)", true, "LIST"));
                } else if ("DELETE".equals(operation)) {
                    fields.add(createFieldDefinition("ID", "ID", "Business Area ID to delete", true, "INTEGER"));
                }
                break;
                
            // For all other entities, return empty array
            // The frontend will use its own entityConfig.js as fallback
            // This prevents 404 errors while allowing frontend to handle field definitions
            default:
                logger.debug("No predefined fields for entity: {}, operation: {}. Returning empty array (frontend will use entityConfig.js)", entityName, operation);
                // Return empty array - frontend will use its own config from entityConfig.js
                // Custom fields will still be added if available
                break;
        }
        
        return fields;
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

