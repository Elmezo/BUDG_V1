package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class MapLayer {
    
    @SerializedName("id")
    private Long id;
    
    @SerializedName("map_id")
    private Long mapId;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("type")
    private String type; // 'tile', 'marker', 'polygon', 'polyline', 'circle', 'rectangle', 'heatmap'
    
    @SerializedName("url_template")
    private String urlTemplate;
    
    @SerializedName("visible")
    private Boolean visible;
    
    @SerializedName("order_index")
    private Integer orderIndex;
    
    @SerializedName("style_json")
    private String styleJson; // Stored as JSON string, parsed when needed
    
    @SerializedName("created_at")
    private Timestamp createdAt;
    
    @SerializedName("updated_at")
    private Timestamp updatedAt;
    
    // Constructors
    public MapLayer() {}
    
    public MapLayer(Long id, Long mapId, String name, String type, String urlTemplate,
                    Boolean visible, Integer orderIndex, String styleJson,
                    Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.mapId = mapId;
        this.name = name;
        this.type = type;
        this.urlTemplate = urlTemplate;
        this.visible = visible;
        this.orderIndex = orderIndex;
        this.styleJson = styleJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getMapId() { return mapId; }
    public void setMapId(Long mapId) { this.mapId = mapId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getUrlTemplate() { return urlTemplate; }
    public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }
    
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    
    public String getStyleJson() { return styleJson; }
    public void setStyleJson(String styleJson) { this.styleJson = styleJson; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

