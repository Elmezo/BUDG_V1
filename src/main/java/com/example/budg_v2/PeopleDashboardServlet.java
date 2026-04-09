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

@WebServlet(name = "PeopleDashboardServlet", urlPatterns = {"/api/people/dashboard/stats"})
public class PeopleDashboardServlet extends HttpServlet {

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
            
            // Parse filter parameters (excludeType, excludeLifecycle, excludeStatus, excludeOrgUnit, excludeProfile)
            Map<String, Set<String>> filters = parseFilters(req);
            
            // Build WHERE clause for filtering people records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build segment filter condition
            String segmentFilter = buildSegmentFilter(userId, "people.ID");
            
            // First, get total count of filtered non-deleted people records for verification
            String totalCountSql = "SELECT COUNT(*) as total FROM people WHERE Deleted_date IS NULL" + filterWhereClause + segmentFilter;
            try (PreparedStatement ps = conn.prepareStatement(totalCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("totalCount", rs.getInt("total"));
                }
            }
            
            // Get statistics for each dropdown field - return ALL options (including 0 counts)
            List<Map<String, Object>> lifecycleStats = getLifecycleStats(conn, filterWhereClause, segmentFilter);
            result.put("lifecycle", lifecycleStats);
            
            List<Map<String, Object>> orgUnitStats = getOrgUnitStats(conn, filterWhereClause, segmentFilter);
            result.put("orgUnit", orgUnitStats);
            
            List<Map<String, Object>> statusStats = getStatusStats(conn, filterWhereClause, segmentFilter);
            result.put("status", statusStats);
            
            List<Map<String, Object>> profileStats = getProfileStats(conn, filterWhereClause, segmentFilter);
            result.put("profile", profileStats);
            
            // Verify counts add up correctly (for debugging)
            int lifecycleSum = lifecycleStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int orgUnitSum = orgUnitStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int statusSum = statusStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int profileSum = profileStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "lifecycleSum", lifecycleSum,
                "orgUnitSum", orgUnitSum,
                "statusSum", statusSum,
                "profileSum", profileSum
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
     * Supports: excludeLifecycle, excludeStatus, excludeOrgUnit, excludeProfile
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
        
        String excludeOrgUnit = req.getParameter("excludeOrgUnit");
        if (excludeOrgUnit != null && !excludeOrgUnit.trim().isEmpty()) {
            filters.put("orgUnit", new HashSet<>(Arrays.asList(excludeOrgUnit.split(","))));
        }
        
        String excludeProfile = req.getParameter("excludeProfile");
        if (excludeProfile != null && !excludeProfile.trim().isEmpty()) {
            filters.put("profile", new HashSet<>(Arrays.asList(excludeProfile.split(","))));
        }
        
