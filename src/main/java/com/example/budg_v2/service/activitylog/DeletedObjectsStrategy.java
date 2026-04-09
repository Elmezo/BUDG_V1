package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Deleted Objects logging
 * Component: Facet name from which the object was deleted
 * Delete case only - 2 columns: Field Name, Value
 * Only 3 rows: ID, Name, Description
 */
public class DeletedObjectsStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Deleted Objects only supports Delete case
        if ("Delete".equals(changeType) && oldState != null) {
            // Only log ID, Name, and Description
            String[] fieldsToLog = {"id", "name", "description"};
            
            for (String field : fieldsToLog) {
                if (oldState.containsKey(field)) {
                    Map<String, String> change = new HashMap<>();
                    change.put("field_name", field);
                    change.put("old_value", oldState.get(field) != null ? oldState.get(field).toString() : null);
                    change.put("new_value", null);
                    changes.add(change);
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
            // Only capture ID, Name, Description
            if (context.containsKey("id")) state.put("id", context.get("id"));
            if (context.containsKey("name")) state.put("name", context.get("name"));
            if (context.containsKey("description")) state.put("description", context.get("description"));
        }
        return state;
    }
    
    @Override
    public Map<String, Object> captureNewState(Object entity, Map<String, Object> context) {
        // For Delete, new state is always null
        return new HashMap<>();
    }
}

