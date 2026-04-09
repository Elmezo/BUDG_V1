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
 * Servlet for role operations (for workflow lane assignments)
 * Endpoints:
 * GET /api/workflow/roles?module={entityId} - Get roles for module
 */
@WebServlet("/api/workflow/roles")
public class WorkflowRoleServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowRoleServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String moduleParam = request.getParameter("module");

        try {
            List<Map<String, Object>> roles = new ArrayList<>();

            String sql;
            PreparedStatement stmt;
            Connection conn = DatabaseConnection.getConnection();

            if (moduleParam != null && !moduleParam.isEmpty()) {
                // Get roles for specific module
                int moduleId = Integer.parseInt(moduleParam);
                sql = "SELECT DISTINCT r.ID, r.PrimaryName FROM object_role r " +
                        "WHERE r.module = ? ORDER BY r.PrimaryName";
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, moduleId);
            } else {
                // Get all roles
                sql = "SELECT ID, PrimaryName FROM object_role ORDER BY PrimaryName";
                stmt = conn.prepareStatement(sql);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> role = new HashMap<>();
                    role.put("id", rs.getInt("ID"));
                    role.put("name", rs.getString("PrimaryName"));
                    roles.add(role);
                }
            }

            stmt.close();
            conn.close();

            response.getWriter().write(gson.toJson(roles));

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid module ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
}
