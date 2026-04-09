package com.example.budg_v2.model;

import java.time.LocalDateTime;

/**
 * Filter criteria for querying activity logs
 */
public class ActivityLogFilter {
    private String setting;
    private String component;
    private String changeType;
    private Integer userId;
    private String userName;
    private String userEmail;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private String searchDetails; // Search term for details
    private Integer offset;
    private Integer limit;
    private String sortBy;
    private String sortOrder; // ASC or DESC
    
    public ActivityLogFilter() {
        this.offset = 0;
        this.limit = 50;
        this.sortBy = "timestamp";
        this.sortOrder = "DESC";
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
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
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
    
    public LocalDateTime getFromDate() {
        return fromDate;
    }
    
    public void setFromDate(LocalDateTime fromDate) {
        this.fromDate = fromDate;
    }
    
    public LocalDateTime getToDate() {
        return toDate;
    }
    
    public void setToDate(LocalDateTime toDate) {
        this.toDate = toDate;
    }
    
    public String getSearchDetails() {
        return searchDetails;
    }
    
    public void setSearchDetails(String searchDetails) {
        this.searchDetails = searchDetails;
    }
    
    public Integer getOffset() {
        return offset;
    }
    
    public void setOffset(Integer offset) {
        this.offset = offset;
    }
    
    public Integer getLimit() {
        return limit;
    }
    
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
    
    public String getSortBy() {
        return sortBy;
    }
    
    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }
    
    public String getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}

