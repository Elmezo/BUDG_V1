package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Custom Fields logging
 * Component: Facet name where the custom field was created
 * Handles Create, Update, Delete
 */
public class CustomFieldsStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        if ("Create".equals(changeType) && newState != null) {
            // Create: At least 3 rows (Name, Is Mandatory, Type) + any other mandatory/optional fields
            String[] mandatoryFields = {"name", "isMandatory", "type"};
            for (String field : mandatoryFields) {
                if (newState.containsKey(field)) {
                    Map<String, String> change = new HashMap<>();
                    change.put("field_name", field);
                    change.put("old_value", null);
                    change.put("new_value", newState.get(field) != null ? newState.get(field).toString() : null);
                    changes.add(change);
                }
            }
            
            // Other fields
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                String fieldName = entry.getKey();
                if (!isInArray(fieldName, mandatoryFields)) {
                    Map<String, String> change = new HashMap<>();
                    change.put("field_name", fieldName);
                    change.put("old_value", null);
                    change.put("new_value", entry.getValue() != null ? entry.getValue().toString() : null);
                    changes.add(change);
                }
            }
        } else if ("Update".equals(changeType) && oldState != null && newState != null) {
            // Update: Log any changed fields
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
        } else if ("Delete".equals(changeType) && oldState != null) {
            // Delete: Log all fields
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", entry.getKey());
                change.put("old_value", entry.getValue() != null ? entry.getValue().toString() : null);
                change.put("new_value", null);
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        if (context != null && context.containsKey("facetName")) {
            return context.get("facetName").toString();
        }
        return "Unknown Facet";
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
    
    private boolean isInArray(String value, String[] array) {
        for (String item : array) {
            if (item.equals(value)) return true;
        }
        return false;
    }
}

