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

@WebServlet("/api/bulk/glossary/download/*")
public class GlossaryBulkDownloadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryBulkDownloadServlet.class);
    private static final Gson gson = new Gson();
    
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
            sendErrorResponse(response, "Missing job ID in path", 400);
            return;
        }

        String jobIdStr = pathInfo.substring(1);
        int jobId;
        
        try {
            jobId = Integer.parseInt(jobIdStr);
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid job ID format", 400);
            return;
        }

        logger.info("Download request for job ID: {}", jobId);

        try {
            JobResourceFile resourceFile = jobDAO.getJobResourceFileByJobId(jobId);
            
            if (resourceFile == null) {
                sendErrorResponse(response, "Job resource file not found", 404);
                return;
            }

            String storagePath = resourceFile.getStoragePath();
            String originalFileName = resourceFile.getOriginalFileName();
            
            if (storagePath == null || storagePath.isEmpty()) {
                sendErrorResponse(response, "Storage path not found for this job", 404);
                return;
            }

            File file = new File(storagePath);
            if (!file.exists() || !file.isFile()) {
                logger.error("File not found at path: {}", storagePath);
                sendErrorResponse(response, "File not found on server", 404);
                return;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setContentLengthLong(file.length());
            response.setHeader("Content-Disposition", 
                "attachment; filename=\"" + (originalFileName != null ? originalFileName : "bulk_upload.xlsx") + "\"");

            try (FileInputStream fileInputStream = new FileInputStream(file);
                 OutputStream outputStream = response.getOutputStream()) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                outputStream.flush();
            }

            logger.info("File downloaded successfully for job ID: {}", jobId);

        } catch (Exception e) {
            logger.error("Error downloading file", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
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

