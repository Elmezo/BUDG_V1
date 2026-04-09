package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class ProcessDurationType {

    @SerializedName("id")
    private Integer id;

    @SerializedName("primaryname")
    private String primaryName;

    @SerializedName("description")
    private String description;

    @SerializedName("priority")
    private Integer priority;

    @SerializedName("lastupdatedatetime")
    private Timestamp lastUpdateDateTime;

    @SerializedName("deletedatetime")
    private Timestamp deletedDateTime;

    @SerializedName("lastupdateuser_id")
    private Integer lastUpdateUserId;

    public ProcessDurationType() {}

    public ProcessDurationType(Integer id, String primaryName, String description,
                              Integer priority, Timestamp lastUpdateDateTime,
                              Timestamp deletedDateTime, Integer lastUpdateUserId) {
        this.id = id;
        this.primaryName = primaryName;
        this.description = description;
        this.priority = priority;
        this.lastUpdateDateTime = lastUpdateDateTime;
        this.deletedDateTime = deletedDateTime;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    public ProcessDurationType(String primaryName, String description, Integer priority) {
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

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Timestamp getLastUpdateDateTime() {
        return lastUpdateDateTime;
    }

    public void setLastUpdateDateTime(Timestamp lastUpdateDateTime) {
        this.lastUpdateDateTime = lastUpdateDateTime;
    }

    public Timestamp getDeletedDateTime() {
        return deletedDateTime;
    }

    public void setDeletedDateTime(Timestamp deletedDateTime) {
        this.deletedDateTime = deletedDateTime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "ProcessDurationType{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", priority=" + priority +
                ", lastUpdateDateTime=" + lastUpdateDateTime +
                ", deletedDateTime=" + deletedDateTime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
