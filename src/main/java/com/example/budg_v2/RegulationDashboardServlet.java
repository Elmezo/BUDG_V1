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

@WebServlet(name = "RegulationDashboardServlet", urlPatterns = {"/api/regulation/dashboard/stats"})
public class RegulationDashboardServlet extends HttpServlet {

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
            
            // Build WHERE clause for filtering regulation records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build segment filter condition
            String segmentFilter = buildSegmentFilter(userId, "regulation.id");
            
            // First, get total count of filtered non-deleted regulation records for verification
            String totalCountSql = "SELECT COUNT(*) as total FROM regulation WHERE DeletedDatetime IS NULL" + filterWhereClause + segmentFilter;
            try (PreparedStatement ps = conn.prepareStatement(totalCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("totalCount", rs.getInt("total"));
                }
            }
            
            // Get statistics for each dropdown field - return ALL options (including 0 counts)
            List<Map<String, Object>> statusStats = getStatusStats(conn, filterWhereClause, segmentFilter);
            result.put("status", statusStats);
            
            List<Map<String, Object>> stageStats = getStageStats(conn, filterWhereClause, segmentFilter);
            result.put("stage", stageStats);
            
            List<Map<String, Object>> maturityStats = getMaturityStats(conn, filterWhereClause, segmentFilter);
            result.put("maturity", maturityStats);
            
            List<Map<String, Object>> probabilityStats = getProbabilityStats(conn, filterWhereClause, segmentFilter);
            result.put("probability", probabilityStats);
            
            List<Map<String, Object>> complianceLevelStats = getComplianceLevelStats(conn, filterWhereClause, segmentFilter);
            result.put("complianceLevel", complianceLevelStats);
            
            // Verify counts add up correctly (for debugging)
            int statusSum = statusStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int stageSum = stageStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int maturitySum = maturityStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int probabilitySum = probabilityStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int complianceLevelSum = complianceLevelStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "statusSum", statusSum,
                "stageSum", stageSum,
                "maturitySum", maturitySum,
                "probabilitySum", probabilitySum,
                "complianceLevelSum", complianceLevelSum
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
        
        String excludeStatus = req.getParameter("excludeStatus");
        if (excludeStatus != null && !excludeStatus.trim().isEmpty()) {
            filters.put("status", new HashSet<>(Arrays.asList(excludeStatus.split(","))));
        }
        
        String excludeStage = req.getParameter("excludeStage");
        if (excludeStage != null && !excludeStage.trim().isEmpty()) {
            filters.put("stage", new HashSet<>(Arrays.asList(excludeStage.split(","))));
        }
        
        String excludeMaturity = req.getParameter("excludeMaturity");
        if (excludeMaturity != null && !excludeMaturity.trim().isEmpty()) {
            filters.put("maturity", new HashSet<>(Arrays.asList(excludeMaturity.split(","))));
        }
        
        String excludeProbability = req.getParameter("excludeProbability");
        if (excludeProbability != null && !excludeProbability.trim().isEmpty()) {
            filters.put("probability", new HashSet<>(Arrays.asList(excludeProbability.split(","))));
        }
        
        String excludeComplianceLevel = req.getParameter("excludeComplianceLevel");
        if (excludeComplianceLevel != null && !excludeComplianceLevel.trim().isEmpty()) {
            filters.put("complianceLevel", new HashSet<>(Arrays.asList(excludeComplianceLevel.split(","))));
        }
        
