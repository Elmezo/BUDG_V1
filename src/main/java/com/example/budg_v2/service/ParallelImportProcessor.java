package com.example.budg_v2.service;

import com.example.budg_v2.bulk.MigrationManifestEntry;
import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.util.HttpClientUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process independent entities in parallel for better performance
 * Groups entities by dependency level and processes each level in parallel
 */
public class ParallelImportProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ParallelImportProcessor.class);

    private final JobDAO jobDAO = new JobDAO();
    private final EntityProcessorRegistry processorRegistry = EntityProcessorRegistry.getInstance();
    private final RelationshipDetector relationshipDetector = new RelationshipDetector();

    // Thread pool for parallel processing
    private final ExecutorService executorService;
    private final int maxConcurrentThreads;

    public ParallelImportProcessor() {
        this.maxConcurrentThreads = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(maxConcurrentThreads);
        logger.info("Initialized ParallelImportProcessor with {} threads", maxConcurrentThreads);
    }

    public ParallelImportProcessor(int maxThreads) {
        this.maxConcurrentThreads = maxThreads;
        this.executorService = Executors.newFixedThreadPool(maxThreads);
        logger.info("Initialized ParallelImportProcessor with {} threads", maxThreads);
    }

    /**
     * Process entities in parallel based on dependencies
     */
    public void processInParallel(List<MigrationManifestEntry> entries,
            Map<String, File> files, int parentJobId, int userId) {
        processInParallel(entries, files, parentJobId, userId, false, false, false);
    }

    /**
     * @param mergeMode when true, singleton entities use Python "Update Existing Items" and processor UPDATE;
     *                  relationships, hierarchy, and roles stay INSERT/add semantics.
     */
    public void processInParallel(List<MigrationManifestEntry> entries,
            Map<String, File> files, int parentJobId, int userId, boolean mergeMode) {
        processInParallel(entries, files, parentJobId, userId, mergeMode, false, false);
    }

    /**
     * @param sequentialWithinDependencyLevel when true, files at the same dependency level are processed
     *                                        one after another (not in parallel). Use for ENV replace imports
     *                                        so Python validation sees committed parents from earlier files in
     *                                        the same level (e.g. System before Dataset).
     */
    public void processInParallel(List<MigrationManifestEntry> entries,
            Map<String, File> files, int parentJobId, int userId, boolean mergeMode,
            boolean sequentialWithinDependencyLevel) {
        processInParallel(entries, files, parentJobId, userId, mergeMode, sequentialWithinDependencyLevel, false);
    }

    /**
     * @param envMergeFacetInsertMode when true (Bulk Import ENV with merge), facet workbooks use
     *                                {@code Add New Items} / INSERT instead of update-by-ID. Otherwise merge
     *                                would require every exported ID to already exist in the target DB.
     */
    public void processInParallel(List<MigrationManifestEntry> entries,
            Map<String, File> files, int parentJobId, int userId, boolean mergeMode,
            boolean sequentialWithinDependencyLevel, boolean envMergeFacetInsertMode) {
        logger.info("Starting parallel processing of {} entries (mergeMode={}, sequentialWithinLevel={}, envMergeFacetInsert={})",
                entries.size(), mergeMode, sequentialWithinDependencyLevel, envMergeFacetInsertMode);

        try {
            // Detect relationships and build dependency graph
            DependencyGraph graph = relationshipDetector.detectRelationships(entries, files);

            // Check for circular dependencies — abort to prevent FK violations
            if (graph.hasCircularDependency()) {
                List<String> suggestions = relationshipDetector.suggestResolutionForCircularDependency(graph);
                String circularDetail = suggestions != null && !suggestions.isEmpty()
                        ? String.join("; ", suggestions)
                        : "no additional detail available";
                logger.error("Circular dependencies detected — aborting parallel import. Details: {}",
                        circularDetail);
                throw new IllegalStateException(
                        "Import aborted: circular dependencies detected between files. Details: " + circularDetail);
            }

            // Group entries by dependency level
            List<List<MigrationManifestEntry>> groups = groupByDependencies(entries, graph);

            logger.info("Grouped {} entries into {} dependency levels", entries.size(), groups.size());

            // Process each group sequentially, but entities within a group in parallel
            AtomicInteger processedCount = new AtomicInteger(0);
            int totalEntries = entries.size();

            for (int level = 0; level < groups.size(); level++) {
                List<MigrationManifestEntry> group = groups.get(level);
                logger.info("Processing dependency level {} with {} entities", level, group.size());

                processGroup(group, files, parentJobId, userId, processedCount, totalEntries, mergeMode,
                        sequentialWithinDependencyLevel, envMergeFacetInsertMode);

                // Wait for all tasks in this level to complete before proceeding
                // (This is handled by processGroup using CountDownLatch)
            }

            // Update parent job
            jobDAO.updateJobStatus(parentJobId, "Completed", true);
            jobDAO.createJobProgress(parentJobId, 100, "Completed",
                    "All entities processed successfully");
            BulkUploadBroadcaster.getInstance().broadcast(parentJobId, "Completed", 100,
                    "All entities processed successfully");

            logger.info("Parallel processing completed for parent job {}", parentJobId);

        } catch (Throwable e) {
            logger.error("Error in parallel processing: {}", e.getMessage(), e);
            try {
                jobDAO.updateJobStatus(parentJobId, "Failed", true);
                jobDAO.createJobProgress(parentJobId, 0, "Failed",
                        "Error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(parentJobId, "Failed", 0,
                        "Error: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("Error updating parent job status", ex);
            }
        }
    }

    /**
     * Group entries by dependency level.
     * Entries whose entity is not present in the dependency graph (no detected
     * relationships) are placed at level 0 so they are never silently dropped.
     */
    public List<List<MigrationManifestEntry>> groupByDependencies(
            List<MigrationManifestEntry> entries, DependencyGraph graph) {

        // Get dependency levels (only contains entities that have at least one edge)
        Map<String, Integer> levels = graph.getDependencyLevels();

        // Group by level while preserving manifest / input list order within each level.
        // (HashMap iteration over entities was non-deterministic and could violate intended tie-breaks.)
        Map<Integer, List<MigrationManifestEntry>> levelGroups = new TreeMap<>();
        for (MigrationManifestEntry entry : entries) {
            String entityName = MigrationTargetRefResolver.resolveEntityLabel(entry.targetRef);
            if (entityName == null) {
                continue;
            }
            int level = levels.getOrDefault(entityName, 0);
            levelGroups.computeIfAbsent(level, k -> new ArrayList<>()).add(entry);
        }

        List<List<MigrationManifestEntry>> orderedGroups = new ArrayList<>(levelGroups.values());
        for (int i = 0; i < orderedGroups.size(); i++) {
            List<MigrationManifestEntry> g = orderedGroups.get(i);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < g.size(); j++) {
                if (j > 0) {
                    sb.append(", ");
                }
                String en = MigrationTargetRefResolver.resolveEntityLabel(g.get(j).targetRef);
                sb.append(en != null ? en : "?").append("(").append(g.get(j).fileEntry).append(")");
            }
            logger.info("Dependency level {} execution order ({} files): {}", i, g.size(), sb);
        }

        return orderedGroups;
    }

    private void bumpGroupProgress(int parentJobId, AtomicInteger processedCount, int totalEntries) {
        int processed = processedCount.incrementAndGet();
        int progress = 10 + (int) ((processed / (double) totalEntries) * 90);
        try {
            jobDAO.createJobProgress(parentJobId, progress, "Processing",
                    "Processed " + processed + " entities");
            BulkUploadBroadcaster.getInstance().broadcast(parentJobId, "Processing", progress,
                    "Processed " + processed + " entities");
        } catch (Exception ex) {
            logger.error("Error updating progress", ex);
        }
    }

    /**
     * Process a group of entities in parallel.
     *
     * @param totalEntries total number of entries across ALL groups — used for
     *                     accurate progress reporting.
     */
    private void processGroup(List<MigrationManifestEntry> group,
            Map<String, File> files, int parentJobId, int userId,
            AtomicInteger processedCount, int totalEntries, boolean mergeMode,
            boolean sequentialWithinDependencyLevel, boolean envMergeFacetInsertMode) {

        if (group.isEmpty()) {
            return;
        }

        if (sequentialWithinDependencyLevel) {
            for (MigrationManifestEntry entry : group) {
                try {
                    processEntry(entry, files, parentJobId, userId, mergeMode, envMergeFacetInsertMode);
                } catch (Throwable e) {
                    logger.error("Error processing entry {}: {}", entry.fileEntry, e.getMessage(), e);
                } finally {
                    bumpGroupProgress(parentJobId, processedCount, totalEntries);
                }
            }
            return;
        }

        CountDownLatch latch = new CountDownLatch(group.size());
        List<Future<?>> futures = new ArrayList<>();

        for (MigrationManifestEntry entry : group) {
            Future<?> future = executorService.submit(() -> {
                try {
                    processEntry(entry, files, parentJobId, userId, mergeMode, envMergeFacetInsertMode);
                } catch (Throwable e) {
                    logger.error("Error processing entry {}: {}", entry.fileEntry, e.getMessage(), e);
                } finally {
                    latch.countDown();
                    bumpGroupProgress(parentJobId, processedCount, totalEntries);
                }
            });
            futures.add(future);
        }

        // Wait for all tasks to complete
        try {
            latch.await(30, TimeUnit.MINUTES); // Max 30 minutes per level
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for group processing", e);
            Thread.currentThread().interrupt();
        }

        // Check for exceptions
        for (Future<?> future : futures) {
            try {
                future.get(); // This will throw if there was an exception
            } catch (Exception e) {
                logger.error("Error in parallel task: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Process a single entry
     */
    private void processEntry(MigrationManifestEntry entry,
            Map<String, File> files, int parentJobId, int userId, boolean mergeMode,
            boolean envMergeFacetInsertMode) {
        File file = files.get(entry.fileEntry);
        if (file == null || !file.exists()) {
            logger.warn("File not found: {}", entry.fileEntry);
            return;
        }

        String entityName = MigrationTargetRefResolver.resolveEntityLabel(entry.targetRef);
        if (entityName == null) {
            logger.warn("Cannot extract entity name from targetRef: {}", entry.targetRef);
            return;
        }

        boolean mergeUpsert = shouldUseMergeUpsert(entry.targetRef, entityName, mergeMode, envMergeFacetInsertMode);
        String pythonUploadOption = mergeUpsert ? "Update Existing Items" : "Add New Items";
        String processorUploadOption = mergeUpsert ? "UPDATE" : "INSERT";

        // Create child job
        int childJobId = -1;
        try {
            String referenceName = "MIG-" + entityName + "-" + System.currentTimeMillis();
            childJobId = jobDAO.createJob("Data Migration Import", referenceName, 0, "Pending", userId);
            jobDAO.createJobProgress(childJobId, 0, "Validating", "Starting validation...");

            // Call Python validation service
            JsonObject validationRequest = new JsonObject();
            validationRequest.addProperty("file_path", file.getAbsolutePath());
            validationRequest.addProperty("upload_option", pythonUploadOption);
            validationRequest.addProperty("entity", entityName);
            validationRequest.addProperty("user_id", userId);
            MigrationColumnMappings.addToValidationRequest(validationRequest, entityName);

            String pythonServiceUrl = MigrationConfig.getInstance().getPythonServiceUrl();
            logger.info("Calling Python validation service for entity: {}", entityName);
            JsonObject validationResponse = HttpClientUtil.postJsonGetJson(pythonServiceUrl, validationRequest);

            String validationStatus = validationResponse.has("status") ? validationResponse.get("status").getAsString()
                    : "error";

            // "error" = fatal (no data can be processed); bail out entirely.
            // "invalid" = soft validation warnings but may still have valid rows in
            // "data"  mirror the behaviour of the regular bulk-upload servlets and
            // continue processing whatever valid rows Python returned.
            if ("error".equals(validationStatus)) {
                String errorMsg = validationResponse.has("error") ? validationResponse.get("error").getAsString()
                        : (validationResponse.has("message") ? validationResponse.get("message").getAsString()
                                : "Validation failed with fatal error");
                logger.error("Fatal validation error for {}: {}", entityName, errorMsg);
                jobDAO.updateJobStatus(childJobId, "Failed", true);
                jobDAO.updateJobProgress(childJobId, "Failed", errorMsg);
                BulkUploadBroadcaster.getInstance().broadcast(childJobId, "Failed", 100, errorMsg);
                return;
            }

            if ("invalid".equals(validationStatus)) {
                // Check if there are any valid rows to process despite warnings
                JsonArray dataCheck = validationResponse.has("data")
                        ? validationResponse.getAsJsonArray("data")
                        : new JsonArray();
                if (dataCheck.size() == 0) {
                    String errorMsg = validationResponse.has("message")
                            ? validationResponse.get("message").getAsString()
                            : "Validation failed  no valid rows to process";
                    logger.error("Validation produced no valid rows for {}: {}", entityName, errorMsg);
                    jobDAO.updateJobStatus(childJobId, "Failed", true);
                    jobDAO.updateJobProgress(childJobId, "Failed", errorMsg);
                    BulkUploadBroadcaster.getInstance().broadcast(childJobId, "Failed", 100, errorMsg);
                    return;
                }
                // There are valid rows  log the warnings and continue
                logger.warn("Validation returned 'invalid' for {} but {} valid rows found; continuing with valid rows",
                        entityName, dataCheck.size());
            }

            // Validation succeeded (or had warnings with valid rows)  process data
            JsonArray validatedData = validationResponse.getAsJsonArray("data");
            int totalRows = validatedData.size();
            jobDAO.updateJobItemsCount(childJobId, totalRows);

            // GenericEntityProcessor infers the table from each row's "entity" field; Python often omits it.
            for (int i = 0; i < validatedData.size(); i++) {
                if (validatedData.get(i).isJsonObject()) {
                    JsonObject row = validatedData.get(i).getAsJsonObject();
                    if (!row.has("entity") || row.get("entity").isJsonNull()) {
                        row.addProperty("entity", entityName);
                    }
                }
            }

            // Get processor and process
            EntityProcessor processor = processorRegistry.getProcessor(entityName);
            processor.process(childJobId, validatedData, userId,
                    "Continue on Error", processorUploadOption, null, null);

        } catch (Throwable e) {
            logger.error("Error processing entry {}: {}", entry.fileEntry, e.getMessage(), e);
            if (childJobId > 0) {
                try {
                    jobDAO.updateJobStatus(childJobId, "Failed", true);
                    jobDAO.updateJobProgress(childJobId, "Failed",
                            "Processing error: " + e.getMessage());
                    BulkUploadBroadcaster.getInstance().broadcast(childJobId, "Failed", 100,
                            "Processing failed: " + e.getMessage());
                } catch (Exception ex) {
                    logger.error("Error updating child job status", ex);
                }
            }
        }
    }

    private static boolean shouldUseMergeUpsert(String targetRef, String entityName, boolean mergeMode,
            boolean envMergeFacetInsertMode) {
        if (!mergeMode || targetRef == null || entityName == null) {
            return false;
        }
        if (envMergeFacetInsertMode) {
            return false;
        }
        if (MigrationTargetRefResolver.relationshipRegistryKeyOrNull(targetRef) != null) {
            return false;
        }
        if (entityName.contains("Role")) {
            return false;
        }
        String lower = entityName.toLowerCase();
        if (lower.contains("hierarchy")) {
            return false;
        }
        return true;
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
