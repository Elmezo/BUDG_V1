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

@WebServlet(name = "ProcessDashboardServlet", urlPatterns = {"/api/process/dashboard/stats"})
public class ProcessDashboardServlet extends HttpServlet {

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
            
            // Build WHERE clause for filtering process records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build segment filter condition
            String segmentFilter = buildSegmentFilter(userId, "process.id");
            
            // First, get total count of filtered non-deleted process records for verification
            String totalCountSql = "SELECT COUNT(*) as total FROM process WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter;
            try (PreparedStatement ps = conn.prepareStatement(totalCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("totalCount", rs.getInt("total"));
                }
            }
            
            // Get statistics for each dropdown field - return ALL options (including 0 counts)
            List<Map<String, Object>> typeStats = getTypeStats(conn, filterWhereClause, segmentFilter);
            result.put("type", typeStats);
            
            List<Map<String, Object>> lifecycleStats = getLifecycleStats(conn, filterWhereClause, segmentFilter);
            result.put("lifecycle", lifecycleStats);
            
            List<Map<String, Object>> statusStats = getStatusStats(conn, filterWhereClause, segmentFilter);
            result.put("status", statusStats);
            
            List<Map<String, Object>> classificationStats = getClassificationStats(conn, filterWhereClause, segmentFilter);
            result.put("classification", classificationStats);
            
            List<Map<String, Object>> automationStats = getAutomationStats(conn, filterWhereClause, segmentFilter);
            result.put("automation", automationStats);
            
            List<Map<String, Object>> levelStats = getLevelStats(conn, filterWhereClause, segmentFilter);
            result.put("level", levelStats);
            
            // Verify counts add up correctly (for debugging)
            int typeSum = typeStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int lifecycleSum = lifecycleStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int statusSum = statusStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int classificationSum = classificationStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int automationSum = automationStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int levelSum = levelStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "typeSum", typeSum,
                "lifecycleSum", lifecycleSum,
                "statusSum", statusSum,
                "classificationSum", classificationSum,
                "automationSum", automationSum,
                "levelSum", levelSum
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
        
        String excludeAutomation = req.getParameter("excludeAutomation");
        if (excludeAutomation != null && !excludeAutomation.trim().isEmpty()) {
            filters.put("automation", new HashSet<>(Arrays.asList(excludeAutomation.split(","))));
        }
        
        String excludeLevel = req.getParameter("excludeLevel");
        if (excludeLevel != null && !excludeLevel.trim().isEmpty()) {
            filters.put("level", new HashSet<>(Arrays.asList(excludeLevel.split(","))));
        }
        
