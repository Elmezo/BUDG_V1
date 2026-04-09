package com.example.budg_v2.bulk.relationships.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of entity ID resolution
 * Contains resolved ID, status flags, and any warnings/errors
 */
public class EntityResolutionResult {
    private Integer id;
    private boolean found;
    private boolean ambiguous;
    private List<Integer> possibleIds;
    private String warning;
    private String error;
    private String resolvedBy; // REF, NAME_PARENT, NAME, CACHE
    
    public EntityResolutionResult() {
        this.possibleIds = new ArrayList<>();
    }
    
    public EntityResolutionResult(Integer id, boolean found, boolean ambiguous, String resolvedBy) {
        this();
        this.id = id;
        this.found = found;
        this.ambiguous = ambiguous;
        this.resolvedBy = resolvedBy;
    }
    
    // Static factory methods for common cases
    public static EntityResolutionResult success(Integer id, String resolvedBy) {
        return new EntityResolutionResult(id, true, false, resolvedBy);
    }
    
    public static EntityResolutionResult notFound(String warning) {
        EntityResolutionResult result = new EntityResolutionResult(null, false, false, null);
        result.setWarning(warning);
        return result;
    }
    
    public static EntityResolutionResult ambiguous(List<Integer> possibleIds, String warning) {
        EntityResolutionResult result = new EntityResolutionResult(null, false, true, null);
        result.setPossibleIds(possibleIds);
        result.setWarning(warning);
        return result;
    }
    
    public static EntityResolutionResult error(String error) {
        EntityResolutionResult result = new EntityResolutionResult(null, false, false, null);
        result.setError(error);
        return result;
    }
    
    public boolean hasWarning() {
        return warning != null && !warning.isEmpty();
    }
    
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }
    
    public boolean isValid() {
        return found && !ambiguous && !hasError();
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public boolean isFound() {
        return found;
    }
    
    public void setFound(boolean found) {
        this.found = found;
    }
    
    public boolean isAmbiguous() {
        return ambiguous;
    }
    
    public void setAmbiguous(boolean ambiguous) {
        this.ambiguous = ambiguous;
    }
    
    public List<Integer> getPossibleIds() {
        return possibleIds;
    }
    
    public void setPossibleIds(List<Integer> possibleIds) {
        this.possibleIds = possibleIds;
    }
    
    public String getWarning() {
        return warning;
    }
    
    public void setWarning(String warning) {
        this.warning = warning;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public String getResolvedBy() {
        return resolvedBy;
    }
    
    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }
    
    @Override
    public String toString() {
        return "EntityResolutionResult{" +
                "id=" + id +
                ", found=" + found +
                ", ambiguous=" + ambiguous +
                ", resolvedBy='" + resolvedBy + '\'' +
                ", warning='" + warning + '\'' +
                ", error='" + error + '\'' +
                '}';
    }
}

