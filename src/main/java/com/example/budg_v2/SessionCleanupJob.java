package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Session cleanup job that periodically removes old session records from the database.
 * 
 * NOTE: This system uses a hybrid authentication approach:
 * - JWT tokens (stateless): Used for authentication and authorization in API requests.
 *   JWT tokens are self-contained and don't require database lookups for validation.
 * - Database sessions (stateful): Stored in auth_sessions table for:
 *   - Token revocation: When a user logs out, their session is marked as invalid
 *   - Session tracking: Track active sessions per user for security monitoring
 *   - Last activity tracking: Monitor user activity for security purposes
 * 
 * This cleanup job only removes old session records from the database. It does NOT
 * invalidate JWT tokens, as JWT tokens are stateless and validated based on expiration
 * time and signature. However, if a session is deleted, the associated JWT token's
 * sessionId claim will no longer be valid, causing AuthFilter to reject the token.
 * 
 * This is intentional: it allows for session revocation while maintaining the benefits
 * of stateless JWT tokens for performance.
 */
@WebListener
public class SessionCleanupJob implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(SessionCleanupJob.class);
    private Timer timer;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        timer = new Timer("session-cleanup-timer", true);
        // Run every 24h
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanup();
            }
        }, 60_000L, 24L * 60L * 60L * 1000L);
        logger.info("Session cleanup job scheduled");
    }

    /**
     * Clean up old session records from the database.
     * Removes sessions that have been inactive for more than 30 days.
     * 
     * This cleanup:
     * - Prevents the auth_sessions table from growing indefinitely
     * - Removes stale session data for users who haven't logged in for a long time
     * - Does NOT affect active JWT tokens (they remain valid until expiration)
     * - DOES affect token validation: deleted sessions will cause AuthFilter to reject tokens
     *   with the deleted sessionId, effectively revoking those tokens
     */
    private void cleanup() {
        // Delete sessions inactive for > 30 days
        String sql = "DELETE FROM auth_sessions WHERE last_activity < (CURRENT_TIMESTAMP - INTERVAL 30 DAY)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                logger.info("Session cleanup removed {} rows", deleted);
            }
        } catch (SQLException e) {
            logger.error("Session cleanup failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (timer != null) {
            try { timer.cancel(); } catch (Exception ignored) {}
        }
        logger.info("Session cleanup job stopped");
    }
}


