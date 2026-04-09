package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model class for job_configkeys table
 * Stores the available configuration option keys
 */
public class JobConfigKey {

    @SerializedName("key_id")
    private Integer keyId;

    @SerializedName("option_key")
    private String optionKey; // e.g., "Cancel on Warning", "Segment Name"

    // Constructors
    public JobConfigKey() {}

    public JobConfigKey(Integer keyId, String optionKey) {
        this.keyId = keyId;
        this.optionKey = optionKey;
    }

    // Getters and Setters
    public Integer getKeyId() {
        return keyId;
    }

    public void setKeyId(Integer keyId) {
        this.keyId = keyId;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public void setOptionKey(String optionKey) {
        this.optionKey = optionKey;
    }
}

