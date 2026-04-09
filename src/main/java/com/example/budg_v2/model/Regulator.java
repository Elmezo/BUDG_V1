package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class Regulator {

    @SerializedName("id")
    private Integer id;

    @SerializedName("primaryName")
    private String primaryName;

    @SerializedName("shortName")
    private String shortName;

    @SerializedName("description")
    private String description;

    @SerializedName("createDateTime")
    private Timestamp createDateTime;

    @SerializedName("lastUpdateDateTime")
    private Timestamp lastUpdateDateTime;

    @SerializedName("deletedDateTime")
    private Timestamp deletedDateTime;

    @SerializedName("lastUpdateUserId")
    private Integer lastUpdateUserId;

    @SerializedName("lastUpdatedByName")
    private String lastUpdatedByName;

    // Constructors
    public Regulator() {}

    public Regulator(Integer id, String primaryName, String shortName, String description, 
                    Timestamp createDateTime, Timestamp lastUpdateDateTime, 
                    Timestamp deletedDateTime, Integer lastUpdateUserId) {
        this.id = id;
        this.primaryName = primaryName;
        this.shortName = shortName;
        this.description = description;
        this.createDateTime = createDateTime;
        this.lastUpdateDateTime = lastUpdateDateTime;
        this.deletedDateTime = deletedDateTime;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    // Getters and Setters
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

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Timestamp getCreateDateTime() {
        return createDateTime;
    }

    public void setCreateDateTime(Timestamp createDateTime) {
        this.createDateTime = createDateTime;
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

    public String getLastUpdatedByName() {
        return lastUpdatedByName;
    }

    public void setLastUpdatedByName(String lastUpdatedByName) {
        this.lastUpdatedByName = lastUpdatedByName;
    }

    @Override
    public String toString() {
        return "Regulator{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", shortName='" + shortName + '\'' +
                ", description='" + description + '\'' +
                ", createDateTime=" + createDateTime +
                ", lastUpdateDateTime=" + lastUpdateDateTime +
                ", deletedDateTime=" + deletedDateTime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
