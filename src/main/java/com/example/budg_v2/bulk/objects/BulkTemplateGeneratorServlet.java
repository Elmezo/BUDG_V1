package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ModuleResolver;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.RoleService;
import com.example.budg_v2.model.Role;
import com.example.unisonsearch.service.ConfigurationService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationConstraint;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servlet for generating dynamic Excel templates for bulk upload
 * Endpoint: /api/bulk/templates/generate/{entity}/{templateType}
 * templateType: INSERT, UPDATE, or DELETE
 * entity: regulator, geography, regulatorytheme, regulation
 */
@WebServlet(urlPatterns = {"/api/bulk/templates/generate/*"})
public class BulkTemplateGeneratorServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkTemplateGeneratorServlet.class);
    private static final Gson gson = new Gson();
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        CorsUtil.setCorsHeaders(response);
        
        try {
            // Parse path: /api/bulk/templates/generate/{entity}/{templateType}
            String pathInfo = request.getPathInfo();
            String requestURI = request.getRequestURI();
            String contextPath = request.getContextPath();
            String servletPath = request.getServletPath();
            
            logger.info("RequestURI: {}, ContextPath: {}, ServletPath: {}, PathInfo: {}", 
                requestURI, contextPath, servletPath, pathInfo);
            
            String entity;
            String templateType;
            
            // Try to get path from PathInfo first (works with wildcard patterns)
            if (pathInfo != null && !pathInfo.equals("/") && !pathInfo.isEmpty()) {
                // PathInfo should be like "/regulation/INSERT"
                String[] pathParts = pathInfo.substring(1).split("/");
                logger.info("Path parts from PathInfo: {}", java.util.Arrays.toString(pathParts));
                
                if (pathParts.length != 2) {
                    sendErrorResponse(response, "Invalid path format. Expected: /api/bulk/templates/generate/{entity}/{templateType}. Got pathInfo: " + pathInfo, 400);
                    return;
                }
                
                entity = pathParts[0].toLowerCase();
                templateType = pathParts[1].toUpperCase();
            } else {
                // Fallback: parse from request URI
                String path = requestURI;
                if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                    path = path.substring(contextPath.length());
                }
                
                String prefix = "/api/bulk/templates/generate/";
                if (!path.startsWith(prefix)) {
                    sendErrorResponse(response, "Invalid path format. Expected: /api/bulk/templates/generate/{entity}/{templateType}. Got URI: " + requestURI, 400);
                    return;
                }
                
                String remainingPath = path.substring(prefix.length());
                String[] pathParts = remainingPath.split("/");
                logger.info("Path parts from URI: {}", java.util.Arrays.toString(pathParts));
                
                if (pathParts.length != 2) {
                    sendErrorResponse(response, "Invalid path format. Expected: /api/bulk/templates/generate/{entity}/{templateType}. Got: " + remainingPath, 400);
                    return;
                }
                
                entity = pathParts[0].toLowerCase();
                templateType = pathParts[1].toUpperCase();
            }
            
            if (!templateType.equals("INSERT") && !templateType.equals("UPDATE") && !templateType.equals("DELETE")) {
                sendErrorResponse(response, "Invalid template type. Must be INSERT, UPDATE, or DELETE", 400);
                return;
            }
            
            // Get segment parameters from query string
            String segmentMode = request.getParameter("segmentMode");
            String segment = request.getParameter("segment");
            
            // Get user ID for segment access control
            int userId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(request);
            
            logger.info("Generating dynamic template - Entity: {}, Type: {}, SegmentMode: {}, Segment: {}, UserId: {}", 
                    entity, templateType, segmentMode, segment, userId);
            
            // Generate Excel template with dropdown lists from database
            XSSFWorkbook workbook = null;
            try {
                workbook = generateTemplate(entity, templateType, segmentMode, segment, userId);
                
                // Set response headers for file download (only after successful generation)
                // Check if segmentation is enabled and segmentMode is provided for INSERT templates
                ConfigurationService configService = new ConfigurationService();
                boolean isSegmentationEnabled = configService.isInformationSegmentationEnabled();
                String fileName;
                
                if ("INSERT".equals(templateType) && isSegmentationEnabled && segmentMode != null && !segmentMode.isEmpty()) {
                    // Use "Segments X {Entity} TEMPLATE {Type}.xlsx" format when segmentation is enabled
                    String entityDisplayName = getEntityDisplayName(entity);
                    fileName = "Segments X " + entityDisplayName + " TEMPLATE " + templateType + ".xlsx";
                } else {
                    // Use original format for non-segmented templates
                    fileName = "TEMPLATE_" + templateType + ".xlsx";
                }
                
                // Set headers before writing to output stream
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                
                // Write workbook to response
                try (OutputStream outputStream = response.getOutputStream()) {
                    workbook.write(outputStream);
                    outputStream.flush();
                    logger.info("Dynamic template generated successfully: {} for {}", fileName, entity);
                }
            } finally {
                if (workbook != null) {
                    try {
                        workbook.close();
                    } catch (Exception e) {
                        logger.warn("Error closing workbook: {}", e.getMessage());
                    }
                }
            }
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage(), e);
            sendErrorResponse(response, "Invalid request: " + e.getMessage(), 400);
        } catch (SQLException e) {
            logger.error("Database error generating template: {}", e.getMessage(), e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Error generating dynamic template", e);
            e.printStackTrace(); // Print full stack trace for debugging
            sendErrorResponse(response, "Internal server error: " + e.getMessage() + 
                (e.getCause() != null ? " (Cause: " + e.getCause().getMessage() + ")" : ""), 500);
        }
    }
    
    /**
     * Generate Excel template based on entity type
     */
    private XSSFWorkbook generateTemplate(String entity, String templateType, String segmentMode, String segment, int userId) throws SQLException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Determine sheet name based on template type
        String sheetName;
        String entityDisplayName = entity.equals("regulatorytheme") || entity.equals("regulatory_theme") 
            ? "Regulatory Theme" 
            : capitalizeFirst(entity);
        
        if ("INSERT".equals(templateType)) {
            sheetName = "Create " + entityDisplayName;
        } else if ("UPDATE".equals(templateType)) {
            sheetName = "Update " + entityDisplayName;
        } else {
            sheetName = "Delete " + entityDisplayName;
        }
        
        XSSFSheet sheet = workbook.createSheet(sheetName);
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        
        // Get entity-specific configuration
        EntityConfig config = getEntityConfig(entity);
        
        // Get lookup values from database if needed
        Map<String, List<String>> lookupValues = new HashMap<>();
        
        if (config.hasLookups()) {
            for (String lookupTable : config.getLookupTables()) {
                List<String> values;
                if ("people".equals(entity) && "role".equals(lookupTable) && isAdmin(userId)) {
                    try {
                        List<Role> roles = new RoleService().getRolesForBulkUploadByAdmin();
                        values = roles.stream()
                                .map(Role::getPrimaryname)
                                .filter(n -> n != null && !n.isEmpty())
                                .collect(Collectors.toList());
                    } catch (SQLException e) {
                        logger.warn("Failed to get roles for bulk upload by admin, falling back to all roles: {}", e.getMessage());
                        values = getLookupValues(lookupTable);
                    }
                } else {
                    values = getLookupValues(lookupTable);
                }
                lookupValues.put(lookupTable, values);
            }
        }
        
        // Generate headers and example row based on entity
        if ("regulator".equals(entity)) {
            generateRegulatorTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("geography".equals(entity)) {
            generateGeographyTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("regulatorytheme".equals(entity) || "regulatory_theme".equals(entity)) {
            generateRegulatoryThemeTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("regulation".equals(entity)) {
            generateRegulationTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("policy".equals(entity)) {
            generatePolicyTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("process".equals(entity)) {
            generateProcessTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("project".equals(entity)) {
            generateProjectTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("committee".equals(entity)) {
            generateCommitteeTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("businessarea".equals(entity) || "business_area".equals(entity)) {
            generateBusinessAreaTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("capability".equals(entity)) {
            generateCapabilityTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("client".equals(entity)) {
            generateClientTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("legal".equals(entity)) {
            generateLegalTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("orgunit".equals(entity) || "org_unit".equals(entity)) {
            generateOrgUnitTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("people".equals(entity)) {
            generatePeopleTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("product".equals(entity)) {
            generateProductTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("system".equals(entity)) {
            generateSystemTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("dataset".equals(entity)) {
            generateDatasetTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("attribute".equals(entity)) {
            generateAttributeTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("interface".equals(entity)) {
            generateInterfaceTemplate(sheet, headerRow, templateType, lookupValues);
        } else if ("glossary".equals(entity)) {
            generateGlossaryTemplate(sheet, headerRow, templateType, lookupValues);
        } else {
            throw new IllegalArgumentException("Unsupported entity: " + entity);
        }

        // Add custom fields columns (for INSERT and UPDATE templates)
        try {
            int moduleId = ModuleResolver.getModuleId(entity);
            addCustomFieldsColumns(sheet, headerRow, moduleId, templateType);
        } catch (Exception e) {
            // If module not found or error getting custom fields, log and continue
            logger.warn("Could not add custom fields for entity {}: {}", entity, e.getMessage());
        }

        // Add Segment column for INSERT and UPDATE templates when segmentation is enabled
        // Check both: INFORMATION_SEGMENTATION_ENABLED config AND (for INSERT) segmentMode parameter
        // IMPORTANT: Segment must be the LAST column - no columns should be added after this
        // Exclude Segment column for attribute, interface, and people entities
        ConfigurationService configService = new ConfigurationService();
        boolean isSegmentationEnabled = configService.isInformationSegmentationEnabled();
        
        int segmentColumnIndex = -1; // Store segment column index to set default value later
        boolean addSegment = isSegmentationEnabled && !"attribute".equals(entity) && !"interface".equals(entity) && !"people".equals(entity)
            && (("INSERT".equals(templateType) && segmentMode != null && !segmentMode.isEmpty())
                || "UPDATE".equals(templateType));
        if (addSegment) {
            String segMode = "UPDATE".equals(templateType) ? null : segmentMode;
            String seg = "UPDATE".equals(templateType) ? null : segment;
            segmentColumnIndex = addSegmentColumn(sheet, headerRow, segMode, seg, userId);
        }

        // Add standard header comments/notes for common columns (ID, Name, Ref, Governance Role, etc.)
        addStandardHeaderComments(entity, templateType, sheet, headerRow);
        
        // Add entity-specific column comments/notes for columns that don't have comments yet
        addEntitySpecificColumnComments(entity, templateType, sheet, headerRow);
        
        // Highlight BUDG Status and BUDG Viewing as mandatory (red) for INSERT templates
        applyMandatoryColumnHighlight(sheet, headerRow, templateType);
        
        // Re-apply segment default value after all columns are added (to ensure it's not overwritten)
        if (segmentColumnIndex >= 0) {
            logger.info("Calling setSegmentDefaultValue - segmentColumnIndex: {}, segmentMode: {}, segment: {}", 
                    segmentColumnIndex, segmentMode, segment);
            setSegmentDefaultValue(sheet, segmentColumnIndex, segmentMode, segment);
        } else {
            logger.info("Not calling setSegmentDefaultValue - segmentColumnIndex is -1 (segment column not added)");
        }
        
        // Auto-size columns
        int maxCol = headerRow.getLastCellNum();
        for (int i = 0; i < maxCol; i++) {
            sheet.autoSizeColumn(i);
        }
        
        return workbook;
    }
    
    /**
     * Add standard comments (notes) to common object headers
     * This complements any existing comments (e.g., date format hints) without overriding them.
     */
    private void addStandardHeaderComments(String entity, String templateType, XSSFSheet sheet, Row headerRow) {
        if (headerRow == null) {
            return;
        }

        int maxCol = headerRow.getLastCellNum();
        for (int i = 0; i < maxCol; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null || cell.getCellType() != CellType.STRING) {
                continue;
            }

            // Do not override existing comments (e.g., detailed date format comments)
            if (cell.getCellComment() != null) {
                continue;
            }

            String header = cell.getStringCellValue();
            if (header == null) {
                continue;
            }
            String headerLower = header.toLowerCase();
            String commentText = null;

            // ID columns
            if ("id".equals(headerLower) || headerLower.endsWith(" id")) {
                if ("INSERT".equals(templateType)) {
                    commentText = "ID is generated by the system. Leave this column empty when creating new records.";
                } else if ("UPDATE".equals(templateType)) {
                    commentText = IDENTITY_GROUP_MSG;
                } else {
                    commentText = "ID of the existing record. Required for DELETE operations.";
                }
            }
            // Reference columns (identity ref: exact "reference", "ref.", "ref")
            else if (headerLower.equals("reference") || headerLower.equals("ref.") || headerLower.equals("ref")) {
                if ("UPDATE".equals(templateType)) {
                    commentText = IDENTITY_GROUP_MSG;
                } else {
                    commentText = "Reference code of the object. Can be used as an alternate identifier together with or instead of Name.";
                }
            }
            // Name columns (excluding Parent Name) - for UPDATE, identity group
            else if (headerLower.contains("name") && !headerLower.contains("parent")) {
                if ("UPDATE".equals(templateType)) {
                    commentText = IDENTITY_GROUP_MSG;
                } else {
                    commentText = "Name of the object. Used to identify the record when ID/Ref are not provided or to help disambiguate duplicates.";
                }
            }
            // Parent Name columns
            else if (headerLower.contains("parent") && headerLower.contains("name")) {
                commentText = "Parent object name (optional). Used only when multiple objects share the same name to help disambiguate.";
            }
            // Governance Role columns
            else if ("governance role".equals(headerLower)) {
                commentText = "Governance Role. Select a value from the dropdown list. Values are loaded dynamically from object_role for this module.";
            }

            if (commentText != null && !commentText.isEmpty()) {
                addCellComment(sheet, headerRow, i, commentText);
            }
        }
    }

    /**
     * Add entity-specific column comments (notes) to headers
     * Only adds comments to columns that don't already have comments (preserves existing notes)
     */
    private void addEntitySpecificColumnComments(String entity, String templateType, XSSFSheet sheet, Row headerRow) {
        if (headerRow == null) {
            return;
        }

        int maxCol = headerRow.getLastCellNum();
        for (int i = 0; i < maxCol; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null || cell.getCellType() != CellType.STRING) {
                continue;
            }

            // Do not override existing comments (preserve all existing notes)
            if (cell.getCellComment() != null) {
                continue;
            }

            String header = cell.getStringCellValue();
            if (header == null || header.trim().isEmpty()) {
                continue;
            }
            
            String headerLower = header.toLowerCase().trim();
            String commentText = getEntitySpecificColumnDescription(entity, templateType, header, headerLower);

            if (commentText != null && !commentText.isEmpty()) {
                addCellComment(sheet, headerRow, i, commentText);
            }
        }
    }

    /**
     * Get entity-specific column description based on column name
     * Returns null if no description is available (column will be skipped)
     */
    private String getEntitySpecificColumnDescription(String entity, String templateType, String header, String headerLower) {
        // Common columns that appear across multiple entities
        String commonDescription = getCommonColumnDescription(headerLower, templateType);
        if (commonDescription != null) {
            return commonDescription;
        }

        // Entity-specific descriptions
        switch (entity.toLowerCase()) {
            case "regulator":
                return getRegulatorColumnDescription(headerLower, templateType);
            case "geography":
                return getGeographyColumnDescription(headerLower, templateType);
            case "regulatorytheme":
            case "regulatory_theme":
                return getRegulatoryThemeColumnDescription(headerLower, templateType);
            case "regulation":
                return getRegulationColumnDescription(headerLower, templateType);
            case "policy":
                return getPolicyColumnDescription(headerLower, templateType);
            case "process":
                return getProcessColumnDescription(headerLower, templateType);
            case "project":
                return getProjectColumnDescription(headerLower, templateType);
            case "committee":
                return getCommitteeColumnDescription(headerLower, templateType);
            case "businessarea":
            case "business_area":
                return getBusinessAreaColumnDescription(headerLower, templateType);
            case "capability":
                return getCapabilityColumnDescription(headerLower, templateType);
            case "client":
                return getClientColumnDescription(headerLower, templateType);
            case "legal":
                return getLegalColumnDescription(headerLower, templateType);
            case "orgunit":
            case "org_unit":
                return getOrgUnitColumnDescription(headerLower, templateType);
            case "people":
                return getPeopleColumnDescription(headerLower, templateType);
            case "product":
                return getProductColumnDescription(headerLower, templateType);
            case "system":
                return getSystemColumnDescription(headerLower, templateType);
            case "dataset":
                return getDatasetColumnDescription(headerLower, templateType);
            case "attribute":
                return getAttributeColumnDescription(headerLower, templateType);
            case "interface":
                return getInterfaceColumnDescription(headerLower, templateType);
            case "glossary":
                return getGlossaryColumnDescription(headerLower, templateType);
            default:
                return null;
        }
    }

    /**
     * Get description for common columns that appear across multiple entities
     */
    private String getCommonColumnDescription(String headerLower, String templateType) {
        // User fields
        if (headerLower.contains("user email")) {
            return "Email address of the user associated with this record. Used for governance and ownership tracking.";
        }
        if (headerLower.contains("user first name")) {
            return "First name of the user associated with this record.";
        }
        if (headerLower.contains("user last name")) {
            return "Last name of the user associated with this record.";
        }
        if (headerLower.contains("user lan id") || headerLower.contains("user lanid")) {
            return "LAN ID (Local Area Network ID) of the user associated with this record.";
        }
        
        // BUDG fields
        if (headerLower.contains("budg viewing")) {
            return "BUDG Viewing classification. Select a value from the dropdown list. Controls visibility and access permissions.";
        }
        if (headerLower.contains("budg status")) {
            return "BUDG Status of the record. Select a value from the dropdown list. Indicates the current operational status.";
        }
        
        // Lifecycle
        if (headerLower.equals("lifecycle")) {
            return "Lifecycle stage of the record. Select a value from the dropdown list. Required field that indicates the current phase in the object's lifecycle.";
        }
        
        // Classification
        if (headerLower.equals("classification")) {
            return "Classification category for the record. Select a value from the dropdown list. Used for categorization and filtering.";
        }
        
        // Description
        if (headerLower.equals("description")) {
            return "Detailed description of the record. Provides additional context and information about the object.";
        }
        
        // Parent references
        if (headerLower.contains("parent ref") || headerLower.contains("parent ref.")) {
            return "Reference code of the parent object. Used to establish hierarchical relationships between records.";
        }
        
        // Short Name
        if (headerLower.contains("short name")) {
            return "Short name or abbreviated identifier for the record. Often used as a unique identifier or code.";
        }
        
        // Long Name
        if (headerLower.contains("long name")) {
            return "Full or extended name of the record. Provides the complete descriptive name.";
        }
        
        return null;
    }

    // Entity-specific column description methods
    
    private String getRegulatorColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("primaryname") || headerLower.contains("primary name")) {
            return "Primary name of the regulator. This is the main identifier for the regulator.";
        }
        if (headerLower.contains("shortname") || headerLower.contains("short name")) {
            return "Short name or abbreviated identifier for the regulator.";
        }
        return null;
    }

    private String getGeographyColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("geography name")) {
            return "Name of the geography location or region.";
        }
        if (headerLower.contains("geography definition")) {
            return "Definition or description of the geography area.";
        }
        if (headerLower.contains("parent geography")) {
            return "Parent geography name. Used to establish hierarchical relationships between geographic regions.";
        }
        return null;
    }

    private String getRegulatoryThemeColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("regulatory theme long name") || headerLower.contains("long name")) {
            return "Full name of the regulatory theme. This is the complete descriptive name.";
        }
        if (headerLower.contains("short name")) {
            return "Short name or abbreviated identifier for the regulatory theme.";
        }
        if (headerLower.contains("parent regulatory theme")) {
            return "Parent regulatory theme name. Used to establish hierarchical relationships.";
        }
        return null;
    }

    private String getRegulationColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("regulation name")) {
            return "Name of the regulation.";
        }
        if (headerLower.contains("regulation number")) {
            return "Regulation number or identifier.";
        }
        if (headerLower.contains("published date") || headerLower.contains("publication date")) {
            return "Date when the regulation was published. Format: dd/mm/yyyy";
        }
        if (headerLower.contains("comments date")) {
            return "Date for comments period. Format: dd/mm/yyyy";
        }
        if (headerLower.contains("final date")) {
            return "Final date or effective date of the regulation. Format: dd/mm/yyyy";
        }
        if (headerLower.contains("compliance date")) {
            return "Date by which compliance is required. Format: dd/mm/yyyy";
        }
        if (headerLower.contains("regulator name")) {
            return "Name of the regulator that issued this regulation.";
        }
        if (headerLower.contains("regulatory theme")) {
            return "Regulatory theme associated with this regulation.";
        }
        return null;
    }

    private String getPolicyColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("policy name")) {
            return "Name of the policy.";
        }
        if (headerLower.contains("policy number")) {
            return "Policy number or identifier.";
        }
        if (headerLower.contains("published date") || headerLower.contains("publication date")) {
            return "Date when the policy was published. Format: dd/mm/yyyy";
        }
        if (headerLower.contains("comments date")) {
            return "Date for comments period. Format: dd/mm/yyyy";
        }
        if (headerLower.contains("final date")) {
            return "Final date or effective date of the policy. Format: dd/mm/yyyy";
        }
        if (headerLower.contains("compliance date")) {
            return "Date by which compliance is required. Format: dd/mm/yyyy";
        }
        if (headerLower.contains("regulation name")) {
            return "Name of the regulation this policy relates to.";
        }
        return null;
    }

    private String getProcessColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("input description")) {
            return "Description of the inputs required for this process.";
        }
        if (headerLower.contains("output description")) {
            return "Description of the outputs produced by this process.";
        }
        if (headerLower.contains("step type")) {
            return "Type of process step. Select a value from the dropdown list.";
        }
        if (headerLower.contains("create permission")) {
            return "Create permission setting. Select 'yes' or 'no' from the dropdown.";
        }
        if (headerLower.contains("read permission")) {
            return "Read permission setting. Select 'yes' or 'no' from the dropdown.";
        }
        if (headerLower.contains("update permission")) {
            return "Update permission setting. Select 'yes' or 'no' from the dropdown.";
        }
        if (headerLower.contains("delete permission")) {
            return "Delete permission setting. Select 'yes' or 'no' from the dropdown.";
        }
        if (headerLower.contains("archive permission")) {
            return "Archive permission setting. Select 'yes' or 'no' from the dropdown.";
        }
        if (headerLower.contains("duration")) {
            return "Duration of the process. Can be a time period or duration value.";
        }
        if (headerLower.contains("duration type")) {
            return "Type of duration measurement. Select a value from the dropdown list.";
        }
        if (headerLower.contains("type") && !headerLower.contains("duration")) {
            return "Process type. Select a value from the dropdown list. Required field.";
        }
        if (headerLower.contains("automation")) {
            return "Automation level or status. Select a value from the dropdown list.";
        }
        return null;
    }

    private String getProjectColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("project name")) {
            return "Name of the project. Required field that identifies the project.";
        }
        if (headerLower.contains("project description")) {
            return "Detailed description of the project. Required field that provides context and objectives.";
        }
        if (headerLower.contains("start date")) {
            return "Project start date. Format: dd/mm/yyyy. Required field.";
        }
        if (headerLower.contains("end date")) {
            return "Project end date. Format: dd/mm/yyyy. Required field.";
        }
        if (headerLower.contains("parent project")) {
            return "Parent project name. Used to establish hierarchical relationships between projects.";
        }
        if (headerLower.equals("rag")) {
            return "RAG (Red, Amber, Green) status indicator. Required field that shows project status. Select a value from the dropdown list.";
        }
        if (headerLower.contains("project lifecycle")) {
            return "Project lifecycle stage. Required field. Select a value from the dropdown list to indicate the current phase.";
        }
        if (headerLower.contains("project type")) {
            return "Type of project. Required field. Select a value from the dropdown list to categorize the project.";
        }
        return null;
    }

    private static final String IDENTITY_GROUP_MSG = "Optional. If you provide any of these identity columns, you must provide all of them together; otherwise the row will be rejected.";
    
    private String getCommitteeColumnDescription(String headerLower, String templateType) {
        if ("UPDATE".equals(templateType) && (headerLower.contains("committee id") || headerLower.equals("reference") || headerLower.contains("committee name"))) {
            return IDENTITY_GROUP_MSG;
        }
        if (headerLower.contains("committee name")) {
            return "Name of the committee. Identifies the committee.";
        }
        if (headerLower.contains("parent committee")) {
            return "Parent committee name. Used to establish hierarchical relationships between committees.";
        }
        if (headerLower.contains("committee type")) {
            return "Type of committee. Select a value from the dropdown list to categorize the committee.";
        }
        return null;
    }

    private String getBusinessAreaColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("business area name")) {
            return "Name of the business area. Required field that identifies the business area.";
        }
        if (headerLower.contains("parent business area")) {
            return "Parent business area name. Used to establish hierarchical relationships.";
        }
        return null;
    }

    private String getCapabilityColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("capability name")) {
            return "Name of the capability. Required field that identifies the capability.";
        }
        if (headerLower.contains("capability definition")) {
            return "Definition of the capability. Required field that describes what the capability represents.";
        }
        if (headerLower.contains("parent capability")) {
            return "Parent capability name. Used to establish hierarchical relationships between capabilities.";
        }
        if (headerLower.contains("capability type")) {
            return "Type of capability. Required field. Select a value from the dropdown list.";
        }
        return null;
    }

    private String getClientColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("client name")) {
            return "Name of the client.";
        }
        if (headerLower.contains("client type")) {
            return "Type of client. Select a value from the dropdown list.";
        }
        return null;
    }

    private String getLegalColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("legal name")) {
            return "Name of the legal entity or legal document.";
        }
        if (headerLower.contains("legal type")) {
            return "Type of legal entity or document. Select a value from the dropdown list.";
        }
        return null;
    }

    private String getOrgUnitColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("org unit name") || headerLower.contains("orgunit name") || headerLower.contains("organizational unit name")) {
            return "Name of the organizational unit.";
        }
        if (headerLower.contains("org unit reference") || headerLower.contains("orgunit reference")) {
            return "Reference code for the organizational unit.";
        }
        if (headerLower.contains("parent org unit") || headerLower.contains("parent orgunit")) {
            return "Parent organizational unit name. Used to establish hierarchical relationships.";
        }
        return null;
    }

    private String getPeopleColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("first name")) {
            return "First name of the person. Required field.";
        }
        if (headerLower.contains("last name")) {
            return "Last name of the person. Required field.";
        }
        if (headerLower.contains("email") && !headerLower.contains("user")) {
            return "Email address of the person. Required field.";
        }
        if (headerLower.contains("password")) {
            return "Password for the user account. Optional field for user authentication.";
        }
        if (headerLower.contains("function")) {
            return "Job function or role of the person.";
        }
        if (headerLower.contains("function description")) {
            return "Description of the person's function or role.";
        }
        if (headerLower.contains("profile")) {
            return "User profile type. Required field. Select a value from the dropdown list.";
        }
        if (headerLower.contains("office location")) {
            return "Physical office location of the person.";
        }
        if (headerLower.contains("internal mail code")) {
            return "Internal mail code for the person's location.";
        }
        if (headerLower.contains("office telephone")) {
            return "Office telephone number.";
        }
        if (headerLower.contains("mobile") || headerLower.contains("cell")) {
            return "Mobile or cell phone number.";
        }
        if (headerLower.contains("lan id") || headerLower.contains("lanid")) {
            return "LAN ID (Local Area Network ID) of the person.";
        }
        if (headerLower.contains("employment type")) {
            return "Type of employment. Required field. Select a value from the dropdown list.";
        }
        return null;
    }

    private String getProductColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("product name")) {
            return "Name of the product. Required field.";
        }
        if (headerLower.contains("product description")) {
            return "Description of the product.";
        }
        if (headerLower.contains("product type")) {
            return "Type of product. Select a value from the dropdown list.";
        }
        if (headerLower.contains("parent") && headerLower.contains("name") && headerLower.contains("product")) {
            return "Parent product name. Used to establish hierarchical relationships.";
        }
        return null;
    }

    private String getSystemColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("asset id")) {
            return "Asset ID associated with the system. Used for asset management integration.";
        }
        if (headerLower.equals("external")) {
            return "Indicates if the system is external (TRUE) or internal (FALSE). Required field. Select from dropdown.";
        }
        if (headerLower.contains("url")) {
            return "URL or web address associated with the system.";
        }
        if (headerLower.contains("dqautomation")) {
            return "Data Quality automation setting. Select TRUE or FALSE from the dropdown.";
        }
        if (headerLower.contains("parent short name")) {
            return "Short name of the parent system. Used to establish hierarchical relationships.";
        }
        if (headerLower.contains("type") && !headerLower.contains("parent")) {
            return "System type. Required field. Select a value from the dropdown list.";
        }
        if (headerLower.equals("confidentiality")) {
            return "Confidentiality rating (CIA). Select a value from the dropdown list. Part of the CIA security rating.";
        }
        if (headerLower.equals("integrity")) {
            return "Integrity rating (CIA). Select a value from the dropdown list. Part of the CIA security rating.";
        }
        if (headerLower.equals("availability")) {
            return "Availability rating (CIA). Select a value from the dropdown list. Part of the CIA security rating.";
        }
        return null;
    }

    private String getDatasetColumnDescription(String headerLower, String templateType) {
        if (headerLower.equals("ref.") || headerLower.equals("ref")) {
            return "Reference code for the dataset.";
        }
        if (headerLower.equals("name")) {
            return "Name of the dataset. Required field.";
        }
        if (headerLower.equals("definition")) {
            return "Definition of the dataset. Required field that describes what the dataset contains.";
        }
        if (headerLower.equals("usage")) {
            return "Usage description of the dataset. Required field that explains how the dataset is used.";
        }
        if (headerLower.contains("type") && !headerLower.contains("dataset")) {
            return "Dataset type. Required field. Select a value from the dropdown list.";
        }
        if (headerLower.contains("system id")) {
            return "ID of the system that contains or manages this dataset.";
        }
        if (headerLower.contains("system short name")) {
            return "Short name of the system associated with this dataset.";
        }
        if (headerLower.contains("parent system short name")) {
            return "Short name of the parent system. Used for hierarchical relationships.";
        }
        if (headerLower.contains("glossary ref") || headerLower.contains("glossary ref.")) {
            return "Reference code of the related glossary term.";
        }
        if (headerLower.contains("glossary name")) {
            return "Name of the related glossary term.";
        }
        if (headerLower.contains("parent glossary name")) {
            return "Parent glossary term name. Used for hierarchical relationships.";
        }
        return null;
    }

    private String getAttributeColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("attribute name")) {
            return "Name of the attribute. Required field.";
        }
        if (headerLower.contains("attribute definition")) {
            return "Definition of the attribute. Required field that describes what the attribute represents.";
        }
        if (headerLower.equals("key")) {
            return "Indicates if this is a key attribute. Select TRUE or FALSE from the dropdown.";
        }
        if (headerLower.contains("business logic")) {
            return "Business logic or rules associated with this attribute.";
        }
        if (headerLower.contains("data length")) {
            return "Maximum data length allowed for this attribute.";
        }
        if (headerLower.contains("data type")) {
            return "Data type of the attribute (e.g., String, Integer, Date). Select from dropdown.";
        }
        if (headerLower.contains("attribute requirement")) {
            return "Requirement level for the attribute. Select a value from the dropdown list.";
        }
        if (headerLower.contains("data set ref") || headerLower.contains("data set ref.")) {
            return "Reference code of the dataset this attribute belongs to.";
        }
        if (headerLower.contains("data set name")) {
            return "Name of the dataset this attribute belongs to.";
        }
        if (headerLower.contains("system short name")) {
            return "Short name of the system associated with this attribute.";
        }
        if (headerLower.contains("glossary ref") || headerLower.contains("glossary ref.")) {
            return "Reference code of the related glossary term.";
        }
        if (headerLower.contains("glossary name")) {
            return "Name of the related glossary term.";
        }
        if (headerLower.contains("parent glossary name")) {
            return "Parent glossary term name. Used for hierarchical relationships.";
        }
        if (headerLower.contains("origin")) {
            return "Origin or source of the attribute.";
        }
        if (headerLower.contains("editability")) {
            return "Editability setting. Select a value from the dropdown list.";
        }
        if (headerLower.contains("editability role")) {
            return "Role required for editing this attribute.";
        }
        if (headerLower.contains("db field name")) {
            return "Database field name corresponding to this attribute.";
        }
        return null;
    }

    private String getInterfaceColumnDescription(String headerLower, String templateType) {
        if (headerLower.contains("interface name")) {
            return "Name of the interface. Required field.";
        }
        if (headerLower.contains("asset id")) {
            return "Asset ID associated with the interface.";
        }
        if (headerLower.contains("synchronisation control") || headerLower.contains("synchronization control")) {
            return "Synchronization control setting for the interface.";
        }
        if (headerLower.contains("interface description")) {
            return "Description of the interface. Required field.";
        }
        if (headerLower.contains("transfer method")) {
            return "Method used for data transfer. Select a value from the dropdown list.";
        }
        if (headerLower.contains("transfer format")) {
            return "Format of data transfer. Select a value from the dropdown list.";
        }
        if (headerLower.contains("interface classification")) {
            return "Classification of the interface. Select a value from the dropdown list.";
        }
        if (headerLower.contains("source system short name")) {
            return "Short name of the source system. Required field.";
        }
        if (headerLower.contains("target system short name")) {
            return "Short name of the target system. Required field.";
        }
        if (headerLower.contains("automation level")) {
            return "Level of automation for the interface. Required field. Select a value from the dropdown list.";
        }
        if (headerLower.contains("frequency")) {
            return "Frequency of data transfer or synchronization. Select a value from the dropdown list.";
        }
        return null;
    }

    private String getGlossaryColumnDescription(String headerLower, String templateType) {
        if (headerLower.equals("name")) {
            return "Name of the glossary term. Required field.";
        }
        if (headerLower.equals("definition")) {
            return "Definition of the glossary term. Required field.";
        }
        if (headerLower.equals("ref.") || headerLower.equals("ref")) {
            return "Reference code for the glossary term.";
        }
        if (headerLower.contains("examples")) {
            return "Examples of usage for the glossary term.";
        }
        if (headerLower.contains("business logic")) {
            return "Business logic or rules associated with the glossary term.";
        }
        if (headerLower.contains("format description")) {
            return "Description of the format for the glossary term.";
        }
        if (headerLower.contains("ldm reference")) {
            return "Logical Data Model (LDM) reference associated with the glossary term.";
        }
        if (headerLower.contains("parent name") && !headerLower.contains("parent glossary")) {
            return "Parent glossary term name. Used to establish hierarchical relationships.";
        }
        if (headerLower.contains("format type")) {
            return "Format type of the glossary term. Required field. Select a value from the dropdown list.";
        }
        if (headerLower.equals("kde")) {
            return "KDE (Key Data Element) type. Select a value from the dropdown list.";
        }
        if (headerLower.contains("security classification")) {
            return "Security classification level. Required field. Select a value from the dropdown list.";
        }
        if (headerLower.contains("type") && !headerLower.contains("format")) {
            return "Type of glossary term. Required field. Select a value from the dropdown list.";
        }
        if (headerLower.equals("confidentiality")) {
            return "Confidentiality rating (CIA). Select a value from the dropdown list.";
        }
        if (headerLower.equals("integrity")) {
            return "Integrity rating (CIA). Select a value from the dropdown list.";
        }
        if (headerLower.equals("availability")) {
            return "Availability rating (CIA). Select a value from the dropdown list.";
        }
        if (headerLower.contains("alias names")) {
            return "Alternative names or aliases for the glossary term.";
        }
        return null;
    }

    /**
     * Generate Regulator template
     */
    private void generateRegulatorTemplate(XSSFSheet sheet, Row headerRow, String templateType, 
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "PrimaryName", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "ShortName", true);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            
            Row exampleRow = sheet.createRow(1);
            int exampleColIndex = 0;
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "ID", false);
            createHeaderCell(headerRow, colIndex++, "PrimaryName", false);
            createHeaderCell(headerRow, colIndex++, "ShortName", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            
            Row exampleRow = sheet.createRow(1);
            int exampleColIndex = 0;
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            
        } else {
            createHeaderCell(headerRow, colIndex++, "ID", true); // Mandatory
            
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Geography template
     */
    private void generateGeographyTemplate(XSSFSheet sheet, Row headerRow, String templateType, 
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Geography Name", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Geography Definition", false);
            createHeaderCell(headerRow, colIndex++, "Parent Geography Name", false);
            
            Row exampleRow = sheet.createRow(1);
            int exampleColIndex = 0;
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Geography ID", false);
            createHeaderCell(headerRow, colIndex++, "Geography Name", false);
            createHeaderCell(headerRow, colIndex++, "Geography Definition", false);
            createHeaderCell(headerRow, colIndex++, "Parent Geography Name", false);
            
            Row exampleRow = sheet.createRow(1);
            int exampleColIndex = 0;
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            
        } else {
            createHeaderCell(headerRow, colIndex++, "Geography ID", true); // Mandatory
            
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Regulatory Theme template
     */
    private void generateRegulatoryThemeTemplate(XSSFSheet sheet, Row headerRow, String templateType, 
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        
        // Get status values if available
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Regulatory Theme Long Name", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Description", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            
            Row exampleRow = sheet.createRow(1);
            int exampleColIndex = 0;
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            
            // Add dropdown validation for status
            if (!statusValues.isEmpty()) {
                addDropdownValidation(sheet, 6, statusValues, 1, 1000); // BUDG Status (column G, index 6)
            }
            
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Regulatory Theme ID", false);
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Regulatory Theme Long Name", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false); // List
            
            Row exampleRow = sheet.createRow(1);
            int exampleColIndex = 0;
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            
            // Add dropdown validation for List fields
            if (!statusValues.isEmpty()) {
                addDropdownValidation(sheet, 7, statusValues, 1, 1000); // BUDG Status
            }
            
        } else {
            createHeaderCell(headerRow, colIndex++, "Regulatory Theme ID", true); // Mandatory
            
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Regulation template
     */
    private void generateRegulationTemplate(XSSFSheet sheet, Row headerRow, String templateType, 
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        
        // Get lookup values from database (dynamic)
        List<String> maturityValues = lookupValues.getOrDefault("regulation_maturity", new ArrayList<>());
        List<String> probabilityValues = lookupValues.getOrDefault("regulation_probability", new ArrayList<>());
        List<String> stageValues = lookupValues.getOrDefault("regulation_stage", new ArrayList<>());
        List<String> complianceLevelValues = lookupValues.getOrDefault("regulation_compliance_level", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("regulation_status", new ArrayList<>());
        List<String> legalAdviceTypeValues = lookupValues.getOrDefault("legal_advice_type", new ArrayList<>());
        List<String> governanceRoleValues = getRoleValuesByModuleName("Regulation");
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Regulation Long Name", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Description", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Additional Info", false);
            int pubDateCol = colIndex;
            createHeaderCell(headerRow, colIndex++, "Publication Date", true); // Mandatory
            addCellComment(sheet, headerRow, pubDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            int commentsDateCol = colIndex;
            createHeaderCell(headerRow, colIndex++, "Comments Date", false);
            addCellComment(sheet, headerRow, commentsDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            int finalDateCol = colIndex;
            createHeaderCell(headerRow, colIndex++, "Finalisation Date", false);
            addCellComment(sheet, headerRow, finalDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            int compDateCol = colIndex;
            createHeaderCell(headerRow, colIndex++, "Compliance Date", true); // Mandatory
            addCellComment(sheet, headerRow, compDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            createHeaderCell(headerRow, colIndex++, "Legal Advice", false);
            createHeaderCell(headerRow, colIndex++, "Parent Regulation Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Regulation Maturity", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Regulation Probability", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Legal Advice Type", false);
            createHeaderCell(headerRow, colIndex++, "Regulation Stage", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Compliance Level", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 25; i++) createCell(exampleRow, i, "");
            
            // Add dropdown validation
            if (!maturityValues.isEmpty()) {
                addDropdownValidation(sheet, 12, maturityValues, 1, 1000); // Regulation Maturity
            }
            if (!probabilityValues.isEmpty()) {
                addDropdownValidation(sheet, 13, probabilityValues, 1, 1000); // Regulation Probability
            }
            if (!statusValues.isEmpty()) {
                addDropdownValidation(sheet, 14, statusValues, 1, 1000); // BUDG Status
            }
            if (!legalAdviceTypeValues.isEmpty()) {
                addDropdownValidation(sheet, 15, legalAdviceTypeValues, 1, 1000); // Legal Advice Type
            }
            if (!stageValues.isEmpty()) {
                addDropdownValidation(sheet, 16, stageValues, 1, 1000); // Regulation Stage
            }
            if (!complianceLevelValues.isEmpty()) {
                addDropdownValidation(sheet, 17, complianceLevelValues, 1, 1000); // Compliance Level
            }
            if (!governanceRoleValues.isEmpty()) {
                addDropdownValidation(sheet, 22, governanceRoleValues, 1, 1000); // Governance Role
            }
            
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Regulation ID", false);
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Regulation Long Name", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Additional Info", false);
            int pubDateCol = colIndex;
            createHeaderCell(headerRow, colIndex++, "Publication Date", false);
            addCellComment(sheet, headerRow, pubDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            int commentsDateCol = colIndex;
            createHeaderCell(headerRow, colIndex++, "Comments Date", false);
            addCellComment(sheet, headerRow, commentsDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            int finalDateCol = colIndex;
            createHeaderCell(headerRow, colIndex++, "Finalisation Date", false);
            addCellComment(sheet, headerRow, finalDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            int compDateCol = colIndex;
            createHeaderCell(headerRow, colIndex++, "Compliance Date", false);
            addCellComment(sheet, headerRow, compDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            createHeaderCell(headerRow, colIndex++, "Legal Advice", false);
            createHeaderCell(headerRow, colIndex++, "Parent Regulation Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Regulation Maturity", false);
            createHeaderCell(headerRow, colIndex++, "Regulation Probability", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Legal Advice Type", false);
            createHeaderCell(headerRow, colIndex++, "Regulation Stage", false);
            createHeaderCell(headerRow, colIndex++, "Compliance Level", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 19; i++) createCell(exampleRow, i, "");
            
            // Add dropdown validation for List fields
            if (!maturityValues.isEmpty()) {
                addDropdownValidation(sheet, 13, maturityValues, 1, 1000); // Regulation Maturity
            }
            if (!probabilityValues.isEmpty()) {
                addDropdownValidation(sheet, 14, probabilityValues, 1, 1000); // Regulation Probability
            }
            if (!statusValues.isEmpty()) {
                addDropdownValidation(sheet, 15, statusValues, 1, 1000); // BUDG Status
            }
            if (!legalAdviceTypeValues.isEmpty()) {
                addDropdownValidation(sheet, 16, legalAdviceTypeValues, 1, 1000); // Legal Advice Type
            }
            if (!stageValues.isEmpty()) {
                addDropdownValidation(sheet, 17, stageValues, 1, 1000); // Regulation Stage
            }
            if (!complianceLevelValues.isEmpty()) {
                addDropdownValidation(sheet, 18, complianceLevelValues, 1, 1000); // Compliance Level
            }
            
        } else {
            createHeaderCell(headerRow, colIndex++, "Regulation ID", true); // Mandatory
            
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Policy template
     */
    private void generatePolicyTemplate(XSSFSheet sheet, Row headerRow, String templateType, 
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        
        // Get lookup values from database (dynamic)
        List<String> typeValues = lookupValues.getOrDefault("policy_type", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("policy_lifecycle_status", new ArrayList<>());
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> governanceRoleValues = getRoleValuesForPolicy();
        
        // Get default role value (first option from list)
        String defaultRole = governanceRoleValues.isEmpty() ? "" : governanceRoleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Name", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Internal", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "URL", false);
            createHeaderCell(headerRow, colIndex++, "Description", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Effective Date", false);
            createHeaderCell(headerRow, colIndex++, "End Date", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Type", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            int exampleColIndex = 0;
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "true");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, defaultRole); // Governance Role (default: first option from Policy module roles)
            
            // Add dropdown validation
            if (!viewingValues.isEmpty()) {
                addDropdownValidation(sheet, 9, viewingValues, 1, 1000); // BUDG Viewing (column J, index 9)
            }
            if (!statusValues.isEmpty()) {
                addDropdownValidation(sheet, 10, statusValues, 1, 1000); // BUDG Status (column K, index 10)
            }
            if (!lifecycleValues.isEmpty()) {
                addDropdownValidation(sheet, 11, lifecycleValues, 1, 1000); // Lifecycle (column L, index 11)
            }
            if (!typeValues.isEmpty()) {
                addDropdownValidation(sheet, 12, typeValues, 1, 1000); // Type (column M, index 12)
            }
            if (!governanceRoleValues.isEmpty()) {
                addDropdownValidation(sheet, 17, governanceRoleValues, 1, 1000); // Governance Role (column R, index 17)
            }
            
            // Add dropdown validation for Internal column
            List<String> internalValues = Arrays.asList("true", "false");
            addDropdownValidation(sheet, 2, internalValues, 1, 1000); // Internal (column C, index 2)
            
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "ID", false);
            createHeaderCell(headerRow, colIndex++, "Name", false);
            createHeaderCell(headerRow, colIndex++, "Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Internal", false);
            createHeaderCell(headerRow, colIndex++, "URL", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Effective Date", false);
            createHeaderCell(headerRow, colIndex++, "End Date", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "Type", false);
            
            Row exampleRow = sheet.createRow(1);
            int exampleColIndex = 0;
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "true");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            createCell(exampleRow, exampleColIndex++, "");
            
            // Add dropdown validation for List fields
            List<String> internalValues = Arrays.asList("true", "false");
            addDropdownValidation(sheet, 3, internalValues, 1, 1000); // Internal
            if (!viewingValues.isEmpty()) {
                addDropdownValidation(sheet, 10, viewingValues, 1, 1000); // BUDG Viewing
            }
            if (!statusValues.isEmpty()) {
                addDropdownValidation(sheet, 11, statusValues, 1, 1000); // BUDG Status
            }
            if (!lifecycleValues.isEmpty()) {
                addDropdownValidation(sheet, 12, lifecycleValues, 1, 1000); // Lifecycle
            }
            if (!typeValues.isEmpty()) {
                addDropdownValidation(sheet, 13, typeValues, 1, 1000); // Type
            }
            
        } else {
            createHeaderCell(headerRow, colIndex++, "ID", true); // Mandatory
            
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Process template
     */
    private void generateProcessTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) throws SQLException {
        int colIndex = 0;
        
        // Lookup lists for Process
        List<String> stepTypeValues = lookupValues.getOrDefault("process_step_type", new ArrayList<>());
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> typeValues = lookupValues.getOrDefault("process_type", new ArrayList<>());
        List<String> durationTypeValues = lookupValues.getOrDefault("process_duration_type", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("process_lifecycle_status", new ArrayList<>());
        List<String> classificationValues = lookupValues.getOrDefault("process_class", new ArrayList<>());
        List<String> automationValues = lookupValues.getOrDefault("process_automation", new ArrayList<>());
        List<String> governanceRoleValues = getRoleValuesForProcess();
        
        // Get default role value (first option from list)
        String defaultRole = governanceRoleValues.isEmpty() ? "" : governanceRoleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            // Required: Name, Description, Step Type, Type, Lifecycle
            createHeaderCell(headerRow, colIndex++, "Name", true);
            createHeaderCell(headerRow, colIndex++, "Ref.", true);
            createHeaderCell(headerRow, colIndex++, "Description", true);
            createHeaderCell(headerRow, colIndex++, "Input Description", false);
            createHeaderCell(headerRow, colIndex++, "Output Description", false);
            createHeaderCell(headerRow, colIndex++, "Step Type", true);
            createHeaderCell(headerRow, colIndex++, "Create Permission", false);
            createHeaderCell(headerRow, colIndex++, "Read Permission", false);
            createHeaderCell(headerRow, colIndex++, "Update Permission", false);
            createHeaderCell(headerRow, colIndex++, "Delete Permission", false);
            createHeaderCell(headerRow, colIndex++, "Archive Permission", false);
            createHeaderCell(headerRow, colIndex++, "Duration", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Type", true);
            createHeaderCell(headerRow, colIndex++, "Duration Type", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true);
            createHeaderCell(headerRow, colIndex++, "Classification", false);
            createHeaderCell(headerRow, colIndex++, "Automation", false);
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultStepType = getDefaultStepTypeValue();
            String defaultProcessType = getDefaultProcessTypeValue();
            String defaultProcessLifecycle = getDefaultProcessLifecycleValue();
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Ref auto: PRC-{ID}
            createCell(exampleRow, ex++, ""); // Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Input Description
            createCell(exampleRow, ex++, ""); // Output Description
            createCell(exampleRow, ex++, defaultStepType); // Step Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, "yes");
            createCell(exampleRow, ex++, "yes");
            createCell(exampleRow, ex++, "yes");
            createCell(exampleRow, ex++, "no");
            createCell(exampleRow, ex++, "no");
            createCell(exampleRow, ex++, ""); // Duration
            createCell(exampleRow, ex++, ""); // Parent Name
            createCell(exampleRow, ex++, ""); // Parent Ref.
            createCell(exampleRow, ex++, defaultViewing); // Viewing
            createCell(exampleRow, ex++, defaultStatus); // Status
            createCell(exampleRow, ex++, defaultProcessType); // Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Duration Type
            createCell(exampleRow, ex++, defaultProcessLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Classification
            createCell(exampleRow, ex++, ""); // Automation
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, defaultRole); // Governance Role (default: first option from Process module roles)
            
            // Add dropdown validation
            List<String> yesNoValues = java.util.Arrays.asList("yes", "no");
            if (!stepTypeValues.isEmpty()) addDropdownValidation(sheet, 5, stepTypeValues, 1, 1000); // Step Type
            addDropdownValidation(sheet, 6, yesNoValues, 1, 1000); // Create Permission
            addDropdownValidation(sheet, 7, yesNoValues, 1, 1000); // Read Permission
            addDropdownValidation(sheet, 8, yesNoValues, 1, 1000); // Update Permission
            addDropdownValidation(sheet, 9, yesNoValues, 1, 1000); // Delete Permission
            addDropdownValidation(sheet, 10, yesNoValues, 1, 1000); // Archive Permission
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 14, viewingValues, 1, 1000); // Viewing
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 15, statusValues, 1, 1000); // Status
            if (!typeValues.isEmpty()) addDropdownValidation(sheet, 16, typeValues, 1, 1000); // Type
            if (!durationTypeValues.isEmpty()) addDropdownValidation(sheet, 17, durationTypeValues, 1, 1000); // Duration Type
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 18, lifecycleValues, 1, 1000); // Lifecycle
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 19, classificationValues, 1, 1000); // Classification
            if (!automationValues.isEmpty()) addDropdownValidation(sheet, 20, automationValues, 1, 1000); // Automation
            if (!governanceRoleValues.isEmpty()) addDropdownValidation(sheet, 25, governanceRoleValues, 1, 1000); // Governance Role
            
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "ID", false);
            createHeaderCell(headerRow, colIndex++, "Name", false);
            createHeaderCell(headerRow, colIndex++, "Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Input Description", false);
            createHeaderCell(headerRow, colIndex++, "Output Description", false);
            createHeaderCell(headerRow, colIndex++, "Step Type", false);
            createHeaderCell(headerRow, colIndex++, "Create Permission", false);
            createHeaderCell(headerRow, colIndex++, "Read Permission", false);
            createHeaderCell(headerRow, colIndex++, "Update Permission", false);
            createHeaderCell(headerRow, colIndex++, "Delete Permission", false);
            createHeaderCell(headerRow, colIndex++, "Archive Permission", false);
            createHeaderCell(headerRow, colIndex++, "Duration", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Type", false);
            createHeaderCell(headerRow, colIndex++, "Duration Type", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "Classification", false);
            createHeaderCell(headerRow, colIndex++, "Automation", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultStepType = getDefaultStepTypeValue();
            String defaultProcessType = getDefaultProcessTypeValue();
            String defaultProcessLifecycle = getDefaultProcessLifecycleValue();
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // ID (mandatory)
            createCell(exampleRow, ex++, ""); // Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Ref.
            createCell(exampleRow, ex++, ""); // Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Input Description
            createCell(exampleRow, ex++, ""); // Output Description
            createCell(exampleRow, ex++, defaultStepType); // Step Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, "yes"); // Create Permission
            createCell(exampleRow, ex++, "yes"); // Read Permission
            createCell(exampleRow, ex++, "yes"); // Update Permission
            createCell(exampleRow, ex++, "no"); // Delete Permission
            createCell(exampleRow, ex++, "no"); // Archive Permission
            createCell(exampleRow, ex++, ""); // Duration
            createCell(exampleRow, ex++, ""); // Parent Name
            createCell(exampleRow, ex++, ""); // Parent Ref.
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultProcessType); // Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Duration Type
            createCell(exampleRow, ex++, defaultProcessLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Classification
            createCell(exampleRow, ex++, ""); // Automation
            
            // Dropdowns for List fields
            List<String> yesNoValues = java.util.Arrays.asList("yes", "no");
            if (!stepTypeValues.isEmpty()) addDropdownValidation(sheet, 6, stepTypeValues, 1, 1000); // Step Type
            addDropdownValidation(sheet, 7, yesNoValues, 1, 1000); // Create Permission
            addDropdownValidation(sheet, 8, yesNoValues, 1, 1000); // Read Permission
            addDropdownValidation(sheet, 9, yesNoValues, 1, 1000); // Update Permission
            addDropdownValidation(sheet, 10, yesNoValues, 1, 1000); // Delete Permission
            addDropdownValidation(sheet, 11, yesNoValues, 1, 1000); // Archive Permission
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 15, viewingValues, 1, 1000); // BUDG Viewing
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 16, statusValues, 1, 1000); // BUDG Status
            if (!typeValues.isEmpty()) addDropdownValidation(sheet, 17, typeValues, 1, 1000); // Type
            if (!durationTypeValues.isEmpty()) addDropdownValidation(sheet, 18, durationTypeValues, 1, 1000); // Duration Type
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 19, lifecycleValues, 1, 1000); // Lifecycle
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 20, classificationValues, 1, 1000); // Classification
            if (!automationValues.isEmpty()) addDropdownValidation(sheet, 21, automationValues, 1, 1000); // Automation
            
        } else {
            createHeaderCell(headerRow, colIndex++, "ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Project template
     */
    private void generateProjectTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) throws SQLException {
        int colIndex = 0;
        
        // Lookup lists for Project
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> ragValues = lookupValues.getOrDefault("project_rag", new ArrayList<>());
        List<String> classificationValues = lookupValues.getOrDefault("project_classification", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("project_lifecycle", new ArrayList<>());
        List<String> projectTypeValues = lookupValues.getOrDefault("project_comment_type", new ArrayList<>());
        List<String> governanceRoleValues = getRoleValuesForProject();
        
        // Get default role value (first option from list)
        String defaultRole = governanceRoleValues.isEmpty() ? "" : governanceRoleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            // Required: Project Name, Description, Start Date, End Date, RAG, Project Lifecycle, Project Type
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Project Name", true);
            createHeaderCell(headerRow, colIndex++, "Project Description", true);
            int startDateCol = colIndex;
            createHeaderCell(headerRow, startDateCol, "Start Date", true);
            addCellComment(sheet, headerRow, startDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            colIndex = startDateCol + 1;
            int endDateCol = colIndex;
            createHeaderCell(headerRow, endDateCol, "End Date", true);
            addCellComment(sheet, headerRow, endDateCol, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            colIndex = endDateCol + 1;
            createHeaderCell(headerRow, colIndex++, "Parent Project Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "RAG", true);
            createHeaderCell(headerRow, colIndex++, "Classification", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", true); // Mandatory - required for Project
            createHeaderCell(headerRow, colIndex++, "Project Lifecycle", true);
            createHeaderCell(headerRow, colIndex++, "Project Type", true);
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultRAG = getDefaultRAGValue();
            String defaultProjectLifecycle = getDefaultProjectLifecycleValue();
            String defaultProjectType = getDefaultProjectTypeValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Reference (auto-generated if empty)
            createCell(exampleRow, ex++, ""); // Project Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Project Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Start Date (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // End Date (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Parent Project Name
            createCell(exampleRow, ex++, ""); // Parent Ref.
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultRAG); // RAG (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Classification
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultProjectLifecycle); // Project Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultProjectType); // Project Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, defaultRole); // Governance Role (default: first option from Project module roles)
            
            // Add dropdown validation
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 7, viewingValues, 1, 1000); // BUDG Viewing
            if (!ragValues.isEmpty()) addDropdownValidation(sheet, 8, ragValues, 1, 1000); // RAG
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 9, classificationValues, 1, 1000); // Classification
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 10, statusValues, 1, 1000); // BUDG Status
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 11, lifecycleValues, 1, 1000); // Project Lifecycle
            if (!projectTypeValues.isEmpty()) addDropdownValidation(sheet, 12, projectTypeValues, 1, 1000); // Project Type
            if (!governanceRoleValues.isEmpty()) addDropdownValidation(sheet, 17, governanceRoleValues, 1, 1000); // Governance Role
            
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Project ID", false);
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Project Name", false);
            createHeaderCell(headerRow, colIndex++, "Project Description", false);
            int startDateColUpdate = colIndex;
            createHeaderCell(headerRow, startDateColUpdate, "Start Date", false);
            addCellComment(sheet, headerRow, startDateColUpdate, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            colIndex = startDateColUpdate + 1;
            int endDateColUpdate = colIndex;
            createHeaderCell(headerRow, endDateColUpdate, "End Date", false);
            addCellComment(sheet, headerRow, endDateColUpdate, "Date format: dd/mm/yyyy (e.g., 12/10/2000)");
            colIndex = endDateColUpdate + 1;
            createHeaderCell(headerRow, colIndex++, "Parent Project Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "RAG", false);
            createHeaderCell(headerRow, colIndex++, "Classification", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Project Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "Project Type", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultRAG = getDefaultRAGValue();
            String defaultProjectLifecycle = getDefaultProjectLifecycleValue();
            String defaultProjectType = getDefaultProjectTypeValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Project ID (mandatory)
            createCell(exampleRow, ex++, ""); // Reference
            createCell(exampleRow, ex++, ""); // Project Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Project Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Start Date (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // End Date (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Parent Project Name
            createCell(exampleRow, ex++, ""); // Parent Ref.
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultRAG); // RAG (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Classification
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultProjectLifecycle); // Project Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultProjectType); // Project Type (mandatory LIST - show default)
            
            // Dropdowns
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 8, viewingValues, 1, 1000); // BUDG Viewing
            if (!ragValues.isEmpty()) addDropdownValidation(sheet, 9, ragValues, 1, 1000); // RAG
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 10, classificationValues, 1, 1000); // Classification
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 11, statusValues, 1, 1000); // BUDG Status
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 12, lifecycleValues, 1, 1000); // Project Lifecycle
            if (!projectTypeValues.isEmpty()) addDropdownValidation(sheet, 13, projectTypeValues, 1, 1000); // Project Type
            
        } else {
            createHeaderCell(headerRow, colIndex++, "Project ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Get role values from object_role table for Project module
     */
    private List<String> getRoleValuesForProject() {
        return getRoleValuesByModuleName("Project");
    }
    
    /**
     * Get role values from object_role table for Policy module
     */
    private List<String> getRoleValuesForPolicy() {
        return getRoleValuesByModuleName("Policy");
    }
    
    /**
     * Get entity configuration
     */
    private EntityConfig getEntityConfig(String entity) {
        EntityConfig config = new EntityConfig();
        
        if ("regulatorytheme".equals(entity) || "regulatory_theme".equals(entity)) {
            config.addLookupTable("status");
        } else if ("regulation".equals(entity)) {
            config.addLookupTable("regulation_maturity");
            config.addLookupTable("regulation_probability");
            config.addLookupTable("regulation_stage");
            config.addLookupTable("regulation_compliance_level");
            config.addLookupTable("regulation_status");
            config.addLookupTable("legal_advice_type");
        } else if ("policy".equals(entity)) {
            config.addLookupTable("policy_type");
            config.addLookupTable("policy_lifecycle_status");
            config.addLookupTable("viewing");
            config.addLookupTable("status");
        } else if ("process".equals(entity)) {
            config.addLookupTable("process_step_type");
            config.addLookupTable("viewing");
            config.addLookupTable("status");
            config.addLookupTable("process_type");
            config.addLookupTable("process_duration_type");
            config.addLookupTable("process_lifecycle_status");
            config.addLookupTable("process_class");
            config.addLookupTable("process_automation");
        } else if ("project".equals(entity)) {
            config.addLookupTable("viewing");
            config.addLookupTable("project_rag");
            config.addLookupTable("project_classification");
            config.addLookupTable("status");
            config.addLookupTable("project_lifecycle");
            config.addLookupTable("project_comment_type");
        } else if ("committee".equals(entity)) {
            config.addLookupTable("viewing");
            config.addLookupTable("committee_classification");
            config.addLookupTable("status");
            config.addLookupTable("committee_lifecycle");
            config.addLookupTable("committee_type");
        } else if ("businessarea".equals(entity) || "business_area".equals(entity)) {
            config.addLookupTable("viewing");
            config.addLookupTable("status");
            config.addLookupTable("business_area_lifecycle");
        } else if ("capability".equals(entity)) {
            config.addLookupTable("viewing");
            config.addLookupTable("status");
            config.addLookupTable("capability_classification");
            config.addLookupTable("capability_lifecyle");
            config.addLookupTable("capability_type");
        } else if ("client".equals(entity)) {
            config.addLookupTable("status");
            config.addLookupTable("client_lifecycle");
            config.addLookupTable("viewing");
        } else if ("legal".equals(entity)) {
            config.addLookupTable("status");
            config.addLookupTable("viewing");
        } else if ("orgunit".equals(entity) || "org_unit".equals(entity)) {
            config.addLookupTable("status");
        } else if ("people".equals(entity)) {
            config.addLookupTable("status");
            config.addLookupTable("employment_type");
            config.addLookupTable("people_lifecycle_status");
            config.addLookupTable("role");
        } else if ("product".equals(entity)) {
            config.addLookupTable("status");
            config.addLookupTable("product_lifecycle");
            config.addLookupTable("viewing");
        } else if ("glossary".equals(entity)) {
            config.addLookupTable("viewing");
            config.addLookupTable("status");
            config.addLookupTable("glossary_lifecycle");
            config.addLookupTable("glossary_format_type");
            config.addLookupTable("glossary_kde_type");
            config.addLookupTable("security_classification");
            config.addLookupTable("glossary_type");
            config.addLookupTable("cia_rating");
        } else if ("system".equals(entity)) {
            config.addLookupTable("viewing");
            config.addLookupTable("status");
            config.addLookupTable("system_lifecycle");
            config.addLookupTable("system_type");
            config.addLookupTable("system_classification");
            config.addLookupTable("cia_rating");
            config.addLookupTable("object_role");
        } else if ("dataset".equals(entity)) {
            config.addLookupTable("viewing");
            config.addLookupTable("status");
            config.addLookupTable("dataset_lifecycle");
            config.addLookupTable("dataset_type");
        } else if ("attribute".equals(entity)) {
            config.addLookupTable("attribute_datatype");
            config.addLookupTable("requirement");
            config.addLookupTable("attribute_origination");
            config.addLookupTable("attribute_editability");
            config.addLookupTable("attribute_edit_role");
            // object_role is fetched via getRoleValuesByModuleName("Attribute") dynamically
        } else if ("interface".equals(entity)) {
            config.addLookupTable("interface_transfer");  // Transfer Method
            config.addLookupTable("interface_transfer_format");  // Transfer Format
            config.addLookupTable("interface_classification");
            config.addLookupTable("interface_lifecycle");
            config.addLookupTable("status");
            config.addLookupTable("interface_automation");  // Automation Level
            config.addLookupTable("interface_frequency");
            config.addLookupTable("viewing");
            config.addLookupTable("object_role");
        }
        // Add more entity-specific configurations here
        
        return config;
    }
    
    /**
     * Returns true if the given user is an admin (Super Admin or role name "Admin").
     * Used to restrict Profile dropdown to Admin and Web User when an admin downloads the People template.
     */
    private boolean isAdmin(int userId) {
        try {
            if (SegmentAccessService.isSuperAdmin(userId)) {
                return true;
            }
            String role = SegmentAccessService.getUserRole(userId);
            return role != null && "admin".equalsIgnoreCase(role.trim());
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Get lookup values from database table
     */
    private List<String> getLookupValues(String tableName) {
        List<String> values = new ArrayList<>();
        
        // Handle different table name formats
        String sql;
        String columnName;
        
        if ("status".equals(tableName)) {
            sql = "SELECT primaryname FROM status ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("viewing".equals(tableName)) {
            sql = "SELECT Name FROM viewing ORDER BY id ASC";
            columnName = "Name";
        } else if ("role".equals(tableName)) {
            sql = "SELECT primaryname FROM role ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("employment_type".equals(tableName) || "people_lifecycle_status".equals(tableName)) {
            // Handle employment_type and people_lifecycle_status with primary_Name column
            sql = "SELECT primary_Name FROM " + tableName + " ORDER BY id ASC";
            columnName = "primary_Name";
        } else if (tableName.startsWith("interface_")) {
            // All interface_* tables use Name column
            sql = "SELECT Name FROM " + tableName + " ORDER BY id ASC";
            columnName = "Name";
        } else if ("glossary_lifecycle".equals(tableName)) {
            sql = "SELECT Name FROM glossary_lifecycle ORDER BY ID ASC";
            columnName = "Name";
        } else if ("glossary_format_type".equals(tableName)) {
            sql = "SELECT Name FROM glossary_format_type ORDER BY ID ASC";
            columnName = "Name";
        } else if ("glossary_type".equals(tableName)) {
            sql = "SELECT Name FROM glossary_type ORDER BY ID ASC";
            columnName = "Name";
        } else if ("glossary_kde_type".equals(tableName)) {
            sql = "SELECT Name FROM glossary_kde_type ORDER BY ID ASC";
            columnName = "Name";
        } else if ("security_classification".equals(tableName)) {
            sql = "SELECT Name FROM security_classification ORDER BY ID ASC";
            columnName = "Name";
        } else if ("cia_rating".equals(tableName)) {
            sql = "SELECT `Values` FROM cia_rating ORDER BY ID ASC";
            columnName = "Values";
        } else if ("system_lifecycle".equals(tableName)) {
            sql = "SELECT Name FROM system_lifecycle ORDER BY ID ASC";
            columnName = "Name";
        } else if ("system_type".equals(tableName)) {
            sql = "SELECT Name FROM system_type ORDER BY ID ASC";
            columnName = "Name";
        } else if ("system_classification".equals(tableName)) {
            sql = "SELECT Name FROM system_classification ORDER BY ID ASC";
            columnName = "Name";
        } else if ("process_lifecycle_status".equals(tableName)) {
            sql = "SELECT primaryname FROM process_lifecycle_status ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("product_lifecycle".equals(tableName)) {
            sql = "SELECT primaryname FROM product_lifecycle ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("project_lifecycle".equals(tableName)) {
            sql = "SELECT primaryname FROM project_lifecycle ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("project_classification".equals(tableName)) {
            sql = "SELECT primaryname FROM project_classification ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("project_type".equals(tableName)) {
            sql = "SELECT primaryname FROM project_type ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("project_comment_type".equals(tableName)) {
            sql = "SELECT primaryname FROM project_comment_type ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("project_rag".equals(tableName)) {
            sql = "SELECT primaryname FROM project_rag ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("object_role".equals(tableName)) {
            // object_role should not be fetched via getLookupValues
            // It should use getRoleValues(moduleId) instead
            // This is a fallback that returns empty list to prevent incorrect data
            logger.warn("getLookupValues called for object_role - this should use getRoleValues(moduleId) instead");
            return new ArrayList<>(); // Return empty list - should use getRoleValues(moduleId) instead
        } else {
            sql = "SELECT PrimaryName FROM " + tableName + " ORDER BY ID ASC";
            columnName = "PrimaryName";
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                String primaryName = rs.getString(columnName);
                if (primaryName != null && !primaryName.trim().isEmpty()) {
                    values.add(primaryName.trim());
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting lookup values from table {}: {}", tableName, e.getMessage());
        }
        
        return values;
    }
    
    /**
     * Get role values from object_role table for a specific module
     */
    @SuppressWarnings("unused")
    private List<String> getRoleValues(int moduleId) {
        List<String> values = new ArrayList<>();
        String sql = "SELECT primaryname FROM object_role WHERE module = ? ORDER BY id ASC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, moduleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String primaryName = rs.getString("primaryname");
                    if (primaryName != null && !primaryName.trim().isEmpty()) {
                        values.add(primaryName.trim());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting role values from object_role for module {}: {}", moduleId, e.getMessage());
        }
        
        return values;
    }
    
    /**
     * Get role values by module name (supports various name formats: with/without underscores, with spaces, etc.)
     * Examples: "Client", "Legal Entity", "Legal_Entity", "legal_entity", "LegalEntity"
     */
    private List<String> getRoleValuesByModuleName(String moduleName) {
        List<String> values = new ArrayList<>();
        
        if (moduleName == null || moduleName.trim().isEmpty()) {
            logger.warn("Module name is null or empty, returning empty role list");
            return values;
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Normalize module name: remove spaces, underscores, convert to lowercase for comparison
            String normalizedName = moduleName.trim()
                .replaceAll("\\s+", " ")  // Normalize multiple spaces to single space
                .replaceAll("_", " ")     // Replace underscores with spaces
                .trim();
            
            // Try multiple query patterns to handle different name formats
            // Pattern 1: Exact match (case-insensitive)
            String moduleQuery = "SELECT id FROM module WHERE LOWER(TRIM(primaryname)) = LOWER(?) LIMIT 1";
            
            // Also try with spaces/underscores normalized
            String moduleQueryNormalized = "SELECT id FROM module WHERE " +
                "LOWER(REPLACE(REPLACE(TRIM(primaryname), '_', ' '), '  ', ' ')) = LOWER(?) LIMIT 1";
            
            Integer moduleId = null;
            
            // Try exact match first
            try (PreparedStatement moduleStmt = conn.prepareStatement(moduleQuery)) {
                moduleStmt.setString(1, normalizedName);
                try (ResultSet moduleRs = moduleStmt.executeQuery()) {
                    if (moduleRs.next()) {
                        moduleId = moduleRs.getInt("id");
                    }
                }
            }
            
            // If not found, try normalized match
            if (moduleId == null) {
                try (PreparedStatement moduleStmt = conn.prepareStatement(moduleQueryNormalized)) {
                    moduleStmt.setString(1, normalizedName);
                    try (ResultSet moduleRs = moduleStmt.executeQuery()) {
                        if (moduleRs.next()) {
                            moduleId = moduleRs.getInt("id");
                        }
                    }
                }
            }
            
            if (moduleId != null) {
                // Get roles for the module
                String roleQuery = "SELECT primaryname FROM object_role WHERE module = ? ORDER BY id ASC";
                try (PreparedStatement roleStmt = conn.prepareStatement(roleQuery)) {
                    roleStmt.setInt(1, moduleId);
                    try (ResultSet roleRs = roleStmt.executeQuery()) {
                        while (roleRs.next()) {
                            String primaryName = roleRs.getString("primaryname");
                            if (primaryName != null && !primaryName.trim().isEmpty()) {
                                values.add(primaryName.trim());
                            }
                        }
                    }
                }
                logger.debug("Found {} roles for module '{}' (ID: {})", values.size(), moduleName, moduleId);
            } else {
                logger.warn("Module '{}' not found in database", moduleName);
            }
        } catch (SQLException e) {
            logger.error("Error getting role values for module '{}': {}", moduleName, e.getMessage(), e);
        }
        
        return values;
    }
    
    /**
     * Get role values from object_role table for Process module
     */
    private List<String> getRoleValuesForProcess() {
        return getRoleValuesByModuleName("Process");
    }
    
    /**
     * Get governance role values for Committee
     */
    private List<String> getRoleValuesForCommittee() {
        List<String> values = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get Committee module ID
            String moduleQuery = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER('Committee') LIMIT 1";
            try (PreparedStatement moduleStmt = conn.prepareStatement(moduleQuery);
                 ResultSet moduleRs = moduleStmt.executeQuery()) {
                if (moduleRs.next()) {
                    int moduleId = moduleRs.getInt("id");
                    String roleQuery = "SELECT primaryname FROM object_role WHERE module = ? ORDER BY id ASC";
                    try (PreparedStatement roleStmt = conn.prepareStatement(roleQuery)) {
                        roleStmt.setInt(1, moduleId);
                        try (ResultSet roleRs = roleStmt.executeQuery()) {
                            while (roleRs.next()) {
                                String primaryName = roleRs.getString("primaryname");
                                if (primaryName != null && !primaryName.trim().isEmpty()) {
                                    values.add(primaryName.trim());
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting role values for Committee module: {}", e.getMessage());
        }
        return values;
    }
    
    /**
     * Get role values from object_role table for Interface module
     */
    private List<String> getRoleValuesForInterface() {
        return getRoleValuesByModuleName("Interface");
    }
    
    /**
     * Get default status value (Active) from status table
     * Returns "Active" if found, otherwise returns the first status value
     */
    private String getDefaultStatusValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First try to find "Active" (case-insensitive)
            String sql = "SELECT primaryname FROM status WHERE LOWER(primaryname) LIKE '%active%' ORDER BY id ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("primaryname");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
            
            // Fallback: get first status value
            String fallbackSql = "SELECT primaryname FROM status ORDER BY id ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(fallbackSql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("primaryname");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default status value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default viewing value (Public) from viewing table
     * Returns "Public" if found, otherwise returns the first viewing value
     */
    private String getDefaultViewingValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First try to find "Public" (case-insensitive)
            String sql = "SELECT Name FROM viewing WHERE LOWER(Name) LIKE '%public%' ORDER BY id ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("Name");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
            
            // Fallback: get first viewing value
            String fallbackSql = "SELECT Name FROM viewing ORDER BY id ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(fallbackSql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("Name");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default viewing value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default classification value from committee_classification table
     * Returns the first value ordered by ID
     */
    private String getDefaultClassificationValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM committee_classification ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default classification value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default lifecycle value from committee_lifecycle table
     * Returns the first value ordered by ID
     */
    private String getDefaultLifecycleValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM committee_lifecycle ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default lifecycle value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default committee type value from committee_type table
     * Returns the first value ordered by ID
     */
    private String getDefaultCommitteeTypeValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM committee_type ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default committee type value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default step type value from process_step_type table
     * Returns the first value ordered by ID
     */
    private String getDefaultStepTypeValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM process_step_type ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default step type value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default process type value from process_type table
     * Returns the first value ordered by ID
     */
    private String getDefaultProcessTypeValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM process_type ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default process type value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default process lifecycle value from process_lifecycle_status table
     * Returns the first value ordered by ID
     */
    private String getDefaultProcessLifecycleValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM process_lifecycle_status ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default process lifecycle value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default RAG value from project_rag table
     * Returns the first value ordered by ID
     */
    private String getDefaultRAGValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM project_rag ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default RAG value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default project lifecycle value from project_lifecycle table
     * Returns the first value ordered by ID
     */
    private String getDefaultProjectLifecycleValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM project_lifecycle ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default project lifecycle value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default project type value from project_comment_type table
     * Returns the first value ordered by ID
     */
    private String getDefaultProjectTypeValue() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT PrimaryName FROM project_comment_type ORDER BY ID ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("PrimaryName");
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default project type value: {}", e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default value from a lookup table by table name
     * Generic helper method for getting first value from any lookup table
     */
    private String getDefaultLookupValue(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql;
            String columnName;
            
            // Use same column logic as getLookupValues()
            if ("status".equals(tableName)) {
                sql = "SELECT primaryname FROM status ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else if ("viewing".equals(tableName)) {
                sql = "SELECT Name FROM viewing ORDER BY id ASC LIMIT 1";
                columnName = "Name";
            } else if ("role".equals(tableName)) {
                sql = "SELECT primaryname FROM role ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else if ("employment_type".equals(tableName) || "people_lifecycle_status".equals(tableName)) {
                sql = "SELECT primary_Name FROM " + tableName + " ORDER BY id ASC LIMIT 1";
                columnName = "primary_Name";
            } else if (tableName.startsWith("interface_")) {
                sql = "SELECT Name FROM " + tableName + " ORDER BY id ASC LIMIT 1";
                columnName = "Name";
            } else if ("glossary_lifecycle".equals(tableName)) {
                sql = "SELECT Name FROM glossary_lifecycle ORDER BY ID ASC LIMIT 1";
                columnName = "Name";
            } else if ("glossary_format_type".equals(tableName)) {
                sql = "SELECT Name FROM glossary_format_type ORDER BY ID ASC LIMIT 1";
                columnName = "Name";
            } else if ("glossary_type".equals(tableName)) {
                sql = "SELECT Name FROM glossary_type ORDER BY ID ASC LIMIT 1";
                columnName = "Name";
            } else if ("glossary_kde_type".equals(tableName)) {
                sql = "SELECT Name FROM glossary_kde_type ORDER BY ID ASC LIMIT 1";
                columnName = "Name";
            } else if ("security_classification".equals(tableName)) {
                sql = "SELECT Name FROM security_classification ORDER BY ID ASC LIMIT 1";
                columnName = "Name";
            } else if ("cia_rating".equals(tableName)) {
                sql = "SELECT `Values` FROM cia_rating ORDER BY ID ASC LIMIT 1";
                columnName = "Values";
            } else if ("system_lifecycle".equals(tableName)) {
                sql = "SELECT Name FROM system_lifecycle ORDER BY ID ASC LIMIT 1";
                columnName = "Name";
            } else if ("system_type".equals(tableName)) {
                sql = "SELECT Name FROM system_type ORDER BY ID ASC LIMIT 1";
                columnName = "Name";
            } else if ("system_classification".equals(tableName)) {
                sql = "SELECT Name FROM system_classification ORDER BY ID ASC LIMIT 1";
                columnName = "Name";
            } else if ("process_lifecycle_status".equals(tableName)) {
                sql = "SELECT primaryname FROM process_lifecycle_status ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else if ("product_lifecycle".equals(tableName)) {
                sql = "SELECT primaryname FROM product_lifecycle ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else if ("project_lifecycle".equals(tableName)) {
                sql = "SELECT primaryname FROM project_lifecycle ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else if ("project_classification".equals(tableName)) {
                sql = "SELECT primaryname FROM project_classification ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else if ("project_type".equals(tableName)) {
                sql = "SELECT primaryname FROM project_type ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else if ("project_comment_type".equals(tableName)) {
                sql = "SELECT primaryname FROM project_comment_type ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else if ("project_rag".equals(tableName)) {
                sql = "SELECT primaryname FROM project_rag ORDER BY id ASC LIMIT 1";
                columnName = "primaryname";
            } else {
                sql = "SELECT PrimaryName FROM " + tableName + " ORDER BY ID ASC LIMIT 1";
                columnName = "PrimaryName";
            }
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(columnName);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting default value from table {}: {}", tableName, e.getMessage());
        }
        return ""; // Return empty string if nothing found
    }
    
    /**
     * Get default system lifecycle value from system_lifecycle table
     */
    private String getDefaultSystemLifecycleValue() {
        return getDefaultLookupValue("system_lifecycle");
    }
    
    /**
     * Get default system type value from system_type table
     */
    private String getDefaultSystemTypeValue() {
        return getDefaultLookupValue("system_type");
    }
    
    /**
     * Get default dataset type value from dataset_type table
     */
    private String getDefaultDatasetTypeValue() {
        return getDefaultLookupValue("dataset_type");
    }
    
    /**
     * Get default dataset lifecycle value from dataset_lifecycle table
     */
    private String getDefaultDatasetLifecycleValue() {
        return getDefaultLookupValue("dataset_lifecycle");
    }
    
    /**
     * Get default interface lifecycle value from interface_lifecycle table
     */
    private String getDefaultInterfaceLifecycleValue() {
        return getDefaultLookupValue("interface_lifecycle");
    }
    
    /**
     * Get default interface automation value from interface_automation table
     */
    private String getDefaultInterfaceAutomationValue() {
        return getDefaultLookupValue("interface_automation");
    }
    
    /**
     * Get default glossary lifecycle value from glossary_lifecycle table
     */
    private String getDefaultGlossaryLifecycleValue() {
        return getDefaultLookupValue("glossary_lifecycle");
    }
    
    /**
     * Get default glossary format type value from glossary_format_type table
     */
    private String getDefaultGlossaryFormatTypeValue() {
        return getDefaultLookupValue("glossary_format_type");
    }
    
    /**
     * Get default security classification value from security_classification table
     */
    private String getDefaultSecurityClassificationValue() {
        return getDefaultLookupValue("security_classification");
    }
    
    /**
     * Get default glossary type value from glossary_type table
     */
    private String getDefaultGlossaryTypeValue() {
        return getDefaultLookupValue("glossary_type");
    }
    
    /**
     * Get default business area lifecycle value from business_area_lifecycle table
     */
    private String getDefaultBusinessAreaLifecycleValue() {
        return getDefaultLookupValue("business_area_lifecycle");
    }
    
    /**
     * Get default capability classification value from capability_classification table
     */
    private String getDefaultCapabilityClassificationValue() {
        return getDefaultLookupValue("capability_classification");
    }
    
    /**
     * Create header cell with styling
     * @param isMandatory if true, the header will have red background color
     */
    private void createHeaderCell(Row row, int colIndex, String value, boolean isMandatory) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
        
        // Style header cell
        CellStyle style = row.getSheet().getWorkbook().createCellStyle();
        Font font = row.getSheet().getWorkbook().createFont();
        font.setBold(true);
        style.setFont(font);
        
        // Set background color: red for mandatory, grey for optional
        if (isMandatory) {
            style.setFillForegroundColor(IndexedColors.RED.getIndex());
        } else {
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cell.setCellStyle(style);
    }
    
    /**
     * For INSERT templates, apply mandatory (red) style to BUDG Status and BUDG Viewing header cells
     * so users see they are required before uploading.
     */
    private void applyMandatoryColumnHighlight(XSSFSheet sheet, Row headerRow, String templateType) {
        if (headerRow == null || !"INSERT".equals(templateType)) {
            return;
        }
        Workbook workbook = sheet.getWorkbook();
        CellStyle mandatoryHeaderStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        mandatoryHeaderStyle.setFont(font);
        mandatoryHeaderStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        mandatoryHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        int maxCol = headerRow.getLastCellNum();
        for (int i = 0; i < maxCol; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null || cell.getCellType() != CellType.STRING) {
                continue;
            }
            String value = cell.getStringCellValue();
            if ("BUDG Status".equals(value) || "BUDG Viewing".equals(value)) {
                cell.setCellStyle(mandatoryHeaderStyle);
            }
        }
    }
    
    /**
     * Create regular cell
     */
    private void createCell(Row row, int colIndex, String value) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
    }
    
    /**
     * Add comment (hint) to a cell
     */
    private void addCellComment(XSSFSheet sheet, Row row, int colIndex, String commentText) {
        if (commentText == null || commentText.trim().isEmpty()) {
            return;
        }
        
        try {
            XSSFWorkbook workbook = sheet.getWorkbook();
            XSSFCreationHelper factory = workbook.getCreationHelper();
            
            // Get or create drawing patriarch
            XSSFDrawing drawing = sheet.getDrawingPatriarch();
            if (drawing == null) {
                drawing = sheet.createDrawingPatriarch();
            }
            
            // Create anchor for comment position with dynamic size based on text length
            ClientAnchor anchor = factory.createClientAnchor();
            anchor.setCol1(colIndex);
            anchor.setCol2(colIndex + 5); // comment width

            int approxCharsPerLine = 45; // أقل حروف في السطر → أسطر أكثر
            int lines = (commentText.length() / approxCharsPerLine) + 1;
            int rowsHigh = Math.min(20, 3 + lines); // حد أقصى أطول من قبل

            anchor.setRow1(row.getRowNum());
            anchor.setRow2(row.getRowNum() + rowsHigh);
            
            // Create comment
            Comment comment = drawing.createCellComment(anchor);
            comment.setString(factory.createRichTextString(commentText));
            
            // Set comment author
            if (comment instanceof XSSFComment) {
                ((XSSFComment) comment).setAuthor("System");
            }
            
            // Attach comment to cell
            Cell cell = row.getCell(colIndex);
            if (cell == null) {
                cell = row.createCell(colIndex);
            }
            cell.setCellComment(comment);
        } catch (Exception e) {
            logger.warn("Could not add comment to cell: {}", e.getMessage());
        }
    }
    
    /**
     * Add dropdown validation (data validation) to a column
     */
    private void addDropdownValidation(XSSFSheet sheet, int colIndex, List<String> values, int firstRow, int lastRow) {
        if (values.isEmpty()) {
            return;
        }
        
        // Create a hidden sheet for the dropdown values
        XSSFWorkbook workbook = sheet.getWorkbook();
        XSSFSheet hiddenSheet = workbook.createSheet("Hidden_" + colIndex);
        
        // Write values to hidden sheet
        for (int i = 0; i < values.size(); i++) {
            Row row = hiddenSheet.createRow(i);
            Cell cell = row.createCell(0);
            cell.setCellValue(values.get(i));
        }
        
        // Hide the sheet
        workbook.setSheetHidden(workbook.getSheetIndex(hiddenSheet), true);
        
        // Create data validation
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sheet);
        XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) 
            dvHelper.createFormulaListConstraint("Hidden_" + colIndex + "!$A$1:$A$" + values.size());
        
        CellRangeAddressList addressList = new CellRangeAddressList(firstRow, lastRow, colIndex, colIndex);
        XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, addressList);
        
        // Set validation properties
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("Invalid Value", "Please select a value from the dropdown list.");
        
        validation.setShowPromptBox(true);
        validation.createPromptBox("Select Value", "Please select a value from the dropdown list.");
        
        sheet.addValidationData(validation);
    }
    
    /**
     * Capitalize first letter of string
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Convert entity key to display name for template filename
     * Examples: "regulator" -> "Regulator", "regulatorytheme" -> "Regulatory Theme"
     */
    private String getEntityDisplayName(String entity) {
        if (entity == null || entity.isEmpty()) {
            return entity;
        }
        
        // Handle special cases with spaces
        if ("regulatorytheme".equals(entity) || "regulatory_theme".equals(entity)) {
            return "Regulatory Theme";
        } else if ("businessarea".equals(entity) || "business_area".equals(entity)) {
            return "Business Area";
        } else if ("orgunit".equals(entity) || "org_unit".equals(entity)) {
            return "Org. Unit";
        } else if ("dataset".equals(entity)) {
            return "Data Set";
        }
        
        // For other entities, capitalize first letter and handle camelCase
        // Convert "regulator" -> "Regulator", "geography" -> "Geography", etc.
        String[] parts = entity.split("_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            String part = parts[i];
            if (!part.isEmpty()) {
                result.append(part.substring(0, 1).toUpperCase());
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Add Segment column to template (for INSERT templates only)
     * Adds dropdown validation with accessible segments for the user
     * 
     * @param sheet The Excel sheet
     * @param headerRow The header row
     * @param segmentMode The segment mode (MULTIPLE, ENTERPRISE, or SPECIFIC)
     * @param segment The selected segment ID from UI (for SPECIFIC mode)
     * @param userId The user ID for segment access control
     * @return The column index where Segment was added
     */
    private int addSegmentColumn(XSSFSheet sheet, Row headerRow, String segmentMode, String segment, int userId) {
        int colIndex = headerRow.getLastCellNum();
        if (colIndex < 0) {
            colIndex = 0;
        }
        
        // Determine if column is mandatory based on segment mode
        boolean isMandatory = "MULTIPLE".equals(segmentMode);
        
        // Create Segment header cell
        createHeaderCell(headerRow, colIndex, "Segment", isMandatory);
        
        // Add comment based on segment mode
        String commentText;
        if (segmentMode == null || segmentMode.isEmpty()) {
            commentText = "Optional. Specify a segment to change the object's segment, or leave empty to keep current segment.";
        } else if ("MULTIPLE".equals(segmentMode)) {
            commentText = "Required when Segment mode = Multiple. Select a segment from the dropdown for each row.";
        } else if ("ENTERPRISE".equals(segmentMode)) {
            commentText = "Optional. Leave empty to assign to Enterprise segment, or select a segment from the dropdown.";
        } else {
            // SPECIFIC mode
            commentText = "Optional. Leave empty to use the selected segment, or select a different segment from the dropdown.";
        }
        
        addCellComment(sheet, headerRow, colIndex, commentText);
        
        // Get accessible segments for the user
        java.util.List<String> accessibleSegmentNames = getUserAccessibleSegmentNames(userId);
        
        // Add dropdown validation if we have accessible segments
        if (!accessibleSegmentNames.isEmpty()) {
            // Add dropdown validation to all data rows (starting from row 1, up to row 1000)
            addDropdownValidation(sheet, colIndex, accessibleSegmentNames, 1, 1000);
        }
        
        // Determine default segment value based on segmentMode and selected segment
        String defaultSegmentValue = "";
        logger.info("Setting default segment value - segmentMode: {}, segment: {}", segmentMode, segment);
        
        if ("SPECIFIC".equals(segmentMode) && segment != null && !segment.trim().isEmpty()) {
            // For SPECIFIC mode, use the selected segment name as default
            try {
                int segmentId = Integer.parseInt(segment.trim());
                logger.info("Getting segment name for ID: {}", segmentId);
                String segmentName = getSegmentNameById(segmentId);
                logger.info("Segment name retrieved: {}", segmentName);
                if (segmentName != null && !segmentName.isEmpty()) {
                    defaultSegmentValue = segmentName;
                    logger.info("Setting default segment value to: {}", defaultSegmentValue);
                } else {
                    logger.warn("Segment name is null or empty for ID: {}", segmentId);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid segment ID format: {}", segment, e);
            } catch (SQLException e) {
                logger.error("Error getting segment name for ID {}: {}", segment, e.getMessage(), e);
            }
        } else if ("ENTERPRISE".equals(segmentMode)) {
            // For ENTERPRISE mode, default to "Enterprise"
            defaultSegmentValue = "Enterprise";
            logger.info("ENTERPRISE mode: Setting default segment value to: {}", defaultSegmentValue);
        } else if ("MULTIPLE".equals(segmentMode)) {
            // For MULTIPLE mode, leave empty (user must select)
            logger.info("MULTIPLE mode: Leaving segment empty");
        }
        
        logger.info("Final default segment value: '{}'", defaultSegmentValue);
        
        // Add cell to example row (row 1) with default value
        Row exampleRow = sheet.getRow(1);
        if (exampleRow == null) {
            exampleRow = sheet.createRow(1);
        }
        createCell(exampleRow, colIndex, defaultSegmentValue);
        logger.info("Created Segment cell at column {} with value: '{}'", colIndex, defaultSegmentValue);
        
        return colIndex; // Return column index for later use
    }
    
    /**
     * Set segment default value in example row (called after all columns are added)
     * This ensures the default value is not overwritten by other operations
     */
    private void setSegmentDefaultValue(XSSFSheet sheet, int segmentColumnIndex, String segmentMode, String segment) {
        // Determine default segment value based on segmentMode and selected segment
        String defaultSegmentValue = "";
        logger.info("Re-applying segment default value - segmentMode: {}, segment: {}, columnIndex: {}", segmentMode, segment, segmentColumnIndex);
        
        if ("SPECIFIC".equals(segmentMode) && segment != null && !segment.trim().isEmpty()) {
            // For SPECIFIC mode, use the selected segment name as default
            try {
                int segmentId = Integer.parseInt(segment.trim());
                logger.info("Getting segment name for ID: {}", segmentId);
                String segmentName = getSegmentNameById(segmentId);
                logger.info("Segment name retrieved: {}", segmentName);
                if (segmentName != null && !segmentName.isEmpty()) {
                    defaultSegmentValue = segmentName;
                    logger.info("Setting default segment value to: {}", defaultSegmentValue);
                } else {
                    logger.warn("Segment name is null or empty for ID: {}", segmentId);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid segment ID format: {}", segment, e);
            } catch (SQLException e) {
                logger.error("Error getting segment name for ID {}: {}", segment, e.getMessage(), e);
            }
        } else if ("ENTERPRISE".equals(segmentMode)) {
            // For ENTERPRISE mode, default to "Enterprise"
            defaultSegmentValue = "Enterprise";
            logger.info("ENTERPRISE mode: Setting default segment value to: {}", defaultSegmentValue);
        }
        // For MULTIPLE mode, leave empty (user must select)
        
        logger.info("Final default segment value to set: '{}'", defaultSegmentValue);
        
        // Set the value in example row (row 1)
        Row exampleRow = sheet.getRow(1);
        if (exampleRow != null) {
            createCell(exampleRow, segmentColumnIndex, defaultSegmentValue);
            logger.info("Re-applied Segment cell at column {} with value: '{}'", segmentColumnIndex, defaultSegmentValue);
        } else {
            logger.warn("Example row (row 1) not found, cannot set segment default value");
        }
    }
    
    /**
     * Get segment name by ID
     * @param segmentId The segment ID
     * @return The segment name, or null if not found
     */
    private String getSegmentNameById(int segmentId) throws SQLException {
        logger.info("getSegmentNameById called with segmentId: {}", segmentId);
        
        // Enterprise is ID 1
        if (segmentId == 1) {
            logger.info("Segment ID 1 detected, returning 'Enterprise'");
            return "Enterprise";
        }
        
        String sql = "SELECT Name FROM segment WHERE ID = ? AND Deleted_At IS NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, segmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String segmentName = rs.getString("Name");
                    logger.info("Found segment name '{}' for ID {}", segmentName, segmentId);
                    return segmentName;
                } else {
                    logger.warn("No segment found for ID {}", segmentId);
                }
            }
        } catch (SQLException e) {
            logger.error("SQL error getting segment name for ID {}: {}", segmentId, e.getMessage(), e);
            throw e;
        }
        logger.warn("Returning null for segment ID {}", segmentId);
        return null;
    }
    
    /**
     * Get list of accessible segment names for a user
     * @param userId The user ID
     * @return List of segment names accessible to the user
     */
    private java.util.List<String> getUserAccessibleSegmentNames(int userId) {
        java.util.List<String> segmentNames = new java.util.ArrayList<>();
        try {
            java.util.List<java.util.Map<String, Object>> segments = SegmentAccessService.getUserAccessibleSegments(userId);
            for (java.util.Map<String, Object> segment : segments) {
                String name = (String) segment.get("name");
                if (name != null && !name.isEmpty()) {
                    segmentNames.add(name);
                }
            }
        } catch (Exception e) {
            logger.warn("Error getting accessible segments for user {}: {}", userId, e.getMessage());
            // If error, return empty list (no dropdown validation)
        }
        return segmentNames;
    }
    
    /**
     * Generate Committee template
     */
    private void generateCommitteeTemplate(XSSFSheet sheet, Row headerRow, String templateType, 
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        
        // Get lookup values from database (dynamic)
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> classificationValues = lookupValues.getOrDefault("committee_classification", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("committee_lifecycle", new ArrayList<>());
        List<String> committeeTypeValues = lookupValues.getOrDefault("committee_type", new ArrayList<>());
        List<String> governanceRoleValues = getRoleValuesForCommittee();
        
        // Get default role value (first option from list)
        String defaultRole = governanceRoleValues.isEmpty() ? "" : governanceRoleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            // Create Committee sheet columns
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Committee Name", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Description", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Parent Committee Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            
            // Lookup fields
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false); // col 5
            createHeaderCell(headerRow, colIndex++, "Classification", true); // Mandatory, col 6
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false); // col 7
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true); // Mandatory, col 8
            createHeaderCell(headerRow, colIndex++, "Committee Type", true); // Mandatory, col 9
            
            // User fields
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false); // col 14
            
            // Create example row with default values
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultClassification = getDefaultClassificationValue();
            String defaultLifecycle = getDefaultLifecycleValue();
            String defaultCommitteeType = getDefaultCommitteeTypeValue();
            
            // Column 0-4: Empty (Reference, Committee Name, Description, Parent Committee Name, Parent Ref.)
            for (int i = 0; i < 5; i++) {
                createCell(exampleRow, i, "");
            }
            // Column 5: BUDG Viewing (default: Public)
            createCell(exampleRow, 5, defaultViewing);
            // Column 6: Classification (mandatory LIST - show default)
            createCell(exampleRow, 6, defaultClassification);
            // Column 7: BUDG Status (default: Active)
            createCell(exampleRow, 7, defaultStatus);
            // Column 8: Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, 8, defaultLifecycle);
            // Column 9: Committee Type (mandatory LIST - show default)
            createCell(exampleRow, 9, defaultCommitteeType);
            // Column 10-13: Empty (User Email, User First Name, User Last Name, User Lan ID)
            for (int i = 10; i < 14; i++) {
                createCell(exampleRow, i, "");
            }
            // Column 14: Governance Role (default: first option from Committee module roles)
            createCell(exampleRow, 14, defaultRole);
            
            // Add dropdown validation for lookup fields
            if (!viewingValues.isEmpty()) {
                addDropdownValidation(sheet, 5, viewingValues, 1, 1000); // BUDG Viewing
            }
            if (!classificationValues.isEmpty()) {
                addDropdownValidation(sheet, 6, classificationValues, 1, 1000); // Classification
            }
            if (!statusValues.isEmpty()) {
                addDropdownValidation(sheet, 7, statusValues, 1, 1000); // BUDG Status
            }
            if (!lifecycleValues.isEmpty()) {
                addDropdownValidation(sheet, 8, lifecycleValues, 1, 1000); // Lifecycle
            }
            if (!committeeTypeValues.isEmpty()) {
                addDropdownValidation(sheet, 9, committeeTypeValues, 1, 1000); // Committee Type
            }
            if (!governanceRoleValues.isEmpty()) {
                addDropdownValidation(sheet, 14, governanceRoleValues, 1, 1000); // Governance Role
            }
            
        } else if ("UPDATE".equals(templateType)) {
            // Update Committee sheet columns
            createHeaderCell(headerRow, colIndex++, "Committee ID", false);
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Committee Name", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Parent Committee Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            
            // Lookup fields
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "Classification", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "Committee Type", false);
            
            // Create example row with default values
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultClassification = getDefaultClassificationValue();
            String defaultLifecycle = getDefaultLifecycleValue();
            String defaultCommitteeType = getDefaultCommitteeTypeValue();
            
            // Column 0-5: Empty (Committee ID, Reference, Committee Name, Description, Parent Committee Name, Parent Ref.)
            for (int i = 0; i < 6; i++) {
                createCell(exampleRow, i, "");
            }
            // Column 6: BUDG Viewing (default: Public)
            createCell(exampleRow, 6, defaultViewing);
            // Column 7: Classification (mandatory LIST - show default)
            createCell(exampleRow, 7, defaultClassification);
            // Column 8: BUDG Status (default: Active)
            createCell(exampleRow, 8, defaultStatus);
            // Column 9: Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, 9, defaultLifecycle);
            // Column 10: Committee Type (mandatory LIST - show default)
            createCell(exampleRow, 10, defaultCommitteeType);
            
            // Add dropdown validation for lookup fields
            if (!viewingValues.isEmpty()) {
                addDropdownValidation(sheet, 6, viewingValues, 1, 1000); // BUDG Viewing
            }
            if (!classificationValues.isEmpty()) {
                addDropdownValidation(sheet, 7, classificationValues, 1, 1000); // Classification
            }
            if (!statusValues.isEmpty()) {
                addDropdownValidation(sheet, 8, statusValues, 1, 1000); // BUDG Status
            }
            if (!lifecycleValues.isEmpty()) {
                addDropdownValidation(sheet, 9, lifecycleValues, 1, 1000); // Lifecycle
            }
            if (!committeeTypeValues.isEmpty()) {
                addDropdownValidation(sheet, 10, committeeTypeValues, 1, 1000); // Committee Type
            }
            
        } else {
            // Delete Committee sheet columns
            createHeaderCell(headerRow, colIndex++, "Committee ID", true); // Mandatory
            
            // Create example row
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Send JSON error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        try {
            // Reset response if headers were already set
            if (response.isCommitted()) {
                logger.warn("Response already committed, cannot send error response");
                return;
            }
            
            response.resetBuffer(); // Clear any previously written content
            response.setStatus(statusCode);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("status", "error");
            errorResponse.addProperty("message", message);
            
            response.getWriter().write(gson.toJson(errorResponse));
            response.getWriter().flush();
        } catch (Exception e) {
            logger.error("Error sending error response: {}", e.getMessage(), e);
            // If we can't send JSON, try to send plain text
            try {
                response.resetBuffer();
                response.setStatus(statusCode);
                response.setContentType("text/plain");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("Error: " + message);
            } catch (Exception e2) {
                logger.error("Failed to send error response: {}", e2.getMessage());
            }
        }
    }
    
    /**
     * Generate Business Area template
     */
    private void generateBusinessAreaTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("business_area_lifecycle", new ArrayList<>());
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> roleValues = getRoleValuesByModuleName("Business Area");
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Business Area Name", true); // Required
            createHeaderCell(headerRow, colIndex++, "Description", true); // Required
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true); // Required
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultBusinessAreaLifecycle = getDefaultBusinessAreaLifecycleValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Business Area Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Parent Name
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultBusinessAreaLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, ""); // Governance Role
            
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 3, viewingValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 4, statusValues, 1, 1000);
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 5, lifecycleValues, 1, 1000);
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 10, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Business Area ID", false);
            createHeaderCell(headerRow, colIndex++, "Business Area Name", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultBusinessAreaLifecycle = getDefaultBusinessAreaLifecycleValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Business Area ID (mandatory)
            createCell(exampleRow, ex++, ""); // Business Area Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Parent Name
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultBusinessAreaLifecycle); // Lifecycle (mandatory LIST - show default)
            
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 4, viewingValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 5, statusValues, 1, 1000);
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 6, lifecycleValues, 1, 1000);
        } else {
            createHeaderCell(headerRow, colIndex++, "Business Area ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Capability template
     */
    private void generateCapabilityTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> classificationValues = lookupValues.getOrDefault("capability_classification", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("capability_lifecyle", new ArrayList<>());
        List<String> capabilityTypeValues = lookupValues.getOrDefault("capability_type", new ArrayList<>());
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        
        // Get Capability roles dynamically
        List<String> roleValues = getRoleValuesByModuleName("Capability");
        
        // Get default role value (first option from list)
        String defaultRole = roleValues.isEmpty() ? "" : roleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Capability Name", true); // Required
            createHeaderCell(headerRow, colIndex++, "Capability Definition", true); // Required
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "Classification", true); // Required
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true); // Required
            createHeaderCell(headerRow, colIndex++, "Capability Type", true); // Required
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultCapabilityClassification = getDefaultCapabilityClassificationValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Reference
            createCell(exampleRow, ex++, ""); // Capability Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Capability Definition (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Parent Capability
            createCell(exampleRow, ex++, ""); // Parent Ref.
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultCapabilityClassification); // Classification (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, ""); // Lifecycle (required but not LIST - check entityConfig)
            createCell(exampleRow, ex++, ""); // Capability Type (required but not LIST - check entityConfig)
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, defaultRole); // Governance Role (default: first option from Capability module roles)
            
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 5, viewingValues, 1, 1000);
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 6, classificationValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 7, statusValues, 1, 1000);
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 8, lifecycleValues, 1, 1000);
            if (!capabilityTypeValues.isEmpty()) addDropdownValidation(sheet, 9, capabilityTypeValues, 1, 1000);
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 14, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Capability ID", false);
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Capability Name", false);
            createHeaderCell(headerRow, colIndex++, "Capability Definition", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "Classification", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "Capability Type", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultCapabilityClassification = getDefaultCapabilityClassificationValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Capability ID (mandatory)
            createCell(exampleRow, ex++, ""); // Reference
            createCell(exampleRow, ex++, ""); // Capability Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Capability Definition (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Parent Name
            createCell(exampleRow, ex++, ""); // Parent Ref.
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultCapabilityClassification); // Classification (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, ""); // Lifecycle (required but not LIST - check entityConfig)
            createCell(exampleRow, ex++, ""); // Capability Type (required but not LIST - check entityConfig)
            
            // List fields dropdown validations
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 6, viewingValues, 1, 1000); // BUDG Viewing
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 7, classificationValues, 1, 1000); // Classification
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 8, statusValues, 1, 1000); // BUDG Status
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 9, lifecycleValues, 1, 1000); // Lifecycle
            if (!capabilityTypeValues.isEmpty()) addDropdownValidation(sheet, 10, capabilityTypeValues, 1, 1000); // Capability Type
        } else {
            createHeaderCell(headerRow, colIndex++, "Capability ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Client template
     */
    private void generateClientTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("client_lifecycle", new ArrayList<>());
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> roleValues = getRoleValuesByModuleName("Client");
        
        // Get default role value (first option from list)
        String defaultRole = roleValues.isEmpty() ? "" : roleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Client Name", true); // Required
            createHeaderCell(headerRow, colIndex++, "Long Name", false); // Optional
            createHeaderCell(headerRow, colIndex++, "Description", true); // Required
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true); // Required
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 11; i++) createCell(exampleRow, i, "");
            createCell(exampleRow, 11, defaultRole); // Governance Role (default: first option from Client module roles)
            
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 4, lifecycleValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 5, statusValues, 1, 1000);
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 6, viewingValues, 1, 1000);
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 11, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Client ID", false);
            createHeaderCell(headerRow, colIndex++, "Client Name", false);
            createHeaderCell(headerRow, colIndex++, "Long Name", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 8; i++) createCell(exampleRow, i, "");
            
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 5, lifecycleValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 6, statusValues, 1, 1000);
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 7, viewingValues, 1, 1000);
        } else {
            createHeaderCell(headerRow, colIndex++, "Client ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Legal template
     */
    private void generateLegalTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        // Get Legal Entity module ID dynamically
        List<String> roleValues = new ArrayList<>();
        // Get Legal Entity roles dynamically (supports "Legal Entity", "Legal_Entity", etc.)
        roleValues = getRoleValuesByModuleName("Legal Entity");
        
        // Get default values (first option from list)
        String defaultStatus = statusValues.isEmpty() ? "" : statusValues.get(0);
        String defaultViewing = viewingValues.isEmpty() ? "" : viewingValues.get(0);
        String defaultRole = roleValues.isEmpty() ? "" : roleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Short Name", true);
            createHeaderCell(headerRow, colIndex++, "Long Name", true);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Short Name
            createCell(exampleRow, ex++, ""); // Long Name
            createCell(exampleRow, ex++, ""); // Description
            createCell(exampleRow, ex++, ""); // Parent Short Name
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status (default: first option)
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing (default: first option)
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, defaultRole); // Governance Role (default: first option)
            
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 4, statusValues, 1, 1000);
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 5, viewingValues, 1, 1000);
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 10, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Legal ID", false);
            createHeaderCell(headerRow, colIndex++, "Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Long Name", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            
            Row exampleRow = sheet.createRow(1);
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Legal ID
            createCell(exampleRow, ex++, ""); // Short Name
            createCell(exampleRow, ex++, ""); // Long Name
            createCell(exampleRow, ex++, ""); // Description
            createCell(exampleRow, ex++, ""); // Parent Short Name
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status (default: first option)
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing (default: first option)
            
            // List fields dropdown validations
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 5, statusValues, 1, 1000); // BUDG Status
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 6, viewingValues, 1, 1000); // BUDG Viewing
        } else {
            createHeaderCell(headerRow, colIndex++, "Legal ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Org Unit template
     */
    private void generateOrgUnitTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Org Unit Name", true);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Reference", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Parent Org Unit Reference", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 5; i++) createCell(exampleRow, i, "");
            
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 4, statusValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Org Unit ID", false);
            createHeaderCell(headerRow, colIndex++, "Org Unit Name", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Parent Org Unit Reference", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 6; i++) createCell(exampleRow, i, "");
            
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 5, statusValues, 1, 1000); // BUDG Status
        } else {
            createHeaderCell(headerRow, colIndex++, "Org Unit ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate People template
     */
    private void generatePeopleTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> employmentTypeValues = lookupValues.getOrDefault("employment_type", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("people_lifecycle_status", new ArrayList<>());
        
        // Profile values from role table
        List<String> profileValues = lookupValues.getOrDefault("role", new ArrayList<>());
        
        if ("INSERT".equals(templateType)) {
            // CREATE Template Columns (in exact order specified)
            createHeaderCell(headerRow, colIndex++, "First Name", true); // 0 - Required
            createHeaderCell(headerRow, colIndex++, "Last Name", true); // 1 - Required
            createHeaderCell(headerRow, colIndex++, "Function", false); // 2 - Optional
            createHeaderCell(headerRow, colIndex++, "Function Description", false); // 3 - Optional
            createHeaderCell(headerRow, colIndex++, "Description", false); // 4 - Optional
            createHeaderCell(headerRow, colIndex++, "Email", true); // 5 - Required
            createHeaderCell(headerRow, colIndex++, "Password", false); // 6 - Optional
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false); // 7 - Optional
            createHeaderCell(headerRow, colIndex++, "Org Unit Reference", false); // 8 - Optional
            createHeaderCell(headerRow, colIndex++, "Org Unit Name", false); // 9 - Optional
            createHeaderCell(headerRow, colIndex++, "Profile", true); // 10 - Required
            createHeaderCell(headerRow, colIndex++, "Office Location", false); // 11 - Optional
            createHeaderCell(headerRow, colIndex++, "Internal Mail Code", false); // 12 - Optional
            createHeaderCell(headerRow, colIndex++, "Office Telephone", false); // 13 - Optional
            createHeaderCell(headerRow, colIndex++, "Mobile/Cell", false); // 14 - Optional
            createHeaderCell(headerRow, colIndex++, "LAN ID", false); // 15 - Optional
            createHeaderCell(headerRow, colIndex++, "Employment Type", true); // 16 - Required
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true); // 17 - Required
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 18; i++) createCell(exampleRow, i, "");
            
            // Add dropdown validations
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 7, statusValues, 1, 1000);
            if (!profileValues.isEmpty()) addDropdownValidation(sheet, 10, profileValues, 1, 1000);
            if (!employmentTypeValues.isEmpty()) addDropdownValidation(sheet, 16, employmentTypeValues, 1, 1000);
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 17, lifecycleValues, 1, 1000);
            
        } else if ("UPDATE".equals(templateType)) {
            // UPDATE Template Columns (in exact order specified)
            createHeaderCell(headerRow, colIndex++, "People ID", false);
            createHeaderCell(headerRow, colIndex++, "First Name", false);
            createHeaderCell(headerRow, colIndex++, "Last Name", false);
            createHeaderCell(headerRow, colIndex++, "Function", false);
            createHeaderCell(headerRow, colIndex++, "Function Description", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "Email", false);
            createHeaderCell(headerRow, colIndex++, "Password", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Org Unit Reference", false);
            createHeaderCell(headerRow, colIndex++, "Org Unit Name", false);
            createHeaderCell(headerRow, colIndex++, "Profile", false);
            createHeaderCell(headerRow, colIndex++, "Office Location", false);
            createHeaderCell(headerRow, colIndex++, "Internal Mail Code", false);
            createHeaderCell(headerRow, colIndex++, "Office Telephone", false);
            createHeaderCell(headerRow, colIndex++, "Mobile/Cell", false);
            createHeaderCell(headerRow, colIndex++, "LAN ID", false);
            createHeaderCell(headerRow, colIndex++, "Employment Type", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 19; i++) createCell(exampleRow, i, "");
            
            // Add dropdown validations for List fields
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 8, statusValues, 1, 1000); // BUDG Status
            if (!profileValues.isEmpty()) addDropdownValidation(sheet, 11, profileValues, 1, 1000); // Profile
            if (!employmentTypeValues.isEmpty()) addDropdownValidation(sheet, 17, employmentTypeValues, 1, 1000); // Employment Type
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 18, lifecycleValues, 1, 1000); // Lifecycle
            
        } else {
            createHeaderCell(headerRow, colIndex++, "People ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Product template
     */
    private void generateProductTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("product_lifecycle", new ArrayList<>());
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> roleValues = getRoleValuesByModuleName("Product");
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Product Name", true); // Required
            createHeaderCell(headerRow, colIndex++, "Long Name", false);
            createHeaderCell(headerRow, colIndex++, "Product Description", true); // Required
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true); // Required
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 13; i++) createCell(exampleRow, i, "");
            
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 5, lifecycleValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 6, statusValues, 1, 1000);
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 7, viewingValues, 1, 1000);
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 12, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Product ID", false);
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Product Name", false);
            createHeaderCell(headerRow, colIndex++, "Long Name", false);
            createHeaderCell(headerRow, colIndex++, "Product Description", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 9; i++) createCell(exampleRow, i, "");
            
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 6, lifecycleValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 7, statusValues, 1, 1000);
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 8, viewingValues, 1, 1000);
        } else {
            createHeaderCell(headerRow, colIndex++, "Product ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate System template
     */
    private void generateSystemTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("system_lifecycle", new ArrayList<>());
        List<String> typeValues = lookupValues.getOrDefault("system_type", new ArrayList<>());
        List<String> classificationValues = lookupValues.getOrDefault("system_classification", new ArrayList<>());
        List<String> ciaRatingValues = lookupValues.getOrDefault("cia_rating", new ArrayList<>());
        
        // Get System roles dynamically
        List<String> roleValues = getRoleValuesByModuleName("System");
        
        // Get default role value (first option from list)
        String defaultRole = roleValues.isEmpty() ? "" : roleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Short Name", true);
            createHeaderCell(headerRow, colIndex++, "Long Name", false);
            createHeaderCell(headerRow, colIndex++, "Asset ID", false);
            createHeaderCell(headerRow, colIndex++, "External", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Description", true);
            createHeaderCell(headerRow, colIndex++, "URL", false);
            createHeaderCell(headerRow, colIndex++, "DQAutomation", false);
            createHeaderCell(headerRow, colIndex++, "Parent Short Name", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true);
            createHeaderCell(headerRow, colIndex++, "Type", true);
            createHeaderCell(headerRow, colIndex++, "Classification", false);
            createHeaderCell(headerRow, colIndex++, "Confidentiality", false);
            createHeaderCell(headerRow, colIndex++, "Integrity", false);
            createHeaderCell(headerRow, colIndex++, "Availability", false);
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultSystemLifecycle = getDefaultSystemLifecycleValue();
            String defaultSystemType = getDefaultSystemTypeValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Short Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Long Name (optional TEXT - empty)
            createCell(exampleRow, ex++, ""); // Asset ID
            createCell(exampleRow, ex++, ""); // External (mandatory LIST - empty, will be handled separately)
            createCell(exampleRow, ex++, ""); // Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // URL
            createCell(exampleRow, ex++, ""); // DQAutomation
            createCell(exampleRow, ex++, ""); // Parent Short Name
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultSystemLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultSystemType); // Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Classification
            createCell(exampleRow, ex++, ""); // Confidentiality
            createCell(exampleRow, ex++, ""); // Integrity
            createCell(exampleRow, ex++, ""); // Availability
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, defaultRole); // Governance Role (default: first option from System module roles)
            
            // Add dropdown validations for External and DQAutomation
            List<String> externalValues = Arrays.asList("TRUE", "FALSE");
            addDropdownValidation(sheet, 3, externalValues, 1, 1000); // External
            List<String> dqAutomationValues = Arrays.asList("TRUE", "FALSE");
            addDropdownValidation(sheet, 6, dqAutomationValues, 1, 1000); // DQAutomation
            
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 8, viewingValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 9, statusValues, 1, 1000);
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 10, lifecycleValues, 1, 1000);
            if (!typeValues.isEmpty()) addDropdownValidation(sheet, 11, typeValues, 1, 1000);
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 12, classificationValues, 1, 1000);
            if (!ciaRatingValues.isEmpty()) {
                addDropdownValidation(sheet, 13, ciaRatingValues, 1, 1000);
                addDropdownValidation(sheet, 14, ciaRatingValues, 1, 1000);
                addDropdownValidation(sheet, 15, ciaRatingValues, 1, 1000);
            }
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 20, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "ID", false);
            createHeaderCell(headerRow, colIndex++, "Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Long Name", false);
            createHeaderCell(headerRow, colIndex++, "Asset ID", false);
            createHeaderCell(headerRow, colIndex++, "External", false);
            createHeaderCell(headerRow, colIndex++, "Description", false);
            createHeaderCell(headerRow, colIndex++, "URL", false);
            createHeaderCell(headerRow, colIndex++, "DQAutomation", false);
            createHeaderCell(headerRow, colIndex++, "Parent Short Name", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "Type", false);
            createHeaderCell(headerRow, colIndex++, "Classification", false);
            createHeaderCell(headerRow, colIndex++, "Confidentiality", false);
            createHeaderCell(headerRow, colIndex++, "Integrity", false);
            createHeaderCell(headerRow, colIndex++, "Availability", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultSystemLifecycle = getDefaultSystemLifecycleValue();
            String defaultSystemType = getDefaultSystemTypeValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // ID
            createCell(exampleRow, ex++, ""); // Short Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Long Name
            createCell(exampleRow, ex++, ""); // Asset ID
            createCell(exampleRow, ex++, ""); // External (mandatory LIST - empty, will be handled separately)
            createCell(exampleRow, ex++, ""); // Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // URL
            createCell(exampleRow, ex++, ""); // DQAutomation
            createCell(exampleRow, ex++, ""); // Parent Short Name
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultSystemLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultSystemType); // Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Classification
            createCell(exampleRow, ex++, ""); // Confidentiality
            createCell(exampleRow, ex++, ""); // Integrity
            createCell(exampleRow, ex++, ""); // Availability
            
            // List fields dropdown validations
            List<String> externalValues = Arrays.asList("TRUE", "FALSE");
            addDropdownValidation(sheet, 4, externalValues, 1, 1000); // External
            List<String> dqAutomationValues = Arrays.asList("TRUE", "FALSE");
            addDropdownValidation(sheet, 7, dqAutomationValues, 1, 1000); // DQAutomation
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 9, viewingValues, 1, 1000); // BUDG Viewing
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 10, statusValues, 1, 1000); // BUDG Status
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 11, lifecycleValues, 1, 1000); // Lifecycle
            if (!typeValues.isEmpty()) addDropdownValidation(sheet, 12, typeValues, 1, 1000); // Type
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 13, classificationValues, 1, 1000); // Classification
            if (!ciaRatingValues.isEmpty()) {
                addDropdownValidation(sheet, 14, ciaRatingValues, 1, 1000); // Confidentiality
                addDropdownValidation(sheet, 15, ciaRatingValues, 1, 1000); // Integrity
                addDropdownValidation(sheet, 16, ciaRatingValues, 1, 1000); // Availability
            }
        } else {
            createHeaderCell(headerRow, colIndex++, "System Ref", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Dataset template
     */
    private void generateDatasetTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("dataset_lifecycle", new ArrayList<>());
        List<String> typeValues = lookupValues.getOrDefault("dataset_type", new ArrayList<>());
        List<String> roleValues = getRoleValuesByModuleName("Data Sets");
        
        // Get default role value (first option from list)
        String defaultRole = roleValues.isEmpty() ? "" : roleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Name", true);
            createHeaderCell(headerRow, colIndex++, "Definition", true);
            createHeaderCell(headerRow, colIndex++, "Usage", true);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "Type", true);
            createHeaderCell(headerRow, colIndex++, "System ID", false);
            createHeaderCell(headerRow, colIndex++, "System Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent System Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Glossary Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Glossary Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Glossary Name", false);
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultDatasetType = getDefaultDatasetTypeValue();
            String defaultDatasetLifecycle = getDefaultDatasetLifecycleValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Ref.
            createCell(exampleRow, ex++, ""); // Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Definition (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Usage
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultDatasetType); // Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // System ID
            createCell(exampleRow, ex++, ""); // System Short Name
            createCell(exampleRow, ex++, ""); // Parent System Short Name
            createCell(exampleRow, ex++, defaultDatasetLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, ""); // Glossary Ref.
            createCell(exampleRow, ex++, ""); // Glossary Name
            createCell(exampleRow, ex++, ""); // Parent Glossary Name
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, defaultRole); // Governance Role (default: first option from Dataset module roles)
            
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 4, viewingValues, 1, 1000);
            if (!typeValues.isEmpty()) addDropdownValidation(sheet, 5, typeValues, 1, 1000);
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 9, lifecycleValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 10, statusValues, 1, 1000);
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 18, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "ID", false);
            createHeaderCell(headerRow, colIndex++, "Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Name", false);
            createHeaderCell(headerRow, colIndex++, "Definition", false);
            createHeaderCell(headerRow, colIndex++, "Usage", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "Type", false);
            createHeaderCell(headerRow, colIndex++, "System ID", false);
            createHeaderCell(headerRow, colIndex++, "System Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent System Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Glossary Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Glossary Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Glossary Name", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultDatasetType = getDefaultDatasetTypeValue();
            String defaultDatasetLifecycle = getDefaultDatasetLifecycleValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // ID (mandatory)
            createCell(exampleRow, ex++, ""); // Ref.
            createCell(exampleRow, ex++, ""); // Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Definition (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Usage
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultDatasetType); // Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // System ID
            createCell(exampleRow, ex++, ""); // System Short Name
            createCell(exampleRow, ex++, ""); // Parent System Short Name
            createCell(exampleRow, ex++, defaultDatasetLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, ""); // Glossary Ref.
            createCell(exampleRow, ex++, ""); // Glossary Name
            createCell(exampleRow, ex++, ""); // Parent Glossary Name
            
            // List fields dropdown validations
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 5, viewingValues, 1, 1000); // BUDG Viewing
            if (!typeValues.isEmpty()) addDropdownValidation(sheet, 6, typeValues, 1, 1000); // Type
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 10, lifecycleValues, 1, 1000); // Lifecycle
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 11, statusValues, 1, 1000); // BUDG Status
        } else {
            createHeaderCell(headerRow, colIndex++, "Dataset ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Attribute template
     */
    private void generateAttributeTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        
        // Get lookup values
        List<String> dataTypeValues = lookupValues.getOrDefault("attribute_datatype", new ArrayList<>());
        List<String> requirementValues = lookupValues.getOrDefault("requirement", new ArrayList<>());
        List<String> originationValues = lookupValues.getOrDefault("attribute_origination", new ArrayList<>());
        List<String> editabilityValues = lookupValues.getOrDefault("attribute_editability", new ArrayList<>());
        List<String> editabilityRoleValues = lookupValues.getOrDefault("attribute_edit_role", new ArrayList<>());
        List<String> roleValues = getRoleValuesByModuleName("Attribute");
        List<String> trueFalseValues = Arrays.asList("True", "False");
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Reference Number", false);
            createHeaderCell(headerRow, colIndex++, "Attribute Name", true);
            createHeaderCell(headerRow, colIndex++, "Attribute Definition", true);
            createHeaderCell(headerRow, colIndex++, "Key", true);
            createHeaderCell(headerRow, colIndex++, "Business Logic", false);
            createHeaderCell(headerRow, colIndex++, "Data Length", false);
            createHeaderCell(headerRow, colIndex++, "Data Type", true);
            createHeaderCell(headerRow, colIndex++, "Attribute Requirement", true);
            createHeaderCell(headerRow, colIndex++, "Data Set Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Data Set Name", false);
            createHeaderCell(headerRow, colIndex++, "System Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Glossary Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Glossary Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Glossary Name", false);
            createHeaderCell(headerRow, colIndex++, "Origin", false);
            createHeaderCell(headerRow, colIndex++, "Editability", false);
            createHeaderCell(headerRow, colIndex++, "Editability Role", false);
            createHeaderCell(headerRow, colIndex++, "DB Field Name", false);
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 24; i++) createCell(exampleRow, i, "");
            
            // Add dropdown validations
            addDropdownValidation(sheet, 3, trueFalseValues, 1, 1000); // Key
            if (!dataTypeValues.isEmpty()) addDropdownValidation(sheet, 6, dataTypeValues, 1, 1000);
            if (!requirementValues.isEmpty()) addDropdownValidation(sheet, 7, requirementValues, 1, 1000);
            if (!originationValues.isEmpty()) addDropdownValidation(sheet, 14, originationValues, 1, 1000);
            if (!editabilityValues.isEmpty()) addDropdownValidation(sheet, 15, editabilityValues, 1, 1000);
            if (!editabilityRoleValues.isEmpty()) addDropdownValidation(sheet, 16, editabilityRoleValues, 1, 1000);
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 22, roleValues, 1, 1000); // Governance Role
            
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Attribute ID", false);
            createHeaderCell(headerRow, colIndex++, "Reference Number", false);
            createHeaderCell(headerRow, colIndex++, "Attribute Name", false);
            createHeaderCell(headerRow, colIndex++, "Attribute Definition", false);
            createHeaderCell(headerRow, colIndex++, "Key", false); // List
            createHeaderCell(headerRow, colIndex++, "Business Logic", false);
            createHeaderCell(headerRow, colIndex++, "Data Length", false);
            createHeaderCell(headerRow, colIndex++, "Data Type", false); // List
            createHeaderCell(headerRow, colIndex++, "Attribute Requirement", false); // List
            createHeaderCell(headerRow, colIndex++, "Data Set Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Data Set Name", false);
            createHeaderCell(headerRow, colIndex++, "System Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Glossary Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Glossary Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Glossary Name", false);
            createHeaderCell(headerRow, colIndex++, "Origin", false); // List
            createHeaderCell(headerRow, colIndex++, "Editability", false); // List
            createHeaderCell(headerRow, colIndex++, "Editability Role", false); // List
            createHeaderCell(headerRow, colIndex++, "DB Field Name", false);
            
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < 19; i++) createCell(exampleRow, i, "");
            
            // Add dropdown validations for List fields
            addDropdownValidation(sheet, 4, trueFalseValues, 1, 1000); // Key
            if (!dataTypeValues.isEmpty()) addDropdownValidation(sheet, 7, dataTypeValues, 1, 1000); // Data Type
            if (!requirementValues.isEmpty()) addDropdownValidation(sheet, 8, requirementValues, 1, 1000); // Attribute Requirement
            if (!originationValues.isEmpty()) addDropdownValidation(sheet, 15, originationValues, 1, 1000); // Origin
            if (!editabilityValues.isEmpty()) addDropdownValidation(sheet, 16, editabilityValues, 1, 1000); // Editability
            if (!editabilityRoleValues.isEmpty()) addDropdownValidation(sheet, 17, editabilityRoleValues, 1, 1000); // Editability Role
            
        } else {
            createHeaderCell(headerRow, colIndex++, "Attribute ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Interface template
     */
    private void generateInterfaceTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> transferMethodValues = lookupValues.getOrDefault("interface_transfer", new ArrayList<>());
        List<String> transferFormatValues = lookupValues.getOrDefault("interface_transfer_format", new ArrayList<>());
        List<String> classificationValues = lookupValues.getOrDefault("interface_classification", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("interface_lifecycle", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> automationValues = lookupValues.getOrDefault("interface_automation", new ArrayList<>());
        List<String> frequencyValues = lookupValues.getOrDefault("interface_frequency", new ArrayList<>());
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> roleValues = getRoleValuesForInterface();
        
        // Get default role value (first option from list)
        String defaultRole = roleValues.isEmpty() ? "" : roleValues.get(0);
        
        if ("INSERT".equals(templateType)) {
            // Column order as specified by user
            createHeaderCell(headerRow, colIndex++, "Interface Name", true);  // Required
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Asset ID", false);
            createHeaderCell(headerRow, colIndex++, "Synchronisation Control", false);
            createHeaderCell(headerRow, colIndex++, "Interface Description", true);  // Required
            createHeaderCell(headerRow, colIndex++, "Transfer Method", false);  // List col
            createHeaderCell(headerRow, colIndex++, "Transfer Format", false);  // List col
            createHeaderCell(headerRow, colIndex++, "Interface Classification", false);  // List col
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true);  // Required, List col
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);  // List col
            createHeaderCell(headerRow, colIndex++, "Source System Short Name", true);  // Required
            createHeaderCell(headerRow, colIndex++, "Target System Short Name", true);  // Required
            createHeaderCell(headerRow, colIndex++, "Automation Level", true);  // Required, List col
            createHeaderCell(headerRow, colIndex++, "Frequency", false);  // List col
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);  // List col
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);  // List col
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultInterfaceLifecycle = getDefaultInterfaceLifecycleValue();
            String defaultInterfaceAutomation = getDefaultInterfaceAutomationValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Interface Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Reference (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Asset ID
            createCell(exampleRow, ex++, ""); // Synchronisation Control
            createCell(exampleRow, ex++, ""); // Interface Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Transfer Method
            createCell(exampleRow, ex++, ""); // Transfer Format
            createCell(exampleRow, ex++, ""); // Interface Classification
            createCell(exampleRow, ex++, defaultInterfaceLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, ""); // Source System Short Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Target System Short Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, defaultInterfaceAutomation); // Automation Level (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Frequency
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, defaultRole); // Governance Role (default: first option from Interface module roles)
            
            // Add dropdown validations for list columns
            if (!transferMethodValues.isEmpty()) addDropdownValidation(sheet, 5, transferMethodValues, 1, 1000);
            if (!transferFormatValues.isEmpty()) addDropdownValidation(sheet, 6, transferFormatValues, 1, 1000);
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 7, classificationValues, 1, 1000);
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 8, lifecycleValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 9, statusValues, 1, 1000);
            if (!automationValues.isEmpty()) addDropdownValidation(sheet, 12, automationValues, 1, 1000);
            if (!frequencyValues.isEmpty()) addDropdownValidation(sheet, 13, frequencyValues, 1, 1000);
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 14, viewingValues, 1, 1000);
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 19, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Interface ID", false);
            createHeaderCell(headerRow, colIndex++, "Interface Name", false);
            createHeaderCell(headerRow, colIndex++, "Reference", false);
            createHeaderCell(headerRow, colIndex++, "Asset ID", false);
            createHeaderCell(headerRow, colIndex++, "Synchronisation Control", false);
            createHeaderCell(headerRow, colIndex++, "Interface Description", false);
            createHeaderCell(headerRow, colIndex++, "Transfer Method", false);
            createHeaderCell(headerRow, colIndex++, "Transfer Format", false);
            createHeaderCell(headerRow, colIndex++, "Interface Classification", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Source System Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Target System Short Name", false);
            createHeaderCell(headerRow, colIndex++, "Automation Level", false);
            createHeaderCell(headerRow, colIndex++, "Frequency", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultInterfaceLifecycle = getDefaultInterfaceLifecycleValue();
            String defaultInterfaceAutomation = getDefaultInterfaceAutomationValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Interface ID
            createCell(exampleRow, ex++, ""); // Interface Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Reference
            createCell(exampleRow, ex++, ""); // Asset ID
            createCell(exampleRow, ex++, ""); // Synchronisation Control
            createCell(exampleRow, ex++, ""); // Interface Description (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Transfer Method
            createCell(exampleRow, ex++, ""); // Transfer Format
            createCell(exampleRow, ex++, ""); // Interface Classification
            createCell(exampleRow, ex++, defaultInterfaceLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, ""); // Source System Short Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Target System Short Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, defaultInterfaceAutomation); // Automation Level (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Frequency
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            
            // Add dropdown validations for list columns
            if (!transferMethodValues.isEmpty()) addDropdownValidation(sheet, 6, transferMethodValues, 1, 1000); // Transfer Method
            if (!transferFormatValues.isEmpty()) addDropdownValidation(sheet, 7, transferFormatValues, 1, 1000); // Transfer Format
            if (!classificationValues.isEmpty()) addDropdownValidation(sheet, 8, classificationValues, 1, 1000); // Interface Classification
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 9, lifecycleValues, 1, 1000); // Lifecycle
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 10, statusValues, 1, 1000); // BUDG Status
            if (!automationValues.isEmpty()) addDropdownValidation(sheet, 13, automationValues, 1, 1000); // Automation Level
            if (!frequencyValues.isEmpty()) addDropdownValidation(sheet, 14, frequencyValues, 1, 1000); // Frequency
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 15, viewingValues, 1, 1000); // BUDG Viewing
        } else {
            // DELETE only needs ID
            createHeaderCell(headerRow, colIndex++, "ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Generate Glossary template
     */
    private void generateGlossaryTemplate(XSSFSheet sheet, Row headerRow, String templateType,
            Map<String, List<String>> lookupValues) {
        int colIndex = 0;
        List<String> viewingValues = lookupValues.getOrDefault("viewing", new ArrayList<>());
        List<String> statusValues = lookupValues.getOrDefault("status", new ArrayList<>());
        List<String> lifecycleValues = lookupValues.getOrDefault("glossary_lifecycle", new ArrayList<>());
        List<String> formatTypeValues = lookupValues.getOrDefault("glossary_format_type", new ArrayList<>());
        List<String> kdeValues = lookupValues.getOrDefault("glossary_kde_type", new ArrayList<>());
        List<String> securityClassificationValues = lookupValues.getOrDefault("security_classification", new ArrayList<>());
        List<String> typeValues = lookupValues.getOrDefault("glossary_type", new ArrayList<>());
        List<String> ciaRatingValues = lookupValues.getOrDefault("cia_rating", new ArrayList<>());
        List<String> roleValues = getRoleValuesByModuleName("Glossary");
        
        if ("INSERT".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "Name", true);
            createHeaderCell(headerRow, colIndex++, "Definition", true);
            createHeaderCell(headerRow, colIndex++, "Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Examples", false);
            createHeaderCell(headerRow, colIndex++, "Business Logic", false);
            createHeaderCell(headerRow, colIndex++, "Format Description", false);
            createHeaderCell(headerRow, colIndex++, "LDM Reference", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Format Type", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "KDE", false);
            createHeaderCell(headerRow, colIndex++, "Security Classification", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Type", true); // Mandatory
            createHeaderCell(headerRow, colIndex++, "Confidentiality", false);
            createHeaderCell(headerRow, colIndex++, "Integrity", false);
            createHeaderCell(headerRow, colIndex++, "Availability", false);
            createHeaderCell(headerRow, colIndex++, "Alias Names", false);
            createHeaderCell(headerRow, colIndex++, "User Email", false);
            createHeaderCell(headerRow, colIndex++, "User First Name", false);
            createHeaderCell(headerRow, colIndex++, "User Last Name", false);
            createHeaderCell(headerRow, colIndex++, "User Lan ID", false);
            createHeaderCell(headerRow, colIndex++, "Governance Role", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultGlossaryLifecycle = getDefaultGlossaryLifecycleValue();
            String defaultGlossaryFormatType = getDefaultGlossaryFormatTypeValue();
            String defaultSecurityClassification = getDefaultSecurityClassificationValue();
            String defaultGlossaryType = getDefaultGlossaryTypeValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Definition (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Ref.
            createCell(exampleRow, ex++, ""); // Examples
            createCell(exampleRow, ex++, ""); // Business Logic
            createCell(exampleRow, ex++, ""); // Format Description
            createCell(exampleRow, ex++, ""); // LDM Reference
            createCell(exampleRow, ex++, ""); // Parent Name
            createCell(exampleRow, ex++, ""); // Parent Ref.
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultGlossaryLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultGlossaryFormatType); // Format Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // KDE
            createCell(exampleRow, ex++, defaultSecurityClassification); // Security Classification (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultGlossaryType); // Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Confidentiality
            createCell(exampleRow, ex++, ""); // Integrity
            createCell(exampleRow, ex++, ""); // Availability
            createCell(exampleRow, ex++, ""); // Alias Names
            createCell(exampleRow, ex++, ""); // User Email
            createCell(exampleRow, ex++, ""); // User First Name
            createCell(exampleRow, ex++, ""); // User Last Name
            createCell(exampleRow, ex++, ""); // User Lan ID
            createCell(exampleRow, ex++, ""); // Governance Role
            
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 9, viewingValues, 1, 1000);
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 10, statusValues, 1, 1000);
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 11, lifecycleValues, 1, 1000);
            if (!formatTypeValues.isEmpty()) addDropdownValidation(sheet, 12, formatTypeValues, 1, 1000);
            if (!kdeValues.isEmpty()) addDropdownValidation(sheet, 13, kdeValues, 1, 1000);
            if (!securityClassificationValues.isEmpty()) addDropdownValidation(sheet, 14, securityClassificationValues, 1, 1000);
            if (!typeValues.isEmpty()) addDropdownValidation(sheet, 15, typeValues, 1, 1000);
            if (!ciaRatingValues.isEmpty()) {
                addDropdownValidation(sheet, 16, ciaRatingValues, 1, 1000);
                addDropdownValidation(sheet, 17, ciaRatingValues, 1, 1000);
                addDropdownValidation(sheet, 18, ciaRatingValues, 1, 1000);
            }
            if (!roleValues.isEmpty()) addDropdownValidation(sheet, 24, roleValues, 1, 1000);
        } else if ("UPDATE".equals(templateType)) {
            createHeaderCell(headerRow, colIndex++, "ID", false);
            createHeaderCell(headerRow, colIndex++, "Name", false);
            createHeaderCell(headerRow, colIndex++, "Definition", false);
            createHeaderCell(headerRow, colIndex++, "Ref.", false);
            createHeaderCell(headerRow, colIndex++, "Examples", false);
            createHeaderCell(headerRow, colIndex++, "Business Logic", false);
            createHeaderCell(headerRow, colIndex++, "Format Description", false);
            createHeaderCell(headerRow, colIndex++, "LDM Reference", false);
            createHeaderCell(headerRow, colIndex++, "Parent Name", false);
            createHeaderCell(headerRow, colIndex++, "Parent Ref.", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Viewing", false);
            createHeaderCell(headerRow, colIndex++, "BUDG Status", false);
            createHeaderCell(headerRow, colIndex++, "Lifecycle", false);
            createHeaderCell(headerRow, colIndex++, "Format Type", false);
            createHeaderCell(headerRow, colIndex++, "KDE", false);
            createHeaderCell(headerRow, colIndex++, "Security Classification", false);
            createHeaderCell(headerRow, colIndex++, "Type", false);
            createHeaderCell(headerRow, colIndex++, "Confidentiality", false);
            createHeaderCell(headerRow, colIndex++, "Integrity", false);
            createHeaderCell(headerRow, colIndex++, "Availability", false);
            createHeaderCell(headerRow, colIndex++, "Alias Names", false);
            
            Row exampleRow = sheet.createRow(1);
            String defaultViewing = getDefaultViewingValue();
            String defaultStatus = getDefaultStatusValue();
            String defaultGlossaryLifecycle = getDefaultGlossaryLifecycleValue();
            String defaultGlossaryFormatType = getDefaultGlossaryFormatTypeValue();
            String defaultSecurityClassification = getDefaultSecurityClassificationValue();
            String defaultGlossaryType = getDefaultGlossaryTypeValue();
            
            int ex = 0;
            createCell(exampleRow, ex++, ""); // ID (mandatory)
            createCell(exampleRow, ex++, ""); // Name (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Definition (mandatory TEXT - empty)
            createCell(exampleRow, ex++, ""); // Ref.
            createCell(exampleRow, ex++, ""); // Examples
            createCell(exampleRow, ex++, ""); // Business Logic
            createCell(exampleRow, ex++, ""); // Format Description
            createCell(exampleRow, ex++, ""); // LDM Reference
            createCell(exampleRow, ex++, ""); // Parent Name
            createCell(exampleRow, ex++, ""); // Parent Ref.
            createCell(exampleRow, ex++, defaultViewing); // BUDG Viewing
            createCell(exampleRow, ex++, defaultStatus); // BUDG Status
            createCell(exampleRow, ex++, defaultGlossaryLifecycle); // Lifecycle (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultGlossaryFormatType); // Format Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // KDE
            createCell(exampleRow, ex++, defaultSecurityClassification); // Security Classification (mandatory LIST - show default)
            createCell(exampleRow, ex++, defaultGlossaryType); // Type (mandatory LIST - show default)
            createCell(exampleRow, ex++, ""); // Confidentiality
            createCell(exampleRow, ex++, ""); // Integrity
            createCell(exampleRow, ex++, ""); // Availability
            createCell(exampleRow, ex++, ""); // Alias Names
            
            // List fields dropdown validations
            if (!viewingValues.isEmpty()) addDropdownValidation(sheet, 10, viewingValues, 1, 1000); // BUDG Viewing
            if (!statusValues.isEmpty()) addDropdownValidation(sheet, 11, statusValues, 1, 1000); // BUDG Status
            if (!lifecycleValues.isEmpty()) addDropdownValidation(sheet, 12, lifecycleValues, 1, 1000); // Lifecycle
            if (!formatTypeValues.isEmpty()) addDropdownValidation(sheet, 13, formatTypeValues, 1, 1000); // Format Type
            if (!kdeValues.isEmpty()) addDropdownValidation(sheet, 14, kdeValues, 1, 1000); // KDE
            if (!securityClassificationValues.isEmpty()) addDropdownValidation(sheet, 15, securityClassificationValues, 1, 1000); // Security Classification
            if (!typeValues.isEmpty()) addDropdownValidation(sheet, 16, typeValues, 1, 1000); // Type
            if (!ciaRatingValues.isEmpty()) {
                addDropdownValidation(sheet, 17, ciaRatingValues, 1, 1000); // Confidentiality
                addDropdownValidation(sheet, 18, ciaRatingValues, 1, 1000); // Integrity
                addDropdownValidation(sheet, 19, ciaRatingValues, 1, 1000); // Availability
            }
        } else {
            createHeaderCell(headerRow, colIndex++, "Glossary ID", true);
            Row exampleRow = sheet.createRow(1);
            createCell(exampleRow, 0, "");
        }
    }
    
    /**
     * Entity configuration helper class
     */
    private static class EntityConfig {
        private List<String> lookupTables = new ArrayList<>();
        
        public void addLookupTable(String tableName) {
            lookupTables.add(tableName);
        }
        
        public List<String> getLookupTables() {
            return lookupTables;
        }
        
        public boolean hasLookups() {
            return !lookupTables.isEmpty();
        }
    }
    
    /**
     * Get custom fields for a module
     * @param moduleId The module ID
     * @return List of custom field metadata maps
     * @throws SQLException if database error occurs
     */
    private List<Map<String, Object>> getCustomFieldsForModule(int moduleId) throws SQLException {
        List<Map<String, Object>> customFields = new ArrayList<>();
        
        String sql = """
            SELECT ID, DisplayName, CustomFieldName, DataType, is_Mandatory, 
                   Default_Value, Description, Placeholder_Text
            FROM Custom_Field_Metadata
            WHERE Module_ID = ?
            ORDER BY ID
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> field = new HashMap<>();
                    field.put("id", rs.getInt("ID"));
                    field.put("displayName", rs.getString("DisplayName"));
                    field.put("customFieldName", rs.getString("CustomFieldName"));
                    field.put("dataType", rs.getString("DataType"));
                    field.put("isMandatory", rs.getBoolean("is_Mandatory"));
                    field.put("defaultValue", rs.getString("Default_Value"));
                    field.put("description", rs.getString("Description"));
                    field.put("placeholderText", rs.getString("Placeholder_Text"));
                    customFields.add(field);
                }
            }
        }
        
        return customFields;
    }
    
    /**
     * Get enum values for a custom field (for dropdown/multiselect)
     * @param metadataId The custom field metadata ID
     * @return List of enum values
     * @throws SQLException if database error occurs
     */
    private List<String> getCustomFieldEnumValues(int metadataId) throws SQLException {
        List<String> enumValues = new ArrayList<>();
        
        String sql = """
            SELECT EnumValue
            FROM Custom_Field_Enum
            WHERE Custom_Field_Metadata_ID = ?
            ORDER BY EnumValue
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, metadataId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    enumValues.add(rs.getString("EnumValue"));
                }
            }
        }
        
        return enumValues;
    }
    
    /**
     * Add custom fields columns to template
     * @param sheet The Excel sheet
     * @param headerRow The header row
     * @param moduleId The module ID
     * @param templateType The template type (INSERT, UPDATE, DELETE)
     * @throws SQLException if database error occurs
     */
    private void addCustomFieldsColumns(XSSFSheet sheet, Row headerRow, int moduleId, String templateType) throws SQLException {
        // Only add custom fields for INSERT and UPDATE templates
        if (!"INSERT".equals(templateType) && !"UPDATE".equals(templateType)) {
            return;
        }
        
        List<Map<String, Object>> customFields = getCustomFieldsForModule(moduleId);
        if (customFields.isEmpty()) {
            return;
        }
        
        int colIndex = headerRow.getLastCellNum();
        if (colIndex < 0) {
            colIndex = 0;
        }
        
        // Get or create example row (row 1)
        Row exampleRow = sheet.getRow(1);
        if (exampleRow == null) {
            exampleRow = sheet.createRow(1);
        }
        
        for (Map<String, Object> field : customFields) {
            String displayName = (String) field.get("displayName");
            String dataType = (String) field.get("dataType");
            boolean isMandatory = (Boolean) field.get("isMandatory");
            String defaultValue = (String) field.get("defaultValue");
            String description = (String) field.get("description");
            String placeholderText = (String) field.get("placeholderText");
            int fieldId = (Integer) field.get("id");
            
            // Build header name with mandatory indicator
            String headerName = displayName;
            if (isMandatory) {
                headerName = displayName + " *";
            }
            
            // Create header cell
            createHeaderCell(headerRow, colIndex, headerName, isMandatory);
            
            // Build comment text
            StringBuilder commentBuilder = new StringBuilder();
            if (description != null && !description.trim().isEmpty()) {
                commentBuilder.append("Description: ").append(description);
            }
            if (placeholderText != null && !placeholderText.trim().isEmpty()) {
                if (commentBuilder.length() > 0) {
                    commentBuilder.append("\n");
                }
                commentBuilder.append("Placeholder: ").append(placeholderText);
            }
            if (isMandatory) {
                if (commentBuilder.length() > 0) {
                    commentBuilder.append("\n");
                }
                commentBuilder.append("Required: Yes");
            } else {
                if (commentBuilder.length() > 0) {
                    commentBuilder.append("\n");
                }
                commentBuilder.append("Required: No");
            }
            if ("dropdown".equalsIgnoreCase(dataType) || "multiselect".equalsIgnoreCase(dataType)) {
                try {
                    List<String> enumValues = getCustomFieldEnumValues(fieldId);
                    if (!enumValues.isEmpty()) {
                        if (commentBuilder.length() > 0) {
                            commentBuilder.append("\n");
                        }
                        commentBuilder.append("Allowed Values: ").append(String.join(", ", enumValues));
                    }
                    if ("multiselect".equalsIgnoreCase(dataType)) {
                        if (commentBuilder.length() > 0) {
                            commentBuilder.append("\n");
                        }
                        commentBuilder.append("Format: comma-separated values");
                    }
                } catch (SQLException e) {
                    logger.warn("Could not load enum values for custom field comment {}: {}", fieldId, e.getMessage());
                }
            }
            
            // Add comment if there's any text
            if (commentBuilder.length() > 0) {
                addCellComment(sheet, headerRow, colIndex, commentBuilder.toString());
            }
            
            // Add Excel data validation based on data type
            addCustomFieldValidation(sheet, colIndex, dataType, fieldId);
            
            // Add default value to example row if exists
            String exampleValue = "";
            if (defaultValue != null && !defaultValue.trim().isEmpty()) {
                exampleValue = defaultValue.trim();
            }
            createCell(exampleRow, colIndex, exampleValue);
            
            colIndex++;
        }
    }
    
    /**
     * Add Excel data validation for custom field based on data type
     * @param sheet The Excel sheet
     * @param colIndex The column index
     * @param dataType The data type (text, number, decimal, date, checkbox, time, percentage, dropdown, multiselect)
     * @param metadataId The custom field metadata ID (for dropdown/multiselect)
     */
    private void addCustomFieldValidation(XSSFSheet sheet, int colIndex, String dataType, int metadataId) {
        try {
            XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sheet);
            CellRangeAddressList addressList = new CellRangeAddressList(1, 1000, colIndex, colIndex);
            
            if ("number".equalsIgnoreCase(dataType)) {
                // Number validation - integer only
                DataValidationConstraint constraint = dvHelper.createNumericConstraint(
                    DataValidationConstraint.ValidationType.INTEGER,
                    DataValidationConstraint.OperatorType.BETWEEN,
                    "-2147483648", "2147483647"
                );
                XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, addressList);
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Invalid Number", "Please enter a valid integer number.");
                sheet.addValidationData(validation);
                
            } else if ("decimal".equalsIgnoreCase(dataType)) {
                // Decimal validation - decimal allowed, max 13 digits for integer part
                DataValidationConstraint constraint = dvHelper.createNumericConstraint(
                    DataValidationConstraint.ValidationType.DECIMAL,
                    DataValidationConstraint.OperatorType.BETWEEN,
                    "-9999999999999.999999999999", "9999999999999.999999999999"
                );
                XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, addressList);
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Invalid Decimal", "Please enter a valid decimal number. Integer part cannot exceed 13 digits.");
                sheet.addValidationData(validation);
                
            } else if ("date".equalsIgnoreCase(dataType)) {
                // Date validation
                DataValidationConstraint constraint = dvHelper.createDateConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    "1900-01-01", "2099-12-31",
                    "yyyy-MM-dd"
                );
                XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, addressList);
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Invalid Date", "Please enter a valid date (dd/mm/yyyy).");
                sheet.addValidationData(validation);
                
            } else if ("checkbox".equalsIgnoreCase(dataType)) {
                // Checkbox - treat as dropdown with true/false
                List<String> checkboxValues = Arrays.asList("true", "false");
                DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(checkboxValues.toArray(new String[0]));
                XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, addressList);
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Invalid Value", "Please select 'true' or 'false'.");
                sheet.addValidationData(validation);
                
            } else if ("time".equalsIgnoreCase(dataType)) {
                // Time validation - using text format with pattern
                DataValidationConstraint constraint = dvHelper.createTextLengthConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    "0", "8"
                );
                XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, addressList);
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Invalid Time", "Please enter a valid time (HH:mm:ss).");
                sheet.addValidationData(validation);
                
            } else if ("percentage".equalsIgnoreCase(dataType)) {
                // Percentage validation - 0 to 100
                DataValidationConstraint constraint = dvHelper.createNumericConstraint(
                    DataValidationConstraint.ValidationType.DECIMAL,
                    DataValidationConstraint.OperatorType.BETWEEN,
                    "0", "100"
                );
                XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, addressList);
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Invalid Percentage", "Please enter a percentage value between 0 and 100.");
                sheet.addValidationData(validation);
                
            } else if ("dropdown".equalsIgnoreCase(dataType)) {
                // Dropdown - enforce single value from enum list
                try {
                    List<String> enumValues = getCustomFieldEnumValues(metadataId);
                    if (!enumValues.isEmpty()) {
                        DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(enumValues.toArray(new String[0]));
                        XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, addressList);
                        validation.setShowErrorBox(true);
                        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                        validation.createErrorBox("Invalid Value", "Please select a value from the list.");
                        sheet.addValidationData(validation);
                    }
                } catch (SQLException e) {
                    logger.warn("Could not load enum values for custom field {}: {}", metadataId, e.getMessage());
                }
            } else if ("multiselect".equalsIgnoreCase(dataType)) {
                // Multiselect values are validated server-side to allow comma-separated combinations.
                // Keep a prompt only; do not force single-value dropdown validation in Excel.
                DataValidationConstraint constraint = dvHelper.createTextLengthConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    "0", "32767"
                );
                XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, addressList);
                validation.setShowErrorBox(false);
                validation.setShowPromptBox(true);
                validation.createPromptBox("Multiselect format", "Use comma-separated values (example: Value A, Value B).");
                sheet.addValidationData(validation);
            }
            // Text type - no special validation needed (free text)
            
        } catch (Exception e) {
            logger.warn("Could not add validation for custom field column {}: {}", colIndex, e.getMessage());
        }
    }
}

