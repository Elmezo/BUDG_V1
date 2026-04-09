package com.example.budg_v2.service;

import com.example.budg_v2.dao.DFCRDao;
import com.example.budg_v2.dao.DFCRTypeSettingsDAO;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.ProcessDefinitionDAO;
import com.example.budg_v2.model.DFCR;
import com.example.budg_v2.model.DFCRTypeSetting;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Service for applying Default Change Request (DF_CR) settings at runtime.
 * 
 * This service is used when creating or editing objects in facets that have
 * DF_CR settings configured. It:
 * - Enforces default status and lifecycle on objects
 * - Auto-creates change requests when workflow approval is enabled
 * - Handles admin bypass logic
 */
public class DFCRService {

    private static final Logger logger = LoggerFactory.getLogger(DFCRService.class);
    private final DFCRDao dfcrDao = new DFCRDao();
    private final DFCRTypeSettingsDAO typeSettingsDao = new DFCRTypeSettingsDAO();
    private final ChangeRequestDAO changeRequestDao = new ChangeRequestDAO();
    private final ProcessDefinitionDAO processDefinitionDAO = new ProcessDefinitionDAO();
    
    // Simple in-memory cache for DFCR settings (to reduce DB queries)
    private static final java.util.Map<String, DFCR> settingsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 60000; // 1 minute cache

    // Cache for CREATE CR lookups (to reduce DB queries under concurrent load)
    private static final java.util.Map<String, CachedCreateCR> createCRCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CREATE_CR_CACHE_TTL_MS = 30000; // 30 seconds cache
    
    private static class CachedCreateCR {
        final Integer crId;
        final long timestamp;
        CachedCreateCR(Integer crId) {
            this.crId = crId;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < CREATE_CR_CACHE_TTL_MS;
        }
    }

    /**
     * Get DF_CR settings for a facet (with simple caching to reduce DB queries)
     */
    public DFCR getSettingsForFacet(String facetName) {
        // Check cache first (if not expired)
        long now = System.currentTimeMillis();
        if (now - cacheTimestamp < CACHE_TTL_MS && settingsCache.containsKey(facetName)) {
            logger.debug("[DFCR] Using cached settings for facet: '{}'", facetName);
            DFCR cached = settingsCache.get(facetName);
            return cached; // Can be null, which is fine
        }
        
        try {
            logger.info("[DFCR] Looking up settings for facet: '{}'", facetName);
            DFCR result = dfcrDao.getByFacetName(facetName);
            if (result != null) {
                logger.info("[DFCR] Settings FOUND for '{}' - workflowEnabled={}, workflowCreateId={}, workflowEditId={}, adminBypass={}, facetId={}", 
                    facetName, result.isWorkflowApprovalEnabled(), result.getWorkflowCreateId(), result.getWorkflowEditId(), 
                    result.isAdminWorkflowBypass(), result.getFacetId());
            } else {
                logger.info("[DFCR] Settings NOT FOUND for facet: '{}'", facetName);
            }
            // Update cache (only if not null - ConcurrentHashMap doesn't allow null values)
            if (result != null) {
                settingsCache.put(facetName, result);
            }
            cacheTimestamp = now;
            return result;
        } catch (SQLException e) {
            logger.error("Error loading DF_CR settings for facet: {}", facetName, e);
            return null;
        }
    }

    /**
     * Clear cache for a specific facet (called after settings are updated)
     * This ensures that changes to "Enable Workflow Approval for Administrators" 
     * and other settings are immediately reflected
     */
    public void clearCacheForFacet(String facetName) {
        if (facetName != null && settingsCache.containsKey(facetName)) {
            settingsCache.remove(facetName);
            logger.info("[DFCR] Cleared cache for facet: '{}' to ensure fresh settings are loaded", facetName);
        }
    }

    /**
     * Clear all cache (called after settings are updated for any facet)
     */
    public void clearAllCache() {
        settingsCache.clear();
        cacheTimestamp = 0;
        logger.info("[DFCR] Cleared all cache to ensure fresh settings are loaded");
    }

    /**
     * Check if workflow approval is enabled for a facet
     */
    public boolean isWorkflowApprovalEnabled(String facetName) {
        DFCR dfcr = getSettingsForFacet(facetName);
        return dfcr != null && dfcr.isWorkflowApprovalEnabled();
    }

    /**
     * Check if admin can bypass workflow for a facet
     */
    public boolean canAdminBypassWorkflow(String facetName) {
        DFCR dfcr = getSettingsForFacet(facetName);
        // If no settings or workflow not enabled, no bypass needed
        if (dfcr == null || !dfcr.isWorkflowApprovalEnabled()) {
            return true;
        }
        return dfcr.isAdminWorkflowBypass();
    }

    /**
     * Get the default status ID for object creation in a facet
     */
    public Integer getDefaultStatusId(String facetName) {
        DFCR dfcr = getSettingsForFacet(facetName);
        return dfcr != null ? dfcr.getStatusId() : null;
    }

    /**
     * Get the default lifecycle ID for object creation in a facet
     */
    public Integer getDefaultLifecycleId(String facetName) {
        DFCR dfcr = getSettingsForFacet(facetName);
        return dfcr != null ? dfcr.getLifecycleId() : null;
    }

    /**
     * Apply DF_CR defaults when creating an object.
     * Returns the created change request ID if workflow is enabled, null otherwise.
     * 
     * @param facetName The facet name (e.g., "Glossary", "Data Set", "System")
     * @param objectId The object ID
     * @param objectTypeId The object's type ID (e.g., glossary_type.ID, dataset_type.ID, system_type.id)
     * @param userId The user ID
     * @param isAdmin Whether the user is an admin
     */
    public Integer applyDefaultsOnCreate(String facetName, int objectId, Integer objectTypeId, int userId, boolean isAdmin) {
        try {
            logger.info("[DFCR] applyDefaultsOnCreate - facet: {}, objectId: {}, objectTypeId: {}, userId: {}, isAdmin: {}", 
                facetName, objectId, objectTypeId, userId, isAdmin);
            
            DFCR dfcr = getSettingsForFacet(facetName);
            logger.info("[DFCR] Retrieved DF_CR settings for {}: {}", facetName, dfcr != null ? "FOUND" : "NULL");
            
            if (dfcr == null || !dfcr.isWorkflowApprovalEnabled()) {
                logger.info("[DFCR] No DF_CR settings or workflow not enabled for facet: {} (dfcr={}, workflowEnabled={})", 
                    facetName, dfcr != null ? "exists" : "null", dfcr != null ? dfcr.isWorkflowApprovalEnabled() : "N/A");
                return null;
            }

            // Check admin bypass - IMPORTANT: Admin bypass ONLY affects admins/super-admins, NOT regular users
            // - If "Enable Workflow Approval for Administrators" is CHECKED (adminBypass=false): 
            //   - Admins/Super-admins: Create CRs (Auto CR works)
            //   - Regular users: Create CRs (Auto CR works)
            // - If "Enable Workflow Approval for Administrators" is UNCHECKED (adminBypass=true):
            //   - Admins/Super-admins: Do NOT create CRs (Auto CR disabled) - BYPASSED
            //   - Regular users: STILL CREATE CRs (Auto CR works) - NOT affected by bypass
            boolean isSuperAdmin = false;
            try {
                isSuperAdmin = com.example.budg_v2.service.SegmentAccessService.isSuperAdmin(userId);
            } catch (SQLException e) {
                logger.warn("[DFCR] Error checking super admin status for user {}: {}", userId, e.getMessage());
            }
            
            // If admin/super admin AND bypass is enabled (button unchecked) → return early (no CR creation for admins only)
            if ((isAdmin || isSuperAdmin) && dfcr.isAdminWorkflowBypass()) {
                logger.info("[DFCR] Admin/SuperAdmin with bypass enabled (button unchecked) - Auto CR disabled for admin, no CR will be created for facet: {}", 
                    facetName);
                return null; // No CR creation - Auto CR disabled for admins
            }
            
            // Regular users are NOT affected by admin bypass - they always get auto CR creation when workflow is enabled
            if (!isAdmin && !isSuperAdmin && dfcr.isAdminWorkflowBypass()) {
                logger.info("[DFCR] Regular user detected with admin bypass enabled - Admin bypass does NOT apply to regular users, will create auto CR for facet: {}", facetName);
            }
            
            if (isAdmin || isSuperAdmin) {
                logger.info("[DFCR] Admin/SuperAdmin - Auto CR enabled, will create CR for facet: {}", facetName);
            }

            // ⚠️ CRITICAL FIX: Check for existing active automatic CR before creating new one (prevents duplicates)
            // DFCR only creates automatic CRs, so we only check for automatic CRs
            com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
            Integer existingCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(dfcr.getFacetId(), objectId);
            if (existingCrId != null) {
                logger.info("[DFCR] Active CR {} already exists for {} {} (facetId: {}), skipping creation", 
                    existingCrId, facetName, objectId, dfcr.getFacetId());
                return existingCrId;
            }

            // Check for type-level settings if objectTypeId is provided and workflow for types is enabled
            DFCRTypeSetting typeSetting = null;
            if (objectTypeId != null && dfcr.isWorkflowForTypesEnabled()) {
                try {
                    typeSetting = typeSettingsDao.getByFacetAndType(dfcr.getFacetId(), objectTypeId);
                    if (typeSetting != null) {
                        logger.info("[DFCR] Found type-level settings for create - facet: {}, typeId: {}, workflowCreateId: {}, crTypeId: {}", 
                            facetName, objectTypeId, typeSetting.getWorkflowCreateId(), typeSetting.getCrTypeId());
                    } else {
                        logger.debug("[DFCR] No type-level settings found for facet: {}, typeId: {} (will use facet-level)", 
                            facetName, objectTypeId);
                    }
                } catch (SQLException e) {
                    logger.warn("[DFCR] Error loading type-level settings: {}", e.getMessage());
                }
            }

            // Use type-level workflow if available, otherwise use facet-level
            Integer workflowCreateId = null;
            Integer crTypeId = null;
            
            if (typeSetting != null) {
                // Type-level setting exists
                workflowCreateId = typeSetting.getWorkflowCreateId(); // NULL means inherited
                crTypeId = typeSetting.getCrTypeId(); // NULL means inherited
                
                // If inherited (null), fall back to facet-level
                if (workflowCreateId == null) {
                    workflowCreateId = dfcr.getWorkflowCreateId();
                    logger.info("[DFCR] Type-level workflowCreateId is inherited, using facet-level: {}", workflowCreateId);
                } else {
                    logger.info("[DFCR] Using type-level workflowCreateId: {}", workflowCreateId);
                }
                
                if (crTypeId == null) {
                    crTypeId = dfcr.getCrTypeId();
                    logger.info("[DFCR] Type-level crTypeId is inherited, using facet-level: {}", crTypeId);
                } else {
                    logger.info("[DFCR] Using type-level crTypeId: {}", crTypeId);
                }
            } else {
                // No type-level setting - use facet-level
                workflowCreateId = dfcr.getWorkflowCreateId();
                crTypeId = dfcr.getCrTypeId();
                logger.info("[DFCR] No type-level setting, using facet-level - workflowCreateId: {}, crTypeId: {}", 
                    workflowCreateId, crTypeId);
            }

            // Create change request if workflow is enabled
            if (workflowCreateId != null) {
                logger.info("[DFCR] Creating auto change request for facet: {} (workflowCreateId={}, crTypeId={})", 
                    facetName, workflowCreateId, crTypeId);
                return createAutoChangeRequest(dfcr, facetName, objectId, userId, "CREATE", workflowCreateId, crTypeId);
            }

            logger.info("[DFCR] No workflow create ID set for facet: {}", facetName);
            return null;
        } catch (Exception e) {
            logger.error("Error applying DF_CR defaults on create for facet: {}", facetName, e);
            return null;
        }
    }
    
