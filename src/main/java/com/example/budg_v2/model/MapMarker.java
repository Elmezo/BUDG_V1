package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class MapMarker {
    
    @SerializedName("id")
    private Long id;
    
    @SerializedName("layer_id")
    private Long layerId;
    
    @SerializedName("lat")
    private java.math.BigDecimal lat;
    
    @SerializedName("lng")
    private java.math.BigDecimal lng;
    
    @SerializedName("title")
    private String title;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("icon_url")
    private String iconUrl;
    
    @SerializedName("metadata_json")
    private String metadataJson; // Stored as JSON string
    
    @SerializedName("created_by")
    private Long createdBy;
    
    @SerializedName("created_at")
    private Timestamp createdAt;
    
    @SerializedName("updated_at")
    private Timestamp updatedAt;
    
    // Constructors
    public MapMarker() {}
    
    public MapMarker(Long id, Long layerId, java.math.BigDecimal lat, java.math.BigDecimal lng,
                     String title, String description, String iconUrl, String metadataJson,
                     Long createdBy, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.layerId = layerId;
        this.lat = lat;
        this.lng = lng;
        this.title = title;
        this.description = description;
        this.iconUrl = iconUrl;
        this.metadataJson = metadataJson;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getLayerId() { return layerId; }
    public void setLayerId(Long layerId) { this.layerId = layerId; }
    
    public java.math.BigDecimal getLat() { return lat; }
    public void setLat(java.math.BigDecimal lat) { this.lat = lat; }
    
    public java.math.BigDecimal getLng() { return lng; }
    public void setLng(java.math.BigDecimal lng) { this.lng = lng; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

