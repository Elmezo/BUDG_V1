package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

public class Policy {
    private int id;
    
    @SerializedName(value = "parentId", alternate = {"ParentID", "parentID"})
    private Integer parentId;
    
    @SerializedName("isPublic")
    private Integer isPublic;
    
    @SerializedName(value = "status", alternate = {"Status"})
    private Integer status;

    @SerializedName("statusName")
    private String statusName;
    
    @SerializedName(value = "lifecycleStatus", alternate = {"Lifecycle_Status"})
    private Integer lifecycleStatus;
    
    @SerializedName(value = "policyType", alternate = {"Policy_Type"})
    private Integer policyType;
    
    @SerializedName(value = "primaryName", alternate = {"PrimaryName"})
    private String primaryName;
    
    @SerializedName(value = "refNumber", alternate = {"RefNumber"})
    private String refNumber;
    
    @SerializedName(value = "description", alternate = {"Description"})
    private String description;
    
    @SerializedName(value = "effectiveDate", alternate = {"EffectiveDate"})
    private String effectiveDate;
    
    @SerializedName(value = "endDate", alternate = {"EndDate"})
    private String endDate;
    
    @SerializedName(value = "internal", alternate = {"Internal"})
    private Integer internal;
    
    @SerializedName(value = "url", alternate = {"URL"})
    private String url;
    
    private String createDatetime;
    private String lastUpdateDatetime;
    
    @SerializedName("createdById")
    private Integer createdById;
    
    @SerializedName("lastUpdateUserId")
    private Integer lastUpdateUserId;
    
    private String createdByName;
    private String lastUpdatedByName;

    // Segment fields (for segment-based access control and display)
    private Integer segmentId;
    private String segmentName;
    
    // Parent name (for display purposes)
    private String parentName;

    // Constructors
    public Policy() {}

    public Policy(String primaryName, String refNumber, String description, String url, Integer internal) {
        this.primaryName = primaryName;
        this.refNumber = refNumber;
        this.description = description;
        this.url = url;
        this.internal = internal;
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

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public Integer getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(Integer lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Integer getPolicyType() {
        return policyType;
    }

    public void setPolicyType(Integer policyType) {
        this.policyType = policyType;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public String getRefNumber() {
        return refNumber;
    }

    public void setRefNumber(String refNumber) {
        this.refNumber = refNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Integer getInternal() {
        return internal;
    }

    public void setInternal(Integer internal) {
        this.internal = internal;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCreateDatetime() {
        return createDatetime;
    }

    public void setCreateDatetime(String createDatetime) {
        this.createDatetime = createDatetime;
    }

    public String getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }

    public void setLastUpdateDatetime(String lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }

    public Integer getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Integer createdById) {
        this.createdById = createdById;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public String getLastUpdatedByName() {
        return lastUpdatedByName;
    }

    public void setLastUpdatedByName(String lastUpdatedByName) {
        this.lastUpdatedByName = lastUpdatedByName;
    }

    public Integer getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(Integer segmentId) {
        this.segmentId = segmentId;
    }

    public String getSegmentName() {
        return segmentName;
    }

    public void setSegmentName(String segmentName) {
        this.segmentName = segmentName;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    @Override
    public String toString() {
        return "Policy{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", isPublic=" + isPublic +
                ", status=" + status +
                ", lifecycleStatus=" + lifecycleStatus +
                ", policyType=" + policyType +
                ", primaryName='" + primaryName + '\'' +
                ", refNumber='" + refNumber + '\'' +
                ", description='" + description + '\'' +
                ", effectiveDate='" + effectiveDate + '\'' +
                ", endDate='" + endDate + '\'' +
                ", internal=" + internal +
                ", url='" + url + '\'' +
                ", createDatetime='" + createDatetime + '\'' +
                ", lastUpdateDatetime='" + lastUpdateDatetime + '\'' +
                ", createdById=" + createdById +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}