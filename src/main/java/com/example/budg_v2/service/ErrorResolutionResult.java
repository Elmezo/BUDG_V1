package com.example.budg_v2.service;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of error handling with fix suggestions
 */
public class ErrorResolutionResult {
    
    public enum ErrorSeverity {
        CRITICAL,   // Must fix - cannot continue
        WARNING,    // Can continue but may cause issues
        INFO        // Suggestion only
    }
    
    private final boolean canAutoFix;
    private final boolean shouldRetry;
    private final ErrorSeverity severity;
    private final String errorMessage;
    private final String suggestedFix;
    private final List<String> fixSuggestions;
    private final JsonObject fixedData; // Fixed row data if auto-fix was applied
    
    private ErrorResolutionResult(Builder builder) {
        this.canAutoFix = builder.canAutoFix;
        this.shouldRetry = builder.shouldRetry;
        this.severity = builder.severity;
        this.errorMessage = builder.errorMessage;
        this.suggestedFix = builder.suggestedFix;
        this.fixSuggestions = builder.fixSuggestions;
        this.fixedData = builder.fixedData;
    }
    
    public boolean canAutoFix() {
        return canAutoFix;
    }
    
    public boolean shouldRetry() {
        return shouldRetry;
    }
    
    public ErrorSeverity getSeverity() {
        return severity;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public String getSuggestedFix() {
        return suggestedFix;
    }
    
    public List<String> getFixSuggestions() {
        return new ArrayList<>(fixSuggestions);
    }
    
    public JsonObject getFixedData() {
        return fixedData;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private boolean canAutoFix = false;
        private boolean shouldRetry = false;
        private ErrorSeverity severity = ErrorSeverity.CRITICAL;
        private String errorMessage;
        private String suggestedFix;
        private List<String> fixSuggestions = new ArrayList<>();
        private JsonObject fixedData;
        
        public Builder canAutoFix(boolean canAutoFix) {
            this.canAutoFix = canAutoFix;
            return this;
        }
        
        public Builder shouldRetry(boolean shouldRetry) {
            this.shouldRetry = shouldRetry;
            return this;
        }
        
        public Builder severity(ErrorSeverity severity) {
            this.severity = severity;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder suggestedFix(String suggestedFix) {
            this.suggestedFix = suggestedFix;
            return this;
        }
        
        public Builder addFixSuggestion(String suggestion) {
            this.fixSuggestions.add(suggestion);
            return this;
        }
        
        public Builder fixSuggestions(List<String> suggestions) {
            this.fixSuggestions = new ArrayList<>(suggestions);
            return this;
        }
        
        public Builder fixedData(JsonObject fixedData) {
            this.fixedData = fixedData;
            return this;
        }
        
        public ErrorResolutionResult build() {
            return new ErrorResolutionResult(this);
        }
    }
}