    /**
     * Backward compatibility: applyDefaultsOnCreate without typeId
     */
    public Integer applyDefaultsOnCreate(String facetName, int objectId, int userId, boolean isAdmin) {
        return applyDefaultsOnCreate(facetName, objectId, null, userId, isAdmin);
    }

    /**
     * Apply DF_CR defaults when editing an object.
     * Returns the created change request ID if workflow is enabled, null otherwise.
     * 
     * @param facetName The facet name
     * @param objectId The object ID
     * @param objectTypeId The object's type ID
     * @param userId The user ID
     * @param isAdmin Whether the user is an admin
     */
    public Integer applyDefaultsOnEdit(String facetName, int objectId, Integer objectTypeId, int userId, boolean isAdmin) {
        try {
            logger.info("[DFCR EDIT] applyDefaultsOnEdit called - facet: {}, objectId: {}, objectTypeId: {}, userId: {}, isAdmin: {}", 
                facetName, objectId, objectTypeId, userId, isAdmin);
            
            DFCR dfcr = getSettingsForFacet(facetName);
            if (dfcr == null) {
                logger.info("[DFCR EDIT] No DF_CR settings found for facet: {}", facetName);
                return null;
            }
            
            logger.info("[DFCR EDIT] DFCR settings found - workflowApprovalEnabled: {}, workflowEditId: {}, adminBypass: {}, facetId: {}", 
                dfcr.isWorkflowApprovalEnabled(), dfcr.getWorkflowEditId(), dfcr.isAdminWorkflowBypass(), dfcr.getFacetId());
            
            if (!dfcr.isWorkflowApprovalEnabled()) {
                logger.info("[DFCR EDIT] Workflow not enabled for facet: {}", facetName);
                return null;
            }

            // Check admin bypass - IMPORTANT: Admin bypass ONLY affects admins/super-admins, NOT regular users
            // - If "Enable Workflow Approval for Administrators" is CHECKED (adminBypass=false): 
            //   - Admins/Super-admins: Create CRs (Auto CR works)
            //   - Regular users: Create CRs (Auto CR works)
            // - If "Enable Workflow Approval for Administrators" is UNCHECKED (adminBypass=true):
            //   - Admins/Super-admins: Do NOT create CRs (Auto CR disabled) - BYPASSED
            //   - Regular users: STILL CREATE CRs (Auto CR works) - NOT affected by bypass
            boolean isSuperAdmin = false;
            try {
                isSuperAdmin = com.example.budg_v2.service.SegmentAccessService.isSuperAdmin(userId);
            } catch (SQLException e) {
                logger.warn("[DFCR EDIT] Error checking super admin status for user {}: {}", userId, e.getMessage());
            }
            
            // If admin/super admin AND bypass is enabled (button unchecked) → return early (no CR creation for admins only)
            if ((isAdmin || isSuperAdmin) && dfcr.isAdminWorkflowBypass()) {
                logger.info("[DFCR EDIT] Admin/SuperAdmin with bypass enabled (button unchecked) - Auto CR disabled for admin, no CR will be created for facet: {}", 
                    facetName);
                return null; // No CR creation - Auto CR disabled for admins
            }
            
            // Regular users are NOT affected by admin bypass - they always get auto CR creation when workflow is enabled
            if (!isAdmin && !isSuperAdmin && dfcr.isAdminWorkflowBypass()) {
                logger.info("[DFCR EDIT] Regular user detected with admin bypass enabled - Admin bypass does NOT apply to regular users, will create auto CR for facet: {}", facetName);
            }
            
            if (isAdmin || isSuperAdmin) {
                logger.info("[DFCR EDIT] Admin/SuperAdmin - Auto CR enabled, will create CR for facet: {}", facetName);
            }

            // Check for type-level settings if objectTypeId is provided and workflow for types is enabled
            DFCRTypeSetting typeSetting = null;
            if (objectTypeId != null && dfcr.isWorkflowForTypesEnabled()) {
                try {
                    typeSetting = typeSettingsDao.getByFacetAndType(dfcr.getFacetId(), objectTypeId);
                    if (typeSetting != null) {
                        logger.info("[DFCR] Found type-level settings for edit - facet: {}, typeId: {}, workflowEditId: {}, crTypeId: {}", 
                            facetName, objectTypeId, typeSetting.getWorkflowEditId(), typeSetting.getCrTypeId());
                    } else {
                        logger.debug("[DFCR] No type-level settings found for facet: {}, typeId: {} (will use facet-level)", 
                            facetName, objectTypeId);
                    }
                } catch (SQLException e) {
                    logger.warn("[DFCR] Error loading type-level settings: {}", e.getMessage());
                }
            }

            // Use type-level workflow if available, otherwise use facet-level
            Integer workflowEditId = null;
            Integer workflowCreateId = null;
            Integer crTypeId = null;
            
            if (typeSetting != null) {
                workflowEditId = typeSetting.getWorkflowEditId();
                if (workflowEditId == null) {
                    workflowEditId = dfcr.getWorkflowEditId();
                }
                workflowCreateId = typeSetting.getWorkflowCreateId();
                if (workflowCreateId == null) {
                    workflowCreateId = dfcr.getWorkflowCreateId();
                }
                crTypeId = typeSetting.getCrTypeId();
                if (crTypeId == null) {
                    crTypeId = dfcr.getCrTypeId();
                }
            } else {
                workflowEditId = dfcr.getWorkflowEditId();
                workflowCreateId = dfcr.getWorkflowCreateId();
                crTypeId = dfcr.getCrTypeId();
            }

            // Situation: wf-create=null, wf-edit=value
            if (workflowCreateId == null) {
                if (workflowEditId == null) {
                    logger.info("[DFCR EDIT] Both workflowCreateId and workflowEditId are null - no CR creation");
                    return null;
                }
                // Reuse CR if same user, create new if different user
                return handleEditWorkflowForNullCreate(facetName, objectId, userId, dfcr, workflowEditId, crTypeId);
            }

            // Situation: wf-create=value
            if (workflowEditId == null) {
                // wf-create=value, wf-edit=null: Don't create edit CR
                // Even if there's a completed CR, we should NOT create a new edit CR when workflow_edit is null
                logger.info("[DFCR EDIT] workflowEditId is null - NO edit CR will be created (workflow_edit is disabled)");
                return null;
            }

            // Situation: wf-create=value, wf-edit=value
            if (workflowEditId.equals(workflowCreateId)) {
                // Same workflow: reuse create CR if active and same user, otherwise create new edit CR
                // IMPORTANT: If create CR is completed/cancelled, always create new edit CR
                return handleEditWorkflowForSameWorkflow(facetName, objectId, userId, dfcr, workflowEditId, workflowCreateId, crTypeId);
            } else {
                // Different workflows: ALWAYS create new edit CR (regardless of user or create CR status)
                logger.info("[DFCR EDIT] workflowEditId ({}) != workflowCreateId ({}) - ALWAYS creating new edit CR", 
                    workflowEditId, workflowCreateId);
                return createAutoChangeRequest(dfcr, facetName, objectId, userId, "EDIT", workflowEditId, crTypeId);
            }
        } catch (Exception e) {
            logger.error("Error applying DF_CR defaults on edit for facet: {}", facetName, e);
            return null;
        }
    }
    
