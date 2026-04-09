package com.example.budg_v2.model;

/**
 * Model class for Admin Activity Log Detail
 * Represents a single field change within an activity log
 */
public class ActivityLogDetail {
    private Long id;
    private Long activityLogId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    
    public ActivityLogDetail() {
    }
    
    public ActivityLogDetail(Long activityLogId, String fieldName, String oldValue, String newValue) {
        this.activityLogId = activityLogId;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getActivityLogId() {
        return activityLogId;
    }
    
    public void setActivityLogId(Long activityLogId) {
        this.activityLogId = activityLogId;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public String getOldValue() {
        return oldValue;
    }
    
    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }
    
    public String getNewValue() {
        return newValue;
    }
    
    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }
}

