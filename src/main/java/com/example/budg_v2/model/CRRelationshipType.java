package com.example.budg_v2.model;

import java.time.LocalDateTime;

public class CRRelationshipType {
    private Integer id;
    private String name;
    private String description;
    private String status;
    private Integer lastUserChange;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public CRRelationshipType() {}
    
    public CRRelationshipType(String name, String description) {
        this.name = name;
        this.description = description;
        this.status = "Enabled";
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
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
}
