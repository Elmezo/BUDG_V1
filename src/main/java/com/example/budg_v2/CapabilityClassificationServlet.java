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

@WebServlet("/api/capability-classifications/*")
public class CapabilityClassificationServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            
            if ("/dropdown".equals(pathInfo)) {
                getAllClassificationsForDropdown(response);
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

    private void getAllClassificationsForDropdown(HttpServletResponse response) throws IOException, SQLException {
        List<ClassificationOption> classifications = getAllClassifications();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", classifications.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(classifications)));
        response.getWriter().write(jsonResponse.toString());
    }

    private List<ClassificationOption> getAllClassifications() throws SQLException {
        List<ClassificationOption> classifications = new ArrayList<>();
        String sql = "SELECT ID, PrimaryName, Description FROM capability_classification ORDER BY PrimaryName";
        
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                ClassificationOption classification = new ClassificationOption();
                classification.id = rs.getInt("ID");
                classification.primaryName = rs.getString("PrimaryName");
                classification.description = rs.getString("Description");
                classifications.add(classification);
            }
        }
        
        return classifications;
    }

    @SuppressWarnings("unused") // fields used by JSON serialization
    private static class ClassificationOption {
        public int id;
        public String primaryName;
        public String description;
    }
}