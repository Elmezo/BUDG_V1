package com.example.budg_v2;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.service.LdapSyncService;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servlet for LDAP synchronization operations
 * 
 * Endpoints:
 * - POST /api/admin/ldap/sync/start - Start synchronization
 * - GET /api/admin/ldap/sync/status/{jobId} - Get sync status
 * - POST /api/admin/ldap/sync/cancel/{jobId} - Cancel sync (if running)
 * 
 * Security: SuperAdmin only
 */
@WebServlet("/api/admin/ldap/sync/*")
public class LdapSyncServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(LdapSyncServlet.class);
    private final Gson gson = new Gson();
    private final LdapSyncService ldapSyncService;
    private final JobDAO jobDAO;
    private final ExecutorService executorService;
    
    public LdapSyncServlet() {
        this.ldapSyncService = new LdapSyncService();
        this.jobDAO = new JobDAO();
        // Single thread executor to prevent concurrent syncs
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LdapSyncThread");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        
        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Check SuperAdmin access
        if (!isSuperAdmin(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: SuperAdmin access required");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid endpoint");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        // GET /api/admin/ldap/sync/status/{jobId}
        if (pathInfo.startsWith("/status/")) {
            String jobIdStr = pathInfo.substring("/status/".length());
            try {
                int jobId = Integer.parseInt(jobIdStr);
                getSyncStatus(jobId, response);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid job ID");
                response.getWriter().write(gson.toJson(error));
            }
        } 
        // GET /api/admin/ldap/sync/history/latest
        else if (pathInfo.equals("/history/latest")) {
            getLatestSyncHistory(response);
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid endpoint");
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        
        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Check SuperAdmin access
        if (!isSuperAdmin(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: SuperAdmin access required");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid endpoint");
            response.getWriter().write(gson.toJson(error));
            return;
        }
        
        // POST /api/admin/ldap/sync/start
        if (pathInfo.equals("/start")) {
            startSync(request, response);
        }
        // POST /api/admin/ldap/sync/cancel/{jobId}
        else if (pathInfo.startsWith("/cancel/")) {
            String jobIdStr = pathInfo.substring("/cancel/".length());
            try {
                int jobId = Integer.parseInt(jobIdStr);
                cancelSync(jobId, response);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid job ID");
                response.getWriter().write(gson.toJson(error));
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid endpoint");
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Start LDAP synchronization
     */
    private void startSync(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User not authenticated");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Create job record
            int jobId = jobDAO.createJob(
                "LDAP_SYNC",
                "LDAP Synchronization",
                null, // items count unknown at start
                "Running",
                userId
            );
            
            logger.info("Starting LDAP synchronization - Job ID: {}, User ID: {}", jobId, userId);
            
            // Start sync in background thread
            executorService.submit(() -> {
                try {
                    ldapSyncService.synchronize(userId, jobId);
                } catch (Exception e) {
                    logger.error("Error in LDAP sync thread", e);
                    try {
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                    } catch (SQLException ex) {
                        logger.error("Error updating job status to Failed", ex);
                    }
                }
            });
            
            // Return response immediately
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("jobId", jobId);
            successResponse.addProperty("message", "Synchronization started");
            response.getWriter().write(gson.toJson(successResponse));
            
        } catch (SQLException e) {
            logger.error("Error starting LDAP sync", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to start synchronization: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Get sync status
     */
    private void getSyncStatus(int jobId, HttpServletResponse response) throws IOException {
        try {
            var job = jobDAO.getJobById(jobId);
            if (job == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Job not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            JsonObject statusResponse = new JsonObject();
            statusResponse.addProperty("jobId", jobId);
            statusResponse.addProperty("status", job.getStatus());
            statusResponse.addProperty("type", job.getType());
            statusResponse.addProperty("referenceName", job.getReferenceName());
            statusResponse.addProperty("itemsCount", job.getItemsCount());
            statusResponse.addProperty("createdDate", job.getCreatedDate() != null ? 
                job.getCreatedDate().toString() : null);
            statusResponse.addProperty("completedDate", job.getCompletedDate() != null ? 
                job.getCompletedDate().toString() : null);
            
            // Get latest progress
            try {
                var progress = jobDAO.getJobProgress(jobId);
                if (progress != null) {
                    statusResponse.addProperty("progress", progress.getExpectedTicks());
                    statusResponse.addProperty("progressMessage", progress.getMessage());
                    statusResponse.addProperty("progressStatus", progress.getStatus());
                }
            } catch (Exception e) {
                logger.debug("Error getting job progress", e);
            }
            
            response.getWriter().write(gson.toJson(statusResponse));
            
        } catch (SQLException e) {
            logger.error("Error getting sync status", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to get sync status: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Cancel sync (if running)
     */
    private void cancelSync(int jobId, HttpServletResponse response) throws IOException {
        try {
            var job = jobDAO.getJobById(jobId);
            if (job == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Job not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            if (!"Running".equals(job.getStatus())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Job is not running");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Update job status to cancelled
            jobDAO.updateJobStatus(jobId, "Cancelled", true);
            
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Synchronization cancelled");
            response.getWriter().write(gson.toJson(successResponse));
            
        } catch (SQLException e) {
            logger.error("Error cancelling sync", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to cancel synchronization: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Get latest sync history
     */
    private void getLatestSyncHistory(HttpServletResponse response) throws IOException {
        try {
            String sql = "SELECT * FROM ldap_sync_history " +
                        "WHERE status = 'Completed' " +
                        "ORDER BY completed_at DESC " +
                        "LIMIT 1";
            
            try (var conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 var stmt = conn.prepareStatement(sql);
                 var rs = stmt.executeQuery()) {
                
                if (rs.next()) {
                    JsonObject history = new JsonObject();
                    history.addProperty("id", rs.getInt("id"));
                    history.addProperty("job_id", rs.getInt("job_id"));
                    history.addProperty("started_at", rs.getTimestamp("started_at").toString());
                    if (rs.getTimestamp("completed_at") != null) {
                        history.addProperty("completed_at", rs.getTimestamp("completed_at").toString());
                    }
                    history.addProperty("status", rs.getString("status"));
                    history.addProperty("users_fetched", rs.getInt("users_fetched"));
                    history.addProperty("users_added", rs.getInt("users_added"));
                    history.addProperty("users_updated", rs.getInt("users_updated"));
                    history.addProperty("users_skipped", rs.getInt("users_skipped"));
                    history.addProperty("users_disabled", rs.getInt("users_disabled"));
                    history.addProperty("org_units_created", rs.getInt("org_units_created"));
                    history.addProperty("org_units_updated", rs.getInt("org_units_updated"));
                    
                    response.getWriter().write(gson.toJson(history));
                } else {
                    // No completed sync found
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "No completed sync history found");
                    response.getWriter().write(gson.toJson(error));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting latest sync history", e);
            String sqlState = e.getSQLState();
            String msg = e.getMessage() != null ? e.getMessage() : "";
            // Missing table or similar: treat as no history so the admin panel still loads
            boolean missingTable = "42S02".equals(sqlState)
                || msg.contains("doesn't exist")
                || msg.contains("Unknown table");
            if (missingTable) {
                logger.warn("ldap_sync_history not available; returning 404 as no history. Run migration create_ldap_sync_history.sql if needed.");
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "No completed sync history found");
                response.getWriter().write(gson.toJson(error));
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Failed to get sync history: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
            }
        }
    }
    
    /**
     * Check if user is SuperAdmin
     */
    private boolean isSuperAdmin(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Object roleObj = request.getAttribute("userRole");
        if (roleObj != null && AppRoleNames.isSuperAdminName(roleObj.toString())) {
            return true;
        }
        
        // Fallback: parse ACCESS_TOKEN cookie directly
        try {
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            String token = null;
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie cookie : cookies) {
                    if ("ACCESS_TOKEN".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
            
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                String role = claims.getStringClaim("role");
                if (role != null && AppRoleNames.isSuperAdminName(role)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("Error parsing token for SuperAdmin check", e);
        }
        
        return false;
    }
}

