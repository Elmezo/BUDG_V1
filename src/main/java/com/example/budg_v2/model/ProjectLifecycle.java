package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

public class ProjectLifecycle {
    @SerializedName("id")
    private Integer id;
    
    @SerializedName("primaryname")
    private String primaryName;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("lastupdate_userid")
    private Integer lastUpdateUserId;
    
    @SerializedName("lastupdatedatetime")
    private String lastUpdatedDateTime;

    // Constructors
    public ProjectLifecycle() {}

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getPrimaryName() { return primaryName; }
    public void setPrimaryName(String primaryName) { this.primaryName = primaryName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getLastUpdateUserId() { return lastUpdateUserId; }
    public void setLastUpdateUserId(Integer lastUpdateUserId) { this.lastUpdateUserId = lastUpdateUserId; }

    public String getLastUpdatedDateTime() { return lastUpdatedDateTime; }
    public void setLastUpdatedDateTime(String lastUpdatedDateTime) { this.lastUpdatedDateTime = lastUpdatedDateTime; }

    @Override
    public String toString() {
        return "ProjectLifecycle{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
