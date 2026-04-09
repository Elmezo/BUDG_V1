package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.GeographyDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.Geography;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
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

@WebServlet("/api/bulk/geography/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class GeographyBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(GeographyBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("geography"); }
    
    private final JobDAO jobDAO = new JobDAO();
    private final GeographyDAO geographyDAO = new GeographyDAO();

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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Geography";
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
            String fileName = "geography_" + timestamp + "_" + uuid + ".xlsx";
            
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
            // Use entity from request parameter, fallback to "Geography" if not provided (entityName already set above for permission check)
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
                metadata.addProperty("entity", entity != null ? entity : "Geography");
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
            validationRequest.addProperty("entity", entity != null ? entity : "Geography");
            validationRequest.addProperty("user_id", userId);

            // Add segment parameters if provided (for INSERT operations only)
            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                validationRequest.addProperty("segment_mode", segmentMode);
                if (segment != null && !segment.trim().isEmpty()) {
                    validationRequest.addProperty("segment", segment);
                }
            }
            
            // Add column mappings if provided
            // Frontend sends: {fieldName: excelColumnName} e.g., {"Geography Name": "اسم الجغرافيا"}
            // Python expects: {excelColumnName: fieldName} e.g., {"اسم الجغرافيا": "Geography Name"}
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

            // Step 8: Handle validation response
            boolean validationHadErrors = false;
            int validationErrorCount = 0;
            JsonArray validatedData;

            if ("error".equals(validationStatus) || "invalid".equals(validationStatus)) {
                // Validation had errors
                JsonArray errors = validationResponse.getAsJsonArray("errors");
                validationErrorCount = errors.size();
                logger.warn("Validation failed with {} errors", validationErrorCount);

                // Store validation errors in Job_Report_Item (use entity name for consistency with success/error items)
                for (int i = 0; i < errors.size(); i++) {
                    JsonObject error = errors.get(i).getAsJsonObject();
                    int reportItemId = jobDAO.createJobReportItem(
                        jobId,
                        entityName,
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

                validatedData = validationResponse.getAsJsonArray("data");
                if (validatedData == null || validatedData.size() == 0) {
                    // No valid rows to process; set Failed and return failed response
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Validation failed");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Validation failed with " + validationErrorCount + " errors", 0, 0, 0, validationErrorCount);
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("status", "failed");
                    errorResponse.addProperty("job_id", jobId);
                    errorResponse.addProperty("reference_name", referenceName);
                    errorResponse.addProperty("message", validationResponse.get("message").getAsString());
                    errorResponse.addProperty("inserted", 0);
                    errorResponse.addProperty("updated", 0);
                    errorResponse.addProperty("deleted", 0);
                    errorResponse.addProperty("failed", validationErrorCount);
                    errorResponse.add("errors", errors);
                    errorResponse.addProperty("report_url", "/api/bulk/geography/report/" + jobId);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write(gson.toJson(errorResponse));
                    return;
                }
                // Has valid rows to process; do NOT set Failed here - set Processing and run background thread
                validationHadErrors = true;
            } else {
                validatedData = validationResponse.getAsJsonArray("data");
            }

            // Step 9: Process validated data in background (return 200 + job_id immediately)
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
            jobDAO.updateJobProgress(jobId, "Processing", "Processing " + totalRows + " rows");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                "Validation complete. Processing " + totalRows + " rows...");
            logger.info("Processing {} validated rows (Async)", totalRows);

            final int finalJobId = jobId;
            final int finalUserId = userId;
            final String finalErrorHandling = errorHandling;
            final String finalUploadOption = uploadOption;
            final String finalSegmentMode = segmentMode;
            final String finalSegment = segment;
            final String finalEntityName = entityName;
            final JsonArray finalValidatedData = validatedData;
            final boolean finalValidationHadErrors = validationHadErrors;
            final int finalValidationErrorCount = validationErrorCount;

            Thread processingThread = new Thread(() -> {
                processGeographyData(finalJobId, finalValidatedData, finalUserId, finalErrorHandling,
                    finalUploadOption, finalSegmentMode, finalSegment, finalEntityName,
                    finalValidationHadErrors, finalValidationErrorCount);
            });
            processingThread.setDaemon(false);
            processingThread.start();

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("message", "Processing " + totalRows + " rows...");
            successResponse.addProperty("report_url", "/api/bulk/geography/report/" + jobId);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(successResponse));
            return;
        } catch (Exception e) {
            logger.error("Error during bulk upload", e);
            
            if (jobId > 0) {
                try {
                    String currentStatus = jobDAO.getJobStatus(jobId);
                    if (currentStatus == null || (!"Completed".equals(currentStatus) && !"Partially Completed".equals(currentStatus))) {
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Unexpected error: " + e.getMessage());
                        if (userIdStr != null && uploadOption != null) {
                            try {
                                int userId = Integer.parseInt(userIdStr);
                                sendNotification(userId, "Geography", uploadOption, 0, 0, 0, 0, 
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

    /**
     * Background processing for Geography bulk upload. Updates job status to Completed/Partially Completed/Failed.
     * Ensures terminal status in finally if still "Processing".
     */
    private void processGeographyData(int jobId, JsonArray validatedData, int userId, String errorHandling,
            String uploadOption, String segmentMode, String segment, String entityName,
            boolean validationHadErrors, int validationErrorCount) {
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        java.util.Map<String, Integer> batchNameToId = new java.util.HashMap<>();
        boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
        Connection conn = null;
        BulkUploadNameValidator nameValidator = new BulkUploadNameValidator();
        int totalRows = validatedData.size();

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
                            // Insert new geography
                            Geography geography = new Geography();
                            String name = rowData.get("Geography Name").getAsString();
                            
                            // Validate name uniqueness within segment
                            if (name != null && !name.trim().isEmpty() && BulkUploadNameValidator.hasNameColumn("Geography")) {
                                Long segmentId = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    try {
                                        segmentId = determineSegmentId(segmentMode, segment, rowData);
                                    } catch (SQLException e) {
                                        logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                                    }
                                }
                                try {
                                    nameValidator.validateNameUniqueInSegment("Geography", name, segmentId, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }
                            
                            geography.setPrimaryName(name);
                            geography.setDescription(rowData.has("Geography Definition") ? 
                                rowData.get("Geography Definition").getAsString() : "");
                            
                            // Handle Parent Geography Name - check batch first (parent in same file), then DB
                            if (rowData.has("Parent Geography Name") && 
                                !rowData.get("Parent Geography Name").isJsonNull() &&
                                !rowData.get("Parent Geography Name").getAsString().trim().isEmpty()) {
                                String parentName = rowData.get("Parent Geography Name").getAsString().trim();
                                Integer parentId = batchNameToId.get(parentName.toLowerCase());
                                if (parentId == null) {
                                    parentId = getGeographyIdByName(parentName);
                                    if (parentId == null) {
                                        throw new IllegalArgumentException("Parent Geography with name '" + parentName + "' not found");
                                    }
                                }
                                geography.setParentId(parentId);
                            }
                            
                            geography.setLastUpdateUserId(userId);

                            Geography created = geographyDAO.createGeography(geography);
                            if (name != null && !name.trim().isEmpty()) {
                                batchNameToId.put(name.trim().toLowerCase(), created.getId());
                            }
                            
                            // Create audit record
                            String userName = getUserName(userId);
                            geographyDAO.createGeographyAuditRecords(created.getId(), userName);
                            geographyDAO.createGeographyAuditRecord(created.getId());
                            
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
                            
                            if (segmentIdToAssign != null && geography.getParentId() != null) {
                                boolean skipValidation = false;
                                try {
                                    skipValidation = SegmentAccessService.isSuperAdmin(userId);
                                } catch (SQLException e) {
                                    logger.warn("Error checking super admin status: {}", e.getMessage());
                                }
                                if (!skipValidation) {
                                    SegmentValidationService validator = new SegmentValidationService();
                                    SegmentValidationService.ValidationResult result = validator.validateParentChildSegment(
                                        geography.getParentId(), segmentIdToAssign.intValue(), "Geography");
                                    if (!result.isValid) {
                                        throw new RuntimeException("Segment validation failed: " + result.message);
                                    }
                                }
                            }
                            
                            if (segmentIdToAssign != null) {
                                try {
                                    validateSegmentAccess(userId, segmentIdToAssign, rowNumber);
                                    
                                    ObjectSegmentService.assignObjectToSegment(
                                        conn,
                                        (long) created.getId(), 
                                        "Geography", 
                                        segmentIdToAssign, 
                                        userId
                                    );
                                    logger.info("Assigned Geography {} to Segment {}", created.getId(), segmentIdToAssign);
                                } catch (Exception segEx) {
                                    // Segment assignment failure = row failure (BUDG behavior)
                                    logger.error("Failed to assign Geography {} to segment: {}", created.getId(), segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    created.getId(), 
                                    "Geography", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Geography {}: {}", created.getId(), cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            insertedCount++;
                            
                            // Create success report item
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                                "Geography created successfully", "info");

                        } else if ("UPDATE".equals(operation)) {
                            List<String> identityCols = Arrays.asList("Geography ID", "Geography Name");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols, "Geography", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByName = null;
                            Integer idVal = BulkUploadUtil.getInteger(rowData, "Geography ID");
                            if (idVal != null) {
                                Geography g = geographyDAO.getGeographyById(idVal);
                                if (g != null) idById = g.getId();
                            }
                            String nameVal = BulkUploadUtil.getString(rowData, "Geography Name");
                            if (nameVal != null && !nameVal.trim().isEmpty()) {
                                Integer nameId = getGeographyIdByName(nameVal.trim());
                                if (nameId != null) idByName = nameId;
                            }
                            int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                            Integer geographyId = idById != null ? idById : idByName;
                            if (geographyId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": No geography found for the provided identity (Geography ID or Geography Name).");
                            }
                            if (filledIdentity >= 2) {
                                java.util.Set<Integer> ids = new java.util.HashSet<>();
                                if (idById != null) ids.add(idById);
                                if (idByName != null) ids.add(idByName);
                                if (ids.size() > 1) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": Geography ID and Geography Name refer to different geographies.");
                                }
                            }
                            // Get old geography for audit
                            Geography oldGeography = geographyDAO.getGeographyById(geographyId);
                            
                            // Double-check that geography exists (defensive programming)
                            if (oldGeography == null) {
                                throw new IllegalArgumentException("Geography with ID " + geographyId + " does not exist");
                            }
                            
                            Geography newGeography = new Geography();
                            newGeography.setId(geographyId);
                            String nameFromRow = BulkUploadUtil.getString(rowData, "Geography Name");
                            newGeography.setPrimaryName(nameFromRow != null && !nameFromRow.trim().isEmpty() ? nameFromRow.trim() : oldGeography.getPrimaryName());
                            String defFromRow = getString(rowData, "Geography Definition");
                            newGeography.setDescription(rowData.has("Geography Definition") && defFromRow != null && !defFromRow.trim().isEmpty()
                                ? defFromRow.trim() : oldGeography.getDescription());
                            
                            // Handle Parent Geography Name - check batch first (parent in same file), then DB
                            if (rowData.has("Parent Geography Name") && 
                                !rowData.get("Parent Geography Name").isJsonNull() &&
                                !rowData.get("Parent Geography Name").getAsString().trim().isEmpty()) {
                                String parentName = rowData.get("Parent Geography Name").getAsString().trim();
                                Integer parentId = batchNameToId.get(parentName.toLowerCase());
                                if (parentId == null) {
                                    parentId = getGeographyIdByName(parentName);
                                    if (parentId == null) {
                                        throw new IllegalArgumentException("Parent Geography with name '" + parentName + "' not found");
                                    }
                                }
                                newGeography.setParentId(parentId);
                            } else {
                                // If not provided, keep existing parent or set to null
                                newGeography.setParentId(null);
                            }
                            
                            // When parent is specified, validate object and parent are not in different private segments
                            Integer geographyParentId = newGeography.getParentId();
                            if (geographyParentId != null && geographyParentId > 0) {
                                int childSegmentId = SegmentAccessService.getObjectSegmentId(geographyId, "Geography");
                                int parentSegmentId = SegmentAccessService.getObjectSegmentId(geographyParentId, "Geography");
                                if (parentSegmentId == -1) {
                                    parentSegmentId = 1; // treat parent unassigned as Enterprise
                                }
                                boolean parentIsPrivate = (parentSegmentId > 1);
                                if (childSegmentId == -1 && parentIsPrivate) {
                                    throw new RuntimeException("Parent belongs to a private segment; the geography must be assigned to that segment or the parent must be in the same segment.");
                                }
                                int effectiveChildSegmentId = (childSegmentId == -1) ? 1 : childSegmentId;
                                SegmentValidationService parentValidator = new SegmentValidationService();
                                SegmentValidationService.ValidationResult parentResult = parentValidator.validateParentChildSegment(
                                        geographyParentId, effectiveChildSegmentId, "Geography");
                                if (!parentResult.isValid) {
                                    throw new RuntimeException("Parent segment validation failed: " + parentResult.message);
                                }
                            }
                            
                            newGeography.setLastUpdateUserId(userId);

                            // Handle segment assignment for UPDATE operations
                            Long newSegmentId = null;
                            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                newSegmentId = determineSegmentId(segmentMode, segment, rowData);
                            } else {
                                // Check if Segment column exists in Excel even when segmentMode is null
                                String segmentName = getString(rowData, "Segment");
                                if (segmentName != null && !segmentName.trim().isEmpty()) {
                                    newSegmentId = getSegmentIdByName(segmentName.trim());
                                    if (newSegmentId == null) {
                                        throw new RuntimeException("Segment '" + segmentName.trim() + "' not found");
                                    }
                                }
                            }
                            
                            if (newSegmentId != null) {
                                try {
                                    // Get current segment
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) geographyId, "Geography");
                                    
                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            geographyId, newSegmentId.intValue(), "Geography", newGeography.getParentId());
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Segment validation failed: " + result.message);
                                        }
                                        
                                        validateSegmentAccess(userId, newSegmentId, rowNumber);
                                        
                                        // Assign new segment
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) geographyId, 
                                            "Geography", 
                                            newSegmentId, 
                                            userId
                                        );
                                        logger.info("Assigned Geography {} to Segment {} (updated)", geographyId, newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Geography {} to segment: {}", geographyId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            String userName = getUserName(userId);
                            geographyDAO.updateGeographyWithAudit(oldGeography, newGeography, userName);
                            
                            // Save custom field values (geographyId already defined above)
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    geographyId, 
                                    "Geography", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Geography {}: {}", geographyId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            updatedCount++;
                            
                            // Create success report item
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                                "Geography updated successfully", "info");

                        } else if ("DELETE".equals(operation)) {
                            int geographyId = rowData.get("Geography ID").getAsInt();
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "geography", geographyId);
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
                                Geography existingGeography = geographyDAO.getGeographyById(geographyId);
                                if (existingGeography == null) {
                                    logger.info("Geography with ID {} does not exist, skipping deletion (idempotent)", geographyId);
                                    continue;
                                }
                                String userName = getUserName(userId);
                                boolean deleted = geographyDAO.deleteGeographyWithAudit(geographyId, userName);
                                if (deleted) {
                                    deletedCount++;
                                    int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                                    jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                        "Geography deleted successfully", "info");
                                } else {
                                    throw new IllegalStateException("Failed to delete geography with ID " + geographyId);
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
                        
                        // Create error report item (all errors recorded so report shows every failing row)
                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "PROCESSING_ERROR", 
                            e.getMessage(), "error");

                        try {
                            conn.rollback();
                            logger.debug("Rolled back row {} due to error", rowNumber);
                        } catch (SQLException rollbackEx) {
                            logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                        }
                        // When Cancel on Warning: do not throw here; continue to collect all row errors, then fail after loop
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

                if (cancelOnWarning && failedCount > 0) {
                    // Cancel on Warning with one or more errors: rollback entire transaction, report all errors
                    try {
                        conn.rollback();
                        logger.warn("Transaction rolled back: Cancel on Warning with {} failed row(s)", failedCount);
                    } catch (SQLException rollbackEx) {
                        logger.error("Error during rollback", rollbackEx);
                    }
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", 
                        String.format("Processing failed with %d error(s). No rows were committed.", failedCount));
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Processing failed with " + failedCount + " error(s)",
                        insertedCount, updatedCount, deletedCount, failedCount);
                    sendNotification(userId, "Geography", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, false, "Cancel on Warning: " + failedCount + " error(s). No rows committed.", jobId);
                    return;
                }

                if (cancelOnWarning) {
                    conn.commit();
                    logger.info("Transaction committed successfully");
                }

                // Step 11: Check for partial completion and update job status (inside try so finally sees terminal status)
                int successfulCount = insertedCount + updatedCount + deletedCount;
                int totalFailedCount = failedCount + validationErrorCount;
                boolean hasFailures = failedCount > 0 || validationHadErrors;
                boolean isPartiallyCompleted = (successfulCount > 0) && hasFailures;
                boolean isAllFailed = (successfulCount == 0) && hasFailures;

                if (isAllFailed) {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobItemsCount(jobId, successfulCount);
                    jobDAO.updateJobProgress(jobId, "Failed",
                        String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                                 insertedCount, updatedCount, deletedCount, totalFailedCount));
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Processing failed: all rows failed",
                        insertedCount, updatedCount, deletedCount, totalFailedCount);
                    logger.info("Bulk upload failed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
                               insertedCount, updatedCount, deletedCount, totalFailedCount);
                    sendNotification(userId, "Geography", uploadOption, insertedCount, updatedCount,
                                    deletedCount, totalFailedCount, false, "All rows failed during processing", jobId);
                } else if (isPartiallyCompleted) {
                    jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                    jobDAO.updateJobItemsCount(jobId, successfulCount);
                    jobDAO.updateJobProgress(jobId, "Partially Completed",
                        String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                                 insertedCount, updatedCount, deletedCount, totalFailedCount));
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                        "Processing complete with some failures",
                        insertedCount, updatedCount, deletedCount, totalFailedCount);
                    logger.info("Bulk upload partially completed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
                               insertedCount, updatedCount, deletedCount, totalFailedCount);
                    sendNotification(userId, "Geography", uploadOption, insertedCount, updatedCount,
                                    deletedCount, totalFailedCount, true, null, jobId);
                } else {
                    jobDAO.updateJobStatus(jobId, "Completed", true);
                    jobDAO.updateJobItemsCount(jobId, successfulCount);
                    jobDAO.updateJobProgress(jobId, "Completed",
                        String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                                 insertedCount, updatedCount, deletedCount, totalFailedCount));
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                        String.format("Bulk upload completed successfully"),
                        insertedCount, updatedCount, deletedCount, totalFailedCount);
                    logger.info("Bulk upload completed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
                               insertedCount, updatedCount, deletedCount, totalFailedCount);
                    sendNotification(userId, "Geography", uploadOption, insertedCount, updatedCount,
                                    deletedCount, totalFailedCount, true, null, jobId);
                }

            } catch (Exception e) {
                logger.error("Error during Geography bulk processing", e);
                if (conn != null) {
                    try {
                        conn.rollback();
                        logger.warn("Transaction rolled back due to error");
                    } catch (SQLException rollbackEx) {
                        logger.error("Error during rollback", rollbackEx);
                    }
                }
                try {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Processing error: " + e.getMessage());
                } catch (SQLException sqlEx) {
                    logger.error("Error updating job status in catch: {}", sqlEx.getMessage(), sqlEx);
                }
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Processing error: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);
                sendNotification(userId, "Geography", uploadOption, insertedCount, updatedCount,
                    deletedCount, failedCount, false, e.getMessage(), jobId);
                return;
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException e) {
                        logger.error("Error closing connection", e);
                    }
                }
                // Ensure job never stays in Processing: if still Processing, set Failed
                try {
                    String status = jobDAO.getJobStatus(jobId);
                    if (status != null && "Processing".equals(status)) {
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Processing ended unexpectedly");
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Processing ended unexpectedly", insertedCount, updatedCount, deletedCount, failedCount);
                    }
                } catch (SQLException sqlEx) {
                    logger.error("Error updating job status in finally: {}", sqlEx.getMessage(), sqlEx);
                }
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
            n.setFacetType("geography");
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
     * Get Geography ID by name
     */
    private Integer getGeographyIdByName(String name) {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM geography WHERE LOWER(PrimaryName) = LOWER(?) AND DeletedDatetime IS NULL LIMIT 1")) {
            ps.setString(1, name.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting geography ID by name: {}", name, e);
        }
        return null;
    }

    /**
     * Generate short reference name with sequential number
     */
    private String generateShortReferenceName() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'GEO-%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("GEO-")) {
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
        return "GEO-" + nextNumber;
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
    
    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }
}

