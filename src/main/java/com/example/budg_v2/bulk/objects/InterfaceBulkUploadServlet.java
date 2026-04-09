package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.InterfaceDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.common.BulkUploadFacetPermissionHelper;
import com.example.budg_v2.bulk.common.BulkUploadReferenceValidator;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@WebServlet("/api/bulk/interface/upload")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, maxFileSize = 1024 * 1024 * 10, maxRequestSize = 1024 * 1024 * 50)
public class InterfaceBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(InterfaceBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("interface"); }

    private final JobDAO jobDAO = new JobDAO();
    private final InterfaceDAO interfaceDAO = new InterfaceDAO();
    private final SegmentDAO segmentDAO = new SegmentDAO();

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

        logger.info("Interface bulk upload request received");

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
            String segmentMode = request.getParameter("segmentMode");
            String segment = request.getParameter("segment");

            // Interface entity does not use segment mode/segment params (inherits from
            // target system)
            if ("Interface".equalsIgnoreCase(entity)) {
                segmentMode = null;
                segment = null;
            }

            if (uploadOption == null || userIdStr == null) {
                sendErrorResponse(response, "Missing required parameters", 400);
                return;
            }

            int userId = Integer.parseInt(userIdStr);

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Interface";
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
            String fileName = "interface_" + timestamp + "_" + uuid + ".xlsx";

            String basePath = getBasePath();
            File directory = new File(basePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(basePath + fileName);
            filePart.write(file.getAbsolutePath());
            storagePath = basePath + fileName;

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

            JsonObject validationResponse;
            try {
                validationResponse = HttpClientUtil.postJsonGetJson(PYTHON_SERVICE_URL, validationRequest);
            } catch (IOException e) {
                handleValidationServiceError(response, jobId, userId, uploadOption, e);
                return;
            }

            String validationStatus = validationResponse.get("status").getAsString();
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);

            if ("error".equals(validationStatus)) {
                handleValidationErrors(response, jobId, userId, uploadOption, referenceName, validationResponse);
                return;
            }
            if ("invalid".equals(validationStatus)) {
                if (cancelOnWarning) {
                    handleValidationErrors(response, jobId, userId, uploadOption, referenceName, validationResponse);
                    return;
                }
                JsonArray validatedDataCheck = validationResponse.has("data")
                        ? validationResponse.getAsJsonArray("data")
                        : new JsonArray();
                if (validatedDataCheck.size() == 0) {
                    handleValidationErrors(response, jobId, userId, uploadOption, referenceName, validationResponse);
                    return;
                }
                // Create report items for validation errors so all rows appear in report
                JsonArray errors = validationResponse.getAsJsonArray("errors");
                if (errors != null) {
                    for (int i = 0; i < errors.size(); i++) {
                        JsonObject error = errors.get(i).getAsJsonObject();
                        int reportItemId = jobDAO.createJobReportItem(jobId, error.get("field").getAsString(),
                                "error", error.get("row").getAsInt());
                        jobDAO.createJobReportItemMessage(reportItemId, error.get("error_code").getAsString(),
                                error.get("message").getAsString(), "error");
                    }
                }
            }

            JsonArray validatedData = validationResponse.getAsJsonArray("data");
            int totalRows = validatedData.size();
            int totalRowsForJob = totalRows;
            if (validationResponse.has("total_rows") && !validationResponse.get("total_rows").isJsonNull()) {
                try {
                    int tr = validationResponse.get("total_rows").getAsInt();
                    if (tr > 0) {
                        totalRowsForJob = tr;
                    }
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
                processInterfaceData(finalJobId, validatedData, finalUserId, finalErrorHandling, finalUploadOption,
                        finalSegmentMode, finalSegment);
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
            if (jobId > 0) {
                try {
                    String currentStatus = jobDAO.getJobStatus(jobId);
                    if (currentStatus == null
                            || (!"Completed".equals(currentStatus) && !"Partially Completed".equals(currentStatus))) {
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());

                        // Send failure notification if we have userId
                        if (userIdStr != null && uploadOption != null) {
                            try {
                                int userId = Integer.parseInt(userIdStr);
                                sendNotification(userId, "Interface", uploadOption, 0, 0, 0, 0,
                                        false, "Unexpected error: " + e.getMessage(), jobId);
                            } catch (Exception notifEx) {
                                logger.error("Error sending notification", notifEx);
                            }
                        }
                    } else {
                        logger.info(
                                "Job {} already completed (status: {}), not overwriting with Failed after exception: {}",
                                jobId, currentStatus, e.getMessage());
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
                    failureMsg = errorMessage.length() > 200 ? errorMessage.substring(0, 197) + "..." : errorMessage;
                }
                n.setMessage(failureMsg);
                n.setEventType("UPLOAD_FAILED");
            }

            n.setChannel("ui");
            n.setFacetType("interface");
            n.setObjectId(jobId);
            n.setRead(false);

            dao.create(n);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }

    private void processInterfaceData(int jobId, JsonArray validatedData, int userId, String errorHandling,
            String uploadOption, String segmentMode, String segment) {
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();
        boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);

        // For DELETE operations: collect report rows
        List<BulkDeleteReportGenerator.ReportRow> deleteReportRows = new ArrayList<>();

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
                        int interfaceId = insertInterface(conn, rowData, userId, refValidator, nameValidator,
                                segmentMode, segment);

                        // Interface segment inherits from target system (not manually specified)
                        // Ignore any Segment column in Excel for Interface
                        Integer targetSystemId = getInteger(rowData, "Target System_ID");
                        if (targetSystemId != null && targetSystemId > 0) {
                            try {
                                syncInterfaceSegment(conn, interfaceId, targetSystemId, userId);
                                logger.info("Interface {} segment inherited from target system {}", interfaceId,
                                        targetSystemId);
                            } catch (Exception segEx) {
                                logger.warn("Failed to sync Interface {} segment from target system: {}", interfaceId,
                                        segEx.getMessage());
                                // Don't fail the row - segment inheritance is best effort
                            }
                        } else {
                            // If no target system, default to Enterprise
                            try {
                                ObjectSegmentService.assignObjectToSegment(conn, (long) interfaceId, "Interface", 1L,
                                        userId);
                                logger.info("Interface {} assigned to Enterprise (no target system)", interfaceId);
                            } catch (Exception segEx) {
                                logger.warn("Failed to assign Interface {} to Enterprise: {}", interfaceId,
                                        segEx.getMessage());
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    interfaceId,
                                    "Interface",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Interface {}: {}", interfaceId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        insertedCount++;

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Interface", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Interface created successfully", "info");

                    } else if ("UPDATE".equals(operation)) {
                        int interfaceId = updateInterface(conn, rowData, userId);

                        // Interface segment inherits from target system (not manually specified)
                        // Ignore any Segment column in Excel for Interface
                        // Sync segment if target system changed or if target system is specified
                        Integer targetSystemId = getInteger(rowData, "Target System_ID");
                        if (targetSystemId != null && targetSystemId > 0) {
                            try {
                                syncInterfaceSegment(conn, interfaceId, targetSystemId, userId);
                                logger.info("Interface {} segment synced from target system {} (updated)", interfaceId,
                                        targetSystemId);
                            } catch (Exception segEx) {
                                logger.warn("Failed to sync Interface {} segment from target system: {}", interfaceId,
                                        segEx.getMessage());
                                // Don't fail the row - segment inheritance is best effort
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    interfaceId,
                                    "Interface",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Interface {}: {}", interfaceId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        updatedCount++;

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Interface", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Interface updated successfully", "info");

                    } else if ("DELETE".equals(operation)) {
                        int interfaceId = rowData.get("ID").getAsInt();

                        // Validate deletion before attempting to delete (use conn for same transaction)
                        BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                        BulkDeleteValidationHelper.ValidationResult validationResult = validationHelper
                                .validateObjectForDeletion(conn, "interface", interfaceId);

                        // Check if validation passed
                        if (validationResult.canDelete()) {
                            // Check for warnings if cancelOnWarning is enabled
                            if (cancelOnWarning && validationResult.hasWarnings()) {
                                // Stop processing on warning
                                String warningMsg = String.join("; ", validationResult.getWarnings());
                                deleteReportRows.add(new BulkDeleteReportGenerator.ReportRow(
                                        rowNumber, interfaceId, validationResult.getObjectName(), "Warning",
                                        null, warningMsg, "Skipped - Cancel on Warning enabled"));

                                failedCount++;
                                int reportItemId = jobDAO.createJobReportItem(jobId, "Interface", "failed", rowNumber);
                                jobDAO.createJobReportItemMessage(reportItemId, "WARNING", warningMsg, "warning");

                                conn.rollback();
                                jobDAO.updateJobStatus(jobId, "Failed", true);
                                jobDAO.updateJobProgress(jobId, "Failed",
                                        "Cancelled at row " + rowNumber + " due to warning");
                                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                        "Cancelled at row " + rowNumber + " due to warning",
                                        insertedCount, updatedCount, deletedCount, failedCount);

                                // Generate and save report before returning
                                generateAndSaveDeleteReport(deleteReportRows, jobId, "Interface", userId);
                                return;
                            }

                            // Proceed with deletion
                            deleteInterface(conn, rowData, userId);
                            deletedCount++;

                            String warningMsg = validationResult.hasWarnings()
                                    ? String.join("; ", validationResult.getWarnings())
                                    : null;
                            deleteReportRows.add(new BulkDeleteReportGenerator.ReportRow(
                                    rowNumber, interfaceId, validationResult.getObjectName(), "Success",
                                    null, warningMsg, "Deleted successfully"));

                            int reportItemId = jobDAO.createJobReportItem(jobId, "Interface", "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    "Interface deleted successfully", "info");
                        } else {
                            // Validation failed - blocking errors
                            String errorMsg = String.join("; ", validationResult.getErrors());
                            String warningMsg = validationResult.hasWarnings()
                                    ? String.join("; ", validationResult.getWarnings())
                                    : null;

                            deleteReportRows.add(new BulkDeleteReportGenerator.ReportRow(
                                    rowNumber, interfaceId, validationResult.getObjectName(), "Failed",
                                    errorMsg, warningMsg, "Skipped - Validation failed"));

                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Interface", "failed", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "ERROR", errorMsg, "error");

                            if (cancelOnWarning) {
                                conn.rollback();
                                jobDAO.updateJobStatus(jobId, "Failed", true);
                                jobDAO.updateJobProgress(jobId, "Failed", "Failed at row " + rowNumber);
                                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                        "Failed at row " + rowNumber, insertedCount, updatedCount, deletedCount,
                                        failedCount);

                                // Generate and save report before returning
                                generateAndSaveDeleteReport(deleteReportRows, jobId, "Interface", userId);
                                return;
                            }
                        }
                    }

                    if (!cancelOnWarning) {
                        conn.commit();
                        logger.debug("Committed row {} successfully", rowNumber);
                    }

                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    int reportItemId = jobDAO.createJobReportItem(jobId, "Interface", "failed", rowNumber);
                    jobDAO.createJobReportItemMessage(reportItemId, "ERROR",
                            "Failed: " + e.getMessage(), "error");

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

            // Generate and save delete report if there were DELETE operations
            if (!deleteReportRows.isEmpty()) {
                generateAndSaveDeleteReport(deleteReportRows, jobId, "Interface", userId);
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

                sendNotification(userId, "Interface", uploadOption, insertedCount, updatedCount,
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
                sendNotification(userId, "Interface", uploadOption, insertedCount, updatedCount,
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
                sendNotification(userId, "Interface", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, true, null, jobId);
            }

        } catch (Exception e) {
            logger.error("Error processing interface bulk upload job {}", jobId, e);
            try {
                if (conn != null)
                    conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);

                // Send failure notification
                sendNotification(userId, "Interface", uploadOption, insertedCount, updatedCount,
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

    private int insertInterface(Connection conn, JsonObject rowData, int userId,
            BulkUploadReferenceValidator refValidator, BulkUploadNameValidator nameValidator, String segmentMode,
            String segment) throws SQLException {
        int rowNumber = rowData.has("row_number") ? rowData.get("row_number").getAsInt() : 0;
        String refNumber = getString(rowData, "Reference");

        // Validate reference uniqueness if provided
        if (refNumber != null && !refNumber.trim().isEmpty()) {
            try {
                refValidator.validateReferenceUnique("Interface", refNumber, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
            }
        }

        if (refNumber == null || refNumber.trim().isEmpty()) {
            refNumber = ReferenceNumberGenerator.generateInterfaceRefNumber();
            // Validate auto-generated reference as well
            try {
                refValidator.validateReferenceUnique("Interface", refNumber, conn);
            } catch (IllegalArgumentException e) {
                // If auto-generated is duplicate, generate a new one (should be rare)
                refNumber = ReferenceNumberGenerator.generateInterfaceRefNumber();
                refValidator.validateReferenceUnique("Interface", refNumber, conn);
            }
        }

        // Get name and validate uniqueness within segment
        String name = getString(rowData, "Interface Name");
        if (name != null && !name.trim().isEmpty()) {
            // Determine target segment ID
            Long segmentId = null;
            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                try {
                    segmentId = determineSegmentId(segmentMode, segment, rowData);
                } catch (SQLException e) {
                    logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                }
            }

            // Validate name uniqueness within segment (Interface uses "SystemInterface"
            // object type)
            if (BulkUploadNameValidator.hasNameColumn("Interface")) {
                try {
                    nameValidator.validateNameUniqueInSegment("Interface", name, segmentId, conn);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                }
            }
        }

        String sql = "INSERT INTO interface (Name, Ref_number, Description, Asset_ID, Synchronisation_Control, " +
                "Transfer_Method_ID, Transfer_Format_ID, Classification_id, Lifecycle_id, status_id, " +
                "Source_systemID, Target_systemID, Automation_ID, Frequency_ID, is_public, " +
                "createdBy_ID, created_datetime, last_updateuser_id, last_updatedtime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int idx = 1;
            ps.setString(idx++, name);
            ps.setString(idx++, refNumber);
            ps.setString(idx++, getString(rowData, "Interface Description"));
            ps.setString(idx++, getString(rowData, "Asset ID"));
            ps.setString(idx++, getString(rowData, "Synchronisation Control"));
            setNullableInt(ps, idx++, getInteger(rowData, "Transfer Method_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Transfer Format_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Interface Classification_ID"));
            // Lifecycle is required - use first value from list if not provided
            Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"), getFirstInterfaceLifecycleId());
            if (lifecycleId == null)
                lifecycleId = 1; // Fallback if table is empty
            ps.setInt(idx++, lifecycleId);
            setNullableInt(ps, idx++, coalesce(getInteger(rowData, "BUDG Status_ID"), getFirstStatusId()));
            setNullableInt(ps, idx++, getInteger(rowData, "Source System_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Target System_ID"));
            // Automation Level is required - use first value from list if not provided
            Integer automationId = coalesce(getInteger(rowData, "Automation Level_ID"),
                    getFirstInterfaceAutomationId());
            if (automationId == null)
                automationId = 1; // Fallback if table is empty
            ps.setInt(idx++, automationId);
            setNullableInt(ps, idx++, getInteger(rowData, "Frequency_ID"));
            setNullableInt(ps, idx++, coalesce(getInteger(rowData, "BUDG Viewing_ID"), getFirstViewingId()));
            ps.setInt(idx++, userId);
            ps.setInt(idx++, userId); // last_updateuser_id

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int interfaceId = rs.getInt(1);

                    // Create audit records using the same connection
                    String userName = getUserName(userId);
                    interfaceDAO.createInterfaceAuditRecords(conn, interfaceId, userName);

                    // Create audit snapshot
                    interfaceDAO.createInterfaceAuditRecord(conn, interfaceId);

                    // Link stakeholder
                    linkInterfaceStakeholder(conn, interfaceId, rowData, userId);

                    return interfaceId;
                }
            }
        }
        throw new SQLException("Failed to insert interface");
    }

    private int updateInterface(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int interfaceId = rowData.get("Interface ID").getAsInt();

        // Get old interface data before update for audit tracking
        com.example.budg_v2.model.Interface oldInterface = interfaceDAO.getInterfaceById(conn, interfaceId);
        if (oldInterface == null) {
            throw new SQLException("Interface not found with ID: " + interfaceId);
        }

        // Build dynamic SQL to only update provided fields
        StringBuilder sql = new StringBuilder("UPDATE interface SET ");
        List<String> updates = new ArrayList<>();

        if (rowData.has("Interface Name"))
            updates.add("Name = ?");
        if (rowData.has("Reference"))
            updates.add("Ref_number = ?");
        if (rowData.has("Interface Description"))
            updates.add("Description = ?");
        if (rowData.has("Asset ID"))
            updates.add("Asset_ID = ?");
        if (rowData.has("Synchronisation Control"))
            updates.add("Synchronisation_Control = ?");
        if (rowData.has("Transfer Method_ID"))
            updates.add("Transfer_Method_ID = ?");
        if (rowData.has("Transfer Format_ID"))
            updates.add("Transfer_Format_ID = ?");
        if (rowData.has("Interface Classification_ID"))
            updates.add("Classification_id = ?");
        if (rowData.has("Lifecycle_ID"))
            updates.add("Lifecycle_id = ?");
        if (rowData.has("BUDG Status_ID"))
            updates.add("status_id = ?");
        if (rowData.has("Source System_ID"))
            updates.add("Source_systemID = ?");
        if (rowData.has("Target System_ID"))
            updates.add("Target_systemID = ?");
        if (rowData.has("Automation Level_ID"))
            updates.add("Automation_ID = ?");
        if (rowData.has("Frequency_ID"))
            updates.add("Frequency_ID = ?");
        if (rowData.has("BUDG Viewing_ID"))
            updates.add("is_public = ?");

        if (updates.isEmpty()) {
            // No fields to update
            return interfaceId;
        }

        updates.add("last_updateuser_id = ?");
        updates.add("last_updatedtime = NOW()");

        sql.append(String.join(", ", updates));
        sql.append(" WHERE id = ?");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;

            if (rowData.has("Interface Name"))
                ps.setString(idx++, getString(rowData, "Interface Name"));
            if (rowData.has("Reference"))
                ps.setString(idx++, getString(rowData, "Reference"));
            if (rowData.has("Interface Description"))
                ps.setString(idx++, getString(rowData, "Interface Description"));
            if (rowData.has("Asset ID"))
                ps.setString(idx++, getString(rowData, "Asset ID"));
            if (rowData.has("Synchronisation Control"))
                ps.setString(idx++, getString(rowData, "Synchronisation Control"));
            if (rowData.has("Transfer Method_ID"))
                setNullableInt(ps, idx++, getInteger(rowData, "Transfer Method_ID"));
            if (rowData.has("Transfer Format_ID"))
                setNullableInt(ps, idx++, getInteger(rowData, "Transfer Format_ID"));
            if (rowData.has("Interface Classification_ID"))
                setNullableInt(ps, idx++, getInteger(rowData, "Interface Classification_ID"));

            // Lifecycle - use first value if not provided (required field)
            if (rowData.has("Lifecycle_ID")) {
                Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"), getFirstInterfaceLifecycleId());
                if (lifecycleId == null)
                    lifecycleId = 1; // Fallback
                ps.setInt(idx++, lifecycleId);
            }

            if (rowData.has("BUDG Status_ID"))
                setNullableInt(ps, idx++, getInteger(rowData, "BUDG Status_ID"));
            if (rowData.has("Source System_ID"))
                setNullableInt(ps, idx++, getInteger(rowData, "Source System_ID"));
            if (rowData.has("Target System_ID"))
                setNullableInt(ps, idx++, getInteger(rowData, "Target System_ID"));

            // Automation Level - use first value if not provided (required field)
            if (rowData.has("Automation Level_ID")) {
                Integer automationId = coalesce(getInteger(rowData, "Automation Level_ID"),
                        getFirstInterfaceAutomationId());
                if (automationId == null)
                    automationId = 1; // Fallback
                ps.setInt(idx++, automationId);
            }

            if (rowData.has("Frequency_ID"))
                setNullableInt(ps, idx++, getInteger(rowData, "Frequency_ID"));
            if (rowData.has("BUDG Viewing_ID"))
                setNullableInt(ps, idx++, getInteger(rowData, "BUDG Viewing_ID"));

            ps.setInt(idx++, userId);
            ps.setInt(idx++, interfaceId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Update failed, interface not found: " + interfaceId);
            }

            // Create audit records after successful update
            com.example.budg_v2.model.Interface newInterface = interfaceDAO.getInterfaceById(conn, interfaceId);
            String userName = getUserName(userId);
            interfaceDAO.createInterfaceUpdateAuditRecords(conn, interfaceId, oldInterface, newInterface, userName);
            interfaceDAO.createInterfaceUpdateAuditSnapshot(conn, interfaceId);

            return interfaceId;
        }
    }

    private void deleteInterface(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int interfaceId = rowData.get("ID").getAsInt();

        String sql = "UPDATE interface SET Deleted_datetime = NOW() WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
            int affected = ps.executeUpdate();

            if (affected > 0) {
                // Create audit records after successful deletion
                String userName = getUserName(userId);
                interfaceDAO.createInterfaceDeleteAuditRecords(conn, interfaceId, userName);
            }
        }
    }

    /**
     * Generate and save delete report Excel file
     */
    private void generateAndSaveDeleteReport(List<BulkDeleteReportGenerator.ReportRow> reportRows,
            int jobId, String entityName, int userId) {
        try {
            BulkDeleteReportGenerator reportGenerator = new BulkDeleteReportGenerator();
            byte[] reportBytes = reportGenerator.generateReport(reportRows, entityName, jobId);

            // Generate file name
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = entityName.toLowerCase() + "_delete_report_" + jobId + "_" + timestamp + ".xlsx";
            String storagePath = getBasePath() + fileName;

            // Ensure directory exists
            File directory = new File(getBasePath());
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Write file to disk
            File reportFile = new File(storagePath);
            Files.write(reportFile.toPath(), reportBytes);

            // Save to Job_Resource_FileName
            jobDAO.createJobResourceFile(jobId, fileName, fileName, storagePath, true, 90);

            logger.info("Delete report generated and saved for job {}: {}", jobId, storagePath);

        } catch (Exception e) {
            logger.error("Error generating delete report for job {}: {}", jobId, e.getMessage(), e);
            // Don't throw - report generation failure shouldn't fail the whole job
        }
    }

    private void linkInterfaceStakeholder(Connection conn, int interfaceId, JsonObject rowData, int userId) {
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
                    roleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, grName.trim(), "Interface");
                }
            }
            if (roleId == null) {
                roleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, "Interface Owner", "Interface");
            }
            if (roleId != null) {
                int rowNum = rowData.has("row_number") && !rowData.get("row_number").isJsonNull()
                        ? rowData.get("row_number").getAsInt() : 0;
                RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, stakeholderUserId, roleId, "Interface", rowNum);
            }
            if (roleId != null) {
                // Prepare stakeholder data
                Map<String, Object> stakeholderData = new HashMap<>();
                stakeholderData.put("userId", stakeholderUserId);
                stakeholderData.put("roleId", roleId);

                // Use DAO to create object_x_people (always creates new)
                int objectXPeopleId = interfaceDAO.createObjectXPeople(conn, stakeholderData, userId);

                // Use DAO to link (with duplicate prevention)
                interfaceDAO.linkStakeholderToInterface(conn, interfaceId, objectXPeopleId);

                // Create audit records
                String userName = getUserName(stakeholderUserId);
                interfaceDAO.createStakeholderAuditRecords(conn, interfaceId, userName, userName, roleId);
            }
        } catch (Exception e) {
            logger.error("Error linking stakeholder: {}", e.getMessage(), e);
        }
    }

    // Stakeholder methods now handled by InterfaceDAO

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

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private Integer coalesce(Integer... values) {
        for (Integer value : values) {
            if (value != null)
                return value;
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

    private Integer getFirstInterfaceLifecycleId() {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT id FROM interface_lifecycle ORDER BY id ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first interface lifecycle ID: {}", e.getMessage());
        }
        return null;
    }

    private Integer getFirstInterfaceAutomationId() {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT id FROM interface_automation ORDER BY id ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first interface automation ID: {}", e.getMessage());
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
                        "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'IF-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("IF-")) {
                        try {
                            int number = Integer.parseInt(refName.substring(3));
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
        return "IF-" + nextNumber;
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
            metadata.addProperty("entity", entity != null ? entity : "Interface");
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
        sendNotification(userId, "Interface", uploadOption, 0, 0, 0, 0, false, userFriendlyMessage, jobId);

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
        sendNotification(userId, "Interface", uploadOption, 0, 0, 0, errors.size(), false, errorMessage, jobId);

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
            // Even if segmentMode is null, check if Segment column exists in Excel
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
            // MULTIPLE mode: Use segment from Excel if provided, otherwise use
            // selectedSegment from UI
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                // User specified a segment in Excel, use it (overrides UI selection)
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug(
                        "MULTIPLE mode: Segment specified in Excel='{}', using segmentId={} (overrides UI selection)",
                        segmentNameFromExcel, segmentId);
                return segmentId;
            }
            // No segment in Excel, use the selected segment from UI
            if (selectedSegment != null && !selectedSegment.trim().isEmpty()) {
                try {
                    // selectedSegment might be an ID or a name
                    // Try parsing as ID first
                    try {
                        Long segmentId = Long.parseLong(selectedSegment.trim());
                        logger.debug("MULTIPLE mode: No Segment in Excel, using selectedSegment ID={} from UI",
                                segmentId);
                        return segmentId;
                    } catch (NumberFormatException e) {
                        // Not a number, try as name
                        Long segmentId = getSegmentIdByName(selectedSegment.trim());
                        if (segmentId != null) {
                            logger.debug(
                                    "MULTIPLE mode: No Segment in Excel, using selectedSegment name='{}' (segmentId={}) from UI",
                                    selectedSegment, segmentId);
                            return segmentId;
                        }
                        throw new IllegalArgumentException("Selected segment '" + selectedSegment + "' does not exist");
                    }
                } catch (SQLException e) {
                    throw new IllegalArgumentException("Error resolving selected segment: " + e.getMessage());
                }
            }
            // No segment in Excel and no selectedSegment in UI
            throw new IllegalArgumentException(
                    "Segment is required when 'Multiple' mode is selected. Either specify it in Excel or select it in the UI");
        } else if ("ENTERPRISE".equalsIgnoreCase(mode)) {
            // Check if Segment column exists in Excel - if provided, use it; otherwise
            // default to Enterprise
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                // User specified a segment in Excel, use it instead of defaulting to Enterprise
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug(
                        "ENTERPRISE mode: Segment specified in Excel='{}', using segmentId={} instead of Enterprise",
                        segmentNameFromExcel, segmentId);
                return segmentId;
            }
            // No segment specified in Excel, default to Enterprise
            logger.debug("ENTERPRISE mode: No Segment in Excel, defaulting to Enterprise (segmentId=1)");
            return 1L;
        } else if ("SPECIFIC".equalsIgnoreCase(mode)) {
            // SPECIFIC mode: Use segment from Excel if provided (overrides UI), otherwise
            // use selectedSegment from UI
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                // User specified a segment in Excel, use it (overrides UI selection)
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug(
                        "SPECIFIC mode: Segment specified in Excel='{}', using segmentId={} (overrides UI selection)",
                        segmentNameFromExcel, segmentId);
                return segmentId;
            }
            // No segment in Excel, use the selected segment from UI
            if (selectedSegment != null && !selectedSegment.trim().isEmpty()) {
                try {
                    // selectedSegment might be an ID or a name
                    // Try parsing as ID first
                    try {
                        Long segmentId = Long.parseLong(selectedSegment.trim());
                        logger.debug("SPECIFIC mode: No Segment in Excel, using selectedSegment ID={} from UI",
                                segmentId);
                        return segmentId;
                    } catch (NumberFormatException e) {
                        // Not a number, try as name
                        Long segmentId = getSegmentIdByName(selectedSegment.trim());
                        if (segmentId != null) {
                            logger.debug(
                                    "SPECIFIC mode: No Segment in Excel, using selectedSegment name='{}' (segmentId={}) from UI",
                                    selectedSegment, segmentId);
                            return segmentId;
                        }
                        throw new IllegalArgumentException("Selected segment '" + selectedSegment + "' does not exist");
                    }
                } catch (SQLException e) {
                    throw new IllegalArgumentException("Error resolving selected segment: " + e.getMessage());
                }
            }
            // No segment in Excel and no selectedSegment in UI
            throw new IllegalArgumentException(
                    "Segment is required when 'Specific' mode is selected. Either specify it in Excel or select it in the UI");
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

    /**
     * Validate that the user has access to the specified segment
     * 
     * @param userId    The user ID
     * @param segmentId The segment ID to validate
     * @param rowNumber The row number for error reporting
     * @throws SQLException             If database error occurs
     * @throws IllegalArgumentException If user does not have access to the segment
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
     * Sync interface segment from target system (inherits segment from target
     * system)
     */
    private void syncInterfaceSegment(Connection conn, int interfaceId, Integer targetSystemId, int userId)
            throws SQLException {
        SegmentInfo targetSegment = resolveTargetSegmentInfo(targetSystemId);
        int desiredSegmentId = targetSegment.id();
        int actorId = userId > 0 ? userId : 1;

        // Validate user has access to the target segment
        int rowNumber = 0; // Interface segment inheritance doesn't have a row number
        validateSegmentAccess(userId, (long) desiredSegmentId, rowNumber);

        int currentSegmentId = segmentDAO.getObjectSegmentId(interfaceId, "Interface");
        if (currentSegmentId == desiredSegmentId && currentSegmentId > 0) {
            return;
        }
        if (currentSegmentId > 0 && currentSegmentId != desiredSegmentId) {
            segmentDAO.removeObjectFromSegment(currentSegmentId, interfaceId, "Interface", actorId);
        }
        segmentDAO.assignObjectToSegment(desiredSegmentId, interfaceId, "Interface", actorId);
        logger.info("Interface {} segment synced to {} (inherited from target system {})",
                interfaceId, desiredSegmentId, targetSystemId);
    }

    /**
     * Resolve target segment info from target system
     */
    private SegmentInfo resolveTargetSegmentInfo(Integer targetSystemId) {
        if (targetSystemId == null || targetSystemId <= 0) {
            return new SegmentInfo(1, "Enterprise");
        }
        return SegmentResponseUtil.resolveSegmentInfo(segmentDAO, targetSystemId, "System");
    }
}
