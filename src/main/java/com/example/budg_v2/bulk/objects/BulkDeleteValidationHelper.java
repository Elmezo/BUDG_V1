package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for validating object deletion in bulk upload operations
 * Reuses validation logic from DeletionValidationServlet
 */
public class BulkDeleteValidationHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(BulkDeleteValidationHelper.class);
    @SuppressWarnings("unused")
    private static final Gson gson = new Gson();
    
    // Facet configuration for deletion validation
    private static final Map<String, FacetConfig> FACET_CONFIGS = new HashMap<>();
    
    static {
        // System configuration
        FACET_CONFIGS.put("system", new FacetConfig(
            "system", "Deleted_datetime", "status", "parent_id", true,
            "system_x_objectxpeople", "system_x_dataset", new String[]{"datasets"}
        ));
        
        // Dataset configuration
        FACET_CONFIGS.put("dataset", new FacetConfig(
            "dataset", "DeletedDatetime", "status", null, true,
            "dataset_x_objectxpeople", null, new String[]{"dataset-relationships", "dataset-content-summary", "dataset-attributes"}
        ));
        
        // Business Area configuration
        FACET_CONFIGS.put("business-area", new FacetConfig(
            "business_area", "deletedatetime", "status", "Parent_ID", true,
            "businessarea_x_objectxpeople", null, null
        ));
        
        // Capability configuration
        FACET_CONFIGS.put("capability", new FacetConfig(
            "capability", "DeletedDatetime", "status", "Parent_ID", true,
            "capability_x_objectxpeople", null, new String[]{"capability-relationships"}
        ));
        
        // Client configuration
        FACET_CONFIGS.put("client", new FacetConfig(
            "client", "DeleteDatetime", "status", "Parent_ID", true,
            "client_x_objectxpeople", null, null
        ));
        
        // Committee configuration
        FACET_CONFIGS.put("committee", new FacetConfig(
            "committee", "DeleteDatetime", "status", "Parent_ID", true,
            "committee_x_objectxpeople", null, new String[]{"committee-relationships"}
        ));
        
        // Geography configuration (no status check)
        FACET_CONFIGS.put("geography", new FacetConfig(
            "geography", "DeletedDatetime", null, "ParentID", false,
            null, null, new String[]{"geography-regulator-links", "geography-legal-entity-links", "geography-regulation-links"}
        ));
        
        // Regulator configuration (no status check)
        FACET_CONFIGS.put("regulator", new FacetConfig(
            "regulator", "DeletedDatetime", null, null, false,
            null, null, new String[]{"regulator-regulation-links", "regulator-geography-links"}
        ));
        
        // Glossary configuration
        FACET_CONFIGS.put("glossary", new FacetConfig(
            "glossary", "Deleted_datetime", "Status", "Parent_ID", true,
            "glossary_x_objectxpeople", null, new String[]{"glossary-alias-names", "glossary-system-links", "glossary-dataset-links", "glossary-attribute-links", "glossary-dq-rule-links", "glossary-relationships"}
        ));
        
        // Policy configuration
        FACET_CONFIGS.put("policy", new FacetConfig(
            "policy", "DeletedDatetime", "status", "ParentID", true,
            "policy_x_objectxpeople", null, new String[]{"policy-relationships"}
        ));
        
        // Process configuration
        FACET_CONFIGS.put("process", new FacetConfig(
            "process", "deleteddatetime", "status", "parentid", true,
            "process_x_objectxpeople", null, new String[]{"process-relationships"}
        ));
        
        // Product configuration
        FACET_CONFIGS.put("product", new FacetConfig(
            "product", "deleteddatetime", "status", "parent_id", true,
            "product_x_objectxpeople", null, null
        ));
        
        // Project configuration
        FACET_CONFIGS.put("project", new FacetConfig(
            "project", "deletedatetime", "status", "parentid", true,
            "project_x_objectxpeople", null, new String[]{"project-relationships"}
        ));
        
        // Regulation configuration
        FACET_CONFIGS.put("regulation", new FacetConfig(
            "regulation", "DeletedDatetime", "RegulationStatus_ID", "Parent_ID", true,
            "regulation_x_objectxpeople", null, new String[]{"regulation-relationships", "regulation-regulator-links"}
        ));
        
        // Regulatory Theme configuration
        FACET_CONFIGS.put("regulatory-theme", new FacetConfig(
            "regulatorytheme", "DeletedDatetime", "Status_ID", "Parent_ID", true,
            null, null, new String[]{"regulatory-theme-regulations"}
        ));
        
        // Legal Entity configuration
        FACET_CONFIGS.put("legal-entity", new FacetConfig(
            "legal", "DeleteDatetime", "Status", "Parent_ID", true,
            "legal_x_objectxpeople", null, null
        ));
        
        // Org Unit configuration
        FACET_CONFIGS.put("org-unit", new FacetConfig(
            "org_unit", "deleted_Date", "status_id", "Parent_ID", true,
            null, null, null
        ));
        
        // People configuration
        FACET_CONFIGS.put("people", new FacetConfig(
            "people", "Deleted_date", "status_id", "org_unit_id", true,
            null, null, new String[]{"people-active-crs", "people-managers", "people-dq-rules", "people-stakeholder-links"}
        ));
        
        // System Interface configuration
        FACET_CONFIGS.put("system-interface", new FacetConfig(
            "interface", "deleted_datetime", "status_id", null, true,
            "interface_x_objectxpeople", null, new String[]{"interface-glossary-links"}
        ));
        FACET_CONFIGS.put("interface", new FacetConfig(
            "interface", "deleted_datetime", "status_id", null, true,
            "interface_x_objectxpeople", null, new String[]{"interface-glossary-links"}
        ));
        
        // Attribute configuration
        FACET_CONFIGS.put("attribute", new FacetConfig(
            "attribute", "DeletedDatetime", null, "Dataset_ID", false,
            null, null, new String[]{"attribute-relationships"}
        ));

        // Data Quality Rule configuration (BUDG: no Attributes linked, no Technical Rule Reference; can be linked to System)
        FACET_CONFIGS.put("data-quality-rule", new FacetConfig(
            "data_quality", "DeletedDatetime", "status", null, true,
            null, null, new String[]{"dq-rule-attribute-links", "dq-rule-technical-reference"}
        ));
        FACET_CONFIGS.put("dataquality", new FacetConfig(
            "data_quality", "DeletedDatetime", "status", null, true,
            null, null, new String[]{"dq-rule-attribute-links", "dq-rule-technical-reference"}
        ));
        FACET_CONFIGS.put("data_quality", new FacetConfig(
            "data_quality", "DeletedDatetime", "status", null, true,
            null, null, new String[]{"dq-rule-attribute-links", "dq-rule-technical-reference"}
        ));
    }
    
    /**
     * Result class for deletion validation
     */
    public static class ValidationResult {
        private boolean canDelete;
        private List<String> errors;
        private List<String> warnings;
        private String objectName;
        
        public ValidationResult() {
            this.canDelete = true;
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }
        
        public boolean canDelete() {
            return canDelete;
        }
        
        public void setCanDelete(boolean canDelete) {
            this.canDelete = canDelete;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public void addError(String error) {
            this.errors.add(error);
            this.canDelete = false;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
        
        public String getObjectName() {
            return objectName;
        }
        
        public void setObjectName(String objectName) {
            this.objectName = objectName;
        }
        
        public boolean hasBlockingErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
    
    /**
     * Validate if an object can be deleted (with default: require status = Deleted for final delete).
     * @param facetType The facet type (e.g., "system", "interface", "product")
     * @param objectId The object ID to validate
     * @return ValidationResult with canDelete flag, errors, and warnings
     */
    public ValidationResult validateObjectForDeletion(String facetType, int objectId) {
        return validateObjectForDeletion(facetType, objectId, true);
    }

    /**
     * Validate if an object can be deleted
     * @param facetType The facet type
     * @param objectId The object ID to validate
     * @param requireDeletedStatus If true, object must already have status "Deleted" (for final delete). If false, any status is allowed (for Admin marking as Deleted).
     * @return ValidationResult with canDelete flag, errors, and warnings
     */
    public ValidationResult validateObjectForDeletion(String facetType, int objectId, boolean requireDeletedStatus) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return validateObjectForDeletion(conn, facetType, objectId, requireDeletedStatus);
        } catch (SQLException e) {
            logger.error("Error getting connection for deletion validation: {}", e.getMessage(), e);
            ValidationResult result = new ValidationResult();
            result.addError("Database error during validation: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Validate object can be deleted (with existing connection, requires status=Deleted).
     */
    public ValidationResult validateObjectForDeletion(Connection conn, String facetType, int objectId) {
        return validateObjectForDeletion(conn, facetType, objectId, true);
    }

    /**
     * Validate object can be deleted (with existing connection)
     * @param requireDeletedStatus When true (final delete by SuperAdmin), object must have status "Deleted". When false (Admin marking as Deleted), status is not required to be Deleted.
     */
    public ValidationResult validateObjectForDeletion(Connection conn, String facetType, int objectId, boolean requireDeletedStatus) {
        ValidationResult result = new ValidationResult();
        
        try {
            String normalizedFacet = normalizeFacet(facetType);
            FacetConfig config = FACET_CONFIGS.get(normalizedFacet);
            
            if (config == null) {
                result.addError("Unsupported object type: " + facetType);
                return result;
            }
            
            // Check if object exists and get current data
            JsonObject objectData = getObjectData(conn, config, objectId);
            if (objectData == null) {
                String entityName = getEntityDisplayName(normalizedFacet);
                result.addError(entityName + " not found or has already been removed.");
                return result;
            }
            
            // Get object name for display
            String objectName = getObjectName(conn, config, objectId);
            if (objectName != null) {
                result.setObjectName(objectName);
            }
            
            // Status validation (if facet has status and we require Deleted for final delete)
            if (config.hasStatus && requireDeletedStatus) {
                String statusValidation = validateStatus(conn, config, objectData);
                if (statusValidation != null) {
                    result.addError(statusValidation);
                }
            }
            
            // Check for stakeholders (warning)
            int stakeholderCount = checkStakeholders(conn, config, objectId);
            if (stakeholderCount > 0) {
                String entityName = getEntityDisplayName(normalizedFacet);
                result.addWarning(entityName + " has " + stakeholderCount + " linked stakeholder" + 
                    (stakeholderCount > 1 ? "s" : "") + ". These Stakeholders will be removed if you proceed.");
            }
            
            // Check for impact relationships (blocking)
            int impactCount = checkImpactRelationships(conn, normalizedFacet, objectId);
            if (impactCount > 0) {
                String entityName = getEntityDisplayName(normalizedFacet);
                result.addError(entityName + " has " + impactCount + " active relationship" + 
                    (impactCount > 1 ? "s" : "") + " in the Impact tab. Please remove these relationships before deletion.");
            }
            
            // Check for child objects (blocking)
            int childCount = checkChildObjects(conn, config, objectId);
            if (childCount > 0) {
                String entityName = getEntityDisplayName(normalizedFacet);
                result.addError(entityName + " contains " + childCount + " child object" +
                    (childCount > 1 ? "s" : "") + ". Remove or reassign children before deletion.");
            }
            
            // Special checks
            if (config.specialChecks != null) {
                for (String checkType : config.specialChecks) {
                    JsonObject specialCheck = performSpecialCheck(conn, normalizedFacet, objectId, checkType);
                    if (specialCheck != null) {
                        if (specialCheck.get("blocking").getAsBoolean()) {
                            result.addError(specialCheck.get("message").getAsString());
                        } else {
                            result.addWarning(specialCheck.get("message").getAsString());
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error validating deletion for {} ID {}: {}", facetType, objectId, e.getMessage(), e);
            result.addError("Database error during validation: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Normalize facet name to match config keys
     */
    private String normalizeFacet(String facet) {
        if (facet == null) return "";
        String normalized = facet.toLowerCase().trim().replace("_", "-");
        // Handle interface variations
        if (normalized.equals("interface") || normalized.equals("system-interface")) {
            return "interface";
        }
        // Handle attributes plural form
        if (normalized.equals("attributes")) {
            return "attribute";
        }
        return normalized;
    }
    
    /**
     * Get object data for validation
     */
    private JsonObject getObjectData(Connection conn, FacetConfig config, int objectId) throws SQLException {
        String sql = "SELECT * FROM " + config.tableName + " WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", rs.getInt("id"));
                    
                    if (config.statusColumn != null) {
                        int statusId = rs.getInt(config.statusColumn);
                        if (!rs.wasNull()) {
                            obj.addProperty("statusId", statusId);
                            String statusName = getStatusName(conn, statusId, config.tableName);
                            obj.addProperty("statusName", statusName);
                        }
                    }
                    
                    return obj;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get object name for display
     */
    private String getObjectName(Connection conn, FacetConfig config, int objectId) throws SQLException {
        // Try common name columns
        String[] nameColumns = {"PrimaryName", "primaryname", "Name", "name", "Interface Name", "interface_name"};
        
        for (String col : nameColumns) {
            try {
                String sql = "SELECT " + col + " FROM " + config.tableName + " WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, objectId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String name = rs.getString(col);
                            if (name != null && !name.trim().isEmpty()) {
                                return name;
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                // Column doesn't exist, try next
            }
        }
        
        return "ID: " + objectId;
    }
    
    /**
     * Get status name by ID
     */
    private String getStatusName(Connection conn, int statusId, String tableName) throws SQLException {
        String statusTable;
        if ("regulation".equals(tableName)) {
            statusTable = "regulation_status";
        } else if ("regulatorytheme".equals(tableName)) {
            statusTable = "status";
        } else {
            statusTable = "status";
        }
        
        String sql = "SELECT primaryname FROM " + statusTable + " WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statusId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        
        return "Unknown";
    }
    
    /**
     * Validate object status
     */
    private String validateStatus(Connection conn, FacetConfig config, JsonObject objectData) {
        if (!objectData.has("statusName")) {
            return "Unable to determine object status. Please try again.";
        }
        
        String statusName = objectData.get("statusName").getAsString();
        if (!"Deleted".equalsIgnoreCase(statusName)) {
            return "BUDG Status must be set to \"Deleted\" before this object can be removed. Current status: " + statusName + ". Please update the status first.";
        }
        
        return null;
    }
    
    /**
     * Check for stakeholders
     */
    private int checkStakeholders(Connection conn, FacetConfig config, int objectId) throws SQLException {
        if (config.stakeholderTable == null) {
            return 0;
        }
        
        String fkColumn = getStakeholderForeignKeyColumn(config.tableName);
        String sql = "SELECT COUNT(*) FROM " + config.stakeholderTable + " WHERE " + fkColumn + " = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.debug("Could not check stakeholders: {}", e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Get stakeholder foreign key column name
     */
    private String getStakeholderForeignKeyColumn(String tableName) {
        // Map table names to their foreign key columns in stakeholder tables
        Map<String, String> columnMap = new HashMap<>();
        columnMap.put("system", "SystemID");
        columnMap.put("dataset", "Dataset_ID");
        columnMap.put("business_area", "BusinessAreaID");  // businessarea_x_objectxpeople uses BusinessAreaID (no underscore)
        columnMap.put("capability", "CapabilityID");
        columnMap.put("client", "Client_ID");
        columnMap.put("committee", "Committee_ID");
        columnMap.put("glossary", "GlossaryID");
        columnMap.put("policy", "Policy_ID");
        columnMap.put("process", "Process_ID");
        columnMap.put("product", "Product_ID");
        columnMap.put("project", "Project_ID");
        columnMap.put("regulation", "Regulation_ID");
        columnMap.put("regulator", "Regulator_ID");
        columnMap.put("legal", "Legal_ID");
        columnMap.put("interface", "InterfaceID");
        
        return columnMap.getOrDefault(tableName.toLowerCase(), "ID");
    }
    
    /**
     * Helper class to hold impact table information
     */
    private static class ImpactTableInfo {
        String tableName;
        String foreignKeyColumn;
        
        ImpactTableInfo(String tableName, String foreignKeyColumn) {
            this.tableName = tableName;
            this.foreignKeyColumn = foreignKeyColumn;
        }
    }
    
    /**
     * Check for impact relationships
     */
    private int checkImpactRelationships(Connection conn, String facetType, int objectId) throws SQLException {
        List<ImpactTableInfo> tables = getAllImpactTablesForFacet(facetType);
        int totalCount = 0;
        
        for (ImpactTableInfo tableInfo : tables) {
            int count = checkImpactTable(conn, tableInfo.tableName, tableInfo.foreignKeyColumn, objectId);
            totalCount += count;
        }
        
        return totalCount;
    }
    
    /**
     * Get ALL impact relationship tables for a facet (both source and target directions)
     * IMPORTANT: Only include relationships that are ACTUALLY SHOWN in the Impact tab for this facet.
     */
    private List<ImpactTableInfo> getAllImpactTablesForFacet(String facetType) {
        List<ImpactTableInfo> tables = new ArrayList<>();
        
        switch (facetType) {
            case "system":
                tables.add(new ImpactTableInfo("system_x_legal", "System_ID"));
                tables.add(new ImpactTableInfo("product_x_system", "System_ID"));
                tables.add(new ImpactTableInfo("client_x_system", "System_ID"));
                tables.add(new ImpactTableInfo("process_x_system", "system_id"));
                tables.add(new ImpactTableInfo("capability_x_system", "System_ID"));
                tables.add(new ImpactTableInfo("project_x_system", "systemid"));
                tables.add(new ImpactTableInfo("businessarea_x_system", "System_ID"));
                tables.add(new ImpactTableInfo("policy_x_system", "System_ID"));
                break;
                
            case "dataset":
                tables.add(new ImpactTableInfo("dataset_x_legal", "Dataset_ID"));
                tables.add(new ImpactTableInfo("product_x_dataset", "Dataset_ID"));
                tables.add(new ImpactTableInfo("client_x_dataset", "Dataset_ID"));
                tables.add(new ImpactTableInfo("policy_x_dataset", "DatasetID"));
                tables.add(new ImpactTableInfo("process_x_dataset", "datasetid"));
                tables.add(new ImpactTableInfo("project_x_dataset", "dataset_id"));
                break;
                
            case "glossary":
                tables.add(new ImpactTableInfo("glossary_x_process", "Glossary_ID"));
                tables.add(new ImpactTableInfo("glossary_x_project", "Glossary_ID"));
                tables.add(new ImpactTableInfo("product_x_glossary", "glossaryid"));
                tables.add(new ImpactTableInfo("client_x_glossary", "Glossary_ID"));
                tables.add(new ImpactTableInfo("businessarea_x_glossary", "Glossary_ID"));
                tables.add(new ImpactTableInfo("policy_x_glossary", "GlossaryID"));
                tables.add(new ImpactTableInfo("capability_x_glossary", "Glossary_ID"));
                break;
                
            case "process":
                tables.add(new ImpactTableInfo("process_x_system", "process_id"));
                tables.add(new ImpactTableInfo("process_x_interface", "process_id"));
                tables.add(new ImpactTableInfo("process_x_legal", "Process_ID"));
                tables.add(new ImpactTableInfo("process_x_dataset", "processid"));
                tables.add(new ImpactTableInfo("process_x_attribute", "processid"));
                tables.add(new ImpactTableInfo("product_x_process", "processid"));
                tables.add(new ImpactTableInfo("client_x_process", "Process_ID"));
                tables.add(new ImpactTableInfo("project_x_process", "process_id"));
                tables.add(new ImpactTableInfo("policy_x_process", "process_id"));
                tables.add(new ImpactTableInfo("glossary_x_process", "Process_ID"));
                tables.add(new ImpactTableInfo("businessarea_x_process", "Process_ID"));
                break;
                
            case "project":
                tables.add(new ImpactTableInfo("project_x_system", "projectid"));
                tables.add(new ImpactTableInfo("project_x_process", "projectid"));
                tables.add(new ImpactTableInfo("project_x_capability", "Project_ID"));
                tables.add(new ImpactTableInfo("project_x_businessarea", "Project_ID"));
                tables.add(new ImpactTableInfo("project_x_dataset", "projectid"));
                tables.add(new ImpactTableInfo("project_x_attribute", "projectid"));
                tables.add(new ImpactTableInfo("product_x_project", "projectid"));
                tables.add(new ImpactTableInfo("client_x_project", "Project_ID"));
                tables.add(new ImpactTableInfo("policy_x_project", "project_id"));
                tables.add(new ImpactTableInfo("glossary_x_project", "Project_ID"));
                tables.add(new ImpactTableInfo("regulation_x_project", "ProjectID"));
                break;
                
            case "policy":
                tables.add(new ImpactTableInfo("policy_x_system", "Policy_ID"));
                tables.add(new ImpactTableInfo("policy_x_process", "policy_id"));
                tables.add(new ImpactTableInfo("policy_x_project", "policy_id"));
                tables.add(new ImpactTableInfo("policy_x_dataset", "PolicyID"));
                tables.add(new ImpactTableInfo("product_x_policy", "policyid"));
                tables.add(new ImpactTableInfo("client_x_policy", "Policy_ID"));
                tables.add(new ImpactTableInfo("policy_x_businessarea", "Policy_ID"));
                tables.add(new ImpactTableInfo("policy_x_legal", "Policy_ID"));
                tables.add(new ImpactTableInfo("policy_x_attribute", "policyid"));
                tables.add(new ImpactTableInfo("policy_x_glossary", "PolicyID"));
                tables.add(new ImpactTableInfo("regulation_x_policy", "PolicyID"));
                break;
                
            case "product":
                tables.add(new ImpactTableInfo("product_x_legal", "Product_ID"));
                tables.add(new ImpactTableInfo("product_x_client", "Product_ID"));
                tables.add(new ImpactTableInfo("product_x_businessarea", "Product_ID"));
                tables.add(new ImpactTableInfo("product_x_system", "Product_ID"));
                tables.add(new ImpactTableInfo("product_x_project", "productid"));
                tables.add(new ImpactTableInfo("product_x_dataset", "Product_ID"));
                tables.add(new ImpactTableInfo("regulation_x_product", "ProductID"));
                tables.add(new ImpactTableInfo("capability_x_product", "Product_ID"));
                tables.add(new ImpactTableInfo("product_x_glossary", "productid"));
                tables.add(new ImpactTableInfo("product_x_process", "productid"));
                tables.add(new ImpactTableInfo("product_x_policy", "productid"));
                break;
                
            case "client":
                tables.add(new ImpactTableInfo("client_x_system", "Client_ID"));
                tables.add(new ImpactTableInfo("product_x_client", "Client_ID"));
                tables.add(new ImpactTableInfo("client_x_process", "Client_ID"));
                tables.add(new ImpactTableInfo("client_x_project", "Client_ID"));
                tables.add(new ImpactTableInfo("client_x_policy", "Client_ID"));
                tables.add(new ImpactTableInfo("capability_x_client", "Client_ID"));
                tables.add(new ImpactTableInfo("client_x_dataset", "Client_ID"));
                tables.add(new ImpactTableInfo("client_x_glossary", "Client_ID"));
                break;
                
            case "capability":
                tables.add(new ImpactTableInfo("capability_x_system", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_process", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_glossary", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_product", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_client", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_legal", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_businessarea", "Capability_ID"));
                tables.add(new ImpactTableInfo("project_x_capability", "Capability_ID"));
                tables.add(new ImpactTableInfo("committee_x_capability", "Capability_ID"));
                break;
                
            case "committee":
                tables.add(new ImpactTableInfo("committee_x_capability", "Committee_ID"));
                break;
                
            case "regulation":
                tables.add(new ImpactTableInfo("regulation_x_product", "RegulationID"));
                tables.add(new ImpactTableInfo("regulation_x_policy", "RegulationID"));
                tables.add(new ImpactTableInfo("regulation_x_project", "RegulationID"));
                tables.add(new ImpactTableInfo("regulation_x_regulatorytheme", "Regulation_ID"));
                break;
                
            case "business-area":
            case "businessarea":
                tables.add(new ImpactTableInfo("businessarea_x_system", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("businessarea_x_process", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("businessarea_x_glossary", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("product_x_businessarea", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("capability_x_businessarea", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("project_x_businessarea", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("policy_x_businessarea", "BusinessArea_ID"));
                break;
                
            case "interface":
            case "system-interface":
                tables.add(new ImpactTableInfo("process_x_interface", "interface_id"));
                break;
                
            case "legal":
            case "legal-entity":
                tables.add(new ImpactTableInfo("legal_x_geography", "Legal_ID"));
                tables.add(new ImpactTableInfo("system_x_legal", "Legal_ID"));
                tables.add(new ImpactTableInfo("dataset_x_legal", "Legal_ID"));
                tables.add(new ImpactTableInfo("policy_x_legal", "Legal_ID"));
                break;
                
            case "attribute":
                tables.add(new ImpactTableInfo("glossary_x_attribute", "Attribute_ID"));
                tables.add(new ImpactTableInfo("process_x_attribute", "attributeid"));
                break;
                
            case "geography":
                tables.add(new ImpactTableInfo("legal_x_geography", "Geography_ID"));
                break;
                
            case "regulatory-theme":
            case "regulatorytheme":
                tables.add(new ImpactTableInfo("regulation_x_regulatorytheme", "RegulatoryTheme_ID"));
                break;

            case "data-quality-rule":
            case "dataquality":
            case "data_quality":
                // Data Quality Rule: no Impact blocking per BUDG (can be linked to System); only attribute links and technical reference are checked
                break;
        }
        
        return tables;
    }
    
    /**
     * Check a single impact relationship table
     * Filters out deleted objects on the related side
     */
    private int checkImpactTable(Connection conn, String table, String foreignKeyColumn, int objectId) throws SQLException {
        try {
            String relatedTableInfo = getRelatedTableInfoForImpactCheck(table, foreignKeyColumn);
            
            String sql;
            if (relatedTableInfo != null) {
                String[] parts = relatedTableInfo.split("\\|");
                String relatedTable = parts[0];
                String relatedIdColumn = parts[1];
                String relatedDeleteColumn = parts[2];
                
                String relationshipRelatedIdColumn = getRelatedIdColumnInRelationshipTable(table, foreignKeyColumn);
                
                if (relationshipRelatedIdColumn != null) {
                    sql = "SELECT COUNT(*) FROM `" + table + "` r " +
                          "INNER JOIN `" + relatedTable + "` rt ON r.`" + relationshipRelatedIdColumn + "` = rt.`" + relatedIdColumn + "` " +
                          "WHERE r.`" + foreignKeyColumn + "` = ? " +
                          "AND r.`" + relationshipRelatedIdColumn + "` IS NOT NULL " +
                          "AND (rt.`" + relatedDeleteColumn + "` IS NULL OR rt.`" + relatedDeleteColumn + "` = '1970-01-01 00:00:00')";
                } else {
                    sql = "SELECT COUNT(*) FROM `" + table + "` WHERE `" + foreignKeyColumn + "` = ?";
                }
            } else {
                sql = "SELECT COUNT(*) FROM `" + table + "` WHERE `" + foreignKeyColumn + "` = ?";
            }
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, objectId);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                        return rs.getInt(1);
                        }
                    }
                }
            } catch (SQLException e) {
            if (e.getMessage() != null && 
                (e.getMessage().contains("doesn't exist") || 
                 e.getMessage().contains("Unknown table") ||
                 e.getMessage().contains("Unknown column"))) {
                logger.debug("Error checking impact table {} with column {}: {}", table, foreignKeyColumn, e.getMessage());
                return 0;
            }
            throw e;
        }
        return 0;
    }
    
    /**
     * Get related table info for impact check
     * Returns: "relatedTable|relatedIdColumn|relatedDeleteColumn" or null if not available
     */
    private String getRelatedTableInfoForImpactCheck(String table, String foreignKeyColumn) {
        // System impact tables (system is target)
        if (table.equals("product_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("client_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("process_x_system") && foreignKeyColumn.equals("system_id")) {
            return "process|id|deleteddatetime";
        }
        if (table.equals("capability_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "capability|ID|DeletedDatetime";
        }
        if (table.equals("project_x_system") && foreignKeyColumn.equals("systemid")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("businessarea_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "business_area|ID|deletedatetime";
        }
        if (table.equals("policy_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "policy|ID|DeletedDatetime";
        }
        if (table.equals("system_x_legal") && foreignKeyColumn.equals("System_ID")) {
            return "legal|ID|DeleteDatetime";
        }
        
        // Client impact tables
        if (table.equals("client_x_system") && foreignKeyColumn.equals("Client_ID")) {
            return "system|id|Deleted_datetime";
        }
        if (table.equals("product_x_client") && foreignKeyColumn.equals("Client_ID")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("client_x_process") && foreignKeyColumn.equals("Client_ID")) {
            return "process|id|deleteddatetime";
        }
        if (table.equals("client_x_project") && foreignKeyColumn.equals("Client_ID")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("client_x_policy") && foreignKeyColumn.equals("Client_ID")) {
            return "policy|ID|DeletedDatetime";
        }
        if (table.equals("capability_x_client") && foreignKeyColumn.equals("Client_ID")) {
            return "capability|ID|DeletedDatetime";
        }
        if (table.equals("client_x_dataset") && foreignKeyColumn.equals("Client_ID")) {
            return "dataset|ID|DeletedDatetime";
        }
        if (table.equals("client_x_glossary") && foreignKeyColumn.equals("Client_ID")) {
            return "glossary|ID|Deleted_datetime";
        }
        
        // Process impact tables
        if (table.equals("product_x_process") && foreignKeyColumn.equals("processid")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("client_x_process") && foreignKeyColumn.equals("Process_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("project_x_process") && foreignKeyColumn.equals("process_id")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("policy_x_process") && foreignKeyColumn.equals("process_id")) {
            return "policy|ID|DeletedDatetime";
        }
        if (table.equals("process_x_dataset") && foreignKeyColumn.equals("processid")) {
            return "dataset|id|DeletedDatetime";
        }
        
        // Project impact tables
        if (table.equals("product_x_project") && foreignKeyColumn.equals("projectid")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("client_x_project") && foreignKeyColumn.equals("Project_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("policy_x_project") && foreignKeyColumn.equals("project_id")) {
            return "policy|ID|DeletedDatetime";
        }
        
        // Product impact tables
        if (table.equals("product_x_system") && foreignKeyColumn.equals("Product_ID")) {
            return "system|id|Deleted_datetime";
        }
        if (table.equals("product_x_dataset") && foreignKeyColumn.equals("Product_ID")) {
            return "dataset|id|DeletedDatetime";
        }
        if (table.equals("product_x_legal") && foreignKeyColumn.equals("Product_ID")) {
            return "legal|ID|DeleteDatetime";
        }
        if (table.equals("product_x_client") && foreignKeyColumn.equals("Product_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("product_x_businessarea") && foreignKeyColumn.equals("Product_ID")) {
            return "business_area|ID|deletedatetime";
        }
        if (table.equals("product_x_glossary") && foreignKeyColumn.equals("productid")) {
            return "glossary|ID|Deleted_datetime";
        }
        if (table.equals("product_x_process") && foreignKeyColumn.equals("productid")) {
            return "process|id|deleteddatetime";
        }
        if (table.equals("product_x_policy") && foreignKeyColumn.equals("productid")) {
            return "policy|ID|DeletedDatetime";
        }
        if (table.equals("product_x_project") && foreignKeyColumn.equals("productid")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("regulation_x_product") && foreignKeyColumn.equals("ProductID")) {
            return "regulation|ID|DeletedDatetime";
        }
        if (table.equals("capability_x_product") && foreignKeyColumn.equals("Product_ID")) {
            return "capability|ID|DeletedDatetime";
        }
        
        // Glossary impact tables
        if (table.equals("glossary_x_process") && foreignKeyColumn.equals("Glossary_ID")) {
            return "process|id|deleteddatetime";
        }
        if (table.equals("glossary_x_project") && foreignKeyColumn.equals("Glossary_ID")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("product_x_glossary") && foreignKeyColumn.equals("glossaryid")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("client_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("businessarea_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "business_area|ID|deletedatetime";
        }
        if (table.equals("policy_x_glossary") && foreignKeyColumn.equals("GlossaryID")) {
            return "policy|ID|DeletedDatetime";
        }
        if (table.equals("capability_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "capability|ID|DeletedDatetime";
        }
        if (table.equals("glossary_x_attribute") && foreignKeyColumn.equals("Attribute_ID")) {
            return "attribute|ID|DeletedDatetime";
        }
        
        // Process impact tables (as source)
        if (table.equals("process_x_system") && foreignKeyColumn.equals("process_id")) {
            return "system|id|Deleted_datetime";
        }
        if (table.equals("process_x_interface") && foreignKeyColumn.equals("process_id")) {
            return "interface|id|deleted_datetime";
        }
        if (table.equals("process_x_legal") && foreignKeyColumn.equals("Process_ID")) {
            return "legal|ID|DeleteDatetime";
        }
        if (table.equals("process_x_attribute") && foreignKeyColumn.equals("processid")) {
            return "attribute|ID|DeletedDatetime";
        }
        if (table.equals("glossary_x_process") && foreignKeyColumn.equals("Process_ID")) {
            return "glossary|ID|Deleted_datetime";
        }
        if (table.equals("businessarea_x_process") && foreignKeyColumn.equals("Process_ID")) {
            return "business_area|ID|deletedatetime";
        }
        
        // Project impact tables (as source)
        if (table.equals("project_x_capability") && foreignKeyColumn.equals("Project_ID")) {
            return "capability|ID|DeletedDatetime";
        }
        if (table.equals("project_x_businessarea") && foreignKeyColumn.equals("Project_ID")) {
            return "business_area|ID|deletedatetime";
        }
        if (table.equals("project_x_attribute") && foreignKeyColumn.equals("projectid")) {
            return "attribute|ID|DeletedDatetime";
        }
        if (table.equals("glossary_x_project") && foreignKeyColumn.equals("Project_ID")) {
            return "glossary|ID|Deleted_datetime";
        }
        if (table.equals("regulation_x_project") && foreignKeyColumn.equals("ProjectID")) {
            return "regulation|ID|DeletedDatetime";
        }
        
        // Policy impact tables
        if (table.equals("policy_x_system") && foreignKeyColumn.equals("Policy_ID")) {
            return "system|id|Deleted_datetime";
        }
        if (table.equals("policy_x_process") && foreignKeyColumn.equals("policy_id")) {
            return "process|id|deleteddatetime";
        }
        if (table.equals("policy_x_project") && foreignKeyColumn.equals("policy_id")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("policy_x_dataset") && foreignKeyColumn.equals("PolicyID")) {
            return "dataset|id|DeletedDatetime";
        }
        if (table.equals("product_x_policy") && foreignKeyColumn.equals("policyid")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("client_x_policy") && foreignKeyColumn.equals("Policy_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("policy_x_businessarea") && foreignKeyColumn.equals("Policy_ID")) {
            return "business_area|ID|deletedatetime";
        }
        if (table.equals("policy_x_legal") && foreignKeyColumn.equals("Policy_ID")) {
            return "legal|ID|DeleteDatetime";
        }
        if (table.equals("policy_x_attribute") && foreignKeyColumn.equals("policyid")) {
            return "attribute|ID|DeletedDatetime";
        }
        if (table.equals("policy_x_glossary") && foreignKeyColumn.equals("PolicyID")) {
            return "glossary|ID|Deleted_datetime";
        }
        if (table.equals("regulation_x_policy") && foreignKeyColumn.equals("PolicyID")) {
            return "regulation|ID|DeletedDatetime";
        }
        
        // Capability impact tables
        if (table.equals("capability_x_system") && foreignKeyColumn.equals("Capability_ID")) {
            return "system|id|Deleted_datetime";
        }
        if (table.equals("capability_x_process") && foreignKeyColumn.equals("Capability_ID")) {
            return "process|id|deleteddatetime";
        }
        if (table.equals("capability_x_glossary") && foreignKeyColumn.equals("Capability_ID")) {
            return "glossary|ID|Deleted_datetime";
        }
        if (table.equals("capability_x_product") && foreignKeyColumn.equals("Capability_ID")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("capability_x_client") && foreignKeyColumn.equals("Capability_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("capability_x_legal") && foreignKeyColumn.equals("Capability_ID")) {
            return "legal|ID|DeleteDatetime";
        }
        if (table.equals("capability_x_businessarea") && foreignKeyColumn.equals("Capability_ID")) {
            return "business_area|ID|deletedatetime";
        }
        if (table.equals("project_x_capability") && foreignKeyColumn.equals("Capability_ID")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("committee_x_capability") && foreignKeyColumn.equals("Capability_ID")) {
            return "committee|ID|DeleteDatetime";
        }
        
        // Committee impact tables
        if (table.equals("committee_x_capability") && foreignKeyColumn.equals("Committee_ID")) {
            return "capability|ID|DeletedDatetime";
        }
        
        // Regulation impact tables
        if (table.equals("regulation_x_product") && foreignKeyColumn.equals("RegulationID")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("regulation_x_policy") && foreignKeyColumn.equals("RegulationID")) {
            return "policy|ID|DeletedDatetime";
        }
        if (table.equals("regulation_x_project") && foreignKeyColumn.equals("RegulationID")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("regulation_x_regulatorytheme") && foreignKeyColumn.equals("Regulation_ID")) {
            return "regulatorytheme|ID|DeletedDatetime";
        }
        
        // Business area impact tables
        if (table.equals("businessarea_x_system") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "system|id|Deleted_datetime";
        }
        if (table.equals("businessarea_x_process") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "process|id|deleteddatetime";
        }
        if (table.equals("businessarea_x_glossary") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "glossary|ID|Deleted_datetime";
        }
        if (table.equals("product_x_businessarea") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("capability_x_businessarea") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "capability|ID|DeletedDatetime";
        }
        if (table.equals("project_x_businessarea") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("policy_x_businessarea") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "policy|ID|DeletedDatetime";
        }
        
        // Legal impact tables
        if (table.equals("legal_x_geography") && foreignKeyColumn.equals("Legal_ID")) {
            return "geography|ID|DeletedDatetime";
        }
        if (table.equals("system_x_legal") && foreignKeyColumn.equals("Legal_ID")) {
            return "system|id|Deleted_datetime";
        }
        if (table.equals("dataset_x_legal") && foreignKeyColumn.equals("Legal_ID")) {
            return "dataset|id|DeletedDatetime";
        }
        if (table.equals("policy_x_legal") && foreignKeyColumn.equals("Legal_ID")) {
            return "policy|ID|DeletedDatetime";
        }
        
        // Interface impact tables
        if (table.equals("process_x_interface") && foreignKeyColumn.equals("interface_id")) {
            return "process|id|deleteddatetime";
        }
        
        // Attribute impact tables
        if (table.equals("glossary_x_attribute") && foreignKeyColumn.equals("Attribute_ID")) {
            return "glossary|ID|Deleted_datetime";
        }
        if (table.equals("process_x_attribute") && foreignKeyColumn.equals("attributeid")) {
            return "process|id|deleteddatetime";
        }
        
        // Geography impact tables
        if (table.equals("legal_x_geography") && foreignKeyColumn.equals("Geography_ID")) {
            return "legal|ID|DeleteDatetime";
        }
        
        // Regulatory theme impact tables
        if (table.equals("regulation_x_regulatorytheme") && foreignKeyColumn.equals("RegulatoryTheme_ID")) {
            return "regulation|ID|DeletedDatetime";
        }
        
        // Dataset impact tables
        if (table.equals("product_x_dataset") && foreignKeyColumn.equals("Dataset_ID")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("client_x_dataset") && foreignKeyColumn.equals("Dataset_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("policy_x_dataset") && foreignKeyColumn.equals("DatasetID")) {
            return "policy|ID|DeletedDatetime";
        }
        if (table.equals("process_x_dataset") && foreignKeyColumn.equals("datasetid")) {
            return "process|id|deleteddatetime";
        }
        if (table.equals("project_x_dataset") && foreignKeyColumn.equals("dataset_id")) {
            return "project|id|deletedatetime";
        }
        if (table.equals("dataset_x_legal") && foreignKeyColumn.equals("Dataset_ID")) {
            return "legal|ID|DeleteDatetime";
        }
        
        return null;
    }
    
    /**
     * Get the related ID column name in the relationship table
     */
    private String getRelatedIdColumnInRelationshipTable(String table, String foreignKeyColumn) {
        // System impact tables (system is target)
        if (table.equals("product_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "Product_ID";
        }
        if (table.equals("client_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "Client_ID";
        }
        if (table.equals("process_x_system") && foreignKeyColumn.equals("system_id")) {
            return "process_id";
        }
        if (table.equals("capability_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "Capability_ID";
        }
        if (table.equals("project_x_system") && foreignKeyColumn.equals("systemid")) {
            return "projectid";
        }
        if (table.equals("businessarea_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "BusinessArea_ID";
        }
        if (table.equals("policy_x_system") && foreignKeyColumn.equals("System_ID")) {
            return "Policy_ID";
        }
        if (table.equals("system_x_legal") && foreignKeyColumn.equals("System_ID")) {
            return "Legal_ID";
        }
        
        // Client impact tables
        if (table.equals("client_x_system") && foreignKeyColumn.equals("Client_ID")) {
            return "System_ID";
        }
        if (table.equals("product_x_client") && foreignKeyColumn.equals("Client_ID")) {
            return "Product_ID";
        }
        if (table.equals("client_x_process") && foreignKeyColumn.equals("Client_ID")) {
            return "Process_ID";
        }
        if (table.equals("client_x_project") && foreignKeyColumn.equals("Client_ID")) {
            return "Project_ID";
        }
        if (table.equals("client_x_policy") && foreignKeyColumn.equals("Client_ID")) {
            return "Policy_ID";
        }
        if (table.equals("capability_x_client") && foreignKeyColumn.equals("Client_ID")) {
            return "Capability_ID";
        }
        if (table.equals("client_x_dataset") && foreignKeyColumn.equals("Client_ID")) {
            return "Dataset_ID";
        }
        if (table.equals("client_x_glossary") && foreignKeyColumn.equals("Client_ID")) {
            return "Glossary_ID";
        }
        
        // Process impact tables
        if (table.equals("product_x_process") && foreignKeyColumn.equals("processid")) {
            return "productid";
        }
        if (table.equals("client_x_process") && foreignKeyColumn.equals("Process_ID")) {
            return "Client_ID";
        }
        if (table.equals("project_x_process") && foreignKeyColumn.equals("process_id")) {
            return "projectid";
        }
        if (table.equals("policy_x_process") && foreignKeyColumn.equals("process_id")) {
            return "policy_id";
        }
        if (table.equals("process_x_dataset") && foreignKeyColumn.equals("processid")) {
            return "datasetid";
        }
        
        // Project impact tables
        if (table.equals("product_x_project") && foreignKeyColumn.equals("projectid")) {
            return "productid";
        }
        if (table.equals("client_x_project") && foreignKeyColumn.equals("Project_ID")) {
            return "Client_ID";
        }
        if (table.equals("policy_x_project") && foreignKeyColumn.equals("project_id")) {
            return "policy_id";
        }
        
        // Product impact tables
        if (table.equals("product_x_system") && foreignKeyColumn.equals("Product_ID")) {
            return "System_ID";
        }
        if (table.equals("product_x_dataset") && foreignKeyColumn.equals("Product_ID")) {
            return "Dataset_ID";
        }
        if (table.equals("product_x_legal") && foreignKeyColumn.equals("Product_ID")) {
            return "Legal_ID";
        }
        if (table.equals("product_x_client") && foreignKeyColumn.equals("Product_ID")) {
            return "Client_ID";
        }
        if (table.equals("product_x_businessarea") && foreignKeyColumn.equals("Product_ID")) {
            return "BusinessArea_ID";
        }
        if (table.equals("product_x_glossary") && foreignKeyColumn.equals("productid")) {
            return "glossaryid";
        }
        if (table.equals("product_x_process") && foreignKeyColumn.equals("productid")) {
            return "processid";
        }
        if (table.equals("product_x_policy") && foreignKeyColumn.equals("productid")) {
            return "policyid";
        }
        if (table.equals("product_x_project") && foreignKeyColumn.equals("productid")) {
            return "projectid";
        }
        if (table.equals("regulation_x_product") && foreignKeyColumn.equals("ProductID")) {
            return "RegulationID";
        }
        if (table.equals("capability_x_product") && foreignKeyColumn.equals("Product_ID")) {
            return "Capability_ID";
        }
        
        // Glossary impact tables
        if (table.equals("glossary_x_process") && foreignKeyColumn.equals("Glossary_ID")) {
            return "Process_ID";
        }
        if (table.equals("glossary_x_project") && foreignKeyColumn.equals("Glossary_ID")) {
            return "Project_ID";
        }
        if (table.equals("product_x_glossary") && foreignKeyColumn.equals("glossaryid")) {
            return "productid";
        }
        if (table.equals("client_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "Client_ID";
        }
        if (table.equals("businessarea_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "BusinessArea_ID";
        }
        if (table.equals("policy_x_glossary") && foreignKeyColumn.equals("GlossaryID")) {
            return "PolicyID";
        }
        if (table.equals("capability_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "Capability_ID";
        }
        if (table.equals("glossary_x_attribute") && foreignKeyColumn.equals("Attribute_ID")) {
            return "Glossary_ID";
        }
        
        // Process impact tables (as source)
        if (table.equals("process_x_system") && foreignKeyColumn.equals("process_id")) {
            return "system_id";
        }
        if (table.equals("process_x_interface") && foreignKeyColumn.equals("process_id")) {
            return "interface_id";
        }
        if (table.equals("process_x_legal") && foreignKeyColumn.equals("Process_ID")) {
            return "Legal_ID";
        }
        if (table.equals("process_x_attribute") && foreignKeyColumn.equals("processid")) {
            return "attributeid";
        }
        if (table.equals("glossary_x_process") && foreignKeyColumn.equals("Process_ID")) {
            return "Glossary_ID";
        }
        if (table.equals("businessarea_x_process") && foreignKeyColumn.equals("Process_ID")) {
            return "BusinessArea_ID";
        }
        
        // Project impact tables (as source)
        if (table.equals("project_x_capability") && foreignKeyColumn.equals("Project_ID")) {
            return "Capability_ID";
        }
        if (table.equals("project_x_businessarea") && foreignKeyColumn.equals("Project_ID")) {
            return "BusinessArea_ID";
        }
        if (table.equals("project_x_attribute") && foreignKeyColumn.equals("projectid")) {
            return "attribute_id";
        }
        if (table.equals("glossary_x_project") && foreignKeyColumn.equals("Project_ID")) {
            return "Glossary_ID";
        }
        if (table.equals("regulation_x_project") && foreignKeyColumn.equals("ProjectID")) {
            return "RegulationID";
        }
        
        // Policy impact tables
        if (table.equals("policy_x_system") && foreignKeyColumn.equals("Policy_ID")) {
            return "System_ID";
        }
        if (table.equals("policy_x_process") && foreignKeyColumn.equals("policy_id")) {
            return "process_id";
        }
        if (table.equals("policy_x_project") && foreignKeyColumn.equals("policy_id")) {
            return "project_id";
        }
        if (table.equals("policy_x_dataset") && foreignKeyColumn.equals("PolicyID")) {
            return "Dataset_ID";
        }
        if (table.equals("product_x_policy") && foreignKeyColumn.equals("policyid")) {
            return "productid";
        }
        if (table.equals("client_x_policy") && foreignKeyColumn.equals("Policy_ID")) {
            return "Client_ID";
        }
        if (table.equals("policy_x_businessarea") && foreignKeyColumn.equals("Policy_ID")) {
            return "BusinessArea_ID";
        }
        if (table.equals("policy_x_legal") && foreignKeyColumn.equals("Policy_ID")) {
            return "Legal_ID";
        }
        if (table.equals("policy_x_attribute") && foreignKeyColumn.equals("policyid")) {
            return "Attribute_ID";
        }
        if (table.equals("policy_x_glossary") && foreignKeyColumn.equals("PolicyID")) {
            return "GlossaryID";
        }
        if (table.equals("regulation_x_policy") && foreignKeyColumn.equals("PolicyID")) {
            return "RegulationID";
        }
        
        // Capability impact tables
        if (table.equals("capability_x_system") && foreignKeyColumn.equals("Capability_ID")) {
            return "System_ID";
        }
        if (table.equals("capability_x_process") && foreignKeyColumn.equals("Capability_ID")) {
            return "Process_ID";
        }
        if (table.equals("capability_x_glossary") && foreignKeyColumn.equals("Capability_ID")) {
            return "Glossary_ID";
        }
        if (table.equals("capability_x_product") && foreignKeyColumn.equals("Capability_ID")) {
            return "Product_ID";
        }
        if (table.equals("capability_x_client") && foreignKeyColumn.equals("Capability_ID")) {
            return "Client_ID";
        }
        if (table.equals("capability_x_legal") && foreignKeyColumn.equals("Capability_ID")) {
            return "Legal_ID";
        }
        if (table.equals("capability_x_businessarea") && foreignKeyColumn.equals("Capability_ID")) {
            return "BusinessArea_ID";
        }
        if (table.equals("project_x_capability") && foreignKeyColumn.equals("Capability_ID")) {
            return "Project_ID";
        }
        if (table.equals("committee_x_capability") && foreignKeyColumn.equals("Capability_ID")) {
            return "Committee_ID";
        }
        
        // Committee impact tables
        if (table.equals("committee_x_capability") && foreignKeyColumn.equals("Committee_ID")) {
            return "Capability_ID";
        }
        
        // Regulation impact tables
        if (table.equals("regulation_x_product") && foreignKeyColumn.equals("RegulationID")) {
            return "ProductID";
        }
        if (table.equals("regulation_x_policy") && foreignKeyColumn.equals("RegulationID")) {
            return "PolicyID";
        }
        if (table.equals("regulation_x_project") && foreignKeyColumn.equals("RegulationID")) {
            return "ProjectID";
        }
        if (table.equals("regulation_x_regulatorytheme") && foreignKeyColumn.equals("Regulation_ID")) {
            return "RegulatoryTheme_ID";
        }
        
        // Business area impact tables
        if (table.equals("businessarea_x_system") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "System_ID";
        }
        if (table.equals("businessarea_x_process") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "Process_ID";
        }
        if (table.equals("businessarea_x_glossary") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "Glossary_ID";
        }
        if (table.equals("product_x_businessarea") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "Product_ID";
        }
        if (table.equals("capability_x_businessarea") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "Capability_ID";
        }
        if (table.equals("project_x_businessarea") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "Project_ID";
        }
        if (table.equals("policy_x_businessarea") && foreignKeyColumn.equals("BusinessArea_ID")) {
            return "Policy_ID";
        }
        
        // Legal impact tables
        if (table.equals("legal_x_geography") && foreignKeyColumn.equals("Legal_ID")) {
            return "Geography_ID";
        }
        if (table.equals("system_x_legal") && foreignKeyColumn.equals("Legal_ID")) {
            return "System_ID";
        }
        if (table.equals("dataset_x_legal") && foreignKeyColumn.equals("Legal_ID")) {
            return "Dataset_ID";
        }
        if (table.equals("policy_x_legal") && foreignKeyColumn.equals("Legal_ID")) {
            return "Policy_ID";
        }
        
        // Interface impact tables
        if (table.equals("process_x_interface") && foreignKeyColumn.equals("interface_id")) {
            return "process_id";
        }
        
        // Attribute impact tables
        if (table.equals("glossary_x_attribute") && foreignKeyColumn.equals("Attribute_ID")) {
            return "Glossary_ID";
        }
        if (table.equals("process_x_attribute") && foreignKeyColumn.equals("attributeid")) {
            return "processid";
        }
        
        // Geography impact tables
        if (table.equals("legal_x_geography") && foreignKeyColumn.equals("Geography_ID")) {
            return "Legal_ID";
        }
        
        // Regulatory theme impact tables
        if (table.equals("regulation_x_regulatorytheme") && foreignKeyColumn.equals("RegulatoryTheme_ID")) {
            return "Regulation_ID";
        }
        
        // Dataset impact tables
        if (table.equals("product_x_dataset") && foreignKeyColumn.equals("Dataset_ID")) {
            return "Product_ID";
        }
        if (table.equals("client_x_dataset") && foreignKeyColumn.equals("Dataset_ID")) {
            return "Client_ID";
        }
        if (table.equals("policy_x_dataset") && foreignKeyColumn.equals("DatasetID")) {
            return "PolicyID";
        }
        if (table.equals("process_x_dataset") && foreignKeyColumn.equals("datasetid")) {
            return "processid";
        }
        if (table.equals("project_x_dataset") && foreignKeyColumn.equals("dataset_id")) {
            return "projectid";
        }
        if (table.equals("dataset_x_legal") && foreignKeyColumn.equals("Dataset_ID")) {
            return "Legal_ID";
        }
        
        return null;
    }
    
    /**
     * Check for child objects
     */
    private int checkChildObjects(Connection conn, FacetConfig config, int objectId) throws SQLException {
        // Process table uses "parentid" (lowercase), not Parent_ID
        String parentCol = ("process".equalsIgnoreCase(config.tableName)) ? "parentid" : config.parentColumn;
        if (parentCol == null) {
            return 0;
        }
        
        String sql = "SELECT COUNT(*) FROM " + config.tableName + " WHERE " + parentCol + " = ?";
        
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
     * Perform special checks for specific facets
     */
    private JsonObject performSpecialCheck(Connection conn, String facetType, int objectId, String checkType) throws SQLException {
        // Simplified version - only implement critical checks
        // Full implementation would include all checks from DeletionValidationServlet
        
        switch (checkType) {
            case "datasets":
            case "system-datasets":
                return checkSystemDatasets(conn, objectId);
            case "geography-regulator-links":
                return checkGeographyRegulatorLinks(conn, objectId);
            case "geography-legal-entity-links":
                return checkGeographyLegalEntityLinks(conn, objectId);
            case "geography-regulation-links":
                return checkGeographyRegulationLinks(conn, objectId);
            case "regulator-regulation-links":
                return checkRegulatorRegulationLinks(conn, objectId);
            case "regulator-geography-links":
                return checkRegulatorGeographyLinks(conn, objectId);
            case "glossary-relationships":
                return checkGlossaryRelationships(conn, objectId);
            case "glossary-alias-names":
                return checkGlossaryAliasNames(conn, objectId);
            case "glossary-system-links":
                return checkGlossarySystemLinks(conn, objectId);
            case "glossary-dataset-links":
                return checkGlossaryDatasetLinks(conn, objectId);
            case "glossary-attribute-links":
                return checkGlossaryAttributeLinks(conn, objectId);
            case "glossary-dq-rule-links":
                return checkGlossaryDQRuleLinks(conn, objectId);
            case "dataset-relationships":
                return checkDatasetRelationships(conn, objectId);
            case "dataset-content-summary":
                return checkDatasetContentSummary(conn, objectId);
            case "dataset-attributes":
                return checkDatasetAttributes(conn, objectId);
            case "people-active-crs":
                return checkPeopleActiveCRs(conn, objectId);
            case "people-managers":
                return checkPeopleManagers(conn, objectId);
            case "people-stakeholder-links":
                return checkPeopleStakeholderLinks(conn, objectId);
            case "people-dq-rules":
                return checkPeopleDQRules(conn, objectId);
            case "regulation-regulator-links":
                return checkRegulationRegulatorLinks(conn, objectId);
            case "process-relationships":
                return checkProcessRelationships(conn, objectId);
            case "project-relationships":
                return checkProjectRelationships(conn, objectId);
            case "policy-relationships":
                return checkPolicyRelationships(conn, objectId);
            case "regulation-relationships":
                return checkRegulationRelationships(conn, objectId);
            case "capability-relationships":
                return checkCapabilityRelationships(conn, objectId);
            case "committee-relationships":
                return checkCommitteeRelationships(conn, objectId);
            case "attribute-relationships":
                return checkAttributeRelationships(conn, objectId);
            case "interface-glossary-links":
                return checkInterfaceGlossaryLinks(conn, objectId);
            case "regulatory-theme-regulations":
                return checkRegulatoryThemeRegulations(conn, objectId);
            case "dq-rule-attribute-links":
                return checkDQRuleAttributeLinks(conn, objectId);
            case "dq-rule-technical-reference":
                return checkDQRuleTechnicalReference(conn, objectId);
            default:
                // For other checks, return null (non-blocking)
                return null;
        }
    }
    
    /**
     * Check system datasets
     */
    private JsonObject checkSystemDatasets(Connection conn, int systemId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dataset d LEFT JOIN status s ON d.status = s.id " +
                     "WHERE d.MasterSource = ? AND d.DeletedDatetime IS NULL AND (s.primaryname IS NULL OR s.primaryname != 'Deleted')";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, systemId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "System is linked to " + count + " dataset" + (count > 1 ? "s" : "") + 
                        " that are not deleted. Remove these datasets first.");
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Check geography regulator links
     */
    private JsonObject checkGeographyRegulatorLinks(Connection conn, int geographyId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM geography_x_regulator WHERE Geography_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, geographyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Geography has " + count + " regulator link" + (count > 1 ? "s" : "") + 
                        ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check geography legal entity links
     */
    private JsonObject checkGeographyLegalEntityLinks(Connection conn, int geographyId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM geography_x_legal WHERE Geography_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, geographyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Geography has " + count + " legal entity link" + (count > 1 ? "s" : "") + 
                        ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check geography regulation links
     */
    private JsonObject checkGeographyRegulationLinks(Connection conn, int geographyId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM geography_x_regulation WHERE Geography_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, geographyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Geography has " + count + " regulation link" + (count > 1 ? "s" : "") + 
                        ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check regulator regulation links
     */
    private JsonObject checkRegulatorRegulationLinks(Connection conn, int regulatorId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM regulation_x_regulator WHERE Regulator_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, regulatorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Regulator has " + count + " regulation link" + (count > 1 ? "s" : "") + 
                        ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check glossary relationships
     * A glossary must not have any relationship with other glossaries (found in Relationships tab)
     */
    private JsonObject checkGlossaryRelationships(Connection conn, int glossaryId) throws SQLException {
        // Check both as source and as target in glossary_x_glossary table
        String sql = "SELECT COUNT(*) FROM glossary_x_glossary WHERE SourceGlossaryID = ? OR TargetGlossaryID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, glossaryId);
            stmt.setInt(2, glossaryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🔗 Glossary has " + count + " relationship" + (count > 1 ? "s" : "") + " with other glossaries in the Relationships tab. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            logger.debug("Error checking glossary relationships: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check glossary alias names (BUDG: no Alias Names)
     */
    private JsonObject checkGlossaryAliasNames(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM glossary_alias_name WHERE GlossaryID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, glossaryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Glossary has " + count + " alias name" + (count > 1 ? "s" : "") + ". Remove all alias names before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table"))) {
                return null;
            }
            throw e;
        }
        return null;
    }

    /**
     * Check glossary system links (BUDG: no links to System)
     */
    private JsonObject checkGlossarySystemLinks(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM glossary_x_system WHERE GlossaryID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, glossaryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Glossary has " + count + " system link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table"))) {
                return null;
            }
            throw e;
        }
        return null;
    }

    /**
     * Check glossary dataset links (BUDG: no links to Data Set)
     */
    private JsonObject checkGlossaryDatasetLinks(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dataset WHERE glossary = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, glossaryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Glossary has " + count + " data set link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table") || e.getMessage().contains("Unknown column"))) {
                return null;
            }
            throw e;
        }
        return null;
    }

    /**
     * Check glossary attribute links (BUDG: no links to Attribute)
     */
    private JsonObject checkGlossaryAttributeLinks(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attribute WHERE Glossary_ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, glossaryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Glossary has " + count + " attribute link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table") || e.getMessage().contains("Unknown column"))) {
                return null;
            }
            throw e;
        }
        return null;
    }

    /**
     * Check glossary data quality rule links (BUDG: no links to Data Quality Rule)
     */
    private JsonObject checkGlossaryDQRuleLinks(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dq_rule WHERE Glossary_ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, glossaryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Glossary has " + count + " data quality rule link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table"))) {
                return null;
            }
            throw e;
        }
        return null;
    }

    /**
     * Check regulator geography links
     */
    private JsonObject checkRegulatorGeographyLinks(Connection conn, int regulatorId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM geography_x_regulator WHERE Regulator_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, regulatorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Regulator has " + count + " geography link" + (count > 1 ? "s" : "") + 
                        ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check dataset relationships
     * A dataset must not have any relationship with other datasets (found in Relationships tab)
     */
    private JsonObject checkDatasetRelationships(Connection conn, int datasetId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attribute_x_attribute axa " +
                    "INNER JOIN attribute sa ON sa.ID = axa.Source_AttributeID " +
                    "INNER JOIN attribute ta ON ta.ID = axa.Target_AttributeID " +
                    "WHERE sa.Dataset_ID = ? OR ta.Dataset_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            stmt.setInt(2, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🔗 Data Set has " + count + " relationship" + (count > 1 ? "s" : "") + 
                        " with other data sets in the Relationships tab. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking dataset relationships: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Check dataset content summary (indirect relationships)
     * A dataset must not have any indirect relationships displayed in the Data Content Summary tab
     */
    private JsonObject checkDatasetContentSummary(Connection conn, int datasetId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attribute a " +
                    "WHERE a.Dataset_ID = ? AND a.Glossary_ID IS NOT NULL AND a.DeletedDatetime IS NULL";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📋 Data Set has " + count + " indirect relationship" + (count > 1 ? "s" : "") + 
                        " in the Data Content Summary tab. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking dataset content summary: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Check dataset attributes
     * A dataset must not contain any attributes
     */
    private JsonObject checkDatasetAttributes(Connection conn, int datasetId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attribute WHERE Dataset_ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '1970-01-01 00:00:00')";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📊 Data Set contains " + count + " attribute" + (count > 1 ? "s" : "") + 
                        ". Remove all attributes before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking dataset attributes: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if person has any Running or Pending Start change requests they created.
     * Cannot delete a person if they have raised a CR that is still Running or Pending Start.
     */
    private JsonObject checkPeopleActiveCRs(Connection conn, int peopleId) throws SQLException {
        FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
        boolean hasActiveCRs = facetChangesDAO.hasActiveCRsCreatedByPerson(peopleId);
        if (hasActiveCRs) {
            JsonObject result = new JsonObject();
            result.addProperty("blocking", true);
            result.addProperty("message", "🔄 Cannot delete person: This person has a Running or Pending Start change request. Please complete or cancel the change request first.");
            return result;
        }
        return null;
    }
    
    /**
     * Check people managers and reports
     * A People object must not contain managers or people reporting to the person.
     */
    private JsonObject checkPeopleManagers(Connection conn, int peopleId) throws SQLException {
        // Check if person has a manager (people_x_people table: Employee = peopleId)
        String checkManagerSql = "SELECT COUNT(*) FROM people_x_people WHERE Employee = ?";
        int hasManager = 0;
        
        try (PreparedStatement stmt = conn.prepareStatement(checkManagerSql)) {
            stmt.setInt(1, peopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    hasManager = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking if person has manager: {}", e.getMessage());
        }
        
        // Check if people report to this person (people_x_people table: Manager = peopleId)
        String checkReportsSql = "SELECT COUNT(*) FROM people_x_people WHERE Manager = ?";
        int hasReports = 0;
        
        try (PreparedStatement stmt = conn.prepareStatement(checkReportsSql)) {
            stmt.setInt(1, peopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    hasReports = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking if people report to person: {}", e.getMessage());
        }
        
        int totalCount = hasManager + hasReports;
        if (totalCount > 0) {
            JsonObject result = new JsonObject();
            result.addProperty("blocking", true);
            String message;
            if (hasManager > 0 && hasReports > 0) {
                message = "👥 Person has a manager and " + hasReports + " person" + (hasReports > 1 ? "s" : "") + 
                    " reporting to them. Remove these relationships before deletion.";
            } else if (hasManager > 0) {
                message = "👥 Person has a manager. Remove this relationship before deletion.";
            } else {
                message = "👥 Person has " + hasReports + " person" + (hasReports > 1 ? "s" : "") + 
                    " reporting to them. Remove these relationships before deletion.";
            }
            result.addProperty("message", message);
            return result;
        }
        
        return null;
    }
    
    /**
     * Check people stakeholder links across all facets
     * A People object must not be a stakeholder of any other facet object
     */
    private JsonObject checkPeopleStakeholderLinks(Connection conn, int peopleId) throws SQLException {
        int totalLinks = 0;
        
        Map<String, String> stakeholderTableConfig = new HashMap<>();
        stakeholderTableConfig.put("system_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("dataset_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("businessarea_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("capability_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("client_x_objectxpeople", "Object_X_ipid");
        stakeholderTableConfig.put("committee_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("glossary_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("legal_x_objectxpeople", "Object_x_ip");
        stakeholderTableConfig.put("policy_x_objectxpeople", "object_x_ip");
        stakeholderTableConfig.put("process_x_objectxpeople", "object_x_ip");
        stakeholderTableConfig.put("product_x_objectxpeople", "object_x_ip");
        stakeholderTableConfig.put("project_x_objectxpeople", "object_x_ip");
        stakeholderTableConfig.put("regulation_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("interface_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("attribute_x_objectxpeople", "Object_x_ipid");
        
        for (Map.Entry<String, String> entry : stakeholderTableConfig.entrySet()) {
            String table = entry.getKey();
            String column = entry.getValue();
            
            try {
                String sql = "SELECT COUNT(*) FROM " + table + " jt " +
                            "INNER JOIN object_x_people oxp ON jt." + column + " = oxp.id " +
                            "WHERE oxp.ipid = ?";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, peopleId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            totalLinks += rs.getInt(1);
                        }
                    }
                }
            } catch (SQLException e) {
                logger.debug("Error checking stakeholder table {}: {}", table, e.getMessage());
            }
        }
        
        if (totalLinks > 0) {
            JsonObject result = new JsonObject();
            result.addProperty("blocking", true);
            result.addProperty("message", "👤 Person is a stakeholder of " + totalLinks + " object" + (totalLinks > 1 ? "s" : "") + 
                ". Remove these stakeholder relationships before deletion.");
            return result;
        }
        
        return null;
    }

    /**
     * Check people data quality rules (BUDG: Person must not be linked to Data Quality Rule)
     */
    private JsonObject checkPeopleDQRules(Connection conn, int peopleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dq_rule WHERE People_ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, peopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "Person has " + count + " data quality rule" + (count > 1 ? "s" : "") + ". Remove these rules before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table"))) {
                return null;
            }
            throw e;
        }
        return null;
    }
    
    /**
     * Check regulation regulator links
     * A regulation must not have links to any regulator
     */
    private JsonObject checkRegulationRegulatorLinks(Connection conn, int regulationId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM regulation_x_regulator rrr " +
                    "INNER JOIN regulator r ON rrr.RegulatorID = r.ID " +
                    "WHERE rrr.RegulationID = ? " +
                    "AND (r.DeletedDatetime IS NULL OR r.DeletedDatetime = '1970-01-01 00:00:00')";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, regulationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📜 Regulation has " + count + " regulator link" + (count > 1 ? "s" : "") + 
                        ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking regulation regulator links: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Generic relationship checker for facets that have self-relationships
     * Filters out deleted objects (both source and target)
     */
    private JsonObject checkGenericRelationships(Connection conn, int objectId, String tableName, String sourceColumn, String targetColumn, String entityName) throws SQLException {
        String mainTable;
        String deletedColumn;
        String idColumn;
        
        if ("regulation_x_regulation".equals(tableName)) {
            mainTable = "regulation";
            deletedColumn = "DeletedDatetime";
            idColumn = "ID";
        } else if ("policy_x_policy".equals(tableName)) {
            mainTable = "policy";
            deletedColumn = "DeletedDatetime";
            idColumn = "ID";
        } else if ("project_x_project".equals(tableName)) {
            mainTable = "project";
            deletedColumn = "deletedatetime";
            idColumn = "id";
        } else if ("process_x_process".equals(tableName)) {
            mainTable = "process";
            deletedColumn = "deleteddatetime";
            idColumn = "id";
        } else if ("glossary_x_glossary".equals(tableName)) {
            mainTable = "glossary";
            deletedColumn = "Deleted_datetime";
            idColumn = "ID";
        } else if ("capability_x_capability".equals(tableName)) {
            mainTable = "capability";
            deletedColumn = "DeletedDatetime";
            idColumn = "ID";
        } else if ("committee_x_committee".equals(tableName)) {
            mainTable = "committee";
            deletedColumn = "DeleteDatetime";
            idColumn = "ID";
        } else {
            // For unknown relationship types, use simple query (no filtering)
            String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE " + sourceColumn + " = ? OR " + targetColumn + " = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, objectId);
                stmt.setInt(2, objectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        int count = rs.getInt(1);
                        JsonObject result = new JsonObject();
                        result.addProperty("blocking", true);
                        result.addProperty("message", "🔗 " + entityName + " has " + count + " relationship" + (count > 1 ? "s" : "") + 
                            " with other " + entityName.toLowerCase() + " objects. Remove these relationships before deletion.");
                        return result;
                    }
                }
            } catch (SQLException e) {
                logger.debug("Error checking {} relationships: {}", entityName, e.getMessage());
            }
            return null;
        }
        
        // For known relationship types, filter out deleted objects on both sides
        String sql = "SELECT COUNT(*) FROM " + tableName + " r " +
                    "LEFT JOIN " + mainTable + " source_obj ON r." + sourceColumn + " = source_obj." + idColumn + " " +
                    "LEFT JOIN " + mainTable + " target_obj ON r." + targetColumn + " = target_obj." + idColumn + " " +
                    "WHERE (r." + sourceColumn + " = ? OR r." + targetColumn + " = ?) " +
                    "AND (source_obj." + deletedColumn + " IS NULL OR source_obj." + deletedColumn + " = '1970-01-01 00:00:00') " +
                    "AND (target_obj." + deletedColumn + " IS NULL OR target_obj." + deletedColumn + " = '1970-01-01 00:00:00')";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            stmt.setInt(2, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🔗 " + entityName + " has " + count + " relationship" + (count > 1 ? "s" : "") + 
                        " with other " + entityName.toLowerCase() + " objects. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking {} relationships: {}", entityName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Check process relationships
     */
    private JsonObject checkProcessRelationships(Connection conn, int processId) throws SQLException {
        return checkGenericRelationships(conn, processId, "process_x_process", "sourceprocess_id", "targetprocess_id", "Process");
    }
    
    /**
     * Check project relationships
     */
    private JsonObject checkProjectRelationships(Connection conn, int projectId) throws SQLException {
        return checkGenericRelationships(conn, projectId, "project_x_project", "sourceprojectid", "targetprojectid", "Project");
    }
    
    /**
     * Check policy relationships
     */
    private JsonObject checkPolicyRelationships(Connection conn, int policyId) throws SQLException {
        return checkGenericRelationships(conn, policyId, "policy_x_policy", "sourceid", "targetid", "Policy");
    }
    
    /**
     * Check regulation relationships
     */
    private JsonObject checkRegulationRelationships(Connection conn, int regulationId) throws SQLException {
        return checkGenericRelationships(conn, regulationId, "regulation_x_regulation", "SourceRegulationID", "TargetRegulationID", "Regulation");
    }
    
    /**
     * Check capability relationships
     */
    private JsonObject checkCapabilityRelationships(Connection conn, int capabilityId) throws SQLException {
        return checkGenericRelationships(conn, capabilityId, "capability_x_capability", "Source_ID", "Target_ID", "Capability");
    }
    
    /**
     * Check committee relationships
     */
    private JsonObject checkCommitteeRelationships(Connection conn, int committeeId) throws SQLException {
        return checkGenericRelationships(conn, committeeId, "committee_x_committee", "Source_Committee_ID", "Target_Committee_ID", "Committee");
    }
    
    /**
     * Check attribute relationships with Process, Policy, and Project
     */
    private JsonObject checkAttributeRelationships(Connection conn, int attributeId) throws SQLException {
        int totalRelationships = 0;
        
        try {
            String sql = "SELECT COUNT(*) FROM process_x_attribute WHERE Attribute_ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, attributeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) totalRelationships += rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking process attribute relationships: {}", e.getMessage());
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM policy_x_attribute WHERE Attribute_ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, attributeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) totalRelationships += rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking policy attribute relationships: {}", e.getMessage());
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM project_x_attribute WHERE Attribute_ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, attributeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) totalRelationships += rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking project attribute relationships: {}", e.getMessage());
        }
        
        if (totalRelationships > 0) {
            JsonObject result = new JsonObject();
            result.addProperty("blocking", true);
            result.addProperty("message", "🔗 Attribute has " + totalRelationships + " relationship" + (totalRelationships > 1 ? "s" : "") + 
                " with Process, Policy, and Project objects. Remove these relationships before deletion.");
            return result;
        }
        
        return null;
    }
    
    /**
     * Check interface glossary links
     */
    private JsonObject checkInterfaceGlossaryLinks(Connection conn, int interfaceId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM interface_x_glossary WHERE Interface = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, interfaceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📚 Interface has " + count + " glossary link" + (count > 1 ? "s" : "") + 
                        ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking interface glossary links: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Check regulatory theme regulations
     */
    private JsonObject checkRegulatoryThemeRegulations(Connection conn, int themeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM regulation WHERE RegulatoryTheme_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, themeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📋 Regulatory Theme contains " + count + " regulation" + (count > 1 ? "s" : "") + 
                        ". Remove these regulations before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking regulatory theme regulations: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get entity display name
     */
    private String getEntityDisplayName(String facetType) {
        Map<String, String> displayNames = new HashMap<>();
        displayNames.put("system", "System");
        displayNames.put("dataset", "Dataset");
        displayNames.put("business-area", "Business Area");
        displayNames.put("capability", "Capability");
        displayNames.put("client", "Client");
        displayNames.put("committee", "Committee");
        displayNames.put("geography", "Geography");
        displayNames.put("regulator", "Regulator");
        displayNames.put("glossary", "Glossary");
        displayNames.put("policy", "Policy");
        displayNames.put("process", "Process");
        displayNames.put("product", "Product");
        displayNames.put("project", "Project");
        displayNames.put("regulation", "Regulation");
        displayNames.put("regulatory-theme", "Regulatory Theme");
        displayNames.put("legal-entity", "Legal Entity");
        displayNames.put("org-unit", "Org Unit");
        displayNames.put("people", "People");
        displayNames.put("interface", "Interface");
        displayNames.put("attribute", "Attribute");
        displayNames.put("data-quality-rule", "Data Quality Rule");
        displayNames.put("dataquality", "Data Quality Rule");
        displayNames.put("data_quality", "Data Quality Rule");
        
        return displayNames.getOrDefault(facetType.toLowerCase(), facetType);
    }

    /**
     * Check Data Quality Rule has no attributes linked (BUDG: no Attributes linked).
     */
    private JsonObject checkDQRuleAttributeLinks(Connection conn, int dqRuleId) throws SQLException {
        String[] tablesToTry = {"attribute_x_data_quality", "data_quality_x_attribute", "attribute_x_dq_rule", "dq_rule_x_attribute"};
        String[] idColumns = {"Data_Quality_ID", "Data_Quality_ID", "DQRule_ID", "DQRule_ID"};
        for (int i = 0; i < tablesToTry.length; i++) {
            try {
                String sql = "SELECT COUNT(*) FROM `" + tablesToTry[i] + "` WHERE `" + idColumns[i] + "` = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, dqRuleId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            int count = rs.getInt(1);
                            JsonObject result = new JsonObject();
                            result.addProperty("blocking", true);
                            result.addProperty("message", "Data Quality Rule has " + count + " attribute link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                            return result;
                        }
                    }
                }
            } catch (SQLException e) {
                if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table") || e.getMessage().contains("Unknown column"))) {
                    continue;
                }
                throw e;
            }
        }
        return null;
    }

    /**
     * Check Data Quality Rule has no Technical Rule Reference (BUDG: no Technical Rule Reference).
     */
    private JsonObject checkDQRuleTechnicalReference(Connection conn, int dqRuleId) throws SQLException {
        try {
            String sql = "SELECT COUNT(*) FROM data_quality WHERE (ID = ? OR id = ?) AND TechnicalRuleReference IS NOT NULL AND TRIM(COALESCE(TechnicalRuleReference, '')) != ''";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, dqRuleId);
                stmt.setInt(2, dqRuleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        JsonObject result = new JsonObject();
                        result.addProperty("blocking", true);
                        result.addProperty("message", "Data Quality Rule has a Technical Rule Reference. Remove it before deletion.");
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown column"))) {
                return null;
            }
            throw e;
        }
        try {
            String junctionSql = "SELECT COUNT(*) FROM local_data_quality_rule_x_technical_reference WHERE Data_Quality_Rule_ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(junctionSql)) {
                stmt.setInt(1, dqRuleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        int count = rs.getInt(1);
                        JsonObject result = new JsonObject();
                        result.addProperty("blocking", true);
                        result.addProperty("message", "Data Quality Rule has " + count + " technical rule reference" + (count > 1 ? "s" : "") + ". Remove these before deletion.");
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || e.getMessage().contains("Unknown table"))) {
                return null;
            }
            throw e;
        }
        return null;
    }
    
    /**
     * Configuration class for facet deletion settings
     */
    @SuppressWarnings({"unused"})
    private static class FacetConfig {
        final String tableName;
        final String deleteColumn;
        final String statusColumn;
        final String parentColumn;
        final boolean hasStatus;
        final String stakeholderTable;
        final String childTable;
        final String[] specialChecks;
        
        FacetConfig(String tableName, String deleteColumn, String statusColumn, 
                   String parentColumn, boolean hasStatus, String stakeholderTable,
                   String childTable, String[] specialChecks) {
            this.tableName = tableName;
            this.deleteColumn = deleteColumn;
            this.statusColumn = statusColumn;
            this.parentColumn = parentColumn;
            this.hasStatus = hasStatus;
            this.stakeholderTable = stakeholderTable;
            this.childTable = childTable;
            this.specialChecks = specialChecks;
        }
    }
}

