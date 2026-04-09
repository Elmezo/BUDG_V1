package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.model.Job;
import com.example.budg_v2.model.JobProgress;
import com.example.budg_v2.model.JobReportItem;
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

import java.io.IOException;
import java.util.List;

@WebServlet("/api/bulk/policy/report/*")
public class PolicyBulkReportServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(PolicyBulkReportServlet.class);
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

        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            sendErrorResponse(response, "Missing job ID in path", 400);
            return;
        }

        // Extract job ID from path
        String jobIdStr = pathInfo.substring(1); // Remove leading '/'
        int jobId;
        
        try {
            jobId = Integer.parseInt(jobIdStr);
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid job ID format", 400);
            return;
        }

        logger.info("Fetching report for job ID: {}", jobId);
        logger.debug("Request path: {}", request.getRequestURI());
        logger.debug("Path info: {}", pathInfo);

        try {
            // Get job details
            Job job = jobDAO.getJobById(jobId);
            if (job == null) {
                logger.warn("Job not found for ID: {}", jobId);
                sendErrorResponse(response, "Job not found for ID: " + jobId, 404);
                return;
            }
            
            logger.info("Job found: ID={}, Type={}, Status={}, Reference={}", 
                       job.getId(), job.getType(), job.getStatus(), job.getReferenceName());

            // Get job progress
            JobProgress progress = jobDAO.getJobProgress(jobId);

            // Get job resource file
            JobResourceFile resourceFile = jobDAO.getJobResourceFileByJobId(jobId);

            // Get job report items with messages
            List<JobReportItem> reportItems = jobDAO.getJobReportItems(jobId);
            logger.info("Found {} report items for job {}", reportItems.size(), jobId);

            // Build response JSON
            JsonObject reportResponse = new JsonObject();
            
            // Job info
            JsonObject jobInfo = gson.toJsonTree(job).getAsJsonObject();
            reportResponse.add("job", jobInfo);

            // Progress info
            if (progress != null) {
                JsonObject progressInfo = gson.toJsonTree(progress).getAsJsonObject();
                reportResponse.add("progress", progressInfo);
                logger.debug("Progress info added: {}", progress.getMessage());
            } else {
                logger.debug("No progress info found for job {}", jobId);
            }

            // File info
            if (resourceFile != null) {
                JsonObject fileInfo = gson.toJsonTree(resourceFile).getAsJsonObject();
                fileInfo.addProperty("download_url", "/api/bulk/policy/download/" + jobId);
                reportResponse.add("file", fileInfo);
                logger.debug("File info added: {}", resourceFile.getFileName());
            } else {
                logger.debug("No resource file found for job {}", jobId);
            }

            // Report items (always include, even if empty)
            reportResponse.add("report_items", gson.toJsonTree(reportItems));

            // Summary counts
            long successCount = reportItems.stream()
                .filter(item -> "success".equals(item.getStatus()))
                .count();
            long errorCount = reportItems.stream()
                .filter(item -> "error".equals(item.getStatus()))
                .count();
            long warningCount = reportItems.stream()
                .filter(item -> "warning".equals(item.getStatus()))
                .count();

            JsonObject summary = new JsonObject();
            summary.addProperty("total_items", reportItems.size());
            summary.addProperty("success_count", successCount);
            summary.addProperty("error_count", errorCount);
            summary.addProperty("warning_count", warningCount);
            reportResponse.add("summary", summary);

            logger.info("Report retrieved for job {}: {} total items ({} success, {} errors, {} warnings)", 
                       jobId, reportItems.size(), successCount, errorCount, warningCount);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(reportResponse));

        } catch (Exception e) {
            logger.error("Error retrieving job report", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
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

