package com.example.budg_v2.service.activitylog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Default Workflows logging
 * Component: "Facet Name + Workflow Name"
 * Handles Create, Update (including diagram updates), Delete
 */
public class DefaultWorkflowsStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        if ("Create".equals(changeType) && newState != null) {
            // Create: Log all mandatory fields + any optional fields + workflow diagram
            String[] mandatoryFields = {"name", "description", "facetName", "status"};
            for (String field : mandatoryFields) {
                if (newState.containsKey(field)) {
                    Map<String, String> change = new HashMap<>();
                    String displayName = mapFieldName(field);
                    change.put("field_name", displayName);
                    change.put("old_value", null);
                    change.put("new_value", newState.get(field) != null ? newState.get(field).toString() : null);
                    changes.add(change);
                }
            }
            
            // Optional fields - Workflow Diagram
            if (newState.containsKey("workflowDiagram")) {
                Map<String, String> change = new HashMap<>();
                change.put("field_name", "Workflow Diagram:"); // Note the colon to match UI
                change.put("old_value", null);
                // Store actual XML content for display
                String diagramXml = newState.get("workflowDiagram") != null ? 
                    newState.get("workflowDiagram").toString() : "";
                change.put("new_value", diagramXml);
                changes.add(change);
            }
        } else if ("Update".equals(changeType) && oldState != null && newState != null) {
            // Update: Log any changed fields and/or diagram
            // Check all fields in newState
            for (Map.Entry<String, Object> entry : newState.entrySet()) {
                String fieldName = entry.getKey();
                Object newValue = entry.getValue();
                Object oldValue = oldState.get(fieldName);
                
                // Special handling for workflow diagram
                if ("workflowDiagram".equals(fieldName)) {
                    // Compare diagram markers (handle null/empty cases)
                    String oldDiagram = oldValue != null ? oldValue.toString() : "";
                    String newDiagram = newValue != null ? newValue.toString() : "";
                    if (!oldDiagram.equals(newDiagram)) {
                        Map<String, String> change = new HashMap<>();
                        change.put("field_name", "Workflow Diagram:"); // Note the colon to match UI
                        
                        // Get process definition ID from states
                        Integer processDefId = null;
                        if (newState.containsKey("processDefinitionId")) {
                            Object idObj = newState.get("processDefinitionId");
                            if (idObj instanceof Number) {
                                processDefId = ((Number) idObj).intValue();
                            }
                        } else if (oldState.containsKey("processDefinitionId")) {
                            Object idObj = oldState.get("processDefinitionId");
                            if (idObj instanceof Number) {
                                processDefId = ((Number) idObj).intValue();
                            }
                        }
                        
                        // Store reference format that frontend can parse to fetch and render diagrams
                        if (processDefId != null) {
                            change.put("old_value", oldDiagram.isEmpty() ? null : "DIAGRAM_REF:" + processDefId);
                            change.put("new_value", newDiagram.isEmpty() ? null : "DIAGRAM_REF:" + processDefId);
                        } else {
                            // Fallback if no ID available
                            change.put("old_value", oldDiagram.isEmpty() ? null : "View");
                            change.put("new_value", newDiagram.isEmpty() ? null : "View");
                        }
                        changes.add(change);
                    }
                } else if (!areEqual(oldValue, newValue)) {
                    // Map field names to display names
                    Map<String, String> change = new HashMap<>();
                    String displayName = mapFieldName(fieldName);
                    change.put("field_name", displayName);
                    change.put("old_value", oldValue != null ? oldValue.toString() : null);
                    change.put("new_value", newValue != null ? newValue.toString() : null);
                    changes.add(change);
                }
            }
            
            // Also check for fields that were removed (in oldState but not in newState)
            // This handles cases where a diagram was deleted
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                String fieldName = entry.getKey();
                if (!newState.containsKey(fieldName)) {
                    Map<String, String> change = new HashMap<>();
                    String displayName = mapFieldName(fieldName);
                    change.put("field_name", displayName);
                    change.put("old_value", entry.getValue() != null ? entry.getValue().toString() : null);
                    change.put("new_value", null);
                    changes.add(change);
                }
            }
        } else if ("Delete".equals(changeType) && oldState != null) {
            // Delete: Log all fields with proper display names
            for (Map.Entry<String, Object> entry : oldState.entrySet()) {
                Map<String, String> change = new HashMap<>();
                String displayName = mapFieldName(entry.getKey());
                change.put("field_name", displayName);
                change.put("old_value", entry.getValue() != null ? entry.getValue().toString() : null);
                change.put("new_value", null);
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        if (context != null) {
            String facetName = context.get("facetName") != null ? context.get("facetName").toString() : "";
            String workflowName = context.get("name") != null ? context.get("name").toString() : "";
            return String.format("%s - %s", facetName, workflowName);
        }
        return "Unknown";
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
    
    /**
     * Map internal field names to display names
     */
    private String mapFieldName(String fieldName) {
        switch (fieldName) {
            case "name":
                return "Name";
            case "description":
                return "Description";
            case "facetName":
                return "Facet Name";
            case "status":
                return "Status";
            case "workflowDiagram":
                return "Workflow Diagram:";
            default:
                // Capitalize first letter
                return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }
    }
}

