package com.example.budg_v2.model;

import java.time.LocalDateTime;

/**
 * Model class for Admin Activity Log
 */
public class ActivityLog {
    private Long id;
    private String setting;
    private String component;
    private Integer userId;
    private String userName;
    private String userEmail;
    private String changeType;
    private LocalDateTime timestamp;
    
    public ActivityLog() {
    }
    
    public ActivityLog(String setting, String component, Integer userId, 
                     String userName, String userEmail, String changeType) {
        this.setting = setting;
        this.component = component;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.changeType = changeType;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
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
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

