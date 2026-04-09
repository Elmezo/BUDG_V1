package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.WorkflowNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Dashboard Sharing Servlet
 * GET /api/dashboard/sharing?dashboardId={id} - Get current sharing settings
 * POST /api/dashboard/sharing - Update sharing settings
 */
@WebServlet(name = "DashboardSharingServlet", urlPatterns = "/api/dashboard/sharing")
public class DashboardSharingServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowNotificationService notificationService = new WorkflowNotificationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        String dashboardIdParam = request.getParameter("dashboardId");
        if (dashboardIdParam == null || dashboardIdParam.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Dashboard ID is required");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try {
            int dashboardId = Integer.parseInt(dashboardIdParam);
            Map<String, Object> sharingInfo = getSharingInfo(dashboardId, userId);
            objectMapper.writeValue(response.getWriter(), sharingInfo);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid dashboard ID");
            objectMapper.writeValue(response.getWriter(), error);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try {
            JsonNode requestData = objectMapper.readTree(request.getReader());
            
            if (!requestData.has("dashboardId") || !requestData.has("sharingType")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Dashboard ID and sharing type are required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            int dashboardId = requestData.get("dashboardId").asInt();
            String sharingType = requestData.get("sharingType").asText();
            
            // Verify user owns the dashboard
            if (!isOwner(dashboardId, userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                Map<String, String> error = new HashMap<>();
                error.put("error", "You don't have permission to modify sharing for this dashboard");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            boolean success = updateSharing(dashboardId, sharingType, requestData, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                result.put("message", "Sharing settings updated successfully");
            } else {
                result.put("error", "Failed to update sharing settings");
            }
            
            objectMapper.writeValue(response.getWriter(), result);

        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private Map<String, Object> getSharingInfo(int dashboardId, int userId) throws SQLException {
        Map<String, Object> sharingInfo = new HashMap<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get dashboard info
            String dashboardSql = "SELECT Is_Public, Created_By FROM dashboards WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(dashboardSql)) {
                ps.setInt(1, dashboardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        boolean isPublic = rs.getBoolean("Is_Public");
                        int createdBy = rs.getInt("Created_By");
                        
                        sharingInfo.put("isPublic", isPublic);
                        sharingInfo.put("isOwner", createdBy == userId);
                        
                        if (isPublic) {
                            sharingInfo.put("sharingType", "public");
                        } else {
                            // Check if shared with specific users
                            String usersSql = "SELECT COUNT(*) as count FROM dashboard_x_user WHERE Dashboard_ID = ?";
                            try (PreparedStatement ps2 = conn.prepareStatement(usersSql)) {
                                ps2.setInt(1, dashboardId);
                                try (ResultSet rs2 = ps2.executeQuery()) {
                                    if (rs2.next() && rs2.getInt("count") > 0) {
                                        sharingInfo.put("sharingType", "selected");
                                        // Get shared users
                                        List<Map<String, Object>> sharedUsers = getSharedUsers(conn, dashboardId);
                                        sharingInfo.put("sharedUsers", sharedUsers);
                                    } else {
                                        sharingInfo.put("sharingType", "stop");
                                    }
                                }
                            }
                        }
                    } else {
                        throw new SQLException("Dashboard not found");
                    }
                }
            }
        }
        
        return sharingInfo;
    }

    private List<Map<String, Object>> getSharedUsers(Connection conn, int dashboardId) throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        
        // User_ID = user being shared with (for primary key), sharing = owner's ID (who shared it)
        String sql = """
            SELECT p.ID, CONCAT(p.First_Name, ' ', p.Last_Name) as name, p.Email
            FROM dashboard_x_user dxu
            JOIN people p ON dxu.User_ID = p.ID
            WHERE dxu.Dashboard_ID = ?
            ORDER BY p.Last_Name, p.First_Name
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dashboardId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("ID"));
                    user.put("name", rs.getString("name"));
                    user.put("email", rs.getString("Email"));
                    users.add(user);
                }
            }
        }
        
        return users;
    }

    private boolean isOwner(int dashboardId, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT Created_By FROM dashboards WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("Created_By") == userId;
                    }
                }
            }
        }
        return false;
    }

    private boolean updateSharing(int dashboardId, String sharingType, JsonNode requestData, int userId) throws SQLException {
        // Retry logic for deadlock handling
        int maxRetries = 3;
        int retryCount = 0;

        // Resolve dashboard name and previously-shared users before modifying DB records
        String dashboardName = getDashboardName(dashboardId);
        List<Integer> previouslySharedUserIds = getSharedUserIds(dashboardId);

        while (retryCount < maxRetries) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);
                
                try {
                    // 1. Clear existing sharing in dashboard_x_user table
                    String deleteSql = "DELETE FROM dashboard_x_user WHERE Dashboard_ID = ?";
                    try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                        ps.setInt(1, dashboardId);
                        ps.executeUpdate();
                    }
                
                // 2. Update dashboard public status
                boolean isPublic = "public".equals(sharingType);
                String updateDashboardSql = "UPDATE dashboards SET Is_Public = ? WHERE ID = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateDashboardSql)) {
                    ps.setBoolean(1, isPublic);
                    ps.setInt(2, dashboardId);
                    ps.executeUpdate();
                }
                
                // 3. Handle sharing type specific logic
                if ("public".equals(sharingType)) {
                    // Public sharing: dashboard visible to all users
                    boolean makeSearchesPublic = requestData.has("makeSearchesPublic") && 
                                               requestData.get("makeSearchesPublic").asBoolean();
                    if (makeSearchesPublic) {
                        updateDashboardSearchesVisibility(conn, dashboardId, true, null);
                    }
                    
                } else if ("selected".equals(sharingType) && requestData.has("selectedUsers")) {
                    // Selected users sharing: add to dashboard_x_user table
                    JsonNode selectedUsers = requestData.get("selectedUsers");
                    if (selectedUsers.isArray() && selectedUsers.size() > 0) {
                        // Validate: prevent sharing with self
                        String ownerSql = "SELECT Created_By FROM dashboards WHERE ID = ?";
                        int dashboardOwnerId = -1;
                        try (PreparedStatement ownerPs = conn.prepareStatement(ownerSql)) {
                            ownerPs.setInt(1, dashboardId);
                            try (ResultSet ownerRs = ownerPs.executeQuery()) {
                                if (ownerRs.next()) {
                                    dashboardOwnerId = ownerRs.getInt("Created_By");
                                }
                            }
                        }
                        
                        // Check if owner is trying to share with themselves
                        for (JsonNode userNode : selectedUsers) {
                            int sharedUserId = userNode.asInt();
                            if (sharedUserId == dashboardOwnerId) {
                                conn.rollback();
                                throw new IllegalArgumentException("You cannot share the dashboard with yourself. Please choose another user.");
                            }
                        }
                        
                        // Insert sharing records
                        String insertSql = "INSERT IGNORE INTO dashboard_x_user (Dashboard_ID, User_ID, sharing) VALUES (?, ?, ?)";
                        Set<Integer> uniqueUserIds = new HashSet<>();
                        for (JsonNode userNode : selectedUsers) {
                            uniqueUserIds.add(userNode.asInt());
                        }

                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            for (Integer sharedUserId : uniqueUserIds) {
                                ps.setInt(1, dashboardId);
                                ps.setInt(2, sharedUserId);
                                ps.setInt(3, dashboardOwnerId);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                        
                        // Handle search sharing if requested
                        boolean shareSearches = requestData.has("shareSearches") && 
                                              requestData.get("shareSearches").asBoolean();
                        if (shareSearches) {
                            List<Integer> userIds = new ArrayList<>(uniqueUserIds);
                            updateDashboardSearchesVisibility(conn, dashboardId, false, userIds);
                        }

                        conn.commit();
                        conn.setAutoCommit(true);

                        // Send DASHBOARD_SHARED notification to each newly added user
                        final int ownerIdFinal = dashboardOwnerId;
                        for (Integer sharedUserId : uniqueUserIds) {
                            notificationService.sendDashboardSharedNotification(dashboardId, dashboardName, sharedUserId, ownerIdFinal);
                        }
                        return true;
                    }
                    
                } else if ("stop".equals(sharingType)) {
                    // Stop sharing: records already cleared above, Is_Public set to false
                }
                
                    conn.commit();
                    conn.setAutoCommit(true);

                    // Send DASHBOARD_UNSHARED notification to all previously-shared users
                    if ("stop".equals(sharingType)) {
                        for (Integer prevUserId : previouslySharedUserIds) {
                            notificationService.sendDashboardUnsharedNotification(dashboardId, dashboardName, prevUserId);
                        }
                    }

                    return true;
                    
                } catch (SQLException e) {
                    conn.rollback();
                    String errorMessage = e.getMessage();
                    System.err.println("[DashboardSharing] Error updating sharing: " + errorMessage);
                    
                    if (errorMessage != null && errorMessage.contains("Deadlock") && retryCount < maxRetries - 1) {
                        retryCount++;
                        System.err.println("[DashboardSharing] Deadlock detected, retrying... (attempt " + retryCount + "/" + maxRetries + ")");
                        try {
                            Thread.sleep(50 + (int)(Math.random() * 100));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new SQLException("Retry interrupted", ie);
                        }
                        continue;
                    }
                    
                    throw e;
                } finally {
                    try { conn.setAutoCommit(true); } catch (Exception ignored) {}
                }
            } catch (SQLException e) {
                if (retryCount >= maxRetries - 1) {
                    throw e;
                }
                retryCount++;
                try {
                    Thread.sleep(50 + (int)(Math.random() * 100));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Retry interrupted", ie);
                }
            }
        }
        
        throw new SQLException("Failed to update sharing after " + maxRetries + " attempts");
    }

    private String getDashboardName(int dashboardId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Title FROM dashboards WHERE ID = ?")) {
            ps.setInt(1, dashboardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("Title");
                    return name != null ? name : "Dashboard #" + dashboardId;
                }
            }
        } catch (Exception e) {
            System.err.println("[DashboardSharing] Could not resolve dashboard name: " + e.getMessage());
        }
        return "Dashboard #" + dashboardId;
    }

    private List<Integer> getSharedUserIds(int dashboardId) {
        List<Integer> userIds = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT User_ID FROM dashboard_x_user WHERE Dashboard_ID = ?")) {
            ps.setInt(1, dashboardId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    userIds.add(rs.getInt("User_ID"));
                }
            }
        } catch (Exception e) {
            System.err.println("[DashboardSharing] Could not fetch shared user IDs: " + e.getMessage());
        }
        return userIds;
    }

    /**
     * Update visibility of searches used in dashboard widgets
     */
    private void updateDashboardSearchesVisibility(Connection conn, int dashboardId, boolean makePublic, List<Integer> userIds) throws SQLException {
        //system.out.println("[DashboardSharing] Updating search visibility for dashboard " + dashboardId + ", public: " + makePublic);
        
        // Get all saved search widgets for this dashboard
        // The search ID is stored in Widget_Config JSON, not as a separate column
        String getSearchesSql = """
            SELECT dw.Widget_Config, dw.Widget_Type
            FROM dashboard_widgets dw 
            WHERE dw.Dashboard_ID = ? 
            AND dw.Widget_Type = 'saved_search'
        """;
        
        List<Integer> searchIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(getSearchesSql)) {
            ps.setInt(1, dashboardId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String widgetConfigJson = rs.getString("Widget_Config");
                    if (widgetConfigJson != null && !widgetConfigJson.trim().isEmpty()) {
                        try {
                            // Parse JSON to extract search ID
                            JsonNode config = objectMapper.readTree(widgetConfigJson);
                            
                            // Try to get searchId from dataSource.searchId
                            Integer searchId = null;
                            if (config.has("dataSource") && config.get("dataSource").has("searchId")) {
                                searchId = config.get("dataSource").get("searchId").asInt();
                            } else if (config.has("source")) {
                                // Fallback to source field (might be string)
                                String source = config.get("source").asText();
                                try {
                                    searchId = Integer.parseInt(source);
                                } catch (NumberFormatException e) {
                                    // Source is not a valid integer, skip
                                }
                            }
                            
                            if (searchId != null && searchId > 0) {
                                searchIds.add(searchId);
                                //system.out.println("[DashboardSharing] Found search ID: " + searchId + " in widget config");
                            }
                        } catch (Exception e) {
                            System.err.println("[DashboardSharing] Error parsing widget config JSON: " + e.getMessage());
                            // Continue with next widget
                        }
                    }
                }
            }
        }
        
        // Remove duplicates
        searchIds = new ArrayList<>(new java.util.HashSet<>(searchIds));
        //system.out.println("[DashboardSharing] Found " + searchIds.size() + " unique searches to update");
        
        if (searchIds.isEmpty()) {
            return; // No searches to update
        }
        
        if (makePublic) {
            // Make searches public in user_search table
            String updateSearchSql = "UPDATE user_search SET is_public = 1 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSearchSql)) {
                for (Integer searchId : searchIds) {
                    ps.setInt(1, searchId);
                    ps.addBatch();
                }
                ps.executeBatch();
                //system.out.println("[DashboardSharing] Made " + searchIds.size() + " searches public");
            }
        } else if (userIds != null && !userIds.isEmpty()) {
            // Share searches with specific users in user_x_search table
            // First, clear existing shares for these searches and users
            String clearSharesSql = "DELETE FROM user_x_search WHERE search_id = ? AND user_reference = ?";
            try (PreparedStatement ps = conn.prepareStatement(clearSharesSql)) {
                for (Integer searchId : searchIds) {
                    for (Integer userId : userIds) {
                        ps.setInt(1, searchId);
                        ps.setInt(2, userId);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
            
            // Then add new shares
            // user_x_search table only has search_id and user_reference columns
            // The presence of a row indicates the search is shared with that user
            String shareSearchSql = "INSERT INTO user_x_search (search_id, user_reference) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(shareSearchSql)) {
                for (Integer searchId : searchIds) {
                    for (Integer userId : userIds) {
                        ps.setInt(1, searchId);
                        ps.setInt(2, userId);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
                //system.out.println("[DashboardSharing] Shared " + searchIds.size() + " searches with " + userIds.size() + " users");
            }
        }
    }
}
