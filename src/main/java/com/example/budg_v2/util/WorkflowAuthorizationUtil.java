package com.example.budg_v2.util;

import com.example.budg_v2.dao.CRStakeholderDAO;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.model.ChangeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Utility class for workflow authorization checks
 */
public class WorkflowAuthorizationUtil {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowAuthorizationUtil.class);
    private static final CRStakeholderDAO stakeholderDAO = new CRStakeholderDAO();
    private static final ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();

    /**
     * Check if user is Requestor (creator of change request)
     * @param userId User ID to check
     * @param changeRequestId Change request ID
     * @return true if user is the creator, false otherwise
     */
    public static boolean isUserRequestor(int userId, int changeRequestId) {
        try {
            ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
            
            if (cr == null) {
                logger.warn("Change request {} not found", changeRequestId);
                return false;
            }
            
            Integer creatorId = cr.getCreatedBy();
            boolean isRequestor = creatorId != null && creatorId == userId;
            
            if (isRequestor) {
                logger.info("User {} is the creator (Requestor) of CR {}", userId, changeRequestId);
            } else {
                logger.debug("User {} is not the creator of CR {} (creator: {})", userId, changeRequestId, creatorId);
            }
            
            return isRequestor;
        } catch (Exception e) {
            logger.error("Error checking if user is requestor", e);
            return false;
        }
    }

    /**
     * Check if user has the required stakeholder role for a task
     * @param userId User ID to check
     * @param changeRequestId Change request ID
     * @param taskRoleName Normalized role name from task (e.g., "DATASET_OWNER" or "REQUESTOR")
     * @return true if user has matching role, false otherwise
     */
    public static boolean checkUserHasRoleForTask(int userId, int changeRequestId, String taskRoleName) {
        try {
            // Normalize task role name for comparison
            String normalizedTaskRole = normalizeRoleName(taskRoleName);
            
            logger.debug("Checking role for user {} in CR {}: looking for role '{}' (normalized: '{}')", 
                userId, changeRequestId, taskRoleName, normalizedTaskRole);
            
            // Check Requestor first
            if ("REQUESTOR".equals(normalizedTaskRole)) {
                return isUserRequestor(userId, changeRequestId);
            }
            
            // Get change request to get its reference (to fetch stakeholders from source object)
            ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (cr == null) {
                logger.warn("Change request {} not found", changeRequestId);
                return false;
            }
            
            String reference = cr.getReference();
            if (reference == null || reference.trim().isEmpty()) {
                logger.warn("Change request {} has no reference", changeRequestId);
                return false;
            }
            
            // Get all stakeholders from the source object (same as frontend API)
            List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersFromSourceObject(reference);
            logger.debug("Found {} stakeholders from source object for CR {} (reference: {})", 
                stakeholders.size(), changeRequestId, reference);
            
            // Check if user is a stakeholder with matching role
            for (Map<String, Object> stakeholder : stakeholders) {
                // Handle different field names: personId, userId, User_ID, user_ID
                Integer stakeholderUserId = null;
                if (stakeholder.containsKey("personId")) {
                    Object userIdObj = stakeholder.get("personId");
                    if (userIdObj instanceof Number) {
                        stakeholderUserId = ((Number) userIdObj).intValue();
                    }
                } else if (stakeholder.containsKey("userId")) {
                    Object userIdObj = stakeholder.get("userId");
                    if (userIdObj instanceof Number) {
                        stakeholderUserId = ((Number) userIdObj).intValue();
                    }
                } else if (stakeholder.containsKey("User_ID")) {
                    Object userIdObj = stakeholder.get("User_ID");
                    if (userIdObj instanceof Number) {
                        stakeholderUserId = ((Number) userIdObj).intValue();
                    }
                } else if (stakeholder.containsKey("user_ID")) {
                    Object userIdObj = stakeholder.get("user_ID");
                    if (userIdObj instanceof Number) {
                        stakeholderUserId = ((Number) userIdObj).intValue();
                    }
                }
                
                String stakeholderRoleName = (String) stakeholder.get("roleName");
                
                logger.debug("Checking stakeholder: userId={}, roleName={}", stakeholderUserId, stakeholderRoleName);
                
                if (stakeholderUserId != null && stakeholderUserId == userId) {
                    if (stakeholderRoleName != null) {
                        String normalizedStakeholderRole = normalizeRoleName(stakeholderRoleName);
                        if (normalizedTaskRole.equals(normalizedStakeholderRole)) {
                            logger.info("User {} has matching role {} for task", userId, normalizedTaskRole);
                            return true;
                        } else {
                            logger.debug("User {} has role '{}' (normalized: '{}') but task requires '{}'", 
                                userId, stakeholderRoleName, normalizedStakeholderRole, normalizedTaskRole);
                        }
                    } else {
                        logger.debug("User {} is a stakeholder but has no role name", userId);
                    }
                }
            }
            
            logger.warn("User {} does not have role {} for CR {}. Available stakeholders: {}", 
                userId, normalizedTaskRole, changeRequestId, stakeholders.size());
            return false;
            
        } catch (SQLException e) {
            logger.error("Error checking user role for task", e);
            return false;
        }
    }

    /**
     * Normalize role name: trim → remove ID prefix if present → uppercase → replace spaces with underscores
     * Examples: 
     *   "3:DATASET_OWNER" → "DATASET_OWNER"
     *   "Dataset Owner" → "DATASET_OWNER"
     *   "DATASET_OWNER" → "DATASET_OWNER"
     * @param roleName Original role name
     * @return Normalized role name
     */
    public static String normalizeRoleName(String roleName) {
        if (roleName == null) {
            return null;
        }
        
        // Remove ID prefix if present (e.g., "3:DATASET_OWNER" -> "DATASET_OWNER")
        String cleanRole = roleName.trim();
        if (cleanRole.contains(":")) {
            int colonIndex = cleanRole.indexOf(':');
            cleanRole = cleanRole.substring(colonIndex + 1).trim();
        }
        
        return cleanRole.toUpperCase().replaceAll("\\s+", "_");
    }
}
