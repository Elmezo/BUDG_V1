package com.example.budg_v2.service;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Segment Validation Service

 * 1. HIERARCHY RULE: Parent-child relationships must stay within the SAME segment
 *    - A child object cannot be in a different segment than its parent
 *    - Entire hierarchy (parent → child → grandchild) must be in same segment
 *
 * 2. CROSS-SEGMENT RELATIONSHIP RULES:
 *    - Enterprise ↔ Enterprise: ✅ ALLOWED
 *    - Enterprise ↔ Private: ✅ ALLOWED
 *    - Private ↔ Enterprise: ✅ ALLOWED
 *    - Private1 ↔ Private1 (same): ✅ ALLOWED
 *    - Private1 ↔ Private2 (different): ❌ NOT ALLOWED
 *
 * 3. VISIBILITY RULE:
 *    - Data Set cannot be public if System is private
 *
 * Enterprise Segment ID = 1 (always)
 */
public class SegmentValidationService {

    private static final int ENTERPRISE_SEGMENT_ID = 1;
    private static final String SEGMENT_RELATIONSHIP_BLOCK_MESSAGE =
            "Segment cannot be changed because this object has relationships with objects in another private segment.";
    private final SegmentDAO segmentDAO = new SegmentDAO();

    /**
     * Validation result with details
     */
    public static class ValidationResult {
        public boolean isValid;
        public String message;
        public String warningType; // "hierarchy", "cross_segment", "visibility"
        public int parentSegmentId;
        public int childSegmentId;
        public String parentSegmentName;
        public String childSegmentName;
        public boolean canProceedWithWarning; // If true, user can override after warning

        public ValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
            this.canProceedWithWarning = false;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, "Valid");
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public static ValidationResult hierarchyConflict(
                int parentSegmentId, String parentSegmentName,
                int childSegmentId, String childSegmentName) {
            ValidationResult result = new ValidationResult(false,
                    String.format("Hierarchy Constraint: Parent is in segment '%s' but this object is in segment '%s'. " +
                                    "Parent-child relationships must be within the same segment. " +
                                    "This parent is not valid for the selected segment. Please remove the parent first.",
                            parentSegmentName, childSegmentName));
            result.warningType = "hierarchy";
            result.parentSegmentId = parentSegmentId;
            result.childSegmentId = childSegmentId;
            result.parentSegmentName = parentSegmentName;
            result.childSegmentName = childSegmentName;
            result.canProceedWithWarning = false;
            return result;
        }

