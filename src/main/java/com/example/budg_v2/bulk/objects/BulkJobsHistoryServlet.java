package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @file BulkJobsHistoryServlet.java
 * @brief REST API Servlet for retrieving bulk upload and migration job history.
 * 
 *        ### File Overview
 *        This servlet manages the retrieval of execution logs for all
 *        bulk-related
 *        activities. It provides detailed context about each job, including
 *        which
 *        facet was affected, the type of operation (Insert/Update/Delete), and
 *        quantitative results (success/failure counts).
 * 
 *        **Business Capability**: Audit Logging and Bulk Operation Monitoring.
 *        **Modules**: Depends on `com.example.budg_v2.dao.JobDAO` for
 *        persistence access.
 * 
 *        ### Responsibility
 *        - Fetching recent bulk jobs with optional user-based filtering.
 *        - Resolution of human-readable entity names from technical reference
 *        codes
 *        (e.g., "SXLE" -> "System X Legal Entity").
 *        - Dynamic metadata discovery from stored JSON descriptors on the
 *        filesystem.
 *        - Aggregation of job statistics for performance and error analysis.
 * 
 *        ### Typical Flow
 *        1. UI requests `/api/bulk/jobs/history` (optionally passing `userId`).
 *        2. Servlet retrieves raw Job records via `jobDAO.getAllBulkJobs`.
 *        3. Logic iterates through jobs, applying heuristics to determine the
 *        logical
 *        entity and operation from the job type and reference name.
 *        4. Statistics are fetched for each job ID.
 *        5. A comprehensive JSON list of job summaries is returned.
 */
