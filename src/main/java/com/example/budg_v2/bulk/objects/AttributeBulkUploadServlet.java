package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.AttributeDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowNotification;
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
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@WebServlet("/api/bulk/attribute/upload")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, maxFileSize = 1024 * 1024 * 10, maxRequestSize = 1024 * 1024 * 50)
public class AttributeBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(AttributeBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("attribute"); }

    private final JobDAO jobDAO = new JobDAO();
    private final AttributeDAO attributeDAO = new AttributeDAO();

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

        logger.info("Attribute bulk upload request received");

        int jobId = -1;
        String storagePath = null;
        String userIdStr = null;
        String uploadOption = null;

        try {
            uploadOption = request.getParameter("uploadOption");
            String entity = request.getParameter("entity");
            userIdStr = request.getParameter("userId");
            String errorHandling = request.getParameter("errorHandling");
            String columnMappingsStr = request.getParameter("columnMappings");
            // Attribute bulk upload does not use segment mode; ignore segment params so
            // segment is never required
            String segmentMode = null;
            String segment = null;

            if (uploadOption == null || userIdStr == null) {
                sendErrorResponse(response, "Missing required parameters", 400);
                return;
            }

            int userId = Integer.parseInt(userIdStr);

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Attribute";
            if (!BulkUploadFacetPermissionHelper.canBulkUploadForModule(userId, entityName)) {
                sendErrorResponse(response, "You do not have permission to bulk upload for this entity.", 403);
                return;
            }

            Part filePart = request.getPart("file");
            if (filePart == null) {
                sendErrorResponse(response, "No file uploaded", 400);
                return;
            }

            String originalFileName = getFileName(filePart);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String uuid = UUID.randomUUID().toString().substring(0, 6);
            String fileName = "attribute_" + timestamp + "_" + uuid + ".xlsx";

            File directory = new File(getBasePath());
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(getBasePath() + fileName);
            filePart.write(file.getAbsolutePath());
            storagePath = file.getAbsolutePath(); // Store absolute path instead of relative

            logger.info("File saved to: {}", storagePath);

            String jobType = determineJobType(entityName, uploadOption);
            String referenceName = generateShortReferenceName();
            jobId = jobDAO.createJob(jobType, referenceName, 0, "Pending", userId);
            logger.info("Job created with ID: {} and reference: {}", jobId, referenceName);

            saveMetadataJson(jobId, userId, entity, uploadOption, errorHandling, referenceName,
                    originalFileName, storagePath, fileName);
            jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
            jobDAO.createJobProgress(jobId, 0, "Validating", "File uploaded, starting validation");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Pending", 5, "File uploaded");

            JsonObject validationRequest = new JsonObject();
            validationRequest.addProperty("file_path", storagePath); // Use stored absolute path directly
            validationRequest.addProperty("upload_option", uploadOption);
            validationRequest.addProperty("entity", entityName);
            validationRequest.addProperty("user_id", userId);

            // Attribute bulk upload does not use segment; no segment params sent to
            // validator

            if (columnMappingsStr != null && !columnMappingsStr.trim().isEmpty()) {
                addColumnMappings(validationRequest, columnMappingsStr);
            }

            JsonObject validationResponse;
            try {
                validationResponse = HttpClientUtil.postJsonGetJson(PYTHON_SERVICE_URL, validationRequest);
            } catch (IOException e) {
                handleValidationServiceError(response, jobId, userId, uploadOption, e);
                return;
            }

            String validationStatus = validationResponse.get("status").getAsString();

            // Determine cancelOnWarning setting
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);

            if ("error".equals(validationStatus)) {
                handleValidationErrors(response, jobId, userId, uploadOption, referenceName, validationResponse);
                return;
            } else if ("invalid".equals(validationStatus)) {
                if (cancelOnWarning) {
                    handleValidationErrors(response, jobId, userId, uploadOption, referenceName, validationResponse);
                    return;
                }

                // If not canceling on warning, check if we have any valid data to process
                JsonArray validatedDataCheck = validationResponse.has("data")
                        ? validationResponse.getAsJsonArray("data")
                        : new JsonArray();
                if (validatedDataCheck.size() == 0) {
                    // No valid rows to process - fail even if cancelOnWarning is false
                    handleValidationErrors(response, jobId, userId, uploadOption, referenceName, validationResponse);
                    return;
                }
                // Continue to process valid rows
            }

            if (!validationResponse.has("data") || validationResponse.get("data").isJsonNull() || !validationResponse.get("data").isJsonArray()) {
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Validation response missing data");
                sendErrorResponse(response, "Validation response missing data", 400);
                return;
            }
            JsonArray validatedData = validationResponse.getAsJsonArray("data");
            int validatedRowCount = validatedData.size();

            // Partial validation success: persist failed rows to the job report (same as handleValidationErrors)
            if ("invalid".equals(validationStatus) && validationResponse.has("errors")
                    && validationResponse.get("errors").isJsonArray()) {
                JsonArray validationErrs = validationResponse.getAsJsonArray("errors");
                if (validationErrs.size() > 0) {
                    appendValidationErrorsToJobReport(jobId, validationErrs);
                }
            }

            int totalRowsForJob = validatedRowCount;
            if (validationResponse.has("total_rows") && !validationResponse.get("total_rows").isJsonNull()) {
                try {
                    totalRowsForJob = validationResponse.get("total_rows").getAsInt();
                } catch (Exception e) {
                    logger.debug("Could not parse total_rows from validation response: {}", e.getMessage());
                }
            }
            jobDAO.updateJobItemsCount(jobId, totalRowsForJob);
            jobDAO.updateJobStatus(jobId, "Processing", false);

            final int finalJobId = jobId;
            final int finalUserId = userId;
            final String finalErrorHandling = errorHandling;
            final String finalUploadOption = uploadOption;
            final String finalSegmentMode = segmentMode;
            final String finalSegment = segment;

            Thread processingThread = new Thread(() -> {
                processAttributeData(finalJobId, validatedData, finalUserId, finalErrorHandling, finalUploadOption,
                        finalSegmentMode, finalSegment);
            });
            processingThread.setDaemon(false);
            processingThread.start();

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("message", "Processing " + validatedRowCount + " rows...");

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
                            sendNotification(userId, "Attribute", uploadOption, 0, 0, 0, 0,
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

    private void processAttributeData(int jobId, JsonArray validatedData, int userId, String errorHandling,
            String uploadOption, String segmentMode, String segment) {
        boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();

        Connection conn = null;
        BulkUploadReferenceValidator refValidator = new BulkUploadReferenceValidator();
        BulkUploadNameValidator nameValidator = new BulkUploadNameValidator();

        try {
            conn = DatabaseConnection.getConnection();
            if (cancelOnWarning) {
                conn.setAutoCommit(false);
            } else {
                conn.setAutoCommit(true);
            }

            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject rowData = validatedData.get(i).getAsJsonObject();
                String operation = rowData.get("operation").getAsString();
                int rowNumber = rowData.get("row_number").getAsInt();

                if (i % 10 == 0) {
                    int progressPercent = 30 + (int) ((i / (double) totalRows) * 65);
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                            String.format("Processing row %d of %d", i + 1, totalRows),
                            insertedCount, updatedCount, deletedCount, failedCount);
                }

                if (!cancelOnWarning) {
                    conn.setAutoCommit(false);
                }

                try {
                    if ("INSERT".equals(operation)) {
                        int attributeId = insertAttribute(conn, rowData, userId, refValidator, nameValidator,
                                segmentMode, segment);

                        // Assign object to segment (for INSERT operations when segmentation is enabled)
                        Long segmentIdToAssign = null;
                        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                            segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
                        } else {
                            // Check if Segment column exists in Excel even when segmentMode is null
                            String segmentName = getString(rowData, "Segment");
                            if (segmentName != null && !segmentName.trim().isEmpty()) {
                                segmentIdToAssign = getSegmentIdByName(segmentName.trim());
                            }
                        }

                        if (segmentIdToAssign != null) {
                            try {
                                validateSegmentAccess(userId, segmentIdToAssign, rowNumber);

                                ObjectSegmentService.assignObjectToSegment(
                                        conn,
                                        (long) attributeId,
                                        "Attribute",
                                        segmentIdToAssign,
                                        userId);
                                logger.info("Assigned Attribute {} to Segment {}", attributeId, segmentIdToAssign);
                            } catch (Exception segEx) {
                                // Segment assignment failure = row failure (BUDG behavior)
                                logger.error("Failed to assign Attribute {} to segment: {}", attributeId,
                                        segEx.getMessage(), segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    attributeId,
                                    "Attribute",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Attribute {}: {}", attributeId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        insertedCount++;

                        // Get dataset ID for valuesjob_dataset
                        Integer datasetId = getInteger(rowData, "Dataset_ID");
                        if (datasetId != null) {
                            jobDAO.insertValuesjobDataset(jobId, datasetId);
                        }

                        jobDAO.insertValuesjobMetaAttribute(jobId, "Suffix_Type", "Append");
                        jobDAO.insertValuesjobMetaAttribute(jobId, "IsOverwrite", "0");

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Attribute", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Attribute created successfully", "info");

                    } else if ("UPDATE".equals(operation)) {
                        int attributeId = updateAttribute(conn, rowData, userId);

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
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) attributeId,
                                        "Attribute");

                                // Only assign if segment is changing
                                if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                    // Validate segment change (Attribute doesn't have parent, so pass null)
                                    SegmentValidationService validator = new SegmentValidationService();
                                    SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            attributeId, newSegmentId.intValue(), "Attribute", null);

                                    if (!result.isValid && !result.canProceedWithWarning) {
                                        throw new RuntimeException("Segment validation failed: " + result.message);
                                    }

                                    validateSegmentAccess(userId, newSegmentId, rowNumber);

                                    // Assign new segment
                                    ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) attributeId,
                                            "Attribute",
                                            newSegmentId,
                                            userId);
                                    logger.info("Assigned Attribute {} to Segment {} (updated)", attributeId,
                                            newSegmentId);
                                }
                            } catch (Exception segEx) {
                                logger.error("Failed to assign Attribute {} to segment: {}", attributeId,
                                        segEx.getMessage(), segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    attributeId,
                                    "Attribute",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Attribute {}: {}", attributeId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        updatedCount++;

                        Integer datasetId = getInteger(rowData, "Dataset_ID");
                        if (datasetId != null) {
                            jobDAO.insertValuesjobDataset(jobId, datasetId);
                        }

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Attribute", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Attribute updated successfully", "info");

                    } else if ("DELETE".equals(operation)) {
                        int attributeId = rowData.get("Attribute ID").getAsInt();
                        BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                        BulkDeleteValidationHelper.ValidationResult validationResult = validationHelper
                                .validateObjectForDeletion(conn, "attribute", attributeId);
                        if (!validationResult.canDelete()) {
                            String errorMsg = String.join("; ", validationResult.getErrors());
                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Attribute", "failed", rowNumber);
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
                                    logger.debug("Rolled back row {} due to delete validation failure", rowNumber);
                                } catch (SQLException rollbackEx) {
                                    logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(),
                                            rollbackEx);
                                }
                            }
                        } else if (validationResult.canDelete() && validationResult.hasWarnings() && cancelOnWarning) {
                            String warningMsg = String.join("; ", validationResult.getWarnings());
                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Attribute", "failed", rowNumber);
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
                            deleteAttribute(conn, rowData);
                            deletedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Attribute", "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    "Attribute deleted successfully", "info");
                        }
                    }

                    if (!cancelOnWarning) {
                        conn.commit();
                        logger.debug("Committed row {} successfully", rowNumber);
                    }

                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    int reportItemId = jobDAO.createJobReportItem(jobId, "Attribute", "failed", rowNumber);
                    String descriptionFromExcel = getString(rowData, "Description");
                    String reportMessage = (descriptionFromExcel != null && !descriptionFromExcel.isBlank())
                            ? descriptionFromExcel
                            : "Failed: " + e.getMessage();
                    jobDAO.createJobReportItemMessage(reportItemId, "ERROR",
                            reportMessage, "error");

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
                            logger.debug("Rolled back row {} due to error", rowNumber);
                        } catch (SQLException rollbackEx) {
                            logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(),
                                    rollbackEx);
                        }
                    }
                } finally {
                    if (!cancelOnWarning) {
                        try {
                            conn.setAutoCommit(true);
                        } catch (SQLException e) {
                            logger.error("Error resetting auto-commit: {}", e.getMessage(), e);
                        }
                    }
                }
            }

            if (cancelOnWarning) {
                conn.commit();
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
                        "Processing failed: all rows failed", insertedCount, updatedCount, deletedCount,
                        failedCount);

                logger.info("Bulk upload job {} failed: {} inserted, {} updated, {} deleted, {} failed",
                        jobId, insertedCount, updatedCount, deletedCount, failedCount);

                sendNotification(userId, "Attribute", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, false, "All rows failed during processing", jobId);
            } else if (isPartiallyCompleted) {
                jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount); // Only successful operations
                jobDAO.updateJobProgress(jobId, "Partially Completed",
                        String.format("Complete: %d inserted, %d updated, %d deleted, %d failed",
                                insertedCount, updatedCount, deletedCount, failedCount));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                        "Processing complete with some failures", insertedCount, updatedCount, deletedCount,
                        failedCount);

                logger.info("Bulk upload job {} partially completed: {} inserted, {} updated, {} deleted, {} failed",
                        jobId, insertedCount, updatedCount, deletedCount, failedCount);

                // Send notification for partial completion
                sendNotification(userId, "Attribute", uploadOption, insertedCount, updatedCount,
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
                sendNotification(userId, "Attribute", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, true, null, jobId);
            }

        } catch (Exception e) {
            logger.error("Error processing attribute bulk upload job {}", jobId, e);
            try {
                if (conn != null)
                    conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);

                // Send failure notification
                sendNotification(userId, "Attribute", uploadOption, insertedCount, updatedCount,
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

    private int insertAttribute(Connection conn, JsonObject rowData, int userId,
            BulkUploadReferenceValidator refValidator, BulkUploadNameValidator nameValidator, String segmentMode,
            String segment) throws SQLException {
        int rowNumber = rowData.has("row_number") ? rowData.get("row_number").getAsInt() : 0;
        String refNumber = getString(rowData, "Reference Number");

        // Validate reference uniqueness if provided
        if (refNumber != null && !refNumber.trim().isEmpty()) {
            try {
                refValidator.validateReferenceUnique("Attribute", refNumber, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
            }
        }

        if (refNumber == null || refNumber.trim().isEmpty()) {
            Set<String> batchRefs = refValidator.getBatchRefsForFacet("Attribute");
            refNumber = ReferenceNumberGenerator.generateAttributeRefNumber(conn, batchRefs);
            try {
                refValidator.validateReferenceUnique("Attribute", refNumber, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
            }
        }

        // Get name and validate uniqueness per dataset (duplicate names allowed across different datasets)
        String name = getString(rowData, "Attribute Name");
        Integer datasetIdForName = getInteger(rowData, "Dataset_ID");
        if (name != null && !name.trim().isEmpty()) {
            if (datasetIdForName != null) {
                if (BulkUploadNameValidator.hasNameColumn("Attribute")) {
                    try {
                        nameValidator.validateNameUniqueInDataset("Attribute", name, datasetIdForName, conn);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                    }
                }
            } else {
                // No dataset: fall back to segment-based uniqueness
                Long segmentId = null;
                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                    try {
                        segmentId = determineSegmentId(segmentMode, segment, rowData);
                    } catch (SQLException e) {
                        logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                    }
                }
                if (BulkUploadNameValidator.hasNameColumn("Attribute")) {
                    try {
                        nameValidator.validateNameUniqueInSegment("Attribute", name, segmentId, conn);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                    }
                }
            }
        }

        // Validate dataset/glossary segment compatibility
        // Skip validation for super admin
        boolean skipValidation = false;
        try {
            skipValidation = SegmentAccessService.isSuperAdmin(userId);
        } catch (SQLException e) {
            logger.warn("Error checking super admin status: {}", e.getMessage());
        }

        if (!skipValidation) {
            Integer datasetId = getInteger(rowData, "Dataset_ID");
            Integer glossaryId = getInteger(rowData, "Glossary_ID");
            if (datasetId != null && glossaryId != null) {
                try {
                    SegmentValidationService segmentValidator = new SegmentValidationService();
                    SegmentValidationService.ValidationResult result = segmentValidator
                            .validateAttributeGlossarySegment(datasetId, glossaryId);
                    if (!result.isValid) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": " + result.message);
                    }
                } catch (SQLException e) {
                    logger.warn("Failed to validate dataset/glossary segment compatibility: {}", e.getMessage());
                    // Continue with insert - validation error will be caught if critical
                }
            }
        }

        String sql = "INSERT INTO attribute (RefNumber, PrimaryName, Definition, Is_PrimaryKey, " +
                "Business_Logic, DataLength, Data_type_ID, Requirement_ID, Dataset_ID, " +
                "Glossary_ID, Origination, Editability, Editability_role, CreatedBy, CreatedDatetime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int idx = 1;
            ps.setString(idx++, refNumber);
            ps.setString(idx++, name);
            ps.setString(idx++, getString(rowData, "Attribute Definition"));
            setNullableInt(ps, idx++, getInteger(rowData, "Key"));
            ps.setString(idx++, getString(rowData, "Business Logic"));
            setNullableInt(ps, idx++, getInteger(rowData, "Data Length"));
            setNullableInt(ps, idx++, getInteger(rowData, "Data Type_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Attribute Requirement_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Dataset_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Glossary_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Origin_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Editability_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Editability Role_ID"));
            ps.setInt(idx++, userId);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int attributeId = rs.getInt(1);

                    // Create alias name for DB Field Name if provided
                    String dbFieldName = getString(rowData, "DB Field Name");
                    if (dbFieldName != null && !dbFieldName.trim().isEmpty()) {
                        insertAliasName(conn, attributeId, dbFieldName, 1); // Type 1 = DB Field Name
                    }

                    // Create audit records (attribute_audit_history)
                    String userName = getUserName(userId);
                    attributeDAO.createAttributeAuditRecords(conn, attributeId, userName);

                    // Create audit snapshot (attribute_audit)
                    attributeDAO.createAttributeAuditRecord(conn, attributeId);

                    // Link stakeholder (creates object_x_people, attribute_x_objectxpeople, and
                    // stakeholder audit)
                    linkAttributeStakeholder(conn, attributeId, rowData, userId);

                    return attributeId;
                }
            }
        }
        throw new SQLException("Failed to insert attribute");
    }

    private void insertAliasName(Connection conn, int attributeId, String aliasName, int nameType) throws SQLException {
        String sql = "INSERT INTO attribute_alias_name (AttributeID, Name, Name_Type) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            ps.setString(2, aliasName);
            ps.setInt(3, nameType);
            ps.executeUpdate();
        }
    }

    private int updateAttribute(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int attributeId = rowData.get("Attribute ID").getAsInt();

        // Load existing Dataset_ID and Glossary_ID for merge-with-existing (resolution-only fields must not overwrite)
        Integer existingDatasetId = null;
        Integer existingGlossaryId = null;
        String selectSql = "SELECT Dataset_ID, Glossary_ID FROM attribute WHERE ID = ?";
        try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
            selectPs.setInt(1, attributeId);
            try (ResultSet rs = selectPs.executeQuery()) {
                if (rs.next()) {
                    Object ds = rs.getObject("Dataset_ID");
                    existingDatasetId = ds == null ? null : ((Number) ds).intValue();
                    Object gl = rs.getObject("Glossary_ID");
                    existingGlossaryId = gl == null ? null : ((Number) gl).intValue();
                }
            }
        }

        String sql = "UPDATE attribute SET RefNumber = ?, PrimaryName = ?, Definition = ?, " +
                "Is_PrimaryKey = ?, Business_Logic = ?, DataLength = ?, Data_type_ID = ?, " +
                "Requirement_ID = ?, Dataset_ID = ?, Glossary_ID = ?, Origination = ?, " +
                "Editability = ?, Editability_role = ?, " +
                "Last_UpdatedUser_ID = ?, Last_UpdateDatetime = NOW() WHERE ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, getString(rowData, "Reference Number"));
            ps.setString(idx++, getString(rowData, "Attribute Name"));
            ps.setString(idx++, getString(rowData, "Attribute Definition"));
            setNullableInt(ps, idx++, getInteger(rowData, "Key"));
            ps.setString(idx++, getString(rowData, "Business Logic"));
            setNullableInt(ps, idx++, getInteger(rowData, "Data Length"));
            setNullableInt(ps, idx++, getInteger(rowData, "Data Type_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Attribute Requirement_ID"));
            Integer datasetId = (rowData.has("Dataset_ID") && !rowData.get("Dataset_ID").isJsonNull()) ? getInteger(rowData, "Dataset_ID") : existingDatasetId;
            Integer glossaryId = (rowData.has("Glossary_ID") && !rowData.get("Glossary_ID").isJsonNull()) ? getInteger(rowData, "Glossary_ID") : existingGlossaryId;
            setNullableInt(ps, idx++, datasetId);
            setNullableInt(ps, idx++, glossaryId);
            setNullableInt(ps, idx++, getInteger(rowData, "Origin_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Editability_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Editability Role_ID"));
            ps.setInt(idx++, userId);
            ps.setInt(idx++, attributeId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Update failed, attribute not found: " + attributeId);
            }

            // Update DB Field Name alias if provided
            String dbFieldName = getString(rowData, "DB Field Name");
            if (dbFieldName != null && !dbFieldName.trim().isEmpty()) {
                // Delete existing alias and insert new one
                String deleteSql = "DELETE FROM attribute_alias_name WHERE AttributeID = ? AND Name_Type = 1";
                try (PreparedStatement delPs = conn.prepareStatement(deleteSql)) {
                    delPs.setInt(1, attributeId);
                    delPs.executeUpdate();
                }
                insertAliasName(conn, attributeId, dbFieldName, 1);
            }

            return attributeId;
        }
    }

    private void deleteAttribute(Connection conn, JsonObject rowData) throws SQLException {
        int attributeId = rowData.get("Attribute ID").getAsInt();

        String sql = "UPDATE attribute SET DeletedDatetime = NOW() WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            ps.executeUpdate();
        }
    }

    private void linkAttributeStakeholder(Connection conn, int attributeId, JsonObject rowData, int userId) {
        try {
            // Resolve stakeholder user: use User Email from file if provided, otherwise use
            // uploader
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

            Integer roleId = getInteger(rowData, "Governance Role_ID");
            if (roleId == null) {
                String grName = getString(rowData, "Governance Role");
                if (grName != null && !grName.trim().isEmpty()) {
                    try {
                        roleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, grName.trim(), "Attribute");
                    } catch (IllegalArgumentException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }
            }
            if (roleId == null) {
                try {
                    roleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, "Attribute Owner", "Attribute");
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            if (roleId != null) {
                int rowNum = rowData.has("row_number") && !rowData.get("row_number").isJsonNull()
                        ? rowData.get("row_number").getAsInt() : 0;
                RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, stakeholderUserId.intValue(), roleId.intValue(), "Attribute", rowNum);
            }
            if (roleId != null) {
                // Prepare stakeholder data
                java.util.Map<String, Object> stakeholderData = new java.util.HashMap<>();
                stakeholderData.put("userId", stakeholderUserId);
                stakeholderData.put("roleId", roleId);

                // Use DAO to create object_x_people (always creates new)
                int objectXPeopleId = attributeDAO.createObjectXPeople(conn, stakeholderData, userId);

                // Use DAO to link (with duplicate prevention)
                attributeDAO.linkStakeholderToAttribute(conn, attributeId, objectXPeopleId, userId);

                // Create audit records for stakeholder (attribute_audit_history)
                String userName = getUserName(stakeholderUserId);
                attributeDAO.createStakeholderAuditRecords(conn, attributeId, userName, userName, roleId);
            }
        } catch (Exception e) {
            logger.error("Error linking stakeholder: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to link stakeholder", e);
        }
    }

    // Stakeholder methods now handled by AttributeDAO

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

    @SuppressWarnings("unused")
    private Boolean getBoolean(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsBoolean();
        }
        return null;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    @SuppressWarnings("unused")
    private void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value != null) {
            ps.setBoolean(index, value);
        } else {
            ps.setNull(index, Types.BOOLEAN);
        }
    }

    private Integer coalesce(Integer... values) {
        for (Integer value : values) {
            if (value != null)
                return value;
        }
        return null;
    }

    private Integer getRoleIdByName(String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT id FROM object_role WHERE LOWER(primaryname) = LOWER(?) LIMIT 1")) {
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

    @SuppressWarnings("unused")
    private Integer getDataTypeIdByName(String dataTypeName) {
        if (dataTypeName == null || dataTypeName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT ID FROM attribute_datatype WHERE LOWER(PrimaryName) = LOWER(?) LIMIT 1")) {
            ps.setString(1, dataTypeName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting data type ID by name: {}", dataTypeName, e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Integer getRequirementIdByName(String requirementName) {
        if (requirementName == null || requirementName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT ID FROM requirement WHERE PrimaryName = ? LIMIT 1")) {
            ps.setString(1, requirementName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting requirement ID by name: {}", requirementName, e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Integer getOriginationIdByName(String originationName) {
        if (originationName == null || originationName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT ID FROM attribute_origination WHERE LOWER(PrimaryName) = LOWER(?) LIMIT 1")) {
            ps.setString(1, originationName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting origination ID by name: {}", originationName, e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Integer getEditabilityIdByName(String editabilityName) {
        if (editabilityName == null || editabilityName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT ID FROM attribute_editability WHERE PrimaryName = ? LIMIT 1")) {
            ps.setString(1, editabilityName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting editability ID by name: {}", editabilityName, e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Integer getEditabilityRoleIdByName(String editabilityRoleName) {
        if (editabilityRoleName == null || editabilityRoleName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT ID FROM attribute_edit_role WHERE LOWER(PrimaryName) = LOWER(?) LIMIT 1")) {
            ps.setString(1, editabilityRoleName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting editability role ID by name: {}", editabilityRoleName, e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Integer getDatasetIdBySystemShortName(String systemShortName) {
        if (systemShortName == null || systemShortName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT d.ID FROM dataset d " +
                                "JOIN system s ON d.MasterSource = s.id " +
                                "WHERE s.Name = ? LIMIT 1")) {
            ps.setString(1, systemShortName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting dataset ID by system short name: {}", systemShortName, e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Integer getGlossaryIdByParentName(String parentGlossaryName) {
        if (parentGlossaryName == null || parentGlossaryName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT ID FROM glossary WHERE Name = ? LIMIT 1")) {
            ps.setString(1, parentGlossaryName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting parent glossary ID by name: {}", parentGlossaryName, e);
        }
        return null;
    }

    /**
     * Resolve User Email to User ID
     * 
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
                        "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'ATT-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("ATT-")) {
                        try {
                            int number = Integer.parseInt(refName.substring(4));
                            if (number > maxNumber)
                                maxNumber = number;
                        } catch (NumberFormatException e) {
                        }
                    }
                }
                if (maxNumber > 0)
                    nextNumber = maxNumber + 1;
            }
        } catch (SQLException e) {
            logger.warn("Error generating reference name: {}", e.getMessage());
        }
        return "ATT-" + nextNumber;
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
            metadata.addProperty("entity", entity != null ? entity : "Attribute");
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
        sendNotification(userId, "Attribute", uploadOption, 0, 0, 0, 0, false, userFriendlyMessage, jobId);

        sendErrorResponse(response, userFriendlyMessage, 503);
    }

    /**
     * Writes validation errors to job report items without failing the job.
     * Used when some rows pass validation and processing continues ("Continue on Warning").
     */
    private void appendValidationErrorsToJobReport(int jobId, JsonArray errors) throws SQLException {
        for (int i = 0; i < errors.size(); i++) {
            JsonObject error = errors.get(i).getAsJsonObject();
            int reportItemId = jobDAO.createJobReportItem(jobId, error.get("field").getAsString(),
                    "error", error.get("row").getAsInt());
            String messageText = error.has("description") && !error.get("description").isJsonNull()
                    && error.get("description").getAsString() != null
                    && !error.get("description").getAsString().isBlank()
                            ? error.get("description").getAsString()
                            : error.get("message").getAsString();
            jobDAO.createJobReportItemMessage(reportItemId, error.get("error_code").getAsString(),
                    messageText, "error");
        }
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
        sendNotification(userId, "Attribute", uploadOption, 0, 0, 0, errors.size(), false, errorMessage, jobId);

        for (int i = 0; i < errors.size(); i++) {
            JsonObject error = errors.get(i).getAsJsonObject();
            int reportItemId = jobDAO.createJobReportItem(jobId, error.get("field").getAsString(),
                    "error", error.get("row").getAsInt());
            String messageText = error.has("description") && !error.get("description").isJsonNull()
                    && error.get("description").getAsString() != null
                    && !error.get("description").getAsString().isBlank()
                            ? error.get("description").getAsString()
                            : error.get("message").getAsString();
            jobDAO.createJobReportItemMessage(reportItemId, error.get("error_code").getAsString(),
                    messageText, "error");
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
                    failureMsg = errorMessage.length() > 200 ? errorMessage.substring(0, 197) + "..." : errorMessage;
                }
                n.setMessage(failureMsg);
                n.setEventType("UPLOAD_FAILED");
            }

            n.setChannel("ui");
            n.setFacetType("attribute");
            n.setObjectId(jobId);
            n.setRead(false);

            dao.create(n);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
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

    /**
     * Determine segment ID based on segment mode and row data
     * 
     * @param segmentMode     The segment mode (MULTIPLE, ENTERPRISE, or SPECIFIC)
     * @param selectedSegment The selected segment ID (for SPECIFIC mode)
     * @param rowData         The row data from validation response (contains
     *                        Segment column for MULTIPLE mode)
     * @return The segment ID to assign, or null if not applicable
     */
    private Long determineSegmentId(String segmentMode, String selectedSegment, JsonObject rowData)
            throws SQLException {
        String segmentNameFromExcel = getString(rowData, "Segment");
        logger.debug("determineSegmentId: segmentMode='{}', selectedSegment='{}', Segment from Excel='{}'",
                segmentMode, selectedSegment, segmentNameFromExcel);

        if (segmentMode == null || segmentMode.trim().isEmpty()) {
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                logger.debug("segmentMode is null, using Segment from Excel='{}', segmentId={}", segmentNameFromExcel,
                        segmentId);
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
                logger.debug("MULTIPLE mode: using Segment from Excel='{}', segmentId={}", segmentNameFromExcel,
                        segmentId);
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
                logger.debug(
                        "ENTERPRISE mode: Segment specified in Excel='{}', using segmentId={} instead of Enterprise",
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
                logger.debug(
                        "SPECIFIC mode: Segment specified in Excel='{}', using segmentId={} (overrides UI selection)",
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
     * 
     * @param userId    The user ID
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
     * 
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
     * 
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
