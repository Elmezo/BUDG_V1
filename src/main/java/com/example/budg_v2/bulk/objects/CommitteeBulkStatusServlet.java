package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.model.Job;
import com.example.budg_v2.model.JobProgress;
import com.example.budg_v2.model.JobReportItem;
import com.example.budg_v2.model.JobReportItemMessage;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet to provide job status for Committee bulk upload polling.
 * Returns current status, progress, and statistics (inserted, updated, deleted, failed).
 * Parses BULK_COUNTS from progress message or "Completed: X inserted, Y updated, Z deleted, W failed";
 * fallback: count from report items. Ensures failed is never 0 when there are error report items.
 */
@WebServlet("/api/bulk/committee/status/*")
public class CommitteeBulkStatusServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(CommitteeBulkStatusServlet.class);
    private static final Gson gson = new Gson();
    private final JobDAO jobDAO = new JobDAO();

    /** BULK_COUNTS:inserted=X,failed=Y,updated=Z,deleted=W */
    private static final Pattern BULK_COUNTS_PATTERN = Pattern.compile(
            "BULK_COUNTS:inserted=(\\d+),failed=(\\d+),updated=(\\d+),deleted=(\\d+)");

    /** Completed: X inserted, Y updated, Z deleted, W failed (or Partially Completed) */
    private static final Pattern COMPLETED_MESSAGE_PATTERN = Pattern.compile(
            "(?:Completed|Partially Completed):\\s*(\\d+)\\s+inserted,\\s*(\\d+)\\s+updated,\\s*(\\d+)\\s+deleted,\\s*(\\d+)\\s+failed");

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(request, response);

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            sendErrorResponse(response, "Job ID is required", 400);
            return;
        }

        try {
            int jobId = Integer.parseInt(pathInfo.substring(1));
            JsonObject statusObj = getJobStatus(jobId);

            if (statusObj == null) {
                sendErrorResponse(response, "Job not found", 404);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(statusObj));
            logger.debug("Returned status for committee bulk job {}", jobId);

        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid job ID format", 400);
        } catch (SQLException e) {
            logger.error("Database error fetching job status", e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Error fetching job status", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    private JsonObject getJobStatus(int jobId) throws SQLException {
        Job job = jobDAO.getJobById(jobId);
        if (job == null) {
            return null;
        }

        JobProgress progress = jobDAO.getJobProgress(jobId);
        List<JobReportItem> reportItems = jobDAO.getJobReportItems(jobId);

        int inserted = 0;
        int updated = 0;
        int deleted = 0;
        int failed = 0;
        String progressMessage = progress != null ? progress.getMessage() : null;

        // Prefer BULK_COUNTS from message
        int[] counts = parseBulkCountsFromMessage(progressMessage);
        if (counts != null) {
            inserted = counts[0];
            failed = counts[1];
            updated = counts[2];
            deleted = counts[3];
        } else {
            // Else parse "Completed: X inserted, Y updated, Z deleted, W failed"
            int[] completedCounts = parseCompletedMessage(progressMessage);
            if (completedCounts != null) {
                inserted = completedCounts[0];
                updated = completedCounts[1];
                deleted = completedCounts[2];
                failed = completedCounts[3];
            } else {
                // Fallback: count from report items (success -> inserted for simplicity; error -> failed)
                for (JobReportItem item : reportItems) {
                    String s = item.getStatus();
                    if (s != null && ("error".equalsIgnoreCase(s) || "failed".equalsIgnoreCase(s))) {
                        failed++;
                    } else if (s != null && "success".equalsIgnoreCase(s)) {
                        inserted++;
                    }
                }
            }
        }

        // Never show 0 failed when there are error report items
        int errorReportItemCount = 0;
        for (JobReportItem item : reportItems) {
            String s = item.getStatus();
            if (s != null && ("error".equalsIgnoreCase(s) || "failed".equalsIgnoreCase(s))) {
                errorReportItemCount++;
            }
        }
        failed = Math.max(failed, errorReportItemCount);

        int progressPercentage = calculateProgress(job.getStatus(), job.getItemsCount(), reportItems.size());
        boolean reportAvailable = failed > 0 || !reportItems.isEmpty()
                || "Completed".equals(job.getStatus()) || "Partially Completed".equals(job.getStatus()) || "Failed".equals(job.getStatus());

        JsonObject result = new JsonObject();
        result.addProperty("job_id", jobId);
        result.addProperty("status", job.getStatus());
        result.addProperty("progress", progressPercentage);
        result.addProperty("message", getDisplayMessage(progressMessage));
        result.addProperty("inserted", inserted);
        result.addProperty("updated", updated);
        result.addProperty("deleted", deleted);
        result.addProperty("failed", failed);
        result.addProperty("report_available", reportAvailable);
        result.addProperty("reference_name", job.getReferenceName() != null ? job.getReferenceName() : "");
        result.add("report_items", buildReportItemsJson(reportItems));

        return result;
    }

    private int[] parseBulkCountsFromMessage(String message) {
        if (message == null) return null;
        Matcher m = BULK_COUNTS_PATTERN.matcher(message);
        if (!m.find()) return null;
        try {
            return new int[] {
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4))
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int[] parseCompletedMessage(String message) {
        if (message == null) return null;
        Matcher m = COMPLETED_MESSAGE_PATTERN.matcher(message);
        if (!m.find()) return null;
        try {
            return new int[] {
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4))
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getDisplayMessage(String progressMessage) {
        if (progressMessage == null) return "";
        int idx = progressMessage.indexOf("\nBULK_COUNTS:");
        if (idx >= 0) {
            return progressMessage.substring(0, idx).trim();
        }
        return progressMessage;
    }

    private JsonArray buildReportItemsJson(List<JobReportItem> reportItems) {
        JsonArray arr = new JsonArray();
        if (reportItems == null) return arr;
        for (JobReportItem item : reportItems) {
            JsonObject o = new JsonObject();
            o.addProperty("status", item.getStatus());
            o.addProperty("position", item.getPosition() != null ? item.getPosition() : 0);
            o.addProperty("row_number", item.getPosition() != null ? item.getPosition() : 0);
            o.addProperty("entity_name", item.getFieldName());
            if (item.getMessages() != null && !item.getMessages().isEmpty()) {
                JsonArray msgArr = new JsonArray();
                for (JobReportItemMessage msg : item.getMessages()) {
                    JsonObject mo = new JsonObject();
                    mo.addProperty("message", msg.getMessage());
                    mo.addProperty("severity", msg.getType());
                    mo.addProperty("message_code", msg.getErrorCode());
                    msgArr.add(mo);
                }
                o.add("messages", msgArr);
            }
            arr.add(o);
        }
        return arr;
    }

    private int calculateProgress(String status, Integer totalItems, int processedItems) {
        if (status == null) return 0;
        switch (status) {
            case "Pending":
                return 0;
            case "Processing":
                if (totalItems != null && totalItems > 0) {
                    return Math.min(95, (processedItems * 90 / totalItems) + 5);
                }
                return 50;
            case "Completed":
            case "Partially Completed":
            case "Failed":
                return 100;
            default:
                return 0;
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
}
