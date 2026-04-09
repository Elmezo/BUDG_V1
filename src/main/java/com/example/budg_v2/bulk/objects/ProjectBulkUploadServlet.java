package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.ProjectDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.Project;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.database.DatabaseConnection;
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
import com.example.budg_v2.bulk.common.BulkUploadReferenceValidator;
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
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@WebServlet("/api/bulk/project/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,
    maxFileSize = 1024 * 1024 * 10,
    maxRequestSize = 1024 * 1024 * 50
)
public class ProjectBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ProjectBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("project"); }

    private final JobDAO jobDAO = new JobDAO();
    private final ProjectDAO projectDAO = new ProjectDAO();

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

        logger.info("Project bulk upload request received");

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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Project";
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
            String fileName = "project_" + timestamp + "_" + uuid + ".xlsx";

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

            // Determine cancelOnWarning setting early (before validation error handling)
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
            
            // Get validated data and errors
            JsonArray validatedData = validationResponse.has("data") ? validationResponse.getAsJsonArray("data") : new JsonArray();
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
            Connection conn = null;
            BulkUploadNameValidator nameValidator = new BulkUploadNameValidator();
            BulkUploadReferenceValidator refValidator = new BulkUploadReferenceValidator();
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
                            processProjectInsertRow(conn, rowData, rowNumber, jobId, entityName, userId,
                                    segmentMode, segment, refValidator, nameValidator,
                                    batchRefToId, batchNameToId);
                            insertedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " created successfully", "info");

                        } else if ("UPDATE".equals(operation)) {
                            List<String> identityCols = Arrays.asList("Project ID", "Reference", "Project Name");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols, "Project", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByRef = null;
                            Integer idByName = null;
                            Integer idVal = getInteger(rowData, "Project ID");
                            if (idVal != null) {
                                Project p = projectDAO.getProjectById(idVal);
                                if (p != null) idById = p.getId();
                            }
                            String refVal = getString(rowData, "Reference");
                            if (refVal != null && !refVal.trim().isEmpty()) {
                                Project p = projectDAO.getProjectByRefNumber(refVal.trim());
                                if (p != null) idByRef = p.getId();
                            }
                            String nameVal = getString(rowData, "Project Name");
                            if (nameVal != null && !nameVal.trim().isEmpty()) {
                                Project p = projectDAO.getProjectByPrimaryName(nameVal.trim());
                                if (p != null) idByName = p.getId();
                            }
                            int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                            Integer projectId = coalesce(idById, idByRef, idByName);
                            if (projectId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": No project found for the provided identity (Project ID, Reference, or Project Name).");
                            }
                            if (filledIdentity >= 2) {
                                java.util.Set<Integer> ids = new java.util.HashSet<>();
                                if (idById != null) ids.add(idById);
                                if (idByRef != null) ids.add(idByRef);
                                if (idByName != null) ids.add(idByName);
                                if (ids.size() > 1) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": Project ID, Reference and Project Name refer to different projects.");
                                }
                            }
                            Project oldProject = projectDAO.getProjectById(projectId);
                            if (oldProject == null) {
                                String nameForInsert = getString(rowData, "Project Name");
                                if ("Add New Items".equals(uploadOption) && nameForInsert != null && !nameForInsert.trim().isEmpty()) {
                                    try {
                                        processProjectInsertRow(conn, rowData, rowNumber, jobId, entityName, userId,
                                                segmentMode, segment, refValidator, nameValidator,
                                                batchRefToId, batchNameToId);
                                        insertedCount++;
                                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                            entityName + " created successfully (row was treated as new record).", "info");
                                    } catch (Exception insertEx) {
                                        failedCount++;
                                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                                        jobDAO.createJobReportItemMessage(reportItemId, "PROCESSING_ERROR",
                                            "Project not found with ID: " + projectId + "; attempted insert failed: " + insertEx.getMessage(), "error");
                                        if (cancelOnWarning) {
                                            conn.rollback();
                                            throw new ServletException("Processing failed at row " + rowNumber + ": " + insertEx.getMessage(), insertEx);
                                        }
                                        try {
                                            conn.rollback();
                                            logger.debug("Rolled back row {} after insert fallback failed", rowNumber);
                                        } catch (SQLException rollbackEx) {
                                            logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                                        }
                                    }
                                    continue;
                                }
                                failedCount++;
                                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                                jobDAO.createJobReportItemMessage(reportItemId, "PROCESSING_ERROR",
                                    "Project with ID " + projectId + " does not exist. For new records leave ID empty and use the 'Add New Items' upload option.", "error");
                                if (cancelOnWarning) {
                                    conn.rollback();
                                    throw new ServletException("Processing failed at row " + rowNumber + ": Project with ID " + projectId + " does not exist. For new records leave ID empty and use the 'Add New Items' upload option.");
                                }
                                try {
                                    conn.rollback();
                                    logger.debug("Rolled back row {} due to project not found", rowNumber);
                                } catch (SQLException rollbackEx) {
                                    logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                                }
                            } else {
                            Project newProject = new Project();
                            newProject.setId(projectId);
                            
                            // Update fields
                            newProject.setPrimaryName(getStringOrDefault(rowData, "Project Name", oldProject.getPrimaryName()));
                            newProject.setDescription(getStringOrDefault(rowData, "Project Description", oldProject.getDescription()));
                            
                            // Parse dates
                            Timestamp startDate = parseDate(getString(rowData, "Start Date"));
                            newProject.setStartDate(startDate != null ? startDate : oldProject.getStartDate());
                            
                            Timestamp endDate = parseDate(getString(rowData, "End Date"));
                            newProject.setEndDate(endDate != null ? endDate : oldProject.getEndDate());
                            
                            // Reference number
                            String refNumber = getString(rowData, "Reference");
                            if (refNumber == null || refNumber.trim().isEmpty()) {
                                refNumber = oldProject.getRefNumber();
                            }
                            newProject.setRefNumber(refNumber);
                            
                            // Parent project resolution - check batch first (parent in same file)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentRef = getString(rowData, "Parent Ref.");
                                String parentName = getString(rowData, "Parent Project Name");
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
                                        Project parentByRef = projectDAO.getProjectByRefNumber(parentRef.trim());
                                        Project parentByName = projectDAO.getProjectByPrimaryName(parentName.trim());
                                        if (parentByRef != null && parentByName != null && !parentByRef.getId().equals(parentByName.getId())) {
                                            throw new RuntimeException("Row " + rowNumber + ": Parent reference '" + parentRef.trim() +
                                                "' and parent name '" + parentName.trim() + "' refer to different projects");
                                        }
                                    }
                                    if (hasRef && parentRef != null) {
                                        Project parent = projectDAO.getProjectByRefNumber(parentRef.trim());
                                        if (parent != null) parentId = parent.getId();
                                        else throw new RuntimeException("Parent project not found with reference: " + parentRef.trim());
                                    } else if (hasName && parentName != null) {
                                        Project parent = projectDAO.getProjectByPrimaryName(parentName.trim());
                                        if (parent != null) parentId = parent.getId();
                                        else throw new RuntimeException("Parent project not found with name: " + parentName.trim());
                                    }
                                }
                            }
                            
                            Integer oldParentId = oldProject.getParentId();
                            if (oldParentId != null && oldParentId == 0) {
                                oldParentId = null;
                            }
                            Integer finalParentId = coalesce(parentId, oldParentId);
                            if (finalParentId != null && finalParentId != 0) {
                                Project parentProject = projectDAO.getProjectById(finalParentId);
                                if (parentProject == null) {
                                    throw new RuntimeException("Parent project with ID " + finalParentId + " does not exist or has been deleted");
                                }
                                try {
                                    if (BulkUploadParentValidator.wouldCreateParentCycle(conn, projectId, finalParentId, "Project")) {
                                        throw new RuntimeException("Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.");
                                    }
                                } catch (SQLException e) {
                                    throw new RuntimeException("Failed to validate parent hierarchy: " + e.getMessage(), e);
                                }
                            }
                            if (finalParentId != null && finalParentId == 0) {
                                finalParentId = null;
                            }
                            newProject.setParentId(finalParentId);
                            
                            // When parent is specified, validate object and parent are not in different private segments
                            if (finalParentId != null && finalParentId > 0) {
                                SegmentDAO segmentDAO = new SegmentDAO();
                                int parentSegmentId = segmentDAO.getObjectSegmentId(finalParentId, "Project", conn);
                                int childSegmentIdRaw = segmentDAO.getObjectSegmentId(projectId, "Project", conn);
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
                                        throw new RuntimeException("Row " + rowNumber + ": Cannot set parent in a private segment when the project has no segment; assign the project to the parent's segment first or in the same row.");
                                    }
                                }
                                int effectiveChildSegmentId = (newSegmentIdForParent != null) ? newSegmentIdForParent.intValue()
                                        : (childSegmentIdRaw == -1 ? 1 : childSegmentIdRaw);
                                SegmentValidationService parentValidator = new SegmentValidationService();
                                SegmentValidationService.ValidationResult parentResult = parentValidator.validateParentChildSegment(
                                        finalParentId, effectiveChildSegmentId, "Project");
                                if (!parentResult.isValid) {
                                    throw new RuntimeException("Row " + rowNumber + ": Parent segment validation failed: " + parentResult.message);
                                }
                            }
                            
                            // Lookup fields
                            Integer viewingId = getInteger(rowData, "IsPublic_ID");
                            if (viewingId == null) viewingId = getInteger(rowData, "BUDG Viewing_ID");
                            if (viewingId == null) viewingId = getInteger(rowData, "is_public");
                            if (viewingId == null) viewingId = getInteger(rowData, "Is_Public_ID");
                            if (viewingId == null) viewingId = getViewingIdByName(getString(rowData, "BUDG Viewing"));
                            if (viewingId == null) viewingId = treatZeroAsNull(oldProject.getIsPublic());
                            newProject.setIsPublic(treatZeroAsNull(viewingId));
                            
                            Integer ragId = coalesce(getInteger(rowData, "RAG_ID"),
                                    getLookupIdByName("project_rag", getString(rowData, "RAG")), treatZeroAsNull(oldProject.getRag()));
                            newProject.setRag(treatZeroAsNull(ragId));
                            
                            Integer classificationId = coalesce(getInteger(rowData, "Classification_ID"),
                                    getLookupIdByName("project_classification", getString(rowData, "Classification")), treatZeroAsNull(oldProject.getClassification()));
                            newProject.setClassification(treatZeroAsNull(classificationId));
                            
                            Integer statusId = coalesce(getInteger(rowData, "BUDG Status_ID"),
                                    getLookupIdByName("status", getString(rowData, "BUDG Status")), treatZeroAsNull(oldProject.getStatus()));
                            newProject.setStatus(treatZeroAsNull(statusId));
                            
                            Integer lifecycleId = coalesce(getInteger(rowData, "Project Lifecycle_ID"),
                                    getLookupIdByName("project_lifecycle", getString(rowData, "Project Lifecycle")), treatZeroAsNull(oldProject.getLifecycleStatus()));
                            newProject.setLifecycleStatus(treatZeroAsNull(lifecycleId));
                            
                            Integer projectTypeId = coalesce(getInteger(rowData, "Project Type_ID"),
                                    getLookupIdByName("project_comment_type", getString(rowData, "Project Type")), treatZeroAsNull(oldProject.getProjectType()));
                            newProject.setProjectType(treatZeroAsNull(projectTypeId));
                            
                            // User resolution - always use userId from session
                            newProject.setLastUpdateUserId(userId);

                            // Handle segment assignment for UPDATE operations (before updateProject so failure does not persist)
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
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) projectId, "Project");
                                    
                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            projectId, newSegmentId.intValue(), "Project", finalParentId);
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Row " + rowNumber + ": Segment validation failed: " + result.message);
                                        }
                                        
                                        validateSegmentAccess(userId, newSegmentId, rowNumber);
                                        
                                        // Assign new segment (before updateProject so failure aborts row)
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) projectId, 
                                            "Project", 
                                            newSegmentId, 
                                            userId
                                        );
                                        logger.info("Assigned Project {} to Segment {} (updated)", projectId, newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Project {} to segment: {}", projectId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }

                            // Update project (audit is handled inside updateProject method)
                            projectDAO.updateProject(newProject);

                            // Save custom field values (projectId already defined above)
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    projectId, 
                                    "Project", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Project {}: {}", projectId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }

                            updatedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " updated successfully", "info");
                            }

                        } else if ("DELETE".equals(operation)) {
                            int projectId = rowData.get("Project ID").getAsInt();
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "project", projectId);
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
                                Project existing = projectDAO.getProjectById(projectId);
                                if (existing == null) {
                                    logger.info("Project with ID {} does not exist, skipping deletion (idempotent)", projectId);
                                    continue;
                                }
                                String userName = getUserName(userId);
                                projectDAO.deleteProjectWithAudit(projectId, userName);
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
                
                // Send failure notification
                if (userIdStr != null && uploadOption != null) {
                    try {
                        int parsedUserId = Integer.parseInt(userIdStr);
                        sendNotification(parsedUserId, "Project", uploadOption, insertedCount, updatedCount, 
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
                
                sendNotification(userId, "Project", uploadOption, insertedCount, updatedCount, 
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
                sendNotification(userId, "Project", uploadOption, insertedCount, updatedCount, 
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
                sendNotification(userId, "Project", uploadOption, insertedCount, updatedCount, 
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
            successResponse.addProperty("report_url", "/api/bulk/project/report/" + jobId);

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
                                sendNotification(userId, "Project", uploadOption, 0, 0, 0, 0, 
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
            n.setFacetType("project");
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
    private final <T> T coalesce(T... values) {
        if (values == null) return null;
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private Integer treatZeroAsNull(Integer value) {
        return (value != null && value == 0) ? null : value;
    }

    private Timestamp parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date date = sdf.parse(dateStr.trim());
            return new Timestamp(date.getTime());
        } catch (Exception e) {
            logger.error("Error parsing date: {}", dateStr, e);
            return null;
        }
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

    @SuppressWarnings("unused")
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

    private Integer getLookupIdByName(String tableName, String primaryName) {
        if (primaryName == null || primaryName.trim().isEmpty()) return null;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM " + tableName + " WHERE LOWER(PrimaryName) = LOWER(?) LIMIT 1")) {
            ps.setString(1, primaryName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID");
            }
        } catch (SQLException e) {
            logger.error("Error getting lookup ID by name from table {}: {}", tableName, primaryName, e);
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

    // Stakeholder methods now handled by ProjectDAO

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

    private String generateShortReferenceName() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'PRJ%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("PRJ")) {
                        try {
                            // Extract number part after "PRJ" (handles both PRJ123 and PRJ-123)
                            String numberPart = refName.substring(3).replace("-", "");
                            if (!numberPart.isEmpty()) {
                                int number = Integer.parseInt(numberPart);
                                if (number > maxNumber) {
                                    maxNumber = number;
                                }
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
        return "PRJ" + nextNumber;
    }

    /**
     * Performs INSERT of a single project row (used by normal INSERT path and by UPDATE->INSERT fallback when project not found and upload is "Add New Items").
     */
    private void processProjectInsertRow(Connection conn, JsonObject rowData, int rowNumber, int jobId, String entityName, int userId,
            String segmentMode, String segment, BulkUploadReferenceValidator refValidator, BulkUploadNameValidator nameValidator,
            java.util.Map<String, Integer> batchRefToId, java.util.Map<String, Integer> batchNameToId) throws SQLException {
        Project project = new Project();
        String name = getString(rowData, "Project Name");

        if (name != null && !name.trim().isEmpty() && BulkUploadNameValidator.hasNameColumn("Project")) {
            Long segmentId = null;
            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                try {
                    segmentId = determineSegmentId(segmentMode, segment, rowData);
                } catch (SQLException e) {
                    logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                }
            }
            try {
                nameValidator.validateNameUniqueInSegment("Project", name, segmentId, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
            }
        }

        project.setPrimaryName(name);
        project.setDescription(getString(rowData, "Project Description"));
        project.setStartDate(parseDate(getString(rowData, "Start Date")));
        project.setEndDate(parseDate(getString(rowData, "End Date")));

        String refNumber = getString(rowData, "Reference");
        if (refNumber != null && !refNumber.trim().isEmpty() && BulkUploadReferenceValidator.hasReferenceColumn("Project")) {
            Long segmentId = null;
            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                try {
                    segmentId = determineSegmentId(segmentMode, segment, rowData);
                } catch (SQLException e) {
                    logger.warn("Failed to determine segment ID for reference validation: {}", e.getMessage());
                }
            }
            try {
                refValidator.validateReferenceUniqueInSegment("Project", refNumber, segmentId, conn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
            }
        }
        project.setRefNumber(refNumber != null && !refNumber.trim().isEmpty() ? refNumber : null);

        Integer parentId = getInteger(rowData, "Parent_ID");
        if (parentId == null) {
            String parentRef = getString(rowData, "Parent Ref.");
            String parentName = getString(rowData, "Parent Project Name");
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
                    Project parentByRef = projectDAO.getProjectByRefNumber(parentRef.trim());
                    Project parentByName = projectDAO.getProjectByPrimaryName(parentName.trim());
                    if (parentByRef != null && parentByName != null && !parentByRef.getId().equals(parentByName.getId())) {
                        throw new RuntimeException("Row " + rowNumber + ": Parent reference '" + parentRef.trim() +
                            "' and parent name '" + parentName.trim() + "' refer to different projects");
                    }
                }
                if (hasRef && parentRef != null) {
                    Project parent = projectDAO.getProjectByRefNumber(parentRef.trim());
                    if (parent != null) parentId = parent.getId();
                    else throw new RuntimeException("Parent project not found with reference: " + parentRef.trim());
                } else if (hasName && parentName != null) {
                    Project parent = projectDAO.getProjectByPrimaryName(parentName.trim());
                    if (parent != null) parentId = parent.getId();
                    else throw new RuntimeException("Parent project not found with name: " + parentName.trim());
                }
            }
        }
        project.setParentId(parentId);

        Integer viewingId = getInteger(rowData, "IsPublic_ID");
        if (viewingId == null) viewingId = getInteger(rowData, "BUDG Viewing_ID");
        if (viewingId == null) viewingId = getInteger(rowData, "is_public");
        if (viewingId == null) viewingId = getInteger(rowData, "Is_Public_ID");
        if (viewingId == null) viewingId = getViewingIdByName(getString(rowData, "BUDG Viewing"));
        if (viewingId == null) viewingId = getFirstViewingId();
        project.setIsPublic(viewingId);

        Integer ragId = coalesce(getInteger(rowData, "RAG_ID"), getLookupIdByName("project_rag", getString(rowData, "RAG")));
        if (ragId == null) ragId = getFirstLookupId("project_rag");
        project.setRag(ragId);

        Integer classificationId = coalesce(getInteger(rowData, "Classification_ID"),
                getLookupIdByName("project_classification", getString(rowData, "Classification")));
        project.setClassification(classificationId);

        Integer statusId = coalesce(getInteger(rowData, "BUDG Status_ID"), getLookupIdByName("status", getString(rowData, "BUDG Status")));
        if (statusId == null) {
            throw new IllegalArgumentException("Row " + rowNumber + ": BUDG Status is required");
        }
        project.setStatus(statusId);

        Integer lifecycleId = coalesce(getInteger(rowData, "Project Lifecycle_ID"),
                getLookupIdByName("project_lifecycle", getString(rowData, "Project Lifecycle")));
        if (lifecycleId == null) lifecycleId = getFirstLookupId("project_lifecycle");
        project.setLifecycleStatus(lifecycleId);

        Integer projectTypeId = coalesce(getInteger(rowData, "Project Type_ID"),
                getLookupIdByName("project_comment_type", getString(rowData, "Project Type")));
        if (projectTypeId == null) projectTypeId = getFirstLookupId("project_comment_type");
        project.setProjectType(projectTypeId);

        Integer finalUserId = userId;
        String userEmail = getString(rowData, "User Email");
        if (userEmail != null && !userEmail.trim().isEmpty()) {
            Integer resolvedUserId = getUserIdByEmail(userEmail.trim());
            if (resolvedUserId != null) finalUserId = resolvedUserId;
        }
        project.setLastUpdateUserId(finalUserId);
        project.setCreatedById(finalUserId);

        Project created = projectDAO.createProject(project, conn);

        if (created.getRefNumber() == null || created.getRefNumber().trim().isEmpty()) {
            String autoRefNumber = generateProjectRefNumber();
            try (java.sql.PreparedStatement updateStmt = conn.prepareStatement("UPDATE project SET refnumber = ? WHERE id = ?")) {
                updateStmt.setString(1, autoRefNumber);
                updateStmt.setInt(2, created.getId());
                updateStmt.executeUpdate();
                created.setRefNumber(autoRefNumber);
            }
        }

        String authorName = getUserName(finalUserId);
        // Use same JDBC connection as insert: uncommitted row is not visible on other connections
        projectDAO.createProjectAuditRecords(conn, created.getId(), authorName);
        projectDAO.createProjectAuditRecord(conn, created.getId());

        try {
            Integer governanceRoleId = getInteger(rowData, "Governance Role_ID");
            if (governanceRoleId == null) {
                String grName = getString(rowData, "Governance Role");
                if (grName != null && !grName.trim().isEmpty()) {
                    try {
                        governanceRoleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, grName, "Project");
                    } catch (IllegalArgumentException e) {
                        throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
                    }
                }
            }
            if (governanceRoleId == null) governanceRoleId = getDefaultProjectOwnerRole();
            if (governanceRoleId != null) {
                RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, finalUserId, governanceRoleId, "Project", rowNumber);
            }
            if (governanceRoleId != null) {
                Map<String, Object> stakeholderData = new HashMap<>();
                stakeholderData.put("userId", finalUserId);
                stakeholderData.put("roleId", governanceRoleId);
                int objectXPeopleId = projectDAO.createObjectXPeople(conn, stakeholderData, userId);
                projectDAO.linkStakeholderToProject(conn, created.getId(), objectXPeopleId);
                String fullName = getUserName(finalUserId);
                projectDAO.createStakeholderAuditRecords(created.getId(), authorName, fullName, governanceRoleId);
            }
        } catch (Exception e) {
            logger.error("Error linking stakeholder to project: {}", e.getMessage());
        }

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
                    parentId, segmentIdToAssign.intValue(), "Project");
                if (!result.isValid) {
                    throw new RuntimeException("Segment validation failed: " + result.message);
                }
            }
        }

        if (segmentIdToAssign != null) {
            validateSegmentAccess(userId, segmentIdToAssign, rowNumber);
            ObjectSegmentService.assignObjectToSegment(conn, (long) created.getId(), "Project", segmentIdToAssign, userId);
        }

        try {
            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(created.getId(), "Project", rowData, userId, conn);
        } catch (Exception cfEx) {
            logger.error("Failed to save custom fields for Project {}: {}", created.getId(), cfEx.getMessage(), cfEx);
            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
        }

        if (name != null && !name.trim().isEmpty()) {
            batchNameToId.put(name.trim().toLowerCase(), created.getId());
        }
        String createdRef = created.getRefNumber();
        if (createdRef != null && !createdRef.trim().isEmpty()) {
            batchRefToId.put(createdRef.trim().toLowerCase(), created.getId());
        }
    }

    /**
     * Generate project reference number (PRJ + number)
     * Finds the highest existing PRJ number and increments it
     * Format: PRJ737, PRJ738, etc. (without dash)
     */
    private String generateProjectRefNumber() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT refnumber FROM project WHERE refnumber LIKE 'PRJ%' AND deletedatetime IS NULL")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refNumber = rs.getString("refnumber");
                    if (refNumber != null && refNumber.startsWith("PRJ")) {
                        try {
                            // Extract number part after "PRJ" (handles both PRJ123 and PRJ-123)
                            String numberPart = refNumber.substring(3).replace("-", "").trim();
                            if (!numberPart.isEmpty()) {
                                int number = Integer.parseInt(numberPart);
                                if (number > maxNumber) {
                                    maxNumber = number;
                                }
                            }
                        } catch (NumberFormatException ignore) {
                            // Skip invalid formats
                        }
                    }
                }
                if (maxNumber > 0) {
                    nextNumber = maxNumber + 1;
                }
            }
        } catch (SQLException e) {
            logger.warn("Error generating project ref number, using fallback: {}", e.getMessage());
            // Fallback: use project ID if available, otherwise use 1
            try {
                try (Connection conn = DatabaseConnection.getConnection();
                     java.sql.Statement stmt = conn.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM project")) {
                    if (rs.next()) {
                        nextNumber = rs.getInt("next_id");
                    }
                }
            } catch (SQLException e2) {
                logger.warn("Error in fallback ref number generation: {}", e2.getMessage());
            }
        }
        return "PRJ" + nextNumber;
    }

    private Integer getDefaultProjectOwnerRole() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First get Project module ID
            String moduleQuery = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER('Project') LIMIT 1";
            try (java.sql.PreparedStatement moduleStmt = conn.prepareStatement(moduleQuery);
                 java.sql.ResultSet moduleRs = moduleStmt.executeQuery()) {
                
                if (moduleRs.next()) {
                    int moduleId = moduleRs.getInt("id");
                    
                    // Get first role for Project module (typically Project Owner)
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
            logger.error("Error getting default Project Owner role: {}", e.getMessage());
        }
        return null;
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

