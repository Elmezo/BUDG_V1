package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model class for job_field_mapping table
 * Represents the mapping between Excel columns and database fields
 */
public class JobFieldMapping {

    @SerializedName("id")
    private Integer id;

    @SerializedName("job_entity_id")
    private Integer jobEntityId; // Foreign key to job(ID)

    @SerializedName("source_field")
    private String sourceField; // Column name in Excel file

    @SerializedName("target_field")
    private String targetField; // Corresponding field in database

    // Constructors
    public JobFieldMapping() {}

    public JobFieldMapping(Integer id, Integer jobEntityId, String sourceField, String targetField) {
        this.id = id;
        this.jobEntityId = jobEntityId;
        this.sourceField = sourceField;
        this.targetField = targetField;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getJobEntityId() {
        return jobEntityId;
    }

    public void setJobEntityId(Integer jobEntityId) {
        this.jobEntityId = jobEntityId;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }
}

