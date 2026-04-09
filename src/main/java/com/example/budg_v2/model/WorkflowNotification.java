package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for workflow_notification table
 * Represents a stored notification for UI display
 */
public class WorkflowNotification {
    private Long id;
    private Long notificationRuleId;
    private Integer workflowTaskId;
    private Integer changeRequestId;
    private Integer recipientUserId;
    private String eventType; // ASSIGN, OVERDUE, ESCALATION
    private String title;
    private String message;
    private String channel; // ui, email, sms
    private String category; // workflow, catalog, roles, bulk_upload
    private Integer objectId; // For role notifications: ID of the object (dataset, system, etc.)
    private String facetType; // For role notifications: Type of facet (e.g., "Data Set", "System", "Glossary")
    private Boolean emailSent;
    private Timestamp emailSentAt;
    private Boolean read;
    private Timestamp createdAt;

    // Constructors
    public WorkflowNotification() {
        this.read = false;
    }

    public WorkflowNotification(Integer workflowTaskId, Integer recipientUserId, 
                               String eventType, String title, String message, String channel) {
        this.workflowTaskId = workflowTaskId;
        this.recipientUserId = recipientUserId;
        this.eventType = eventType;
        this.title = title;
        this.message = message;
        this.channel = channel;
        this.read = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNotificationRuleId() {
        return notificationRuleId;
    }

    public void setNotificationRuleId(Long notificationRuleId) {
        this.notificationRuleId = notificationRuleId;
    }

    public Integer getWorkflowTaskId() {
        return workflowTaskId;
    }

    public void setWorkflowTaskId(Integer workflowTaskId) {
        this.workflowTaskId = workflowTaskId;
    }

    public Integer getChangeRequestId() {
        return changeRequestId;
    }

    public void setChangeRequestId(Integer changeRequestId) {
        this.changeRequestId = changeRequestId;
    }

    public Integer getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(Integer recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getEmailSent() {
        return emailSent;
    }

    public void setEmailSent(Boolean emailSent) {
        this.emailSent = emailSent;
    }

    public Timestamp getEmailSentAt() {
        return emailSentAt;
    }

    public void setEmailSentAt(Timestamp emailSentAt) {
        this.emailSentAt = emailSentAt;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getObjectId() {
        return objectId;
    }

    public void setObjectId(Integer objectId) {
        this.objectId = objectId;
    }

    public String getFacetType() {
        return facetType;
    }

    public void setFacetType(String facetType) {
        this.facetType = facetType;
    }
}

