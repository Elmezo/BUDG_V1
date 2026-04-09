package com.example.budg_v2.bulk.relationships.config;

/**
 * Configuration for an entity involved in a relationship
 * Defines table structure and column mappings for entity lookup
 */
public class EntityConfig {
    private String name;              // e.g., "capability", "client"
    private String tableName;         // e.g., "capability", "client"
    private String idColumn;          // e.g., "ID"
    private String refColumn;         // e.g., "RefNumber" (nullable if entity doesn't have ref)
    private String nameColumn;        // e.g., "PrimaryName"
    private String parentColumn;      // e.g., "Parent_ID" (nullable)
    private String deletedColumn;     // e.g., "DeletedDatetime" (for soft delete check)
    private boolean required;         // Is this entity required for the relationship?
    
    public EntityConfig() {
    }
    
    public EntityConfig(String name, String tableName, String idColumn, String refColumn, 
                       String nameColumn, String parentColumn, String deletedColumn, boolean required) {
        this.name = name;
        this.tableName = tableName;
        this.idColumn = idColumn;
        this.refColumn = refColumn;
        this.nameColumn = nameColumn;
        this.parentColumn = parentColumn;
        this.deletedColumn = deletedColumn;
        this.required = required;
    }
    
    public boolean hasRefColumn() {
        return refColumn != null && !refColumn.isEmpty();
    }
    
    public boolean hasParentColumn() {
        return parentColumn != null && !parentColumn.isEmpty();
    }
    
    public boolean hasDeletedColumn() {
        return deletedColumn != null && !deletedColumn.isEmpty();
    }
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getIdColumn() {
        return idColumn;
    }
    
    public void setIdColumn(String idColumn) {
        this.idColumn = idColumn;
    }
    
    public String getRefColumn() {
        return refColumn;
    }
    
    public void setRefColumn(String refColumn) {
        this.refColumn = refColumn;
    }
    
    public String getNameColumn() {
        return nameColumn;
    }
    
    public void setNameColumn(String nameColumn) {
        this.nameColumn = nameColumn;
    }
    
    public String getParentColumn() {
        return parentColumn;
    }
    
    public void setParentColumn(String parentColumn) {
        this.parentColumn = parentColumn;
    }
    
    public String getDeletedColumn() {
        return deletedColumn;
    }
    
    public void setDeletedColumn(String deletedColumn) {
        this.deletedColumn = deletedColumn;
    }
    
    public boolean isRequired() {
        return required;
    }
    
    public void setRequired(boolean required) {
        this.required = required;
    }
    
    @Override
    public String toString() {
        return "EntityConfig{" +
                "name='" + name + '\'' +
                ", tableName='" + tableName + '\'' +
                ", required=" + required +
                '}';
    }
}

