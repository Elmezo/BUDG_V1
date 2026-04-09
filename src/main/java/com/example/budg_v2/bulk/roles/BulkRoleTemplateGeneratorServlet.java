package com.example.budg_v2.bulk.roles;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Servlet for generating dynamic Excel templates for Role bulk upload
 * Endpoint: /api/bulk/templates/generate-role/{roleEntity}/{templateType}
 * templateType: INSERT or DELETE (same template for both)
 * roleEntity: businessarearole, capabilityrole, etc.
 */
@WebServlet(urlPatterns = {"/api/bulk/templates/generate-role/*"})
public class BulkRoleTemplateGeneratorServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkRoleTemplateGeneratorServlet.class);
    private static final Gson gson = new Gson();
    
    // Role entity configuration
    private static final Map<String, RoleEntityConfig> ROLE_ENTITY_CONFIG = new HashMap<>();

    // Notes loaded from notes.json: Map<fileName, Map<headerName, noteText>>
    private static final Map<String, Map<String, String>> NOTES_BY_FILE_AND_HEADER = loadNotesFromJson();
    
    static {
        // Common descriptions for user identification columns
        String userEmailDesc = "User Email (optional). Provide this OR User Lan ID OR User First Name + User Last Name to identify the person.";
        String userFirstNameDesc = "User First Name (optional). Provide with User Last Name if Email/Lan ID are not provided.";
        String userLastNameDesc = "User Last Name (optional). Provide with User First Name if Email/Lan ID are not provided.";
        String userLanIdDesc = "User Lan ID (optional). Provide this OR User Email OR First+Last Name to identify the person.";
        String governanceRoleDesc = "Governance Role (required). Select a value from the dropdown list. Values are loaded dynamically from object_role for this module.";

        // Business Area Role
        ROLE_ENTITY_CONFIG.put("businessarearole", new RoleEntityConfig(
            "Business Area Role",
            "Business Area",
            new String[]{"Business Area Name", "Business Area Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{true, false, false, false, false, false, true}, // required flags
            new boolean[]{false, false, false, false, false, false, true}, // list flags (Governance Role)
            "business-area-role.xlsx",
            new String[]{
                "Business Area Name (required). Used to identify the Business Area to assign the role to.",
                "Business Area Parent Name (optional). Used to disambiguate when multiple Business Areas share the same name.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Capability Role
        ROLE_ENTITY_CONFIG.put("capabilityrole", new RoleEntityConfig(
            "Capability Role",
            "Capability",
            new String[]{"Capability Ref.", "Capability Name", "Parent Capability Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            "capability-role.xlsx",
            new String[]{
                "Capability Ref. (optional). Reference code of the Capability. Provide Ref. or Name.",
                "Capability Name (optional). Name of the Capability. Provide Ref. or Name.",
                "Parent Capability Name (optional). Used to disambiguate when multiple Capabilities share the same name.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Client Role
        ROLE_ENTITY_CONFIG.put("clientrole", new RoleEntityConfig(
            "Client Role",
            "Client",
            new String[]{"Client Name", "Client Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{true, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            "client-role.xlsx",
            new String[]{
                "Client Name (required). Used to identify the Client to assign the role to.",
                "Client Parent Name (optional). Used to disambiguate when multiple Clients share the same name.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Committee Role
        ROLE_ENTITY_CONFIG.put("committeerole", new RoleEntityConfig(
            "Committee Role",
            "Committee",
            new String[]{"Committee Ref.", "Committee Name", "Committee Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            "committee-role.xlsx",
            new String[]{
                "Committee Ref. (optional). Reference code of the Committee. Provide Ref. or Name.",
                "Committee Name (optional). Name of the Committee. Provide Ref. or Name.",
                "Committee Parent Name (optional). Used to disambiguate when multiple Committees share the same name.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Data Quality Role
        ROLE_ENTITY_CONFIG.put("dataqualityrole", new RoleEntityConfig(
            "Data Quality Role",
            "Data Quality",
            new String[]{"Ref.", "Rule Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            "data-quality-role.xlsx",
            new String[]{
                "Ref. (optional). Reference of the Data Quality rule. Provide Ref. or Rule Name.",
                "Rule Name (optional). Name of the Data Quality rule. Provide Ref. or Rule Name.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Data Set Role
        ROLE_ENTITY_CONFIG.put("datasetrole", new RoleEntityConfig(
            "Data Set Role",
            "Data Sets",
            new String[]{"Ref.", "Name", "System Short Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            "data-set-role.xlsx",
            new String[]{
                "Ref. (required if Name is empty). Reference of the Data Set. You must provide either Ref. or Name.",
                "Name (required if Ref. is empty). Name of the Data Set. You must provide either Ref. or Name.",
                "System Short Name (optional). Short name of the System that owns the Data Set (for disambiguation only; do not use instead of Ref. or Name).",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Glossary Role
        ROLE_ENTITY_CONFIG.put("glossaryrole", new RoleEntityConfig(
            "Glossary Role",
            "Glossary",
            new String[]{"Ref.", "Name", "Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            "glossary-role.xlsx",
            new String[]{
                "Ref. (optional). Reference of the Glossary term. Provide Ref. or Name.",
                "Name (optional). Name of the Glossary term. Provide Ref. or Name.",
                "Parent Name (optional). Parent Glossary term name (used for disambiguation).",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Interface Role
        ROLE_ENTITY_CONFIG.put("interfacerole", new RoleEntityConfig(
            "Interface Role",
            "Interface",
            new String[]{"Interface Ref.", "Interface Name", "Interface Source System Short Name", "Interface Target System Short Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, true, true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, false, true},
            "interface-role.xlsx",
            new String[]{
                "Interface Ref. (optional). Reference of the Interface. Provide Ref. or Name.",
                "Interface Name (optional). Name of the Interface. Provide Ref. or Name.",
                "Interface Source System Short Name (required). Short name of the source System.",
                "Interface Target System Short Name (required). Short name of the target System.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Legal Entity Role
        ROLE_ENTITY_CONFIG.put("legalentityrole", new RoleEntityConfig(
            "Legal Entity Role",
            "Legal Entity",
            new String[]{"Legal Entity Name", "Legal Entity Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{true, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            "legal-entity-role.xlsx",
            new String[]{
                "Legal Entity Name (required). Use the Legal Entity Short Name or Long Name, as stored in the system, to identify the Legal Entity.",
                "Legal Entity Parent Name (optional). Used to disambiguate when multiple Legal Entities share the same name.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Policy Role
        ROLE_ENTITY_CONFIG.put("policyrole", new RoleEntityConfig(
            "Policy Role",
            "Policy",
            new String[]{"Ref.", "Name", "Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            "policy-role.xlsx",
            new String[]{
                "Ref. (optional). Reference of the Policy. Provide Ref. or Name.",
                "Name (optional). Name of the Policy. Provide Ref. or Name.",
                "Parent Name (optional). Parent Policy name (used for disambiguation).",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Process Role
        ROLE_ENTITY_CONFIG.put("processrole", new RoleEntityConfig(
            "Process Role",
            "Process",
            new String[]{"Ref.", "Name", "Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            "process-role.xlsx",
            new String[]{
                "Ref. (optional). Reference of the Process. Provide Ref. or Name.",
                "Name (optional). Name of the Process. Provide Ref. or Name.",
                "Parent Name (optional). Parent Process name (used for disambiguation).",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Product Role
        ROLE_ENTITY_CONFIG.put("productrole", new RoleEntityConfig(
            "Product Role",
            "Product",
            new String[]{"Product Name", "Product Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{true, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            "product-role.xlsx",
            new String[]{
                "Product Name (required). Used to identify the Product to assign the role to.",
                "Product Parent Name (optional). Used to disambiguate when multiple Products share the same name.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Project Role
        ROLE_ENTITY_CONFIG.put("projectrole", new RoleEntityConfig(
            "Project Role",
            "Project",
            new String[]{"Project Ref.", "Project Name", "Project Parent Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            "project-role.xlsx",
            new String[]{
                "Project Ref. (optional). Reference of the Project. Provide Ref. or Name.",
                "Project Name (optional). Name of the Project. Provide Ref. or Name.",
                "Project Parent Name (optional). Parent Project name (used for disambiguation).",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // Regulation Role
        ROLE_ENTITY_CONFIG.put("regulationrole", new RoleEntityConfig(
            "Regulation Role",
            "Regulation",
            new String[]{"Regulation Ref.", "Regulation Name", "Parent Regulation Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            "regulation-role.xlsx",
            new String[]{
                "Regulation Ref. (optional). Reference of the Regulation. Provide Ref. or Name.",
                "Regulation Name (optional). Name of the Regulation. Provide Ref. or Name.",
                "Parent Regulation Name (optional). Parent Regulation name (used for disambiguation).",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
        
        // System Role
        ROLE_ENTITY_CONFIG.put("systemrole", new RoleEntityConfig(
            "System Role",
            "System",
            new String[]{"Short Name", "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"},
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            "system-role.xlsx",
            new String[]{
                "Short Name (required). Short name of the System to assign the role to.",
                userEmailDesc,
                userFirstNameDesc,
                userLastNameDesc,
                userLanIdDesc,
                governanceRoleDesc
            }
        ));
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        CorsUtil.setCorsHeaders(response);
        
        try {
            // Parse path: /api/bulk/templates/generate-role/{roleEntity}/{templateType}
            String pathInfo = request.getPathInfo();
            
            logger.info("Role template request - PathInfo: {}", pathInfo);
            
            if (pathInfo == null || pathInfo.equals("/")) {
                sendErrorResponse(response, "Missing role entity and template type in path", 400);
                return;
            }
            
            String[] pathParts = pathInfo.substring(1).split("/");
            
            if (pathParts.length != 2) {
                sendErrorResponse(response, "Invalid path format. Expected: /api/bulk/templates/generate-role/{roleEntity}/{templateType}", 400);
                return;
            }
            
            String roleEntity = pathParts[0].toLowerCase().replaceAll("\\s+", "").replaceAll("\\.", "");
            String templateType = pathParts[1].toUpperCase();
            
            // Validate template type
            if (!templateType.equals("INSERT") && !templateType.equals("DELETE")) {
                sendErrorResponse(response, "Invalid template type. Must be INSERT or DELETE", 400);
                return;
            }
            
            // Validate role entity
            if (!ROLE_ENTITY_CONFIG.containsKey(roleEntity)) {
                sendErrorResponse(response, "Unsupported role entity: " + roleEntity + ". Supported: " + ROLE_ENTITY_CONFIG.keySet(), 400);
                return;
            }
            
            logger.info("Generating role template - Entity: {}, Type: {}", roleEntity, templateType);
            
            // Generate Excel template
            XSSFWorkbook workbook = null;
            try {
                workbook = generateRoleTemplate(roleEntity, templateType);
                
                // Set response headers for file download
                String fileName = "TEMPLATE_" + templateType + ".xlsx";
                
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                
                // Write workbook to response
                try (OutputStream outputStream = response.getOutputStream()) {
                    workbook.write(outputStream);
                    outputStream.flush();
                    logger.info("Role template generated successfully: {} for {}", fileName, roleEntity);
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
            logger.error("Database error generating role template: {}", e.getMessage(), e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Error generating role template", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Generate Excel template for role assignment
     */
    private XSSFWorkbook generateRoleTemplate(String roleEntityKey, String templateType) throws SQLException {
        RoleEntityConfig config = ROLE_ENTITY_CONFIG.get(roleEntityKey);
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Determine sheet name
        String operation = templateType.equals("INSERT") ? "Create" : "Delete";
        String sheetName = operation + " " + config.displayName;
        
        XSSFSheet sheet = workbook.createSheet(sheetName);
        
        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle requiredHeaderStyle = createRequiredHeaderStyle(workbook);
        
        // Create header row
        Row headerRow = sheet.createRow(0);

        // Prepare for comments (notes) on header cells
        CreationHelper creationHelper = workbook.getCreationHelper();
        XSSFDrawing drawing = sheet.createDrawingPatriarch();

        for (int i = 0; i < config.columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(config.columns[i]);
            cell.setCellStyle(config.required[i] ? requiredHeaderStyle : headerStyle);

            // Resolve description for this header:
            // 1) Try to get from notes.json (per file & header)
            // 2) Fallback to static description from config (if any)
            String description = null;
            if (config.fileName != null) {
                Map<String, String> fileNotes = NOTES_BY_FILE_AND_HEADER.get(config.fileName);
                if (fileNotes != null) {
                    description = fileNotes.get(config.columns[i]);
                }
            }
            if ((description == null || description.isEmpty()) &&
                config.descriptions != null && i < config.descriptions.length) {
                description = config.descriptions[i];
            }

            // Add comment (note) for each header if description is available
            if (description != null && !description.isEmpty()) {
                ClientAnchor anchor = creationHelper.createClientAnchor();
                anchor.setCol1(i);
                anchor.setCol2(i + 5); // comment width

                // Approximate number of lines based on description length
                int approxCharsPerLine = 45; // أقل حروف في السطر → أسطر أكثر
                int lines = (description.length() / approxCharsPerLine) + 1;

                // زوّدنا الحد الأقصى للارتفاع عشان notes طويلة
                int rowsHigh = Math.min(20, 3 + lines);

                anchor.setRow1(0);
                anchor.setRow2(rowsHigh);

                XSSFComment comment = drawing.createCellComment(anchor);
                comment.setString(creationHelper.createRichTextString(description));
                cell.setCellComment(comment);
            }
        }
        
        // Get governance roles from database dynamically
        int moduleId = getModuleId(config.modulePrimaryName);
        List<String> governanceRoles = getGovernanceRoles(moduleId);
        
        if (!governanceRoles.isEmpty()) {
            // Create hidden sheet for governance roles
            String hiddenSheetName = "Hidden_GovernanceRoles";
            XSSFSheet hiddenSheet = workbook.createSheet(hiddenSheetName);
            workbook.setSheetHidden(workbook.getSheetIndex(hiddenSheet), true);
            
            // Populate hidden sheet with governance role values
            for (int i = 0; i < governanceRoles.size(); i++) {
                Row row = hiddenSheet.createRow(i);
                Cell cell = row.createCell(0);
                cell.setCellValue(governanceRoles.get(i));
            }
            
            // Find Governance Role column index
            int governanceRoleColIndex = -1;
            for (int i = 0; i < config.columns.length; i++) {
                if (config.columns[i].equals("Governance Role")) {
                    governanceRoleColIndex = i;
                    break;
                }
            }
            
            if (governanceRoleColIndex >= 0) {
                // Create data validation for Governance Role column
                String formula = hiddenSheetName + "!$A$1:$A$" + governanceRoles.size();
                XSSFDataValidationHelper validationHelper = new XSSFDataValidationHelper(sheet);
                XSSFDataValidationConstraint constraint = (XSSFDataValidationConstraint) 
                    validationHelper.createFormulaListConstraint(formula);
                
                // Apply validation to rows 2-1000 (row 1 is header)
                CellRangeAddressList addressList = new CellRangeAddressList(1, 1000, governanceRoleColIndex, governanceRoleColIndex);
                XSSFDataValidation validation = (XSSFDataValidation) validationHelper.createValidation(constraint, addressList);
                
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Invalid Input", "Please select a value from the dropdown list.");
                validation.setSuppressDropDownArrow(true);
                
                sheet.addValidationData(validation);
            }
        }
        
        // Auto-size columns
        for (int i = 0; i < config.columns.length; i++) {
            sheet.autoSizeColumn(i);
            // Add extra width for better readability
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
        }
        
        return workbook;
    }
    
    /**
     * Load notes definitions from notes.json into a map: Map<fileName, Map<headerName, noteText>>
     */
    private static Map<String, Map<String, String>> loadNotesFromJson() {
        Map<String, Map<String, String>> result = new HashMap<>();
        try {
            // Try to load from classpath resources
            java.io.InputStream is = BulkRoleTemplateGeneratorServlet.class
                    .getClassLoader()
                    .getResourceAsStream("roles-notes.json");

            if (is == null) {
                // Fallback: try relative file path from working directory
                Path path = Paths.get("src/main/resources/roles-notes.json");
                if (Files.exists(path)) {
                    is = Files.newInputStream(path);
                }
            }

            if (is == null) {
                logger.warn("roles-notes.json not found on classpath or expected path. Role header notes will fall back to static descriptions.");
                return result;
            }

            try (java.io.Reader reader = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8)) {
                NotesEntry[] entries = gson.fromJson(reader, NotesEntry[].class);
                if (entries != null) {
                    for (NotesEntry entry : entries) {
                        if (entry == null || entry.fileName == null || entry.headerName == null) continue;
                        String fileName = entry.fileName.trim();
                        String headerName = entry.headerName.trim();
                        String noteText = entry.noteText != null ? entry.noteText.trim() : "";
                        if (fileName.isEmpty() || headerName.isEmpty() || noteText.isEmpty()) continue;

                        result
                            .computeIfAbsent(fileName, k -> new HashMap<>())
                            .put(headerName, noteText);
                    }
                }
                logger.info("Loaded {} files of header notes from roles-notes.json", result.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load roles-notes.json for role templates: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Get module ID from database dynamically
     */
    private int getModuleId(String modulePrimaryName) throws SQLException {
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, modulePrimaryName);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        throw new SQLException("Module not found: " + modulePrimaryName);
    }
    
    /**
     * Get governance roles for a specific module from database
     */
    private List<String> getGovernanceRoles(int moduleId) throws SQLException {
        List<String> roles = new ArrayList<>();
        String sql = "SELECT primaryname FROM object_role WHERE module = ? ORDER BY primaryname";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, moduleId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roles.add(rs.getString("primaryname"));
                }
            }
        }
        
        logger.info("Loaded {} governance roles for module ID {}", roles.size(), moduleId);
        return roles;
    }
    
    /**
     * Create header cell style
     */
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Create required header cell style (red font)
     */
    private CellStyle createRequiredHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.RED.getIndex()); // Red color for required fields
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Send JSON error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("status", "error");
        errorResponse.addProperty("message", message);
        
        response.getWriter().write(gson.toJson(errorResponse));
    }
    
    /**
     * Configuration class for role entities
     */
    private static class RoleEntityConfig {
        String displayName;        // Display name for the role (e.g., "Business Area Role")
        String modulePrimaryName;  // Module primary name in database (e.g., "Business Area")
        String[] columns;          // Column headers
        boolean[] required;        // Required flags for each column
        @SuppressWarnings("unused")
        boolean[] isList;          // List/dropdown flags for each column
        String fileName;           // Excel template file name (for mapping to notes.json)
        String[] descriptions;     // Column descriptions (for Excel comments)
        
        RoleEntityConfig(String displayName, String modulePrimaryName, String[] columns, 
                        boolean[] required, boolean[] isList, String fileName, String[] descriptions) {
            this.displayName = displayName;
            this.modulePrimaryName = modulePrimaryName;
            this.columns = columns;
            this.required = required;
            this.isList = isList;
            this.fileName = fileName;
            this.descriptions = descriptions;
        }
    }

    /**
     * Helper DTO to map entries from notes.json
     */
    private static class NotesEntry {
        @SerializedName("File Name")
        String fileName;

        @SerializedName("Header Name")
        String headerName;

        @SerializedName("Note Text")
        String noteText;
    }
}

