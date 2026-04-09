package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model class for job_configuration table
 * Stores configuration options for each bulk upload job
 */
public class JobConfiguration {

    @SerializedName("id")
    private Integer id;

    @SerializedName("job_entity_id")
    private Integer jobEntityId; // Foreign key to job(ID)

    @SerializedName("option_keyid")
    private Integer optionKeyId; // Foreign key to job_configkeys(KeyID)

    @SerializedName("option_value")
    private String optionValue; // The actual value (e.g., "True", "False", "Finance")

    // Constructors
    public JobConfiguration() {}

    public JobConfiguration(Integer id, Integer jobEntityId, Integer optionKeyId, String optionValue) {
        this.id = id;
        this.jobEntityId = jobEntityId;
        this.optionKeyId = optionKeyId;
        this.optionValue = optionValue;
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

    public Integer getOptionKeyId() {
        return optionKeyId;
    }

    public void setOptionKeyId(Integer optionKeyId) {
        this.optionKeyId = optionKeyId;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public void setOptionValue(String optionValue) {
        this.optionValue = optionValue;
    }
}

