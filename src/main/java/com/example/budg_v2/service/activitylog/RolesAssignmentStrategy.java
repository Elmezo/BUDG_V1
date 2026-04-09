package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Roles Assignment logging
 * Component: Facet name
 * Just 3 rows: Facet Name, Role Name, Members Assigned
 */
public class RolesAssignmentStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Log 3 fields: Facet Name, Role Name, Members Assigned
        String[] fieldsToLog = {"facetName", "roleName", "membersAssigned"};
        
        if ("Create".equals(changeType)) {
            // For Create, log all fields from newState
            if (newState != null) {
                for (String field : fieldsToLog) {
                    Object newValue = newState.get(field);
                    if (newValue != null) {
                        Map<String, String> change = new HashMap<>();
                        String displayName = "facetName".equals(field) ? "Facet Name" :
                                           "roleName".equals(field) ? "Role Name" :
                                           "membersAssigned".equals(field) ? "Members Assigned" : field;
                        change.put("field_name", displayName);
                        change.put("old_value", null);
                        change.put("new_value", newValue.toString());
                        changes.add(change);
                    }
                }
            }
        } else if ("Delete".equals(changeType)) {
            // For Delete, log all fields from oldState
            if (oldState != null) {
                for (String field : fieldsToLog) {
                    Object oldValue = oldState.get(field);
                    if (oldValue != null) {
                        Map<String, String> change = new HashMap<>();
                        String displayName = "facetName".equals(field) ? "Facet Name" :
                                           "roleName".equals(field) ? "Role Name" :
                                           "membersAssigned".equals(field) ? "Members Assigned" : field;
                        change.put("field_name", displayName);
                        change.put("old_value", oldValue.toString());
                        change.put("new_value", null);
                        changes.add(change);
                    }
                }
            }
        } else {
            // Update case: compare old and new states
            if (oldState != null && newState != null) {
                for (String field : fieldsToLog) {
                    Object oldValue = oldState.get(field);
                    Object newValue = newState.get(field);
                    
                    if (!areEqual(oldValue, newValue)) {
                        Map<String, String> change = new HashMap<>();
                        // Map internal names to display names
                        String displayName = "facetName".equals(field) ? "Facet Name" :
                                           "roleName".equals(field) ? "Role Name" :
                                           "membersAssigned".equals(field) ? "Members Assigned" : field;
                        change.put("field_name", displayName);
                        change.put("old_value", oldValue != null ? oldValue.toString() : null);
                        change.put("new_value", newValue != null ? newValue.toString() : null);
                        changes.add(change);
                    }
                }
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
            state.put("facetName", context.get("facetName"));
            state.put("roleName", context.get("roleName"));
            state.put("membersAssigned", context.get("membersAssigned"));
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

