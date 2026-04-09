package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class JobResourceFile {

    @SerializedName("id")
    private Integer id;

    @SerializedName("job_id")
    private Integer jobId;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("original_file_name")
    private String originalFileName;

    @SerializedName("storage_path")
    private String storagePath;

    @SerializedName("store_file")
    private Boolean storeFile;

    @SerializedName("retention_days")
    private Integer retentionDays;

    @SerializedName("delete_at")
    private Timestamp deleteAt;

    // Constructors
    public JobResourceFile() {}

    public JobResourceFile(Integer id, Integer jobId, String fileName, 
                          String originalFileName, String storagePath,
                          Boolean storeFile, Integer retentionDays, 
                          Timestamp deleteAt) {
        this.id = id;
        this.jobId = jobId;
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.storagePath = storagePath;
        this.storeFile = storeFile;
        this.retentionDays = retentionDays;
        this.deleteAt = deleteAt;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public Boolean getStoreFile() {
        return storeFile;
    }

    public void setStoreFile(Boolean storeFile) {
        this.storeFile = storeFile;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Timestamp getDeleteAt() {
        return deleteAt;
    }

    public void setDeleteAt(Timestamp deleteAt) {
        this.deleteAt = deleteAt;
    }
}


