package com.example.budg_v2.util;

import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.EmailQueue;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.service.EmailQueueService;
import com.example.budg_v2.service.EmailService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for processing batched emails
 * Similar to WorkflowSlaScheduler, runs periodically to send queued emails
 */
@WebListener
public class EmailBatchScheduler implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(EmailBatchScheduler.class);
    private final EmailQueueService emailQueueService;
    private final EmailService emailService;
    private final WorkflowNotificationDAO notificationDAO;
    private ScheduledExecutorService scheduler;
    private static final int BATCH_SIZE = 50; // Process up to 50 emails per run
    private static final int MAX_RETRIES = 3;

    public EmailBatchScheduler() {
        this.emailQueueService = new EmailQueueService();
        this.emailService = new EmailService();
        this.notificationDAO = new WorkflowNotificationDAO();
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        int intervalMinutes = 15; // Default: check every 15 minutes
        start(intervalMinutes);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        stop();
    }

    /**
     * Start the email batch scheduler
     * @param intervalMinutes How often to check for pending emails (default: 15 minutes)
     */
    private void start(int intervalMinutes) {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.warn("Email batch scheduler is already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EmailBatchScheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            this::processEmailQueue,
            0, // Start immediately
            intervalMinutes,
            TimeUnit.MINUTES
        );

        logger.info("Email batch scheduler started with interval: {} minutes", intervalMinutes);
    }

    /**
     * Stop the email batch scheduler
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
            logger.info("Email batch scheduler stopped");
        }
    }

    /**
     * Process pending emails from queue
     */
    private void processEmailQueue() {
        try {
            List<EmailQueue> pendingEmails = emailQueueService.getPendingEmails(BATCH_SIZE);
            logger.debug("Processing {} pending emails", pendingEmails.size());

            int sentCount = 0;
            int failedCount = 0;

            for (EmailQueue emailQueue : pendingEmails) {
                try {
                    // Send email
                    boolean sent = emailService.sendImmediate(
                        emailQueue.getRecipientUserId(),
                        emailQueue.getSubject(),
                        emailQueue.getBody(),
                        emailQueue.getNotificationId()
                    );

                    if (sent) {
                        emailQueueService.markAsSent(emailQueue.getId());
                        sentCount++;

                        // Mark notification as sent if notificationId exists
                        if (emailQueue.getNotificationId() != null) {
                            markNotificationAsSent(emailQueue.getNotificationId());
                        }
                    } else {
                        // Increment retry count
                        emailQueueService.incrementRetryCount(emailQueue.getId());

                        // Check if max retries reached
                        if (emailQueue.getRetryCount() != null && emailQueue.getRetryCount() >= MAX_RETRIES) {
                            emailQueueService.markAsFailed(
                                emailQueue.getId(),
                                "Max retry attempts reached"
                            );
                            failedCount++;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error processing email queue item: " + emailQueue.getId(), e);
                    try {
                        emailQueueService.incrementRetryCount(emailQueue.getId());
                        if (emailQueue.getRetryCount() != null && emailQueue.getRetryCount() >= MAX_RETRIES) {
                            emailQueueService.markAsFailed(
                                emailQueue.getId(),
                                "Error: " + e.getMessage()
                            );
                            failedCount++;
                        }
                    } catch (SQLException sqlEx) {
                        logger.error("Error updating email queue status", sqlEx);
                    }
                }
            }

            if (sentCount > 0 || failedCount > 0) {
                logger.info("Email batch processing complete: {} sent, {} failed", sentCount, failedCount);
            }

            // Cleanup old sent emails (older than 30 days)
            try {
                int cleaned = emailQueueService.cleanupOldEmails(30);
                if (cleaned > 0) {
                    logger.debug("Cleaned up {} old email queue records", cleaned);
                }
            } catch (SQLException e) {
                logger.error("Error cleaning up old emails", e);
            }

        } catch (SQLException e) {
            logger.error("Error processing email queue", e);
        }
    }

    /**
     * Mark notification as email sent
     */
    private void markNotificationAsSent(Long notificationId) {
        try {
            WorkflowNotification notification = notificationDAO.findById(notificationId);
            if (notification != null) {
                notification.setEmailSent(true);
                notification.setEmailSentAt(new java.sql.Timestamp(System.currentTimeMillis()));
                notificationDAO.update(notification);
            }
        } catch (SQLException e) {
            logger.error("Error marking notification as sent: " + notificationId, e);
        }
    }
}

