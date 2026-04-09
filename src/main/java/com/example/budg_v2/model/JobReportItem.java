package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class JobReportItem {

    @SerializedName("id")
    private Integer id;

    @SerializedName("job_id")
    private Integer jobId;

    @SerializedName("field_name")
    private String fieldName;

    @SerializedName("status")
    private String status;

    @SerializedName("position")
    private Integer position;

    @SerializedName("messages")
    private List<JobReportItemMessage> messages;

    // Constructors
    public JobReportItem() {}

    public JobReportItem(Integer id, Integer jobId, String fieldName, 
                        String status, Integer position) {
        this.id = id;
        this.jobId = jobId;
        this.fieldName = fieldName;
        this.status = status;
        this.position = position;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public List<JobReportItemMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<JobReportItemMessage> messages) {
        this.messages = messages;
    }
}