        return filters;
    }
    
    /**
     * Build WHERE clause to exclude filtered regulation records
     */
    private String buildFilterWhereClause(Map<String, Set<String>> filters, Connection conn) throws SQLException {
        if (filters.isEmpty()) {
            return "";
        }
        
        List<String> conditions = new ArrayList<>();
        
        // Exclude by Status (using regulation_status table, no nulls for status)
        if (filters.containsKey("status") && !filters.get("status").isEmpty()) {
            Set<String> statusNames = filters.get("status");
            List<Integer> statusIds = getStatusIdsByName(conn, statusNames);
            if (!statusIds.isEmpty()) {
                String idsStr = statusIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("regulation.RegulationStatus_ID NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Stage (no nulls for stage)
        if (filters.containsKey("stage") && !filters.get("stage").isEmpty()) {
            Set<String> stageNames = filters.get("stage");
            List<Integer> stageIds = getStageIdsByName(conn, stageNames);
            if (!stageIds.isEmpty()) {
                String idsStr = stageIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("regulation.RegulationStage_ID NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Maturity (no nulls for maturity)
        if (filters.containsKey("maturity") && !filters.get("maturity").isEmpty()) {
            Set<String> maturityNames = filters.get("maturity");
            List<Integer> maturityIds = getMaturityIdsByName(conn, maturityNames);
            if (!maturityIds.isEmpty()) {
                String idsStr = maturityIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("regulation.RegulationMaturity_ID NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Probability (no nulls for probability)
        if (filters.containsKey("probability") && !filters.get("probability").isEmpty()) {
            Set<String> probabilityNames = filters.get("probability");
            List<Integer> probabilityIds = getProbabilityIdsByName(conn, probabilityNames);
            if (!probabilityIds.isEmpty()) {
                String idsStr = probabilityIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("regulation.RegulationProbability_ID NOT IN (" + idsStr + ")");
            }
        }
        
        // Exclude by Compliance Level (no nulls for compliance level)
        if (filters.containsKey("complianceLevel") && !filters.get("complianceLevel").isEmpty()) {
            Set<String> complianceLevelNames = filters.get("complianceLevel");
            List<Integer> complianceLevelIds = getComplianceLevelIdsByName(conn, complianceLevelNames);
            if (!complianceLevelIds.isEmpty()) {
                String idsStr = complianceLevelIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("regulation.ComplianceLevel_ID NOT IN (" + idsStr + ")");
            }
        }
        
        if (conditions.isEmpty()) {
            return "";
        }
        
        return " AND " + String.join(" AND ", conditions);
    }
    
    private List<Integer> getStatusIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM regulation_status WHERE PrimaryName IN (" + placeholders + ")";
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
    
    private List<Integer> getStageIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM regulation_stage WHERE PrimaryName IN (" + placeholders + ")";
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
    
    private List<Integer> getMaturityIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM regulation_maturity WHERE PrimaryName IN (" + placeholders + ")";
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
    
    private List<Integer> getProbabilityIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM regulation_probability WHERE PrimaryName IN (" + placeholders + ")";
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
    
    private List<Integer> getComplianceLevelIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM regulation_compliance_level WHERE PrimaryName IN (" + placeholders + ")";
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

    /**
     * Build segment filter condition for regulation
     */
    private String buildSegmentFilter(Integer userId, String idColumn) {
        if (userId != null && userId > 0) {
            try {
                String segmentCondition = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Regulation", idColumn);
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
                    "AND sot.Type = 'Regulation' " +
                    "AND sxr.Segment_ID = 1 " +
                    "AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR " +
                "NOT EXISTS (" +
                    "SELECT 1 FROM segment_x_resource sxr " +
                    "JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                    "JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                    "WHERE orr.Object_ID = " + idColumn + " " +
                    "AND sot.Type = 'Regulation' " +
                    "AND sxr.Deleted_At IS NULL" +
                ")" +
            ")";
        }
        return "";
    }
    
    /**
     * Get Status stats - returns ALL status options with counts (including 0)
     */
    private List<Map<String, Object>> getStatusStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT rs.PrimaryName as label, COALESCE(COUNT(r.ID), 0) as count " +
                     "FROM regulation_status rs " +
                     "LEFT JOIN (SELECT * FROM regulation WHERE DeletedDatetime IS NULL" + filterWhereClause + segmentFilter + ") r ON r.RegulationStatus_ID = rs.ID " +
                     "GROUP BY rs.ID, rs.PrimaryName " +
                     "ORDER BY rs.ID";
        
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
        
        // Note: Status does NOT include null values
        
        return stats;
    }
    
    /**
     * Get Stage stats - returns ALL stage options with counts (including 0)
     */
    private List<Map<String, Object>> getStageStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT rst.PrimaryName as label, COALESCE(COUNT(r.ID), 0) as count " +
                     "FROM regulation_stage rst " +
                     "LEFT JOIN (SELECT * FROM regulation WHERE DeletedDatetime IS NULL" + filterWhereClause + segmentFilter + ") r ON r.RegulationStage_ID = rst.ID " +
                     "GROUP BY rst.ID, rst.PrimaryName " +
                     "ORDER BY rst.ID";
        
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
        
        // Note: Stage does NOT include null values
        
        return stats;
    }
    
    /**
     * Get Maturity stats - returns ALL maturity options with counts (including 0)
     */
    private List<Map<String, Object>> getMaturityStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT rm.PrimaryName as label, COALESCE(COUNT(r.ID), 0) as count " +
                     "FROM regulation_maturity rm " +
                     "LEFT JOIN (SELECT * FROM regulation WHERE DeletedDatetime IS NULL" + filterWhereClause + segmentFilter + ") r ON r.RegulationMaturity_ID = rm.ID " +
                     "GROUP BY rm.ID, rm.PrimaryName " +
                     "ORDER BY rm.ID";
        
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
        
        // Note: Maturity does NOT include null values
        
        return stats;
    }
    
    /**
     * Get Probability stats - returns ALL probability options with counts (including 0)
     */
    private List<Map<String, Object>> getProbabilityStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT rp.PrimaryName as label, COALESCE(COUNT(r.ID), 0) as count " +
                     "FROM regulation_probability rp " +
                     "LEFT JOIN (SELECT * FROM regulation WHERE DeletedDatetime IS NULL" + filterWhereClause + segmentFilter + ") r ON r.RegulationProbability_ID = rp.ID " +
                     "GROUP BY rp.ID, rp.PrimaryName " +
                     "ORDER BY rp.ID";
        
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
        
        // Note: Probability does NOT include null values
        
        return stats;
    }
    
    /**
     * Get Compliance Level stats - returns ALL compliance level options with counts (including 0)
     */
    private List<Map<String, Object>> getComplianceLevelStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT rcl.PrimaryName as label, COALESCE(COUNT(r.ID), 0) as count " +
                     "FROM regulation_compliance_level rcl " +
                     "LEFT JOIN (SELECT * FROM regulation WHERE DeletedDatetime IS NULL" + filterWhereClause + segmentFilter + ") r ON r.ComplianceLevel_ID = rcl.ID " +
                     "GROUP BY rcl.ID, rcl.PrimaryName " +
                     "ORDER BY rcl.ID";
        
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
        
        // Note: Compliance Level does NOT include null values
        
        return stats;
    }
}
