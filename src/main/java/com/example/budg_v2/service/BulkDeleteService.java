package com.example.budg_v2.service;

import com.example.budg_v2.bulk.objects.BulkDeleteValidationHelper;
import com.example.budg_v2.config.BulkUpdateDefinitionConfig;
import com.example.budg_v2.dao.BulkDeleteDAO;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for Bulk Delete operations.
 * Handles validation and execution of bulk delete operations.
 */
public class BulkDeleteService {

    private final BulkDeleteDAO bulkDeleteDAO;
    private final BulkDeleteValidationHelper validationHelper;

    // Error messages
    public static final String ERROR_UNAUTHORIZED = "You do not have permission to perform bulk delete operations.";
    public static final String ERROR_EXCLUDED_FACET = "Bulk delete is not available for this facet.";
    public static final String ERROR_NO_SELECTION = "No items selected for deletion.";
    public static final String ERROR_WORKFLOW = "Cannot delete the object because it is under workflow.";
    public static final String ERROR_ACTIVE_CR = "Cannot delete the object because it has ongoing change requests.";
    public static final String ERROR_STAKEHOLDERS = "The object has linked stakeholders. Unlink and try again.";
    
    // Blocking error messages
    public static final String ERROR_STATUS_NOT_DELETED = "Object must be in 'Deleted' status before deletion.";
    public static final String ERROR_IMPACT_RELATIONS = "This object has active impact relations and cannot be deleted.";
    public static final String ERROR_GEOGRAPHY_LINKS = "Geography object is linked to regulations or regulators and cannot be deleted.";
    public static final String ERROR_SYSTEM_DATASETS_NOT_DELETED = "This system is linked to one or more datasets for which the BUDG Status is not set to Deleted.";
    
    // Warning messages
    public static final String WARNING_STAKEHOLDERS = "This object has stakeholders assigned. Do you want to proceed?";
    public static final String WARNING_CHILD_OBJECTS = "This object has child objects. Do you want to unlink all dependencies and proceed?";
    public static final String WARNING_SYSTEM_DATASETS = "This system is linked to one or more datasets. Do you want to delete the datasets first?";
    
    /**
     * Warning type enum - must match frontend
     */
    public enum WarningType {
        STAKEHOLDERS,
        CHILD_OBJECTS,
        SYSTEM_DATASETS
    }

    public BulkDeleteService() {
        this.bulkDeleteDAO = new BulkDeleteDAO();
        this.validationHelper = new BulkDeleteValidationHelper();
    }

    /**
     * Result class for bulk delete operation
     */
    public static class BulkDeleteResult {
        private boolean success;
        private int totalRows;
        private int deletedRows;
        private int skippedRows;
        private List<String> skippedReasons;
        private String message;
        private String error;
        private List<ItemResult> itemResults;

        public BulkDeleteResult() {
            this.success = true;
            this.totalRows = 0;
            this.deletedRows = 0;
            this.skippedRows = 0;
            this.skippedReasons = new ArrayList<>();
            this.itemResults = new ArrayList<>();
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getTotalRows() {
            return totalRows;
        }

        public void setTotalRows(int totalRows) {
            this.totalRows = totalRows;
        }

        public int getDeletedRows() {
            return deletedRows;
        }

        public void setDeletedRows(int deletedRows) {
            this.deletedRows = deletedRows;
        }

        public int getSkippedRows() {
            return skippedRows;
        }

        public void setSkippedRows(int skippedRows) {
            this.skippedRows = skippedRows;
        }

        public List<String> getSkippedReasons() {
            return skippedReasons;
        }

        public void addSkippedReason(String reason) {
            this.skippedReasons.add(reason);
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
            this.success = false;
        }

        public List<ItemResult> getItemResults() {
            return itemResults;
        }

        public void addItemResult(ItemResult result) {
            this.itemResults.add(result);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("success", success);
            map.put("totalRows", totalRows);
            map.put("deletedRows", deletedRows);
            map.put("skippedRows", skippedRows);
            map.put("skippedReasons", skippedReasons);
            if (message != null)
                map.put("message", message);
            if (error != null)
                map.put("error", error);
            if (itemResults != null)
                map.put("results", itemResults);
            return map;
        }
    }

    /**
     * Result for individual item deletion
     */
    public static class ItemResult {
        private int id;
        private boolean deleted;
        private String error;

        public ItemResult(int id, boolean deleted, String error) {
            this.id = id;
            this.deleted = deleted;
            this.error = error;
        }

        public int getId() {
            return id;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public String getError() {
            return error;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("deleted", deleted);
            if (error != null)
                map.put("error", error);
            return map;
        }
    }

