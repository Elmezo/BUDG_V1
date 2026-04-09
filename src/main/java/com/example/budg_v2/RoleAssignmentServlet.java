package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.dao.RoleAssignmentDAO;
import com.example.budg_v2.model.RoleAssignment;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/role-assignments")
public class RoleAssignmentServlet extends HttpServlet {
    private transient RoleAssignmentDAO dao;
    private transient Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.dao = new RoleAssignmentDAO();
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            List<RoleAssignment> list = dao.listAll();
            resp.getWriter().write(gson.toJson(list));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write('{'+"\"error\":\""+ e.getMessage().replace("\"","'") +"\""+'}');
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) (Map<?, ?>) gson.fromJson(sb.toString(), Map.class);
            Object oRoleId = body.get("objectRoleId");
            Integer objectRoleId = null;
            if (oRoleId instanceof Number) {
                objectRoleId = ((Number) oRoleId).intValue();
            } else if (oRoleId != null) {
                try { objectRoleId = Integer.parseInt(oRoleId.toString()); } catch (Exception ignored) {}
            }
            String usersJson = (String) body.get("usersJson");
            if (usersJson == null || usersJson.isEmpty()) usersJson = "[]";
            if (objectRoleId == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"objectRoleId is required\"}");
                return;
            }
            
            // Check if this is a create or update by checking if assignment exists
            boolean isCreate = !roleAssignmentExists(objectRoleId);
            
            // Capture old state before update/create
            Map<String, Object> oldState = buildRoleAssignmentState(objectRoleId);
            
            RoleAssignment saved = dao.upsert(objectRoleId, usersJson);
            
            // Capture new state after update/create
            Map<String, Object> newState = buildRoleAssignmentState(objectRoleId);
            
            // Log activity - Create or Update case
            // Component should be facet name (module name from module table)
            if (saved != null && newState.containsKey("facetName")) {
                String facetName = (String) newState.get("facetName");
                if (facetName != null && !facetName.isEmpty()) {
                    // Pass context map with facetName for strategy
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("facetName", facetName);
                    contextMap.put("roleName", newState.get("roleName"));
                    
                    String changeType = isCreate ? ActivityLogConstants.CHANGE_TYPE_CREATE : ActivityLogConstants.CHANGE_TYPE_UPDATE;
                    
                    ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_ROLES_ASSIGNMENT,
                        facetName, changeType,
                        oldState, newState, contextMap);
                }
            }
            
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            ok.put("data", saved);
            resp.getWriter().write(gson.toJson(ok));
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "Database error" : e.getMessage();
            if (msg.toLowerCase().contains("invalid objectroleid")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            resp.getWriter().write('{'+"\"error\":\""+ msg.replace("\"","'") +"\""+'}');
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write('{'+"\"error\":\""+ e.getMessage().replace("\"","'") +"\""+'}');
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String idParam = req.getParameter("id");
        if (idParam == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"id is required\"}");
            return;
        }
        try {
            int id = Integer.parseInt(idParam);
            
            // Capture old state before deletion
            Map<String, Object> oldState = getRoleAssignmentStateById(id);
            
            boolean ok = dao.deleteById(id);
            
            // Log activity - Delete case
            if (ok && oldState != null && oldState.containsKey("facetName")) {
                String facetName = (String) oldState.get("facetName");
                if (facetName != null && !facetName.isEmpty()) {
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("facetName", facetName);
                    contextMap.put("roleName", oldState.get("roleName"));
                    
                    // For delete, newState should be empty map
                    Map<String, Object> emptyNewState = new HashMap<>();
                    
                    ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_ROLES_ASSIGNMENT,
                        facetName, ActivityLogConstants.CHANGE_TYPE_DELETE,
                        oldState, emptyNewState, contextMap);
                }
            }
            
            Map<String, Object> res = new HashMap<>();
            res.put("success", ok);
            resp.getWriter().write(gson.toJson(res));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write('{'+"\"error\":\""+ e.getMessage().replace("\"","'") +"\""+'}');
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.handlePreflight(resp);
    }
    
    /**
     * Check if role assignment exists for the given objectRoleId
     */
    private boolean roleAssignmentExists(Integer objectRoleId) {
        try {
            String sql = "SELECT COUNT(*) FROM role_assignment WHERE objectroleid = ?";
            try (java.sql.Connection c = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, objectRoleId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking role assignment existence: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Build role assignment state map for activity logging
     * Returns map with facetName, roleName, and membersAssigned
     */
    private Map<String, Object> buildRoleAssignmentState(Integer objectRoleId) throws SQLException {
        Map<String, Object> state = new HashMap<>();
        if (objectRoleId == null) {
            return state;
        }
        
        // Get existing role assignment
        String sql = "SELECT ra.id, ra.objectroleid, ra.users, " +
                " r.primaryname AS role_name, m.primaryname AS module_name " +
                " FROM role_assignment ra " +
                " LEFT JOIN object_role r ON ra.objectroleid = r.id " +
                " LEFT JOIN module m ON r.module = m.id " +
                " WHERE ra.objectroleid = ?";
        
        try (java.sql.Connection c = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, objectRoleId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    state.put("facetName", rs.getString("module_name"));
                    state.put("roleName", rs.getString("role_name"));
                    
                    // Parse usersJson to get member names
                    String usersJson = rs.getString("users");
                    String membersAssigned = formatMembersFromJson(usersJson);
                    state.put("membersAssigned", membersAssigned);
                } else {
                    // If no existing assignment, try to get role and module info
                    String roleSql = "SELECT r.primaryname AS role_name, m.primaryname AS module_name " +
                            " FROM object_role r " +
                            " LEFT JOIN module m ON r.module = m.id " +
                            " WHERE r.id = ?";
                    try (java.sql.PreparedStatement rolePs = c.prepareStatement(roleSql)) {
                        rolePs.setInt(1, objectRoleId);
                        try (java.sql.ResultSet roleRs = rolePs.executeQuery()) {
                            if (roleRs.next()) {
                                state.put("facetName", roleRs.getString("module_name"));
                                state.put("roleName", roleRs.getString("role_name"));
                                state.put("membersAssigned", "");
                            }
                        }
                    }
                }
            }
        }
        return state;
    }
    
    /**
     * Get role assignment state by ID for logging (for delete operations)
     */
    private Map<String, Object> getRoleAssignmentStateById(int id) {
        try {
            String sql = "SELECT ra.id, ra.objectroleid, ra.users, " +
                    " r.primaryname AS role_name, m.primaryname AS module_name " +
                    " FROM role_assignment ra " +
                    " LEFT JOIN object_role r ON ra.objectroleid = r.id " +
                    " LEFT JOIN module m ON r.module = m.id " +
                    " WHERE ra.id = ?";
            
            try (java.sql.Connection c = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> state = new HashMap<>();
                        state.put("facetName", rs.getString("module_name"));
                        state.put("roleName", rs.getString("role_name"));
                        
                        // Parse usersJson to get member names
                        String usersJson = rs.getString("users");
                        String membersAssigned = formatMembersFromJson(usersJson);
                        state.put("membersAssigned", membersAssigned);
                        return state;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting role assignment state: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Format members from JSON array to comma-separated string
     * Format: "name(email), name2(email2)"
     */
    private String formatMembersFromJson(String usersJson) {
        if (usersJson == null || usersJson.trim().isEmpty() || "[]".equals(usersJson.trim())) {
            return "";
        }
        try {
            JsonArray usersArray = JsonParser.parseString(usersJson).getAsJsonArray();
            java.util.List<String> memberStrings = new java.util.ArrayList<>();
            
            for (int i = 0; i < usersArray.size(); i++) {
                com.google.gson.JsonElement element = usersArray.get(i);
                String name = null;
                String email = null;
                
                if (element.isJsonObject()) {
                    // If it's an object, try to extract name and email
                    com.google.gson.JsonObject userObj = element.getAsJsonObject();
                    if (userObj.has("name")) {
                        name = userObj.get("name").getAsString();
                    } else if (userObj.has("userName")) {
                        name = userObj.get("userName").getAsString();
                    } else if (userObj.has("first_name") && userObj.has("last_name")) {
                        String firstName = userObj.has("first_name") ? userObj.get("first_name").getAsString() : "";
                        String lastName = userObj.has("last_name") ? userObj.get("last_name").getAsString() : "";
                        name = (firstName + " " + lastName).trim();
                    }
                    if (userObj.has("email")) {
                        email = userObj.get("email").getAsString();
                    }
                } else if (element.isJsonPrimitive()) {
                    // If it's a primitive (likely a user ID), fetch user details from database
                    try {
                        int userId = element.getAsInt();
                        java.util.Map<String, String> userInfo = getUserInfoById(userId);
                        if (userInfo != null) {
                            name = userInfo.get("name");
                            email = userInfo.get("email");
                        } else {
                            // Fallback to user ID if not found
                            name = "User ID: " + userId;
                            email = "";
                        }
                    } catch (NumberFormatException e) {
                        // If not a number, treat as string
                        name = element.getAsString();
                        email = "";
                    }
                }
                
                // Format as "name(email)" or just "name" if no email
                if (name != null && !name.isEmpty()) {
                    if (email != null && !email.isEmpty()) {
                        memberStrings.add(name + "(" + email + ")");
                    } else {
                        memberStrings.add(name);
                    }
                }
            }
            return String.join(", ", memberStrings);
        } catch (Exception e) {
            System.err.println("Error formatting members from JSON: " + e.getMessage());
            return usersJson; // Fallback to raw JSON if parsing fails
        }
    }
    
    /**
     * Get user name and email by user ID from database
     */
    private java.util.Map<String, String> getUserInfoById(int userId) {
        java.util.Map<String, String> userInfo = new HashMap<>();
        try {
            String sql = "SELECT first_name, last_name, email FROM people WHERE ID = ?";
            try (java.sql.Connection c = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String firstName = rs.getString("first_name");
                        String lastName = rs.getString("last_name");
                        String email = rs.getString("email");
                        
                        // Build full name
                        String name = "";
                        if (firstName != null && !firstName.trim().isEmpty()) {
                            name = firstName.trim();
                        }
                        if (lastName != null && !lastName.trim().isEmpty()) {
                            name = (name + " " + lastName.trim()).trim();
                        }
                        if (name.isEmpty()) {
                            name = "User ID: " + userId;
                        }
                        
                        userInfo.put("name", name);
                        userInfo.put("email", email != null ? email : "");
                        return userInfo;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user info: " + e.getMessage());
        }
        return null;
    }
}