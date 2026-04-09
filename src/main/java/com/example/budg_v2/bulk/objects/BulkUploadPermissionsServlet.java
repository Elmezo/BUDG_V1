package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Servlet to get user permissions for bulk upload
 * Endpoint: /api/bulk/permissions?userId={userId}
 * Returns: JSON with allowed entities and operations
 */
@WebServlet("/api/bulk/permissions")
public class BulkUploadPermissionsServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadPermissionsServlet.class);
    private static final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(request, response);

        try {
            // Get userId from request attributes (set by AuthFilter)
            Integer userId = null;
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr instanceof Integer) {
                userId = (Integer) userIdAttr;
            } else if (userIdAttr != null) {
                try {
                    userId = Integer.parseInt(String.valueOf(userIdAttr));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid userId format in request attributes: {}", userIdAttr);
                }
            }
            
            // Return error if no userId found (user not authenticated)
            if (userId == null) {
                logger.error("No userId found in request attributes - user not authenticated");
                sendErrorResponse(response, "User not authenticated", 401);
                return;
            }
            


            logger.debug("Getting permissions for userId: {}", userId);

            // Check if user is Super Admin (all permissions granted)
            String userRole = (String) request.getAttribute("userRole");
            boolean isSuperAdminUser = userRole != null && 
                (userRole.equalsIgnoreCase("Super Admin") || userRole.equalsIgnoreCase("Suber Admin"));
            
            logger.debug("User role: {}, isSuperAdmin: {}", userRole, isSuperAdminUser);

            // Get user permissions from database for non-Super Admin (Admin/Web User)
            PermissionService permissionService = new PermissionService();

            // Build response with allowed entities
            JsonObject responseJson = new JsonObject();
            JsonObject allowedEntities = new JsonObject();
            
            // Map of entity names to module names
            Map<String, String> entityModuleMap = new HashMap<>();
            entityModuleMap.put("System", "System");
            entityModuleMap.put("Regulation", "Regulation");
            entityModuleMap.put("Regulator", "Regulator");
            entityModuleMap.put("Geography", "Geography");
            entityModuleMap.put("Regulatory Theme", "Regulatory Theme");
            entityModuleMap.put("Committee", "Committee");
            entityModuleMap.put("Policy", "Policy");
            entityModuleMap.put("Dataset", "Dataset");
            entityModuleMap.put("Attribute", "Attribute");
            entityModuleMap.put("Glossary", "Glossary");
            entityModuleMap.put("Interface", "Interface");
            entityModuleMap.put("Process", "Process");
            entityModuleMap.put("Project", "Project");
            entityModuleMap.put("Business Area", "Business Area");
            entityModuleMap.put("Capability", "Capability");
            entityModuleMap.put("Client", "Client");
            entityModuleMap.put("Legal", "Legal");
            entityModuleMap.put("Org. Unit", "Org. Unit");
            entityModuleMap.put("People", "People");
            entityModuleMap.put("Product", "Product");
            
            // Check each entity
            for (Map.Entry<String, String> entry : entityModuleMap.entrySet()) {
                String entityName = entry.getKey();
                String moduleName = entry.getValue();
                
                // Super Admin has all permissions
                boolean canCreate, canUpdate, canDelete;
                if (isSuperAdminUser) {
                    canCreate = true;
                    canUpdate = true;
                    canDelete = true;
                } else {
                    // Check if user has permissions for this module (DB uses "New" and "Edit")
                    Set<String> modulePerms = permissionService.getModulePermissions(userId, moduleName);
                    canCreate = modulePerms.contains(PermissionService.PERMISSION_NEW);
                    canUpdate = modulePerms.contains(PermissionService.PERMISSION_EDIT);
                    canDelete = permissionService.canDelete(userId, moduleName);
                }
                
                JsonObject entityPermissions = new JsonObject();
                entityPermissions.addProperty("canCreate", canCreate);
                entityPermissions.addProperty("canUpdate", canUpdate);
                entityPermissions.addProperty("canDelete", canDelete);
                entityPermissions.addProperty("canUpload", canCreate); // Can upload if can create
                
                allowedEntities.add(entityName, entityPermissions);
            }
            
            responseJson.add("allowedEntities", allowedEntities);
            responseJson.addProperty("userId", userId);
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            logger.error("Error getting user permissions", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        
        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
}

