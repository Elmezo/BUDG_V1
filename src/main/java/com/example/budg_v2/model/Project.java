package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class Project {
    @SerializedName("id")
    private Integer id;
    
    @SerializedName("parentid")
    private Integer parentId;
    
    @SerializedName("is_public")
    private Integer isPublic;
    
    @SerializedName("rag")
    private Integer rag;
    
    @SerializedName("classification")
    private Integer classification;
    
    @SerializedName("status")
    private Integer status;

    @SerializedName("statusName")
    private String statusName;
    
    @SerializedName("lifecycle_status")
    private Integer lifecycleStatus;
    
    @SerializedName("project_type")
    private Integer projectType;
    
    @SerializedName("refnumber")
    private String refNumber;
    
    @SerializedName("primaryname")
    private String primaryName;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("startdate")
    private Timestamp startDate;
    
    @SerializedName("enddate")
    private Timestamp endDate;
    
    @SerializedName("createdatetime")
    private Timestamp createDateTime;
    
    @SerializedName("lastupdatedatetime")
    private Timestamp lastUpdateDateTime;
    
    @SerializedName("deletedatetime")
    private Timestamp deletedDateTime;
    
    @SerializedName("createdby_id")
    private Integer createdById;
    
    @SerializedName("lastupdateuser_id")
    private Integer lastUpdateUserId;

    // Constructors
    public Project() {}

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getParentId() { return parentId; }
    public void setParentId(Integer parentId) { this.parentId = parentId; }

    public Integer getIsPublic() { return isPublic; }
    public void setIsPublic(Integer isPublic) { this.isPublic = isPublic; }

    public Integer getRag() { return rag; }
    public void setRag(Integer rag) { this.rag = rag; }

    public Integer getClassification() { return classification; }
    public void setClassification(Integer classification) { this.classification = classification; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }

    public Integer getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(Integer lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    public Integer getProjectType() { return projectType; }
    public void setProjectType(Integer projectType) { this.projectType = projectType; }

    public String getRefNumber() { return refNumber; }
    public void setRefNumber(String refNumber) { this.refNumber = refNumber; }

    public String getPrimaryName() { return primaryName; }
    public void setPrimaryName(String primaryName) { this.primaryName = primaryName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getStartDate() { return startDate; }
    public void setStartDate(Timestamp startDate) { this.startDate = startDate; }

    public Timestamp getEndDate() { return endDate; }
    public void setEndDate(Timestamp endDate) { this.endDate = endDate; }

    public Timestamp getCreateDateTime() { return createDateTime; }
    public void setCreateDateTime(Timestamp createDateTime) { this.createDateTime = createDateTime; }

    public Timestamp getLastUpdateDateTime() { return lastUpdateDateTime; }
    public void setLastUpdateDateTime(Timestamp lastUpdateDateTime) { this.lastUpdateDateTime = lastUpdateDateTime; }

    public Timestamp getDeletedDateTime() { return deletedDateTime; }
    public void setDeletedDateTime(Timestamp deletedDateTime) { this.deletedDateTime = deletedDateTime; }

    public Integer getCreatedById() { return createdById; }
    public void setCreatedById(Integer createdById) { this.createdById = createdById; }

    public Integer getLastUpdateUserId() { return lastUpdateUserId; }
    public void setLastUpdateUserId(Integer lastUpdateUserId) { this.lastUpdateUserId = lastUpdateUserId; }

    @Override
    public String toString() {
        return "Project{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", isPublic=" + isPublic +
                ", rag=" + rag +
                ", classification=" + classification +
                ", status=" + status +
                ", lifecycleStatus=" + lifecycleStatus +
                ", projectType=" + projectType +
                ", refNumber='" + refNumber + '\'' +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", createDateTime=" + createDateTime +
                ", lastUpdateDateTime=" + lastUpdateDateTime +
                ", deletedDateTime=" + deletedDateTime +
                ", createdById=" + createdById +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
