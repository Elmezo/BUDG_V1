package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.UserContextUtil;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/periodic-review")
public class PeriodicReviewServlet extends HttpServlet {

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);
        String moduleIdStr = req.getParameter("moduleId");

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (moduleIdStr != null) {
                // Fetch single module status (keep existing logic if needed, or remove if
                // unused)
                int moduleId = Integer.parseInt(moduleIdStr);
                boolean isEnabled = false;

                String sql = "SELECT IsEnabled FROM periodic_review_modules WHERE ModuleID = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, moduleId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            isEnabled = rs.getBoolean("IsEnabled");
                        }
                    }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("isEnabled", isEnabled);
                JsonUtil.sendJsonResponse(resp.getWriter(), result);
            } else {
                // Fetch only modules that exist in periodic_review_modules
                String sql = "SELECT m.id, m.primaryname, prm.IsEnabled " +
                        "FROM module m " +
                        "INNER JOIN periodic_review_modules prm ON m.id = prm.ModuleID";

                java.util.List<Map<String, Object>> modules = new java.util.ArrayList<>();

                try (PreparedStatement stmt = conn.prepareStatement(sql);
                        ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> module = new HashMap<>();
                        module.put("id", rs.getInt("id"));
                        module.put("name", rs.getString("primaryname"));
                        module.put("isEnabled", rs.getBoolean("IsEnabled"));
                        modules.add(module);
                    }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", modules);
                JsonUtil.sendJsonResponse(resp.getWriter(), result);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid moduleId", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error", 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);

        try {
            BufferedReader reader = req.getReader();
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (!json.has("moduleId") || !json.has("isEnabled")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Missing required fields", 400);
                return;
            }

            int moduleId = json.get("moduleId").getAsInt();
            boolean isEnabled = json.get("isEnabled").getAsBoolean();
            int userId = UserContextUtil.getCurrentUserId(req);

            try (Connection conn = DatabaseConnection.getConnection()) {
                // Check if record exists
                boolean exists = false;
                String checkSql = "SELECT ID FROM periodic_review_modules WHERE ModuleID = ?";
                try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                    stmt.setInt(1, moduleId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            exists = true;
                        }
                    }
                }

                if (exists) {
                    // Update
                    String updateSql = "UPDATE periodic_review_modules SET IsEnabled = ?, Last_updatedDate = NOW(), Last_updatedBy_ID = ? WHERE ModuleID = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                        stmt.setBoolean(1, isEnabled);
                        stmt.setInt(2, userId);
                        stmt.setInt(3, moduleId);
                        stmt.executeUpdate();
                    }
                } else {
                    // Insert
                    String insertSql = "INSERT INTO periodic_review_modules (ModuleID, IsEnabled, Last_updatedDate, Last_updatedBy_ID) VALUES (?, ?, NOW(), ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                        stmt.setInt(1, moduleId);
                        stmt.setBoolean(2, isEnabled);
                        stmt.setInt(3, userId);
                        stmt.executeUpdate();
                    }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                JsonUtil.sendJsonResponse(resp.getWriter(), result);

            }

        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error processing request", 500);
        }
    }
}
