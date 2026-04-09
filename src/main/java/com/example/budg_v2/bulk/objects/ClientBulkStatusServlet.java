package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
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

@WebServlet("/api/bulk/client/status/*")
public class ClientBulkStatusServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ClientBulkStatusServlet.class);
    private static final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(request, response);

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            sendErrorResponse(response, "Job ID is required", 400);
            return;
        }

        try {
            int jobId = Integer.parseInt(pathInfo.substring(1));
            JsonObject status = getJobStatus(jobId);
            
            if (status == null) {
                sendErrorResponse(response, "Job not found", 404);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(status));

        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid Job ID format", 400);
        } catch (SQLException e) {
            logger.error("Database error while fetching status", e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Unexpected error while fetching status", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    private JsonObject getJobStatus(int jobId) throws SQLException {
        // Schema: `job` has ID, Status, Completed_date, Items_Count; `job_progress` has Job_ID, Status, Message (no progress_percentage, updated_at)
        String query = """
            SELECT j.`ID` as id, j.`Status` as status, (j.`Completed_date` IS NOT NULL) AS is_completed, j.`Items_Count` AS items_count,
                   0 AS progress_percentage, jp.`Status` AS stage, jp.`Message` as progress_message,
                   NULL AS progress_updated_at
            FROM `job` j
            LEFT JOIN `job_progress` jp ON j.`ID` = jp.`Job_ID`
            WHERE j.`ID` = ?
            ORDER BY jp.`ID` DESC
            LIMIT 1
        """;

        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            
            ps.setInt(1, jobId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                JsonObject status = new JsonObject();
                status.addProperty("job_id", rs.getInt("id"));
                status.addProperty("status", rs.getString("status"));
                status.addProperty("is_completed", rs.getBoolean("is_completed"));
                status.addProperty("items_count", rs.getInt("items_count"));
                status.addProperty("progress_percentage", rs.getInt("progress_percentage"));
                status.addProperty("stage", rs.getString("stage"));
                status.addProperty("progress_message", rs.getString("progress_message"));
                status.addProperty("progress_updated_at", rs.getTimestamp("progress_updated_at") != null ? 
                    rs.getTimestamp("progress_updated_at").toString() : null);

                return status;
            }
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("message", message);
        response.setStatus(statusCode);
        response.getWriter().write(gson.toJson(error));
    }
}
