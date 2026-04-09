package com.example.unisonsearch.servlet;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Servlet to fetch UNISON_DEFAULTS configuration from app_config table.
 */
@WebServlet(name = "UnisonDefaultsServlet", urlPatterns = { "/UnisonSearch/api/defaults", "/api/unison-defaults" })
public class UnisonDefaultsServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private static final String CONFIG_KEY = "UNISON_DEFAULTS";

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT definition FROM app_config WHERE config_key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, CONFIG_KEY);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String definition = rs.getString("definition");

                        // Parse and validate JSON
                        try {
                            JsonObject jsonObject = JsonParser.parseString(definition).getAsJsonObject();

                            String response = gson.toJson(jsonObject);

                            resp.getWriter().write(response);
                        } catch (Exception e) {
                            System.err.println("[UnisonDefaultsServlet] ERROR: Failed to parse JSON from database: "
                                    + e.getMessage());
                            e.printStackTrace();
                            // If parsing fails, return as plain string wrapped in object
                            JsonObject wrapper = new JsonObject();
                            wrapper.addProperty("definition", definition);
                            resp.getWriter().write(gson.toJson(wrapper));
                        }
                    } else {
                        System.err.println(
                                "[UnisonDefaultsServlet] WARNING: UNISON_DEFAULTS not found in app_config table!");
                        System.err.println("[UnisonDefaultsServlet] Please insert the configuration using:");
                        System.err.println(
                                "[UnisonDefaultsServlet] INSERT INTO app_config (config_key, definition) VALUES ('UNISON_DEFAULTS', '<json_data>');");

                        // Config not found, return default empty structure
                        JsonObject emptyDefaults = new JsonObject();
                        emptyDefaults.add("facets", gson.toJsonTree(new Object[0]));
                        emptyDefaults.addProperty("lastUpdated", System.currentTimeMillis());
                        resp.getWriter().write(gson.toJson(emptyDefaults));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[UnisonDefaultsServlet] SQL ERROR: " + e.getMessage());
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            System.err.println("[UnisonDefaultsServlet] UNEXPECTED ERROR: " + e.getMessage());
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Server error: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
}
