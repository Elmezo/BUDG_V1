package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class EmploymentType {

    @SerializedName("id")
    private Integer id;

    @SerializedName("primary_Name")
    private String primaryName;

    @SerializedName("last_updated_date")
    private Timestamp lastUpdatedDate;

    @SerializedName("last_update_user_id")
    private Integer lastUpdateUserId;

    public EmploymentType() {}

    public EmploymentType(Integer id, String primaryName, Timestamp lastUpdatedDate, Integer lastUpdateUserId) {
        this.id = id;
        this.primaryName = primaryName;
        this.lastUpdatedDate = lastUpdatedDate;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    public EmploymentType(String primaryName) {
        this.primaryName = primaryName;
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
        return "EmploymentType{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", lastUpdatedDate=" + lastUpdatedDate +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