        public static ValidationResult crossSegmentNotAllowed(
                String sourceSegmentName, String targetSegmentName) {
            ValidationResult result = new ValidationResult(false,
                    String.format("Cross-Segment Relationship Not Allowed: Cannot create relationship between " +
                                    "Private segment '%s' and Private segment '%s'. " +
                                    "Relationships are only allowed between Enterprise↔Private or within the same segment.",
                            sourceSegmentName, targetSegmentName));
            result.warningType = "cross_segment";
            result.canProceedWithWarning = false;
            return result;
        }
    }

    /**
     * Check if segment is the Enterprise segment
     */
    public boolean isEnterpriseSegment(int segmentId) {
        return segmentId == ENTERPRISE_SEGMENT_ID;
    }

    /**
     * Get segment name by ID
     */
    public String getSegmentName(int segmentId) throws SQLException {
        if (segmentId == ENTERPRISE_SEGMENT_ID) {
            return "Enterprise";
        }
        String sql = "SELECT Name FROM segment WHERE ID = ? AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        }
        return "Unknown";
    }

    private int normalizeSegmentId(int segmentId) {
        return segmentId == -1 ? ENTERPRISE_SEGMENT_ID : segmentId;
    }

    /**
     * Get system name by ID
     */
    private String getSystemName(int systemId) throws SQLException {
        String sql = "SELECT Name FROM system WHERE id = ? AND Deleted_datetime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, systemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        }
        return null;
    }

    /**
     * Get glossary name by ID
     */
    private String getGlossaryName(int glossaryId) throws SQLException {
        String sql = "SELECT Name FROM glossary WHERE ID = ? AND Deleted_datetime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, glossaryId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        }
        return null;
    }

    /**
     * HIERARCHY RULE VALIDATION (per requirements):
     * Parent and Child Hierarchy:
     * - Both Enterprise = OK
     * - One Enterprise, one Private = OK
     * - Both same Private = OK
     * - Different Private = NOT OK
     *
     * @param parentId The parent object's ID (null if no parent)
     * @param childSegmentId The segment ID the child object will be assigned to
     * @param objectType The object type (e.g., "Glossary", "Policy", "System")
     * @return ValidationResult with details
     */
    public ValidationResult validateParentChildSegment(Integer parentId, int childSegmentId, String objectType)
            throws SQLException {
        // If no parent, always valid
        if (parentId == null || parentId <= 0) {
            return ValidationResult.success();
        }

        // Get parent's segment
        int parentSegmentId = segmentDAO.getObjectSegmentId(parentId, objectType);

        // If parent has no segment assignment (-1), treat it as Enterprise (default segment)
        // This allows operations to proceed when parent hasn't been assigned a segment yet
        if (parentSegmentId == -1) {
            parentSegmentId = ENTERPRISE_SEGMENT_ID;
        }

        // Both Enterprise = OK
        if (isEnterpriseSegment(parentSegmentId) && isEnterpriseSegment(childSegmentId)) {
            return ValidationResult.success();
        }

        // One Enterprise, one Private = OK
        if (isEnterpriseSegment(parentSegmentId) || isEnterpriseSegment(childSegmentId)) {
            return ValidationResult.success();
        }

        // Both same Private = OK
        if (parentSegmentId == childSegmentId) {
            return ValidationResult.success();
        }

        // Different Private segments = NOT OK
        String parentSegmentName = getSegmentName(parentSegmentId);
        String childSegmentName = getSegmentName(childSegmentId);

        return ValidationResult.hierarchyConflict(
                parentSegmentId, parentSegmentName,
                childSegmentId, childSegmentName
        );
    }

    /**
     * Reusable boolean check used by segment-change validation flows.
     */
    public boolean validateParentForSegment(Long parentId, Long segmentId, String objectType) throws SQLException {
        if (parentId == null || parentId <= 0 || segmentId == null || segmentId <= 0 || objectType == null || objectType.trim().isEmpty()) {
            return true;
        }
        ValidationResult result = validateParentChildSegment(parentId.intValue(), segmentId.intValue(), objectType);
        return result.isValid;
    }

    /**
     * Backward-compatible overload for callers that cannot provide object type.
     */
    public boolean validateParentForSegment(Long parentId, Long segmentId) {
        return true;
    }

    /**
     * Validate that changing a parent's segment won't break hierarchy with its children
     * Per requirements: Enterprise parent can have children in any segment
     *
     * @param objectId The object whose segment is being changed
     * @param newSegmentId The new segment ID
     * @param objectType The object type
     * @return ValidationResult with details
     */
    public ValidationResult validateSegmentChangeWithChildren(int objectId, int newSegmentId, String objectType)
            throws SQLException {
        // Enterprise parent may have children in other segments = OK
        if (isEnterpriseSegment(newSegmentId)) {
            return ValidationResult.success();
        }

        // Get children count in different private segments (Enterprise children are OK)
        String childTable = getChildTableName(objectType);
        String parentColumn = getParentColumnName(objectType);

        if (childTable == null || parentColumn == null) {
            return ValidationResult.success(); // No hierarchy for this type
        }

        // Find first child in a conflicting private segment (not Enterprise, not same target segment).
        String sql = String.format(
                "SELECT c.ID AS child_id, sxr.Segment_ID AS child_segment_id FROM %s c " +
                        "JOIN object_reference orr ON orr.Object_ID = c.ID " +
                        "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        "JOIN segment_x_resource sxr ON sxr.Object_Reference_ID = orr.ID " +
                        "WHERE sot.Type = ? AND c.%s = ? " +
                        "  AND sxr.Segment_ID != ? AND sxr.Segment_ID != 1 AND sxr.Deleted_At IS NULL " +
                        "LIMIT 1",
                childTable, parentColumn
        );

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, objectType);
            pstmt.setInt(2, objectId);
            pstmt.setInt(3, newSegmentId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int childId = rs.getInt("child_id");
                    int childSegmentId = normalizeSegmentId(rs.getInt("child_segment_id"));
                    String childSegmentName = getSegmentName(childSegmentId);
                    String targetSegmentName = getSegmentName(newSegmentId);
                    return ValidationResult.error(
                            String.format(
                                    "%s Blocked: Child relation prevents move. %s (ID: %d) has child %s (ID: %d) " +
                                            "in private segment '%s', but target segment is '%s'.",
                                    SEGMENT_RELATIONSHIP_BLOCK_MESSAGE,
                                    objectType, objectId, objectType, childId, childSegmentName, targetSegmentName
                            )
                    );
                }
            }
        }

        return ValidationResult.success();
    }

    /**
     * Validate that a Regulatory Theme can be moved to the new segment when it has linked regulations.
     * Cannot change segment to another private segment if the theme has regulations that belong to a different private segment.
     *
     * @param regulatoryThemeId The Regulatory Theme ID
     * @param newSegmentId The target segment ID
     * @return ValidationResult error if any linked regulation is in a different private segment
     */
    private ValidationResult validateRegulatoryThemeSegmentMoveWithRegulations(int regulatoryThemeId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                regulatoryThemeId,
                newSegmentId,
                "SELECT DISTINCT Regulation_ID FROM regulation_x_regulatorytheme WHERE RegulatoryTheme_ID = ?",
                "Regulation_ID",
                "Regulation",
                "Regulatory Theme",
                "regulations"
        );
    }

    private ValidationResult validateRegulationSegmentMoveWithRegulators(int regulationId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                regulationId,
                newSegmentId,
                "SELECT DISTINCT RegulatorID FROM regulation_x_regulator WHERE RegulationID = ?",
                "RegulatorID",
                "Regulator",
                "Regulation",
                "regulators"
        );
    }

    private ValidationResult validateRegulationSegmentMoveWithRegulatoryThemes(int regulationId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                regulationId,
                newSegmentId,
                "SELECT DISTINCT RegulatoryTheme_ID FROM regulation_x_regulatorytheme WHERE Regulation_ID = ?",
                "RegulatoryTheme_ID",
                "RegulatoryTheme",
                "Regulation",
                "regulatory themes"
        );
    }

    private ValidationResult validateRegulatorSegmentMoveWithRegulations(int regulatorId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                regulatorId,
                newSegmentId,
                "SELECT DISTINCT RegulationID FROM regulation_x_regulator WHERE RegulatorID = ?",
                "RegulationID",
                "Regulation",
                "Regulator",
                "regulations"
        );
    }

    private ValidationResult validateRegulatorSegmentMoveWithGeographies(int regulatorId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                regulatorId,
                newSegmentId,
                "SELECT DISTINCT Geography_ID FROM regulator_x_geography WHERE Regulator_ID = ?",
                "Geography_ID",
                "Geography",
                "Regulator",
                "geographies"
        );
    }

    private ValidationResult validateGeographySegmentMoveWithRegulators(int geographyId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                geographyId,
                newSegmentId,
                "SELECT DISTINCT Regulator_ID FROM regulator_x_geography WHERE Geography_ID = ?",
                "Regulator_ID",
                "Regulator",
                "Geography",
                "regulators"
        );
    }

    private ValidationResult validateBusinessAreaSegmentMoveWithSystems(int businessAreaId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                businessAreaId,
                newSegmentId,
                "SELECT DISTINCT System_ID FROM businessarea_x_system WHERE BusinessArea_ID = ?",
                "System_ID",
                "System",
                "Business Area",
                "systems"
        );
    }

    private ValidationResult validateBusinessAreaSegmentMoveWithProcesses(int businessAreaId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                businessAreaId,
                newSegmentId,
                "SELECT DISTINCT Process_ID FROM businessarea_x_process WHERE BusinessArea_ID = ?",
                "Process_ID",
                "Process",
                "Business Area",
                "processes"
        );
    }

    private ValidationResult validateBusinessAreaSegmentMoveWithGlossaries(int businessAreaId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                businessAreaId,
                newSegmentId,
                "SELECT DISTINCT Glossary_ID FROM businessarea_x_glossary WHERE BusinessArea_ID = ?",
                "Glossary_ID",
                "Glossary",
                "Business Area",
                "glossaries"
        );
    }

    private ValidationResult validateBusinessAreaSegmentMoveWithProjects(int businessAreaId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                businessAreaId,
                newSegmentId,
                "SELECT DISTINCT Project_ID FROM project_x_businessarea WHERE BusinessArea_ID = ?",
                "Project_ID",
                "Project",
                "Business Area",
                "projects"
        );
    }

    private ValidationResult validateBusinessAreaSegmentMoveWithPolicies(int businessAreaId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                businessAreaId,
                newSegmentId,
                "SELECT DISTINCT Policy_ID FROM policy_x_businessarea WHERE BusinessArea_ID = ?",
                "Policy_ID",
                "Policy",
                "Business Area",
                "policies"
        );
    }

    private ValidationResult validateBusinessAreaSegmentMoveWithProducts(int businessAreaId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                businessAreaId,
                newSegmentId,
                "SELECT DISTINCT Product_ID FROM product_x_businessarea WHERE BusinessArea_ID = ?",
                "Product_ID",
                "Product",
                "Business Area",
                "products"
        );
    }

    private ValidationResult validateBusinessAreaSegmentMoveWithCapabilities(int businessAreaId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                businessAreaId,
                newSegmentId,
                "SELECT DISTINCT Capability_ID FROM capability_x_businessarea WHERE BusinessArea_ID = ?",
                "Capability_ID",
                "Capability",
                "Business Area",
                "capabilities"
        );
    }

    private ValidationResult validateClientSegmentMoveWithSystems(int clientId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                clientId,
                newSegmentId,
                "SELECT DISTINCT System_ID FROM client_x_system WHERE Client_ID = ?",
                "System_ID",
                "System",
                "Client",
                "systems"
        );
    }

    private ValidationResult validateClientSegmentMoveWithProcesses(int clientId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                clientId,
                newSegmentId,
                "SELECT DISTINCT Process_ID FROM client_x_process WHERE Client_ID = ?",
                "Process_ID",
                "Process",
                "Client",
                "processes"
        );
    }

    private ValidationResult validateClientSegmentMoveWithProjects(int clientId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                clientId,
                newSegmentId,
                "SELECT DISTINCT Project_ID FROM client_x_project WHERE Client_ID = ?",
                "Project_ID",
                "Project",
                "Client",
                "projects"
        );
    }

    private ValidationResult validateClientSegmentMoveWithPolicies(int clientId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                clientId,
                newSegmentId,
                "SELECT DISTINCT Policy_ID FROM client_x_policy WHERE Client_ID = ?",
                "Policy_ID",
                "Policy",
                "Client",
                "policies"
        );
    }

    private ValidationResult validateClientSegmentMoveWithProducts(int clientId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                clientId,
                newSegmentId,
                "SELECT DISTINCT Product_ID FROM product_x_client WHERE Client_ID = ?",
                "Product_ID",
                "Product",
                "Client",
                "products"
        );
    }

    private ValidationResult validateClientSegmentMoveWithCapabilities(int clientId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                clientId,
                newSegmentId,
                "SELECT DISTINCT Capability_ID FROM capability_x_client WHERE Client_ID = ?",
                "Capability_ID",
                "Capability",
                "Client",
                "capabilities"
        );
    }

    private ValidationResult validateClientSegmentMoveWithDatasets(int clientId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                clientId,
                newSegmentId,
                "SELECT DISTINCT Dataset_ID FROM client_x_dataset WHERE Client_ID = ?",
                "Dataset_ID",
                "Dataset",
                "Client",
                "datasets"
        );
    }

    private ValidationResult validateClientSegmentMoveWithGlossaries(int clientId, int newSegmentId)
            throws SQLException {
        return validateMoveAgainstLinkedObjects(
                clientId,
                newSegmentId,
                "SELECT DISTINCT Glossary_ID FROM client_x_glossary WHERE Client_ID = ?",
                "Glossary_ID",
                "Glossary",
                "Client",
                "glossaries"
        );
    }

    private ValidationResult validateMoveAgainstLinkedObjects(
            int objectId,
            int newSegmentId,
            String sql,
            String linkedIdColumn,
            String linkedObjectType,
            String movingObjectLabel,
            String linkedCollectionLabel) throws SQLException {
        int normalizedTargetSegmentId = normalizeSegmentId(newSegmentId);
        if (isEnterpriseSegment(normalizedTargetSegmentId)) {
            return ValidationResult.success();
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, objectId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int linkedObjectId = rs.getInt(linkedIdColumn);
                    int linkedSegmentId = normalizeSegmentId(segmentDAO.getObjectSegmentId(linkedObjectId, linkedObjectType));
                    ValidationResult relationshipResult =
                            validateCrossSegmentRelationshipBySegmentIds(normalizedTargetSegmentId, linkedSegmentId);
                    if (!relationshipResult.isValid) {
                        String targetSegmentName = getSegmentName(normalizedTargetSegmentId);
                        String linkedSegmentName = getSegmentName(linkedSegmentId);
                        return ValidationResult.error(
                                String.format(
                                        "%s Blocked: Relation %s(ID: %d) -> %s(ID: %d) crosses private segments. " +
                                                "Linked %s is in '%s' while target segment is '%s'.",
                                        SEGMENT_RELATIONSHIP_BLOCK_MESSAGE,
                                        movingObjectLabel,
                                        objectId,
                                        linkedObjectType,
                                        linkedObjectId,
                                        linkedCollectionLabel,
                                        linkedSegmentName,
                                        targetSegmentName
                                )
                        );
                    }
                }
            }
        }

        return ValidationResult.success();
    }

    /**
     * Validate Glossary -> Attribute assignments when moving glossary segment.
     * Attributes inherit dataset segment, so we validate using attribute.dataset_id segment.
     */
    private ValidationResult validateGlossarySegmentMoveWithAssignedAttributes(int glossaryId, int newSegmentId)
            throws SQLException {
        int normalizedTargetSegmentId = normalizeSegmentId(newSegmentId);
        if (isEnterpriseSegment(normalizedTargetSegmentId)) {
            return ValidationResult.success();
        }

        String sql = """
            SELECT a.ID AS attribute_id, a.Dataset_ID AS dataset_id
            FROM attribute a
            WHERE a.Glossary_ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, glossaryId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int attributeId = rs.getInt("attribute_id");
                    Integer datasetId = rs.getObject("dataset_id") != null ? rs.getInt("dataset_id") : null;

                    int relatedSegmentId = -1;
                    if (datasetId != null && datasetId > 0) {
                        relatedSegmentId = normalizeSegmentId(segmentDAO.getObjectSegmentId(datasetId, "Dataset"));
                    } else {
                        // Fallback for legacy rows without dataset assignment
                        relatedSegmentId = normalizeSegmentId(segmentDAO.getObjectSegmentId(attributeId, "Attribute"));
                    }

                    ValidationResult relationshipResult =
                            validateCrossSegmentRelationshipBySegmentIds(normalizedTargetSegmentId, relatedSegmentId);
                    if (!relationshipResult.isValid) {
                        String relatedSegmentName = getSegmentName(relatedSegmentId);
                        String targetSegmentName = getSegmentName(normalizedTargetSegmentId);
                        return ValidationResult.error(
                                String.format(
                                        "%s Blocked: Relation Glossary(ID: %d) -> Attribute(ID: %d) crosses private segments. " +
                                                "Assigned Attribute is in '%s' while target segment is '%s'.",
                                        SEGMENT_RELATIONSHIP_BLOCK_MESSAGE,
                                        glossaryId,
                                        attributeId,
                                        relatedSegmentName,
                                        targetSegmentName
                                )
                        );
                    }
                }
            }
        }

        return ValidationResult.success();
    }

    /**
     * CROSS-SEGMENT RELATIONSHIP VALIDATION
     *
     * Validates if a relationship can be created between two objects in different segments.
     *
     * Rules:
     * - Enterprise ↔ Enterprise: ✅ ALLOWED
     * - Enterprise ↔ Private: ✅ ALLOWED  
     * - Private ↔ Enterprise: ✅ ALLOWED
     * - Private1 ↔ Private1: ✅ ALLOWED (same segment)
     * - Private1 ↔ Private2: ❌ NOT ALLOWED (different private segments)
     *
     * @param sourceObjectId Source object ID
     * @param sourceObjectType Source object type
     * @param targetObjectId Target object ID
     * @param targetObjectType Target object type
     * @return ValidationResult with details
     */
    public ValidationResult validateCrossSegmentRelationship(
            int sourceObjectId, String sourceObjectType,
            int targetObjectId, String targetObjectType) throws SQLException {
        return validateCrossSegmentRelationship(sourceObjectId, sourceObjectType, targetObjectId, targetObjectType, null);
    }

    /**
     * Validate cross-segment relationship using the provided connection (e.g. from bulk upload for same-transaction visibility).
     */
    public ValidationResult validateCrossSegmentRelationship(
            int sourceObjectId, String sourceObjectType,
            int targetObjectId, String targetObjectType,
            Connection conn) throws SQLException {
        int sourceSegmentId = conn != null
                ? segmentDAO.getObjectSegmentId(sourceObjectId, sourceObjectType, conn)
                : segmentDAO.getObjectSegmentId(sourceObjectId, sourceObjectType);
        int targetSegmentId = conn != null
                ? segmentDAO.getObjectSegmentId(targetObjectId, targetObjectType, conn)
                : segmentDAO.getObjectSegmentId(targetObjectId, targetObjectType);

        // If either object has no segment assignment (-1), treat as Enterprise (consistent with
        // validateParentChildSegment and validateStakeholderCanBeAdded behavior).
        // Unassigned objects default to Enterprise, which can relate to anything.
        if (sourceSegmentId == -1) {
            sourceSegmentId = ENTERPRISE_SEGMENT_ID;
        }
        if (targetSegmentId == -1) {
            targetSegmentId = ENTERPRISE_SEGMENT_ID;
        }

        // Same segment - always allowed
        if (sourceSegmentId == targetSegmentId) {
            return ValidationResult.success();
        }

        // Enterprise ↔ Anything - always allowed
        if (isEnterpriseSegment(sourceSegmentId) || isEnterpriseSegment(targetSegmentId)) {
            return ValidationResult.success();
        }

        // Different private segments - NOT allowed
        String sourceSegmentName = getSegmentName(sourceSegmentId);
        String targetSegmentName = getSegmentName(targetSegmentId);

        return ValidationResult.crossSegmentNotAllowed(sourceSegmentName, targetSegmentName);
    }

    /**
     * Validate relationship by segment IDs directly (when object IDs are not known yet)
     */
    public ValidationResult validateCrossSegmentRelationshipBySegmentIds(
            int sourceSegmentId, int targetSegmentId) throws SQLException {

        // Same segment - always allowed
        if (sourceSegmentId == targetSegmentId) {
            return ValidationResult.success();
        }

        // Enterprise ↔ Anything - always allowed
        if (isEnterpriseSegment(sourceSegmentId) || isEnterpriseSegment(targetSegmentId)) {
            return ValidationResult.success();
        }

        // Different private segments - NOT allowed
        String sourceSegmentName = getSegmentName(sourceSegmentId);
        String targetSegmentName = getSegmentName(targetSegmentId);

        return ValidationResult.crossSegmentNotAllowed(sourceSegmentName, targetSegmentName);
    }

    // ==================== DATASET / SYSTEM / GLOSSARY / ATTRIBUTE RULES ====================

    /**
     * DATASET ↔ SYSTEM SEGMENT RULE (per task description):
     * - If the System is in Enterprise (1): Dataset may be in any segment.
     * - If the System is in a non-enterprise segment: Dataset MUST be in the SAME segment.
     *
     * Also validates that the user has access to the system's segment.
     *
     * @param systemId The system ID
     * @param datasetSegmentId The dataset segment ID
     * @param userId The user ID (for access validation)
     * @return ValidationResult
     */
    public ValidationResult validateDatasetSystemSegment(Integer systemId, int datasetSegmentId, int userId) throws SQLException {
        if (systemId == null || systemId <= 0) {
            return ValidationResult.success();
        }

        int systemSegmentId = segmentDAO.getObjectSegmentId(systemId, "System");

        // If system has no segment assignment (-1), treat it as Enterprise.
        // This keeps create/update flows working for legacy systems that were
        // never explicitly assigned in segment_x_resource.
        if (systemSegmentId == -1) {
            systemSegmentId = ENTERPRISE_SEGMENT_ID;
        }

        // Validate user has access to the system's segment
        if (userId > 0) {
            Set<Integer> accessibleSegments = SegmentAccessService.getAccessibleSegmentIds(userId);
            if (!accessibleSegments.contains(systemSegmentId)) {
                String systemSegName = getSegmentName(systemSegmentId);
                return ValidationResult.error(
                        String.format("Access Denied: You do not have access to the system's segment '%s'. " +
                                        "Please select a system from a segment you have access to.",
                                systemSegName));
            }
        }

        // Enterprise system can relate to datasets in any segment
        if (isEnterpriseSegment(systemSegmentId)) {
            return ValidationResult.success();
        }

        // System in private segment -> dataset must be same segment
        if (systemSegmentId == datasetSegmentId) {
            return ValidationResult.success();
        }

        String systemSegName = getSegmentName(systemSegmentId);
        String datasetSegName = getSegmentName(datasetSegmentId);
        return ValidationResult.error(
                String.format("Dataset/System Segment Rule Violation: This dataset is in segment '%s' but its System is in segment '%s'. " +
                                "When a System belongs to a private segment, its Datasets must belong to the same segment.",
                        datasetSegName, systemSegName));
    }

    /**
     * DATASET ↔ SYSTEM SEGMENT RULE (backward compatibility - without user validation)
     * - If the System is in Enterprise (1): Dataset may be in any segment.
     * - If the System is in a non-enterprise segment: Dataset MUST be in the SAME segment.
     */
    public ValidationResult validateDatasetSystemSegment(Integer systemId, int datasetSegmentId) throws SQLException {
        return validateDatasetSystemSegment(systemId, datasetSegmentId, 0);
    }

    /**
     * Get the required dataset segment ID based on the selected glossary.
     * When creating a dataset, the segment should be derived from the glossary:
     * - If glossary is Enterprise: dataset can be in any segment (returns null to indicate no restriction)
     * - If glossary is private: dataset must be in the same segment as the glossary
     *
     * @param glossaryId The glossary ID
     * @param datasetIsPublic Whether the dataset is public (AccessControlType == 1)
     * @return The required segment ID, or null if no restriction (Enterprise glossary with public dataset)
     * @throws SQLException
     */
    public Integer getRequiredDatasetSegmentFromGlossary(Integer glossaryId, boolean datasetIsPublic) throws SQLException {
        if (glossaryId == null || glossaryId <= 0) {
            return null; // No glossary selected, no restriction
        }

        int glossarySegmentId = segmentDAO.getObjectSegmentId(glossaryId, "Glossary");

        // If glossary has no segment assignment (-1), return null (no restriction)
        if (glossarySegmentId == -1) {
            return null;
        }

        // Public dataset can attach to ANY glossary, so no segment restriction
        if (datasetIsPublic) {
            return null;
        }

        // Private dataset: must match glossary segment
        // If glossary is Enterprise, dataset can be any segment (null = no restriction)
        if (isEnterpriseSegment(glossarySegmentId)) {
            return null;
        }

        // Glossary is private: dataset must be in same segment
        return glossarySegmentId;
    }

    /**
     * DATASET ↔ GLOSSARY SEGMENT RULE (per requirements):
     * The dataset segment should be derived from the glossary segment.
     * - Public dataset (AccessControlType == 1) can attach to ANY glossary (no segment restriction)
     * - Private dataset must be in the SAME segment as the glossary (or Enterprise if glossary is Enterprise)
     *
     * Also validates that the user has access to the glossary's segment.
     *
     * @param glossaryId The glossary ID
     * @param datasetSegmentId The dataset segment ID
     * @param datasetIsPublic Whether the dataset is public (AccessControlType == 1)
     * @param userId The user ID (for access validation)
     * @return ValidationResult
     */
    public ValidationResult validateDatasetGlossarySegment(Integer glossaryId, int datasetSegmentId, boolean datasetIsPublic, int userId) throws SQLException {
        if (glossaryId == null || glossaryId <= 0) {
            return ValidationResult.success();
        }

        int glossarySegmentId = segmentDAO.getObjectSegmentId(glossaryId, "Glossary");

        // If glossary has no segment assignment (-1), validation fails
        if (glossarySegmentId == -1) {
            String glossaryName = getGlossaryName(glossaryId);
            String glossaryIdentifier = glossaryName != null ?
                    String.format("'%s' (ID: %d)", glossaryName, glossaryId) :
                    String.format("ID: %d", glossaryId);
            return ValidationResult.error(
                    String.format("Glossary %s has no segment assignment. Please assign a segment to this glossary before saving this dataset.",
                            glossaryIdentifier));
        }

        // Validate user has access to the glossary's segment
        if (userId > 0) {
            Set<Integer> accessibleSegments = SegmentAccessService.getAccessibleSegmentIds(userId);
            if (!accessibleSegments.contains(glossarySegmentId)) {
                String glossarySegName = getSegmentName(glossarySegmentId);
                return ValidationResult.error(
                        String.format("Access Denied: You do not have access to the glossary's segment '%s'. " +
                                        "Please select a glossary from a segment you have access to.",
                                glossarySegName));
            }
        }

        // Public dataset can attach to ANY glossary (public or private) - no segment restriction
        if (datasetIsPublic) {
            return ValidationResult.success();
        }

        // Private dataset: must match glossary segment
        // If glossary is Enterprise, dataset can be any segment
        if (isEnterpriseSegment(glossarySegmentId)) {
            return ValidationResult.success();
        }

        // Glossary is private: dataset must be in same segment
        if (glossarySegmentId == datasetSegmentId) {
            return ValidationResult.success();
        }

        String glossarySegName = getSegmentName(glossarySegmentId);
        String datasetSegName = getSegmentName(datasetSegmentId);
        return ValidationResult.error(
                String.format("Dataset/Glossary Segment Rule Violation: The dataset is in segment '%s' but the selected Glossary is in segment '%s'. " +
                                "The dataset segment must match the glossary segment. Please set the dataset segment to '%s' to match the glossary.",
                        datasetSegName, glossarySegName, glossarySegName));
    }

    /**
     * DATASET ↔ GLOSSARY SEGMENT RULE (backward compatibility - without user validation)
     * The dataset segment should be derived from the glossary segment.
     * - Public dataset (AccessControlType == 1) can attach to ANY glossary (no segment restriction)
     * - Private dataset must be in the SAME segment as the glossary (or Enterprise if glossary is Enterprise)
     */
    public ValidationResult validateDatasetGlossarySegment(Integer glossaryId, int datasetSegmentId, boolean datasetIsPublic) throws SQLException {
        return validateDatasetGlossarySegment(glossaryId, datasetSegmentId, datasetIsPublic, 0);
    }

    /**
     * DATASET ↔ GLOSSARY SEGMENT RULE (backward compatibility - assumes private dataset)
     * - Glossary must be in Enterprise OR the same segment as the dataset.
     */
    public ValidationResult validateDatasetGlossarySegment(Integer glossaryId, int datasetSegmentId) throws SQLException {
        return validateDatasetGlossarySegment(glossaryId, datasetSegmentId, false);
    }

    /**
     * ATTRIBUTE ↔ DATASET/GLOSSARY rule (per requirements):
     * - Attributes in public dataset can attach to ANY glossary (public or private)
     * - Attributes in private dataset can attach to Enterprise glossary OR same private segment glossary
     *
     * Attribute inherits dataset segment, so any glossary linked via the attribute must respect the dataset rule.
     *
     * @param datasetId The dataset ID
     * @param glossaryId The glossary ID
     * @param datasetIsPublic Whether the dataset is public (AccessControlType == 1)
     * @return ValidationResult
     */
    public ValidationResult validateAttributeGlossarySegment(Integer datasetId, Integer glossaryId, boolean datasetIsPublic) throws SQLException {
        if (datasetId == null || datasetId <= 0) return ValidationResult.success();
        if (glossaryId == null || glossaryId <= 0) return ValidationResult.success();
        int datasetSegmentId = segmentDAO.getObjectSegmentId(datasetId, "Dataset");
        return validateDatasetGlossarySegment(glossaryId, datasetSegmentId, datasetIsPublic);
    }

    /**
     * ATTRIBUTE ↔ DATASET/GLOSSARY rule (backward compatibility - assumes private dataset)
     * Attribute inherits dataset segment, so any glossary linked via the attribute must also respect the dataset rule.
     */
    public ValidationResult validateAttributeGlossarySegment(Integer datasetId, Integer glossaryId) throws SQLException {
        return validateAttributeGlossarySegment(datasetId, glossaryId, false);
    }

    /**
     * VISIBILITY RULE VALIDATION
     *
     * Per BUDG docs: "Data Set cannot be public if System is private"
     *
     * @param datasetId The dataset ID
     * @param systemId The system ID the dataset belongs to
     * @param datasetIsPublic Whether the dataset is being set to public
     * @return ValidationResult with details
     */
    public ValidationResult validateDatasetVisibility(int datasetId, int systemId, boolean datasetIsPublic)
            throws SQLException {
        if (!datasetIsPublic) {
            return ValidationResult.success(); // Private dataset - no restriction
        }

        int systemSegmentId = segmentDAO.getObjectSegmentId(systemId, "System");

        // If system has no segment assignment (-1), validation fails
        if (systemSegmentId == -1) {
            String systemName = getSystemName(systemId);
            String systemIdentifier = systemName != null ?
                    String.format("'%s' (ID: %d)", systemName, systemId) :
                    String.format("ID: %d", systemId);
            return ValidationResult.error(
                    String.format("System %s has no segment assignment. Please assign a segment to this system before saving this public dataset.",
                            systemIdentifier));
        }

        // If system is in Enterprise (public), dataset can be public
        if (isEnterpriseSegment(systemSegmentId)) {
            return ValidationResult.success();
        }

        // System is in private segment, dataset cannot be public
        String systemSegmentName = getSegmentName(systemSegmentId);
        return ValidationResult.error(
                String.format("Visibility Rule Violation: This dataset cannot be set to public because " +
                                "its System is in private segment '%s'. " +
                                "Move the System to Enterprise segment first, or keep the dataset private.",
                        systemSegmentName)
        );
    }

    /**
     * Move parent to child's segment (when user confirms to proceed with hierarchy conflict)
     *
     * @param parentId Parent object ID
     * @param childSegmentId Target segment ID (child's segment)
     * @param objectType Object type
     * @param userId User performing the action
     */
    public void moveParentToChildSegment(int parentId, int childSegmentId, String objectType, int userId)
            throws SQLException {
        int currentSegmentId = segmentDAO.getObjectSegmentId(parentId, objectType);

        if (currentSegmentId != childSegmentId) {
            // Remove from current segment
            if (currentSegmentId > 0) {
                segmentDAO.removeObjectFromSegment(currentSegmentId, parentId, objectType, userId);
            }
            // Add to new segment
            segmentDAO.assignObjectToSegment(childSegmentId, parentId, objectType, userId);
            System.out.println("✅ Moved parent " + parentId + " from segment " + currentSegmentId +
                    " to segment " + childSegmentId);
        }
    }

    /**
     * Get the table name for child objects based on object type
     */
    private String getChildTableName(String objectType) {
        return switch (objectType) {
            case "Glossary" -> "glossary";
            case "Policy" -> "policy";
            case "System" -> "system";
            case "Regulation" -> "regulation";
            case "Project" -> "project";
            case "Process" -> "process";
            case "BusinessArea", "Business Area" -> "business_area";
            case "OrgUnit" -> "org_unit";
            case "Client" -> "client";
            case "Product" -> "product";
            default -> null;
        };
    }

    /**
     * Get the parent column name based on object type
     */
    private String getParentColumnName(String objectType) {
        return switch (objectType) {
            case "Glossary" -> "Parent_ID";
            case "Policy" -> "ParentID";
            case "System" -> "parent_id";
            case "Regulation" -> "parent_id";
            case "Project" -> "parentid";
            case "Process" -> "parentid"; // process table uses parentid
            case "BusinessArea", "Business Area" -> "Parent_ID";
            case "OrgUnit" -> "Parent_ID";
            case "Client" -> "Parent_ID";
            case "Product" -> "parent_id";
            default -> null;
        };
    }

    private String getObjectTableName(String objectType) {
        return switch (objectType) {
            case "Glossary" -> "glossary";
            case "Policy" -> "policy";
            case "System" -> "system";
            case "Regulation" -> "regulation";
            case "Project" -> "project";
            case "Process" -> "process";
            case "BusinessArea", "Business Area" -> "business_area";
            case "OrgUnit" -> "org_unit";
            case "Client" -> "client";
            case "Product" -> "product";
            default -> null;
        };
    }

    private String getObjectIdColumnName(String objectType) {
        return switch (objectType) {
            case "System", "Regulation" -> "id";
            case "Policy" -> "ID";
            case "Glossary" -> "ID";
            case "Project" -> "ID";
            case "Process" -> "id";
            case "BusinessArea", "Business Area" -> "ID";
            case "OrgUnit" -> "ID";
            case "Client" -> "ID";
            case "Product" -> "id";
            default -> "ID";
        };
    }

    private Integer resolveParentIdForObject(int objectId, String objectType) throws SQLException {
        String tableName = getObjectTableName(objectType);
        String parentColumn = getParentColumnName(objectType);
        String idColumn = getObjectIdColumnName(objectType);
        if (tableName == null || parentColumn == null || idColumn == null) {
            return null;
        }

        String sql = String.format(
                "SELECT %s AS parent_id FROM %s WHERE %s = ? LIMIT 1",
                parentColumn, tableName, idColumn
        );

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, objectId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int parentId = rs.getInt("parent_id");
                    return rs.wasNull() || parentId <= 0 ? null : parentId;
                }
            }
        }
        return null;
    }

    /**
     * Get full segment info by ID
     */
    public Map<String, Object> getSegmentInfo(int segmentId) throws SQLException {
        Map<String, Object> info = new HashMap<>();
        info.put("id", segmentId);
        info.put("name", getSegmentName(segmentId));
        info.put("isEnterprise", isEnterpriseSegment(segmentId));
        return info;
    }

    /**
     * Validate that an object can be moved to a new segment
     * Checks both parent constraints (upward) and children constraints (downward)
     *
     * @param objectId Object ID
     * @param newSegmentId New segment ID
     * @param objectType Object type
     * @param parentId Parent object ID (null if none)
     * @return ValidationResult
     */
    public ValidationResult validateSegmentMove(int objectId, int newSegmentId, String objectType, Integer parentId)
            throws SQLException {
        newSegmentId = normalizeSegmentId(newSegmentId);

        if (parentId == null || parentId <= 0) {
            parentId = resolveParentIdForObject(objectId, objectType);
        }

        // Check parent constraint
        ValidationResult parentResult = validateParentChildSegment(parentId, newSegmentId, objectType);
        if (!parentResult.isValid && !parentResult.canProceedWithWarning) {
            return parentResult;
        }

        // Check children constraint
        ValidationResult childrenResult = validateSegmentChangeWithChildren(objectId, newSegmentId, objectType);
        if (!childrenResult.isValid) {
            return childrenResult;
        }

        // Regulatory Theme: cannot move to another private segment if it has regulations in a different private segment
        if ("Regulatory Theme".equals(objectType) || "RegulatoryTheme".equals(objectType)) {
            ValidationResult regulationsResult = validateRegulatoryThemeSegmentMoveWithRegulations(objectId, newSegmentId);
            if (!regulationsResult.isValid) {
                return regulationsResult;
            }
        }

        if ("Regulation".equals(objectType)) {
            ValidationResult regulatorsResult = validateRegulationSegmentMoveWithRegulators(objectId, newSegmentId);
            if (!regulatorsResult.isValid) {
                return regulatorsResult;
            }

            ValidationResult themesResult = validateRegulationSegmentMoveWithRegulatoryThemes(objectId, newSegmentId);
            if (!themesResult.isValid) {
                return themesResult;
            }

            // Peer regulation relationships (both directions)
            ValidationResult peerRegulationsAsSourceResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT TargetRegulationID AS peer_id FROM regulation_x_regulation WHERE SourceRegulationID = ?",
                    "peer_id", "Regulation", "Regulation", "related regulations");
            if (!peerRegulationsAsSourceResult.isValid) return peerRegulationsAsSourceResult;
            ValidationResult peerRegulationsAsTargetResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT SourceRegulationID AS peer_id FROM regulation_x_regulation WHERE TargetRegulationID = ?",
                    "peer_id", "Regulation", "Regulation", "related regulations");
            if (!peerRegulationsAsTargetResult.isValid) return peerRegulationsAsTargetResult;
        }

        if ("Regulator".equals(objectType)) {
            ValidationResult regulationsResult = validateRegulatorSegmentMoveWithRegulations(objectId, newSegmentId);
            if (!regulationsResult.isValid) {
                return regulationsResult;
            }

            ValidationResult geographiesResult = validateRegulatorSegmentMoveWithGeographies(objectId, newSegmentId);
            if (!geographiesResult.isValid) {
                return geographiesResult;
            }
        }

        if ("Geography".equals(objectType)) {
            ValidationResult regulatorsResult = validateGeographySegmentMoveWithRegulators(objectId, newSegmentId);
            if (!regulatorsResult.isValid) {
                return regulatorsResult;
            }
        }

        if ("BusinessArea".equals(objectType) || "Business Area".equals(objectType)) {
            ValidationResult systemsResult = validateBusinessAreaSegmentMoveWithSystems(objectId, newSegmentId);
            if (!systemsResult.isValid) {
                return systemsResult;
            }
            ValidationResult processesResult = validateBusinessAreaSegmentMoveWithProcesses(objectId, newSegmentId);
            if (!processesResult.isValid) {
                return processesResult;
            }
            ValidationResult glossariesResult = validateBusinessAreaSegmentMoveWithGlossaries(objectId, newSegmentId);
            if (!glossariesResult.isValid) {
                return glossariesResult;
            }
            ValidationResult projectsResult = validateBusinessAreaSegmentMoveWithProjects(objectId, newSegmentId);
            if (!projectsResult.isValid) {
                return projectsResult;
            }
            ValidationResult policiesResult = validateBusinessAreaSegmentMoveWithPolicies(objectId, newSegmentId);
            if (!policiesResult.isValid) {
                return policiesResult;
            }
            ValidationResult productsResult = validateBusinessAreaSegmentMoveWithProducts(objectId, newSegmentId);
            if (!productsResult.isValid) {
                return productsResult;
            }
            ValidationResult capabilitiesResult = validateBusinessAreaSegmentMoveWithCapabilities(objectId, newSegmentId);
            if (!capabilitiesResult.isValid) {
                return capabilitiesResult;
            }
        }

        if ("Client".equals(objectType)) {
            ValidationResult systemsResult = validateClientSegmentMoveWithSystems(objectId, newSegmentId);
            if (!systemsResult.isValid) {
                return systemsResult;
            }
            ValidationResult processesResult = validateClientSegmentMoveWithProcesses(objectId, newSegmentId);
            if (!processesResult.isValid) {
                return processesResult;
            }
            ValidationResult projectsResult = validateClientSegmentMoveWithProjects(objectId, newSegmentId);
            if (!projectsResult.isValid) {
                return projectsResult;
            }
            ValidationResult policiesResult = validateClientSegmentMoveWithPolicies(objectId, newSegmentId);
            if (!policiesResult.isValid) {
                return policiesResult;
            }
            ValidationResult productsResult = validateClientSegmentMoveWithProducts(objectId, newSegmentId);
            if (!productsResult.isValid) {
                return productsResult;
            }
            ValidationResult capabilitiesResult = validateClientSegmentMoveWithCapabilities(objectId, newSegmentId);
            if (!capabilitiesResult.isValid) {
                return capabilitiesResult;
            }
            ValidationResult datasetsResult = validateClientSegmentMoveWithDatasets(objectId, newSegmentId);
            if (!datasetsResult.isValid) {
                return datasetsResult;
            }
            ValidationResult glossariesResult = validateClientSegmentMoveWithGlossaries(objectId, newSegmentId);
            if (!glossariesResult.isValid) {
                return glossariesResult;
            }
        }

        if ("System".equals(objectType)) {
            ValidationResult projectsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT projectid FROM project_x_system WHERE systemid = ?",
                    "projectid", "Project", "System", "projects");
            if (!projectsResult.isValid) return projectsResult;

            ValidationResult processesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT process_id FROM process_x_system WHERE system_id = ?",
                    "process_id", "Process", "System", "processes");
            if (!processesResult.isValid) return processesResult;

            ValidationResult policiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Policy_ID FROM policy_x_system WHERE System_ID = ?",
                    "Policy_ID", "Policy", "System", "policies");
            if (!policiesResult.isValid) return policiesResult;

            ValidationResult productsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Product_ID FROM product_x_system WHERE System_ID = ?",
                    "Product_ID", "Product", "System", "products");
            if (!productsResult.isValid) return productsResult;

            ValidationResult clientsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Client_ID FROM client_x_system WHERE System_ID = ?",
                    "Client_ID", "Client", "System", "clients");
            if (!clientsResult.isValid) return clientsResult;

            ValidationResult businessAreasResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT BusinessArea_ID FROM businessarea_x_system WHERE System_ID = ?",
                    "BusinessArea_ID", "BusinessArea", "System", "business areas");
            if (!businessAreasResult.isValid) return businessAreasResult;

            // Data Content Summary links (System ↔ Glossary)
            ValidationResult glossariesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT GlossaryID FROM glossary_x_system WHERE SystemID = ?",
                    "GlossaryID", "Glossary", "System", "glossaries");
            if (!glossariesResult.isValid) return glossariesResult;
        }

        if ("Policy".equals(objectType)) {
            ValidationResult systemsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT System_ID FROM policy_x_system WHERE Policy_ID = ?",
                    "System_ID", "System", "Policy", "systems");
            if (!systemsResult.isValid) return systemsResult;

            ValidationResult processesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT process_id FROM policy_x_process WHERE policy_id = ?",
                    "process_id", "Process", "Policy", "processes");
            if (!processesResult.isValid) return processesResult;

            ValidationResult projectsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT project_id FROM policy_x_project WHERE policy_id = ?",
                    "project_id", "Project", "Policy", "projects");
            if (!projectsResult.isValid) return projectsResult;

            ValidationResult glossariesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT GlossaryID FROM policy_x_glossary WHERE PolicyID = ?",
                    "GlossaryID", "Glossary", "Policy", "glossaries");
            if (!glossariesResult.isValid) return glossariesResult;

            ValidationResult datasetsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT DatasetID FROM policy_x_dataset WHERE PolicyID = ?",
                    "DatasetID", "Dataset", "Policy", "datasets");
            if (!datasetsResult.isValid) return datasetsResult;

            ValidationResult clientsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Client_ID FROM client_x_policy WHERE Policy_ID = ?",
                    "Client_ID", "Client", "Policy", "clients");
            if (!clientsResult.isValid) return clientsResult;

            ValidationResult businessAreasResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT BusinessArea_ID FROM policy_x_businessarea WHERE Policy_ID = ?",
                    "BusinessArea_ID", "BusinessArea", "Policy", "business areas");
            if (!businessAreasResult.isValid) return businessAreasResult;

            // Peer policy relationships (both directions)
            ValidationResult peerPoliciesAsSourceResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT targetid AS peer_id FROM policy_x_policy WHERE sourceid = ?",
                    "peer_id", "Policy", "Policy", "related policies");
            if (!peerPoliciesAsSourceResult.isValid) return peerPoliciesAsSourceResult;
            ValidationResult peerPoliciesAsTargetResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT sourceid AS peer_id FROM policy_x_policy WHERE targetid = ?",
                    "peer_id", "Policy", "Policy", "related policies");
            if (!peerPoliciesAsTargetResult.isValid) return peerPoliciesAsTargetResult;
        }

        if ("Process".equals(objectType)) {
            ValidationResult systemsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT system_id FROM process_x_system WHERE process_id = ?",
                    "system_id", "System", "Process", "systems");
            if (!systemsResult.isValid) return systemsResult;

            ValidationResult clientsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Client_ID FROM client_x_process WHERE Process_ID = ?",
                    "Client_ID", "Client", "Process", "clients");
            if (!clientsResult.isValid) return clientsResult;

            ValidationResult glossariesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Glossary_ID FROM glossary_x_process WHERE Process_ID = ?",
                    "Glossary_ID", "Glossary", "Process", "glossaries");
            if (!glossariesResult.isValid) return glossariesResult;

            ValidationResult projectsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT projectid FROM project_x_process WHERE process_id = ?",
                    "projectid", "Project", "Process", "projects");
            if (!projectsResult.isValid) return projectsResult;

            ValidationResult policiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT policy_id FROM policy_x_process WHERE process_id = ?",
                    "policy_id", "Policy", "Process", "policies");
            if (!policiesResult.isValid) return policiesResult;

            ValidationResult datasetsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT datasetid FROM process_x_dataset WHERE processid = ?",
                    "datasetid", "Dataset", "Process", "datasets");
            if (!datasetsResult.isValid) return datasetsResult;

            ValidationResult businessAreasResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT BusinessArea_ID FROM businessarea_x_process WHERE Process_ID = ?",
                    "BusinessArea_ID", "BusinessArea", "Process", "business areas");
            if (!businessAreasResult.isValid) return businessAreasResult;
        }

        if ("Project".equals(objectType)) {
            ValidationResult systemsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT systemid FROM project_x_system WHERE projectid = ?",
                    "systemid", "System", "Project", "systems");
            if (!systemsResult.isValid) return systemsResult;

            ValidationResult glossariesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Glossary_ID FROM glossary_x_project WHERE Project_ID = ?",
                    "Glossary_ID", "Glossary", "Project", "glossaries");
            if (!glossariesResult.isValid) return glossariesResult;

            ValidationResult policiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT policy_id FROM policy_x_project WHERE project_id = ?",
                    "policy_id", "Policy", "Project", "policies");
            if (!policiesResult.isValid) return policiesResult;

            ValidationResult clientsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Client_ID FROM client_x_project WHERE Project_ID = ?",
                    "Client_ID", "Client", "Project", "clients");
            if (!clientsResult.isValid) return clientsResult;

            ValidationResult datasetsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT dataset_id FROM project_x_dataset WHERE projectid = ?",
                    "dataset_id", "Dataset", "Project", "datasets");
            if (!datasetsResult.isValid) return datasetsResult;

            ValidationResult processesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT process_id FROM project_x_process WHERE projectid = ?",
                    "process_id", "Process", "Project", "processes");
            if (!processesResult.isValid) return processesResult;

            ValidationResult businessAreasResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT BusinessArea_ID FROM project_x_businessarea WHERE Project_ID = ?",
                    "BusinessArea_ID", "BusinessArea", "Project", "business areas");
            if (!businessAreasResult.isValid) return businessAreasResult;

            // Peer project relationships (both directions)
            ValidationResult peerProjectsAsSourceResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT targetprojectid AS peer_id FROM project_x_project WHERE sourceprojectid = ?",
                    "peer_id", "Project", "Project", "related projects");
            if (!peerProjectsAsSourceResult.isValid) return peerProjectsAsSourceResult;
            ValidationResult peerProjectsAsTargetResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT sourceprojectid AS peer_id FROM project_x_project WHERE targetprojectid = ?",
                    "peer_id", "Project", "Project", "related projects");
            if (!peerProjectsAsTargetResult.isValid) return peerProjectsAsTargetResult;
        }

        if ("Product".equals(objectType)) {
            ValidationResult systemsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT System_ID FROM product_x_system WHERE Product_ID = ?",
                    "System_ID", "System", "Product", "systems");
            if (!systemsResult.isValid) return systemsResult;

            ValidationResult glossariesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT glossaryid AS Glossary_ID FROM product_x_glossary WHERE productid = ?",
                    "Glossary_ID", "Glossary", "Product", "glossaries");
            if (!glossariesResult.isValid) return glossariesResult;

            ValidationResult datasetsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Dataset_ID FROM product_x_dataset WHERE Product_ID = ?",
                    "Dataset_ID", "Dataset", "Product", "datasets");
            if (!datasetsResult.isValid) return datasetsResult;

            ValidationResult clientsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Client_ID FROM product_x_client WHERE Product_ID = ?",
                    "Client_ID", "Client", "Product", "clients");
            if (!clientsResult.isValid) return clientsResult;

            ValidationResult businessAreasResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT BusinessArea_ID FROM product_x_businessarea WHERE Product_ID = ?",
                    "BusinessArea_ID", "BusinessArea", "Product", "business areas");
            if (!businessAreasResult.isValid) return businessAreasResult;
        }

        if ("Capability".equals(objectType)) {
            ValidationResult glossariesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Glossary_ID FROM capability_x_glossary WHERE Capability_ID = ?",
                    "Glossary_ID", "Glossary", "Capability", "glossaries");
            if (!glossariesResult.isValid) return glossariesResult;

            ValidationResult clientsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Client_ID FROM capability_x_client WHERE Capability_ID = ?",
                    "Client_ID", "Client", "Capability", "clients");
            if (!clientsResult.isValid) return clientsResult;

            ValidationResult businessAreasResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT BusinessArea_ID FROM capability_x_businessarea WHERE Capability_ID = ?",
                    "BusinessArea_ID", "BusinessArea", "Capability", "business areas");
            if (!businessAreasResult.isValid) return businessAreasResult;

            ValidationResult committeesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Committee_ID FROM committee_x_capability WHERE Capability_ID = ?",
                    "Committee_ID", "Committee", "Capability", "committees");
            if (!committeesResult.isValid) return committeesResult;

            // Peer capability relationships (both directions)
            ValidationResult peerCapabilitiesAsSourceResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Target_ID AS peer_id FROM capability_x_capability WHERE Source_ID = ?",
                    "peer_id", "Capability", "Capability", "related capabilities");
            if (!peerCapabilitiesAsSourceResult.isValid) return peerCapabilitiesAsSourceResult;
            ValidationResult peerCapabilitiesAsTargetResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Source_ID AS peer_id FROM capability_x_capability WHERE Target_ID = ?",
                    "peer_id", "Capability", "Capability", "related capabilities");
            if (!peerCapabilitiesAsTargetResult.isValid) return peerCapabilitiesAsTargetResult;
        }

        if ("Dataset".equals(objectType)) {
            ValidationResult projectsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT projectid FROM project_x_dataset WHERE dataset_id = ?",
                    "projectid", "Project", "Dataset", "projects");
            if (!projectsResult.isValid) return projectsResult;

            ValidationResult processesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT processid FROM process_x_dataset WHERE datasetid = ?",
                    "processid", "Process", "Dataset", "processes");
            if (!processesResult.isValid) return processesResult;

            ValidationResult policiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT PolicyID FROM policy_x_dataset WHERE DatasetID = ?",
                    "PolicyID", "Policy", "Dataset", "policies");
            if (!policiesResult.isValid) return policiesResult;

            ValidationResult productsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Product_ID FROM product_x_dataset WHERE Dataset_ID = ?",
                    "Product_ID", "Product", "Dataset", "products");
            if (!productsResult.isValid) return productsResult;

            ValidationResult clientsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Client_ID FROM client_x_dataset WHERE Dataset_ID = ?",
                    "Client_ID", "Client", "Dataset", "clients");
            if (!clientsResult.isValid) return clientsResult;

            ValidationResult legalEntitiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Legal_ID FROM dataset_x_legal WHERE Dataset_ID = ?",
                    "Legal_ID", "LegalEntity", "Dataset", "legal entities");
            if (!legalEntitiesResult.isValid) return legalEntitiesResult;
        }

        if ("Glossary".equals(objectType)) {
            ValidationResult attributesResult = validateGlossarySegmentMoveWithAssignedAttributes(objectId, newSegmentId);
            if (!attributesResult.isValid) return attributesResult;

            ValidationResult projectsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Project_ID FROM glossary_x_project WHERE Glossary_ID = ?",
                    "Project_ID", "Project", "Glossary", "projects");
            if (!projectsResult.isValid) return projectsResult;

            ValidationResult processesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Process_ID FROM glossary_x_process WHERE Glossary_ID = ?",
                    "Process_ID", "Process", "Glossary", "processes");
            if (!processesResult.isValid) return processesResult;

            ValidationResult policiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT PolicyID FROM policy_x_glossary WHERE GlossaryID = ?",
                    "PolicyID", "Policy", "Glossary", "policies");
            if (!policiesResult.isValid) return policiesResult;

            ValidationResult productsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT productid AS Product_ID FROM product_x_glossary WHERE glossaryid = ?",
                    "Product_ID", "Product", "Glossary", "products");
            if (!productsResult.isValid) return productsResult;

            ValidationResult capabilitiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Capability_ID FROM capability_x_glossary WHERE Glossary_ID = ?",
                    "Capability_ID", "Capability", "Glossary", "capabilities");
            if (!capabilitiesResult.isValid) return capabilitiesResult;

            ValidationResult clientsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Client_ID FROM client_x_glossary WHERE Glossary_ID = ?",
                    "Client_ID", "Client", "Glossary", "clients");
            if (!clientsResult.isValid) return clientsResult;

            ValidationResult businessAreasResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT BusinessArea_ID FROM businessarea_x_glossary WHERE Glossary_ID = ?",
                    "BusinessArea_ID", "BusinessArea", "Glossary", "business areas");
            if (!businessAreasResult.isValid) return businessAreasResult;

            // Strategic Source links (Glossary ↔ System)
            ValidationResult systemsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT SystemID FROM glossary_x_system WHERE GlossaryID = ?",
                    "SystemID", "System", "Glossary", "systems");
            if (!systemsResult.isValid) return systemsResult;
        }

        if ("Committee".equals(objectType)) {
            ValidationResult capabilitiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Capability_ID FROM committee_x_capability WHERE Committee_ID = ?",
                    "Capability_ID", "Capability", "Committee", "capabilities");
            if (!capabilitiesResult.isValid) return capabilitiesResult;

            // Peer committee relationships (both directions)
            ValidationResult peerCommitteesAsSourceResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Target_ID AS peer_id FROM committee_x_committee WHERE Source_ID = ?",
                    "peer_id", "Committee", "Committee", "related committees");
            if (!peerCommitteesAsSourceResult.isValid) return peerCommitteesAsSourceResult;
            ValidationResult peerCommitteesAsTargetResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Source_ID AS peer_id FROM committee_x_committee WHERE Target_ID = ?",
                    "peer_id", "Committee", "Committee", "related committees");
            if (!peerCommitteesAsTargetResult.isValid) return peerCommitteesAsTargetResult;
        }

        if ("LegalEntity".equals(objectType)) {
            ValidationResult geographiesResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Geography_ID FROM legal_x_geography WHERE Legal_ID = ?",
                    "Geography_ID", "Geography", "Legal Entity", "geographies");
            if (!geographiesResult.isValid) return geographiesResult;

            ValidationResult datasetsResult = validateMoveAgainstLinkedObjects(
                    objectId, newSegmentId,
                    "SELECT DISTINCT Dataset_ID FROM dataset_x_legal WHERE Legal_ID = ?",
                    "Dataset_ID", "Dataset", "Legal Entity", "datasets");
            if (!datasetsResult.isValid) return datasetsResult;
        }

        // Check stakeholder access
        ValidationResult stakeholderResult = validateStakeholderAccess(objectId, newSegmentId, objectType);
        if (!stakeholderResult.isValid) {
            return stakeholderResult;
        }

        // Return parent result (may have warning that can be overridden)
        return parentResult;
    }

    /**
     * Reusable validation entrypoint for segment changes.
     * Validates parent/children + linked relationships before allowing segment update.
     */
    public ValidationResult validateSegmentChangeForRelationships(int objectId, int newSegmentId, String objectType)
            throws SQLException {
        ValidationResult result = validateSegmentMove(objectId, newSegmentId, objectType, null);
        if (!result.isValid && result.message != null && !result.message.contains(SEGMENT_RELATIONSHIP_BLOCK_MESSAGE)) {
            result.message = SEGMENT_RELATIONSHIP_BLOCK_MESSAGE + " " + result.message;
        } else if (!result.isValid && (result.message == null || result.message.isBlank())) {
            result.message = SEGMENT_RELATIONSHIP_BLOCK_MESSAGE;
        }
        return result;
    }

    /**
     * Validate that all stakeholders of an object have access to the new segment.
     * If any stakeholder doesn't have access, prevent the segment change.
     *
     * @param objectId The object ID
     * @param newSegmentId The new segment ID
     * @param objectType The object type (e.g., "Dataset", "System", "Project")
     * @return ValidationResult indicating if all stakeholders have access
     */
    public ValidationResult validateStakeholderAccess(int objectId, int newSegmentId, String objectType)
            throws SQLException {
        // Enterprise segment is accessible to all, so no need to check stakeholders
        if (newSegmentId == ENTERPRISE_SEGMENT_ID) {
            return ValidationResult.success();
        }

        // Get all stakeholders for this object
        List<Integer> stakeholderUserIds = getStakeholderUserIds(objectId, objectType);

        if (stakeholderUserIds.isEmpty()) {
            // No stakeholders - change is allowed
            return ValidationResult.success();
        }

        // Check if all stakeholders have access to the new segment
        List<Integer> stakeholdersWithoutAccess = new ArrayList<>();
        for (Integer userId : stakeholderUserIds) {
            if (userId == null || userId <= 0) continue;

            // Check if user has access to the new segment
            Set<Integer> accessibleSegments = SegmentAccessService.getAccessibleSegmentIds(userId);
            if (!accessibleSegments.contains(newSegmentId)) {
                stakeholdersWithoutAccess.add(userId);
            }
        }

        if (!stakeholdersWithoutAccess.isEmpty()) {
            // Get stakeholder names for error message
            List<String> stakeholderNames = getStakeholderNames(stakeholdersWithoutAccess);
            String segmentName = getSegmentName(newSegmentId);
            String namesList = String.join(", ", stakeholderNames);

            return ValidationResult.error(
                    String.format("Cannot change segment to '%s'. The following stakeholders do not have access to this segment: %s. " +
                                    "All stakeholders must have access to the new segment before the change can be made.",
                            segmentName, namesList)
            );
        }

        return ValidationResult.success();
    }

    /**
     * Validate stakeholder assignment.
     *
     * Current rule: any valid user can be added as a stakeholder regardless of
     * segment access. View/edit access is enforced separately at request time.
     *
     * @param userId The user ID to be added as stakeholder
     * @param objectId The object ID
     * @param objectType The object type (e.g., "Dataset", "System", "Project")
     * @return ValidationResult indicating whether assignment is allowed
     */
    public ValidationResult validateStakeholderCanBeAdded(int userId, int objectId, String objectType)
            throws SQLException {
        if (userId <= 0) {
            return ValidationResult.error("Invalid user ID");
        }

        // Get the segment this object belongs to
        int objectSegmentId = SegmentAccessService.getObjectSegmentId(objectId, objectType);

        // Enterprise segment (ID=1) or unassigned objects are accessible to everyone
        if (objectSegmentId <= 1) {
            return ValidationResult.success();
        }

        // Check if the person (userId = people.ID) has access to this segment
        boolean personHasAccess = SegmentAccessService.hasSegmentAccess(userId, objectSegmentId);
        if (!personHasAccess) {
            String personName = getPersonName(userId);
            String segmentName = getSegmentName(objectSegmentId);
            return ValidationResult.error(
                    "Cannot add \"" + personName + "\" as a stakeholder: they do not have access to the \"" +
                            segmentName + "\" segment. Please grant them access to this segment first, or choose a different person."
            );
        }

        return ValidationResult.success();
    }

    private String getPersonName(int userId) {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS name FROM people WHERE ID = ? AND Deleted_date IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    return name != null && !name.trim().isEmpty() ? name.trim() : "User #" + userId;
                }
            }
        } catch (SQLException e) {
            // Ignore — fallback below
        }
        return "User #" + userId;
    }

    /**
     * Get username by ID
     */
    @SuppressWarnings("unused")
    private String getUserName(int userId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS name FROM people WHERE ID = ? AND Deleted_date IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    return name != null && !name.trim().isEmpty() ? name.trim() : "User " + userId;
                }
            }
        }
        return "User " + userId;
    }

    /**
     * Get user IDs of all stakeholders for an object
     */
    private List<Integer> getStakeholderUserIds(int objectId, String objectType) throws SQLException {
        List<Integer> userIds = new ArrayList<>();

        // Map object type to stakeholder junction table
        String junctionTable = getStakeholderJunctionTable(objectType);
        String objectIdColumn = getStakeholderObjectIdColumn(objectType);
        String joinColumn = getStakeholderJoinColumn(objectType);

        if (junctionTable == null || objectIdColumn == null || joinColumn == null) {
            // Object type doesn't have stakeholders or mapping not found
            return userIds;
        }

        String sql = String.format("""
            SELECT DISTINCT oxp.ipid AS user_id
            FROM %s jt
            JOIN object_x_people oxp ON jt.%s = oxp.ID
            JOIN people p ON oxp.ipid = p.ID
            WHERE jt.%s = ?
            AND p.Deleted_date IS NULL
        """, junctionTable, joinColumn, objectIdColumn);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, objectId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int userId = rs.getInt("user_id");
                    if (userId > 0) {
                        userIds.add(userId);
                    }
                }
            }
        } catch (SQLException e) {
            // Some deployments do not have stakeholder junction tables for every object
            // type (e.g., geography_x_objectxpeople). Treat that as "no stakeholders"
            // so segment moves are still allowed instead of failing hard.
            boolean tableMissing = e.getErrorCode() == 1146 ||
                    (e.getMessage() != null && e.getMessage().toLowerCase().contains("doesn't exist"));
            if (tableMissing) {
                return userIds;
            }
            throw e;
        }

        return userIds;
    }

    /**
     * Get stakeholder names from user IDs
     */
    private List<String> getStakeholderNames(List<Integer> userIds) throws SQLException {
        List<String> names = new ArrayList<>();

        if (userIds.isEmpty()) {
            return names;
        }

        String placeholders = userIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = String.format("""
            SELECT CONCAT(First_Name, ' ', Last_Name) AS name
            FROM people
            WHERE ID IN (%s)
            AND Deleted_date IS NULL
        """, placeholders);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < userIds.size(); i++) {
                pstmt.setInt(i + 1, userIds.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null && !name.trim().isEmpty()) {
                        names.add(name.trim());
                    }
                }
            }
        }

        return names;
    }

    /**
     * Map object type to stakeholder junction table name
     */
    private String getStakeholderJunctionTable(String objectType) {
        return switch (objectType) {
            case "Dataset" -> "dataset_x_objectxpeople";
            case "System" -> "system_x_objectxpeople";
            case "Glossary" -> "glossary_x_objectxpeople";
            case "Process" -> "process_x_objectxpeople";
            case "Project" -> "project_x_objectxpeople";
            case "Product" -> "product_x_objectxpeople";
            case "Policy" -> "policy_x_objectxpeople";
            case "BusinessArea", "Business Area" -> "businessarea_x_objectxpeople";
            case "Capability" -> "capability_x_objectxpeople";
            case "Client" -> "client_x_objectxpeople";
            case "Committee" -> "committee_x_objectxpeople";
            case "LegalEntity", "Legal" -> "legal_x_objectxpeople";
            case "SystemInterface", "Interface" -> "interface_x_objectxpeople";
            case "RegulatoryTheme", "Regulatory Theme" -> "regulatorytheme_x_objectxpeople";
            case "Regulation" -> "regulation_x_objectxpeople";
            case "Regulator" -> "regulator_x_objectxpeople";
            case "Geography" -> "geography_x_objectxpeople";
            default -> null;
        };
    }

    /**
     * Map object type to object ID column name in stakeholder junction table
     */
    private String getStakeholderObjectIdColumn(String objectType) {
        return switch (objectType) {
            case "Dataset" -> "Dataset_ID";
            case "System" -> "SystemID";
            case "Glossary" -> "GlossaryID";
            case "Process" -> "process_id";
            case "Project" -> "project_id";
            case "Product" -> "product_id";
            case "Policy" -> "policy_id";
            case "BusinessArea", "Business Area" -> "BusinessAreaID";
            case "Capability" -> "CapabilityID";
            case "Client" -> "ClientID";
            case "Committee" -> "Committee_ID";
            case "LegalEntity", "Legal" -> "Legal_ID";
            case "SystemInterface", "Interface" -> "InterfaceID";
            case "RegulatoryTheme", "Regulatory Theme" -> "RegulatoryThemeID";
            case "Regulation" -> "RegulationID";
            case "Regulator" -> "RegulatorID";
            case "Geography" -> "GeographyID";
            default -> null;
        };
    }

    /**
     * Map object type to join column name in stakeholder junction table.
     */
    private String getStakeholderJoinColumn(String objectType) {
        return switch (objectType) {
            // These tables use lowercase join column.
            case "Process", "Project", "Product", "Policy", "LegalEntity", "Legal" -> "object_x_ip";
            // Most legacy stakeholder tables use this join column.
            default -> "Object_x_ipid";
        };
    }
}

