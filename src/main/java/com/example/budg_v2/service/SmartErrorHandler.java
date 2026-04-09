package com.example.budg_v2.service;

import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent error detection and auto-fix capabilities
 * Handles common errors automatically and suggests fixes for others
 */
public class SmartErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartErrorHandler.class);
    
    // Patterns for common errors
    private static final Pattern FOREIGN_KEY_PATTERN = 
        Pattern.compile("Cannot add or update a child row.*FOREIGN KEY.*`(\\w+)`.*REFERENCES `(\\w+)`", Pattern.CASE_INSENSITIVE);
    private static final Pattern DUPLICATE_KEY_PATTERN = 
        Pattern.compile("Duplicate entry.*for key.*'?(\\w+)'?", Pattern.CASE_INSENSITIVE);
    @SuppressWarnings("unused") // reserved for future missing-column handling
    private static final Pattern MISSING_COLUMN_PATTERN = 
        Pattern.compile("Unknown column.*'([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVALID_DATA_TYPE_PATTERN = 
        Pattern.compile("Incorrect.*value.*for column.*'([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_NULL_PATTERN = 
        Pattern.compile("Field.*'([^']+)'.*doesn't have a default value", Pattern.CASE_INSENSITIVE);
    
    /**
     * Handle error and determine resolution
     * 
     * @param error Exception that occurred
     * @param rowData Row data that caused the error
     * @param entityType Entity type being processed
     * @return Error resolution result
     */
    public ErrorResolutionResult handleError(Exception error, JsonObject rowData, String entityType) {
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            errorMessage = error.getClass().getSimpleName();
        }
        
        logger.debug("Handling error for entity {}: {}", entityType, errorMessage);
        
        // Check if we can auto-fix
        if (canAutoFix(error)) {
            JsonObject fixedData = autoFix(rowData, error, entityType);
            if (fixedData != null) {
                return ErrorResolutionResult.builder()
                    .canAutoFix(true)
                    .shouldRetry(true)
                    .severity(ErrorResolutionResult.ErrorSeverity.WARNING)
                    .errorMessage(errorMessage)
                    .suggestedFix("Auto-fixed: " + getFixDescription(error))
                    .fixedData(fixedData)
                    .build();
            }
        }
        
        // Generate suggestions
        List<String> suggestions = suggestFixes(error, rowData, entityType);
        ErrorResolutionResult.ErrorSeverity severity = determineSeverity(error);
        
        return ErrorResolutionResult.builder()
            .canAutoFix(false)
            .shouldRetry(false)
            .severity(severity)
            .errorMessage(errorMessage)
            .suggestedFix(suggestions.isEmpty() ? null : suggestions.get(0))
            .fixSuggestions(suggestions)
            .build();
    }
    
    /**
     * Check if error can be auto-fixed
     */
    public boolean canAutoFix(Exception error) {
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return false;
        }
        
        // Check for auto-fixable errors
        if (FOREIGN_KEY_PATTERN.matcher(errorMessage).find()) {
            return true; // Can try to resolve foreign key
        }
        if (DUPLICATE_KEY_PATTERN.matcher(errorMessage).find()) {
            return true; // Can skip or update
        }
        if (INVALID_DATA_TYPE_PATTERN.matcher(errorMessage).find()) {
            return true; // Can try to convert
        }
        if (NOT_NULL_PATTERN.matcher(errorMessage).find()) {
            return true; // Can try to use default
        }
        
        return false;
    }
    
    /**
     * Auto-fix the error
     */
    public JsonObject autoFix(JsonObject rowData, Exception error, String entityType) {
        if (rowData == null) {
            return null;
        }
        
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return null;
        }
        
        // Create a copy to modify
        JsonObject fixedData = deepCopy(rowData);
        
        try {
            // Fix foreign key errors
            Matcher fkMatcher = FOREIGN_KEY_PATTERN.matcher(errorMessage);
            if (fkMatcher.find()) {
                String fkColumn = fkMatcher.group(1);
                String refTable = fkMatcher.group(2);
                return fixForeignKey(fixedData, fkColumn, refTable, entityType);
            }
            
            // Fix duplicate key errors
            Matcher dupMatcher = DUPLICATE_KEY_PATTERN.matcher(errorMessage);
            if (dupMatcher.find()) {
                // For duplicates, we can't auto-fix - need to skip or update
                // Return null to indicate cannot auto-fix
                return null;
            }
            
            // Fix invalid data type errors
            Matcher typeMatcher = INVALID_DATA_TYPE_PATTERN.matcher(errorMessage);
            if (typeMatcher.find()) {
                String column = typeMatcher.group(1);
                return fixDataType(fixedData, column, entityType);
            }
            
            // Fix NOT NULL errors
            Matcher nullMatcher = NOT_NULL_PATTERN.matcher(errorMessage);
            if (nullMatcher.find()) {
                String column = nullMatcher.group(1);
                return fixNotNullField(fixedData, column, entityType);
            }
            
        } catch (Exception e) {
            logger.warn("Error during auto-fix: {}", e.getMessage());
            return null;
        }
        
        return null;
    }
    
    /**
     * Fix foreign key error by resolving the reference
     */
    private JsonObject fixForeignKey(JsonObject rowData, String fkColumn, String refTable, String entityType) {
        // Try to find the foreign key value in row data
        String fkValueKey = findKeyInRowData(rowData, fkColumn);
        if (fkValueKey == null) {
            logger.debug("Cannot find foreign key column {} in row data", fkColumn);
            return null;
        }
        
        // If it's already an ID, return as-is (might be invalid ID)
        if (rowData.has(fkValueKey) && !rowData.get(fkValueKey).isJsonNull()) {
            try {
                int id = rowData.get(fkValueKey).getAsInt();
                if (id > 0) {
                    // Already has ID, might be invalid - try to resolve by name
                    return resolveForeignKeyByName(rowData, fkColumn, refTable, entityType);
                }
            } catch (Exception e) {
                // Not an integer, try to resolve
            }
        }
        
        // Try to resolve by name or reference
        return resolveForeignKeyByName(rowData, fkColumn, refTable, entityType);
    }
    
    /**
     * Resolve foreign key by name or reference
     */
    private JsonObject resolveForeignKeyByName(JsonObject rowData, String fkColumn, String refTable, String entityType) {
        // Try to find name column (e.g., System_ID -> System Name)
        String nameColumn = fkColumn.replace("_ID", "").replace("ID", "") + " Name";
        String nameKey = findKeyInRowData(rowData, nameColumn);
        
        if (nameKey != null && rowData.has(nameKey) && !rowData.get(nameKey).isJsonNull()) {
            String name = rowData.get(nameKey).getAsString();
            if (name != null && !name.trim().isEmpty()) {
                Integer resolvedId = lookupEntityIdByName(refTable, name);
                if (resolvedId != null) {
                    JsonObject fixed = deepCopy(rowData);
                    String fkKey = findKeyInRowData(rowData, fkColumn);
                    if (fkKey != null) {
                        fixed.addProperty(fkKey, resolvedId);
                        logger.info("Auto-fixed foreign key {}: resolved '{}' to ID {}", fkColumn, name, resolvedId);
                        return fixed;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Fix data type error by converting value
     */
    private JsonObject fixDataType(JsonObject rowData, String column, String entityType) {
        String columnKey = findKeyInRowData(rowData, column);
        if (columnKey == null || !rowData.has(columnKey)) {
            return null;
        }
        
        // Try to infer correct type and convert
        // This is a simplified version - in practice, we'd need to know the expected type
        JsonObject fixed = deepCopy(rowData);
        
        try {
            String value = rowData.get(columnKey).getAsString();
            // Try to convert to number if it looks like a number
            if (value != null && value.matches("^-?\\d+$")) {
                fixed.addProperty(columnKey, Integer.parseInt(value));
                logger.info("Auto-fixed data type for {}: converted '{}' to integer", column, value);
                return fixed;
            } else if (value != null && value.matches("^-?\\d+\\.\\d+$")) {
                fixed.addProperty(columnKey, Double.parseDouble(value));
                logger.info("Auto-fixed data type for {}: converted '{}' to double", column, value);
                return fixed;
            }
        } catch (Exception e) {
            logger.debug("Cannot auto-fix data type for {}: {}", column, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Fix NOT NULL field by using default value
     */
    private JsonObject fixNotNullField(JsonObject rowData, String column, String entityType) {
        String columnKey = findKeyInRowData(rowData, column);
        if (columnKey == null) {
            return null;
        }
        
        JsonObject fixed = deepCopy(rowData);
        
        // Try to get default value from database
        String defaultValue = getDefaultValue(column, entityType);
        if (defaultValue != null) {
            fixed.addProperty(columnKey, defaultValue);
            logger.info("Auto-fixed NOT NULL field {}: using default value '{}'", column, defaultValue);
            return fixed;
        }
        
        // Use common defaults based on column name
        String lowerColumn = column.toLowerCase();
        if (lowerColumn.contains("status") || lowerColumn.contains("state")) {
            fixed.addProperty(columnKey, "1"); // Default status
            return fixed;
        } else if (lowerColumn.contains("type")) {
            fixed.addProperty(columnKey, "1"); // Default type
            return fixed;
        } else if (lowerColumn.contains("name") || lowerColumn.contains("title")) {
            fixed.addProperty(columnKey, ""); // Empty string
            return fixed;
        }
        
        return null;
    }
    
    /**
     * Suggest fixes for the error
     */
    public List<String> suggestFixes(Exception error, JsonObject rowData, String entityType) {
        List<String> suggestions = new ArrayList<>();
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return suggestions;
        }
        
        Matcher fkMatcher = FOREIGN_KEY_PATTERN.matcher(errorMessage);
        if (fkMatcher.find()) {
            String fkColumn = fkMatcher.group(1);
            String refTable = fkMatcher.group(2);
            suggestions.add(String.format("Foreign key '%s' references non-existent record in '%s'. " +
                "Ensure the referenced record exists or provide a valid %s name.", 
                fkColumn, refTable, refTable));
        }
        
        Matcher dupMatcher = DUPLICATE_KEY_PATTERN.matcher(errorMessage);
        if (dupMatcher.find()) {
            String key = dupMatcher.group(1);
            suggestions.add(String.format("Duplicate entry for key '%s'. " +
                "The record already exists. Use UPDATE operation instead of INSERT, or provide a unique value.", key));
        }
        
        Matcher nullMatcher = NOT_NULL_PATTERN.matcher(errorMessage);
        if (nullMatcher.find()) {
            String column = nullMatcher.group(1);
            suggestions.add(String.format("Required field '%s' is missing. " +
                "Please provide a value for this field.", column));
        }
        
        Matcher typeMatcher = INVALID_DATA_TYPE_PATTERN.matcher(errorMessage);
        if (typeMatcher.find()) {
            String column = typeMatcher.group(1);
            suggestions.add(String.format("Invalid data type for column '%s'. " +
                "Please check the data format and try again.", column));
        }
        
        return suggestions;
    }
    
    /**
     * Determine error severity
     */
    private ErrorResolutionResult.ErrorSeverity determineSeverity(Exception error) {
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return ErrorResolutionResult.ErrorSeverity.CRITICAL;
        }
        
        // Foreign key errors are usually critical
        if (FOREIGN_KEY_PATTERN.matcher(errorMessage).find()) {
            return ErrorResolutionResult.ErrorSeverity.CRITICAL;
        }
        
        // Duplicate key errors are warnings (can skip)
        if (DUPLICATE_KEY_PATTERN.matcher(errorMessage).find()) {
            return ErrorResolutionResult.ErrorSeverity.WARNING;
        }
        
        // Data type errors are warnings (might be fixable)
        if (INVALID_DATA_TYPE_PATTERN.matcher(errorMessage).find()) {
            return ErrorResolutionResult.ErrorSeverity.WARNING;
        }
        
        // NOT NULL errors are critical
        if (NOT_NULL_PATTERN.matcher(errorMessage).find()) {
            return ErrorResolutionResult.ErrorSeverity.CRITICAL;
        }
        
        return ErrorResolutionResult.ErrorSeverity.CRITICAL;
    }
    
    /**
     * Get fix description
     */
    private String getFixDescription(Exception error) {
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return "Unknown error";
        }
        
        if (FOREIGN_KEY_PATTERN.matcher(errorMessage).find()) {
            return "Resolved foreign key reference";
        }
        if (DUPLICATE_KEY_PATTERN.matcher(errorMessage).find()) {
            return "Handled duplicate key";
        }
        if (INVALID_DATA_TYPE_PATTERN.matcher(errorMessage).find()) {
            return "Converted data type";
        }
        if (NOT_NULL_PATTERN.matcher(errorMessage).find()) {
            return "Applied default value";
        }
        
        return "Applied fix";
    }
    
    /**
     * Find key in row data (case-insensitive)
     */
    private String findKeyInRowData(JsonObject rowData, String columnName) {
        // Direct match
        if (rowData.has(columnName)) {
            return columnName;
        }
        
        // Case-insensitive match
        for (String key : rowData.keySet()) {
            if (key.equalsIgnoreCase(columnName)) {
                return key;
            }
        }
        
        // Try with variations
        String normalized = columnName.replace("_", " ").replace("-", " ");
        for (String key : rowData.keySet()) {
            if (key.replace("_", " ").replace("-", " ").equalsIgnoreCase(normalized)) {
                return key;
            }
        }
        
        return null;
    }
    
    /**
     * Lookup entity ID by name
     */
    private Integer lookupEntityIdByName(String tableName, String name) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Try common name columns
            String[] nameColumns = {"PrimaryName", "Name", "primaryname", "name"};
            String[] idColumns = {"ID", "id", "Id"};
            
            for (String nameCol : nameColumns) {
                for (String idCol : idColumns) {
                    try {
                        String sql = String.format("SELECT %s FROM %s WHERE %s = ? AND Deleted_datetime IS NULL LIMIT 1",
                            idCol, tableName, nameCol);
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, name);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    return rs.getInt(1);
                                }
                            }
                        }
                    } catch (SQLException e) {
                        // Try next combination
                        continue;
                    }
                }
            }
        } catch (SQLException e) {
            logger.debug("Error looking up entity ID: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get default value for column
     */
    private String getDefaultValue(String column, String entityType) {
        // Try to get from database schema
        try (Connection conn = DatabaseConnection.getConnection()) {
            String tableName = inferTableName(entityType);
            String dbName = conn.getCatalog();
            if (dbName == null) {
                dbName = conn.getSchema();
            }
            
            String sql = "SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbName);
                ps.setString(2, tableName);
                ps.setString(3, column);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String defaultValue = rs.getString("COLUMN_DEFAULT");
                        if (defaultValue != null && !defaultValue.equals("NULL")) {
                            return defaultValue.replace("'", "").replace("\"", "");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.debug("Error getting default value: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Infer table name from entity type
     */
    private String inferTableName(String entityType) {
        if (entityType == null) {
            return "unknown";
        }
        return entityType.toLowerCase().replace(" ", "_");
    }
    
    /**
     * Deep copy JsonObject
     */
    private JsonObject deepCopy(JsonObject original) {
        if (original == null) {
            return null;
        }
        // Simple copy - in practice might need more sophisticated copying
        return original.deepCopy();
    }
}