    /**
     * Backward compatibility: applyDefaultsOnEdit without typeId
     */
    public Integer applyDefaultsOnEdit(String facetName, int objectId, int userId, boolean isAdmin) {
        return applyDefaultsOnEdit(facetName, objectId, null, userId, isAdmin);
    }

    /**
     * Create an auto-generated change request based on DF_CR settings
     * 
     * @param dfcr The facet-level DFCR settings (for fallback values)
     * @param facetName The facet name
     * @param objectId The object ID
     * @param userId The user ID
     * @param action "CREATE" or "EDIT"
     * @param workflowId The workflow ID to use (from type-level or facet-level)
     * @param crTypeId The CR type ID to use (from type-level or facet-level)
     */
    private Integer createAutoChangeRequest(DFCR dfcr, String facetName, int objectId, int userId, 
                                          String action, Integer workflowId, Integer crTypeId) throws SQLException {
        // ⚠️ CRITICAL: For EDIT actions, check if workflow_edit is null - if so, don't create CR
        if ("EDIT".equals(action) && dfcr.getWorkflowEditId() == null) {
            logger.warn("[DFCR] Attempted to create EDIT CR when workflow_edit is null - this should not happen! Returning null.");
            return null;
        }
        
        // ⚠️ CRITICAL: Check for existing active automatic CR before creating new one (prevents duplicates)
        // DFCR only creates automatic CRs, so we only check for automatic CRs
        com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
        Integer existingActiveCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(dfcr.getFacetId(), objectId);
        if (existingActiveCrId != null) {
            logger.info("[DFCR] Active CR {} already exists for {} {} (facetId: {}), reusing it instead of creating new one", 
                existingActiveCrId, facetName, objectId, dfcr.getFacetId());
            return existingActiveCrId;
        }
        
        String reference = facetName + " " + objectId;
        
        // Get workflow name to use in CR title (use workflow name only, without reference)
        String workflowName = getWorkflowName(workflowId);
        // Include action (CREATE/EDIT) in title to help identify CR type later
        String title = workflowName;
        if ("EDIT".equals(action)) {
            title = "Auto-generated CR for EDIT: " + workflowName;
        } else if ("CREATE".equals(action)) {
            title = "Auto-generated CR for CREATE: " + workflowName;
        }
        
        ChangeRequest cr = new ChangeRequest();
        // ChangeRequest model uses primaryName + summary fields (no title field)
        cr.setPrimaryName(title);
        cr.setSummary(title);
        cr.setReference(reference);
        cr.setCreatedBy(userId);
        cr.setLastUserChange(userId);
        cr.setCreatedAt(LocalDateTime.now());
        cr.setUpdatedAt(LocalDateTime.now());
        
        // Auto-created CRs: mandatory_workflow = true and status = "Pending Start"
        cr.setMandatoryWorkflow(true);
        
        // FORCE status to 2 (Pending Start) for ALL auto-created CRs
        Integer pendingStartStatusId = getStatusIdByName("Pending Start");
        if (pendingStartStatusId == null) {
            pendingStartStatusId = getStatusIdByName("PendingStart");
        }
        if (pendingStartStatusId == null) {
            pendingStartStatusId = getStatusIdByName("Pending");
        }
        
        // Always use status ID 1 if lookup failed (trying different ID)
        if (pendingStartStatusId == null) {
            pendingStartStatusId = 1;
            logger.warn("[DFCR] Status lookup failed for 'Pending Start', using status ID 1 as default");
        }
        
        // FORCE set the status - don't allow null
        cr.setCrStatusId(pendingStartStatusId);
        logger.info("[DFCR] Creating CR with mandatory_workflow={}, crStatusId={} (forced)", 
            cr.getMandatoryWorkflow(), pendingStartStatusId);
        
        // Apply CR type (from type-level if provided, otherwise from facet-level)
        if (crTypeId != null) {
            cr.setCrTypeId(crTypeId);
        } else if (dfcr.getCrTypeId() != null) {
            cr.setCrTypeId(dfcr.getCrTypeId());
        }
        
        // Apply other defaults from facet-level (urgency, severity)
        if (dfcr.getCrUrgencyId() != null) {
            cr.setCrUrgencyId(dfcr.getCrUrgencyId());
        }
        if (dfcr.getCrSeverityId() != null) {
            cr.setCrSeverityId(dfcr.getCrSeverityId());
        }

        // Store the workflow process definition ID
        if (workflowId != null) {
            cr.setProcessDefinitionId(workflowId);
            logger.info("[DFCR] Setting processDefinitionId={} for auto-created CR", workflowId);
        }
        
        // Create the change request
        Integer crId = changeRequestDao.createChangeRequest(cr);
        
        logger.info("Created auto change request {} for {} {} (facet: {}, user: {}, workflowId: {}, crTypeId: {})", 
                crId, action, reference, facetName, userId, workflowId, crTypeId);
        
        // Mark the object as under revision
        markObjectUnderRevision(facetName, objectId, crId);
        
        // Invalidate CREATE CR cache to ensure fresh data for concurrent requests
        invalidateCreateCRCache(dfcr.getFacetId(), objectId);
        
        return crId;
    }
    
