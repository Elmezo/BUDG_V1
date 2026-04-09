package com.example.budg_v2.bulk.roles;

import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.bulk.objects.BulkUploadReportGenerator;
import com.example.budg_v2.bulk.roles.base.RoleUploadHandler;
import com.example.budg_v2.bulk.roles.handlers.*;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.JobReportItem;
import com.example.budg_v2.model.JobReportItemMessage;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.HttpClientUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@WebServlet("/api/bulk/role/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class RoleBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(RoleBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";

    private static String getBasePath() {
        return BulkPathUtil.getBulkPathForEntity("roles");
    }

    private final JobDAO jobDAO = new JobDAO();

    // Registry of role handlers
    private static final Map<String, RoleUploadHandler> ROLE_HANDLERS = new HashMap<>();
    
    static {
        // Register all role handlers in alphabetical order
        registerHandler(new BusinessAreaRoleHandler());
        registerHandler(new CapabilityRoleHandler());
        registerHandler(new ClientRoleHandler());
        registerHandler(new CommitteeRoleHandler());
        registerHandler(new DataSetRoleHandler());
        registerHandler(new GlossaryRoleHandler());
        registerHandler(new InterfaceRoleHandler());
        registerHandler(new LegalEntityRoleHandler());
        registerHandler(new PolicyRoleHandler());
        registerHandler(new ProcessRoleHandler());
        registerHandler(new ProductRoleHandler());
        registerHandler(new ProjectRoleHandler());
        registerHandler(new RegulationRoleHandler());
        registerHandler(new SystemRoleHandler());
    }
    
    private static void registerHandler(RoleUploadHandler handler) {
        ROLE_HANDLERS.put(handler.getRoleType(), handler);
        logger.info("Registered role handler: {}", handler.getRoleType());
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(request, response);

        logger.info("Role bulk upload request received");

        int jobId = -1;
        String storagePath = null;

        try {
            // Step 1: Extract form data
            String uploadType = request.getParameter("uploadType");
            String entity = request.getParameter("entity");
            String uploadOption = request.getParameter("uploadOption");
            String errorHandling = request.getParameter("errorHandling");
            String userIdStr = request.getParameter("userId");
            String columnMappingsStr = request.getParameter("columnMappings");

            logger.info("Upload parameters - Type: {}, Entity: {}, Option: {}, ErrorHandling: {}, UserId: {}",
                    uploadType, entity, uploadOption, errorHandling, userIdStr);

            if (uploadOption == null || userIdStr == null) {
                sendErrorResponse(response, "Missing required parameters: uploadOption or userId", 400);
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(userIdStr);
            } catch (NumberFormatException e) {
                sendErrorResponse(response, "Invalid userId format", 400);
                return;
            }

            // Step 2: Get uploaded file
            Part filePart = request.getPart("file");
            if (filePart == null) {
                sendErrorResponse(response, "No file uploaded", 400);
                return;
            }

            String originalFileName = getFileName(filePart);
            logger.info("Uploaded file: {}", originalFileName);

            // Step 3: Save file to disk
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String uuid = UUID.randomUUID().toString().substring(0, 6);
            String fileName = "role_" + timestamp + "_" + uuid + ".xlsx";

            String basePath = getBasePath();
            File directory = new File(basePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(basePath + fileName);
            filePart.write(file.getAbsolutePath());
            storagePath = basePath + fileName;

            logger.info("File saved to: {}", storagePath);

            // Step 4: Create job record
            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Role";
            String jobType = determineJobType(entityName, uploadOption);
            String referenceName = generateShortReferenceName(entityName);
            jobId = jobDAO.createJob(jobType, referenceName, 0, "Pending", userId);
            logger.info("Job created with ID: {} and reference: {}", jobId, referenceName);

            // Step 5: Save metadata
            try {
                String metadataFileName = fileName.replace(".xlsx", ".json");
                File metadataFile = new File(getBasePath() + metadataFileName);

                JsonObject metadata = new JsonObject();
                metadata.addProperty("job_id", jobId);
                metadata.addProperty("user_id", userId);
                metadata.addProperty("entity", entityName);
                metadata.addProperty("upload_option", uploadOption);
                metadata.addProperty("error_handling", errorHandling);
                metadata.addProperty("uploaded_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
                metadata.addProperty("reference_name", referenceName);
                metadata.addProperty("original_file_name", originalFileName);
                metadata.addProperty("storage_path", storagePath);

                Files.writeString(metadataFile.toPath(), gson.toJson(metadata));
                logger.info("Metadata JSON saved to: {}", metadataFile.getAbsolutePath());
            } catch (Exception metaEx) {
                logger.warn("Failed to save metadata JSON file: {}", metaEx.getMessage());
            }

            // Step 6: Store file path in job_resource_file
            jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
            jobDAO.createJobProgress(jobId, 0, "Validating", "File uploaded, starting validation");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Pending", 5, "File uploaded, starting validation");

            // Step 7: Call Python validation service
            JsonObject validationRequest = new JsonObject();
            File absoluteFile = new File(storagePath).getAbsoluteFile();
            validationRequest.addProperty("file_path", absoluteFile.getAbsolutePath());
            validationRequest.addProperty("upload_type", "Role");
            validationRequest.addProperty("upload_option", uploadOption);
            validationRequest.addProperty("entity", entityName);
            validationRequest.addProperty("user_id", userId);

            if (columnMappingsStr != null && !columnMappingsStr.trim().isEmpty()) {
                try {
                    JsonObject frontendMappings = gson.fromJson(columnMappingsStr, JsonObject.class);
                    JsonObject pythonMappings = new JsonObject();
                    for (String fieldName : frontendMappings.keySet()) {
                        com.google.gson.JsonElement element = frontendMappings.get(fieldName);
                        if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
                            String excelColumn = element.getAsString();
                            if (excelColumn != null && !excelColumn.trim().isEmpty()) {
                                pythonMappings.addProperty(excelColumn, fieldName);
                            }
                        }
                    }
                    if (pythonMappings.size() > 0) {
                        validationRequest.add("column_mappings", pythonMappings);
                        logger.info("Column mappings applied: {} mappings", pythonMappings.size());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse column mappings: {}", e.getMessage(), e);
                }
            }

            logger.info("Calling Python validation service: {}", PYTHON_SERVICE_URL);
            JsonObject validationResponse;
            try {
                validationResponse = HttpClientUtil.postJsonGetJson(PYTHON_SERVICE_URL, validationRequest);
            } catch (IOException e) {
                String userFriendlyMessage = "Validation service error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", userFriendlyMessage);
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, userFriendlyMessage, 0, 0, 0, 0);
                sendErrorResponse(response, userFriendlyMessage, 503);
                return;
            }

            // Log validation response structure for debugging
            logger.info("Validation response keys: {}", validationResponse.keySet());
            if (validationResponse.has("total_rows")) {
                logger.info("Validation response total_rows: {}", validationResponse.get("total_rows").getAsInt());
            }
            if (validationResponse.has("valid_rows")) {
                logger.info("Validation response valid_rows: {}", validationResponse.get("valid_rows").getAsInt());
            }
            if (validationResponse.has("invalid_rows")) {
                logger.info("Validation response invalid_rows: {}", validationResponse.get("invalid_rows").getAsInt());
            }

            String validationStatus = validationResponse.get("status").getAsString();
            logger.info("Validation response status: {}", validationStatus);

            // Extract validated data and errors before checking status
            // Python service returns "data" field, not "normalized_data"
            JsonArray normalizedRows = null;
            if (validationResponse.has("normalized_data")) {
                normalizedRows = validationResponse.getAsJsonArray("normalized_data");
                logger.debug("Found normalized_data field in validation response");
            } else if (validationResponse.has("data")) {
                normalizedRows = validationResponse.getAsJsonArray("data");
                logger.debug("Found data field in validation response");
            } else {
                logger.warn("Neither normalized_data nor data field found in validation response");
                normalizedRows = new JsonArray(); // Empty array if neither exists
            }
            
            if (normalizedRows == null) {
                logger.warn("normalizedRows is null, initializing empty array");
                normalizedRows = new JsonArray();
            }
            
            logger.info("Extracted {} valid rows from validation response", normalizedRows.size());

            // Total rows in file (data rows only); Excel data row numbers are 2..totalRowsFromFile+1 (row 1 = header)
            int totalRowsFromFile = validationResponse.has("total_rows") && validationResponse.get("total_rows").isJsonPrimitive()
                ? validationResponse.get("total_rows").getAsInt() : 0;

            // Support both "errors" and "validation_errors" so no key mismatch causes errors to be skipped
            JsonArray errors = new JsonArray();
            if (validationResponse.has("errors") && validationResponse.get("errors").isJsonArray()) {
                errors = validationResponse.getAsJsonArray("errors");
            }
            if (validationResponse.has("validation_errors") && validationResponse.get("validation_errors").isJsonArray()) {
                JsonArray validationErrors = validationResponse.getAsJsonArray("validation_errors");
                for (int k = 0; k < validationErrors.size(); k++) {
                    errors.add(validationErrors.get(k));
                }
            }
            int errorCount = errors.size();
            logger.info("Validation response errors count: {}", errorCount);
            if (("invalid".equals(validationStatus) || "error".equals(validationStatus)) && errors.size() > 0) {
                try {
                    if (errors.get(0).isJsonObject()) {
                        JsonObject first = errors.get(0).getAsJsonObject();
                        String firstMsg = (first.has("message") && !first.get("message").isJsonNull())
                            ? first.get("message").getAsString() : "";
                        int firstRow = first.has("row") && first.get("row").isJsonPrimitive()
                            ? (first.get("row").getAsJsonPrimitive().isNumber() ? first.get("row").getAsInt() : 0) : 0;
                        logger.info("First validation error: row={}, message={}", firstRow,
                            firstMsg != null && firstMsg.length() > 100 ? firstMsg.substring(0, 100) + "..." : firstMsg);
                    }
                } catch (Exception e) {
                    logger.debug("Could not log first validation error: {}", e.getMessage());
                }
            }
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
            logger.info("Cancel on Warning setting: {}", cancelOnWarning);

            // Persist every validation error with robust row/field/message extraction
            for (int i = 0; i < errors.size(); i++) {
                JsonObject errObj;
                try {
                    if (!errors.get(i).isJsonObject()) continue;
                    errObj = errors.get(i).getAsJsonObject();
                } catch (Exception e) {
                    logger.warn("Skipping non-object validation error at index {}", i, e);
                    continue;
                }
                int rowNum = 0;
                if (errObj.has("row")) {
                    try {
                        if (errObj.get("row").isJsonPrimitive() && errObj.get("row").getAsJsonPrimitive().isNumber()) {
                            rowNum = errObj.get("row").getAsInt();
                        } else if (errObj.get("row").isJsonPrimitive() && errObj.get("row").getAsJsonPrimitive().isString()) {
                            rowNum = Integer.parseInt(errObj.get("row").getAsString());
                        }
                    } catch (Exception e) {
                        logger.debug("Could not parse row number for validation error: {}", e.getMessage());
                    }
                }
                String field = "unknown";
                if (errObj.has("field") && !errObj.get("field").isJsonNull()) {
                    try { field = errObj.get("field").getAsString(); } catch (Exception e) { /* use default */ }
                }
                String message = "Validation error";
                if (errObj.has("message") && !errObj.get("message").isJsonNull()) {
                    try { message = errObj.get("message").getAsString(); } catch (Exception e) { /* use default */ }
                }
                String errorCode = "VALIDATION_ERROR";
                if (errObj.has("error_code") && !errObj.get("error_code").isJsonNull()) {
                    try { errorCode = errObj.get("error_code").getAsString(); } catch (Exception e) { /* use default */ }
                }
                try {
                    int reportItemId = jobDAO.createJobReportItem(jobId, field, "error", rowNum);
                    jobDAO.createJobReportItemMessage(reportItemId, errorCode, message, "ERROR");
                } catch (SQLException e) {
                    logger.error("Failed to create report item for validation error", e);
                }
            }

            // Count distinct rows that failed (for user-facing "failed" count), not total errors
            int failedRowsCount;
            if (validationResponse.has("invalid_rows")) {
                failedRowsCount = validationResponse.get("invalid_rows").getAsInt();
            } else {
                Set<Integer> failedRowNumbers = new HashSet<>();
                for (int i = 0; i < errors.size(); i++) {
                    try {
                        if (!errors.get(i).isJsonObject()) continue;
                        JsonObject err = errors.get(i).getAsJsonObject();
                        if (!err.has("row")) continue;
                        if (err.get("row").isJsonPrimitive() && err.get("row").getAsJsonPrimitive().isNumber()) {
                            failedRowNumbers.add(err.get("row").getAsInt());
                        } else if (err.get("row").isJsonPrimitive() && err.get("row").getAsJsonPrimitive().isString()) {
                            failedRowNumbers.add(Integer.parseInt(err.get("row").getAsString()));
                        }
                    } catch (Exception e) {
                        logger.debug("Could not parse row number for failed-row count: {}", e.getMessage());
                    }
                }
                failedRowsCount = failedRowNumbers.size();
            }

            // Check for validation failure
            // "error" status = file-level error, always fail
            // "invalid" status = row-level errors, check if we should continue
            if ("error".equals(validationStatus)) {
                // File-level error - always fail
                String errorMessage = validationResponse.has("message") 
                    ? validationResponse.get("message").getAsString() 
                    : "Validation failed";
                
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", errorMessage);
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, errorMessage, 0, 0, 0, failedRowsCount);
                
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("status", "error");
                errorResponse.addProperty("message", errorMessage);
                errorResponse.addProperty("job_id", jobId);
                errorResponse.addProperty("reference_name", referenceName);
                errorResponse.addProperty("inserted", 0);
                errorResponse.addProperty("updated", 0);
                errorResponse.addProperty("deleted", 0);
                errorResponse.addProperty("failed", failedRowsCount);
                if (errors.size() > 0) {
                    errorResponse.add("errors", errors);
                }
                generateAndSaveRoleReport(jobId, new ArrayList<>(), entityName, userId, totalRowsFromFile);
                response.setStatus(400);
                response.getWriter().write(gson.toJson(errorResponse));
                return;
            } else if ("invalid".equals(validationStatus)) {
                // Row-level errors - check if we should continue
                String errorMessage = validationResponse.has("message") 
                    ? validationResponse.get("message").getAsString() 
                    : "Validation completed with errors";
                
                // If cancelOnWarning is true, stop processing
                if (cancelOnWarning) {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", errorMessage + " - Cancel on Warning enabled");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, 
                        errorMessage + " - Cancel on Warning enabled", 0, 0, 0, failedRowsCount);
                    
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("status", "error");
                    errorResponse.addProperty("message", errorMessage);
                    errorResponse.addProperty("job_id", jobId);
                    errorResponse.addProperty("reference_name", referenceName);
                    errorResponse.addProperty("inserted", 0);
                    errorResponse.addProperty("updated", 0);
                    errorResponse.addProperty("deleted", 0);
                    errorResponse.addProperty("failed", failedRowsCount);
                    if (errors.size() > 0) {
                        errorResponse.add("errors", errors);
                    }
                    generateAndSaveRoleReport(jobId, new ArrayList<>(), entityName, userId, totalRowsFromFile);
                    response.setStatus(400);
                    response.getWriter().write(gson.toJson(errorResponse));
                    return;
                } else if (normalizedRows.size() == 0) {
                    // No valid rows to process - fail even if cancelOnWarning is false
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", errorMessage + " - no valid rows to process");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, 
                        errorMessage + " - no valid rows", 0, 0, 0, failedRowsCount);
                    
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("status", "error");
                    errorResponse.addProperty("message", errorMessage);
                    errorResponse.addProperty("job_id", jobId);
                    errorResponse.addProperty("reference_name", referenceName);
                    errorResponse.addProperty("inserted", 0);
                    errorResponse.addProperty("updated", 0);
                    errorResponse.addProperty("deleted", 0);
                    errorResponse.addProperty("failed", failedRowsCount);
                    if (errors.size() > 0) {
                        errorResponse.add("errors", errors);
                    }
                    generateAndSaveRoleReport(jobId, new ArrayList<>(), entityName, userId, totalRowsFromFile);
                    response.setStatus(400);
                    response.getWriter().write(gson.toJson(errorResponse));
                    return;
                }
                // If cancelOnWarning is false and there are valid rows, continue processing
                logger.info("Cancel on Warning is false, continuing with {} valid rows despite {} errors (total_rows in file: {})",
                    normalizedRows.size(), errorCount, totalRowsFromFile);
                logger.info("Will process {} valid rows, skipping {} rows with errors", 
                    normalizedRows.size(), errorCount);
            }

            // Step 8: Process validated data
            int totalRows = normalizedRows.size();
            logger.info("Proceeding to process {} valid rows (total rows in normalizedRows array)", totalRows);
            
            if (totalRows == 0) {
                logger.warn("No valid rows to process after validation. This should have been caught earlier.");
            }
            
            jobDAO.updateJobItemsCount(jobId, totalRows);
            jobDAO.updateJobProgress(jobId, "Processing", "Starting role assignment processing");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 10, "Processing " + totalRows + " rows");

            // Get handler for this role type
            RoleUploadHandler handler = ROLE_HANDLERS.get(entityName);
            if (handler == null) {
                String errorMsg = "No handler registered for role type: " + entityName;
                logger.error(errorMsg);
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", errorMsg);
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, errorMsg, 0, 0, 0, 0);
                sendErrorResponse(response, errorMsg, 400);
                return;
            }

            // Determine operation type
            String operation = determineOperation(uploadOption);
            logger.info("Operation type determined: {}", operation);

            // Process rows in separate thread to allow WebSocket connection to establish
            final int finalJobId = jobId;
            final int finalUserId = userId;
            final String finalErrorHandling = errorHandling;
            final String finalOperation = operation;
            final JsonArray finalNormalizedRows = normalizedRows; // Keep reference for logging
            final int finalFailedRowsCount = failedRowsCount; // Validation-failed rows (included in reported "failed" count)
            final int finalTotalRowsFromFile = totalRowsFromFile;
            
            logger.info("Starting processing thread for job {} with {} rows, operation: {}, errorHandling: {}, totalRowsFromFile: {}", 
                finalJobId, finalNormalizedRows.size(), finalOperation, finalErrorHandling, finalTotalRowsFromFile);
            
            Thread processingThread = new Thread(() -> {
                logger.info("Processing thread started for job {}, processing {} rows", 
                    finalJobId, finalNormalizedRows.size());
                processRows(finalJobId, finalNormalizedRows, handler, finalOperation, finalUserId, finalErrorHandling, finalFailedRowsCount, finalTotalRowsFromFile);
            });
            processingThread.setDaemon(false);
            processingThread.start();
            logger.info("Processing thread started successfully for job {}", finalJobId);

            // Return response immediately
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            String responseMessage;
            if (failedRowsCount > 0) {
                successResponse.addProperty("validation_failed_rows", failedRowsCount);
                successResponse.addProperty("validation_errors_count", errorCount);
                responseMessage = String.format("%d row(s) had validation errors (see job report). Processing %d valid rows...",
                        failedRowsCount, totalRows);
            } else {
                responseMessage = "Processing " + totalRows + " rows...";
            }
            successResponse.addProperty("message", responseMessage);
            successResponse.addProperty("total_rows", totalRows);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(successResponse));

        } catch (Exception e) {
            logger.error("Error processing role bulk upload", e);
            
            if (jobId != -1) {
                try {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Internal error: " + e.getMessage());
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, "Internal error", 0, 0, 0, 0);
                } catch (SQLException sqlEx) {
                    logger.error("Error updating job status after failure", sqlEx);
                }
            }
            
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    private void processRows(int jobId, JsonArray normalizedRows, RoleUploadHandler handler, 
                            String operation, int userId, String errorHandling, int validationFailedRowsCount, int totalRowsFromFile) {
        int processedCount = 0; // Successful rows only
        int totalProcessedCount = 0; // All rows processed (successful + failed)
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0; // Processing failures only
        boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
        
        // Collect report rows for Excel generation
        List<BulkUploadReportGenerator.ReportRow> reportRows = new ArrayList<>();

        try {
            // Small delay to allow WebSocket connection to establish
            try {
                Thread.sleep(500); // 500ms delay
            } catch (InterruptedException e) {
                logger.warn("Processing thread interrupted during initial delay", e);
                Thread.currentThread().interrupt();
            }
            
            logger.info("Starting role processing for job {}: {} rows, cancelOnWarning: {}", 
                jobId, normalizedRows.size(), cancelOnWarning);
            for (int i = 0; i < normalizedRows.size(); i++) {
                JsonObject rowData = normalizedRows.get(i).getAsJsonObject();
                int rowNumber = rowData.has("rowNumber") ? rowData.get("rowNumber").getAsInt() : (i + 1);
                
                logger.info("Processing row {}/{} (Excel row {}), job {}", 
                    i + 1, normalizedRows.size(), rowNumber, jobId);
                
                String entityName = extractEntityName(rowData);
                
                // Use a separate connection for each row to ensure isolation
                // Each row is processed in its own transaction:
                // - When cancelOnWarning is false: successful rows commit independently, failed rows rollback, processing continues
                // - When cancelOnWarning is true: successful rows commit independently, failed rows rollback, processing stops
                // This ensures that when cancelOnWarning is false, successful rows are committed even if other rows fail
                try (Connection conn = DatabaseConnection.getConnection()) {
                    conn.setAutoCommit(false);
                    
                    try {
                        logger.debug("Validating row {} before processing", rowNumber);
                        // Validate row before processing
                        JsonObject validationResult = handler.validateRow(rowData, operation, userId);
                        boolean isValid = validationResult.has("valid") && validationResult.get("valid").getAsBoolean();
                        
                        if (!isValid) {
                            String errorMsg = validationResult.has("error") 
                                ? validationResult.get("error").getAsString() 
                                : "Validation failed";
                            logger.warn("Row {} validation failed: {}", rowNumber, errorMsg);
                            throw new IllegalArgumentException(errorMsg);
                        }
                        
                        logger.debug("Row {} validation passed, performing {} operation", rowNumber, operation);
                        // Perform operation
                        if ("INSERT".equalsIgnoreCase(operation)) {
                            handler.insert(conn, rowData, userId);
                            logger.debug("Insert operation completed for row {}", rowNumber);
                        } else if ("DELETE".equalsIgnoreCase(operation)) {
                            handler.delete(conn, rowData, userId);
                            logger.debug("Delete operation completed for row {}", rowNumber);
                        }
                        
                        // Commit the transaction for this successful row
                        // This commit happens independently for each row, so successful rows are persisted
                        // even when other rows fail (when cancelOnWarning is false)
                        conn.commit();
                        logger.info("Row {} processed successfully ({}), transaction committed. " +
                            "Total successful so far: {}, total failed so far: {}", 
                            rowNumber, operation, processedCount + 1, failedCount);
                        
                        // Create success report item
                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                        String successMsg = operation.equals("INSERT") 
                            ? "Role assigned successfully" 
                            : "Role removed successfully";
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", successMsg, "INFO");
                        
                        // Add to report rows
                        String objectId = extractObjectId(rowData);
                        String objectName = extractObjectName(rowData);
                        reportRows.add(new BulkUploadReportGenerator.ReportRow(
                            rowNumber,
                            objectId,
                            objectName,
                            operation,
                            "Success",
                            null,
                            null,
                            successMsg
                        ));
                        
                        // Increment appropriate counter based on operation
                        if ("INSERT".equalsIgnoreCase(operation)) {
                            insertedCount++;
                        } else if ("DELETE".equalsIgnoreCase(operation)) {
                            deletedCount++;
                        }
                        
                        // Increment processed count only for successful rows
                        processedCount++;
                        totalProcessedCount++;
                        
                    } catch (Exception e) {
                        // Rollback this row's transaction - other rows are unaffected
                        try {
                            conn.rollback();
                            logger.debug("Rolled back transaction for failed row {}", rowNumber);
                        } catch (SQLException rollbackEx) {
                            logger.error("Error rolling back transaction for row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                        }
                        
                        logger.error("Error processing row {}: {}, rolling back transaction. " +
                            "This row will be skipped, but other rows will continue processing.", 
                            rowNumber, e.getMessage(), e);
                        
                        // Create detailed error report item
                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                        
                        // Determine error field and code from exception message
                        String errorField = "role_assignment";
                        String errorCode = "PROCESSING_ERROR";
                        String errorMessage = e.getMessage();
                        
                        if (errorMessage != null) {
                            if (errorMessage.contains("duplicate") || errorMessage.contains("already exists")) {
                                errorCode = "DUPLICATE_ASSIGNMENT";
                            } else if (errorMessage.contains("does not exist") || errorMessage.contains("not found")) {
                                errorCode = "NOT_FOUND";
                            } else if (errorMessage.contains("module")) {
                                errorCode = "MODULE_MISMATCH";
                                errorField = "governance_role";
                            } else if (errorMessage.contains("Legal Entity")) {
                                errorField = "legal_entity";
                            } else if (errorMessage.contains("Person") || errorMessage.contains("User")) {
                                errorField = "person";
                            }
                        }
                        
                        jobDAO.createJobReportItemMessage(reportItemId, errorCode, errorMessage, "ERROR");
                        
                        // Add to report rows
                        String objectId = extractObjectId(rowData);
                        String objectName = extractObjectName(rowData);
                        String detail = "Skipped - " + (errorField != null ? errorField + ": " : "") + errorMessage + " (user: " + getUserName(userId) + ")";
                        reportRows.add(new BulkUploadReportGenerator.ReportRow(
                            rowNumber,
                            objectId,
                            objectName,
                            operation,
                            "Failed",
                            errorMessage,
                            null,
                            detail
                        ));
                        
                        failedCount++;
                        totalProcessedCount++;
                        
                        logger.info("Row {} failed ({}). Progress: {} successful, {} failed, {} remaining. cancelOnWarning: {}", 
                            rowNumber, errorMessage, processedCount, failedCount, 
                            normalizedRows.size() - totalProcessedCount, cancelOnWarning);
                        
                        if (cancelOnWarning) {
                            logger.warn("Cancelling all changes due to error in row {} (Cancel on Warning enabled). " +
                                "Stopping processing. {} rows processed successfully, {} rows failed before cancellation.", 
                                rowNumber, processedCount, failedCount);
                            // Generate report before breaking
                            generateAndSaveRoleReport(jobId, reportRows, entityName, userId, totalRowsFromFile);
                            // Note: Individual row already rolled back, will update job status to Failed at the end
                            break;
                        } else {
                            logger.info("Cancel on Warning is false, continuing to process remaining rows. " +
                                "Row {} failed but processing will continue. {} rows remaining to process.", 
                                rowNumber, normalizedRows.size() - totalProcessedCount);
                        }
                    }
                    
                } catch (SQLException sqlEx) {
                    // SQLException occurred outside the inner try block (connection setup issue)
                    // No need to rollback as connection wasn't established or was auto-rolled back
                    logger.error("Database error processing row {}: {}. " +
                        "This row will be skipped, but other rows will continue processing.", 
                        rowNumber, sqlEx.getMessage(), sqlEx);
                    failedCount++;
                    totalProcessedCount++;
                    
                    try {
                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "DB_ERROR", 
                            "Database error: " + sqlEx.getMessage(), "ERROR");
                    } catch (SQLException reportEx) {
                        logger.error("Failed to create error report item for row {}", rowNumber, reportEx);
                    }
                    
                    // Add to report rows
                    String objectId = extractObjectId(rowData);
                    String objectName = extractObjectName(rowData);
                    reportRows.add(new BulkUploadReportGenerator.ReportRow(
                        rowNumber,
                        objectId,
                        objectName,
                        operation,
                        "Failed",
                        "Database error: " + sqlEx.getMessage(),
                        null,
                        "Skipped - Database error"
                    ));
                    
                    logger.info("Row {} database error. Progress: {} successful, {} failed, {} remaining. cancelOnWarning: {}", 
                        rowNumber, processedCount, failedCount, 
                        normalizedRows.size() - totalProcessedCount, cancelOnWarning);
                    
                    if (cancelOnWarning) {
                        logger.warn("Cancelling all changes due to database error in row {} (Cancel on Warning enabled). " +
                            "Stopping processing. {} rows processed successfully, {} rows failed before cancellation.", 
                            rowNumber, processedCount, failedCount);
                        // Generate report before breaking
                        generateAndSaveRoleReport(jobId, reportRows, entityName, userId, totalRowsFromFile);
                        break;
                    } else {
                        logger.info("Cancel on Warning is false, continuing to process remaining rows. " +
                            "Row {} database error but processing will continue. {} rows remaining to process.", 
                            rowNumber, normalizedRows.size() - totalProcessedCount);
                    }
                }
                
                // Update progress periodically based on total rows processed
                // This ensures progress updates even when some rows fail
                if (totalProcessedCount % 10 == 0 || totalProcessedCount == normalizedRows.size()) {
                    int progressPercentage = (totalProcessedCount * 90) / normalizedRows.size() + 10;
                    int failedTotal = validationFailedRowsCount + failedCount;
                    jobDAO.updateJobProgress(jobId, "Processing", 
                        String.format("Processed %d of %d rows (%d successful, %d failed)", 
                            totalProcessedCount, normalizedRows.size(), processedCount, failedTotal));
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercentage, 
                        "Processing rows", insertedCount, updatedCount, deletedCount, failedTotal);
                }
            }
            
            // Final status update (failedTotal = validation failures + processing failures for user-facing count)
            int successCount = insertedCount + updatedCount + deletedCount;
            int failedTotal = validationFailedRowsCount + failedCount;
            logger.info("Processing loop completed for job {}. Processed: {} total, {} successful, {} failed ({} validation, {} processing)", 
                jobId, totalProcessedCount, successCount, failedTotal, validationFailedRowsCount, failedCount);
            
            String finalStatus;
            if (failedCount == 0) {
                finalStatus = "Completed";
                logger.info("All rows processed successfully, marking job {} as Completed", jobId);
            } else if (cancelOnWarning && failedCount > 0) {
                // If Cancel on Warning is enabled and there are errors, mark as Failed
                finalStatus = "Failed";
                logger.info("Cancel on Warning was enabled and errors occurred, marking job {} as Failed", jobId);
            } else if (successCount > 0) {
                finalStatus = "Partially Completed";
                logger.info("Some rows succeeded and some failed, marking job {} as Partially Completed", jobId);
            } else {
                finalStatus = "Failed";
                logger.warn("All rows failed, marking job {} as Failed", jobId);
            }
            
            // Generate and save report
            String entityName = handler.getRoleType();
            generateAndSaveRoleReport(jobId, reportRows, entityName, userId, totalRowsFromFile);
            
            jobDAO.updateJobStatus(jobId, finalStatus, true);
            jobDAO.updateJobItemsCount(jobId, successCount); // Only successful operations
            jobDAO.updateJobProgress(jobId, finalStatus, 
                String.format("Completed: %d successful, %d errors", successCount, failedTotal));
            BulkUploadBroadcaster.getInstance().broadcast(jobId, finalStatus, 100, 
                "Processing completed", insertedCount, updatedCount, deletedCount, failedTotal);
            
            logger.info("Role bulk upload completed - Job: {}, Status: {}, Inserted: {}, Updated: {}, Deleted: {}, Failed: {}", 
                jobId, finalStatus, insertedCount, updatedCount, deletedCount, failedTotal);
            
        } catch (Exception e) {
            logger.error("Fatal error during row processing", e);
            try {
                int failedTotal = validationFailedRowsCount + failedCount;
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Fatal error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, 
                    "Fatal error", insertedCount, updatedCount, deletedCount, failedTotal);
            } catch (SQLException sqlEx) {
                logger.error("Error updating job status after fatal error", sqlEx);
            }
        }
    }

    private String extractEntityName(JsonObject rowData) {
        // Try to extract a meaningful entity name from the row
        if (rowData.has("Legal Entity Name")) {
            return rowData.get("Legal Entity Name").getAsString();
        }
        if (rowData.has("Policy Name")) {
            return rowData.get("Policy Name").getAsString();
        }
        if (rowData.has("Short Name")) {
            return rowData.get("Short Name").getAsString();
        }
        if (rowData.has("User Email")) {
            return rowData.get("User Email").getAsString();
        }
        if (rowData.has("rowNumber")) {
            return "Row " + rowData.get("rowNumber").getAsInt();
        }
        return "Unknown";
    }

    private String determineOperation(String uploadOption) {
        if (uploadOption == null) {
            return "INSERT";
        }
        String lower = uploadOption.toLowerCase();
        if (lower.contains("delete") || lower.contains("remove")) {
            return "DELETE";
        }
        return "INSERT";
    }

    private String determineJobType(String entityName, String uploadOption) {
        // Return "bulk_upload" for consistency with other bulk upload servlets
        return "bulk_upload";
    }

    /**
     * Generate short reference name with sequential number
     * Format: ROLE-{entityType}-{nextNumber}
     * Examples: ROLE-LEGAL-1, ROLE-POLICY-2
     */
    private String generateShortReferenceName(String entityName) {
        // Extract entity type prefix from entity name
        String entityPrefix = getEntityPrefix(entityName);
        String prefixPattern = "ROLE-" + entityPrefix + "-%";
        
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE ?")) {
            ps.setString(1, prefixPattern);
            try (ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    String expectedPrefix = "ROLE-" + entityPrefix + "-";
                    if (refName != null && refName.startsWith(expectedPrefix)) {
                        try {
                            String numberPart = refName.substring(expectedPrefix.length());
                            int number = Integer.parseInt(numberPart);
                            if (number > maxNumber) {
                                maxNumber = number;
                            }
                        } catch (NumberFormatException ignore) {
                            // Skip invalid format
                        }
                    }
                }
                if (maxNumber > 0) {
                    nextNumber = maxNumber + 1;
                }
            }
        } catch (SQLException e) {
            logger.warn("Error generating short reference name: {}", e.getMessage());
        }
        return "ROLE-" + entityPrefix + "-" + nextNumber;
    }
    
    /**
     * Get entity prefix for reference name
     * Maps entity names to short prefixes
     */
    private String getEntityPrefix(String entityName) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return "UNKNOWN";
        }
        
        String entity = entityName.trim();
        
        // Map entity names to prefixes (alphabetical order)
        if (entity.equalsIgnoreCase("Business Area Role")) return "BUSAREA";
        if (entity.equalsIgnoreCase("Capability Role")) return "CAPAB";
        if (entity.equalsIgnoreCase("Client Role")) return "CLIENT";
        if (entity.equalsIgnoreCase("Committee Role")) return "COMIT";
        if (entity.equalsIgnoreCase("Data Set Role")) return "DATASET";
        if (entity.equalsIgnoreCase("Glossary Role")) return "GLOSS";
        if (entity.equalsIgnoreCase("Interface Role")) return "INTERF";
        if (entity.equalsIgnoreCase("Legal Entity Role")) return "LEGAL";
        if (entity.equalsIgnoreCase("Policy Role")) return "POLICY";
        if (entity.equalsIgnoreCase("Process Role")) return "PROCES";
        if (entity.equalsIgnoreCase("Product Role")) return "PRODUC";
        if (entity.equalsIgnoreCase("Project Role")) return "PROJEC";
        if (entity.equalsIgnoreCase("Regulation Role")) return "REG";
        if (entity.equalsIgnoreCase("System Role")) return "SYSTEM";
        
        // Default: use first word or first 6 characters
        String[] words = entity.split("\\s+");
        if (words.length > 0) {
            String firstWord = words[0].toUpperCase();
            return firstWord.length() > 6 ? firstWord.substring(0, 6) : firstWord;
        }
        
        return entity.toUpperCase().substring(0, Math.min(6, entity.length()));
    }

    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition != null) {
            for (String token : contentDisposition.split(";")) {
                if (token.trim().startsWith("filename")) {
                    return token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
                }
            }
        }
        return "unknown.xlsx";
    }

    private String getUserName(int userId) {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fullName");
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching user name for userId {}: {}", userId, e.getMessage());
        }
        return "User " + userId;
    }

    /**
     * Generate and save role report Excel file.
     * Includes both processing results (reportRows) and validation errors (from Job_Report_Item)
     * so the downloaded report shows all failing rows.
     * When totalRowsFromFile > 0, ensures every Excel data row (2..totalRowsFromFile+1, row 1 = header) has a report line.
     */
    private void generateAndSaveRoleReport(int jobId, List<BulkUploadReportGenerator.ReportRow> reportRows,
                                           String entityName, int userId, int totalRowsFromFile) {
        try {
            Set<Integer> processedRowNumbers = reportRows.stream()
                    .map(BulkUploadReportGenerator.ReportRow::getRowNumber)
                    .collect(Collectors.toSet());

            // Load validation errors from Job_Report_Item even when reportRows is empty (e.g. all rows failed validation)
            List<BulkUploadReportGenerator.ReportRow> validationErrorRows = new ArrayList<>();
            try {
                List<JobReportItem> allItems = jobDAO.getJobReportItems(jobId);
                for (JobReportItem item : allItems) {
                    if (!"error".equals(item.getStatus())) continue;
                    Integer position = item.getPosition();
                    boolean isProcessedRow = position != null && processedRowNumbers.contains(position);
                    if (isProcessedRow) continue;
                    // Include validation-error rows; use sentinel row 0 when Position is null so they still appear in report
                    int rowNumberForReport = (position != null) ? position : 0;
                    String errorMsg = "Validation error";
                    if (item.getMessages() != null && !item.getMessages().isEmpty()) {
                        errorMsg = item.getMessages().stream()
                                .map(JobReportItemMessage::getMessage)
                                .filter(m -> m != null && !m.isEmpty())
                                .collect(Collectors.joining("; "));
                        if (errorMsg.isEmpty()) {
                            errorMsg = "Validation error";
                        }
                    }
                    validationErrorRows.add(new BulkUploadReportGenerator.ReportRow(
                            rowNumberForReport,
                            "",
                            item.getFieldName() != null ? item.getFieldName() : "",
                            "",
                            "Failed",
                            errorMsg,
                            null,
                            "Validation error - row skipped"));
                }
            } catch (SQLException e) {
                logger.warn("Could not load validation errors for report: {}", e.getMessage());
            }

            List<BulkUploadReportGenerator.ReportRow> allRows = new ArrayList<>(reportRows);
            allRows.addAll(validationErrorRows);

            // Ensure every file row has a report line: fill gaps for row numbers 2..lastDataRow (Excel: row 1 = header, data = 2..totalRowsFromFile+1)
            Set<Integer> reportedRowNumbers = allRows.stream()
                    .map(BulkUploadReportGenerator.ReportRow::getRowNumber)
                    .collect(Collectors.toSet());
            int lastDataRow = totalRowsFromFile > 0 ? totalRowsFromFile + 1 : allRows.stream()
                    .mapToInt(BulkUploadReportGenerator.ReportRow::getRowNumber)
                    .filter(rn -> rn > 0)
                    .max()
                    .orElse(1);
            for (int rowNum = 2; rowNum <= lastDataRow; rowNum++) {
                if (!reportedRowNumbers.contains(rowNum)) {
                    allRows.add(new BulkUploadReportGenerator.ReportRow(
                            rowNum,
                            "",
                            "Row " + rowNum,
                            "",
                            "Failed",
                            "Row not included in processing report.",
                            null,
                            "Skipped - Row not included in processing report."));
                    reportedRowNumbers.add(rowNum);
                }
            }
            allRows.sort(Comparator.comparingInt(BulkUploadReportGenerator.ReportRow::getRowNumber));

            if (!allRows.isEmpty()) {
                BulkUploadReportGenerator reportGenerator = new BulkUploadReportGenerator();
                byte[] reportBytes = reportGenerator.generateReport(allRows, entityName, jobId);

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fileName = "role_" + entityName.toLowerCase().replace(" ", "_") + "_report_" + jobId + "_" + timestamp + ".xlsx";
                String basePath = getBasePath();
                String storagePath = basePath + fileName;

                File directory = new File(basePath);
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                File reportFile = new File(storagePath);
                Files.write(reportFile.toPath(), reportBytes);

                jobDAO.createJobResourceFile(jobId, fileName, fileName, storagePath, true, 90);

                logger.info("Role report generated and saved for job {}: {}", jobId, storagePath);
            }
        } catch (Exception e) {
            logger.error("Error generating role report for job {}: {}", jobId, e.getMessage(), e);
        }
    }
    
    /**
     * Extract object ID from row data
     */
    private String extractObjectId(JsonObject rowData) {
        // Try common ID fields
        if (rowData.has("ID")) {
            return rowData.get("ID").getAsString();
        }
        if (rowData.has("Object ID")) {
            return rowData.get("Object ID").getAsString();
        }
        return "";
    }
    
    /**
     * Extract object name from row data
     */
    private String extractObjectName(JsonObject rowData) {
        // Try common name fields
        String[] nameFields = {
            "Legal Entity Name", "Policy Name", "System Name", "Process Name",
            "Product Name", "Project Name", "Short Name", "Primary Name"
        };
        
        for (String field : nameFields) {
            if (rowData.has(field)) {
                return rowData.get(field).getAsString();
            }
        }
        
        // Fallback to entity name
        return extractEntityName(rowData);
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

