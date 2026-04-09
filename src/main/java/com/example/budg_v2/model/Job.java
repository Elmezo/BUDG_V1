package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class Job {

    @SerializedName("id")
    private Integer id;

    @SerializedName("created_date")
    private Timestamp createdDate;

    @SerializedName("completed_date")
    private Timestamp completedDate;

    @SerializedName("type")
    private String type;

    @SerializedName("reference_name")
    private String referenceName;

    @SerializedName("items_count")
    private Integer itemsCount;

    @SerializedName("status")
    private String status; // Pending, Processing, Completed, Failed

    @SerializedName("created_by")
    private Integer createdBy;

    @SerializedName("child_jobs_order")
    private Integer childJobsOrder; // ترتيب الوظائف الفرعية لو العملية كبيرة ومقسومة على أكثر من جزء

    // Constructors
    public Job() {}

    public Job(Integer id, Timestamp createdDate, Timestamp completedDate, 
               String type, String referenceName, Integer itemsCount, 
               String status, Integer createdBy) {
        this.id = id;
        this.createdDate = createdDate;
        this.completedDate = completedDate;
        this.type = type;
        this.referenceName = referenceName;
        this.itemsCount = itemsCount;
        this.status = status;
        this.createdBy = createdBy;
    }

    public Job(Integer id, Timestamp createdDate, Timestamp completedDate, 
               String type, String referenceName, Integer itemsCount, 
               String status, Integer createdBy, Integer childJobsOrder) {
        this.id = id;
        this.createdDate = createdDate;
        this.completedDate = completedDate;
        this.type = type;
        this.referenceName = referenceName;
        this.itemsCount = itemsCount;
        this.status = status;
        this.createdBy = createdBy;
        this.childJobsOrder = childJobsOrder;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Timestamp createdDate) {
        this.createdDate = createdDate;
    }

    public Timestamp getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Timestamp completedDate) {
        this.completedDate = completedDate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    public Integer getItemsCount() {
        return itemsCount;
    }

    public void setItemsCount(Integer itemsCount) {
        this.itemsCount = itemsCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getChildJobsOrder() {
        return childJobsOrder;
    }

    public void setChildJobsOrder(Integer childJobsOrder) {
        this.childJobsOrder = childJobsOrder;
    }
}


