package com.example.budg_v2.bulk.relationships.config;

/**
 * Configuration for a relationship type
 * Defines relationship table structure, entities involved, and supported operations
 */
public class RelationshipConfig {
    private String key;                      // e.g., "capabilityxclient"
    private String tableName;                // e.g., "capability_x_client"
    private String displayName;              // e.g., "Capability X Client"
    
    private EntityConfig entityA;            // Configuration for first entity
    private EntityConfig entityB;            // Configuration for second entity
    
    private String relationTypeTable;        // e.g., "capability_x_client_relationtype" (nullable)
    private String relationTypeColumn;       // e.g., "RelationType_ID" (nullable)
    private boolean requiresRelationType;    // Some relationships don't have type
    
    private String descriptionColumn;        // Optional description field
    private String[] additionalColumns;      // Other columns to populate
    
    private String entityAIdColumn;          // e.g., "Capability_ID"
    private String entityBIdColumn;          // e.g., "Client_ID"
    
    /** Optional role for Entity A when same type as B (e.g. "source" for Source/Target columns). */
    private String entityARole;
    /** Optional role for Entity B when same type as A (e.g. "target" for Source/Target columns). */
    private String entityBRole;
    
    private boolean supportsInsert;
    private boolean supportsDelete;
    private boolean supportsUpdate;
    
    public RelationshipConfig() {
        this.additionalColumns = new String[0];
    }
    
    public RelationshipConfig(String key, String tableName, String displayName,
                             EntityConfig entityA, EntityConfig entityB,
                             String entityAIdColumn, String entityBIdColumn,
                             String relationTypeTable, String relationTypeColumn,
                             boolean requiresRelationType,
                             String descriptionColumn, String[] additionalColumns,
                             boolean supportsInsert, boolean supportsDelete, boolean supportsUpdate) {
        this.key = key;
        this.tableName = tableName;
        this.displayName = displayName;
        this.entityA = entityA;
        this.entityB = entityB;
        this.entityAIdColumn = entityAIdColumn;
        this.entityBIdColumn = entityBIdColumn;
        this.relationTypeTable = relationTypeTable;
        this.relationTypeColumn = relationTypeColumn;
        this.requiresRelationType = requiresRelationType;
        this.descriptionColumn = descriptionColumn;
        this.additionalColumns = additionalColumns != null ? additionalColumns : new String[0];
        this.supportsInsert = supportsInsert;
        this.supportsDelete = supportsDelete;
        this.supportsUpdate = supportsUpdate;
    }
    
    /**
     * Constructor with optional entity roles for same-entity relationships (e.g. Source/Target).
     * When both entities are the same type, entityARole/entityBRole (e.g. "source", "target")
     * are used to match the correct Excel columns.
     */
    public RelationshipConfig(String key, String tableName, String displayName,
                             EntityConfig entityA, EntityConfig entityB,
                             String entityAIdColumn, String entityBIdColumn,
                             String relationTypeTable, String relationTypeColumn,
                             boolean requiresRelationType,
                             String descriptionColumn, String[] additionalColumns,
                             boolean supportsInsert, boolean supportsDelete, boolean supportsUpdate,
                             String entityARole, String entityBRole) {
        this(key, tableName, displayName, entityA, entityB, entityAIdColumn, entityBIdColumn,
                relationTypeTable, relationTypeColumn, requiresRelationType, descriptionColumn,
                additionalColumns, supportsInsert, supportsDelete, supportsUpdate);
        this.entityARole = entityARole;
        this.entityBRole = entityBRole;
    }
    
    public boolean hasRelationType() {
        return relationTypeTable != null && !relationTypeTable.isEmpty();
    }
    
    public boolean hasDescription() {
        return descriptionColumn != null && !descriptionColumn.isEmpty();
    }
    
    public boolean supportsOperation(String operation) {
        if (operation == null) return false;
        switch (operation.toUpperCase()) {
            case "INSERT":
                return supportsInsert;
            case "DELETE":
                return supportsDelete;
            case "UPDATE":
                return supportsUpdate;
            default:
                return false;
        }
    }
    
    // Getters and Setters
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public EntityConfig getEntityA() {
        return entityA;
    }
    
    public void setEntityA(EntityConfig entityA) {
        this.entityA = entityA;
    }
    
    public EntityConfig getEntityB() {
        return entityB;
    }
    
    public void setEntityB(EntityConfig entityB) {
        this.entityB = entityB;
    }
    
    public String getRelationTypeTable() {
        return relationTypeTable;
    }
    
    public void setRelationTypeTable(String relationTypeTable) {
        this.relationTypeTable = relationTypeTable;
    }
    
    public String getRelationTypeColumn() {
        return relationTypeColumn;
    }
    
    public void setRelationTypeColumn(String relationTypeColumn) {
        this.relationTypeColumn = relationTypeColumn;
    }
    
    public boolean isRequiresRelationType() {
        return requiresRelationType;
    }
    
    public void setRequiresRelationType(boolean requiresRelationType) {
        this.requiresRelationType = requiresRelationType;
    }
    
    public String getDescriptionColumn() {
        return descriptionColumn;
    }
    
    public void setDescriptionColumn(String descriptionColumn) {
        this.descriptionColumn = descriptionColumn;
    }
    
    public String[] getAdditionalColumns() {
        return additionalColumns;
    }
    
    public void setAdditionalColumns(String[] additionalColumns) {
        this.additionalColumns = additionalColumns;
    }
    
    public String getEntityAIdColumn() {
        return entityAIdColumn;
    }
    
    public void setEntityAIdColumn(String entityAIdColumn) {
        this.entityAIdColumn = entityAIdColumn;
    }
    
    public String getEntityBIdColumn() {
        return entityBIdColumn;
    }
    
    public void setEntityBIdColumn(String entityBIdColumn) {
        this.entityBIdColumn = entityBIdColumn;
    }
    
    public String getEntityARole() {
        return entityARole;
    }
    
    public void setEntityARole(String entityARole) {
        this.entityARole = entityARole;
    }
    
    public String getEntityBRole() {
        return entityBRole;
    }
    
    public void setEntityBRole(String entityBRole) {
        this.entityBRole = entityBRole;
    }
    
    public boolean isSupportsInsert() {
        return supportsInsert;
    }
    
    public void setSupportsInsert(boolean supportsInsert) {
        this.supportsInsert = supportsInsert;
    }
    
    public boolean isSupportsDelete() {
        return supportsDelete;
    }
    
    public void setSupportsDelete(boolean supportsDelete) {
        this.supportsDelete = supportsDelete;
    }
    
    public boolean isSupportsUpdate() {
        return supportsUpdate;
    }
    
    public void setSupportsUpdate(boolean supportsUpdate) {
        this.supportsUpdate = supportsUpdate;
    }
    
    @Override
    public String toString() {
        return "RelationshipConfig{" +
                "key='" + key + '\'' +
                ", tableName='" + tableName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", requiresRelationType=" + requiresRelationType +
                ", supportsInsert=" + supportsInsert +
                ", supportsDelete=" + supportsDelete +
                ", supportsUpdate=" + supportsUpdate +
                '}';
    }
}

