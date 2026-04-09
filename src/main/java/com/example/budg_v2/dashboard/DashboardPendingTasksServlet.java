package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard widget: Pending Tasks
 * Returns pending tasks for the current user
 */
@WebServlet(name = "DashboardPendingTasksServlet", urlPatterns = "/api/dashboard/pending-tasks")
public class DashboardPendingTasksServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try {
            Map<String, Object> result = getPendingTasks(userId);
            objectMapper.writeValue(response.getWriter(), result);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error: " + e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private Map<String, Object> getPendingTasks(int userId) throws SQLException {
        List<Map<String, Object>> tasks = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if job table exists and what columns it has
            boolean hasReferenceNameColumn = checkColumnExists(conn, "job", "reference_name");
            
            // Build query based on available columns
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT j.id, ");
            
            // Always include these columns if they exist (with fallbacks)
            if (checkColumnExists(conn, "job", "type")) {
                sqlBuilder.append("j.type, ");
            } else {
                sqlBuilder.append("'Unknown' AS type, ");
            }
            
            if (hasReferenceNameColumn) {
                sqlBuilder.append("j.reference_name, ");
            } else {
                sqlBuilder.append("'No reference' AS reference_name, ");
            }
            
            if (checkColumnExists(conn, "job", "created_date")) {
                sqlBuilder.append("j.created_date, ");
            } else if (checkColumnExists(conn, "job", "created_at")) {
                sqlBuilder.append("j.created_at AS created_date, ");
            } else {
                sqlBuilder.append("NOW() AS created_date, ");
            }
            
            sqlBuilder.append("j.status ");
            sqlBuilder.append("FROM job j ");
            sqlBuilder.append("WHERE j.created_by = ? ");
            sqlBuilder.append("AND j.status = 'Pending' ");
            sqlBuilder.append("ORDER BY ");
            
            if (checkColumnExists(conn, "job", "created_date")) {
                sqlBuilder.append("j.created_date ");
            } else if (checkColumnExists(conn, "job", "created_at")) {
                sqlBuilder.append("j.created_at ");
            } else {
                sqlBuilder.append("j.id ");
            }
            
            sqlBuilder.append("DESC LIMIT 10");
            
            String sql = sqlBuilder.toString();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> task = new HashMap<>();
                        task.put("id", rs.getInt("id"));
                        task.put("type", rs.getString("type"));
                        
                        if (hasReferenceNameColumn) {
                            task.put("referenceName", rs.getString("reference_name"));
                        } else {
                            task.put("referenceName", "No reference");
                        }
                        
                        task.put("createdDate", rs.getTimestamp("created_date"));
                        task.put("status", rs.getString("status"));
                        tasks.add(task);
                    }
                }
            } catch (SQLException e) {
                // If job table doesn't exist or query fails, return empty list
                // This allows the widget to show empty state
                System.err.println("Pending tasks query failed (table may not exist): " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("tasks", tasks);
        result.put("count", tasks.size());
        
        return result;
    }
    
    /**
     * Check if a column exists in a table
     */
    private boolean checkColumnExists(Connection conn, String tableName, String columnName) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet columns = meta.getColumns(null, null, tableName, columnName);
            boolean exists = columns.next();
            columns.close();
            return exists;
        } catch (SQLException e) {
            return false;
        }
    }
}

