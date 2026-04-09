package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

public class JobReportItemMessage {

    @SerializedName("id")
    private Integer id;

    @SerializedName("report_id")
    private Integer reportId;

    @SerializedName("error_code")
    private String errorCode;

    @SerializedName("message")
    private String message;

    @SerializedName("type")
    private String type;

    // Constructors
    public JobReportItemMessage() {}

    public JobReportItemMessage(Integer id, Integer reportId, String errorCode, 
                               String message, String type) {
        this.id = id;
        this.reportId = reportId;
        this.errorCode = errorCode;
        this.message = message;
        this.type = type;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getReportId() {
        return reportId;
    }

    public void setReportId(Integer reportId) {
        this.reportId = reportId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}


