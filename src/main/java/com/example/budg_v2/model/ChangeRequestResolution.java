package com.example.budg_v2.model;

import java.time.LocalDateTime;

/**
 * Model class for changerequest_resolution table
 */
public class ChangeRequestResolution {
    private Integer id;
    private Integer changeRequestId;
    private Integer resolutionStatusId;
    private String description;
    private Integer lastUserChange;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional fields for display
    private String statusName;

    // Default constructor
    public ChangeRequestResolution() {}

    // Constructor with required fields
    public ChangeRequestResolution(Integer changeRequestId, Integer resolutionStatusId, String description, Integer lastUserChange) {
        this.changeRequestId = changeRequestId;
        this.resolutionStatusId = resolutionStatusId;
        this.description = description;
        this.lastUserChange = lastUserChange;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getChangeRequestId() {
        return changeRequestId;
    }

    public void setChangeRequestId(Integer changeRequestId) {
        this.changeRequestId = changeRequestId;
    }

    public Integer getResolutionStatusId() {
        return resolutionStatusId;
    }

    public void setResolutionStatusId(Integer resolutionStatusId) {
        this.resolutionStatusId = resolutionStatusId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getLastUserChange() {
        return lastUserChange;
    }

    public void setLastUserChange(Integer lastUserChange) {
        this.lastUserChange = lastUserChange;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    @Override
    public String toString() {
        return "ChangeRequestResolution{" +
                "id=" + id +
                ", changeRequestId=" + changeRequestId +
                ", resolutionStatusId=" + resolutionStatusId +
                ", description='" + description + '\'' +
                ", lastUserChange=" + lastUserChange +
                ", createdAt=" + createdAt +
                '}';
    }
}
