package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.CommitteeDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.model.Committee;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.example.budg_v2.bulk.common.BulkUploadReferenceValidator;
import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.common.BulkUploadFacetPermissionHelper;
import com.example.budg_v2.bulk.common.BulkUploadNameValidator;
import com.example.budg_v2.bulk.roles.util.RoleHandlerUtil;
import com.example.budg_v2.bulk.common.BulkUploadUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@WebServlet("/api/bulk/committee/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,
    maxFileSize = 1024 * 1024 * 10,
    maxRequestSize = 1024 * 1024 * 50
)
public class CommitteeBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(CommitteeBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("committee"); }

    private final JobDAO jobDAO = new JobDAO();
    private final CommitteeDAO committeeDAO = new CommitteeDAO();

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

        logger.info("Committee bulk upload request received");

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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Committee";
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
            String fileName = "committee_" + timestamp + "_" + uuid + ".xlsx";

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

            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
            JsonArray errors = validationResponse.has("errors") ? validationResponse.getAsJsonArray("errors") : new JsonArray();
            
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

            // If validation failed completely or cancelOnWarning is enabled and there are errors, stop processing
            if (("error".equals(validationStatus) || "invalid".equals(validationStatus)) && 
                (cancelOnWarning || !validationResponse.has("data") || validationResponse.getAsJsonArray("data").size() == 0)) {
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Validation failed");
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Validation failed with " + errors.size() + " errors", 0, 0, 0, errors.size());

                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("status", "failed");
                errorResponse.addProperty("job_id", jobId);
                errorResponse.addProperty("reference_name", referenceName);
                errorResponse.addProperty("message", validationResponse.has("message") ? 
                    validationResponse.get("message").getAsString() : "Validation failed");
                errorResponse.addProperty("inserted", 0);
                errorResponse.addProperty("updated", 0);
                errorResponse.addProperty("deleted", 0);
                errorResponse.addProperty("failed", errors.size());
                errorResponse.add("errors", errors);

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(gson.toJson(errorResponse));
                return;
            }

            // Get validated data - may be empty if all rows had errors, but we continue if cancelOnWarning is false
            JsonArray validatedData = validationResponse.has("data") ? validationResponse.getAsJsonArray("data") : new JsonArray();
            int totalRows = validatedData.size();

            jobDAO.updateJobItemsCount(jobId, totalRows);
            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.updateJobProgress(jobId, "Processing", "Processing " + totalRows + " rows");

            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                "Validation complete. Processing " + totalRows + " rows...");

            int insertedCount = 0;
            int updatedCount = 0;
            int deletedCount = 0;
            int failedCount = 0;
            java.util.Map<String, Integer> batchRefToId = new java.util.HashMap<>();
            java.util.Map<String, Integer> batchNameToId = new java.util.HashMap<>();
            Set<String> batchRefs = new HashSet<>();

            // cancelOnWarning already determined above
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
                            Committee committee = new Committee();
                            
                            // Committee Name (mandatory)
                            String name = getString(rowData, "Committee Name");
                            
                            // Validate name is not empty
                            if (name == null || name.trim().isEmpty()) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Committee Name is required and cannot be empty");
                            }
                            
                            // Validate name uniqueness within segment - ALWAYS validate if name column exists
                            if (BulkUploadNameValidator.hasNameColumn("Committee")) {
                                Long segmentId = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    try {
                                        segmentId = determineSegmentId(segmentMode, segment, rowData);
                                    } catch (SQLException e) {
                                        logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                                    }
                                }
                                // If segmentMode is null, check if Segment column exists in Excel
                                if (segmentId == null) {
                                    String segmentName = getString(rowData, "Segment");
                                    if (segmentName != null && !segmentName.trim().isEmpty()) {
                                        try {
                                            segmentId = getSegmentIdByName(segmentName.trim());
                                        } catch (SQLException e) {
                                            logger.warn("Failed to get segment ID by name for validation: {}", e.getMessage());
                                        }
                                    }
                                }
                                try {
                                    nameValidator.validateNameUniqueInSegment("Committee", name.trim(), segmentId, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                } catch (SQLException e) {
                                    logger.error("Database error during name validation for row {}: {}", rowNumber, e.getMessage(), e);
                                    throw new RuntimeException("Row " + rowNumber + ": Database error during name validation: " + e.getMessage(), e);
                                }
                            } else {
                                logger.warn("Committee name validation skipped - hasNameColumn returned false");
                            }
                            
                            committee.setPrimaryName(name.trim());
                            
                            // Description (mandatory)
                            committee.setDescription(getString(rowData, "Description"));
                            
                            // Reference Number - auto-generate if empty (read "Reference" or "Ref")
                            String refNumber = getString(rowData, "Reference");
                            if (refNumber == null || refNumber.trim().isEmpty()) {
                                refNumber = getString(rowData, "Ref");
                            }
                            if (refNumber != null) {
                                refNumber = refNumber.trim();
                                if (refNumber.isEmpty()) refNumber = null;
                            }
                            
                            // Validate reference uniqueness if provided
                            if (refNumber != null && !refNumber.isEmpty()) {
                                try {
                                    refValidator.validateReferenceUnique("Committee", refNumber, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                                batchRefs.add(refNumber.toLowerCase());
                            }
                            
                            if (refNumber == null || refNumber.isEmpty()) {
                                try {
                                    refNumber = ReferenceNumberGenerator.generateCommitteeRefNumber(conn, batchRefs);
                                } catch (SQLException e) {
                                    logger.error("Error generating committee reference number: {}", e.getMessage(), e);
                                    throw new RuntimeException("Failed to generate reference number", e);
                                }
                                try {
                                    refValidator.validateReferenceUnique("Committee", refNumber, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                                batchRefs.add(refNumber.trim().toLowerCase());
                            }
                            committee.setRefNumber(refNumber);
                            
                            // Parent Committee resolution (by Ref first, then by Name)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentRef = getString(rowData, "Parent Ref.");
                                String parentName = getString(rowData, "Parent Committee Name");
                                boolean hasRef = parentRef != null && !parentRef.trim().isEmpty();
                                boolean hasName = parentName != null && !parentName.trim().isEmpty();
                                
                                // Validate that if both are provided, they refer to the same parent
                                if (hasRef && hasName && parentRef != null && parentName != null) {
                                    Committee parentByRef = committeeDAO.getCommitteeByRefNumber(parentRef.trim());
                                    Committee parentByName = committeeDAO.getCommitteeByPrimaryName(parentName.trim());
                                    
                                    if (parentByRef != null && parentByName != null) {
                                        if (parentByRef.getId() != parentByName.getId()) {
                                            throw new RuntimeException("Row " + rowNumber + ": Parent reference '" + parentRef.trim() +
                                                "' and parent name '" + parentName.trim() + "' refer to different committees");
                                        }
                                    }
                                }
                                
                                if (hasRef && parentRef != null) {
                                    Committee parent = committeeDAO.getCommitteeByRefNumber(parentRef.trim());
                                    if (parent != null) {
                                        parentId = parent.getId();
                                    } else {
                                        throw new RuntimeException("Parent committee not found with reference: " + parentRef.trim());
                                    }
                                } else if (hasName && parentName != null) {
                                    Committee parent = committeeDAO.getCommitteeByPrimaryName(parentName.trim());
                                    if (parent != null) {
                                        parentId = parent.getId();
                                    } else {
                                        throw new RuntimeException("Parent committee not found with name: " + parentName.trim());
                                    }
                                }
                            }
                            committee.setParentId(parentId);
                            
                            // BUDG Viewing - use first value if empty
                            Integer viewingId = getInteger(rowData, "IsPublic_ID");
                            if (viewingId == null) viewingId = getInteger(rowData, "BUDG Viewing_ID");
                            if (viewingId == null) viewingId = getInteger(rowData, "is_public");
                            if (viewingId == null) viewingId = getInteger(rowData, "Is_Public_ID");
                            if (viewingId == null) viewingId = getViewingIdByName(getString(rowData, "BUDG Viewing"));
                            if (viewingId == null) viewingId = getFirstViewingId();
                            committee.setIsPublic(viewingId);
                            
                            // Classification (mandatory) - use first value if empty
                            Integer classificationId = coalesce(getInteger(rowData, "Classification_ID"),
                                    getLookupIdByName("committee_classification", getString(rowData, "Classification")));
                            if (classificationId == null) classificationId = getFirstLookupId("committee_classification");
                            committee.setClassification(classificationId);
                            
                            // BUDG Status - use first value if empty
                            Integer statusId = coalesce(getInteger(rowData, "BUDG Status_ID"),
                                    getLookupIdByName("status", getString(rowData, "BUDG Status")));
                            if (statusId == null) statusId = getFirstStatusId();
                            committee.setStatus(statusId);
                            
                            // Lifecycle (mandatory) - use first value if empty
                            Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"),
                                    getLookupIdByName("committee_lifecycle", getString(rowData, "Lifecycle")));
                            if (lifecycleId == null) lifecycleId = getFirstLookupId("committee_lifecycle");
                            committee.setLifecycle(lifecycleId);
                            
                            // Committee Type (mandatory) - use first value if empty
                            Integer committeeTypeId = coalesce(getInteger(rowData, "Committee Type_ID"),
                                    getLookupIdByName("committee_type", getString(rowData, "Committee Type")));
                            if (committeeTypeId == null) committeeTypeId = getFirstLookupId("committee_type");
                            committee.setCommitteeType(committeeTypeId);
                            
                            // User resolution: use User Email from file if provided, otherwise use uploader
                            Integer finalUserId = userId; // Default to uploader
                            String userEmail = getString(rowData, "User Email");
                            if (userEmail != null && !userEmail.trim().isEmpty()) {
                                Integer resolvedUserId = getUserIdByEmail(userEmail.trim());
                                if (resolvedUserId != null) {
                                    finalUserId = resolvedUserId;
                                    logger.info("Resolved User Email '{}' to User ID {}", userEmail, finalUserId);
                                } else {
                                    logger.warn("User Email '{}' not found, using uploader ID {} as stakeholder", userEmail, userId);
                                }
                            }
                            committee.setLastUpdateUserID(finalUserId);
                            
                            // Create committee
                            Committee created = committeeDAO.createCommittee(committee);
                            
                            // Create audit records
                            String authorName = getUserName(finalUserId);
                            committeeDAO.createCommitteeAuditRecords(created.getId(), authorName);
                            committeeDAO.createCommitteeAuditRecord(created.getId());
                            
                            // Create stakeholder record for the resolved user
                            try {
                                // Get governance role from file - check both ID and name
                                String governanceRoleName = getString(rowData, "Governance Role");
                                Integer governanceRoleId = getInteger(rowData, "Governance Role_ID");
                                
                                logger.debug("Row {}: Governance Role from file - Name: '{}', ID: {}", 
                                        rowNumber, governanceRoleName, governanceRoleId);
                                
                                // Try to resolve role by name if ID not provided (scoped to Committee module)
                                if (governanceRoleId == null && governanceRoleName != null && !governanceRoleName.trim().isEmpty()) {
                                    try {
                                        governanceRoleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, governanceRoleName.trim(), "Committee");
                                    } catch (IllegalArgumentException e) {
                                        throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
                                    }
                                    logger.debug("Row {}: Resolved Governance Role '{}' to ID: {}",
                                            rowNumber, governanceRoleName, governanceRoleId);
                                }

                                // If no role specified, get default Committee Owner role
                                if (governanceRoleId == null) {
                                    governanceRoleId = getDefaultCommitteeOwnerRole();
                                    logger.info("Row {}: No governance role specified, using default role: {}", rowNumber, governanceRoleId);
                                }
                                
                                if (governanceRoleId != null) {
                                    RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, finalUserId,
                                            governanceRoleId, "Committee", rowNumber);
                                }
                                if (governanceRoleId != null) {
                                    logger.info("Row {}: Creating stakeholder for committee {} with userId {} and roleId {}",
                                            rowNumber, created.getId(), finalUserId, governanceRoleId);

                                    // Prepare stakeholder data
                                    java.util.Map<String, Object> stakeholderData = new java.util.HashMap<>();
                                    stakeholderData.put("userId", finalUserId);
                                    stakeholderData.put("roleId", governanceRoleId);

                                    // Use DAO to create object_x_people (always creates new)
                                    int objectXPeopleId = committeeDAO.createObjectXPeople(conn, stakeholderData, userId);
                                    logger.debug("Row {}: Created object_x_people with ID: {}", rowNumber, objectXPeopleId);

                                    // Use DAO to link (with duplicate prevention)
                                    committeeDAO.linkStakeholderToCommittee(conn, created.getId(), objectXPeopleId, finalUserId);
                                    logger.debug("Row {}: Linked stakeholder to committee", rowNumber);

                                    // Create audit records
                                    String fullName = getUserName(finalUserId);
                                    committeeDAO.createStakeholderAuditRecords(created.getId(), authorName, fullName, governanceRoleId);
                                    logger.info("Row {}: Successfully created stakeholder for committee {} (userId: {}, roleId: {})", 
                                            rowNumber, created.getId(), finalUserId, governanceRoleId);
                                } else {
                                    logger.warn("Row {}: Cannot create stakeholder for committee {}: no governance role found (userId: {})", 
                                            rowNumber, created.getId(), finalUserId);
                                }
                            } catch (Exception e) {
                                logger.error("Row {}: Error linking stakeholder to committee {}: {}", 
                                        rowNumber, created.getId(), e.getMessage(), e);
                                // Don't throw - stakeholder creation failure shouldn't fail the entire row
                                // but log it for debugging
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
                                        parentId, segmentIdToAssign.intValue(), "Committee");
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
                                        "Committee", 
                                        segmentIdToAssign, 
                                        userId
                                    );
                                    logger.info("Assigned Committee {} to Segment {}", created.getId(), segmentIdToAssign);
                                } catch (Exception segEx) {
                                    // Segment assignment failure = row failure (BUDG behavior)
                                    logger.error("Failed to assign Committee {} to segment: {}", created.getId(), segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    created.getId(), 
                                    "Committee", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Committee {}: {}", created.getId(), cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            if (name != null && !name.trim().isEmpty()) {
                                batchNameToId.put(name.trim().toLowerCase(), created.getId());
                            }
                            if (refNumber != null && !refNumber.trim().isEmpty()) {
                                batchRefToId.put(refNumber.trim().toLowerCase(), created.getId());
                            }
                            
                            insertedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " created successfully", "info");

                        } else if ("UPDATE".equals(operation)) {
                            List<String> identityCols = Arrays.asList("Committee ID", "Reference", "Committee Name");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols, "Committee", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByRef = null;
                            Integer idByName = null;
                            Integer committeeIdVal = getInteger(rowData, "Committee ID");
                            if (committeeIdVal == null) committeeIdVal = getInteger(rowData, "Committee_ID");
                            if (committeeIdVal != null) {
                                Committee c = committeeDAO.getCommitteeById(committeeIdVal);
                                if (c != null) idById = c.getId();
                            }
                            String refVal = getString(rowData, "Reference");
                            if (refVal != null && !refVal.trim().isEmpty()) {
                                Committee c = committeeDAO.getCommitteeByRefNumber(refVal.trim());
                                if (c != null) idByRef = c.getId();
                            }
                            String nameVal = getString(rowData, "Committee Name");
                            if (nameVal != null && !nameVal.trim().isEmpty()) {
                                Committee c = committeeDAO.getCommitteeByPrimaryName(nameVal.trim());
                                if (c != null) idByName = c.getId();
                            }
                            // Consistency: if any two of ID/Ref/Name resolve to different committees, fail before using coalesce
                            if (idById != null && idByRef != null && !idById.equals(idByRef)) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Committee ID, Reference and Committee Name refer to different committees.");
                            }
                            if (idById != null && idByName != null && !idById.equals(idByName)) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Committee ID, Reference and Committee Name refer to different committees.");
                            }
                            if (idByRef != null && idByName != null && !idByRef.equals(idByName)) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Committee ID, Reference and Committee Name refer to different committees.");
                            }
                            Integer committeeId = coalesce(idById, idByRef, idByName);
                            if (committeeId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": No committee found for the provided identity (Committee ID, Reference, or Committee Name).");
                            }
                            Committee oldCommittee = committeeDAO.getCommitteeById(committeeId);
                            if (oldCommittee == null) {
                                throw new IllegalArgumentException("Committee with ID " + committeeId + " does not exist");
                            }
                            
                            Committee newCommittee = new Committee();
                            newCommittee.setId(committeeId);
                            
                            // Committee Name
                            newCommittee.setPrimaryName(getStringOrDefault(rowData, "Committee Name", oldCommittee.getPrimaryName()));
                            
                            // Description
                            newCommittee.setDescription(getStringOrDefault(rowData, "Description", oldCommittee.getDescription()));
                            
                            // Reference Number
                            String refNumber = getString(rowData, "Reference");
                            if (refNumber == null || refNumber.trim().isEmpty()) {
                                refNumber = oldCommittee.getRefNumber();
                            }
                            newCommittee.setRefNumber(refNumber);
                            
                            // Parent Committee resolution - check batch first (parent in same file)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentRef = getString(rowData, "Parent Ref.");
                                String parentName = getString(rowData, "Parent Committee Name");
                                boolean hasRef = parentRef != null && !parentRef.trim().isEmpty();
                                boolean hasName = parentName != null && !parentName.trim().isEmpty();
                                if (hasRef && parentRef != null) {
                                    Integer fromBatch = batchRefToId.get(parentRef.trim().toLowerCase());
                                    if (fromBatch != null) parentId = fromBatch;
                                }
                                if (parentId == null && hasName && parentName != null) {
                                    Integer fromBatch = batchNameToId.get(parentName.trim().toLowerCase());
                                    if (fromBatch != null) parentId = fromBatch;
                                }
                                if (parentId == null) {
                                    if (hasRef && hasName && parentRef != null && parentName != null) {
                                        Committee parentByRef = committeeDAO.getCommitteeByRefNumber(parentRef.trim());
                                        Committee parentByName = committeeDAO.getCommitteeByPrimaryName(parentName.trim());
                                        if (parentByRef != null && parentByName != null && parentByRef.getId() != parentByName.getId()) {
                                            throw new RuntimeException("Row " + rowNumber + ": Parent reference '" + parentRef.trim() +
                                                "' and parent name '" + parentName.trim() + "' refer to different committees");
                                        }
                                    }
                                    if (hasRef && parentRef != null) {
                                        Committee parent = committeeDAO.getCommitteeByRefNumber(parentRef.trim());
                                        if (parent != null) parentId = parent.getId();
                                        else throw new RuntimeException("Parent committee not found with reference: " + parentRef.trim());
                                    } else if (hasName && parentName != null) {
                                        Committee parent = committeeDAO.getCommitteeByPrimaryName(parentName.trim());
                                        if (parent != null) parentId = parent.getId();
                                        else throw new RuntimeException("Parent committee not found with name: " + parentName.trim());
                                    }
                                }
                            }
                            
                            // Use the resolved parentId, or keep old value if nothing was specified
                            Integer oldParentId = oldCommittee.getParentId();
                            if (oldParentId != null && oldParentId == 0) {
                                oldParentId = null;
                            }
                            
                            Integer finalParentId = coalesce(parentId, oldParentId);
                            
                            // Validate that the parent ID actually exists if it's not null and not 0
                            if (finalParentId != null && finalParentId != 0) {
                                Committee parentCommittee = committeeDAO.getCommitteeById(finalParentId);
                                if (parentCommittee == null) {
                                    throw new RuntimeException("Parent committee with ID " + finalParentId + " does not exist or has been deleted");
                                }
                            }
                            
                            // Convert 0 to null before setting (no parent)
                            if (finalParentId != null && finalParentId == 0) {
                                finalParentId = null;
                            }
                            
                            newCommittee.setParentId(finalParentId);
                            
                            // When parent is specified, validate object and parent are not in different private segments
                            if (finalParentId != null && finalParentId > 0) {
                                SegmentDAO segmentDAO = new SegmentDAO();
                                int parentSegmentId = segmentDAO.getObjectSegmentId(finalParentId, "Committee", conn);
                                int childSegmentIdRaw = segmentDAO.getObjectSegmentId(committeeId, "Committee", conn);
                                Long newSegmentIdForParent = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    newSegmentIdForParent = determineSegmentId(segmentMode, segment, rowData);
                                } else {
                                    String segmentName = getString(rowData, "Segment");
                                    if (segmentName != null && !segmentName.trim().isEmpty()) {
                                        newSegmentIdForParent = getSegmentIdByName(segmentName.trim());
                                    }
                                }
                                // Parent in private segment and child has no segment: require row to assign child to parent's segment
                                if (parentSegmentId > 1 && childSegmentIdRaw == -1) {
                                    if (newSegmentIdForParent == null || newSegmentIdForParent.intValue() != parentSegmentId) {
                                        throw new RuntimeException("Row " + rowNumber + ": Cannot set parent in a private segment when the committee has no segment; assign the committee to the parent's segment first or in the same row.");
                                    }
                                }
                                int effectiveChildSegmentId = (newSegmentIdForParent != null) ? newSegmentIdForParent.intValue()
                                        : (childSegmentIdRaw == -1 ? 1 : childSegmentIdRaw);
                                SegmentValidationService parentValidator = new SegmentValidationService();
                                SegmentValidationService.ValidationResult parentResult = parentValidator.validateParentChildSegment(
                                        finalParentId, effectiveChildSegmentId, "Committee");
                                if (!parentResult.isValid) {
                                    throw new RuntimeException("Row " + rowNumber + ": Cannot set parent committee (segment validation): " + parentResult.message);
                                }
                            }
                            
                            // BUDG Viewing
                            Integer viewingId = getInteger(rowData, "IsPublic_ID");
                            if (viewingId == null) viewingId = getInteger(rowData, "BUDG Viewing_ID");
                            if (viewingId == null) viewingId = getInteger(rowData, "is_public");
                            if (viewingId == null) viewingId = getInteger(rowData, "Is_Public_ID");
                            if (viewingId == null) viewingId = getViewingIdByName(getString(rowData, "BUDG Viewing"));
                            if (viewingId == null) viewingId = treatZeroAsNull(oldCommittee.getIsPublic());
                            newCommittee.setIsPublic(treatZeroAsNull(viewingId));
                            
                            // Classification
                            Integer classificationId = coalesce(getInteger(rowData, "Classification_ID"),
                                    getLookupIdByName("committee_classification", getString(rowData, "Classification")), treatZeroAsNull(oldCommittee.getClassification()));
                            newCommittee.setClassification(treatZeroAsNull(classificationId));
                            
                            // BUDG Status
                            Integer statusId = coalesce(getInteger(rowData, "BUDG Status_ID"),
                                    getLookupIdByName("status", getString(rowData, "BUDG Status")), treatZeroAsNull(oldCommittee.getStatus()));
                            newCommittee.setStatus(treatZeroAsNull(statusId));
                            
                            // Lifecycle
                            Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"),
                                    getLookupIdByName("committee_lifecycle", getString(rowData, "Lifecycle")), treatZeroAsNull(oldCommittee.getLifecycle()));
                            newCommittee.setLifecycle(treatZeroAsNull(lifecycleId));
                            
                            // Committee Type
                            Integer committeeTypeId = coalesce(getInteger(rowData, "Committee Type_ID"),
                                    getLookupIdByName("committee_type", getString(rowData, "Committee Type")), treatZeroAsNull(oldCommittee.getCommitteeType()));
                            newCommittee.setCommitteeType(treatZeroAsNull(committeeTypeId));
                            
                            // User resolution - always use userId from session
                            newCommittee.setLastUpdateUserID(userId);
                            
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
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) committeeId, "Committee");
                                    
                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            committeeId, newSegmentId.intValue(), "Committee", finalParentId);
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Row " + rowNumber + ": Segment change not allowed: " + result.message);
                                        }
                                        
                                        
                                        validateSegmentAccess(userId, newSegmentId, rowNumber);
                                        
                                        // Assign new segment
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) committeeId, 
                                            "Committee", 
                                            newSegmentId, 
                                            userId
                                        );
                                        logger.info("Assigned Committee {} to Segment {} (updated)", committeeId, newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Committee {} to segment: {}", committeeId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Update committee (audit is handled inside updateCommitteeWithAudit method)
                            String userName = getUserName(userId);
                            committeeDAO.updateCommitteeWithAudit(oldCommittee, newCommittee, userName);
                            
                            // Save custom field values (committeeId already defined above)
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    committeeId, 
                                    "Committee", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Committee {}: {}", committeeId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            updatedCount++;
                            // Success report only here: after update and segment assignment. Failed rows (e.g. segment validation) are reported only in catch as "error".
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " updated successfully", "info");

                        } else if ("DELETE".equals(operation)) {
                            Integer idToDelete = getInteger(rowData, "Committee ID");
                            if (idToDelete == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Committee ID is required for delete");
                            }
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "committee", idToDelete);
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
                                Committee existing = committeeDAO.getCommitteeById(idToDelete);
                                if (existing == null) {
                                    logger.info("Committee with ID {} does not exist, skipping deletion (idempotent)", idToDelete);
                                    continue;
                                }
                                committeeDAO.deleteCommitteeWithAudit(idToDelete, getUserName(userId));
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
                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "PROCESSING_ERROR",
                            e.getMessage(), "error");

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
                String progressMsg = String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount)
                    + "\nBULK_COUNTS:inserted=" + insertedCount + ",failed=" + failedCount + ",updated=" + updatedCount + ",deleted=" + deletedCount;
                jobDAO.updateJobProgress(jobId, "Failed", progressMsg);

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Processing failed: all rows failed",
                    insertedCount, updatedCount, deletedCount, failedCount);
                
                logger.info("Bulk upload job {} failed: {} inserted, {} updated, {} deleted, {} failed",
                    jobId, insertedCount, updatedCount, deletedCount, failedCount);
                
                sendNotification(userId, "Committee", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, false, "All rows failed during processing", jobId);
            } else if (isPartiallyCompleted) {
                jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount); // Only successful operations
                String progressMsg = String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount)
                    + "\nBULK_COUNTS:inserted=" + insertedCount + ",failed=" + failedCount + ",updated=" + updatedCount + ",deleted=" + deletedCount;
                jobDAO.updateJobProgress(jobId, "Partially Completed", progressMsg);

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                    "Processing complete with some failures",
                    insertedCount, updatedCount, deletedCount, failedCount);
                
                logger.info("Bulk upload job {} partially completed: {} inserted, {} updated, {} deleted, {} failed",
                    jobId, insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send notification for partial completion
                sendNotification(userId, "Committee", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, true, null, jobId);
            } else {
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount);
                String progressMsg = String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount)
                    + "\nBULK_COUNTS:inserted=" + insertedCount + ",failed=" + failedCount + ",updated=" + updatedCount + ",deleted=" + deletedCount;
                jobDAO.updateJobProgress(jobId, "Completed", progressMsg);

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    "Bulk upload completed successfully",
                    insertedCount, updatedCount, deletedCount, failedCount);
                
                // Send success notification
                sendNotification(userId, "Committee", uploadOption, insertedCount, updatedCount, 
                                deletedCount, failedCount, true, null, jobId);
            }

            // Failed count must include validation errors stored in report items (GUI must not show 0 when report has errors)
            int errorReportItemCount = 0;
            try {
                List<com.example.budg_v2.model.JobReportItem> reportItems = jobDAO.getJobReportItems(jobId);
                for (com.example.budg_v2.model.JobReportItem item : reportItems) {
                    String s = item.getStatus();
                    if (s != null && ("error".equalsIgnoreCase(s) || "failed".equalsIgnoreCase(s))) {
                        errorReportItemCount++;
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load report items for failed count: {}", e.getMessage());
            }
            int failedToSend = Math.max(failedCount, errorReportItemCount);

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("inserted", insertedCount);
            successResponse.addProperty("updated", updatedCount);
            successResponse.addProperty("deleted", deletedCount);
            successResponse.addProperty("failed", failedToSend);
            successResponse.addProperty("report_url", "/api/bulk/committee/report/" + jobId);

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
                                sendNotification(userId, "Committee", uploadOption, 0, 0, 0, 0, 
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
            n.setFacetType("committee");
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
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'COM-%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("COM-")) {
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
            logger.warn("Error generating short reference name, using fallback number: {}", e.getMessage());
        }
        return "COM-" + nextNumber;
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

    // Helper methods for data extraction
    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private String getStringOrDefault(JsonObject obj, String key, String def) {
        String v = getString(obj, key);
        return v != null ? v : def;
    }

    private Integer getInteger(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            com.google.gson.JsonElement el = obj.get(key);
            if (el.isJsonPrimitive()) {
                com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsInt();
                if (p.isString()) {
                    String s = p.getAsString();
                    if (s == null || s.trim().isEmpty()) return null;
                    return (int) Double.parseDouble(s.trim());
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
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

    // Lookup helper methods
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


    // Stakeholder methods
    // Stakeholder methods now handled by CommitteeDAO

    private Integer getDefaultCommitteeOwnerRole() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First get Committee module ID
            String moduleQuery = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER('Committee') LIMIT 1";
            try (java.sql.PreparedStatement moduleStmt = conn.prepareStatement(moduleQuery);
                 java.sql.ResultSet moduleRs = moduleStmt.executeQuery()) {
                
                if (moduleRs.next()) {
                    int moduleId = moduleRs.getInt("id");
                    logger.info("Found Committee module ID: {}", moduleId);
                    
                    // Get first role for Committee module (typically Committee Owner)
                    String roleQuery = "SELECT id FROM object_role WHERE module = ? ORDER BY id ASC LIMIT 1";
                    try (java.sql.PreparedStatement roleStmt = conn.prepareStatement(roleQuery)) {
                        roleStmt.setInt(1, moduleId);
                        try (java.sql.ResultSet roleRs = roleStmt.executeQuery()) {
                            if (roleRs.next()) {
                                int roleId = roleRs.getInt("id");
                                logger.info("Found default Committee Owner role ID: {}", roleId);
                                return roleId;
                            } else {
                                logger.warn("No roles found for Committee module (moduleId: {})", moduleId);
                            }
                        }
                    }
                } else {
                    logger.warn("Committee module not found in database");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default Committee Owner role: {}", e.getMessage(), e);
        }
        logger.error("Failed to get default Committee Owner role - returning null");
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

