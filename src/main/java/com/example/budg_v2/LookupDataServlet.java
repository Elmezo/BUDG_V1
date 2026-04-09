package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/lookup-data")
public class LookupDataServlet extends HttpServlet {

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);
        String entityIdStr = req.getParameter("entityId");

        try (Connection conn = DatabaseConnection.getConnection()) {
            Map<String, Object> result = new HashMap<>();

            // Get all lookup data
            result.put("changeRequestSeverity", getChangeRequestSeverity(conn));
            result.put("changeRequestUrgency", getChangeRequestUrgency(conn));
            result.put("changeRequestType", getChangeRequestType(conn));

            // Process definitions require entityId
            if (entityIdStr != null && !entityIdStr.isEmpty()) {
                int entityId = Integer.parseInt(entityIdStr);
                result.put("processDefinition", getProcessDefinition(conn, entityId));
            } else {
                result.put("processDefinition", new ArrayList<>());
            }

            result.put("system", getSystem(conn));
            result.put("datasetType", getDatasetType(conn));
            result.put("glossaryType", getGlossaryType(conn));
            result.put("systemType", getSystemType(conn));
            result.put("processType", getProcessType(conn));
            result.put("datasetLifecycle", getDatasetLifecycle(conn));
            result.put("glossaryLifecycle", getGlossaryLifecycle(conn));
            result.put("systemLifecycle", getSystemLifecycle(conn));
            result.put("processLifecycleStatus", getProcessLifecycleStatus(conn));
            result.put("status", getStatus(conn));

            result.put("success", true);
            JsonUtil.sendJsonResponse(resp.getWriter(), result);

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid entityId format", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }

    private List<Map<String, Object>> getChangeRequestSeverity(Connection conn) throws SQLException {
        String sql = "SELECT id, PrimaryName FROM changerequest_severity WHERE Status = 'Enabled'";
        return executeSimpleQuery(conn, sql);
    }

    private List<Map<String, Object>> getChangeRequestUrgency(Connection conn) throws SQLException {
        String sql = "SELECT id, PrimaryName FROM changerequest_urgency WHERE Status = 'Enabled'";
        return executeSimpleQuery(conn, sql);
    }

    private List<Map<String, Object>> getChangeRequestType(Connection conn) throws SQLException {
        String sql = "SELECT id, PrimaryName FROM changerequest_type WHERE Status = 'Enabled'";
        return executeSimpleQuery(conn, sql);
    }

    private List<Map<String, Object>> getProcessDefinition(Connection conn, int entityId) throws SQLException {
        String sql = "SELECT id, PrimaryName, Is_Default FROM process_definition " +
                "WHERE Status = 'Enabled' AND Entity_ID = ? ORDER BY PrimaryName";

        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, entityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("primaryName", rs.getString("PrimaryName"));
                    row.put("isDefault", rs.getBoolean("Is_Default"));
                    results.add(row);
                }
            }
        }
        return results;
    }

    private List<Map<String, Object>> getSystem(Connection conn) throws SQLException {
        String sql = "SELECT id, Name FROM system WHERE parent_id IS NULL AND Deleted_datetime IS NULL";
        return executeQueryWithName(conn, sql);
    }

    private List<Map<String, Object>> getDatasetType(Connection conn) throws SQLException {
        String sql = "SELECT id, PrimaryName FROM dataset_type";
        return executeSimpleQuery(conn, sql);
    }

    private List<Map<String, Object>> getGlossaryType(Connection conn) throws SQLException {
        String sql = "SELECT id, name FROM glossary_type";
        return executeQueryWithName(conn, sql);
    }

    private List<Map<String, Object>> getSystemType(Connection conn) throws SQLException {
        String sql = "SELECT id, name FROM system_type";
        return executeQueryWithName(conn, sql);
    }

    private List<Map<String, Object>> getProcessType(Connection conn) throws SQLException {
        String sql = "SELECT id, primaryname FROM process_type";
        return executeSimpleQuery(conn, sql);
    }

    private List<Map<String, Object>> getDatasetLifecycle(Connection conn) throws SQLException {
        String sql = "SELECT id, PrimaryName FROM dataset_lifecycle";
        return executeSimpleQuery(conn, sql);
    }

    private List<Map<String, Object>> getGlossaryLifecycle(Connection conn) throws SQLException {
        String sql = "SELECT id, name FROM glossary_lifecycle";
        return executeQueryWithName(conn, sql);
    }

    private List<Map<String, Object>> getSystemLifecycle(Connection conn) throws SQLException {
        String sql = "SELECT id, name FROM system_lifecycle";
        return executeQueryWithName(conn, sql);
    }

    private List<Map<String, Object>> getProcessLifecycleStatus(Connection conn) throws SQLException {
        String sql = "SELECT id, primaryname FROM process_lifecycle_status";
        return executeSimpleQuery(conn, sql);
    }

    private List<Map<String, Object>> getStatus(Connection conn) throws SQLException {
        String sql = "SELECT id, primaryname FROM status WHERE deletedate IS NULL ORDER BY priority";
        return executeSimpleQuery(conn, sql);
    }

    private List<Map<String, Object>> executeSimpleQuery(Connection conn, String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString(2)); // PrimaryName or primaryname
                results.add(row);
            }
        }
        return results;
    }

    private List<Map<String, Object>> executeQueryWithName(Connection conn, String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString(2)); // Name or name
                results.add(row);
            }
        }
        return results;
    }
}