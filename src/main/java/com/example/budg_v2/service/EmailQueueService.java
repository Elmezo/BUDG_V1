package com.example.budg_v2.service;

import com.example.budg_v2.dao.EmailQueueDAO;
import com.example.budg_v2.model.EmailQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

/**
 * Service for managing email queue for batched delivery
 */
public class EmailQueueService {

    private static final Logger logger = LoggerFactory.getLogger(EmailQueueService.class);
    private final EmailQueueDAO emailQueueDAO;
    private final UserEmailFrequencyService frequencyService;

    public EmailQueueService() {
        this.emailQueueDAO = new EmailQueueDAO();
        this.frequencyService = new UserEmailFrequencyService();
    }

    /**
     * Queue an email for batch delivery
     * Calculates scheduled_at based on user's email frequency preference
     * @param recipientUserId User ID
     * @param subject Email subject
     * @param body Email body (HTML)
     * @param notificationId Optional notification ID
     * @return Queue ID
     */
    public Long queueEmail(Integer recipientUserId, String subject, String body, Long notificationId) throws SQLException {
        if (recipientUserId == null) {
            throw new IllegalArgumentException("Recipient user ID cannot be null");
        }

        // Get user's email frequency preference
        String frequency = frequencyService.getUserEmailFrequency(recipientUserId);
        
        // Calculate scheduled_at based on frequency
        Timestamp scheduledAt = calculateScheduledTime(frequency);

        EmailQueue emailQueue = new EmailQueue();
        emailQueue.setNotificationId(notificationId);
        emailQueue.setRecipientUserId(recipientUserId);
        emailQueue.setSubject(subject);
        emailQueue.setBody(body);
        emailQueue.setScheduledAt(scheduledAt);
        emailQueue.setStatus("PENDING");

        Long queueId = emailQueueDAO.queueEmail(emailQueue);
        logger.debug("Queued email for user {} with scheduled_at: {}", recipientUserId, scheduledAt);
        return queueId;
    }

    /**
     * Calculate scheduled time based on user frequency preference
     * @param frequency User frequency: "Daily", "Weekly", "Monthly", or null
     * @return Timestamp when email should be sent
     */
    private Timestamp calculateScheduledTime(String frequency) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 9); // 9 AM
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (frequency == null || frequency.equalsIgnoreCase("Please select")) {
            // Default: send at next 9 AM
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } else if (frequency.equalsIgnoreCase("Daily")) {
            // Send at next 9 AM
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } else if (frequency.equalsIgnoreCase("Weekly")) {
            // Send next Monday at 9 AM
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int daysUntilMonday = (Calendar.MONDAY - dayOfWeek + 7) % 7;
            if (daysUntilMonday == 0) {
                daysUntilMonday = 7; // If today is Monday, schedule for next Monday
            }
            cal.add(Calendar.DAY_OF_MONTH, daysUntilMonday);
        } else if (frequency.equalsIgnoreCase("Monthly")) {
            // Send first day of next month at 9 AM
            cal.add(Calendar.MONTH, 1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            // Unknown frequency, default to next 9 AM
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        return new Timestamp(cal.getTimeInMillis());
    }

    /**
     * Get pending emails ready to be sent
     * @param limit Maximum number of emails to retrieve
     * @return List of pending emails
     */
    public List<EmailQueue> getPendingEmails(int limit) throws SQLException {
        return emailQueueDAO.getPendingEmails(limit);
    }

    /**
     * Get pending emails for a specific user
     * @param userId User ID
     * @return List of pending emails for user
     */
    public List<EmailQueue> getPendingEmailsByUser(Integer userId) throws SQLException {
        return emailQueueDAO.getPendingEmailsByUser(userId);
    }

    /**
     * Mark email as sent
     */
    public void markAsSent(Long emailQueueId) throws SQLException {
        emailQueueDAO.markAsSent(emailQueueId);
    }

    /**
     * Mark email as failed
     */
    public void markAsFailed(Long emailQueueId, String errorMessage) throws SQLException {
        emailQueueDAO.markAsFailed(emailQueueId, errorMessage);
    }

    /**
     * Increment retry count
     */
    public void incrementRetryCount(Long emailQueueId) throws SQLException {
        emailQueueDAO.incrementRetryCount(emailQueueId);
    }

    /**
     * Cleanup old sent emails
     * @param daysOld Number of days old to cleanup
     * @return Number of emails deleted
     */
    public int cleanupOldEmails(int daysOld) throws SQLException {
        return emailQueueDAO.cleanupOldEmails(daysOld);
    }
}































