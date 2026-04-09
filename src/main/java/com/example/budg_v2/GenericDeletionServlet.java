package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.util.ActivityLogHelper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.model.SystemSettings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic servlet for handling soft deletion of objects
 * Performs soft delete by setting deletedatetime column to NOW()
 */
@WebServlet("/api/delete/*")
public class GenericDeletionServlet extends HttpServlet {
    
    private static final Gson gson = new Gson();
    
    // Facet configuration for deletion
    private static final Map<String, DeletionConfig> DELETION_CONFIGS = new HashMap<>();
    
    static {
        // System configuration
        DELETION_CONFIGS.put("system", new DeletionConfig(
            "system", "Deleted_datetime", "id"
        ));
        
        // Dataset configuration
        DELETION_CONFIGS.put("dataset", new DeletionConfig(
            "dataset", "DeletedDatetime", "ID"
        ));
        
        // Business Area configuration
        DELETION_CONFIGS.put("business-area", new DeletionConfig(
            "business_area", "deletedatetime", "ID"
        ));
        
        // Capability configuration
        DELETION_CONFIGS.put("capability", new DeletionConfig(
            "capability", "DeletedDatetime", "ID"
        ));
        
        // Client configuration
        DELETION_CONFIGS.put("client", new DeletionConfig(
            "client", "DeleteDatetime", "ID"
        ));
        
        // Committee configuration
        DELETION_CONFIGS.put("committee", new DeletionConfig(
            "committee", "DeleteDatetime", "ID"
        ));
        
        // Geography configuration
        DELETION_CONFIGS.put("geography", new DeletionConfig(
            "geography", "DeletedDatetime", "ID"
        ));
        
        // Glossary configuration
        DELETION_CONFIGS.put("glossary", new DeletionConfig(
            "glossary", "Deleted_datetime", "ID"
        ));
        
        // Legal Entity configuration
        DELETION_CONFIGS.put("legal-entity", new DeletionConfig(
            "legal", "DeleteDatetime", "ID"
        ));
        
        // Org Unit configuration
        DELETION_CONFIGS.put("org-unit", new DeletionConfig(
            "org_unit", "deleted_Date", "ID"
        ));
        
        // People configuration
        DELETION_CONFIGS.put("people", new DeletionConfig(
            "people", "Deleted_date", "ID"
        ));
        
        // Policy configuration
        DELETION_CONFIGS.put("policy", new DeletionConfig(
            "policy", "DeletedDatetime", "ID"
        ));
        
        // Process configuration
        DELETION_CONFIGS.put("process", new DeletionConfig(
            "process", "deleteddatetime", "id"
        ));
        
        // Product configuration
        DELETION_CONFIGS.put("product", new DeletionConfig(
            "product", "deleteddatetime", "id"
        ));
        
        // Project configuration
        DELETION_CONFIGS.put("project", new DeletionConfig(
            "project", "deletedatetime", "id"
        ));
        
        // Regulation configuration
        DELETION_CONFIGS.put("regulation", new DeletionConfig(
            "regulation", "DeletedDatetime", "ID"
        ));
        
        // Regulator configuration
        DELETION_CONFIGS.put("regulator", new DeletionConfig(
            "regulator", "DeletedDatetime", "ID"
        ));
        
        // Regulatory Theme configuration
        DELETION_CONFIGS.put("regulatory-theme", new DeletionConfig(
            "regulatorytheme", "DeletedDatetime", "ID"
        ));
        
        // System Interface configuration
        DELETION_CONFIGS.put("system-interface", new DeletionConfig(
            "interface", "deleted_datetime", "id"
        ));
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        // V-10: Soft (and permanent) delete must be restricted server-side.
        // Only Super Admin can delete objects. Regular Admins cannot delete.
        // (AuthFilter already enforces DELETE method restrictions, but we add
        //  an explicit server-side check here as defense-in-depth.)
        int userId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(request);
        boolean isSuperAdmin = false;
        try {
            isSuperAdmin = com.example.budg_v2.service.SegmentAccessService.isSuperAdmin(userId);
        } catch (java.sql.SQLException e) {
            System.err.println("Error checking Super Admin status: " + e.getMessage());
        }
        if (!isSuperAdmin) {
            sendError(response, 403, "Forbidden: Only Super Administrators can delete objects");
            return;
        }

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
            
            // Get current user for audit trail
            String userName = getCurrentUserName(request);
            
            // Capture old state before deletion
            Map<String, Object> oldState = getObjectStateBeforeDeletion(facetType, objectId);
            
            DeletionResult deletionResult = performSoftDelete(facetType, objectId, userName);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", deletionResult.success);
            String entityName = getEntityDisplayName(facetType);
            
            if (deletionResult.success) {
                // Log activity - Delete case
                if (oldState != null) {
                    String facetName = getFacetNameFromType(facetType);
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_DELETED_OBJECTS,
                        facetName, ActivityLogConstants.CHANGE_TYPE_DELETE,
                        oldState, null);
                }
                
                String message = "✓ " + entityName + " has been removed successfully";
                if (deletionResult.datasetsDeleted > 0) {
                    message += " (including " + deletionResult.datasetsDeleted + " data set" + 
                              (deletionResult.datasetsDeleted > 1 ? "s" : "") + ")";
                }
                if (deletionResult.attributesDeleted > 0) {
                    message += " (including " + deletionResult.attributesDeleted + " attribute" + 
                              (deletionResult.attributesDeleted > 1 ? "s" : "") + ")";
                }
                result.addProperty("message", message);
                if (deletionResult.datasetsDeleted > 0) {
                    result.addProperty("datasetsDeleted", deletionResult.datasetsDeleted);
                }
                if (deletionResult.attributesDeleted > 0) {
                    result.addProperty("attributesDeleted", deletionResult.attributesDeleted);
                }
            } else {
                result.addProperty("message", "✕ Failed to remove " + entityName.toLowerCase());
            }
            
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(gson.toJson(result));
            
        } catch (NumberFormatException e) {
            sendError(response, 400, "Invalid object ID format");
        } catch (SQLException e) {
            // Check if this is a CR validation error (blocking deletion)
            if (e.getMessage() != null && e.getMessage().contains("Cannot delete person")) {
                sendError(response, 400, e.getMessage());
            } else {
                System.err.println("Deletion error: " + e.getMessage());
                e.printStackTrace();
                sendError(response, 500, "Internal server error during deletion");
            }
        } catch (Exception e) {
            System.err.println("Deletion error: " + e.getMessage());
            e.printStackTrace();
            sendError(response, 500, "Internal server error during deletion");
        }
    }
    
    /**
     * Result class for deletion operation
     */
    private static class DeletionResult {
        boolean success;
        int datasetsDeleted;
        int attributesDeleted;
        
        DeletionResult(boolean success, int datasetsDeleted, int attributesDeleted) {
            this.success = success;
            this.datasetsDeleted = datasetsDeleted;
            this.attributesDeleted = attributesDeleted;
        }
    }
    
    /**
     * Perform soft delete by setting deletedatetime to NOW()
     */
    private DeletionResult performSoftDelete(String facetType, int objectId, String userName) throws SQLException {
        DeletionConfig config = DELETION_CONFIGS.get(facetType);
        if (config == null) {
            throw new IllegalArgumentException("Unsupported facet type: " + facetType);
        }
        
        // Defense-in-depth: Block deletion of people who have raised active CRs (Running or Pending Start)
        if ("people".equals(facetType)) {
            FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
            if (facetChangesDAO.hasActiveCRsCreatedByPerson(objectId)) {
                throw new SQLException("Cannot delete person: This person has a Running or Pending Start change request. Please complete or cancel the change request first.");
            }
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Step 1: Perform soft delete
                // Wrap column names in backticks to handle spaces and special characters
                String deleteColumn = config.deleteColumn.contains(" ") ? "`" + config.deleteColumn + "`" : config.deleteColumn;
                String idColumn = config.idColumn.contains(" ") ? "`" + config.idColumn + "`" : config.idColumn;
                String deleteSql = "UPDATE " + config.tableName + " SET " + deleteColumn + " = NOW() WHERE " + idColumn + " = ?";
                
                //system.out.println("Executing soft delete SQL: " + deleteSql);
                //system.out.println("Parameters: facetType=" + facetType + ", objectId=" + objectId + ", tableName=" + config.tableName + ", deleteColumn=" + config.deleteColumn + ", idColumn=" + config.idColumn);
                
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, objectId);
                    int affectedRows = deleteStmt.executeUpdate();
                    
                    //system.out.println("Soft delete affected rows: " + affectedRows);
                    
                    if (affectedRows == 0) {
                        throw new SQLException("No object found with ID: " + objectId + " in table " + config.tableName);
                    }
                }
                
                // Step 2: If deleting a system, also soft delete all its datasets
                int datasetsDeleted = 0;
                if ("system".equals(facetType)) {
                    datasetsDeleted = softDeleteSystemDatasets(conn, objectId, userName);
                }
                
                // Step 2b: If deleting a dataset, also soft delete all its attributes
                int attributesDeleted = 0;
                if ("dataset".equals(facetType)) {
                    attributesDeleted = softDeleteDatasetAttributes(conn, objectId, userName);
                }
                
                // Step 3: Delete associated change requests if automatic deletion is enabled
                deleteAssociatedChangeRequests(conn, facetType, objectId);
                
                // Step 4: Create audit record if audit table exists
                createAuditRecord(conn, config, objectId, userName);
                
                conn.commit();
                //system.out.println("Successfully soft deleted " + facetType + " with ID: " + objectId);
                return new DeletionResult(true, datasetsDeleted, attributesDeleted);
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    /**
     * Soft delete all datasets belonging to a system
     * @return Number of datasets deleted
     */
    private int softDeleteSystemDatasets(Connection conn, int systemId, String userName) throws SQLException {
        // First, get all dataset IDs that will be deleted
        String getDatasetIdsSql = "SELECT ID FROM dataset WHERE MasterSource = ? AND DeletedDatetime IS NULL";
        java.util.List<Integer> datasetIds = new java.util.ArrayList<>();
        
        try (PreparedStatement getIdsStmt = conn.prepareStatement(getDatasetIdsSql)) {
            getIdsStmt.setInt(1, systemId);
            try (var rs = getIdsStmt.executeQuery()) {
                while (rs.next()) {
                    datasetIds.add(rs.getInt("ID"));
                }
            }
        }
        
        if (datasetIds.isEmpty()) {
            //system.out.println("No datasets to delete for system ID: " + systemId);
            return 0;
        }
        
        // Soft delete all datasets where MasterSource = systemId and not already deleted
        String deleteDatasetsSql = "UPDATE dataset SET DeletedDatetime = NOW() WHERE MasterSource = ? AND DeletedDatetime IS NULL";
        
        try (PreparedStatement deleteDatasetsStmt = conn.prepareStatement(deleteDatasetsSql)) {
            deleteDatasetsStmt.setInt(1, systemId);
            int datasetsDeleted = deleteDatasetsStmt.executeUpdate();
            
            //system.out.println("Soft deleted " + datasetsDeleted + " dataset(s) for system ID: " + systemId);
            
            // Create audit records for each deleted dataset
            for (int datasetId : datasetIds) {
                createDatasetAuditRecord(conn, datasetId, userName);
            }
            
            return datasetsDeleted;
        }
    }
    
    /**
     * Soft delete all attributes belonging to a dataset
     * @return Number of attributes deleted
     */
    private int softDeleteDatasetAttributes(Connection conn, int datasetId, String userName) throws SQLException {
        // First, get all attribute IDs that will be deleted
        String getAttributeIdsSql = "SELECT ID FROM attribute WHERE Dataset_ID = ? AND DeletedDatetime IS NULL";
        java.util.List<Integer> attributeIds = new java.util.ArrayList<>();
        
        try (PreparedStatement getIdsStmt = conn.prepareStatement(getAttributeIdsSql)) {
            getIdsStmt.setInt(1, datasetId);
            try (var rs = getIdsStmt.executeQuery()) {
                while (rs.next()) {
                    attributeIds.add(rs.getInt("ID"));
                }
            }
        }
        
        if (attributeIds.isEmpty()) {
            //system.out.println("No attributes to delete for dataset ID: " + datasetId);
            return 0;
        }
        
        // Soft delete all attributes where Dataset_ID = datasetId and not already deleted
        String deleteAttributesSql = "UPDATE attribute SET DeletedDatetime = NOW() WHERE Dataset_ID = ? AND DeletedDatetime IS NULL";
        
        try (PreparedStatement deleteAttributesStmt = conn.prepareStatement(deleteAttributesSql)) {
            deleteAttributesStmt.setInt(1, datasetId);
            int attributesDeleted = deleteAttributesStmt.executeUpdate();
            
            //system.out.println("Soft deleted " + attributesDeleted + " attribute(s) for dataset ID: " + datasetId);
            
            // Create audit records for each deleted attribute
            for (int attributeId : attributeIds) {
                createAttributeAuditRecord(conn, attributeId, userName);
            }
            
            return attributesDeleted;
        }
    }
    
    /**
     * Create audit record for dataset deletion
     */
    private void createDatasetAuditRecord(Connection conn, int datasetId, String userName) {
        try {
            String auditSql = "INSERT INTO dataset_audit_history (id, object, event, updateType, field, `from`, `to`, author) " +
                             "VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)";
            
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                auditStmt.setInt(1, datasetId);
                auditStmt.setString(2, "Dataset");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Dataset");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                //system.out.println("Created audit record for dataset deletion ID: " + datasetId);
            }
        } catch (SQLException e) {
            // Audit record creation is not critical - log but don't fail the deletion
            //system.out.println("Could not create audit record for dataset ID " + datasetId + ": " + e.getMessage());
        }
    }
    
    /**
     * Delete associated change requests based on system settings
     * @return Number of change requests deleted
     */
    private int deleteAssociatedChangeRequests(Connection conn, String facetType, int objectId) {
        try {
            // Check if automatic deletion is enabled
            SystemSettingsDAO settingsDAO = new SystemSettingsDAO();
            SystemSettings autoDeleteSetting = settingsDAO.getSetting("Change Requests", "enable_automatic_deletion");
            
            if (autoDeleteSetting == null || 
                !("true".equalsIgnoreCase(autoDeleteSetting.getSettingValue()) || 
                  "1".equals(autoDeleteSetting.getSettingValue()))) {
                // Automatic deletion is disabled
                return 0;
            }
            
            // Get days to automatically delete
            SystemSettings daysSetting = settingsDAO.getSetting("Change Requests", "days_to_automatically_delete");
            int daysToDelete = 0;
            if (daysSetting != null && daysSetting.getSettingValue() != null) {
                try {
                    daysToDelete = Integer.parseInt(daysSetting.getSettingValue());
                } catch (NumberFormatException e) {
                    daysToDelete = 0;
                }
            }
            
            // Build the reference string based on facet type
            String reference = buildChangeRequestReference(facetType, objectId);
            if (reference == null) {
                // Unsupported facet type for change requests
                return 0;
            }
            
            if (daysToDelete == 0) {
                // Immediate deletion
                String deleteSql = "UPDATE changerequest " +
                                 "SET Deleted_At = NOW(), " +
                                 "    scheduled_delete_at = NULL " +
                                 "WHERE Reference = ? " +
                                 "  AND Deleted_At IS NULL";
                
                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setString(1, reference);
                    int deleted = stmt.executeUpdate();
                    return deleted;
                }
            } else {
                // Delayed deletion - set scheduled_delete_at
                String scheduleSql = "UPDATE changerequest " +
                                    "SET scheduled_delete_at = DATE_ADD(NOW(), INTERVAL ? DAY) " +
                                    "WHERE Reference = ? " +
                                    "  AND Deleted_At IS NULL " +
                                    "  AND scheduled_delete_at IS NULL";
                
                try (PreparedStatement stmt = conn.prepareStatement(scheduleSql)) {
                    stmt.setInt(1, daysToDelete);
                    stmt.setString(2, reference);
                    int scheduled = stmt.executeUpdate();
                    return scheduled;
                }
            }
            
        } catch (Exception e) {
            // Log error but don't fail the deletion
            System.err.println("Error deleting change requests for " + facetType + " " + objectId + ": " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Build the change request reference string based on facet type
     */
    private String buildChangeRequestReference(String facetType, int objectId) {
        switch (facetType) {
            case "glossary":
                return "Glossary " + objectId;
            case "system":
                return "System " + objectId;
            case "dataset":
                return "Data Set " + objectId;
            case "process":
                return "Process " + objectId;
            case "product":
                return "Product " + objectId;
            case "project":
                return "Project " + objectId;
            case "policy":
                return "Policy " + objectId;
            case "client":
                return "Client " + objectId;
            case "committee":
                return "Committee " + objectId;
            case "capability":
                return "Capability " + objectId;
            case "business-area":
                return "Business Area " + objectId;
            case "legal-entity":
                return "Legal Entity " + objectId;
            case "regulation":
                return "Regulation " + objectId;
            case "regulator":
                return "Regulator " + objectId;
            case "org-unit":
                return "Org Unit " + objectId;
            case "geography":
                return "Geography " + objectId;
            case "people":
                return "People " + objectId;
            case "system-interface":
                return "Interface " + objectId;
            default:
                return null;
        }
    }
    
    /**
     * Create audit record for attribute deletion
     */
    private void createAttributeAuditRecord(Connection conn, int attributeId, String userName) {
        try {
            String auditSql = "INSERT INTO attribute_audit_history (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                             "VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, NOW(), NOW())";
            
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                auditStmt.setInt(1, attributeId);
                auditStmt.setString(2, "Attribute");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Attribute");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                //system.out.println("Created audit record for attribute deletion ID: " + attributeId);
            }
        } catch (SQLException e) {
            // Audit record creation is not critical - log but don't fail the deletion
            //system.out.println("Could not create audit record for attribute ID " + attributeId + ": " + e.getMessage());
        }
    }
    
    /**
     * Create audit record for deletion
     */
    private void createAuditRecord(Connection conn, DeletionConfig config, int objectId, String userName) {
        try {
            String auditTableName = getAuditTableName(config.tableName);
            String auditSql = "INSERT INTO " + auditTableName + " (id, object, event, updateType, field, `from`, `to`, author) " +
                             "VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)";
            
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                auditStmt.setInt(1, objectId);
                auditStmt.setString(2, capitalizeFirst(config.tableName));
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, capitalizeFirst(config.tableName));
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                //system.out.println("Created audit record for deletion of " + config.tableName + " ID: " + objectId);
            }
        } catch (SQLException e) {
            // Audit record creation is not critical - log but don't fail the deletion
            //system.out.println("Could not create audit record for " + config.tableName + ": " + e.getMessage());
        }
    }
    
    /**
     * Get audit table name for a given table
     */
    private String getAuditTableName(String tableName) {
        // Table name differs from pattern (DB uses regulatory_theme_audit_history, not regulatorytheme_audit_history)
        if ("regulatorytheme".equalsIgnoreCase(tableName)) {
            return "regulatory_theme_audit_history";
        }
        // Most audit tables follow the pattern: tablename_audit_history
        return tableName + "_audit_history";
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
    
    /**
     * Capitalize first letter of string
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Get current user name from session or request
     */
    /**
     * Get object state before deletion for logging
     */
    private Map<String, Object> getObjectStateBeforeDeletion(String facetType, int objectId) {
        try {
            DeletionConfig config = DELETION_CONFIGS.get(facetType);
            if (config == null) {
                return null;
            }
            
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Get object name and description based on facet type
                String nameColumn = getObjectNameColumn(facetType);
                String descriptionColumn = getObjectDescriptionColumn(facetType);
                
                if (nameColumn == null) {
                    return null;
                }
                
                String sql = "SELECT " + config.idColumn + " AS id";
                if (nameColumn != null) {
                    sql += ", " + nameColumn + " AS name";
                }
                if (descriptionColumn != null) {
                    sql += ", " + descriptionColumn + " AS description";
                }
                sql += " FROM " + config.tableName + " WHERE " + config.idColumn + " = ?";
                
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, objectId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Map<String, Object> state = new HashMap<>();
                            state.put("id", rs.getInt("id"));
                            if (nameColumn != null) {
                                state.put("name", rs.getString("name"));
                            }
                            if (descriptionColumn != null) {
                                state.put("description", rs.getString("description"));
                            }
                            return state;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting object state before deletion: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get object name column based on facet type
     */
    private String getObjectNameColumn(String facetType) {
        switch (facetType) {
            case "system": return "PrimaryName";
            case "dataset": return "PrimaryName";
            case "business-area": return "PrimaryName";
            case "capability": return "PrimaryName";
            case "client": return "PrimaryName";
            case "committee": return "PrimaryName";
            case "geography": return "PrimaryName";
            case "glossary": return "Name";
            case "legal-entity": return "ShortName";
            case "org-unit": return "PrimaryName";
            case "people": return "CONCAT(First_Name, ' ', Last_Name)";
            case "policy": return "PrimaryName";
            case "process": return "PrimaryName";
            case "product": return "PrimaryName";
            case "project": return "PrimaryName";
            case "regulation": return "PrimaryName";
            case "regulator": return "PrimaryName";
            case "regulatory-theme": return "PrimaryName";
            case "system-interface": return "PrimaryName";
            default: return null;
        }
    }
    
    /**
     * Get object description column based on facet type
     */
    private String getObjectDescriptionColumn(String facetType) {
        switch (facetType) {
            case "system": return "Description";
            case "dataset": return "Definition";
            case "business-area": return "Description";
            case "capability": return "Description";
            case "client": return "Description";
            case "committee": return "Description";
            case "geography": return "Description";
            case "glossary": return "Description";
            case "legal-entity": return "Description";
            case "org-unit": return "Description";
            case "people": return null; // People don't have description
            case "policy": return "Description";
            case "process": return "Description";
            case "product": return "Description";
            case "project": return "Description";
            case "regulation": return "Description";
            case "regulator": return "Description";
            case "regulatory-theme": return "Description";
            case "system-interface": return "Description";
            default: return null;
        }
    }
    
    /**
     * Get facet name from facet type for logging
     */
    private String getFacetNameFromType(String facetType) {
        return getEntityDisplayName(facetType);
    }
    
    private String getCurrentUserName(HttpServletRequest request) {
        // Try to get user from session
        try {
            Object userObj = request.getSession().getAttribute("user");
            if (userObj != null) {
                // Assuming user object has a getName() method or similar
                return userObj.toString();
            }
        } catch (Exception e) {
            //system.out.println("Could not get user from session: " + e.getMessage());
        }
        
        // Fallback to a default user name
        return "System";
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
     * Configuration class for deletion settings
     */
    private static class DeletionConfig {
        final String tableName;
        final String deleteColumn;
        final String idColumn;
        
        DeletionConfig(String tableName, String deleteColumn, String idColumn) {
            this.tableName = tableName;
            this.deleteColumn = deleteColumn;
            this.idColumn = idColumn;
        }
    }
}
