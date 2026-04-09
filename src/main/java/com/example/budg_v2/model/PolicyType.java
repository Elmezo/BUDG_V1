package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

public class PolicyType {
    private int id;
    
    @SerializedName("PrimaryName")
    private String primaryName;
    
    @SerializedName("Description")
    private String description;
    
    private String lastUpdateDatetime;
    private Integer lastUpdateUserId;

    // Constructors
    public PolicyType() {}

    public PolicyType(int id, String primaryName, String description) {
        this.id = id;
        this.primaryName = primaryName;
        this.description = description;
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

    public String getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }

    public void setLastUpdateDatetime(String lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }
}
