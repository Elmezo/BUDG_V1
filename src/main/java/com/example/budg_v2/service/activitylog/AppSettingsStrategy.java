package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for App Settings logging
 * Components: Quick Link, Display Settings, Search Settings, Glossary RollUp
 * Supports Create and Update cases
 */
public class AppSettingsStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // App Settings supports Create and Update
        if ("Create".equals(changeType) && newState != null) {
            // For Create, log all fields from newState
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                Map<String, String> change = new HashMap<>();
                String displayFieldName = mapFieldName(entry.getKey());
                change.put("field_name", displayFieldName);
                change.put("old_value", null);
                change.put("new_value", entry.getValue() != null ? entry.getValue().toString() : null);
                changes.add(change);
            }
        } else if ("Update".equals(changeType) && oldState != null && newState != null) {
            // For Update, track all changed fields
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                String fieldName = entry.getKey();
                Object newValue = entry.getValue();
                Object oldValue = oldState.get(fieldName);
                
                if (!areEqual(oldValue, newValue)) {
                    Map<String, String> change = new HashMap<>();
                    String displayFieldName = mapFieldName(fieldName);
                    change.put("field_name", displayFieldName);
                    change.put("old_value", oldValue != null ? oldValue.toString() : null);
                    change.put("new_value", newValue != null ? newValue.toString() : null);
                    changes.add(change);
                }
            }
            
            // Also check for fields removed in oldState but not in newState
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                String fieldName = entry.getKey();
                if (!newState.containsKey(fieldName)) {
                    Map<String, String> change = new HashMap<>();
                    String displayFieldName = mapFieldName(fieldName);
                    change.put("field_name", displayFieldName);
                    change.put("old_value", entry.getValue() != null ? entry.getValue().toString() : null);
                    change.put("new_value", null);
                    changes.add(change);
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Map internal field names to display names
     */
    private String mapFieldName(String fieldName) {
        // For Display Settings, field names are facet names (e.g., "Dataset", "Attribute")
        // Return as-is since they're already user-friendly names
        // For other settings, map to display names
        switch (fieldName) {
            // Quick Links fields
            case "Saved Search ID": return "Saved Search ID";
            case "Saved Search Name": return "Saved Search Name";
            case "Description": return "Description";
            // Search Settings fields
            case "Enabled": return "Enabled";
            case "Value": return "Value";
            case "Setting": return "Setting";
            case "Fuzzy Search Enabled": return "Fuzzy Search Enabled";
            case "Hide Non-Public Objects": return "Hide Non-Public Objects";
            // Glossary RollUp fields
            case "Enabled Types": return "Enabled Types";
            // Display Settings fields
            case "Active Facets for Unison": return "Active Facets for Unison";
            // Display Settings fields (legacy - should not be used anymore)
            case "Active Facets": return "Active Facets";
            case "Inactive Facets": return "Inactive Facets";
            // Generic
            case "Definition": return "Definition";
            default: 
                // For Display Settings, field names are facet names - return as-is
                return fieldName;
        }
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        if (context != null && context.containsKey("component")) {
            return context.get("component").toString();
        }
        return "Unknown Component";
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

