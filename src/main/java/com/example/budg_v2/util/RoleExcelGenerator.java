package com.example.budg_v2.util;

import com.example.budg_v2.database.DatabaseConnection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Utility class for generating BUDG-compatible Role Excel files
 */
public class RoleExcelGenerator {

    private static final Logger logger = LoggerFactory.getLogger(RoleExcelGenerator.class);

    // Facet to Stakeholder Table mapping
    private static final Map<String, String> STAKEHOLDER_TABLES = new HashMap<>();
    // Facet to ID Column mapping
    private static final Map<String, String> ID_COLUMNS = new HashMap<>();
    // Facet to Object_X_IPID Column variation mapping
    private static final Map<String, String> OXP_ID_COLUMNS = new HashMap<>();

    static {
        STAKEHOLDER_TABLES.put("dataset", "dataset_x_objectxpeople");
        STAKEHOLDER_TABLES.put("system", "system_x_objectxpeople");
        STAKEHOLDER_TABLES.put("glossary", "glossary_x_objectxpeople");
        STAKEHOLDER_TABLES.put("policy", "policy_x_objectxpeople");
        STAKEHOLDER_TABLES.put("process", "process_x_objectxpeople");
        STAKEHOLDER_TABLES.put("product", "product_x_objectxpeople");
        STAKEHOLDER_TABLES.put("project", "project_x_objectxpeople");
        STAKEHOLDER_TABLES.put("business_area", "businessarea_x_objectxpeople");
        STAKEHOLDER_TABLES.put("capability", "capability_x_objectxpeople");
        STAKEHOLDER_TABLES.put("client", "client_x_objectxpeople");
        STAKEHOLDER_TABLES.put("legal_entity", "legal_x_objectxpeople");
        STAKEHOLDER_TABLES.put("interface", "interface_x_objectxpeople");
        STAKEHOLDER_TABLES.put("committee", "committee_x_objectxpeople");
        STAKEHOLDER_TABLES.put("regulation", "regulation_x_objectxpeople");
        STAKEHOLDER_TABLES.put("attribute", "attribute_x_objectxpeople");
        // regulator doesn't have objectxpeople table
        // STAKEHOLDER_TABLES.put("regulator", "regulator_x_objectxpeople");
        // regulatory_theme: no regulatorytheme_x_objectxpeople in schema (roles N/A for this facet)

        ID_COLUMNS.put("dataset", "Dataset_ID");
        ID_COLUMNS.put("system", "SystemID");
        ID_COLUMNS.put("glossary", "GlossaryID");
        ID_COLUMNS.put("policy", "Policy_ID");
        ID_COLUMNS.put("process", "process_id");
        ID_COLUMNS.put("product", "product_id");
        ID_COLUMNS.put("project", "project_id");
        ID_COLUMNS.put("business_area", "BusinessAreaID");
        ID_COLUMNS.put("capability", "CapabilityID");
        ID_COLUMNS.put("client", "ClientID");
        ID_COLUMNS.put("legal_entity", "Legal_ID");
        ID_COLUMNS.put("interface", "InterfaceID");
        ID_COLUMNS.put("committee", "Committee_ID");
        ID_COLUMNS.put("regulation", "RegulationID");
        ID_COLUMNS.put("attribute", "AttributeID");

        OXP_ID_COLUMNS.put("policy", "Object_X_IP");
        OXP_ID_COLUMNS.put("process", "object_x_ip");
        OXP_ID_COLUMNS.put("product", "object_x_ip");
        OXP_ID_COLUMNS.put("project", "object_x_ip");
        OXP_ID_COLUMNS.put("committee", "Object_X_ipid");
        OXP_ID_COLUMNS.put("legal_entity", "Object_X_IP");
    }

