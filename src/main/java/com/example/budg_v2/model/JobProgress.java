package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

public class JobProgress {

    @SerializedName("id")
    private Integer id;

    @SerializedName("job_id")
    private Integer jobId;

    @SerializedName("expected_ticks")
    private Integer expectedTicks;

    @SerializedName("status")
    private String status;

    @SerializedName("message")
    private String message;

    // Constructors
    public JobProgress() {}

    public JobProgress(Integer id, Integer jobId, Integer expectedTicks, 
                      String status, String message) {
        this.id = id;
        this.jobId = jobId;
        this.expectedTicks = expectedTicks;
        this.status = status;
        this.message = message;
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

    public Integer getExpectedTicks() {
        return expectedTicks;
    }

    public void setExpectedTicks(Integer expectedTicks) {
        this.expectedTicks = expectedTicks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}


