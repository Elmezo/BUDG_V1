package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for User Preference logging
 * Component: "Facet Name + Unison Grid"
 * Only Update case
 */
public class UserPreferenceStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // User Preference only supports Update
        if (oldState == null || newState == null) {
            return changes;
        }
        
        // Track all changed fields
        for (Map.Entry<String, Object> entry : newState.entrySet()) {
            String fieldName = entry.getKey();
            Object newValue = entry.getValue();
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
        if (context != null) {
            String facetName = context.get("facetName") != null ? context.get("facetName").toString() : "";
            return String.format("%s - Unison Grid", facetName);
        }
        return "Unknown - Unison Grid";
    }
    
    @Override
    public Map<String, Object> captureOldState(Object entity, Map<String, Object> context) {
        Map<String, Object> state = new HashMap<>();
        if (context != null) {
            state.putAll(context);
        }
        return state;
    }
    
    @Override
    public Map<String, Object> captureNewState(Object entity, Map<String, Object> context) {
        return captureOldState(entity, context);
    }
    
    private boolean areEqual(Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) return true;
        if (oldValue == null || newValue == null) return false;
        return oldValue.equals(newValue);
    }
}

