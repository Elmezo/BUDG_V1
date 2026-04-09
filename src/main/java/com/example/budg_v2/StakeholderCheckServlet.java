package com.example.budg_v2;

import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.UserContextUtil;
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

/**
 * Servlet to check if user is a stakeholder on a specific object
 * 
 * Endpoints:
 * - GET /api/check-stakeholder/{moduleName}/{objectId} - Check if current user is stakeholder
 */
@WebServlet("/api/check-stakeholder/*")
public class StakeholderCheckServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(StakeholderCheckServlet.class);
    private static final Gson gson = new Gson();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        CorsUtil.setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid path format. Expected: /{moduleName}/{objectId}");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Expected format: /ModuleName/ObjectId
            String[] parts = pathInfo.substring(1).split("/");
            
            if (parts.length != 2) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid path format. Expected: /{moduleName}/{objectId}");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            String moduleName = parts[0];
            int objectId;
            try {
                objectId = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid object ID format");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Not authenticated");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Normalize module name to facet type
            String facetType = normalizeModuleNameToFacetType(moduleName);
            if (facetType == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unknown module name: " + moduleName);
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            boolean isStakeholder = PermissionCheckUtil.isUserStakeholder(userId, objectId, facetType);
            
            Map<String, Object> result = new HashMap<>();
            result.put("isStakeholder", isStakeholder);
            result.put("moduleName", moduleName);
            result.put("objectId", objectId);
            result.put("facetType", facetType);
            result.put("userId", userId);
            
            logger.debug("User {} is {} stakeholder on {} object {} (facet: {})", 
                userId, isStakeholder ? "a" : "not a", moduleName, objectId, facetType);
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            logger.error("Error checking stakeholder status", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
    
    /**
     * Normalize module name to facet type for stakeholder checks
     */
    private String normalizeModuleNameToFacetType(String moduleName) {
        if (moduleName == null) {
            return null;
        }
        
        // Map common module names to facet types
        String normalized = moduleName.toLowerCase()
            .replace(" ", "-")
            .replace("_", "-");
        
        // Handle special cases
        if (normalized.equals("data-sets") || normalized.equals("dataset")) {
            return "dataset";
        }
        if (normalized.equals("system-interface") || normalized.equals("interface")) {
            return "interface";
        }
        if (normalized.equals("business-area") || normalized.equals("businessarea")) {
            return "business-area";
        }
        if (normalized.equals("legal-entity") || normalized.equals("legalentity")) {
            return "legal-entity";
        }
        if (normalized.equals("org-unit") || normalized.equals("orgunit")) {
            return "org-unit";
        }
        if (normalized.equals("regulatory-theme") || normalized.equals("regulatorytheme")) {
            return "regulatory-theme";
        }
        
        return normalized;
    }
}
