package com.example.budg_v2;

import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.service.SegmentAccessService;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet(name = "ProductDashboardServlet", urlPatterns = {"/api/product/dashboard/stats"})
public class ProductDashboardServlet extends HttpServlet {

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
        Integer userId = UserContextUtil.getCurrentUserIdOrNull(req);
        if (userId == null || userId <= 0) {
            userId = null; // Anonymous user - will use Enterprise-only filtering
        }

        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            Map<String, Object> result = new HashMap<>();
            
            // Parse filter parameters
            Map<String, Set<String>> filters = parseFilters(req);
            
            // Build WHERE clause for filtering product records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build segment filter condition
            String segmentFilter = buildSegmentFilter(userId, "product.id");
            
            // First, get total count of filtered non-deleted product records for verification
            String totalCountSql = "SELECT COUNT(*) as total FROM product WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter;
            try (PreparedStatement ps = conn.prepareStatement(totalCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("totalCount", rs.getInt("total"));
                }
            }
            
            // Get statistics for each dropdown field - return ALL options (including 0 counts)
            List<Map<String, Object>> lifecycleStats = getLifecycleStats(conn, filterWhereClause, segmentFilter);
            result.put("lifecycle", lifecycleStats);
            
            List<Map<String, Object>> statusStats = getStatusStats(conn, filterWhereClause, segmentFilter);
            result.put("status", statusStats);
            
            // Verify counts add up correctly (for debugging)
            int lifecycleSum = lifecycleStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int statusSum = statusStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "lifecycleSum", lifecycleSum,
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
     */
    private Map<String, Set<String>> parseFilters(HttpServletRequest req) {
        Map<String, Set<String>> filters = new HashMap<>();
        
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
     * Build WHERE clause to exclude filtered product records
     */
    private String buildFilterWhereClause(Map<String, Set<String>> filters, Connection conn) throws SQLException {
        if (filters.isEmpty()) {
            return "";
        }
        
        List<String> conditions = new ArrayList<>();
        
        // Exclude by Lifecycle
        if (filters.containsKey("lifecycle") && !filters.get("lifecycle").isEmpty()) {
            Set<String> lifecycleNames = filters.get("lifecycle");
            List<Integer> lifecycleIds = getLifecycleIdsByName(conn, lifecycleNames);
            if (!lifecycleIds.isEmpty()) {
                String idsStr = lifecycleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("product.lifecycle_status NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Status
        if (filters.containsKey("status") && !filters.get("status").isEmpty()) {
            Set<String> statusNames = filters.get("status");
            List<Integer> statusIds = getStatusIdsByName(conn, statusNames);
            if (!statusIds.isEmpty()) {
                String idsStr = statusIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("product.status NOT IN (" + idsStr + ")");
            }
        }
        
        if (conditions.isEmpty()) {
            return "";
        }
        
        return " AND " + String.join(" AND ", conditions);
    }
    
    private List<Integer> getLifecycleIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT id FROM product_lifecycle WHERE PrimaryName IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : validNames) {
                ps.setString(idx++, name.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                }
            }
        }
        return ids;
    }
    
    private List<Integer> getStatusIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        String placeholders = names.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
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
     * Build segment filter condition for product
     */
    private String buildSegmentFilter(Integer userId, String idColumn) {
        if (userId != null && userId > 0) {
            try {
                String segmentCondition = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Product", idColumn);
                if (segmentCondition != null && !segmentCondition.isEmpty()) {
                    return " AND " + segmentCondition;
                }
            } catch (SQLException e) {
                System.err.println("Error building segment filter: " + e.getMessage());
            }
        } else {
            // Anonymous user - Enterprise only (objects with no segment assignment or Segment_ID = 1)
            return " AND (" +
                "EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'Product' " +
                    "AND sxr.Segment_ID = 1 " +
                    "AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR " +
                "NOT EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'Product' " +
                    "AND sxr.Deleted_At IS NULL" +
                ")" +
            ")";
        }
        return "";
    }

    /**
     * Get Lifecycle stats - returns ALL lifecycle options with counts (including 0)
     */
    private List<Map<String, Object>> getLifecycleStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT pl.PrimaryName as label, COALESCE(COUNT(prd.id), 0) as count " +
                     "FROM product_lifecycle pl " +
                     "LEFT JOIN (SELECT * FROM product WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter + ") prd ON prd.lifecycle_status = pl.id " +
                     "GROUP BY pl.id, pl.PrimaryName " +
                     "ORDER BY pl.id";
        
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
    private List<Map<String, Object>> getStatusStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT COALESCE(s.PrimaryName, s.primaryname) as label, COALESCE(COUNT(prd.id), 0) as count " +
                     "FROM status s " +
                     "LEFT JOIN (SELECT * FROM product WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter + ") prd ON prd.status = s.id " +
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

