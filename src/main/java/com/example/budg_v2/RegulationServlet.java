package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.model.Regulation;
import com.example.budg_v2.service.RegulationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/regulation/*")
public class RegulationServlet extends HttpServlet {
    private RegulationService regulationService;
    private SegmentDAO segmentDAO;
    private Gson gson;
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    public void init() throws ServletException {
        super.init();
        regulationService = new RegulationService();
        segmentDAO = new SegmentDAO();
        gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        //system.out.println("RegulationServlet: doGet called with pathInfo: " + pathInfo);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            // Get current user ID for segment filtering
            // Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (pathInfo == null || pathInfo.equals("/")) {
                List<Regulation> regulations = userId > 0 
                    ? regulationService.getAllRegulationsBySegmentAccess(userId)
                    : regulationService.getAllRegulations();
                regulations = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        regulations,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Regulation",
                        Regulation::getId);
                response.getWriter().write(gson.toJson(regulations));
            } else if (pathInfo.equals("/list")) {
                // Get regulations for dropdown - filtered by segment access
                List<Regulation> regulations = userId > 0 
                    ? regulationService.getRegulationsForDropdownBySegmentAccess(userId)
                    : regulationService.getRegulationsForDropdown();
                //system.out.println("RegulationServlet /list - regulations count: " + regulations.size() + " for user " + userId);

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Regulation r : regulations) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", r.getId());
                    o.addProperty("primaryname", r.getPrimaryName());
                    o.addProperty("description", r.getDescription());
                    if (r.getParentId() != null) {
                        o.addProperty("parentId", r.getParentId());
                    }
                    arr.add(o);
                }
                response.getWriter().write(arr.toString());
            } else if (pathInfo.equals("/parent-picker")) {
                // Get regulations for parent picker - filtered by segment access
                List<Regulation> regulations = userId > 0 
                    ? regulationService.getRegulationsForDropdownBySegmentAccess(userId)
                    : regulationService.getRegulationsForDropdown();
                //system.out.println("RegulationServlet /parent-picker - regulations count: " + regulations.size() + " for user " + userId);

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Regulation r : regulations) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", r.getId());
                    o.addProperty("primaryname", r.getPrimaryName());
                    o.addProperty("description", r.getDescription());
                    if (r.getParentId() != null) {
                        o.addProperty("parentId", r.getParentId());
                    }
                    arr.add(o);
                }
                response.getWriter().write(arr.toString());
            } else if (pathInfo.equals("/reference-data")) {
                // Get all reference data for dropdowns
                com.google.gson.JsonObject referenceData = new com.google.gson.JsonObject();
                
                // Convert each list to JsonArray
                referenceData.add("viewing", gson.toJsonTree(regulationService.getViewingOptions()));
                referenceData.add("legalAdviceTypes", gson.toJsonTree(regulationService.getLegalAdviceTypes()));
                referenceData.add("regulationMaturity", gson.toJsonTree(regulationService.getRegulationMaturityOptions()));
                referenceData.add("regulationProbability", gson.toJsonTree(regulationService.getRegulationProbabilityOptions()));
                referenceData.add("regulationStatus", gson.toJsonTree(regulationService.getRegulationStatusOptions()));
                referenceData.add("regulationImpactRating", gson.toJsonTree(regulationService.getRegulationImpactRatingOptions()));
                referenceData.add("regulationStage", gson.toJsonTree(regulationService.getRegulationStageOptions()));
                referenceData.add("regulationComplianceLevel", gson.toJsonTree(regulationService.getRegulationComplianceLevelOptions()));
                
                response.getWriter().write(gson.toJson(referenceData));
            } else if (pathInfo.matches("/\\d+/stakeholders")) {
                // Get stakeholders for a regulation
                try {
                    String[] parts = pathInfo.split("/");
                    int id = Integer.parseInt(parts[1]);
                    List<Map<String, Object>> stakeholders = regulationService.getRegulationStakeholders(id);
                    
                    // Add role assignment validation for each stakeholder
                    try (Connection conn = DatabaseConnection.getConnection()) {
                        for (Map<String, Object> stakeholder : stakeholders) {
                            Object peopleIdObj = stakeholder.get("peopleId");
                            Object roleIdObj = stakeholder.get("roleId");
                            
                            if (peopleIdObj != null && roleIdObj != null) {
                                try {
                                    int stakeholderUserId = ((Number) peopleIdObj).intValue();
                                    int roleId = ((Number) roleIdObj).intValue();
                                    
                                    DefaultStakeholderUtil.ValidationResult validation = 
                                        DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, stakeholderUserId, roleId);
                                    
                                    stakeholder.put("roleAssignmentValid", validation.isValid());
                                    if (!validation.isValid()) {
                                        stakeholder.put("roleAssignmentWarning", validation.getWarningMessage());
                                    }
                                    stakeholder.put("isDefaultOnlyAssignment", DefaultStakeholderUtil.isDefaultOnlyStakeholder(conn, roleId));
                                } catch (Exception e) {
                                    // If validation fails, mark as invalid
                                    stakeholder.put("roleAssignmentValid", false);
                                    stakeholder.put("roleAssignmentWarning", "Error validating role assignment");
                                }
                            }
                        }
                    } catch (SQLException e) {
                        // Log error but continue with response
                        System.err.println("Error validating role assignments: " + e.getMessage());
                    }
                    
                    response.getWriter().write(gson.toJson(stakeholders));
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid regulation ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.matches("/\\d+")) {
                // Get regulation by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1)); // Remove leading "/"
                    Regulation regulation = regulationService.getRegulationById(id);
                    if (regulation != null) {
                        if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(regulation.getStatusName())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\":\"This object is not available.\"}");
                            return;
                        }
                        // Check access control for all users (including guests)
                        // GUEST ACCESS CHECK: Only allow public objects in Enterprise segment
                        if (userId <= 0) {
                            // Guest user - check if object is public and in Enterprise segment
                            try {
                                boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Regulation");
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
                            boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Regulation");
                            if (!canAccess) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this regulation.\"}");
                                return;
                            }
                        }

                        JsonObject regulationJson = JsonParser.parseString(gson.toJson(regulation)).getAsJsonObject();
                        SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Regulation");
                        SegmentResponseUtil.applySegmentInfo(regulationJson, segmentInfo, request);
                        response.getWriter().write(regulationJson.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Regulation not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid regulation ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.startsWith("/search/")) {
                // Search regulations
                String searchTerm = pathInfo.substring(8); // Remove "/search/" prefix
                List<Regulation> regulations = userId > 0
                    ? regulationService.searchRegulationsBySegmentAccess(searchTerm, userId)
                    : regulationService.searchRegulations(searchTerm);
                response.getWriter().write(gson.toJson(regulations));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Endpoint not found");
                response.getWriter().write(gson.toJson(error));
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        String pathInfo = request.getPathInfo();
        
        // Check if this is a stakeholder save request
        if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders")) {
            // Extract regulation ID from path
            String[] parts = pathInfo.split("/");
            int regulationId = Integer.parseInt(parts[1]);
            
            // Check if user is admin first (admins bypass all checks)
            if (UserContextUtil.isCurrentUserAdmin(request)) {
                // Admin can always save stakeholders - bypass permission check
                handleSaveStakeholders(request, response, pathInfo);
                return;
            }
            
            // Check Edit permission + stakeholder status for Regulation (for non-admins)
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Regulation", regulationId)) {
                return; // Response already sent
            }
            
            handleSaveStakeholders(request, response, pathInfo);
            return;
        }

        // Check create permission for new regulation creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Regulation")) {
            return; // Response already sent
        }

        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuffer.append(line);
            }

            String jsonData = jsonBuffer.toString();
            //system.out.println("RegulationServlet POST - Received JSON: " + jsonData);
            //system.out.println("RegulationServlet POST - JSON length: " + jsonData.length());
            //system.out.println("RegulationServlet POST - JSON is empty: " + jsonData.trim().isEmpty());

            if (jsonData == null || jsonData.trim().isEmpty()) {
                //system.out.println("RegulationServlet POST - Empty JSON data received");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Empty JSON data received");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Regulation regulation;
            try {
                regulation = gson.fromJson(jsonData, Regulation.class);
                //system.out.println("RegulationServlet POST - Successfully parsed Regulation object");
            } catch (Exception e) {
                //system.out.println("RegulationServlet POST - JSON parsing error: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid JSON format: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            //system.out.println("RegulationServlet POST - Parsed Regulation:");
            //system.out.println("- PrimaryName: " + regulation.getPrimaryName());
            //system.out.println("- Description: " + regulation.getDescription());
            //system.out.println("- ParentId: " + regulation.getParentId());
            //system.out.println("- RefNumber: " + regulation.getRefNumber());
            long segmentIdForName = 1L;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentIdForName = jsonObj.get("segmentId").getAsLong();
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Regulation", regulation.getPrimaryName(), segmentIdForName,
                        null)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "A regulation with this name already exists in this segment.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            // Validate segment hierarchy before creating regulation
            Integer segmentId = 1;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentId = jsonObj.get("segmentId").getAsInt();
                }
            } catch (Exception ignored) {}
            Integer regulationParentId = regulation.getParentId();
            if (regulationParentId != null && regulationParentId > 0) {
                try {
                    var hierarchyResult = segmentValidationService.validateParentChildSegment(regulationParentId, segmentId, "Regulation");
                    if (!hierarchyResult.isValid) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", hierarchyResult.message);
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error validating regulation hierarchy: " + e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Error validating segment hierarchy: " + e.getMessage());
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            int regulationId = regulationService.createRegulation(regulation);
            //system.out.println("RegulationServlet POST - Created regulation with ID: " + regulationId);
            int userId = UserContextUtil.getCurrentUserId(request);
            try {
                segmentDAO.assignObjectToSegment(segmentId, regulationId, "Regulation", userId > 0 ? userId : 1);
                //system.out.println("✅ Regulation " + regulationId + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning regulation to segment: " + e.getMessage());
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Regulation created successfully");
            responseJson.addProperty("id", regulationId);

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("RegulationServlet POST - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            //system.out.println("RegulationServlet POST - SQL Error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error creating regulation: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("RegulationServlet POST - General Error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request data: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    private void handleSaveStakeholders(HttpServletRequest request, HttpServletResponse response, String pathInfo) throws IOException {
        try {
            String[] parts = pathInfo.split("/");
            int regulationId = Integer.parseInt(parts[1]);

            BufferedReader reader = request.getReader();
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuffer.append(line);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = gson.fromJson(jsonBuffer.toString(), Map.class);
            int currentUserId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(request);

            // Validate role assignments before saving
            try (Connection conn = DatabaseConnection.getConnection()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> inserts = (List<Map<String, Object>>) payload.getOrDefault("inserts", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> updates = (List<Map<String, Object>>) payload.getOrDefault("updates", java.util.Collections.emptyList());
                
                // Validate inserts
                for (Map<String, Object> row : inserts) {
                    Object ipidObj = row.get("ipid");
                    Object roleIdObj = row.get("roleId");
                    
                    if (ipidObj != null && roleIdObj != null) {
                        int stakeholderUserId = ((Number) ipidObj).intValue();
                        int roleId = ((Number) roleIdObj).intValue();
                        
                        DefaultStakeholderUtil.ValidationResult validation = 
                            DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, stakeholderUserId, roleId);
                        
                        if (!validation.isValid()) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject error = new JsonObject();
                            error.addProperty("error", validation.getWarningMessage());
                            response.getWriter().write(gson.toJson(error));
                            return;
                        }
                    }
                }
                
                // Validate updates
                for (Map<String, Object> row : updates) {
                    Object ipidObj = row.get("ipid");
                    Object roleIdObj = row.get("roleId");
                    
                    if (ipidObj != null && roleIdObj != null) {
                        int stakeholderUserId = ((Number) ipidObj).intValue();
                        int roleId = ((Number) roleIdObj).intValue();
                        
                        DefaultStakeholderUtil.ValidationResult validation = 
                            DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, stakeholderUserId, roleId);
                        
                        if (!validation.isValid()) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject error = new JsonObject();
                            error.addProperty("error", validation.getWarningMessage());
                            response.getWriter().write(gson.toJson(error));
                            return;
                        }
                    }
                }

                // Validate current user can save (no default-only role blocking)
                java.util.Set<Integer> deleteIds = DefaultStakeholderUtil.extractObjectXPeopleIdsFromDeletes(payload);
                DefaultStakeholderUtil.ValidationResult canSave = DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                        conn, "Regulation", regulationId, currentUserId, deleteIds);
                if (!canSave.isValid()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", canSave.getWarningMessage());
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            regulationService.saveRegulationStakeholders(regulationId, payload, currentUserId);

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Stakeholders saved successfully");
            response.getWriter().write(responseJson.toString());
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid regulation ID");
            response.getWriter().write(gson.toJson(error));
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Regulation ID required for update");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int id = Integer.parseInt(pathInfo.substring(1));
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Regulation", id)) {
                return; // Response already sent
            }
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuffer.append(line);
            }

            String jsonData = jsonBuffer.toString();
            //system.out.println("Received JSON data: " + jsonData);

            Regulation regulation = gson.fromJson(jsonData, Regulation.class);
            regulation.setId(id);
            long effectiveSegmentId = 1L;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    effectiveSegmentId = jsonObj.get("segmentId").getAsLong();
                } else {
                    int cur = segmentDAO.getObjectSegmentId(id, "Regulation");
                    if (cur > 0) {
                        effectiveSegmentId = cur;
                    }
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Regulation", regulation.getPrimaryName(), effectiveSegmentId,
                        id)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "A regulation with this name already exists in this segment.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "Regulation");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Access denied. You don't have permission to edit this regulation.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            boolean success = regulationService.updateRegulation(regulation);

            // Update segment assignment if provided - even if the base update returned false.
            // This fixes the case where the user only changes the segment field.
            boolean segmentUpdated = false;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    Integer segmentId = jsonObj.get("segmentId").getAsInt();
                    int curSegmentId = segmentDAO.getObjectSegmentId(id, "Regulation");
                    if (curSegmentId != segmentId) {
                        // Validate segment move before applying
                        SegmentValidationService.ValidationResult validationResult =
                            segmentValidationService.validateSegmentMove(id, segmentId, "Regulation", null);
                        if (!validationResult.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject error = new JsonObject();
                            error.addProperty("error", validationResult.message);
                            response.getWriter().write(gson.toJson(error));
                            return;
                        }
                        if (curSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(curSegmentId, id, "Regulation", currentUserId > 0 ? currentUserId : 1);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, id, "Regulation", currentUserId > 0 ? currentUserId : 1);
                        System.out.println("✅ Regulation " + id + " segment changed from " + curSegmentId + " to " + segmentId);
                        segmentUpdated = true;
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Error updating regulation segment: " + e.getMessage());
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success || segmentUpdated);
            responseJson.addProperty("message", (success || segmentUpdated) ? "Regulation updated successfully" : "Failed to update regulation");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("RegulationServlet PUT - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error updating regulation: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("Exception in doPut: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request data: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Regulation ID required for deletion");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int id = Integer.parseInt(pathInfo.substring(1));

            boolean success = regulationService.deleteRegulation(id);

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            responseJson.addProperty("message", success ? "Regulation deleted successfully" : "Failed to delete regulation");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error deleting regulation: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request data: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            String pathInfo = request.getPathInfo();
            //system.out.println("RegulationServlet PATCH - Path Info: " + pathInfo);

            if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders/accept")) {
                String[] parts = pathInfo.split("/");
                int regulationId = Integer.parseInt(parts[1]);
                //system.out.println("PATCH - Extracted regulation ID: " + regulationId);

                BufferedReader reader = request.getReader();
                StringBuilder jsonBuffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuffer.append(line);
                }

                String requestBody = jsonBuffer.toString();
                //system.out.println("PATCH - Request body: " + requestBody);

                if (requestBody == null || requestBody.trim().isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Request body is required");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> jsonObject = gson.fromJson(requestBody, Map.class);
                //system.out.println("PATCH - Parsed JSON: " + jsonObject.toString());

                if (!jsonObject.containsKey("objectXPeopleId")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Missing required field: objectXPeopleId is required");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                int objectXPeopleId = ((Double) jsonObject.get("objectXPeopleId")).intValue();
                int currentUserId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(request);
                if (currentUserId <= 0) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "User not authenticated");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                boolean success = regulationService.acceptStakeholderStatus(objectXPeopleId, currentUserId, regulationId);

                if (success) {
                    //system.out.println("PATCH - Stakeholder status accepted successfully");
                    JsonObject successResponse = new JsonObject();
                    successResponse.addProperty("success", true);
                    successResponse.addProperty("message", "Status accepted successfully");
                    response.getWriter().write(gson.toJson(successResponse));
                } else {
                    //system.out.println("PATCH - Failed to accept stakeholder status");
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Failed to accept stakeholder status");
                    response.getWriter().write(gson.toJson(error));
                }
            } else {
                //system.out.println("PATCH - Invalid endpoint: " + pathInfo);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid endpoint");
                response.getWriter().write(gson.toJson(error));
            }
        } catch (SQLException e) {
            System.err.println("PATCH - Database error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            System.err.println("PATCH - Server error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
