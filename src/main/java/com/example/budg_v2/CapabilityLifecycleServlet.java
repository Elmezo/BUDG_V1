package com.example.budg_v2;

import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/capability-lifecycles/*")
public class CapabilityLifecycleServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            
            if ("/dropdown".equals(pathInfo)) {
                getAllLifecyclesForDropdown(response);
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid endpoint", 404);
            }
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error occurred: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllLifecyclesForDropdown(HttpServletResponse response) throws IOException, SQLException {
        List<LifecycleOption> lifecycles = getAllLifecycles();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", lifecycles.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(lifecycles)));
        response.getWriter().write(jsonResponse.toString());
    }

    private List<LifecycleOption> getAllLifecycles() throws SQLException {
        List<LifecycleOption> lifecycles = new ArrayList<>();
        String sql = "SELECT ID, PrimaryName, Description FROM capability_lifecyle ORDER BY PrimaryName";
        
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                LifecycleOption lifecycle = new LifecycleOption();
                lifecycle.id = rs.getInt("ID");
                lifecycle.primaryName = rs.getString("PrimaryName");
                lifecycle.description = rs.getString("Description");
                lifecycles.add(lifecycle);
            }
        }
        
        return lifecycles;
    }

    @SuppressWarnings("unused") // fields used by JSON serialization
    private static class LifecycleOption {
        public int id;
        public String primaryName;
        public String description;
    }
}