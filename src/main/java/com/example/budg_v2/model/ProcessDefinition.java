package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for process_definition table
 */
public class ProcessDefinition {
    private int id;
    private String primaryName;
    private String reference;
    private boolean isDefault;
    private String description;
    private String status;
    private int lastUserChange;
    private int entityId;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Constructors
    public ProcessDefinition() {
    }

    public ProcessDefinition(String primaryName, String description, int entityId) {
        this.primaryName = primaryName;
        this.description = description;
        this.entityId = entityId;
        this.status = "Enabled";
        this.isDefault = false;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getLastUserChange() {
        return lastUserChange;
    }

    public void setLastUserChange(int lastUserChange) {
        this.lastUserChange = lastUserChange;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
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
