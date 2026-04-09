package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for system_settings table
 * Represents system-wide configuration settings organized by groups
 */
public class SystemSettings {
    private Integer id;
    private String settingGroup;
    private String settingKey;
    private String settingValue;
    private String dataType;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Constructors
    public SystemSettings() {
    }

    public SystemSettings(String settingGroup, String settingKey, String settingValue, String dataType) {
        this.settingGroup = settingGroup;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.dataType = dataType;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSettingGroup() {
        return settingGroup;
    }

    public void setSettingGroup(String settingGroup) {
        this.settingGroup = settingGroup;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
