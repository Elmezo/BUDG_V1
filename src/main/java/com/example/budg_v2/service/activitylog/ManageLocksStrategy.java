package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Manage Locks logging
 * Component: "Object Lock"
 * Delete case only - 2 columns: Field Name, Value
 */
public class ManageLocksStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Manage Locks only supports Delete case
        if ("Delete".equals(changeType) && oldState != null) {
            // Log all fields from old state with proper field name mapping
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                Map<String, String> change = new HashMap<>();
                String displayFieldName = mapFieldName(entry.getKey());
                change.put("field_name", displayFieldName);
                change.put("old_value", entry.getValue() != null ? entry.getValue().toString() : null);
                change.put("new_value", null);
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    private String mapFieldName(String field) {
        switch (field) {
            case "objectName": return "Object Name";
            case "lockedUserName": return "Locked By User Name";
            case "lockedUserEmail": return "Locked By User Email";
            default: return field;
        }
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        return "Object Lock";
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
        // For Delete, new state is always null
        return new HashMap<>();
    }
}

