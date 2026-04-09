package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet(name = "SegmentServlet", urlPatterns = {"/api/segments", "/api/segments/*"})
public class SegmentServlet extends HttpServlet {

    private final SegmentDAO segmentDAO = new SegmentDAO();
    private final SegmentValidationService validationService = new SegmentValidationService();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            int userId = UserContextUtil.getCurrentUserId(request);
            String userRole = (String) request.getAttribute("userRole");
            boolean superAdmin = isSuperAdmin(userRole);
            
            if (path == null || path.equals("/")) {
                // GET /api/segments - Get segments the user can access
                List<Map<String, Object>> segments = segmentDAO.getAllSegments(userId, superAdmin);
                sendJson(response, segments);
            } else if (path.matches("/\\d+")) {
                // GET /api/segments/{id} - Get segment by ID
                int id = Integer.parseInt(path.substring(1));
                Map<String, Object> segment = segmentDAO.getSegmentById(id);
                if (segment != null) {
                    sendJson(response, segment);
                } else {
                    sendError(response, "Segment not found", 404);
                }
            } else if (path.matches("/\\d+/admin-users")) {
                // GET /api/segments/{id}/admin-users - Get segment admin users
                int segmentId = Integer.parseInt(path.substring(1, path.indexOf("/admin-users")));
                List<Map<String, Object>> adminUsers = getSegmentAdminUsers(segmentId);
                sendJson(response, adminUsers);
            } else if (path.matches("/\\d+/assigned-org-units")) {
                // GET /api/segments/{id}/assigned-org-units - Get assigned org units
                int segmentId = Integer.parseInt(path.substring(1, path.indexOf("/assigned-org-units")));
                List<Map<String, Object>> orgUnits = getAssignedOrgUnits(segmentId);
                sendJson(response, orgUnits);
            } else if (path.matches("/\\d+/assigned-users")) {
                // GET /api/segments/{id}/assigned-users - Get assigned users
                int segmentId = Integer.parseInt(path.substring(1, path.indexOf("/assigned-users")));
                List<Map<String, Object>> users = getAssignedUsers(segmentId);
                sendJson(response, users);
            } else if (path.matches("/\\d+/objects")) {
                // GET /api/segments/{id}/objects - Get objects assigned to segment
                int segmentId = Integer.parseInt(path.substring(1, path.indexOf("/objects")));
                String objectType = request.getParameter("type"); // Optional filter
                List<Map<String, Object>> objects = segmentDAO.getSegmentObjects(segmentId, objectType);
                sendJson(response, objects);
            } else if (path.matches("/\\d+/object-counts")) {
                // GET /api/segments/{id}/object-counts - Get count of objects by type
                int segmentId = Integer.parseInt(path.substring(1, path.indexOf("/object-counts")));
                Map<String, Integer> counts = segmentDAO.getSegmentObjectCounts(segmentId);
                boolean hasObjects = segmentDAO.segmentHasObjects(segmentId);
                Map<String, Object> result = new HashMap<>();
                result.put("counts", counts);
                result.put("hasObjects", hasObjects);
                result.put("totalObjects", counts.values().stream().mapToInt(Integer::intValue).sum());
                sendJson(response, result);
            } else {
                sendError(response, "Invalid endpoint", 404);
            }
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            int userId = UserContextUtil.getCurrentUserId(request);
            String userRole = (String) request.getAttribute("userRole");
            
            // ==================== SEGMENT VALIDATION ENDPOINTS ====================
            // Per BUDG v7.0-7.2 Segmentation Rules
            
            if ("/validate-hierarchy".equals(path)) {
                // POST /api/segments/validate-hierarchy
                // Validates parent-child segment hierarchy constraint
                // Rule: Parent-child relationships must be within the SAME segment
                handleValidateHierarchy(request, response);
                return;
            }
            
            if ("/validate-relationship".equals(path)) {
                // POST /api/segments/validate-relationship
                // Validates cross-segment relationship constraint
                // Rule: Enterprise↔Private = OK, Private↔Private (different) = NOT OK
                handleValidateRelationship(request, response);
                return;
            }

            if ("/validate-segment-change".equals(path)) {
                // POST /api/segments/validate-segment-change
                // Validates all related objects before allowing segment update
                handleValidateSegmentChange(request, response);
                return;
            }
            
            if ("/move-parent".equals(path)) {
                // POST /api/segments/move-parent
                // Moves parent to child's segment when user confirms hierarchy conflict resolution
                handleMoveParent(request, response, userId);
                return;
            }
            
            // ==================== STANDARD SEGMENT CRUD ENDPOINTS ====================
            
