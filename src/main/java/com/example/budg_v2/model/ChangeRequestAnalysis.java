package com.example.budg_v2.model;

import java.time.LocalDateTime;

/**
 * Model class for changerequest_analysis table
 */
public class ChangeRequestAnalysis {
    private Integer id;
    private Integer changeRequestId;
    private String analysis;
    private Integer lastUserChange;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Default constructor
    public ChangeRequestAnalysis() {}

    // Constructor with required fields
    public ChangeRequestAnalysis(Integer changeRequestId, String analysis, Integer lastUserChange) {
        this.changeRequestId = changeRequestId;
        this.analysis = analysis;
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

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
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

    @Override
    public String toString() {
        return "ChangeRequestAnalysis{" +
                "id=" + id +
                ", changeRequestId=" + changeRequestId +
                ", analysis='" + analysis + '\'' +
                ", lastUserChange=" + lastUserChange +
                ", createdAt=" + createdAt +
                '}';
    }
}
