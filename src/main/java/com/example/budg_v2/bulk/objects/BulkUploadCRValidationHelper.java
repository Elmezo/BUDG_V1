package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.LockDAO;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.util.ModuleResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Helper class for validating bulk upload operations against change requests and locks.
 * Ensures objects can only be deleted/edited when appropriate based on CR status and lock state.
 */
public class BulkUploadCRValidationHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(BulkUploadCRValidationHelper.class);
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
    private final LockDAO lockDAO = new LockDAO();
    
    /**
     * Facet name to facet ID mapping
     */
    private static final java.util.Map<String, Integer> FACET_NAME_TO_ID = new java.util.HashMap<>();
    static {
        FACET_NAME_TO_ID.put("dataset", 11);
        FACET_NAME_TO_ID.put("data set", 11);
        FACET_NAME_TO_ID.put("system", 13);
        FACET_NAME_TO_ID.put("process", 4);
        FACET_NAME_TO_ID.put("glossary", 12);
    }
    
    /**
     * Facet name to module name mapping for lock checking
     */
    private static final java.util.Map<String, String> FACET_TO_MODULE = new java.util.HashMap<>();
    static {
        FACET_TO_MODULE.put("dataset", "Data Sets");
        FACET_TO_MODULE.put("data set", "Data Sets");
        FACET_TO_MODULE.put("system", "System");
        FACET_TO_MODULE.put("process", "Process");
        FACET_TO_MODULE.put("glossary", "Glossary");
    }
    
    /**
     * Validate that an object can be deleted.
     * Objects cannot be deleted if they have pending start or running change requests.
     * 
     * @param facetType The facet type name (e.g., "dataset", "system", "process", "glossary")
     * @param objectId The object ID
     * @param conn Database connection
     * @throws ValidationException if object cannot be deleted
     */
    public void validateObjectForDeletion(String facetType, int objectId, Connection conn) throws ValidationException {
        Integer facetId = getFacetId(facetType);
        if (facetId == null) {
            logger.warn("Unknown facet type: {}, skipping deletion validation", facetType);
            return; // Unknown facet type, skip validation
        }
        
        try {
            boolean hasActiveCRs = facetChangesDAO.hasActiveCRs(facetId, objectId);
            if (hasActiveCRs) {
                String entityName = getEntityDisplayName(facetType);
                throw new ValidationException(
                    entityName + " cannot be deleted. It has a pending start or running change request that must be completed or cancelled first."
                );
            }
        } catch (SQLException e) {
            logger.error("Error checking active CRs for {} {}: {}", facetType, objectId, e.getMessage(), e);
            throw new ValidationException("Error validating deletion: " + e.getMessage());
        }
    }
    
    /**
     * Validate that stakeholder removal is allowed for an object.
     * Stakeholders cannot be removed if the object has a running Change Request.
     * 
     * @param facetType The facet type name (e.g., "process", "system", "dataset", "glossary")
     * @param objectId The object ID
     * @throws ValidationException if the object has a running CR and stakeholder removal is not allowed
     */
    public void validateStakeholderRemovalAllowed(String facetType, int objectId) throws ValidationException {
        Integer facetId = getFacetId(facetType);
        if (facetId == null) {
            logger.debug("Unknown facet type: {}, skipping stakeholder removal validation", facetType);
            return; // Unknown facet type, no CR support for that entity
        }
        
        try {
            boolean hasRunning = facetChangesDAO.hasRunningCR(facetId, objectId);
            if (hasRunning) {
                String entityName = getEntityDisplayName(facetType);
                throw new ValidationException(
                    entityName + " has a running Change Request. Stakeholders cannot be removed until the CR is completed or cancelled."
                );
            }
        } catch (SQLException e) {
            logger.error("Error checking running CR for {} {}: {}", facetType, objectId, e.getMessage(), e);
            throw new ValidationException("Error validating stakeholder removal: " + e.getMessage());
        }
    }
    
    /**
     * Validate that an object can be edited.
     * 
     * Rules:
     * 1. Object must not be locked (unless locked by self)
     * 2. If object has running auto CR: block edit
     * 3. If object has pending start auto CR: only creator can edit
     * 4. If no active CR: allow edit (DFCRService will create CR if configured)
     * 
     * @param facetType The facet type name (e.g., "dataset", "system", "process", "glossary")
     * @param objectId The object ID
     * @param userId The user ID attempting the edit
     * @param conn Database connection
     * @throws ValidationException if object cannot be edited
     */
    public void validateObjectForEdit(String facetType, int objectId, int userId, Connection conn) throws ValidationException {
        // First: Check if object is locked
        if (isObjectLocked(facetType, objectId, userId, conn)) {
            String entityName = getEntityDisplayName(facetType);
            throw new ValidationException(
                entityName + " is currently locked and cannot be edited. Please unlock it first or wait for the lock to be released."
            );
        }
        
        // Second: Check for active auto CR
        Integer facetId = getFacetId(facetType);
        if (facetId == null) {
            logger.warn("Unknown facet type: {}, skipping CR validation", facetType);
            return; // Unknown facet type, skip validation
        }
        
        try {
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetId, objectId);
            
            if (activeCrId != null) {
                // Get CR details
                CRInfo crInfo = getActiveCRInfo(activeCrId, conn);
                
                if (crInfo == null) {
                    logger.warn("Could not retrieve CR info for CR ID: {}", activeCrId);
                    return; // Can't validate, allow edit
                }
                
                String statusLower = crInfo.statusName != null ? crInfo.statusName.toLowerCase() : "";
                String entityName = getEntityDisplayName(facetType);
                
                // Check if CR is running or in progress
                if (statusLower.contains("running") || statusLower.contains("in progress")) {
                    throw new ValidationException(
                        entityName + " cannot be edited. There is an active Change Request (status: " + crInfo.statusName + 
                        ") that must be completed or cancelled before editing is allowed."
                    );
                }
                
                // Check if CR is pending start
                if (statusLower.contains("pending start")) {
                    // Only creator can edit
                    if (crInfo.createdBy == null || crInfo.createdBy != userId) {
                        throw new ValidationException(
                            entityName + " cannot be edited. There is a pending start Change Request created by another user. " +
                            "Only the creator of the change request can edit this object."
                        );
                    }
                    // Creator can edit - DFCRService will reuse the existing CR
                    logger.info("Allowing edit for {} {} - pending start CR {} created by same user {}", 
                        facetType, objectId, activeCrId, userId);
                }
            }
            // If no active CR exists, allow edit (DFCRService will create new CR if configured)
            
        } catch (SQLException e) {
            logger.error("Error checking active CR for {} {}: {}", facetType, objectId, e.getMessage(), e);
            throw new ValidationException("Error validating edit: " + e.getMessage());
        }
    }
    
    /**
     * Get active CR information including status and creator
     * 
     * @param crId The change request ID
     * @param conn Database connection
     * @return CRInfo object with status and creator, or null if not found
     */
    public CRInfo getActiveCRInfo(Integer crId, Connection conn) {
        if (crId == null) {
            return null;
        }
        
        try {
            ChangeRequest cr = changeRequestDAO.getChangeRequestById(crId);
            if (cr == null) {
                return null;
            }
            
            CRInfo info = new CRInfo();
            info.crId = crId;
            info.createdBy = cr.getCreatedBy();
            
            // Get status name
            if (cr.getCrStatusId() != null) {
                String statusName = changeRequestDAO.getStatusNameById(cr.getCrStatusId());
                info.statusName = statusName;
            }
            
            return info;
        } catch (SQLException e) {
            logger.error("Error getting CR info for CR ID {}: {}", crId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check if an object is locked
     * 
     * @param facetType The facet type name
     * @param objectId The object ID
     * @param userId The user ID
     * @param conn Database connection
     * @return true if object is locked (and not by self), false otherwise
     */
    public boolean isObjectLocked(String facetType, int objectId, int userId, Connection conn) {
        try {
            String moduleName = FACET_TO_MODULE.get(facetType.toLowerCase());
            if (moduleName == null) {
                logger.warn("Unknown facet type for lock check: {}", facetType);
                return false; // Unknown facet, assume not locked
            }
            
            int moduleId = ModuleResolver.getModuleId(facetType.toLowerCase());
            java.util.Map<String, Object> lockInfo = lockDAO.checkLock(moduleId, objectId, userId);
            String lockStatus = (String) lockInfo.get("status");
            
            // Object is locked if status is not "no_lock" and not "locked_by_self"
            boolean isLocked = !"no_lock".equals(lockStatus) && !"locked_by_self".equals(lockStatus);
            
            if (isLocked) {
                boolean isPermanent = (Boolean) lockInfo.getOrDefault("isPermanent", false);
                String lockedBy = (String) lockInfo.getOrDefault("lockedByName", "another user");
                logger.info("Object {} {} is locked (permanent: {}, locked by: {})", facetType, objectId, isPermanent, lockedBy);
            }
            
            return isLocked;
        } catch (SQLException e) {
            logger.error("Error checking lock for {} {}: {}", facetType, objectId, e.getMessage(), e);
            return false; // On error, allow edit (fail open)
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown facet type for lock check: {}", facetType);
            return false; // Unknown facet, assume not locked
        }
    }
    
    /**
     * Get facet ID from facet name
     */
    private Integer getFacetId(String facetType) {
        if (facetType == null) return null;
        return FACET_NAME_TO_ID.get(facetType.toLowerCase().trim());
    }
    
    /**
     * Get entity display name for error messages
     */
    private String getEntityDisplayName(String facetType) {
        if (facetType == null) return "Object";
        String lower = facetType.toLowerCase().trim();
        switch (lower) {
            case "dataset":
            case "data set":
                return "Dataset";
            case "system":
                return "System";
            case "process":
                return "Process";
            case "glossary":
                return "Glossary";
            default:
                return facetType;
        }
    }
    
    /**
     * Information about an active change request
     */
    public static class CRInfo {
        public Integer crId;
        public String statusName;
        public Integer createdBy;
    }
    
    /**
     * Exception thrown when validation fails
     */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}

