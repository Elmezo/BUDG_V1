package com.example.budg_v2;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// import jakarta.servlet.http.HttpSession; // Disabled with authentication
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.example.budg_v2.database.DatabaseConnection;

/**
 * Servlet to handle Export Objects configuration
 * Manages the EXPORT_PEOPLE_ENABLED setting in app_config table
 */
@WebServlet("/admin/api/export-config")
public class ExportConfigServlet extends HttpServlet {
    private static final String CONFIG_KEY = "EXPORT_PEOPLE_ENABLED";
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // Check admin authentication - DISABLED FOR DEVELOPMENT
        // HttpSession session = request.getSession(false);
        // if (session == null || session.getAttribute("user") == null) {
        // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // out.print("{\"error\": \"Unauthorized\"}");
        // return;
        // }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT config_key, definition FROM app_config WHERE config_key = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, CONFIG_KEY);
                try (ResultSet rs = stmt.executeQuery()) {
                    JsonObject result = new JsonObject();
                    if (rs.next()) {
                        result.addProperty("config_key", rs.getString("config_key"));
                        result.addProperty("definition", rs.getString("definition"));
                        result.addProperty("enabled", "true".equalsIgnoreCase(rs.getString("definition")));
                    } else {
                        // Default to false if not found
                        result.addProperty("config_key", CONFIG_KEY);
                        result.addProperty("definition", "false");
                        result.addProperty("enabled", false);

                        // Insert default value
                        insertDefaultConfig(conn);
                    }
                    out.print(gson.toJson(result));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // Check admin authentication - DISABLED FOR DEVELOPMENT
        // HttpSession session = request.getSession(false);
        // if (session == null || session.getAttribute("user") == null) {
        // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // out.print("{\"error\": \"Unauthorized\"}");
        // return;
        // }

        // Read request body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JsonObject requestData = gson.fromJson(sb.toString(), JsonObject.class);
        boolean enabled = requestData.has("enabled") && requestData.get("enabled").getAsBoolean();
        String definition = enabled ? "true" : "false";

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if config exists
            String checkSql = "SELECT COUNT(*) FROM app_config WHERE config_key = ?";
            boolean exists = false;
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, CONFIG_KEY);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        exists = true;
                    }
                }
            }

            if (exists) {
                // Update existing config
                String updateSql = "UPDATE app_config SET definition = ? WHERE config_key = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, definition);
                    stmt.setString(2, CONFIG_KEY);
                    stmt.executeUpdate();
                }
            } else {
                // Insert new config
                String insertSql = "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, CONFIG_KEY);
                    stmt.setString(2, definition);
                    stmt.executeUpdate();
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("config_key", CONFIG_KEY);
            result.addProperty("definition", definition);
            result.addProperty("enabled", enabled);
            out.print(gson.toJson(result));

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
        }
    }

    private void insertDefaultConfig(Connection conn) throws SQLException {
        String insertSql = "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setString(1, CONFIG_KEY);
            stmt.setString(2, "false");
            stmt.executeUpdate();
        }
    }
}
