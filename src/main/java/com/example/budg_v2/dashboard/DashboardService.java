package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service class for dashboard-related database operations
 */
public class DashboardService {
    
    /**
     * Get user's dashboard configuration
     * 
     * @param userId The user ID
     * @return Map containing dashboard configuration
     * @throws SQLException If a database error occurs
     */
    public static Map<String, Object> getUserDashboardConfig(int userId) throws SQLException {
        Map<String, Object> config = new HashMap<>();
        List<Map<String, Object>> widgets = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if dashboard tables exist
            if (!tablesExist(conn)) {
                // Return default config if tables don't exist
                return getDefaultConfig();
            }
            
            // Get dashboard ID for user
            Integer dashboardId = getUserDashboardId(conn, userId);
            if (dashboardId == null) {
                // Create new dashboard for user
                dashboardId = createUserDashboard(conn, userId);
                if (dashboardId == null) {
                    return getDefaultConfig();
                }
            }
            
            // Get dashboard widgets and their order
            String sql = """
                SELECT dxu.widget_id, dxu.widget_order, dxu.is_visible
                FROM dashboard_x_user dxu
                WHERE dxu.dashboard_id = ?
                ORDER BY dxu.widget_order
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> widget = new HashMap<>();
                        widget.put("id", rs.getString("widget_id"));
                        widget.put("order", rs.getInt("widget_order"));
                        widget.put("visible", rs.getBoolean("is_visible"));
                        widgets.add(widget);
                    }
                }
            }
            
            config.put("dashboardId", dashboardId);
            config.put("widgets", widgets);
        }
        
        return config;
    }
    
    /**
     * Save user's dashboard configuration
     * 
     * @param userId The user ID
     * @param widgets List of widget configurations
     * @return true if successful, false otherwise
     */
    public static boolean saveUserDashboardConfig(int userId, List<Map<String, Object>> widgets) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if dashboard tables exist
            if (!tablesExist(conn)) {
                return false;
            }
            
            // Get or create dashboard ID for user
            Integer dashboardId = getUserDashboardId(conn, userId);
            if (dashboardId == null) {
                dashboardId = createUserDashboard(conn, userId);
                if (dashboardId == null) {
                    return false;
                }
            }
            
            // Begin transaction
            conn.setAutoCommit(false);
            
            try {
                // Delete existing widget configurations
                String deleteSql = "DELETE FROM dashboard_x_user WHERE dashboard_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, dashboardId);
                    ps.executeUpdate();
                }
                
                // Insert new widget configurations
                String insertSql = """
                    INSERT INTO dashboard_x_user (dashboard_id, widget_id, widget_order, is_visible)
                    VALUES (?, ?, ?, ?)
                """;
                
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> widget : widgets) {
                        ps.setInt(1, dashboardId);
                        ps.setString(2, (String) widget.get("id"));
                        ps.setInt(3, ((Number) widget.get("order")).intValue());
                        ps.setBoolean(4, (Boolean) widget.get("visible"));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get dashboard ID for a user
     */
    private static Integer getUserDashboardId(Connection conn, int userId) throws SQLException {
        String sql = """
            SELECT d.id
            FROM dashboard d
            WHERE d.user_id = ?
            LIMIT 1
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        return null;
    }
    
    /**
     * Create a new dashboard for a user
     */
    private static Integer createUserDashboard(Connection conn, int userId) throws SQLException {
        String sql = "INSERT INTO dashboard (user_id, name, created_at) VALUES (?, ?, NOW())";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, "My Dashboard");
            ps.executeUpdate();
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int dashboardId = rs.getInt(1);
                    
                    // Add default widgets
                    addDefaultWidgets(conn, dashboardId);
                    
                    return dashboardId;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Add default widgets to a new dashboard
     */
    private static void addDefaultWidgets(Connection conn, int dashboardId) throws SQLException {
        String sql = """
            INSERT INTO dashboard_x_user (dashboard_id, widget_id, widget_order, is_visible)
            VALUES (?, ?, ?, ?)
        """;
        
        String[][] defaultWidgets = {
            {"objectCounts", "1", "true"},
            {"rolesNotAccepted", "2", "true"},
            {"stakeholdership", "3", "true"},
            {"savedSearches", "4", "true"},
            {"changeRequests", "5", "true"},
            {"team", "6", "true"},
            {"pendingTasks", "7", "false"}
        };
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] widget : defaultWidgets) {
                ps.setInt(1, dashboardId);
                ps.setString(2, widget[0]);
                ps.setInt(3, Integer.parseInt(widget[1]));
                ps.setBoolean(4, Boolean.parseBoolean(widget[2]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    
    /**
     * Check if dashboard tables exist
     */
    private static boolean tablesExist(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "dashboard", null)) {
            boolean dashboardExists = rs.next();
            
            try (ResultSet rs2 = conn.getMetaData().getTables(null, null, "dashboard_x_user", null)) {
                boolean dashboardXUserExists = rs2.next();
                
                return dashboardExists && dashboardXUserExists;
            }
        }
    }
    
    /**
     * Get default dashboard configuration
     */
    private static Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new HashMap<>();
        List<Map<String, Object>> widgets = new ArrayList<>();
        
        // Define default widget configuration
        String[][] defaultWidgets = {
            {"objectCounts", "1", "true"},
            {"rolesNotAccepted", "2", "true"},
            {"stakeholdership", "3", "true"},
            {"savedSearches", "4", "true"},
            {"changeRequests", "5", "true"},
            {"team", "6", "true"},
            {"pendingTasks", "7", "false"}
        };
        
        for (String[] widget : defaultWidgets) {
            Map<String, Object> widgetConfig = new HashMap<>();
            widgetConfig.put("id", widget[0]);
            widgetConfig.put("order", Integer.parseInt(widget[1]));
            widgetConfig.put("visible", Boolean.parseBoolean(widget[2]));
            widgets.add(widgetConfig);
        }
        
        config.put("dashboardId", null);
        config.put("widgets", widgets);
        
        return config;
    }
}
