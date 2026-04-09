package com.example.budg_v2.model;

import java.time.LocalDateTime;

/**
 * Capability Model
 * Represents the capability table structure
 */
public class Capability {
    private Integer id;
    private Integer parentId;
    private Integer isPublic;
    private Integer classification;
    private Integer status;
    private String statusName;
    private Integer lifecycle;
    private Integer capabilityType;
    private String refNumber;
    private String primaryName;
    private String description;
    private LocalDateTime createDatetime;
    private LocalDateTime lastUpdateDatetime;
    private LocalDateTime deletedDatetime;
    private Integer lastUpdateUserId;

    // Constructors
    public Capability() {}

    public Capability(String primaryName, String description) {
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

    public Integer getCapabilityType() {
        return capabilityType;
    }

    public void setCapabilityType(Integer capabilityType) {
        this.capabilityType = capabilityType;
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

    public LocalDateTime getCreateDatetime() {
        return createDatetime;
    }

    public void setCreateDatetime(LocalDateTime createDatetime) {
        this.createDatetime = createDatetime;
    }

    public LocalDateTime getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }

    public void setLastUpdateDatetime(LocalDateTime lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }

    public LocalDateTime getDeletedDatetime() {
        return deletedDatetime;
    }

    public void setDeletedDatetime(LocalDateTime deletedDatetime) {
        this.deletedDatetime = deletedDatetime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "Capability{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", parentId=" + parentId +
                ", status=" + status +
                ", lifecycle=" + lifecycle +
                ", classification=" + classification +
                ", capabilityType=" + capabilityType +
                '}';
    }
}