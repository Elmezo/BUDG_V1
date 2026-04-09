package com.example.budg_v2.bulk.relationships.base;

import com.example.budg_v2.bulk.relationships.dto.RelationshipRowData;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Interface for handling relationship bulk uploads
 * Each relationship type should implement this interface
 */
public interface RelationshipUploadHandler {
    
    /**
     * Get the relationship type identifier
     * @return Relationship type (e.g., "Policy X System", "Process X Client")
     */
    String getRelationshipType();
    
    /**
     * Insert a new relationship
     * @param conn Database connection
     * @param rowData Row data from Excel
     * @param userId User performing the operation
     * @throws SQLException If database error occurs
     */
    void insert(Connection conn, JsonObject rowData, int userId) throws SQLException;
    
    /**
     * Delete an existing relationship
     * @param conn Database connection
     * @param rowData Row data from Excel
     * @param userId User performing the operation
     * @throws SQLException If database error occurs
     */
    void delete(Connection conn, JsonObject rowData, int userId) throws SQLException;
    
    /**
     * Validate a row before processing
     * @param rowData Row data from Excel
     * @param operation Operation type (INSERT or DELETE)
     * @return Validation result as JsonObject with "valid" boolean and optional "error" message
     */
    JsonObject validateRow(JsonObject rowData, String operation);
    
    /**
     * Get the table name for this relationship
     * @return Table name
     */
    String getTableName();
    
    /**
     * Comprehensive validation with entity resolution (NEW)
     * 
     * @param conn Database connection
     * @param rowData Structured row data with resolved IDs
     * @param rowNumber Row number for error reporting
     * @param cancelOnWarning Whether to treat warnings as blocking
     * @param entityCache Session entity cache
     * @param relationTypeCache Session relationship type cache
     * @return True if row is valid for processing, false otherwise
     */
    default boolean validateAndResolve(Connection conn, RelationshipRowData rowData,
                                      int rowNumber, boolean cancelOnWarning,
                                      Map<String, Integer> entityCache,
                                      Map<String, Integer> relationTypeCache) {
        // Default implementation - subclasses can override for custom validation
        return rowData.isValid() && !rowData.hasErrors() && 
               (!cancelOnWarning || !rowData.hasWarnings());
    }
}

