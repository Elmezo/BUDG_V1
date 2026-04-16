package com.example.budg_v2.service;

import com.example.budg_v2.config.BulkUpdateDefinitionConfig;
import com.example.budg_v2.config.BulkUpdateDefinitionConfig.DefinitionField;
import com.example.budg_v2.dao.BulkUpdateDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.DFCR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Service layer for Bulk Update operations.
 * Implements business logic, validations, and transaction management.
 */
public class BulkUpdateService {

    private static final Logger logger = LoggerFactory.getLogger(BulkUpdateService.class);
    
    private final BulkUpdateDAO bulkUpdateDAO;
    private final SegmentDAO segmentDAO;
    private final FacetChangesDAO facetChangesDAO;
    @SuppressWarnings("unused") // reserved for change-request integration
    private final ChangeRequestDAO changeRequestDAO;
    private final DFCRService dfcrService;
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    // Error messages matching BUDG
    public static final String ERROR_MUTUALLY_EXCLUSIVE = "Cannot update the Segment field together with System or Glossary fields for a dataset.";
    public static final String ERROR_PARENT_SEGMENT_MUTUALLY_EXCLUSIVE = "Cannot update the Parent field and the Segment field at the same time for an object. Please clear the other field first.";
    public static final String ERROR_NO_SELECTION = "No selected rows";
    public static final String ERROR_MIXED_FACETS = "Please select objects from the same facet only";
    public static final String ERROR_EXCLUDED_FACET = "Bulk Update is not available for this facet";
    public static final String ERROR_UNAUTHORIZED = "You do not have permission to perform Bulk Update";
    public static final String SUCCESS_MESSAGE = "The items have been updated.";

    public BulkUpdateService() {
        this.bulkUpdateDAO = new BulkUpdateDAO();
        this.segmentDAO = new SegmentDAO();
        this.facetChangesDAO = new FacetChangesDAO();
        this.changeRequestDAO = new ChangeRequestDAO();
        this.dfcrService = new DFCRService();
    }

    /**
     * Result object for bulk update operations
     */
    public static class BulkUpdateResult {
        private int totalRows;
        private int updatedRows;
        private int skippedRows;
        private List<String> skippedReasons;
        private boolean success;
        private String message;
        private String error;

        public BulkUpdateResult() {
            this.skippedReasons = new ArrayList<>();
            this.success = true;
        }

        // Getters and setters
        public int getTotalRows() {
            return totalRows;
        }

        public void setTotalRows(int totalRows) {
            this.totalRows = totalRows;
        }

        public int getUpdatedRows() {
            return updatedRows;
        }

        public void setUpdatedRows(int updatedRows) {
            this.updatedRows = updatedRows;
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

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
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

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("success", success);
            map.put("totalRows", totalRows);
            map.put("updatedRows", updatedRows);
            map.put("skippedRows", skippedRows);
            map.put("skippedReasons", skippedReasons);
            if (message != null)
                map.put("message", message);
            if (error != null)
                map.put("error", error);
            return map;
        }
    }

    /**
     * Validate bulk update request before execution
     */
    public BulkUpdateResult validateRequest(String facet, List<Integer> objectIds, Map<String, Object> updates,
            int userId, boolean isAdmin) {
        BulkUpdateResult result = new BulkUpdateResult();
        result.setTotalRows(objectIds.size());

        // Check authorization
        if (!isAdmin) {
            result.setError(ERROR_UNAUTHORIZED);
            return result;
        }

        // Check if facet is excluded
        if (BulkUpdateDefinitionConfig.isFacetExcluded(facet)) {
            result.setError(ERROR_EXCLUDED_FACET);
            return result;
        }

        // Check if objects selected
        if (objectIds == null || objectIds.isEmpty()) {
            result.setError(ERROR_NO_SELECTION);
            return result;
        }

        // Check for mutually exclusive fields (Dataset only)
        if (facet.equalsIgnoreCase("dataset")) {
            String mutuallyExclusiveError = checkMutuallyExclusiveFields(updates);
            if (mutuallyExclusiveError != null) {
                result.setError(mutuallyExclusiveError);
                return result;
            }
        }

        return result;
    }

    /**
     * Check if updates contain mutually exclusive fields for Dataset
     * System and Glossary can be updated together, but Segment is mutually exclusive with both
     */
    private String checkMutuallyExclusiveFields(Map<String, Object> updates) {
        if (updates == null)
            return null;

        // Check if segment is selected with system or glossary
        boolean hasSegment = updates.containsKey("segment") && updates.get("segment") != null;
        boolean hasSystem = updates.containsKey("system_short_name") && updates.get("system_short_name") != null;
        boolean hasGlossary = updates.containsKey("glossary_name") && updates.get("glossary_name") != null;

        if (hasSegment && (hasSystem || hasGlossary)) {
            return ERROR_MUTUALLY_EXCLUSIVE;
        }
        return null;
    }

    /**
     * Check if updates contain both Parent and Segment fields (mutually exclusive)
     */
    @SuppressWarnings("unused")
    private String checkParentSegmentMutuallyExclusive(Map<String, Object> updates) {
        if (updates == null)
            return null;

        boolean hasParent = updates.containsKey("parent") && 
                           updates.get("parent") != null && 
                           !updates.get("parent").toString().trim().isEmpty();
        
        Object segmentValue = updates.get("segment");
        if (segmentValue == null) {
            segmentValue = updates.get("segment_id");
        }
        boolean hasSegment = segmentValue != null && 
                            !segmentValue.toString().trim().isEmpty();

        if (hasParent && hasSegment) {
            return ERROR_PARENT_SEGMENT_MUTUALLY_EXCLUSIVE;
        }
        return null;
    }

