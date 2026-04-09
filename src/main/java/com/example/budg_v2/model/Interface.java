package com.example.budg_v2.model;

import java.sql.Timestamp;

public class Interface {
    private Integer id;
    private String name;
    private String refNumber;
    private String description;
    private Integer classificationId;
    private Integer lifecycleId;
    private Integer statusId;
    private Integer sourceSystemId;
    private Integer targetSystemId;
    private Integer automationId;
    private Integer frequencyId;
    private Integer transferMethodId;
    private Integer transferFormatId;
    private String assetId;
    private String synchronisationControl;
    private Integer isPublic;
    private Timestamp createdDatetime;
    private Timestamp lastUpdatedtime;
    private Timestamp deletedDatetime;
    private Integer createdById;
    private Integer lastUpdateUserId;

    // Default constructor
    public Interface() {}

    // Constructor with all fields
    public Interface(Integer id, String name, String refNumber, String description,
                    Integer classificationId, Integer lifecycleId, Integer statusId,
                    Integer sourceSystemId, Integer targetSystemId, Integer automationId,
                    Integer frequencyId, Integer transferMethodId, Integer transferFormatId,
                    String assetId, String synchronisationControl, Integer isPublic,
                    Timestamp createdDatetime, Timestamp lastUpdatedtime, Timestamp deletedDatetime,
                    Integer createdById, Integer lastUpdateUserId) {
        this.id = id;
        this.name = name;
        this.refNumber = refNumber;
        this.description = description;
        this.classificationId = classificationId;
        this.lifecycleId = lifecycleId;
        this.statusId = statusId;
        this.sourceSystemId = sourceSystemId;
        this.targetSystemId = targetSystemId;
        this.automationId = automationId;
        this.frequencyId = frequencyId;
        this.transferMethodId = transferMethodId;
        this.transferFormatId = transferFormatId;
        this.assetId = assetId;
        this.synchronisationControl = synchronisationControl;
        this.isPublic = isPublic;
        this.createdDatetime = createdDatetime;
        this.lastUpdatedtime = lastUpdatedtime;
        this.deletedDatetime = deletedDatetime;
        this.createdById = createdById;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRefNumber() { return refNumber; }
    public void setRefNumber(String refNumber) { this.refNumber = refNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getClassificationId() { return classificationId; }
    public void setClassificationId(Integer classificationId) { this.classificationId = classificationId; }

    public Integer getLifecycleId() { return lifecycleId; }
    public void setLifecycleId(Integer lifecycleId) { this.lifecycleId = lifecycleId; }

    public Integer getStatusId() { return statusId; }
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    public Integer getSourceSystemId() { return sourceSystemId; }
    public void setSourceSystemId(Integer sourceSystemId) { this.sourceSystemId = sourceSystemId; }

    public Integer getTargetSystemId() { return targetSystemId; }
    public void setTargetSystemId(Integer targetSystemId) { this.targetSystemId = targetSystemId; }

    public Integer getAutomationId() { return automationId; }
    public void setAutomationId(Integer automationId) { this.automationId = automationId; }

    public Integer getFrequencyId() { return frequencyId; }
    public void setFrequencyId(Integer frequencyId) { this.frequencyId = frequencyId; }

    public Integer getTransferMethodId() { return transferMethodId; }
    public void setTransferMethodId(Integer transferMethodId) { this.transferMethodId = transferMethodId; }

    public Integer getTransferFormatId() { return transferFormatId; }
    public void setTransferFormatId(Integer transferFormatId) { this.transferFormatId = transferFormatId; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getSynchronisationControl() { return synchronisationControl; }
    public void setSynchronisationControl(String synchronisationControl) { this.synchronisationControl = synchronisationControl; }

    public Integer getIsPublic() { return isPublic; }
    public void setIsPublic(Integer isPublic) { this.isPublic = isPublic; }

    public Timestamp getCreatedDatetime() { return createdDatetime; }
    public void setCreatedDatetime(Timestamp createdDatetime) { this.createdDatetime = createdDatetime; }

    public Timestamp getLastUpdatedtime() { return lastUpdatedtime; }
    public void setLastUpdatedtime(Timestamp lastUpdatedtime) { this.lastUpdatedtime = lastUpdatedtime; }

    public Timestamp getDeletedDatetime() { return deletedDatetime; }
    public void setDeletedDatetime(Timestamp deletedDatetime) { this.deletedDatetime = deletedDatetime; }

    public Integer getCreatedById() { return createdById; }
    public void setCreatedById(Integer createdById) { this.createdById = createdById; }

    public Integer getLastUpdateUserId() { return lastUpdateUserId; }
    public void setLastUpdateUserId(Integer lastUpdateUserId) { this.lastUpdateUserId = lastUpdateUserId; }

    @Override
    public String toString() {
        return "Interface{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", refNumber='" + refNumber + '\'' +
                ", description='" + description + '\'' +
                ", classificationId=" + classificationId +
                ", lifecycleId=" + lifecycleId +
                ", statusId=" + statusId +
                ", sourceSystemId=" + sourceSystemId +
                ", targetSystemId=" + targetSystemId +
                ", automationId=" + automationId +
                ", frequencyId=" + frequencyId +
                ", transferMethodId=" + transferMethodId +
                ", transferFormatId=" + transferFormatId +
                ", assetId='" + assetId + '\'' +
                ", synchronisationControl='" + synchronisationControl + '\'' +
                ", isPublic=" + isPublic +
                ", createdDatetime=" + createdDatetime +
                ", lastUpdatedtime=" + lastUpdatedtime +
                ", deletedDatetime=" + deletedDatetime +
                ", createdById=" + createdById +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
