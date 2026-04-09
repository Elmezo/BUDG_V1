package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class Status {

    @SerializedName("id")
    private Integer id;

    @SerializedName("primaryname")
    private String primaryName;

    @SerializedName("description")
    private String description;

    @SerializedName("lastupdatedatetime")
    private Timestamp lastUpdateDateTime;

    @SerializedName("deletedate")
    private Timestamp deleteDate;

    @SerializedName("priority")
    private Integer priority;

    @SerializedName("lastupdateuser_id")
    private Integer lastUpdateUserId;

    public Status() {}

    public Status(Integer id, String primaryName, String description,
                  Timestamp lastUpdateDateTime, Timestamp deleteDate,
                  Integer priority, Integer lastUpdateUserId) {
        this.id = id;
        this.primaryName = primaryName;
        this.description = description;
        this.lastUpdateDateTime = lastUpdateDateTime;
        this.deleteDate = deleteDate;
        this.priority = priority;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    public Status(String primaryName, String description, Integer priority) {
        this.primaryName = primaryName;
        this.description = description;
        this.priority = priority;
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

    public Timestamp getDeleteDate() {
        return deleteDate;
    }

    public void setDeleteDate(Timestamp deleteDate) {
        this.deleteDate = deleteDate;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "Status{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", lastUpdateDateTime=" + lastUpdateDateTime +
                ", deleteDate=" + deleteDate +
                ", priority=" + priority +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
