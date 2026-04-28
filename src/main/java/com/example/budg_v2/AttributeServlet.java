package com.example.budg_v2;

import com.example.budg_v2.dao.AttributeDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

@WebServlet(name = "AttributeServlet", urlPatterns = {"/api/attribute", "/api/attribute/*"})
public class AttributeServlet extends HttpServlet {

    private static final int DATASET_FACET_TYPE = 11;
    private final AttributeDAO attributeDAO = new AttributeDAO();
    private final SegmentDAO segmentDAO = new SegmentDAO();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            if ("/lookups".equals(path)) {
                // Return all lookups
                JsonObject obj = new JsonObject();
                obj.addProperty("success", true);
                com.google.gson.JsonObject data = new com.google.gson.JsonObject();
                data.add("requirements", com.google.gson.JsonParser.parseString(JsonUtil.toJson(attributeDAO.listRequirements())));
                data.add("glossaries", com.google.gson.JsonParser.parseString(JsonUtil.toJson(attributeDAO.listGlossaries())));
                data.add("originations", com.google.gson.JsonParser.parseString(JsonUtil.toJson(attributeDAO.listOriginations())));
                data.add("editabilities", com.google.gson.JsonParser.parseString(JsonUtil.toJson(attributeDAO.listEditabilities())));
                data.add("editRoles", com.google.gson.JsonParser.parseString(JsonUtil.toJson(attributeDAO.listEditRoles())));
                data.add("dataTypes", com.google.gson.JsonParser.parseString(JsonUtil.toJson(attributeDAO.listDataTypes())));
                obj.add("data", data);
                response.getWriter().write(obj.toString());
            } else if (path != null && path.matches("/lookups/(requirements|glossaries|originations|editabilities|editRoles|dataTypes)")) {
                String type = path.substring(path.lastIndexOf('/') + 1);
                java.util.List<java.util.Map<String, Object>> data;
                switch (type) {
                    case "requirements": data = attributeDAO.listRequirements(); break;
                    case "glossaries": data = attributeDAO.listGlossaries(); break;
                    case "originations": data = attributeDAO.listOriginations(); break;
                    case "editabilities": data = attributeDAO.listEditabilities(); break;
                    case "editRoles": data = attributeDAO.listEditRoles(); break;
                    case "dataTypes": data = attributeDAO.listDataTypes(); break;
                    default: data = java.util.Collections.emptyList();
                }
                JsonObject obj = new JsonObject();
                obj.addProperty("success", true);
                obj.add("data", com.google.gson.JsonParser.parseString(JsonUtil.toJson(data)));
                response.getWriter().write(obj.toString());
            } else if (path != null && path.matches("/\\d+")) {
                int datasetId = Integer.parseInt(path.substring(1));
                
                String view = request.getParameter("view");
                java.util.List<java.util.Map<String, Object>> data;
                
                if (view != null && "changes".equals(view.trim())) {
                    // View Changes: show original attributes + any pending new/updated attributes from active EDIT CR
                    // This ensures attributes from completed CREATE CR still appear, and any new EDIT changes are also shown
                    data = getAttributesWithPendingChanges(datasetId);
                } else {
                    // View Original: show only original attributes (exclude any pending ones added during active CR)
                    data = getOriginalAttributes(datasetId);
                }
                
                JsonObject obj = new JsonObject();
                obj.addProperty("success", true);
                obj.add("data", com.google.gson.JsonParser.parseString(JsonUtil.toJson(data)));
                response.getWriter().write(obj.toString());
            } else {
                sendError(response, "Invalid endpoint. Use /api/attribute/{id}", 404);
            }
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (Exception e) {
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            JsonObject body = JsonParser.parseString(request.getReader().lines().reduce("", (a,b) -> a + b)).getAsJsonObject();
            // Server-side required field validation
            String primaryName = getString(body, "primary_name");
            String definition = getString(body, "definition");
            if (primaryName == null || primaryName.isBlank()) { sendError(response, "Missing required field: primary_name", 400); return; }
            if (definition == null || definition.isBlank()) { sendError(response, "Missing required field: definition", 400); return; }
            
            // Data Type validation - required for all attributes
            Integer dataTypeId = getInt(body, "data_type_id");
            if (dataTypeId == null) {
                sendError(response, "Data Type is required for all attributes", 400);
                return;
            }
            
            // Data Length validation - required only for string types
            Integer dataLength = getInt(body, "data_length");
            String dataTypeName = attributeDAO.getDataTypeNameById(dataTypeId);
            if (dataTypeName != null) {
                String upperType = dataTypeName.trim().toUpperCase();
                boolean isStringType = upperType.equals("STRING") || 
                                      upperType.equals("VARCHAR") || 
                                      upperType.equals("CHAR") || 
                                      upperType.equals("TEXT");
                
                if (isStringType) {
                    // String types require Data Length
                    if (dataLength == null || dataLength <= 0) {
                        sendError(response, "Data Length is required for string types (String, VARCHAR, CHAR, Text)", 400);
                        return;
                    }
                } else {
                    // Non-string types should not have Data Length - clear it
                    dataLength = null;
                }
            }
            
            java.util.Map<String, Object> data = toMap(body);

            // Segment rule: glossary attached via attribute must be Enterprise or in the same segment as the dataset
            Integer datasetId = getInt(body, "dataset_id");
            Integer glossaryId = getInt(body, "glossary_id");
            if (datasetId == null || datasetId <= 0) {
                sendError(response, "Missing required field: dataset_id", 400);
                return;
            }
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Data Sets", datasetId)) {
                return; // Response already sent
            }

            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    new DFCRService().ensureEditAutoCrIfMissing("Data Set", DATASET_FACET_TYPE, datasetId, getDatasetType(datasetId), currentUserId, isAdmin);
                } catch (Exception e) {
                    System.err.println("[AttributeServlet] DFCR ensure before attribute create: " + e.getMessage());
                }
            }
            
            // Check for active CR and get cloned dataset ID (like Impact tab)
            Integer activeCrId = null;
            Integer datasetIdToUse = datasetId; // Default to original ID
            
            try {
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_TYPE, datasetId);
                if (activeCrId != null) {
                    // Get or create cloned dataset (like Impact tab does with cloned process)
                    try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                        
                        if (nobjectId == null) {
                            // No summary mapping exists - clone the dataset
                            nobjectId = cloneDatasetRow(datasetId);
                            if (nobjectId != null) {
                                facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "summary", activeCrId);
                                System.out.println("Cloned dataset " + datasetId + " to " + nobjectId + " for attribute pending changes");
                            }
                        }
                        
                        if (nobjectId != null) {
                            datasetIdToUse = nobjectId;
                            data.put("dataset_id", nobjectId); // Update data map with cloned dataset ID
                            System.out.println("Using cloned dataset ID " + nobjectId + " (original: " + datasetId + ") for attribute");
                        } else {
                            System.err.println("Could not get/create cloned dataset for attribute, using original ID " + datasetId);
                        }
                    } catch (SQLException e) {
                        System.err.println("Error getting/cloning dataset for attribute: " + e.getMessage());
                        // Continue with original ID
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error checking for active CR: " + e.getMessage());
            }
            
            if (datasetIdToUse != null && glossaryId != null) {
                var vr = segmentValidationService.validateAttributeGlossarySegment(datasetIdToUse, glossaryId);
                if (!vr.isValid) {
                    sendError(response, vr.message, 400);
                    return;
                }
            }

            // Override data_length if it was cleared for non-string types
            if (dataLength == null && dataTypeName != null) {
                String upperType = dataTypeName.trim().toUpperCase();
                boolean isStringType = upperType.equals("STRING") || 
                                      upperType.equals("VARCHAR") || 
                                      upperType.equals("CHAR") || 
                                      upperType.equals("TEXT");
                if (!isStringType) {
                    data.put("data_length", null);
                }
            }
            
            int id = attributeDAO.insertAttribute(data);

            Integer createdByForCf = getInt(body, "created_by");
            int userIdForCf = (createdByForCf != null && createdByForCf > 0) ? createdByForCf : 1;
            try {
                CustomFieldServlet.materializeAttributeDefaultsIfNoDataYet(id, userIdForCf);
            } catch (Exception e) {
                System.err.println("Optional custom field default materialization for new attribute " + id + ": " + e.getMessage());
            }
            
            // Check for active automatic CR on parent dataset for pending changes tracking
            // Save mapping if active CR exists (datasetId is original ID, not cloned)
            if (datasetId != null && datasetId > 0 && activeCrId != null) {
                try {
                    // Save mapping for this attribute change (use original datasetId for mapping)
                    String areaKey = "summary#attribute";
                    facetChangesDAO.saveMapping("dataset", datasetId, id, areaKey, activeCrId);
                } catch (SQLException e) {
                    System.err.println("Error saving attribute mapping for pending changes: " + e.getMessage());
                }
            }

            // Enforce: Attribute segment must always equal its Dataset segment
            try {
                int datasetSegmentId = segmentDAO.getObjectSegmentId(datasetId, "Dataset");
                int currentAttrSegmentId = segmentDAO.getObjectSegmentId(id, "Attribute");
                Integer createdBy = getInt(body, "created_by");
                int userId = (createdBy != null && createdBy > 0) ? createdBy : 1;
                if (currentAttrSegmentId != datasetSegmentId) {
                    if (currentAttrSegmentId > 0) {
                        segmentDAO.removeObjectFromSegment(currentAttrSegmentId, id, "Attribute", userId);
                    }
                    segmentDAO.assignObjectToSegment(datasetSegmentId, id, "Attribute", userId);
                }
            } catch (Exception e) {
                // Do not fail attribute creation; but log for debugging
                System.err.println("❌ Error enforcing Attribute segment == Dataset segment: " + e.getMessage());
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("success", true);
            obj.addProperty("id", id);
            response.getWriter().write(obj.toString());
        } catch (Exception e) {
            sendError(response, e.getMessage(), 400);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            if (path == null || !path.matches("/\\d+")) { sendError(response, "ID required in path", 400); return; }
            int id = Integer.parseInt(path.substring(1));
            JsonObject body = JsonParser.parseString(request.getReader().lines().reduce("", (a,b) -> a + b)).getAsJsonObject();
            // Server-side required field validation
            String primaryName = getString(body, "primary_name");
            String definition = getString(body, "definition");
            if (primaryName == null || primaryName.isBlank()) { sendError(response, "Missing required field: primary_name", 400); return; }
            if (definition == null || definition.isBlank()) { sendError(response, "Missing required field: definition", 400); return; }
            
            // Data Type validation - required for all attributes
            Integer dataTypeId = getInt(body, "data_type_id");
            if (dataTypeId == null) {
                sendError(response, "Data Type is required for all attributes", 400);
                return;
            }
            
            // Data Length validation - required only for string types
            Integer dataLength = getInt(body, "data_length");
            String dataTypeName = attributeDAO.getDataTypeNameById(dataTypeId);
            if (dataTypeName != null) {
                String upperType = dataTypeName.trim().toUpperCase();
                boolean isStringType = upperType.equals("STRING") || 
                                      upperType.equals("VARCHAR") || 
                                      upperType.equals("CHAR") || 
                                      upperType.equals("TEXT");
                
                if (isStringType) {
                    // String types require Data Length
                    if (dataLength == null || dataLength <= 0) {
                        sendError(response, "Data Length is required for string types (String, VARCHAR, CHAR, Text)", 400);
                        return;
                    }
                } else {
                    // Non-string types should not have Data Length - clear it
                    dataLength = null;
                }
            }
            
            java.util.Map<String, Object> data = toMap(body);

            // Segment rule: glossary attached via attribute must be Enterprise or in the same segment as the dataset
            Integer datasetId = getInt(body, "dataset_id");
            Integer glossaryId = getInt(body, "glossary_id");
            if (datasetId == null || datasetId <= 0) {
                try {
                    datasetId = attributeDAO.getDatasetIdForAttribute(id);
                } catch (Exception ignored) {
                    datasetId = null;
                }
            }
            
            if (datasetId == null || datasetId <= 0) {
                sendError(response, "Could not determine dataset_id for attribute", 400);
                return;
            }
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Data Sets", datasetId)) {
                return; // Response already sent
            }

            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    new DFCRService().ensureEditAutoCrIfMissing("Data Set", DATASET_FACET_TYPE, datasetId, getDatasetType(datasetId), currentUserId, isAdmin);
                } catch (Exception e) {
                    System.err.println("[AttributeServlet] DFCR ensure before attribute update: " + e.getMessage());
                }
            }
            
            // Check for active CR and get cloned dataset ID (like Impact tab)
            Integer activeCrId = null;
            Integer datasetIdToUse = datasetId; // Default to original ID
            
            if (datasetId != null && datasetId > 0) {
                try {
                    activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_TYPE, datasetId);
                    if (activeCrId != null) {
                        // Get or create cloned dataset (like Impact tab does with cloned process)
                        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                            Integer nobjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                            
                            if (nobjectId == null) {
                                // No summary mapping exists - clone the dataset
                                nobjectId = cloneDatasetRow(datasetId);
                                if (nobjectId != null) {
                                    facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "summary", activeCrId);
                                    System.out.println("Cloned dataset " + datasetId + " to " + nobjectId + " for attribute update pending changes");
                                }
                            }
                            
                            if (nobjectId != null) {
                                datasetIdToUse = nobjectId;
                                body.addProperty("dataset_id", nobjectId); // Update body with cloned dataset ID
                                System.out.println("Using cloned dataset ID " + nobjectId + " (original: " + datasetId + ") for attribute update");
                            } else {
                                System.err.println("Could not get/create cloned dataset for attribute update, using original ID " + datasetId);
                            }
                        } catch (SQLException e) {
                            System.err.println("Error getting/cloning dataset for attribute update: " + e.getMessage());
                            // Continue with original ID
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error checking for active CR: " + e.getMessage());
                }
            }
            
            if (datasetIdToUse != null && glossaryId != null) {
                var vr = segmentValidationService.validateAttributeGlossarySegment(datasetIdToUse, glossaryId);
                if (!vr.isValid) {
                    sendError(response, vr.message, 400);
                    return;
                }
            }

            // Override data_length if it was cleared for non-string types
            if (dataLength == null && dataTypeName != null) {
                String upperType = dataTypeName.trim().toUpperCase();
                boolean isStringType = upperType.equals("STRING") || 
                                      upperType.equals("VARCHAR") || 
                                      upperType.equals("CHAR") || 
                                      upperType.equals("TEXT");
                if (!isStringType) {
                    data.put("data_length", null);
                }
            }
            
            data.put("id", id);
            boolean ok = attributeDAO.updateAttribute(data);
            
            // Check for active automatic CR on parent dataset for pending changes tracking
            // Save mapping if active CR exists (datasetId is original ID, not cloned)
            if (datasetId != null && datasetId > 0 && activeCrId != null) {
                try {
                    // Save mapping for this attribute change (use original datasetId for mapping)
                    String areaKey = "summary#attribute";
                    facetChangesDAO.saveMapping("dataset", datasetId, id, areaKey, activeCrId);
                } catch (SQLException e) {
                    System.err.println("Error saving attribute mapping for pending changes: " + e.getMessage());
                }
            }

            // Enforce: Attribute segment must always equal its Dataset segment
            if (datasetId != null && datasetId > 0) {
                try {
                    int datasetSegmentId = segmentDAO.getObjectSegmentId(datasetId, "Dataset");
                    int currentAttrSegmentId = segmentDAO.getObjectSegmentId(id, "Attribute");
                    Integer updatedBy = getInt(body, "last_updated_user_id");
                    int userId = (updatedBy != null && updatedBy > 0) ? updatedBy : 1;
                    if (currentAttrSegmentId != datasetSegmentId) {
                        if (currentAttrSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(currentAttrSegmentId, id, "Attribute", userId);
                        }
                        segmentDAO.assignObjectToSegment(datasetSegmentId, id, "Attribute", userId);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error enforcing Attribute segment == Dataset segment: " + e.getMessage());
                }
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("success", ok);
            response.getWriter().write(obj.toString());
        } catch (Exception e) {
            sendError(response, e.getMessage(), 400);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            if (path == null || !path.matches("/\\d+")) { sendError(response, "ID required in path", 400); return; }
            int id = Integer.parseInt(path.substring(1));
            boolean ok = attributeDAO.deleteAttribute(id);
            JsonObject obj = new JsonObject();
            obj.addProperty("success", ok);
            response.getWriter().write(obj.toString());
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("foreign key constraint")) {
                if (msg.contains("process_x_attribute")) {
                    msg = "This attribute is linked to one or more Processes. Open the attribute's Impact tab and remove the Process links before deleting it.";
                } else if (msg.contains("policy_x_attribute")) {
                    msg = "This attribute is linked to one or more Policies. Open the attribute's Impact tab and remove the Policy links before deleting it.";
                } else if (msg.contains("project_x_attribute")) {
                    msg = "This attribute is linked to one or more Projects. Open the attribute's Impact tab and remove the Project links before deleting it.";
                } else if (msg.contains("attribute_x_attribute")) {
                    msg = "This attribute is used in one or more attribute relationships (as a source or target). "
                        + "Go to the Relationships tab and remove those relationships before deleting it.";
                } else {
                    msg = "This attribute cannot be deleted because it is still referenced by other records. Remove those links first.";
                }
            }
            sendError(response, msg != null ? msg : "An error occurred while deleting the attribute.", 400);
        }
    }

    private void setupResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
    }

    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        JsonUtil.sendErrorResponse(response.getWriter(), message, status);
    }

    private java.util.Map<String, Object> toMap(JsonObject json) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("is_primary_key", getInt(json, "is_primary_key"));
        m.put("is_mandatory", getInt(json, "is_mandatory"));
        m.put("rank", getInt(json, "rank"));
        m.put("confidence_score", getFloat(json, "confidence_score"));
        m.put("requirement_id", getInt(json, "requirement_id"));
        m.put("business_logic", getString(json, "business_logic"));
        m.put("ref_number", getString(json, "ref_number"));
        m.put("primary_name", getString(json, "primary_name"));
        m.put("definition", getString(json, "definition"));
        m.put("db_field_name", getString(json, "db_field_name"));
        m.put("glossary_id", getInt(json, "glossary_id"));
        m.put("origination", getInt(json, "origination"));
        m.put("editability", getInt(json, "editability"));
        m.put("editability_role", getInt(json, "editability_role"));
        m.put("data_type_id", getInt(json, "data_type_id"));
        m.put("data_length", getInt(json, "data_length"));
        m.put("dataset_id", getInt(json, "dataset_id"));
        m.put("created_by", getInt(json, "created_by"));
        m.put("last_updated_user_id", getInt(json, "last_updated_user_id"));
        return m;
    }

    private Integer getInt(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : null;
    }

    private Float getFloat(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsFloat() : null;
    }

    private String getString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }
    
    /**
     * Get attributes with pending changes for View Changes mode.
     * Returns original attributes + any pending new/updated attributes from the active EDIT CR.
     * This ensures attributes from a completed CREATE CR still appear, and any new EDIT changes are also shown.
     */
    private java.util.List<java.util.Map<String, Object>> getAttributesWithPendingChanges(int datasetId) throws SQLException {
        Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_TYPE, datasetId);
        if (activeCrId == null) {
            // No active CR - return all attributes as-is
            return attributeDAO.getAttributesByDatasetId(datasetId);
        }
        
        // Get all pending (new/updated) attribute IDs from the mapping for this CR
        String areaKey = "summary#attribute";
        java.util.List<Integer> pendingIds = facetChangesDAO.getAllNObjectIds("dataset", datasetId, areaKey, activeCrId);
        java.util.Set<Integer> pendingIdSet = new java.util.HashSet<>(pendingIds);
        
        // Start with original attributes from the original dataset
        java.util.List<java.util.Map<String, Object>> originalAttributes = attributeDAO.getAttributesByDatasetId(datasetId);
        java.util.List<java.util.Map<String, Object>> allAttributes = new java.util.ArrayList<>();
        
        // Add original attributes (exclude any that are in the pending set - they'll be re-added with updated values)
        for (java.util.Map<String, Object> attr : originalAttributes) {
            Object idObj = attr.get("ID");
            if (idObj == null) idObj = attr.get("id");
            if (idObj != null) {
                int attrId = ((Number) idObj).intValue();
                if (!pendingIdSet.contains(attrId)) {
                    allAttributes.add(attr);
                }
            } else {
                allAttributes.add(attr);
            }
        }
        
        // Add pending attributes (newly added or updated during this CR)
        for (Integer pendingId : pendingIds) {
            java.util.Map<String, Object> pending = attributeDAO.getAttributeMapById(pendingId);
            if (pending != null) {
                pending.put("pending", true);
                allAttributes.add(pending);
            }
        }
        
        return allAttributes;
    }
    
    /**
     * Get only original attributes (exclude pending changes) for View Original mode.
     * Shows all attributes that belong to the original dataset (Dataset_ID = datasetId).
     * 
     * Newly added attributes during an active EDIT CR are automatically excluded because
     * they are inserted with Dataset_ID = clonedDatasetId, so getAttributesByDatasetId(originalDatasetId)
     * won't return them.
     * 
     * Updated attributes (edited in-place during EDIT CR) still have Dataset_ID = originalDatasetId,
     * so they correctly appear in View Original. The update is in-place so the current values are shown.
     * 
     * NOTE: We intentionally do NOT filter by pending mappings here. The previous approach filtered out
     * all attributes whose IDs appeared in the CR mappings, which incorrectly hid attributes that were
     * merely updated (not newly added) during the EDIT CR. Since the UPDATE SQL in AttributeDAO does NOT
     * change Dataset_ID, updated attributes remain with Dataset_ID = originalDatasetId and should still
     * appear in View Original.
     */
    private java.util.List<java.util.Map<String, Object>> getOriginalAttributes(int datasetId) throws SQLException {
        // Simply return all attributes belonging to the original dataset.
        // - Attributes from completed CREATE CR have Dataset_ID = originalDatasetId (updated by applyAttributeChanges)
        // - Attributes updated during active EDIT CR still have Dataset_ID = originalDatasetId (UPDATE doesn't change it)
        // - Newly added attributes during active EDIT CR have Dataset_ID = clonedDatasetId, so they are excluded
        return attributeDAO.getAttributesByDatasetId(datasetId);
    }
    
    /**
     * Clone a dataset row for pending changes (same as DatasetServlet.cloneDatasetRow)
     */
    private Integer cloneDatasetRow(int originalId) throws SQLException {
        String sql = "INSERT INTO dataset (" +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID" +
                ") SELECT " +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID " +
                "FROM dataset WHERE ID = ?";
        
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    private Integer getDatasetType(int datasetId) {
        try (java.sql.Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT DatasetType FROM dataset WHERE ID = ?";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int typeId = rs.getInt("DatasetType");
                        return rs.wasNull() ? null : typeId;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AttributeServlet] Error getting dataset type: " + e.getMessage());
        }
        return null;
    }
    
}
