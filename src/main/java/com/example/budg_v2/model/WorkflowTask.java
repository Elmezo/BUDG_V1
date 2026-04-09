package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for workflow_instance_task table
 */
public class WorkflowTask {
    private int id;
    private int workflowInstanceId;
    private String bpmnNodeId;
    private String parentGatewayId;
    private String name;
    private String status;
    private Integer assignedTo;
    private Timestamp dueDate;
    private Timestamp startedAt;
    private Timestamp completedAt;
    private String roleName;
    private Timestamp assignedAt;
    private Integer completedBy;
    private String decision;
    private Timestamp dueAt;
    private Boolean isOverdue;
    private Timestamp escalatedAt;

    // Constructors
    public WorkflowTask() {
    }

    public WorkflowTask(int workflowInstanceId, String bpmnNodeId, String name) {
        this.workflowInstanceId = workflowInstanceId;
        this.bpmnNodeId = bpmnNodeId;
        this.name = name;
        this.status = "Pending";
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(int workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public String getBpmnNodeId() {
        return bpmnNodeId;
    }

    public void setBpmnNodeId(String bpmnNodeId) {
        this.bpmnNodeId = bpmnNodeId;
    }

    public String getParentGatewayId() {
        return parentGatewayId;
    }

    public void setParentGatewayId(String parentGatewayId) {
        this.parentGatewayId = parentGatewayId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Integer assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Timestamp getDueDate() {
        return dueDate;
    }

    public void setDueDate(Timestamp dueDate) {
        this.dueDate = dueDate;
    }

    public Timestamp getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Timestamp startedAt) {
        this.startedAt = startedAt;
    }

    public Timestamp getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Timestamp getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Timestamp assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Integer getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(Integer completedBy) {
        this.completedBy = completedBy;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Timestamp getDueAt() {
        return dueAt;
    }

    public void setDueAt(Timestamp dueAt) {
        this.dueAt = dueAt;
    }

    public Boolean getIsOverdue() {
        return isOverdue;
    }

    public void setIsOverdue(Boolean isOverdue) {
        this.isOverdue = isOverdue;
    }

    public Timestamp getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(Timestamp escalatedAt) {
        this.escalatedAt = escalatedAt;
    }
}
