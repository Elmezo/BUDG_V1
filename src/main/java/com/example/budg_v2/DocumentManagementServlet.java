package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DocumentStorageService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet for document management operations (list, download, add URL, delete)
 * Endpoints:
 * GET /api/documents?facetType={type}&facetId={id} - List documents
 * GET /api/documents/{id}/download?facetType={type} - Download document
 * POST /api/documents/url - Add URL documents
 * DELETE /api/documents/{id}?facetType={type} - Delete document
 */
@WebServlet({"/api/documents", "/api/documents/*"})
public class DocumentManagementServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DocumentManagementServlet.class);
    private final Gson gson = new Gson();
    private final DocumentStorageService storageService = new DocumentStorageService();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        // Check if this is a download request
        if (pathInfo != null && pathInfo.matches("/\\d+/download")) {
            handleDownload(request, response);
            return;
        }

        // Otherwise, list documents
        handleList(request, response);
    }

    /**
     * List documents for a facet
     */
    private void handleList(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String facetType = request.getParameter("facetType");
            String facetIdStr = request.getParameter("facetId");
            String view = request.getParameter("view"); // 'changes' to include pending documents

            if (facetType == null || facetIdStr == null) {
                sendError(response, "facetType and facetId are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int facetId = Integer.parseInt(facetIdStr);

            // Get documents from database
            JsonArray documents;
            if ("changes".equals(view) && isSupportedForPendingChanges(facetType)) {
                // Include pending documents (from active CR)
                documents = getDocumentsWithPending(facetType, facetId);
            } else {
                // Original documents only (exclude pending)
                documents = getDocumentsExcludingPending(facetType, facetId);
            }

            JsonObject responseData = new JsonObject();
            responseData.addProperty("success", true);
            responseData.add("documents", documents);
            responseData.addProperty("count", documents.size());

            response.getWriter().write(gson.toJson(responseData));

        } catch (NumberFormatException e) {
            sendError(response, "Invalid facet ID", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e) {
            logger.error("Database error listing documents", e);
            sendError(response, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Error listing documents", e);
            sendError(response, "Internal server error: " + e.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Download document file
     */
    private void handleDownload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String pathInfo = request.getPathInfo();
            String[] parts = pathInfo.split("/");
            int documentId = Integer.parseInt(parts[1]);

            String facetType = request.getParameter("facetType");
            if (facetType == null) {
                sendError(response, "facetType is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Get document metadata from database
            DocumentMetadata metadata = getDocumentMetadata(facetType, documentId);
            if (metadata == null) {
                sendError(response, "Document not found", HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // If it's a URL, redirect to it
            if (metadata.isUrl) {
                response.sendRedirect(metadata.filePath);
                return;
            }

            // Otherwise, serve the file
            Path filePath = storageService.getAbsolutePath(metadata.filePath);
            if (!Files.exists(filePath)) {
                sendError(response, "File not found on disk", HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Set response headers for file download
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + metadata.fileName + "\"");
            response.setContentLengthLong(Files.size(filePath));

            // Stream file to response
            try (InputStream inputStream = Files.newInputStream(filePath);
                    OutputStream outputStream = response.getOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            logger.info("Downloaded document: {} (ID: {})", metadata.fileName, documentId);

        } catch (NumberFormatException e) {
            sendError(response, "Invalid document ID", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e) {
            logger.error("Database error downloading document", e);
            sendError(response, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Error downloading document", e);
            sendError(response, "Internal server error: " + e.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get current user ID
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                sendError(response, "User not authenticated", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();

            String facetType = requestData.get("facetType").getAsString();
            int facetId = requestData.get("facetId").getAsInt();
            JsonArray urls = requestData.getAsJsonArray("urls");

            if (urls == null || urls.size() == 0) {
                sendError(response, "No URLs provided", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Check edit permission
            if (!hasEditPermission(userId, facetType, facetId)) {
                sendError(response, "You don't have permission to add documents for this object",
                        HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            // Check for active CR for pending changes
            boolean isPending = false;
            Integer activeCrId = null;
            if (isSupportedForPendingChanges(facetType)) {
                try {
                    Integer facetTypeId = facetChangesDAO.getFacetId(facetType);
                    if (facetTypeId != null) {
                        activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetTypeId, facetId);
                        if (activeCrId != null) {
                            isPending = true;
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Could not check pending changes status: {}", e.getMessage());
                }
            }

            // Add each URL
            int addedCount = 0;
            List<Integer> addedDocIds = new ArrayList<>();
            for (int i = 0; i < urls.size(); i++) {
                JsonObject urlData = urls.get(i).getAsJsonObject();

                String url = urlData.get("url").getAsString();
                String name = urlData.get("name").getAsString();
                String description = urlData.get("description").getAsString();
                int documentTypeId = urlData.get("documentTypeId").getAsInt();

                // Validate
                if (name.trim().length() < 3) {
                    continue; // Skip invalid entries
                }
                if (description.trim().length() < 3) {
                    continue;
                }
                if (url.trim().isEmpty()) {
                    continue;
                }

                // Save URL document
                int docId = saveUrlDocument(facetType, facetId, documentTypeId, name, description, url, userId);
                if (docId > 0) {
                    addedCount++;
                    addedDocIds.add(docId);
                }
            }

            // Save pending changes mappings if CR is active
            if (isPending && activeCrId != null && !addedDocIds.isEmpty()) {
                String areaKey = "documents#" + getDocumentTableName(facetType);
                for (Integer docId : addedDocIds) {
                    try {
                        facetChangesDAO.saveMapping(facetType.toLowerCase(), facetId, docId, areaKey, activeCrId);
                        logger.info("URL document {} saved as pending change for {} ID {} (CR: {})", 
                            docId, facetType, facetId, activeCrId);
                    } catch (SQLException e) {
                        logger.warn("Could not save pending change mapping for document {}: {}", docId, e.getMessage());
                    }
                }
            }

            JsonObject responseData = new JsonObject();
            responseData.addProperty("success", true);
            responseData.addProperty("message", isPending ? addedCount + " URL(s) added as pending changes" : addedCount + " URL(s) added successfully");
            responseData.addProperty("count", addedCount);
            responseData.addProperty("pending", isPending);
            if (activeCrId != null) {
                responseData.addProperty("changeRequestId", activeCrId);
            }

            response.getWriter().write(gson.toJson(responseData));

            logger.info("Added {} URL documents for {} ID {} (pending: {})", addedCount, facetType, facetId, isPending);

        } catch (SQLException e) {
            logger.error("Database error adding URL documents", e);
            sendError(response, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Error adding URL documents", e);
            sendError(response, "Internal server error: " + e.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get current user ID
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                sendError(response, "User not authenticated", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Document ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int documentId = Integer.parseInt(pathInfo.substring(1));
            String facetType = request.getParameter("facetType");

            if (facetType == null) {
                sendError(response, "facetType is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Get document metadata before deleting
            DocumentMetadata metadata = getDocumentMetadata(facetType, documentId);
            if (metadata == null) {
                sendError(response, "Document not found", HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Delete from database
            deleteDocument(facetType, documentId);

            // Delete file from disk if it's not a URL
            if (!metadata.isUrl) {
                storageService.deleteFile(metadata.filePath);
            }

            JsonObject responseData = new JsonObject();
            responseData.addProperty("success", true);
            responseData.addProperty("message", "Document deleted successfully");

            response.getWriter().write(gson.toJson(responseData));

            logger.info("Deleted document ID {} from {}", documentId, facetType);

        } catch (NumberFormatException e) {
            sendError(response, "Invalid document ID", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e) {
            logger.error("Database error deleting document", e);
            sendError(response, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Error deleting document", e);
            sendError(response, "Internal server error: " + e.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                sendError(response, "User not authenticated", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1 || pathInfo.contains("download")) {
                sendError(response, "Document ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int documentId;
            try {
                documentId = Integer.parseInt(pathInfo.substring(1).split("/")[0]);
            } catch (NumberFormatException e) {
                sendError(response, "Invalid document ID", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String facetType = request.getParameter("facetType");
            if (facetType == null) {
                sendError(response, "facetType is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            DocumentMetadata metadata = getDocumentMetadata(facetType, documentId);
            if (metadata == null) {
                sendError(response, "Document not found", HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if (!hasEditPermission(userId, facetType, 0)) {
                sendError(response, "You don't have permission to edit documents for this object",
                        HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();

            String name = body.has("name") ? body.get("name").getAsString().trim() : null;
            String description = body.has("description") ? body.get("description").getAsString().trim() : null;
            if (name == null || name.length() < 3) {
                sendError(response, "Name must be at least 3 characters", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            if (description == null || description.length() < 3) {
                sendError(response, "Description must be at least 3 characters", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            if (!body.has("documentTypeId")) {
                sendError(response, "documentTypeId is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            int documentTypeId = body.get("documentTypeId").getAsInt();

            String url = null;
            if (metadata.isUrl && body.has("url")) {
                url = body.get("url").getAsString().trim();
                if (url.isEmpty()) {
                    sendError(response, "URL cannot be empty for URL documents", HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
            }

            updateDocument(facetType, documentId, name, description, documentTypeId, url, userId);

            JsonObject responseData = new JsonObject();
            responseData.addProperty("success", true);
            responseData.addProperty("message", "Document updated successfully");
            response.getWriter().write(gson.toJson(responseData));
            logger.info("Updated document ID {} for {}", documentId, facetType);

        } catch (SQLException e) {
            logger.error("Database error updating document", e);
            sendError(response, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Error updating document", e);
            sendError(response, "Internal server error: " + e.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Update document metadata (and URL path for URL documents only)
     */
    private void updateDocument(String facetType, int documentId, String name, String description,
            int documentTypeId, String urlOrNull, int userId) throws SQLException {
        String tableName = getDocumentTableName(facetType);
        String sql;
        if (urlOrNull != null) {
            sql = "UPDATE " + tableName + " SET Name = ?, Description = ?, Document_Type_ID = ?, " +
                    "File_Path = ?, File_Name = ?, Last_UpdatedUser_ID = ? WHERE ID = ? AND DeletedDatetime IS NULL";
        } else {
            sql = "UPDATE " + tableName + " SET Name = ?, Description = ?, Document_Type_ID = ?, " +
                    "Last_UpdatedUser_ID = ? WHERE ID = ? AND DeletedDatetime IS NULL";
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, name);
            ps.setString(i++, description);
            ps.setInt(i++, documentTypeId);
            if (urlOrNull != null) {
                ps.setString(i++, urlOrNull);
                ps.setString(i++, urlOrNull);
            }
            ps.setInt(i++, userId);
            ps.setInt(i++, documentId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Document not found or already deleted");
            }
        }
    }

    /**
     * Get documents for a facet (all documents)
     */
    @SuppressWarnings("unused")
    private JsonArray getDocuments(String facetType, int facetId) throws SQLException {
        String tableName = getDocumentTableName(facetType);
        String facetColumn = getFacetColumnName(facetType);

        String sql = "SELECT d.ID, d.Document_Type_ID, d.Name, d.Description, d.File_Name, d.File_Path, d.Is_URL, " +
                "d.File_Size, d.CreatedDatetime, dt.PrimaryName as DocumentType " +
                "FROM " + tableName + " d " +
                "LEFT JOIN document dt ON d.Document_Type_ID = dt.ID " +
                "WHERE d." + facetColumn + " = ? AND d.DeletedDatetime IS NULL " +
                "ORDER BY d.CreatedDatetime DESC";

        JsonArray documents = new JsonArray();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, facetId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject doc = new JsonObject();
                    doc.addProperty("id", rs.getInt("ID"));
                    doc.addProperty("documentTypeId", rs.getInt("Document_Type_ID"));
                    doc.addProperty("name", rs.getString("Name"));
                    doc.addProperty("description", rs.getString("Description"));
                    doc.addProperty("fileName", rs.getString("File_Name"));
                    doc.addProperty("filePath", rs.getString("File_Path"));
                    doc.addProperty("isUrl", rs.getBoolean("Is_URL"));
                    doc.addProperty("fileSize", rs.getLong("File_Size"));
                    doc.addProperty("documentType", rs.getString("DocumentType"));
                    doc.addProperty("createdDatetime", rs.getString("CreatedDatetime"));
                    documents.add(doc);
                }
            }
        }

        return documents;
    }
    
    /**
     * Get documents excluding pending ones (for original view)
     * Excludes documents that are tracked in pending changes for an active CR
     */
    private JsonArray getDocumentsExcludingPending(String facetType, int facetId) throws SQLException {
        String tableName = getDocumentTableName(facetType);
        String facetColumn = getFacetColumnName(facetType);
        String changesTableName = getChangesTableName(facetType);
        
        // Get pending document IDs from changes table
        List<Integer> pendingDocIds = new ArrayList<>();
        Integer activeCrId = null;
        try {
            Integer facetTypeId = facetChangesDAO.getFacetId(facetType);
            if (facetTypeId != null) {
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetTypeId, facetId);
                if (activeCrId != null && changesTableName != null) {
                    // ⚠️ FIX: Get ALL pending document IDs (not just for this specific area_key)
                    // This ensures we exclude all documents that were added during pending changes
                    // Use case-insensitive LIKE pattern to match any documents area key
                    String pendingSql = "SELECT DISTINCT nobject_id, area_key FROM " + changesTableName + 
                                       " WHERE object_id = ? AND change_request_id = ? " +
                                       "AND area_key LIKE ?";
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(pendingSql)) {
                        ps.setInt(1, facetId);
                        ps.setInt(2, activeCrId);
                        ps.setString(3, "documents#%"); // Match any documents area key (case-insensitive by default)
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                // Use getObject to properly handle NULL values
                                Object nobjectIdObj = rs.getObject("nobject_id");
                                String areaKey = rs.getString("area_key");
                                
                                // Check for NULL and valid positive ID
                                if (nobjectIdObj != null) {
                                    Integer nobjectId = (Integer) nobjectIdObj;
                                    if (nobjectId > 0) {
                                        if (!pendingDocIds.contains(nobjectId)) {
                                            pendingDocIds.add(nobjectId);
                                            logger.debug("Found pending document ID {} with area_key '{}' for {} ID {} (CR: {})", 
                                                nobjectId, areaKey, facetType, facetId, activeCrId);
                                        }
                                    } else {
                                        logger.warn("⚠️ [View Original] Skipping invalid nobject_id: {} (<= 0) for area_key '{}'", 
                                            nobjectId, areaKey);
                                    }
                                } else {
                                    logger.warn("⚠️ [View Original] Skipping NULL nobject_id for area_key '{}'", areaKey);
                                }
                            }
                        }
                    }
                    logger.info("🔍 [View Original] Excluding {} pending document(s) from View Original for {} ID {} (CR: {})", 
                        pendingDocIds.size(), facetType, facetId, activeCrId);
                    
                    // Additional diagnostic: Always check what area_keys exist for this CR
                    String diagnosticSql = "SELECT COUNT(*) as cnt, GROUP_CONCAT(DISTINCT area_key) as area_keys, " +
                                          "GROUP_CONCAT(DISTINCT nobject_id) as nobject_ids FROM " + changesTableName + 
                                          " WHERE object_id = ? AND change_request_id = ?";
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(diagnosticSql)) {
                        ps.setInt(1, facetId);
                        ps.setInt(2, activeCrId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                int totalMappings = rs.getInt("cnt");
                                String allAreaKeys = rs.getString("area_keys");
                                String allNObjectIds = rs.getString("nobject_ids");
                                logger.info("🔍 [View Original] Diagnostic: {} total mappings for {} ID {} (CR: {}), area_keys: {}, nobject_ids: {}", 
                                    totalMappings, facetType, facetId, activeCrId, allAreaKeys, allNObjectIds);
                                
                                // If we found mappings but no pending doc IDs, there might be an issue with the LIKE pattern
                                if (totalMappings > 0 && pendingDocIds.isEmpty()) {
                                    logger.warn("⚠️ [View Original] WARNING: Found {} mappings but 0 pending doc IDs! This suggests the LIKE pattern 'documents#%' is not matching.", totalMappings);
                                }
                            }
                        }
                    } catch (SQLException e) {
                        logger.debug("Could not run diagnostic query: {}", e.getMessage());
                    }
                } else {
                    logger.debug("No active CR found for {} ID {} - showing all documents in View Original", facetType, facetId);
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not get pending document IDs for {} ID {}: {}", facetType, facetId, e.getMessage(), e);
        }
        
        // Build SQL to exclude pending documents
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.ID, d.Document_Type_ID, d.Name, d.Description, d.File_Name, d.File_Path, d.Is_URL, ");
        sql.append("d.File_Size, d.CreatedDatetime, dt.PrimaryName as DocumentType ");
        sql.append("FROM ").append(tableName).append(" d ");
        sql.append("LEFT JOIN document dt ON d.Document_Type_ID = dt.ID ");
        sql.append("WHERE d.").append(facetColumn).append(" = ? AND d.DeletedDatetime IS NULL ");
        
        if (!pendingDocIds.isEmpty()) {
            sql.append("AND d.ID NOT IN (");
            for (int i = 0; i < pendingDocIds.size(); i++) {
                sql.append(i > 0 ? ", ?" : "?");
            }
            sql.append(") ");
        }
        sql.append("ORDER BY d.CreatedDatetime DESC");

        JsonArray documents = new JsonArray();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            ps.setInt(paramIndex++, facetId);
            for (Integer docId : pendingDocIds) {
                ps.setInt(paramIndex++, docId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject doc = new JsonObject();
                    doc.addProperty("id", rs.getInt("ID"));
                    doc.addProperty("documentTypeId", rs.getInt("Document_Type_ID"));
                    doc.addProperty("name", rs.getString("Name"));
                    doc.addProperty("description", rs.getString("Description"));
                    doc.addProperty("fileName", rs.getString("File_Name"));
                    doc.addProperty("filePath", rs.getString("File_Path"));
                    doc.addProperty("isUrl", rs.getBoolean("Is_URL"));
                    doc.addProperty("fileSize", rs.getLong("File_Size"));
                    doc.addProperty("documentType", rs.getString("DocumentType"));
                    doc.addProperty("createdDatetime", rs.getString("CreatedDatetime"));
                    doc.addProperty("isPending", false);
                    documents.add(doc);
                }
            }
        }

        return documents;
    }
    
    /**
     * Get documents including pending ones (for changes view)
     * Includes all documents and marks pending ones with isPending=true
     */
    private JsonArray getDocumentsWithPending(String facetType, int facetId) throws SQLException {
        String tableName = getDocumentTableName(facetType);
        String facetColumn = getFacetColumnName(facetType);
        String changesTableName = getChangesTableName(facetType);
        
        // Get pending document IDs from changes table
        List<Integer> pendingDocIds = new ArrayList<>();
        Integer activeCrId = null;
        try {
            Integer facetTypeId = facetChangesDAO.getFacetId(facetType);
            if (facetTypeId != null) {
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetTypeId, facetId);
                if (activeCrId != null && changesTableName != null) {
                    // ⚠️ FIX: Get ALL pending document IDs (not just for this specific area_key)
                    // This ensures we correctly mark all documents that were added during pending changes
                    // Use case-insensitive LIKE pattern to match any documents area key
                    String pendingSql = "SELECT DISTINCT nobject_id, area_key FROM " + changesTableName + 
                                       " WHERE object_id = ? AND change_request_id = ? " +
                                       "AND area_key LIKE ?";
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(pendingSql)) {
                        ps.setInt(1, facetId);
                        ps.setInt(2, activeCrId);
                        ps.setString(3, "documents#%"); // Match any documents area key (case-insensitive by default)
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                // Use getObject to properly handle NULL values
                                Object nobjectIdObj = rs.getObject("nobject_id");
                                String areaKey = rs.getString("area_key");
                                
                                // Check for NULL and valid positive ID
                                if (nobjectIdObj != null) {
                                    Integer nobjectId = (Integer) nobjectIdObj;
                                    if (nobjectId > 0) {
                                        if (!pendingDocIds.contains(nobjectId)) {
                                            pendingDocIds.add(nobjectId);
                                            logger.debug("Found pending document ID {} with area_key '{}' for {} ID {} (CR: {})", 
                                                nobjectId, areaKey, facetType, facetId, activeCrId);
                                        }
                                    } else {
                                        logger.warn("⚠️ [View Changes] Skipping invalid nobject_id: {} (<= 0) for area_key '{}'", 
                                            nobjectId, areaKey);
                                    }
                                } else {
                                    logger.warn("⚠️ [View Changes] Skipping NULL nobject_id for area_key '{}'", areaKey);
                                }
                            }
                        }
                    }
                    logger.info("🔍 [View Changes] Marking {} document(s) as pending in View Changes for {} ID {} (CR: {})", 
                        pendingDocIds.size(), facetType, facetId, activeCrId);
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not get pending document IDs for {} ID {}: {}", facetType, facetId, e.getMessage(), e);
        }
        
        // Get all documents
        String sql = "SELECT d.ID, d.Document_Type_ID, d.Name, d.Description, d.File_Name, d.File_Path, d.Is_URL, " +
                "d.File_Size, d.CreatedDatetime, dt.PrimaryName as DocumentType " +
                "FROM " + tableName + " d " +
                "LEFT JOIN document dt ON d.Document_Type_ID = dt.ID " +
                "WHERE d." + facetColumn + " = ? AND d.DeletedDatetime IS NULL " +
                "ORDER BY d.CreatedDatetime DESC";

        JsonArray documents = new JsonArray();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, facetId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int docId = rs.getInt("ID");
                    JsonObject doc = new JsonObject();
                    doc.addProperty("id", docId);
                    doc.addProperty("documentTypeId", rs.getInt("Document_Type_ID"));
                    doc.addProperty("name", rs.getString("Name"));
                    doc.addProperty("description", rs.getString("Description"));
                    doc.addProperty("fileName", rs.getString("File_Name"));
                    doc.addProperty("filePath", rs.getString("File_Path"));
                    doc.addProperty("isUrl", rs.getBoolean("Is_URL"));
                    doc.addProperty("fileSize", rs.getLong("File_Size"));
                    doc.addProperty("documentType", rs.getString("DocumentType"));
                    doc.addProperty("createdDatetime", rs.getString("CreatedDatetime"));
                    doc.addProperty("isPending", pendingDocIds.contains(docId));
                    documents.add(doc);
                }
            }
        }

        return documents;
    }
    
    /**
     * Get changes table name for facet type
     */
    private String getChangesTableName(String facetType) {
        switch (facetType.toLowerCase()) {
            case "glossary": return "glossary_changes";
            case "system": return "system_changes";
            case "process": return "process_changes";
            case "dataset": return "dataset_changes";
            default: return null;
        }
    }

    /**
     * Get document metadata
     */
    private DocumentMetadata getDocumentMetadata(String facetType, int documentId) throws SQLException {
        String tableName = getDocumentTableName(facetType);

        String sql = "SELECT File_Path, File_Name, Is_URL FROM " + tableName +
                " WHERE ID = ? AND DeletedDatetime IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, documentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    DocumentMetadata metadata = new DocumentMetadata();
                    metadata.filePath = rs.getString("File_Path");
                    metadata.fileName = rs.getString("File_Name");
                    metadata.isUrl = rs.getBoolean("Is_URL");
                    return metadata;
                }
            }
        }

        return null;
    }

    /**
     * Save URL document
     * @return The ID of the newly created document, or -1 if failed
     */
    private int saveUrlDocument(String facetType, int facetId, int documentTypeId,
            String name, String description, String url, int userId) throws SQLException {
        String tableName = getDocumentTableName(facetType);
        String facetColumn = getFacetColumnName(facetType);

        String sql = "INSERT INTO " + tableName + " (" + facetColumn + ", Document_Type_ID, Name, Description, " +
                "File_Path, File_Name, Is_URL, CreatedBy, Last_UpdatedUser_ID) " +
                "VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, facetId);
            ps.setInt(2, documentTypeId);
            ps.setString(3, name);
            ps.setString(4, description);
            ps.setString(5, url);
            ps.setString(6, url); // Use URL as filename for display
            ps.setInt(7, userId);
            ps.setInt(8, userId);

            ps.executeUpdate();
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }
    
    /**
     * Check if facet type supports pending changes
     */
    private boolean isSupportedForPendingChanges(String facetType) {
        String lower = facetType.toLowerCase();
        return "glossary".equals(lower) || "system".equals(lower) || 
               "process".equals(lower) || "dataset".equals(lower);
    }

    /**
     * Delete document
     */
    private void deleteDocument(String facetType, int documentId) throws SQLException {
        String tableName = getDocumentTableName(facetType);

        // Soft delete
        String sql = "UPDATE " + tableName + " SET DeletedDatetime = NOW() WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, documentId);
            ps.executeUpdate();
        }
    }

    /**
     * Check if user has edit permission
     */
    private boolean hasEditPermission(int userId, String facetType, int facetId) throws SQLException {
        // For now, return true - will be enhanced with actual permission logic
        return true;
    }

    /**
     * Get document table name for facet type
     */
    private String getDocumentTableName(String facetType) {
        switch (facetType.toLowerCase()) {
            case "system":
                return "system_x_document";
            case "dataset":
                return "dataset_x_document";
            case "glossary":
                return "glossary_x_document";
            case "process":
                return "process_x_document";
            case "project":
                return "project_x_document";
            case "capability":
                return "capability_x_document";
            case "changerequest":
                return "changerequest_x_document";
            case "interface":
                return "interface_x_document";
            case "client":
                return "client_x_document";
            default:
                throw new IllegalArgumentException("Unknown facet type: " + facetType);
        }
    }

    /**
     * Get facet column name for facet type
     */
    private String getFacetColumnName(String facetType) {
        switch (facetType.toLowerCase()) {
            case "system":
                return "System_ID";
            case "dataset":
                return "Dataset_ID";
            case "glossary":
                return "Glossary_ID";
            case "process":
                return "Process_ID";
            case "project":
                return "Project_ID";
            case "capability":
                return "Capability_ID";
            case "changerequest":
                return "ChangeRequest_ID";
            case "interface":
                return "Interface_ID";
            case "client":
                return "Client_ID";
            default:
                throw new IllegalArgumentException("Unknown facet type: " + facetType);
        }
    }

    /**
     * Send error response
     */
    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        response.setStatus(status);
        response.getWriter().write(gson.toJson(error));
    }

    /**
     * Inner class for document metadata
     */
    private static class DocumentMetadata {
        String filePath;
        String fileName;
        boolean isUrl;
    }
}
