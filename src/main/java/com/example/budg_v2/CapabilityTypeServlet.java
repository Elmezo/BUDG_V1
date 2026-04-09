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

@WebServlet("/api/capability-types/*")
public class CapabilityTypeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            
            if ("/dropdown".equals(pathInfo)) {
                getAllTypesForDropdown(response);
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

    private void getAllTypesForDropdown(HttpServletResponse response) throws IOException, SQLException {
        List<TypeOption> types = getAllTypes();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", types.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(types)));
        response.getWriter().write(jsonResponse.toString());
    }

    private List<TypeOption> getAllTypes() throws SQLException {
        List<TypeOption> types = new ArrayList<>();
        String sql = "SELECT ID, PrimaryName, Description FROM capability_type ORDER BY PrimaryName";
        
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                TypeOption type = new TypeOption();
                type.id = rs.getInt("ID");
                type.primaryName = rs.getString("PrimaryName");
                type.description = rs.getString("Description");
                types.add(type);
            }
        }
        
        return types;
    }

    @SuppressWarnings("unused") // fields used by JSON serialization
    private static class TypeOption {
        public int id;
        public String primaryName;
        public String description;
    }
}