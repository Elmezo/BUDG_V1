package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.model.Job;
import com.example.budg_v2.model.JobResourceFile;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Generic servlet for downloading original uploaded files for any entity
 * Endpoint: /api/bulk/{entity}/download/{jobId}
 */
@WebServlet("/api/bulk/*")
public class BulkDownloadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkDownloadServlet.class);
    private static final Gson gson = new Gson();
    
    private final JobDAO jobDAO = new JobDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        CorsUtil.setCorsHeaders(request, response);
        
        // This servlet only handles GET requests for downloads
        // POST requests should be handled by specific upload servlets
        // Only handle POST if this is actually a download path (which shouldn't happen)
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestURI;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        
        // Only handle POST if this is a download path (which is invalid)
        if (path.contains("/download/")) {
            sendErrorResponse(response, "POST method is not allowed for download operations. Use GET instead.", 405);
            return;
        }
        
        // For non-download paths, don't handle POST - let other servlets handle it
        // Return 404 instead of 405 to allow Tomcat to try other servlets
        // This servlet should only match download paths
        sendErrorResponse(response, "Not found", 404);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        CorsUtil.setCorsHeaders(response);

        try {
            // Parse path: /api/bulk/{entity}/download/{jobId}
            // Use getRequestURI() to get the full path
            String requestURI = request.getRequestURI();
            
            // Remove context path if present
            String contextPath = request.getContextPath();
            String path = requestURI;
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
            
            // Expected format: /api/bulk/{entity}/download/{jobId}
            // Only handle download requests, ignore other bulk requests (let other servlets handle them)
            if (!path.startsWith("/api/bulk/") || !path.contains("/download/")) {
                sendErrorResponse(response, "Not found", 404);
                return;
            }
            
            // Remove /api/bulk/ prefix
            String remainingPath = path.substring("/api/bulk/".length());
            String[] pathParts = remainingPath.split("/");
            
            if (pathParts.length < 3 || !pathParts[1].equals("download")) {
                sendErrorResponse(response, "Invalid path format. Expected: /api/bulk/{entity}/download/{jobId}", 400);
                return;
            }
            
            String entity = pathParts[0]; // e.g., "regulator", "committee", etc.
            String jobIdStr = pathParts[2]; // e.g., "123"
            
            int jobId;
            try {
                jobId = Integer.parseInt(jobIdStr);
            } catch (NumberFormatException e) {
                sendErrorResponse(response, "Invalid job ID format", 400);
                return;
            }

            logger.info("Download request - Entity: {}, Job ID: {}", entity, jobId);

            // First check if the job exists
            try {
                Job job = jobDAO.getJobById(jobId);
                if (job == null) {
                    logger.warn("Job not found - Entity: {}, Job ID: {}", entity, jobId);
                    sendErrorResponse(response, "Job not found", 404);
                    return;
                }
                logger.debug("Job found - Type: {}, Status: {}, Reference: {}", 
                    job.getType(), job.getStatus(), job.getReferenceName());
            } catch (Exception e) {
                logger.error("Error checking job existence for Job ID: {}", jobId, e);
                sendErrorResponse(response, "Error checking job: " + e.getMessage(), 500);
                return;
            }

            // Get job resource file
            JobResourceFile resourceFile;
            try {
                resourceFile = jobDAO.getJobResourceFileByJobId(jobId);
            } catch (Exception e) {
                logger.error("Error retrieving job resource file for Job ID: {}", jobId, e);
                sendErrorResponse(response, "Error retrieving job resource file: " + e.getMessage(), 500);
                return;
            }
            
            if (resourceFile == null) {
                logger.warn("Job resource file not found - Entity: {}, Job ID: {}. " +
                    "This may indicate the file was not saved during upload or was deleted.", entity, jobId);
                sendErrorResponse(response, 
                    "Job resource file not found. The file may not have been saved during upload or may have been deleted.", 404);
                return;
            }

            String storagePath = resourceFile.getStoragePath();
            String originalFileName = resourceFile.getOriginalFileName();
            
            if (storagePath == null || storagePath.isEmpty()) {
                logger.warn("Storage path missing - Entity: {}, Job ID: {}, ResourceFileID: {}",
                        entity, jobId, resourceFile.getId());
                sendErrorResponse(response, "Storage path not found for this job", 404);
                return;
            }

            // Check if file exists
            File file = new File(storagePath);
            if (!file.exists() || !file.isFile()) {
                String absolutePath = file.getAbsolutePath();
                logger.warn("Download file missing - Entity: {}, Job ID: {}, storagePath: {}, absolutePath: {}, exists: {}, isFile: {}",
                        entity, jobId, storagePath, absolutePath, file.exists(), file.isFile());
                sendErrorResponse(response, "File not found on server (path: " + absolutePath + ")", 404);
                return;
            }

            // Set response headers for file download
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setContentLengthLong(file.length());
            response.setHeader("Content-Disposition", 
                "attachment; filename=\"" + (originalFileName != null ? originalFileName : "bulk_upload.xlsx") + "\"");

            // Stream file to response
            try (FileInputStream fileInputStream = new FileInputStream(file);
                 OutputStream outputStream = response.getOutputStream()) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                outputStream.flush();
            }

            logger.info("File downloaded successfully - Entity: {}, Job ID: {}", entity, jobId);

        } catch (Exception e) {
            logger.error("Error downloading file", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        
        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
}

