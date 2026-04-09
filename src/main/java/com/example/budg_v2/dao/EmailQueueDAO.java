package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.EmailQueue;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for email_queue table operations
 */
public class EmailQueueDAO {

    /**
     * Queue an email for batch delivery
     */
    public Long queueEmail(EmailQueue emailQueue) throws SQLException {
        String sql = "INSERT INTO email_queue (notification_id, recipient_user_id, subject, " +
                "body, status, scheduled_at, retry_count, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (emailQueue.getNotificationId() != null) {
                stmt.setLong(1, emailQueue.getNotificationId());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }

            stmt.setInt(2, emailQueue.getRecipientUserId());
            stmt.setString(3, emailQueue.getSubject());
            stmt.setString(4, emailQueue.getBody());
            stmt.setString(5, emailQueue.getStatus() != null ? emailQueue.getStatus() : "PENDING");
            stmt.setTimestamp(6, emailQueue.getScheduledAt());
            stmt.setInt(7, emailQueue.getRetryCount() != null ? emailQueue.getRetryCount() : 0);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to queue email, no ID obtained");
    }

    /**
     * Get pending emails ready to be sent (scheduled_at <= now)
     */
    public List<EmailQueue> getPendingEmails(int limit) throws SQLException {
        String sql = "SELECT * FROM email_queue " +
                "WHERE status = 'PENDING' AND scheduled_at <= NOW() " +
                "ORDER BY scheduled_at ASC LIMIT ?";
        List<EmailQueue> emails = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    emails.add(mapResultSet(rs));
                }
            }
        }
        return emails;
    }

    /**
     * Get pending emails for a specific user
     */
    public List<EmailQueue> getPendingEmailsByUser(Integer userId) throws SQLException {
        String sql = "SELECT * FROM email_queue " +
                "WHERE status = 'PENDING' AND recipient_user_id = ? AND scheduled_at <= NOW() " +
                "ORDER BY scheduled_at ASC";

        List<EmailQueue> emails = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    emails.add(mapResultSet(rs));
                }
            }
        }
        return emails;
    }

    /**
     * Mark email as sent
     */
    public void markAsSent(Long emailQueueId) throws SQLException {
        String sql = "UPDATE email_queue SET status = 'SENT', sent_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, emailQueueId);
            stmt.executeUpdate();
        }
    }

    /**
     * Mark email as failed
     */
    public void markAsFailed(Long emailQueueId, String errorMessage) throws SQLException {
        String sql = "UPDATE email_queue SET status = 'FAILED', error_message = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, errorMessage);
            stmt.setLong(2, emailQueueId);
            stmt.executeUpdate();
        }
    }

    /**
     * Increment retry count
     */
    public void incrementRetryCount(Long emailQueueId) throws SQLException {
        String sql = "UPDATE email_queue SET retry_count = retry_count + 1 WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, emailQueueId);
            stmt.executeUpdate();
        }
    }

    /**
     * Cleanup old sent emails (older than specified days)
     */
    public int cleanupOldEmails(int daysOld) throws SQLException {
        String sql = "DELETE FROM email_queue " +
                "WHERE status = 'SENT' AND sent_at < DATE_SUB(NOW(), INTERVAL ? DAY)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, daysOld);
            return stmt.executeUpdate();
        }
    }

    /**
     * Map ResultSet to EmailQueue object
     */
    private EmailQueue mapResultSet(ResultSet rs) throws SQLException {
        EmailQueue emailQueue = new EmailQueue();
        emailQueue.setId(rs.getLong("id"));

        Long notificationId = rs.getLong("notification_id");
        if (!rs.wasNull()) {
            emailQueue.setNotificationId(notificationId);
        }

        emailQueue.setRecipientUserId(rs.getInt("recipient_user_id"));
        emailQueue.setSubject(rs.getString("subject"));
        emailQueue.setBody(rs.getString("body"));
        emailQueue.setStatus(rs.getString("status"));
        emailQueue.setScheduledAt(rs.getTimestamp("scheduled_at"));

        Timestamp sentAt = rs.getTimestamp("sent_at");
        if (sentAt != null) {
            emailQueue.setSentAt(sentAt);
        }

        String errorMessage = rs.getString("error_message");
        if (errorMessage != null) {
            emailQueue.setErrorMessage(errorMessage);
        }

        emailQueue.setRetryCount(rs.getInt("retry_count"));
        emailQueue.setCreatedAt(rs.getTimestamp("created_at"));
        return emailQueue;
    }
}




















































