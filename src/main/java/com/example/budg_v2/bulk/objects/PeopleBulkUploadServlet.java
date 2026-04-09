package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.OrgUnitDAO;
import com.example.budg_v2.dao.PeopleDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.model.Person;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.common.BulkUploadFacetPermissionHelper;
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

@WebServlet("/api/bulk/people/upload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,
    maxFileSize = 1024 * 1024 * 10,
    maxRequestSize = 1024 * 1024 * 50
)
public class PeopleBulkUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(PeopleBulkUploadServlet.class);
    private static final Gson gson = new Gson();
    private static final String PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";

    private static String getBasePath() {
        return BulkPathUtil.getBulkPathForEntity("people");
    }

    private final JobDAO jobDAO = new JobDAO();
    private final PeopleDAO peopleDAO = new PeopleDAO();
    private final OrgUnitDAO orgUnitDAO = new OrgUnitDAO();

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

        logger.info("People bulk upload request received");

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

            String entityName = (entity != null && !entity.trim().isEmpty()) ? entity : "People";
            if (!BulkUploadFacetPermissionHelper.canBulkUploadForModule(userId, entityName)) {
                sendErrorResponse(response, "You do not have permission to bulk upload for this entity.", 403);
                return;
            }

            // Only Super Admin can update the People facet
            if ("Update Existing Items".equals(uploadOption)) {
                try {
                    if (!SegmentAccessService.isSuperAdmin(userId)) {
                        sendErrorResponse(response, "Only Super Admin can update the People facet. Admins and Web Users are not allowed.", 403);
                        return;
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking super admin status: {}", e.getMessage());
                    sendErrorResponse(response, "Unable to verify permissions.", 503);
                    return;
                }
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
            String fileName = "people_" + timestamp + "_" + uuid + ".xlsx";

            String basePath = getBasePath();
            File directory = new File(basePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(basePath + fileName);
            filePart.write(file.getAbsolutePath());
            storagePath = basePath + fileName;

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

            // Get cancelOnWarning flag early to check before processing
            boolean cancelOnWarning = "Cancel on Warning".equalsIgnoreCase(errorHandling);

            // Capture validation errors so we can include them in the success response when continuing with partial data
            JsonArray validationErrorsArray = null;

            if ("error".equals(validationStatus) || "invalid".equals(validationStatus)) {
                JsonArray errors = validationResponse.getAsJsonArray("errors");
                validationErrorsArray = errors;

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

                if ("error".equals(validationStatus)) {
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
                }

                // invalid: optional cancel on warning, or no valid rows
                if (cancelOnWarning) {
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
                }

                JsonArray validatedDataCheck = validationResponse.has("data") ? validationResponse.getAsJsonArray("data") : new JsonArray();
                if (validatedDataCheck.size() == 0) {
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
                "Validation complete. Processing " + totalRows + " valid row(s)...");

            int insertedCount = 0;
            int updatedCount = 0;
            int deletedCount = 0;
            int failedCount = 0;

            // cancelOnWarning already defined above
            Connection conn = null;
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
                        int denom = Math.max(1, totalRows);
                        int progressPercent = 30 + (int)((i / (double)denom) * 65);
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                            String.format("Processing row %d of %d", i + 1, totalRows),
                            insertedCount, updatedCount, deletedCount, failedCount);
                    }

                    if (!cancelOnWarning) {
                        conn.setAutoCommit(false);
                    }

                    try {
                        if ("INSERT".equals(operation)) {
                            Person person = new Person();
                            
                            // First_Name (required) — accept template aliases and full "Name" split (matches Python validator)
                            person.setFirstName(getFirstNameFromRow(rowData));
                            if (person.getFirstName() == null || person.getFirstName().trim().isEmpty()) {
                                throw new IllegalArgumentException("First Name is required");
                            }
                            
                            // Last_Name (required)
                            person.setLastName(getLastNameFromRow(rowData));
                            if (person.getLastName() == null || person.getLastName().trim().isEmpty()) {
                                throw new IllegalArgumentException("Last Name is required");
                            }
                            
                            // Email (required, unique)
                            person.setEmail(getEmailFromRow(rowData));
                            if (person.getEmail() == null || person.getEmail().trim().isEmpty()) {
                                throw new IllegalArgumentException("Email is required");
                            }
                            
                            // Profile_Name (required)
                            String profileName = getString(rowData, "Profile");
                            if (profileName == null || profileName.trim().isEmpty()) {
                                throw new IllegalArgumentException("Profile is required");
                            }
                            person.setProfileName(profileName.trim());
                            
                            // System_Role (Profile ID) - required
                            Integer systemRoleId = getInteger(rowData, "System_Role");
                            if (systemRoleId != null) {
                                person.setSystemRole(systemRoleId);
                            } else {
                                throw new IllegalArgumentException("System_Role is required");
                            }
                            
                            // Function_Name (optional)
                            person.setFunctionName(getString(rowData, "Function"));
                            
                            // Function_Description (optional)
                            person.setFunctionDescription(getString(rowData, "Function Description"));
                            
                            // Description (optional)
                            person.setDescription(getString(rowData, "Description"));
                            
                            // Password (optional)
                            person.setPassword(getString(rowData, "Password"));
                            
                            // Org_Unit_ID or Org Unit Name (optional)
                            Integer orgUnitId = getInteger(rowData, "Org_Unit_ID");
                            if (orgUnitId == null) {
                                String orgUnitName = getString(rowData, "Org Unit Name");
                                if (orgUnitName != null && !orgUnitName.trim().isEmpty()) {
                                    var orgUnit = orgUnitDAO.getOrgUnitByName(orgUnitName.trim());
                                    if (orgUnit != null) {
                                        orgUnitId = orgUnit.getId();
                                    } else {
                                        throw new RuntimeException("Org Unit not found with name: " + orgUnitName.trim());
                                    }
                                }
                            }
                            person.setOrgUnitId(orgUnitId);
                            
                            // Status (optional, default: first from status)
                            Integer statusId = coalesce(getInteger(rowData, "status_id"),
                                    getLookupIdByName("status", getString(rowData, "BUDG Status")));
                            if (statusId == null) statusId = getFirstStatusId();
                            person.setStatusId(statusId);
                            
                            // Create person in people table
                            int personId = peopleDAO.createPerson(person, userId);
                            
                            // Create people_details record with employment_type, lifecycle, office fields
                            createPeopleDetailsFull(personId, rowData, userId);
                            // Link people.ip_details to people_details so the join returns details (status, Office Location, Lifecycle, etc.)
                            updatePeopleIpDetails(conn, personId);
                            
                            // Create audit records
                            try {
                                String userName = getUserName(userId);
                                peopleDAO.createPeopleAuditRecords(personId, userName);
                            } catch (Exception e) {
                                logger.warn("Failed to create audit records for person {}: {}", personId, e.getMessage());
                                // Don't fail the bulk upload if audit fails
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
                                    if (segmentIdToAssign == null) {
                                        throw new IllegalArgumentException("Segment '" + segmentName.trim() + "' does not exist");
                                    }
                                }
                            }
                            
                            if (segmentIdToAssign != null) {
                                try {
                                    validateSegmentAccess(userId, segmentIdToAssign, rowNumber);
                                    
                                    ObjectSegmentService.assignObjectToSegment(
                                        conn,
                                        (long) personId, 
                                        "People", 
                                        segmentIdToAssign, 
                                        userId
                                    );
                                    logger.info("Assigned People {} to Segment {}", personId, segmentIdToAssign);
                                } catch (Exception segEx) {
                                    // Segment assignment failure = row failure (BUDG behavior)
                                    logger.error("Failed to assign People {} to segment: {}", personId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    personId, 
                                    "People", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for People {}: {}", personId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            insertedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " created successfully", "info");

                        } else if ("UPDATE".equals(operation)) {
                            // Normalize email key so "email" (lowercase) is treated as "Email" for identity check and lookup
                            if (BulkUploadUtil.getString(rowData, "Email") == null || BulkUploadUtil.getString(rowData, "Email").trim().isEmpty()) {
                                String emailFromLower = BulkUploadUtil.getString(rowData, "email");
                                if (emailFromLower != null && !emailFromLower.trim().isEmpty()) {
                                    rowData.addProperty("Email", emailFromLower);
                                }
                            }
                            List<String> identityCols = Arrays.asList("People ID", "First Name", "Last Name", "Email");
                            String identityErr = BulkUploadUtil.requireAtLeastOneIdentityColumn(rowData, identityCols, "People", rowNumber);
                            if (identityErr != null) {
                                throw new IllegalArgumentException(identityErr);
                            }
                            Integer idById = null;
                            Integer idByEmail = null;
                            Integer idByFirstLast = null;
                            Integer idVal = getInteger(rowData, "People ID");
                            if (idVal != null) {
                                Person p = getPersonById(idVal);
                                if (p != null) idById = p.getId();
                            }
                            String emailVal = getString(rowData, "Email");
                            if (emailVal == null || emailVal.trim().isEmpty()) {
                                emailVal = getString(rowData, "email");
                            }
                            if (emailVal != null && !emailVal.trim().isEmpty()) {
                                Person p = getPersonByEmail(emailVal.trim());
                                if (p != null) idByEmail = p.getId();
                            }
                            String firstVal = getString(rowData, "First Name");
                            String lastVal = getString(rowData, "Last Name");
                            if (firstVal != null && !firstVal.trim().isEmpty() && lastVal != null && !lastVal.trim().isEmpty()) {
                                Person p = getPersonByFirstAndLastName(firstVal.trim(), lastVal.trim());
                                if (p != null) idByFirstLast = p.getId();
                            }
                            Integer personId = coalesce(idById, idByEmail, idByFirstLast);
                            if (personId == null) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": No person found for the provided identity (People ID, Email, or First Name and Last Name).");
                            }
                            java.util.Set<Integer> ids = new java.util.HashSet<>();
                            if (idById != null) ids.add(idById);
                            if (idByEmail != null) ids.add(idByEmail);
                            if (idByFirstLast != null) ids.add(idByFirstLast);
                            if (ids.size() > 1) {
                                throw new IllegalArgumentException("Row " + rowNumber + ": People ID, Email and First/Last Name must refer to the same person.");
                            }
                            // Get existing person
                            Person oldPerson = getPersonById(personId);
                            if (oldPerson == null) {
                                throw new IllegalArgumentException("Person with ID " + personId + " does not exist");
                            }

                            // Admins cannot edit their own profile via bulk upload
                            if (personId == userId) {
                                try {
                                    if (!SegmentAccessService.isSuperAdmin(userId)) {
                                        String role = SegmentAccessService.getUserRole(userId);
                                        if (role != null && "admin".equalsIgnoreCase(role.trim())) {
                                            throw new IllegalArgumentException("Admins cannot edit their own profile via bulk upload");
                                        }
                                    }
                                } catch (SQLException e) {
                                    logger.warn("Error checking role for self-edit: {}", e.getMessage());
                                }
                            }
                            
                            Person newPerson = new Person();
                            newPerson.setId(personId);
                            
                            // First_Name (required)
                            newPerson.setFirstName(getStringOrDefault(rowData, "First Name", oldPerson.getFirstName()));
                            
                            // Last_Name (required)
                            newPerson.setLastName(getStringOrDefault(rowData, "Last Name", oldPerson.getLastName()));
                            
                            // Email (required)
                            newPerson.setEmail(getStringOrDefault(rowData, "Email", oldPerson.getEmail()));
                            
                            // Profile_Name (required)
                            newPerson.setProfileName(getStringOrDefault(rowData, "Profile", oldPerson.getProfileName()));
                            
                            // System_Role (Profile ID) - required
                            Integer systemRoleId = getInteger(rowData, "System_Role");
                            if (systemRoleId != null) {
                                newPerson.setSystemRole(systemRoleId);
                            } else {
                                newPerson.setSystemRole(oldPerson.getSystemRole());
                            }
                            
                            // Function_Name (optional)
                            newPerson.setFunctionName(getStringOrDefault(rowData, "Function", oldPerson.getFunctionName()));
                            
                            // Function_Description (optional)
                            newPerson.setFunctionDescription(getStringOrDefault(rowData, "Function Description", oldPerson.getFunctionDescription()));
                            
                            // Description (optional)
                            newPerson.setDescription(getStringOrDefault(rowData, "Description", oldPerson.getDescription()));
                            
                            // Password (optional)
                            newPerson.setPassword(getStringOrDefault(rowData, "Password", oldPerson.getPassword()));
                            
                            // Org_Unit_ID or Org Unit Name
                            Integer orgUnitId = getInteger(rowData, "Org_Unit_ID");
                            if (orgUnitId == null) {
                                String orgUnitName = getString(rowData, "Org Unit Name");
                                if (orgUnitName != null && !orgUnitName.trim().isEmpty()) {
                                    var orgUnit = orgUnitDAO.getOrgUnitByName(orgUnitName.trim());
                                    if (orgUnit != null) {
                                        orgUnitId = orgUnit.getId();
                                    } else {
                                        throw new RuntimeException("Org Unit not found with name: " + orgUnitName.trim());
                                    }
                                }
                            }
                            if (orgUnitId == null) orgUnitId = oldPerson.getOrgUnitId();
                            newPerson.setOrgUnitId(orgUnitId);
                            
                            // Status
                            Integer statusId = coalesce(getInteger(rowData, "status_id"),
                                    getLookupIdByName("status", getString(rowData, "BUDG Status")));
                            if (statusId == null) statusId = oldPerson.getStatusId();
                            newPerson.setStatusId(statusId);
                            
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
                                    Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) personId, "People");
                                    
                                    // Only assign if segment is changing
                                    if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                        // Validate segment change (People uses orgUnitId as parent)
                                        SegmentValidationService validator = new SegmentValidationService();
                                        SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            personId, newSegmentId.intValue(), "People", orgUnitId);
                                        
                                        if (!result.isValid && !result.canProceedWithWarning) {
                                            throw new RuntimeException("Segment validation failed: " + result.message);
                                        }
                                        
                                        
                                        validateSegmentAccess(userId, newSegmentId, rowNumber);
                                        
                                        // Assign new segment
                                        ObjectSegmentService.assignObjectToSegment(
                                            conn,
                                            (long) personId, 
                                            "People", 
                                            newSegmentId, 
                                            userId
                                        );
                                        logger.info("Assigned People {} to Segment {} (updated)", personId, newSegmentId);
                                    }
                                } catch (Exception segEx) {
                                    logger.error("Failed to assign People {} to segment: {}", personId, segEx.getMessage(), segEx);
                                    throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
                                }
                            }
                            
                            // Update person
                            peopleDAO.updatePerson(newPerson, userId);
                            
                            // Update people_details with employment_type, lifecycle, office fields
                            updatePeopleDetailsFull(personId, rowData, userId);
                            
                            // Create update audit records
                            try {
                                String userName = getUserName(userId);
                                peopleDAO.createPeopleUpdateAuditRecords(personId, oldPerson, newPerson, userName);
                            } catch (Exception e) {
                                logger.warn("Failed to create update audit records for person {}: {}", personId, e.getMessage());
                                // Don't fail the bulk upload if audit fails
                            }
                            
                            // Save custom field values
                            try {
                                CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    personId, 
                                    "People", 
                                    rowData, 
                                    userId,
                                    conn
                                );
                            } catch (Exception cfEx) {
                                logger.error("Failed to save custom fields for People {}: {}", personId, cfEx.getMessage(), cfEx);
                                throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                            }
                            
                            updatedCount++;
                            int reportItemId = jobDAO.createJobReportItem(jobId, entityName, "success", rowNumber);
                            jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                entityName + " updated successfully", "info");

                        } else if ("DELETE".equals(operation)) {
                            int personId = rowData.get("People ID").getAsInt();
                            BulkDeleteValidationHelper validationHelper = new BulkDeleteValidationHelper();
                            BulkDeleteValidationHelper.ValidationResult validationResult =
                                validationHelper.validateObjectForDeletion(conn, "people", personId);
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
                                // Create delete audit record before deleting
                                try {
                                    String userName = getUserName(userId);
                                    peopleDAO.createPeopleDeleteAuditRecord(personId, userName);
                                } catch (Exception e) {
                                    logger.warn("Failed to create delete audit record for person {}: {}", personId, e.getMessage());
                                }
                                // Soft delete
                                String deleteSql = "UPDATE people SET lastupdateuser_id = ?, Last_Updated = NOW() WHERE ID = ?";
                                try (java.sql.PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                                    ps.setInt(1, userId);
                                    ps.setInt(2, personId);
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
                        sendNotification(parsedUserId, "People", uploadOption, insertedCount, updatedCount, 
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

            // Include validation errors in failed count so GUI and report match
            if (validationErrorsArray != null && validationErrorsArray.size() > 0) {
                failedCount += validationErrorsArray.size();
            }

            // Check for partial completion (some success AND some failures)
            int successfulCount = insertedCount + updatedCount + deletedCount;
            boolean isPartiallyCompleted = (successfulCount > 0) && (failedCount > 0);
            boolean allValidationFailed = (successfulCount == 0) && (failedCount > 0);

            if (allValidationFailed) {
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed",
                    String.format("All %d row(s) had validation errors", failedCount));
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "All rows had validation errors",
                    insertedCount, updatedCount, deletedCount, failedCount);
                sendNotification(userId, "People", uploadOption, insertedCount, updatedCount,
                    deletedCount, failedCount, false, "All rows had validation errors", jobId);
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
                sendNotification(userId, "People", uploadOption, insertedCount, updatedCount,
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
                sendNotification(userId, "People", uploadOption, insertedCount, updatedCount, 
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
            successResponse.addProperty("report_url", "/api/bulk/people/report/" + jobId);
            if (validationErrorsArray != null && validationErrorsArray.size() > 0) {
                successResponse.add("validation_errors", validationErrorsArray);
                successResponse.addProperty("validation_errors_count", validationErrorsArray.size());
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
                                sendNotification(userId, "People", uploadOption, 0, 0, 0, 0, 
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
            n.setFacetType("people");
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
                 "SELECT Reference_Name FROM job WHERE Reference_Name LIKE 'PEO-%'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int maxNumber = 0;
                while (rs.next()) {
                    String refName = rs.getString("Reference_Name");
                    if (refName != null && refName.startsWith("PEO-")) {
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
        return "PEO-" + nextNumber;
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

    /** First name from row JSON: canonical "First Name" plus aliases; optional split from "Name". */
    private String getFirstNameFromRow(JsonObject rowData) {
        String v = coalesce(
                getString(rowData, "First Name"),
                getString(rowData, "FirstName"),
                getString(rowData, "First_Name"));
        if (v != null && !v.trim().isEmpty()) {
            return v.trim();
        }
        String name = getString(rowData, "Name");
        if (name != null && !name.trim().isEmpty()) {
            String[] parts = name.trim().split("\\s+", 2);
            return parts[0];
        }
        return null;
    }

    /** Last name from row JSON: canonical "Last Name" plus aliases; optional remainder from "Name". */
    private String getLastNameFromRow(JsonObject rowData) {
        String v = coalesce(
                getString(rowData, "Last Name"),
                getString(rowData, "LastName"),
                getString(rowData, "Last_Name"));
        if (v != null && !v.trim().isEmpty()) {
            return v.trim();
        }
        String name = getString(rowData, "Name");
        if (name != null && !name.trim().isEmpty()) {
            String[] parts = name.trim().split("\\s+", 2);
            if (parts.length > 1) {
                return parts[1].trim();
            }
        }
        return null;
    }

    /** Email from row JSON with common column aliases. */
    private String getEmailFromRow(JsonObject rowData) {
        String v = coalesce(
                getString(rowData, "Email"),
                getString(rowData, "E-mail"),
                getString(rowData, "email"));
        return v != null ? v.trim() : null;
    }

    /** LAN ID from row data; accepts both "LAN ID" and "LAN Id" for backward compatibility. */
    private String getLanId(JsonObject obj) {
        String v = getString(obj, "LAN ID");
        return v != null ? v : getString(obj, "LAN Id");
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

    private Person getPersonById(int personId) {
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM people WHERE ID = ? LIMIT 1")) {
            ps.setInt(1, personId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPerson(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting person by ID: {}", personId, e);
        }
        return null;
    }

    private Person getPersonByEmail(String email) {
        if (email == null || email.trim().isEmpty()) return null;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM people WHERE LOWER(Email) = LOWER(?) LIMIT 1")) {
            ps.setString(1, email.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPerson(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting person by Email: {}", email, e);
        }
        return null;
    }

    private Person getPersonByFirstAndLastName(String firstName, String lastName) {
        if (firstName == null || firstName.trim().isEmpty() || lastName == null || lastName.trim().isEmpty()) return null;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM people WHERE LOWER(First_Name) = LOWER(?) AND LOWER(Last_Name) = LOWER(?) LIMIT 1")) {
            ps.setString(1, firstName.trim());
            ps.setString(2, lastName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPerson(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting person by First/Last name: {} {}", firstName, lastName, e);
        }
        return null;
    }

    private Person mapResultSetToPerson(java.sql.ResultSet rs) throws SQLException {
        Person person = new Person();
        person.setId(rs.getInt("ID"));
        person.setStatusId(rs.getInt("status_id"));
        person.setFirstName(rs.getString("First_Name"));
        person.setLastName(rs.getString("Last_Name"));
        person.setEmail(rs.getString("Email"));
        person.setProfileName(rs.getString("Profile_Name"));
        person.setSystemRole(rs.getObject("System_Role") != null ? rs.getInt("System_Role") : null);
        person.setFunctionName(rs.getString("Function_Name"));
        person.setFunctionDescription(rs.getString("Function_Description"));
        person.setDescription(rs.getString("Description"));
        person.setPassword(rs.getString("Password"));
        person.setOrgUnitId(rs.getObject("Org_Unit_ID") != null ? rs.getInt("Org_Unit_ID") : null);
        person.setSourceId(rs.getObject("source_id") != null ? rs.getInt("source_id") : null);
        person.setProfileImageId(rs.getObject("profile_ImageID") != null ? rs.getInt("profile_ImageID") : null);
        return person;
    }

    private Integer treatZeroAsNull(Integer value) {
        return (value != null && value == 0) ? null : value;
    }

    /** Sets people.ip_details = personId so the join in PeopleService returns the people_details row. */
    private void updatePeopleIpDetails(Connection conn, int personId) throws SQLException {
        if (conn == null) return;
        String sql = "UPDATE people SET ip_details = ? WHERE id = ?";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            ps.setInt(2, personId);
            ps.executeUpdate();
        }
    }

    private void createPeopleDetailsFull(int personId, JsonObject rowData, int userId) throws SQLException {
        String sql = """
            INSERT INTO people_details (id, employment_type, lifecycle, 
                office_location, internal_mail_code, other_info_description,
                office_telephone, mobile_telephone, lan_id,
                last_updateuser_id, created_datetime, last_updatedtime)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                employment_type = VALUES(employment_type),
                lifecycle = VALUES(lifecycle),
                office_location = VALUES(office_location),
                internal_mail_code = VALUES(internal_mail_code),
                other_info_description = VALUES(other_info_description),
                office_telephone = VALUES(office_telephone),
                mobile_telephone = VALUES(mobile_telephone),
                lan_id = VALUES(lan_id),
                last_updateuser_id = VALUES(last_updateuser_id),
                last_updatedtime = NOW()
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, personId);
            
            // employment_type (required)
            Integer employmentTypeId = coalesce(getInteger(rowData, "employment_type"),
                    getLookupIdByName("employment_type", getString(rowData, "Employment Type")));
            if (employmentTypeId == null) employmentTypeId = getFirstLookupId("employment_type");
            if (employmentTypeId != null) ps.setInt(2, employmentTypeId); else ps.setNull(2, java.sql.Types.INTEGER);
            
            // lifecycle (required)
            Integer lifecycleId = coalesce(getInteger(rowData, "lifecycle"),
                    getLookupIdByName("people_lifecycle_status", getString(rowData, "Lifecycle")));
            if (lifecycleId == null) lifecycleId = getFirstLookupId("people_lifecycle_status");
            if (lifecycleId != null) ps.setInt(3, lifecycleId); else ps.setNull(3, java.sql.Types.INTEGER);
            
            // Office fields (all optional)
            ps.setString(4, getString(rowData, "Office Location"));
            ps.setString(5, getString(rowData, "Internal Mail Code"));
            ps.setString(6, getString(rowData, "Other Info Description"));
            ps.setString(7, getString(rowData, "Office Telephone"));
            ps.setString(8, getString(rowData, "Mobile Telephone"));
            ps.setString(9, getLanId(rowData));
            ps.setInt(10, userId);
            
            ps.executeUpdate();
        }
    }

    private void updatePeopleDetailsFull(int personId, JsonObject rowData, int userId) throws SQLException {
        // First check if record exists
        String checkSql = "SELECT COUNT(*) FROM people_details WHERE id = ?";
        boolean recordExists = false;
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setInt(1, personId);
            try (java.sql.ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    recordExists = rs.getInt(1) > 0;
                }
            }
        }
        
        if (recordExists) {
            // Get old values first
            String selectSql = "SELECT * FROM people_details WHERE id = ?";
            JsonObject oldData = new JsonObject();
            try (Connection conn = DatabaseConnection.getConnection();
                 java.sql.PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                selectPs.setInt(1, personId);
                try (java.sql.ResultSet rs = selectPs.executeQuery()) {
                    if (rs.next()) {
                        oldData.addProperty("employment_type", rs.getObject("employment_type") != null ? rs.getInt("employment_type") : 0);
                        oldData.addProperty("lifecycle", rs.getObject("lifecycle") != null ? rs.getInt("lifecycle") : 0);
                        oldData.addProperty("office_location", rs.getString("office_location"));
                        oldData.addProperty("internal_mail_code", rs.getString("internal_mail_code"));
                        oldData.addProperty("other_info_description", rs.getString("other_info_description"));
                        oldData.addProperty("office_telephone", rs.getString("office_telephone"));
                        oldData.addProperty("mobile_telephone", rs.getString("mobile_telephone"));
                        oldData.addProperty("lan_id", rs.getString("lan_id"));
                    }
                }
            }
            
            String updateSql = """
                UPDATE people_details SET
                    employment_type = ?, lifecycle = ?,
                    office_location = ?, internal_mail_code = ?, other_info_description = ?,
                    office_telephone = ?, mobile_telephone = ?, lan_id = ?,
                    last_updateuser_id = ?, last_updatedtime = NOW()
                WHERE id = ?
            """;
            
            try (Connection conn = DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(updateSql)) {
                
                // employment_type
                Integer employmentTypeId = coalesce(getInteger(rowData, "employment_type"),
                        getLookupIdByName("employment_type", getString(rowData, "Employment Type")));
                if (employmentTypeId == null) employmentTypeId = treatZeroAsNull(oldData.get("employment_type").getAsInt());
                if (employmentTypeId != null) ps.setInt(1, employmentTypeId); else ps.setNull(1, java.sql.Types.INTEGER);
                
                // lifecycle
                Integer lifecycleId = coalesce(getInteger(rowData, "lifecycle"),
                        getLookupIdByName("people_lifecycle_status", getString(rowData, "Lifecycle")));
                if (lifecycleId == null) lifecycleId = treatZeroAsNull(oldData.get("lifecycle").getAsInt());
                if (lifecycleId != null) ps.setInt(2, lifecycleId); else ps.setNull(2, java.sql.Types.INTEGER);
                
                // Office fields
                ps.setString(3, getStringOrDefault(rowData, "Office Location", oldData.get("office_location") != null && !oldData.get("office_location").isJsonNull() ? oldData.get("office_location").getAsString() : null));
                ps.setString(4, getStringOrDefault(rowData, "Internal Mail Code", oldData.get("internal_mail_code") != null && !oldData.get("internal_mail_code").isJsonNull() ? oldData.get("internal_mail_code").getAsString() : null));
                ps.setString(5, getStringOrDefault(rowData, "Other Info Description", oldData.get("other_info_description") != null && !oldData.get("other_info_description").isJsonNull() ? oldData.get("other_info_description").getAsString() : null));
                ps.setString(6, getStringOrDefault(rowData, "Office Telephone", oldData.get("office_telephone") != null && !oldData.get("office_telephone").isJsonNull() ? oldData.get("office_telephone").getAsString() : null));
                ps.setString(7, getStringOrDefault(rowData, "Mobile Telephone", oldData.get("mobile_telephone") != null && !oldData.get("mobile_telephone").isJsonNull() ? oldData.get("mobile_telephone").getAsString() : null));
                String lanIdUpdate = getLanId(rowData);
                if (lanIdUpdate == null && oldData.get("lan_id") != null && !oldData.get("lan_id").isJsonNull()) {
                    lanIdUpdate = oldData.get("lan_id").getAsString();
                }
                ps.setString(8, lanIdUpdate);
                ps.setInt(9, userId);
                ps.setInt(10, personId);
                
                ps.executeUpdate();
            }
        } else {
            // Create new record
            createPeopleDetailsFull(personId, rowData, userId);
        }
    }

    private Integer getLookupIdByName(String tableName, String primaryName) {
        if (primaryName == null || primaryName.trim().isEmpty()) return null;
        
        // Determine the correct column name based on table
        String columnName = "PrimaryName"; // default
        if ("employment_type".equals(tableName) || "people_lifecycle_status".equals(tableName)) {
            columnName = "primary_Name";
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT ID FROM " + tableName + " WHERE " + columnName + " = ? LIMIT 1")) {
            ps.setString(1, primaryName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID");
            }
        } catch (SQLException e) {
            logger.error("Error getting lookup ID by name from table {}: {}", tableName, primaryName, e);
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

    /**
     * Get user's full name for audit records
     */
    private String getUserName(int userId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fullName");
                }
            }
        }
        return "Unknown User";
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
                if (segmentId == null) {
                    throw new IllegalArgumentException("Segment '" + segmentNameFromExcel.trim() + "' does not exist");
                }
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
