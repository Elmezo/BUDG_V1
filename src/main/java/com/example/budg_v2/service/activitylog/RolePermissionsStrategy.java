package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Role Permissions logging
 * Component: Role name (e.g., "Business Area Head")
 * Details: Separate fields for Role, Module, Permission
 */
public class RolePermissionsStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Check if oldState is null or empty (for Create operations)
        boolean isCreate = (oldState == null || oldState.isEmpty()) && newState != null;
        // Check if newState is null or empty (for Delete operations)
        boolean isDelete = oldState != null && (newState == null || newState.isEmpty());
        
        // Role Permissions: Show separate fields (Role, Module, Permission)
        if (isCreate) {
            // Create case - show all three fields with empty old values
            String role = getValue(newState, "role");
            String module = getValue(newState, "module");
            String permission = getValue(newState, "permission");
            
            if (role != null && !role.isEmpty()) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Role");
                change.put("old_value", null);
                change.put("new_value", role);
                changes.add(change);
            }
            
            if (module != null && !module.isEmpty()) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Module");
                change.put("old_value", null);
                change.put("new_value", module);
                changes.add(change);
            }
            
            if (permission != null && !permission.isEmpty()) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Permission");
                change.put("old_value", null);
                change.put("new_value", permission);
                changes.add(change);
            }
        } else if (isDelete) {
            // Delete case
            String role = getValue(oldState, "role");
            String module = getValue(oldState, "module");
            String permission = getValue(oldState, "permission");
            
            if (role != null && !role.isEmpty()) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Role");
                change.put("old_value", role);
                change.put("new_value", null);
                changes.add(change);
            }
            
            if (module != null && !module.isEmpty()) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Module");
                change.put("old_value", module);
                change.put("new_value", null);
                changes.add(change);
            }
            
            if (permission != null && !permission.isEmpty()) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Permission");
                change.put("old_value", permission);
                change.put("new_value", null);
                changes.add(change);
            }
        } else if (oldState != null && newState != null) {
            // Update case - check each field individually
            String oldRole = getValue(oldState, "role");
            String newRole = getValue(newState, "role");
            String oldModule = getValue(oldState, "module");
            String newModule = getValue(newState, "module");
            String oldPermission = getValue(oldState, "permission");
            String newPermission = getValue(newState, "permission");
            
            // Check if Role changed
            if (!equals(oldRole, newRole)) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Role");
                change.put("old_value", oldRole);
                change.put("new_value", newRole);
                changes.add(change);
            }
            
            // Check if Module changed
            if (!equals(oldModule, newModule)) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Module");
                change.put("old_value", oldModule);
                change.put("new_value", newModule);
                changes.add(change);
            }
            
            // Check if Permission changed
            if (!equals(oldPermission, newPermission)) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Permission");
                change.put("old_value", oldPermission);
                change.put("new_value", newPermission);
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    private String getValue(Map<String, Object> state, String key) {
        if (state == null) return null;
        Object value = state.get(key);
        return value != null ? value.toString() : null;
    }
    
    private boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        // Component should be just the role name (e.g., "Business Area Head")
        if (context != null) {
            Object role = context.get("role");
            if (role != null) {
                return role.toString();
            }
        }
        return "Unknown Role";
    }
    
    @Override
    public Map<String, Object> captureOldState(Object entity, Map<String, Object> context) {
        Map<String, Object> state = new HashMap<>();
        if (context != null) {
            state.put("module", context.get("module"));
            state.put("role", context.get("role"));
            state.put("permission", context.get("permission"));
        }
        return state;
    }
    
    @Override
    public Map<String, Object> captureNewState(Object entity, Map<String, Object> context) {
        return captureOldState(entity, context);
    }
}

