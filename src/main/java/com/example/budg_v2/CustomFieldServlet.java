package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.CustomFieldPendingFacetHelper;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Unified servlet for managing custom fields across all facets.
 * Handles metadata (field definitions) and data (field values) operations.
 */
@WebServlet("/api/custom-fields/*")
public class CustomFieldServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Shown when PUT would change more than name / description / mandatory during a blocking CR. */
    private static final String MSG_CR_METADATA_RESTRICTED =
        "Only name, description, and mandatory can be edited while a related change request is running or pending start";
    /** Shown when POST or DELETE metadata is attempted during a blocking CR. */
    private static final String MSG_CR_NO_CREATE_DELETE =
        "Custom fields cannot be added or removed while a related change request is running or pending start";

    /** Parse object to int; accepts Number or String (e.g. from JSON). */
    private static int toInt(Object obj) {
        if (obj == null) throw new IllegalArgumentException("null");
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.parseInt(obj.toString());
    }

    /** Parse object to Integer; returns null if obj is null. */
    private static Integer toIntOrNull(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.parseInt(obj.toString());
    }

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
                sendError(resp, "Invalid path", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");
            
            // GET /api/custom-fields/metadata?facetId=X&objectId=Y
            if ("metadata".equals(pathParts[0])) {
                String facetId = req.getParameter("facetId");
                String objectIdParam = req.getParameter("objectId");
                Integer objectId = objectIdParam != null ? Integer.parseInt(objectIdParam) : null;
                
                if (facetId == null || facetId.trim().isEmpty()) {
                    sendError(resp, "facetId is required", HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                
                List<Map<String, Object>> fields = getCustomFieldsMetadata(facetId, objectId);
                Map<String, Object> response = Map.of("success", true, "data", fields);
                objectMapper.writeValue(resp.getWriter(), response);
                return;
            }
            
            // GET /api/custom-fields/data?facetId=X&objectId=Y[&view=changes]
            if ("data".equals(pathParts[0])) {
                String facetId = req.getParameter("facetId");
                String objectIdParam = req.getParameter("objectId");
                String viewParam = req.getParameter("view");
                
                if (facetId == null || objectIdParam == null) {
                    sendError(resp, "facetId and objectId are required", HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                
                int objectId = Integer.parseInt(objectIdParam);
                List<Map<String, Object>> values;
                try (Connection conn = DatabaseConnection.getConnection()) {
                    Integer moduleId = getModuleIdByFacetName(conn, facetId);
                    if (moduleId == null) {
                        values = new ArrayList<>();
                    } else {
                        String modulePrimary = getModulePrimaryName(conn, moduleId);
                        String facetKey = CustomFieldPendingFacetHelper.toFacetChangesKeyFromModulePrimaryName(modulePrimary);
                        int effectiveId = CustomFieldPendingFacetHelper.resolveEffectiveFacetObjectId(
                                conn, moduleId, facetKey, objectId, viewParam, false);
                        values = getCustomFieldValuesForModule(conn, moduleId, effectiveId);
                    }
                }
                Map<String, Object> response = Map.of("success", true, "data", values);
                objectMapper.writeValue(resp.getWriter(), response);
                return;
            }

            // GET /api/custom-fields/facets — module primary names for authenticated users (replaces admin-only list for runtime mapping)
            if ("facets".equals(pathParts[0])) {
                int userId = UserContextUtil.getCurrentUserId(req);
                if (userId <= 0) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    objectMapper.writeValue(resp.getWriter(), Map.of("success", false, "error", "Authentication required"));
                    return;
                }
                try (Connection conn = DatabaseConnection.getConnection()) {
                    List<Map<String, Object>> facets = listModuleFacetsForMapping(conn);
                    objectMapper.writeValue(resp.getWriter(), Map.of("success", true, "data", facets));
                }
                return;
            }

            // GET /api/custom-fields/has-blocking-cr?facetId=X
            if ("has-blocking-cr".equals(pathParts[0])) {
                String facetId = req.getParameter("facetId");
                if (facetId == null || facetId.trim().isEmpty()) {
                    sendError(resp, "facetId is required", HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                try (Connection conn = DatabaseConnection.getConnection()) {
                    // Resolve facetId to module PrimaryName (the CR Reference uses the module name)
                    Integer moduleId = getModuleIdByFacetName(conn, facetId);
                    boolean hasBlockingCR = false;
                    if (moduleId != null) {
                        String moduleName = getModulePrimaryName(conn, moduleId);
                        if (moduleName != null) {
                            hasBlockingCR = hasBlockingChangeRequestForFacet(conn, moduleName);
                        }
                    }
                    Map<String, Object> crResponse = new HashMap<>();
                    crResponse.put("success", true);
                    crResponse.put("hasBlockingCR", hasBlockingCR);
                    objectMapper.writeValue(resp.getWriter(), crResponse);
                }
                return;
            }

            sendError(resp, "Invalid endpoint", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, "Internal server error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(resp, "Invalid path", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");
            Map<String, Object> requestData = objectMapper.readValue(req.getReader(), Map.class);
            int userId = UserContextUtil.getCurrentUserId(req);
            if (userId <= 0) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                Map<String, Object> error = new java.util.HashMap<>();
                error.put("success", false);
                error.put("error", "User authentication required");
                objectMapper.writeValue(resp.getWriter(), error);
                return;
            }

            // POST /api/custom-fields/metadata - Create new custom field
            if ("metadata".equals(pathParts[0])) {
                Map<String, Object> result = createCustomFieldMetadata(requestData, userId, req);
                if ((Boolean) result.get("success")) {
                    resp.setStatus(HttpServletResponse.SC_CREATED);
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                objectMapper.writeValue(resp.getWriter(), result);
                return;
            }

            // POST /api/custom-fields/data - Save custom field values
            if ("data".equals(pathParts[0])) {
                // Check Edit permission based on facetId and objectId from request body
                String facetId = (String) requestData.get("facetId");
                Object objectIdObj = requestData.get("objectId");
                
                if (facetId != null && !facetId.isBlank() && objectIdObj != null) {
                    Integer objectId = toInteger(objectIdObj);
                    if (objectId == null) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        objectMapper.writeValue(resp.getWriter(), Map.of("success", false, "error", "Invalid objectId"));
                        return;
                    }
                    // Normalize facetId to module name (e.g., "glossary" -> "Glossary", "system" -> "System")
                    String moduleName = normalizeFacetIdToModuleName(facetId);
                    if (moduleName != null) {
                        if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(req, resp, moduleName, objectId)) {
                            return; // Response already sent
                        }
                    } else {
                        // Fallback: check with facetId as-is if normalization fails
                        PermissionService permissionService = new PermissionService();
                        if (!permissionService.canEdit(userId, facetId)) {
                            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            objectMapper.writeValue(resp.getWriter(), Map.of("success", false, "error", "You don't have permission to edit " + facetId));
                            return;
                        }
                    }
                }
                
                Map<String, Object> result = saveCustomFieldValues(requestData, userId, true,
                        UserContextUtil.isCurrentUserAdmin(req));
                if ((Boolean) result.get("success")) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                objectMapper.writeValue(resp.getWriter(), result);
                return;
            }

            sendError(resp, "Invalid endpoint", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, "Internal server error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(resp, "Invalid path", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");
            Map<String, Object> requestData = objectMapper.readValue(req.getReader(), Map.class);
            int userId = UserContextUtil.getCurrentUserId(req);
            if (userId <= 0) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                Map<String, Object> error = new java.util.HashMap<>();
                error.put("success", false);
                error.put("error", "User authentication required");
                objectMapper.writeValue(resp.getWriter(), error);
                return;
            }

            // PUT /api/custom-fields/metadata - Update custom field metadata
            if ("metadata".equals(pathParts[0])) {
                Map<String, Object> result = updateCustomFieldMetadata(requestData, userId, req);
                if ((Boolean) result.get("success")) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                objectMapper.writeValue(resp.getWriter(), result);
                return;
            }

            sendError(resp, "Invalid endpoint", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, "Internal server error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(resp, "Invalid path", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");
            int userId = UserContextUtil.getCurrentUserId(req);
            if (userId <= 0) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                Map<String, Object> error = new java.util.HashMap<>();
                error.put("success", false);
                error.put("error", "User authentication required");
                objectMapper.writeValue(resp.getWriter(), error);
                return;
            }

            // DELETE /api/custom-fields/metadata/{fieldId}
            if ("metadata".equals(pathParts[0]) && pathParts.length > 1) {
                int fieldId = Integer.parseInt(pathParts[1]);
                Map<String, Object> result = deleteCustomFieldMetadata(fieldId, userId, req);
                if ((Boolean) result.get("success")) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                objectMapper.writeValue(resp.getWriter(), result);
                return;
            }

            sendError(resp, "Invalid endpoint", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, "Internal server error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // ============= METADATA OPERATIONS =============

    private List<Map<String, Object>> listModuleFacetsForMapping(Connection conn) throws SQLException {
        List<Map<String, Object>> facets = new ArrayList<>();
        String sql = "SELECT id, primaryname, tablename FROM module WHERE primaryname IS NOT NULL AND primaryname != '' ORDER BY primaryname";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> facet = new HashMap<>();
                String primaryName = rs.getString("primaryname");
                facet.put("id", primaryName);
                facet.put("name", primaryName);
                facet.put("tableName", rs.getString("tablename"));
                facet.put("moduleId", rs.getInt("id"));
                facets.add(facet);
            }
        }
        return facets;
    }

    private List<Map<String, Object>> getCustomFieldsMetadata(String facetId, Integer objectId) throws SQLException {
        List<Map<String, Object>> fields = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer moduleId = getModuleIdByFacetName(conn, facetId);
            if (moduleId == null) {
                return fields;
            }

            Map<Integer, Map<String, Object>> valueMap = new HashMap<>();
            if (objectId != null) {
                valueMap = getCustomFieldValuesMap(conn, moduleId, objectId);
            }

            String sql = """
                SELECT cfm.ID, cfm.Module_ID, cfm.CustomFieldName, cfm.DisplayName,
                       cfm.is_Mandatory, cfm.is_Searchable, cfm.DataType, cfm.Default_Value,
                       cfm.Description, cfm.Placeholder_Text, cfm.LastUpdateDatetime,
                       CONCAT(COALESCE(p.First_Name, ''), ' ', COALESCE(p.Last_Name, '')) AS LastUpdateUserName
                FROM Custom_Field_Metadata cfm
                LEFT JOIN People p ON cfm.LastUpdate_UserID = p.ID
                WHERE cfm.Module_ID = ?
                ORDER BY cfm.ID DESC
            """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, moduleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> field = new HashMap<>();
                        int fieldId = rs.getInt("ID");
                        field.put("id", fieldId);
                        field.put("facetId", facetId);
                        field.put("displayName", rs.getString("DisplayName"));
                        field.put("technicalName", rs.getString("CustomFieldName"));
                        field.put("type", rs.getString("DataType"));
                        field.put("mandatory", rs.getBoolean("is_Mandatory"));
                        field.put("defaultValue", rs.getString("Default_Value"));
                        field.put("placeholder", rs.getString("Placeholder_Text"));
                        field.put("description", rs.getString("Description"));
                        field.put("searchable", rs.getBoolean("is_Searchable"));
                        field.put("lastUpdatedAt", rs.getTimestamp("LastUpdateDatetime"));
                        field.put("lastUpdatedBy", rs.getString("LastUpdateUserName"));

                        // Add current values if objectId provided
                        Map<String, Object> valueData = valueMap.get(fieldId);
                        if (valueData != null) {
                            field.put("currentValue", valueData.get("value"));
                            field.put("currentEnumId", valueData.get("enumId"));
                            // For multiselect, also include enumIds array
                            if ("multiselect".equals(field.get("type")) && valueData.get("enumIds") != null) {
                                field.put("currentEnumIds", valueData.get("enumIds"));
                            }
                        }

                        // Get enum values for dropdown/multiselect
                        if ("dropdown".equals(field.get("type")) || "multiselect".equals(field.get("type"))) {
                            field.put("enumValues", getEnumValues(conn, fieldId));
                        }

                        fields.add(field);
                    }
                }
            }
        }
        return fields;
    }

    private Map<String, Object> createCustomFieldMetadata(Map<String, Object> data, int userId, HttpServletRequest req) throws SQLException {
        Map<String, Object> response = new HashMap<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String facetId = (String) data.get("facetId");
                String displayName = (String) data.get("displayName");
                String dataType = (String) data.get("type");
                boolean mandatory = data.get("mandatory") != null && (Boolean) data.get("mandatory");
                String defaultValue = data.get("defaultValue") != null ? (String) data.get("defaultValue") : "";
                String placeholder = data.get("placeholder") != null ? (String) data.get("placeholder") : "";
                String description = data.get("description") != null ? (String) data.get("description") : "";
                boolean searchable = data.get("searchable") == null || (Boolean) data.get("searchable");

                Integer moduleId = getModuleIdByFacetName(conn, facetId);
                if (moduleId == null) {
                    response.put("success", false);
                    response.put("error", "Facet not found");
                    return response;
                }

                // Check display name uniqueness
                if (!isDisplayNameUnique(conn, displayName, moduleId, null)) {
                    response.put("success", false);
                    response.put("error", "Display name must be unique for this facet");
                    return response;
                }

                // Generate custom field name
                String customFieldName = generateCustomFieldName(conn, displayName, moduleId);

                // Insert metadata
                String sql = """
                    INSERT INTO Custom_Field_Metadata 
                    (Module_ID, CustomFieldName, DisplayName, is_Mandatory, is_Searchable, 
                     DataType, Default_Value, Description, Placeholder_Text, LastUpdate_UserID)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

                int fieldId;
                try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, moduleId);
                    stmt.setString(2, customFieldName);
                    stmt.setString(3, displayName);
                    stmt.setBoolean(4, mandatory);
                    stmt.setBoolean(5, searchable);
                    stmt.setString(6, dataType);
                    stmt.setString(7, defaultValue);
                    stmt.setString(8, description);
                    stmt.setString(9, placeholder);
                    stmt.setInt(10, userId);
                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            fieldId = rs.getInt(1);
                        } else {
                            throw new SQLException("Failed to get generated field ID");
                        }
                    }
                }

                // Create enum values for dropdown/multiselect
                if (("dropdown".equals(dataType) || "multiselect".equals(dataType)) && data.get("dropdownValues") != null) {
                    String dropdownValuesStr = (String) data.get("dropdownValues");
                    if (dropdownValuesStr != null && !dropdownValuesStr.trim().isEmpty()) {
                        List<String> enumValues = Arrays.asList(dropdownValuesStr.split("\n"));
                        createEnumValues(conn, fieldId, enumValues);
                    }
                }

                conn.commit();
                
                // Log activity
                Map<String, Object> newState = new HashMap<>();
                newState.put("name", displayName);
                newState.put("isMandatory", mandatory);
                newState.put("type", dataType);
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    newState.put("defaultValue", defaultValue);
                }
                if (description != null && !description.isEmpty()) {
                    newState.put("description", description);
                }
                if (placeholder != null && !placeholder.isEmpty()) {
                    newState.put("placeholder", placeholder);
                }
                newState.put("searchable", searchable);
                if (("dropdown".equals(dataType) || "multiselect".equals(dataType)) && data.get("dropdownValues") != null) {
                    String dropdownValuesStr = (String) data.get("dropdownValues");
                    if (dropdownValuesStr != null && !dropdownValuesStr.trim().isEmpty()) {
                        newState.put("dropdownValues", dropdownValuesStr);
                    }
                }
                // Pass contextMap with facetName for component name
                Map<String, Object> contextMap = new HashMap<>();
                contextMap.put("facetName", facetId); // facetId is already the facet name
                ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_CUSTOM_FIELDS, facetId,
                    ActivityLogConstants.CHANGE_TYPE_CREATE, null, newState, contextMap);
                
                response.put("success", true);
                response.put("fieldId", fieldId);
                response.put("message", "Custom field created successfully");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return response;
    }

    private Map<String, Object> updateCustomFieldMetadata(Map<String, Object> data, int userId, HttpServletRequest req) throws SQLException {
        Map<String, Object> response = new HashMap<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int fieldId = toInt(data.get("fieldId"));
                String requestedType = data.get("type") != null ? String.valueOf(data.get("type")).trim() : "";
                boolean mandatory = data.get("mandatory") != null && (Boolean) data.get("mandatory");
                String defaultValue = data.get("defaultValue") != null ? (String) data.get("defaultValue") : "";
                String placeholder = data.get("placeholder") != null ? (String) data.get("placeholder") : "";
                String description = data.get("description") != null ? (String) data.get("description") : "";
                List<String> enumValues = Collections.emptyList();

                Integer moduleId = getModuleIdByFieldId(conn, fieldId);
                if (moduleId == null) {
                    response.put("success", false);
                    response.put("error", "Custom field not found");
                    return response;
                }

                Map<String, Object> oldState = getCustomFieldMetadataById(conn, fieldId);
                if (oldState == null) {
                    response.put("success", false);
                    response.put("error", "Custom field not found");
                    return response;
                }

                // Fall back to existing values for fields not sent by the client
                String rawDisplayName = data.get("displayName") != null ? ((String) data.get("displayName")).trim() : null;
                String displayName = (rawDisplayName != null && !rawDisplayName.isEmpty())
                    ? rawDisplayName
                    : (String) oldState.get("name");
                // Preserve the existing searchable value if the client does not send it
                boolean searchable = data.get("searchable") != null
                    ? (Boolean) data.get("searchable")
                    : Boolean.TRUE.equals(oldState.get("searchable"));

                String dataType = oldState.get("type") != null ? String.valueOf(oldState.get("type")).trim() : "";
                if (dataType.isEmpty()) {
                    response.put("success", false);
                    response.put("error", "Custom field not found");
                    return response;
                }
                if (!requestedType.isEmpty()
                        && !normalizeDataType(requestedType).equals(normalizeDataType(dataType))) {
                    response.put("success", false);
                    response.put("error", "Field type cannot be changed after creation");
                    return response;
                }

                if (isEnumType(dataType)) {
                    String dropdownValuesStr = data.get("dropdownValues") != null ? (String) data.get("dropdownValues") : "";
                    enumValues = normalizeEnumValues(dropdownValuesStr);
                    if (enumValues.isEmpty()) {
                        response.put("success", false);
                        response.put("error", "Dropdown values are required");
                        return response;
                    }
                    if (defaultValue != null && !defaultValue.trim().isEmpty()) {
                        boolean defaultExists = enumValues.stream()
                            .anyMatch(v -> v.equalsIgnoreCase(defaultValue.trim()));
                        if (!defaultExists) {
                            response.put("success", false);
                            response.put("error", "Default value must be one of the dropdown choices");
                            return response;
                        }
                    }
                }

                // Check for blocking change requests before update
                String facetId = oldState.get("facetName") != null ? (String) oldState.get("facetName") : null;
                if (facetId != null) {
                    boolean hasBlockingCR = hasBlockingChangeRequestForFacet(conn, facetId);
                    if (hasBlockingCR && hasRestrictedMetadataChanges(oldState, dataType, defaultValue, placeholder, searchable, enumValues)) {
                        response.put("success", false);
                        response.put("error", "Only name, description, and mandatory can be edited while a related change request is running or pending start");
                        return response;
                    }
                }

                // Check display name uniqueness (excluding current field)
                if (!isDisplayNameUnique(conn, displayName, moduleId, fieldId)) {
                    response.put("success", false);
                    response.put("error", "Display name must be unique for this facet");
                    return response;
                }

                // Update metadata
                String sql = """
                    UPDATE Custom_Field_Metadata 
                    SET DisplayName = ?, is_Mandatory = ?, is_Searchable = ?, DataType = ?, 
                        Default_Value = ?, Description = ?, Placeholder_Text = ?, 
                        LastUpdate_UserID = ?, LastUpdateDatetime = CURRENT_TIMESTAMP
                    WHERE ID = ?
                """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, displayName);
                    stmt.setBoolean(2, mandatory);
                    stmt.setBoolean(3, searchable);
                    stmt.setString(4, dataType);
                    stmt.setString(5, defaultValue);
                    stmt.setString(6, description);
                    stmt.setString(7, placeholder);
                    stmt.setInt(8, userId);
                    stmt.setInt(9, fieldId);
                    int rows = stmt.executeUpdate();
                    
                    if (rows == 0) {
                        response.put("success", false);
                        response.put("error", "Custom field not found");
                        return response;
                    }
                }

                // Update enum values
                if (isEnumType(dataType)) {
                    updateEnumValues(conn, fieldId, enumValues);
                } else {
                    // Remove enum values if type changed
                    deleteEnumValues(conn, fieldId);
                }

                conn.commit();
                
                // Log activity
                if (oldState != null && facetId != null) {
                    Map<String, Object> newState = new HashMap<>();
                    newState.put("name", displayName);
                    newState.put("isMandatory", mandatory);
                    newState.put("type", dataType);
                    if (defaultValue != null && !defaultValue.isEmpty()) {
                        newState.put("defaultValue", defaultValue);
                    }
                    if (description != null && !description.isEmpty()) {
                        newState.put("description", description);
                    }
                    if (placeholder != null && !placeholder.isEmpty()) {
                        newState.put("placeholder", placeholder);
                    }
                    newState.put("searchable", searchable);
                    if (("dropdown".equals(dataType) || "multiselect".equals(dataType)) && data.get("dropdownValues") != null) {
                        String dropdownValuesStr = (String) data.get("dropdownValues");
                        if (!dropdownValuesStr.isEmpty()) {
                            newState.put("dropdownValues", dropdownValuesStr);
                        }
                    }
                    // Remove facetName from oldState for comparison (it's not a field name)
                    Map<String, Object> oldStateForComparison = new HashMap<>(oldState);
                    oldStateForComparison.remove("facetName");
                    // Pass contextMap with facetName for component name
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("facetName", facetId); // facetId is already the facet name
                    ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_CUSTOM_FIELDS, facetId,
                        ActivityLogConstants.CHANGE_TYPE_UPDATE, oldStateForComparison, newState, contextMap);
                }
                
                response.put("success", true);
                response.put("message", "Custom field updated successfully");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return response;
    }

    private Map<String, Object> deleteCustomFieldMetadata(int fieldId, int userId, HttpServletRequest req) throws SQLException {
        Map<String, Object> response = new HashMap<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check if field exists
                if (getModuleIdByFieldId(conn, fieldId) == null) {
                    response.put("success", false);
                    response.put("error", "Custom field not found");
                    return response;
                }

                // Get old state before deletion for logging
                Map<String, Object> oldState = getCustomFieldMetadataById(conn, fieldId);
                String facetId = oldState != null ? (String) oldState.get("facetName") : null;

                // Create audit records before deletion
                createAuditRecords(conn, fieldId, userId);

                // Delete field data
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Custom_Field_Data WHERE Custom_Field_Metadata_ID = ?")) {
                    stmt.setInt(1, fieldId);
                    stmt.executeUpdate();
                }

                // Delete enum values
                deleteEnumValues(conn, fieldId);

                // Delete metadata
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Custom_Field_Metadata WHERE ID = ?")) {
                    stmt.setInt(1, fieldId);
                    int rows = stmt.executeUpdate();
                    if (rows == 0) {
                        response.put("success", false);
                        response.put("error", "Custom field not found");
                        return response;
                    }
                }

                conn.commit();
                
                // Log activity
                if (oldState != null && facetId != null) {
                    // Remove facetName from oldState for logging (it's not a field name)
                    Map<String, Object> oldStateForLogging = new HashMap<>(oldState);
                    oldStateForLogging.remove("facetName");
                    // Pass contextMap with facetName for component name
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("facetName", facetId); // facetId is already the facet name
                    ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_CUSTOM_FIELDS, facetId,
                        ActivityLogConstants.CHANGE_TYPE_DELETE, oldStateForLogging, null, contextMap);
                }
                
                response.put("success", true);
                response.put("message", "Custom field deleted successfully");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return response;
    }

    // ============= DATA OPERATIONS =============

    private List<Map<String, Object>> getCustomFieldValues(String facetId, int objectId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer moduleId = getModuleIdByFacetName(conn, facetId);
            if (moduleId == null) {
                return new ArrayList<>();
            }
            return getCustomFieldValuesForModule(conn, moduleId, objectId);
        }
    }

    private List<Map<String, Object>> getCustomFieldValuesForModule(Connection conn, int moduleId, int facetObjectId) throws SQLException {
        List<Map<String, Object>> values = new ArrayList<>();
        String sql = """
                SELECT cfd.Custom_Field_Metadata_ID, cfd.Custom_Field_Enum_ID, cfd.Custom_Field_Value, cfm.DataType
                FROM Custom_Field_Data cfd
                INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                ORDER BY cfd.Custom_Field_Metadata_ID, cfd.Custom_Field_Enum_ID
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, moduleId);
            stmt.setInt(2, facetObjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> value = new HashMap<>();
                    value.put("metadataId", rs.getInt("Custom_Field_Metadata_ID"));
                    Integer enumId = rs.getObject("Custom_Field_Enum_ID") != null ? rs.getInt("Custom_Field_Enum_ID") : null;
                    value.put("enumId", enumId);
                    value.put("value", rs.getString("Custom_Field_Value"));
                    values.add(value);
                }
            }
        }
        return values;
    }

    /**
     * First save from the Custom Fields tab should create the same automatic edit CR as the summary tab
     * for Glossary, Data Set, System, and Process (only).
     */
    private static void ensureDfcrAutoCrForCustomFieldSave(Connection conn, String facetKey, int canonicalObjectId,
                                                           int userId, boolean isAdmin) {
        if (userId <= 0 || facetKey == null) {
            return;
        }
        String fk = facetKey.toLowerCase().trim();
        if (!"glossary".equals(fk) && !"dataset".equals(fk) && !"system".equals(fk) && !"process".equals(fk)) {
            return;
        }
        try {
            FacetChangesDAO facetDao = new FacetChangesDAO();
            Integer moduleFacetTypeId = facetDao.getFacetId(fk);
            if (moduleFacetTypeId == null) {
                return;
            }
            String dfcrFacetName;
            switch (fk) {
                case "glossary":
                    dfcrFacetName = "Glossary";
                    break;
                case "dataset":
                    dfcrFacetName = "Data Set";
                    break;
                case "system":
                    dfcrFacetName = "System";
                    break;
                case "process":
                    dfcrFacetName = "Process";
                    break;
                default:
                    return;
            }
            Integer typeId = loadDfcrObjectTypeId(conn, fk, canonicalObjectId);
            new DFCRService().ensureEditAutoCrIfMissing(dfcrFacetName, moduleFacetTypeId, canonicalObjectId, typeId, userId, isAdmin);
        } catch (Exception e) {
            System.err.println("[CustomFieldServlet] DFCR ensure before custom field save: " + e.getMessage());
        }
    }

    private static Integer loadDfcrObjectTypeId(Connection conn, String facetKey, int objectId) throws SQLException {
        switch (facetKey.toLowerCase().trim()) {
            case "glossary":
                try (PreparedStatement ps = conn.prepareStatement("SELECT Type FROM glossary WHERE ID = ?")) {
                    ps.setInt(1, objectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int t = rs.getInt("Type");
                            return rs.wasNull() ? null : t;
                        }
                    }
                }
                break;
            case "dataset":
                try (PreparedStatement ps = conn.prepareStatement("SELECT DatasetType FROM dataset WHERE ID = ?")) {
                    ps.setInt(1, objectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int t = rs.getInt("DatasetType");
                            return rs.wasNull() ? null : t;
                        }
                    }
                }
                break;
            case "system":
                try (PreparedStatement ps = conn.prepareStatement("SELECT Type FROM system WHERE ID = ?")) {
                    ps.setInt(1, objectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int t = rs.getInt("Type");
                            return rs.wasNull() ? null : t;
                        }
                    }
                }
                break;
            case "process":
                try (PreparedStatement ps = conn.prepareStatement("SELECT type FROM process WHERE id = ?")) {
                    ps.setInt(1, objectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int t = rs.getInt("type");
                            return rs.wasNull() ? null : t;
                        }
                    }
                }
                break;
            default:
                break;
        }
        return null;
    }

    private Map<String, Object> saveCustomFieldValues(Map<String, Object> data, int userId) throws SQLException {
        return saveCustomFieldValues(data, userId, true, false);
    }

    /**
     * When {@code validateMandatory} is false, skips mandatory-field validation (used only for trusted
     * server-side materialization of metadata defaults on new objects).
     */
    private Map<String, Object> saveCustomFieldValues(Map<String, Object> data, int userId, boolean validateMandatory,
                                                      boolean isAdmin) throws SQLException {
        Map<String, Object> response = new HashMap<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String facetId = (String) data.get("facetId");
                Integer objectId = toInteger(data.get("objectId"));
                if (objectId == null) {
                    response.put("success", false);
                    response.put("error", "objectId is required");
                    return response;
                }
                final int canonicalObjectId = objectId;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> values = (List<Map<String, Object>>) data.get("values");

                Integer moduleId = getModuleIdByFacetName(conn, facetId);
                if (moduleId == null) {
                    response.put("success", false);
                    response.put("error", "Facet not found");
                    return response;
                }

                String modulePrimary = getModulePrimaryName(conn, moduleId);
                String facetKey = CustomFieldPendingFacetHelper.toFacetChangesKeyFromModulePrimaryName(modulePrimary);
                ensureDfcrAutoCrForCustomFieldSave(conn, facetKey, canonicalObjectId, userId, isAdmin);
                int dataObjectId = CustomFieldPendingFacetHelper.resolveEffectiveFacetObjectId(
                        conn, moduleId, facetKey, canonicalObjectId, null, true);

                if (validateMandatory) {
                    Map<Integer, CustomFieldValidationMeta> metadataById = loadModuleCustomFieldValidationMeta(conn, moduleId);
                    String validationError = validateIncomingCustomFieldValues(values, metadataById);
                    if (validationError != null) {
                        conn.rollback();
                        response.put("success", false);
                        response.put("error", validationError);
                        return response;
                    }
                }

                // Load current values before any delete (for facet history diff)
                Map<Integer, Map<String, String>> oldValues = loadCurrentCustomFieldValues(conn, moduleId, dataObjectId);
                Map<Integer, Map<String, String>> newValues = buildNewCustomFieldValueMap(conn, values, moduleId);

                // Create audit records
                createAuditRecordsForObject(conn, moduleId, dataObjectId, userId);

                // Delete audit records first (to avoid foreign key constraint violation)
                // Delete audit records that reference the data records we're about to delete
                try (PreparedStatement stmt = conn.prepareStatement("""
                    DELETE cfa FROM Custom_Field_Data_Audit cfa
                    INNER JOIN Custom_Field_Data cfd ON cfa.ID = cfd.ID
                    INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                    WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """)) {
                    stmt.setInt(1, moduleId);
                    stmt.setInt(2, dataObjectId);
                    stmt.executeUpdate();
                }

                // Delete existing values (now safe since audit records are deleted)
                try (PreparedStatement stmt = conn.prepareStatement("""
                    DELETE cfd FROM Custom_Field_Data cfd
                    INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                    WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """)) {
                    stmt.setInt(1, moduleId);
                    stmt.setInt(2, dataObjectId);
                    stmt.executeUpdate();
                }

                // Insert new values
                if (values != null && !values.isEmpty()) {
                    String insertSql = """
                        INSERT INTO Custom_Field_Data
                        (Custom_Field_Metadata_ID, Custom_Field_Enum_ID, Facet_Object_ID, Custom_Field_Value, LastUpdate_UserID, CreateDatetime)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """;

                    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                        for (Map<String, Object> valueData : values) {
                            Integer metadataId = toInteger(valueData.get("metadataId"));
                            if (metadataId == null) {
                                continue;
                            }
                            Integer enumId = toInteger(valueData.get("enumId"));
                            Object rawValue = valueData.get("value");
                            String value = rawValue != null ? String.valueOf(rawValue) : null;

                            // Verify metadata belongs to this module
                            if (!verifyMetadataBelongsToModule(conn, metadataId, moduleId)) {
                                continue;
                            }

                            // Get data type
                            String dataType = getDataType(conn, metadataId);
                            if (dataType == null) continue;
                            String normalizedDataType = normalizeDataType(dataType);

                            if (isEnumType(normalizedDataType)) {
                                if (enumId != null) {
                                    stmt.setInt(1, metadataId);
                                    stmt.setInt(2, enumId);
                                    stmt.setInt(3, dataObjectId);
                                    stmt.setNull(4, Types.LONGVARCHAR);
                                    stmt.setInt(5, userId);
                                    stmt.addBatch();
                                }
                            } else if (isCheckboxType(normalizedDataType)) {
                                String normalized = (value != null && "true".equalsIgnoreCase(value.trim())) ? "true" : "false";
                                stmt.setInt(1, metadataId);
                                stmt.setNull(2, Types.INTEGER);
                                stmt.setInt(3, dataObjectId);
                                stmt.setString(4, normalized);
                                stmt.setInt(5, userId);
                                stmt.addBatch();
                            } else {
                                if (value != null && !value.trim().isEmpty()) {
                                    stmt.setInt(1, metadataId);
                                    stmt.setNull(2, Types.INTEGER);
                                    stmt.setInt(3, dataObjectId);
                                    stmt.setString(4, value);
                                    stmt.setInt(5, userId);
                                    stmt.addBatch();
                                }
                            }
                        }
                        stmt.executeBatch();
                    }
                }

                // Write facet audit history for custom field changes (for history tab)
                String auditTable = getAuditTableNameByModuleId(conn, moduleId);
                String objectDisplayName = getModulePrimaryName(conn, moduleId);
                String author = getUserFullNameForAudit(conn, userId);
                if (auditTable != null && objectDisplayName != null) {
                    Set<Integer> allKeys = new HashSet<>();
                    allKeys.addAll(oldValues.keySet());
                    allKeys.addAll(newValues.keySet());
                    for (Integer metadataId : allKeys) {
                        Map<String, String> oldRow = oldValues.get(metadataId);
                        Map<String, String> newRow = newValues.get(metadataId);
                        String fieldName = oldRow != null ? oldRow.get("displayName") : (newRow != null ? newRow.get("displayName") : "Field " + metadataId);
                        if (fieldName == null) fieldName = "Field " + metadataId;
                        // Classify by the actual (normalized) value rather than map presence.
                        // A blank custom field is never persisted to Custom_Field_Data, so it stays
                        // absent from oldValues on every save; comparing presence alone logs a phantom
                        // "Added" row each time. Comparing normalized values avoids that.
                        String oldVal = normalizeCustomFieldAuditValue(oldRow != null ? oldRow.get("value") : null);
                        String newVal = normalizeCustomFieldAuditValue(newRow != null ? newRow.get("value") : null);
                        boolean oldBlank = oldVal.isEmpty();
                        boolean newBlank = newVal.isEmpty();
                        if (oldVal.equals(newVal)) {
                            continue; // unchanged (including blank -> blank): no history row
                        }
                        if (oldBlank) {
                            insertFacetAuditRecordForCustomField(conn, auditTable, objectDisplayName, canonicalObjectId, "Added", fieldName, null, newVal, author);
                        } else if (newBlank) {
                            insertFacetAuditRecordForCustomField(conn, auditTable, objectDisplayName, canonicalObjectId, "Removed", fieldName, oldVal, null, author);
                        } else {
                            insertFacetAuditRecordForCustomField(conn, auditTable, objectDisplayName, canonicalObjectId, "Changed", fieldName, oldVal, newVal, author);
                        }
                    }
                }

                conn.commit();
                response.put("success", true);
                response.put("message", "Custom field values saved successfully");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return response;
    }

    /**
     * Called when a new attribute row is created. Writes custom field values from metadata {@code Default_Value}
     * only if this object has no {@code Custom_Field_Data} rows yet. Legacy attributes never hit this path on
     * create again, so they remain without CF rows until the user saves — the UI shows them as unspecified.
     */
    public static void materializeAttributeDefaultsIfNoDataYet(int attributeId, int userId) {
        if (attributeId <= 0 || userId <= 0) {
            return;
        }
        CustomFieldServlet servlet = new CustomFieldServlet();
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer moduleId = servlet.getModuleIdByFacetName(conn, "Attribute");
            if (moduleId == null) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM Custom_Field_Data cfd
                INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """)) {
                ps.setInt(1, moduleId);
                ps.setInt(2, attributeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return;
                    }
                }
            }
            List<Map<String, Object>> values = servlet.buildValuesFromMetadataDefaults(conn, moduleId);
            if (values.isEmpty()) {
                return;
            }
            String facetPrimary = servlet.getModulePrimaryName(conn, moduleId);
            if (facetPrimary == null || facetPrimary.isBlank()) {
                facetPrimary = "Attribute";
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("facetId", facetPrimary);
            payload.put("objectId", attributeId);
            payload.put("values", values);
            Map<String, Object> result = servlet.saveCustomFieldValues(payload, userId, false, false);
            if (Boolean.FALSE.equals(result.get("success"))) {
                System.err.println("materializeAttributeDefaultsIfNoDataYet: " + result.get("error"));
            }
        } catch (SQLException e) {
            System.err.println("materializeAttributeDefaultsIfNoDataYet: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildValuesFromMetadataDefaults(Connection conn, int moduleId) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT ID, DataType, Default_Value FROM Custom_Field_Metadata WHERE Module_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int metadataId = rs.getInt("ID");
                    String dataType = rs.getString("DataType");
                    String defaultVal = rs.getString("Default_Value");
                    if (dataType == null) {
                        continue;
                    }
                    String norm = normalizeDataType(dataType);
                    if (isEnumType(norm)) {
                        if (defaultVal == null || defaultVal.trim().isEmpty()) {
                            continue;
                        }
                        if ("multiselect".equals(norm)) {
                            String[] parts = defaultVal.split(",");
                            for (String part : parts) {
                                String trimmed = part.trim();
                                if (trimmed.isEmpty()) {
                                    continue;
                                }
                                Integer enumId = lookupEnumIdByLabel(conn, metadataId, trimmed);
                                if (enumId != null) {
                                    Map<String, Object> row = new HashMap<>();
                                    row.put("metadataId", metadataId);
                                    row.put("enumId", enumId);
                                    row.put("value", null);
                                    out.add(row);
                                }
                            }
                        } else {
                            Integer enumId = lookupEnumIdByLabel(conn, metadataId, defaultVal.trim());
                            if (enumId != null) {
                                Map<String, Object> row = new HashMap<>();
                                row.put("metadataId", metadataId);
                                row.put("enumId", enumId);
                                row.put("value", null);
                                out.add(row);
                            }
                        }
                    } else if (isCheckboxType(norm)) {
                        if (defaultVal == null || defaultVal.trim().isEmpty()) {
                            continue;
                        }
                        Map<String, Object> row = new HashMap<>();
                        row.put("metadataId", metadataId);
                        row.put("enumId", null);
                        row.put("value", "true".equalsIgnoreCase(defaultVal.trim()) ? "true" : "false");
                        out.add(row);
                    } else {
                        if (defaultVal == null || defaultVal.trim().isEmpty()) {
                            continue;
                        }
                        Map<String, Object> row = new HashMap<>();
                        row.put("metadataId", metadataId);
                        row.put("enumId", null);
                        row.put("value", defaultVal.trim());
                        out.add(row);
                    }
                }
            }
        }
        return out;
    }

    private Integer lookupEnumIdByLabel(Connection conn, int metadataId, String label) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ID FROM Custom_Field_Enum WHERE Custom_Field_Metadata_ID = ? AND LOWER(TRIM(EnumValue)) = LOWER(?)")) {
            ps.setInt(1, metadataId);
            ps.setString(2, label.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        return null;
    }

    // ============= HELPER METHODS =============

    private Integer getModuleIdByFacetName(Connection conn, String facetName) throws SQLException {
        if (facetName == null || facetName.trim().isEmpty()) {
            return null;
        }

        String raw = facetName.trim();

        // 1) If facet is a numeric module ID, resolve directly.
        try {
            int moduleId = Integer.parseInt(raw);
            try (PreparedStatement byId = conn.prepareStatement("SELECT id FROM module WHERE id = ?")) {
                byId.setInt(1, moduleId);
                try (ResultSet rs = byId.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (NumberFormatException ignored) {
            // Not a numeric id; continue to name resolution.
        }

        // 2) Try exact primaryname first.
        try (PreparedStatement exact = conn.prepareStatement("SELECT id FROM module WHERE primaryname = ?")) {
            exact.setString(1, raw);
            try (ResultSet rs = exact.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        // 3) Try normalized canonical module name used by permission checks.
        String canonicalName = normalizeFacetIdToModuleName(raw);
        if (canonicalName != null && !canonicalName.equals(raw)) {
            try (PreparedStatement canonical = conn.prepareStatement("SELECT id FROM module WHERE primaryname = ?")) {
                canonical.setString(1, canonicalName);
                try (ResultSet rs = canonical.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        }

        // 4) Fallback: normalized comparison over module names
        String normalizedRaw = normalizeFacetForCompare(raw);
        String normalizedRawRoot = normalizeFacetRootForCompare(raw);
        String normalizedCanonical = canonicalName != null ? normalizeFacetForCompare(canonicalName) : null;
        String normalizedCanonicalRoot = canonicalName != null ? normalizeFacetRootForCompare(canonicalName) : null;
        try (PreparedStatement all = conn.prepareStatement("SELECT id, primaryname FROM module")) {
            try (ResultSet rs = all.executeQuery()) {
                while (rs.next()) {
                    String primaryName = rs.getString("primaryname");
                    if (primaryName == null || primaryName.trim().isEmpty()) {
                        continue;
                    }
                    String normalizedPrimary = normalizeFacetForCompare(primaryName);
                    String normalizedPrimaryRoot = normalizeFacetRootForCompare(primaryName);
                    if (normalizedPrimary.equals(normalizedRaw) ||
                            normalizedPrimaryRoot.equals(normalizedRawRoot) ||
                            (normalizedCanonical != null && (normalizedPrimary.equals(normalizedCanonical) ||
                                    normalizedPrimaryRoot.equals(normalizedCanonicalRoot)))) {
                        return rs.getInt("id");
                    }
                }
            }
        }

        return null;
    }

    private String normalizeFacetForCompare(String value) {
        if (value == null) return "";
        return value.toLowerCase().replaceAll("[\\s_\\-]+", "");
    }

    private String normalizeFacetRootForCompare(String value) {
        String normalized = normalizeFacetForCompare(value);
        if (normalized.endsWith("ies") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 3) + "y";
        }
        if (normalized.endsWith("es") && normalized.length() > 2) {
            return normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("s") && normalized.length() > 1) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Integer getModuleIdByFieldId(Connection conn, int fieldId) throws SQLException {
        String sql = "SELECT Module_ID FROM Custom_Field_Metadata WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fieldId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Module_ID");
                }
            }
        }
        return null;
    }

    /**
     * Normalise a module/facet primaryName to the form used in CR Reference values.
     * FacetChangesService.normalizeFacetNameForReference applies title-case with a
     * special rule for "dataset" → "Data Set". We mirror that logic here so that
     * modules stored as "dataset" still match CRs whose Reference starts with "Data Set".
     */
    private String normalizeFacetNameForCRLookup(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        // Collapse spaces / hyphens / underscores so "data sets", "Data-Sets", "datasets" all match
        String compact = lower.replaceAll("[\\s_\\-]+", "");
        // DatasetServlet / DatasetImpactServlet always pass "Data Set" to DFCR (singular); Reference is "Data Set {id}".
        // Module.primaryname is often plural ("Data Sets") or one word ("Dataset") — those must still resolve to the same prefix.
        if ("dataset".equals(compact) || "datasets".equals(compact)) {
            return "Data Set";
        }
        switch (lower) {
            case "dataset":
            case "data set":
            case "datasets":
            case "data sets":
            case "data-sets":
            case "data_sets":
                return "Data Set";
            default:
                // Title-case: capitalise first letter of every word
                String[] words = trimmed.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < words.length; i++) {
                    if (i > 0) sb.append(" ");
                    if (words[i].length() > 0) {
                        sb.append(Character.toUpperCase(words[i].charAt(0)));
                        if (words[i].length() > 1) sb.append(words[i].substring(1).toLowerCase(java.util.Locale.ROOT));
                    }
                }
                return sb.toString();
        }
    }

    private boolean hasBlockingChangeRequestForFacet(Connection conn, String facetName) throws SQLException {
        if (facetName == null || facetName.trim().isEmpty()) {
            return false;
        }
        // Build two name variants: raw (as stored in module table) and normalised (as used in CR References)
        String raw  = facetName.trim();
        String norm = normalizeFacetNameForCRLookup(raw);

        // Consider any active (non-completed, non-cancelled) CR that references any object
        // in this facet as blocking. CRs are stored as "FacetName objectId" (e.g. "Data Set 42").
        // We check both the exact name and the "name + space" prefix to cover all stored variants.
        String sql = """
            SELECT COUNT(*) AS cnt
            FROM changerequest cr
            LEFT JOIN changerequeststatus cs ON cs.ID = cr.CR_StatusID
            WHERE (
                      cr.Reference = ?
                   OR cr.Reference LIKE ?
                   OR cr.Reference = ?
                   OR cr.Reference LIKE ?
                  )
              AND cr.Deleted_At IS NULL
              AND (
                    cr.CR_StatusID IS NULL
                 OR (
                        UPPER(COALESCE(cs.PrimaryName, '')) NOT LIKE '%COMPLETED%'
                    AND UPPER(COALESCE(cs.PrimaryName, '')) NOT LIKE '%CANCELLED%'
                    AND UPPER(COALESCE(cs.PrimaryName, '')) NOT LIKE '%CANCELED%'
                 )
              )
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, raw);
            stmt.setString(2, raw  + " %");
            stmt.setString(3, norm);
            stmt.setString(4, norm + " %");
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt("cnt") > 0;
            }
        }
    }

    private boolean hasRestrictedMetadataChanges(
        Map<String, Object> oldState,
        String dataType,
        String defaultValue,
        String placeholder,
        boolean searchable,
        List<String> enumValues
    ) {
        String oldType = oldState.get("type") != null ? String.valueOf(oldState.get("type")) : "";
        String oldDefaultValue = oldState.get("defaultValue") != null ? String.valueOf(oldState.get("defaultValue")) : "";
        String oldPlaceholder = oldState.get("placeholder") != null ? String.valueOf(oldState.get("placeholder")) : "";
        boolean oldSearchable = oldState.get("searchable") instanceof Boolean && (Boolean) oldState.get("searchable");
        String oldDropdownValues = oldState.get("dropdownValues") != null ? String.valueOf(oldState.get("dropdownValues")) : "";

        String newType = dataType != null ? dataType : "";
        String newDefaultValue = defaultValue != null ? defaultValue : "";
        String newPlaceholder = placeholder != null ? placeholder : "";
        String newDropdownValues = String.join("\n", enumValues != null ? enumValues : Collections.emptyList());

        if (!normalizeDataType(oldType).equals(normalizeDataType(newType))) {
            return true;
        }
        if (!oldDefaultValue.trim().equals(newDefaultValue.trim())) {
            return true;
        }
        if (!oldPlaceholder.trim().equals(newPlaceholder.trim())) {
            return true;
        }
        if (oldSearchable != searchable) {
            return true;
        }
        return !oldDropdownValues.trim().equals(newDropdownValues.trim());
    }

    private Map<String, Object> getCustomFieldMetadataById(Connection conn, int fieldId) throws SQLException {
        String sql = """
            SELECT cfm.ID, cfm.CustomFieldName, cfm.DisplayName, cfm.is_Mandatory, cfm.is_Searchable,
                   cfm.DataType, cfm.Default_Value, cfm.Description, cfm.Placeholder_Text,
                   m.PrimaryName AS FacetName
            FROM Custom_Field_Metadata cfm
            JOIN Module m ON cfm.Module_ID = m.ID
            WHERE cfm.ID = ?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fieldId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> field = new HashMap<>();
                    field.put("id", rs.getInt("ID"));
                    field.put("customFieldName", rs.getString("CustomFieldName"));
                    field.put("name", rs.getString("DisplayName"));
                    field.put("isMandatory", rs.getBoolean("is_Mandatory"));
                    field.put("searchable", rs.getBoolean("is_Searchable"));
                    field.put("type", rs.getString("DataType"));
                    field.put("defaultValue", rs.getString("Default_Value"));
                    field.put("description", rs.getString("Description"));
                    field.put("placeholder", rs.getString("Placeholder_Text"));
                    field.put("facetName", rs.getString("FacetName"));
                    // Get enum values if applicable
                    String dataType = rs.getString("DataType");
                    if (isEnumType(dataType)) {
                        List<Map<String, Object>> enumValueList = getEnumValues(conn, fieldId);
                        List<String> enumValues = new ArrayList<>();
                        for (Map<String, Object> e : enumValueList) {
                            enumValues.add((String) e.get("enumValue"));
                        }
                        field.put("dropdownValues", String.join("\n", enumValues));
                    }
                    return field;
                }
            }
        }
        return null;
    }

    private boolean isDisplayNameUnique(Connection conn, String displayName, int moduleId, Integer excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Custom_Field_Metadata WHERE DisplayName = ? AND Module_ID = ?";
        if (excludeId != null) {
            sql += " AND ID != ?";
        }
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, displayName);
            stmt.setInt(2, moduleId);
            if (excludeId != null) {
                stmt.setInt(3, excludeId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        }
        return false;
    }

    private String generateCustomFieldName(Connection conn, String displayName, int moduleId) throws SQLException {
        String base = "cf_" + displayName.trim()
            .replaceAll("[^a-zA-Z0-9\\s]", "")
            .replaceAll("\\s+", "_")
            .toLowerCase();
        
        String candidate = base;
        int index = 1;
        while (customFieldNameExists(conn, candidate, moduleId)) {
            candidate = base + "_" + index;
            index++;
        }
        return candidate;
    }

    private boolean customFieldNameExists(Connection conn, String customFieldName, int moduleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Custom_Field_Metadata WHERE CustomFieldName = ? AND Module_ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customFieldName);
            stmt.setInt(2, moduleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private List<Map<String, Object>> getEnumValues(Connection conn, int metadataId) throws SQLException {
        List<Map<String, Object>> enumValues = new ArrayList<>();
        String sql = "SELECT ID, EnumValue FROM Custom_Field_Enum WHERE Custom_Field_Metadata_ID = ? ORDER BY EnumValue";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, metadataId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> enumValue = new HashMap<>();
                    enumValue.put("id", rs.getInt("ID"));
                    enumValue.put("enumValue", rs.getString("EnumValue"));
                    enumValues.add(enumValue);
                }
            }
        }
        return enumValues;
    }

    private void createEnumValues(Connection conn, int metadataId, List<String> enumValues) throws SQLException {
        String sql = "INSERT INTO Custom_Field_Enum (Custom_Field_Metadata_ID, EnumValue) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String value : enumValues) {
                if (value != null && !value.trim().isEmpty()) {
                    stmt.setInt(1, metadataId);
                    stmt.setString(2, value.trim());
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        }
    }

    private void updateEnumValues(Connection conn, int metadataId, List<String> enumValues) throws SQLException {
        List<String> normalizedEnumValues = normalizeEnumValues(enumValues);
        Map<Integer, String> existingById = new HashMap<>();
        Map<String, Integer> existingByValue = new HashMap<>();

        String existingSql = "SELECT ID, EnumValue FROM Custom_Field_Enum WHERE Custom_Field_Metadata_ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(existingSql)) {
            stmt.setInt(1, metadataId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("ID");
                    String enumValue = rs.getString("EnumValue");
                    existingById.put(id, enumValue);
                    existingByValue.put(enumValue.toLowerCase(Locale.ROOT), id);
                }
            }
        }

        Set<Integer> keepIds = new HashSet<>();
        List<String> newValues = new ArrayList<>();
        for (String enumValue : normalizedEnumValues) {
            Integer existingId = existingByValue.get(enumValue.toLowerCase(Locale.ROOT));
            if (existingId != null) {
                keepIds.add(existingId);
            } else {
                newValues.add(enumValue);
            }
        }

        List<Integer> deleteIds = new ArrayList<>();
        for (Integer existingId : existingById.keySet()) {
            if (!keepIds.contains(existingId)) {
                deleteIds.add(existingId);
            }
        }

        if (!deleteIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(deleteIds.size(), "?"));
            String inUseSql = "SELECT COUNT(*) FROM Custom_Field_Data WHERE Custom_Field_Enum_ID IN (" + placeholders + ")";
            try (PreparedStatement stmt = conn.prepareStatement(inUseSql)) {
                for (int i = 0; i < deleteIds.size(); i++) {
                    stmt.setInt(i + 1, deleteIds.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Cannot delete dropdown option because it is already used by existing records");
                    }
                }
            }

            String deleteSql = "DELETE FROM Custom_Field_Enum WHERE ID IN (" + placeholders + ")";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                for (int i = 0; i < deleteIds.size(); i++) {
                    stmt.setInt(i + 1, deleteIds.get(i));
                }
                stmt.executeUpdate();
            }
        }

        if (!newValues.isEmpty()) {
            createEnumValues(conn, metadataId, newValues);
        }
    }

    private void deleteEnumValues(Connection conn, int metadataId) throws SQLException {
        String inUseSql = """
            SELECT COUNT(*)
            FROM Custom_Field_Data cfd
            INNER JOIN Custom_Field_Enum cfe ON cfd.Custom_Field_Enum_ID = cfe.ID
            WHERE cfe.Custom_Field_Metadata_ID = ?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(inUseSql)) {
            stmt.setInt(1, metadataId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new SQLException("Cannot delete dropdown options because they are used by existing records");
                }
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Custom_Field_Enum WHERE Custom_Field_Metadata_ID = ?")) {
            stmt.setInt(1, metadataId);
            stmt.executeUpdate();
        }
    }

    private List<String> normalizeEnumValues(String dropdownValues) {
        if (dropdownValues == null || dropdownValues.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return normalizeEnumValues(Arrays.asList(dropdownValues.split("\n")));
    }

    private List<String> normalizeEnumValues(List<String> enumValues) {
        if (enumValues == null || enumValues.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : enumValues) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private Map<Integer, Map<String, Object>> getCustomFieldValuesMap(Connection conn, int moduleId, int objectId) throws SQLException {
        Map<Integer, Map<String, Object>> values = new HashMap<>();
        String sql = """
            SELECT cfd.Custom_Field_Metadata_ID, cfd.Custom_Field_Enum_ID, cfd.Custom_Field_Value, cfm.DataType
            FROM Custom_Field_Data cfd
            INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
            WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
            ORDER BY cfd.Custom_Field_Metadata_ID, cfd.Custom_Field_Enum_ID
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, moduleId);
            stmt.setInt(2, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int metadataId = rs.getInt("Custom_Field_Metadata_ID");
                    Integer enumId = rs.getObject("Custom_Field_Enum_ID") != null ? rs.getInt("Custom_Field_Enum_ID") : null;
                    String valueStr = rs.getString("Custom_Field_Value");
                    String dataType = rs.getString("DataType");
                    
                    Map<String, Object> value = values.get(metadataId);
                    if (value == null) {
                        value = new HashMap<>();
                        value.put("enumId", enumId);
                        value.put("value", valueStr);
                        // For multiselect, store enumIds as a list
                        if ("multiselect".equalsIgnoreCase(dataType)) {
                            List<Integer> enumIds = new ArrayList<>();
                            if (enumId != null) {
                                enumIds.add(enumId);
                            }
                            value.put("enumIds", enumIds);
                        }
                        values.put(metadataId, value);
                    } else {
                        // For multiselect, accumulate enum IDs
                        if ("multiselect".equalsIgnoreCase(dataType) && enumId != null) {
                            @SuppressWarnings("unchecked")
                            List<Integer> enumIds = (List<Integer>) value.get("enumIds");
                            if (enumIds == null) {
                                enumIds = new ArrayList<>();
                                value.put("enumIds", enumIds);
                            }
                            enumIds.add(enumId);
                        }
                    }
                }
            }
        }
        return values;
    }

    private boolean verifyMetadataBelongsToModule(Connection conn, int metadataId, int moduleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Custom_Field_Metadata WHERE ID = ? AND Module_ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, metadataId);
            stmt.setInt(2, moduleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private String getDataType(Connection conn, int metadataId) throws SQLException {
        String sql = "SELECT DataType FROM Custom_Field_Metadata WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, metadataId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("DataType");
                }
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeDataType(String dataType) {
        if (dataType == null) return "";
        String normalized = dataType.trim().toLowerCase().replace("_", "").replace("-", "").replace(" ", "");
        if (normalized.equals("multipledropdown")) return "multiselect";
        if (normalized.equals("singledropdown")) return "dropdown";
        if (normalized.equals("boolean")) return "checkbox";
        return normalized;
    }

    private boolean isEnumType(String dataType) {
        String normalized = normalizeDataType(dataType);
        return "dropdown".equals(normalized) || "multiselect".equals(normalized);
    }

    private boolean isCheckboxType(String dataType) {
        return "checkbox".equals(normalizeDataType(dataType));
    }

    private Map<Integer, CustomFieldValidationMeta> loadModuleCustomFieldValidationMeta(Connection conn, int moduleId) throws SQLException {
        Map<Integer, CustomFieldValidationMeta> out = new HashMap<>();
        String sql = """
            SELECT ID, DisplayName, is_Mandatory, DataType
            FROM Custom_Field_Metadata
            WHERE Module_ID = ?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, moduleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int metadataId = rs.getInt("ID");
                    String displayName = rs.getString("DisplayName");
                    boolean mandatory = rs.getBoolean("is_Mandatory");
                    String dataType = normalizeDataType(rs.getString("DataType"));
                    out.put(metadataId, new CustomFieldValidationMeta(metadataId, displayName, mandatory, dataType));
                }
            }
        }
        return out;
    }

    private String validateIncomingCustomFieldValues(List<Map<String, Object>> values, Map<Integer, CustomFieldValidationMeta> metadataById) {
        Map<Integer, IncomingFieldState> incomingState = new HashMap<>();

        if (values != null) {
            for (Map<String, Object> valueData : values) {
                Integer metadataId = toInteger(valueData.get("metadataId"));
                if (metadataId == null) {
                    continue;
                }
                CustomFieldValidationMeta meta = metadataById.get(metadataId);
                if (meta == null) {
                    continue;
                }

                IncomingFieldState state = incomingState.computeIfAbsent(metadataId, k -> new IncomingFieldState());
                Integer enumId = toInteger(valueData.get("enumId"));
                Object rawValue = valueData.get("value");
                String value = rawValue != null ? String.valueOf(rawValue).trim() : null;

                if (enumId != null) {
                    state.hasEnumValue = true;
                }
                if (value != null && !value.isEmpty()) {
                    state.hasTextValue = true;
                }

                if ("percentage".equals(meta.dataType) && value != null && !value.isEmpty()) {
                    try {
                        double percentageValue = Double.parseDouble(value);
                        if (percentageValue < 0 || percentageValue > 100) {
                            return "Percentage value for '" + meta.getDisplayNameSafe() + "' must be between 0 and 100";
                        }
                    } catch (NumberFormatException e) {
                        return "Percentage value for '" + meta.getDisplayNameSafe() + "' must be numeric";
                    }
                }
            }
        }

        for (CustomFieldValidationMeta meta : metadataById.values()) {
            if (!meta.mandatory || "checkbox".equals(meta.dataType)) {
                continue;
            }

            IncomingFieldState state = incomingState.get(meta.metadataId);
            boolean hasValue;
            if ("dropdown".equals(meta.dataType) || "multiselect".equals(meta.dataType)) {
                hasValue = state != null && state.hasEnumValue;
            } else {
                hasValue = state != null && state.hasTextValue;
            }

            if (!hasValue) {
                return "Mandatory custom field '" + meta.getDisplayNameSafe() + "' must have a value";
            }
        }

        return null;
    }

    private static class CustomFieldValidationMeta {
        final int metadataId;
        final String displayName;
        final boolean mandatory;
        final String dataType;

        CustomFieldValidationMeta(int metadataId, String displayName, boolean mandatory, String dataType) {
            this.metadataId = metadataId;
            this.displayName = displayName;
            this.mandatory = mandatory;
            this.dataType = dataType != null ? dataType : "";
        }

        String getDisplayNameSafe() {
            if (displayName == null || displayName.trim().isEmpty()) {
                return "Field " + metadataId;
            }
            return displayName;
        }
    }

    private static class IncomingFieldState {
        boolean hasEnumValue;
        boolean hasTextValue;
    }

    private void createAuditRecords(Connection conn, int fieldId, int userId) throws SQLException {
        String sql = """
            INSERT INTO Custom_Field_Data_Audit 
            (ID, Custom_Field_Metadata_ID, Custom_Field_Enum_ID, Facet_Object_ID, 
             Custom_Field_Value, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, RevType)
            SELECT ID, Custom_Field_Metadata_ID, Custom_Field_Enum_ID, Facet_Object_ID, 
                   Custom_Field_Value, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, 'DELETE'
            FROM Custom_Field_Data 
            WHERE Custom_Field_Metadata_ID = ?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fieldId);
            stmt.executeUpdate();
        }
    }

    private void createAuditRecordsForObject(Connection conn, int moduleId, int objectId, int userId) throws SQLException {
        String sql = """
            INSERT INTO Custom_Field_Data_Audit 
            (ID, Custom_Field_Metadata_ID, Custom_Field_Enum_ID, Facet_Object_ID, 
             Custom_Field_Value, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, RevType)
            SELECT cfd.ID, cfd.Custom_Field_Metadata_ID, cfd.Custom_Field_Enum_ID, cfd.Facet_Object_ID, 
                   cfd.Custom_Field_Value, cfd.CreateDatetime, cfd.LastUpdateDatetime, cfd.LastUpdate_UserID, 'UPDATE'
            FROM Custom_Field_Data cfd
            INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
            WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, moduleId);
            stmt.setInt(2, objectId);
            stmt.executeUpdate();
        }
    }

    /** Get audit table name for a module (e.g. client_audit_history) for facet history tab. */
    private String getAuditTableNameByModuleId(Connection conn, int moduleId) throws SQLException {
        String moduleName = getModulePrimaryName(conn, moduleId);
        if (moduleName == null) return null;
        return getAuditTableNameByModuleName(moduleName);
    }

    private String getModulePrimaryName(Connection conn, int moduleId) throws SQLException {
        String sql = "SELECT primaryname FROM module WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getAuditTableNameByModuleName(String moduleName) {
        if (moduleName == null) return null;
        switch (moduleName) {
            case "System": return "system_audit_history";
            case "Committee": return "committee_audit_history";
            case "Policy": return "policy_audit_history";
            case "Process": return "process_audit_history";
            case "Project": return "project_audit_history";
            case "Product": return "product_audit_history";
            case "BusinessArea": return "business_area_audit_history";
            case "Capability": return "capability_audit_history";
            case "Client": return "client_audit_history";
            case "Dataset": case "Data Sets": return "dataset_audit_history";
            case "Glossary": return "glossary_audit_history";
            case "LegalEntity": case "Legal Entity": return "legal_audit_history";
            case "Interface": case "SystemInterface": return "interface_audit_history";
            case "Attribute": case "Attributes": return "attribute_audit_history";
            case "Regulation": return "regulation_audit_history";
            case "Regulatory Theme": case "RegulatoryTheme": return "regulatory_theme_audit_history";
            case "Regulator": return "regulator_audit_history";
            case "Geography": return "geography_audit_history";
            case "ChangeRequest": return "changerequest_audit_history";
            default: return null;
        }
    }

    /** Load current custom field values for object: metadataId -> { displayName, valueString }. */
    private Map<Integer, Map<String, String>> loadCurrentCustomFieldValues(Connection conn, int moduleId, int objectId) throws SQLException {
        Map<Integer, Map<String, String>> out = new HashMap<>();
        String sql = """
            SELECT cfm.ID AS metadataId, cfm.DisplayName,
                   cfd.Custom_Field_Value AS textValue, cfd.Custom_Field_Enum_ID AS enumId,
                   cfe.EnumValue AS enumValue
            FROM Custom_Field_Data cfd
            INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
            LEFT JOIN Custom_Field_Enum cfe ON cfd.Custom_Field_Enum_ID = cfe.ID
            WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            ps.setInt(2, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int metaId = rs.getInt("metadataId");
                    String displayName = rs.getString("DisplayName");
                    String textValue = rs.getString("textValue");
                    Integer enumId = rs.getObject("enumId") != null ? rs.getInt("enumId") : null;
                    String enumValue = rs.getString("enumValue");
                    String valueStr = (enumId != null && enumValue != null) ? enumValue : (textValue != null ? textValue : "");
                    Map<String, String> row = new HashMap<>();
                    row.put("displayName", displayName != null ? displayName : "Field " + metaId);
                    row.put("value", valueStr);
                    out.put(metaId, row);
                }
            }
        }
        return out;
    }

    /** Build new value map from request: metadataId -> { displayName, valueString }. */
    private Map<Integer, Map<String, String>> buildNewCustomFieldValueMap(Connection conn, List<Map<String, Object>> values, int moduleId) throws SQLException {
        Map<Integer, Map<String, String>> out = new HashMap<>();
        if (values == null) return out;
        for (Map<String, Object> valueData : values) {
            Integer metadataId = toInteger(valueData.get("metadataId"));
            if (metadataId == null || !verifyMetadataBelongsToModule(conn, metadataId, moduleId)) continue;
            String displayName = getCustomFieldDisplayName(conn, metadataId);
            Integer enumId = toInteger(valueData.get("enumId"));
            Object rawValue = valueData.get("value");
            String valueStr;
            if (enumId != null) {
                String enumLabel = getEnumLabel(conn, enumId);
                valueStr = enumLabel != null ? enumLabel : String.valueOf(rawValue);
            } else {
                valueStr = rawValue != null ? String.valueOf(rawValue) : "";
            }
            Map<String, String> row = new HashMap<>();
            row.put("displayName", displayName != null ? displayName : "Field " + metadataId);
            row.put("value", valueStr);
            out.put(metadataId, row);
        }
        return out;
    }

    private String getCustomFieldDisplayName(Connection conn, int metadataId) throws SQLException {
        String sql = "SELECT DisplayName FROM Custom_Field_Metadata WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, metadataId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("DisplayName");
            }
        }
        return null;
    }

    /**
     * Normalize a custom field value for audit comparison. Treats null and blank/whitespace
     * the same so that an absent (never-persisted, empty) field is not mistaken for a change.
     */
    private String normalizeCustomFieldAuditValue(String value) {
        if (value == null) return "";
        return value.trim();
    }

    private String getEnumLabel(Connection conn, int enumId) throws SQLException {
        String sql = "SELECT EnumValue FROM Custom_Field_Enum WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enumId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("EnumValue");
            }
        }
        return null;
    }

    private String getUserFullNameForAudit(Connection conn, int userId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return "System";
    }

    /** Insert one row into the facet's audit history for a custom field change. */
    private void insertFacetAuditRecordForCustomField(Connection conn, String auditTable, String objectDisplayName,
            int objectId, String updateType, String fieldName, String fromValue, String toValue, String author) {
        if (auditTable == null) return;
        try {
            String sql = "INSERT INTO " + auditTable + " (id, object, event, updateType, field, `from`, `to`, author) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, objectId);
                ps.setString(2, objectDisplayName);
                ps.setString(3, "Details");
                ps.setString(4, updateType);
                ps.setString(5, fieldName);
                ps.setString(6, fromValue);
                ps.setString(7, toValue);
                ps.setString(8, author);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // Log but do not fail the main save
            System.err.println("CustomFieldServlet: Failed to insert facet audit record: " + e.getMessage());
        }
    }

    /**
     * Normalize facetId to module name for permission checks
     * @param facetId The facet ID (e.g., "glossary", "system", "dataset")
     * @return Module name (e.g., "Glossary", "System", "Data Sets") or null if not found
     */
    private String normalizeFacetIdToModuleName(String facetId) {
        if (facetId == null) {
            return null;
        }
        
        String normalized = facetId.toLowerCase().trim();
        
        // Map facet IDs to module names
        Map<String, String> facetModuleMap = new HashMap<>();
        facetModuleMap.put("glossary", "Glossary");
        facetModuleMap.put("glossaries", "Glossary");
        facetModuleMap.put("system", "System");
        facetModuleMap.put("systems", "System");
        facetModuleMap.put("dataset", "Data Sets");
        facetModuleMap.put("data-set", "Data Sets");
        facetModuleMap.put("data sets", "Data Sets");
        facetModuleMap.put("datasets", "Data Sets");
        facetModuleMap.put("process", "Process");
        facetModuleMap.put("processes", "Process");
        facetModuleMap.put("policy", "Policy");
        facetModuleMap.put("policies", "Policy");
        facetModuleMap.put("product", "Product");
        facetModuleMap.put("products", "Product");
        facetModuleMap.put("project", "Project");
        facetModuleMap.put("projects", "Project");
        facetModuleMap.put("client", "Client");
        facetModuleMap.put("clients", "Client");
        facetModuleMap.put("legal-entity", "Legal Entity");
        facetModuleMap.put("legalentity", "Legal Entity");
        facetModuleMap.put("legal entity", "Legal Entity");
        facetModuleMap.put("business-area", "Business Area");
        facetModuleMap.put("businessarea", "Business Area");
        facetModuleMap.put("business area", "Business Area");
        facetModuleMap.put("business areas", "Business Area");
        facetModuleMap.put("capability", "Capability");
        facetModuleMap.put("capabilities", "Capability");
        facetModuleMap.put("committee", "Committee");
        facetModuleMap.put("committees", "Committee");
        facetModuleMap.put("regulation", "Regulation");
        facetModuleMap.put("regulations", "Regulation");
        facetModuleMap.put("interface", "Interface");
        facetModuleMap.put("interfaces", "Interface");
        facetModuleMap.put("system-interface", "Interface");
        facetModuleMap.put("attribute", "Attribute");
        facetModuleMap.put("attributes", "Attribute");
        facetModuleMap.put("people", "People");
        facetModuleMap.put("person", "People");
        facetModuleMap.put("role", "Role");
        facetModuleMap.put("roles", "Role");
        facetModuleMap.put("org unit", "Org Unit");
        facetModuleMap.put("org-unit", "Org Unit");
        facetModuleMap.put("orgunit", "Org Unit");
        facetModuleMap.put("geography", "Geography");
        facetModuleMap.put("geographies", "Geography");
        facetModuleMap.put("regulator", "Regulator");
        facetModuleMap.put("regulators", "Regulator");
        facetModuleMap.put("regulatorytheme", "Regulatory Theme");
        facetModuleMap.put("regulatory theme", "Regulatory Theme");
        facetModuleMap.put("regulatory-theme", "Regulatory Theme");
        facetModuleMap.put("change request", "Change Requests");
        facetModuleMap.put("change requests", "Change Requests");
        facetModuleMap.put("changerequest", "Change Requests");
        facetModuleMap.put("changerequests", "Change Requests");
        
        return facetModuleMap.get(normalized);
    }
    
    private void sendError(HttpServletResponse resp, String message, int statusCode) throws IOException {
        resp.setStatus(statusCode);
        Map<String, Object> error = Map.of("success", false, "error", message);
        objectMapper.writeValue(resp.getWriter(), error);
    }
}

