package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.unisonsearch.model.UnisonSearchRequest;
import com.example.unisonsearch.model.UnisonSearchResponse;
import com.example.unisonsearch.model.FacetResult;
import com.example.unisonsearch.service.UnisonSearchService;
import com.example.unisonsearch.service.SearchService;
import com.example.unisonsearch.service.GraphTraversalService;
import com.example.unisonsearch.service.CompoundQueryService;
import com.example.unisonsearch.repository.DatabaseHelper;
import com.example.unisonsearch.repository.QueryBuilder;
import com.example.unisonsearch.service.ConfigurationService;
import com.example.unisonsearch.service.RelationshipManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * New Widget servlet - provides data for creating new widgets
 * Endpoints:
 * - GET /api/dashboard/new-widget/saved-searches - Get all saved searches for current user
 * - GET /api/dashboard/new-widget/facets - Get all facets from module table
 * - GET /api/dashboard/new-widget/facet-columns?facet={facetName} - Get columns for a facet
 * - POST /api/dashboard/new-widget/preview - Preview widget data
 */
@WebServlet(name = "NewWidgetServlet", urlPatterns = {
    "/api/dashboard/new-widget/saved-searches",
    "/api/dashboard/new-widget/facets",
    "/api/dashboard/new-widget/facet-columns",
    "/api/dashboard/new-widget/preview"
})
public class NewWidgetServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String currentTableNameForMapping = null; // Store actual table name when using alias
    private UnisonSearchService unisonSearchService = null; // Lazy initialization

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        String pathInfo = request.getRequestURI();
        
        try {
            if (pathInfo.endsWith("/saved-searches")) {
                List<Map<String, Object>> searches = getSavedSearches(userId);
                objectMapper.writeValue(response.getWriter(), searches);
            } else if (pathInfo.endsWith("/facets")) {
                // Get saved search ID from query parameter if provided
                String searchId = request.getParameter("searchId");
                List<Map<String, Object>> facets = getFacets(searchId, request);
                objectMapper.writeValue(response.getWriter(), facets);
            } else if (pathInfo.endsWith("/facet-columns")) {
                String facetName = request.getParameter("facet");
                if (facetName == null || facetName.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Facet parameter is required");
                    objectMapper.writeValue(response.getWriter(), error);
                    return;
                }
                try {
                    List<Map<String, Object>> columns = getFacetColumns(facetName);
                    objectMapper.writeValue(response.getWriter(), columns);
                } catch (SQLException e) {
                    System.err.println("Error getting facet columns for " + facetName + ": " + e.getMessage());
                    e.printStackTrace();
                    // Return empty list instead of error to allow widget creation to continue
                    objectMapper.writeValue(response.getWriter(), new ArrayList<>());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        String pathInfo = request.getRequestURI();
        
        if (pathInfo.endsWith("/preview")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> requestData = objectMapper.readValue(request.getReader(), Map.class);
                Map<String, Object> previewData = getPreviewData(requestData, userId);
                objectMapper.writeValue(response.getWriter(), previewData);
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Error generating preview: " + e.getMessage());
                objectMapper.writeValue(response.getWriter(), error);
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private List<Map<String, Object>> getSavedSearches(int userId) throws SQLException {
        List<Map<String, Object>> searches = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT DISTINCT
                    us.id,
                    us.name,
                    us.description,
                    CASE 
                        WHEN us.user_reference = ? THEN NULL
                        WHEN us.is_public = 1 THEN NULL
                        ELSE CONCAT(p.First_Name, ' ', p.Last_Name, ' (', p.Email, ')')
                    END AS shared_by,
                    CASE 
                        WHEN us.user_reference = ? THEN 0
                        WHEN us.is_public = 1 THEN 1
                        ELSE 2
                    END AS search_type
                FROM user_search us
                LEFT JOIN user_x_search uxs ON uxs.search_id = us.id AND uxs.user_reference = ?
                LEFT JOIN people p ON p.ID = us.user_reference
                WHERE (
                    us.user_reference = ? 
                    OR us.is_public = 1 
                    OR uxs.search_id IS NOT NULL
                )
                AND us.condition_definition IS NOT NULL
                AND us.condition_definition != ''
                AND us.condition_definition != 'null'
                ORDER BY us.name
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId); // For shared_by CASE
                ps.setInt(2, userId); // For search_type CASE
                ps.setInt(3, userId); // For user_x_search JOIN
                ps.setInt(4, userId); // For WHERE clause
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> search = new HashMap<>();
                        search.put("id", rs.getInt("id"));
                        search.put("name", rs.getString("name"));
                        search.put("description", rs.getString("description"));
                        search.put("sharedBy", rs.getString("shared_by"));
                        search.put("searchType", rs.getInt("search_type"));
                        searches.add(search);
                    }
                }
            }
        }
        
        return searches;
    }

    private List<Map<String, Object>> getFacets(String searchId, HttpServletRequest request) throws SQLException {
        List<Map<String, Object>> facets = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // If searchId is provided, get only facets with count > 0 from that search
            if (searchId != null && !searchId.isEmpty()) {
                try {
                    // Get the saved search query
                    String searchSql = "SELECT condition_definition FROM saved_search WHERE id = ?";
                    try (PreparedStatement searchStmt = conn.prepareStatement(searchSql)) {
                        searchStmt.setString(1, searchId);
                        try (ResultSet searchRs = searchStmt.executeQuery()) {
                            if (searchRs.next()) {
                                String queryJson = searchRs.getString("condition_definition");
                                if (queryJson != null && !queryJson.isEmpty()) {
                                    // Execute Unison Search to get facet counts
                                    try {
                                        // Get userId from request attribute (set by filter)
                                        Integer userIdObj = (Integer) request.getAttribute("userId");
                                        int userId = userIdObj != null ? userIdObj : 0;
                                        
                                        // Use executeUnisonSearchForWidget which properly converts condition_definition to Unison Search format
                                        // Pass null as targetFacetName since we want results for all facets
                                        UnisonSearchResponse unisonResponse = executeUnisonSearchForWidget(queryJson, null, userId);
                                        
                                        if (unisonResponse != null && unisonResponse.getResults() != null) {
                                            Map<String, FacetResult> allResults = unisonResponse.getResults();
                                            
                                            // Get all facets from module table
                                            String allFacetsSql = "SELECT id, primaryname, tablename FROM module WHERE primaryname IS NOT NULL AND primaryname != '' ORDER BY primaryname";
                                            try (PreparedStatement allFacetsStmt = conn.prepareStatement(allFacetsSql);
                                                 ResultSet allFacetsRs = allFacetsStmt.executeQuery()) {
                                                
                                                while (allFacetsRs.next()) {
                                                    String facetName = allFacetsRs.getString("primaryname");
                                                    String facetId = mapFacetNameToId(facetName);
                                                    
                                                    // Check if this facet has count > 0
                                                    FacetResult facetResult = allResults.get(facetId);
                                                    
                                                    // Try normalized facet name if not found
                                                    if (facetResult == null) {
                                                        String normalizedFacet = normalizeFacetNameForUnison(facetName);
                                                        facetResult = allResults.get(normalizedFacet);
                                                    }
                                                    
                                                    // Try exact facet name if still not found
                                                    if (facetResult == null) {
                                                        facetResult = allResults.get(facetName);
                                                    }
                                                    
                                                    if (facetResult != null && facetResult.getCount() > 0) {
                                                        Map<String, Object> facet = new HashMap<>();
                                                        facet.put("id", facetName);
                                                        facet.put("name", facetName);
                                                        facet.put("tableName", allFacetsRs.getString("tablename"));
                                                        facet.put("moduleId", allFacetsRs.getInt("id"));
                                                        facets.add(facet);
                                                    }
                                                }
                                            }
                                            
                                            // If no facets found with count > 0, return empty list
                                            System.out.println("[NewWidgetServlet] Returning " + facets.size() + " facets with count > 0 for search " + searchId);
                                            return facets;
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Error executing Unison Search for facets: " + e.getMessage());
                                        e.printStackTrace();
                                        // Fall through to return all facets if search fails
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error getting facets for search " + searchId + ": " + e.getMessage());
                    e.printStackTrace();
                    // Fall through to return all facets if search fails
                }
            }
            
            // Default: return all facets (when no searchId or search fails)
            String sql = "SELECT id, primaryname, tablename FROM module WHERE primaryname IS NOT NULL AND primaryname != '' ORDER BY primaryname";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    Map<String, Object> facet = new HashMap<>();
                    facet.put("id", rs.getString("primaryname"));
                    facet.put("name", rs.getString("primaryname"));
                    facet.put("tableName", rs.getString("tablename"));
                    facet.put("moduleId", rs.getInt("id"));
                    facets.add(facet);
                }
            }
        }
        
        return facets;
    }

    private List<Map<String, Object>> getFacetColumns(String facetName) throws SQLException {
        List<Map<String, Object>> columns = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get module info
            String moduleSql = "SELECT id, tablename FROM module WHERE primaryname = ?";
            String tableName = null;
            Integer moduleId = null;
            
            try (PreparedStatement ps = conn.prepareStatement(moduleSql)) {
                ps.setString(1, facetName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        moduleId = rs.getInt("id");
                        tableName = rs.getString("tablename");
                    }
                }
            }
            
            if (tableName == null) {
                System.err.println("No table found for facet: " + facetName);
                return columns;
            }
            
            // Get columns from module_search_config if available
            // These are the columns that take direct values from the creation page
            boolean configTableExists = false;
            try {
                // Check if table exists first
                try (ResultSet rs = conn.getMetaData().getTables(null, null, "module_search_config", null)) {
                    configTableExists = rs.next();
                }
                
                if (configTableExists) {
                    String configSql = "SELECT fields_return FROM module_search_config WHERE module_name = ? OR index_name = ?";
                    try (PreparedStatement ps = conn.prepareStatement(configSql)) {
                        ps.setString(1, facetName);
                        ps.setString(2, facetName);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String fieldsReturn = rs.getString("fields_return");
                                if (fieldsReturn != null && !fieldsReturn.isEmpty()) {
                                    String[] fields = fieldsReturn.split(",");
                                    for (String field : fields) {
                                        String trimmed = field.trim();
                                        if (!trimmed.isEmpty()) {
                                            String fieldLower = trimmed.toLowerCase();
                                            
                                            // Skip pure ID fields - they should be replaced with value fields
                                            // But keep the field name in the column so it can be used for JOINs
                                            if (fieldLower.endsWith("_id") || (fieldLower.endsWith("id") && !fieldLower.equals("id"))) {
                                                // This is an ID field, convert it to a value field name
                                                String valueFieldName = convertIdFieldToValueField(trimmed, tableName);
                                                if (!valueFieldName.equals(trimmed)) {
                                                    // Use the converted field name
                                                    Map<String, Object> column = new HashMap<>();
                                                    column.put("name", trimmed); // Keep original for JOIN lookup
                                                    column.put("displayName", valueFieldName);
                                                    column.put("isIdField", true); // Mark as ID field for later JOIN
                                                    columns.add(column);
                                                } else {
                                                    // Couldn't convert, skip this ID field
                                                    continue;
                                                }
                                            } else {
                                                // Not an ID field, add as-is
                                                Map<String, Object> column = new HashMap<>();
                                                column.put("name", trimmed);
                                                column.put("displayName", formatColumnName(trimmed));
                                                columns.add(column);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                // module_search_config table might not exist, continue to fallback
                System.err.println("Error querying module_search_config: " + e.getMessage());
            }
            
            // If no columns from module_search_config, use default columns for common facets
            // These are the standard fields that appear in create pages
            if (columns.isEmpty()) {
                columns.addAll(getDefaultColumnsForFacet(facetName));
            }
            
            // Add custom fields for this facet if moduleId is available
            if (moduleId != null) {
                try {
                    String customFieldsSql = """
                        SELECT CustomFieldName, DisplayName
                        FROM Custom_Field_Metadata
                        WHERE Module_ID = ?
                        ORDER BY DisplayName
                    """;
                    try (PreparedStatement ps = conn.prepareStatement(customFieldsSql)) {
                        ps.setInt(1, moduleId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String customFieldName = rs.getString("CustomFieldName");
                                String displayName = rs.getString("DisplayName");
                                if (customFieldName != null && !customFieldName.isEmpty()) {
                                    Map<String, Object> column = new HashMap<>();
                                    column.put("name", customFieldName);
                                    column.put("displayName", displayName != null ? displayName : formatColumnName(customFieldName));
                                    column.put("isCustomField", true); // Mark as custom field
                                    columns.add(column);
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error querying Custom_Field_Metadata: " + e.getMessage());
                    // Continue without custom fields if query fails
                }
            }
            
            // Return columns (either from module_search_config or default columns for create pages)
        } catch (Exception e) {
            System.err.println("Error in getFacetColumns for facet " + facetName + ": " + e.getMessage());
            e.printStackTrace();
            // Return empty list instead of throwing to allow widget creation to continue
            // The frontend will handle empty columns gracefully
        }
        
        return columns;
    }
    
    /**
     * Get default columns for a facet based on common create page fields
     * These are the standard fields that appear in create pages for each facet
     */
    private List<Map<String, Object>> getDefaultColumnsForFacet(String facetName) {
        List<Map<String, Object>> defaultColumns = new ArrayList<>();
        String facetLower = facetName.toLowerCase();
        
        // Facet-specific default columns (based on create pages)
        // Note: "name" is the database column name, "displayName" is the business name shown in UI
        // ALL fields from create pages must be included, not just some
        if (facetLower.contains("dataset") || facetLower.equals("data sets")) {
            // Dataset create page fields: Name, System Short Name, Ref, Definition, Glossary Name, Usage, BUDG Status, Type, BUDG Viewing, Lifecycle
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("MasterSource", "System Short Name")); // Will be converted to System Name via JOIN
            defaultColumns.add(createColumn("RefNumber", "Ref."));
            defaultColumns.add(createColumn("definition", "Definition"));
            defaultColumns.add(createColumn("glossary", "Glossary Name")); // Will be converted from Glossary_ID via JOIN
            defaultColumns.add(createColumn("Usage", "Usage"));
            defaultColumns.add(createColumn("status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("DatasetType", "Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("AccessControlType", "BUDG Viewing")); // Will be converted via JOIN
            defaultColumns.add(createColumn("lifecycle", "Lifecycle")); // Will be converted via JOIN
        } else if (facetLower.contains("attribute")) {
            // Attribute create page fields: Name, Ref, Data Type, Lifecycle, Glossary, Dataset, Origin, Editability, Editability Role
            // Note: Attribute table does not have a "Type" column - it has "Data_type_ID" instead
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("RefNumber", "Ref."));
            defaultColumns.add(createColumn("Data_type_ID", "Data Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Requirement_ID", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Glossary_ID", "Glossary")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Dataset_ID", "Dataset")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Origination", "Origin")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Editability", "Editability")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Editability_role", "Editability Role")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Definition", "Definition"));
        } else if (facetLower.contains("glossary")) {
            // Glossary create page fields: Name, Parent Name, Definition, Ref, Format Type, Format Description, LDM Reference, Business Logic, Examples, BUDG Status, Lifecycle, BUDG Viewing, Type, Security Classification, CIA Rating (C, I, A), KDE
            // Note: Alias Names is in a separate table (glossary_alias_names), not a direct column
            defaultColumns.add(createColumn("Name", "Name"));
            defaultColumns.add(createColumn("Parent_ID", "Parent Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Definition"));
            defaultColumns.add(createColumn("Ref_Number", "Ref."));
            defaultColumns.add(createColumn("Format_type", "Format Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Format", "Format Description"));
            defaultColumns.add(createColumn("LDM", "LDM Reference"));
            defaultColumns.add(createColumn("Business_Logic", "Business Logic"));
            defaultColumns.add(createColumn("Examples", "Examples"));
            defaultColumns.add(createColumn("Status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Lifecycle", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Is_Public", "BUDG Viewing")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Type", "Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Security_Classification", "Security Classification")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Confidentiality_Rating", "CIA Rating C")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Integrity_Rating", "CIA Rating I")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Availability_Rating", "CIA Rating A")); // Will be converted via JOIN
            defaultColumns.add(createColumn("KDE", "KDE")); // Will be converted via JOIN
        } else if (facetLower.contains("system")) {
            // System create page fields: Short Name, Parent Short Name, Description, Type, Long Name, URL, BUDG Status, Lifecycle, BUDG Viewing, Classification, CIA Rating (C, I, A), Asset ID
            defaultColumns.add(createColumn("Name", "Short Name"));
            defaultColumns.add(createColumn("parent_id", "Parent Short Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Type", "Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Long_Name", "Long Name"));
            defaultColumns.add(createColumn("URL", "URL"));
            defaultColumns.add(createColumn("status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Lifecycle", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("is_Public", "BUDG Viewing")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Classification", "Classification")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Confidentiality_Rating", "CIA Rating C")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Integrity_Rating", "CIA Rating I")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Availability_Rating", "CIA Rating A")); // Will be converted via JOIN
            defaultColumns.add(createColumn("AssetID", "Asset ID"));
        } else if (facetLower.contains("people")) {
            // People create page fields: First Name, Last Name, Email, Function, Org Unit, Profile, Description
            defaultColumns.add(createColumn("First_Name", "First Name"));
            defaultColumns.add(createColumn("Last_Name", "Last Name"));
            defaultColumns.add(createColumn("Email", "Email"));
            defaultColumns.add(createColumn("Function_Name", "Function")); // Correct column name from schema
            defaultColumns.add(createColumn("Org_Unit_ID", "Org Unit")); // Will be converted via JOIN
            defaultColumns.add(createColumn("System_Role", "Profile")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Description"));
        } else if (facetLower.contains("process")) {
            // Process create page fields: Name, Ref, Type, Lifecycle, Step Type, Description, Parent, Input Description, Output Description, Classification, Automation, Permissions, Duration Type, Duration Value
            defaultColumns.add(createColumn("primaryname", "Name"));
            defaultColumns.add(createColumn("refnumber", "Ref."));
            defaultColumns.add(createColumn("type", "Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("lifecycle_status", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("step_type", "Step Type"));
            defaultColumns.add(createColumn("description", "Description"));
            defaultColumns.add(createColumn("parentid", "Parent Name")); // Will be converted via JOIN if exists
            defaultColumns.add(createColumn("input_description", "Input Description"));
            defaultColumns.add(createColumn("output_description", "Output Description"));
            defaultColumns.add(createColumn("processclass_id", "Classification")); // Will be converted via JOIN
            defaultColumns.add(createColumn("processautomation_id", "Automation")); // Will be converted via JOIN
            // Note: Permissions, Duration Type, Duration Value are complex fields that may not be in the main table
        } else if (facetLower.contains("project")) {
            // Project create page fields: Name, Ref, Status, Description
            defaultColumns.add(createColumn("primaryname", "Name"));
            defaultColumns.add(createColumn("refnumber", "Ref."));
            defaultColumns.add(createColumn("status", "Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("description", "Description"));
        } else if (facetLower.contains("policy")) {
            // Policy create page fields: Name, Parent Name, Ref, Type, Description, Internal, URL, Effective Date, End Date, BUDG Status, Lifecycle, BUDG Viewing
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("ParentID", "Parent Name")); // Correct: ParentID not Parent_ID in schema
            defaultColumns.add(createColumn("refNumber", "Ref.")); // Correct: refNumber not RefNumber in schema
            defaultColumns.add(createColumn("Policy_Type", "Type")); // Correct: Policy_Type not Type in schema
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Internal", "Internal"));
            defaultColumns.add(createColumn("URL", "URL"));
            defaultColumns.add(createColumn("EffectiveDate", "Effective Date"));
            defaultColumns.add(createColumn("EndDate", "End Date"));
            defaultColumns.add(createColumn("Status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Lifecycle_Status", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("isPublic", "BUDG Viewing")); // Correct: isPublic not Is_Public in schema
        } else if (facetLower.contains("product")) {
            // Product create page fields: Name, Ref, Long Name, Lifecycle, Parent, BUDG Status, BUDG Viewing
            // Note: Product table does not have a Type column in schema.sql
            defaultColumns.add(createColumn("primaryname", "Name"));
            defaultColumns.add(createColumn("refnumber", "Ref."));
            defaultColumns.add(createColumn("longname", "Long Name"));
            defaultColumns.add(createColumn("lifecycle_status", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("parent_id", "Parent Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("is_public", "BUDG Viewing")); // Will be converted via JOIN - lowercase in schema
        } else if (facetLower.contains("business") && facetLower.contains("area")) {
            // Business Area create page fields: Name, Ref, Description, Parent, BUDG Status, Lifecycle, BUDG Viewing
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("RefNumber", "Ref."));
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Parent_ID", "Parent Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Lifecycle", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Is_Public", "BUDG Viewing")); // Will be converted via JOIN
        } else if (facetLower.contains("client")) {
            // Client create page fields: Primary Name, Long Name, Parent Name, Description, BUDG Status
            defaultColumns.add(createColumn("PrimaryName", "Primary Name"));
            defaultColumns.add(createColumn("LongName", "Long Name"));
            defaultColumns.add(createColumn("Parent_ID", "Parent Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("IsPublic", "BUDG Viewing")); // Will be converted via JOIN
        } else if (facetLower.contains("legal") && facetLower.contains("entity")) {
            // Legal Entity create page fields: Short Name, Long Name, Parent Short Name, Description, BUDG Status, BUDG Viewing
            // Note: Table name is "legal" not "legal_entity", and it does not have a Lifecycle column
            defaultColumns.add(createColumn("ShortName", "Short Name"));
            defaultColumns.add(createColumn("LongName", "Long Name"));
            defaultColumns.add(createColumn("Parent_ID", "Parent Short Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Is_Public", "BUDG Viewing")); // Will be converted via JOIN
        } else if (facetLower.contains("interface")) {
            // Interface create page fields: Name, Ref, Source System Short Name, Target System Short Name, Description, Synchronisation Control, BUDG Status, BUDG Viewing, Lifecycle, Automation, Frequency, Asset ID, Transfer Method, Transfer Format, Interface Classification
            defaultColumns.add(createColumn("Name", "Name"));
            defaultColumns.add(createColumn("Ref_number", "Ref."));
            defaultColumns.add(createColumn("Source_systemID", "Source System Short Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Target_systemID", "Target System Short Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Synchronisation_Control", "Synchronisation Control"));
            defaultColumns.add(createColumn("status_id", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("is_public", "BUDG Viewing")); // Correct: is_public not Is_Public in schema
            defaultColumns.add(createColumn("Lifecycle_id", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Automation_ID", "Automation")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Frequency_ID", "Frequency")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Asset_ID", "Asset ID"));
            defaultColumns.add(createColumn("Transfer_Method_ID", "Transfer Method")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Transfer_Format_ID", "Transfer Format")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Classification_id", "Interface Classification")); // Will be converted via JOIN
        } else if (facetLower.contains("org") && facetLower.contains("unit")) {
            // Org Unit create page fields: Name, Ref, Description
            defaultColumns.add(createColumn("Name", "Name"));
            defaultColumns.add(createColumn("Reference", "Ref."));
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("status_id", "Status")); // Will be converted via JOIN
        } else if (facetLower.contains("capability")) {
            // Capability create page fields: Name, Ref, Description, Parent, BUDG Status, BUDG Viewing, Lifecycle, Classification, Capability Type
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("RefNumber", "Ref."));
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Parent_ID", "Parent Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Status", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Is_Public", "BUDG Viewing")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Lifecycle", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Classification", "Classification")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Capability_Type", "Capability Type")); // Will be converted via JOIN
        } else if (facetLower.contains("regulation")) {
            // Regulation create page fields: Long Name, Parent, Ref, Description, Short Name, Legal Advice, Legal Advice Type, Additional Info, Compliance Level, Maturity, Probability, BUDG Status, Stage, Access Control, Publication Date, Impact Rating
            // Note: Effective Date and End Date don't exist in schema - using ComplianceDate, FinalisationDate, CommentsDate instead
            defaultColumns.add(createColumn("primaryName", "Long Name"));
            defaultColumns.add(createColumn("Parent_ID", "Parent")); // Will be converted via JOIN
            defaultColumns.add(createColumn("RefNumber", "Ref."));
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("ShortName", "Short Name"));
            defaultColumns.add(createColumn("LegalAdvice", "Legal Advice")); // Correct: LegalAdvice not Legal_Advice in schema
            defaultColumns.add(createColumn("LegalAdviceType_ID", "Legal Advice Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("AdditionalInfo", "Additional Info")); // Correct: AdditionalInfo not Additional_Info in schema
            defaultColumns.add(createColumn("ComplianceLevel_ID", "Compliance Level")); // Will be converted via JOIN
            defaultColumns.add(createColumn("RegulationMaturity_ID", "Maturity")); // Will be converted via JOIN
            defaultColumns.add(createColumn("RegulationProbability_ID", "Probability")); // Will be converted via JOIN
            defaultColumns.add(createColumn("RegulationStatus_ID", "BUDG Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("RegulationStage_ID", "Stage")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Is_Public", "Access Control")); // Will be converted via JOIN
            defaultColumns.add(createColumn("PublicationDate", "Publication Date")); // Correct: PublicationDate not Publication_Date in schema
            defaultColumns.add(createColumn("RegulationImpactRating_ID", "Impact Rating")); // Will be converted via JOIN
        } else if (facetLower.contains("regulatory") && facetLower.contains("theme")) {
            // Regulatory Theme create page fields: Primary Name, Parent Name, Description, Ref, Short Name, BUDG Status
            defaultColumns.add(createColumn("PrimaryName", "Primary Name"));
            defaultColumns.add(createColumn("Parent_ID", "Parent Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("RefNumber", "Ref."));
            defaultColumns.add(createColumn("ShortName", "Short Name"));
            defaultColumns.add(createColumn("Status_ID", "BUDG Status")); // Will be converted via JOIN
        } else if (facetLower.contains("committee")) {
            // Committee create page fields: Name, Ref, Type, Status, Lifecycle, Classification, Parent
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("RefNumber", "Ref."));
            defaultColumns.add(createColumn("Committee_Type", "Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Status", "Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Lifecycle", "Lifecycle")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Classification", "Classification")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Parent_ID", "Parent Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Is_Public", "BUDG Viewing")); // Will be converted via JOIN
        } else if (facetLower.contains("geography")) {
            // Geography create page fields: Name, Parent, Description
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("ParentID", "Parent Name")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Description", "Description"));
        } else if (facetLower.contains("regulator")) {
            // Regulator create page fields: Name, Short Name, Description
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("ShortName", "Short Name"));
            defaultColumns.add(createColumn("Description", "Description"));
        } else if (facetLower.contains("role")) {
            // Role create page fields: Full Name, Role, Object Type, Object, Role Accepted
            defaultColumns.add(createColumn("FullName", "Full Name"));
            defaultColumns.add(createColumn("Role", "Role"));
            defaultColumns.add(createColumn("ObjectType", "Object Type"));
            defaultColumns.add(createColumn("Object", "Object"));
            defaultColumns.add(createColumn("RoleAccepted", "Role Accepted"));
            defaultColumns.add(createColumn("Description", "Description"));
        } else if (facetLower.contains("change request") || facetLower.contains("changerequest")) {
            // Change Request create page fields: Title, Type, Summary, Parent, Severity, Urgency, Estimated Benefit, Estimated Cost
            defaultColumns.add(createColumn("PrimaryName", "Title"));
            defaultColumns.add(createColumn("CR_TypeID", "Type")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Summary", "Summary"));
            defaultColumns.add(createColumn("Parent_ID", "Parent Name")); // Will be converted via JOIN (self-referencing)
            defaultColumns.add(createColumn("CR_SeverityID", "Severity")); // Will be converted via JOIN
            defaultColumns.add(createColumn("CR_UrgencyID", "Urgency")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Estimated_BenefitID", "Estimated Benefit")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Estimated_CostID", "Estimated Cost")); // Will be converted via JOIN
            defaultColumns.add(createColumn("CR_StatusID", "Status")); // Will be converted via JOIN
            defaultColumns.add(createColumn("Reference", "Ref."));
        } else {
            // Generic fallback for unknown facets
            defaultColumns.add(createColumn("PrimaryName", "Name"));
            defaultColumns.add(createColumn("RefNumber", "Ref."));
            defaultColumns.add(createColumn("Description", "Description"));
            defaultColumns.add(createColumn("Lifecycle", "Lifecycle"));
            defaultColumns.add(createColumn("Status", "Status"));
        }
        
        return defaultColumns;
    }
    
    private Map<String, Object> createColumn(String name, String displayName) {
        Map<String, Object> column = new HashMap<>();
        column.put("name", name);
        column.put("displayName", displayName);
        return column;
    }

    private String formatColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return "";
        }
        
        // If it already has spaces or dots, return as-is (likely already formatted business name)
        if (columnName.contains(" ") || columnName.contains(".")) {
            return columnName;
        }
        
        // Business name mappings to match create page labels (user-friendly names)
        String colLower = columnName.toLowerCase();
        Map<String, String> businessNameMap = new HashMap<>();
        businessNameMap.put("accesscontroltype", "Viewing");
        businessNameMap.put("format_type", "Format Type");
        businessNameMap.put("lifecycle_status", "Lifecycle");
        businessNameMap.put("lifecycle", "Lifecycle");
        businessNameMap.put("is_public", "Is Public");
        businessNameMap.put("ispublic", "Is Public");
        businessNameMap.put("ref_number", "Ref.");
        businessNameMap.put("refnumber", "Ref.");
        businessNameMap.put("ref", "Ref.");
        businessNameMap.put("business_logic", "Business Logic");
        businessNameMap.put("parent_id", "Parent");
        businessNameMap.put("parentid", "Parent");
        businessNameMap.put("master_source", "System");
        businessNameMap.put("mastersource", "System");
        businessNameMap.put("dataset_type", "Type");
        businessNameMap.put("datasettype", "Type");
        businessNameMap.put("type", "Type");
        businessNameMap.put("security_classification", "Security Classification");
        businessNameMap.put("confidentiality_rating", "Confidentiality Rating");
        businessNameMap.put("integrity_rating", "Integrity Rating");
        businessNameMap.put("availability_rating", "Availability Rating");
        businessNameMap.put("name", "Name");
        businessNameMap.put("primaryname", "Name");
        businessNameMap.put("primary_name", "Name");
        businessNameMap.put("definition", "Definition");
        businessNameMap.put("description", "Description");
        businessNameMap.put("shortname", "Short Name");
        businessNameMap.put("short_name", "Short Name");
        businessNameMap.put("longname", "Long Name");
        businessNameMap.put("long_name", "Long Name");
        businessNameMap.put("status", "BUDG Status");
        businessNameMap.put("budgstatus", "BUDG Status");
        businessNameMap.put("budg_status", "BUDG Status");
        businessNameMap.put("classification", "Classification");
        businessNameMap.put("automation", "Automation");
        businessNameMap.put("frequency", "Frequency");
        businessNameMap.put("orgunit", "Org Unit");
        businessNameMap.put("org_unit", "Org Unit");
        businessNameMap.put("profile", "Profile");
        businessNameMap.put("level", "Level");
        businessNameMap.put("stage", "Stage");
        businessNameMap.put("maturity", "Maturity");
        businessNameMap.put("probability", "Probability");
        businessNameMap.put("compliancelevel", "Compliance Level");
        businessNameMap.put("compliance_level", "Compliance Level");
        businessNameMap.put("roletype", "Role Type");
        businessNameMap.put("role_type", "Role Type");
        businessNameMap.put("objecttype", "Object Type");
        businessNameMap.put("object_type", "Object Type");
        businessNameMap.put("roleaccepted", "Role Accepted");
        businessNameMap.put("role_accepted", "Role Accepted");
        businessNameMap.put("first_name", "First Name");
        businessNameMap.put("firstname", "First Name");
        businessNameMap.put("last_name", "Last Name");
        businessNameMap.put("lastname", "Last Name");
        businessNameMap.put("email", "Email");
        businessNameMap.put("function", "Function");
        businessNameMap.put("fullname", "Full Name");
        businessNameMap.put("full_name", "Full Name");
        businessNameMap.put("parentname", "Parent Name");
        businessNameMap.put("parent_name", "Parent Name");
        businessNameMap.put("parentshortname", "Parent Short Name");
        businessNameMap.put("parent_short_name", "Parent Short Name");
        businessNameMap.put("sourcesystemshortname", "Source System Short Name");
        businessNameMap.put("source_system_short_name", "Source System Short Name");
        businessNameMap.put("targetsystemshortname", "Target System Short Name");
        businessNameMap.put("target_system_short_name", "Target System Short Name");
        businessNameMap.put("aliasnames", "Alias Names");
        businessNameMap.put("alias_names", "Alias Names");
        businessNameMap.put("kde", "KDE");
        businessNameMap.put("ldm", "LDM");
        
        // Check if we have a business name mapping
        if (businessNameMap.containsKey(colLower)) {
            return businessNameMap.get(colLower);
        }
        
        // Convert camelCase or snake_case to Title Case with spaces as fallback
        // First handle snake_case
        String spaced = columnName.replaceAll("_", " ");
        // Then handle camelCase
        spaced = spaced.replaceAll("([a-z])([A-Z])", "$1 $2");
        // Capitalize first letter of each word
        String[] words = spaced.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            if (!words[i].isEmpty()) {
                result.append(words[i].substring(0, 1).toUpperCase());
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Check if a column exists in a table
     */
    private boolean checkColumnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            return rs.next();
        } catch (SQLException e) {
            // If metadata query fails, assume column doesn't exist
            return false;
        }
    }

    private Map<String, Object> getPreviewData(Map<String, Object> requestData, int userId) throws SQLException, IOException {
        Map<String, Object> result = new HashMap<>();
        
        String widgetSource = (String) requestData.get("widgetSource");
        String visualizeAs = (String) requestData.get("visualizeAs");
        
        if ("text".equals(widgetSource)) {
            // Text widget - just return success
            result.put("type", "text");
            result.put("success", true);
            return result;
        }
        
        // Saved search widget
        Integer searchId = null;
        Object searchIdObj = requestData.get("source");
        if (searchIdObj instanceof Number) {
            searchId = ((Number) searchIdObj).intValue();
        } else if (searchIdObj instanceof String) {
            try {
                searchId = Integer.parseInt((String) searchIdObj);
            } catch (NumberFormatException e) {
                result.put("error", "Invalid search ID");
                return result;
            }
        }
        
        String facetName = (String) requestData.get("focus");
        
        if (searchId == null || facetName == null) {
            result.put("error", "Source and Focus are required");
            return result;
        }
        
        // Get saved search query from user_search table
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check which query column exists - prioritize condition_definition
            boolean hasConditionDefinition = checkColumnExists(conn, "user_search", "condition_definition");
            boolean hasQueryJson = checkColumnExists(conn, "user_search", "query_json");
            boolean hasQuery = checkColumnExists(conn, "user_search", "query");
            
            String queryColumn = null;
            if (hasConditionDefinition) {
                queryColumn = "condition_definition";
            } else if (hasQueryJson) {
                queryColumn = "query_json";
            } else if (hasQuery) {
                queryColumn = "query";
            }
            
            String queryJson = null;
            
            if (queryColumn != null) {
                // Updated query to include visibility rules (owned, public, or shared)
                String searchSql = """
                    SELECT %s FROM user_search us
                    LEFT JOIN user_x_search uxs ON uxs.search_id = us.id AND uxs.user_reference = ?
                    WHERE us.id = ? AND (
                        us.user_reference = ? 
                        OR us.is_public = 1 
                        OR uxs.search_id IS NOT NULL
                    )
                """.formatted(queryColumn);
                
                try (PreparedStatement ps = conn.prepareStatement(searchSql)) {
                    ps.setInt(1, userId); // For user_x_search JOIN
                    ps.setInt(2, searchId); // Search ID
                    ps.setInt(3, userId); // For user_reference check
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            queryJson = rs.getString(queryColumn);
                        }
                    }
                }
            } else {
                // If no query column exists, just use the search ID for preview
                // This means we'll do a basic count/table/chart without filters
                queryJson = null;
            }
            
            // Validate that condition_definition exists and is not empty
            if (queryJson == null || queryJson.trim().isEmpty() || queryJson.trim().equalsIgnoreCase("null")) {
                result.put("error", "Selected search does not have saved search criteria (condition_definition). Please select a search with saved filters.");
                //system.out.println("Search " + searchId + " has no condition_definition - returning error");
                return result;
            }
            
            // Check if search exists with visibility rules
            String checkSql = """
                SELECT us.id FROM user_search us
                LEFT JOIN user_x_search uxs ON uxs.search_id = us.id AND uxs.user_reference = ?
                WHERE us.id = ? AND (
                    us.user_reference = ? 
                    OR us.is_public = 1 
                    OR uxs.search_id IS NOT NULL
                )
            """;
            boolean searchExists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, userId); // For user_x_search JOIN
                ps.setInt(2, searchId); // Search ID
                ps.setInt(3, userId); // For user_reference check
                try (ResultSet rs = ps.executeQuery()) {
                    searchExists = rs.next();
                }
            }
            
            if (!searchExists) {
                result.put("error", "Saved search not found or access denied");
                return result;
            }
            
            // Get table name for the facet
            String tableName = getTableNameForFacet(conn, facetName);
            if (tableName == null) {
                System.err.println("Table not found for facet: " + facetName);
                result.put("error", "Facet table not found");
                return result;
            }
            
            // Special case for Attribute facet
            if (facetName.equals("Attribute")) {
                tableName = "attribute";
                //system.out.println("Special case: Using 'attribute' table for Attribute facet");
            }
            
            //system.out.println("Table name for facet '" + facetName + "': " + tableName);
            
            // Parse condition_definition to extract search parameters for the chosen facet
            Map<String, Object> searchParams = new HashMap<>();
            if (queryJson != null && !queryJson.isEmpty()) {
                searchParams = parseConditionDefinition(queryJson, facetName);
            }
            // If no condition_definition or no filters for this facet, searchParams will be empty
            
            //system.out.println("Search params for facet '" + facetName + "': " + searchParams);
            //system.out.println("Has filterGroups: " + searchParams.containsKey("filterGroups"));
            
            // Use Unison Search to get filtered IDs for the target facet and counts for all facets
            Set<Integer> filteredIds = null;
            Map<String, Integer> allFacetCounts = null;
            if (queryJson != null && !queryJson.isEmpty()) {
                try {
                    System.out.println("[NewWidgetServlet] Executing Unison Search for saved search widget, facet: " + facetName);
                    UnisonSearchResponse unisonResponse = executeUnisonSearchForWidget(queryJson, facetName, userId);
                    if (unisonResponse != null) {
                        if (unisonResponse.isSuccess()) {
                            // Get filtered IDs for target facet
                            Map<String, FacetResult> allResults = unisonResponse.getResults();
                            if (allResults != null && !allResults.isEmpty()) {
                                // Extract counts for all facets
                                allFacetCounts = new HashMap<>();
                                for (Map.Entry<String, FacetResult> entry : allResults.entrySet()) {
                                    String facetId = entry.getKey();
                                    FacetResult facetResult = entry.getValue();
                                    if (facetResult != null) {
                                        int count = facetResult.getCount();
                                        allFacetCounts.put(facetId, count);
                                        System.out.println("[NewWidgetServlet] Facet " + facetId + " count: " + count);
                                    }
                                }
                                
                                // Get IDs for target facet
                                String targetFacetId = mapFacetNameToId(facetName);
                                System.out.println("[NewWidgetServlet] Looking for target facet: " + facetName + " (mapped to: " + targetFacetId + ")");
                                System.out.println("[NewWidgetServlet] Available facet IDs in results: " + allResults.keySet());
                                
                                FacetResult targetResult = allResults.get(targetFacetId);
                                if (targetResult == null) {
                                    // Try normalized facet name
                                    String normalizedFacet = normalizeFacetNameForUnison(facetName);
                                    System.out.println("[NewWidgetServlet] Trying normalized facet: " + normalizedFacet);
                                    targetResult = allResults.get(normalizedFacet);
                                }
                                if (targetResult == null) {
                                    // Try exact facet name
                                    System.out.println("[NewWidgetServlet] Trying exact facet name: " + facetName);
                                    targetResult = allResults.get(facetName);
                                }
                                
                                if (targetResult != null && targetResult.getIds() != null) {
                                    filteredIds = targetResult.getIds();
                                    System.out.println("[NewWidgetServlet] Found " + filteredIds.size() + " IDs for target facet: " + facetName);
                                } else {
                                    System.out.println("[NewWidgetServlet] WARNING: No results found for target facet: " + facetName);
                                }
                            } else {
                                System.out.println("[NewWidgetServlet] WARNING: Unison Search returned empty results");
                            }
                        } else {
                            System.err.println("[NewWidgetServlet] Unison Search failed: " + unisonResponse.getError());
                        }
                    } else {
                        System.err.println("[NewWidgetServlet] Unison Search returned null response");
                    }
                } catch (Exception e) {
                    System.err.println("[NewWidgetServlet] Error executing Unison Search: " + e.getMessage());
                    e.printStackTrace();
                    // Fallback to old method if Unison Search fails
                    filteredIds = null;
                    allFacetCounts = null;
                }
            } else {
                System.out.println("[NewWidgetServlet] No queryJson available, skipping Unison Search");
            }
            
            // Execute search based on visualization type
            if ("count".equals(visualizeAs)) {
                // Get count of objects matching the saved search for this facet
                int count = 0;
                
                // First, try to get count from allFacetCounts (from Unison Search)
                if (allFacetCounts != null) {
                    String targetFacetId = mapFacetNameToId(facetName);
                    Integer countFromUnison = allFacetCounts.get(targetFacetId);
                    if (countFromUnison == null) {
                        // Try normalized facet name
                        String normalizedFacet = normalizeFacetNameForUnison(facetName);
                        countFromUnison = allFacetCounts.get(normalizedFacet);
                    }
                    if (countFromUnison == null) {
                        // Try exact facet name
                        countFromUnison = allFacetCounts.get(facetName);
                    }
                    if (countFromUnison != null) {
                        count = countFromUnison;
                        System.out.println("[NewWidgetServlet] Using count from Unison Search: " + count);
                    }
                }
                
                // If count is still 0 and we have filteredIds, use the size
                if (count == 0 && filteredIds != null) {
                    count = filteredIds.size();
                    System.out.println("[NewWidgetServlet] Using count from filteredIds: " + count);
                }
                
                // Fallback to SQL query if still 0
                if (count == 0) {
                    count = getSearchResultCount(conn, tableName, searchParams, facetName, filteredIds);
                    System.out.println("[NewWidgetServlet] Using count from SQL query: " + count);
                }
                
                result.put("type", "count");
                result.put("count", count);
                result.put("facet", facetName);
                // Include all facet counts if available
                if (allFacetCounts != null) {
                    result.put("allFacetCounts", allFacetCounts);
                }
            } else if ("table".equals(visualizeAs)) {
                // Get table data
                @SuppressWarnings("unchecked")
                List<String> displayColumns = (List<String>) requestData.get("display");
                if (displayColumns == null || displayColumns.isEmpty()) {
                    result.put("error", "Please select at least one display column");
                    return result;
                }
                List<Map<String, Object>> tableData = getSearchResultTable(conn, tableName, searchParams, facetName, displayColumns, filteredIds);
                result.put("type", "table");
                result.put("columns", displayColumns);
                result.put("data", tableData);
                result.put("count", tableData.size());
                result.put("facet", facetName);
                result.put("focus", facetName); // Also include as focus for consistency
                // Include all facet counts if available
                if (allFacetCounts != null) {
                    result.put("allFacetCounts", allFacetCounts);
                }
            } else {
                // Chart visualization (doughnut or bar)
                String visualizeBy = (String) requestData.get("visualizeBy");
                System.out.println("[NewWidgetServlet] Chart visualization requested - visualizeBy: " + visualizeBy + ", visualizeAs: " + visualizeAs);
                if (visualizeBy == null || visualizeBy.isEmpty()) {
                    result.put("error", "Please select a column to visualize by");
                    return result;
                }
                System.out.println("[NewWidgetServlet] About to call getSearchResultChart with filteredIds: " + (filteredIds != null ? filteredIds.size() + " IDs" : "null"));
                Map<String, Integer> chartData = getSearchResultChart(conn, tableName, searchParams, facetName, visualizeBy, filteredIds);
                System.out.println("[NewWidgetServlet] getSearchResultChart returned " + chartData.size() + " groups");
                result.put("type", visualizeAs);
                result.put("data", chartData);
                result.put("column", visualizeBy);
                result.put("facet", facetName);
                result.put("focus", facetName); // Also include as focus for consistency
                // Include all facet counts if available
                if (allFacetCounts != null) {
                    result.put("allFacetCounts", allFacetCounts);
                }
            }
        }
        
        return result;
    }
    
    private Map<String, Object> parseConditionDefinition(String conditionDefinition, String targetFacetName) {
        Map<String, Object> params = new HashMap<>();
        boolean facetFoundInSearch = false;
        
        try {
            if (conditionDefinition != null && !conditionDefinition.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> searchData = objectMapper.readValue(conditionDefinition, Map.class);
                
                // Extract searchGroups
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> searchGroups = (List<Map<String, Object>>) searchData.get("searchGroups");
                if (searchGroups != null) {
                    // Map facet name to facet ID for comparison
                    String targetFacetId = mapFacetNameToId(targetFacetName);
                    //system.out.println("Looking for facet '" + targetFacetName + "' mapped to ID '" + targetFacetId + "'");
                    
                    // Debug: show all facet IDs in the search
                    //system.out.println("Available facet IDs in search:");
                    for (Map<String, Object> searchGroup : searchGroups) {
                        java.util.Objects.requireNonNull(searchGroup);
                    }
                    
                    // First, check if the target facet exists in any search group (active or inactive)
                    for (Map<String, Object> searchGroup : searchGroups) {
                        String facetId = (String) searchGroup.get("facetId");
                        if (facetId != null && (facetId.equals(targetFacetId) || facetId.equalsIgnoreCase(targetFacetId))) {
                            facetFoundInSearch = true;
                            break;
                        }
                    }
                    
                    // If facet is not found in the search at all, mark it as such
                    if (!facetFoundInSearch) {
                        params.put("facetNotInSearch", true);
                        //system.out.println("Facet '" + targetFacetName + "' (" + targetFacetId + ") is NOT part of this saved search - will return no data");
                    } else {
                        // Facet exists in search, now look for active filters
                        for (Map<String, Object> searchGroup : searchGroups) {
                            Boolean active = (Boolean) searchGroup.get("active");
                            String facetId = (String) searchGroup.get("facetId");
                            
                            // Only process active search groups for the target facet
                            if (active != null && active && facetId != null && (facetId.equals(targetFacetId) || facetId.equalsIgnoreCase(targetFacetId))) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> filterGroups = (List<Map<String, Object>>) searchGroup.get("filterGroups");
                                if (filterGroups != null) {
                                    params.put("filterGroups", filterGroups);
                                    params.put("facetId", facetId);
                                    //system.out.println("Found active filters for facet '" + targetFacetName + "' in saved search");
                                    break; // Use the first matching active search group
                                }
                            }
                        }
                        
                        // If facet exists but has no active filters, show all records for that facet
                        if (!params.containsKey("filterGroups")) {
                            //system.out.println("Facet '" + targetFacetName + "' exists in saved search but has no active filters - will show all records");
                        }
                    }
                }
                
                // Print the full condition_definition for debugging
                //system.out.println("FULL condition_definition: " + conditionDefinition);
                //system.out.println("Parsed condition_definition for facet " + targetFacetName + " (" + mapFacetNameToId(targetFacetName) + "): " + params);
            } else {
                // No condition_definition means no search criteria - show all records
                //system.out.println("No condition_definition found - will show all records for facet: " + targetFacetName);
            }
        } catch (Exception e) {
            // If parsing fails, return empty map (will use default search)
            System.err.println("Error parsing condition_definition: " + e.getMessage());
            e.printStackTrace();
        }
        return params;
    }
    
    private String mapFacetNameToId(String facetName) {
        // Special case for Attribute facet
        if (facetName.equals("Attribute")) {
            //system.out.println("Special case: Mapping Attribute facet directly to ATTRIBUTE");
            return "ATTRIBUTE";
        }
        
        // Map common facet names to their IDs used in condition_definition
        Map<String, String> facetMapping = new HashMap<>();
        facetMapping.put("Dataset", "DATASET");
        facetMapping.put("Data Sets", "DATASET");  // Handle plural form
        facetMapping.put("Attribute", "ATTRIBUTE");
        facetMapping.put("System", "SYSTEM");
        facetMapping.put("Glossary", "GLOSSARY");
        facetMapping.put("People", "PEOPLE");
        facetMapping.put("Process", "PROCESS");
        facetMapping.put("Project", "PROJECT");
        facetMapping.put("Product", "PRODUCT");
        facetMapping.put("Policy", "POLICY");
        facetMapping.put("Legal Entity", "LEGAL_ENTITY");
        facetMapping.put("Business Area", "BUSINESS_AREA");
        facetMapping.put("Capability", "CAPABILITY");
        facetMapping.put("Committee", "COMMITTEE");
        facetMapping.put("Client", "CLIENT");
        facetMapping.put("Geography", "GEOGRAPHY");
        facetMapping.put("Regulation", "REGULATION");
        facetMapping.put("Regulator", "REGULATOR");
        facetMapping.put("Regulatory Theme", "REGULATORY_THEME");
        facetMapping.put("Change Request", "CHANGE_REQUEST");
        facetMapping.put("ChangeRequests", "CHANGE_REQUEST");
        
        return facetMapping.getOrDefault(facetName, facetName.toUpperCase());
    }
    
    private String buildWhereClause(Map<String, Object> searchParams, String tableName, List<Object> parameters) {
        StringBuilder whereClause = new StringBuilder();
        
        if (searchParams.containsKey("filterGroups")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filterGroups = (List<Map<String, Object>>) searchParams.get("filterGroups");
            
            if (filterGroups != null && !filterGroups.isEmpty()) {
                List<String> conditions = new ArrayList<>();
                
                //system.out.println("Building WHERE clause for table: " + tableName + " with " + filterGroups.size() + " filter groups");
                
                for (Map<String, Object> filterGroup : filterGroups) {
                    String field = (String) filterGroup.get("field");
                    String condition = (String) filterGroup.get("condition");
                    Object value = filterGroup.get("value");
                    
                    //system.out.println("Processing filter: field=" + field + ", condition=" + condition + ", value=" + value);
                    //system.out.println("Filter group details: " + filterGroup);
                    
                    if (field != null && condition != null && value != null) {
                        // Special case for attribute table fields which could be in different formats
                        if (tableName.equalsIgnoreCase("attribute")) {
                            // Handle common field name variations
                            if (field.equals("name") || field.equals("dbFieldName") || field.equals("DBFieldName")) {
                                field = "name"; // Standardize to "name" which maps to PrimaryName
                                //system.out.println("Standardized attribute name field to 'name'");
                            } else if (field.equals("description") || field.equals("Definition")) {
                                field = "description"; // Standardize to "description" which maps to Definition
                                //system.out.println("Standardized attribute description field to 'description'");
                            }
                        }
                        
                        // Get the actual table name for mapping (not the alias)
                        String actualTableName = (tableName.equals("t") && currentTableNameForMapping != null) ? 
                                                currentTableNameForMapping : tableName;
                        String dbColumn = mapFieldToDbColumn(field, actualTableName);
                        //system.out.println("Mapped field '" + field + "' to column '" + dbColumn + "' for table '" + actualTableName + "'");
                        
                        // If the mapped column is the same as the field and doesn't look like a valid column name,
                        // it might not exist - skip this filter to avoid SQL errors
                        if (dbColumn.equals(field) && !isLikelyColumnName(field)) {
                            System.err.println("Warning: Field '" + field + "' may not exist in table '" + actualTableName + "' - skipping filter");
                            continue;
                        }
                        
                        // Prefix column with table alias if we're using one
                        String qualifiedColumn = tableName.equals("t") ? "t." + dbColumn : dbColumn;
                        String sqlCondition = buildSqlCondition(qualifiedColumn, condition, value, parameters);
                        
                        if (sqlCondition != null) {
                            conditions.add(sqlCondition);
                            //system.out.println("Added SQL condition: " + sqlCondition);
                        } else {
                            System.err.println("Failed to build SQL condition for field: " + field + ", condition: " + condition);
                        }
                    } else {
                        System.err.println("Invalid filter group - missing field, condition, or value: " + filterGroup);
                    }
                }
                
                if (!conditions.isEmpty()) {
                    whereClause.append(" WHERE ");
                    whereClause.append(String.join(" AND ", conditions));
                    //system.out.println("Final WHERE clause: " + whereClause.toString());
                } else {
                    //system.out.println("No valid conditions generated - will return empty WHERE clause");
                }
            } else {
                //system.out.println("No filter groups found in searchParams for table: " + tableName + " - will show ALL records (no filters applied)");
            }
        } else {
            //system.out.println("No filterGroups key in searchParams for table: " + tableName + " - showing ALL records (no filters in saved search for this facet)");
        }
        
        String result = whereClause.toString();
        if (result.isEmpty()) {
            //system.out.println("Returning empty WHERE clause - query will return ALL records from table: " + tableName);
        }
        return result;
    }
    
    private boolean isLikelyColumnName(String field) {
        // Check if the field looks like a database column name
        // Column names typically contain underscores, are camelCase, or are single words
        if (field == null || field.isEmpty()) {
            return false;
        }
        // If it contains underscores or is camelCase, it's likely a column name
        return field.contains("_") || 
               (field.length() > 1 && Character.isUpperCase(field.charAt(1))) ||
               field.matches("^[a-zA-Z][a-zA-Z0-9_]*$");
    }
    
    private String mapFieldToDbColumn(String field, String tableName) {
        // Map common field names to database column names for different tables
        // Note: Column names are case-sensitive and vary by table
        Map<String, Map<String, String>> tableFieldMappings = new HashMap<>();
        
        // Dataset table mappings (PrimaryName, definition - lowercase)
        Map<String, String> datasetFields = new HashMap<>();
        datasetFields.put("name", "PrimaryName");
        datasetFields.put("Name", "PrimaryName");
        datasetFields.put("description", "definition");
        datasetFields.put("Description", "definition");
        datasetFields.put("definition", "definition");
        datasetFields.put("Definition", "definition");
        datasetFields.put("lifecycle", "lifecycle");
        datasetFields.put("Lifecycle", "lifecycle");
        datasetFields.put("status", "status");
        datasetFields.put("Status", "status");
        datasetFields.put("BUDG Status", "status");
        datasetFields.put("budgStatus", "status");
        datasetFields.put("systemId", "MasterSource");
        datasetFields.put("systemName", "MasterSource");
        datasetFields.put("System", "MasterSource");
        datasetFields.put("System Short Name", "MasterSource");
        datasetFields.put("Master Source", "MasterSource");
        datasetFields.put("MasterSource", "MasterSource");
        datasetFields.put("ref", "RefNumber");
        datasetFields.put("Ref.", "RefNumber");
        datasetFields.put("RefNumber", "RefNumber");
        datasetFields.put("type", "DatasetType");
        datasetFields.put("Type", "DatasetType");
        datasetFields.put("DatasetType", "DatasetType");
        datasetFields.put("glossary", "glossary");
        datasetFields.put("Glossary Name", "glossary");
        datasetFields.put("glossaryName", "glossary");
        datasetFields.put("usage", "Usage");
        datasetFields.put("Usage", "Usage");
        datasetFields.put("viewing", "AccessControlType");
        datasetFields.put("BUDG Viewing", "AccessControlType");
        datasetFields.put("AccessControlType", "AccessControlType");
        datasetFields.put("Value Info ID", "ValueInfoID");
        datasetFields.put("ValueInfoID", "ValueInfoID");
        datasetFields.put("ValueInfo", "ValueInfoID");
        tableFieldMappings.put("dataset", datasetFields);
        
        // Attribute table mappings (PrimaryName, Definition - capital D)
        Map<String, String> attributeFields = new HashMap<>();
        // Standard field mappings
        attributeFields.put("name", "PrimaryName");
        attributeFields.put("dbFieldName", "PrimaryName"); // No DBFieldName column, use PrimaryName instead
        attributeFields.put("description", "Definition");
        attributeFields.put("lifecycle", "Requirement_ID"); // Map lifecycle to Requirement_ID
        attributeFields.put("status", "Editability"); // Map status to Editability
        attributeFields.put("dataSetId", "Dataset_ID");
        attributeFields.put("dataSetName", "Dataset_ID"); // Will need join for actual name
        attributeFields.put("origin", "Origination"); // Map origin to Origination
        attributeFields.put("requirement", "Requirement_ID"); // Map requirement to Requirement_ID
        attributeFields.put("editability", "Editability");
        attributeFields.put("editabilityRole", "Editability_role");
        
        // Add mappings for the exact field names used in the search JSON
        attributeFields.put("DBFieldName", "PrimaryName"); // This is what's in the search JSON
        attributeFields.put("Definition", "Definition");
        attributeFields.put("Requirement_ID", "Requirement_ID");
        attributeFields.put("Editability", "Editability");
        attributeFields.put("Dataset_ID", "Dataset_ID");
        attributeFields.put("Origination", "Origination");
        attributeFields.put("Editability_role", "Editability_role");
        attributeFields.put("PrimaryName", "PrimaryName");
        
        tableFieldMappings.put("attribute", attributeFields);
        
        // System table mappings (Name, Description - capital D)
        Map<String, String> systemFields = new HashMap<>();
        systemFields.put("name", "Name");
        systemFields.put("Name", "Name");
        systemFields.put("Short Name", "Name");
        systemFields.put("shortName", "Name");
        systemFields.put("description", "Description");
        systemFields.put("Description", "Description");
        systemFields.put("type", "Type");
        systemFields.put("Type", "Type");
        systemFields.put("lifecycle", "Lifecycle");
        systemFields.put("Lifecycle", "Lifecycle");
        systemFields.put("classification", "Classification");
        systemFields.put("Classification", "Classification");
        systemFields.put("status", "status");
        systemFields.put("Status", "status");
        systemFields.put("BUDG Status", "status");
        systemFields.put("budgStatus", "status");
        systemFields.put("longName", "Long_Name");
        systemFields.put("Long Name", "Long_Name");
        systemFields.put("Long_Name", "Long_Name");
        systemFields.put("viewing", "is_Public");
        systemFields.put("BUDG Viewing", "is_Public");
        systemFields.put("is_Public", "is_Public");
        systemFields.put("isPublic", "is_Public");
        systemFields.put("parent", "parent_id");
        systemFields.put("Parent", "parent_id");
        systemFields.put("Parent Short Name", "parent_id");
        systemFields.put("parent_id", "parent_id");
        systemFields.put("url", "URL");
        systemFields.put("URL", "URL");
        systemFields.put("assetId", "AssetID");
        systemFields.put("Asset ID", "AssetID");
        systemFields.put("AssetID", "AssetID");
        systemFields.put("confidentialityRating", "Confidentiality_Rating");
        systemFields.put("CIA Rating C", "Confidentiality_Rating");
        systemFields.put("Confidentiality_Rating", "Confidentiality_Rating");
        systemFields.put("integrityRating", "Integrity_Rating");
        systemFields.put("CIA Rating I", "Integrity_Rating");
        systemFields.put("Integrity_Rating", "Integrity_Rating");
        systemFields.put("availabilityRating", "Availability_Rating");
        systemFields.put("CIA Rating A", "Availability_Rating");
        systemFields.put("Availability_Rating", "Availability_Rating");
        tableFieldMappings.put("system", systemFields);
        
        // Glossary table mappings (Name, Description - capital D)
        Map<String, String> glossaryFields = new HashMap<>();
        glossaryFields.put("name", "Name");
        glossaryFields.put("Name", "Name");
        glossaryFields.put("description", "Description");
        glossaryFields.put("Description", "Description");
        glossaryFields.put("definition", "Description");
        glossaryFields.put("Definition", "Description");
        glossaryFields.put("type", "Type");
        glossaryFields.put("Type", "Type");
        glossaryFields.put("lifecycle", "Lifecycle");
        glossaryFields.put("Lifecycle", "Lifecycle");
        glossaryFields.put("parentName", "Parent_ID");
        glossaryFields.put("Parent Name", "Parent_ID");
        glossaryFields.put("Parent_ID", "Parent_ID");
        glossaryFields.put("ref", "Ref_Number");
        glossaryFields.put("Ref.", "Ref_Number");
        glossaryFields.put("Ref_Number", "Ref_Number");
        glossaryFields.put("formatType", "Format_type");
        glossaryFields.put("Format Type", "Format_type");
        glossaryFields.put("Format_type", "Format_type");
        glossaryFields.put("aliasNames", "alias_names");
        glossaryFields.put("Alias Names", "alias_names");
        glossaryFields.put("alias_names", "alias_names");
        glossaryFields.put("formatDescription", "Format");
        glossaryFields.put("Format Description", "Format");
        glossaryFields.put("Format", "Format");
        glossaryFields.put("ldmReference", "LDM");
        glossaryFields.put("LDM Reference", "LDM");
        glossaryFields.put("LDM", "LDM");
        glossaryFields.put("businessLogic", "Business_Logic");
        glossaryFields.put("Business Logic", "Business_Logic");
        glossaryFields.put("Business_Logic", "Business_Logic");
        glossaryFields.put("examples", "Examples");
        glossaryFields.put("Examples", "Examples");
        glossaryFields.put("status", "Status");
        glossaryFields.put("Status", "Status");
        glossaryFields.put("BUDG Status", "Status");
        glossaryFields.put("budgStatus", "Status");
        glossaryFields.put("viewing", "Is_Public");
        glossaryFields.put("BUDG Viewing", "Is_Public");
        glossaryFields.put("AccessControlType", "Is_Public");
        glossaryFields.put("Is_Public", "Is_Public");
        glossaryFields.put("securityClassification", "Security_Classification");
        glossaryFields.put("Security Classification", "Security_Classification");
        glossaryFields.put("Security_Classification", "Security_Classification");
        glossaryFields.put("confidentialityRating", "Confidentiality_Rating");
        glossaryFields.put("CIA Rating C", "Confidentiality_Rating");
        glossaryFields.put("Confidentiality_Rating", "Confidentiality_Rating");
        glossaryFields.put("integrityRating", "Integrity_Rating");
        glossaryFields.put("CIA Rating I", "Integrity_Rating");
        glossaryFields.put("Integrity_Rating", "Integrity_Rating");
        glossaryFields.put("availabilityRating", "Availability_Rating");
        glossaryFields.put("CIA Rating A", "Availability_Rating");
        glossaryFields.put("Availability_Rating", "Availability_Rating");
        glossaryFields.put("kde", "KDE");
        glossaryFields.put("KDE", "KDE");
        tableFieldMappings.put("glossary", glossaryFields);
        
        // People table mappings (First_Name, Last_Name, Email)
        Map<String, String> peopleFields = new HashMap<>();
        peopleFields.put("firstName", "First_Name");
        peopleFields.put("First Name", "First_Name");
        peopleFields.put("First_Name", "First_Name");
        peopleFields.put("lastName", "Last_Name");
        peopleFields.put("Last Name", "Last_Name");
        peopleFields.put("Last_Name", "Last_Name");
        peopleFields.put("email", "Email");
        peopleFields.put("Email", "Email");
        peopleFields.put("function", "Function");
        peopleFields.put("Function", "Function");
        peopleFields.put("Function_Name", "Function");
        peopleFields.put("orgUnit", "Org_Unit_ID");
        peopleFields.put("Org Unit", "Org_Unit_ID");
        peopleFields.put("OrgUnit_ID", "Org_Unit_ID");
        peopleFields.put("Org_Unit_ID", "Org_Unit_ID");
        peopleFields.put("profile", "System_Role");
        peopleFields.put("Profile", "System_Role");
        peopleFields.put("Profile_ID", "System_Role");
        peopleFields.put("System_Role", "System_Role");
        peopleFields.put("description", "Description");
        peopleFields.put("Description", "Description");
        tableFieldMappings.put("people", peopleFields);
        
        // Process table mappings (primaryname - lowercase, description - lowercase)
        Map<String, String> processFields = new HashMap<>();
        processFields.put("name", "primaryname");
        processFields.put("Name", "primaryname");
        processFields.put("primaryname", "primaryname");
        processFields.put("description", "description");
        processFields.put("Description", "description");
        processFields.put("lifecycle", "lifecycle_status");
        processFields.put("Lifecycle", "lifecycle_status");
        processFields.put("lifecycle_status", "lifecycle_status");
        processFields.put("status", "status");
        processFields.put("Status", "status");
        processFields.put("type", "type");
        processFields.put("Type", "type");
        processFields.put("ref", "refnumber");
        processFields.put("Ref.", "refnumber");
        processFields.put("refnumber", "refnumber");
        processFields.put("stepType", "step_type");
        processFields.put("Step Type", "step_type");
        processFields.put("step_type", "step_type");
        processFields.put("parent", "parentid");
        processFields.put("Parent", "parentid");
        processFields.put("Parent Name", "parentid");
        processFields.put("parentid", "parentid");
        processFields.put("inputDescription", "input_description");
        processFields.put("Input Description", "input_description");
        processFields.put("input_description", "input_description");
        processFields.put("outputDescription", "output_description");
        processFields.put("Output Description", "output_description");
        processFields.put("output_description", "output_description");
        processFields.put("classification", "processclass_id");
        processFields.put("Classification", "processclass_id");
        processFields.put("processclass_id", "processclass_id");
        processFields.put("automation", "processautomation_id");
        processFields.put("Automation", "processautomation_id");
        processFields.put("processautomation_id", "processautomation_id");
        tableFieldMappings.put("process", processFields);
        
        // Project table mappings (primaryname - lowercase, description - lowercase)
        Map<String, String> projectFields = new HashMap<>();
        projectFields.put("name", "primaryname");
        projectFields.put("Name", "primaryname");
        projectFields.put("primaryname", "primaryname");
        projectFields.put("description", "description");
        projectFields.put("Description", "description");
        projectFields.put("lifecycle", "lifecycle_status");
        projectFields.put("Lifecycle", "lifecycle_status");
        projectFields.put("lifecycle_status", "lifecycle_status");
        projectFields.put("status", "status");
        projectFields.put("Status", "status");
        projectFields.put("ref", "refnumber");
        projectFields.put("Ref.", "refnumber");
        projectFields.put("refnumber", "refnumber");
        projectFields.put("type", "project_type");
        projectFields.put("Type", "project_type");
        projectFields.put("project_type", "project_type");
        projectFields.put("classification", "classification");
        projectFields.put("Classification", "classification");
        tableFieldMappings.put("project", projectFields);
        
        // Product table mappings (primaryname - lowercase, description - lowercase)
        Map<String, String> productFields = new HashMap<>();
        productFields.put("name", "primaryname");
        productFields.put("Name", "primaryname");
        productFields.put("primaryname", "primaryname");
        productFields.put("description", "description");
        productFields.put("Description", "description");
        productFields.put("lifecycle", "lifecycle_status");
        productFields.put("Lifecycle", "lifecycle_status");
        productFields.put("lifecycle_status", "lifecycle_status");
        productFields.put("status", "status");
        productFields.put("Status", "status");
        productFields.put("BUDG Status", "status");
        productFields.put("budgStatus", "status");
        productFields.put("ref", "refnumber");
        productFields.put("Ref.", "refnumber");
        productFields.put("refnumber", "refnumber");
        productFields.put("longName", "longname");
        productFields.put("Long Name", "longname");
        productFields.put("Long_Name", "longname");
        productFields.put("longname", "longname");
        productFields.put("type", "Type");
        productFields.put("Type", "Type");
        productFields.put("parent", "parent_id");
        productFields.put("Parent", "parent_id");
        productFields.put("Parent Name", "parent_id");
        productFields.put("parent_id", "parent_id");
        productFields.put("viewing", "is_Public");
        productFields.put("BUDG Viewing", "is_Public");
        productFields.put("is_Public", "is_Public");
        productFields.put("isPublic", "is_Public");
        tableFieldMappings.put("product", productFields);
        
        // Policy table mappings (PrimaryName - capital, Description - capital D)
        Map<String, String> policyFields = new HashMap<>();
        policyFields.put("name", "PrimaryName");
        policyFields.put("Name", "PrimaryName");
        policyFields.put("PrimaryName", "PrimaryName");
        policyFields.put("description", "Description");
        policyFields.put("Description", "Description");
        policyFields.put("lifecycle", "Lifecycle_Status");
        policyFields.put("Lifecycle", "Lifecycle_Status");
        policyFields.put("Lifecycle_Status", "Lifecycle_Status");
        policyFields.put("status", "Status");
        policyFields.put("Status", "Status");
        policyFields.put("BUDG Status", "Status");
        policyFields.put("budgStatus", "Status");
        policyFields.put("type", "Policy_Type");
        policyFields.put("Type", "Policy_Type");
        policyFields.put("Policy_Type", "Policy_Type");
        policyFields.put("parent", "Parent_ID");
        policyFields.put("Parent", "Parent_ID");
        policyFields.put("Parent Name", "Parent_ID");
        policyFields.put("Parent_ID", "Parent_ID");
        policyFields.put("ref", "RefNumber");
        policyFields.put("Ref.", "RefNumber");
        policyFields.put("RefNumber", "RefNumber");
        policyFields.put("internal", "Internal");
        policyFields.put("Internal", "Internal");
        policyFields.put("url", "URL");
        policyFields.put("URL", "URL");
        policyFields.put("effectiveDate", "EffectiveDate");
        policyFields.put("Effective Date", "EffectiveDate");
        policyFields.put("EffectiveDate", "EffectiveDate");
        policyFields.put("endDate", "EndDate");
        policyFields.put("End Date", "EndDate");
        policyFields.put("EndDate", "EndDate");
        policyFields.put("viewing", "Is_Public");
        policyFields.put("BUDG Viewing", "Is_Public");
        policyFields.put("Is_Public", "Is_Public");
        tableFieldMappings.put("policy", policyFields);
        
        // Legal Entity table mappings (check schema for actual column names)
        Map<String, String> legalEntityFields = new HashMap<>();
        legalEntityFields.put("name", "PrimaryName");
        legalEntityFields.put("description", "Description");
        legalEntityFields.put("lifecycle", "Lifecycle");
        legalEntityFields.put("status", "Status");
        tableFieldMappings.put("legal_entity", legalEntityFields);
        tableFieldMappings.put("legalentity", legalEntityFields);
        
        // Business Area table mappings
        Map<String, String> businessAreaFields = new HashMap<>();
        businessAreaFields.put("name", "PrimaryName");
        businessAreaFields.put("Name", "PrimaryName");
        businessAreaFields.put("PrimaryName", "PrimaryName");
        businessAreaFields.put("description", "Description");
        businessAreaFields.put("Description", "Description");
        businessAreaFields.put("lifecycle", "Lifecycle");
        businessAreaFields.put("Lifecycle", "Lifecycle");
        businessAreaFields.put("status", "Status");
        businessAreaFields.put("Status", "Status");
        businessAreaFields.put("BUDG Status", "Status");
        businessAreaFields.put("budgStatus", "Status");
        businessAreaFields.put("ref", "RefNumber");
        businessAreaFields.put("Ref.", "RefNumber");
        businessAreaFields.put("RefNumber", "RefNumber");
        businessAreaFields.put("parent", "Parent_ID");
        businessAreaFields.put("Parent", "Parent_ID");
        businessAreaFields.put("Parent Name", "Parent_ID");
        businessAreaFields.put("Parent_ID", "Parent_ID");
        businessAreaFields.put("viewing", "Is_Public");
        businessAreaFields.put("BUDG Viewing", "Is_Public");
        businessAreaFields.put("Is_Public", "Is_Public");
        tableFieldMappings.put("business_area", businessAreaFields);
        tableFieldMappings.put("businessarea", businessAreaFields);
        
        // Capability table mappings
        Map<String, String> capabilityFields = new HashMap<>();
        capabilityFields.put("name", "PrimaryName");
        capabilityFields.put("Name", "PrimaryName");
        capabilityFields.put("PrimaryName", "PrimaryName");
        capabilityFields.put("description", "Description");
        capabilityFields.put("Description", "Description");
        capabilityFields.put("lifecycle", "Lifecycle");
        capabilityFields.put("Lifecycle", "Lifecycle");
        capabilityFields.put("status", "Status");
        capabilityFields.put("Status", "Status");
        capabilityFields.put("BUDG Status", "Status");
        capabilityFields.put("budgStatus", "Status");
        capabilityFields.put("ref", "RefNumber");
        capabilityFields.put("Ref.", "RefNumber");
        capabilityFields.put("RefNumber", "RefNumber");
        capabilityFields.put("type", "Capability_Type");
        capabilityFields.put("Type", "Capability_Type");
        capabilityFields.put("Capability_Type", "Capability_Type");
        capabilityFields.put("parent", "Parent_ID");
        capabilityFields.put("Parent", "Parent_ID");
        capabilityFields.put("Parent Name", "Parent_ID");
        capabilityFields.put("Parent_ID", "Parent_ID");
        capabilityFields.put("viewing", "Is_Public");
        capabilityFields.put("BUDG Viewing", "Is_Public");
        capabilityFields.put("Is_Public", "Is_Public");
        capabilityFields.put("classification", "Classification");
        capabilityFields.put("Classification", "Classification");
        tableFieldMappings.put("capability", capabilityFields);
        
        // Committee table mappings
        Map<String, String> committeeFields = new HashMap<>();
        committeeFields.put("name", "PrimaryName");
        committeeFields.put("Name", "PrimaryName");
        committeeFields.put("PrimaryName", "PrimaryName");
        committeeFields.put("description", "Description");
        committeeFields.put("Description", "Description");
        committeeFields.put("lifecycle", "Lifecycle");
        committeeFields.put("Lifecycle", "Lifecycle");
        committeeFields.put("status", "Status");
        committeeFields.put("Status", "Status");
        committeeFields.put("ref", "RefNumber");
        committeeFields.put("Ref.", "RefNumber");
        committeeFields.put("RefNumber", "RefNumber");
        committeeFields.put("type", "Committee_Type");
        committeeFields.put("Type", "Committee_Type");
        committeeFields.put("Committee_Type", "Committee_Type");
        committeeFields.put("classification", "Classification");
        committeeFields.put("Classification", "Classification");
        committeeFields.put("parent", "Parent_ID");
        committeeFields.put("Parent", "Parent_ID");
        committeeFields.put("Parent Name", "Parent_ID");
        committeeFields.put("Parent_ID", "Parent_ID");
        committeeFields.put("viewing", "Is_Public");
        committeeFields.put("BUDG Viewing", "Is_Public");
        committeeFields.put("Is_Public", "Is_Public");
        tableFieldMappings.put("committee", committeeFields);
        
        // Client table mappings
        Map<String, String> clientFields = new HashMap<>();
        clientFields.put("name", "PrimaryName");
        clientFields.put("Primary Name", "PrimaryName");
        clientFields.put("PrimaryName", "PrimaryName");
        clientFields.put("longName", "LongName");
        clientFields.put("Long Name", "LongName");
        clientFields.put("LongName", "LongName");
        clientFields.put("description", "Description");
        clientFields.put("Description", "Description");
        clientFields.put("lifecycle", "Lifecycle");
        clientFields.put("Lifecycle", "Lifecycle");
        clientFields.put("status", "Status");
        clientFields.put("Status", "Status");
        clientFields.put("BUDG Status", "Status");
        clientFields.put("budgStatus", "Status");
        clientFields.put("parent", "Parent_ID");
        clientFields.put("Parent", "Parent_ID");
        clientFields.put("Parent Name", "Parent_ID");
        clientFields.put("Parent_ID", "Parent_ID");
        clientFields.put("viewing", "IsPublic");
        clientFields.put("BUDG Viewing", "IsPublic");
        clientFields.put("IsPublic", "IsPublic");
        tableFieldMappings.put("client", clientFields);
        
        // Geography table mappings
        Map<String, String> geographyFields = new HashMap<>();
        geographyFields.put("name", "PrimaryName");
        geographyFields.put("Name", "PrimaryName");
        geographyFields.put("PrimaryName", "PrimaryName");
        geographyFields.put("description", "Description");
        geographyFields.put("Description", "Description");
        geographyFields.put("parent", "ParentID");
        geographyFields.put("Parent", "ParentID");
        geographyFields.put("Parent Name", "ParentID");
        geographyFields.put("ParentID", "ParentID");
        tableFieldMappings.put("geography", geographyFields);
        
        // Change Request table mappings
        Map<String, String> changeRequestFields = new HashMap<>();
        changeRequestFields.put("title", "PrimaryName");
        changeRequestFields.put("Title", "PrimaryName");
        changeRequestFields.put("PrimaryName", "PrimaryName");
        changeRequestFields.put("summary", "Summary");
        changeRequestFields.put("Summary", "Summary");
        changeRequestFields.put("type", "CR_TypeID");
        changeRequestFields.put("Type", "CR_TypeID");
        changeRequestFields.put("CR_TypeID", "CR_TypeID");
        changeRequestFields.put("parent", "Parent_ID");
        changeRequestFields.put("Parent", "Parent_ID");
        changeRequestFields.put("Parent Name", "Parent_ID");
        changeRequestFields.put("Parent_ID", "Parent_ID");
        changeRequestFields.put("severity", "CR_SeverityID");
        changeRequestFields.put("Severity", "CR_SeverityID");
        changeRequestFields.put("CR_SeverityID", "CR_SeverityID");
        changeRequestFields.put("urgency", "CR_UrgencyID");
        changeRequestFields.put("Urgency", "CR_UrgencyID");
        changeRequestFields.put("CR_UrgencyID", "CR_UrgencyID");
        changeRequestFields.put("estimatedBenefit", "Estimated_BenefitID");
        changeRequestFields.put("Estimated Benefit", "Estimated_BenefitID");
        changeRequestFields.put("Estimated_BenefitID", "Estimated_BenefitID");
        changeRequestFields.put("estimatedCost", "Estimated_CostID");
        changeRequestFields.put("Estimated Cost", "Estimated_CostID");
        changeRequestFields.put("Estimated_CostID", "Estimated_CostID");
        changeRequestFields.put("status", "CR_StatusID");
        changeRequestFields.put("Status", "CR_StatusID");
        changeRequestFields.put("CR_StatusID", "CR_StatusID");
        changeRequestFields.put("ref", "Reference");
        changeRequestFields.put("Ref.", "Reference");
        changeRequestFields.put("Reference", "Reference");
        tableFieldMappings.put("changerequest", changeRequestFields);
        tableFieldMappings.put("change_request", changeRequestFields);
        tableFieldMappings.put("changerrequest", changeRequestFields);
        
        // Interface table mappings
        Map<String, String> interfaceFields = new HashMap<>();
        interfaceFields.put("name", "Name");
        interfaceFields.put("Name", "Name");
        interfaceFields.put("description", "Description");
        interfaceFields.put("Description", "Description");
        interfaceFields.put("ref", "Ref_number");
        interfaceFields.put("Ref.", "Ref_number");
        interfaceFields.put("RefNumber", "Ref_number");
        interfaceFields.put("Ref_number", "Ref_number");
        interfaceFields.put("lifecycle", "Lifecycle_id");
        interfaceFields.put("Lifecycle", "Lifecycle_id");
        interfaceFields.put("Lifecycle_id", "Lifecycle_id");
        interfaceFields.put("status", "status_id");
        interfaceFields.put("Status", "status_id");
        interfaceFields.put("BUDG Status", "status_id");
        interfaceFields.put("budgStatus", "status_id");
        interfaceFields.put("status_id", "status_id");
        interfaceFields.put("automation", "Automation_ID");
        interfaceFields.put("Automation", "Automation_ID");
        interfaceFields.put("Automation_ID", "Automation_ID");
        interfaceFields.put("frequency", "Frequency_ID");
        interfaceFields.put("Frequency", "Frequency_ID");
        interfaceFields.put("Frequency_ID", "Frequency_ID");
        interfaceFields.put("sourceSystem", "Source_systemID");
        interfaceFields.put("Source System Short Name", "Source_systemID");
        interfaceFields.put("Source_systemID", "Source_systemID");
        interfaceFields.put("targetSystem", "Target_systemID");
        interfaceFields.put("Target System Short Name", "Target_systemID");
        interfaceFields.put("Target_systemID", "Target_systemID");
        interfaceFields.put("synchronisationControl", "Synchronisation_Control");
        interfaceFields.put("Synchronisation Control", "Synchronisation_Control");
        interfaceFields.put("Synchronisation_Control", "Synchronisation_Control");
        interfaceFields.put("viewing", "Is_Public");
        interfaceFields.put("BUDG Viewing", "Is_Public");
        interfaceFields.put("Is_Public", "Is_Public");
        interfaceFields.put("assetId", "Asset_ID");
        interfaceFields.put("Asset ID", "Asset_ID");
        interfaceFields.put("Asset_ID", "Asset_ID");
        interfaceFields.put("transferMethod", "Transfer_Method_ID");
        interfaceFields.put("Transfer Method", "Transfer_Method_ID");
        interfaceFields.put("Transfer_Method_ID", "Transfer_Method_ID");
        interfaceFields.put("transferFormat", "Transfer_Format_ID");
        interfaceFields.put("Transfer Format", "Transfer_Format_ID");
        interfaceFields.put("Transfer_Format_ID", "Transfer_Format_ID");
        interfaceFields.put("interfaceClassification", "Classification_id");
        interfaceFields.put("Interface Classification", "Classification_id");
        interfaceFields.put("Classification_id", "Classification_id");
        tableFieldMappings.put("interface", interfaceFields);
        tableFieldMappings.put("system_interface", interfaceFields);
        
        // Org Unit table mappings
        Map<String, String> orgUnitFields = new HashMap<>();
        orgUnitFields.put("name", "Name");
        orgUnitFields.put("Name", "Name");
        orgUnitFields.put("description", "Description");
        orgUnitFields.put("Description", "Description");
        orgUnitFields.put("ref", "Reference");
        orgUnitFields.put("Ref.", "Reference");
        orgUnitFields.put("RefNumber", "Reference");
        orgUnitFields.put("Reference", "Reference");
        orgUnitFields.put("status", "status_id");
        orgUnitFields.put("Status", "status_id");
        orgUnitFields.put("status_id", "status_id");
        orgUnitFields.put("parent", "Parent_ID");
        orgUnitFields.put("Parent", "Parent_ID");
        orgUnitFields.put("Parent Org Unit", "Parent_ID");
        orgUnitFields.put("Parent_ID", "Parent_ID");
        tableFieldMappings.put("org_unit", orgUnitFields);
        tableFieldMappings.put("orgunit", orgUnitFields);
        
        // Regulation table mappings
        Map<String, String> regulationFields = new HashMap<>();
        regulationFields.put("name", "primaryName");
        regulationFields.put("Name", "primaryName");
        regulationFields.put("Long Name", "primaryName");
        regulationFields.put("primaryName", "primaryName");
        regulationFields.put("description", "Description");
        regulationFields.put("Description", "Description");
        regulationFields.put("shortName", "ShortName");
        regulationFields.put("Short Name", "ShortName");
        regulationFields.put("ShortName", "ShortName");
        regulationFields.put("ref", "RefNumber");
        regulationFields.put("Ref.", "RefNumber");
        regulationFields.put("RefNumber", "RefNumber");
        regulationFields.put("parent", "Parent_ID");
        regulationFields.put("Parent", "Parent_ID");
        regulationFields.put("Parent_ID", "Parent_ID");
        regulationFields.put("legalAdvice", "Legal_Advice");
        regulationFields.put("Legal Advice", "Legal_Advice");
        regulationFields.put("Legal_Advice", "Legal_Advice");
        regulationFields.put("legalAdviceType", "LegalAdviceType_ID");
        regulationFields.put("Legal Advice Type", "LegalAdviceType_ID");
        regulationFields.put("LegalAdviceType_ID", "LegalAdviceType_ID");
        regulationFields.put("additionalInfo", "Additional_Info");
        regulationFields.put("Additional Info", "Additional_Info");
        regulationFields.put("Additional_Info", "Additional_Info");
        regulationFields.put("complianceLevel", "ComplianceLevel_ID");
        regulationFields.put("Compliance Level", "ComplianceLevel_ID");
        regulationFields.put("ComplianceLevel_ID", "ComplianceLevel_ID");
        regulationFields.put("maturity", "RegulationMaturity_ID");
        regulationFields.put("Maturity", "RegulationMaturity_ID");
        regulationFields.put("RegulationMaturity_ID", "RegulationMaturity_ID");
        regulationFields.put("probability", "RegulationProbability_ID");
        regulationFields.put("Probability", "RegulationProbability_ID");
        regulationFields.put("RegulationProbability_ID", "RegulationProbability_ID");
        regulationFields.put("status", "RegulationStatus_ID");
        regulationFields.put("Status", "RegulationStatus_ID");
        regulationFields.put("BUDG Status", "RegulationStatus_ID");
        regulationFields.put("RegulationStatus_ID", "RegulationStatus_ID");
        regulationFields.put("stage", "RegulationStage_ID");
        regulationFields.put("Stage", "RegulationStage_ID");
        regulationFields.put("RegulationStage_ID", "RegulationStage_ID");
        regulationFields.put("accessControl", "Is_Public");
        regulationFields.put("Access Control", "Is_Public");
        regulationFields.put("Is_Public", "Is_Public");
        regulationFields.put("publicationDate", "Publication_Date");
        regulationFields.put("Publication Date", "Publication_Date");
        regulationFields.put("Publication_Date", "Publication_Date");
        regulationFields.put("effectiveDate", "Effective_Date");
        regulationFields.put("Effective Date", "Effective_Date");
        regulationFields.put("Effective_Date", "Effective_Date");
        regulationFields.put("endDate", "End_Date");
        regulationFields.put("End Date", "End_Date");
        regulationFields.put("End_Date", "End_Date");
        regulationFields.put("impactRating", "RegulationImpactRating_ID");
        regulationFields.put("Impact Rating", "RegulationImpactRating_ID");
        regulationFields.put("RegulationImpactRating_ID", "RegulationImpactRating_ID");
        tableFieldMappings.put("regulation", regulationFields);
        
        // Regulator table mappings
        Map<String, String> regulatorFields = new HashMap<>();
        regulatorFields.put("name", "PrimaryName");
        regulatorFields.put("Name", "PrimaryName");
        regulatorFields.put("PrimaryName", "PrimaryName");
        regulatorFields.put("shortName", "ShortName");
        regulatorFields.put("Short Name", "ShortName");
        regulatorFields.put("ShortName", "ShortName");
        regulatorFields.put("description", "Description");
        regulatorFields.put("Description", "Description");
        tableFieldMappings.put("regulator", regulatorFields);
        
        // Regulatory Theme table mappings
        Map<String, String> regulatoryThemeFields = new HashMap<>();
        regulatoryThemeFields.put("name", "PrimaryName");
        regulatoryThemeFields.put("Primary Name", "PrimaryName");
        regulatoryThemeFields.put("PrimaryName", "PrimaryName");
        regulatoryThemeFields.put("description", "Description");
        regulatoryThemeFields.put("Description", "Description");
        regulatoryThemeFields.put("ref", "RefNumber");
        regulatoryThemeFields.put("Ref.", "RefNumber");
        regulatoryThemeFields.put("RefNumber", "RefNumber");
        regulatoryThemeFields.put("shortName", "ShortName");
        regulatoryThemeFields.put("Short Name", "ShortName");
        regulatoryThemeFields.put("ShortName", "ShortName");
        regulatoryThemeFields.put("parent", "Parent_ID");
        regulatoryThemeFields.put("Parent", "Parent_ID");
        regulatoryThemeFields.put("Parent Name", "Parent_ID");
        regulatoryThemeFields.put("Parent_ID", "Parent_ID");
        regulatoryThemeFields.put("status", "Status_ID");
        regulatoryThemeFields.put("Status", "Status_ID");
        regulatoryThemeFields.put("BUDG Status", "Status_ID");
        regulatoryThemeFields.put("Status_ID", "Status_ID");
        tableFieldMappings.put("regulatory_theme", regulatoryThemeFields);
        tableFieldMappings.put("regulatorytheme", regulatoryThemeFields);
        
        // Get the mapping for this table (case-insensitive)
        String tableKey = tableName.toLowerCase();
        Map<String, String> fieldMapping = tableFieldMappings.get(tableKey);
        
        if (fieldMapping != null) {
            // Try exact match first
            String mappedColumn = fieldMapping.get(field);
            if (mappedColumn != null) {
                return mappedColumn;
            }
            
            // Try case-insensitive match
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(field)) {
                    return entry.getValue();
                }
            }
        }
        
        // Default: assume field name matches column name (might already be a valid column name)
        // This handles cases where the dropdown shows actual column names
        return field;
    }
    
    private String buildSqlCondition(String dbColumn, String condition, Object value, List<Object> parameters) {
        if (dbColumn == null || condition == null || value == null) {
            return null;
        }
        
        // Special case for attribute table - check if column exists
        if (dbColumn.equals("DBFieldName")) {
            //system.out.println("WARNING: DBFieldName column doesn't exist, using PrimaryName instead");
            dbColumn = "PrimaryName";
        }
        
        String valueStr = value.toString();
        
        switch (condition.toLowerCase()) {
            case "contains":
                parameters.add("%" + valueStr + "%");
                return dbColumn + " LIKE ?";
                
            case "equals":
            case "is":
                parameters.add(valueStr);
                return dbColumn + " = ?";
                
            case "starts_with":
            case "startswith":
                parameters.add(valueStr + "%");
                return dbColumn + " LIKE ?";
                
            case "ends_with":
            case "endswith":
                parameters.add("%" + valueStr);
                return dbColumn + " LIKE ?";
                
            case "not_equals":
            case "not":
                parameters.add(valueStr);
                return dbColumn + " != ?";
                
            case "greater_than":
            case "gt":
                parameters.add(valueStr);
                return dbColumn + " > ?";
                
            case "less_than":
            case "lt":
                parameters.add(valueStr);
                return dbColumn + " < ?";
                
            case "is_null":
                return dbColumn + " IS NULL";
                
            case "is_not_null":
                return dbColumn + " IS NOT NULL";
                
            default:
                // Default to contains for unknown conditions
                System.err.println("Unknown condition: " + condition + ", defaulting to LIKE");
                parameters.add("%" + valueStr + "%");
                return dbColumn + " LIKE ?";
        }
    }
    
    private int getSearchResultCount(Connection conn, String tableName, Map<String, Object> searchParams, String facetName, Set<Integer> filteredIds) throws SQLException {
        // If facet is not part of the saved search, return 0 count
        if (searchParams.containsKey("facetNotInSearch") && (Boolean) searchParams.get("facetNotInSearch")) {
            //system.out.println("Facet '" + facetName + "' not in saved search - returning count: 0");
            return 0;
        }
        
        // If we have filtered IDs from Unison Search, use them directly
        if (filteredIds != null) {
            return filteredIds.size();
        }
        
        // Special handling for attribute table - always use attribute-specific WHERE clause builder
        boolean isAttributeTable = tableName.equalsIgnoreCase("attribute");
        
        String fromClause = tableName;
        String countColumn;
        
        if (isAttributeTable) {
            // Use alias for attribute table
            fromClause = tableName + " a";
            countColumn = "a.ID";
        } else {
            // For non-attribute tables, use table name with ID column
            countColumn = tableName + ".ID";
        }
        
        String sql = "SELECT COUNT(DISTINCT " + countColumn + ") as count FROM " + fromClause;
        
        // Build WHERE clause from filterGroups
        List<Object> parameters = new ArrayList<>();
        String tableAlias = isAttributeTable ? "a" : tableName;
        this.currentTableNameForMapping = tableName;
        String whereClause = isAttributeTable ?
                            buildWhereClauseForAttribute(searchParams, tableAlias, parameters) :
                            buildWhereClause(searchParams, tableAlias, parameters);
        
        sql += whereClause;
        
        //system.out.println("Executing COUNT query for table '" + tableName + "': " + sql);
        //system.out.println("Parameters: " + parameters);
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    //system.out.println("Query returned count: " + count);
                    return count;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error executing COUNT query for table '" + tableName + "': " + e.getMessage());
            System.err.println("SQL: " + sql);
            throw e;
        }
        //system.out.println("Query returned no rows");
        return 0;
    }
    
    /**
     * Build WHERE clause specifically for attribute table, handling dbFieldName as PrimaryName
     */
    private String buildWhereClauseForAttribute(Map<String, Object> searchParams, String tableAlias, 
                                                List<Object> parameters) {
        StringBuilder whereClause = new StringBuilder();
        
        //system.out.println("buildWhereClauseForAttribute called with tableAlias=" + tableAlias);
        //system.out.println("searchParams keys: " + searchParams.keySet());
        
        if (searchParams.containsKey("filterGroups")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filterGroups = (List<Map<String, Object>>) searchParams.get("filterGroups");
            
            if (filterGroups != null && !filterGroups.isEmpty()) {
                List<String> nameConditions = new ArrayList<>(); // For name/dbFieldName - OR together (both map to PrimaryName)
                List<String> otherConditions = new ArrayList<>(); // For other fields - AND together
                
                //system.out.println("Building WHERE clause for attribute table with " + filterGroups.size() + " filter groups");
                
                // Track unique name/dbFieldName conditions to avoid duplicates
                // Since both name and dbFieldName map to PrimaryName, we only need one condition per unique value
                Map<String, String> uniqueNameConditions = new HashMap<>(); // value -> sqlCondition
                
                for (Map<String, Object> filterGroup : filterGroups) {
                    String field = (String) filterGroup.get("field");
                    String condition = (String) filterGroup.get("condition");
                    Object value = filterGroup.get("value");
                    
                    //system.out.println("Processing attribute filter: field=" + field + ", condition=" + condition + ", value=" + value);
                    
                    if (field != null && condition != null && value != null) {
                        String sqlCondition = null;
                        
                        // Handle name and dbFieldName - both map to PrimaryName
                        if (field.equals("name") || field.equals("dbFieldName") || field.equals("DBFieldName")) {
                            // Both name and dbFieldName refer to PrimaryName column
                            String qualifiedColumn = tableAlias + ".PrimaryName";
                            // Create a unique key for this condition to avoid duplicates
                            String conditionKey = condition + "|" + value.toString();
                            
                            // Only add if we haven't seen this exact condition before
                            if (!uniqueNameConditions.containsKey(conditionKey)) {
                                sqlCondition = buildSqlCondition(qualifiedColumn, condition, value, parameters);
                                if (sqlCondition != null) {
                                    uniqueNameConditions.put(conditionKey, sqlCondition);
                                    nameConditions.add(sqlCondition);
                                    //system.out.println("Added " + field + " condition (mapped to PrimaryName): " + sqlCondition);
                                }
                            } else {
                                //system.out.println("Skipping duplicate condition for " + field + " (same value and condition as previous)");
                            }
                        } else {
                            // Other fields - standard mapping
                            if (field.equals("description") || field.equals("Definition")) {
                                field = "description";
                            }
                            
                            String dbColumn = mapFieldToDbColumn(field, "attribute");
                            String qualifiedColumn = tableAlias + "." + dbColumn;
                            sqlCondition = buildSqlCondition(qualifiedColumn, condition, value, parameters);
                            if (sqlCondition != null) {
                                otherConditions.add(sqlCondition);
                                //system.out.println("Added other condition: " + sqlCondition);
                            }
                        }
                    }
                }
                
                // Combine conditions: (nameConditions OR together) AND (otherConditions AND together)
                List<String> allConditions = new ArrayList<>();
                
                if (!nameConditions.isEmpty()) {
                    if (nameConditions.size() == 1) {
                        allConditions.add(nameConditions.get(0));
                    } else {
                        // OR the name conditions together
                        allConditions.add("(" + String.join(" OR ", nameConditions) + ")");
                    }
                }
                
                allConditions.addAll(otherConditions);
                
                if (!allConditions.isEmpty()) {
                    whereClause.append(" WHERE ");
                    whereClause.append(String.join(" AND ", allConditions));
                    //system.out.println("Final WHERE clause for attribute: " + whereClause.toString());
                    //system.out.println("Parameters count: " + parameters.size());
                } else {
                    //system.out.println("No conditions generated for attribute WHERE clause");
                }
            } else {
                //system.out.println("filterGroups is empty or null");
            }
        } else {
            //system.out.println("No filterGroups key in searchParams for attribute table");
        }
        
        return whereClause.toString();
    }
    
    private List<Map<String, Object>> getSearchResultTable(Connection conn, String tableName, 
                                                             Map<String, Object> searchParams, String facetName, 
                                                             List<String> displayColumns, Set<Integer> filteredIds) throws SQLException {
        System.out.println("[NewWidgetServlet] ===== getSearchResultTable CALLED =====");
        System.out.println("[NewWidgetServlet] tableName: " + tableName);
        System.out.println("[NewWidgetServlet] facetName: " + facetName);
        System.out.println("[NewWidgetServlet] displayColumns: " + displayColumns);
        System.out.println("[NewWidgetServlet] filteredIds: " + (filteredIds != null ? filteredIds.size() + " IDs: " + filteredIds : "null"));
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        // If we have filtered IDs from Unison Search and they're empty, return empty list
        // NOTE: If filteredIds is null, we'll proceed with the query (for non-saved-search widgets)
        if (filteredIds != null && filteredIds.isEmpty()) {
            System.out.println("[NewWidgetServlet] Filtered IDs is empty - returning empty table results");
            return results; // No matching IDs
        }
        
        // If filteredIds is null, we might not have Unison Search results - but still try to build the query
        // This allows the method to work for non-saved-search widgets
        if (filteredIds == null) {
            System.out.println("[NewWidgetServlet] WARNING: filteredIds is null - proceeding without ID filter (may be non-saved-search widget)");
        }
        
        // Use alias for attribute table, otherwise use table name
        boolean isAttributeTable = tableName.equalsIgnoreCase("attribute");
        String tableAlias = isAttributeTable ? "a" : "t";
        String fromClause = isAttributeTable ? tableName + " a" : tableName + " t";
        
        // Build SELECT columns with lookup joins for foreign keys
        List<String> selectColumns = new ArrayList<>();
        Map<String, LookupJoinInfo> columnLookups = new HashMap<>(); // column -> lookup info
        StringBuilder joinClause = new StringBuilder();
        int joinCounter = 0;
        
        for (String col : displayColumns) {
            System.out.println("[NewWidgetServlet] Processing display column: '" + col + "'");
            String dbColumn = mapFieldToDbColumn(col, tableName);
            if (dbColumn == null) {
                dbColumn = col; // Use as-is if mapping fails
            }
            System.out.println("[NewWidgetServlet] Mapped column '" + col + "' to database column: '" + dbColumn + "'");
            
            // Check if this column is a foreign key that needs a lookup
            LookupJoinInfo lookupInfo = getLookupJoinInfoForTableColumn(dbColumn, tableName, col);
            
            if (lookupInfo != null) {
                // This is a foreign key - join with lookup table
                String joinAlias = "lk" + (joinCounter++);
                joinClause.append(" LEFT JOIN ").append(lookupInfo.lookupTable)
                          .append(" ").append(joinAlias)
                          .append(" ON ").append(tableAlias).append(".").append(dbColumn)
                          .append(" = ").append(joinAlias).append(".").append(lookupInfo.lookupIdColumn);
                
                // Select the label column from lookup table
                String labelExpr;
                if (lookupInfo.labelColumn.contains("COALESCE")) {
                    // Handle COALESCE expressions - prefix each column with join alias
                    String innerExpr = lookupInfo.labelColumn.replace("COALESCE(", "").replace(")", "").trim();
                    String[] parts = innerExpr.split(",");
                    StringBuilder newExpr = new StringBuilder("COALESCE(");
                    for (int i = 0; i < parts.length; i++) {
                        if (i > 0) newExpr.append(", ");
                        String part = parts[i].trim();
                        // Add join alias prefix
                        newExpr.append(joinAlias).append(".").append(part);
                    }
                    newExpr.append(", 'Not Set')");
                    labelExpr = newExpr.toString();
                } else {
                    labelExpr = joinAlias + "." + lookupInfo.labelColumn;
                }
                
                selectColumns.add(labelExpr + " AS " + col);
                columnLookups.put(col, lookupInfo);
            } else {
                // Regular column - use as-is
                String qualifiedColumn = tableAlias + "." + dbColumn;
                selectColumns.add(qualifiedColumn + " AS " + col);
            }
        }
        
        // Build SELECT query
        StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
        sql.append(String.join(", ", selectColumns));
        sql.append(" FROM ").append(fromClause);
        sql.append(joinClause);
        
        // Build WHERE clause
        List<Object> parameters = new ArrayList<>();
        String whereClause = "";
        
        // If we have filtered IDs from Unison Search, use them directly (skip saved search filters)
        // Unison Search already filtered the data, so we just need to filter by IDs
        if (filteredIds != null && !filteredIds.isEmpty()) {
            String idColumn = isAttributeTable ? "a.ID" : tableAlias + ".ID";
            whereClause = " WHERE " + idColumn + " IN (";
            // Add placeholders for IDs
            String placeholders = String.join(",", Collections.nCopies(filteredIds.size(), "?"));
            whereClause += placeholders + ")";
            parameters.addAll(filteredIds);
            
            // Add soft delete condition
            String deletedColumn = getSoftDeleteColumn(tableName);
            if (deletedColumn != null) {
                whereClause += " AND " + tableAlias + "." + deletedColumn + " IS NULL";
            }
        } else {
            // No filtered IDs - use saved search filters (for non-saved-search widgets)
            this.currentTableNameForMapping = tableName;
            whereClause = isAttributeTable ?
                        buildWhereClauseForAttribute(searchParams, tableAlias, parameters) :
                        buildWhereClause(searchParams, tableAlias, parameters);
        }
        
        sql.append(whereClause);
        sql.append(" LIMIT 10"); // Limit preview to 10 rows
        
        System.out.println("[NewWidgetServlet] Executing TABLE query for table '" + tableName + "': " + sql.toString());
        System.out.println("[NewWidgetServlet] TABLE query parameters: " + parameters);
        System.out.println("[NewWidgetServlet] TABLE query filteredIds: " + (filteredIds != null ? filteredIds.size() + " IDs: " + filteredIds : "null"));
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (String column : displayColumns) {
                        Object value = rs.getObject(column);
                        row.put(column, value != null ? value.toString() : "");
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error executing TABLE query for table '" + tableName + "': " + e.getMessage());
            System.err.println("SQL: " + sql.toString());
            throw e;
        }
        
        //system.out.println("Query returned " + results.size() + " rows");
        return results;
    }
    
    private Map<String, Integer> getSearchResultChart(Connection conn, String tableName, 
                                                       Map<String, Object> searchParams, String facetName, 
                                                       String visualizeBy, Set<Integer> filteredIds) throws SQLException {
        System.out.println("[NewWidgetServlet] ===== getSearchResultChart CALLED =====");
        System.out.println("[NewWidgetServlet] tableName: " + tableName);
        System.out.println("[NewWidgetServlet] facetName: " + facetName);
        System.out.println("[NewWidgetServlet] visualizeBy: " + visualizeBy);
        System.out.println("[NewWidgetServlet] filteredIds: " + (filteredIds != null ? filteredIds.size() + " IDs: " + filteredIds : "null"));
        
        Map<String, Integer> chartData = new HashMap<>();
        
        // If we have filtered IDs from Unison Search and they're empty, return empty chart
        // NOTE: If filteredIds is null, we'll proceed with the query (for non-saved-search widgets)
        if (filteredIds != null && filteredIds.isEmpty()) {
            System.out.println("[NewWidgetServlet] Filtered IDs is empty - returning empty chart data");
            return chartData;
        }
        
        // If filteredIds is null, we might not have Unison Search results - but still try to build the query
        // This allows the method to work for non-saved-search widgets
        if (filteredIds == null) {
            System.out.println("[NewWidgetServlet] WARNING: filteredIds is null - proceeding without ID filter (may be non-saved-search widget)");
        }
        
        // Map visualizeBy field name to database column name
        System.out.println("[NewWidgetServlet] Chart - visualizeBy: " + visualizeBy + ", tableName: " + tableName);
        String dbVisualizeByColumn = mapFieldToDbColumn(visualizeBy, tableName);
        if (dbVisualizeByColumn == null || dbVisualizeByColumn.isEmpty()) {
            // If mapping fails, try using the visualizeBy as-is (might be already a column name)
            dbVisualizeByColumn = visualizeBy;
            System.out.println("[NewWidgetServlet] Chart - mapping failed, using visualizeBy as-is: " + dbVisualizeByColumn);
        } else {
            System.out.println("[NewWidgetServlet] Chart - mapped to dbColumn: " + dbVisualizeByColumn);
        }
        
        // Use alias for all tables to ensure consistent ID filtering
        boolean isAttributeTable = tableName.equalsIgnoreCase("attribute");
        String tableAlias = "t";
        String fromClause = tableName + " " + tableAlias;
        String joinClause = "";
        
        // Check if this column is a foreign key to a lookup table (Status, Lifecycle, etc.)
        LookupJoinInfo lookupInfo = getLookupJoinInfo(dbVisualizeByColumn, tableName, visualizeBy);
        if (lookupInfo != null) {
            System.out.println("[NewWidgetServlet] Chart - lookupInfo found: table=" + lookupInfo.lookupTable + ", idColumn=" + lookupInfo.lookupIdColumn + ", labelColumn=" + lookupInfo.labelColumn);
        } else {
            System.out.println("[NewWidgetServlet] Chart - no lookupInfo found, will use raw column value");
        }
        
        // Build query - use label from lookup table if available, otherwise use the column value
        String labelColumn;
        
        if (lookupInfo != null) {
            // Use LEFT JOIN to include records even if lookup value doesn't exist
            joinClause += " LEFT JOIN " + lookupInfo.lookupTable + " lk ON " + tableAlias + "." + dbVisualizeByColumn + " = lk." + lookupInfo.lookupIdColumn;
            // Build label column - handle COALESCE expressions in labelColumn
            if (lookupInfo.labelColumn.contains("COALESCE")) {
                // If labelColumn already has COALESCE, prefix each column with lk. and add Not Set fallback
                // Example: COALESCE(PrimaryName, primaryname) -> COALESCE(lk.PrimaryName, lk.primaryname, 'Not Set')
                String innerExpr = lookupInfo.labelColumn.replace("COALESCE(", "").replace(")", "").trim();
                // Split by comma and prefix each part with lk.
                String[] parts = innerExpr.split(",");
                StringBuilder newExpr = new StringBuilder("COALESCE(");
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) newExpr.append(", ");
                    String part = parts[i].trim();
                    newExpr.append("lk.").append(part);
                }
                newExpr.append(", 'Not Set')");
                labelColumn = newExpr.toString();
            } else {
                // Simple column name - wrap in COALESCE
                labelColumn = "COALESCE(lk." + lookupInfo.labelColumn + ", 'Not Set')";
            }
        } else {
            // No lookup table - use the column value directly, cast to string
            labelColumn = "COALESCE(CAST(" + tableAlias + "." + dbVisualizeByColumn + " AS CHAR), 'Unknown')";
        }
        
        // Use DISTINCT for attribute table to avoid duplicate counts
        String countExpr = isAttributeTable ? "COUNT(DISTINCT " + tableAlias + ".ID)" : "COUNT(*)";
        String sql = "SELECT " + labelColumn + " as label, " + countExpr + " as count FROM " + fromClause;
        if (!joinClause.isEmpty()) {
            sql += joinClause;
        }
        
        // Build WHERE clause
        List<Object> parameters = new ArrayList<>();
        // Store actual table name for field mapping
        this.currentTableNameForMapping = tableName;
        
        String whereClause = "";
        
        // If we have filtered IDs from Unison Search, use ONLY those IDs (Unison Search already applied all filters)
        if (filteredIds != null && !filteredIds.isEmpty()) {
            // Always use tableAlias for ID column (consistent with fromClause)
            String idColumn = tableAlias + ".ID";
            whereClause = " WHERE " + idColumn + " IN (";
            // Add placeholders for IDs
            String placeholders = String.join(",", Collections.nCopies(filteredIds.size(), "?"));
            whereClause += placeholders + ")";
            parameters.addAll(filteredIds);
            
            // Add soft delete condition
            String deletedColumn = getSoftDeleteColumn(tableName);
            if (deletedColumn != null) {
                whereClause += " AND " + tableAlias + "." + deletedColumn + " IS NULL";
            }
            
            System.out.println("[NewWidgetServlet] Using filtered IDs from Unison Search for chart: " + filteredIds.size() + " IDs");
            System.out.println("[NewWidgetServlet] Chart SQL will filter by: " + idColumn + " IN (" + filteredIds.size() + " IDs)");
            if (deletedColumn != null) {
                System.out.println("[NewWidgetServlet] Added soft delete condition: " + tableAlias + "." + deletedColumn + " IS NULL");
            }
        } else {
            // Fallback to old filterGroups approach if no filtered IDs
            whereClause = isAttributeTable ?
                            buildWhereClauseForAttribute(searchParams, tableAlias, parameters) :
                            buildWhereClause(searchParams, tableAlias, parameters);
            System.out.println("[NewWidgetServlet] Using filterGroups approach for chart (no filtered IDs)");
        }
        
        sql += whereClause;
        sql += " GROUP BY " + labelColumn;
        sql += " ORDER BY count DESC";
        sql += " LIMIT 20"; // Limit to top 20 groups
        
        //system.out.println("Executing CHART query for table '" + tableName + "': " + sql);
        //system.out.println("Parameters: " + parameters);
        //system.out.println("Visualize by column: " + dbVisualizeByColumn);
        if (lookupInfo != null) {
            //system.out.println("Using lookup table: " + lookupInfo.lookupTable);
        }
        
        // Debug: Print the SQL with parameter values
        String debugSql = sql;
        for (Object param : parameters) {
            debugSql = debugSql.replaceFirst("\\?", param instanceof String ? "'" + param + "'" : String.valueOf(param));
        }
        //system.out.println("DEBUG - Executing SQL: " + debugSql);
        
        // Debug logging
        System.out.println("[NewWidgetServlet] ===== CHART QUERY DEBUG =====");
        System.out.println("[NewWidgetServlet] Table: " + tableName);
        System.out.println("[NewWidgetServlet] VisualizeBy: " + visualizeBy);
        System.out.println("[NewWidgetServlet] DB Column: " + dbVisualizeByColumn);
        System.out.println("[NewWidgetServlet] Filtered IDs count: " + (filteredIds != null ? filteredIds.size() : 0));
        if (filteredIds != null && !filteredIds.isEmpty()) {
            System.out.println("[NewWidgetServlet] Filtered IDs: " + filteredIds);
        }
        System.out.println("[NewWidgetServlet] SQL: " + sql);
        System.out.println("[NewWidgetServlet] Parameters: " + parameters);
        System.out.println("[NewWidgetServlet] =============================");
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                int rowCount = 0;
                while (rs.next()) {
                    String label = rs.getString("label");
                    int count = rs.getInt("count");
                    if (label == null || label.trim().isEmpty()) {
                        label = "Not Set";
                    }
                    chartData.put(label.trim(), count);
                    rowCount++;
                    System.out.println("[NewWidgetServlet] Chart data row: " + label + " = " + count);
                }
                System.out.println("[NewWidgetServlet] Chart query returned " + rowCount + " groups");
                if (rowCount == 0 && filteredIds != null && !filteredIds.isEmpty()) {
                    System.out.println("[NewWidgetServlet] WARNING: No chart data returned despite having " + filteredIds.size() + " filtered IDs");
                }
            }
        } catch (SQLException e) {
            System.err.println("[NewWidgetServlet] SQL Error executing CHART query for table '" + tableName + "': " + e.getMessage());
            System.err.println("[NewWidgetServlet] SQL: " + sql);
            System.err.println("[NewWidgetServlet] Parameters: " + parameters);
            e.printStackTrace();
            throw e;
        }
        
        //system.out.println("Query returned " + chartData.size() + " groups");
        
        // Reset the table name mapping field
        this.currentTableNameForMapping = null;
        
        return chartData;
    }
    
    /**
     * Information about a lookup table join
     */
    private static class LookupJoinInfo {
        String lookupTable;
        String lookupIdColumn;
        String labelColumn;
        
        LookupJoinInfo(String lookupTable, String lookupIdColumn, String labelColumn) {
            this.lookupTable = lookupTable;
            this.lookupIdColumn = lookupIdColumn;
            this.labelColumn = labelColumn;
        }
    }
    
    /**
     * Determine if a column is a foreign key to a lookup table and return join information
     */
    private LookupJoinInfo getLookupJoinInfo(String dbColumn, String tableName, String fieldName) {
        // Normalize column and field names for comparison
        String columnLower = dbColumn.toLowerCase();
        String fieldLower = fieldName.toLowerCase();
        
        // Status lookup - most common
        if (columnLower.equals("status") || fieldLower.contains("status")) {
            // Handle case-sensitive column names
            return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
        }
        
        // Lifecycle lookups - table-specific
        if (columnLower.contains("lifecycle") || fieldLower.contains("lifecycle")) {
            if (tableName.equalsIgnoreCase("dataset")) {
                return new LookupJoinInfo("dataset_lifecycle", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("process")) {
                return new LookupJoinInfo("process_lifecycle_status", "id", "primaryname");
            } else if (tableName.equalsIgnoreCase("product")) {
                return new LookupJoinInfo("product_lifecycle", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("project")) {
                return new LookupJoinInfo("project_lifecycle", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("policy")) {
                return new LookupJoinInfo("policy_lifecycle_status", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("committee")) {
                return new LookupJoinInfo("committee_lifecycle", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("system")) {
                return new LookupJoinInfo("system_lifecycle", "ID", "Name");
            } else if (tableName.equalsIgnoreCase("glossary")) {
                return new LookupJoinInfo("glossary_lifecycle", "id", "Name");
            } else if (tableName.equalsIgnoreCase("people")) {
                return new LookupJoinInfo("people_lifecycle_status", "ID", "Primary_Name");
            } else if (tableName.equalsIgnoreCase("business_area") || tableName.equalsIgnoreCase("businessarea")) {
                return new LookupJoinInfo("business_area_lifecycle", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("capability")) {
                return new LookupJoinInfo("capability_lifecycle", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("client")) {
                return new LookupJoinInfo("client_lifecycle", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("legal_entity") || tableName.equalsIgnoreCase("legalentity") || tableName.equalsIgnoreCase("legal")) {
                return new LookupJoinInfo("legal_entity_lifecycle", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("system_interface") || tableName.equalsIgnoreCase("interface")) {
                return new LookupJoinInfo("system_interface_lifecycle", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("org_unit") || tableName.equalsIgnoreCase("orgunit")) {
                // Org Unit typically doesn't have lifecycle, but check if it does
                return null;
            } else if (tableName.equalsIgnoreCase("regulation")) {
                return new LookupJoinInfo("regulation_lifecycle", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("regulatory_theme") || tableName.equalsIgnoreCase("regulatorytheme")) {
                return new LookupJoinInfo("regulatory_theme_lifecycle", "id", "PrimaryName");
            } else {
                // Default lifecycle table - but many tables don't have a lifecycle lookup
                // Return null to use raw value instead
                return null;
            }
        }
        
        // Type lookups
        if (columnLower.contains("type") || fieldLower.contains("type") || columnLower.equals("datasettype")) {
            if (tableName.equalsIgnoreCase("dataset")) {
                return new LookupJoinInfo("dataset_type", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("policy")) {
                return new LookupJoinInfo("policy_type", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("committee")) {
                return new LookupJoinInfo("committee_type", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("system")) {
                return new LookupJoinInfo("system_type", "id", "Name");
            } else if (tableName.equalsIgnoreCase("glossary")) {
                return new LookupJoinInfo("glossary_type", "id", "Name");
            } else if (tableName.equalsIgnoreCase("process")) {
                return new LookupJoinInfo("process_type", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("capability")) {
                return new LookupJoinInfo("capability_type", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("client")) {
                return new LookupJoinInfo("client_type", "id", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("business_area") || tableName.equalsIgnoreCase("businessarea")) {
                return new LookupJoinInfo("business_area_type", "id", "PrimaryName");
            }
        }
        
        // MasterSource (System) - for dataset table
        if ((columnLower.equals("mastersource") || fieldLower.contains("mastersource") || fieldLower.contains("system")) 
            && tableName.equalsIgnoreCase("dataset")) {
            return new LookupJoinInfo("system", "id", "Name");
        }
        
        // Glossary - for dataset and attribute tables
        if (columnLower.equals("glossary") || fieldLower.contains("glossary") || columnLower.equals("glossary_id")) {
            if (tableName.equalsIgnoreCase("dataset") || tableName.equalsIgnoreCase("attribute")) {
                return new LookupJoinInfo("glossary", "ID", "Name");
            }
        }
        
        // Attribute-specific lookups
        if (tableName.equalsIgnoreCase("attribute")) {
            if (columnLower.equals("editability") || fieldLower.contains("editability")) {
                return new LookupJoinInfo("attribute_editability", "id", "PrimaryName");
            } else if (columnLower.equals("editability_role") || columnLower.equals("editabilityrole") || fieldLower.contains("editabilityrole")) {
                return new LookupJoinInfo("attribute_edit_role", "id", "PrimaryName");
            } else if (columnLower.equals("origination") || fieldLower.contains("origin")) {
                return new LookupJoinInfo("attribute_origination", "id", "PrimaryName");
            } else if (columnLower.equals("data_type_id") || columnLower.equals("datatype") || fieldLower.contains("datatype")) {
                return new LookupJoinInfo("attribute_datatype", "id", "PrimaryName");
            } else if (columnLower.equals("requirement_id") || fieldLower.contains("requirement")) {
                return new LookupJoinInfo("requirement", "id", "PrimaryName");
            }
        }
        
        // Process-specific lookups
        if (tableName.equalsIgnoreCase("process")) {
            if (columnLower.equals("processautomation_id") || columnLower.equals("automation") || fieldLower.contains("automation")) {
                return new LookupJoinInfo("process_automation", "id", "primaryname");
            } else if (columnLower.equals("processclass_id") || columnLower.equals("classification") || fieldLower.contains("classification")) {
                return new LookupJoinInfo("process_class", "id", "primaryname");
            } else if (columnLower.equals("parentid") || columnLower.equals("parent_id") || fieldLower.contains("parent")) {
                return new LookupJoinInfo("process", "id", "primaryname");
            } else if (columnLower.equals("step_type") || fieldLower.contains("step_type")) {
                // Step type might be a lookup table or enum - check if it exists
                return null; // For now, return null to use raw value
            }
        }
        
        // People-specific lookups
        if (tableName.equalsIgnoreCase("people")) {
            if (columnLower.equals("status_id") || (columnLower.equals("status") && fieldLower.contains("status"))) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("employee_type") || columnLower.equals("employeetype") || fieldLower.contains("employeetype")) {
                return new LookupJoinInfo("employment_type", "id", "primary_Name");
            } else if (columnLower.equals("profile_id") || columnLower.equals("profile") || fieldLower.contains("profile") || 
                       columnLower.equals("system_role") || fieldLower.contains("system_role")) {
                return new LookupJoinInfo("role", "id", "primaryname");
            } else if (columnLower.equals("org_unit_id") || columnLower.equals("orgunit_id") || fieldLower.contains("org_unit") || fieldLower.contains("orgunit")) {
                return new LookupJoinInfo("org_unit", "ID", "Name");
            }
        }
        
        // System-specific lookups (already handled MasterSource above, but add more)
        // Note: cia_rating table uses "Values" column, not "PrimaryName"
        if (tableName.equalsIgnoreCase("system")) {
            if (columnLower.equals("classification") || fieldLower.contains("classification")) {
                return new LookupJoinInfo("system_classification", "id", "Name");
            } else if (columnLower.equals("confidentiality_rating") || fieldLower.contains("confidentiality") || fieldLower.contains("cia rating c")) {
                return new LookupJoinInfo("cia_rating", "id", "Values");
            } else if (columnLower.equals("integrity_rating") || fieldLower.contains("integrity") || fieldLower.contains("cia rating i")) {
                return new LookupJoinInfo("cia_rating", "id", "Values");
            } else if (columnLower.equals("availability_rating") || fieldLower.contains("availability") || fieldLower.contains("cia rating a")) {
                return new LookupJoinInfo("cia_rating", "id", "Values");
            } else if (columnLower.equals("type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("system_type", "id", "Name");
            } else if (columnLower.equals("lifecycle") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("system_lifecycle", "id", "Name");
            } else if (columnLower.equals("parent_id") || columnLower.equals("parentid") || fieldLower.contains("parent")) {
                return new LookupJoinInfo("system", "id", "Name");
            } else if (columnLower.equals("is_public") || columnLower.equals("ispublic") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Security Classification - check BEFORE general classification to avoid conflicts
        // Note: security_classification table uses "Name" column, not "PrimaryName"
        if (columnLower.contains("security_classification") || columnLower.equals("security_classification") || 
            (fieldLower.contains("security") && (fieldLower.contains("classification") || columnLower.contains("security")))) {
            return new LookupJoinInfo("security_classification", "ID", "Name");
        }
        
        // Classification (general, not security)
        // Note: interface_classification uses "Name" column, not "PrimaryName"
        if (columnLower.contains("classification") || fieldLower.contains("classification") || columnLower.equals("processclass_id")) {
            if (tableName.equalsIgnoreCase("system")) {
                return new LookupJoinInfo("system_classification", "id", "Name");
            } else if (tableName.equalsIgnoreCase("process")) {
                return new LookupJoinInfo("process_class", "id", "primaryname");
            } else if (tableName.equalsIgnoreCase("committee")) {
                return new LookupJoinInfo("committee_classification", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("capability")) {
                return new LookupJoinInfo("capability_classification", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("interface") || tableName.equalsIgnoreCase("system_interface")) {
                // interface_classification uses "Name" column, not "PrimaryName"
                return new LookupJoinInfo("interface_classification", "id", "Name");
            } else if (tableName.equalsIgnoreCase("project")) {
                return new LookupJoinInfo("project_classification", "id", "primaryname");
            } else {
                // Don't use generic "classification" table as it doesn't exist
                // Return null to use raw value instead
                return null;
            }
        }
        
        // Viewing / AccessControlType / Is_Public - table-specific
        // Note: viewing table uses "Name" column (lowercase id), not "PrimaryName"
        if (columnLower.contains("viewing") || fieldLower.contains("viewing") || 
            columnLower.equals("accesscontroltype") || fieldLower.contains("accesscontroltype") ||
            columnLower.equals("is_public") || fieldLower.contains("is_public")) {
            // viewing table always uses "Name" column and lowercase "id"
            return new LookupJoinInfo("viewing", "id", "Name");
        }
        
        // Format Type - table-specific
        if (columnLower.contains("format_type") || fieldLower.contains("format")) {
            if (tableName.equalsIgnoreCase("glossary")) {
                return new LookupJoinInfo("glossary_format_type", "ID", "Name");
            } else {
                return new LookupJoinInfo("format_type", "ID", "PrimaryName");
            }
        }
        
        // KDE - table-specific
        if (columnLower.contains("kde") || fieldLower.equals("kde")) {
            if (tableName.equalsIgnoreCase("glossary")) {
                return new LookupJoinInfo("glossary_kde_type", "ID", "Name");
            } else {
                return new LookupJoinInfo("kde", "ID", "PrimaryName");
            }
        }
        
        // Internal (boolean-like, but might be in a lookup table)
        if (columnLower.equals("internal")) {
            // Usually a boolean, but check if it's a lookup
            // For now, return null to use the raw value
        }
        
        // No lookup table found
        return null;
    }
    
    /**
     * Get lookup join info for a table column (for table display)
     * Similar to getLookupJoinInfo but handles more foreign key cases
     */
    private LookupJoinInfo getLookupJoinInfoForTableColumn(String dbColumn, String tableName, String fieldName) {
        // Normalize for comparison
        String columnLower = dbColumn.toLowerCase();
        String fieldLower = fieldName.toLowerCase();
        
        // MasterSource -> system.Name (Short Name)
        if (columnLower.equals("mastersource") || fieldLower.contains("mastersource") || fieldLower.contains("system")) {
            if (tableName.equalsIgnoreCase("dataset")) {
                return new LookupJoinInfo("system", "id", "Name");
            }
        }
        
        // Glossary -> glossary.Name
        if (columnLower.equals("glossary") || fieldLower.contains("glossary")) {
            if (tableName.equalsIgnoreCase("dataset") || tableName.equalsIgnoreCase("attribute")) {
                return new LookupJoinInfo("glossary", "ID", "Name");
            }
        }
        
        // Parent_ID lookups - table-specific
        if (columnLower.equals("parent_id") || columnLower.equals("parentid") || columnLower.equals("parentid") || fieldLower.contains("parent")) {
            if (tableName.equalsIgnoreCase("glossary")) {
                return new LookupJoinInfo("glossary", "ID", "Name");
            } else if (tableName.equalsIgnoreCase("client")) {
                return new LookupJoinInfo("client", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("legal_entity") || tableName.equalsIgnoreCase("legalentity")) {
                return new LookupJoinInfo("legal_entity", "ID", "ShortName");
            } else if (tableName.equalsIgnoreCase("business_area") || tableName.equalsIgnoreCase("businessarea")) {
                return new LookupJoinInfo("business_area", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("capability")) {
                return new LookupJoinInfo("capability", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("regulation")) {
                return new LookupJoinInfo("regulation", "ID", "primaryName");
            } else if (tableName.equalsIgnoreCase("regulatory_theme") || tableName.equalsIgnoreCase("regulatorytheme")) {
                return new LookupJoinInfo("regulatorytheme", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("committee")) {
                return new LookupJoinInfo("committee", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("geography")) {
                if (columnLower.equals("parentid")) {
                    return new LookupJoinInfo("geography", "ID", "PrimaryName");
                }
            } else if (tableName.equalsIgnoreCase("system")) {
                return new LookupJoinInfo("system", "id", "Name");
            } else if (tableName.equalsIgnoreCase("product")) {
                return new LookupJoinInfo("product", "id", "primaryname");
            } else if (tableName.equalsIgnoreCase("policy")) {
                return new LookupJoinInfo("policy", "ID", "PrimaryName");
            } else if (tableName.equalsIgnoreCase("org_unit") || tableName.equalsIgnoreCase("orgunit")) {
                return new LookupJoinInfo("org_unit", "ID", "Name");
            } else if (tableName.equalsIgnoreCase("changerequest") || tableName.equalsIgnoreCase("change_request")) {
                return new LookupJoinInfo("changerequest", "ID", "PrimaryName");
            }
        }
        
        // Change Request specific lookups
        if (tableName.equalsIgnoreCase("changerequest") || tableName.equalsIgnoreCase("change_request")) {
            if (columnLower.equals("cr_typeid") || columnLower.equals("cr_type_id") || fieldLower.contains("type")) {
                return new LookupJoinInfo("changerequest_type", "ID", "PrimaryName");
            } else if (columnLower.equals("cr_severityid") || columnLower.equals("cr_severity_id") || fieldLower.contains("severity")) {
                return new LookupJoinInfo("changerequest_severity", "ID", "PrimaryName");
            } else if (columnLower.equals("cr_urgencyid") || columnLower.equals("cr_urgency_id") || fieldLower.contains("urgency")) {
                return new LookupJoinInfo("changerequest_urgency", "ID", "PrimaryName");
            } else if (columnLower.equals("estimated_benefitid") || columnLower.equals("estimated_benefit_id") || fieldLower.contains("estimated benefit")) {
                return new LookupJoinInfo("changerequest_value", "ID", "PrimaryName");
            } else if (columnLower.equals("estimated_costid") || columnLower.equals("estimated_cost_id") || fieldLower.contains("estimated cost")) {
                return new LookupJoinInfo("changerequest_value", "ID", "PrimaryName");
            } else if (columnLower.equals("cr_statusid") || columnLower.equals("cr_status_id") || fieldLower.contains("status")) {
                return new LookupJoinInfo("changerequeststatus", "ID", "PrimaryName");
            }
        }
        
        // Dataset_ID -> dataset.PrimaryName
        if (columnLower.equals("dataset_id") || fieldLower.contains("dataset")) {
            if (tableName.equalsIgnoreCase("attribute")) {
                return new LookupJoinInfo("dataset", "ID", "PrimaryName");
            }
        }
        
        // Interface-specific lookups
        // Note: interface_transfer_format uses "Name" column, not "PrimaryName"
        if (tableName.equalsIgnoreCase("interface") || tableName.equalsIgnoreCase("system_interface")) {
            if (columnLower.equals("source_systemid") || columnLower.equals("source_system_id") || fieldLower.contains("source system")) {
                return new LookupJoinInfo("system", "id", "Name");
            } else if (columnLower.equals("target_systemid") || columnLower.equals("target_system_id") || fieldLower.contains("target system")) {
                return new LookupJoinInfo("system", "id", "Name");
            } else if (columnLower.equals("transfer_method_id") || columnLower.equals("transfermethod_id") || fieldLower.contains("transfer method")) {
                // Check if transfer_method table exists - might be interface_transfer_method
                return new LookupJoinInfo("interface_transfer_method", "id", "Name");
            } else if (columnLower.equals("transfer_format_id") || columnLower.equals("transferformat_id") || fieldLower.contains("transfer format")) {
                // interface_transfer_format uses "Name" column
                return new LookupJoinInfo("interface_transfer_format", "id", "Name");
            } else if (columnLower.equals("automation_id") || columnLower.equals("automation") || fieldLower.contains("automation")) {
                return new LookupJoinInfo("interface_automation", "id", "Name");
            } else if (columnLower.equals("frequency_id") || columnLower.equals("frequency") || fieldLower.contains("frequency")) {
                return new LookupJoinInfo("interface_frequency", "id", "Name");
            } else if (columnLower.equals("lifecycle") || columnLower.equals("lifecycle_id") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("system_interface_lifecycle", "id", "PrimaryName");
            } else if (columnLower.equals("status") || columnLower.equals("status_id") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("classification_id") || columnLower.equals("classification") || fieldLower.contains("classification")) {
                return new LookupJoinInfo("interface_classification", "id", "Name");
            }
        }
        
        // Regulation-specific lookups
        if (tableName.equalsIgnoreCase("regulation")) {
            if (columnLower.equals("legaladvicetype_id") || columnLower.equals("legal_advice_type_id") || fieldLower.contains("legal advice type")) {
                return new LookupJoinInfo("legal_advice_type", "id", "PrimaryName");
            } else if (columnLower.equals("compliancelevel_id") || columnLower.equals("compliance_level_id") || fieldLower.contains("compliance level")) {
                return new LookupJoinInfo("compliance_level", "id", "PrimaryName");
            } else if (columnLower.equals("regulationmaturity_id") || columnLower.equals("regulation_maturity_id") || fieldLower.contains("maturity")) {
                return new LookupJoinInfo("regulation_maturity", "id", "PrimaryName");
            } else if (columnLower.equals("regulationprobability_id") || columnLower.equals("regulation_probability_id") || fieldLower.contains("probability")) {
                return new LookupJoinInfo("regulation_probability", "id", "PrimaryName");
            } else if (columnLower.equals("regulationstatus_id") || columnLower.equals("regulation_status_id") || fieldLower.contains("regulation status")) {
                return new LookupJoinInfo("regulation_status", "id", "PrimaryName");
            } else if (columnLower.equals("regulationstage_id") || columnLower.equals("regulation_stage_id") || fieldLower.contains("stage")) {
                return new LookupJoinInfo("regulation_stage", "id", "PrimaryName");
            } else if (columnLower.equals("regulationimpactrating_id") || columnLower.equals("regulation_impact_rating_id") || fieldLower.contains("impact rating")) {
                return new LookupJoinInfo("regulation_impact_rating", "id", "PrimaryName");
            }
        }
        
        // Glossary-specific lookups
        if (tableName.equalsIgnoreCase("glossary")) {
            if (columnLower.equals("format_type") || columnLower.equals("formattype") || fieldLower.contains("format type")) {
                return new LookupJoinInfo("glossary_format_type", "ID", "Name");
            } else if (columnLower.equals("kde") || fieldLower.contains("kde")) {
                return new LookupJoinInfo("glossary_kde_type", "ID", "Name");
            } else if (columnLower.equals("type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("glossary_type", "id", "Name");
            } else if (columnLower.equals("security_classification") || columnLower.equals("securityclassification") || fieldLower.contains("security classification")) {
                return new LookupJoinInfo("security_classification", "ID", "Name");
            } else if (columnLower.equals("lifecycle") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("glossary_lifecycle", "id", "Name");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("is_public") || columnLower.equals("ispublic") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            } else if (columnLower.equals("confidentiality_rating") || fieldLower.contains("confidentiality") || fieldLower.contains("cia rating c")) {
                return new LookupJoinInfo("cia_rating", "id", "Values");
            } else if (columnLower.equals("integrity_rating") || fieldLower.contains("integrity") || fieldLower.contains("cia rating i")) {
                return new LookupJoinInfo("cia_rating", "id", "Values");
            } else if (columnLower.equals("availability_rating") || fieldLower.contains("availability") || fieldLower.contains("cia rating a")) {
                return new LookupJoinInfo("cia_rating", "id", "Values");
            }
        }
        
        // Process-specific lookups
        if (tableName.equalsIgnoreCase("process")) {
            if (columnLower.equals("type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("process_type", "ID", "PrimaryName");
            } else if (columnLower.equals("lifecycle") || columnLower.equals("lifecycle_status") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("process_lifecycle_status", "id", "primaryname");
            } else if (columnLower.equals("classification") || columnLower.equals("processclass_id") || fieldLower.contains("classification")) {
                return new LookupJoinInfo("process_class", "id", "primaryname");
            } else if (columnLower.equals("automation") || columnLower.equals("processautomation_id") || fieldLower.contains("automation")) {
                return new LookupJoinInfo("process_automation", "id", "primaryname");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            }
        }
        
        // Project-specific lookups
        if (tableName.equalsIgnoreCase("project")) {
            if (columnLower.equals("type") || columnLower.equals("project_type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("project_type", "id", "primaryname");
            } else if (columnLower.equals("lifecycle") || columnLower.equals("lifecycle_status") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("project_lifecycle", "id", "primaryname");
            } else if (columnLower.equals("classification") || fieldLower.contains("classification")) {
                return new LookupJoinInfo("project_classification", "id", "primaryname");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            }
        }
        
        // Product-specific lookups
        // Note: product_type table may not exist - check schema
        if (tableName.equalsIgnoreCase("product")) {
            if (columnLower.equals("type") || fieldLower.contains("type")) {
                // Product Type might be in a lookup table or might be a direct field
                // Return null to use raw value if table doesn't exist
                return null;
            } else if (columnLower.equals("lifecycle") || columnLower.equals("lifecycle_status") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("product_lifecycle", "id", "primaryname");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("is_public") || columnLower.equals("ispublic") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Policy-specific lookups
        if (tableName.equalsIgnoreCase("policy")) {
            if (columnLower.equals("type") || columnLower.equals("policy_type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("policy_type", "ID", "PrimaryName");
            } else if (columnLower.equals("lifecycle") || columnLower.equals("lifecycle_status") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("policy_lifecycle_status", "id", "PrimaryName");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("is_public") || columnLower.equals("ispublic") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Committee-specific lookups
        if (tableName.equalsIgnoreCase("committee")) {
            if (columnLower.equals("type") || columnLower.equals("committee_type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("committee_type", "ID", "PrimaryName");
            } else if (columnLower.equals("lifecycle") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("committee_lifecycle", "ID", "PrimaryName");
            } else if (columnLower.equals("classification") || fieldLower.contains("classification")) {
                return new LookupJoinInfo("committee_classification", "ID", "PrimaryName");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("is_public") || columnLower.equals("ispublic") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Capability-specific lookups
        if (tableName.equalsIgnoreCase("capability")) {
            if (columnLower.equals("type") || columnLower.equals("capability_type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("capability_type", "ID", "PrimaryName");
            } else if (columnLower.equals("lifecycle") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("capability_lifecycle", "id", "PrimaryName");
            } else if (columnLower.equals("classification") || fieldLower.contains("classification")) {
                return new LookupJoinInfo("capability_classification", "ID", "PrimaryName");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("is_public") || columnLower.equals("ispublic") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Client-specific lookups
        if (tableName.equalsIgnoreCase("client")) {
            if (columnLower.equals("type") || columnLower.equals("client_type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("client_type", "id", "PrimaryName");
            } else if (columnLower.equals("lifecycle") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("client_lifecycle", "ID", "PrimaryName");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("ispublic") || columnLower.equals("is_public") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Business Area-specific lookups
        if (tableName.equalsIgnoreCase("business_area") || tableName.equalsIgnoreCase("businessarea")) {
            if (columnLower.equals("type") || columnLower.equals("business_area_type") || fieldLower.contains("type")) {
                return new LookupJoinInfo("business_area_type", "id", "PrimaryName");
            } else if (columnLower.equals("lifecycle") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("business_area_lifecycle", "ID", "PrimaryName");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("is_public") || columnLower.equals("ispublic") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Legal Entity-specific lookups
        // Note: legal table (not legal_entity) does not have a Lifecycle column in schema.sql
        if (tableName.equalsIgnoreCase("legal_entity") || tableName.equalsIgnoreCase("legalentity") || tableName.equalsIgnoreCase("legal")) {
            if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("is_public") || columnLower.equals("ispublic") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Org Unit-specific lookups
        if (tableName.equalsIgnoreCase("org_unit") || tableName.equalsIgnoreCase("orgunit")) {
            if (columnLower.equals("status") || columnLower.equals("status_id") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            }
        }
        
        // Regulatory Theme-specific lookups
        // Note: regulatory_theme_status and regulatory_theme_lifecycle may not exist - use status table
        if (tableName.equalsIgnoreCase("regulatory_theme") || tableName.equalsIgnoreCase("regulatorytheme")) {
            if (columnLower.equals("status") || columnLower.equals("status_id") || fieldLower.contains("status")) {
                // Use generic status table if regulatory_theme_status doesn't exist
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("lifecycle") || fieldLower.contains("lifecycle")) {
                // Use generic lifecycle if regulatory_theme_lifecycle doesn't exist
                return new LookupJoinInfo("regulatory_theme_lifecycle", "id", "PrimaryName");
            }
        }
        
        // Dataset-specific lookups
        if (tableName.equalsIgnoreCase("dataset")) {
            if (columnLower.equals("type") || columnLower.equals("datasettype") || fieldLower.contains("type")) {
                return new LookupJoinInfo("dataset_type", "ID", "PrimaryName");
            } else if (columnLower.equals("lifecycle") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("dataset_lifecycle", "ID", "PrimaryName");
            } else if (columnLower.equals("status") || fieldLower.contains("status")) {
                return new LookupJoinInfo("status", "ID", "COALESCE(PrimaryName, primaryname)");
            } else if (columnLower.equals("accesscontroltype") || columnLower.equals("is_public") || fieldLower.contains("viewing")) {
                return new LookupJoinInfo("viewing", "id", "Name");
            }
        }
        
        // Attribute-specific lookups
        if (tableName.equalsIgnoreCase("attribute")) {
            if (columnLower.equals("data_type_id") || columnLower.equals("datatype") || fieldLower.contains("data type")) {
                return new LookupJoinInfo("attribute_datatype", "id", "PrimaryName");
            } else if (columnLower.equals("requirement_id") || fieldLower.contains("requirement") || fieldLower.contains("lifecycle")) {
                return new LookupJoinInfo("requirement", "id", "PrimaryName");
            } else if (columnLower.equals("editability") || fieldLower.contains("editability")) {
                return new LookupJoinInfo("attribute_editability", "id", "PrimaryName");
            } else if (columnLower.equals("editability_role") || columnLower.equals("editabilityrole") || fieldLower.contains("editabilityrole")) {
                return new LookupJoinInfo("attribute_edit_role", "id", "PrimaryName");
            } else if (columnLower.equals("origination") || fieldLower.contains("origin")) {
                return new LookupJoinInfo("attribute_origination", "id", "PrimaryName");
            }
        }
        
        // Use the existing lookup logic for other columns
        return getLookupJoinInfo(dbColumn, tableName, fieldName);
    }
    
    /**
     * Convert an ID field to its corresponding value field name
     * This is used to replace ID columns (like Parent_ID) with value columns (like Parent Name)
     * Returns the field name that should be used to display the value instead of the ID
     */
    private String convertIdFieldToValueField(String fieldName, String tableName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        
        String fieldLower = fieldName.toLowerCase();
        String tableLower = tableName != null ? tableName.toLowerCase() : "";
        
        // Parent_ID -> Parent Name
        if (fieldLower.equals("parent_id") || fieldLower.equals("parentid")) {
            if (tableLower.equals("glossary")) {
                return "Parent Name";
            } else if (tableLower.equals("process") || tableLower.equals("project") || tableLower.equals("product")) {
                return "Parent Name";
            } else if (tableLower.equals("client")) {
                return "Parent Name";
            } else if (tableLower.equals("legal_entity") || tableLower.equals("legalentity") || tableLower.equals("legal")) {
                return "Parent Short Name";
            } else if (tableLower.equals("regulation")) {
                return "Parent";
            }
            return "Parent Name";
        }
        
        // Glossary_ID -> Glossary Name
        if (fieldLower.equals("glossary_id") || fieldLower.equals("glossaryid")) {
            if (tableLower.equals("dataset") || tableLower.equals("attribute")) {
                return "Glossary Name";
            }
            return "Glossary";
        }
        
        // Dataset_ID -> Dataset
        if (fieldLower.equals("dataset_id") || fieldLower.equals("datasetid")) {
            if (tableLower.equals("attribute")) {
                return "Dataset";
            }
            return "Dataset";
        }
        
        // Requirement_ID -> Lifecycle (for Attribute)
        if (fieldLower.equals("requirement_id") || fieldLower.equals("requirementid")) {
            if (tableLower.equals("attribute")) {
                return "Lifecycle";
            }
            return "Requirement";
        }
        
        // MasterSource -> System
        if (fieldLower.equals("mastersource") || fieldLower.equals("master_source")) {
            if (tableLower.equals("dataset")) {
                return "System Short Name";
            }
            return "System";
        }
        
        // OrgUnit_ID -> Org Unit
        if (fieldLower.equals("orgunit_id") || fieldLower.equals("org_unit_id") || fieldLower.equals("orgunitid")) {
            return "Org Unit";
        }
        
        // Profile_ID -> Profile
        if (fieldLower.equals("profile_id") || fieldLower.equals("profileid")) {
            return "Profile";
        }
        
        // If it ends with _ID or ID, try to convert to a readable name
        if (fieldLower.endsWith("_id") || fieldLower.endsWith("id")) {
            // Remove _id or id suffix
            String baseName = fieldLower.replaceAll("_id$", "").replaceAll("id$", "");
            // Convert to Title Case
            String[] words = baseName.split("_");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (i > 0) result.append(" ");
                if (!words[i].isEmpty()) {
                    result.append(words[i].substring(0, 1).toUpperCase());
                    if (words[i].length() > 1) {
                        result.append(words[i].substring(1));
                    }
                }
            }
            return result.toString();
        }
        
        // Return original if no conversion needed
        return fieldName;
    }
    
    @SuppressWarnings("unused")
    private String getNameColumnForTable(String tableName) {
        // Common name columns for different tables
        Map<String, String> nameColumns = new HashMap<>();
        nameColumns.put("dataset", "PrimaryName");
        nameColumns.put("glossary", "Name");
        nameColumns.put("system", "Name");
        nameColumns.put("process", "primaryname");
        nameColumns.put("project", "primaryname");
        nameColumns.put("product", "primaryname");
        nameColumns.put("people", "CONCAT(First_Name, ' ', Last_Name)");
        
        return nameColumns.getOrDefault(tableName.toLowerCase(), "PrimaryName");
    }

    private String getTableNameForFacet(Connection conn, String facetName) throws SQLException {
        // Special case for Attribute facet
        if (facetName.equals("Attribute")) {
            //system.out.println("Special case: Using 'attribute' as table name for Attribute facet");
            return "attribute";
        }
        
        String sql = "SELECT tablename FROM module WHERE primaryname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, facetName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("tablename");
                }
            }
        }
        return null;
    }
    
    /**
     * Initialize UnisonSearchService (lazy initialization)
     */
    private UnisonSearchService getUnisonSearchService() {
        if (unisonSearchService == null) {
            try {
                ConfigurationService configService = new ConfigurationService();
                RelationshipManager relationshipManager = new RelationshipManager();
                QueryBuilder queryBuilder = new QueryBuilder(configService, relationshipManager);
                DatabaseHelper databaseHelper = new DatabaseHelper();
                SearchService searchService = new SearchService(queryBuilder, databaseHelper);
                GraphTraversalService graphService = new GraphTraversalService(relationshipManager, databaseHelper);
                CompoundQueryService compoundService = new CompoundQueryService();
                unisonSearchService = new UnisonSearchService(searchService, graphService, compoundService);
            } catch (Exception e) {
                System.err.println("[NewWidgetServlet] Error initializing UnisonSearchService: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return unisonSearchService;
    }
    
    /**
     * Convert saved search condition_definition to Unison Search format and execute
     * Returns UnisonSearchResponse with results for all facets
     */
    @SuppressWarnings("unchecked")
    private UnisonSearchResponse executeUnisonSearchForWidget(String conditionDefinition, String targetFacetName, Integer userId) throws SQLException, IOException {
        UnisonSearchService service = getUnisonSearchService();
        if (service == null) {
            System.err.println("[NewWidgetServlet] UnisonSearchService not available");
            return null;
        }
        
        try {
            // Parse condition_definition
            Map<String, Object> searchData = objectMapper.readValue(conditionDefinition, Map.class);
            List<Map<String, Object>> searchGroups = (List<Map<String, Object>>) searchData.get("searchGroups");
            
            if (searchGroups == null || searchGroups.isEmpty()) {
                System.out.println("[NewWidgetServlet] No searchGroups found in condition_definition");
                return null;
            }
            
            // Convert searchGroups to Unison Search format
            List<UnisonSearchRequest.SearchItem> searchItems = new ArrayList<>();
            
            for (Map<String, Object> group : searchGroups) {
                Boolean active = (Boolean) group.get("active");
                if (active == null || !active) {
                    continue; // Skip inactive groups
                }
                
                String facetId = (String) group.get("facetId");
                String operator = (String) group.get("operator");
                if (operator == null) {
                    operator = "FIND";
                }
                
                // Handle nested searches array format
                List<Map<String, Object>> searches = (List<Map<String, Object>>) group.get("searches");
                if (searches != null && !searches.isEmpty()) {
                    for (Map<String, Object> search : searches) {
                        Boolean searchActive = (Boolean) search.get("active");
                        if (searchActive == null || !searchActive) {
                            continue;
                        }
                        String searchFacetId = (String) search.get("facetId");
                        String searchOperator = (String) search.get("operator");
                        if (searchOperator == null) {
                            searchOperator = operator;
                        }
                        
                        List<Map<String, Object>> filterGroups = (List<Map<String, Object>>) search.get("filterGroups");
                        if (filterGroups != null && !filterGroups.isEmpty()) {
                            // Build keyword from filterGroups (use first filterGroup's query/value)
                            String keyword = "";
                            
                            for (Map<String, Object> filterGroup : filterGroups) {
                                String query = (String) filterGroup.get("query");
                                Object value = filterGroup.get("value");
                                if (query != null && !query.isEmpty()) {
                                    keyword = query;
                                    break; // Use first non-empty query
                                } else if (value != null && !value.toString().isEmpty()) {
                                    keyword = value.toString();
                                    break; // Use first non-empty value
                                }
                            }
                            
                            // For Unison Search, filters can be empty - keyword is the main search term
                            UnisonSearchRequest.SearchItem item = new UnisonSearchRequest.SearchItem();
                            item.setFacet(searchFacetId);
                            item.setOperator(searchOperator.toUpperCase());
                            item.setKeyword(keyword);
                            item.setFilters(new HashMap<>()); // Empty filters - keyword search handles it
                            searchItems.add(item);
                        }
                    }
                } else {
                    // Handle flat format: filterGroups directly on group
                    List<Map<String, Object>> filterGroups = (List<Map<String, Object>>) group.get("filterGroups");
                    if (filterGroups != null && !filterGroups.isEmpty()) {
                        String keyword = "";
                        
                        for (Map<String, Object> filterGroup : filterGroups) {
                            String query = (String) filterGroup.get("query");
                            Object value = filterGroup.get("value");
                            if (query != null && !query.isEmpty()) {
                                keyword = query;
                                break; // Use first non-empty query
                            } else if (value != null && !value.toString().isEmpty()) {
                                keyword = value.toString();
                                break; // Use first non-empty value
                            }
                        }
                        
                        // For Unison Search, filters can be empty - keyword is the main search term
                        UnisonSearchRequest.SearchItem item = new UnisonSearchRequest.SearchItem();
                        item.setFacet(facetId);
                        item.setOperator(operator.toUpperCase());
                        item.setKeyword(keyword);
                        item.setFilters(new HashMap<>()); // Empty filters - keyword search handles it
                        searchItems.add(item);
                    }
                }
            }
            
            if (searchItems.isEmpty()) {
                System.out.println("[NewWidgetServlet] No active search items found");
                return UnisonSearchResponse.error("No active search items found");
            }
            
            System.out.println("[NewWidgetServlet] Executing Unison Search with " + searchItems.size() + " search items");
            for (int i = 0; i < searchItems.size(); i++) {
                UnisonSearchRequest.SearchItem item = searchItems.get(i);
                System.out.println("[NewWidgetServlet] Search item " + i + ": facet=" + item.getFacet() + ", operator=" + item.getOperator() + ", keyword=" + item.getKeyword());
            }
            
            // Execute Unison Search with maxDepth=1 (direct neighbors only)
            UnisonSearchResponse response = service.executeUnisonSearch(searchItems, 1, userId);
            
            if (response == null) {
                System.err.println("[NewWidgetServlet] Unison Search returned null response");
                return UnisonSearchResponse.error("Unison Search returned null");
            }
            
            if (!response.isSuccess()) {
                System.err.println("[NewWidgetServlet] Unison Search failed: " + response.getError());
                return response;
            }
            
            // Return the full response with all facet results
            Map<String, FacetResult> results = response.getResults();
            if (results == null || results.isEmpty()) {
                System.out.println("[NewWidgetServlet] WARNING: No results from Unison Search (results is null or empty)");
                return response; // Return response even if empty
            }
            
            System.out.println("[NewWidgetServlet] Unison Search returned results for " + results.size() + " facets: " + results.keySet());
            for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
                FacetResult fr = entry.getValue();
                int count = fr != null ? fr.getCount() : 0;
                int idCount = (fr != null && fr.getIds() != null) ? fr.getIds().size() : 0;
                System.out.println("[NewWidgetServlet]   - " + entry.getKey() + ": count=" + count + ", ids=" + idCount);
            }
            return response;
            
        } catch (Exception e) {
            System.err.println("[NewWidgetServlet] Error in executeUnisonSearchForWidget: " + e.getMessage());
            e.printStackTrace();
            throw new SQLException("Failed to execute Unison Search: " + e.getMessage(), e);
        }
    }
    
    /**
     * Normalize facet name for Unison Search (convert to facet ID format)
     */
    private String normalizeFacetNameForUnison(String facetName) {
        // Map common facet names to their IDs
        Map<String, String> mapping = new HashMap<>();
        mapping.put("Dataset", "DATASET");
        mapping.put("Attribute", "ATTRIBUTE");
        mapping.put("System", "SYSTEM");
        mapping.put("Glossary", "GLOSSARY");
        mapping.put("People", "PEOPLE");
        mapping.put("Process", "PROCESS");
        mapping.put("Product", "PRODUCT");
        mapping.put("Policy", "POLICY");
        mapping.put("Legal Entity", "LEGAL_ENTITY");
        mapping.put("Business Area", "BUSINESS_AREA");
        mapping.put("Capability", "CAPABILITY");
        mapping.put("Committee", "COMMITTEE");
        mapping.put("Client", "CLIENT");
        mapping.put("Geography", "GEOGRAPHY");
        mapping.put("Regulation", "REGULATION");
        mapping.put("Regulator", "REGULATOR");
        mapping.put("Regulatory Theme", "REGULATORY_THEME");
        
        return mapping.getOrDefault(facetName, facetName.toUpperCase().replace(" ", "_"));
    }
    
    /**
     * Get the soft delete column name for a given table name
     */
    private String getSoftDeleteColumn(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return null;
        }
        
        String table = tableName.trim().toLowerCase();
        switch (table) {
            case "dataset":
                return "DeletedDatetime";
            case "attribute":
                return "DeletedDatetime";
            case "system":
                return "Deleted_datetime";
            case "glossary":
                return "Deleted_datetime";
            case "people":
                return "Deleted_date";
            case "interface":
                return "deleted_datetime";
            case "orgunit":
            case "org_unit":
                return "deleted_Date";
            case "process":
                return "DeletedDatetime";
            case "project":
                return "deletedatetime";
            case "product":
                return "DeletedDatetime";
            case "policy":
                return "DeletedDatetime";
            case "legal-entity":
            case "legalentity":
            case "legal":
                return "DeleteDatetime";
            case "business-area":
            case "business_area":
                return "deletedatetime";
            case "capability":
                return "DeletedDatetime";
            case "committee":
                return "DeleteDatetime";
            case "geography":
                return "DeletedDatetime";
            case "regulation":
                return "DeletedDatetime";
            case "regulator":
                return "DeletedDatetime";
            case "regulatory-theme":
            case "regulatorytheme":
                return "DeletedDatetime";
            default:
                return null; // No known soft delete column for this table
        }
    }
}

