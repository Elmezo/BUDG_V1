package com.example.budg_v2.service;

import com.example.budg_v2.dao.AdminActivityLogDAO;
import com.example.budg_v2.model.ActivityLog;
import com.example.budg_v2.model.ActivityLogDetail;
import com.example.budg_v2.service.activitylog.ActivityLogStrategy;
import com.example.budg_v2.service.activitylog.DefaultActivityLogStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for Admin Activity Logging
 * Generic logging engine that works across all admin operations
 */
public class AdminActivityLogService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminActivityLogService.class);
    
    private final AdminActivityLogDAO dao;
    private final Map<String, ActivityLogStrategy> strategies;
    
    public AdminActivityLogService() {
        this.dao = new AdminActivityLogDAO();
        this.strategies = new HashMap<>();
        initializeStrategies();
    }
    
    /**
     * Initialize strategy mappings
     */
    private void initializeStrategies() {
        // Register strategies
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_SYSTEM_SETTINGS, 
                      new com.example.budg_v2.service.activitylog.SystemSettingsStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_OWNERSHIP_TRANSFER, 
                      new com.example.budg_v2.service.activitylog.OwnershipTransferStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_ROLE_PERMISSIONS, 
                      new com.example.budg_v2.service.activitylog.RolePermissionsStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_DEFAULT_WORKFLOWS, 
                      new com.example.budg_v2.service.activitylog.DefaultWorkflowsStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_DEFAULT_CRS, 
                      new com.example.budg_v2.service.activitylog.DefaultCRsStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_CUSTOM_FIELDS, 
                      new com.example.budg_v2.service.activitylog.CustomFieldsStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_DROPDOWN_CONFIG, 
                      new com.example.budg_v2.service.activitylog.DropdownConfigStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_SEGMENTS, 
                      new com.example.budg_v2.service.activitylog.SegmentsStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_DELETED_OBJECTS, 
                      new com.example.budg_v2.service.activitylog.DeletedObjectsStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_ROLES_ASSIGNMENT, 
                      new com.example.budg_v2.service.activitylog.RolesAssignmentStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_MANAGE_LOCKS, 
                      new com.example.budg_v2.service.activitylog.ManageLocksStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_LOCKED_USERS, 
                      new com.example.budg_v2.service.activitylog.LockedUsersStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_USER_PREFERENCE, 
                      new com.example.budg_v2.service.activitylog.UserPreferenceStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_APP_SETTINGS, 
                      new com.example.budg_v2.service.activitylog.AppSettingsStrategy());
        strategies.put(com.example.budg_v2.constants.ActivityLogConstants.SETTING_ROLES_RESPONSIBILITIES, 
                      new com.example.budg_v2.service.activitylog.RolesResponsibilitiesStrategy());
    }
    
    /**
     * Main entry point for logging an activity
     */
    public void logActivity(ActivityLogContext context) {
        try {
            // Get the appropriate strategy
            ActivityLogStrategy strategy = strategies.getOrDefault(
                context.getSetting(), 
                new DefaultActivityLogStrategy()
            );
            
            // Generate diff if we have both old and new states
            List<Map<String, String>> changes = new ArrayList<>();
            
            if (context.getOldState() != null && context.getNewState() != null) {
                // Use strategy to generate diff
                changes = strategy.generateDiff(
                    context.getOldState(), 
                    context.getNewState(), 
                    context.getChangeType()
                );
            } else if (context.getChangeType().equals("Create") && context.getNewState() != null) {
                // For Create, all fields in newState are changes
                // Use strategy if it can handle Create, otherwise use default logic
                Map<String, Object> emptyOldState = new HashMap<>();
                changes = strategy.generateDiff(emptyOldState, context.getNewState(), "Create");
                
                // If strategy didn't return changes, use default logic
                if (changes.isEmpty()) {
                    for (Map.Entry<String, Object> entry : context.getNewState().entrySet()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("field_name", entry.getKey());
                        change.put("old_value", null);
                        change.put("new_value", entry.getValue() != null ? entry.getValue().toString() : null);
                        changes.add(change);
                    }
                }
            } else if (context.getChangeType().equals("Delete") && context.getOldState() != null) {
                // For Delete, all fields in oldState are changes
                // Use strategy if it can handle Delete, otherwise use default logic
                Map<String, Object> emptyNewState = new HashMap<>();
                changes = strategy.generateDiff(context.getOldState(), emptyNewState, "Delete");
                
                // If strategy didn't return changes, use default logic
                if (changes.isEmpty()) {
                    for (Map.Entry<String, Object> entry : context.getOldState().entrySet()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("field_name", entry.getKey());
                        change.put("old_value", entry.getValue() != null ? entry.getValue().toString() : null);
                        change.put("new_value", null);
                        changes.add(change);
                    }
                }
            }
            
            // Determine component name - always try strategy first, then fallback to context component
            String componentName = null;
            
            // Always try strategy first (with contextMap if available, otherwise null)
            Map<String, Object> contextMap = context.getContextMap();
            String strategyComponent = strategy.getComponentName(contextMap);
            if (strategyComponent != null && !strategyComponent.trim().isEmpty()) {
                componentName = strategyComponent;
            }
            
            // If strategy didn't provide a component name, use the one from context
            if (componentName == null || componentName.trim().isEmpty()) {
                componentName = context.getComponent();
            }
            
            // Ensure component name is never null - use fallback
            if (componentName == null || componentName.trim().isEmpty()) {
                // Try to get from newState or oldState
                if (context.getNewState() != null && context.getNewState().containsKey("name")) {
                    componentName = String.valueOf(context.getNewState().get("name"));
                } else if (context.getOldState() != null && context.getOldState().containsKey("name")) {
                    componentName = String.valueOf(context.getOldState().get("name"));
                } else {
                    // Final fallback - use setting name
                    componentName = context.getSetting() != null ? context.getSetting() : "Unknown Component";
                }
            }
            
            // Create ActivityLog
            ActivityLog log = new ActivityLog(
                context.getSetting(),
                componentName,
                context.getUserId(),
                context.getUserName(),
                context.getUserEmail(),
                context.getChangeType()
            );
            
            // Save log and get generated ID
            Long logId = dao.insertActivityLog(log);
            
            // Create details
            if (!changes.isEmpty()) {
                List<ActivityLogDetail> details = new ArrayList<>();
                for (Map<String, String> change : changes) {
                    ActivityLogDetail detail = new ActivityLogDetail(
                        logId,
                        change.get("field_name"),
                        change.get("old_value"),
                        change.get("new_value")
                    );
                    details.add(detail);
                }
                
                // Bulk insert details
                dao.insertActivityLogDetails(details);
            }
            
            logger.debug("Activity logged successfully: {} - {} by {}", 
                context.getSetting(), context.getComponent(), context.getUserName());
            
        } catch (SQLException e) {
            // Log error but don't fail the main operation
            logger.error("Failed to log activity: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Log an activity without details (e.g., Download Logs, Other Actions)
     */
    public void logSimpleActivity(String setting, String component, Integer userId, 
                                 String userName, String userEmail, String changeType) {
        ActivityLogContext context = new ActivityLogContext();
        context.setSetting(setting);
        context.setComponent(component);
        context.setUserId(userId);
        context.setUserName(userName);
        context.setUserEmail(userEmail);
        context.setChangeType(changeType);
        // No old/new state for simple activities
        logActivity(context);
    }
    
    /**
     * Register a strategy for a specific setting
     */
    public void registerStrategy(String setting, ActivityLogStrategy strategy) {
        strategies.put(setting, strategy);
    }
}

