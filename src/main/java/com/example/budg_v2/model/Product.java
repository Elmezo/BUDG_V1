package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class Product {
    @SerializedName("id")
    private Integer id;
    
    @SerializedName("parentid")
    private Integer parentId;
    
    @SerializedName("is_public")
    private Integer isPublic;
    
    @SerializedName("status")
    private Integer status;

    @SerializedName("statusName")
    private String statusName;
    
    @SerializedName("lifecycle_status")
    private Integer lifecycleStatus;
    
    @SerializedName("primaryname")
    private String primaryName;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("refnumber")
    private String refNumber;
    
    @SerializedName("longname")
    private String longName;
    
    @SerializedName("startdate")
    private Timestamp startDate;
    
    @SerializedName("enddate")
    private Timestamp endDate;
    
    @SerializedName("createdatetime")
    private Timestamp createdDatetime;
    
    @SerializedName("lastupdateuser_id")
    private Integer lastUpdateUserId;
    
    @SerializedName("createdby_id")
    private Integer createdById;
    
    @SerializedName("last_updated_datetime")
    private Timestamp lastUpdatedDatetime;

    // Constructors
    public Product() {}

    public Product(String primaryName, String description) {
        this.primaryName = primaryName;
        this.description = description;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRefNumber() {
        return refNumber;
    }

    public void setRefNumber(String refNumber) {
        this.refNumber = refNumber;
    }

    public String getLongName() {
        return longName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public Timestamp getCreatedDatetime() {
        return createdDatetime;
    }

    public void setCreatedDatetime(Timestamp createdDatetime) {
        this.createdDatetime = createdDatetime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    public Integer getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Integer createdById) {
        this.createdById = createdById;
    }

    public Timestamp getLastUpdatedDatetime() {
        return lastUpdatedDatetime;
    }

    public void setLastUpdatedDatetime(Timestamp lastUpdatedDatetime) {
        this.lastUpdatedDatetime = lastUpdatedDatetime;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", isPublic=" + isPublic +
                ", status=" + status +
                ", lifecycleStatus=" + lifecycleStatus +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", refNumber='" + refNumber + '\'' +
                ", longName='" + longName + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", createdDatetime=" + createdDatetime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                ", lastUpdatedDatetime=" + lastUpdatedDatetime +
                '}';
    }
}