    /**
     * Check if user is Admin or Super Admin (can access bulk delete at all).
     */
    public boolean isUserAdmin(int userId) {
        if (userId <= 0) return false;
        try {
            return new PermissionService().isAdminOrSuperAdmin(userId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if user is Super Admin (only Super Admin can perform final soft delete).
     * Admin/WebUser can only set status to Deleted.
     */
    public boolean isUserSuperAdmin(int userId) {
        if (userId <= 0) return false;
        try {
            return SegmentAccessService.isSuperAdmin(userId);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Validate bulk delete request before execution.
     * Any authenticated user may call; Web User is restricted to updateStatusToDeleted per object in executeBulkDelete.
     */
    public BulkDeleteResult validateRequest(String facet, List<Integer> objectIds, int userId) {
        BulkDeleteResult result = new BulkDeleteResult();
        result.setTotalRows(objectIds != null ? objectIds.size() : 0);

        // Check if facet is excluded
        if (BulkUpdateDefinitionConfig.isFacetExcludedForBulkDelete(facet)) {
            result.setError(ERROR_EXCLUDED_FACET);
            return result;
        }

        // Check if objects selected
        if (objectIds == null || objectIds.isEmpty()) {
            result.setError(ERROR_NO_SELECTION);
            return result;
        }

        return result;
    }

    /**
     * Execute bulk delete operation with confirmations.
     * SuperAdmin: performs final soft delete (set Deleted_datetime).
     * Admin: only sets status to Deleted (no Deleted_datetime); facets without status are skipped.
     * WebUser: only sets status to Deleted for objects they have permission on (segment + facet); others skipped.
     *
     * @param facet Facet type
     * @param objectIds List of object IDs to delete
     * @param userId User performing the deletion
     * @param isSuperAdmin true if user is Super Admin (can perform final delete)
     * @param isWebUser true if user is Web User (per-object permission check required)
     * @param confirmations Map of objectId -> Map of warning type -> confirmed
     */
    public BulkDeleteResult executeBulkDelete(String facet, List<Integer> objectIds, int userId, boolean isSuperAdmin,
                                             boolean isWebUser, Map<Integer, Map<WarningType, Boolean>> confirmations) {
        BulkDeleteResult result = new BulkDeleteResult();
        result.setTotalRows(objectIds.size());

        String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
        if (tableName == null) {
            result.setError("Unknown facet: " + facet);
            return result;
        }

        int deletedCount = 0;
        int skippedCount = 0;
        PermissionService permissionService = isWebUser ? new PermissionService() : null;

        try (Connection conn = DatabaseConnection.getConnection()) {
            for (Integer objectId : objectIds) {
                // Each object in separate transaction (BUDG-style)
                    conn.setAutoCommit(false);
                try {
                    // Web User: must have segment access and Edit permission on this facet for this object
                    if (isWebUser && permissionService != null) {
                        try {
                            if (!SegmentAccessService.canAccessObject(userId, objectId, facet)) {
                                skippedCount++;
                                String err = "No permission on this object (segment access).";
                                result.addSkippedReason("Object " + objectId + ": " + err);
                                result.addItemResult(new ItemResult(objectId, false, err));
                                conn.rollback();
                                continue;
                            }
                            if (!permissionService.canEdit(userId, facet)) {
                                skippedCount++;
                                String err = "No permission on this object (facet).";
                                result.addSkippedReason("Object " + objectId + ": " + err);
                                result.addItemResult(new ItemResult(objectId, false, err));
                                conn.rollback();
                                continue;
                            }
                        } catch (SQLException e) {
                            skippedCount++;
                            String err = "Permission check failed: " + e.getMessage();
                            result.addSkippedReason("Object " + objectId + ": " + err);
                            result.addItemResult(new ItemResult(objectId, false, err));
                            conn.rollback();
                            continue;
                        }
                    }

                    // Validate object (SuperAdmin final delete requires status=Deleted; Admin/WebUser mark-as-Deleted does not)
                    ValidationResult validation = validateObjectForDeletion(conn, facet, tableName, objectId, isSuperAdmin);
                    
                    // Check blocking errors
                    if (!validation.isCanDelete()) {
                        skippedCount++;
                        String errorMsg = validation.getBlockingErrors().isEmpty()
                            ? "Validation failed"
                            : String.join("; ", validation.getBlockingErrors());
                        result.addSkippedReason("Object " + objectId + ": " + errorMsg);
                        result.addItemResult(new ItemResult(objectId, false, errorMsg));
                        conn.rollback();
                        continue;
                    }
                    
                    // Check if all warnings are confirmed
                    // If no warnings exist, proceed directly to deletion
                    if (validation.getWarnings().isEmpty()) {
                        // No warnings - proceed directly to deletion
                    } else {
                        // Warnings exist - check if all are confirmed
                        Map<WarningType, Boolean> objectConfirmations = confirmations != null 
                            ? confirmations.get(objectId) 
                            : null;
                        
                        boolean allWarningsConfirmed = true;
                        for (Warning warning : validation.getWarnings()) {
                            WarningType type = warning.getType();
                            boolean confirmed = objectConfirmations != null 
                                && Boolean.TRUE.equals(objectConfirmations.get(type));
                            
                            if (!confirmed) {
                                allWarningsConfirmed = false;
                                break;
                            }
                        }
                        
                        if (!allWarningsConfirmed) {
                            // User didn't confirm all warnings - skip object
                            skippedCount++;
                            result.addSkippedReason("Object " + objectId + ": User did not confirm all warnings");
                            result.addItemResult(new ItemResult(objectId, false, "User did not confirm all warnings"));
                            conn.rollback();
                            continue;
                        }
                        
                        // All warnings confirmed - perform actions
                        for (Warning warning : validation.getWarnings()) {
                            WarningType type = warning.getType();
                            
                            switch (type) {
                                case STAKEHOLDERS:
                                    bulkDeleteDAO.removeStakeholders(conn, facet, objectId);
                                    break;
                                case CHILD_OBJECTS:
                                    bulkDeleteDAO.unlinkChildObjects(conn, facet, tableName, objectId);
                                    break;
                                case SYSTEM_DATASETS:
                                    // Delete datasets first
                                    bulkDeleteDAO.deleteSystemDatasets(conn, objectId);
                                    break;
                            }
                        }
                    }
                    
                    // SuperAdmin: final soft delete (set Deleted_datetime). Admin/WebUser: only set status to Deleted.
                    boolean done;
                    if (isSuperAdmin) {
                        done = bulkDeleteDAO.performSoftDelete(conn, facet, objectId);
                    } else {
                        if (!bulkDeleteDAO.facetHasStatus(facet)) {
                            skippedCount++;
                            String error = "Only Super Admin can delete this object type.";
                            result.addSkippedReason("Object " + objectId + ": " + error);
                            result.addItemResult(new ItemResult(objectId, false, error));
                            conn.rollback();
                            continue;
                        }
                        done = bulkDeleteDAO.updateStatusToDeleted(conn, facet, objectId);
                    }
                    if (done) {
                        deletedCount++;
                        result.addItemResult(new ItemResult(objectId, true, null));
                        conn.commit();
                    } else {
                        skippedCount++;
                        String error = "Object not found or already deleted";
                        result.addSkippedReason("Object " + objectId + ": " + error);
                        result.addItemResult(new ItemResult(objectId, false, error));
                        conn.rollback();
                    }

                } catch (SQLException e) {
                    conn.rollback();
                    skippedCount++;
                    String error = "Database error: " + e.getMessage();
                    result.addSkippedReason("Object " + objectId + ": " + error);
                    result.addItemResult(new ItemResult(objectId, false, error));
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            result.setError("Database error: " + e.getMessage());
            return result;
        }

        result.setDeletedRows(deletedCount);
        result.setSkippedRows(skippedCount);
        
        if (deletedCount > 0) {
            result.setMessage("Successfully deleted " + deletedCount + " item(s).");
        }
        if (skippedCount > 0) {
            if (result.getMessage() != null) {
                result.setMessage(result.getMessage() + " " + skippedCount + " item(s) were skipped.");
            } else {
                result.setMessage(skippedCount + " item(s) were skipped.");
            }
        }

        return result;
    }
    
    /**
     * Execute bulk delete operation (legacy method without confirmations).
     * Uses transaction-per-row strategy - if one row fails, skip and continue.
     */
    public BulkDeleteResult executeBulkDelete(String facet, List<Integer> objectIds, int userId) {
        return executeBulkDelete(facet, objectIds, userId, isUserSuperAdmin(userId), !isUserAdmin(userId), null);
    }

    /**
     * Validation result for a single object
     */
    public static class ValidationResult {
        private int id;
        private String name;
        private boolean canDelete;
        private List<String> blockingErrors;
        private List<Warning> warnings;
        
        public ValidationResult(int id) {
            this.id = id;
            this.name = null;
            this.canDelete = true;
            this.blockingErrors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }
        
        public int getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public boolean isCanDelete() {
            return canDelete;
        }
        
        public void setCanDelete(boolean canDelete) {
            this.canDelete = canDelete;
        }
        
        public List<String> getBlockingErrors() {
            return blockingErrors;
        }
        
        public void addBlockingError(String error) {
            this.blockingErrors.add(error);
            this.canDelete = false; // Blocking errors prevent deletion
        }
        
        public List<Warning> getWarnings() {
            return warnings;
        }
        
        public void addWarning(WarningType type, String message) {
            this.warnings.add(new Warning(type, message));
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("name", name != null ? name : "Unknown");
            map.put("canDelete", canDelete);
            map.put("blockingErrors", blockingErrors);
            List<Map<String, Object>> warningsList = new ArrayList<>();
            for (Warning w : warnings) {
                Map<String, Object> wMap = new HashMap<>();
                wMap.put("type", w.getType().name());
                wMap.put("message", w.getMessage());
                warningsList.add(wMap);
            }
            map.put("warnings", warningsList);
            return map;
        }
    }
    
    /**
     * Warning class
     */
    public static class Warning {
        private WarningType type;
        private String message;
        
        public Warning(WarningType type, String message) {
            this.type = type;
            this.message = message;
        }
        
        public WarningType getType() {
            return type;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * Pre-validate all objects before deletion
     * Returns validation results for each object
     */
    public Map<String, Object> preValidateBulkDelete(String facet, List<Integer> objectIds) {
        Map<String, Object> result = new HashMap<>();
        List<ValidationResult> validationResults = new ArrayList<>();
        
        String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
        if (tableName == null) {
            result.put("error", "Unknown facet: " + facet);
            return result;
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (Integer objectId : objectIds) {
                // Pre-validate with requireDeletedStatus=true so UI shows "must be Deleted" for final delete
                ValidationResult validation = validateObjectForDeletion(conn, facet, tableName, objectId, true);
                validationResults.add(validation);
            }
        } catch (SQLException e) {
            result.put("error", "Database error during validation: " + e.getMessage());
            return result;
        }
        
        // Build summary
        int total = validationResults.size();
        int canDeleteCount = 0;
        int hasWarningsCount = 0;
        int blockedCount = 0;
        
        for (ValidationResult vr : validationResults) {
            if (vr.isCanDelete()) {
                canDeleteCount++;
                if (!vr.getWarnings().isEmpty()) {
                    hasWarningsCount++;
                }
            } else {
                blockedCount++;
            }
        }
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("canDelete", canDeleteCount);
        summary.put("hasWarnings", hasWarningsCount);
        summary.put("blocked", blockedCount);
        
        List<Map<String, Object>> resultsList = new ArrayList<>();
        for (ValidationResult vr : validationResults) {
            resultsList.add(vr.toMap());
        }
        
        result.put("validationResults", resultsList);
        result.put("summary", summary);
        
        return result;
    }
    
    /**
     * Validate object can be deleted.
     * requireDeletedStatus: true for SuperAdmin (final delete) = object must already have status Deleted; false for Admin (mark as Deleted) = any status allowed.
     */
    private ValidationResult validateObjectForDeletion(Connection conn, String facet, String tableName, int objectId, boolean requireDeletedStatus) throws SQLException {
        BulkDeleteValidationHelper.ValidationResult helperResult = validationHelper.validateObjectForDeletion(conn, facet, objectId, requireDeletedStatus);
        
        // Convert to BulkDeleteService.ValidationResult format
        ValidationResult result = new ValidationResult(objectId);
        
        // Copy object name
        if (helperResult.getObjectName() != null) {
            result.setName(helperResult.getObjectName());
        }
        
        // Copy errors (blocking)
        for (String error : helperResult.getErrors()) {
            result.addBlockingError(error);
        }
        
        // Copy warnings
        for (String warning : helperResult.getWarnings()) {
            // Map warning messages to warning types based on content
            String lowerWarning = warning.toLowerCase();
            if (lowerWarning.contains("stakeholder")) {
                result.addWarning(WarningType.STAKEHOLDERS, warning);
            } else if (lowerWarning.contains("child") || lowerWarning.contains("unlinked")) {
                result.addWarning(WarningType.CHILD_OBJECTS, warning);
            } else if (lowerWarning.contains("dataset") || lowerWarning.contains("data set")) {
                result.addWarning(WarningType.SYSTEM_DATASETS, warning);
            } else {
                // Generic warning - default to STAKEHOLDERS type
                result.addWarning(WarningType.STAKEHOLDERS, warning);
            }
        }
        
        return result;
    }
    
    /**
     * Normalize facet name
     */
    private String normalizeFacet(String facet) {
        if (facet == null) return "";
        return facet.toLowerCase().trim().replace("_", "-");
    }
    
    /**
     * Validate object can be deleted (legacy method - kept for backward compatibility)
     * Returns error message if cannot be deleted, null if OK
     */
    private String validateObjectForDeletionLegacy(Connection conn, String facet, String tableName, int objectId) throws SQLException {
        // Check if under workflow (has active_cr_id)
        if (bulkDeleteDAO.hasActiveChangeRequest(conn, tableName, objectId)) {
            return ERROR_ACTIVE_CR;
        }

        // Check if has linked stakeholders
        if (bulkDeleteDAO.hasLinkedStakeholders(conn, facet, objectId)) {
            return ERROR_STAKEHOLDERS;
        }

        return null; // OK to delete
    }
}


