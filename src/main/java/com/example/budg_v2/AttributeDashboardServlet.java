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

@WebServlet(name = "AttributeDashboardServlet", urlPatterns = {"/api/attribute/dashboard/stats"})
public class AttributeDashboardServlet extends HttpServlet {

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
            
            // Parse filter parameters (excludeEditability, excludeMandatory, excludeOrigin)
            Map<String, Set<String>> filters = parseFilters(req);
            
            // Build WHERE clause for filtering attribute records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build segment filter condition
            String segmentFilter = buildSegmentFilter(userId, "attribute.ID");
            
            // First, get total count of all attribute records for verification
            String totalCountSql = "SELECT COUNT(*) as total FROM attribute WHERE 1=1" + filterWhereClause + segmentFilter;
            try (PreparedStatement ps = conn.prepareStatement(totalCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("totalCount", rs.getInt("total"));
                }
            }
            
            // Get statistics for each dropdown field - return ALL options (including 0 counts)
            List<Map<String, Object>> editabilityStats = getEditabilityStats(conn, filterWhereClause, segmentFilter);
            result.put("editability", editabilityStats);
            
            List<Map<String, Object>> mandatoryStats = getMandatoryStats(conn, filterWhereClause, segmentFilter);
            result.put("mandatory", mandatoryStats);
            
            List<Map<String, Object>> originStats = getOriginStats(conn, filterWhereClause, segmentFilter);
            result.put("origin", originStats);
            
            // Verify counts add up correctly (for debugging)
            int editabilitySum = editabilityStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int mandatorySum = mandatoryStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int originSum = originStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "editabilitySum", editabilitySum,
                "mandatorySum", mandatorySum,
                "originSum", originSum
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
     * Supports: excludeEditability, excludeMandatory, excludeOrigin
     */
    private Map<String, Set<String>> parseFilters(HttpServletRequest req) {
        Map<String, Set<String>> filters = new HashMap<>();
        
        String excludeEditability = req.getParameter("excludeEditability");
        if (excludeEditability != null && !excludeEditability.trim().isEmpty()) {
            filters.put("editability", new HashSet<>(Arrays.asList(excludeEditability.split(","))));
        }
        
        String excludeMandatory = req.getParameter("excludeMandatory");
        if (excludeMandatory != null && !excludeMandatory.trim().isEmpty()) {
            filters.put("mandatory", new HashSet<>(Arrays.asList(excludeMandatory.split(","))));
        }
        
        String excludeOrigin = req.getParameter("excludeOrigin");
        if (excludeOrigin != null && !excludeOrigin.trim().isEmpty()) {
            filters.put("origin", new HashSet<>(Arrays.asList(excludeOrigin.split(","))));
        }
        
        return filters;
    }
    
