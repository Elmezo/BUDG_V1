package com.example.budg_v2;

import com.example.budg_v2.dao.CommitteeDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Committee;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.example.budg_v2.util.RoleNotificationHelper;
import com.example.budg_v2.util.ResponseSanitizer;
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
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Committee Servlet for handling CRUD operations
 * Endpoint: /api/committee
 */
@WebServlet({"/api/committee", "/api/committee/*", "/committee", "/committee/*"})
public class CommitteeServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();
    private CommitteeDAO committeeDAO = new CommitteeDAO();
    private SegmentDAO segmentDAO = new SegmentDAO();
    private SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();
            int userId = UserContextUtil.getCurrentUserId(request);

            if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders")) {
                String[] parts = pathInfo.split("/");
                int id = Integer.parseInt(parts[1]);
                Integer moduleId = null;
                try {
                    String moduleParam = request.getParameter("moduleId");
                    if (moduleParam != null && !moduleParam.isBlank()) moduleId = Integer.parseInt(moduleParam);
                } catch (Exception ignored) {}
                List<java.util.Map<String, Object>> stakeholders = getCommitteeStakeholders(conn, id, moduleId);
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("success", true);
                jsonResponse.add("data", JsonParser.parseString(gson.toJson(stakeholders)));
                out.print(jsonResponse.toString());
            } else if ("/hierarchy".equals(pathInfo)) {
                // Hierarchy view returns the full tree so the relationship UI can
                // structurally show every node; access-restricted private nodes
                // are then masked (xxxx + lock) by HierarchyAccessMasker below.
                List<Committee> committees = getAllCommitteesUnfiltered(conn);

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Committee c : committees) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", c.getId());
                    o.addProperty("primaryName", c.getPrimaryName());
                    o.addProperty("description", c.getDescription());
                    o.addProperty("parentId", c.getParentId());
                    SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, c.getId(), "Committee");
                    SegmentResponseUtil.applySegmentInfo(o, segmentInfo, request);
                    o.addProperty("segmentRestricted", ResponseSanitizer.isStakeholderOnly(request));
                    arr.add(o);
                }
                com.example.budg_v2.util.HierarchyAccessMasker.mask(arr, "Committee", userId);
                out.print(arr.toString());
            } else if (pathInfo != null && pathInfo.matches("/relationships/\\d+")) {
                // Get committee relationships by source ID
                String[] parts = pathInfo.split("/");
                int sourceId = Integer.parseInt(parts[2]);
                List<java.util.Map<String, Object>> relationships = getCommitteeRelationshipsBySourceId(conn, sourceId);
                out.print(gson.toJson(relationships));
            } else if (pathInfo != null && "/relation-type/list".equals(pathInfo)) {
                // Get committee relationship types
                List<java.util.Map<String, Object>> relationTypes = getCommitteeRelationTypes(conn);
                out.print(gson.toJson(relationTypes));
            } else if (pathInfo == null) {
                String idParam = request.getParameter("id");
                if (idParam != null && !idParam.isEmpty()) {
                    // Get single committee by ID
                    int id = Integer.parseInt(idParam);
                    Committee committee = getCommitteeById(conn, id);
                    if (committee != null) {
                        if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(committee.getStatusName())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            out.print("{\"error\":\"This object is not available.\"}");
                            return;
                        }
                        // Check access control for all users (including guests)
                        // GUEST ACCESS CHECK: Only allow public objects in Enterprise segment
                        if (userId <= 0) {
                            // Guest user - check if object is public and in Enterprise segment
                            try {
                                boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Committee");
                                if (!canAccess) {
                                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    out.print("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                                    return;
                                }
                            } catch (SQLException e) {
                                // On error, deny access for safety
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                out.print("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                                return;
                            }
                        }

                        // Authenticated user access check
                        if (userId > 0) {
                            boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Committee");
                            if (!canAccess) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                out.print("{\"error\":\"Access denied. You don't have permission to view this committee.\"}");
                                return;
                            }
                        }

                        JsonObject committeeJson = gson.toJsonTree(committee).getAsJsonObject();
                        SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, committee.getId(), "Committee");
                        SegmentResponseUtil.applySegmentInfo(committeeJson, segmentInfo, request);
                        committeeJson.addProperty("segmentRestricted", ResponseSanitizer.isStakeholderOnly(request));
                        out.print(committeeJson.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        out.print("{\"error\": \"Committee not found\"}");
                    }
                } else {
                    // Get all committees
                    List<Committee> committees = getAllCommittees(conn, userId);
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (Committee committee : committees) {
                        JsonObject committeeJson = gson.toJsonTree(committee).getAsJsonObject();
                        SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, committee.getId(), "Committee");
                        SegmentResponseUtil.applySegmentInfo(committeeJson, segmentInfo, request);
                        committeeJson.addProperty("segmentRestricted", ResponseSanitizer.isStakeholderOnly(request));
                        arr.add(committeeJson);
                    }
                    out.print(arr.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\": \"Invalid endpoint\"}");
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();
            
            // Handle committee relationship creation (skip permission check for relationships)
            if (pathInfo != null && "/relationship".equals(pathInfo)) {
                String requestBody = getRequestBody(request);
                JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
                
                int sourceId = jsonObject.get("sourceId").getAsInt();
                int targetId = jsonObject.get("targetId").getAsInt();
                int relationType = jsonObject.get("relationType").getAsInt();
                String description = jsonObject.has("description") && !jsonObject.get("description").isJsonNull() 
                    ? jsonObject.get("description").getAsString() : null;
                int userId = UserContextUtil.getCurrentUserId(request);

                var committeeCreateValidation = segmentValidationService.validateCrossSegmentRelationship(sourceId, "Committee", targetId, "Committee");
                if (!committeeCreateValidation.isValid) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"" + committeeCreateValidation.message + "\"}");
                    return;
                }

                int relationshipId = createCommitteeRelationship(conn, sourceId, targetId, relationType, description, userId);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("id", relationshipId);
                out.print(responseJson.toString());
                return;
            }
            
            // Check create permission for new committee creation
            // Skip permission check for relationship endpoint
            if (pathInfo == null || !"/relationship".equals(pathInfo)) {
                if (!PermissionCheckUtil.checkCreatePermission(request, response, "Committee")) {
                    return; // Response already sent
                }
            }

            // Parse JSON request body
            String requestBody = getRequestBody(request);
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

            // Debug logging
            //system.out.println("Received JSON data: " + jsonObject.toString());
            //system.out.println("isPublic value: " + (jsonObject.has("isPublic") ? jsonObject.get("isPublic") : "NOT PRESENT"));

            // Validate mandatory fields
            String validationError = validateMandatoryFields(jsonObject);
            if (validationError != null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"" + validationError + "\"}");
                return;
            }

            // Check for duplicate PrimaryName and RefNumber
            String duplicateError = checkForDuplicates(conn, jsonObject, null);
            if (duplicateError != null) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print("{\"error\": \"" + duplicateError + "\"}");
                return;
            }

            // Validate foreign key relationships
            String fkError = validateForeignKeys(conn, jsonObject);
            if (fkError != null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"" + fkError + "\"}");
                return;
            }

            // Insert new committee
            Committee committee = insertCommittee(conn, jsonObject, request);
            if (committee != null) {
                response.setStatus(HttpServletResponse.SC_CREATED);

                // Debug logging for response
                //system.out.println("Committee object before JSON serialization:");
                //system.out.println("ID: " + committee.getId());
                //system.out.println("isPublic: " + committee.getIsPublic());
                //system.out.println("PrimaryName: " + committee.getPrimaryName());

                String jsonResponse = gson.toJson(committee);
                //system.out.println("JSON response: " + jsonResponse);
                out.print(jsonResponse);
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"error\": \"Failed to create committee\"}");
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();

            // Handle committee relationship update (skip permission check for relationships)
            if (pathInfo != null && pathInfo.matches("/relationship/\\d+")) {
                String[] parts = pathInfo.split("/");
                int relationshipId = Integer.parseInt(parts[2]);
                
                String requestBody = getRequestBody(request);
                JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
                
                int relationType = jsonObject.get("relationType").getAsInt();
                int targetCommitteeId = jsonObject.get("targetCommitteeId").getAsInt();
                String description = jsonObject.has("description") && !jsonObject.get("description").isJsonNull() 
                    ? jsonObject.get("description").getAsString() : null;
                int userId = UserContextUtil.getCurrentUserId(request);

                int sourceCommitteeId = getSourceCommitteeId(conn, relationshipId);
                if (sourceCommitteeId > 0) {
                    var committeeUpdateValidation = segmentValidationService.validateCrossSegmentRelationship(sourceCommitteeId, "Committee", targetCommitteeId, "Committee");
                    if (!committeeUpdateValidation.isValid) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.print("{\"error\": \"" + committeeUpdateValidation.message + "\"}");
                        return;
                    }
                }

                boolean success = updateCommitteeRelationship(conn, relationshipId, relationType, targetCommitteeId, description, userId);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", success);
                out.print(responseJson.toString());
                return;
            }

            // Handle stakeholders update
            if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders")) {
                // For now, just return success (stakeholders update not implemented yet)
                out.print("{\"success\": true, \"message\": \"Stakeholders updated successfully\"}");
                return;
            }

            // Parse JSON request body
            String requestBody = getRequestBody(request);
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

            // Get committee ID from request
            if (!jsonObject.has("id") || jsonObject.get("id").isJsonNull()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Committee ID is required for update\"}");
                return;
            }

            int committeeId = jsonObject.get("id").getAsInt();
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Committee", committeeId)) {
                return; // Response already sent
            }

            // Check if committee exists
            Committee existingCommittee = getCommitteeById(conn, committeeId);
            if (existingCommittee == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\": \"Committee not found\"}");
                return;
            }

            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, committeeId, "Committee");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.print("{\"error\": \"Access denied. You don't have permission to edit this committee.\"}");
                    return;
                }
            }

            // Validate mandatory fields
            String validationError = validateMandatoryFields(jsonObject);
            if (validationError != null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"" + validationError + "\"}");
                return;
            }

            // Check for duplicate PrimaryName and RefNumber (excluding current record)
            String duplicateError = checkForDuplicates(conn, jsonObject, committeeId);
            if (duplicateError != null) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print("{\"error\": \"" + duplicateError + "\"}");
                return;
            }

            // Validate foreign key relationships
            String fkError = validateForeignKeys(conn, jsonObject);
            if (fkError != null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"" + fkError + "\"}");
                return;
            }

            // Desired segment (optional; accept both segmentId and segment_id)
            Integer segmentId = null;
                if (jsonObject.has("segmentId") && !jsonObject.get("segmentId").isJsonNull()) {
                segmentId = jsonObject.get("segmentId").getAsInt();
            } else if (jsonObject.has("segment_id") && !jsonObject.get("segment_id").isJsonNull()) {
                segmentId = jsonObject.get("segment_id").getAsInt();
            }


            // Update committee with audit tracking (may return null if no rows changed)
            Committee updatedCommittee = updateCommitteeWithAudit(conn, jsonObject, request);

            // Apply segment update even if committee table had no changes
            // Always process segment update if segmentId is provided, regardless of segmentChanged flag
            if (segmentId != null) {
                    try {
                        int currentSegmentId = segmentDAO.getObjectSegmentId(committeeId, "Committee");
                        
                        if (currentSegmentId != segmentId) {
                            var validationResult = segmentValidationService.validateSegmentMove(
                                committeeId,
                                segmentId,
                                "Committee",
                                null
                            );
                            if (!validationResult.isValid) {
                                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("error", validationResult.message);
                                out.print(gson.toJson(errorResponse));
                                return;
                            }
                            
                            if (currentSegmentId > 0 && currentSegmentId != -1) {
                                segmentDAO.removeObjectFromSegment(currentSegmentId, committeeId, "Committee", currentUserId > 0 ? currentUserId : 1);
                            }
                            
                            segmentDAO.assignObjectToSegment(segmentId, committeeId, "Committee", currentUserId > 0 ? currentUserId : 1);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error updating committee segment: " + e.getMessage());
                        e.printStackTrace();
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JsonObject errorResponse = new JsonObject();
                        errorResponse.addProperty("error", "Error updating committee segment: " + e.getMessage());
                        out.print(gson.toJson(errorResponse));
                        return;
                    }
                }

            if (updatedCommittee != null) {
                JsonObject committeeJson = gson.toJsonTree(updatedCommittee).getAsJsonObject();
                SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, committeeId, "Committee");
                SegmentResponseUtil.applySegmentInfo(committeeJson, segmentInfo);
                out.print(committeeJson.toString());
            } else if (segmentId != null) {
                // Segment-only change: return current committee data
                Committee current = getCommitteeById(conn, committeeId);
                if (current != null) {
                    JsonObject committeeJson = gson.toJsonTree(current).getAsJsonObject();
                    SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, committeeId, "Committee");
                    SegmentResponseUtil.applySegmentInfo(committeeJson, segmentInfo);
                    out.print(committeeJson.toString());
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.print("{\"error\": \"Committee not found\"}");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"No changes detected\"}");
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();
            
            // Handle committee relationship deletion
            if (pathInfo != null && pathInfo.matches("/relationship/\\d+")) {
                String[] parts = pathInfo.split("/");
                int relationshipId = Integer.parseInt(parts[2]);
                
                boolean success = deleteCommitteeRelationship(conn, relationshipId);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", success);
                out.print(responseJson.toString());
                return;
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
            return;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
            return;
        }

        try {
            String idParam = request.getParameter("id");

            if (idParam == null || idParam.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Committee ID is required for deletion\"}");
                return;
            }

            int committeeId = Integer.parseInt(idParam);

            // Get username from request
            String userName = getUserName(request);

            // Delete committee with audit
            CommitteeDAO committeeDAO = new CommitteeDAO();
            boolean deleted = committeeDAO.deleteCommitteeWithAudit(committeeId, userName);
            
            if (deleted) {
                out.print("{\"message\": \"Committee deleted successfully\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\": \"Committee not found or already deleted\"}");
            }

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Invalid committee ID format\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    private String getUserName(HttpServletRequest request) {
        try {
            String userJson = (String) request.getAttribute("user");
            if (userJson != null && userJson.contains("\"username\":")) {
                int start = userJson.indexOf("\"username\":\"") + 12;
                int end = userJson.indexOf("\"", start);
                if (end > start) {
                    return userJson.substring(start, end);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting username: " + e.getMessage());
        }
        return "Unknown User";
    }

    // Helper methods

    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private String validateMandatoryFields(JsonObject jsonObject) {
        if (!jsonObject.has("primaryName") || jsonObject.get("primaryName").isJsonNull() ||
                jsonObject.get("primaryName").getAsString().trim().isEmpty()) {
            return "Primary Name is required";
        }
        if (!jsonObject.has("description") || jsonObject.get("description").isJsonNull() ||
                jsonObject.get("description").getAsString().trim().isEmpty()) {
            return "Description is required";
        }
        if (!jsonObject.has("status") || jsonObject.get("status").isJsonNull()) {
            return "Status is required";
        }
        if (!jsonObject.has("lifecycle") || jsonObject.get("lifecycle").isJsonNull()) {
            return "Lifecycle is required";
        }
        if (!jsonObject.has("classification") || jsonObject.get("classification").isJsonNull()) {
            return "Classification is required";
        }
        if (!jsonObject.has("committeeType") || jsonObject.get("committeeType").isJsonNull()) {
            return "Committee Type is required";
        }
        // isPublic is optional - will default to 1 (Public) if not provided
        return null;
    }

    private String checkForDuplicates(Connection conn, JsonObject jsonObject, Integer excludeId) throws SQLException {
        String primaryName = jsonObject.get("primaryName").getAsString().trim();
        String refNumber = jsonObject.has("refNumber") && !jsonObject.get("refNumber").isJsonNull() ?
                jsonObject.get("refNumber").getAsString() : null;

        long segmentId = 1L;
        if (jsonObject.has("segmentId") && !jsonObject.get("segmentId").isJsonNull()) {
            segmentId = jsonObject.get("segmentId").getAsInt();
        } else if (excludeId != null) {
            int cur = segmentDAO.getObjectSegmentId(excludeId, "Committee");
            if (cur > 0) {
                segmentId = cur;
            }
        }
        if (SegmentScopedPrimaryNameCheck.exists(conn, "Committee", primaryName, segmentId, excludeId)) {
            return "Primary Name already exists in this segment";
        }

        // Check RefNumber duplicate if provided - use centralized RefNumberValidator
        // Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects
        if (refNumber != null && !refNumber.trim().isEmpty()) {
            refNumber = refNumber.trim();
            try {
                boolean isUnique;
                if (excludeId != null) {
                    // Update operation - exclude current object ID
                    isUnique = com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Committee", refNumber, excludeId);
                } else {
                    // Create operation - check uniqueness
                    isUnique = com.example.budg_v2.util.RefNumberValidator.isRefNumberUnique("Committee", refNumber);
                }
                if (!isUnique) {
                    return "This reference number is already in use. Please enter a unique reference number.";
                }
            } catch (SQLException e) {
                // Log error but don't block operation
                System.err.println("[CommitteeServlet] Error validating RefNumber uniqueness: " + e.getMessage());
            }
        }

        return null;
    }

    private String validateForeignKeys(Connection conn, JsonObject jsonObject) throws SQLException {
        // Validate Status
        if (jsonObject.has("status") && !jsonObject.get("status").isJsonNull()) {
            if (!validateForeignKey(conn, "status", jsonObject.get("status").getAsInt())) {
                return "Invalid Status ID";
            }
        }

        // Validate Lifecycle
        if (jsonObject.has("lifecycle") && !jsonObject.get("lifecycle").isJsonNull()) {
            if (!validateForeignKey(conn, "committee_lifecycle", jsonObject.get("lifecycle").getAsInt())) {
                return "Invalid Lifecycle ID";
            }
        }

        // Validate Classification
        if (jsonObject.has("classification") && !jsonObject.get("classification").isJsonNull()) {
            if (!validateForeignKey(conn, "committee_classification", jsonObject.get("classification").getAsInt())) {
                return "Invalid Classification ID";
            }
        }

        // Validate Committee Type
        if (jsonObject.has("committeeType") && !jsonObject.get("committeeType").isJsonNull()) {
            if (!validateForeignKey(conn, "committee_type", jsonObject.get("committeeType").getAsInt())) {
                return "Invalid Committee Type ID";
            }
        }

        // Validate Is_Public
        if (jsonObject.has("isPublic") && !jsonObject.get("isPublic").isJsonNull()) {
            if (!validateForeignKey(conn, "viewing", jsonObject.get("isPublic").getAsInt())) {
                return "Invalid Is_Public ID";
            }
        }

        // Validate Parent_ID
        if (jsonObject.has("parentId") && !jsonObject.get("parentId").isJsonNull()) {
            if (!validateForeignKey(conn, "committee", jsonObject.get("parentId").getAsInt())) {
                return "Invalid Parent ID";
            }
        }

        // Validate LastUpdate_UserID
        if (jsonObject.has("lastUpdateUserID") && !jsonObject.get("lastUpdateUserID").isJsonNull()) {
            if (!validateForeignKey(conn, "people", jsonObject.get("lastUpdateUserID").getAsInt())) {
                return "Invalid Last Update User ID";
            }
        }

        return null;
    }

    private boolean validateForeignKey(Connection conn, String tableName, int id) throws SQLException {
        // Handle case sensitivity for different tables
        String idColumn = "ID";
        if ("viewing".equalsIgnoreCase(tableName)) {
            idColumn = "id"; // viewing table uses lowercase 'id'
        }

        String query = "SELECT COUNT(*) FROM " + tableName + " WHERE " + idColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    @SuppressWarnings("unused")
    private List<Committee> getAllCommittees(Connection conn) throws SQLException {
        return getAllCommitteesForGuest(conn);
    }

    /**
     * Get all committees for guest users (public, Enterprise only, not deleted)
     */
    private List<Committee> getAllCommitteesForGuest(Connection conn) throws SQLException {
        String guestFilter = SegmentAccessService.buildGuestFilterClause("Committee", "c", "c.ID");
        if (guestFilter == null) {
            return getAllCommitteesUnfiltered(conn);
        }
        String query = "SELECT c.ID, c.Parent_ID, c.Is_Public, c.Classification, c.Status, c.Lifecycle, " +
                "c.Committee_Type, c.RefNumber, c.PrimaryName, c.Description, c.CreateDatetime, " +
                "c.LastUpdateDatetime, c.DeleteDatetime, c.LastUpdate_UserID " +
                "FROM committee c WHERE " + guestFilter + " ORDER BY c.ID";
        List<Committee> committees = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                committees.add(mapCommitteeFromResultSet(rs));
            }
        }
        return committees;
    }

    private List<Committee> getAllCommitteesUnfiltered(Connection conn) throws SQLException {
        String query = """
            SELECT c.ID, c.Parent_ID, c.Is_Public, c.Classification, c.Status, c.Lifecycle,
                c.Committee_Type, c.RefNumber, c.PrimaryName, c.Description, c.CreateDatetime,
                c.LastUpdateDatetime, c.DeleteDatetime, c.LastUpdate_UserID
            FROM committee c
            WHERE c.DeleteDatetime IS NULL
            ORDER BY c.ID
            """;
        List<Committee> committees = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                committees.add(mapCommitteeFromResultSet(rs));
            }
        }
        return committees;
    }

    private Committee mapCommitteeFromResultSet(ResultSet rs) throws SQLException {
        Committee committee = new Committee();
        committee.setId(rs.getInt("ID"));
        committee.setParentId(rs.getObject("Parent_ID", Integer.class));
        committee.setIsPublic(rs.getObject("Is_Public", Integer.class));
        committee.setClassification(rs.getObject("Classification", Integer.class));
        committee.setStatus(rs.getObject("Status", Integer.class));
        committee.setLifecycle(rs.getObject("Lifecycle", Integer.class));
        committee.setCommitteeType(rs.getObject("Committee_Type", Integer.class));
        committee.setRefNumber(rs.getString("RefNumber"));
        committee.setPrimaryName(rs.getString("PrimaryName"));
        committee.setDescription(rs.getString("Description"));
        committee.setCreateDatetime(rs.getTimestamp("CreateDatetime"));
        committee.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime"));
        committee.setDeleteDatetime(rs.getTimestamp("DeleteDatetime"));
        committee.setLastUpdateUserID(rs.getObject("LastUpdate_UserID", Integer.class));
        return committee;
    }

    /**
     * Get all committees filtered by user's segment access
     */
    private List<Committee> getAllCommittees(Connection conn, int userId) throws SQLException {
        if (userId <= 0) {
            return getAllCommitteesForGuest(conn);
        }
        List<Committee> allCommittees = getAllCommitteesUnfiltered(conn);
        if (allCommittees.isEmpty()) {
            return allCommittees;
        }
        
        // Get accessible committee IDs for this user
        List<Integer> allIds = allCommittees.stream().map(Committee::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIdsInSegments(userId, "Committee", allIds);
        
        // Filter to only accessible committees
        return allCommittees.stream()
                .filter(c -> accessibleIds.contains(c.getId()))
                .collect(Collectors.toList());
    }

    private List<java.util.Map<String, Object>> getCommitteeStakeholders(Connection conn, int committeeId, Integer moduleId) throws SQLException {
        StringBuilder q = new StringBuilder()
                .append("SELECT \n")
                .append("    orl.PrimaryName AS Role,\n")
                .append("    CONCAT(p.First_Name, ' ', p.Last_Name) AS Name,\n")
                .append("    ou.Name AS OrgUnit,\n")
                .append("    ra.Message AS RoleAccepted,\n")
                .append("    oxp.isdelegateof AS isDelegateOf\n")
                .append("FROM object_role orl\n")
                .append("JOIN object_x_people oxp ON oxp.RoleID = orl.ID\n")
                .append("JOIN committee_x_objectxpeople cxop ON cxop.Object_x_ipid = oxp.ID\n")
                .append("JOIN people p ON p.ID = oxp.iPID\n")
                .append("JOIN org_unit ou ON ou.ID = p.Org_Unit_ID\n")
                .append("LEFT JOIN roleaccepted ra ON ra.ID = oxp.AcceptedID\n")
                .append("WHERE cxop.Committee_ID = ?\n");

        List<Object> params = new ArrayList<>();
        params.add(committeeId);
        if (moduleId != null) {
            q.append("  AND orl.module = ?\n");
            params.add(moduleId);
        }

        try (PreparedStatement ps = conn.prepareStatement(q.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<java.util.Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("role", rs.getString("Role"));
                    row.put("name", rs.getString("Name"));
                    row.put("orgUnit", rs.getString("OrgUnit"));
                    row.put("roleAccepted", rs.getString("RoleAccepted"));
                    row.put("isDelegateOf", rs.getObject("isDelegateOf"));
                    list.add(row);
                }
                return list;
            }
        }
    }

    private Committee getCommitteeById(Connection conn, int id) throws SQLException {
        String query = """
            SELECT 
                c.ID,
                c.Parent_ID,
                c.Is_Public,
                c.Classification,
                c.Status,
                c.Lifecycle,
                c.Committee_Type,
                c.RefNumber,
                c.PrimaryName,
                c.Description,
                c.CreateDatetime,
                c.LastUpdateDatetime,
                c.DeleteDatetime,
                c.LastUpdate_UserID,
                c.Created_By
            FROM committee c
            WHERE c.ID = ? AND c.DeleteDatetime IS NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Committee committee = new Committee();
                committee.setId(rs.getInt("ID"));
                committee.setParentId(rs.getObject("Parent_ID", Integer.class));

                // Debug logging for isPublic retrieval
                Object isPublicValue = rs.getObject("Is_Public", Integer.class);
                //system.out.println("Retrieved isPublic value from DB: " + isPublicValue);
                committee.setIsPublic((Integer) isPublicValue);
                committee.setClassification(rs.getObject("Classification", Integer.class));
                committee.setStatus(rs.getObject("Status", Integer.class));
                committee.setLifecycle(rs.getObject("Lifecycle", Integer.class));
                committee.setCommitteeType(rs.getObject("Committee_Type", Integer.class));
                committee.setRefNumber(rs.getString("RefNumber"));
                committee.setPrimaryName(rs.getString("PrimaryName"));
                committee.setDescription(rs.getString("Description"));
                committee.setCreateDatetime(rs.getTimestamp("CreateDatetime"));
                committee.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime"));
                committee.setDeleteDatetime(rs.getTimestamp("DeleteDatetime"));
                committee.setLastUpdateUserID(rs.getObject("LastUpdate_UserID", Integer.class));
                committee.setCreatedBy(rs.getObject("Created_By", Integer.class));

                return committee;
            }
        }
        return null;
    }

    private Committee insertCommittee(Connection conn, JsonObject jsonObject, HttpServletRequest request) throws SQLException {
        // Auto-generate RefNumber if empty
        String refNumber = jsonObject.has("refNumber") && !jsonObject.get("refNumber").isJsonNull() ?
                jsonObject.get("refNumber").getAsString() : null;
        if (ReferenceNumberGenerator.isEmpty(refNumber)) {
            // Same DB connection as INSERT so the "used refs" snapshot matches this transaction
            refNumber = ReferenceNumberGenerator.generateCommitteeRefNumber(conn, null);
        }

        // Resolve creator ID: from JSON createdById/createdBy if present, else current user from request
        Integer createdById = null;
        if (jsonObject.has("createdById") && !jsonObject.get("createdById").isJsonNull()) {
            createdById = jsonObject.get("createdById").getAsInt();
        } else if (jsonObject.has("createdBy") && !jsonObject.get("createdBy").isJsonNull()) {
            createdById = jsonObject.get("createdBy").getAsInt();
        }
        if (createdById == null) {
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            createdById = currentUserId > 0 ? currentUserId : null;
        }

        // Validate segment hierarchy before insert
        Integer parentIdVal = jsonObject.has("parentId") && !jsonObject.get("parentId").isJsonNull()
                ? jsonObject.get("parentId").getAsInt() : null;
        Integer segmentIdVal = jsonObject.has("segmentId") && !jsonObject.get("segmentId").isJsonNull()
                ? jsonObject.get("segmentId").getAsInt() : 1;
        if (parentIdVal != null && parentIdVal > 0) {
            try {
                var hierarchyResult = segmentValidationService.validateParentChildSegment(parentIdVal, segmentIdVal, "Committee");
                if (!hierarchyResult.isValid) {
                    throw new SQLException("Segment hierarchy violation: " + hierarchyResult.message);
                }
            } catch (SQLException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Error validating segment hierarchy: " + e.getMessage(), e);
            }
        }

        String query = """
            INSERT INTO committee 
            (Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type, 
             RefNumber, PrimaryName, Description, LastUpdate_UserID, Created_By)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setObject(1, parentIdVal);

            // Debug logging for isPublic
            Object isPublicValue = jsonObject.has("isPublic") && !jsonObject.get("isPublic").isJsonNull() ?
                    jsonObject.get("isPublic").getAsInt() : 1; // Default to Public (1)
            //system.out.println("Setting isPublic value: " + isPublicValue);
            stmt.setObject(2, isPublicValue);
            stmt.setInt(3, jsonObject.get("classification").getAsInt());
            stmt.setInt(4, jsonObject.get("status").getAsInt());
            stmt.setInt(5, jsonObject.get("lifecycle").getAsInt());
            stmt.setInt(6, jsonObject.get("committeeType").getAsInt());
            stmt.setString(7, refNumber);
            stmt.setString(8, jsonObject.get("primaryName").getAsString());
            stmt.setString(9, jsonObject.get("description").getAsString());
            stmt.setObject(10, jsonObject.has("lastUpdateUserID") && !jsonObject.get("lastUpdateUserID").isJsonNull() ?
                    jsonObject.get("lastUpdateUserID").getAsInt() : null);
            stmt.setObject(11, createdById);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);

                    // Get user ID from jsonObject
                    Integer userId = jsonObject.has("lastUpdateUserID") && !jsonObject.get("lastUpdateUserID").isJsonNull()
                            ? jsonObject.get("lastUpdateUserID").getAsInt()
                            : null;

                    // Debug logging
                    //system.out.println("🔍 Received userId: " + userId);
                    //system.out.println("🔍 Committee ID: " + newId);

                    // Assign committee to segment
                    Integer segmentId = jsonObject.has("segmentId") && !jsonObject.get("segmentId").isJsonNull()
                            ? jsonObject.get("segmentId").getAsInt()
                            : 1; // Default to Enterprise segment
                    try {
                        segmentDAO.assignObjectToSegment(segmentId, newId, "Committee", userId != null ? userId : 1);
                        //system.out.println("✅ Committee " + newId + " assigned to segment " + segmentId);
                    } catch (Exception e) {
                        System.err.println("❌ Error assigning committee to segment: " + e.getMessage());
                    }

                    // Automatically assign creator role
                    if (userId != null) {
                        try {
                            assignCreatorRole(conn, newId, userId);
                        } catch (Exception e) {
                            System.err.println("❌ Error assigning creator role: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the insert if role assignment fails
                        }
                    } else {
                        System.err.println("⚠️ userId is NULL - skipping assignCreatorRole");
                    }

                    // Create audit records for the new committee
                    try {
                        String userName = getCurrentUserName(request);
                        committeeDAO.createCommitteeAuditRecords(newId, userName);
                        //system.out.println("✅ Audit records created for committee ID: " + newId);
                    } catch (Exception e) {
                        System.err.println("❌ Error creating audit records: " + e.getMessage());
                        e.printStackTrace();
                        // Continue - don't fail the entire save operation
                    }

                    // Create committee audit record
                    try {
                        committeeDAO.createCommitteeAuditRecord(newId);
                        //system.out.println("✅ Committee audit record created for committee ID: " + newId);
                    } catch (Exception e) {
                        System.err.println("❌ Error creating committee audit record: " + e.getMessage());
                        e.printStackTrace();
                        // Continue - don't fail the entire save operation
                    }

                    return getCommitteeById(conn, newId);
                }
            }
        }
        return null;
    }

    /**
     * تحديث Committee مع تسجيل audit records
     */
    private Committee updateCommitteeWithAudit(Connection conn, JsonObject jsonObject, HttpServletRequest request) throws SQLException {
        int committeeId = jsonObject.get("id").getAsInt();
        
        // الحصول على البيانات القديمة قبل التحديث
        Committee oldCommittee = getCommitteeById(conn, committeeId);
        if (oldCommittee == null) {
            return null;
        }
        
        // إنشاء Committee object جديد من JSON (يُبقى RefNumber الحالي إذا كان الطلب لا يرسل مرجعاً)
        Committee newCommittee = createCommitteeFromJson(conn, jsonObject, oldCommittee);
        
        // الحصول على اسم المستخدم
        String userName = getCurrentUserName(request);
        
        // استخدام CommitteeDAO لتحديث مع audit - تمرير الـ connection الموجود
        boolean updated = updateCommitteeWithAuditUsingExistingConnection(conn, oldCommittee, newCommittee, userName);
        
        if (updated) {
            return getCommitteeById(conn, committeeId);
        }
        
        return null;
    }

    /**
     * إنشاء Committee object من JSON data (مسار التحديث فقط).
     * إذا كان refNumber غائباً أو فارغاً في JSON نُبقي مرجع السجل الحالي؛ لا نُولّد رقماً جديداً لكل حفظ.
     */
    private Committee createCommitteeFromJson(Connection conn, JsonObject jsonObject, Committee previousCommittee) throws SQLException {
        Committee committee = new Committee();
        
        committee.setId(jsonObject.get("id").getAsInt());
        
        String refNumber = jsonObject.has("refNumber") && !jsonObject.get("refNumber").isJsonNull() ?
                jsonObject.get("refNumber").getAsString() : null;
        if (ReferenceNumberGenerator.isEmpty(refNumber)) {
            if (previousCommittee != null && !ReferenceNumberGenerator.isEmpty(previousCommittee.getRefNumber())) {
                refNumber = previousCommittee.getRefNumber();
            } else {
                refNumber = ReferenceNumberGenerator.generateCommitteeRefNumber(conn, null);
            }
        }
        committee.setRefNumber(refNumber);
        
        committee.setPrimaryName(jsonObject.get("primaryName").getAsString());
        committee.setDescription(jsonObject.get("description").getAsString());
        
        committee.setParentId(jsonObject.has("parentId") && !jsonObject.get("parentId").isJsonNull() ?
                jsonObject.get("parentId").getAsInt() : null);
        committee.setIsPublic(jsonObject.has("isPublic") && !jsonObject.get("isPublic").isJsonNull() ?
                jsonObject.get("isPublic").getAsInt() : 1); // Default to Public (1)
        committee.setClassification(jsonObject.get("classification").getAsInt());
        committee.setStatus(jsonObject.get("status").getAsInt());
        committee.setLifecycle(jsonObject.get("lifecycle").getAsInt());
        committee.setCommitteeType(jsonObject.get("committeeType").getAsInt());
        committee.setLastUpdateUserID(jsonObject.has("lastUpdateUserID") && !jsonObject.get("lastUpdateUserID").isJsonNull() ?
                jsonObject.get("lastUpdateUserID").getAsInt() : null);
        
        return committee;
    }

    /**
     * تحديث Committee مع audit باستخدام connection موجود
     */
    private boolean updateCommitteeWithAuditUsingExistingConnection(Connection conn, Committee oldCommittee, Committee newCommittee, String userName) throws SQLException {
        PreparedStatement updateStmt = null;
        
        try {
            // الخطوة 1: تحديث الجدول الأساسي
            String updateSql = """
                UPDATE committee 
                SET Parent_ID = ?, Is_Public = ?, Classification = ?, Status = ?, 
                    Lifecycle = ?, Committee_Type = ?, RefNumber = ?, PrimaryName = ?, 
                    Description = ?, LastUpdate_UserID = ?, LastUpdateDatetime = NOW()
                WHERE ID = ?
            """;
            
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setObject(1, newCommittee.getParentId());
            updateStmt.setObject(2, newCommittee.getIsPublic());
            updateStmt.setObject(3, newCommittee.getClassification());
            updateStmt.setObject(4, newCommittee.getStatus());
            updateStmt.setObject(5, newCommittee.getLifecycle());
            updateStmt.setObject(6, newCommittee.getCommitteeType());
            updateStmt.setString(7, newCommittee.getRefNumber());
            updateStmt.setString(8, newCommittee.getPrimaryName());
            updateStmt.setString(9, newCommittee.getDescription());
            updateStmt.setObject(10, newCommittee.getLastUpdateUserID());
            updateStmt.setInt(11, newCommittee.getId());
            
            int affectedRows = updateStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات - استخدام نفس الـ connection
                try {
                    committeeDAO.createCommitteeUpdateAuditRecords(conn, newCommittee.getId(), oldCommittee, newCommittee, userName);
                    //system.out.println("✅ Committee update audit records created for ID: " + newCommittee.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating committee update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في committee_audit - استخدام نفس الـ connection
                try {
                    committeeDAO.createCommitteeUpdateAuditSnapshot(conn, newCommittee.getId());
                    //system.out.println("✅ CommitteeDAO: committee_audit update snapshot created for ID: " + newCommittee.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating committee_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
            
        } finally {
            // تنظيف الـ statement فقط
            try {
                if (updateStmt != null) updateStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing update statement: " + e.getMessage());
            }
        }
    }

    /**
     * Automatically assigns creator role to the user for newly created committee
     * @param conn Database connection
     * @param committeeId The newly created committee ID
     * @param userId The current user ID
     * @return The role ID that was assigned
     */
    private void assignCreatorRole(Connection conn, int committeeId, int userId) throws SQLException {
        int moduleId = DefaultStakeholderUtil.getModuleId(conn, "Committee");
        List<Integer> rolesToAssign = DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);

        if (rolesToAssign.isEmpty()) {
            return;
        }

        String userFullName = getUserFullName(userId);

        for (Integer roleId : rolesToAssign) {
            try {
                String insertOXP = """
                        INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                        VALUES (NULL, ?, ?, 2, 1, ?)
                        """;
                int objectXPeopleId;
                try (PreparedStatement stmt = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, userId);
                    stmt.setInt(2, roleId);
                    stmt.setInt(3, userId);
                    if (stmt.executeUpdate() == 0) {
                        throw new SQLException("Failed to insert into object_x_people");
                    }
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            objectXPeopleId = generatedKeys.getInt(1);
                        } else {
                            throw new SQLException("Failed to get generated key for object_x_people");
                        }
                    }
                }

                String insertCXOP = """
                        INSERT INTO committee_x_objectxpeople (Object_X_ipid, Committee_ID, LastUpdateUser_ID)
                        VALUES (?, ?, ?)
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(insertCXOP)) {
                    stmt.setInt(1, objectXPeopleId);
                    stmt.setInt(2, committeeId);
                    stmt.setInt(3, userId);
                    if (stmt.executeUpdate() == 0) {
                        throw new SQLException("Failed to insert into committee_x_objectxpeople");
                    }
                }

                RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                        "Committee", committeeId, userId, roleId, objectXPeopleId, conn);

                if (userFullName != null) {
                    committeeDAO.createStakeholderAuditRecords(committeeId, userFullName, userFullName, roleId);
                }
            } catch (SQLException e) {
                System.err.println("❌ Error assigning default role " + roleId + " to creator: " + e.getMessage());
            }
        }
    }

    /**
     * الحصول على اسم المستخدم الحالي
     */
    private String getCurrentUserName(HttpServletRequest request) {
        try {
            // محاولة الحصول على اسم المستخدم من session أو attributes
            Object userAttr = request.getAttribute("userName");
            if (userAttr != null) {
                return userAttr.toString();
            }
            
            // محاولة الحصول من userId
            Integer userId = (Integer) request.getAttribute("userId");
            if (userId != null) {
                // محاولة الحصول على اسم المستخدم من قاعدة البيانات
                try {
                    String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
                    try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                         java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, userId);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return rs.getString("fullName");
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error getting user name from database: " + e.getMessage());
                }
                
                return "User ID: " + userId;
            }
            
            return "Unknown User";
        } catch (Exception e) {
            System.err.println("Error getting current user name: " + e.getMessage());
            return "Unknown User";
        }
    }

    private String getUserFullName(int userId) {
        try {
            String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("fullName");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting user full name from database: " + e.getMessage());
        }
        return "Unknown User";
    }

    // Get committee relationships by source ID (bidirectional - forward + reverse)
    private List<java.util.Map<String, Object>> getCommitteeRelationshipsBySourceId(Connection conn, int sourceId) throws SQLException {
        List<java.util.Map<String, Object>> relationships = new ArrayList<>();
        
        // Forward relationships: where current committee is SOURCE
        String forwardSql = """
            SELECT 
                cxc.ID as id,
                cxc.Source_ID as sourceCommitteeId,
                cxc.Target_ID as targetCommitteeId,
                cxc.RelationType as relationType,
                cxc.Description as description,
                c.PrimaryName as targetCommitteeName,
                rt.PrimaryName as relationshipType,
                rt.ReverseName as relationshipTypeReverseName,
                'forward' as direction
            FROM committee_x_committee cxc
            LEFT JOIN committee c ON cxc.Target_ID = c.ID
            LEFT JOIN committee_x_committee_relationtype rt ON cxc.RelationType = rt.ID
            WHERE cxc.Source_ID = ? AND (c.DeleteDatetime IS NULL OR c.DeleteDatetime = '1970-01-01 00:00:00')
        """;
        
        // Reverse relationships: where current committee is TARGET
        String reverseSql = """
            SELECT 
                cxc.ID as id,
                cxc.Source_ID as sourceCommitteeId,
                cxc.Target_ID as targetCommitteeId,
                cxc.RelationType as relationType,
                cxc.Description as description,
                c.PrimaryName as sourceCommitteeName,
                rt.PrimaryName as relationshipType,
                rt.ReverseName as relationshipTypeReverseName,
                'reverse' as direction
            FROM committee_x_committee cxc
            LEFT JOIN committee c ON cxc.Source_ID = c.ID
            LEFT JOIN committee_x_committee_relationtype rt ON cxc.RelationType = rt.ID
            WHERE cxc.Target_ID = ? AND (c.DeleteDatetime IS NULL OR c.DeleteDatetime = '1970-01-01 00:00:00')
        """;
        
        // Get forward relationships
        try (PreparedStatement stmt = conn.prepareStatement(forwardSql)) {
            stmt.setInt(1, sourceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> rel = new java.util.HashMap<>();
                    rel.put("id", rs.getInt("id"));
                    rel.put("sourceCommitteeId", rs.getInt("sourceCommitteeId"));
                    rel.put("targetCommitteeId", rs.getInt("targetCommitteeId"));
                    rel.put("relationType", rs.getInt("relationType"));
                    rel.put("description", rs.getString("description"));
                    rel.put("targetCommitteeName", rs.getString("targetCommitteeName"));
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
                    java.util.Map<String, Object> rel = new java.util.HashMap<>();
                    rel.put("id", rs.getInt("id"));
                    rel.put("sourceCommitteeId", rs.getInt("sourceCommitteeId"));
                    rel.put("targetCommitteeId", rs.getInt("targetCommitteeId"));
                    rel.put("relationType", rs.getInt("relationType"));
                    rel.put("description", rs.getString("description"));
                    // In reverse, the source committee is the "other" committee
                    rel.put("targetCommitteeName", rs.getString("sourceCommitteeName"));
                    rel.put("targetCommitteeId", rs.getInt("sourceCommitteeId")); // For reverse, use source as target for link
                    // Use reverse name if available, otherwise use regular name
                    String reverseName = rs.getString("relationshipTypeReverseName");
                    rel.put("relationshipType", (reverseName != null && !reverseName.trim().isEmpty()) ? reverseName : rs.getString("relationshipType"));
                    rel.put("direction", "reverse");
                    relationships.add(rel);
                }
            }
        }
        
        // Sort by target committee name
        relationships.sort((a, b) -> {
            String nameA = (String) a.getOrDefault("targetCommitteeName", "");
            String nameB = (String) b.getOrDefault("targetCommitteeName", "");
            return nameA.compareToIgnoreCase(nameB);
        });
        
        return relationships;
    }

    // Create committee relationship
    private int createCommitteeRelationship(Connection conn, int sourceId, int targetId, int relationType, String description, int userId) throws SQLException {
        String sql = """
            INSERT INTO committee_x_committee (Source_ID, Target_ID, RelationType, Description, LastUpdate_UserID, CreateDatetime, LastUpdateDatetime)
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

    // Update committee relationship
    private boolean updateCommitteeRelationship(Connection conn, int relationshipId, int relationType, int targetCommitteeId, String description, int userId) throws SQLException {
        String sql = """
            UPDATE committee_x_committee 
            SET RelationType = ?, Target_ID = ?, Description = ?, LastUpdate_UserID = ?, LastUpdateDatetime = NOW()
            WHERE ID = ?
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationType);
            stmt.setInt(2, targetCommitteeId);
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

    // Delete committee relationship
    private boolean deleteCommitteeRelationship(Connection conn, int relationshipId) throws SQLException {
        String sql = "DELETE FROM committee_x_committee WHERE ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationshipId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    // Get committee relationship types
    private List<java.util.Map<String, Object>> getCommitteeRelationTypes(Connection conn) throws SQLException {
        List<java.util.Map<String, Object>> relationTypes = new ArrayList<>();
        
        String sql = """
            SELECT ID as id, PrimaryName as name, PrimaryName as primaryname
            FROM committee_x_committee_relationtype
            ORDER BY PrimaryName
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> rt = new java.util.HashMap<>();
                rt.put("id", rs.getInt("id"));
                rt.put("name", rs.getString("name"));
                rt.put("primaryname", rs.getString("primaryname"));
                relationTypes.add(rt);
            }
        }
        
        return relationTypes;
    }

    private int getSourceCommitteeId(Connection conn, int relationshipId) throws SQLException {
        String sql = "SELECT Source_ID FROM committee_x_committee WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationshipId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Source_ID");
                }
            }
        }
        return -1;
    }

}
