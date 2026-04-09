package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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

@WebServlet("/api/bulk/product/report/*")
public class ProductBulkReportServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ProductBulkReportServlet.class);
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
            JsonObject report = getJobReport(jobId);
            
            if (report == null) {
                sendErrorResponse(response, "Job not found", 404);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(report));

        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid Job ID format", 400);
        } catch (SQLException e) {
            logger.error("Database error while fetching report", e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Unexpected error while fetching report", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }

    private JsonObject getJobReport(int jobId) throws SQLException {
        // Schema: table `job` (lowercase), column `Type`; use backticks for case-sensitive MySQL
        String jobQuery = """
                    SELECT j.`ID` as id, j.`Type` AS job_type, j.`Reference_Name` as reference_name, j.`Items_Count` as items_count, j.`Status` as status,
                           (j.`Completed_date` IS NOT NULL) as is_completed,
                           j.`Created_date` as created_at, j.`Completed_date` as updated_at, j.`Created_By` as created_by_user_id,
                           CONCAT(p.`First_Name`, ' ', p.`Last_Name`) as created_by_name
                    FROM `job` j
                    LEFT JOIN `people` p ON j.`Created_By` = p.`ID`
                    WHERE j.`ID` = ?
                """;

        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(jobQuery)) {
            
            ps.setInt(1, jobId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                JsonObject report = new JsonObject();
                report.addProperty("job_id", rs.getInt("id"));
                report.addProperty("job_type", rs.getString("job_type"));
                report.addProperty("reference_name", rs.getString("reference_name"));
                report.addProperty("items_count", rs.getInt("items_count"));
                report.addProperty("status", rs.getString("status"));
                report.addProperty("is_completed", rs.getBoolean("is_completed"));
                report.addProperty("created_at", rs.getTimestamp("created_at") != null ? 
                    rs.getTimestamp("created_at").toString() : null);
                report.addProperty("updated_at", rs.getTimestamp("updated_at") != null ? 
                    rs.getTimestamp("updated_at").toString() : null);
                report.addProperty("created_by_user_id", rs.getInt("created_by_user_id"));
                report.addProperty("created_by_name", rs.getString("created_by_name"));

                JsonArray items = getReportItems(conn, jobId);
                report.add("items", items);

                return report;
            }
        }
    }

    private JsonArray getReportItems(Connection conn, int jobId) throws SQLException {
        String itemsQuery = """
                    SELECT jri.`ID` as id, jri.`Field_Name` as entity_name, jri.`Status` as status, jri.`Position` as row_number
                    FROM `job_report_item` jri
                    WHERE jri.`Job_ID` = ?
                    ORDER BY jri.`Position`
                """;

        JsonArray items = new JsonArray();

        try (PreparedStatement ps = conn.prepareStatement(itemsQuery)) {
            ps.setInt(1, jobId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("id", rs.getInt("id"));
                    item.addProperty("entity_name", rs.getString("entity_name"));
                    item.addProperty("status", rs.getString("status"));
                    item.addProperty("row_number", rs.getInt("row_number"));

                    JsonArray messages = getItemMessages(conn, rs.getInt("id"));
                    item.add("messages", messages);

                    items.add(item);
                }
            }
        }

        return items;
    }

    private JsonArray getItemMessages(Connection conn, int itemId) throws SQLException {
        String messagesQuery = """
                    SELECT jrim.`ID` as id, jrim.`Error_Code` as message_code, jrim.`Message` as message_text, jrim.`Type` as severity
                    FROM `job_report_item_messages` jrim
                    WHERE jrim.`Report_ID` = ?
                    ORDER BY jrim.`ID`
                """;

        JsonArray messages = new JsonArray();

        try (PreparedStatement ps = conn.prepareStatement(messagesQuery)) {
            ps.setInt(1, itemId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject message = new JsonObject();
                    message.addProperty("id", rs.getInt("id"));
                    message.addProperty("message_code", rs.getString("message_code"));
                    message.addProperty("message_text", rs.getString("message_text"));
                    message.addProperty("severity", rs.getString("severity"));
                    messages.add(message);
                }
            }
        }

        return messages;
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
