package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.DatasetDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.Dataset;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.bulk.common.BulkUploadReferenceValidator;
import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.common.BulkUploadFacetPermissionHelper;
import com.example.budg_v2.bulk.common.BulkUploadNameValidator;
import com.example.budg_v2.bulk.roles.util.RoleHandlerUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@WebServlet("/api/bulk/dataset/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class DatasetBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DatasetBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("dataset"); }
    
    private final JobDAO jobDAO = new JobDAO();
    private final DatasetDAO datasetDAO = new DatasetDAO();

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

        logger.info("Dataset bulk upload request received");

        int jobId = -1;
        String storagePath = null;
        String userIdStr = null;
        String uploadOption = null;

        try {
            // Extract form data
            uploadOption = request.getParameter("uploadOption");
            String entity = request.getParameter("entity");
            userIdStr = request.getParameter("userId");
            String errorHandling = request.getParameter("errorHandling");
            String columnMappingsStr = request.getParameter("columnMappings");
            String segmentMode = request.getParameter("segmentMode");
            String segment = request.getParameter("segment");

            if (uploadOption == null || userIdStr == null) {
                sendErrorResponse(response, "Missing required parameters", 400);
                return;
            }

            int userId = Integer.parseInt(userIdStr);

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Dataset";
            if (!BulkUploadFacetPermissionHelper.canBulkUploadForModule(userId, entityName)) {
                sendErrorResponse(response, "You do not have permission to bulk upload for this entity.", 403);
                return;
            }

            // Get uploaded file
            Part filePart = request.getPart("file");
            if (filePart == null) {
                sendErrorResponse(response, "No file uploaded", 400);
                return;
            }

            String originalFileName = getFileName(filePart);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String uuid = UUID.randomUUID().toString().substring(0, 6);
            String fileName = "dataset_" + timestamp + "_" + uuid + ".xlsx";
            
            File directory = new File(getBasePath());
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            File file = new File(getBasePath() + fileName);
            filePart.write(file.getAbsolutePath());
            storagePath = getBasePath() + fileName;
            
            logger.info("File saved to: {}", storagePath);

            // Create Job record (entityName set above for permission check)
            String jobType = determineJobType(entityName, uploadOption);
            String referenceName = generateShortReferenceName();
            jobId = jobDAO.createJob(jobType, referenceName, 0, "Pending", userId);
            logger.info("Job created with ID: {} and reference: {}", jobId, referenceName);

            // Save metadata and create job resources
            saveMetadataJson(jobId, userId, entity, uploadOption, errorHandling, referenceName, 
                           originalFileName, storagePath, fileName);
            jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
            jobDAO.createJobProgress(jobId, 0, "Validating", "File uploaded, starting validation");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Pending", 5, "File uploaded");

            // Call Python validation service
            JsonObject validationRequest = new JsonObject();
            validationRequest.addProperty("file_path", new File(storagePath).getAbsolutePath());
            validationRequest.addProperty("upload_option", uploadOption);
            validationRequest.addProperty("entity", entityName);
            validationRequest.addProperty("user_id", userId);

            // Add segment parameters if provided (for INSERT operations only)
            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                validationRequest.addProperty("segment_mode", segmentMode);
                if (segment != null && !segment.trim().isEmpty()) {
                    validationRequest.addProperty("segment", segment);
                }
            }
            
            if (columnMappingsStr != null && !columnMappingsStr.trim().isEmpty()) {
                addColumnMappings(validationRequest, columnMappingsStr);
            }

            jobDAO.updateJobProgress(jobId, "Validating", "Validating file with Python service...");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 20, "Validating file with Python service...");

            JsonObject validationResponse;
            try {
                validationResponse = HttpClientUtil.postJsonGetJson(PYTHON_SERVICE_URL, validationRequest, HttpClientUtil.BULK_VALIDATION_SOCKET_TIMEOUT_MS);
            } catch (IOException e) {
                handleValidationServiceError(response, jobId, userId, uploadOption, e);
                return;
            }

            String validationStatus = validationResponse.get("status").getAsString();
            if ("error".equals(validationStatus) || "invalid".equals(validationStatus)) {
                handleValidationErrors(response, jobId, userId, uploadOption, referenceName, validationResponse);
                return;
            }

            // Store validation errors in report when present (so all rows appear in report even when we proceed with data)
            if (validationResponse.has("errors") && validationResponse.get("errors").isJsonArray()) {
                JsonArray errors = validationResponse.getAsJsonArray("errors");
                for (int i = 0; i < errors.size(); i++) {
                    JsonObject error = errors.get(i).getAsJsonObject();
                    int reportItemId = jobDAO.createJobReportItem(jobId, error.get("field").getAsString(),
                        "error", error.get("row").getAsInt());
                    jobDAO.createJobReportItemMessage(reportItemId, error.get("error_code").getAsString(),
                        error.get("message").getAsString(), "error");
                }
            }

            // Process validated data
            JsonArray validatedData = validationResponse.getAsJsonArray("data");
            int totalRows = validatedData.size();
            
            jobDAO.updateJobItemsCount(jobId, totalRows);
            jobDAO.updateJobStatus(jobId, "Processing", false);
            
            final int finalJobId = jobId;
            final int finalUserId = userId;
            final String finalErrorHandling = errorHandling;
            final String finalUploadOption = uploadOption;
            final String finalSegmentMode = segmentMode;
            final String finalSegment = segment;
            
            Thread processingThread = new Thread(() -> {
                processDatasetData(finalJobId, validatedData, finalUserId, finalErrorHandling, finalUploadOption, finalSegmentMode, finalSegment);
            });
            processingThread.setDaemon(false);
            processingThread.start();

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("message", "Processing " + totalRows + " rows...");
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(successResponse));

        } catch (Exception e) {
            logger.error("Unexpected error during upload", e);
            if (jobId != -1) {
                try {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());
                    
                    // Send failure notification if we have userId
                    if (userIdStr != null && uploadOption != null) {
                        try {
                            int userId = Integer.parseInt(userIdStr);
                            sendNotification(userId, "Dataset", uploadOption, 0, 0, 0, 0, 
                                          false, "Unexpected error: " + e.getMessage(), jobId);
                        } catch (Exception notifEx) {
                            logger.error("Error sending notification", notifEx);
                        }
                    }
                } catch (Exception e2) {
                    logger.error("Error updating job status", e2);
                }
            }
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }
    
    private void sendNotification(int userId, String entityName, String uploadOption, 
                                  int insertedCount, int updatedCount, int deletedCount, 
                                  int failedCount, boolean success, String errorMessage, int jobId) {
        try {
            WorkflowNotificationDAO dao = new WorkflowNotificationDAO();
            WorkflowNotification n = new WorkflowNotification();
            n.setRecipientUserId(userId);
            n.setCategory("bulk_upload");
            
            // Format title based on upload option
            String titleSuffix = uploadOption != null ? uploadOption : "Bulk Upload";
            n.setTitle(entityName + " : " + titleSuffix);
            
            if (success) {
                n.setMessage(String.format("Bulk upload completed: %d inserted, %d updated, %d deleted, %d failed", 
                    insertedCount, updatedCount, deletedCount, failedCount));
                n.setEventType("UPLOAD_COMPLETE");
            } else {
                String failureMsg = "Bulk upload failed";
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    failureMsg = errorMessage.length() > 200 ? 
                        errorMessage.substring(0, 197) + "..." : errorMessage;
                }
                n.setMessage(failureMsg);
                n.setEventType("UPLOAD_FAILED");
            }
            
            n.setChannel("ui");
            n.setFacetType("dataset");
            n.setObjectId(jobId);
            n.setRead(false);
            
            dao.create(n);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }

    private void processDatasetData(int jobId, JsonArray validatedData, int userId, String errorHandling, String uploadOption, String segmentMode, String segment) {
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();
        
        boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
        Connection conn = null;
        BulkUploadReferenceValidator refValidator = new BulkUploadReferenceValidator();
        BulkUploadNameValidator nameValidator = new BulkUploadNameValidator();
        Set<String> datasetBatchRefs = new HashSet<>();
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Transaction strategy: 
            // - If cancelOnWarning is true: use single transaction (all-or-nothing)
            // - If cancelOnWarning is false: commit each row individually (preserve successful rows)
            if (cancelOnWarning) {
                conn.setAutoCommit(false);
            } else {
                conn.setAutoCommit(true); // Auto-commit mode for per-row commits
            }

            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject rowData = validatedData.get(i).getAsJsonObject();
                String operation = rowData.get("operation").getAsString();
                int rowNumber = rowData.get("row_number").getAsInt();

                if (i % 10 == 0) {
                    int progressPercent = 30 + (int)((i / (double)totalRows) * 65);
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                        String.format("Processing row %d of %d", i + 1, totalRows),
                        insertedCount, updatedCount, deletedCount, failedCount);
                }

                // For per-row commits, start a transaction for this row
                if (!cancelOnWarning) {
                    conn.setAutoCommit(false);
                }
                
                try {
                    if ("INSERT".equals(operation)) {
                        int datasetId = insertDataset(conn, rowData, userId, segmentMode, segment, refValidator, nameValidator, datasetBatchRefs);
                        
                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                datasetId, 
                                "Data Sets", 
                                rowData, 
                                userId,
                                conn
                            );
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Dataset {}: {}", datasetId, cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }
                        
                        insertedCount++;
                        
                        // Insert into valuesjob_dataset table
                        jobDAO.insertValuesjobDataset(jobId, datasetId);
                        
                        // Insert into valuesjob_meta_attribute with Append mode
                        jobDAO.insertValuesjobMetaAttribute(jobId, "Suffix_Type", "Append");
                        jobDAO.insertValuesjobMetaAttribute(jobId, "IsOverwrite", "0");
                        
                        int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                            "Dataset created successfully", "info");

                    } else if ("UPDATE".equals(operation)) {
                        int datasetId = updateDataset(conn, rowData, userId);
                        
                        // Handle segment assignment for UPDATE operations
                        Long newSegmentId = null;
                        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                            newSegmentId = determineSegmentId(segmentMode, segment, rowData);
                        } else {
                            // Check if Segment column exists in Excel even when segmentMode is null
                            String segmentName = getString(rowData, "Segment");
                            if (segmentName != null && !segmentName.trim().isEmpty()) {
                                newSegmentId = getSegmentIdByName(segmentName.trim());
                            }
                        }
                        
                        if (newSegmentId != null) {
                            try {
                                // Get current segment
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) datasetId, "Dataset");
                                
                                // Only assign if segment is changing
                                if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                    // Validate segment change
                                    // Skip validation for super admin
                                    boolean skipValidation = false;
                                    try {
                                        skipValidation = SegmentAccessService.isSuperAdmin(userId);
                                    } catch (SQLException e) {
                                        logger.warn("Error checking super admin status: {}", e.getMessage());
                                    }
                                    
                                    if (!skipValidation) {
                                        SegmentValidationService validator = new SegmentValidationService();
                                        
                                        // Dataset segment move: cannot move to another private segment if system/glossary are in current segment
                                        long curSeg = (currentSegmentId != null) ? currentSegmentId : 1L;
                                        long newSeg = newSegmentId;
                                        if (curSeg != newSeg && curSeg != 1L && newSeg != 1L) {
                                            Dataset dsForMove = datasetDAO.getDatasetForUpdate(datasetId);
                                            if (dsForMove != null) {
                                                Integer sysId = dsForMove.getMasterSource();
                                                Integer gloId = dsForMove.getGlossary();
                                                if (sysId != null && sysId > 0) {
                                                    Long sysSeg = ObjectSegmentService.getObjectSegment(sysId.longValue(), "System");
                                                    if (sysSeg != null && sysSeg == curSeg) {
                                                        throw new RuntimeException("Cannot update the segment to another private segment; this dataset is related to objects (System) that belong to the first private segment.");
                                                    }
                                                }
                                                if (gloId != null && gloId > 0) {
                                                    Long gloSeg = ObjectSegmentService.getObjectSegment(gloId.longValue(), "Glossary");
                                                    if (gloSeg != null && gloSeg == curSeg) {
                                                        throw new RuntimeException("Cannot update the segment to another private segment; this dataset is related to objects (Glossary) that belong to the first private segment.");
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Validate Dataset/System segment rule
                                        Integer systemId = getInteger(rowData, "System_ID");
                                        if (systemId != null && systemId > 0) {
                                            SegmentValidationService.ValidationResult dsSysResult = 
                                                validator.validateDatasetSystemSegment(systemId, newSegmentId.intValue());
                                            if (!dsSysResult.isValid) {
                                                throw new RuntimeException("Dataset/System segment validation failed: " + dsSysResult.message);
                                            }
                                        }
                                        
                                        // Validate Dataset/Glossary segment rule
                                        Integer glossaryId = getInteger(rowData, "Glossary_ID");
                                        if (glossaryId != null && glossaryId > 0) {
                                            SegmentValidationService.ValidationResult dsGloResult = 
                                                validator.validateDatasetGlossarySegment(glossaryId, newSegmentId.intValue());
                                            if (!dsGloResult.isValid) {
                                                throw new RuntimeException("Dataset/Glossary segment validation failed: " + dsGloResult.message);
                                            }
                                        }
                                        
                                        // Validate segment move (includes stakeholder validation)
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            datasetId, newSegmentId.intValue(), "Dataset", systemId);
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Segment validation failed: " + result.message);
                                        }
                                    }
                                    
                                    validateSegmentAccess(userId, newSegmentId, rowNumber);
                                    
                                    // Assign new segment
                                    ObjectSegmentService.assignObjectToSegment(
                                        conn,
                                        (long) datasetId, 
                                        "Dataset", 
                                        newSegmentId, 
                                        userId
                                    );
                                    logger.info("Assigned Dataset {} to Segment {} (updated)", datasetId, newSegmentId);
                                }
                            } catch (Exception segEx) {
                                logger.error("Failed to assign Dataset {} to segment: {}", datasetId, segEx.getMessage(), segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                            }
                        }
                        
                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                datasetId, 
                                "Data Sets", 
                                rowData, 
                                userId,
                                conn
                            );
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Dataset {}: {}", datasetId, cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }
                        
                        updatedCount++;
                        
                        // Insert into valuesjob_dataset table for update too
                        jobDAO.insertValuesjobDataset(jobId, datasetId);
                        
                        int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                            "Dataset updated successfully", "info");

                    } else if ("DELETE".equals(operation)) {
                        int datasetId = rowData.get("ID").getAsInt();
                        BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                        BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "dataset", datasetId);
                        if (!validationResult.canDelete()) {
                            String errorMsg = String.join("; ", validationResult.getErrors());
                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "failed", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "ERROR", errorMsg, "error");
                            if (cancelOnWarning) {
                                conn.rollback();
                                jobDAO.updateJobStatus(jobId, "Failed", true);
                                jobDAO.updateJobProgress(jobId, "Failed", "Failed at row " + rowNumber);
                                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                        "Failed at row " + rowNumber, insertedCount, updatedCount, deletedCount, failedCount);
                                return;
                            } else {
                                try {
                                    conn.rollback();
                                } catch (SQLException rollbackEx) {
                                    logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                                }
                            }
                        } else if (validationResult.canDelete() && validationResult.hasWarnings() && cancelOnWarning) {
                            String warningMsg = String.join("; ", validationResult.getWarnings());
                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "failed", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "WARNING", warningMsg, "warning");
                            conn.rollback();
                            jobDAO.updateJobStatus(jobId, "Failed", true);
                            jobDAO.updateJobProgress(jobId, "Failed",
                                    "Cancelled at row " + rowNumber + " due to warning");
                            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                    "Cancelled at row " + rowNumber + " due to warning",
                                    insertedCount, updatedCount, deletedCount, failedCount);
                            return;
                        } else {
                            deleteDataset(conn, rowData);
                            deletedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    "Dataset deleted successfully", "info");
                        }
                    }
                    
                    // Commit this row if using per-row commits
                    if (!cancelOnWarning) {
                        conn.commit();
                        logger.debug("Committed row {} successfully", rowNumber);
                    }

                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    
                    // Provide user-friendly error messages
                    String errorMessage = e.getMessage();
                    if (errorMessage != null) {
                        // Convert technical NPE messages to user-friendly ones
                        if (errorMessage.contains("getGlossary()") && errorMessage.contains("null")) {
                            errorMessage = "Glossary is required but was not provided. Please specify a valid Glossary ID or Glossary Name.";
                        } else if (errorMessage.contains("getMasterSource()") && errorMessage.contains("null")) {
                            errorMessage = "System ID is required but was not provided. Please specify a valid System ID or System Short Name.";
                        } else if (errorMessage.contains("intValue()") && errorMessage.contains("null")) {
                            // Generic null pointer for Integer fields
                            if (errorMessage.contains("getGlossary")) {
                                errorMessage = "Glossary is required but was not provided. Please specify a valid Glossary ID or Glossary Name.";
                            } else if (errorMessage.contains("getMasterSource")) {
                                errorMessage = "System ID is required but was not provided. Please specify a valid System ID or System Short Name.";
                            } else {
                                errorMessage = "A required field is missing. Please check all required fields are provided.";
                            }
                        }
                    } else {
                        errorMessage = "An error occurred while processing this row. Please check the data and try again.";
                    }
                    
                    int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "failed", rowNumber);
                    jobDAO.createJobReportItemMessage(reportItemId, "ERROR",
                        "Failed: " + errorMessage, "error");

                    if (cancelOnWarning) {
                        // All-or-nothing mode: rollback entire transaction and stop
                        conn.rollback();
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Failed at row " + rowNumber + ": " + errorMessage);
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Failed at row " + rowNumber + ": " + errorMessage, insertedCount, updatedCount, deletedCount, failedCount);
                        return;
                    } else {
                        // Per-row commit mode: rollback only this row and continue
                        try {
                            conn.rollback();
                            logger.debug("Rolled back row {} due to error", rowNumber);
                        } catch (SQLException rollbackEx) {
                            logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                        }
                    }
                } finally {
                    // Reset auto-commit for next row if using per-row commits
                    if (!cancelOnWarning) {
                        try {
                            conn.setAutoCommit(true);
                        } catch (SQLException e) {
                            logger.error("Error resetting auto-commit: {}", e.getMessage(), e);
                        }
                    }
                }
            }

            // Final commit for all-or-nothing mode
            if (cancelOnWarning) {
                conn.commit();
                logger.info("Transaction committed successfully");
            }
            
            // Check for final completion status
            int successfulCount = insertedCount + updatedCount + deletedCount;
            boolean hasFailures = failedCount > 0;
            boolean isPartiallyCompleted = (successfulCount > 0) && hasFailures;
            boolean isAllFailed = (successfulCount == 0) && hasFailures;
            
            if (isAllFailed) {
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount);
                jobDAO.updateJobProgress(jobId, "Failed",
                    String.format("Complete: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount));
                
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Processing failed: all rows failed", insertedCount, updatedCount, deletedCount, failedCount);
                
                logger.info("Bulk upload job {} failed: {} inserted, {} updated, {} deleted, {} failed",
                    jobId, insertedCount, updatedCount, deletedCount, failedCount);
                
                sendNotification(userId, "Dataset", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, false, "All rows failed during processing", jobId);
            } else if (isPartiallyCompleted) {
                jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount); // Only successful operations
                jobDAO.updateJobProgress(jobId, "Partially Completed",
                    String.format("Complete: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount));
                
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                    "Processing complete with some failures", insertedCount, updatedCount, deletedCount, failedCount);
                
                logger.info("Bulk upload job {} partially completed: {} inserted, {} updated, {} deleted, {} failed",
                    jobId, insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send notification for partial completion
                sendNotification(userId, "Dataset", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, true, null, jobId);
            } else {
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount);
                jobDAO.updateJobProgress(jobId, "Completed",
                    String.format("Complete: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount));
                
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    "Processing complete", insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send success notification
                sendNotification(userId, "Dataset", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, true, null, jobId);
            }

        } catch (Exception e) {
            logger.error("Error processing dataset bulk upload job {}", jobId, e);
            try {
                if (conn != null) conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send failure notification
                sendNotification(userId, "Dataset", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, false, e.getMessage(), jobId);
            } catch (Exception e2) {
                logger.error("Error updating job status after failure", e2);
            }
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
    }

    private int insertDataset(Connection conn, JsonObject rowData, int userId, String segmentMode, String segment, BulkUploadReferenceValidator refValidator, BulkUploadNameValidator nameValidator, Set<String> batchRefs) throws SQLException {
        int rowNumber = rowData.has("row_number") ? rowData.get("row_number").getAsInt() : 0;
        Dataset dataset = new Dataset();
        
        // Get reference number; normalize so blank/whitespace is treated as empty
        String refNumber = getString(rowData, "Ref.");
        if (refNumber != null) {
            refNumber = refNumber.trim();
            if (refNumber.isEmpty()) refNumber = null;
        }
        
        // Validate reference uniqueness only when user actually provided a ref
        if (refNumber != null) {
            try {
                refValidator.validateReferenceUnique("Dataset", refNumber, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowData.get("row_number").getAsInt() + ": " + e.getMessage());
            }
            batchRefs.add(refNumber.toLowerCase());
        }
        
        // Auto-generate Ref if empty (batch-aware so multiple empty rows get distinct refs; generator excludes deleted rows)
        if (refNumber == null) {
            refNumber = ReferenceNumberGenerator.generateDatasetRefNumber(conn, batchRefs);
            batchRefs.add(refNumber.trim().toLowerCase());
        }
        
        dataset.setRefNumber(refNumber);
        
        // Get name and validate uniqueness within system
        String name = getString(rowData, "Name");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Row " + rowData.get("row_number").getAsInt() + ": Name is required for Dataset");
        }
        
        name = name.trim();
        
        // Get system ID (MasterSource) for validation (Python validation should always set System_ID for INSERT)
        Integer systemId = getInteger(rowData, "System_ID");
        if (systemId == null || systemId <= 0) {
            throw new IllegalArgumentException("Row " + rowData.get("row_number").getAsInt()
                + ": System ID or System Short Name is required to link the dataset to a system.");
        }

        // Validate name uniqueness within system (both in batch and database)
        if (BulkUploadNameValidator.hasNameColumn("Dataset")) {
            try {
                nameValidator.validateNameUniqueInSystem("Dataset", name, systemId, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowData.get("row_number").getAsInt() + ": " + e.getMessage());
            }
        }

        // Target segment before insert: enforce Dataset/System and Dataset/Glossary rules for all users (no super-admin bypass)
        Long segmentIdToAssign = null;
        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
            segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
        } else {
            String segmentName = getString(rowData, "Segment");
            if (segmentName != null && !segmentName.trim().isEmpty()) {
                segmentIdToAssign = getSegmentIdByName(segmentName.trim());
            }
        }
        long effectiveDatasetSegmentId = (segmentIdToAssign != null) ? segmentIdToAssign : 1L;
        Integer glossaryId = getInteger(rowData, "Glossary_ID");
        Integer viewingResolved = coalesce(getInteger(rowData, "BUDG Viewing_ID"), getFirstViewingId());
        boolean datasetIsPublic = (viewingResolved != null && viewingResolved == 1);
        validateDatasetInsertSystemGlossarySegmentRules(
                systemId, glossaryId, effectiveDatasetSegmentId, datasetIsPublic, userId, rowNumber);
        
        dataset.setPrimaryName(name);
        dataset.setDefinition(getString(rowData, "Definition"));
        dataset.setUsage(getString(rowData, "Usage"));
        
        // Set IDs with defaults
        dataset.setAccessControlType(viewingResolved);
        dataset.setDatasetType(coalesce(getInteger(rowData, "Type_ID"), getFirstLookupId("dataset_type")));
        dataset.setMasterSource(getInteger(rowData, "System_ID"));
        dataset.setLifecycle(coalesce(getInteger(rowData, "Lifecycle_ID"), getFirstLookupId("dataset_lifecycle")));
        dataset.setStatus(coalesce(getInteger(rowData, "BUDG Status_ID"), getFirstStatusId()));
        dataset.setGlossary(getInteger(rowData, "Glossary_ID"));
        
        int datasetId = datasetDAO.insert(dataset, userId);
        
        // Create audit records
        String userName = getUserName(userId);
        datasetDAO.createDatasetAuditRecords(datasetId, userName);
        datasetDAO.createDatasetAuditRecord(datasetId);
        
        // Link stakeholder (validate stakeholder has access to segment that will be assigned)
        linkDatasetStakeholder(datasetId, rowData, userId, segmentIdToAssign);
        
        // Assign object to segment (segment compatibility already validated above)
        if (segmentIdToAssign != null) {
            try {
                validateSegmentAccess(userId, segmentIdToAssign, rowNumber);
                
                ObjectSegmentService.assignObjectToSegment(
                    conn,
                    (long) datasetId, 
                    "Dataset", 
                    segmentIdToAssign, 
                    userId
                );
                logger.info("Assigned Dataset {} to Segment {}", datasetId, segmentIdToAssign);
            } catch (Exception segEx) {
                logger.error("Failed to assign Dataset {} to segment: {}", datasetId, segEx.getMessage(), segEx);
                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
            }
        }
        
        return datasetId;
    }

    /**
     * Enforces Dataset/System and Dataset/Glossary segment rules before insert (same rules as UI).
     * Uses Enterprise segment (id=1) when no explicit segment is chosen, so private system/glossary cannot slip through.
     */
    private void validateDatasetInsertSystemGlossarySegmentRules(
            Integer systemId,
            Integer glossaryId,
            long effectiveDatasetSegmentId,
            boolean datasetIsPublic,
            int userId,
            int rowNumber) throws SQLException {
        SegmentValidationService validator = new SegmentValidationService();
        int seg = (int) effectiveDatasetSegmentId;

        if (systemId != null && systemId > 0) {
            SegmentValidationService.ValidationResult dsSysResult =
                    validator.validateDatasetSystemSegment(systemId, seg, userId);
            if (!dsSysResult.isValid) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + dsSysResult.message);
            }
            if (datasetIsPublic) {
                SegmentValidationService.ValidationResult visResult =
                        validator.validateDatasetVisibility(0, systemId, true);
                if (!visResult.isValid) {
                    throw new IllegalArgumentException("Row " + rowNumber + ": " + visResult.message);
                }
            }
        }
        if (glossaryId != null && glossaryId > 0) {
            SegmentValidationService.ValidationResult dsGloResult =
                    validator.validateDatasetGlossarySegment(glossaryId, seg, datasetIsPublic, userId);
            if (!dsGloResult.isValid) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + dsGloResult.message);
            }
        }
    }

    private int updateDataset(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int rowNumber = rowData.has("row_number") ? rowData.get("row_number").getAsInt() : 0;
        Integer datasetId = getInteger(rowData, "ID");
        if (datasetId == null) {
            String ref = getString(rowData, "Ref.");
            if (ref != null && !ref.isBlank()) {
                datasetId = datasetDAO.getDatasetIdByRefNumber(conn, ref);
            }
        }
        if (datasetId == null) {
            String name = getString(rowData, "Name");
            if (name != null && !name.isBlank()) {
                Integer systemForLookup = getInteger(rowData, "System_ID");
                datasetId = datasetDAO.getDatasetIdByPrimaryName(conn, name, systemForLookup);
                if (datasetId == null) {
                    if (systemForLookup != null && systemForLookup > 0) {
                        throw new RuntimeException("Row " + rowNumber + ": No dataset named '" + name.trim()
                                + "' found for the specified system (ID " + systemForLookup + ")");
                    }
                    int n = datasetDAO.countDatasetsByPrimaryName(conn, name);
                    if (n == 0) {
                        throw new RuntimeException("Row " + rowNumber + ": No dataset found with name '" + name.trim() + "'");
                    }
                    throw new RuntimeException("Row " + rowNumber + ": Multiple datasets share the name '" + name.trim()
                            + "'; specify System ID or Ref. to identify the row");
                }
            }
        }
        if (datasetId == null) {
            throw new RuntimeException("Row " + rowNumber + ": At least one of ID, Ref., or Name is required for update");
        }
        int id = datasetId;
        
        // Validate object can be edited (CR and lock checks)
        try {
            BulkUploadCRValidationHelper validationHelper = new BulkUploadCRValidationHelper();
            validationHelper.validateObjectForEdit("dataset", id, userId, conn);
        } catch (BulkUploadCRValidationHelper.ValidationException e) {
            throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
        }
        
        Dataset existing = datasetDAO.getDatasetForUpdate(id);
        if (existing == null) {
            throw new RuntimeException("Row " + rowNumber + ": Dataset not found for ID " + id);
        }

        // System/glossary segment validation before update (use dataset's current segment)
        Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) id, "Dataset");
        int datasetSegmentId = (currentSegmentId != null) ? currentSegmentId.intValue() : -1;
        Integer effectiveSystemId = (rowData.has("System_ID") && !rowData.get("System_ID").isJsonNull()) ? getInteger(rowData, "System_ID") : existing.getMasterSource();
        Integer effectiveGlossaryId = (rowData.has("Glossary_ID") && !rowData.get("Glossary_ID").isJsonNull()) ? getInteger(rowData, "Glossary_ID") : existing.getGlossary();
        SegmentValidationService segmentValidator = new SegmentValidationService();
        if (effectiveSystemId != null && effectiveSystemId > 0) {
            SegmentValidationService.ValidationResult sysResult = segmentValidator.validateDatasetSystemSegment(effectiveSystemId, datasetSegmentId);
            if (!sysResult.isValid) {
                throw new RuntimeException("Row " + rowNumber + ": " + (sysResult.message != null ? sysResult.message : "Cannot update this dataset's system to one that belongs to another private segment"));
            }
        }
        if (effectiveGlossaryId != null && effectiveGlossaryId > 0) {
            boolean datasetIsPublic = (existing.getAccessControlType() != null && existing.getAccessControlType() == 1);
            SegmentValidationService.ValidationResult gloResult = segmentValidator.validateDatasetGlossarySegment(effectiveGlossaryId, datasetSegmentId, datasetIsPublic);
            if (!gloResult.isValid) {
                throw new RuntimeException("Row " + rowNumber + ": " + (gloResult.message != null ? gloResult.message : "Cannot assign this glossary as it belongs to another private segment"));
            }
        }

        String nameFromRow = getString(rowData, "Name");
        String definitionFromRow = getString(rowData, "Definition");
        
        Dataset dataset = new Dataset();
        dataset.setId(id);
        // Merge with existing: set only when key is present in row; else keep existing
        String refFromRow = getString(rowData, "Ref.");
        dataset.setRefNumber((rowData.has("Ref.") && refFromRow != null && !refFromRow.isBlank()) ? refFromRow : existing.getRefNumber());
        dataset.setPrimaryName((nameFromRow != null && !nameFromRow.isBlank()) ? nameFromRow : existing.getPrimaryName());
        dataset.setDefinition((definitionFromRow != null && !definitionFromRow.isBlank()) ? definitionFromRow : existing.getDefinition());
        String usageFromRow = getString(rowData, "Usage");
        dataset.setUsage((rowData.has("Usage") && usageFromRow != null && !usageFromRow.isBlank()) ? usageFromRow : existing.getUsage());
        dataset.setAccessControlType(rowData.has("BUDG Viewing_ID") && !rowData.get("BUDG Viewing_ID").isJsonNull() ? getInteger(rowData, "BUDG Viewing_ID") : existing.getAccessControlType());
        dataset.setDatasetType(rowData.has("Type_ID") && !rowData.get("Type_ID").isJsonNull() ? getInteger(rowData, "Type_ID") : existing.getDatasetType());
        dataset.setMasterSource(rowData.has("System_ID") && !rowData.get("System_ID").isJsonNull() ? getInteger(rowData, "System_ID") : existing.getMasterSource());
        dataset.setLifecycle(rowData.has("Lifecycle_ID") && !rowData.get("Lifecycle_ID").isJsonNull() ? getInteger(rowData, "Lifecycle_ID") : existing.getLifecycle());
        dataset.setStatus(rowData.has("BUDG Status_ID") && !rowData.get("BUDG Status_ID").isJsonNull() ? getInteger(rowData, "BUDG Status_ID") : existing.getStatus());
        dataset.setGlossary(rowData.has("Glossary_ID") && !rowData.get("Glossary_ID").isJsonNull() ? getInteger(rowData, "Glossary_ID") : existing.getGlossary());
        
        datasetDAO.update(dataset, userId);
        
        return id;
    }

    private void deleteDataset(Connection conn, JsonObject rowData) throws SQLException {
        int datasetId = rowData.get("ID").getAsInt();
        int rowNumber = rowData.get("row_number").getAsInt();
        
        // Validate object can be deleted (CR check)
        try {
            BulkUploadCRValidationHelper validationHelper = new BulkUploadCRValidationHelper();
            validationHelper.validateObjectForDeletion("dataset", datasetId, conn);
        } catch (BulkUploadCRValidationHelper.ValidationException e) {
            throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
        }
        
        String sql = "UPDATE dataset SET DeletedDatetime = NOW() WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.executeUpdate();
        }
    }

    private void linkDatasetStakeholder(int datasetId, JsonObject rowData, int userId, Long segmentIdToAssign) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Resolve stakeholder user: use User Email from file if provided, otherwise use uploader
            Integer stakeholderUserId = userId; // Default to uploader
            String userEmail = getString(rowData, "User Email");
            if (userEmail != null && !userEmail.trim().isEmpty()) {
                Integer resolvedUserId = getUserIdByEmail(userEmail.trim());
                if (resolvedUserId != null) {
                    stakeholderUserId = resolvedUserId;
                    logger.info("Resolved User Email '{}' to User ID {}", userEmail, stakeholderUserId);
                } else {
                    logger.warn("User Email '{}' not found, using uploader ID {} as stakeholder", userEmail, userId);
                }
            }
            
            // Validate stakeholder has access to dataset's segment
            // Skip validation for super admin
            boolean skipValidation = false;
            try {
                skipValidation = SegmentAccessService.isSuperAdmin(userId);
            } catch (SQLException e) {
                logger.warn("Error checking super admin status: {}", e.getMessage());
            }
            
            if (!skipValidation && stakeholderUserId != null && stakeholderUserId > 0) {
                try {
                    // Use segment that will be assigned (or current segment if not changing)
                    Long datasetSegmentId = segmentIdToAssign;
                    if (datasetSegmentId == null) {
                        // If no segment specified, get current segment (defaults to Enterprise)
                        datasetSegmentId = ObjectSegmentService.getObjectSegment((long) datasetId, "Dataset");
                        if (datasetSegmentId == null) {
                            datasetSegmentId = 1L; // Default to Enterprise
                        }
                    }
                    
                    if (datasetSegmentId != null && datasetSegmentId > 0) {
                        // Enterprise segment (ID=1) is accessible to all
                        if (datasetSegmentId != 1) {
                            boolean hasAccess = SegmentAccessService.hasSegmentAccess(stakeholderUserId, datasetSegmentId.intValue());
                            if (!hasAccess) {
                                SegmentValidationService validator = new SegmentValidationService();
                                String segmentName = validator.getSegmentName(datasetSegmentId.intValue());
                                String stakeholderName = getUserName(stakeholderUserId);
                                throw new RuntimeException(
                                    String.format("Cannot add stakeholder '%s' to dataset. The stakeholder does not have access to segment '%s'. " +
                                                  "All stakeholders must have access to the object's segment.",
                                        stakeholderName, segmentName != null ? segmentName : "Unknown"));
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error validating stakeholder segment access: {}", e.getMessage());
                    // Continue with stakeholder assignment - validation error will be caught if critical
                }
            }
            
            // Resolve governance role scoped to Data Sets module; validate person on admin role list when configured
            String governanceRoleName = getString(rowData, "Governance Role");
            Integer roleId = getInteger(rowData, "Governance Role_ID");
            if (roleId == null && governanceRoleName != null && !governanceRoleName.trim().isEmpty()) {
                try {
                    roleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, governanceRoleName.trim(), "Data Sets");
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            if (roleId == null) {
                roleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, "Dataset Owner", "Data Sets");
            }
            if (roleId != null) {
                int rowNum = rowData.has("row_number") && !rowData.get("row_number").isJsonNull()
                        ? rowData.get("row_number").getAsInt() : 0;
                RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, stakeholderUserId.intValue(), roleId.intValue(), "Data Sets", rowNum);
                logger.info("Using Governance Role ID {} for dataset {}", roleId, datasetId);
                // Check if object_x_people record already exists for this user and role
                Integer objectXPeopleId = findObjectXPeopleId(conn, stakeholderUserId, roleId);
                
                if (objectXPeopleId == null) {
                    // Create new object_x_people record
                    Map<String, Object> stakeholderData = new HashMap<>();
                    stakeholderData.put("userId", stakeholderUserId);
                    stakeholderData.put("roleId", roleId);
                    
                    objectXPeopleId = datasetDAO.createObjectXPeople(conn, stakeholderData, userId);
                    logger.debug("Created new object_x_people with ID: {} for user {} and role {}", objectXPeopleId, stakeholderUserId, roleId);
                } else {
                    logger.debug("Using existing object_x_people with ID: {} for user {} and role {}", objectXPeopleId, stakeholderUserId, roleId);
                }

                // Use DAO to link (with duplicate prevention)
                datasetDAO.linkStakeholderToDataset(conn, datasetId, objectXPeopleId, userId);

                // Create audit records
                String userName = getUserName(stakeholderUserId);
                datasetDAO.createStakeholderAuditRecords(datasetId, userName, userName, roleId);
            } else {
                logger.warn("No governance role found for dataset {}, skipping stakeholder assignment", datasetId);
            }
        } catch (Exception e) {
            logger.error("Error linking stakeholder to dataset {}: {}", datasetId, e.getMessage(), e);
        }
    }
    
    /**
     * Find object_x_people ID for a given user and role
     * @param conn Database connection
     * @param personId The person ID
     * @param roleId The role ID
     * @return The object_x_people ID, or null if not found
     */
    private Integer findObjectXPeopleId(Connection conn, int personId, int roleId) throws SQLException {
        String sql = "SELECT ID FROM object_x_people WHERE ipid = ? AND RoleID = ? LIMIT 1";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            ps.setInt(2, roleId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        
        return null;
    }

    // Stakeholder methods now handled by DatasetDAO

    // Helper methods
    private String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private Integer getInteger(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return null;
    }

    private Integer coalesce(Integer... values) {
        for (Integer value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private Integer getFirstLookupId(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT ID FROM " + tableName + " ORDER BY ID ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first lookup ID from {}: {}", tableName, e.getMessage());
        }
        return null;
    }

    private Integer getFirstViewingId() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM viewing ORDER BY id ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first viewing ID: {}", e.getMessage());
        }
        return null;
    }

    private Integer getFirstStatusId() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT ID FROM status ORDER BY ID ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first status ID: {}", e.getMessage());
        }
        return null;
    }

    private Integer getRoleIdByName(String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM object_role WHERE LOWER(primaryname) = LOWER(?) LIMIT 1")) {
            ps.setString(1, roleName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting role ID by name: {}", roleName, e);
        }
        return null;
    }

    /**
     * Resolve User Email to User ID
     * @param email User email address
     * @return User ID if found, null otherwise
     */
    private Integer getUserIdByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM people WHERE LOWER(Email) = LOWER(?) AND Deleted_date IS NULL LIMIT 1")) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting user ID by email: {}", email, e);
        }
        return null;
    }

    private String getUserName(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT CONCAT(First_Name, ' ', Last_Name) as full_name FROM people WHERE ID = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("full_name");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting user name for ID {}: {}", userId, e.getMessage());
        }
        return "Unknown User";
    }

    private String generateShortReferenceName() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'DS-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("DS-")) {
                        try {
                            int number = Integer.parseInt(refName.substring(3));
                            if (number > maxNumber) maxNumber = number;
                        } catch (NumberFormatException e) {}
                    }
                }
                if (maxNumber > 0) nextNumber = maxNumber + 1;
            }
        } catch (SQLException e) {
            logger.warn("Error generating reference name: {}", e.getMessage());
        }
        return "DS-" + nextNumber;
    }

    private String determineJobType(String entity, String uploadOption) {
        String operation = switch (uploadOption) {
            case "Add New Items" -> "Bulk Insert";
            case "Update Existing Items" -> "Bulk Update";
            case "Remove Existing Items" -> "Bulk Delete";
            default -> "Bulk Upload";
        };
        return entity + " " + operation;
    }

    private String getFileName(Part filePart) {
        String contentDisposition = filePart.getHeader("content-disposition");
        for (String token : contentDisposition.split(";")) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return "unknown.xlsx";
    }

    private void saveMetadataJson(int jobId, int userId, String entity, String uploadOption,
                                  String errorHandling, String referenceName, String originalFileName,
                                  String storagePath, String fileName) {
        try {
            String metadataFileName = fileName.replace(".xlsx", ".json");
            File metadataFile = new File(getBasePath() + metadataFileName);
            
            JsonObject metadata = new JsonObject();
            metadata.addProperty("job_id", jobId);
            metadata.addProperty("user_id", userId);
            metadata.addProperty("entity", entity != null ? entity : "Dataset");
            metadata.addProperty("upload_option", uploadOption);
            metadata.addProperty("error_handling", errorHandling);
            metadata.addProperty("uploaded_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
            metadata.addProperty("reference_name", referenceName);
            metadata.addProperty("original_file_name", originalFileName);
            metadata.addProperty("storage_path", storagePath);
            
            Files.writeString(metadataFile.toPath(), gson.toJson(metadata));
        } catch (Exception e) {
            logger.warn("Failed to save metadata JSON: {}", e.getMessage());
        }
    }

    private void addColumnMappings(JsonObject validationRequest, String columnMappingsStr) {
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
            }
        } catch (Exception e) {
            logger.warn("Failed to parse column mappings: {}", e.getMessage());
        }
    }

    private void handleValidationServiceError(HttpServletResponse response, int jobId, int userId, 
                                             String uploadOption, IOException e) 
            throws SQLException, IOException {
        String userFriendlyMessage = e.getMessage() != null && e.getMessage().contains("Connection refused")
            ? "Validation service unavailable. Please ensure Python service is running on port 8000."
            : "Validation service error: " + e.getMessage();
        
        jobDAO.updateJobStatus(jobId, "Failed", true);
        jobDAO.updateJobProgress(jobId, "Failed", userFriendlyMessage);
        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, userFriendlyMessage, 0, 0, 0, 0);
        
        // Send failure notification
        sendNotification(userId, "Dataset", uploadOption, 0, 0, 0, 0, false, userFriendlyMessage, jobId);
        
        sendErrorResponse(response, userFriendlyMessage, 503);
    }

    private void handleValidationErrors(HttpServletResponse response, int jobId, int userId, 
                                       String uploadOption, String referenceName,
                                       JsonObject validationResponse) throws SQLException, IOException {
        JsonArray errors = validationResponse.getAsJsonArray("errors");
        jobDAO.updateJobStatus(jobId, "Failed", true);
        jobDAO.updateJobProgress(jobId, "Failed", "Validation failed");
        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, 
            "Validation failed with " + errors.size() + " errors", 0, 0, 0, errors.size());
        
        // Send failure notification
        String errorMessage = "Validation failed with " + errors.size() + " errors";
        sendNotification(userId, "Dataset", uploadOption, 0, 0, 0, errors.size(), false, errorMessage, jobId);

        for (int i = 0; i < errors.size(); i++) {
            JsonObject error = errors.get(i).getAsJsonObject();
            int reportItemId = jobDAO.createJobReportItem(jobId, error.get("field").getAsString(),
                "error", error.get("row").getAsInt());
            jobDAO.createJobReportItemMessage(reportItemId, error.get("error_code").getAsString(),
                error.get("message").getAsString(), "error");
        }

        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("status", "failed");
        errorResponse.addProperty("job_id", jobId);
        errorResponse.addProperty("reference_name", referenceName);
        errorResponse.addProperty("message", validationResponse.get("message").getAsString());
        errorResponse.addProperty("inserted", 0);
        errorResponse.addProperty("updated", 0);
        errorResponse.addProperty("deleted", 0);
        errorResponse.addProperty("failed", errors.size());
        errorResponse.add("errors", errors);
        
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write(gson.toJson(errorResponse));
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Determine segment ID based on segment mode and row data
     * @param segmentMode The segment mode (MULTIPLE, ENTERPRISE, or SPECIFIC)
     * @param selectedSegment The selected segment ID (for SPECIFIC mode)
     * @param rowData The row data from validation response (contains Segment column for MULTIPLE mode)
     * @return The segment ID to assign, or null if not applicable
     */
    private Long determineSegmentId(String segmentMode, String selectedSegment, JsonObject rowData) throws SQLException {
        String segmentNameFromExcel = getString(rowData, "Segment");
        logger.debug("determineSegmentId: segmentMode='{}', selectedSegment='{}', Segment from Excel='{}'", 
            segmentMode, selectedSegment, segmentNameFromExcel);
        
        if (segmentMode == null || segmentMode.trim().isEmpty()) {
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                logger.debug("segmentMode is null, using Segment from Excel='{}', segmentId={}", segmentNameFromExcel, segmentId);
                return segmentId;
            }
            return null;
        }
        
        String mode = segmentMode.trim();
        
        if ("MULTIPLE".equalsIgnoreCase(mode)) {
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug("MULTIPLE mode: using Segment from Excel='{}', segmentId={}", segmentNameFromExcel, segmentId);
                return segmentId;
            } else if (selectedSegment != null && !selectedSegment.trim().isEmpty()) {
                try {
                    Long segmentId = Long.parseLong(selectedSegment.trim());
                    logger.debug("MULTIPLE mode: No Segment in Excel, using selected segmentId={}", segmentId);
                    return segmentId;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid selected segment ID: " + selectedSegment);
                }
            } else {
                throw new IllegalArgumentException("Segment is required when 'Multiple' mode is selected");
            }
        } else if ("ENTERPRISE".equalsIgnoreCase(mode)) {
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug("ENTERPRISE mode: Segment specified in Excel='{}', using segmentId={} instead of Enterprise", 
                    segmentNameFromExcel, segmentId);
                return segmentId;
            }
            logger.debug("ENTERPRISE mode: No Segment in Excel, defaulting to Enterprise (segmentId=1)");
            return 1L;
        } else if ("SPECIFIC".equalsIgnoreCase(mode)) {
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug("SPECIFIC mode: Segment specified in Excel='{}', using segmentId={} (overrides UI selection)", 
                    segmentNameFromExcel, segmentId);
                return segmentId;
            }
            if (selectedSegment == null || selectedSegment.trim().isEmpty()) {
                throw new IllegalArgumentException("Segment ID is required when 'Specific' mode is selected");
            }
            try {
                Long segmentId = Long.parseLong(selectedSegment.trim());
                logger.debug("SPECIFIC mode: No Segment in Excel, using selected segmentId={} from UI", segmentId);
                return segmentId;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid segment ID: " + selectedSegment);
            }
        }
        
        logger.warn("Unknown segmentMode='{}', returning null", segmentMode);
        return null;
    }
    
    /**
     * Validate that user has access to the specified segment
     * @param userId The user ID
     * @param segmentId The segment ID to validate
     * @param rowNumber The row number (for error messages)
     * @throws IllegalArgumentException if user does not have access to the segment
     */
    private void validateSegmentAccess(int userId, Long segmentId, int rowNumber) throws SQLException {
        if (segmentId == null) {
            return;
        }
        
        if (SegmentAccessService.isSuperAdmin(userId)) {
            logger.debug("Row {}: User {} is Super Admin, has access to all segments", rowNumber, userId);
            return;
        }
        
        java.util.Set<Integer> accessibleSegmentIds = SegmentAccessService.getAccessibleSegmentIds(userId);
        
        if (segmentId == 1L) {
            logger.debug("Row {}: Segment is Enterprise (ID=1), always accessible", rowNumber);
            return;
        }
        
        if (!accessibleSegmentIds.contains(segmentId.intValue())) {
            String segmentName = getSegmentNameById(segmentId);
            throw new IllegalArgumentException("Row " + rowNumber + ": User does not have access to segment '" + 
                (segmentName != null ? segmentName : "ID " + segmentId) + "'");
        }
        
        logger.debug("Row {}: User {} has access to segment ID={}", rowNumber, userId, segmentId);
    }
    
    /**
     * Get segment name by ID
     * @param segmentId The segment ID
     * @return The segment name, or null if not found
     */
    private String getSegmentNameById(Long segmentId) throws SQLException {
        if (segmentId == null) {
            return null;
        }
        
        String sql = "SELECT Name FROM segment WHERE ID = ? AND Deleted_At IS NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, segmentId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        }
        return null;
    }
    
    /**
     * Get segment ID by name
     * @param segmentName The segment name
     * @return The segment ID, or null if not found
     */
    private Long getSegmentIdByName(String segmentName) throws SQLException {
        if (segmentName == null || segmentName.trim().isEmpty()) {
            return null;
        }
        
        // Handle "Enterprise" as special case
        if ("Enterprise".equalsIgnoreCase(segmentName.trim())) {
            return 1L;
        }
        
        String sql = "SELECT ID FROM segment WHERE LOWER(Name) = LOWER(?) AND Deleted_At IS NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, segmentName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("ID");
                }
            }
        }
        return null;
    }
}

