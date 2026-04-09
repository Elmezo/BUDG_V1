package com.example.budg_v2.dao;

import com.example.budg_v2.config.BulkUpdateDefinitionConfig;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Access Object for Bulk Delete operations.
 * Handles database operations for bulk deletes across facets.
 */
public class BulkDeleteDAO {

    // Mapping of facet types to their stakeholder junction tables and ID columns
    private static final Map<String, StakeholderTableInfo> STAKEHOLDER_TABLE_MAP = new HashMap<>();
    
    static {
        // Initialize stakeholder table mappings
        STAKEHOLDER_TABLE_MAP.put("system", new StakeholderTableInfo("system_x_objectxpeople", "SystemID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("dataset", new StakeholderTableInfo("dataset_x_objectxpeople", "Dataset_ID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("glossary", new StakeholderTableInfo("glossary_x_objectxpeople", "GlossaryID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("process", new StakeholderTableInfo("process_x_objectxpeople", "process_id", "object_x_ip"));
        STAKEHOLDER_TABLE_MAP.put("policy", new StakeholderTableInfo("policy_x_objectxpeople", "Policy_ID", "Object_X_IP"));
        STAKEHOLDER_TABLE_MAP.put("product", new StakeholderTableInfo("product_x_objectxpeople", "product_id", "Object_x_ip"));
        STAKEHOLDER_TABLE_MAP.put("project", new StakeholderTableInfo("project_x_objectxpeople", "project_id", "object_x_ip"));
        STAKEHOLDER_TABLE_MAP.put("client", new StakeholderTableInfo("client_x_objectxpeople", "ClientID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("committee", new StakeholderTableInfo("committee_x_objectxpeople", "Committee_ID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("business-area", new StakeholderTableInfo("businessarea_x_objectxpeople", "BusinessAreaID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("businessarea", new StakeholderTableInfo("businessarea_x_objectxpeople", "BusinessAreaID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("capability", new StakeholderTableInfo("capability_x_objectxpeople", "CapabilityID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("legal-entity", new StakeholderTableInfo("legal_x_objectxpeople", "Legal_ID", "Object_x_ip"));
        STAKEHOLDER_TABLE_MAP.put("legalentity", new StakeholderTableInfo("legal_x_objectxpeople", "Legal_ID", "Object_x_ip"));
        STAKEHOLDER_TABLE_MAP.put("regulation", new StakeholderTableInfo("regulation_x_objectxpeople", "RegulationID", "Object_x_ipid"));
        STAKEHOLDER_TABLE_MAP.put("interface", new StakeholderTableInfo("interface_x_objectxpeople", "InterfaceID", "Object_x_ipid"));
    }

    /**
     * Check if an object has an active change request (active_cr_id is not null)
     * This indicates the object is under workflow
     */
    public boolean hasActiveChangeRequest(Connection conn, String tableName, int objectId) throws SQLException {
        // Check if table has active_cr_id column
        String idColumn = getTableIdColumn(tableName);
        String sql = "SELECT active_cr_id FROM `" + tableName + "` WHERE " + idColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("active_cr_id") != null;
                }
            }
        } catch (SQLException e) {
            // Column might not exist - return false
            if (e.getMessage().contains("Unknown column")) {
                return false;
            }
            throw e;
        }
        return false;
    }

    /**
     * Check if an object has linked stakeholders
     */
    public boolean hasLinkedStakeholders(Connection conn, String facet, int objectId) throws SQLException {
        String normalizedFacet = normalizeFacet(facet);
        StakeholderTableInfo info = STAKEHOLDER_TABLE_MAP.get(normalizedFacet);
        
        if (info == null) {
            // Facet doesn't have stakeholder table - return false
            return false;
        }

        String sql = "SELECT COUNT(*) FROM `" + info.tableName + "` WHERE `" + info.idColumn + "` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - return false
            if (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table")) {
                return false;
            }
            throw e;
        }
        return false;
    }

    /**
     * Get object name for display
     */
    public String getObjectName(Connection conn, String facet, int objectId) throws SQLException {
        String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
        if (tableName == null) {
            return null;
        }

        String nameColumn = getNameColumn(tableName);
        String idColumn = getTableIdColumn(tableName);
        
        String sql = "SELECT " + nameColumn + " AS name FROM `" + tableName + "` WHERE " + idColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        }
        return null;
    }

    /**
     * Update object status to "Deleted" only (no Deleted_datetime).
     * Used for Admin/WebUser who can only mark as Deleted; SuperAdmin performs final soft delete.
     * Returns false if facet has no status column (e.g. geography, regulator).
     */
    public boolean updateStatusToDeleted(Connection conn, String facet, int objectId) throws SQLException {
        String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
        if (tableName == null) {
            throw new SQLException("Unknown facet: " + facet);
        }
        String statusColumn = getStatusColumnForFacet(facet, tableName);
        if (statusColumn == null) {
            return false; // Facet has no status (e.g. geography, regulator)
        }
        String statusTable = getStatusTableForFacet(tableName);
        String idColumn = getTableIdColumn(tableName);
        // Resolve "Deleted" status id from lookup table
        String idSql = "SELECT id FROM `" + statusTable + "` WHERE LOWER(TRIM(primaryname)) = 'deleted' LIMIT 1";
        Integer deletedStatusId = null;
        try (PreparedStatement ps = conn.prepareStatement(idSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                deletedStatusId = rs.getInt("id");
            }
        }
        if (deletedStatusId == null) {
            throw new SQLException("Deleted status not found in " + statusTable);
        }
        String updateSql = "UPDATE `" + tableName + "` SET `" + statusColumn + "` = ? WHERE `" + idColumn + "` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setInt(1, deletedStatusId);
            stmt.setInt(2, objectId);
            int affected = stmt.executeUpdate();
            return affected > 0;
        }
    }

    /**
     * Whether the facet has a status column (Admin can only set status to Deleted for such facets).
     */
    public boolean facetHasStatus(String facet) {
        String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
        if (tableName == null) return false;
        return getStatusColumnForFacet(facet, tableName) != null;
    }

    private String getStatusColumnForFacet(String facet, String tableName) {
        String normalizedFacet = normalizeFacet(facet);
        if ("geography".equals(normalizedFacet) || "regulator".equals(normalizedFacet)) {
            return null;
        }
        if ("regulation".equals(normalizedFacet)) return "RegulationStatus_ID";
        if ("regulatorytheme".equals(normalizedFacet) || "regulatory-theme".equals(normalizedFacet)) return "Status_ID";
        if ("interface".equals(normalizedFacet) || "people".equals(normalizedFacet) || "org-unit".equals(normalizedFacet) || "orgunit".equals(normalizedFacet)) {
            return "status_id";
        }
        if ("attribute".equals(normalizedFacet)) return null; // attribute has no status in config
        if ("data-quality-rule".equals(normalizedFacet) || "dataquality".equals(normalizedFacet) || "data_quality".equals(normalizedFacet)) return "status";
        return getStatusColumn(tableName);
    }

    private String getStatusTableForFacet(String tableName) {
        return getStatusTableName(tableName);
    }

    /**
     * Perform soft delete by setting deletedatetime column to NOW()
     * Uses the same pattern as GenericDeletionServlet
     */
    public boolean performSoftDelete(Connection conn, String facet, int objectId) throws SQLException {
        String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
        if (tableName == null) {
            throw new SQLException("Unknown facet: " + facet);
        }

        // Get delete column name based on facet
        String deleteColumn = getDeleteColumn(facet, tableName);
        String idColumn = getTableIdColumn(tableName);

        // Wrap column names in backticks to handle spaces and special characters
        String deleteColumnQuoted = deleteColumn.contains(" ") ? "`" + deleteColumn + "`" : deleteColumn;
        String idColumnQuoted = idColumn.contains(" ") ? "`" + idColumn + "`" : idColumn;
        
        String sql = "UPDATE `" + tableName + "` SET " + deleteColumnQuoted + " = NOW() WHERE " + idColumnQuoted + " = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Get delete column name for a facet
     * Based on actual database schema from lite_clean.sql
     */
    private String getDeleteColumn(String facet, String tableName) {
        // Map facet to delete column name (matching actual database schema)
        String normalizedFacet = normalizeFacet(facet);
        switch (normalizedFacet) {
            case "system":
                return "Deleted_datetime";  // system table uses Deleted_datetime (with underscore)
            case "dataset":
                return "DeletedDatetime";
            case "business-area":
            case "businessarea":
                return "deletedatetime";  // business_area table uses deletedatetime (lowercase)
            case "capability":
            case "client":
            case "committee":
            case "policy":
            case "regulation":
            case "regulator":
            case "regulatory-theme":
            case "regulatorytheme":
                return "DeletedDatetime";
            case "glossary":
                return "Deleted_datetime";  // glossary table uses Deleted_datetime (with underscore)
            case "process":
                return "deleteddatetime";  // process table uses deleteddatetime (lowercase)
            case "product":
                return "deleteddatetime";  // product table uses deleteddatetime (lowercase)
            case "legal-entity":
            case "legalentity":
                return "DeleteDatetime";
            case "org-unit":
            case "orgunit":
                return "deleted_Date";
            case "people":
                return "Deleted_date";
            case "project":
                return "deletedatetime";
            case "interface":
                return "deleted_datetime";
            case "data-quality-rule":
            case "dataquality":
            case "data-quality":
            case "data_quality":
                return "DeletedDatetime";
            default:
                // Try common patterns
                return "DeletedDatetime";
        }
    }

    /**
     * Get name column for a table
     * Based on actual database schema from lite_clean.sql
     */
    private String getNameColumn(String tableName) {
        if (tableName.equals("people")) {
            return "CONCAT(First_Name, ' ', Last_Name)";
        } else if (tableName.equals("interface") || tableName.equals("system_interface")) {
            return "Name";
        } else if (tableName.equals("system")) {
            return "Name";
        } else if (tableName.equals("legal") || tableName.equals("legal_entity")) {
            return "ShortName";
        } else if (tableName.equals("glossary")) {
            return "Name";  // glossary table uses Name (not PrimaryName)
        } else if (tableName.equals("org_unit")) {
            return "Name";  // org_unit table uses Name (not PrimaryName)
        }
        return "PrimaryName";
    }

    /**
     * Get ID column for a table
     * Based on actual database schema from lite_clean.sql
     * Tables using "ID" (uppercase): attribute, business_area, capability, client, committee, 
     * dataset, glossary, legal, org_unit, people, policy, regulation
     * Tables using "id" (lowercase): interface, process, product, project, system
     */
    private String getTableIdColumn(String tableName) {
        // Tables that use "ID" (uppercase)
        if (tableName.equals("attribute") || tableName.equals("business_area") 
                || tableName.equals("capability") || tableName.equals("client") 
                || tableName.equals("committee") || tableName.equals("dataset")
                || tableName.equals("data_quality") || tableName.equals("glossary") || tableName.equals("legal")
                || tableName.equals("org_unit") || tableName.equals("people")
                || tableName.equals("policy") || tableName.equals("regulation")) {
            return "ID";
        }
        // Tables that use "id" (lowercase): interface, process, product, project, system
        return "id";
    }

    /**
     * Normalize facet name
     */
    private String normalizeFacet(String facet) {
        if (facet == null) return "";
        return facet.toLowerCase().trim().replace("_", "-");
    }

    /**
     * Check if object's BUDG Status is "Deleted"
     * Returns true if status is "Deleted", false otherwise
     * Exception: Geography and Regulator - always return true (skip check)
     */
    public boolean checkBudgStatus(Connection conn, String facet, String tableName, int objectId) throws SQLException {
        String normalizedFacet = normalizeFacet(facet);
        
        // Exception: Geography and Regulator don't have status
        if ("geography".equals(normalizedFacet) || "regulator".equals(normalizedFacet)) {
            return true;
        }
        
        // Determine status column name
        String statusColumn = getStatusColumn(tableName);
        if (statusColumn == null) {
            // Table doesn't have status - assume OK
            return true;
        }
        
        String idColumn = getTableIdColumn(tableName);
        String statusTable = getStatusTableName(tableName);
        
        String sql = "SELECT s.primaryname " +
                     "FROM `" + tableName + "` t " +
                     "LEFT JOIN `" + statusTable + "` s ON t.`" + statusColumn + "` = s.id " +
                     "WHERE t.`" + idColumn + "` = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String statusName = rs.getString("primaryname");
                    return "Deleted".equalsIgnoreCase(statusName);
                }
            }
        }
        return false;
    }
    
    /**
     * Check if object has any Impact tab records (blocking)
     * Uses EXISTS pattern for efficiency
     */
    public boolean checkImpactRecords(Connection conn, String facet, int objectId) throws SQLException {
        String normalizedFacet = normalizeFacet(facet);
        String[] relationshipTables = getImpactRelationshipTables(normalizedFacet);
        
        if (relationshipTables.length == 0) {
            return false; // No impact tables for this facet
        }
        
        // Check each table individually - if any has records, return true
        for (String table : relationshipTables) {
            try {
                String foreignKeyColumn = getImpactForeignKeyColumn(normalizedFacet, table);
                String sql = "SELECT 1 WHERE EXISTS (SELECT 1 FROM `" + table + "` WHERE `" + foreignKeyColumn + "` = ?)";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, objectId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return true; // Found impact record
                        }
                    }
                }
            } catch (SQLException e) {
                // Table might not exist - continue to next table
                if (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table")) {
                    continue;
                }
                throw e;
            }
        }
        
