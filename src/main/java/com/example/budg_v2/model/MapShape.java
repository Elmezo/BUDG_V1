package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class MapShape {
    
    @SerializedName("id")
    private Long id;
    
    @SerializedName("layer_id")
    private Long layerId;
    
    @SerializedName("type")
    private String type; // 'polygon', 'polyline', 'circle', 'rectangle'
    
    @SerializedName("coordinates_json")
    private String coordinatesJson; // GeoJSON format stored as string
    
    @SerializedName("style_json")
    private String styleJson; // Stored as JSON string
    
    @SerializedName("metadata_json")
    private String metadataJson; // Stored as JSON string
    
    @SerializedName("created_by")
    private Long createdBy;
    
    @SerializedName("created_at")
    private Timestamp createdAt;
    
    @SerializedName("updated_at")
    private Timestamp updatedAt;
    
    // Constructors
    public MapShape() {}
    
    public MapShape(Long id, Long layerId, String type, String coordinatesJson,
                    String styleJson, String metadataJson, Long createdBy,
                    Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.layerId = layerId;
        this.type = type;
        this.coordinatesJson = coordinatesJson;
        this.styleJson = styleJson;
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
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getCoordinatesJson() { return coordinatesJson; }
    public void setCoordinatesJson(String coordinatesJson) { this.coordinatesJson = coordinatesJson; }
    
    public String getStyleJson() { return styleJson; }
    public void setStyleJson(String styleJson) { this.styleJson = styleJson; }
    
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

