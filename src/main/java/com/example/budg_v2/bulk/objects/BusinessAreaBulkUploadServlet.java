package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.BusinessAreaDAO;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
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
import com.example.budg_v2.bulk.roles.util.RoleHandlerUtil;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@WebServlet({"/bulk/businessarea/upload", "/api/bulk/businessarea/upload"})
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,
    maxFileSize = 1024 * 1024 * 10,
    maxRequestSize = 1024 * 1024 * 50
)
public class BusinessAreaBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BusinessAreaBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("businessarea"); }

    private final JobDAO jobDAO = new JobDAO();
    private final BusinessAreaDAO businessAreaDAO = new BusinessAreaDAO();

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

        logger.info("Business Area bulk upload request received at /bulk/businessarea/upload");
        logger.info("Request URI: {}, Path Info: {}, Servlet Path: {}", 
            request.getRequestURI(), request.getPathInfo(), request.getServletPath());

        int jobId = -1;
        String storagePath = null;
        String userIdStr = null;
        String uploadOption = null;

        try {
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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Business Area";
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
            logger.info("Uploaded file: {}", originalFileName);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String uuid = UUID.randomUUID().toString().substring(0, 6);
            String fileName = "businessarea_" + timestamp + "_" + uuid + ".xlsx";

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

            jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
            jobDAO.createJobProgress(jobId, 0, "Validating", "File uploaded, starting validation");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Pending", 5, "File uploaded, starting validation");

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
                validationResponse = HttpClientUtil.postJsonGetJson(
                    PYTHON_SERVICE_URL,
                    validationRequest,
                    HttpClientUtil.BULK_VALIDATION_SOCKET_TIMEOUT_MS
                );
            } catch (IOException e) {
                String baseMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                String userFriendlyMessage = "Validation service error: " + baseMessage;
                boolean isTimeoutOrConnection = baseMessage.toLowerCase().contains("timeout")
                        || baseMessage.toLowerCase().contains("connection refused")
                        || baseMessage.toLowerCase().contains("connect");
                if (isTimeoutOrConnection) {
                    userFriendlyMessage += ". Ensure the bulk validation service is running at " + PYTHON_SERVICE_URL;
                }
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", userFriendlyMessage);
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, userFriendlyMessage, 0, 0, 0, 0);
                sendErrorResponse(response, userFriendlyMessage, 503);
                return;
            }

            String validationStatus = validationResponse.get("status").getAsString();
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Validating", 20, "Validation in progress...");

            int validationErrorCount = 0;
            JsonArray validatedData;
            if ("error".equals(validationStatus)) {
                // Hard validation failure (e.g. file unreadable): fail and do not process any rows
                JsonArray errors = validationResponse.has("errors") ? validationResponse.getAsJsonArray("errors") : new JsonArray();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Validation failed");
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Validation failed with " + errors.size() + " errors", 0, 0, 0, errors.size());
                try {
                    storeValidationErrorsInReport(jobId, entityName, validationResponse, errors);
                } catch (SQLException e) {
                    logger.warn("Failed to store validation errors in report: {}", e.getMessage());
                }
                sendValidationErrorResponse(response, jobId, referenceName, validationResponse, errors, entityName);
                return;
            }

            if ("invalid".equals(validationStatus)) {
                // Some rows have validation errors; store them in report
                JsonArray errors = validationResponse.getAsJsonArray("errors");
                try {
                    storeValidationErrorsInReport(jobId, entityName, validationResponse, errors);
                } catch (SQLException e) {
                    logger.warn("Failed to store validation errors in report: {}", e.getMessage());
                }

                if (cancelOnWarning) {
                    // User chose "Cancel on Warning": fail job and do not process any rows
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Validation failed");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Validation failed with " + errors.size() + " errors", 0, 0, 0, errors.size());
                    sendValidationErrorResponse(response, jobId, referenceName, validationResponse, errors, entityName);
                    return;
                }

                // User chose "Continue on Warning": process only valid rows from "data"
                validatedData = validationResponse.has("data") ? validationResponse.getAsJsonArray("data") : null;
                if (validatedData == null || validatedData.size() == 0) {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Validation failed - no valid rows to process");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Validation failed with " + errors.size() + " errors; no valid rows", 0, 0, 0, errors.size());
                    sendValidationErrorResponse(response, jobId, referenceName, validationResponse, errors, entityName);
                    return;
                }
                validationErrorCount = errors.size();
                logger.info("Continue on Warning: processing {} valid rows despite {} validation error(s)", validatedData.size(), errors.size());
            } else {
                validatedData = validationResponse.getAsJsonArray("data");
                if (validatedData == null) {
                    validatedData = new JsonArray();
                }
            }

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

            int insertedCount = 0;
            int updatedCount = 0;
            int deletedCount = 0;
            int failedCount = validationErrorCount;
            java.util.Map<String, Integer> batchNameToId = new java.util.HashMap<>();

            Connection conn = null;
            BulkUploadNameValidator nameValidator = new BulkUploadNameValidator();
            try {
                conn = DatabaseConnection.getConnection();
                // Transaction strategy: cancelOnWarning = single transaction; !cancelOnWarning = per-row commits
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
                        int progressPercent = 30 + (int)((i / (double)totalRows) * 65);
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                            String.format("Processing row %d of %d", i + 1, totalRows),
                            insertedCount, updatedCount, deletedCount, failedCount);
                    }

                    // Start row-level transaction when using per-row commits
                    if (!cancelOnWarning) {
                        conn.setAutoCommit(false);
                    }

                    try {
                        if ("INSERT".equals(operation)) {
                            // Extract fields from validated data
                            String primaryName = getString(rowData, "PrimaryName");
                            
                            // Validate name uniqueness within segment
                            if (primaryName != null && !primaryName.trim().isEmpty() && BulkUploadNameValidator.hasNameColumn("BusinessArea")) {
                                Long segmentId = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    try {
                                        segmentId = determineSegmentId(segmentMode, segment, rowData);
                                    } catch (SQLException e) {
                                        logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                                    }
                                }
                                try {
                                    nameValidator.validateNameUniqueInSegment("BusinessArea", primaryName, segmentId, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }
                            
                            String description = getString(rowData, "Description");
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentName = getString(rowData, "Parent Business Area");
                                if (parentName != null && !parentName.trim().isEmpty()) {
                                    Integer fromBatch = batchNameToId.get(parentName.trim().toLowerCase());
                                    if (fromBatch != null) parentId = fromBatch;
                                    else parentId = getBusinessAreaIdByName(conn, parentName.trim());
                                }
                            }
                            String parentNameRequired = getString(rowData, "Parent Business Area");
                            if (parentNameRequired != null && !parentNameRequired.trim().isEmpty() && parentId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Parent Business Area '" + parentNameRequired.trim()
                                        + "' could not be resolved. Place the parent row above this row in the file, or use an existing catalogue name.");
                            }
                            Integer statusId = getInteger(rowData, "Status_ID");
                            if (statusId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": BUDG Status is required.");
                            }
                            Integer lifecycleId = getInteger(rowData, "Lifecycle_ID");
                            if (lifecycleId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Lifecycle is required.");
                            }
                            Integer isPublicId = getInteger(rowData, "Is_Public_ID");
                            if (isPublicId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": BUDG Viewing is required.");
                            }

                            // Create savepoint before INSERT operation to allow rollback on failure
                            java.sql.Savepoint rowSavepoint = conn.setSavepoint("row_" + rowNumber);
                            
                            try {
                                // Create business area via DAO
                                String insertSql = """
                                    INSERT INTO business_area (PrimaryName, Description, Parent_ID, Status, Lifecycle, Is_Public, 
                                        CreateDatetime, LastUpdateDatetime, createdby_id, LastUpdate_UserID)
                                    VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?)
                                """;
                                
                                int businessAreaId;
                                try (java.sql.PreparedStatement ps = conn.prepareStatement(insertSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                                    ps.setString(1, primaryName);
                                    ps.setString(2, description);
                                    if (parentId != null) ps.setInt(3, parentId); else ps.setNull(3, java.sql.Types.INTEGER);
                                    if (statusId != null) ps.setInt(4, statusId); else ps.setNull(4, java.sql.Types.INTEGER);
                                    if (lifecycleId != null) ps.setInt(5, lifecycleId); else ps.setNull(5, java.sql.Types.INTEGER);
                                    if (isPublicId != null) ps.setInt(6, isPublicId); else ps.setNull(6, java.sql.Types.INTEGER);
                                    ps.setInt(7, userId); // createdby_id
                                    ps.setInt(8, userId); // LastUpdate_UserID
                                    
                                    ps.executeUpdate();
                                    try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                                        if (keys.next()) {
                                            businessAreaId = keys.getInt(1);
                                        } else {
                                            throw new SQLException("Failed to create business area, no ID obtained");
                                        }
                                    }
                                }

                                if (primaryName != null && !primaryName.trim().isEmpty()) {
                                    batchNameToId.put(primaryName.trim().toLowerCase(), businessAreaId);
                                }
                                
                                // Create audit records
                                String userName = getUserName(userId);
                                businessAreaDAO.createBusinessAreaAuditRecords(businessAreaId, userName, conn);
                                
                                // User resolution: use User Email from file if provided, otherwise use uploader
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
                                
                                // Create stakeholder if governance role provided (role scoped to Business Area module; person on admin list when configured)
                                try {
                                    Integer governanceRoleId = getInteger(rowData, "Governance Role_ID");
                                    if (governanceRoleId == null) {
                                        String grName = getString(rowData, "Governance Role");
                                        if (grName != null && !grName.trim().isEmpty()) {
                                            try {
                                                governanceRoleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, grName, "Business Area");
                                            } catch (IllegalArgumentException e) {
                                                throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
                                            }
                                        }
                                    }
                                    if (governanceRoleId == null) {
                                        governanceRoleId = getDefaultBusinessAreaOwnerRole();
                                    }
                                    if (governanceRoleId != null) {
                                        RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, stakeholderUserId,
                                                governanceRoleId, "Business Area", rowNumber);
                                    }
                                    if (governanceRoleId != null) {
                                        // Prepare stakeholder data
                                        Map<String, Object> stakeholderData = new HashMap<>();
                                        stakeholderData.put("userId", stakeholderUserId);
                                        stakeholderData.put("roleId", governanceRoleId);
                                        
                                        // Use DAO to create object_x_people (always creates new)
                                        int objectXPeopleId = businessAreaDAO.createObjectXPeople(conn, stakeholderData, userId);
                                        
                                        // Use DAO to link (with duplicate prevention)
                                        businessAreaDAO.linkStakeholderToBusinessArea(conn, businessAreaId, objectXPeopleId, userId);
                                        
                                        // Create audit records
                                        String stakeholderName = getUserName(stakeholderUserId);
                                        businessAreaDAO.createStakeholderAuditRecords(businessAreaId, stakeholderName, stakeholderName, governanceRoleId);
                                    }
                                } catch (Exception e) {
                                    logger.error("Error linking stakeholder to business area {}: {}", businessAreaId, e.getMessage(), e);
                                }
                                
                                // Assign object to segment (for INSERT operations when segmentation is enabled)
                                Long segmentIdToAssign = null;
                                String segmentNameFromExcel = getString(rowData, "Segment");
                                logger.info("Row {}: segmentMode='{}', selectedSegment='{}', Segment from Excel='{}'", 
                                    rowNumber, segmentMode, segment, segmentNameFromExcel);
                                
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
                                    logger.info("Row {}: Determined segmentId={} for segmentMode='{}'", 
                                        rowNumber, segmentIdToAssign, segmentMode);
                                } else {
                                    // Check if Segment column exists in Excel even when segmentMode is null
                                    if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                                        segmentIdToAssign = getSegmentIdByName(segmentNameFromExcel.trim());
                                        logger.info("Row {}: segmentMode is null, using Segment from Excel='{}', segmentId={}", 
                                            rowNumber, segmentNameFromExcel, segmentIdToAssign);
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
                                            parentId, segmentIdToAssign.intValue(), "Business Area");
                                        if (!result.isValid) {
                                            throw new RuntimeException("Segment validation failed: " + result.message);
                                        }
                                    }
                                }
                                
                                if (segmentIdToAssign != null) {
                                    try {
                                        // Validate user has access to this segment before assignment
                                        validateSegmentAccess(userId, segmentIdToAssign, rowNumber);
                                        
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) businessAreaId, 
                                            "Business Area", 
                                            segmentIdToAssign, 
                                            userId
                                        );
                                        logger.info("Assigned Business Area {} to Segment {}", businessAreaId, segmentIdToAssign);
                                    } catch (Exception segEx) {
                                        // Segment assignment failure = row failure (BUDG behavior)
                                        logger.error("Failed to assign Business Area {} to segment: {}", businessAreaId, segEx.getMessage(), segEx);
                                        throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                    }
                                }
                                
                                // Save custom field values
                                try {
                                    CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                        businessAreaId, 
                                        "Business Area", 
                                        rowData, 
                                        userId,
                                        conn
                                    );
                                } catch (Exception cfEx) {
                                    logger.error("Failed to save custom fields for Business Area {}: {}", businessAreaId, cfEx.getMessage(), cfEx);
                                    throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                                }
                                
                                // All operations succeeded - increment count and create success report
                                insertedCount++;
                                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                                jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    entityName + " created successfully", "info");
                                    
                            } catch (Exception e) {
                                // Rollback to savepoint to undo the INSERT and all subsequent operations
                                try {
                                    conn.rollback(rowSavepoint);
                                    logger.info("Rolled back row {} to savepoint due to error: {}", rowNumber, e.getMessage());
                                } catch (SQLException rollbackEx) {
                                    logger.error("Failed to rollback to savepoint for row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                                }
                                // Re-throw the exception to be caught by outer catch block
                                throw e;
                            }

                        } else if ("UPDATE".equals(operation)) {
                            List<String> identityCols = Arrays.asList("ID", "PrimaryName");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols, "Business Area", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByName = null;
                            Integer idVal = BulkUploadUtil.getInteger(rowData, "ID");
                            if (idVal != null) {
                                idById = getBusinessAreaIdById(conn, idVal);
                            }
                            String nameVal = BulkUploadUtil.getString(rowData, "PrimaryName");
                            if (nameVal != null && !nameVal.trim().isEmpty()) {
                                idByName = getBusinessAreaIdByName(conn, nameVal.trim());
                            }
                            int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                            Integer businessAreaId = coalesce(idById, idByName);
                            if (businessAreaId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": No business area found for the provided identity (ID or PrimaryName).");
                            }
                            if (filledIdentity >= 2) {
                                java.util.Set<Integer> ids = new java.util.HashSet<>();
                                if (idById != null) ids.add(idById);
                                if (idByName != null) ids.add(idByName);
                                if (ids.size() > 1) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": ID and PrimaryName refer to different business areas.");
                                }
                            }
                            
                            // Get old business area
                            String selectSql = "SELECT * FROM business_area WHERE ID = ?";
                            JsonObject oldData = new JsonObject();
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(selectSql)) {
                                ps.setInt(1, businessAreaId);
                                try (java.sql.ResultSet rs = ps.executeQuery()) {
                                    if (!rs.next()) {
                                        throw new IllegalArgumentException("Business Area with ID " + businessAreaId + " does not exist");
                                    }
                                    oldData.addProperty("PrimaryName", rs.getString("PrimaryName"));
                                    oldData.addProperty("Description", rs.getString("Description"));
                                    oldData.addProperty("Parent_ID", rs.getInt("Parent_ID"));
                                    oldData.addProperty("Status", rs.getInt("Status"));
                                    oldData.addProperty("Lifecycle", rs.getInt("Lifecycle"));
                                    oldData.addProperty("Is_Public", rs.getInt("Is_Public"));
                                }
                            }
                            
                            // Build update with new values or keep old (safe for DB null -> JsonNull)
                            String oldPrimary = getStringFromJsonSafe(oldData, "PrimaryName");
                            String primaryName = getStringOrDefault(rowData, "PrimaryName", oldPrimary != null ? oldPrimary : "");
                            String oldDesc = getStringFromJsonSafe(oldData, "Description");
                            String description = getStringOrDefault(rowData, "Description", oldDesc != null ? oldDesc : "");
                            Integer parentId = coalesce(getInteger(rowData, "Parent_ID"), treatZeroAsNull(getIntFromJsonSafe(oldData, "Parent_ID")));
                            Integer statusId = coalesce(getInteger(rowData, "Status_ID"), treatZeroAsNull(getIntFromJsonSafe(oldData, "Status")));
                            Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"), treatZeroAsNull(getIntFromJsonSafe(oldData, "Lifecycle")));
                            Integer isPublicId = coalesce(getInteger(rowData, "Is_Public_ID"), treatZeroAsNull(getIntFromJsonSafe(oldData, "Is_Public")));
                            
                            String updateSql = """
                                UPDATE business_area 
                                SET PrimaryName = ?, Description = ?, Parent_ID = ?, Status = ?, Lifecycle = ?, Is_Public = ?, 
                                    LastUpdateDatetime = NOW(), LastUpdate_UserID = ?
                                WHERE ID = ?
                            """;
                            
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(updateSql)) {
                                ps.setString(1, primaryName);
                                ps.setString(2, description);
                                if (parentId != null) ps.setInt(3, parentId); else ps.setNull(3, java.sql.Types.INTEGER);
                                if (statusId != null) ps.setInt(4, statusId); else ps.setNull(4, java.sql.Types.INTEGER);
                                if (lifecycleId != null) ps.setInt(5, lifecycleId); else ps.setNull(5, java.sql.Types.INTEGER);
                                if (isPublicId != null) ps.setInt(6, isPublicId); else ps.setNull(6, java.sql.Types.INTEGER);
                                ps.setInt(7, userId);
                                ps.setInt(8, businessAreaId);
                                ps.executeUpdate();
                            }
                            
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
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) businessAreaId, "Business Area");
                                int effectiveChildSegmentId = (newSegmentIdForParent != null) ? newSegmentIdForParent.intValue()
                                        : (currentSegmentId != null ? currentSegmentId.intValue() : 1);
                                SegmentValidationService parentValidator = new SegmentValidationService();
                                SegmentValidationService.ValidationResult parentResult = parentValidator.validateParentChildSegment(
                                        parentId, effectiveChildSegmentId, "Business Area");
                                if (!parentResult.isValid) {
                                    throw new RuntimeException("Parent segment validation failed: " + (parentResult.message != null ? parentResult.message : "Parent and object are in different private segments."));
                                }
                            }
                            
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
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) businessAreaId, "Business Area");
                                    
                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            businessAreaId, newSegmentId.intValue(), "Business Area", parentId);
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Segment validation failed: " + (result.message != null ? result.message : "Segment move not allowed."));
                                        }
                                        
                                        // Validate user has access to this segment before assignment
                                        validateSegmentAccess(userId, newSegmentId, rowNumber);
                                        
                                        // Assign new segment
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) businessAreaId, 
                                            "Business Area", 
                                            newSegmentId, 
                                            userId
                                        );
                                        logger.info("Assigned Business Area {} to Segment {} (updated)", businessAreaId, newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Business Area {} to segment: {}", businessAreaId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Create audit for update
                            String userName = getUserName(userId);
                            businessAreaDAO.createBusinessAreaAuditRecords(businessAreaId, userName, conn);
                            
                            // Save custom field values (businessAreaId already defined above)
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    businessAreaId, 
                                    "Business Area", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Business Area {}: {}", businessAreaId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            updatedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " updated successfully", "info");

                        } else if ("DELETE".equals(operation)) {
                            int businessAreaId = rowData.get("ID").getAsInt();
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "business-area", businessAreaId);
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
                                String deleteSql = "UPDATE business_area SET deletedatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
                                try (java.sql.PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                                    ps.setInt(1, userId);
                                    ps.setInt(2, businessAreaId);
                                    ps.executeUpdate();
                                }
                                deletedCount++;
                                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                                jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    entityName + " deleted successfully", "info");
                            }
                        }

                        // Commit this row when using per-row commits
                        if (!cancelOnWarning) {
                            conn.commit();
                            logger.debug("Committed row {} successfully", rowNumber);
                        }

                    } catch (Exception e) {
                        logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                        failedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "failed", rowNumber);
                        String msg = e.getMessage() != null ? e.getMessage() : "";
                        String messageCode = msg.contains("Parent segment validation failed") ? "PARENT_SEGMENT_VALIDATION_FAILED"
                                : msg.contains("Segment validation failed") ? "SEGMENT_VALIDATION_FAILED"
                                : msg.contains("Failed to assign object to segment") ? "SEGMENT_ASSIGNMENT_FAILED"
                                : "PROCESSING_ERROR";
                        jobDAO.createJobReportItemMessage(reportItemId, messageCode,
                            msg, "error");

                        if (cancelOnWarning) {
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

                // Final commit only for all-or-nothing mode
                if (cancelOnWarning) {
                    conn.commit();
                    logger.info("Transaction committed successfully");
                }
            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        logger.error("Error during rollback", rollbackEx);
                    }
                }
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Processing error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Processing error: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);
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
                    "Processing failed: all rows failed",
                    insertedCount, updatedCount, deletedCount, failedCount);
                
                logger.info("Bulk upload job {} failed: {} inserted, {} updated, {} deleted, {} failed",
                    jobId, insertedCount, updatedCount, deletedCount, failedCount);
                
                sendNotification(userId, "Business Area", uploadOption, insertedCount, updatedCount,
                                deletedCount, failedCount, false, "All rows failed during processing", jobId);
            } else if (isPartiallyCompleted) {
                jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount); // Only successful operations
                jobDAO.updateJobProgress(jobId, "Partially Completed",
                    String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                    "Processing complete with some failures",
                    insertedCount, updatedCount, deletedCount, failedCount);
                
                logger.info("Bulk upload job {} partially completed: {} inserted, {} updated, {} deleted, {} failed",
                    jobId, insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send notification for partial completion
                sendNotification(userId, "Business Area", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, true, null, jobId);
            } else {
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount);
                jobDAO.updateJobProgress(jobId, "Completed",
                    String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    "Bulk upload completed successfully",
                    insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send success notification
                sendNotification(userId, "Business Area", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, true, null, jobId);
            }

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("inserted", insertedCount);
            successResponse.addProperty("updated", updatedCount);
            successResponse.addProperty("deleted", deletedCount);
            successResponse.addProperty("failed", failedCount);
            successResponse.addProperty("report_url", "/api/bulk/businessarea/report/" + jobId);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(successResponse));

        } catch (Exception e) {
            logger.error("Error during bulk upload", e);
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
                                sendNotification(userId, "Business Area", uploadOption, 0, 0, 0, 0, 
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
            n.setFacetType("business_area");
            n.setObjectId(jobId);
            n.setRead(false);
            
            dao.create(n);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Store validation errors in Job_Report_Item and optionally mark non-error rows as skipped.
     */
    private void storeValidationErrorsInReport(int jobId, String entityName, JsonObject validationResponse, JsonArray errors) throws SQLException {
        java.util.Set<Integer> rowsWithErrors = new java.util.HashSet<>();
        for (int i = 0; i < errors.size(); i++) {
            JsonObject error = errors.get(i).getAsJsonObject();
            int rowNum = error.get("row").getAsInt();
            rowsWithErrors.add(rowNum);
            int reportItemId = jobDAO.createJobReportItem(
                jobId,
                error.get("field").getAsString(),
                "error",
                rowNum
            );
            jobDAO.createJobReportItemMessage(
                reportItemId,
                error.get("error_code").getAsString(),
                error.get("message").getAsString(),
                "error"
            );
        }
        int totalRowsFromFile = validationResponse.has("total_rows") ? validationResponse.get("total_rows").getAsInt() : 0;
        for (int rowNumber = 2; rowNumber <= totalRowsFromFile + 1; rowNumber++) {
            if (!rowsWithErrors.contains(rowNumber)) {
                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "skipped", rowNumber);
                jobDAO.createJobReportItemMessage(reportItemId, "SKIPPED",
                    "Not processed due to validation errors in other rows.", "warning");
            }
        }
    }

    /**
     * Send JSON error response for validation failure.
     */
    private void sendValidationErrorResponse(HttpServletResponse response, int jobId, String referenceName,
            JsonObject validationResponse, JsonArray errors, String entityName) throws IOException {
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("status", "failed");
        errorResponse.addProperty("job_id", jobId);
        errorResponse.addProperty("reference_name", referenceName);
        errorResponse.addProperty("message", validationResponse.has("message") ? validationResponse.get("message").getAsString() : "Validation failed");
        errorResponse.addProperty("inserted", 0);
        errorResponse.addProperty("updated", 0);
        errorResponse.addProperty("deleted", 0);
        errorResponse.addProperty("failed", errors.size());
        errorResponse.add("errors", errors);
        errorResponse.addProperty("report_url", "/api/bulk/businessarea/report/" + jobId);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write(gson.toJson(errorResponse));
    }

    // Helper methods
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        for (String content : contentDisposition.split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return "unknown";
    }

    private String generateShortReferenceName() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'BA-%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("BA-")) {
                        try {
                            String numberPart = refName.substring(3);
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
        return "BA-" + nextNumber;
    }

    private String determineJobType(String entityName, String uploadOption) {
        return "bulk_upload";
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }

    // Data extraction helpers
    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private String getStringOrDefault(JsonObject obj, String key, String def) {
        String v = getString(obj, key);
        return v != null ? v : def;
    }

    private Integer getInteger(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : null;
    }

    /** Safe get string from JsonObject; returns null if key missing or value is JsonNull (avoids getAsString on JsonNull). */
    private static String getStringFromJsonSafe(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }

    /** Safe get int from JsonObject; returns null if key missing or value is JsonNull (avoids getAsInt on JsonNull). */
    private static Integer getIntFromJsonSafe(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsInt();
    }

    @SafeVarargs
    private <T> T coalesce(T... values) {
        if (values == null) return null;
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private Integer treatZeroAsNull(Integer value) {
        return (value != null && value == 0) ? null : value;
    }

    // Lookup helpers
    private Integer getFirstStatusId() {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT ID FROM status ORDER BY ID ASC LIMIT 1")) {
            if (rs.next()) return rs.getInt("ID");
        } catch (SQLException e) {
            logger.error("Error getting first status ID: {}", e.getMessage());
        }
        return null;
    }

    private Integer getFirstLookupId(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT ID FROM " + tableName + " ORDER BY ID ASC LIMIT 1")) {
            if (rs.next()) return rs.getInt("ID");
        } catch (SQLException e) {
            logger.error("Error getting first lookup ID from table {}: {}", tableName, e.getMessage());
        }
        return null;
    }

    private Integer getFirstViewingId() {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT id FROM viewing ORDER BY id ASC LIMIT 1")) {
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            logger.error("Error getting first viewing ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get Business Area ID by ID (checks existence).
     */
    private Integer getBusinessAreaIdById(Connection conn, int id) {
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "SELECT ID FROM business_area WHERE ID = ? AND (deletedatetime IS NULL) LIMIT 1")) {
            ps.setInt(1, id);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID");
            }
        } catch (SQLException e) {
            logger.error("Error getting business area by ID: {}", id, e);
        }
        return null;
    }

    /**
     * Get Business Area ID by PrimaryName using the given connection (same transaction).
     */
    private Integer getBusinessAreaIdByName(Connection conn, String name) {
        if (name == null || name.trim().isEmpty()) return null;
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "SELECT ID FROM business_area WHERE LOWER(PrimaryName) = LOWER(?) AND (deletedatetime IS NULL) LIMIT 1")) {
            ps.setString(1, name.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID");
            }
        } catch (SQLException e) {
            logger.error("Error getting business area ID by name: {}", name, e);
        }
        return null;
    }

    private Integer getRoleIdByName(String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) return null;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM object_role WHERE LOWER(primaryname) = LOWER(?) LIMIT 1")) {
            ps.setString(1, roleName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
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
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM people WHERE Email = ? AND Deleted_date IS NULL LIMIT 1")) {
            ps.setString(1, email.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
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

    // Stakeholder methods now handled by BusinessAreaDAO

    private Integer getDefaultBusinessAreaOwnerRole() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String moduleQuery = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER('Business Area') LIMIT 1";
            try (java.sql.PreparedStatement moduleStmt = conn.prepareStatement(moduleQuery);
                 java.sql.ResultSet moduleRs = moduleStmt.executeQuery()) {
                
                if (moduleRs.next()) {
                    int moduleId = moduleRs.getInt("id");
                    String roleQuery = "SELECT id FROM object_role WHERE module = ? ORDER BY id ASC LIMIT 1";
                    try (java.sql.PreparedStatement roleStmt = conn.prepareStatement(roleQuery)) {
                        roleStmt.setInt(1, moduleId);
                        try (java.sql.ResultSet roleRs = roleStmt.executeQuery()) {
                            if (roleRs.next()) {
                                return roleRs.getInt("id");
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default Business Area Owner role: {}", e.getMessage(), e);
        }
        return null;
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
            // Even if segmentMode is null, check if Segment column exists in Excel
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                logger.debug("segmentMode is null, using Segment from Excel='{}', segmentId={}", segmentNameFromExcel, segmentId);
                return segmentId;
            }
            return null;
        }
        
        String mode = segmentMode.trim();
        
        if ("MULTIPLE".equalsIgnoreCase(mode)) {
            // MULTIPLE mode: Use segment from Excel if provided, otherwise use selectedSegment from UI
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                // User specified a segment in Excel, use it (overrides UI selection)
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug("MULTIPLE mode: Segment specified in Excel='{}', using segmentId={} (overrides UI selection)", 
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
                        logger.debug("MULTIPLE mode: No Segment in Excel, using selectedSegment ID={} from UI", segmentId);
                        return segmentId;
                    } catch (NumberFormatException e) {
                        // Not a number, try as name
                        Long segmentId = getSegmentIdByName(selectedSegment.trim());
                        if (segmentId != null) {
                            logger.debug("MULTIPLE mode: No Segment in Excel, using selectedSegment name='{}' (segmentId={}) from UI", 
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
            throw new IllegalArgumentException("Segment is required when 'Multiple' mode is selected. Either specify it in Excel or select it in the UI");
        } else if ("ENTERPRISE".equalsIgnoreCase(mode)) {
            // Check if Segment column exists in Excel - if provided, use it; otherwise default to Enterprise
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                // User specified a segment in Excel, use it instead of defaulting to Enterprise
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug("ENTERPRISE mode: Segment specified in Excel='{}', using segmentId={} instead of Enterprise", 
                    segmentNameFromExcel, segmentId);
                return segmentId;
            }
            // No segment specified in Excel, default to Enterprise
            logger.debug("ENTERPRISE mode: No Segment in Excel, defaulting to Enterprise (segmentId=1)");
            return 1L;
        } else if ("SPECIFIC".equalsIgnoreCase(mode)) {
            // SPECIFIC mode: Use segment from Excel if provided (overrides UI), otherwise use selectedSegment from UI
            if (segmentNameFromExcel != null && !segmentNameFromExcel.trim().isEmpty()) {
                // User specified a segment in Excel, use it (overrides UI selection)
                Long segmentId = getSegmentIdByName(segmentNameFromExcel.trim());
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel + "' does not exist");
                }
                logger.debug("SPECIFIC mode: Segment specified in Excel='{}', using segmentId={} (overrides UI selection)", 
                    segmentNameFromExcel, segmentId);
                return segmentId;
            }
            // No segment specified in Excel, use the selected segment from UI (applies to all rows)
            if (selectedSegment == null || selectedSegment.trim().isEmpty()) {
                throw new IllegalArgumentException("Segment ID is required when 'Specific' mode is selected");
            }
            try {
                Long segmentId = Long.parseLong(selectedSegment.trim());
                logger.debug("SPECIFIC mode: No Segment in Excel, using selected segmentId={} from UI (applies to all rows)", segmentId);
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
            return; // No segment to validate
        }
        
        // Super Admin has access to all segments
        if (SegmentAccessService.isSuperAdmin(userId)) {
            logger.debug("Row {}: User {} is Super Admin, has access to all segments", rowNumber, userId);
            return;
        }
        
        // Get accessible segment IDs for the user
        java.util.Set<Integer> accessibleSegmentIds = SegmentAccessService.getAccessibleSegmentIds(userId);
        
        // Enterprise (ID=1) is always accessible
        if (segmentId == 1L) {
            logger.debug("Row {}: Segment is Enterprise (ID=1), always accessible", rowNumber);
            return;
        }
        
        // Check if segment is in accessible list
        if (!accessibleSegmentIds.contains(segmentId.intValue())) {
            // Get segment name for error message
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

