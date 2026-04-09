package com.example.budg_v2.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for entity processing rules
 * Loads configuration from JSON file and provides entity-specific settings
 */
public class EntityProcessingConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(EntityProcessingConfig.class);
    private static final String CONFIG_FILE = "/entity-processing-config.json";
    
    private static volatile EntityProcessingConfig instance;
    private final Map<String, EntityConfig> entityConfigs = new HashMap<>();
    private final Gson gson = new Gson();
    
    private EntityProcessingConfig() {
        loadConfiguration();
    }
    
    /**
     * Get singleton instance
     */
    public static EntityProcessingConfig getInstance() {
        if (instance == null) {
            synchronized (EntityProcessingConfig.class) {
                if (instance == null) {
                    instance = new EntityProcessingConfig();
                }
            }
        }
        return instance;
    }
    
    /**
     * Load configuration from JSON file
     */
    private void loadConfiguration() {
        try (InputStream is = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                logger.warn("Configuration file {} not found, using defaults", CONFIG_FILE);
                loadDefaultConfiguration();
                return;
            }
            
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonObject config = gson.fromJson(reader, JsonObject.class);
                parseConfiguration(config);
                logger.info("Loaded entity processing configuration for {} entities", entityConfigs.size());
            }
        } catch (Exception e) {
            logger.error("Error loading configuration file: {}", e.getMessage(), e);
            loadDefaultConfiguration();
        }
    }
    
    /**
     * Parse configuration JSON
     */
    private void parseConfiguration(JsonObject config) {
        if (config.has("entities")) {
            var entities = config.getAsJsonObject("entities");
            for (String entityName : entities.keySet()) {
                JsonObject entityConfig = entities.getAsJsonObject(entityName);
                EntityConfig configObj = new EntityConfig();
                configObj.entityName = entityName;
                configObj.processorClass = entityConfig.has("processorClass") ? 
                    entityConfig.get("processorClass").getAsString() : null;
                configObj.errorHandling = entityConfig.has("errorHandling") ? 
                    entityConfig.get("errorHandling").getAsString() : "Continue on Error";
                configObj.retryOnError = entityConfig.has("retryOnError") ? 
                    entityConfig.get("retryOnError").getAsBoolean() : false;
                configObj.maxRetries = entityConfig.has("maxRetries") ? 
                    entityConfig.get("maxRetries").getAsInt() : 0;
                entityConfigs.put(entityName.toLowerCase(), configObj);
            }
        }
    }
    
    /**
     * Load default configuration
     */
    private void loadDefaultConfiguration() {
        // Default configurations for known entities
        EntityConfig systemConfig = new EntityConfig();
        systemConfig.entityName = "System";
        systemConfig.errorHandling = "Continue on Error";
        systemConfig.retryOnError = true;
        systemConfig.maxRetries = 3;
        entityConfigs.put("system", systemConfig);
        
        EntityConfig datasetConfig = new EntityConfig();
        datasetConfig.entityName = "Dataset";
        datasetConfig.errorHandling = "Continue on Error";
        datasetConfig.retryOnError = true;
        datasetConfig.maxRetries = 3;
        entityConfigs.put("dataset", datasetConfig);
        
        // Add more defaults as needed
    }
    
    /**
     * Get configuration for an entity
     */
    public EntityConfig getConfig(String entityName) {
        if (entityName == null) {
            return getDefaultConfig();
        }
        return entityConfigs.getOrDefault(entityName.toLowerCase(), getDefaultConfig());
    }
    
    /**
     * Get default configuration
     */
    private EntityConfig getDefaultConfig() {
        EntityConfig defaultConfig = new EntityConfig();
        defaultConfig.entityName = "Unknown";
        defaultConfig.errorHandling = "Continue on Error";
        defaultConfig.retryOnError = false;
        defaultConfig.maxRetries = 0;
        return defaultConfig;
    }
    
    /**
     * Entity configuration
     */
    public static class EntityConfig {
        public String entityName;
        public String processorClass;
        public String errorHandling;
        public boolean retryOnError;
        public int maxRetries;
    }
}

