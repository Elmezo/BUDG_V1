package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DocumentStorageService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
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

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Servlet for handling document file uploads
 * Endpoint: POST /api/documents/upload
 */
@WebServlet("/api/documents/upload")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, // 2MB
        maxFileSize = 1024 * 1024 * 10, // 10MB
        maxRequestSize = 1024 * 1024 * 50 // 50MB
)
public class DocumentUploadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DocumentUploadServlet.class);
    private final Gson gson = new Gson();
    private final DocumentStorageService storageService = new DocumentStorageService();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
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

            // Get form parameters
            String facetType = request.getParameter("facetType");
            String facetIdStr = request.getParameter("facetId");
            String name = request.getParameter("name");
            String description = request.getParameter("description");
            String documentTypeIdStr = request.getParameter("documentTypeId");

            // Validate parameters
            String validationError = validateParameters(facetType, facetIdStr, name, description, documentTypeIdStr);
            if (validationError != null) {
                sendError(response, validationError, HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int facetId = Integer.parseInt(facetIdStr);
            int documentTypeId = Integer.parseInt(documentTypeIdStr);

            // Validate name and description length
            if (name.trim().length() < 3) {
                sendError(response, "Name must be at least 3 characters long.", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            if (description.trim().length() < 3) {
                sendError(response, "Document description must be at least 3 characters long.",
                        HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Get uploaded file
            Part filePart = request.getPart("file");
            if (filePart == null) {
                sendError(response, "No file uploaded", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String originalFilename = getFileName(filePart);
            if (originalFilename == null || originalFilename.isEmpty()) {
                sendError(response, "Invalid file name", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Validate filename
            String filenameError = storageService.getFilenameValidationError(originalFilename);
            if (filenameError != null) {
                sendError(response, "Upload failed with: " + filenameError, HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Validate file size
            long fileSize = filePart.getSize();
            if (!storageService.isValidFileSize(fileSize)) {
                sendError(response, "File size exceeds maximum allowed size of " +
                        DocumentStorageService.getMaxFileSizeMB() + "MB", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Check edit permission
            if (!hasEditPermission(request, response, facetType)) {
                // Error response already sent by PermissionCheckUtil.checkEditPermission
                return;
            }

            // Save file to disk
            String relativePath;
            try (InputStream fileContent = filePart.getInputStream()) {
                relativePath = storageService.saveFile(fileContent, facetType, originalFilename);
            }

            // Save document metadata to database
            int documentId = saveDocumentMetadata(facetType, facetId, documentTypeId, name, description,
                    relativePath, originalFilename, fileSize, userId);

            // Check for active CR and save mapping for pending changes (for supported facets)
            boolean isPending = false;
            Integer activeCrId = null;
            if (isSupportedForPendingChanges(facetType)) {
                try {
                    Integer facetTypeId = facetChangesDAO.getFacetId(facetType);
                    if (facetTypeId != null) {
                        activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetTypeId, facetId);
                        if (activeCrId != null) {
                            // Save mapping for pending changes tracking
                            String areaKey = "documents#" + getDocumentTableName(facetType);
                            facetChangesDAO.saveMapping(facetType.toLowerCase(), facetId, documentId, areaKey, activeCrId);
                            isPending = true;
                            logger.info("Document {} saved as pending change for {} ID {} (CR: {})", 
                                documentId, facetType, facetId, activeCrId);
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Could not check/save pending changes for document: {}", e.getMessage());
                    // Continue without pending changes - document is still saved
                }
            }

            // Return success response
            JsonObject responseData = new JsonObject();
            responseData.addProperty("success", true);
            responseData.addProperty("message", isPending ? "Document uploaded as pending change" : "Document uploaded successfully");
            responseData.addProperty("documentId", documentId);
            responseData.addProperty("fileName", originalFilename);
            responseData.addProperty("pending", isPending);
            if (activeCrId != null) {
                responseData.addProperty("changeRequestId", activeCrId);
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(responseData));

            logger.info("Document uploaded successfully: {} for {} ID {} (pending: {})", originalFilename, facetType, facetId, isPending);

        } catch (NumberFormatException e) {
            logger.error("Invalid number format in request", e);
            sendError(response, "Invalid facet ID or document type ID", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e) {
            logger.error("Database error during document upload", e);
            sendError(response, "Database error: " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Error during document upload", e);
            sendError(response, "Internal server error: " + e.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Validate request parameters
     */
    private String validateParameters(String facetType, String facetIdStr, String name,
            String description, String documentTypeIdStr) {
        if (facetType == null || facetType.trim().isEmpty()) {
            return "Facet type is required";
        }
        if (facetIdStr == null || facetIdStr.trim().isEmpty()) {
            return "Facet ID is required";
        }
        if (name == null || name.trim().isEmpty()) {
            return "You must enter a Document Name.";
        }
        if (description == null || description.trim().isEmpty()) {
            return "You must enter a Document Description.";
        }
        if (documentTypeIdStr == null || documentTypeIdStr.trim().isEmpty()) {
            return "You must select a Document Type.";
        }
        return null;
    }

    /**
     * Save document metadata to database
     */
    private int saveDocumentMetadata(String facetType, int facetId, int documentTypeId,
            String name, String description, String filePath,
            String fileName, long fileSize, int userId) throws SQLException {
        String tableName = getDocumentTableName(facetType);
        String facetColumn = getFacetColumnName(facetType);

        String sql = "INSERT INTO " + tableName + " (" + facetColumn + ", Document_Type_ID, Name, Description, " +
                "File_Path, File_Name, Is_URL, File_Size, CreatedBy, Last_UpdatedUser_ID) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, facetId);
            ps.setInt(2, documentTypeId);
            ps.setString(3, name);
            ps.setString(4, description);
            ps.setString(5, filePath);
            ps.setString(6, fileName);
            ps.setLong(7, fileSize);
            ps.setInt(8, userId);
            ps.setInt(9, userId);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        throw new SQLException("Failed to insert document metadata");
    }

    /**
     * Check if user has edit permission on facet
     * Uses PermissionCheckUtil to verify role-based Edit permission for the facet
     */
    private boolean hasEditPermission(HttpServletRequest request, HttpServletResponse response, String facetType) throws IOException {
        // Map facetType to module name (same logic as AuthFilter.mapModuleName)
        String moduleName = mapFacetTypeToModuleName(facetType);
        if (moduleName == null) {
            logger.warn("Unknown facet type for permission check: {}", facetType);
            sendError(response, "Unknown facet type: " + facetType, HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        
        // Use PermissionCheckUtil to check Edit permission
        return PermissionCheckUtil.checkEditPermission(request, response, moduleName);
    }
    
    /**
     * Map facet type to module name (same logic as AuthFilter.mapModuleName)
     */
    private String mapFacetTypeToModuleName(String facetType) {
        if (facetType == null) return null;
        return switch (facetType.toLowerCase()) {
            case "policy", "policies" -> "Policy";
            case "system", "systems" -> "System";
            case "dataset", "datasets" -> "Data Sets";
            case "glossary", "glossaries" -> "Glossary";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "interface", "interfaces" -> "Interface";
            case "system-interface", "system_interface", "systeminterfaces" -> "Interface";
            case "client", "clients" -> "Client";
            case "committee", "committees" -> "Committee";
            case "geography", "geographies" -> "Geography";
            case "capability", "capabilities" -> "Capability";
            case "business-area", "business_area", "business-areas", "business_areas" -> "Business Area";
            case "org-unit", "org_unit", "org-units", "org_units" -> "Org Unit";
            case "people" -> "People";
            case "legal", "legal-entity", "legalentity", "legal-entities", "legal_entities" -> "Legal Entity";
            default -> capitalizeFirst(facetType);
        };
    }
    
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
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
     * Extract filename from Part header
     */
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition != null) {
            for (String content : contentDisposition.split(";")) {
                if (content.trim().startsWith("filename")) {
                    return content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
                }
            }
        }
        return null;
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
     * Send error response
     */
    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        response.setStatus(status);
        response.getWriter().write(gson.toJson(error));
    }
}
