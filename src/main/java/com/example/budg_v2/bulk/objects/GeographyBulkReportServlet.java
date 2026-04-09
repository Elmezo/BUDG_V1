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

@WebServlet("/api/bulk/geography/report/*")
public class GeographyBulkReportServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(GeographyBulkReportServlet.class);
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

        String jobIdStr = pathInfo.substring(1);
        int jobId;
        try {
            jobId = Integer.parseInt(jobIdStr);
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid job ID format", 400);
            return;
        }

        logger.info("Fetching Geography report for job ID: {}", jobId);
        try {
            Job job = jobDAO.getJobById(jobId);
            if (job == null) {
                sendErrorResponse(response, "Job not found for ID: " + jobId, 404);
                return;
            }

            JobProgress progress = jobDAO.getJobProgress(jobId);
            JobResourceFile resourceFile = jobDAO.getJobResourceFileByJobId(jobId);
            List<JobReportItem> reportItems = jobDAO.getJobReportItems(jobId);

            JsonObject reportResponse = new JsonObject();
            reportResponse.add("job", gson.toJsonTree(job).getAsJsonObject());
            if (progress != null) {
                reportResponse.add("progress", gson.toJsonTree(progress).getAsJsonObject());
            }
            if (resourceFile != null) {
                JsonObject fileInfo = gson.toJsonTree(resourceFile).getAsJsonObject();
                fileInfo.addProperty("download_url", "/api/bulk/geography/download/" + jobId);
                reportResponse.add("file", fileInfo);
            }
            reportResponse.add("report_items", gson.toJsonTree(reportItems));

            long successCount = reportItems.stream().filter(i -> "success".equals(i.getStatus())).count();
            long errorCount = reportItems.stream().filter(i -> "error".equals(i.getStatus())).count();
            long warningCount = reportItems.stream().filter(i -> "warning".equals(i.getStatus())).count();
            JsonObject summary = new JsonObject();
            summary.addProperty("total_items", reportItems.size());
            summary.addProperty("success_count", successCount);
            summary.addProperty("error_count", errorCount);
            summary.addProperty("warning_count", warningCount);
            reportResponse.add("summary", summary);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(reportResponse));

        } catch (Exception e) {
            logger.error("Error retrieving job report", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
}

