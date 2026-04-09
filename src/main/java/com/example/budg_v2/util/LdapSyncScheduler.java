package com.example.budg_v2.util;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.service.LdapSettingsService;
import com.example.budg_v2.service.LdapSyncService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job for automatic LDAP synchronization
 * Runs at configured intervals to sync LDAP users with the database
 * 
 * Pattern: Similar to EmailBatchScheduler and ChangeRequestDeletionScheduler
 */
@WebListener
public class LdapSyncScheduler implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(LdapSyncScheduler.class);
    private static final String SETTINGS_GROUP = "LDAP Settings";
    private static final int DEFAULT_INTERVAL_MINUTES = 60;
    private static final int MIN_INTERVAL_MINUTES = 15; // Minimum interval to prevent overload
    
    private final LdapSettingsService ldapSettingsService;
    private final LdapSyncService ldapSyncService;
    private final SystemSettingsDAO systemSettingsDAO;
    private final JobDAO jobDAO;
    private ScheduledExecutorService scheduler;

    public LdapSyncScheduler() {
        this.ldapSettingsService = new LdapSettingsService();
        this.ldapSyncService = new LdapSyncService();
        this.systemSettingsDAO = new SystemSettingsDAO();
        this.jobDAO = new JobDAO();
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        stop();
    }

    /**
     * Start the LDAP sync scheduler
     * Loads settings from database and schedules sync if enabled
     */
    private void start() {
        try {
            // Load scheduler settings
            Map<String, Object> settings = systemSettingsDAO.getSettingsByGroup(SETTINGS_GROUP);
            boolean enabled = getBooleanValue(settings, "scheduledSyncEnabled", false);
            int intervalMinutes = getIntValue(settings, "scheduledSyncIntervalMinutes", DEFAULT_INTERVAL_MINUTES);
            
            // Enforce minimum interval
            if (intervalMinutes < MIN_INTERVAL_MINUTES) {
                logger.warn("Scheduled sync interval {} minutes is below minimum {}, using minimum", 
                    intervalMinutes, MIN_INTERVAL_MINUTES);
                intervalMinutes = MIN_INTERVAL_MINUTES;
            }
            
            if (!enabled) {
                logger.info("LDAP scheduled sync is disabled - scheduler not started");
                return;
            }
            
            if (scheduler != null && !scheduler.isShutdown()) {
                logger.warn("LDAP sync scheduler is already running");
                return;
            }

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LdapSyncScheduler");
                t.setDaemon(true);
                return t;
            });

            // Schedule first run after initial delay (1 minute), then repeat at interval
            scheduler.scheduleAtFixedRate(
                this::runScheduledSync,
                1, // Initial delay: 1 minute
                intervalMinutes,
                TimeUnit.MINUTES
            );

            logger.info("LDAP sync scheduler started with interval: {} minutes (next run in 1 minute)", intervalMinutes);
            
        } catch (Exception e) {
            logger.error("Failed to start LDAP sync scheduler", e);
        }
    }

    /**
     * Stop the LDAP sync scheduler
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("LDAP sync scheduler stopped");
        }
    }

    /**
     * Run scheduled LDAP sync
     * Checks if sync is still enabled and creates a job to run sync
     */
    private void runScheduledSync() {
        try {
            // Check if sync is still enabled
            Map<String, Object> settings = systemSettingsDAO.getSettingsByGroup(SETTINGS_GROUP);
            boolean enabled = getBooleanValue(settings, "scheduledSyncEnabled", false);
            
            if (!enabled) {
                logger.debug("LDAP scheduled sync is disabled - skipping scheduled run");
                return;
            }
            
            // Check if LDAP is enabled
            if (!ldapSettingsService.getLdapSettings().isLdapEnabled()) {
                logger.debug("LDAP is not enabled - skipping scheduled sync");
                return;
            }
            
            logger.info("Starting scheduled LDAP synchronization");
            
            // Create a job for tracking
            int jobId;
            try {
                jobId = jobDAO.createJob("LDAP Scheduled Sync", "Scheduled", null, "Pending", 1); // User ID 1 = system
            } catch (SQLException e) {
                logger.error("Failed to create job for scheduled LDAP sync", e);
                return;
            }
            
            // Run sync in background thread to avoid blocking scheduler
            scheduler.execute(() -> {
                try {
                    ldapSyncService.synchronize(1, jobId); // User ID 1 = system
                    logger.info("Scheduled LDAP synchronization completed (jobId: {})", jobId);
                } catch (Exception e) {
                    logger.error("Error during scheduled LDAP synchronization (jobId: {})", jobId, e);
                    try {
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                    } catch (SQLException sqlEx) {
                        logger.error("Failed to update job status after error", sqlEx);
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Error in scheduled LDAP sync task", e);
            // Don't crash the scheduler - log and continue
        }
    }

    // Helper methods
    private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString()) || "1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString());
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

