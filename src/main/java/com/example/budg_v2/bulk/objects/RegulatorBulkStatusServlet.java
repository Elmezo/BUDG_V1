package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.model.Job;
import com.example.budg_v2.model.JobProgress;
import com.example.budg_v2.model.JobReportItem;
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
import java.sql.SQLException;
import java.util.List;

/**
 * Servlet to provide job status for bulk upload polling
 * Returns current status, progress, and statistics
 */
@WebServlet("/api/bulk/regulator/status/*")
public class RegulatorBulkStatusServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(RegulatorBulkStatusServlet.class);
    private static final Gson gson = new Gson();
    private final JobDAO jobDAO = new JobDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            // Extract job ID from path: /api/bulk/regulator/status/{jobId}
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendErrorResponse(response, "Job ID is required", 400);
                return;
            }

            String jobIdStr = pathInfo.substring(1); // Remove leading '/'
            int jobId;
            try {
                jobId = Integer.parseInt(jobIdStr);
            } catch (NumberFormatException e) {
                sendErrorResponse(response, "Invalid job ID format", 400);
                return;
            }

            logger.info("Fetching status for job ID: {}", jobId);

            // Get job details
            Job job = jobDAO.getJobById(jobId);
            if (job == null) {
                sendErrorResponse(response, "Job not found", 404);
                return;
            }

            // Get job progress
            JobProgress progress = jobDAO.getJobProgress(jobId);

            // Get report items to count statistics
            List<JobReportItem> reportItems = jobDAO.getJobReportItems(jobId);

            // Count statistics from report items
            int inserted = 0;
            int updated = 0;
            int deleted = 0;
            int failed = 0;

            for (JobReportItem item : reportItems) {
                if ("error".equalsIgnoreCase(item.getStatus()) || "skipped".equalsIgnoreCase(item.getStatus())) {
                    failed++;
                } else if ("success".equalsIgnoreCase(item.getStatus())) {
                    // Determine operation type from field name or messages
                    // For now, we'll use simple heuristics
                    // In production, you might want to add operation type to report items
                    inserted++; // Simplified - all success counted as inserted
                }
            }

            // Calculate progress percentage
            int progressPercentage = calculateProgress(job.getStatus(), job.getItemsCount(), reportItems.size());

            // Build response
            JsonObject statusResponse = new JsonObject();
            statusResponse.addProperty("status", job.getStatus());
            statusResponse.addProperty("progress", progressPercentage);
            
            if (progress != null) {
                statusResponse.addProperty("message", progress.getMessage());
            } else {
                statusResponse.addProperty("message", getDefaultMessage(job.getStatus()));
            }

            statusResponse.addProperty("inserted", inserted);
            statusResponse.addProperty("updated", updated);
            statusResponse.addProperty("deleted", deleted);
            statusResponse.addProperty("failed", failed);
            statusResponse.addProperty("report_available", failed > 0 || !reportItems.isEmpty());
            statusResponse.addProperty("job_id", jobId);
            statusResponse.addProperty("reference_name", job.getReferenceName());

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(statusResponse));

            logger.info("Successfully returned status for job {}: {}", jobId, job.getStatus());

        } catch (SQLException e) {
            logger.error("Database error fetching job status", e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Error fetching job status", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    /**
     * Calculate progress percentage based on job status and processed items
     */
    private int calculateProgress(String status, Integer totalItems, int processedItems) {
        if (status == null) {
            return 0;
        }

        switch (status) {
            case "Pending":
                return 0;
            case "Processing":
                // Calculate based on processed vs total items
                if (totalItems != null && totalItems > 0) {
                    return Math.min(95, (processedItems * 90 / totalItems) + 5);
                }
                return 50; // Default if total unknown
            case "Completed":
                return 100;
            case "Failed":
                return 100;
            default:
                return 0;
        }
    }

    /**
     * Get default status message based on job status
     */
    private String getDefaultMessage(String status) {
        if (status == null) {
            return "Unknown status";
        }

        switch (status) {
            case "Pending":
                return "Job is queued and waiting to be processed";
            case "Processing":
                return "Processing your bulk upload...";
            case "Completed":
                return "Bulk upload completed successfully";
            case "Failed":
                return "Bulk upload failed";
            default:
                return "Status: " + status;
        }
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);

        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
}

