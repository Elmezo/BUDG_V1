package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

/**
 * Committee model class representing the committee table
 */
public class Committee {
    private int id;
    private Integer parentId;
    @SerializedName("isPublic")
    private Integer isPublic;
    private Integer classification;
    private Integer status;
    private String statusName;
    private Integer lifecycle;
    private Integer committeeType;
    private String refNumber;
    private String primaryName;
    private String description;
    private Timestamp createDatetime;
    private Timestamp lastUpdateDatetime;
    private Timestamp deleteDatetime;
    private Integer lastUpdateUserID;
    private Integer createdBy;

    // Default constructor
    public Committee() {}

    // Constructor with all fields
    public Committee(int id, Integer parentId, Integer isPublic, Integer classification, 
                    Integer status, Integer lifecycle, Integer committeeType, 
                    String refNumber, String primaryName, String description, 
                    Timestamp createDatetime, Timestamp lastUpdateDatetime, 
                    Timestamp deleteDatetime, Integer lastUpdateUserID) {
        this.id = id;
        this.parentId = parentId;
        this.isPublic = isPublic;
        this.classification = classification;
        this.status = status;
        this.lifecycle = lifecycle;
        this.committeeType = committeeType;
        this.refNumber = refNumber;
        this.primaryName = primaryName;
        this.description = description;
        this.createDatetime = createDatetime;
        this.lastUpdateDatetime = lastUpdateDatetime;
        this.deleteDatetime = deleteDatetime;
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

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    public Integer getClassification() {
        return classification;
    }

    public void setClassification(Integer classification) {
        this.classification = classification;
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

    public Integer getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Integer lifecycle) {
        this.lifecycle = lifecycle;
    }

    public Integer getCommitteeType() {
        return committeeType;
    }

    public void setCommitteeType(Integer committeeType) {
        this.committeeType = committeeType;
    }

    public String getRefNumber() {
        return refNumber;
    }

    public void setRefNumber(String refNumber) {
        this.refNumber = refNumber;
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

    public Timestamp getDeleteDatetime() {
        return deleteDatetime;
    }

    public void setDeleteDatetime(Timestamp deleteDatetime) {
        this.deleteDatetime = deleteDatetime;
    }

    public Integer getLastUpdateUserID() {
        return lastUpdateUserID;
    }

    public void setLastUpdateUserID(Integer lastUpdateUserID) {
        this.lastUpdateUserID = lastUpdateUserID;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "Committee{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", isPublic=" + isPublic +
                ", classification=" + classification +
                ", status=" + status +
                ", lifecycle=" + lifecycle +
                ", committeeType=" + committeeType +
                ", refNumber='" + refNumber + '\'' +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", createDatetime=" + createDatetime +
                ", lastUpdateDatetime=" + lastUpdateDatetime +
                ", deleteDatetime=" + deleteDatetime +
                ", lastUpdateUserID=" + lastUpdateUserID +
                ", createdBy=" + createdBy +
                '}';
    }
}
