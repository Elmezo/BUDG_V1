package com.example.budg_v2.util;

import com.example.budg_v2.service.DefaultWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to initialize default workflows on application startup
 */
public class DefaultWorkflowInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultWorkflowInitializer.class);
    private static boolean initialized = false;
    
    /**
     * Initialize default workflows for all facets
     * This should be called once during application startup
     */
    public static void initialize() {
        if (initialized) {
            logger.debug("Default workflows already initialized, skipping...");
            return;
        }
        
        synchronized (DefaultWorkflowInitializer.class) {
            if (initialized) {
                return;
            }
            
            try {
                logger.info("Initializing default workflows...");
                DefaultWorkflowService service = new DefaultWorkflowService();
                service.createDefaultWorkflowsForAllFacets();
                initialized = true;
                logger.info("Default workflows initialization completed");
            } catch (Exception e) {
                logger.error("Failed to initialize default workflows", e);
                // Don't fail startup - workflows can be created manually later
            }
        }
    }
}

