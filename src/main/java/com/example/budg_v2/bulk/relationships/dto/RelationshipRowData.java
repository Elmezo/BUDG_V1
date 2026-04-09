package com.example.budg_v2.bulk.relationships.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured DTO for each Excel row during bulk relationship upload
 * Contains original values, resolved IDs, and validation status
 */
public class RelationshipRowData {
    private static final Logger logger = LoggerFactory.getLogger(RelationshipRowData.class);
    
    private int rowNumber;
    private Map<String, String> originalValues;
    private Map<String, Integer> resolvedEntityIds;
    private Integer relationshipTypeId;
    private List<String> warnings;
    private List<String> errors;
    private boolean isValid;
    private String skipReason;
    
    public RelationshipRowData() {
        this.originalValues = new HashMap<>();
        this.resolvedEntityIds = new HashMap<>();
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.isValid = false;
    }
    
    public RelationshipRowData(int rowNumber) {
        this();
        this.rowNumber = rowNumber;
    }
    
    public void addWarning(String warning) {
        if (warning != null && !this.warnings.contains(warning)) {
            this.warnings.add(warning);
        }
    }
    
    public void addError(String error) {
        if (error != null && !this.errors.contains(error)) {
            this.errors.add(error);
            this.isValid = false;
        }
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public void addResolvedEntityId(String entityKey, Integer id) {
        // Allow null values for optional entities, but log a warning for debugging
        // CRITICAL: Null values stored here could cause NullPointerException if intValue() is called later
        if (id == null) {
            logger.debug("Row {}: Adding null entity ID for key '{}'. " +
                "This is acceptable for optional entities, but calling code must check for null before using intValue().",
                rowNumber, entityKey);
        } else if (id <= 0) {
            logger.warn("Row {}: Adding invalid entity ID {} for key '{}'. " +
                "Entity IDs should be positive integers. This may cause issues later.",
                rowNumber, id, entityKey);
        }
        this.resolvedEntityIds.put(entityKey, id);
    }
    
    public void addOriginalValue(String columnName, String value) {
        this.originalValues.put(columnName, value);
    }
    
    // Getters and Setters
    public int getRowNumber() {
        return rowNumber;
    }
    
    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }
    
    public Map<String, String> getOriginalValues() {
        return originalValues;
    }
    
    public void setOriginalValues(Map<String, String> originalValues) {
        this.originalValues = originalValues;
    }
    
    public Map<String, Integer> getResolvedEntityIds() {
        return resolvedEntityIds;
    }
    
    public void setResolvedEntityIds(Map<String, Integer> resolvedEntityIds) {
        this.resolvedEntityIds = resolvedEntityIds;
    }
    
    public Integer getRelationshipTypeId() {
        return relationshipTypeId;
    }
    
    public void setRelationshipTypeId(Integer relationshipTypeId) {
        // CRITICAL: Null values stored here could cause NullPointerException if intValue() is called later
        if (relationshipTypeId == null) {
            logger.debug("Row {}: Setting null relationship type ID. " +
                "This is acceptable if relationship type is optional, but calling code must check for null before using intValue().",
                rowNumber);
        } else if (relationshipTypeId <= 0) {
            logger.warn("Row {}: Setting invalid relationship type ID {}. " +
                "Relationship type IDs should be positive integers. This may cause issues later.",
                rowNumber, relationshipTypeId);
        }
        this.relationshipTypeId = relationshipTypeId;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
    
    public boolean isValid() {
        return isValid;
    }
    
    public void setValid(boolean valid) {
        isValid = valid;
    }
    
    public String getSkipReason() {
        return skipReason;
    }
    
    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }
    
    @Override
    public String toString() {
        return "RelationshipRowData{" +
                "rowNumber=" + rowNumber +
                ", isValid=" + isValid +
                ", warnings=" + warnings.size() +
                ", errors=" + errors.size() +
                ", skipReason='" + skipReason + '\'' +
                '}';
    }
}

