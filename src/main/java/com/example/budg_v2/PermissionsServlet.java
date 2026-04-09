package com.example.budg_v2;

import com.example.budg_v2.dao.PermissionsDAO;
import com.example.budg_v2.dao.PermissionsDAO.PermissionView;
import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.util.ActivityLogHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.budg_v2.util.CorsUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/permissions")
public class PermissionsServlet extends HttpServlet {
    private transient PermissionsDAO dao;
    private transient Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.dao = new PermissionsDAO();
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            List<PermissionView> rows = dao.getAllPermissions();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", rows);
            result.put("count", rows.size());
            resp.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            Map<String, Object> payload = gson.fromJson(body.toString(), new TypeToken<Map<String, Object>>(){}.getType());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> changes = (List<Map<String, Object>>) payload.get("changes");
            
            // Get current user ID from request
            Integer currentUserId = null;
            if (payload.containsKey("currentUserId")) {
                Object userIdObj = payload.get("currentUserId");
                if (userIdObj instanceof Number) {
                    currentUserId = ((Number) userIdObj).intValue();
                }
            }
            
            int created = 0, updated = 0, deleted = 0;
            if (changes != null) {
                for (Map<String, Object> c : changes) {
                    String type = String.valueOf(c.get("type"));
                    String module = (String) c.get("module");
                    String role = (String) c.get("role");
                    
                    // Support both single permission (backward compatible) and multiple permissions (new)
                    String permission = (String) c.get("permission");
                    List<String> permissions = null;
                    List<Integer> permissionIds = null;
                    
                    // Check for multiple permissions (new format)
                    Object permissionsObj = c.get("permissions");
                    if (permissionsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> permList = (List<String>) permissionsObj;
                        permissions = permList;
                    }
                    
                    // Check for permission IDs array
                    Object permissionIdsObj = c.get("permissionIds");
                    if (permissionIdsObj instanceof List) {
                        List<?> rawList = (List<?>) permissionIdsObj;
                        permissionIds = new ArrayList<>();
                        for (Object item : rawList) {
                            if (item instanceof Number) {
                                permissionIds.add(((Number) item).intValue());
                            }
                        }
                    }
                    
                    if ("create".equalsIgnoreCase(type)) {
                        Number moduleIdNum = (Number) c.get("moduleId");
                        Number roleIdNum = (Number) c.get("objectRoleId");
                        Integer moduleId = moduleIdNum != null ? moduleIdNum.intValue() : dao.findModuleIdByPrimaryName(module);
                        Integer roleId = roleIdNum != null ? roleIdNum.intValue() : dao.findRoleIdByPrimaryName(role);
                        
                        // Get permission IDs - ALWAYS prefer permission names over IDs for reliability
                        List<Integer> finalPermissionIds = new ArrayList<>();
                        if (permissions != null && !permissions.isEmpty()) {
                            // Always convert permission names to IDs for accuracy
                            finalPermissionIds = dao.findPermissionIdsByNames(permissions);
                        } else if (permissionIds != null && !permissionIds.isEmpty()) {
                            // Fallback to provided IDs if no names available
                            finalPermissionIds = permissionIds;
                        } else if (permission != null) {
                            // Single permission backward compatibility
                            Integer permId = dao.findPermissionIdByName(permission);
                            if (permId != null) {
                                finalPermissionIds.add(permId);
                            }
                        }
                        
                        int id = dao.insertPermissionWithMultiple(moduleId, roleId, finalPermissionIds, currentUserId);
                        if (id > 0) {
                            created++;
                            // Log activity
                            Map<String, Object> newState = new HashMap<>();
                            newState.put("module", module);
                            newState.put("role", role);
                            newState.put("permissions", permissions != null ? permissions : (permission != null ? List.of(permission) : List.of()));
                            Map<String, Object> contextMap = new HashMap<>();
                            contextMap.put("role", role);
                            contextMap.put("module", module);
                            contextMap.put("permissions", newState.get("permissions"));
                            ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_ROLE_PERMISSIONS,
                                role, ActivityLogConstants.CHANGE_TYPE_CREATE, null, newState, contextMap);
                        }
                    } else if ("update".equalsIgnoreCase(type)) {
                        Number idNum = (Number) c.get("id");
                        if (idNum == null) continue;
                        int id = idNum.intValue();
                        
                        // Get old state before update
                        PermissionView oldPermission = dao.getPermissionById(id);
                        Map<String, Object> oldState = new HashMap<>();
                        if (oldPermission != null) {
                            oldState.put("module", oldPermission.getModule());
                            oldState.put("role", oldPermission.getRole());
                            oldState.put("permissions", oldPermission.getPermissions());
                        }
                        
                        Number moduleIdNum = (Number) c.get("moduleId");
                        Number roleIdNum = (Number) c.get("objectRoleId");
                        Integer moduleId = moduleIdNum != null ? moduleIdNum.intValue() : dao.findModuleIdByPrimaryName(module);
                        Integer roleId = roleIdNum != null ? roleIdNum.intValue() : dao.findRoleIdByPrimaryName(role);
                        
                        // Get permission IDs - ALWAYS prefer permission names over IDs for reliability
                        // This ensures correct mapping even if frontend sends incorrect IDs
                        List<Integer> finalPermissionIds = new ArrayList<>();
                        if (permissions != null && !permissions.isEmpty()) {
                            // Always convert permission names to IDs for accuracy
                            finalPermissionIds = dao.findPermissionIdsByNames(permissions);
                        } else if (permissionIds != null && !permissionIds.isEmpty()) {
                            // Fallback to provided IDs if no names available
                            finalPermissionIds = permissionIds;
                        } else if (permission != null) {
                            // Single permission backward compatibility
                            Integer permId = dao.findPermissionIdByName(permission);
                            if (permId != null) {
                                finalPermissionIds.add(permId);
                            }
                        }
                        
                        int result = dao.updatePermissionWithMultiple(id, moduleId, roleId, finalPermissionIds, currentUserId);
                        if (result > 0) {
                            updated++;
                            Map<String, Object> newState = new HashMap<>();
                            newState.put("module", module);
                            newState.put("role", role);
                            newState.put("permissions", permissions != null ? permissions : (permission != null ? List.of(permission) : List.of()));
                            Map<String, Object> contextMap = new HashMap<>();
                            contextMap.put("role", role);
                            contextMap.put("module", module);
                            contextMap.put("permissions", newState.get("permissions"));
                            ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_ROLE_PERMISSIONS,
                                role, ActivityLogConstants.CHANGE_TYPE_UPDATE, oldState, newState, contextMap);
                        }
                    } else if ("delete".equalsIgnoreCase(type)) {
                        Number idNum = (Number) c.get("id");
                        if (idNum == null) continue;
                        int id = idNum.intValue();
                        
                        // Get old state before delete
                        PermissionView oldPermission = dao.getPermissionById(id);
                        Map<String, Object> oldState = new HashMap<>();
                        if (oldPermission != null) {
                            oldState.put("module", oldPermission.getModule());
                            oldState.put("role", oldPermission.getRole());
                            oldState.put("permissions", oldPermission.getPermissions());
                        }
                        
                        int result = dao.deletePermission(id);
                        if (result > 0) {
                            deleted++;
                            String roleName = oldState.get("role") != null ? oldState.get("role").toString() : "Unknown Role";
                            Map<String, Object> contextMap = new HashMap<>();
                            contextMap.put("role", oldState.get("role"));
                            contextMap.put("module", oldState.get("module"));
                            contextMap.put("permissions", oldState.get("permissions"));
                            ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_ROLE_PERMISSIONS,
                                roleName, ActivityLogConstants.CHANGE_TYPE_DELETE, oldState, null, contextMap);
                        }
                    }
                }
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("created", created);
            result.put("updated", updated);
            result.put("deleted", deleted);
            resp.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }

    @Override
    protected void doOptions(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse resp) throws IOException {
        CorsUtil.handlePreflight(resp);
    }
}


