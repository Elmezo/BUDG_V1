package com.example.budg_v2.bulk.relationships.dto;

/**
 * Result of checking for duplicate relationships
 */
public class DuplicateCheckResult {
    private boolean isDuplicate;
    private Integer existingId;
    private String message;
    
    public DuplicateCheckResult() {
    }
    
    public DuplicateCheckResult(boolean isDuplicate, Integer existingId, String message) {
        this.isDuplicate = isDuplicate;
        this.existingId = existingId;
        this.message = message;
    }
    
    public static DuplicateCheckResult noDuplicate() {
        return new DuplicateCheckResult(false, null, null);
    }
    
    public static DuplicateCheckResult duplicate(Integer existingId) {
        return new DuplicateCheckResult(true, existingId, 
            "Relationship already exists with ID=" + existingId);
    }
    
    public static DuplicateCheckResult notFound() {
        return new DuplicateCheckResult(false, null, 
            "Relationship does not exist in database");
    }
    
    // Getters and Setters
    public boolean isDuplicate() {
        return isDuplicate;
    }
    
    public void setDuplicate(boolean duplicate) {
        isDuplicate = duplicate;
    }
    
    public Integer getExistingId() {
        return existingId;
    }
    
    public void setExistingId(Integer existingId) {
        this.existingId = existingId;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @Override
    public String toString() {
        return "DuplicateCheckResult{" +
                "isDuplicate=" + isDuplicate +
                ", existingId=" + existingId +
                ", message='" + message + '\'' +
                '}';
    }
}

