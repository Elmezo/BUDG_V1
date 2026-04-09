package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class Map {
    
    @SerializedName("id")
    private Long id;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("default_center_lat")
    private java.math.BigDecimal defaultCenterLat;
    
    @SerializedName("default_center_lng")
    private java.math.BigDecimal defaultCenterLng;
    
    @SerializedName("default_zoom")
    private Integer defaultZoom;
    
    @SerializedName("enabled")
    private Boolean enabled;
    
    @SerializedName("created_at")
    private Timestamp createdAt;
    
    @SerializedName("updated_at")
    private Timestamp updatedAt;
    
    @SerializedName("created_by")
    private Long createdBy;
    
    // Constructors
    public Map() {}
    
    public Map(Long id, String name, String description, java.math.BigDecimal defaultCenterLat,
               java.math.BigDecimal defaultCenterLng, Integer defaultZoom, Boolean enabled,
               Timestamp createdAt, Timestamp updatedAt, Long createdBy) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultCenterLat = defaultCenterLat;
        this.defaultCenterLng = defaultCenterLng;
        this.defaultZoom = defaultZoom;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public java.math.BigDecimal getDefaultCenterLat() { return defaultCenterLat; }
    public void setDefaultCenterLat(java.math.BigDecimal defaultCenterLat) { this.defaultCenterLat = defaultCenterLat; }
    
    public java.math.BigDecimal getDefaultCenterLng() { return defaultCenterLng; }
    public void setDefaultCenterLng(java.math.BigDecimal defaultCenterLng) { this.defaultCenterLng = defaultCenterLng; }
    
    public Integer getDefaultZoom() { return defaultZoom; }
    public void setDefaultZoom(Integer defaultZoom) { this.defaultZoom = defaultZoom; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}

