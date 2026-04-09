package com.example.budg_v2.service;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

/**
 * Model class representing an Organization Unit
 */
public class OrgUnit {
    
    @SerializedName("id")
    private Integer id;
    
    @SerializedName("reference")
    private String reference;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("parent_id")
    private Integer parentId;
    
    @SerializedName("status_id")
    private Integer statusId;
    
    @SerializedName("created_date")
    private Timestamp createdDate;
    
    @SerializedName("last_updated_date")
    private Timestamp lastUpdatedDate;
    
    @SerializedName("deleted_date")
    private Timestamp deletedDate;
    
    @SerializedName("lastupdateuser_ID")
    private Integer lastUpdateUserId;
    
    // Default constructor
    public OrgUnit() {}
    
    // Constructor with all fields
    public OrgUnit(Integer id, String reference, String name, String description, 
                   Integer parentId, Integer statusId, Timestamp createdDate, Timestamp lastUpdatedDate, 
                   Timestamp deletedDate, Integer lastUpdateUserId) {
        this.id = id;
        this.reference = reference;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.statusId = statusId;
        this.createdDate = createdDate;
        this.lastUpdatedDate = lastUpdatedDate;
        this.deletedDate = deletedDate;
        this.lastUpdateUserId = lastUpdateUserId;
    }
    
    // Constructor for creating new units
    public OrgUnit(String reference, String name, String description, Integer parentId, Integer statusId) {
        this.reference = reference;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.statusId = statusId;
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getReference() {
        return reference;
    }
    
    public void setReference(String reference) {
        this.reference = reference;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Integer getParentId() {
        return parentId;
    }
    
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
    
    public Integer getStatusId() {
        return statusId;
    }
    
    public void setStatusId(Integer statusId) {
        this.statusId = statusId;
    }
    
    public Timestamp getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(Timestamp createdDate) {
        this.createdDate = createdDate;
    }
    
    public Timestamp getLastUpdatedDate() {
        return lastUpdatedDate;
    }
    
    public void setLastUpdatedDate(Timestamp lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }
    
    public Timestamp getDeletedDate() {
        return deletedDate;
    }
    
    public void setDeletedDate(Timestamp deletedDate) {
        this.deletedDate = deletedDate;
    }
    
    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }
    
    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }
    
    @Override
    public String toString() {
        return "OrgUnit{" +
                "id=" + id +
                ", reference='" + reference + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", parentId=" + parentId +
                ", statusId=" + statusId +
                ", createdDate=" + createdDate +
                ", lastUpdatedDate=" + lastUpdatedDate +
                ", deletedDate=" + deletedDate +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
