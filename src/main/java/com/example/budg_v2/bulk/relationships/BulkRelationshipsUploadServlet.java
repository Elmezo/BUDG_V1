package com.example.budg_v2.bulk.relationships;

import com.example.budg_v2.bulk.relationships.config.EntityConfig;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import com.example.budg_v2.bulk.relationships.dto.BulkUploadResponse;
import com.example.budg_v2.bulk.relationships.dto.RelationshipRowData;
import com.example.budg_v2.bulk.relationships.dto.ValidationIssue;
import com.example.budg_v2.bulk.relationships.dto.CacheStats;
import com.example.budg_v2.bulk.relationships.dto.DuplicateCheckResult;
import com.example.budg_v2.bulk.relationships.dto.EntityResolutionResult;
import com.example.budg_v2.bulk.relationships.util.DuplicateChecker;
import com.example.budg_v2.bulk.relationships.util.ExistenceChecker;
import com.example.budg_v2.bulk.relationships.util.RelationshipExcelParser;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.LockDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.EntityResolverService;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.RelationshipTypeLookupService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ModuleResolver;
import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.objects.BulkUploadReportGenerator;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import com.google.gson.JsonObject;

/**
 * Main servlet for bulk relationship upload
 * Handles file upload, validation, entity resolution, and database inserts
 */
