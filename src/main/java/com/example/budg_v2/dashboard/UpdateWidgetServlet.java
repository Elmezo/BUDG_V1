package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Servlet for updating dashboard widgets
 * PUT /api/dashboard/widgets/{id}
 */
@WebServlet(name = "UpdateWidgetServlet", urlPatterns = "/api/dashboard/widgets/update/*")
public class UpdateWidgetServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Check authentication
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        // Extract widget ID from URL path
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.startsWith("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Widget ID is required");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        String widgetIdStr = pathInfo.substring(1);
        int widgetId;
        try {
            widgetId = Integer.parseInt(widgetIdStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid widget ID");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        // Parse request body
        JsonNode requestData;
        try {
            requestData = objectMapper.readTree(request.getInputStream());
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request body");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        // Extract widget data
        String title = requestData.path("title").asText();
        String description = requestData.path("description").asText("");
        
        // Widget type specific data
        String widgetType = requestData.path("widgetSource").asText();
        JsonNode configData = null;
        
        if ("text".equals(widgetType)) {
            // Text widget
            String textContent = requestData.path("textContent").asText("");
            
            // Create config JSON
            ObjectNode config = objectMapper.createObjectNode();
            config.put("content", textContent);
            config.put("contentType", "html");
            if (!description.isEmpty()) {
                config.put("description", description);
            }
            configData = config;
        } else if ("savedSearch".equals(widgetType)) {
            // Saved search widget - save like text widget but with saved search config
            String source = requestData.path("source").asText("");
            String focus = requestData.path("focus").asText("");
            String visualizeAs = requestData.path("visualizeAs").asText("");
            String visualizeBy = requestData.path("visualizeBy").asText("");
            JsonNode displayNode = requestData.path("display");
            
            // Parse display columns
            java.util.List<String> displayColumns = new java.util.ArrayList<>();
            if (displayNode.isArray()) {
                for (JsonNode col : displayNode) {
                    displayColumns.add(col.asText());
                }
            }
            
            // Create config JSON similar to text widget structure
            ObjectNode config = objectMapper.createObjectNode();
            config.put("widgetSource", "savedSearch");
            config.put("source", source);
            config.put("focus", focus);
            config.put("visualizeAs", visualizeAs);
            config.put("visualizeBy", visualizeBy);
            
            // Add display columns as array
            ArrayNode displayArray = objectMapper.createArrayNode();
            for (String col : displayColumns) {
                displayArray.add(col);
            }
            config.set("display", displayArray);
            
            if (!description.isEmpty()) {
                config.put("description", description);
            }
            
            // Also include dataSource and visualization for backward compatibility
            ObjectNode dataSource = objectMapper.createObjectNode();
            dataSource.put("type", "saved_search");
            try {
                dataSource.put("searchId", Integer.parseInt(source));
            } catch (NumberFormatException e) {
                dataSource.put("searchId", 0);
            }
            config.set("dataSource", dataSource);
            config.put("facet", focus);
            
            ObjectNode visualization = objectMapper.createObjectNode();
            visualization.put("type", visualizeAs);
            visualization.put("visualizeBy", visualizeBy);
            visualization.set("displayColumns", displayArray);
            config.set("visualization", visualization);
            
            configData = config;
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid widget type");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // First verify the widget belongs to this user
            if (!verifyWidgetOwnership(conn, widgetId, userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                Map<String, String> error = new HashMap<>();
                error.put("error", "You don't have permission to update this widget");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Update the widget
            String sql = "UPDATE dashboard_widgets SET Title = ?, Description = ?, Widget_Config = ? WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, title);
                ps.setString(2, description != null ? description : "");
                ps.setString(3, configData.toString());
                ps.setInt(4, widgetId);
                
                int rowsAffected = ps.executeUpdate();
                
                if (rowsAffected > 0) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Widget updated successfully");
                    result.put("id", widgetId);
                    objectMapper.writeValue(response.getWriter(), result);
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Widget not found");
                    objectMapper.writeValue(response.getWriter(), error);
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    /**
     * Verify that the widget belongs to the user
     */
    private boolean verifyWidgetOwnership(Connection conn, int widgetId, int userId) throws SQLException {
        String sql = """
            SELECT dw.ID
            FROM dashboard_widgets dw
            JOIN dashboards d ON dw.Dashboard_ID = d.ID
            WHERE dw.ID = ? AND d.Created_By = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, widgetId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // Returns true if widget exists and belongs to user
            }
        }
    }
}
