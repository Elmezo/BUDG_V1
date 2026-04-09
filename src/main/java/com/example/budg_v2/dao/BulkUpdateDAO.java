package com.example.budg_v2.dao;

import com.example.budg_v2.config.BulkUpdateDefinitionConfig;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object for Bulk Update operations.
 * Handles database operations for bulk updates across facets.
 */
public class BulkUpdateDAO {

    /**
     * Check if an object has an active change request.
     * A change request is considered active if:
     * 1. active_cr_id is not null AND the CR status is NOT Completed, Cancelled, Rejected, or Closed
     * 2. OR there's a Change Request with matching Reference (e.g., "Dataset 57") that is active
     */
    public boolean hasActiveChangeRequest(Connection conn, String tableName, int objectId) throws SQLException {
        //system.out.println("[BulkUpdateDAO] hasActiveChangeRequest called: tableName=" + tableName + ", objectId=" + objectId);
        
        // First check: active_cr_id in the table
        String sql = "SELECT t.active_cr_id, crs.PrimaryName as status_name " +
                     "FROM `" + tableName + "` t " +
                     "LEFT JOIN changerequest cr ON t.active_cr_id = cr.ID " +
                     "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                     "WHERE t.id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Object activeCrId = rs.getObject("active_cr_id");
                    //system.out.println("[BulkUpdateDAO] First check - active_cr_id from table: " + activeCrId);
                    
                    if (activeCrId != null) {
                        // Check if CR status is completed, cancelled, rejected, or closed
                        String statusName = rs.getString("status_name");
                        //system.out.println("[BulkUpdateDAO] First check - CR status_name: " + statusName);
                        
                        if (statusName != null) {
                            String statusUpper = statusName.toUpperCase();
                            if (statusUpper.contains("COMPLETED") || 
                                statusUpper.contains("CANCELLED") || 
                                statusUpper.contains("CANCELED") ||
                                statusUpper.contains("REJECTED") ||
                                statusUpper.contains("CLOSED")) {
                                // CR is completed/cancelled, but continue to check by Reference
                                //system.out.println("[BulkUpdateDAO] First check - CR is completed/cancelled, continuing to check by Reference");
                            } else {
                                // CR is active
                                //system.out.println("[BulkUpdateDAO] First check - CR is ACTIVE (returning true)");
                                return true;
                            }
                        } else {
                            // Status is null, consider it active
                            //system.out.println("[BulkUpdateDAO] First check - CR status is NULL, considering it active (returning true)");
                            return true;
                        }
                    } else {
                        //system.out.println("[BulkUpdateDAO] First check - active_cr_id is NULL, continuing to check by Reference");
                    }
                } else {
                    //system.out.println("[BulkUpdateDAO] First check - No row found in table");
                }
            }
        }
        
        // Second check: Search by Reference (e.g., "Dataset 57", "Glossary 123")
        // Get facet name from table name
        String facetName = getFacetNameFromTable(tableName);
        //system.out.println("[BulkUpdateDAO] Second check - facetName from table: " + facetName);
        
        if (facetName == null) {
            //system.out.println("[BulkUpdateDAO] Second check - facetName is NULL, returning false");
            return false;
        }
        
        // Build reference patterns (capitalized and lowercase)
        String facetNameCapitalized = facetName.substring(0, 1).toUpperCase() + facetName.substring(1).toLowerCase();
        String reference = facetNameCapitalized + " " + objectId;
        String referenceLower = facetName.toLowerCase() + " " + objectId;
        String referencePattern = "%" + facetName + "%" + objectId + "%";
        
        //system.out.println("[BulkUpdateDAO] Second check - Reference patterns: " +
            //"reference=" + reference + ", referenceLower=" + referenceLower + ", pattern=" + referencePattern);
        
        // First, let's check ALL CRs related to this object (for debugging)
        String debugSql = """
            SELECT cr.ID, cr.Reference, cr.PrimaryName, cr.Mandatory_Workflow, cr.Deleted_At, 
                   cr.CR_StatusID, crs.PrimaryName as status_name
            FROM changerequest cr
            LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID
            WHERE (
                cr.Reference = ?
                OR cr.Reference = ?
                OR LOWER(cr.Reference) = LOWER(?)
                OR LOWER(cr.Reference) LIKE LOWER(?)
                OR LOWER(cr.PrimaryName) LIKE LOWER(?)
                OR LOWER(cr.Summary) LIKE LOWER(?)
            )
            ORDER BY cr.Created_At DESC
            """;
        
        try (PreparedStatement debugStmt = conn.prepareStatement(debugSql)) {
            debugStmt.setString(1, reference);
            debugStmt.setString(2, referenceLower);
            debugStmt.setString(3, reference);
            debugStmt.setString(4, referencePattern);
            debugStmt.setString(5, referencePattern);
            debugStmt.setString(6, referencePattern);
            
            //system.out.println("[BulkUpdateDAO] Debug - Checking ALL CRs with patterns: " +
               // reference + ", " + referenceLower + ", " + referencePattern);
            
            try (ResultSet debugRs = debugStmt.executeQuery()) {
                int crCount = 0;
                while (debugRs.next()) {
                    crCount++;
                    debugRs.getInt("ID");
                    debugRs.getString("Reference");
                    debugRs.getString("PrimaryName");
                    debugRs.getInt("Mandatory_Workflow");
                    debugRs.getObject("Deleted_At");
                    debugRs.getObject("CR_StatusID");
                    debugRs.getString("status_name");
                    
                    //system.out.println("[BulkUpdateDAO] Debug - Found CR #" + crCount +
                       // ": ID=" + crId + ", Reference=" + crRef + ", Name=" + crName +
                    //    ", Mandatory_Workflow=" + mandatoryWorkflow + ", Deleted_At=" + deletedAt +
                      //  ", StatusID=" + crStatusId + ", StatusName=" + statusName);
                }
                if (crCount == 0) {
                    //system.out.println("[BulkUpdateDAO] Debug - No CRs found at all with these patterns");
                }
            }
        }
        
        // Search for active Change Request by Reference
        String crSql = """
            SELECT cr.ID, crs.PrimaryName as status_name
            FROM changerequest cr
            LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID
            WHERE (
                cr.Reference = ?
                OR cr.Reference = ?
                OR LOWER(cr.Reference) = LOWER(?)
                OR LOWER(cr.Reference) LIKE LOWER(?)
                OR LOWER(cr.PrimaryName) LIKE LOWER(?)
                OR LOWER(cr.Summary) LIKE LOWER(?)
            )
            AND cr.Mandatory_Workflow = 1
            AND cr.Deleted_At IS NULL
            AND (
                cr.CR_StatusID IS NULL
                OR crs.ID IS NULL
                OR (
                    LOWER(crs.PrimaryName) NOT LIKE '%complete%' 
                    AND LOWER(crs.PrimaryName) NOT LIKE '%cancelled%'
                    AND LOWER(crs.PrimaryName) NOT LIKE '%canceled%'
                    AND LOWER(crs.PrimaryName) NOT LIKE '%reject%'
                    AND LOWER(crs.PrimaryName) NOT LIKE '%closed%'
                )
            )
            ORDER BY cr.Created_At DESC
            LIMIT 1
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(crSql)) {
            stmt.setString(1, reference);
            stmt.setString(2, referenceLower);
            stmt.setString(3, reference);
            stmt.setString(4, referencePattern);
            stmt.setString(5, referencePattern);
            stmt.setString(6, referencePattern);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    rs.getInt("ID");
                    rs.getString("status_name");
                    //system.out.println("[BulkUpdateDAO] Second check - Found active CR by Reference: ID=" + crId + ", status=" + crStatus + " (returning true)");
                    // Found an active Change Request by Reference
                    return true;
                } else {
                    //system.out.println("[BulkUpdateDAO] Second check - No active CR found by Reference (after filtering by Mandatory_Workflow=1, Deleted_At IS NULL, and status)");
                }
            }
        }
        
        //system.out.println("[BulkUpdateDAO] Final result: No active CR found (returning false)");
        return false;
    }
    
    /**
     * Get facet name from table name (reverse lookup)
     */
    private String getFacetNameFromTable(String tableName) {
        if (tableName == null) {
            //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: tableName is NULL");
            return null;
        }
        
        String normalized = tableName.toLowerCase();
        //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: tableName=" + tableName + ", normalized=" + normalized);
        
        // Direct match for common facets
        if (normalized.equals("dataset")) {
            //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: Direct match - returning 'dataset'");
            return "dataset";
        }
        if (normalized.equals("glossary")) {
            //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: Direct match - returning 'glossary'");
            return "glossary";
        }
        if (normalized.equals("system")) {
            //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: Direct match - returning 'system'");
            return "system";
        }
        if (normalized.equals("process")) {
            //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: Direct match - returning 'process'");
            return "process";
        }
        
        // Reverse lookup from FACET_TABLE_MAP
        Map<String, String> facetTableMap = BulkUpdateDefinitionConfig.FACET_TABLE_MAP;
        //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: Searching in FACET_TABLE_MAP...");
        for (String facetName : facetTableMap.keySet()) {
            if (facetTableMap.get(facetName).equalsIgnoreCase(tableName)) {
                //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: Found match - facetName=" + facetName);
                // Return the normalized facet name (prefer base name without variants)
                if (facetName.equals("dataset") || facetName.equals("glossary") || 
                    facetName.equals("system") || facetName.equals("process")) {
                    //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: Returning facetName=" + facetName);
                    return facetName;
                }
            }
        }
        
        //system.out.println("[BulkUpdateDAO] getFacetNameFromTable: No match found, returning NULL");
        return null;
    }

    /**
     * Update a single field on a single object
     */
    public boolean updateField(Connection conn, String tableName, int objectId, String columnName, Object value,
            int userId) throws SQLException {
        // Get the correct datetime and user ID column names for this table
        String datetimeColumn = findDatetimeColumn(conn, tableName);
        String userIdColumn = findUserIdColumn(conn, tableName);
        String idColumn = findIdColumn(conn, tableName);
        
        //system.out.println("[BulkUpdateDAO] updateField: tableName=" + tableName + ", columnName=" + columnName +
         //   ", datetimeColumn=" + datetimeColumn + ", userIdColumn=" + userIdColumn + ", idColumn=" + idColumn);
        
        String sql = "UPDATE `" + tableName + "` SET `" + columnName + "` = ?";
        if (userIdColumn != null) {
            sql += ", `" + userIdColumn + "` = ?";
        }
        if (datetimeColumn != null) {
            sql += ", `" + datetimeColumn + "` = NOW()";
        }
        sql += " WHERE `" + idColumn + "` = ?";
        
        //system.out.println("[BulkUpdateDAO] updateField SQL: " + sql);
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            if (value == null) {
                stmt.setNull(paramIndex++, Types.INTEGER);
            } else if (value instanceof Integer) {
                stmt.setInt(paramIndex++, (Integer) value);
            } else if (value instanceof String) {
                stmt.setString(paramIndex++, (String) value);
            } else {
                stmt.setObject(paramIndex++, value);
            }
            if (userIdColumn != null) {
                stmt.setInt(paramIndex++, userId);
            }
            stmt.setInt(paramIndex, objectId);
            return stmt.executeUpdate() > 0;
        }
    }
    
    /**
     * Find the datetime column name for a table
     */
    private String findDatetimeColumn(Connection conn, String tableName) throws SQLException {
        String[] possibleColumns = {
            "LastUpdateDatetime", "Last_Update_Datetime", "LastUpdated_Datetime",
            "lastupdatedatetime", "last_updated_datetime", "lastupdated_datetime",
            "LastUpdateDate", "Last_Update_Date", "lastupdatedate",
            "Updated_At", "updated_at", "UpdatedAt"
        };
        
        return findColumn(conn, tableName, possibleColumns);
    }
    
    /**
     * Find the user ID column name for a table
     */
    private String findUserIdColumn(Connection conn, String tableName) throws SQLException {
        String[] possibleColumns = {
            "LastUpdateUser_id", "Last_Update_User_id", "lastupdateuser_id",
            "LastUpdateUser_ID", "Last_Update_User_ID", "lastupdateuser_ID",
            "Updated_By", "updated_by", "UpdatedBy"
        };
        
        return findColumn(conn, tableName, possibleColumns);
    }
    
    /**
     * Find the ID column name for a table
     */
    private String findIdColumn(Connection conn, String tableName) throws SQLException {
        String[] possibleColumns = { "id", "ID", "Id" };
        String found = findColumn(conn, tableName, possibleColumns);
        return found != null ? found : "id"; // Default to "id" if not found
    }
    
    /**
     * Find a column by checking possible names against actual table columns
     */
    private String findColumn(Connection conn, String tableName, String[] possibleColumns) throws SQLException {
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        
        Set<String> actualColumns = new HashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    actualColumns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        
        // Find the first matching column (case-insensitive)
        for (String possibleColumn : possibleColumns) {
            for (String actualColumn : actualColumns) {
                if (actualColumn.equalsIgnoreCase(possibleColumn)) {
                    return actualColumn; // Return the actual column name from the table
                }
            }
        }
        
        return null; // No matching column found
    }

    /**
     * Get lookup values for a table
     */
    public List<Map<String, Object>> getLookupValues(Connection conn, String tableName) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();

        // Determine the name column based on table
        // Match the logic from getObjectInfo for consistency
        String nameColumn = "PrimaryName";
        if (tableName.equalsIgnoreCase("people")) {
            nameColumn = "CONCAT(First_Name, ' ', Last_Name)";
        } else if (tableName.equalsIgnoreCase("cia_rating")) {
            nameColumn = "`Values`";
        } else if (tableName.equalsIgnoreCase("interface") || tableName.equalsIgnoreCase("system_interface")) {
            nameColumn = "Name";
        } else if (tableName.equalsIgnoreCase("system")) {
            nameColumn = "Name";
        } else if (tableName.equalsIgnoreCase("legal") || tableName.equalsIgnoreCase("legal_entity")) {
            nameColumn = "ShortName";
        } else if (tableName.equalsIgnoreCase("segment") || tableName.equalsIgnoreCase("org_unit")
                || tableName.equalsIgnoreCase("org-unit")) {
            nameColumn = "Name";
        }

        String sql = "SELECT id, " + nameColumn + " AS name FROM `" + tableName + "` ORDER BY name";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("name"));
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Accept role - set AcceptedID = 1 and AcceptedDate = NOW()
     */
    public boolean acceptRole(Connection conn, int objectXPeopleId, int userId) throws SQLException {
        String sql = "UPDATE object_x_people SET AcceptedID = 1, AcceptedDate = NOW(), lastupdateuser_id = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, objectXPeopleId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Reassign role to different person
     */
    public boolean reassignRole(Connection conn, int objectXPeopleId, int newPersonId, int userId) throws SQLException {
        String sql = "UPDATE object_x_people SET ipid = ?, AcceptedID = 0, AcceptedDate = NULL, lastupdateuser_id = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newPersonId);
            stmt.setInt(2, userId);
            stmt.setInt(3, objectXPeopleId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Change role status
     */
    public boolean changeRoleStatus(Connection conn, int objectXPeopleId, int newStatusId, int userId)
            throws SQLException {
        String sql = "UPDATE object_x_people SET statusID = ?, lastupdateuser_id = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newStatusId);
            stmt.setInt(2, userId);
            stmt.setInt(3, objectXPeopleId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Find the associated object for a role assignment and check for active CR
     * Returns a map with "tableName" and "objectId" if found, null otherwise
     */
    private Map<String, Object> findAssociatedObject(Connection conn, int objectXPeopleId) throws SQLException {
        // Check all junction tables to find the associated object
        String[][] junctionMappings = {
            {"dataset_x_objectxpeople", "Dataset_ID", "dataset", "Object_x_ipid"},
            {"system_x_objectxpeople", "SystemID", "system", "Object_x_ipid"},
            {"interface_x_objectxpeople", "InterfaceID", "interface", "Object_x_ipid"},
            {"glossary_x_objectxpeople", "GlossaryID", "glossary", "Object_x_ipid"},
            {"process_x_objectxpeople", "process_id", "process", "object_x_ip"},
            {"policy_x_objectxpeople", "Policy_ID", "policy", "Object_X_IP"},
            {"product_x_objectxpeople", "product_id", "product", "Object_x_ip"},
            {"project_x_objectxpeople", "project_id", "project", "object_x_ip"},
            {"businessarea_x_objectxpeople", "BusinessAreaID", "business_area", "Object_x_ipid"},
            {"client_x_objectxpeople", "ClientID", "client", "Object_x_ipid"},
            {"committee_x_objectxpeople", "Committee_ID", "committee", "Object_x_ipid"},
            {"legal_x_objectxpeople", "Legal_ID", "legal", "Object_x_ip"},
            {"capability_x_objectxpeople", "CapabilityID", "capability", "Object_x_ipid"},
            {"regulation_x_objectxpeople", "RegulationID", "regulation", "Object_x_ipid"},
            {"attribute_x_objectxpeople", "AttributeID", "attribute", "Object_x_ipid"}
        };

        for (String[] mapping : junctionMappings) {
            String junctionTable = mapping[0];
            String objectIdColumn = mapping[1];
            String objectTable = mapping[2];
            String oxpColumn = mapping[3];
            
            try {
                String sql = "SELECT " + objectIdColumn + " FROM " + junctionTable + 
                            " WHERE " + oxpColumn + " = ? LIMIT 1";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, objectXPeopleId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int objectId = rs.getInt(objectIdColumn);
                            Map<String, Object> result = new HashMap<>();
                            result.put("tableName", objectTable);
                            result.put("objectId", objectId);
                            return result;
                        }
                    }
                }
            } catch (SQLException e) {
                // Table or column doesn't exist, try next
                continue;
            }
        }
        return null;
    }

    /**
     * Delete role - soft delete by setting deleted flag if column exists, otherwise
     * hard delete
     */
    public boolean deleteRole(Connection conn, int objectXPeopleId, int userId) throws SQLException {
        // Check for active CR before deletion
        Map<String, Object> associatedObject = findAssociatedObject(conn, objectXPeopleId);
        if (associatedObject != null) {
            String tableName = (String) associatedObject.get("tableName");
            int objectId = (Integer) associatedObject.get("objectId");
            
            if (hasActiveChangeRequest(conn, tableName, objectId)) {
                throw new SQLException("Cannot delete role: The associated object has an active change request. Please complete or cancel the change request first.");
            }
        }

        // Try soft delete first (check if 'deleted' or 'is_deleted' column exists)
        try {
            // First try to check if soft delete column exists
            String checkSql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'object_x_people' " +
                    "AND COLUMN_NAME IN ('deleted', 'is_deleted', 'status')";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                    ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    String deleteColumn = rs.getString("COLUMN_NAME");
                    String softDeleteSql;
                    if (deleteColumn.equals("status")) {
                        // Set status to inactive/deleted
                        softDeleteSql = "UPDATE object_x_people SET status = 'DELETED', lastupdateuser_id = ? WHERE id = ?";
                    } else {
                        softDeleteSql = "UPDATE object_x_people SET " + deleteColumn
                                + " = 1, lastupdateuser_id = ? WHERE id = ?";
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(softDeleteSql)) {
                        stmt.setInt(1, userId);
                        stmt.setInt(2, objectXPeopleId);
                        return stmt.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            // Re-throw if it's our CR validation error
            if (e.getMessage() != null && e.getMessage().contains("Cannot delete role")) {
                throw e;
            }
            // Soft delete column doesn't exist, proceed with hard delete
        }

        // Hard delete - need to delete from junction tables first
        // Get the module/object links before deleting
        deleteRoleFromJunctionTables(conn, objectXPeopleId);

        // Then delete from object_x_people
        String sql = "DELETE FROM object_x_people WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectXPeopleId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Delete role links from junction tables
     */
    private void deleteRoleFromJunctionTables(Connection conn, int objectXPeopleId) throws SQLException {
        // List of junction tables that reference object_x_people
        String[] junctionTables = {
                "dataset_x_objectxpeople",
                "glossary_x_objectxpeople",
                "system_x_objectxpeople",
                "process_x_objectxpeople",
                "policy_x_objectxpeople",
                "product_x_objectxpeople",
                "project_x_objectxpeople",
                "client_x_objectxpeople",
                "committee_x_objectxpeople",
                "businessarea_x_objectxpeople",
                "capability_x_objectxpeople",
                "legal_x_objectxpeople",
                "regulation_x_objectxpeople",
                "interface_x_objectxpeople"
        };

        for (String table : junctionTables) {
            // Try different column names for the foreign key
            String[] columnNames = { "Object_x_ipid", "object_x_ip", "Object_X_IP", "oxp_id" };
            for (String columnName : columnNames) {
                try {
                    String sql = "DELETE FROM " + table + " WHERE " + columnName + " = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, objectXPeopleId);
                        stmt.executeUpdate();
                        break; // Column found, move to next table
                    }
                } catch (SQLException e) {
                    // Column doesn't exist in this table, try next
                }
            }
        }
    }

    /**
     * Get role data with extended columns for bulk update items display
     */
    public List<Map<String, Object>> getRoleDataWithExtendedColumns(Connection conn, List<Integer> objectXPeopleIds)
            throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        if (objectXPeopleIds.isEmpty())
            return results;

        String placeholders = String.join(",", Collections.nCopies(objectXPeopleIds.size(), "?"));
        String sql = "SELECT " +
                "oxp.ID, " +
                "orl.PrimaryName AS Role, " +
                "CONCAT(p.First_Name, ' ', p.Last_Name) AS AssignedTo, " +
                "p.ID AS PersonId, " +
                "oxp.ipid, " +
                "oxp.AcceptedDate AS DateAccepted, " +
                "CASE WHEN oxp.AcceptedID = 1 THEN 'Yes' ELSE 'No' END AS RoleAccepted, " +
                "COALESCE(rs.PrimaryName, 'Active') AS RoleStatus, " +
                "COALESCE(" +
                "  d.PrimaryName, " +           // Dataset
                "  s.name, " +                  // System
                "  i.name, " +                  // Interface
                "  g.name, " +                   // Glossary
                "  prc.PrimaryName, " +         // Process
                "  pol.PrimaryName, " +         // Policy
                "  prod.PrimaryName, " +        // Product
                "  proj.PrimaryName, " +        // Project
                "  ba.PrimaryName, " +          // Business Area
                "  c.PrimaryName, " +           // Client
                "  com.PrimaryName, " +         // Committee
                "  le.ShortName, " +            // Legal Entity
                "  cap.PrimaryName, " +         // Capability
                "  reg.PrimaryName, " +         // Regulation
                "  a.PrimaryName " +            // Attribute
                ") AS Object, " +
                "COALESCE(" +
                "  crs_d.PrimaryName, " +        // CR Status from Dataset
                "  crs_s.PrimaryName, " +       // CR Status from System
                "  crs_i.PrimaryName, " +       // CR Status from Interface
                "  crs_g.PrimaryName, " +       // CR Status from Glossary
                "  crs_prc.PrimaryName, " +     // CR Status from Process
                "  crs_pol.PrimaryName, " +     // CR Status from Policy
                "  crs_prod.PrimaryName, " +    // CR Status from Product
                "  crs_proj.PrimaryName, " +    // CR Status from Project
                "  crs_ba.PrimaryName, " +      // CR Status from Business Area
                "  crs_c.PrimaryName, " +       // CR Status from Client
                "  crs_com.PrimaryName, " +     // CR Status from Committee
                "  crs_le.PrimaryName, " +      // CR Status from Legal Entity
                "  crs_cap.PrimaryName, " +     // CR Status from Capability
                "  crs_reg.PrimaryName, " +     // CR Status from Regulation
                "  crs_a.PrimaryName " +        // CR Status from Attribute
                ") AS CRStatus " +
                "FROM object_x_people oxp " +
                "JOIN object_role orl ON oxp.RoleID = orl.ID " +
                "JOIN people p ON oxp.ipid = p.ID " +
                "LEFT JOIN role_status rs ON oxp.statusID = rs.ID " +
                "LEFT JOIN dataset_x_objectxpeople dxoxp ON oxp.ID = dxoxp.Object_x_ipid " +
                "LEFT JOIN dataset d ON dxoxp.Dataset_ID = d.ID " +
                "LEFT JOIN changerequest cr_d ON d.active_cr_id = cr_d.ID " +
                "LEFT JOIN changerequeststatus crs_d ON cr_d.CR_StatusID = crs_d.ID " +
                "LEFT JOIN system_x_objectxpeople sxoxp ON oxp.ID = sxoxp.Object_x_ipid " +
                "LEFT JOIN system s ON sxoxp.SystemID = s.ID " +
                "LEFT JOIN changerequest cr_s ON s.active_cr_id = cr_s.ID " +
                "LEFT JOIN changerequeststatus crs_s ON cr_s.CR_StatusID = crs_s.ID " +
                "LEFT JOIN interface_x_objectxpeople ixoxp ON oxp.ID = ixoxp.Object_x_ipid " +
                "LEFT JOIN interface i ON ixoxp.InterfaceID = i.ID " +
                "LEFT JOIN changerequest cr_i ON i.active_cr_id = cr_i.ID " +
                "LEFT JOIN changerequeststatus crs_i ON cr_i.CR_StatusID = crs_i.ID " +
                "LEFT JOIN glossary_x_objectxpeople gxoxp ON oxp.ID = gxoxp.Object_x_ipid " +
                "LEFT JOIN glossary g ON gxoxp.GlossaryID = g.ID " +
                "LEFT JOIN changerequest cr_g ON g.active_cr_id = cr_g.ID " +
                "LEFT JOIN changerequeststatus crs_g ON cr_g.CR_StatusID = crs_g.ID " +
                "LEFT JOIN process_x_objectxpeople prcxoxp ON oxp.ID = prcxoxp.object_x_ip " +
                "LEFT JOIN process prc ON prcxoxp.process_id = prc.ID " +
                "LEFT JOIN changerequest cr_prc ON prc.active_cr_id = cr_prc.ID " +
                "LEFT JOIN changerequeststatus crs_prc ON cr_prc.CR_StatusID = crs_prc.ID " +
                "LEFT JOIN policy_x_objectxpeople polxoxp ON oxp.ID = polxoxp.Object_X_IP " +
                "LEFT JOIN policy pol ON polxoxp.Policy_ID = pol.ID " +
                "LEFT JOIN changerequest cr_pol ON pol.active_cr_id = cr_pol.ID " +
                "LEFT JOIN changerequeststatus crs_pol ON cr_pol.CR_StatusID = crs_pol.ID " +
                "LEFT JOIN product_x_objectxpeople prodxoxp ON oxp.ID = prodxoxp.Object_x_ip " +
                "LEFT JOIN product prod ON prodxoxp.product_id = prod.ID " +
                "LEFT JOIN changerequest cr_prod ON prod.active_cr_id = cr_prod.ID " +
                "LEFT JOIN changerequeststatus crs_prod ON cr_prod.CR_StatusID = crs_prod.ID " +
                "LEFT JOIN project_x_objectxpeople projxoxp ON oxp.ID = projxoxp.object_x_ip " +
                "LEFT JOIN project proj ON projxoxp.project_id = proj.ID " +
                "LEFT JOIN changerequest cr_proj ON proj.active_cr_id = cr_proj.ID " +
                "LEFT JOIN changerequeststatus crs_proj ON cr_proj.CR_StatusID = crs_proj.ID " +
                "LEFT JOIN businessarea_x_objectxpeople baxoxp ON oxp.ID = baxoxp.Object_x_ipid " +
                "LEFT JOIN business_area ba ON baxoxp.BusinessAreaID = ba.ID " +
                "LEFT JOIN changerequest cr_ba ON ba.active_cr_id = cr_ba.ID " +
                "LEFT JOIN changerequeststatus crs_ba ON cr_ba.CR_StatusID = crs_ba.ID " +
                "LEFT JOIN client_x_objectxpeople cxoxp ON oxp.ID = cxoxp.Object_x_ipid " +
                "LEFT JOIN client c ON cxoxp.ClientID = c.ID " +
                "LEFT JOIN changerequest cr_c ON c.active_cr_id = cr_c.ID " +
                "LEFT JOIN changerequeststatus crs_c ON cr_c.CR_StatusID = crs_c.ID " +
                "LEFT JOIN committee_x_objectxpeople comxoxp ON oxp.ID = comxoxp.Object_x_ipid " +
                "LEFT JOIN committee com ON comxoxp.Committee_ID = com.ID " +
                "LEFT JOIN changerequest cr_com ON com.active_cr_id = cr_com.ID " +
                "LEFT JOIN changerequeststatus crs_com ON cr_com.CR_StatusID = crs_com.ID " +
                "LEFT JOIN legal_x_objectxpeople lexoxp ON oxp.ID = lexoxp.Object_x_ip " +
                "LEFT JOIN legal le ON lexoxp.Legal_ID = le.ID " +
                "LEFT JOIN changerequest cr_le ON le.active_cr_id = cr_le.ID " +
                "LEFT JOIN changerequeststatus crs_le ON cr_le.CR_StatusID = crs_le.ID " +
                "LEFT JOIN capability_x_objectxpeople capxoxp ON oxp.ID = capxoxp.Object_x_ipid " +
                "LEFT JOIN capability cap ON capxoxp.CapabilityID = cap.ID " +
                "LEFT JOIN changerequest cr_cap ON cap.active_cr_id = cr_cap.ID " +
                "LEFT JOIN changerequeststatus crs_cap ON cr_cap.CR_StatusID = crs_cap.ID " +
                "LEFT JOIN regulation_x_objectxpeople regxoxp ON oxp.ID = regxoxp.Object_x_ipid " +
                "LEFT JOIN regulation reg ON regxoxp.RegulationID = reg.ID " +
                "LEFT JOIN changerequest cr_reg ON reg.active_cr_id = cr_reg.ID " +
                "LEFT JOIN changerequeststatus crs_reg ON cr_reg.CR_StatusID = crs_reg.ID " +
                "LEFT JOIN attribute_x_objectxpeople axoxp ON oxp.ID = axoxp.Object_x_ipid " +
                "LEFT JOIN attribute a ON axoxp.AttributeID = a.ID " +
                "LEFT JOIN changerequest cr_a ON a.active_cr_id = cr_a.ID " +
                "LEFT JOIN changerequeststatus crs_a ON cr_a.CR_StatusID = crs_a.ID " +
                "WHERE oxp.ID IN (" + placeholders + ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Integer id : objectXPeopleIds) {
                stmt.setInt(idx++, id);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("ID", rs.getInt("ID"));
                    row.put("Role", rs.getString("Role"));
                    row.put("AssignedTo", rs.getString("AssignedTo"));
                    row.put("PersonId", rs.getInt("PersonId"));
                    row.put("ipid", rs.getInt("ipid"));
                    row.put("DateAccepted", rs.getTimestamp("DateAccepted"));
                    row.put("RoleAccepted", rs.getString("RoleAccepted"));
                    row.put("RoleStatus", rs.getString("RoleStatus"));
                    row.put("Object", rs.getString("Object"));
                    String crStatus = rs.getString("CRStatus");
                    row.put("CRStatus", crStatus != null ? crStatus : "");
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Get object name and link info for an object, including workflow status
     */
    public Map<String, Object> getObjectInfo(Connection conn, String facet, int objectId) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
        if (tableName == null)
            return result;

        String nameColumn = "PrimaryName";
        if (tableName.equals("people")) {
            nameColumn = "CONCAT(First_Name, ' ', Last_Name)";
        } else if (tableName.equals("interface") || tableName.equals("system_interface")) {
            nameColumn = "Name";
        } else if (tableName.equals("system")) {
            nameColumn = "Name";
        } else if (tableName.equals("legal") || tableName.equals("legal_entity")) {
            nameColumn = "ShortName";
        }

        // Check if table has active_cr_id column for workflow awareness
        boolean hasActiveCrId = BulkUpdateDefinitionConfig.requiresCRValidation(facet);
        String activeCrIdSelect = hasActiveCrId ? ", active_cr_id" : "";

        // Handle case sensitivity for id column (some tables use 'id', others use 'ID')
        String idColumn = "id";
        if (tableName.equals("policy") || tableName.equals("glossary") || tableName.equals("regulation") 
                || tableName.equals("capability") || tableName.equals("committee") || tableName.equals("client")) {
            idColumn = "ID";
        }
        
        String sql = "SELECT " + idColumn + " AS id, " + nameColumn + " AS name" + activeCrIdSelect + " FROM `" + tableName
                + "` WHERE " + idColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    result.put("id", rs.getInt("id"));
                    result.put("ID", rs.getInt("id")); // Also add as ID for compatibility
                    result.put("name", rs.getString("name"));
                    result.put("Name", rs.getString("name")); // Also add as Name for compatibility
                    result.put("facet", facet);

                    // Add workflow status
                    if (hasActiveCrId) {
                        Object activeCrId = rs.getObject("active_cr_id");
                        result.put("isUnderWorkflow", activeCrId != null);
                        if (activeCrId != null) {
                            result.put("activeCrId", activeCrId);
                        }
                    } else {
                        result.put("isUnderWorkflow", false);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Get audit history table name for a given table name
     * @param tableName The main table name (e.g., "dataset", "system")
     * @return The audit history table name (e.g., "dataset_audit_history") or null if not found
     */
    private String getAuditHistoryTableName(String tableName) {
        if (tableName == null) {
            return null;
        }
        
        String normalized = tableName.toLowerCase();
        
        // Mapping from table name to audit_history table name
        Map<String, String> auditHistoryMap = Map.ofEntries(
            Map.entry("dataset", "dataset_audit_history"),
            Map.entry("glossary", "glossary_audit_history"),
            Map.entry("system", "system_audit_history"),
            Map.entry("process", "process_audit_history"),
            Map.entry("attribute", "attribute_audit_history"),
            Map.entry("policy", "policy_audit_history"),
            Map.entry("client", "client_audit_history"),
            Map.entry("committee", "committee_audit_history"),
            Map.entry("product", "product_audit_history"),
            Map.entry("project", "project_audit_history"),
            Map.entry("interface", "interface_audit_history"),
            Map.entry("capability", "capability_audit_history"),
            Map.entry("legal", "legal_audit_history"),
            Map.entry("business_area", "business_area_audit_history"),
            Map.entry("regulation", "regulation_audit_history"),
            Map.entry("org_unit", "orgunit_audit_history")
        );
        
        return auditHistoryMap.get(normalized);
    }
    
    /**
     * Get object name for audit history from table name
     * @param tableName The main table name (e.g., "dataset", "system")
     * @return The object name for the audit history (e.g., "Dataset", "System")
     */
    private String getObjectName(String tableName) {
        if (tableName == null) {
            return null;
        }
        
        String normalized = tableName.toLowerCase();
        
        // Mapping from table name to object name
        Map<String, String> objectNameMap = Map.ofEntries(
            Map.entry("dataset", "Dataset"),
            Map.entry("glossary", "Glossary"),
            Map.entry("system", "System"),
            Map.entry("process", "Process"),
            Map.entry("attribute", "Attribute"),
            Map.entry("policy", "Policy"),
            Map.entry("client", "Client"),
            Map.entry("committee", "Committee"),
            Map.entry("product", "Product"),
            Map.entry("project", "Project"),
            Map.entry("interface", "Interface"),
            Map.entry("capability", "Capability"),
            Map.entry("legal", "Legal Entity"),
            Map.entry("business_area", "Business Area"),
            Map.entry("regulation", "Regulation"),
            Map.entry("org_unit", "Org Unit")
        );
        
        return objectNameMap.get(normalized);
    }
    
    /**
     * Check if a table has an audit_history table
     * @param conn Database connection
     * @param tableName The main table name
     * @return true if audit_history table exists, false otherwise
     */
    private boolean tableHasAuditHistory(Connection conn, String tableName) throws SQLException {
        String auditHistoryTable = getAuditHistoryTableName(tableName);
        if (auditHistoryTable == null) {
            return false;
        }
        
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auditHistoryTable);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Get lookup name from ID for a given table
     * @param conn Database connection
     * @param tableName The lookup table name
     * @param id The ID to look up
     * @return The name corresponding to the ID, or the ID as string if not found
     */
    private String getLookupNameFromId(Connection conn, String tableName, Object id) throws SQLException {
        if (id == null || tableName == null) {
            return null;
        }
        
        // Convert id to integer if possible
        Integer lookupId = null;
        try {
            if (id instanceof Integer) {
                lookupId = (Integer) id;
            } else if (id instanceof String) {
                lookupId = Integer.parseInt((String) id);
            } else if (id instanceof Number) {
                lookupId = ((Number) id).intValue();
            } else {
                return String.valueOf(id);
            }
        } catch (NumberFormatException e) {
            // If it's not a number, return as is
            return String.valueOf(id);
        }
        
        // Determine the name column based on table
        String nameColumn = "PrimaryName";
        String idColumn = "id";
        
        if (tableName.equalsIgnoreCase("people")) {
            nameColumn = "CONCAT(First_Name, ' ', Last_Name)";
            idColumn = "ID";
        } else if (tableName.equalsIgnoreCase("cia_rating")) {
            nameColumn = "`Values`";
        } else if (tableName.equalsIgnoreCase("segment") || tableName.equalsIgnoreCase("org_unit")
                || tableName.equalsIgnoreCase("org-unit")) {
            nameColumn = "Name";
        } else if (tableName.equalsIgnoreCase("system") || tableName.equalsIgnoreCase("interface")) {
            nameColumn = "Name";
        } else if (tableName.equalsIgnoreCase("legal") || tableName.equalsIgnoreCase("legal_entity")) {
            nameColumn = "ShortName";
            idColumn = "ID";
        } else if (tableName.equalsIgnoreCase("axon_viewing") || tableName.equalsIgnoreCase("viewing")) {
            // viewing table uses Name column (not PrimaryName)
            nameColumn = "Name";
        } else if (tableName.equalsIgnoreCase("glossary")) {
            // glossary table uses Name column
            nameColumn = "Name";
            idColumn = "ID";
        } else if (tableName.equalsIgnoreCase("glossary_format_type") || 
                   tableName.equalsIgnoreCase("glossary_type") ||
                   tableName.equalsIgnoreCase("system_lifecycle") ||
                   tableName.equalsIgnoreCase("system_type") ||
                   tableName.equalsIgnoreCase("system_classification") ||
                   tableName.equalsIgnoreCase("interface_classification") ||
                   tableName.equalsIgnoreCase("interface_lifecycle") ||
                   tableName.equalsIgnoreCase("interface_transfer_method") ||
                   tableName.equalsIgnoreCase("interface_transfer_format") ||
                   tableName.equalsIgnoreCase("interface_transfer")) {
            // These lookup tables use Name column instead of PrimaryName
            nameColumn = "Name";
        } else {
            // Check if table uses ID (uppercase) instead of id
            String checkSql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                             "AND COLUMN_NAME IN ('id', 'ID') LIMIT 1";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, tableName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        idColumn = rs.getString("COLUMN_NAME");
                    }
                }
            }
        }
        
        String sql = "SELECT " + nameColumn + " AS name FROM `" + tableName + "` WHERE `" + idColumn + "` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, lookupId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        }
        
        // If not found, return the ID as string
        return String.valueOf(lookupId);
    }
    
    /**
     * Convert value to display string, resolving lookup/reference names if applicable
     * @param conn Database connection
     * @param facet The facet name
     * @param fieldName The field display name (e.g., "Type", "Lifecycle")
     * @param value The value to convert (could be ID or name)
     * @return The display string (name if lookup/reference, otherwise the value as string)
     */
    private String convertValueToDisplayString(Connection conn, String facet, String fieldName, Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        
        // Find field by display name (since logAudit is called with displayName)
        BulkUpdateDefinitionConfig.DefinitionField field = null;
        List<BulkUpdateDefinitionConfig.DefinitionField> fields = BulkUpdateDefinitionConfig.getDefinitionsForFacet(facet);
        for (BulkUpdateDefinitionConfig.DefinitionField f : fields) {
            if (f.getDisplayName().equalsIgnoreCase(fieldName)) {
                field = f;
                break;
            }
        }
        
        // If field is LOOKUP or REFERENCE, try to get the name
        if (field != null && field.getLookupTable() != null && 
            (field.getFieldType() == BulkUpdateDefinitionConfig.FieldType.LOOKUP ||
             field.getFieldType() == BulkUpdateDefinitionConfig.FieldType.REFERENCE)) {
            
            try {
                // Check if value is already a string (might be a name already)
                if (value instanceof String) {
                    String strValue = (String) value;
                    // Try to parse as integer, if fails, assume it's already a name
                    try {
                        Integer.parseInt(strValue.trim());
                        // It's a number, get the name
                        return getLookupNameFromId(conn, field.getLookupTable(), value);
                    } catch (NumberFormatException e) {
                        // It's already a name, return as is
                        return strValue;
                    }
                } else if (value instanceof Number) {
                    // It's a number (ID), get the name
                    return getLookupNameFromId(conn, field.getLookupTable(), value);
                }
            } catch (SQLException e) {
                // If lookup fails, fall back to string value
                System.err.println("Failed to get lookup name for field " + fieldName + ": " + e.getMessage());
            }
        }
        
        // For non-lookup fields or if lookup failed, return as string
        return String.valueOf(value);
    }

    /**
     * Log audit entry for bulk update
     * Logs to facet-specific audit_history table if available, otherwise falls back to general audit table
     */
    public void logAudit(Connection conn, String facet, int objectId, String fieldName, Object oldValue,
            Object newValue, int userId, String action) throws SQLException {
        try {
            // Get username from user ID
            String userName = null;
            String userSql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS name FROM people WHERE ID = ?";
            try (PreparedStatement userStmt = conn.prepareStatement(userSql)) {
                userStmt.setInt(1, userId);
                try (ResultSet rs = userStmt.executeQuery()) {
                    if (rs.next()) {
                        userName = rs.getString("name");
                    }
                }
            }
            
            // Convert values to display strings (names instead of IDs)
            String oldValueDisplay = convertValueToDisplayString(conn, facet, fieldName, oldValue);
            String newValueDisplay = convertValueToDisplayString(conn, facet, fieldName, newValue);
            
            // Get table name from facet
            String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
            if (tableName == null) {
                // Fallback to general audit table
                logToGeneralAuditTable(conn, facet, objectId, fieldName, oldValueDisplay, newValueDisplay, userName, action);
                return;
            }
            
            // Check if this table has an audit_history table
            if (tableHasAuditHistory(conn, tableName)) {
                // Log to facet-specific audit_history table
                String auditHistoryTable = getAuditHistoryTableName(tableName);
                String objectName = getObjectName(tableName);
                
                if (auditHistoryTable != null && objectName != null) {
                    String sql = "INSERT INTO `" + auditHistoryTable + "` " +
                                "(id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
                    
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, objectId);
                        stmt.setString(2, objectName);
                        stmt.setString(3, "Details"); // event
                        stmt.setString(4, "Updated"); // updateType
                        stmt.setString(5, fieldName);
                        stmt.setString(6, oldValueDisplay);
                        stmt.setString(7, newValueDisplay);
                        stmt.setString(8, userName);
                        stmt.executeUpdate();
                    }
                    return; // Successfully logged to audit_history
                }
            }
            
            // Fallback to general audit table if no audit_history table exists
            logToGeneralAuditTable(conn, facet, objectId, fieldName, oldValueDisplay, newValueDisplay, userName, action);
            
        } catch (SQLException e) {
            // Audit logging failure should not fail the main operation
            System.err.println("Failed to log audit: " + e.getMessage());
        }
    }
    
    /**
     * Fallback method to log to general audit table
     */
    private void logToGeneralAuditTable(Connection conn, String facet, int objectId, String fieldName,
            Object oldValue, Object newValue, String userName, String action) throws SQLException {
        // Build action description with object info
        String actionDescription = action;
        if (facet != null && objectId > 0) {
            actionDescription = String.format("%s: %s #%d - %s", action, facet, objectId, fieldName);
        }
        
        // Insert into audit table
        String sql = "INSERT INTO audit (Action, Field_Name, Old_Value, New_Value, Created_time, UserName, Data_Store_ID) "
                + "VALUES (?, ?, ?, ?, NOW(), ?, NULL)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, actionDescription);
            stmt.setString(2, fieldName);
            stmt.setString(3, oldValue != null ? String.valueOf(oldValue) : null);
            stmt.setString(4, newValue != null ? String.valueOf(newValue) : null);
            stmt.setString(5, userName);
            stmt.executeUpdate();
        }
    }

    /**
     * Get current field value for an object
     */
    public Object getFieldValue(Connection conn, String tableName, int objectId, String columnName)
            throws SQLException {
        String sql = "SELECT `" + columnName + "` FROM `" + tableName + "` WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject(1);
                }
            }
        }
        return null;
    }

    /**
     * Get role status lookup values
     */
    public List<Map<String, Object>> getRoleStatusValues(Connection conn) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT id, PrimaryName AS name FROM role_status ORDER BY PrimaryName";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("name"));
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Get people lookup values for role reassignment
     */
    public List<Map<String, Object>> getPeopleValues(Connection conn) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT ID AS id, CONCAT(First_Name, ' ', Last_Name) AS name FROM people WHERE lifecycle_status_id != 3 ORDER BY name";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("name"));
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Get all descendant IDs (children and their descendants recursively) for a given object
     * Uses recursive SQL CTE to traverse the parent-child hierarchy
     * 
     * @param conn Database connection
     * @param tableName The table name (e.g., "glossary", "system", "process")
     * @param idColumn The ID column name (e.g., "ID", "id")
     * @param parentColumn The parent column name (e.g., "Parent_ID", "parent_id", "parentid")
     * @param objectId The object ID to get descendants for
     * @return Set of all descendant IDs (including direct children and all their descendants)
     */
    public Set<Integer> getAllDescendantIds(Connection conn, String tableName, String idColumn, String parentColumn, int objectId) throws SQLException {
        Set<Integer> descendantIds = new HashSet<>();
        
        // Find the deleted datetime column name for this table
        String deletedColumn = findDeletedDatetimeColumn(conn, tableName);
        String deletedCondition = "";
        if (deletedColumn != null) {
            deletedCondition = " AND " + deletedColumn + " IS NULL";
        }
        
        // Use recursive CTE to get all descendants
        // Base case: direct children
        // Recursive case: children of children
        String sql = "WITH RECURSIVE descendants AS (" +
                "    SELECT " + idColumn + " as id " +
                "    FROM `" + tableName + "` " +
                "    WHERE " + parentColumn + " = ?" + deletedCondition +
                "    UNION ALL " +
                "    SELECT t." + idColumn + " " +
                "    FROM `" + tableName + "` t " +
                "    INNER JOIN descendants d ON t." + parentColumn + " = d.id " +
                "    WHERE d.id IS NOT NULL" + deletedCondition +
                ") " +
                "SELECT DISTINCT id FROM descendants";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    descendantIds.add(rs.getInt("id"));
                }
            }
        } catch (SQLException e) {
            // If recursive CTE fails (e.g., MySQL < 8.0), fall back to iterative approach
            System.err.println("Recursive CTE failed for " + tableName + ", using iterative approach: " + e.getMessage());
            return getAllDescendantIdsIterative(conn, tableName, idColumn, parentColumn, objectId, deletedColumn);
        }
        
        return descendantIds;
    }
    
    /**
     * Fallback method to get descendants iteratively (for databases that don't support recursive CTE)
     */
    private Set<Integer> getAllDescendantIdsIterative(Connection conn, String tableName, String idColumn, 
            String parentColumn, int objectId, String deletedColumn) throws SQLException {
        Set<Integer> descendantIds = new HashSet<>();
        Set<Integer> currentLevel = new HashSet<>();
        currentLevel.add(objectId);
        
        String deletedCondition = "";
        if (deletedColumn != null) {
            deletedCondition = " AND " + deletedColumn + " IS NULL";
        }
        
        // Iteratively find children until no more children are found
        int maxDepth = 100; // Prevent infinite loops
        for (int depth = 0; depth < maxDepth; depth++) {
            Set<Integer> nextLevel = new HashSet<>();
            
            if (currentLevel.isEmpty()) {
                break;
            }
            
            // Build IN clause for current level
            String placeholders = String.join(",", Collections.nCopies(currentLevel.size(), "?"));
            String sql = "SELECT " + idColumn + " FROM `" + tableName + "` " +
                        "WHERE " + parentColumn + " IN (" + placeholders + ")" + deletedCondition;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                for (Integer parentId : currentLevel) {
                    stmt.setInt(paramIndex++, parentId);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int childId = rs.getInt(idColumn);
                        nextLevel.add(childId);
                        descendantIds.add(childId);
                    }
                }
            }
            
            if (nextLevel.isEmpty()) {
                break;
            }
            
            currentLevel = nextLevel;
        }
        
        return descendantIds;
    }
    
    /**
     * Find the deleted datetime column name for a table
     */
    private String findDeletedDatetimeColumn(Connection conn, String tableName) throws SQLException {
        String[] possibleColumns = {
            "Deleted_Datetime", "deleted_datetime", "DeletedDatetime", "deletedDatetime",
            "Deleted_Datetime", "DeletedDatetime", "deleted_datetime",
            "DeletedAt", "deleted_at", "deletedAt",
            "deletedatetime", "Deleted_Datetime"
        };
        
        return findColumn(conn, tableName, possibleColumns);
    }
}
