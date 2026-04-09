package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Locked Users logging
 * Component: "Locked Users"
 * Delete case only - For each user, display 6 rows
 */
public class LockedUsersStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Locked Users only supports Delete case
        if ("Delete".equals(changeType) && oldState != null) {
            // For each user, log 6 rows
            String[] fieldsToLog = {
                "email", "name", "role", "status", 
                "lockedFrom", "lockedUntil"
            };
            
            for (String field : fieldsToLog) {
                if (oldState.containsKey(field)) {
                    Map<String, String> change = new HashMap<>();
                    // Map internal names to display names
                    String displayName = mapFieldName(field);
                    change.put("field_name", displayName);
                    change.put("old_value", oldState.get(field) != null ? oldState.get(field).toString() : null);
                    change.put("new_value", null);
                    changes.add(change);
                }
            }
        }
        
        return changes;
    }
    
    private String mapFieldName(String field) {
        switch (field) {
            case "email": return "Locked User Email";
            case "name": return "Locked User Name";
            case "role": return "Locked User Role";
            case "status": return "Locked User Status";
            case "lockedFrom": return "Locked From";
            case "lockedUntil": return "Locked Until";
            default: return field;
        }
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        // Use user's name as component name if available, otherwise use default
        if (context != null && context.containsKey("name")) {
            String name = context.get("name").toString();
            if (name != null && !name.trim().isEmpty()) {
                return name;
            }
        }
        return "Locked Users";
    }
    
    @Override
    public Map<String, Object> captureOldState(Object entity, Map<String, Object> context) {
        Map<String, Object> state = new HashMap<>();
        if (context != null) {
            state.put("email", context.get("email"));
            state.put("name", context.get("name"));
            state.put("role", context.get("role"));
            state.put("status", context.get("status"));
            state.put("lockedFrom", context.get("lockedFrom"));
            state.put("lockedUntil", context.get("lockedUntil"));
        }
        return state;
    }
    
    @Override
    public Map<String, Object> captureNewState(Object entity, Map<String, Object> context) {
        // For Delete, new state is always null
        return new HashMap<>();
    }
}

