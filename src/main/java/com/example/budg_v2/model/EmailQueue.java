package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for email_queue table
 * Represents queued emails for batch delivery
 */
public class EmailQueue {
    private Long id;
    private Long notificationId;
    private Integer recipientUserId;
    private String subject;
    private String body;
    private String status; // PENDING, SENT, FAILED
    private Timestamp scheduledAt;
    private Timestamp sentAt;
    private String errorMessage;
    private Integer retryCount;
    private Timestamp createdAt;

    // Constructors
    public EmailQueue() {
        this.status = "PENDING";
        this.retryCount = 0;
    }

    public EmailQueue(Long notificationId, Integer recipientUserId, String subject, 
                     String body, Timestamp scheduledAt) {
        this.notificationId = notificationId;
        this.recipientUserId = recipientUserId;
        this.subject = subject;
        this.body = body;
        this.scheduledAt = scheduledAt;
        this.status = "PENDING";
        this.retryCount = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public Integer getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(Integer recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Timestamp scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Timestamp getSentAt() {
        return sentAt;
    }

    public void setSentAt(Timestamp sentAt) {
        this.sentAt = sentAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}





















































