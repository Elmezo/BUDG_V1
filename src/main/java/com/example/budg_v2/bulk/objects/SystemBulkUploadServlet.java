package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.SystemDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.common.BulkUploadFacetPermissionHelper;
import com.example.budg_v2.bulk.common.BulkUploadParentValidator;
import com.example.budg_v2.bulk.common.BulkUploadReferenceValidator;
import com.example.budg_v2.bulk.common.BulkUploadNameValidator;
import com.example.budg_v2.bulk.roles.util.RoleHandlerUtil;
import com.example.budg_v2.bulk.common.BulkUploadUtil;
import com.google.gson.Gson;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@WebServlet("/api/bulk/system/upload")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, // 2MB
        maxFileSize = 1024 * 1024 * 10, // 10MB
        maxRequestSize = 1024 * 1024 * 50 // 50MB
)
public class SystemBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SystemBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("system"); }

    private final JobDAO jobDAO = new JobDAO();
    private final SystemDAO systemDAO = new SystemDAO();

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

        logger.info("System bulk upload request received");

        int jobId = -1;
        String storagePath = null;
        String userIdStr = null;
        String uploadOption = null;

        try {
            // Step 1: Extract form data
            String uploadType = request.getParameter("uploadType");
            String entity = request.getParameter("entity");
            uploadOption = request.getParameter("uploadOption");
            String errorHandling = request.getParameter("errorHandling");
            userIdStr = request.getParameter("userId");
            String columnMappingsStr = request.getParameter("columnMappings");
            String segmentMode = request.getParameter("segmentMode");
            String segment = request.getParameter("segment");

            logger.info(
                    "Upload parameters - Type: {}, Entity: {}, Option: {}, ErrorHandling: {}, UserId: {}, SegmentMode: {}, Segment: {}",
                    uploadType, entity, uploadOption, errorHandling, userIdStr, segmentMode, segment);

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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "System";
            if (!BulkUploadFacetPermissionHelper.canBulkUploadForModule(userId, entityName)) {
                sendErrorResponse(response, "You do not have permission to bulk upload for this entity.", 403);
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
            String fileName = "system_" + timestamp + "_" + uuid + ".xlsx";

            File directory = new File(getBasePath());
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(getBasePath() + fileName);
            filePart.write(file.getAbsolutePath());
            storagePath = getBasePath() + fileName;

            logger.info("File saved to: {}", storagePath);

            // Step 4: Create Job record (entityName set above for permission check)
            String jobType = determineJobType(entityName, uploadOption);
            String referenceName = generateShortReferenceName();
            jobId = jobDAO.createJob(jobType, referenceName, 0, "Pending", userId);
            logger.info("Job created with ID: {} and reference: {}", jobId, referenceName);

            // Save metadata JSON
            saveMetadataJson(jobId, userId, entity, uploadOption, errorHandling, referenceName,
                    originalFileName, storagePath, fileName);

            // Step 5: Create Job Resource File record
            jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
            logger.info("Job resource file record created");

            // Step 6: Create Job Progress record
            jobDAO.createJobProgress(jobId, 0, "Validating", "File uploaded, starting validation");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Pending", 5, "File uploaded, starting validation");

            // Step 7: Call Python validation service
            JsonObject validationRequest = new JsonObject();
            File absoluteFile = new File(storagePath).getAbsoluteFile();
            validationRequest.addProperty("file_path", absoluteFile.getAbsolutePath());
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

            logger.info("Calling Python validation service: {}", PYTHON_SERVICE_URL);
            JsonObject validationResponse;
            try {
                validationResponse = HttpClientUtil.postJsonGetJson(PYTHON_SERVICE_URL, validationRequest, HttpClientUtil.BULK_VALIDATION_SOCKET_TIMEOUT_MS);
            } catch (IOException e) {
                handleValidationServiceError(response, jobId, userId, uploadOption, e);
                return;
            }

            String validationStatus = validationResponse.get("status").getAsString();
            logger.info("Validation status: {}", validationStatus);

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
            // Step 9: Validation succeeded, process the data
            JsonArray validatedData = validationResponse.getAsJsonArray("data");
            int totalRows = validatedData.size();

            jobDAO.updateJobItemsCount(jobId, totalRows);
            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.updateJobProgress(jobId, "Processing", "Processing " + totalRows + " rows");

            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                    "Validation complete. Processing " + totalRows + " rows...");

            logger.info("Processing {} validated rows", totalRows);

            // Step 10: Process data in separate thread
            final int finalJobId = jobId;
            final int finalUserId = userId;
            final String finalErrorHandling = errorHandling;
            final String finalReferenceName = referenceName;
            final String finalUploadOption = uploadOption;
            final String finalSegmentMode = segmentMode;
            final String finalSegment = segment;

            Thread processingThread = new Thread(() -> {
                processSystemData(finalJobId, validatedData, finalUserId, finalErrorHandling, finalReferenceName,
                        finalUploadOption, finalSegmentMode, finalSegment);
            });
            processingThread.setDaemon(false);
            processingThread.start();

            // Step 11: Return success response
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("message", "File uploaded successfully. Processing " + totalRows + " rows...");

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(successResponse));

        } catch (Exception e) {
            logger.error("Unexpected error during upload", e);
            if (jobId != -1) {
                try {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Unexpected error: " + e.getMessage());
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Unexpected error: " + e.getMessage(), 0, 0, 0, 0);

                    // Send failure notification if we have userId
                    if (userIdStr != null && uploadOption != null) {
                        try {
                            int userId = Integer.parseInt(userIdStr);
                            sendNotification(userId, "System", uploadOption, 0, 0, 0, 0,
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
                    failureMsg = errorMessage.length() > 200 ? errorMessage.substring(0, 197) + "..." : errorMessage;
                }
                n.setMessage(failureMsg);
                n.setEventType("UPLOAD_FAILED");
            }

            n.setChannel("ui");
            n.setFacetType("system");
            n.setObjectId(jobId);
            n.setRead(false);

            dao.create(n);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }

    private void processSystemData(int jobId, JsonArray validatedData, int userId,
            String errorHandling, String referenceName, String uploadOption, String segmentMode, String segment) {
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
                        int systemId = insertSystem(conn, rowData, userId, refValidator, nameValidator, segmentMode,
                                segment);

                        // Assign object to segment (for INSERT operations when segmentation is enabled)
                        Long segmentIdToAssign = null;
                        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                            segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
                            logger.debug("Segment ID determined for System {} (INSERT): mode={}, segmentId={}",
                                    systemId, segmentMode, segmentIdToAssign);
                        } else {
                            // Check if Segment column exists in Excel even when segmentMode is null
                            String segmentName = getString(rowData, "Segment");
                            if (segmentName != null && !segmentName.trim().isEmpty()) {
                                segmentIdToAssign = getSegmentIdByName(segmentName.trim());
                                logger.debug(
                                        "Segment ID determined from Excel column for System {} (INSERT, null mode): {} -> {}",
                                        systemId, segmentName, segmentIdToAssign);
                            } else {
                                logger.debug(
                                        "No segment ID determined for System {} (INSERT, null mode, no Segment column)",
                                        systemId);
                            }
                        }

                        if (segmentIdToAssign != null && systemId > 0) {
                            logger.info("Assigning System {} to Segment {} (INSERT)", systemId, segmentIdToAssign);
                            try {
                                // Validate parent/child hierarchy before assigning segment
                                // Skip validation for super admin
                                boolean skipValidation = false;
                                try {
                                    skipValidation = com.example.budg_v2.service.SegmentAccessService
                                            .isSuperAdmin(userId);
                                } catch (SQLException e) {
                                    logger.warn("Error checking super admin status: {}", e.getMessage());
                                }

                                if (!skipValidation) {
                                    SegmentValidationService validator = new SegmentValidationService();
                                    Integer parentId = getInteger(rowData, "Parent_ID");
                                    SegmentValidationService.ValidationResult result = validator
                                            .validateParentChildSegment(
                                                    parentId, segmentIdToAssign.intValue(), "System");

                                    if (!result.isValid) {
                                        throw new RuntimeException("Segment validation failed: " + result.message);
                                    }
                                }

                                validateSegmentAccess(userId, segmentIdToAssign, rowNumber);

                                ObjectSegmentService.assignObjectToSegment(
                                        conn,
                                        (long) systemId,
                                        "System",
                                        segmentIdToAssign,
                                        userId);
                                logger.info("Assigned System {} to Segment {}", systemId, segmentIdToAssign);
                            } catch (Exception segEx) {
                                // Segment assignment failure = row failure (BUDG behavior)
                                logger.error("Failed to assign System {} to segment: {}", systemId, segEx.getMessage(),
                                        segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    systemId,
                                    "System",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for System {}: {}", systemId, cfEx.getMessage(),
                                    cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        insertedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, "System", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "System created successfully", "info");

                    } else if ("UPDATE".equals(operation)) {
                        List<String> identityCols = Arrays.asList("ID", "Short Name");
                        String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols,
                                "System", rowNumber);
                        if (identityErr != null) {
                            throw new IllegalArgumentException(identityErr);
                        }
                        Integer idById = null;
                        Integer idByName = null;
                        Integer idVal = BulkUploadUtil.getInteger(rowData, "ID");
                        if (idVal != null) {
                            idById = getSystemIdById(conn, idVal);
                        }
                        String nameVal = BulkUploadUtil.getString(rowData, "Short Name");
                        if (nameVal != null && !nameVal.trim().isEmpty()) {
                            idByName = getSystemIdByName(conn, nameVal.trim());
                        }
                        int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                        Integer systemId = coalesce(idById, idByName);
                        if (systemId == null) {
                            throw new IllegalArgumentException("Row " + rowNumber
                                    + ": No system found for the provided identity (ID or Short Name).");
                        }
                        if (filledIdentity >= 2) {
                            Set<Integer> ids = new HashSet<>();
                            if (idById != null)
                                ids.add(idById);
                            if (idByName != null)
                                ids.add(idByName);
                            if (ids.size() > 1) {
                                throw new IllegalArgumentException(
                                        "Row " + rowNumber + ": ID and Short Name refer to different systems.");
                            }
                        }
                        // When parent is specified, validate circular hierarchy and segment before
                        // update
                        Integer parentId = getInteger(rowData, "Parent_ID");
                        if (parentId != null && parentId > 0) {
                            try {
                                if (BulkUploadParentValidator.wouldCreateParentCycle(conn, systemId, parentId,
                                        "System")) {
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
                            Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) systemId, "System");
                            int effectiveChildSegmentId = (newSegmentIdForParent != null)
                                    ? newSegmentIdForParent.intValue()
                                    : (currentSegmentId != null ? currentSegmentId.intValue() : 1);
                            SegmentValidationService parentValidator = new SegmentValidationService();
                            SegmentValidationService.ValidationResult parentResult = parentValidator
                                    .validateParentChildSegment(
                                            parentId, effectiveChildSegmentId, "System");
                            if (!parentResult.isValid) {
                                throw new RuntimeException("Parent segment validation failed: " + parentResult.message);
                            }
                        }
                        rowData.add("ID", new com.google.gson.JsonPrimitive(systemId));
                        updateSystem(conn, rowData, userId);

                        // Handle segment assignment for UPDATE operations
                        Long newSegmentId = null;
                        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                            newSegmentId = determineSegmentId(segmentMode, segment, rowData);
                            logger.debug("Segment ID determined for System {} (UPDATE): mode={}, segmentId={}",
                                    systemId, segmentMode, newSegmentId);
                        } else {
                            // Check if Segment column exists in Excel even when segmentMode is null
                            String segmentName = getString(rowData, "Segment");
                            if (segmentName != null && !segmentName.trim().isEmpty()) {
                                newSegmentId = getSegmentIdByName(segmentName.trim());
                                logger.debug(
                                        "Segment ID determined from Excel column for System {} (UPDATE, null mode): {} -> {}",
                                        systemId, segmentName, newSegmentId);
                            } else {
                                logger.debug(
                                        "No segment ID determined for System {} (UPDATE, null mode, no Segment column)",
                                        systemId);
                            }
                        }

                        if (newSegmentId != null) {
                            logger.info("Assigning System {} to Segment {} (UPDATE)", systemId, newSegmentId);
                            try {
                                // Get current segment
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) systemId,
                                        "System");

                                // Only assign if segment is changing
                                if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                    // Validate segment change
                                    SegmentValidationService validator = new SegmentValidationService();
                                    SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            systemId, newSegmentId.intValue(), "System", parentId);

                                    if (!result.isValid && !result.canProceedWithWarning) {
                                        throw new RuntimeException("Segment validation failed: " + result.message);
                                    }

                                    // rowNumber is already defined at the start of updateSystem method
                                    validateSegmentAccess(userId, newSegmentId, rowNumber);

                                    // Assign new segment
                                    ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) systemId,
                                            "System",
                                            newSegmentId,
                                            userId);
                                    logger.info("Assigned System {} to Segment {} (updated)", systemId, newSegmentId);
                                }
                            } catch (Exception segEx) {
                                logger.error("Failed to assign System {} to segment: {}", systemId, segEx.getMessage(),
                                        segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    systemId,
                                    "System",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for System {}: {}", systemId, cfEx.getMessage(),
                                    cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        updatedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, "System", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "System updated successfully", "info");

                    } else if ("DELETE".equals(operation)) {
                        int systemId = rowData.get("ID").getAsInt();
                        BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                        BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "system", systemId);
                        if (!validationResult.canDelete()) {
                            String errorMsg = String.join("; ", validationResult.getErrors());
                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "System", "failed", rowNumber);
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
                                    logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                                }
                            }
                        } else if (validationResult.canDelete() && validationResult.hasWarnings() && cancelOnWarning) {
                            String warningMsg = String.join("; ", validationResult.getWarnings());
                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "System", "failed", rowNumber);
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
                            deleteSystem(conn, rowData);
                            deletedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, "System", "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    "System deleted successfully", "info");
                        }
                    }

                    if (!cancelOnWarning) {
                        conn.commit();
                        logger.debug("Committed row {} successfully", rowNumber);
                    }

                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    int reportItemId = jobDAO.createJobReportItem(jobId, "System", "failed", rowNumber);
                    jobDAO.createJobReportItemMessage(reportItemId, "ERROR",
                            "Failed to process: " + e.getMessage(), "error");

                    if (cancelOnWarning) {
                        conn.rollback();
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed",
                                "Processing failed at row " + rowNumber + ": " + e.getMessage());
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                "Processing failed at row " + rowNumber, insertedCount, updatedCount, deletedCount,
                                failedCount);
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
                        String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                                insertedCount, updatedCount, deletedCount, failedCount));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Processing failed: all rows failed", insertedCount, updatedCount, deletedCount, failedCount);

                logger.info("Bulk upload job {} failed: {} inserted, {} updated, {} deleted, {} failed",
                        jobId, insertedCount, updatedCount, deletedCount, failedCount);

                sendNotification(userId, "System", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, false, "All rows failed during processing", jobId);
            } else if (isPartiallyCompleted) {
                jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount); // Only successful operations
                jobDAO.updateJobProgress(jobId, "Partially Completed",
                        String.format("Processing complete: %d inserted, %d updated, %d deleted, %d failed",
                                insertedCount, updatedCount, deletedCount, failedCount));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                        "Processing complete with some failures", insertedCount, updatedCount, deletedCount,
                        failedCount);

                logger.info("Bulk upload job {} partially completed: {} inserted, {} updated, {} deleted, {} failed",
                        jobId, insertedCount, updatedCount, deletedCount, failedCount);

                // Send notification for partial completion
                sendNotification(userId, "System", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, true, null, jobId);
            } else {
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount);
                jobDAO.updateJobProgress(jobId, "Completed",
                        String.format("Processing complete: %d inserted, %d updated, %d deleted, %d failed",
                                insertedCount, updatedCount, deletedCount, failedCount));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                        "Processing complete", insertedCount, updatedCount, deletedCount, failedCount);

                logger.info("Bulk upload job {} completed: {} inserted, {} updated, {} deleted, {} failed",
                        jobId, insertedCount, updatedCount, deletedCount, failedCount);

                // Send success notification
                sendNotification(userId, "System", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, true, null, jobId);
            }

        } catch (Exception e) {
            logger.error("Error processing system bulk upload job {}", jobId, e);
            try {
                if (conn != null)
                    conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Processing error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Processing failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);

                // Send failure notification
                sendNotification(userId, "System", uploadOption, insertedCount, updatedCount,
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

    private int insertSystem(Connection conn, JsonObject rowData, int userId, BulkUploadReferenceValidator refValidator,
            BulkUploadNameValidator nameValidator, String segmentMode, String segment) throws SQLException {
        int rowNumber = rowData.has("row_number") ? rowData.get("row_number").getAsInt() : 0;
        // Get Asset ID before validation
        String assetId = getString(rowData, "Asset ID");

        // Validate AssetID uniqueness if provided (System uses AssetID as reference)
        if (assetId != null && !assetId.trim().isEmpty()) {
            try {
                refValidator.validateReferenceUnique("System", assetId, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
            }
        }

        // Get name and validate uniqueness within segment
        String name = getString(rowData, "Short Name");
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
            if (BulkUploadNameValidator.hasNameColumn("System")) {
                try {
                    nameValidator.validateNameUniqueInSegment("System", name, segmentId, conn);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                }
            }
        }

        String sql = "INSERT INTO system (Name, Long_Name, AssetID, External, Description, URL, DQ_Automation, " +
                "parent_id, is_Public, status, Lifecycle, Type, Classification, Confidentiality_Rating, " +
                "Integrity_Rating, Availability_Rating, CreatedBy_ID, Last_updated_UserID, " +
                "Created_Datetime, Last_Updated_Datetime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            int idx = 1;

            // Short Name (required)
            ps.setString(idx++, name);

            // Long Name
            ps.setString(idx++, getString(rowData, "Long Name"));

            // Asset ID
            ps.setString(idx++, assetId);

            // External (required, defaults to 0 if missing)
            ps.setBoolean(idx++, getBoolean(rowData, "External", false));

            // Description (required)
            ps.setString(idx++, getString(rowData, "Description"));

            // URL
            ps.setString(idx++, getString(rowData, "URL"));

            // DQAutomation
            setNullableBoolean(ps, idx++, getBooleanOrNull(rowData, "DQAutomation"));

            // Parent ID
            setNullableInt(ps, idx++, getInteger(rowData, "Parent_ID"));

            // BUDG Viewing (use first if not provided)
            Integer viewingId = coalesce(getInteger(rowData, "BUDG Viewing_ID"), getFirstViewingId());
            setNullableInt(ps, idx++, viewingId);

            // BUDG Status (use first if not provided)
            Integer statusId = coalesce(getInteger(rowData, "BUDG Status_ID"), getFirstStatusId());
            setNullableInt(ps, idx++, statusId);

            // Lifecycle (required, use first if not provided)
            Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"),
                    getFirstLookupId("system_lifecycle"));
            setNullableInt(ps, idx++, lifecycleId);

            // Type (required, use first if not provided)
            Integer typeId = coalesce(getInteger(rowData, "Type_ID"),
                    getFirstLookupId("system_type"));
            setNullableInt(ps, idx++, typeId);

            // Classification
            setNullableInt(ps, idx++, getInteger(rowData, "Classification_ID"));

            // CIA Ratings
            setNullableInt(ps, idx++, getInteger(rowData, "Confidentiality_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Integrity_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Availability_ID"));

            // User IDs
            ps.setInt(idx++, userId);
            ps.setInt(idx++, userId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Insert failed, no rows affected");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int systemId = rs.getInt(1);

                    // Audit on same connection as insert (avoids lock wait / invisible row)
                    try {
                        String userName = getUserName(userId);
                        systemDAO.createSystemAuditRecords(systemId, userName, conn);
                        systemDAO.createSystemAuditRecord(systemId, conn);
                    } catch (Exception e) {
                        logger.error("Error creating system audit for ID {}: {}", systemId, e.getMessage());
                    }

                    linkSystemStakeholder(conn, systemId, rowData, userId);

                    logger.info("System created with ID: {}", systemId);
                    return systemId;
                }
            }
        }
        throw new SQLException("Failed to insert system, no ID generated");
    }

    private Integer getSystemIdById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM system WHERE id = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    private Integer getSystemIdByName(Connection conn, String name) throws SQLException {
        if (name == null || name.trim().isEmpty())
            return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM system WHERE LOWER(Name) = LOWER(?) AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1")) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    private void updateSystem(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int systemId = rowData.get("ID").getAsInt();
        int rowNumber = rowData.get("row_number").getAsInt();

        // Validate object can be edited (CR and lock checks)
        try {
            BulkUploadCRValidationHelper validationHelper = new BulkUploadCRValidationHelper();
            validationHelper.validateObjectForEdit("system", systemId, userId, conn);
        } catch (BulkUploadCRValidationHelper.ValidationException e) {
            throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
        }

        // Load existing system row for partial update (keep existing when row value is
        // empty/missing)
        String existingName = null;
        String existingLongName = null;
        boolean existingExternal = false;
        String existingDescription = null;
        String existingAssetId = null;
        String existingUrl = null;
        String loadSql = "SELECT Name, Long_Name, External, Description, AssetID, URL FROM system WHERE id = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1";
        try (PreparedStatement loadPs = conn.prepareStatement(loadSql)) {
            loadPs.setInt(1, systemId);
            try (ResultSet rs = loadPs.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("System not found with id: " + systemId);
                }
                existingName = rs.getString("Name");
                existingLongName = rs.getString("Long_Name");
                existingExternal = rs.getBoolean("External");
                existingDescription = rs.getString("Description");
                existingAssetId = rs.getString("AssetID");
                existingUrl = rs.getString("URL");
            }
        }

        String shortNameFromRow = getString(rowData, "Short Name");
        String longNameFromRow = getString(rowData, "Long Name");
        boolean externalFromRow = rowData.has("External") && !rowData.get("External").isJsonNull()
                ? rowData.get("External").getAsBoolean()
                : existingExternal;

        String sql = "UPDATE system SET Name = ?, Long_Name = ?, AssetID = ?, External = ?, Description = ?, " +
                "URL = ?, DQ_Automation = ?, parent_id = ?, is_Public = ?, status = ?, Lifecycle = ?, " +
                "Type = ?, Classification = ?, Confidentiality_Rating = ?, Integrity_Rating = ?, " +
                "Availability_Rating = ?, Last_updated_UserID = ?, Last_Updated_Datetime = NOW() " +
                "WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;

            ps.setString(idx++,
                    (shortNameFromRow != null && !shortNameFromRow.isBlank()) ? shortNameFromRow : existingName);
            ps.setString(idx++,
                    (longNameFromRow != null && !longNameFromRow.isBlank()) ? longNameFromRow : existingLongName);
            ps.setString(idx++, coalesceString(getString(rowData, "Asset ID"), existingAssetId));
            ps.setBoolean(idx++, externalFromRow);
            ps.setString(idx++, coalesceString(getString(rowData, "Description"), existingDescription));
            ps.setString(idx++, coalesceString(getString(rowData, "URL"), existingUrl));
            setNullableBoolean(ps, idx++, getBooleanOrNull(rowData, "DQAutomation"));
            setNullableInt(ps, idx++, getInteger(rowData, "Parent_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "BUDG Viewing_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "BUDG Status_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Lifecycle_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Type_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Classification_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Confidentiality_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Integrity_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Availability_ID"));
            ps.setInt(idx++, userId);
            ps.setInt(idx++, systemId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Update failed, system not found: " + systemId);
            }

            // Create update audit snapshot
            systemDAO.createSystemUpdateAuditSnapshot(systemId);

            logger.info("System updated with ID: {}", systemId);
        }
    }

    private static String coalesceString(String first, String fallback) {
        return (first != null && !first.isBlank()) ? first : fallback;
    }

    private void deleteSystem(Connection conn, JsonObject rowData) throws SQLException {
        int systemId = rowData.get("ID").getAsInt();
        int rowNumber = rowData.get("row_number").getAsInt();

        // Validate object can be deleted (CR check)
        try {
            BulkUploadCRValidationHelper validationHelper = new BulkUploadCRValidationHelper();
            validationHelper.validateObjectForDeletion("system", systemId, conn);
        } catch (BulkUploadCRValidationHelper.ValidationException e) {
            throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
        }

        String sql = "UPDATE system SET Deleted_datetime = NOW() WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Delete failed, system not found: " + systemId);
            }

            logger.info("System soft deleted with ID: {}", systemId);
        }
    }

    private void linkSystemStakeholder(Connection conn, int systemId, JsonObject rowData, int userId) {
        try {
            Integer stakeholderUserId = userId;
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

            Integer governanceRoleId = getInteger(rowData, "Governance Role_ID");
            if (governanceRoleId == null) {
                String grName = getString(rowData, "Governance Role");
                if (grName != null && !grName.trim().isEmpty()) {
                    governanceRoleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, grName.trim(), "System");
                }
            }
            if (governanceRoleId == null) {
                governanceRoleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, "System Owner", "System");
            }
            if (governanceRoleId == null) {
                governanceRoleId = getDefaultSystemOwnerRole();
            }
            if (governanceRoleId != null) {
                int rowNum = rowData.has("row_number") && !rowData.get("row_number").isJsonNull()
                        ? rowData.get("row_number").getAsInt() : 0;
                RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, stakeholderUserId, governanceRoleId, "System", rowNum);
            }
            if (governanceRoleId != null) {
                Map<String, Object> stakeholderData = new HashMap<>();
                stakeholderData.put("userId", stakeholderUserId);
                stakeholderData.put("roleId", governanceRoleId);

                int objectXPeopleId = systemDAO.createObjectXPeople(conn, stakeholderData, userId);
                systemDAO.linkStakeholderToSystem(conn, systemId, objectXPeopleId);

                String userName = getUserName(stakeholderUserId);
                systemDAO.createStakeholderAuditRecords(systemId, userName, userName, governanceRoleId, conn);
            }
        } catch (Exception e) {
            logger.error("Error linking stakeholder to system {}: {}", systemId, e.getMessage(), e);
        }
    }

    // Stakeholder methods now handled by SystemDAO

    // Helper methods for data extraction
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

    private boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsBoolean();
        }
        return defaultValue;
    }

    private Boolean getBooleanOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsBoolean();
        }
        return null;
    }

    private Integer coalesce(Integer... values) {
        for (Integer value : values) {
            if (value != null)
                return value;
        }
        return null;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, java.sql.Types.INTEGER);
        }
    }

    private void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value != null) {
            ps.setBoolean(index, value);
        } else {
            ps.setNull(index, java.sql.Types.BOOLEAN);
        }
    }

    private Integer getFirstLookupId(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT id FROM " + tableName + " ORDER BY id ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first lookup ID from table {}: {}", tableName, e.getMessage());
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

    private Integer getDefaultSystemOwnerRole() {
        return getRoleIdByName("System Owner");
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

    private String generateShortReferenceName() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'SYS-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("SYS-")) {
                        try {
                            String numberPart = refName.substring(4);
                            int number = Integer.parseInt(numberPart);
                            if (number > maxNumber) {
                                maxNumber = number;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore malformed reference names
                        }
                    }
                }
                if (maxNumber > 0) {
                    nextNumber = maxNumber + 1;
                }
            }
        } catch (SQLException e) {
            logger.warn("Error generating short reference name, using fallback number: {}", e.getMessage());
        }
        return "SYS-" + nextNumber;
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
            metadata.addProperty("entity", entity != null ? entity : "System");
            metadata.addProperty("upload_option", uploadOption);
            metadata.addProperty("error_handling", errorHandling);
            metadata.addProperty("uploaded_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
            metadata.addProperty("reference_name", referenceName);
            metadata.addProperty("original_file_name", originalFileName);
            metadata.addProperty("storage_path", storagePath);

            Files.writeString(metadataFile.toPath(), gson.toJson(metadata));
            logger.info("Metadata JSON saved to: {}", metadataFile.getAbsolutePath());
        } catch (Exception e) {
            logger.warn("Failed to save metadata JSON file: {}", e.getMessage());
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
                logger.info("Column mappings applied: {} mappings", pythonMappings.size());
            }
        } catch (Exception e) {
            logger.warn("Failed to parse column mappings: {}", e.getMessage(), e);
        }
    }

    private void handleValidationServiceError(HttpServletResponse response, int jobId, int userId,
            String uploadOption, IOException e)
            throws SQLException, IOException {
        logger.error("Python validation service error: {}", e.getMessage(), e);

        String errorMessage = e.getMessage();
        String userFriendlyMessage;
        if (errorMessage != null && errorMessage.contains("Connection refused")) {
            userFriendlyMessage = "Validation service is not available. Please ensure the Python validation service is running on port 8000.";
        } else if (errorMessage != null && errorMessage.contains("timeout")) {
            userFriendlyMessage = "Validation service timeout. The service may be overloaded or unavailable.";
        } else {
            userFriendlyMessage = "Validation service error: "
                    + (errorMessage != null ? errorMessage : "Unknown error");
        }

        jobDAO.updateJobStatus(jobId, "Failed", true);
        jobDAO.updateJobProgress(jobId, "Failed", userFriendlyMessage);

        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, userFriendlyMessage, 0, 0, 0, 0);

        // Send failure notification
        sendNotification(userId, "System", uploadOption, 0, 0, 0, 0, false, userFriendlyMessage, jobId);

        sendErrorResponse(response, userFriendlyMessage, 503);
    }

    private void handleValidationErrors(HttpServletResponse response, int jobId, int userId,
            String uploadOption, String referenceName,
            JsonObject validationResponse) throws SQLException, IOException {
        JsonArray errors = validationResponse.getAsJsonArray("errors");
        logger.warn("Validation failed with {} errors", errors.size());

        jobDAO.updateJobStatus(jobId, "Failed", true);
        jobDAO.updateJobProgress(jobId, "Failed", "Validation failed");

        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                "Validation failed with " + errors.size() + " errors", 0, 0, 0, errors.size());

        // Send failure notification
        String errorMessage = "Validation failed with " + errors.size() + " errors";
        sendNotification(userId, "System", uploadOption, 0, 0, 0, errors.size(), false, errorMessage, jobId);

        for (int i = 0; i < errors.size(); i++) {
            JsonObject error = errors.get(i).getAsJsonObject();
            int reportItemId = jobDAO.createJobReportItem(
                    jobId,
                    error.get("field").getAsString(),
                    "error",
                    error.get("row").getAsInt());

            jobDAO.createJobReportItemMessage(
                    reportItemId,
                    error.get("error_code").getAsString(),
                    error.get("message").getAsString(),
                    "error");
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
