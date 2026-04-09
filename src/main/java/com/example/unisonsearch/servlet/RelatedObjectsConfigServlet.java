package com.example.unisonsearch.servlet;

import com.example.unisonsearch.config.RelatedObjectsConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for managing Related Objects configuration.
 * Allows admins to enable/disable related objects globally or per facet.
 */
@WebServlet("/api/unison-search/related-objects/config")
public class RelatedObjectsConfigServlet extends HttpServlet {
    
    private final Gson gson = new Gson();
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        try {
            RelatedObjectsConfig config = RelatedObjectsConfig.getInstance();
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("enabled", config.isEnabled());
            response.put("maxDepth", config.getMaxDepth());
            response.put("facetSettings", config.getAllFacetSettings());
            response.put("summary", config.getSummary());
            
            PrintWriter out = resp.getWriter();
            out.print(gson.toJson(response));
            out.flush();
            
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to get config: " + e.getMessage());
            
            PrintWriter out = resp.getWriter();
            out.print(gson.toJson(error));
            out.flush();
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        // Check if user is admin (basic check - enhance with proper role check)
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Authentication required");
            
            PrintWriter out = resp.getWriter();
            out.print(gson.toJson(error));
            out.flush();
            return;
        }
        
        if (!isAdmin(session)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Admin access required");
            PrintWriter out = resp.getWriter();
            out.print(gson.toJson(error));
            out.flush();
            return;
        }
        
        try {
            // Read request body
            BufferedReader reader = req.getReader();
            JsonObject requestBody = gson.fromJson(reader, JsonObject.class);
            
            RelatedObjectsConfig config = RelatedObjectsConfig.getInstance();
            
            // Update global enabled flag
            if (requestBody.has("enabled")) {
                boolean enabled = requestBody.get("enabled").getAsBoolean();
                config.setEnabled(enabled);
            }
            
            // Update max depth
            if (requestBody.has("maxDepth")) {
                int maxDepth = requestBody.get("maxDepth").getAsInt();
                config.setMaxDepth(maxDepth);
            }
            
            // Update per-facet settings
            if (requestBody.has("facetSettings")) {
                JsonObject facetSettings = requestBody.getAsJsonObject("facetSettings");
                for (String facet : facetSettings.keySet()) {
                    boolean enabled = facetSettings.get(facet).getAsBoolean();
                    config.setEnabledForFacet(facet, enabled);
                }
            }
            
            // Handle reset to defaults
            if (requestBody.has("reset") && requestBody.get("reset").getAsBoolean()) {
                config.resetToDefaults();
            }
            
            // Build success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration updated successfully");
            response.put("enabled", config.isEnabled());
            response.put("maxDepth", config.getMaxDepth());
            response.put("summary", config.getSummary());
            
            PrintWriter out = resp.getWriter();
            out.print(gson.toJson(response));
            out.flush();
            
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to update config: " + e.getMessage());
            
            PrintWriter out = resp.getWriter();
            out.print(gson.toJson(error));
            out.flush();
        }
    }
    
    // Helper method to check if user is admin (to be implemented)
    private boolean isAdmin(HttpSession session) {
        // TODO: Implement proper admin check
        // Example: check user role from session or database
        // Object userRole = session.getAttribute("userRole");
        // return "ADMIN".equals(userRole);
        return true; // Temporarily allow all authenticated users
    }
}

