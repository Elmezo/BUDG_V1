package com.example.budg_v2;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/process/step-type/list")
public class ProcessStepTypeServlet extends HttpServlet {
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //system.out.println("ProcessStepTypeServlet GET - Starting request");
        //system.out.println("ProcessStepTypeServlet GET - Request URL: " + request.getRequestURL());
        //system.out.println("ProcessStepTypeServlet GET - Request URI: " + request.getRequestURI());
        
        // Set CORS headers
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            List<ProcessStepType> stepTypes = getAllProcessStepTypes();
            
            //system.out.println("ProcessStepTypeServlet GET - Found " + stepTypes.size() + " step types");
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.add("data", gson.toJsonTree(stepTypes));
            
            //system.out.println("ProcessStepTypeServlet GET - Response JSON: " + gson.toJson(responseJson));
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (SQLException e) {
            //system.out.println("ProcessStepTypeServlet GET - SQL Error: " + e.getMessage());
            e.printStackTrace();
            
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("ProcessStepTypeServlet GET - General Error: " + e.getMessage());
            e.printStackTrace();
            
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "Server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Handle CORS preflight requests
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private List<ProcessStepType> getAllProcessStepTypes() throws SQLException {
        List<ProcessStepType> stepTypes = new ArrayList<>();
        
        //system.out.println("ProcessStepTypeServlet - Starting getAllProcessStepTypes()");
        
        // Check if table exists first
        String checkTableSql = "SHOW TABLES LIKE 'process_step_type'";
        
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkTableSql);
             ResultSet tableRs = checkStmt.executeQuery()) {
            
            if (!tableRs.next()) {
                //system.out.println("ProcessStepTypeServlet - Table process_step_type does not exist, returning empty list");
                return stepTypes; // Return empty list if table doesn't exist
            } else {
                //system.out.println("ProcessStepTypeServlet - Table process_step_type exists");
                // Log table name found
                //system.out.println("ProcessStepTypeServlet - Table found: " + tableRs.getString(1));
            }
        }
        
        // Table exists, now get data
        String sql = "SELECT ID, PrimaryName, Description FROM process_step_type ORDER BY PrimaryName";
        //system.out.println("ProcessStepTypeServlet - Executing query: " + sql);
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            //system.out.println("ProcessStepTypeServlet - Query executed successfully");
            while (rs.next()) {
                ProcessStepType stepType = new ProcessStepType();
                stepType.setId(rs.getInt("ID"));
                stepType.setPrimaryName(rs.getString("PrimaryName"));
                stepType.setDescription(rs.getString("Description"));
                stepTypes.add(stepType);
            }
        }
        
        return stepTypes;
    }

    private Connection getConnection() throws SQLException {
        // Use the same database connection as other servlets
        return com.example.budg_v2.database.DatabaseConnection.getConnection();
    }

    // Inner class for ProcessStepType
    public static class ProcessStepType {
        private int id;
        private String primaryName;
        private String description;

        public ProcessStepType() {}

        public ProcessStepType(int id, String primaryName, String description) {
            this.id = id;
            this.primaryName = primaryName;
            this.description = description;
        }

        // Getters and Setters
        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getPrimaryName() {
            return primaryName;
        }

        public void setPrimaryName(String primaryName) {
            this.primaryName = primaryName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return "ProcessStepType{" +
                    "id=" + id +
                    ", primaryName='" + primaryName + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }
}
