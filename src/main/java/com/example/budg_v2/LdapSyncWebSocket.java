package com.example.budg_v2;

import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

/**
 * WebSocket endpoint for real-time LDAP sync progress tracking
 * Endpoint: /ws/ldap-sync/{jobId}
 */
@ServerEndpoint("/ws/ldap-sync/{jobId}")
public class LdapSyncWebSocket {

    private static final Logger logger = LoggerFactory.getLogger(LdapSyncWebSocket.class);
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
            
            logger.info("WebSocket opened for LDAP sync job {} (session: {})", jobId, session.getId());
        } catch (NumberFormatException e) {
            logger.error("Invalid job ID format: {}", jobIdStr);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Invalid job ID"));
            } catch (Exception ex) {
                logger.error("Error closing session", ex);
            }
        } catch (Exception e) {
            logger.error("Error opening WebSocket", e);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Internal server error"));
            } catch (Exception ex) {
                logger.error("Error closing session", ex);
            }
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // LDAP sync is server-initiated, client doesn't send messages
        logger.debug("Received message from client: {}", message);
    }

    @OnClose
    public void onClose(Session session, @PathParam("jobId") String jobIdStr) {
        try {
            int jobId = Integer.parseInt(jobIdStr);
            BulkUploadBroadcaster.getInstance().unregisterSession(jobId, session);
            logger.info("WebSocket closed for LDAP sync job {} (session: {})", jobId, session.getId());
        } catch (NumberFormatException e) {
            logger.warn("Invalid job ID format on close: {}", jobIdStr);
        } catch (Exception e) {
            logger.error("Error closing WebSocket", e);
        }
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("jobId") String jobIdStr) {
        logger.error("WebSocket error for LDAP sync job {}: {}", jobIdStr, error.getMessage(), error);
        try {
            int jobId = Integer.parseInt(jobIdStr);
            BulkUploadBroadcaster.getInstance().unregisterSession(jobId, session);
        } catch (Exception e) {
            logger.error("Error unregistering session on error", e);
        }
    }
}

