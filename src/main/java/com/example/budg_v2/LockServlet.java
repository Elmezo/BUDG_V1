package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.dao.LockDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.ModuleResolver;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "LockServlet", urlPatterns = {"/api/lock/*"})
public class LockServlet extends HttpServlet {
    
    private final LockDAO lockDAO = new LockDAO();
    private final PermissionService permissionService = new PermissionService();
    private final Gson gson = new Gson();
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        String pathInfo = request.getPathInfo();
        
        //system.out.println("=== LOCK GET REQUEST ===");
        //system.out.println("Path: " + pathInfo);
        //system.out.println("Request attribute 'userId': " + request.getAttribute("userId"));
        //system.out.println("Request attribute 'userRole': " + request.getAttribute("userRole"));
        
        int userId = UserContextUtil.getCurrentUserId(request);
        //system.out.println("Resolved userId: " + userId);
        
        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("")) {
                // Get all locks - admin/super admin only (regular users use my-locks)
                if (!isAdminOrSuperAdmin(request)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Only administrators can view all locks. Use My Locked Items to see your own locks.\"}");
                    return;
                }
                // TC-014: SuperAdmin sees all locks; Admin sees only locks in their accessible segments
                List<Map<String, Object>> locks;
                if (isSuperAdmin(request)) {
                    locks = lockDAO.getAllLocks();
                } else {
                    locks = lockDAO.getAllLocksForAdmin(userId);
                }
                response.getWriter().write(gson.toJson(locks));
                return;
            }
            
            String[] parts = pathInfo.replaceFirst("^/", "").split("/");
            
            if (parts.length >= 1 && "my-locks".equals(parts[0])) {
                // Get user's own locks
                //system.out.println("Getting locks for user ID: " + userId);
                List<Map<String, Object>> locks = lockDAO.getUserLocks(userId);
                //system.out.println("Found " + locks.size() + " locks for user " + userId);
                JsonObject responseObj = new JsonObject();
                responseObj.add("locks", gson.toJsonTree(locks));
                String jsonResponse = responseObj.toString();
                //system.out.println("Returning locks response: " + jsonResponse);
                response.getWriter().write(jsonResponse);
                return;
            }
            
            if (parts.length >= 2) {
                // Check lock status: /api/lock/{moduleName}/{objectId}
                String moduleName = parts[0];
                int objectId = Integer.parseInt(parts[1]);

                int moduleId = ModuleResolver.getModuleId(moduleName);
                Map<String, Object> lockInfo = lockDAO.checkLock(moduleId, objectId, userId);

                // V-04 + V-09: Filter lock metadata and existence based on user role
                lockInfo = filterLockInfoForUser(lockInfo, userId, objectId, moduleName, request);

                response.getWriter().write(gson.toJson(lockInfo));
                return;
            }
            
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid path\"}");
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid object ID\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error";
            response.getWriter().write("{\"error\":\"" + msg + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        String pathInfo = request.getPathInfo();
        
        //system.out.println("=== LOCK POST REQUEST ===");
        //system.out.println("Path: " + pathInfo);
        //system.out.println("Request attribute 'userId': " + request.getAttribute("userId"));
        //system.out.println("Request attribute 'userRole': " + request.getAttribute("userRole"));
        
        int userId = UserContextUtil.getCurrentUserId(request);
        //system.out.println("Resolved userId: " + userId);
        
        try {
            // Check if this is a release-beacon request (from navigator.sendBeacon on tab close)
            if (pathInfo != null && pathInfo.matches("^/.+/\\d+/release-beacon/?$")) {
                handleReleaseBeacon(request, response, pathInfo);
                return;
            }
            
            // Check if this is a toggle-permanent request
            if (pathInfo != null && pathInfo.matches("^/\\d+/toggle-permanent/?$")) {
                handleTogglePermanent(request, response, pathInfo, userId);
                return;
            }
            
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("")) {
                // Acquire lock
                //system.out.println("=== ACQUIRE LOCK REQUEST ===");
                //system.out.println("User ID from request: " + userId);
                
                JsonObject json = JsonUtil.parseJsonFromRequest(request.getReader());
                
                String moduleName = JsonUtil.getJsonString(json, "moduleName");
                if (moduleName == null || moduleName.isBlank()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"moduleName is required\"}");
                    return;
                }
                
                Integer objectId = JsonUtil.getJsonInt(json, "objectId");
                if (objectId == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"objectId is required\"}");
                    return;
                }
                
                //system.out.println("Module: " + moduleName + ", Object ID: " + objectId);
                
                // Check if user has Edit permission for this module
                // Admin and Super Admin bypass this check (handled in permissionService)
                String normalizedModuleName = normalizeModuleName(moduleName);
                if (!permissionService.canEdit(userId, normalizedModuleName)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject resp = new JsonObject();
                    resp.addProperty("success", false);
                    resp.addProperty("error", "You don't have permission to edit " + moduleName);
                    response.getWriter().write(resp.toString());
                    return;
                }
                
                Boolean isPermanentObj = JsonUtil.getJsonBoolean(json, "isPermanent");
                boolean isPermanent = isPermanentObj != null && isPermanentObj;
                
                int moduleId = ModuleResolver.getModuleId(moduleName);
                //system.out.println("Module ID resolved to: " + moduleId);
                //system.out.println("Creating lock with user ID: " + userId);
                
                // Validate that the object exists before creating a lock
                if (!lockDAO.objectExists(moduleId, objectId)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject resp = new JsonObject();
                    resp.addProperty("success", false);
                    resp.addProperty("error", "Object with ID " + objectId + " does not exist in " + moduleName);
                    response.getWriter().write(resp.toString());
                    return;
                }
                
                Integer lockId = lockDAO.acquireLock(moduleId, objectId, userId, isPermanent);
                //system.out.println("Lock created with ID: " + lockId);
                
                if (lockId != null) {
                    JsonObject resp = new JsonObject();
                    resp.addProperty("success", true);
                    resp.addProperty("lockId", lockId);
                    response.getWriter().write(resp.toString());
                } else {
                    // Check if locked by another user
                    Map<String, Object> existingLock = lockDAO.checkLock(moduleId, objectId);
                    if (existingLock != null) {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        JsonObject resp = new JsonObject();
                        resp.addProperty("success", false);
                        resp.addProperty("error", "Object is already locked by another user");
                        resp.addProperty("lockedBy", (String) existingLock.get("lockedByName"));
                        resp.addProperty("lockedById", ((Number) existingLock.get("lockedById")).intValue());
                        resp.addProperty("isPermanent", (Boolean) existingLock.get("isPermanent"));
                        response.getWriter().write(resp.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.getWriter().write("{\"success\":false,\"error\":\"Failed to acquire lock\"}");
                    }
                }
                return;
            }
            
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid path\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error";
            System.err.println("SQL Error in acquireLock: " + e.getMessage());
            e.printStackTrace();
            response.getWriter().write("{\"error\":\"" + msg + "\"}");
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Invalid parameter";
            System.err.println("Validation Error in acquireLock: " + e.getMessage());
            e.printStackTrace();
            response.getWriter().write("{\"error\":\"" + msg + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Unknown error";
            System.err.println("Error in acquireLock: " + e.getMessage());
            e.printStackTrace();
            response.getWriter().write("{\"error\":\"" + msg + "\"}");
        }
    }
    
    /**
     * Handle release-beacon request from navigator.sendBeacon on tab close
     * This is called when user closes the browser tab
     */
    private void handleReleaseBeacon(HttpServletRequest request, HttpServletResponse response, String pathInfo) throws IOException {
        System.out.println("🔓 [LockServlet] Release beacon received: " + pathInfo);
        
        try {
            // Parse path: /{facetType}/{objectId}/release-beacon
            String[] parts = pathInfo.replaceFirst("^/", "").split("/");
            if (parts.length < 3) {
                System.err.println("🔓 [LockServlet] Invalid beacon path: " + pathInfo);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid path\"}");
                return;
            }
            
            String facetType = parts[0];
            int objectId = Integer.parseInt(parts[1]);
            
            System.out.println("🔓 [LockServlet] Releasing lock for: " + facetType + "/" + objectId);
            
            // Parse request body to get token
            JsonObject json = null;
            try {
                json = JsonUtil.parseJsonFromRequest(request.getReader());
            } catch (Exception e) {
                System.err.println("🔓 [LockServlet] Could not parse beacon body: " + e.getMessage());
            }
            
            // Try to get user ID from token in body, or from request attribute
            int userId = 0;
            if (json != null) {
                String token = JsonUtil.getJsonString(json, "token");
                if (token != null && !token.isEmpty()) {
                    // Validate token and get user ID using JwtUtil
                    try {
                        Integer tokenUserId = com.example.budg_v2.util.JwtUtil.getUserIdFromToken(token);
                        if (tokenUserId != null && tokenUserId > 0) {
                            userId = tokenUserId;
                        }
                    } catch (Exception e) {
                        System.err.println("🔓 [LockServlet] Error parsing token: " + e.getMessage());
                    }
                    System.out.println("🔓 [LockServlet] User ID from token: " + userId);
                }
            }
            
            // Fallback to request attribute
            if (userId <= 0) {
                userId = UserContextUtil.getCurrentUserId(request);
                System.out.println("🔓 [LockServlet] User ID from request: " + userId);
            }
            
            if (userId <= 0) {
                System.err.println("🔓 [LockServlet] Could not determine user ID for beacon release");
                // Still try to release by object - check if lock is not permanent
                // This is a fallback for when the user context is lost
            }
            
            int moduleId = ModuleResolver.getModuleId(facetType);
            System.out.println("🔓 [LockServlet] Module ID: " + moduleId);
            
            // Use the beacon-specific release method that handles temporary locks
            // This method only releases TEMPORARY locks and handles the case where userId might be 0
            boolean released = lockDAO.releaseTemporaryLockForBeacon(moduleId, objectId, userId);
            System.out.println("🔓 [LockServlet] Lock release result: " + released);
            
            JsonObject resp = new JsonObject();
            resp.addProperty("success", released);
            if (released) {
                resp.addProperty("message", "Lock released via beacon");
            } else {
                resp.addProperty("message", "Could not release lock");
            }
            response.getWriter().write(resp.toString());
            
        } catch (NumberFormatException e) {
            System.err.println("🔓 [LockServlet] Invalid object ID in beacon path: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid object ID\"}");
        } catch (SQLException e) {
            System.err.println("🔓 [LockServlet] Database error in beacon release: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
    
    /**
     * Handle toggle permanent lock request
     */
    private void handleTogglePermanent(HttpServletRequest request, HttpServletResponse response, String pathInfo, int userId) throws IOException {
        try {
            // Extract lock ID from path
            String[] parts = pathInfo.split("/");
            int lockId = Integer.parseInt(parts[1]);
            
            // Parse request body
            JsonObject json = JsonUtil.parseJsonFromRequest(request.getReader());
            Boolean isPermanentObj = JsonUtil.getJsonBoolean(json, "isPermanent");
            boolean isPermanent = isPermanentObj != null && isPermanentObj;
            
            // Update lock permanent status
            String sql = "UPDATE object_lock SET Is_Permanent = ?, Updated_Datetime = NOW() WHERE ID = ? AND LockedBy_ID = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, isPermanent);
                ps.setInt(2, lockId);
                ps.setInt(3, userId);
                
                int updated = ps.executeUpdate();
                
                if (updated == 0) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("error", "You don't have permission to modify this lock");
                    response.getWriter().write(error.toString());
                    return;
                }
                
                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("message", isPermanent ? "Lock made permanent" : "Permanent status removed");
                resp.addProperty("isPermanent", isPermanent);
                response.getWriter().write(resp.toString());
            }
            
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(error.toString());
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "Invalid lock ID");
            response.getWriter().write(error.toString());
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        String pathInfo = request.getPathInfo();
        int userId = UserContextUtil.getCurrentUserId(request);
        
        // Check if user is super admin
        boolean isSuperAdmin = isSuperAdmin(request);
        
        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Lock ID or object path required\"}");
                return;
            }
            
            String[] parts = pathInfo.replaceFirst("^/", "").split("/");
            
            // Release all user locks: DELETE /api/lock/release-all
            if (parts.length == 1 && "release-all".equals(parts[0])) {
                int released = lockDAO.releaseAllUserLocks(userId);
                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("releasedCount", released);
                resp.addProperty("message", released + " lock(s) released");
                response.getWriter().write(resp.toString());
                return;
            }
            
            if (parts.length == 1) {
                // Delete by lock ID: /api/lock/{lockId} - admin/super admin only (manage locks in admin panel)
                if (!isAdminOrSuperAdmin(request)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Only administrators can release locks from the admin panel. Use My Locked Items to release your own temporary locks.\"}");
                    return;
                }
                int lockId = Integer.parseInt(parts[0]);
                
                // Capture old state before deletion
                Map<String, Object> oldState = getLockInfoById(lockId);
                
                boolean released = lockDAO.releaseLockById(lockId);
                
                if (released && oldState != null && !oldState.isEmpty()) {
                    // Log activity - Delete case
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("component", ActivityLogConstants.COMPONENT_OBJECT_LOCK);
                    
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_MANAGE_LOCKS,
                        ActivityLogConstants.COMPONENT_OBJECT_LOCK, ActivityLogConstants.CHANGE_TYPE_DELETE,
                        oldState, null, contextMap);
                }
                
                JsonObject resp = new JsonObject();
                resp.addProperty("success", released);
                if (!released) {
                    resp.addProperty("error", "Failed to release lock or lock not found");
                }
                response.getWriter().write(resp.toString());
                return;
            }
            
            if (parts.length >= 2) {
                // Release lock by module and object: /api/lock/{moduleName}/{objectId}
                String moduleName = parts[0];
                int objectId = Integer.parseInt(parts[1]);
                
                int moduleId = ModuleResolver.getModuleId(moduleName);
                
                // Capture old state before deletion
                Map<String, Object> oldState = getLockInfoByModuleAndObject(moduleId, objectId);
                
                // Check if lock exists and is permanent before attempting release
                boolean isPermanent = false;
                if (oldState != null && !oldState.isEmpty()) {
                    Object permanentObj = oldState.get("isPermanent");
                    isPermanent = permanentObj instanceof Boolean ? (Boolean) permanentObj : false;
                }
                
                boolean released = lockDAO.releaseLock(moduleId, objectId, userId, isSuperAdmin);
                
                if (released && oldState != null && !oldState.isEmpty()) {
                    // Log activity - Delete case
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("component", ActivityLogConstants.COMPONENT_OBJECT_LOCK);
                    
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_MANAGE_LOCKS,
                        ActivityLogConstants.COMPONENT_OBJECT_LOCK, ActivityLogConstants.CHANGE_TYPE_DELETE,
                        oldState, null, contextMap);
                }
                
                JsonObject resp = new JsonObject();
                resp.addProperty("success", released);
                if (!released) {
                    resp.addProperty("error", "Failed to release lock. You may not have permission or lock does not exist.");
                    resp.addProperty("userId", userId);
                    resp.addProperty("moduleId", moduleId);
                    resp.addProperty("objectId", objectId);
                    resp.addProperty("isPermanent", isPermanent);
                }
                response.getWriter().write(resp.toString());
                return;
            }
            
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid path\"}");
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid ID\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error";
            response.getWriter().write("{\"error\":\"" + msg + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
    
    /**
     * V-04: Strip lock reason/timestamp for unauthorized users.
     * V-09: For non-stakeholder WebUsers who didn't lock the object, hide lock existence entirely.
     *
     * Authorized to see full metadata: SuperAdmin, Admin (responsible segment), Stakeholder, CR Owner.
     * Authorized to see lock icon only: no other roles — they see nothing (status = no_lock).
     */
    private Map<String, Object> filterLockInfoForUser(Map<String, Object> lockInfo, int userId,
            int objectId, String moduleName, HttpServletRequest request) {
        // If object is not locked, nothing to filter
        Boolean locked = (Boolean) lockInfo.get("locked");
        if (locked == null || !locked) {
            return lockInfo;
        }

        // SuperAdmin: full access — return as-is
        if (isSuperAdmin(request)) {
            return lockInfo;
        }

        // Admin: full access within their scope — return as-is
        if (isAdminOrSuperAdmin(request)) {
            return lockInfo;
        }

        // Non-admin user: check if they are the lock owner
        Object lockedByIdObj = lockInfo.get("lockedById");
        int lockedById = lockedByIdObj instanceof Number ? ((Number) lockedByIdObj).intValue() : -1;
        if (userId > 0 && lockedById == userId) {
            // User locked the object themselves — return full info
            return lockInfo;
        }

        // Check if user is a stakeholder on this object
        String facetType = com.example.budg_v2.util.PermissionCheckUtil.normalizeFacetTypeForLock(moduleName);
        boolean isStakeholder = false;
        if (facetType != null && objectId > 0 && userId > 0) {
            isStakeholder = com.example.budg_v2.util.PermissionCheckUtil.isUserStakeholder(userId, objectId, facetType);
        }

        if (isStakeholder) {
            // Stakeholder sees full lock metadata
            return lockInfo;
        }

        // V-09: WebUser who didn't lock and is not a stakeholder — hide lock existence entirely
        Map<String, Object> hidden = new HashMap<>();
        hidden.put("locked", false);
        hidden.put("status", "no_lock");
        return hidden;
    }

    private boolean isSuperAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("userRole");
        if (roleObj == null) return false;
        return AppRoleNames.isSuperAdminName(roleObj.toString());
    }

    /**
     * Admin or super admin can see all locks and release any lock from admin panel.
     */
    private boolean isAdminOrSuperAdmin(HttpServletRequest request) {
        if (isSuperAdmin(request)) return true;
        Object roleObj = request.getAttribute("userRole");
        if (roleObj == null) return false;
        String role = roleObj.toString().trim().toLowerCase().replace('_', ' ').replace('-', ' ');
        return "admin".equals(role) || role.contains("admin");
    }
    
    /**
     * Normalize module name to match what's stored in permissions table
     * The lock API receives module names like "policy", "system", etc.
     * but the permissions table stores them as "Policy", "System", etc.
     */
    private String normalizeModuleName(String moduleName) {
        if (moduleName == null) return null;
        
        // First, handle special cases from ModuleResolver
        return switch (moduleName.toLowerCase().trim()) {
            case "policy", "policies" -> "Policy";
            case "system", "systems" -> "System";
            case "dataset", "datasets", "data sets" -> "Data Sets";
            case "glossary" -> "Glossary";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "interface", "interfaces" -> "Interface";
            case "client", "clients" -> "Client";
            case "committee", "committees" -> "Committee";
            case "geography", "geographies" -> "Geography";
            case "capability", "capabilities" -> "Capability";
            case "business_area", "business-area", "business areas" -> "Business Areas";
            case "org_unit", "org-unit", "org units" -> "Org Units";
            case "people" -> "People";
            default -> capitalizeFirstLetter(moduleName);
        };
    }
    
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    /**
     * Get lock information by lock ID for logging
     */
    private Map<String, Object> getLockInfoById(int lockId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First, get basic lock info without trying to get object name (to avoid table existence issues)
            String sql = "SELECT ol.ID, ol.Module_ID, ol.Object_ID, " +
                        "CONCAT(p.First_Name, ' ', p.Last_Name) as LockedBy_Name, " +
                        "p.Email as LockedBy_Email, " +
                        "m.primaryname as Module_Name " +
                        "FROM object_lock ol " +
                        "LEFT JOIN people p ON ol.LockedBy_ID = p.ID " +
                        "LEFT JOIN module m ON ol.Module_ID = m.id " +
                        "WHERE ol.ID = ?";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, lockId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> lockInfo = new HashMap<>();
                        rs.getInt("Module_ID"); // consumed for result set
                        int objectId = rs.getInt("Object_ID");
                        String moduleName = rs.getString("Module_Name");
                        
                        // Try to get object name separately (with error handling)
                        String objectName = getObjectNameSafely(conn, moduleName, objectId);
                        if (objectName == null) {
                            objectName = (moduleName != null ? moduleName : "Unknown Module") + " - Object " + objectId;
                        }
                        
                        lockInfo.put("objectName", objectName);
                        String lockedByName = rs.getString("LockedBy_Name");
                        lockInfo.put("lockedUserName", lockedByName != null ? lockedByName : "Unknown User");
                        String lockedByEmail = rs.getString("LockedBy_Email");
                        lockInfo.put("lockedUserEmail", lockedByEmail != null ? lockedByEmail : "Unknown Email");
                        return lockInfo;
                    }
                }
            }
        } catch (SQLException e) {
            // Log error but don't fail the operation
            System.err.println("Error getting lock info: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Safely get object name, handling cases where table might not exist
     */
    private String getObjectNameSafely(Connection conn, String moduleName, int objectId) {
        if (moduleName == null) {
            return null;
        }
        
        try {
            String sql = null;
            String columnName = null;
            
            // Map module names to table/column names
            switch (moduleName) {
                case "Data Sets":
                    sql = "SELECT PrimaryName FROM dataset WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Glossary":
                    sql = "SELECT Name FROM glossary WHERE ID = ?";
                    columnName = "Name";
                    break;
                case "Systems":
                    sql = "SELECT PrimaryName FROM system WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Processes":
                    sql = "SELECT PrimaryName FROM process WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Products":
                    sql = "SELECT PrimaryName FROM product WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Projects":
                    sql = "SELECT PrimaryName FROM project WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Clients":
                    sql = "SELECT PrimaryName FROM client WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Interfaces":
                    sql = "SELECT PrimaryName FROM interface WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Policies":
                    sql = "SELECT PrimaryName FROM policy WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Regulations":
                    sql = "SELECT PrimaryName FROM regulation WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Regulators":
                    sql = "SELECT PrimaryName FROM regulator WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Capabilities":
                    sql = "SELECT PrimaryName FROM capability WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Business Areas":
                    sql = "SELECT PrimaryName FROM business_area WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Committees":
                    sql = "SELECT PrimaryName FROM committee WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "Geographies":
                    sql = "SELECT PrimaryName FROM geography WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                case "People":
                    sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as Name FROM people WHERE ID = ?";
                    columnName = "Name";
                    break;
                case "Org Units":
                    sql = "SELECT PrimaryName FROM org_unit WHERE ID = ?";
                    columnName = "PrimaryName";
                    break;
                default:
                    return null; // Unknown module
            }
            
            if (sql != null) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, objectId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString(columnName);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist or query failed - return null to use fallback
            // Don't log here as this is expected for some modules
        }
        
        return null;
    }
    
    /**
     * Get lock information by module and object ID for logging
     */
    private Map<String, Object> getLockInfoByModuleAndObject(int moduleId, int objectId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First, get basic lock info without trying to get object name (to avoid table existence issues)
            String sql = "SELECT ol.ID, ol.Module_ID, ol.Object_ID, ol.Is_Permanent, " +
                        "CONCAT(p.First_Name, ' ', p.Last_Name) as LockedBy_Name, " +
                        "p.Email as LockedBy_Email, " +
                        "m.primaryname as Module_Name " +
                        "FROM object_lock ol " +
                        "LEFT JOIN people p ON ol.LockedBy_ID = p.ID " +
                        "LEFT JOIN module m ON ol.Module_ID = m.id " +
                        "WHERE ol.Module_ID = ? AND ol.Object_ID = ?";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, moduleId);
                ps.setInt(2, objectId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> lockInfo = new HashMap<>();
                        String moduleName = rs.getString("Module_Name");
                        boolean isPermanent = rs.getBoolean("Is_Permanent");
                        
                        // Try to get object name separately (with error handling)
                        String objectName = getObjectNameSafely(conn, moduleName, objectId);
                        if (objectName == null) {
                            objectName = (moduleName != null ? moduleName : "Unknown Module") + " - Object " + objectId;
                        }
                        
                        lockInfo.put("objectName", objectName);
                        lockInfo.put("isPermanent", isPermanent);
                        String lockedByName = rs.getString("LockedBy_Name");
                        lockInfo.put("lockedUserName", lockedByName != null ? lockedByName : "Unknown User");
                        String lockedByEmail = rs.getString("LockedBy_Email");
                        lockInfo.put("lockedUserEmail", lockedByEmail != null ? lockedByEmail : "Unknown Email");
                        return lockInfo;
                    }
                }
            }
        } catch (SQLException e) {
            // Log error but don't fail the operation
            System.err.println("Error getting lock info: " + e.getMessage());
        }
        return null;
    }
}