    /**
     * Build WHERE clause to exclude filtered attribute records
     * Returns a string with actual ID values (safe because IDs come from database lookups)
     * Uses table name directly (not alias) for use in subqueries
     */
    private String buildFilterWhereClause(Map<String, Set<String>> filters, Connection conn) throws SQLException {
        if (filters.isEmpty()) {
            return "";
        }
        
        List<String> conditions = new ArrayList<>();
        
        // Exclude by Editability
        if (filters.containsKey("editability") && !filters.get("editability").isEmpty()) {
            Set<String> editabilityNames = filters.get("editability");
            List<Integer> editabilityIds = getEditabilityIdsByName(conn, editabilityNames);
            if (!editabilityIds.isEmpty()) {
                String idsStr = editabilityIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("attribute.Editability NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Mandatory (from requirement table)
        if (filters.containsKey("mandatory") && !filters.get("mandatory").isEmpty()) {
            Set<String> mandatoryNames = filters.get("mandatory");
            List<Integer> requirementIds = getRequirementIdsByName(conn, mandatoryNames);
            if (!requirementIds.isEmpty()) {
                String idsStr = requirementIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("attribute.Requirement_ID NOT IN (" + idsStr + ")");
            }
            // Also handle null values if "null" is in the filter
            if (mandatoryNames.contains("null") || mandatoryNames.contains("Not Set")) {
                conditions.add("attribute.Requirement_ID IS NOT NULL");
            }
        }
        
        // Exclude by Origin
        if (filters.containsKey("origin") && !filters.get("origin").isEmpty()) {
            Set<String> originNames = filters.get("origin");
            List<Integer> originIds = getOriginIdsByName(conn, originNames);
            if (!originIds.isEmpty()) {
                String idsStr = originIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("attribute.Origination NOT IN (" + idsStr + ")");
            }
        }
        
        if (conditions.isEmpty()) {
            return "";
        }
        
        return " AND " + String.join(" AND ", conditions);
    }
    
    private List<Integer> getEditabilityIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        String placeholders = names.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM attribute_editability WHERE PrimaryName IN (" + placeholders + ")";
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
    
    private List<Integer> getRequirementIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        // Filter out "null" and "Not Set" as they're handled separately
        Set<String> filteredNames = new HashSet<>();
        for (String name : names) {
            if (!"null".equalsIgnoreCase(name.trim()) && !"Not Set".equalsIgnoreCase(name.trim())) {
                filteredNames.add(name.trim());
            }
        }
        
        if (filteredNames.isEmpty()) return ids;
        
        String placeholders = filteredNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM requirement WHERE PrimaryName IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : filteredNames) {
                ps.setString(idx++, name);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ID"));
                }
            }
        }
        return ids;
    }
    
    private List<Integer> getOriginIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        String placeholders = names.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM attribute_origination WHERE PrimaryName IN (" + placeholders + ")";
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
     * Build segment filter condition for attribute
     */
    private String buildSegmentFilter(Integer userId, String idColumn) {
        if (userId != null && userId > 0) {
            try {
                String segmentCondition = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Attribute", idColumn);
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
                    "AND sot.Type = 'Attribute' " +
                    "AND sxr.Segment_ID = 1 " +
                    "AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR " +
                "NOT EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'Attribute' " +
                    "AND sxr.Deleted_At IS NULL" +
                ")" +
            ")";
        }
        return "";
    }

    /**
     * Get Editability stats - returns ALL editability options with counts (including 0)
     */
    private List<Map<String, Object>> getEditabilityStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all editability options and their counts (including 0)
        // Use filtered subquery to exclude filtered attribute records and apply segment filtering
        String sql = "SELECT ae.PrimaryName as label, COALESCE(COUNT(a.ID), 0) as count " +
                     "FROM attribute_editability ae " +
                     "LEFT JOIN (SELECT * FROM attribute WHERE 1=1" + filterWhereClause + segmentFilter + ") a ON a.Editability = ae.id " +
                     "GROUP BY ae.id, ae.PrimaryName " +
                     "ORDER BY ae.id";
        
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
        String nullSql = "SELECT COUNT(*) as count FROM attribute WHERE Editability IS NULL" + filterWhereClause + segmentFilter;
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
    
    /**
     * Get Mandatory stats - returns ALL mandatory options with counts (including 0)
     * Mandatory comes from requirement table (Requirement_ID column)
     */
    private List<Map<String, Object>> getMandatoryStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all requirement options and their counts (including 0)
        // Use filtered subquery to exclude filtered attribute records and apply segment filtering
        String sql = "SELECT r.PrimaryName as label, COALESCE(COUNT(a.ID), 0) as count " +
                     "FROM requirement r " +
                     "LEFT JOIN (SELECT * FROM attribute WHERE 1=1" + filterWhereClause + segmentFilter + ") a ON a.Requirement_ID = r.id " +
                     "GROUP BY r.id, r.PrimaryName " +
                     "ORDER BY r.id";
        
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
        String nullSql = "SELECT COUNT(*) as count FROM attribute WHERE Requirement_ID IS NULL" + filterWhereClause + segmentFilter;
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
    
    /**
     * Get Origin stats - returns ALL origin options with counts (including 0)
     */
    private List<Map<String, Object>> getOriginStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Get all origin options and their counts (including 0)
        // Use filtered subquery to exclude filtered attribute records and apply segment filtering
        String sql = "SELECT ao.PrimaryName as label, COALESCE(COUNT(a.ID), 0) as count " +
                     "FROM attribute_origination ao " +
                     "LEFT JOIN (SELECT * FROM attribute WHERE 1=1" + filterWhereClause + segmentFilter + ") a ON a.Origination = ao.id " +
                     "GROUP BY ao.id, ao.PrimaryName " +
                     "ORDER BY ao.id";
        
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
        String nullSql = "SELECT COUNT(*) as count FROM attribute WHERE Origination IS NULL" + filterWhereClause + segmentFilter;
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

