package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class PeopleLifecycleStatus {

    @SerializedName("id")
    private Integer id;

    @SerializedName("primary_Name")
    private String primaryName;

    @SerializedName("Description")
    private String description;

    @SerializedName("last_updated_date")
    private Timestamp lastUpdatedDate;

    @SerializedName("last_update_user_id")
    private Integer lastUpdateUserId;

    public PeopleLifecycleStatus() {}

    public PeopleLifecycleStatus(Integer id, String primaryName, String description, 
                                Timestamp lastUpdatedDate, Integer lastUpdateUserId) {
        this.id = id;
        this.primaryName = primaryName;
        this.description = description;
        this.lastUpdatedDate = lastUpdatedDate;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    public PeopleLifecycleStatus(String primaryName, String description) {
        this.primaryName = primaryName;
        this.description = description;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public Timestamp getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate(Timestamp lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "PeopleLifecycleStatus{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", lastUpdatedDate=" + lastUpdatedDate +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
