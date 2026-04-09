package com.example.budg_v2.bulk.relationships.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive response for bulk relationship upload
 * Contains success status, counts, warnings, errors, and cache statistics
 */
public class BulkUploadResponse {
    private boolean success;
    private String status;  // "success", "failed", "error", "invalid"
    private int inserted;
    private int deleted;
    private int skipped;
    private int failed;
    private int totalRows;
    private boolean cancelledDueToWarning;
    private long processingTimeMs;
    private List<ValidationIssue> warnings;
    private List<ValidationIssue> errors;
    private CacheStats cacheStats;
    private String message;
    private Integer jobId;
    private String referenceName;
    @SerializedName("upload_option")
    private String uploadOption;
    private List<RelationshipRowData> allProcessedRows;
    private boolean reportAvailable;
    
    public BulkUploadResponse() {
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.cacheStats = new CacheStats();
    }
    
    public void addWarning(ValidationIssue warning) {
        this.warnings.add(warning);
    }
    
    public void addError(ValidationIssue error) {
        this.errors.add(error);
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public int getInserted() {
        return inserted;
    }
    
    public void setInserted(int inserted) {
        this.inserted = inserted;
    }
    
    public int getDeleted() {
        return deleted;
    }
    
    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }
    
    public int getSkipped() {
        return skipped;
    }
    
    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
    
    public int getFailed() {
        return failed;
    }
    
    public void setFailed(int failed) {
        this.failed = failed;
    }
    
    public int getTotalRows() {
        return totalRows;
    }
    
    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }
    
    public boolean isCancelledDueToWarning() {
        return cancelledDueToWarning;
    }
    
    public void setCancelledDueToWarning(boolean cancelledDueToWarning) {
        this.cancelledDueToWarning = cancelledDueToWarning;
    }
    
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
    
    public List<ValidationIssue> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<ValidationIssue> warnings) {
        this.warnings = warnings;
    }
    
    public List<ValidationIssue> getErrors() {
        return errors;
    }
    
    public void setErrors(List<ValidationIssue> errors) {
        this.errors = errors;
    }
    
    public CacheStats getCacheStats() {
        return cacheStats;
    }
    
    public void setCacheStats(CacheStats cacheStats) {
        this.cacheStats = cacheStats;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Integer getJobId() {
        return jobId;
    }
    
    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }
    
    public String getReferenceName() {
        return referenceName;
    }
    
    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }
    
    public String getUploadOption() {
        return uploadOption;
    }
    
    public void setUploadOption(String uploadOption) {
        this.uploadOption = uploadOption;
    }
    
    public List<RelationshipRowData> getAllProcessedRows() {
        return allProcessedRows;
    }
    
    public void setAllProcessedRows(List<RelationshipRowData> allProcessedRows) {
        this.allProcessedRows = allProcessedRows;
    }
    
    public boolean isReportAvailable() {
        return reportAvailable;
    }
    
    public void setReportAvailable(boolean reportAvailable) {
        this.reportAvailable = reportAvailable;
    }
    
    @Override
    public String toString() {
        return "BulkUploadResponse{" +
                "success=" + success +
                ", inserted=" + inserted +
                ", deleted=" + deleted +
                ", skipped=" + skipped +
                ", failed=" + failed +
                ", totalRows=" + totalRows +
                ", cancelledDueToWarning=" + cancelledDueToWarning +
                ", processingTimeMs=" + processingTimeMs +
                ", warnings=" + warnings.size() +
                ", errors=" + errors.size() +
                '}';
    }
}

