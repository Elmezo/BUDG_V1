package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.model.Policy;
import com.example.budg_v2.model.PolicyType;
import com.example.budg_v2.model.PolicyLifecycleStatus;
import com.example.budg_v2.service.PolicyService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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

@WebServlet("/api/policy/*")
public class PolicyServlet extends HttpServlet {
    private PolicyService policyService;
    private SegmentDAO segmentDAO;
    private SegmentValidationService segmentValidationService;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        policyService = new PolicyService();
        segmentDAO = new SegmentDAO();
        segmentValidationService = new SegmentValidationService();
        gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        //system.out.println("PolicyServlet: doGet called with pathInfo: " + pathInfo);
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
                // Get all policies - filtered by segment access
                List<Policy> policies = userId > 0 
                    ? policyService.getAllPoliciesBySegmentAccess(userId)
                    : policyService.getAllPolicies();
                response.getWriter().write(gson.toJson(policies));
            } else if (pathInfo.equals("/list")) {
                // Get policies for dropdown - filtered by segment access
                List<Policy> policies = userId > 0 
                    ? policyService.getPoliciesForDropdownBySegmentAccess(userId)
                    : policyService.getPoliciesForDropdown();
                policies = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        policies,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Policy",
                        Policy::getId);
                //system.out.println("PolicyServlet /list - policies count: " + policies.size() + " for user " + userId);

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Policy p : policies) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId());
                    o.addProperty("primaryname", p.getPrimaryName());
                    o.addProperty("description", p.getDescription());
                    o.addProperty("refnumber", p.getRefNumber());
                    if (p.getParentId() != null) {
                        o.addProperty("parentId", p.getParentId());
                    }
                    arr.add(o);
                    //system.out.println("PolicyServlet: Added policy to response: " + p.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if (pathInfo.equals("/parent-picker")) {
                // Get policies for parent picker - filtered by segment access
                List<Policy> policies = userId > 0 
                    ? policyService.getPoliciesForDropdownBySegmentAccess(userId)
                    : policyService.getPoliciesForDropdown();
                //system.out.println("PolicyServlet /parent-picker - policies count: " + policies.size() + " for user " + userId);

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Policy p : policies) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId());
                    o.addProperty("primaryname", p.getPrimaryName());
                    o.addProperty("description", p.getDescription());
                    o.addProperty("refnumber", p.getRefNumber());
                    if (p.getParentId() != null) {
                        o.addProperty("parentId", p.getParentId());
                    }
                    arr.add(o);
                    //system.out.println("PolicyServlet: Added policy to parent picker: " + p.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if (pathInfo.matches("/\\d+")) {
                // Get policy by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1)); // Remove leading "/"
                    Policy policy = policyService.getPolicyById(id);
                    if (policy != null) {
                        if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(policy.getStatusName())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\":\"This object is not available.\"}");
                            return;
                        }
                        if (!enforcePolicyViewAccess(userId, id, response)) {
                            return;
                        }

                        JsonObject policyJson = com.google.gson.JsonParser.parseString(gson.toJson(policy)).getAsJsonObject();
                        SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Policy");
                        SegmentResponseUtil.applySegmentInfo(policyJson, segmentInfo, request);
                        response.getWriter().write(policyJson.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Policy not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid policy ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.equals("/policy-type")) {
                // Get policy types
                //system.out.println("PolicyServlet: Handling /policy-type request");
                try {
                    List<PolicyType> policyTypes = policyService.getPolicyTypes();
                    //system.out.println("PolicyServlet: Found " + policyTypes.size() + " policy types");
                    response.getWriter().write(gson.toJson(policyTypes));
                } catch (Exception e) {
                    //system.out.println("PolicyServlet: Error loading policy types: " + e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Failed to load policy types");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.equals("/lifecycle/list")) {
                // Get policy lifecycle statuses
                //system.out.println("PolicyServlet: Handling /lifecycle/list request");
                try {
                    List<PolicyLifecycleStatus> lifecycleStatuses = policyService.getPolicyLifecycleStatuses();
                    //system.out.println("PolicyServlet: Found " + lifecycleStatuses.size() + " lifecycle statuses");
                    response.getWriter().write(gson.toJson(lifecycleStatuses));
                } catch (Exception e) {
                    //system.out.println("PolicyServlet: Error loading lifecycle statuses: " + e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Failed to load lifecycle statuses");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.startsWith("/search/")) {
                // Search policies
                String searchTerm = pathInfo.substring(8); // Remove "/search/" prefix
                List<Policy> policies = policyService.searchPolicies(searchTerm);
                response.getWriter().write(gson.toJson(policies));
            } else if (pathInfo.startsWith("/hierarchy/")) {
                // Get policy hierarchy
                try {
                    int id = Integer.parseInt(pathInfo.substring(11)); // Remove "/hierarchy/" prefix
                    List<java.util.Map<String, Object>> hierarchy = policyService.getPolicyHierarchy(id);
                    response.getWriter().write(gson.toJson(hierarchy));
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid policy ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.startsWith("/relationships/")) {
                // Get policy relationships
                try {
                    int id = Integer.parseInt(pathInfo.substring(15)); // Remove "/relationships/" prefix
                    List<java.util.Map<String, Object>> relationships = policyService.getPolicyRelationshipsBySourceId(id);
                    response.getWriter().write(gson.toJson(relationships));
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid policy ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.startsWith("/relationship/")) {
                // Delete policy relationship
                try {
                    int relationshipId = Integer.parseInt(pathInfo.substring(13)); // Remove "/relationship/" prefix
                    boolean success = policyService.deletePolicyRelationship(relationshipId);
                    JsonObject responseJson = new JsonObject();
                    responseJson.addProperty("success", success);
                    response.getWriter().write(gson.toJson(responseJson));
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid relationship ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.startsWith("/")) {
                // Get policy by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1));
                    Policy policy = policyService.getPolicyById(id);
                    if (policy != null) {
                        if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(policy.getStatusName())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\":\"This object is not available.\"}");
                            return;
                        }
                        if (!enforcePolicyViewAccess(userId, id, response)) {
                            return;
                        }

                        JsonObject policyJson = com.google.gson.JsonParser.parseString(gson.toJson(policy)).getAsJsonObject();
                        SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Policy");
                        SegmentResponseUtil.applySegmentInfo(policyJson, segmentInfo, request);
                        response.getWriter().write(policyJson.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Policy not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid policy ID");
                    response.getWriter().write(gson.toJson(error));
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    private boolean enforcePolicyViewAccess(int userId, int id, HttpServletResponse response) throws IOException, SQLException {
        // Guests can only access public objects in Enterprise segment.
        if (userId <= 0) {
            try {
                boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Policy");
                if (!canAccess) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                    return false;
                }
            } catch (SQLException e) {
                // On error, deny access for safety.
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                return false;
            }
        }

        if (userId > 0) {
            boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Policy");
            if (!canAccess) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this policy.\"}");
                return false;
            }
        }

        return true;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // Check create permission - Admin/Super Admin bypass, regular users need "New" permission
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Policy")) {
            return; // Response already sent by checkCreatePermission
        }

        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuffer.append(line);
            }

            String jsonData = jsonBuffer.toString();
            //system.out.println("PolicyServlet POST - Received JSON: " + jsonData);
            //system.out.println("PolicyServlet POST - JSON length: " + jsonData.length());
            //system.out.println("PolicyServlet POST - JSON is empty: " + jsonData.trim().isEmpty());

            if (jsonData == null || jsonData.trim().isEmpty()) {
                //system.out.println("PolicyServlet POST - Empty JSON data received");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Empty JSON data received");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Policy policy;
            try {
                policy = gson.fromJson(jsonData, Policy.class);
                //system.out.println("PolicyServlet POST - Successfully parsed Policy object");
            } catch (Exception e) {
                //system.out.println("PolicyServlet POST - JSON parsing error: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid JSON format: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            //system.out.println("PolicyServlet POST - Parsed Policy:");
            //system.out.println("- PrimaryName: " + policy.getPrimaryName());
            //system.out.println("- Description: " + policy.getDescription());
            //system.out.println("- Internal: " + policy.getInternal());
            //system.out.println("- URL: " + policy.getUrl());
            //system.out.println("- EffectiveDate: " + policy.getEffectiveDate());
            //system.out.println("- EndDate: " + policy.getEndDate());
            //system.out.println("- ParentId: " + policy.getParentId());
            //system.out.println("- RefNumber: " + policy.getRefNumber());
            //system.out.println("- PolicyType: " + policy.getPolicyType());
            //system.out.println("- Status: " + policy.getStatus());
            //system.out.println("- LifecycleStatus: " + policy.getLifecycleStatus());
            //system.out.println("- IsPublic: " + policy.getIsPublic());

            // Validate segment hierarchy before creating policy
            Integer segmentId = 1; // Default to Enterprise segment
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentId = jsonObj.get("segmentId").getAsInt();
                }
            } catch (Exception ignored) {}
            
            // Validate hierarchy: parent-child relationships must stay within the same segment
            Integer parentId = policy.getParentId();
            if (parentId != null && parentId > 0) {
                try {
                    var hierarchyValidation = segmentValidationService.validateParentChildSegment(parentId, segmentId, "Policy");
                    if (!hierarchyValidation.isValid) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", hierarchyValidation.message);
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error validating policy hierarchy: " + e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Error validating segment hierarchy: " + e.getMessage());
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            if (policy.getPrimaryName() != null && !policy.getPrimaryName().trim().isEmpty()) {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    if (SegmentScopedPrimaryNameCheck.exists(conn, "Policy", policy.getPrimaryName().trim(),
                            segmentId.longValue(), null)) {
                        throw new IllegalArgumentException("Policy Name already exists in this segment");
                    }
                }
            }

            int policyId = policyService.createPolicy(policy, request);
            //system.out.println("PolicyServlet POST - Created policy with ID: " + policyId);

            // Assign policy to segment
            Integer userId = policy.getLastUpdateUserId();
            try {
                segmentDAO.assignObjectToSegment(segmentId, policyId, "Policy", userId != null ? userId : 1);
                //system.out.println("✅ Policy " + policyId + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning policy to segment: " + e.getMessage());
                // Continue - don't fail the entire save
            }

            // Automatically assign creator role
            if (userId != null) {
                try {
                    assignCreatorRole(policyId, userId);
                } catch (Exception e) {
                    System.err.println("❌ Error assigning creator role: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the policy creation if role assignment fails
                }
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Policy created successfully");
            responseJson.addProperty("id", policyId);

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("PolicyServlet POST - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            //system.out.println("PolicyServlet POST - SQL Error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error creating policy: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("PolicyServlet POST - General Error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request data: " + e.getMessage());
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
                error.addProperty("error", "Policy ID required for update");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int id = Integer.parseInt(pathInfo.substring(1));
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Policy", id)) {
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

            Policy policy = gson.fromJson(jsonData, Policy.class);
            //system.out.println("Parsed policy object: " + policy);
            //system.out.println("Policy primaryName: " + policy.getPrimaryName());
            //system.out.println("Policy description: " + policy.getDescription());
            //system.out.println("Policy status: " + policy.getStatus());
            //system.out.println("Policy lifecycleStatus: " + policy.getLifecycleStatus());
            //system.out.println("Policy policyType: " + policy.getPolicyType());
            //system.out.println("Policy parentId: " + policy.getParentId());

            policy.setId(id);

            // Check role-based edit permission first
            if (!PermissionCheckUtil.checkEditPermission(request, response, "Policy")) {
                return; // Response already sent by checkEditPermission
            }
            
            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "Policy");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Access denied. You don't have permission to edit this policy.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
                // Set lastUpdateUserId from current user
                policy.setLastUpdateUserId(currentUserId);
            }

            if (policy.getPrimaryName() != null && !policy.getPrimaryName().trim().isEmpty()) {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                Integer reqSeg = null;
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    reqSeg = jsonObj.get("segmentId").getAsInt();
                }
                int curSeg = segmentDAO.getObjectSegmentId(id, "Policy");
                long effSeg = (reqSeg != null) ? reqSeg.longValue() : (curSeg > 0 ? curSeg : 1L);
                try (Connection conn = DatabaseConnection.getConnection()) {
                    if (SegmentScopedPrimaryNameCheck.exists(conn, "Policy", policy.getPrimaryName().trim(),
                            effSeg, id)) {
                        throw new IllegalArgumentException("Policy Name already exists in this segment");
                    }
                }
            }

            boolean success = policyService.updatePolicy(policy, request);

            if (success) {
                // Update segment assignment if provided - parse from JSON
                try {
                    JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                    if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                        Integer segmentId = jsonObj.get("segmentId").getAsInt();
                        int curSegmentId = segmentDAO.getObjectSegmentId(id, "Policy");
                        if (curSegmentId != segmentId) {
                            // Validate hierarchy and segment move (includes parent, children, and stakeholder checks)
                            Integer parentId = policy.getParentId();
                            var validationResult = segmentValidationService.validateSegmentMove(id, segmentId, "Policy", parentId);
                            if (!validationResult.isValid) {
                                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                JsonObject error = new JsonObject();
                                error.addProperty("success", false);
                                error.addProperty("error", validationResult.message);
                                response.getWriter().write(gson.toJson(error));
                                return;
                            }
                            
                            if (curSegmentId > 0) {
                                segmentDAO.removeObjectFromSegment(curSegmentId, id, "Policy", currentUserId > 0 ? currentUserId : 1);
                            }
                            segmentDAO.assignObjectToSegment(segmentId, id, "Policy", currentUserId > 0 ? currentUserId : 1);
                            //system.out.println("✅ Policy " + id + " segment changed from " + curSegmentId + " to " + segmentId);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error updating policy segment: " + e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("error", "Error updating policy segment: " + e.getMessage());
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            responseJson.addProperty("message", success ? "Policy updated successfully" : "Failed to update policy");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("PolicyServlet PUT - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error updating policy: " + e.getMessage());
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

        // Check delete permission - Only Admin/Super Admin can delete
        if (!PermissionCheckUtil.checkDeletePermission(request, response, "Policy")) {
            return; // Response already sent by checkDeletePermission
        }

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Policy ID required for deletion");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int id = Integer.parseInt(pathInfo.substring(1));

            boolean success = policyService.deletePolicy(id, request);

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            responseJson.addProperty("message", success ? "Policy deleted successfully" : "Failed to delete policy");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error deleting policy: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request data: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Automatically assigns creator role to the user for newly created policy
     * @param policyId The newly created policy ID
     * @param userId The current user ID
     */
    private void assignCreatorRole(int policyId, int userId) throws SQLException {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Policy");
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

                        String insertPolicyX = """
                                INSERT INTO policy_x_objectxpeople (Object_X_IP, Policy_ID, LastUpdate_UserID)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertPolicyX)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, policyId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into policy_x_objectxpeople");
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Policy", policyId, userId, roleId, objectXPeopleId, conn);

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

    /**
     * Get next available ID for object_x_people table
     * (table doesn't have AUTO_INCREMENT)
     */
    @SuppressWarnings("unused")
    private Integer getNextObjectXPeopleId(java.sql.Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 AS next_id FROM object_x_people";
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("next_id");
            }
        }
        return 1; // fallback
    }
}