@WebServlet(urlPatterns = {"/api/bulk/relationships/upload"})
@MultipartConfig(
    maxFileSize = 52428800,      // 50MB
    maxRequestSize = 52428800,
    fileSizeThreshold = 1048576   // 1MB
)
public class BulkRelationshipsUploadServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(BulkRelationshipsUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final int BATCH_THRESHOLD = 100;

    private static String getBasePath() {
        return BulkPathUtil.getBulkPathForEntity("relationships");
    }

    private final EntityResolverService entityResolverService = new EntityResolverService();
    private final RelationshipTypeLookupService relationshipTypeLookupService = new RelationshipTypeLookupService();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();
    private final JobDAO jobDAO = new JobDAO();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final LockDAO lockDAO = new LockDAO();
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        long startTime = System.currentTimeMillis();
        
        // Declare variables outside try block for error handling
        Integer jobId = null;
        String entity = null;
        String uploadOption = null;
        int userId = 0;
        RelationshipConfig config = null;
        List<RelationshipRowData> parsedRowsForReport = null;
        Map<String, String> columnMappings = null;
        
        try {
            // Extract parameters
            entity = request.getParameter("entity");
            uploadOption = request.getParameter("uploadOption");
            String cancelOnWarningStr = request.getParameter("cancelOnWarning");
            String userIdStr = request.getParameter("userId");
            String columnMappingsStr = request.getParameter("columnMappings");
            
            boolean cancelOnWarning = "true".equalsIgnoreCase(cancelOnWarningStr);
            
            // Parse column mappings if provided
            if (columnMappingsStr != null && !columnMappingsStr.trim().isEmpty()) {
                try {
                    com.google.gson.JsonObject frontendMappings = gson.fromJson(columnMappingsStr, com.google.gson.JsonObject.class);
                    columnMappings = new HashMap<>();
                    for (String fieldName : frontendMappings.keySet()) {
                        com.google.gson.JsonElement element = frontendMappings.get(fieldName);
                        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
                            String excelColumn = element.getAsString();
                            if (excelColumn != null && !excelColumn.trim().isEmpty()) {
                                // Store mapping: Excel column name -> Field name
                                columnMappings.put(excelColumn, fieldName);
                            }
                        }
                    }
                    if (!columnMappings.isEmpty()) {
                        logger.info("Column mappings received: {} mappings", columnMappings.size());
                        logger.debug("Column mappings: {}", columnMappings);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse column mappings: {}", e.getMessage(), e);
                    logger.warn("Column mappings JSON string was: {}", columnMappingsStr);
                }
            }
            
            logger.info("Starting bulk upload: entity={}, operation={}, cancelOnWarning={}, userId={}", 
                entity, uploadOption, cancelOnWarning, userIdStr);
            
            // Validate parameters
            if (entity == null || uploadOption == null || userIdStr == null) {
                sendErrorResponse(response, "Missing required parameters", 400);
                return;
            }
            
            try {
                userId = Integer.parseInt(userIdStr);
            } catch (NumberFormatException e) {
                sendErrorResponse(response, "Invalid userId format", 400);
                return;
            }
            
            // Get relationship configuration
            config = RelationshipConfigRegistry.getConfig(entity);
            if (config == null) {
                sendErrorResponse(response, "Unsupported relationship: " + entity, 400);
                return;
            }
            
            // Check if operation is supported
            if (!config.supportsOperation(uploadOption)) {
                sendErrorResponse(response, 
                    String.format("Operation %s not supported for %s", uploadOption, config.getDisplayName()), 400);
                return;
            }
            
            // Get uploaded file
            Part filePart = request.getPart("file");
            if (filePart == null) {
                sendErrorResponse(response, "No file uploaded", 400);
                return;
            }
            
            // Validate file
            validateFile(filePart);
            
            // Get original filename
            String originalFileName = filePart.getSubmittedFileName();
            if (originalFileName == null || originalFileName.isEmpty()) {
                originalFileName = "relationship_upload.xlsx";
            }
            
            // Save file to disk
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String uuid = java.util.UUID.randomUUID().toString().substring(0, 6);
            String fileName = "relationship_" + timestamp + "_" + uuid + ".xlsx";
            
            String basePath = getBasePath();
            File directory = new File(basePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            File file = new File(basePath + fileName);
            String storagePath = basePath + fileName;
            
            // Save the file to disk
            filePart.write(file.getAbsolutePath());
            logger.info("File saved to: {}", storagePath);
            
            // Parse Excel file (pass uploadOption so DELETE uses looser row range and empty-relationship-type rows appear in report)
            List<RelationshipRowData> rows;
            try (InputStream inputStream = filePart.getInputStream()) {
                rows = RelationshipExcelParser.parseExcel(inputStream, config, columnMappings, uploadOption);
            }
            parsedRowsForReport = rows;
            
            logger.info("Parsed {} rows from Excel file", rows.size());
            
            if (rows.isEmpty()) {
                // Create job even for empty file so frontend gets a job ID and user can access report/jobs UI
                String jobType = determineJobType(config.getDisplayName(), uploadOption);
                String referenceName = generateShortReferenceName(config.getDisplayName());
                jobId = jobDAO.createJob(jobType, referenceName, 0, "Failed", userId);
                jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
                jobDAO.createJobProgress(jobId, 100, "Failed", "No data rows found in Excel file");
                BulkUploadResponse emptyResponse = new BulkUploadResponse();
                emptyResponse.setSuccess(false);
                emptyResponse.setStatus("failed");
                emptyResponse.setMessage("No data rows found in Excel file");
                emptyResponse.setJobId(jobId);
                emptyResponse.setReferenceName(referenceName);
                emptyResponse.setSkipped(0);
                emptyResponse.setFailed(0);
                emptyResponse.setReportAvailable(true);
                sendJsonResponse(response, emptyResponse);
                return;
            }
            
            // Create job record for tracking
            String jobType = determineJobType(config.getDisplayName(), uploadOption);
            String referenceName = generateShortReferenceName(config.getDisplayName());
            jobId = jobDAO.createJob(jobType, referenceName, rows.size(), "Processing", userId);
            logger.info("Job created with ID: {} and reference: {}", jobId, referenceName);
            
            // Save metadata JSON file
            try {
                String metadataFileName = fileName.replace(".xlsx", ".json");
                File metadataFile = new File(getBasePath() + metadataFileName);
                
                JsonObject metadata = new JsonObject();
                metadata.addProperty("job_id", jobId);
                metadata.addProperty("user_id", userId);
                metadata.addProperty("entity", config.getDisplayName());
                metadata.addProperty("upload_option", uploadOption);
                metadata.addProperty("error_handling", cancelOnWarning ? "cancel_on_warning" : "continue");
                metadata.addProperty("uploaded_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
                metadata.addProperty("reference_name", referenceName);
                metadata.addProperty("original_file_name", originalFileName);
                metadata.addProperty("storage_path", storagePath);
                
                Files.writeString(metadataFile.toPath(), gson.toJson(metadata));
                logger.info("Metadata JSON saved to: {}", metadataFile.getAbsolutePath());
            } catch (Exception metaEx) {
                logger.warn("Failed to save metadata JSON file: {}", metaEx.getMessage());
            }
            
            // Create Job Resource File record
            jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
            logger.info("Job resource file record created");
            
            // Create initial progress
            jobDAO.createJobProgress(jobId, 0, "Starting", "Bulk relationship upload started");
            
            // Process rows based on operation
            BulkUploadResponse uploadResponse;
            if ("INSERT".equals(uploadOption)) {
                uploadResponse = processInsert(rows, config, userId, cancelOnWarning, jobId, columnMappings);
            } else if ("DELETE".equals(uploadOption)) {
                // Delete uses same parsing and columnMappings as INSERT so mapping step in UI applies to delete too
                uploadResponse = processDelete(rows, config, userId, cancelOnWarning, jobId, columnMappings);
            } else if ("UPDATE".equals(uploadOption)) {
                // Special handling for Segment X Object UPDATE operation
                if ("segmentxobject".equals(config.getKey())) {
                    uploadResponse = processSegmentXObjectUpdate(rows, config, userId, cancelOnWarning, jobId, columnMappings);
                } else {
                    sendErrorResponse(response, "UPDATE operation not supported for " + config.getDisplayName(), 400);
                    return;
                }
            } else {
                sendErrorResponse(response, "Unsupported operation: " + uploadOption, 400);
                return;
            }
            
            // Generate and save report
            generateAndSaveRelationshipReport(jobId, uploadResponse, config, uploadOption, userId, columnMappings);
            
            // Ensure failed count is never 0 when there are errors or warnings (so GUI and BULK_COUNTS show correct count)
            int errorAndWarningCount = uploadResponse.getErrors().size() + uploadResponse.getWarnings().size();
            if (errorAndWarningCount > 0) {
                uploadResponse.setFailed(Math.max(uploadResponse.getFailed(), errorAndWarningCount));
            }
            
            // Update job status based on result (after correcting failed count so BULK_COUNTS is correct)
            updateJobStatus(jobId, uploadResponse, uploadOption);
            
            long endTime = System.currentTimeMillis();
            uploadResponse.setProcessingTimeMs(endTime - startTime);
            
            // Add job tracking information to response
            uploadResponse.setJobId(jobId);
            uploadResponse.setReferenceName(referenceName);
            uploadResponse.setUploadOption(uploadOption);
            uploadResponse.setStatus(uploadResponse.isSuccess() ? "success" : "failed");
            // Always make report available for relationship uploads
            uploadResponse.setReportAvailable(true);
            
            String operationVerb = "INSERT".equals(uploadOption) ? "inserted" : "deleted";
            int operationCount = "DELETE".equals(uploadOption) ? uploadResponse.getDeleted() : uploadResponse.getInserted();
            logger.info("Upload completed: success={}, {}={}, skipped={}, errors={}, warnings={}, time={}ms",
                uploadResponse.isSuccess(), operationVerb, operationCount, 
                uploadResponse.getSkipped(), uploadResponse.getErrors().size(),
                uploadResponse.getWarnings().size(), uploadResponse.getProcessingTimeMs());
            
            // Log errors for debugging
            if (!uploadResponse.getErrors().isEmpty()) {
                logger.warn("Response contains {} errors:", uploadResponse.getErrors().size());
                for (ValidationIssue error : uploadResponse.getErrors()) {
                    logger.warn("  Error: row={}, message={}", error.getRow(), error.getMessage());
                }
            }
            
            // Final safeguard: never show 0 failed when there are errors or warnings (GUI and status API)
            int finalErrorAndWarningCount = uploadResponse.getErrors().size() + uploadResponse.getWarnings().size();
            if (finalErrorAndWarningCount > 0 && uploadResponse.getFailed() < finalErrorAndWarningCount) {
                uploadResponse.setFailed(Math.max(uploadResponse.getFailed(), finalErrorAndWarningCount));
            }
            
            // DELETE: ensure response always has inserted=0 and deleted=count so GUI shows count in Deleted box, not Inserted
            if ("DELETE".equals(uploadOption)) {
                int delCount = uploadResponse.getDeleted();
                int insCount = uploadResponse.getInserted();
                if (delCount == 0 && insCount > 0) {
                    uploadResponse.setDeleted(insCount);
                }
                uploadResponse.setInserted(0);
            }
            
            sendJsonResponse(response, uploadResponse);
            
        } catch (Exception e) {
            logger.error("Error processing bulk upload", e);
            
            // If we have a jobId, create a proper response with it so frontend can show download button
            if (jobId != null && jobId.intValue() > 0) {
                try {
                    BulkUploadResponse errorResponse = new BulkUploadResponse();
                    errorResponse.setSuccess(false);
                    errorResponse.setStatus("failed");
                    errorResponse.setMessage("Internal server error: " + e.getMessage());
                    errorResponse.setJobId(jobId);
                    errorResponse.setTotalRows(0);
                    errorResponse.setInserted(0);
                    errorResponse.setDeleted(0);
                    errorResponse.setSkipped(0);
                    errorResponse.setFailed(1);
                    errorResponse.setReportAvailable(true); // Report available even on error
                    if (uploadOption != null) {
                        errorResponse.setUploadOption(uploadOption);
                    }
                    // Call updateJobStatus so BULK_COUNTS is written and status API returns correct failed count
                    if (config != null && uploadOption != null) {
                        updateJobStatus(jobId, errorResponse, uploadOption);
                    } else {
                        jobDAO.updateJobStatus(jobId, "Failed", false);
                        String errorMsg = "Upload failed: " + e.getMessage();
                        String messageWithCounts = errorMsg + "\nBULK_COUNTS:inserted=0,failed=1,updated=0,deleted=0";
                        jobDAO.createJobProgress(jobId, 100, "Failed", messageWithCounts);
                    }
                    // Try to generate a basic report with the error if we have config (include all parsed rows so report lists every row)
                    if (config != null && entity != null && uploadOption != null && userId > 0) {
                        try {
                            if (parsedRowsForReport != null && !parsedRowsForReport.isEmpty()) {
                                String uploadErrorMsg = "Upload failed: " + e.getMessage();
                                for (RelationshipRowData r : parsedRowsForReport) {
                                    r.addError(uploadErrorMsg);
                                }
                                errorResponse.setAllProcessedRows(parsedRowsForReport);
                            }
                            generateAndSaveRelationshipReport(jobId, errorResponse, config, uploadOption, userId, columnMappings);
                        } catch (Exception reportEx) {
                            logger.warn("Failed to generate error report: {}", reportEx.getMessage());
                        }
                    }
                    
                    sendJsonResponse(response, errorResponse);
                } catch (Exception responseEx) {
                    logger.error("Error creating error response with jobId", responseEx);
                    sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
                }
            } else {
                // No jobId available, send simple error response
                sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
            }
        }
    }
    
    /**
     * Process INSERT operation
     */
    private BulkUploadResponse processInsert(List<RelationshipRowData> rows, RelationshipConfig config,
                                            int userId, boolean cancelOnWarning, int jobId, Map<String, String> columnMappings) {
        
        BulkUploadResponse uploadResponse = new BulkUploadResponse();
        // Use max of row count and max row number so report range covers all Excel rows (e.g. rows 2,3,5,10 -> maxRow 10)
        int maxRowNumInsert = rows.isEmpty() ? 0 : rows.stream().mapToInt(RelationshipRowData::getRowNumber).max().orElse(rows.size());
        uploadResponse.setTotalRows(Math.max(rows.size(), maxRowNumInsert));
        
        // For report generation: collect all rows
        List<RelationshipRowData> allProcessedRows = new ArrayList<>();
        
        // Initialize caches
        Map<String, Integer> entityCache = new HashMap<>();
        Map<String, Integer> relationTypeCache = new HashMap<>();
        CacheStats cacheStats = new CacheStats();
        
        Connection conn = null;
        List<RelationshipRowData> skippedRows = null;
        List<RelationshipRowData> validRows = null;
        int validRowsCount = 0;
        int insertedCount = 0;
        
        try {
            conn = getConnectionWithRetry(3);
            
            // Transaction strategy: 
            // - If cancelOnWarning is true: use single transaction (all-or-nothing)
            // - If cancelOnWarning is false: commit each row individually (preserve successful rows)
            if (cancelOnWarning) {
                conn.setAutoCommit(false);
            } else {
                conn.setAutoCommit(true); // Auto-commit mode for per-row commits
            }
            
            validRows = new ArrayList<>();
            skippedRows = new ArrayList<>();
            
            // Track processed relationships within the same batch to prevent duplicates
            Set<String> processedRelationships = new HashSet<>();
            
            // Phase 1: Validate and resolve all rows
            logger.info("Phase 1: Validating {} rows...", rows.size());
            
            // Pre-populate all rows for report so report includes every row even when returning early
            allProcessedRows.addAll(rows);
            
            for (RelationshipRowData row : rows) {
                logger.debug("Row {}: Starting validation. Available columns: {}", 
                    row.getRowNumber(), String.join(", ", row.getOriginalValues().keySet()));
                
                // Pre-validate
                RelationshipExcelParser.preValidateRow(row, config, columnMappings);
                
                if (row.hasErrors()) {
                    logger.debug("Row {}: Pre-validation failed with {} errors: {}", 
                        row.getRowNumber(), row.getErrors().size(), 
                        String.join("; ", row.getErrors()));
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Early check: required relationship type empty (defensive, in case preValidate missed column)
                if (config.hasRelationType() && config.isRequiresRelationType()) {
                    String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                    if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()) {
                        row.addError("Relationship type is required but not provided.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Resolve entities
                logger.debug("Row {}: Resolving entities...", row.getRowNumber());
                boolean resolved = resolveEntitiesForRow(conn, row, config, entityCache, cacheStats, userId, columnMappings);
                
                if (!resolved || row.hasErrors()) {
                    logger.debug("Row {}: Entity resolution failed. Resolved: {}, Errors: {}", 
                        row.getRowNumber(), resolved, row.hasErrors() ? row.getErrors().size() : 0);
                    if (row.hasErrors()) {
                        logger.debug("Row {}: Entity resolution errors: {}", 
                            row.getRowNumber(), 
                            String.join("; ", row.getErrors()));
                    }
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Resolve relationship type
                if (config.hasRelationType()) {
                    resolveRelationshipTypeForRow(conn, row, config, relationTypeCache, cacheStats, columnMappings);
                    
                    // CRITICAL: Validate required relationship type before proceeding
                    if (row.getRelationshipTypeId() == null && config.isRequiresRelationType()) {
                        // Project X System: when relation type failed to resolve but entities are resolved, check if relationship already exists (any type) -> treat as duplicate
                        if ("project_x_system".equals(config.getTableName())) {
                            Map<String, Integer> ids = row.getResolvedEntityIds();
                            Integer entityAId = ids != null ? ids.get("entityA") : null;
                            Integer entityBId = ids != null ? ids.get("entityB") : null;
                            if (entityAId != null && entityBId != null && entityAId > 0 && entityBId > 0) {
                                boolean relationshipExists = false;
                                try {
                                    String existsSql = "SELECT ID FROM project_x_system WHERE projectid = ? AND systemid = ? LIMIT 1";
                                    try (PreparedStatement psExists = conn.prepareStatement(existsSql)) {
                                        psExists.setInt(1, entityAId);
                                        psExists.setInt(2, entityBId);
                                        try (ResultSet rsExists = psExists.executeQuery()) {
                                            if (rsExists.next()) relationshipExists = true;
                                        }
                                    }
                                } catch (SQLException e) {
                                    logger.warn("Row {}: Failed to check existing project_x_system relationship: {}", row.getRowNumber(), e.getMessage());
                                }
                                if (relationshipExists) {
                                    row.addWarning("Duplicate relationship - a relationship between this Project and System already exists; not inserted.");
                                    handleRowWarning(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                    continue;
                                }
                            }
                        }
                        String relationTypeTable = config.getRelationTypeTable();
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        String availableColumns = row.getOriginalValues() != null && !row.getOriginalValues().isEmpty()
                            ? String.join(", ", row.getOriginalValues().keySet()) : "";
                        String valuePart = (relationshipTypeValue != null && !relationshipTypeValue.trim().isEmpty())
                            ? String.format(" Value provided: '%s'.", relationshipTypeValue.trim())
                            : "";
                        String columnsPart = availableColumns.isEmpty() ? "" : String.format(" Available columns in row: %s.", availableColumns);
                        String errorMsg = String.format(
                            "Relationship type is required but not found or could not be resolved.%s " +
                            "Please verify that the 'Relationship Type' column in your Excel file contains a valid value that exists in the %s lookup table.%s",
                            valuePart, relationTypeTable != null ? relationTypeTable : "relationship type lookup table", columnsPart);
                        row.addError(errorMsg);
                        logger.error("Row {}: Required relationship type is null. Relationship type table: {}, value: {}", 
                            row.getRowNumber(), relationTypeTable, relationshipTypeValue);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Glossary X System: resolve optional "Strategic Data Set Name" to dataset ID and store on row
                if ("glossary_x_system".equals(config.getTableName())) {
                    try {
                        String strategicDataSetName = findFieldValue(row, "Strategic Data Set Name", columnMappings);
                        if (strategicDataSetName != null && !strategicDataSetName.trim().isEmpty()) {
                            Integer datasetId = resolveDatasetIdByName(conn, strategicDataSetName);
                            row.addResolvedEntityId("dataset", datasetId);
                            if (datasetId == null) {
                                row.addWarning("Strategic Data Set Name '" + strategicDataSetName.trim() + "' could not be resolved; relationship will be inserted with no dataset.");
                            }
                        } else {
                            row.addResolvedEntityId("dataset", null);
                        }
                    } catch (SQLException e) {
                        logger.warn("Row {}: Failed to resolve Strategic Data Set Name: {}", row.getRowNumber(), e.getMessage());
                        row.addResolvedEntityId("dataset", null);
                    }
                }
                
                // Check for duplicates (for glossary_x_system include dataset ID in uniqueness)
                Integer datasetIdForDupCheck = "glossary_x_system".equals(config.getTableName())
                    ? row.getResolvedEntityIds().get("dataset") : null;
                DuplicateCheckResult dupResult = DuplicateChecker.checkDuplicate(
                    conn, config.getTableName(),
                    config.getEntityAIdColumn(), config.getEntityBIdColumn(),
                    config.getRelationTypeColumn(),
                    row.getResolvedEntityIds(),
                    row.getRelationshipTypeId(),
                    row.getRowNumber(),
                    datasetIdForDupCheck
                );
                
                if (dupResult.isDuplicate()) {
                    String dupMsg = "project_x_process".equals(config.getTableName())
                        ? "A relationship between this Project and Process already exists. If you intended a different Process, ensure Process Ref. and Process Name refer to the same process."
                        : "Duplicate relationship - already exists; not inserted.";
                    if (!"project_x_process".equals(config.getTableName()) && dupResult.getMessage() != null && !dupResult.getMessage().isEmpty()) {
                        dupMsg = dupMsg + " " + dupResult.getMessage();
                    }
                    boolean treatDuplicateAsError = "process_x_dataset".equals(config.getTableName())
                        || "policy_x_system".equals(config.getTableName()) || "policy_x_process".equals(config.getTableName())
                        || "policy_x_glossary".equals(config.getTableName()) || "policy_x_dataset".equals(config.getTableName())
                        || "policy_x_attribute".equals(config.getTableName()) || "client_x_policy".equals(config.getTableName())
                        || "regulation_x_project".equals(config.getTableName()) || "regulator_x_geography".equals(config.getTableName())
                        || "client_x_glossary".equals(config.getTableName()) || "capability_x_process".equals(config.getTableName())
                        || "product_x_dataset".equals(config.getTableName()) || "project_x_dataset".equals(config.getTableName());
                    if (treatDuplicateAsError) {
                        row.addError(dupMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    } else {
                        row.addWarning(dupMsg);
                        handleRowWarning(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    }
                    continue;
                }
                
                // Check for duplicates within the same batch/file
                // Uniqueness constraint: (EntityA, EntityB, RelationshipType) must be unique
                Map<String, Integer> resolvedEntityIds = row.getResolvedEntityIds();
                if (resolvedEntityIds == null) {
                    row.addError("Resolved entity IDs map is null - cannot process relationship");
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Defensive null checks when retrieving entity IDs
                // CRITICAL: Always check for null after Map.get() to prevent NullPointerException when calling intValue()
                Integer entityAId = resolvedEntityIds.get("entityA");
                Integer entityBId = resolvedEntityIds.get("entityB");
                
                // Enhanced logging when Map.get() returns null to help diagnose issues
                EntityConfig entityAConfig = config.getEntityA();
                EntityConfig entityBConfig = config.getEntityB();
                
                if (entityAId == null) {
                    String expectedKeys = "Expected key: 'entityA'";
                    logger.warn("Row {}: Map.get('entityA') returned null. {} Available keys in resolvedEntityIds: {}. " +
                        "Entity A config: {} (required: {}). " +
                        "This may cause NullPointerException if intValue() is called on this null value.",
                        row.getRowNumber(), expectedKeys, resolvedEntityIds.keySet(), 
                        entityAConfig.getName(), entityAConfig.isRequired());
                }
                if (entityBId == null) {
                    String expectedKeys = "Expected key: 'entityB'";
                    logger.warn("Row {}: Map.get('entityB') returned null. {} Available keys in resolvedEntityIds: {}. " +
                        "Entity B config: {} (required: {}). " +
                        "This may cause NullPointerException if intValue() is called on this null value.",
                        row.getRowNumber(), expectedKeys, resolvedEntityIds.keySet(),
                        entityBConfig.getName(), entityBConfig.isRequired());
                }
                
                // Log warning if IDs are unexpectedly null (for debugging mapping issues)
                if (entityAId == null && entityBId == null) {
                    logger.error("Row {}: Both entity IDs are null after resolution. This may indicate a column mapping issue. " +
                        "Expected keys: ['entityA', 'entityB']. Available keys in resolvedEntityIds: {}. " +
                        "Entity A: {} (required: {}), Entity B: {} (required: {})", 
                        row.getRowNumber(), resolvedEntityIds.keySet(),
                        entityAConfig.getName(), entityAConfig.isRequired(),
                        entityBConfig.getName(), entityBConfig.isRequired());
                }
                Integer relationTypeId = row.getRelationshipTypeId();
                
                // Regulation X Project: require both Regulation and Project (safety net so null IDs never reach insert)
                if ("regulationxproject".equals(config.getKey()) || "regulation_x_project".equals(config.getTableName())) {
                    if (entityAId == null) {
                        row.addError("Regulation is required. Provide Regulation Ref. or Regulation Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null) {
                        row.addError("Project is required. Provide Project Ref. or Project Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Regulation X Product: require both Regulation and Product (safety net so null IDs never reach insert)
                if ("regulationxproduct".equals(config.getKey()) || "regulation_x_product".equals(config.getTableName())) {
                    if (entityAId == null) {
                        row.addError("Regulation is required. Provide Regulation Ref. or Regulation Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null) {
                        row.addError("Product is required. Provide Product Name (or Product Parent Name).");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Regulator X Geography: require Geography (Entity B) so missing Geography Name always fails and appears in report
                if ("regulatorxgeography".equals(config.getKey()) || "regulator_x_geography".equals(config.getTableName())) {
                    if (entityBId == null) {
                        row.addError("Geography is required. Provide Geography Name (or Parent Geography Name for disambiguation).");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Glossary X System / System X Glossary: require both Glossary and System to be resolved; show which entity failed when data was provided
                if ("glossaryxsystem".equals(config.getKey()) || "systemxglossary".equals(config.getKey()) || "glossary_x_system".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg;
                        if (entityAId == null && entityBId == null) {
                            errorMsg = "Glossary and System are required. Provide Glossary Ref. or Glossary Name, and System Short Name.";
                        } else if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                            errorMsg = "System could not be resolved. Check System Short Name and Parent System Short Name.";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                            errorMsg = "Glossary could not be resolved. Check Glossary Ref./Name and Parent Glossary Name.";
                        } else {
                            errorMsg = "Glossary and System are required. Provide Glossary Ref. or Glossary Name, and System Short Name.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Glossary X Client (client_x_glossary): require at least one glossary column and resolved glossary (no insert with glossary N/A)
                if ("glossaryxclient".equals(config.getKey()) || "client_x_glossary".equals(config.getTableName())) {
                    boolean hasGlossaryData = RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole());
                    if (!hasGlossaryData) {
                        row.addError("Glossary is required. Provide at least Glossary Ref. or Glossary Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null) {
                        row.addError("Glossary could not be resolved. Check Glossary Ref./Name and Parent Glossary Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Capability X Glossary: require both Capability and Glossary to be resolved (no insert with only one side or incompatible glossary)
                if ("capabilityxglossary".equals(config.getKey()) || "capability_x_glossary".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg;
                        if (entityAId == null && entityBId == null) {
                            errorMsg = "Capability X Glossary requires both Capability and Glossary to be resolved. " +
                                "Provide Capability Ref. or Name and Glossary Ref. or Name.";
                        } else if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                            errorMsg = "Capability was provided but could not be resolved. Check Capability Ref./Name and Capability Parent Name.";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                            errorMsg = "Glossary was provided but could not be resolved. Check Glossary Ref./Name and Parent Glossary Name.";
                        } else {
                            errorMsg = "Capability X Glossary requires both Capability and Glossary to be resolved. " +
                                "Provide Capability Ref. or Name and Glossary Ref. or Name.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Final validation: Ensure required entities are present before adding to validRows
                // Note: entityAConfig and entityBConfig are already declared above (lines 492-493)
                
                // Project X Data Set: both Project and Data Set must be resolved (no insert with project or dataset = N/A)
                if ("project_x_dataset".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Project X Data Set: Both Project and Data Set entities failed to resolve. " +
                            "Both Project and Data Set must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Project Name' or 'Project Ref.' and " +
                            "'Data Set Name' or 'Data Set Ref.' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Project was provided but could not be resolved. Check Project Ref./Name and Project Parent Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Data Set was provided but could not be resolved. Check Data Set Ref./Name and Data Set System Short Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Project X Data Set requires both Project and Data Set to be resolved. Missing or invalid Project/Data Set.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Process X Data Set: both Process and Data Set must be resolved (no insert with process or dataset = N/A)
                if ("process_x_dataset".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Process X Data Set: Both Process and Data Set entities failed to resolve. " +
                            "Both Process and Data Set must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Process Name' or 'Process Ref.' and " +
                            "'Data Set Name' or 'Data Set Ref.' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Process was provided but could not be resolved. Check Process Ref./Name and Parent Process Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Data Set was provided but could not be resolved. Check Data Set Ref./Name and Data Set System Short Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Process X Data Set requires both Process and Data Set to be resolved. Missing or invalid Process/Data Set.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Policy X Data Set: both Policy and Data Set must be resolved (no insert with policy or dataset = N/A)
                if ("policy_x_dataset".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Policy X Data Set: Both Policy and Data Set entities failed to resolve. " +
                            "Both Policy and Data Set must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Policy Name' or 'Policy Ref.' and " +
                            "'Data Set Name' or 'Data Set Ref.' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Policy was provided but could not be resolved. Check Policy Ref./Name and Parent Policy Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Data Set was provided but could not be resolved. Check Data Set Ref./Name and Data Set System Short Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Policy X Data Set requires both Policy and Data Set to be resolved. Missing or invalid Policy/Data Set.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // Ref matching: when Policy Ref or Data Set Ref is provided, verify it matches the resolved entity
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Policy Ref. value '%s' does not match the resolved Policy (Ref: %s). Use the correct Policy Ref. or leave it blank to resolve by name.",
                                refAVal, dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    if (refBVal != null && !refBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, entityBConfig, entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            String errorMsg = String.format(
                                "Data Set Ref. value '%s' does not match the resolved Data Set (Ref: %s). Use the correct Data Set Ref. or leave it blank to resolve by name.",
                                refBVal, dbRefB);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Data Set X Legal Entity (dataset_x_legal): both Data Set and Legal must be resolved (no insert with dataset or legal = N/A)
                if ("dataset_x_legal".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Data Set X Legal Entity: Both Data Set and Legal Entity failed to resolve. " +
                            "Both must be successfully resolved. Please check 'Data Set Ref.'/ 'Data Set Name' and 'Legal Entity Short Name' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Data Set was provided but could not be resolved. Check Data Set Ref./Name and Data Set System Short Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Legal Entity was provided but could not be resolved. Check Legal Entity Short Name and Parent Legal Entity Short Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Data Set X Legal Entity requires both Data Set and Legal Entity to be resolved. Missing or invalid Data Set/Legal Entity.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // System X Legal (system_x_legal): both System and Legal must be resolved; clear report reasons for missing system, missing legal, unresolved (incompatible parent name)
                if ("system_x_legal".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg;
                        if (entityAId == null && entityBId == null) {
                            errorMsg = "System and Legal Entity are required. Provide System Short Name (and Parent System Name if needed) and Legal Entity Short Name (and Parent Legal Entity Short Name if needed).";
                        } else if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                            errorMsg = "System could not be resolved. Check System Short Name and Parent System Name (incompatible parent name or system not found).";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                            errorMsg = "Legal Entity could not be resolved. Check Legal Entity Short Name and Parent Legal Entity Short Name (incompatible parent name or legal not found).";
                        } else if (entityAId == null) {
                            errorMsg = "System is required. Provide System Short Name (and Parent System Name if needed).";
                        } else {
                            errorMsg = "Legal Entity is required. Provide Legal Entity Short Name (and Parent Legal Entity Short Name if needed).";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Policy X Attribute: both Policy and Attribute must be resolved (no insert with policy or attribute = N/A)
                if ("policy_x_attribute".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Policy X Attribute: Both Policy and Attribute entities failed to resolve. " +
                            "Both Policy and Attribute must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Policy Name' or 'Policy Ref.' and " +
                            "'Attribute Name' or 'Attribute Ref.' (and optionally Attribute Data Set Name / Attribute System Short Name for disambiguation).";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Policy was provided but could not be resolved. Check Policy Ref./Name and Parent Policy Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Invalid attribute name: no attribute found with the given name/ref, or attribute info is missing. Check Attribute Ref./Name and optionally Attribute Data Set Name / Attribute System Short Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Policy X Attribute requires both Policy and Attribute to be resolved. Missing or invalid Policy/Attribute.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // When Attribute System Short Name or Attribute Data Set Name is provided, validate they match the resolved attribute's system/dataset
                    String rowSystemShortName = getRowValueByColumnPattern(row, "Attribute System", "System Short");
                    String rowDataSetName = getRowValueByColumnPattern(row, "Attribute Data Set", "Data Set Name");
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowDataSetName != null && !rowDataSetName.trim().isEmpty())) {
                        String[] attrSystemDataset = getAttributeDatasetAndSystemName(conn, entityBId);
                        String dbDatasetName = attrSystemDataset != null ? attrSystemDataset[0] : null;
                        String dbSystemName = attrSystemDataset != null ? attrSystemDataset[1] : null;
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                String errorMsg = "Attribute System Short Name was provided but the attribute is not linked to a system (or dataset has no system).";
                                row.addError(errorMsg);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                String errorMsg = String.format(
                                    "Attribute System Short Name '%s' does not match the attribute's system ('%s'). The attribute belongs to a different system.",
                                    rowSystemShortName.trim(), dbSystemName);
                                row.addError(errorMsg);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowDataSetName != null && !rowDataSetName.trim().isEmpty()) {
                            if (dbDatasetName == null || dbDatasetName.trim().isEmpty()) {
                                String errorMsg = "Attribute Data Set Name was provided but the attribute is not linked to a dataset.";
                                row.addError(errorMsg);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowDataSetName.trim().equalsIgnoreCase(dbDatasetName.trim())) {
                                String errorMsg = String.format(
                                    "Attribute Data Set Name '%s' does not match the attribute's dataset ('%s'). The attribute belongs to a different dataset.",
                                    rowDataSetName.trim(), dbDatasetName);
                                row.addError(errorMsg);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Project X Attribute: both Project and Attribute must be resolved when provided (no insert with project or attribute = N/A)
                if ("project_x_attribute".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Project X Attribute: Both Project and Attribute entities failed to resolve. " +
                            "Both Project and Attribute must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Project Name' or 'Project Ref.' and " +
                            "'Attribute Name' or 'Attribute Ref.' (and optionally Attribute Data Set Name / Attribute System Short Name for disambiguation).";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Project was provided but could not be resolved. Check Project Ref./Name and Parent Project Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Attribute was provided but could not be resolved. Check Attribute Ref./Name and optionally Attribute Data Set Name / Attribute System Short Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Project X Attribute requires both Project and Attribute to be resolved. Missing or invalid Project/Attribute.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // When Attribute System Short Name or Attribute Data Set Name is provided, validate they match the resolved attribute's system/dataset
                    String rowSystemShortName = getRowValueByColumnPattern(row, "Attribute System", "System Short");
                    String rowDataSetName = getRowValueByColumnPattern(row, "Attribute Data Set", "Data Set Name");
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowDataSetName != null && !rowDataSetName.trim().isEmpty())) {
                        String[] attrSystemDataset = getAttributeDatasetAndSystemName(conn, entityBId);
                        String dbDatasetName = attrSystemDataset != null ? attrSystemDataset[0] : null;
                        String dbSystemName = attrSystemDataset != null ? attrSystemDataset[1] : null;
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                String errorMsg = "Attribute System Short Name was provided but the attribute is not linked to a system (or dataset has no system).";
                                row.addError(errorMsg);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                String errorMsg = String.format(
                                    "Attribute System Short Name '%s' does not match the attribute's system ('%s'). The attribute belongs to a different system.",
                                    rowSystemShortName.trim(), dbSystemName);
                                row.addError(errorMsg);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowDataSetName != null && !rowDataSetName.trim().isEmpty()) {
                            if (dbDatasetName == null || dbDatasetName.trim().isEmpty()) {
                                String errorMsg = "Attribute Data Set Name was provided but the attribute is not linked to a dataset.";
                                row.addError(errorMsg);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowDataSetName.trim().equalsIgnoreCase(dbDatasetName.trim())) {
                                String errorMsg = String.format(
                                    "Attribute Data Set Name '%s' does not match the attribute's dataset ('%s'). The attribute belongs to a different dataset.",
                                    rowDataSetName.trim(), dbDatasetName);
                                row.addError(errorMsg);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Process X Glossary (glossary_x_process): both Glossary and Process must be resolved (no insert with process or glossary = N/A)
                if ("glossary_x_process".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Process X Glossary: Both Glossary and Process entities failed to resolve. " +
                            "Both Glossary and Process must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Glossary Name' or 'Glossary Ref.' and " +
                            "'Process Name' or 'Process Ref.' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Glossary was provided but could not be resolved. Check Glossary Ref./Name and Parent Glossary Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Process was provided but could not be resolved. Check Process Ref./Name and Parent Process Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Process X Glossary requires both Glossary and Process to be resolved. Missing or invalid Glossary/Process.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Process X System Interface (process_x_interface): both Process and Interface must be resolved (no insert with interface = N/A)
                if ("process_x_interface".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Process X System Interface: Both Process and Interface entities failed to resolve. " +
                            "Both Process and Interface must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Process Name' or 'Process Ref.' and " +
                            "'Interface Name' or 'Interface Ref.' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Process was provided but could not be resolved. Check Process Ref./Name and Parent Process Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Interface was provided but could not be resolved. Check Interface Ref./Name and Interface Source/Target System Short Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Process X System Interface requires both Process and Interface to be resolved. Missing or invalid Process/Interface.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Project X Process: both Project and Process must be resolved
                if ("project_x_process".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Project X Process: Both Project and Process entities failed to resolve. " +
                            "Both Project and Process must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Project Name' or 'Project Ref.' and " +
                            "'Process Name' or 'Process Ref.' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // When both Process Ref. and Process Name are provided, ensure they refer to the same process
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String nameColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "name", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                        EntityResolutionResult resultByRef = entityResolverService.resolveEntity(
                            conn, entityBConfig, refBVal.trim(), null, null, row.getRowNumber(), entityCache, cacheStats);
                        EntityResolutionResult resultByName = entityResolverService.resolveEntity(
                            conn, entityBConfig, null, nameBVal.trim(), null, row.getRowNumber(), entityCache, cacheStats);
                        Integer idByRef = resultByRef.isFound() ? resultByRef.getId() : null;
                        Integer idByName = resultByName.isFound() ? resultByName.getId() : null;
                        if (idByRef != null && idByName != null && !idByRef.equals(idByName)) {
                            row.addError("Process Ref. and Process Name refer to different processes. Please ensure both refer to the same process.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Process X Product (product_x_process): both Product and Process must be resolved; do not insert with process = N/A
                if ("product_x_process".equals(config.getTableName())) {
                    if (entityAId == null && entityBId == null) {
                        String errorMsg = "Process X Product: Both Product and Process entities failed to resolve. " +
                            "Both Product and Process must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Product Name' and 'Process Name' or 'Process Ref.' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Product was provided but could not be resolved. Check Product Name/Ref and Parent Product Name (e.g. parent must exist in the system).";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Process was provided but could not be resolved. Check Process Ref./Name and Parent Process Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Process X Product requires both Product and Process to be resolved. Missing or invalid Product/Process.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // When both Process Ref. and Process Name are provided, ensure they refer to the same process
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String nameColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "name", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                        EntityResolutionResult resultByRef = entityResolverService.resolveEntity(
                            conn, entityBConfig, refBVal.trim(), null, null, row.getRowNumber(), entityCache, cacheStats);
                        EntityResolutionResult resultByName = entityResolverService.resolveEntity(
                            conn, entityBConfig, null, nameBVal.trim(), null, row.getRowNumber(), entityCache, cacheStats);
                        Integer idByRef = resultByRef.isFound() ? resultByRef.getId() : null;
                        Integer idByName = resultByName.isFound() ? resultByName.getId() : null;
                        if (idByRef != null && idByName != null && !idByRef.equals(idByName)) {
                            String errorMsg = "Process Ref. and Process Name refer to different processes. Please ensure both refer to the same process.";
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Project X Capability: both Project and Capability must be resolved
                if ("project_x_capability".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Project X Capability: Both Project and Capability must be successfully resolved to create a relationship. " +
                            "Please check that the Excel file contains valid 'Project Name' or 'Project Ref.' and " +
                            "'Capability Name' or 'Capability Ref.' columns.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Policy X Project: both Policy and Project must be resolved
                if ("policy_x_project".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = entityAId == null && entityBId == null
                            ? "Policy X Project requires both Policy and Project to be identified. Missing Policy and Project info."
                            : entityAId == null
                                ? "Policy X Project requires both Policy and Project to be identified. Missing Policy info."
                                : "Policy X Project requires both Policy and Project to be identified. Missing Project info.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // When both Policy Ref and Project Ref are provided, verify they match the resolved entities
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && refBVal != null && !refBVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        String dbRefB = getEntityRefFromDb(conn, entityBConfig, entityBId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Policy Ref. value '%s' does not match the resolved Policy (Ref: %s). Use the correct Policy Ref. or leave it blank to resolve by name.",
                                refAVal, dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            String errorMsg = String.format(
                                "Project Ref. value '%s' does not match the resolved Project (Ref: %s). Use the correct Project Ref. or leave it blank to resolve by name.",
                                refBVal, dbRefB);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    // When both Project Ref and Project Name are provided, ensure they refer to the same project
                    String nameColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "name", columnMappings, config.getEntityBRole());
                    String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                        EntityResolutionResult resultByRef = entityResolverService.resolveEntity(
                            conn, entityBConfig, refBVal.trim(), null, null, row.getRowNumber(), entityCache, cacheStats);
                        EntityResolutionResult resultByName = entityResolverService.resolveEntity(
                            conn, entityBConfig, null, nameBVal.trim(), null, row.getRowNumber(), entityCache, cacheStats);
                        Integer idByRef = resultByRef.isFound() ? resultByRef.getId() : null;
                        Integer idByName = resultByName.isFound() ? resultByName.getId() : null;
                        if (idByRef != null && idByName != null && !idByRef.equals(idByName)) {
                            String errorMsg = "Project Ref. and Project Name refer to different projects. Please ensure both refer to the same project.";
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Policy X Process: when Policy Ref and/or Process Ref are provided, verify they match the resolved entities
                if ("policy_x_process".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Policy Ref. value '%s' does not match the resolved Policy (Ref: %s). Use the correct Policy Ref. or leave it blank to resolve by name.",
                                refAVal, dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    if (refBVal != null && !refBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, entityBConfig, entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            String errorMsg = String.format(
                                "Process Ref. value '%s' does not match the resolved Process (Ref: %s). Use the correct Process Ref. or leave it blank to resolve by name.",
                                refBVal, dbRefB);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Process X Attribute: when both Process Ref and Process Name are provided, ensure they refer to the same process; same for Attribute
                if ("process_x_attribute".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        EntityResolutionResult resultByRefA = entityResolverService.resolveEntity(
                            conn, entityAConfig, refAVal.trim(), null, null, row.getRowNumber(), entityCache, cacheStats);
                        EntityResolutionResult resultByNameA = entityResolverService.resolveEntity(
                            conn, entityAConfig, null, nameAVal.trim(), null, row.getRowNumber(), entityCache, cacheStats);
                        Integer idByRefA = resultByRefA.isFound() ? resultByRefA.getId() : null;
                        Integer idByNameA = resultByNameA.isFound() ? resultByNameA.getId() : null;
                        if (idByRefA != null && idByNameA != null && !idByRefA.equals(idByNameA)) {
                            row.addError("Process Ref. and Process Name refer to different processes. Please ensure both refer to the same process.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String nameColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "name", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                        EntityResolutionResult resultByRefB = entityResolverService.resolveEntity(
                            conn, entityBConfig, refBVal.trim(), null, null, row.getRowNumber(), entityCache, cacheStats);
                        EntityResolutionResult resultByNameB = entityResolverService.resolveEntity(
                            conn, entityBConfig, null, nameBVal.trim(), null, row.getRowNumber(), entityCache, cacheStats);
                        Integer idByRefB = resultByRefB.isFound() ? resultByRefB.getId() : null;
                        Integer idByNameB = resultByNameB.isFound() ? resultByNameB.getId() : null;
                        if (idByRefB != null && idByNameB != null && !idByRefB.equals(idByNameB)) {
                            row.addError("Attribute Ref. and Attribute Name refer to different attributes. Please ensure both refer to the same attribute.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Project X Attribute: when both Attribute Ref and Attribute Name provided, ensure they refer to the same attribute (reject incompatible ref)
                if ("project_x_attribute".equals(config.getTableName()) && entityAId != null && entityBId != null && entityBConfig.hasRefColumn()) {
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String nameColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "name", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                        EntityResolutionResult resultByRefB = entityResolverService.resolveEntity(
                            conn, entityBConfig, refBVal.trim(), null, null, row.getRowNumber(), entityCache, cacheStats);
                        EntityResolutionResult resultByNameB = entityResolverService.resolveEntity(
                            conn, entityBConfig, null, nameBVal.trim(), null, row.getRowNumber(), entityCache, cacheStats);
                        Integer idByRefB = resultByRefB.isFound() ? resultByRefB.getId() : null;
                        Integer idByNameB = resultByNameB.isFound() ? resultByNameB.getId() : null;
                        if (idByRefB != null && idByNameB != null && !idByRefB.equals(idByNameB)) {
                            row.addError("Attribute Ref. and Attribute Name refer to different attributes. Use a ref and name that identify the same attribute, or leave one blank to resolve by the other.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Specific handling for Product_X_Legal: Product (Entity A) is optional, Legal (Entity B) is required
                if ("product_x_legal".equals(config.getTableName())) {
                    if (entityBId == null) {
                        logger.error("Row {}: Product_X_Legal - Legal Entity (Entity B) ID is null but is required. " +
                            "This should have been caught during entity resolution. Available keys: {}", 
                            row.getRowNumber(), resolvedEntityIds.keySet());
                    }
                    if (entityAId == null) {
                        logger.debug("Row {}: Product_X_Legal - Product (Entity A) ID is null, which is acceptable as it's optional.", 
                            row.getRowNumber());
                    }
                    // When product info was provided in the row, require it to resolve; do not insert with product = N/A
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Product was provided but could not be resolved. Check Product Name/Ref and Parent Product Name (e.g. parent must exist in the system).";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                // Product_X_BusinessArea: do not insert with product = N/A when row had product data
                if ("product_x_businessarea".equals(config.getTableName())) {
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Product was provided but could not be resolved. Check Product Name/Ref and Parent Product Name (e.g. parent must exist in the system).";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                // Product X Business Area: when both Product Ref and Product Name provided, verify they match the resolved product (reject incompatible ref)
                if ("product_x_businessarea".equals(config.getTableName()) && entityAId != null && entityAConfig.hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Product Ref. value '%s' does not match the resolved Product (Ref: %s). Use the correct Product Ref. or leave it blank to resolve by name.",
                                refAVal.trim(), dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Product X Client: when both Product Ref and Product Name provided, verify they match the resolved product (reject incompatible ref)
                if ("product_x_client".equals(config.getTableName()) && entityAId != null && entityAConfig.hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Product Ref. value '%s' does not match the resolved Product (Ref: %s). Use the correct Product Ref. or leave it blank to resolve by name.",
                                refAVal.trim(), dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Specific handling for Product_X_Client: Product (Entity A) is optional, Client (Entity B) is required
                // When product info was provided in the row, require it to resolve; do not insert with product = N/A
                if ("product_x_client".equals(config.getTableName())) {
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Product was provided but could not be resolved. Check Product Name/Ref and Parent Product Name (e.g. parent must exist in the system).";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Policy X System: do not insert with policy = N/A; policy is required
                if ("policy_x_system".equals(config.getTableName()) && entityAId == null) {
                    String errorMsg = "Policy is required. Provide Policy Ref. or Policy Name.";
                    row.addError(errorMsg);
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Policy X Business Area: do not insert with policy = N/A; policy is required
                if ("policy_x_businessarea".equals(config.getTableName()) && entityAId == null) {
                    String errorMsg = "Policy is required for Policy X Business Area. Provide Policy Ref. or Policy Name.";
                    row.addError(errorMsg);
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Process X Client (client_x_process): both Client and Process required; do not insert with process = N/A
                if ("client_x_process".equals(config.getTableName())) {
                    if (entityAId == null) {
                        row.addError("Client is required. Provide Client Name (or Client Parent Name).");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null) {
                        row.addError("Process is required. Provide Process Ref. or Process Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Policy X Glossary: both Policy and Glossary required; do not insert with N/A
                if ("policy_x_glossary".equals(config.getTableName())) {
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Policy was provided but could not be resolved. Check Policy Ref./Name and Parent Policy Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Glossary was provided but could not be resolved. Check Glossary Ref./Name and Parent Glossary Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Policy X Glossary requires both Policy and Glossary to be resolved. Missing or invalid Policy/Glossary.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // Ref matching: when Policy Ref or Glossary Ref is provided, verify it matches the resolved entity
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Policy Ref. value '%s' does not match the resolved Policy (Ref: %s). Use the correct Policy Ref. or leave it blank to resolve by name.",
                                refAVal, dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    if (refBVal != null && !refBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, entityBConfig, entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            String errorMsg = String.format(
                                "Glossary Ref. value '%s' does not match the resolved Glossary (Ref: %s). Use the correct Glossary Ref. or leave it blank to resolve by name.",
                                refBVal, dbRefB);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Glossary X Product (product_x_glossary): both Product and Glossary required; do not insert with N/A
                if ("product_x_glossary".equals(config.getTableName())) {
                    if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                        String errorMsg = "Product was provided but could not be resolved. Check Product Name and Product Parent Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                        String errorMsg = "Glossary was provided but could not be resolved. Check Glossary Ref./Name and Parent Glossary Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Glossary X Product requires both Product and Glossary to be resolved. Missing or invalid Product/Glossary.";
                        logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // Ref matching: when Glossary Ref (Entity B) is provided, verify it matches the resolved entity
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, entityBConfig, entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            String errorMsg = String.format(
                                "Glossary Ref. value '%s' does not match the resolved Glossary (Ref: %s). Use the correct Glossary Ref. or leave it blank to resolve by name.",
                                refBVal, dbRefB);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Glossary X System: when Glossary Ref is provided, verify it matches the resolved glossary (Entity A block - System has no ref so this does not run for glossary_x_system)
                if ("glossary_x_system".equals(config.getTableName()) && entityAId != null && entityAConfig.hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Glossary Ref. value '%s' does not match the resolved Glossary (Ref: %s). Use the correct Glossary Ref. or leave it blank to resolve by name.",
                                refAVal, dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Glossary X System: when Glossary Ref (Entity B) is provided, verify it matches the resolved Glossary
                if ("glossary_x_system".equals(config.getTableName()) && entityBId != null && entityBConfig.hasRefColumn()) {
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, entityBConfig, entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            String errorMsg = String.format(
                                "Glossary Ref. value '%s' does not match the resolved Glossary (Ref: %s). Use the correct Glossary Ref. or leave it blank to resolve by name.",
                                refBVal, dbRefB);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Committee X Capability: require both Committee and Capability to be resolved (no insert with N/A)
                if ("committee_x_capability".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Committee X Capability requires both Committee and Capability to be resolved. " +
                            "Provide Committee Ref. or Committee Name and Capability Ref. or Capability Name.";
                        if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                            errorMsg = "Committee was provided but could not be resolved. Check Committee Ref./Name and Committee Parent Name.";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                            errorMsg = "Capability was provided but could not be resolved. Check Capability Ref./Name and Capability Parent Name.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Committee X Capability: when Committee Ref. or Capability Ref. is provided, verify it matches the resolved entity (reject incompatible refs)
                if ("committee_x_capability".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && entityAConfig.hasRefColumn()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Committee Ref. value '%s' does not match the resolved Committee (Ref: %s). Use the correct Committee Ref. or leave it blank to resolve by name.",
                                refAVal, dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    if (refBVal != null && !refBVal.trim().isEmpty() && entityBConfig.hasRefColumn()) {
                        String dbRefB = getEntityRefFromDb(conn, entityBConfig, entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            String errorMsg = String.format(
                                "Capability Ref. value '%s' does not match the resolved Capability (Ref: %s). Use the correct Capability Ref. or leave it blank to resolve by name.",
                                refBVal, dbRefB);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Committee X Committee: require both Source Committee and Target Committee to be resolved (no insert with committee = N/A)
                if ("committee_x_committee".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Committee X Committee requires both Source Committee and Target Committee to be resolved. " +
                            "Provide Source Committee Ref. or Name and Target Committee Ref. or Name.";
                        if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                            errorMsg = "Source Committee was provided but could not be resolved. Check Source Committee Ref./Name and Parent Name.";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                            errorMsg = "Target Committee was provided but could not be resolved. Check Target Committee Ref./Name and Parent Name.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Capability X System: require both Capability and System to be resolved (no insert with N/A)
                if ("capability_x_system".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Capability X System requires both Capability and System to be resolved. " +
                            "Provide Capability Ref. or Capability Name and System Short Name.";
                        if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                            errorMsg = "Capability was provided but could not be resolved. Check Capability Ref./Name and Capability Parent Name.";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                            errorMsg = "System was provided but could not be resolved. Check System Short Name and Parent System Short Name.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Capability X Legal Entity: require both Capability and Legal Entity to be resolved (no insert with N/A)
                if ("capability_x_legal".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Capability and Legal Entity are both required; one or both were not resolved. " +
                            "Provide Capability Ref. or Capability Name and Legal Entity Short Name.";
                        if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                            errorMsg = "Capability was provided but could not be resolved. Check Capability Ref./Name and Capability Parent Name.";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                            errorMsg = "Legal Entity was provided but could not be resolved. Check Legal Entity Short Name.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Capability X System: when Capability Ref. is provided, verify it matches the resolved entity (reject incompatible refs)
                if ("capability_x_system".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && entityAConfig.hasRefColumn()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Capability Ref. value '%s' does not match the resolved Capability (Ref: %s). Use the correct Capability Ref. or leave it blank to resolve by name.",
                                refAVal, dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Capability X Client: when Capability Ref. is provided, verify it matches the resolved entity (reject incompatible refs)
                if ("capability_x_client".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && entityAConfig.hasRefColumn()) {
                        String dbRefA = getEntityRefFromDb(conn, entityAConfig, entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            String errorMsg = String.format(
                                "Capability Ref. value '%s' does not match the resolved Capability (Ref: %s). Use the correct Capability Ref. or leave it blank to resolve by name.",
                                refAVal, dbRefA);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Capability X Process: require both Capability and Process to be resolved (no insert with N/A)
                if ("capability_x_process".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Capability X Process requires both Capability and Process to be resolved. " +
                            "Provide Capability Ref. or Capability Name and Process Ref. or Process Name.";
                        if (entityAId == null && RelationshipExcelParser.hasEntityData(row, entityAConfig, columnMappings, config.getEntityARole())) {
                            errorMsg = "Capability was provided but could not be resolved. Check Capability Ref./Name and Capability Parent Name.";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, entityBConfig, columnMappings, config.getEntityBRole())) {
                            errorMsg = "Process was provided but could not be resolved. Check Process Ref./Name and Parent Process Name.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Capability X Process: when Process Ref. is provided, verify it matches the resolved entity (reject incompatible refs)
                if ("capability_x_process".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && entityBConfig.hasRefColumn()) {
                        String dbRefB = getEntityRefFromDb(conn, entityBConfig, entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            String errorMsg = String.format(
                                "Process Ref. value '%s' does not match the resolved Process (Ref: %s). Use the correct Process Ref. or leave it blank to resolve by name.",
                                refBVal, dbRefB);
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                if (entityAConfig.isRequired() && entityAId == null) {
                    row.addError(String.format("Required Entity A (%s) ID is null - cannot insert relationship", 
                        entityAConfig.getName()));
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                if (entityBConfig.isRequired() && entityBId == null) {
                    row.addError(String.format("Required Entity B (%s) ID is null - cannot insert relationship", 
                        entityBConfig.getName()));
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Data Set X Client: validate Data Set System Short Name and Data Set Parent System Short Name match the dataset's system (and parent)
                if ("client_x_dataset".equals(config.getTableName()) && entityBId != null) {
                    String rowSystemShortName = findFieldValue(row, "Data Set System Short Name", columnMappings);
                    String rowParentSystemShortName = findFieldValue(row, "Data Set Parent System Short Name", columnMappings);
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty())) {
                        String[] dsSystemParent = getDatasetSystemAndParentSystemName(conn, entityBId);
                        if (dsSystemParent == null) {
                            row.addError("Could not determine the dataset's system. Please verify the dataset exists and is linked to a system.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                        String dbSystemName = dsSystemParent[0];
                        String dbParentSystemName = dsSystemParent[1];
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Data Set System Short Name was provided but the dataset is not linked to a system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set System Short Name '%s' does not match the dataset's system ('%s'). Everything entered must be compatible.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty()) {
                            if (dbParentSystemName == null || dbParentSystemName.trim().isEmpty()) {
                                row.addError("Data Set Parent System Short Name was provided but the dataset's system has no parent system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowParentSystemShortName.trim().equalsIgnoreCase(dbParentSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set Parent System Short Name '%s' does not match the dataset's system parent ('%s'). Everything entered must be compatible.",
                                    rowParentSystemShortName.trim(), dbParentSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                // Data Set X Product: validate Data Set System Short Name and Data Set Parent System Short Name match the dataset's system (and parent)
                else if ("product_x_dataset".equals(config.getTableName()) && entityBId != null) {
                    String rowSystemShortName = findFieldValue(row, "Data Set System Short Name", columnMappings);
                    String rowParentSystemShortName = findFieldValue(row, "Data Set Parent System Short Name", columnMappings);
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty())) {
                        String[] dsSystemParent = getDatasetSystemAndParentSystemName(conn, entityBId);
                        if (dsSystemParent == null) {
                            row.addError("Could not determine the dataset's system. Please verify the dataset exists and is linked to a system.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                        String dbSystemName = dsSystemParent[0];
                        String dbParentSystemName = dsSystemParent[1];
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Data Set System Short Name was provided but the dataset is not linked to a system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set System Short Name '%s' does not match the dataset's system ('%s'). Everything entered must be compatible.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty()) {
                            if (dbParentSystemName == null || dbParentSystemName.trim().isEmpty()) {
                                row.addError("Data Set Parent System Short Name was provided but the dataset's system has no parent system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowParentSystemShortName.trim().equalsIgnoreCase(dbParentSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set Parent System Short Name '%s' does not match the dataset's system parent ('%s'). Everything entered must be compatible.",
                                    rowParentSystemShortName.trim(), dbParentSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                // Policy X Data Set (insert): validate Data Set System Short Name and Data Set Parent System Short Name match the dataset's system (prevent inserting when incompatible)
                else if ("policy_x_dataset".equals(config.getTableName()) && entityBId != null) {
                    String rowSystemShortName = findFieldValue(row, "Data Set System Short Name", columnMappings);
                    String rowParentSystemShortName = findFieldValue(row, "Data Set Parent System Short Name", columnMappings);
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty())) {
                        String[] dsSystemParent = getDatasetSystemAndParentSystemName(conn, entityBId);
                        if (dsSystemParent == null) {
                            row.addError("Could not determine the dataset's system. Please verify the dataset exists and is linked to a system.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                        String dbSystemName = dsSystemParent[0];
                        String dbParentSystemName = dsSystemParent[1];
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Data Set System Short Name was provided but the dataset is not linked to a system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set System Short Name '%s' does not match the dataset's system ('%s'). Everything entered must be compatible.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty()) {
                            if (dbParentSystemName == null || dbParentSystemName.trim().isEmpty()) {
                                row.addError("Data Set Parent System Short Name was provided but the dataset's system has no parent system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowParentSystemShortName.trim().equalsIgnoreCase(dbParentSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set Parent System Short Name '%s' does not match the dataset's system parent ('%s'). Everything entered must be compatible.",
                                    rowParentSystemShortName.trim(), dbParentSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                // Project X Data Set (insert): validate Data Set System Short Name and Data Set Parent System Short Name match the dataset's system (prevent inserting when incompatible)
                else if ("project_x_dataset".equals(config.getTableName()) && entityBId != null) {
                    String rowSystemShortName = findFieldValue(row, "Data Set System Short Name", columnMappings);
                    String rowParentSystemShortName = findFieldValue(row, "Data Set Parent System Short Name", columnMappings);
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty())) {
                        String[] dsSystemParent = getDatasetSystemAndParentSystemName(conn, entityBId);
                        if (dsSystemParent == null) {
                            row.addError("Could not determine the dataset's system. Please verify the dataset exists and is linked to a system.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                        String dbSystemName = dsSystemParent[0];
                        String dbParentSystemName = dsSystemParent[1];
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Data Set System Short Name was provided but the dataset is not linked to a system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set System Short Name '%s' does not match the dataset's system ('%s'). Everything entered must be compatible.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty()) {
                            if (dbParentSystemName == null || dbParentSystemName.trim().isEmpty()) {
                                row.addError("Data Set Parent System Short Name was provided but the dataset's system has no parent system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowParentSystemShortName.trim().equalsIgnoreCase(dbParentSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set Parent System Short Name '%s' does not match the dataset's system parent ('%s'). Everything entered must be compatible.",
                                    rowParentSystemShortName.trim(), dbParentSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Safety check: at least one entity must be present
                // This is critical because a relationship cannot exist without at least one entity
                // Even if both entities are marked as optional, at least one must resolve successfully
                if (entityAId == null && entityBId == null) {
                    String entityAName = entityAConfig.getName();
                    String entityBName = entityBConfig.getName();
                    String errorMsg = String.format(
                        "Both %s (Entity A) and %s (Entity B) failed to resolve. " +
                        "At least one entity must be successfully resolved to create a relationship. " +
                        "Please check that the Excel file contains valid columns for at least one entity " +
                        "(e.g., '%s Name' or '%s Ref.' for %s, or '%s Name' or '%s Ref.' for %s). " +
                        "Available keys in resolvedEntityIds: %s",
                        entityAName, entityBName, entityAName, entityAName, entityAName,
                        entityBName, entityBName, entityBName, resolvedEntityIds.keySet());
                    if ("glossary_x_glossary".equals(config.getTableName())) {
                        errorMsg += " Ensure the file has columns such as 'Source Glossary Ref.' or 'Source Glossary Name' for the source glossary, and 'Target Glossary Ref.' or 'Target Glossary Name' for the target glossary.";
                    }
                    if ("attribute_x_attribute".equals(config.getTableName())) {
                        errorMsg += " Ensure the file has columns such as 'Source Attribute Ref.' or 'Source Attribute Name' for the source attribute, and 'Target Attribute Ref.' or 'Target Attribute Name' for the target attribute.";
                    }
                    logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                    row.addError(errorMsg);
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Glossary X Glossary: explicit early rejection of self-relationship with clear message
                if ("glossary_x_glossary".equals(config.getTableName()) && entityAId != null && entityBId != null && entityAId.equals(entityBId)) {
                    String errorMsg = "Source and target glossary are the same. A relationship cannot be created between a glossary and itself.";
                    row.addError(errorMsg);
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Prevent relationship between an object and itself (same-facet types: glossary X glossary, policy X policy, etc.)
                if (entityAId != null && entityBId != null
                    && config.getEntityA().getTableName().equals(config.getEntityB().getTableName())
                    && entityAId.equals(entityBId)) {
                    String errorMsg = "Cannot create a relationship between an object and itself. Source and target must be different objects.";
                    row.addError(errorMsg);
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Attribute X Attribute: when interface is specified, source and target attributes' systems must match the interface's source and target systems
                if ("attribute_x_attribute".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    try {
                        Integer interfaceId = resolveInterfaceIdFromRow(conn, row, columnMappings);
                        if (interfaceId != null) {
                            int[] ifaceSystems = getInterfaceSourceAndTargetSystemIds(conn, interfaceId);
                            if (ifaceSystems != null && (ifaceSystems[0] != 0 || ifaceSystems[1] != 0)) {
                                Integer sourceAttrSys = getAttributeSystemId(conn, entityAId);
                                Integer targetAttrSys = getAttributeSystemId(conn, entityBId);
                                if (ifaceSystems[0] != 0 && (sourceAttrSys == null || sourceAttrSys != ifaceSystems[0])) {
                                    row.addError("The specified interface's source system must match the source attribute's system. The source attribute must belong to a dataset in the interface's source system.");
                                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                    continue;
                                }
                                if (ifaceSystems[1] != 0 && (targetAttrSys == null || targetAttrSys != ifaceSystems[1])) {
                                    row.addError("The specified interface's target system must match the target attribute's system. The target attribute must belong to a dataset in the interface's target system.");
                                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                    continue;
                                }
                                // If Interface Source/Target System Short Name columns are provided, they must match the interface's source/target
                                String ifaceSourceName = findFieldValue(row, "Interface Source System Short Name", columnMappings);
                                String ifaceTargetName = findFieldValue(row, "Interface Target System Short Name", columnMappings);
                                if (ifaceSourceName != null && !ifaceSourceName.trim().isEmpty() && ifaceSystems[0] != 0) {
                                    Integer resolvedSysId = resolveSystemIdByName(conn, ifaceSourceName.trim());
                                    if (resolvedSysId == null || resolvedSysId != ifaceSystems[0]) {
                                        row.addError("Interface source system short name must match the specified interface's source system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                }
                                if (ifaceTargetName != null && !ifaceTargetName.trim().isEmpty() && ifaceSystems[1] != 0) {
                                    Integer resolvedSysId = resolveSystemIdByName(conn, ifaceTargetName.trim());
                                    if (resolvedSysId == null || resolvedSysId != ifaceSystems[1]) {
                                        row.addError("Interface target system short name must match the specified interface's target system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                }
                            }
                        }
                    } catch (SQLException e) {
                        logger.error("Row {}: Error validating attribute_x_attribute interface compatibility: {}", row.getRowNumber(), e.getMessage(), e);
                        row.addError("Could not validate interface and source/target compatibility: " + e.getMessage());
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Process X System Interface: row's Interface Source/Target System Short Name must match the resolved interface's source/target
                if ("process_x_interface".equals(config.getTableName()) && entityBId != null) {
                    try {
                        int[] ifaceSystems = getInterfaceSourceAndTargetSystemIds(conn, entityBId);
                        if (ifaceSystems != null && (ifaceSystems[0] != 0 || ifaceSystems[1] != 0)) {
                            String ifaceSourceName = findFieldValue(row, "Interface Source System Short Name", columnMappings);
                            String ifaceTargetName = findFieldValue(row, "Interface Target System Short Name", columnMappings);
                            if (ifaceSourceName != null && !ifaceSourceName.trim().isEmpty() && ifaceSystems[0] != 0) {
                                Integer resolvedSysId = resolveSystemIdByName(conn, ifaceSourceName.trim());
                                if (resolvedSysId == null || resolvedSysId != ifaceSystems[0]) {
                                    row.addError("Interface source system short name must match the specified interface's source system.");
                                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                    continue;
                                }
                            }
                            if (ifaceTargetName != null && !ifaceTargetName.trim().isEmpty() && ifaceSystems[1] != 0) {
                                Integer resolvedSysId = resolveSystemIdByName(conn, ifaceTargetName.trim());
                                if (resolvedSysId == null || resolvedSysId != ifaceSystems[1]) {
                                    row.addError("Interface target system short name must match the specified interface's target system.");
                                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                    continue;
                                }
                            }
                        }
                    } catch (SQLException e) {
                        logger.error("Row {}: Error validating process_x_interface source/target: {}", row.getRowNumber(), e.getMessage(), e);
                        row.addError("Could not validate interface source/target: " + e.getMessage());
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Prevent relationships between objects in different private segments
                if (entityAId != null && entityBId != null) {
                    try {
                        String sourceObjectType = entityNameToSegmentObjectType(entityAConfig.getName());
                        String targetObjectType = entityNameToSegmentObjectType(entityBConfig.getName());
                        SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                            entityAId.intValue(), sourceObjectType, entityBId.intValue(), targetObjectType, conn);
                        if (!segmentResult.isValid) {
                            row.addError(segmentResult.message);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    } catch (SQLException e) {
                        logger.error("Row {}: Error validating cross-segment relationship: {}", row.getRowNumber(), e.getMessage(), e);
                        row.addError("Could not validate segment access: " + e.getMessage());
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Policy X Process: explicit cross-segment validation so Policy and Process from different private segments are never inserted
                if ("policy_x_process".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    try {
                        String sourceObjectType = entityNameToSegmentObjectType(entityAConfig.getName());
                        String targetObjectType = entityNameToSegmentObjectType(entityBConfig.getName());
                        SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                            entityAId.intValue(), sourceObjectType, entityBId.intValue(), targetObjectType, conn);
                        if (!segmentResult.isValid) {
                            row.addError(segmentResult.message);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    } catch (SQLException e) {
                        logger.error("Row {}: Error validating policy_x_process cross-segment: {}", row.getRowNumber(), e.getMessage(), e);
                        row.addError("Could not validate segment access: " + e.getMessage());
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Regulation X Policy: require both Regulation and Policy to be resolved (no partial mapping)
                if ("regulation_x_policy".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    String errorMsg = "Both Regulation and Policy must be resolved. " +
                        "Provide valid Regulation Ref./Name and Policy Ref./Name that exist in the system.";
                    row.addError(errorMsg);
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Regulation X Regulatory Theme: require both Regulation and Regulatory Theme (empty regulation/theme -> clear report message)
                if ("regulation_x_regulatorytheme".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    if (entityAId == null) {
                        row.addError("Regulation is required but not provided.");
                    }
                    if (entityBId == null) {
                        row.addError("Regulatory Theme is required but not provided.");
                    }
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Regulation X Project: when both Regulation Ref and Name (or both Project Ref and Name) provided, verify they match resolved entity (incompatible ref)
                if ("regulation_x_project".equals(config.getTableName()) && entityAId != null && entityBId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError("Incompatible ref: Regulation Ref. does not match the resolved Regulation.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "ref", columnMappings, config.getEntityBRole());
                    String nameColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "name", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, config.getEntityB(), entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            row.addError("Incompatible ref: Project Ref. does not match the resolved Project.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Regulation X Regulatory Theme: when both Regulation Ref and Name (or both Regulatory Theme Ref and Name) provided, verify they match resolved entity (incompatible ref)
                if ("regulation_x_regulatorytheme".equals(config.getTableName()) && entityAId != null && entityBId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError("Incompatible ref: Regulation Ref. does not match the resolved Regulation.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    if (config.getEntityB().hasRefColumn()) {
                        String refColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "ref", columnMappings, config.getEntityBRole());
                        String nameColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "name", columnMappings, config.getEntityBRole());
                        String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                        String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                        if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                            String dbRefB = getEntityRefFromDb(conn, config.getEntityB(), entityBId);
                            if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                                row.addError("Incompatible ref: Regulatory Theme Ref. does not match the resolved Regulatory Theme.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Do not add relationship if either object is locked
                if (entityAId != null && entityAId > 0) {
                    String lockError = checkObjectLocked(entityAConfig.getName(), entityAId.intValue(), userId);
                    if (lockError != null) {
                        row.addError("Cannot add/update relationship: " + lockError);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                if (entityBId != null && entityBId > 0) {
                    String lockError = checkObjectLocked(entityBConfig.getName(), entityBId.intValue(), userId);
                    if (lockError != null) {
                        row.addError("Cannot add/update relationship: " + lockError);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Build relationship key for duplicate checking within the same batch/file
                // CRITICAL: Handle optional entities properly - for relationships, we typically need both entities
                // but some configurations allow one to be optional. We can only build a meaningful key if we have
                // at least the required entity IDs. For Project_X_System, Entity A (Project) is optional but
                // Entity B (System) is required, so we need Entity B at minimum.
                String relationshipKey = null;
                boolean canBuildKey = false;
                
                // Determine if we can build a relationship key: only when both entity IDs are non-null
                // so we never add keys like 14|NULL|1 to processedRelationships (avoids false duplicate for unresolved rows)
                if (entityAId != null && entityBId != null && entityAId > 0 && entityBId > 0) {
                    canBuildKey = true;
                }
                
                if (canBuildKey) {
                    // Create unique key for the relationship
                    // Always include relationship type in the key when the relationship supports it
                    // This ensures (EntityA, EntityB, RelationshipType) uniqueness
                    // For glossary_x_system also include dataset so (glossary, system, dataset, type) is unique
                    if (config.hasRelationType()) {
                        // Include relationship type in key (even if NULL) to enforce uniqueness
                        // Use explicit String.valueOf() to avoid any autoboxing issues and handle nulls safely
                        String entityAStr = entityAId != null ? String.valueOf(entityAId) : "NULL";
                        String entityBStr = entityBId != null ? String.valueOf(entityBId) : "NULL";
                        if ("glossary_x_system".equals(config.getTableName())) {
                            Integer datasetId = resolvedEntityIds.get("dataset");
                            String datasetStr = (datasetId != null && datasetId > 0) ? String.valueOf(datasetId) : "NULL";
                            relationshipKey = entityAStr + "|" + entityBStr + "|" + datasetStr + "|" +
                                (relationTypeId != null && relationTypeId > 0 ? String.valueOf(relationTypeId) : "NULL");
                        } else {
                            relationshipKey = entityAStr + "|" + entityBStr + "|" +
                                (relationTypeId != null && relationTypeId > 0 ? String.valueOf(relationTypeId) : "NULL");
                        }
                    } else {
                        // Relationship doesn't support types, so just use entity IDs
                        String entityAStr = entityAId != null ? String.valueOf(entityAId) : "NULL";
                        String entityBStr = entityBId != null ? String.valueOf(entityBId) : "NULL";
                        relationshipKey = entityAStr + "|" + entityBStr;
                    }
                    
                    // Check if this relationship was already processed in this batch
                    if (relationshipKey != null && processedRelationships.contains(relationshipKey)) {
                        String duplicateMessage;
                        if ("project_x_attribute".equals(config.getTableName())) {
                            duplicateMessage = "Another row in this file has the same Project, Attribute, and Relationship Type. " +
                                "Each (Project, Attribute, Relationship Type) combination must appear only once.";
                        } else {
                            String relationshipTypeInfo = config.hasRelationType()
                                ? (relationTypeId != null && relationTypeId > 0 
                                    ? ", Relationship Type ID: " + relationTypeId 
                                    : ", Relationship Type: NULL")
                                : "";
                            duplicateMessage = String.format(
                                "Duplicate relationship found in the same file. This relationship was already processed at an earlier row. " +
                                "Entity A ID: %s, Entity B ID: %s%s. " +
                                "The combination of (Entity A, Entity B, Relationship Type) must be unique.",
                                entityAId != null ? String.valueOf(entityAId) : "null",
                                entityBId != null ? String.valueOf(entityBId) : "null",
                                relationshipTypeInfo);
                        }
                        row.addError(duplicateMessage);
                        logger.debug("Row {}: In-file duplicate detected (key={}); skipping and adding to report", row.getRowNumber(), relationshipKey);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    
                    // Mark this relationship as processed
                    if (relationshipKey != null) {
                        processedRelationships.add(relationshipKey);
                    }
                } else {
                    // Cannot build relationship key - log for debugging
                    logger.debug("Row {}: Cannot build relationship key - Entity A: {}, Entity B: {}. " +
                        "Entity A required: {}, Entity B required: {}. Skipping duplicate check for this row.",
                        row.getRowNumber(), entityAId, entityBId, 
                        entityAConfig.isRequired(), entityBConfig.isRequired());
                }
                
                // Row is valid - all required entities are present
                row.setValid(true);
                validRows.add(row);
                logger.info("Row {}: Marked as VALID. Entity A ID: {}, Entity B ID: {}, Relationship Type ID: {}. " +
                    "This row will be inserted when cancelOnWarning is false.", 
                    row.getRowNumber(), entityAId, entityBId, relationTypeId);
            }
            
            logger.info("Validation complete: {} valid, {} skipped out of {} total rows", 
                validRows.size(), skippedRows.size(), rows.size());

            // Set failed count from Phase 1 skipped rows so initial response and GUI show correct count
            uploadResponse.setFailed(skippedRows.size());
            // Ensure failed is never 0 when there are validation errors: use distinct row count from errors (rows with errors, not total messages)
            int rowsWithErrors = (int) uploadResponse.getErrors().stream().map(ValidationIssue::getRow).distinct().count();
            if (rowsWithErrors > 0) {
                uploadResponse.setFailed(Math.max(uploadResponse.getFailed(), rowsWithErrors));
            }
            // Fallback when no skipped rows but we have error entries (path that missed adding to skippedRows)
            if (uploadResponse.getFailed() == 0 && !uploadResponse.getErrors().isEmpty()) {
                uploadResponse.setFailed(Math.max(1, rowsWithErrors > 0 ? rowsWithErrors : uploadResponse.getErrors().size()));
            }

            // Track rows that failed during insert (per-row commit mode) for accurate failed count in response
            int insertFailedCount = 0;
            
            // Log detailed breakdown if there are skipped rows
            if (!skippedRows.isEmpty()) {
                int errorCount = 0;
                int warningCount = 0;
                for (RelationshipRowData skipped : skippedRows) {
                    if (skipped.hasErrors()) {
                        errorCount += skipped.getErrors().size();
                    }
                    if (skipped.hasWarnings()) {
                        warningCount += skipped.getWarnings().size();
                    }
                }
                logger.info("Skipped rows breakdown: {} rows with {} errors and {} warnings", 
                    skippedRows.size(), errorCount, warningCount);
            }
            
            // Log detailed statistics if no valid rows
            if (validRows.isEmpty() && !rows.isEmpty()) {
                logger.warn("No valid rows found after validation. Total rows: {}, Skipped: {}", 
                    rows.size(), skippedRows.size());
                // Log reasons for skipping
                int preValidationErrors = 0;
                int entityResolutionErrors = 0;
                int relationshipTypeErrors = 0;
                int duplicateErrors = 0;
                int finalValidationErrors = 0;
                
                for (RelationshipRowData row : skippedRows) {
                    if (row.hasErrors()) {
                        for (String errorMsg : row.getErrors()) {
                            String errorMsgLower = errorMsg.toLowerCase();
                            if (errorMsgLower.contains("required but not provided") || errorMsgLower.contains("missing required")) {
                                preValidationErrors++;
                            } else if (errorMsgLower.contains("not found") || errorMsgLower.contains("could not find")) {
                                entityResolutionErrors++;
                            } else if (errorMsgLower.contains("relationship type")) {
                                relationshipTypeErrors++;
                            } else if (errorMsgLower.contains("duplicate")) {
                                duplicateErrors++;
                            } else {
                                finalValidationErrors++;
                            }
                        }
                    }
                }
                
                logger.warn("Error breakdown - Pre-validation: {}, Entity resolution: {}, Relationship type: {}, Duplicates: {}, Other: {}", 
                    preValidationErrors, entityResolutionErrors, relationshipTypeErrors, duplicateErrors, finalValidationErrors);
            }
            
            // If cancel on warning and there were errors/warnings, do not insert; generate report and return
            if (cancelOnWarning && (uploadResponse.hasErrors() || uploadResponse.hasWarnings())) {
                if (conn != null) {
                    try {
                        conn.rollback();
                        logger.info("Transaction rolled back due to validation errors (cancel on warning)");
                    } catch (SQLException ex) {
                        logger.error("Error rolling back transaction", ex);
                    }
                }
                uploadResponse.setSuccess(false);
                uploadResponse.setMessage("Upload cancelled due to validation errors");
                uploadResponse.setSkipped(skippedRows.size());
                uploadResponse.setInserted(0);
                uploadResponse.setFailed(skippedRows.size());
                uploadResponse.setAllProcessedRows(allProcessedRows);
                generateReportFromRows(allProcessedRows, jobId, config, "INSERT", userId, columnMappings);
                updateJobStatus(jobId, uploadResponse, "INSERT");
                return uploadResponse;
            }
            
            // Phase 2: Insert valid rows
            if (!validRows.isEmpty()) {
                validRowsCount = validRows.size();
                logger.info("Phase 2: Inserting {} relationships...", validRows.size());
                
                if (cancelOnWarning) {
                    // All-or-nothing mode: single transaction
                    if (validRows.size() > BATCH_THRESHOLD) {
                        insertedCount = insertBatch(conn, validRows, config, userId, jobId, columnMappings);
                    } else {
                        for (RelationshipRowData row : validRows) {
                            insertSingle(conn, row, config, userId, jobId, columnMappings);
                            insertedCount++;
                        }
                    }
                    
                    conn.commit();
                    logger.info("Successfully committed {} relationships", insertedCount);
                } else {
                    // Per-row commit mode: commit each row individually
                    // This ensures each successful row is committed even if subsequent rows fail
                    logger.info("Processing {} valid rows with per-row commits (cancelOnWarning=false)", validRows.size());
                    
                    for (RelationshipRowData row : validRows) {
                        // For per-row commits, ensure we start with a clean transaction state
                        boolean rowSucceeded = false;
                        
                        try {
                            // Ensure connection is in a good state before starting
                            if (conn.isClosed()) {
                                logger.error("Connection is closed, cannot process row {}", row.getRowNumber());
                                row.addError("Database connection is closed");
                                handleRowError(row, uploadResponse, skippedRows, false, jobId);
                                insertFailedCount++;
                                continue;
                            }
                            
                            // Verify connection is still valid
                            if (!conn.isValid(1)) {
                                logger.error("Connection is not valid, cannot process row {}", row.getRowNumber());
                                row.addError("Database connection is not valid");
                                handleRowError(row, uploadResponse, skippedRows, false, jobId);
                                insertFailedCount++;
                                continue;
                            }
                            
                            // Save current auto-commit state
                            boolean originalAutoCommit = conn.getAutoCommit();
                            
                            try {
                                // Set auto-commit to false for explicit transaction control
                                conn.setAutoCommit(false);
                                
                                // Insert the row
                                logger.debug("Inserting row {} with explicit transaction", row.getRowNumber());
                                insertSingle(conn, row, config, userId, jobId, columnMappings);
                                
                                // Commit the transaction explicitly
                                conn.commit();
                                rowSucceeded = true;
                                insertedCount++;
                                logger.info("Successfully committed row {} (insertedCount: {})", row.getRowNumber(), insertedCount);
                                
                            } catch (SQLException insertEx) {
                                // Rollback this specific row's transaction
                                try {
                                    if (!conn.isClosed()) {
                                        conn.rollback();
                                        logger.debug("Rolled back row {} due to SQL error: {}", row.getRowNumber(), insertEx.getMessage());
                                    }
                                } catch (SQLException rollbackEx) {
                                    logger.error("Error rolling back row {}: {}", row.getRowNumber(), rollbackEx.getMessage(), rollbackEx);
                                }
                                // Re-throw to be caught by outer catch block
                                throw insertEx;
                            } catch (Exception insertEx) {
                                // Rollback for any other exception type
                                try {
                                    if (!conn.isClosed()) {
                                        conn.rollback();
                                        logger.debug("Rolled back row {} due to error: {}", row.getRowNumber(), insertEx.getMessage());
                                    }
                                } catch (SQLException rollbackEx) {
                                    logger.error("Error rolling back row {}: {}", row.getRowNumber(), rollbackEx.getMessage(), rollbackEx);
                                }
                                // Record row as failed and create report item so failed count and GUI are correct
                                row.addError("Unexpected error: " + insertEx.getMessage());
                                handleRowError(row, uploadResponse, skippedRows, false, jobId);
                                // Re-throw to be caught by outer catch block
                                throw insertEx;
                            } finally {
                                // Always restore original auto-commit state
                                try {
                                    if (!conn.isClosed()) {
                                        conn.setAutoCommit(originalAutoCommit);
                                        logger.debug("Restored auto-commit state to {} for row {}", originalAutoCommit, row.getRowNumber());
                                    }
                                } catch (SQLException e) {
                                    logger.error("Error restoring auto-commit state for row {}: {}", row.getRowNumber(), e.getMessage(), e);
                                }
                            }
                            
                        } catch (SQLException e) {
                            logger.error("SQL error processing row {}: {}", row.getRowNumber(), e.getMessage(), e);
                            // handleRowError adds row to skippedRows; do not double-count via insertFailedCount
                            String errorMsg = "Database error: " + e.getMessage();
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, false, jobId);
                            
                            // Ensure connection is in a good state for next row
                            try {
                                if (!conn.isClosed()) {
                                    // Reset to auto-commit mode for next row
                                    conn.setAutoCommit(true);
                                    // Verify connection is still valid
                                    if (!conn.isValid(1)) {
                                        logger.warn("Connection is not valid after error on row {}, may need to reconnect", row.getRowNumber());
                                    } else {
                                        logger.debug("Connection is valid after error on row {}, continuing with next row", row.getRowNumber());
                                    }
                                }
                            } catch (SQLException recoveryEx) {
                                logger.error("Error recovering connection after error on row {}: {}", row.getRowNumber(), recoveryEx.getMessage(), recoveryEx);
                            }
                            
                        } catch (Exception e) {
                            logger.error("Unexpected error processing row {}: {}", row.getRowNumber(), e.getMessage(), e);
                            // handleRowError adds row to skippedRows; do not double-count via insertFailedCount
                            String errorMsg = "Unexpected error: " + e.getMessage();
                            row.addError(errorMsg);
                            handleRowError(row, uploadResponse, skippedRows, false, jobId);
                            
                            // Ensure connection is in a good state for next row
                            try {
                                if (!conn.isClosed()) {
                                    conn.setAutoCommit(true);
                                    if (!conn.isValid(1)) {
                                        logger.warn("Connection is not valid after unexpected error on row {}", row.getRowNumber());
                                    }
                                }
                            } catch (SQLException recoveryEx) {
                                logger.error("Error recovering connection after unexpected error on row {}: {}", row.getRowNumber(), recoveryEx.getMessage(), recoveryEx);
                            }
                        }
                        
                        // Log success/failure for this row
                        if (rowSucceeded) {
                            logger.debug("Row {} processed successfully", row.getRowNumber());
                        } else {
                            logger.debug("Row {} failed to process", row.getRowNumber());
                        }
                    }
                    
                    // Insert-phase failed count for messaging (rows already in skippedRows via handleRowError)
                    insertFailedCount = validRows.size() - insertedCount;
                    logger.info("Per-row commit processing complete: {} inserted, {} failed out of {} valid rows", 
                        insertedCount, insertFailedCount, validRows.size());
                }
                
                uploadResponse.setSuccess(insertedCount > 0);
                uploadResponse.setInserted(insertedCount);
                
                if (insertedCount == 0 && insertFailedCount > 0) {
                    uploadResponse.setMessage("No valid rows were inserted. All rows failed.");
                } else if (insertFailedCount > 0) {
                    uploadResponse.setMessage(String.format("Partially completed: %d inserted, %d failed", insertedCount, insertFailedCount));
                }
            } else {
                logger.warn("No valid rows to insert");
                uploadResponse.setSuccess(false);
                uploadResponse.setMessage("No valid rows to insert");
                uploadResponse.setInserted(0);
                uploadResponse.setFailed(skippedRows.size());
            }
            
            uploadResponse.setSkipped(skippedRows.size());
            // Failed count: skippedRows includes both validation-phase and insert-phase failures (handleRowError adds to skippedRows)
            uploadResponse.setFailed(skippedRows.size());
            // Ensure failed is never 0 when there are errors (GUI and BULK_COUNTS)
            int finalRowsWithErrors = (int) uploadResponse.getErrors().stream().map(ValidationIssue::getRow).distinct().count();
            if (finalRowsWithErrors > 0) {
                uploadResponse.setFailed(Math.max(uploadResponse.getFailed(), finalRowsWithErrors));
            }
            // When batch insert was used, rows skipped inside insertBatch are not in skippedRows; count them as failed
            if (validRowsCount > 0) {
                uploadResponse.setFailed(Math.max(uploadResponse.getFailed(), validRowsCount - insertedCount));
            }
            uploadResponse.setCacheStats(cacheStats);
            
            // Store all processed rows for report generation
            uploadResponse.setAllProcessedRows(allProcessedRows);
            
        } catch (SQLException e) {
            logger.error("Database error during upload", e);
            if (conn != null) {
                try {
                    // Only rollback if we're in transaction mode (cancelOnWarning=true)
                    if (cancelOnWarning) {
                        conn.rollback();
                        logger.info("Transaction rolled back due to error");
                    }
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            uploadResponse.setSuccess(false);
            uploadResponse.setMessage("Database error: " + e.getMessage());
            uploadResponse.setSkipped(skippedRows != null ? skippedRows.size() : 0);
            int failedCount = skippedRows != null ? skippedRows.size() : 0;
            // When insert phase failed (cancelOnWarning), attach error to every valid row so report shows all as failed
            if (validRows != null && !validRows.isEmpty()) {
                String errorMsg = "Database error: " + e.getMessage();
                for (RelationshipRowData r : validRows) {
                    r.addError(errorMsg);
                    uploadResponse.addError(ValidationIssue.error(r.getRowNumber(), "", "", errorMsg));
                }
                failedCount += validRows.size();
            }
            uploadResponse.setFailed(failedCount);
            // Ensure report has all rows even when exception occurred before allProcessedRows.addAll(rows)
            if (allProcessedRows.isEmpty() && rows != null && !rows.isEmpty()) {
                allProcessedRows.addAll(rows);
            }
            uploadResponse.setAllProcessedRows(allProcessedRows);
            if (uploadResponse.getFailed() == 0) {
                int errorAndWarningCount = uploadResponse.getErrors().size() + uploadResponse.getWarnings().size();
                int fallbackFailed = Math.max(1, validRowsCount > 0 ? validRowsCount : (errorAndWarningCount > 0 ? errorAndWarningCount : 1));
                uploadResponse.setFailed(fallbackFailed);
                try {
                    int reportItemId = jobDAO.createJobReportItem(jobId, "Relationship upload", "error", 1);
                    jobDAO.createJobReportItemMessage(reportItemId, "DATABASE_ERROR", e.getMessage(), "error");
                } catch (Exception reportEx) {
                    logger.warn("Failed to create job report item for database error: {}", reportEx.getMessage());
                }
            }
            generateReportFromRows(allProcessedRows, jobId, config, "INSERT", userId, columnMappings);
            updateJobStatus(jobId, uploadResponse, "INSERT");
        } catch (Exception e) {
            logger.error("Unexpected error during upload", e);
            if (conn != null && cancelOnWarning) {
                try {
                    conn.rollback();
                    logger.info("Transaction rolled back due to unexpected error");
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            uploadResponse.setSuccess(false);
            uploadResponse.setMessage("Unexpected error: " + e.getMessage());
            uploadResponse.setSkipped(skippedRows != null ? skippedRows.size() : 0);
            int failedCountEx = skippedRows != null ? skippedRows.size() : 0;
            if (validRows != null && !validRows.isEmpty()) {
                String errorMsg = "Unexpected error: " + e.getMessage();
                for (RelationshipRowData r : validRows) {
                    r.addError(errorMsg);
                    uploadResponse.addError(ValidationIssue.error(r.getRowNumber(), "", "", errorMsg));
                }
                failedCountEx += validRows.size();
            }
            uploadResponse.setFailed(failedCountEx);
            // Ensure report has all rows even when exception occurred before allProcessedRows.addAll(rows)
            if (allProcessedRows.isEmpty() && rows != null && !rows.isEmpty()) {
                allProcessedRows.addAll(rows);
            }
            uploadResponse.setAllProcessedRows(allProcessedRows);
            if (uploadResponse.getFailed() == 0) {
                int errorAndWarningCount = uploadResponse.getErrors().size() + uploadResponse.getWarnings().size();
                int fallbackFailed = Math.max(1, validRowsCount > 0 ? validRowsCount : (errorAndWarningCount > 0 ? errorAndWarningCount : 1));
                uploadResponse.setFailed(fallbackFailed);
                try {
                    int reportItemId = jobDAO.createJobReportItem(jobId, "Relationship upload", "error", 1);
                    jobDAO.createJobReportItemMessage(reportItemId, "UNEXPECTED_ERROR", e.getMessage(), "error");
                } catch (Exception reportEx) {
                    logger.warn("Failed to create job report item for unexpected error: {}", reportEx.getMessage());
                }
            }
            generateReportFromRows(allProcessedRows, jobId, config, "INSERT", userId, columnMappings);
            updateJobStatus(jobId, uploadResponse, "INSERT");
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
        
        return uploadResponse;
    }
    
    /**
     * Process DELETE operation
     */
    private BulkUploadResponse processDelete(List<RelationshipRowData> rows, RelationshipConfig config,
                                            int userId, boolean cancelOnWarning, int jobId, Map<String, String> columnMappings) {
        
        BulkUploadResponse uploadResponse = new BulkUploadResponse();
        // Use max of row count and max row number so report range covers all Excel rows (e.g. rows 2,3,5,10 -> maxRow 10)
        int maxRowNumberInRows = rows.isEmpty() ? 0 : rows.stream().mapToInt(RelationshipRowData::getRowNumber).max().orElse(rows.size());
        uploadResponse.setTotalRows(Math.max(rows.size(), maxRowNumberInRows));
        
        // For report generation: collect all rows
        List<RelationshipRowData> allProcessedRows = new ArrayList<>();
        
        // Initialize caches
        Map<String, Integer> entityCache = new HashMap<>();
        Map<String, Integer> relationTypeCache = new HashMap<>();
        CacheStats cacheStats = new CacheStats();
        
        Connection conn = null;
        List<RelationshipRowData> skippedRows = null;
        
        try {
            conn = getConnectionWithRetry(3);
            conn.setAutoCommit(false);
            
            List<RelationshipRowData> validRows = new ArrayList<>();
            skippedRows = new ArrayList<>();
            
            // Phase 1: Validate and resolve all rows
            logger.info("Phase 1: Validating {} rows for deletion...", rows.size());
            
            // Pre-populate all rows for report so report includes every row even when returning early
            allProcessedRows.addAll(rows);
            
            jobDAO.createJobProgress(jobId, 10, "Validating", "Validating rows for deletion...");
            
            for (RelationshipRowData row : rows) {
                // Pre-validate
                RelationshipExcelParser.preValidateRow(row, config, columnMappings);
                
                if (row.hasErrors()) {
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Early check: required relationship type empty (so row appears in report; mirror processInsert)
                if (config.hasRelationType() && config.isRequiresRelationType()) {
                    String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                    if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()) {
                        row.addError("Relationship type is required but not provided.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Resolve entities
                boolean resolved = resolveEntitiesForRow(conn, row, config, entityCache, cacheStats, userId, columnMappings);
                
                if (!resolved || row.hasErrors()) {
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Resolve relationship type
                if (config.hasRelationType()) {
                    resolveRelationshipTypeForRow(conn, row, config, relationTypeCache, cacheStats, columnMappings);
                    
                    if (row.getRelationshipTypeId() == null && config.isRequiresRelationType()) {
                        String relationTypeTable = config.getRelationTypeTable();
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        String availableColumns = row.getOriginalValues() != null && !row.getOriginalValues().isEmpty()
                            ? String.join(", ", row.getOriginalValues().keySet()) : "";
                        String valuePart = (relationshipTypeValue != null && !relationshipTypeValue.trim().isEmpty())
                            ? String.format(" Value provided: '%s'.", relationshipTypeValue.trim())
                            : "";
                        String columnsPart = availableColumns.isEmpty() ? "" : String.format(" Available columns in row: %s.", availableColumns);
                        row.addError(String.format(
                            "Relationship type is required but not found or could not be resolved.%s " +
                            "Please verify that the 'Relationship Type' column in your Excel file contains a valid value that exists in the %s lookup table.%s",
                            valuePart, relationTypeTable != null ? relationTypeTable : "relationship type lookup table", columnsPart));
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Glossary X System delete: resolve optional "Strategic Data Set Name" to dataset ID and store on row (so existence check and DELETE match exact row)
                if ("glossary_x_system".equals(config.getTableName())) {
                    try {
                        String strategicDataSetName = findFieldValue(row, "Strategic Data Set Name", columnMappings);
                        if (strategicDataSetName != null && !strategicDataSetName.trim().isEmpty()) {
                            Integer datasetId = resolveDatasetIdByName(conn, strategicDataSetName);
                            row.addResolvedEntityId("dataset", datasetId);
                        } else {
                            row.addResolvedEntityId("dataset", null);
                        }
                    } catch (SQLException e) {
                        logger.warn("Row {}: Failed to resolve Strategic Data Set Name for delete: {}", row.getRowNumber(), e.getMessage());
                        row.addResolvedEntityId("dataset", null);
                    }
                }
                
                // Required-entity check for delete (mirror processInsert)
                Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                Integer entityAId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                Integer entityBId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                // Regulation X Policy: clear errors for empty Regulation / empty Policy so report shows them
                if ("regulation_x_policy".equals(config.getTableName())) {
                    if (config.getEntityA().isRequired() && entityAId == null) {
                        row.addError("Regulation is required but not provided.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (config.getEntityB().isRequired() && entityBId == null) {
                        row.addError("Policy is required but not provided.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                // Regulation X Regulatory Theme: require both Regulation and Regulatory Theme (empty reg/theme -> clear report message)
                if ("regulation_x_regulatorytheme".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    if (entityAId == null) {
                        row.addError("Regulation is required but not provided.");
                    }
                    if (entityBId == null) {
                        row.addError("Regulatory Theme is required but not provided.");
                    }
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                // System X Legal (system_x_legal) DELETE: clear report reasons for empty system, empty legal
                if ("system_x_legal".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg;
                        if (entityAId == null && entityBId == null) {
                            errorMsg = "System and Legal Entity are required but not provided. Provide System Short Name and Legal Entity Short Name.";
                        } else if (entityAId == null) {
                            errorMsg = "System is required but not provided.";
                        } else {
                            errorMsg = "Legal Entity is required but not provided.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                // Product X Legal (product_x_legal) DELETE: require both relationship type and legal entity, both as specified in the database
                if ("product_x_legal".equals(config.getTableName())) {
                    Integer relationTypeId = row.getRelationshipTypeId();
                    if (entityBId == null || (config.isRequiresRelationType() && relationTypeId == null)) {
                        String errorMsg;
                        if (entityBId == null && (relationTypeId == null && config.isRequiresRelationType())) {
                            errorMsg = "To delete this row, provide both Relationship Type and Legal Entity exactly as specified in the database.";
                        } else if (entityBId == null) {
                            errorMsg = "Legal Entity is required for delete. Provide the Legal Entity Short Name (and Parent if applicable) as specified in the database.";
                        } else {
                            errorMsg = "Relationship type is required for delete. Provide the relationship type as specified in the database.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                if (config.getEntityA().isRequired() && entityAId == null) {
                    row.addError(String.format("Required Entity A (%s) ID is null - cannot delete relationship",
                        config.getEntityA().getName()));
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                if (config.getEntityB().isRequired() && entityBId == null) {
                    row.addError(String.format("Required Entity B (%s) ID is null - cannot delete relationship",
                        config.getEntityB().getName()));
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Glossary X System delete: when Glossary Ref (Entity B) is provided, verify it matches the resolved Glossary (do not delete when incompatible)
                if ("glossary_x_system".equals(config.getTableName()) && entityBId != null && config.getEntityB().hasRefColumn()) {
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "ref", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, config.getEntityB(), entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            row.addError(String.format(
                                "Glossary Ref. value '%s' does not match the resolved Glossary (Ref: %s). Use the correct Glossary Ref. or leave it blank to resolve by name.",
                                refBVal.trim(), dbRefB));
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Product X Business Area delete: when both Product Ref and Product Name provided, verify they match the resolved product (do not delete when incompatible)
                if ("product_x_businessarea".equals(config.getTableName()) && entityAId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError(String.format(
                                "Product Ref. value '%s' does not match the resolved Product (Ref: %s). Use the correct Product Ref. or leave it blank to resolve by name.",
                                refAVal.trim(), dbRefA));
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Product X Client delete: when both Product Ref and Product Name provided, verify they match the resolved product (do not delete when incompatible)
                if ("product_x_client".equals(config.getTableName()) && entityAId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError(String.format(
                                "Product Ref. value '%s' does not match the resolved Product (Ref: %s). Use the correct Product Ref. or leave it blank to resolve by name.",
                                refAVal.trim(), dbRefA));
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Project X Project delete: when both Source Project Ref and Source Project Name (or both Target) provided, verify they match the resolved project (incompatible ref)
                if ("project_x_project".equals(config.getTableName()) && entityAId != null && entityBId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError("Incompatible ref: Source Project Ref. does not match the resolved Source Project.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "ref", columnMappings, config.getEntityBRole());
                    String nameColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "name", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, config.getEntityB(), entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            row.addError("Incompatible ref: Target Project Ref. does not match the resolved Target Project.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Regulation X Policy delete: when both Regulation Ref and Name (or both Policy Ref and Name) provided, verify they match resolved entity (incompatible ref); ensure clear errors for empty reg/policy
                if ("regulation_x_policy".equals(config.getTableName()) && entityAId != null && entityBId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError("Incompatible ref: Regulation Ref. does not match the resolved Regulation.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    String refColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "ref", columnMappings, config.getEntityBRole());
                    String nameColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "name", columnMappings, config.getEntityBRole());
                    String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                    String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                    if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                        String dbRefB = getEntityRefFromDb(conn, config.getEntityB(), entityBId);
                        if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                            row.addError("Incompatible ref: Policy Ref. does not match the resolved Policy.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Regulation X Product delete: when both Regulation Ref and Regulation Name provided, verify they match resolved regulation (incompatible ref)
                if ("regulation_x_product".equals(config.getTableName()) && entityAId != null && entityBId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError("Incompatible ref: Regulation Ref. does not match the resolved Regulation.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Regulation X Regulator delete: when both Regulation Ref and Regulation Name provided, verify they match resolved regulation (incompatible ref); regulator has no ref
                if ("regulation_x_regulator".equals(config.getTableName()) && entityAId != null && entityBId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError("Incompatible ref: Regulation Ref. does not match the resolved Regulation.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Regulation X Regulatory Theme delete: when both Regulation Ref and Name (or both Regulatory Theme Ref and Name) provided, verify they match resolved entity (incompatible ref)
                if ("regulation_x_regulatorytheme".equals(config.getTableName()) && entityAId != null && entityBId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        String dbRefA = getEntityRefFromDb(conn, config.getEntityA(), entityAId);
                        if (dbRefA != null && !refAVal.trim().equalsIgnoreCase(dbRefA.trim())) {
                            row.addError("Incompatible ref: Regulation Ref. does not match the resolved Regulation.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    if (config.getEntityB().hasRefColumn()) {
                        String refColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "ref", columnMappings, config.getEntityBRole());
                        String nameColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "name", columnMappings, config.getEntityBRole());
                        String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                        String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                        if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                            String dbRefB = getEntityRefFromDb(conn, config.getEntityB(), entityBId);
                            if (dbRefB != null && !refBVal.trim().equalsIgnoreCase(dbRefB.trim())) {
                                row.addError("Incompatible ref: Regulatory Theme Ref. does not match the resolved Regulatory Theme.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Policy X Attribute delete: when Attribute System Short Name or Attribute Data Set Name is provided, validate they match the resolved attribute's system/dataset (prevent deleting wrong relationship)
                if ("policy_x_attribute".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String rowSystemShortName = getRowValueByColumnPattern(row, "Attribute System", "System Short");
                    String rowDataSetName = getRowValueByColumnPattern(row, "Attribute Data Set", "Data Set Name");
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowDataSetName != null && !rowDataSetName.trim().isEmpty())) {
                        String[] attrSystemDataset = getAttributeDatasetAndSystemName(conn, entityBId);
                        String dbDatasetName = attrSystemDataset != null ? attrSystemDataset[0] : null;
                        String dbSystemName = attrSystemDataset != null ? attrSystemDataset[1] : null;
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Attribute System Short Name was provided but the attribute is not linked to a system (or dataset has no system).");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Attribute System Short Name '%s' does not match the attribute's system ('%s'). The attribute belongs to a different system.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowDataSetName != null && !rowDataSetName.trim().isEmpty()) {
                            if (dbDatasetName == null || dbDatasetName.trim().isEmpty()) {
                                row.addError("Attribute Data Set Name was provided but the attribute is not linked to a dataset.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowDataSetName.trim().equalsIgnoreCase(dbDatasetName.trim())) {
                                row.addError(String.format(
                                    "Attribute Data Set Name '%s' does not match the attribute's dataset ('%s'). The attribute belongs to a different dataset.",
                                    rowDataSetName.trim(), dbDatasetName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Process X Attribute delete: when Attribute System Short Name or Attribute Data Set Name is provided, validate they match the resolved attribute's system/dataset (prevent deleting wrong relationship)
                if ("process_x_attribute".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String rowSystemShortName = getRowValueByColumnPattern(row, "Attribute System", "System Short");
                    String rowDataSetName = getRowValueByColumnPattern(row, "Attribute Data Set", "Data Set Name");
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowDataSetName != null && !rowDataSetName.trim().isEmpty())) {
                        String[] attrSystemDataset = getAttributeDatasetAndSystemName(conn, entityBId);
                        String dbDatasetName = attrSystemDataset != null ? attrSystemDataset[0] : null;
                        String dbSystemName = attrSystemDataset != null ? attrSystemDataset[1] : null;
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Attribute System Short Name was provided but the attribute is not linked to a system (or dataset has no system).");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Attribute System Short Name '%s' does not match the attribute's system ('%s'). The attribute belongs to a different system.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowDataSetName != null && !rowDataSetName.trim().isEmpty()) {
                            if (dbDatasetName == null || dbDatasetName.trim().isEmpty()) {
                                row.addError("Attribute Data Set Name was provided but the attribute is not linked to a dataset.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowDataSetName.trim().equalsIgnoreCase(dbDatasetName.trim())) {
                                row.addError(String.format(
                                    "Attribute Data Set Name '%s' does not match the attribute's dataset ('%s'). The attribute belongs to a different dataset.",
                                    rowDataSetName.trim(), dbDatasetName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Project X Attribute delete: when Attribute System Short Name or Attribute Data Set Name is provided, validate they match the resolved attribute's system/dataset (prevent deleting wrong relationship)
                if ("project_x_attribute".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    String rowSystemShortName = getRowValueByColumnPattern(row, "Attribute System", "System Short");
                    String rowDataSetName = getRowValueByColumnPattern(row, "Attribute Data Set", "Data Set Name");
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowDataSetName != null && !rowDataSetName.trim().isEmpty())) {
                        String[] attrSystemDataset = getAttributeDatasetAndSystemName(conn, entityBId);
                        String dbDatasetName = attrSystemDataset != null ? attrSystemDataset[0] : null;
                        String dbSystemName = attrSystemDataset != null ? attrSystemDataset[1] : null;
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Attribute System Short Name was provided but the attribute is not linked to a system (or dataset has no system).");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Attribute System Short Name '%s' does not match the attribute's system ('%s'). The attribute belongs to a different system.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowDataSetName != null && !rowDataSetName.trim().isEmpty()) {
                            if (dbDatasetName == null || dbDatasetName.trim().isEmpty()) {
                                row.addError("Attribute Data Set Name was provided but the attribute is not linked to a dataset.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowDataSetName.trim().equalsIgnoreCase(dbDatasetName.trim())) {
                                row.addError(String.format(
                                    "Attribute Data Set Name '%s' does not match the attribute's dataset ('%s'). The attribute belongs to a different dataset.",
                                    rowDataSetName.trim(), dbDatasetName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Interface X Glossary delete: clear errors when interface or glossary is missing (before existence check)
                if ("interface_x_glossary".equals(config.getTableName())) {
                    if (entityAId == null) {
                        String ifaceSourceName = findFieldValue(row, "Interface Source System Short Name", columnMappings);
                        String ifaceTargetName = findFieldValue(row, "Interface Target System Short Name", columnMappings);
                        boolean hasSourceOrTarget = (ifaceSourceName != null && !ifaceSourceName.trim().isEmpty()) || (ifaceTargetName != null && !ifaceTargetName.trim().isEmpty());
                        if (hasSourceOrTarget) {
                            row.addError("Either Interface Name or Interface Ref. is required to identify the interface.");
                        } else {
                            row.addError("Interface is required. Provide Interface Ref. or Interface Name.");
                        }
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null) {
                        row.addError("Glossary is required. Provide Glossary Ref. or Glossary Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Committee X Committee delete: require both Source and Target Committee resolved (avoid misleading "Relationship does not exist" when target is missing)
                if ("committee_x_committee".equals(config.getTableName())) {
                    if (entityAId == null || entityBId == null) {
                        String errorMsg = "Committee X Committee delete requires both Source Committee and Target Committee to be resolved. " +
                            "Provide Source Committee Ref. or Name and Target Committee Ref. or Name.";
                        if (entityAId == null && RelationshipExcelParser.hasEntityData(row, config.getEntityA(), columnMappings, config.getEntityARole())) {
                            errorMsg = "Source Committee was provided but could not be resolved. Check Source Committee Ref./Name and Parent Name.";
                        } else if (entityBId == null && RelationshipExcelParser.hasEntityData(row, config.getEntityB(), columnMappings, config.getEntityBRole())) {
                            errorMsg = "Target Committee was provided but could not be resolved. Check Target Committee Ref./Name and Parent Name.";
                        } else if (entityBId == null) {
                            errorMsg = "Target Committee was not provided or could not be resolved. Check Target Committee Ref./Name and Parent Name.";
                        } else if (entityAId == null) {
                            errorMsg = "Source Committee was not provided or could not be resolved. Check Source Committee Ref./Name and Parent Name.";
                        }
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Process X System Interface delete: require both Process and Interface resolved (specific errors instead of generic "Relationship does not exist")
                if ("process_x_interface".equals(config.getTableName())) {
                    if (entityAId == null) {
                        String errorMsg = RelationshipExcelParser.hasEntityData(row, config.getEntityA(), columnMappings, config.getEntityARole())
                            ? "Process was provided but could not be resolved. Check Process Ref./Name and Parent Process Name."
                            : "Process is required. Provide Process Ref. or Process Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null) {
                        String ifaceSourceName = findFieldValue(row, "Interface Source System Short Name", columnMappings);
                        String ifaceTargetName = findFieldValue(row, "Interface Target System Short Name", columnMappings);
                        String ifaceName = findFieldValue(row, "Interface Name", columnMappings);
                        String ifaceRef = findFieldValue(row, "Interface Ref.", columnMappings);
                        boolean hasSourceOrTarget = (ifaceSourceName != null && !ifaceSourceName.trim().isEmpty()) || (ifaceTargetName != null && !ifaceTargetName.trim().isEmpty());
                        boolean hasIfaceNameOrRef = (ifaceName != null && !ifaceName.trim().isEmpty()) || (ifaceRef != null && !ifaceRef.trim().isEmpty());
                        String errorMsg = (hasSourceOrTarget && !hasIfaceNameOrRef)
                            ? "Either Interface Name or Interface Ref. is required to identify the interface."
                            : "Interface is required. Provide Interface Ref. or Interface Name.";
                        row.addError(errorMsg);
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Process X Attribute delete: require both Process and Attribute resolved (specific errors instead of generic "Relationship does not exist in database - cannot delete")
                if ("process_x_attribute".equals(config.getTableName())) {
                    if (entityAId == null) {
                        row.addError("Process is required but not provided - cannot delete.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null) {
                        row.addError("Attribute is required but not provided - cannot delete.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Process X Client (client_x_process) delete: require both Client and Process (specific errors instead of generic "Relationship does not exist in database - cannot delete")
                if ("client_x_process".equals(config.getTableName())) {
                    if (entityAId == null) {
                        row.addError("Client is required. Provide Client Name (or Client Parent Name).");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (entityBId == null) {
                        row.addError("Process is required. Provide Process Ref. or Process Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Product X Client delete: when row has client data require Client resolved; when row has product data require Product resolved (specific errors instead of generic "Relationship does not exist")
                if ("product_x_client".equals(config.getTableName())) {
                    if (RelationshipExcelParser.hasEntityData(row, config.getEntityB(), columnMappings, config.getEntityBRole()) && entityBId == null) {
                        row.addError("Client is required. Provide Client Name (or Parent Client Name).");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    if (RelationshipExcelParser.hasEntityData(row, config.getEntityA(), columnMappings, config.getEntityARole()) && entityAId == null) {
                        row.addError("Product was provided but could not be resolved. Check Product Ref./Name and Parent Product Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                }
                
                // Project X Glossary (glossary_x_project) delete: when both Glossary Ref and Glossary Name provided, ensure they refer to the same glossary (prevent deleting with incompatible ref)
                if ("glossary_x_project".equals(config.getTableName()) && entityAId != null && entityBId != null && config.getEntityA().hasRefColumn()) {
                    String refColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "ref", columnMappings, config.getEntityARole());
                    String nameColA = RelationshipExcelParser.findExcelColumnName(row, config.getEntityA(), "name", columnMappings, config.getEntityARole());
                    String refAVal = refColA != null ? row.getOriginalValues().get(refColA) : null;
                    String nameAVal = nameColA != null ? row.getOriginalValues().get(nameColA) : null;
                    if (refAVal != null && !refAVal.trim().isEmpty() && nameAVal != null && !nameAVal.trim().isEmpty()) {
                        EntityResolutionResult resultByRefA = entityResolverService.resolveEntity(
                            conn, config.getEntityA(), refAVal.trim(), null, null, row.getRowNumber(), entityCache, cacheStats);
                        EntityResolutionResult resultByNameA = entityResolverService.resolveEntity(
                            conn, config.getEntityA(), null, nameAVal.trim(), null, row.getRowNumber(), entityCache, cacheStats);
                        Integer idByRefA = resultByRefA.isFound() ? resultByRefA.getId() : null;
                        Integer idByNameA = resultByNameA.isFound() ? resultByNameA.getId() : null;
                        if (idByRefA != null && idByNameA != null && !idByRefA.equals(idByNameA)) {
                            row.addError("Glossary Ref. and Glossary Name refer to different glossaries. Use a ref and name that identify the same glossary, or leave one blank.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Project X Attribute delete: require project/attribute when provided; empty attribute → specific error; incompatible ref vs name → specific error (before existence check)
                if ("project_x_attribute".equals(config.getTableName())) {
                    // When attribute not resolved but row has Name and (Data Set or System), replace generic "not found" with clearer message (issue 3)
                    if (entityBId == null) {
                        String attrName = getRowValueByColumnPattern(row, "Attribute", "Name");
                        String attrDataSet = getRowValueByColumnPattern(row, "Attribute Data Set", "Data Set Name");
                        String attrSystem = getRowValueByColumnPattern(row, "Attribute System", "System Short");
                        boolean hasName = attrName != null && !attrName.trim().isEmpty();
                        boolean hasDataSetOrSystem = (attrDataSet != null && !attrDataSet.trim().isEmpty()) || (attrSystem != null && !attrSystem.trim().isEmpty());
                        if (hasName && hasDataSetOrSystem) {
                            String clearerMsg = "No attribute found with the given Attribute Name and Attribute Data Set Name / Attribute System Short Name combination. Verify the attribute exists and that the dataset and system match.";
                            boolean foundGeneric = false;
                            for (String err : new ArrayList<>(row.getErrors())) {
                                if (err != null && (err.toLowerCase().contains("not found") || err.toLowerCase().contains("no attribute found"))) {
                                    row.getErrors().remove(err);
                                    foundGeneric = true;
                                }
                            }
                            for (String warn : new ArrayList<>(row.getWarnings())) {
                                if (warn != null && (warn.toLowerCase().contains("not found") || warn.toLowerCase().contains("no attribute found"))) {
                                    row.getWarnings().remove(warn);
                                    foundGeneric = true;
                                }
                            }
                            if (foundGeneric) {
                                row.addError(clearerMsg);
                            }
                        }
                    }
                    if (RelationshipExcelParser.hasEntityData(row, config.getEntityA(), columnMappings, config.getEntityARole()) && entityAId == null) {
                        row.addError("Project was provided but could not be resolved. Check Project Ref./Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    boolean hasAttributeData = RelationshipExcelParser.hasEntityData(row, config.getEntityB(), columnMappings, config.getEntityBRole());
                    if (hasAttributeData && entityBId == null) {
                        row.addError("Attribute was provided but could not be resolved. Check Attribute Ref./Name and optionally Attribute Data Set Name / Attribute System Short Name.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // Empty attribute columns: no Attribute Ref, no Attribute Name (and no dataset/system) → attribute required for delete
                    String attrRefVal = getRowValueByColumnPattern(row, "Attribute", "Ref");
                    String attrNameVal = getRowValueByColumnPattern(row, "Attribute", "Name");
                    if (attrRefVal == null) attrRefVal = "";
                    if (attrNameVal == null) attrNameVal = "";
                    boolean hasAnyAttributeIdentifier = (attrRefVal != null && !attrRefVal.trim().isEmpty()) || (attrNameVal != null && !attrNameVal.trim().isEmpty());
                    if (!hasAnyAttributeIdentifier && entityBId == null) {
                        row.addError("Attribute is required for delete. Provide Attribute Ref. or Attribute Name (and optionally Attribute Data Set Name / Attribute System Short Name).");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // When both Attribute Ref and Attribute Name provided, ensure they refer to the same attribute
                    if (entityAId != null && entityBId != null && config.getEntityB().hasRefColumn()) {
                        String refColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "ref", columnMappings, config.getEntityBRole());
                        String nameColB = RelationshipExcelParser.findExcelColumnName(row, config.getEntityB(), "name", columnMappings, config.getEntityBRole());
                        String refBVal = refColB != null ? row.getOriginalValues().get(refColB) : null;
                        String nameBVal = nameColB != null ? row.getOriginalValues().get(nameColB) : null;
                        if (refBVal != null && !refBVal.trim().isEmpty() && nameBVal != null && !nameBVal.trim().isEmpty()) {
                            EntityResolutionResult resultByRefB = entityResolverService.resolveEntity(
                                conn, config.getEntityB(), refBVal.trim(), null, null, row.getRowNumber(), entityCache, cacheStats);
                            EntityResolutionResult resultByNameB = entityResolverService.resolveEntity(
                                conn, config.getEntityB(), null, nameBVal.trim(), null, row.getRowNumber(), entityCache, cacheStats);
                            Integer idByRefB = resultByRefB.isFound() ? resultByRefB.getId() : null;
                            Integer idByNameB = resultByNameB.isFound() ? resultByNameB.getId() : null;
                            if (idByRefB != null && idByNameB != null && !idByRefB.equals(idByNameB)) {
                                row.addError("Attribute Ref. and Attribute Name refer to different attributes. Use a ref and name that identify the same attribute, or leave one blank.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Data Set X Client (delete): validate Data Set System Short Name and Data Set Parent System Short Name match the dataset's system (and parent)
                if ("client_x_dataset".equals(config.getTableName()) && entityBId != null) {
                    String rowSystemShortName = findFieldValue(row, "Data Set System Short Name", columnMappings);
                    String rowParentSystemShortName = findFieldValue(row, "Data Set Parent System Short Name", columnMappings);
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty())) {
                        String[] dsSystemParent = getDatasetSystemAndParentSystemName(conn, entityBId);
                        if (dsSystemParent == null) {
                            row.addError("Could not determine the dataset's system. Please verify the dataset exists and is linked to a system.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                        String dbSystemName = dsSystemParent[0];
                        String dbParentSystemName = dsSystemParent[1];
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Data Set System Short Name was provided but the dataset is not linked to a system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set System Short Name '%s' does not match the dataset's system ('%s'). Everything entered must be compatible.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty()) {
                            if (dbParentSystemName == null || dbParentSystemName.trim().isEmpty()) {
                                row.addError("Data Set Parent System Short Name was provided but the dataset's system has no parent system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowParentSystemShortName.trim().equalsIgnoreCase(dbParentSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set Parent System Short Name '%s' does not match the dataset's system parent ('%s'). Everything entered must be compatible.",
                                    rowParentSystemShortName.trim(), dbParentSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                // Data Set X Product (delete): validate Data Set System Short Name and Data Set Parent System Short Name match the dataset's system (and parent)
                else if ("product_x_dataset".equals(config.getTableName()) && entityBId != null) {
                    String rowSystemShortName = findFieldValue(row, "Data Set System Short Name", columnMappings);
                    String rowParentSystemShortName = findFieldValue(row, "Data Set Parent System Short Name", columnMappings);
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty())) {
                        String[] dsSystemParent = getDatasetSystemAndParentSystemName(conn, entityBId);
                        if (dsSystemParent == null) {
                            row.addError("Could not determine the dataset's system. Please verify the dataset exists and is linked to a system.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                        String dbSystemName = dsSystemParent[0];
                        String dbParentSystemName = dsSystemParent[1];
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Data Set System Short Name was provided but the dataset is not linked to a system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set System Short Name '%s' does not match the dataset's system ('%s'). Everything entered must be compatible.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty()) {
                            if (dbParentSystemName == null || dbParentSystemName.trim().isEmpty()) {
                                row.addError("Data Set Parent System Short Name was provided but the dataset's system has no parent system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowParentSystemShortName.trim().equalsIgnoreCase(dbParentSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set Parent System Short Name '%s' does not match the dataset's system parent ('%s'). Everything entered must be compatible.",
                                    rowParentSystemShortName.trim(), dbParentSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                // Policy X Data Set (delete): validate Data Set System Short Name and Data Set Parent System Short Name match the dataset's system (prevent deleting when incompatible)
                else if ("policy_x_dataset".equals(config.getTableName()) && entityBId != null) {
                    String rowSystemShortName = findFieldValue(row, "Data Set System Short Name", columnMappings);
                    String rowParentSystemShortName = findFieldValue(row, "Data Set Parent System Short Name", columnMappings);
                    if ((rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) || (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty())) {
                        String[] dsSystemParent = getDatasetSystemAndParentSystemName(conn, entityBId);
                        if (dsSystemParent == null) {
                            row.addError("Could not determine the dataset's system. Please verify the dataset exists and is linked to a system.");
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                        String dbSystemName = dsSystemParent[0];
                        String dbParentSystemName = dsSystemParent[1];
                        if (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty()) {
                            if (dbSystemName == null || dbSystemName.trim().isEmpty()) {
                                row.addError("Data Set System Short Name was provided but the dataset is not linked to a system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowSystemShortName.trim().equalsIgnoreCase(dbSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set System Short Name '%s' does not match the dataset's system ('%s'). Everything entered must be compatible.",
                                    rowSystemShortName.trim(), dbSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                        if (rowParentSystemShortName != null && !rowParentSystemShortName.trim().isEmpty()) {
                            if (dbParentSystemName == null || dbParentSystemName.trim().isEmpty()) {
                                row.addError("Data Set Parent System Short Name was provided but the dataset's system has no parent system.");
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                            if (!rowParentSystemShortName.trim().equalsIgnoreCase(dbParentSystemName.trim())) {
                                row.addError(String.format(
                                    "Data Set Parent System Short Name '%s' does not match the dataset's system parent ('%s'). Everything entered must be compatible.",
                                    rowParentSystemShortName.trim(), dbParentSystemName));
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        }
                    }
                }
                
                // Prevent delete of self-relationship (same-facet types: glossary X glossary, policy X policy, etc.)
                resolvedIds = row.getResolvedEntityIds();
                if (resolvedIds != null) {
                    entityAId = resolvedIds.get("entityA");
                    entityBId = resolvedIds.get("entityB");
                    if (entityAId != null && entityBId != null
                        && config.getEntityA().getTableName().equals(config.getEntityB().getTableName())
                        && entityAId.equals(entityBId)) {
                        row.addError("Cannot delete: relationship is between an object and itself. Source and target must be different objects.");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        continue;
                    }
                    // Attribute X Attribute: when interface is specified, source and target attributes' systems must match the interface's source and target systems
                    if ("attribute_x_attribute".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                        try {
                            Integer interfaceId = resolveInterfaceIdFromRow(conn, row, columnMappings);
                            if (interfaceId != null) {
                                int[] ifaceSystems = getInterfaceSourceAndTargetSystemIds(conn, interfaceId);
                                if (ifaceSystems != null && (ifaceSystems[0] != 0 || ifaceSystems[1] != 0)) {
                                    Integer sourceAttrSys = getAttributeSystemId(conn, entityAId);
                                    Integer targetAttrSys = getAttributeSystemId(conn, entityBId);
                                    if (ifaceSystems[0] != 0 && (sourceAttrSys == null || sourceAttrSys != ifaceSystems[0])) {
                                        row.addError("The specified interface's source system must match the source attribute's system. The source attribute must belong to a dataset in the interface's source system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                    if (ifaceSystems[1] != 0 && (targetAttrSys == null || targetAttrSys != ifaceSystems[1])) {
                                        row.addError("The specified interface's target system must match the target attribute's system. The target attribute must belong to a dataset in the interface's target system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                    // If Interface Source/Target System Short Name columns are provided, they must match the interface's source/target
                                    String ifaceSourceName = findFieldValue(row, "Interface Source System Short Name", columnMappings);
                                    String ifaceTargetName = findFieldValue(row, "Interface Target System Short Name", columnMappings);
                                    if (ifaceSourceName != null && !ifaceSourceName.trim().isEmpty() && ifaceSystems[0] != 0) {
                                        Integer resolvedSysId = resolveSystemIdByName(conn, ifaceSourceName.trim());
                                        if (resolvedSysId == null || resolvedSysId != ifaceSystems[0]) {
                                            row.addError("Interface source system short name must match the specified interface's source system.");
                                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                            continue;
                                        }
                                    }
                                    if (ifaceTargetName != null && !ifaceTargetName.trim().isEmpty() && ifaceSystems[1] != 0) {
                                        Integer resolvedSysId = resolveSystemIdByName(conn, ifaceTargetName.trim());
                                        if (resolvedSysId == null || resolvedSysId != ifaceSystems[1]) {
                                            row.addError("Interface target system short name must match the specified interface's target system.");
                                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                            continue;
                                        }
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            logger.error("Row {}: Error validating attribute_x_attribute interface compatibility (delete): {}", row.getRowNumber(), e.getMessage(), e);
                            row.addError("Could not validate interface and source/target compatibility: " + e.getMessage());
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    // Interface X Glossary delete: row's Interface Source/Target System Short Name must match the resolved interface's source/target
                    if ("interface_x_glossary".equals(config.getTableName()) && entityAId != null) {
                        try {
                            int[] ifaceSystems = getInterfaceSourceAndTargetSystemIds(conn, entityAId);
                            if (ifaceSystems != null && (ifaceSystems[0] != 0 || ifaceSystems[1] != 0)) {
                                String ifaceSourceName = findFieldValue(row, "Interface Source System Short Name", columnMappings);
                                String ifaceTargetName = findFieldValue(row, "Interface Target System Short Name", columnMappings);
                                if (ifaceSourceName != null && !ifaceSourceName.trim().isEmpty() && ifaceSystems[0] != 0) {
                                    Integer resolvedSysId = resolveSystemIdByName(conn, ifaceSourceName.trim());
                                    if (resolvedSysId == null || resolvedSysId != ifaceSystems[0]) {
                                        row.addError("Interface source system short name must match the specified interface's source system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                }
                                if (ifaceTargetName != null && !ifaceTargetName.trim().isEmpty() && ifaceSystems[1] != 0) {
                                    Integer resolvedSysId = resolveSystemIdByName(conn, ifaceTargetName.trim());
                                    if (resolvedSysId == null || resolvedSysId != ifaceSystems[1]) {
                                        row.addError("Interface target system short name must match the specified interface's target system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            logger.error("Row {}: Error validating interface_x_glossary source/target (delete): {}", row.getRowNumber(), e.getMessage(), e);
                            row.addError("Could not validate interface source/target: " + e.getMessage());
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    // Process X System Interface delete: row's Interface Source/Target System Short Name must match the resolved interface's source/target
                    if ("process_x_interface".equals(config.getTableName()) && entityBId != null) {
                        try {
                            int[] ifaceSystems = getInterfaceSourceAndTargetSystemIds(conn, entityBId);
                            if (ifaceSystems != null && (ifaceSystems[0] != 0 || ifaceSystems[1] != 0)) {
                                String ifaceSourceName = findFieldValue(row, "Interface Source System Short Name", columnMappings);
                                String ifaceTargetName = findFieldValue(row, "Interface Target System Short Name", columnMappings);
                                if (ifaceSourceName != null && !ifaceSourceName.trim().isEmpty() && ifaceSystems[0] != 0) {
                                    Integer resolvedSysId = resolveSystemIdByName(conn, ifaceSourceName.trim());
                                    if (resolvedSysId == null || resolvedSysId != ifaceSystems[0]) {
                                        row.addError("Interface source system short name must match the specified interface's source system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                }
                                if (ifaceTargetName != null && !ifaceTargetName.trim().isEmpty() && ifaceSystems[1] != 0) {
                                    Integer resolvedSysId = resolveSystemIdByName(conn, ifaceTargetName.trim());
                                    if (resolvedSysId == null || resolvedSysId != ifaceSystems[1]) {
                                        row.addError("Interface target system short name must match the specified interface's target system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                }
                                // Detect interchanged source/target: if row provides both, they must match interface's source and target (not swapped)
                                if (ifaceSourceName != null && !ifaceSourceName.trim().isEmpty() && ifaceTargetName != null && !ifaceTargetName.trim().isEmpty()
                                        && ifaceSystems[0] != 0 && ifaceSystems[1] != 0) {
                                    Integer rowSourceId = resolveSystemIdByName(conn, ifaceSourceName.trim());
                                    Integer rowTargetId = resolveSystemIdByName(conn, ifaceTargetName.trim());
                                    if (rowSourceId != null && rowTargetId != null
                                            && rowSourceId.equals(ifaceSystems[1]) && rowTargetId.equals(ifaceSystems[0])) {
                                        row.addError("Interface source and target systems are interchanged. Interface Source System Short Name must match the interface's source system and Interface Target System Short Name must match the interface's target system.");
                                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                        continue;
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            logger.error("Row {}: Error validating process_x_interface source/target (delete): {}", row.getRowNumber(), e.getMessage(), e);
                            row.addError("Could not validate interface source/target: " + e.getMessage());
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    // Prevent delete when objects are in different private segments
                    if (entityAId != null && entityBId != null) {
                        try {
                            String sourceObjectType = entityNameToSegmentObjectType(config.getEntityA().getName());
                            String targetObjectType = entityNameToSegmentObjectType(config.getEntityB().getName());
                            SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                                entityAId.intValue(), sourceObjectType, entityBId.intValue(), targetObjectType, conn);
                            if (!segmentResult.isValid) {
                                row.addError(segmentResult.message);
                                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                                continue;
                            }
                        } catch (SQLException e) {
                            logger.error("Row {}: Error validating cross-segment relationship: {}", row.getRowNumber(), e.getMessage(), e);
                            row.addError("Could not validate segment access: " + e.getMessage());
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    // Do not delete relationship if either object is locked
                    // For glossary_x_system (System X Glossary), skip lock check on Entity A (glossary): deletion is from system facet, so glossary lock does not block
                    if (!"glossary_x_system".equals(config.getTableName()) && entityAId != null && entityAId > 0) {
                        String lockError = checkObjectLocked(config.getEntityA().getName(), entityAId.intValue(), userId);
                        if (lockError != null) {
                            row.addError("Cannot delete relationship: " + lockError);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                    if (entityBId != null && entityBId > 0) {
                        String lockError = checkObjectLocked(config.getEntityB().getName(), entityBId.intValue(), userId);
                        if (lockError != null) {
                            row.addError("Cannot delete relationship: " + lockError);
                            handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                            continue;
                        }
                    }
                }
                
                // Check if relationship exists (for DELETE operation; for glossary_x_system include dataset in match)
                Integer datasetIdForExists = "glossary_x_system".equals(config.getTableName())
                    ? row.getResolvedEntityIds().get("dataset") : null;
                DuplicateCheckResult existenceResult = ExistenceChecker.checkExists(
                    conn, config.getTableName(),
                    config.getEntityAIdColumn(), config.getEntityBIdColumn(),
                    config.getRelationTypeColumn(),
                    row.getResolvedEntityIds(),
                    row.getRelationshipTypeId(),
                    row.getRowNumber(),
                    datasetIdForExists
                );
                
                if (!existenceResult.isDuplicate()) { // isDuplicate() means "exists" in this context
                    row.addWarning("No relationship exists for this combination in the database - cannot delete.");
                    handleRowWarning(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Row is valid and relationship exists
                row.setValid(true);
                validRows.add(row);
            }
            
            logger.info("Validation complete: {} valid, {} skipped", validRows.size(), skippedRows.size());
            
            jobDAO.createJobProgress(jobId, 50, "Deleting", "Deleting relationships...");
            
            // If cancel on warning and there were errors/warnings, do not delete; generate report and return
            if (cancelOnWarning && (uploadResponse.hasErrors() || uploadResponse.hasWarnings())) {
                if (conn != null) {
                    try {
                        conn.rollback();
                        logger.info("Transaction rolled back due to validation errors (cancel on warning)");
                    } catch (SQLException ex) {
                        logger.error("Error rolling back transaction", ex);
                    }
                }
                uploadResponse.setSuccess(false);
                uploadResponse.setMessage("Upload cancelled due to validation errors");
                uploadResponse.setSkipped(skippedRows.size());
                uploadResponse.setFailed(skippedRows.size());
                uploadResponse.setInserted(0);
                uploadResponse.setDeleted(0);
                uploadResponse.setAllProcessedRows(allProcessedRows);
                generateReportFromRows(allProcessedRows, jobId, config, "DELETE", userId, columnMappings);
                updateJobStatus(jobId, uploadResponse, "DELETE");
                return uploadResponse;
            }
            
            // Phase 2: Delete valid rows
            if (!validRows.isEmpty()) {
                logger.info("Phase 2: Deleting {} relationships...", validRows.size());
                
                int actualDeletedCount;
                if (validRows.size() > BATCH_THRESHOLD) {
                    actualDeletedCount = deleteBatch(conn, validRows, config, jobId);
                } else {
                    actualDeletedCount = 0;
                    String noMatchWarning = "No matching relationship found to delete (0 rows affected). The specified entities may not be linked, or the identifiers may not match.";
                    for (RelationshipRowData row : validRows) {
                        if (deleteSingle(conn, row, config, jobId)) {
                            actualDeletedCount++;
                        } else {
                            row.addWarning(noMatchWarning);
                        }
                    }
                }
                
                conn.commit();
                logger.info("Successfully committed {} relationship deletions ({} rows actually deleted)", validRows.size(), actualDeletedCount);
                
                uploadResponse.setSuccess(true);
                uploadResponse.setDeleted(actualDeletedCount);
                uploadResponse.setInserted(0);
            } else {
                logger.warn("No valid rows to delete");
                uploadResponse.setSuccess(false);
                uploadResponse.setMessage("No valid rows to delete");
                uploadResponse.setInserted(0);
                uploadResponse.setDeleted(0);
            }
            
            uploadResponse.setSkipped(skippedRows.size());
            uploadResponse.setFailed(skippedRows.size());
            uploadResponse.setCacheStats(cacheStats);
            
            // Store all processed rows for report generation
            uploadResponse.setAllProcessedRows(allProcessedRows);
            
        } catch (SQLException sqlEx) {
            logger.error("Database error during delete operation", sqlEx);
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("Transaction rolled back due to error");
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            uploadResponse.setSuccess(false);
            uploadResponse.setMessage("Database error: " + sqlEx.getMessage());
            uploadResponse.setSkipped(skippedRows != null ? skippedRows.size() : 0);
            uploadResponse.setFailed(skippedRows != null ? skippedRows.size() : 0);
            uploadResponse.setInserted(0);
            uploadResponse.setDeleted(0);
            if (allProcessedRows.isEmpty() && rows != null && !rows.isEmpty()) {
                allProcessedRows.addAll(rows);
            }
            uploadResponse.setAllProcessedRows(allProcessedRows);
            if (uploadResponse.getFailed() == 0) {
                int errorAndWarningCount = uploadResponse.getErrors().size() + uploadResponse.getWarnings().size();
                uploadResponse.setFailed(Math.max(1, errorAndWarningCount > 0 ? errorAndWarningCount : 1));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during delete operation", ex);
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("Transaction rolled back due to unexpected error");
                } catch (SQLException rollbackEx) {
                    logger.error("Error rolling back transaction", rollbackEx);
                }
            }
            uploadResponse.setSuccess(false);
            uploadResponse.setMessage("Unexpected error: " + ex.getMessage());
            uploadResponse.setSkipped(skippedRows != null ? skippedRows.size() : 0);
            uploadResponse.setFailed(skippedRows != null ? skippedRows.size() : 0);
            uploadResponse.setInserted(0);
            uploadResponse.setDeleted(0);
            if (allProcessedRows.isEmpty() && rows != null && !rows.isEmpty()) {
                allProcessedRows.addAll(rows);
            }
            uploadResponse.setAllProcessedRows(allProcessedRows);
            if (uploadResponse.getFailed() == 0) {
                int errorAndWarningCount = uploadResponse.getErrors().size() + uploadResponse.getWarnings().size();
                uploadResponse.setFailed(Math.max(1, errorAndWarningCount > 0 ? errorAndWarningCount : 1));
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    logger.error("Error closing connection", closeEx);
                }
            }
        }
        
        return uploadResponse;
    }
    
    /**
     * Process UPDATE operation for Segment X Object
     * Moves objects from their current segment (or assigns if no segment) to the target segment
     */
    private BulkUploadResponse processSegmentXObjectUpdate(List<RelationshipRowData> rows, RelationshipConfig config,
                                                          int userId, boolean cancelOnWarning, int jobId, Map<String, String> columnMappings) {
        
        BulkUploadResponse uploadResponse = new BulkUploadResponse();
        uploadResponse.setTotalRows(rows.size());
        
        List<RelationshipRowData> allProcessedRows = new ArrayList<>();
        List<RelationshipRowData> validRows = new ArrayList<>();
        List<RelationshipRowData> skippedRows = new ArrayList<>();
        
        Connection conn = null;
        
        logger.info("Processing Segment X Object UPDATE for {} rows...", rows.size());
        
        try {
            conn = getConnectionWithRetry(3);
            conn.setAutoCommit(false);
            
        // Phase 1: Validate and parse rows
        allProcessedRows.addAll(rows);
        for (RelationshipRowData row : rows) {
            try {
                // Pre-validate
                RelationshipExcelParser.preValidateRow(row, config, columnMappings);
                
                if (row.hasErrors()) {
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Extract values from row using helper method that respects column mappings
                String objectIdStr = findFieldValue(row, "Object ID", columnMappings);
                String objectType = findFieldValue(row, "Object Type", columnMappings);
                String targetSegmentName = findFieldValue(row, "Target Segment", columnMappings);
                
                // Validate required fields
                if (objectIdStr == null || objectIdStr.trim().isEmpty()) {
                    row.addError("Object ID is required");
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                if (objectType == null || objectType.trim().isEmpty()) {
                    row.addError("Object Type is required");
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                if (targetSegmentName == null || targetSegmentName.trim().isEmpty()) {
                    row.addError("Target Segment is required");
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Parse Object ID
                Long objectId;
                try {
                    objectId = Long.parseLong(objectIdStr.trim());
                } catch (NumberFormatException e) {
                    row.addError("Invalid Object ID: " + objectIdStr);
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Resolve Target Segment ID by name
                Long targetSegmentId = resolveSegmentIdByName(targetSegmentName.trim());
                if (targetSegmentId == null) {
                    row.addError("Target Segment '" + targetSegmentName + "' not found");
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    continue;
                }
                
                // Store resolved values in row for processing
                // CRITICAL: Explicit null checks before calling intValue() to prevent NullPointerException
                // Note: objectId is guaranteed non-null (from Long.parseLong() which throws if invalid)
                // and targetSegmentId is already validated above (line 1234) - if null, we continue
                // Safe to call intValue() - both values are guaranteed non-null at this point
                row.addResolvedEntityId("objectId", objectId.intValue());
                row.addResolvedEntityId("targetSegmentId", targetSegmentId.intValue());
                row.getOriginalValues().put("resolvedObjectType", objectType);
                
                // Row is valid
                row.setValid(true);
                validRows.add(row);
                
            } catch (Exception e) {
                logger.error("Error processing row {}: {}", row.getRowNumber(), e.getMessage(), e);
                row.addError("Error processing row: " + e.getMessage());
                handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
            }
        }
        
        logger.info("Validation complete: {} valid, {} skipped", validRows.size(), skippedRows.size());
        
        // If cancel on warning and there were errors/warnings, do not process; generate report and return
        if (cancelOnWarning && (uploadResponse.hasErrors() || uploadResponse.hasWarnings())) {
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("Transaction rolled back due to validation errors (cancel on warning)");
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            uploadResponse.setSuccess(false);
            uploadResponse.setMessage("Upload cancelled due to validation errors");
            uploadResponse.setSkipped(skippedRows.size());
            uploadResponse.setFailed(skippedRows.size());
            uploadResponse.setAllProcessedRows(allProcessedRows);
            generateReportFromRows(allProcessedRows, jobId, config, "UPDATE", userId, columnMappings);
            updateJobStatus(jobId, uploadResponse, "UPDATE");
            return uploadResponse;
        }
        
        // Phase 2: Move objects to target segments
        if (!validRows.isEmpty()) {
            logger.info("Phase 2: Moving {} objects to target segments...", validRows.size());
            
            int successCount = 0;
            int errorCount = 0;
            
            for (RelationshipRowData row : validRows) {
                try {
                    Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                    if (resolvedIds == null) {
                        logger.error("Row {}: resolvedEntityIds map is null - cannot process", row.getRowNumber());
                        row.addError("Internal error: resolved entity IDs map is null");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        errorCount++;
                        continue;
                    }
                    
                    Integer objectIdInt = resolvedIds.get("objectId");
                    Integer targetSegmentIdInt = resolvedIds.get("targetSegmentId");
                    
                    if (objectIdInt == null || targetSegmentIdInt == null) {
                        logger.error("Row {}: Missing required IDs - objectId={}, targetSegmentId={}", 
                            row.getRowNumber(), objectIdInt, targetSegmentIdInt);
                        row.addError("Internal error: Missing object ID or target segment ID");
                        handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                        errorCount++;
                        continue;
                    }
                    
                    Long objectId = (long) objectIdInt;
                    Long targetSegmentId = (long) targetSegmentIdInt;
                    String objectType = row.getOriginalValues().get("resolvedObjectType");
                    
                    // Get current segment
                    Long currentSegmentId = ObjectSegmentService.getObjectSegment(objectId, objectType);
                    
                    // Only validate and assign if segment is changing
                    if (currentSegmentId == null || !currentSegmentId.equals(targetSegmentId)) {
                        // Get parent ID for validation
                        Integer parentId = getObjectParentId(conn, objectIdInt, objectType);
                        
                        // Validate segment change
                        SegmentValidationService validator = new SegmentValidationService();
                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                            objectIdInt, targetSegmentIdInt, objectType, parentId);
                        
                        if (!result.isValid && !result.canProceedWithWarning) {
                            throw new RuntimeException("Segment validation failed: " + result.message);
                        }
                        
                        // Move object to target segment (this will soft-delete old assignment and create new one)
                        ObjectSegmentService.assignObjectToSegment(conn, objectId, objectType, targetSegmentId, userId);
                        
                        logger.info("Moved {} #{} to segment {} (validated)", objectType, objectId, targetSegmentId);
                    } else {
                        logger.debug("Object {} #{} already in segment {}, skipping", objectType, objectId, targetSegmentId);
                    }
                    
                    successCount++;
                    
                } catch (Exception e) {
                    logger.error("Failed to move object at row {}: {}", row.getRowNumber(), e.getMessage(), e);
                    row.addError("Failed to move object: " + e.getMessage());
                    handleRowError(row, uploadResponse, skippedRows, cancelOnWarning, jobId);
                    errorCount++;
                }
            }
            
            uploadResponse.setSuccess(successCount > 0);
            uploadResponse.setInserted(successCount);
            uploadResponse.setSkipped(skippedRows.size());
            uploadResponse.setFailed(skippedRows.size());
            uploadResponse.setAllProcessedRows(allProcessedRows);
            
            logger.info("Segment X Object UPDATE completed: {} moved, {} errors, {} skipped", 
                successCount, errorCount, skippedRows.size());
        } else {
            logger.warn("No valid rows to process");
            uploadResponse.setSuccess(false);
            uploadResponse.setMessage("No valid rows to process");
            uploadResponse.setSkipped(skippedRows.size());
            uploadResponse.setFailed(skippedRows.size());
            uploadResponse.setAllProcessedRows(allProcessedRows);
        }
        
        // Commit transaction
        if (conn != null) {
            try {
                conn.commit();
                logger.info("Transaction committed successfully");
            } catch (SQLException e) {
                logger.error("Error committing transaction", e);
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        logger.error("Error rolling back transaction", rollbackEx);
                    }
                }
                throw new RuntimeException("Failed to commit transaction: " + e.getMessage(), e);
            } finally {
                try {
                    if (conn != null) {
                        conn.setAutoCommit(true);
                        conn.close();
                    }
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
        
        } catch (Exception e) {
            logger.error("Error in processSegmentXObjectUpdate", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.error("Error rolling back transaction", rollbackEx);
                } finally {
                    try {
                        if (conn != null) {
                            conn.setAutoCommit(true);
                            conn.close();
                        }
                    } catch (SQLException closeEx) {
                        logger.error("Error closing connection", closeEx);
                    }
                }
            }
            uploadResponse.setSuccess(false);
            uploadResponse.setMessage("Error processing update: " + e.getMessage());
        }
        
        return uploadResponse;
    }
    
    /**
     * Resolve segment ID by segment name
     */
    private Long resolveSegmentIdByName(String segmentName) {
        String sql = "SELECT ID FROM segment WHERE LOWER(Name) = LOWER(?) AND Deleted_At IS NULL";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, segmentName);
            
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error resolving segment ID for name '{}': {}", segmentName, e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Find description value in row data, respecting column mappings
     * Uses pattern matching to find description column based on config
     * 
     * @param row Row data containing original Excel values
     * @param config Relationship configuration
     * @param columnMappings Map of Excel column name -> Field name (can be null)
     * @return Description value or null if not found
     */
    private String findDescriptionValue(RelationshipRowData row, RelationshipConfig config, Map<String, String> columnMappings) {
        Map<String, String> originalValues = row.getOriginalValues();
        if (originalValues == null || originalValues.isEmpty()) {
            return null;
        }
        
        String descriptionColumn = config.getDescriptionColumn();
        if (descriptionColumn == null || descriptionColumn.isEmpty()) {
            return null;
        }
        
        // Normalize database column name to Excel-friendly patterns
        // e.g., "Sourcing_Logic" -> "Sourcing Logic", "Description" -> "Description"
        String normalizedDbColumn = descriptionColumn.replace("_", " ");
        
        // 1. Direct lookup by normalized database column name
        String value = originalValues.get(normalizedDbColumn);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        
        // Case-insensitive lookup
        for (Map.Entry<String, String> entry : originalValues.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(normalizedDbColumn)) {
                String val = entry.getValue();
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        }
        
        // 2. Lookup by mapped field name (if column mappings exist)
        if (columnMappings != null && !columnMappings.isEmpty()) {
            // Check if any mapped field name matches the description column
            for (Map.Entry<String, String> mapping : columnMappings.entrySet()) {
                String mappedFieldName = mapping.getValue();
                String excelColumnName = mapping.getKey();
                
                // Check if mapped field name matches the description column (normalized)
                if (mappedFieldName != null && 
                    (mappedFieldName.equalsIgnoreCase(descriptionColumn) || 
                     mappedFieldName.equalsIgnoreCase(normalizedDbColumn))) {
                    // Look up by Excel column name
                    value = originalValues.get(excelColumnName);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        }
        
        // 3. Pattern matching (case-insensitive, handles variations)
        String lowerDescriptionColumn = descriptionColumn.toLowerCase().replace("_", " ");
        for (Map.Entry<String, String> entry : originalValues.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            
            String lowerKey = key.toLowerCase().replaceAll("\\s+", " ");
            
            // Check if key contains the description column name (normalized)
            if (lowerKey.contains(lowerDescriptionColumn) || lowerDescriptionColumn.contains(lowerKey)) {
                String val = entry.getValue();
                if (val != null && !val.trim().isEmpty()) {
                    logger.debug("Row {}: Found description '{}' using pattern matching for '{}'", 
                        row.getRowNumber(), key, descriptionColumn);
                    return val.trim();
                }
            }
        }
        
        // 4. Common description column name patterns
        String[] commonPatterns = {"Description", "description", "Desc", "desc"};
        for (String pattern : commonPatterns) {
            value = originalValues.get(pattern);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        
        // 5. Not found
        logger.debug("Row {}: Could not find description column '{}'. Available columns: {}", 
            row.getRowNumber(), descriptionColumn, String.join(", ", originalValues.keySet()));
        return null;
    }
    
    /**
     * Find field value in row data, respecting column mappings
     * Checks in order: mapped field name, standard field name, pattern matching
     * 
     * @param row Row data containing original Excel values
     * @param fieldName Standard field name to look for (e.g., "Object ID", "Object Type")
     * @param columnMappings Map of Excel column name -> Field name (can be null)
     * @return Field value or null if not found
     */
    private String findFieldValue(RelationshipRowData row, String fieldName, Map<String, String> columnMappings) {
        Map<String, String> originalValues = row.getOriginalValues();
        if (originalValues == null || originalValues.isEmpty()) {
            return null;
        }
        
        // 1. Direct lookup by standard field name (case-sensitive first, then case-insensitive)
        String value = originalValues.get(fieldName);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        
        // Case-insensitive lookup
        for (Map.Entry<String, String> entry : originalValues.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(fieldName)) {
                String val = entry.getValue();
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        }
        
        // 2. Lookup by mapped field name (if column mappings exist)
        if (columnMappings != null && !columnMappings.isEmpty()) {
            // Check if any mapped field name matches the standard field name
            for (Map.Entry<String, String> mapping : columnMappings.entrySet()) {
                String mappedFieldName = mapping.getValue();
                String excelColumnName = mapping.getKey();
                
                // Check if mapped field name matches the field we're looking for
                if (mappedFieldName != null && mappedFieldName.equalsIgnoreCase(fieldName)) {
                    // Look up by Excel column name
                    value = originalValues.get(excelColumnName);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        }
        
        // 3. Pattern matching (case-insensitive, handles variations)
        String lowerFieldName = fieldName.toLowerCase().replaceAll("\\s+", "");
        for (Map.Entry<String, String> entry : originalValues.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            
            String lowerKey = key.toLowerCase().replaceAll("\\s+", "");
            
            // Check if key contains the field name (normalized)
            if (lowerKey.contains(lowerFieldName) || lowerFieldName.contains(lowerKey)) {
                String val = entry.getValue();
                if (val != null && !val.trim().isEmpty()) {
                    logger.debug("Row {}: Found field '{}' using pattern matching for '{}'", 
                        row.getRowNumber(), key, fieldName);
                    return val.trim();
                }
            }
        }
        
        // 4. Not found
        logger.debug("Row {}: Could not find field '{}'. Available columns: {}", 
            row.getRowNumber(), fieldName, String.join(", ", originalValues.keySet()));
        return null;
    }
    
    /**
     * Resolve entities for a row
     * 
     * @param conn Database connection
     * @param row Row data containing original Excel values
     * @param config Relationship configuration
     * @param cache Entity cache
     * @param cacheStats Cache statistics
     * @param userId User ID
     * @param columnMappings Optional column mappings from frontend (Excel column name -> Field name)
     * @return True if entities resolved successfully, false otherwise
     */
    private boolean resolveEntitiesForRow(Connection conn, RelationshipRowData row, RelationshipConfig config,
                                         Map<String, Integer> cache, CacheStats cacheStats, int userId,
                                         Map<String, String> columnMappings) {
        // Resolve Entity A
        EntityConfig entityAConfig = config.getEntityA();
        // Use Excel-friendly column names instead of database column names (track for same-entity same-column validation)
        String refColumnNameA = null;
        String refA = null;
        if (entityAConfig.hasRefColumn()) {
            refColumnNameA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "ref", columnMappings, config.getEntityARole());
            if (refColumnNameA != null) {
                refA = row.getOriginalValues().get(refColumnNameA);
            } else {
                logger.debug("Row {}: Could not find {} reference column in Excel file. Available columns: {}", 
                    row.getRowNumber(), entityAConfig.getName(), 
                    String.join(", ", row.getOriginalValues().keySet()));
            }
        }
        String nameColumnNameA = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "name", columnMappings, config.getEntityARole());
        String nameA = nameColumnNameA != null ? row.getOriginalValues().get(nameColumnNameA) : null;
        if (nameColumnNameA == null && entityAConfig.isRequired()) {
            // Log available columns for debugging when required column is missing
            String availableColumns = String.join(", ", row.getOriginalValues().keySet());
            logger.warn("Row {}: Could not find {} (Entity A) name column in Excel file. This is required for entity resolution. " +
                "Looking for column patterns like '{} Name' or '{} Ref'. Available columns: {}", 
                row.getRowNumber(), entityAConfig.getName(), entityAConfig.getName(), entityAConfig.getName(), availableColumns);
            // Add detailed error message to help user identify the issue
            row.addError(String.format(
                "Missing required column for %s (Entity A): Could not find a column matching '%s Name' or '%s Ref' patterns. " +
                "Available columns in Excel: %s. Please ensure your Excel file has a column named '%s Name' or '%s Ref'.",
                entityAConfig.getName(), entityAConfig.getName(), entityAConfig.getName(), availableColumns,
                entityAConfig.getName(), entityAConfig.getName()));
        }
        String parentA = null;
        if (entityAConfig.hasParentColumn()) {
            String parentColumnName = RelationshipExcelParser.findExcelColumnName(row, entityAConfig, "parent", columnMappings, config.getEntityARole());
            if (parentColumnName != null) {
                parentA = row.getOriginalValues().get(parentColumnName);
            }
        }
        // Process X Data Set: fallback when Process (Entity A) name/ref/parent column not found by standard matching
        if ("process_x_dataset".equals(config.getTableName()) && (nameColumnNameA == null || (entityAConfig.hasRefColumn() && refColumnNameA == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("process") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                if (lowerKey.contains("process") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                if (entityAConfig.hasParentColumn() && lowerKey.contains("parent") && lowerKey.contains("process") && lowerKey.contains("name")) {
                    if (parentA == null || parentA.trim().isEmpty()) {
                        parentA = val.trim();
                    }
                }
            }
        }
        // Process X Glossary (glossary_x_process): fallback when Glossary (Entity A) name/ref/parent column not found by standard matching
        if ("glossary_x_process".equals(config.getTableName()) && (nameColumnNameA == null || (entityAConfig.hasRefColumn() && refColumnNameA == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("glossary") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                if (lowerKey.contains("glossary") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                if (entityAConfig.hasParentColumn() && lowerKey.contains("parent") && lowerKey.contains("glossary") && lowerKey.contains("name")) {
                    if (parentA == null || parentA.trim().isEmpty()) {
                        parentA = val.trim();
                    }
                }
            }
        }
        // Process X Legal Entity: fallback when Process (Entity A) name/ref/parent column not found by standard matching
        if ("process_x_legal".equals(config.getTableName()) && (nameColumnNameA == null || (entityAConfig.hasRefColumn() && refColumnNameA == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("process") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                if (lowerKey.contains("process") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                if (entityAConfig.hasParentColumn() && lowerKey.contains("parent") && lowerKey.contains("process") && lowerKey.contains("name")) {
                    if (parentA == null || parentA.trim().isEmpty()) {
                        parentA = val.trim();
                    }
                }
            }
        }
        // Project X Process: Entity A (project) ref/name only from columns containing "project" (and ref/name), not "process", so process value is never sent to project resolver
        if ("project_x_process".equals(config.getTableName())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("project") && !lowerKey.contains("process") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    refA = val.trim();
                }
                if (lowerKey.contains("project") && !lowerKey.contains("process") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    nameA = val.trim();
                }
            }
        }
        // Project X Product: fallback when Product (Entity A) name column not found by standard matching (Product has no ref column)
        if ("product_x_project".equals(config.getTableName()) && nameColumnNameA == null) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("product") && lowerKey.contains("name") && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    nameA = val.trim();
                    nameColumnNameA = key;
                    break;
                }
            }
        }
        // Project X Project: fallback when Source Project (Entity A) ref/name column not found by standard matching
        if ("project_x_project".equals(config.getTableName()) && (nameColumnNameA == null || (entityAConfig.hasRefColumn() && refColumnNameA == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("source") && lowerKey.contains("project") && (lowerKey.contains("ref") || lowerKey.contains("reference")) && !lowerKey.contains("relationship")) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                if (lowerKey.contains("source") && lowerKey.contains("project") && lowerKey.contains("name") && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
            }
        }
        // Regulation X Project: fill-when-null so mapping works when findExcelColumnName did not find columns (e.g. "Reg Ref" or "Proj Ref")
        if ("regulation_x_project".equals(config.getTableName()) && (refA == null || refA.trim().isEmpty() || nameA == null || nameA.trim().isEmpty())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                boolean isReg = lowerKey.contains("regulation") || (lowerKey.contains("reg") && !lowerKey.contains("policy") && !lowerKey.contains("project"));
                if (isReg && !lowerKey.contains("policy") && !lowerKey.contains("parent") && !lowerKey.contains("project") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                if (isReg && !lowerKey.contains("policy") && !lowerKey.contains("parent") && !lowerKey.contains("project") && lowerKey.contains("name")) {
                    if (nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
            }
        }
        if ("regulation_x_project".equals(config.getTableName()) && (refColumnNameA == null || nameColumnNameA == null)) {
            logger.debug("Row {}: Regulation X Project - Entity A (Regulation) column missing. refColumn={}, nameColumn={}, availableKeys=[{}], columnMappings={}",
                row.getRowNumber(), refColumnNameA, nameColumnNameA, String.join(", ", row.getOriginalValues().keySet()),
                columnMappings != null ? columnMappings.toString() : "null");
        }
        // Regulation X Policy: fill-when-null so mapping works when findExcelColumnName did not find columns (e.g. "Reg Ref" without "regulation")
        if ("regulation_x_policy".equals(config.getTableName()) && (refA == null || refA.trim().isEmpty() || nameA == null || nameA.trim().isEmpty())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                boolean isReg = lowerKey.contains("regulation") || (lowerKey.contains("reg") && !lowerKey.contains("policy"));
                if (isReg && !lowerKey.contains("policy") && !lowerKey.contains("parent") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                if (isReg && !lowerKey.contains("policy") && !lowerKey.contains("parent") && lowerKey.contains("name")) {
                    if (nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
            }
        }
        // Regulation X Policy: diagnostic log when Regulation columns not found (for "mapping doesn't happen" debugging)
        if ("regulation_x_policy".equals(config.getTableName()) && (refColumnNameA == null || nameColumnNameA == null)) {
            logger.debug("Row {}: Regulation X Policy - Entity A (Regulation) column missing. refColumn={}, nameColumn={}, availableKeys=[{}], columnMappings={}",
                row.getRowNumber(), refColumnNameA, nameColumnNameA, String.join(", ", row.getOriginalValues().keySet()),
                columnMappings != null ? columnMappings.toString() : "null");
        }
        // Regulation X Policy: Entity A (Regulation) ref/name only from columns containing "regulation" (and ref/name), not "policy", so policy value is never sent to regulation resolver
        if ("regulation_x_policy".equals(config.getTableName())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("regulation") && !lowerKey.contains("policy") && !lowerKey.contains("parent") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    refA = val.trim();
                }
                if (lowerKey.contains("regulation") && !lowerKey.contains("policy") && !lowerKey.contains("parent") && lowerKey.contains("name")) {
                    nameA = val.trim();
                }
            }
        }
        // Regulation X Regulator: fallback when Regulation (Entity A) ref/name column not found by standard matching
        if ("regulation_x_regulator".equals(config.getTableName()) && (nameColumnNameA == null || (entityAConfig.hasRefColumn() && refColumnNameA == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("regulation") && !lowerKey.contains("policy") && !lowerKey.contains("parent") && !lowerKey.contains("regulator") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                if (lowerKey.contains("regulation") && !lowerKey.contains("policy") && !lowerKey.contains("parent") && !lowerKey.contains("regulator") && lowerKey.contains("name")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
            }
        }
        // Regulation X Regulatory Theme: fallback when Regulation (Entity A) ref/name column not found by standard matching
        if ("regulation_x_regulatorytheme".equals(config.getTableName()) && (nameColumnNameA == null || (entityAConfig.hasRefColumn() && refColumnNameA == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("regulation") && !lowerKey.contains("policy") && !lowerKey.contains("parent") && !lowerKey.contains("regulatorytheme") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                if (lowerKey.contains("regulation") && !lowerKey.contains("policy") && !lowerKey.contains("parent") && !lowerKey.contains("regulatorytheme") && lowerKey.contains("name")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
            }
        }
        
        EntityResolutionResult resultA = entityResolverService.resolveEntity(
            conn, entityAConfig, refA, nameA, parentA, row.getRowNumber(), cache, cacheStats
        );
        
        if (resultA.hasError()) {
            row.addError(resultA.getError());
            return false;
        }
        
        // Check if required entity A was resolved successfully
        if (entityAConfig.isRequired()) {
            if (resultA.getId() == null) {
                // Required entity not found - convert warning to error
                String identifier = nameA != null ? nameA : (refA != null ? refA : "not provided");
                String errorMsg;
                if ("process_x_legal".equals(config.getTableName()) && (identifier == null || identifier.trim().isEmpty() || "not provided".equals(identifier))) {
                    errorMsg = "Process (Entity A) is required but not provided. Ensure the file is the Process X Legal Entity template (Process Ref., Process Name, Parent Process Name, Legal Short Name, Parent Legal Short Name). If your file has Policy columns (e.g. Policy Ref., Policy Name), use the Policy X Legal Entity template or select that relationship type.";
                } else {
                    errorMsg = resultA.hasWarning() ? resultA.getWarning() : 
                        String.format("%s (Entity A) not found: No %s found with identifier '%s' in the database. " +
                            "Please verify the identifier is correct and the %s exists in the system. " +
                            "If you provided column mappings, ensure the Excel column names match the expected patterns (e.g., '%s Name' or '%s Ref').",
                            entityAConfig.getName(), entityAConfig.getName().toLowerCase(), 
                            identifier, entityAConfig.getName().toLowerCase(),
                            entityAConfig.getName(), entityAConfig.getName());
                }
                logger.warn("Row {}: Required Entity A ({}) not found. Identifier: '{}', Available columns: {}", 
                    row.getRowNumber(), entityAConfig.getName(), identifier,
                    String.join(", ", row.getOriginalValues().keySet()));
                row.addError(errorMsg);
                return false;
            }
        }
        
        if (resultA.hasWarning()) {
            row.addWarning(resultA.getWarning());
        }
        if (resultA.getId() != null) {
            // CRITICAL: Only add non-null IDs to prevent NullPointerException when intValue() is called later
            row.addResolvedEntityId("entityA", resultA.getId());
            logger.debug("Row {}: Successfully resolved Entity A ({}) with ID: {}", 
                row.getRowNumber(), entityAConfig.getName(), resultA.getId());
        } else {
            if (!entityAConfig.isRequired()) {
                logger.debug("Row {}: Entity A ({}) was not resolved (optional entity, ID is null)", 
                    row.getRowNumber(), entityAConfig.getName());
            } else {
                logger.warn("Row {}: Required Entity A ({}) was not resolved - ID is null. This should have been caught earlier.",
                    row.getRowNumber(), entityAConfig.getName());
            }
        }
        
        // Resolve Entity B
        EntityConfig entityBConfig = config.getEntityB();
        // Use Excel-friendly column names instead of database column names
        String refB = null;
        String refColumnNameB = null;
        if (entityBConfig.hasRefColumn()) {
            refColumnNameB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "ref", columnMappings, config.getEntityBRole());
            if (refColumnNameB != null) {
                refB = row.getOriginalValues().get(refColumnNameB);
            } else {
                logger.debug("Row {}: Could not find {} reference column in Excel file. Available columns: {}", 
                    row.getRowNumber(), entityBConfig.getName(), 
                    String.join(", ", row.getOriginalValues().keySet()));
            }
        }
        String nameColumnNameB = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "name", columnMappings, config.getEntityBRole());
        String nameB = nameColumnNameB != null ? row.getOriginalValues().get(nameColumnNameB) : null;
        // Project X Product: diagnostic log when Entity B (project) name/ref column not found (for "mapping doesn't happen" debugging)
        if ("product_x_project".equals(config.getTableName()) && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            logger.debug("Row {}: Project X Product - Entity B (project) column missing. nameColumn={}, refColumn={}, availableKeys=[{}], columnMappings={}",
                row.getRowNumber(), nameColumnNameB, refColumnNameB, String.join(", ", row.getOriginalValues().keySet()),
                columnMappings != null ? columnMappings.toString() : "null");
        }
        // Project X Product: fallback when Project (Entity B) name/ref column not found by standard matching (applies to both INSERT and DELETE via shared resolution)
        if ("product_x_project".equals(config.getTableName()) && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("project") && (lowerKey.contains("ref") || lowerKey.contains("reference")) && !lowerKey.contains("relationship")) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
                if (lowerKey.contains("project") && lowerKey.contains("name") && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        // Project X Project: fallback when Target Project (Entity B) ref/name column not found by standard matching
        if ("project_x_project".equals(config.getTableName()) && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("target") && lowerKey.contains("project") && (lowerKey.contains("ref") || lowerKey.contains("reference")) && !lowerKey.contains("relationship")) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
                if (lowerKey.contains("target") && lowerKey.contains("project") && lowerKey.contains("name") && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        // Regulation X Regulator: fallback when Regulator (Entity B) name column not found by standard matching (regulator has no ref)
        if ("regulation_x_regulator".equals(config.getTableName()) && nameColumnNameB == null) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("regulator") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    nameB = val.trim();
                    nameColumnNameB = key;
                    break;
                }
            }
        }
        // Regulation X Regulatory Theme: fallback when Regulatory Theme (Entity B) ref/name column not found by standard matching
        if ("regulation_x_regulatorytheme".equals(config.getTableName()) && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("regulatory") && lowerKey.contains("theme") && !lowerKey.contains("parent") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
                if (lowerKey.contains("regulatory") && lowerKey.contains("theme") && !lowerKey.contains("parent") && lowerKey.contains("name")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        // Process X Product: diagnostic log when Entity B (process) name/ref column not found (for "mapping doesn't happen" debugging)
        if ("product_x_process".equals(config.getTableName()) && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            logger.debug("Row {}: Process X Product - Entity B (process) column missing. nameColumn={}, refColumn={}, availableKeys=[{}], columnMappings={}",
                row.getRowNumber(), nameColumnNameB, refColumnNameB, String.join(", ", row.getOriginalValues().keySet()),
                columnMappings != null ? columnMappings.toString() : "null");
        }
        // Process X Product: fallback when Product or Process name/ref column not found by standard matching (applies to both INSERT and DELETE via shared resolution)
        if ("product_x_process".equals(config.getTableName()) && (nameColumnNameA == null || nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                // Product Name (Entity A): key contains "product" and "name", not "parent"
                if (lowerKey.contains("product") && lowerKey.contains("name") && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                // Process Name (Entity B): key contains "process" and "name", not "relationship"
                if (lowerKey.contains("process") && lowerKey.contains("name") && !lowerKey.contains("relationship") && !lowerKey.contains("parent")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
                // Process Ref. (Entity B): key contains "process" and "ref"/"reference"
                if (entityBConfig.hasRefColumn() && lowerKey.contains("process") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
            }
        }
        // Project X Process: Entity B (process) ref/name only from columns containing "process" (and ref/name), not "parent"
        if ("project_x_process".equals(config.getTableName())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("process") && !lowerKey.contains("parent") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    refB = val.trim();
                }
                if (lowerKey.contains("process") && !lowerKey.contains("parent") && lowerKey.contains("name")) {
                    nameB = val.trim();
                }
            }
        }
        // Regulation X Project: fill-when-null for Entity B (Project) so mapping works when findExcelColumnName did not find columns (e.g. "Proj Ref")
        if ("regulation_x_project".equals(config.getTableName()) && (refB == null || refB.trim().isEmpty() || nameB == null || nameB.trim().isEmpty())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("project") && !lowerKey.contains("parent") && !lowerKey.contains("relationship") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
                if (lowerKey.contains("project") && !lowerKey.contains("parent") && !lowerKey.contains("relationship") && lowerKey.contains("name")) {
                    if (nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        if ("regulation_x_project".equals(config.getTableName()) && (refColumnNameB == null || nameColumnNameB == null)) {
            logger.debug("Row {}: Regulation X Project - Entity B (Project) column missing. refColumn={}, nameColumn={}, availableKeys=[{}], columnMappings={}",
                row.getRowNumber(), refColumnNameB, nameColumnNameB, String.join(", ", row.getOriginalValues().keySet()),
                columnMappings != null ? columnMappings.toString() : "null");
        }
        // Regulation X Policy: fill-when-null for Entity B (Policy) so mapping works when findExcelColumnName did not find columns
        if ("regulation_x_policy".equals(config.getTableName()) && (refB == null || refB.trim().isEmpty() || nameB == null || nameB.trim().isEmpty())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("policy") && !lowerKey.contains("parent") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
                if (lowerKey.contains("policy") && !lowerKey.contains("parent") && lowerKey.contains("name")) {
                    if (nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        // Regulation X Policy: diagnostic log when Policy (Entity B) columns not found (for "mapping doesn't happen" debugging)
        if ("regulation_x_policy".equals(config.getTableName()) && (refColumnNameB == null || nameColumnNameB == null)) {
            logger.debug("Row {}: Regulation X Policy - Entity B (Policy) column missing. refColumn={}, nameColumn={}, availableKeys=[{}], columnMappings={}",
                row.getRowNumber(), refColumnNameB, nameColumnNameB, String.join(", ", row.getOriginalValues().keySet()),
                columnMappings != null ? columnMappings.toString() : "null");
        }
        // Regulation X Policy: Entity B (Policy) ref/name only from columns containing "policy" (and ref/name), not "parent"
        if ("regulation_x_policy".equals(config.getTableName())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("policy") && !lowerKey.contains("parent") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    refB = val.trim();
                }
                if (lowerKey.contains("policy") && !lowerKey.contains("parent") && lowerKey.contains("name")) {
                    nameB = val.trim();
                }
            }
        }
        // Product X Legal: fallback when Legal or Product name column not found by standard matching (applies to both INSERT and DELETE via shared resolution)
        if ("product_x_legal".equals(config.getTableName()) && (nameColumnNameA == null || nameColumnNameB == null)) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("relationship") && lowerKey.contains("type")) continue;
                // Legal Short Name (Entity B): key contains "legal" and ("short" or "name")
                if (lowerKey.contains("legal") && (lowerKey.contains("short") || lowerKey.contains("name")) && !lowerKey.contains("relationship")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
                // Product Name (Entity A): key contains "product" and ("ref" or "name"), not "parent"
                if (lowerKey.contains("product") && (lowerKey.contains("ref") || lowerKey.contains("name")) && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
            }
        }
        // Process X Attribute: fallback when Process or Attribute name/ref column not found by standard matching (applies to both INSERT and DELETE via shared resolution)
        if ("process_x_attribute".equals(config.getTableName()) && (nameColumnNameA == null || (entityAConfig.hasRefColumn() && refColumnNameA == null) || nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                // Process Ref. (Entity A): key contains "process" and "ref"/"reference", not "relationship"
                if (entityAConfig.hasRefColumn() && lowerKey.contains("process") && (lowerKey.contains("ref") || lowerKey.contains("reference")) && !lowerKey.contains("relationship")) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                // Process Name (Entity A): key contains "process" and "name", not "parent"/"relationship"
                if (lowerKey.contains("process") && lowerKey.contains("name") && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                // Parent Process Name (Entity A)
                if (entityAConfig.hasParentColumn() && lowerKey.contains("parent") && lowerKey.contains("process") && lowerKey.contains("name")) {
                    if (parentA == null || parentA.trim().isEmpty()) {
                        parentA = val.trim();
                    }
                }
                // Attribute Ref. (Entity B): key contains "attribute" and "ref"/"reference", not "relationship"
                if (entityBConfig.hasRefColumn() && lowerKey.contains("attribute") && (lowerKey.contains("ref") || lowerKey.contains("reference")) && !lowerKey.contains("relationship")) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
                // Attribute Name (Entity B): key contains "attribute" and "name", not "data set"/"system short"/"relationship"
                if (lowerKey.contains("attribute") && lowerKey.contains("name") && !lowerKey.contains("relationship")) {
                    if (!((lowerKey.contains("data") && lowerKey.contains("set")) || (lowerKey.contains("system") && lowerKey.contains("short")))) {
                        if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                            nameB = val.trim();
                            if (nameColumnNameB == null) nameColumnNameB = key;
                        }
                    }
                }
            }
        }
        // Glossary X Product: diagnostic log when Entity B (glossary) name/ref column not found (for "mapping doesn't happen" debugging)
        if ("product_x_glossary".equals(config.getTableName()) && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            logger.debug("Row {}: Glossary X Product - Entity B (glossary) column missing. nameColumn={}, refColumn={}, availableKeys=[{}], columnMappings={}",
                row.getRowNumber(), nameColumnNameB, refColumnNameB, String.join(", ", row.getOriginalValues().keySet()),
                columnMappings != null ? columnMappings.toString() : "null");
        }
        // Data Set X Product: diagnostic log when Entity A (Product) or Entity B (Data Set) name/ref column not found (for "1-Mapping" debugging)
        if ("product_x_dataset".equals(config.getTableName()) && (nameColumnNameA == null || (entityAConfig.hasRefColumn() && refColumnNameA == null) || nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            logger.debug("Row {}: Data Set X Product - Entity A (Product) or Entity B (Data Set) column missing. nameColumnA={}, refColumnA={}, nameColumnB={}, refColumnB={}, availableKeys=[{}], columnMappings={}",
                row.getRowNumber(), nameColumnNameA, refColumnNameA, nameColumnNameB, refColumnNameB, String.join(", ", row.getOriginalValues().keySet()),
                columnMappings != null ? columnMappings.toString() : "null");
        }
        // Data Set X Product: fallback when Product or Data Set name/ref column not found by standard matching (applies to both INSERT and DELETE via shared resolution)
        if ("product_x_dataset".equals(config.getTableName()) && (nameColumnNameA == null || nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                // Product Name (Entity A): key contains "product" and "name", not "parent"
                if (lowerKey.contains("product") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                // Data Set Name (Entity B): key contains "data" and "set" and "name", not "parent"/"short"
                if (lowerKey.contains("data") && lowerKey.contains("set") && lowerKey.contains("name")
                        && !lowerKey.contains("parent") && !lowerKey.contains("short")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
                // Data Set Ref. (Entity B): key contains "data" and "set" and "ref"
                if (entityBConfig.hasRefColumn() && lowerKey.contains("data") && lowerKey.contains("set") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
            }
        }
        // Product X Business Area: fallback when Product or Business Area name/ref column not found by standard matching (applies to both INSERT and DELETE; ensures mapping is applied)
        if ("product_x_businessarea".equals(config.getTableName()) && (nameColumnNameA == null || nameColumnNameB == null || (entityAConfig.hasRefColumn() && refColumnNameA == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                // Product Ref. (Entity A): key contains "product" and "ref", not "relationship"
                if (entityAConfig.hasRefColumn() && lowerKey.contains("product") && (lowerKey.contains("ref") || lowerKey.contains("reference")) && !lowerKey.contains("relationship")) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                // Product Name (Entity A): key contains "product" and "name", not "parent"/"relationship"
                if (lowerKey.contains("product") && lowerKey.contains("name") && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                // Business Area Name (Entity B): key contains "business" and "area" and "name" (or equivalent), not "parent"
                if (lowerKey.contains("business") && lowerKey.contains("area") && !lowerKey.contains("parent") && !lowerKey.contains("relationship")) {
                    boolean isNameColumn = lowerKey.contains("name") || (!lowerKey.contains("ref") && !lowerKey.contains("reference") && !lowerKey.contains("short"));
                    if (isNameColumn && (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty())) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        // Process X Data Set: fallback when Data Set (Entity B) name/ref column not found by standard matching
        if ("process_x_dataset".equals(config.getTableName()) && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("data") && lowerKey.contains("set") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
                if (lowerKey.contains("data") && lowerKey.contains("set") && lowerKey.contains("name") && !lowerKey.contains("short")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        // Glossary X Glossary: fallback when Source/Target Glossary name or ref column not found by standard matching (applies to both INSERT and DELETE via shared resolution)
        if ("glossary_x_glossary".equals(config.getTableName()) && (nameColumnNameA == null || refColumnNameA == null || nameColumnNameB == null || refColumnNameB == null)) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                // Source Glossary Name (Entity A): key contains "source" and "glossary" and "name", not "parent"
                if (lowerKey.contains("source") && lowerKey.contains("glossary") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                // Source Glossary Ref. (Entity A): key contains "source" and "glossary" and "ref"
                if (lowerKey.contains("source") && lowerKey.contains("glossary") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameA == null || refA == null || refA.trim().isEmpty()) {
                        refA = val.trim();
                        if (refColumnNameA == null) refColumnNameA = key;
                    }
                }
                // Target Glossary Name (Entity B): key contains "target" and "glossary" and "name", not "parent"
                if (lowerKey.contains("target") && lowerKey.contains("glossary") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
                // Target Glossary Ref. (Entity B): key contains "target" and "glossary" and "ref"
                if (lowerKey.contains("target") && lowerKey.contains("glossary") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
            }
        }
        // Regulator X Geography: fallback when Regulator or Geography name column not found by standard matching (applies to both INSERT and DELETE via shared resolution)
        if ("regulator_x_geography".equals(config.getTableName()) && (nameColumnNameA == null || nameColumnNameB == null)) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                // Entity A (Regulator): key contains "regulator" and "name", not "parent"
                if (lowerKey.contains("regulator") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                // Entity B (Geography): key contains "geography" or "geo" and "name"; parent column handled by findExcelColumnName
                if ((lowerKey.contains("geography") || lowerKey.contains("geo")) && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        // People X People: fallback when Manager or Employee name column not found or value empty (header/key variants)
        if ("people_x_people".equals(config.getTableName()) && (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty() || nameColumnNameB == null || nameB == null || nameB.trim().isEmpty())) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                // Entity A (Manager): key contains "manager" and "email"
                if (lowerKey.contains("manager") && lowerKey.contains("email")) {
                    if (nameColumnNameA == null || nameA == null || nameA.trim().isEmpty()) {
                        nameA = val.trim();
                        if (nameColumnNameA == null) nameColumnNameA = key;
                    }
                }
                // Entity B (Employee): key contains "employee" and "email"
                if (lowerKey.contains("employee") && lowerKey.contains("email")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
            }
        }
        // Business Area X Glossary: fallback when Entity B (glossary) name/ref column not found by standard matching
        if ("businessarea_x_glossary".equals(config.getTableName()) && "glossary".equals(entityBConfig.getTableName())
                && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                if (!lowerKey.contains("glossary") || lowerKey.contains("parent")) continue;
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (entityBConfig.hasRefColumn() && (refColumnNameB == null || refB == null || refB.trim().isEmpty())
                        && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    refB = val.trim();
                    if (refColumnNameB == null) refColumnNameB = key;
                }
                if ((nameColumnNameB == null || nameB == null || nameB.trim().isEmpty())
                        && lowerKey.contains("name")) {
                    nameB = val.trim();
                    if (nameColumnNameB == null) nameColumnNameB = key;
                }
            }
        }
        if (nameColumnNameB == null && entityBConfig.isRequired()) {
            // Log available columns for debugging when required column is missing
            String availableColumns = String.join(", ", row.getOriginalValues().keySet());
            logger.warn("Row {}: Could not find {} (Entity B) name column in Excel file. This is required for entity resolution. " +
                "Looking for column patterns like '{} Name' or '{} Ref'. Available columns: {}", 
                row.getRowNumber(), entityBConfig.getName(), entityBConfig.getName(), entityBConfig.getName(), availableColumns);
            
            // Check if the column exists but value is empty (different error)
            boolean columnExistsButEmpty = false;
            for (String colName : row.getOriginalValues().keySet()) {
                String lowerCol = colName.toLowerCase();
                String entityNameLower = entityBConfig.getName().toLowerCase();
                // Check if column name matches entity pattern
                if ((lowerCol.contains("business") && lowerCol.contains("area") && !lowerCol.contains("parent")) ||
                    (entityNameLower.contains("business") && entityNameLower.contains("area") && 
                     lowerCol.contains("business") && lowerCol.contains("area") && !lowerCol.contains("parent"))) {
                    String value = row.getOriginalValues().get(colName);
                    if (value == null || value.trim().isEmpty()) {
                        columnExistsButEmpty = true;
                        logger.debug("Row {}: Found {} column '{}' but value is empty", 
                            row.getRowNumber(), entityBConfig.getName(), colName);
                        break;
                    }
                }
            }
            
            if (!columnExistsButEmpty) {
                // Add detailed error message to help user identify the issue
                row.addError(String.format(
                    "Missing required column for %s (Entity B): Could not find a column matching '%s Name' or '%s Ref' patterns. " +
                    "Available columns in Excel: %s. Please ensure your Excel file has a column named '%s Name' or '%s Ref'.",
                    entityBConfig.getName(), entityBConfig.getName(), entityBConfig.getName(), availableColumns,
                    entityBConfig.getName(), entityBConfig.getName()));
            } else {
                row.addError(String.format(
                    "%s (Entity B) is required but the value is empty. Please ensure the Business Area Name column contains a value.",
                    entityBConfig.getName()));
            }
        } else if (nameColumnNameB != null) {
            logger.debug("Row {}: Found {} (Entity B) name column: '{}' with value: '{}'", 
                row.getRowNumber(), entityBConfig.getName(), nameColumnNameB, nameB);
        }
        String parentB = null;
        if (entityBConfig.hasParentColumn()) {
            String parentColumnName = RelationshipExcelParser.findExcelColumnName(row, entityBConfig, "parent", columnMappings, config.getEntityBRole());
            if (parentColumnName != null) {
                parentB = row.getOriginalValues().get(parentColumnName);
            }
        }
        // Process X Glossary (glossary_x_process): fallback when Process (Entity B) name/ref/parent column not found by standard matching
        if ("glossary_x_process".equals(config.getTableName()) && (nameColumnNameB == null || (entityBConfig.hasRefColumn() && refColumnNameB == null))) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("process") && (lowerKey.contains("ref") || lowerKey.contains("reference"))) {
                    if (refColumnNameB == null || refB == null || refB.trim().isEmpty()) {
                        refB = val.trim();
                        if (refColumnNameB == null) refColumnNameB = key;
                    }
                }
                if (lowerKey.contains("process") && lowerKey.contains("name") && !lowerKey.contains("parent")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
                if (entityBConfig.hasParentColumn() && lowerKey.contains("parent") && lowerKey.contains("process") && lowerKey.contains("name")) {
                    if (parentB == null || parentB.trim().isEmpty()) {
                        parentB = val.trim();
                    }
                }
            }
        }
        // Process X Legal Entity: fallback when Legal (Entity B) name/parent column not found by standard matching (Legal has no ref column)
        if ("process_x_legal".equals(config.getTableName()) && nameColumnNameB == null) {
            Map<String, String> orig = row.getOriginalValues();
            for (String key : orig.keySet()) {
                String lowerKey = key.toLowerCase();
                String val = orig.get(key);
                if (val == null || val.trim().isEmpty()) continue;
                if (lowerKey.contains("legal") && (lowerKey.contains("short") || lowerKey.contains("name")) && !lowerKey.contains("parent")) {
                    if (nameColumnNameB == null || nameB == null || nameB.trim().isEmpty()) {
                        nameB = val.trim();
                        if (nameColumnNameB == null) nameColumnNameB = key;
                    }
                }
                if (entityBConfig.hasParentColumn() && lowerKey.contains("parent") && lowerKey.contains("legal") && (lowerKey.contains("short") || lowerKey.contains("name"))) {
                    if (parentB == null || parentB.trim().isEmpty()) {
                        parentB = val.trim();
                    }
                }
            }
        }
        
        EntityResolutionResult resultB = entityResolverService.resolveEntity(
            conn, entityBConfig, refB, nameB, parentB, row.getRowNumber(), cache, cacheStats
        );
        
        if (resultB.hasError()) {
            row.addError(resultB.getError());
            return false;
        }
        
        // Check if required entity B was resolved successfully
        if (entityBConfig.isRequired()) {
            if (resultB.getId() == null) {
                // Required entity not found - convert warning to error
                String identifier = nameB != null ? nameB : (refB != null ? refB : "not provided");
                String errorMsg;
                if ("businessarea_x_glossary".equals(config.getTableName()) && "glossary".equals(entityBConfig.getTableName())) {
                    errorMsg = "Such glossary doesn't exist in the db";
                } else {
                    errorMsg = resultB.hasWarning() ? resultB.getWarning() : 
                        String.format("%s (Entity B) not found: No %s found with identifier '%s' in the database. " +
                            "Please verify the identifier is correct and the %s exists in the system. " +
                            "If you provided column mappings, ensure the Excel column names match the expected patterns (e.g., '%s Name' or '%s Ref').",
                            entityBConfig.getName(), entityBConfig.getName().toLowerCase(), 
                            identifier, entityBConfig.getName().toLowerCase(),
                            entityBConfig.getName(), entityBConfig.getName());
                }
                logger.warn("Row {}: Required Entity B ({}) not found. Identifier: '{}', Available columns: {}", 
                    row.getRowNumber(), entityBConfig.getName(), identifier,
                    String.join(", ", row.getOriginalValues().keySet()));
                row.addError(errorMsg);
                logger.debug("Row {} now has {} errors", row.getRowNumber(), row.getErrors().size());
                return false;
            }
        }
        
        if (resultB.hasWarning()) {
            row.addWarning(resultB.getWarning());
        }
        if (resultB.getId() != null) {
            // CRITICAL: Only add non-null IDs to prevent NullPointerException when intValue() is called later
            row.addResolvedEntityId("entityB", resultB.getId());
            logger.debug("Row {}: Successfully resolved Entity B ({}) with ID: {}", 
                row.getRowNumber(), entityBConfig.getName(), resultB.getId());
        } else {
            if (!entityBConfig.isRequired()) {
                logger.debug("Row {}: Entity B ({}) was not resolved (optional entity, ID is null)", 
                    row.getRowNumber(), entityBConfig.getName());
            } else {
                logger.warn("Row {}: Required Entity B ({}) was not resolved - ID is null. This should have been caught earlier.",
                    row.getRowNumber(), entityBConfig.getName());
            }
            // Process X Attribute / Policy X Attribute / Project X Attribute: when standard resolver did not set entity B (attribute),
            // try resolving by attribute name + dataset name and/or system short name from the row
            String tableName = config.getTableName();
            if (("process_x_attribute".equals(tableName) || "policy_x_attribute".equals(tableName) || "project_x_attribute".equals(tableName))
                    && "attribute".equals(entityBConfig.getTableName())
                    && nameB != null && !nameB.trim().isEmpty()) {
                String rowDataSetName = getRowValueByColumnPattern(row, "Attribute Data Set", "Data Set Name");
                String rowSystemShortName = getRowValueByColumnPattern(row, "Attribute System", "System Short");
                if ((rowDataSetName != null && !rowDataSetName.trim().isEmpty())
                        || (rowSystemShortName != null && !rowSystemShortName.trim().isEmpty())) {
                    try {
                        Integer attrId = resolveAttributeByNameAndDatasetAndSystem(conn, nameB.trim(),
                                rowDataSetName != null ? rowDataSetName.trim() : null,
                                rowSystemShortName != null ? rowSystemShortName.trim() : null);
                        if (attrId != null) {
                            row.addResolvedEntityId("entityB", attrId);
                            logger.debug("Row {}: Resolved Entity B (attribute) by name+dataset+system with ID: {}",
                                    row.getRowNumber(), attrId);
                        }
                    } catch (SQLException e) {
                        logger.warn("Row {}: Failed to resolve attribute by name+dataset+system: {}", row.getRowNumber(), e.getMessage());
                    }
                }
            }
        }

        // Business Area X Glossary: when Parent Glossary Name is provided, validate parent exists and is the actual parent of the child
        if ("businessarea_x_glossary".equals(config.getTableName()) && "glossary".equals(entityBConfig.getTableName())
                && resultB.getId() != null && parentB != null && !parentB.trim().isEmpty()) {
            EntityResolutionResult parentGlossaryResult = entityResolverService.resolveEntity(
                conn, entityBConfig, null, parentB.trim(), null, row.getRowNumber(), cache, cacheStats);
            if (parentGlossaryResult.getId() == null) {
                row.addError("Incompatible parent");
                logger.warn("Row {}: Parent Glossary '{}' not found or not valid for child glossary", row.getRowNumber(), parentB.trim());
                return false;
            }
            Integer childGlossaryId = resultB.getId();
            Integer resolvedParentId = parentGlossaryResult.getId();
            Integer childActualParentId = getGlossaryParentId(conn, childGlossaryId);
            if (childActualParentId == null ? (resolvedParentId != null) : !childActualParentId.equals(resolvedParentId)) {
                row.addError("Incompatible parent");
                logger.warn("Row {}: Parent Glossary '{}' (ID {}) is not the actual parent of child glossary (ID {}, Parent_ID in DB: {})",
                    row.getRowNumber(), parentB.trim(), resolvedParentId, childGlossaryId, childActualParentId);
                return false;
            }
        }

        // Same-entity configs (e.g. Policy X Policy): Source and Target must use different columns
        if (config.getEntityARole() != null && config.getEntityBRole() != null) {
            String primaryColumnA = refColumnNameA != null ? refColumnNameA : nameColumnNameA;
            String primaryColumnB = refColumnNameB != null ? refColumnNameB : nameColumnNameB;
            if (primaryColumnA != null && primaryColumnB != null && primaryColumnA.equals(primaryColumnB)) {
                String errorMsg = "Source and Target must use different columns (e.g. 'Source Policy Ref.' and 'Target Policy Ref.'). " +
                    "Both entities are currently resolved from the same column: '" + primaryColumnA + "'.";
                row.addError(errorMsg);
                logger.warn("Row {}: Same-entity relationship used same column for source and target: '{}'", row.getRowNumber(), primaryColumnA);
                return false;
            }
        }
        
        // Special logging for Project_X_Dataset to help diagnose resolution issues
        if ("projectxdataset".equals(config.getTableName())) {
            Integer resolvedEntityAId = resultA.getId();
            Integer resolvedEntityBId = resultB.getId();
            String projectIdentifier = nameA != null ? nameA : (refA != null ? refA : "not provided");
            String datasetIdentifier = nameB != null ? nameB : (refB != null ? refB : "not provided");
            
            if (resolvedEntityAId == null && resolvedEntityBId == null) {
                logger.warn("Row {}: Project_X_Dataset - Both entities failed to resolve. " +
                    "Project identifier: '{}', Data Set identifier: '{}'. " +
                    "At least one entity must resolve successfully. Available Excel columns: {}",
                    row.getRowNumber(), projectIdentifier, datasetIdentifier,
                    String.join(", ", row.getOriginalValues().keySet()));
            } else if (resolvedEntityAId == null) {
                logger.info("Row {}: Project_X_Dataset - Project failed to resolve (identifier: '{}'), " +
                    "but Data Set resolved successfully (ID: {}). This is acceptable as both entities are optional.",
                    row.getRowNumber(), projectIdentifier, resolvedEntityBId);
            } else if (resolvedEntityBId == null) {
                logger.info("Row {}: Project_X_Dataset - Data Set failed to resolve (identifier: '{}'), " +
                    "but Project resolved successfully (ID: {}). This is acceptable as both entities are optional.",
                    row.getRowNumber(), datasetIdentifier, resolvedEntityAId);
            } else {
                logger.debug("Row {}: Project_X_Dataset - Both Project (ID: {}) and Data Set (ID: {}) resolved successfully.",
                    row.getRowNumber(), resolvedEntityAId, resolvedEntityBId);
            }
        }
        
        // Handle change request mapping for glossary relationships
        // Check if either entity is a glossary and has an active change request
        try {
            // For client_x_glossary (Glossary X Client), Entity B is the glossary
            if ("client_x_glossary".equals(config.getTableName()) && resultB.getId() != null && 
                "glossary".equals(entityBConfig.getTableName())) {
                Integer originalGlossaryId = resultB.getId();
                Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(12, originalGlossaryId); // 12 is GLOSSARY_FACET_ID
                
                if (activeCrId != null) {
                    // Get or create mapping for 'summary' area (this gets the cloned glossary ID)
                    Integer nobjectId = facetChangesDAO.getNObjectId("glossary", originalGlossaryId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists yet - need to clone the glossary row first
                        // This should have been done when summary was edited, but handle it here just in case
                        nobjectId = cloneGlossaryRow(conn, originalGlossaryId);
                        if (nobjectId != null) {
                            try {
                                facetChangesDAO.saveMapping("glossary", originalGlossaryId, nobjectId, "summary", activeCrId);
                                logger.debug("Row {}: Cloned glossary {} to {} for client_x_glossary relationship under CR {}", 
                                    row.getRowNumber(), originalGlossaryId, nobjectId, activeCrId);
                            } catch (SQLException e) {
                                logger.warn("Row {}: Failed to save summary mapping after cloning glossary: {}", 
                                    row.getRowNumber(), e.getMessage());
                            }
                        }
                    }
                    
                    if (nobjectId != null) {
                        // Use cloned glossary ID instead of original
                        row.addResolvedEntityId("entityB", nobjectId);
                        // Create mapping for impact#glossary_X_client area
                        try {
                            facetChangesDAO.saveMapping("glossary", originalGlossaryId, nobjectId, "impact#glossary_X_client", activeCrId);
                            logger.debug("Row {}: Using cloned glossary ID {} (original: {}) for client_x_glossary relationship under CR {}", 
                                row.getRowNumber(), nobjectId, originalGlossaryId, activeCrId);
                        } catch (SQLException e) {
                            logger.warn("Row {}: Failed to save mapping for glossary_X_client area: {}", 
                                row.getRowNumber(), e.getMessage());
                        }
                    } else {
                        logger.warn("Row {}: Active CR {} found for glossary {}, but failed to clone. Using original ID.", 
                            row.getRowNumber(), activeCrId, originalGlossaryId);
                    }
                }
            }
            // For glossary_x_system (Glossary X System), Entity A is the glossary
            else if ("glossary_x_system".equals(config.getTableName()) && resultA.getId() != null && 
                "glossary".equals(entityAConfig.getTableName())) {
                Integer originalGlossaryId = resultA.getId();
                Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(12, originalGlossaryId); // 12 is GLOSSARY_FACET_ID
                
                if (activeCrId != null) {
                    // Get or create mapping for 'summary' area (this gets the cloned glossary ID)
                    Integer nobjectId = facetChangesDAO.getNObjectId("glossary", originalGlossaryId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists yet - need to clone the glossary row first
                        // This should have been done when summary was edited, but handle it here just in case
                        nobjectId = cloneGlossaryRow(conn, originalGlossaryId);
                        if (nobjectId != null) {
                            try {
                                facetChangesDAO.saveMapping("glossary", originalGlossaryId, nobjectId, "summary", activeCrId);
                                logger.debug("Row {}: Cloned glossary {} to {} for glossary_x_system relationship under CR {}", 
                                    row.getRowNumber(), originalGlossaryId, nobjectId, activeCrId);
                            } catch (SQLException e) {
                                logger.warn("Row {}: Failed to save summary mapping after cloning glossary: {}", 
                                    row.getRowNumber(), e.getMessage());
                            }
                        }
                    }
                    
                    if (nobjectId != null) {
                        // Use cloned glossary ID instead of original
                        row.addResolvedEntityId("entityA", nobjectId);
                        // Create mapping for relationships#glossary_x_system area
                        try {
                            facetChangesDAO.saveMapping("glossary", originalGlossaryId, nobjectId, "relationships#glossary_x_system", activeCrId);
                            logger.debug("Row {}: Using cloned glossary ID {} (original: {}) for glossary_x_system relationship under CR {}", 
                                row.getRowNumber(), nobjectId, originalGlossaryId, activeCrId);
                        } catch (SQLException e) {
                            logger.warn("Row {}: Failed to save mapping for glossary_x_system area: {}", 
                                row.getRowNumber(), e.getMessage());
                        }
                    } else {
                        logger.warn("Row {}: Active CR {} found for glossary {}, but failed to clone. Using original ID.", 
                            row.getRowNumber(), activeCrId, originalGlossaryId);
                    }
                }
            }
            // For other glossary relationships, check both Entity A and Entity B
            else {
                // Check Entity A if it's a glossary
                if (resultA.getId() != null && "glossary".equals(entityAConfig.getTableName())) {
                    Integer originalGlossaryId = resultA.getId();
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(12, originalGlossaryId);
                    
                    if (activeCrId != null) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("glossary", originalGlossaryId, "summary", activeCrId);
                        if (nobjectId != null) {
                            row.addResolvedEntityId("entityA", nobjectId);
                            logger.debug("Row {}: Using cloned glossary ID {} (original: {}) for Entity A under CR {}", 
                                row.getRowNumber(), nobjectId, originalGlossaryId, activeCrId);
                        }
                    }
                }
                
                // Check Entity B if it's a glossary (and not already handled above)
                if (resultB.getId() != null && "glossary".equals(entityBConfig.getTableName()) && 
                    !"client_x_glossary".equals(config.getTableName())) {
                    Integer originalGlossaryId = resultB.getId();
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(12, originalGlossaryId);
                    
                    if (activeCrId != null) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("glossary", originalGlossaryId, "summary", activeCrId);
                        if (nobjectId != null) {
                            row.addResolvedEntityId("entityB", nobjectId);
                            logger.debug("Row {}: Using cloned glossary ID {} (original: {}) for Entity B under CR {}", 
                                row.getRowNumber(), nobjectId, originalGlossaryId, activeCrId);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Row {}: Error checking for change request mapping: {}", row.getRowNumber(), e.getMessage());
            // Continue with original IDs if mapping check fails
        }
        
        // Validate cross-segment relationship if both entities are resolved
        if (resultA.getId() != null && resultB.getId() != null && !row.hasErrors()) {
            try {
                // Skip validation for super admin
                boolean skipValidation = false;
                if (userId > 0) {
                    try {
                        skipValidation = SegmentAccessService.isSuperAdmin(userId);
                    } catch (SQLException e) {
                        logger.warn("Error checking super admin status: {}", e.getMessage());
                    }
                }
                
                if (!skipValidation) {
                    // Validate cross-segment relationship (use canonical segment object types for SegmentDAO lookup)
                    String sourceObjectType = entityNameToSegmentObjectType(entityAConfig.getName());
                    String targetObjectType = entityNameToSegmentObjectType(entityBConfig.getName());
                    SegmentValidationService.ValidationResult validationResult = 
                        segmentValidationService.validateCrossSegmentRelationship(
                            resultA.getId(), 
                            sourceObjectType,
                            resultB.getId(), 
                            targetObjectType,
                            conn
                        );
                    
                    if (!validationResult.isValid) {
                        row.addError(validationResult.message);
                        return false;
                    }
                }
            } catch (SQLException e) {
                logger.error("Error validating cross-segment relationship for row {}: {}", 
                    row.getRowNumber(), e.getMessage(), e);
                row.addError("Error validating segment relationship: " + e.getMessage());
                return false;
            }
        }
        
        // Return true if there are no errors
        // Warnings don't prevent entity resolution from succeeding
        // Warnings will be handled separately and may cause row to be skipped based on cancelOnWarning setting
        return !row.hasErrors();
    }
    
    /**
     * Resolve relationship type for a row
     * 
     * @param conn Database connection
     * @param row Row data containing original Excel values
     * @param config Relationship configuration
     * @param cache Relationship type cache
     * @param cacheStats Cache statistics
     * @param columnMappings Optional column mappings from frontend (Excel column name -> Field name)
     */
    private void resolveRelationshipTypeForRow(Connection conn, RelationshipRowData row, 
                                              RelationshipConfig config,
                                              Map<String, Integer> cache, CacheStats cacheStats,
                                              Map<String, String> columnMappings) {
        Map<String, String> originalValues = row.getOriginalValues();
        if (originalValues == null || originalValues.isEmpty()) {
            logger.debug("Row {}: No original values available for relationship type resolution", row.getRowNumber());
            return;
        }
        // Use same logic as pre-validation so "Relationship Type" is found regardless of Product vs Project headers
        String relationTypeName = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
        // Special case: Attribute_X_Attribute uses "Sourcing Type" instead of "Relationship Type"
        if ((relationTypeName == null || relationTypeName.trim().isEmpty()) && 
            "attribute_x_attribute".equals(config.getTableName())) {
            for (Map.Entry<String, String> entry : originalValues.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null && !value.trim().isEmpty()) {
                    if (key.toLowerCase().contains("sourcing type")) {
                        relationTypeName = value.trim();
                        logger.debug("Row {}: Found relationship type '{}' in column '{}' (Sourcing Type)", 
                            row.getRowNumber(), relationTypeName, key);
                        break;
                    }
                }
            }
        }
        if ((relationTypeName == null || relationTypeName.trim().isEmpty()) && config.isRequiresRelationType()) {
            logger.debug("Row {}: Relationship type column not found", row.getRowNumber());
        }
        if (relationTypeName != null && !relationTypeName.trim().isEmpty()) {
            // Lookup service normalizes (trim + collapse spaces) before query
            Integer typeId = relationshipTypeLookupService.resolveRelationshipType(
                conn, config.getRelationTypeTable(), relationTypeName, 
                row.getRowNumber(), cache, cacheStats
            );
            if (typeId != null) {
                row.setRelationshipTypeId(typeId);
                logger.debug("Row {}: Resolved relationship type '{}' to ID {}", 
                    row.getRowNumber(), relationTypeName, typeId);
            } else {
                row.addWarning(String.format(
                    "Relationship type not found: The relationship type '%s' does not exist in %s. Please verify the relationship type name is correct and exists in the system.",
                    relationTypeName, config.getRelationTypeTable()));
                logger.warn("Row {}: Relationship type '{}' not found in table {}", 
                    row.getRowNumber(), relationTypeName, config.getRelationTypeTable());
            }
        } else if (config.isRequiresRelationType()) {
            logger.debug("Row {}: Relationship type is required but value is empty or null", row.getRowNumber());
        }
    }
    
    /**
     * Handle row error
     */
    private void handleRowError(RelationshipRowData row, BulkUploadResponse response,
                               List<RelationshipRowData> skippedRows, boolean cancelOnWarning, int jobId) {
        logger.debug("Handling errors for row {}: {} errors", row.getRowNumber(), row.getErrors().size());
        
        // Add all errors to response first so failed count and BULK_COUNTS stay correct even if Job Report Item creation fails
        for (String error : row.getErrors()) {
            response.addError(ValidationIssue.error(row.getRowNumber(), "", "", error));
            logger.debug("Added error to response: row={}, message={}", row.getRowNumber(), error);
        }
        try {
            // Create Job Report Item for this error so report and status API include this row
            try {
                String entityName = getEntityNameForRow(row);
                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", row.getRowNumber());
                for (String error : row.getErrors()) {
                    jobDAO.createJobReportItemMessage(reportItemId, "VALIDATION_ERROR", error, "error");
                }
            } catch (Exception e) {
                logger.error("Failed to create job report item for error at row {}: {}", row.getRowNumber(), e.getMessage());
            }
            row.setSkipReason("Error: " + String.join("; ", row.getErrors()));
        } finally {
            // Always add row to skippedRows so failed count and report include every failed row
            skippedRows.add(row);
        }
        
        if (cancelOnWarning) {
            response.setCancelledDueToWarning(true);
            response.setSuccess(false);
            response.setMessage("Upload cancelled at row " + row.getRowNumber() + " due to error");
        }
        
        logger.debug("Response now has {} errors", response.getErrors().size());
    }
    
    /**
     * Get entity name for row (for report items)
     */
    private String getEntityNameForRow(RelationshipRowData row) {
        Map<String, String> originalValues = row.getOriginalValues();
        if (originalValues == null || originalValues.isEmpty()) {
            return "Relationship (Row " + row.getRowNumber() + ")";
        }
        
        // Try to get meaningful identifier from row
        String entityA = originalValues.values().stream().findFirst().orElse("Unknown");
        return "Relationship: " + entityA;
    }
    
    /**
     * Get the ref (RefNumber/refnumber) of an entity from the database by ID.
     * Used to validate that a row's provided ref matches the resolved entity (e.g. Policy X Project).
     *
     * @param conn Database connection
     * @param entityConfig Entity configuration (table, id column, ref column)
     * @param entityId Resolved entity ID
     * @return The ref value or null if not found or on error
     */
    private String getEntityRefFromDb(Connection conn, EntityConfig entityConfig, int entityId) {
        if (conn == null || entityConfig == null || !entityConfig.hasRefColumn()) {
            return null;
        }
        String tableName = entityConfig.getTableName();
        String idColumn = entityConfig.getIdColumn();
        String refColumn = entityConfig.getRefColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(refColumn)
           .append(" FROM ").append(tableName)
           .append(" WHERE ").append(idColumn).append(" = ?");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        sql.append(" LIMIT 1");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not get ref for {} id {}: {}", entityConfig.getName(), entityId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Get the Parent_ID of a glossary from the database.
     * Used for Business Area X Glossary to validate that the provided Parent Glossary Name matches the child's actual parent.
     *
     * @param conn Database connection
     * @param glossaryId Glossary ID (child)
     * @return Parent_ID of the glossary, or null if not found or on error
     */
    private Integer getGlossaryParentId(Connection conn, int glossaryId) {
        if (conn == null) {
            return null;
        }
        String sql = "SELECT Parent_ID FROM glossary WHERE ID = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int parentId = rs.getInt(1);
                    return rs.wasNull() ? null : parentId;
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not get Parent_ID for glossary id {}: {}", glossaryId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Get a row value by column name pattern (e.g. key contains "Attribute System" and "System Short").
     * Used for Policy X Attribute to read "Attribute System Short Name" / "Attribute Data Set Name" from Excel.
     */
    private String getRowValueByColumnPattern(RelationshipRowData row, String... keySubstrings) {
        Map<String, String> values = row.getOriginalValues();
        if (values == null) return null;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey() == null) continue;
            String k = e.getKey().trim().toLowerCase();
            boolean matches = true;
            for (String sub : keySubstrings) {
                if (sub == null) continue;
                if (!k.contains(sub.trim().toLowerCase())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                String v = e.getValue();
                return v == null ? null : v.trim();
            }
        }
        return null;
    }
    
    /**
     * Get the dataset name and system name for an attribute (attribute -> dataset -> system).
     * @return String[2] = {datasetPrimaryName, systemName} or null if not found
     */
    private String[] getAttributeDatasetAndSystemName(Connection conn, int attributeId) {
        String sql = "SELECT d.PrimaryName AS datasetName, s.Name AS systemName " +
            "FROM attribute a " +
            "LEFT JOIN dataset d ON d.ID = a.Dataset_ID AND d.DeletedDatetime IS NULL " +
            "LEFT JOIN system s ON s.id = d.MasterSource " +
            "WHERE a.ID = ? AND a.Deleted_Datetime IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String datasetName = rs.getString("datasetName");
                    String systemName = rs.getString("systemName");
                    return new String[]{datasetName != null ? datasetName : "", systemName != null ? systemName : ""};
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not get dataset/system for attribute id {}: {}", attributeId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Resolve attribute by name plus optional dataset and system (for disambiguation).
     * Used when Attribute Name and (Attribute Data Set Name and/or Attribute System Short Name) are provided.
     * @return Attribute ID if exactly one match, null if none or multiple
     */
    private Integer resolveAttributeByNameAndDatasetAndSystem(Connection conn, String attributeName,
                                                              String datasetPrimaryName, String systemShortName) throws SQLException {
        if (attributeName == null || attributeName.trim().isEmpty()) return null;
        String attrNorm = attributeName.trim();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.ID FROM attribute a ");
        sql.append("LEFT JOIN dataset d ON d.ID = a.Dataset_ID AND d.DeletedDatetime IS NULL ");
        sql.append("LEFT JOIN system s ON s.id = d.MasterSource AND (s.Deleted_datetime IS NULL OR s.Deleted_datetime = '') ");
        sql.append("WHERE a.Deleted_Datetime IS NULL AND LOWER(TRIM(a.PrimaryName)) = LOWER(?) ");
        List<String> params = new ArrayList<>();
        params.add(attrNorm);
        if (datasetPrimaryName != null && !datasetPrimaryName.trim().isEmpty()) {
            sql.append("AND d.ID IS NOT NULL AND LOWER(TRIM(d.PrimaryName)) = LOWER(?) ");
            params.add(datasetPrimaryName.trim());
        }
        if (systemShortName != null && !systemShortName.trim().isEmpty()) {
            sql.append("AND s.id IS NOT NULL AND LOWER(TRIM(s.Name)) = LOWER(?) ");
            params.add(systemShortName.trim());
        }
        sql.append("LIMIT 2");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                Integer singleId = null;
                int count = 0;
                while (rs.next()) {
                    singleId = rs.getInt("ID");
                    count++;
                    if (count > 1) return null;
                }
                return singleId;
            }
        }
    }
    
    /**
     * Get system ID for an attribute (via attribute.Dataset_ID -> dataset.MasterSource).
     * @return System ID or null if attribute has no dataset or dataset has no MasterSource
     */
    private Integer getAttributeSystemId(Connection conn, int attributeId) throws SQLException {
        String sql = "SELECT d.MasterSource AS systemId FROM attribute a " +
            "LEFT JOIN dataset d ON d.ID = a.Dataset_ID AND d.DeletedDatetime IS NULL " +
            "WHERE a.ID = ? AND a.Deleted_Datetime IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object sysId = rs.getObject("systemId");
                    if (sysId == null || rs.wasNull()) return null;
                    return rs.getInt("systemId");
                }
            }
        }
        return null;
    }
    
    /**
     * Get interface's source and target system IDs.
     * @return int[2] = { sourceSystemID, targetSystemID } (0-based; either can be 0 if null in DB)
     */
    private int[] getInterfaceSourceAndTargetSystemIds(Connection conn, int interfaceId) throws SQLException {
        String sql = "SELECT Source_systemID, Target_systemID FROM interface WHERE id = ? AND deleted_datetime IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int src = rs.getInt("Source_systemID");
                    boolean srcNull = rs.wasNull();
                    int tgt = rs.getInt("Target_systemID");
                    boolean tgtNull = rs.wasNull();
                    return new int[]{ srcNull ? 0 : src, tgtNull ? 0 : tgt };
                }
            }
        }
        return null;
    }
    
    /**
     * Resolve interface ID from row (Interface Name or Interface Ref.).
     */
    private Integer resolveInterfaceIdFromRow(Connection conn, RelationshipRowData row, Map<String, String> columnMappings) throws SQLException {
        String nameVal = findFieldValue(row, "Interface Name", columnMappings);
        String refVal = findFieldValue(row, "Interface Ref.", columnMappings);
        if ((nameVal == null || nameVal.trim().isEmpty()) && (refVal == null || refVal.trim().isEmpty())) {
            return null;
        }
        String sql = "SELECT id FROM interface WHERE deleted_datetime IS NULL AND (LOWER(TRIM(Name)) = LOWER(?) OR (Ref_number IS NOT NULL AND TRIM(Ref_number) = ?)) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String search = (nameVal != null && !nameVal.trim().isEmpty()) ? nameVal.trim() : refVal.trim();
            ps.setString(1, search);
            ps.setString(2, search);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }
    
    /**
     * Resolve system ID by name (system.Name).
     */
    private Integer resolveSystemIdByName(Connection conn, String systemName) throws SQLException {
        if (systemName == null || systemName.trim().isEmpty()) return null;
        String sql = "SELECT id FROM system WHERE Deleted_datetime IS NULL AND LOWER(TRIM(Name)) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, systemName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }
    
    /**
     * Get the system name and parent system name for a dataset (dataset.MasterSource -> system, system.parent_id -> parent system).
     * Used for Data Set X Client to validate "Data Set System Short Name" and "Data Set Parent System Short Name" match the dataset's system.
     * @return String[2] = {systemName, parentSystemName} or null if not found; either element may be null/empty if no system or no parent
     */
    private String[] getDatasetSystemAndParentSystemName(Connection conn, int datasetId) {
        String sql = "SELECT s.Name AS systemName, parent_sys.Name AS parentSystemName " +
            "FROM dataset d " +
            "LEFT JOIN system s ON s.id = d.MasterSource AND (s.Deleted_datetime IS NULL OR s.Deleted_datetime = '') " +
            "LEFT JOIN system parent_sys ON parent_sys.id = s.parent_id AND (parent_sys.Deleted_datetime IS NULL OR parent_sys.Deleted_datetime = '') " +
            "WHERE d.ID = ? AND d.DeletedDatetime IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String systemName = rs.getString("systemName");
                    String parentSystemName = rs.getString("parentSystemName");
                    return new String[]{systemName != null ? systemName : "", parentSystemName != null ? parentSystemName : ""};
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not get system/parent system for dataset id {}: {}", datasetId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Resolve dataset ID by PrimaryName (for Glossary X System "Strategic Data Set Name").
     * @return Dataset ID or null if not found or name is blank
     */
    private Integer resolveDatasetIdByName(Connection conn, String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT ID FROM dataset WHERE PrimaryName = ? AND DeletedDatetime IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        return null;
    }
    
    /**
     * Handle row warning
     * Creates a JobReportItem with status "error" so the status API counts this row as failed.
     */
    private void handleRowWarning(RelationshipRowData row, BulkUploadResponse response,
                                 List<RelationshipRowData> skippedRows, boolean cancelOnWarning, int jobId) {
        // Always add warnings to response for reporting
        for (String warning : row.getWarnings()) {
            ValidationIssue issue = ValidationIssue.warning(
                row.getRowNumber(), "", "", warning
            );
            response.addWarning(issue);
        }
        
        // Create Job Report Item so status API shows correct failed count (warnings = skipped = failed)
        try {
            String entityName = getEntityNameForRow(row);
            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", row.getRowNumber());
            for (String warning : row.getWarnings()) {
                jobDAO.createJobReportItemMessage(reportItemId, "VALIDATION_WARNING", warning, "warning");
            }
            logger.debug("Row {}: Skipped due to warning (e.g. duplicate); created Job_Report_Item id={} so report and failed count include this row",
                row.getRowNumber(), reportItemId);
        } catch (Exception e) {
            logger.error("Failed to create job report item for warning at row {}: {}", row.getRowNumber(), e.getMessage());
        }
        
        // Always add to skippedRows for accurate reporting (row will be skipped by continue statement)
        // But only cancel the entire upload if cancelOnWarning is true
        row.setSkipReason("Warning: " + String.join("; ", row.getWarnings()));
        skippedRows.add(row);
        
        if (cancelOnWarning) {
            response.setCancelledDueToWarning(true);
            response.setSuccess(false);
            response.setMessage("Upload cancelled at row " + row.getRowNumber() + " due to warning");
        }
        // If cancelOnWarning is false, warnings are logged and row is skipped (for duplicates, etc.)
        // but the upload continues processing remaining rows
    }
    
    /**
     * Insert single relationship
     */
    private void insertSingle(Connection conn, RelationshipRowData row, RelationshipConfig config, int userId, int jobId, Map<String, String> columnMappings) 
            throws SQLException {
        
        // Check if table requires explicit ID
        boolean requiresId = requiresExplicitId(config.getTableName());
        int nextId = -1;
        if (requiresId) {
            nextId = getNextIdForTable(conn, config.getTableName());
        }
        
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(config.getTableName()).append(" (");
        
        // Include ID field if required (process_x_system uses lowercase "id" per ProcessImpactDAO)
        if (requiresId) {
            sql.append("process_x_system".equals(config.getTableName()) ? "id, " : "ID, ");
        }
        
        sql.append(config.getEntityAIdColumn()).append(", ")
           .append(config.getEntityBIdColumn());
        
        // Glossary X System: Strategic_DatasetID (nullable) between SystemID and Relation_TypeID
        boolean hasStrategicDatasetColumn = "glossary_x_system".equals(config.getTableName());
        if (hasStrategicDatasetColumn) {
            sql.append(", Strategic_DatasetID");
        }
        
        if (config.hasRelationType()) {
            sql.append(", ").append(config.getRelationTypeColumn());
        }
        
        if (config.hasDescription()) {
            // regulation_x_regulator table uses Regulator_Reg_Ref, not descriptionColumn (Regulator_Reg_Reference)
            if ("regulation_x_regulator".equals(config.getTableName())) {
                sql.append(", Regulator_Reg_Ref");
            } else {
                sql.append(", ").append(config.getDescriptionColumn());
            }
        }
        
        // Determine the correct user column name and datetime column names based on table
        String userColumn = "LastUpdateUser_ID"; // Default
        String createDatetimeColumn = "CreateDatetime"; // Default
        String lastUpdateDatetimeColumn = "LastUpdateDatetime"; // Default
        boolean hasCreateDatetime = true; // Track if table has CreateDatetime column
        
        if ("client_x_project".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("client_x_system".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("client_x_glossary".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("client_x_process".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("client_x_dataset".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("attribute_x_attribute".equals(config.getTableName())) {
            userColumn = "Last_UpdateUserID";
        } else if ("committee_x_committee".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("product_x_glossary".equals(config.getTableName())) {
            // product_x_glossary uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("glossary_x_system".equals(config.getTableName())) {
            // glossary_x_system doesn't have CreateDatetime column, only LastUpdate_Datetime
            userColumn = "LastUpdate_UserID";
            lastUpdateDatetimeColumn = "LastUpdate_Datetime";
            hasCreateDatetime = false;
        } else if ("interface_x_glossary".equals(config.getTableName())) {
            // interface_x_glossary uses LastUpdate_datetime (with underscore and lowercase 'd')
            userColumn = "LastUpdate_UserID";
            lastUpdateDatetimeColumn = "LastUpdate_datetime";
        } else if ("process_x_interface".equals(config.getTableName())) {
            // process_x_interface uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("process_x_system".equals(config.getTableName())) {
            // process_x_system uses created_datetime (lowercase with underscore), lastupdatedatetime (all lowercase), last_update_userid (lowercase with underscore)
            userColumn = "last_update_userid";
            createDatetimeColumn = "created_datetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("process_x_dataset".equals(config.getTableName())) {
            // process_x_dataset uses all lowercase column names (note: database has typo "lastudpate_userid")
            userColumn = "lastudpate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("process_x_attribute".equals(config.getTableName())) {
            // process_x_attribute uses all lowercase column names (note: database has typo "lastudpate_userid")
            userColumn = "lastudpate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("product_x_process".equals(config.getTableName())) {
            // product_x_process uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("product_x_dataset".equals(config.getTableName())) {
            // product_x_dataset uses LastUpdated_UserID (with 'd' in Updated, per DatasetImpactDAO)
            userColumn = "LastUpdated_UserID";
        } else if ("product_x_project".equals(config.getTableName())) {
            // product_x_project uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_process".equals(config.getTableName())) {
            // project_x_process uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_project".equals(config.getTableName())) {
            // project_x_project uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_system".equals(config.getTableName())) {
            // project_x_system uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_dataset".equals(config.getTableName())) {
            // project_x_dataset uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_attribute".equals(config.getTableName())) {
            // project_x_attribute uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("regulator_x_geography".equals(config.getTableName())) {
            // regulator_x_geography doesn't have CreateDatetime column, only LastUpdateDatetime
            userColumn = "LastUpdate_UserID";
            lastUpdateDatetimeColumn = "LastUpdateDatetime";
            hasCreateDatetime = false;
        } else if ("regulation_x_policy".equals(config.getTableName())) {
            // regulation_x_policy uses LastUpdate_UserID (with underscore)
            userColumn = "LastUpdate_UserID";
        } else if ("regulation_x_product".equals(config.getTableName())) {
            // regulation_x_product uses LastUpdate_UserID (with underscore)
            userColumn = "LastUpdate_UserID";
        } else if ("regulation_x_project".equals(config.getTableName())) {
            // regulation_x_project uses LastUpdate_UserID (with underscore)
            userColumn = "LastUpdate_UserID";
        } else if ("regulation_x_regulator".equals(config.getTableName())) {
            // regulation_x_regulator doesn't have CreateDatetime column, only LastUpdateDatetime
            userColumn = "LastUpdate_UserID";
            lastUpdateDatetimeColumn = "LastUpdateDatetime";
            hasCreateDatetime = false;
        } else if ("people_x_people".equals(config.getTableName())) {
            // people_x_people uses Created_Datetime, Last_Update_Datetime, and Last_Update_UserID
            userColumn = "Last_Update_UserID";
            createDatetimeColumn = "Created_Datetime";
            lastUpdateDatetimeColumn = "Last_Update_Datetime";
        } else if ("policy_x_attribute".equals(config.getTableName())) {
            // policy_x_attribute uses all lowercase column names (note: database has typo "lastudpate_userid")
            userColumn = "lastudpate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("policy_x_dataset".equals(config.getTableName())) {
            // policy_x_dataset uses LastUdpate_UserID (note: database has typo "Udpate" instead of "Update")
            userColumn = "LastUdpate_UserID";
        } else if ("policy_x_glossary".equals(config.getTableName())) {
            // policy_x_glossary uses LastUdpate_UserID (note: database has typo "Udpate" instead of "Update")
            userColumn = "LastUdpate_UserID";
        } else if ("policy_x_policy".equals(config.getTableName())) {
            // policy_x_policy uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("policy_x_process".equals(config.getTableName())) {
            // policy_x_process uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("process_x_process".equals(config.getTableName())) {
            // process_x_process uses lowercase and typo lastupdatedateime (per ProcessImpactDAO)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedateime";
        } else if ("product_x_policy".equals(config.getTableName())) {
            // product_x_policy uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("policy_x_project".equals(config.getTableName())) {
            // policy_x_project uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("client_x_policy".equals(config.getTableName())) {
            // client_x_policy uses LastUpdate_UserID (with underscore, per PolicyImpactDAO)
            userColumn = "LastUpdate_UserID";
        } else if ("policy_x_system".equals(config.getTableName())) {
            // policy_x_system uses LastUpdate_UserID (with underscore before UserID)
            userColumn = "LastUpdate_UserID";
        } else if ("product_x_system".equals(config.getTableName())) {
            // product_x_system uses LastUpdated_UserID (with 'd' in Updated)
            userColumn = "LastUpdated_UserID";
        }
        
        // Build column list - glossary_x_system doesn't have CreateDatetime
        if (hasCreateDatetime) {
            sql.append(", ").append(createDatetimeColumn).append(", ").append(lastUpdateDatetimeColumn).append(", ").append(userColumn).append(") VALUES (");
        } else {
            sql.append(", ").append(lastUpdateDatetimeColumn).append(", ").append(userColumn).append(") VALUES (");
        }
        
        // Add placeholder for ID if required
        if (requiresId) {
            sql.append("?, ");
        }
        
        sql.append("?, ?");
        
        if (hasStrategicDatasetColumn) {
            sql.append(", ?");
        }
        if (config.hasRelationType()) {
            sql.append(", ?");
        }
        if (config.hasDescription()) {
            sql.append(", ?");
        }
        
        // Adjust placeholders based on whether table has CreateDatetime
        // hasCreateDatetime was already declared above
        if (hasCreateDatetime) {
            sql.append(", ?, ?, ?)");
        } else {
            sql.append(", ?, ?)");
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            // Defensive null check for resolvedEntityIds map
            Map<String, Integer> resolvedEntityIds = row.getResolvedEntityIds();
            if (resolvedEntityIds == null) {
                throw new SQLException(String.format(
                    "Cannot insert relationship at row %d: resolvedEntityIds map is null",
                    row.getRowNumber()));
            }
            
            // Get entity IDs with null safety for optional entities
            // CRITICAL: Always check for null after Map.get() to prevent NullPointerException when calling intValue()
            Integer entityAId = resolvedEntityIds.get("entityA");
            Integer entityBId = resolvedEntityIds.get("entityB");
            
            // Policy X Attribute: defensive guard - never insert with null policy or attribute ID.
            // Column mapping: policyid = entityA (Policy), attributeid = entityB (Attribute).
            if ("policy_x_attribute".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                throw new SQLException(String.format(
                    "Policy X Attribute requires both Policy and Attribute IDs at row %d; one was null (policy=%s, attribute=%s).",
                    row.getRowNumber(), entityAId, entityBId));
            }
            if ("project_x_attribute".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                throw new SQLException(String.format(
                    "Project X Attribute requires both Project and Attribute IDs at row %d; one was null (project=%s, attribute=%s).",
                    row.getRowNumber(), entityAId, entityBId));
            }
            // Regulation X Project: defensive guard - never insert with null RegulationID or ProjectID
            if ("regulation_x_project".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                throw new SQLException(String.format(
                    "Regulation X Project requires both Regulation and Project IDs at row %d; one was null (RegulationID=%s, ProjectID=%s).",
                    row.getRowNumber(), entityAId, entityBId));
            }
            
            // Get entity configs for validation
            EntityConfig entityAConfig = config.getEntityA();
            EntityConfig entityBConfig = config.getEntityB();
            
            // Enhanced logging when Map.get() returns null to help diagnose issues
            if (entityAId == null) {
                logger.warn("Row {}: Map.get('entityA') returned null in insertSingle. Expected key: 'entityA'. " +
                    "Available keys: {}. Entity A config: {} (required: {}). " +
                    "This may cause NullPointerException if intValue() is called on this null value.",
                    row.getRowNumber(), resolvedEntityIds.keySet(), 
                    entityAConfig.getName(), entityAConfig.isRequired());
            }
            if (entityBId == null) {
                logger.warn("Row {}: Map.get('entityB') returned null in insertSingle. Expected key: 'entityB'. " +
                    "Available keys: {}. Entity B config: {} (required: {}). " +
                    "This may cause NullPointerException if intValue() is called on this null value.",
                    row.getRowNumber(), resolvedEntityIds.keySet(),
                    entityBConfig.getName(), entityBConfig.isRequired());
            }
            
            // Defensive logging for debugging null issues
            if (entityAId == null || entityBId == null) {
                logger.warn("Row {}: Entity IDs after retrieval - entityAId: {}, entityBId: {}, resolvedEntityIds map: {}. " +
                    "Entity A: {} (required: {}), Entity B: {} (required: {})", 
                    row.getRowNumber(), entityAId, entityBId, resolvedEntityIds,
                    entityAConfig.getName(), entityAConfig.isRequired(),
                    entityBConfig.getName(), entityBConfig.isRequired());
            }
            
            // Validate required entities - additional safety layer beyond entity resolution validation
            if (entityAConfig.isRequired() && entityAId == null) {
                String errorMsg = String.format(
                    "Cannot insert relationship at row %d: Required Entity A (%s) ID is null. " +
                    "This indicates that the %s entity failed to resolve during entity resolution. " +
                    "Please check that the Excel file contains the correct column for %s (e.g., '%s Name' or '%s Ref') " +
                    "and that the entity exists in the database.",
                    row.getRowNumber(), entityAConfig.getName(), entityAConfig.getName().toLowerCase(),
                    entityAConfig.getName(), entityAConfig.getName(), entityAConfig.getName());
                logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                throw new SQLException(errorMsg);
            }
            
            if (entityBConfig.isRequired() && entityBId == null) {
                String errorMsg = String.format(
                    "Cannot insert relationship at row %d: Required Entity B (%s) ID is null. " +
                    "This indicates that the %s entity failed to resolve during entity resolution. " +
                    "Please check that the Excel file contains the correct column for %s (e.g., '%s Name' or '%s Ref') " +
                    "and that the entity exists in the database.",
                    row.getRowNumber(), entityBConfig.getName(), entityBConfig.getName().toLowerCase(),
                    entityBConfig.getName(), entityBConfig.getName(), entityBConfig.getName());
                logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                throw new SQLException(errorMsg);
            }
            
            // Safety check: at least one entity must be present
            if (entityAId == null && entityBId == null) {
                String errorMsg = String.format(
                    "Cannot insert relationship at row %d: both Entity A (%s) and Entity B (%s) IDs are null. " +
                    "At least one entity must be resolved successfully.",
                    row.getRowNumber(), entityAConfig.getName(), entityBConfig.getName());
                logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                throw new SQLException(errorMsg);
            }

            // Process X Client (client_x_process): never insert with process = N/A or client = N/A
            if ("client_x_process".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                String errorMsg = entityAId == null
                    ? "Client is required. Provide Client Name (or Client Parent Name)."
                    : "Process is required. Provide Process Ref. or Process Name.";
                logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                throw new SQLException("Cannot insert relationship at row " + row.getRowNumber() + ": " + errorMsg);
            }
            // Process X Data Set: never insert with process = N/A or dataset = N/A
            if ("process_x_dataset".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                String errorMsg = "Process and Data Set are required; not inserted.";
                logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                throw new SQLException("Cannot insert relationship at row " + row.getRowNumber() + ": " + errorMsg);
            }
            // Process X Glossary (glossary_x_process): never insert with glossary = N/A or process = N/A
            if ("glossary_x_process".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                String errorMsg = "Glossary and Process are required; not inserted.";
                logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                throw new SQLException("Cannot insert relationship at row " + row.getRowNumber() + ": " + errorMsg);
            }
            // Process X Legal Entity: never insert with process = N/A or legal = N/A
            if ("process_x_legal".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                throw new SQLException("Process and Legal Entity are required; not inserted.");
            }
            // Process X Process: never insert with process = N/A or predecessor process = N/A
            if ("process_x_process".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                throw new SQLException("Process and Predecessor Process are required; not inserted.");
            }
            // Product X Client: never insert with client = N/A (client is required)
            if ("product_x_client".equals(config.getTableName()) && entityBId == null) {
                throw new SQLException("Client is required; not inserted.");
            }

            // Never insert when RelationType is required but null (e.g. BA_X_Process) so BA/Process facets show correct data
            if (config.hasRelationType() && config.isRequiresRelationType() && row.getRelationshipTypeId() == null) {
                String errorMsg = String.format(
                    "Cannot insert relationship at row %d: Relationship type is required but is null. " +
                    "Please provide a valid Relationship Type from the lookup table.",
                    row.getRowNumber());
                logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                throw new SQLException(errorMsg);
            }

            // Safety net: reject self-relationship (same-entity types e.g. Policy X Policy)
            if (entityAId != null && entityBId != null && entityAId.equals(entityBId)
                    && entityAConfig.getTableName().equals(entityBConfig.getTableName())) {
                String errorMsg = "Source and target must be different. Cannot create a relationship between an object and itself.";
                logger.warn("Row {}: Rejecting self-relationship insert (entityAId=entityBId={})", row.getRowNumber(), entityAId);
                throw new SQLException(errorMsg);
            }
            
            // Capability X Glossary: defensive segment validation so incompatible glossary is never inserted
            if ("capability_x_glossary".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                try {
                    SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                        entityAId.intValue(), "Capability", entityBId.intValue(), "Glossary", conn);
                    if (!segmentResult.isValid) {
                        String errorMsg = String.format("Cannot insert relationship at row %d: %s",
                            row.getRowNumber(), segmentResult.message != null ? segmentResult.message : "Glossary is not compatible with the capability (segment rule).");
                        logger.warn("Row {}: Capability X Glossary segment validation failed: {}", row.getRowNumber(), segmentResult.message);
                        throw new SQLException(errorMsg);
                    }
                } catch (SQLException e) {
                    throw e;
                } catch (Exception e) {
                    logger.error("Row {}: Error validating Capability X Glossary segment: {}", row.getRowNumber(), e.getMessage(), e);
                    throw new SQLException("Could not validate segment compatibility: " + e.getMessage(), e);
                }
            }
            
            // Capability X Business Area: prevent relationship between objects in different private segments
            if ("capability_x_businessarea".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                try {
                    SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                        entityAId.intValue(), "Capability", entityBId.intValue(), "Business Area", conn);
                    if (!segmentResult.isValid) {
                        String errorMsg = String.format("Cannot insert relationship at row %d: %s",
                            row.getRowNumber(), segmentResult.message != null ? segmentResult.message : "Capability and Business Area must be in the same segment or one must be in Enterprise. They are in different private segments.");
                        logger.warn("Row {}: Capability X Business Area segment validation failed: {}", row.getRowNumber(), segmentResult.message);
                        throw new SQLException(errorMsg);
                    }
                } catch (SQLException e) {
                    throw e;
                } catch (Exception e) {
                    logger.error("Row {}: Error validating Capability X Business Area segment: {}", row.getRowNumber(), e.getMessage(), e);
                    throw new SQLException("Could not validate segment compatibility: " + e.getMessage(), e);
                }
            }
            
            int currentParam = 1;
            
            // Set ID if required
            if (requiresId) {
                ps.setInt(currentParam++, nextId);
            }
            
            // Set entity A ID with additional defensive check for required entities
            // This prevents any edge case where null might slip through
            if (entityAId != null) {
                // entityAId is non-null here, safe to use
                ps.setInt(currentParam++, entityAId);
            } else {
                // Only allow null for optional entities
                if (entityAConfig.isRequired()) {
                    throw new SQLException(String.format(
                        "Internal error at row %d: Required Entity A (%s) ID is null before database insert",
                        row.getRowNumber(), entityAConfig.getName()));
                }
                ps.setNull(currentParam++, java.sql.Types.INTEGER);
            }
            
            // Set entity B ID with additional defensive check for required entities
            // This prevents any edge case where null might slip through
            if (entityBId != null) {
                // entityBId is non-null here, safe to use
                ps.setInt(currentParam++, entityBId);
            } else {
                // Only allow null for optional entities
                if (entityBConfig.isRequired()) {
                    throw new SQLException(String.format(
                        "Internal error at row %d: Required Entity B (%s) ID is null before database insert",
                        row.getRowNumber(), entityBConfig.getName()));
                }
                ps.setNull(currentParam++, java.sql.Types.INTEGER);
            }
            
            // Glossary X System: Strategic_DatasetID (nullable)
            if (hasStrategicDatasetColumn) {
                Integer datasetId = resolvedEntityIds.get("dataset");
                if (datasetId != null && datasetId > 0) {
                    ps.setInt(currentParam++, datasetId);
                } else {
                    ps.setNull(currentParam++, java.sql.Types.INTEGER);
                }
            }
            
            if (config.hasRelationType()) {
                if (row.getRelationshipTypeId() != null) {
                    ps.setInt(currentParam++, row.getRelationshipTypeId());
                } else {
                    ps.setNull(currentParam++, java.sql.Types.INTEGER);
                }
            }
            
            if (config.hasDescription()) {
                String desc = findDescriptionValue(row, config, columnMappings);
                ps.setString(currentParam++, desc);
            }
            
            Timestamp now = new Timestamp(System.currentTimeMillis());
            // glossary_x_system doesn't have CreateDatetime, only LastUpdate_Datetime
            // hasCreateDatetime was already declared above
            if (hasCreateDatetime) {
                ps.setTimestamp(currentParam++, now); // CreateDatetime
            }
            ps.setTimestamp(currentParam++, now); // LastUpdateDatetime
            ps.setInt(currentParam, userId);
            
            // Log insert details for glossary_x_system (System X Glossary) to diagnose "correct row not inserted" issues
            if ("glossary_x_system".equals(config.getTableName())) {
                Integer relationTypeId = row.getRelationshipTypeId();
                logger.info("Row {}: Inserting into glossary_x_system - table={}, {}={}, {}={}, Relation_TypeID={}, rowNumber={}",
                    row.getRowNumber(), config.getTableName(),
                    config.getEntityAIdColumn(), entityAId,
                    config.getEntityBIdColumn(), entityBId,
                    relationTypeId != null ? relationTypeId : "NULL",
                    row.getRowNumber());
            }
            // Policy X Attribute: debug log so support can confirm policyid/attributeid written (policyid=entityA, attributeid=entityB)
            if ("policy_x_attribute".equals(config.getTableName())) {
                logger.debug("Row {}: Inserting into policy_x_attribute - policyid={}, attributeid={}",
                    row.getRowNumber(), entityAId, entityBId);
            }
            
            ps.executeUpdate();
            
            if ("glossary_x_system".equals(config.getTableName())) {
                logger.info("Row {}: Successfully inserted into glossary_x_system ({}={}, {}={})",
                    row.getRowNumber(), config.getEntityAIdColumn(), entityAId, config.getEntityBIdColumn(), entityBId);
            }
            logger.debug("Inserted relationship for row {} (ID: {})", row.getRowNumber(), requiresId ? nextId : "auto");
            
            // Create success report item so status API and report list every row (do not mutate row so Excel report shows Success)
            try {
                String entityName = getEntityNameForRow(row);
                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", row.getRowNumber());
                jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                    "Relationship inserted successfully", "info");
            } catch (Exception e) {
                logger.warn("Failed to create report item for successful insert at row {}: {} (insert succeeded; report will still show row as Success)", 
                    row.getRowNumber(), e.getMessage());
            }
        }
    }
    
    /**
     * Insert batch of relationships.
     * @return Number of rows actually added to the batch (and executed).
     */
    private int insertBatch(Connection conn, List<RelationshipRowData> rows, RelationshipConfig config, int userId, int jobId, Map<String, String> columnMappings) 
            throws SQLException {
        
        // Check if table requires explicit ID
        boolean requiresId = requiresExplicitId(config.getTableName());
        int nextId = -1;
        if (requiresId) {
            nextId = getNextIdForTable(conn, config.getTableName());
        }
        
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(config.getTableName()).append(" (");
        
        // Include ID field if required (process_x_system uses lowercase "id" per ProcessImpactDAO)
        if (requiresId) {
            sql.append("process_x_system".equals(config.getTableName()) ? "id, " : "ID, ");
        }
        
        sql.append(config.getEntityAIdColumn()).append(", ")
           .append(config.getEntityBIdColumn());
        
        // Glossary X System: Strategic_DatasetID (nullable) between SystemID and Relation_TypeID
        boolean hasStrategicDatasetColumn = "glossary_x_system".equals(config.getTableName());
        if (hasStrategicDatasetColumn) {
            sql.append(", Strategic_DatasetID");
        }
        
        if (config.hasRelationType()) {
            sql.append(", ").append(config.getRelationTypeColumn());
        }
        
        if (config.hasDescription()) {
            // regulation_x_regulator table uses Regulator_Reg_Ref, not descriptionColumn (Regulator_Reg_Reference)
            if ("regulation_x_regulator".equals(config.getTableName())) {
                sql.append(", Regulator_Reg_Ref");
            } else {
                sql.append(", ").append(config.getDescriptionColumn());
            }
        }
        
        // Determine the correct user column name and datetime column names based on table
        String userColumn = "LastUpdateUser_ID"; // Default
        String createDatetimeColumn = "CreateDatetime"; // Default
        String lastUpdateDatetimeColumn = "LastUpdateDatetime"; // Default
        boolean hasCreateDatetime = true; // Track if table has CreateDatetime column
        
        if ("client_x_project".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("client_x_system".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("client_x_glossary".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("client_x_process".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("client_x_dataset".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("attribute_x_attribute".equals(config.getTableName())) {
            userColumn = "Last_UpdateUserID";
        } else if ("committee_x_committee".equals(config.getTableName())) {
            userColumn = "LastUpdate_UserID";
        } else if ("product_x_glossary".equals(config.getTableName())) {
            // product_x_glossary uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("glossary_x_system".equals(config.getTableName())) {
            // glossary_x_system doesn't have CreateDatetime column, only LastUpdate_Datetime
            userColumn = "LastUpdate_UserID";
            lastUpdateDatetimeColumn = "LastUpdate_Datetime";
            hasCreateDatetime = false;
        } else if ("interface_x_glossary".equals(config.getTableName())) {
            // interface_x_glossary uses LastUpdate_datetime (with underscore and lowercase 'd')
            userColumn = "LastUpdate_UserID";
            lastUpdateDatetimeColumn = "LastUpdate_datetime";
        } else if ("process_x_interface".equals(config.getTableName())) {
            // process_x_interface uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("process_x_system".equals(config.getTableName())) {
            // process_x_system uses created_datetime (lowercase with underscore), lastupdatedatetime (all lowercase), last_update_userid (lowercase with underscore)
            userColumn = "last_update_userid";
            createDatetimeColumn = "created_datetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("process_x_dataset".equals(config.getTableName())) {
            // process_x_dataset uses all lowercase column names (note: database has typo "lastudpate_userid")
            userColumn = "lastudpate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("process_x_attribute".equals(config.getTableName())) {
            // process_x_attribute uses all lowercase column names (note: database has typo "lastudpate_userid")
            userColumn = "lastudpate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("product_x_process".equals(config.getTableName())) {
            // product_x_process uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("product_x_dataset".equals(config.getTableName())) {
            // product_x_dataset uses LastUpdated_UserID (with 'd' in Updated, per DatasetImpactDAO)
            userColumn = "LastUpdated_UserID";
        } else if ("product_x_project".equals(config.getTableName())) {
            // product_x_project uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_process".equals(config.getTableName())) {
            // project_x_process uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_project".equals(config.getTableName())) {
            // project_x_project uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_system".equals(config.getTableName())) {
            // project_x_system uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_dataset".equals(config.getTableName())) {
            // project_x_dataset uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("project_x_attribute".equals(config.getTableName())) {
            // project_x_attribute uses all lowercase column names (createdatetime, lastupdatedatetime, lastupdate_userid)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("regulator_x_geography".equals(config.getTableName())) {
            // regulator_x_geography doesn't have CreateDatetime column, only LastUpdateDatetime
            userColumn = "LastUpdate_UserID";
            lastUpdateDatetimeColumn = "LastUpdateDatetime";
            hasCreateDatetime = false;
        } else if ("regulation_x_policy".equals(config.getTableName())) {
            // regulation_x_policy uses LastUpdate_UserID (with underscore)
            userColumn = "LastUpdate_UserID";
        } else if ("regulation_x_product".equals(config.getTableName())) {
            // regulation_x_product uses LastUpdate_UserID (with underscore)
            userColumn = "LastUpdate_UserID";
        } else if ("regulation_x_project".equals(config.getTableName())) {
            // regulation_x_project uses LastUpdate_UserID (with underscore)
            userColumn = "LastUpdate_UserID";
        } else if ("regulation_x_regulator".equals(config.getTableName())) {
            // regulation_x_regulator doesn't have CreateDatetime column, only LastUpdateDatetime
            userColumn = "LastUpdate_UserID";
            lastUpdateDatetimeColumn = "LastUpdateDatetime";
            hasCreateDatetime = false;
        } else if ("people_x_people".equals(config.getTableName())) {
            // people_x_people uses Created_Datetime, Last_Update_Datetime, and Last_Update_UserID
            userColumn = "Last_Update_UserID";
            createDatetimeColumn = "Created_Datetime";
            lastUpdateDatetimeColumn = "Last_Update_Datetime";
        } else if ("policy_x_attribute".equals(config.getTableName())) {
            // policy_x_attribute uses all lowercase column names (note: database has typo "lastudpate_userid")
            userColumn = "lastudpate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("policy_x_dataset".equals(config.getTableName())) {
            // policy_x_dataset uses LastUdpate_UserID (note: database has typo "Udpate" instead of "Update")
            userColumn = "LastUdpate_UserID";
        } else if ("policy_x_glossary".equals(config.getTableName())) {
            // policy_x_glossary uses LastUdpate_UserID (note: database has typo "Udpate" instead of "Update")
            userColumn = "LastUdpate_UserID";
        } else if ("policy_x_policy".equals(config.getTableName())) {
            // policy_x_policy uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("policy_x_process".equals(config.getTableName())) {
            // policy_x_process uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("process_x_process".equals(config.getTableName())) {
            // process_x_process uses lowercase and typo lastupdatedateime (per ProcessImpactDAO)
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedateime";
        } else if ("product_x_policy".equals(config.getTableName())) {
            // product_x_policy uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("policy_x_project".equals(config.getTableName())) {
            // policy_x_project uses all lowercase column names
            userColumn = "lastupdate_userid";
            createDatetimeColumn = "createdatetime";
            lastUpdateDatetimeColumn = "lastupdatedatetime";
        } else if ("client_x_policy".equals(config.getTableName())) {
            // client_x_policy uses LastUpdate_UserID (with underscore, per PolicyImpactDAO)
            userColumn = "LastUpdate_UserID";
        } else if ("policy_x_system".equals(config.getTableName())) {
            // policy_x_system uses LastUpdate_UserID (with underscore before UserID)
            userColumn = "LastUpdate_UserID";
        } else if ("product_x_system".equals(config.getTableName())) {
            // product_x_system uses LastUpdated_UserID (with 'd' in Updated)
            userColumn = "LastUpdated_UserID";
        }
        
        // Build column list - glossary_x_system doesn't have CreateDatetime
        if (hasCreateDatetime) {
            sql.append(", ").append(createDatetimeColumn).append(", ").append(lastUpdateDatetimeColumn).append(", ").append(userColumn).append(") VALUES (");
        } else {
            sql.append(", ").append(lastUpdateDatetimeColumn).append(", ").append(userColumn).append(") VALUES (");
        }
        
        // Add placeholder for ID if required
        if (requiresId) {
            sql.append("?, ");
        }
        
        sql.append("?, ?");
        
        if (hasStrategicDatasetColumn) {
            sql.append(", ?");
        }
        if (config.hasRelationType()) {
            sql.append(", ?");
        }
        if (config.hasDescription()) {
            sql.append(", ?");
        }
        
        // Adjust placeholders based on whether table has CreateDatetime
        // hasCreateDatetime was already declared above
        if (hasCreateDatetime) {
            sql.append(", ?, ?, ?)");
        } else {
            sql.append(", ?, ?)");
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int batchCount = 0;
            List<RelationshipRowData> actuallyInsertedRows = new ArrayList<>();
            
            // Get entity configs once outside the loop for efficiency
            EntityConfig entityAConfig = config.getEntityA();
            EntityConfig entityBConfig = config.getEntityB();
            
            for (RelationshipRowData row : rows) {
                // Defensive null check for resolvedEntityIds map
                Map<String, Integer> resolvedEntityIds = row.getResolvedEntityIds();
                if (resolvedEntityIds == null) {
                    logger.warn("Row {}: Skipping relationship insert - resolvedEntityIds map is null", 
                        row.getRowNumber());
                    continue; // Skip this row
                }
                
                // Never insert when RelationType is required but null (e.g. BA_X_Process) so BA/Process facets show correct data
                if (config.hasRelationType() && config.isRequiresRelationType() && row.getRelationshipTypeId() == null) {
                    logger.error("Row {}: Skipping relationship insert - Relationship type is required but is null. " +
                        "This row should have been rejected in validation; skipping to avoid inserting with NULL RelationType.",
                        row.getRowNumber());
                    row.addError("Relationship type is required but not provided or could not be resolved.");
                    continue; // Skip this row - do not add to batch
                }
                
                // Get entity IDs with null safety for optional entities
                // CRITICAL: Always check for null after Map.get() to prevent NullPointerException when calling intValue()
                Integer entityAId = resolvedEntityIds.get("entityA");
                Integer entityBId = resolvedEntityIds.get("entityB");
                
                // Policy X Attribute: defensive guard - never insert with null policy or attribute ID (policyid=entityA, attributeid=entityB)
                if ("policy_x_attribute".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    throw new SQLException(String.format(
                        "Policy X Attribute requires both Policy and Attribute IDs at row %d; one was null (policy=%s, attribute=%s).",
                        row.getRowNumber(), entityAId, entityBId));
                }
                if ("project_x_attribute".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    throw new SQLException(String.format(
                        "Project X Attribute requires both Project and Attribute IDs at row %d; one was null (project=%s, attribute=%s).",
                        row.getRowNumber(), entityAId, entityBId));
                }
                // Regulation X Project: defensive guard - never insert with null RegulationID or ProjectID
                if ("regulation_x_project".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    row.addError(String.format(
                        "Regulation X Project requires both Regulation and Project IDs; one was null at row %d (RegulationID=%s, ProjectID=%s).",
                        row.getRowNumber(), entityAId, entityBId));
                    continue; // Skip this row so it is not inserted
                }
                // Process X Client (client_x_process): never insert with process = N/A or client = N/A
                if ("client_x_process".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    String msg = entityAId == null
                        ? "Client is required. Provide Client Name (or Client Parent Name)."
                        : "Process is required. Provide Process Ref. or Process Name.";
                    row.addError(msg);
                    continue; // Skip this row so it is not inserted
                }
                // Process X Data Set: never insert with process = N/A or dataset = N/A
                if ("process_x_dataset".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    row.addError("Process and Data Set are required; not inserted.");
                    continue; // Skip this row so it is not inserted
                }
                // Process X Glossary (glossary_x_process): never insert with glossary = N/A or process = N/A
                if ("glossary_x_process".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    row.addError("Glossary and Process are required; not inserted.");
                    continue; // Skip this row so it is not inserted
                }
                // Process X Legal Entity: never insert with process = N/A or legal = N/A
                if ("process_x_legal".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    row.addError("Process and Legal Entity are required; not inserted.");
                    continue; // Skip this row so it is not inserted
                }
                // Process X Process: never insert with process = N/A or predecessor process = N/A
                if ("process_x_process".equals(config.getTableName()) && (entityAId == null || entityBId == null)) {
                    row.addError("Process and Predecessor Process are required; not inserted.");
                    continue; // Skip this row so it is not inserted
                }
                // Product X Client: never insert with client = N/A (client is required)
                if ("product_x_client".equals(config.getTableName()) && entityBId == null) {
                    row.addError("Client is required; not inserted.");
                    continue; // Skip this row so it is not inserted
                }
                
                // Enhanced logging when Map.get() returns null to help diagnose issues
                if (entityAId == null) {
                    logger.warn("Row {}: Map.get('entityA') returned null in insertBatch. Expected key: 'entityA'. " +
                        "Available keys: {}. Entity A config: {} (required: {}). " +
                        "This may cause NullPointerException if intValue() is called on this null value.",
                        row.getRowNumber(), resolvedEntityIds.keySet(), 
                        entityAConfig.getName(), entityAConfig.isRequired());
                }
                if (entityBId == null) {
                    logger.warn("Row {}: Map.get('entityB') returned null in insertBatch. Expected key: 'entityB'. " +
                        "Available keys: {}. Entity B config: {} (required: {}). " +
                        "This may cause NullPointerException if intValue() is called on this null value.",
                        row.getRowNumber(), resolvedEntityIds.keySet(),
                        entityBConfig.getName(), entityBConfig.isRequired());
                }
                
                // Defensive logging for debugging null issues
                if (entityAId == null || entityBId == null) {
                    logger.warn("Row {}: Entity IDs after retrieval - entityAId: {}, entityBId: {}, resolvedEntityIds map: {}. " +
                        "Entity A: {} (required: {}), Entity B: {} (required: {})", 
                        row.getRowNumber(), entityAId, entityBId, resolvedEntityIds,
                        entityAConfig.getName(), entityAConfig.isRequired(),
                        entityBConfig.getName(), entityBConfig.isRequired());
                }
                
                // Validate required entities - additional safety layer beyond entity resolution validation
                if (entityAConfig.isRequired() && entityAId == null) {
                    logger.error("Row {}: Skipping relationship insert - Required Entity A ({}) ID is null. " +
                        "This indicates that the {} entity failed to resolve during entity resolution. " +
                        "Please check that the Excel file contains the correct column for {} (e.g., '{} Name' or '{} Ref') " +
                        "and that the entity exists in the database.",
                        row.getRowNumber(), entityAConfig.getName(), entityAConfig.getName().toLowerCase(),
                        entityAConfig.getName(), entityAConfig.getName(), entityAConfig.getName());
                    continue; // Skip this row
                }
                
                if (entityBConfig.isRequired() && entityBId == null) {
                    logger.error("Row {}: Skipping relationship insert - Required Entity B ({}) ID is null. " +
                        "This indicates that the {} entity failed to resolve during entity resolution. " +
                        "Please check that the Excel file contains the correct column for {} (e.g., '{} Name' or '{} Ref') " +
                        "and that the entity exists in the database.",
                        row.getRowNumber(), entityBConfig.getName(), entityBConfig.getName().toLowerCase(),
                        entityBConfig.getName(), entityBConfig.getName(), entityBConfig.getName());
                    continue; // Skip this row
                }
                
                // Safety check: at least one entity must be present
                if (entityAId == null && entityBId == null) {
                    logger.error("Row {}: Skipping relationship insert - both Entity A ({}) and Entity B ({}) IDs are null. " +
                        "At least one entity must be resolved successfully.",
                        row.getRowNumber(), entityAConfig.getName(), entityBConfig.getName());
                    continue; // Skip this row
                }
                
                // Capability X Glossary: defensive segment validation so incompatible glossary is never inserted
                if ("capability_x_glossary".equals(config.getTableName()) && entityAId != null && entityBId != null) {
                    try {
                        SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                            entityAId.intValue(), "Capability", entityBId.intValue(), "Glossary", conn);
                        if (!segmentResult.isValid) {
                            String errorMsg = String.format("Cannot insert relationship at row %d: %s",
                                row.getRowNumber(), segmentResult.message != null ? segmentResult.message : "Glossary is not compatible with the capability (segment rule).");
                            logger.warn("Row {}: Capability X Glossary segment validation failed in batch: {}", row.getRowNumber(), segmentResult.message);
                            throw new SQLException(errorMsg);
                        }
                    } catch (SQLException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("Row {}: Error validating Capability X Glossary segment in batch: {}", row.getRowNumber(), e.getMessage(), e);
                        throw new SQLException("Could not validate segment compatibility: " + e.getMessage(), e);
                    }
                }
                
                // Business Area X Process/System/Glossary: defensive segment validation so cross-segment relationships are never inserted
                String tableName = config.getTableName();
                if (entityAId != null && entityBId != null && ("businessarea_x_process".equals(tableName) || "businessarea_x_system".equals(tableName) || "businessarea_x_glossary".equals(tableName))) {
                    String targetType = "businessarea_x_process".equals(tableName) ? "Process" : ("businessarea_x_system".equals(tableName) ? "System" : "Glossary");
                    try {
                        SegmentValidationService.ValidationResult segmentResult = segmentValidationService.validateCrossSegmentRelationship(
                            entityAId.intValue(), "BusinessArea", entityBId.intValue(), targetType, conn);
                        if (!segmentResult.isValid) {
                            String errorMsg = String.format("Cannot insert relationship at row %d: %s",
                                row.getRowNumber(), segmentResult.message != null ? segmentResult.message : "Business Area and " + targetType + " must be in the same segment or one must be in Enterprise.");
                            logger.warn("Row {}: Business Area X {} segment validation failed in batch: {}", row.getRowNumber(), targetType, segmentResult.message);
                            throw new SQLException(errorMsg);
                        }
                    } catch (SQLException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("Row {}: Error validating Business Area X {} segment in batch: {}", row.getRowNumber(), targetType, e.getMessage(), e);
                        throw new SQLException("Could not validate segment compatibility: " + e.getMessage(), e);
                    }
                }
                
                int currentParam = 1;
                
                // Set ID if required (increment for each row in batch)
                if (requiresId) {
                    ps.setInt(currentParam++, nextId + batchCount);
                }
                
                // Additional defensive null checks before setInt calls
                // Set entity A ID with additional defensive check for required entities
                if (entityAId != null) {
                    // entityAId is non-null here, safe to use
                    ps.setInt(currentParam++, entityAId);
                } else {
                    // Only allow null for optional entities
                    if (entityAConfig.isRequired()) {
                        logger.error("Row {}: Internal error - Required Entity A ({}) ID is null before database insert. Skipping row.",
                            row.getRowNumber(), entityAConfig.getName());
                        continue; // Skip this row
                    }
                    ps.setNull(currentParam++, java.sql.Types.INTEGER);
                }
                
                // Set entity B ID with additional defensive check for required entities
                if (entityBId != null) {
                    // entityBId is non-null here, safe to use
                    ps.setInt(currentParam++, entityBId);
                } else {
                    // Only allow null for optional entities
                    if (entityBConfig.isRequired()) {
                        logger.error("Row {}: Internal error - Required Entity B ({}) ID is null before database insert. Skipping row.",
                            row.getRowNumber(), entityBConfig.getName());
                        continue; // Skip this row
                    }
                    ps.setNull(currentParam++, java.sql.Types.INTEGER);
                }
                
                // Glossary X System: Strategic_DatasetID (nullable)
                if (hasStrategicDatasetColumn) {
                    Integer datasetId = resolvedEntityIds.get("dataset");
                    if (datasetId != null && datasetId > 0) {
                        ps.setInt(currentParam++, datasetId);
                    } else {
                        ps.setNull(currentParam++, java.sql.Types.INTEGER);
                    }
                }
                
                if (config.hasRelationType()) {
                    if (row.getRelationshipTypeId() != null) {
                        ps.setInt(currentParam++, row.getRelationshipTypeId());
                    } else {
                        ps.setNull(currentParam++, java.sql.Types.INTEGER);
                    }
                }
                
                if (config.hasDescription()) {
                    String desc = findDescriptionValue(row, config, columnMappings);
                    ps.setString(currentParam++, desc);
                }
                
                Timestamp now = new Timestamp(System.currentTimeMillis());
                // glossary_x_system doesn't have CreateDatetime, only LastUpdate_Datetime
                // hasCreateDatetime was already declared above
                if (hasCreateDatetime) {
                    ps.setTimestamp(currentParam++, now); // CreateDatetime
                }
                ps.setTimestamp(currentParam++, now); // LastUpdateDatetime
                ps.setInt(currentParam, userId);
                
                ps.addBatch();
                batchCount++;
                actuallyInsertedRows.add(row);
                
                // Execute every 500 rows
                if (batchCount % 500 == 0) {
                    ps.executeBatch();
                    logger.debug("Executed batch of {} relationships", batchCount);
                }
            }
            
            // Execute remaining
            ps.executeBatch();
            logger.info("Batch insert completed: {} relationships ({} actually inserted)", rows.size(), batchCount);
            
            // Create success report items only for rows that were actually inserted
            for (RelationshipRowData reportRow : actuallyInsertedRows) {
                try {
                    String entityName = getEntityNameForRow(reportRow);
                    int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", reportRow.getRowNumber());
                    jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                        "Relationship inserted successfully", "info");
                } catch (Exception ex) {
                    logger.warn("Failed to create report item for successful insert at row {}: {}", 
                        reportRow.getRowNumber(), ex.getMessage());
                }
            }
            return batchCount;
        }
    }
    
    /**
     * Delete single relationship.
     * @return true if a row was actually deleted (executeUpdate > 0), false otherwise
     */
    private boolean deleteSingle(Connection conn, RelationshipRowData row, RelationshipConfig config, int jobId) 
            throws SQLException {
        
        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ").append(config.getTableName())
           .append(" WHERE ").append(config.getEntityAIdColumn()).append(" = ?")
           .append(" AND ").append(config.getEntityBIdColumn()).append(" = ?");
        // Glossary X System: match exact row by Strategic_DatasetID (NULL-safe)
        boolean hasStrategicDatasetColumn = "glossary_x_system".equals(config.getTableName());
        if (hasStrategicDatasetColumn) {
            sql.append(" AND Strategic_DatasetID <=> ?");
        }
        if (config.hasRelationType() && row.getRelationshipTypeId() != null) {
            sql.append(" AND ").append(config.getRelationTypeColumn()).append(" = ?");
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            // Get entity IDs with null safety (defensive check - both should be present for delete)
            Map<String, Integer> resolvedEntityIds = row.getResolvedEntityIds();
            if (resolvedEntityIds == null) {
                String errorMsg = String.format(
                    "Cannot delete relationship at row %d: resolvedEntityIds map is null. " +
                    "This indicates that entity resolution failed. Please check that both entities were resolved successfully.",
                    row.getRowNumber());
                logger.error("Row {}: {}", row.getRowNumber(), errorMsg);
                throw new SQLException(errorMsg);
            }
            
            // CRITICAL: Always check for null after Map.get() to prevent NullPointerException when calling intValue()
            Integer entityAId = resolvedEntityIds.get("entityA");
            Integer entityBId = resolvedEntityIds.get("entityB");
            
            // Get entity configs for enhanced error messages
            EntityConfig entityAConfig = config.getEntityA();
            EntityConfig entityBConfig = config.getEntityB();
            
            // Enhanced logging when Map.get() returns null to help diagnose issues
            if (entityAId == null) {
                logger.warn("Row {}: Map.get('entityA') returned null in deleteSingle. Expected key: 'entityA'. " +
                    "Available keys: {}. Entity A config: {} (required: {}). " +
                    "This may cause NullPointerException if intValue() is called on this null value.",
                    row.getRowNumber(), resolvedEntityIds.keySet(), 
                    entityAConfig.getName(), entityAConfig.isRequired());
            }
            if (entityBId == null) {
                logger.warn("Row {}: Map.get('entityB') returned null in deleteSingle. Expected key: 'entityB'. " +
                    "Available keys: {}. Entity B config: {} (required: {}). " +
                    "This may cause NullPointerException if intValue() is called on this null value.",
                    row.getRowNumber(), resolvedEntityIds.keySet(),
                    entityBConfig.getName(), entityBConfig.isRequired());
            }
            
            // Log when null IDs are detected
            if (entityAId == null || entityBId == null) {
                logger.warn("Row {}: Entity IDs for delete operation - entityAId: {}, entityBId: {}, resolvedEntityIds map: {}. " +
                    "Entity A: {} (required: {}), Entity B: {} (required: {})", 
                    row.getRowNumber(), entityAId, entityBId, resolvedEntityIds,
                    entityAConfig.getName(), entityAConfig.isRequired(),
                    entityBConfig.getName(), entityBConfig.isRequired());
            }
            
            // For delete operations, both entities should be present, but handle nulls defensively
            if (entityAId == null) {
                String errorMsgA = String.format(
                    "Cannot delete relationship at row %d: Entity A (%s) ID is null. " +
                    "This indicates that the %s entity failed to resolve during entity resolution. " +
                    "Please check that the Excel file contains the correct column for %s (e.g., '%s Name' or '%s Ref') " +
                    "and that the entity exists in the database.",
                    row.getRowNumber(), entityAConfig.getName(), entityAConfig.getName().toLowerCase(),
                    entityAConfig.getName(), entityAConfig.getName(), entityAConfig.getName());
                logger.error("Row {}: {}", row.getRowNumber(), errorMsgA);
                throw new SQLException(errorMsgA);
            }
            if (entityBId == null) {
                String errorMsgB = String.format(
                    "Cannot delete relationship at row %d: Entity B (%s) ID is null. " +
                    "This indicates that the %s entity failed to resolve during entity resolution. " +
                    "Please check that the Excel file contains the correct column for %s (e.g., '%s Name' or '%s Ref') " +
                    "and that the entity exists in the database.",
                    row.getRowNumber(), entityBConfig.getName(), entityBConfig.getName().toLowerCase(),
                    entityBConfig.getName(), entityBConfig.getName(), entityBConfig.getName());
                logger.error("Row {}: {}", row.getRowNumber(), errorMsgB);
                throw new SQLException(errorMsgB);
            }
            
            // Additional defensive check right before setInt to prevent any edge cases
            if (entityAId == null || entityBId == null) {
                String errorMsgInternal = String.format(
                    "Internal error at row %d: Entity IDs became null before database delete operation. " +
                    "Entity A (%s) ID: %s, Entity B (%s) ID: %s",
                    row.getRowNumber(), entityAConfig.getName(), entityAId, entityBConfig.getName(), entityBId);
                logger.error("Row {}: {}", row.getRowNumber(), errorMsgInternal);
                throw new SQLException(errorMsgInternal);
            }
            
            int param = 1;
            ps.setInt(param++, entityAId);
            ps.setInt(param++, entityBId);
            if (hasStrategicDatasetColumn) {
                Integer datasetId = resolvedEntityIds.get("dataset");
                if (datasetId != null && datasetId > 0) {
                    ps.setInt(param++, datasetId);
                } else {
                    ps.setNull(param++, java.sql.Types.INTEGER);
                }
            }
            if (config.hasRelationType() && row.getRelationshipTypeId() != null) {
                ps.setInt(param++, row.getRelationshipTypeId());
            }
            
            int deleted = ps.executeUpdate();
            logger.debug("Deleted relationship for row {}: {} rows affected", row.getRowNumber(), deleted);
            
            if (deleted > 0) {
                // Create success report item only when a row was actually deleted
                try {
                    String entityName = getEntityNameForRow(row);
                    int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", row.getRowNumber());
                    jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                        "Relationship deleted successfully", "info");
                } catch (Exception e) {
                    logger.warn("Failed to create report item for successful delete at row {}: {}", 
                        row.getRowNumber(), e.getMessage());
                }
                return true;
            }
            return false;
        }
    }
    
    /**
     * Delete batch of relationships.
     * @return number of rows actually deleted (where executeUpdate/executeBatch affected a row)
     */
    private int deleteBatch(Connection conn, List<RelationshipRowData> rows, RelationshipConfig config, int jobId) 
            throws SQLException {
        
        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ").append(config.getTableName())
           .append(" WHERE ").append(config.getEntityAIdColumn()).append(" = ?")
           .append(" AND ").append(config.getEntityBIdColumn()).append(" = ?");
        boolean hasStrategicDatasetColumn = "glossary_x_system".equals(config.getTableName());
        if (hasStrategicDatasetColumn) {
            sql.append(" AND Strategic_DatasetID <=> ?");
        }
        // Check if we need to handle relationship type
        boolean hasRelationType = config.hasRelationType();
        if (hasRelationType) {
            sql.append(" AND ").append(config.getRelationTypeColumn()).append(" = ?");
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int batchCount = 0;
            int actualDeletedCount = 0;
            List<RelationshipRowData> currentBatchRows = new ArrayList<>();
            List<RelationshipRowData> actuallyDeletedRows = new ArrayList<>();
            
            // Get entity configs once outside the loop for efficiency
            EntityConfig entityAConfig = config.getEntityA();
            EntityConfig entityBConfig = config.getEntityB();
            
            for (RelationshipRowData row : rows) {
                // Get entity IDs with null safety (defensive check - both should be present for delete)
                Map<String, Integer> resolvedEntityIds = row.getResolvedEntityIds();
                if (resolvedEntityIds == null) {
                    logger.error("Row {}: resolvedEntityIds map is null for delete operation. " +
                        "This indicates that entity resolution failed. Skipping row.",
                        row.getRowNumber());
                    continue; // Skip this row
                }
                
                // CRITICAL: Always check for null after Map.get() to prevent NullPointerException when calling intValue()
                Integer entityAId = resolvedEntityIds.get("entityA");
                Integer entityBId = resolvedEntityIds.get("entityB");
                
                // Note: entityAConfig and entityBConfig are already declared outside the loop (lines 3024-3025)
                
                // Enhanced logging when Map.get() returns null to help diagnose issues
                if (entityAId == null) {
                    logger.warn("Row {}: Map.get('entityA') returned null in deleteBatch. Expected key: 'entityA'. " +
                        "Available keys: {}. Entity A config: {} (required: {}). " +
                        "This may cause NullPointerException if intValue() is called on this null value.",
                        row.getRowNumber(), resolvedEntityIds.keySet(), 
                        entityAConfig.getName(), entityAConfig.isRequired());
                }
                if (entityBId == null) {
                    logger.warn("Row {}: Map.get('entityB') returned null in deleteBatch. Expected key: 'entityB'. " +
                        "Available keys: {}. Entity B config: {} (required: {}). " +
                        "This may cause NullPointerException if intValue() is called on this null value.",
                        row.getRowNumber(), resolvedEntityIds.keySet(),
                        entityBConfig.getName(), entityBConfig.isRequired());
                }
                
                // Log when null IDs are detected
                if (entityAId == null || entityBId == null) {
                    logger.warn("Row {}: Entity IDs for delete operation - entityAId: {}, entityBId: {}, resolvedEntityIds map: {}. " +
                        "Entity A: {} (required: {}), Entity B: {} (required: {})", 
                        row.getRowNumber(), entityAId, entityBId, resolvedEntityIds,
                        entityAConfig.getName(), entityAConfig.isRequired(),
                        entityBConfig.getName(), entityBConfig.isRequired());
                }
                
                // For delete operations, both entities should be present, but handle nulls defensively
                if (entityAId == null) {
                    logger.error("Row {}: Entity A ({}) ID is null for delete operation. " +
                        "This indicates that the {} entity failed to resolve during entity resolution. " +
                        "Please check that the Excel file contains the correct column for {} (e.g., '{} Name' or '{} Ref') " +
                        "and that the entity exists in the database. Skipping row.",
                        row.getRowNumber(), entityAConfig.getName(), entityAConfig.getName().toLowerCase(),
                        entityAConfig.getName(), entityAConfig.getName(), entityAConfig.getName());
                    continue; // Skip this row
                }
                if (entityBId == null) {
                    logger.error("Row {}: Entity B ({}) ID is null for delete operation. " +
                        "This indicates that the {} entity failed to resolve during entity resolution. " +
                        "Please check that the Excel file contains the correct column for {} (e.g., '{} Name' or '{} Ref') " +
                        "and that the entity exists in the database. Skipping row.",
                        row.getRowNumber(), entityBConfig.getName(), entityBConfig.getName().toLowerCase(),
                        entityBConfig.getName(), entityBConfig.getName(), entityBConfig.getName());
                    continue; // Skip this row
                }
                
                // Additional defensive check right before setInt to prevent any edge cases
                if (entityAId == null || entityBId == null) {
                    logger.error("Row {}: Internal error - Entity IDs became null before database delete operation. " +
                        "Entity A ({}) ID: {}, Entity B ({}) ID: {}. Skipping row.",
                        row.getRowNumber(), entityAConfig.getName(), entityAId, entityBConfig.getName(), entityBId);
                    continue; // Skip this row
                }
                
                int param = 1;
                ps.setInt(param++, entityAId);
                ps.setInt(param++, entityBId);
                if (hasStrategicDatasetColumn) {
                    Integer datasetId = resolvedEntityIds.get("dataset");
                    if (datasetId != null && datasetId > 0) {
                        ps.setInt(param++, datasetId);
                    } else {
                        ps.setNull(param++, java.sql.Types.INTEGER);
                    }
                }
                if (hasRelationType && row.getRelationshipTypeId() != null) {
                    ps.setInt(param++, row.getRelationshipTypeId());
                }
                
                ps.addBatch();
                batchCount++;
                currentBatchRows.add(row);
                
                // Execute every 500 rows and count actual deletes from update counts
                if (batchCount % 500 == 0) {
                    int[] counts = ps.executeBatch();
                    for (int i = 0; i < counts.length; i++) {
                        if (counts[i] > 0) {
                            actualDeletedCount++;
                            actuallyDeletedRows.add(currentBatchRows.get(i));
                        }
                    }
                    currentBatchRows.clear();
                    logger.debug("Executed batch delete of {} relationships", counts.length);
                }
            }
            
            // Execute remaining and count actual deletes (currentBatchRows holds the last partial batch)
            int[] counts = ps.executeBatch();
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] > 0) {
                    actualDeletedCount++;
                    actuallyDeletedRows.add(currentBatchRows.get(i));
                }
            }
            logger.info("Batch delete completed: {} relationships, {} actually deleted", rows.size(), actualDeletedCount);
            
            // Mark rows that were not actually deleted so report shows Skipped/Failed
            String noMatchWarning = "No matching relationship found to delete (0 rows affected). The specified entities may not be linked, or the identifiers may not match.";
            for (RelationshipRowData row : rows) {
                if (!actuallyDeletedRows.contains(row)) {
                    row.addWarning(noMatchWarning);
                }
            }
            
            // Create success report items only for rows that were actually deleted
            for (RelationshipRowData row : actuallyDeletedRows) {
                try {
                    String entityName = getEntityNameForRow(row);
                    int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", row.getRowNumber());
                    jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                        "Relationship deleted successfully", "info");
                } catch (Exception e) {
                    logger.warn("Failed to create report item for successful delete at row {}: {}", 
                        row.getRowNumber(), e.getMessage());
                }
            }
            return actualDeletedCount;
        }
    }
    
    /**
     * Validate uploaded file
     */
    private void validateFile(Part filePart) throws IOException {
        String fileName = filePart.getSubmittedFileName();
        
        // Check file extension
        if (!fileName.toLowerCase().endsWith(".xlsx") && !fileName.toLowerCase().endsWith(".xls")) {
            throw new IOException("Invalid file type. Only Excel files (.xlsx, .xls) are allowed");
        }
        
        // Check file size
        long fileSize = filePart.getSize();
        if (fileSize > 52428800) {
            throw new IOException("File too large. Maximum size: 50MB");
        }
        if (fileSize == 0) {
            throw new IOException("File is empty");
        }
        
        logger.info("File validation passed: name={}, size={} bytes", fileName, fileSize);
    }
    
    /**
     * Get database connection with retry
     */
    private Connection getConnectionWithRetry(int maxAttempts) throws SQLException {
        int attempts = 0;
        SQLException lastException = null;
        
        while (attempts < maxAttempts) {
            try {
                Connection conn = DatabaseConnection.getConnection();
                if (conn != null && !conn.isClosed()) {
                    logger.debug("Database connection established (attempt {})", attempts + 1);
                    return conn;
                }
            } catch (SQLException e) {
                lastException = e;
                attempts++;
                logger.warn("Connection attempt {} failed: {}", attempts, e.getMessage());
                
                if (attempts < maxAttempts) {
                    try {
                        Thread.sleep(1000L * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new SQLException("Failed to establish database connection after " + maxAttempts + " attempts", lastException);
    }
    
    /**
     * Check if an object is locked (by another user or permanently).
     * @param entityName Entity config name for ModuleResolver (e.g. "glossary", "policy")
     * @param objectId Object ID
     * @param userId Current user ID (locked_by_self is allowed)
     * @return Error message if locked and not by self, null if ok to proceed
     */
    private String checkObjectLocked(String entityName, int objectId, int userId) {
        try {
            // ModuleResolver expects "legalentity" or "legal-entity", EntityConfig uses "legal"
            String moduleEntityType = "legal".equalsIgnoreCase(entityName) ? "legalentity" : entityName;
            int moduleId = ModuleResolver.getModuleId(moduleEntityType);
            Map<String, Object> lockInfo = lockDAO.checkLock(moduleId, objectId, userId);
            String status = (String) lockInfo.get("status");
            if ("no_lock".equals(status) || "locked_by_self".equals(status)) {
                return null;
            }
            String lockedBy = (String) lockInfo.getOrDefault("lockedByName", "another user");
            boolean isPermanent = (Boolean) lockInfo.getOrDefault("isPermanent", false);
            if (isPermanent) {
                return "Object (ID: " + objectId + ") has a permanent lock. Only administrators can edit or unlock it.";
            }
            return "Object (ID: " + objectId + ") is being edited by " + lockedBy + " and is temporarily locked. Please try again later.";
        } catch (SQLException e) {
            logger.warn("Error checking lock for {} {}: {}", entityName, objectId, e.getMessage());
            return null; // On error, allow proceed (fail open)
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown entity type for lock check: {}", entityName);
            return null;
        }
    }
    
    /**
     * Map entity config name (from EntityConfig.getName()) to segment object type
     * used by SegmentDAO.getObjectSegmentId and SegmentValidationService.
     */
    private static String entityNameToSegmentObjectType(String entityName) {
        if (entityName == null || entityName.isEmpty()) return entityName;
        switch (entityName.toLowerCase()) {
            case "glossary": return "Glossary";
            case "policy": return "Policy";
            case "process": return "Process";
            case "legal": return "LegalEntity";
            case "businessarea": return "BusinessArea";
            case "system": return "System";
            case "dataset": return "Dataset";
            case "attribute": return "Attribute";
            case "interface": return "Interface";
            case "geography": return "Geography";
            case "regulator": return "Regulator";
            case "committee": return "Committee";
            case "project": return "Project";
            case "client": return "Client";
            case "product": return "Product";
            case "capability": return "Capability";
            case "people": return "People";
            case "regulation": return "Regulation";
            case "regulatorytheme": return "RegulatoryTheme";
            case "orgunit": return "OrgUnit";
            case "org. unit": return "OrgUnit";
            default: return entityName.substring(0, 1).toUpperCase() + entityName.substring(1).toLowerCase();
        }
    }
    
    /**
     * Check if a table requires an explicit ID field (not AUTO_INCREMENT)
     * Currently only glossary_x_glossary requires this
     */
    private boolean requiresExplicitId(String tableName) {
        return "glossary_x_glossary".equals(tableName) || "process_x_system".equals(tableName) || "product_x_dataset".equals(tableName) || "interface_x_glossary".equals(tableName);
    }
    
    /**
     * Get the next available ID for a table that requires explicit IDs
     * Similar to GlossaryXGlossaryDAO.getNextId()
     */
    private int getNextIdForTable(Connection conn, String tableName) throws SQLException {
        // process_x_system uses lowercase "id" column (per ProcessImpactDAO)
        String sql = "process_x_system".equals(tableName)
            ? "SELECT COALESCE(MAX(id), 0) + 1 AS nextId FROM process_x_system"
            : "SELECT COALESCE(MAX(ID), 0) + 1 AS nextId FROM " + tableName;
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("nextId");
            } else {
                return 1; // Default to 1 if no records exist
            }
        }
    }
    
    /**
     * Send JSON response
     */
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(200);
        response.getWriter().write(gson.toJson(data));
    }
    
    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        response.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Determine job type based on entity and operation
     */
    private String determineJobType(String entityName, String uploadOption) {
        String operation = uploadOption != null ? uploadOption.toUpperCase() : "INSERT";
        return String.format("RELATIONSHIP_BULK_%s", operation);
    }
    
    /**
     * Generate short reference name for job
     */
    private String generateShortReferenceName(String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return "REL_" + System.currentTimeMillis();
        }
        
        // Take first letters of each word, max 10 characters
        String[] words = entityName.split("[\\s_]+");
        StringBuilder ref = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                ref.append(word.charAt(0));
            }
        }
        
        String timestamp = String.valueOf(System.currentTimeMillis() % 100000);
        return ref.toString().toUpperCase() + "_" + timestamp;
    }
    
    /**
     * Update job status based on upload response
     */
    /**
     * Generate and save relationship report Excel file.
     * Uses same columnMappings as upload so report status (e.g. missing relationship type) matches processInsert/processDelete.
     */
    private void generateAndSaveRelationshipReport(int jobId, BulkUploadResponse uploadResponse, 
                                                   RelationshipConfig config, String uploadOption, int userId,
                                                   Map<String, String> columnMappings) {
        try {
            // Report must include every row from the file (success and failure). Never omit rows.
            List<RelationshipRowData> processedRows = uploadResponse.getAllProcessedRows();
            if (processedRows == null || processedRows.isEmpty()) {
                logger.warn("Relationship report for job {}: getAllProcessedRows() is null or empty; report will only include rows from errors/warnings. " +
                    "Ensure processInsert always sets allProcessedRows before returning.", jobId);
                processedRows = new ArrayList<>();
            } else {
                logger.debug("Relationship report for job {}: processing {} rows so report lists every row.", jobId, processedRows.size());
                if (uploadResponse.getTotalRows() > 0 && processedRows.size() < uploadResponse.getTotalRows()) {
                    logger.warn("Relationship report for job {}: processedRows size ({}) is less than totalRows ({}). Some rows may be missing from the report.",
                        jobId, processedRows.size(), uploadResponse.getTotalRows());
                }
            }
            
            List<BulkUploadReportGenerator.ReportRow> reportRows = new ArrayList<>();
            // When no processed rows but totalRows > 0 (e.g. DELETE exception path), build placeholder rows so report is never empty
            if ((processedRows == null || processedRows.isEmpty()) && uploadResponse.getTotalRows() > 0) {
                int totalRows = uploadResponse.getTotalRows();
                for (int rowNum = 1; rowNum <= totalRows; rowNum++) {
                    reportRows.add(new BulkUploadReportGenerator.ReportRow(
                        rowNum, "", "Row " + rowNum + ": Relationship", uploadOption,
                        "Failed", "Row not included in processing report.", null,
                        "Skipped - Row not included in processing report."));
                }
            }
            
            // Convert every RelationshipRowData to ReportRow (no filtering by status; 1-based row numbers match Excel)
            for (RelationshipRowData row : processedRows) {
                try {
                    String entityA;
                    String entityB;
                    try {
                        entityA = getEntityDisplayName(row, "entityA", config);
                        entityB = getEntityDisplayName(row, "entityB", config);
                    } catch (Exception displayEx) {
                        logger.debug("Row {}: getEntityDisplayName failed (unresolved entities?), using fallback for report: {}", row.getRowNumber(), displayEx.getMessage());
                        entityA = "N/A";
                        entityB = "N/A";
                    }
                    // Include row number and source/target so every row is identifiable in the report
                    String relationshipName = "Row " + row.getRowNumber() + ": " + entityA + " - " + entityB;
                    
                    String status = "Success";
                    String errorMsg = null;
                    String warningMsg = null;
                    String actionTaken = "DELETE".equals(uploadOption) ? "Deleted successfully" : "Processed successfully";
                    
                    if (row.hasErrors()) {
                        status = "Failed";
                        errorMsg = String.join("; ", row.getErrors());
                        actionTaken = "Skipped - " + errorMsg;
                    } else if (row.hasWarnings()) {
                        warningMsg = String.join("; ", row.getWarnings());
                        // When the only issue is duplicate (already exists), show clear status/reason
                        boolean isDuplicateOnly = warningMsg != null && (warningMsg.contains("Duplicate relationship") || warningMsg.contains("already exists; not inserted"));
                        if (isDuplicateOnly) {
                            status = "Skipped";
                            actionTaken = "Skipped - relationship already exists; not inserted.";
                        } else {
                            status = row.isValid() ? "Warning" : "Failed";
                            actionTaken = row.isValid() ? "Processed with warnings" : "Skipped - " + warningMsg;
                        }
                        // DELETE: rows that were not actually deleted (0 rows affected) must show as Failed, not Warning
                        if ("DELETE".equals(uploadOption) && warningMsg != null && warningMsg.contains("No matching relationship found to delete")) {
                            status = "Failed";
                            errorMsg = warningMsg;
                            actionTaken = "Skipped - " + errorMsg;
                        }
                        // DELETE: non-existent relationship (existence check failed) must show as Failed with clear reason
                        if ("DELETE".equals(uploadOption) && warningMsg != null && warningMsg.contains("No relationship exists for this combination")) {
                            status = "Failed";
                            errorMsg = warningMsg;
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Early explicit check (DELETE): ensure rows with empty relationship type always get a report line with Failed
                    if ("DELETE".equals(uploadOption) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()) {
                            status = "Failed";
                            errorMsg = "Relationship type is required but not provided.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // When error is "legal not found" with name 'X' and X equals row's relationship type value, show relationship-type resolution message instead
                    if (errorMsg != null && "process_x_legal".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        if (relationshipTypeValue != null && !relationshipTypeValue.trim().isEmpty()
                                && (errorMsg.contains("legal") && errorMsg.contains("not found") && errorMsg.contains("name '"))) {
                            int nameStart = errorMsg.indexOf("name '");
                            if (nameStart >= 0) {
                                nameStart += "name '".length();
                                int nameEnd = errorMsg.indexOf("'", nameStart);
                                if (nameEnd > nameStart) {
                                    String quotedName = errorMsg.substring(nameStart, nameEnd);
                                    if (quotedName.trim().equals(relationshipTypeValue.trim())) {
                                        errorMsg = "Relationship type '" + relationshipTypeValue.trim() + "' could not be resolved. Please use a valid relationship type from the lookup.";
                                        actionTaken = "Skipped - " + errorMsg;
                                    }
                                }
                            }
                        }
                    }
                    // Defensive: ensure rows with missing relationship type always get a report line with Failed (INSERT and DELETE; use same column resolution as processInsert/processDelete)
                    if (config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive (DELETE): ensure rows with empty relationship type always appear in report as Failed
                    if ("DELETE".equals(uploadOption) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Glossary X System rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("glossary_x_system".equals(config.getTableName())) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = "Relationship type is required but not provided.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Interface X Glossary rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("interface_x_glossary".equals(config.getTableName())) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = "Relationship type is required but not provided.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Product X Legal rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("product_x_legal".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: System X Legal rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("system_x_legal".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: System X Client (client_x_system) rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("client_x_system".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Project X Project rows (INSERT and DELETE) with empty or unresolved relationship type must always appear in report as Failed
                    if ("project_x_project".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relTypeVal = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relTypeId = row.getRelationshipTypeId();
                        if (relTypeVal == null || relTypeVal.trim().isEmpty() || relTypeId == null) {
                            status = "Failed";
                            errorMsg = (relTypeId == null && (relTypeVal == null || relTypeVal.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive (DELETE): Project X Project rows with non-existent relationship must show as Failed with clear reason
                    if ("project_x_project".equals(config.getTableName()) && "DELETE".equals(uploadOption) && warningMsg != null
                            && warningMsg.contains("No relationship exists for this combination")) {
                        status = "Failed";
                        errorMsg = warningMsg;
                        actionTaken = "Skipped - " + errorMsg;
                    }
                    // Defensive: Regulation X Product rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("regulation_x_product".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relTypeVal = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relTypeId = row.getRelationshipTypeId();
                        if (relTypeVal == null || relTypeVal.trim().isEmpty() || relTypeId == null) {
                            status = "Failed";
                            errorMsg = (relTypeId == null && (relTypeVal == null || relTypeVal.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive (DELETE): Regulation X Product rows with non-existent relationship must show as Failed with clear reason
                    if ("regulation_x_product".equals(config.getTableName()) && "DELETE".equals(uploadOption) && warningMsg != null
                            && warningMsg.contains("No relationship exists for this combination")) {
                        status = "Failed";
                        errorMsg = warningMsg;
                        actionTaken = "Skipped - " + errorMsg;
                    }
                    // Defensive: Regulation X Regulator rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("regulation_x_regulator".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relTypeValReg = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relTypeIdReg = row.getRelationshipTypeId();
                        if (relTypeValReg == null || relTypeValReg.trim().isEmpty() || relTypeIdReg == null) {
                            status = "Failed";
                            errorMsg = (relTypeIdReg == null && (relTypeValReg == null || relTypeValReg.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relTypeIdReg == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive (DELETE): Regulation X Regulator rows with non-existent relationship must show as Failed with clear reason
                    if ("regulation_x_regulator".equals(config.getTableName()) && "DELETE".equals(uploadOption) && warningMsg != null
                            && (warningMsg.contains("No relationship exists for this combination") || warningMsg.contains("No matching relationship found to delete"))) {
                        status = "Failed";
                        errorMsg = warningMsg;
                        actionTaken = "Skipped - " + errorMsg;
                    }
                    // Defensive: Regulation X Regulatory Theme rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("regulation_x_regulatorytheme".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relTypeValRxt = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relTypeIdRxt = row.getRelationshipTypeId();
                        if (relTypeValRxt == null || relTypeValRxt.trim().isEmpty() || relTypeIdRxt == null) {
                            status = "Failed";
                            errorMsg = (relTypeIdRxt == null && (relTypeValRxt == null || relTypeValRxt.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relTypeIdRxt == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive (DELETE): Regulation X Regulatory Theme rows with non-existent relationship must show as Failed with clear reason
                    if ("regulation_x_regulatorytheme".equals(config.getTableName()) && "DELETE".equals(uploadOption) && warningMsg != null
                            && (warningMsg.contains("No relationship exists for this combination") || warningMsg.contains("No matching relationship found to delete"))) {
                        status = "Failed";
                        errorMsg = warningMsg;
                        actionTaken = "Skipped - " + errorMsg;
                    }
                    // Defensive: Process X System Interface rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("process_x_interface".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Policy X System rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("policy_x_system".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Attribute rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("process_x_attribute".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Product (product_x_process) rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("product_x_process".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Client (client_x_process) rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("client_x_process".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Data Set rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("process_x_dataset".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Glossary (glossary_x_process) rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("glossary_x_process".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Legal Entity rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("process_x_legal".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Product X Business Area rows with empty or unresolved relationship type must always appear in report as Failed
                    if ("product_x_businessarea".equals(config.getTableName()) && config.isRequiresRelationType()) {
                        String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                        Integer relationshipTypeId = row.getRelationshipTypeId();
                        if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()
                                || relationshipTypeId == null) {
                            status = "Failed";
                            errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                ? "Relationship type is required but not provided."
                                : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Capability X System rows with null capability or system must never show as Success
                    if ("Success".equals(status) && "capability_x_system".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer capId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer sysId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (capId == null || sysId == null) {
                            status = "Failed";
                            errorMsg = "Capability and System are required; one or both were not resolved.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Capability X Legal Entity rows with null capability or legal must never show as Success
                    if ("Success".equals(status) && "capability_x_legal".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer capId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer legalId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (capId == null || legalId == null) {
                            status = "Failed";
                            errorMsg = "Capability and Legal Entity are required; one or both were not resolved.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Capability X Client rows with null capability or client must never show as Success
                    if ("Success".equals(status) && "capability_x_client".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer capId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer clientId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (capId == null || clientId == null) {
                            status = "Failed";
                            errorMsg = "Capability and Client are required; one or both were not resolved.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Data Set X Client rows with null dataset must never show as Success
                    if ("Success".equals(status) && "client_x_dataset".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer datasetId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (datasetId == null) {
                            status = "Failed";
                            errorMsg = "Dataset is required; not inserted.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Data Set X Legal Entity rows with null dataset or legal must never show as Success
                    if ("Success".equals(status) && "dataset_x_legal".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer datasetId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer legalId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (datasetId == null || legalId == null) {
                            status = "Failed";
                            errorMsg = "Data Set and Legal Entity are required; one or both were not resolved.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Data Set rows with null process or dataset must never show as Success
                    if ("Success".equals(status) && "process_x_dataset".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer processId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer datasetId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (processId == null || datasetId == null) {
                            status = "Failed";
                            errorMsg = "Process and Data Set are required; not inserted.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Glossary (glossary_x_process) rows with null glossary or process must never show as Success
                    if ("Success".equals(status) && "glossary_x_process".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer glossaryId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer processId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (glossaryId == null || processId == null) {
                            status = "Failed";
                            errorMsg = "Glossary and Process are required; not inserted.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Legal Entity rows with null process or legal must never show as Success
                    if ("Success".equals(status) && "process_x_legal".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer entityAId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer entityBId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (entityAId == null || entityBId == null) {
                            status = "Failed";
                            errorMsg = "Process and Legal Entity are required; not inserted.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Process X Process rows with null process or predecessor process must never show as Success
                    if ("Success".equals(status) && "process_x_process".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer entityAId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer entityBId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (entityAId == null || entityBId == null) {
                            status = "Failed";
                            errorMsg = "Process and Predecessor Process are required; not inserted.";
                            actionTaken = "Skipped - " + errorMsg;
                        }
                    }
                    // Defensive: Product X Client rows with null client or product provided but unresolved must never show as Success
                    if ("Success".equals(status) && "product_x_client".equals(config.getTableName())) {
                        Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                        Integer entityAId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                        Integer entityBId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                        if (entityBId == null) {
                            status = "Failed";
                            errorMsg = "Client is required; not inserted.";
                            actionTaken = "Skipped - " + errorMsg;
                        } else if (entityAId == null) {
                            // Check if row has product data (product name/ref column with non-empty value)
                            boolean hasProductData = false;
                            Map<String, String> orig = row.getOriginalValues();
                            if (orig != null) {
                                for (Map.Entry<String, String> e : orig.entrySet()) {
                                    String key = e.getKey();
                                    String val = e.getValue();
                                    if (key != null && val != null && !val.trim().isEmpty()) {
                                        String k = key.toLowerCase();
                                        if (k.contains("product") && (k.contains("ref") || k.contains("name"))) {
                                            hasProductData = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (hasProductData) {
                                status = "Failed";
                                errorMsg = errorMsg != null ? errorMsg : "Product was provided but could not be resolved.";
                                actionTaken = "Skipped - " + errorMsg;
                            }
                        }
                    }
                    // Defensive: Policy X Project rows with null policy or project, or missing relationship type, must never show as Success
                    if ("policy_x_project".equals(config.getTableName())) {
                        if (row.hasErrors()) {
                            status = "Failed";
                            errorMsg = errorMsg != null ? errorMsg : String.join("; ", row.getErrors());
                            actionTaken = "Skipped - " + (errorMsg != null ? errorMsg : "Validation failed.");
                        } else if ("Success".equals(status)) {
                            Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                            Integer policyId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                            Integer projectId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                            if (policyId == null || projectId == null) {
                                status = "Failed";
                                errorMsg = "Policy and Project are required; one or both were not resolved.";
                                actionTaken = "Skipped - " + errorMsg;
                            }
                        }
                    }
                    // Defensive: Policy X Project DELETE - ensure every row gets a report line (empty relationship type -> Failed; successfully deleted -> Success)
                    if ("DELETE".equals(uploadOption) && "policy_x_project".equals(config.getTableName())) {
                        if (row.hasErrors()) {
                            status = "Failed";
                            errorMsg = errorMsg != null ? errorMsg : String.join("; ", row.getErrors());
                            actionTaken = "Skipped - " + (errorMsg != null ? errorMsg : "Validation failed.");
                        } else if (config.isRequiresRelationType()) {
                            String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                            Integer relationshipTypeId = row.getRelationshipTypeId();
                            if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty() || relationshipTypeId == null) {
                                status = "Failed";
                                errorMsg = "Relationship type is required but not provided.";
                                actionTaken = "Skipped - " + errorMsg;
                            }
                        }
                        if (!"Failed".equals(status)) {
                            Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                            Integer policyId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                            Integer projectId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                            if (policyId != null && projectId != null) {
                                status = "Success";
                                actionTaken = "Processed successfully";
                            }
                        }
                    }
                    // Defensive: Regulation X Policy DELETE - ensure every row gets a report line (empty relationship type, empty reg/policy, etc. -> Failed)
                    if ("DELETE".equals(uploadOption) && "regulation_x_policy".equals(config.getTableName())) {
                        if (row.hasErrors()) {
                            status = "Failed";
                            errorMsg = errorMsg != null ? errorMsg : String.join("; ", row.getErrors());
                            actionTaken = "Skipped - " + (errorMsg != null ? errorMsg : "Validation failed.");
                        } else if (config.isRequiresRelationType()) {
                            String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                            Integer relationshipTypeId = row.getRelationshipTypeId();
                            if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty() || relationshipTypeId == null) {
                                status = "Failed";
                                errorMsg = "Relationship type is required but not provided.";
                                actionTaken = "Skipped - " + errorMsg;
                            }
                        }
                        if (!"Failed".equals(status)) {
                            Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                            Integer entityAId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                            Integer entityBId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                            if (entityAId != null && entityBId != null) {
                                status = "Success";
                                actionTaken = "Deleted successfully";
                            }
                        }
                    }
                    // Defensive: Regulation X Project - ensure every row gets a report line (empty reg/project, wrong/empty rel type, duplicate, segment validation, incompatible ref -> Failed)
                    if ("regulation_x_project".equals(config.getTableName())) {
                        if (row.hasErrors()) {
                            status = "Failed";
                            errorMsg = errorMsg != null ? errorMsg : String.join("; ", row.getErrors());
                            actionTaken = "Skipped - " + (errorMsg != null ? errorMsg : "Validation failed.");
                        } else if (config.isRequiresRelationType()) {
                            String relationshipTypeValue = RelationshipExcelParser.findRelationshipTypeValue(row, columnMappings);
                            Integer relationshipTypeId = row.getRelationshipTypeId();
                            if (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty() || relationshipTypeId == null) {
                                status = "Failed";
                                errorMsg = (relationshipTypeId == null && (relationshipTypeValue == null || relationshipTypeValue.trim().isEmpty()))
                                    ? "Relationship type is required but not provided."
                                    : (relationshipTypeId == null ? "Relationship type could not be resolved." : "Relationship type is required but not provided.");
                                actionTaken = "Skipped - " + errorMsg;
                            }
                        }
                        if (!"Failed".equals(status)) {
                            Map<String, Integer> resolvedIds = row.getResolvedEntityIds();
                            Integer entityAId = resolvedIds != null ? resolvedIds.get("entityA") : null;
                            Integer entityBId = resolvedIds != null ? resolvedIds.get("entityB") : null;
                            if (entityAId != null && entityBId != null) {
                                status = "Success";
                                actionTaken = "INSERT".equals(uploadOption) ? "Processed successfully" : "Deleted successfully";
                            } else if (entityAId == null || entityBId == null) {
                                status = "Failed";
                                errorMsg = errorMsg != null ? errorMsg : "Regulation and Project are required; one or both were not resolved.";
                                actionTaken = "Skipped - " + (errorMsg != null ? errorMsg : "Validation failed.");
                            }
                        }
                    }
                    
                    String objectId = "";
                    Map<String, Integer> resolvedEntityIds = row.getResolvedEntityIds();
                    if (resolvedEntityIds != null && !resolvedEntityIds.isEmpty()) {
                        Integer entityAId = resolvedEntityIds.get("entityA");
                        Integer entityBId = resolvedEntityIds.get("entityB");
                        if (entityAId != null && entityBId != null) {
                            objectId = entityAId + " - " + entityBId;
                        }
                    }
                    
                    reportRows.add(new BulkUploadReportGenerator.ReportRow(
                        row.getRowNumber(),
                        objectId,
                        relationshipName,
                        uploadOption,
                        status,
                        errorMsg,
                        warningMsg,
                        actionTaken
                    ));
                } catch (Exception e) {
                    logger.warn("Error generating report for row {}: {}", row.getRowNumber(), e.getMessage());
                    String reportErrorMsg = "Error generating report for this row";
                    String reportAction = "Skipped - " + reportErrorMsg;
                    if (row.hasErrors() && !row.getErrors().isEmpty()) {
                        reportErrorMsg = String.join("; ", row.getErrors());
                        reportAction = "Skipped - " + reportErrorMsg;
                    }
                    reportRows.add(new BulkUploadReportGenerator.ReportRow(
                        row.getRowNumber(),
                        "",
                        "Row " + row.getRowNumber() + ": Relationship",
                        uploadOption,
                        "Failed",
                        reportErrorMsg,
                        null,
                        reportAction
                    ));
                }
            }
            
            // Add rows from errors/warnings that might not be in processedRows
            for (ValidationIssue error : uploadResponse.getErrors()) {
                // Check if already added
                boolean alreadyAdded = reportRows.stream()
                    .anyMatch(r -> r.getRowNumber() == error.getRow());
                if (!alreadyAdded) {
                    reportRows.add(new BulkUploadReportGenerator.ReportRow(
                        error.getRow(),
                        "",
                        "Row " + error.getRow() + ": Relationship",
                        uploadOption,
                        "Failed",
                        error.getMessage(),
                        null,
                        "Skipped - " + error.getMessage()
                    ));
                }
            }
            
            for (ValidationIssue warning : uploadResponse.getWarnings()) {
                boolean alreadyAdded = reportRows.stream()
                    .anyMatch(r -> r.getRowNumber() == warning.getRow());
                if (!alreadyAdded) {
                    reportRows.add(new BulkUploadReportGenerator.ReportRow(
                        warning.getRow(),
                        "",
                        "Row " + warning.getRow() + ": Relationship",
                        uploadOption,
                        "Warning",
                        null,
                        warning.getMessage(),
                        "Warning - " + warning.getMessage()
                    ));
                }
            }
            
            // Missing rows safeguard: ensure every row from processedRows has a report entry (so no row is omitted from the report)
            Set<Integer> reportedRowNumbers = new HashSet<>();
            for (BulkUploadReportGenerator.ReportRow r : reportRows) {
                reportedRowNumbers.add(r.getRowNumber());
            }
            for (RelationshipRowData row : processedRows) {
                int rn = row.getRowNumber();
                if (!reportedRowNumbers.contains(rn)) {
                    reportRows.add(new BulkUploadReportGenerator.ReportRow(
                        rn, "", "Row " + rn + ": Relationship", uploadOption,
                        "Failed", "Row not included in processing report.", null,
                        "Skipped - Row not included in processing report."));
                    reportedRowNumbers.add(rn);
                }
            }
            // If we still have fewer report rows than total file rows, add placeholders for missing row numbers (1..totalRows)
            int totalRowsForReport = uploadResponse.getTotalRows();
            if (totalRowsForReport > 0 && reportRows.size() < totalRowsForReport) {
                for (int rowNum = 1; rowNum <= totalRowsForReport; rowNum++) {
                    if (!reportedRowNumbers.contains(rowNum)) {
                        reportRows.add(new BulkUploadReportGenerator.ReportRow(
                            rowNum, "", "Row " + rowNum + ": Relationship", uploadOption,
                            "Failed", "Row not included in processing report.", null,
                            "Skipped - Row not included in processing report."));
                        reportedRowNumbers.add(rowNum);
                    }
                }
            }
            
            // Ensure every input row has exactly one report line (1..maxRow); fill gaps with fallback.
            // ProcessedRows use Excel row numbers (e.g. 2..10 for 9 rows), so cap by max(row numbers in report) so no row is dropped.
            int totalRows = uploadResponse.getTotalRows();
            if (totalRows > 0) {
                Map<Integer, BulkUploadReportGenerator.ReportRow> rowNumberToReportRow = new HashMap<>();
                int maxRow = totalRows;
                for (BulkUploadReportGenerator.ReportRow r : reportRows) {
                    rowNumberToReportRow.putIfAbsent(r.getRowNumber(), r);
                    maxRow = Math.max(maxRow, r.getRowNumber());
                }
                String fallbackMessage = "Row not included in processing";
                List<BulkUploadReportGenerator.ReportRow> reportRowsFinal = new ArrayList<>();
                for (int rowNum = 1; rowNum <= maxRow; rowNum++) {
                    BulkUploadReportGenerator.ReportRow r = rowNumberToReportRow.get(rowNum);
                    if (r == null) {
                        r = new BulkUploadReportGenerator.ReportRow(
                            rowNum, "", "Row " + rowNum + ": Relationship", uploadOption,
                            "Failed", fallbackMessage, null, "Skipped - " + fallbackMessage);
                    }
                    reportRowsFinal.add(r);
                }
                reportRows = reportRowsFinal;
            }
            
            // Sort by row number so report shows rows in same order as Excel file (stable, predictable)
            reportRows.sort(Comparator.comparingInt(BulkUploadReportGenerator.ReportRow::getRowNumber));
            
            // Log if report row count does not match processed rows (helps detect missing rows)
            int expectedFromProcessed = (processedRows != null && !processedRows.isEmpty()) ? processedRows.size() : 0;
            if (expectedFromProcessed > 0 && reportRows.size() < expectedFromProcessed) {
                logger.warn("Relationship report for job {}: report has {} rows but getAllProcessedRows() had {}; some rows may be missing.",
                    jobId, reportRows.size(), expectedFromProcessed);
            }
            
            // Ensure we never write an empty report when job had content: add one placeholder row if still empty but totalRows > 0
            if (reportRows.isEmpty() && uploadResponse.getTotalRows() > 0) {
                reportRows.add(new BulkUploadReportGenerator.ReportRow(
                    1, "", "Row 1: Relationship", uploadOption,
                    "Failed", "No rows were included in the report.", null,
                    "Skipped - No rows were included in the report."));
            }
            
            if (!reportRows.isEmpty()) {
                BulkUploadReportGenerator reportGenerator = new BulkUploadReportGenerator();
                byte[] reportBytes = reportGenerator.generateReport(reportRows, config.getDisplayName(), jobId);
                
                // Generate file name
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fileName = "relationship_" + config.getKey() + "_report_" + jobId + "_" + timestamp + ".xlsx";
                String basePath = getBasePath();
                String storagePath = basePath + fileName;
                
                // Ensure directory exists
                File directory = new File(basePath);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                
                // Write file to disk
                File reportFile = new File(storagePath);
                Files.write(reportFile.toPath(), reportBytes);
                
                // Save to Job_Resource_FileName
                jobDAO.createJobResourceFile(jobId, fileName, fileName, storagePath, true, 90);
                
                logger.info("Relationship report generated and saved for job {}: {}", jobId, storagePath);
            }
            
        } catch (Exception e) {
            logger.error("Error generating relationship report for job {}: {}", jobId, e.getMessage(), e);
            // Don't throw - report generation failure shouldn't fail the whole job
        }
    }
    
    /**
     * Get entity display name from row data for report description.
     * Uses Excel column names (e.g. "Source Glossary Name", "Target Glossary Name") when config has entity roles.
     */
    private String getEntityDisplayName(RelationshipRowData row, String entityKey, RelationshipConfig config) {
        Map<String, String> originalValues = row.getOriginalValues();
        if (originalValues == null) {
            return "Unknown";
        }
        
        String role = "entityA".equals(entityKey) ? config.getEntityARole() : config.getEntityBRole();
        EntityConfig entityConfig = "entityA".equals(entityKey) ? config.getEntityA() : config.getEntityB();
        String entityName = entityConfig != null ? entityConfig.getName() : "";
        
        // When config has role (e.g. source/target for glossary_x_glossary), match Excel headers like "Source Glossary Name", "Target Glossary Ref."
        if (role != null && !role.isEmpty() && entityName != null) {
            String roleLower = role.toLowerCase();
            String entityLower = entityName.toLowerCase();
            for (Map.Entry<String, String> entry : originalValues.entrySet()) {
                String key = entry.getKey();
                if (key == null) continue;
                String keyLower = key.toLowerCase();
                String value = entry.getValue();
                if (value == null || value.trim().isEmpty()) continue;
                if (keyLower.startsWith(roleLower + " ") || keyLower.startsWith(roleLower + ".") || keyLower.startsWith(roleLower)) {
                    if (keyLower.contains(entityLower) && (keyLower.contains("name") || keyLower.contains("ref"))) {
                        if (!keyLower.contains("parent")) {
                            return value.trim();
                        }
                    }
                }
            }
        }
        
        // Try to find entity name in original values by DB column name
        if (entityConfig != null) {
            String nameColumn = entityConfig.getNameColumn();
            if (nameColumn != null && originalValues.containsKey(nameColumn)) {
                String value = originalValues.get(nameColumn);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        
        // Try common column name patterns
        String[] possibleColumns = {
            entityKey + " Name", entityKey + "Name",
            entityKey.substring(0, 1).toUpperCase() + entityKey.substring(1) + " Name"
        };
        for (String col : possibleColumns) {
            if (originalValues.containsKey(col)) {
                String value = originalValues.get(col);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        
        // Product X Legal: match Excel columns "Product Name" / "Legal Short Name" for report display
        if (config != null && "product_x_legal".equals(config.getTableName())) {
            for (Map.Entry<String, String> entry : originalValues.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null || value.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if ("entityA".equals(entityKey)) {
                    if (k.contains("product") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                        return value.trim();
                    }
                } else if ("entityB".equals(entityKey)) {
                    if (k.contains("legal") && (k.contains("short") || k.contains("name")) && !k.contains("relationship")) {
                        return value.trim();
                    }
                }
            }
        }
        
        // Fallback to first non-empty value
        for (String value : originalValues.values()) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        
        return "Unknown";
    }
    
    /**
     * Generate report from rows (helper method). Pass columnMappings so report status (e.g. missing relationship type) matches processInsert/processDelete.
     */
    private void generateReportFromRows(List<RelationshipRowData> rows, int jobId,
                                       RelationshipConfig config, String uploadOption, int userId,
                                       Map<String, String> columnMappings) {
        BulkUploadResponse tempResponse = new BulkUploadResponse();
        tempResponse.setAllProcessedRows(rows);
        generateAndSaveRelationshipReport(jobId, tempResponse, config, uploadOption, userId, columnMappings);
    }
    
    private void updateJobStatus(int jobId, BulkUploadResponse uploadResponse, String uploadOption) {
        try {
            // Ensure failed count is never 0 when there are errors/warnings so BULK_COUNTS and GUI show correct count
            int errorAndWarningCount = uploadResponse.getErrors().size() + uploadResponse.getWarnings().size();
            if (errorAndWarningCount > 0) {
                uploadResponse.setFailed(Math.max(uploadResponse.getFailed(), errorAndWarningCount));
            }
            String status;
            int failed = uploadResponse.getFailed();
            int operationCount = "DELETE".equals(uploadOption) ? uploadResponse.getDeleted() : uploadResponse.getInserted();
            if (uploadResponse.isCancelledDueToWarning()) {
                status = "Failed";
            } else if (operationCount > 0 && failed > 0) {
                status = "Partially Completed";
            } else {
                status = uploadResponse.isSuccess() ? "Completed" : "Failed";
            }
            String message = uploadResponse.getMessage();
            String operationVerb = "INSERT".equals(uploadOption) ? "inserted" : "deleted";
            
            if (message == null || message.isEmpty()) {
                if ("Partially Completed".equals(status)) {
                    message = String.format("Partially completed: %d %s, %d failed",
                        operationCount, operationVerb, failed);
                } else if (uploadResponse.isSuccess()) {
                    message = String.format("Successfully %s %d relationships, skipped %d", 
                        operationVerb, operationCount, uploadResponse.getSkipped());
                } else if (uploadResponse.isCancelledDueToWarning()) {
                    message = "Upload cancelled due to warning";
                } else {
                    message = String.format("Upload failed. Errors: %d, Warnings: %d", 
                        uploadResponse.getErrors().size(), uploadResponse.getWarnings().size());
                }
            }
            
            // Append parseable counts so status API can return correct inserted/deleted/failed (matches direct upload response)
            int insertedCount;
            int deletedCount;
            if ("DELETE".equals(uploadOption)) {
                insertedCount = 0;
                deletedCount = uploadResponse.getDeleted();
            } else {
                deletedCount = 0;
                insertedCount = "INSERT".equals(uploadOption) ? uploadResponse.getInserted() : 0;
            }
            String countsSuffix = String.format("\nBULK_COUNTS:inserted=%d,failed=%d,updated=0,deleted=%d",
                insertedCount, failed, deletedCount);
            String messageWithCounts = message + countsSuffix;

            boolean isCompleted = "Completed".equals(status) || "Partially Completed".equals(status);
            jobDAO.updateJobStatus(jobId, status, isCompleted);
            jobDAO.createJobProgress(jobId, 100, status, messageWithCounts);
            
            if ("DELETE".equals(uploadOption)) {
                logger.debug("Job {} DELETE BULK_COUNTS: inserted=0, deleted={}", jobId, deletedCount);
            }
            logger.info("Job {} updated with status: {}", jobId, status);
        } catch (Exception e) {
            logger.error("Failed to update job status for job {}: {}", jobId, e.getMessage(), e);
        }
    }
    
    /**
     * Clone a glossary row for pending changes
     * Returns the ID of the cloned row, or null on error
     */
    private Integer cloneGlossaryRow(Connection conn, int originalId) throws SQLException {
        String sql = "INSERT INTO glossary (" +
                "Name, Description, Format, LDM, Business_Logic, Examples, Ref_Number, " +
                "Format_type, Parent_ID, Status, Lifecycle, Is_Public, Type, " +
                "Security_Classification, KDE, Confidentiality_Rating, Integrity_Rating, " +
                "Availability_Rating, CreatedBy_ID, Created_Datetime, Last_updated_userID, Last_Updated_Datetime" +
                ") SELECT " +
                "Name, Description, Format, LDM, Business_Logic, Examples, Ref_Number, " +
                "Format_type, Parent_ID, Status, Lifecycle, Is_Public, Type, " +
                "Security_Classification, KDE, Confidentiality_Rating, Integrity_Rating, " +
                "Availability_Rating, CreatedBy_ID, Created_Datetime, Last_updated_userID, Last_Updated_Datetime " +
                "FROM glossary WHERE ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Get the parent ID for an object based on its type
     * @param conn Database connection
     * @param objectId The object ID
     * @param objectType The object type (e.g., "System", "Project", "Glossary")
     * @return Parent ID if exists, null otherwise
     * @throws SQLException If database error occurs
     */
    private Integer getObjectParentId(Connection conn, int objectId, String objectType) throws SQLException {
        String tableName = getTableNameForObjectType(objectType);
        String parentColumn = getParentColumnForObjectType(objectType);
        
        if (tableName == null || parentColumn == null) {
            // Object type doesn't support parent relationships
            return null;
        }
        
        String sql = "SELECT " + parentColumn + " FROM " + tableName + " WHERE id = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, objectId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Integer parentId = rs.getObject(parentColumn, Integer.class);
                    if (rs.wasNull()) {
                        return null;
                    }
                    return parentId;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get the table name for an object type
     * @param objectType The object type
     * @return Table name or null if not supported
     */
    private String getTableNameForObjectType(String objectType) {
        return switch (objectType) {
            case "Glossary" -> "glossary";
            case "Policy" -> "policy";
            case "System" -> "system";
            case "Regulation" -> "regulation";
            case "Project" -> "project";
            case "Process" -> "process";
            case "OrgUnit" -> "org_unit";
            case "Client" -> "client";
            case "Product" -> "product";
            case "Dataset" -> "dataset";
            case "Capability" -> "capability";
            case "Committee" -> "committee";
            case "BusinessArea" -> "business_area";
            default -> null;
        };
    }
    
    /**
     * Get the parent column name for an object type
     * @param objectType The object type
     * @return Parent column name or null if not supported
     */
    private String getParentColumnForObjectType(String objectType) {
        return switch (objectType) {
            case "Glossary" -> "Parent_ID";
            case "Policy" -> "ParentID";
            case "System" -> "parent_id";
            case "Regulation" -> "parent_id";
            case "Project" -> "parent_id";
            case "Process" -> "parentid"; // process table uses parentid
            case "OrgUnit" -> "Parent_ID";
            case "Client" -> "Parent_ID";
            case "Product" -> "parent_id";
            case "Dataset" -> "System_ID"; // Dataset uses System_ID as parent
            case "Capability" -> "Parent_ID";
            case "Committee" -> "Parent_ID";
            case "BusinessArea" -> "Parent_ID";
            default -> null;
        };
    }
}

