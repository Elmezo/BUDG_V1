package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for change request type operations
 * Endpoints:
 * GET /api/changerequest_types - List all CR types
 */
@WebServlet("/api/changerequest_types")
public class ChangeRequestTypeServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestTypeServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            List<Map<String, Object>> types = new ArrayList<>();

            String sql = "SELECT ID, PrimaryName FROM changerequest_type ORDER BY PrimaryName";

            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Map<String, Object> type = new HashMap<>();
                    type.put("id", rs.getInt("ID"));
                    type.put("name", rs.getString("PrimaryName"));
                    types.add(type);
                }
            }

            response.getWriter().write(gson.toJson(types));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
}
