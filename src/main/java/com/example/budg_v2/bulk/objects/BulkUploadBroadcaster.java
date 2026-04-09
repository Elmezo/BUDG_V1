package com.example.budg_v2.bulk.objects;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Singleton broadcaster for WebSocket job updates
 * Manages WebSocket sessions and broadcasts job status updates
 */
public class BulkUploadBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadBroadcaster.class);
    private static final Gson gson = new Gson();
    private static BulkUploadBroadcaster instance;

    // Map of jobId -> Set of WebSocket sessions
    private final Map<Integer, Set<Session>> jobSessions = new ConcurrentHashMap<>();

    private BulkUploadBroadcaster() {
        // Private constructor for singleton
    }

    /**
     * Get singleton instance
     */
    public static synchronized BulkUploadBroadcaster getInstance() {
        if (instance == null) {
            instance = new BulkUploadBroadcaster();
        }
        return instance;
    }

    /**
     * Register a WebSocket session for a job
     */
    public void registerSession(int jobId, Session session) {
        jobSessions.computeIfAbsent(jobId, k -> new CopyOnWriteArraySet<>()).add(session);
        logger.info("Session {} registered for job {}", session.getId(), jobId);
    }

    /**
     * Unregister a WebSocket session
     */
    public void unregisterSession(int jobId, Session session) {
        Set<Session> sessions = jobSessions.get(jobId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                jobSessions.remove(jobId);
            }
            logger.info("Session {} unregistered from job {}", session.getId(), jobId);
        }
    }

    /**
     * Broadcast status update to all sessions watching a job
     */
    public void broadcast(int jobId, String status, int progress, String message) {
        JsonObject update = new JsonObject();
        update.addProperty("status", status);
        update.addProperty("progress", progress);
        update.addProperty("message", message);
        update.addProperty("timestamp", System.currentTimeMillis());

        broadcast(jobId, update);
    }

    /**
     * Broadcast status update with statistics
     */
    public void broadcast(int jobId, String status, int progress, String message, 
                         int inserted, int updated, int deleted, int failed) {
        JsonObject update = new JsonObject();
        update.addProperty("status", status);
        update.addProperty("progress", progress);
        update.addProperty("message", message);
        update.addProperty("inserted", inserted);
        update.addProperty("updated", updated);
        update.addProperty("deleted", deleted);
        update.addProperty("failed", failed);
        update.addProperty("timestamp", System.currentTimeMillis());

        broadcast(jobId, update);
    }

    /**
     * Broadcast JSON object to all sessions
     */
    public void broadcast(int jobId, JsonObject update) {
        Set<Session> sessions = jobSessions.get(jobId);
        if (sessions == null || sessions.isEmpty()) {
            logger.debug("No sessions to broadcast to for job {}", jobId);
            return;
        }

        String message = gson.toJson(update);
        logger.info("Broadcasting to {} sessions for job {}: {}", sessions.size(), jobId, message);

        // Broadcast to all sessions, remove closed ones
        // Use async remote endpoint to handle concurrent sends without blocking
        sessions.removeIf(session -> {
            if (!session.isOpen()) {
                logger.debug("Removing closed session {}", session.getId());
                return true;
            }

            try {
                // Use async remote endpoint to avoid TEXT_FULL_WRITING errors with concurrent sends
                // Async operations don't throw IOException synchronously - errors are handled asynchronously
                session.getAsyncRemote().sendText(message);
                return false;
            } catch (IllegalStateException e) {
                // Session may be in an invalid state (e.g., TEXT_FULL_WRITING, closed, etc.)
                logger.warn("Session {} is in invalid state, removing: {}", session.getId(), e.getMessage());
                return true; // Remove session on error
            } catch (Exception e) {
                // Catch any other unexpected exceptions
                logger.error("Unexpected error sending message to session {}: {}", session.getId(), e.getMessage());
                return true; // Remove session on error
            }
        });

        // Clean up if no sessions left
        if (sessions.isEmpty()) {
            jobSessions.remove(jobId);
        }
    }

    /**
     * Get count of active sessions for a job
     */
    public int getSessionCount(int jobId) {
        Set<Session> sessions = jobSessions.get(jobId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Close all sessions for a job
     */
    public void closeAllSessions(int jobId) {
        Set<Session> sessions = jobSessions.remove(jobId);
        if (sessions != null) {
            for (Session session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (IOException e) {
                    logger.error("Error closing session {}: {}", session.getId(), e.getMessage());
                }
            }
            logger.info("Closed {} sessions for job {}", sessions.size(), jobId);
        }
    }
}

