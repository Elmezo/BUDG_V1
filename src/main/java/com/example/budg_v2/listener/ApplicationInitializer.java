package com.example.budg_v2.listener;

import com.example.budg_v2.dao.ProcessDefinitionObjectScopeDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.AxonLogger;
import com.example.budg_v2.util.DefaultWorkflowInitializer;
import com.example.budg_v2.util.DistributedRateLimiter;
import com.example.budg_v2.util.EnvFileLoader;
import com.example.budg_v2.util.HttpClientUtil;
import com.example.unisonsearch.service.UnisonSchemaInitializer;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Application initializer that runs on startup
 * Initializes database tables and other required resources
 * 
 * SECURITY: Requires JWT_SECRET_KEY to be set before startup
 */
@WebListener
public class ApplicationInitializer implements ServletContextListener {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationInitializer.class);
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("Initializing BUDG Platform application...");
        
        // Test logging system - write test logs to ensure files are created
        try {
            AxonLogger.logInfo("Application startup initiated",
                AxonLogger.kv("component", "BUDG-core"),
                AxonLogger.kv("module", "ApplicationInitializer"),
                AxonLogger.kv("action", "contextInitialized"),
                AxonLogger.kv("event", "application_startup")
            );
            
            // Test error log (using DEBUG level for test logs, not ERROR)
            AxonLogger.logDebug("Test error log - this is expected during startup",
                AxonLogger.kv("component", "BUDG-core"),
                AxonLogger.kv("module", "ApplicationInitializer"),
                AxonLogger.kv("action", "test_logging"),
                AxonLogger.kv("severity", "test")
            );
            
            // Test audit log
            AxonLogger.logAudit("Application startup audit log",
                AxonLogger.kv("component", "BUDG-core"),
                AxonLogger.kv("module", "ApplicationInitializer"),
                AxonLogger.kv("action", "startup_audit"),
                AxonLogger.kv("event_type", "application_startup")
            );
            
            logger.info("Test logs written successfully - logging system is operational");
        } catch (Exception e) {
            logger.error("Failed to write test logs", e);
        }
        
        // Load .env file if it exists (before checking for required variables)
        EnvFileLoader.loadEnvFile();
        
        // Validate JWT_SECRET_KEY is set (required for security)
        validateJwtSecretKey();

        DatabaseConnection.verifyJdbcCredentialsConfigured();
        validateBulkValidationApiKey();
        
        try {
            // Initialize distributed rate limiting table
            DistributedRateLimiter.initializeTable();
            logger.info("Distributed rate limiting initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize distributed rate limiting", e);
            // Don't fail startup - rate limiting will work but may have issues
        }
        
        // Ensure Enterprise segment exists and is not deleted
        // Per BUDG docs: "Enterprise segment is a public segment that all users can access"
        try {
            SegmentDAO segmentDAO = new SegmentDAO();
            segmentDAO.ensureEnterpriseSegment();
            logger.info("Enterprise segment verification completed");
        } catch (Exception e) {
            logger.error("Failed to ensure Enterprise segment exists", e);
            // Don't fail startup - segment functionality may have issues
        }
        
        // Ensure process_definition_id column exists in changerequest table
        try {
            ensureChangeRequestProcessDefinitionIdColumn();
            logger.info("Change request process_definition_id column verification completed");
        } catch (Exception e) {
            logger.error("Failed to ensure process_definition_id column exists in changerequest table", e);
            // Don't fail startup - workflow linking may have issues
        }

        // Object-private workflow scope table
        try (Connection conn = DatabaseConnection.getConnection()) {
            ProcessDefinitionObjectScopeDAO.ensureTableExists(conn);
            logger.info("process_definition_object_scope table verification completed");
        } catch (Exception e) {
            logger.error("Failed to ensure process_definition_object_scope table exists", e);
        }
        
        // Initialize default workflows for all facets
        try {
            DefaultWorkflowInitializer.initialize();
            logger.info("Default workflows initialization completed");
        } catch (Exception e) {
            logger.error("Failed to initialize default workflows", e);
            // Don't fail startup - workflows can be created manually later
        }

        // Ensure Unison tables (i_user, unison, unison_facets) and UNISON_DEFAULTS exist and are seeded
        try {
            UnisonSchemaInitializer.ensureUnisonTablesAndDefaults();
            logger.info("Unison schema and defaults initialization completed");
        } catch (Exception e) {
            logger.error("Failed to initialize Unison schema and defaults", e);
            // Don't fail startup - Unison search may work if tables already exist
        }

        logger.info("Application initialization completed");
    }
    
    /**
     * Ensure process_definition_id column exists in changerequest table
     */
    private void ensureChangeRequestProcessDefinitionIdColumn() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if column exists
            boolean columnExists = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "changerequest", "process_definition_id")) {
                columnExists = rs.next();
            } catch (SQLException e) {
                logger.debug("Could not check for process_definition_id column: {}", e.getMessage());
            }
            
            if (!columnExists) {
                // Add the column
                String sql = "ALTER TABLE changerequest ADD COLUMN process_definition_id INT NULL";
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                    logger.info("Added process_definition_id column to changerequest table");
                } catch (SQLException e) {
                    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (msg.contains("duplicate column") || msg.contains("already exists")) {
                        // Column was added by another thread/request, ignore
                        logger.debug("process_definition_id column already exists");
                    } else {
                        logger.warn("Could not add process_definition_id column to changerequest table: {}", e.getMessage());
                        throw e;
                    }
                }
            } else {
                logger.debug("process_definition_id column already exists in changerequest table");
            }
        } catch (SQLException e) {
            logger.error("Error ensuring process_definition_id column exists: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to ensure process_definition_id column exists", e);
        }
    }
    
    /**
     * Validate that JWT_SECRET_KEY is set. Fail startup if not found.
     * Checks:
     * 1. System environment variable
     * 2. System property (may be set from .env file)
     * 
     * @throws IllegalStateException if JWT_SECRET_KEY is not set or is too short
     */
    private void validateJwtSecretKey() {
        String jwtSecret = System.getenv("JWT_SECRET_KEY");
        
        // Check system property if env var not found (may be loaded from .env)
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            jwtSecret = System.getProperty("JWT_SECRET_KEY");
        }
        
        // Fail if still not found
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            String errorMsg = 
                "\n" +
                "═══════════════════════════════════════════════════════════════\n" +
                "  CRITICAL ERROR: JWT_SECRET_KEY is not configured!\n" +
                "═══════════════════════════════════════════════════════════════\n" +
                "\n" +
                "For security, JWT_SECRET_KEY must be set before starting the application.\n" +
                "\n" +
                "Options to set JWT_SECRET_KEY:\n" +
                "  1. Create a .env file in the project root with:\n" +
                "     JWT_SECRET_KEY=your-secret-key-here-at-least-32-characters\n" +
                "\n" +
                "  2. Set as environment variable:\n" +
                "     Windows PowerShell: $env:JWT_SECRET_KEY = \"your-key\"\n" +
                "     Linux/Mac: export JWT_SECRET_KEY=\"your-key\"\n" +
                "\n" +
                "  3. Run the setup script:\n" +
                "     .\\setup-jwt-secret.ps1\n" +
                "\n" +
                "Generate a secure key (64+ characters recommended):\n" +
                "  PowerShell: [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))\n" +
                "  Linux/Mac: openssl rand -base64 64\n" +
                "\n" +
                "See env.example for template.\n" +
                "═══════════════════════════════════════════════════════════════\n";
            
            logger.error(errorMsg);
            throw new IllegalStateException("JWT_SECRET_KEY is required but not configured. Application cannot start.");
        }
        
        // Validate key length (minimum 32 bytes = 256 bits)
        byte[] keyBytes = jwtSecret.getBytes();
        if (keyBytes.length < 32) {
            String errorMsg =
                "\n" +
                "═══════════════════════════════════════════════════════════════\n" +
                "  CRITICAL ERROR: JWT_SECRET_KEY is too short!\n" +
                "═══════════════════════════════════════════════════════════════\n" +
                "\n" +
                "JWT_SECRET_KEY must be at least 32 bytes (256 bits) for security.\n" +
                "Configured key is below the minimum required length.\n" +
                "\n" +
                "Please generate a longer key (64+ bytes recommended).\n" +
                "═══════════════════════════════════════════════════════════════\n";
            logger.error(errorMsg);
            throw new IllegalStateException("JWT_SECRET_KEY must be at least 32 bytes (256 bits)");
        }
        
        logger.info("JWT_SECRET_KEY validated successfully");
    }

    /**
     * Shared secret for the Python bulk validation service — must match {@code BULK_VALIDATION_API_KEY} there
     * and be sent by Java as {@code X-API-Key} on {@code /api/validate} requests.
     */
    private void validateBulkValidationApiKey() {
        String key = System.getenv("BULK_VALIDATION_API_KEY");
        if (key == null || key.trim().isEmpty()) {
            key = System.getProperty("BULK_VALIDATION_API_KEY");
        }
        if (key != null) {
            key = key.trim();
        }
        if (key == null || key.isEmpty()) {
            key = HttpClientUtil.BULK_VALIDATION_API_KEY_DEV_DEFAULT;
            System.setProperty("BULK_VALIDATION_API_KEY", key);
            logger.warn(
                    "BULK_VALIDATION_API_KEY not set; using dev default (same as env.example / Python). "
                            + "Set BULK_VALIDATION_API_KEY in .env for production.");
        }
        if (key.length() < 16) {
            throw new IllegalStateException("BULK_VALIDATION_API_KEY must be at least 16 characters.");
        }
        logger.info("BULK_VALIDATION_API_KEY is configured (length: {} characters)", key.length());
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("Shutting down BUDG Platform application...");
    }
}

