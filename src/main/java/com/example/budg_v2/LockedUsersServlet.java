package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.AppRoleNames;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "LockedUsersServlet", urlPatterns = {"/api/locked-users/*"})
public class LockedUsersServlet extends HttpServlet {
    
    private final Gson gson = new Gson();
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
            // Check super admin role
            if (!isSuperAdmin(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Forbidden: super admin role required\"}");
                return;
            }
            
            // Get list of locked users
            List<Map<String, Object>> lockedUsers = getLockedUsers();
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(lockedUsers));
            
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error";
            response.getWriter().write("{\"error\":\"" + msg + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
            // Check super admin role
            if (!isSuperAdmin(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Forbidden: super admin role required\"}");
                return;
            }
            
            String pathInfo = request.getPathInfo();
            
            // Handle unlock endpoint: /api/locked-users/unlock
            if (pathInfo != null && pathInfo.equals("/unlock")) {
                // Parse request body
                JsonObject requestBody = gson.fromJson(request.getReader(), JsonObject.class);
                JsonArray emailsArray = requestBody.getAsJsonArray("emails");
                
                if (emailsArray == null || emailsArray.size() == 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"No email addresses provided\"}");
                    return;
                }
                
                // Extract email addresses
                List<String> emails = new ArrayList<>();
                for (int i = 0; i < emailsArray.size(); i++) {
                    emails.add(emailsArray.get(i).getAsString());
                }
                
                // Get locked user info before unlocking (for logging)
                List<Map<String, Object>> usersToUnlock = getLockedUsersByEmails(emails);
                
                // Unlock users
                unlockUsers(emails);
                
                // Log activity for each unlocked user - Delete case
                for (Map<String, Object> user : usersToUnlock) {
                    // Build oldState with all required fields, ensuring no null values break logging
                    Map<String, Object> oldState = new HashMap<>();
                    String email = user.get("email") != null ? user.get("email").toString() : "Unknown Email";
                    String name = user.get("name") != null ? user.get("name").toString() : "Unknown User";
                    String role = user.get("role") != null ? user.get("role").toString() : "Unknown Role";
                    String lockedDate = user.get("locked_date") != null ? user.get("locked_date").toString() : "Unknown Date";
                    
                    oldState.put("email", email);
                    oldState.put("name", name);
                    oldState.put("role", role);
                    oldState.put("status", "Locked"); // User is locked
                    oldState.put("lockedFrom", lockedDate);
                    oldState.put("lockedUntil", null); // No unlock date specified
                    
                    // Only log if we have at least email and name (not "Unknown")
                    if (!email.equals("Unknown Email") && !name.equals("Unknown User") && !oldState.isEmpty()) {
                        // Use user's name as component name (similar to ManageLocks using object name)
                        String componentName = name != null && !name.trim().isEmpty() ? name : ActivityLogConstants.SETTING_LOCKED_USERS;
                        
                        Map<String, Object> contextMap = new HashMap<>();
                        contextMap.put("name", name); // Pass name to strategy for component name resolution
                        
                        ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_LOCKED_USERS,
                            componentName, ActivityLogConstants.CHANGE_TYPE_DELETE,
                            oldState, null, contextMap);
                    }
                }
                
                // Return success message
                Map<String, String> result = new HashMap<>();
                result.put("message", "The user accounts have been unlocked.");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(gson.toJson(result));
                return;
            }
            
            // Unknown endpoint
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid endpoint\"}");
            
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error";
            response.getWriter().write("{\"error\":\"" + msg + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
    
    /**
     * Get list of locked users
     */
    private List<Map<String, Object>> getLockedUsers() throws SQLException {
        String sql = """
            SELECT 
                p.Email,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                COALESCE(r.primaryname, 'User') AS role,
                p.locked_date
            FROM people p
            LEFT JOIN role r ON p.System_Role = r.id
            WHERE p.is_locked = 1 
            AND p.Deleted_date IS NULL
            ORDER BY p.locked_date DESC
        """;
        
        List<Map<String, Object>> lockedUsers = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                // Ensure all fields have values, even if null
                String email = rs.getString("Email");
                user.put("email", email != null ? email : "");
                
                String name = rs.getString("name");
                user.put("name", name != null ? name : "");
                
                String role = rs.getString("role");
                user.put("role", role != null ? role : "User");
                
                // Format locked_date
                java.sql.Timestamp lockedDate = rs.getTimestamp("locked_date");
                if (lockedDate != null) {
                    // Format as: YYYY-MM-DD HH:mm:ss
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    user.put("locked_date", sdf.format(lockedDate));
                } else {
                    user.put("locked_date", null);
                }
                
                lockedUsers.add(user);
            }
        } catch (SQLException e) {
            // Log error but don't fail the operation
            System.err.println("Error getting locked users: " + e.getMessage());
            // Return empty list if query fails
            return new ArrayList<>();
        }
        
        return lockedUsers;
    }
    
    /**
     * Unlock user accounts
     */
    private void unlockUsers(List<String> emails) throws SQLException {
        if (emails == null || emails.isEmpty()) {
            return;
        }
        
        // Build placeholders for IN clause
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < emails.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = String.format("""
            UPDATE people 
            SET is_locked = 0, 
                locked_date = NULL, 
                lock_reason = NULL 
            WHERE Email IN (%s) 
            AND Deleted_date IS NULL
        """, placeholders.toString());
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (int i = 0; i < emails.size(); i++) {
                pstmt.setString(i + 1, emails.get(i));
            }
            
            pstmt.executeUpdate();
        }
    }
    
    /**
     * Get locked users by email addresses
     */
    private List<Map<String, Object>> getLockedUsersByEmails(List<String> emails) throws SQLException {
        if (emails == null || emails.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Build placeholders for IN clause
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < emails.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = String.format("""
            SELECT 
                p.Email,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                COALESCE(r.primaryname, 'User') AS role,
                p.locked_date
            FROM people p
            LEFT JOIN role r ON p.System_Role = r.id
            WHERE p.Email IN (%s)
            AND p.is_locked = 1 
            AND p.Deleted_date IS NULL
        """, placeholders.toString());
        
        List<Map<String, Object>> lockedUsers = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (int i = 0; i < emails.size(); i++) {
                pstmt.setString(i + 1, emails.get(i));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    // Ensure all fields have values, even if null
                    String email = rs.getString("Email");
                    user.put("email", email != null ? email : "");
                    
                    String name = rs.getString("name");
                    user.put("name", name != null ? name : "");
                    
                    String role = rs.getString("role");
                    user.put("role", role != null ? role : "User");
                    
                    // Format locked_date
                    java.sql.Timestamp lockedDate = rs.getTimestamp("locked_date");
                    if (lockedDate != null) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        user.put("locked_date", sdf.format(lockedDate));
                    } else {
                        user.put("locked_date", null);
                    }
                    
                    lockedUsers.add(user);
                }
            }
        } catch (SQLException e) {
            // Log error but don't fail the operation
            System.err.println("Error getting locked users by emails: " + e.getMessage());
            // Return empty list if query fails
            return new ArrayList<>();
        }
        
        return lockedUsers;
    }
    
    /**
     * Check if user is super admin
     */
    private boolean isSuperAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("userRole");
        if (roleObj == null) return false;
        return AppRoleNames.isSuperAdminName(roleObj.toString());
    }
}

