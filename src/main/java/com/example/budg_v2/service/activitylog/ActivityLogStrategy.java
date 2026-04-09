package com.example.budg_v2.service.activitylog;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for different activity log strategies
 * Each setting type implements this interface to handle its specific logging logic
 */
public interface ActivityLogStrategy {
    
    /**
     * Generate the list of field changes from old and new states
     * @param oldState The state before the change
     * @param newState The state after the change
     * @param changeType The type of change (Create, Update, Delete)
     * @return List of field changes, each represented as a map with field_name, old_value, new_value
     */
    List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                          Map<String, Object> newState, 
                                          String changeType);
    
    /**
     * Get the component name for the given context
     * @param context Additional context (e.g., entity name, setting group)
     * @return Component name
     */
    String getComponentName(Map<String, Object> context);
    
    /**
     * Capture the old state before an action
     * @param entity The entity being modified (can be null for Create)
     * @param context Additional context
     * @return Map of field names to values
     */
    Map<String, Object> captureOldState(Object entity, Map<String, Object> context);
    
    /**
     * Capture the new state after an action
     * @param entity The entity after modification (can be null for Delete)
     * @param context Additional context
     * @return Map of field names to values
     */
    Map<String, Object> captureNewState(Object entity, Map<String, Object> context);
}

