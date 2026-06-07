package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Capability;
import com.example.budg_v2.service.CapabilityService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
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
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.example.budg_v2.database.DatabaseConnection;

@WebServlet("/api/capabilities/*")
public class CapabilityServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(CapabilityServlet.class.getName());
    private CapabilityService capabilityService;
    private SegmentDAO segmentDAO;
    private SegmentValidationService segmentValidationService;

    public CapabilityServlet() {
        this.capabilityService = new CapabilityService();
        this.segmentDAO = new SegmentDAO();
        this.segmentValidationService = new SegmentValidationService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (pathInfo == null || "/".equals(pathInfo) || "/list".equals(pathInfo)) {
                // Return all capabilities
                logger.info("CapabilityServlet: Getting all capabilities (userId: " + userId + ")");
                List<Capability> capabilities = userId > 0 ? 
                    capabilityService.getAllCapabilities(userId) : 
                    capabilityService.getAllCapabilities();
                capabilities = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        capabilities,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Capability",
                        Capability::getId);
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("count", capabilities.size());
                
                com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
                for (Capability capability : capabilities) {
                    com.google.gson.JsonObject capabilityJson = new com.google.gson.JsonObject();
                    capabilityJson.addProperty("id", capability.getId());
                    capabilityJson.addProperty("primaryName", capability.getPrimaryName());
                    capabilityJson.addProperty("description", capability.getDescription());
                    capabilityJson.addProperty("parentId", capability.getParentId());
                    capabilityJson.addProperty("refNumber", capability.getRefNumber());
                    capabilityJson.addProperty("status", capability.getStatus());
                    capabilityJson.addProperty("lifecycle", capability.getLifecycle());
                    capabilityJson.addProperty("classification", capability.getClassification());
                    capabilityJson.addProperty("capabilityType", capability.getCapabilityType());
                    capabilityJson.addProperty("isPublic", capability.getIsPublic());
                    capabilityJson.addProperty("createDatetime", capability.getCreateDatetime() != null ? capability.getCreateDatetime().toString() : null);
                    capabilityJson.addProperty("lastUpdateDatetime", capability.getLastUpdateDatetime() != null ? capability.getLastUpdateDatetime().toString() : null);
                    dataArray.add(capabilityJson);
                }
                responseJson.add("data", dataArray);
                response.getWriter().write(responseJson.toString());
                
            } else if ("/hierarchy".equals(pathInfo)) {
                // Return the full set so the relationship hierarchy tree can include
                // children that live in private segments. Inaccessible nodes are
                // masked downstream so their identifying fields are hidden.
                List<Capability> capabilities = userId > 0
                    ? capabilityService.getAllCapabilitiesForHierarchy()
                    : capabilityService.getAllCapabilities();

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Capability cap : capabilities) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", cap.getId());
                    o.addProperty("primaryName", cap.getPrimaryName());
                    o.addProperty("description", cap.getDescription());
                    o.addProperty("refNumber", cap.getRefNumber());
                    o.addProperty("parentId", cap.getParentId());
                    arr.add(o);
                }
                com.example.budg_v2.util.HierarchyAccessMasker.mask(arr, "Capability", userId);
                response.getWriter().write(arr.toString());
                
            } else if ("/dropdown".equals(pathInfo)) {
                getAllCapabilitiesForDropdown(request, response);
            } else if ("/generate-ref".equals(pathInfo)) {
                generateRefNumber(response);
            } else if (pathInfo.startsWith("/relationships/")) {
                // Get relationships by source capability ID
                String idParam = pathInfo.substring(15); // Remove "/relationships/" prefix
                try {
                    int sourceId = Integer.parseInt(idParam);
                    getCapabilityRelationshipsBySourceId(sourceId, response);
                } catch (NumberFormatException e) {
                    sendErrorSafe(response, 400, "Invalid capability ID");
                }
            } else if ("/relation-type/list".equals(pathInfo)) {
                // Get capability relationship types
                getCapabilityRelationTypes(response);
            } else if (pathInfo.startsWith("/search/")) {
                String searchQuery = pathInfo.substring(8); // Remove "/search/" prefix
                searchCapabilities(response, searchQuery);
            } else if (pathInfo.startsWith("/")) {
                // Handle paths like /{id} or /{id}/hierarchy
                String[] parts = pathInfo.substring(1).split("/");
                try {
                    int id = Integer.parseInt(parts[0]);
                    
                    if (parts.length > 1 && "hierarchy".equals(parts[1].toLowerCase())) {
                        // Handle /{id}/hierarchy endpoint
                        try {
                            com.example.budg_v2.dao.CapabilityDAO capabilityDAO = new com.example.budg_v2.dao.CapabilityDAO();
                            List<Map<String, Object>> hierarchy = capabilityDAO.getCapabilityHierarchyFlat(id);
                            com.example.budg_v2.util.HierarchyAccessMasker.mask(hierarchy, "Capability", userId);
                            com.google.gson.Gson gson = new com.google.gson.Gson();
                            response.getWriter().write(gson.toJson(hierarchy));
                        } catch (SQLException e) {
                            System.err.println("[CapabilityServlet] Error getting hierarchy for capability " + id + ": " + e.getMessage());
                            e.printStackTrace();
                            // Return empty array on error
                            response.getWriter().write("[]");
                        }
                        return;
                    } else {
                        // Get capability by ID
                        getCapabilityById(request, response, id);
                    }
                } catch (NumberFormatException e) {
                    sendErrorSafe(response, 400, "Invalid capability ID");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error in doGet", e);
            sendErrorSafe(response, 500, "Database error occurred");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error in doGet", e);
            sendErrorSafe(response, 500, "An unexpected error occurred");
        }
    }

    /** Send JSON error without throwing; avoids container HTML error page when response is committed. */
    private void sendErrorSafe(HttpServletResponse response, int status, String message) {
        if (response.isCommitted()) return;
        try {
            response.setStatus(status);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            JsonUtil.sendErrorResponse(response.getWriter(), message, status);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not send error response (response may be committed)", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            
            // Check if this is a relationship endpoint (skip permission check for relationships)
            if (pathInfo != null && pathInfo.equals("/relationship")) {
                handleCreateCapabilityRelationship(request, response);
                return;
            }
            
            // Check create permission for new capability creation
            if (!PermissionCheckUtil.checkCreatePermission(request, response, "Capability")) {
                return; // Response already sent
            }
            
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryName");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parentId");
            String refNumber = JsonUtil.getJsonString(jsonData, "refNumber");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer lifecycle = JsonUtil.getJsonInt(jsonData, "lifecycle");
            Integer classification = JsonUtil.getJsonInt(jsonData, "classification");
            Integer capabilityType = JsonUtil.getJsonInt(jsonData, "capabilityType");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "isPublic");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastUpdateUserId");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }
            
            if (lastUpdateUserId == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "User ID is required", 400);
                return;
            }

            // Auto-generate ref number if not provided
            if (refNumber == null || refNumber.trim().isEmpty()) {
                try {
                    refNumber = com.example.budg_v2.util.ReferenceNumberGenerator.generateCapabilityReference();
                    logger.info("Auto-generated ref number: " + refNumber);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Error generating capability reference", e);
                    JsonUtil.sendErrorResponse(response.getWriter(), "Failed to generate reference number", 500);
                    return;
                }
            }

            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentId == null) {
                segmentId = 1;
            }
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Capability", primaryName.trim(),
                        segmentId.longValue(), null)) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Capability name already exists in this segment", 400);
                    return;
                }
            }

            Capability capability = new Capability();
            capability.setPrimaryName(primaryName.trim());
            capability.setDescription(description);
            capability.setParentId(parentId);
            capability.setRefNumber(refNumber);
            capability.setStatus(status);
            capability.setLifecycle(lifecycle);
            capability.setClassification(classification);
            capability.setCapabilityType(capabilityType);
            capability.setIsPublic(isPublic);
            capability.setLastUpdateUserId(lastUpdateUserId);

            // Validate segment hierarchy before creating capability
            if (parentId != null && parentId > 0) {
                try {
                    var hierarchyResult = segmentValidationService.validateParentChildSegment(parentId, segmentId, "Capability");
                    if (!hierarchyResult.isValid) {
                        JsonUtil.sendErrorResponse(response.getWriter(), hierarchyResult.message, 400);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error validating capability hierarchy: " + e.getMessage());
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error validating segment hierarchy: " + e.getMessage(), 500);
                    return;
                }
            }

            Capability createdCapability = capabilityService.createCapability(capability);
            
            // Assign capability to segment
            try {
                segmentDAO.assignObjectToSegment(segmentId, createdCapability.getId(), "Capability", lastUpdateUserId != null ? lastUpdateUserId : 1);
                //system.out.println("✅ Capability " + createdCapability.getId() + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning capability to segment: " + e.getMessage());
            }
            
            // Assign creator role after successful creation
            //system.out.println("✅ Calling assignCreatorRole...");
            try {
                assignCreatorRole(createdCapability.getId(), lastUpdateUserId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning creator role: " + e.getMessage());
                e.printStackTrace();
                // Continue - don't fail the entire save
            }
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Capability created successfully");
            responseJson.addProperty("id", createdCapability.getId());
            
            response.getWriter().write(responseJson.toString());
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error in doPost", e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Database error occurred", 500);
        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error in doPost", e);
            JsonUtil.sendErrorResponse(response.getWriter(), "An unexpected error occurred", 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.length() <= 1) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Capability ID is required", 400);
                return;
            }

            // Check if this is a relationship update endpoint
            if (pathInfo.startsWith("/relationship/")) {
                String idParam = pathInfo.substring(14); // Remove "/relationship/" prefix
                try {
                    int relationshipId = Integer.parseInt(idParam);
                    handleUpdateCapabilityRelationship(request, response, relationshipId);
                    return;
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid relationship ID", 400);
                    return;
                }
            }
            
            String idParam = pathInfo.substring(1);
            int id;
            try {
                id = Integer.parseInt(idParam);
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid capability ID: " + idParam, 400);
                return;
            }
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Capability", id)) {
                return; // Response already sent
            }
            
            int capabilityId = id;
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryName");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parentId");
            String refNumber = JsonUtil.getJsonString(jsonData, "refNumber");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer lifecycle = JsonUtil.getJsonInt(jsonData, "lifecycle");
            Integer classification = JsonUtil.getJsonInt(jsonData, "classification");
            Integer capabilityType = JsonUtil.getJsonInt(jsonData, "capabilityType");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "isPublic");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastUpdateUserId");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }
            
            if (lastUpdateUserId == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "User ID is required", 400);
                return;
            }

            Integer segmentIdForName = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentIdForName == null) {
                segmentIdForName = JsonUtil.getJsonInt(jsonData, "segment_id");
            }
            long effSeg;
            if (segmentIdForName != null) {
                effSeg = segmentIdForName.longValue();
            } else {
                int cur = segmentDAO.getObjectSegmentId(capabilityId, "Capability");
                effSeg = cur > 0 ? cur : 1L;
            }
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Capability", primaryName.trim(), effSeg, capabilityId)) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Capability name already exists in this segment", 400);
                    return;
                }
            }

            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, capabilityId, "Capability");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonUtil.sendErrorResponse(response.getWriter(), "Access denied. You don't have permission to edit this capability.", 403);
                    return;
                }
            }

            Capability capability = new Capability();
            capability.setId(capabilityId);
            capability.setPrimaryName(primaryName.trim());
            capability.setDescription(description);
            capability.setParentId(parentId);
            capability.setRefNumber(refNumber);
            capability.setStatus(status);
            capability.setLifecycle(lifecycle);
            capability.setClassification(classification);
            capability.setCapabilityType(capabilityType);
            capability.setIsPublic(isPublic);
            capability.setLastUpdateUserId(lastUpdateUserId);

            // Segment change should be allowed even if the core record is unchanged
            Integer segmentId = segmentIdForName;
            boolean segmentChanged = false;
            if (segmentId != null) {
                try {
                    int currentSegmentId = segmentDAO.getObjectSegmentId(capabilityId, "Capability");
                    segmentChanged = currentSegmentId != segmentId;
                } catch (Exception ignored) {
                    segmentChanged = true;
                }
            }

            // Ensure object exists (avoid creating segment assignments for non-existent IDs)
            Capability existing = capabilityService.getCapabilityById(capabilityId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Capability not found", 404);
                return;
            }

            boolean updated = capabilityService.updateCapability(capability);
            
            // Apply segment assignment even if core update had no changes
            if (segmentId != null && segmentChanged) {
                    try {
                        int currentSegmentId = segmentDAO.getObjectSegmentId(capabilityId, "Capability");
                        if (currentSegmentId != segmentId) {
                            com.example.budg_v2.service.SegmentValidationService validationService = new com.example.budg_v2.service.SegmentValidationService();
                            var validationResult = validationService.validateSegmentMove(
                                    capabilityId,
                                    segmentId,
                                    "Capability",
                                    parentId
                            );
                            if (!validationResult.isValid) {
                                JsonUtil.sendErrorResponse(response.getWriter(), validationResult.message, 400);
                                return;
                            }
                            if (currentSegmentId > 0) {
                                segmentDAO.removeObjectFromSegment(currentSegmentId, capabilityId, "Capability", lastUpdateUserId != null ? lastUpdateUserId : 1);
                            }
                            segmentDAO.assignObjectToSegment(segmentId, capabilityId, "Capability", lastUpdateUserId != null ? lastUpdateUserId : 1);
                            System.out.println("✅ Capability " + capabilityId + " segment changed from " + currentSegmentId + " to " + segmentId);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error updating capability segment: " + e.getMessage());
                    }
                }

            if (!updated && segmentId == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "No changes detected", 400);
                return;
            }

                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Capability updated successfully");
                response.getWriter().write(responseJson.toString());
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error in doPut", e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Database error occurred", 500);
        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            if (e instanceof NumberFormatException) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid capability ID", 400);
            } else {
                logger.log(Level.SEVERE, "Unexpected error in doPut", e);
                JsonUtil.sendErrorResponse(response.getWriter(), "An unexpected error occurred", 500);
            }
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Capability ID is required", 400);
                return;
            }

            // Check if this is a relationship delete endpoint
            if (pathInfo.startsWith("/relationship/")) {
                String idParam = pathInfo.substring(14); // Remove "/relationship/" prefix
                try {
                    int relationshipId = Integer.parseInt(idParam);
                    handleDeleteCapabilityRelationship(request, response, relationshipId);
                    return;
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid relationship ID", 400);
                    return;
                }
            }

            String idParam = pathInfo.substring(1);
            int capabilityId = Integer.parseInt(idParam);

            boolean deleted = capabilityService.deleteCapability(capabilityId, request);
            
            if (deleted) {
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Capability deleted successfully");
                response.getWriter().write(responseJson.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Capability not found or delete failed", 404);
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error in doDelete", e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Database error occurred", 500);
        } catch (Exception e) {
            if (e instanceof NumberFormatException) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid capability ID", 400);
            } else {
                logger.log(Level.SEVERE, "Unexpected error in doDelete", e);
                JsonUtil.sendErrorResponse(response.getWriter(), "An unexpected error occurred", 500);
            }
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllCapabilitiesForDropdown(HttpServletRequest request, HttpServletResponse response) throws IOException, SQLException {
        int userId = UserContextUtil.getCurrentUserId(request);
        List<Capability> capabilities = userId > 0 ? 
            capabilityService.getAllCapabilitiesForDropdown(userId) : 
            capabilityService.getAllCapabilitiesForDropdown();
        capabilities = RequestedSegmentFilterUtil.filterByRequestedSegment(
                capabilities,
                RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                "Capability",
                Capability::getId);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", capabilities.size());
        
        com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
        for (Capability capability : capabilities) {
            com.google.gson.JsonObject capabilityJson = new com.google.gson.JsonObject();
            capabilityJson.addProperty("id", capability.getId());
            capabilityJson.addProperty("primaryName", capability.getPrimaryName());
            capabilityJson.addProperty("description", capability.getDescription());
            dataArray.add(capabilityJson);
        }
        jsonResponse.add("data", dataArray);
        
        response.getWriter().write(jsonResponse.toString());
    }

    private void getCapabilityById(HttpServletRequest request, HttpServletResponse response, int id) throws IOException, SQLException {
        Capability capability = capabilityService.getCapabilityById(id);
        if (capability != null) {
            if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(capability.getStatusName())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"This object is not available.\"}");
                return;
            }
            // Check access control for all users (including guests)
            int userId = UserContextUtil.getCurrentUserId(request);

            // GUEST ACCESS CHECK: Only allow public objects in Enterprise segment
            if (userId <= 0) {
                // Guest user - check if object is public and in Enterprise segment
                try {
                    boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Capability");
                    if (!canAccess) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                        return;
                    }
                } catch (SQLException e) {
                    // On error, deny access for safety
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                    return;
                }
            }

            // Authenticated user access check
            if (userId > 0) {
                boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Capability");
                if (!canAccess) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this capability.\"}");
                    return;
                }
            }

            try {
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);

                com.google.gson.JsonObject capabilityJson = new com.google.gson.JsonObject();
                capabilityJson.addProperty("id", capability.getId() != null ? capability.getId() : id);
                capabilityJson.addProperty("primaryName", capability.getPrimaryName());
                capabilityJson.addProperty("description", capability.getDescription());
                capabilityJson.addProperty("parentId", capability.getParentId());
                capabilityJson.addProperty("refNumber", capability.getRefNumber());
                capabilityJson.addProperty("status", capability.getStatus());
                capabilityJson.addProperty("lifecycle", capability.getLifecycle());
                capabilityJson.addProperty("classification", capability.getClassification());
                capabilityJson.addProperty("capabilityType", capability.getCapabilityType());
                capabilityJson.addProperty("isPublic", capability.getIsPublic());
                capabilityJson.addProperty("createDatetime", capability.getCreateDatetime() != null ? capability.getCreateDatetime().toString() : null);
                capabilityJson.addProperty("lastUpdateDatetime", capability.getLastUpdateDatetime() != null ? capability.getLastUpdateDatetime().toString() : null);
                // Expose last update user so view can resolve "Last Updated By" (capability.js resolveReferences)
                if (capability.getLastUpdateUserId() != null) {
                    capabilityJson.addProperty("lastUpdateUserId", capability.getLastUpdateUserId());
                    capabilityJson.addProperty("LastUpdateUser_ID", capability.getLastUpdateUserId());
                }

                SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Capability");
                SegmentResponseUtil.applySegmentInfo(capabilityJson, segmentInfo, request);

                responseJson.add("data", capabilityJson);
                response.getWriter().write(responseJson.toString());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error building capability response for id=" + id, e);
                sendErrorSafe(response, 500, "Failed to load capability: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
        } else {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                try {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Capability not found", 404);
                } catch (IOException e2) {
                    logger.log(Level.WARNING, "Could not send 404 response", e2);
                }
            }
        }
    }

    private void generateRefNumber(HttpServletResponse response) throws IOException {
        try {
            String refNumber = com.example.budg_v2.util.ReferenceNumberGenerator.generateCapabilityReference();
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("refNumber", refNumber);
            response.getWriter().write(responseJson.toString());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error generating ref number", e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Failed to generate reference number", 500);
        }
    }

    private void searchCapabilities(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<Capability> capabilities = capabilityService.searchCapabilities(searchQuery);
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("success", true);
        responseJson.addProperty("count", capabilities.size());
        
        com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
        for (Capability capability : capabilities) {
            com.google.gson.JsonObject capabilityJson = new com.google.gson.JsonObject();
            capabilityJson.addProperty("id", capability.getId());
            capabilityJson.addProperty("primaryName", capability.getPrimaryName());
            capabilityJson.addProperty("description", capability.getDescription());
            capabilityJson.addProperty("parentId", capability.getParentId());
            capabilityJson.addProperty("refNumber", capability.getRefNumber());
            capabilityJson.addProperty("status", capability.getStatus());
            capabilityJson.addProperty("lifecycle", capability.getLifecycle());
            capabilityJson.addProperty("classification", capability.getClassification());
            capabilityJson.addProperty("capabilityType", capability.getCapabilityType());
            capabilityJson.addProperty("isPublic", capability.getIsPublic());
            capabilityJson.addProperty("createDatetime", capability.getCreateDatetime() != null ? capability.getCreateDatetime().toString() : null);
            capabilityJson.addProperty("lastUpdateDatetime", capability.getLastUpdateDatetime() != null ? capability.getLastUpdateDatetime().toString() : null);
            dataArray.add(capabilityJson);
        }
        responseJson.add("data", dataArray);
        
        response.getWriter().write(responseJson.toString());
    }

    private void assignCreatorRole(int capabilityId, int userId) throws SQLException {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Capability");
                java.util.List<Integer> rolesToAssign = com.example.budg_v2.util.DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);

                if (rolesToAssign.isEmpty()) {
                    conn.commit();
                    return;
                }

                for (Integer roleId : rolesToAssign) {
                    try {
                        String insertOXP = """
                                INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                                VALUES (NULL, ?, ?, 2, 1, ?)
                                """;
                        int objectXPeopleId;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertOXP, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, roleId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into object_x_people");
                            try (java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new java.sql.SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        String insertCapabilityX = """
                                INSERT INTO capability_x_objectxpeople (Object_x_ipid, CapabilityID, Last_UpdateUser_ID)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertCapabilityX)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, capabilityId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into capability_x_objectxpeople");
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Capability", capabilityId, userId, roleId, objectXPeopleId, conn);

                    } catch (java.sql.SQLException e) {
                        System.err.println("❌ Error assigning default role " + roleId + " to creator: " + e.getMessage());
                    }
                }

                conn.commit();
            } catch (java.sql.SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (java.sql.SQLException e) {
            System.err.println("❌ Error in assignCreatorRole: " + e.getMessage());
            throw e;
        }
    }


    @SuppressWarnings("unused")
    private Integer getNextObjectXPeopleId(java.sql.Connection conn) throws SQLException {
        String query = "SELECT COALESCE(MAX(ID), 0) + 1 FROM object_x_people";
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(query);
             java.sql.ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1; // Fallback
    }

    // Get capability relationships by source ID (bidirectional - forward + reverse)
    private void getCapabilityRelationshipsBySourceId(int sourceId, HttpServletResponse response) throws IOException, SQLException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Forward relationships: where current capability is SOURCE
            String forwardSql = """
                SELECT 
                    cxc.ID as id,
                    cxc.Source_ID as sourceCapabilityId,
                    cxc.Target_ID as targetCapabilityId,
                    cxc.RelationType as relationType,
                    cxc.Description as description,
                    c.PrimaryName as targetCapabilityName,
                    rt.PrimaryName as relationshipType,
                    rt.ReverseName as relationshipTypeReverseName,
                    'forward' as direction
                FROM capability_x_capability cxc
                LEFT JOIN capability c ON cxc.Target_ID = c.ID
                LEFT JOIN capability_x_capability_relationtype rt ON cxc.RelationType = rt.ID
                WHERE cxc.Source_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            """;
            
            // Reverse relationships: where current capability is TARGET
            String reverseSql = """
                SELECT 
                    cxc.ID as id,
                    cxc.Source_ID as sourceCapabilityId,
                    cxc.Target_ID as targetCapabilityId,
                    cxc.RelationType as relationType,
                    cxc.Description as description,
                    c.PrimaryName as sourceCapabilityName,
                    rt.PrimaryName as relationshipType,
                    rt.ReverseName as relationshipTypeReverseName,
                    'reverse' as direction
                FROM capability_x_capability cxc
                LEFT JOIN capability c ON cxc.Source_ID = c.ID
                LEFT JOIN capability_x_capability_relationtype rt ON cxc.RelationType = rt.ID
                WHERE cxc.Target_ID = ? AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '1970-01-01 00:00:00')
            """;
            
            // Get forward relationships
            try (PreparedStatement stmt = conn.prepareStatement(forwardSql)) {
                stmt.setInt(1, sourceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> rel = new HashMap<>();
                        rel.put("id", rs.getInt("id"));
                        rel.put("sourceCapabilityId", rs.getInt("sourceCapabilityId"));
                        rel.put("targetCapabilityId", rs.getInt("targetCapabilityId"));
                        rel.put("relationType", rs.getInt("relationType"));
                        rel.put("description", rs.getString("description"));
                        rel.put("targetCapabilityName", rs.getString("targetCapabilityName"));
                        rel.put("relationshipType", rs.getString("relationshipType"));
                        rel.put("direction", "forward");
                        relationships.add(rel);
                    }
                }
            }
            
            // Get reverse relationships
            try (PreparedStatement stmt = conn.prepareStatement(reverseSql)) {
                stmt.setInt(1, sourceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> rel = new HashMap<>();
                        rel.put("id", rs.getInt("id"));
                        rel.put("sourceCapabilityId", rs.getInt("sourceCapabilityId"));
                        rel.put("targetCapabilityId", rs.getInt("targetCapabilityId"));
                        rel.put("relationType", rs.getInt("relationType"));
                        rel.put("description", rs.getString("description"));
                        // In reverse, the source capability is the "other" capability
                        rel.put("targetCapabilityName", rs.getString("sourceCapabilityName"));
                        rel.put("targetCapabilityId", rs.getInt("sourceCapabilityId")); // For reverse, use source as target for link
                        // Use reverse name if available, otherwise use regular name
                        String reverseName = rs.getString("relationshipTypeReverseName");
                        rel.put("relationshipType", (reverseName != null && !reverseName.trim().isEmpty()) ? reverseName : rs.getString("relationshipType"));
                        rel.put("direction", "reverse");
                        relationships.add(rel);
                    }
                }
            }
            
            // Sort by target capability name
            relationships.sort((a, b) -> {
                String nameA = (String) a.getOrDefault("targetCapabilityName", "");
                String nameB = (String) b.getOrDefault("targetCapabilityName", "");
                return nameA.compareToIgnoreCase(nameB);
            });
        }
        
        com.google.gson.JsonObject responseJson = new com.google.gson.JsonObject();
        responseJson.addProperty("success", true);
        com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
        for (Map<String, Object> rel : relationships) {
            com.google.gson.JsonObject relJson = new com.google.gson.JsonObject();
            relJson.addProperty("id", (Integer) rel.get("id"));
            relJson.addProperty("sourceCapabilityId", (Integer) rel.get("sourceCapabilityId"));
            relJson.addProperty("targetCapabilityId", (Integer) rel.get("targetCapabilityId"));
            relJson.addProperty("relationType", (Integer) rel.get("relationType"));
            relJson.addProperty("description", (String) rel.get("description"));
            relJson.addProperty("targetCapabilityName", (String) rel.get("targetCapabilityName"));
            relJson.addProperty("relationshipType", (String) rel.get("relationshipType"));
            dataArray.add(relJson);
        }
        responseJson.add("data", dataArray);
        response.getWriter().write(responseJson.toString());
    }

    // Create capability relationship
    private void handleCreateCapabilityRelationship(HttpServletRequest request, HttpServletResponse response) throws IOException, SQLException {
        JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());
        int sourceId = JsonUtil.getJsonInt(jsonData, "sourceId");
        int targetId = JsonUtil.getJsonInt(jsonData, "targetId");
        int relationType = JsonUtil.getJsonInt(jsonData, "relationType");
        String description = JsonUtil.getJsonString(jsonData, "description");
        int userId = UserContextUtil.getCurrentUserId(request);
        
        if (sourceId <= 0 || targetId <= 0 || relationType <= 0) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Source ID, Target ID, and Relation Type are required", 400);
            return;
        }

        var validationResult = segmentValidationService.validateCrossSegmentRelationship(sourceId, "Capability", targetId, "Capability");
        if (!validationResult.isValid) {
            JsonUtil.sendErrorResponse(response.getWriter(), validationResult.message, 400);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            int relationshipId = createCapabilityRelationship(conn, sourceId, targetId, relationType, description, userId);
            
            if (relationshipId > 0) {
                logCapXCapInsertAudit(conn, sourceId, targetId, relationType, userId);

                com.google.gson.JsonObject responseJson = new com.google.gson.JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("id", relationshipId);
                responseJson.addProperty("message", "Capability relationship created successfully");
                response.getWriter().write(responseJson.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Failed to create capability relationship", 500);
            }
        }
    }

    // Update capability relationship
    private void handleUpdateCapabilityRelationship(HttpServletRequest request, HttpServletResponse response, int relationshipId) throws IOException, SQLException {
        JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());
        int relationType = JsonUtil.getJsonInt(jsonData, "relationType");
        int targetCapabilityId = JsonUtil.getJsonInt(jsonData, "targetCapabilityId");
        String description = JsonUtil.getJsonString(jsonData, "description");
        int userId = UserContextUtil.getCurrentUserId(request);
        
        if (relationType <= 0 || targetCapabilityId <= 0) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Relation Type and Target Capability ID are required", 400);
            return;
        }

        int sourceCapabilityId = getSourceCapabilityId(relationshipId);
        if (sourceCapabilityId > 0) {
            var validationResult = segmentValidationService.validateCrossSegmentRelationship(sourceCapabilityId, "Capability", targetCapabilityId, "Capability");
            if (!validationResult.isValid) {
                JsonUtil.sendErrorResponse(response.getWriter(), validationResult.message, 400);
                return;
            }
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            CapXCapSnapshot before = fetchCapXCapSnapshot(conn, relationshipId);
            boolean updated = updateCapabilityRelationship(conn, relationshipId, relationType, targetCapabilityId, description, userId);

            if (updated) {
                if (before != null) {
                    logCapXCapUpdateAudit(conn,
                            before.sourceId,
                            before.targetId, targetCapabilityId,
                            before.relationTypeId, relationType,
                            userId);
                }

                com.google.gson.JsonObject responseJson = new com.google.gson.JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Capability relationship updated successfully");
                response.getWriter().write(responseJson.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Capability relationship not found or update failed", 404);
            }
        }
    }

    // Delete capability relationship
    private void handleDeleteCapabilityRelationship(HttpServletRequest request, HttpServletResponse response, int relationshipId) throws IOException, SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            CapXCapSnapshot before = fetchCapXCapSnapshot(conn, relationshipId);
            boolean deleted = deleteCapabilityRelationship(conn, relationshipId);

            if (deleted) {
                if (before != null) {
                    int userId = UserContextUtil.getCurrentUserId(request);
                    logCapXCapDeleteAudit(conn,
                            before.sourceId,
                            before.targetId,
                            before.relationTypeId,
                            userId);
                }

                com.google.gson.JsonObject responseJson = new com.google.gson.JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Capability relationship deleted successfully");
                response.getWriter().write(responseJson.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Capability relationship not found or delete failed", 404);
            }
        }
    }

    // Helper methods for capability relationships
    private int createCapabilityRelationship(Connection conn, int sourceId, int targetId, int relationType, String description, int userId) throws SQLException {
        String sql = """
            INSERT INTO capability_x_capability (Source_ID, Target_ID, RelationType, Description, LastUpdateUser_ID, CreateDatetime, LastUpdateDatetime)
            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, sourceId);
            stmt.setInt(2, targetId);
            stmt.setInt(3, relationType);
            if (description != null && !description.trim().isEmpty()) {
                stmt.setString(4, description);
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }
            stmt.setInt(5, userId);
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return -1;
    }

    private boolean updateCapabilityRelationship(Connection conn, int relationshipId, int relationType, int targetCapabilityId, String description, int userId) throws SQLException {
        String sql = """
            UPDATE capability_x_capability 
            SET RelationType = ?, Target_ID = ?, Description = ?, LastUpdateUser_ID = ?, LastUpdateDatetime = NOW()
            WHERE ID = ?
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationType);
            stmt.setInt(2, targetCapabilityId);
            if (description != null && !description.trim().isEmpty()) {
                stmt.setString(3, description);
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            stmt.setInt(4, userId);
            stmt.setInt(5, relationshipId);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    private boolean deleteCapabilityRelationship(Connection conn, int relationshipId) throws SQLException {
        String sql = "DELETE FROM capability_x_capability WHERE ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationshipId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    // Get capability relationship types
    private void getCapabilityRelationTypes(HttpServletResponse response) throws IOException, SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT ID as id, PrimaryName as name, PrimaryName as primaryname
                FROM capability_x_capability_relationtype
                ORDER BY PrimaryName
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> rt = new HashMap<>();
                    rt.put("id", rs.getInt("id"));
                    rt.put("name", rs.getString("name"));
                    rt.put("primaryname", rs.getString("primaryname"));
                    relationTypes.add(rt);
                }
            }
        }
        
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (Map<String, Object> rt : relationTypes) {
            com.google.gson.JsonObject rtJson = new com.google.gson.JsonObject();
            rtJson.addProperty("id", (Integer) rt.get("id"));
            rtJson.addProperty("name", (String) rt.get("name"));
            rtJson.addProperty("primaryname", (String) rt.get("primaryname"));
            arr.add(rtJson);
        }
        response.getWriter().write(arr.toString());
    }

    private int getSourceCapabilityId(int relationshipId) {
        String sql = "SELECT Source_ID FROM capability_x_capability WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationshipId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Source_ID");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error looking up source capability for relationship " + relationshipId + ": " + e.getMessage());
        }
        return -1;
    }

    // ---------------------------------------------------------------------
    // Audit history for Capability ↔ Capability relationships (Impact tab)
    //
    // The cross-facet (Capability X System / X Process / ...) audit is written
    // by CapabilityImpactDAO, but capability_x_capability is managed here and
    // historically had NO audit logging. As a result, relationships added in
    // the Capability page > Impact > Capabilities sub-tab did not appear in
    // the object History tab nor in the People Activity Stream expanded table.
    // The helpers below close that gap by writing "Capability X Capability"
    // rows into capability_audit_history for INSERT/UPDATE/DELETE, mirroring
    // the pattern in CapabilityImpactDAO. We log on BOTH ends (source and
    // target capability) so the relationship shows up in either capability's
    // history.
    // ---------------------------------------------------------------------

    private static final String CAP_X_CAP_OBJECT = "Capability X Capability";

    private String getCapabilityPrimaryName(Connection conn, int capabilityId) {
        String sql = "SELECT PrimaryName FROM capability WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching capability name for ID " + capabilityId + ": " + e.getMessage());
        }
        return null;
    }

    private String getCapXCapRelationTypeName(Connection conn, int relationTypeId) {
        String sql = "SELECT PrimaryName FROM capability_x_capability_relationtype WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching cap-x-cap relation type for ID " + relationTypeId + ": " + e.getMessage());
        }
        return null;
    }

    private String getUserFullName(Connection conn, int userId) {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fullName");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user full name for ID " + userId + ": " + e.getMessage());
        }
        return "System";
    }

    /**
     * Write one audit row into capability_audit_history. Designed to mirror the
     * column layout used by CapabilityImpactDAO so existing readers (e.g.
     * HistoryAuditServlet, PersonActivityServlet.calculateEvents) treat these
     * rows the same as System/Process/Glossary relationship rows.
     */
    private void writeCapAuditRow(Connection conn,
                                  int capabilityId,
                                  String event,
                                  String updateType,
                                  String field,
                                  String fromValue,
                                  String toValue,
                                  String author) {
        String sql = "INSERT INTO capability_audit_history " +
                "(id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            ps.setString(2, CAP_X_CAP_OBJECT);
            ps.setString(3, event);
            ps.setString(4, updateType);
            ps.setString(5, field);
            if (fromValue == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, fromValue);
            }
            if (toValue == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, toValue);
            }
            ps.setString(8, author);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error writing Capability X Capability audit row for capability "
                    + capabilityId + ": " + e.getMessage());
        }
    }

    /** Audit a freshly-created capability_x_capability link on both ends. */
    private void logCapXCapInsertAudit(Connection conn, int sourceId, int targetId,
                                       int relationTypeId, int userId) {
        String sourceName = getCapabilityPrimaryName(conn, sourceId);
        String targetName = getCapabilityPrimaryName(conn, targetId);
        String relTypeName = getCapXCapRelationTypeName(conn, relationTypeId);
        String author = getUserFullName(conn, userId);

        // From the source's history: it gained a link to `targetName`.
        writeCapAuditRow(conn, sourceId, "link", "Added", "Capability", null, targetName, author);
        writeCapAuditRow(conn, sourceId, "link", "Added", "RelationType", null, relTypeName, author);

        // From the target's history: it was linked from `sourceName`.
        if (targetId != sourceId) {
            writeCapAuditRow(conn, targetId, "link", "Added", "Capability", null, sourceName, author);
            writeCapAuditRow(conn, targetId, "link", "Added", "RelationType", null, relTypeName, author);
        }
    }

    /** Audit changes to an existing capability_x_capability link. */
    private void logCapXCapUpdateAudit(Connection conn,
                                       int sourceId,
                                       int oldTargetId, int newTargetId,
                                       int oldRelationTypeId, int newRelationTypeId,
                                       int userId) {
        String author = getUserFullName(conn, userId);

        if (oldTargetId != newTargetId) {
            String oldName = getCapabilityPrimaryName(conn, oldTargetId);
            String newName = getCapabilityPrimaryName(conn, newTargetId);
            writeCapAuditRow(conn, sourceId, "edit", "Updated", "Capability", oldName, newName, author);
            // Reflect the change on each target's history too.
            if (oldTargetId > 0 && oldTargetId != sourceId) {
                writeCapAuditRow(conn, oldTargetId, "edit", "Updated", "Capability",
                        getCapabilityPrimaryName(conn, sourceId), null, author);
            }
            if (newTargetId > 0 && newTargetId != sourceId) {
                writeCapAuditRow(conn, newTargetId, "edit", "Updated", "Capability",
                        null, getCapabilityPrimaryName(conn, sourceId), author);
            }
        }

        if (oldRelationTypeId != newRelationTypeId) {
            String oldRt = getCapXCapRelationTypeName(conn, oldRelationTypeId);
            String newRt = getCapXCapRelationTypeName(conn, newRelationTypeId);
            writeCapAuditRow(conn, sourceId, "edit", "Updated", "RelationType", oldRt, newRt, author);
            int counterpartId = (oldTargetId == newTargetId) ? oldTargetId : newTargetId;
            if (counterpartId > 0 && counterpartId != sourceId) {
                writeCapAuditRow(conn, counterpartId, "edit", "Updated", "RelationType", oldRt, newRt, author);
            }
        }
    }

    /** Audit a deletion on both ends of the link. */
    private void logCapXCapDeleteAudit(Connection conn, int sourceId, int targetId,
                                       int relationTypeId, int userId) {
        String sourceName = getCapabilityPrimaryName(conn, sourceId);
        String targetName = getCapabilityPrimaryName(conn, targetId);
        String relTypeName = getCapXCapRelationTypeName(conn, relationTypeId);
        String author = getUserFullName(conn, userId);

        writeCapAuditRow(conn, sourceId, "delete", "Deleted", "Capability", targetName, null, author);
        writeCapAuditRow(conn, sourceId, "delete", "Deleted", "RelationType", relTypeName, null, author);

        if (targetId > 0 && targetId != sourceId) {
            writeCapAuditRow(conn, targetId, "delete", "Deleted", "Capability", sourceName, null, author);
            writeCapAuditRow(conn, targetId, "delete", "Deleted", "RelationType", relTypeName, null, author);
        }
    }

    /** Snapshot of an existing capability_x_capability row, used so UPDATE/DELETE
     *  audit rows can be written with proper "from" values. */
    private static class CapXCapSnapshot {
        int sourceId;
        int targetId;
        int relationTypeId;
    }

    private CapXCapSnapshot fetchCapXCapSnapshot(Connection conn, int relationshipId) {
        String sql = "SELECT Source_ID, Target_ID, RelationType FROM capability_x_capability WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CapXCapSnapshot s = new CapXCapSnapshot();
                    s.sourceId = rs.getInt("Source_ID");
                    s.targetId = rs.getInt("Target_ID");
                    s.relationTypeId = rs.getInt("RelationType");
                    return s;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error snapshotting capability_x_capability row " + relationshipId + ": " + e.getMessage());
        }
        return null;
    }
}