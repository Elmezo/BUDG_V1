package com.example.budg_v2.bulk.roles.handlers;

import com.example.budg_v2.bulk.roles.base.RoleUploadHandler;
import com.example.budg_v2.bulk.roles.util.RoleHandlerUtil;
import com.example.budg_v2.bulk.common.BulkUploadUtil;
import com.example.budg_v2.bulk.objects.BulkUploadCRValidationHelper;
import com.example.budg_v2.dao.DatasetDAO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for Data Set Role bulk uploads
 * Manages role assignments to datasets through dataset_x_objectxpeople table
 */
public class DataSetRoleHandler implements RoleUploadHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSetRoleHandler.class);
    private static final String ENTITY_NAME = "Data Sets";
    private static final String LINKING_TABLE = "dataset_x_objectxpeople";
    
    @Override
    public String getRoleType() {
        return "Data Set Role";
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
        Integer datasetId = BulkUploadUtil.getInteger(rowData, "Dataset_ID");
        Integer personId = BulkUploadUtil.getInteger(rowData, "Person_ID");
        Integer roleId = BulkUploadUtil.getInteger(rowData, "Role_ID");
        
        if (datasetId == null || personId == null || roleId == null) {
            throw new IllegalArgumentException("Required IDs are missing");
        }
        
        logger.debug("Inserting role assignment: Dataset={}, Person={}, Role={}", datasetId, personId, roleId);
        
        try {
            DatasetDAO datasetDAO = new DatasetDAO();
            
            // Step 1: Check if object_x_people record already exists
            Integer objectXPeopleId = RoleHandlerUtil.findObjectXPeopleId(conn, personId, roleId);
            
            if (objectXPeopleId == null) {
                // Step 2: Create new object_x_people record if it doesn't exist
                Map<String, Object> stakeholderData = new HashMap<>();
                stakeholderData.put("userId", personId);
                stakeholderData.put("roleId", roleId);
                
                objectXPeopleId = datasetDAO.createObjectXPeople(conn, stakeholderData, userId);
                logger.debug("Created new object_x_people with ID: {}", objectXPeopleId);
            } else {
                logger.debug("Using existing object_x_people with ID: {}", objectXPeopleId);
            }
            
            // Step 3: Check if link already exists before attempting to link
            RoleHandlerUtil.checkLinkNotExists(conn, LINKING_TABLE, "Dataset_ID", datasetId, "Object_x_ipid", objectXPeopleId, ENTITY_NAME);
            
            // Step 4: Link to dataset
            datasetDAO.linkStakeholderToDataset(conn, datasetId, objectXPeopleId, userId);
            logger.debug("Linked stakeholder to dataset: Dataset={}, ObjectXPeople={}", datasetId, objectXPeopleId);
            
            logger.info("Successfully assigned role {} to person {} for dataset {}", roleId, personId, datasetId);
            
        } catch (SQLException e) {
            logger.error("Error inserting role assignment: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public void delete(Connection conn, JsonObject rowData, int userId) throws SQLException {
        Integer datasetId = BulkUploadUtil.getInteger(rowData, "Dataset_ID");
        Integer personId = BulkUploadUtil.getInteger(rowData, "Person_ID");
        Integer roleId = BulkUploadUtil.getInteger(rowData, "Role_ID");
        
        if (datasetId == null || personId == null || roleId == null) {
            throw new IllegalArgumentException("Required IDs are missing");
        }
        
        logger.debug("Deleting role assignment: Dataset={}, Person={}, Role={}", datasetId, personId, roleId);
        
        // Find ALL object_x_people IDs for this person+role combination
        String findOxpSql = "SELECT ID FROM object_x_people WHERE ipid = ? AND RoleID = ?";
        List<Integer> objectXPeopleIds = new ArrayList<>();
        
        try (PreparedStatement ps = conn.prepareStatement(findOxpSql)) {
            ps.setInt(1, personId);
            ps.setInt(2, roleId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    objectXPeopleIds.add(rs.getInt("ID"));
                }
            }
        }
        
        if (objectXPeopleIds.isEmpty()) {
            throw new SQLException("No role assignment found for this " + ENTITY_NAME + ". The person may not have this role assigned, or the role assignment may have already been removed.");
        }
        
        // Delete ALL links for all found object_x_people IDs for this dataset
        int totalRowsAffected = 0;
        String deleteSql = "DELETE FROM " + LINKING_TABLE + " WHERE Dataset_ID = ? AND Object_x_ipid = ?";
        
        for (Integer objectXPeopleId : objectXPeopleIds) {
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, datasetId);
                ps.setInt(2, objectXPeopleId);
                totalRowsAffected += ps.executeUpdate();
            }
        }
        
        if (totalRowsAffected == 0) {
            throw new SQLException("No role assignment found to delete for this " + ENTITY_NAME + ". The role assignment may have already been removed.");
        }
        
        logger.info("Successfully removed {} role assignment(s) from dataset: Dataset={}, Person={}, Role={}",
            totalRowsAffected, datasetId, personId, roleId);
    }
    
    @Override
    public JsonObject validateRow(JsonObject rowData, String operation, int userId) {
        JsonObject basicValidation = RoleHandlerUtil.validateBasicIds(rowData, "Dataset_ID", ENTITY_NAME);
        if (basicValidation.has("error")) {
            return basicValidation;
        }
        
        Integer datasetId = BulkUploadUtil.getInteger(rowData, "Dataset_ID");
        Integer personId = BulkUploadUtil.getInteger(rowData, "Person_ID");
        Integer roleId = BulkUploadUtil.getInteger(rowData, "Role_ID");
        if (datasetId == null || personId == null || roleId == null) {
            return BulkUploadUtil.createValidationError("Dataset_ID, Person_ID, and Role_ID are required.");
        }
        final int entityId = datasetId.intValue();
        final int personIdVal = personId.intValue();
        final int roleIdVal = roleId.intValue();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Segment access validation - check if user has access to this object
            // Super Admin can access all objects, skip validation
            if (!SegmentAccessService.isSuperAdmin(userId)) {
                String segmentObjectType = RoleHandlerUtil.mapEntityNameToSegmentObjectType(ENTITY_NAME);
                if (segmentObjectType != null && datasetId != null) {
                    if (!SegmentAccessService.canAccessObject(userId, datasetId, segmentObjectType)) {
                        return BulkUploadUtil.createValidationError(
                            "You do not have access to modify roles for this " + ENTITY_NAME + ". Access is restricted based on segment assignments.");
                    }
                }
            }
            
            if (!RoleHandlerUtil.isRoleInModule(conn, roleId, ENTITY_NAME)) {
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
            if ("INSERT".equalsIgnoreCase(operation) && personId != null && datasetId != null) {
                try {
                    Long objectSegmentId = ObjectSegmentService.getObjectSegment((long) datasetId, "Dataset");
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
                if (RoleHandlerUtil.isDuplicateAssignment(conn, entityId, personIdVal, roleIdVal,
                        LINKING_TABLE, "Dataset_ID", "Object_x_ipid", ENTITY_NAME)) {
                    return BulkUploadUtil.createValidationError("This role assignment already exists for this " + ENTITY_NAME);
                }
            } else if ("DELETE".equalsIgnoreCase(operation)) {
                String errorMessage = RoleHandlerUtil.checkAssignmentExistsWithDetails(
                    conn, entityId, personIdVal, roleIdVal,
                    LINKING_TABLE, "Dataset_ID", "Object_x_ipid", ENTITY_NAME,
                    "dataset", "PrimaryName");
                if (errorMessage != null) {
                    return BulkUploadUtil.createValidationError(errorMessage);
                }
                try {
                    new BulkUploadCRValidationHelper().validateStakeholderRemovalAllowed("dataset", entityId);
                } catch (BulkUploadCRValidationHelper.ValidationException e) {
                    return BulkUploadUtil.createValidationError(e.getMessage());
                }
            }
            
        } catch (SQLException e) {
            logger.error("Database error during validation: {}", e.getMessage(), e);
            return BulkUploadUtil.createValidationError("Database error during validation: " + e.getMessage());
        }
        
        return BulkUploadUtil.createValidationSuccess();
    }
}

