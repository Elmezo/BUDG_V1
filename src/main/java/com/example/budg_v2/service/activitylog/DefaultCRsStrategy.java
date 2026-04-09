package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strategy for Default Change Requests logging
 * Component: One of the 4 facets
 * Only Update case is supported
 */
public class DefaultCRsStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Default CRs only supports Update
        if (oldState == null || newState == null) {
            return changes;
        }
        
        // Track all changed fields in newState
        // Ensure we check ALL fields that exist in either oldState or newState
        Set<String> allFieldNames = new HashSet<>();
        allFieldNames.addAll(oldState.keySet());
        allFieldNames.addAll(newState.keySet());
        
        for (String fieldName : allFieldNames) {
            Object newValueObj = newState.get(fieldName);
            Object oldValueObj = oldState.get(fieldName);
            
            // Get raw string values for comparison (before normalization)
            String oldStr = oldValueObj != null ? oldValueObj.toString().trim() : "";
            String newStr = newValueObj != null ? newValueObj.toString().trim() : "";
            
            // Normalize for comparison (empty strings and "Select a workflow" become null)
            String normalizedOld = normalizeValue(oldValueObj);
            String normalizedNew = normalizeValue(newValueObj);
            
            // Check if values are different using normalized values
            boolean isDifferent = !areEqual(normalizedOld, normalizedNew);
            
            // Also check raw strings - if they're different, it's a change
            // This catches edge cases where normalization might hide differences
            if (!isDifferent && !oldStr.equals(newStr)) {
                isDifferent = true;
            }
            
            if (isDifferent) {
                Map<String, String> change = new HashMap<>();
                // Map internal field names to display names
                String displayName = mapFieldName(fieldName);
                change.put("field_name", displayName);
                
                // Get display values (for showing in the log)
                String displayOld = normalizeValueForDisplay(oldValueObj);
                String displayNew = normalizeValueForDisplay(newValueObj);
                change.put("old_value", displayOld != null ? displayOld : "");
                change.put("new_value", displayNew != null ? displayNew : "");
                changes.add(change);
            }
        }
        
        // Note: The above loop already checks all fields from both oldState and newState,
        // so we don't need a separate loop for removed fields
        
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
    
    private boolean areEqual(String normalizedOld, String normalizedNew) {
        // Both values are already normalized (null means empty/not set)
        if (normalizedOld == null && normalizedNew == null) return true;
        if (normalizedOld == null || normalizedNew == null) return false;
        return normalizedOld.equals(normalizedNew);
    }
    
    /**
     * Normalize value for comparison - convert null, empty string, and "Select a workflow" to null
     * Handles boolean values by converting to "true"/"false" strings
     */
    private String normalizeValue(Object value) {
        if (value == null) return null;
        
        // Handle boolean values
        if (value instanceof Boolean) {
            return value.toString(); // "true" or "false"
        }
        
        String str = value.toString().trim();
        if (str.isEmpty() || str.equals("Select a workflow")) {
            return null;
        }
        return str;
    }
    
    /**
     * Normalize value for display - convert null to empty string, but keep "Select a workflow" as is
     */
    private String normalizeValueForDisplay(Object value) {
        if (value == null) return "";
        String str = value.toString().trim();
        // Keep "Select a workflow" as is for display purposes
        return str;
    }
    
    /**
     * Map internal field names to display names
     * Format: "Settings for Facet - {Field Description}"
     */
    private String mapFieldName(String fieldName) {
        switch (fieldName) {
            case "defaultWorkflowForCreating":
                return "Settings for Facet - Default Workflow for Creating Object";
            case "defaultWorkflowForEditing":
                return "Settings for Facet - Default Workflow for Editing Object";
            case "defaultBUDGStatusForCreating":
                return "Settings for Facet - Default BUDG Status for Creating Object";
            case "defaultLifecycleForCreating":
                return "Settings for Facet - Default Lifecycle for Creating Object";
            case "defaultChangeRequestType":
                return "Settings for Facet - Default Change Request Type";
            case "defaultChangeRequestUrgency":
                return "Settings for Facet - Default Change Request Urgency";
            case "defaultChangeRequestSeverity":
                return "Settings for Facet - Default Change Request Severity";
            case "defaultChangeRequestSystem":
                return "Settings for Facet - Default Change Request System";
            case "workflowApprovalEnabled":
                return "Settings for Facet - Workflow Approval Enabled";
            case "workflowForTypesEnabled":
                return "Settings for Facet - Workflow for Types Enabled";
            case "enableWorkflowApprovalForAdministrators":
                return "Settings for Facet - Enable Workflow Approval for Administrators";
            default:
                // Capitalize and format default field name
                return "Settings for Facet - " + formatFieldName(fieldName);
        }
    }
    
    /**
     * Format field name from camelCase to Title Case
     */
    private String formatFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        
        // Split camelCase
        String formatted = fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
        // Capitalize first letter of each word
        String[] words = formatted.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(word.substring(0, 1).toUpperCase())
                  .append(word.substring(1).toLowerCase());
        }
        return result.toString();
    }
}

