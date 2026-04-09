package com.example.budg_v2.service;

import com.google.gson.JsonArray;

/**
 * Interface for entity processors
 * Each entity type should implement this interface to process its data
 */
public interface EntityProcessor {
    /**
     * Process validated data for an entity
     * 
     * @param jobId Job ID for tracking
     * @param validatedData Validated data from Python service
     * @param userId User ID performing the operation
     * @param errorHandling Error handling mode ("Continue on Error" or "Cancel on Warning")
     * @param uploadOption Upload option ("INSERT", "UPDATE", "DELETE")
     * @param segmentMode Segment mode (null, "Enterprise", "Multiple", "Specific")
     * @param segment Selected segment ID (if segmentMode is "Specific")
     */
    void process(int jobId, JsonArray validatedData, int userId,
                String errorHandling, String uploadOption, String segmentMode, String segment);
    
    /**
     * Get the entity name this processor handles
     * 
     * @return Entity name
     */
    String getEntityName();
    
    /**
     * Check if this processor supports the given entity name
     * 
     * @param entityName Entity name to check
     * @return true if supported, false otherwise
     */
    boolean supports(String entityName);
}

