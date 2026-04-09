package com.example.budg_v2.util;

import com.example.budg_v2.dao.GlossaryDAO;
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
 * Utility class for generating Glossary Hierarchy Excel files for dataset migrations
 * Displays the complete hierarchy of glossaries linked to datasets and their attributes
 */
public class GlossaryHierarchyExcelGenerator {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryHierarchyExcelGenerator.class);
    private final GlossaryDAO glossaryDAO;

    public GlossaryHierarchyExcelGenerator() {
        this.glossaryDAO = new GlossaryDAO();
    }

    /**
     * Generate Glossary Hierarchy Excel file for datasets
     * 
     * @param datasetIds List of dataset IDs to collect glossaries from
     * @param timestamp Timestamp for file naming
     * @return Byte array containing Excel file data
     */
    public byte[] generateGlossaryHierarchyExcel(List<Integer> datasetIds, String timestamp) {
        try {
            // Collect all glossary IDs from datasets and their attributes
            Set<Integer> glossaryIds = collectGlossaryIds(datasetIds);
            
            if (glossaryIds.isEmpty()) {
                logger.warn("No glossaries found for datasets: {}", datasetIds);
                return null;
            }

            // Get hierarchy data for all glossaries
            List<Map<String, Object>> hierarchyData = getGlossaryHierarchyData(new ArrayList<>(glossaryIds));
            
            if (hierarchyData.isEmpty()) {
                logger.warn("No hierarchy data found for glossaries: {}", glossaryIds);
                return null;
            }

            // Create Excel workbook
            XSSFWorkbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Glossary Hierarchy");

            // Define headers
            List<String> headers = Arrays.asList(
                    "Ref_Number", "Name", "Description", "Type", "Parent Name", "Parent Ref.",
                    "Strategic Source System", "Data Items", "Data Attributes", "Segment"
            );

            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (Map<String, Object> rowData : hierarchyData) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;
                
                for (String header : headers) {
                    Cell cell = row.createCell(colNum++);
                    Object value = getValueForHeader(rowData, header);
                    setCellValue(cell, value);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();

            logger.info("Generated Glossary Hierarchy Excel with {} rows for {} glossaries", 
                    hierarchyData.size(), glossaryIds.size());
            
            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("Error generating Glossary Hierarchy Excel: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Collect all glossary IDs from datasets (direct links and via attributes)
     */
    private Set<Integer> collectGlossaryIds(List<Integer> datasetIds) {
        Set<Integer> glossaryIds = new HashSet<>();
        
        if (datasetIds == null || datasetIds.isEmpty()) {
            return glossaryIds;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String inClause = String.join(",", Collections.nCopies(datasetIds.size(), "?"));
            
            // Get glossaries directly linked to datasets
            String datasetSql = "SELECT DISTINCT glossary FROM dataset " +
                    "WHERE ID IN (" + inClause + ") " +
                    "AND glossary IS NOT NULL AND glossary != 0 " +
                    "AND DeletedDatetime IS NULL";
            
            try (PreparedStatement ps = conn.prepareStatement(datasetSql)) {
                for (int i = 0; i < datasetIds.size(); i++) {
                    ps.setInt(i + 1, datasetIds.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int glossaryId = rs.getInt("glossary");
                        if (glossaryId > 0) {
                            glossaryIds.add(glossaryId);
                        }
                    }
                }
            }

            // Get glossaries linked to attributes of these datasets
            String attributeSql = "SELECT DISTINCT Glossary_ID FROM attribute " +
                    "WHERE Dataset_ID IN (" + inClause + ") " +
                    "AND Glossary_ID IS NOT NULL AND Glossary_ID != 0 " +
                    "AND DeletedDatetime IS NULL";
            
            try (PreparedStatement ps = conn.prepareStatement(attributeSql)) {
                for (int i = 0; i < datasetIds.size(); i++) {
                    ps.setInt(i + 1, datasetIds.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int glossaryId = rs.getInt("Glossary_ID");
                        if (glossaryId > 0) {
                            glossaryIds.add(glossaryId);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("Error collecting glossary IDs: {}", e.getMessage(), e);
        }

        return glossaryIds;
    }

    /**
     * Get hierarchy data for all glossaries
     * Uses GlossaryDAO to get full hierarchy for each glossary
     */
    private List<Map<String, Object>> getGlossaryHierarchyData(List<Integer> glossaryIds) {
        List<Map<String, Object>> allHierarchyData = new ArrayList<>();
        Set<Integer> processedIds = new HashSet<>(); // Avoid duplicates

        try (Connection conn = DatabaseConnection.getConnection()) {
            for (Integer glossaryId : glossaryIds) {
                if (processedIds.contains(glossaryId)) {
                    continue; // Skip if already processed
                }

                try {
                    List<Map<String, Object>> hierarchy = glossaryDAO.getGlossaryHierarchy(glossaryId);
                    
                    for (Map<String, Object> item : hierarchy) {
                        Integer id = (Integer) item.get("id");
                        if (id != null && !processedIds.contains(id)) {
                            // Convert to Excel format
                            Map<String, Object> excelRow = convertToExcelFormat(item, conn);
                            allHierarchyData.add(excelRow);
                            processedIds.add(id);
                        }
                    }
                } catch (SQLException e) {
                    logger.error("Error getting hierarchy for glossary {}: {}", glossaryId, e.getMessage(), e);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting connection for glossary hierarchy: {}", e.getMessage(), e);
        }

        return allHierarchyData;
    }

    /**
     * Convert hierarchy item to Excel row format
     */
    private Map<String, Object> convertToExcelFormat(Map<String, Object> item, Connection conn) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        
        Integer id = (Integer) item.get("id");
        
        // Get glossary details
        String sql = "SELECT g.Ref_Number, g.Name, g.Description, gt.Name AS Type, " +
                "parent.Name AS ParentName, parent.Ref_Number AS ParentRef, " +
                "(SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                " FROM glossary_x_system gxs " +
                " LEFT JOIN system s ON s.id = gxs.SystemID " +
                " WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) AS StrategicSourceSystem, " +
                "(SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) AS DataItems, " +
                "(SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) AS DataAttributes, " +
                "(SELECT seg.Name FROM segment_x_resource sxr " +
                " JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                " JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                " JOIN segment seg ON seg.ID = sxr.Segment_ID " +
                " WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' " +
                " AND sxr.Deleted_At IS NULL LIMIT 1) AS Segment " +
                "FROM glossary g " +
                "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "LEFT JOIN glossary parent ON parent.ID = g.Parent_ID " +
                "WHERE g.ID = ? AND g.Deleted_datetime IS NULL";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    row.put("Ref_Number", rs.getString("Ref_Number"));
                    row.put("Name", rs.getString("Name"));
                    row.put("Description", rs.getString("Description"));
                    row.put("Type", rs.getString("Type"));
                    row.put("Parent Name", rs.getString("ParentName"));
                    row.put("Parent Ref.", rs.getString("ParentRef"));
                    row.put("Strategic Source System", rs.getString("StrategicSourceSystem"));
                    row.put("Data Items", rs.getInt("DataItems"));
                    row.put("Data Attributes", rs.getInt("DataAttributes"));
                    row.put("Segment", rs.getString("Segment"));
                }
            }
        }
        
        return row;
    }

    /**
     * Get value for header from row data
     */
    private Object getValueForHeader(Map<String, Object> rowData, String header) {
        // Map header names to row data keys
        switch (header) {
            case "Ref_Number":
                return rowData.get("Ref_Number");
            case "Name":
                return rowData.get("Name");
            case "Description":
                return rowData.get("Description");
            case "Type":
                return rowData.get("Type");
            case "Parent Name":
                return rowData.get("Parent Name");
            case "Parent Ref.":
                return rowData.get("Parent Ref.");
            case "Strategic Source System":
                return rowData.get("Strategic Source System");
            case "Data Items":
                return rowData.get("Data Items");
            case "Data Attributes":
                return rowData.get("Data Attributes");
            case "Segment":
                return rowData.get("Segment");
            default:
                return null;
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
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Create header style
     */
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
