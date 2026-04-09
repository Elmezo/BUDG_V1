package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.model.Role;
import com.example.budg_v2.service.RoleService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.dao.RoleDAO;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.AppRoleNames;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/roles")
public class RoleServlet extends HttpServlet {

    private RoleService roleService;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.roleService = new RoleService();
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            List<Role> roles;
            if ("1".equals(request.getParameter("forBulkUploadByAdmin"))) {
                Integer userId = null;
                String userIdStr = request.getParameter("userId");
                if (userIdStr != null && !userIdStr.trim().isEmpty()) {
                    try {
                        userId = Integer.parseInt(userIdStr.trim());
                    } catch (NumberFormatException ignored) {}
                }
                if (userId != null && isAdmin(userId)) {
                    roles = roleService.getRolesForBulkUploadByAdmin();
                } else {
                    roles = roleService.getAllRoles();
                }
            } else {
                roles = roleService.getAllRoles();
            }
            String jsonRoles = gson.toJson(roles);
            response.getWriter().write(jsonRoles);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Unexpected error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    private boolean isAdmin(int userId) {
        try {
            if (SegmentAccessService.isSuperAdmin(userId)) return true;
            String role = SegmentAccessService.getUserRole(userId);
            return role != null && "admin".equalsIgnoreCase(role.trim());
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Roles & Responsibilities editing is Super Admin only
        Object roleAttr = request.getAttribute("userRole");
        if (!AppRoleNames.isSuperAdminName(roleAttr != null ? roleAttr.toString() : "")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"success\":false,\"error\":\"Forbidden: Super Admin role required to modify roles\"}");
            return;
        }

        try {
            // Read request body
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                requestBody.append(line);
            }

            // Parse JSON request
            String jsonRequest = requestBody.toString();
            //system.out.println("Received roles request: " + jsonRequest);

            // Parse the request to extract changes, newRoles, and deletedIds
            try {
                // Parse JSON using Gson
                com.google.gson.JsonObject jsonObject = gson.fromJson(jsonRequest, com.google.gson.JsonObject.class);
                
                int totalChanges = 0;
                int updates = 0;
                int newRoles = 0;
                int deletions = 0;
                
                // Process updates
                if (jsonObject.has("changes")) {
                    com.google.gson.JsonArray changes = jsonObject.getAsJsonArray("changes");
                    updates = changes.size();
                    for (com.google.gson.JsonElement change : changes) {
                        com.google.gson.JsonObject changeObj = change.getAsJsonObject();
                        processRoleUpdate(changeObj, request);
                    }
                }
                
                // Process new roles
                if (jsonObject.has("newRoles")) {
                    com.google.gson.JsonArray newRolesArray = jsonObject.getAsJsonArray("newRoles");
                    newRoles = newRolesArray.size();
                    //system.out.println("Processing " + newRoles + " new roles");
                    for (com.google.gson.JsonElement newRole : newRolesArray) {
                        com.google.gson.JsonObject newRoleObj = newRole.getAsJsonObject();
                        //system.out.println("Processing new role: " + newRoleObj.toString());
                        processNewRole(newRoleObj, request);
                    }
                } else {
                    //system.out.println("No newRoles found in request");
                }
                
                // Process deletions
                if (jsonObject.has("deletedIds")) {
                    com.google.gson.JsonArray deletedIds = jsonObject.getAsJsonArray("deletedIds");
                    deletions = deletedIds.size();
                    for (com.google.gson.JsonElement deletedId : deletedIds) {
                        int id = deletedId.getAsInt();
                        try {
                            processRoleDelete(id, request);
                            //system.out.println("Deleted role with ID: " + id);
                        } catch (Exception deleteException) {
                            // Handle deletion errors specifically
                            System.err.println("Error deleting role: " + deleteException.getMessage());
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            // Escape quotes in error message for JSON
                            String errorMsg = deleteException.getMessage().replace("\"", "\\\"");
                            response.getWriter().write("{\"error\": \"" + errorMsg + "\"}");
                            return;
                        }
                    }
                }
                
                totalChanges = updates + newRoles + deletions;
                
                if (totalChanges == 0) {
                    //system.out.println("No changes to process");
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write("{\"success\": true, \"message\": \"No changes to save\"}");
                } else {
                    //system.out.println("Roles data processed successfully: " + updates + " updates, " + newRoles + " new roles, " + deletions + " deletions");
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write("{\"success\": true, \"message\": \"Changes saved successfully!\"}");
                }
                
            } catch (com.google.gson.JsonParseException parseException) {
                System.err.println("Error parsing roles request: " + parseException.getMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String errorMsg = parseException.getMessage().replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
                response.getWriter().write("{\"error\": \"Invalid request format: " + errorMsg + "\"}");
            } catch (Exception otherException) {
                // Handle other exceptions (like SQLException from updates/creates)
                System.err.println("Error processing roles request: " + otherException.getMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String errorMsg = otherException.getMessage().replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
                response.getWriter().write("{\"error\": \"" + errorMsg + "\"}");
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Failed to save roles: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    private void processRoleUpdate(com.google.gson.JsonObject changeObj, HttpServletRequest request) throws Exception {
        int id = changeObj.get("id").getAsInt();
        String primaryName = changeObj.get("primaryName").getAsString();
        String description = changeObj.get("description").getAsString();
        String defaultVal = changeObj.get("default").getAsString();
        String facet = changeObj.get("facet").getAsString();
        String roleType = changeObj.get("roleType").getAsString();

        // Capture old state before update
        java.util.Map<String, Object> oldState = getRoleStateById(id);

        // Get module ID
        Integer moduleId = roleService.getModuleIdByName(facet);
        if (moduleId == null) {
            throw new Exception("Module not found: " + facet);
        }

        // Get role type ID
        Integer roleTypeId = roleService.getRoleTypeIdByName(roleType);
        if (roleTypeId == null) {
            throw new Exception("Role type not found: " + roleType);
        }

        // Create ObjectRole object
        RoleDAO.ObjectRole role = new RoleDAO.ObjectRole();
        role.setId(id);
        role.setModule(moduleId);
        role.setPrimaryname(primaryName);
        role.setDescription(description);
        role.setDefaultrole("Yes".equals(defaultVal));
        role.setObjectroletypeId(roleTypeId);

        // Update in database
        boolean success = roleService.updateObjectRole(role);
        if (success) {
            // Log activity - Update case
            java.util.Map<String, Object> newState = buildRoleState(primaryName, description, defaultVal, facet, roleType);
            // Pass contextMap with role name for component name
            java.util.Map<String, Object> contextMap = new java.util.HashMap<>();
            contextMap.put("primaryName", primaryName);
            ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_ROLES_RESPONSIBILITIES,
                primaryName, ActivityLogConstants.CHANGE_TYPE_UPDATE,
                oldState, newState, contextMap);
            //system.out.println("Updated role: " + primaryName);
        } else {
            throw new Exception("Failed to update role: " + primaryName);
        }
    }

    private void processNewRole(com.google.gson.JsonObject newRoleObj, HttpServletRequest request) throws Exception {
        //system.out.println("=== Processing New Role ===");
        
        String primaryName = newRoleObj.get("primaryName").getAsString();
        String description = newRoleObj.get("description").getAsString();
        String defaultVal = newRoleObj.get("default").getAsString();
        String facet = newRoleObj.get("facet").getAsString();
        String roleType = newRoleObj.get("roleType").getAsString();

        //system.out.println("Primary Name: " + primaryName);
        //system.out.println("Description: " + description);
        //system.out.println("Default: " + defaultVal);
        //system.out.println("Facet: " + facet);
        //system.out.println("Role Type: " + roleType);

        // Get module ID
        Integer moduleId = roleService.getModuleIdByName(facet);
        //system.out.println("Module ID for '" + facet + "': " + moduleId);
        if (moduleId == null) {
            throw new Exception("Module not found: " + facet);
        }

        // Get role type ID
        Integer roleTypeId = roleService.getRoleTypeIdByName(roleType);
        //system.out.println("Role Type ID for '" + roleType + "': " + roleTypeId);
        if (roleTypeId == null) {
            throw new Exception("Role type not found: " + roleType);
        }

        // Create ObjectRole object
        RoleDAO.ObjectRole role = new RoleDAO.ObjectRole();
        role.setModule(moduleId);
        role.setPrimaryname(primaryName);
        role.setDescription(description);
        role.setDefaultrole("Yes".equals(defaultVal));
        role.setObjectroletypeId(roleTypeId);

        //system.out.println("About to insert role into database...");
        // Insert into database
        roleService.insertObjectRole(role);
        
        // Log activity - Create case
        java.util.Map<String, Object> newState = buildRoleState(primaryName, description, defaultVal, facet, roleType);
        // Pass contextMap with role name for component name
        java.util.Map<String, Object> contextMap = new java.util.HashMap<>();
        contextMap.put("primaryName", primaryName);
        ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_ROLES_RESPONSIBILITIES,
            primaryName, ActivityLogConstants.CHANGE_TYPE_CREATE,
            null, newState, contextMap);
        
        //system.out.println("✅ Created new role: " + primaryName + " with ID: " + newId);
    }
    
    private void processRoleDelete(int id, HttpServletRequest request) throws Exception {
        // Capture old state before deletion
        java.util.Map<String, Object> oldState = getRoleStateById(id);
        
        try {
            // Delete from database
            boolean success = roleService.deleteObjectRole(id);
            
            if (success && oldState != null) {
                // Log activity - Delete case
                String roleName = (String) oldState.get("primaryName");
                // Pass contextMap with role name for component name
                java.util.Map<String, Object> contextMap = new java.util.HashMap<>();
                contextMap.put("primaryName", roleName);
                ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_ROLES_RESPONSIBILITIES,
                    roleName, ActivityLogConstants.CHANGE_TYPE_DELETE,
                    oldState, null, contextMap);
            }
        } catch (SQLException e) {
            // Re-throw with a more user-friendly message if it's about foreign key constraint
            if (e.getMessage() != null && e.getMessage().contains("foreign key constraint")) {
                throw new Exception(e.getMessage());
            }
            throw e;
        }
    }
    
    /**
     * Get role state by ID for logging
     */
    private java.util.Map<String, Object> getRoleStateById(int id) {
        try {
            List<RoleDAO.ObjectRole> allRoles = roleService.getAllObjectRoles();
            for (RoleDAO.ObjectRole role : allRoles) {
                if (role.getId() == id) {
                    return buildRoleStateFromObjectRole(role);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting role state: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Build role state map from role data
     */
    private java.util.Map<String, Object> buildRoleState(String primaryName, String description, 
                                                          String defaultVal, String facet, String roleType) {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("primaryName", primaryName);
        state.put("description", description);
        state.put("default", defaultVal);
        state.put("facet", facet);
        state.put("roleType", roleType);
        return state;
    }
    
    /**
     * Build role state map from ObjectRole object
     */
    private java.util.Map<String, Object> buildRoleStateFromObjectRole(RoleDAO.ObjectRole role) {
        // Get facet name directly from module ID (role.getModule() is already the module ID)
        String facet = getFacetNameById(role.getModule());
        String roleType = getRoleTypeNameById(role.getObjectroletypeId());
        
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("primaryName", role.getPrimaryname());
        state.put("description", role.getDescription());
        state.put("default", role.isDefaultrole() ? "Yes" : "No");
        state.put("facet", facet);
        state.put("roleType", roleType);
        return state;
    }
    
    /**
     * Get facet name by module ID
     */
    private String getFacetNameById(int moduleId) {
        try {
            java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
            String sql = "SELECT primaryname FROM module WHERE id = ?";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, moduleId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("primaryname");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting facet name: " + e.getMessage());
        }
        return "Unknown";
    }
    
    /**
     * Get role type name by ID
     */
    private String getRoleTypeNameById(int roleTypeId) {
        try {
            // Query object_role_type table
            java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
            String sql = "SELECT primaryname FROM object_role_type WHERE id = ?";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, roleTypeId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("primaryname");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting role type name: " + e.getMessage());
        }
        return "Unknown";
    }
}