        return filters;
    }
    
    /**
     * Build WHERE clause to exclude filtered people records
     * Returns a string with actual ID values (safe because IDs come from database lookups)
     * Uses table name directly (not alias) for use in subqueries
     */
    private String buildFilterWhereClause(Map<String, Set<String>> filters, Connection conn) throws SQLException {
        if (filters.isEmpty()) {
            return "";
        }
        
        List<String> conditions = new ArrayList<>();
        
        // Exclude by Lifecycle (via people_details)
        if (filters.containsKey("lifecycle") && !filters.get("lifecycle").isEmpty()) {
            Set<String> lifecycleNames = filters.get("lifecycle");
            // Separate null from non-null lifecycles
            Set<String> nonNullNames = new HashSet<>(lifecycleNames);
            boolean excludeNull = nonNullNames.remove("null") || nonNullNames.remove("Not Set");
            
            // Exclude non-null lifecycles
            if (!nonNullNames.isEmpty()) {
                List<Integer> lifecycleIds = getLifecycleIdsByName(conn, nonNullNames);
                if (!lifecycleIds.isEmpty()) {
                    String idsStr = lifecycleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                    conditions.add("people.id NOT IN (SELECT p.id FROM people p " +
                                   "LEFT JOIN people_details pd ON p.ip_details = pd.id " +
                                   "WHERE pd.lifecycle IN (" + idsStr + "))");
                }
            }
            
            // Exclude null values if "null" is in the filter
            if (excludeNull) {
                conditions.add("people.id NOT IN (SELECT p.id FROM people p " +
                               "LEFT JOIN people_details pd ON p.ip_details = pd.id " +
                               "WHERE pd.lifecycle IS NULL)");
            }
        }
        
        // Exclude by Status
        if (filters.containsKey("status") && !filters.get("status").isEmpty()) {
            Set<String> statusNames = filters.get("status");
            List<Integer> statusIds = getStatusIdsByName(conn, statusNames);
            if (!statusIds.isEmpty()) {
                String idsStr = statusIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("people.status_id NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Org Unit
        if (filters.containsKey("orgUnit") && !filters.get("orgUnit").isEmpty()) {
            Set<String> orgUnitNames = filters.get("orgUnit");
            List<Integer> orgUnitIds = getOrgUnitIdsByName(conn, orgUnitNames);
            if (!orgUnitIds.isEmpty()) {
                String idsStr = orgUnitIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("people.Org_Unit_ID NOT IN (" + idsStr + ")");
            }
            // Also handle null values if "null" is in the filter
            if (orgUnitNames.contains("null") || orgUnitNames.contains("Not Set")) {
                conditions.add("people.Org_Unit_ID IS NOT NULL");
            }
        }
        
        // Exclude by Profile (System_Role)
        if (filters.containsKey("profile") && !filters.get("profile").isEmpty()) {
            Set<String> profileNames = filters.get("profile");
            List<Integer> profileIds = getProfileIdsByName(conn, profileNames);
            if (!profileIds.isEmpty()) {
                String idsStr = profileIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("people.System_Role NOT IN (" + idsStr + ")");
            }
            // Also handle null values if "null" is in the filter
            if (profileNames.contains("null") || profileNames.contains("Not Set")) {
                conditions.add("people.System_Role IS NOT NULL");
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
        
        // Filter out "null" and "Not Set" as they're not in the lookup table
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM people_lifecycle_status WHERE Primary_Name IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : validNames) {
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
    
    private List<Integer> getOrgUnitIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        // Filter out "null" and "Not Set" as they're not in the lookup table
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM org_unit WHERE Name IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : validNames) {
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
    
    private List<Integer> getProfileIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        // Filter out "null" and "Not Set" as they're not in the lookup table
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT id FROM role WHERE primaryname IN (" + placeholders + ")";
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
     * Adapt segment filter for use in subqueries where people table is aliased as 'p'
     * Replaces 'people.ID' with 'p.id' in the segment filter string (case-insensitive)
     */
    private String adaptSegmentFilterForSubquery(String segmentFilter) {
        if (segmentFilter == null || segmentFilter.isEmpty()) {
            return segmentFilter;
        }
        // Replace people.ID or people.id with p.id (case-insensitive)
        return segmentFilter.replaceAll("(?i)people\\.ID", "p.id")
                           .replaceAll("(?i)people\\.id", "p.id");
    }

    /**
     * Build segment filter condition for people
     */
    private String buildSegmentFilter(Integer userId, String idColumn) {
        if (userId != null && userId > 0) {
            try {
                String segmentCondition = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "InvolvedParty", idColumn);
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
                    "AND sot.Type = 'InvolvedParty' " +
                    "AND sxr.Segment_ID = 1 " +
                    "AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR " +
                "NOT EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'InvolvedParty' " +
                    "AND sxr.Deleted_At IS NULL" +
                ")" +
            ")";
        }
        return "";
    }

    /**
     * Get Lifecycle stats - returns ALL lifecycle options with counts (including 0)
     * Lifecycle comes from people_details.lifecycle -> people_lifecycle_status
     */
    private List<Map<String, Object>> getLifecycleStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Adapt segment filter for subquery context (replace people.ID with p.id)
        String adaptedSegmentFilter = adaptSegmentFilterForSubquery(segmentFilter);
        
        // Get all lifecycle options and their counts (including 0)
        // Use filtered subquery to exclude filtered people records
        // Need to handle the filter properly since lifecycle is in people_details
        String sql = "SELECT pls.Primary_Name as label, COALESCE(COUNT(p.id), 0) as count " +
                     "FROM people_lifecycle_status pls " +
                     "LEFT JOIN (SELECT p.id, pd.lifecycle FROM people p " +
                     "           LEFT JOIN people_details pd ON p.ip_details = pd.id " +
                     "           WHERE p.Deleted_date IS NULL" + filterWhereClause + adaptedSegmentFilter + ") p ON p.lifecycle = pls.ID " +
                     "GROUP BY pls.ID, pls.Primary_Name " +
                     "ORDER BY pls.ID";
        
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
        // Need to apply filter but exclude lifecycle filter from it for null count
        String nullSql = "SELECT COUNT(*) as count FROM people p " +
                        "LEFT JOIN people_details pd ON p.ip_details = pd.id " +
                        "WHERE pd.lifecycle IS NULL AND p.Deleted_date IS NULL";

        // Apply filter but remove lifecycle-related conditions for null count
        String nullFilterClause = filterWhereClause;
        if (nullFilterClause.contains("lifecycle")) {
            // Remove lifecycle filter conditions for null count query
            // This is a simplified approach - in practice, we'd need to parse and rebuild
            // For now, we'll use the full filter and it should work since we're checking IS NULL
        }
        nullSql += nullFilterClause + adaptedSegmentFilter;
        
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
     * Get Org Unit stats - returns ALL org unit options with counts (including 0)
     */
    private List<Map<String, Object>> getOrgUnitStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Adapt segment filter for subquery context (replace people.ID with p.id)
        String adaptedSegmentFilter = adaptSegmentFilterForSubquery(segmentFilter);
        
        // Get all org unit options and their counts (including 0)
        // Use filtered subquery to exclude filtered people records
        String sql = "SELECT ou.Name as label, COALESCE(COUNT(p.id), 0) as count " +
                     "FROM org_unit ou " +
                     "LEFT JOIN (SELECT * FROM people p WHERE p.Deleted_date IS NULL" + filterWhereClause + adaptedSegmentFilter + ") p ON p.Org_Unit_ID = ou.ID " +
                     "GROUP BY ou.ID, ou.Name " +
                     "ORDER BY ou.ID";
        
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
        String nullSql = "SELECT COUNT(*) as count FROM people WHERE Org_Unit_ID IS NULL AND Deleted_date IS NULL" + filterWhereClause + segmentFilter;
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
     * Get Status stats - returns ALL status options with counts (including 0)
     */
    private List<Map<String, Object>> getStatusStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Adapt segment filter for subquery context (replace people.ID with p.id)
        String adaptedSegmentFilter = adaptSegmentFilterForSubquery(segmentFilter);
        
        // Get all status options and their counts (including 0)
        // Use filtered subquery to exclude filtered people records
        // Use COALESCE to handle both PrimaryName and primaryname columns
        String sql = "SELECT COALESCE(s.PrimaryName, s.primaryname) as label, COALESCE(COUNT(p.id), 0) as count " +
                     "FROM status s " +
                     "LEFT JOIN (SELECT * FROM people p WHERE p.Deleted_date IS NULL" + filterWhereClause + adaptedSegmentFilter + ") p ON p.status_id = s.id " +
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
     * Get Profile stats - returns ALL profile options with counts (including 0)
     * Profile comes from people.System_Role -> role.primaryname
     */
    private List<Map<String, Object>> getProfileStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Adapt segment filter for subquery context (replace people.ID with p.id)
        String adaptedSegmentFilter = adaptSegmentFilterForSubquery(segmentFilter);
        
        // Get all profile options and their counts (including 0)
        // Use filtered subquery to exclude filtered people records
        String sql = "SELECT r.primaryname as label, COALESCE(COUNT(p.id), 0) as count " +
                     "FROM role r " +
                     "LEFT JOIN (SELECT * FROM people p WHERE p.Deleted_date IS NULL" + filterWhereClause + adaptedSegmentFilter + ") p ON p.System_Role = r.id " +
                     "GROUP BY r.id, r.primaryname " +
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
        String nullSql = "SELECT COUNT(*) as count FROM people WHERE System_Role IS NULL AND Deleted_date IS NULL" + filterWhereClause + segmentFilter;
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

