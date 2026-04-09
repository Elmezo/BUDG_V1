package com.example.unisonsearch.servlet;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.unisonsearch.service.ConfigurationService;
import com.example.unisonsearch.service.UnisonService;
import com.example.unisonsearch.service.UnisonFacetService;
import com.example.unisonsearch.util.FacetNormalizationUtil;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JwtUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for managing Unison facet configurations.
 * Endpoints:
 * GET /api/unison/facets - Get user's facet configuration
 * POST /api/unison/facets/save - Save user's facet layout
 * POST /api/unison/facets/reset - Reset to defaults
 * GET /api/unison/defaults - Get SuperAdmin defaults (if admin)
 * POST /api/unison/defaults/save - Save SuperAdmin defaults (if admin)
 */
@WebServlet(name = "UnisonFacetServlet", urlPatterns = { "/api/unison/*" })
public class UnisonFacetServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private final ConfigurationService configurationService = new ConfigurationService();
    private final UnisonService unisonService = new UnisonService(configurationService);
    private final UnisonFacetService unisonFacetService = new UnisonFacetService(unisonService, configurationService);

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Path not specified\"}");
            return;
        }

        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        try {
            if (pathInfo.equals("/facets")) {
                // Get user's facets
                List<Map<String, Object>> facets = unisonFacetService.getFacetsForUser(userId);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", facets);
                response.getWriter().write(gson.toJson(result));
            } else if (pathInfo.equals("/defaults")) {
                // Get SuperAdmin defaults (check if user is admin)
                if (!isAdmin(userId)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Forbidden - Admin access required\"}");
                    return;
                }
                JsonObject defaults = unisonFacetService.getSuperAdminDefaults();
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", defaults != null ? gson.fromJson(defaults.toString(), Object.class) : null);
                response.getWriter().write(gson.toJson(result));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\":\"Endpoint not found\"}");
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Path not specified\"}");
            return;
        }

        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        try {
            if (pathInfo.equals("/facets/save")) {
                // Save user's facet layout
                String body = getRequestBody(request);
                JsonObject json = gson.fromJson(body, JsonObject.class);
                JsonArray facetsArray = json.getAsJsonArray("facets");
                
                // Get old state before update
                List<Map<String, Object>> oldFacets = unisonFacetService.getFacetsForUser(userId);
                Map<String, Map<String, Object>> oldFacetsMap = new HashMap<>();
                for (Map<String, Object> oldFacet : oldFacets) {
                    oldFacetsMap.put((String) oldFacet.get("facetId"), oldFacet);
                }
                
                List<Map<String, Object>> facets = new ArrayList<>();
                for (int i = 0; i < facetsArray.size(); i++) {
                    JsonObject facet = facetsArray.get(i).getAsJsonObject();
                    Map<String, Object> facetMap = new HashMap<>();
                    String canonicalFacetId = FacetNormalizationUtil.normalizeToCanonical(facet.get("facetId").getAsString());
                    if (canonicalFacetId == null) {
                        continue;
                    }
                    facetMap.put("facetId", canonicalFacetId);
                    facetMap.put("active", facet.get("active").getAsBoolean());
                    facetMap.put("activeFields", facet.has("activeFields") ? facet.get("activeFields").getAsString() : "");
                    facetMap.put("ordering", facet.get("ordering").getAsInt());
                    facets.add(facetMap);
                }
                
                unisonFacetService.updateFacets(userId, facets);
                
                // Get new state after update
                List<Map<String, Object>> newFacets = unisonFacetService.getFacetsForUser(userId);
                Map<String, Map<String, Object>> newFacetsMap = new HashMap<>();
                for (Map<String, Object> newFacet : newFacets) {
                    newFacetsMap.put((String) newFacet.get("facetId"), newFacet);
                }
                
                // Log activity for each changed facet
                for (Map<String, Object> facet : facets) {
                    String facetId = (String) facet.get("facetId");
                    Map<String, Object> oldFacet = oldFacetsMap.get(facetId);
                    Map<String, Object> newFacet = newFacetsMap.get(facetId);
                    
                    if (oldFacet != null && newFacet != null) {
                        // Check if anything changed
                        boolean changed = false;
                        if (!oldFacet.get("active").equals(newFacet.get("active"))) changed = true;
                        if (!oldFacet.get("activeFields").equals(newFacet.get("activeFields"))) changed = true;
                        if (!oldFacet.get("ordering").equals(newFacet.get("ordering"))) changed = true;
                        
                        if (changed) {
                            Map<String, Object> oldState = new HashMap<>();
                            oldState.put("facetId", facetId);
                            oldState.put("active", oldFacet.get("active"));
                            oldState.put("activeFields", oldFacet.get("activeFields"));
                            oldState.put("ordering", oldFacet.get("ordering"));
                            
                            Map<String, Object> newState = new HashMap<>();
                            newState.put("facetId", facetId);
                            newState.put("active", newFacet.get("active"));
                            newState.put("activeFields", newFacet.get("activeFields"));
                            newState.put("ordering", newFacet.get("ordering"));
                            
                            // Component = "Facet Name + Unison Grid"
                            String component = facetId + " - " + ActivityLogConstants.COMPONENT_UNISON_GRID;
                            
                            ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_USER_PREFERENCE,
                                component, ActivityLogConstants.CHANGE_TYPE_UPDATE,
                                oldState, newState);
                        }
                    }
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Facets saved successfully");
                response.getWriter().write(gson.toJson(result));
            } else if (pathInfo.equals("/facets/reset")) {
                // Reset to defaults
                unisonFacetService.resetToDefaults(userId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Facets reset to defaults");
                response.getWriter().write(gson.toJson(result));
            } else if (pathInfo.equals("/defaults/save")) {
                // Save SuperAdmin defaults
                if (!isAdmin(userId)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Forbidden - Admin access required\"}");
                    return;
                }
                
                String body = getRequestBody(request);
                JsonObject defaults = gson.fromJson(body, JsonObject.class);
                unisonFacetService.saveSuperAdminDefaults(defaults);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Default layout saved successfully");
                response.getWriter().write(gson.toJson(result));
            } else if (pathInfo.equals("/columns/save")) {
                // Save user's column preferences for a facet
                String body = getRequestBody(request);
                JsonObject json = gson.fromJson(body, JsonObject.class);
                
                if (!json.has("facetId") || !json.has("columns")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"Missing facetId or columns\"}");
                    return;
                }
                
                String facetId = FacetNormalizationUtil.normalizeToCanonical(json.get("facetId").getAsString());
                if (facetId == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"Invalid facetId\"}");
                    return;
                }
                JsonArray columnsArray = json.getAsJsonArray("columns");
                List<String> columns = new ArrayList<>();
                for (int i = 0; i < columnsArray.size(); i++) {
                    columns.add(columnsArray.get(i).getAsString());
                }
                
                unisonFacetService.saveColumnPreferences(userId, facetId, columns);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Column preferences saved successfully");
                response.getWriter().write(gson.toJson(result));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\":\"Endpoint not found\"}");
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    private Integer getUserIdFromSession(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            if (userIdAttr instanceof Integer) {
                return (Integer) userIdAttr;
            } else if (userIdAttr instanceof Number) {
                return ((Number) userIdAttr).intValue();
            }
        }
        
        // Fallback: parse ACCESS_TOKEN cookie directly if filter didn't set attributes
        // This is needed because AuthFilter allows GET requests without token,
        // but we need authentication for /api/unison endpoints
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                Integer userId = JwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    // Set attributes for future use
                    request.setAttribute("userId", userId);
                    request.setAttribute("userEmail", claims.getStringClaim("email"));
                    request.setAttribute("userName", (claims.getStringClaim("firstName") + " " + claims.getStringClaim("lastName")).trim());
                    request.setAttribute("userRole", claims.getStringClaim("role"));
                    return userId;
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, continue to check session
        }
        
        // Last fallback: try to get from session
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            Object userId = session.getAttribute("userId");
            if (userId != null) {
                if (userId instanceof Integer) {
                    return (Integer) userId;
                } else if (userId instanceof Number) {
                    return ((Number) userId).intValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Helper method to get cookie value by name
     */
    private String getCookie(HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private boolean isAdmin(int userId) {
        // TODO: Implement admin check - this should check user's role
        // For now, return false - implement based on your role system
        // You can check against a role table or system_role field in people table
        return false;
    }

    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}

