package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for changerequest_workflow_crtype table
 */
public class WorkflowMapping {
    private int id;
    private int processDefinitionId;
    private String crType;
    private int entityId;
    private int lastUserChange;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Constructors
    public WorkflowMapping() {
    }

    public WorkflowMapping(int processDefinitionId, String crType, int entityId) {
        this.processDefinitionId = processDefinitionId;
        this.crType = crType;
        this.entityId = entityId;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(int processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public String getCrType() {
        return crType;
    }

    public void setCrType(String crType) {
        this.crType = crType;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public int getLastUserChange() {
        return lastUserChange;
    }

    public void setLastUserChange(int lastUserChange) {
        this.lastUserChange = lastUserChange;
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
}
