package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default strategy for activity logging
 * Handles generic cases where no specific strategy is needed
 */
public class DefaultActivityLogStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        if (oldState == null || newState == null) {
            return changes;
        }
        
        // Find all changed fields
        for (Map.Entry<String, Object> newEntry : newState.entrySet()) {
            String fieldName = newEntry.getKey();
            Object newValue = newEntry.getValue();
            Object oldValue = oldState.get(fieldName);
            
            // Check if value changed
            if (!areEqual(oldValue, newValue)) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", fieldName);
                change.put("old_value", oldValue != null ? oldValue.toString() : null);
                change.put("new_value", newValue != null ? newValue.toString() : null);
                changes.add(change);
            }
        }
        
        // Also check for fields that were removed (in oldState but not in newState)
        for (Map.Entry<String, Object> oldEntry : oldState.entrySet()) {
            String fieldName = oldEntry.getKey();
            if (!newState.containsKey(fieldName)) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", fieldName);
                change.put("old_value", oldEntry.getValue() != null ? oldEntry.getValue().toString() : null);
                change.put("new_value", null);
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        // Default: try to get component from context, or return "General"
        if (context != null && context.containsKey("component")) {
            return context.get("component").toString();
        }
        return "General";
    }
    
    @Override
    public Map<String, Object> captureOldState(Object entity, Map<String, Object> context) {
        // Default implementation: convert entity to map if possible
        if (entity instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) entity;
            return m;
        }
        // For other types, return empty map or use reflection
        return new HashMap<>();
    }
    
    @Override
    public Map<String, Object> captureNewState(Object entity, Map<String, Object> context) {
        // Same as captureOldState
        return captureOldState(entity, context);
    }
    
    /**
     * Compare two values for equality (handles nulls)
     */
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

