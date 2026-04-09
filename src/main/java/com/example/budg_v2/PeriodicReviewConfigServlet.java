package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@WebServlet("/api/periodic-review-config")
public class PeriodicReviewConfigServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);
        String action = req.getParameter("action");
        String moduleIdStr = req.getParameter("moduleId");
        String idStr = req.getParameter("id");

        try (Connection conn = DatabaseConnection.getConnection()) {

            // Get specific record for update
            if ("getById".equals(action) && idStr != null) {
                getConfigById(conn, resp, idStr);
                return;
            }

            // Get list of configurations
            if (moduleIdStr == null || moduleIdStr.isEmpty()) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "moduleId parameter is required", 400);
                return;
            }

            int moduleId = Integer.parseInt(moduleIdStr);

            String sql = "SELECT ID, Name, Description, LastUpdatedDate " +
                    "FROM periodic_review_config " +
                    "WHERE ModuleID = ? and IsEnabled = 1 " +
                    "ORDER BY LastUpdatedDate DESC";

            List<Map<String, Object>> configurations = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, moduleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> config = new HashMap<>();
                        config.put("id", rs.getInt("ID"));
                        config.put("name", rs.getString("Name"));
                        config.put("description", rs.getString("Description"));
                        config.put("lastUpdated", rs.getTimestamp("LastUpdatedDate"));
                        configurations.add(config);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", configurations);
            result.put("count", configurations.size());
            JsonUtil.sendJsonResponse(resp.getWriter(), result);

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid parameter format", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error", 500);
        }
    }

    private void getConfigById(Connection conn, HttpServletResponse resp, String idStr) throws IOException {
        try {
            int id = Integer.parseInt(idStr);
            String sql = "SELECT * FROM periodic_review_config WHERE ID = ? and IsEnabled = 1";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> config = new HashMap<>();
                        config.put("id", rs.getInt("ID"));
                        config.put("moduleId", rs.getInt("ModuleID"));
                        config.put("name", rs.getString("Name"));
                        config.put("description", rs.getString("Description"));
                        config.put("filterConfig", rs.getString("FilterConfig"));
                        config.put("threshold", rs.getObject("Threshold"));
                        config.put("reviewStartDate", rs.getString("ReviewStartDate"));
                        config.put("recurrenceConfig", rs.getString("RecurrenceConfig"));
                        config.put("workflowId", rs.getString("WorkflowID"));
                        config.put("crProviderRef", rs.getString("CR_ProviderRef"));
                        config.put("crTitle", rs.getString("CR_Title"));
                        config.put("crSummary", rs.getString("CR_Summary"));
                        config.put("crType", rs.getObject("CR_Type"));
                        config.put("crSeverity", rs.getObject("CR_Severity"));
                        config.put("crUrgency", rs.getObject("CR_Urgency"));
                        config.put("crInitiatorEmail", rs.getString("CR_InitiatorEmail"));
                        config.put("isEnabled", rs.getObject("IsEnabled"));
                        config.put("createDate", rs.getTimestamp("Createdate"));
                        config.put("lastUpdatedDate", rs.getTimestamp("LastUpdatedDate"));
                        config.put("createdById", rs.getObject("CreatedBy_ID"));
                        config.put("lastUpdatedById", rs.getObject("LastUpdatedBy_ID"));

                        Map<String, Object> result = new HashMap<>();
                        result.put("success", true);
                        result.put("data", config);
                        JsonUtil.sendJsonResponse(resp.getWriter(), result);
                    } else {
                        JsonUtil.sendErrorResponse(resp.getWriter(), "Configuration not found", 404);
                    }
                }
            }
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error", 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);

        try (Connection conn = DatabaseConnection.getConnection()) {
            BufferedReader reader = req.getReader();
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);

            String sql = "INSERT INTO periodic_review_config " +
                    "(ModuleID, Name, Description, FilterConfig, Threshold, ReviewStartDate, " +
                    "RecurrenceConfig, WorkflowID, CR_ProviderRef, CR_Title, CR_Summary, " +
                    "CR_Type, CR_Severity, CR_Urgency, CR_InitiatorEmail, IsEnabled, Createdate, CreatedBy_ID) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                setStatementParameters(stmt, jsonObject, true);

                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", true);
                            result.put("message", "Configuration created successfully");
                            result.put("id", generatedKeys.getInt(1));
                            JsonUtil.sendJsonResponse(resp.getWriter(), result);
                        }
                    }
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Failed to create configuration", 500);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);

        try (Connection conn = DatabaseConnection.getConnection()) {
            BufferedReader reader = req.getReader();
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);

            if (!jsonObject.has("id")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "ID is required for update", 400);
                return;
            }

            String sql = "UPDATE periodic_review_config SET " +
                    "ModuleID = ?, Name = ?, Description = ?, FilterConfig = ?, Threshold = ?, " +
                    "ReviewStartDate = ?, RecurrenceConfig = ?, WorkflowID = ?, CR_ProviderRef = ?, " +
                    "CR_Title = ?, CR_Summary = ?, CR_Type = ?, CR_Severity = ?, CR_Urgency = ?, " +
                    "CR_InitiatorEmail = ?, IsEnabled = ?, LastUpdatedDate = ?, LastUpdatedBy_ID = ? " +
                    "WHERE ID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                setStatementParameters(stmt, jsonObject, false);
                stmt.setInt(19, jsonObject.get("id").getAsInt());

                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Configuration updated successfully");
                    JsonUtil.sendJsonResponse(resp.getWriter(), result);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Configuration not found", 404);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);
        String idStr = req.getParameter("id");

        if (idStr == null || idStr.isEmpty()) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "ID parameter is required", 400);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            int id = Integer.parseInt(idStr);

            // Soft Delete (Disable Instead of Delete)
            String sql = "UPDATE periodic_review_config SET IsEnabled = 0 WHERE ID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Configuration disabled successfully (soft deleted)");
                    JsonUtil.sendJsonResponse(resp.getWriter(), result);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Configuration not found", 404);
                }
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }


    private void setStatementParameters(PreparedStatement stmt, JsonObject json, boolean isInsert) throws SQLException {
        int index = 1;

        // ModuleID
        stmt.setInt(index++, json.has("moduleId") ? json.get("moduleId").getAsInt() : 0);

        // Name
        stmt.setString(index++, json.has("name") ? json.get("name").getAsString() : null);

        // Description
        stmt.setString(index++, json.has("description") ? json.get("description").getAsString() : null);

        // FilterConfig
        stmt.setString(index++, json.has("filterConfig") ? json.get("filterConfig").getAsString() : null);

        // Threshold
        if (json.has("threshold") && !json.get("threshold").isJsonNull()) {
            stmt.setInt(index++, json.get("threshold").getAsInt());
        } else {
            stmt.setNull(index++, java.sql.Types.INTEGER);
        }

        // ReviewStartDate
        stmt.setString(index++, json.has("reviewStartDate") ? json.get("reviewStartDate").getAsString() : null);

        // RecurrenceConfig
        stmt.setString(index++, json.has("recurrenceConfig") ? json.get("recurrenceConfig").getAsString() : null);

        // WorkflowID
        stmt.setString(index++, json.has("workflowId") ? json.get("workflowId").getAsString() : null);

        // CR_ProviderRef
        stmt.setString(index++, json.has("crProviderRef") ? json.get("crProviderRef").getAsString() : null);

        // CR_Title
        stmt.setString(index++, json.has("crTitle") ? json.get("crTitle").getAsString() : null);

        // CR_Summary
        stmt.setString(index++, json.has("crSummary") ? json.get("crSummary").getAsString() : null);

        // CR_Type
        if (json.has("crType") && !json.get("crType").isJsonNull()) {
            stmt.setInt(index++, json.get("crType").getAsInt());
        } else {
            stmt.setNull(index++, java.sql.Types.INTEGER);
        }

        // CR_Severity
        if (json.has("crSeverity") && !json.get("crSeverity").isJsonNull()) {
            stmt.setInt(index++, json.get("crSeverity").getAsInt());
        } else {
            stmt.setNull(index++, java.sql.Types.INTEGER);
        }

        // CR_Urgency
        if (json.has("crUrgency") && !json.get("crUrgency").isJsonNull()) {
            stmt.setInt(index++, json.get("crUrgency").getAsInt());
        } else {
            stmt.setNull(index++, java.sql.Types.INTEGER);
        }

        // CR_InitiatorEmail
        stmt.setString(index++, json.has("crInitiatorEmail") ? json.get("crInitiatorEmail").getAsString() : null);

        // IsEnabled
        if (json.has("isEnabled") && !json.get("isEnabled").isJsonNull()) {
            stmt.setBoolean(index++, json.get("isEnabled").getAsBoolean());
        } else {
            stmt.setNull(index++, java.sql.Types.BOOLEAN);
        }

        if (isInsert) {
            // Createdate
            stmt.setTimestamp(index++, new Timestamp(System.currentTimeMillis()));

            // CreatedBy_ID
            if (json.has("createdById") && !json.get("createdById").isJsonNull()) {
                stmt.setInt(index++, json.get("createdById").getAsInt());
            } else {
                stmt.setNull(index++, java.sql.Types.INTEGER);
            }
        } else {
            // LastUpdatedDate
            stmt.setTimestamp(index++, new Timestamp(System.currentTimeMillis()));

            // LastUpdatedBy_ID
            if (json.has("lastUpdatedById") && !json.get("lastUpdatedById").isJsonNull()) {
                stmt.setInt(index++, json.get("lastUpdatedById").getAsInt());
            } else {
                stmt.setNull(index++, java.sql.Types.INTEGER);
            }
        }
    }
}