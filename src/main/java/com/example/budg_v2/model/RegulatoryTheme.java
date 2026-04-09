package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class RegulatoryTheme {

    @SerializedName("id")
    private Integer id;

    @SerializedName("parentId")
    private Integer parentId;

    @SerializedName("statusId")
    private Integer statusId;

    @SerializedName("statusName")
    private String statusName;

    @SerializedName("refNumber")
    private String refNumber;

    @SerializedName("shortName")
    private String shortName;

    @SerializedName("primaryName")
    private String primaryName;

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

    @SerializedName("updatedByName")
    private String updatedByName;

    // Constructors
    public RegulatoryTheme() {}

    public RegulatoryTheme(Integer id, Integer parentId, Integer statusId, String refNumber, 
                          String shortName, String primaryName, String description, 
                          Timestamp createDateTime, Timestamp lastUpdateDateTime, 
                          Timestamp deletedDateTime, Integer lastUpdateUserId) {
        this.id = id;
        this.parentId = parentId;
        this.statusId = statusId;
        this.refNumber = refNumber;
        this.shortName = shortName;
        this.primaryName = primaryName;
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

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public Integer getStatusId() {
        return statusId;
    }

    public void setStatusId(Integer statusId) {
        this.statusId = statusId;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public String getRefNumber() {
        return refNumber;
    }

    public void setRefNumber(String refNumber) {
        this.refNumber = refNumber;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
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

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }

    @Override
    public String toString() {
        return "RegulatoryTheme{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", statusId=" + statusId +
                ", refNumber='" + refNumber + '\'' +
                ", shortName='" + shortName + '\'' +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", createDateTime=" + createDateTime +
                ", lastUpdateDateTime=" + lastUpdateDateTime +
                ", deletedDateTime=" + deletedDateTime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