@WebServlet("/api/bulk/jobs/history")
public class BulkJobsHistoryServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkJobsHistoryServlet.class);
    private static final Gson gson = new Gson();
    private final JobDAO jobDAO = new JobDAO();
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    /** BULK_COUNTS:inserted=X,failed=Y,updated=Z,deleted=W (from job progress message). */
    private static final Pattern BULK_COUNTS_PATTERN = Pattern.compile(
        "BULK_COUNTS:inserted=(\\d+),failed=(\\d+),updated=(\\d+),deleted=(\\d+)");

    private static int[] parseBulkCountsFromMessage(String message) {
        if (message == null) return null;
        Matcher m = BULK_COUNTS_PATTERN.matcher(message);
        if (!m.find()) {
            // Optional: when message is truncated or BULK_COUNTS is on its own line, try to extract and parse that line
            int idx = message.indexOf("BULK_COUNTS:");
            if (idx >= 0) {
                int end = message.indexOf("\n", idx);
                String line = (end < 0 ? message.substring(idx) : message.substring(idx, end)).trim();
                m = BULK_COUNTS_PATTERN.matcher(line);
                if (!m.find()) return null;
            } else {
                return null;
            }
        }
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

    /** True when upload_option indicates a delete operation (so inserted=0, deleted from counts). */
    private static boolean isDeleteUploadOption(String uploadOption) {
        if (uploadOption == null) return false;
        String u = uploadOption.trim();
        return "DELETE".equalsIgnoreCase(u) || "Remove Existing Items".equalsIgnoreCase(u);
    }

    /**
     * Handles CORS preflight requests.
     * 
     * @purpose Configures browser CORS headers for cross-origin history calls.
     * @param req  The HTTP servlet request.
     * @param resp The HTTP servlet response.
     */
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
        long requestStartMs = System.currentTimeMillis();

        try {
            // Get parameters
            String userIdStr = request.getParameter("userId");
            int limit = parsePositiveIntOrDefault(request.getParameter("limit"), DEFAULT_LIMIT);
            if (limit > MAX_LIMIT) {
                limit = MAX_LIMIT;
            }
            int offset = parseNonNegativeIntOrDefault(request.getParameter("offset"), 0);

            Integer userId = null;
            if (userIdStr != null) {
                try {
                    userId = Integer.parseInt(userIdStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid userId parameter: {}", userIdStr);
                }
            }

            logger.info("Fetching bulk jobs history (userId: {}, limit: {}, offset: {})", userId, limit, offset);

            List<Job> jobs = jobDAO.getAllBulkJobs(userId, limit, offset);
            List<Integer> jobIds = new ArrayList<>(jobs.size());
            for (Job job : jobs) {
                jobIds.add(job.getId());
            }
            Set<Integer> requestedJobIds = new HashSet<>(jobIds);
            Map<Integer, JobProgress> progressByJobId = jobs.isEmpty()
                    ? Collections.emptyMap()
                    : jobDAO.getJobProgressForJobIds(jobIds);
            Set<Integer> downloadableJobIds = jobs.isEmpty()
                    ? Collections.emptySet()
                    : jobDAO.getJobIdsWithResourceFile(jobIds);
            Map<Integer, JsonObject> metadataByJobId = jobs.isEmpty()
                    ? Collections.emptyMap()
                    : buildMetadataIndexForJobIds(requestedJobIds);
            int metadataFallbacks = 0;
            int detailedStatsFallbacks = 0;

            // Build response
            JsonArray jobsArray = new JsonArray();
            for (Job job : jobs) {
                JsonObject jobJson = new JsonObject();
                jobJson.addProperty("job_id", job.getId());
                jobJson.addProperty("reference_name", job.getReferenceName());
                jobJson.addProperty("type", job.getType());

                // Try to get entity and other metadata from metadata JSON file first (most
                // reliable)
                // But check if it's a relationship job first by examining job type and
                // reference name
                String entity = null;
                JsonObject metadata = metadataByJobId.get(job.getId());

                // First check if this is a relationship job by job type
                boolean isRelationshipJob = job.getType() != null &&
                        (job.getType().startsWith("RELATIONSHIP_BULK_") || job.getType().startsWith("RELATIONSHIP_"));

                // Or check if reference name matches relationship pattern (e.g., "SXLE_12345")
                if (!isRelationshipJob && job.getReferenceName() != null &&
                        job.getReferenceName().matches("^[A-Z]{2,10}_\\d+$")) {
                    isRelationshipJob = true;
                }

                if (isRelationshipJob) {
                    // This is a relationship job - set entity to "Relationship" directly
                    entity = "Relationship";
                    jobJson.addProperty("upload_type", "Relationship");
                    jobJson.addProperty("technical_entity", "relationships");

                    // Still try to get metadata for upload_option
                    if (metadata == null) {
                        metadata = getMetadataFromFile(job.getReferenceName(), null, job.getId());
                        if (metadata != null) {
                            metadataFallbacks++;
                        }
                    }
                    if (metadata != null && metadata.has("upload_option")) {
                        jobJson.addProperty("upload_option", metadata.get("upload_option").getAsString());
                    }
                } else {
                    // Check if it's a data migration import job
                    boolean isDataMigrationJob = job.getType() != null && 
                        job.getType().equals("Data Migration Import");
                    
                    // Check if it's a role job
                    boolean isRoleJob = job.getReferenceName() != null && job.getReferenceName().startsWith("ROLE-");
                    
                    if (isDataMigrationJob) {
                        entity = "Data Migration Import";
                        jobJson.addProperty("upload_type", "Data Migration");
                        jobJson.addProperty("technical_entity", "data_migration");
                    } else if (isRoleJob) {
                        jobJson.addProperty("upload_type", "Role");
                        jobJson.addProperty("technical_entity", "role");
                    } else {
                        jobJson.addProperty("upload_type", "Object");
                    }

                    // Skip entity extraction for data migration jobs
                    if (isDataMigrationJob) {
                        // Entity already set above
                    } else {
                        // Not a relationship job - use normal entity extraction logic
                        // First try to extract from reference name (most reliable)
                        String entityFromReference = extractEntityFromReferenceName(job.getReferenceName());

                        if (entityFromReference != null && !entityFromReference.equals("Unknown") && !isRoleJob) {
                            // For regular objects, the technical entity is the cleaned version of the
                            // entity name
                            String technicalEntity = entityFromReference.toLowerCase().replace(" ", "").replace(".", "");
                            jobJson.addProperty("technical_entity", technicalEntity);
                        }

                        // Then try metadata
                        if (metadata == null) {
                            metadata = getMetadataFromFile(job.getReferenceName(), null, job.getId());
                            if (metadata != null) {
                                metadataFallbacks++;
                            }
                        }
                        if (metadata != null && metadata.has("entity")) {
                            String metadataEntity = metadata.get("entity").getAsString();
                            // Regulation uses REG-, Regulator uses REGTOR-; reference prefix disambiguates.
                            if (entityFromReference == null || entityFromReference.equals("Unknown")) {
                                entity = metadataEntity;
                            } else {
                                // Prefer reference name extraction over metadata
                                entity = entityFromReference;
                                logger.trace("Using entity from reference '{}' instead of metadata '{}' for ref: {}",
                                        entityFromReference, metadataEntity, job.getReferenceName());
                            }

                            if (metadata.has("upload_option")) {
                                jobJson.addProperty("upload_option", metadata.get("upload_option").getAsString());
                            }
                        } else {
                            // No metadata, use reference extraction
                            entity = entityFromReference;
                        }

                        // If still not found, try extracting from job type as last resort
                        if (entity == null || entity.equals("Unknown")) {
                            entity = extractEntityFromJobType(job.getType());

                            // Try again with extracted entity to get metadata
                            if (entity != null && !entity.equals("Unknown")) {
                                metadata = getMetadataFromFile(job.getReferenceName(), entity, job.getId());
                                if (metadata != null) {
                                    metadataFallbacks++;
                                }
                                if (metadata != null && metadata.has("upload_option")) {
                                    jobJson.addProperty("upload_option", metadata.get("upload_option").getAsString());
                                }
                            }
                        }
                    }
                }

                // For relationships, try to get display name from reference abbreviation
                String finalEntity = entity != null ? entity : "Unknown";
                if ("Relationship".equals(finalEntity)) {
                    String relationshipDisplayName = getRelationshipDisplayNameFromReference(job.getReferenceName());
                    if (relationshipDisplayName != null) {
                        // Convert display name to short format: "System X Legal Entity" ->
                        // "System_x_Legal"
                        // Replace " X " with "_x_", remove "Entity" suffix
                        String shortName = relationshipDisplayName
                                .replace(" X ", "_x_")
                                .replace(" x ", "_x_")
                                .replace(" Entity", "")
                                .replace(" entity", "")
                                .replace("Entity", "")
                                .replace("entity", "")
                                .trim();

                        // Clean up any double spaces or trailing underscores
                        shortName = shortName.replaceAll("\\s+", " ").trim();
                        if (shortName.endsWith("_")) {
                            shortName = shortName.substring(0, shortName.length() - 1);
                        }

                        // Get operation type and format entity display
                        String operation = getOperationFromJobType(job.getType());
                        String itemsText = "";
                        if (job.getItemsCount() != null && job.getItemsCount() > 0) {
                            itemsText = " " + job.getItemsCount() + " Items";
                        } else {
                            itemsText = " Items";
                        }
                        finalEntity = shortName + " " + operation + itemsText;
                    } else {
                        // Relationship display name lookup failed, use generic format
                        logger.warn("Failed to resolve relationship display name for reference: {}",
                                job.getReferenceName());
                        String operation = getOperationFromJobType(job.getType());
                        finalEntity = "Relationship " + operation + " Items";
                    }
                }

                jobJson.addProperty("entity", finalEntity);
                if (!jobJson.has("technical_entity")) {
                    String fallbackTechnicalEntity = finalEntity.toLowerCase()
                            .replace(" x ", "_x_")
                            .replace(" ", "")
                            .replace(".", "");
                    if ("legalentity".equals(fallbackTechnicalEntity)) {
                        fallbackTechnicalEntity = "legal";
                    } else if ("datamigrationimport".equals(fallbackTechnicalEntity)) {
                        fallbackTechnicalEntity = "data_migration";
                    }
                    jobJson.addProperty("technical_entity", fallbackTechnicalEntity);
                }

                jobJson.addProperty("status", job.getStatus());

                if (job.getCreatedDate() != null) {
                    jobJson.addProperty("created_date", dateFormat.format(job.getCreatedDate()));
                }

                if (job.getCompletedDate() != null) {
                    jobJson.addProperty("completed_date", dateFormat.format(job.getCompletedDate()));
                }

                jobJson.addProperty("items_count", job.getItemsCount() != null ? job.getItemsCount() : 0);
                jobJson.addProperty("created_by", job.getCreatedBy() != null ? job.getCreatedBy() : 0);
                jobJson.addProperty("has_downloadable_file", downloadableJobIds.contains(job.getId()));

                // Get statistics: prefer BULK_COUNTS from job progress so deleted count appears in Deleted square, not Inserted
                try {
                    int inserted = 0;
                    int updated = 0;
                    int deleted = 0;
                    int failed = 0;
                    JobProgress progress = progressByJobId.get(job.getId());
                    int[] bulkCounts = (progress != null && progress.getMessage() != null)
                        ? parseBulkCountsFromMessage(progress.getMessage()) : null;
                    boolean isDeleteJob = isRelationshipJob && metadata != null && metadata.has("upload_option")
                            && isDeleteUploadOption(metadata.get("upload_option").getAsString());
                    // Infer delete job from job type when metadata is missing so deleted count shows in Deleted square
                    if (!isDeleteJob && isRelationshipJob && job.getType() != null
                            && job.getType().toUpperCase().contains("DELETE")) {
                        isDeleteJob = true;
                    }
                    if (bulkCounts != null) {
                        inserted = bulkCounts[0];
                        failed = bulkCounts[1];
                        updated = bulkCounts[2];
                        deleted = bulkCounts[3];
                        // Ensure DELETE jobs never show deleted count in Inserted square; infer delete when counts indicate it (e.g. metadata missing)
                        if (isDeleteJob || (isRelationshipJob && deleted > 0 && inserted == 0)) {
                            isDeleteJob = true;
                            inserted = 0;
                        }
                        // When relationship job has any deleted count, never show count in Inserted square (Project X Data Set and all relationship DELETE)
                        if (isRelationshipJob && deleted > 0) {
                            inserted = 0;
                        }
                        // For any relationship job whose type contains "DELETE" (e.g. RELATIONSHIP_BULK_DELETE), never show success count in Inserted square (Project X Glossary and all relationship DELETE)
                        if (isRelationshipJob && job.getType() != null && job.getType().toUpperCase().contains("DELETE")) {
                            isDeleteJob = true;
                            inserted = 0;
                        }
                    } else {
                        if (shouldComputeDetailedStats(job)) {
                            detailedStatsFallbacks++;
                            Map<String, Integer> stats = jobDAO.getJobStatistics(job.getId());
                            inserted = stats.getOrDefault("inserted", 0);
                            updated = stats.getOrDefault("updated", 0);
                            deleted = stats.getOrDefault("deleted", 0);
                            failed = stats.getOrDefault("failed", 0);
                            // For relationship DELETE jobs (from type or metadata), never show success count in Inserted square
                            if (isRelationshipJob && job.getType() != null && job.getType().toUpperCase().contains("DELETE")) {
                                isDeleteJob = true;
                                deleted = stats.getOrDefault("deleted", 0) + stats.getOrDefault("inserted", 0);
                                inserted = 0;
                            }
                            // For relationship DELETE jobs, getJobStatistics may count success as "inserted" by default; show them in deleted square
                            else if (isDeleteJob) {
                                deleted = deleted + inserted;
                                inserted = 0;
                            } else if (isRelationshipJob && inserted > 0 && updated == 0) {
                                // Metadata missing or no upload_option: infer delete so count is not shown in Inserted square (glossary x glossary delete fix)
                                deleted = inserted;
                                inserted = 0;
                            } else if (isRelationshipJob) {
                                // Infer delete from report items when BULK_COUNTS missing: if any success message contains "deleted", show in Deleted square
                                boolean inferDelete = false;
                                try {
                                    List<JobReportItem> reportItems = jobDAO.getJobReportItems(job.getId());
                                    if (reportItems != null) {
                                        for (JobReportItem item : reportItems) {
                                            if (item != null && "success".equalsIgnoreCase(item.getStatus()) && item.getMessages() != null) {
                                                for (JobReportItemMessage msg : item.getMessages()) {
                                                    if (msg != null && msg.getMessage() != null && msg.getMessage().toLowerCase().contains("deleted")) {
                                                        inferDelete = true;
                                                        break;
                                                    }
                                                }
                                                if (inferDelete) break;
                                            }
                                        }
                                    }
                                } catch (SQLException e) {
                                    logger.debug("Could not get report items for job {} to infer delete: {}", job.getId(), e.getMessage());
                                }
                                if (inferDelete) {
                                    deleted = stats.getOrDefault("deleted", 0) + stats.getOrDefault("inserted", 0);
                                    inserted = 0;
                                    isDeleteJob = true; // So GUI shows count in Deleted square and upload_option is added below
                                }
                            }
                            // When relationship job has deleted count, do not show it in Inserted square (Regulator X Geography delete fix)
                            if (isRelationshipJob && deleted > 0) {
                                inserted = 0;
                            }
                        }
                    }
                    if (isDeleteJob) {
                        inserted = 0;
                    }
                    // Final safeguard: for relationship jobs with any deleted count, never show it in Inserted square (Project X Project and all relationship DELETE)
                    if (isRelationshipJob && deleted > 0) {
                        inserted = 0;
                    }
                    jobJson.addProperty("inserted", inserted);
                    jobJson.addProperty("updated", updated);
                    jobJson.addProperty("deleted", deleted);
                    jobJson.addProperty("failed", failed);
                    // When delete job was inferred from job type, ensure GUI gets upload_option so count shows in Deleted square
                    if (isRelationshipJob && isDeleteJob && !jobJson.has("upload_option")) {
                        jobJson.addProperty("upload_option", "DELETE");
                    }
                } catch (Exception e) {
                    logger.warn("Could not get statistics for job {}: {}", job.getId(), e.getMessage());
                    jobJson.addProperty("inserted", 0);
                    jobJson.addProperty("updated", 0);
                    jobJson.addProperty("deleted", 0);
                    jobJson.addProperty("failed", 0);
                }

                jobsArray.add(jobJson);
            }

            JsonObject responseObj = new JsonObject();
            responseObj.add("jobs", jobsArray);
            responseObj.addProperty("count", jobs.size());
            responseObj.addProperty("limit", limit);
            responseObj.addProperty("offset", offset);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(responseObj));

            long elapsedMs = System.currentTimeMillis() - requestStartMs;
            logger.info("Successfully returned {} bulk jobs in {}ms (metadataFallbacks: {}, detailedStatsFallbacks: {})",
                    jobs.size(), elapsedMs, metadataFallbacks, detailedStatsFallbacks);

        } catch (SQLException e) {
            logger.error("Database error fetching jobs history", e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Error fetching jobs history", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    private int parsePositiveIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int parseNonNegativeIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean shouldComputeDetailedStats(Job job) {
        if (job == null || job.getStatus() == null) {
            return false;
        }
        String statusLower = job.getStatus().toLowerCase();
        return statusLower.contains("completed") || statusLower.contains("failed");
    }

    private Map<Integer, JsonObject> buildMetadataIndexForJobIds(Set<Integer> targetJobIds) {
        if (targetJobIds == null || targetJobIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, JsonObject> metadataByJobId = new HashMap<>();
        for (String basePath : getAllMetadataDirectories()) {
            File dir = new File(basePath);
            if (!dir.exists() || !dir.isDirectory()) {
                continue;
            }
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                try {
                    String jsonContent = Files.readString(file.toPath());
                    JsonObject metadata = gson.fromJson(jsonContent, JsonObject.class);
                    if (metadata != null && metadata.has("job_id")) {
                        int jobId = metadata.get("job_id").getAsInt();
                        if (targetJobIds.contains(jobId) && !metadataByJobId.containsKey(jobId)) {
                            metadataByJobId.put(jobId, metadata);
                            if (metadataByJobId.size() == targetJobIds.size()) {
                                return metadataByJobId;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Skip malformed metadata files
                }
            }
        }
        return metadataByJobId;
    }

    /**
     * Extract entity name from job type
     * Examples: "Process Upload" -> "Process", "Regulator Update" -> "Regulator",
     * "Policy Delete" -> "Policy"
     */
    /**
     * Extract entity name from reference name prefix
     * Maps reference name prefixes to entity names
     */
    private String extractEntityFromReferenceName(String referenceName) {
        if (referenceName == null || referenceName.trim().isEmpty()) {
            return "Unknown";
        }

        // Map of reference prefixes to entity names (REGTOR- before REG- is unnecessary: prefixes differ)
        if (referenceName.startsWith("REGTOR-"))
            return "Regulator";
        if (referenceName.startsWith("REG-"))
            return "Regulation";
        if (referenceName.startsWith("RTH-"))
            return "Regulatory Theme";
        if (referenceName.startsWith("POL-"))
            return "Policy";
        if (referenceName.startsWith("PRC-"))
            return "Process";
        if (referenceName.startsWith("PRJ-"))
            return "Project";
        if (referenceName.startsWith("PRD-"))
            return "Product";
        if (referenceName.startsWith("LEG-"))
            return "Legal Entity";
        if (referenceName.startsWith("PEO-"))
            return "People";
        if (referenceName.startsWith("CLI-"))
            return "Client";
        if (referenceName.startsWith("COM-"))
            return "Committee";
        if (referenceName.startsWith("ORG-"))
            return "Org. Unit";
        if (referenceName.startsWith("BA-"))
            return "Business Area";
        if (referenceName.startsWith("GEO-"))
            return "Geography";
        if (referenceName.startsWith("SYS-"))
            return "System";
        if (referenceName.startsWith("DS-"))
            return "Dataset";
        if (referenceName.startsWith("ATTR-") || referenceName.startsWith("ATT-"))
            return "Attribute";
        if (referenceName.startsWith("GLO-"))
            return "Glossary";
        if (referenceName.startsWith("INT-"))
            return "Interface";
        if (referenceName.startsWith("CAP-"))
            return "Capability";

        // Role bulk uploads - new format: ROLE-{entityType}-{number}
        // Check specific role types first (order matters - more specific first)
        if (referenceName.startsWith("ROLE-LEGAL-"))
            return "Legal Entity Role";
        if (referenceName.startsWith("ROLE-POLICY-"))
            return "Policy Role";
        if (referenceName.startsWith("ROLE-PROCESS-"))
            return "Process Role";
        if (referenceName.startsWith("ROLE-PRODUCT-"))
            return "Product Role";
        if (referenceName.startsWith("ROLE-SYSTEM-"))
            return "System Role";
        if (referenceName.startsWith("ROLE-DATASET-"))
            return "Dataset Role";
        if (referenceName.startsWith("ROLE-REG-"))
            return "Regulation Role";
        if (referenceName.startsWith("ROLE-REGTOR-"))
            return "Regulator Role";
        if (referenceName.startsWith("ROLE-BUSAREA-"))
            return "Business Area Role";
        if (referenceName.startsWith("ROLE-CAPAB-"))
            return "Capability Role";
        if (referenceName.startsWith("ROLE-CLIENT-"))
            return "Client Role";
        if (referenceName.startsWith("ROLE-COMIT-"))
            return "Committee Role";
        if (referenceName.startsWith("ROLE-GLOSS-"))
            return "Glossary Role";
        if (referenceName.startsWith("ROLE-INTERF-"))
            return "Interface Role";
        if (referenceName.startsWith("ROLE-PROJEC-"))
            return "Project Role";
        // Legacy role format (must be last)
        if (referenceName.startsWith("ROLE-"))
            return "Role";

        // Relationship bulk uploads - format: {ABBREV}_{timestamp}
        // Relationships use first letters of each word (e.g., "Policy X System" ->
        // "PXS")
        // Check common relationship patterns
        if (referenceName.matches("^[A-Z]{2,10}_\\d+$")) {
            // Pattern matches relationship format (e.g., PXS_12345, PXC_12346, SXC_39886)
            // Return "Relationship" - display name will be extracted in
            // getRelationshipDisplayNameFromReference
            return "Relationship";
        }

        // Legacy format: BULK_REG_...
        if (referenceName.startsWith("BULK_REG_"))
            return "Regulator";

        return "Unknown";
    }

    private String extractEntityFromJobType(String jobType) {
        if (jobType == null || jobType.trim().isEmpty()) {
            return "Unknown";
        }

        String trimmedType = jobType.trim();

        // If job type is generic "bulk_upload", can't extract entity from it
        if ("bulk_upload".equalsIgnoreCase(trimmedType)) {
            return "Unknown";
        }

        // Handle relationship bulk uploads - format: "RELATIONSHIP_BULK_INSERT",
        // "RELATIONSHIP_BULK_UPDATE", etc.
        if (trimmedType.startsWith("RELATIONSHIP_BULK_") || trimmedType.startsWith("RELATIONSHIP_")) {
            return "Relationship";
        }

        // Remove operation suffixes (Upload, Update, Delete)
        String entity = trimmedType;
        if (entity.endsWith(" Upload") || entity.endsWith(" Update") || entity.endsWith(" Delete")) {
            entity = entity.substring(0, entity.lastIndexOf(" "));
        }

        return entity.isEmpty() ? "Unknown" : entity;
    }

    /**
     * Get relationship display name from reference abbreviation
     * Example: "SXC_39886" -> "System X Client", "SXLE_5473" -> "System X Legal
     * Entity"
     */
    private String getRelationshipDisplayNameFromReference(String referenceName) {
        if (referenceName == null || !referenceName.matches("^[A-Z]{2,10}_\\d+$")) {
            return null;
        }

        // Extract abbreviation (e.g., "SXC" from "SXC_39886" or "SXLE" from
        // "SXLE_5473")
        String abbreviation = referenceName.split("_")[0];

        // Normalize abbreviation by removing "X" if present (for comparison)
        String normalizedAbbrev = abbreviation.replace("X", "").replace("x", "");

        logger.debug("Trying to match abbreviation: {} (normalized: {}) from reference: {}",
                abbreviation, normalizedAbbrev, referenceName);

        // Try to find matching relationship config by checking all configs
        // and matching their generated abbreviations
        for (String key : RelationshipConfigRegistry.getSupportedKeys()) {
            RelationshipConfig config = RelationshipConfigRegistry.getConfig(key);
            if (config != null) {
                String displayName = config.getDisplayName();
                // Generate abbreviation from display name (ignoring "X")
                String[] words = displayName.split("[\\s_]+");
                StringBuilder generatedAbbrev = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty() && !word.equalsIgnoreCase("X")) {
                        generatedAbbrev.append(word.charAt(0));
                    }
                }

                // Compare normalized abbreviations (both without "X")
                if (generatedAbbrev.toString().equalsIgnoreCase(normalizedAbbrev)) {
                    logger.debug("Matched abbreviation {} to relationship: {}", abbreviation, displayName);
                    return displayName;
                }
            }
        }

        logger.debug("No match found for abbreviation: {}", abbreviation);
        return null;
    }

    /**
     * Get operation type from job type
     * Example: "RELATIONSHIP_BULK_INSERT" -> "Upload New"
     * "RELATIONSHIP_BULK_UPDATE" -> "Update"
     * "RELATIONSHIP_BULK_DELETE" -> "Delete"
     */
    private String getOperationFromJobType(String jobType) {
        if (jobType == null) {
            return "Upload";
        }

        String upperType = jobType.toUpperCase();
        if (upperType.contains("INSERT")) {
            return "Upload New";
        } else if (upperType.contains("UPDATE")) {
            return "Update";
        } else if (upperType.contains("DELETE")) {
            return "Delete";
        }

        return "Upload";
    }

    /**
     * Get metadata from JSON file based on reference name and entity
     * Supports multiple entity types:
     * - Regulator: "BULK_REG_20241105_145623_a3b5c7" ->
     * "regulator_20241105_145623_a3b5c7.json"
     * - Process: "PRC-123" -> searches in process directory for matching metadata
     * by job_id
     * - Regulation: "REG-7" -> searches in regulation directory for matching
     * metadata by job_id
     * - Other entities: searches in their respective directories
     * 
     * If entity is null, searches all bulk directories for matching job_id
     */
    private JsonObject getMetadataFromFile(String referenceName, String entity, int jobId) {
        if (referenceName == null) {
            return null;
        }

        // If entity is provided, search in that entity's directory
        if (entity != null && !entity.isEmpty()) {
            try {
                String basePath = getBasePathForEntity(entity);
                String metadataFileName = getMetadataFileName(referenceName, entity, jobId);

                if (metadataFileName == null) {
                    return null;
                }

                File metadataFile = new File(basePath + metadataFileName);

                if (metadataFile.exists()) {
                    String jsonContent = Files.readString(metadataFile.toPath());
                    return gson.fromJson(jsonContent, JsonObject.class);
                }
            } catch (Exception e) {
                logger.debug("Could not read metadata file for reference {} (entity: {}, jobId: {}): {}",
                        referenceName, entity, jobId, e.getMessage());
            }
        }

        // If entity is null or file not found, search all bulk directories by job_id
        return searchMetadataFileByJobIdInAllDirectories(jobId);
    }

    /**
     * Get base path for entity's bulk upload directory (uses configured bulk.template.path).
     */
    private String getBasePathForEntity(String entity) {
        if (entity == null) {
            return BulkPathUtil.getBulkPathForEntity("regulator");
        }

        // Handle Relationships
        if (entity.equals("Relationship")) {
            return BulkPathUtil.getBulkPathForEntity("relationships");
        }

        // Handle Roles (all role types go to roles directory)
        if (entity.endsWith(" Role")) {
            return BulkPathUtil.getBulkPathForEntity("roles");
        }

        // Convert entity name to lowercase directory name
        String entityLower = entity.toLowerCase().replace(" ", "").replace(".", "");

        // Handle special cases
        switch (entity) {
            case "Regulatory Theme":
                return BulkPathUtil.getBulkPathForEntity("regulatorytheme");
            case "Org. Unit":
                return BulkPathUtil.getBulkPathForEntity("orgunit");
            case "Legal Entity":
                return BulkPathUtil.getBulkPathForEntity("legalentity");
            default:
                return BulkPathUtil.getBulkPathForEntity(entityLower);
        }
    }

    /**
     * Get metadata file name from reference name and entity
     */
    private String getMetadataFileName(String referenceName, String entity, int jobId) {
        if (referenceName == null || entity == null) {
            return null;
        }

        // Regulator: "BULK_REG_20241105_145623_a3b5c7" ->
        // "regulator_20241105_145623_a3b5c7.json"
        if (referenceName.startsWith("BULK_REG_")) {
            return referenceName.replace("BULK_REG_", "regulator_") + ".json";
        }

        // Regulator job ref: "REGTOR-7" -> regulator_*.json by job_id
        if (referenceName.startsWith("REGTOR-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Regulation: "REG-7" -> regulation_*.json by job_id
        if (referenceName.startsWith("REG-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Process: "PRC-123" -> need to search for process_*.json files
        // Since Process uses PRC-{number} format, we need to search all metadata files
        // and match by job_id in the metadata (more reliable than reference_name)
        if (referenceName.startsWith("PRC-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Project: "PRJ-123" -> need to search for project_*.json files
        // Since Project uses PRJ-{number} format, we need to search all metadata files
        // and match by job_id in the metadata (more reliable than reference_name)
        if (referenceName.startsWith("PRJ-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Role: "ROLE-LEGAL-123" or "ROLE-123" (legacy) -> need to search for
        // role_*.json files
        // Since Role uses ROLE-{entityType}-{number} format, we need to search all
        // metadata files
        // and match by job_id in the metadata (more reliable than reference_name)
        if (referenceName.startsWith("ROLE-")) {
            String basePath = BulkPathUtil.getBulkPathForEntity("roles");
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Committee: "COM-123" -> need to search for committee_*.json files
        // Since Committee uses COM-{number} format, we need to search all metadata
        // files
        // and match by job_id in the metadata (more reliable than reference_name)
        if (referenceName.startsWith("COM-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Regulator: "REGTOR-123" -> need to search for regulator_*.json files
        if (referenceName.startsWith("REGTOR-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Regulatory Theme: "RTH-123" -> need to search for regulatorytheme_*.json
        // files
        if (referenceName.startsWith("RTH-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Policy: "POL-123" -> need to search for policy_*.json files
        if (referenceName.startsWith("POL-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Product: "PRD-123" -> need to search for product_*.json files
        if (referenceName.startsWith("PRD-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Legal Entity: "LEG-123" -> need to search for legalentity_*.json files
        if (referenceName.startsWith("LEG-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // People: "PEO-123" -> need to search for people_*.json files
        if (referenceName.startsWith("PEO-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Client: "CLI-123" -> need to search for client_*.json files
        if (referenceName.startsWith("CLI-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Org. Unit: "ORG-123" -> need to search for orgunit_*.json files
        if (referenceName.startsWith("ORG-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Business Area: "BA-123" -> need to search for businessarea_*.json files
        if (referenceName.startsWith("BA-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Geography: "GEO-123" -> need to search for geography_*.json files
        if (referenceName.startsWith("GEO-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // System: "SYS-123" -> need to search for system_*.json files
        if (referenceName.startsWith("SYS-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Dataset: "DS-123" -> need to search for dataset_*.json files
        if (referenceName.startsWith("DS-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Attribute: "ATTR-123" or "ATT-123" -> need to search for attribute_*.json
        // files
        if (referenceName.startsWith("ATTR-") || referenceName.startsWith("ATT-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Glossary: "GLO-123" -> need to search for glossary_*.json files
        if (referenceName.startsWith("GLO-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Interface: "INT-123" -> need to search for interface_*.json files
        if (referenceName.startsWith("INT-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Capability: "CAP-123" -> need to search for capability_*.json files
        if (referenceName.startsWith("CAP-")) {
            String basePath = getBasePathForEntity(entity);
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // Relationship bulk uploads - format: {ABBREV}_{timestamp} (e.g., PXS_12345,
        // PXC_12346)
        // Relationships are stored in configured bulk/relationships directory
        if (referenceName.matches("^[A-Z]{2,10}_\\d+$")) {
            String basePath = BulkPathUtil.getBulkPathForEntity("relationships");
            return searchMetadataFileByJobId(basePath, jobId, referenceName);
        }

        // For other entities, try common patterns
        // Try to find metadata file - this is a fallback that searches the directory
        String basePath = getBasePathForEntity(entity);
        return searchMetadataFileByReference(basePath, referenceName, jobId);
    }

    /**
     * Search for metadata file by reading all JSON files in directory and matching
     * reference_name or job_id
     */
    private String searchMetadataFileByReference(String basePath, String referenceName, int jobId) {
        try {
            File dir = new File(basePath);
            if (!dir.exists() || !dir.isDirectory()) {
                return null;
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) {
                return null;
            }

            for (File file : files) {
                try {
                    String jsonContent = Files.readString(file.toPath());
                    JsonObject metadata = gson.fromJson(jsonContent, JsonObject.class);

                    // Match by reference_name first, then by job_id as fallback
                    if (metadata.has("reference_name") &&
                            referenceName.equals(metadata.get("reference_name").getAsString())) {
                        return file.getName();
                    }

                    // Also try matching by job_id (more reliable for Process jobs)
                    if (metadata.has("job_id") &&
                            jobId == metadata.get("job_id").getAsInt()) {
                        return file.getName();
                    }
                } catch (Exception e) {
                    // Skip files that can't be parsed
                    continue;
                }
            }
        } catch (Exception e) {
            logger.debug("Error searching metadata files in {}: {}", basePath, e.getMessage());
        }

        return null;
    }

    /**
     * Search for metadata file by job_id (more reliable for Process and other
     * entities)
     */
    private String searchMetadataFileByJobId(String basePath, int jobId, String referenceName) {
        try {
            File dir = new File(basePath);
            if (!dir.exists() || !dir.isDirectory()) {
                return null;
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) {
                return null;
            }

            for (File file : files) {
                try {
                    String jsonContent = Files.readString(file.toPath());
                    JsonObject metadata = gson.fromJson(jsonContent, JsonObject.class);

                    // Match by job_id (most reliable)
                    if (metadata.has("job_id") &&
                            jobId == metadata.get("job_id").getAsInt()) {
                        return file.getName();
                    }

                    // Fallback: match by reference_name
                    if (referenceName != null && metadata.has("reference_name") &&
                            referenceName.equals(metadata.get("reference_name").getAsString())) {
                        return file.getName();
                    }
                } catch (Exception e) {
                    // Skip files that can't be parsed
                    continue;
                }
            }
        } catch (Exception e) {
            logger.debug("Error searching metadata files by job_id in {}: {}", basePath, e.getMessage());
        }

        return null;
    }

    /**
     * Search for metadata file by job_id across all bulk upload directories
     * Used when entity is unknown
     */
    private String[] getAllMetadataDirectories() {
        return new String[] {
                BulkPathUtil.getBulkPathForEntity("regulator"),
                BulkPathUtil.getBulkPathForEntity("relationships"),
                BulkPathUtil.getBulkPathForEntity("regulation"),
                BulkPathUtil.getBulkPathForEntity("process"),
                BulkPathUtil.getBulkPathForEntity("policy"),
                BulkPathUtil.getBulkPathForEntity("project"),
                BulkPathUtil.getBulkPathForEntity("geography"),
                BulkPathUtil.getBulkPathForEntity("regulatorytheme"),
                BulkPathUtil.getBulkPathForEntity("committee"),
                BulkPathUtil.getBulkPathForEntity("roles"),
                BulkPathUtil.getBulkPathForEntity("people"),
                BulkPathUtil.getBulkPathForEntity("legalentity"),
                BulkPathUtil.getBulkPathForEntity("orgunit"),
                BulkPathUtil.getBulkPathForEntity("client"),
                BulkPathUtil.getBulkPathForEntity("businessarea"),
                BulkPathUtil.getBulkPathForEntity("system"),
                BulkPathUtil.getBulkPathForEntity("dataset"),
                BulkPathUtil.getBulkPathForEntity("attribute"),
                BulkPathUtil.getBulkPathForEntity("glossary"),
                BulkPathUtil.getBulkPathForEntity("interface"),
                BulkPathUtil.getBulkPathForEntity("capability"),
                BulkPathUtil.getBulkPathForEntity("product")
        };
    }

    private JsonObject searchMetadataFileByJobIdInAllDirectories(int jobId) {
        for (String basePath : getAllMetadataDirectories()) {
            try {
                File dir = new File(basePath);
                if (!dir.exists() || !dir.isDirectory()) {
                    continue;
                }

                File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                if (files == null) {
                    continue;
                }

                for (File file : files) {
                    try {
                        String jsonContent = Files.readString(file.toPath());
                        JsonObject metadata = gson.fromJson(jsonContent, JsonObject.class);

                        // Match by job_id
                        if (metadata.has("job_id") &&
                                jobId == metadata.get("job_id").getAsInt()) {
                            return metadata;
                        }
                    } catch (Exception e) {
                        // Skip files that can't be parsed
                        continue;
                    }
                }
            } catch (Exception e) {
                logger.debug("Error searching metadata files in {}: {}", basePath, e.getMessage());
            }
        }

        return null;
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
