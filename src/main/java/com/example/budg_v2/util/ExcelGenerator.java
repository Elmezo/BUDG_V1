package com.example.budg_v2.util;

import com.example.budg_v2.database.DatabaseConnection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationConstraint;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Utility class for generating Excel files for facets in bulk upload template
 * format
 */
public class ExcelGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ExcelGenerator.class);

    /**
     * Generate Excel file for a facet with data in bulk upload template format
     */
    public byte[] generateFacetExcel(String facet, List<Map<String, Object>> data, String timestamp) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            String normalizedFacet = normalizeFacetName(facet);
            XSSFSheet sheet = workbook.createSheet(getSheetName(normalizedFacet));

            // Get headers with mandatory flags for INSERT format
            List<HeaderInfo> headerInfos = getHeadersWithMandatoryFlags(normalizedFacet);

            // Create header row with colors (red for mandatory, grey for optional)
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headerInfos.size(); i++) {
                HeaderInfo headerInfo = headerInfos.get(i);
                createHeaderCellWithColor(headerRow, i, headerInfo.header, headerInfo.isMandatory, workbook);
            }

            // Create data rows
            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;
                for (HeaderInfo headerInfo : headerInfos) {
                    Cell cell = row.createCell(colNum++);
                    Object value = rowData.get(headerInfo.header);
                    setCellValue(cell, value);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headerInfos.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Add dropdown validations for list fields
            addDropdownValidationsForFacet(sheet, normalizedFacet, workbook);

            // Add List sheets for dropdowns
            addListSheetsForFacet(workbook, normalizedFacet);

            // Write to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();

            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("Error generating Excel for facet {}: {}", facet, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get facet data from database
     */
    public List<Map<String, Object>> getFacetData(String facet, List<Integer> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }

        // Normalize facet name to handle variations like DATA-SETS
        String normalizedFacet = normalizeFacetName(facet);

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = getQueryForFacet(normalizedFacet, ids.size());
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setParameters(ps, ids, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> results = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = mapResultSetToRow(rs, normalizedFacet);
                        results.add(row);
                    }
                    return results;
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting facet data for {}: {}", facet, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Normalize facet name (handle variations like DATA-SETS, data_sets, etc.)
     */
    private String normalizeFacetName(String facet) {
        if (facet == null)
            return null;

        // Convert to lowercase and replace hyphens/spaces with underscores
        String normalized = facet.toLowerCase().replace("-", "_").replace(" ", "_");

        // Handle specific variations
        switch (normalized) {
            case "data_sets":
            case "datasets":
                return "dataset";
            case "legalentity":
            case "legal":
                return "legal_entity";
            case "businessarea":
                return "business_area";
            case "regulatorytheme":
                return "regulatory_theme";
            case "orgunit":
            case "org-unit":
                return "org_unit";
            default:
                return normalized;
        }
    }

    /**
     * Header information with mandatory flag
     */
    private static class HeaderInfo {
        String header;
        boolean isMandatory;

        HeaderInfo(String header, boolean isMandatory) {
            this.header = header;
            this.isMandatory = isMandatory;
        }
    }

    /**
     * Create header cell with color (red for mandatory, grey for optional)
     */
    private void createHeaderCellWithColor(Row row, int colIndex, String value, boolean isMandatory,
            Workbook workbook) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);

        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
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
     * Get headers with mandatory flags for facet (INSERT format matching bulk
     * upload template)
     */
    private List<HeaderInfo> getHeadersWithMandatoryFlags(String facet) {
        List<HeaderInfo> headers = new ArrayList<>();
        String facetLower = facet.toLowerCase();

        switch (facetLower) {
            case "dataset":
                // Dataset INSERT format from BulkTemplateGeneratorServlet
                headers.add(new HeaderInfo("Ref.", false));
                headers.add(new HeaderInfo("Name", true)); // Mandatory
                headers.add(new HeaderInfo("Definition", true)); // Mandatory
                headers.add(new HeaderInfo("Usage", true)); // Mandatory
                headers.add(new HeaderInfo("BUDG Viewing", false));
                headers.add(new HeaderInfo("Type", true)); // Mandatory
                headers.add(new HeaderInfo("System ID", false));
                headers.add(new HeaderInfo("System Short Name", false));
                headers.add(new HeaderInfo("Parent System Short Name", false));
                headers.add(new HeaderInfo("Lifecycle", true)); // Mandatory
                headers.add(new HeaderInfo("BUDG Status", false));
                headers.add(new HeaderInfo("Glossary Ref.", false));
                headers.add(new HeaderInfo("Glossary Name", false));
                headers.add(new HeaderInfo("Parent Glossary Name", false));
                headers.add(new HeaderInfo("User Email", false));
                headers.add(new HeaderInfo("User First Name", false));
                headers.add(new HeaderInfo("User Last Name", false));
                headers.add(new HeaderInfo("User Lan ID", false));
                headers.add(new HeaderInfo("Governance Role", false));
                headers.add(new HeaderInfo("Segment", false));
                break;

            case "glossary":
                // Glossary INSERT format from BulkTemplateGeneratorServlet
                headers.add(new HeaderInfo("Name", true)); // Mandatory
                headers.add(new HeaderInfo("Definition", true)); // Mandatory
                headers.add(new HeaderInfo("Ref.", false));
                headers.add(new HeaderInfo("Examples", false));
                headers.add(new HeaderInfo("Business Logic", false));
                headers.add(new HeaderInfo("Format Description", false));
                headers.add(new HeaderInfo("LDM Reference", false));
                headers.add(new HeaderInfo("Parent Name", false));
                headers.add(new HeaderInfo("Parent Ref.", false));
                headers.add(new HeaderInfo("BUDG Viewing", false));
                headers.add(new HeaderInfo("BUDG Status", false));
                headers.add(new HeaderInfo("Lifecycle", true)); // Mandatory
                headers.add(new HeaderInfo("Format Type", true)); // Mandatory
                headers.add(new HeaderInfo("KDE", false));
                headers.add(new HeaderInfo("Security Classification", true)); // Mandatory
                headers.add(new HeaderInfo("Type", true)); // Mandatory
                headers.add(new HeaderInfo("Confidentiality", false));
                headers.add(new HeaderInfo("Integrity", false));
                headers.add(new HeaderInfo("Availability", false));
                headers.add(new HeaderInfo("Alias Names", false));
                headers.add(new HeaderInfo("User Email", false));
                headers.add(new HeaderInfo("User First Name", false));
                headers.add(new HeaderInfo("User Last Name", false));
                headers.add(new HeaderInfo("User Lan ID", false));
                headers.add(new HeaderInfo("Governance Role", false));
                headers.add(new HeaderInfo("Segment", false));
                break;

            case "system":
                // System INSERT format from BulkTemplateGeneratorServlet
                headers.add(new HeaderInfo("Short Name", true)); // Mandatory
                headers.add(new HeaderInfo("Long Name", true)); // Mandatory
                headers.add(new HeaderInfo("Asset ID", false));
                headers.add(new HeaderInfo("External", true)); // Mandatory
                headers.add(new HeaderInfo("Description", true)); // Mandatory
                headers.add(new HeaderInfo("URL", false));
                headers.add(new HeaderInfo("DQAutomation", false));
                headers.add(new HeaderInfo("Parent Short Name", false));
                headers.add(new HeaderInfo("BUDG Viewing", false));
                headers.add(new HeaderInfo("BUDG Status", false));
                headers.add(new HeaderInfo("Lifecycle", true)); // Mandatory
                headers.add(new HeaderInfo("Type", true)); // Mandatory
                headers.add(new HeaderInfo("Classification", false));
                headers.add(new HeaderInfo("Confidentiality", false));
                headers.add(new HeaderInfo("Integrity", false));
                headers.add(new HeaderInfo("Availability", false));
                headers.add(new HeaderInfo("User Email", false));
                headers.add(new HeaderInfo("User First Name", false));
                headers.add(new HeaderInfo("User Last Name", false));
                headers.add(new HeaderInfo("User Lan ID", false));
                headers.add(new HeaderInfo("Governance Role", false));
                headers.add(new HeaderInfo("Segment", false));
                break;

            case "attribute":
                // Attribute INSERT format (BUDG-compliant)
                headers.add(new HeaderInfo("Reference Number", false));
                headers.add(new HeaderInfo("Attribute Name", true)); // Mandatory
                headers.add(new HeaderInfo("Attribute Definition", true)); // Mandatory
                headers.add(new HeaderInfo("Key", false));
                headers.add(new HeaderInfo("Business Logic", false));
                headers.add(new HeaderInfo("Data Length", false));
                headers.add(new HeaderInfo("Data Type", false));
                headers.add(new HeaderInfo("Attribute Requirement", false));
                headers.add(new HeaderInfo("Data Set Ref.", false));
                headers.add(new HeaderInfo("Data Set Name", false));
                headers.add(new HeaderInfo("System Short Name", false));
                headers.add(new HeaderInfo("Glossary Ref.", false));
                headers.add(new HeaderInfo("Glossary Name", false));
                headers.add(new HeaderInfo("Parent Glossary Name", false));
                headers.add(new HeaderInfo("Origin", false));
                headers.add(new HeaderInfo("Editability", false));
                headers.add(new HeaderInfo("Editability Role", false));
                headers.add(new HeaderInfo("DB Field Name", false));
                headers.add(new HeaderInfo("User Email", false));
                headers.add(new HeaderInfo("User First Name", false));
                headers.add(new HeaderInfo("User Last Name", false));
                headers.add(new HeaderInfo("User Lan ID", false));
                headers.add(new HeaderInfo("Governance Role", false));
                break;

            case "process":
                // Process INSERT format from BulkTemplateGeneratorServlet
                headers.add(new HeaderInfo("Name", true)); // Mandatory
                headers.add(new HeaderInfo("Ref.", true)); // Mandatory
                headers.add(new HeaderInfo("Description", true)); // Mandatory
                headers.add(new HeaderInfo("Input Description", false));
                headers.add(new HeaderInfo("Output Description", false));
                headers.add(new HeaderInfo("Step Type", true)); // Mandatory
                headers.add(new HeaderInfo("Create Permission", false));
                headers.add(new HeaderInfo("Read Permission", false));
                headers.add(new HeaderInfo("Update Permission", false));
                headers.add(new HeaderInfo("Delete Permission", false));
                headers.add(new HeaderInfo("Archive Permission", false));
                headers.add(new HeaderInfo("Duration", false));
                headers.add(new HeaderInfo("Parent Name", false));
                headers.add(new HeaderInfo("Parent Ref.", false));
                headers.add(new HeaderInfo("BUDG Viewing", false));
                headers.add(new HeaderInfo("BUDG Status", false));
                headers.add(new HeaderInfo("Type", true)); // Mandatory
                headers.add(new HeaderInfo("Duration Type", false));
                headers.add(new HeaderInfo("Lifecycle", true)); // Mandatory
                headers.add(new HeaderInfo("Classification", false));
                headers.add(new HeaderInfo("Automation", false));
                headers.add(new HeaderInfo("User Email", false));
                headers.add(new HeaderInfo("User First Name", false));
                headers.add(new HeaderInfo("User Last Name", false));
                headers.add(new HeaderInfo("User Lan ID", false));
                headers.add(new HeaderInfo("Governance Role", false));
                headers.add(new HeaderInfo("Segment", false));
                break;

            case "project":
                // Project INSERT format from BulkTemplateGeneratorServlet
                headers.add(new HeaderInfo("Reference", false));
                headers.add(new HeaderInfo("Project Name", true)); // Mandatory
                headers.add(new HeaderInfo("Project Description", true)); // Mandatory
                headers.add(new HeaderInfo("Start Date", true)); // Mandatory
                headers.add(new HeaderInfo("End Date", true)); // Mandatory
                headers.add(new HeaderInfo("Parent Project Name", false));
                headers.add(new HeaderInfo("Parent Ref.", false));
                headers.add(new HeaderInfo("BUDG Viewing", false));
                headers.add(new HeaderInfo("RAG", true)); // Mandatory
                headers.add(new HeaderInfo("Classification", false));
                headers.add(new HeaderInfo("BUDG Status", false));
                headers.add(new HeaderInfo("Project Lifecycle", true)); // Mandatory
                headers.add(new HeaderInfo("Project Type", true)); // Mandatory
                headers.add(new HeaderInfo("User Email", false));
                headers.add(new HeaderInfo("User First Name", false));
                headers.add(new HeaderInfo("User Last Name", false));
                headers.add(new HeaderInfo("User Lan ID", false));
                headers.add(new HeaderInfo("Governance Role", false));
                headers.add(new HeaderInfo("Segment", false));
                break;

            case "regulation":
                // Regulation INSERT format from BulkTemplateGeneratorServlet
                headers.add(new HeaderInfo("Reference", false));
                headers.add(new HeaderInfo("Short Name", false));
                headers.add(new HeaderInfo("Regulation Long Name", true)); // Mandatory
                headers.add(new HeaderInfo("Description", true)); // Mandatory
                headers.add(new HeaderInfo("Additional Info", false));
                headers.add(new HeaderInfo("Publication Date", true)); // Mandatory
                headers.add(new HeaderInfo("Comments Date", false));
                headers.add(new HeaderInfo("Finalisation Date", false));
                headers.add(new HeaderInfo("Compliance Date", true)); // Mandatory
                headers.add(new HeaderInfo("Legal Advice", false));
                headers.add(new HeaderInfo("Parent Regulation Name", false));
                headers.add(new HeaderInfo("Parent Ref.", false));
                headers.add(new HeaderInfo("Regulation Maturity", true)); // Mandatory
                headers.add(new HeaderInfo("Regulation Probability", true)); // Mandatory
                headers.add(new HeaderInfo("BUDG Status", false));
                headers.add(new HeaderInfo("Legal Advice Type", false));
                headers.add(new HeaderInfo("Regulation Stage", true)); // Mandatory
                headers.add(new HeaderInfo("Compliance Level", true)); // Mandatory
                headers.add(new HeaderInfo("User Email", false));
                headers.add(new HeaderInfo("User First Name", false));
                headers.add(new HeaderInfo("User Last Name", false));
                headers.add(new HeaderInfo("User Lan ID", false));
                headers.add(new HeaderInfo("Governance Role", false));
                headers.add(new HeaderInfo("Segment", false));
                break;

            case "committee":
                // Committee INSERT format from BulkTemplateGeneratorServlet
                headers.add(new HeaderInfo("Ref.", false));
                headers.add(new HeaderInfo("Name", true)); // Mandatory
                headers.add(new HeaderInfo("Description", true)); // Mandatory
                headers.add(new HeaderInfo("Parent Name", false));
                headers.add(new HeaderInfo("Parent Ref.", false));
                headers.add(new HeaderInfo("BUDG Viewing", false));
                headers.add(new HeaderInfo("Classification", true)); // Mandatory
                headers.add(new HeaderInfo("BUDG Status", false));
                headers.add(new HeaderInfo("Lifecycle", true)); // Mandatory
                headers.add(new HeaderInfo("Committee Type", true)); // Mandatory
                headers.add(new HeaderInfo("User Email", false));
                headers.add(new HeaderInfo("User First Name", false));
                headers.add(new HeaderInfo("User Last Name", false));
                headers.add(new HeaderInfo("User Lan ID", false));
                headers.add(new HeaderInfo("Governance Role", false));
                headers.add(new HeaderInfo("Segment", false));
                break;

            case "policy":
                // Policy INSERT format matching SQL query aliases
                headers.add(new HeaderInfo("Primary Name", true)); // Mandatory - matches SQL alias 'Primary Name'
                headers.add(new HeaderInfo("refNumber", false));
                headers.add(new HeaderInfo("Description", false));
                headers.add(new HeaderInfo("ParentID", false));
                headers.add(new HeaderInfo("Parent Name", false));
                headers.add(new HeaderInfo("Parent Ref.", false));
                headers.add(new HeaderInfo("isPublic", false));
                headers.add(new HeaderInfo("Status", false));
                headers.add(new HeaderInfo("Lifecycle_Status", false));
                headers.add(new HeaderInfo("Policy_Type", false));
                headers.add(new HeaderInfo("EffectiveDate", false));
                headers.add(new HeaderInfo("EndDate", false));
                headers.add(new HeaderInfo("Internal", false));
                headers.add(new HeaderInfo("URL", false));
                headers.add(new HeaderInfo("Segment", false));
                break;

            case "org_unit":
                // Org Unit INSERT format
                headers.add(new HeaderInfo("Ref.", false));
                headers.add(new HeaderInfo("Name", true)); // Mandatory
                headers.add(new HeaderInfo("Description", false));
                headers.add(new HeaderInfo("Parent Name", false));
                headers.add(new HeaderInfo("BUDG Status", false));
                break;

            default:
                // For other facets, use existing headers (backward compatibility)
                List<String> oldHeaders = getHeadersForFacet(facet);
                for (String header : oldHeaders) {
                    headers.add(new HeaderInfo(header, false));
                }
                break;
        }

        return headers;
    }

    /**
     * Get headers for facet (INSERT format) - kept for backward compatibility
     */
    private List<String> getHeadersForFacet(String facet) {
        // Map facet names to their column headers based on bulk upload template format
        Map<String, List<String>> facetHeaders = new HashMap<>();

        // System headers (BUDG-compliant - NO People data)
        facetHeaders.put("system", Arrays.asList(
                "Short Name", "Long Name", "Description", "Asset ID", "URL", "External",
                "DQAutomation", "Parent Short Name", "BUDG Viewing", "BUDG Status",
                "Lifecycle", "Type", "Classification", "Confidentiality", "Integrity",
                "Availability", "Segment"));
        // REMOVED: User Email, User First Name, User Last Name, User Lan ID, Governance
        // Role
        // Reason: People data forbidden by BUDG rules

        // Dataset headers
        facetHeaders.put("dataset", Arrays.asList(
                "Primary Name", "Definition", "Usage", "Ref.", "System_ID", "Glossary_ID",
                "BUDG Status_ID", "Type_ID", "Lifecycle_ID", "BUDG Viewing_ID", "Segment"));

        // Glossary headers
        facetHeaders.put("glossary", Arrays.asList(
                "Name", "Description", "Format", "LDM", "Business_Logic", "Examples",
                "Ref_Number", "Parent_ID", "BUDG Status_ID", "Lifecycle_ID", "Type_ID", "Segment"));

        // Attribute headers (BUDG-compliant format)
        facetHeaders.put("attribute", Arrays.asList(
                "Reference Number", "Attribute Name", "Attribute Definition", "Key", "Business Logic",
                "Data Length", "Data Type", "Attribute Requirement", "Data Set Ref.", "Data Set Name",
                "System Short Name", "Glossary Ref.", "Glossary Name", "Parent Glossary Name",
                "Origin", "Editability", "Editability Role", "DB Field Name",
                "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role"));

        // Policy headers
        facetHeaders.put("policy", Arrays.asList(
                "Primary Name", "refNumber", "Description", "ParentID", "Parent Name", "Parent Ref.", "isPublic",
                "Status", "Lifecycle_Status", "Policy_Type", "EffectiveDate", "EndDate", "Internal", "URL", "Segment"));

        // Process headers
        facetHeaders.put("process", Arrays.asList(
                "primaryname", "refnumber", "description", "parentid", "ispublic",
                "status", "type", "lifecycle_status", "processclass_id", "processautomation_id",
                "duration_type", "input_description", "output_description", "duration", "Segment"));

        // Project headers
        facetHeaders.put("project", Arrays.asList(
                "primaryname", "refnumber", "description", "parentid", "is_public",
                "rag", "classification", "status", "lifecycle_status", "project_type",
                "startdate", "enddate", "Segment"));

        // Product headers
        facetHeaders.put("product", Arrays.asList(
                "primaryname", "longname", "refnumber", "description", "parent_id",
                "is_public", "status", "lifecycle_status", "Segment"));

        // Client headers
        facetHeaders.put("client", Arrays.asList(
                "PrimaryName", "LongName", "Description", "Parent Name", "Parent Ref.", "IsPublic",
                "Status", "Lifecycle", "Segment"));

        // Legal Entity headers
        facetHeaders.put("legal_entity", Arrays.asList(
                "ShortName", "LongName", "Description", "Parent Name", "Parent Ref.", "Is_Public", "Status",
                "Segment"));

        // Capability headers
        facetHeaders.put("capability", Arrays.asList(
                "PrimaryName", "RefNumber", "Description", "Parent Name", "Parent Ref.", "Is_Public",
                "Status", "Lifecycle", "Capability_Type", "Classification", "Segment"));

        // Business Area headers
        facetHeaders.put("business_area", Arrays.asList(
                "PrimaryName", "Description", "Parent Name", "Parent Ref.", "Is_Public", "Status", "Lifecycle",
                "Segment"));

        // Committee headers
        facetHeaders.put("committee", Arrays.asList(
                "PrimaryName", "RefNumber", "Description", "Parent Name", "Parent Ref.", "Is_Public",
                "Status", "Lifecycle", "Committee_Type", "Classification", "Segment"));

        // Interface headers
        facetHeaders.put("interface", Arrays.asList(
                "Name", "Ref_number", "Description", "Source_systemID", "Target_systemID",
                "Transfer_Method_ID", "Transfer_Format_ID", "Classification_id", "Lifecycle_id",
                "status_id", "Automation_ID", "Frequency_ID", "is_public", "Asset_ID", "Synchronisation_Control",
                "Segment"));

        // Regulation headers
        facetHeaders.put("regulation", Arrays.asList(
                "primaryName", "ShortName", "RefNumber", "Description", "Parent Name", "Parent Ref.", "Is_Public",
                "Rank", "PublicationDate", "CommentsDate", "FinalisationDate", "ComplianceDate",
                "LegalAdvice", "RegulationMaturity_ID", "RegulationProbability_ID", "RegulationStatus_ID",
                "RegulationImpactRating_ID", "LegalAdviceType_ID", "RegulationStage_ID", "ComplianceLevel_ID",
                "Segment"));

        // Geography headers
        facetHeaders.put("geography", Arrays.asList(
                "PrimaryName", "Description", "ParentID", "Segment"));

        // Regulator headers
        facetHeaders.put("regulator", Arrays.asList(
                "PrimaryName", "ShortName", "Description", "Segment"));

        // Regulatory Theme headers
        facetHeaders.put("regulatory_theme", Arrays.asList(
                "PrimaryName", "ShortName", "RefNumber", "Description", "Parent_ID", "Status_ID", "Segment"));

        // Org Unit headers
        facetHeaders.put("org_unit", Arrays.asList(
                "Ref.", "Name", "Description", "Parent Name", "BUDG Status"));

        // For now, return a default set if facet not found
        return facetHeaders.getOrDefault(facet.toLowerCase(), Arrays.asList("ID", "Name"));
    }

    /**
     * Get SQL query for facet
     */
    private String getQueryForFacet(String facet, int idCount) {
        String inClause = String.join(",", Collections.nCopies(idCount, "?"));

        switch (facet.toLowerCase()) {
            case "system":
                return "SELECT s.id, s.Name AS 'Short Name', s.Long_Name AS 'Long Name', " +
                        "s.AssetID AS 'Asset ID', " +
                        "CASE WHEN s.External = 1 THEN 'TRUE' ELSE 'FALSE' END AS 'External', " +
                        "s.Description, s.URL, " +
                        "CASE WHEN s.DQ_Automation = 1 THEN 'TRUE' ELSE 'FALSE' END AS 'DQAutomation', " +
                        "parent.Name AS 'Parent Short Name', " +
                        "v.Name AS 'BUDG Viewing', st.primaryname AS 'BUDG Status', " +
                        "sl.name AS 'Lifecycle', stype.name AS 'Type', " +
                        "sc.Name AS 'Classification', " +
                        "cia_conf.Values AS 'Confidentiality', " +
                        "cia_int.Values AS 'Integrity', " +
                        "cia_avail.Values AS 'Availability', " +
                        "NULL AS 'User Email', " +
                        "NULL AS 'User First Name', " +
                        "NULL AS 'User Last Name', " +
                        "NULL AS 'User Lan ID', " +
                        "NULL AS 'Governance Role', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = s.id AND sot.Type = 'System' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM system s " +
                        "LEFT JOIN system parent ON parent.id = s.parent_id " +
                        "LEFT JOIN viewing v ON v.id = s.is_Public " +
                        "LEFT JOIN status st ON st.ID = s.status " +
                        "LEFT JOIN system_lifecycle sl ON sl.ID = s.Lifecycle " +
                        "LEFT JOIN system_type stype ON stype.ID = s.Type " +
                        "LEFT JOIN system_classification sc ON sc.ID = s.Classification " +
                        "LEFT JOIN cia_rating cia_conf ON cia_conf.ID = s.Confidentiality_Rating " +
                        "LEFT JOIN cia_rating cia_int ON cia_int.ID = s.Integrity_Rating " +
                        "LEFT JOIN cia_rating cia_avail ON cia_avail.ID = s.Availability_Rating " +
                        "WHERE s.id IN (" + inClause + ") AND s.Deleted_datetime IS NULL";
            // REMOVED: All People-related subqueries (User Email, First Name, Last Name,
            // Lan ID, Governance Role)
            // Reason: BUDG forbids People data in bulk migrate

            case "dataset":
                return "SELECT d.ID, d.RefNumber AS 'Ref.', d.PrimaryName AS 'Name', " +
                        "d.definition AS 'Definition', d.Usage, " +
                        "v.Name AS 'BUDG Viewing', " +
                        "dt.PrimaryName AS 'Type', " +
                        "s.id AS 'System ID', " +
                        "s.Name AS 'System Short Name', " +
                        "parent_sys.Name AS 'Parent System Short Name', " +
                        "dl.PrimaryName AS 'Lifecycle', " +
                        "st.primaryname AS 'BUDG Status', " +
                        "g.Ref_Number AS 'Glossary Ref.', " +
                        "g.Name AS 'Glossary Name', " +
                        "parent_gloss.Name AS 'Parent Glossary Name', " +
                        "NULL AS 'User Email', " +
                        "NULL AS 'User First Name', " +
                        "NULL AS 'User Last Name', " +
                        "NULL AS 'User Lan ID', " +
                        "NULL AS 'Governance Role', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = d.ID AND sot.Type = 'Dataset' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM dataset d " +
                        "LEFT JOIN system s ON s.id = d.MasterSource " +
                        "LEFT JOIN system parent_sys ON parent_sys.id = s.parent_id " +
                        "LEFT JOIN glossary g ON g.ID = d.glossary " +
                        "LEFT JOIN glossary parent_gloss ON parent_gloss.ID = g.Parent_ID " +
                        "LEFT JOIN status st ON st.ID = d.status " +
                        "LEFT JOIN dataset_type dt ON dt.ID = d.DatasetType " +
                        "LEFT JOIN dataset_lifecycle dl ON dl.ID = d.lifecycle " +
                        "LEFT JOIN viewing v ON v.id = d.AccessControlType " +
                        "WHERE d.ID IN (" + inClause + ") AND d.DeletedDatetime IS NULL";

            case "glossary":
                return "SELECT g.ID, g.Name, g.Description, " +
                        "g.Ref_Number AS 'Ref.', g.Examples, " +
                        "g.Business_Logic AS 'Business Logic', " +
                        "g.Format AS 'Format Description', " +
                        "g.LDM AS 'LDM Reference', " +
                        "parent.Name AS 'Parent Name', " +
                        "parent.Ref_Number AS 'Parent Ref.', " +
                        "v.Name AS 'BUDG Viewing', " +
                        "st.primaryname AS 'BUDG Status', " +
                        "gl.Name AS 'Lifecycle', " +
                        "gft.Name AS 'Format Type', " +
                        "gkde.Name AS 'KDE', " +
                        "sc.Name AS 'Security Classification', " +
                        "gt.Name AS 'Type', " +
                        "cia_conf.Values AS 'Confidentiality', " +
                        "cia_int.Values AS 'Integrity', " +
                        "cia_avail.Values AS 'Availability', " +
                        "(SELECT GROUP_CONCAT(gan.Name ORDER BY gan.Name SEPARATOR ', ') " +
                        " FROM glossary_alias_names gan " +
                        " WHERE gan.Glossary_id = g.ID) AS 'Alias Names', " +
                        "NULL AS 'User Email', " +
                        "NULL AS 'User First Name', " +
                        "NULL AS 'User Last Name', " +
                        "NULL AS 'User Lan ID', " +
                        "NULL AS 'Governance Role', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM glossary g " +
                        "LEFT JOIN status st ON st.ID = g.Status " +
                        "LEFT JOIN glossary_lifecycle gl ON gl.ID = g.Lifecycle " +
                        "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                        "LEFT JOIN glossary_format_type gft ON gft.ID = g.Format_type " +
                        "LEFT JOIN glossary_kde_type gkde ON gkde.ID = g.KDE " +
                        "LEFT JOIN security_classification sc ON sc.ID = g.Security_Classification " +
                        "LEFT JOIN viewing v ON v.id = g.Is_Public " +
                        "LEFT JOIN glossary parent ON parent.ID = g.Parent_ID " +
                        "LEFT JOIN cia_rating cia_conf ON cia_conf.ID = g.Confidentiality_Rating " +
                        "LEFT JOIN cia_rating cia_int ON cia_int.ID = g.Integrity_Rating " +
                        "LEFT JOIN cia_rating cia_avail ON cia_avail.ID = g.Availability_Rating " +
                        "WHERE g.ID IN (" + inClause + ") AND g.Deleted_datetime IS NULL";

            case "attribute":
                return "SELECT a.ID, " +
                        "a.RefNumber AS 'Reference Number', " +
                        "a.PrimaryName AS 'Attribute Name', " +
                        "a.Definition AS 'Attribute Definition', " +
                        "CASE WHEN a.Is_PrimaryKey = 1 THEN 'TRUE' ELSE 'FALSE' END AS 'Key', " +
                        "a.Business_Logic AS 'Business Logic', " +
                        "a.DataLength AS 'Data Length', " +
                        "dt.PrimaryName AS 'Data Type', " +
                        "r.PrimaryName AS 'Attribute Requirement', " +
                        "d.RefNumber AS 'Data Set Ref.', " +
                        "d.PrimaryName AS 'Data Set Name', " +
                        "s.Name AS 'System Short Name', " +
                        "g.Ref_Number AS 'Glossary Ref.', " +
                        "g.Name AS 'Glossary Name', " +
                        "parent_gloss.Name AS 'Parent Glossary Name', " +
                        "ao.PrimaryName AS 'Origin', " +
                        "ae.PrimaryName AS 'Editability', " +
                        "aer.PrimaryName AS 'Editability Role', " +
                        "(SELECT aan.Name FROM attribute_alias_name aan " +
                        " WHERE aan.AttributeID = a.ID AND aan.Name_Type = 1 LIMIT 1) AS 'DB Field Name', " +
                        "p.Email AS 'User Email', " +
                        "p.First_Name AS 'User First Name', " +
                        "p.Last_Name AS 'User Last Name', " +
                        "pd.lan_id AS 'User Lan ID', " +
                        "orl.primaryname AS 'Governance Role' " +
                        "FROM attribute a " +
                        "LEFT JOIN dataset d ON d.ID = a.Dataset_ID " +
                        "LEFT JOIN system s ON s.id = d.MasterSource " +
                        "LEFT JOIN glossary g ON g.ID = a.Glossary_ID " +
                        "LEFT JOIN glossary parent_gloss ON parent_gloss.ID = g.Parent_ID " +
                        "LEFT JOIN attribute_datatype dt ON dt.ID = a.Data_type_ID " +
                        "LEFT JOIN requirement r ON r.ID = a.Requirement_ID " +
                        "LEFT JOIN attribute_origination ao ON ao.ID = a.Origination " +
                        "LEFT JOIN attribute_editability ae ON ae.ID = a.Editability " +
                        "LEFT JOIN attribute_edit_role aer ON aer.ID = a.Editability_role " +
                        "LEFT JOIN attribute_x_objectxpeople axo ON axo.AttributeID = a.ID " +
                        "LEFT JOIN object_x_people oxp ON oxp.id = axo.Object_x_ipid " +
                        "LEFT JOIN people p ON p.ID = oxp.ipid " +
                        "LEFT JOIN people_details pd ON pd.id = p.ip_details " +
                        "LEFT JOIN object_role orl ON orl.id = oxp.roleID " +
                        "WHERE a.ID IN (" + inClause + ") AND a.DeletedDatetime IS NULL";

            case "policy":
                return "SELECT p.ID, p.PrimaryName AS 'Primary Name', p.refNumber, p.Description, p.ParentID, " +
                        "parent.PrimaryName AS 'Parent Name', " +
                        "parent.refNumber AS 'Parent Ref.', " +
                        "v.Name AS 'isPublic', " +
                        "st.primaryname AS 'Status', " +
                        "pl.PrimaryName AS 'Lifecycle_Status', " +
                        "pt.PrimaryName AS 'Policy_Type', " +
                        "p.EffectiveDate, p.EndDate, p.Internal, p.URL, " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = p.ID AND sot.Type = 'Policy' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM policy p " +
                        "LEFT JOIN policy parent ON parent.ID = p.ParentID " +
                        "LEFT JOIN viewing v ON v.id = p.isPublic " +
                        "LEFT JOIN status st ON st.ID = p.Status " +
                        "LEFT JOIN policy_lifecycle_status pl ON pl.ID = p.Lifecycle_Status " +
                        "LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                        "WHERE p.ID IN (" + inClause + ") AND p.DeletedDatetime IS NULL";

            case "process":
                return "SELECT pr.id, " +
                        "pr.primaryname AS 'Name', " +
                        "pr.refnumber AS 'Ref.', " +
                        "pr.description AS 'Description', " +
                        "pr.input_description AS 'Input Description', " +
                        "pr.output_description AS 'Output Description', " +
                        "pst.PrimaryName AS 'Step Type', " +
                        "CASE WHEN pr.cancreate = 1 THEN 'yes' WHEN pr.cancreate = 0 THEN 'no' ELSE '' END AS 'Create Permission', "
                        +
                        "CASE WHEN pr.canread = 1 THEN 'yes' WHEN pr.canread = 0 THEN 'no' ELSE '' END AS 'Read Permission', "
                        +
                        "CASE WHEN pr.canupdate = 1 THEN 'yes' WHEN pr.canupdate = 0 THEN 'no' ELSE '' END AS 'Update Permission', "
                        +
                        "CASE WHEN pr.candelete = 1 THEN 'yes' WHEN pr.candelete = 0 THEN 'no' ELSE '' END AS 'Delete Permission', "
                        +
                        "CASE WHEN pr.canarchive = 1 THEN 'yes' WHEN pr.canarchive = 0 THEN 'no' ELSE '' END AS 'Archive Permission', "
                        +
                        "pr.duration AS 'Duration', " +
                        "parent.primaryname AS 'Parent Name', " +
                        "parent.refnumber AS 'Parent Ref.', " +
                        "v.Name AS 'BUDG Viewing', " +
                        "st.primaryname AS 'BUDG Status', " +
                        "pt.PrimaryName AS 'Type', " +
                        "pdt.PrimaryName AS 'Duration Type', " +
                        "pl.PrimaryName AS 'Lifecycle', " +
                        "pc.PrimaryName AS 'Classification', " +
                        "pa.PrimaryName AS 'Automation', " +
                        "p.Email AS 'User Email', " +
                        "p.First_Name AS 'User First Name', " +
                        "p.Last_Name AS 'User Last Name', " +
                        "pd.lan_id AS 'User Lan ID', " +
                        "orl.primaryname AS 'Governance Role', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = pr.id AND sot.Type = 'Process' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM process pr " +
                        "LEFT JOIN process parent ON parent.id = pr.parentid " +
                        "LEFT JOIN viewing v ON v.id = pr.ispublic " +
                        "LEFT JOIN status st ON st.ID = pr.status " +
                        "LEFT JOIN process_type pt ON pt.ID = pr.type " +
                        "LEFT JOIN process_lifecycle_status pl ON pl.id = pr.lifecycle_status " +
                        "LEFT JOIN process_class pc ON pc.ID = pr.processclass_id " +
                        "LEFT JOIN process_automation pa ON pa.ID = pr.processautomation_id " +
                        "LEFT JOIN process_step_type pst ON pst.id = pr.step_type " +
                        "LEFT JOIN process_duration_type pdt ON pdt.ID = pr.duration_type " +
                        "LEFT JOIN process_x_objectxpeople pxo ON pxo.process_id = pr.id " +
                        "LEFT JOIN object_x_people oxp ON oxp.id = pxo.object_x_ip " +
                        "LEFT JOIN people p ON p.ID = oxp.ipid " +
                        "LEFT JOIN people_details pd ON pd.id = p.ip_details " +
                        "LEFT JOIN object_role orl ON orl.id = oxp.roleID " +
                        "WHERE pr.id IN (" + inClause + ") AND pr.deleteddatetime IS NULL";

            case "project":
                return "SELECT proj.id, " +
                        "proj.refnumber AS 'Reference', " +
                        "proj.primaryname AS 'Project Name', " +
                        "proj.description AS 'Project Description', " +
                        "proj.startdate AS 'Start Date', " +
                        "proj.enddate AS 'End Date', " +
                        "parent.primaryname AS 'Parent Project Name', " +
                        "parent.refnumber AS 'Parent Ref.', " +
                        "v.Name AS 'BUDG Viewing', " +
                        "pr.PrimaryName AS 'RAG', " +
                        "pc.PrimaryName AS 'Classification', " +
                        "st.primaryname AS 'BUDG Status', " +
                        "pl.PrimaryName AS 'Project Lifecycle', " +
                        "pt.PrimaryName AS 'Project Type', " +
                        "NULL AS 'User Email', " +
                        "NULL AS 'User First Name', " +
                        "NULL AS 'User Last Name', " +
                        "NULL AS 'User Lan ID', " +
                        "NULL AS 'Governance Role', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = proj.id AND sot.Type = 'Project' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM project proj " +
                        "LEFT JOIN project parent ON parent.id = proj.parentid " +
                        "LEFT JOIN viewing v ON v.id = proj.is_public " +
                        "LEFT JOIN project_rag pr ON pr.ID = proj.rag " +
                        "LEFT JOIN project_classification pc ON pc.ID = proj.classification " +
                        "LEFT JOIN status st ON st.ID = proj.status " +
                        "LEFT JOIN project_lifecycle pl ON pl.ID = proj.lifecycle_status " +
                        "LEFT JOIN project_type pt ON pt.ID = proj.project_type " +
                        "WHERE proj.id IN (" + inClause + ") AND proj.deletedatetime IS NULL";

            case "product":
                return "SELECT prod.id, prod.primaryname, prod.longname, prod.refnumber, prod.description, " +
                        "parent.primaryname AS 'parent name', " +
                        "parent.refnumber AS 'parent ref.', " +
                        "v.Name AS 'is_public', " +
                        "st.primaryname AS 'status', " +
                        "pl.PrimaryName AS 'lifecycle_status', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = prod.id AND sot.Type = 'Product' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM product prod " +
                        "LEFT JOIN product parent ON parent.id = prod.parent_id " +
                        "LEFT JOIN viewing v ON v.id = prod.is_public " +
                        "LEFT JOIN status st ON st.ID = prod.status " +
                        "LEFT JOIN product_lifecycle pl ON pl.ID = prod.lifecycle_status " +
                        "WHERE prod.id IN (" + inClause + ") AND prod.deleteddatetime IS NULL";

            case "client":
                return "SELECT c.ID, c.PrimaryName, c.LongName, c.Description, " +
                        "parent.PrimaryName AS 'Parent Name', " +
                        "parent.PrimaryName AS 'Parent Ref.', " +
                        "v.Name AS 'IsPublic', " +
                        "st.primaryname AS 'Status', " +
                        "cl.PrimaryName AS 'Lifecycle', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = c.ID AND sot.Type = 'Client' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM client c " +
                        "LEFT JOIN client parent ON parent.ID = c.Parent_ID " +
                        "LEFT JOIN viewing v ON v.id = c.IsPublic " +
                        "LEFT JOIN status st ON st.ID = c.Status " +
                        "LEFT JOIN client_lifecycle cl ON cl.ID = c.Lifecycle " +
                        "WHERE c.ID IN (" + inClause + ") AND c.DeleteDatetime IS NULL";

            case "legal_entity":
            case "legalentity":
                return "SELECT l.ID, l.ShortName, l.LongName, l.Description, " +
                        "parent.ShortName AS 'Parent Name', " +
                        "parent.ShortName AS 'Parent Ref.', " +
                        "v.Name AS 'Is_Public', " +
                        "st.primaryname AS 'Status', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = l.ID AND sot.Type = 'LegalEntity' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM legal l " +
                        "LEFT JOIN legal parent ON parent.ID = l.Parent_ID " +
                        "LEFT JOIN viewing v ON v.id = l.Is_Public " +
                        "LEFT JOIN status st ON st.ID = l.Status " +
                        "WHERE l.ID IN (" + inClause + ") AND l.DeleteDatetime IS NULL";

            case "capability":
                return "SELECT cap.ID, cap.PrimaryName, cap.RefNumber, cap.Description, " +
                        "parent.PrimaryName AS 'Parent Name', " +
                        "parent.RefNumber AS 'Parent Ref.', " +
                        "v.Name AS 'Is_Public', " +
                        "st.primaryname AS 'Status', " +
                        "cl.PrimaryName AS 'Lifecycle', " +
                        "ct.PrimaryName AS 'Capability_Type', " +
                        "cc.PrimaryName AS 'Classification', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = cap.ID AND sot.Type = 'Capability' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM capability cap " +
                        "LEFT JOIN capability parent ON parent.ID = cap.Parent_ID " +
                        "LEFT JOIN viewing v ON v.id = cap.Is_Public " +
                        "LEFT JOIN status st ON st.ID = cap.Status " +
                        "LEFT JOIN capability_lifecyle cl ON cl.ID = cap.Lifecycle " +
                        "LEFT JOIN capability_type ct ON ct.ID = cap.Capability_Type " +
                        "LEFT JOIN capability_classification cc ON cc.ID = cap.Classification " +
                        "WHERE cap.ID IN (" + inClause + ") AND cap.DeletedDatetime IS NULL";

            case "business_area":
                return "SELECT ba.ID, ba.PrimaryName, ba.Description, " +
                        "parent.PrimaryName AS 'Parent Name', " +
                        "parent.PrimaryName AS 'Parent Ref.', " +
                        "v.Name AS 'Is_Public', " +
                        "st.primaryname AS 'Status', " +
                        "bl.PrimaryName AS 'Lifecycle', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = ba.ID AND sot.Type = 'BusinessArea' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM business_area ba " +
                        "LEFT JOIN business_area parent ON parent.ID = ba.Parent_ID " +
                        "LEFT JOIN viewing v ON v.id = ba.Is_Public " +
                        "LEFT JOIN status st ON st.ID = ba.Status " +
                        "LEFT JOIN business_area_lifecycle bl ON bl.ID = ba.Lifecycle " +
                        "WHERE ba.ID IN (" + inClause + ") AND ba.deletedatetime IS NULL";

            case "committee":
                return "SELECT com.ID, com.PrimaryName, com.RefNumber, com.Description, " +
                        "parent.PrimaryName AS 'Parent Name', " +
                        "parent.RefNumber AS 'Parent Ref.', " +
                        "v.Name AS 'Is_Public', " +
                        "st.primaryname AS 'Status', " +
                        "cl.PrimaryName AS 'Lifecycle', " +
                        "ct.PrimaryName AS 'Committee_Type', " +
                        "cc.PrimaryName AS 'Classification', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = com.ID AND sot.Type = 'Committee' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM committee com " +
                        "LEFT JOIN committee parent ON parent.ID = com.Parent_ID " +
                        "LEFT JOIN viewing v ON v.id = com.Is_Public " +
                        "LEFT JOIN status st ON st.ID = com.Status " +
                        "LEFT JOIN committee_lifecycle cl ON cl.ID = com.Lifecycle " +
                        "LEFT JOIN committee_type ct ON ct.ID = com.Committee_Type " +
                        "LEFT JOIN committee_classification cc ON cc.ID = com.Classification " +
                        "WHERE com.ID IN (" + inClause + ") AND com.DeleteDatetime IS NULL";

            case "interface":
                // Lookup tables use Name (not PrimaryName); Automation_ID -> interface_automation (see InterfaceDAO)
                return "SELECT i.id, i.Name, i.Ref_number, i.Description, " +
                        "ss.Name AS 'Source_systemID', " +
                        "ts.Name AS 'Target_systemID', " +
                        "tm.Name AS 'Transfer_Method_ID', " +
                        "tf.Name AS 'Transfer_Format_ID', " +
                        "ic.Name AS 'Classification_id', " +
                        "il.Name AS 'Lifecycle_id', " +
                        "st.primaryname AS 'status_id', " +
                        "ia.Name AS 'Automation_ID', " +
                        "ifreq.Name AS 'Frequency_ID', " +
                        "v.Name AS 'is_public', " +
                        "i.Asset_ID, i.Synchronisation_Control, " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = i.id AND sot.Type = 'SystemInterface' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM interface i " +
                        "LEFT JOIN system ss ON ss.id = i.Source_systemID " +
                        "LEFT JOIN system ts ON ts.id = i.Target_systemID " +
                        "LEFT JOIN interface_transfer tm ON tm.id = i.Transfer_Method_ID " +
                        "LEFT JOIN interface_transfer_format tf ON tf.id = i.Transfer_Format_ID " +
                        "LEFT JOIN interface_classification ic ON ic.id = i.Classification_id " +
                        "LEFT JOIN interface_lifecycle il ON il.id = i.Lifecycle_id " +
                        "LEFT JOIN status st ON st.ID = i.status_id " +
                        "LEFT JOIN interface_automation ia ON ia.id = i.Automation_ID " +
                        "LEFT JOIN interface_frequency ifreq ON ifreq.id = i.Frequency_ID " +
                        "LEFT JOIN viewing v ON v.id = i.is_public " +
                        "WHERE i.id IN (" + inClause + ") AND i.deleted_datetime IS NULL";

            case "regulation":
                return "SELECT reg.ID, " +
                        "reg.RefNumber AS 'Reference', " +
                        "reg.ShortName AS 'Short Name', " +
                        "reg.primaryName AS 'Regulation Long Name', " +
                        "reg.Description AS 'Description', " +
                        "reg.AdditionalInfo AS 'Additional Info', " +
                        "reg.PublicationDate AS 'Publication Date', " +
                        "reg.CommentsDate AS 'Comments Date', " +
                        "reg.FinalisationDate AS 'Finalisation Date', " +
                        "reg.ComplianceDate AS 'Compliance Date', " +
                        "reg.LegalAdvice AS 'Legal Advice', " +
                        "parent.primaryName AS 'Parent Regulation Name', " +
                        "parent.RefNumber AS 'Parent Ref.', " +
                        "rm.PrimaryName AS 'Regulation Maturity', " +
                        "rp.PrimaryName AS 'Regulation Probability', " +
                        "rs.PrimaryName AS 'BUDG Status', " +
                        "la.PrimaryName AS 'Legal Advice Type', " +
                        "rg.PrimaryName AS 'Regulation Stage', " +
                        "rc.PrimaryName AS 'Compliance Level', " +
                        "NULL AS 'User Email', " +
                        "NULL AS 'User First Name', " +
                        "NULL AS 'User Last Name', " +
                        "NULL AS 'User Lan ID', " +
                        "NULL AS 'Governance Role', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = reg.ID AND sot.Type = 'Regulation' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM regulation reg " +
                        "LEFT JOIN regulation parent ON parent.ID = reg.Parent_ID " +
                        "LEFT JOIN regulation_maturity rm ON rm.ID = reg.RegulationMaturity_ID " +
                        "LEFT JOIN regulation_probability rp ON rp.ID = reg.RegulationProbability_ID " +
                        "LEFT JOIN regulation_status rs ON rs.ID = reg.RegulationStatus_ID " +
                        "LEFT JOIN legal_advice_type la ON la.ID = reg.LegalAdviceType_ID " +
                        "LEFT JOIN regulation_stage rg ON rg.ID = reg.RegulationStage_ID " +
                        "LEFT JOIN regulation_compliance_level rc ON rc.ID = reg.ComplianceLevel_ID " +
                        "WHERE reg.ID IN (" + inClause + ") AND reg.DeletedDatetime IS NULL";

            case "geography":
                return "SELECT geo.ID, " +
                        "geo.PrimaryName AS 'Geography Name', " +
                        "geo.Description AS 'Geography Definition', " +
                        "parent.PrimaryName AS 'Parent Geography Name', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = geo.ID AND sot.Type = 'Geography' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM geography geo " +
                        "LEFT JOIN geography parent ON parent.ID = geo.ParentID " +
                        "WHERE geo.ID IN (" + inClause + ") AND geo.DeletedDatetime IS NULL";

            case "regulator":
                return "SELECT reg.ID, reg.PrimaryName, reg.ShortName, reg.Description, " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = reg.ID AND sot.Type = 'Regulator' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM regulator reg " +
                        "WHERE reg.ID IN (" + inClause + ") AND reg.DeletedDatetime IS NULL";

            case "regulatory_theme":
            case "regulatorytheme":
                return "SELECT rt.ID, rt.PrimaryName, rt.ShortName, rt.RefNumber, rt.Description, " +
                        "rt.Parent_ID, " +
                        "st.primaryname AS 'Status_ID', " +
                        "(SELECT seg.Name FROM segment_x_resource sxr " +
                        " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                        " WHERE orr.Object_ID = rt.ID AND sot.Type = 'RegulatoryTheme' " +
                        " AND sxr.Deleted_At IS NULL LIMIT 1) AS 'Segment' " +
                        "FROM regulatorytheme rt " +
                        "LEFT JOIN status st ON st.ID = rt.Status_ID " +
                        "WHERE rt.ID IN (" + inClause + ") AND rt.DeletedDatetime IS NULL";

            case "org_unit":
                return "SELECT ou.ID, " +
                        "ou.Reference AS 'Ref.', " +
                        "ou.Name, " +
                        "ou.Description, " +
                        "parent_ou.Name AS 'Parent Name', " +
                        "st.primaryname AS 'BUDG Status' " +
                        "FROM org_unit ou " +
                        "LEFT JOIN org_unit parent_ou ON parent_ou.ID = ou.Parent_ID " +
                        "LEFT JOIN status st ON st.ID = ou.status_id " +
                        "WHERE ou.ID IN (" + inClause + ") AND ou.deleted_Date IS NULL";

            default:
                // Generic query for other facets - escape table name with backticks for safety
                // This handles table names with special characters (hyphens, spaces, etc.)
                // Note: facet should already be normalized, but we escape for safety
                String idColumn = "ID";
                if (facet.equalsIgnoreCase("system") || facet.equalsIgnoreCase("process") ||
                        facet.equalsIgnoreCase("project") || facet.equalsIgnoreCase("product") ||
                        facet.equalsIgnoreCase("interface")) {
                    idColumn = "id";
                }
                return "SELECT * FROM `" + facet + "` WHERE " + idColumn + " IN (" + inClause + ")";
        }
    }

    /**
     * Map ResultSet to row Map
     */
    private Map<String, Object> mapResultSetToRow(ResultSet rs, String facet) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        List<String> headers = getHeaderNamesForFacet(facet);

        for (String header : headers) {
            try {
                Object value = rs.getObject(header);
                row.put(header, value);
            } catch (SQLException e) {
                // Column might not exist, set to null
                row.put(header, null);
            }
        }

        return row;
    }

    /**
     * Get header names only (for mapping ResultSet)
     */
    private List<String> getHeaderNamesForFacet(String facet) {
        String facetLower = facet.toLowerCase();
        if ("dataset".equals(facetLower) || "glossary".equals(facetLower) || "system".equals(facetLower)
                || "attribute".equals(facetLower) || "process".equals(facetLower) || "project".equals(facetLower)
                || "regulation".equals(facetLower) || "policy".equals(facetLower)) {
            // Use new headers with mandatory flags
            List<HeaderInfo> headerInfos = getHeadersWithMandatoryFlags(facetLower);
            List<String> headers = new ArrayList<>();
            for (HeaderInfo info : headerInfos) {
                headers.add(info.header);
            }
            return headers;
        } else {
            // Use old headers for backward compatibility
            return getHeadersForFacet(facet);
        }
    }

    /**
     * Set cell value based on type
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Create header style
     */
    @SuppressWarnings("unused")
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * Get sheet name for facet
     */
    private String getSheetName(String facet) {
        Map<String, String> sheetNames = Map.ofEntries(
                Map.entry("dataset", "Data Set"),
                Map.entry("legal_entity", "Legal Entity"),
                Map.entry("business_area", "Business Area"),
                Map.entry("regulatory_theme", "Regulatory Theme"),
                Map.entry("org_unit", "Org Unit"));
        return sheetNames.getOrDefault(facet, capitalizeFirst(facet));
    }

    /**
     * Capitalize first letter
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Set parameters for IN clause
     */
    private void setParameters(PreparedStatement ps, List<Integer> ids, int startIndex) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            ps.setInt(startIndex + i, ids.get(i));
        }
    }

    /**
     * Add dropdown validations for facet based on bulk upload template format
     * Now handles all facets dynamically, not just dataset, glossary, and system
     */
    private void addDropdownValidationsForFacet(XSSFSheet sheet, String facet, XSSFWorkbook workbook) {
        String facetLower = facet.toLowerCase();
        Map<String, List<String>> lookupValues = new HashMap<>();

        try {
            // Get headers for this facet to find column indices dynamically
            List<HeaderInfo> headers = getHeadersWithMandatoryFlags(facetLower);

            // Find the index of "Governance Role" column
            int governanceRoleColIndex = -1;
            for (int i = 0; i < headers.size(); i++) {
                if ("Governance Role".equalsIgnoreCase(headers.get(i).header)) {
                    governanceRoleColIndex = i;
                    break;
                }
            }

            // Get governance role values for this facet (using admin panel roles)
            if (governanceRoleColIndex >= 0) {
                List<String> roleValues = getRoleValuesForFacet(facetLower);
                if (!roleValues.isEmpty()) {
                    addDropdownValidation(sheet, governanceRoleColIndex, roleValues, 1, 1000, workbook);
                    logger.debug("Added governance role dropdown for facet {} at column {}", facet,
                            governanceRoleColIndex);
                } else {
                    logger.debug("No governance roles found for facet {}", facet);
                }
            }

            // Handle facet-specific dropdowns (keep existing logic for backward
            // compatibility)
            switch (facetLower) {
                case "dataset":
                    lookupValues.put("viewing", getLookupValues("viewing"));
                    lookupValues.put("dataset_type", getLookupValues("dataset_type"));
                    lookupValues.put("dataset_lifecycle", getLookupValues("dataset_lifecycle"));
                    lookupValues.put("status", getLookupValues("status"));

                    // Find column indices dynamically
                    int viewingCol = findColumnIndex(headers, "BUDG Viewing");
                    int typeCol = findColumnIndex(headers, "Type");
                    int lifecycleCol = findColumnIndex(headers, "Lifecycle");
                    int statusCol = findColumnIndex(headers, "BUDG Status");

                    if (viewingCol >= 0 && !lookupValues.get("viewing").isEmpty()) {
                        addDropdownValidation(sheet, viewingCol, lookupValues.get("viewing"), 1, 1000, workbook);
                    }
                    if (typeCol >= 0 && !lookupValues.get("dataset_type").isEmpty()) {
                        addDropdownValidation(sheet, typeCol, lookupValues.get("dataset_type"), 1, 1000, workbook);
                    }
                    if (lifecycleCol >= 0 && !lookupValues.get("dataset_lifecycle").isEmpty()) {
                        addDropdownValidation(sheet, lifecycleCol, lookupValues.get("dataset_lifecycle"), 1, 1000,
                                workbook);
                    }
                    if (statusCol >= 0 && !lookupValues.get("status").isEmpty()) {
                        addDropdownValidation(sheet, statusCol, lookupValues.get("status"), 1, 1000, workbook);
                    }
                    break;

                case "glossary":
                    lookupValues.put("viewing", getLookupValues("viewing"));
                    lookupValues.put("status", getLookupValues("status"));
                    lookupValues.put("glossary_lifecycle", getLookupValues("glossary_lifecycle"));
                    lookupValues.put("glossary_format_type", getLookupValues("glossary_format_type"));
                    lookupValues.put("glossary_kde_type", getLookupValues("glossary_kde_type"));
                    lookupValues.put("security_classification", getLookupValues("security_classification"));
                    lookupValues.put("glossary_type", getLookupValues("glossary_type"));
                    lookupValues.put("cia_rating", getLookupValues("cia_rating"));

                    // Find column indices dynamically
                    int glossViewingCol = findColumnIndex(headers, "BUDG Viewing");
                    int glossStatusCol = findColumnIndex(headers, "BUDG Status");
                    int glossLifecycleCol = findColumnIndex(headers, "Lifecycle");
                    int formatTypeCol = findColumnIndex(headers, "Format Type");
                    int kdeCol = findColumnIndex(headers, "KDE");
                    int securityClassCol = findColumnIndex(headers, "Security Classification");
                    int glossTypeCol = findColumnIndex(headers, "Type");
                    int confCol = findColumnIndex(headers, "Confidentiality");
                    int intCol = findColumnIndex(headers, "Integrity");
                    int availCol = findColumnIndex(headers, "Availability");

                    if (glossViewingCol >= 0 && !lookupValues.get("viewing").isEmpty()) {
                        addDropdownValidation(sheet, glossViewingCol, lookupValues.get("viewing"), 1, 1000, workbook);
                    }
                    if (glossStatusCol >= 0 && !lookupValues.get("status").isEmpty()) {
                        addDropdownValidation(sheet, glossStatusCol, lookupValues.get("status"), 1, 1000, workbook);
                    }
                    if (glossLifecycleCol >= 0 && !lookupValues.get("glossary_lifecycle").isEmpty()) {
                        addDropdownValidation(sheet, glossLifecycleCol, lookupValues.get("glossary_lifecycle"), 1, 1000,
                                workbook);
                    }
                    if (formatTypeCol >= 0 && !lookupValues.get("glossary_format_type").isEmpty()) {
                        addDropdownValidation(sheet, formatTypeCol, lookupValues.get("glossary_format_type"), 1, 1000,
                                workbook);
                    }
                    if (kdeCol >= 0 && !lookupValues.get("glossary_kde_type").isEmpty()) {
                        addDropdownValidation(sheet, kdeCol, lookupValues.get("glossary_kde_type"), 1, 1000, workbook);
                    }
                    if (securityClassCol >= 0 && !lookupValues.get("security_classification").isEmpty()) {
                        addDropdownValidation(sheet, securityClassCol, lookupValues.get("security_classification"), 1,
                                1000, workbook);
                    }
                    if (glossTypeCol >= 0 && !lookupValues.get("glossary_type").isEmpty()) {
                        addDropdownValidation(sheet, glossTypeCol, lookupValues.get("glossary_type"), 1, 1000,
                                workbook);
                    }
                    if (!lookupValues.get("cia_rating").isEmpty()) {
                        if (confCol >= 0) {
                            addDropdownValidation(sheet, confCol, lookupValues.get("cia_rating"), 1, 1000, workbook);
                        }
                        if (intCol >= 0) {
                            addDropdownValidation(sheet, intCol, lookupValues.get("cia_rating"), 1, 1000, workbook);
                        }
                        if (availCol >= 0) {
                            addDropdownValidation(sheet, availCol, lookupValues.get("cia_rating"), 1, 1000, workbook);
                        }
                    }
                    break;

                case "system":
                    List<String> externalValues = Arrays.asList("TRUE", "FALSE");
                    List<String> dqAutomationValues = Arrays.asList("TRUE", "FALSE");
                    lookupValues.put("viewing", getLookupValues("viewing"));
                    lookupValues.put("status", getLookupValues("status"));
                    lookupValues.put("system_lifecycle", getLookupValues("system_lifecycle"));
                    lookupValues.put("system_type", getLookupValues("system_type"));
                    lookupValues.put("system_classification", getLookupValues("system_classification"));
                    lookupValues.put("cia_rating", getLookupValues("cia_rating"));

                    // Find column indices dynamically
                    int externalCol = findColumnIndex(headers, "External");
                    int sysViewingCol = findColumnIndex(headers, "BUDG Viewing");
                    int sysStatusCol = findColumnIndex(headers, "BUDG Status");
                    int sysLifecycleCol = findColumnIndex(headers, "Lifecycle");
                    int sysTypeCol = findColumnIndex(headers, "Type");
                    int sysClassCol = findColumnIndex(headers, "Classification");
                    int sysConfCol = findColumnIndex(headers, "Confidentiality");
                    int sysIntCol = findColumnIndex(headers, "Integrity");
                    int sysAvailCol = findColumnIndex(headers, "Availability");
                    int dqAutomationCol = findColumnIndex(headers, "DQAutomation");

                    if (externalCol >= 0) {
                        addDropdownValidation(sheet, externalCol, externalValues, 1, 1000, workbook);
                    }
                    if (sysViewingCol >= 0 && !lookupValues.get("viewing").isEmpty()) {
                        addDropdownValidation(sheet, sysViewingCol, lookupValues.get("viewing"), 1, 1000, workbook);
                    }
                    if (sysStatusCol >= 0 && !lookupValues.get("status").isEmpty()) {
                        addDropdownValidation(sheet, sysStatusCol, lookupValues.get("status"), 1, 1000, workbook);
                    }
                    if (sysLifecycleCol >= 0 && !lookupValues.get("system_lifecycle").isEmpty()) {
                        addDropdownValidation(sheet, sysLifecycleCol, lookupValues.get("system_lifecycle"), 1, 1000,
                                workbook);
                    }
                    if (sysTypeCol >= 0 && !lookupValues.get("system_type").isEmpty()) {
                        addDropdownValidation(sheet, sysTypeCol, lookupValues.get("system_type"), 1, 1000, workbook);
                    }
                    if (sysClassCol >= 0 && !lookupValues.get("system_classification").isEmpty()) {
                        addDropdownValidation(sheet, sysClassCol, lookupValues.get("system_classification"), 1, 1000,
                                workbook);
                    }
                    if (!lookupValues.get("cia_rating").isEmpty()) {
                        if (sysConfCol >= 0) {
                            addDropdownValidation(sheet, sysConfCol, lookupValues.get("cia_rating"), 1, 1000, workbook);
                        }
                        if (sysIntCol >= 0) {
                            addDropdownValidation(sheet, sysIntCol, lookupValues.get("cia_rating"), 1, 1000, workbook);
                        }
                        if (sysAvailCol >= 0) {
                            addDropdownValidation(sheet, sysAvailCol, lookupValues.get("cia_rating"), 1, 1000,
                                    workbook);
                        }
                    }
                    if (dqAutomationCol >= 0) {
                        addDropdownValidation(sheet, dqAutomationCol, dqAutomationValues, 1, 1000, workbook);
                    }
                    break;
            }
        } catch (Exception e) {
            logger.warn("Error adding dropdown validations for facet {}: {}", facet, e.getMessage());
        }
    }

    /**
     * Find column index by header name (case-insensitive)
     */
    private int findColumnIndex(List<HeaderInfo> headers, String headerName) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).header.equalsIgnoreCase(headerName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Add dropdown validation to a column
     */
    private void addDropdownValidation(XSSFSheet sheet, int colIndex, List<String> values, int firstRow, int lastRow,
            XSSFWorkbook workbook) {
        if (values.isEmpty()) {
            return;
        }

        try {
            // Create a hidden sheet for the dropdown values
            String hiddenSheetName = "Hidden_" + sheet.getSheetName() + "_" + colIndex;
            XSSFSheet hiddenSheet = workbook.createSheet(hiddenSheetName);

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
            XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) dvHelper
                    .createFormulaListConstraint(hiddenSheetName + "!$A$1:$A$" + values.size());

            CellRangeAddressList addressList = new CellRangeAddressList(firstRow, lastRow, colIndex, colIndex);
            XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, addressList);

            // Set validation properties
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("Invalid Value", "Please select a value from the dropdown list.");
            validation.setShowPromptBox(true);
            validation.createPromptBox("Select Value", "Please select a value from the dropdown list.");

            sheet.addValidationData(validation);
        } catch (Exception e) {
            logger.warn("Error adding dropdown validation for column {}: {}", colIndex, e.getMessage());
        }
    }

    /**
     * Get lookup values from database
     */
    private List<String> getLookupValues(String tableName) {
        List<String> values = new ArrayList<>();

        String sql;
        String columnName;

        if ("status".equals(tableName)) {
            sql = "SELECT primaryname FROM status ORDER BY id ASC";
            columnName = "primaryname";
        } else if ("viewing".equals(tableName)) {
            sql = "SELECT Name FROM viewing ORDER BY id ASC";
            columnName = "Name";
        } else if ("dataset_type".equals(tableName)) {
            sql = "SELECT PrimaryName FROM dataset_type ORDER BY ID ASC";
            columnName = "PrimaryName";
        } else if ("dataset_lifecycle".equals(tableName)) {
            sql = "SELECT PrimaryName FROM dataset_lifecycle ORDER BY ID ASC";
            columnName = "PrimaryName";
        } else if ("glossary_lifecycle".equals(tableName)) {
            sql = "SELECT name FROM glossary_lifecycle ORDER BY ID ASC";
            columnName = "name";
        } else if ("glossary_format_type".equals(tableName)) {
            sql = "SELECT Name FROM glossary_format_type ORDER BY ID ASC";
            columnName = "Name";
        } else if ("glossary_kde_type".equals(tableName)) {
            sql = "SELECT Name FROM glossary_kde_type ORDER BY ID ASC";
            columnName = "Name";
        } else if ("glossary_type".equals(tableName)) {
            sql = "SELECT Name FROM glossary_type ORDER BY ID ASC";
            columnName = "Name";
        } else if ("security_classification".equals(tableName)) {
            sql = "SELECT Name FROM security_classification ORDER BY ID ASC";
            columnName = "Name";
        } else if ("system_lifecycle".equals(tableName)) {
            sql = "SELECT Name FROM system_lifecycle ORDER BY ID ASC";
            columnName = "Name";
        } else if ("system_type".equals(tableName)) {
            sql = "SELECT Name FROM system_type ORDER BY ID ASC";
            columnName = "Name";
        } else if ("system_classification".equals(tableName)) {
            sql = "SELECT Name FROM system_classification ORDER BY ID ASC";
            columnName = "Name";
        } else if ("cia_rating".equals(tableName)) {
            sql = "SELECT `Values` FROM cia_rating ORDER BY ID ASC";
            columnName = "Values";
        } else {
            // Default: try PrimaryName
            sql = "SELECT PrimaryName FROM " + tableName + " ORDER BY ID ASC";
            columnName = "PrimaryName";
        }

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String value = rs.getString(columnName);
                if (value != null && !value.trim().isEmpty()) {
                    values.add(value.trim());
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting lookup values from {}: {}", tableName, e.getMessage());
        }

        return values;
    }

    /**
     * Map facet name to module name as it appears in the database
     */
    private String mapFacetNameToModuleName(String facetName) {
        if (facetName == null) {
            return null;
        }

        String normalized = normalizeFacetName(facetName).toLowerCase();

        // Map normalized facet names to module names (as they appear in
        // module.primaryname)
        switch (normalized) {
            case "dataset":
            case "data_sets":
            case "datasets":
                return "Data Sets";
            case "glossary":
                return "Glossary";
            case "system":
                return "System";
            case "process":
                return "Process";
            case "policy":
                return "Policy";
            case "project":
                return "Project";
            case "committee":
                return "Committee";
            case "business_area":
            case "businessarea":
                return "Business Area";
            case "capability":
                return "Capability";
            case "client":
                return "Client";
            case "legal_entity":
            case "legalentity":
            case "legal":
                return "Legal Entity";
            case "product":
                return "Product";
            case "attribute":
                return "Attribute";
            case "interface":
                return "Interface";
            case "regulation":
                return "Regulation";
            case "geography":
                return "Geography";
            case "regulator":
                return "Regulator";
            case "regulatory_theme":
            case "regulatorytheme":
                return "Regulatory Theme";
            case "org_unit":
            case "orgunit":
                return "Org Unit";
            default:
                // Try to capitalize first letter as fallback
                if (normalized.length() > 0) {
                    return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
                }
                return normalized;
        }
    }

    /**
     * Get module ID for a facet by resolving facet name to module name and querying
     * the module table
     */
    private Integer getModuleIdForFacet(String facetName) {
        String moduleName = mapFacetNameToModuleName(facetName);
        if (moduleName == null) {
            logger.warn("Could not map facet name to module name: {}", facetName);
            return null;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER(?) LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, moduleName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting module ID for facet {} (module name: {}): {}",
                    facetName, moduleName, e.getMessage());
        }
        return null;
    }

    /**
     * Get role values for a facet by dynamically resolving the module ID
     * This replaces the hardcoded getRoleValuesForDataset/Glossary/System methods
     */
    private List<String> getRoleValuesForFacet(String facetName) {
        Integer moduleId = getModuleIdForFacet(facetName);
        if (moduleId == null) {
            logger.warn("Could not resolve module ID for facet: {}, returning empty role list", facetName);
            return new ArrayList<>();
        }

        return getRoleValues(moduleId, facetName);
    }

    /**
     * Get role values for Dataset (module 10) - kept for backward compatibility.
     * @deprecated Use getRoleValuesForFacet() instead
     */
    @Deprecated
    private List<String> getRoleValuesForDataset() {
        return getRoleValues(10, "Dataset");
    }

    /**
     * Get role values for Glossary (module 8) - kept for backward compatibility.
     * @deprecated Use getRoleValuesForFacet() instead
     */
    @Deprecated
    private List<String> getRoleValuesForGlossary() {
        return getRoleValues(8, "Glossary");
    }

    /**
     * Get role values for System (module 1) - kept for backward compatibility.
     * @deprecated Use getRoleValuesForFacet() instead
     */
    @Deprecated
    private List<String> getRoleValuesForSystem() {
        return getRoleValues(1, "System");
    }

    /**
     * Get role values for a module
     */
    private List<String> getRoleValues(int moduleId, String moduleName) {
        List<String> values = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT DISTINCT orl.primaryname " +
                    "FROM object_role orl " +
                    "JOIN module m ON m.id = orl.module " +
                    "WHERE m.id = ? " +
                    "AND orl.primaryname IS NOT NULL " +
                    "ORDER BY orl.primaryname";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, moduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String value = rs.getString("primaryname");
                        if (value != null && !value.trim().isEmpty()) {
                            values.add(value.trim());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting role values for module {}: {}", moduleName, e.getMessage());
        }
        return values;
    }

    /**
     * Add List sheets for dropdowns based on facet (enhanced version)
     */
    private void addListSheetsForFacet(XSSFWorkbook workbook, String facet) {
        String normalizedFacet = normalizeFacetName(facet);

        try {
            switch (normalizedFacet) {
                case "dataset":
                    addListSheetForRoles(workbook, "Data Owner", "Dataset", "Owner");
                    addListSheetForRoles(workbook, "Data Steward", "Dataset", "Steward");
                    // Add lookup tables
                    addListSheet(workbook, "BUDG Viewing", "viewing", "Name");
                    addListSheet(workbook, "Dataset Type", "dataset_type", "PrimaryName");
                    addListSheet(workbook, "Dataset Lifecycle", "dataset_lifecycle", "PrimaryName");
                    addListSheet(workbook, "BUDG Status", "status", "primaryname");
                    break;

                case "glossary":
                    addListSheetForRoles(workbook, "Glossary Definition Owner", "Glossary", "Definition Owner");
                    addListSheetForRoles(workbook, "Glossary Steward", "Glossary", "Steward");
                    addListSheetForRoles(workbook, "Glossary Validator", "Glossary", "Validator");
                    // Add lookup tables
                    addListSheet(workbook, "BUDG Viewing", "viewing", "Name");
                    addListSheet(workbook, "BUDG Status", "status", "primaryname");
                    addListSheet(workbook, "Glossary Lifecycle", "glossary_lifecycle", "name");
                    addListSheet(workbook, "Format Type", "glossary_format_type", "Name");
                    addListSheet(workbook, "KDE", "glossary_kde_type", "Name");
                    addListSheet(workbook, "Security Classification", "security_classification", "Name");
                    addListSheet(workbook, "Glossary Type", "glossary_type", "Name");
                    addListSheet(workbook, "CIA Rating", "cia_rating", "Values");
                    break;

                case "system":
                    addListSheetForRoles(workbook, "System Business Owner", "System", "Business Owner");
                    addListSheetForRoles(workbook, "System Steward", "System", "Steward");
                    // Add lookup tables
                    addListSheet(workbook, "BUDG Viewing", "viewing", "Name");
                    addListSheet(workbook, "BUDG Status", "status", "primaryname");
                    addListSheet(workbook, "System Lifecycle", "system_lifecycle", "Name");
                    addListSheet(workbook, "System Type", "system_type", "Name");
                    addListSheet(workbook, "System Classification", "system_classification", "Name");
                    addListSheet(workbook, "CIA Rating", "cia_rating", "Values");
                    break;

                default:
                    // Use existing addListSheets for backward compatibility
                    addListSheets(workbook, normalizedFacet);
                    break;
            }
        } catch (Exception e) {
            logger.warn("Error adding list sheets for facet {}: {}", facet, e.getMessage());
        }
    }

    /**
     * Add List sheets for dropdowns based on facet (kept for backward
     * compatibility)
     */
    private void addListSheets(XSSFWorkbook workbook, String facet) {
        String normalizedFacet = normalizeFacetName(facet);

        try {
            switch (normalizedFacet) {
                case "regulatory_theme":
                    addListSheet(workbook, "BUDG Status", "status", "primaryname");
                    break;
                case "system":
                    addListSheetForRoles(workbook, "System Business Owner", "System", "Business Owner");
                    addListSheetForRoles(workbook, "System Steward", "System", "Steward");
                    break;
                case "dataset":
                    addListSheetForRoles(workbook, "Data Owner", "Dataset", "Owner");
                    addListSheetForRoles(workbook, "Data Steward", "Dataset", "Steward");
                    break;
                case "glossary":
                    addListSheetForRoles(workbook, "Glossary Definition Owner", "Glossary", "Definition Owner");
                    addListSheetForRoles(workbook, "Glossary Steward", "Glossary", "Steward");
                    addListSheetForRoles(workbook, "Glossary Validator", "Glossary", "Validator");
                    break;
                case "attribute":
                    addListSheetForRoles(workbook, "Data Attribute Owner", "Attribute", "Owner");
                    addListSheetForRoles(workbook, "Data Attribute Steward", "Attribute", "Steward");
                    break;
                case "policy":
                    addListSheetForRoles(workbook, "Policy Owner", "Policy", "Owner");
                    addListSheetForRoles(workbook, "Policy Steward", "Policy", "Steward");
                    break;
                // Add more facets as needed
            }
        } catch (Exception e) {
            logger.warn("Error adding list sheets for facet {}: {}", facet, e.getMessage());
        }
    }

    /**
     * Add List sheet for roles filtered by module and role type
     */
    private void addListSheetForRoles(XSSFWorkbook workbook, String sheetName, String moduleName,
            String roleTypePattern) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT DISTINCT orl.primaryname " +
                    "FROM object_role orl " +
                    "JOIN module m ON m.id = orl.module " +
                    "LEFT JOIN object_role_type ort ON ort.id = orl.objectroletype_id " +
                    "WHERE m.primaryname = ? " +
                    "AND (ort.primaryname LIKE ? OR orl.primaryname LIKE ?) " +
                    "AND orl.primaryname IS NOT NULL " +
                    "ORDER BY orl.primaryname";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, moduleName);
                String pattern = "%" + roleTypePattern + "%";
                ps.setString(2, pattern);
                ps.setString(3, pattern);

                try (ResultSet rs = ps.executeQuery()) {
                    Sheet listSheet = workbook.createSheet(sheetName);
                    int rowNum = 0;

                    while (rs.next()) {
                        Row row = listSheet.createRow(rowNum++);
                        Cell cell = row.createCell(0);
                        String value = rs.getString("primaryname");
                        if (value != null && !value.trim().isEmpty()) {
                            cell.setCellValue(value.trim());
                        }
                    }

                    if (rowNum > 0) {
                        listSheet.autoSizeColumn(0);
                        logger.debug("Added list sheet '{}' with {} values", sheetName, rowNum);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error creating list sheet '{}' for module {}: {}", sheetName, moduleName, e.getMessage());
        }
    }

    /**
     * Add a List sheet with values from database
     */
    private void addListSheet(XSSFWorkbook workbook, String sheetName, String tableName, String columnName) {
        addListSheet(workbook, sheetName, tableName, columnName, null);
    }

    /**
     * Add a List sheet with values from database with optional WHERE clause
     */
    private void addListSheet(XSSFWorkbook workbook, String sheetName, String tableName, String columnName,
            String whereClause) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String colEscaped = "`" + columnName.replace("`", "``") + "`";
            String tableEscaped = "`" + tableName.replace("`", "``") + "`";
            String sql = "SELECT DISTINCT " + colEscaped + " FROM " + tableEscaped;
            if (whereClause != null && !whereClause.isEmpty()) {
                sql += " WHERE " + whereClause + " AND " + colEscaped + " IS NOT NULL";
            } else {
                sql += " WHERE " + colEscaped + " IS NOT NULL";
            }
            sql += " ORDER BY " + colEscaped;

            try (PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {

                Sheet listSheet = workbook.createSheet(sheetName);
                int rowNum = 0;

                while (rs.next()) {
                    Row row = listSheet.createRow(rowNum++);
                    Cell cell = row.createCell(0);
                    String value = rs.getString(columnName);
                    if (value != null && !value.trim().isEmpty()) {
                        cell.setCellValue(value.trim());
                    }
                }

                if (rowNum > 0) {
                    listSheet.autoSizeColumn(0);
                    logger.debug("Added list sheet '{}' with {} values", sheetName, rowNum);
                }
            }
        } catch (SQLException e) {
            logger.warn("Error creating list sheet '{}' from table {}: {}", sheetName, tableName, e.getMessage());
        }
    }
}
