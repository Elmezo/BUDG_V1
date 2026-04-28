package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.example.unisonsearch.service.ConfigurationService;
import com.example.unisonsearch.util.Constants;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Servlet for managing Data Migration enablement configuration
 * Endpoints:
 * GET /api/admin/environment/data-migration - Get current status
 * POST /api/admin/environment/data-migration - Update status
 */
@WebServlet("/api/admin/environment/data-migration")
public class DataMigrationConfigServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DataMigrationConfigServlet.class);
    private final Gson gson = new Gson();
    private final ConfigurationService configurationService = new ConfigurationService();

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        //system.out.println("[DataMigrationConfigServlet] ====== doGet called ======");
        //system.out.println("[DataMigrationConfigServlet] Request URI: " + request.getRequestURI());
        
        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Check Admin or SuperAdmin access
        boolean isAdmin = isAdminOrSuperAdmin(request);
        //system.out.println("[DataMigrationConfigServlet] isAdminOrSuperAdmin returned: " + isAdmin);
        
        if (!isAdmin) {
            //system.out.println("[DataMigrationConfigServlet] ❌ Access denied - returning 403");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: Admin or SuperAdmin access required");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        //system.out.println("[DataMigrationConfigServlet] ✅ Access granted - proceeding");

        try {
            //system.out.println("[DataMigrationConfigServlet] Getting data migration status from ConfigurationService...");
            boolean enabled = configurationService.isDataMigrationEnabled();
            //system.out.println("[DataMigrationConfigServlet] Data migration enabled: " + enabled);
            
            JsonObject result = new JsonObject();
            result.addProperty("enabled", enabled);
            String jsonResponse = gson.toJson(result);
            //system.out.println("[DataMigrationConfigServlet] Response JSON: " + jsonResponse);
            //system.out.println("[DataMigrationConfigServlet] Setting response status to 200...");
            response.setStatus(HttpServletResponse.SC_OK);
            //system.out.println("[DataMigrationConfigServlet] Writing response...");
            response.getWriter().write(jsonResponse);
            response.getWriter().flush();
            //system.out.println("[DataMigrationConfigServlet] ✅ Response sent successfully with status 200");

        } catch (Exception e) {
            logger.error("Error getting data migration status", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to get data migration status: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        //system.out.println("[DataMigrationConfigServlet] ====== doPost called ======");
        //system.out.println("[DataMigrationConfigServlet] Request URI: " + request.getRequestURI());
        
        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Check Admin or SuperAdmin access
        boolean isAdmin = isAdminOrSuperAdmin(request);
        //system.out.println("[DataMigrationConfigServlet] isAdminOrSuperAdmin returned: " + isAdmin);
        
        if (!isAdmin) {
            //system.out.println("[DataMigrationConfigServlet] ❌ Access denied - returning 403");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: Admin or SuperAdmin access required");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        //system.out.println("[DataMigrationConfigServlet] ✅ Access granted - proceeding");

        try {
            // Read request body
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }

            JsonObject requestData = gson.fromJson(jsonBody.toString(), JsonObject.class);

            if (requestData == null || !requestData.has("enabled")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing 'enabled' field in request body");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            boolean newValue = requestData.get("enabled").getAsBoolean();
            String newValueStr = newValue ? "true" : "false";

            // Get current value for audit logging
            boolean oldValue = configurationService.isDataMigrationEnabled();
            String oldValueStr = oldValue ? "true" : "false";

            // Get user info for audit
            int userId = UserContextUtil.getCurrentUserId(request);
            String userName = "Unknown";
            try {
                Object userNameObj = request.getAttribute("userName");
                if (userNameObj != null) {
                    userName = userNameObj.toString();
                }
            } catch (Exception e) {
                logger.debug("Could not get user name for audit: " + e.getMessage());
            }

            // Update configuration in database
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Check if config exists
                String checkSql = "SELECT COUNT(*) FROM app_config WHERE config_key = ?";
                boolean configExists = false;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, Constants.CONFIG_DATA_MIGRATION_ENABLED);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            configExists = rs.getInt(1) > 0;
                        }
                    }
                }

                if (configExists) {
                    // Update existing config
                    String updateSql = "UPDATE app_config SET definition = ? WHERE config_key = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, newValueStr);
                        ps.setString(2, Constants.CONFIG_DATA_MIGRATION_ENABLED);
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected > 0) {
                            // Log audit
                            logger.info("Data Migration configuration changed by user {} (ID: {}): {} -> {}", 
                                    userName, userId, oldValueStr, newValueStr);
                            
                            JsonObject result = new JsonObject();
                            result.addProperty("success", true);
                            result.addProperty("enabled", newValue);
                            result.addProperty("message", "Data Migration configuration updated successfully");
                            response.getWriter().write(gson.toJson(result));
                        } else {
                            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            JsonObject error = new JsonObject();
                            error.addProperty("error", "Failed to update configuration");
                            response.getWriter().write(gson.toJson(error));
                        }
                    }
                } else {
                    // Insert new config
                    String insertSql = "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setString(1, Constants.CONFIG_DATA_MIGRATION_ENABLED);
                        ps.setString(2, newValueStr);
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected > 0) {
                            // Log audit
                            logger.info("Data Migration configuration created by user {} (ID: {}): {}", 
                                    userName, userId, newValueStr);
                            
                            JsonObject result = new JsonObject();
                            result.addProperty("success", true);
                            result.addProperty("enabled", newValue);
                            result.addProperty("message", "Data Migration configuration created successfully");
                            response.getWriter().write(gson.toJson(result));
                        } else {
                            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            JsonObject error = new JsonObject();
                            error.addProperty("error", "Failed to create configuration");
                            response.getWriter().write(gson.toJson(error));
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error updating data migration configuration", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Database error: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
            }

        } catch (Exception e) {
            logger.error("Error updating data migration status", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Check if current user is Admin or SuperAdmin
     * Uses UserContextUtil first, then falls back to JWT token parsing
     */
    private boolean isAdminOrSuperAdmin(HttpServletRequest request) {
        //system.out.println("[DataMigrationConfigServlet] ====== isAdminOrSuperAdmin called ======");
        logger.debug("[DataMigrationConfigServlet] Checking admin access...");
        
        // First try UserContextUtil
        //system.out.println("[DataMigrationConfigServlet] Trying UserContextUtil.isCurrentUserAdmin()...");
        boolean isAdminViaUtil = UserContextUtil.isCurrentUserAdmin(request);
        //system.out.println("[DataMigrationConfigServlet] UserContextUtil.isCurrentUserAdmin() returned: " + isAdminViaUtil);
        logger.debug("[DataMigrationConfigServlet] UserContextUtil.isCurrentUserAdmin() returned: {}", isAdminViaUtil);
        
        if (isAdminViaUtil) {
            //system.out.println("[DataMigrationConfigServlet] ✅ User is admin via UserContextUtil");
            logger.debug("[DataMigrationConfigServlet] User is admin via UserContextUtil");
            return true;
        }
        
        // Fallback: parse JWT token directly (like MeServlet does)
        //system.out.println("[DataMigrationConfigServlet] Falling back to JWT token parsing...");
        logger.debug("[DataMigrationConfigServlet] Falling back to JWT token parsing");
        try {
            Cookie[] cookies = request.getCookies();
            logger.debug("[DataMigrationConfigServlet] Cookies present: {}", cookies != null);
            
            String token = getCookie(request, "ACCESS_TOKEN");
            logger.debug("[DataMigrationConfigServlet] Access token present: {}", token != null && !token.isEmpty());
            
            if (token != null && !token.isEmpty()) {
                //system.out.println("[DataMigrationConfigServlet] Parsing JWT token...");
                logger.debug("[DataMigrationConfigServlet] Parsing JWT token");
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                String role = claims.getStringClaim("role");
                //system.out.println("[DataMigrationConfigServlet] Role from JWT: '" + role + "'");
                logger.debug("[DataMigrationConfigServlet] Role claim present: {}", role != null && !role.trim().isEmpty());
                
                if (role != null && !role.trim().isEmpty()) {
                    //system.out.println("[DataMigrationConfigServlet] Normalized role: '" + normalizedRole + "'");
                    logger.debug("[DataMigrationConfigServlet] Normalized role key present");
                    
                    boolean isAdmin = AppRoleNames.isAdminOrSuperAdminName(role);
                    
                    //system.out.println("[DataMigrationConfigServlet] Is admin based on JWT: " + isAdmin);
                    logger.debug("[DataMigrationConfigServlet] Is admin based on JWT: {}", isAdmin);
                    
                    if (isAdmin) {
                        //system.out.println("[DataMigrationConfigServlet] ✅ User is admin/super admin based on JWT token");
                        logger.debug("[DataMigrationConfigServlet] User is admin/super admin based on JWT token");
                        return true;
                    } else {
                        //system.out.println("[DataMigrationConfigServlet] ❌ Role '" + normalizedRole + "' is not admin");
                        logger.warn("[DataMigrationConfigServlet] JWT role is not admin");
                    }
                } else {
                    //system.out.println("[DataMigrationConfigServlet] ❌ Role is null or empty in JWT");
                    logger.warn("[DataMigrationConfigServlet] Role is null or empty in JWT");
                }
            } else {
                //system.out.println("[DataMigrationConfigServlet] ❌ No ACCESS_TOKEN cookie found");
                logger.warn("[DataMigrationConfigServlet] No access token cookie found");
            }
        } catch (Exception e) {
            //system.out.println("[DataMigrationConfigServlet] ❌ Exception during JWT parsing: " + e.getMessage());
            logger.error("[DataMigrationConfigServlet] Exception during JWT parsing", e);
        }
        
        //system.out.println("[DataMigrationConfigServlet] ❌ User is NOT admin - returning false");
        logger.debug("[DataMigrationConfigServlet] User is NOT admin");
        return false;
    }
    
    /**
     * Get cookie value from request
     */
    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}

