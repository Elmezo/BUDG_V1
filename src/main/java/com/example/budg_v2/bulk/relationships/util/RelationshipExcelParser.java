package com.example.budg_v2.bulk.relationships.util;

import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import com.example.budg_v2.bulk.relationships.dto.RelationshipRowData;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Excel files in relationship bulk upload
 * Supports both standard and streaming parsing for large files
 */
public class RelationshipExcelParser {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipExcelParser.class);
    @SuppressWarnings("unused")
    private static final int LARGE_FILE_THRESHOLD = 1000; // Use streaming for files > 1000 rows
    /** Max extra rows after lastDataRowNum to include so trailing empty or sparse rows appear in the report. */
    private static final int TRAILING_ROW_LIMIT = 50;
    
    /**
     * Parse Excel file and return list of row data.
     * Delegates to {@link #parseExcel(InputStream, RelationshipConfig, Map, String)} with uploadOption null.
     */
    public static List<RelationshipRowData> parseExcel(InputStream inputStream, RelationshipConfig config, 
                                                       Map<String, String> columnMappings) 
            throws IOException {
        return parseExcel(inputStream, config, columnMappings, null);
    }

    /**
     * Parse Excel file and return list of row data
     * 
     * @param inputStream Excel file input stream
     * @param config Relationship configuration
     * @param columnMappings Optional column mappings from frontend (Excel column name -> Field name)
     * @param uploadOption Optional operation: "DELETE" so row range includes rows with only one entity (for report)
     * @return List of parsed row data
     * @throws IOException If file cannot be read
     */
    public static List<RelationshipRowData> parseExcel(InputStream inputStream, RelationshipConfig config, 
                                                       Map<String, String> columnMappings, String uploadOption) 
            throws IOException {
        
        logger.info("Parsing Excel file for relationship: {}", config.getDisplayName());
        
        List<RelationshipRowData> rows = new ArrayList<>();
        
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getPhysicalNumberOfRows();
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                logger.warn("Excel file has no data rows (only header or empty); lastRowNum={}", lastRowNum);
                return rows;
            }
            logger.info("Total rows in sheet: {}, lastRowNum: {} (parsing every data row 2 to {})", totalRows, lastRowNum, lastRowNum + 1);
            
            // Parse header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Excel file has no header row");
            }
            
            Map<Integer, String> columnMap = parseHeaderRow(headerRow);
            logger.debug("Parsed {} columns from header", columnMap.size());
            
            // Use last row that has meaningful data (at least one entity A or B identifier).
            // Rows with only dropdown/validation data (e.g. to row 1000) do not extend the range.
            // Stop when two consecutive rows both lack meaningful data so range does not extend past real data.
            int lastDataRowNum = 0;
            int firstDataRowNum = 0;
            int consecutiveEmpty = 0;
            for (int r = 1; r <= lastRowNum; r++) {
                Row rw = sheet.getRow(r);
                boolean meaningful = rw != null && rowHasMeaningfulDataForLastRow(rw, columnMap, config, columnMappings, uploadOption);
                if (meaningful) {
                    if (firstDataRowNum == 0) {
                        firstDataRowNum = r;
                    }
                    lastDataRowNum = r;
                    consecutiveEmpty = 0;
                } else {
                    consecutiveEmpty++;
                    if (consecutiveEmpty >= 2) {
                        break;
                    }
                }
            }
            if (lastDataRowNum < 1) {
                // Fallback: use same entity-based check (last row with any entity data)
                for (int r = 1; r <= lastRowNum; r++) {
                    Row rw = sheet.getRow(r);
                    if (rw != null && rowHasMeaningfulDataForLastRow(rw, columnMap, config, columnMappings, uploadOption)) {
                        lastDataRowNum = r;
                        if (firstDataRowNum == 0) {
                            firstDataRowNum = r;
                        }
                    }
                }
            }
            // Regulation X Policy: cap range so dropdown/validation spill to row 1000 does not extend parsed range (e.g. 9 rows only)
            if (config != null && "regulationxpolicy".equals(config.getKey()) && firstDataRowNum > 0) {
                lastDataRowNum = Math.min(lastDataRowNum, firstDataRowNum + 100);
            }
            // Regulation X Project: same cap so only real data rows (e.g. 9) are considered, not up to row 1000
            if (config != null && "regulationxproject".equals(config.getKey()) && firstDataRowNum > 0) {
                lastDataRowNum = Math.min(lastDataRowNum, firstDataRowNum + 100);
            }
            // System X Glossary: cap range so dropdown/validation spill does not extend parsed range
            if (config != null && ("systemxglossary".equals(config.getKey()) || "glossary_x_system".equals(config.getTableName())) && firstDataRowNum > 0) {
                lastDataRowNum = Math.min(lastDataRowNum, firstDataRowNum + 100);
            }
            // System X Legal: cap range so only real data rows (e.g. 9) are considered, not up to row 1000
            if (config != null && "systemxlegalentity".equals(config.getKey()) && firstDataRowNum > 0) {
                lastDataRowNum = Math.min(lastDataRowNum, firstDataRowNum + 100);
            }
            // Product X Legal: cap range so only real data rows (e.g. 9) are considered, not up to row 1000
            if (config != null && "productxlegalentity".equals(config.getKey()) && firstDataRowNum > 0) {
                lastDataRowNum = Math.min(lastDataRowNum, firstDataRowNum + 100);
            }
            // System X Client: cap range so only real data rows (e.g. 9) are considered, not up to row 1000
            if (config != null && "systemxclient".equals(config.getKey()) && firstDataRowNum > 0) {
                lastDataRowNum = Math.min(lastDataRowNum, firstDataRowNum + 100);
            }
            if (lastDataRowNum < 1) {
                logger.warn("Excel file has no data rows (only header or empty); lastDataRowNum={}", lastDataRowNum);
                return rows;
            }
            logger.info("Total rows in sheet: {}, lastRowNum: {}, last row with data: {} (parsing data rows 2 to {})",
                totalRows, lastRowNum, lastDataRowNum + 1, lastDataRowNum + 1);
            if (config != null && "capabilityxsystem".equals(config.getKey())) {
                logger.debug("Capability X System: entity-based lastDataRowNum={} (Excel row {}), parsing rows 2 to {}",
                    lastDataRowNum, lastDataRowNum + 1, lastDataRowNum + 1);
            }
            
            // Parse data rows: iterate every row from 2 to lastDataRowNum+1 (1-based).
            // Only treat as empty if row has no non-empty cell in any header-mapped column (avoids misclassifying sparse rows).
            for (int rowNum = 1; rowNum <= lastDataRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || !rowHasAnyDataInMappedColumns(row, columnMap)) {
                    logger.debug("Including empty row {} in report with validation error", rowNum + 1);
                    RelationshipRowData emptyRowData = new RelationshipRowData(rowNum + 1);
                    emptyRowData.addError("Row is empty or has no data");
                    if (config != null && config.isRequiresRelationType()) {
                        emptyRowData.addError("Relationship Type is required but not provided.");
                    }
                    rows.add(emptyRowData);
                    continue;
                }
                
                try {
                    RelationshipRowData rowData = parseDataRow(row, rowNum + 1, columnMap, columnMappings);
                    rows.add(rowData);
                } catch (Exception e) {
                    logger.error("Error parsing row {}: {}", rowNum + 1, e.getMessage());
                    // Add error row to maintain row numbers
                    RelationshipRowData errorRow = new RelationshipRowData(rowNum + 1);
                    errorRow.addError("Failed to parse row: " + e.getMessage());
                    rows.add(errorRow);
                }
            }
            
            // Include a bounded number of trailing rows (e.g. user-added row with cap ref/name empty) so they appear in the report
            int trailingLimit = Math.min(lastRowNum, lastDataRowNum + TRAILING_ROW_LIMIT);
            for (int rowNum = lastDataRowNum + 1; rowNum <= trailingLimit; rowNum++) {
                Row trailingRow = sheet.getRow(rowNum);
                if (trailingRow == null || !rowHasAnyDataInMappedColumns(trailingRow, columnMap)) {
                    RelationshipRowData emptyRowData = new RelationshipRowData(rowNum + 1);
                    emptyRowData.addError("Row is empty or has no data");
                    if (config != null && config.isRequiresRelationType()) {
                        emptyRowData.addError("Relationship Type is required but not provided.");
                    }
                    rows.add(emptyRowData);
                    continue;
                }
                try {
                    RelationshipRowData rowData = parseDataRow(trailingRow, rowNum + 1, columnMap, columnMappings);
                    rows.add(rowData);
                } catch (Exception e) {
                    logger.error("Error parsing trailing row {}: {}", rowNum + 1, e.getMessage());
                    RelationshipRowData errorRow = new RelationshipRowData(rowNum + 1);
                    errorRow.addError("Failed to parse row: " + e.getMessage());
                    rows.add(errorRow);
                }
            }
            
            logger.info("Successfully parsed {} data rows", rows.size());
        }
        
        return rows;
    }
    
    /**
     * Parse header row and create column mapping
     */
    private static Map<Integer, String> parseHeaderRow(Row headerRow) {
        Map<Integer, String> columnMap = new HashMap<>();
        
        for (int cellNum = 0; cellNum < headerRow.getLastCellNum(); cellNum++) {
            Cell cell = headerRow.getCell(cellNum);
            if (cell != null) {
                String columnName = getCellValueAsString(cell);
                if (columnName != null && !columnName.trim().isEmpty()) {
                    columnMap.put(cellNum, columnName.trim());
                }
            }
        }
        
        return columnMap;
    }
    
    /**
     * Parse a data row
     */
    private static RelationshipRowData parseDataRow(Row row, int rowNumber, Map<Integer, String> columnMap, 
                                                   Map<String, String> columnMappings) {
        RelationshipRowData rowData = new RelationshipRowData(rowNumber);
        
        for (Map.Entry<Integer, String> entry : columnMap.entrySet()) {
            int cellNum = entry.getKey();
            String excelColumnName = entry.getValue();
            
            Cell cell = row.getCell(cellNum);
            String value = getCellValueAsString(cell);
            
            // Trim and clean value
            if (value != null) {
                value = value.trim();
            }
            
            // If column mappings provided, use mapped field name; otherwise use Excel column name
            if (columnMappings != null && columnMappings.containsKey(excelColumnName)) {
                String mappedFieldName = columnMappings.get(excelColumnName);
                // Store both: mapped name as key, and original Excel name for reference
                rowData.addOriginalValue(mappedFieldName, value);
                // Also keep original Excel column name for backward compatibility
                rowData.addOriginalValue(excelColumnName, value);
            } else {
                // No mapping, use Excel column name as-is
                rowData.addOriginalValue(excelColumnName, value);
            }
        }
        
        return rowData;
    }
    
    /**
     * Get cell value as string, handling all cell types
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                    
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        // Handle date as string
                        return cell.getDateCellValue().toString();
                    } else {
                        // Handle numeric - remove .0 for integers
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == (long) numericValue) {
                            return String.valueOf((long) numericValue);
                        } else {
                            return String.valueOf(numericValue);
                        }
                    }
                    
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                    
                case FORMULA:
                    // Try to evaluate formula
                    try {
                        return cell.getStringCellValue();
                    } catch (IllegalStateException e) {
                        // Formula evaluates to numeric
                        try {
                            double numValue = cell.getNumericCellValue();
                            if (numValue == (long) numValue) {
                                return String.valueOf((long) numValue);
                            } else {
                                return String.valueOf(numValue);
                            }
                        } catch (Exception ex) {
                            return null;
                        }
                    }
                    
                case BLANK:
                    return null;
                    
                default:
                    return null;
            }
        } catch (Exception e) {
            logger.warn("Error reading cell value: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if row is empty
     */
    @SuppressWarnings("unused")
    private static boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        
        for (int cellNum = 0; cellNum < row.getLastCellNum(); cellNum++) {
            Cell cell = row.getCell(cellNum);
            String value = getCellValueAsString(cell);
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Returns true if the row has at least one non-empty cell in a column that appears in the header map.
     * Used so sparse rows (e.g. data only in a few columns) are not misclassified as empty.
     */
    private static boolean rowHasAnyDataInMappedColumns(Row row, Map<Integer, String> columnMap) {
        if (row == null || columnMap == null || columnMap.isEmpty()) {
            return false;
        }
        for (Integer cellNum : columnMap.keySet()) {
            Cell cell = row.getCell(cellNum);
            String value = getCellValueAsString(cell);
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds a map of header name to cell value from a POI row and column map.
     * Mirrors parseDataRow so that findExcelColumnName / hasEntityValue work the same way.
     */
    private static Map<String, String> buildRowValuesFromSheetRow(Row row, Map<Integer, String> columnMap,
                                                                   Map<String, String> columnMappings) {
        Map<String, String> values = new HashMap<>();
        if (row == null || columnMap == null || columnMap.isEmpty()) {
            return values;
        }
        for (Map.Entry<Integer, String> entry : columnMap.entrySet()) {
            int cellNum = entry.getKey();
            String excelColumnName = entry.getValue();
            Cell cell = row.getCell(cellNum);
            String value = getCellValueAsString(cell);
            if (value != null) {
                value = value.trim();
            }
            if (columnMappings != null && columnMappings.containsKey(excelColumnName)) {
                String mappedFieldName = columnMappings.get(excelColumnName);
                values.put(mappedFieldName, value);
                values.put(excelColumnName, value);
            } else {
                values.put(excelColumnName, value);
            }
        }
        return values;
    }

    /**
     * Returns true if the row has at least one entity (A or B) identifier (Ref or Name).
     * Used when computing lastDataRowNum so that rows with only dropdown/validation data
     * (e.g. extending to row 1000) do not extend the parsed range.
     * When config is null, falls back to requiring at least two non-empty mapped cells.
     * For configs with relationship type, we do not require the relationship-type column to be
     * non-empty for a row to be meaningful; rows with entity A and B data but empty relationship
     * type extend the range, are parsed, and appear in the report (with validation errors).
     * For DELETE when relationship type is required, uses a looser rule (hasA || hasB) so rows
     * with only one entity are still parsed and appear in the report with validation errors.
     */
    private static boolean rowHasMeaningfulDataForLastRow(Row row, Map<Integer, String> columnMap,
                                                          RelationshipConfig config, Map<String, String> columnMappings,
                                                          String uploadOption) {
        if (row == null || columnMap == null || columnMap.isEmpty()) {
            return false;
        }
        if (config == null) {
            int nonEmptyCount = 0;
            for (Integer cellNum : columnMap.keySet()) {
                Cell cell = row.getCell(cellNum);
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    nonEmptyCount++;
                    if (nonEmptyCount >= 2) {
                        return true;
                    }
                }
            }
            return false;
        }
        Map<String, String> values = buildRowValuesFromSheetRow(row, columnMap, columnMappings);
        // DELETE when relationship type is required (productxlegalentity): include rows that have the Relationship Type column (even empty) so they appear in the report
        if ("DELETE".equals(uploadOption) && config != null && config.isRequiresRelationType() && "productxlegalentity".equals(config.getKey())) {
            for (String key : values.keySet()) {
                if (key != null) {
                    String k = key.toLowerCase();
                    if ((k.contains("relationship") && k.contains("type")) || (k.contains("relation") && k.contains("type"))) {
                        return true;
                    }
                }
            }
        }
        // System X Client (systemxclient): include rows that have the Relationship Type column (even empty) so empty-relationship-type rows extend range and appear in report (INSERT and DELETE)
        if (config != null && config.isRequiresRelationType() && "systemxclient".equals(config.getKey())) {
            for (String key : values.keySet()) {
                if (key != null) {
                    String k = key.toLowerCase();
                    if ((k.contains("relationship") && k.contains("type")) || (k.contains("relation") && k.contains("type"))) {
                        return true;
                    }
                }
            }
        }
        if (config == null) {
            return false;
        }
        RelationshipRowData tmp = new RelationshipRowData(0);
        tmp.setOriginalValues(values);
        boolean hasA = hasEntityValue(tmp, config.getEntityA(), columnMappings, config.getEntityARole());
        boolean hasB = hasEntityValue(tmp, config.getEntityB(), columnMappings, config.getEntityBRole());
        // DELETE when relationship type is required: include rows that have entity data or the relationship type column (even empty) so empty-relationship-type rows appear in report for all configs
        if ("DELETE".equals(uploadOption) && config != null && config.isRequiresRelationType()) {
            boolean hasRelationshipTypeKey = false;
            for (String key : values.keySet()) {
                if (key != null) {
                    String k = key.toLowerCase();
                    if ((k.contains("relationship") && k.contains("type")) || (k.contains("relation") && k.contains("type"))) {
                        hasRelationshipTypeKey = true;
                        break;
                    }
                }
            }
            if (hasA || hasB || hasRelationshipTypeKey) {
                return true;
            }
        }
        // Glossary X System / System X Glossary: only extend range when row has both Glossary (Ref or Name) and System (Short Name).
        // Do not treat row as meaningful based only on Strategic Data Set Name or Relationship Type (avoids spill to row 1000).
        // For DELETE when relationship type is required, also include rows that have the Relationship Type column (even empty) so they appear in the report.
        if (config != null && ("glossaryxsystem".equals(config.getKey()) || "systemxglossary".equals(config.getKey()) || "glossary_x_system".equals(config.getTableName()))) {
            if ("DELETE".equals(uploadOption) && config.isRequiresRelationType()) {
                for (String key : values.keySet()) {
                    if (key != null) {
                        String k = key.toLowerCase();
                        if ((k.contains("relationship") && k.contains("type")) || (k.contains("relation") && k.contains("type"))) {
                            return true;
                        }
                    }
                }
            }
            boolean hasGlossary = false;
            boolean hasSystemShortName = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("glossary") && (k.contains("ref") || k.contains("name")) && !k.contains("parent")) {
                    hasGlossary = true;
                }
                if (k.contains("system") && k.contains("short") && k.contains("name")) {
                    hasSystemShortName = true;
                }
                if (hasGlossary && hasSystemShortName) {
                    return true;
                }
            }
            return false;
        }
        // System X Legal: only extend range when row has both System and Legal identifier data (not relationship-type-only so dropdown spill does not extend to row 1000).
        // For DELETE when relationship type is required, extend when row has system, legal, or relationship-type column so empty-relationship-type rows appear in report.
        if (config != null && "systemxlegalentity".equals(config.getKey())) {
            if ("DELETE".equals(uploadOption) && config.isRequiresRelationType()) {
                for (String key : values.keySet()) {
                    if (key != null) {
                        String k = key.toLowerCase();
                        if ((k.contains("relationship") && k.contains("type")) || (k.contains("relation") && k.contains("type"))) {
                            return true;
                        }
                    }
                }
            }
            boolean hasSystem = false;
            boolean hasLegal = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("system") && ((k.contains("short") && k.contains("name")) || k.contains("name")) && !k.contains("legal") && !k.contains("relationship")) {
                    hasSystem = true;
                }
                if (k.contains("legal") && (k.contains("short") || k.contains("name")) && !k.contains("relationship")) {
                    hasLegal = true;
                }
                if (hasSystem && hasLegal) return true;
            }
            return false;
        }
        // Policy X Policy: only extend range when row has both Source Policy and Target Policy identifier data (not validation/dropdown-only rows).
        if (config != null && "policyxpolicy".equals(config.getKey())) {
            boolean hasSourcePolicy = false;
            boolean hasTargetPolicy = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("source") && k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasSourcePolicy = true;
                }
                if (k.contains("target") && k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasTargetPolicy = true;
                }
                boolean both = hasSourcePolicy && hasTargetPolicy;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasSourcePolicy || hasTargetPolicy);
                if (both || eitherForDelete) {
                    return true;
                }
            }
            return false;
        }
        // Policy X Project: only extend range when row has both Policy and Project identifier data (not validation/dropdown-only rows).
        if (config != null && "policyxproject".equals(config.getKey())) {
            boolean hasPolicy = false;
            boolean hasProject = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("relationship")) {
                    hasProject = true;
                }
                if (hasPolicy && hasProject) return true;
            }
            return false;
        }
        // Policy X System: only extend range when row has both Policy and System identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "policyxsystem".equals(config.getKey())) {
            boolean hasPolicy = false;
            boolean hasSystem = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                if (k.contains("system") && k.contains("short") && k.contains("name")) {
                    hasSystem = true;
                }
                boolean both = hasPolicy && hasSystem;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasPolicy || hasSystem);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Process X Client: only extend range when row has both Client and Process identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "processxclient".equals(config.getKey())) {
            boolean hasClient = false;
            boolean hasProcess = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("client") && k.contains("name")) {
                    hasClient = true;
                }
                if (k.contains("process") && (k.contains("ref") || k.contains("name"))) {
                    hasProcess = true;
                }
                boolean both = hasClient && hasProcess;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasClient || hasProcess);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Product X Client: only extend range when row has both Product and Client identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has product, client, or relationship-type column so empty-relationship-type rows appear in report.
        if (config != null && "productxclient".equals(config.getKey())) {
            boolean hasProduct = false;
            boolean hasClient = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (val == null || val.trim().isEmpty()) continue;
                if (k.contains("product") && (k.contains("ref") || k.contains("name")) && !k.contains("parent")) {
                    hasProduct = true;
                }
                if (k.contains("client") && k.contains("name")) {
                    hasClient = true;
                }
            }
            boolean both = hasProduct && hasClient;
            boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProduct || hasClient || hasRelationshipTypeKey);
            return both || eitherForDelete;
        }
        // Product X Legal Entity: only extend range when row has Legal identifier (and optionally Product) (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has legal, product, or relationship-type column so empty-relationship-type rows appear in report.
        if (config != null && "productxlegalentity".equals(config.getKey())) {
            boolean hasProduct = false;
            boolean hasLegal = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (val == null || val.trim().isEmpty()) continue;
                if (k.contains("product") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProduct = true;
                }
                if (k.contains("legal") && (k.contains("short") || k.contains("name")) && !k.contains("relationship")) {
                    hasLegal = true;
                }
            }
            boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasLegal || hasProduct || hasRelationshipTypeKey);
            return hasLegal || eitherForDelete;
        }
        // Process X Data Set: only extend range when row has both Process and Data Set identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "processxdataset".equals(config.getKey())) {
            boolean hasProcess = false;
            boolean hasDataSet = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("process") && (k.contains("ref") || k.contains("name"))) {
                    hasProcess = true;
                }
                // Data Set Ref. or Data Set Name (exclude "Data Set System Short Name" for range extension so relationship-type-only rows don't extend to 1000)
                if ((k.contains("data") && k.contains("set")) && (k.contains("ref") || k.contains("reference") || (k.contains("name") && !k.contains("short")))) {
                    hasDataSet = true;
                }
                boolean both = hasProcess && hasDataSet;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProcess || hasDataSet);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Process X Glossary: only extend range when row has both Glossary and Process identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "processxglossary".equals(config.getKey())) {
            boolean hasGlossary = false;
            boolean hasProcess = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("glossary") && (k.contains("ref") || k.contains("reference") || k.contains("name") || k.contains("parent"))) {
                    hasGlossary = true;
                }
                if (k.contains("process") && (k.contains("ref") || k.contains("reference") || k.contains("name"))) {
                    hasProcess = true;
                }
                boolean both = hasGlossary && hasProcess;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasGlossary || hasProcess);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Process X Legal Entity: only extend range when row has both Process and Legal identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either process/legal or a relationship-type column key so empty-relationship-type rows appear in report.
        if (config != null && "processxlegalentity".equals(config.getKey())) {
            boolean hasProcess = false;
            boolean hasLegal = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                String k = key != null ? key.toLowerCase() : "";
                if (!k.isEmpty() && k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (key == null || val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("process") && (k.contains("ref") || k.contains("reference") || k.contains("name"))) {
                    hasProcess = true;
                }
                if (k.contains("legal") && (k.contains("short") || k.contains("name")) && !k.contains("relationship")) {
                    hasLegal = true;
                }
            }
            boolean both = hasProcess && hasLegal;
            boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProcess || hasLegal || hasRelationshipTypeKey);
            return both || eitherForDelete;
        }
        // Process X Product: only extend range when row has both Product and Process identifier data (not validation/dropdown-only rows).
        if (config != null && "processxproduct".equals(config.getKey())) {
            boolean hasProduct = false;
            boolean hasProcess = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("product") && (k.contains("ref") || k.contains("name")) && !k.contains("relationship")) {
                    if (!k.contains("parent")) hasProduct = true;
                }
                if (k.contains("process") && (k.contains("ref") || k.contains("name")) && !k.contains("relationship")) {
                    if (!k.contains("parent")) hasProcess = true;
                }
                if (hasProduct && hasProcess) return true;
            }
            return false;
        }
        // Process X Process: only extend range when row has both Process (source) and Predecessor Process identifier data (not validation/dropdown-only rows).
        // For DELETE, also include rows with only one process or a relationship-type column so empty-relationship-type and error rows appear in the report.
        if (config != null && "processxprocess".equals(config.getKey())) {
            if ("DELETE".equals(uploadOption)) {
                boolean hasRelationshipTypeKey = false;
                for (String key : values.keySet()) {
                    if (key != null) {
                        String k = key.toLowerCase();
                        if (k.contains("relationship") && k.contains("type")) {
                            hasRelationshipTypeKey = true;
                            break;
                        }
                    }
                }
                return hasA || hasB || hasRelationshipTypeKey;
            }
            return hasA && hasB;
        }
        // Process X System: only extend range when row has both Process and System identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "processxsystem".equals(config.getKey())) {
            boolean hasProcess = false;
            boolean hasSystem = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("process") && (k.contains("ref") || k.contains("name"))) {
                    hasProcess = true;
                }
                if (k.contains("system") && k.contains("name")) {
                    hasSystem = true;
                }
                boolean both = hasProcess && hasSystem;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProcess || hasSystem);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Project X System: only extend range when row has both Project and System identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-project or empty-relationship-type rows appear in report.
        if (config != null && "projectxsystem".equals(config.getKey())) {
            boolean hasProject = false;
            boolean hasSystem = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("project") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                if (k.contains("system") && (k.contains("name") || k.contains("short"))) {
                    hasSystem = true;
                }
                boolean both = hasProject && hasSystem;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProject || hasSystem);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Product X Business Area: only extend range when row has both Product and Business Area identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "productxbusinessarea".equals(config.getKey())) {
            boolean hasProduct = false;
            boolean hasBusinessArea = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("product") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProduct = true;
                }
                if (k.contains("business") && k.contains("area") && !k.contains("parent") && !k.contains("relationship")) {
                    if (k.contains("name") || (!k.contains("ref") && !k.contains("reference") && !k.contains("short"))) {
                        hasBusinessArea = true;
                    }
                }
                boolean both = hasProduct && hasBusinessArea;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProduct || hasBusinessArea);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Project X Attribute: only extend range when row has Project or Attribute identifier data (not relationship-type-only rows, to avoid extending to row 1000).
        // For DELETE when relationship type is required, also include rows with relationship-type column so empty project/attribute rows appear in report.
        if (config != null && "projectxattribute".equals(config.getKey())) {
            boolean hasProject = false;
            boolean hasAttribute = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent")) {
                    hasProject = true;
                }
                if (k.contains("attribute") && (k.contains("ref") || k.contains("name") || k.contains("data set") || k.contains("dataset") || k.contains("system")) && !k.contains("relationship")) {
                    hasAttribute = true;
                }
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProject || hasAttribute || hasRelationshipTypeKey);
                if ((hasProject || hasAttribute) || eitherForDelete) return true;
            }
            return (hasProject || hasAttribute) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && hasRelationshipTypeKey);
        }
        // Project X Business Area: only extend range when row has both Project and Business Area identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "projectxbusinessarea".equals(config.getKey())) {
            boolean hasProject = false;
            boolean hasBusinessArea = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                if (k.contains("business") && k.contains("area") && !k.contains("parent") && !k.contains("relationship")) {
                    if (k.contains("name") || (!k.contains("ref") && !k.contains("reference") && !k.contains("short"))) {
                        hasBusinessArea = true;
                    }
                }
                boolean both = hasProject && hasBusinessArea;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProject || hasBusinessArea);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Project X Capability: only extend range when row has both Project and Capability identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "projectxcapability".equals(config.getKey())) {
            boolean hasProject = false;
            boolean hasCapability = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                if (k.contains("capability") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasCapability = true;
                }
                boolean both = hasProject && hasCapability;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProject || hasCapability);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Project X Client: only extend range when row has both Project and Client identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "projectxclient".equals(config.getKey())) {
            boolean hasProject = false;
            boolean hasClient = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                if (k.contains("client") && (k.contains("name") || k.contains("ref")) && !k.contains("relationship")) {
                    hasClient = true;
                }
                boolean both = hasProject && hasClient;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProject || hasClient);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Project X Data Set: only extend range when row has both Project and Data Set identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "projectxdataset".equals(config.getKey())) {
            boolean hasProject = false;
            boolean hasDataSet = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                // Data Set Ref. or Data Set Name (exclude "Data Set System Short Name" for range extension so relationship-type-only rows don't extend to 1000)
                if ((k.contains("data") && k.contains("set")) && (k.contains("ref") || k.contains("reference") || (k.contains("name") && !k.contains("short")))) {
                    hasDataSet = true;
                }
                boolean both = hasProject && hasDataSet;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProject || hasDataSet);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Project X Glossary: only extend range when row has both Glossary and Project identifier data (not relationship type/description spill to row 1000).
        // For DELETE when relationship type is required, also treat as meaningful when row has glossary, project, or relationship-type column so empty-project/relationship-type-only rows appear in report.
        if (config != null && "projectxglossary".equals(config.getKey())) {
            boolean hasGlossary = false;
            boolean hasProject = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("glossary") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasGlossary = true;
                }
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                boolean both = hasGlossary && hasProject;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasGlossary || hasProject || hasRelationshipTypeKey);
                if (both || eitherForDelete) return true;
            }
            return (hasGlossary && hasProject) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasGlossary || hasProject || hasRelationshipTypeKey));
        }
        // Project X Product: only extend range when row has both Product and Project identifier data (not relationship type/description spill to row 1000).
        // Also treat as meaningful when row has product, project, or relationship-type column; for DELETE when relationship type is required, include relationship-type-only rows so they appear in report.
        if (config != null && "projectxproduct".equals(config.getKey())) {
            boolean hasProduct = false;
            boolean hasProject = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("product") && k.contains("name") && !k.contains("parent") && !k.contains("relationship")) {
                    hasProduct = true;
                }
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                boolean both = hasProduct && hasProject;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProduct || hasProject || hasRelationshipTypeKey);
                if (both || hasProduct || hasProject || eitherForDelete) return true;
            }
            return (hasProduct && hasProject) || (hasProduct || hasProject) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProduct || hasProject || hasRelationshipTypeKey));
        }
        // Project X Process: only extend range when row has both Project and Process identifier data (not validation/dropdown spill to row 1000).
        // For DELETE when relationship type is required, extend when row has either so empty-project/empty-process rows appear in report.
        if (config != null && "projectxprocess".equals(config.getKey())) {
            boolean hasProject = false;
            boolean hasProcess = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                if (k.contains("process") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProcess = true;
                }
                boolean both = hasProject && hasProcess;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasProject || hasProcess);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Project X Project: only extend range when row has both Source Project and Target Project identifier data (not relationship type/description spill to row 1000).
        // For DELETE when relationship type is required, also treat as meaningful when row has source project, target project, or relationship-type column so they appear in report.
        if (config != null && "projectxproject".equals(config.getKey())) {
            boolean hasSourceProject = false;
            boolean hasTargetProject = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("source") && k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasSourceProject = true;
                }
                if (k.contains("target") && k.contains("project") && (k.contains("ref") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasTargetProject = true;
                }
                boolean both = hasSourceProject && hasTargetProject;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasSourceProject || hasTargetProject || hasRelationshipTypeKey);
                if (both || eitherForDelete) return true;
            }
            return (hasSourceProject && hasTargetProject) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasSourceProject || hasTargetProject || hasRelationshipTypeKey));
        }
        // Regulation X Policy: only extend range when row has both Regulation and Policy identifier data (not relationship-type-only so dropdown spill does not extend to row 1000).
        // For DELETE when relationship type is required, extend when row has regulation, policy, or relationship type column (even empty) so empty-relationship-type rows appear in report.
        if (config != null && "regulationxpolicy".equals(config.getKey())) {
            boolean hasRegulation = false;
            boolean hasPolicy = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                String k = key != null ? key.toLowerCase() : "";
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (key == null || val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("regulation") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("policy") && !k.contains("parent")) {
                    hasRegulation = true;
                }
                if (k.contains("policy") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("parent")) {
                    hasPolicy = true;
                }
                boolean both = hasRegulation && hasPolicy;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasPolicy || hasRelationshipTypeKey);
                if (both || eitherForDelete) return true;
            }
            return (hasRegulation && hasPolicy) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasPolicy || hasRelationshipTypeKey));
        }
        // Regulation X Product: only extend range when row has both Regulation and Product identifier data (not relationship type/description spill to row 1000).
        // For DELETE when relationship type is required, also treat as meaningful when row has regulation, product, or relationship-type column so empty-relationship-type rows appear in report.
        if (config != null && "regulationxproduct".equals(config.getKey())) {
            boolean hasRegulation = false;
            boolean hasProduct = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("regulation") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("policy") && !k.contains("parent") && !k.contains("product")) {
                    hasRegulation = true;
                }
                if (k.contains("product") && k.contains("name") && !k.contains("parent")) {
                    hasProduct = true;
                }
                boolean both = hasRegulation && hasProduct;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasProduct || hasRelationshipTypeKey);
                if (both || eitherForDelete) return true;
            }
            return (hasRegulation && hasProduct) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasProduct || hasRelationshipTypeKey));
        }
        // Regulation X Project: only extend range when row has both Regulation and Project identifier data (not dropdown/validation spill to row 1000).
        // For DELETE when relationship type is required, extend when row has regulation, project, or relationship-type column so partial rows appear in report.
        if (config != null && "regulationxproject".equals(config.getKey())) {
            boolean hasRegulation = false;
            boolean hasProject = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                String k = key != null ? key.toLowerCase() : "";
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (key == null || val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("regulation") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("policy") && !k.contains("parent") && !k.contains("project")) {
                    hasRegulation = true;
                }
                if (k.contains("project") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("parent") && !k.contains("relationship")) {
                    hasProject = true;
                }
                boolean both = hasRegulation && hasProject;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasProject || hasRelationshipTypeKey);
                if (both || eitherForDelete) return true;
            }
            return (hasRegulation && hasProject) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasProject || hasRelationshipTypeKey));
        }
        // Regulation X Regulator: only extend range when row has both Regulation and Regulator identifier data (not relationship type/description spill to row 1000).
        // For DELETE when relationship type is required, also include rows with regulation only, regulator only, or relationship-type column (even empty) so they appear in report.
        if (config != null && "regulationxregulator".equals(config.getKey())) {
            boolean hasRegulation = false;
            boolean hasRegulator = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                String k = key != null ? key.toLowerCase() : "";
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (key == null || val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("regulation") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("policy") && !k.contains("parent") && !k.contains("regulator")) {
                    hasRegulation = true;
                }
                if (k.contains("regulator") && k.contains("name") && !k.contains("parent")) {
                    hasRegulator = true;
                }
                boolean both = hasRegulation && hasRegulator;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasRegulator || hasRelationshipTypeKey);
                if (both || eitherForDelete) return true;
            }
            return (hasRegulation && hasRegulator) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasRegulator || hasRelationshipTypeKey));
        }
        // Regulation X Regulatory Theme: only extend range when row has both Regulation and Regulatory Theme identifier data (not relationship type/description spill to row 1000).
        // For DELETE when relationship type is required, also include rows with regulation only, theme only, or relationship-type column so they appear in report.
        if (config != null && "regulationxregulatorytheme".equals(config.getKey())) {
            boolean hasRegulation = false;
            boolean hasRegulatoryTheme = false;
            boolean hasRelationshipTypeKey = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                String k = key != null ? key.toLowerCase() : "";
                if (k.contains("relationship") && k.contains("type")) {
                    hasRelationshipTypeKey = true;
                }
                if (key == null || val == null || val.trim().isEmpty()) continue;
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("regulation") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("policy") && !k.contains("parent") && !k.contains("regulatorytheme")) {
                    hasRegulation = true;
                }
                if (k.contains("regulatory") && k.contains("theme") && (k.contains("ref") || k.contains("reference") || k.contains("name")) && !k.contains("parent")) {
                    hasRegulatoryTheme = true;
                }
                boolean both = hasRegulation && hasRegulatoryTheme;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasRegulatoryTheme || hasRelationshipTypeKey);
                if (both || eitherForDelete) return true;
            }
            return (hasRegulation && hasRegulatoryTheme) || ("DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasRegulation || hasRegulatoryTheme || hasRelationshipTypeKey));
        }
        // When both entities are required (e.g. Glossary X Client, Glossary X Product), only treat row as meaningful if it has both values,
        // so rows with only one column filled (e.g. validation/dropdown spill to row 1000) do not extend the range
        if (config != null && config.getEntityA().isRequired() && config.getEntityB().isRequired()) {
            return hasA && hasB;
        }
        if (hasA || hasB) {
            return true;
        }
        // For Data Set X Legal Entity (and similar), consider row meaningful if it has any non-empty value in a key column
        // so that all data rows are parsed and appear in the report even when header/column resolution differs slightly
        if (config != null && "datasetxlegalentity".equals(config.getKey())) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key != null && val != null && !val.trim().isEmpty()) {
                    String k = key.toLowerCase();
                    if (k.contains("legal") || k.contains("data set") || k.contains("dataset") || k.contains("relationship type")) {
                        return true;
                    }
                }
            }
        }
        // Data Set X Product: only extend range when row has product/dataset/relationship-type data (not validation/dropdown-only rows)
        if (config != null && "datasetxproduct".equals(config.getKey())) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key != null && val != null && !val.trim().isEmpty()) {
                    String k = key.toLowerCase();
                    boolean isProduct = k.contains("product") && (k.contains("name") || k.contains("ref")) && !k.contains("parent");
                    boolean isDataSet = (k.contains("data") && k.contains("set")) && (k.contains("name") || k.contains("ref"));
                    boolean isRelationshipType = k.contains("relationship") && k.contains("type");
                    if (isProduct || isDataSet || isRelationshipType) {
                        return true;
                    }
                }
            }
        }
        // Glossary X Glossary: only extend range when row has source/target glossary or relationship-type data (not validation/dropdown-only rows)
        if (config != null && "glossaryxglossary".equals(config.getKey())) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key != null && val != null && !val.trim().isEmpty()) {
                    String k = key.toLowerCase();
                    boolean isSourceGlossary = k.contains("source") && k.contains("glossary") && (k.contains("name") || k.contains("ref"));
                    boolean isTargetGlossary = k.contains("target") && k.contains("glossary") && (k.contains("name") || k.contains("ref"));
                    boolean isRelationshipType = k.contains("relationship") && k.contains("type");
                    if (isSourceGlossary || isTargetGlossary || isRelationshipType) {
                        return true;
                    }
                }
            }
        }
        // Regulator X Geography: extend range when row has Regulator name OR Geography (name/parent) data so rows with empty Geography still appear in report (INSERT and DELETE)
        if (config != null && "regulatorxgeography".equals(config.getKey())) {
            boolean hasRegulatorName = false;
            boolean hasGeographyData = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("regulator") && k.contains("name") && !k.contains("parent")) {
                    hasRegulatorName = true;
                }
                if ((k.contains("geography") || k.contains("geo")) && k.contains("name")) {
                    hasGeographyData = true;
                }
                if (hasRegulatorName || hasGeographyData) {
                    return true;
                }
            }
            return false;
        }
        // People X People: extend range when row has Manager Email OR Employee Email key-column data so all rows appear in report (INSERT and DELETE)
        if (config != null && "peoplexpeople".equals(config.getKey())) {
            boolean hasManagerEmail = false;
            boolean hasEmployeeEmail = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("manager") && k.contains("email")) {
                    hasManagerEmail = true;
                }
                if (k.contains("employee") && k.contains("email")) {
                    hasEmployeeEmail = true;
                }
                if (hasManagerEmail || hasEmployeeEmail) {
                    return true;
                }
            }
            return false;
        }
        // Policy X Business Area: only extend range when row has both Policy and Business Area key-column data (and optionally Relationship Type)
        if (config != null && "policyxbusinessarea".equals(config.getKey())) {
            boolean hasPolicy = false;
            boolean hasBusinessArea = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                if (k.contains("business") && k.contains("area") && k.contains("name") && !k.contains("relationship")) {
                    hasBusinessArea = true;
                }
                if (hasPolicy && hasBusinessArea) return true;
            }
            return false;
        }
        // Legal Entity X Geography: only extend range when row has both Legal and Geography identifier data (not validation/dropdown-only rows)
        if (config != null && "legalentityxgeography".equals(config.getKey())) {
            boolean hasLegal = false;
            boolean hasGeography = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if ((k.contains("legal") && (k.contains("short") || k.contains("name"))) || (k.contains("parent") && k.contains("legal"))) {
                    hasLegal = true;
                }
                if (k.contains("geography") && !k.contains("relationship")) {
                    hasGeography = true;
                }
                if (hasLegal && hasGeography) return true;
            }
            return false;
        }
        // Policy X Attribute: only extend range when row has both Policy and Attribute identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "policyxattribute".equals(config.getKey())) {
            boolean hasPolicy = false;
            boolean hasAttribute = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                if (k.contains("attribute") && (k.contains("ref") || k.contains("name")) && !k.contains("relationship")) {
                    hasAttribute = true;
                }
                boolean both = hasPolicy && hasAttribute;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasPolicy || hasAttribute);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Process X Attribute: only extend range when row has both Process and Attribute identifier data (not validation/dropdown-only rows).
        // Exclude Attribute Data Set Name and Attribute System Short Name from counting as attribute identifier.
        if (config != null && "processxattribute".equals(config.getKey())) {
            boolean hasProcess = false;
            boolean hasAttribute = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("process") && (k.contains("ref") || k.contains("name")) && !k.contains("relationship")) {
                    hasProcess = true;
                }
                if (k.contains("attribute") && (k.contains("ref") || k.contains("name")) && !k.contains("relationship")) {
                    if (!((k.contains("data") && k.contains("set")) || (k.contains("system") && k.contains("short")))) {
                        hasAttribute = true;
                    }
                }
                if (hasProcess && hasAttribute) return true;
            }
            return false;
        }
        // Policy X Data Set: only extend range when row has both Policy and Dataset key-column data (and optionally Relationship Type).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "policyxdataset".equals(config.getKey())) {
            boolean hasPolicy = false;
            boolean hasDataset = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                boolean isDataSetCol = (k.contains("data") && k.contains("set") && (k.contains("name") || k.contains("ref")))
                    || (k.contains("dataset") && (k.contains("name") || k.contains("ref")));
                if (isDataSetCol && !k.contains("relationship")) {
                    hasDataset = true;
                }
                boolean both = hasPolicy && hasDataset;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasPolicy || hasDataset);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Policy X Legal Entity: only extend range when row has both Policy and Legal key-column data (and optionally Relationship Type).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "policyxlegalentity".equals(config.getKey())) {
            boolean hasPolicy = false;
            boolean hasLegal = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                if (k.contains("legal") && (k.contains("short") || k.contains("name")) && !k.contains("relationship")) {
                    hasLegal = true;
                }
                boolean bothPolicyLegal = hasPolicy && hasLegal;
                boolean eitherForDeleteLegal = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasPolicy || hasLegal);
                if (bothPolicyLegal || eitherForDeleteLegal) return true;
            }
            return false;
        }
        // Policy X Process: only extend range when row has both Policy and Process key-column data (and optionally Relationship Type).
        // For DELETE or INSERT when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "policyxprocess".equals(config.getKey())) {
            boolean hasPolicy = false;
            boolean hasProcess = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                if (k.contains("process") && (k.contains("ref") || k.contains("name")) && !k.contains("relationship")) {
                    hasProcess = true;
                }
                boolean both = hasPolicy && hasProcess;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasPolicy || hasProcess);
                boolean eitherForInsert = !"DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasPolicy || hasProcess);
                if (both || eitherForDelete || eitherForInsert) return true;
            }
            return false;
        }
        // Policy X Client: only extend range when row has both Client and Policy identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "policyxclient".equals(config.getKey())) {
            boolean hasClient = false;
            boolean hasPolicy = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("client") && (k.contains("ref") || k.contains("name"))) {
                    hasClient = true;
                }
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                boolean both = hasClient && hasPolicy;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasClient || hasPolicy);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        // Policy X Glossary: only extend range when row has both Policy and Glossary identifier data (not validation/dropdown-only rows).
        // For DELETE when relationship type is required, extend when row has either so empty-relationship-type rows appear in report.
        if (config != null && "policyxglossary".equals(config.getKey())) {
            boolean hasPolicy = false;
            boolean hasGlossary = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null || val.trim().isEmpty()) continue;
                String k = key.toLowerCase();
                if (k.contains("relationship") && k.contains("type")) continue;
                if (k.contains("description")) continue;
                if (k.contains("policy") && (k.contains("ref") || k.contains("name"))) {
                    hasPolicy = true;
                }
                if (k.contains("glossary") && (k.contains("ref") || k.contains("name")) && !k.contains("relationship")) {
                    hasGlossary = true;
                }
                boolean both = hasPolicy && hasGlossary;
                boolean eitherForDelete = "DELETE".equals(uploadOption) && config.isRequiresRelationType() && (hasPolicy || hasGlossary);
                if (both || eitherForDelete) return true;
            }
            return false;
        }
        return false;
    }
    
    /**
     * Pre-validate row data before database lookup
     * Checks for null/empty required fields, data types, etc.
     * 
     * @param rowData Row data containing original Excel values
     * @param config Relationship configuration
     * @param columnMappings Optional column mappings from frontend (Excel column name -> Field name)
     */
    public static void preValidateRow(RelationshipRowData rowData, RelationshipConfig config, Map<String, String> columnMappings) {
        // This will be implemented based on config
        // For now, basic validation
        
        if (rowData.getOriginalValues().isEmpty()) {
            rowData.addError("Row has no data");
            return;
        }
        
        // Check if at least one entity identifier is present
        // Use Excel-friendly column names instead of database column names
        boolean hasEntityAData = hasEntityValue(rowData, config.getEntityA(), columnMappings, config.getEntityARole());
        boolean hasEntityBData = hasEntityValue(rowData, config.getEntityB(), columnMappings, config.getEntityBRole());
        
        if (!hasEntityAData && config.getEntityA().isRequired()) {
            String entityAName = config.getEntityA().getName();
            String nameColumn = findExcelColumnName(rowData, config.getEntityA(), "name", columnMappings, config.getEntityARole());
            String refColumn = config.getEntityA().hasRefColumn() ? 
                findExcelColumnName(rowData, config.getEntityA(), "ref", columnMappings, config.getEntityARole()) : null;
            String availableColumns = String.join(", ", rowData.getOriginalValues().keySet());
            String expectedColumnsDesc = buildExpectedColumnNamesForEntity(config.getEntityA(), config.getEntityARole());
            String errorMsg = String.format(
                "%s is required but not provided. Expected column names: %s. Available columns: %s",
                entityAName, expectedColumnsDesc, availableColumns);
            if (nameColumn != null || refColumn != null) {
                String foundCol = nameColumn != null ? nameColumn : refColumn;
                errorMsg = String.format(
                    "The column '%s' is required but the value is empty. Please ensure the column contains a value.",
                    foundCol);
            }
            logger.debug("Row {}: {} validation failed. Name column: {}, Ref column: {}, Available: {}", 
                rowData.getRowNumber(), entityAName, nameColumn, refColumn, availableColumns);
            rowData.addError(errorMsg);
        }
        
        if (!hasEntityBData && config.getEntityB().isRequired()) {
            String entityBName = config.getEntityB().getName();
            // Format entity name for display (capitalize words, handle camelCase)
            String displayName = formatEntityNameForDisplay(entityBName);
            String nameColumn = findExcelColumnName(rowData, config.getEntityB(), "name", columnMappings, config.getEntityBRole());
            String refColumn = config.getEntityB().hasRefColumn() ? 
                findExcelColumnName(rowData, config.getEntityB(), "ref", columnMappings, config.getEntityBRole()) : null;
            String availableColumns = String.join(", ", rowData.getOriginalValues().keySet());
            String expectedColumnsDesc = buildExpectedColumnNamesForEntity(config.getEntityB(), config.getEntityBRole());
            
            // Generate more helpful error message
            String errorMsg;
            if (nameColumn != null || refColumn != null) {
                // Column found but value is empty
                String foundColumn = nameColumn != null ? nameColumn : refColumn;
                errorMsg = String.format(
                    "The column '%s' is required but the value is empty. Please ensure the column contains a value.",
                    foundColumn);
                logger.warn("Row {}: {} column '{}' found but value is empty. Available columns: {}", 
                    rowData.getRowNumber(), displayName, foundColumn, availableColumns);
            } else {
                // Column not found - provide helpful suggestions
                errorMsg = String.format(
                    "%s is required but not provided. Expected column names: %s. Available columns: [%s]",
                    displayName, expectedColumnsDesc, availableColumns);
                logger.warn("Row {}: {} validation failed - column not found. Expected: {}. Available: {}", 
                    rowData.getRowNumber(), displayName, expectedColumnsDesc, availableColumns);
            }
            rowData.addError(errorMsg);
        }
        
        // Regulation X Regulator: require at least one of Regulation Ref. or Regulation Name
        if ("regulationxregulator".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing regulation info. Provide Regulation Ref. or Regulation Name.");
            }
        }
        
        // Regulation X Project: require both Regulation and Project; clear message when both missing
        if ("regulationxproject".equals(config.getKey())) {
            if (!hasEntityAData && !hasEntityBData) {
                rowData.addError("Missing regulation and/or project info. Provide Regulation Ref./Name and Project Ref./Name.");
            }
        }
        
        // Regulation X Policy: require both Regulation and Policy
        if ("regulationxpolicy".equals(config.getKey())) {
            if (!hasEntityAData && !hasEntityBData) {
                rowData.addError("Missing regulation and/or policy info. Provide Regulation Ref./Name and Policy Ref./Name.");
            } else if (!hasEntityAData) {
                rowData.addError("Missing regulation info. Provide Regulation Ref. or Regulation Name.");
            } else if (!hasEntityBData) {
                rowData.addError("Missing policy info. Provide Policy Ref. or Policy Name.");
            }
        }
        
        // Regulation X Product: require both Regulation and Product
        if ("regulationxproduct".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing regulation info. Provide Regulation Ref. or Regulation Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing product info. Provide Product Name (or Product Parent Name).");
            }
        }
        
        // Project X Data Set: require both project and dataset info for bulk upload
        if ("projectxdataset".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing project info. Please provide Project Ref. or Project Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing dataset info. Please provide Data Set Ref., Data Set Name, or Data Set System Short Name.");
            }
        }
        
        // Project X Business Area: require both project and business area info for bulk upload
        if ("projectxbusinessarea".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing project info. Please provide Project Ref. or Project Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing business area info. Please provide Business Area Name.");
            }
        }
        
        // Project X Capability: require both project and capability info for bulk upload
        if ("projectxcapability".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing project info. Please provide Project Ref. or Project Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing capability info. Please provide Capability Ref. or Capability Name.");
            }
        }
        
        // Project X Client: require both client (entity A) and project (entity B) info for bulk upload
        if ("projectxclient".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing client info. Please provide Client Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing project info. Please provide Project Ref. or Project Name.");
            }
        }
        
        // Process X Data Set: require both process and dataset info for bulk upload
        if ("processxdataset".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing process info. Please provide Process Ref., Process Name, or Parent Process Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing dataset info. Please provide Data Set Ref., Data Set Name, or Data Set System Short Name.");
            }
        }
        
        // Process X Glossary: require both glossary and process info for bulk upload
        if ("processxglossary".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing glossary info. Please provide Glossary Ref., Glossary Name, or Parent Glossary Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing process info. Please provide Process Ref., Process Name, or Parent Process Name.");
            }
        }
        
        // Process X Product: require both product and process info for bulk upload
        if ("processxproduct".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing product info. Please provide Product Name (or Product Ref. if applicable).");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing process info. Please provide Process Ref., Process Name, or Parent Process Name.");
            }
        }
        
        // Process X Legal Entity: require process info for bulk upload (so rows are not inserted with process = N/A)
        if ("processxlegalentity".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing process info. Please provide Process Ref., Process Name, or Parent Process Name.");
            }
        }
        
        // Process X System: require process info for bulk upload (so rows are not inserted with process = N/A)
        if ("processxsystem".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing process info. Please provide Process Ref., Process Name, or Parent Process Name.");
            }
        }
        
        // Policy X System: require policy info for bulk upload (so rows are not inserted with policy = N/A)
        if ("policyxsystem".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing policy info. Please provide Policy Ref. or Policy Name.");
            }
        }
        
        // Process X Client: require both client and process info for bulk upload (so rows are not inserted with process = N/A)
        if ("processxclient".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing client info. Please provide Client Name (or Client Parent Name).");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing process info. Please provide Process Ref. or Process Name.");
            }
        }
        
        // Policy X Product: require policy info for bulk upload (so rows are not inserted with policy = N/A)
        if ("policyxproduct".equalsIgnoreCase(config.getKey())) {
            if (!hasEntityBData) {
                rowData.addError("Missing policy info. Please provide Policy Ref. or Policy Name.");
            }
        }
        
        // Policy X Legal Entity: require policy info for bulk upload (so rows are not inserted with policy = N/A)
        if ("policyxlegalentity".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing policy info. Please provide Policy Ref. or Policy Name.");
            }
        }
        
        // Policy X Glossary: require both policy and glossary info for bulk upload (so rows are not inserted with N/A)
        if ("policyxglossary".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing policy info. Please provide Policy Ref. or Policy Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing glossary info. Please provide Glossary Ref., Glossary Name, or Parent Glossary Name.");
            }
        }
        
        // Glossary X Product: require both product and glossary info for bulk upload (so rows are not inserted with N/A)
        if ("glossaryxproduct".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing product info. Please provide Product Name (or Product Parent Name).");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing glossary info. Please provide Glossary Ref., Glossary Name, or Parent Glossary Name.");
            }
        }
        
        // Policy X Data Set: require both policy and dataset info for bulk upload (so rows are not inserted with N/A)
        if ("policyxdataset".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing policy info. Please provide Policy Ref. or Policy Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing dataset info. Please provide Data Set Ref., Data Set Name, or Data Set System Short Name.");
            }
        }
        
        // Glossary X System: require at least one of Glossary Ref or Glossary Name (Entity A)
        if ("glossaryxsystem".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Glossary X System requires Glossary. Provide Glossary Ref. or Glossary Name.");
            }
        }
        
        // Policy X Attribute: require both policy and attribute info for bulk upload (so rows are not inserted with N/A)
        if ("policyxattribute".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing policy info. Please provide Policy Ref. or Policy Name.");
            }
            if (!hasEntityBData) {
                rowData.addError("Missing attribute info. Please provide Attribute Ref., Attribute Name, and optionally Attribute Data Set Name / Attribute System Short Name.");
            }
        }
        
        // Policy X Business Area: require policy info for bulk upload (so rows are not inserted with policy = N/A)
        if ("policyxbusinessarea".equals(config.getKey())) {
            if (!hasEntityAData) {
                rowData.addError("Missing policy info. Please provide Policy Ref. or Policy Name.");
            }
        }
        
        // Committee X Capability: require Committee Ref. or Committee Name AND Capability Ref. or Capability Name (no parent-only)
        if ("committeexcapability".equals(config.getKey())) {
            if (!hasEntityAData || !hasEntityBData) {
                if (!hasEntityAData && !hasEntityBData) {
                    rowData.addError("Committee X Capability requires Committee Ref. or Committee Name and Capability Ref. or Capability Name. " +
                        "Neither committee nor capability may be identified by parent only.");
                } else if (!hasEntityAData) {
                    rowData.addError("Committee X Capability requires Committee Ref. or Committee Name. " +
                        "Committee may not be identified by parent name only.");
                } else {
                    rowData.addError("Committee X Capability requires Capability Ref. or Capability Name. " +
                        "Capability may not be identified by parent name only.");
                }
            }
        }
        
        // Capability X System: require both Capability (Ref or Name, not parent-only) and System
        if ("capabilityxsystem".equals(config.getKey())) {
            if (!hasEntityAData || !hasEntityBData) {
                if (!hasEntityAData && !hasEntityBData) {
                    rowData.addError("Capability X System requires Capability Ref. or Capability Name and System Short Name. " +
                        "Capability may not be identified by parent name only.");
                } else if (!hasEntityAData) {
                    rowData.addError("Capability X System requires Capability Ref. or Capability Name. " +
                        "Capability may not be identified by parent name only.");
                } else {
                    rowData.addError("Capability X System requires System. Provide System Short Name (and optionally Parent System Short Name).");
                }
            }
        }
        
        // Capability X Process: require both Capability (Ref or Name, not parent-only) and Process (Ref or Name, not parent-only)
        if ("capabilityxprocess".equals(config.getKey())) {
            if (!hasEntityAData || !hasEntityBData) {
                if (!hasEntityAData && !hasEntityBData) {
                    rowData.addError("Capability X Process requires Capability Ref. or Capability Name and Process Ref. or Process Name. " +
                        "Neither capability nor process may be identified by parent name only.");
                } else if (!hasEntityAData) {
                    rowData.addError("Capability X Process requires Capability Ref. or Capability Name. " +
                        "Capability may not be identified by parent name only.");
                } else {
                    rowData.addError("Capability X Process requires Process Ref. or Process Name. " +
                        "Process may not be identified by parent name only.");
                }
            }
        }
        
        // Capability X Legal Entity: require both Capability (Ref or Name, not parent-only) and Legal Entity Short Name
        if ("capabilityxlegalentity".equals(config.getKey())) {
            if (!hasEntityAData || !hasEntityBData) {
                if (!hasEntityAData && !hasEntityBData) {
                    rowData.addError("Capability X Legal Entity requires Capability Ref. or Capability Name and Legal Entity Short Name. " +
                        "Capability may not be identified by parent name only.");
                } else if (!hasEntityAData) {
                    rowData.addError("Capability X Legal Entity requires Capability Ref. or Capability Name. " +
                        "Capability may not be identified by parent name only.");
                } else {
                    rowData.addError("Capability X Legal Entity requires Legal Entity Short Name.");
                }
            }
        }
        
        // Capability X Client: require both Capability (Ref or Name, not parent-only) and Client
        if ("capabilityxclient".equals(config.getKey())) {
            if (!hasEntityAData || !hasEntityBData) {
                if (!hasEntityAData && !hasEntityBData) {
                    rowData.addError("Capability X Client requires Capability Ref. or Capability Name and Client. " +
                        "Capability may not be identified by parent name only.");
                } else if (!hasEntityAData) {
                    rowData.addError("Capability X Client requires Capability Ref. or Capability Name. " +
                        "Capability may not be identified by parent name only.");
                } else {
                    rowData.addError("Capability X Client requires Client. Provide Client Name (and optionally Client Parent Name).");
                }
            }
        }
        
        // Data Set X Client: require both Client and Data Set (Ref or Name)
        if ("datasetxclient".equals(config.getKey())) {
            if (!hasEntityAData || !hasEntityBData) {
                if (!hasEntityAData && !hasEntityBData) {
                    rowData.addError("Data Set X Client requires Client and Data Set. Provide Client Name (and optionally Client Parent Name), and Data Set Ref., Data Set Name, or Data Set System Short Name.");
                } else if (!hasEntityAData) {
                    rowData.addError("Data Set X Client requires Client. Provide Client Name (and optionally Client Parent Name).");
                } else {
                    rowData.addError("Data Set X Client requires Data Set. Provide Data Set Ref., Data Set Name, or Data Set System Short Name.");
                }
            }
        }
        
        // Data Set X Product: require both Product and Data Set (Ref or Name)
        if ("datasetxproduct".equals(config.getKey())) {
            if (!hasEntityAData || !hasEntityBData) {
                if (!hasEntityAData && !hasEntityBData) {
                    rowData.addError("Data Set X Product requires Product and Data Set. Provide Product Name and Data Set Ref. or Data Set Name.");
                } else if (!hasEntityAData) {
                    rowData.addError("Data Set X Product requires Product. Provide Product Name (and optionally Product Parent Name).");
                } else {
                    rowData.addError("Data Set X Product requires Data Set. Provide Data Set Ref. or Data Set Name.");
                }
            }
        }
        
        // Capability X Product: require both Capability (Ref or Name, not parent-only) and Product
        if ("capabilityxproduct".equals(config.getKey())) {
            if (!hasEntityAData || !hasEntityBData) {
                if (!hasEntityAData && !hasEntityBData) {
                    rowData.addError("Capability X Product requires Capability Ref. or Capability Name and Product. " +
                        "Capability may not be identified by parent name only.");
                } else if (!hasEntityAData) {
                    rowData.addError("Capability X Product requires Capability Ref. or Capability Name. " +
                        "Capability may not be identified by parent name only.");
                } else {
                    rowData.addError("Capability X Product requires Product. Provide Product Name (and optionally Product Parent Name).");
                }
            }
        }
        
        // Product X Legal: when both product and legal are missing, add explicit error
        if ("product_x_legal".equals(config.getTableName()) && !hasEntityAData && !hasEntityBData) {
            rowData.addError("Missing product info and Legal is required. Provide at least Legal Short Name; if linking to a product, provide Product Name or Product Ref.");
        }
        
        // Check for required relationship type
        if (config.hasRelationType() && config.isRequiresRelationType()) {
            String relationshipTypeValue = findRelationshipTypeValue(rowData, columnMappings);
            if (isBlankOrWhitespace(relationshipTypeValue)) {
                String availableColumns = String.join(", ", rowData.getOriginalValues().keySet());
                String errorMsg = String.format(
                    "Relationship Type is required but not provided. Expected column name: 'Relationship Type'. Available columns: [%s]",
                    availableColumns);
                logger.warn("Row {}: Relationship Type is required but not found or empty. Available columns: {}", 
                    rowData.getRowNumber(), availableColumns);
                rowData.addError(errorMsg);
            }
        }
    }
    
    /**
     * Find relationship type value from row data.
     * Looks for common column name patterns like "Relationship Type" (works for both Project and Product templates).
     * Also checks mapped field names when column mappings are provided.
     *
     * @param rowData Row data containing original Excel values
     * @param columnMappings Optional column mappings from frontend (Excel column name -> Field name)
     * @return Relationship type value or null if not found
     */
    public static String findRelationshipTypeValue(RelationshipRowData rowData, Map<String, String> columnMappings) {
        Map<String, String> values = rowData.getOriginalValues();
        if (values == null || values.isEmpty()) {
            return null;
        }
        
        // Step 0: Check mapped field names first (if column mappings provided)
        if (columnMappings != null && !columnMappings.isEmpty()) {
            String[] mappedFieldNames = {
                "relationshipType",
                "relationType",
                "relationshipTypeName",
                "relationTypeName",
                "RelationshipType",
                "RelationType",
                "sourcingType",
                "SourcingType"
            };
            
            // Check if any mapped field name matches
            for (Map.Entry<String, String> mapping : columnMappings.entrySet()) {
                String mappedFieldName = mapping.getValue();
                String excelColumnName = mapping.getKey();
                
                // Check if mapped field name matches expected patterns
                for (String expectedName : mappedFieldNames) {
                    if (mappedFieldName != null && mappedFieldName.equalsIgnoreCase(expectedName)) {
                        // Check if this Excel column exists in the values (treat whitespace-only as missing)
                        String value = values.get(excelColumnName);
                        if (!isBlankOrWhitespace(value)) {
                            logger.debug("Row {}: Found relationship type '{}' using mapped field name '{}' in column '{}'", 
                                rowData.getRowNumber(), value.trim(), mappedFieldName, excelColumnName);
                            return value.trim();
                        }
                    }
                }
            }
            
            // Also check if the mapped field name itself exists as a key in values
            for (String mappedName : mappedFieldNames) {
                String value = values.get(mappedName);
                if (!isBlankOrWhitespace(value)) {
                    logger.debug("Row {}: Found relationship type '{}' using direct mapped field name '{}'", 
                        rowData.getRowNumber(), value.trim(), mappedName);
                    return value.trim();
                }
            }
        }
        
        // Try common column name patterns (include template header with dropdown hint for Glossary X Client, Committee X Capability, and others).
        // Attribute_X_Attribute template uses "Sourcing Type" instead of "Relationship Type".
        String[] possibleColumnNames = {
            "Relationship Type",
            "relationship type",
            "RelationshipType",
            "relationshipType",
            "Relation Type",
            "relation type",
            "RelationType",
            "relationType",
            "Sourcing Type",
            "sourcing type",
            "Relationship Type (required, dropdown from client_x_glossary_relationtype)",
            "Relationship Type (required, dropdown from committee_x_capability_relationtype)",
            "Relationship Type (required, dropdown from businessarea_x_glossary_relationtype)",
            "Relationship Type (required, dropdown from product_x_glossary_relationtype)",
            "Relationship Type (required, dropdown from glossary_x_system_relationtype)"
        };
        
        for (String columnName : possibleColumnNames) {
            String value = values.get(columnName);
            if (!isBlankOrWhitespace(value)) {
                return value.trim();
            }
        }
        
        // Try case-insensitive search with normalized keys (trim, collapse spaces) so Excel header variants match.
        // Match headers that start with, contain, or equal "relationship type" / "relation type"
        // so any header that clearly indicates relationship type is recognized (e.g. dropdown hints, typos with extra words).
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String normalizedKey = normalizeHeaderKey(key);
            boolean isRelationshipTypeColumn = normalizedKey.equals("relationship type")
                || normalizedKey.equals("relation type")
                || normalizedKey.equals("relationshiptype")
                || normalizedKey.equals("relationtype")
                || normalizedKey.equals("sourcing type")  // Attribute_X_Attribute template uses "Sourcing Type"
                || normalizedKey.startsWith("relationship type ")
                || normalizedKey.startsWith("relationship type")  // e.g. "Relationship Type (required, dropdown from ...)"
                || normalizedKey.startsWith("relation type ")
                || normalizedKey.startsWith("relation type")
                || normalizedKey.startsWith("sourcing type")
                || normalizedKey.contains("relationship type")
                || normalizedKey.contains("relation type");
            if (isRelationshipTypeColumn) {
                String value = entry.getValue();
                if (!isBlankOrWhitespace(value)) {
                    return value.trim();
                }
                // Column found but value is empty or whitespace-only - treat as missing (return null so preValidateRow adds error)
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Normalize header key for matching: trim, collapse multiple spaces to single space, lowercase.
     * Handles Excel header variants like "Relationship  Type" (double space) or trailing spaces.
     */
    private static String normalizeHeaderKey(String key) {
        if (key == null) return "";
        String t = key.trim();
        if (t.isEmpty()) return "";
        // Replace multiple spaces with single space
        String collapsed = t.replaceAll("\\s+", " ");
        return collapsed.toLowerCase();
    }
    
    /**
     * Returns true if the value is null, empty, or contains only whitespace (including Unicode spaces).
     * Used so that " " or non-breaking space do not count as provided for required fields.
     */
    private static boolean isBlankOrWhitespace(String value) {
        if (value == null) return true;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) return false;
        }
        return true;
    }
    
    /**
     * Public API: check if row has any identifier value for the given entity (name or ref).
     * Used by callers (e.g. BulkRelationshipsUploadServlet) to decide if product was "provided" for Product_X_Legal.
     *
     * @param rowData Row data containing original Excel values
     * @param entityConfig Entity configuration
     * @param columnMappings Optional column mappings from frontend (Excel column name -> Field name)
     * @param entityRole Optional role (e.g. "source", "target") for same-entity relationships
     * @return true if the row has a non-blank value for the entity's name or ref column
     */
    public static boolean hasEntityData(RelationshipRowData rowData, com.example.budg_v2.bulk.relationships.config.EntityConfig entityConfig, Map<String, String> columnMappings, String entityRole) {
        return hasEntityValue(rowData, entityConfig, columnMappings, entityRole);
    }
    
    /**
     * Check if row has value for entity (using Excel-friendly column names)
     * Uses the same logic as findExcelColumnName to ensure consistency
     * 
     * @param rowData Row data containing original Excel values
     * @param entityConfig Entity configuration
     * @param columnMappings Optional column mappings from frontend (Excel column name -> Field name)
     * @param entityRole Optional role (e.g. "source", "target") for same-entity relationships
     */
    private static boolean hasEntityValue(RelationshipRowData rowData, com.example.budg_v2.bulk.relationships.config.EntityConfig entityConfig, Map<String, String> columnMappings, String entityRole) {
        Map<String, String> values = rowData.getOriginalValues();
        if (values == null || values.isEmpty()) {
            logger.debug("Row {}: No values found for entity {}", rowData.getRowNumber(), entityConfig.getName());
            return false;
        }
        
        // Use findExcelColumnName to find name column (same logic as entity resolution)
        String nameColumnName = findExcelColumnName(rowData, entityConfig, "name", columnMappings, entityRole);
        if (nameColumnName != null) {
            String value = values.get(nameColumnName);
            if (!isBlankOrWhitespace(value)) {
                logger.debug("Row {}: Found {} value in column '{}': '{}'", 
                    rowData.getRowNumber(), entityConfig.getName(), nameColumnName, value);
                return true;
            } else {
                logger.warn("Row {}: Found {} column '{}' but value is empty or whitespace-only. This will cause validation to fail.", 
                    rowData.getRowNumber(), entityConfig.getName(), nameColumnName);
            }
        } else {
            logger.warn("Row {}: Could not find {} name column. Available columns: [{}]. " +
                "This will cause a '{} is required but not provided' error.",
                rowData.getRowNumber(), entityConfig.getName(), String.join(", ", values.keySet()), 
                formatEntityNameForDisplay(entityConfig.getName()));
        }
        
        // Check for ref column if entity has ref column
        if (entityConfig.hasRefColumn()) {
            String refColumnName = findExcelColumnName(rowData, entityConfig, "ref", columnMappings, entityRole);
            if (refColumnName != null) {
                String value = values.get(refColumnName);
                if (!isBlankOrWhitespace(value)) {
                    logger.debug("Row {}: Found {} ref value in column '{}': '{}'", 
                        rowData.getRowNumber(), entityConfig.getName(), refColumnName, value);
                    return true;
                } else {
                    logger.debug("Row {}: Found {} ref column '{}' but value is empty or whitespace-only", 
                        rowData.getRowNumber(), entityConfig.getName(), refColumnName);
                }
            }
        }
        
        logger.debug("Row {}: No valid value found for entity {}", rowData.getRowNumber(), entityConfig.getName());
        return false;
    }
    
    /**
     * Check if row has value for any of the specified columns
     */
    @SuppressWarnings("unused")
    private static boolean hasAnyValue(RelationshipRowData rowData, String... columnNames) {
        for (String columnName : columnNames) {
            if (columnName != null) {
                String value = rowData.getOriginalValues().get(columnName);
                if (value != null && !value.trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Extract value for entity from row data
     * Maps Excel column names to entity fields
     */
    public static String extractEntityValue(RelationshipRowData rowData, String entityName, String fieldType) {
        Map<String, String> values = rowData.getOriginalValues();
        
        // Try to find value based on common patterns
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey().toLowerCase();
            
            if (key.contains(entityName.toLowerCase()) && key.contains(fieldType.toLowerCase())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Find Excel column name for entity field (no role filter).
     * Delegates to {@link #findExcelColumnName(RelationshipRowData, com.example.budg_v2.bulk.relationships.config.EntityConfig, String, Map, String)} with entityRole null.
     */
    public static String findExcelColumnName(RelationshipRowData rowData,
                                            com.example.budg_v2.bulk.relationships.config.EntityConfig entityConfig,
                                            String fieldType, Map<String, String> columnMappings) {
        return findExcelColumnName(rowData, entityConfig, fieldType, columnMappings, null);
    }
    
    /**
     * Find Excel column name for entity field
     * Maps entity type and field type to Excel column names (e.g., "Client Name", "System Short Name")
     * Also checks mapped field names when column mappings are provided.
     * When entityRole is non-null (e.g. "source", "target"), only columns whose name contains that role are considered.
     * 
     * @param rowData Row data containing original Excel values
     * @param entityConfig Entity configuration
     * @param fieldType Type of field: "name", "ref", or "parent"
     * @param columnMappings Optional column mappings from frontend (Excel column name -> Field name)
     * @param entityRole Optional role (e.g. "source", "target") for same-entity relationships; when set, only columns containing this role match
     * @return Excel column name or null if not found
     */
    public static String findExcelColumnName(RelationshipRowData rowData, 
                                            com.example.budg_v2.bulk.relationships.config.EntityConfig entityConfig,
                                            String fieldType, Map<String, String> columnMappings,
                                            String entityRole) {
        Map<String, String> values = rowData.getOriginalValues();
        String entityName = entityConfig.getName();
        String lowerEntity = entityName.toLowerCase();
        String lowerFieldType = fieldType.toLowerCase();
        String lowerRole = entityRole != null ? entityRole.toLowerCase() : null;
        
        // Normalize entity name by removing spaces for comparison
        String normalizedEntity = lowerEntity.replaceAll("\\s+", "");
        
        // Step 0: Check mapped field names first (if column mappings provided)
        if (columnMappings != null && !columnMappings.isEmpty()) {
            // Generate expected mapped field name patterns.
            // When entityRole is set (e.g. "source", "target"), include role so frontend "sourceGlossaryName" / "targetGlossaryName" match.
            String camelCaseEntity = toCamelCase(entityName);
            String camelCaseField = lowerFieldType.substring(0, 1).toUpperCase() + lowerFieldType.substring(1);
            String expectedMappedField = camelCaseEntity + camelCaseField;
            if (lowerRole != null && !lowerRole.isEmpty()) {
                String camelCaseRole = lowerRole.substring(0, 1).toUpperCase() + lowerRole.substring(1);
                expectedMappedField = camelCaseRole + camelCaseEntity + camelCaseField;
            }
            
            // Check if any mapped field name matches the expected pattern
            for (Map.Entry<String, String> mapping : columnMappings.entrySet()) {
                String mappedFieldName = mapping.getValue();
                String excelColumnName = mapping.getKey();
                
                // When entityRole is set, only consider columns that start with the role (e.g. "Process Ref." vs "Predecessor Process Ref.")
                if (lowerRole != null) {
                    String lowerExcel = excelColumnName.toLowerCase();
                    boolean startsWithRole = lowerExcel.startsWith(lowerRole + " ") || lowerExcel.startsWith(lowerRole + ".")
                        || (lowerExcel.startsWith("parent ") && lowerExcel.contains(lowerRole));
                    if (!startsWithRole) {
                        continue;
                    }
                }
                
                // Check if mapped field name matches expected pattern (case-insensitive)
                // Normalize mapped field name (e.g. "Product Name" -> "productName") so frontend display names match
                String normalizedMapped = mappedFieldName != null ? toCamelCase(mappedFieldName.trim()) : "";
                boolean matchesExpected = mappedFieldName != null && (mappedFieldName.equalsIgnoreCase(expectedMappedField) || normalizedMapped.equalsIgnoreCase(expectedMappedField));
                // Accept frontend display names for Legal and Data Set (e.g. "Legal Entity Short Name", "Data Set Name") so 1-Mapping works
                boolean matchesLegalOrDatasetAlias = mappedFieldName != null && normalizedMapped.length() > 0
                    && mappedFieldNameMatchesEntityField(normalizedMapped, normalizedEntity, lowerFieldType);
                if ((matchesExpected || matchesLegalOrDatasetAlias)) {
                    // For Legal entity "name", only accept Excel columns whose name contains "legal" (never use "Product Name" for Legal)
                    if ("legal".equals(normalizedEntity) && "name".equals(lowerFieldType)) {
                        if (!excelColumnName.toLowerCase().contains("legal")) {
                            continue;
                        }
                    }
                    // Check if this Excel column exists in the values
                    if (values.containsKey(excelColumnName)) {
                        logger.debug("Row {}: Found column '{}' using mapped field name '{}' for {} {}", 
                            rowData.getRowNumber(), excelColumnName, mappedFieldName, entityName, fieldType);
                        if ("product".equalsIgnoreCase(normalizedEntity) || "dataset".equalsIgnoreCase(normalizedEntity)) {
                            logger.debug("Row {}: Data Set X Product mapping - Excel column '{}' used for {} {}", 
                                rowData.getRowNumber(), excelColumnName, entityName, fieldType);
                        }
                        return excelColumnName;
                    }
                }
            }
            
            // Also check if the mapped field name itself exists as a key in values
            // (This happens when parseDataRow stores both mapped name and Excel name)
            String[] possibleMappedNames;
            if (lowerRole != null && !lowerRole.isEmpty()) {
                String camelCaseRole = lowerRole.substring(0, 1).toUpperCase() + lowerRole.substring(1);
                possibleMappedNames = new String[] {
                    expectedMappedField,
                    camelCaseRole + camelCaseEntity + lowerFieldType,
                    lowerRole + camelCaseEntity + camelCaseField,
                    lowerRole + camelCaseEntity + lowerFieldType
                };
            } else {
                List<String> names = new ArrayList<>(Arrays.asList(
                    expectedMappedField,
                    camelCaseEntity + lowerFieldType,
                    normalizedEntity + lowerFieldType,
                    toCamelCase(entityName) + camelCaseField
                ));
                // Frontend display names for Legal / Data Set so 1-Mapping works
                if ("legal".equals(normalizedEntity) && "name".equals(lowerFieldType)) {
                    names.add("Legal Entity Short Name");
                    names.add("Legal Short Name");
                }
                if ("dataset".equals(normalizedEntity) && "name".equals(lowerFieldType)) {
                    names.add("Data Set Name");
                }
                if ("dataset".equals(normalizedEntity) && "ref".equals(lowerFieldType)) {
                    names.add("Data Set Ref.");
                    names.add("Data Set Ref");
                }
                if ("geography".equals(normalizedEntity) && "name".equals(lowerFieldType)) {
                    names.add("Geography");
                    names.add("Geography Name");
                }
                possibleMappedNames = names.toArray(new String[0]);
            }
            
            for (String mappedName : possibleMappedNames) {
                // For Legal entity "name", only accept keys whose name contains "legal" (never use "Product Name" for Legal)
                if ("legal".equals(normalizedEntity) && "name".equals(lowerFieldType)) {
                    if (!mappedName.toLowerCase().contains("legal")) {
                        continue;
                    }
                }
                if (values.containsKey(mappedName) && (lowerRole == null || roleMatchesColumn(mappedName.toLowerCase(), lowerRole))) {
                    logger.debug("Row {}: Found column '{}' using direct mapped field name for {} {}", 
                        rowData.getRowNumber(), mappedName, entityName, fieldType);
                    return mappedName;
                }
            }
        }
        
        // First pass: Try to find matching Excel column name (standard patterns)
        String foundColumn = findColumnByPattern(values, entityName, normalizedEntity, lowerEntity, lowerFieldType, entityRole);
        if (foundColumn != null) {
            return foundColumn;
        }
        
        // Second pass: If not found, try to find by mapped field name patterns
        // Common mapped field name patterns: camelCase (e.g., "clientName", "dataSetName")
        foundColumn = findColumnByMappedFieldName(values, entityName, normalizedEntity, lowerEntity, lowerFieldType, entityRole);
        if (foundColumn != null) {
            logger.debug("Row {}: Found column '{}' using mapped field name pattern for {} {}", 
                rowData.getRowNumber(), foundColumn, entityName, fieldType);
            return foundColumn;
        }
        
        // Log available columns for debugging if column not found
        if (logger.isDebugEnabled()) {
            logger.debug("Row {}: Could not find {} column for {} {}. Available columns: {}", 
                rowData.getRowNumber(), fieldType, entityName, fieldType, 
                String.join(", ", values.keySet()));
        } else {
            // Use warn level for important column matching failures
            logger.warn("Row {}: Could not find {} column for {} '{}'. Available columns: [{}]", 
                rowData.getRowNumber(), fieldType, entityName, fieldType, 
                String.join(", ", values.keySet()));
        }
        
        return null;
    }
    
    /**
     * Returns true when a column name matches the entity role (e.g. "Process" vs "Predecessor").
     * Prefers columns that start with the role so "Process Name" matches Process and "Predecessor Process Name" matches Predecessor.
     */
    private static boolean roleMatchesColumn(String lowerColumnName, String lowerRole) {
        if (lowerRole == null) return true;
        return lowerColumnName.startsWith(lowerRole + " ") || lowerColumnName.startsWith(lowerRole + ".")
            || (lowerColumnName.startsWith("parent ") && lowerColumnName.contains(lowerRole))
            || lowerColumnName.startsWith(lowerRole); // camelCase keys e.g. processName
    }
    
    /**
     * Find column by standard Excel column name patterns.
     * When entityRole is non-null, only columns that start with the role are considered (e.g. "Process" vs "Predecessor").
     */
    private static String findColumnByPattern(Map<String, String> values, String entityName, 
                                              String normalizedEntity, String lowerEntity, String lowerFieldType,
                                              String entityRole) {
        String lowerRole = entityRole != null ? entityRole.toLowerCase() : null;
        
        // Try to find matching column name
        for (String columnName : values.keySet()) {
            String lowerColumn = columnName.toLowerCase();
            String normalizedColumn = lowerColumn.replaceAll("\\s+", "");
            
            if (lowerRole != null && !roleMatchesColumn(lowerColumn, lowerRole)) {
                continue;
            }
            
            // Match based on field type
            if ("name".equals(lowerFieldType)) {
                // Skip parent columns
                if (lowerColumn.contains("parent")) {
                    continue;
                }
                
                // Special cases: check businessarea first, then system, then legal
                if (normalizedEntity.equals("businessarea")) {
                    // For businessarea: look for "Business Area Name" or "Business Area" (handles space in column name)
                    // Check for both "business" and "area" to ensure it's a business area column
                    // Accept columns with or without "name" (e.g., "Business Area" or "Business Area Name")
                    // Also handle variations like "BusinessArea Name" (no space) or "BusinessAreaName"
                    // Handle case variations: "business area", "Business Area", "BUSINESS AREA", etc.
                    String normalizedColumnForBusinessArea = lowerColumn.replaceAll("[\\s_-]+", "");
                    boolean isBusinessArea = (lowerColumn.contains("business") && lowerColumn.contains("area")) ||
                                            normalizedColumnForBusinessArea.contains("businessarea");
                    
                    // Exclude columns that are clearly not the name column
                    boolean isExcluded = lowerColumn.contains("short") || 
                                         lowerColumn.contains("parent") ||
                                         lowerColumn.contains("ref") ||
                                         lowerColumn.contains("reference") ||
                                         lowerColumn.contains("id") ||
                                         lowerColumn.contains("description");
                    
                    if (isBusinessArea && !isExcluded) {
                        // Prefer columns with "name" but also accept without "name"
                        // This allows "Business Area" to match even without "Name"
                        logger.debug("Matched Business Area column: '{}' for entity '{}' (normalized: '{}')", 
                            columnName, entityName, normalizedColumnForBusinessArea);
                        return columnName;
                    }
                } else if (normalizedEntity.equals("system")) {
                    // For system: look for "System Short Name" (contains "system", "short", "name")
                    if (lowerColumn.contains("system") && lowerColumn.contains("short") && 
                        lowerColumn.contains("name")) {
                        return columnName;
                    }
                } else if (normalizedEntity.equals("legal")) {
                    // For legal: accept multiple patterns:
                    // - "Legal Entity Short Name" (full form)
                    // - "Legal Short Name" (short form)
                    // - "Legal Entity Name" (without short, if exists)
                    if (lowerColumn.contains("legal") && lowerColumn.contains("name")) {
                        // Must contain "short" for legal entity (it uses ShortName in DB)
                        if (lowerColumn.contains("short")) {
                            return columnName;
                        }
                    }
                } else if (normalizedEntity.equals("product")) {
                    // Special case for "Product" entity: look for "Product Name"
                    // Handles both "Product Name" and "product name" variations
                    // Typo-tolerant: accept common typo "Proudct" -> treat as "product"
                    String normalizedForProduct = lowerColumn.replace("proudct", "product");
                    if (normalizedForProduct.contains("product") && lowerColumn.contains("name")) {
                        // Exclude parent and short name columns
                        if (!lowerColumn.contains("parent") && !lowerColumn.contains("short")) {
                            return columnName;
                        }
                    }
                } else if (normalizedEntity.equals("dataset")) {
                    // Special case for "Data Set" entity: look for "Data Set Name" or "Dataset Name"
                    // Handles both "Data Set" (with space) and "Dataset" (no space) in column names
                    // Excel columns typically use "Data Set" (two words with space)
                    boolean isDatasetName = (lowerColumn.contains("data") && lowerColumn.contains("set") && lowerColumn.contains("name")) ||
                                            (lowerColumn.contains("dataset") && lowerColumn.contains("name"));
                    if (isDatasetName) {
                        // Exclude parent and short name columns
                        if (!lowerColumn.contains("parent") && !lowerColumn.contains("short")) {
                            return columnName;
                        }
                    }
                } else if (normalizedEntity.equals("project")) {
                    // Special case for "Project" entity: look for "Project Name" (handles "Project Ref." in ref branch)
                    if (lowerColumn.contains("project") && lowerColumn.contains("name")) {
                        if (!lowerColumn.contains("parent") && !lowerColumn.contains("short")) {
                            return columnName;
                        }
                    }
                } else if (normalizedEntity.equals("process")) {
                    // Process: prefer "Process Name" only; exclude "Parent Process Name"
                    if (lowerColumn.contains("process") && lowerColumn.contains("name")) {
                        if (!lowerColumn.contains("parent") && !lowerColumn.contains("short")) {
                            return columnName;
                        }
                    }
                } else if (normalizedEntity.equals("glossary")) {
                    // Glossary: look for "Glossary Name"; exclude "Parent Glossary Name"
                    // Robust match: normalized key so "Glossary Name", "Glossary  Name" match
                    String normalizedKey = normalizeHeaderKey(columnName);
                    if (normalizedKey.equals("glossary name") || normalizedKey.replace(".", "").equals("glossary name")) {
                        return columnName;
                    }
                    if (lowerColumn.contains("glossary") && lowerColumn.contains("name")) {
                        if (!lowerColumn.contains("parent") && !lowerColumn.contains("short")) {
                            return columnName;
                        }
                    }
                } else if (normalizedEntity.equals("attribute")) {
                    // Attribute: prefer "Attribute Name" only; exclude "Attribute Data Set Name", "Attribute System Short Name"
                    if (lowerColumn.contains("attribute") && lowerColumn.contains("name")) {
                        if (!lowerColumn.contains("data set") && !lowerColumn.contains("dataset")
                                && !lowerColumn.contains("system") && !lowerColumn.contains("short")) {
                            return columnName;
                        }
                    }
                } else if (normalizedEntity.equals("people")) {
                    // People X People: identifier column in DB is Email, not Name. Template uses "Manager Email", "Employee Email".
                    if (lowerColumn.contains("email")) {
                        return columnName;
                    }
                } else if (normalizedEntity.equals("regulatorytheme")) {
                    // Regulatory Theme: "Regulatory Theme Name" (two words) - match "regulatory" + "theme" + "name"
                    boolean isRegulatoryThemeName = (lowerColumn.contains("regulatory") && lowerColumn.contains("theme") && lowerColumn.contains("name")) ||
                            (normalizedColumn.contains("regulatorytheme") && lowerColumn.contains("name"));
                    if (isRegulatoryThemeName && !lowerColumn.contains("parent") && !lowerColumn.contains("short")) {
                        return columnName;
                    }
                } else if (normalizedEntity.equals("regulation")) {
                    // Regulation: look for "Regulation Name"; exclude "Parent Regulation Name"
                    if (lowerColumn.contains("regulation") && lowerColumn.contains("name")) {
                        if (!lowerColumn.contains("parent") && !lowerColumn.contains("short")) {
                            return columnName;
                        }
                    }
                } else if (normalizedEntity.equals("client")) {
                    // Client: match "Client Name" but exclude "Client Parent Name" (template: Client Name, Client Parent Name)
                    if (lowerColumn.contains("client") && lowerColumn.contains("name") && !lowerColumn.contains("parent")) {
                        return columnName;
                    }
                } else if (normalizedEntity.equals("geography")) {
                    // Geography: accept "Geography" (template header) or "Geography Name"; exclude parent in name branch
                    if (lowerColumn.contains("parent")) continue;
                    if (lowerColumn.trim().equals("geography") || (lowerColumn.contains("geography") && lowerColumn.contains("name") && !lowerColumn.contains("short"))) {
                        return columnName;
                    }
                } else {
                    // For other entities: look for "{Entity} Name" (contains entity and "name", not "short")
                    // Try both normalized and original entity name matching
                    if ((normalizedColumn.contains(normalizedEntity) || lowerColumn.contains(lowerEntity)) && 
                        lowerColumn.contains("name") && !lowerColumn.contains("short")) {
                        return columnName;
                    }
                }
            } else if ("ref".equals(lowerFieldType)) {
                // For ref: look for "{Entity} Ref" or "{Entity} Reference"
                boolean matchesEntity = false;
                
                // Check businessarea first
                if (normalizedEntity.equals("businessarea")) {
                    matchesEntity = lowerColumn.contains("business") && lowerColumn.contains("area");
                } else if (normalizedEntity.equals("legal")) {
                    // Check for "legal entity" pattern
                    matchesEntity = lowerColumn.contains("legal") && lowerColumn.contains("entity");
                } else if (normalizedEntity.equals("product")) {
                    // Special case for "Product" entity: look for "Product Ref" or "Product Ref."
                    // Typo-tolerant: accept "Proudct"
                    String normalizedForProduct = lowerColumn.replace("proudct", "product");
                    matchesEntity = normalizedForProduct.contains("product");
                } else if (normalizedEntity.equals("dataset")) {
                    // Special case for "Data Set" entity: look for "Data Set Ref." or "Dataset Ref."
                    // Excel columns typically use "Data Set Ref." (two words with space)
                    matchesEntity = (lowerColumn.contains("data") && lowerColumn.contains("set")) ||
                                   lowerColumn.contains("dataset");
                } else if (normalizedEntity.equals("project")) {
                    // Special case for "Project" entity: "Project Ref." or "Project Ref" (trailing period optional)
                    matchesEntity = lowerColumn.contains("project");
                } else if (normalizedEntity.equals("process") || normalizedEntity.equals("attribute")) {
                    // Process/Attribute: match entity ref but exclude "Parent ... Ref" columns
                    matchesEntity = (normalizedColumn.contains(normalizedEntity) || lowerColumn.contains(lowerEntity))
                            && !lowerColumn.contains("parent");
                } else if (normalizedEntity.equals("capability")) {
                    // Capability: match "Capability Ref." but exclude "Parent ... Ref" columns
                    matchesEntity = (normalizedColumn.contains("capability") || lowerColumn.contains("capability"))
                            && !lowerColumn.contains("parent");
                } else if (normalizedEntity.equals("glossary")) {
                    // Glossary: look for "Glossary Ref." or "Glossary Ref" (template uses "Glossary Ref." with period)
                    // Robust match: normalized key so "Glossary Ref.", "Glossary  Ref" match
                    String nkRef = normalizeHeaderKey(columnName);
                    if (nkRef.equals("glossary ref") || nkRef.startsWith("glossary ref.")
                            || nkRef.replace(".", "").trim().equals("glossary ref")) {
                        if (lowerColumn.contains("ref") || lowerColumn.contains("reference")) {
                            return columnName;
                        }
                    }
                    // When entityRole is set (e.g. source/target for Glossary X Glossary), accept "Source Glossary Ref." / "Target Glossary Ref."
                    if (lowerRole != null && !lowerColumn.contains("parent")
                            && lowerColumn.contains("glossary") && (lowerColumn.contains("ref") || lowerColumn.contains("reference"))
                            && ((lowerColumn.contains("source") && "source".equals(lowerRole)) || (lowerColumn.contains("target") && "target".equals(lowerRole)))) {
                        return columnName;
                    }
                    matchesEntity = lowerColumn.contains("glossary") && !lowerColumn.contains("parent");
                } else if (normalizedEntity.equals("regulation")) {
                    // Regulation: look for "Regulation Ref." or "Regulation Ref"
                    matchesEntity = lowerColumn.contains("regulation") && !lowerColumn.contains("parent");
                } else if (normalizedEntity.equals("regulatorytheme")) {
                    // Regulatory Theme Ref.: "Regulatory Theme Ref." (two words)
                    matchesEntity = (lowerColumn.contains("regulatory") && lowerColumn.contains("theme")) ||
                            normalizedColumn.contains("regulatorytheme");
                } else if (normalizedEntity.equals("committee")) {
                    // Committee: match "Committee Ref." but exclude "Parent ... Ref" columns
                    matchesEntity = (normalizedColumn.contains("committee") || lowerColumn.contains("committee"))
                            && !lowerColumn.contains("parent");
                } else if (normalizedEntity.equals("policy")) {
                    // Policy: match "Policy Ref." but exclude "Policy Parent" / "Policy Parent Name" columns
                    matchesEntity = lowerColumn.contains("policy") && !lowerColumn.contains("parent");
                } else {
                    // General case
                    matchesEntity = normalizedColumn.contains(normalizedEntity) || lowerColumn.contains(lowerEntity);
                }
                
                if (matchesEntity && 
                    (lowerColumn.contains("ref") || lowerColumn.contains("reference"))) {
                    return columnName;
                }
            } else if ("parent".equals(lowerFieldType)) {
                // For parent: look for "{Entity} Parent Name" or "Parent {Entity} Name"
                // Must contain both "parent" and "name"
                if (!lowerColumn.contains("parent") || !lowerColumn.contains("name")) {
                    continue;
                }
                
                // Explicitly accept template header variants: "Parent Product Name", "Product Parent Name", "Parent Business Area Name", "Business Area Parent Name"
                String normalizedKey = normalizeHeaderKey(columnName);
                if (normalizedEntity.equals("product") && (normalizedKey.equals("parent product name") || normalizedKey.equals("product parent name"))) {
                    return columnName;
                }
                if (normalizedEntity.equals("businessarea") && (normalizedKey.equals("parent business area name") || normalizedKey.equals("business area parent name"))) {
                    return columnName;
                }
                
                boolean matchesEntity = false;
                
                // Check businessarea first
                if (normalizedEntity.equals("businessarea")) {
                    matchesEntity = lowerColumn.contains("business") && lowerColumn.contains("area");
                } else if (normalizedEntity.equals("legal")) {
                    // Check for "legal entity" pattern or just "legal"
                    matchesEntity = lowerColumn.contains("legal");
                } else if (normalizedEntity.equals("product")) {
                    // Accept both "Product Parent Name" and "Parent Product Name" (order-independent: must contain product, parent, name)
                    // Typo-tolerant: accept "Proudct"
                    String normalizedForProduct = lowerColumn.replace("proudct", "product");
                    matchesEntity = normalizedForProduct.contains("product");
                } else if (normalizedEntity.equals("client")) {
                    // Accept both "Parent Client Name" and "Client Parent Name" (order-independent: must contain client, parent, name)
                    matchesEntity = lowerColumn.contains("client");
                } else if (normalizedEntity.equals("dataset")) {
                    // Special case for "Data Set" entity: look for "Data Set Parent" or "Dataset Parent"
                    // Excel columns typically use "Data Set Parent" (two words with space)
                    matchesEntity = (lowerColumn.contains("data") && lowerColumn.contains("set")) ||
                                   lowerColumn.contains("dataset");
                } else if (normalizedEntity.equals("regulatorytheme")) {
                    // Parent Regulatory Theme Name: "Parent Regulatory Theme Name" (two words)
                    matchesEntity = (lowerColumn.contains("regulatory") && lowerColumn.contains("theme")) ||
                            normalizedColumn.contains("regulatorytheme");
                } else if (normalizedEntity.equals("regulation")) {
                    // Parent Regulation Name: "Parent Regulation Name"
                    matchesEntity = lowerColumn.contains("regulation");
                } else if (normalizedEntity.equals("geography")) {
                    // Parent Geography Name or Parent Geo Name
                    matchesEntity = (lowerColumn.contains("geography") || lowerColumn.contains("geo"));
                } else {
                    // General case
                    matchesEntity = normalizedColumn.contains(normalizedEntity) || lowerColumn.contains(lowerEntity);
                }
                
                if (matchesEntity) {
                    return columnName;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find column by mapped field name patterns (camelCase, etc.)
     * Used as fallback when standard Excel column name patterns don't match.
     * When entityRole is non-null, only columns whose name contains that role are considered.
     */
    private static String findColumnByMappedFieldName(Map<String, String> values, String entityName,
                                                     String normalizedEntity, String lowerEntity, String lowerFieldType,
                                                     String entityRole) {
        String lowerRole = entityRole != null ? entityRole.toLowerCase() : null;
        
        // Generate possible mapped field name patterns
        // Pattern 1: camelCase (e.g., "clientName", "dataSetName")
        String camelCaseEntity = toCamelCase(entityName);
        String camelCaseField = lowerFieldType.substring(0, 1).toUpperCase() + lowerFieldType.substring(1);
        String camelCasePattern = camelCaseEntity + camelCaseField;
        
        // Pattern 2: lowercase with underscore (e.g., "client_name", "data_set_name")
        String snakeCaseEntity = normalizedEntity.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        String snakeCasePattern = snakeCaseEntity + "_" + lowerFieldType;
        
        // Pattern 3: entity name + field type (e.g., "clientname", "datasetname")
        String simplePattern = normalizedEntity + lowerFieldType;
        
        // Check for exact matches first
        for (String columnName : values.keySet()) {
            String lowerColumn = columnName.toLowerCase().replaceAll("\\s+", "");
            String lowerColumnForRole = columnName.toLowerCase();
            
            if (lowerRole != null && !roleMatchesColumn(lowerColumnForRole, lowerRole)) {
                continue;
            }
            
            // Check camelCase pattern
            if (lowerColumn.equals(camelCasePattern.toLowerCase()) || 
                lowerColumn.equals(camelCaseEntity.toLowerCase() + lowerFieldType)) {
                // Verify it's the right field type
                if (("name".equals(lowerFieldType) && !lowerColumn.contains("parent") && !lowerColumn.contains("ref")) ||
                    ("ref".equals(lowerFieldType) && (lowerColumn.contains("ref") || lowerColumn.contains("reference"))) ||
                    ("parent".equals(lowerFieldType) && lowerColumn.contains("parent"))) {
                    return columnName;
                }
            }
            
            // Check snake_case pattern
            if (lowerColumn.equals(snakeCasePattern) || 
                lowerColumn.contains(snakeCaseEntity) && lowerColumn.contains(lowerFieldType)) {
                // Verify it's the right field type
                if (("name".equals(lowerFieldType) && !lowerColumn.contains("parent") && !lowerColumn.contains("ref")) ||
                    ("ref".equals(lowerFieldType) && (lowerColumn.contains("ref") || lowerColumn.contains("reference"))) ||
                    ("parent".equals(lowerFieldType) && lowerColumn.contains("parent"))) {
                    return columnName;
                }
            }
            
            // Check simple pattern
            if (lowerColumn.equals(simplePattern) || 
                (lowerColumn.contains(normalizedEntity) && lowerColumn.contains(lowerFieldType))) {
                // Verify it's the right field type
                if (("name".equals(lowerFieldType) && !lowerColumn.contains("parent") && !lowerColumn.contains("ref")) ||
                    ("ref".equals(lowerFieldType) && (lowerColumn.contains("ref") || lowerColumn.contains("reference"))) ||
                    ("parent".equals(lowerFieldType) && lowerColumn.contains("parent"))) {
                    return columnName;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Return true if the normalized mapped field name (camelCase from frontend, e.g. "legalEntityShortName")
     * is an accepted alias for the given entity and field type. Used so 1-Mapping works for Dataset X Legal
     * when frontend sends "Legal Entity Short Name", "Data Set Name", "Data Set Ref." etc.
     */
    private static boolean mappedFieldNameMatchesEntityField(String normalizedMapped, String normalizedEntity, String lowerFieldType) {
        if (normalizedMapped == null) return false;
        String n = normalizedMapped.toLowerCase().replaceAll("[\\s._-]+", "");
        if (n.isEmpty()) return false;
        if ("legal".equals(normalizedEntity) && "name".equals(lowerFieldType)) {
            return "legalentityshortname".equals(n) || "legalshortname".equals(n) || "legalname".equals(n);
        }
        if ("dataset".equals(normalizedEntity) && "name".equals(lowerFieldType)) {
            return "datasetname".equals(n);
        }
        if ("dataset".equals(normalizedEntity) && "ref".equals(lowerFieldType)) {
            return n.contains("dataset") && n.contains("ref");
        }
        return false;
    }

    /**
     * Convert entity name to camelCase
     * e.g., "Data Set" -> "dataSet", "Client" -> "client"
     */
    private static String toCamelCase(String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return "";
        }
        
        String[] words = entityName.split("[\\s_]+");
        if (words.length == 0) {
            return entityName.toLowerCase();
        }
        
        StringBuilder result = new StringBuilder(words[0].toLowerCase());
        for (int i = 1; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                result.append(words[i].substring(0, 1).toUpperCase());
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Format entity name for display in error messages
     * Converts "businessarea" -> "Business Area", "project" -> "Project", etc.
     */
    private static String formatEntityNameForDisplay(String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return entityName;
        }
        
        // Handle common entity name patterns
        if ("businessarea".equalsIgnoreCase(entityName)) {
            return "Business Area";
        } else if ("legalentity".equalsIgnoreCase(entityName) || "legal".equalsIgnoreCase(entityName)) {
            return "Legal Entity";
        } else if ("dataset".equalsIgnoreCase(entityName)) {
            return "Data Set";
        } else {
            // Capitalize first letter and add spaces before capital letters (for camelCase)
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (char c : entityName.toCharArray()) {
                if (Character.isUpperCase(c) && !first) {
                    result.append(' ');
                }
                if (first) {
                    result.append(Character.toUpperCase(c));
                    first = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
            return result.toString();
        }
    }
    
    /**
     * Build expected column names string for error messages.
     * For people entity with Email column and a role (e.g. Manager/Employee), returns "'Manager Email'" or "'Employee Email'".
     */
    private static String buildExpectedColumnNamesForEntity(com.example.budg_v2.bulk.relationships.config.EntityConfig entityConfig, String entityRole) {
        if ("people".equalsIgnoreCase(entityConfig.getName())
                && "Email".equalsIgnoreCase(entityConfig.getNameColumn())
                && entityRole != null && !entityRole.trim().isEmpty()) {
            return "'" + entityRole.trim() + " Email'";
        }
        return buildExpectedColumnNames(formatEntityNameForDisplay(entityConfig.getName()), entityConfig.hasRefColumn());
    }
    
    /**
     * Build expected column names string for error messages
     */
    private static String buildExpectedColumnNames(String displayName, boolean hasRefColumn) {
        List<String> expected = new ArrayList<>();
        expected.add("'" + displayName + " Name'");
        if (hasRefColumn) {
            expected.add("'" + displayName + " Ref'");
            expected.add("'" + displayName + " Reference'");
        }
        return String.join(" or ", expected);
    }
}

