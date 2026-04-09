package com.example.budg_v2.model;

import java.sql.Timestamp;

public class BusinessArea {
    private int id;
    private Integer parentId;
    private Integer isPublic;
    private Integer status;
    private String statusName;
    private Integer lifecycle;
    private String primaryName;
    private String description;
    private Timestamp createDatetime;
    private Timestamp lastUpdateDatetime;
    private Timestamp deleteDatetime;
    private Integer createUserId;
    private Integer lastUpdateUserId;

    // Constructors
    public BusinessArea() {}

    public BusinessArea(int id, Integer parentId, Integer isPublic, Integer status, Integer lifecycle,
                      String primaryName, String description, Timestamp createDatetime,
                      Timestamp lastUpdateDatetime, Timestamp deleteDatetime, Integer createUserId, Integer lastUpdateUserId) {
        this.id = id;
        this.parentId = parentId;
        this.isPublic = isPublic;
        this.status = status;
        this.lifecycle = lifecycle;
        this.primaryName = primaryName;
        this.description = description;
        this.createDatetime = createDatetime;
        this.lastUpdateDatetime = lastUpdateDatetime;
        this.deleteDatetime = deleteDatetime;
        this.createUserId = createUserId;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public Integer getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Integer lifecycle) {
        this.lifecycle = lifecycle;
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

    public Integer getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(Integer createUserId) {
        this.createUserId = createUserId;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "BusinessArea{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", isPublic=" + isPublic +
                ", status=" + status +
                ", lifecycle=" + lifecycle +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", createDatetime=" + createDatetime +
                ", lastUpdateDatetime=" + lastUpdateDatetime +
                ", deleteDatetime=" + deleteDatetime +
                ", createUserId=" + createUserId +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
