package com.example.budg_v2;

import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Servlet to get user permissions
 * Returns permission info for the current user
 * 
 * Endpoints:
 * - GET /api/user/permissions - Get all permissions for current user
 * - GET /api/user/permissions/{module} - Get permissions for specific module
 */
@WebServlet("/api/user/permissions/*")
public class UserPermissionsServlet extends HttpServlet {
    
    private transient PermissionService permissionService;
    private transient Gson gson;
    
    @Override
    public void init() throws ServletException {
        super.init();
        this.permissionService = new PermissionService();
        this.gson = new Gson();
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        try {
            int userId = UserContextUtil.getCurrentUserId(req);
            
            if (userId <= 0) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Authentication required");
                resp.getWriter().write(gson.toJson(error));
                return;
            }
            
            String pathInfo = req.getPathInfo();
            boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
            
            if (pathInfo == null || pathInfo.equals("/")) {
                // Return all permissions for user
                Map<String, Set<String>> allPermissions = permissionService.getUserPermissions(userId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("userId", userId);
                result.put("isAdmin", isAdmin);
                result.put("permissions", allPermissions);
                
                resp.getWriter().write(gson.toJson(result));
            } else {
                // Return permissions for specific module
                String moduleName = pathInfo.substring(1); // Remove leading slash
                
                PermissionService.PermissionResult permResult = 
                    permissionService.checkAllPermissions(userId, moduleName);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("userId", userId);
                result.put("module", moduleName);
                result.put("isAdmin", permResult.isAdmin());
                result.put("canView", permResult.isCanView());
                result.put("canCreate", permResult.isCanCreate());
                result.put("canEdit", permResult.isCanEdit());
                result.put("canDelete", permResult.isCanDelete());
                
                resp.getWriter().write(gson.toJson(result));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.handlePreflight(resp);
    }
}
