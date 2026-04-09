package com.example.budg_v2.model;

import java.time.LocalDateTime;

public class CRRelationship {
    private Integer id;
    private Integer sourceId;
    private Integer targetId;
    private Integer crRelationshipTypeId;
    private Integer lastUserChange;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Display fields
    private String relationshipTypeName;
    private String targetChangeRequestTitle;
    private String targetChangeRequestReference;
    
    // Constructors
    public CRRelationship() {}
    
    public CRRelationship(Integer sourceId, Integer targetId, Integer crRelationshipTypeId) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.crRelationshipTypeId = crRelationshipTypeId;
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public Integer getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(Integer sourceId) {
        this.sourceId = sourceId;
    }
    
    public Integer getTargetId() {
        return targetId;
    }
    
    public void setTargetId(Integer targetId) {
        this.targetId = targetId;
    }
    
    public Integer getCrRelationshipTypeId() {
        return crRelationshipTypeId;
    }
    
    public void setCrRelationshipTypeId(Integer crRelationshipTypeId) {
        this.crRelationshipTypeId = crRelationshipTypeId;
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
    
    public String getRelationshipTypeName() {
        return relationshipTypeName;
    }
    
    public void setRelationshipTypeName(String relationshipTypeName) {
        this.relationshipTypeName = relationshipTypeName;
    }
    
    public String getTargetChangeRequestTitle() {
        return targetChangeRequestTitle;
    }
    
    public void setTargetChangeRequestTitle(String targetChangeRequestTitle) {
        this.targetChangeRequestTitle = targetChangeRequestTitle;
    }
    
    public String getTargetChangeRequestReference() {
        return targetChangeRequestReference;
    }
    
    public void setTargetChangeRequestReference(String targetChangeRequestReference) {
        this.targetChangeRequestReference = targetChangeRequestReference;
    }
}
