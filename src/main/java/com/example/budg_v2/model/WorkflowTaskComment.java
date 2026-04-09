package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for workflow_task_comment table
 */
public class WorkflowTaskComment {
    private int id;
    private int workflowTaskId;
    private String commentText;
    private int createdBy;
    private Timestamp createdAt;

    // Constructors
    public WorkflowTaskComment() {
    }

    public WorkflowTaskComment(int workflowTaskId, String commentText, int createdBy) {
        this.workflowTaskId = workflowTaskId;
        this.commentText = commentText;
        this.createdBy = createdBy;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWorkflowTaskId() {
        return workflowTaskId;
    }

    public void setWorkflowTaskId(int workflowTaskId) {
        this.workflowTaskId = workflowTaskId;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
