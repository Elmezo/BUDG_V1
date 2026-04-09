package com.example.budg_v2.model;

import java.time.LocalDateTime;

/**
 * Model class for changerequest table
 */
public class ChangeRequest {
    private Integer id;
    private String primaryName;
    private Integer parentId;
    private String reference;
    private String summary;
    private Integer lastUserChange;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Integer crStatusId;
    private Integer crTypeId;
    private Integer crSeverityId;
    private Integer crUrgencyId;
    private Integer processInstanceId;
    private Integer processDefinitionId; // Default workflow process definition ID
    private Integer estimatedBenefitId;
    private Integer estimatedCostId;
    private Integer visibility;
    private Boolean mandatoryWorkflow;
    private String delta; // JSON field
    
    // Additional fields for view display
    private String typeName;
    private String statusName;
    private String severityName;
    private String urgencyName;
    private Float estimatedBenefit;
    private String estimatedBenefitCurrency;
    private Float estimatedCost;
    private String estimatedCostCurrency;
    private String createdByName;
    private String lastUserChangeName;
    private Boolean isBlocked; // Transient: true if there are older incomplete CRs for the same object

    // Default constructor
    public ChangeRequest() {}

    // Constructor with required fields
    public ChangeRequest(String primaryName, String summary, Integer crTypeId, 
                        Integer crSeverityId, Integer crUrgencyId, Integer createdBy) {
        this.primaryName = primaryName;
        this.summary = summary;
        this.crTypeId = crTypeId;
        this.crSeverityId = crSeverityId;
        this.crUrgencyId = crUrgencyId;
        this.createdBy = createdBy;
        this.lastUserChange = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getLastUserChange() {
        return lastUserChange;
    }

    public void setLastUserChange(Integer lastUserChange) {
        this.lastUserChange = lastUserChange;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Integer getCrStatusId() {
        return crStatusId;
    }

    public void setCrStatusId(Integer crStatusId) {
        this.crStatusId = crStatusId;
    }

    public Integer getCrTypeId() {
        return crTypeId;
    }

    public void setCrTypeId(Integer crTypeId) {
        this.crTypeId = crTypeId;
    }

    public Integer getCrSeverityId() {
        return crSeverityId;
    }

    public void setCrSeverityId(Integer crSeverityId) {
        this.crSeverityId = crSeverityId;
    }

    public Integer getCrUrgencyId() {
        return crUrgencyId;
    }

    public void setCrUrgencyId(Integer crUrgencyId) {
        this.crUrgencyId = crUrgencyId;
    }

    public Integer getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(Integer processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public Integer getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(Integer processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public Integer getEstimatedBenefitId() {
        return estimatedBenefitId;
    }

    public void setEstimatedBenefitId(Integer estimatedBenefitId) {
        this.estimatedBenefitId = estimatedBenefitId;
    }

    public Integer getEstimatedCostId() {
        return estimatedCostId;
    }

    public void setEstimatedCostId(Integer estimatedCostId) {
        this.estimatedCostId = estimatedCostId;
    }

    public Integer getVisibility() {
        return visibility;
    }

    public void setVisibility(Integer visibility) {
        this.visibility = visibility;
    }

    public Boolean getMandatoryWorkflow() {
        return mandatoryWorkflow;
    }

    public void setMandatoryWorkflow(Boolean mandatoryWorkflow) {
        this.mandatoryWorkflow = mandatoryWorkflow;
    }

    public String getDelta() {
        return delta;
    }

    public void setDelta(String delta) {
        this.delta = delta;
    }

    @Override
    public String toString() {
        return "ChangeRequest{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", reference='" + reference + '\'' +
                ", crTypeId=" + crTypeId +
                ", crSeverityId=" + crSeverityId +
                ", crUrgencyId=" + crUrgencyId +
                ", createdBy=" + createdBy +
                ", createdAt=" + createdAt +
                '}';
    }

    // Getters and setters for additional fields
    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public String getSeverityName() {
        return severityName;
    }

    public void setSeverityName(String severityName) {
        this.severityName = severityName;
    }

    public String getUrgencyName() {
        return urgencyName;
    }

    public void setUrgencyName(String urgencyName) {
        this.urgencyName = urgencyName;
    }

    public Float getEstimatedBenefit() {
        return estimatedBenefit;
    }

    public void setEstimatedBenefit(Float estimatedBenefit) {
        this.estimatedBenefit = estimatedBenefit;
    }

    public String getEstimatedBenefitCurrency() {
        return estimatedBenefitCurrency;
    }

    public void setEstimatedBenefitCurrency(String estimatedBenefitCurrency) {
        this.estimatedBenefitCurrency = estimatedBenefitCurrency;
    }

    public Float getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(Float estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public String getEstimatedCostCurrency() {
        return estimatedCostCurrency;
    }

    public void setEstimatedCostCurrency(String estimatedCostCurrency) {
        this.estimatedCostCurrency = estimatedCostCurrency;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public String getLastUserChangeName() {
        return lastUserChangeName;
    }

    public void setLastUserChangeName(String lastUserChangeName) {
        this.lastUserChangeName = lastUserChangeName;
    }
    
    public Boolean getIsBlocked() {
        return isBlocked;
    }
    
    public void setIsBlocked(Boolean isBlocked) {
        this.isBlocked = isBlocked;
    }
}
