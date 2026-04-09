package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Servlet for checking Information Segmentation enabled status
 * Endpoint: GET /api/segmentation/enabled
 * Returns: {"enabled": true/false}
 * 
 * This endpoint is public (no SuperAdmin requirement) to allow bulk upload page to check segmentation status
 */
@WebServlet("/api/segmentation/enabled")
public class SegmentationConfigServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SegmentationConfigServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            boolean enabled = checkInformationSegmentationEnabled();
            
            JsonObject result = new JsonObject();
            result.addProperty("enabled", enabled);
            response.getWriter().write(gson.toJson(result));
            
        } catch (SQLException e) {
            logger.error("Database error checking segmentation enabled", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error checking segmentation enabled", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Check if Information Segmentation is enabled
     * Segmentation is enabled if either enterprise_segment_default OR assigned_segments_default is enabled
     * Priority:
     * 1. Check enterprise_segment_default and assigned_segments_default in "DefaultSegment" group (highest priority - reflects Admin Panel settings)
     * 2. If not found, check app_config.ENTERPRISE_SEGMENT_DEFAULT and ASSIGNED_SEGMENTS_DEFAULT
     * 
     * @return true if segmentation is enabled (either enterprise or assigned segments is enabled), false otherwise
     */
    private boolean checkInformationSegmentationEnabled() throws SQLException {
        // First check enterprise_segment_default and assigned_segments_default in "DefaultSegment" group
        // This reflects what the user configured in Admin Panel → System Settings → Default Segment
        boolean enterpriseEnabled = false;
        boolean assignedEnabled = false;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT setting_key, setting_value FROM system_settings WHERE setting_group = ? AND setting_key IN (?, ?)")) {
            ps.setString(1, "DefaultSegment");
            ps.setString(2, "enterprise_segment_default");
            ps.setString(3, "assigned_segments_default");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("setting_key");
                    String value = rs.getString("setting_value");
                    boolean isEnabled = "true".equalsIgnoreCase(value) || "1".equals(value);
                    
                    if ("enterprise_segment_default".equals(key)) {
                        enterpriseEnabled = isEnabled;
                    } else if ("assigned_segments_default".equals(key)) {
                        assignedEnabled = isEnabled;
                    }
                }
            }
        }
        
        // If either is enabled, return true
        if (enterpriseEnabled || assignedEnabled) {
            return true;
        }

        // If not found in system_settings, fall back to app_config
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT config_key, definition FROM app_config WHERE config_key IN (?, ?)")) {
            ps.setString(1, "ENTERPRISE_SEGMENT_DEFAULT");
            ps.setString(2, "ASSIGNED_SEGMENTS_DEFAULT");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("config_key");
                    String value = rs.getString("definition");
                    boolean isEnabled = "true".equalsIgnoreCase(value) || "1".equals(value);
                    
                    if ("ENTERPRISE_SEGMENT_DEFAULT".equals(key) && isEnabled) {
                        return true;
                    } else if ("ASSIGNED_SEGMENTS_DEFAULT".equals(key) && isEnabled) {
                        return true;
                    }
                }
            }
        }

        return false; // Default to disabled
    }
}