        return false; // No impact records found
    }
    
    /**
     * Check if object has child objects
     * Returns count of children
     */
    public int checkChildObjects(Connection conn, String facet, String tableName, int objectId) throws SQLException {
        String parentColumn = getParentColumn(facet, tableName);
        if (parentColumn == null) {
            return 0; // Facet doesn't support hierarchy
        }
        
        String sql = "SELECT COUNT(*) FROM `" + tableName + "` WHERE `" + parentColumn + "` = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    /**
     * Check System's datasets and their statuses
     * Returns object with:
     * - hasNonDeletedDatasets: true if any dataset has status != "Deleted"
     * - hasDatasets: true if any datasets exist (even if deleted)
     * - datasetCount: total count of datasets
     */
    public SystemDatasetCheckResult checkSystemDatasets(Connection conn, int systemId) throws SQLException {
        SystemDatasetCheckResult result = new SystemDatasetCheckResult();
        
        String sql = "SELECT d.ID, s.primaryname as status " +
                     "FROM dataset d " +
                     "LEFT JOIN status s ON d.status = s.id " +
                     "WHERE d.MasterSource = ? AND d.DeletedDatetime IS NULL";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, systemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.datasetCount++;
                    result.hasDatasets = true;
                    String status = rs.getString("status");
                    if (status == null || !"Deleted".equalsIgnoreCase(status)) {
                        result.hasNonDeletedDatasets = true;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Check Geography links to regulators/regulations (blocking)
     * Uses EXISTS pattern
     */
    public boolean checkGeographyLinks(Connection conn, int geographyId) throws SQLException {
        String sql = "SELECT 1 " +
                     "WHERE EXISTS (SELECT 1 FROM regulator_x_geography WHERE Geography_ID = ?) " +
                     "   OR EXISTS (SELECT 1 FROM legal_x_geography WHERE Geography_ID = ?) " +
                     "   OR EXISTS (" +
                     "       SELECT 1 FROM regulation_x_regulator_x_geography rxrxg " +
                     "       JOIN regulator_x_geography rxg ON rxrxg.Regulator_X_Geography_ID = rxg.ID " +
                     "       WHERE rxg.Geography_ID = ?" +
                     "   )";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, geographyId);
            stmt.setInt(2, geographyId);
            stmt.setInt(3, geographyId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            // Table might not exist - return false
            if (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table")) {
                return false;
            }
            throw e;
        }
    }
    
    /**
     * Remove stakeholders for an object
     */
    public void removeStakeholders(Connection conn, String facet, int objectId) throws SQLException {
        String normalizedFacet = normalizeFacet(facet);
        StakeholderTableInfo info = STAKEHOLDER_TABLE_MAP.get(normalizedFacet);
        
        if (info == null) {
            return; // Facet doesn't have stakeholder table
        }
        
        String sql = "DELETE FROM `" + info.tableName + "` WHERE `" + info.idColumn + "` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Unlink child objects by setting parent_id = NULL
     */
    public void unlinkChildObjects(Connection conn, String facet, String tableName, int objectId) throws SQLException {
        String parentColumn = getParentColumn(facet, tableName);
        if (parentColumn == null) {
            return; // Facet doesn't support hierarchy
        }
        
        String sql = "UPDATE `" + tableName + "` SET `" + parentColumn + "` = NULL WHERE `" + parentColumn + "` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Delete datasets for a system (soft delete)
     */
    public void deleteSystemDatasets(Connection conn, int systemId) throws SQLException {
        String sql = "UPDATE dataset SET DeletedDatetime = NOW() WHERE MasterSource = ? AND DeletedDatetime IS NULL";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, systemId);
            stmt.executeUpdate();
        }
    }
    
    // Helper methods
    
    private String getStatusColumn(String tableName) {
        // Most tables use "status" but some have variations
        if (tableName.equals("regulation")) {
            return "RegulationStatus_ID";
        }
        return "status";
    }
    
    private String getStatusTableName(String tableName) {
        if (tableName.equals("regulation")) {
            return "regulation_status";
        }
        return "status";
    }
    
    private String getParentColumn(String facet, String tableName) {
        // Process table uses "parentid" (lowercase, no underscore), not Parent_ID or parent_id
        if (tableName != null && tableName.equalsIgnoreCase("process")) {
            return "parentid";
        }
        String normalizedFacet = normalizeFacet(facet);
        switch (normalizedFacet) {
            case "glossary":
                return "Parent_ID";
            case "system":
                return "parent_id";
            case "policy":
                return "ParentID";
            case "project":
                return "parentid";
            case "process":
                return "parentid";
            case "product":
                return "parent_id";
            case "legal-entity":
            case "legalentity":
                return "Parent_ID";
            case "capability":
                return "Parent_ID";
            case "client":
                return "Parent_ID";
            case "committee":
                return "Parent_ID";
            case "regulation":
                return "Parent_ID";
            case "business-area":
            case "businessarea":
                return "Parent_ID";
            case "org-unit":
            case "orgunit":
                return "Parent_ID";
            case "geography":
                return "Parent_ID";
            default:
                return null;
        }
    }
    
    private String[] getImpactRelationshipTables(String facet) {
        switch (facet) {
            case "system":
                return new String[]{
                    "process_x_system", "project_x_system", "policy_x_system",
                    "product_x_system", "client_x_system", "capability_x_system",
                    "businessarea_x_system"
                };
            case "dataset":
                return new String[]{
                    "process_x_dataset", "project_x_dataset", "policy_x_dataset",
                    "dataset_x_legal"
                };
            case "glossary":
                return new String[]{
                    "glossary_x_process", "glossary_x_project", "glossary_x_system",
                    "glossary_x_dataset", "glossary_x_attribute"
                };
            case "process":
                return new String[]{
                    "process_x_system", "process_x_product", "process_x_client",
                    "process_x_project", "process_x_policy", "process_x_interface",
                    "process_x_legal", "process_x_dataset"
                };
            case "project":
                return new String[]{
                    "project_x_system", "project_x_process", "project_x_product",
                    "project_x_client", "project_x_policy", "project_x_capability",
                    "project_x_businessarea"
                };
            case "policy":
                return new String[]{
                    "policy_x_system", "policy_x_process", "policy_x_project",
                    "policy_x_dataset"
                };
            case "product":
                return new String[]{
                    "product_x_system", "product_x_process", "product_x_project"
                };
            case "client":
                return new String[]{
                    "client_x_system", "client_x_process", "client_x_project"
                };
            case "capability":
                return new String[]{
                    "capability_x_system", "project_x_capability"
                };
            case "business-area":
            case "businessarea":
                return new String[]{
                    "businessarea_x_system", "businessarea_x_process", "businessarea_x_glossary"
                };
            default:
                return new String[]{};
        }
    }
    
    private String getImpactForeignKeyColumn(String facet, String tableName) {
        // Map table names to their foreign key columns
        if (tableName.contains("_x_system")) {
            if (tableName.equals("process_x_system")) return "system_id";
            if (tableName.equals("project_x_system")) return "systemid";
            if (tableName.equals("policy_x_system")) return "System_ID";
            if (tableName.equals("product_x_system")) return "systemid";
            if (tableName.equals("client_x_system")) return "System_ID";
            if (tableName.equals("capability_x_system")) return "System_ID";
            if (tableName.equals("businessarea_x_system")) return "System_ID";
        }
        if (tableName.contains("_x_dataset")) {
            if (tableName.equals("process_x_dataset")) return "datasetid";
            if (tableName.equals("project_x_dataset")) return "dataset_id";
            if (tableName.equals("policy_x_dataset")) return "DatasetID";
            if (tableName.equals("dataset_x_legal")) return "Dataset_ID";
        }
        if (tableName.contains("_x_process")) {
            if (tableName.equals("process_x_system")) return "process_id";
            if (tableName.equals("project_x_process")) return "process_id";
            if (tableName.equals("policy_x_process")) return "process_id";
            if (tableName.equals("glossary_x_process")) return "Process_ID";
        }
        if (tableName.contains("_x_project")) {
            if (tableName.equals("project_x_system")) return "projectid";
            if (tableName.equals("project_x_process")) return "projectid";
            if (tableName.equals("project_x_product")) return "projectid";
            if (tableName.equals("project_x_client")) return "projectid";
            if (tableName.equals("project_x_policy")) return "projectid";
            if (tableName.equals("project_x_capability")) return "Project_ID";
            if (tableName.equals("project_x_businessarea")) return "Project_ID";
            if (tableName.equals("glossary_x_project")) return "Project_ID";
        }
        if (tableName.contains("_x_policy")) {
            if (tableName.equals("policy_x_system")) return "Policy_ID";
            if (tableName.equals("policy_x_process")) return "policy_id";
            if (tableName.equals("policy_x_project")) return "policy_id";
            if (tableName.equals("policy_x_dataset")) return "PolicyID";
        }
        if (tableName.contains("_x_product")) {
            if (tableName.equals("product_x_system")) return "productid";
            if (tableName.equals("product_x_process")) return "productid";
            if (tableName.equals("product_x_project")) return "productid";
        }
        if (tableName.contains("_x_client")) {
            if (tableName.equals("client_x_system")) return "Client_ID";
            if (tableName.equals("client_x_process")) return "Client_ID";
            if (tableName.equals("client_x_project")) return "Client_ID";
        }
        if (tableName.contains("_x_capability")) {
            if (tableName.equals("capability_x_system")) return "Capability_ID";
            if (tableName.equals("project_x_capability")) return "Capability_ID";
        }
        if (tableName.contains("_x_businessarea") || tableName.contains("businessarea_x_")) {
            // For business-area relationships, always use BusinessArea_ID (no underscore between Business and Area)
            if (tableName.equals("businessarea_x_system")) return "BusinessArea_ID";
            if (tableName.equals("businessarea_x_process")) return "BusinessArea_ID";
            if (tableName.equals("businessarea_x_glossary")) return "BusinessArea_ID";
            if (tableName.equals("project_x_businessarea")) return "BusinessArea_ID";
        }
        if (tableName.contains("_x_glossary")) {
            if (tableName.equals("glossary_x_process")) return "Glossary_ID";
            if (tableName.equals("glossary_x_project")) return "Glossary_ID";
            if (tableName.equals("glossary_x_system")) return "Glossary_ID";
            if (tableName.equals("glossary_x_dataset")) return "Glossary_ID";
            if (tableName.equals("glossary_x_attribute")) return "Glossary_ID";
        }
        if (tableName.contains("_x_system") && facet.equals("system")) {
            // For system facet, check which column references the system
            return "systemid";
        }
        
        // Default pattern: try to infer from facet name
        String baseName = facet.replace("-", "_");
        // Special handling for business_area - remove underscore between Business and Area
        if (baseName.equals("business_area") || baseName.equals("businessarea")) {
            return "BusinessArea_ID";
        }
        String capitalized = baseName.substring(0, 1).toUpperCase() + baseName.substring(1);
        // Handle camelCase conversion for multi-word facets (e.g., legal_entity -> LegalEntity_ID)
        if (baseName.contains("_")) {
            String[] parts = baseName.split("_");
            StringBuilder camelCase = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    camelCase.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
                }
            }
            return camelCase.toString() + "_ID";
        }
        return capitalized + "_ID";
    }
    
    /**
     * Result class for System Dataset check
     */
    public static class SystemDatasetCheckResult {
        public boolean hasNonDeletedDatasets = false;
        public boolean hasDatasets = false;
        public int datasetCount = 0;
    }

    /**
     * Inner class to hold stakeholder table information
     */
    @SuppressWarnings("unused")
    private static class StakeholderTableInfo {
        final String tableName;
        final String idColumn;
        final String objectXIpidColumn;

        StakeholderTableInfo(String tableName, String idColumn, String objectXIpidColumn) {
            this.tableName = tableName;
            this.idColumn = idColumn;
            this.objectXIpidColumn = objectXIpidColumn;
        }
    }
}

