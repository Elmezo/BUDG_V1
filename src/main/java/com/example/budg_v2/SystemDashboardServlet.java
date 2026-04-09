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

@WebServlet(name = "SystemDashboardServlet", urlPatterns = {"/api/system/dashboard/stats"})
public class SystemDashboardServlet extends HttpServlet {

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
            
            // Parse filter parameters (excludeType, excludeLifecycle, excludeStatus, excludeClassification)
            Map<String, Set<String>> filters = parseFilters(req);
            
            // Build WHERE clause for filtering system records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build segment filter condition
            String segmentFilter = buildSegmentFilter(userId, "system.id");
            
            // First, get total count of filtered non-deleted system records for verification
            String totalCountSql = "SELECT COUNT(*) as total FROM system WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter;
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
            
            List<Map<String, Object>> classificationStats = getClassificationStats(conn, filterWhereClause, segmentFilter);
            result.put("classification", classificationStats);
            
            // Verify counts add up correctly (for debugging)
            int lifecycleSum = lifecycleStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int typeSum = typeStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int statusSum = statusStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int classificationSum = classificationStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "lifecycleSum", lifecycleSum,
                "typeSum", typeSum,
                "statusSum", statusSum,
                "classificationSum", classificationSum
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
     * Supports: excludeType, excludeLifecycle, excludeStatus, excludeClassification
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
        
        String excludeClassification = req.getParameter("excludeClassification");
        if (excludeClassification != null && !excludeClassification.trim().isEmpty()) {
            filters.put("classification", new HashSet<>(Arrays.asList(excludeClassification.split(","))));
        }
        
        return filters;
    }
    
    /**
     * Build WHERE clause to exclude filtered system records
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
                conditions.add("system.Type NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Lifecycle
        if (filters.containsKey("lifecycle") && !filters.get("lifecycle").isEmpty()) {
            Set<String> lifecycleNames = filters.get("lifecycle");
            List<Integer> lifecycleIds = getLifecycleIdsByName(conn, lifecycleNames);
            if (!lifecycleIds.isEmpty()) {
                String idsStr = lifecycleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("system.Lifecycle NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Status
        if (filters.containsKey("status") && !filters.get("status").isEmpty()) {
            Set<String> statusNames = filters.get("status");
            List<Integer> statusIds = getStatusIdsByName(conn, statusNames);
            if (!statusIds.isEmpty()) {
                String idsStr = statusIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("system.Status NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Classification
        if (filters.containsKey("classification") && !filters.get("classification").isEmpty()) {
            Set<String> classificationNames = filters.get("classification");
            // Separate null from non-null classifications
            Set<String> nonNullNames = new HashSet<>(classificationNames);
            boolean excludeNull = nonNullNames.remove("null") || nonNullNames.remove("Not Set");
            
            // Exclude non-null classifications
            if (!nonNullNames.isEmpty()) {
                List<Integer> classificationIds = getClassificationIdsByName(conn, nonNullNames);
                if (!classificationIds.isEmpty()) {
                    String idsStr = classificationIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                    conditions.add("system.Classification NOT IN (" + idsStr + ")");
                }
            }
            
            // Exclude null values if "null" is in the filter
            if (excludeNull) {
                conditions.add("system.Classification IS NOT NULL");
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
        String sql = "SELECT ID FROM system_type WHERE Name IN (" + placeholders + ")";
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
        String sql = "SELECT id FROM system_lifecycle WHERE Name IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : names) {
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
    
    private List<Integer> getClassificationIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        // Filter out "null" and "Not Set" as they're not in the lookup table
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT id FROM system_classification WHERE Name IN (" + placeholders + ")";
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

    /**
     * Build segment filter condition for system
     */
    private String buildSegmentFilter(Integer userId, String idColumn) {
        if (userId != null && userId > 0) {
            try {
                String segmentCondition = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "System", idColumn);
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
                    "AND sot.Type = 'System' " +
                    "AND sxr.Segment_ID = 1 " +
                    "AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR " +
                "NOT EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'System' " +
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
        // Use filtered subquery to exclude filtered system records and apply segment filtering
        String sql = "SELECT sl.Name as label, COALESCE(COUNT(s.id), 0) as count " +
                     "FROM system_lifecycle sl " +
                     "LEFT JOIN (SELECT * FROM system WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter + ") s ON s.Lifecycle = sl.id " +
                     "GROUP BY sl.id, sl.Name " +
                     "ORDER BY sl.id";
        
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
        // Use filtered subquery to exclude filtered system records and apply segment filtering
        String sql = "SELECT st.Name as label, COALESCE(COUNT(s.id), 0) as count " +
                     "FROM system_type st " +
                     "LEFT JOIN (SELECT * FROM system WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter + ") s ON s.Type = st.id " +
                     "GROUP BY st.id, st.Name " +
                     "ORDER BY st.id";
        
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
        // Use filtered subquery to exclude filtered system records and apply segment filtering
        // Use COALESCE to handle both PrimaryName and primaryname columns
        String sql = "SELECT COALESCE(s.PrimaryName, s.primaryname) as label, COALESCE(COUNT(sys.id), 0) as count " +
                     "FROM status s " +
                     "LEFT JOIN (SELECT * FROM system WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter + ") sys ON sys.Status = s.id " +
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

    /**
     * Get Classification stats - returns ALL classification options with counts (including 0)
     */
    private List<Map<String, Object>> getClassificationStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all classification options and their counts (including 0)
        // Use filtered subquery to exclude filtered system records and apply segment filtering
        String sql = "SELECT sc.Name as label, COALESCE(COUNT(s.id), 0) as count " +
                     "FROM system_classification sc " +
                     "LEFT JOIN (SELECT * FROM system WHERE Deleted_datetime IS NULL" + filterWhereClause + segmentFilter + ") s ON s.Classification = sc.id " +
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
        
        // Always include null values (even with 0 count)
        String nullSql = "SELECT COUNT(*) as count FROM system WHERE Classification IS NULL AND Deleted_datetime IS NULL" + filterWhereClause;
        try (PreparedStatement ps = conn.prepareStatement(nullSql);
             ResultSet rs = ps.executeQuery()) {
            int nullCount = 0;
            if (rs.next()) {
                nullCount = rs.getInt("count");
            }
            Map<String, Object> nullItem = new HashMap<>();
            nullItem.put("label", "null");
            nullItem.put("count", nullCount);
            stats.add(nullItem);
        }
        
        return stats;
    }
}