    /**
     * Mark an object as under revision with an active change request
     */
    public void markObjectUnderRevision(String facetName, int objectId, Integer crId) {
        String tableName = getTableNameForFacet(facetName);
        if (tableName == null) {
            logger.warn("Unknown facet for marking under revision: {}", facetName);
            return;
        }
        
        String sql = "UPDATE `" + tableName + "` SET under_revision = 1, active_cr_id = ? WHERE id = ?";
        
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (crId != null) {
                stmt.setInt(1, crId);
            } else {
                stmt.setNull(1, java.sql.Types.INTEGER);
            }
            stmt.setInt(2, objectId);
            int updated = stmt.executeUpdate();
            logger.info("Marked {} {} as under revision (CR: {}), rows updated: {}", facetName, objectId, crId, updated);
        } catch (java.sql.SQLException e) {
            // Column may not exist yet - log but don't fail
            logger.warn("Could not mark object as under revision (column may not exist): {}", e.getMessage());
        }
    }
    
    /**
     * Clear the under revision flag when CR is completed or cancelled
     */
    public void clearObjectUnderRevision(String facetName, int objectId) {
        String tableName = getTableNameForFacet(facetName);
        if (tableName == null) {
            logger.warn("Unknown facet for clearing under revision: {}", facetName);
            return;
        }
        
        String sql = "UPDATE `" + tableName + "` SET under_revision = 0, active_cr_id = NULL WHERE id = ?";
        
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            int updated = stmt.executeUpdate();
            logger.info("Cleared under revision for {} {}, rows updated: {}", facetName, objectId, updated);
        } catch (java.sql.SQLException e) {
            logger.warn("Could not clear under revision flag: {}", e.getMessage());
        }
    }
    
    /**
     * Get the database table name for a facet
     */
    private String getTableNameForFacet(String facetName) {
        if (facetName == null) return null;
        switch (facetName.toLowerCase()) {
            case "glossary": return "glossary";
            case "dataset":
            case "data set": return "dataset";
            case "system": return "system";
            case "process": return "process";
            case "business area":
            case "businessarea": return "business_area";
            default: return null;
        }
    }

    /**
     * Update object Status and Lifecycle based on End event properties
     * @param facetName The facet name (e.g., "Dataset", "Business Area")
     * @param objectId The object ID
     * @param statusId The status ID to set (can be null)
     * @param lifecycleId The lifecycle ID to set (can be null)
     */
    public void updateObjectStatusAndLifecycle(String facetName, int objectId, Integer statusId, Integer lifecycleId) {
        if (facetName == null) {
            logger.warn("Cannot update object: facet name is null");
            return;
        }

        String tableName = getTableNameForFacet(facetName);
        if (tableName == null) {
            logger.warn("Unknown facet for updating status/lifecycle: {}", facetName);
            return;
        }

        // Build SQL update statement based on what needs to be updated
        StringBuilder sql = new StringBuilder("UPDATE `").append(tableName).append("` SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();
        boolean hasUpdate = false;

        if (statusId != null) {
            sql.append("`status` = ?");
            params.add(statusId);
            hasUpdate = true;
        }

        if (lifecycleId != null) {
            if (hasUpdate) {
                sql.append(", ");
            }
            // Different facets use different lifecycle column names
            String lifecycleColumn = getLifecycleColumnName(facetName);
            sql.append("`").append(lifecycleColumn).append("` = ?");
            params.add(lifecycleId);
            hasUpdate = true;
            
            // Some facets also have LifecycleStatus column
            if (hasLifecycleStatusColumn(facetName)) {
                sql.append(", `LifecycleStatus` = ?");
                params.add(lifecycleId);
            }
        }

        if (!hasUpdate) {
            logger.info("No status or lifecycle to update for {} {}", facetName, objectId);
            return;
        }

        sql.append(" WHERE `id` = ?");
        params.add(objectId);

        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) param);
                } else {
                    stmt.setObject(i + 1, param);
                }
            }

            int updated = stmt.executeUpdate();
            logger.info("Updated {} {} with status={}, lifecycle={}, rows updated: {}", 
                facetName, objectId, statusId, lifecycleId, updated);
        } catch (java.sql.SQLException e) {
            logger.error("Error updating {} {} status/lifecycle: {}", facetName, objectId, e.getMessage(), e);
            throw new RuntimeException("Failed to update object status/lifecycle", e);
        }
    }

    /**
     * Get the lifecycle column name for a facet
     */
    private String getLifecycleColumnName(String facetName) {
        if (facetName == null) return "lifecycle";
        switch (facetName.toLowerCase()) {
            case "process": return "lifecycle";
            case "business area":
            case "businessarea": return "Lifecycle";
            default: return "lifecycle";
        }
    }

    /**
     * Check if facet has LifecycleStatus column in addition to lifecycle
     */
    private boolean hasLifecycleStatusColumn(String facetName) {
        if (facetName == null) return false;
        switch (facetName.toLowerCase()) {
            case "business area":
            case "businessarea": return true;
            default: return false;
        }
    }

    /**
     * Check if status field should be locked (read-only) based on DF_CR settings
     */
    public boolean isStatusLocked(String facetName) {
        DFCR dfcr = getSettingsForFacet(facetName);
        return dfcr != null && dfcr.isWorkflowApprovalEnabled() && dfcr.getStatusId() != null;
    }

    /**
     * Check if lifecycle field should be locked (read-only) based on DF_CR settings
     */
    public boolean isLifecycleLocked(String facetName) {
        DFCR dfcr = getSettingsForFacet(facetName);
        return dfcr != null && dfcr.isWorkflowApprovalEnabled() && dfcr.getLifecycleId() != null;
    }

    /**
     * Get locked fields info for a facet (used by frontend to disable fields)
     * 
     * @param facetName The facet name
     * @param isAdmin Whether the user is an admin
     * @param userId The user ID
     */
    public LockedFieldsInfo getLockedFieldsInfo(String facetName, boolean isAdmin, int userId) {
        return getLockedFieldsInfo(facetName, isAdmin, userId, null);
    }
    
    /**
     * Get locked fields info for a facet (used by frontend to disable fields)
     * 
     * @param facetName The facet name
     * @param isAdmin Whether the user is an admin
     * @param userId The user ID
     * @param objectId Optional object ID - if provided, checks if object was created through default CR
     */
    public LockedFieldsInfo getLockedFieldsInfo(String facetName, boolean isAdmin, int userId, Integer objectId) {
        logger.info("[DFCR] getLockedFieldsInfo - facet: {}, isAdmin: {}, userId: {}, objectId: {}", facetName, isAdmin, userId, objectId);
        
        DFCR dfcr = getSettingsForFacet(facetName);
        LockedFieldsInfo info = new LockedFieldsInfo();
        
        if (dfcr == null || !dfcr.isWorkflowApprovalEnabled()) {
            logger.info("[DFCR] No locks - dfcr is null or workflow not enabled");
            return info; // No locks
        }

        logger.info("[DFCR] DFCR settings - workflowApprovalEnabled: {}, adminWorkflowBypass: {}, workflowCreateId: {}, workflowEditId: {}", 
            dfcr.isWorkflowApprovalEnabled(), dfcr.isAdminWorkflowBypass(), dfcr.getWorkflowCreateId(), dfcr.getWorkflowEditId());

        // Check admin bypass - IMPORTANT: Admin bypass ONLY affects admins/super-admins, NOT regular users
        // - If "Enable Workflow Approval for Administrators" is CHECKED (adminBypass=false): 
        //   - Admins/Super-admins: Get DF_CR features (locks + CRs + defaults)
        //   - Regular users: Get DF_CR features (locks + CRs + defaults)
        // - If "Enable Workflow Approval for Administrators" is UNCHECKED (adminBypass=true):
        //   - Admins/Super-admins: Do NOT get DF_CR features (no locks, no CRs, no defaults) - BYPASSED
        //   - Regular users: STILL GET DF_CR features (locks + CRs + defaults) - NOT affected by bypass
        boolean isSuperAdmin = false;
        try {
            isSuperAdmin = com.example.budg_v2.service.SegmentAccessService.isSuperAdmin(userId);
        } catch (SQLException e) {
            logger.warn("[DFCR] Error checking super admin status for user {}: {}", userId, e.getMessage());
        }
        
        // If admin/super admin AND bypass is enabled (button unchecked) → return early (no auto CR features for admins only)
        if ((isAdmin || isSuperAdmin) && dfcr.isAdminWorkflowBypass()) {
            logger.info("[DFCR] Admin/SuperAdmin with bypass enabled (button unchecked) - Auto CR disabled completely for admin (no locks, no CRs, no defaults)");
            return info; // Return empty info - no auto CR features for admins
        }
        
        // Regular users are NOT affected by admin bypass - they always get DF_CR features when workflow is enabled
        if (!isAdmin && !isSuperAdmin && dfcr.isAdminWorkflowBypass()) {
            logger.info("[DFCR] Regular user detected with admin bypass enabled - Admin bypass does NOT apply to regular users, will get DF_CR features (locks + CRs + defaults)");
        }
        
        if (isAdmin || isSuperAdmin) {
            logger.info("[DFCR] Admin/SuperAdmin detected - Auto CR enabled with ALL features (locks + CRs + defaults)");
        }

        Integer workflowCreateId = dfcr.getWorkflowCreateId();
        Integer workflowEditId = dfcr.getWorkflowEditId();
        
        // Rule 1: If only workflowEditId is set (workflowCreateId is null), don't lock
        if (workflowCreateId == null && workflowEditId != null) {
            logger.info("[DFCR] Only workflowEditId is set (workflowCreateId is null) - NOT locking status/lifecycle");
            info.setWorkflowEnabled(true);
            return info; // Don't lock
        }
        
        // Rule 2: If workflowCreateId is null and workflowEditId is also null, don't lock
        if (workflowCreateId == null) {
            logger.info("[DFCR] workflowCreateId is null - NOT locking status/lifecycle");
            info.setWorkflowEnabled(true);
            return info; // Don't lock
        }
        
        // Situation: wf-create=value
        boolean hasActiveCreateCR = false; // Track if there's an active CREATE CR (for edit pages)
        boolean wasCreatedThroughDefaultCR = false; // Track if object was created through default CR (for edit pages)
        
        if (objectId == null) {
            // This is a CREATE page - lock fields if workflowCreateId is set
            logger.info("[DFCR] CREATE page detected (objectId is null) - will lock status/lifecycle if workflowCreateId is set");
            // Will lock below
        } else {
            // This is an edit page
            try {
                com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
                Integer facetId = facetChangesDAO.getFacetId(facetName);
                if (facetId != null) {
                    // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
                    // Manual CRs should NOT trigger locking or pending changes logic
                    // This isolates auto CR logic from manual CR logic
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetId, objectId);
                    if (activeCrId == null) {
                        // ⚠️ IMPORTANT: Even if no active CR, check for CREATE CRs that haven't completed
                        // This ensures locks are applied if CREATE CR exists in any non-completed state (pending, start, running)
                        logger.info("[DFCR] Object {} has NO active automatic CRs - checking for CREATE CRs that haven't completed", objectId);
                        Integer createCrId = getCreateCRIdForObject(facetId, objectId, workflowCreateId);
                        if (createCrId != null) {
                            logger.info("[DFCR] Found CREATE CR {} for object {} that hasn't completed - will lock status/lifecycle", createCrId, objectId);
                            hasActiveCreateCR = true;
                            activeCrId = createCrId; // Use this for further checks
                        } else {
                            logger.info("[DFCR] Object {} has NO active automatic CRs and NO incomplete CREATE CRs - NOT locking status/lifecycle", objectId);
                        info.setWorkflowEnabled(true);
                            return info; // Don't lock - no active auto CR and no incomplete CREATE CR
                    }
                    } else {
                    logger.info("[DFCR] Object {} has active automatic CR {} - will check locking rules", objectId, activeCrId);
                    }
                    
                    // Rule 3: ALWAYS check if the active CR is an EDIT CR - if so, don't lock
                    // This applies regardless of whether workflowEditId == workflowCreateId or not
                    logger.info("[DFCR] Checking active CR for object {} - activeCrId: {}, workflowEditId: {}, workflowCreateId: {}", 
                        objectId, activeCrId, workflowEditId, workflowCreateId);
                    if (activeCrId != null) {
                        // Check if the active CR is an EDIT CR by checking:
                        // 1. The title/primaryName (contains "EDIT")
                        // 2. The process_definition_id (matches workflowEditId)
                        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                            String checkCrTypeSql = "SELECT PrimaryName, Summary, process_definition_id FROM changerequest WHERE ID = ?";
                            try (java.sql.PreparedStatement stmt = conn.prepareStatement(checkCrTypeSql)) {
                                stmt.setInt(1, activeCrId);
                                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                                    if (rs.next()) {
                                        String primaryName = rs.getString("PrimaryName");
                                        String summary = rs.getString("Summary");
                                        Integer crProcessDefinitionId = rs.getInt("process_definition_id");
                                        if (rs.wasNull()) {
                                            crProcessDefinitionId = null;
                                        }
                                        String crTitle = (primaryName != null ? primaryName : "") + " " + (summary != null ? summary : "");
                                        
                                        logger.info("[DFCR] Active CR {} details - PrimaryName: '{}', Summary: '{}', crTitle: '{}', process_definition_id: {}", 
                                            activeCrId, primaryName, summary, crTitle, crProcessDefinitionId);
                                        
                                        // Check if CR is an EDIT CR by:
                                        // 1. Title contains "EDIT" (case-insensitive) - check multiple variations
                                        // 2. process_definition_id matches workflowEditId (if workflowEditId is set)
                                        boolean isEditCr = false;
                                        
                                        String crTitleUpper = crTitle != null ? crTitle.toUpperCase() : "";
                                        
                                        // Check multiple variations of EDIT CR title
                                        if (crTitleUpper.contains("AUTO-GENERATED CR FOR EDIT") || 
                                            crTitleUpper.contains("AUTO GENERATED CR FOR EDIT") ||
                                            crTitleUpper.contains("CR FOR EDIT") ||
                                            (crTitleUpper.contains("EDIT") && crTitleUpper.contains("AUTO"))) {
                                            isEditCr = true;
                                            logger.info("[DFCR] Active CR {} is an EDIT CR (detected by title: '{}')", activeCrId, crTitle);
                                        } else if (workflowEditId != null && crProcessDefinitionId != null 
                                                   && crProcessDefinitionId.equals(workflowEditId)
                                                   && !workflowEditId.equals(workflowCreateId)) {
                                            // ⚠️ Only use process_definition_id to detect EDIT CR when workflows are DIFFERENT
                                            // When workflowEditId == workflowCreateId, both CREATE and EDIT CRs have the same 
                                            // process_definition_id, so we can't distinguish them this way
                                            isEditCr = true;
                                            logger.info("[DFCR] Active CR {} is an EDIT CR (detected by process_definition_id: {} matches workflowEditId: {} and differs from workflowCreateId: {})", 
                                                activeCrId, crProcessDefinitionId, workflowEditId, workflowCreateId);
                                        } else if (workflowEditId != null && workflowEditId.equals(workflowCreateId) 
                                                   && crProcessDefinitionId != null && crProcessDefinitionId.equals(workflowEditId)) {
                                            // ⚠️ SPECIAL CASE: workflowEditId == workflowCreateId AND title doesn't contain EDIT/CREATE
                                            // This is a legacy CR created before the title fix.
                                            // To determine if it's EDIT or CREATE, check if a DIFFERENT CR exists for the same object
                                            // that IS a CREATE CR (by title). If a completed/cancelled CREATE CR exists, 
                                            // then this unknown CR is likely an EDIT CR.
                                            // Also: if object was created through default CR and this CR was created AFTER the CREATE CR,
                                            // it's likely an EDIT CR.
                                            logger.info("[DFCR] Active CR {} has same workflow for create/edit (id={}) and no type indicator in title - checking if EDIT by examining other CRs", 
                                                activeCrId, workflowEditId);
                                            
                                            // Check if there's a separate CREATE CR (completed/cancelled) for this object
                                            // If yes, then this CR must be an EDIT CR
                                            boolean hasCompletedOrCancelledCreateCR = hasCompletedCreateCR(facetId, objectId, activeCrId);
                                            if (hasCompletedOrCancelledCreateCR) {
                                                isEditCr = true;
                                                logger.info("[DFCR] Active CR {} is likely an EDIT CR (found completed/cancelled CREATE CR for same object)", activeCrId);
                                            } else {
                                                // No completed CREATE CR found - this could be the CREATE CR itself
                                                // Check title for CREATE indicator
                                                if (crTitleUpper.contains("AUTO-GENERATED CR FOR CREATE") || 
                                                    crTitleUpper.contains("AUTO GENERATED CR FOR CREATE") ||
                                                    crTitleUpper.contains("CR FOR CREATE") ||
                                                    (crTitleUpper.contains("CREATE") && crTitleUpper.contains("AUTO"))) {
                                                    logger.info("[DFCR] Active CR {} is a CREATE CR (detected by title: '{}')", activeCrId, crTitle);
                                                    // Not EDIT - will be handled as CREATE below
                                                } else {
                                                    // Completely unknown - assume it's based on creation order
                                                    // If object already exists (we're on edit page), and no completed CREATE CR,
                                                    // the object may have been created before DFCR was enabled
                                                    // In this case, treat this as an EDIT CR (don't lock)
                                                    isEditCr = true;
                                                    logger.info("[DFCR] Active CR {} - cannot determine type, treating as EDIT CR since object exists and no completed CREATE CR found", activeCrId);
                                                }
                                            }
                                        } else {
                                            logger.info("[DFCR] Active CR {} is NOT an EDIT CR - crTitleUpper: '{}', workflowEditId: {}, crProcessDefinitionId: {}, workflowCreateId: {}", 
                                                activeCrId, crTitleUpper, workflowEditId, crProcessDefinitionId, workflowCreateId);
                                        }
                                        
                                        if (isEditCr) {
                                            logger.info("[DFCR] Active CR {} is an EDIT CR - checking for incomplete CREATE CRs before deciding on locks", activeCrId);
                                            // ⚠️ CRITICAL: Even if EDIT CR is active, check for CREATE CRs that haven't completed
                                            // If CREATE CR exists (pending/start/running), we MUST lock status/lifecycle
                                            // Only unlock if CREATE CR is completed/cancelled
                                            // Pass activeCrId to EXCLUDE it from results (so EDIT CR doesn't match itself as CREATE)
                                            Integer createCrId = getCreateCRIdForObject(facetId, objectId, workflowCreateId, activeCrId);
                                            if (createCrId != null) {
                                                logger.info("[DFCR] Found incomplete CREATE CR {} despite active EDIT CR {} - WILL lock status/lifecycle", 
                                                    createCrId, activeCrId);
                                                hasActiveCreateCR = true;
                                                // Continue to lock below - don't return early
                                            } else {
                                                logger.info("[DFCR] Active CR {} is an EDIT CR and no incomplete CREATE CRs found - NOT locking status/lifecycle", activeCrId);
                                            info.setWorkflowEnabled(true);
                                                return info; // Don't lock - only EDIT CR is active and CREATE CR is completed/cancelled
                                            }
                                        }
                                        
                                        // If active CR is NOT an EDIT CR, it must be a CREATE CR
                                        // Also check if process_definition_id matches workflowCreateId to confirm it's a CREATE CR
                                        boolean isCreateCr = false;
                                        if (workflowCreateId != null && crProcessDefinitionId != null && crProcessDefinitionId.equals(workflowCreateId)) {
                                            isCreateCr = true;
                                            logger.info("[DFCR] Active CR {} is a CREATE CR (detected by process_definition_id: {} matches workflowCreateId: {})", 
                                                activeCrId, crProcessDefinitionId, workflowCreateId);
                                        } else if (crTitleUpper.contains("AUTO-GENERATED CR FOR CREATE") || 
                                                   crTitleUpper.contains("AUTO GENERATED CR FOR CREATE") ||
                                                   crTitleUpper.contains("CR FOR CREATE") ||
                                                   (crTitleUpper.contains("CREATE") && crTitleUpper.contains("AUTO"))) {
                                            isCreateCr = true;
                                            logger.info("[DFCR] Active CR {} is a CREATE CR (detected by title: '{}')", activeCrId, crTitle);
                                        } else {
                                            // If we can't determine the type, assume it's a CREATE CR if it's not an EDIT CR
                                            isCreateCr = true;
                                            logger.info("[DFCR] Active CR {} is assumed to be a CREATE CR (not an EDIT CR, title: '{}', process_definition_id: {})", 
                                                activeCrId, crTitle, crProcessDefinitionId);
                                        }
                                        
                                        // If active CR is a CREATE CR, mark it and will lock below
                                        if (isCreateCr) {
                                            hasActiveCreateCR = true;
                                            logger.info("[DFCR] Active CR {} is a CREATE CR - will lock status/lifecycle (title: '{}', process_definition_id: {})", 
                                                activeCrId, crTitle, crProcessDefinitionId);
                                        }
                                    } else {
                                        logger.warn("[DFCR] Active CR {} not found in database", activeCrId);
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            logger.warn("[DFCR] Error checking CR type: {}", e.getMessage(), e);
                            // Continue with normal locking logic if check fails
                        }
                    }
                }
            } catch (SQLException e) {
                logger.warn("[DFCR] Error checking for active CRs: {}", e.getMessage());
                // Continue with normal locking logic if check fails
            }
            
            // Check if object was created through default CR
            wasCreatedThroughDefaultCR = wasObjectCreatedThroughDefaultCR(facetName, objectId);
            
            // Case 1: wf-create=value, wf-edit=value (same) OR wf-create=value, wf-edit=null
            // If there's an active CREATE CR, lock regardless of wasCreatedThroughDefaultCR
            // Otherwise, lock ONLY if object was created through default CR
            if (workflowEditId == null || workflowEditId.equals(workflowCreateId)) {
                if (hasActiveCreateCR) {
                    // Active CREATE CR exists - lock status/lifecycle
                    logger.info("[DFCR] Active CREATE CR found for object {} - will lock status/lifecycle", objectId);
                    // Will lock below
                } else if (!wasCreatedThroughDefaultCR) {
                    logger.info("[DFCR] Object {} was NOT created through default CR (old data) and no active CREATE CR - NOT locking status/lifecycle", objectId);
                    info.setWorkflowEnabled(true);
                    return info; // Don't lock
                } else {
                    // Object was created through default CR - will lock below
                    logger.info("[DFCR] Object {} was created through default CR - will lock status/lifecycle", objectId);
                }
            }
            // Case 2: wf-create=value, wf-edit=value (different)
            // If we reach here and there's an active CR, it must be a CREATE CR (EDIT CR was already handled above)
            // Lock only if active CR is from creating workflow
            else {
                // workflowEditId != null && workflowEditId != workflowCreateId
                // If there's no active CR or active CR is CREATE, lock
                // (EDIT CR was already handled above and returned early)
                logger.info("[DFCR] workflowEditId ({}) != workflowCreateId ({}) - will lock if CREATE CR is active", 
                    workflowEditId, workflowCreateId);
                // If hasActiveCreateCR is true, we'll lock below
                // If not, we need to check if we should lock based on other conditions
                if (!hasActiveCreateCR) {
                    // No active CREATE CR - check if we should lock based on wasCreatedThroughDefaultCR
                    if (!wasCreatedThroughDefaultCR) {
                        logger.info("[DFCR] Object {} was NOT created through default CR and no active CREATE CR - NOT locking status/lifecycle", objectId);
                        info.setWorkflowEnabled(true);
                        return info; // Don't lock
                    }
                    // Object was created through default CR - will lock below
                    logger.info("[DFCR] Object {} was created through default CR - will lock status/lifecycle", objectId);
                }
            }
        }

        // Apply defaults and locks (we only reach here if bypass is NOT enabled for admins)
        // Only lock if we determined we should lock (hasActiveCreateCR or wasCreatedThroughDefaultCR)
        logger.info("[DFCR] Applying defaults and locks - statusId: {}, lifecycleId: {}, hasActiveCreateCR: {}, wasCreatedThroughDefaultCR: {}, objectId: {}", 
            dfcr.getStatusId(), dfcr.getLifecycleId(), hasActiveCreateCR, wasCreatedThroughDefaultCR, objectId);
        
        // Set default values and lock fields
        info.setDefaultStatusId(dfcr.getStatusId());
        info.setDefaultLifecycleId(dfcr.getLifecycleId());
        // Lock fields if:
        // 1. This is a CREATE page (objectId == null), OR
        // 2. This is an EDIT page with active CREATE CR, OR
        // 3. This is an EDIT page and object was created through default CR
        boolean shouldLock = (objectId == null) || hasActiveCreateCR || wasCreatedThroughDefaultCR;
        logger.info("[DFCR] Lock decision - shouldLock: {} (objectId==null: {}, hasActiveCreateCR: {}, wasCreatedThroughDefaultCR: {})", 
            shouldLock, (objectId == null), hasActiveCreateCR, wasCreatedThroughDefaultCR);
        info.setStatusLocked(shouldLock && dfcr.getStatusId() != null);
        info.setLifecycleLocked(shouldLock && dfcr.getLifecycleId() != null);
        info.setWorkflowEnabled(true);
        
        logger.info("[DFCR] Auto CR enabled - defaults and locks applied (statusLocked: {}, lifecycleLocked: {}, statusId: {}, lifecycleId: {})", 
            info.isStatusLocked(), info.isLifecycleLocked(), dfcr.getStatusId(), dfcr.getLifecycleId());

        return info;
    }
    
    /**
     * Handle edit workflow when wf-create=null, wf-edit=value
     * Reuse CR if same user, create new if different user
     */
    private Integer handleEditWorkflowForNullCreate(String facetName, int objectId, int userId, 
            DFCR dfcr, Integer workflowEditId, Integer crTypeId) throws SQLException {
        logger.info("[DFCR EDIT] Handling edit workflow for null create - facet: {}, objectId: {}, userId: {}", 
            facetName, objectId, userId);
        
        // Check if there's an existing active CR for this object
        com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
        // DFCR only creates automatic CRs, so we only check for automatic CRs
        Integer existingCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(dfcr.getFacetId(), objectId);
        
        if (existingCrId != null) {
            // Check if this CR was created by the same user
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                String checkSql = "SELECT Created_By FROM changerequest WHERE ID = ?";
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                    stmt.setInt(1, existingCrId);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int crCreatedBy = rs.getInt("Created_By");
                            if (crCreatedBy == userId) {
                                // Same user - reuse existing CR
                                logger.info("[DFCR EDIT] Reusing existing CR {} created by same user {}", existingCrId, userId);
                                return existingCrId;
                            } else {
                                // Different user - create new edit CR
                                logger.info("[DFCR EDIT] Existing CR {} created by different user {} (current: {}), creating new edit CR", 
                                    existingCrId, crCreatedBy, userId);
                                return createAutoChangeRequest(dfcr, facetName, objectId, userId, "EDIT", workflowEditId, crTypeId);
                            }
                        }
                    }
                }
            }
        }
        
        // No existing CR - create new edit CR
        logger.info("[DFCR EDIT] No existing CR found, creating new edit CR");
        return createAutoChangeRequest(dfcr, facetName, objectId, userId, "EDIT", workflowEditId, crTypeId);
    }

    /**
     * Handle edit workflow when wf-create=value, wf-edit=value (same workflow)
     * Reuse create CR if active and same user, otherwise create new edit CR
     * IMPORTANT: If create CR is completed/cancelled, always create new edit CR
     */
    private Integer handleEditWorkflowForSameWorkflow(String facetName, int objectId, int userId, 
            DFCR dfcr, Integer workflowEditId, Integer workflowCreateId, Integer crTypeId) throws SQLException {
        logger.info("[DFCR EDIT] Handling edit workflow for same workflow - facet: {}, objectId: {}, userId: {}, workflowEditId: {}, workflowCreateId: {}", 
            facetName, objectId, userId, workflowEditId, workflowCreateId);
        
        com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
        
        // First, check if there's an existing active CR for this object
        Integer existingActiveCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(dfcr.getFacetId(), objectId);
        
        if (existingActiveCrId != null) {
            // There's an active CR - check if it was created by the same user
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                String checkSql = "SELECT Created_By, process_definition_id FROM changerequest WHERE ID = ?";
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                    stmt.setInt(1, existingActiveCrId);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int crCreatedBy = rs.getInt("Created_By");
                            Integer crProcessDefinitionId = rs.getInt("process_definition_id");
                            if (rs.wasNull()) {
                                crProcessDefinitionId = null;
                            }
                            
                            if (crCreatedBy == userId) {
                                // Same user - reuse active CR
                                // Update processDefinitionId if needed
                                if (workflowEditId != null && !workflowEditId.equals(crProcessDefinitionId)) {
                                    String updateSql = "UPDATE changerequest SET process_definition_id = ? WHERE ID = ?";
                                    try (java.sql.PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                                        updateStmt.setInt(1, workflowEditId);
                                        updateStmt.setInt(2, existingActiveCrId);
                                        updateStmt.executeUpdate();
                                        logger.info("[DFCR EDIT] Updated processDefinitionId to {} for reused CR {}", workflowEditId, existingActiveCrId);
                                    }
                                }
                                logger.info("[DFCR EDIT] Reusing active create CR {} for same user {}, no new CR created", existingActiveCrId, userId);
                                return existingActiveCrId;
                            } else {
                                // Different user - create new edit CR
                                logger.info("[DFCR EDIT] Active CR {} created by different user {} (current: {}), creating new edit CR", 
                                    existingActiveCrId, crCreatedBy, userId);
                                return createAutoChangeRequest(dfcr, facetName, objectId, userId, "EDIT", workflowEditId, crTypeId);
                            }
                        }
                    }
                }
            }
        }
        
        // No active CR found - ALWAYS create a new edit CR immediately
        // This ensures that edits are tracked from the first edit after the create CR is resolved
        // We don't need to check for completed CRs - if there's no active CR, we create edit CR directly
        // This is more efficient and ensures immediate tracking of changes
        logger.info("[DFCR EDIT] No active CR found for {} {} - creating new edit CR immediately for first edit", facetName, objectId);
        return createAutoChangeRequest(dfcr, facetName, objectId, userId, "EDIT", workflowEditId, crTypeId);
    }

    /**
     * Check if an object was created through default CR
     * An object is considered created through default CR if:
     * - under_revision = 1 OR active_cr_id IS NOT NULL
     * 
     * @param facetName The facet name
     * @param objectId The object ID
     * @return true if object was created through default CR, false otherwise
     */
    private boolean wasObjectCreatedThroughDefaultCR(String facetName, int objectId) {
        String tableName = getTableNameForFacet(facetName);
        if (tableName == null) {
            logger.warn("[DFCR] Unknown facet for checking default CR creation: {}", facetName);
            return false;
        }
        
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            // Check if under_revision = 1 OR active_cr_id IS NOT NULL
            // If either is true, the object was created through default CR
            String sql = "SELECT under_revision, active_cr_id FROM `" + tableName + "` WHERE id = ?";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, objectId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Check under_revision column (if exists)
                        boolean underRevision = false;
                        try {
                            int underRevisionValue = rs.getInt("under_revision");
                            underRevision = (underRevisionValue == 1);
                        } catch (java.sql.SQLException e) {
                            // Column may not exist - that's okay
                            logger.debug("[DFCR] under_revision column may not exist: {}", e.getMessage());
                        }
                        
                        // Check active_cr_id column (if exists)
                        boolean hasActiveCR = false;
                        try {
                            Integer activeCrId = rs.getInt("active_cr_id");
                            if (!rs.wasNull()) {
                                hasActiveCR = (activeCrId != null && activeCrId > 0);
                            }
                        } catch (java.sql.SQLException e) {
                            // Column may not exist - that's okay
                            logger.debug("[DFCR] active_cr_id column may not exist: {}", e.getMessage());
                        }
                        
                        boolean result = underRevision || hasActiveCR;
                        logger.info("[DFCR] Object {} {} - underRevision: {}, hasActiveCR: {}, wasCreatedThroughDefaultCR: {}", 
                            facetName, objectId, underRevision, hasActiveCR, result);
                        return result;
                    } else {
                        logger.warn("[DFCR] Object {} {} not found in table {}", facetName, objectId, tableName);
                        return false;
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            logger.error("[DFCR] Error checking if object was created through default CR: {}", e.getMessage(), e);
            // On error, assume it was NOT created through default CR (safer to not lock)
            return false;
        }
    }

    /**
     * DTO for locked fields information
     */
    public static class LockedFieldsInfo {
        private boolean statusLocked;
        private Integer defaultStatusId;
        private boolean lifecycleLocked;
        private Integer defaultLifecycleId;
        private boolean workflowEnabled;

        public boolean isStatusLocked() {
            return statusLocked;
        }

        public void setStatusLocked(boolean statusLocked) {
            this.statusLocked = statusLocked;
        }

        public Integer getDefaultStatusId() {
            return defaultStatusId;
        }

        public void setDefaultStatusId(Integer defaultStatusId) {
            this.defaultStatusId = defaultStatusId;
        }

        public boolean isLifecycleLocked() {
            return lifecycleLocked;
        }

        public void setLifecycleLocked(boolean lifecycleLocked) {
            this.lifecycleLocked = lifecycleLocked;
        }

        public Integer getDefaultLifecycleId() {
            return defaultLifecycleId;
        }

        public void setDefaultLifecycleId(Integer defaultLifecycleId) {
            this.defaultLifecycleId = defaultLifecycleId;
        }

        public boolean isWorkflowEnabled() {
            return workflowEnabled;
        }

        public void setWorkflowEnabled(boolean workflowEnabled) {
            this.workflowEnabled = workflowEnabled;
        }
    }
    
    /**
     * Get status ID by status name from changerequeststatus table
     */
    private Integer getStatusIdByName(String statusName) {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            String sql = "SELECT ID FROM changerequeststatus WHERE PrimaryName = ?";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, statusName);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ID");
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("[DFCR] Error getting status ID for '{}': {}", statusName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Get workflow name from ID, or fallback name if not found
     */
    private String getWorkflowName(Integer workflowId) {
        if (workflowId == null) {
            return "Auto-generated CR";
        }
        try {
            ProcessDefinition pd = processDefinitionDAO.findById(workflowId);
            if (pd != null && pd.getPrimaryName() != null && !pd.getPrimaryName().trim().isEmpty()) {
                return pd.getPrimaryName();
            }
        } catch (SQLException e) {
            logger.warn("[DFCR] Error getting workflow name for ID {}: {}", workflowId, e.getMessage());
        }
        // Fallback if workflow not found
        return "Auto-generated CR";
    }
    
    /**
     * Check if there's a completed/cancelled CREATE CR for the given object (excluding a specific CR).
     * Used to determine if an unknown CR type is likely an EDIT CR.
     */
    private boolean hasCompletedCreateCR(Integer facetId, Integer objectId, Integer excludeCrId) {
        if (facetId == null || objectId == null) {
            return false;
        }
        
        try {
            String[] facetVariations = getFacetNameVariationsForFacetId(facetId);
            
            StringBuilder whereClauses = new StringBuilder();
            for (int i = 0; i < facetVariations.length; i++) {
                String variation = facetVariations[i];
                String exactRef = variation + " " + objectId;
                String pattern = "%" + variation + "%" + objectId + "%";
                
                if (i > 0) whereClauses.append(" OR ");
                whereClauses.append("cr.Reference = '").append(exactRef.replace("'", "''")).append("'");
                whereClauses.append(" OR LOWER(cr.Reference) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
            }
            
            // Find any CR for this object that is completed/cancelled (i.e., a finished CREATE CR)
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT cr.ID FROM changerequest cr ");
            sqlBuilder.append("LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID ");
            sqlBuilder.append("WHERE (").append(whereClauses.toString()).append(") ");
            sqlBuilder.append("AND cr.Deleted_At IS NULL ");
            sqlBuilder.append("AND cr.Mandatory_Workflow = 1 ");
            if (excludeCrId != null) {
                sqlBuilder.append("AND cr.ID != ? ");
            }
            // Must be completed/cancelled
            sqlBuilder.append("AND crs.ID IS NOT NULL ");
            sqlBuilder.append("AND ( ");
            sqlBuilder.append("    LOWER(crs.PrimaryName) LIKE '%complete%' ");
            sqlBuilder.append("    OR LOWER(crs.PrimaryName) LIKE '%cancelled%' ");
            sqlBuilder.append("    OR LOWER(crs.PrimaryName) LIKE '%canceled%' ");
            sqlBuilder.append("    OR LOWER(crs.PrimaryName) LIKE '%reject%' ");
            sqlBuilder.append("    OR LOWER(crs.PrimaryName) LIKE '%closed%' ");
            sqlBuilder.append(") ");
            sqlBuilder.append("LIMIT 1");
            
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                if (excludeCrId != null) {
                    stmt.setInt(1, excludeCrId);
                }
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    boolean found = rs.next();
                    logger.debug("[DFCR] hasCompletedCreateCR - facetId: {}, objectId: {}, excludeCrId: {}, found: {}", 
                        facetId, objectId, excludeCrId, found);
                    return found;
                }
            }
        } catch (SQLException e) {
            logger.warn("[DFCR] Error checking for completed CREATE CR: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get facet name variations for a facet ID (mirrors FacetChangesDAO logic)
     */
    private String[] getFacetNameVariationsForFacetId(int facetId) {
        switch (facetId) {
            case 11: return new String[]{"Data Set", "Dataset", "data set", "dataset"};
            case 12: return new String[]{"Glossary", "glossary"};
            case 13: return new String[]{"System", "system"};
            case 4: return new String[]{"Process", "process"};
            default: return new String[]{};
        }
    }
    
    /**
     * Get CREATE CR ID for an object that hasn't completed (pending, start, running, etc.)
     * This is used to ensure locks are applied even if the CR is not "active" in the traditional sense
     * Uses caching to reduce DB load under concurrent access
     * 
     * @param facetId The facet ID
     * @param objectId The object ID
     * @param workflowCreateId The workflow create ID (to identify CREATE CRs)
     * @return The CREATE CR ID if found, null otherwise
     */
    private Integer getCreateCRIdForObject(Integer facetId, Integer objectId, Integer workflowCreateId) {
        return getCreateCRIdForObject(facetId, objectId, workflowCreateId, null);
    }
    
    /**
     * Find an incomplete CREATE CR for the given object.
     * @param excludeCrId Optional CR ID to exclude from results (e.g., the current active EDIT CR)
     */
    private Integer getCreateCRIdForObject(Integer facetId, Integer objectId, Integer workflowCreateId, Integer excludeCrId) {
        if (facetId == null || objectId == null || workflowCreateId == null) {
            return null;
        }
        
        // Check cache first (reduces DB load under concurrent access)
        String cacheKey = facetId + ":" + objectId + ":" + workflowCreateId + ":" + (excludeCrId != null ? excludeCrId : "none");
        CachedCreateCR cached = createCRCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            logger.debug("[DFCR] Using cached CREATE CR result for object {}: {}", objectId, cached.crId);
            return cached.crId;
        }
        
        try {
            // Get facet name variations manually (since getFacetNameVariations is private)
            String[] facetVariations = getFacetNameVariationsForFacetId(facetId);
            
            // Build dynamic query for all facet name variations
            StringBuilder whereClauses = new StringBuilder();
            for (int i = 0; i < facetVariations.length; i++) {
                String variation = facetVariations[i];
                String exactRef = variation + " " + objectId;
                String pattern = "%" + variation + "%" + objectId + "%";
                
                if (i > 0) whereClauses.append(" OR ");
                whereClauses.append("cr.Reference = '").append(exactRef.replace("'", "''")).append("'");
                whereClauses.append(" OR LOWER(cr.Reference) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
                whereClauses.append(" OR LOWER(cr.PrimaryName) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
                whereClauses.append(" OR LOWER(cr.Summary) LIKE LOWER('").append(pattern.replace("'", "''")).append("')");
            }
            
            // Build SQL query - find CREATE CRs that haven't completed
            // ⚠️ CRITICAL: Must EXCLUDE EDIT CRs - only find actual CREATE CRs
            // This is important when workflowCreateId == workflowEditId (same workflow for both)
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT cr.ID, cr.Reference, cr.CR_StatusID, cr.PrimaryName, cr.Summary, cr.process_definition_id, ");
            sqlBuilder.append("crs.ID as status_id, crs.PrimaryName as status_name ");
            sqlBuilder.append("FROM changerequest cr ");
            sqlBuilder.append("LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID ");
            sqlBuilder.append("WHERE (").append(whereClauses.toString()).append(") ");
            sqlBuilder.append("AND cr.Deleted_At IS NULL ");
            sqlBuilder.append("AND cr.Mandatory_Workflow = 1 ");  // Only automatic CRs
            sqlBuilder.append("AND cr.process_definition_id = ? ");  // Must match workflowCreateId
            // ⚠️ EXCLUDE EDIT CRs by title - only find actual CREATE CRs
            sqlBuilder.append("AND UPPER(COALESCE(cr.PrimaryName, '')) NOT LIKE '%CR FOR EDIT%' ");
            sqlBuilder.append("AND UPPER(COALESCE(cr.Summary, '')) NOT LIKE '%CR FOR EDIT%' ");
            // ⚠️ EXCLUDE specific CR ID if provided (prevents EDIT CR from matching itself as CREATE)
            if (excludeCrId != null) {
                sqlBuilder.append("AND cr.ID != ? ");
            }
            sqlBuilder.append("AND ( ");
            sqlBuilder.append("    cr.CR_StatusID IS NULL ");
            sqlBuilder.append("    OR crs.ID IS NULL ");
            sqlBuilder.append("    OR ( ");
            sqlBuilder.append("        LOWER(crs.PrimaryName) NOT LIKE '%complete%' ");
            sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%cancelled%' ");
            sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%canceled%' ");
            sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%reject%' ");
            sqlBuilder.append("        AND LOWER(crs.PrimaryName) NOT LIKE '%closed%' ");
            sqlBuilder.append("    ) ");
            sqlBuilder.append(") ");
            sqlBuilder.append("ORDER BY cr.Created_At DESC ");
            sqlBuilder.append("LIMIT 1");
            
            String sql = sqlBuilder.toString();
            logger.debug("[DFCR] getCreateCRIdForObject SQL - facetId: {}, objectId: {}, workflowCreateId: {}, excludeCrId: {}", 
                facetId, objectId, workflowCreateId, excludeCrId);
            
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                stmt.setInt(paramIndex++, workflowCreateId);
                if (excludeCrId != null) {
                    stmt.setInt(paramIndex++, excludeCrId);
                }
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    Integer crId = null;
                    if (rs.next()) {
                        crId = rs.getInt("ID");
                        logger.info("[DFCR] Found CREATE CR {} for object {} (facetId: {}, workflowCreateId: {}) that hasn't completed", 
                            crId, objectId, facetId, workflowCreateId);
                    }
                    
                    // Cache the result (even if null) to reduce DB queries under concurrent load
                    createCRCache.put(cacheKey, new CachedCreateCR(crId));
                    return crId;
                }
            }
        } catch (SQLException e) {
            logger.warn("[DFCR] Error checking for CREATE CR for object {}: {}", objectId, e.getMessage());
            // Cache null result on error to prevent repeated failed queries
            createCRCache.put(cacheKey, new CachedCreateCR(null));
        }
        return null;
    }
    
    /**
     * Invalidate CREATE CR cache for an object (call when CR status changes)
     * This ensures fresh data after CR creation/completion/cancellation
     */
    public static void invalidateCreateCRCache(Integer facetId, Integer objectId) {
        if (facetId == null || objectId == null) {
            return;
        }
        // Remove all cache entries for this object (regardless of workflowCreateId)
        String prefix = facetId + ":" + objectId + ":";
        createCRCache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        logger.debug("[DFCR] Invalidated CREATE CR cache for object {} (facetId: {})", objectId, facetId);
    }
    
    /**
     * Invalidate CREATE CR cache based on CR reference (e.g., "Glossary 123")
     * This is useful when only the CR reference is known (e.g., from ChangeRequestServlet)
     */
    public static void invalidateCreateCRCacheByReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return;
        }
        // Parse reference to extract facet name and object ID
        // Format: "FacetName ObjectId" (e.g., "Glossary 123")
        String[] parts = reference.trim().split("\\s+", 2);
        if (parts.length < 2) {
            return;
        }
        try {
            String facetName = parts[0];
            Integer objectId = Integer.parseInt(parts[1]);
            
            // Get facet ID from facet name
            com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
            Integer facetId = facetChangesDAO.getFacetId(facetName);
            if (facetId != null) {
                invalidateCreateCRCache(facetId, objectId);
            }
        } catch (Exception e) {
            logger.debug("[DFCR] Could not parse reference for cache invalidation: {}", reference);
        }
    }
}

