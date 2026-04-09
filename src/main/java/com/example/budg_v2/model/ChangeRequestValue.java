package com.example.budg_v2.model;

import java.time.LocalDateTime;

/**
 * Model class for changerequest_value table
 */
public class ChangeRequestValue {
    private Integer id;
    private Float value;
    private String type; // Enum: 'Enabled', 'Disabled'
    private Integer lastUserChange;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer crCurrencyId;

    // Default constructor
    public ChangeRequestValue() {
        this.type = "Enabled";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Constructor with required fields
    public ChangeRequestValue(Float value, Integer crCurrencyId, Integer lastUserChange) {
        this.value = value;
        this.crCurrencyId = crCurrencyId;
        this.lastUserChange = lastUserChange;
        this.type = "Enabled";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Float getValue() {
        return value;
    }

    public void setValue(Float value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getLastUserChange() {
        return lastUserChange;
    }

    public void setLastUserChange(Integer lastUserChange) {
        this.lastUserChange = lastUserChange;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getCrCurrencyId() {
        return crCurrencyId;
    }

    public void setCrCurrencyId(Integer crCurrencyId) {
        this.crCurrencyId = crCurrencyId;
    }

    @Override
    public String toString() {
        return "ChangeRequestValue{" +
                "id=" + id +
                ", value=" + value +
                ", type='" + type + '\'' +
                ", crCurrencyId=" + crCurrencyId +
                ", createdAt=" + createdAt +
                '}';
    }
}
