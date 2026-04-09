package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.PolicyDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.model.Policy;
import com.example.budg_v2.database.DatabaseConnection;
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
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@WebServlet("/api/bulk/policy/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class PolicyBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(PolicyBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("policy"); }
    
    private final JobDAO jobDAO = new JobDAO();
    private final PolicyDAO policyDAO = new PolicyDAO();

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

        logger.info("Policy bulk upload request received");

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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Policy";
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
            String fileName = "policy_" + timestamp + "_" + uuid + ".xlsx";
            
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
            // Use entity from request parameter, fallback to "Policy" if not provided (entityName set above)
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
                metadata.addProperty("entity", entity != null ? entity : "Policy");
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
            File absoluteFile = new File(storagePath).getAbsoluteFile();
            validationRequest.addProperty("file_path", absoluteFile.getAbsolutePath());
            validationRequest.addProperty("upload_option", uploadOption);
            validationRequest.addProperty("entity", entity != null ? entity : "Policy");
            validationRequest.addProperty("user_id", userId);

            // Add segment parameters if provided (for INSERT operations only)
            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                validationRequest.addProperty("segment_mode", segmentMode);
                if (segment != null && !segment.trim().isEmpty()) {
                    validationRequest.addProperty("segment", segment);
                }
            }
            
            // Add column mappings if provided
            // Frontend sends: {fieldName: excelColumnName} e.g., {"Publication Date": "تاريخ النشر"}
            // Python expects: {excelColumnName: fieldName} e.g., {"تاريخ النشر": "Publication Date"}
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
            JsonObject validationResponse;
            try {
                validationResponse = HttpClientUtil.postJsonGetJson(PYTHON_SERVICE_URL, validationRequest);
            } catch (IOException e) {
                logger.error("Python validation service error: {}", e.getMessage(), e);
                
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
                
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, userFriendlyMessage, 0, 0, 0, 0);
                
                sendErrorResponse(response, userFriendlyMessage, 503);
                return;
            }

            String validationStatus = validationResponse.get("status").getAsString();
            logger.info("Validation status: {}", validationStatus);

            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Validating", 20, "Validation in progress...");

            // Step 8: Handle validation response
            JsonArray validatedData = validationResponse.getAsJsonArray("data");
            JsonArray errors = validationResponse.has("errors") ? validationResponse.getAsJsonArray("errors") : new JsonArray();
            
            // Check if this is a service error (not just validation errors)
            if ("error".equals(validationStatus)) {
                logger.error("Validation service error");
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Validation service error");
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, 
                    "Validation service error", 0, 0, 0, 0);
                
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("status", "failed");
                errorResponse.addProperty("job_id", jobId);
                errorResponse.addProperty("reference_name", referenceName);
                errorResponse.addProperty("message", validationResponse.has("message") ? 
                    validationResponse.get("message").getAsString() : "Validation service error");
                errorResponse.addProperty("inserted", 0);
                errorResponse.addProperty("updated", 0);
                errorResponse.addProperty("deleted", 0);
                errorResponse.addProperty("failed", 0);
                
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(gson.toJson(errorResponse));
                return;
            }
            
            // Handle validation errors (status "invalid" or errors present)
            if ("invalid".equals(validationStatus) || (errors != null && errors.size() > 0)) {
                logger.warn("Validation found {} errors, but checking for valid rows to process", errors.size());
                
                // Log validation errors to job report
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
                
                // Check if there are any valid rows to process
                if (validatedData == null || validatedData.size() == 0) {
                    // No valid rows, fail completely
                    logger.warn("No valid rows to process, failing upload");
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Validation failed - no valid rows");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, 
                        "Validation failed with " + errors.size() + " errors, no valid rows", 0, 0, 0, errors.size());
                    
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("status", "failed");
                    errorResponse.addProperty("job_id", jobId);
                    errorResponse.addProperty("reference_name", referenceName);
                    errorResponse.addProperty("message", validationResponse.has("message") ? 
                        validationResponse.get("message").getAsString() : "Validation failed - no valid rows");
                    errorResponse.addProperty("inserted", 0);
                    errorResponse.addProperty("updated", 0);
                    errorResponse.addProperty("deleted", 0);
                    errorResponse.addProperty("failed", errors.size());
                    errorResponse.add("errors", errors);
                    
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write(gson.toJson(errorResponse));
                    return;
                }
                
                // There are valid rows, log warning but continue processing
                logger.info("Found {} valid rows to process despite {} validation errors", 
                    validatedData.size(), errors.size());
            }

            // Step 9: Process the validated data (whether status was "valid" or "invalid" with valid rows)
            int totalRows = validatedData != null ? validatedData.size() : 0;
            
            // If no valid data to process, return early
            if (validatedData == null || totalRows == 0) {
                logger.warn("No valid data to process");
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobProgress(jobId, "Completed", "No valid rows to process");
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    "No valid rows to process", 0, 0, 0, 0);
                
                JsonObject responseObj = new JsonObject();
                responseObj.addProperty("status", "success");
                responseObj.addProperty("job_id", jobId);
                responseObj.addProperty("reference_name", referenceName);
                responseObj.addProperty("inserted", 0);
                responseObj.addProperty("updated", 0);
                responseObj.addProperty("deleted", 0);
                responseObj.addProperty("failed", 0);
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(gson.toJson(responseObj));
                return;
            }
            
            jobDAO.updateJobItemsCount(jobId, totalRows);
            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.updateJobProgress(jobId, "Processing", "Processing " + totalRows + " rows");
            
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30, 
                "Validation complete. Processing " + totalRows + " rows...");
            
            logger.info("Processing {} validated rows", totalRows);

            // Step 10: Process each row
            int insertedCount = 0;
            int updatedCount = 0;
            int deletedCount = 0;
            int failedCount = 0;
            int validationErrorCount = errors != null ? errors.size() : 0; // Track validation errors

            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
            Connection conn = null;
            BulkUploadReferenceValidator refValidator = new BulkUploadReferenceValidator();
            BulkUploadNameValidator nameValidator = new BulkUploadNameValidator();
            Map<String, Integer> batchRefToId = new HashMap<>();
            Map<String, Integer> batchNameToId = new HashMap<>();
            
            try {
                conn = DatabaseConnection.getConnection();
                
                // Transaction strategy: 
                // - If cancelOnWarning is true: use single transaction (all-or-nothing)
                // - If cancelOnWarning is false: commit each row individually (preserve successful rows)
                if (cancelOnWarning) {
                    conn.setAutoCommit(false);
                } else {
                    conn.setAutoCommit(true); // Auto-commit mode for per-row commits
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

                    // For per-row commits, start a transaction for this row
                    if (!cancelOnWarning) {
                        conn.setAutoCommit(false);
                    }
                    
                    try {
                        if ("INSERT".equals(operation)) {
                            Policy policy = new Policy();
                            String name = rowData.get("Name").getAsString();
                            
                            // Validate name uniqueness within segment
                            if (name != null && !name.trim().isEmpty() && BulkUploadNameValidator.hasNameColumn("Policy")) {
                                Long segmentId = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    try {
                                        segmentId = determineSegmentId(segmentMode, segment, rowData);
                                    } catch (SQLException e) {
                                        logger.warn("Failed to determine segment ID for name validation: {}", e.getMessage());
                                    }
                                }
                                try {
                                    nameValidator.validateNameUniqueInSegment("Policy", name, segmentId, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }
                            
                            policy.setPrimaryName(name);
                            
                            // Handle Reference Number - generate if empty or if user put "POL-1" as placeholder
                            String refNumber = rowData.has("Ref.") && !rowData.get("Ref.").isJsonNull() ? 
                                rowData.get("Ref.").getAsString().trim() : "";
                            
                            // Validate reference uniqueness if provided (and not placeholder)
                            if (refNumber != null && !refNumber.isEmpty() && !refNumber.equalsIgnoreCase("POL-1")) {
                                try {
                                    refValidator.validateReferenceUnique("Policy", refNumber, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }
                            
                            if (refNumber == null || refNumber.isEmpty() || refNumber.equalsIgnoreCase("POL-1")) {
                                refNumber = generateDefaultRefCode();
                                // Validate auto-generated reference as well
                                try {
                                    refValidator.validateReferenceUnique("Policy", refNumber, conn);
                                } catch (IllegalArgumentException e) {
                                    // If auto-generated is duplicate, generate a new one (should be rare)
                                    refNumber = generateDefaultRefCode();
                                    refValidator.validateReferenceUnique("Policy", refNumber, conn);
                                }
                            }
                            policy.setRefNumber(refNumber);
                            
                            policy.setDescription(rowData.has("Description") && !rowData.get("Description").isJsonNull() ? 
                                rowData.get("Description").getAsString() : "");
                            
                            // Handle Internal - convert boolean
                            Integer internal = parseInternalValue(rowData.has("Internal") && !rowData.get("Internal").isJsonNull() ? 
                                rowData.get("Internal").getAsString() : null);
                            policy.setInternal(internal);
                            
                            policy.setUrl(rowData.has("URL") && !rowData.get("URL").isJsonNull() ? 
                                rowData.get("URL").getAsString() : "");
                            
                            // Handle dates
                            policy.setEffectiveDate(parseDateFromString(
                                rowData.has("Effective Date") && !rowData.get("Effective Date").isJsonNull() ? 
                                rowData.get("Effective Date").getAsString() : null));
                            policy.setEndDate(parseDateFromString(
                                rowData.has("End Date") && !rowData.get("End Date").isJsonNull() ? 
                                rowData.get("End Date").getAsString() : null));
                            
                            // Handle Parent_ID (resolve from batch first if parent in same file)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentRef = getString(rowData, "Parent Ref.");
                                String parentName = getString(rowData, "Parent Name");
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
                                if (parentId == null && (hasRef || hasName)) {
                                    try {
                                        if (hasRef && parentRef != null) {
                                            Policy byRef = policyDAO.getPolicyByRefNumber(parentRef.trim());
                                            if (byRef != null) parentId = byRef.getId();
                                        }
                                        if (parentId == null && hasName && parentName != null) {
                                            Policy byName = policyDAO.getPolicyByPrimaryName(parentName.trim());
                                            if (byName != null) parentId = byName.getId();
                                        }
                                    } catch (SQLException e) {
                                        throw new RuntimeException("Failed to resolve parent policy: " + e.getMessage(), e);
                                    }
                                    if (parentId == null) {
                                        String refPart = (hasRef && parentRef != null) ? "ref '" + parentRef.trim() + "'" : "";
                                        String namePart = (hasName && parentName != null) ? "name '" + parentName.trim() + "'" : "";
                                        throw new RuntimeException("Parent policy not found: " + (refPart.isEmpty() ? namePart : (namePart.isEmpty() ? refPart : refPart + " or " + namePart)));
                                    }
                                }
                            }
                            policy.setParentId(parentId);
                            
                            // Handle lookup fields - Python sends IDs directly
                            // Type is mandatory - fail if not provided
                            Integer policyTypeId = null;
                            if (rowData.has("Type_ID") && !rowData.get("Type_ID").isJsonNull()) {
                                policyTypeId = rowData.get("Type_ID").getAsInt();
                            } else if (rowData.has("Type") && !rowData.get("Type").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                policyTypeId = getLookupIdByName("policy_type", rowData.get("Type").getAsString());
                            }
                            // Fail if Type is not provided (mandatory field)
                            if (policyTypeId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Type is required and cannot be empty. Please specify a valid Type value.");
                            }
                            policy.setPolicyType(policyTypeId);
                            
                            // Lifecycle is mandatory - fail if not provided
                            Integer lifecycleStatusId = null;
                            if (rowData.has("Lifecycle_ID") && !rowData.get("Lifecycle_ID").isJsonNull()) {
                                lifecycleStatusId = rowData.get("Lifecycle_ID").getAsInt();
                            } else if (rowData.has("Lifecycle") && !rowData.get("Lifecycle").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                lifecycleStatusId = getLookupIdByName("policy_lifecycle_status", rowData.get("Lifecycle").getAsString());
                            }
                            // Fail if Lifecycle is not provided (mandatory field)
                            if (lifecycleStatusId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": Lifecycle is required and cannot be empty. Please specify a valid Lifecycle value.");
                            }
                            policy.setLifecycleStatus(lifecycleStatusId);
                            
                            Integer viewingId = null;
                            if (rowData.has("BUDG Viewing_ID") && !rowData.get("BUDG Viewing_ID").isJsonNull()) {
                                viewingId = rowData.get("BUDG Viewing_ID").getAsInt();
                            } else if (rowData.has("BUDG Viewing") && !rowData.get("BUDG Viewing").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                viewingId = getViewingIdByName(rowData.get("BUDG Viewing").getAsString());
                            }
                            // Use first ID from table as default if not provided
                            if (viewingId == null) {
                                viewingId = getFirstViewingId();
                                if (viewingId == null) {
                                    viewingId = 1; // Fallback if table is empty
                                }
                            }
                            policy.setIsPublic(viewingId);
                            
                            Integer statusId = null;
                            if (rowData.has("BUDG Status_ID") && !rowData.get("BUDG Status_ID").isJsonNull()) {
                                statusId = rowData.get("BUDG Status_ID").getAsInt();
                            } else if (rowData.has("BUDG Status") && !rowData.get("BUDG Status").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                statusId = getLookupIdByName("status", rowData.get("BUDG Status").getAsString());
                            }
                            // Use first ID from table as default if not provided
                            if (statusId == null) {
                                statusId = getFirstStatusId();
                                if (statusId == null) {
                                    statusId = 1; // Fallback if table is empty
                                }
                            }
                            policy.setStatus(statusId);
                            
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
                            policy.setCreatedById(finalUserId);
                            policy.setLastUpdateUserId(finalUserId);
                            
                            int policyId = policyDAO.createPolicy(policy);
                            
                            // Add to batch maps for parent-in-same-file resolution
                            if (name != null && !name.trim().isEmpty()) {
                                batchNameToId.put(name.trim().toLowerCase(), policyId);
                            }
                            if (refNumber != null && !refNumber.trim().isEmpty()) {
                                batchRefToId.put(refNumber.trim().toLowerCase(), policyId);
                            }
                            
                            // Create audit records
                            String authorName = getUserName(finalUserId);
                            policyDAO.createPolicyAuditRecords(policyId, authorName);
                            policyDAO.createPolicyAuditRecord(policyId);
                            
                            // Create stakeholder record for the resolved user
                            try {
                                // Get governance role from file - check both ID and name
                                Integer governanceRoleIdFromId = getInteger(rowData, "Governance Role_ID");
                                String governanceRoleName = getString(rowData, "Governance Role");
                                
                                logger.debug("Row {}: INSERT - Governance Role_ID from file: {}, Governance Role name: '{}'", 
                                        rowNumber, governanceRoleIdFromId, governanceRoleName);
                                
                                Integer governanceRoleIdFromName = null;
                                if (governanceRoleIdFromId == null && governanceRoleName != null && !governanceRoleName.trim().isEmpty()) {
                                    try {
                                        governanceRoleIdFromName = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn,
                                                governanceRoleName.trim(), "Policy");
                                    } catch (IllegalArgumentException e) {
                                        throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
                                    }
                                }
                                Integer governanceRoleId = coalesce(governanceRoleIdFromId, governanceRoleIdFromName);
                                
                                logger.debug("Row {}: Resolved Governance Role ID from name: {}, Final ID: {}", 
                                        rowNumber, governanceRoleIdFromName, governanceRoleId);
                                
                                // If no role specified, get default Policy Owner role
                                if (governanceRoleId == null) {
                                    governanceRoleId = getDefaultPolicyOwnerRole();
                                    logger.info("Row {}: No governance role specified, using default role: {}", rowNumber, governanceRoleId);
                                } else {
                                    logger.info("Row {}: Using governance role from file: {} (from {})", 
                                            rowNumber, governanceRoleId, 
                                            governanceRoleIdFromId != null ? "ID" : "name");
                                }
                                
                                if (governanceRoleId != null) {
                                    RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, finalUserId,
                                            governanceRoleId, "Policy", rowNumber);
                                }
                                if (governanceRoleId != null) {
                                    logger.info("Row {}: Creating stakeholder for policy {} with userId {} and roleId {}",
                                            rowNumber, policyId, finalUserId, governanceRoleId);

                                    // Prepare stakeholder data
                                    java.util.Map<String, Object> stakeholderData = new java.util.HashMap<>();
                                    stakeholderData.put("userId", finalUserId);
                                    stakeholderData.put("roleId", governanceRoleId);

                                    // Use DAO to create object_x_people (always creates new)
                                    int objectXPeopleId = policyDAO.createObjectXPeople(conn, stakeholderData, userId);
                                    logger.debug("Row {}: Created object_x_people with ID: {}", rowNumber, objectXPeopleId);

                                    // Use DAO to link (with duplicate prevention)
                                    policyDAO.linkStakeholderToPolicy(conn, policyId, objectXPeopleId, finalUserId);
                                    logger.debug("Row {}: Linked stakeholder to policy", rowNumber);

                                    // Create audit records
                                    String fullName = getUserName(finalUserId);
                                    policyDAO.createStakeholderAuditRecords(policyId, authorName, fullName, governanceRoleId);
                                    logger.info("Row {}: Successfully created stakeholder for policy {} (userId: {}, roleId: {})", 
                                            rowNumber, policyId, finalUserId, governanceRoleId);
                                } else {
                                    logger.warn("Row {}: Cannot create stakeholder for policy {}: no governance role found (userId: {})", 
                                            rowNumber, policyId, finalUserId);
                                }
                            } catch (Exception e) {
                                logger.error("Row {}: Error linking stakeholder to policy {}: {}", 
                                        rowNumber, policyId, e.getMessage(), e);
                                // Don't throw - stakeholder creation failure shouldn't fail the entire row
                            }
                            
                            // Assign object to segment (for INSERT operations when segmentation is enabled)
                            Long segmentIdToAssign = null;
                            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
                                
                                // For MULTIPLE or SPECIFIC mode, require explicit segment - fail if not provided
                                String mode = segmentMode.trim();
                                if (("MULTIPLE".equalsIgnoreCase(mode) || "SPECIFIC".equalsIgnoreCase(mode)) && segmentIdToAssign == null) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": Segment is required when '" + mode + "' mode is selected. Please specify a segment in the template or select one in the UI.");
                                }
                            } else {
                                // Check if Segment column exists in Excel even when segmentMode is null
                                String segmentName = getString(rowData, "Segment");
                                if (segmentName != null && !segmentName.trim().isEmpty()) {
                                    segmentIdToAssign = getSegmentIdByName(segmentName.trim());
                                }
                                // If no segmentMode and no segment in Excel, do not assign (prevent auto-assignment to Enterprise)
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
                                        parentId, segmentIdToAssign.intValue(), "Policy");
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
                                        (long) policyId, 
                                        "Policy", 
                                        segmentIdToAssign, 
                                        userId
                                    );
                                    logger.info("Assigned Policy {} to Segment {}", policyId, segmentIdToAssign);
                                } catch (Exception segEx) {
                                    // Segment assignment failure = row failure (BUDG behavior)
                                    logger.error("Failed to assign Policy {} to segment: {}", policyId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            } else {
                                // Log that no segment was assigned (this is now expected behavior when not specified)
                                logger.debug("Row {}: No segment specified for Policy {}, object will not be assigned to any segment", rowNumber, policyId);
                            }
                            
                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    policyId, 
                                    "Policy", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Policy {}: {}", policyId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            insertedCount++;
                            
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                                entityName + " created successfully", "info");

                        } else if ("UPDATE".equals(operation)) {
                            List<String> identityCols = Arrays.asList("ID", "Ref.", "Name");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols, "Policy", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByRef = null;
                            Integer idByName = null;
                            Integer idVal = getInteger(rowData, "ID");
                            if (idVal != null) {
                                try {
                                    Policy p = policyDAO.getPolicyById(idVal);
                                    if (p != null) idById = p.getId();
                                } catch (SQLException e) { /* ignore */ }
                            }
                            String refVal = getString(rowData, "Ref.");
                            if (refVal != null && !refVal.trim().isEmpty()) {
                                try {
                                    Policy p = policyDAO.getPolicyByRefNumber(refVal.trim());
                                    if (p != null) idByRef = p.getId();
                                } catch (SQLException e) { /* ignore */ }
                            }
                            String nameVal = getString(rowData, "Name");
                            if (nameVal != null && !nameVal.trim().isEmpty()) {
                                try {
                                    Policy p = policyDAO.getPolicyByPrimaryName(nameVal.trim());
                                    if (p != null) idByName = p.getId();
                                } catch (SQLException e) { /* ignore */ }
                            }
                            int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                            Integer policyId = coalesce(idById, idByRef, idByName);
                            if (policyId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": No policy found for the provided identity (ID, Ref., or Name).");
                            }
                            if (filledIdentity >= 2) {
                                Set<Integer> ids = new HashSet<>();
                                if (idById != null) ids.add(idById);
                                if (idByRef != null) ids.add(idByRef);
                                if (idByName != null) ids.add(idByName);
                                if (ids.size() > 1) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": ID, Ref. and Name refer to different policies.");
                                }
                            }
                            
                            Policy oldPolicy = policyDAO.getPolicyById(policyId);
                            if (oldPolicy == null) {
                                throw new IllegalArgumentException("Policy with ID " + policyId + " does not exist");
                            }
                            
                            Policy newPolicy = new Policy();
                            newPolicy.setId(policyId);
                            String nameFromRow = getString(rowData, "Name");
                            newPolicy.setPrimaryName(nameFromRow != null && !nameFromRow.trim().isEmpty() ? nameFromRow.trim() : oldPolicy.getPrimaryName());
                            
                            String refNumber = rowData.has("Ref.") && !rowData.get("Ref.").isJsonNull() ? 
                                rowData.get("Ref.").getAsString().trim() : "";
                            if (refNumber == null || refNumber.isEmpty()) {
                                refNumber = oldPolicy.getRefNumber();
                            }
                            newPolicy.setRefNumber(refNumber);
                            
                            String descriptionFromRow = getString(rowData, "Description");
                            newPolicy.setDescription(descriptionFromRow != null && !descriptionFromRow.trim().isEmpty()
                                ? descriptionFromRow.trim() : oldPolicy.getDescription());
                            
                            Integer internal = parseInternalValue(rowData.has("Internal") && !rowData.get("Internal").isJsonNull() ? 
                                rowData.get("Internal").getAsString() : null);
                            newPolicy.setInternal(internal != null ? internal : oldPolicy.getInternal());
                            
                            newPolicy.setUrl(rowData.has("URL") && !rowData.get("URL").isJsonNull() ? 
                                rowData.get("URL").getAsString() : "");
                            
                            newPolicy.setEffectiveDate(parseDateFromString(
                                rowData.has("Effective Date") && !rowData.get("Effective Date").isJsonNull() ? 
                                rowData.get("Effective Date").getAsString() : null));
                            newPolicy.setEndDate(parseDateFromString(
                                rowData.has("End Date") && !rowData.get("End Date").isJsonNull() ? 
                                rowData.get("End Date").getAsString() : null));
                            
                            // Handle Parent_ID (resolve from batch first if parent in same file)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentRef = getString(rowData, "Parent Ref.");
                                String parentName = getString(rowData, "Parent Name");
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
                                if (parentId == null && (hasRef || hasName)) {
                                    try {
                                        if (hasRef && parentRef != null) {
                                            Policy byRef = policyDAO.getPolicyByRefNumber(parentRef.trim());
                                            if (byRef != null) parentId = byRef.getId();
                                        }
                                        if (parentId == null && hasName && parentName != null) {
                                            Policy byName = policyDAO.getPolicyByPrimaryName(parentName.trim());
                                            if (byName != null) parentId = byName.getId();
                                        }
                                    } catch (SQLException e) {
                                        throw new RuntimeException("Failed to resolve parent policy: " + e.getMessage(), e);
                                    }
                                    if (parentId == null) {
                                        String refPart = (hasRef && parentRef != null) ? "ref '" + parentRef.trim() + "'" : "";
                                        String namePart = (hasName && parentName != null) ? "name '" + parentName.trim() + "'" : "";
                                        throw new RuntimeException("Parent policy not found: " + (refPart.isEmpty() ? namePart : (namePart.isEmpty() ? refPart : refPart + " or " + namePart)));
                                    }
                                }
                            }
                            // Prevent circular hierarchy: new parent must not be a descendant of this object
                            if (parentId != null && parentId > 0) {
                                try {
                                    if (BulkUploadParentValidator.wouldCreateParentCycle(conn, policyId, parentId, "Policy")) {
                                        throw new RuntimeException("Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.");
                                    }
                                } catch (SQLException e) {
                                    throw new RuntimeException("Failed to validate parent hierarchy: " + e.getMessage(), e);
                                }
                            }
                            newPolicy.setParentId(parentId);
                            
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
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) policyId, "Policy");
                                int effectiveChildSegmentId = (newSegmentIdForParent != null) ? newSegmentIdForParent.intValue()
                                        : (currentSegmentId != null ? currentSegmentId.intValue() : 1);
                                SegmentValidationService parentValidator = new SegmentValidationService();
                                SegmentValidationService.ValidationResult parentResult = parentValidator.validateParentChildSegment(
                                        parentId, effectiveChildSegmentId, "Policy");
                                if (!parentResult.isValid) {
                                    throw new RuntimeException("Parent segment validation failed: " + parentResult.message);
                                }
                            }
                            
                            Integer policyTypeId = null;
                            if (rowData.has("Type_ID") && !rowData.get("Type_ID").isJsonNull()) {
                                policyTypeId = rowData.get("Type_ID").getAsInt();
                            } else if (rowData.has("Type") && !rowData.get("Type").isJsonNull()) {
                                policyTypeId = getLookupIdByName("policy_type", rowData.get("Type").getAsString());
                            }
                            newPolicy.setPolicyType(policyTypeId != null ? policyTypeId : oldPolicy.getPolicyType());
                            
                            Integer lifecycleStatusId = null;
                            if (rowData.has("Lifecycle_ID") && !rowData.get("Lifecycle_ID").isJsonNull()) {
                                lifecycleStatusId = rowData.get("Lifecycle_ID").getAsInt();
                            } else if (rowData.has("Lifecycle") && !rowData.get("Lifecycle").isJsonNull()) {
                                lifecycleStatusId = getLookupIdByName("policy_lifecycle_status", rowData.get("Lifecycle").getAsString());
                            }
                            newPolicy.setLifecycleStatus(lifecycleStatusId != null ? lifecycleStatusId : oldPolicy.getLifecycleStatus());
                            
                            Integer viewingId = null;
                            if (rowData.has("BUDG Viewing_ID") && !rowData.get("BUDG Viewing_ID").isJsonNull()) {
                                viewingId = rowData.get("BUDG Viewing_ID").getAsInt();
                            } else if (rowData.has("BUDG Viewing") && !rowData.get("BUDG Viewing").isJsonNull()) {
                                viewingId = getViewingIdByName(rowData.get("BUDG Viewing").getAsString());
                            }
                            newPolicy.setIsPublic(viewingId != null ? viewingId : oldPolicy.getIsPublic());
                            
                            Integer statusId = null;
                            if (rowData.has("BUDG Status_ID") && !rowData.get("BUDG Status_ID").isJsonNull()) {
                                statusId = rowData.get("BUDG Status_ID").getAsInt();
                            } else if (rowData.has("BUDG Status") && !rowData.get("BUDG Status").isJsonNull()) {
                                statusId = getLookupIdByName("status", rowData.get("BUDG Status").getAsString());
                            }
                            newPolicy.setStatus(statusId != null ? statusId : oldPolicy.getStatus());
                            
                            // User resolution - always use userId from session
                            newPolicy.setLastUpdateUserId(userId);
                            
                            String userName = getUserName(userId);
                            policyDAO.updatePolicy(newPolicy, userName);
                            
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
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) policyId, "Policy");
                                    
                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            policyId, newSegmentId.intValue(), "Policy", parentId);
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Segment validation failed: " + result.message);
                                        }
                                        
                                        
                                        validateSegmentAccess(userId, newSegmentId, rowNumber);
                                        
                                        // Assign new segment
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) policyId, 
                                            "Policy", 
                                            newSegmentId, 
                                            userId
                                        );
                                        logger.info("Assigned Policy {} to Segment {} (updated)", policyId, newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Policy {} to segment: {}", policyId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Handle Governance Role update - create/update stakeholder
                            try {
                                // Get governance role from file
                                Integer governanceRoleIdFromId = getInteger(rowData, "Governance Role_ID");
                                String governanceRoleName = getString(rowData, "Governance Role");
                                Integer governanceRoleIdFromName = null;
                                if (governanceRoleIdFromId == null && governanceRoleName != null && !governanceRoleName.trim().isEmpty()) {
                                    try {
                                        governanceRoleIdFromName = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn,
                                                governanceRoleName.trim(), "Policy");
                                    } catch (IllegalArgumentException e) {
                                        throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
                                    }
                                }
                                Integer governanceRoleId = coalesce(governanceRoleIdFromId, governanceRoleIdFromName);
                                
                                logger.debug("Row {}: UPDATE - Governance Role_ID from file: {}, Governance Role name: {}, Resolved ID from name: {}, Final ID: {}", 
                                        rowNumber, governanceRoleIdFromId, governanceRoleName, governanceRoleIdFromName, governanceRoleId);
                                
                                // If Governance Role is provided, update/create stakeholder
                                if (governanceRoleId != null) {
                                    // User resolution: use User Email from file if provided, otherwise use uploader
                                    Integer finalUserId = userId; // Default to uploader
                                    String userEmail = getString(rowData, "User Email");
                                    if (userEmail != null && !userEmail.trim().isEmpty()) {
                                        Integer resolvedUserId = getUserIdByEmail(userEmail.trim());
                                        if (resolvedUserId != null) {
                                            finalUserId = resolvedUserId;
                                            logger.info("Row {}: Resolved User Email '{}' to User ID {}", rowNumber, userEmail, finalUserId);
                                        } else {
                                            logger.warn("Row {}: User Email '{}' not found, using uploader ID {} as stakeholder", rowNumber, userEmail, userId);
                                        }
                                    }
                                    RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, finalUserId,
                                            governanceRoleId, "Policy", rowNumber);

                                    logger.info("Row {}: Creating/updating stakeholder for policy {} with userId {} and roleId {}",
                                            rowNumber, policyId, finalUserId, governanceRoleId);

                                    // Prepare stakeholder data
                                    java.util.Map<String, Object> stakeholderData = new java.util.HashMap<>();
                                    stakeholderData.put("userId", finalUserId);
                                    stakeholderData.put("roleId", governanceRoleId);

                                    // Use DAO to create object_x_people (always creates new)
                                    int objectXPeopleId = policyDAO.createObjectXPeople(conn, stakeholderData, userId);

                                    // Use DAO to link (with duplicate prevention)
                                    policyDAO.linkStakeholderToPolicy(conn, policyId, objectXPeopleId, finalUserId);

                                    // Create audit records
                                    String authorName = getUserName(finalUserId);
                                    policyDAO.createStakeholderAuditRecords(policyId, userName, authorName, governanceRoleId);
                                    logger.info("Row {}: Successfully created/updated stakeholder for policy {}", rowNumber, policyId);
                                } else {
                                    logger.debug("Row {}: No Governance Role provided in update, skipping stakeholder update", rowNumber);
                                }
                            } catch (Exception e) {
                                logger.error("Row {}: Error updating stakeholder for policy {}: {}", rowNumber, policyId, e.getMessage(), e);
                                // Don't fail the update if stakeholder creation fails
                            }
                            
                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    policyId, 
                                    "Policy", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Policy {}: {}", policyId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            updatedCount++;
                            
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS", 
                                entityName + " updated successfully", "info");

                        } else if ("DELETE".equals(operation)) {
                            int policyId = rowData.get("ID").getAsInt();
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "policy", policyId);
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
                                Policy existingPolicy = policyDAO.getPolicyById(policyId);
                                if (existingPolicy == null) {
                                    logger.info("Policy with ID {} does not exist, skipping deletion (idempotent)", policyId);
                                    continue;
                                }
                                policyDAO.deletePolicy(policyId, userId);
                                deletedCount++;
                                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                                jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    entityName + " deleted successfully", "info");
                            }
                        }
                        
                        // Commit this row if using per-row commits
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
                            // All-or-nothing mode: rollback entire transaction and stop
                            conn.rollback();
                            throw new ServletException("Processing failed at row " + rowNumber + ": " + e.getMessage());
                        } else {
                            // Per-row commit mode: rollback only this row and continue
                            try {
                                conn.rollback();
                                logger.debug("Rolled back row {} due to error", rowNumber);
                            } catch (SQLException rollbackEx) {
                                logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(), rollbackEx);
                            }
                        }
                    } finally {
                        // Reset auto-commit for next row if using per-row commits
                        if (!cancelOnWarning) {
                            try {
                                conn.setAutoCommit(true);
                            } catch (SQLException e) {
                                logger.error("Error resetting auto-commit: {}", e.getMessage(), e);
                            }
                        }
                    }
                }

                // Final commit for all-or-nothing mode
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

            // Step 11: Check for partial completion and update job status
            int successfulCount = insertedCount + updatedCount + deletedCount;
            int totalFailed = validationErrorCount + failedCount;
            boolean hasFailures = totalFailed > 0;
            boolean isPartiallyCompleted = (successfulCount > 0) && hasFailures;
            boolean isAllFailed = (successfulCount == 0) && hasFailures;
            
            if (isAllFailed) {
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount);
                jobDAO.updateJobProgress(jobId, "Failed", 
                    String.format("Completed: %d inserted, %d updated, %d deleted, %d failed", 
                                 insertedCount, updatedCount, deletedCount, totalFailed));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Processing failed: all rows failed",
                    insertedCount, updatedCount, deletedCount, totalFailed);

                logger.info("Bulk upload failed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {} ({} validation, {} processing)", 
                           insertedCount, updatedCount, deletedCount, totalFailed, validationErrorCount, failedCount);
                
                sendNotification(userId, "Policy", uploadOption, insertedCount, updatedCount, 
                                deletedCount, totalFailed, false, "All rows failed during processing", jobId);
            } else if (isPartiallyCompleted) {
                jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount); // Only successful operations
                jobDAO.updateJobProgress(jobId, "Partially Completed", 
                    String.format("Completed: %d inserted, %d updated, %d deleted, %d failed", 
                                 insertedCount, updatedCount, deletedCount, totalFailed));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                    "Processing complete with some failures",
                    insertedCount, updatedCount, deletedCount, totalFailed);

                logger.info("Bulk upload partially completed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {} ({} validation, {} processing)", 
                           insertedCount, updatedCount, deletedCount, totalFailed, validationErrorCount, failedCount);
                
                // Send notification for partial completion
                sendNotification(userId, "Policy", uploadOption, insertedCount, updatedCount, 
                                deletedCount, totalFailed, true, null, jobId);
            } else {
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobItemsCount(jobId, successfulCount);
                jobDAO.updateJobProgress(jobId, "Completed", 
                    String.format("Completed: %d inserted, %d updated, %d deleted, %d failed", 
                                 insertedCount, updatedCount, deletedCount, totalFailed));

                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    String.format("Bulk upload completed successfully"),
                    insertedCount, updatedCount, deletedCount, totalFailed);

                logger.info("Bulk upload completed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {} ({} validation, {} processing)", 
                           insertedCount, updatedCount, deletedCount, totalFailed, validationErrorCount, failedCount);
                
                // Send success notification
                sendNotification(userId, "Policy", uploadOption, insertedCount, updatedCount, 
                                deletedCount, totalFailed, true, null, jobId);
            }

            // Step 12: Return success response
            // totalFailed already calculated above (line 982)
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("inserted", insertedCount);
            successResponse.addProperty("updated", updatedCount);
            successResponse.addProperty("deleted", deletedCount);
            successResponse.addProperty("failed", totalFailed);
            successResponse.addProperty("report_url", "/api/bulk/policy/report/" + jobId);

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
                                sendNotification(userId, "Policy", uploadOption, 0, 0, 0, 0, 
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
            n.setFacetType("policy");
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

    private Integer getLookupIdByName(String tableName, String primaryName) {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM " + tableName + " WHERE LOWER(PrimaryName) = LOWER(?) LIMIT 1")) {
            ps.setString(1, primaryName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting lookup ID by name from table {}: {}", tableName, primaryName, e);
        }
        return null;
    }

    /**
     * Get first ID from lookup table (for default values)
     */
    @SuppressWarnings("unused")
    private Integer getFirstLookupId(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT ID FROM " + tableName + " ORDER BY ID ASC LIMIT 1")) {
            if (rs.next()) {
                return rs.getInt("ID");
            }
        } catch (SQLException e) {
            logger.error("Error getting first lookup ID from table {}: {}", tableName, e.getMessage());
        }
        return null;
    }

    /**
     * Get first ID from viewing table (special case - uses Name column)
     */
    private Integer getFirstViewingId() {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT id FROM viewing ORDER BY id ASC LIMIT 1")) {
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            logger.error("Error getting first viewing ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get first ID from status table (special case - uses primaryname column)
     */
    private Integer getFirstStatusId() {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT ID FROM status ORDER BY ID ASC LIMIT 1")) {
            if (rs.next()) {
                return rs.getInt("ID");
            }
        } catch (SQLException e) {
            logger.error("Error getting first status ID: {}", e.getMessage());
        }
        return null;
    }

    private Integer getViewingIdByName(String viewingName) {
        if (viewingName == null || viewingName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM viewing WHERE Name = ? LIMIT 1")) {
            ps.setString(1, viewingName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting viewing ID by name: {}", viewingName, e);
        }
        return null;
    }

    private String parseDateFromString(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = dateStr.trim();
        
        Pattern pattern = Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]+)\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(trimmed);
        
        if (matcher.matches()) {
            try {
                String day = matcher.group(1);
                String monthName = matcher.group(2);
                String year = matcher.group(3);
                
                String[] monthNames = {"january", "february", "march", "april", "may", "june",
                                      "july", "august", "september", "october", "november", "december"};
                int month = -1;
                String monthLower = monthName.toLowerCase();
                for (int i = 0; i < monthNames.length; i++) {
                    if (monthNames[i].startsWith(monthLower) || monthLower.startsWith(monthNames[i].substring(0, 3))) {
                        month = i + 1;
                        break;
                    }
                }
                
                if (month == -1) {
                    logger.warn("Could not parse month from date string: {}", dateStr);
                    return null;
                }
                
                int dayInt = Integer.parseInt(day);
                int yearInt = Integer.parseInt(year);
                return String.format("%04d-%02d-%02d", yearInt, month, dayInt);
                
            } catch (Exception e) {
                logger.warn("Error parsing date format '25th May 2018': {}", dateStr, e);
            }
        }
        
        try {
            java.sql.Date.valueOf(trimmed);
            return trimmed;
        } catch (IllegalArgumentException e) {
        }
        
        String[] formats = {
            "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "MM-dd-yyyy",
            "dd.MM.yyyy", "MM.dd.yyyy", "yyyy/MM/dd", "yyyy-MM-dd"
        };
        
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false);
                Date date = sdf.parse(trimmed);
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
                return outputFormat.format(date);
            } catch (ParseException e) {
            }
        }
        
        logger.warn("Could not parse date string: {}", dateStr);
        return null;
    }

    private Integer parseInternalValue(String internalStr) {
        if (internalStr == null || internalStr.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = internalStr.trim().toLowerCase();
        
        if (trimmed.equals("true") || trimmed.equals("yes") || trimmed.equals("1") || 
            trimmed.equals("y") || trimmed.equals("t")) {
            return 1;
        } else if (trimmed.equals("false") || trimmed.equals("no") || trimmed.equals("0") || 
                   trimmed.equals("n") || trimmed.equals("f")) {
            return 0;
        }
        
        try {
            int value = Integer.parseInt(trimmed);
            return (value != 0) ? 1 : 0;
        } catch (NumberFormatException e) {
            logger.warn("Could not parse Internal value: {}", internalStr);
            return null;
        }
    }

    private String generateShortReferenceName() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'POL-%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("POL-")) {
                        try {
                            String numberPart = refName.substring(4);
                            int number = Integer.parseInt(numberPart);
                            if (number > maxNumber) {
                                maxNumber = number;
                            }
                        } catch (NumberFormatException e) {
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
        return "POL-" + nextNumber;
    }

    private String generateDefaultRefCode() {
        int nextId = 1;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(ID), 0) + 1 AS next_id FROM policy")) {
            if (rs.next()) {
                nextId = rs.getInt("next_id");
            }
        } catch (SQLException e) {
            logger.warn("Error generating default ref code, using fallback ID: {}", e.getMessage());
        }
        return "POL-" + nextId;
    }

    private String determineJobType(String entityName, String uploadOption) {
        // Always return "bulk_upload" for consistency with other bulk upload servlets
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
    
    // Helper methods for stakeholder management
    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
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
    
    // Stakeholder methods now handled by PolicyDAO
    
    private Integer getDefaultPolicyOwnerRole() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First get Policy module ID
            String moduleQuery = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER('Policy') LIMIT 1";
            try (java.sql.PreparedStatement moduleStmt = conn.prepareStatement(moduleQuery);
                 java.sql.ResultSet moduleRs = moduleStmt.executeQuery()) {
                
                if (moduleRs.next()) {
                    int moduleId = moduleRs.getInt("id");
                    logger.info("Found Policy module ID: {}", moduleId);
                    
                    // Get first role for Policy module (typically Policy Owner)
                    String roleQuery = "SELECT id FROM object_role WHERE module = ? ORDER BY id ASC LIMIT 1";
                    try (java.sql.PreparedStatement roleStmt = conn.prepareStatement(roleQuery)) {
                        roleStmt.setInt(1, moduleId);
                        try (java.sql.ResultSet roleRs = roleStmt.executeQuery()) {
                            if (roleRs.next()) {
                                int roleId = roleRs.getInt("id");
                                logger.info("Found default Policy Owner role ID: {}", roleId);
                                return roleId;
                            } else {
                                logger.warn("No roles found for Policy module (moduleId: {})", moduleId);
                            }
                        }
                    }
                } else {
                    logger.warn("Policy module not found in database");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default Policy Owner role: {}", e.getMessage(), e);
        }
        logger.error("Failed to get default Policy Owner role - returning null");
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

