package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.RegulationDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.model.Regulation;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.bulk.common.BulkUploadParentValidator;
import com.example.budg_v2.bulk.common.BulkUploadReferenceValidator;
import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.common.BulkUploadFacetPermissionHelper;
import com.example.budg_v2.bulk.common.BulkUploadNameValidator;
import com.example.budg_v2.bulk.roles.util.RoleHandlerUtil;
import com.example.budg_v2.bulk.common.BulkUploadUtil;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@WebServlet("/api/bulk/regulation/upload")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, // 2MB
        maxFileSize = 1024 * 1024 * 10, // 10MB
        maxRequestSize = 1024 * 1024 * 50 // 50MB
)
public class RegulationBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(RegulationBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("regulation"); }

    private final JobDAO jobDAO = new JobDAO();
    private final RegulationDAO regulationDAO = new RegulationDAO();

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

            logger.info(
                    "Upload parameters - Type: {}, Entity: {}, Option: {}, ErrorHandling: {}, UserId: {}, SegmentMode: {}, Segment: {}",
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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "Regulation";
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
            String fileName = "regulation_" + timestamp + "_" + uuid + ".xlsx";

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
            // Use entity from request parameter, fallback to "Regulation" if not provided (entityName set above)
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
                metadata.addProperty("entity", entity != null ? entity : "Regulation");
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
            validationRequest.addProperty("entity", entity != null ? entity : "Regulation");
            validationRequest.addProperty("user_id", userId);

            // Add segment parameters if provided (for INSERT operations only)
            if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                validationRequest.addProperty("segment_mode", segmentMode);
                if (segment != null && !segment.trim().isEmpty()) {
                    validationRequest.addProperty("segment", segment);
                }
            }

            // Add column mappings if provided
            // Frontend sends: {fieldName: excelColumnName} e.g., {"Publication Date":
            // "تاريخ النشر"}
            // Python expects: {excelColumnName: fieldName} e.g., {"تاريخ النشر":
            // "Publication Date"}
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
                    // Don't fail the upload if mapping parsing fails, just continue without
                    // mappings
                }
            }

            logger.info("Calling Python validation service: {}", PYTHON_SERVICE_URL);
            logger.info("Validation request - file_path: {}, upload_option: {}", absoluteFile.getAbsolutePath(),
                    uploadOption);
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
                    userFriendlyMessage = "Validation service error: "
                            + (errorMessage != null ? errorMessage : "Unknown error");
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
            boolean isInvalid = "invalid".equals(validationStatus);
            boolean isError = "error".equals(validationStatus);
            boolean shouldFail = isError;
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);

            JsonArray validatedData = validationResponse.has("data") ? validationResponse.getAsJsonArray("data")
                    : new JsonArray();

            if (isInvalid) {
                if (cancelOnWarning || validatedData.size() == 0) {
                    shouldFail = true;
                }
            }

            // Do not process partial data when Python reported blocking custom-field errors
            if (isInvalid && !shouldFail && validationResponse.has("errors")) {
                JsonArray errArr = validationResponse.getAsJsonArray("errors");
                for (int i = 0; i < errArr.size(); i++) {
                    JsonElement el = errArr.get(i);
                    if (el == null || !el.isJsonObject()) {
                        continue;
                    }
                    JsonObject eo = el.getAsJsonObject();
                    if (!eo.has("error_code")) {
                        continue;
                    }
                    String code = eo.get("error_code").getAsString();
                    if ("CUSTOM_FIELD_MANDATORY".equals(code) || "CUSTOM_FIELD_VALIDATION_ERROR".equals(code)
                            || "CUSTOM_FIELD_VALIDATION_EXCEPTION".equals(code)) {
                        shouldFail = true;
                        break;
                    }
                }
            }

            if (shouldFail) {
                // Validation failed
                JsonArray errors = validationResponse.has("errors") ? validationResponse.getAsJsonArray("errors")
                        : new JsonArray();
                logger.warn("Validation failed with {} errors", errors.size());

                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Validation failed");

                // Broadcast failure via WebSocket
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Validation failed with " + errors.size() + " errors", 0, 0, 0, errors.size());

                // Store validation errors in Job_Report_Item
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
                errorResponse.addProperty("message",
                        validationResponse.has("message") ? validationResponse.get("message").getAsString()
                                : "Validation failed");
                errorResponse.addProperty("inserted", 0);
                errorResponse.addProperty("updated", 0);
                errorResponse.addProperty("deleted", 0);
                errorResponse.addProperty("failed", errors.size());
                errorResponse.add("errors", errors);

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(gson.toJson(errorResponse));
                return;
            }

            // Step 9: Validation succeeded, process the data
            int totalRows = validatedData.size();

            jobDAO.updateJobItemsCount(jobId, totalRows);
            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.updateJobProgress(jobId, "Processing", "Processing " + totalRows + " rows");

            // Broadcast processing start
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                    "Validation complete. Processing " + totalRows + " rows...");

            logger.info("Processing {} validated rows (Async)", totalRows);

            // Start async processing
            final int finalJobId = jobId;
            final int finalUserId = userId;
            final String finalErrorHandling = errorHandling;
            final String finalUploadOption = uploadOption;
            final String finalSegmentMode = segmentMode;
            final String finalSegment = segment;
            final String finalEntityName = entityName;

            final JsonArray finalValidatedData = validatedData;

            Thread processingThread = new Thread(() -> {
                processRegulationData(finalJobId, finalValidatedData, finalUserId, finalErrorHandling,
                        finalUploadOption, finalSegmentMode, finalSegment, finalEntityName);
            });
            processingThread.setDaemon(false);
            processingThread.start();

            // Step 12: Return success response immediately
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", jobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("message", "Processing " + totalRows + " rows...");
            successResponse.addProperty("report_url", "/api/bulk/regulation/report/" + jobId);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(successResponse));

        } catch (Exception e) {
            logger.error("Error during bulk upload", e);

            // Update job status if job was created (do not overwrite Completed/Partially
            // Completed)
            if (jobId > 0) {
                try {
                    String currentStatus = jobDAO.getJobStatus(jobId);
                    if (currentStatus == null
                            || (!"Completed".equals(currentStatus) && !"Partially Completed".equals(currentStatus))) {
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Unexpected error: " + e.getMessage());

                        // Send failure notification if we have userId
                        if (userIdStr != null && uploadOption != null) {
                            try {
                                int userId = Integer.parseInt(userIdStr);
                                sendNotification(userId, "Regulation", uploadOption, 0, 0, 0, 0,
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
                    failureMsg = errorMessage.length() > 200 ? errorMessage.substring(0, 197) + "..." : errorMessage;
                }
                n.setMessage(failureMsg);
                n.setEventType("UPLOAD_FAILED");
            }

            n.setChannel("ui");
            n.setFacetType("regulation");
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

    /**
     * Get lookup ID by table name and primary name
     */
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
     * Parse date from string format like "25th May 2018" to SQL Date format
     * (YYYY-MM-DD)
     */
    private String parseDateFromString(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        String trimmed = dateStr.trim();

        // Try to parse "25th May 2018" format
        Pattern pattern = Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]+)\\s+(\\d{4})",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(trimmed);

        if (matcher.matches()) {
            try {
                String day = matcher.group(1);
                String monthName = matcher.group(2);
                String year = matcher.group(3);

                // Map month names to numbers
                String[] monthNames = { "january", "february", "march", "april", "may", "june",
                        "july", "august", "september", "october", "november", "december" };
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

                // Format as YYYY-MM-DD
                int dayInt = Integer.parseInt(day);
                int yearInt = Integer.parseInt(year);
                return String.format("%04d-%02d-%02d", yearInt, month, dayInt);

            } catch (Exception e) {
                logger.warn("Error parsing date format '25th May 2018': {}", dateStr, e);
            }
        }

        // Try standard SQL date format (YYYY-MM-DD)
        try {
            java.sql.Date.valueOf(trimmed);
            return trimmed;
        } catch (IllegalArgumentException e) {
            // Not in YYYY-MM-DD format, try other formats
        }

        // Try other common formats
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
                // Try next format
            }
        }

        logger.warn("Could not parse date string: {}", dateStr);
        return null;
    }

    /**
     * Generate short reference name for bulk upload job
     * Format: REG-{nextNumber}
     */
    private String generateShortReferenceName() {
        int nextNumber = 1;
        try (Connection conn = DatabaseConnection.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'REG-%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    // Extract number from "REG-12" format
                    if (refName != null && refName.startsWith("REG-")) {
                        try {
                            String numberPart = refName.substring(4);
                            int number = Integer.parseInt(numberPart);
                            if (number > maxNumber) {
                                maxNumber = number;
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid format
                        }
                    }
                }
                if (maxNumber > 0) {
                    nextNumber = maxNumber + 1;
                }
            }
        } catch (SQLException e) {
            logger.warn("Error generating short reference name, using fallback number: {}", e.getMessage());
            // fallback will use 1
        }
        return "REG-" + nextNumber;
    }

    /**
     * Generate default reference code for Regulation
     * Format: REG-{nextId}
     */
    private String generateDefaultRefCode() {
        int nextId = 1;
        try (Connection conn = DatabaseConnection.getConnection();
                java.sql.Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt
                        .executeQuery("SELECT COALESCE(MAX(ID), 0) + 1 AS next_id FROM regulation")) {
            if (rs.next()) {
                nextId = rs.getInt("next_id");
            }
        } catch (SQLException e) {
            logger.warn("Error generating default ref code, using fallback ID: {}", e.getMessage());
            // fallback will use 1
        }
        return "REG-" + nextId;
    }

    /**
     * Detect if this is a Role Template (multiple rows with same Regulation ID)
     */
    private boolean detectRoleTemplate(JsonArray validatedData) {
        if (validatedData.size() < 2) {
            return false; // Need at least 2 rows to be a role template
        }

        Map<Integer, Integer> regulationIdCounts = new HashMap<>();

        for (int i = 0; i < validatedData.size(); i++) {
            JsonObject rowData = validatedData.get(i).getAsJsonObject();

            // Check if row has Regulation ID (required for Role Template)
            if (rowData.has("Regulation ID") && !rowData.get("Regulation ID").isJsonNull()) {
                int regulationId = rowData.get("Regulation ID").getAsInt();
                regulationIdCounts.put(regulationId, regulationIdCounts.getOrDefault(regulationId, 0) + 1);
            }
        }

        // If any Regulation ID appears more than once, it's a Role Template
        for (Integer count : regulationIdCounts.values()) {
            if (count > 1) {
                logger.info("Detected Role Template: Multiple rows with same Regulation ID");
                return true;
            }
        }

        return false;
    }

    /**
     * Process Role Template: Group rows by Regulation ID and process stakeholders
     */
    private void processRoleTemplate(Connection conn, JsonArray validatedData, int jobId,
            int userId, boolean cancelOnWarning,
            int insertedCount, int updatedCount,
            int deletedCount, int failedCount, String entityName) throws Exception {

        // Group rows by Regulation ID
        Map<Integer, List<JsonObject>> regulationGroups = new HashMap<>();

        for (int i = 0; i < validatedData.size(); i++) {
            JsonObject rowData = validatedData.get(i).getAsJsonObject();

            if (!rowData.has("Regulation ID") || rowData.get("Regulation ID").isJsonNull()) {
                // Row without Regulation ID - skip (should not happen in Role Template)
                logger.warn("Row {} does not have Regulation ID, skipping", rowData.get("row_number").getAsInt());
                continue;
            }

            int regulationId = rowData.get("Regulation ID").getAsInt();
            regulationGroups.computeIfAbsent(regulationId, k -> new ArrayList<>()).add(rowData);
        }

        // Process each group
        int processedGroups = 0;
        int totalGroups = regulationGroups.size();

        for (Map.Entry<Integer, List<JsonObject>> entry : regulationGroups.entrySet()) {
            int regulationId = entry.getKey();
            List<JsonObject> rows = entry.getValue();

            // Broadcast progress
            processedGroups++;
            int progressPercent = 30 + (int) ((processedGroups / (double) totalGroups) * 65);
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                    String.format("Processing Regulation ID %d (%d stakeholders)", regulationId, rows.size()),
                    insertedCount, updatedCount, deletedCount, failedCount);

            try {
                // Verify regulation exists
                Regulation regulation = regulationDAO.getRegulationById(regulationId);
                if (regulation == null) {
                    throw new IllegalArgumentException("Regulation with ID " + regulationId + " does not exist");
                }

                // Process all stakeholders for this regulation
                String userName = getUserName(userId);
                int successCount = 0;
                int errorCount = 0;

                for (JsonObject rowData : rows) {
                    int rowNumber = rowData.get("row_number").getAsInt();

                    try {
                        // Extract stakeholder information
                        Integer governanceRoleId = null;
                        Integer stakeholderUserId = userId; // Default to uploader

                        if (rowData.has("Governance Role_ID") && !rowData.get("Governance Role_ID").isJsonNull()) {
                            governanceRoleId = rowData.get("Governance Role_ID").getAsInt();
                        }

                        if (rowData.has("User_ID") && !rowData.get("User_ID").isJsonNull()) {
                            stakeholderUserId = rowData.get("User_ID").getAsInt();
                        }

                        // Create/update stakeholder if Governance Role is provided
                        if (governanceRoleId != null && stakeholderUserId != null) {
                            regulationDAO.createOrUpdateStakeholderFromBulk(
                                    regulationId,
                                    stakeholderUserId,
                                    governanceRoleId,
                                    userId,
                                    userName);
                            successCount++;

                            // Create success report item
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    String.format("Stakeholder created/updated for Regulation ID %d", regulationId),
                                    "info");
                        } else {
                            // Missing required fields
                            errorCount++;
                            failedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "MISSING_FIELDS",
                                    "Governance Role_ID and User_ID are required for Role Template", "error");

                            if (cancelOnWarning) {
                                conn.rollback();
                                throw new ServletException("Missing required fields at row " + rowNumber);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing stakeholder row {} for Regulation ID {}: {}",
                                rowNumber, regulationId, e.getMessage(), e);
                        errorCount++;
                        failedCount++;

                        int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "PROCESSING_ERROR",
                                e.getMessage(), "error");

                        if (cancelOnWarning) {
                            conn.rollback();
                            throw new ServletException("Processing failed at row " + rowNumber + ": " + e.getMessage());
                        }
                    }
                }

                logger.info("Processed {} stakeholders for Regulation ID {}: {} success, {} errors",
                        rows.size(), regulationId, successCount, errorCount);

            } catch (Exception e) {
                logger.error("Error processing Regulation ID {}: {}", regulationId, e.getMessage(), e);

                // Mark all rows for this regulation as failed
                for (JsonObject rowData : rows) {
                    failedCount++;
                    int rowNumber = rowData.get("row_number").getAsInt();
                    int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "error", rowNumber);
                    jobDAO.createJobReportItemMessage(reportItemId, "PROCESSING_ERROR",
                            e.getMessage(), "error");
                }

                if (cancelOnWarning) {
                    conn.rollback();
                    throw new ServletException(
                            "Processing failed for Regulation ID " + regulationId + ": " + e.getMessage());
                }
            }
        }
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

    // Helper methods for stakeholder management
    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private Integer getInteger(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : null;
    }

    @SafeVarargs
    private <T> T coalesce(T... values) {
        if (values == null)
            return null;
        for (T v : values) {
            if (v != null)
                return v;
        }
        return null;
    }

    private Integer getRoleIdByName(String roleName) {
        if (roleName == null || roleName.trim().isEmpty())
            return null;
        try (Connection conn = DatabaseConnection.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM object_role WHERE LOWER(primaryname) = LOWER(?) LIMIT 1")) {
            ps.setString(1, roleName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("id");
            }
        } catch (SQLException e) {
            logger.error("Error getting role ID by name: {}", roleName, e);
        }
        return null;
    }

    private int ensureObjectXPeople(int userId, int roleId, int currentUserId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Try find existing oxp for same ipid and role
            String findSql = "SELECT ID FROM object_x_people WHERE ipid = ? AND RoleID = ? LIMIT 1";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(findSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, roleId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ID");
                    }
                }
            }
            String insertSql = """
                        INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                        VALUES (NULL, ?, ?, 2, 1, ?)
                    """;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(insertSql,
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setInt(2, roleId);
                ps.setInt(3, currentUserId);
                ps.executeUpdate();
                try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next())
                        return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create object_x_people for userId=" + userId + ", roleId=" + roleId);
    }

    /** Must use the bulk-upload {@code conn} so the link participates in the same transaction as the regulation insert. */
    private void linkStakeholderToRegulation(Connection conn, int regulationId, int objectXPeopleId)
            throws SQLException {
        String sql = """
                INSERT INTO regulation_x_objectxpeople (RegulationID, Object_x_ipid, CreateDatetime)
                VALUES (?, ?, NOW())
                """;
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            ps.setInt(2, objectXPeopleId);
            try {
                ps.executeUpdate();
            } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
                // already linked, ignore
            }
        }
    }

    private Integer getDefaultRegulationOwnerRole() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First get Regulation module ID
            String moduleQuery = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER('Regulation') LIMIT 1";
            try (java.sql.PreparedStatement moduleStmt = conn.prepareStatement(moduleQuery);
                    java.sql.ResultSet moduleRs = moduleStmt.executeQuery()) {

                if (moduleRs.next()) {
                    int moduleId = moduleRs.getInt("id");
                    logger.info("Found Regulation module ID: {}", moduleId);

                    // Get first role for Regulation module (typically Regulation Owner)
                    String roleQuery = "SELECT id FROM object_role WHERE module = ? ORDER BY id ASC LIMIT 1";
                    try (java.sql.PreparedStatement roleStmt = conn.prepareStatement(roleQuery)) {
                        roleStmt.setInt(1, moduleId);
                        try (java.sql.ResultSet roleRs = roleStmt.executeQuery()) {
                            if (roleRs.next()) {
                                int roleId = roleRs.getInt("id");
                                logger.info("Found default Regulation Owner role ID: {}", roleId);
                                return roleId;
                            } else {
                                logger.warn("No roles found for Regulation module (moduleId: {})", moduleId);
                            }
                        }
                    }
                } else {
                    logger.warn("Regulation module not found in database");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default Regulation Owner role: {}", e.getMessage(), e);
        }
        logger.error("Failed to get default Regulation Owner role - returning null");
        return null;
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

    private void processRegulationData(int jobId, JsonArray validatedData, int userId, String errorHandling,
            String uploadOption, String segmentMode, String segment, String entityName) {
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();

        boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);
        Connection conn = null;
        BulkUploadReferenceValidator refValidator = new BulkUploadReferenceValidator();
        BulkUploadNameValidator nameValidator = new BulkUploadNameValidator();

        // Detect if this is a Role Template (multiple rows with same Regulation ID)
        boolean isRoleTemplate = detectRoleTemplate(validatedData);

        try {
            conn = DatabaseConnection.getConnection();
            if (cancelOnWarning) {
                conn.setAutoCommit(false);
            } else {
                conn.setAutoCommit(true);
            }

            if (isRoleTemplate) {
                // Process Role Template: Group rows by Regulation ID
                processRoleTemplate(conn, validatedData, jobId, userId, cancelOnWarning,
                        insertedCount, updatedCount, deletedCount, failedCount, entityName);
            } else {
                // Process normal template: One row = One regulation
                Map<String, Integer> batchRefToId = new HashMap<>();
                Map<String, Integer> batchNameToId = new HashMap<>();
                for (int i = 0; i < validatedData.size(); i++) {
                    JsonObject rowData = validatedData.get(i).getAsJsonObject();
                    String operation = rowData.get("operation").getAsString();
                    int rowNumber = rowData.get("row_number").getAsInt();

                    // Broadcast progress periodically (every 10 rows)
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
                            // Insert new regulation
                            Regulation regulation = new Regulation();
                            String name = rowData.get("Regulation Long Name").getAsString();

                            // Validate name uniqueness within segment
                            if (name != null && !name.trim().isEmpty()
                                    && BulkUploadNameValidator.hasNameColumn("Regulation")) {
                                Long segmentId = null;
                                if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                                    try {
                                        segmentId = determineSegmentId(segmentMode, segment, rowData);
                                    } catch (SQLException e) {
                                        logger.warn("Failed to determine segment ID for name validation: {}",
                                                e.getMessage());
                                    }
                                }
                                try {
                                    nameValidator.validateNameUniqueInSegment("Regulation", name, segmentId, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }

                            regulation.setPrimaryName(name);
                            regulation.setShortName(rowData.has("Short Name") && !rowData.get("Short Name").isJsonNull()
                                    ? rowData.get("Short Name").getAsString()
                                    : "");

                            // Handle Reference Number - generate if empty
                            String refNumber = rowData.has("Reference") && !rowData.get("Reference").isJsonNull()
                                    ? rowData.get("Reference").getAsString().trim()
                                    : "";

                            // Validate reference uniqueness if provided
                            if (refNumber != null && !refNumber.isEmpty()) {
                                try {
                                    refValidator.validateReferenceUnique("Regulation", refNumber, conn);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Row " + rowNumber + ": " + e.getMessage());
                                }
                            }

                            if (refNumber == null || refNumber.isEmpty()) {
                                refNumber = generateDefaultRefCode();
                                // Validate auto-generated reference as well
                                try {
                                    refValidator.validateReferenceUnique("Regulation", refNumber, conn);
                                } catch (IllegalArgumentException e) {
                                    // If auto-generated is duplicate, generate a new one (should be rare)
                                    refNumber = generateDefaultRefCode();
                                    refValidator.validateReferenceUnique("Regulation", refNumber, conn);
                                }
                            }
                            regulation.setRefNumber(refNumber);

                            regulation.setDescription(
                                    rowData.has("Description") && !rowData.get("Description").isJsonNull()
                                            ? rowData.get("Description").getAsString()
                                            : "");
                            regulation.setAdditionalInfo(
                                    rowData.has("Additional Info") && !rowData.get("Additional Info").isJsonNull()
                                            ? rowData.get("Additional Info").getAsString()
                                            : "");

                            // Handle dates - convert from "25th May 2018" format to SQL Date
                            regulation.setPublicationDate(parseDateFromString(
                                    rowData.has("Publication Date") && !rowData.get("Publication Date").isJsonNull()
                                            ? rowData.get("Publication Date").getAsString()
                                            : null));
                            regulation.setCommentsDate(parseDateFromString(
                                    rowData.has("Comments Date") && !rowData.get("Comments Date").isJsonNull()
                                            ? rowData.get("Comments Date").getAsString()
                                            : null));
                            regulation.setFinalisationDate(parseDateFromString(
                                    rowData.has("Finalisation Date") && !rowData.get("Finalisation Date").isJsonNull()
                                            ? rowData.get("Finalisation Date").getAsString()
                                            : null));
                            regulation.setComplianceDate(parseDateFromString(
                                    rowData.has("Compliance Date") && !rowData.get("Compliance Date").isJsonNull()
                                            ? rowData.get("Compliance Date").getAsString()
                                            : null));

                            regulation.setLegalAdvice(
                                    rowData.has("Legal Advice") && !rowData.get("Legal Advice").isJsonNull()
                                            ? rowData.get("Legal Advice").getAsString()
                                            : "");

                            // Handle Parent_ID (resolve from batch first if parent in same file)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentRef = getString(rowData, "Parent Ref.");
                                String parentName = getString(rowData, "Parent Regulation Name");
                                boolean hasRef = parentRef != null && !parentRef.trim().isEmpty();
                                boolean hasName = parentName != null && !parentName.trim().isEmpty();
                                if (hasRef && parentRef != null) {
                                    Integer fromBatch = batchRefToId.get(parentRef.trim().toLowerCase());
                                    if (fromBatch != null)
                                        parentId = fromBatch;
                                }
                                if (parentId == null && hasName && parentName != null) {
                                    Integer fromBatch = batchNameToId.get(parentName.trim().toLowerCase());
                                    if (fromBatch != null)
                                        parentId = fromBatch;
                                }
                                if (parentId == null && (hasRef || hasName)) {
                                    try {
                                        if (hasRef && parentRef != null) {
                                            Regulation byRef = regulationDAO.getRegulationByRefNumber(parentRef.trim());
                                            if (byRef != null)
                                                parentId = byRef.getId();
                                        }
                                        if (parentId == null && hasName && parentName != null) {
                                            Regulation byName = regulationDAO
                                                    .getRegulationByPrimaryName(parentName.trim());
                                            if (byName != null)
                                                parentId = byName.getId();
                                        }
                                    } catch (SQLException e) {
                                        throw new RuntimeException(
                                                "Failed to resolve parent regulation: " + e.getMessage(), e);
                                    }
                                    if (parentId == null) {
                                        String msg = (hasRef && parentRef != null) ? "ref '" + parentRef.trim() + "'" : "";
                                        if (hasName && parentName != null) msg += (msg.isEmpty() ? "" : " or ") + "name '" + parentName.trim() + "'";
                                        throw new RuntimeException("Parent regulation not found: " + msg);
                                    }
                                }
                            }
                            regulation.setParentId(parentId);

                            // Handle lookup fields - Python now sends IDs directly
                            Integer maturityId = null;
                            if (rowData.has("Regulation Maturity_ID")
                                    && !rowData.get("Regulation Maturity_ID").isJsonNull()) {
                                maturityId = rowData.get("Regulation Maturity_ID").getAsInt();
                            } else if (rowData.has("Regulation Maturity")
                                    && !rowData.get("Regulation Maturity").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                maturityId = getLookupIdByName("regulation_maturity",
                                        rowData.get("Regulation Maturity").getAsString());
                            }
                            regulation.setRegulationMaturityId(maturityId != null ? maturityId : 1);

                            Integer probabilityId = null;
                            if (rowData.has("Regulation Probability_ID")
                                    && !rowData.get("Regulation Probability_ID").isJsonNull()) {
                                probabilityId = rowData.get("Regulation Probability_ID").getAsInt();
                            } else if (rowData.has("Regulation Probability")
                                    && !rowData.get("Regulation Probability").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                probabilityId = getLookupIdByName("regulation_probability",
                                        rowData.get("Regulation Probability").getAsString());
                            }
                            regulation.setRegulationProbabilityId(probabilityId != null ? probabilityId : 1);

                            Integer statusId = null;
                            if (rowData.has("BUDG Status_ID") && !rowData.get("BUDG Status_ID").isJsonNull()) {
                                statusId = rowData.get("BUDG Status_ID").getAsInt();
                            } else if (rowData.has("BUDG Status") && !rowData.get("BUDG Status").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                statusId = getLookupIdByName("regulation_status",
                                        rowData.get("BUDG Status").getAsString());
                            }
                            regulation.setRegulationStatusId(statusId != null ? statusId : 1);

                            Integer legalAdviceTypeId = null;
                            if (rowData.has("Legal Advice Type_ID")
                                    && !rowData.get("Legal Advice Type_ID").isJsonNull()) {
                                legalAdviceTypeId = rowData.get("Legal Advice Type_ID").getAsInt();
                            } else if (rowData.has("Legal Advice Type")
                                    && !rowData.get("Legal Advice Type").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                legalAdviceTypeId = getLookupIdByName("legal_advice_type",
                                        rowData.get("Legal Advice Type").getAsString());
                            }
                            regulation.setLegalAdviceTypeId(legalAdviceTypeId);

                            Integer stageId = null;
                            if (rowData.has("Regulation Stage_ID")
                                    && !rowData.get("Regulation Stage_ID").isJsonNull()) {
                                stageId = rowData.get("Regulation Stage_ID").getAsInt();
                            } else if (rowData.has("Regulation Stage")
                                    && !rowData.get("Regulation Stage").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                stageId = getLookupIdByName("regulation_stage",
                                        rowData.get("Regulation Stage").getAsString());
                            }
                            regulation.setRegulationStageId(stageId != null ? stageId : 1);

                            Integer complianceLevelId = null;
                            if (rowData.has("Compliance Level_ID")
                                    && !rowData.get("Compliance Level_ID").isJsonNull()) {
                                complianceLevelId = rowData.get("Compliance Level_ID").getAsInt();
                            } else if (rowData.has("Compliance Level")
                                    && !rowData.get("Compliance Level").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                complianceLevelId = getLookupIdByName("regulation_compliance_level",
                                        rowData.get("Compliance Level").getAsString());
                            }
                            regulation.setComplianceLevelId(complianceLevelId != null ? complianceLevelId : 1);

                            // Handle Is_Public (Access Control) - default to 1
                            regulation.setIsPublic(1);

                            // User resolution: use User Email from file if provided, otherwise use uploader
                            Integer finalUserId = userId; // Default to uploader
                            String userEmail = getString(rowData, "User Email");
                            if (userEmail != null && !userEmail.trim().isEmpty()) {
                                Integer resolvedUserId = getUserIdByEmail(userEmail.trim());
                                if (resolvedUserId != null) {
                                    finalUserId = resolvedUserId;
                                    logger.info("Resolved User Email '{}' to User ID {}", userEmail, finalUserId);
                                } else {
                                    logger.warn("User Email '{}' not found, using uploader ID {} as stakeholder",
                                            userEmail, userId);
                                }
                            }

                            Regulation created = regulationDAO.createRegulation(conn, regulation, finalUserId);

                            String authorName = getUserName(finalUserId);

                            // Create stakeholder record for the resolved user
                            try {
                                Integer governanceRoleId = getInteger(rowData, "Governance Role_ID");
                                if (governanceRoleId == null) {
                                    String grName = getString(rowData, "Governance Role");
                                    if (grName != null && !grName.trim().isEmpty()) {
                                        try {
                                            governanceRoleId = RoleHandlerUtil.getRoleIdByNameInModuleRuntime(conn, grName, "Regulation");
                                        } catch (IllegalArgumentException e) {
                                            throw new RuntimeException("Row " + rowNumber + ": " + e.getMessage(), e);
                                        }
                                    }
                                }
                                // If no role specified, get default Regulation Owner role
                                if (governanceRoleId == null) {
                                    governanceRoleId = getDefaultRegulationOwnerRole();
                                    logger.info("No governance role specified, using default role: {}",
                                            governanceRoleId);
                                }

                                if (governanceRoleId != null) {
                                    RoleHandlerUtil.validateGovernanceRoleForObjectInsert(conn, finalUserId,
                                            governanceRoleId, "Regulation", rowNumber);
                                }
                                if (governanceRoleId != null) {
                                    logger.info("Creating stakeholder for regulation {} with userId {} and roleId {}",
                                            created.getId(), finalUserId, governanceRoleId);
                                    int objectXPeopleId = ensureObjectXPeople(finalUserId, governanceRoleId, userId);
                                    linkStakeholderToRegulation(conn, created.getId(), objectXPeopleId);
                                    String fullName = getUserName(finalUserId);
                                    regulationDAO.createStakeholderAuditRecords(conn, created.getId(), authorName,
                                            fullName, governanceRoleId);
                                    logger.info("Successfully created stakeholder for regulation {}", created.getId());
                                } else {
                                    logger.warn(
                                            "Cannot create stakeholder for regulation {}: no governance role found (userId: {})",
                                            created.getId(), finalUserId);
                                }
                            } catch (Exception e) {
                                logger.error("Error linking stakeholder to regulation {}: {}", created.getId(),
                                        e.getMessage(), e);
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
                                    SegmentValidationService.ValidationResult result = validator
                                            .validateParentChildSegment(
                                                    parentId, segmentIdToAssign.intValue(), "Regulation");
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
                                            "Regulation",
                                            segmentIdToAssign,
                                            userId);
                                    logger.info("Assigned Regulation {} to Segment {}", created.getId(),
                                            segmentIdToAssign);
                                } catch (Exception segEx) {
                                    // Segment assignment failure = row failure (BUDG behavior)
                                    logger.error("Failed to assign Regulation {} to segment: {}", created.getId(),
                                            segEx.getMessage(), segEx);
                                    throw new RuntimeException(
                                            "Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }

                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                        created.getId(),
                                        "Regulation",
                                        rowData,
                                        userId,
                                        conn);
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Regulation {}: {}", created.getId(),
                                        cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }

                            insertedCount++;

                            // Create success report item
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    "Regulation created successfully", "info");

                        } else if ("UPDATE".equals(operation)) {
                            List<String> identityCols = Arrays.asList("Regulation ID", "Reference",
                                    "Regulation Long Name");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols,
                                    "Regulation", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByRef = null;
                            Integer idByName = null;
                            Integer idVal = BulkUploadUtil.getInteger(rowData, "Regulation ID");
                            if (idVal != null) {
                                Regulation r = regulationDAO.getRegulationById(idVal);
                                if (r != null)
                                    idById = r.getId();
                            }
                            String refVal = BulkUploadUtil.getString(rowData, "Reference");
                            if (refVal != null && !refVal.trim().isEmpty()) {
                                Regulation r = regulationDAO.getRegulationByRefNumber(refVal.trim());
                                if (r != null)
                                    idByRef = r.getId();
                            }
                            String nameVal = BulkUploadUtil.getString(rowData, "Regulation Long Name");
                            if (nameVal != null && !nameVal.trim().isEmpty()) {
                                Regulation r = regulationDAO.getRegulationByPrimaryName(nameVal.trim());
                                if (r != null)
                                    idByName = r.getId();
                            }
                            int filledIdentity = BulkUploadUtil.countFilledIdentityColumns(rowData, identityCols);
                            Integer regulationId = coalesce(idById, idByRef, idByName);
                            if (regulationId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber
                                        + ": No regulation found for the provided identity (Regulation ID, Reference, or Regulation Long Name).");
                            }
                            if (filledIdentity >= 2) {
                                java.util.Set<Integer> ids = new java.util.HashSet<>();
                                if (idById != null)
                                    ids.add(idById);
                                if (idByRef != null)
                                    ids.add(idByRef);
                                if (idByName != null)
                                    ids.add(idByName);
                                if (ids.size() > 1) {
                                    throw new IllegalArgumentException("Row " + rowNumber
                                            + ": Regulation ID, Reference and Regulation Long Name refer to different regulations.");
                                }
                            }
                            // Get old regulation for audit
                            Regulation oldRegulation = regulationDAO.getRegulationById(regulationId);

                            // Double-check that regulation exists (defensive programming)
                            if (oldRegulation == null) {
                                throw new IllegalArgumentException(
                                        "Regulation with ID " + regulationId + " does not exist");
                            }

                            Regulation newRegulation = new Regulation();
                            newRegulation.setId(regulationId);
                            String nameFromRow = BulkUploadUtil.getString(rowData, "Regulation Long Name");
                            newRegulation.setPrimaryName(
                                    nameFromRow != null && !nameFromRow.trim().isEmpty() ? nameFromRow.trim()
                                            : oldRegulation.getPrimaryName());
                            newRegulation
                                    .setShortName(rowData.has("Short Name") && !rowData.get("Short Name").isJsonNull()
                                            ? rowData.get("Short Name").getAsString()
                                            : "");

                            // Handle Reference Number - keep existing if empty in update
                            String refNumber = rowData.has("Reference") && !rowData.get("Reference").isJsonNull()
                                    ? rowData.get("Reference").getAsString().trim()
                                    : "";
                            if (refNumber == null || refNumber.isEmpty()) {
                                refNumber = oldRegulation.getRefNumber();
                            }
                            newRegulation.setRefNumber(refNumber);

                            // Preserve existing description when row has no/empty Description
                            String descFromRow = rowData.has("Description") && !rowData.get("Description").isJsonNull()
                                    ? rowData.get("Description").getAsString()
                                    : null;
                            newRegulation.setDescription(descFromRow != null && !descFromRow.trim().isEmpty()
                                    ? descFromRow.trim()
                                    : oldRegulation.getDescription());
                            newRegulation.setAdditionalInfo(
                                    rowData.has("Additional Info") && !rowData.get("Additional Info").isJsonNull()
                                            ? rowData.get("Additional Info").getAsString()
                                            : "");

                            // Handle dates - convert from "25th May 2018" format to SQL Date
                            newRegulation.setPublicationDate(parseDateFromString(
                                    rowData.has("Publication Date") && !rowData.get("Publication Date").isJsonNull()
                                            ? rowData.get("Publication Date").getAsString()
                                            : null));
                            newRegulation.setCommentsDate(parseDateFromString(
                                    rowData.has("Comments Date") && !rowData.get("Comments Date").isJsonNull()
                                            ? rowData.get("Comments Date").getAsString()
                                            : null));
                            newRegulation.setFinalisationDate(parseDateFromString(
                                    rowData.has("Finalisation Date") && !rowData.get("Finalisation Date").isJsonNull()
                                            ? rowData.get("Finalisation Date").getAsString()
                                            : null));
                            String complianceDate = null;
                            if (rowData.has("Compliance Date") && !rowData.get("Compliance Date").isJsonNull()) {
                                String compStr = rowData.get("Compliance Date").getAsString();
                                if (compStr != null && !compStr.trim().isEmpty()) {
                                    complianceDate = parseDateFromString(compStr);
                                }
                            }
                            newRegulation.setComplianceDate(complianceDate != null ? complianceDate : oldRegulation.getComplianceDate());

                            newRegulation.setLegalAdvice(
                                    rowData.has("Legal Advice") && !rowData.get("Legal Advice").isJsonNull()
                                            ? rowData.get("Legal Advice").getAsString()
                                            : "");

                            // Handle Parent_ID (resolve from batch first if parent in same file)
                            Integer parentId = getInteger(rowData, "Parent_ID");
                            if (parentId == null) {
                                String parentRef = getString(rowData, "Parent Ref.");
                                String parentName = getString(rowData, "Parent Regulation Name");
                                boolean hasRef = parentRef != null && !parentRef.trim().isEmpty();
                                boolean hasName = parentName != null && !parentName.trim().isEmpty();
                                if (hasRef && parentRef != null) {
                                    Integer fromBatch = batchRefToId.get(parentRef.trim().toLowerCase());
                                    if (fromBatch != null)
                                        parentId = fromBatch;
                                }
                                if (parentId == null && hasName && parentName != null) {
                                    Integer fromBatch = batchNameToId.get(parentName.trim().toLowerCase());
                                    if (fromBatch != null)
                                        parentId = fromBatch;
                                }
                                if (parentId == null && (hasRef || hasName)) {
                                    try {
                                        if (hasRef && parentRef != null) {
                                            Regulation byRef = regulationDAO.getRegulationByRefNumber(parentRef.trim());
                                            if (byRef != null)
                                                parentId = byRef.getId();
                                        }
                                        if (parentId == null && hasName && parentName != null) {
                                            Regulation byName = regulationDAO
                                                    .getRegulationByPrimaryName(parentName.trim());
                                            if (byName != null)
                                                parentId = byName.getId();
                                        }
                                    } catch (SQLException e) {
                                        throw new RuntimeException(
                                                "Failed to resolve parent regulation: " + e.getMessage(), e);
                                    }
                                    if (parentId == null) {
                                        String msg = (hasRef && parentRef != null) ? "ref '" + parentRef.trim() + "'" : "";
                                        if (hasName && parentName != null) msg += (msg.isEmpty() ? "" : " or ") + "name '" + parentName.trim() + "'";
                                        throw new RuntimeException("Parent regulation not found: " + msg);
                                    }
                                }
                            }
                            // Prevent circular hierarchy: new parent must not be a descendant of this
                            // object
                            if (parentId != null && parentId > 0) {
                                try {
                                    if (BulkUploadParentValidator.wouldCreateParentCycle(conn, regulationId, parentId,
                                            "Regulation")) {
                                        throw new RuntimeException(
                                                "Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.");
                                    }
                                } catch (SQLException e) {
                                    throw new RuntimeException("Failed to validate parent hierarchy: " + e.getMessage(),
                                            e);
                                }
                            }
                            newRegulation.setParentId(parentId);

                            // When parent is specified, validate object and parent are not in different
                            // private segments
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
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) regulationId,
                                        "Regulation");
                                int effectiveChildSegmentId = (newSegmentIdForParent != null)
                                        ? newSegmentIdForParent.intValue()
                                        : (currentSegmentId != null ? currentSegmentId.intValue() : 1);
                                SegmentValidationService parentValidator = new SegmentValidationService();
                                SegmentValidationService.ValidationResult parentResult = parentValidator
                                        .validateParentChildSegment(
                                                parentId, effectiveChildSegmentId, "Regulation");
                                if (!parentResult.isValid) {
                                    throw new RuntimeException(
                                            "Parent segment validation failed: " + parentResult.message);
                                }
                            }

                            // Handle lookup fields - Python now sends IDs directly
                            Integer maturityId = null;
                            if (rowData.has("Regulation Maturity_ID")
                                    && !rowData.get("Regulation Maturity_ID").isJsonNull()) {
                                maturityId = rowData.get("Regulation Maturity_ID").getAsInt();
                            } else if (rowData.has("Regulation Maturity")
                                    && !rowData.get("Regulation Maturity").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                maturityId = getLookupIdByName("regulation_maturity",
                                        rowData.get("Regulation Maturity").getAsString());
                            }
                            newRegulation.setRegulationMaturityId(
                                    maturityId != null ? maturityId : oldRegulation.getRegulationMaturityId());

                            Integer probabilityId = null;
                            if (rowData.has("Regulation Probability_ID")
                                    && !rowData.get("Regulation Probability_ID").isJsonNull()) {
                                probabilityId = rowData.get("Regulation Probability_ID").getAsInt();
                            } else if (rowData.has("Regulation Probability")
                                    && !rowData.get("Regulation Probability").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                probabilityId = getLookupIdByName("regulation_probability",
                                        rowData.get("Regulation Probability").getAsString());
                            }
                            newRegulation.setRegulationProbabilityId(
                                    probabilityId != null ? probabilityId : oldRegulation.getRegulationProbabilityId());

                            Integer statusId = null;
                            if (rowData.has("BUDG Status_ID") && !rowData.get("BUDG Status_ID").isJsonNull()) {
                                statusId = rowData.get("BUDG Status_ID").getAsInt();
                            } else if (rowData.has("BUDG Status") && !rowData.get("BUDG Status").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                statusId = getLookupIdByName("regulation_status",
                                        rowData.get("BUDG Status").getAsString());
                            }
                            newRegulation.setRegulationStatusId(
                                    statusId != null ? statusId : oldRegulation.getRegulationStatusId());

                            Integer legalAdviceTypeId = null;
                            if (rowData.has("Legal Advice Type_ID")
                                    && !rowData.get("Legal Advice Type_ID").isJsonNull()) {
                                legalAdviceTypeId = rowData.get("Legal Advice Type_ID").getAsInt();
                            } else if (rowData.has("Legal Advice Type")
                                    && !rowData.get("Legal Advice Type").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                legalAdviceTypeId = getLookupIdByName("legal_advice_type",
                                        rowData.get("Legal Advice Type").getAsString());
                            }
                            newRegulation.setLegalAdviceTypeId(legalAdviceTypeId != null ? legalAdviceTypeId
                                    : oldRegulation.getLegalAdviceTypeId());

                            Integer stageId = null;
                            if (rowData.has("Regulation Stage_ID")
                                    && !rowData.get("Regulation Stage_ID").isJsonNull()) {
                                stageId = rowData.get("Regulation Stage_ID").getAsInt();
                            } else if (rowData.has("Regulation Stage")
                                    && !rowData.get("Regulation Stage").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                stageId = getLookupIdByName("regulation_stage",
                                        rowData.get("Regulation Stage").getAsString());
                            }
                            newRegulation.setRegulationStageId(
                                    stageId != null ? stageId : oldRegulation.getRegulationStageId());

                            Integer complianceLevelId = null;
                            if (rowData.has("Compliance Level_ID")
                                    && !rowData.get("Compliance Level_ID").isJsonNull()) {
                                complianceLevelId = rowData.get("Compliance Level_ID").getAsInt();
                            } else if (rowData.has("Compliance Level")
                                    && !rowData.get("Compliance Level").isJsonNull()) {
                                // Fallback: if name is provided, convert to ID
                                complianceLevelId = getLookupIdByName("regulation_compliance_level",
                                        rowData.get("Compliance Level").getAsString());
                            }
                            newRegulation.setComplianceLevelId(complianceLevelId != null ? complianceLevelId
                                    : oldRegulation.getComplianceLevelId());

                            // Handle Is_Public (Access Control) - default to 1
                            newRegulation.setIsPublic(1);

                            // User resolution - always use userId from session
                            regulationDAO.updateRegulation(regulationId, newRegulation, userId);

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
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) regulationId,
                                            "Regulation");

                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator
                                                .validateSegmentMove(
                                                        regulationId, newSegmentId.intValue(), "Regulation", parentId);

                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Segment validation failed: " + result.message);
                                        }

                                        validateSegmentAccess(userId, newSegmentId, rowNumber);

                                        // Assign new segment
                                        ObjectSegmentService.assignObjectToSegment(
                                                conn,
                                                (long) regulationId,
                                                "Regulation",
                                                newSegmentId,
                                                userId);
                                        logger.info("Assigned Regulation {} to Segment {} (updated)", regulationId,
                                                newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign Regulation {} to segment: {}", regulationId,
                                            segEx.getMessage(), segEx);
                                    throw new RuntimeException(
                                            "Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }

                            // Create audit record
                            String userName = getUserName(userId);
                            regulationDAO.createRegulationUpdateAuditRecords(regulationId, oldRegulation, newRegulation,
                                    userName);
                            regulationDAO.createRegulationUpdateAuditSnapshot(regulationId);

                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                        regulationId,
                                        "Regulation",
                                        rowData,
                                        userId,
                                        conn);
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for Regulation {}: {}", regulationId,
                                        cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }

                            updatedCount++;

                            // Create success report item
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                    "Regulation updated successfully", "info");

                        } else if ("DELETE".equals(operation)) {
                            int regulationId = rowData.get("Regulation ID").getAsInt();
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult = validationHelper
                                    .validateObjectForDeletion(conn, "regulation", regulationId);
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
                                        logger.error("Error rolling back row {}: {}", rowNumber, rollbackEx.getMessage(),
                                                rollbackEx);
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
                                Regulation existingRegulation = regulationDAO.getRegulationById(regulationId);
                                if (existingRegulation == null) {
                                    logger.info("Regulation with ID {} does not exist, skipping deletion (idempotent)",
                                            regulationId);
                                    continue;
                                }
                                regulationDAO.deleteRegulation(regulationId);
                                deletedCount++;
                                int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                                jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                        "Regulation deleted successfully", "info");
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
            }

            if (cancelOnWarning) {
                conn.commit();
                logger.info("Transaction committed successfully");
            }

            // Step 11: Check for partial completion and update job status (inside try so finally sees terminal status)
            int successfulCount = insertedCount + updatedCount + deletedCount;
            boolean hasFailures = failedCount > 0;
            boolean isPartiallyCompleted = (successfulCount > 0) && hasFailures;
            boolean isAllFailed = (successfulCount == 0) && hasFailures;
            try {
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
                    sendNotification(userId, "Regulation", uploadOption, insertedCount, updatedCount,
                            deletedCount, failedCount, false, "All rows failed during processing", jobId);
                } else if (isPartiallyCompleted) {
                    jobDAO.updateJobStatus(jobId, "Partially Completed", true);
                    jobDAO.updateJobItemsCount(jobId, successfulCount);
                    jobDAO.updateJobProgress(jobId, "Partially Completed",
                            String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                                    insertedCount, updatedCount, deletedCount, failedCount));
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Partially Completed", 100,
                            "Processing complete with some failures",
                            insertedCount, updatedCount, deletedCount, failedCount);
                    logger.info("Bulk upload partially completed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
                            insertedCount, updatedCount, deletedCount, failedCount);
                    sendNotification(userId, "Regulation", uploadOption, insertedCount, updatedCount,
                            deletedCount, failedCount, true, null, jobId);
                } else {
                    jobDAO.updateJobStatus(jobId, "Completed", true);
                    jobDAO.updateJobItemsCount(jobId, successfulCount);
                    jobDAO.updateJobProgress(jobId, "Completed",
                            String.format("Completed: %d inserted, %d updated, %d deleted, %d failed",
                                    insertedCount, updatedCount, deletedCount, failedCount));
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                            String.format("Bulk upload completed successfully"),
                            insertedCount, updatedCount, deletedCount, failedCount);
                    logger.info("Bulk upload completed - Inserted: {}, Updated: {}, Deleted: {}, Failed: {}",
                            insertedCount, updatedCount, deletedCount, failedCount);
                    sendNotification(userId, "Regulation", uploadOption, insertedCount, updatedCount,
                            deletedCount, failedCount, true, null, jobId);
                }
            } catch (Exception e) {
                logger.error("Error updating final job status", e);
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

            try {
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Processing error: " + e.getMessage());
            } catch (SQLException sqlEx) {
                logger.error("Error updating job status to Failed: {}", sqlEx.getMessage(), sqlEx);
            }

            // Broadcast failure via WebSocket
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Processing error: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);

            // Send failure notification
            if (userId != 0 && uploadOption != null) {
                try {
                    sendNotification(userId, "Regulation", uploadOption, 0, 0, 0, 0,
                            false, "Unexpected error: " + e.getMessage(), jobId);
                } catch (Exception notifEx) {
                    logger.error("Error sending notification", notifEx);
                }
            }

            logger.error("Bulk processing failed: " + e.getMessage(), e);
            return; // Exit thread

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
            // Ensure job always reaches a terminal status so the UI stops loading
            try {
                String status = jobDAO.getJobStatus(jobId);
                if (status != null && "Processing".equals(status)) {
                    jobDAO.updateJobStatus(jobId, "Failed", true);
                    jobDAO.updateJobProgress(jobId, "Failed", "Processing ended unexpectedly");
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Processing ended unexpectedly", insertedCount, updatedCount, deletedCount, failedCount);
                }
            } catch (Exception ex) {
                logger.error("Error ensuring job terminal status", ex);
            }
        }
    }
}
