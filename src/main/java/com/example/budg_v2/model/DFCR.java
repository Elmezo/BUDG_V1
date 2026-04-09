package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for Default Change Request settings (DF_CR table)
 */
public class DFCR {
    private int id;
    private int facetId;                    // FK to module.id
    private Integer statusId;               // FK to status.ID
    private String lifecycleTable;          // Combined lifecycle in format "facet_id" (e.g., "glossary_2", "dataset_3")
    private Integer crTypeId;               // FK to changerequest_type.ID
    private Integer crUrgencyId;            // FK to changerequest_urgency.ID
    private Integer crSeverityId;           // FK to changerequest_severity.ID
    private Integer workflowCreateId;       // FK to process_definition.ID for creating objects
    private Integer workflowEditId;         // FK to process_definition.ID for editing objects
    private Integer processDefinitionId;    // Legacy single workflow column (for backward compatibility)
    private Integer canCreate;              // Flag column (legacy compatibility)
    private Integer canRead;                // Flag column (legacy compatibility)
    private String crSystem;                // Native, ServiceNow, JIRA
    private boolean workflowApprovalEnabled;
    private boolean workflowForTypesEnabled;
    private boolean adminWorkflowBypass;    // If true, admins bypass workflow
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Transient fields for display names (not stored in DB)
    private transient String facetName;
    private transient String statusName;
    private transient String lifecycleName;
    private transient String crTypeName;
    private transient String crUrgencyName;
    private transient String crSeverityName;
    private transient String workflowCreateName;
    private transient String workflowEditName;
    private transient String processDefinitionName;

    public DFCR() {
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFacetId() {
        return facetId;
    }

    public void setFacetId(int facetId) {
        this.facetId = facetId;
    }

    public Integer getStatusId() {
        return statusId;
    }

    public void setStatusId(Integer statusId) {
        this.statusId = statusId;
    }

    public String getLifecycleTable() {
        return lifecycleTable;
    }

    public void setLifecycleTable(String lifecycleTable) {
        this.lifecycleTable = lifecycleTable;
    }

    // Legacy getter/setter for backward compatibility - extracts ID from combined format
    public Integer getLifecycleId() {
        if (lifecycleTable != null && lifecycleTable.contains("_")) {
            try {
                String[] parts = lifecycleTable.split("_");
                return Integer.parseInt(parts[parts.length - 1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public void setLifecycleId(Integer lifecycleId) {
        // This method is kept for compatibility but doesn't directly set anything
        // Use setLifecycleTable with combined format instead
    }

    public Integer getCrTypeId() {
        return crTypeId;
    }

    public void setCrTypeId(Integer crTypeId) {
        this.crTypeId = crTypeId;
    }

    public Integer getCrUrgencyId() {
        return crUrgencyId;
    }

    public void setCrUrgencyId(Integer crUrgencyId) {
        this.crUrgencyId = crUrgencyId;
    }

    public Integer getCrSeverityId() {
        return crSeverityId;
    }

    public void setCrSeverityId(Integer crSeverityId) {
        this.crSeverityId = crSeverityId;
    }

    public Integer getWorkflowCreateId() {
        return workflowCreateId;
    }

    public void setWorkflowCreateId(Integer workflowCreateId) {
        this.workflowCreateId = workflowCreateId;
    }

    public Integer getWorkflowEditId() {
        return workflowEditId;
    }

    public void setWorkflowEditId(Integer workflowEditId) {
        this.workflowEditId = workflowEditId;
    }

    public Integer getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(Integer processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public Integer getCanCreate() {
        return canCreate;
    }

    public void setCanCreate(Integer canCreate) {
        this.canCreate = canCreate;
    }

    public Integer getCanRead() {
        return canRead;
    }

    public void setCanRead(Integer canRead) {
        this.canRead = canRead;
    }

    public String getCrSystem() {
        return crSystem;
    }

    public void setCrSystem(String crSystem) {
        this.crSystem = crSystem;
    }

    public boolean isWorkflowApprovalEnabled() {
        return workflowApprovalEnabled;
    }

    public void setWorkflowApprovalEnabled(boolean workflowApprovalEnabled) {
        this.workflowApprovalEnabled = workflowApprovalEnabled;
    }

    public boolean isWorkflowForTypesEnabled() {
        return workflowForTypesEnabled;
    }

    public void setWorkflowForTypesEnabled(boolean workflowForTypesEnabled) {
        this.workflowForTypesEnabled = workflowForTypesEnabled;
    }

    public boolean isAdminWorkflowBypass() {
        return adminWorkflowBypass;
    }

    public void setAdminWorkflowBypass(boolean adminWorkflowBypass) {
        this.adminWorkflowBypass = adminWorkflowBypass;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Transient getters and setters

    public String getFacetName() {
        return facetName;
    }

    public void setFacetName(String facetName) {
        this.facetName = facetName;
    }

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

    public String getCrTypeName() {
        return crTypeName;
    }

    public void setCrTypeName(String crTypeName) {
        this.crTypeName = crTypeName;
    }

    public String getCrUrgencyName() {
        return crUrgencyName;
    }

    public void setCrUrgencyName(String crUrgencyName) {
        this.crUrgencyName = crUrgencyName;
    }

    public String getCrSeverityName() {
        return crSeverityName;
    }

    public void setCrSeverityName(String crSeverityName) {
        this.crSeverityName = crSeverityName;
    }

    public String getWorkflowCreateName() {
        return workflowCreateName;
    }

    public void setWorkflowCreateName(String workflowCreateName) {
        this.workflowCreateName = workflowCreateName;
    }

    public String getWorkflowEditName() {
        return workflowEditName;
    }

    public void setWorkflowEditName(String workflowEditName) {
        this.workflowEditName = workflowEditName;
    }

    public String getProcessDefinitionName() {
        return processDefinitionName;
    }

    public void setProcessDefinitionName(String processDefinitionName) {
        this.processDefinitionName = processDefinitionName;
    }

    @Override
    public String toString() {
        return "DFCR{" +
                "id=" + id +
                ", facetId=" + facetId +
                ", statusId=" + statusId +
                ", lifecycleTable='" + lifecycleTable + '\'' +
                ", lifecycleId=" + getLifecycleId() +
                ", crTypeId=" + crTypeId +
                ", crUrgencyId=" + crUrgencyId +
                ", crSeverityId=" + crSeverityId +
                ", workflowCreateId=" + workflowCreateId +
                ", workflowEditId=" + workflowEditId +
                ", crSystem='" + crSystem + '\'' +
                ", workflowApprovalEnabled=" + workflowApprovalEnabled +
                ", workflowForTypesEnabled=" + workflowForTypesEnabled +
                ", adminWorkflowBypass=" + adminWorkflowBypass +
                '}';
    }
}

