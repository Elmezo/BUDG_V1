package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Dropdown Configurations logging
 * Component: Name of the facet field where value was added
 * Handles Create, Update, Delete
 */
public class DropdownConfigStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        if ("Create".equals(changeType) && newState != null) {
            // Create: Primary Name (required) + Description (if specified)
            if (newState.containsKey("name")) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Primary Name");
                change.put("old_value", null);
                change.put("new_value", newState.get("name").toString());
                changes.add(change);
            }
            
            if (newState.containsKey("description")) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Description");
                change.put("old_value", null);
                change.put("new_value", newState.get("description").toString());
                changes.add(change);
            }
        } else if ("Update".equals(changeType) && oldState != null && newState != null) {
            // Update: Log any changed fields
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                String fieldName = entry.getKey();
                Object newValue = entry.getValue();
                Object oldValue = oldState.get(fieldName);
                
                if (!areEqual(oldValue, newValue)) {
                    Map<String, String> change = new HashMap<>();
                    // Map internal field names to display names
                    String displayFieldName = "name".equals(fieldName) ? "Primary Name" : 
                                             "description".equals(fieldName) ? "Description" : fieldName;
                    change.put("field_name", displayFieldName);
                    change.put("old_value", oldValue != null ? oldValue.toString() : null);
                    change.put("new_value", newValue != null ? newValue.toString() : null);
                    changes.add(change);
                }
            }
        } else if ("Delete".equals(changeType) && oldState != null) {
            // Delete: Log all fields
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                String displayFieldName = "name".equals(entry.getKey()) ? "Primary Name" : 
                                         "description".equals(entry.getKey()) ? "Description" : entry.getKey();
                Map<String, String> change = new HashMap<>();
                change.put("field_name", displayFieldName);
                change.put("old_value", entry.getValue() != null ? entry.getValue().toString() : null);
                change.put("new_value", null);
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        if (context != null && context.containsKey("fieldName")) {
            return context.get("fieldName").toString();
        }
        return "Unknown Field";
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

