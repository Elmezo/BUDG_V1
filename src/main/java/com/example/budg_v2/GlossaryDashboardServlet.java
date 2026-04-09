package com.example.budg_v2;

import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.CorsUtil;
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

@WebServlet(name = "GlossaryDashboardServlet", urlPatterns = {"/api/glossary/dashboard/stats"})
public class GlossaryDashboardServlet extends HttpServlet {

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
            
            // Parse filter parameters (excludeType, excludeLifecycle, excludeStatus, excludeSecurity)
            Map<String, Set<String>> filters = parseFilters(req);
            
            // Build WHERE clause for filtering glossary records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build segment filter condition
            String segmentFilter = buildSegmentFilter(userId, "glossary.ID");
            
            // First, get total count of filtered non-deleted glossary records for verification
            String totalCountSql = "SELECT COUNT(*) as total FROM glossary WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter;
            try (PreparedStatement ps = conn.prepareStatement(totalCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("totalCount", rs.getInt("total"));
                }
            }
            
            // Get statistics for each dropdown field - return ALL options (including 0 counts)
            List<Map<String, Object>> lifecycleStats = getLifecycleStats(conn, filterWhereClause, segmentFilter);
            result.put("lifecycle", lifecycleStats);
            
            List<Map<String, Object>> typeStats = getTypeStats(conn, filterWhereClause, segmentFilter);
            result.put("type", typeStats);
            
            List<Map<String, Object>> statusStats = getStatusStats(conn, filterWhereClause, segmentFilter);
            result.put("status", statusStats);
            
            // For Security Classification, get ALL options and show count (including 0)
            List<Map<String, Object>> securityStats = getSecurityClassificationStats(conn, filterWhereClause, segmentFilter);
            result.put("securityClassification", securityStats);
            
            // Verify counts add up correctly (for debugging)
            int lifecycleSum = lifecycleStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int typeSum = typeStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int statusSum = statusStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int securitySum = securityStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "lifecycleSum", lifecycleSum,
                "typeSum", typeSum,
                "statusSum", statusSum,
                "securitySum", securitySum
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
     * Supports: excludeType, excludeLifecycle, excludeStatus, excludeSecurity
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
        
        String excludeSecurity = req.getParameter("excludeSecurity");
        if (excludeSecurity != null && !excludeSecurity.trim().isEmpty()) {
            filters.put("security", new HashSet<>(Arrays.asList(excludeSecurity.split(","))));
        }
        
        return filters;
    }
    
    /**
     * Build WHERE clause to exclude filtered glossary records
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
                conditions.add("glossary.Type NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Lifecycle
        if (filters.containsKey("lifecycle") && !filters.get("lifecycle").isEmpty()) {
            Set<String> lifecycleNames = filters.get("lifecycle");
            List<Integer> lifecycleIds = getLifecycleIdsByName(conn, lifecycleNames);
            if (!lifecycleIds.isEmpty()) {
                String idsStr = lifecycleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("glossary.Lifecycle NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Status
        if (filters.containsKey("status") && !filters.get("status").isEmpty()) {
            Set<String> statusNames = filters.get("status");
            List<Integer> statusIds = getStatusIdsByName(conn, statusNames);
            if (!statusIds.isEmpty()) {
                String idsStr = statusIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("glossary.Status NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Security Classification
        if (filters.containsKey("security") && !filters.get("security").isEmpty()) {
            Set<String> securityNames = filters.get("security");
            List<Integer> securityIds = getSecurityIdsByName(conn, securityNames);
            if (!securityIds.isEmpty()) {
                String idsStr = securityIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("glossary.Security_Classification NOT IN (" + idsStr + ")");
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
        String sql = "SELECT ID FROM glossary_type WHERE Name IN (" + placeholders + ")";
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
        String sql = "SELECT ID FROM glossary_lifecycle WHERE Name IN (" + placeholders + ")";
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
        String sql = "SELECT ID FROM status WHERE primaryname IN (" + placeholders + ")";
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
    
    private List<Integer> getSecurityIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        String placeholders = names.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM security_classification WHERE Name IN (" + placeholders + ")";
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

    /**
     * Build segment filter condition for glossary
     */
    private String buildSegmentFilter(Integer userId, String idColumn) {
        if (userId != null && userId > 0) {
            try {
                String segmentCondition = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Glossary", idColumn);
                if (segmentCondition != null && !segmentCondition.isEmpty()) {
                    return " AND " + segmentCondition;
                }
            } catch (SQLException e) {
                System.err.println("Error building segment filter: " + e.getMessage());
            }
        } else {
            // Anonymous user - Enterprise only
            return " AND (" +
                "EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'Glossary' " +
                    "AND sxr.Segment_ID = 1 " +
                    "AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR " +
                "NOT EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'Glossary' " +
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
        
        // Get all lifecycle options and their counts (including 0)
        // Use filtered subquery to exclude filtered glossary records and apply segment filtering
        String sql = "SELECT gl.Name as label, COALESCE(COUNT(g.ID), 0) as count " +
                     "FROM glossary_lifecycle gl " +
                     "LEFT JOIN (SELECT * FROM glossary WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter + ") g ON g.Lifecycle = gl.id " +
                     "GROUP BY gl.id, gl.Name " +
                     "ORDER BY gl.id";
        
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
    private List<Map<String, Object>> getTypeStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all type options and their counts (including 0)
        // Use filtered subquery to exclude filtered glossary records and apply segment filtering
        String sql = "SELECT gt.Name as label, COALESCE(COUNT(g.ID), 0) as count " +
                     "FROM glossary_type gt " +
                     "LEFT JOIN (SELECT * FROM glossary WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter + ") g ON g.Type = gt.id " +
                     "GROUP BY gt.id, gt.Name " +
                     "ORDER BY gt.id";
        
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
        
        // Get all status options and their counts (including 0)
        // Use filtered subquery to exclude filtered glossary records and apply segment filtering
        String sql = "SELECT s.primaryname as label, COALESCE(COUNT(g.ID), 0) as count " +
                     "FROM status s " +
                     "LEFT JOIN (SELECT * FROM glossary WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter + ") g ON g.Status = s.id " +
                     "GROUP BY s.id, s.primaryname " +
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

    /**
     * Get Security Classification stats - returns ALL classifications with counts (including 0)
     */
    private List<Map<String, Object>> getSecurityClassificationStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all security classifications and their counts (including 0)
        // Use filtered subquery to exclude filtered glossary records and apply segment filtering
        String sql = "SELECT sc.Name as label, COALESCE(COUNT(g.ID), 0) as count " +
                     "FROM security_classification sc " +
                     "LEFT JOIN (SELECT * FROM glossary WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter + ") g ON g.Security_Classification = sc.id " +
                     "GROUP BY sc.id, sc.Name " +
                     "ORDER BY sc.id";
        
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

