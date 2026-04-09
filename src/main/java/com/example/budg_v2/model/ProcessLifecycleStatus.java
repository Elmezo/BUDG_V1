package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class ProcessLifecycleStatus {

    @SerializedName("id")
    private Integer id;

    @SerializedName("primaryname")
    private String primaryName;

    @SerializedName("description")
    private String description;

    @SerializedName("lastupdatedatetime")
    private Timestamp lastUpdateDateTime;

    @SerializedName("lastupdateuser_id")
    private Integer lastUpdateUserId;

    public ProcessLifecycleStatus() {}

    public ProcessLifecycleStatus(Integer id, String primaryName, String description,
                                 Timestamp lastUpdateDateTime, Integer lastUpdateUserId) {
        this.id = id;
        this.primaryName = primaryName;
        this.description = description;
        this.lastUpdateDateTime = lastUpdateDateTime;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    public ProcessLifecycleStatus(String primaryName, String description) {
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

    public Timestamp getLastUpdateDateTime() {
        return lastUpdateDateTime;
    }

    public void setLastUpdateDateTime(Timestamp lastUpdateDateTime) {
        this.lastUpdateDateTime = lastUpdateDateTime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "ProcessLifecycleStatus{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", lastUpdateDateTime=" + lastUpdateDateTime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
