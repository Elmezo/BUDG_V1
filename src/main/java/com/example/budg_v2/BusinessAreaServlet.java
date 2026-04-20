package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.BusinessArea;
import com.example.budg_v2.service.BusinessAreaService;
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
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

@WebServlet("/api/business-areas/*")
public class BusinessAreaServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(BusinessAreaServlet.class.getName());
    private BusinessAreaService businessAreaService;
    private SegmentDAO segmentDAO;
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    public void init() {
        this.businessAreaService = new BusinessAreaService();
        this.segmentDAO = new SegmentDAO();
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
                // Return all business areas
                logger.info("BusinessAreaServlet: Getting all business areas (userId: " + userId + ")");
                List<BusinessArea> businessAreas = userId > 0 ? 
                    businessAreaService.getAllBusinessAreas(userId) : 
                    businessAreaService.getAllBusinessAreas();
                businessAreas = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        businessAreas,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "BusinessArea",
                        BusinessArea::getId);
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("count", businessAreas.size());
                
                com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
                for (BusinessArea businessArea : businessAreas) {
                    com.google.gson.JsonObject businessAreaJson = new com.google.gson.JsonObject();
                    businessAreaJson.addProperty("id", businessArea.getId());
                    businessAreaJson.addProperty("primaryName", businessArea.getPrimaryName());
                    businessAreaJson.addProperty("description", businessArea.getDescription());
                    businessAreaJson.addProperty("parentId", businessArea.getParentId());
                    businessAreaJson.addProperty("status", businessArea.getStatus());
                    businessAreaJson.addProperty("lifecycle", businessArea.getLifecycle());
                    businessAreaJson.addProperty("isPublic", businessArea.getIsPublic());
                    dataArray.add(businessAreaJson);
                }
                responseJson.add("data", dataArray);
                logger.info("BusinessAreaServlet: Sending response with " + dataArray.size() + " business areas");
                response.getWriter().write(responseJson.toString());
                
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchBusinessAreas(response, searchQuery.trim());
                } else {
                    getAllBusinessAreas(response);
                }
            } else if ("/hierarchy".equals(pathInfo)) {
                // Return the full set so the relationship hierarchy tree can include
                // children that live in private segments. Inaccessible nodes are
                // masked downstream so their identifying fields are hidden.
                List<BusinessArea> businessAreas = userId > 0
                    ? businessAreaService.getAllBusinessAreasForHierarchy()
                    : businessAreaService.getAllBusinessAreas();

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (BusinessArea ba : businessAreas) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", ba.getId());
                    o.addProperty("primaryName", ba.getPrimaryName());
                    o.addProperty("description", ba.getDescription());
                    o.addProperty("parentId", ba.getParentId());
                    arr.add(o);
                }
                com.example.budg_v2.util.HierarchyAccessMasker.mask(arr, "BusinessArea", userId);
                response.getWriter().write(arr.toString());
            } else if ("/dropdown".equals(pathInfo)) {
                // Return business areas for dropdown/parent picker
                logger.info("BusinessAreaServlet: Getting business areas for dropdown (userId: " + userId + ")");
                List<BusinessArea> businessAreas = userId > 0 ? 
                    businessAreaService.getBusinessAreasForDropdown(userId) : 
                    businessAreaService.getBusinessAreasForDropdown();
                businessAreas = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        businessAreas,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "BusinessArea",
                        BusinessArea::getId);
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("count", businessAreas.size());
                
                com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
                for (BusinessArea businessArea : businessAreas) {
                    com.google.gson.JsonObject businessAreaJson = new com.google.gson.JsonObject();
                    businessAreaJson.addProperty("id", businessArea.getId());
                    businessAreaJson.addProperty("primaryName", businessArea.getPrimaryName());
                    businessAreaJson.addProperty("description", businessArea.getDescription());
                    dataArray.add(businessAreaJson);
                }
                responseJson.add("data", dataArray);
                response.getWriter().write(responseJson.toString());
                
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                try {
                    int id = Integer.parseInt(idParam);
                    getBusinessAreaById(request, response, id);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid business area ID: " + idParam, 400);
                }
            } else {
                getAllBusinessAreas(response);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "BusinessAreaServlet GET error: " + e.getMessage(), e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Error retrieving business areas: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        // Check create permission for new business area creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Business Area")) {
            return; // Response already sent
        }

        try {
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryName");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parentId");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer lifecycle = JsonUtil.getJsonInt(jsonData, "lifecycle");
            Integer BUDGViewing = JsonUtil.getJsonInt(jsonData, "budgViewing");
            Integer createdById = JsonUtil.getJsonInt(jsonData, "createdById");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastUpdateUserId");
            
            // Get from request context if still null
            if (createdById == null || lastUpdateUserId == null) {
                int userIdFromContext = UserContextUtil.getCurrentUserId(request);
                if (userIdFromContext > 0) {
                    if (createdById == null) {
                        createdById = userIdFromContext;
                    }
                    if (lastUpdateUserId == null) {
                        lastUpdateUserId = userIdFromContext;
                    }
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                    return;
                }
            }

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            // Validate parent/segment compatibility during create
            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentId == null) {
                segmentId = 1; // Enterprise default
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "BusinessArea", primaryName.trim(),
                        segmentId.longValue(), null)) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Business area name already exists in this segment", 400);
                    return;
                }
            }
            SegmentValidationService.ValidationResult createValidation =
                segmentValidationService.validateParentChildSegment(parentId, segmentId, "BusinessArea");
            if (!createValidation.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonUtil.sendErrorResponse(response.getWriter(), createValidation.message, 400);
                return;
            }

            BusinessArea businessArea = new BusinessArea();
            businessArea.setPrimaryName(primaryName.trim());
            businessArea.setDescription(description);
            businessArea.setParentId(parentId);
            businessArea.setStatus(status);
            businessArea.setLifecycle(lifecycle);
            businessArea.setIsPublic(BUDGViewing);
            businessArea.setCreateUserId(createdById);
            businessArea.setLastUpdateUserId(lastUpdateUserId);

            logger.info("BusinessAreaServlet: Creating business area: " + businessArea.getPrimaryName());
            int businessAreaId = businessAreaService.createBusinessArea(businessArea);
            
            if (businessAreaId > 0) {
                logger.info("BusinessAreaServlet: Business area created successfully with ID: " + businessAreaId);
                
                // Assign business area to segment
                try {
                    segmentDAO.assignObjectToSegment(segmentId, businessAreaId, "BusinessArea", lastUpdateUserId != null ? lastUpdateUserId : 1);
                    //system.out.println("✅ BusinessArea " + businessAreaId + " assigned to segment " + segmentId);
                } catch (Exception e) {
                    System.err.println("❌ Error assigning business area to segment: " + e.getMessage());
                }
                
                // Assign creator role and create stakeholder audit
                //system.out.println("✅ Calling assignCreatorRoleAndAudit...");
                try {
                    businessAreaService.assignCreatorRoleAndAudit(businessAreaId, lastUpdateUserId);
                } catch (Exception e) {
                    System.err.println("❌ Error in assignCreatorRoleAndAudit: " + e.getMessage());
                    e.printStackTrace();
                    // Continue - don't fail the entire save
                }
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Business area created successfully");
                responseJson.addProperty("id", businessAreaId);
                response.getWriter().write(responseJson.toString());
            } else {
                logger.severe("BusinessAreaServlet: Failed to create business area");
                JsonUtil.sendErrorResponse(response.getWriter(), "Failed to create business area", 500);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "BusinessAreaServlet POST error: " + e.getMessage(), e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating business area: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Business area ID is required", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            int id;
            try {
                id = Integer.parseInt(idParam);
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid business area ID: " + idParam, 400);
                return;
            }
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Business Area", id)) {
                return; // Response already sent
            }

            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryName");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parentId");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer lifecycle = JsonUtil.getJsonInt(jsonData, "lifecycle");
            Integer BUDGViewing = JsonUtil.getJsonInt(jsonData, "budgViewing");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastUpdateUserId");
            
            if (lastUpdateUserId == null) {
                int userIdFromContext = UserContextUtil.getCurrentUserId(request);
                if (userIdFromContext > 0) {
                    lastUpdateUserId = userIdFromContext;
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                    return;
                }
            }

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "BusinessArea");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonUtil.sendErrorResponse(response.getWriter(), "Access denied. You don't have permission to edit this business area.", 403);
                    return;
                }
            }

            BusinessArea businessArea = new BusinessArea();
            businessArea.setId(id);
            businessArea.setPrimaryName(primaryName.trim());
            businessArea.setDescription(description);
            businessArea.setParentId(parentId);
            businessArea.setStatus(status);
            businessArea.setLifecycle(lifecycle);
            businessArea.setIsPublic(BUDGViewing);
            businessArea.setLastUpdateUserId(lastUpdateUserId);

            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            int currentSegmentId = segmentDAO.getObjectSegmentId(id, "BusinessArea");
            int effectiveSegmentId = segmentId != null ? segmentId : currentSegmentId;

            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "BusinessArea", primaryName.trim(),
                        effectiveSegmentId > 0 ? effectiveSegmentId : 1L, id)) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Business area name already exists in this segment", 400);
                    return;
                }
            }

            // Validate parent change even when segment is unchanged
            SegmentValidationService.ValidationResult parentValidation =
                segmentValidationService.validateParentChildSegment(parentId, effectiveSegmentId, "BusinessArea");
            if (!parentValidation.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonUtil.sendErrorResponse(response.getWriter(), parentValidation.message, 400);
                return;
            }

            boolean success = businessAreaService.updateBusinessArea(businessArea);
            // Update segment assignment if provided (even if the base object update returned false).
            // This fixes the case where the user only changes the segment field.
            boolean segmentUpdated = false;
            if (segmentId != null) {
                try {
                    if (currentSegmentId != segmentId) {
                        SegmentValidationService.ValidationResult validationResult =
                            segmentValidationService.validateSegmentMove(id, segmentId, "BusinessArea", parentId);
                        if (!validationResult.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonUtil.sendErrorResponse(response.getWriter(), validationResult.message, 400);
                            return;
                        }

                        if (currentSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(currentSegmentId, id, "BusinessArea", lastUpdateUserId != null ? lastUpdateUserId : 1);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, id, "BusinessArea", lastUpdateUserId != null ? lastUpdateUserId : 1);
                        segmentUpdated = true;
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error updating business area segment", e);
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error updating business area segment: " + e.getMessage(), 500);
                    return;
                }
            }

            if (success || segmentUpdated) {
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Business area updated successfully");
                response.getWriter().write(responseJson.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Business area not found or update failed", 404);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "BusinessAreaServlet PUT error: " + e.getMessage(), e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating business area: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Business area ID is required", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            int id;
            try {
                id = Integer.parseInt(idParam);
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid business area ID: " + idParam, 400);
                return;
            }

            boolean success = businessAreaService.deleteBusinessArea(id, request);
            if (success) {
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Business area deleted successfully");
                response.getWriter().write(responseJson.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Business area not found or deletion failed", 404);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "BusinessAreaServlet DELETE error: " + e.getMessage(), e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting business area: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllBusinessAreas(HttpServletResponse response) throws IOException, SQLException {
        List<BusinessArea> businessAreas = businessAreaService.getAllBusinessAreas();
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("success", true);
        responseJson.addProperty("count", businessAreas.size());
        
        com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
        for (BusinessArea businessArea : businessAreas) {
            com.google.gson.JsonObject businessAreaJson = new com.google.gson.JsonObject();
            businessAreaJson.addProperty("id", businessArea.getId());
            businessAreaJson.addProperty("primaryName", businessArea.getPrimaryName());
            businessAreaJson.addProperty("description", businessArea.getDescription());
            businessAreaJson.addProperty("parentId", businessArea.getParentId());
            businessAreaJson.addProperty("status", businessArea.getStatus());
            businessAreaJson.addProperty("lifecycle", businessArea.getLifecycle());
            businessAreaJson.addProperty("isPublic", businessArea.getIsPublic());
            dataArray.add(businessAreaJson);
        }
        responseJson.add("data", dataArray);
        response.getWriter().write(responseJson.toString());
    }

    private void getBusinessAreaById(HttpServletRequest request, HttpServletResponse response, int id) throws IOException, SQLException {
        BusinessArea businessArea = businessAreaService.getBusinessAreaById(id);
        if (businessArea != null) {
            if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(businessArea.getStatusName())) {
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
                    boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Business_Area");
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
                boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "BusinessArea");
                if (!canAccess) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this business area.\"}");
                    return;
                }
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            
            com.google.gson.JsonObject businessAreaJson = new com.google.gson.JsonObject();
            businessAreaJson.addProperty("id", businessArea.getId());
            businessAreaJson.addProperty("primaryName", businessArea.getPrimaryName());
            businessAreaJson.addProperty("description", businessArea.getDescription());
            businessAreaJson.addProperty("parentId", businessArea.getParentId());
            businessAreaJson.addProperty("status", businessArea.getStatus());
            businessAreaJson.addProperty("lifecycle", businessArea.getLifecycle());
            businessAreaJson.addProperty("isPublic", businessArea.getIsPublic());
            businessAreaJson.addProperty("CreateDatetime", businessArea.getCreateDatetime() != null ? businessArea.getCreateDatetime().toString() : null);
            businessAreaJson.addProperty("createDatetime", businessArea.getCreateDatetime() != null ? businessArea.getCreateDatetime().toString() : null);
            businessAreaJson.addProperty("LastUpdateDatetime", businessArea.getLastUpdateDatetime() != null ? businessArea.getLastUpdateDatetime().toString() : null);
            businessAreaJson.addProperty("lastUpdateDatetime", businessArea.getLastUpdateDatetime() != null ? businessArea.getLastUpdateDatetime().toString() : null);
            businessAreaJson.addProperty("Create_UserID", businessArea.getCreateUserId());
            businessAreaJson.addProperty("createdby_id", businessArea.getCreateUserId());
            businessAreaJson.addProperty("LastUpdate_UserID", businessArea.getLastUpdateUserId());

            // Include current segment metadata for UI using SegmentResponseUtil
            try {
                SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "BusinessArea");
                System.out.println("🔍 DEBUG doGet - BusinessArea ID=" + id + ", resolved segmentId=" + segmentInfo.id() + ", segmentName=" + segmentInfo.name());
                SegmentResponseUtil.applySegmentInfo(businessAreaJson, segmentInfo, request);
            } catch (Exception e) {
                System.err.println("❌ Error loading business area segment: " + e.getMessage());
                e.printStackTrace();
                // Don't default to Enterprise - return -1 (Not Specified) instead
                businessAreaJson.addProperty("segmentId", -1);
                businessAreaJson.addProperty("segmentName", "Not Specified");
            }

            responseJson.add("data", businessAreaJson);
            
            // DEBUG: Log the exact JSON being sent to frontend
            String jsonResponse = responseJson.toString();
            System.out.println("🔍 DEBUG - Full JSON response being sent:");
            System.out.println(jsonResponse);
            
            response.getWriter().write(jsonResponse);
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Business area not found", 404);
        }
    }

    private void searchBusinessAreas(HttpServletResponse response, String searchTerm) throws IOException, SQLException {
        List<BusinessArea> businessAreas = businessAreaService.searchBusinessAreas(searchTerm);
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("success", true);
        responseJson.addProperty("count", businessAreas.size());
        responseJson.addProperty("searchQuery", searchTerm);
        
        com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
        for (BusinessArea businessArea : businessAreas) {
            com.google.gson.JsonObject businessAreaJson = new com.google.gson.JsonObject();
            businessAreaJson.addProperty("id", businessArea.getId());
            businessAreaJson.addProperty("primaryName", businessArea.getPrimaryName());
            businessAreaJson.addProperty("description", businessArea.getDescription());
            businessAreaJson.addProperty("parentId", businessArea.getParentId());
            businessAreaJson.addProperty("status", businessArea.getStatus());
            businessAreaJson.addProperty("lifecycle", businessArea.getLifecycle());
            businessAreaJson.addProperty("isPublic", businessArea.getIsPublic());
            dataArray.add(businessAreaJson);
        }
        responseJson.add("data", dataArray);
        response.getWriter().write(responseJson.toString());
    }

}
