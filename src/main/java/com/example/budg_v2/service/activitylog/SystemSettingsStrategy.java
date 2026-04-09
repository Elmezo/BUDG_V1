package com.example.budg_v2.service.activitylog;

import com.example.budg_v2.constants.ActivityLogConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for System Settings logging
 * Handles Environment, Dashboard, and other system settings components
 */
public class SystemSettingsStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        if (oldState == null || newState == null) {
            return changes;
        }
        
        // System Settings only supports Update operations
        // Track all changed fields
        for (Map.Entry<String, Object> newEntry : newState.entrySet()) {
            String fieldName = newEntry.getKey();
            Object newValue = newEntry.getValue();
            Object oldValue = oldState.get(fieldName);
            
            if (!areEqual(oldValue, newValue)) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", fieldName);
                change.put("old_value", oldValue != null ? oldValue.toString() : null);
                change.put("new_value", newValue != null ? newValue.toString() : null);
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        // Component is determined by the setting group (e.g., "Environment", "Dashboard")
        if (context != null && context.containsKey("component")) {
            return context.get("component").toString();
        }
        return ActivityLogConstants.COMPONENT_DASHBOARD;
    }
    
    @Override
    public Map<String, Object> captureOldState(Object entity, Map<String, Object> context) {
        // For System Settings, entity is a Map<String, Object> of settings
        if (entity instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) entity;
            return new HashMap<>(m);
        }
        return new HashMap<>();
    }
    
    @Override
    public Map<String, Object> captureNewState(Object entity, Map<String, Object> context) {
        return captureOldState(entity, context);
    }
    
    private boolean areEqual(Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) {
            return true;
        }
        if (oldValue == null || newValue == null) {
            return false;
        }
        return oldValue.equals(newValue);
    }
}

