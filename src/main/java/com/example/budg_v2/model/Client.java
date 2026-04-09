package com.example.budg_v2.model;

import java.sql.Timestamp;

public class Client {
    
    private int id;
    private Integer parentId;
    private Integer lifecycle;
    private Integer status;
    private Integer isPublic;
    private String primaryName;
    private String longName;
    private String description;
    private Timestamp createDatetime;
    private Timestamp lastUpdateDatetime;
    private Integer lastUpdateUserID;
    
    // Related entity names for display
    private String statusName;
    private String lifecycleName;
    private String viewingName;
    private String lastUpdatedByName;
    
    public Client() {}
    
    public Client(int id, Integer parentId, Integer lifecycle, Integer status, Integer isPublic,
                  String primaryName, String longName, String description, Timestamp createDatetime,
                  Timestamp lastUpdateDatetime, Integer lastUpdateUserID) {
        this.id = id;
        this.parentId = parentId;
        this.lifecycle = lifecycle;
        this.status = status;
        this.isPublic = isPublic;
        this.primaryName = primaryName;
        this.longName = longName;
        this.description = description;
        this.createDatetime = createDatetime;
        this.lastUpdateDatetime = lastUpdateDatetime;
        this.lastUpdateUserID = lastUpdateUserID;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public Integer getParentId() {
        return parentId;
    }
    
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
    
    public Integer getLifecycle() {
        return lifecycle;
    }
    
    public void setLifecycle(Integer lifecycle) {
        this.lifecycle = lifecycle;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public Integer getIsPublic() {
        return isPublic;
    }
    
    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }
    
    public String getPrimaryName() {
        return primaryName;
    }
    
    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }
    
    public String getLongName() {
        return longName;
    }
    
    public void setLongName(String longName) {
        this.longName = longName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Timestamp getCreateDatetime() {
        return createDatetime;
    }
    
    public void setCreateDatetime(Timestamp createDatetime) {
        this.createDatetime = createDatetime;
    }
    
    public Timestamp getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }
    
    public void setLastUpdateDatetime(Timestamp lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }
    
    public Integer getLastUpdateUserID() {
        return lastUpdateUserID;
    }
    
    public void setLastUpdateUserID(Integer lastUpdateUserID) {
        this.lastUpdateUserID = lastUpdateUserID;
    }
    
    // Related entity getters and setters
    public String getStatusName() {
        return statusName;
    }
    
    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }
    
    public String getLifecycleName() {
        return lifecycleName;
    }
    
    public void setLifecycleName(String lifecycleName) {
        this.lifecycleName = lifecycleName;
    }
    
    public String getViewingName() {
        return viewingName;
    }
    
    public void setViewingName(String viewingName) {
        this.viewingName = viewingName;
    }
    
    public String getLastUpdatedByName() {
        return lastUpdatedByName;
    }
    
    public void setLastUpdatedByName(String lastUpdatedByName) {
        this.lastUpdatedByName = lastUpdatedByName;
    }
    
    @Override
    public String toString() {
        return "Client{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", lifecycle=" + lifecycle +
                ", status=" + status +
                ", isPublic=" + isPublic +
                ", primaryName='" + primaryName + '\'' +
                ", longName='" + longName + '\'' +
                ", description='" + description + '\'' +
                ", createDatetime=" + createDatetime +
                ", lastUpdateDatetime=" + lastUpdateDatetime +
                ", lastUpdateUserID=" + lastUpdateUserID +
                ", statusName='" + statusName + '\'' +
                ", lifecycleName='" + lifecycleName + '\'' +
                ", viewingName='" + viewingName + '\'' +
                ", lastUpdatedByName='" + lastUpdatedByName + '\'' +
                '}';
    }
}