    /**
     * Normalize facet string to keys used in STAKEHOLDER_TABLES / ID_COLUMNS.
     */
    private static String normalizeFacetForRoles(String facet) {
        if (facet == null || facet.isEmpty()) {
            return facet;
        }
        String n = facet.toLowerCase().trim().replace("-", "_");
        return switch (n) {
            case "businessarea" -> "business_area";
            case "legalentity", "legal" -> "legal_entity";
            case "regulatorytheme" -> "regulatory_theme";
            case "orgunit" -> "org_unit";
            default -> n;
        };
    }

    /**
     * True when this facet has a stakeholder (roles) junction table in the schema.
     */
    public boolean hasRoleStakeholderTable(String facet) {
        String key = normalizeFacetForRoles(facet);
        return key != null && STAKEHOLDER_TABLES.containsKey(key) && ID_COLUMNS.containsKey(key);
    }

    /**
     * Generate Role Excel for a facet
     */
    public byte[] generateRoleExcel(String facet, List<Map<String, Object>> data, String timestamp) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Governance Role");

            // Get headers based on facet type
            List<String> headers = getHeadersForFacet(facet);

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.createCell(i);
                    Object value = rowData.get(headers.get(i));
                    setCellValue(cell, value);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("Error generating Role Excel for facet {}: {}", facet, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get headers for facet based on facet type
     */
    private List<String> getHeadersForFacet(String facet) {
        switch (facet.toLowerCase()) {
            case "dataset":
                // Dataset Role: Ref, Name, System Short Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "System Short Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "system":
                // System Role: Short Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Short Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "policy":
                // Policy Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "capability":
                // Capability Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "regulatory_theme":
            case "regulatorytheme":
                // Regulatory Theme Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "product":
                // Product Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "client":
                // Client Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "process":
                // Process Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "project":
                // Project Role: Project Ref., Project Name, Project Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Project Ref.", "Project Name", "Project Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "committee":
                // Committee Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "business_area":
            case "businessarea":
                // Business Area Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "legal_entity":
            case "legalentity":
                // Legal Entity Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            case "glossary":
                // Glossary Role: Ref, Name, Parent Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "Parent Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
            
            default:
                // Default: Ref, Name, User Email, User First Name, User Last Name, User Lan ID, Governance Role
                return Arrays.asList(
                        "Ref.", "Name", "User Email",
                        "User First Name", "User Last Name", "User Lan ID", "Governance Role");
        }
    }

    /**
     * Get Role data from database with facet-aware joins
     */
    public List<Map<String, Object>> getRoleData(String facet, List<Integer> objectIds) {
        if (objectIds == null || objectIds.isEmpty())
            return new ArrayList<>();

        String roleKey = normalizeFacetForRoles(facet);
        String tableName = STAKEHOLDER_TABLES.get(roleKey);
        String idColumn = ID_COLUMNS.get(roleKey);
        String oxpIdColumn = OXP_ID_COLUMNS.getOrDefault(roleKey, "Object_x_ipid");

        if (tableName == null || idColumn == null) {
            logger.debug("No role table mapping for facet: {} (key={})", facet, roleKey);
            return new ArrayList<>();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        String inClause = String.join(",", Collections.nCopies(objectIds.size(), "?"));

        // Build query based on facet
        String sql = buildRoleQuery(roleKey, tableName, idColumn, oxpIdColumn, inClause);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < objectIds.size(); i++) {
                ps.setInt(i + 1, objectIds.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    
                    // Add Ref. or Project Ref. (not for system)
                    if (roleKey.equals("project")) {
                        row.put("Project Ref.", rs.getString("RefNumber"));
                    } else if (!roleKey.equals("system")) {
                        row.put("Ref.", rs.getString("RefNumber"));
                    }
                    
                    // Add Name (or Short Name for system, or Project Name for project)
                    if (roleKey.equals("system")) {
                        row.put("Short Name", rs.getString("Name"));
                    } else if (roleKey.equals("project")) {
                        row.put("Project Name", rs.getString("PrimaryName"));
                    } else {
                        row.put("Name", rs.getString("PrimaryName"));
                    }
                    
                    // Add System Short Name for dataset
                    if (roleKey.equals("dataset")) {
                        row.put("System Short Name", rs.getString("SystemShortName"));
                    }
                    
                    // Add Parent Name for policy, capability, regulatory_theme, product, client, process, project, business_area, legal_entity, and glossary
                    if (roleKey.equals("policy") || roleKey.equals("capability") || roleKey.equals("regulatory_theme")
                            || roleKey.equals("product") || roleKey.equals("client") || roleKey.equals("process")
                            || roleKey.equals("project") || roleKey.equals("business_area")
                            || roleKey.equals("legal_entity") || roleKey.equals("glossary")) {
                        if (roleKey.equals("project")) {
                            row.put("Project Parent Name", rs.getString("ParentName"));
                        } else {
                            row.put("Parent Name", rs.getString("ParentName"));
                        }
                    }
                    
                    // Add user and role columns
                    row.put("User Email", rs.getString("UserEmail"));
                    row.put("User First Name", rs.getString("UserFirstName"));
                    row.put("User Last Name", rs.getString("UserLastName"));
                    row.put("User Lan ID", rs.getString("UserLanID"));
                    row.put("Governance Role", rs.getString("GovernanceRole"));
                    
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching role data for {}: {}", facet, e.getMessage(), e);
        }

        return results;
    }

    private String buildRoleQuery(String facet, String tableName, String idColumn, String oxpIdColumn,
            String inClause) {
        // Build SELECT clause based on facet type
        StringBuilder select = new StringBuilder("SELECT ");
        
        // Add Ref and Name columns (except for system)
        if (!facet.equals("system")) {
            select.append("o.%s AS RefNumber, ");
        }
        // For system, use Name; for others, use PrimaryName
        if (facet.equals("system")) {
            select.append("o.%s AS Name, ");
        } else {
            select.append("o.%s AS PrimaryName, ");
        }
        
        // Add System Short Name for dataset
        if (facet.equals("dataset")) {
            select.append("s.Name AS SystemShortName, ");
        }
        
        // Add Parent Name for policy, capability, regulatory_theme, product, client, process, project, business_area, legal_entity, and glossary
        if (facet.equals("policy") || facet.equals("capability") || facet.equals("regulatory_theme") || facet.equals("regulatorytheme") || facet.equals("product") || facet.equals("client") || facet.equals("process") || facet.equals("project") || facet.equals("business_area") || facet.equals("businessarea")) {
            select.append("parent.PrimaryName AS ParentName, ");
        } else if (facet.equals("legal_entity") || facet.equals("legalentity")) {
            // Legal entities use ShortName, not PrimaryName
            select.append("parent.ShortName AS ParentName, ");
        } else if (facet.equals("glossary")) {
            // Glossary uses Name, not PrimaryName
            select.append("parent.Name AS ParentName, ");
        }
        
        // Add user and role columns
        select.append("p.Email AS UserEmail, p.First_Name AS UserFirstName, p.Last_Name AS UserLastName, ");
        select.append("pd.lan_id AS UserLanID, orl.primaryname AS GovernanceRole ");
        
        // Build FROM clause
        select.append("FROM `%s` o ");
        select.append("JOIN `%s` jt ON jt.%s = o.%s ");
        select.append("JOIN object_x_people oxp ON jt.%s = oxp.id ");
        select.append("JOIN people p ON oxp.ipid = p.ID ");
        select.append("LEFT JOIN people_details pd ON p.ip_details = pd.id ");
        select.append("JOIN object_role orl ON oxp.roleID = orl.id ");
        
        // Add System join for dataset
        if (facet.equals("dataset")) {
            select.append("LEFT JOIN system s ON s.id = o.MasterSource ");
        }
        
        // Add Parent join for policy, capability, regulatory_theme, product, client, process, project, business_area, and glossary
        if (facet.equals("policy")) {
            select.append("LEFT JOIN policy parent ON parent.ID = o.ParentID ");
        } else if (facet.equals("capability")) {
            select.append("LEFT JOIN capability parent ON parent.ID = o.Parent_ID ");
        } else if (facet.equals("regulatory_theme") || facet.equals("regulatorytheme")) {
            select.append("LEFT JOIN regulatorytheme parent ON parent.ID = o.Parent_ID ");
        } else if (facet.equals("product")) {
            select.append("LEFT JOIN product parent ON parent.id = o.parent_id ");
        } else if (facet.equals("client")) {
            select.append("LEFT JOIN client parent ON parent.ID = o.Parent_ID ");
        } else if (facet.equals("process")) {
            select.append("LEFT JOIN process parent ON parent.id = o.parentid ");
        } else if (facet.equals("project")) {
            select.append("LEFT JOIN project parent ON parent.id = o.parentid ");
        } else if (facet.equals("business_area") || facet.equals("businessarea")) {
            select.append("LEFT JOIN business_area parent ON parent.ID = o.Parent_ID ");
        } else if (facet.equals("legal_entity") || facet.equals("legalentity")) {
            select.append("LEFT JOIN legal parent ON parent.ID = o.Parent_ID ");
        } else if (facet.equals("glossary")) {
            select.append("LEFT JOIN glossary parent ON parent.ID = o.Parent_ID ");
        }
        
        select.append("WHERE o.%s IN (%s)");

        String refCol = getRefColumn(facet);
        String nameCol = getNameColumn(facet);
        String objIdCol = getObjectIdColumn(facet);
        String tableForFacet = getTableForFacet(facet);
        
        // Build the query string with proper parameter substitution
        String query = select.toString();
        
        // For system, we don't need refCol in the SELECT
        if (facet.equals("system")) {
            // System: nameCol, tableForFacet, tableName, idColumn, objIdCol, oxpIdColumn, objIdCol, inClause
            return String.format(query, nameCol, tableForFacet, tableName, idColumn, objIdCol,
                    oxpIdColumn, objIdCol, inClause);
        } else {
            // Other facets: refCol, nameCol, tableForFacet, tableName, idColumn, objIdCol, oxpIdColumn, objIdCol, inClause
            return String.format(query, refCol, nameCol, tableForFacet, tableName, idColumn, objIdCol,
                    oxpIdColumn, objIdCol, inClause);
        }
    }

    private String getTableForFacet(String facet) {
        switch (facet) {
            case "legal_entity":
                return "legal";
            case "business_area":
                return "business_area";
            case "regulatory_theme":
                return "regulatorytheme";
            case "interface":
                return "interface";
            default:
                return facet;
        }
    }

    private String getRefColumn(String facet) {
        switch (facet) {
            case "dataset":
                return "RefNumber";
            case "system":
                return "AssetID";
            case "glossary":
                return "Ref_Number";
            case "policy":
                return "refNumber";
            case "process":
                return "refnumber";
            case "project":
                return "refnumber";
            case "capability":
                return "RefNumber";
            case "committee":
                return "RefNumber";
            case "interface":
                return "Ref_number";
            case "regulation":
                return "RefNumber";
            case "regulatory_theme":
                return "RefNumber";
            case "attribute":
                return "RefNumber";
            default:
                return "ID";
        }
    }

    private String getNameColumn(String facet) {
        switch (facet) {
            case "system":
            case "glossary":
            case "interface":
                return "Name";
            case "legal_entity":
                return "ShortName";
            default:
                return "PrimaryName";
        }
    }

    private String getObjectIdColumn(String facet) {
        if (facet.equals("system") || facet.equals("process") || facet.equals("project") || facet.equals("product")
                || facet.equals("interface")) {
            return "id";
        }
        return "ID";
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
