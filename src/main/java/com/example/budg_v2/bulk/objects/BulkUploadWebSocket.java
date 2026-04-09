package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.model.Job;
import com.example.budg_v2.model.JobProgress;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.sql.SQLException;
import java.util.Map;

/**
 * WebSocket endpoint for real-time bulk upload progress tracking
 * Endpoint: /ws/bulk-upload/{jobId}
 */
@ServerEndpoint("/ws/bulk-upload/{jobId}")
public class BulkUploadWebSocket {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadWebSocket.class);
    private static final Gson gson = new Gson();

    @OnOpen
    public void onOpen(Session session, @PathParam("jobId") String jobIdStr) {
        try {
            int jobId = Integer.parseInt(jobIdStr);
            
            // Register session with broadcaster
            BulkUploadBroadcaster.getInstance().registerSession(jobId, session);
            
            // Send connection confirmation
            JsonObject confirmation = new JsonObject();
            confirmation.addProperty("type", "connected");
            confirmation.addProperty("job_id", jobId);
            confirmation.addProperty("message", "WebSocket connected successfully");
            confirmation.addProperty("timestamp", System.currentTimeMillis());
            session.getBasicRemote().sendText(gson.toJson(confirmation));

            // Send a snapshot of the current job state so late subscribers
            // immediately see the latest status/progress even if processing
            // finished before the WebSocket was opened.
            sendJobSnapshot(jobId, session);

            logger.info("WebSocket opened for job {} (session: {})", jobId, session.getId());
        } catch (NumberFormatException e) {
            logger.error("Invalid job ID format: {}", jobIdStr);
            try {
                JsonObject error = new JsonObject();
                error.addProperty("type", "error");
                error.addProperty("message", "Invalid job ID format");
                session.getBasicRemote().sendText(gson.toJson(error));
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Invalid job ID"));
            } catch (Exception ex) {
                logger.error("Error sending error message", ex);
            }
        } catch (Exception e) {
            logger.error("Error in onOpen", e);
        }
    }

    /**
     * Send a snapshot of the current job state to the newly connected session.
     * This ensures the UI can immediately reflect the latest status even if all
     * broadcasts happened before the WebSocket was opened.
     */
    private void sendJobSnapshot(int jobId, Session session) {
        try {
            JobDAO jobDAO = new JobDAO();

            Job job = jobDAO.getJobById(jobId);
            if (job == null) {
                logger.warn("No job found for ID {} when sending WebSocket snapshot", jobId);
                return;
            }

            JobProgress progress = jobDAO.getJobProgress(jobId);
            Map<String, Integer> stats;
            try {
                stats = jobDAO.getJobStatistics(jobId);
            } catch (SQLException statsEx) {
                logger.warn("Failed to load job statistics for job {}: {}", jobId, statsEx.getMessage());
                stats = null;
            }

            JsonObject snapshot = new JsonObject();
            snapshot.addProperty("type", "snapshot");
            snapshot.addProperty("job_id", jobId);
            snapshot.addProperty("status", job.getStatus());

            String progressMessage = progress != null ? progress.getMessage() : null;
            if (progressMessage == null || progressMessage.trim().isEmpty()) {
                progressMessage = "Job status: " + job.getStatus();
            }
            snapshot.addProperty("message", progressMessage);

            int progressPercent = 0;
            String status = job.getStatus() != null ? job.getStatus() : "";
            if ("Completed".equalsIgnoreCase(status) ||
                    "Partially Completed".equalsIgnoreCase(status) ||
                    "Failed".equalsIgnoreCase(status)) {
                progressPercent = 100;
            } else if ("Processing".equalsIgnoreCase(status)) {
                progressPercent = 80;
            } else if ("Pending".equalsIgnoreCase(status)) {
                progressPercent = 10;
            }
            snapshot.addProperty("progress", progressPercent);

            if (stats != null) {
                snapshot.addProperty("inserted", stats.getOrDefault("inserted", 0));
                snapshot.addProperty("updated", stats.getOrDefault("updated", 0));
                snapshot.addProperty("deleted", stats.getOrDefault("deleted", 0));
                snapshot.addProperty("failed", stats.getOrDefault("failed", 0));
            }

            snapshot.addProperty("timestamp", System.currentTimeMillis());

            session.getBasicRemote().sendText(gson.toJson(snapshot));
        } catch (Exception e) {
            logger.error("Error sending job snapshot for job {}: {}", jobId, e.getMessage(), e);
        }
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("jobId") String jobIdStr) {
        logger.debug("Message received from client for job {}: {}", jobIdStr, message);
        
        // Handle client messages (e.g., ping/pong for keep-alive)
        try {
            JsonObject request = gson.fromJson(message, JsonObject.class);
            String type = request.has("type") ? request.get("type").getAsString() : "";
            
            if ("ping".equals(type)) {
                JsonObject pong = new JsonObject();
                pong.addProperty("type", "pong");
                pong.addProperty("timestamp", System.currentTimeMillis());
                session.getBasicRemote().sendText(gson.toJson(pong));
            }
        } catch (Exception e) {
            logger.warn("Error processing client message: {}", e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason, @PathParam("jobId") String jobIdStr) {
        try {
            int jobId = Integer.parseInt(jobIdStr);
            
            // Unregister session from broadcaster
            BulkUploadBroadcaster.getInstance().unregisterSession(jobId, session);
            
            logger.info("WebSocket closed for job {} (session: {}): {} - {}", 
                       jobId, session.getId(), closeReason.getCloseCode(), closeReason.getReasonPhrase());
        } catch (NumberFormatException e) {
            logger.error("Invalid job ID format on close: {}", jobIdStr);
        } catch (Exception e) {
            logger.error("Error in onClose", e);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable, @PathParam("jobId") String jobIdStr) {
        logger.error("WebSocket error for job {} (session: {}): {}", 
                    jobIdStr, session.getId(), throwable.getMessage(), throwable);
        
        try {
            int jobId = Integer.parseInt(jobIdStr);
            BulkUploadBroadcaster.getInstance().unregisterSession(jobId, session);
        } catch (Exception e) {
            logger.error("Error in onError cleanup", e);
        }
    }
}

