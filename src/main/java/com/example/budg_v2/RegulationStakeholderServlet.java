package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Regulation Stakeholder Servlet for handling stakeholder lookup operations
 * Endpoints: /api/regulation/stakeholder/lookup
 */
@WebServlet("/api/regulation/stakeholder/*")
public class RegulationStakeholderServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String requestURI = request.getRequestURI();

            //system.out.println("RegulationStakeholderServlet - Request URI: " + requestURI);

            // Handle stakeholder lookup requests
            if (requestURI.contains("/regulation/stakeholder/lookup")) {
                handleLookupRequest(request, response, conn, out);
                return;
            }

            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"error\": \"Invalid endpoint\"}");

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    // Handle lookup requests for dropdowns
    private void handleLookupRequest(HttpServletRequest request, HttpServletResponse response,
                                     Connection conn, PrintWriter out) throws SQLException {
        String type = request.getParameter("type");
        String roleId = request.getParameter("roleId");
        
        //system.out.println("RegulationStakeholderServlet - Lookup request type: " + type);
        //system.out.println("RegulationStakeholderServlet - Role ID parameter: " + roleId);

        if (type == null) {
            //system.out.println("RegulationStakeholderServlet - Error: Type parameter is required");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Type parameter is required\"}");
            return;
        }

        List<JsonObject> results = new ArrayList<>();

        switch (type.toLowerCase()) {
            case "roles":
                //system.out.println("RegulationStakeholderServlet - Getting regulation roles...");
                results = getRegulationRoles(conn);
                break;
            case "rolestatus":
                //system.out.println("RegulationStakeholderServlet - Getting role statuses...");
                results = getRoleStatuses(conn);
                break;
            case "people":
                //system.out.println("RegulationStakeholderServlet - Getting people for role ID: " + roleId);
                if (roleId != null) {
                    results = getPeopleByRole(conn, Integer.parseInt(roleId));
                }
                break;
            default:
                //system.out.println("RegulationStakeholderServlet - Error: Invalid type parameter: " + type);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid type parameter\"}");
                return;
        }

        //system.out.println("RegulationStakeholderServlet - Returning " + results.size() + " results for type: " + type);
        out.print(gson.toJson(results));
    }

    // Get module ID by module name
    private int getModuleId(Connection conn, String moduleName) throws SQLException {
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        //system.out.println("RegulationStakeholderServlet - Looking for module: '" + moduleName + "'");

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, moduleName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int moduleId = rs.getInt("id");
                    //system.out.println("RegulationStakeholderServlet - Found module ID: " + moduleId + " for '" + moduleName + "'");
                    return moduleId;
                }
            }
        }

        //system.out.println("RegulationStakeholderServlet - Module not found: " + moduleName);
        throw new SQLException("Module not found: " + moduleName);
    }

    // Get regulation roles
    private List<JsonObject> getRegulationRoles(Connection conn) throws SQLException {
        String sql = """
            SELECT 
                orl.ID AS RoleID,
                orl.PrimaryName AS Role
            FROM object_role orl
            WHERE orl.module = ?
            ORDER BY orl.PrimaryName
            """;

        List<JsonObject> roles = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int moduleId = getModuleId(conn, "Regulation");
            //system.out.println("RegulationStakeholderServlet - Module ID for 'Regulation': " + moduleId);
            stmt.setInt(1, moduleId);

            try (ResultSet rs = stmt.executeQuery()) {
                //system.out.println("RegulationStakeholderServlet - Executing query for roles with module ID: " + moduleId);
                while (rs.next()) {
                    JsonObject role = new JsonObject();
                    role.addProperty("RoleID", rs.getInt("RoleID"));
                    role.addProperty("Role", rs.getString("Role"));
                    roles.add(role);
                    //system.out.println("RegulationStakeholderServlet - Found role: " + rs.getString("Role") + " (ID: " + rs.getInt("RoleID") + ")");
                }
                //system.out.println("RegulationStakeholderServlet - Total roles found: " + count);
            }
        }

        return roles;
    }

    // Get role statuses (from object_x_ip_status table for edit mode)
    private List<JsonObject> getRoleStatuses(Connection conn) throws SQLException {
        String sql = "SELECT ID, PrimaryName FROM object_x_ip_status ORDER BY PrimaryName";
        //system.out.println("RegulationStakeholderServlet - Getting role statuses...");

        List<JsonObject> statuses = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject status = new JsonObject();
                    status.addProperty("ID", rs.getInt("ID"));
                    status.addProperty("PrimaryName", rs.getString("PrimaryName"));
                    statuses.add(status);
                    //system.out.println("RegulationStakeholderServlet - Found status: " + rs.getString("PrimaryName") + " (ID: " + rs.getInt("ID") + ")");
                }
                //system.out.println("RegulationStakeholderServlet - Total statuses found: " + count);
            }
        }

        return statuses;
    }

    // Get people by role
    private List<JsonObject> getPeopleByRole(Connection conn, int roleId) throws SQLException {
        //system.out.println("RegulationStakeholderServlet - Getting people for role ID: " + roleId);
        
        // Get the users JSON from role_assignment
        String getUsersQuery = "SELECT users FROM role_assignment WHERE objectroleid = ?";
        List<Integer> userIds = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(getUsersQuery)) {
            stmt.setInt(1, roleId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String usersJson = rs.getString("users");
                    //system.out.println("RegulationStakeholderServlet - Found users JSON: " + usersJson);
                    
                    if (usersJson != null && !usersJson.trim().isEmpty()) {
                        try {
                            usersJson = usersJson.trim();
                            // Remove brackets if present
                            if (usersJson.startsWith("[") && usersJson.endsWith("]")) {
                                usersJson = usersJson.substring(1, usersJson.length() - 1);
                            }
                            
                            if (!usersJson.isEmpty()) {
                                String[] userIdStrings = usersJson.split(",");
                                for (String userIdStr : userIdStrings) {
                                    try {
                                        int userId = Integer.parseInt(userIdStr.trim());
                                        userIds.add(userId);
                                        //system.out.println("RegulationStakeholderServlet - Added user ID: " + userId);
                                    } catch (NumberFormatException e) {
                                        //system.out.println("RegulationStakeholderServlet - Skipping invalid user ID: " + userIdStr);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            //system.out.println("RegulationStakeholderServlet - Error parsing users JSON: " + e.getMessage());
                        }
                    }
                } else {
                    //system.out.println("RegulationStakeholderServlet - No role assignment found for role ID: " + roleId);
                }
            }
        }
        
        if (userIds.isEmpty()) {
            //system.out.println("RegulationStakeholderServlet - No user IDs found for role ID: " + roleId);
            return new ArrayList<>();
        }
        
        // Now get the people details
        String sql = """
            SELECT
                p.ID AS PeopleID,
                CONCAT(p.First_Name, ' ', p.Last_Name, ' (', p.Email, ')') AS Name
            FROM people p
            WHERE p.ID IN (%s)
            AND p.Deleted_date IS NULL
            ORDER BY p.First_Name, p.Last_Name
            """;
        
        // Create placeholders for IN clause
        String placeholders = userIds.stream()
            .map(id -> "?")
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        
        sql = String.format(sql, placeholders);
        //system.out.println("RegulationStakeholderServlet - Executing query: " + sql);
        //system.out.println("RegulationStakeholderServlet - With user IDs: " + userIds);

        List<JsonObject> people = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Set the user IDs as parameters
            for (int i = 0; i < userIds.size(); i++) {
                stmt.setInt(i + 1, userIds.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject person = new JsonObject();
                    person.addProperty("PeopleID", rs.getInt("PeopleID"));
                    person.addProperty("Name", rs.getString("Name"));
                    people.add(person);
                }
            }
        }

        return people;
    }

    // Get people by role with segment filtering
    @SuppressWarnings("unused")
    private List<JsonObject> getPeopleByRoleWithSegmentFilter(Connection conn, int roleId, int objectId) throws SQLException {
        List<JsonObject> people = new ArrayList<>();
        
        try {
            List<Map<String, Object>> users = SegmentAccessService.getUsersForStakeholderSelection(objectId, "Regulation", roleId);
            
            for (Map<String, Object> user : users) {
                JsonObject person = new JsonObject();
                person.addProperty("PeopleID", (Integer) user.get("PeopleID"));
                person.addProperty("Name", (String) user.get("Name"));
                people.add(person);
            }
        } catch (SQLException e) {
            System.err.println("Error getting users for stakeholder selection: " + e.getMessage());
            e.printStackTrace();
            // Fallback to original behavior on error
            return getPeopleByRole(conn, roleId);
        }
        
        return people;
    }
}

