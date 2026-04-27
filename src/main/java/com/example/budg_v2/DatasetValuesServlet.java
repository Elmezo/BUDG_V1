package com.example.budg_v2;

import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.model.WorkflowNotification;
import java.io.File;
import java.util.UUID;

@WebServlet(name = "DatasetValuesServlet", urlPatterns = { "/api/dataset-values/*" })
@MultipartConfig
public class DatasetValuesServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DatasetValuesServlet.class);
    private static final int DATASET_FACET_TYPE = 11;
    private static final Pattern COLUMN_PATTERN = Pattern.compile("Column\\s+'([^']+)'", Pattern.CASE_INSENSITIVE);
    private final Gson gson = new Gson();
    private static String getBasePath() { return BulkPathUtil.getBulkPathForEntity("dataset-values"); }
    private final JobDAO jobDAO = new JobDAO();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();

    private static class UploadProcessResult {
        private final int insertedRows;
        private final int rejectedRows;

        private UploadProcessResult(int insertedRows, int rejectedRows) {
            this.insertedRows = insertedRows;
            this.rejectedRows = rejectedRows;
        }
    }

    private static class UploadCancelledDueToWarningException extends Exception {
        private final int rejectedRows;

        private UploadCancelledDueToWarningException(String message, int rejectedRows) {
            super(message);
            this.rejectedRows = rejectedRows;
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // Parse path: /dataset-values/{id}/metadata OR /dataset-values/{id}/sample
        String[] parts = pathInfo.split("/");
        // pathInfo starts with /, so parts[0] is empty, parts[1] is id, parts[2] is
        // action
        if (parts.length < 3) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            int datasetId = Integer.parseInt(parts[1]);
            String action = parts[2];

            if ("metadata".equalsIgnoreCase(action)) {
                // Check if viewing changes - use cloned dataset ID like Impact tab
                String view = req.getParameter("view");
                int datasetIdToLoad = datasetId; // Default to original ID
                
                if (view != null && "changes".equals(view.trim())) {
                    // Like Impact tab: use cloned dataset ID for view=changes
                    try {
                        Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_TYPE, datasetId);
                        if (activeCrId != null) {
                            Integer nobjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                            if (nobjectId == null) {
                                // Clone dataset if needed
                                try (Connection conn = DatabaseConnection.getConnection()) {
                                    nobjectId = cloneDatasetRow(datasetId);
                                    if (nobjectId != null) {
                                        facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "summary", activeCrId);
                                        logger.info("Cloned dataset {} to {} for values metadata view=changes", datasetId, nobjectId);
                                    }
                                }
                            }
                            if (nobjectId != null) {
                                datasetIdToLoad = nobjectId;
                                logger.info("Using cloned dataset ID {} (original: {}) for values metadata view=changes", nobjectId, datasetId);
                            }
                        }
                    } catch (SQLException e) {
                        logger.error("Error getting cloned dataset for view=changes: {}", e.getMessage());
                    }
                }
                
                // Load metadata using datasetIdToLoad (cloned if view=changes, original otherwise)
                handleGetMetadata(datasetIdToLoad, resp);
            } else if ("sample".equalsIgnoreCase(action)) {
                handleGetSample(datasetId, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid dataset ID\"}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo(); // /dataset-values/{id}/save or /metadata
        if (pathInfo == null)
            return;
        String[] parts = pathInfo.split("/");
        if (parts.length < 3)
            return;

        try {
            int datasetId = Integer.parseInt(parts[1]);
            String action = parts[2];

            if ("save".equalsIgnoreCase(action)) {
                handleSaveValues(datasetId, req, resp);
            } else if ("metadata".equalsIgnoreCase(action)) {
                // Legacy or pure metadata update
                // Reuse save logic or separate
                // For now, assuming new frontend uses 'save'
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject err = new JsonObject();
            err.addProperty("success", false);
            err.addProperty("status", "failed");
            err.addProperty("message", e.getMessage() != null ? e.getMessage() : "Unexpected server error");
            resp.getWriter().write(gson.toJson(err));
        }
    }
    
    /**
     * Clone a dataset row for pending changes (same as DatasetServlet.cloneDatasetRow)
     */
    private Integer cloneDatasetRow(int originalId) throws SQLException {
        String sql = "INSERT INTO dataset (" +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID" +
                ") SELECT " +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID " +
                "FROM dataset WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    private Integer getDatasetType(int datasetId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT DatasetType FROM dataset WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int typeId = rs.getInt("DatasetType");
                        return rs.wasNull() ? null : typeId;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error getting dataset type for id {}: {}", datasetId, e.getMessage());
        }
        return null;
    }
    
    private void handleGetMetadata(int datasetId, HttpServletResponse resp) throws SQLException, IOException {
        Map<String, Object> map = new HashMap<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT frequency, frequency_comments, availability, availability_comments, values_in_axon FROM values_datastore WHERE dataset_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, datasetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        populateMetadataMap(map, rs);
                    } else {
                        populateEmptyMetadataMap(map);
                    }
                }
            }
        }

        resp.getWriter().write(gson.toJson(map));
    }
    
    private void populateMetadataMap(Map<String, Object> map, ResultSet rs) throws SQLException {
        map.put("frequency", rs.getString("frequency"));
        map.put("frequencyComments", rs.getString("frequency_comments"));
        map.put("availability", rs.getString("availability"));
        map.put("availabilityComments", rs.getString("availability_comments"));
        
        // Convert boolean to string value for select dropdown
        // true -> "Sample Set", false -> "false", null -> ""
        Boolean viaValue = rs.getObject("values_in_axon", Boolean.class);
        String viaString = "";
        if (viaValue != null) {
            if (viaValue) {
                viaString = "Sample Set";
            } else {
                viaString = "false";
            }
        }
        map.put("valuesInAxon", viaString);
    }
    
    private void populateEmptyMetadataMap(Map<String, Object> map) {
        map.put("frequency", null);
        map.put("frequencyComments", null);
        map.put("availability", null);
        map.put("availabilityComments", null);
        map.put("valuesInAxon", "");
    }

    private void handleGetSample(int datasetId, HttpServletResponse resp) throws SQLException, IOException {
        List<Map<String, Object>> sampleData = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Find DataStore for this dataset (BUDG: only ONE datastore per dataset)
            String dsSql = "SELECT id FROM values_datastore WHERE dataset_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(dsSql)) {
                ps.setInt(1, datasetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int datastoreId = rs.getInt(1);

                        // Get fields
                        Map<Integer, String> fieldMap = new HashMap<>();
                        String fieldSql = "SELECT id, column_name FROM values_field WHERE datastore_id = ?";
                        try (PreparedStatement fs = conn.prepareStatement(fieldSql)) {
                            fs.setInt(1, datastoreId);
                            try (ResultSet frs = fs.executeQuery()) {
                                while (frs.next()) {
                                    fieldMap.put(frs.getInt("id"), frs.getString("column_name"));
                                }
                            }
                        }

                        // Get entries (limit to 500 for sample)
                        // For Append: Preserve insertion order - newest batches first, rows within batch in original order
                        // Order by entry_ref_id DESC (newest first), then row_index ASC (original insertion order)
                        String entrySql = "SELECT e.row_index, e.field_id, e.value_text, er.id as entry_ref_id, er.upload_timestamp " +
                                "FROM values_entry e " +
                                "INNER JOIN values_entry_ref er ON e.entry_ref_id = er.id " +
                                "WHERE er.datastore_id = ? " +
                                "ORDER BY er.id DESC, e.row_index ASC " +
                                "LIMIT 500";

                        try (PreparedStatement es = conn.prepareStatement(entrySql)) {
                            es.setInt(1, datastoreId);
                            try (ResultSet ers = es.executeQuery()) {
                                // Use LinkedHashMap to preserve insertion order
                                // Key: entry_ref_id_row_index, Value: row data map
                                // This ensures rows are added in the order they appear in the result set
                                Map<String, Map<String, Object>> rows = new java.util.LinkedHashMap<>();
                                while (ers.next()) {
                                    int entryRefId = ers.getInt("entry_ref_id");
                                    int rowIndex = ers.getInt("row_index");
                                    int fieldId = ers.getInt("field_id");
                                    String val = ers.getString("value_text");
                                    String colName = fieldMap.get(fieldId);

                                    // Use entry_ref_id + row_index as key to handle multiple entry_refs
                                    // Format: entry_ref_id_row_index to maintain order
                                    String rowKey = entryRefId + "_" + rowIndex;
                                    rows.computeIfAbsent(rowKey, k -> new HashMap<>()).put(colName, val);
                                }
                                // Add all rows in the order they were inserted (LinkedHashMap preserves order)
                                // This maintains: newest batches first, rows within each batch in insertion order
                                sampleData.addAll(rows.values());

                                // Resolve Names for IDs
                                Map<String, String> attributeTypes = getAttributeTypes(conn, datasetId);
                                if (!attributeTypes.isEmpty()) {
                                    for (Map<String, Object> row : sampleData) {
                                        for (Map.Entry<String, Object> entry : row.entrySet()) {
                                            String colName = entry.getKey();
                                            Object valObj = entry.getValue();
                                            if (valObj == null)
                                                continue;
                                            String val = valObj.toString();

                                            // Find type
                                            String type = null;
                                            for (Map.Entry<String, String> at : attributeTypes.entrySet()) {
                                                if (at.getKey().equalsIgnoreCase(colName)) {
                                                    type = at.getValue();
                                                    break;
                                                }
                                            }

                                            if (type != null) {
                                                String resolvedName = resolveReferenceName(conn, type, val);
                                                if (resolvedName != null) {
                                                    row.put(colName, resolvedName);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        resp.getWriter().write(gson.toJson(sampleData));
    }

    private void handleSaveValues(int datasetId, HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // Check edit permission + stakeholder status
        if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(req, resp, "Data Sets", datasetId)) {
            return; // Response already sent
        }
        
        // Multi-part request: metadata (json sring), file (binary), uploadType (string)
        Part filePart = req.getPart("file");
        String uploadType = req.getParameter("uploadType"); // "append" or "overwrite"
        String errorHandling = req.getParameter("errorHandling");
        boolean cancelOnWarning = errorHandling == null || errorHandling.isBlank()
                || "Cancel on Warning".equalsIgnoreCase(errorHandling);
        String metadataJson = req.getParameter("metadata");
        int userId = UserContextUtil.getCurrentUserId(req);

        if (userId > 0) {
            try {
                boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
                new DFCRService().ensureEditAutoCrIfMissing("Data Set", DATASET_FACET_TYPE, datasetId, getDatasetType(datasetId), userId, isAdmin);
            } catch (Exception e) {
                logger.warn("[DatasetValues] DFCR ensure before save values: {}", e.getMessage());
            }
        }
        
        // Check for active CR and get cloned dataset ID (like Impact tab)
        Integer activeCrId = null;
        int datasetIdToUse = datasetId; // Default to original ID
        
        try {
            activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_TYPE, datasetId);
            if (activeCrId != null) {
                // Get or create cloned dataset (like Impact tab does with cloned process)
                try (Connection conn = DatabaseConnection.getConnection()) {
                    Integer nobjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists - clone the dataset
                        nobjectId = cloneDatasetRow(datasetId);
                        if (nobjectId != null) {
                            facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "summary", activeCrId);
                            logger.info("Cloned dataset {} to {} for values pending changes", datasetId, nobjectId);
                        }
                    }
                    
                    if (nobjectId != null) {
                        datasetIdToUse = nobjectId;
                        logger.info("Using cloned dataset ID {} (original: {}) for values", nobjectId, datasetId);
                    } else {
                        logger.warn("Could not get/create cloned dataset for values, using original ID {}", datasetId);
                    }
                } catch (SQLException e) {
                    logger.error("Error getting/cloning dataset for values: {}", e.getMessage());
                    // Continue with original ID
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking for active CR: {}", e.getMessage());
        }
        
        // Fetch Dataset Name for Notification (before try block to use in catch)
        String datasetName = "Dataset";
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT primaryname FROM dataset WHERE id = ?")) {
                ps.setInt(1, datasetId); // Use original ID for name lookup
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        datasetName = rs.getString(1);
                }
            }
            
            conn.setAutoCommit(false); // Transaction start

            boolean fileUploadAttempted = (filePart != null && filePart.getSize() > 0);
            int rowCount = 0;
            int rejectedCount = 0;
            boolean uploadSuccess = false;
            int jobId = -1;
            String storagePath = null;
            String fileName = null;
            String originalFileName = null;

            try {
                // If file upload, save file and create job
                if (fileUploadAttempted && filePart != null) {
                    originalFileName = filePart.getSubmittedFileName();
                    if (originalFileName == null || originalFileName.isEmpty()) {
                        originalFileName = "upload.xlsx";
                    }
                    
                    // Save file to disk
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                    String uuid = UUID.randomUUID().toString().substring(0, 6);
                    fileName = "dataset_values_" + timestamp + "_" + uuid + ".xlsx";
                    
                    File directory = new File(getBasePath());
                    if (!directory.exists()) {
                        directory.mkdirs();
                    }
                    
                    File file = new File(getBasePath() + fileName);
                    filePart.write(file.getAbsolutePath());
                    storagePath = getBasePath() + fileName;
                    
                    logger.info("File saved to: {}", storagePath);
                    
                    // Create Job record
                    String operation = "overwrite".equalsIgnoreCase(uploadType) ? "overwrite" : "append";
                    String jobType = "dataset_values_bulk_" + operation;
                    String referenceName = "DS-" + datasetId + "-VALUES-" + operation.toUpperCase();
                    jobId = jobDAO.createJob(jobType, referenceName, 0, "Processing", userId);
                    logger.info("Job created with ID: {} for dataset values upload", jobId);
                    
                    // Create Job Resource File record
                    jobDAO.createJobResourceFile(jobId, fileName, originalFileName, storagePath, true, 90);
                    logger.info("Job resource file record created");
                    
                    // Create Job Progress record
                    jobDAO.createJobProgress(jobId, 0, "Processing", "File uploaded, processing values");
                }
                // 1. Ensure DataStore exists for this dataset (BUDG: only ONE datastore per dataset)
                // Use datasetIdToUse (cloned if CR active, original otherwise)
                int datastoreId = -1;
                String checkDs = "SELECT id FROM values_datastore WHERE dataset_id = ? LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(checkDs)) {
                    ps.setInt(1, datasetIdToUse); // Use cloned dataset ID if CR active
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            datastoreId = rs.getInt(1);
                        } else {
                            // Create new DataStore (UNIQUE constraint ensures only one per dataset)
                            try (PreparedStatement ins = conn.prepareStatement(
                                    "INSERT INTO values_datastore (dataset_id) VALUES (?)",
                                    Statement.RETURN_GENERATED_KEYS)) {
                                ins.setInt(1, datasetIdToUse); // Use cloned dataset ID if CR active
                                ins.executeUpdate();
                                try (ResultSet gk = ins.getGeneratedKeys()) {
                                    if (gk.next())
                                        datastoreId = gk.getInt(1);
                                }
                            }
                        }
                    }
                }

                // 2. Handle File Upload if present
                if (fileUploadAttempted) {
                    // Read file from saved path instead of Part
                    File savedFile = new File(storagePath);
                    if (!savedFile.exists()) {
                        throw new IOException("Saved file not found: " + storagePath);
                    }
                    
                    // Create a new Part-like object or read from file
                    // For now, we'll read the file directly
                    try (InputStream fileInputStream = new java.io.FileInputStream(savedFile)) {
                        // We need to process the file - let's create a method that accepts InputStream
                        UploadProcessResult processResult = processFileUploadFromStream(conn, datasetIdToUse, datastoreId, fileInputStream, 
                                originalFileName, uploadType, userId, jobId, cancelOnWarning);
                        rowCount = processResult.insertedRows;
                        rejectedCount = processResult.rejectedRows;
                    }
                    
                    // Only delete old values for overwrite mode AFTER successful processing
                    // This prevents data loss if the upload fails
                    if ("overwrite".equalsIgnoreCase(uploadType) && rowCount > 0) {
                        // Audit entry (use original datasetId for audit)
                        insertAudit(conn, datasetId, "DELETE", userId, "Overwrite triggered - old values deleted after successful upload");
                        
                        // Delete old entry refs and entries (excluding the one we just created)
                        // Get the latest entry_ref_id to exclude it from deletion
                        int latestEntryRefId = -1;
                        String getLatestRefSql = "SELECT id FROM values_entry_ref WHERE datastore_id = ? ORDER BY id DESC LIMIT 1";
                        try (PreparedStatement ps = conn.prepareStatement(getLatestRefSql)) {
                            ps.setInt(1, datastoreId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    latestEntryRefId = rs.getInt(1);
                                }
                            }
                        }
                        
                        // Delete entries from old entry_refs (excluding the latest one we just created)
                        if (latestEntryRefId > 0) {
                            String deleteEntries = "DELETE FROM values_entry WHERE entry_ref_id IN (SELECT id FROM values_entry_ref WHERE datastore_id = ? AND id != ?)";
                            try (PreparedStatement ps = conn.prepareStatement(deleteEntries)) {
                                ps.setInt(1, datastoreId);
                                ps.setInt(2, latestEntryRefId);
                                ps.executeUpdate();
                            }
                            
                            // Delete old entry_refs (excluding the latest one)
                            String deleteRefs = "DELETE FROM values_entry_ref WHERE datastore_id = ? AND id != ?";
                            try (PreparedStatement ps = conn.prepareStatement(deleteRefs)) {
                                ps.setInt(1, datastoreId);
                                ps.setInt(2, latestEntryRefId);
                                ps.executeUpdate();
                            }
                        }
                    }
                    
                    uploadSuccess = rejectedCount == 0;
                }

                // 3. Handle Metadata
                if (metadataJson != null && !metadataJson.isEmpty()) {
                    JsonObject meta = JsonParser.parseString(metadataJson).getAsJsonObject();

                    String updateSql = "UPDATE values_datastore SET frequency = ?, frequency_comments = ?, availability = ?, availability_comments = ?, values_in_axon = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, getJsonString(meta, "frequency"));
                        ps.setString(2, getJsonString(meta, "frequencyComments"));
                        ps.setString(3, getJsonString(meta, "availability"));
                        ps.setString(4, getJsonString(meta, "availabilityComments"));

                        // Parse valuesInAxon: "Sample Set" -> true, "false" -> false, "" -> null
                        String viaVal = getJsonString(meta, "valuesInAxon");
                        Boolean via = null;
                        if (viaVal != null && !viaVal.isEmpty()) {
                            if ("Sample Set".equalsIgnoreCase(viaVal)) {
                                via = true;
                            } else if ("false".equalsIgnoreCase(viaVal) || "False".equals(viaVal)) {
                                via = false;
                            }
                        }
                        if (via != null) {
                            ps.setBoolean(5, via);
                        } else {
                            ps.setNull(5, Types.BOOLEAN);
                        }

                        ps.setInt(6, datastoreId);
                        ps.executeUpdate();
                        
                        // Save mapping if active CR exists (datasetId is original ID, not cloned)
                        if (activeCrId != null) {
                            try {
                                // Save mapping for this value info change (use original datasetId for mapping)
                                String areaKey = "summary#dataset_value_info";
                                facetChangesDAO.saveMapping("dataset", datasetId, datastoreId, areaKey, activeCrId);
                            } catch (SQLException e) {
                                logger.warn("Error saving value info mapping for pending changes: {}", e.getMessage());
                            }
                        }
                    }
                }

                conn.commit();

                // Update job with results only after successful commit to avoid misleading My Jobs counts
                if (fileUploadAttempted && jobId > 0) {
                    int totalProcessed = rowCount + rejectedCount;
                    String finalStatus;
                    if (rowCount > 0 && rejectedCount > 0) {
                        finalStatus = "Partially Completed";
                    } else if (rowCount == 0 && rejectedCount > 0) {
                        finalStatus = "Failed";
                    } else {
                        finalStatus = "Completed";
                    }
                    String progressMessage = String.format(
                            "%s: %d inserted, %d updated, %d deleted, %d failed via %s%nBULK_COUNTS:inserted=%d,failed=%d,updated=%d,deleted=%d",
                            finalStatus, rowCount, 0, 0, rejectedCount, uploadType, rowCount, rejectedCount, 0, 0);
                    jobDAO.updateJobItemsCount(jobId, totalProcessed);
                    jobDAO.updateJobStatus(jobId, finalStatus, true);
                    jobDAO.updateJobProgress(jobId, finalStatus, progressMessage);
                }

                // Send success notification if file was uploaded
                if (fileUploadAttempted && uploadSuccess) {
                    String displayUploadType = "overwrite".equalsIgnoreCase(uploadType) ? "Overwrite" : "Append";
                    sendNotification(userId, datasetId, datasetName, displayUploadType, rowCount, true, null);
                }

                JsonObject jsonResp = new JsonObject();
                boolean hasFileOutcome = fileUploadAttempted;
                boolean fullSuccess = !hasFileOutcome || rejectedCount == 0;
                boolean partialSuccess = hasFileOutcome && rowCount > 0 && rejectedCount > 0;
                boolean fullFailure = hasFileOutcome && rowCount == 0 && rejectedCount > 0;

                jsonResp.addProperty("success", fullSuccess);
                if (fullSuccess) {
                    jsonResp.addProperty("status", "completed");
                    jsonResp.addProperty("message", "Values saved successfully");
                } else if (partialSuccess) {
                    jsonResp.addProperty("status", "partial");
                    jsonResp.addProperty("message", String.format(
                            "Upload partially completed: %d inserted, %d failed. Check My Jobs report.",
                            rowCount, rejectedCount));
                } else if (fullFailure) {
                    jsonResp.addProperty("status", "failed");
                    jsonResp.addProperty("message", String.format(
                            "Upload failed: %d rows failed validation. Check My Jobs report.",
                            rejectedCount));
                } else {
                    jsonResp.addProperty("status", "completed");
                    jsonResp.addProperty("message", "Values saved successfully");
                }
                if (jobId > 0) {
                    jsonResp.addProperty("job_id", jobId);
                    jsonResp.addProperty("inserted", rowCount);
                    jsonResp.addProperty("failed", rejectedCount);
                }
                if (fullFailure) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                resp.getWriter().write(jsonResp.toString());

            } catch (Exception e) {
                conn.rollback();
                boolean cancelledDueToWarning = e instanceof UploadCancelledDueToWarningException;
                int failedRowsForJob = rejectedCount > 0 ? rejectedCount : 1;
                if (e instanceof UploadCancelledDueToWarningException warningException) {
                    failedRowsForJob = Math.max(failedRowsForJob, warningException.rejectedRows);
                }
                
                // Update job status to failed
                if (jobId > 0) {
                    try {
                        String errorMessage = e.getMessage();
                        if (errorMessage == null || errorMessage.isEmpty()) {
                            errorMessage = "Upload failed";
                        }
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        String progressMessage = errorMessage + System.lineSeparator()
                                + "BULK_COUNTS:inserted=0,failed=" + failedRowsForJob + ",updated=0,deleted=0";
                        jobDAO.updateJobItemsCount(jobId, failedRowsForJob);
                        jobDAO.updateJobProgress(jobId, "Failed", progressMessage);
                        addJobReportMessage(jobId, "File Validation", 0, "error",
                                cancelledDueToWarning ? "CANCELLED_DUE_TO_WARNING"
                                        : ((e instanceof IllegalArgumentException) ? "VALIDATION_ERROR" : "UPLOAD_ERROR"),
                                errorMessage, "error");
                    } catch (Exception jobEx) {
                        logger.error("Failed to update job status", jobEx);
                    }
                }
                
                // Send failure notification if file upload was attempted
                if (fileUploadAttempted) {
                    String displayUploadType = "overwrite".equalsIgnoreCase(uploadType) ? "Overwrite" : "Append";
                    String errorMessage = e.getMessage();
                    if (errorMessage == null || errorMessage.isEmpty()) {
                        errorMessage = "Upload failed";
                    }
                    sendNotification(userId, datasetId, datasetName, displayUploadType, 0, false, errorMessage);
                }

                String errorMessage = e.getMessage();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = "Upload failed";
                }
                JsonObject errorResp = new JsonObject();
                errorResp.addProperty("success", false);
                errorResp.addProperty("status", "failed");
                errorResp.addProperty("message", errorMessage);
                errorResp.addProperty("cancelled_due_to_warning", cancelledDueToWarning);
                errorResp.addProperty("failed", failedRowsForJob);
                if (jobId > 0) {
                    errorResp.addProperty("job_id", jobId);
                }
                resp.setStatus((e instanceof IllegalArgumentException || cancelledDueToWarning)
                        ? HttpServletResponse.SC_BAD_REQUEST
                        : HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write(gson.toJson(errorResp));
                return;
            }
        }
    }

    /**
     * Process file upload from InputStream (for saved files)
     */
    private UploadProcessResult processFileUploadFromStream(Connection conn, int datasetId, int datastoreId, 
            InputStream fileInputStream, String fileName, String uploadType, int userId, int jobId,
            boolean cancelOnWarning) throws Exception {
        // Detect file type
        boolean isExcel = fileName != null && 
                          (fileName.toLowerCase().endsWith(".xlsx") || 
                           fileName.toLowerCase().endsWith(".xls"));

        String[] headers;
        List<String[]> dataRows;

        if (isExcel) {
            List<String[]> allRows = readExcelFile(fileInputStream, fileName);
            if (allRows.isEmpty()) {
                throw new IllegalArgumentException("Empty Excel file");
            }
            headers = allRows.get(0);
            for (int i = 0; i < headers.length; i++) {
                if (headers[i] != null) {
                    headers[i] = headers[i].trim().replaceAll("\"", "");
                }
            }
            dataRows = allRows.subList(1, allRows.size());
        } else {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(fileInputStream, StandardCharsets.UTF_8)
            );
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("Empty file");
            }
            headers = headerLine.split(",");
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim().replaceAll("\"", "");
            }
            
            dataRows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                dataRows.add(values);
            }
        }

        return processFileData(conn, datasetId, datastoreId, headers, dataRows, uploadType, userId, jobId, cancelOnWarning);
    }

    /**
     * Process file data (headers and rows) - common logic for both Part and InputStream
     */
    private UploadProcessResult processFileData(Connection conn, int datasetId, int datastoreId, String[] headers, 
            List<String[]> dataRows, String uploadType, int userId, int jobId, boolean cancelOnWarning) throws Exception {
        Map<Integer, Integer> colIndexToFieldId = new HashMap<>();

        // Validation: Check if headers match Dataset Attributes
        // Fetch Attributes (mock query or checking known structure)
        // For strict validation as requested: "If not okay, show error message"
        List<String> validAttributes = new ArrayList<>();
        String attrSql = "SELECT primaryname FROM attribute WHERE dataset_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(attrSql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    validAttributes.add(rs.getString(1).toLowerCase());
            }
        }

        // Check if all headers exist in validAttributes
        List<String> invalidHeaders = new ArrayList<>();
        for (String h : headers) {
            String cleanHeader = h.trim().replaceAll("\"", "");
            if (!validAttributes.contains(cleanHeader.toLowerCase())) {
                invalidHeaders.add(cleanHeader);
            }
        }

        if (!invalidHeaders.isEmpty() && !validAttributes.isEmpty()) {
            if (jobId > 0) {
                for (String invalidHeader : invalidHeaders) {
                    addJobReportMessage(jobId, invalidHeader, 0, "error", "INVALID_HEADER",
                            "Header '" + invalidHeader + "' does not match dataset attributes", "error");
                }
            }
            // Logic: If strict validation required, fail. But allow flexible if no
            // attributes defined?
            // User said: "names of attributes... validation... "
            throw new IllegalArgumentException("Invalid headers found: " + String.join(", ", invalidHeaders)
                    + ". Expected attributes: " + String.join(", ", validAttributes));
        }

        // BUDG: Block upload if ANY attribute is missing data type (Phase 1.3)
        // BUDG: Data Length required ONLY for String types (same logic as Frontend)
        String attrTypeCheck = "SELECT a.PrimaryName, adt.PrimaryName as DataType, a.DataLength as DataLength " +
                               "FROM attribute a " +
                               "LEFT JOIN attribute_datatype adt ON a.Data_type_ID = adt.ID " +
                               "WHERE a.Dataset_ID = ?";
        List<String> missingTypes = new ArrayList<>();
        List<String> missingLengths = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(attrTypeCheck)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String attrName = rs.getString("PrimaryName");
                    String dataType = rs.getString("DataType");
                    Integer dataLength = rs.getObject("DataLength", Integer.class); // Use getObject to handle NULL
                    
                    // Check if data type is missing (required for ALL attributes)
                    if (dataType == null || dataType.trim().isEmpty()) {
                        missingTypes.add(attrName);
                        continue; // Skip length check if type is missing
                    }
                    
                    // Check if data length is required (only for String types)
                    String dataTypeUpper = dataType.trim().toUpperCase();
                    boolean isStringType = dataTypeUpper.equals("STRING") || 
                                         dataTypeUpper.equals("VARCHAR") || 
                                         dataTypeUpper.equals("CHAR") || 
                                         dataTypeUpper.equals("TEXT");
                    
                    if (isStringType && (dataLength == null || dataLength <= 0)) {
                        missingLengths.add(attrName);
                    }
                }
            }
        }

        // Build error message
        if (!missingTypes.isEmpty() || !missingLengths.isEmpty()) {
            if (jobId > 0) {
                for (String attr : missingTypes) {
                    addJobReportMessage(jobId, attr, 0, "error", "MISSING_DATA_TYPE",
                            "Attribute is missing data type: " + attr, "error");
                }
                for (String attr : missingLengths) {
                    addJobReportMessage(jobId, attr, 0, "error", "MISSING_DATA_LENGTH",
                            "String attribute is missing data length: " + attr, "error");
                }
            }
            StringBuilder errorMsg = new StringBuilder("Cannot upload values: ");
            List<String> errors = new ArrayList<>();
            
            if (!missingTypes.isEmpty()) {
                errors.add("The following attributes are missing data type: " + String.join(", ", missingTypes));
            }
            
            if (!missingLengths.isEmpty()) {
                errors.add("The following string attributes are missing data length: " + String.join(", ", missingLengths));
            }
            
            errorMsg.append(String.join(". ", errors));
            errorMsg.append(". Please specify the required fields before uploading values.");
            
            throw new IllegalArgumentException(errorMsg.toString());
        }

        // Note: Delete operations for overwrite mode are now handled in handleSaveValues()
        // after successful processing to prevent data loss if upload fails

        // 2. Create Entry Reference
        int refId = -1;
        try (
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO values_entry_ref (datastore_id, user_id, upload_type) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, datastoreId);
            ps.setInt(2, userId);
            ps.setString(3, uploadType);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next())
                    refId = gk.getInt(1);
            }
        }

        // 3. Parse CSV (Re-read or continue if stream reset supported? InputStream
        // usually once. Need to re-open or handle stream)
        // Since we read line 1, we continue from line 2.
        // NOTE: BufferedReader consumes stream. We created 'reader' earlier. Use that.

        // Headers are already parsed above into 'headers' array.
        // But we need to map them to fields now.
        Map<Integer, String> colIndexToType = new HashMap<>();
        Map<String, String> attributeTypes = getAttributeTypes(conn, datasetId);

        for (int i = 0; i < headers.length; i++) {
            String colName = headers[i].trim().replaceAll("\"", ""); // cleanup quotes
            int fieldId = getOrCreateField(conn, datastoreId, colName, datasetId);
            colIndexToFieldId.put(i, fieldId);

            for (Map.Entry<String, String> entry : attributeTypes.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(colName)) {
                    colIndexToType.put(i, entry.getValue());
                    break;
                }
            }
        }

        int rowIndex = 0;
        int rowCount = 0;
        int rejectedCount = 0;
        List<String> rejectionReasons = new ArrayList<>();

        // Phase 3: In-memory tracking for UNIQUE rule validation (within same upload)
        Map<Integer, java.util.Set<String>> uniqueValueCache = new HashMap<>(); // fieldId -> Set of values

        // Prepare batch insert
        String insertValParams = "INSERT INTO values_entry (entry_ref_id, field_id, row_index, value_text) VALUES (?, ?, ?, ?)";
        try (
                PreparedStatement ps = conn.prepareStatement(insertValParams)) {
            // Phase 3: Process rows from Excel or CSV - row by row with error handling
            for (String[] values : dataRows) {
                rowIndex++;
                boolean rowRejected = false;
                String rejectionReason = null;
                List<String> rowValues = new ArrayList<>(); // Store values for this row

                try {
                    // BUDG: Insert values for ALL columns (rows × columns), missing values = NULL (Phase 1.2)
                    for (int i = 0; i < headers.length; i++) {
                        int fieldId = colIndexToFieldId.get(i);
                        String val = null;
                        
                        // Get value if exists in the row
                        if (i < values.length && values[i] != null) {
                            val = values[i].trim().replaceAll("^\"|\"$", ""); // remove surrounding quotes
                            if (val.isEmpty()) {
                                val = null; // Empty string becomes NULL
                            }
                        }
                        // If i >= values.length or values[i] is null, val remains null (missing column)

                        // Resolve ID if reference type and value is not null/empty
                        if (val != null && !val.isEmpty()) {
                            String type = colIndexToType.get(i);
                            if (type != null) {
                                String resolvedId = resolveReferenceId(conn, type, val);
                                if (resolvedId != null)
                                    val = resolvedId;
                            }
                            
                            // BUDG: Soft validation for data types (Phase 2.2) - reject only grossly invalid values
                            String dataType = getAttributeDataType(conn, datasetId, headers[i]);
                            if (dataType != null) {
                                try {
                                    validateValueDataTypeSoft(val, dataType, headers[i]);
                                } catch (IllegalArgumentException e) {
                                    rowRejected = true;
                                    rejectionReason = "Row " + rowIndex + ": " + e.getMessage();
                                    break; // Stop processing this row
                                }
                            }
                            
                            // Phase 3: Rule enforcement (BUDG: governance-oriented)
                            try {
                                validateFieldRules(conn, fieldId, val, rowIndex, refId, uniqueValueCache);
                            } catch (IllegalArgumentException e) {
                                rowRejected = true;
                                rejectionReason = e.getMessage(); // Already includes row number
                                break; // Stop processing this row
                            }
                        } else {
                            // Phase 3: Check REQUIRED rule even for NULL/empty values
                            try {
                                validateFieldRules(conn, fieldId, val, rowIndex, refId, uniqueValueCache);
                            } catch (IllegalArgumentException e) {
                                rowRejected = true;
                                rejectionReason = e.getMessage(); // Already includes row number
                                break; // Stop processing this row
                            }
                        }

                        rowValues.add(val); // Store value for batch insert
                    }

                    // If row passed all validations, add to batch
                    if (!rowRejected) {
                        for (int i = 0; i < headers.length; i++) {
                            int fieldId = colIndexToFieldId.get(i);
                            String val = i < rowValues.size() ? rowValues.get(i) : null;
                            
                            ps.setInt(1, refId);
                            ps.setInt(2, fieldId);
                            ps.setInt(3, rowIndex);
                            if (val == null) {
                                ps.setNull(4, Types.VARCHAR); // Explicit NULL for missing values
                            } else {
                                ps.setString(4, val);
                            }
                            ps.addBatch();
                        }
                        rowCount++;
                    } else {
                        // Row was rejected - log to audit
                        rejectedCount++;
                        rejectionReasons.add(rejectionReason);
                        if (jobId > 0) {
                            String fieldName = extractColumnName(rejectionReason);
                            addJobReportMessage(jobId, fieldName, rowIndex, "error", "ROW_VALIDATION_ERROR",
                                    rejectionReason, "error");
                        }
                        insertAudit(conn, datasetId, "REJECTED", userId, 
                                "Row " + rowIndex + " rejected: " + rejectionReason);
                        if (cancelOnWarning) {
                            throw new UploadCancelledDueToWarningException(
                                    "Upload cancelled due to warning at row " + rowIndex + ": " + rejectionReason,
                                    rejectedCount);
                        }
                    }

                } catch (UploadCancelledDueToWarningException e) {
                    throw e;
                } catch (Exception e) {
                    // Catch any unexpected errors during row processing
                    rejectedCount++;
                    String errorMsg = "Row " + rowIndex + ": Unexpected error - " + e.getMessage();
                    rejectionReasons.add(errorMsg);
                    if (jobId > 0) {
                        String fieldName = extractColumnName(errorMsg);
                        addJobReportMessage(jobId, fieldName, rowIndex, "error", "ROW_PROCESSING_ERROR",
                                errorMsg, "error");
                    }
                    insertAudit(conn, datasetId, "REJECTED", userId, errorMsg);
                    if (cancelOnWarning) {
                        throw new UploadCancelledDueToWarningException(
                                "Upload cancelled due to warning at row " + rowIndex + ": " + errorMsg,
                                rejectedCount);
                    }
                }

                // Execute batch every 100 successful rows
                if (rowCount > 0 && rowCount % 100 == 0) {
                    ps.executeBatch();
                }
            }
            
            // Execute remaining batch
            if (rowCount > 0) {
                ps.executeBatch();
            }
        }

        // Update Entry Ref with count of successfully inserted rows
        try (
                PreparedStatement ps = conn
                        .prepareStatement("UPDATE values_entry_ref SET row_count = ? WHERE id = ?")) {
            ps.setInt(1, rowCount);
            ps.setInt(2, refId);
            ps.executeUpdate();
        }

        // Audit successful upload
        String auditMessage = String.format("Uploaded %d rows via %s", rowCount, uploadType);
        if (rejectedCount > 0) {
            auditMessage += String.format(" (%d rows rejected)", rejectedCount);
        }
        insertAudit(conn, datasetId, "INSERT", userId, auditMessage);

        // If there were rejections, throw exception with details (but after successful rows are saved)
        if (rejectedCount > 0) {
            String errorSummary = String.format("Upload completed with %d successful rows and %d rejected rows. Rejection reasons: %s",
                    rowCount, rejectedCount, String.join("; ", rejectionReasons));
            logger.warn(errorSummary);
            // Don't throw exception - rows were saved, just log the rejections
            // The caller can check rowCount vs total rows to determine if there were issues
        }

        // Return row count - notification will be sent by caller
        return new UploadProcessResult(rowCount, rejectedCount);
    }

    private void addJobReportMessage(int jobId, String fieldName, int position, String itemStatus,
            String errorCode, String message, String messageType) {
        if (jobId <= 0) {
            return;
        }
        try {
            int reportItemId = jobDAO.createJobReportItem(
                    jobId,
                    fieldName != null && !fieldName.isBlank() ? fieldName : "Value Upload",
                    itemStatus,
                    position);
            jobDAO.createJobReportItemMessage(
                    reportItemId,
                    errorCode,
                    message,
                    messageType);
        } catch (Exception ex) {
            logger.error("Failed to create job report item for job {}: {}", jobId, ex.getMessage(), ex);
        }
    }

    private String extractColumnName(String message) {
        if (message == null || message.isBlank()) {
            return "Value Upload";
        }
        Matcher matcher = COLUMN_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Value Upload";
    }

    private int getOrCreateField(Connection conn, int datastoreId, String colName, int datasetId) throws SQLException {
        // Check if exists
        String sql = "SELECT id FROM values_field WHERE datastore_id = ? AND column_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datastoreId);
            ps.setString(2, colName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        
        // BUDG: Get attribute info to use correct data_type and link dataset_attribute_id (Phase 2.1)
        String attrSql = "SELECT a.ID, a.Data_type_ID, adt.PrimaryName as DataType " +
                         "FROM attribute a " +
                         "LEFT JOIN attribute_datatype adt ON a.Data_type_ID = adt.ID " +
                         "WHERE a.Dataset_ID = ? AND a.PrimaryName = ?";
        int attributeId = -1;
        String dataType = "String"; // Default fallback
        
        try (PreparedStatement ps = conn.prepareStatement(attrSql)) {
            ps.setInt(1, datasetId);
            ps.setString(2, colName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    attributeId = rs.getInt("ID");
                    String attrDataType = rs.getString("DataType");
                    if (attrDataType != null && !attrDataType.trim().isEmpty()) {
                        dataType = attrDataType;
                    }
                }
            }
        }
        
        // Create field with correct data type and attribute link
        String ins = "INSERT INTO values_field (datastore_id, column_name, data_type, dataset_attribute_id) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, datastoreId);
            ps.setString(2, colName);
            ps.setString(3, dataType);
            if (attributeId > 0) {
                ps.setInt(4, attributeId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to create field " + colName);
    }

    /**
     * Insert audit record with timestamp
     * Phase 3: Enhanced audit logging
     */
    private void insertAudit(Connection conn, int datasetId, String action, int userId, String details) {
        String sql = "INSERT INTO values_audit (dataset_id, action_type, user_id, details, action_timestamp) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.setString(2, action);
            ps.setInt(3, userId);
            ps.setString(4, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert audit record: {}", e.getMessage(), e);
            // Don't throw - audit failure shouldn't break the upload
        }
    }

    private String getJsonString(JsonObject json, String key) {
        if (json != null && json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return null;
    }

    private void sendNotification(int userId, int datasetId, String datasetName, String uploadType, int rowCount, 
                                   boolean success, String errorMessage) {
        try {
            WorkflowNotificationDAO dao = new WorkflowNotificationDAO();
            WorkflowNotification n = new WorkflowNotification();
            n.setRecipientUserId(userId);
            n.setCategory("bulk_upload");
            // Title format: "Attribute Values : Append" or "[Dataset Name] : [Type]"
            n.setTitle(
                    datasetName + " : " + (uploadType.equalsIgnoreCase("Overwrite") ? "Upload New Items" : "Append"));
            
            // Message format based on success/failure
            if (success) {
                // Message format: "Uploading of 4 items completed successfully"
                n.setMessage("Uploading of " + rowCount + " items completed successfully");
            } else {
                // Failure message
                String failureMsg = "Upload failed";
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    // Truncate long error messages
                    if (errorMessage.length() > 200) {
                        failureMsg = errorMessage.substring(0, 197) + "...";
                    } else {
                        failureMsg = errorMessage;
                    }
                }
                n.setMessage(failureMsg);
            }
            
            n.setChannel("ui");
            n.setEventType(success ? "UPLOAD_COMPLETE" : "UPLOAD_FAILED");
            n.setFacetType("dataset"); // Standardized facet name
            n.setObjectId(datasetId);
            n.setRead(false);

            dao.create(n);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to send notification: " + e.getMessage());
        }
    }

    private Map<String, String> getAttributeTypes(Connection conn, int datasetId) throws SQLException {
        Map<String, String> types = new HashMap<>();
        String sql = "SELECT a.PrimaryName, adt.PrimaryName as TypeName FROM attribute a LEFT JOIN attribute_datatype adt ON a.Data_type_ID = adt.ID WHERE a.Dataset_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    types.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return types;
    }

    private String resolveReferenceId(Connection conn, String type, String value) {
        if (type == null || value == null)
            return null;
        String table = null;
        String col = "PrimaryName";
        if (type.equalsIgnoreCase("System"))
            table = "system";
        else if (type.equalsIgnoreCase("Glossary")) {
            table = "glossary";
            col = "Name";
        } else if (type.equalsIgnoreCase("Business Area")) {
            table = "business_area";
        } else if (type.equalsIgnoreCase("Policy")) {
            table = "policy";
        } else if (type.equalsIgnoreCase("Project")) {
            table = "project";
        } else if (type.equalsIgnoreCase("Process")) {
            table = "process";
        } else if (type.equalsIgnoreCase("Person") || type.equalsIgnoreCase("Employee")) {
            return resolvePersonId(conn, value);
        }

        if (table != null) {
            String sql = "SELECT ID FROM " + table + " WHERE " + col + " = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, value);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        return String.valueOf(rs.getInt(1));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private String resolvePersonId(Connection conn, String value) {
        String sql = "SELECT ID FROM people WHERE CONCAT(First_Name, ' ', Last_Name) = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return String.valueOf(rs.getInt(1));
            }
        } catch (Exception e) {
        }
        return null;
    }

    private String resolveReferenceName(Connection conn, String type, String value) {
        if (type == null || value == null)
            return null;
        // Verify it's an integer ID
        if (!value.matches("\\d+"))
            return null;

        String table = null;
        String col = "PrimaryName";
        if (type.equalsIgnoreCase("System"))
            table = "system";
        else if (type.equalsIgnoreCase("Glossary")) {
            table = "glossary";
            col = "Name";
        } else if (type.equalsIgnoreCase("Business Area")) {
            table = "business_area";
        } else if (type.equalsIgnoreCase("Policy")) {
            table = "policy";
        } else if (type.equalsIgnoreCase("Project")) {
            table = "project";
        } else if (type.equalsIgnoreCase("Process")) {
            table = "process";
        } else if (type.equalsIgnoreCase("Person") || type.equalsIgnoreCase("Employee")) {
            return resolvePersonName(conn, value);
        }

        if (table != null) {
            String sql = "SELECT " + col + " FROM " + table + " WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, value);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        return rs.getString(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private String resolvePersonName(Connection conn, String value) {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString(1);
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Get data type for an attribute (helper method for validation)
     */
    private String getAttributeDataType(Connection conn, int datasetId, String attributeName) throws SQLException {
        String sql = "SELECT adt.PrimaryName as DataType " +
                     "FROM attribute a " +
                     "LEFT JOIN attribute_datatype adt ON a.Data_type_ID = adt.ID " +
                     "WHERE a.Dataset_ID = ? AND a.PrimaryName = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.setString(2, attributeName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("DataType");
                }
            }
        }
        return null;
    }

    /**
     * BUDG-style soft validation: reject only grossly invalid values
     * Allows reasonable variations for dates and booleans
     */
    private void validateValueDataTypeSoft(String value, String dataType, String columnName) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) return; // NULL values are allowed
        
        String upperType = dataType.toUpperCase();
        
        // Integer validation - must be parseable as integer
        if (upperType.contains("INT") || upperType.equals("INTEGER")) {
            try {
                Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Column '" + columnName + "' must be an integer, got: " + value);
            }
            return;
        }
        
        // Decimal/Float validation - must be parseable as decimal
        if (upperType.contains("DECIMAL") || upperType.contains("FLOAT") || 
            upperType.contains("DOUBLE") || upperType.contains("NUMERIC")) {
            try {
                Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Column '" + columnName + "' must be a decimal number, got: " + value);
            }
            return;
        }
        
        // Boolean validation - flexible (true/false, 1/0, yes/no, etc.)
        if (upperType.contains("BOOL")) {
            String lowerVal = value.trim().toLowerCase();
            if (!lowerVal.equals("true") && !lowerVal.equals("false") && 
                !lowerVal.equals("1") && !lowerVal.equals("0") &&
                !lowerVal.equals("yes") && !lowerVal.equals("no") &&
                !lowerVal.equals("y") && !lowerVal.equals("n")) {
                throw new IllegalArgumentException("Column '" + columnName + "' must be boolean (true/false/1/0/yes/no), got: " + value);
            }
            return;
        }
        
        // Date validation - soft (try to parse, don't enforce strict format)
        if (upperType.contains("DATE")) {
            // Try common date formats - if none work, it's invalid
            String trimmed = value.trim();
            // Basic check: should contain numbers and separators
            if (!trimmed.matches(".*\\d+.*")) {
                throw new IllegalArgumentException("Column '" + columnName + "' must be a date, got: " + value);
            }
            // More lenient: just check it has some date-like structure
            // Don't enforce YYYY-MM-DD strictly (BUDG soft validation)
            return;
        }
        
        // String types don't need validation
    }

    /**
     * Read Excel file (.xlsx or .xls) and return rows as List<String[]>
     * First row is headers, rest are data rows
     * Phase 3: Excel support
     */
    private List<String[]> readExcelFile(InputStream is, String fileName) throws IOException {
        List<String[]> rows = new ArrayList<>();
        
        Workbook workbook;
        if (fileName.toLowerCase().endsWith(".xlsx")) {
            workbook = new XSSFWorkbook(is);
        } else {
            workbook = new HSSFWorkbook(is);
        }
        
        try {
            Sheet sheet = workbook.getSheetAt(0); // First sheet only
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Empty Excel file");
            }
            
            for (Row row : sheet) {
                if (row == null) continue;
                
                List<String> cellValues = new ArrayList<>();
                int lastCellNum = row.getLastCellNum();
                
                for (int i = 0; i < lastCellNum; i++) {
                    Cell cell = row.getCell(i);
                    String value = null;
                    if (cell != null) {
                        value = getCellValueAsString(cell);
                        // Convert empty strings to null for consistency
                        if (value != null && value.trim().isEmpty()) {
                            value = null;
                        }
                    }
                    cellValues.add(value);
                }
                
                if (!cellValues.isEmpty()) {
                    rows.add(cellValues.toArray(new String[0]));
                }
            }
        } finally {
            workbook.close();
        }
        
        return rows;
    }

    /**
     * Convert Excel cell to String value
     * Phase 3: Excel support
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Format date as yyyy-MM-dd
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    return sdf.format(cell.getDateCellValue());
                } else {
                    // Check if it's a whole number
                    double numValue = cell.getNumericCellValue();
                    if (numValue == Math.floor(numValue)) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Try to get the calculated value
                try {
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    switch (cellValue.getCellType()) {
                        case STRING:
                            return cellValue.getStringValue();
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                                return sdf.format(DateUtil.getJavaDate(cellValue.getNumberValue()));
                            } else {
                                double numVal = cellValue.getNumberValue();
                                if (numVal == Math.floor(numVal)) {
                                    return String.valueOf((long) numVal);
                                } else {
                                    return String.valueOf(numVal);
                                }
                            }
                        case BOOLEAN:
                            return String.valueOf(cellValue.getBooleanValue());
                        default:
                            return cell.getCellFormula();
                    }
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            case BLANK:
                return null; // NULL for blank cells
            default:
                return "";
        }
    }

    /**
     * Validate field rules (MAX_LENGTH, REQUIRED, UNIQUE)
     * BUDG: Governance-oriented validation, blocks only clear violations
     * Phase 3: Rule enforcement
     */
    private void validateFieldRules(Connection conn, int fieldId, String value, 
                                    int rowIndex, int entryRefId,
                                    Map<Integer, java.util.Set<String>> uniqueValueCache) 
        throws SQLException, IllegalArgumentException {
        String rulesSql = "SELECT rule_type, rule_value FROM values_rule WHERE field_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(rulesSql)) {
            ps.setInt(1, fieldId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ruleType = rs.getString("rule_type");
                    String ruleValue = rs.getString("rule_value");
                    
                    switch (ruleType) {
                        case "MAX_LENGTH":
                            validateMaxLength(fieldId, value, ruleValue, rowIndex);
                            break;
                        case "REQUIRED":
                            validateRequired(fieldId, value, rowIndex);
                            break;
                        case "UNIQUE":
                            validateUnique(conn, fieldId, value, entryRefId, rowIndex, uniqueValueCache);
                            break;
                    }
                }
            }
        }
    }

    /**
     * Validate MAX_LENGTH rule
     * Phase 3: Rule enforcement
     */
    private void validateMaxLength(int fieldId, String value, String ruleValue, int rowIndex) 
        throws IllegalArgumentException {
        if (value != null && value.length() > Integer.parseInt(ruleValue)) {
            throw new IllegalArgumentException(
                String.format("Row %d, Field ID %d: Value exceeds maximum length of %s characters (value length: %d)", 
                             rowIndex, fieldId, ruleValue, value.length())
            );
        }
    }

    /**
     * Validate REQUIRED rule
     * Phase 3: Rule enforcement
     */
    private void validateRequired(int fieldId, String value, int rowIndex) 
        throws IllegalArgumentException {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Row %d, Field ID %d: Field is required but value is empty", rowIndex, fieldId)
            );
        }
    }

    /**
     * Validate UNIQUE rule (application-level, within same entry_ref)
     * Phase 3: Rule enforcement
     * Uses in-memory cache to track values in current upload
     */
    private void validateUnique(Connection conn, int fieldId, String value, int entryRefId, int rowIndex,
                                Map<Integer, java.util.Set<String>> uniqueValueCache) 
        throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            return; // NULL values are allowed (unless REQUIRED)
        }
        
        // Use in-memory cache for uniqueness check within same upload
        uniqueValueCache.putIfAbsent(fieldId, new java.util.HashSet<>());
        java.util.Set<String> seenValues = uniqueValueCache.get(fieldId);
        
        if (seenValues.contains(value)) {
            throw new IllegalArgumentException(
                String.format("Row %d, Field ID %d: Value must be unique within this upload, duplicate value found: %s", 
                             rowIndex, fieldId, value)
            );
        }
        
        // Add to cache for next row
        seenValues.add(value);
    }
}