            if (path == null || path.equals("/")) {
                // POST /api/segments - Create new segment (SUPER ADMIN ONLY)
                if (!isSuperAdmin(userRole)) {
                    sendError(response, "Forbidden: Only Super Admin can create segments", 403);
                    return;
                }
                
                JsonObject data = parseJsonFromRequest(request);
                String name = getString(data, "name");
                String description = getString(data, "description");

                // Validate admin users BEFORE creating anything to avoid "saved but error" partial writes
                if (!hasAtLeastOneAdminUserInPayload(data)) {
                    sendError(response, "You must assign at least one Segment Admin user before saving", 400);
                    return;
                }
                
                if (name == null || name.trim().isEmpty()) {
                    sendError(response, "Segment name is required", 400);
                    return;
                }
                
                int segmentId = segmentDAO.createSegment(name, description, userId);
                
                // Handle admin users, org units, and users if provided
                handleSegmentAssignments(segmentId, data, userId);
                
                // Validate that at least one admin user is assigned
                List<Map<String, Object>> adminUsers = segmentDAO.getSegmentAdminUsers(segmentId);
                if (adminUsers == null || adminUsers.isEmpty()) {
                    // Rollback: delete the segment if no admin users
                    segmentDAO.deleteSegment(segmentId, userId);
                    sendError(response, "You must assign at least one Segment Admin user before saving", 400);
                    return;
                }
                
                Map<String, Object> result = segmentDAO.getSegmentById(segmentId);
                
                // Get segment name for component - ensure it's not null
                String segmentName = (String) result.get("name");
                if (segmentName == null || segmentName.trim().isEmpty()) {
                    segmentName = (String) result.get("Name"); // Fallback to uppercase
                }
                if (segmentName == null || segmentName.trim().isEmpty()) {
                    segmentName = "Segment " + segmentId; // Fallback to ID if name not available
                }
                
                // Log activity - Create case
                Map<String, Object> newState = buildSegmentState(segmentId);
                // Pass contextMap with segmentName for component name
                Map<String, Object> contextMap = new HashMap<>();
                contextMap.put("segmentName", segmentName);
                ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_SEGMENTS,
                    segmentName, ActivityLogConstants.CHANGE_TYPE_CREATE,
                    null, newState, contextMap);
                
