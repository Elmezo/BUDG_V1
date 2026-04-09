package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.ClientDAO;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Client;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.bulk.common.BulkUploadParentValidator;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet("/api/bulk/client/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,
    maxFileSize = 1024 * 1024 * 10,
    maxRequestSize = 1024 * 1024 * 50
)
public class ClientBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ClientBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static final Pattern SEGMENT_ID_PATTERN = Pattern.compile("segment ID\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("client"); }

    private final JobDAO jobDAO = new JobDAO();
    private final ClientDAO clientDAO = new ClientDAO();

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

        logger.info("Client bulk upload request received");

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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Client";
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
            String fileName = "client_" + timestamp + "_" + uuid + ".xlsx";

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
                validationResponse = HttpClientUtil.postJsonGetJson(PYTHON_SERVICE_URL, validationRequest);
            } catch (IOException e) {
                String userFriendlyMessage = "Validation service error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", userFriendlyMessage);
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, userFriendlyMessage, 0, 0, 0, 0);
                sendErrorResponse(response, userFriendlyMessage, 503);
                return;
            }

            String validationStatus = validationResponse.get("status").getAsString();
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Validating", 20, "Validation in progress...");

            // Determine cancelOnWarning setting early so it's available for validation error handling
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);

            if ("error".equals(validationStatus) || "invalid".equals(validationStatus)) {
                JsonArray errors = validationResponse.getAsJsonArray("errors");
                
                // Store validation errors in Job_Report_Item
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

                // Handle validation errors based on cancelOnWarning setting
                if ("error".equals(validationStatus)) {
                    // Service-level error - always fail
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Validation failed");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Validation failed with " + errors.size() + " errors", 0, 0, 0, errors.size());

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
                    return;
                } else if ("invalid".equals(validationStatus)) {
                    // Validation errors found - check cancelOnWarning
                    JsonArray validatedData = validationResponse.has("data") ? validationResponse.getAsJsonArray("data") : new JsonArray();
                    
                    if (cancelOnWarning) {
                        // Stop processing if cancelOnWarning is enabled
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Validation failed - Cancel on Warning enabled");
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                "Validation failed with " + errors.size() + " errors (Cancel on Warning enabled)", 0, 0, 0, errors.size());

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
                        return;
                    } else if (validatedData.size() == 0) {
                        // No valid rows to process - fail even if cancelOnWarning is false
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Validation failed - no valid rows to process");
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                "Validation failed with " + errors.size() + " errors - no valid rows", 0, 0, 0, errors.size());

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
                        return;
                    }
                    // If cancelOnWarning is false and there are valid rows, continue processing
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
            jobDAO.updateJobProgress(jobId, "Processing", "Processing " + totalRows + " valid row(s) from file");

            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                "Validation complete. Processing " + totalRows + " valid row(s) (" + totalRowsForJob + " data row(s) in file)...");

            int insertedCount = 0;
            int updatedCount = 0;
            int deletedCount = 0;
            int failedCount = 0;

            // Store validation errors if any, to include in final response
            JsonArray validationErrors = new JsonArray();
            if ("invalid".equals(validationStatus) && !cancelOnWarning) {
                // If we're continuing despite validation errors, store them for final response
                if (validationResponse.has("errors")) {
                    validationErrors = validationResponse.getAsJsonArray("errors");
                    // Count validation errors as failed
                    failedCount += validationErrors.size();
                }
            }

            Connection conn = null;
            BulkUploadNameValidator nameValidator = new BulkUploadNameValidator();
            java.util.Map<String, Integer> batchNameToId = new java.util.HashMap<>();
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
                            Client client = new Client();
                            
                            // PrimaryName (required)
                            String name = getString(rowData, "PrimaryName");
                            if (name == null || name.trim().isEmpty()) {
                                throw new IllegalArgumentException("PrimaryName is required");
                            }
                            
                            // Validate name uniqueness within segment
                            if (BulkUploadNameValidator.hasNameColumn("Client")) {
                                Long segmentId = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    try {
                                        segmentId = determineSegmentId(segmentMode, segment, rowData);
                                    } catch (SQLException e) {
                                        logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                                    }
                                }
                                try {
                                    nameValidator.validateNameUniqueInSegment("Client", name, segmentId, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }
                            
                            client.setPrimaryName(name);
                            
                            // LongName (optional)
                            client.setLongName(getString(rowData, "LongName"));
                            
                            // Description (optional)
                            client.setDescription(getString(rowData, "Description"));
                            
                            // Parent Client resolution (by PrimaryName; resolve from batch first if parent in same file)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentName = getString(rowData, "Parent Client Name");
                                if (parentName != null && !parentName.trim().isEmpty()) {
                                    Integer fromBatch = batchNameToId.get(parentName.trim().toLowerCase());
                                    if (fromBatch != null) {
                                        parentId = fromBatch;
                                    } else {
                                        Client parent = getClientByPrimaryName(parentName.trim());
                                        if (parent != null) {
                                            parentId = parent.getId();
                                        } else {
                                            throw new RuntimeException("Parent client not found with name: " + parentName.trim());
                                        }
                                    }
                                }
                            }
                            client.setParentId(parentId);
                            
                            // Lifecycle (optional, default: first from client_lifecycle)
                            Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"),
                                    getLookupIdByName("client_lifecycle", getString(rowData, "Lifecycle")));
                            if (lifecycleId == null) lifecycleId = getFirstLookupId("client_lifecycle");
                            client.setLifecycle(lifecycleId);
                            
                            // Status (optional, default: first from status)
                            Integer statusId = coalesce(getInteger(rowData, "Status_ID"),
                                    getLookupIdByName("status", getString(rowData, "BUDG Status")));
                            if (statusId == null) statusId = getFirstStatusId();
                            client.setStatus(statusId);
                            
                            // IsPublic/Viewing (optional, default: first from viewing)
                            Integer viewingId = getInteger(rowData, "IsPublic_ID");
                            if (viewingId == null) viewingId = getInteger(rowData, "is_public");
                            if (viewingId == null) viewingId = getInteger(rowData, "Is_Public_ID");
                            if (viewingId == null) viewingId = getViewingIdByName(getString(rowData, "BUDG Viewing"));
                            if (viewingId == null) viewingId = getFirstViewingId();
                            client.setIsPublic(viewingId);
                            
                            // User ID
                            client.setLastUpdateUserID(userId);
                            
                            // Create client
                            Client created = clientDAO.createClient(client);
                            
                            // Add to batch map for parent-in-same-file resolution
                            if (name != null && !name.trim().isEmpty()) {
                                batchNameToId.put(name.trim().toLowerCase(), created.getId());
                            }
                            
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
                            
                            // Create audit records
                            String userName = getUserName(stakeholderUserId);
                            clientDAO.createClientAuditRecords(created.getId(), userName);
                            clientDAO.createClientAuditRecord(created.getId());
                            
                            // Create stakeholder record (role must belong to Client module; person must be on admin role list when configured)
                            try {
                                Integer governanceRoleId = getInteger(rowData, "Governance Role_ID");
                                if (governanceRoleId == null) {
                                    String grName = getString(rowData, "Governance Role");
                                    if (grName != null && !grName.trim().isEmpty()) {
                                        try {
                                            governanceRoleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, grName, "Client");
                                        } catch (IllegalArgumentException e) {
                                            throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
                                        }
                                    }
                                }
                                if (governanceRoleId == null) {
                                    governanceRoleId = getDefaultClientOwnerRole();
                                }
                                if (governanceRoleId != null) {
                                    RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, stakeholderUserId,
                                            governanceRoleId, "Client", rowNumber);
                                }
                                if (governanceRoleId != null) {
                                    // Prepare stakeholder data
                                    java.util.Map<String, Object> stakeholderData = new java.util.HashMap<>();
                                    stakeholderData.put("userId", stakeholderUserId);
                                    stakeholderData.put("roleId", governanceRoleId);

                                    // Use DAO to create object_x_people (always creates new)
                                    int objectXPeopleId = clientDAO.createObjectXPeople(conn, stakeholderData, userId);

                                    // Use DAO to link (with duplicate prevention)
                                    clientDAO.linkStakeholderToClient(conn, created.getId(), objectXPeopleId, userId);

                                    // Create audit records
                                    clientDAO.createStakeholderAuditRecords(created.getId(), userName, userName, governanceRoleId);
                                }
                            } catch (Exception e) {
                                logger.error("Error linking stakeholder to client {}: {}", created.getId(), e.getMessage(), e);
                            }
                            
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
                                        parentId, segmentIdToAssign.intValue(), "Client");
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
                                        "Client", 
                                        segmentIdToAssign, 
                                        userId
                                    );
                                    logger.info("Assigned Client {} to Segment {}", created.getId(), segmentIdToAssign);
                                } catch (Exception segEx) {
                                    // Segment assignment failure = row failure (BUDG behavior)
                                    logger.error("Failed to assign Client {} to segment: {}", created.getId(), segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    created.getId(), 
                                    "Client", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Client {}: {}", created.getId(), cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            insertedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " created successfully", "info");

                        } else if ("UPDATE".equals(operation)) {
                            List<String> identityCols = Arrays.asList("ID", "Client Name");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols, "Client", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByName = null;
                            Integer idVal = getInteger(rowData, "ID");
                            if (idVal != null) {
                                Client c = clientDAO.getClientById(idVal);
                                if (c != null) idById = c.getId();
                            }
                            String nameVal = coalesce(getString(rowData, "Client Name"), getString(rowData, "PrimaryName"));
                            if (nameVal != null && !nameVal.trim().isEmpty()) {
                                Client c = getClientByPrimaryName(nameVal.trim());
                                if (c != null) idByName = c.getId();
                            }
                            int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                            Integer clientId = coalesce(idById, idByName);
                            if (clientId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": No client found for the provided identity (ID or Client Name).");
                            }
                            if (filledIdentity >= 2) {
                                java.util.Set<Integer> ids = new java.util.HashSet<>();
                                if (idById != null) ids.add(idById);
                                if (idByName != null) ids.add(idByName);
                                if (ids.size() > 1) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": ID and Client Name refer to different clients.");
                                }
                            }
                            Client oldClient = clientDAO.getClientById(clientId);
                            if (oldClient == null) {
                                throw new IllegalArgumentException("Client with ID " + clientId + " does not exist");
                            }
                            
                            Client newClient = new Client();
                            newClient.setId(clientId);
                            
                            // PrimaryName (required)
                            newClient.setPrimaryName(getStringOrDefault(rowData, "PrimaryName", oldClient.getPrimaryName()));
                            
                            // LongName (optional)
                            newClient.setLongName(getStringOrDefault(rowData, "LongName", oldClient.getLongName()));
                            
                            // Description (optional)
                            newClient.setDescription(getStringOrDefault(rowData, "Description", oldClient.getDescription()));
                            
                            // Parent Client resolution (resolve from batch first if parent in same file)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentName = getString(rowData, "Parent Client Name");
                                if (parentName != null && !parentName.trim().isEmpty()) {
                                    Integer fromBatch = batchNameToId.get(parentName.trim().toLowerCase());
                                    if (fromBatch != null) {
                                        parentId = fromBatch;
                                    } else {
                                        Client parent = getClientByPrimaryName(parentName.trim());
                                        if (parent != null) {
                                            parentId = parent.getId();
                                        } else {
                                            throw new RuntimeException("Parent client not found with name: " + parentName.trim());
                                        }
                                    }
                                } else {
                                    parentId = treatZeroAsNull(oldClient.getParentId());
                                }
                            }
                            if (parentId != null && parentId > 0) {
                                try {
                                    if (BulkUploadParentValidator.wouldCreateParentCycle(conn, clientId, parentId, "Client")) {
                                        throw new RuntimeException("Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.");
                                    }
                                } catch (SQLException e) {
                                    throw new RuntimeException("Failed to validate parent hierarchy: " + e.getMessage(), e);
                                }
                            }
                            newClient.setParentId(parentId);
                            
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
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) clientId, "Client");
                                int effectiveChildSegmentId = (newSegmentIdForParent != null) ? newSegmentIdForParent.intValue()
                                        : (currentSegmentId != null ? currentSegmentId.intValue() : 1);
                                SegmentValidationService parentValidator = new SegmentValidationService();
                                SegmentValidationService.ValidationResult parentResult = parentValidator.validateParentChildSegment(
                                        parentId, effectiveChildSegmentId, "Client");
                                if (!parentResult.isValid) {
                                    throw new RuntimeException("Parent segment validation failed: " + parentResult.message);
                                }
                            }
                            
                            // Lifecycle
                            Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"),
                                    getLookupIdByName("client_lifecycle", getString(rowData, "Lifecycle")));
                            if (lifecycleId == null) lifecycleId = treatZeroAsNull(oldClient.getLifecycle());
                            newClient.setLifecycle(lifecycleId);
                            
                            // Status
                            Integer statusId = coalesce(getInteger(rowData, "Status_ID"),
                                    getLookupIdByName("status", getString(rowData, "BUDG Status")));
                            if (statusId == null) statusId = treatZeroAsNull(oldClient.getStatus());
                            newClient.setStatus(statusId);
                            
                            // IsPublic/Viewing
                            Integer viewingId = getInteger(rowData, "IsPublic_ID");
                            if (viewingId == null) viewingId = getInteger(rowData, "is_public");
                            if (viewingId == null) viewingId = getInteger(rowData, "Is_Public_ID");
                            if (viewingId == null) viewingId = getViewingIdByName(getString(rowData, "BUDG Viewing"));
                            if (viewingId == null) viewingId = treatZeroAsNull(oldClient.getIsPublic());
                            newClient.setIsPublic(viewingId);
                            
                            newClient.setLastUpdateUserID(userId);
                            
                            // Update client
                            clientDAO.updateClient(newClient);
                            
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
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) clientId, "Client");
                                    
                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            clientId, newSegmentId.intValue(), "Client", parentId);
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Segment validation failed: " + result.message);
                                        }
                                        
                                        
                                        validateSegmentAccess(userId, newSegmentId, rowNumber);
                                        
                                        // Assign new segment
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) clientId, 
                                            "Client", 
                                            newSegmentId, 
                                            userId
                                        );
                                        logger.info("Assigned Client {} to Segment {} (updated)", clientId, newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Client {} to segment: {}", clientId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Create update audit records
                            String userName = getUserName(userId);
                            clientDAO.createClientUpdateAuditRecords(clientId, oldClient, newClient, userName);
                            clientDAO.createClientUpdateAuditSnapshot(clientId);
                            
                            // Save custom field values (clientId already defined above)
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    clientId, 
                                    "Client", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Client {}: {}", clientId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            updatedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " updated successfully", "info");

                        } else if ("DELETE".equals(operation)) {
                            int clientId = rowData.get("ID").getAsInt();
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "client", clientId);
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
                                // Soft delete
                                String deleteSql = "UPDATE client SET DeleteDatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
                                try (java.sql.PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                                    ps.setInt(1, userId);
                                    ps.setInt(2, clientId);
                                    ps.executeUpdate();
                                }
                                deletedCount++;
                                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                                jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    entityName + " deleted successfully", "info");
                            }
                        }

                        if (!cancelOnWarning) {
                            conn.commit();
                            logger.debug("Committed row {} successfully", rowNumber);
                        }

                    } catch (Exception e) {
                        logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                        failedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "failed", rowNumber);
                        String msg = e.getMessage() != null ? e.getMessage() : "";
                        msg = formatSegmentMessage(msg);
                        String messageCode = msg.contains("Parent segment validation failed") ? "PARENT_SEGMENT_VALIDATION_FAILED"
                                : msg.contains("Segment validation failed") ? "SEGMENT_VALIDATION_FAILED"
                                : msg.contains("Failed to assign object to segment") ? "SEGMENT_ASSIGNMENT_FAILED"
                                : "PROCESSING_ERROR";
                        jobDAO.createJobReportItemMessage(reportItemId, messageCode,
                            msg, "error");

                        if (cancelOnWarning) {
                            conn.rollback();
                            throw new ServletException("Processing failed at row " + rowNumber + ": " + msg);
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
                
                sendNotification(userId, "Client", uploadOption, insertedCount, updatedCount,
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
                sendNotification(userId, "Client", uploadOption, insertedCount, updatedCount, 
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
                sendNotification(userId, "Client", uploadOption, insertedCount, updatedCount, 
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
            successResponse.addProperty("report_url", "/api/bulk/client/report/" + jobId);
            
            // Include validation errors in response if processing continued despite them
            if (validationErrors.size() > 0) {
                successResponse.add("errors", validationErrors);
            }

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
                                sendNotification(userId, "Client", uploadOption, 0, 0, 0, 0, 
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
            n.setFacetType("client");
            n.setObjectId(jobId);
            n.setRead(false);
            
            dao.create(n);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }

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
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'CLI-%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("CLI-")) {
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
        return "CLI-" + nextNumber;
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

    @SafeVarargs
    private <T> T coalesce(T... values) {
        if (values == null) return null;
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Rewrites "segment ID X" to "segment 'SegmentName'" for user-facing errors.
     */
    private String formatSegmentMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        Matcher matcher = SEGMENT_ID_PATTERN.matcher(message);
        StringBuffer rewritten = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            long segmentId = Long.parseLong(matcher.group(1));
            String segmentName = null;
            try {
                segmentName = getSegmentNameById(segmentId);
            } catch (SQLException e) {
                logger.debug("Could not resolve segment name for ID {}: {}", segmentId, e.getMessage());
            }
            if (segmentName != null && !segmentName.isBlank()) {
                matcher.appendReplacement(rewritten, "segment '" + Matcher.quoteReplacement(segmentName) + "'");
                changed = true;
            }
        }
        if (!changed) {
            return message;
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private Integer treatZeroAsNull(Integer value) {
        return (value != null && value == 0) ? null : value;
    }

    private Client getClientByPrimaryName(String primaryName) {
        if (primaryName == null || primaryName.trim().isEmpty()) return null;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM client WHERE LOWER(PrimaryName) = LOWER(?) LIMIT 1")) {
            ps.setString(1, primaryName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Client client = new Client();
                    client.setId(rs.getInt("ID"));
                    client.setPrimaryName(rs.getString("PrimaryName"));
                    client.setLongName(rs.getString("LongName"));
                    client.setDescription(rs.getString("Description"));
                    client.setParentId(rs.getObject("Parent_ID") != null ? rs.getInt("Parent_ID") : null);
                    client.setLifecycle(rs.getObject("Lifecycle") != null ? rs.getInt("Lifecycle") : null);
                    client.setStatus(rs.getObject("Status") != null ? rs.getInt("Status") : null);
                    client.setIsPublic(rs.getObject("IsPublic") != null ? rs.getInt("IsPublic") : null);
                    return client;
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting client by primary name: {}", primaryName, e);
        }
        return null;
    }

    private Integer getLookupIdByName(String tableName, String primaryName) {
        if (primaryName == null || primaryName.trim().isEmpty()) return null;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM " + tableName + " WHERE PrimaryName = ? LIMIT 1")) {
            ps.setString(1, primaryName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID");
            }
        } catch (SQLException e) {
            logger.error("Error getting lookup ID by name from table {}: {}", tableName, primaryName, e);
        }
        return null;
    }

    private Integer getViewingIdByName(String viewingName) {
        if (viewingName == null || viewingName.trim().isEmpty()) return null;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM viewing WHERE LOWER(Name) = LOWER(?) LIMIT 1")) {
            ps.setString(1, viewingName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            logger.error("Error getting viewing ID by name: {}", viewingName, e);
        }
        return null;
    }

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

    private Integer getRoleIdByName(String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) return null;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM object_role WHERE primaryname = ? LIMIT 1")) {
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
                 "SELECT ID FROM people WHERE LOWER(Email) = LOWER(?) AND Deleted_date IS NULL LIMIT 1")) {
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

    // Stakeholder methods now handled by ClientDAO

    private Integer getDefaultClientOwnerRole() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String moduleQuery = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER('Client') LIMIT 1";
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
            logger.error("Error getting default Client Owner role: {}", e.getMessage(), e);
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
