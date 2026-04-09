package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.GlossaryDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.common.BulkUploadFacetPermissionHelper;
import com.example.budg_v2.bulk.common.BulkUploadParentValidator;
import com.example.budg_v2.bulk.common.BulkUploadReferenceValidator;
import com.example.budg_v2.bulk.common.BulkUploadNameValidator;
import com.example.budg_v2.bulk.roles.util.RoleHandlerUtil;
import com.example.budg_v2.bulk.common.BulkUploadUtil;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@WebServlet("/api/bulk/glossary/upload")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, maxFileSize = 1024 * 1024 * 10, maxRequestSize = 1024 * 1024 * 50)
public class GlossaryBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("glossary"); }

    private final JobDAO jobDAO = new JobDAO();
    private final GlossaryDAO glossaryDAO = new GlossaryDAO();

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

        logger.info("Glossary bulk upload request received");

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

            if (uploadOption == null || userIdStr == null) {
                sendErrorResponse(response, "Missing required parameters", 400);
                return;
            }

            int userId = Integer.parseInt(userIdStr);

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Glossary";
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
            String fileName = "glossary_" + timestamp + "_" + uuid + ".xlsx";

            File directory = new File(getBasePath());
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(getBasePath() + fileName);
            filePart.write(file.getAbsolutePath());
            storagePath = getBasePath() + fileName;

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
                processGlossaryData(finalJobId, validatedData, finalUserId, finalErrorHandling, finalUploadOption,
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
            if (jobId != -1) {
                try {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());

                    // Send failure notification if we have userId
                    if (userIdStr != null && uploadOption != null) {
                        try {
                            int userId = Integer.parseInt(userIdStr);
                            sendNotification(userId, "Glossary", uploadOption, 0, 0, 0, 0,
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

    private void processGlossaryData(int jobId, JsonArray validatedData, int userId, String errorHandling,
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
        Set<String> glossaryBatchRefs = new HashSet<>();

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
                        int glossaryId = insertGlossary(conn, rowData, userId, segmentMode, segment, refValidator,
                                nameValidator, glossaryBatchRefs);

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    glossaryId,
                                    "Glossary",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Glossary {}: {}", glossaryId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        insertedCount++;

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Glossary created successfully", "info");

                    } else if ("UPDATE".equals(operation)) {
                        List<String> identityCols = Arrays.asList("ID", "Name", "Ref.");
                        String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols,
                                "Glossary", rowNumber);
                        if (identityErr != null) {
                            throw new IllegalArgumentException(identityErr);
                        }
                        Integer idById = null;
                        Integer idByName = null;
                        Integer idByRef = null;
                        Integer idVal = BulkUploadUtil.getInteger(rowData, "ID");
                        if (idVal != null) {
                            Integer found = getGlossaryIdById(conn, idVal);
                            if (found != null)
                                idById = found;
                        }
                        String nameVal = BulkUploadUtil.getString(rowData, "Name");
                        if (nameVal != null && !nameVal.trim().isEmpty()) {
                            idByName = getGlossaryIdByName(conn, nameVal.trim());
                        }
                        String refVal = BulkUploadUtil.getString(rowData, "Ref.");
                        if (refVal != null && !refVal.trim().isEmpty()) {
                            idByRef = getGlossaryIdByRef(conn, refVal.trim());
                        }
                        int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                        Integer glossaryId = coalesce(idById, idByName, idByRef);
                        if (glossaryId == null) {
                            throw new IllegalArgumentException("Row " + rowNumber
                                    + ": No glossary found for the provided identity (ID, Name, or Ref.).");
                        }
                        if (filledIdentity >= 2) {
                            Set<Integer> ids = new HashSet<>();
                            if (idById != null)
                                ids.add(idById);
                            if (idByName != null)
                                ids.add(idByName);
                            if (idByRef != null)
                                ids.add(idByRef);
                            if (ids.size() > 1) {
                                throw new IllegalArgumentException(
                                        "Row " + rowNumber + ": ID, Name and Ref. refer to different glossary terms.");
                            }
                        }
                        // When parent is specified, validate circular hierarchy and segment before
                        // update
                        Integer parentId = getInteger(rowData, "Parent_ID");
                        if (parentId != null && parentId > 0) {
                            try {
                                if (BulkUploadParentValidator.wouldCreateParentCycle(conn, glossaryId, parentId,
                                        "Glossary")) {
                                    throw new RuntimeException(
                                            "Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.");
                                }
                            } catch (SQLException e) {
                                throw new RuntimeException("Failed to validate parent hierarchy: " + e.getMessage(), e);
                            }
                            Long newSegmentIdForParent = null;
                            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                newSegmentIdForParent = determineSegmentId(segmentMode, segment, rowData);
                            } else {
                                String segmentName = getString(rowData, "Segment");
                                if (segmentName != null && !segmentName.trim().isEmpty()) {
                                    newSegmentIdForParent = getSegmentIdByName(segmentName.trim());
                                }
                            }
                            Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) glossaryId,
                                    "Glossary");
                            int effectiveChildSegmentId = (newSegmentIdForParent != null)
                                    ? newSegmentIdForParent.intValue()
                                    : (currentSegmentId != null ? currentSegmentId.intValue() : 1);
                            SegmentValidationService parentValidator = new SegmentValidationService();
                            SegmentValidationService.ValidationResult parentResult = parentValidator
                                    .validateParentChildSegment(
                                            parentId, effectiveChildSegmentId, "Glossary");
                            if (!parentResult.isValid) {
                                throw new RuntimeException("Parent segment validation failed: " + parentResult.message);
                            }
                        }
                        rowData.add("ID", new JsonPrimitive(glossaryId));
                        updateGlossary(conn, rowData, userId);

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
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) glossaryId,
                                        "Glossary");

                                // Only assign if segment is changing
                                if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                    // Validate segment change
                                    SegmentValidationService validator = new SegmentValidationService();
                                    SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            glossaryId, newSegmentId.intValue(), "Glossary", parentId);

                                    if (!result.isValid && !result.canProceedWithWarning) {
                                        throw new RuntimeException("Segment validation failed: " + result.message);
                                    }

                                    validateSegmentAccess(userId, newSegmentId, rowNumber);

                                    // Assign new segment
                                    ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) glossaryId,
                                            "Glossary",
                                            newSegmentId,
                                            userId);
                                    logger.info("Assigned Glossary {} to Segment {} (updated)", glossaryId,
                                            newSegmentId);
                                }
                            } catch (Exception segEx) {
                                logger.error("Failed to assign Glossary {} to segment: {}", glossaryId,
                                        segEx.getMessage(), segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    glossaryId,
                                    "Glossary",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Glossary {}: {}", glossaryId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        updatedCount++;

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Glossary updated successfully", "info");

                    } else if ("DELETE".equals(operation)) {
                        int glossaryId = rowData.get("ID").getAsInt();
                        BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                        BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "glossary", glossaryId);
                        if (!validationResult.canDelete()) {
                            String errorMsg = String.join("; ", validationResult.getErrors());
                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "failed", rowNumber);
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
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "failed", rowNumber);
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
                            deleteGlossary(conn, rowData);
                            deletedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    "Glossary deleted successfully", "info");
                        }
                    }

                    if (!cancelOnWarning) {
                        conn.commit();
                        logger.debug("Committed row {} successfully", rowNumber);
                    }

                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "failed", rowNumber);
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

                sendNotification(userId, "Glossary", uploadOption, insertedCount, updatedCount,
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
                sendNotification(userId, "Glossary", uploadOption, insertedCount, updatedCount,
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
                sendNotification(userId, "Glossary", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, true, null, jobId);
            }

        } catch (Exception e) {
            logger.error("Error processing glossary bulk upload job {}", jobId, e);
            try {
                if (conn != null)
                    conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);

                // Send failure notification
                sendNotification(userId, "Glossary", uploadOption, insertedCount, updatedCount,
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

    private int insertGlossary(Connection conn, JsonObject rowData, int userId, String segmentMode, String segment,
            BulkUploadReferenceValidator refValidator, BulkUploadNameValidator nameValidator, Set<String> batchRefs) throws SQLException {
        int rowNumber = rowData.has("row_number") ? rowData.get("row_number").getAsInt() : 0;
        // Get Ref.; normalize so blank/whitespace is treated as empty
        String refNumber = getString(rowData, "Ref.");
        if (refNumber != null) {
            refNumber = refNumber.trim();
            if (refNumber.isEmpty()) refNumber = null;
        }

        // Validate reference uniqueness only when user actually provided a ref
        if (refNumber != null) {
            try {
                refValidator.validateReferenceUnique("Glossary", refNumber, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
            }
            batchRefs.add(refNumber.toLowerCase());
        }

        // Auto-generate Ref if empty (batch-aware so multiple empty rows get distinct refs; generator excludes deleted rows)
        if (refNumber == null) {
            refNumber = ReferenceNumberGenerator.generateGlossaryRefNumber(conn, batchRefs);
            batchRefs.add(refNumber.trim().toLowerCase());
        }

        // Get name and validate uniqueness within segment
        // Validator normalizes the primary name column to "Name"
        String name = getString(rowData, "Name");
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

            // Validate name uniqueness within segment
            if (BulkUploadNameValidator.hasNameColumn("Glossary")) {
                try {
                    nameValidator.validateNameUniqueInSegment("Glossary", name, segmentId, conn);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                }
            }
        }

        String sql = "INSERT INTO glossary (" +
                "Parent_ID, Is_Public, Status, Lifecycle, Format_type, KDE, Security_Classification, Type, " +
                "Confidentiality_Rating, Integrity_Rating, Availability_Rating, " +
                "Name, Description, Ref_Number, Examples, Business_Logic, Format, LDM, " +
                "Created_Datetime, Last_Updated_Datetime, CreatedBy_ID, Last_updated_userID" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int idx = 1;
            // Parent_ID
            setNullableInt(ps, idx++, getInteger(rowData, "Parent_ID"));
            // Is_Public (viewing)
            setNullableInt(ps, idx++, coalesce(getInteger(rowData, "BUDG Viewing_ID"), getFirstViewingId()));
            // Status
            setNullableInt(ps, idx++, coalesce(getInteger(rowData, "BUDG Status_ID"), getFirstStatusId()));
            // Lifecycle
            setNullableInt(ps, idx++, getInteger(rowData, "Lifecycle_ID"));
            // Format_type
            setNullableInt(ps, idx++, getInteger(rowData, "Format Type_ID"));
            // KDE
            setNullableInt(ps, idx++, getInteger(rowData, "KDE_ID"));
            // Security_Classification
            setNullableInt(ps, idx++, getInteger(rowData, "Security Classification_ID"));
            // Type
            setNullableInt(ps, idx++, getInteger(rowData, "Type_ID"));
            // Confidentiality / Integrity / Availability ratings (if provided)
            setNullableInt(ps, idx++, getInteger(rowData, "Confidentiality_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Integrity_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Availability_ID"));

            // Name / description / reference and text fields
            ps.setString(idx++, name); // Name
            ps.setString(idx++, getString(rowData, "Definition")); // Description
            ps.setString(idx++, refNumber); // Ref_Number
            ps.setString(idx++, getString(rowData, "Examples")); // Examples
            ps.setString(idx++, getString(rowData, "Business Logic")); // Business_Logic
            ps.setString(idx++, getString(rowData, "Format Description")); // Format
            ps.setString(idx++, getString(rowData, "LDM Reference")); // LDM

            // Audit columns
            ps.setInt(idx++, userId); // CreatedBy_ID
            ps.setInt(idx++, userId); // Last_updated_userID

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int glossaryId = rs.getInt(1);

                    // Create audit records (best-effort; do not fail bulk job if this fails)
                    try {
                        String userName = getUserName(userId);
                        glossaryDAO.createGlossaryAuditRecords(conn, glossaryId, userName);
                    } catch (Exception e) {
                        logger.error("Error creating glossary audit records for ID {}: {}", glossaryId, e.getMessage());
                    }

                    // Link stakeholder (same transaction as insert — avoids lock wait on FK)
                    linkGlossaryStakeholder(conn, glossaryId, rowData, userId);

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
                            // Validate parent/child hierarchy before assigning segment
                            // Skip validation for super admin
                            boolean skipValidation = false;
                            try {
                                skipValidation = SegmentAccessService.isSuperAdmin(userId);
                            } catch (SQLException e) {
                                logger.warn("Error checking super admin status: {}", e.getMessage());
                            }

                            if (!skipValidation) {
                                SegmentValidationService validator = new SegmentValidationService();
                                Integer parentId = getInteger(rowData, "Parent_ID");
                                SegmentValidationService.ValidationResult result = validator.validateParentChildSegment(
                                        parentId, segmentIdToAssign.intValue(), "Glossary");

                                if (!result.isValid) {
                                    throw new RuntimeException("Segment validation failed: " + result.message);
                                }
                            }

                            validateSegmentAccess(userId, segmentIdToAssign, rowNumber);

                            ObjectSegmentService.assignObjectToSegment(
                                    conn,
                                    (long) glossaryId,
                                    "Glossary",
                                    segmentIdToAssign,
                                    userId);
                            logger.info("Assigned Glossary {} to Segment {}", glossaryId, segmentIdToAssign);
                        } catch (Exception segEx) {
                            // Segment assignment failure = row failure (BUDG behavior)
                            logger.error("Failed to assign Glossary {} to segment: {}", glossaryId, segEx.getMessage(),
                                    segEx);
                            throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                    segEx);
                        }
                    }

                    return glossaryId;
                }
            }
        }
        throw new SQLException("Failed to insert glossary");
    }

    private Integer getGlossaryIdById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ID FROM glossary WHERE ID = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("ID") : null;
            }
        }
    }

    private Integer getGlossaryIdByName(Connection conn, String name) throws SQLException {
        if (name == null || name.trim().isEmpty())
            return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ID FROM glossary WHERE LOWER(Name) = LOWER(?) AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1")) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("ID") : null;
            }
        }
    }

    private Integer getGlossaryIdByRef(Connection conn, String ref) throws SQLException {
        if (ref == null || ref.trim().isEmpty())
            return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ID FROM glossary WHERE Ref_Number = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1")) {
            ps.setString(1, ref.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("ID") : null;
            }
        }
    }

    private int updateGlossary(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int glossaryId = rowData.get("ID").getAsInt();
        int rowNumber = rowData.get("row_number").getAsInt();

        // Validate object can be edited (CR and lock checks)
        try {
            BulkUploadCRValidationHelper validationHelper = new BulkUploadCRValidationHelper();
            validationHelper.validateObjectForEdit("glossary", glossaryId, userId, conn);
        } catch (BulkUploadCRValidationHelper.ValidationException e) {
            throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
        }

        // Partial update: only set columns present in rowData so omitted fields are
        // preserved
        List<String> setClauses = new ArrayList<>();
        List<Object> bindValues = new ArrayList<>();

        if (hasNonEmptyString(rowData, "Name")) {
            setClauses.add("Name = ?");
            bindValues.add(getString(rowData, "Name").trim());
        }
        if (hasNonEmptyString(rowData, "Definition")) {
            setClauses.add("Description = ?");
            bindValues.add(getString(rowData, "Definition").trim());
        }
        if (hasNonEmptyString(rowData, "Ref.")) {
            setClauses.add("Ref_Number = ?");
            bindValues.add(getString(rowData, "Ref.").trim());
        }
        if (hasNonEmptyString(rowData, "Examples")) {
            setClauses.add("Examples = ?");
            bindValues.add(getString(rowData, "Examples").trim());
        }
        if (hasNonEmptyString(rowData, "Business Logic")) {
            setClauses.add("Business_Logic = ?");
            bindValues.add(getString(rowData, "Business Logic").trim());
        }
        if (hasNonEmptyString(rowData, "Format Description")) {
            setClauses.add("Format = ?");
            bindValues.add(getString(rowData, "Format Description").trim());
        }
        if (hasNonEmptyString(rowData, "LDM Reference")) {
            setClauses.add("LDM = ?");
            bindValues.add(getString(rowData, "LDM Reference").trim());
        }
        Integer parentId = getInteger(rowData, "Parent_ID");
        if (rowData.has("Parent_ID") && parentId != null) {
            setClauses.add("Parent_ID = ?");
            bindValues.add(parentId);
        }
        Integer viewingId = getInteger(rowData, "BUDG Viewing_ID");
        if (rowData.has("BUDG Viewing_ID") && viewingId != null) {
            setClauses.add("Is_Public = ?");
            bindValues.add(viewingId);
        }
        Integer statusId = getInteger(rowData, "BUDG Status_ID");
        if (rowData.has("BUDG Status_ID") && statusId != null) {
            setClauses.add("Status = ?");
            bindValues.add(statusId);
        }
        Integer lifecycleId = getInteger(rowData, "Lifecycle_ID");
        if (rowData.has("Lifecycle_ID") && lifecycleId != null) {
            setClauses.add("Lifecycle = ?");
            bindValues.add(lifecycleId);
        }
        Integer formatTypeId = getInteger(rowData, "Format Type_ID");
        if (rowData.has("Format Type_ID") && formatTypeId != null) {
            setClauses.add("Format_type = ?");
            bindValues.add(formatTypeId);
        }
        Integer kdeId = getInteger(rowData, "KDE_ID");
        if (rowData.has("KDE_ID") && kdeId != null) {
            setClauses.add("KDE = ?");
            bindValues.add(kdeId);
        }
        Integer securityId = getInteger(rowData, "Security Classification_ID");
        if (rowData.has("Security Classification_ID") && securityId != null) {
            setClauses.add("Security_Classification = ?");
            bindValues.add(securityId);
        }
        Integer typeId = getInteger(rowData, "Type_ID");
        if (rowData.has("Type_ID") && typeId != null) {
            setClauses.add("Type = ?");
            bindValues.add(typeId);
        }
        Integer confidentialityId = getInteger(rowData, "Confidentiality_ID");
        if (rowData.has("Confidentiality_ID") && confidentialityId != null) {
            setClauses.add("Confidentiality_Rating = ?");
            bindValues.add(confidentialityId);
        }
        Integer integrityId = getInteger(rowData, "Integrity_ID");
        if (rowData.has("Integrity_ID") && integrityId != null) {
            setClauses.add("Integrity_Rating = ?");
            bindValues.add(integrityId);
        }
        Integer availabilityId = getInteger(rowData, "Availability_ID");
        if (rowData.has("Availability_ID") && availabilityId != null) {
            setClauses.add("Availability_Rating = ?");
            bindValues.add(availabilityId);
        }

        setClauses.add("Last_updated_userID = ?");
        bindValues.add(userId);
        setClauses.add("Last_Updated_Datetime = NOW()");

        String sql = "UPDATE glossary SET " + String.join(", ", setClauses) + " WHERE ID = ?";
        bindValues.add(glossaryId);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object v : bindValues) {
                if (v == null) {
                    ps.setNull(idx++, Types.INTEGER);
                } else if (v instanceof Integer) {
                    ps.setInt(idx++, (Integer) v);
                } else {
                    ps.setString(idx++, (String) v);
                }
            }

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Update failed, glossary not found: " + glossaryId);
            }

            return glossaryId;
        }
    }

    private boolean hasNonEmptyString(JsonObject obj, String key) {
        String s = getString(obj, key);
        return s != null && !s.trim().isEmpty();
    }

    private void deleteGlossary(Connection conn, JsonObject rowData) throws SQLException {
        int glossaryId = rowData.get("ID").getAsInt();
        int rowNumber = rowData.get("row_number").getAsInt();

        // Validate object can be deleted (CR check)
        try {
            BulkUploadCRValidationHelper validationHelper = new BulkUploadCRValidationHelper();
            validationHelper.validateObjectForDeletion("glossary", glossaryId, conn);
        } catch (BulkUploadCRValidationHelper.ValidationException e) {
            throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
        }

        String sql = "UPDATE glossary SET Deleted_datetime = NOW() WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.executeUpdate();
        }
    }

    private void linkGlossaryStakeholder(Connection conn, int glossaryId, JsonObject rowData, int userId) {
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
                    roleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, grName.trim(), "Glossary");
                }
            }
            if (roleId == null) {
                roleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, "Glossary Owner", "Glossary");
            }
            if (roleId != null) {
                int rowNum = rowData.has("row_number") && !rowData.get("row_number").isJsonNull()
                        ? rowData.get("row_number").getAsInt() : 0;
                RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, stakeholderUserId, roleId, "Glossary", rowNum);
            }
            if (roleId != null) {
                Map<String, Object> stakeholderData = new HashMap<>();
                stakeholderData.put("userId", stakeholderUserId);
                stakeholderData.put("roleId", roleId);

                int objectXPeopleId = glossaryDAO.createObjectXPeople(conn, stakeholderData, userId);
                glossaryDAO.linkStakeholderToGlossary(conn, glossaryId, objectXPeopleId, userId);

                String userName = getUserName(stakeholderUserId);
                glossaryDAO.createStakeholderAuditRecords(conn, glossaryId, userName, userName, roleId);
            }
        } catch (Exception e) {
            logger.error("Error linking stakeholder to glossary {}: {}", glossaryId, e.getMessage(), e);
        }
    }

    // Stakeholder methods now handled by GlossaryDAO

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
                        "SELECT ID FROM people WHERE Email = ? AND Deleted_date IS NULL LIMIT 1")) {
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
                        "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'GL-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("GL-")) {
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
        return "GL-" + nextNumber;
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
            metadata.addProperty("entity", entity != null ? entity : "Glossary");
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
        sendNotification(userId, "Glossary", uploadOption, 0, 0, 0, 0, false, userFriendlyMessage, jobId);

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

        for (int i = 0; i < errors.size(); i++) {
            JsonObject error = errors.get(i).getAsJsonObject();
            int reportItemId = jobDAO.createJobReportItem(jobId, error.get("field").getAsString(),
                    "error", error.get("row").getAsInt());
            jobDAO.createJobReportItemMessage(reportItemId, error.get("error_code").getAsString(),
                    error.get("message").getAsString(), "error");
        }

        // Send failure notification
        String errorMessage = "Validation failed with " + errors.size() + " errors";
        sendNotification(userId, "Glossary", uploadOption, 0, 0, 0, errors.size(), false, errorMessage, jobId);

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
            n.setFacetType("glossary");
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
