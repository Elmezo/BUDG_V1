package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Roles & Responsibilities logging
 * Component: Role name (e.g., "Data Steward")
 * Details: Fields like Primary Name, Description, DefaultRole, Facet, Role Type
 */
public class RolesResponsibilitiesStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        if ("Create".equals(changeType) && newState != null) {
            // Create: Log all fields except "facet" (component name already shows the facet)
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                String fieldName = entry.getKey();
                // Skip facet field - it's redundant since component name already shows it
                if ("facet".equals(fieldName)) {
                    continue;
                }
                Map<String, String> change = new HashMap<>();
                String displayName = mapFieldName(fieldName);
                change.put("field_name", displayName);
                change.put("old_value", null);
                change.put("new_value", formatValue(entry.getValue()));
                changes.add(change);
            }
        } else if ("Update".equals(changeType) && oldState != null && newState != null) {
            // Update: Log only changed fields (excluding facet if it didn't change)
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                String fieldName = entry.getKey();
                // Skip facet field if it hasn't changed - component name already shows it
                if ("facet".equals(fieldName)) {
                    continue;
                }
                Object newValue = entry.getValue();
                Object oldValue = oldState.get(fieldName);
                
                if (!areEqual(oldValue, newValue)) {
                    Map<String, String> change = new HashMap<>();
                    String displayName = mapFieldName(fieldName);
                    change.put("field_name", displayName);
                    change.put("old_value", formatValue(oldValue));
                    change.put("new_value", formatValue(newValue));
                    changes.add(change);
                }
            }
            
            // Check if facet changed - only log it if it actually changed
            // Use normalized string comparison to handle whitespace/case differences
            Object oldFacet = oldState.get("facet");
            Object newFacet = newState.get("facet");
            if (!areEqualStrings(oldFacet, newFacet)) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Facet");
                change.put("old_value", formatValue(oldFacet));
                change.put("new_value", formatValue(newFacet));
                changes.add(change);
            }
            
            // Also check for fields that were removed (in oldState but not in newState)
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                String fieldName = entry.getKey();
                // Skip facet - already handled above
                if ("facet".equals(fieldName)) {
                    continue;
                }
                if (!newState.containsKey(fieldName)) {
                    Map<String, String> change = new HashMap<>();
                    String displayName = mapFieldName(fieldName);
                    change.put("field_name", displayName);
                    change.put("old_value", formatValue(entry.getValue()));
                    change.put("new_value", null);
                    changes.add(change);
                }
            }
        } else if ("Delete".equals(changeType) && oldState != null) {
            // Delete: Log all fields except "facet" (component name already shows it)
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                String fieldName = entry.getKey();
                // Skip facet field - it's redundant since component name already shows it
                if ("facet".equals(fieldName)) {
                    continue;
                }
                Map<String, String> change = new HashMap<>();
                String displayName = mapFieldName(fieldName);
                change.put("field_name", displayName);
                change.put("old_value", formatValue(entry.getValue()));
                change.put("new_value", null);
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        // Component should be the role name (primaryName)
        // This is already set correctly in RoleServlet
        if (context != null && context.containsKey("primaryName")) {
            return context.get("primaryName").toString();
        }
        return "Unknown Role";
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
    
    /**
     * Map internal field names to display names
     */
    private String mapFieldName(String fieldName) {
        switch (fieldName) {
            case "primaryName":
                return "Primary Name";
            case "description":
                return "Description";
            case "default":
                return "DefaultRole";
            case "facet":
                return "Facet";
            case "roleType":
                return "Role Type";
            default:
                // Capitalize first letter
                return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }
    }
    
    /**
     * Format value for display
     * Converts "Yes"/"No" to "true"/"false" for boolean fields
     */
    private String formatValue(Object value) {
        if (value == null) {
            return null;
        }
        
        String strValue = value.toString();
        
        // Convert "Yes"/"No" to "true"/"false" for boolean display
        if ("Yes".equalsIgnoreCase(strValue)) {
            return "true";
        } else if ("No".equalsIgnoreCase(strValue)) {
            return "false";
        }
        
        return strValue;
    }
    
    private boolean areEqual(Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) return true;
        if (oldValue == null || newValue == null) return false;
        return oldValue.equals(newValue);
    }
    
    /**
     * Compare two values as strings, handling nulls, whitespace, and case differences
     * Used specifically for facet comparison to avoid false positives
     */
    private boolean areEqualStrings(Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) return true;
        if (oldValue == null || newValue == null) return false;
        
        // Normalize strings: trim whitespace and compare case-insensitively
        String oldStr = oldValue.toString().trim();
        String newStr = newValue.toString().trim();
        
        // If both are empty after trimming, consider them equal
        if (oldStr.isEmpty() && newStr.isEmpty()) return true;
        
        // Case-insensitive comparison
        return oldStr.equalsIgnoreCase(newStr);
    }
}