                sendJson(response, result);
            } else if (path.matches("/\\d+/admin-users")) {
                // POST /api/segments/{id}/admin-users - Assign admin users
                int segmentId = Integer.parseInt(path.substring(1, path.indexOf("/admin-users")));
                
                if (!canManageSegment(segmentId, userId, userRole)) {
                    sendError(response, "Forbidden: Only Super Admin or Segment Admin can manage segment administrators", 403);
                    return;
                }
                
                JsonObject data = parseJsonFromRequest(request);
                assignAdminUsers(segmentId, data, userId);
                sendJson(response, Map.of("success", true));
            } else if (path.matches("/\\d+/assigned-org-units")) {
                // POST /api/segments/{id}/assigned-org-units - Assign org units
                int segmentId = Integer.parseInt(path.substring(1, path.indexOf("/assigned-org-units")));
                
                if (!canManageSegment(segmentId, userId, userRole)) {
                    sendError(response, "Forbidden: Only Super Admin or Segment Admin can manage segment org units", 403);
                    return;
                }
                
                JsonObject data = parseJsonFromRequest(request);
                assignOrgUnits(segmentId, data, userId);
                sendJson(response, Map.of("success", true));
            } else if (path.matches("/\\d+/assigned-users")) {
                // POST /api/segments/{id}/assigned-users - Assign users
                int segmentId = Integer.parseInt(path.substring(1, path.indexOf("/assigned-users")));
                
                if (!canManageSegment(segmentId, userId, userRole)) {
                    sendError(response, "Forbidden: Only Super Admin or Segment Admin can manage segment users", 403);
                    return;
                }
                
                JsonObject data = parseJsonFromRequest(request);
                assignUsers(segmentId, data, userId);
                sendJson(response, Map.of("success", true));
            } else {
                sendError(response, "Invalid endpoint", 404);
            }
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            int userId = UserContextUtil.getCurrentUserId(request);
            String userRole = (String) request.getAttribute("userRole");
            
            if (path != null && path.matches("/\\d+")) {
                // PUT /api/segments/{id} - Update segment
                int id = Integer.parseInt(path.substring(1));
                
                if (!canManageSegment(id, userId, userRole)) {
                    sendError(response, "Forbidden: Only Super Admin or Segment Admin can update segments", 403);
                    return;
                }
                
                JsonObject data = parseJsonFromRequest(request);
                String name = getString(data, "name");
                String description = getString(data, "description");

                // Capture old state before update
                Map<String, Object> oldState = buildSegmentState(id);

                // If client provided admin_users, validate it BEFORE writing to DB
                // This prevents partial updates where the DB saves changes but we later reject due to 0 admins.
                if (data.has("admin_users")) {
                    if (!hasAtLeastOneAdminUserInPayload(data)) {
                        sendError(response, "You must have at least one Segment Admin user assigned", 400);
                        return;
                    }
                }
                
                if (name == null || name.trim().isEmpty()) {
                    sendError(response, "Segment name is required", 400);
                    return;
                }
                
                boolean updated = segmentDAO.updateSegment(id, name, description, userId);
                if (updated) {
                    // Handle assignments if provided
                    handleSegmentAssignments(id, data, userId);
                    
                    // Validate that at least one admin user is assigned
                    List<Map<String, Object>> adminUsers = segmentDAO.getSegmentAdminUsers(id);
                    if (adminUsers == null || adminUsers.isEmpty()) {
                        sendError(response, "You must have at least one Segment Admin user assigned", 400);
                        return;
                    }
                    
                    Map<String, Object> result = segmentDAO.getSegmentById(id);
                    
                    // Get segment name for component - ensure it's not null
                    String segmentName = (String) result.get("name");
                    if (segmentName == null || segmentName.trim().isEmpty()) {
                        segmentName = (String) result.get("Name"); // Fallback to uppercase
                    }
                    if (segmentName == null || segmentName.trim().isEmpty()) {
                        segmentName = "Segment " + id; // Fallback to ID if name not available
                    }
                    
                    // Log activity - Update case
                    Map<String, Object> newState = buildSegmentState(id);
                    // Pass contextMap with segmentName for component name
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("segmentName", segmentName);
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_SEGMENTS,
                        segmentName, ActivityLogConstants.CHANGE_TYPE_UPDATE,
                        oldState, newState, contextMap);
                    
                    sendJson(response, result);
                } else {
                    sendError(response, "Segment not found", 404);
                }
            } else {
                sendError(response, "Invalid endpoint", 404);
            }
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            int userId = UserContextUtil.getCurrentUserId(request);
            String userRole = (String) request.getAttribute("userRole");
            
            if (path != null && path.matches("/\\d+$")) {
                // DELETE /api/segments/{id} - Delete segment (SUPER ADMIN ONLY)
                // Per BUDG docs: When deleting a segment, objects must be moved to another segment
                if (!isSuperAdmin(userRole)) {
                    sendError(response, "Forbidden: Only Super Admin can delete segments", 403);
                    return;
                }
                
                int id = Integer.parseInt(path.substring(1));
                
                // Cannot delete Enterprise segment
                if (id == 1) {
                    sendError(response, "Cannot delete the Enterprise segment", 400);
                    return;
                }
                
                // Get target segment from query parameter (default to Enterprise = 1)
                String targetSegmentParam = request.getParameter("targetSegment");
                int targetSegmentId = 1; // Default to Enterprise
                if (targetSegmentParam != null && !targetSegmentParam.isEmpty()) {
                    try {
                        targetSegmentId = Integer.parseInt(targetSegmentParam);
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid target segment ID", 400);
                        return;
                    }
                }
                
                // Capture old state before deletion
                Map<String, Object> oldState = buildSegmentState(id);
                
                // Get segment name for component - ensure it's not null
                String segmentName = (String) oldState.get("name");
                if (segmentName == null || segmentName.trim().isEmpty()) {
                    // Fallback: try to get from segment directly
                    Map<String, Object> segment = segmentDAO.getSegmentById(id);
                    if (segment != null) {
                        segmentName = (String) segment.get("name");
                        if (segmentName == null || segmentName.trim().isEmpty()) {
                            segmentName = (String) segment.get("Name"); // Fallback to uppercase
                        }
                    }
                    if (segmentName == null || segmentName.trim().isEmpty()) {
                        segmentName = "Segment " + id; // Fallback to ID if name not available
                    }
                }
                
                // Use the new deletion method that handles object reassignment
                boolean deleted = segmentDAO.deleteSegmentWithReassignment(id, targetSegmentId, userId);
                if (deleted) {
                    // Log activity - Delete case
                    // Pass contextMap with segmentName for component name
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("segmentName", segmentName);
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_SEGMENTS,
                        segmentName, ActivityLogConstants.CHANGE_TYPE_DELETE,
                        oldState, null, contextMap);
                    
                    sendJson(response, Map.of("success", true, "objectsMovedTo", targetSegmentId));
                } else {
                    sendError(response, "Segment not found", 404);
                }
            } else if (path != null && path.matches("/\\d+/admin-users/\\d+")) {
                // DELETE /api/segments/{id}/admin-users/{userId} - Remove admin user
                String[] parts = path.substring(1).split("/");
                int segmentId = Integer.parseInt(parts[0]);
                int peopleId = Integer.parseInt(parts[2]);
                
                if (!canManageSegment(segmentId, userId, userRole)) {
                    sendError(response, "Forbidden: Only Super Admin or Segment Admin can remove segment administrators", 403);
                    return;
                }
                
                // Check if this is the last admin user
                List<Map<String, Object>> currentAdmins = segmentDAO.getSegmentAdminUsers(segmentId);
                if (currentAdmins != null && currentAdmins.size() <= 1) {
                    sendError(response, "Cannot remove the last Segment Admin. You must have at least one admin assigned.", 400);
                    return;
                }
                
                segmentDAO.removeAdminUser(segmentId, peopleId, userId);
                sendJson(response, Map.of("success", true));
            } else if (path != null && path.matches("/\\d+/assigned-org-units/\\d+")) {
                // DELETE /api/segments/{id}/assigned-org-units/{orgUnitId} - Remove org unit
                String[] parts = path.substring(1).split("/");
                int segmentId = Integer.parseInt(parts[0]);
                int orgUnitId = Integer.parseInt(parts[2]);
                
                if (!canManageSegment(segmentId, userId, userRole)) {
                    sendError(response, "Forbidden: Only Super Admin or Segment Admin can remove segment org units", 403);
                    return;
                }
                
                segmentDAO.removeOrgUnit(segmentId, orgUnitId, userId);
                sendJson(response, Map.of("success", true));
            } else if (path != null && path.matches("/\\d+/assigned-users/\\d+")) {
                // DELETE /api/segments/{id}/assigned-users/{userId} - Remove user
                String[] parts = path.substring(1).split("/");
                int segmentId = Integer.parseInt(parts[0]);
                int peopleId = Integer.parseInt(parts[2]);
                
                if (!canManageSegment(segmentId, userId, userRole)) {
                    sendError(response, "Forbidden: Only Super Admin or Segment Admin can remove segment users", 403);
                    return;
                }
                
                segmentDAO.removeUser(segmentId, peopleId, userId);
                sendJson(response, Map.of("success", true));
            } else {
                sendError(response, "Invalid endpoint", 404);
            }
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // Helper methods for assignments
    private List<Map<String, Object>> getSegmentAdminUsers(int segmentId) throws SQLException {
        return segmentDAO.getSegmentAdminUsers(segmentId);
    }

    private List<Map<String, Object>> getAssignedOrgUnits(int segmentId) throws SQLException {
        return segmentDAO.getAssignedOrgUnits(segmentId);
    }

    private List<Map<String, Object>> getAssignedUsers(int segmentId) throws SQLException {
        return segmentDAO.getAssignedUsers(segmentId);
    }
    
    /**
     * Build segment state map for activity logging
     */
    private Map<String, Object> buildSegmentState(int segmentId) throws SQLException {
        Map<String, Object> state = new HashMap<>();
        try {
            Map<String, Object> segment = segmentDAO.getSegmentById(segmentId);
            if (segment != null) {
                // Get segment name and description - use lowercase keys as returned by DAO
                Object nameObj = segment.get("name");
                if (nameObj == null) {
                    nameObj = segment.get("Name"); // Fallback to uppercase
                }
                state.put("name", nameObj != null ? nameObj.toString() : "");
                
                Object descObj = segment.get("description");
                if (descObj == null) {
                    descObj = segment.get("Description"); // Fallback to uppercase
                }
                state.put("description", descObj != null ? descObj.toString() : "");
                
                // Format admin users as comma-separated "name(email)"
                List<Map<String, Object>> adminUsers = getSegmentAdminUsers(segmentId);
                state.put("admin_users", formatUserList(adminUsers));
                
                // Format org units as comma-separated names
                List<Map<String, Object>> orgUnits = getAssignedOrgUnits(segmentId);
                state.put("assigned_org_units", formatOrgUnitList(orgUnits));
                
                // Format assigned users as comma-separated "name(email)"
                List<Map<String, Object>> assignedUsers = getAssignedUsers(segmentId);
                state.put("assigned_users", formatUserList(assignedUsers));
            } else {
                // If segment not found, set empty values
                state.put("name", "");
                state.put("description", "");
                state.put("admin_users", "");
                state.put("assigned_org_units", "");
                state.put("assigned_users", "");
            }
        } catch (Exception e) {
            // If any error occurs, set empty values to prevent null issues
            System.err.println("Error building segment state: " + e.getMessage());
            e.printStackTrace();
            state.put("name", "");
            state.put("description", "");
            state.put("admin_users", "");
            state.put("assigned_org_units", "");
            state.put("assigned_users", "");
        }
        return state;
    }
    
    /**
     * Format a list of user maps into a comma-separated string of "name(email)"
     */
    private String formatUserList(List<Map<String, Object>> users) {
        if (users == null || users.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < users.size(); i++) {
            Map<String, Object> user = users.get(i);
            String name = null;
            String email = null;
            
            // Try different possible field names for user name
            Object nameObj = user.get("name");
            if (nameObj == null) {
                nameObj = user.get("Name");
            }
            if (nameObj == null) {
                nameObj = user.get("full_name");
            }
            if (nameObj == null) {
                nameObj = user.get("FullName");
            }
            if (nameObj != null) {
                name = String.valueOf(nameObj).trim();
                if (name.isEmpty() || "null".equalsIgnoreCase(name)) {
                    name = null;
                }
            }
            
            // If name still not found, try to build from first_name and last_name
            if (name == null || name.trim().isEmpty()) {
                Object firstNameObj = user.get("first_name");
                Object lastNameObj = user.get("last_name");
                if (firstNameObj == null) {
                    firstNameObj = user.get("FirstName");
                }
                if (lastNameObj == null) {
                    lastNameObj = user.get("LastName");
                }
                if (firstNameObj != null || lastNameObj != null) {
                    String firstName = firstNameObj != null ? String.valueOf(firstNameObj).trim() : "";
                    String lastName = lastNameObj != null ? String.valueOf(lastNameObj).trim() : "";
                    name = (firstName + " " + lastName).trim();
                    if (name.isEmpty()) {
                        name = null;
                    }
                }
            }
            
            // Try different possible field names for email
            Object emailObj = user.get("email");
            if (emailObj == null) {
                emailObj = user.get("Email");
            }
            if (emailObj != null) {
                email = String.valueOf(emailObj);
                // Check if it's actually null (String.valueOf(null) returns "null")
                if ("null".equalsIgnoreCase(email) || email.trim().isEmpty()) {
                    email = null;
                }
            }
            
            // If email is not in the map or is null/empty, try to get from user_id or id
            if (email == null || email.trim().isEmpty()) {
                Object userIdObj = user.get("user_id");
                if (userIdObj == null) {
                    userIdObj = user.get("id");
                }
                if (userIdObj != null) {
                    // Try to fetch user info from database
                    try {
                        int userId = ((Number) userIdObj).intValue();
                        Map<String, String> userInfo = getUserInfoById(userId);
                        if (userInfo != null) {
                            if (name == null || name.trim().isEmpty()) {
                                name = userInfo.get("name");
                            }
                            String fetchedEmail = userInfo.get("email");
                            if (fetchedEmail != null && !fetchedEmail.trim().isEmpty()) {
                                email = fetchedEmail;
                            }
                        } else {
                            if (name == null || name.trim().isEmpty()) {
                                name = "User ID: " + userId;
                            }
                        }
                    } catch (Exception e) {
                        if (name == null || name.trim().isEmpty()) {
                            name = "User ID: " + userIdObj;
                        }
                    }
                }
            }
            
            // If no name found but email exists, use email as name
            if ((name == null || name.trim().isEmpty()) && email != null && !email.trim().isEmpty()) {
                name = email;
            }
            
            // Format as "name(email)" or just "name" if no email
            if (name != null && !name.trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                if (email != null && !email.trim().isEmpty() && !email.equals(name)) {
                    sb.append(name.trim()).append("(").append(email.trim()).append(")");
                } else {
                    sb.append(name.trim());
                }
            }
        }
        return sb.toString();
    }
    
    /**
     * Get user name and email by user ID from database
     */
    private Map<String, String> getUserInfoById(int userId) {
        Map<String, String> userInfo = new HashMap<>();
        try {
            String sql = "SELECT first_name, last_name, email FROM people WHERE ID = ?";
            try (java.sql.Connection c = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String firstName = rs.getString("first_name");
                        String lastName = rs.getString("last_name");
                        String email = rs.getString("email");
                        
                        // Build full name
                        String name = "";
                        if (firstName != null && !firstName.trim().isEmpty()) {
                            name = firstName.trim();
                        }
                        if (lastName != null && !lastName.trim().isEmpty()) {
                            name = (name + " " + lastName.trim()).trim();
                        }
                        if (name.isEmpty()) {
                            name = "User ID: " + userId;
                        }
                        
                        userInfo.put("name", name);
                        userInfo.put("email", email != null ? email : "");
                        return userInfo;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user info: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Format a list of org unit maps into a comma-separated string of names
     */
    private String formatOrgUnitList(List<Map<String, Object>> orgUnits) {
        if (orgUnits == null || orgUnits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orgUnits.size(); i++) {
            Map<String, Object> orgUnit = orgUnits.get(i);
            String name = null;
            // Try different possible field names for org unit name
            if (orgUnit.containsKey("name")) {
                name = String.valueOf(orgUnit.get("name"));
            } else if (orgUnit.containsKey("Name")) {
                name = String.valueOf(orgUnit.get("Name"));
            } else if (orgUnit.containsKey("primary_name")) {
                name = String.valueOf(orgUnit.get("primary_name"));
            } else if (orgUnit.containsKey("PrimaryName")) {
                name = String.valueOf(orgUnit.get("PrimaryName"));
            }
            
            if (name != null && !name.trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(name.trim());
            }
        }
        return sb.toString();
    }

    private void handleSegmentAssignments(int segmentId, JsonObject data, int userId) throws SQLException {
        // For Save (POST/PUT), we interpret assignment arrays as the desired final state and synchronize DB accordingly.

        // -------------------- admin_users --------------------
        if (data.has("admin_users") && data.get("admin_users").isJsonArray()) {
            var adminUsersArray = data.getAsJsonArray("admin_users");
            Set<Integer> desiredAdminIds = new HashSet<>();
            Map<Integer, String> desiredAdminType = new HashMap<>();

            for (var element : adminUsersArray) {
                if (!element.isJsonObject()) continue;
                var userObj = element.getAsJsonObject();
                if (!userObj.has("user_id") || userObj.get("user_id").isJsonNull()) continue;
                int peopleId = userObj.get("user_id").getAsInt();
                if (peopleId <= 0) continue;
                desiredAdminIds.add(peopleId);
                String assignmentType = userObj.has("assignment_type") && !userObj.get("assignment_type").isJsonNull()
                        ? userObj.get("assignment_type").getAsString()
                        : "manual";
                desiredAdminType.put(peopleId, assignmentType);
            }

            // Remove admins not in desired list
            List<Map<String, Object>> currentAdmins = segmentDAO.getSegmentAdminUsers(segmentId);
            for (Map<String, Object> admin : currentAdmins) {
                Object idObj = admin.get("user_id");
                if (idObj == null) continue;
                int currentId = ((Number) idObj).intValue();
                if (!desiredAdminIds.contains(currentId)) {
                    segmentDAO.removeAdminUser(segmentId, currentId, userId);
                }
            }

            // Add/ensure desired admins exist with correct origin
            for (Integer peopleId : desiredAdminIds) {
                segmentDAO.assignAdminUser(segmentId, peopleId, desiredAdminType.getOrDefault(peopleId, "manual"), userId);
            }
        }

        // -------------------- assigned_org_units --------------------
        if (data.has("assigned_org_units") && data.get("assigned_org_units").isJsonArray()) {
            var orgUnitsArray = data.getAsJsonArray("assigned_org_units");
            Set<Integer> desiredOrgUnitIds = new HashSet<>();
            for (var element : orgUnitsArray) {
                if (!element.isJsonObject()) continue;
                var orgUnitObj = element.getAsJsonObject();
                if (!orgUnitObj.has("id") || orgUnitObj.get("id").isJsonNull()) continue;
                int orgUnitId = orgUnitObj.get("id").getAsInt();
                if (orgUnitId > 0) desiredOrgUnitIds.add(orgUnitId);
            }

            // Remove org units not in desired list
            List<Map<String, Object>> currentOrgUnits = segmentDAO.getAssignedOrgUnits(segmentId);
            for (Map<String, Object> ou : currentOrgUnits) {
                Object idObj = ou.get("id");
                if (idObj == null) continue;
                int currentId = ((Number) idObj).intValue();
                if (!desiredOrgUnitIds.contains(currentId)) {
                    segmentDAO.removeOrgUnit(segmentId, currentId, userId);
                }
            }

            // Add desired org units
            for (Integer orgUnitId : desiredOrgUnitIds) {
                segmentDAO.assignOrgUnit(segmentId, orgUnitId, userId);
            }
        }

        // -------------------- assigned_users --------------------
        if (data.has("assigned_users") && data.get("assigned_users").isJsonArray()) {
            var usersArray = data.getAsJsonArray("assigned_users");
            Set<Integer> desiredUserIds = new HashSet<>();
            for (var element : usersArray) {
                if (!element.isJsonObject()) continue;
                var userObj = element.getAsJsonObject();
                if (!userObj.has("id") || userObj.get("id").isJsonNull()) continue;
                int peopleId = userObj.get("id").getAsInt();
                if (peopleId > 0) desiredUserIds.add(peopleId);
            }

            // Remove users not in desired list
            List<Map<String, Object>> currentUsers = segmentDAO.getAssignedUsers(segmentId);
            for (Map<String, Object> u : currentUsers) {
                Object idObj = u.get("id");
                if (idObj == null) continue;
                int currentId = ((Number) idObj).intValue();
                if (!desiredUserIds.contains(currentId)) {
                    segmentDAO.removeUser(segmentId, currentId, userId);
                }
            }

            // Add desired users
            for (Integer peopleId : desiredUserIds) {
                segmentDAO.assignUser(segmentId, peopleId, userId);
            }
        }
    }

    /**
     * Returns true if request payload contains at least one admin user.
     * Expected shape:
     * {
     *   "admin_users": [{ "user_id": 123, "assignment_type": "manual" }, ...]
     * }
     */
    private boolean hasAtLeastOneAdminUserInPayload(JsonObject data) {
        if (data == null) return false;
        if (!data.has("admin_users") || !data.get("admin_users").isJsonArray()) return false;
        var arr = data.getAsJsonArray("admin_users");
        if (arr.size() == 0) return false;
        for (var el : arr) {
            if (!el.isJsonObject()) continue;
            var obj = el.getAsJsonObject();
            if (obj.has("user_id") && !obj.get("user_id").isJsonNull()) {
                try {
                    int id = obj.get("user_id").getAsInt();
                    if (id > 0) return true;
                } catch (Exception ignored) {
                    // ignore invalid types
                }
            }
        }
        return false;
    }

    private void assignAdminUsers(int segmentId, JsonObject data, int userId) throws SQLException {
        if (data.has("user_ids") && data.get("user_ids").isJsonArray()) {
            var userIdsArray = data.getAsJsonArray("user_ids");
            String assignmentType = getString(data, "assignment_type");
            if (assignmentType == null) assignmentType = "manual";
            
            for (var element : userIdsArray) {
                if (element.isJsonPrimitive()) {
                    int peopleId = element.getAsInt();
                    segmentDAO.assignAdminUser(segmentId, peopleId, assignmentType, userId);
                }
            }
        }
    }

    private void assignOrgUnits(int segmentId, JsonObject data, int userId) throws SQLException {
        if (data.has("org_unit_ids") && data.get("org_unit_ids").isJsonArray()) {
            var orgUnitIdsArray = data.getAsJsonArray("org_unit_ids");
            for (var element : orgUnitIdsArray) {
                if (element.isJsonPrimitive()) {
                    int orgUnitId = element.getAsInt();
                    segmentDAO.assignOrgUnit(segmentId, orgUnitId, userId);
                }
            }
        }
    }

    private void assignUsers(int segmentId, JsonObject data, int userId) throws SQLException {
        if (data.has("user_ids") && data.get("user_ids").isJsonArray()) {
            var userIdsArray = data.getAsJsonArray("user_ids");
            for (var element : userIdsArray) {
                if (element.isJsonPrimitive()) {
                    int peopleId = element.getAsInt();
                    segmentDAO.assignUser(segmentId, peopleId, userId);
                }
            }
        }
    }

    // Utility methods
    private void setupResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
    }
    
    /**
     * Check if user is Super Admin
     */
    private boolean isSuperAdmin(String role) {
        return AppRoleNames.isSuperAdminName(role);
    }

    /**
     * Check if user has management permissions for a specific segment
     */
    private boolean canManageSegment(int segmentId, int userId, String userRole) throws SQLException {
        if (isSuperAdmin(userRole)) return true;
        return segmentDAO.isSegmentAdmin(segmentId, userId);
    }

    private void sendJson(HttpServletResponse response, Object data) throws IOException {
        response.getWriter().write(gson.toJson(data));
    }

    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        response.getWriter().write(gson.toJson(error));
    }

    private JsonObject parseJsonFromRequest(HttpServletRequest request) throws IOException {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }
        return JsonParser.parseString(json.toString()).getAsJsonObject();
    }

    private String getString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return null;
    }
    
    private Integer getInt(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsInt();
        }
        return null;
    }

    // ==================== SEGMENT VALIDATION HANDLERS ====================
    
    /**
     * POST /api/segments/validate-hierarchy
     * 
     * Validates that parent-child relationships stay within the SAME segment.
     * Per BUDG Segmentation Rules v7.0-7.2
     * 
     * Request body:
     * {
     *   "parentId": 123,           // Parent object ID
     *   "childSegmentId": 2,       // Target segment for child
     *   "objectType": "Glossary"   // Object type (Glossary, Policy, System, etc.)
     * }
     * 
     * Response:
     * {
     *   "isValid": true/false,
     *   "message": "...",
     *   "canProceed": true/false,  // If true, user can override with warning
     *   "parentSegmentId": 1,
     *   "parentSegmentName": "Enterprise",
     *   "childSegmentId": 2,
     *   "childSegmentName": "Private Segment"
     * }
     */
    private void handleValidateHierarchy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            JsonObject data = parseJsonFromRequest(request);
            
            Integer parentId = getInt(data, "parentId");
            Integer childSegmentId = getInt(data, "childSegmentId");
            String objectType = getString(data, "objectType");
            
            if (childSegmentId == null || objectType == null) {
                sendError(response, "childSegmentId and objectType are required", 400);
                return;
            }
            
            SegmentValidationService.ValidationResult result = 
                validationService.validateParentChildSegment(parentId, childSegmentId, objectType);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("isValid", result.isValid);
            responseData.put("message", result.message);
            responseData.put("canProceed", result.canProceedWithWarning);
            
            if (!result.isValid) {
                responseData.put("warningType", result.warningType);
                responseData.put("parentSegmentId", result.parentSegmentId);
                responseData.put("parentSegmentName", result.parentSegmentName);
                responseData.put("childSegmentId", result.childSegmentId);
                responseData.put("childSegmentName", result.childSegmentName);
            }
            
            sendJson(response, responseData);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * POST /api/segments/validate-relationship
     * 
     * Validates cross-segment relationship constraints.
     * Per BUDG Segmentation Rules v7.0-7.2:
     * - Enterprise ↔ Enterprise: ✅ ALLOWED
     * - Enterprise ↔ Private: ✅ ALLOWED  
     * - Private ↔ Enterprise: ✅ ALLOWED
     * - Private1 ↔ Private1: ✅ ALLOWED (same segment)
     * - Private1 ↔ Private2: ❌ NOT ALLOWED (different private segments)
     * 
     * Request body (Option 1 - by object IDs):
     * {
     *   "sourceObjectId": 123,
     *   "sourceObjectType": "Glossary",
     *   "targetObjectId": 456,
     *   "targetObjectType": "Dataset"
     * }
     * 
     * Request body (Option 2 - by segment IDs):
     * {
     *   "sourceSegmentId": 1,
     *   "targetSegmentId": 2
     * }
     * 
     * Response:
     * {
     *   "isValid": true/false,
     *   "message": "..."
     * }
     */
    private void handleValidateRelationship(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            JsonObject data = parseJsonFromRequest(request);
            
            SegmentValidationService.ValidationResult result;
            
            // Check if validating by segment IDs (simpler)
            Integer sourceSegmentId = getInt(data, "sourceSegmentId");
            Integer targetSegmentId = getInt(data, "targetSegmentId");
            
            if (sourceSegmentId != null && targetSegmentId != null) {
                // Validate by segment IDs
                result = validationService.validateCrossSegmentRelationshipBySegmentIds(
                    sourceSegmentId, targetSegmentId);
            } else {
                // Validate by object IDs
                Integer sourceObjectId = getInt(data, "sourceObjectId");
                String sourceObjectType = getString(data, "sourceObjectType");
                Integer targetObjectId = getInt(data, "targetObjectId");
                String targetObjectType = getString(data, "targetObjectType");
                
                if (sourceObjectId == null || sourceObjectType == null || 
                    targetObjectId == null || targetObjectType == null) {
                    sendError(response, "Either (sourceSegmentId, targetSegmentId) or " +
                        "(sourceObjectId, sourceObjectType, targetObjectId, targetObjectType) are required", 400);
                    return;
                }
                
                result = validationService.validateCrossSegmentRelationship(
                    sourceObjectId, sourceObjectType, targetObjectId, targetObjectType);
            }
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("isValid", result.isValid);
            responseData.put("message", result.message);
            
            if (!result.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            
            sendJson(response, responseData);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        }
    }

    /**
     * POST /api/segments/validate-segment-change
     *
     * Validates that an object can change segment without creating disallowed cross-private relationships.
     *
     * Request body:
     * {
     *   "objectId": 123,
     *   "objectType": "Glossary",
     *   "newSegmentId": 2
     * }
     *
     * Response:
     * {
     *   "isValid": true/false,
     *   "message": "..."
     * }
     */
    private void handleValidateSegmentChange(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            JsonObject data = parseJsonFromRequest(request);

            Integer objectId = getInt(data, "objectId");
            String objectType = getString(data, "objectType");
            Integer newSegmentId = getInt(data, "newSegmentId");

            if (objectId == null || objectType == null || newSegmentId == null) {
                sendError(response, "objectId, objectType, and newSegmentId are required", 400);
                return;
            }

            SegmentValidationService.ValidationResult result =
                validationService.validateSegmentChangeForRelationships(objectId, newSegmentId, objectType);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("isValid", result.isValid);
            responseData.put("message", result.message);

            if (!result.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            sendJson(response, responseData);
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * POST /api/segments/move-parent
     * 
     * Moves a parent object to the child's segment when user confirms hierarchy conflict resolution.
     * Called after user acknowledges the hierarchy warning and wants to proceed.
     * 
     * Request body:
     * {
     *   "parentId": 123,
     *   "targetSegmentId": 2,
     *   "objectType": "Glossary"
     * }
     * 
     * Response:
     * {
     *   "success": true,
     *   "message": "Parent moved to segment successfully"
     * }
     */
    private void handleMoveParent(HttpServletRequest request, HttpServletResponse response, int userId) throws IOException {
        try {
            JsonObject data = parseJsonFromRequest(request);
            
            Integer parentId = getInt(data, "parentId");
            Integer targetSegmentId = getInt(data, "targetSegmentId");
            String objectType = getString(data, "objectType");
            
            if (parentId == null || targetSegmentId == null || objectType == null) {
                sendError(response, "parentId, targetSegmentId, and objectType are required", 400);
                return;
            }
            
            // Move parent to the target segment
            validationService.moveParentToChildSegment(parentId, targetSegmentId, objectType, userId);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Parent moved to segment successfully");
            responseData.put("parentId", parentId);
            responseData.put("newSegmentId", targetSegmentId);
            
            sendJson(response, responseData);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        }
    }
}

