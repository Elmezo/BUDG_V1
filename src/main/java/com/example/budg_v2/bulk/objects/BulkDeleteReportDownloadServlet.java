package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.example.budg_v2.database.DatabaseConnection;

/**
 * Servlet for downloading bulk delete report Excel files
 * Endpoint: /api/bulk/{entity}/delete-report/{jobId}
 */
@WebServlet("/api/bulk/*/delete-report/*")
public class BulkDeleteReportDownloadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkDeleteReportDownloadServlet.class);
    private static final Gson gson = new Gson();
    
    @SuppressWarnings("unused")
    private final JobDAO jobDAO = new JobDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            sendErrorResponse(response, "Missing entity and job ID in path", 400);
            return;
        }

        // Path format: /{entity}/delete-report/{jobId}
        String[] pathParts = pathInfo.split("/");
        if (pathParts.length < 4) {
            sendErrorResponse(response, "Invalid path format. Expected: /{entity}/delete-report/{jobId}", 400);
            return;
        }

        String entity = pathParts[1];
        String jobIdStr = pathParts[3];
        int jobId;
        
        try {
            jobId = Integer.parseInt(jobIdStr);
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid job ID format", 400);
            return;
        }

        logger.info("Delete report download request for entity: {}, job ID: {}", entity, jobId);

        try {
            // Find delete report file for this job
            // Report files have names like: {entity}_delete_report_{jobId}_{timestamp}.xlsx
            JobResourceFile reportFile = findDeleteReportFile(jobId, entity);
            
            if (reportFile == null) {
                sendErrorResponse(response, "Delete report not found for this job", 404);
                return;
            }

            String storagePath = reportFile.getStoragePath();
            String originalFileName = reportFile.getOriginalFileName();
            
            if (storagePath == null || storagePath.isEmpty()) {
                sendErrorResponse(response, "Storage path not found for this report", 404);
                return;
            }

            File file = new File(storagePath);
            if (!file.exists() || !file.isFile()) {
                logger.error("Report file not found at path: {}", storagePath);
                sendErrorResponse(response, "Report file not found on server", 404);
                return;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setContentLengthLong(file.length());
            response.setHeader("Content-Disposition", 
                "attachment; filename=\"" + (originalFileName != null ? originalFileName : "delete_report.xlsx") + "\"");

            try (FileInputStream fileInputStream = new FileInputStream(file);
                 OutputStream outputStream = response.getOutputStream()) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                outputStream.flush();
            }

            logger.info("Delete report downloaded successfully for job ID: {}", jobId);

        } catch (Exception e) {
            logger.error("Error downloading delete report", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Find delete report file for a job
     * Looks for files with pattern: {entity}_delete_report_{jobId}_*.xlsx
     */
    private JobResourceFile findDeleteReportFile(int jobId, String entity) throws SQLException {
        String entityLower = entity.toLowerCase();
        String pattern = entityLower + "_delete_report_" + jobId + "_";
        
        // Get all resource files for this job
        String sql = "SELECT * FROM Job_Resource_FileName WHERE JobID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, jobId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String fileName = rs.getString("FileName");
                    if (fileName != null && fileName.contains("_delete_report_") && 
                        fileName.startsWith(pattern)) {
                        // Found delete report file
                        return mapResultSetToJobResourceFile(rs);
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Map ResultSet to JobResourceFile
     */
    private JobResourceFile mapResultSetToJobResourceFile(ResultSet rs) throws SQLException {
        JobResourceFile file = new JobResourceFile();
        file.setId(rs.getInt("ID"));
        file.setJobId(rs.getInt("JobID"));
        file.setFileName(rs.getString("FileName"));
        file.setOriginalFileName(rs.getString("Original_FileName"));
        file.setStoragePath(rs.getString("Storage_Path"));
        file.setStoreFile(rs.getBoolean("StoreFile"));
        file.setRetentionDays(rs.getInt("Retention_Days"));
        if (rs.getTimestamp("Delete_At") != null) {
            file.setDeleteAt(rs.getTimestamp("Delete_At"));
        }
        return file;
    }

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

