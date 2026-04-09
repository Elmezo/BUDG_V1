package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.RegulatoryThemeDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.model.RegulatoryTheme;
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
import com.example.budg_v2.bulk.common.BulkUploadUtil;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.List;
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
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static com.example.budg_v2.bulk.common.BulkUploadUtil.getString;

@WebServlet("/api/bulk/regulatorytheme/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class RegulatoryThemeBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(RegulatoryThemeBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("regulatorytheme"); }
    
    private final JobDAO jobDAO = new JobDAO();
    private final RegulatoryThemeDAO regulatoryThemeDAO = new RegulatoryThemeDAO();

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

        logger.info("Bulk upload request received");

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

            logger.info("Upload parameters - Type: {}, Entity: {}, Option: {}, ErrorHandling: {}, UserId: {}, SegmentMode: {}, Segment: {}", 
                       uploadType, entity, uploadOption, errorHandling, userIdStr, segmentMode, segment);

            // Validate parameters
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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Regulatory Theme";
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
            String fileName = "regulatorytheme_" + timestamp + "_" + uuid + ".xlsx";
            
            // Ensure directory exists
            File directory = new File(getBasePath());
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            File file = new File(getBasePath() + fileName);
            filePart.write(file.getAbsolutePath());
            storagePath = getBasePath() + fileName;
            
            logger.info("File saved to: {}", storagePath);

            // Step 4: Create Job record
            // Use entity from request parameter, fallback to "Regulatory Theme" if not provided (entityName set above)
            String jobType = determineJobType(entityName, uploadOption);
            String referenceName = generateShortReferenceName();
            jobId = jobDAO.createJob(jobType, referenceName, 0, "Pending", userId);
            logger.info("Job created with ID: {} and reference: {}", jobId, referenceName);

            // Step 4.5: Save metadata JSON file
            try {
                String metadataFileName = fileName.replace(".xlsx", ".json");
                File metadataFile = new File(getBasePath() + metadataFileName);
                
                JsonObject metadata = new JsonObject();
                metadata.addProperty("job_id", jobId);
                metadata.addProperty("user_id", userId);
                metadata.addProperty("entity", entity != null ? entity : "Regulatory Theme");
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
                // Don't fail the upload if metadata save fails
            }

            // Step 5: Create Job Resource File record
            jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
            logger.info("Job resource file record created");

            // Step 6: Create Job Progress record
            jobDAO.createJobProgress(jobId, 0, "Validating", "File uploaded, starting validation");
            logger.info("Job progress record created");

            // Broadcast initial status via WebSocket
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Pending", 5, "File uploaded, starting validation");

            // Step 7: Call Python validation service
            JsonObject validationRequest = new JsonObject();
            // Convert relative path to absolute path for Python service
            File absoluteFile = new File(storagePath).getAbsoluteFile();
            validationRequest.addProperty("file_path", absoluteFile.getAbsolutePath());
            validationRequest.addProperty("upload_option", uploadOption);
            validationRequest.addProperty("entity", entity != null ? entity : "Regulatory Theme");
            validationRequest.addProperty("user_id", userId);

            // Add segment parameters if provided (for INSERT operations only)
            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                validationRequest.addProperty("segment_mode", segmentMode);
                if (segment != null && !segment.trim().isEmpty()) {
                    validationRequest.addProperty("segment", segment);
                }
            }
            
            // Add column mappings if provided
            // Frontend sends: {fieldName: excelColumnName} e.g., {"Regulatory Theme Long Name": "اسم الموضوع التنظيمي"}
            // Python expects: {excelColumnName: fieldName} e.g., {"اسم الموضوع التنظيمي": "Regulatory Theme Long Name"}
            if (columnMappingsStr != null && !columnMappingsStr.trim().isEmpty()) {
                try {
                    JsonObject frontendMappings = gson.fromJson(columnMappingsStr, JsonObject.class);
                    JsonObject pythonMappings = new JsonObject();
                    
                    // Reverse the mapping: fieldName->excelColumn becomes excelColumn->fieldName
                    for (String fieldName : frontendMappings.keySet()) {
                        com.google.gson.JsonElement element = frontendMappings.get(fieldName);
                        // Skip null or empty values
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
                        logger.debug("Column mappings details: {}", gson.toJson(pythonMappings));
                    } else {
                        logger.info("No valid column mappings found after parsing");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse column mappings: {}", e.getMessage(), e);
                    // Don't fail the upload if mapping parsing fails, just continue without mappings
                }
            }

            logger.info("Calling Python validation service: {}", PYTHON_SERVICE_URL);
            logger.info("Validation request - file_path: {}, upload_option: {}", absoluteFile.getAbsolutePath(), uploadOption);
            JsonObject validationResponse;
            try {
                validationResponse = HttpClientUtil.postJsonGetJson(PYTHON_SERVICE_URL, validationRequest);
            } catch (IOException e) {
                logger.error("Python validation service error: {}", e.getMessage(), e);
                
                // Check if it's a connection error
                String errorMessage = e.getMessage();
                String userFriendlyMessage;
                if (errorMessage != null && errorMessage.contains("Connection refused")) {
                    userFriendlyMessage = "Validation service is not available. Please ensure the Python validation service is running on port 8000.";
                } else if (errorMessage != null && errorMessage.contains("timeout")) {
                    userFriendlyMessage = "Validation service timeout. The service may be overloaded or unavailable.";
                } else {
                    userFriendlyMessage = "Validation service error: " + (errorMessage != null ? errorMessage : "Unknown error");
                }
                
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", userFriendlyMessage);
                
                // Broadcast failure via WebSocket
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, userFriendlyMessage, 0, 0, 0, 0);
                
                sendErrorResponse(response, userFriendlyMessage, 503); // 503 Service Unavailable
                return;
            }

            String validationStatus = validationResponse.get("status").getAsString();
            logger.info("Validation status: {}", validationStatus);

            // Broadcast validation progress
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Validating", 20, "Validation in progress...");

            // Determine cancelOnWarning setting
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
            logger.info("Cancel on Warning setting: {}", cancelOnWarning);

            // Count of validation errors when we continue with invalid status (so GUI failed count matches report)
            int validationErrorCount = 0;
            int validationFailedRows = 0;

            // Step 8: Handle validation response
            if ("error".equals(validationStatus)) {
                // Critical error - always fail
                JsonArray errors = validationResponse.getAsJsonArray("errors");
                logger.warn("Validation error status with {} errors", errors.size());

                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Validation error occurred");

                // Broadcast failure via WebSocket
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, 
                    "Validation error occurred", 0, 0, 0, errors != null ? errors.size() : 0);

                // Store validation errors in Job_Report_Item
                if (errors != null) {
                    for (int i = 0; i < errors.size(); i++) {
                        JsonObject error = errors.get(i).getAsJsonObject();
                        int reportItemId = jobDAO.createJobReportItem(
                            jobId,
                            error.get("field").getAsString(),
                            "error",
                            error.get("row").getAsInt()
                        );
                        
                        jobDAO.createJobReportItemMessage(
                            reportItemId,
                            error.get("error_code").getAsString(),
                            error.get("message").getAsString(),
                            "error"
                        );
                    }
                }

                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("status", "failed");
                errorResponse.addProperty("job_id", jobId);
                errorResponse.addProperty("reference_name", referenceName);
                errorResponse.addProperty("message", validationResponse.get("message").getAsString());
                errorResponse.addProperty("inserted", 0);
                errorResponse.addProperty("updated", 0);
                errorResponse.addProperty("deleted", 0);
                errorResponse.addProperty("failed", errors != null ? errors.size() : 0);
                errorResponse.addProperty("validation_error_count", errors != null ? errors.size() : 0);
                errorResponse.addProperty("validation_failed_rows", countDistinctValidationErrorRows(validationResponse));
                if (errors != null) {
                    errorResponse.add("errors", errors);
                }
                
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(gson.toJson(errorResponse));
                return;
            } else if ("invalid".equals(validationStatus)) {
                // Validation errors found - check cancelOnWarning
                JsonArray errors = validationResponse.getAsJsonArray("errors");
                JsonArray validatedData = validationResponse.getAsJsonArray("data");
                logger.warn("Validation invalid status with {} errors, {} valid rows", 
                    errors != null ? errors.size() : 0, validatedData != null ? validatedData.size() : 0);

                // Store validation errors in Job_Report_Item
                if (errors != null) {
                    for (int i = 0; i < errors.size(); i++) {
                        JsonObject error = errors.get(i).getAsJsonObject();
                        int reportItemId = jobDAO.createJobReportItem(
                            jobId,
                            error.get("field").getAsString(),
                            "error",
                            error.get("row").getAsInt()
                        );
                        
                        jobDAO.createJobReportItemMessage(
                            reportItemId,
                            error.get("error_code").getAsString(),
                            error.get("message").getAsString(),
                            "error"
                        );
                    }
                }

                if (cancelOnWarning) {
                    // Stop processing if cancelOnWarning is enabled
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Validation failed - Cancel on Warning enabled");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Validation failed with " + (errors != null ? errors.size() : 0) + " errors (Cancel on Warning enabled)", 0, 0, 0, errors != null ? errors.size() : 0);

                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("status", "failed");
                    errorResponse.addProperty("job_id", jobId);
                    errorResponse.addProperty("reference_name", referenceName);
                    errorResponse.addProperty("message", validationResponse.get("message").getAsString());
                    errorResponse.addProperty("inserted", 0);
                    errorResponse.addProperty("updated", 0);
                    errorResponse.addProperty("deleted", 0);
                    errorResponse.addProperty("failed", errors != null ? errors.size() : 0);
                    errorResponse.addProperty("validation_error_count", errors != null ? errors.size() : 0);
                    errorResponse.addProperty("validation_failed_rows", countDistinctValidationErrorRows(validationResponse));
                    if (errors != null) {
                        errorResponse.add("errors", errors);
                    }

                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write(gson.toJson(errorResponse));
                    return;
                } else if (validatedData == null || validatedData.size() == 0) {
                    // No valid rows to process - fail even if cancelOnWarning is false
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Validation failed - no valid rows to process");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Validation failed with " + (errors != null ? errors.size() : 0) + " errors - no valid rows", 0, 0, 0, errors != null ? errors.size() : 0);

                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("status", "failed");
                    errorResponse.addProperty("job_id", jobId);
                    errorResponse.addProperty("reference_name", referenceName);
                    errorResponse.addProperty("message", validationResponse.get("message").getAsString());
                    errorResponse.addProperty("inserted", 0);
                    errorResponse.addProperty("updated", 0);
                    errorResponse.addProperty("deleted", 0);
                    errorResponse.addProperty("failed", errors != null ? errors.size() : 0);
                    errorResponse.addProperty("validation_error_count", errors != null ? errors.size() : 0);
                    errorResponse.addProperty("validation_failed_rows", countDistinctValidationErrorRows(validationResponse));
                    if (errors != null) {
                        errorResponse.add("errors", errors);
                    }

                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write(gson.toJson(errorResponse));
                    return;
                }
                // If cancelOnWarning is false and there are valid rows, continue processing
                validationErrorCount = (errors != null ? errors.size() : 0);
                logger.info("Cancel on Warning is false, continuing with {} valid rows ({} validation errors in report)", validatedData.size(), validationErrorCount);
            }

            validationFailedRows = countDistinctValidationErrorRows(validationResponse);

            // Step 9: Validation succeeded, process the data
            JsonArray validatedData = validationResponse.getAsJsonArray("data");
            int totalRows = validatedData.size();
            
            jobDAO.updateJobItemsCount(jobId, totalRows);
            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.updateJobProgress(jobId, "Processing", "Processing " + totalRows + " rows");
            
            // Broadcast processing start
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30, 
                "Validation complete. Processing " + totalRows + " rows...");
            
            logger.info("Processing {} validated rows", totalRows);

            // Step 10: Process each row
            java.util.Map<String, Integer> batchRefToId = new java.util.HashMap<>();
            java.util.Map<String, Integer> batchNameToId = new java.util.HashMap<>();
            int insertedCount = 0;
            int updatedCount = 0;
            int deletedCount = 0;
            int failedCount = validationErrorCount; // Include validation errors so GUI failed count matches report

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

                    // Broadcast progress periodically (every 10 rows)
                    if (i % 10 == 0) {
                        int progressPercent = 30 + (int)((i / (double)totalRows) * 65);
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                            String.format("Processing row %d of %d", i + 1, totalRows),
                            insertedCount, updatedCount, deletedCount, failedCount);
                    }

                    if (!cancelOnWarning) {
                        conn.setAutoCommit(false);
                    }

                    try {
                        if ("INSERT".equals(operation)) {
                            // Insert new regulatory theme
                            RegulatoryTheme theme = new RegulatoryTheme();
                            String name = BulkUploadUtil.getString(rowData, "Regulatory Theme Long Name");
                            if (name == null) name = "";
                            
                            // Validate name uniqueness within segment
                            if (name != null && !name.trim().isEmpty() && BulkUploadNameValidator.hasNameColumn("RegulatoryTheme")) {
                                Long segmentId = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    try {
                                        segmentId = determineSegmentId(segmentMode, segment, rowData);
                                    } catch (SQLException e) {
                                        logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                                    }
                                }
                                try {
                                    nameValidator.validateNameUniqueInSegment("RegulatoryTheme", name, segmentId, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }
                            
                            theme.setPrimaryName(name);
                            theme.setShortName(rowData.has("Short Name") && !rowData.get("Short Name").isJsonNull() ? 
                                rowData.get("Short Name").getAsString() : "");
                            
                            // Handle Reference Number - generate if empty
                            String refNumber = rowData.has("Reference") && !rowData.get("Reference").isJsonNull() ? 
                                rowData.get("Reference").getAsString().trim() : "";
                            
                            // Validate reference uniqueness if provided
                            if (refNumber != null && !refNumber.isEmpty()) {
                                try {
                                    refValidator.validateReferenceUnique("RegulatoryTheme", refNumber, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }
                            
                            if (refNumber == null || refNumber.isEmpty()) {
                                refNumber = generateDefaultRefCode();
                                // Validate auto-generated reference as well
                                try {
                                    refValidator.validateReferenceUnique("RegulatoryTheme", refNumber, conn);
                                } catch (IllegalArgumentException e) {
                                    // If auto-generated is duplicate, generate a new one (should be rare)
                                    refNumber = generateDefaultRefCode();
                                    refValidator.validateReferenceUnique("RegulatoryTheme", refNumber, conn);
                                }
                            }
                            theme.setRefNumber(refNumber);
                            
                            String description = BulkUploadUtil.getString(rowData, "Description");
                            theme.setDescription(description != null ? description : "");
                            
                            // Handle Parent Regulatory Theme Name or Parent Ref. - check batch first (parent in same file), then DB; enforce name/ref refer to same theme
                            Integer parentId = resolveParentIdWithConsistencyCheck(rowData, batchNameToId, batchRefToId, rowNumber);
                            theme.setParentId(parentId);
                            
                            // Resolve segment before create so we can validate parent-child segment and abort if different private segments
                            Long segmentIdToAssign = null;
                            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
                            } else {
                                String segmentName = getString(rowData, "Segment");
                                if (segmentName != null && !segmentName.trim().isEmpty()) {
                                    segmentIdToAssign = getSegmentIdByName(segmentName.trim());
                                }
                            }
                            if (segmentIdToAssign != null && parentId != null) {
                                boolean skipValidation = false;
                                try {
                                    skipValidation = SegmentAccessService.isSuperAdmin(userId);
                                } catch (SQLException e) {
                                    logger.warn("Error checking super admin status: {}", e.getMessage());
                                }
                                if (!skipValidation) {
                                    SegmentValidationService validator = new SegmentValidationService();
                                    SegmentValidationService.ValidationResult result = validator.validateParentChildSegment(
                                        parentId, segmentIdToAssign.intValue(), "RegulatoryTheme");
                                    if (!result.isValid) {
                                        throw new RuntimeException("Row " + rowNumber + ": Segment validation failed: " + result.message);
                                    }
                                }
                            }
                            
                            // Handle BUDG Status - convert to ID, default to 1 if empty
                            Integer statusId = null;
                            if (rowData.has("BUDG Status") && 
                                !rowData.get("BUDG Status").isJsonNull() &&
                                !rowData.get("BUDG Status").getAsString().trim().isEmpty()) {
                                String statusName = rowData.get("BUDG Status").getAsString().trim();
                                statusId = getStatusIdByName(statusName);
                                if (statusId == null) {
                                    throw new IllegalArgumentException("Status with name '" + statusName + "' not found");
                                }
                            }
                            // If status is not provided or empty, set to 1
                            if (statusId == null) {
                                statusId = 1;
                            }
                            theme.setStatusId(statusId);
                            
                            theme.setLastUpdateUserId(userId);

                            RegulatoryTheme created = regulatoryThemeDAO.createRegulatoryTheme(theme, conn);
                            if (name != null && !name.trim().isEmpty()) {
                                batchNameToId.put(name.trim().toLowerCase(), created.getId());
                            }
                            if (refNumber != null && !refNumber.trim().isEmpty()) {
                                batchRefToId.put(refNumber.trim().toLowerCase(), created.getId());
                            }

                            // Assign object to segment (segmentIdToAssign already resolved and parent-child validated before create)
                            if (segmentIdToAssign != null) {
                                try {
                                    validateSegmentAccess(userId, segmentIdToAssign, rowNumber);

                                    ObjectSegmentService.assignObjectToSegment(
                                        conn,
                                        (long) created.getId(),
                                        "RegulatoryTheme",
                                        segmentIdToAssign,
                                        userId
                                    );
                                    logger.info("Assigned Regulatory Theme {} to Segment {}", created.getId(), segmentIdToAssign);
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Regulatory Theme {} to segment: {}", created.getId(), segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }

                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    created.getId(),
                                    "Regulatory Theme",
                                    rowData,
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Regulatory Theme {}: {}", created.getId(), cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }

                            String userName = getUserName(userId);
                            regulatoryThemeDAO.createRegulatoryThemeAuditRecords(created.getId(), userName, conn);
                            regulatoryThemeDAO.createRegulatoryThemeAuditRecord(created.getId(), conn);
                            
                            insertedCount++;
                            
                            // Create success report item
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                                "Regulatory Theme created successfully", "info");

                        } else if ("UPDATE".equals(operation)) {
                            List<String> identityCols = Arrays.asList("Regulatory Theme ID", "Reference", "Regulatory Theme Long Name");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols, "Regulatory Theme", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByRef = null;
                            Integer idByName = null;
                            Integer idVal = BulkUploadUtil.getInteger(rowData, "Regulatory Theme ID");
                            if (idVal != null) {
                                RegulatoryTheme t = regulatoryThemeDAO.getRegulatoryThemeById(idVal);
                                if (t != null) idById = t.getId();
                            }
                            String refVal = BulkUploadUtil.getString(rowData, "Reference");
                            if (refVal != null && !refVal.trim().isEmpty()) {
                                RegulatoryTheme t = regulatoryThemeDAO.getRegulatoryThemeByRefNumber(refVal.trim());
                                if (t != null) idByRef = t.getId();
                            }
                            String nameVal = BulkUploadUtil.getString(rowData, "Regulatory Theme Long Name");
                            if (nameVal != null && !nameVal.trim().isEmpty()) {
                                RegulatoryTheme t = regulatoryThemeDAO.getRegulatoryThemeByPrimaryName(nameVal.trim());
                                if (t != null) idByName = t.getId();
                            }
                            int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                            Integer themeId = BulkUploadUtil.coalesce(idById, idByRef, idByName);
                            if (themeId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": No regulatory theme found for the provided identity (Regulatory Theme ID, Reference, or Regulatory Theme Long Name).");
                            }
                            if (filledIdentity >= 2) {
                                java.util.Set<Integer> ids = new java.util.HashSet<>();
                                if (idById != null) ids.add(idById);
                                if (idByRef != null) ids.add(idByRef);
                                if (idByName != null) ids.add(idByName);
                                if (ids.size() > 1) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": Regulatory Theme ID, Reference and Regulatory Theme Long Name refer to different regulatory themes.");
                                }
                            }
                            // Get old theme for audit
                            RegulatoryTheme oldTheme = regulatoryThemeDAO.getRegulatoryThemeById(themeId);
                            
                            // Double-check that theme exists (defensive programming)
                            if (oldTheme == null) {
                                throw new IllegalArgumentException("Regulatory Theme with ID " + themeId + " does not exist");
                            }
                            
                            RegulatoryTheme newTheme = new RegulatoryTheme();
                            newTheme.setId(themeId);
                            String nameFromRow = BulkUploadUtil.getString(rowData, "Regulatory Theme Long Name");
                            newTheme.setPrimaryName(nameFromRow != null && !nameFromRow.trim().isEmpty() ? nameFromRow.trim() : oldTheme.getPrimaryName());
                            // Short Name: if provided in row, use as new value; otherwise keep existing
                            String shortNameFromRow = rowData.has("Short Name") && !rowData.get("Short Name").isJsonNull() ? rowData.get("Short Name").getAsString() : null;
                            newTheme.setShortName(shortNameFromRow != null && !shortNameFromRow.trim().isEmpty() ? shortNameFromRow.trim() : (oldTheme.getShortName() != null ? oldTheme.getShortName() : ""));
                            
                            // Handle Reference Number - keep existing if empty in update
                            String refNumber = rowData.has("Reference") && !rowData.get("Reference").isJsonNull() ? 
                                rowData.get("Reference").getAsString().trim() : "";
                            if (refNumber == null || refNumber.isEmpty()) {
                                // If empty in update, keep existing ref number
                                refNumber = oldTheme.getRefNumber();
                            }
                            newTheme.setRefNumber(refNumber);
                            
                            // Description: if provided in row, use as new value; otherwise keep existing
                            String descriptionFromRow = rowData.has("Description") && !rowData.get("Description").isJsonNull() ? rowData.get("Description").getAsString() : null;
                            newTheme.setDescription(descriptionFromRow != null ? descriptionFromRow : (oldTheme.getDescription() != null ? oldTheme.getDescription() : ""));
                            
                            // Handle Parent Regulatory Theme Name or Parent Ref. - convert to ID; enforce name/ref refer to same theme
                            Integer parentId = resolveParentIdWithConsistencyCheck(rowData, new java.util.HashMap<>(), new java.util.HashMap<>(), rowNumber);
                            newTheme.setParentId(parentId);
                            
                            // When parent is specified, validate object and parent are not in different private segments
                            if (parentId != null && parentId > 0) {
                                Long newSegmentIdForParent = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    newSegmentIdForParent = determineSegmentId(segmentMode, segment, rowData);
                                } else {
                                    String segmentName = getString(rowData, "Segment");
                                    if (segmentName != null && !segmentName.trim().isEmpty()) {
                                        newSegmentIdForParent = getSegmentIdByName(segmentName.trim());
                                    }
                                }
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) themeId, "Regulatory Theme");
                                int effectiveChildSegmentId = (newSegmentIdForParent != null) ? newSegmentIdForParent.intValue()
                                        : (currentSegmentId != null ? currentSegmentId.intValue() : 1);
                                SegmentValidationService parentValidator = new SegmentValidationService();
                                SegmentValidationService.ValidationResult parentResult = parentValidator.validateParentChildSegment(
                                        parentId, effectiveChildSegmentId, "Regulatory Theme");
                                if (!parentResult.isValid) {
                                    throw new RuntimeException("Row " + rowNumber + ": Parent segment validation failed: " + parentResult.message);
                                }
                            }
                            
                            // Handle BUDG Status - convert to ID, default to 1 if empty
                            Integer statusId = null;
                            if (rowData.has("BUDG Status") && 
                                !rowData.get("BUDG Status").isJsonNull() &&
                                !rowData.get("BUDG Status").getAsString().trim().isEmpty()) {
                                String statusName = rowData.get("BUDG Status").getAsString().trim();
                                statusId = getStatusIdByName(statusName);
                                if (statusId == null) {
                                    throw new IllegalArgumentException("Status with name '" + statusName + "' not found");
                                }
                            }
                            // If status is not provided or empty, set to 1
                            if (statusId == null) {
                                statusId = 1;
                            }
                            newTheme.setStatusId(statusId);
                            
                            newTheme.setLastUpdateUserId(userId);

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
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) themeId, "Regulatory Theme");
                                    
                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            themeId, newSegmentId.intValue(), "Regulatory Theme", parentId);
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Segment validation failed: " + result.message);
                                        }
                                        
                                        
                                        validateSegmentAccess(userId, newSegmentId, rowNumber);
                                        
                                        // Assign new segment using the same connection
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) themeId, 
                                            "RegulatoryTheme", 
                                            newSegmentId, 
                                            userId
                                        );
                                        logger.info("Assigned Regulatory Theme {} to Segment {} (updated)", themeId, newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Regulatory Theme {} to segment: {}", themeId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            String userName = getUserName(userId);
                            regulatoryThemeDAO.updateRegulatoryThemeWithAudit(oldTheme, newTheme, userName);
                            
                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    themeId, 
                                    "Regulatory Theme", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                // Custom field failure = row failure (BUDG behavior)
                                logger.error("Failed to save custom fields for Regulatory Theme {}: {}", themeId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            updatedCount++;
                            
                            // Create success report item
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                                "Regulatory Theme updated successfully", "info");

                        } else if ("DELETE".equals(operation)) {
                            int themeId = rowData.get("Regulatory Theme ID").getAsInt();
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "regulatory-theme", themeId);
                            if (!validationResult.canDelete()) {
                                String errorMsg = String.join("; ", validationResult.getErrors());
                                failedCount++;
                                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "failed", rowNumber);
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
                                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "failed", rowNumber);
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
                                RegulatoryTheme existingTheme = regulatoryThemeDAO.getRegulatoryThemeById(themeId);
                                if (existingTheme == null) {
                                    logger.info("Regulatory Theme with ID {} does not exist, skipping deletion (idempotent)", themeId);
                                    continue;
                                }
                                String userName = getUserName(userId);
                                boolean deleted = regulatoryThemeDAO.deleteRegulatoryThemeWithAudit(themeId, userName);
                                if (deleted) {
                                    deletedCount++;
                                    int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                                    jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                        "Regulatory Theme deleted successfully", "info");
                                } else {
                                    throw new IllegalStateException("Failed to delete regulatory theme with ID " + themeId);
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
                        
                        // Create error report item
                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "PROCESSING_ERROR", 
                            e.getMessage(), "error");

                        if (cancelOnWarning) {
                            // Rollback and exit
                            conn.rollback();
                            throw new ServletException("Processing failed at row " + rowNumber + ": " + e.getMessage());
                        } else {
                            try {
                                conn.rollback();
                                logger.debug("Rolled back row {} due to error", rowNumber);
                            } catch (SQLException rollbackEx) {
                                logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
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
                    logger.info("Transaction committed successfully");
                }

            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                        logger.warn("Transaction rolled back due to error");
                    } catch (SQLException rollbackEx) {
                        logger.error("Error during rollback", rollbackEx);
                    }
                }
                
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Processing error: " + e.getMessage());
                
                // Broadcast failure via WebSocket
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Processing error: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send failure notification
                if (userIdStr != null && uploadOption != null) {
                    try {
                        int parsedUserId = Integer.parseInt(userIdStr);
                        sendNotification(parsedUserId, "Regulatory Theme", uploadOption, insertedCount, updatedCount, 
                                        deletedCount, failedCount, false, e.getMessage(), jobId);
                    } catch (Exception notifEx) {
                        logger.error("Error sending notification", notifEx);
                    }
                }
                
                throw new ServletException("Bulk processing failed: " + e.getMessage(), e);
                
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

            // Step 11: Check for final completion status and update job status
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
                    "Processing failed: all rows failed",
                    insertedCount, updatedCount, deletedCount, failedCount);

                logger.info("Bulk upload failed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
                           insertedCount, updatedCount, deletedCount, failedCount);
                
                sendNotification(userId, "Regulatory Theme", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, false, "All rows failed during processing", jobId);
            } else if (isPartiallyCompleted) {
                jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount); // Only successful operations
                jobDAO.updateJobProgress(jobId, "Partially Completed", 
                    String.format("Completed: %d inserted, %d updated, %d deleted, %d failed", 
                                 insertedCount, updatedCount, deletedCount, failedCount));

                // Broadcast completion via WebSocket
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                    "Processing complete with some failures",
                    insertedCount, updatedCount, deletedCount, failedCount);

                logger.info("Bulk upload partially completed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {}", 
                           insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send notification for partial completion
                sendNotification(userId, "Regulatory Theme", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, true, null, jobId);
            } else {
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount);
                jobDAO.updateJobProgress(jobId, "Completed", 
                    String.format("Completed: %d inserted, %d updated, %d deleted, %d failed", 
                                 insertedCount, updatedCount, deletedCount, failedCount));

                // Broadcast completion via WebSocket
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    String.format("Bulk upload completed successfully"),
                    insertedCount, updatedCount, deletedCount, failedCount);

                logger.info("Bulk upload completed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {}", 
                           insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send success notification
                sendNotification(userId, "Regulatory Theme", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, true, null, jobId);
            }

            // Step 12: Return success response
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("inserted", insertedCount);
            successResponse.addProperty("updated", updatedCount);
            successResponse.addProperty("deleted", deletedCount);
            successResponse.addProperty("failed", failedCount);
            successResponse.addProperty("validation_error_count", validationErrorCount);
            successResponse.addProperty("validation_failed_rows", validationFailedRows);
            successResponse.addProperty("report_url", "/api/bulk/regulatorytheme/report/" + jobId);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(successResponse));

        } catch (Exception e) {
            logger.error("Error during bulk upload", e);
            
            // Update job status if job was created (do not overwrite Completed/Partially Completed)
            if (jobId > 0) {
                try {
                    String currentStatus = jobDAO.getJobStatus(jobId);
                    if (currentStatus == null || (!"Completed".equals(currentStatus) && !"Partially Completed".equals(currentStatus))) {
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Unexpected error: " + e.getMessage());
                        
                        // Send failure notification if we have userId
                        if (userIdStr != null && uploadOption != null) {
                            try {
                                int userId = Integer.parseInt(userIdStr);
                                sendNotification(userId, "Regulatory Theme", uploadOption, 0, 0, 0, 0, 
                                          false, "Unexpected error: " + e.getMessage(), jobId);
                            } catch (Exception notifEx) {
                                logger.error("Error sending notification", notifEx);
                            }
                        }
                    } else {
                        logger.info("Job {} already completed (status: {}), not overwriting with Failed after exception: {}", jobId, currentStatus, e.getMessage());
                    }
                } catch (SQLException sqlEx) {
                    logger.error("Error updating job status", sqlEx);
                }
            }
            
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }
    
    private static int countDistinctValidationErrorRows(JsonObject validationResponse) {
        if (validationResponse == null || !validationResponse.has("errors")) {
            return 0;
        }
        com.google.gson.JsonArray errors = validationResponse.getAsJsonArray("errors");
        if (errors == null || errors.size() == 0) {
            return 0;
        }
        java.util.Set<Integer> rows = new java.util.HashSet<>();
        for (int i = 0; i < errors.size(); i++) {
            com.google.gson.JsonObject e = errors.get(i).getAsJsonObject();
            if (e.has("row") && !e.get("row").isJsonNull()) {
                try {
                    rows.add(e.get("row").getAsInt());
                } catch (Exception ignored) {
                    // skip malformed row field
                }
            }
        }
        return rows.size();
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
            n.setFacetType("regulatory_theme");
            n.setObjectId(jobId);
            n.setRead(false);
            
            dao.create(n);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Get file name from Part header
     */
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        for (String content : contentDisposition.split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return "unknown";
    }

    /**
     * Get user name from user ID
     */
    private String getUserName(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?")) {
            ps.setInt(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fullName");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting user name", e);
        }
        return "Unknown User";
    }

    /**
     * Resolve parent Regulatory Theme ID from row (Parent Regulatory Theme Name and/or Parent Ref.).
     * When both name and ref are provided, both must refer to the same theme; otherwise throws.
     *
     * @param rowData row data
     * @param batchNameToId name -> id for themes created in same file (INSERT only; use empty map for UPDATE)
     * @param batchRefToId ref -> id for themes created in same file (INSERT only; use empty map for UPDATE)
     * @param rowNumber row number for error messages
     * @return parent ID or null if no parent specified
     */
    private Integer resolveParentIdWithConsistencyCheck(JsonObject rowData,
            java.util.Map<String, Integer> batchNameToId, java.util.Map<String, Integer> batchRefToId, int rowNumber) {
        // Accept "Parent Regulatory Theme Name" or template alias "Parent Name"
        String parentNameVal = getString(rowData, "Parent Regulatory Theme Name");
        if (parentNameVal == null || parentNameVal.trim().isEmpty()) {
            parentNameVal = getString(rowData, "Parent Name");
        }
        boolean hasName = parentNameVal != null && !parentNameVal.trim().isEmpty();
        boolean hasRef = rowData.has("Parent Ref.") && !rowData.get("Parent Ref.").isJsonNull()
                && !rowData.get("Parent Ref.").getAsString().trim().isEmpty();

        if (hasName && hasRef) {
            String parentName = (parentNameVal != null) ? parentNameVal.trim() : "";
            String parentRef = rowData.get("Parent Ref.").getAsString().trim();
            Integer idByName = batchNameToId.get(parentName.toLowerCase());
            if (idByName == null) idByName = getRegulatoryThemeIdByName(parentName);
            Integer idByRef = batchRefToId.get(parentRef.toLowerCase());
            if (idByRef == null) idByRef = getRegulatoryThemeIdByRefNumber(parentRef);
            if (idByName != null && idByRef != null && !idByName.equals(idByRef)) {
                throw new IllegalArgumentException("Row " + rowNumber + ": Parent name and Parent Ref. refer to different regulatory themes");
            }
            if (idByName != null) return idByName;
            if (idByRef != null) return idByRef;
            throw new IllegalArgumentException("Row " + rowNumber + ": Parent Regulatory Theme with name '" + parentName + "' and reference '" + parentRef + "' not found");
        }

        if (hasName) {
            String parentName = (parentNameVal != null) ? parentNameVal.trim() : "";
            Integer id = batchNameToId.get(parentName.toLowerCase());
            if (id == null) id = getRegulatoryThemeIdByName(parentName);
            if (id == null) throw new IllegalArgumentException("Row " + rowNumber + ": Parent Regulatory Theme with name '" + parentName + "' not found");
            return id;
        }
        if (hasRef) {
            String parentRef = rowData.get("Parent Ref.").getAsString().trim();
            Integer id = batchRefToId.get(parentRef.toLowerCase());
            if (id == null) id = getRegulatoryThemeIdByRefNumber(parentRef);
            if (id == null) throw new IllegalArgumentException("Row " + rowNumber + ": Parent Regulatory Theme with reference '" + parentRef + "' not found");
            return id;
        }
        return null;
    }

    /**
     * Get Regulatory Theme ID by PrimaryName
     */
    private Integer getRegulatoryThemeIdByName(String name) {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM regulatorytheme WHERE LOWER(PrimaryName) = LOWER(?) AND DeletedDatetime IS NULL LIMIT 1")) {
            ps.setString(1, name.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting regulatory theme ID by name: {}", name, e);
        }
        return null;
    }

    /**
     * Get Regulatory Theme ID by RefNumber
     */
    private Integer getRegulatoryThemeIdByRefNumber(String refNumber) {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM regulatorytheme WHERE RefNumber = ? AND DeletedDatetime IS NULL LIMIT 1")) {
            ps.setString(1, refNumber.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting regulatory theme ID by ref number: {}", refNumber, e);
        }
        return null;
    }

    /**
     * Get Status ID by primaryname
     */
    private Integer getStatusIdByName(String statusName) {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM status WHERE primaryname = ? LIMIT 1")) {
            ps.setString(1, statusName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting status ID by name: {}", statusName, e);
        }
        return null;
    }

    /**
     * Generate default reference code for Regulatory Theme
     * Format: REGTH-{nextId}
     */
    private String generateDefaultRefCode() {
        int nextId = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(ID), 0) + 1 AS next_id FROM regulatorytheme")) {
            if (rs.next()) {
                nextId = rs.getInt("next_id");
            }
        } catch (SQLException e) {
            logger.warn("Error generating default ref code, using fallback ID: {}", e.getMessage());
            // fallback will use 1
        }
        return "REGTH-" + nextId;
    }

    /**
     * Generate short reference name with sequential number
     */
    private String generateShortReferenceName() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'RTH-%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("RTH-")) {
                        try {
                            String numberPart = refName.substring(4);
                            int number = Integer.parseInt(numberPart);
                            if (number > maxNumber) {
                                maxNumber = number;
                            }
                        } catch (NumberFormatException ignore) {}
                    }
                }
                if (maxNumber > 0) {
                    nextNumber = maxNumber + 1;
                }
            }
        } catch (SQLException e) {
            logger.warn("Error generating short reference name: {}", e.getMessage());
        }
        return "RTH-" + nextNumber;
    }

    /**
     * Determine job type based on entity and upload option
     * Format: "FacetName Upload" | "FacetName Update" | "FacetName Delete"
     */
    private String determineJobType(String entityName, String uploadOption) {
        // Always return "bulk_upload" for consistency with other bulk upload servlets
        return "bulk_upload";
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

