package com.example.budg_v2.service.activitylog;

import com.example.budg_v2.constants.ActivityLogConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for Ownership Transfer logging
 * Handles Dashboard and Saved Search ownership transfers
 */
public class OwnershipTransferStrategy implements ActivityLogStrategy {
    
    @Override
    public List<Map<String, String>> generateDiff(Map<String, Object> oldState, 
                                                   Map<String, Object> newState, 
                                                   String changeType) {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Ownership Transfer only supports Update operations
        // Field name format: "Type of transfer + name of the object"
        // Example: "Dashboard Ownership Transfer - Report Dashboard"
        
        if (oldState == null || newState == null) {
            return changes;
        }
        
        String transferType = (String) newState.get("transferType"); // "Dashboard" or "Saved Search"
        if (transferType == null) {
            // Try to infer from component or use default
            transferType = "Dashboard";
        }
        
        String objectName = (String) newState.get("objectName");
        String oldOwner = oldState.get("ownerName") != null ? oldState.get("ownerName").toString() : null;
        String newOwner = newState.get("ownerName") != null ? newState.get("ownerName").toString() : null;
        
        if (objectName != null && oldOwner != null && newOwner != null) {
            Map<String, String> change = new HashMap<>();
            // Field name format: "Dashboard Ownership Transfer - Report Dashboard"
            // or "Saved Search Ownership Transfer - Search Name"
            String fieldName;
            if ("Dashboard".equals(transferType)) {
                fieldName = "Dashboard Ownership Transfer - " + objectName;
            } else if ("Saved Search".equals(transferType)) {
                fieldName = "Saved Search Ownership Transfer - " + objectName;
            } else {
                fieldName = transferType + " Ownership Transfer - " + objectName;
            }
            change.put("field_name", fieldName);
            change.put("old_value", oldOwner);
            change.put("new_value", newOwner);
            changes.add(change);
        }
        
        return changes;
    }
    
    @Override
    public String getComponentName(Map<String, Object> context) {
        // Component depends on transfer type
        if (context != null) {
            String transferType = (String) context.get("transferType");
            if ("Dashboard".equals(transferType)) {
                return ActivityLogConstants.COMPONENT_DASHBOARD_OWNERSHIP_TRANSFER;
            } else if ("Saved Search".equals(transferType)) {
                return ActivityLogConstants.COMPONENT_SAVED_SEARCH_OWNERSHIP_TRANSFER;
            }
        }
        return ActivityLogConstants.COMPONENT_DASHBOARD_OWNERSHIP_TRANSFER;
    }
    
    @Override
    public Map<String, Object> captureOldState(Object entity, Map<String, Object> context) {
        Map<String, Object> state = new HashMap<>();
        
        // Extract old owner information
        if (context != null) {
            state.put("ownerName", context.get("oldOwnerName"));
            state.put("ownerId", context.get("oldOwnerId"));
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> captureNewState(Object entity, Map<String, Object> context) {
        Map<String, Object> state = new HashMap<>();
        
        // Extract new owner and transfer information
        if (context != null) {
            state.put("ownerName", context.get("newOwnerName"));
            state.put("ownerId", context.get("newOwnerId"));
            state.put("transferType", context.get("transferType"));
            state.put("objectName", context.get("objectName"));
        }
        
        return state;
    }
}