        return filters;
    }
    
    /**
     * Build WHERE clause to exclude filtered process records
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
                conditions.add("process.type NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Lifecycle
        if (filters.containsKey("lifecycle") && !filters.get("lifecycle").isEmpty()) {
            Set<String> lifecycleNames = filters.get("lifecycle");
            List<Integer> lifecycleIds = getLifecycleIdsByName(conn, lifecycleNames);
            if (!lifecycleIds.isEmpty()) {
                String idsStr = lifecycleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("process.lifecycle_status NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Status
        if (filters.containsKey("status") && !filters.get("status").isEmpty()) {
            Set<String> statusNames = filters.get("status");
            List<Integer> statusIds = getStatusIdsByName(conn, statusNames);
            if (!statusIds.isEmpty()) {
                String idsStr = statusIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("process.status NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Classification
        if (filters.containsKey("classification") && !filters.get("classification").isEmpty()) {
            Set<String> classificationNames = filters.get("classification");
            List<Integer> classificationIds = getClassificationIdsByName(conn, classificationNames);
            if (!classificationIds.isEmpty()) {
                String idsStr = classificationIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("process.processclass_id NOT IN (" + idsStr + ")");
            }
            // Also handle null values if "null" is in the filter
            if (classificationNames.contains("null") || classificationNames.contains("Not Set")) {
                conditions.add("process.processclass_id IS NOT NULL");
            }
        }
        
        // Exclude by Automation
        if (filters.containsKey("automation") && !filters.get("automation").isEmpty()) {
            Set<String> automationNames = filters.get("automation");
            List<Integer> automationIds = getAutomationIdsByName(conn, automationNames);
            if (!automationIds.isEmpty()) {
                String idsStr = automationIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("process.processautomation_id NOT IN (" + idsStr + ")");
            }
            // Also handle null values if "null" is in the filter
            if (automationNames.contains("null") || automationNames.contains("Not Set")) {
                conditions.add("process.processautomation_id IS NOT NULL");
            }
        }
        
        // Exclude by Level (based on parentid)
        // "Top level" = no parent (parentid IS NULL)
        // "Second level" = has parent (parentid IS NOT NULL)
        if (filters.containsKey("level") && !filters.get("level").isEmpty()) {
            Set<String> levelNames = filters.get("level");
            if (levelNames.contains("Top level")) {
                conditions.add("process.parentid IS NOT NULL");
            }
            if (levelNames.contains("Second level")) {
                conditions.add("process.parentid IS NULL");
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
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT id FROM process_type WHERE PrimaryName IN (" + placeholders + ")";
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
    
    private List<Integer> getLifecycleIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT id FROM process_lifecycle_status WHERE primaryname IN (" + placeholders + ")";
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
    
    private List<Integer> getClassificationIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT id FROM process_class WHERE primaryname IN (" + placeholders + ")";
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
    
    private List<Integer> getAutomationIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT id FROM process_automation WHERE primaryname IN (" + placeholders + ")";
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
     * Build segment filter condition for process
     */
    private String buildSegmentFilter(Integer userId, String idColumn) {
        if (userId != null && userId > 0) {
            try {
                String segmentCondition = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Process", idColumn);
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
                    "AND sot.Type = 'Process' " +
                    "AND sxr.Segment_ID = 1 " +
                    "AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR " +
                "NOT EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'Process' " +
                    "AND sxr.Deleted_At IS NULL" +
                ")" +
            ")";
        }
        return "";
    }

    /**
     * Get Type stats - returns ALL type options with counts (including 0)
     */
    private List<Map<String, Object>> getTypeStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT pt.PrimaryName as label, COALESCE(COUNT(pr.id), 0) as count " +
                     "FROM process_type pt " +
                     "LEFT JOIN (SELECT * FROM process WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter + ") pr ON pr.type = pt.id " +
                     "GROUP BY pt.id, pt.PrimaryName " +
                     "ORDER BY pt.id";
        
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
     * Get Lifecycle stats - returns ALL lifecycle options with counts (including 0)
     */
    private List<Map<String, Object>> getLifecycleStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT pls.primaryname as label, COALESCE(COUNT(pr.id), 0) as count " +
                     "FROM process_lifecycle_status pls " +
                     "LEFT JOIN (SELECT * FROM process WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter + ") pr ON pr.lifecycle_status = pls.id " +
                     "GROUP BY pls.id, pls.primaryname " +
                     "ORDER BY pls.id";
        
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
        
        String sql = "SELECT COALESCE(s.PrimaryName, s.primaryname) as label, COALESCE(COUNT(pr.id), 0) as count " +
                     "FROM status s " +
                     "LEFT JOIN (SELECT * FROM process WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter + ") pr ON pr.status = s.id " +
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
        
        String sql = "SELECT pc.primaryname as label, COALESCE(COUNT(pr.id), 0) as count " +
                     "FROM process_class pc " +
                     "LEFT JOIN (SELECT * FROM process WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter + ") pr ON pr.processclass_id = pc.id " +
                     "GROUP BY pc.id, pc.primaryname " +
                     "ORDER BY pc.id";
        
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
        String nullSql = "SELECT COUNT(*) as count FROM process WHERE processclass_id IS NULL AND deleteddatetime IS NULL" + filterWhereClause + segmentFilter;
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
     * Get Automation stats - returns ALL automation options with counts (including 0)
     */
    private List<Map<String, Object>> getAutomationStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT pa.primaryname as label, COALESCE(COUNT(pr.id), 0) as count " +
                     "FROM process_automation pa " +
                     "LEFT JOIN (SELECT * FROM process WHERE deleteddatetime IS NULL" + filterWhereClause + segmentFilter + ") pr ON pr.processautomation_id = pa.id " +
                     "GROUP BY pa.id, pa.primaryname " +
                     "ORDER BY pa.id";
        
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
        String nullSql = "SELECT COUNT(*) as count FROM process WHERE processautomation_id IS NULL AND deleteddatetime IS NULL" + filterWhereClause + segmentFilter;
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
     * Get Level stats - returns level distribution based on parentid
     * "Top level" = no parent (parentid IS NULL)
     * "Second level" = has parent (parentid IS NOT NULL)
     */
    private List<Map<String, Object>> getLevelStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Top level (no parent)
        String topLevelSql = "SELECT COUNT(*) as count FROM process WHERE parentid IS NULL AND deleteddatetime IS NULL" + filterWhereClause + segmentFilter;
        try (PreparedStatement ps = conn.prepareStatement(topLevelSql);
             ResultSet rs = ps.executeQuery()) {
            int topLevelCount = 0;
            if (rs.next()) {
                topLevelCount = rs.getInt("count");
            }
            Map<String, Object> topLevelItem = new HashMap<>();
            topLevelItem.put("label", "Top level");
            topLevelItem.put("count", topLevelCount);
            stats.add(topLevelItem);
        }
        
        // Second level (has parent)
        String secondLevelSql = "SELECT COUNT(*) as count FROM process WHERE parentid IS NOT NULL AND deleteddatetime IS NULL" + filterWhereClause + segmentFilter;
        try (PreparedStatement ps = conn.prepareStatement(secondLevelSql);
             ResultSet rs = ps.executeQuery()) {
            int secondLevelCount = 0;
            if (rs.next()) {
                secondLevelCount = rs.getInt("count");
            }
            Map<String, Object> secondLevelItem = new HashMap<>();
            secondLevelItem.put("label", "Second level");
            secondLevelItem.put("count", secondLevelCount);
            stats.add(secondLevelItem);
        }
        
        return stats;
    }
}

