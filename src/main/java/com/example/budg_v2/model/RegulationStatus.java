package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.time.LocalDateTime;

public class RegulationStatus {
    @SerializedName("id")
    private int id;
    
    @SerializedName("primaryName")
    private String primaryName;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("lastUpdateDatetime")
    private LocalDateTime lastUpdateDatetime;
    
    @SerializedName("lastUpdateUserId")
    private Integer lastUpdateUserId;

    // Default constructor
    public RegulationStatus() {}

    // Constructor with required fields
    public RegulationStatus(String primaryName) {
        this.primaryName = primaryName;
        this.lastUpdateDatetime = LocalDateTime.now();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }

    public void setLastUpdateDatetime(LocalDateTime lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "RegulationStatus{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", lastUpdateDatetime=" + lastUpdateDatetime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
