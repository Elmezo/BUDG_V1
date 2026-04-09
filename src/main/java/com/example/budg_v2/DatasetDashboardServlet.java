package com.example.budg_v2;

import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.DashboardSearchUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet(name = "DatasetDashboardServlet", urlPatterns = {"/api/dataset/dashboard/stats"})
public class DatasetDashboardServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        // Get userId for segment filtering
        Integer userId = UserContextUtil.getCurrentUserId(req);
        if (userId == null || userId <= 0) {
            userId = null; // Anonymous user - will use Enterprise-only filtering
        }

        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            Map<String, Object> result = new HashMap<>();
            
            // Parse filter parameters (excludeType, excludeLifecycle, excludeStatus)
            Map<String, Set<String>> filters = parseFilters(req);
            
            // Get search query parameter
            String searchQuery = req.getParameter("q");
            
            // Build WHERE clause for filtering dataset records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build search filter condition if query is provided
            String searchFilter = "";
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                searchFilter = buildSearchFilterCondition(searchQuery.trim());
            }
            
            // Build segment filter condition
            String segmentFilter = "";
            if (userId != null && userId > 0) {
                try {
                    String segmentCondition = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Dataset", "dataset.ID");
                    if (segmentCondition != null && !segmentCondition.isEmpty()) {
                        segmentFilter = " AND " + segmentCondition;
                    }
                } catch (SQLException e) {
                    System.err.println("Error building segment filter: " + e.getMessage());
                }
            } else {
                // Anonymous user - Enterprise only (objects with no segment assignment or Segment_ID = 1)
                // Objects are visible if they have no segment assignment (treated as Enterprise)
                segmentFilter = " AND (" +
                    "EXISTS (" +
                        "SELECT 1 FROM segment_x_resource sxr " +
                        "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        "WHERE orr.Object_ID = dataset.ID " +
                        "AND sot.Type = 'Dataset' " +
                        "AND sxr.Segment_ID = 1 " +
                        "AND sxr.Deleted_At IS NULL" +
                    ") " +
                    "OR " +
                    "NOT EXISTS (" +
                        "SELECT 1 FROM segment_x_resource sxr " +
                        "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                        "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                        "WHERE orr.Object_ID = dataset.ID " +
                        "AND sot.Type = 'Dataset' " +
                        "AND sxr.Deleted_At IS NULL" +
                    ")" +
                ")";
            }
            
            // First, get total count of filtered non-deleted dataset records for verification
            String totalCountSql = "SELECT COUNT(*) as total FROM dataset WHERE DeletedDatetime IS NULL" + filterWhereClause + searchFilter + segmentFilter;
            try (PreparedStatement ps = conn.prepareStatement(totalCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("totalCount", rs.getInt("total"));
                }
            }
            
            // Get statistics for each dropdown field - return ALL options (including 0 counts)
            List<Map<String, Object>> lifecycleStats = getLifecycleStats(conn, filterWhereClause, searchFilter, segmentFilter);
            result.put("lifecycle", lifecycleStats);
            
            List<Map<String, Object>> typeStats = getTypeStats(conn, filterWhereClause, searchFilter, segmentFilter);
            result.put("type", typeStats);
            
            List<Map<String, Object>> statusStats = getStatusStats(conn, filterWhereClause, searchFilter, segmentFilter);
            result.put("status", statusStats);
            
            // Verify counts add up correctly (for debugging)
            int lifecycleSum = lifecycleStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int typeSum = typeStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int statusSum = statusStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "lifecycleSum", lifecycleSum,
                "typeSum", typeSum,
                "statusSum", statusSum
            ));
            
            resp.getWriter().write(gson.toJson(result));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorMsg = "Database error: " + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += " - Cause: " + e.getCause().getMessage();
            }
            JsonUtil.sendErrorResponse(resp.getWriter(), errorMsg, 500);
            e.printStackTrace();
            System.err.println("SQL Error details: " + e.getMessage());
            if (e.getNextException() != null) {
                System.err.println("Next exception: " + e.getNextException().getMessage());
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonUtil.sendErrorResponse(resp.getWriter(), "Unexpected error: " + e.getMessage(), 500);
            e.printStackTrace();
        }
    }
    
    /**
     * Parse filter parameters from request
     * Supports: excludeType, excludeLifecycle, excludeStatus
     */
    private Map<String, Set<String>> parseFilters(HttpServletRequest req) {
        Map<String, Set<String>> filters = new HashMap<>();
        
        String excludeType = req.getParameter("excludeType");
        if (excludeType != null && !excludeType.trim().isEmpty()) {
            filters.put("type", new HashSet<>(Arrays.asList(excludeType.split(","))));
        }
        
        String excludeLifecycle = req.getParameter("excludeLifecycle");
        if (excludeLifecycle != null && !excludeLifecycle.trim().isEmpty()) {
            filters.put("lifecycle", new HashSet<>(Arrays.asList(excludeLifecycle.split(","))));
        }
        
        String excludeStatus = req.getParameter("excludeStatus");
        if (excludeStatus != null && !excludeStatus.trim().isEmpty()) {
            filters.put("status", new HashSet<>(Arrays.asList(excludeStatus.split(","))));
        }
        
        return filters;
    }
    
    /**
     * Build WHERE clause to exclude filtered dataset records
     * Returns a string with actual ID values (safe because IDs come from database lookups)
     * Uses table name directly (not alias) for use in subqueries
     */
    private String buildFilterWhereClause(Map<String, Set<String>> filters, Connection conn) throws SQLException {
        if (filters.isEmpty()) {
            return "";
        }
        
        List<String> conditions = new ArrayList<>();
        
        // Exclude by Type
        if (filters.containsKey("type") && !filters.get("type").isEmpty()) {
            Set<String> typeNames = filters.get("type");
            List<Integer> typeIds = getTypeIdsByName(conn, typeNames);
            if (!typeIds.isEmpty()) {
                String idsStr = typeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("dataset.DatasetType NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Lifecycle
        if (filters.containsKey("lifecycle") && !filters.get("lifecycle").isEmpty()) {
            Set<String> lifecycleNames = filters.get("lifecycle");
            List<Integer> lifecycleIds = getLifecycleIdsByName(conn, lifecycleNames);
            if (!lifecycleIds.isEmpty()) {
                String idsStr = lifecycleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("dataset.lifecycle NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Status
        if (filters.containsKey("status") && !filters.get("status").isEmpty()) {
            Set<String> statusNames = filters.get("status");
            List<Integer> statusIds = getStatusIdsByName(conn, statusNames);
            if (!statusIds.isEmpty()) {
                String idsStr = statusIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("dataset.status NOT IN (" + idsStr + ")");
            }
        }
        
        if (conditions.isEmpty()) {
            return "";
        }
        
        return " AND " + String.join(" AND ", conditions);
    }
    
    private List<Integer> getTypeIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        String placeholders = names.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM dataset_type WHERE PrimaryName IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : names) {
                ps.setString(idx++, name.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ID"));
                }
            }
        }
        return ids;
    }
    
    private List<Integer> getLifecycleIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        String placeholders = names.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM dataset_lifecycle WHERE PrimaryName IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : names) {
                ps.setString(idx++, name.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ID"));
                }
            }
        }
        return ids;
    }
    
    private List<Integer> getStatusIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        String placeholders = names.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        // Try both PrimaryName and primaryname columns
        String sql = "SELECT ID FROM status WHERE PrimaryName IN (" + placeholders + ") OR primaryname IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : names) {
                ps.setString(idx++, name.trim());
            }
            for (String name : names) {
                ps.setString(idx++, name.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ID"));
                }
            }
        }
        return ids;
    }

    /**
     * Build search filter condition for dataset search
     * Uses DashboardSearchUtil for consistency
     */
    private String buildSearchFilterCondition(String query) {
        return DashboardSearchUtil.buildSearchFilterCondition("dataset", "dataset", query);
    }
    
    /**
     * Get Lifecycle stats - returns ALL lifecycle options with counts (including 0)
     */
    private List<Map<String, Object>> getLifecycleStats(Connection conn, String filterWhereClause, String searchFilter, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all lifecycle options and their counts (including 0)
        // Use filtered subquery to exclude filtered dataset records and apply segment filtering
        String sql = "SELECT dl.PrimaryName as label, COALESCE(COUNT(d.ID), 0) as count " +
                     "FROM dataset_lifecycle dl " +
                     "LEFT JOIN (SELECT * FROM dataset WHERE DeletedDatetime IS NULL" + filterWhereClause + searchFilter + segmentFilter + ") d ON d.lifecycle = dl.id " +
                     "GROUP BY dl.id, dl.PrimaryName " +
                     "ORDER BY dl.id";
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                String label = rs.getString("label");
                if (label == null || label.trim().isEmpty()) {
                    label = "Not Set";
                }
                item.put("label", label.trim());
                item.put("count", rs.getInt("count"));
                stats.add(item);
            }
        }
        
        return stats;
    }
    
    /**
     * Get Type stats - returns ALL type options with counts (including 0)
     */
    private List<Map<String, Object>> getTypeStats(Connection conn, String filterWhereClause, String searchFilter, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all type options and their counts (including 0)
        // Use filtered subquery to exclude filtered dataset records and apply segment filtering
        String sql = "SELECT dt.PrimaryName as label, COALESCE(COUNT(d.ID), 0) as count " +
                     "FROM dataset_type dt " +
                     "LEFT JOIN (SELECT * FROM dataset WHERE DeletedDatetime IS NULL" + filterWhereClause + searchFilter + segmentFilter + ") d ON d.DatasetType = dt.id " +
                     "GROUP BY dt.id, dt.PrimaryName " +
                     "ORDER BY dt.id";
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                String label = rs.getString("label");
                if (label == null || label.trim().isEmpty()) {
                    label = "Not Set";
                }
                item.put("label", label.trim());
                item.put("count", rs.getInt("count"));
                stats.add(item);
            }
        }
        
        return stats;
    }
    
    /**
     * Get Status stats - returns ALL status options with counts (including 0)
     */
    private List<Map<String, Object>> getStatusStats(Connection conn, String filterWhereClause, String searchFilter, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all status options and their counts (including 0)
        // Use filtered subquery to exclude filtered dataset records and apply segment filtering
        // Use COALESCE to handle both PrimaryName and primaryname columns
        String sql = "SELECT COALESCE(s.PrimaryName, s.primaryname) as label, COALESCE(COUNT(d.ID), 0) as count " +
                     "FROM status s " +
                     "LEFT JOIN (SELECT * FROM dataset WHERE DeletedDatetime IS NULL" + filterWhereClause + searchFilter + segmentFilter + ") d ON d.status = s.id " +
                     "GROUP BY s.id, COALESCE(s.PrimaryName, s.primaryname) " +
                     "ORDER BY s.id";
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                String label = rs.getString("label");
                if (label == null || label.trim().isEmpty()) {
                    label = "Not Set";
                }
                item.put("label", label.trim());
                item.put("count", rs.getInt("count"));
                stats.add(item);
            }
        }
        
        return stats;
    }
}

