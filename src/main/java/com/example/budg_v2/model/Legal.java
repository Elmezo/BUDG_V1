package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class Legal {

    @SerializedName("id")
    private Integer id;

    @SerializedName("parent_id")
    private Integer parentId;

    @SerializedName("status")
    private Integer status;

    @SerializedName("is_public")
    private Integer isPublic;

    @SerializedName("shortname")
    private String shortName;

    @SerializedName("longname")
    private String longName;

    @SerializedName("description")
    private String description;

    @SerializedName("createdatetime")
    private Timestamp createDatetime;

    @SerializedName("lastupdatedatetime")
    private Timestamp lastUpdateDatetime;

    @SerializedName("deletedatetime")
    private Timestamp deleteDatetime;

    @SerializedName("lastupdateuser_id")
    private Integer lastUpdateUserId;

    // Additional fields for joined data
    @SerializedName("parent_short_name")
    private String parentShortName;

    @SerializedName("parent_long_name")
    private String parentLongName;

    @SerializedName("BUDG_status")
    private String BUDGStatus;

    @SerializedName("BUDG_viewing")
    private String BUDGViewing;

    @SerializedName("last_updated_by")
    private String lastUpdatedBy;

    public Legal() {}

    public Legal(Integer id, Integer parentId, Integer status, Integer isPublic,
                 String shortName, String longName, String description,
                 Timestamp createDatetime, Timestamp lastUpdateDatetime,
                 Timestamp deleteDatetime, Integer lastUpdateUserId) {
        this.id = id;
        this.parentId = parentId;
        this.status = status;
        this.isPublic = isPublic;
        this.shortName = shortName;
        this.longName = longName;
        this.description = description;
        this.createDatetime = createDatetime;
        this.lastUpdateDatetime = lastUpdateDatetime;
        this.deleteDatetime = deleteDatetime;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getLongName() {
        return longName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Timestamp getCreateDatetime() {
        return createDatetime;
    }

    public void setCreateDatetime(Timestamp createDatetime) {
        this.createDatetime = createDatetime;
    }

    public Timestamp getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }

    public void setLastUpdateDatetime(Timestamp lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }

    public Timestamp getDeleteDatetime() {
        return deleteDatetime;
    }

    public void setDeleteDatetime(Timestamp deleteDatetime) {
        this.deleteDatetime = deleteDatetime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    // Additional getters and setters for joined data
    public String getParentShortName() {
        return parentShortName;
    }

    public void setParentShortName(String parentShortName) {
        this.parentShortName = parentShortName;
    }

    public String getParentLongName() {
        return parentLongName;
    }

    public void setParentLongName(String parentLongName) {
        this.parentLongName = parentLongName;
    }

    public String getBUDGStatus() {
        return BUDGStatus;
    }

    public void setBUDGStatus(String BUDGStatus) {
        this.BUDGStatus = BUDGStatus;
    }

    public String getBUDGViewing() {
        return BUDGViewing;
    }

    public void setBUDGViewing(String BUDGViewing) {
        this.BUDGViewing = BUDGViewing;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    @Override
    public String toString() {
        return "Legal{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", status=" + status +
                ", isPublic=" + isPublic +
                ", shortName='" + shortName + '\'' +
                ", longName='" + longName + '\'' +
                ", description='" + description + '\'' +
                ", createDatetime=" + createDatetime +
                ", lastUpdateDatetime=" + lastUpdateDatetime +
                ", deleteDatetime=" + deleteDatetime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
