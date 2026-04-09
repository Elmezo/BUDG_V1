package com.example.budg_v2.bulk.roles.handlers;

import com.example.budg_v2.bulk.roles.base.RoleUploadHandler;
import com.example.budg_v2.bulk.roles.util.RoleHandlerUtil;
import com.example.budg_v2.bulk.common.BulkUploadUtil;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.ObjectSegmentService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for Regulation Role bulk uploads
 * Manages role assignments to regulations through regulation_x_objectxpeople table
 */
public class RegulationRoleHandler implements RoleUploadHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(RegulationRoleHandler.class);
    private static final String ENTITY_NAME = "Regulation";
    private static final String LINKING_TABLE = "regulation_x_objectxpeople";
    
    @Override
    public String getRoleType() {
        return "Regulation Role";
    }
    
    @Override
    public String getEntityName() {
        return ENTITY_NAME;
    }
    
    @Override
    public String getLinkingTableName() {
        return LINKING_TABLE;
    }
    
    @Override
    public void insert(Connection conn, JsonObject rowData, int userId) throws SQLException {
        Integer regulationId = BulkUploadUtil.getInteger(rowData, "Regulation_ID");
        Integer personId = BulkUploadUtil.getInteger(rowData, "Person_ID");
        Integer roleId = BulkUploadUtil.getInteger(rowData, "Role_ID");
        
        if (regulationId == null || personId == null || roleId == null) {
            throw new IllegalArgumentException("Required IDs are missing");
        }
        
        logger.debug("Inserting role assignment: Regulation={}, Person={}, Role={}", regulationId, personId, roleId);
        
        try {
            // Step 1: Create object_x_people record
            String insertOxpSql = """
                INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            
            // Step 1: Check if object_x_people record already exists
            Integer objectXPeopleId = RoleHandlerUtil.findObjectXPeopleId(conn, personId, roleId);
            
            if (objectXPeopleId == null) {
                // Step 2: Create new object_x_people record if it doesn't exist
                try (PreparedStatement ps = conn.prepareStatement(insertOxpSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setObject(1, null);
                    ps.setInt(2, personId);
                    ps.setInt(3, roleId);
                    ps.setInt(4, 1);
                    ps.setInt(5, 1);
                    ps.setInt(6, userId);
                    
                    ps.executeUpdate();
                    
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            objectXPeopleId = rs.getInt(1);
                        } else {
                            throw new SQLException("Failed to get generated object_x_people ID");
                        }
                    }
                }
                logger.debug("Created new object_x_people with ID: {}", objectXPeopleId);
            } else {
                logger.debug("Using existing object_x_people with ID: {}", objectXPeopleId);
            }
            
            // Step 3: Check if link already exists before attempting to link
            RoleHandlerUtil.checkLinkNotExists(conn, LINKING_TABLE, "RegulationID", regulationId, "Object_x_ipid", objectXPeopleId, ENTITY_NAME);
            
            // Step 4: Link to regulation
            String linkSql = """
                INSERT INTO regulation_x_objectxpeople (RegulationID, Object_x_ipid, CreateDatetime)
                VALUES (?, ?, NOW())
                """;
            
            try (PreparedStatement ps = conn.prepareStatement(linkSql)) {
                ps.setInt(1, regulationId);
                ps.setInt(2, objectXPeopleId);
                ps.executeUpdate();
            }
            
            logger.debug("Linked stakeholder to regulation: Regulation={}, ObjectXPeople={}", regulationId, objectXPeopleId);
            logger.info("Successfully assigned role {} to person {} for regulation {}", roleId, personId, regulationId);
            
        } catch (SQLException e) {
            logger.error("Error inserting role assignment: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public void delete(Connection conn, JsonObject rowData, int userId) throws SQLException {
        Integer regulationId = BulkUploadUtil.getInteger(rowData, "Regulation_ID");
        Integer personId = BulkUploadUtil.getInteger(rowData, "Person_ID");
        Integer roleId = BulkUploadUtil.getInteger(rowData, "Role_ID");
        
        if (regulationId == null || personId == null || roleId == null) {
            throw new IllegalArgumentException("Required IDs are missing");
        }
        
        logger.debug("Deleting role assignment: Regulation={}, Person={}, Role={}", regulationId, personId, roleId);
        
        String findOxpSql = "SELECT ID FROM object_x_people WHERE ipid = ? AND RoleID = ?";
        List<Integer> objectXPeopleIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(findOxpSql)) {
            ps.setInt(1, personId);
            ps.setInt(2, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) objectXPeopleIds.add(rs.getInt("ID"));
            }
        }
        if (objectXPeopleIds.isEmpty()) {
            throw new SQLException("No role assignment found for this " + ENTITY_NAME + ". The person may not have this role assigned, or the role assignment may have already been removed.");
        }
        int totalRowsAffected = 0;
        String deleteSql = "DELETE FROM " + LINKING_TABLE + " WHERE RegulationID = ? AND Object_x_ipid = ?";
        for (Integer objectXPeopleId : objectXPeopleIds) {
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, regulationId);
                ps.setInt(2, objectXPeopleId);
                totalRowsAffected += ps.executeUpdate();
            }
        }
        if (totalRowsAffected == 0) {
            throw new SQLException("No role assignment found to delete for this " + ENTITY_NAME + ". The role assignment may have already been removed.");
        }
        logger.info("Successfully removed {} role assignment(s) from regulation: Regulation={}, Person={}, Role={}",
            totalRowsAffected, regulationId, personId, roleId);
    }
    
    @Override
    public JsonObject validateRow(JsonObject rowData, String operation, int userId) {
        JsonObject basicValidation = RoleHandlerUtil.validateBasicIds(rowData, "Regulation_ID", ENTITY_NAME);
        if (basicValidation.has("error")) {
            return basicValidation;
        }
        
        Integer regulationId = BulkUploadUtil.getInteger(rowData, "Regulation_ID");
        Integer personId = BulkUploadUtil.getInteger(rowData, "Person_ID");
        Integer roleId = BulkUploadUtil.getInteger(rowData, "Role_ID");
        if (regulationId == null || personId == null || roleId == null) {
            return BulkUploadUtil.createValidationError("Regulation_ID, Person_ID, and Role_ID are required.");
        }
        final int regulationIdVal = regulationId.intValue();
        final int personIdVal = personId.intValue();
        final int roleIdVal = roleId.intValue();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Segment access validation - check if user has access to this object
            // Super Admin can access all objects, skip validation
            if (!SegmentAccessService.isSuperAdmin(userId)) {
                String segmentObjectType = RoleHandlerUtil.mapEntityNameToSegmentObjectType(ENTITY_NAME);
                if (segmentObjectType != null && regulationId != null) {
                    if (!SegmentAccessService.canAccessObject(userId, regulationId, segmentObjectType)) {
                        return BulkUploadUtil.createValidationError(
                            "You do not have access to modify roles for this " + ENTITY_NAME + ". Access is restricted based on segment assignments.");
                    }
                }
            }
            
            if (!RoleHandlerUtil.isRoleInModule(conn, roleIdVal, ENTITY_NAME)) {
                return BulkUploadUtil.createValidationError("Role is not assigned to " + ENTITY_NAME + " module");
            }
            
            // Role assignment validation - validate user is assigned to the role in template (for INSERT only)
            if ("INSERT".equalsIgnoreCase(operation) && personId != null && roleId != null) {
                try {
                    DefaultStakeholderUtil.ValidationResult roleAssignmentValidation = 
                        DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, personId, roleId);
                    if (!roleAssignmentValidation.isValid()) {
                        return BulkUploadUtil.createValidationError(roleAssignmentValidation.getWarningMessage());
                    }
                } catch (SQLException e) {
                    logger.error("Error validating role assignment: {}", e.getMessage(), e);
                    return BulkUploadUtil.createValidationError("Unable to verify role assignment; user not allowed.");
                }
            }
            
            // Segment access validation for stakeholders - check if stakeholder has access to object's segment
            if ("INSERT".equalsIgnoreCase(operation) && personId != null && regulationId != null) {
                try {
                    Long objectSegmentId = ObjectSegmentService.getObjectSegment((long) regulationId, ENTITY_NAME);
                    if (objectSegmentId != null && objectSegmentId > 1) {
                        // Object is in a private segment (not Enterprise)
                        boolean hasAccess = SegmentAccessService.hasSegmentAccess(personId, objectSegmentId.intValue());
                        if (!hasAccess) {
                            SegmentValidationService validator = new SegmentValidationService();
                            String segmentName = validator.getSegmentName(objectSegmentId.intValue());
                            String personName = RoleHandlerUtil.getPersonName(conn, personId);
                            return BulkUploadUtil.createValidationError(
                                String.format("Cannot add stakeholder '%s' to %s. The stakeholder does not have access to segment '%s'. All stakeholders must have access to the object's segment.",
                                    personName != null ? personName : "User " + personId, ENTITY_NAME, segmentName != null ? segmentName : "Unknown"));
                        }
                    }
                } catch (SQLException e) {
                    logger.error("Error validating stakeholder segment access: {}", e.getMessage(), e);
                    // Don't fail validation on error, but log it
                }
            }
            
            if ("INSERT".equalsIgnoreCase(operation)) {
                if (RoleHandlerUtil.isDuplicateAssignment(conn, regulationIdVal, personIdVal, roleIdVal,
                        LINKING_TABLE, "RegulationID", "Object_x_ipid", ENTITY_NAME)) {
                    return BulkUploadUtil.createValidationError("This role assignment already exists for this " + ENTITY_NAME);
                }
            } else if ("DELETE".equalsIgnoreCase(operation)) {
                // Prevent deleting when user specified the wrong role: if this person has only one role on this regulation and it is not the one in the file, reject
                List<Integer> rolesOnEntity = RoleHandlerUtil.getRoleIdsForPersonOnEntity(conn, regulationIdVal, personIdVal,
                    LINKING_TABLE, "RegulationID", "Object_x_ipid");
                if (rolesOnEntity.size() == 1 && !rolesOnEntity.get(0).equals(roleId)) {
                    int actualRoleId = rolesOnEntity.get(0).intValue();
                    String actualRoleName = RoleHandlerUtil.getRoleName(conn, actualRoleId);
                    return BulkUploadUtil.createValidationError(
                        "The specified role does not match the stakeholder's role on this " + ENTITY_NAME + ". "
                        + "This person has only one role on this object: " + (actualRoleName != null ? "'" + actualRoleName + "'" : "Role ID " + actualRoleId) + ". "
                        + "Specify that role to remove the assignment.");
                }
                String errorMessage = RoleHandlerUtil.checkAssignmentExistsWithDetails(
                    conn, regulationIdVal, personIdVal, roleIdVal,
                    LINKING_TABLE, "RegulationID", "Object_x_ipid", ENTITY_NAME,
                    "regulation", "PrimaryName");
                if (errorMessage != null) {
                    return BulkUploadUtil.createValidationError(errorMessage);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Database error during validation: {}", e.getMessage(), e);
            return BulkUploadUtil.createValidationError("Database error during validation: " + e.getMessage());
        }
        
        return BulkUploadUtil.createValidationSuccess();
    }
}

