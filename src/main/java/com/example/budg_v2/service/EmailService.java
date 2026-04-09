package com.example.budg_v2.service;

import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.EmailSettings;
import com.example.budg_v2.model.WorkflowNotification;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for sending emails using Jakarta Mail API
 */
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final EmailSettingsService emailSettingsService;
    private final WorkflowNotificationDAO notificationDAO;

    // Thread pool for async email sending (single thread to avoid overwhelming SMTP server)
    private static final ExecutorService emailExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EmailService-Thread");
        t.setDaemon(true);
        return t;
    });

    public EmailService() {
        this.emailSettingsService = new EmailSettingsService();
        this.notificationDAO = new WorkflowNotificationDAO();
    }

    /**
     * Send email asynchronously (non-blocking)
     * This method returns immediately and sends the email in the background
     * @param recipientUserId User ID of recipient
     * @param subject Email subject
     * @param body Email body (HTML)
     * @param notificationId Optional notification ID to mark as sent
     */
    public void sendAsync(Integer recipientUserId, String subject, String body, Long notificationId) {
        if (recipientUserId == null) {
            logger.warn("Cannot send email: recipient user ID is null");
            return;
        }

        // Submit email sending task to thread pool
        emailExecutor.submit(() -> {
            try {
                sendImmediate(recipientUserId, subject, body, notificationId);
            } catch (Exception e) {
                logger.error("Error in async email sending for user: " + recipientUserId, e);
            }
        });
    }

    /**
     * Send email immediately (synchronous)
     * @param recipientUserId User ID of recipient
     * @param subject Email subject
     * @param body Email body (HTML)
     * @param notificationId Optional notification ID to mark as sent
     * @return true if sent successfully, false otherwise
     */
    public boolean sendImmediate(Integer recipientUserId, String subject, String body, Long notificationId) {
        if (recipientUserId == null) {
            logger.warn("Cannot send email: recipient user ID is null");
            return false;
        }

        // Check if email is enabled globally
        if (!emailSettingsService.isEmailEnabled()) {
            logger.debug("Email is disabled globally, skipping email send");
            return false;
        }

        try {
            EmailSettings settings = emailSettingsService.getCurrentSettings();
            if (settings == null) {
                logger.error("Email settings not configured");
                return false;
            }

            String recipientEmail = getUserEmail(recipientUserId);
            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                logger.warn("User {} does not have an email address", recipientUserId);
                return false;
            }

            // Send email
            boolean sent = sendEmail(settings, recipientEmail, subject, body);

            // Mark notification as sent if notificationId is provided
            if (sent && notificationId != null) {
                markNotificationAsSent(notificationId);
            }

            return sent;
        } catch (Exception e) {
            logger.error("Error sending email to user: " + recipientUserId, e);
            return false;
        }
    }

    /**
     * Send email using Jakarta Mail API
     */
    private boolean sendEmail(EmailSettings settings, String recipientEmail, String subject, String body) {
        try {
            // Setup mail properties
            Properties props = new Properties();
            props.put("mail.smtp.host", settings.getSmtpHost());
            props.put("mail.smtp.port", settings.getSmtpPort());
            props.put("mail.smtp.auth", "true");

            // Configure encryption
            if ("SSL".equalsIgnoreCase(settings.getEncryption())) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", settings.getSmtpHost());
            } else if ("TLS".equalsIgnoreCase(settings.getEncryption())) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }

            // Create session with authenticator
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            settings.getSmtpUsername(),
                            settings.getSmtpPassword()
                    );
                }
            });

            // Create message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(settings.getSmtpUsername()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject(subject);
            message.setContent(body, "text/html; charset=utf-8");

            // Send message
            Transport.send(message);
            logger.info("Email sent successfully to: {}", recipientEmail);
            return true;

        } catch (MessagingException e) {
            logger.error("Failed to send email to: " + recipientEmail, e);
            return false;
        }
    }

    /**
     * Get user's email address from people table
     */
    private String getUserEmail(Integer userId) throws SQLException {
        String sql = "SELECT Email FROM people WHERE ID = ? AND Deleted_date IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Email");
                }
            }
        }
        return null;
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

    /**
     * Test SMTP connection
     * @param settings Email settings to test
     * @return true if connection successful, false otherwise
     */
    public boolean testConnection(EmailSettings settings) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", settings.getSmtpHost());
            props.put("mail.smtp.port", settings.getSmtpPort());
            props.put("mail.smtp.auth", "true");

            if ("SSL".equalsIgnoreCase(settings.getEncryption())) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", settings.getSmtpHost());
            } else if ("TLS".equalsIgnoreCase(settings.getEncryption())) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            settings.getSmtpUsername(),
                            settings.getSmtpPassword()
                    );
                }
            });

            // Try to connect
            Transport transport = session.getTransport("smtp");
            transport.connect(settings.getSmtpHost(), settings.getSmtpPort(),
                    settings.getSmtpUsername(), settings.getSmtpPassword());
            transport.close();

            logger.info("SMTP connection test successful");
            return true;

        } catch (MessagingException e) {
            logger.error("SMTP connection test failed", e);
            return false;
        }
    }
}