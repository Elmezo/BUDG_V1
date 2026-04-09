package com.example.budg_v2.snapshot;

import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.dao.JobDAO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

/**
 * Persists a completed table-snapshot import as a {@code job} row plus a JSON report on disk
 * (linked via {@code job_resource_filename}) so users can open details from My Jobs.
 */
public final class TableSnapshotImportJobRecorder {

    private static final Logger logger = LoggerFactory.getLogger(TableSnapshotImportJobRecorder.class);
    private static final Gson COMPACT_GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class JobRecord {
        public final int jobId;
        public final String referenceName;
        public final String summaryMessage;

        public JobRecord(int jobId, String referenceName, String summaryMessage) {
            this.jobId = jobId;
            this.referenceName = referenceName;
            this.summaryMessage = summaryMessage;
        }
    }

    private TableSnapshotImportJobRecorder() {
    }

    /**
     * @return job metadata, or {@code null} if the job row or file could not be written (import still succeeded)
     */
    public static JobRecord recordSuccess(int userId, String uploadedFileName, JsonObject importResult) {
        JobDAO jobDAO = new JobDAO();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String referenceName = "SNAP-" + ts;
        JsonArray tables = importResult.getAsJsonArray("tables");
        int tableCount = tables == null ? 0 : tables.size();
        int jobId = -1;
        try {
            jobId = jobDAO.createJob("Bulk Table Snapshot Import", referenceName, tableCount, "Processing", userId);
            JsonObject payload = buildFilePayload(jobId, referenceName, uploadedFileName, importResult);
            File dir = new File(BulkPathUtil.getBulkPathForEntity("table-snapshot-import"));
            if (!dir.exists() && !dir.mkdirs()) {
                throw new java.io.IOException("Could not create directory: " + dir.getAbsolutePath());
            }
            String diskFileName = "table_snapshot_import_" + jobId + ".json";
            File outFile = new File(dir, diskFileName);
            Files.writeString(outFile.toPath(), PRETTY_GSON.toJson(payload), StandardCharsets.UTF_8);
            jobDAO.createJobResourceFile(jobId, diskFileName, "table_snapshot_import_details.json",
                    outFile.getAbsolutePath(), true, 90);
            String summary = computeSummaryLine(importResult);
            jobDAO.createJobProgress(jobId, 100, "Completed", summary);
            jobDAO.updateJobStatus(jobId, "Completed", true);
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100, summary);
            return new JobRecord(jobId, referenceName, summary);
        } catch (Exception e) {
            logger.error("Failed to persist table snapshot import job", e);
            if (jobId > 0) {
                try {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.createJobProgress(jobId, 0, "Failed", "Report file error: " + e.getMessage());
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 0,
                            "Report file error: " + e.getMessage());
                } catch (SQLException ex) {
                    logger.debug("Could not mark snapshot import job failed", ex);
                }
            }
            return null;
        }
    }

    public static String computeSummaryLine(JsonObject importResult) {
        String mode = importResult.has("mode") ? importResult.get("mode").getAsString() : "";
        JsonArray tables = importResult.getAsJsonArray("tables");
        int n = tables == null ? 0 : tables.size();
        long totalAtt = 0;
        long totalIns = 0;
        long totalSkip = 0;
        if (tables != null) {
            for (JsonElement el : tables) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject t = el.getAsJsonObject();
                if (t.has("status") && "skipped".equalsIgnoreCase(t.get("status").getAsString())) {
                    continue;
                }
                if (t.has("rowsAttempted")) {
                    totalAtt += t.get("rowsAttempted").getAsLong();
                }
                if (t.has("rowsInserted")) {
                    totalIns += t.get("rowsInserted").getAsLong();
                }
                if (t.has("rowsSkippedDuplicate")) {
                    totalSkip += t.get("rowsSkippedDuplicate").getAsLong();
                }
            }
        }
        return String.format("Mode: %s | Tables: %d | Σ attempted: %d | Σ inserted: %d | Σ skipped (dup): %d",
                mode, n, totalAtt, totalIns, totalSkip);
    }

    /**
     * Response for the admin UI: no per-table array (details are in the job JSON file).
     */
    public static JsonObject toClientSummary(JsonObject fullImportResult, JobRecord record) {
        JsonObject o = new JsonObject();
        o.addProperty("status", "success");
        if (fullImportResult.has("mode")) {
            o.addProperty("mode", fullImportResult.get("mode").getAsString());
        }
        JsonArray tables = fullImportResult.getAsJsonArray("tables");
        int n = tables == null ? 0 : tables.size();
        long totalAtt = 0;
        long totalIns = 0;
        long totalSkip = 0;
        if (tables != null) {
            for (JsonElement el : tables) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject t = el.getAsJsonObject();
                if (t.has("status") && "skipped".equalsIgnoreCase(t.get("status").getAsString())) {
                    continue;
                }
                if (t.has("rowsAttempted")) {
                    totalAtt += t.get("rowsAttempted").getAsLong();
                }
                if (t.has("rowsInserted")) {
                    totalIns += t.get("rowsInserted").getAsLong();
                }
                if (t.has("rowsSkippedDuplicate")) {
                    totalSkip += t.get("rowsSkippedDuplicate").getAsLong();
                }
            }
        }
        o.addProperty("tableCount", n);
        o.addProperty("totalRowsAttempted", totalAtt);
        o.addProperty("totalRowsInserted", totalIns);
        o.addProperty("totalRowsSkippedDuplicate", totalSkip);
        if (record != null) {
            o.addProperty("job_id", record.jobId);
            o.addProperty("reference_name", record.referenceName);
        }
        return o;
    }

    private static JsonObject buildFilePayload(int jobId, String referenceName, String uploadedFileName,
            JsonObject importResult) {
        JsonObject payload = new JsonObject();
        payload.addProperty("jobId", jobId);
        payload.addProperty("referenceName", referenceName);
        payload.addProperty("uploadedFileName", uploadedFileName == null ? "" : uploadedFileName);
        payload.addProperty("completedAt", Instant.now().toString());
        payload.add("import",
                JsonParser.parseString(COMPACT_GSON.toJson(importResult)).getAsJsonObject());
        return payload;
    }
}
