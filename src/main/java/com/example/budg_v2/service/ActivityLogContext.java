package com.example.budg_v2.service;

import java.util.Map;

/**
 * Context object for activity logging
 * Contains all information needed to log an admin activity
 */
public class ActivityLogContext {
    private String setting;
    private String component;
    private Integer userId;
    private String userName;
    private String userEmail;
    private String changeType;
    private Map<String, Object> oldState;
    private Map<String, Object> newState;
    private Map<String, Object> contextMap; // Additional context for strategy (e.g., facetName, roleName)
    private Object entity; // Optional: the entity being modified
    
    public ActivityLogContext() {
    }
    
    public ActivityLogContext(String setting, String component, Integer userId, 
                             String userName, String userEmail, String changeType) {
        this.setting = setting;
        this.component = component;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.changeType = changeType;
    }
    
    // Getters and Setters
    public String getSetting() {
        return setting;
    }
    
    public void setSetting(String setting) {
        this.setting = setting;
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }
    
    public Integer getUserId() {
        return userId;
    }
    
    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public String getUserEmail() {
        return userEmail;
    }
    
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
    
    public Map<String, Object> getOldState() {
        return oldState;
    }
    
    public void setOldState(Map<String, Object> oldState) {
        this.oldState = oldState;
    }
    
    public Map<String, Object> getNewState() {
        return newState;
    }
    
    public void setNewState(Map<String, Object> newState) {
        this.newState = newState;
    }
    
    public Object getEntity() {
        return entity;
    }
    
    public void setEntity(Object entity) {
        this.entity = entity;
    }
    
    public Map<String, Object> getContextMap() {
        return contextMap;
    }
    
    public void setContextMap(Map<String, Object> contextMap) {
        this.contextMap = contextMap;
    }
}

