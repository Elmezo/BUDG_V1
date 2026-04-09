package com.example.budg_v2.model;

import java.time.LocalDateTime;

/**
 * Model for pending changes stored during active Change Request.
 * When an object has an active auto-created CR, edits go here instead of the object table.
 */
public class PendingChange {
    private int id;
    private int facetType;          // module.id (Glossary=12, Dataset=11, System=13, Process=4)
    private String facetName;       // Transient: resolved facet name for display
    private int objectId;
    private int changeRequestId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private int userId;
    private String userName;        // Transient: resolved user name for display
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public PendingChange() {}

    public PendingChange(int facetType, int objectId, int changeRequestId, 
                         String fieldName, String oldValue, String newValue, int userId) {
        this.facetType = facetType;
        this.objectId = objectId;
        this.changeRequestId = changeRequestId;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.userId = userId;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFacetType() { return facetType; }
    public void setFacetType(int facetType) { this.facetType = facetType; }

    public String getFacetName() { return facetName; }
    public void setFacetName(String facetName) { this.facetName = facetName; }

    public int getObjectId() { return objectId; }
    public void setObjectId(int objectId) { this.objectId = objectId; }

    public int getChangeRequestId() { return changeRequestId; }
    public void setChangeRequestId(int changeRequestId) { this.changeRequestId = changeRequestId; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

