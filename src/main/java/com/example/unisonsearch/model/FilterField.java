package com.example.unisonsearch.model;

import java.util.Map;

/**
 * Represents a filter field configuration for a facet.
 */
public class FilterField {
    private String id;
    private String name;
    private FilterType type;
    private String lookupTable;
    private String fieldName;
    private boolean multiSelect;
    private Map<String, String> staticValues; // For enums without lookup tables (e.g., RAG Status)
    
    public FilterField() {
    }
    
    public FilterField(String id, String name, FilterType type, String lookupTable) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.lookupTable = lookupTable;
        this.multiSelect = true; // Default for dropdowns
    }
    
    public FilterField(String id, String name, FilterType type, String lookupTable, String fieldName) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.lookupTable = lookupTable;
        this.fieldName = fieldName;
        this.multiSelect = true;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public FilterType getType() {
        return type;
    }
    
    public void setType(FilterType type) {
        this.type = type;
    }
    
    public String getLookupTable() {
        return lookupTable;
    }
    
    public void setLookupTable(String lookupTable) {
        this.lookupTable = lookupTable;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public boolean isMultiSelect() {
        return multiSelect;
    }
    
    public void setMultiSelect(boolean multiSelect) {
        this.multiSelect = multiSelect;
    }
    
    public Map<String, String> getStaticValues() {
        return staticValues;
    }
    
    public void setStaticValues(Map<String, String> staticValues) {
        this.staticValues = staticValues;
    }
}
