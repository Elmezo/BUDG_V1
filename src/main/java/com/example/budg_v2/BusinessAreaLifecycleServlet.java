package com.example.budg_v2;

import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

@WebServlet("/api/business-area-lifecycles/*")
public class BusinessAreaLifecycleServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(BusinessAreaLifecycleServlet.class.getName());

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
                List<LifecycleOption> lifecycles = getAllLifecycles();
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("count", lifecycles.size());
                
                com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
                for (LifecycleOption lifecycle : lifecycles) {
                    com.google.gson.JsonObject lifecycleJson = new com.google.gson.JsonObject();
                    lifecycleJson.addProperty("id", lifecycle.getId());
                    lifecycleJson.addProperty("name", lifecycle.getName());
                    lifecycleJson.addProperty("description", lifecycle.getDescription());
                    dataArray.add(lifecycleJson);
                }
                responseJson.add("data", dataArray);
                
                response.getWriter().write(responseJson.toString());
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving business area lifecycles: " + e.getMessage(), e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error: " + e.getMessage(), e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllLifecyclesForDropdown(HttpServletResponse response) throws IOException, SQLException {
        List<LifecycleOption> lifecycles = getAllLifecycles();
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("success", true);
        responseJson.addProperty("count", lifecycles.size());
        
        com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
        for (LifecycleOption lifecycle : lifecycles) {
            com.google.gson.JsonObject lifecycleJson = new com.google.gson.JsonObject();
            lifecycleJson.addProperty("id", lifecycle.getId());
            lifecycleJson.addProperty("name", lifecycle.getName());
            lifecycleJson.addProperty("description", lifecycle.getDescription());
            dataArray.add(lifecycleJson);
        }
        responseJson.add("data", dataArray);
        response.getWriter().write(responseJson.toString());
    }

    private List<LifecycleOption> getAllLifecycles() throws SQLException {
        List<LifecycleOption> lifecycles = new ArrayList<>();
        String sql = "SELECT ID, PrimaryName, Description FROM business_area_lifecycle ORDER BY PrimaryName";

        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                LifecycleOption lifecycle = new LifecycleOption();
                lifecycle.setId(rs.getInt("ID"));
                lifecycle.setName(rs.getString("PrimaryName"));
                lifecycle.setDescription(rs.getString("Description"));
                lifecycles.add(lifecycle);
            }
        }
        return lifecycles;
    }

    // Inner class for lifecycle option
    public static class LifecycleOption {
        private int id;
        private String name;
        private String description;

        public LifecycleOption() {}

        public LifecycleOption(int id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
