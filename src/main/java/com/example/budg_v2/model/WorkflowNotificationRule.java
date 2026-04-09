package com.example.budg_v2.model;

import java.sql.Timestamp;
import java.util.List;

/**
 * Model class for workflow_notification_rule table
 * Represents a notification rule configuration
 */
public class WorkflowNotificationRule {
    private Long id;
    private String module; // Workflow name or "*" for all
    private String eventType; // ASSIGN, OVERDUE, ESCALATION
    private String recipientRole;
    private Long recipientUserId;
    private List<String> channels; // ["email", "ui", "sms"]
    private String deliveryMode; // IMMEDIATE or BATCHED
    private Boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Constructors
    public WorkflowNotificationRule() {
    }

    public WorkflowNotificationRule(String module, String eventType, List<String> channels) {
        this.module = module;
        this.eventType = eventType;
        this.channels = channels;
        this.active = true;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getRecipientRole() {
        return recipientRole;
    }

    public void setRecipientRole(String recipientRole) {
        this.recipientRole = recipientRole;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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

