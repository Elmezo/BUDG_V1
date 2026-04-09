package com.example.budg_v2.bulk.roles.base;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for handling role bulk uploads
 * Each entity role type should implement this interface
 */
public interface RoleUploadHandler {
    
    /**
     * Get the role type identifier
     * @return Role type (e.g., "Policy Role", "System Role")
     */
    String getRoleType();
    
    /**
     * Insert a new role assignment
     * @param conn Database connection
     * @param rowData Row data from Excel
     * @param userId User performing the operation
     * @throws SQLException If database error occurs
     */
    void insert(Connection conn, JsonObject rowData, int userId) throws SQLException;
    
    /**
     * Delete an existing role assignment
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
     * @param userId User performing the operation (for segment access validation)
     * @return Validation result as JsonObject with "valid" boolean and optional "error" message
     */
    JsonObject validateRow(JsonObject rowData, String operation, int userId);
    
    /**
     * Get the entity name for this role type
     * @return Entity name (e.g., "Policy", "System")
     */
    String getEntityName();
    
    /**
     * Get the linking table name for role assignments
     * @return Table name (e.g., "policy_x_people", "system_x_people")
     */
    String getLinkingTableName();
}

