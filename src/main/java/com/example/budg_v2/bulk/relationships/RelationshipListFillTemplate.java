package com.example.budg_v2.bulk.relationships;

import com.example.budg_v2.database.DatabaseConnection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for populating list/dropdown columns in relationship templates
 * Creates hidden sheets with list values and applies data validation
 */
public class RelationshipListFillTemplate {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipListFillTemplate.class);
    private static int hiddenSheetCounter = 0;
    
    /**
     * Add list validation to a specific column in a sheet
     * @param workbook The workbook to add validation to
     * @param mainSheet The main sheet where validation will be applied
     * @param columnIndex The column index (0-based) to apply validation
     * @param tableName The database table name to fetch list values from
     * @param displayColumn The column name to display (usually 'PrimaryName' or 'Type')
     * @throws SQLException If database error occurs
     */
    public static void addListValidation(XSSFWorkbook workbook, XSSFSheet mainSheet, 
                                        int columnIndex, String tableName, 
                                        String displayColumn) throws SQLException {
        
        // Get list values from database
        List<String> listValues = getListValuesFromDatabase(tableName, displayColumn);
        
        if (listValues.isEmpty()) {
            logger.warn("No list values found for table {} column {}", tableName, displayColumn);
            return;
        }
        
        // Create unique hidden sheet name
        String hiddenSheetName = generateHiddenSheetName(tableName);
        
        // Create hidden sheet
        XSSFSheet hiddenSheet = workbook.createSheet(hiddenSheetName);
        workbook.setSheetHidden(workbook.getSheetIndex(hiddenSheet), true);
        
        // Populate hidden sheet with list values
        for (int i = 0; i < listValues.size(); i++) {
            Row row = hiddenSheet.createRow(i);
            Cell cell = row.createCell(0);
            cell.setCellValue(listValues.get(i));
        }
        
        // Create data validation formula
        String formula = hiddenSheetName + "!$A$1:$A$" + listValues.size();
        XSSFDataValidationHelper validationHelper = new XSSFDataValidationHelper(mainSheet);
        XSSFDataValidationConstraint constraint = (XSSFDataValidationConstraint) 
            validationHelper.createFormulaListConstraint(formula);
        
        // Apply validation to rows 2-100 (row 1 is header). Using 100 avoids extending sheet used range to row 1000.
        CellRangeAddressList addressList = new CellRangeAddressList(1, 100, columnIndex, columnIndex);
        XSSFDataValidation validation = (XSSFDataValidation) validationHelper.createValidation(constraint, addressList);
        
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("Invalid Input", "Please select a value from the dropdown list.");
        validation.setSuppressDropDownArrow(true);
        
        mainSheet.addValidationData(validation);
        
        logger.info("Added list validation for column {} from table {} with {} values", 
            columnIndex, tableName, listValues.size());
    }
    
    /**
     * Get list values from database table
     * @param tableName The table name to query
     * @param displayColumn The column name to fetch
     * @return List of string values
     * @throws SQLException If database error occurs
     */
    private static List<String> getListValuesFromDatabase(String tableName, String displayColumn) throws SQLException {
        List<String> values = new ArrayList<>();
        
        // Sanitize table and column names to prevent SQL injection
        String sanitizedTable = sanitizeSqlIdentifier(tableName);
        String sanitizedColumn = sanitizeSqlIdentifier(displayColumn);
        
        String sql = "SELECT DISTINCT " + sanitizedColumn + " FROM " + sanitizedTable + 
                     " WHERE " + sanitizedColumn + " IS NOT NULL";
        if ("segment".equalsIgnoreCase(tableName)) {
            sql += " AND Deleted_At IS NULL";
        }
        sql += " ORDER BY " + sanitizedColumn;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null && !value.trim().isEmpty()) {
                    values.add(value.trim());
                }
            }
        }
        
        logger.debug("Fetched {} values from {}.{}", values.size(), tableName, displayColumn);
        return values;
    }
    
    /**
     * Sanitize SQL identifier (table/column name) to prevent SQL injection
     * Allows only alphanumeric characters and underscores
     */
    private static String sanitizeSqlIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("SQL identifier cannot be null or empty");
        }
        
        // Remove any characters that are not alphanumeric or underscore
        String sanitized = identifier.replaceAll("[^a-zA-Z0-9_]", "");
        
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        
        return sanitized;
    }
    
    /**
     * Generate unique hidden sheet name
     * Sheet names are limited to 31 characters in Excel
     */
    private static String generateHiddenSheetName(String tableName) {
        hiddenSheetCounter++;
        
        // Clean table name and ensure sheet name is valid
        String cleanName = tableName.replaceAll("[^a-zA-Z0-9_]", "");
        
        // Excel sheet names max 31 chars
        String sheetName = "Hidden_" + cleanName;
        if (sheetName.length() > 28) {
            sheetName = "Hidden_" + cleanName.substring(0, 21);
        }
        
        // Add counter to ensure uniqueness
        sheetName += "_" + hiddenSheetCounter;
        
        return sheetName;
    }
    
    /**
     * Reset hidden sheet counter (useful for testing)
     */
    public static void resetCounter() {
        hiddenSheetCounter = 0;
    }
}

