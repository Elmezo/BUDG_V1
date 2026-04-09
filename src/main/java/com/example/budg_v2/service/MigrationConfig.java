package com.example.budg_v2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads bulk-migration configuration from migration.properties on the classpath.
 * Provides a single, centralized location for all migration-related settings so
 * that values like the Python validation service URL do not need to be hardcoded
 * in multiple places.
 */
public final class MigrationConfig {

    private static final Logger logger = LoggerFactory.getLogger(MigrationConfig.class);
    private static final String PROPERTIES_FILE = "/migration.properties";
    private static final String DEFAULT_PYTHON_SERVICE_URL = "http://localhost:8000/api/validate";

    private static volatile MigrationConfig instance;

    private final Properties props = new Properties();

    private MigrationConfig() {
        try (InputStream is = MigrationConfig.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                props.load(is);
                logger.info("Loaded migration configuration from {}", PROPERTIES_FILE);
            } else {
                logger.warn("migration.properties not found on classpath — using defaults");
            }
        } catch (IOException e) {
            logger.error("Failed to load migration.properties — using defaults", e);
        }
    }

    public static MigrationConfig getInstance() {
        if (instance == null) {
            synchronized (MigrationConfig.class) {
                if (instance == null) {
                    instance = new MigrationConfig();
                }
            }
        }
        return instance;
    }

    /**
     * Returns the Python validation microservice URL.
     * Defaults to {@code http://localhost:8000/api/validate} if not set.
     */
    public String getPythonServiceUrl() {
        return props.getProperty("migration.python-service.url", DEFAULT_PYTHON_SERVICE_URL);
    }
}
