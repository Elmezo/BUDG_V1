package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for workflow_instance table
 */
public class WorkflowInstance {
    private int id;
    private int processDefinitionId;
    private Integer changeRequestId;
    private String currentBpmnNodeId;
    private String status;
    private Timestamp startedAt;
    private Timestamp endedAt;

    // Constructors
    public WorkflowInstance() {
    }

    public WorkflowInstance(int processDefinitionId, Integer changeRequestId) {
        this.processDefinitionId = processDefinitionId;
        this.changeRequestId = changeRequestId;
        this.status = "Enabled";
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

    public Integer getChangeRequestId() {
        return changeRequestId;
    }

    public void setChangeRequestId(Integer changeRequestId) {
        this.changeRequestId = changeRequestId;
    }

    public String getCurrentBpmnNodeId() {
        return currentBpmnNodeId;
    }

    public void setCurrentBpmnNodeId(String currentBpmnNodeId) {
        this.currentBpmnNodeId = currentBpmnNodeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Timestamp startedAt) {
        this.startedAt = startedAt;
    }

    public Timestamp getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Timestamp endedAt) {
        this.endedAt = endedAt;
    }
}