    /**
     * Execute bulk update operation
     * Uses transaction-per-row strategy - if one row fails, skip and continue
     */
    public BulkUpdateResult executeBulkUpdate(String facet, List<Integer> objectIds, Map<String, Object> updates,
            int userId) {
        BulkUpdateResult result = new BulkUpdateResult();
        result.setTotalRows(objectIds.size());

        // If no definition selected or no value, return success without changes
        if (updates == null || updates.isEmpty()) {
            result.setMessage(SUCCESS_MESSAGE);
            result.setUpdatedRows(0);
            return result;
        }

        String tableName = BulkUpdateDefinitionConfig.getTableForFacet(facet);
        if (tableName == null) {
            result.setError("Unknown facet: " + facet);
            return result;
        }

        int updatedCount = 0;
        int skippedCount = 0;

        // Special handling for role operations
        if (facet.equalsIgnoreCase("role")) {
            return executeRoleBulkUpdate(objectIds, updates, userId);
        }

        // Check if facet has default CR enabled for editing
        DFCR dfcr = dfcrService.getSettingsForFacet(facet);
        boolean hasDefaultCREnabled = dfcr != null && 
            dfcr.isWorkflowApprovalEnabled() && 
            dfcr.getWorkflowEditId() != null;
        
        // Get facet ID for CR queries
        Integer facetId = facetChangesDAO.getFacetId(facet);
        if (facetId == null) {
            // Try alternative facet name formats
            String normalizedFacet = normalizeFacetNameForDFCR(facet);
            facetId = facetChangesDAO.getFacetId(normalizedFacet);
        }
        
        logger.info("[BulkUpdate] Facet: {}, hasDefaultCREnabled: {}, facetId: {}", 
            facet, hasDefaultCREnabled, facetId);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (Integer objectId : objectIds) {
                logger.debug("[BulkUpdate] Processing objectId={}", objectId);
                // Transaction per row
                conn.setAutoCommit(false);
                try {
                    // Determine target object ID (original or cloned) based on CR status
                    int targetObjectId = getTargetObjectId(conn, facet, facetId, objectId, userId, hasDefaultCREnabled);
                    
                    if (targetObjectId != objectId) {
                        logger.info("[BulkUpdate] Object {} has active CR - updating cloned row {}", objectId, targetObjectId);
                    } else {
                        logger.debug("[BulkUpdate] Object {} - updating original row", objectId);
                    }

                    // Special handling for Dataset mutually exclusive fields
                    // System and Glossary can be updated together, but Segment is mutually exclusive with both
                    // Note: Segment operations should always use original objectId, not cloned
                    if (facet.equalsIgnoreCase("dataset")) {
                        boolean hasSystem = updates.containsKey("system_short_name") && updates.get("system_short_name") != null;
                        boolean hasGlossary = updates.containsKey("glossary_name") && updates.get("glossary_name") != null;
                        boolean hasSegment = updates.containsKey("segment") && updates.get("segment") != null;
                        
                        // When updating System or Glossary (or both together), clear Segment only
                        if (hasSystem || hasGlossary) {
                            try {
                                int currentSegmentId = segmentDAO.getObjectSegmentId(objectId, "Dataset");
                                // If object is in a segment other than Enterprise (1), remove it
                                // Use original objectId for segment operations
                                if (currentSegmentId > 1) {
                                    segmentDAO.removeObjectFromSegment(currentSegmentId, objectId, "Dataset", userId);
                                }
                            } catch (SQLException e) {
                                // Log error but continue with update
                                logger.warn("Error removing segment assignment for dataset {}: {}", objectId, e.getMessage());
                            }
                        }
                        // When updating Segment, clear both System and Glossary
                        else if (hasSegment) {
                            // Use targetObjectId for field updates (may be cloned row)
                            bulkUpdateDAO.updateField(conn, tableName, targetObjectId, "MasterSource", null, userId);
                            bulkUpdateDAO.updateField(conn, tableName, targetObjectId, "glossary", null, userId);
                        }
                    }

                    // Apply each update
                    boolean rowUpdated = false;
                    for (Map.Entry<String, Object> update : updates.entrySet()) {
                        String fieldId = update.getKey();
                        Object value = update.getValue();

                        DefinitionField field = BulkUpdateDefinitionConfig.getFieldById(facet, fieldId);
                        if (field == null)
                            continue;

                        // Type conversion for LOOKUP and REFERENCE
                        if (value != null && (field.getFieldType() == BulkUpdateDefinitionConfig.FieldType.LOOKUP
                                || field.getFieldType() == BulkUpdateDefinitionConfig.FieldType.REFERENCE)) {
                            try {
                                if (value instanceof String) {
                                    value = Integer.parseInt((String) value);
                                } else if (value instanceof Number) {
                                    value = ((Number) value).intValue();
                                }
                            } catch (Exception e) {
                                // Fallback to original value if parsing fails
                            }
                        }

                        // Parent / hierarchy segment rules (same as facet edit pages)
                        if ("parent".equals(fieldId) && value != null) {
                            int newParentId = -1;
                            if (value instanceof Number) {
                                newParentId = ((Number) value).intValue();
                            } else {
                                try {
                                    newParentId = Integer.parseInt(value.toString().trim());
                                } catch (NumberFormatException e) {
                                    newParentId = -1;
                                }
                            }
                            if (newParentId > 0) {
                                String objectTypeForSegment = normalizeFacetForSegment(facet);
                                int childSegmentId = segmentDAO.getObjectSegmentId(objectId, objectTypeForSegment);
                                SegmentValidationService.ValidationResult parentSegResult =
                                        segmentValidationService.validateParentChildSegment(
                                                newParentId, childSegmentId, objectTypeForSegment);
                                if (!parentSegResult.isValid && !parentSegResult.canProceedWithWarning) {
                                    throw new SQLException(parentSegResult.message);
                                }
                            }
                        }

                        // Special handling for Segment assignment
                        // Segment operations should always use original objectId, not cloned
                        if (field.getFieldId().equals("segment") || field.getFieldId().equals("segment_id")) {
                            if (value instanceof Integer) {
                                String objectTypeForSegment = normalizeFacetForSegment(facet);
                                segmentDAO.assignObjectToSegment((Integer) value, objectId, objectTypeForSegment,
                                        userId);
                                rowUpdated = true;
                                // Log audit for segment (always use original objectId)
                                bulkUpdateDAO.logAudit(conn, facet, objectId, field.getDisplayName(), null, value,
                                        userId, "BULK_UPDATE");
                                continue;
                            }
                        }

                        // Special handling for People lifecycle (stored in people_details table)
                        // People operations should always use original objectId
                        if (facet.equalsIgnoreCase("people") && fieldId.equals("lifecycle")) {
                            // Get old value from people_details for audit
                            Object oldValue = getPeopleDetailsLifecycle(conn, objectId);
                            
                            // Update people_details.lifecycle
                            Integer lifecycleId = null;
                            if (value instanceof Integer) {
                                lifecycleId = (Integer) value;
                            } else if (value instanceof Number) {
                                lifecycleId = ((Number) value).intValue();
                            }
                            
                            updatePeopleDetailsLifecycle(conn, objectId, lifecycleId, userId);
                            rowUpdated = true;
                            
                            // Log audit (always use original objectId)
                            bulkUpdateDAO.logAudit(conn, facet, objectId, field.getDisplayName(), oldValue, value,
                                    userId, "BULK_UPDATE");
                            continue;
                        }

                        String columnName = field.getColumnName();
                        if (columnName == null)
                            continue;

                        // Get old value for audit (use targetObjectId which may be cloned row)
                        Object oldValue = bulkUpdateDAO.getFieldValue(conn, tableName, targetObjectId, columnName);

                        // Update the field (use targetObjectId which may be cloned row)
                        boolean updated = bulkUpdateDAO.updateField(conn, tableName, targetObjectId, columnName, value,
                                userId);
                        if (updated) {
                            rowUpdated = true;
                            // Log audit (use original objectId for audit trail, but note if cloned)
                            bulkUpdateDAO.logAudit(conn, facet, objectId, field.getDisplayName(), oldValue, value,
                                    userId, "BULK_UPDATE");
                        }
                    }

                    if (rowUpdated) {
                        String normFacet = facet != null ? facet.toLowerCase().replace("-", "") : "";
                        if ("dataset".equals(normFacet)) {
                            validateDatasetSegmentConsistencyAfterBulk(conn, targetObjectId, objectId, userId);
                        } else if ("attribute".equals(normFacet) || "attributes".equals(normFacet)) {
                            validateAttributeSegmentConsistencyAfterBulk(conn, targetObjectId, userId);
                        }
                        updatedCount++;
                    }
                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    skippedCount++;
                    result.addSkippedReason("Object " + objectId + ": " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            result.setError("Database error: " + e.getMessage());
            return result;
        }

        result.setUpdatedRows(updatedCount);
        result.setSkippedRows(skippedCount);
        result.setMessage(SUCCESS_MESSAGE);
        return result;
    }

    /**
     * After bulk column updates on a dataset row, enforce the same segment rules as {@link DatasetServlet}
     * (system vs segment, glossary vs segment, public vs private system).
     */
    private void validateDatasetSegmentConsistencyAfterBulk(Connection conn, int targetObjectId, int logicalDatasetId,
            int userId) throws SQLException {
        Integer masterSource = null;
        Integer glossary = null;
        Integer accessControl = null;
        String sql = "SELECT MasterSource, glossary, AccessControlType FROM dataset WHERE ID = ? AND DeletedDatetime IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, targetObjectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object ms = rs.getObject(1);
                    masterSource = (ms != null) ? ((Number) ms).intValue() : null;
                    Object gl = rs.getObject(2);
                    glossary = (gl != null) ? ((Number) gl).intValue() : null;
                    Object ac = rs.getObject(3);
                    accessControl = (ac != null) ? ((Number) ac).intValue() : null;
                }
            }
        }
        boolean datasetIsPublic = accessControl != null && accessControl == 1;
        int segmentId = segmentDAO.getObjectSegmentId(logicalDatasetId, "Dataset");
        if (masterSource != null && masterSource > 0) {
            SegmentValidationService.ValidationResult sysResult =
                    segmentValidationService.validateDatasetSystemSegment(masterSource, segmentId, userId);
            if (!sysResult.isValid) {
                throw new SQLException(sysResult.message);
            }
            SegmentValidationService.ValidationResult visResult =
                    segmentValidationService.validateDatasetVisibility(logicalDatasetId, masterSource, datasetIsPublic);
            if (!visResult.isValid) {
                throw new SQLException(visResult.message);
            }
        }
        if (glossary != null && glossary > 0) {
            SegmentValidationService.ValidationResult gloResult =
                    segmentValidationService.validateDatasetGlossarySegment(glossary, segmentId, datasetIsPublic, userId);
            if (!gloResult.isValid) {
                throw new SQLException(gloResult.message);
            }
        }
    }

    /**
     * After bulk updates on an attribute row, enforce attribute↔glossary segment rules (same as {@link com.example.budg_v2.AttributeServlet}).
     */
    private void validateAttributeSegmentConsistencyAfterBulk(Connection conn, int targetAttributeId, int userId)
            throws SQLException {
        Integer datasetId = null;
        Integer glossaryId = null;
        String attrSql = "SELECT Dataset_ID, Glossary_ID FROM attribute WHERE ID = ? AND DeletedDatetime IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(attrSql)) {
            ps.setInt(1, targetAttributeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object d = rs.getObject(1);
                    datasetId = (d != null) ? ((Number) d).intValue() : null;
                    Object g = rs.getObject(2);
                    glossaryId = (g != null) ? ((Number) g).intValue() : null;
                }
            }
        }
        if (datasetId == null || datasetId <= 0 || glossaryId == null || glossaryId <= 0) {
            return;
        }
        boolean datasetIsPublic = false;
        String dsSql = "SELECT AccessControlType FROM dataset WHERE ID = ? AND DeletedDatetime IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(dsSql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object ac = rs.getObject(1);
                    datasetIsPublic = ac != null && ((Number) ac).intValue() == 1;
                }
            }
        }
        SegmentValidationService.ValidationResult r =
                segmentValidationService.validateAttributeGlossarySegment(datasetId, glossaryId, datasetIsPublic);
        if (!r.isValid) {
            throw new SQLException(r.message);
        }
    }

    /**
     * Execute role-specific bulk update operations
     */
    private BulkUpdateResult executeRoleBulkUpdate(List<Integer> objectXPeopleIds, Map<String, Object> updates,
            int userId) {
        BulkUpdateResult result = new BulkUpdateResult();
        result.setTotalRows(objectXPeopleIds.size());

        // Validate that only one role operation is selected
        int operationCount = 0;
        if (updates.containsKey("accept_roles") && Boolean.TRUE.equals(updates.get("accept_roles"))) {
            operationCount++;
        }
        if (updates.containsKey("reassign_to") && updates.get("reassign_to") != null) {
            operationCount++;
        }
        if (updates.containsKey("change_status") && updates.get("change_status") != null) {
            operationCount++;
        }
        if (updates.containsKey("delete_roles") && Boolean.TRUE.equals(updates.get("delete_roles"))) {
            operationCount++;
        }

        if (operationCount > 1) {
            result.setError("Only one role operation can be performed at a time. Please select only one operation.");
            return result;
        }

        int updatedCount = 0;
        int skippedCount = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            for (Integer oxpId : objectXPeopleIds) {
                conn.setAutoCommit(false);
                try {
                    boolean rowUpdated = false;

                    // Accept roles
                    if (updates.containsKey("accept_roles") && Boolean.TRUE.equals(updates.get("accept_roles"))) {
                        if (bulkUpdateDAO.acceptRole(conn, oxpId, userId)) {
                            rowUpdated = true;
                            bulkUpdateDAO.logAudit(conn, "role", oxpId, "Accept Roles", "No", "Yes", userId,
                                    "BULK_UPDATE");
                        }
                    }

                    // Reassign roles
                    if (updates.containsKey("reassign_to")) {
                        Object newPersonIdObj = updates.get("reassign_to");
                        if (newPersonIdObj != null) {
                            int newPersonId = (newPersonIdObj instanceof Integer) ? (Integer) newPersonIdObj
                                    : Integer.parseInt(newPersonIdObj.toString());
                            if (bulkUpdateDAO.reassignRole(conn, oxpId, newPersonId, userId)) {
                                rowUpdated = true;
                                bulkUpdateDAO.logAudit(conn, "role", oxpId, "Reassign Roles To", null,
                                        String.valueOf(newPersonId), userId, "BULK_UPDATE");
                            }
                        }
                    }

                    // Change status
                    if (updates.containsKey("change_status")) {
                        Object newStatusIdObj = updates.get("change_status");
                        if (newStatusIdObj != null) {
                            int newStatusId = (newStatusIdObj instanceof Integer) ? (Integer) newStatusIdObj
                                    : Integer.parseInt(newStatusIdObj.toString());
                            if (bulkUpdateDAO.changeRoleStatus(conn, oxpId, newStatusId, userId)) {
                                rowUpdated = true;
                                bulkUpdateDAO.logAudit(conn, "role", oxpId, "Change Role Status", null,
                                        String.valueOf(newStatusId), userId, "BULK_UPDATE");
                            }
                        }
                    }

                    // Delete roles (last operation)
                    if (updates.containsKey("delete_roles") && Boolean.TRUE.equals(updates.get("delete_roles"))) {
                        bulkUpdateDAO.logAudit(conn, "role", oxpId, "Delete Roles", "Active", "Deleted", userId,
                                "BULK_UPDATE");
                        if (bulkUpdateDAO.deleteRole(conn, oxpId, userId)) {
                            rowUpdated = true;
                        }
                    }

                    if (rowUpdated) {
                        updatedCount++;
                    }
                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    skippedCount++;
                    result.addSkippedReason("Role " + oxpId + ": " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            result.setError("Database error: " + e.getMessage());
            return result;
        }

        result.setUpdatedRows(updatedCount);
        result.setSkippedRows(skippedCount);
        result.setMessage(SUCCESS_MESSAGE);
        return result;
    }

    /**
     * Get definition fields for a facet
     */
    public List<Map<String, Object>> getDefinitions(String facet) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<DefinitionField> fields = BulkUpdateDefinitionConfig.getDefinitionsForFacet(facet);

        for (DefinitionField field : fields) {
            Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.put("fieldId", field.getFieldId());
            fieldMap.put("displayName", field.getDisplayName());
            fieldMap.put("lookupTable", field.getLookupTable());
            fieldMap.put("fieldType", field.getFieldType().name());
            result.add(fieldMap);
        }
        return result;
    }

    /**
     * Get lookup values for a field
     */
    public List<Map<String, Object>> getLookupValues(String facet, String fieldId) {
        DefinitionField field = BulkUpdateDefinitionConfig.getFieldById(facet, fieldId);
        if (field == null || field.getLookupTable() == null) {
            return Collections.emptyList();
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Special handling for certain lookups
            if (field.getLookupTable().equals("people")) {
                return bulkUpdateDAO.getPeopleValues(conn);
            } else if (field.getLookupTable().equals("role_status")) {
                return bulkUpdateDAO.getRoleStatusValues(conn);
            }
            return bulkUpdateDAO.getLookupValues(conn, field.getLookupTable());
        } catch (SQLException e) {
            System.err.println("Error getting lookup values: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get role data with extended columns
     */
    public List<Map<String, Object>> getRoleDataWithExtendedColumns(List<Integer> objectXPeopleIds) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return bulkUpdateDAO.getRoleDataWithExtendedColumns(conn, objectXPeopleIds);
        } catch (SQLException e) {
            System.err.println("Error getting role data: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get object info for items display, including segment information
     */
    public List<Map<String, Object>> getObjectsInfo(String facet, List<Integer> objectIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String objectType = normalizeFacetForSegment(facet);
            for (Integer id : objectIds) {
                Map<String, Object> info = bulkUpdateDAO.getObjectInfo(conn, facet, id);
                if (!info.isEmpty()) {
                    // Add segment ID to the info
                    try {
                        int segmentId = SegmentAccessService.getObjectSegmentId(id, objectType);
                        info.put("segmentId", segmentId);
                    } catch (SQLException e) {
                        // If segment lookup fails, default to Enterprise (1)
                        System.err.println("Error getting segment ID for object " + id + ": " + e.getMessage());
                        info.put("segmentId", 1);
                    }
                    result.add(info);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting object info: " + e.getMessage());
        }
        return result;
    }

    /**
     * Check if user has admin role
     */
    public boolean isUserAdmin(int userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT System_Role FROM people WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int roleId = rs.getInt("System_Role");
                        // 1 = Super Admin, 2 = Admin
                        return roleId == 1 || roleId == 2;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking user role: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get filtered parent options based on segment rules and hierarchy constraints
     * - If selected objects belong to different segments, return empty list
     * - If all objects are in Enterprise (segment 1), return all parent options
     * - If all objects are in a private segment, return only Enterprise + same segment objects
     * - Excludes all descendants (children and their descendants) of the objects being updated
     */
    public List<Map<String, Object>> getFilteredParentOptions(String facet, List<Integer> objectIds, int userId) {
        if (objectIds == null || objectIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String objectType = normalizeFacetForSegment(facet);
            
            // Get segment IDs for all selected objects
            Set<Integer> segmentIds = new HashSet<>();
            for (Integer objectId : objectIds) {
                try {
                    int segmentId = SegmentAccessService.getObjectSegmentId(objectId, objectType);
                    segmentIds.add(segmentId);
                } catch (SQLException e) {
                    System.err.println("Error getting segment ID for object " + objectId + ": " + e.getMessage());
                    // Default to Enterprise if lookup fails
                    segmentIds.add(1);
                }
            }

            // If objects belong to different segments, return empty list
            if (segmentIds.size() > 1) {
                return Collections.emptyList();
            }

            // All objects are in the same segment
            int commonSegmentId = segmentIds.iterator().next();

            // Get all parent options (same type as facet)
            List<Map<String, Object>> allParentOptions = getLookupValues(facet, "parent");
            if (allParentOptions.isEmpty()) {
                return Collections.emptyList();
            }

            // Get table and column names for this facet
            String tableName = getTableNameForFacet(facet);
            String idColumn = getIdColumnNameForFacet(facet);
            String parentColumn = getParentColumnNameForFacet(facet);
            
            // If facet doesn't support parent relationships, return all options (no filtering needed)
            if (tableName == null || parentColumn == null) {
                return allParentOptions;
            }

            // Collect all descendant IDs for all objects being updated
            Set<Integer> excludedIds = new HashSet<>();
            try (Connection conn = DatabaseConnection.getConnection()) {
                for (Integer objectId : objectIds) {
                    try {
                        Set<Integer> descendants = bulkUpdateDAO.getAllDescendantIds(conn, tableName, idColumn, parentColumn, objectId);
                        excludedIds.addAll(descendants);
                        // Also exclude the object itself (can't be its own parent)
                        excludedIds.add(objectId);
                    } catch (SQLException e) {
                        System.err.println("Error getting descendants for object " + objectId + ": " + e.getMessage());
                        // Continue with other objects even if one fails
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error getting database connection for descendant lookup: " + e.getMessage());
                // Continue without descendant filtering if connection fails
            }

            // Filter options based on segment and exclude descendants
            List<Map<String, Object>> filteredOptions = new ArrayList<>();
            for (Map<String, Object> option : allParentOptions) {
                Object idObj = option.get("id") != null ? option.get("id") : option.get("ID");
                if (idObj == null) continue;
                
                int parentId;
                if (idObj instanceof Integer) {
                    parentId = (Integer) idObj;
                } else if (idObj instanceof String) {
                    try {
                        parentId = Integer.parseInt((String) idObj);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                } else {
                    continue;
                }

                // Exclude if this parent is a descendant of any object being updated
                if (excludedIds.contains(parentId)) {
                    continue;
                }

                // Filter by segment if not in Enterprise
                if (commonSegmentId != 1) {
                    try {
                        int parentSegmentId = SegmentAccessService.getObjectSegmentId(parentId, objectType);
                        // Include if parent is in Enterprise (1) or same segment
                        if (parentSegmentId == 1 || parentSegmentId == commonSegmentId) {
                            filteredOptions.add(option);
                        }
                    } catch (SQLException e) {
                        // If segment lookup fails, default to Enterprise, so include it
                        filteredOptions.add(option);
                    }
                } else {
                    // All objects are in Enterprise, include all non-descendant options
                    filteredOptions.add(option);
                }
            }

            return filteredOptions;
        } catch (Exception e) {
            System.err.println("Error getting filtered parent options: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get filtered dataset reference options (system/glossary) based on segment rules
     * - If selected datasets belong to different segments, return empty list
     * - If all datasets are in Enterprise (segment 1), return all options
     * - If all datasets are in a private segment, return only Enterprise + same segment objects
     */
    public List<Map<String, Object>> getFilteredDatasetReferenceOptions(String facet, String fieldId, List<Integer> datasetIds, int userId) {
        if (datasetIds == null || datasetIds.isEmpty()) {
            return Collections.emptyList();
        }

        if (!facet.equalsIgnoreCase("dataset")) {
            return Collections.emptyList();
        }

        try {
            // Get segment IDs for all selected datasets
            Set<Integer> segmentIds = new HashSet<>();
            for (Integer datasetId : datasetIds) {
                try {
                    int segmentId = SegmentAccessService.getObjectSegmentId(datasetId, "Dataset");
                    segmentIds.add(segmentId);
                } catch (SQLException e) {
                    System.err.println("Error getting segment ID for dataset " + datasetId + ": " + e.getMessage());
                    // Default to Enterprise if lookup fails
                    segmentIds.add(1);
                }
            }

            // If datasets belong to different segments, return empty list
            if (segmentIds.size() > 1) {
                return Collections.emptyList();
            }

            // All datasets are in the same segment
            int commonSegmentId = segmentIds.iterator().next();

            // Get all reference options based on fieldId
            List<Map<String, Object>> allReferenceOptions;
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (fieldId.equals("system_short_name")) {
                    // Get all systems
                    allReferenceOptions = bulkUpdateDAO.getLookupValues(conn, "system");
                } else if (fieldId.equals("glossary_name")) {
                    // Get all glossaries
                    allReferenceOptions = bulkUpdateDAO.getLookupValues(conn, "glossary");
                } else {
                    return Collections.emptyList();
                }
            }
            
            if (allReferenceOptions == null || allReferenceOptions.isEmpty()) {
                return Collections.emptyList();
            }

            // If all datasets are in Enterprise (segment 1), return all options
            if (commonSegmentId == 1) {
                return allReferenceOptions;
            }

            // If all datasets are in a private segment, filter to Enterprise + same segment
            List<Map<String, Object>> filteredOptions = new ArrayList<>();
            String objectType = fieldId.equals("system_short_name") ? "System" : "Glossary";
            
            for (Map<String, Object> option : allReferenceOptions) {
                Object idObj = option.get("id") != null ? option.get("id") : option.get("ID");
                if (idObj == null) continue;
                
                int referenceId;
                if (idObj instanceof Integer) {
                    referenceId = (Integer) idObj;
                } else if (idObj instanceof String) {
                    try {
                        referenceId = Integer.parseInt((String) idObj);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                } else {
                    continue;
                }

                try {
                    int referenceSegmentId = SegmentAccessService.getObjectSegmentId(referenceId, objectType);
                    // Include if reference is in Enterprise (1) or same segment
                    if (referenceSegmentId == 1 || referenceSegmentId == commonSegmentId) {
                        filteredOptions.add(option);
                    }
                } catch (SQLException e) {
                    // If segment lookup fails, default to Enterprise, so include it
                    filteredOptions.add(option);
                }
            }

            return filteredOptions;
        } catch (Exception e) {
            System.err.println("Error getting filtered dataset reference options: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Normalize facet name to match SegmentDAO object types
     */
    private String normalizeFacetForSegment(String facet) {
        if (facet == null)
            return "";
        String normalized = facet.toLowerCase().replace("-", "");
        if (normalized.equals("dataset"))
            return "Dataset";
        if (normalized.equals("legalentity"))
            return "LegalEntity";
        if (normalized.equals("product"))
            return "Product";
        if (normalized.equals("client"))
            return "Client";
        if (normalized.equals("committee"))
            return "Committee";
        if (normalized.equals("businessarea"))
            return "BusinessArea";
        if (normalized.equals("process"))
            return "Process";
        if (normalized.equals("project"))
            return "Project";
        if (normalized.equals("regulation"))
            return "Regulation";
        if (normalized.equals("capability"))
            return "Capability";
        if (normalized.equals("orgunit") || normalized.equals("org-unit"))
            return "OrgUnit";
        // Capitalize first letter for other facets
        if (normalized.length() > 0) {
            return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
        }
        return facet; // Fallback
    }

    /**
     * Get table name for a facet
     */
    private String getTableNameForFacet(String facet) {
        if (facet == null) {
            return null;
        }
        return BulkUpdateDefinitionConfig.getTableForFacet(facet);
    }

    /**
     * Get ID column name for a facet
     * Tables using "ID" (uppercase): glossary, dataset, policy, regulation, business_area, committee, client, capability
     * Tables using "id" (lowercase): system, process, project, org_unit
     */
    private String getIdColumnNameForFacet(String facet) {
        if (facet == null) {
            return "id";
        }
        String normalized = facet.toLowerCase().replace("-", "");
        switch (normalized) {
            case "glossary":
            case "dataset":
            case "policy":
            case "regulation":
            case "businessarea":
            case "business-area":
            case "committee":
            case "client":
            case "capability":
                return "ID";
            case "system":
            case "process":
            case "project":
            case "orgunit":
            case "org-unit":
                return "id";
            default:
                return "id"; // Default to lowercase
        }
    }

    /**
     * Get parent column name for a facet
     * Different facets use different naming conventions for the parent column
     */
    private String getParentColumnNameForFacet(String facet) {
        if (facet == null) {
            return null;
        }
        String normalized = facet.toLowerCase().replace("-", "");
        switch (normalized) {
            case "glossary":
                return "Parent_ID";
            case "system":
                return "parent_id";
            case "process":
                return "parentid";
            case "project":
                return "parentid";
            case "policy":
                return "ParentID";
            case "regulation":
                return "Parent_ID";
            case "businessarea":
            case "business-area":
                return "Parent_ID";
            case "committee":
                return "Parent_ID";
            case "orgunit":
            case "org-unit":
                return "parent_id";
            default:
                return null; // Facet doesn't support parent relationships
        }
    }

    /**
     * Get lifecycle value from people_details table for a person
     */
    private Object getPeopleDetailsLifecycle(Connection conn, int personId) throws SQLException {
        String sql = "SELECT lifecycle FROM people_details WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, personId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("lifecycle");
                }
            }
        }
        return null;
    }

    /**
     * Update lifecycle in people_details table for a person
     * Creates people_details record if it doesn't exist
     */
    private void updatePeopleDetailsLifecycle(Connection conn, int personId, Integer lifecycleId, int userId)
            throws SQLException {
        // Check if people_details exists
        String checkSql = "SELECT COUNT(*) FROM people_details WHERE id = ?";
        boolean exists = false;
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, personId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    exists = true;
                }
            }
        }

        if (exists) {
            // Update existing
            String updateSql = "UPDATE people_details SET lifecycle = ?, " +
                    "last_updateuser_id = ?, last_updatedtime = NOW() WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                if (lifecycleId != null) {
                    pstmt.setInt(1, lifecycleId);
                } else {
                    pstmt.setNull(1, Types.INTEGER);
                }
                pstmt.setInt(2, userId);
                pstmt.setInt(3, personId);
                pstmt.executeUpdate();
            }
        } else if (lifecycleId != null) {
            // Create new people_details record
            String insertSql = "INSERT INTO people_details (id, lifecycle, " +
                    "last_updateuser_id, created_datetime, last_updatedtime) " +
                    "VALUES (?, ?, ?, NOW(), NOW())";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, personId);
                pstmt.setInt(2, lifecycleId);
                pstmt.setInt(3, userId);
                pstmt.executeUpdate();
            }
        }
    }

    /**
     * Get the target object ID to update (original or cloned based on CR status)
     * Returns cloned row ID if active CR exists, otherwise returns original ID
     */
    private int getTargetObjectId(Connection conn, String facet, Integer facetId, int objectId, 
                                   int userId, boolean hasDefaultCREnabled) throws SQLException {
        // Check for existing active automatic CR
        Integer activeCrId = null;
        if (facetId != null) {
            try {
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetId, objectId);
            } catch (SQLException e) {
                logger.warn("Error checking for active CR for {} {}: {}", facet, objectId, e.getMessage());
            }
        }
        
        // If no active CR and DFCR is enabled, try to create one
        if (activeCrId == null && hasDefaultCREnabled) {
            try {
                boolean isAdmin = isUserAdmin(userId);
                // Get object type ID if needed (null for now, can be enhanced later)
                Integer objectTypeId = null;
                Integer createdCrId = dfcrService.applyDefaultsOnEdit(facet, objectId, objectTypeId, userId, isAdmin);
                if (createdCrId != null) {
                    activeCrId = createdCrId;
                    logger.info("[BulkUpdate] Created auto CR {} for {} {}", activeCrId, facet, objectId);
                }
            } catch (Exception e) {
                logger.warn("Error creating auto CR for {} {}: {}", facet, objectId, e.getMessage());
            }
        }
        
        // If no active CR, update original row
        if (activeCrId == null) {
            return objectId;
        }
        
        // Check CR status
        String crStatus = getCRStatus(conn, activeCrId);
        boolean isCompleted = isCRStatusCompleted(crStatus);
        
        // If CR is completed, update original row
        if (isCompleted) {
            logger.debug("[BulkUpdate] CR {} is completed - updating original row for {} {}", 
                activeCrId, facet, objectId);
            return objectId;
        }
        
        // CR is active (Running or Pending Start) - get or create cloned row
        logger.debug("[BulkUpdate] CR {} is active (status: {}) - updating cloned row for {} {}", 
            activeCrId, crStatus, facet, objectId);
        return getOrCreateClonedRow(conn, facet, objectId, activeCrId);
    }

    /**
     * Get or create cloned row for an object under active CR
     */
    private int getOrCreateClonedRow(Connection conn, String facet, int objectId, int crId) throws SQLException {
        // Check if mapping already exists
        Integer nobjectId = facetChangesDAO.getNObjectId(facet, objectId, "summary", crId);
        
        if (nobjectId != null) {
            logger.debug("[BulkUpdate] Found existing cloned row {} for {} {}", nobjectId, facet, objectId);
            return nobjectId;
        }
        
        // No mapping exists - clone the row
        logger.info("[BulkUpdate] Cloning row for {} {} under CR {}", facet, objectId, crId);
        nobjectId = cloneRowForFacet(conn, facet, objectId);
        
        if (nobjectId == null) {
            logger.error("[BulkUpdate] Failed to clone row for {} {} - using original", facet, objectId);
            return objectId;
        }
        
        // Create mapping
        try {
            facetChangesDAO.saveMapping(facet, objectId, nobjectId, "summary", crId);
            logger.info("[BulkUpdate] Created mapping: {} {} -> {} (CR {})", facet, objectId, nobjectId, crId);
        } catch (SQLException e) {
            logger.error("[BulkUpdate] Failed to save mapping for {} {}: {}", facet, objectId, e.getMessage());
            // Continue with cloned row even if mapping fails
        }
        
        return nobjectId;
    }

    /**
     * Clone a row for a specific facet
     */
    private Integer cloneRowForFacet(Connection conn, String facet, int originalId) throws SQLException {
        String normalizedFacet = facet.toLowerCase().trim();
        
        switch (normalizedFacet) {
            case "glossary":
                return cloneGlossaryRow(conn, originalId);
            case "dataset":
            case "data set":
                return cloneDatasetRow(conn, originalId);
            case "system":
                return cloneSystemRow(conn, originalId);
            case "process":
                return cloneProcessRow(conn, originalId);
            case "attribute":
                return cloneAttributeRow(conn, originalId);
            default:
                logger.warn("[BulkUpdate] Cloning not supported for facet: {}", facet);
                return null;
        }
    }

    /**
     * Clone a glossary row
     */
    private Integer cloneGlossaryRow(Connection conn, int originalId) throws SQLException {
        String sql = "INSERT INTO glossary (" +
                "Name, Description, Format, LDM, Business_Logic, Examples, Ref_Number, " +
                "Format_type, Parent_ID, Status, Lifecycle, Is_Public, Type, " +
                "Security_Classification, KDE, Confidentiality_Rating, Integrity_Rating, " +
                "Availability_Rating, CreatedBy_ID, Created_Datetime, Last_updated_userID, Last_Updated_Datetime" +
                ") SELECT " +
                "Name, Description, Format, LDM, Business_Logic, Examples, Ref_Number, " +
                "Format_type, Parent_ID, Status, Lifecycle, Is_Public, Type, " +
                "Security_Classification, KDE, Confidentiality_Rating, Integrity_Rating, " +
                "Availability_Rating, CreatedBy_ID, Created_Datetime, Last_updated_userID, Last_Updated_Datetime " +
                "FROM glossary WHERE ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Clone a dataset row
     */
    private Integer cloneDatasetRow(Connection conn, int originalId) throws SQLException {
        String sql = "INSERT INTO dataset (" +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID" +
                ") SELECT " +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID " +
                "FROM dataset WHERE ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Clone a system row
     */
    private Integer cloneSystemRow(Connection conn, int originalId) throws SQLException {
        String sql = "INSERT INTO system (" +
                "Name, Long_Name, Description, Type, External, URL, " +
                "Status, Lifecycle, Is_Public, Confidentiality_Rating, " +
                "Integrity_Rating, Availability_Rating, AssetID, Classification, " +
                "DQ_Automation, Parent_ID, CreatedBy_ID, Created_Datetime, " +
                "Last_Updated_Datetime, Last_updated_userID" +
                ") SELECT " +
                "Name, Long_Name, Description, Type, External, URL, " +
                "Status, Lifecycle, Is_Public, Confidentiality_Rating, " +
                "Integrity_Rating, Availability_Rating, AssetID, Classification, " +
                "DQ_Automation, Parent_ID, CreatedBy_ID, Created_Datetime, " +
                "Last_Updated_Datetime, Last_updated_userID " +
                "FROM system WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Clone a process row
     */
    private Integer cloneProcessRow(Connection conn, int originalId) throws SQLException {
        String sql = "INSERT INTO process (" +
                "primaryname, description, parentid, ispublic, status, type, duration_type, duration, " +
                "lifecycle_status, processclass_id, processautomation_id, refnumber, " +
                "input_description, output_description, step_type, " +
                "cancreate, canread, canupdate, candelete, canarchive, " +
                "createdby_id, createdatetime, lastupdatedatetime, lastupdateuser_id" +
                ") SELECT " +
                "primaryname, description, parentid, ispublic, status, type, duration_type, duration, " +
                "lifecycle_status, processclass_id, processautomation_id, refnumber, " +
                "input_description, output_description, step_type, " +
                "cancreate, canread, canupdate, candelete, canarchive, " +
                "createdby_id, createdatetime, lastupdatedatetime, lastupdateuser_id " +
                "FROM process WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Clone an attribute row
     */
    private Integer cloneAttributeRow(Connection conn, int originalId) throws SQLException {
        String sql = "INSERT INTO attribute (" +
                "Is_PrimaryKey, Requirement_ID, Business_Logic, RefNumber, PrimaryName, " +
                "Definition, Glossary_ID, Origination, Editability, Editability_role, " +
                "Data_type_ID, DataLength, Dataset_ID, CreatedBy, Confidence_score, " +
                "CreatedDatetime, Last_UpdateDatetime" +
                ") SELECT " +
                "Is_PrimaryKey, Requirement_ID, Business_Logic, RefNumber, PrimaryName, " +
                "Definition, Glossary_ID, Origination, Editability, Editability_role, " +
                "Data_type_ID, DataLength, Dataset_ID, CreatedBy, Confidence_score, " +
                "CreatedDatetime, NULL " +
                "FROM attribute WHERE ID = ? AND DeletedDatetime IS NULL";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get CR status name by CR ID
     */
    private String getCRStatus(Connection conn, int crId) throws SQLException {
        String sql = "SELECT crs.PrimaryName as status_name " +
                     "FROM changerequest cr " +
                     "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                     "WHERE cr.ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, crId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status_name");
                }
            }
        }
        return null;
    }

    /**
     * Check if CR status is completed, cancelled, rejected, or closed
     */
    private boolean isCRStatusCompleted(String statusName) {
        if (statusName == null) {
            return false;
        }
        String statusLower = statusName.toLowerCase();
        return statusLower.contains("completed") ||
               statusLower.contains("cancelled") ||
               statusLower.contains("canceled") ||
               statusLower.contains("rejected") ||
               statusLower.contains("closed");
    }

    /**
     * Normalize facet name for DFCR lookup
     */
    private String normalizeFacetNameForDFCR(String facet) {
        if (facet == null) {
            return null;
        }
        String normalized = facet.toLowerCase().trim();
        // Map common variations
        if (normalized.equals("data set") || normalized.equals("dataset")) {
            return "Data Set";
        }
        // Capitalize first letter
        if (normalized.length() > 0) {
            return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
        }
        return facet;
    }

    /**
     * Get segment information for a list of attributes.
     * Returns common segment ID if all attributes are in the same segment, null if different segments.
     * 
     * @param attributeIds List of attribute IDs
     * @return Map containing:
     *   - commonSegmentId: The common segment ID if all same, null if different segments
     *   - segmentIds: List of segment IDs for each attribute (in order)
     *   - allSameSegment: Boolean indicating if all attributes are in the same segment
     */
    public Map<String, Object> getAttributeSegmentInfo(List<Integer> attributeIds) {
        Map<String, Object> result = new HashMap<>();
        List<Integer> segmentIds = new ArrayList<>();
        Set<Integer> uniqueSegmentIds = new HashSet<>();
        
        try {
            String objectType = "Attribute";
            for (Integer attributeId : attributeIds) {
                try {
                    int segmentId = SegmentAccessService.getObjectSegmentId(attributeId, objectType);
                    segmentIds.add(segmentId);
                    uniqueSegmentIds.add(segmentId);
                } catch (SQLException e) {
                    System.err.println("Error getting segment ID for attribute " + attributeId + ": " + e.getMessage());
                    // Default to Enterprise if lookup fails
                    segmentIds.add(1);
                    uniqueSegmentIds.add(1);
                }
            }
            
            result.put("segmentIds", segmentIds);
            
            if (uniqueSegmentIds.size() == 1) {
                // All attributes are in the same segment
                int commonSegmentId = uniqueSegmentIds.iterator().next();
                result.put("commonSegmentId", commonSegmentId);
                result.put("allSameSegment", true);
            } else {
                // Attributes belong to different segments
                result.put("commonSegmentId", null);
                result.put("allSameSegment", false);
            }
        } catch (Exception e) {
            System.err.println("Error getting attribute segment info: " + e.getMessage());
            result.put("commonSegmentId", null);
            result.put("allSameSegment", false);
            result.put("segmentIds", segmentIds);
        }
        
        return result;
    }
}
