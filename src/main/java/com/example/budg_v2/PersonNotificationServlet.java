package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles notification frequency settings for people.
 */
@WebServlet("/api/people/notification/*")
public class PersonNotificationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Person ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int personId = Integer.parseInt(pathInfo.substring(1));
            Map<String, Object> notificationData = getNotificationFrequency(personId);

            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), notificationData);
        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid person ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
            sendError(response, "Database error: " + sqlEx.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Person ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int personId = Integer.parseInt(pathInfo.substring(1));
            
            // Parse request body
            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = objectMapper.readValue(request.getReader(), Map.class);
            String frequency = (String) requestData.get("frequency");
            
            if (frequency == null || frequency.trim().isEmpty()) {
                sendError(response, "Frequency is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            boolean success = updateNotificationFrequency(personId, frequency.trim());
            
            if (success) {
                response.setStatus(HttpServletResponse.SC_OK);
                Map<String, Object> result = Map.of("success", true, "message", "Notification frequency updated successfully");
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendError(response, "Failed to update notification frequency", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid person ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
            sendError(response, "Database error: " + sqlEx.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> getNotificationFrequency(int personId) throws SQLException {
        final String sql = """
            SELECT nf.PrimaryName,
                   nf.Last_UpdateDatetime,
                   nf.Last_updateUserID,
                   p.First_Name,
                   p.Last_Name
            FROM note_frequency nf
            LEFT JOIN people p ON nf.Last_updateUserID = p.ID
            WHERE nf.Last_updateUserID = ?
            ORDER BY nf.Last_UpdateDatetime DESC
            LIMIT 1
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("value", safeTrim(rs.getString("PrimaryName")));
                    result.put("updatedAt", rs.getTimestamp("Last_UpdateDatetime") != null ? 
                        rs.getTimestamp("Last_UpdateDatetime").toInstant().toString() : null);
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    result.put("updatedBy", updater.isEmpty() ? null : updater);
                    return result;
                }
            }
        }

        // Return default if no record found
        Map<String, Object> result = new HashMap<>();
        result.put("value", "Please select");
        result.put("updatedAt", null);
        result.put("updatedBy", null);
        return result;
    }

    private boolean updateNotificationFrequency(int personId, String frequency) throws SQLException {
        final String sql = """
            INSERT INTO note_frequency (PrimaryName, Last_updateUserID, Last_UpdateDatetime)
            VALUES (?, ?, NOW())
            ON DUPLICATE KEY UPDATE
            PrimaryName = VALUES(PrimaryName),
            Last_UpdateDatetime = VALUES(Last_UpdateDatetime)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, frequency);
            ps.setInt(2, personId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    private String buildFullName(String firstName, String lastName) {
        String first = safeTrim(firstName);
        String last = safeTrim(lastName);
        return (first + " " + last).trim();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
