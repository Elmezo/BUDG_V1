package com.example.unisonsearch.servlet;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Servlet for people autocomplete search.
 * Endpoint: /UnisonSearch/api/people/search?query=...&limit=10
 * 
 * Security features:
 * - Hard limit: max 50 results
 * - PreparedStatement (SQL injection protected)
 * - Permission check placeholder (TODO: implement based on your security system)
 * - CORS headers for frontend
 * - Filters deleted users
 */
@WebServlet(name = "PeopleSearchServlet", urlPatterns = {"/UnisonSearch/api/people/search"})
public class PeopleSearchServlet extends HttpServlet {
    
    private final Gson gson = new Gson();
    private static final int HARD_MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 10;
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);
        
        // Get and validate query parameter
        String query = req.getParameter("query");
        if (query == null) {
            query = "";
        }
        query = query.trim();
        
        // Get and validate limit parameter
        int limit = DEFAULT_LIMIT;
        try {
            if (req.getParameter("limit") != null) {
                limit = Integer.parseInt(req.getParameter("limit"));
            }
        } catch (NumberFormatException e) {
            // Use default limit if invalid
        }
        
        // Enforce hard limit
        if (limit < 1) {
            limit = 1;
        }
        if (limit > HARD_MAX_LIMIT) {
            limit = HARD_MAX_LIMIT;
        }
        
        // TODO: Permission check - implement based on your security system
        // Example:
        // if (!canSearchPeople(req)) {
        //     resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        //     JsonObject error = new JsonObject();
        //     error.addProperty("error", "Insufficient permissions to search people");
        //     resp.getWriter().write(gson.toJson(error));
        //     return;
        // }
        
        try {
            JsonArray results = searchPeople(query, limit);
            
            JsonObject response = new JsonObject();
            response.add("values", results);
            response.addProperty("count", results.size());
            
            resp.getWriter().write(gson.toJson(response));
            
        } catch (Exception e) {
            System.err.println("[PeopleSearchServlet] ERROR: " + e.getMessage());
            e.printStackTrace();
            
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to search people: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Search for people in the database
     * @param query Search term (matches First_Name, Last_Name, Email)
     * @param limit Maximum number of results
     * @return JSON array of people objects
     */
    private JsonArray searchPeople(String query, int limit) throws Exception {
        JsonArray results = new JsonArray();
        
        String like = "%" + query + "%";
        
        // SQL query with PreparedStatement to prevent SQL injection
        String sql = "SELECT ID, First_Name, Last_Name, Email " +
                     "FROM people " +
                     "WHERE (Deleted_date IS NULL OR Deleted_date = 0) " +
                     "AND (? = '' OR First_Name LIKE ? OR Last_Name LIKE ? OR Email LIKE ?) " +
                     "ORDER BY First_Name, Last_Name " +
                     "LIMIT ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            // Set parameters
            ps.setString(1, query);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            ps.setInt(5, limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject person = new JsonObject();
                    
                    int id = rs.getInt("ID");
                    String firstName = rs.getString("First_Name");
                    String lastName = rs.getString("Last_Name");
                    String email = rs.getString("Email");
                    
                    // Build full name
                    String fullName = buildFullName(firstName, lastName);
                    
                    person.addProperty("id", String.valueOf(id));
                    person.addProperty("name", fullName);
                    
                    if (email != null && !email.trim().isEmpty()) {
                        person.addProperty("email", email);
                    }
                    
                    results.add(person);
                }
            }
        }
        
        return results;
    }
    
    /**
     * Build full name from first and last names
     */
    private String buildFullName(String firstName, String lastName) {
        StringBuilder name = new StringBuilder();
        
        if (firstName != null && !firstName.trim().isEmpty()) {
            name.append(firstName.trim());
        }
        
        if (lastName != null && !lastName.trim().isEmpty()) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(lastName.trim());
        }
        
        // Return ID if no name available
        return name.length() > 0 ? name.toString() : "Unknown";
    }
    
    // TODO: Implement permission check based on your security system
    // private boolean canSearchPeople(HttpServletRequest req) {
    //     // Check user role/permissions
    //     // Example: 
    //     // HttpSession session = req.getSession(false);
    //     // if (session == null) return false;
    //     // String userRole = (String) session.getAttribute("userRole");
    //     // return "Admin".equals(userRole) || "Manager".equals(userRole);
    //     return true; // Default: allow all for now
    // }
}
