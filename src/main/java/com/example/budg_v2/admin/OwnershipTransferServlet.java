package com.example.budg_v2.admin;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ActivityLogHelper;
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
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Ownership Transfer Servlet
 * Handles all ownership transfer operations for dashboards and saved searches
 * 
 * Endpoints:
 * GET /admin/api/ownership-transfer/dashboards - Get transferable dashboards
 * GET /admin/api/ownership-transfer/searches - Get transferable saved searches
 * GET /admin/api/ownership-transfer/users - Get active users for transfer
 * POST /admin/api/ownership-transfer/transfer - Transfer ownership
 */
@WebServlet(name = "OwnershipTransferServlet", urlPatterns = {
    "/admin/api/ownership-transfer/dashboards",
    "/admin/api/ownership-transfer/searches",
    "/admin/api/ownership-transfer/users",
    "/admin/api/ownership-transfer/transfer"
})
public class OwnershipTransferServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getRequestURI();
        
        try {
            if (pathInfo.endsWith("/dashboards")) {
                List<Map<String, Object>> dashboards = getTransferableDashboards();
                objectMapper.writeValue(response.getWriter(), dashboards);
            } else if (pathInfo.endsWith("/searches")) {
                List<Map<String, Object>> searches = getTransferableSearches();
                objectMapper.writeValue(response.getWriter(), searches);
            } else if (pathInfo.endsWith("/users")) {
                List<Map<String, Object>> users = getActiveUsers();
                objectMapper.writeValue(response.getWriter(), users);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid endpoint");
                objectMapper.writeValue(response.getWriter(), error);
            }
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

        String pathInfo = request.getRequestURI();
        
        if (!pathInfo.endsWith("/transfer")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid endpoint");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try {
            JsonNode requestData = objectMapper.readTree(request.getReader());
            String type = requestData.has("type") ? requestData.get("type").asText() : null;
            List<Integer> itemIds = new ArrayList<>();
            if (requestData.has("itemIds") && requestData.get("itemIds").isArray()) {
                for (JsonNode idNode : requestData.get("itemIds")) {
                    itemIds.add(idNode.asInt());
                }
            }
            Integer newOwnerId = requestData.has("newOwnerId") ? requestData.get("newOwnerId").asInt() : null;
            boolean transferSearches = requestData.has("transferSearches") && requestData.get("transferSearches").asBoolean();

            if (type == null || itemIds.isEmpty() || newOwnerId == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Missing required parameters: type, itemIds, newOwnerId");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            boolean success = false;
            if ("dashboard".equals(type)) {
                success = transferDashboardOwnership(request, itemIds, newOwnerId, transferSearches);
            } else if ("search".equals(type)) {
                success = transferSearchOwnership(request, itemIds, newOwnerId);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid type. Must be 'dashboard' or 'search'");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                result.put("message", "Ownership transferred successfully");
            } else {
                result.put("error", "Failed to transfer ownership");
            }
            objectMapper.writeValue(response.getWriter(), result);

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    /**
     * Get dashboards that can be transferred (owned by inactive users, shared with others, not public)
     */
    private List<Map<String, Object>> getTransferableDashboards() throws SQLException {
        List<Map<String, Object>> dashboards = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Debug: check all dashboards owned by inactive users
            // User_ID = owner's ID (who shared it), sharing = user ID it's shared with (shared to)
            String debugSql = """
                SELECT d.ID, d.Title, d.Created_By, d.Is_Public,
                       p.First_Name, p.Last_Name, p.status_id, p.Deleted_date,
                       s.PrimaryName, s.primaryname, s.ID as status_table_id,
                       (SELECT COUNT(*) FROM dashboard_x_user dxu WHERE dxu.Dashboard_ID = d.ID) as total_shares,
                       (SELECT COUNT(*) FROM dashboard_x_user dxu WHERE dxu.Dashboard_ID = d.ID AND dxu.sharing = d.Created_By) as other_user_shares,
                       CASE 
                           WHEN p.Deleted_date IS NOT NULL THEN 'DELETED_USER'
                           WHEN LOWER(COALESCE(s.PrimaryName, s.primaryname, '')) LIKE 'inactive%' OR s.ID = 2 THEN 'INACTIVE'
                           ELSE 'ACTIVE'
                       END as user_status_check,
                       CASE 
                           WHEN d.Is_Public = 1 THEN 'PUBLIC'
                           WHEN EXISTS (SELECT 1 FROM dashboard_x_user dxu2 WHERE dxu2.Dashboard_ID = d.ID AND dxu2.sharing = d.Created_By) THEN 'SHARED_WITH_OTHERS'
                           ELSE 'NOT_SHARED_WITH_OTHERS'
                       END as sharing_check
                FROM dashboards d
                INNER JOIN people p ON d.Created_By = p.ID
                LEFT JOIN status s ON p.status_id = s.ID
                WHERE d.Created_By = 1 OR d.Created_By = 4
                ORDER BY d.Created_By, d.ID
            """;
            
            System.out.println("[OwnershipTransfer] Debug: Checking all dashboards for users 1 and 4:");
            try (PreparedStatement debugPs = conn.prepareStatement(debugSql)) {
                try (ResultSet debugRs = debugPs.executeQuery()) {
                    while (debugRs.next()) {
                        System.out.println(String.format(
                            "[OwnershipTransfer] Dashboard ID=%d, Title=%s, Created_By=%d, Is_Public=%d, " +
                            "Owner=%s %s, status_id=%d, PrimaryName=%s, status_table_id=%d, " +
                            "total_shares=%d, other_user_shares=%d, user_status=%s, sharing_status=%s",
                            debugRs.getInt("ID"),
                            debugRs.getString("Title"),
                            debugRs.getInt("Created_By"),
                            debugRs.getInt("Is_Public"),
                            debugRs.getString("First_Name"),
                            debugRs.getString("Last_Name"),
                            debugRs.getInt("status_id"),
                            debugRs.getString("PrimaryName"),
                            debugRs.getInt("status_table_id"),
                            debugRs.getInt("total_shares"),
                            debugRs.getInt("other_user_shares"),
                            debugRs.getString("user_status_check"),
                            debugRs.getString("sharing_check")
                        ));
                    }
                }
            }
            
            // Query to find dashboards owned by inactive users that are shared with other users (not public)
            // User_ID = user being shared with (for primary key), sharing = owner's ID (who shared it)
            // Only include created dashboards (Is_Default = 0 or NULL), exclude main/default dashboards (Is_Default = 1)
            String sql = """
                SELECT DISTINCT
                    d.ID,
                    d.Title,
                    d.Description,
                    d.Created_By,
                    CONCAT(p_owner.First_Name, ' ', p_owner.Last_Name) AS owner_name,
                    p_owner.Email AS owner_email,
                    COALESCE(s.PrimaryName, s.primaryname) AS owner_status,
                    d.Is_Public,
                    'Limited' AS sharing_type,
                    COALESCE(MAX(p_owner.Last_Updated), p_owner.Created_Date) AS inactive_date
                FROM dashboards d
                INNER JOIN people p_owner ON d.Created_By = p_owner.ID
                LEFT JOIN status s ON p_owner.status_id = s.ID
                INNER JOIN dashboard_x_user dxu ON d.ID = dxu.Dashboard_ID
                WHERE p_owner.Deleted_date IS NULL
                    AND (
                        LOWER(COALESCE(s.PrimaryName, s.primaryname, '')) LIKE 'inactive%' 
                        OR s.ID = 2
                    )
                    AND d.Is_Public = 0
                    AND (d.Is_Default = 0 OR d.Is_Default IS NULL)
                    AND dxu.sharing = d.Created_By
                GROUP BY d.ID, d.Title, d.Description, d.Created_By, 
                         p_owner.First_Name, p_owner.Last_Name, p_owner.Email,
                         s.PrimaryName, s.primaryname, d.Is_Public, p_owner.Last_Updated, p_owner.Created_Date
                ORDER BY d.Title
            """;
            
            System.out.println("[OwnershipTransfer] Executing main query for transferable dashboards...");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        Map<String, Object> dashboard = new HashMap<>();
                        dashboard.put("id", rs.getInt("ID"));
                        dashboard.put("dashboardName", rs.getString("Title"));
                        dashboard.put("description", rs.getString("Description"));
                        String ownerName = rs.getString("owner_name");
                        String ownerStatus = rs.getString("owner_status");
                        
                        System.out.println(String.format(
                            "[OwnershipTransfer] Found transferable dashboard: ID=%d, Title=%s, Owner=%s, Status=%s",
                            rs.getInt("ID"), rs.getString("Title"), ownerName, ownerStatus
                        ));
                        
                        dashboard.put("previousOwner", ownerName != null ? ownerName : "Unknown");
                        dashboard.put("previousOwnerEmail", rs.getString("owner_email"));
                        dashboard.put("previousOwnerId", rs.getInt("Created_By"));
                        
                        // Format inactive date
                        java.sql.Timestamp inactiveDate = rs.getTimestamp("inactive_date");
                        if (inactiveDate != null) {
                            dashboard.put("userInactiveFrom", DATE_FORMAT.format(inactiveDate));
                        } else {
                            dashboard.put("userInactiveFrom", "N/A");
                        }
                        
                        dashboard.put("sharing", rs.getString("sharing_type"));
                        dashboards.add(dashboard);
                    }
                    System.out.println("[OwnershipTransfer] Total transferable dashboards found: " + count);
                }
            }
        }
        
        return dashboards;
    }

    /**
     * Get saved searches that can be transferred (owned by inactive users, shared with others, not public)
     */
    private List<Map<String, Object>> getTransferableSearches() throws SQLException {
        List<Map<String, Object>> searches = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Query to find saved searches owned by inactive users that are shared with other users (not public)
            // user_x_search table: search_id and user_reference (user it's shared with)
            String sql = """
                SELECT DISTINCT
                    us.id,
                    us.name,
                    us.description,
                    us.user_reference,
                    CONCAT(p_owner.First_Name, ' ', p_owner.Last_Name) AS owner_name,
                    p_owner.Email AS owner_email,
                    COALESCE(s.PrimaryName, s.primaryname) AS owner_status,
                    us.is_public,
                    'Limited' AS sharing_type,
                    COALESCE(MAX(p_owner.Last_Updated), p_owner.Created_Date) AS inactive_date
                FROM user_search us
                INNER JOIN people p_owner ON us.user_reference = p_owner.ID
                LEFT JOIN status s ON p_owner.status_id = s.ID
                INNER JOIN user_x_search uxs ON us.id = uxs.search_id
                WHERE us.user_reference IS NOT NULL
                    AND p_owner.Deleted_date IS NULL
                    AND (
                        LOWER(COALESCE(s.PrimaryName, s.primaryname, '')) LIKE 'inactive%' 
                        OR s.ID = 2
                    )
                    AND us.is_public = 0
                    AND uxs.user_reference != us.user_reference
                GROUP BY us.id, us.name, us.description, us.user_reference,
                         p_owner.First_Name, p_owner.Last_Name, p_owner.Email,
                         s.PrimaryName, s.primaryname, us.is_public, p_owner.Last_Updated, p_owner.Created_Date
                ORDER BY us.name
            """;
            
            System.out.println("[OwnershipTransfer] Executing main query for transferable searches...");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        Map<String, Object> search = new HashMap<>();
                        search.put("id", rs.getInt("id"));
                        search.put("searchName", rs.getString("name"));
                        search.put("description", rs.getString("description"));
                        String ownerName = rs.getString("owner_name");
                        String ownerStatus = rs.getString("owner_status");
                        
                        System.out.println(String.format(
                            "[OwnershipTransfer] Found transferable search: ID=%d, Name=%s, Owner=%s, Status=%s",
                            rs.getInt("id"), rs.getString("name"), ownerName, ownerStatus
                        ));
                        
                        search.put("previousOwner", ownerName != null ? ownerName : "Unknown");
                        search.put("previousOwnerEmail", rs.getString("owner_email"));
                        search.put("previousOwnerId", rs.getInt("user_reference"));
                        
                        // Format inactive date
                        java.sql.Timestamp inactiveDate = rs.getTimestamp("inactive_date");
                        if (inactiveDate != null) {
                            search.put("userInactiveFrom", DATE_FORMAT.format(inactiveDate));
                        } else {
                            search.put("userInactiveFrom", "N/A");
                        }
                        
                        search.put("sharing", rs.getString("sharing_type"));
                        searches.add(search);
                    }
                    System.out.println("[OwnershipTransfer] Total transferable searches found: " + count);
                }
            }
        }
        
        return searches;
    }

    /**
     * Get active users for transfer dropdown
     */
    private List<Map<String, Object>> getActiveUsers() throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT 
                    p.ID,
                    CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                    p.Email
                FROM people p
                LEFT JOIN status s ON p.status_id = s.ID
                WHERE p.Deleted_date IS NULL
                    AND (s.PrimaryName IS NULL OR LOWER(s.PrimaryName) LIKE 'active%' OR s.ID = 1)
                ORDER BY p.Last_Name, p.First_Name
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
        }
        
        return users;
    }

    /**
     * Transfer dashboard ownership
     */
    private boolean transferDashboardOwnership(HttpServletRequest request, List<Integer> dashboardIds, Integer newOwnerId, boolean transferSearches) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get old owner info and dashboard names before transfer
                // Only transfer created dashboards, exclude main/default dashboards (Is_Default = 1)
                Map<Integer, Map<String, Object>> dashboardInfo = new HashMap<>();
                String getDashboardInfoSql = "SELECT ID, Title, Created_By, Is_Default FROM dashboards WHERE ID = ?";
                try (PreparedStatement ps = conn.prepareStatement(getDashboardInfoSql)) {
                    for (Integer dashboardId : dashboardIds) {
                        ps.setInt(1, dashboardId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                // Skip main/default dashboards (Is_Default = 1)
                                int isDefault = rs.getInt("Is_Default");
                                if (isDefault == 1) {
                                    System.out.println(String.format(
                                        "[OwnershipTransfer] Skipping main dashboard: ID=%d, Title=%s (Is_Default=1)",
                                        dashboardId, rs.getString("Title")
                                    ));
                                    continue;
                                }
                                
                                Map<String, Object> info = new HashMap<>();
                                info.put("title", rs.getString("Title"));
                                info.put("oldOwnerId", rs.getInt("Created_By"));
                                dashboardInfo.put(dashboardId, info);
                            }
                        }
                    }
                }
                
                // If no valid dashboards to transfer after filtering, return false
                if (dashboardInfo.isEmpty()) {
                    System.out.println("[OwnershipTransfer] No created dashboards to transfer (all were main dashboards)");
                    return false;
                }
                
                // Get old owner names
                for (Map.Entry<Integer, Map<String, Object>> entry : dashboardInfo.entrySet()) {
                    Integer oldOwnerId = (Integer) entry.getValue().get("oldOwnerId");
                    String oldOwnerName = getOwnerName(conn, oldOwnerId);
                    entry.getValue().put("oldOwnerName", oldOwnerName);
                }
                
                // Get new owner name
                String newOwnerName = getOwnerName(conn, newOwnerId);
                
                // Update dashboard ownership (only for created dashboards, not main ones)
                String updateDashboardSql = "UPDATE dashboards SET Created_By = ? WHERE ID = ? AND (Is_Default = 0 OR Is_Default IS NULL)";
                try (PreparedStatement ps = conn.prepareStatement(updateDashboardSql)) {
                    for (Integer dashboardId : dashboardInfo.keySet()) {
                        ps.setInt(1, newOwnerId);
                        ps.setInt(2, dashboardId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // If transferSearches is true, transfer ownership of searches used in dashboard widgets
                if (transferSearches) {
                    transferDashboardSearches(conn, new ArrayList<>(dashboardInfo.keySet()), newOwnerId);
                }

                // After transferring ownership, update all sharing records to reflect new owner
                // Update sharing column to new owner's ID (who shared it) for all existing shares
                // Note: User_ID is the shared user (for primary key), sharing is the owner
                String updateSharingSql = """
                    UPDATE dashboard_x_user 
                    SET sharing = ?
                    WHERE Dashboard_ID = ?
                """;
                try (PreparedStatement ps = conn.prepareStatement(updateSharingSql)) {
                    for (Integer dashboardId : dashboardInfo.keySet()) {
                        ps.setInt(1, newOwnerId);
                        ps.setInt(2, dashboardId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
                
                // Log activity for each dashboard transfer
                for (Map.Entry<Integer, Map<String, Object>> entry : dashboardInfo.entrySet()) {
                    Map<String, Object> info = entry.getValue();
                    Map<String, Object> oldState = new HashMap<>();
                    oldState.put("ownerName", info.get("oldOwnerName"));
                    
                    Map<String, Object> newState = new HashMap<>();
                    newState.put("ownerName", newOwnerName);
                    newState.put("transferType", "Dashboard");
                    newState.put("objectName", info.get("title"));
                    
                    // Log using helper - Component should be "Dashboard Ownership Transfer"
                    ActivityLogHelper.logActivity(request, 
                        ActivityLogConstants.SETTING_OWNERSHIP_TRANSFER,
                        ActivityLogConstants.COMPONENT_DASHBOARD_OWNERSHIP_TRANSFER,
                        ActivityLogConstants.CHANGE_TYPE_UPDATE,
                        oldState, newState);
                }
                
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Transfer ownership of searches used in dashboard widgets
     */
    private void transferDashboardSearches(Connection conn, List<Integer> dashboardIds, Integer newOwnerId) throws SQLException {
        // Get all saved search IDs from dashboard widgets
        Set<Integer> searchIds = new HashSet<>();
        
        String getSearchesSql = """
            SELECT dw.Widget_Config
            FROM dashboard_widgets dw
            WHERE dw.Dashboard_ID = ?
                AND dw.Widget_Type = 'saved_search'
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(getSearchesSql)) {
            for (Integer dashboardId : dashboardIds) {
                ps.setInt(1, dashboardId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String widgetConfigJson = rs.getString("Widget_Config");
                        if (widgetConfigJson != null && !widgetConfigJson.trim().isEmpty()) {
                            try {
                                JsonNode config = objectMapper.readTree(widgetConfigJson);
                                
                                // Try to get searchId from dataSource.searchId
                                Integer searchId = null;
                                if (config.has("dataSource") && config.get("dataSource").has("searchId")) {
                                    searchId = config.get("dataSource").get("searchId").asInt();
                                } else if (config.has("source")) {
                                    String source = config.get("source").asText();
                                    try {
                                        searchId = Integer.parseInt(source);
                                    } catch (NumberFormatException e) {
                                        // Source is not a valid integer, skip
                                    }
                                }
                                
                                if (searchId != null && searchId > 0) {
                                    searchIds.add(searchId);
                                }
                            } catch (Exception e) {
                                // Skip invalid JSON
                            }
                        }
                    }
                }
            }
        }

        // Transfer ownership of searches that are owned by inactive users
        // Only transfer searches that are shared with limited people (not public, not private)
        if (!searchIds.isEmpty()) {
            // First, filter to only include searches that:
            // 1. Are NOT public (is_public = 0)
            // 2. Are shared with limited people (exist in user_x_search, excluding owner)
            String filterSearchesSql = """
                SELECT DISTINCT us.id
                FROM user_search us
                INNER JOIN people p ON us.user_reference = p.ID
                LEFT JOIN status s ON p.status_id = s.ID
                INNER JOIN user_x_search uxs ON us.id = uxs.search_id
                WHERE us.id = ?
                    AND (LOWER(COALESCE(s.PrimaryName, s.primaryname, '')) LIKE 'inactive%' OR s.ID = 2)
                    AND us.is_public = 0
                    AND uxs.user_reference != us.user_reference
            """;
            
            Set<Integer> transferableSearchIds = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(filterSearchesSql)) {
                for (Integer searchId : searchIds) {
                    ps.setInt(1, searchId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            transferableSearchIds.add(searchId);
                        }
                    }
                }
            }
            
            // Only transfer searches that meet the criteria
            if (!transferableSearchIds.isEmpty()) {
                String transferSearchSql = """
                    UPDATE user_search us
                    INNER JOIN people p ON us.user_reference = p.ID
                    LEFT JOIN status s ON p.status_id = s.ID
                    SET us.user_reference = ?
                    WHERE us.id = ?
                        AND (LOWER(COALESCE(s.PrimaryName, s.primaryname, '')) LIKE 'inactive%' OR s.ID = 2)
                """;
                
                try (PreparedStatement ps = conn.prepareStatement(transferSearchSql)) {
                    for (Integer searchId : transferableSearchIds) {
                        ps.setInt(1, newOwnerId);
                        ps.setInt(2, searchId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Remove new owner from user_x_search since they are now the owner
                String deleteAccessSql = "DELETE FROM user_x_search WHERE search_id = ? AND user_reference = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteAccessSql)) {
                    for (Integer searchId : transferableSearchIds) {
                        ps.setInt(1, searchId);
                        ps.setInt(2, newOwnerId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        }
    }

    /**
     * Transfer saved search ownership
     */
    private boolean transferSearchOwnership(HttpServletRequest request, List<Integer> searchIds, Integer newOwnerId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get old owner info and search names before transfer
                Map<Integer, Map<String, Object>> searchInfo = new HashMap<>();
                String getSearchInfoSql = "SELECT id, name, user_reference FROM user_search WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(getSearchInfoSql)) {
                    for (Integer searchId : searchIds) {
                        ps.setInt(1, searchId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                Map<String, Object> info = new HashMap<>();
                                info.put("name", rs.getString("name"));
                                info.put("oldOwnerId", rs.getInt("user_reference"));
                                searchInfo.put(searchId, info);
                            }
                        }
                    }
                }
                
                // Get old owner names
                for (Map.Entry<Integer, Map<String, Object>> entry : searchInfo.entrySet()) {
                    Integer oldOwnerId = (Integer) entry.getValue().get("oldOwnerId");
                    String oldOwnerName = getOwnerName(conn, oldOwnerId);
                    entry.getValue().put("oldOwnerName", oldOwnerName);
                }
                
                // Get new owner name
                String newOwnerName = getOwnerName(conn, newOwnerId);
                
                // Update search ownership
                String updateSearchSql = "UPDATE user_search SET user_reference = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSearchSql)) {
                    for (Integer searchId : searchIds) {
                        ps.setInt(1, newOwnerId);
                        ps.setInt(2, searchId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Remove new owner from user_x_search since they are now the owner
                // The owner should not appear in the "Shared With" list
                String deleteAccessSql = "DELETE FROM user_x_search WHERE search_id = ? AND user_reference = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteAccessSql)) {
                    for (Integer searchId : searchIds) {
                        ps.setInt(1, searchId);
                        ps.setInt(2, newOwnerId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
                
                // Log activity for each search transfer
                for (Map.Entry<Integer, Map<String, Object>> entry : searchInfo.entrySet()) {
                    Map<String, Object> info = entry.getValue();
                    Map<String, Object> oldState = new HashMap<>();
                    oldState.put("ownerName", info.get("oldOwnerName"));
                    
                    Map<String, Object> newState = new HashMap<>();
                    newState.put("ownerName", newOwnerName);
                    newState.put("transferType", "Saved Search");
                    newState.put("objectName", info.get("name"));
                    
                    // Log using helper
                    ActivityLogHelper.logActivity(request, 
                        ActivityLogConstants.SETTING_OWNERSHIP_TRANSFER,
                        ActivityLogConstants.COMPONENT_SAVED_SEARCH_OWNERSHIP_TRANSFER,
                        ActivityLogConstants.CHANGE_TYPE_UPDATE,
                        oldState, newState);
                }
                
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Get owner name from user ID
     */
    private String getOwnerName(Connection conn, Integer ownerId) throws SQLException {
        if (ownerId == null) {
            return "Unknown";
        }
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS name FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        }
        return "Unknown";
    }
}

