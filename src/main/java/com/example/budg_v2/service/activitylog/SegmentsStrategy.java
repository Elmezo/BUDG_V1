package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Segments logging
 * Component: Segment name
 * Handles Create, Update, Delete
 */
public class SegmentsStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Fields that should only be shown if they have values (exclude name and description - they should always be shown)
        String[] conditionalFields = {"assigned_org_units", "assigned_users"};
        
        if ("Create".equals(changeType) && newState != null) {
            // Create: Log all fields that have values
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                String fieldName = entry.getKey();
                String newValue = formatValue(entry.getValue());
                
                // For conditional fields, only show if they have a value
                if (isConditionalField(fieldName, conditionalFields)) {
                    if (newValue != null && !newValue.trim().isEmpty()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("field_name", mapFieldName(fieldName));
                        change.put("old_value", null);
                        change.put("new_value", newValue);
                        changes.add(change);
                    }
                } else {
                    // For other fields (like admin_users), always show
                    Map<String, String> change = new HashMap<>();
                    change.put("field_name", mapFieldName(fieldName));
                    change.put("old_value", null);
                    change.put("new_value", newValue);
                    changes.add(change);
                }
            }
        } else if ("Update".equals(changeType) && oldState != null && newState != null) {
            // Update: Log only changed fields that have values
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                String fieldName = entry.getKey();
                Object newValue = entry.getValue();
                Object oldValue = oldState.get(fieldName);
                
                if (!areEqual(oldValue, newValue)) {
                    String formattedOldValue = formatValue(oldValue);
                    String formattedNewValue = formatValue(newValue);
                    
                    // For conditional fields, only show if at least one value exists
                    if (isConditionalField(fieldName, conditionalFields)) {
                        if ((formattedOldValue != null && !formattedOldValue.trim().isEmpty()) ||
                            (formattedNewValue != null && !formattedNewValue.trim().isEmpty())) {
                            Map<String, String> change = new HashMap<>();
                            change.put("field_name", mapFieldName(fieldName));
                            change.put("old_value", formattedOldValue);
                            change.put("new_value", formattedNewValue);
                            changes.add(change);
                        }
                    } else {
                        // For other fields (like admin_users), always show if changed
                        Map<String, String> change = new HashMap<>();
                        change.put("field_name", mapFieldName(fieldName));
                        change.put("old_value", formattedOldValue);
                        change.put("new_value", formattedNewValue);
                        changes.add(change);
                    }
                }
            }
            
            // Also check for fields that were removed (in oldState but not in newState)
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                String fieldName = entry.getKey();
                if (!newState.containsKey(fieldName)) {
                    String formattedOldValue = formatValue(entry.getValue());
                    // For conditional fields, only show if old value exists
                    if (isConditionalField(fieldName, conditionalFields)) {
                        if (formattedOldValue != null && !formattedOldValue.trim().isEmpty()) {
                            Map<String, String> change = new HashMap<>();
                            change.put("field_name", mapFieldName(fieldName));
                            change.put("old_value", formattedOldValue);
                            change.put("new_value", null);
                            changes.add(change);
                        }
                    } else {
                        // For other fields, always show
                        Map<String, String> change = new HashMap<>();
                        change.put("field_name", mapFieldName(fieldName));
                        change.put("old_value", formattedOldValue);
                        change.put("new_value", null);
                        changes.add(change);
                    }
                }
            }
        } else if ("Delete".equals(changeType) && oldState != null) {
            // Delete: Log only fields that have values
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                String fieldName = entry.getKey();
                String oldValue = formatValue(entry.getValue());
                
                // For conditional fields, only show if they have a value
                if (isConditionalField(fieldName, conditionalFields)) {
                    if (oldValue != null && !oldValue.trim().isEmpty()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("field_name", mapFieldName(fieldName));
                        change.put("old_value", oldValue);
                        change.put("new_value", null);
                        changes.add(change);
                    }
                } else {
                    // For other fields (like admin_users), always show
                    Map<String, String> change = new HashMap<>();
                    change.put("field_name", mapFieldName(fieldName));
                    change.put("old_value", oldValue);
                    change.put("new_value", null);
                    changes.add(change);
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Check if a field is in the conditional fields list
     */
    private boolean isConditionalField(String fieldName, String[] conditionalFields) {
        for (String conditionalField : conditionalFields) {
            if (conditionalField.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Map internal field names to display names
     */
    private String mapFieldName(String fieldName) {
        switch (fieldName) {
            case "name":
                return "Segment Name";
            case "description":
                return "Segment Description";
            case "admin_users":
                return "Admin User(s) Assigned";
            case "assigned_org_units":
                return "Assigned Org Units";
            case "assigned_users":
                return "Assigned Users";
            default:
                // Capitalize first letter and replace underscores with spaces
                return fieldName.substring(0, 1).toUpperCase() + 
                       fieldName.substring(1).replace("_", " ");
        }
    }
    
    /**
     * Format value for display
     */
    private String formatValue(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString();
        // Trim whitespace
        str = str.trim();
        // If empty string, return null for cleaner display
        if (str.isEmpty()) {
            return null;
        }
        // If it's the string "null", return null
        if ("null".equalsIgnoreCase(str)) {
            return null;
        }
        return str;
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        if (context != null && context.containsKey("segmentName")) {
            return context.get("segmentName").toString();
        }
        return "Unknown Segment";
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

