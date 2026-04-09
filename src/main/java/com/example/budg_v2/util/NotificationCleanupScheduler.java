package com.example.budg_v2.util;

import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job for clearing old notifications
 * Runs daily at 12:00 AM server time to delete notifications older than the configured number of days
 * Only clears notifications for categories: catalog, workflow, bulk_upload
 * Does NOT clear roles notifications
 */
@WebListener
public class NotificationCleanupScheduler implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationCleanupScheduler.class);
    private final SystemSettingsDAO systemSettingsDAO = new SystemSettingsDAO();
    private final WorkflowNotificationDAO notificationDAO = new WorkflowNotificationDAO();
    private ScheduledExecutorService scheduler;
    
    // Categories to clear (roles is excluded as per requirements)
    private static final List<String> CLEARABLE_CATEGORIES = Arrays.asList("catalog", "workflow", "bulk_upload");
    
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
     * Start the notification cleanup scheduler
     * Calculates delay until next 12:00 AM and schedules daily execution
     */
    private void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.warn("Notification cleanup scheduler is already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NotificationCleanupScheduler");
            t.setDaemon(true);
            return t;
        });

        // Calculate delay until next 12:00 AM
        long delaySeconds = calculateDelayUntilMidnight();
        
        logger.info("Notification cleanup scheduler will start in {} seconds (at 12:00 AM)", delaySeconds);

        // Schedule first execution at next midnight, then repeat daily
        scheduler.scheduleAtFixedRate(
            this::cleanupOldNotifications,
            delaySeconds,
            DAILY_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        logger.info("Notification cleanup scheduler started - will run daily at 12:00 AM");
    }

    /**
     * Calculate delay in seconds until next 12:00 AM (midnight)
     */
    private long calculateDelayUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.toLocalDate().atStartOfDay().plusDays(1);
        
        long delaySeconds = ChronoUnit.SECONDS.between(now, nextMidnight);
        
        // If we're already past midnight today, schedule for next midnight
        if (delaySeconds <= 0) {
            nextMidnight = nextMidnight.plusDays(1);
            delaySeconds = ChronoUnit.SECONDS.between(now, nextMidnight);
        }
        
        return delaySeconds;
    }

    /**
     * Stop the notification cleanup scheduler
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
            logger.info("Notification cleanup scheduler stopped");
        }
    }

    /**
     * Clean up old notifications based on configured settings
     */
    private void cleanupOldNotifications() {
        try {
            // Get configured number of days from system settings
            int daysOld = systemSettingsDAO.getClearNotificationsDays();
            
            // If value is 0, automatic clearing is disabled
            if (daysOld <= 0) {
                logger.debug("Notification cleanup skipped - automatic clearing is disabled (days = 0)");
                return;
            }

            logger.info("Starting notification cleanup - deleting notifications older than {} days", daysOld);

            // Delete old notifications for the specified categories
            int deletedCount = notificationDAO.deleteOldNotifications(daysOld, CLEARABLE_CATEGORIES);

            if (deletedCount > 0) {
                logger.info("Notification cleanup completed - deleted {} old notifications (categories: {})", 
                    deletedCount, String.join(", ", CLEARABLE_CATEGORIES));
            } else {
                logger.debug("Notification cleanup completed - no notifications to delete");
            }

        } catch (SQLException e) {
            logger.error("Error during notification cleanup", e);
        } catch (Exception e) {
            logger.error("Unexpected error during notification cleanup", e);
        }
    }
}
