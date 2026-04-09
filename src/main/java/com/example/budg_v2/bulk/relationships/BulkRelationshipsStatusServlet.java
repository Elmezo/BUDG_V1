package com.example.budg_v2.bulk.relationships;

import com.example.budg_v2.bulk.common.BulkPathUtil;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet("/api/bulk/relationships/status/*")
public class BulkRelationshipsStatusServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkRelationshipsStatusServlet.class);
    private static final Gson gson = new Gson();
    private final JobDAO jobDAO = new JobDAO();

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
            JsonObject status = getJobStatus(jobId);
            
            if (status == null) {
                sendErrorResponse(response, "Job not found", 404);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(status));

        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid Job ID format", 400);
        } catch (SQLException e) {
            logger.error("Database error while fetching status", e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Unexpected error while fetching status", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    private JsonObject getJobStatus(int jobId) throws SQLException {
        // Get job details
        Job job = jobDAO.getJobById(jobId);
        if (job == null) {
            return null;
        }

        // Resolve upload_option as early as possible (before BULK_COUNTS or report-item counts) so DELETE count shows in Deleted square
        String uploadOption = null;
        JsonObject relationshipMetadata = getRelationshipJobMetadata(jobId);
        if (relationshipMetadata != null && relationshipMetadata.has("upload_option")) {
            uploadOption = relationshipMetadata.get("upload_option").getAsString();
        }
        if (uploadOption == null && job.getType() != null && job.getType().startsWith("RELATIONSHIP_BULK_")) {
            String suffix = job.getType().substring("RELATIONSHIP_BULK_".length()).trim().toUpperCase();
            if ("DELETE".equals(suffix) || "INSERT".equals(suffix) || "UPDATE".equals(suffix)) {
                uploadOption = suffix;
            }
        }
        if (uploadOption == null && job.getType() != null) {
            String jobTypeUpper = job.getType().toUpperCase();
            if (jobTypeUpper.contains("DELETE")) {
                uploadOption = "DELETE";
            } else if (jobTypeUpper.contains("UPDATE")) {
                uploadOption = "UPDATE";
            } else if (jobTypeUpper.contains("INSERT")) {
                uploadOption = "INSERT";
            }
        }

        // Get job progress
        JobProgress progress = jobDAO.getJobProgress(jobId);

        // Get report items to count statistics
        List<JobReportItem> reportItems = jobDAO.getJobReportItems(jobId);

        // Count statistics: prefer numeric counts stored in Job_Progress message (matches direct upload response)
        int inserted = 0;
        int updated = 0;
        int deleted = 0;
        int failed = 0;
        int[] parsedCounts = null;
        if (progress != null && progress.getMessage() != null) {
            parsedCounts = parseBulkCountsFromMessage(progress.getMessage());
            if (parsedCounts != null) {
                inserted = parsedCounts[0];
                failed = parsedCounts[1];
                updated = parsedCounts[2];
                deleted = parsedCounts[3];
            }
        }
        if (parsedCounts == null) {
            for (JobReportItem countItem : reportItems) {
                if ("error".equalsIgnoreCase(countItem.getStatus())) {
                    failed++;
                } else if ("success".equalsIgnoreCase(countItem.getStatus())) {
                    if ("DELETE".equals(uploadOption)) {
                        deleted++;
                    } else {
                        inserted++;
                    }
                }
            }
            // When metadata is missing, infer DELETE from success messages so count appears in Deleted square
            if (uploadOption == null && inferDeleteFromReportItems(reportItems)) {
                uploadOption = "DELETE";
                deleted = inserted;
                inserted = 0;
            }
        }
        // When metadata is missing but we have BULK_COUNTS, infer DELETE so the API returns upload_option and GUI shows count in Deleted square
        if (uploadOption == null && parsedCounts != null && inserted == 0 && deleted > 0) {
            uploadOption = "DELETE";
        }
        // Final fallback: if counts show deleted > 0 and inserted == 0, treat as DELETE so GUI shows count in Deleted square
        if (uploadOption == null && deleted > 0 && inserted == 0) {
            uploadOption = "DELETE";
        }

        // Fallback: never show 0 failed when BULK_COUNTS had failed=0 or is missing but there are report items with status "error" or "failed"
        int errorReportItemCount = 0;
        for (JobReportItem rItem : reportItems) {
            String s = rItem.getStatus();
            if (s != null && ("error".equalsIgnoreCase(s) || "failed".equalsIgnoreCase(s))) {
                errorReportItemCount++;
            }
        }
        failed = Math.max(failed, errorReportItemCount);

        // DELETE jobs: always show success count in Deleted square, never in Inserted (fix GUI showing deleted count in Inserted square)
        if ("DELETE".equals(uploadOption)) {
            if (deleted == 0 && inserted > 0) {
                deleted = inserted;
            }
            inserted = 0;
        }

        // Calculate progress percentage
        int progressPercentage = calculateProgress(job.getStatus(), job.getItemsCount(), reportItems.size());

        // Build response
        JsonObject status = new JsonObject();
        status.addProperty("job_id", jobId);
        status.addProperty("status", job.getStatus());
        status.addProperty("is_completed", job.getCompletedDate() != null);
        status.addProperty("items_count", job.getItemsCount() != null ? job.getItemsCount() : 0);
        status.addProperty("progress_percentage", progressPercentage);
        
        if (progress != null) {
            String progressStatus = progress.getStatus();
            if (progressStatus != null) {
                status.addProperty("stage", progressStatus);
            } else {
                status.add("stage", null);
            }
            String progressMsg = progress.getMessage();
            if (progressMsg != null) {
                // Strip BULK_COUNTS line for display so UI sees human-readable message only
                status.addProperty("progress_message", stripBulkCountsFromMessage(progressMsg));
            } else {
                status.addProperty("progress_message", "");
            }
        } else {
            status.add("stage", null);
            status.addProperty("progress_message", getDefaultMessage(job.getStatus()));
        }
        status.add("progress_updated_at", null); // job_progress table doesn't have updated_at column

        // Defensive: immediately before response, for DELETE ensure count is never shown in Inserted square
        if ("DELETE".equals(uploadOption)) {
            if (deleted == 0 && inserted > 0) {
                deleted = inserted;
            }
            inserted = 0;
        }
        status.addProperty("inserted", inserted);
        status.addProperty("updated", updated);
        status.addProperty("deleted", deleted);
        status.addProperty("failed", failed);
        // Include upload_option (from metadata or inferred from success messages) so the GUI shows deleted count in the Deleted square, not Inserted
        if (uploadOption != null) {
            status.addProperty("upload_option", uploadOption);
        }
        // Report available when there are failures, report items, or job is in a terminal state (so client can show Download Report button)
        boolean reportAvailable = failed > 0 || !reportItems.isEmpty() || isJobTerminal(job.getStatus());
        status.addProperty("report_available", reportAvailable);
        status.addProperty("reference_name", job.getReferenceName());

        return status;
    }

    /** Infer DELETE from success report messages when metadata is missing so GUI shows count in Deleted square. */
    private boolean inferDeleteFromReportItems(List<JobReportItem> reportItems) {
        if (reportItems == null) return false;
        for (JobReportItem item : reportItems) {
            if (item != null && "success".equalsIgnoreCase(item.getStatus()) && item.getMessages() != null) {
                for (var msg : item.getMessages()) {
                    if (msg != null && msg.getMessage() != null && msg.getMessage().toLowerCase().contains("deleted")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Load relationship job metadata from the relationships bulk directory by job_id.
     * Used to get upload_option so the GUI shows deleted count in the Deleted square and status API fallback counts success as deleted for DELETE jobs.
     */
    private JsonObject getRelationshipJobMetadata(int jobId) {
        try {
            String basePath = BulkPathUtil.getBulkPathForEntity("relationships");
            File dir = new File(basePath);
            if (!dir.exists() || !dir.isDirectory()) {
                return null;
            }
            File[] files = dir.listFiles((d, name) -> name != null && name.endsWith(".json"));
            if (files == null) {
                return null;
            }
            for (File file : files) {
                try {
                    String jsonContent = Files.readString(file.toPath());
                    JsonObject metadata = gson.fromJson(jsonContent, JsonObject.class);
                    if (metadata.has("job_id") && jobId == metadata.get("job_id").getAsInt()) {
                        return metadata;
                    }
                } catch (Exception e) {
                    // Skip files that can't be parsed
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read relationship metadata for job {}: {}", jobId, e.getMessage());
        }
        return null;
    }

    /**
     * Parse BULK_COUNTS:inserted=X,failed=Y,updated=Z,deleted=W from progress message.
     * @return int[4] = {inserted, failed, updated, deleted} or null if not found
     */
    private static final Pattern BULK_COUNTS_PATTERN = Pattern.compile(
        "BULK_COUNTS:inserted=(\\d+),failed=(\\d+),updated=(\\d+),deleted=(\\d+)");

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

    /** Remove BULK_COUNTS line from message for display. */
    private String stripBulkCountsFromMessage(String message) {
        if (message == null) return "";
        int idx = message.indexOf("\nBULK_COUNTS:");
        if (idx >= 0) {
            return message.substring(0, idx).trim();
        }
        return message;
    }

    private int calculateProgress(String jobStatus, Integer itemsCount, int reportItemsCount) {
        if (itemsCount == null || itemsCount == 0) {
            return 0;
        }
        
        if ("Completed".equals(jobStatus) || "Partially Completed".equals(jobStatus)) {
            return 100;
        } else if ("Failed".equals(jobStatus)) {
            return 100;
        } else {
            // Calculate based on report items processed
            return Math.min(100, (reportItemsCount * 100) / itemsCount);
        }
    }

    /** True when job is completed, partially completed, or failed (report download should be offered). */
    private boolean isJobTerminal(String status) {
        if (status == null) return false;
        return "Completed".equals(status) || "Partially Completed".equals(status) || "Failed".equals(status);
    }

    private String getDefaultMessage(String status) {
        if (status == null) {
            return "Processing...";
        }
        switch (status) {
            case "Pending":
                return "Job is pending";
            case "Processing":
                return "Processing...";
            case "Completed":
                return "Upload completed successfully";
            case "Partially Completed":
                return "Upload completed with some errors";
            case "Failed":
                return "Upload failed";
            default:
                return "Processing...";
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

