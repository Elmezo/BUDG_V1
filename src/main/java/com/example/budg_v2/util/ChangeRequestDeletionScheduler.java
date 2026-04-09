package com.example.budg_v2.util;

import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.model.SystemSettings;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job for deleting change requests that are scheduled for deletion
 * Runs daily at 12:00 AM server time to delete CRs whose scheduled_delete_at timestamp has passed
 */
@WebListener
public class ChangeRequestDeletionScheduler implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestDeletionScheduler.class);
    private final SystemSettingsDAO systemSettingsDAO = new SystemSettingsDAO();
    private final ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
    private ScheduledExecutorService scheduler;
    
    // Daily interval in seconds (24 hours)
    private static final long DAILY_INTERVAL_SECONDS = 24 * 60 * 60;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        stop();
    }

    /**
     * Start the change request deletion scheduler
     * Calculates delay until next 12:00 AM and schedules daily execution
     */
    private void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.warn("Change request deletion scheduler is already running");
            return;
        }

        try {
            // Check if automatic deletion is enabled
            SystemSettings autoDeleteSetting = systemSettingsDAO.getSetting("Change Requests", "enable_automatic_deletion");
            if (autoDeleteSetting == null || !"true".equalsIgnoreCase(autoDeleteSetting.getSettingValue())) {
                logger.info("Change request automatic deletion is disabled - scheduler not started");
                return;
            }

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "change-request-deletion-scheduler");
                t.setDaemon(true);
                return t;
            });

            // Calculate delay until next 12:00 AM
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextMidnight = now.toLocalDate().atStartOfDay().plusDays(1);
            long delaySeconds = ChronoUnit.SECONDS.between(now, nextMidnight);

            // Schedule first run at midnight, then repeat daily
            scheduler.scheduleAtFixedRate(
                this::processScheduledDeletions,
                delaySeconds,
                DAILY_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            );

            logger.info("Change request deletion scheduler started - will run daily at 12:00 AM (next run in {} seconds)", delaySeconds);

        } catch (Exception e) {
            logger.error("Failed to start change request deletion scheduler", e);
        }
    }

    /**
     * Stop the change request deletion scheduler
     */
    private void stop() {
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
            logger.info("Change request deletion scheduler stopped");
        }
    }

    /**
     * Process scheduled deletions - delete CRs whose scheduled_delete_at has passed
     */
    private void processScheduledDeletions() {
        try {
            // Check if automatic deletion is still enabled
            SystemSettings autoDeleteSetting = systemSettingsDAO.getSetting("Change Requests", "enable_automatic_deletion");
            if (autoDeleteSetting == null || !"true".equalsIgnoreCase(autoDeleteSetting.getSettingValue())) {
                logger.debug("Change request automatic deletion is disabled - skipping scheduled deletion");
                return;
            }

            logger.info("Starting scheduled change request deletion");

            // Delete CRs where scheduled_delete_at <= NOW() and Deleted_At IS NULL
            int deletedCount = changeRequestDAO.deleteScheduledChangeRequests();

            if (deletedCount > 0) {
                logger.info("Scheduled change request deletion completed - deleted {} change request(s)", deletedCount);
            } else {
                logger.debug("Scheduled change request deletion completed - no change requests to delete");
            }

        } catch (SQLException e) {
            logger.error("Error during scheduled change request deletion", e);
        } catch (Exception e) {
            logger.error("Unexpected error during scheduled change request deletion", e);
        }
    }
}

