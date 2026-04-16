package com.example.budg_v2;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.dao.FacetChangesDAO;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for validating object deletion conditions
 * Checks status, stakeholders, impact relationships, and child objects
 */
@WebServlet("/api/deletion-validation/*")
public class DeletionValidationServlet extends HttpServlet {
    
    private static final Gson gson = new Gson();
    private static final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    
    // Facet configuration for deletion validation
    private static final Map<String, FacetConfig> FACET_CONFIGS = new HashMap<>();
    
    static {
        // System configuration
        FACET_CONFIGS.put("system", new FacetConfig(
            "system", "Deleted_datetime", "status", "parent_id", true,
            "system_x_objectxpeople", "system_x_dataset", new String[]{"datasets", "system-glossary-data-content"}
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
        
        // Regulator configuration (no status check - no parent column)
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
            "product", "DeletedDatetime", "status", "parent_id", true,
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
        
        // Attribute configuration
        FACET_CONFIGS.put("attribute", new FacetConfig(
            "attribute", "DeletedDatetime", null, "Dataset_ID", false,
            "attribute_x_objectxpeople", null, new String[]{"attribute-relationships", "attribute-dataset-cr", "attribute-values", "attribute-target-relationships", "attribute-source-relationships"}
        ));
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            sendError(response, 400, "Facet type and object ID required");
            return;
        }
        
        String[] pathParts = pathInfo.substring(1).split("/");
        if (pathParts.length < 2) {
            sendError(response, 400, "Both facet type and object ID required");
            return;
        }
        
        String facetType = pathParts[0];
        String objectIdStr = pathParts[1];
        
        try {
            int objectId = Integer.parseInt(objectIdStr);
            JsonObject validationResult = validateDeletion(facetType, objectId);
            
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(gson.toJson(validationResult));
            
        } catch (NumberFormatException e) {
            sendError(response, 400, "Invalid object ID format");
        } catch (Exception e) {
            System.err.println("Deletion validation error: " + e.getMessage());
            e.printStackTrace();
            sendError(response, 500, "Internal server error during validation");
        }
    }
    
    /**
     * Validate deletion conditions for an object
     */
    private JsonObject validateDeletion(String facetType, int objectId) throws SQLException {
        JsonObject result = new JsonObject();
        result.addProperty("canDelete", true);
        
        List<String> errors = new ArrayList<>();
        List<JsonObject> warnings = new ArrayList<>();
        
        FacetConfig config = FACET_CONFIGS.get(facetType);
        if (config == null) {
            errors.add("❌ Unsupported object type: " + facetType);
            result.addProperty("canDelete", false);
            result.add("errors", gson.toJsonTree(errors));
            return result;
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            
            // Check if object exists and get current data
            JsonObject objectData = getObjectData(conn, config, objectId);
            if (objectData == null) {
                String entityName = getEntityDisplayName(facetType);
                errors.add("🔍 " + entityName + " not found or has already been removed.");
                result.addProperty("canDelete", false);
                result.add("errors", gson.toJsonTree(errors));
                return result;
            }
            
            // Status validation (if facet has status)
            if (config.hasStatus) {
                String statusValidation = validateStatus(conn, config, objectData);
                if (statusValidation != null) {
                    errors.add(statusValidation);
                    result.addProperty("canDelete", false);
                }
            }
            
            // Check for stakeholders
            int stakeholderCount = checkStakeholders(conn, config, objectId);
            if (stakeholderCount > 0) {
                // For attribute, stakeholders are blocking (cannot delete)
                if ("attribute".equals(facetType)) {
                    String entityName = getEntityDisplayName(facetType);
                    errors.add("👤 " + entityName + " is linked to " + stakeholderCount + " stakeholder" + (stakeholderCount > 1 ? "s" : "") + ". Remove these stakeholder links before deletion.");
                    result.addProperty("canDelete", false);
                } else {
                    // For other facets, stakeholders are warnings (will be removed)
                    JsonObject warning = new JsonObject();
                    warning.addProperty("type", "stakeholders");
                    String entityName = getEntityDisplayName(facetType);
                    warning.addProperty("message", "⚠️ " + entityName + " has " + stakeholderCount + " linked stakeholder" + (stakeholderCount > 1 ? "s" : "") + ". These Stakeholders will be removed if you proceed.");
                    warning.addProperty("count", stakeholderCount);
                    warnings.add(warning);
                }
            }
            
            // Check for impact relationships
            int impactCount = checkImpactRelationships(conn, facetType, objectId);
            if (impactCount > 0) {
                String entityName = getEntityDisplayName(facetType);
                errors.add("🔗 " + entityName + " has " + impactCount + " active relationship" + (impactCount > 1 ? "s" : "") + " in the Impact tab. Please remove these relationships before deletion.");
                result.addProperty("canDelete", false);
            }
            
            // Check for child objects
            int childCount = checkChildObjects(conn, config, objectId);
            if (childCount > 0) {
                JsonObject warning = new JsonObject();
                warning.addProperty("type", "children");
                String entityName = getEntityDisplayName(facetType);
                warning.addProperty("message", "📦 " + entityName + " contains " + childCount + " child object" + (childCount > 1 ? "s" : "") + ". These will be unlinked if you proceed.");
                warning.addProperty("count", childCount);
                warnings.add(warning);
            }
            
            // Special checks
            if (config.specialChecks != null) {
                for (String checkType : config.specialChecks) {
                    JsonObject specialCheck = performSpecialCheck(conn, facetType, objectId, checkType);
                    if (specialCheck != null) {
                        if (specialCheck.get("blocking").getAsBoolean()) {
                            errors.add(specialCheck.get("message").getAsString());
                            result.addProperty("canDelete", false);
                        } else {
                            JsonObject warning = new JsonObject();
                            warning.addProperty("type", checkType);
                            warning.addProperty("message", specialCheck.get("message").getAsString());
                            warnings.add(warning);
                        }
                    }
                }
            }
            
        }
        
        result.add("errors", gson.toJsonTree(errors));
        result.add("warnings", gson.toJsonTree(warnings));
        
        return result;
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
                            // Get status name - use appropriate status table based on facet type
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
     * Get status name by ID
     */
    private String getStatusName(Connection conn, int statusId, String tableName) throws SQLException {
        // Determine the status table name based on the main table
        String statusTable;
        if ("regulation".equals(tableName)) {
            statusTable = "regulation_status";
        } else if ("regulatorytheme".equals(tableName)) {
            statusTable = "status"; // regulatory theme uses the standard status table
        } else {
            statusTable = "status"; // default to standard status table
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
            return "❌ Unable to determine object status. Please try again.";
        }
        
        String statusName = objectData.get("statusName").getAsString();
        if (!"Deleted".equalsIgnoreCase(statusName)) {
            return "📋 BUDG Status must be set to \"Deleted\" before this object can be removed. Current status: " + statusName + ". Please update the status first.";
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
        
        String sql = "SELECT COUNT(*) FROM " + config.stakeholderTable + " WHERE " + 
                     getStakeholderForeignKeyColumn(config.tableName) + " = ?";
        
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
     * Check for impact relationships (both source and target directions)
     * Checks ALL impact tables where this facet appears in ANY direction
     * This ensures comprehensive checking for ALL facets
     */
    private int checkImpactRelationships(Connection conn, String facetType, int objectId) throws SQLException {
        int totalCount = 0;
        
        // Get ALL impact tables for this facet (both source and target)
        List<ImpactTableInfo> allTables = getAllImpactTablesForFacet(facetType);
        
        for (ImpactTableInfo tableInfo : allTables) {
            int count = checkImpactTable(conn, tableInfo.tableName, tableInfo.foreignKeyColumn, objectId);
            totalCount += count;
            // Debug logging - log all tables being checked
            System.out.println("[DeletionValidation] Checking " + tableInfo.tableName + " (" + tableInfo.foreignKeyColumn + "): " + count + " relationships");
        }
        
        return totalCount;
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
     * Get ALL impact relationship tables for a facet (both source and target directions)
     * IMPORTANT: Only include relationships that are ACTUALLY SHOWN in the Impact tab for this facet.
     * Do NOT include relationships that appear in other tabs (e.g., Relationships tab, Hierarchy tab).
     * The deletion validation message says "in the Impact tab", so we must only check what's shown there.
     */
    private List<ImpactTableInfo> getAllImpactTablesForFacet(String facetType) {
        List<ImpactTableInfo> tables = new ArrayList<>();
        
        switch (facetType) {
            case "system":
                // Only relationships shown in System Impact tab: Product, Client, Legal Entity, Process, Capability, Project, Business Area, Policy
                // System as SOURCE
                tables.add(new ImpactTableInfo("system_x_legal", "System_ID"));
                // System as TARGET
                tables.add(new ImpactTableInfo("product_x_system", "System_ID"));
                tables.add(new ImpactTableInfo("client_x_system", "System_ID"));
                tables.add(new ImpactTableInfo("process_x_system", "system_id"));
                tables.add(new ImpactTableInfo("capability_x_system", "System_ID"));
                tables.add(new ImpactTableInfo("project_x_system", "systemid"));
                tables.add(new ImpactTableInfo("businessarea_x_system", "System_ID"));
                tables.add(new ImpactTableInfo("policy_x_system", "System_ID"));
                // Note: glossary_x_system is shown in System Data Content Summary tab (not Impact tab)
                // It is checked separately as a special check (system-glossary-data-content) and should be blocking
                break;
                
            case "dataset":
                // Only relationships shown in Dataset Impact tab: Product, Client, Legal Entity, Policy, Process, Project
                // Dataset as SOURCE
                tables.add(new ImpactTableInfo("dataset_x_legal", "Dataset_ID"));
                // Dataset as TARGET
                tables.add(new ImpactTableInfo("product_x_dataset", "Dataset_ID"));
                tables.add(new ImpactTableInfo("client_x_dataset", "Dataset_ID"));
                tables.add(new ImpactTableInfo("policy_x_dataset", "DatasetID"));
                tables.add(new ImpactTableInfo("process_x_dataset", "datasetid"));
                tables.add(new ImpactTableInfo("project_x_dataset", "dataset_id"));
                // Note: glossary_x_dataset is NOT shown in Dataset Impact tab
                break;
                
            case "glossary":
                // Only relationships shown in Glossary Impact tab: Product, Client, Business Area, Process, Policy, Capability, Project
                // Glossary as SOURCE (where glossary is source)
                tables.add(new ImpactTableInfo("glossary_x_process", "Glossary_ID"));
                tables.add(new ImpactTableInfo("glossary_x_project", "Glossary_ID"));
                // Glossary as TARGET (where glossary is target - reverse relationships shown in Impact tab)
                tables.add(new ImpactTableInfo("product_x_glossary", "glossaryid"));
                tables.add(new ImpactTableInfo("client_x_glossary", "Glossary_ID"));
                tables.add(new ImpactTableInfo("businessarea_x_glossary", "Glossary_ID"));
                tables.add(new ImpactTableInfo("policy_x_glossary", "GlossaryID"));
                tables.add(new ImpactTableInfo("capability_x_glossary", "Glossary_ID"));
                // Note: glossary_x_system, glossary_x_dataset, glossary_x_attribute are NOT shown in Glossary Impact tab
                break;
                
            case "process":
                // Only relationships shown in Process Impact tab: System, Product, Client, Project, Policy, Interface, Legal, Dataset, Attribute, Glossary (reverse), Business Area (reverse), Capability (reverse)
                // Process as SOURCE
                tables.add(new ImpactTableInfo("process_x_system", "process_id"));
                tables.add(new ImpactTableInfo("process_x_interface", "process_id"));
                tables.add(new ImpactTableInfo("process_x_legal", "Process_ID"));
                tables.add(new ImpactTableInfo("process_x_dataset", "processid"));
                tables.add(new ImpactTableInfo("process_x_attribute", "processid"));
                // Process as TARGET (reverse relationships shown in Impact tab)
                tables.add(new ImpactTableInfo("product_x_process", "processid"));
                tables.add(new ImpactTableInfo("client_x_process", "Process_ID"));
                tables.add(new ImpactTableInfo("project_x_process", "process_id"));
                tables.add(new ImpactTableInfo("policy_x_process", "process_id"));
                tables.add(new ImpactTableInfo("glossary_x_process", "Process_ID"));
                tables.add(new ImpactTableInfo("businessarea_x_process", "Process_ID"));
                // Note: project_x_process and policy_x_process are shown in Project/Policy Impact tabs, not Process Impact tab
                break;
                
            case "project":
                // Only relationships shown in Project Impact tab: System, Process, Glossary, Policy, Product, Client, Capability, Business Area, Dataset, Attribute, Regulation (reverse)
                // Project as SOURCE
                tables.add(new ImpactTableInfo("project_x_system", "projectid"));
                tables.add(new ImpactTableInfo("project_x_process", "projectid"));
                tables.add(new ImpactTableInfo("project_x_capability", "Project_ID"));
                tables.add(new ImpactTableInfo("project_x_businessarea", "Project_ID"));
                tables.add(new ImpactTableInfo("project_x_dataset", "projectid"));
                tables.add(new ImpactTableInfo("project_x_attribute", "projectid"));
                // Project as TARGET (reverse relationships shown in Impact tab)
                tables.add(new ImpactTableInfo("product_x_project", "projectid"));
                tables.add(new ImpactTableInfo("client_x_project", "Project_ID"));
                tables.add(new ImpactTableInfo("policy_x_project", "project_id"));
                tables.add(new ImpactTableInfo("glossary_x_project", "Project_ID"));
                tables.add(new ImpactTableInfo("regulation_x_project", "ProjectID"));
                break;
                
            case "policy":
                // Only relationships shown in Policy Impact tab: Product, Client, Process, Project, System, Business Area, Legal, Dataset, Attribute, Glossary, Regulation (reverse)
                // Policy as SOURCE
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
                // Policy as TARGET (reverse relationships shown in Impact tab)
                tables.add(new ImpactTableInfo("regulation_x_policy", "PolicyID"));
                break;
                
            case "product":
                // Only relationships shown in Product Impact tab: Legal Entity, Client, Business Area, System (reverse), Project (reverse), Dataset (reverse), Regulation (reverse), Capability (reverse), Glossary (reverse), Process (reverse), Policy (reverse)
                // Product as SOURCE
                tables.add(new ImpactTableInfo("product_x_legal", "Product_ID"));
                tables.add(new ImpactTableInfo("product_x_client", "Product_ID"));
                tables.add(new ImpactTableInfo("product_x_businessarea", "Product_ID"));
                // Product as TARGET (reverse relationships shown in Impact tab - these are loaded via reverse APIs)
                // Note: These reverse relationships are checked via product_x_system, product_x_project, etc. where product is source
                // But the Impact tab shows them as reverse, so we check the opposite direction
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
                // Only relationships shown in Client Impact tab: System, Product, Process, Project, Policy, Capability, Dataset, Glossary
                // Client as TARGET (reverse relationships - these show which entities impact this client)
                // Note: Some tables use client_x_* pattern, others use *_x_client pattern
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
                // Only relationships shown in Capability Impact tab: System, Process, Glossary, Product, Client, Legal Entity, Business Area, Project, Committee (reverse)
                // Capability as SOURCE
                tables.add(new ImpactTableInfo("capability_x_system", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_process", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_glossary", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_product", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_client", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_legal", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_businessarea", "Capability_ID"));
                tables.add(new ImpactTableInfo("capability_x_project", "Capability_ID"));
                // Capability as TARGET (reverse relationships shown in Impact tab)
                tables.add(new ImpactTableInfo("committee_x_capability", "Capability_ID"));
                break;
                
            case "committee":
                // Committee as SOURCE
                tables.add(new ImpactTableInfo("committee_x_capability", "Committee_ID"));
                // Committee as TARGET (if any tables reference committee)
                break;
                
            case "regulation":
                // Only relationships shown in Regulation Impact tab: Product, Policy, Project, Regulatory Theme
                // Regulation as SOURCE
                tables.add(new ImpactTableInfo("regulation_x_product", "RegulationID"));
                tables.add(new ImpactTableInfo("regulation_x_policy", "RegulationID"));
                tables.add(new ImpactTableInfo("regulation_x_project", "RegulationID"));
                tables.add(new ImpactTableInfo("regulation_x_regulatorytheme", "Regulation_ID"));
                // Note: regulation_x_regulator is NOT shown in Regulation Impact tab (it's in Relationships tab)
                break;
                
            case "business-area":
            case "businessarea":
                // Only relationships shown in Business Area Impact tab: Glossary, System, Process, Product (reverse), Capability (reverse), Project (reverse), Policy (reverse)
                // Business area as SOURCE
                tables.add(new ImpactTableInfo("businessarea_x_system", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("businessarea_x_process", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("businessarea_x_glossary", "BusinessArea_ID"));
                // Business area as TARGET (reverse relationships shown in Impact tab)
                tables.add(new ImpactTableInfo("product_x_businessarea", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("capability_x_businessarea", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("project_x_businessarea", "BusinessArea_ID"));
                tables.add(new ImpactTableInfo("policy_x_businessarea", "BusinessArea_ID"));
                break;
                
            case "interface":
            case "system-interface":
                // Interface as TARGET
                tables.add(new ImpactTableInfo("process_x_interface", "interface_id"));
                break;
                
            case "legal":
            case "legal-entity":
                // Only relationships shown in Legal Entity Impact tab: Geography, System, Dataset, Policy
                // Legal as SOURCE (forward relationships)
                tables.add(new ImpactTableInfo("legal_x_geography", "Legal_ID"));
                // Legal as TARGET (reverse relationships shown in Impact tab)
                tables.add(new ImpactTableInfo("system_x_legal", "Legal_ID"));
                tables.add(new ImpactTableInfo("dataset_x_legal", "Legal_ID"));
                tables.add(new ImpactTableInfo("policy_x_legal", "Legal_ID"));
                // Note: process_x_legal is NOT shown in Legal Entity Impact tab
                break;
                
            case "attribute":
                // Only relationships shown in Attribute Impact context (shown in Process and Project Impact tabs)
                // Attribute as TARGET
                tables.add(new ImpactTableInfo("glossary_x_attribute", "Attribute_ID"));
                tables.add(new ImpactTableInfo("process_x_attribute", "attributeid"));
                // Note: project_x_attribute is shown in Project Impact tab, but we check it when validating Project deletion
                break;
                
            case "geography":
                // Only relationships shown in Geography Impact tab: Legal Entity
                // Geography as TARGET
                tables.add(new ImpactTableInfo("legal_x_geography", "Geography_ID"));
                // Note: regulator_x_geography is NOT shown in Geography Impact tab (it's in Relationships tab or not displayed)
                break;
                
            case "regulator":
                // Note: Regulator does not have an Impact tab in the UI
                // regulation_x_regulator is checked as a deletion requirement (not an impact relationship)
                // regulator_x_geography is NOT shown in any Impact tab
                // Since there's no Impact tab, we don't check impact relationships for Regulator
                break;
                
            case "regulatory-theme":
            case "regulatorytheme":
                // Only relationships shown in Regulatory Theme Impact context (shown in Regulation Impact tab)
                // Regulatory theme as TARGET
                tables.add(new ImpactTableInfo("regulation_x_regulatorytheme", "RegulatoryTheme_ID"));
                break;
                
            case "org-unit":
            case "orgunit":
                // Org unit as TARGET (people table has org_unit_id, but that's not an impact relationship table)
                // Org unit doesn't have direct impact relationship tables
                // It's referenced in people table, but that's handled separately
                break;
                
            case "people":
                // People don't have direct impact relationship tables
                // They are stakeholders (checked separately) and have manager relationships (checked separately)
                // People can be referenced in various stakeholder tables, but those are stakeholder checks, not impact
                break;
                
            // Add more facets as needed
            default:
                // For unknown facets, try to find tables dynamically
                // This is a fallback - ideally all facets should be explicitly listed above
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
            // Determine the related table and its delete column based on the relationship table
            String relatedTableInfo = getRelatedTableInfoForImpactCheck(table, foreignKeyColumn);
            
            String sql;
            if (relatedTableInfo != null) {
                // Join with related table to filter deleted objects
                String[] parts = relatedTableInfo.split("\\|");
                String relatedTable = parts[0];
                String relatedIdColumn = parts[1];
                String relatedDeleteColumn = parts[2];
                
                // Determine the related ID column in the relationship table
                String relationshipRelatedIdColumn = getRelatedIdColumnInRelationshipTable(table, foreignKeyColumn);
                
                if (relationshipRelatedIdColumn != null) {
                    sql = "SELECT COUNT(*) FROM `" + table + "` r " +
                          "INNER JOIN `" + relatedTable + "` rt ON r.`" + relationshipRelatedIdColumn + "` = rt.`" + relatedIdColumn + "` " +
                          "WHERE r.`" + foreignKeyColumn + "` = ? " +
                          "AND r.`" + relationshipRelatedIdColumn + "` IS NOT NULL " +
                          "AND (rt.`" + relatedDeleteColumn + "` IS NULL OR rt.`" + relatedDeleteColumn + "` = '1970-01-01 00:00:00')";
                } else {
                    // Fallback: simple query without join
                    sql = "SELECT COUNT(*) FROM `" + table + "` WHERE `" + foreignKeyColumn + "` = ?";
                }
            } else {
                // No related table info - use simple query
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
            // Table might not exist or column might be different - continue to next table
            if (e.getMessage() != null && 
                (e.getMessage().contains("doesn't exist") || 
                 e.getMessage().contains("Unknown table") ||
                 e.getMessage().contains("Unknown column"))) {
                System.err.println("Error checking impact table " + table + " with column " + foreignKeyColumn + ": " + e.getMessage());
                return 0;
            }
            // Re-throw if it's a different error
            System.err.println("SQL error checking impact table " + table + ": " + e.getMessage());
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
        // System as source
        if (table.equals("system_x_legal") && foreignKeyColumn.equals("System_ID")) {
            return "legal|ID|DeleteDatetime";
        }
        
        // Client impact tables (client is target - reverse relationships)
        // Note: Some tables use client_x_* pattern, others use *_x_client pattern
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
        // Glossary as target: filter out deleted related client / policy / capability / business area
        if (table.equals("client_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("policy_x_glossary") && foreignKeyColumn.equals("GlossaryID")) {
            return "policy|ID|DeletedDatetime";
        }
        if (table.equals("capability_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "capability|ID|DeletedDatetime";
        }
        if (table.equals("businessarea_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "business_area|ID|deletedatetime";
        }
        if (table.equals("product_x_glossary") && foreignKeyColumn.equals("glossaryid")) {
            return "product|id|deleteddatetime";
        }
        
        // Process impact tables (process is target - reverse relationships)
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
        // Process as source
        if (table.equals("process_x_dataset") && foreignKeyColumn.equals("processid")) {
            return "dataset|id|DeletedDatetime";
        }
        
        // Project impact tables (project is target - reverse relationships)
        if (table.equals("product_x_project") && foreignKeyColumn.equals("projectid")) {
            return "product|id|deleteddatetime";
        }
        if (table.equals("client_x_project") && foreignKeyColumn.equals("Project_ID")) {
            return "client|ID|DeleteDatetime";
        }
        if (table.equals("policy_x_project") && foreignKeyColumn.equals("project_id")) {
            return "policy|ID|DeletedDatetime";
        }
        
        // Product impact tables (product is source)
        if (table.equals("product_x_system") && foreignKeyColumn.equals("Product_ID")) {
            return "system|id|Deleted_datetime";
        }
        if (table.equals("product_x_dataset") && foreignKeyColumn.equals("Product_ID")) {
            return "dataset|id|DeletedDatetime";
        }
        
        // Return null for tables we don't have info for - will use simple query
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
        // System as source
        if (table.equals("system_x_legal") && foreignKeyColumn.equals("System_ID")) {
            return "Legal_ID";
        }
        
        // Client impact tables (client is target - reverse relationships)
        // Note: Some tables use client_x_* pattern, others use *_x_client pattern
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
        if (table.equals("client_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "Client_ID";
        }
        if (table.equals("policy_x_glossary") && foreignKeyColumn.equals("GlossaryID")) {
            return "PolicyID";
        }
        if (table.equals("capability_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "Capability_ID";
        }
        if (table.equals("businessarea_x_glossary") && foreignKeyColumn.equals("Glossary_ID")) {
            return "BusinessArea_ID";
        }
        if (table.equals("product_x_glossary") && foreignKeyColumn.equals("glossaryid")) {
            return "productid";
        }
        
        // Process impact tables (process is target - reverse relationships)
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
        // Process as source
        if (table.equals("process_x_dataset") && foreignKeyColumn.equals("processid")) {
            return "datasetid";
        }
        
        // Project impact tables (project is target - reverse relationships)
        if (table.equals("product_x_project") && foreignKeyColumn.equals("projectid")) {
            return "productid";
        }
        if (table.equals("client_x_project") && foreignKeyColumn.equals("Project_ID")) {
            return "Client_ID";
        }
        if (table.equals("policy_x_project") && foreignKeyColumn.equals("project_id")) {
            return "policy_id";
        }
        
        // Product impact tables (product is source)
        if (table.equals("product_x_system") && foreignKeyColumn.equals("Product_ID")) {
            return "System_ID";
        }
        if (table.equals("product_x_dataset") && foreignKeyColumn.equals("Product_ID")) {
            return "Dataset_ID";
        }
        
        return null;
    }
    
    /**
     * Check for child objects
     */
    private int checkChildObjects(Connection conn, FacetConfig config, int objectId) throws SQLException {
        if (config.parentColumn == null) {
            return 0;
        }
        
        String sql = "SELECT COUNT(*) FROM " + config.tableName + " WHERE " + config.parentColumn + " = ?";
        
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
        switch (checkType) {
            // System checks
            case "datasets":
                return checkSystemDatasets(conn, objectId);
            case "system-glossary-data-content":
                return checkSystemGlossaryDataContent(conn, objectId);
            
            // Geography checks
            case "geography-regulator-links":
                return checkGeographyRegulatorLinks(conn, objectId);
            case "geography-legal-entity-links":
                return checkGeographyLegalEntityLinks(conn, objectId);
            case "geography-regulation-links":
                return checkGeographyRegulationLinks(conn, objectId);
            
            // Regulator checks
            case "regulator-regulation-links":
                return checkRegulatorRegulationLinks(conn, objectId);
            case "regulator-geography-links":
                return checkRegulatorGeographyLinks(conn, objectId);
            
            // Dataset checks
            case "dataset-relationships":
                return checkDatasetRelationships(conn, objectId);
            case "dataset-attributes":
                return checkDatasetAttributes(conn, objectId);
            case "dataset-content-summary":
                return checkDatasetContentSummary(conn, objectId);
            
            // Glossary checks
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
            case "glossary-relationships":
                return checkGlossaryRelationships(conn, objectId);
            
            // People checks
            case "people-active-crs":
                return checkPeopleActiveCRs(conn, objectId);
            case "people-managers":
                return checkPeopleManagers(conn, objectId);
            case "people-dq-rules":
                return checkPeopleDQRules(conn, objectId);
            case "people-stakeholder-links":
                return checkPeopleStakeholderLinks(conn, objectId);
            
            // Attribute checks
            case "attribute-relationships":
                return checkAttributeRelationships(conn, objectId);
            case "attribute-dataset-cr":
                return checkAttributeDatasetChangeRequest(conn, objectId);
            case "attribute-values":
                return checkAttributeValues(conn, objectId);
            case "attribute-target-relationships":
                return checkAttributeTargetRelationships(conn, objectId);
            case "attribute-source-relationships":
                return checkAttributeSourceRelationships(conn, objectId);
            
            // Capability checks
            case "capability-relationships":
                return checkCapabilityRelationships(conn, objectId);
            
            // Committee checks
            case "committee-relationships":
                return checkCommitteeRelationships(conn, objectId);
            
            // Process checks
            case "process-relationships":
                return checkProcessRelationships(conn, objectId);
            
            // Project checks
            case "project-relationships":
                return checkProjectRelationships(conn, objectId);
            
            // Policy checks
            case "policy-relationships":
                return checkPolicyRelationships(conn, objectId);
            
            // Regulation checks
            case "regulation-relationships":
                return checkRegulationRelationships(conn, objectId);
            case "regulation-regulator-links":
                return checkRegulationRegulatorLinks(conn, objectId);
            
            // Regulatory Theme checks
            case "regulatory-theme-regulations":
                return checkRegulatoryThemeRegulations(conn, objectId);
            
            // Interface checks
            case "interface-glossary-links":
                return checkInterfaceGlossaryLinks(conn, objectId);
            
            default:
                return null;
        }
    }
    
    /**
     * Check system datasets
     * Returns different messages based on whether datasets can be auto-deleted or not
     */
    private JsonObject checkSystemDatasets(Connection conn, int systemId) throws SQLException {
        // Get all active datasets for this system
        String sql = "SELECT d.ID FROM dataset d LEFT JOIN status s ON d.status = s.id " +
                     "WHERE d.MasterSource = ? AND d.DeletedDatetime IS NULL AND (s.primaryname IS NULL OR s.primaryname != 'Deleted')";
        
        List<Integer> datasetIds = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, systemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    datasetIds.add(rs.getInt("ID"));
                }
            }
        }
        
        if (datasetIds.isEmpty()) {
            return null;
        }
        
        int count = datasetIds.size();
        
        // Check if any dataset cannot be auto-deleted
        List<Integer> nonDeletableDatasets = new ArrayList<>();
        for (Integer datasetId : datasetIds) {
            if (!canAutoDeleteDataset(conn, datasetId)) {
                nonDeletableDatasets.add(datasetId);
            }
        }
        
        JsonObject result = new JsonObject();
        
        if (!nonDeletableDatasets.isEmpty()) {
            // Some datasets cannot be auto-deleted - blocking
            result.addProperty("blocking", true);
            result.addProperty("message", "💾 System contains " + count + " active data set" + (count > 1 ? "s" : "") + 
                " that cannot be deleted automatically. Please remove relationships or attributes from these datasets before deletion.");
        } else {
            // All datasets can be auto-deleted - warning with proceed message
            result.addProperty("blocking", false);
            result.addProperty("message", "💾 System contains " + count + " active data set" + (count > 1 ? "s" : "") + 
                " that may not be marked as Deleted. The dataset" + (count > 1 ? "s will" : " will") + " be deleted as well. Do you want to proceed?");
        }
        
        return result;
    }
    
    /**
     * Check if a dataset can be auto-deleted when its system is deleted
     * A dataset can be auto-deleted if:
     * 1. It has no impact relationships (in Impact tab)
     * 2. It has no dataset relationships (inbound/outbound)
     * 3. It has no attributes
     */
    private boolean canAutoDeleteDataset(Connection conn, int datasetId) throws SQLException {
        // Check 1: Impact relationships (Impact tab)
        // Dataset impact tables: product_x_dataset, client_x_dataset, policy_x_dataset, process_x_dataset, project_x_dataset, dataset_x_legal
        // Check each table separately and sum the counts
        int totalImpactCount = 0;
        
        // product_x_dataset
        try {
            String sql = "SELECT COUNT(*) FROM product_x_dataset WHERE Dataset_ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalImpactCount += rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        
        // client_x_dataset
        try {
            String sql = "SELECT COUNT(*) FROM client_x_dataset WHERE Dataset_ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalImpactCount += rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        
        // policy_x_dataset
        try {
            String sql = "SELECT COUNT(*) FROM policy_x_dataset WHERE DatasetID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalImpactCount += rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        
        // process_x_dataset
        try {
            String sql = "SELECT COUNT(*) FROM process_x_dataset WHERE datasetid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalImpactCount += rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        
        // project_x_dataset
        try {
            String sql = "SELECT COUNT(*) FROM project_x_dataset WHERE dataset_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalImpactCount += rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        
        // dataset_x_legal
        try {
            String sql = "SELECT COUNT(*) FROM dataset_x_legal WHERE Dataset_ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalImpactCount += rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        
        if (totalImpactCount > 0) {
            return false; // Has impact relationships
        }
        
        // Check 2: Dataset relationships (inbound/outbound)
        try {
            String relationshipSql = "SELECT COUNT(*) FROM attribute_x_attribute axa " +
                "INNER JOIN attribute sa ON sa.ID = axa.Source_AttributeID " +
                "INNER JOIN attribute ta ON ta.ID = axa.Target_AttributeID " +
                "WHERE sa.Dataset_ID = ? OR ta.Dataset_ID = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(relationshipSql)) {
                stmt.setInt(1, datasetId);
                stmt.setInt(2, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return false; // Has relationships
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        
        // Check 3: Attributes
        try {
            String attributesSql = "SELECT COUNT(*) FROM attribute WHERE Dataset_ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '1970-01-01 00:00:00')";
            
            try (PreparedStatement stmt = conn.prepareStatement(attributesSql)) {
                stmt.setInt(1, datasetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return false; // Has attributes
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        
        return true; // Can be auto-deleted
    }
    
    /**
     * Check system-glossary links in data content summary
     * This should be blocking like Impact tab
     */
    private JsonObject checkSystemGlossaryDataContent(Connection conn, int systemId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM glossary_x_system WHERE SystemID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, systemId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📋 System has " + count + " glossary link" + (count > 1 ? "s" : "") + 
                        " in the Data Content Summary tab. Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            System.err.println("Error checking system-glossary data content: " + e.getMessage());
        }
        return null;
    }
    
    // ==================== Geography Checks ====================
    
    /**
     * Check geography regulator links
     */
    private JsonObject checkGeographyRegulatorLinks(Connection conn, int geographyId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM regulator_x_geography WHERE Geography_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, geographyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🔗 Geography has " + count + " regulator link" + (count > 1 ? "s" : "") + 
                        " (regulator). Remove these links before deletion.");
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
        String sql = "SELECT COUNT(*) FROM legal_x_geography WHERE Geography_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, geographyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🏛️ Geography has " + count + " legal entity link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check geography regulation links through regulators
     */
    private JsonObject checkGeographyRegulationLinks(Connection conn, int geographyId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM regulation_x_regulator rr " +
                     "JOIN regulator_x_geography rg ON rr.RegulatorID = rg.Regulator_ID " +
                     "WHERE rg.Geography_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, geographyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📋 Geography has " + count + " regulation link" + (count > 1 ? "s" : "") + " through regulators. Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Tables might not exist - ignore
        }
        return null;
    }
    
    // ==================== Regulator Checks ====================
    
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
                    result.addProperty("message", "📋 Regulator has " + count + " regulation link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check regulator geography links
     */
    private JsonObject checkRegulatorGeographyLinks(Connection conn, int regulatorId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM regulator_x_geography WHERE Regulator_ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, regulatorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🌍 Regulator has " + count + " geography link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    // ==================== Dataset Checks ====================
    
    /**
     * Check dataset relationships
     * A dataset must not have any relationship with other datasets (found in Relationships tab)
     * Relationships are stored as attribute-to-attribute relationships in attribute_x_attribute table
     */
    private JsonObject checkDatasetRelationships(Connection conn, int datasetId) throws SQLException {
        // Check both inbound (source attribute in this dataset) and outbound (target attribute in this dataset)
        // relationships in attribute_x_attribute table
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
                    result.addProperty("message", "🔗 Data Set has " + count + " relationship" + (count > 1 ? "s" : "") + " with other data sets in the Relationships tab. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            System.err.println("Error checking dataset relationships: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Check dataset attributes
     * A dataset must not contain any attributes
     */
    private JsonObject checkDatasetAttributes(Connection conn, int datasetId) throws SQLException {
        // Check for any attributes in this dataset (excluding deleted ones)
        String sql = "SELECT COUNT(*) FROM attribute WHERE Dataset_ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '1970-01-01 00:00:00')";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📊 Data Set contains " + count + " attribute" + (count > 1 ? "s" : "") + ". Remove all attributes before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            System.err.println("Error checking dataset attributes: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Check dataset content summary (indirect relationships)
     * A dataset must not have any indirect relationships displayed in the Data Content Summary tab
     * Indirect relationships are attributes within the dataset that link to glossaries
     */
    private JsonObject checkDatasetContentSummary(Connection conn, int datasetId) throws SQLException {
        // Check if dataset has attributes that link to glossaries (indirect relationships)
        // Attributes with Glossary_ID create indirect relationships shown in Data Content Summary
        String sql = "SELECT COUNT(*) FROM attribute a " +
                    "WHERE a.Dataset_ID = ? AND a.Glossary_ID IS NOT NULL AND a.DeletedDatetime IS NULL";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📋 Data Set has " + count + " indirect relationship" + (count > 1 ? "s" : "") + " in the Data Content Summary tab. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            System.err.println("Error checking dataset content summary: " + e.getMessage());
        }
        return null;
    }
    
    // ==================== Glossary Checks ====================
    
    /**
     * Check glossary alias names
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
                    result.addProperty("message", "🏷️ Glossary has " + count + " alias name" + (count > 1 ? "s" : "") + ". Remove all alias names before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check glossary system links
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
                    result.addProperty("message", "💻 Glossary has " + count + " system link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check glossary dataset links
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
                    result.addProperty("message", "💾 Glossary has " + count + " data set link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check glossary attribute links
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
                    result.addProperty("message", "📊 Glossary has " + count + " attribute link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check glossary data quality rule links
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
                    result.addProperty("message", "📏 Glossary has " + count + " data quality rule link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
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
            System.err.println("Error checking glossary relationships: " + e.getMessage());
        }
        return null;
    }
    
    // ==================== People Checks ====================
    
    /**
     * Check if person has any Running or Pending Start change requests they created.
     * Cannot delete a person if they have raised a CR that is still Running or Pending Start.
     */
    private JsonObject checkPeopleActiveCRs(Connection conn, int peopleId) throws SQLException {
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
            // Table might not exist - ignore
            System.err.println("Error checking if person has manager: " + e.getMessage());
        }
        
        // Check if anyone reports to this person (people_x_people table: Manager = peopleId)
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
            // Table might not exist - ignore
            System.err.println("Error checking if people report to person: " + e.getMessage());
        }
        
        int totalCount = hasManager + hasReports;
        if (totalCount > 0) {
            JsonObject result = new JsonObject();
            result.addProperty("blocking", true);
            String message;
            if (hasManager > 0 && hasReports > 0) {
                message = "👥 Person has a manager and " + hasReports + " person" + (hasReports > 1 ? "s" : "") + " reporting to them. Remove these relationships before deletion.";
            } else if (hasManager > 0) {
                message = "👥 Person has a manager. Remove this relationship before deletion.";
            } else {
                message = "👥 Person has " + hasReports + " person" + (hasReports > 1 ? "s" : "") + " reporting to them. Remove these relationships before deletion.";
            }
            result.addProperty("message", message);
            return result;
        }
        
        return null;
    }
    
    /**
     * Check people data quality rules
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
                    result.addProperty("message", "📏 Person has " + count + " data quality rule" + (count > 1 ? "s" : "") + ". Remove these rules before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check people stakeholder links across all facets
     * A People object must not be a stakeholder of any other facet object
     */
    private JsonObject checkPeopleStakeholderLinks(Connection conn, int peopleId) throws SQLException {
        int totalLinks = 0;
        
        // Map of stakeholder tables to their object_x_people column names
        // Format: "table_name" -> "column_name"
        // Note: Different tables use different column name conventions
        Map<String, String> stakeholderTableConfig = new HashMap<>();
        stakeholderTableConfig.put("system_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("dataset_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("businessarea_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("capability_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("client_x_objectxpeople", "Object_X_ipid");  // Note: capital X
        stakeholderTableConfig.put("committee_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("glossary_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("legal_x_objectxpeople", "Object_x_ip");
        stakeholderTableConfig.put("policy_x_objectxpeople", "object_x_ip");  // Note: lowercase
        stakeholderTableConfig.put("process_x_objectxpeople", "object_x_ip");  // Note: lowercase
        stakeholderTableConfig.put("product_x_objectxpeople", "object_x_ip");  // Note: lowercase
        stakeholderTableConfig.put("project_x_objectxpeople", "object_x_ip");  // Note: lowercase
        stakeholderTableConfig.put("regulation_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("interface_x_objectxpeople", "Object_x_ipid");
        stakeholderTableConfig.put("attribute_x_objectxpeople", "Object_x_ipid");
        
        for (Map.Entry<String, String> entry : stakeholderTableConfig.entrySet()) {
            String table = entry.getKey();
            String column = entry.getValue();
            
            try {
                // Join through object_x_people to find if this person is a stakeholder
                // object_x_people.ipid = peopleId, and the junction table references object_x_people.id
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
                // Table might not exist or column name might be different - ignore
                System.err.println("Error checking stakeholder table " + table + ": " + e.getMessage());
            }
        }
        
        if (totalLinks > 0) {
            JsonObject result = new JsonObject();
            result.addProperty("blocking", true);
            result.addProperty("message", "👤 Person is a stakeholder of " + totalLinks + " object" + (totalLinks > 1 ? "s" : "") + ". Remove these stakeholder relationships before deletion.");
            return result;
        }
        
        return null;
    }
    
    // ==================== Additional Relationship Checks ====================
    
    /**
     * Check attribute relationships with Process, Policy, and Project
     */
    private JsonObject checkAttributeRelationships(Connection conn, int attributeId) throws SQLException {
        int processCount = 0;
        int policyCount = 0;
        int projectCount = 0;
        
        // Check process relationships
        try {
            String sql = "SELECT COUNT(*) FROM process_x_attribute WHERE attributeid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, attributeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) processCount = rs.getInt(1);
                }
            }
        } catch (SQLException e) { /* ignore */ }
        
        // Check policy relationships
        try {
            String sql = "SELECT COUNT(*) FROM policy_x_attribute WHERE attributeid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, attributeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) policyCount = rs.getInt(1);
                }
            }
        } catch (SQLException e) { /* ignore */ }
        
        // Check project relationships
        try {
            String sql = "SELECT COUNT(*) FROM project_x_attribute WHERE attributeid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, attributeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) projectCount = rs.getInt(1);
                }
            }
        } catch (SQLException e) { /* ignore */ }
        
        int totalRelationships = processCount + policyCount + projectCount;
        if (totalRelationships > 0) {
            StringBuilder detail = new StringBuilder();
            if (processCount > 0) {
                detail.append("\n\u2022 ").append(processCount).append(" Process link").append(processCount > 1 ? "s" : "");
            }
            if (policyCount > 0) {
                detail.append("\n\u2022 ").append(policyCount).append(" Policy link").append(policyCount > 1 ? "s" : "");
            }
            if (projectCount > 0) {
                detail.append("\n\u2022 ").append(projectCount).append(" Project link").append(projectCount > 1 ? "s" : "");
            }
            String message = "🔗 This attribute has active links that must be removed before deletion:" + detail
                + "\nOpen the attribute's Impact tab to remove these links.";
            JsonObject result = new JsonObject();
            result.addProperty("blocking", true);
            result.addProperty("message", message);
            return result;
        }
        
        return null;
    }
    
    /**
     * Check if parent dataset has ongoing change requests
     * Cannot delete an attribute if the parent Data Set has ongoing change requests
     */
    private JsonObject checkAttributeDatasetChangeRequest(Connection conn, int attributeId) throws SQLException {
        // Get the parent dataset ID
        String sql = "SELECT Dataset_ID FROM attribute WHERE ID = ?";
        Integer datasetId = null;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attributeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    datasetId = rs.getObject("Dataset_ID") != null ? rs.getInt("Dataset_ID") : null;
                }
            }
        }
        
        if (datasetId == null || datasetId <= 0) {
            return null; // No parent dataset or invalid dataset ID
        }
        
        // Check if dataset has ongoing change requests
        Integer datasetFacetId = facetChangesDAO.getFacetId("dataset");
        if (datasetFacetId != null) {
            boolean hasActiveCRs = facetChangesDAO.hasActiveCRs(datasetFacetId, datasetId);
            if (hasActiveCRs) {
                JsonObject result = new JsonObject();
                result.addProperty("blocking", true);
                result.addProperty("message", "⚠️ Cannot delete attribute: The parent Data Set has ongoing change requests. Please complete or cancel the change request first.");
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Check if attribute has values associated (values tab)
     * You must remove the values associated to an attribute before you can delete an attribute
     */
    private JsonObject checkAttributeValues(Connection conn, int attributeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM values_field WHERE dataset_attribute_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attributeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "📊 Attribute has " + count + " value" + (count > 1 ? "s" : "") + 
                        " associated in the Values tab. Remove these values before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            System.err.println("Error checking attribute values: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if attribute is a relationship target for other attributes (relationships tab)
     * The attribute is the relationship target for one or more attributes
     */
    private JsonObject checkAttributeTargetRelationships(Connection conn, int attributeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attribute_x_attribute WHERE Target_AttributeID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attributeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🔗 Attribute is the relationship target for " + count + " attribute" + (count > 1 ? "s" : "") + 
                        " in the Relationships tab. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            System.err.println("Error checking attribute target relationships: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if attribute is a relationship source for other attributes (relationships tab)
     */
    private JsonObject checkAttributeSourceRelationships(Connection conn, int attributeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attribute_x_attribute WHERE Source_AttributeID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attributeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🔗 This attribute is the source in " + count + " relationship"
                        + (count > 1 ? "s" : "") + " with other attributes. Go to the Relationships tab and remove those relationships before deleting it.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            System.err.println("Error checking attribute source relationships: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Generic relationship checker for facets that have self-relationships
     * Filters out deleted objects (both source and target) for all facets
     */
    private JsonObject checkGenericRelationships(Connection conn, int objectId, String tableName, String sourceColumn, String targetColumn, String entityName) throws SQLException {
        // Determine the main table name, deleted column name, and ID column name based on entity type
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
                    result.addProperty("message", "🔗 " + entityName + " has " + count + " relationship" + (count > 1 ? "s" : "") + " with other " + entityName.toLowerCase() + " objects. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
                System.err.println("Error checking " + entityName + " relationships: " + e.getMessage());
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
                    result.addProperty("message", "🔗 " + entityName + " has " + count + " relationship" + (count > 1 ? "s" : "") + " with other " + entityName.toLowerCase() + " objects. Remove these relationships before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking " + entityName + " relationships: " + e.getMessage());
        }
        return null;
    }
    
    private JsonObject checkCapabilityRelationships(Connection conn, int capabilityId) throws SQLException {
        return checkGenericRelationships(conn, capabilityId, "capability_x_capability", "Source_ID", "Target_ID", "Capability");
    }
    
    private JsonObject checkCommitteeRelationships(Connection conn, int committeeId) throws SQLException {
        return checkGenericRelationships(conn, committeeId, "committee_x_committee", "Source_ID", "Target_ID", "Committee");
    }
    
    private JsonObject checkProcessRelationships(Connection conn, int processId) throws SQLException {
        return checkGenericRelationships(conn, processId, "process_x_process", "sourceprocess_id", "targetprocess_id", "Process");
    }
    
    private JsonObject checkProjectRelationships(Connection conn, int projectId) throws SQLException {
        return checkGenericRelationships(conn, projectId, "project_x_project", "sourceprojectid", "targetprojectid", "Project");
    }
    
    private JsonObject checkPolicyRelationships(Connection conn, int policyId) throws SQLException {
        return checkGenericRelationships(conn, policyId, "policy_x_policy", "sourceid", "targetid", "Policy");
    }
    
    private JsonObject checkRegulationRelationships(Connection conn, int regulationId) throws SQLException {
        // Use correct column names: SourceRegulationID and TargetRegulationID (no underscores)
        return checkGenericRelationships(conn, regulationId, "regulation_x_regulation", "SourceRegulationID", "TargetRegulationID", "Regulation");
    }
    
    /**
     * Check regulation regulator links
     * A regulation must not have links to any regulator
     */
    private JsonObject checkRegulationRegulatorLinks(Connection conn, int regulationId) throws SQLException {
        // Check for regulator links, filtering out deleted regulators
        String sql = "SELECT COUNT(*) FROM regulation_x_regulator rxr " +
                    "LEFT JOIN regulator r ON rxr.RegulatorID = r.ID " +
                    "WHERE rxr.RegulationID = ? " +
                    "AND (r.DeletedDatetime IS NULL OR r.DeletedDatetime = '1970-01-01 00:00:00')";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, regulationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    JsonObject result = new JsonObject();
                    result.addProperty("blocking", true);
                    result.addProperty("message", "🏛️ Regulation has " + count + " regulator link" + (count > 1 ? "s" : "") + ". Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
            System.err.println("Error checking regulation regulator links: " + e.getMessage());
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
                    result.addProperty("message", "📋 Regulatory Theme contains " + count + " regulation" + (count > 1 ? "s" : "") + ". Remove these regulations before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - ignore
        }
        return null;
    }
    
    /**
     * Check interface glossary links in Data Content Summary (summary tab)
     * This should be blocking like Impact tab
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
                        " in the Data Content Summary tab. Remove these links before deletion.");
                    return result;
                }
            }
        } catch (SQLException e) {
            // Table might not exist - log error for debugging
            System.err.println("Error checking interface-glossary data content: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Helper methods for table/column name mapping
     */
    private String getStakeholderForeignKeyColumn(String tableName) {
        // Map table names to their foreign key columns in stakeholder tables
        // Note: Column naming is inconsistent across tables
        switch (tableName) {
            case "system": return "SystemID";  // system_x_objectxpeople uses SystemID
            case "dataset": return "Dataset_ID";  // dataset_x_objectxpeople uses Dataset_ID
            case "attribute": return "AttributeID";  // attribute_x_objectxpeople uses AttributeID
            case "business_area": return "BusinessAreaID";  // businessarea_x_objectxpeople uses BusinessAreaID
            case "capability": return "CapabilityID";
            case "client": return "ClientID";
            case "committee": return "Committee_ID";
            case "geography": return "GeographyID";
            case "glossary": return "GlossaryID";
            case "legal": return "Legal_ID";
            case "org_unit": return "OrgUnitID";
            case "people": return "PeopleID";
            case "policy": return "Policy_ID";
            case "process": return "process_id";
            case "product": return "product_id";
            case "project": return "project_id";
            case "regulation": return "RegulationID";
            case "regulator": return "RegulatorID";
            case "regulatory_theme": return "RegulatoryThemeID";
            case "system_interface": return "SystemInterfaceID";
            default: 
                // Default: try camelCase ID format (most common)
                String[] parts = tableName.split("_");
                StringBuilder result = new StringBuilder();
                for (String part : parts) {
                    result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
                }
                result.append("ID");
                return result.toString();
        }
    }
    
    @SuppressWarnings("unused")
    private String[] getImpactRelationshipTables(String facetType) {
        // Return tables where this facet is the SOURCE (appears first in table name)
        // For example: glossary_x_process (glossary is source), process_x_system (process is source)
        switch (facetType) {
            case "system":
                // System is rarely the source, but check if there are any
                return new String[]{};
            case "dataset":
                // Dataset as source
                return new String[]{
                    "dataset_x_legal"
                };
            case "glossary":
                // Glossary as source
                return new String[]{
                    "glossary_x_process", "glossary_x_project", "glossary_x_system",
                    "glossary_x_dataset", "glossary_x_attribute"
                };
            case "process":
                // Process as source
                return new String[]{
                    "process_x_system", "process_x_product", "process_x_client",
                    "process_x_project", "process_x_policy", "process_x_interface",
                    "process_x_legal", "process_x_dataset"
                };
            case "project":
                // Project as source
                return new String[]{
                    "project_x_system", "project_x_process", "project_x_product",
                    "project_x_client", "project_x_policy", "project_x_capability",
                    "project_x_businessarea"
                };
            case "policy":
                // Policy as source
                return new String[]{
                    "policy_x_system", "policy_x_process", "policy_x_project",
                    "policy_x_dataset"
                };
            case "product":
                // Product as source
                return new String[]{
                    "product_x_system", "product_x_process", "product_x_project",
                    "product_x_glossary", "product_x_dataset"
                };
            case "client":
                // Client as source
                return new String[]{
                    "client_x_system", "client_x_process", "client_x_project",
                    "client_x_glossary", "client_x_dataset"
                };
            case "capability":
                // Capability as source
                return new String[]{
                    "capability_x_system"
                };
            case "business-area":
            case "businessarea":
                // Business area as source
                return new String[]{
                    "businessarea_x_system", "businessarea_x_process", "businessarea_x_glossary"
                };
            default:
                return new String[]{};
        }
    }
    
    @SuppressWarnings("unused")
    private String getRelationshipForeignKeyColumn(String facetType) {
        // This is a simplified version - the actual column name depends on the table
        // For impact checks, we use getImpactForeignKeyColumn which handles table-specific columns
        switch (facetType) {
            case "system": return "System_ID";
            case "dataset": return "Dataset_ID";
            case "business-area": return "BusinessArea_ID";
            case "glossary": return "Glossary_ID";
            default: 
                // Try to match common patterns
                String baseName = facetType.replace("-", "_");
                return baseName.substring(0, 1).toUpperCase() + baseName.substring(1) + "_ID";
        }
    }
    
    /**
     * Get the correct foreign key column for a specific impact relationship table
     * This handles table-specific column naming variations
     */
    private String getImpactForeignKeyColumn(String facetType, String tableName) {
        // Handle glossary-specific tables
        if (tableName.contains("_x_glossary") || tableName.contains("glossary_x_")) {
            if (tableName.equals("product_x_glossary")) {
                return "glossaryid"; // lowercase, no underscore
            }
            if (tableName.equals("client_x_glossary")) {
                return "Glossary_ID";
            }
            if (tableName.equals("policy_x_glossary")) {
                return "GlossaryID";
            }
            if (tableName.equals("businessarea_x_glossary") || tableName.equals("capability_x_glossary")) {
                return "Glossary_ID";
            }
            if (tableName.equals("glossary_x_process")) return "Glossary_ID";
            if (tableName.equals("glossary_x_project")) return "Glossary_ID";
            if (tableName.equals("glossary_x_system")) return "Glossary_ID";
            if (tableName.equals("glossary_x_dataset")) return "Glossary_ID";
            if (tableName.equals("glossary_x_attribute")) return "Glossary_ID";
        }
        
        // Handle system tables (where system is the TARGET - these should be in reverse tables, not source)
        // But we keep this for backward compatibility and to handle any edge cases
        if (tableName.contains("_x_system") && !tableName.startsWith("system_x_")) {
            if (tableName.equals("process_x_system")) return "system_id";
            if (tableName.equals("project_x_system")) return "systemid";
            if (tableName.equals("policy_x_system")) return "System_ID";
            if (tableName.equals("product_x_system")) return "systemid";
            if (tableName.equals("client_x_system")) return "System_ID";
            if (tableName.equals("capability_x_system")) return "System_ID";
            if (tableName.equals("businessarea_x_system")) return "System_ID";
            if (tableName.equals("glossary_x_system")) return "System_ID";
        }
        
        // Handle dataset tables (where dataset is the TARGET - these should be in reverse tables)
        // But we keep this for backward compatibility
        if (tableName.contains("_x_dataset") && !tableName.startsWith("dataset_x_")) {
            if (tableName.equals("process_x_dataset")) return "datasetid";
            if (tableName.equals("project_x_dataset")) return "dataset_id";
            if (tableName.equals("policy_x_dataset")) return "DatasetID";
            if (tableName.equals("product_x_dataset")) return "Dataset_ID";
            if (tableName.equals("client_x_dataset")) return "Dataset_ID";
            if (tableName.equals("glossary_x_dataset")) return "Dataset_ID";
        }
        // Handle dataset as source
        if (tableName.startsWith("dataset_x_")) {
            if (tableName.equals("dataset_x_legal")) return "Dataset_ID";
        }
        
        // Handle process tables (where process is the SOURCE)
        if (tableName.startsWith("process_x_")) {
            if (tableName.equals("process_x_system")) return "process_id";
            if (tableName.equals("process_x_product")) return "process_id";
            if (tableName.equals("process_x_client")) return "process_id";
            if (tableName.equals("process_x_project")) return "process_id";
            if (tableName.equals("process_x_policy")) return "process_id";
            if (tableName.equals("process_x_interface")) return "process_id";
            if (tableName.equals("process_x_legal")) return "process_id";
            if (tableName.equals("process_x_dataset")) return "process_id";
            if (tableName.equals("process_x_attribute")) return "processid";
        }
        
        // Handle project tables (where project is the SOURCE)
        if (tableName.startsWith("project_x_")) {
            if (tableName.equals("project_x_system")) return "projectid";
            if (tableName.equals("project_x_process")) return "projectid";
            if (tableName.equals("project_x_product")) return "projectid";
            if (tableName.equals("project_x_client")) return "projectid";
            if (tableName.equals("project_x_policy")) return "projectid";
            if (tableName.equals("project_x_capability")) return "Project_ID";
            if (tableName.equals("project_x_businessarea")) return "Project_ID";
        }
        
        // Handle glossary tables (where glossary is the SOURCE)
        if (tableName.startsWith("glossary_x_")) {
            if (tableName.equals("glossary_x_process")) return "Glossary_ID";
            if (tableName.equals("glossary_x_project")) return "Glossary_ID";
            if (tableName.equals("glossary_x_system")) return "Glossary_ID";
            if (tableName.equals("glossary_x_dataset")) return "Glossary_ID";
            if (tableName.equals("glossary_x_attribute")) return "Glossary_ID";
        }
        // Handle glossary as target (reverse relationships)
        if (tableName.contains("_x_glossary") && !tableName.startsWith("glossary_x_")) {
            if (tableName.equals("product_x_glossary")) return "glossaryid";
            if (tableName.equals("client_x_glossary")) return "Glossary_ID";
            if (tableName.equals("policy_x_glossary")) return "GlossaryID";
            if (tableName.equals("businessarea_x_glossary")) return "Glossary_ID";
            if (tableName.equals("capability_x_glossary")) return "Glossary_ID";
        }
        
        // Handle policy tables (where policy is the SOURCE)
        if (tableName.startsWith("policy_x_")) {
            if (tableName.equals("policy_x_system")) return "Policy_ID";
            if (tableName.equals("policy_x_process")) return "policy_id";
            if (tableName.equals("policy_x_project")) return "policy_id";
            if (tableName.equals("policy_x_dataset")) return "PolicyID";
        }
        
        // Handle product tables (where product is the SOURCE)
        if (tableName.startsWith("product_x_")) {
            if (tableName.equals("product_x_system")) return "Product_ID";
            if (tableName.equals("product_x_process")) return "productid";
            if (tableName.equals("product_x_project")) return "productid";
            if (tableName.equals("product_x_glossary")) return "productid";
            if (tableName.equals("product_x_dataset")) return "Product_ID";
        }
        
        // Handle client tables (where client is the SOURCE)
        if (tableName.startsWith("client_x_")) {
            if (tableName.equals("client_x_system")) return "Client_ID";
            if (tableName.equals("client_x_process")) return "Client_ID";
            if (tableName.equals("client_x_project")) return "Client_ID";
            if (tableName.equals("client_x_glossary")) return "Client_ID";
            if (tableName.equals("client_x_dataset")) return "Client_ID";
        }
        
        // Handle capability tables (where capability is the SOURCE)
        if (tableName.startsWith("capability_x_")) {
            if (tableName.equals("capability_x_system")) return "Capability_ID";
        }
        
        // Handle business area tables (where business area is the SOURCE)
        if (tableName.startsWith("businessarea_x_")) {
            if (tableName.equals("businessarea_x_system")) return "BusinessArea_ID";
            if (tableName.equals("businessarea_x_process")) return "BusinessArea_ID";
            if (tableName.equals("businessarea_x_glossary")) return "BusinessArea_ID";
        }
        
        // Handle dataset tables (where dataset is the SOURCE)
        if (tableName.startsWith("dataset_x_")) {
            if (tableName.equals("dataset_x_legal")) return "Dataset_ID";
        }
        
        // Handle interface tables (where interface is the SOURCE)
        if (tableName.startsWith("interface_x_")) {
            // Interface is rarely the source in impact tables, but add if needed
        }
        
        // Handle legal tables (where legal is the SOURCE)
        if (tableName.startsWith("legal_x_")) {
            // Legal is rarely the source in impact tables, but add if needed
        }
        
        // Handle attribute tables (where attribute is the SOURCE)
        if (tableName.startsWith("attribute_x_")) {
            // Attribute relationships are typically checked differently
        }
        
        // Handle policy tables
        if (tableName.contains("_x_policy")) {
            if (tableName.equals("policy_x_system")) return "Policy_ID";
            if (tableName.equals("policy_x_process")) return "policy_id";
            if (tableName.equals("policy_x_project")) return "policy_id";
            if (tableName.equals("policy_x_dataset")) return "PolicyID";
        }
        
        // Handle product tables (where product is the TARGET)
        if (tableName.contains("_x_product")) {
            // Note: product_x_system, product_x_process, product_x_project have product as SOURCE, not target
            // So they shouldn't be here. This section is for tables like capability_x_product, regulation_x_product
        }
        
        // Handle client tables
        if (tableName.contains("_x_client")) {
            if (tableName.equals("client_x_system")) return "Client_ID";
            if (tableName.equals("client_x_process")) return "Client_ID";
            if (tableName.equals("client_x_project")) return "Client_ID";
        }
        
        // Handle capability tables
        if (tableName.contains("_x_capability")) {
            if (tableName.equals("capability_x_system")) return "Capability_ID";
            if (tableName.equals("project_x_capability")) return "Capability_ID";
        }
        
        // Handle business area tables
        if (tableName.contains("_x_businessarea") || tableName.contains("businessarea_x_")) {
            if (tableName.equals("businessarea_x_system")) return "BusinessArea_ID";
            if (tableName.equals("businessarea_x_process")) return "BusinessArea_ID";
            if (tableName.equals("businessarea_x_glossary")) return "BusinessArea_ID";
            if (tableName.equals("project_x_businessarea")) return "BusinessArea_ID";
        }
        
        // Default: try to infer from facet name
        String baseName = facetType.replace("-", "_");
        if (baseName.equals("business_area") || baseName.equals("businessarea")) {
            return "BusinessArea_ID";
        }
        String capitalized = baseName.substring(0, 1).toUpperCase() + baseName.substring(1);
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
     * Get reverse impact relationship tables where this facet is the TARGET (appears second in table name)
     * For example, for glossary: product_x_glossary, client_x_glossary (where glossary is the target)
     * For system: process_x_system, project_x_system, etc. (where system is the target)
     * This ensures we check ALL impact relationships in BOTH directions for EVERY facet
     */
    @SuppressWarnings("unused")
    private String[] getReverseImpactRelationshipTables(String facetType) {
        switch (facetType) {
            case "system":
                // Tables where system is the target - ALL tables that reference system
                return new String[]{
                    "process_x_system", "project_x_system", "policy_x_system",
                    "product_x_system", "client_x_system", "capability_x_system",
                    "businessarea_x_system", "glossary_x_system"
                };
            case "dataset":
                // Tables where dataset is the target - ALL tables that reference dataset
                return new String[]{
                    "process_x_dataset", "project_x_dataset", "policy_x_dataset",
                    "product_x_dataset", "client_x_dataset", "glossary_x_dataset"
                };
            case "glossary":
                // Tables where glossary is the target - ALL tables that reference glossary
                return new String[]{
                    "product_x_glossary", "client_x_glossary", "businessarea_x_glossary"
                };
            case "process":
                // Tables where process is the target - ALL tables that reference process
                return new String[]{
                    "glossary_x_process", "project_x_process", "policy_x_process",
                    "businessarea_x_process"
                };
            case "project":
                // Tables where project is the target - ALL tables that reference project
                return new String[]{
                    "glossary_x_project", "process_x_project", "policy_x_project"
                };
            case "policy":
                // Tables where policy is the target - ALL tables that reference policy
                return new String[]{
                    "process_x_policy", "project_x_policy"
                };
            case "product":
                // Tables where product is the target - ALL tables that reference product
                return new String[]{
                    "process_x_product", "project_x_product"
                };
            case "client":
                // Tables where client is the target - ALL tables that reference client
                return new String[]{
                    "process_x_client", "project_x_client"
                };
            case "capability":
                // Tables where capability is the target - ALL tables that reference capability
                return new String[]{
                    "project_x_capability"
                };
            case "business-area":
            case "businessarea":
                // Tables where business area is the target - ALL tables that reference business area
                return new String[]{
                    "project_x_businessarea"
                };
            case "interface":
            case "system-interface":
                // Tables where interface is the target - ALL tables that reference interface
                return new String[]{
                    "process_x_interface"
                };
            case "legal":
            case "legal-entity":
                // Tables where legal entity is the target - ALL tables that reference legal entity
                return new String[]{
                    "process_x_legal", "dataset_x_legal"
                };
            case "attribute":
                // Tables where attribute is the target - ALL tables that reference attribute
                return new String[]{
                    "glossary_x_attribute", "process_x_attribute"
                };
            default:
                return new String[]{};
        }
    }
    
    /**
     * Get the foreign key column for reverse impact relationships (where facet is the target)
     * For example, in product_x_glossary, glossary is the target, so we need glossaryid column
     * In process_x_system, system is the target, so we need system_id or systemid column
     */
    private String getReverseImpactForeignKeyColumn(String facetType, String tableName) {
        // Handle system as target
        if (facetType.equals("system")) {
            if (tableName.equals("process_x_system")) return "system_id";
            if (tableName.equals("project_x_system")) return "systemid";
            if (tableName.equals("policy_x_system")) return "System_ID";
            if (tableName.equals("product_x_system")) return "systemid";
            if (tableName.equals("client_x_system")) return "System_ID";
            if (tableName.equals("capability_x_system")) return "System_ID";
            if (tableName.equals("businessarea_x_system")) return "System_ID";
            if (tableName.equals("glossary_x_system")) return "System_ID";
        }
        
        // Handle dataset as target
        if (facetType.equals("dataset")) {
            if (tableName.equals("process_x_dataset")) return "datasetid";
            if (tableName.equals("project_x_dataset")) return "dataset_id";
            if (tableName.equals("policy_x_dataset")) return "DatasetID";
            if (tableName.equals("product_x_dataset")) return "Dataset_ID";
            if (tableName.equals("client_x_dataset")) return "Dataset_ID";
            if (tableName.equals("glossary_x_dataset")) return "Dataset_ID";
        }
        
        // Handle glossary as target
        if (facetType.equals("glossary")) {
            if (tableName.equals("product_x_glossary")) {
                return "glossaryid"; // lowercase, no underscore
            }
            if (tableName.equals("client_x_glossary")) {
                return "Glossary_ID";
            }
            if (tableName.equals("policy_x_glossary")) {
                return "GlossaryID";
            }
            if (tableName.equals("businessarea_x_glossary")) {
                return "Glossary_ID";
            }
            if (tableName.equals("capability_x_glossary")) {
                return "Glossary_ID";
            }
        }
        
        // Handle process as target
        if (facetType.equals("process")) {
            if (tableName.equals("glossary_x_process")) {
                return "Process_ID";
            }
            if (tableName.equals("project_x_process")) {
                return "process_id";
            }
            if (tableName.equals("policy_x_process")) {
                return "process_id";
            }
            if (tableName.equals("businessarea_x_process")) {
                return "Process_ID";
            }
        }
        
        // Handle project as target
        if (facetType.equals("project")) {
            if (tableName.equals("glossary_x_project")) {
                return "Project_ID";
            }
            if (tableName.equals("process_x_project")) {
                return "projectid";
            }
            if (tableName.equals("policy_x_project")) {
                return "project_id";
            }
        }
        
        // Handle policy as target
        if (facetType.equals("policy")) {
            if (tableName.equals("process_x_policy")) {
                return "policy_id";
            }
            if (tableName.equals("project_x_policy")) {
                return "policy_id";
            }
        }
        
        // Handle product as target
        if (facetType.equals("product")) {
            if (tableName.equals("process_x_product")) {
                return "productid";
            }
            if (tableName.equals("project_x_product")) {
                return "productid";
            }
        }
        
        // Handle client as target
        if (facetType.equals("client")) {
            if (tableName.equals("process_x_client")) {
                return "Client_ID";
            }
            if (tableName.equals("project_x_client")) {
                return "Client_ID";
            }
        }
        
        // Handle capability as target
        if (facetType.equals("capability")) {
            if (tableName.equals("project_x_capability")) {
                return "Capability_ID";
            }
        }
        
        // Handle business area as target
        if (facetType.equals("business-area") || facetType.equals("businessarea")) {
            if (tableName.equals("project_x_businessarea")) {
                return "BusinessArea_ID";
            }
        }
        
        // Handle interface as target
        if (facetType.equals("interface") || facetType.equals("system-interface")) {
            if (tableName.equals("process_x_interface")) {
                return "interface_id";
            }
        }
        
        // Handle legal entity as target
        if (facetType.equals("legal") || facetType.equals("legal-entity")) {
            if (tableName.equals("process_x_legal")) {
                return "Legal_ID";
            }
            if (tableName.equals("dataset_x_legal")) {
                return "Legal_ID";
            }
        }
        
        // Handle attribute as target
        if (facetType.equals("attribute")) {
            if (tableName.equals("glossary_x_attribute")) {
                return "Attribute_ID";
            }
            if (tableName.equals("process_x_attribute")) {
                return "attributeid";
            }
        }
        
        // Default: try to infer from facet name
        String baseName = facetType.replace("-", "_");
        if (baseName.equals("business_area") || baseName.equals("businessarea")) {
            return "BusinessArea_ID";
        }
        String capitalized = baseName.substring(0, 1).toUpperCase() + baseName.substring(1);
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
        // For reverse relationships, sometimes the column is lowercase
        // Try lowercase version first for common patterns
        if (tableName.contains("_x_" + baseName.toLowerCase())) {
            return baseName.toLowerCase() + "id";
        }
        return capitalized + "_ID";
    }
    
    /**
     * Get user-friendly display name for entity type
     */
    private String getEntityDisplayName(String facetType) {
        switch (facetType) {
            case "system": return "System";
            case "dataset": return "Data Set";
            case "business-area": return "Business Area";
            case "capability": return "Capability";
            case "client": return "Client";
            case "committee": return "Committee";
            case "geography": return "Geography";
            case "glossary": return "Glossary";
            case "legal-entity": return "Legal Entity";
            case "org-unit": return "Organizational Unit";
            case "people": return "Person";
            case "policy": return "Policy";
            case "process": return "Process";
            case "product": return "Product";
            case "project": return "Project";
            case "regulation": return "Regulation";
            case "regulator": return "Regulator";
            case "regulatory-theme": return "Regulatory Theme";
            case "system-interface": return "System Interface";
            default:
                // Convert kebab-case to Title Case
                String[] parts = facetType.split("-");
                StringBuilder result = new StringBuilder();
                for (String part : parts) {
                    if (result.length() > 0) result.append(" ");
                    result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
                }
                return result.toString();
        }
    }
    
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        response.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Configuration class for facet deletion settings
     */
    private static class FacetConfig {
        final String tableName;
        @SuppressWarnings("unused")
        final String deleteColumn;
        final String statusColumn;
        final String parentColumn;
        final boolean hasStatus;
        final String stakeholderTable;
        @SuppressWarnings("unused")
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
