package com.example.budg_v2.service;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Service for managing user email frequency preferences
 * Reads from note_frequency table
 */
public class UserEmailFrequencyService {

    private static final Logger logger = LoggerFactory.getLogger(UserEmailFrequencyService.class);

    /**
     * Get user's email frequency preference
     * @param userId User ID
     * @return Frequency string: "Daily", "Weekly", "Monthly", "Not receiving notification emails", or null if not set
     */
    public String getUserEmailFrequency(Integer userId) throws SQLException {
        if (userId == null) {
            return null;
        }

        String sql = """
            SELECT nf.PrimaryName
            FROM note_frequency nf
            WHERE nf.Last_updateUserID = ?
            ORDER BY nf.Last_UpdateDatetime DESC
            LIMIT 1
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String frequency = rs.getString("PrimaryName");
                    return frequency != null ? frequency.trim() : null;
                }
            }
        }
        return null; // No preference set, use system default
    }

    /**
     * Check if user should receive emails
     * @param userId User ID
     * @return true if user should receive emails, false otherwise
     */
    public boolean shouldReceiveEmails(Integer userId) {
        try {
            String frequency = getUserEmailFrequency(userId);
            if (frequency == null) {
                return true; // Default: receive emails
            }
            return !frequency.equalsIgnoreCase("Not receiving notification emails") &&
                   !frequency.equalsIgnoreCase("Please select");
        } catch (SQLException e) {
            logger.error("Error checking user email frequency for user: " + userId, e);
            return true; // Default to receiving emails on error
        }
    }

    /**
     * Check if email should be sent immediately or queued based on user frequency
     * @param userId User ID
     * @param ruleDeliveryMode Rule's delivery mode (IMMEDIATE or BATCHED)
     * @return true if should send immediately, false if should queue
     */
    public boolean shouldSendImmediately(Integer userId, String ruleDeliveryMode) {
        // If rule is set to IMMEDIATE, always send immediately
        if ("IMMEDIATE".equalsIgnoreCase(ruleDeliveryMode)) {
            return true;
        }

        // If rule is BATCHED, check user frequency
        try {
            String frequency = getUserEmailFrequency(userId);
            if (frequency == null) {
                // No user preference, use system default (immediate for now)
                return true;
            }

            // If user has "Not receiving notification emails", don't send
            if (frequency.equalsIgnoreCase("Not receiving notification emails") ||
                frequency.equalsIgnoreCase("Please select")) {
                return false;
            }

            // For Daily, Weekly, Monthly - queue for batch delivery
            return false;
        } catch (SQLException e) {
            logger.error("Error determining email delivery mode for user: " + userId, e);
            return true; // Default to immediate on error
        }
    }
}





















































