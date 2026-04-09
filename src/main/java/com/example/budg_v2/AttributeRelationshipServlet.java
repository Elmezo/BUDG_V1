package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for handling Dataset Attribute Relationships
 * Endpoints:
 * - GET /api/dataset-relationships/{datasetId} - Get inbound and outbound relationships
 * - GET /api/dataset-relationships/lookups - Get lookup data for dropdowns
 * - GET /api/dataset-relationships/cascading/{datasetId} - Get cascading dropdown data
 * - POST /api/dataset-relationships/save - Save/update/delete relationships
 */
@WebServlet({"/api/dataset-relationships/*"})
public class AttributeRelationshipServlet extends HttpServlet {
    private static final int DATASET_FACET_TYPE = 11;
    private final Gson gson = new Gson();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(resp, "Invalid endpoint", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            
            if ("lookups".equals(parts[0])) {
                handleGetLookups(resp);
            } else if ("cascading".equals(parts[0]) && parts.length > 1) {
                int datasetId = Integer.parseInt(parts[1]);
                handleGetCascadingData(datasetId, resp);
            } else if ("system-data".equals(parts[0]) && parts.length > 1) {
                int systemId = Integer.parseInt(parts[1]);
                handleGetSystemData(systemId, resp);
            } else {
                // Get relationships for dataset
                int datasetId = Integer.parseInt(parts[0]);
                String view = req.getParameter("view");
                
                System.out.println("[AttributeRelationshipServlet] Loading relationships for dataset " + datasetId + 
                    " with view=" + view);
                
                if (view != null && "changes".equals(view.trim())) {
                    // View Changes: show original relationships + any pending new relationships from active EDIT CR
                    // This mirrors the approach used for attributes (AttributeServlet.getAttributesWithPendingChanges)
                    handleGetRelationshipsWithPendingChanges(datasetId, resp, req);
                } else {
                    // View Original: show only original relationships (exclude pending ones added during active CR)
                    // This mirrors the approach used for attributes (AttributeServlet.getOriginalAttributes)
                    handleGetOriginalRelationships(datasetId, resp, req);
                }
            }
        } catch (NumberFormatException e) {
            sendError(resp, "Invalid dataset ID", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(resp, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, "Server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        
        try {
            if (pathInfo != null && pathInfo.contains("/save")) {
                handleSaveRelationships(req, resp);
            } else {
                sendError(resp, "Invalid endpoint", 400);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, "Server error: " + e.getMessage(), 500);
        }
    }

    /**
     * Get original relationships for View Original mode.
     * Shows all relationships that belong to the original dataset, EXCLUDING any pending
     * new relationships added during the active EDIT CR.
     * 
     * This mirrors AttributeServlet.getOriginalAttributes logic:
     * - Relationships from completed CREATE CR are already saved with original attribute IDs
     * - Newly added relationships during active EDIT CR are tracked via dataset_changes mappings
     *   with area_key = "relationships#attribute_x_attribute"
     * - We exclude those pending relationship IDs from the results
     */
    private void handleGetOriginalRelationships(int datasetId, HttpServletResponse resp,
                                                HttpServletRequest req) throws SQLException, IOException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            JsonObject result = new JsonObject();
            
            // Get all relationships for the original dataset
            List<Map<String, Object>> inbound = getInboundRelationships(conn, datasetId);
            List<Map<String, Object>> outbound = getOutboundRelationships(conn, datasetId);
            
            // Check for active auto CR - if present, exclude pending new relationships
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_TYPE, datasetId);
            if (activeCrId != null) {
                String areaKey = "relationships#attribute_x_attribute";
                List<Integer> pendingIds = facetChangesDAO.getAllNObjectIds("dataset", datasetId, areaKey, activeCrId);
                java.util.Set<Integer> pendingIdSet = new java.util.HashSet<>(pendingIds);
                
                if (!pendingIdSet.isEmpty()) {
                    System.out.println("[AttributeRelationshipServlet] View Original: excluding " + pendingIdSet.size() + " pending relationship IDs for CR " + activeCrId);
                    inbound = filterOutPendingRelationships(inbound, pendingIdSet);
                    outbound = filterOutPendingRelationships(outbound, pendingIdSet);
                }
            }
            
            // V-05: hide related datasets the user cannot access
            inbound = RelationshipAccessUtil.filterBySegmentAccess(inbound, req, "Dataset", "targetDatasetId");
            outbound = RelationshipAccessUtil.filterBySegmentAccess(outbound, req, "Dataset", "sourceDatasetId");
            
            result.add("inbound", gson.toJsonTree(inbound));
            result.addProperty("inboundHiddenCount", RelationshipAccessUtil.countHidden(inbound));
            result.add("outbound", gson.toJsonTree(outbound));
            result.addProperty("outboundHiddenCount", RelationshipAccessUtil.countHidden(outbound));
            
            resp.getWriter().write(gson.toJson(result));
        }
    }
    
    /**
     * Get relationships with pending changes for View Changes mode.
     * Shows original relationships + any pending new relationships from the active EDIT CR.
     * 
     * This mirrors AttributeServlet.getAttributesWithPendingChanges logic:
     * - Start with all relationships for the original dataset
     * - Add any pending new relationships tracked via dataset_changes mappings
     *   with area_key = "relationships#attribute_x_attribute"
     * - Pending relationships are marked with "pending": true
     */
    private void handleGetRelationshipsWithPendingChanges(int datasetId, HttpServletResponse resp,
                                                          HttpServletRequest req) throws SQLException, IOException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            JsonObject result = new JsonObject();
            
            // Get all relationships for the original dataset
            List<Map<String, Object>> inbound = getInboundRelationships(conn, datasetId);
            List<Map<String, Object>> outbound = getOutboundRelationships(conn, datasetId);
            
            // Check for active auto CR - if present, add pending new relationships
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_TYPE, datasetId);
            if (activeCrId != null) {
                String areaKey = "relationships#attribute_x_attribute";
                List<Integer> pendingIds = facetChangesDAO.getAllNObjectIds("dataset", datasetId, areaKey, activeCrId);
                
                if (!pendingIds.isEmpty()) {
                    // Collect IDs of original relationships (to avoid duplicates)
                    java.util.Set<Integer> originalInboundIds = collectRelationshipIds(inbound);
                    java.util.Set<Integer> originalOutboundIds = collectRelationshipIds(outbound);
                    
                    System.out.println("[AttributeRelationshipServlet] View Changes: adding " + pendingIds.size() + " pending relationships for CR " + activeCrId);
                    
                    for (Integer pendingRelId : pendingIds) {
                        // Skip if already in original results (shouldn't happen, but safety check)
                        if (originalInboundIds.contains(pendingRelId) || originalOutboundIds.contains(pendingRelId)) {
                            continue;
                        }
                        
                        // Load the pending relationship by ID
                        Map<String, Object> pendingRel = getRelationshipById(conn, pendingRelId);
                        if (pendingRel != null) {
                            pendingRel.put("pending", true);
                            
                            // Determine if this is inbound or outbound based on source attribute's dataset
                            Object sourceAttrId = pendingRel.get("Source_AttributeID");
                            if (sourceAttrId != null && attributeBelongsToDataset(conn, sourceAttrId, datasetId)) {
                                inbound.add(pendingRel);
                            } else {
                                outbound.add(pendingRel);
                            }
                        }
                    }
                }
            }
            
            // V-05: hide related datasets the user cannot access
            inbound = RelationshipAccessUtil.filterBySegmentAccess(inbound, req, "Dataset", "targetDatasetId");
            outbound = RelationshipAccessUtil.filterBySegmentAccess(outbound, req, "Dataset", "sourceDatasetId");
            
            result.add("inbound", gson.toJsonTree(inbound));
            result.addProperty("inboundHiddenCount", RelationshipAccessUtil.countHidden(inbound));
            result.add("outbound", gson.toJsonTree(outbound));
            result.addProperty("outboundHiddenCount", RelationshipAccessUtil.countHidden(outbound));
            
            resp.getWriter().write(gson.toJson(result));
        }
    }
    
    /**
     * Filter out relationships whose IDs are in the pending set
     */
    private List<Map<String, Object>> filterOutPendingRelationships(List<Map<String, Object>> relationships, 
                                                                     java.util.Set<Integer> pendingIdSet) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> rel : relationships) {
            Object idObj = rel.get("ID");
            if (idObj == null) idObj = rel.get("id");
            if (idObj != null) {
                int relId = ((Number) idObj).intValue();
                if (!pendingIdSet.contains(relId)) {
                    filtered.add(rel);
                }
            } else {
                filtered.add(rel);
            }
        }
        return filtered;
    }
    
    /**
     * Collect relationship IDs from a list of relationship maps
     */
    private java.util.Set<Integer> collectRelationshipIds(List<Map<String, Object>> relationships) {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (Map<String, Object> rel : relationships) {
            Object idObj = rel.get("ID");
            if (idObj == null) idObj = rel.get("id");
            if (idObj != null) {
                ids.add(((Number) idObj).intValue());
            }
        }
        return ids;
    }

    /**
     * Get lookup data for dropdowns
     */
    private void handleGetLookups(HttpServletResponse resp) throws SQLException, IOException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            JsonObject result = new JsonObject();
            
            // Get relation types
            String typeSql = "SELECT ID, PrimaryName, Description FROM attribute_x_attribute_relationtype ORDER BY PrimaryName";
            List<Map<String, Object>> relationTypes = executeQuery(conn, typeSql);
            result.add("relationTypes", gson.toJsonTree(relationTypes));
            
            // Get relation scopes
            String scopeSql = "SELECT ID, PrimaryName, Description FROM attribute_x_attribute_relationscope ORDER BY PrimaryName";
            List<Map<String, Object>> relationScopes = executeQuery(conn, scopeSql);
            result.add("relationScopes", gson.toJsonTree(relationScopes));
            
            resp.getWriter().write(gson.toJson(result));
        }
    }

    /**
     * Get cascading dropdown data for edit mode
     */
    private void handleGetCascadingData(int datasetId, HttpServletResponse resp) throws SQLException, IOException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            JsonObject result = new JsonObject();
            
            // Get all systems for dropdown
            String allSystemsSql = "SELECT id, Name FROM system ORDER BY Name";
            List<Map<String, Object>> allSystems = executeQuery(conn, allSystemsSql);
            result.add("systems", gson.toJsonTree(allSystems));
            
            // Get dataset's MasterSource system ID for default selection
            String datasetSystemSql = "SELECT MasterSource FROM dataset WHERE ID = ?";
            Integer defaultSystemId = null;
            try (PreparedStatement ps = conn.prepareStatement(datasetSystemSql)) {
                ps.setInt(1, datasetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        defaultSystemId = (Integer) rs.getObject("MasterSource");
                    }
                }
            }
            result.addProperty("defaultSystemId", defaultSystemId);
            
            // Get attributes for this dataset
            String attrSql = "SELECT ID, PrimaryName, RefNumber FROM attribute " +
                           "WHERE Dataset_ID = ? AND DeletedDatetime IS NULL " +
                           "ORDER BY PrimaryName";
            List<Map<String, Object>> attributes = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(attrSql)) {
                ps.setInt(1, datasetId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> attr = new HashMap<>();
                        attr.put("id", rs.getInt("ID"));
                        attr.put("name", rs.getString("PrimaryName"));
                        attr.put("refNumber", rs.getString("RefNumber"));
                        attributes.add(attr);
                    }
                }
            }
            result.add("attributes", gson.toJsonTree(attributes));
            
            // Get interfaces and datasets for default system (if exists)
            if (defaultSystemId != null) {
                // Get interfaces for default system
                String interfaceSql = "SELECT id, Name, Ref_number FROM interface " +
                                    "WHERE Source_systemID = ? AND deleted_datetime IS NULL " +
                                    "ORDER BY Name";
                List<Map<String, Object>> interfaces = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(interfaceSql)) {
                    ps.setInt(1, defaultSystemId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> iface = new HashMap<>();
                            iface.put("id", rs.getInt("id"));
                            iface.put("name", rs.getString("Name"));
                            iface.put("refNumber", rs.getString("Ref_number"));
                            interfaces.add(iface);
                        }
                    }
                }
                result.add("interfaces", gson.toJsonTree(interfaces));
                
                // Get datasets for default system
                String datasetSql = "SELECT ID, PrimaryName, RefNumber FROM dataset " +
                                  "WHERE MasterSource = ? AND DeletedDatetime IS NULL " +
                                  "ORDER BY PrimaryName";
                List<Map<String, Object>> datasets = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(datasetSql)) {
                    ps.setInt(1, defaultSystemId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> ds = new HashMap<>();
                            ds.put("id", rs.getInt("ID"));
                            ds.put("name", rs.getString("PrimaryName"));
                            ds.put("refNumber", rs.getString("RefNumber"));
                            datasets.add(ds);
                        }
                    }
                }
                result.add("datasets", gson.toJsonTree(datasets));
            } else {
                result.add("interfaces", new JsonArray());
                result.add("datasets", new JsonArray());
            }
            
            resp.getWriter().write(gson.toJson(result));
        }
    }

    /**
     * Get interfaces and datasets for a specific system
     */
    private void handleGetSystemData(int systemId, HttpServletResponse resp) throws SQLException, IOException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            JsonObject result = new JsonObject();
            
            // Get interfaces for this system
            String interfaceSql = "SELECT id, Name, Ref_number FROM interface " +
                                "WHERE Source_systemID = ? AND deleted_datetime IS NULL " +
                                "ORDER BY Name";
            List<Map<String, Object>> interfaces = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(interfaceSql)) {
                ps.setInt(1, systemId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> iface = new HashMap<>();
                        iface.put("id", rs.getInt("id"));
                        iface.put("name", rs.getString("Name"));
                        iface.put("refNumber", rs.getString("Ref_number"));
                        interfaces.add(iface);
                    }
                }
            }
            result.add("interfaces", gson.toJsonTree(interfaces));
            
            // Get datasets for this system
            String datasetSql = "SELECT ID, PrimaryName, RefNumber FROM dataset " +
                              "WHERE MasterSource = ? AND DeletedDatetime IS NULL " +
                              "ORDER BY PrimaryName";
            List<Map<String, Object>> datasets = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(datasetSql)) {
                ps.setInt(1, systemId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> ds = new HashMap<>();
                        ds.put("id", rs.getInt("ID"));
                        ds.put("name", rs.getString("PrimaryName"));
                        ds.put("refNumber", rs.getString("RefNumber"));
                        datasets.add(ds);
                    }
                }
            }
            result.add("datasets", gson.toJsonTree(datasets));
            
            resp.getWriter().write(gson.toJson(result));
        }
    }


    /**
     * Save/update/delete relationships
     * 
     * Auto CR approach (consistent with Attributes tab):
     * - Relationships are ALWAYS saved with ORIGINAL attribute IDs (no cloning)
     * - When auto CR is active: save a mapping in dataset_changes for each new relationship
     *   with area_key = "relationships#attribute_x_attribute" and nobject_id = new relationship ID
     * - This allows View Original to exclude pending relationships and View Changes to include them
     * 
     * On CR completion (applyRelationshipChanges): attribute_x_attribute is a special case that
     * returns early with "no action needed" because relationships already use original attribute IDs.
     * 
     * On CR discard (deleteRelationshipRecordById): the pending relationship records are deleted
     * from attribute_x_attribute table using the nobject_id (relationship ID) from the mapping.
     */
    private void handleSaveRelationships(HttpServletRequest req, HttpServletResponse resp) throws IOException, SQLException {
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        
        JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();
        JsonArray operations = requestData.getAsJsonArray("operations");
        Integer datasetId = requestData.has("datasetId") && !requestData.get("datasetId").isJsonNull() 
                           ? requestData.get("datasetId").getAsInt() : null;
        
        System.out.println("[AttributeRelationshipServlet] Saving relationships - datasetId: " + datasetId + ", operations count: " + (operations != null ? operations.size() : 0));
        
        // Check for active auto CR (no need for cloned dataset - we save with original attribute IDs)
        Integer activeCrId = null;
        
        if (datasetId != null) {
            try {
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_TYPE, datasetId);
                if (activeCrId != null) {
                    System.out.println("[AttributeRelationshipServlet] Active auto CR found: " + activeCrId + " for dataset " + datasetId);
                }
            } catch (SQLException e) {
                System.err.println("[AttributeRelationshipServlet] Error checking for active CR: " + e.getMessage());
            }
        }
        
        if (operations == null) {
            operations = new JsonArray();
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                List<Integer> insertedIds = new ArrayList<>();
                
                for (int i = 0; i < operations.size(); i++) {
                    JsonObject op = operations.get(i).getAsJsonObject();
                    String operation = op.get("operation").getAsString();
                    JsonObject data = op.get("data").getAsJsonObject();
                    
                    // No attribute ID mapping needed - save with original attribute IDs
                    // The view logic (handleGetOriginalRelationships / handleGetRelationshipsWithPendingChanges)
                    // uses dataset_changes mappings to determine which relationships are pending
                    
                    switch (operation) {
                        case "INSERT":
                            SegmentValidationService.ValidationResult insertValidation =
                                    validateAttributeRelationshipSegments(conn, data);
                            if (!insertValidation.isValid) {
                                conn.rollback();
                                sendError(resp, insertValidation.message, 400);
                                return;
                            }
                            int newId = insertRelationship(conn, data);
                            if (newId > 0) {
                                insertedIds.add(newId);
                            } else {
                                System.err.println("Warning: Failed to get generated ID for new relationship");
                            }
                            break;
                        case "UPDATE":
                            SegmentValidationService.ValidationResult updateValidation =
                                    validateAttributeRelationshipSegments(conn, data);
                            if (!updateValidation.isValid) {
                                conn.rollback();
                                sendError(resp, updateValidation.message, 400);
                                return;
                            }
                            updateRelationship(conn, data);
                            break;
                        case "DELETE":
                            deleteRelationship(conn, data);
                            break;
                    }
                }
                
                conn.commit();
                
                // Save mappings for newly inserted relationships when auto CR is active
                // This allows View Original to exclude them and View Changes to include them
                if (activeCrId != null && datasetId != null && !insertedIds.isEmpty()) {
                    String areaKey = "relationships#attribute_x_attribute";
                    for (Integer newRelId : insertedIds) {
                        try {
                            facetChangesDAO.saveMapping("dataset", datasetId, newRelId, areaKey, activeCrId);
                            System.out.println("[AttributeRelationshipServlet] Saved mapping for new relationship " + newRelId + " in CR " + activeCrId);
                        } catch (SQLException e) {
                            System.err.println("[AttributeRelationshipServlet] Error saving mapping for relationship " + newRelId + ": " + e.getMessage());
                        }
                    }
                }
                
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "Relationships saved successfully");
                resp.getWriter().write(gson.toJson(result));
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private SegmentValidationService.ValidationResult validateAttributeRelationshipSegments(Connection conn, JsonObject data) throws SQLException {
        Integer sourceAttributeId = getIntOrNull(data, "sourceAttributeId");
        Integer targetAttributeId = getIntOrNull(data, "targetAttributeId");
        if (sourceAttributeId == null || targetAttributeId == null
                || sourceAttributeId <= 0 || targetAttributeId <= 0) {
            return SegmentValidationService.ValidationResult.error(
                    "Missing required attribute IDs for relationship validation");
        }

        return segmentValidationService.validateCrossSegmentRelationship(
                sourceAttributeId, "Attribute",
                targetAttributeId, "Attribute",
                conn);
    }

    /**
     * Get inbound relationships
     * For inbound relationships: source attribute (sa) is in the current dataset, target attribute (ta) is in another dataset
     * The "Source System" should be the system of the TARGET dataset (td), because that's the system related to the "Related Data Set" (targetDataset)
     */
    private List<Map<String, Object>> getInboundRelationships(Connection conn, int datasetId) throws SQLException {
        String sql = "SELECT " +
                   "    axa.ID, " +
                   "    axa.Source_AttributeID, " +
                   "    axa.Target_AttributeID, " +
                   "    axa.Relation_Type, " +
                   "    axa.Relation_Scope, " +
                   "    axa.Relation_Method, " +
                   "    axa.Sourcing_Logic, " +
                   "    axa.Review_Status, " +
                   "    sa.PrimaryName as sourceAttributeName, " +
                   "    sa.RefNumber as sourceAttributeRef, " +
                   "    ta.PrimaryName as targetAttributeName, " +
                   "    ta.RefNumber as targetAttributeRef, " +
                   "    td.ID as targetDatasetId, " +
                   "    td.PrimaryName as targetDatasetName, " +
                   "    td.RefNumber as targetDatasetRef, " +
                   "    s.id as systemId, " +
                   "    s.Name as systemName, " +
                   "    i.id as interfaceId, " +
                   "    i.Name as interfaceName, " +
                   "    i.Ref_number as interfaceRef, " +
                   "    rt.PrimaryName as relationType, " +
                   "    rs.PrimaryName as relationScope " +
                   "FROM attribute_x_attribute axa " +
                   "INNER JOIN attribute sa ON sa.ID = axa.Source_AttributeID " +
                   "INNER JOIN attribute ta ON ta.ID = axa.Target_AttributeID " +
                   "INNER JOIN dataset sd ON sd.ID = sa.Dataset_ID " +
                   "INNER JOIN dataset td ON td.ID = ta.Dataset_ID " +
                   "LEFT JOIN system s ON s.id = td.MasterSource " +
                   "LEFT JOIN interface i ON i.id = axa.Relation_Method " +
                   "LEFT JOIN attribute_x_attribute_relationtype rt ON rt.ID = axa.Relation_Type " +
                   "LEFT JOIN attribute_x_attribute_relationscope rs ON rs.ID = axa.Relation_Scope " +
                   "WHERE sa.Dataset_ID = ? " +
                   "ORDER BY sa.PrimaryName, ta.PrimaryName";
        
        return executeQueryWithParams(conn, sql, datasetId);
    }

    /**
     * Get outbound relationships
     */
    private List<Map<String, Object>> getOutboundRelationships(Connection conn, int datasetId) throws SQLException {
        String sql = "SELECT " +
                   "    axa.ID, " +
                   "    axa.Source_AttributeID, " +
                   "    axa.Target_AttributeID, " +
                   "    axa.Relation_Type, " +
                   "    axa.Relation_Scope, " +
                   "    axa.Relation_Method, " +
                   "    axa.Sourcing_Logic, " +
                   "    axa.Review_Status, " +
                   "    sa.PrimaryName as sourceAttributeName, " +
                   "    sa.RefNumber as sourceAttributeRef, " +
                   "    sd.ID as sourceDatasetId, " +
                   "    sd.PrimaryName as sourceDatasetName, " +
                   "    sd.RefNumber as sourceDatasetRef, " +
                   "    ta.PrimaryName as targetAttributeName, " +
                   "    ta.RefNumber as targetAttributeRef, " +
                   "    ss.id as systemId, " +
                   "    ss.Name as systemName, " +
                   "    i.id as interfaceId, " +
                   "    i.Name as interfaceName, " +
                   "    i.Ref_number as interfaceRef, " +
                   "    rt.PrimaryName as relationType, " +
                   "    rs.PrimaryName as relationScope " +
                   "FROM attribute_x_attribute axa " +
                   "INNER JOIN attribute sa ON sa.ID = axa.Source_AttributeID " +
                   "INNER JOIN attribute ta ON ta.ID = axa.Target_AttributeID " +
                   "INNER JOIN dataset sd ON sd.ID = sa.Dataset_ID " +
                   "INNER JOIN dataset td ON td.ID = ta.Dataset_ID " +
                   "LEFT JOIN system ss ON ss.id = sd.MasterSource " +
                   "LEFT JOIN interface i ON i.id = axa.Relation_Method " +
                   "LEFT JOIN attribute_x_attribute_relationtype rt ON rt.ID = axa.Relation_Type " +
                   "LEFT JOIN attribute_x_attribute_relationscope rs ON rs.ID = axa.Relation_Scope " +
                   "WHERE ta.Dataset_ID = ? " +
                   "ORDER BY ta.PrimaryName, sa.PrimaryName";
        
        return executeQueryWithParams(conn, sql, datasetId);
    }

    /**
     * Insert a new relationship and return the generated ID
     */
    private int insertRelationship(Connection conn, JsonObject data) throws SQLException {
        String sql = "INSERT INTO attribute_x_attribute " +
                   "(Relation_Type, Source_AttributeID, Target_AttributeID, Relation_Scope, Relation_Method, " +
                   "Sourcing_Logic, Review_Status, " +
                   "CreateDatetime, LastUpdateDatetime, Last_UpdateUserID) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?)";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, getIntOrNull(data, "relationTypeId"));
            ps.setObject(2, getIntOrNull(data, "sourceAttributeId"));
            ps.setObject(3, getIntOrNull(data, "targetAttributeId"));
            ps.setObject(4, getIntOrNull(data, "relationScopeId"));
            ps.setObject(5, getIntOrNull(data, "interfaceId"));
            ps.setString(6, getStringOrNull(data, "sourcingLogic"));
            ps.setString(7, getStringOrNull(data, "reviewStatus"));
            ps.setObject(8, getIntOrNull(data, "userId"));
            ps.executeUpdate();
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Update an existing relationship
     */
    private void updateRelationship(Connection conn, JsonObject data) throws SQLException {
        String sql = "UPDATE attribute_x_attribute SET " +
                   "Relation_Type = ?, " +
                   "Source_AttributeID = ?, " +
                   "Target_AttributeID = ?, " +
                   "Relation_Scope = ?, " +
                   "Relation_Method = ?, " +
                   "Sourcing_Logic = ?, " +
                   "Review_Status = ?, " +
                   "LastUpdateDatetime = NOW(), " +
                   "Last_UpdateUserID = ? " +
                   "WHERE ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, getIntOrNull(data, "relationTypeId"));
            ps.setObject(2, getIntOrNull(data, "sourceAttributeId"));
            ps.setObject(3, getIntOrNull(data, "targetAttributeId"));
            ps.setObject(4, getIntOrNull(data, "relationScopeId"));
            ps.setObject(5, getIntOrNull(data, "interfaceId"));
            ps.setString(6, getStringOrNull(data, "sourcingLogic"));
            ps.setString(7, getStringOrNull(data, "reviewStatus"));
            ps.setObject(8, getIntOrNull(data, "userId"));
            ps.setInt(9, data.get("id").getAsInt());
            ps.executeUpdate();
        }
    }

    /**
     * Delete a relationship
     * Note: The relationship info is obtained before deletion in handleSaveRelationships
     */
    private void deleteRelationship(Connection conn, JsonObject data) throws SQLException {
        String sql = "DELETE FROM attribute_x_attribute WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, data.get("id").getAsInt());
            ps.executeUpdate();
        }
    }
    
    /**
     * Get relationship info before deletion (for tracking in pending changes)
     */
    @SuppressWarnings("unused")
    private Map<String, Object> getRelationshipInfoBeforeDelete(Connection conn, int relId) throws SQLException {
        String sql = "SELECT axa.ID, axa.Source_AttributeID, axa.Target_AttributeID, axa.Relation_Type, axa.Relation_Scope, " +
                     "sa.PrimaryName as source_name, sa.RefNumber as source_ref, " +
                     "ta.PrimaryName as target_name, ta.RefNumber as target_ref " +
                     "FROM attribute_x_attribute axa " +
                     "LEFT JOIN attribute sa ON axa.Source_AttributeID = sa.ID " +
                     "LEFT JOIN attribute ta ON axa.Target_AttributeID = ta.ID " +
                     "WHERE axa.ID = ?";
        
        Map<String, Object> info = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    info.put("id", rs.getInt("ID"));
                    info.put("source_name", rs.getString("source_name"));
                    info.put("source_ref", rs.getString("source_ref"));
                    info.put("target_name", rs.getString("target_name"));
                    info.put("target_ref", rs.getString("target_ref"));
                }
            }
        }
        return info;
    }

    /**
     * Execute a query and return results as list of maps
     */
    private List<Map<String, Object>> executeQuery(Connection conn, String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Execute a query with parameters
     */
    private List<Map<String, Object>> executeQueryWithParams(Connection conn, String sql, Object... params) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnLabel(i), rs.getObject(i));
                    }
                    // Also add lowercase 'id' for compatibility if 'ID' exists
                    if (row.containsKey("ID") && !row.containsKey("id")) {
                        row.put("id", row.get("ID"));
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Helper to get integer or null from JsonObject
     */
    private Integer getIntOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return null;
    }

    /**
     * Helper to get string or null from JsonObject
     */
    private String getStringOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            String value = obj.get(key).getAsString();
            return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
        }
        return null;
    }

    /**
     * Send error response
     */
    private void sendError(HttpServletResponse resp, String message, int status) throws IOException {
        resp.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        resp.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Get a single attribute relationship by ID with full details (matching getInboundRelationships format)
     */
    private Map<String, Object> getRelationshipById(Connection conn, int relationshipId) throws SQLException {
        String sql = "SELECT " +
                   "    axa.ID, " +
                   "    axa.Source_AttributeID, " +
                   "    axa.Target_AttributeID, " +
                   "    axa.Relation_Type, " +
                   "    axa.Relation_Scope, " +
                   "    axa.Relation_Method, " +
                   "    axa.Sourcing_Logic, " +
                   "    axa.Review_Status, " +
                   "    sa.PrimaryName as sourceAttributeName, " +
                   "    sa.RefNumber as sourceAttributeRef, " +
                   "    ta.PrimaryName as targetAttributeName, " +
                   "    ta.RefNumber as targetAttributeRef, " +
                   "    td.ID as targetDatasetId, " +
                   "    td.PrimaryName as targetDatasetName, " +
                   "    td.RefNumber as targetDatasetRef, " +
                   "    s.id as systemId, " +
                   "    s.Name as systemName, " +
                   "    i.id as interfaceId, " +
                   "    i.Name as interfaceName, " +
                   "    i.Ref_number as interfaceRef, " +
                   "    rt.PrimaryName as relationType, " +
                   "    rs.PrimaryName as relationScope " +
                   "FROM attribute_x_attribute axa " +
                   "INNER JOIN attribute sa ON sa.ID = axa.Source_AttributeID " +
                   "INNER JOIN attribute ta ON ta.ID = axa.Target_AttributeID " +
                   "INNER JOIN dataset sd ON sd.ID = sa.Dataset_ID " +
                   "INNER JOIN dataset td ON td.ID = ta.Dataset_ID " +
                   "LEFT JOIN system s ON s.id = td.MasterSource " +
                   "LEFT JOIN interface i ON i.id = axa.Relation_Method " +
                   "LEFT JOIN attribute_x_attribute_relationtype rt ON rt.ID = axa.Relation_Type " +
                   "LEFT JOIN attribute_x_attribute_relationscope rs ON rs.ID = axa.Relation_Scope " +
                   "WHERE axa.ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationshipId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("ID", rs.getInt("ID"));
                    row.put("Source_AttributeID", rs.getInt("Source_AttributeID"));
                    row.put("Target_AttributeID", rs.getInt("Target_AttributeID"));
                    row.put("Relation_Type", rs.getInt("Relation_Type"));
                    row.put("Relation_Scope", rs.getObject("Relation_Scope") != null ? rs.getInt("Relation_Scope") : null);
                    row.put("Relation_Method", rs.getObject("Relation_Method") != null ? rs.getInt("Relation_Method") : null);
                    row.put("Sourcing_Logic", rs.getString("Sourcing_Logic"));
                    row.put("Review_Status", rs.getString("Review_Status"));
                    row.put("sourceAttributeName", rs.getString("sourceAttributeName"));
                    row.put("sourceAttributeRef", rs.getString("sourceAttributeRef"));
                    row.put("targetAttributeName", rs.getString("targetAttributeName"));
                    row.put("targetAttributeRef", rs.getString("targetAttributeRef"));
                    row.put("targetDatasetId", rs.getObject("targetDatasetId") != null ? rs.getInt("targetDatasetId") : null);
                    row.put("targetDatasetName", rs.getString("targetDatasetName"));
                    row.put("targetDatasetRef", rs.getString("targetDatasetRef"));
                    row.put("systemId", rs.getObject("systemId") != null ? rs.getInt("systemId") : null);
                    row.put("systemName", rs.getString("systemName"));
                    row.put("interfaceId", rs.getObject("interfaceId") != null ? rs.getInt("interfaceId") : null);
                    row.put("interfaceName", rs.getString("interfaceName"));
                    row.put("interfaceRef", rs.getString("interfaceRef"));
                    row.put("relationType", rs.getString("relationType"));
                    row.put("relationScope", rs.getString("relationScope"));
                    // Also add lowercase 'id' for compatibility
                    row.put("id", rs.getInt("ID"));
                    return row;
                }
                return null;
            }
        }
    }
    
    /**
     * Check if an attribute belongs to a specific dataset
     */
    private boolean attributeBelongsToDataset(Connection conn, Object attrIdObj, int datasetId) throws SQLException {
        if (attrIdObj == null) return false;
        int attrId = ((Number) attrIdObj).intValue();
        
        String sql = "SELECT 1 FROM attribute WHERE ID = ? AND Dataset_ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attrId);
            stmt.setInt(2, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}

