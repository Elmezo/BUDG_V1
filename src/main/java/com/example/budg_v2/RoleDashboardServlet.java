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

@WebServlet(name = "RoleDashboardServlet", urlPatterns = {"/api/role/dashboard/stats"})
public class RoleDashboardServlet extends HttpServlet {

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
            
            // Build WHERE clause for filtering role records
            String filterWhereClause = buildFilterWhereClause(filters, conn);
            
            // Build segment filter condition for roles (based on linked objects' segments)
            String segmentFilter = buildSegmentFilterForRoles(userId, conn);
            
            // Ensure segmentFilter is not null or empty - if it is, use empty string (no filtering)
            if (segmentFilter == null || segmentFilter.trim().isEmpty()) {
                System.err.println("[RoleDashboard] Warning: segmentFilter is null or empty, using empty string");
                segmentFilter = "";
            }
            
            // Ensure filterWhereClause is not null
            if (filterWhereClause == null) {
                filterWhereClause = "";
            }
            
            // First, get total count of filtered role records for verification
            // Note: object_x_people doesn't have a soft delete column, so we filter by people.Deleted_date
            String totalCountSql = "SELECT COUNT(*) as total FROM object_x_people oxp " +
                                   "LEFT JOIN people p ON oxp.ipid = p.ID " +
                                   "WHERE (p.Deleted_date IS NULL OR p.ID IS NULL)" + filterWhereClause + segmentFilter;
            
            // Debug logging
            System.out.println("[RoleDashboard] filterWhereClause: [" + filterWhereClause + "]");
            System.out.println("[RoleDashboard] segmentFilter: [" + segmentFilter + "]");
            System.out.println("[RoleDashboard] segmentFilter length: " + (segmentFilter != null ? segmentFilter.length() : "null"));
            System.out.println("[RoleDashboard] totalCountSql: " + totalCountSql);
            
            // Validate SQL before executing
            if (totalCountSql == null || totalCountSql.trim().isEmpty()) {
                throw new SQLException("Generated SQL is null or empty");
            }
            
            // Count parentheses to ensure they're balanced
            long openParens = totalCountSql.chars().filter(ch -> ch == '(').count();
            long closeParens = totalCountSql.chars().filter(ch -> ch == ')').count();
            if (openParens != closeParens) {
                System.err.println("[RoleDashboard] WARNING: Unbalanced parentheses! Open: " + openParens + ", Close: " + closeParens);
                System.err.println("[RoleDashboard] SQL length: " + totalCountSql.length());
            }
            
            try (PreparedStatement ps = conn.prepareStatement(totalCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("totalCount", rs.getInt("total"));
                }
            } catch (SQLException e) {
                System.err.println("[RoleDashboard] SQL Error executing totalCountSql:");
                System.err.println("[RoleDashboard] SQL: " + totalCountSql);
                System.err.println("[RoleDashboard] Error: " + e.getMessage());
                throw e;
            }
            
            // Get statistics for each dropdown field - return ALL options (including 0 counts)
            List<Map<String, Object>> roleTypeStats = getRoleTypeStats(conn, filterWhereClause, segmentFilter);
            result.put("roleType", roleTypeStats);
            
            List<Map<String, Object>> objectTypeStats = getObjectTypeStats(conn, filterWhereClause, segmentFilter);
            result.put("objectType", objectTypeStats);
            
            List<Map<String, Object>> roleAcceptedStats = getRoleAcceptedStats(conn, filterWhereClause, segmentFilter);
            result.put("roleAccepted", roleAcceptedStats);
            
            // Verify counts add up correctly (for debugging)
            int roleTypeSum = roleTypeStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int objectTypeSum = objectTypeStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            int roleAcceptedSum = roleAcceptedStats.stream().mapToInt(m -> (Integer) m.get("count")).sum();
            
            result.put("verification", Map.of(
                "roleTypeSum", roleTypeSum,
                "objectTypeSum", objectTypeSum,
                "roleAcceptedSum", roleAcceptedSum
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
        
        String excludeRoleType = req.getParameter("excludeRoleType");
        if (excludeRoleType != null && !excludeRoleType.trim().isEmpty()) {
            filters.put("roleType", new HashSet<>(Arrays.asList(excludeRoleType.split(","))));
        }
        
        String excludeObjectType = req.getParameter("excludeObjectType");
        if (excludeObjectType != null && !excludeObjectType.trim().isEmpty()) {
            filters.put("objectType", new HashSet<>(Arrays.asList(excludeObjectType.split(","))));
        }
        
        String excludeRoleAccepted = req.getParameter("excludeRoleAccepted");
        if (excludeRoleAccepted != null && !excludeRoleAccepted.trim().isEmpty()) {
            filters.put("roleAccepted", new HashSet<>(Arrays.asList(excludeRoleAccepted.split(","))));
        }
        
        return filters;
    }
    
    /**
     * Build WHERE clause to exclude filtered role records
     */
    private String buildFilterWhereClause(Map<String, Set<String>> filters, Connection conn) throws SQLException {
        if (filters.isEmpty()) {
            return "";
        }
        
        List<String> conditions = new ArrayList<>();
        
        // Exclude by Role Type (no nulls for role type)
        if (filters.containsKey("roleType") && !filters.get("roleType").isEmpty()) {
            Set<String> roleTypeNames = filters.get("roleType");
            List<Integer> roleTypeIds = getRoleTypeIdsByName(conn, roleTypeNames);
            if (!roleTypeIds.isEmpty()) {
                String idsStr = roleTypeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("oxp.id NOT IN (SELECT oxp2.id FROM object_x_people oxp2 " +
                               "JOIN object_role orl2 ON oxp2.roleID = orl2.id " +
                               "WHERE orl2.objectroletype_id IN (" + idsStr + "))");
            }
        }
        
        // Exclude by Object Type (no nulls for object type)
        if (filters.containsKey("objectType") && !filters.get("objectType").isEmpty()) {
            Set<String> objectTypeNames = filters.get("objectType");
            List<Integer> moduleIds = getModuleIdsByName(conn, objectTypeNames);
            if (!moduleIds.isEmpty()) {
                String idsStr = moduleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                conditions.add("oxp.id NOT IN (SELECT oxp2.id FROM object_x_people oxp2 " +
                               "JOIN object_role orl2 ON oxp2.roleID = orl2.id " +
                               "WHERE orl2.module IN (" + idsStr + "))");
            }
        }
        
        // Exclude by Role Accepted
        if (filters.containsKey("roleAccepted") && !filters.get("roleAccepted").isEmpty()) {
            Set<String> roleAcceptedNames = filters.get("roleAccepted");
            if (roleAcceptedNames.contains("Yes")) {
                conditions.add("oxp.AcceptedID IS NOT NULL");
            }
            if (roleAcceptedNames.contains("No")) {
                conditions.add("oxp.AcceptedID IS NULL");
            }
        }
        
        if (conditions.isEmpty()) {
            return "";
        }
        
        return " AND " + String.join(" AND ", conditions);
    }
    
    private List<Integer> getRoleTypeIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT id FROM object_role_type WHERE primaryname IN (" + placeholders + ")";
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
    
    private List<Integer> getModuleIdsByName(Connection conn, Set<String> names) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (names.isEmpty()) return ids;
        
        Set<String> validNames = names.stream()
            .filter(n -> n != null && !n.trim().equalsIgnoreCase("null") && !n.trim().equalsIgnoreCase("Not Set"))
            .collect(java.util.stream.Collectors.toSet());
        
        if (validNames.isEmpty()) return ids;
        
        String placeholders = validNames.stream().map(n -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ID FROM module WHERE PrimaryName IN (" + placeholders + ")";
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
     * Build segment filter condition for roles
     * Roles are filtered based on the segments of the objects they are assigned to
     */
    private String buildSegmentFilterForRoles(Integer userId, Connection conn) {
        if (userId == null || userId <= 0) {
            // Anonymous user - Enterprise only
            System.out.println("[RoleDashboard] buildSegmentFilterForRoles: Anonymous user, using anonymous filter");
            return buildAnonymousRoleSegmentFilter();
        }
        
        try {
            // Get effective segment IDs
            Set<Integer> effectiveSegments = SegmentAccessService.getEffectiveFilterSegmentIds(userId);
            System.out.println("[RoleDashboard] buildSegmentFilterForRoles: effectiveSegments = " + effectiveSegments);
            if (effectiveSegments == null || effectiveSegments.isEmpty()) {
                System.out.println("[RoleDashboard] buildSegmentFilterForRoles: No effective segments, using anonymous filter");
                return buildAnonymousRoleSegmentFilter();
            }
            
            String segmentIds = effectiveSegments.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            
            // Ensure segmentIds is not empty
            if (segmentIds == null || segmentIds.trim().isEmpty()) {
                System.err.println("Warning: segmentIds is empty, using anonymous filter");
                return buildAnonymousRoleSegmentFilter();
            }
            
            System.out.println("[RoleDashboard] buildSegmentFilterForRoles: segmentIds = " + segmentIds);
            boolean enterpriseSelected = effectiveSegments.contains(1);
            System.out.println("[RoleDashboard] buildSegmentFilterForRoles: enterpriseSelected = " + enterpriseSelected);
            
            // Build condition that checks if ANY linked object is in selected segments
            // This checks all possible object linking tables
            StringBuilder condition = new StringBuilder(" AND (");
            
            // For each object type, add a condition checking if the linked object is in selected segments
            // Dataset
            condition.append("(EXISTS (SELECT 1 FROM dataset_x_objectxpeople dxoxp JOIN dataset d ON dxoxp.Dataset_ID = d.ID WHERE dxoxp.Object_x_ipid = oxp.id AND d.DeletedDatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = d.ID AND sot.Type = 'Dataset' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = d.ID AND sot.Type = 'Dataset' AND sxr.Deleted_At IS NULL)))");
                // For enterpriseSelected, the line above already closes: inner EXISTS, AND group, and outer EXISTS
                // We still need to close the Dataset condition group opening parenthesis
                condition.append(")");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = d.ID AND sot.Type = 'Dataset' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
                // For else branch: close the outer EXISTS and the Dataset condition group
                condition.append("))");
            }
            
            // System
            condition.append(" OR EXISTS (SELECT 1 FROM system_x_objectxpeople sxoxp JOIN system s ON sxoxp.SystemID = s.id WHERE sxoxp.Object_x_ipid = oxp.id AND s.Deleted_datetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = s.id AND sot.Type = 'System' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = s.id AND sot.Type = 'System' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = s.id AND sot.Type = 'System' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Interface
            condition.append(" OR EXISTS (SELECT 1 FROM interface_x_objectxpeople ixoxp JOIN interface i ON ixoxp.InterfaceID = i.id WHERE ixoxp.Object_x_ipid = oxp.id AND i.deleted_datetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = i.id AND sot.Type = 'SystemInterface' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = i.id AND sot.Type = 'SystemInterface' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = i.id AND sot.Type = 'SystemInterface' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Glossary
            condition.append(" OR EXISTS (SELECT 1 FROM glossary_x_objectxpeople gxoxp JOIN glossary g ON gxoxp.GlossaryID = g.ID WHERE gxoxp.Object_x_ipid = oxp.id AND g.Deleted_datetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Process
            condition.append(" OR EXISTS (SELECT 1 FROM process_x_objectxpeople prcxoxp JOIN process p ON prcxoxp.process_id = p.id WHERE prcxoxp.object_x_ip = oxp.id AND p.deleteddatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = p.id AND sot.Type = 'Process' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = p.id AND sot.Type = 'Process' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = p.id AND sot.Type = 'Process' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Policy
            condition.append(" OR EXISTS (SELECT 1 FROM policy_x_objectxpeople polxoxp JOIN policy pol ON polxoxp.Policy_ID = pol.id WHERE polxoxp.Object_X_IP = oxp.id AND pol.DeletedDatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = pol.ID AND sot.Type = 'Policy' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = pol.ID AND sot.Type = 'Policy' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = pol.ID AND sot.Type = 'Policy' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Product
            condition.append(" OR EXISTS (SELECT 1 FROM product_x_objectxpeople prodxoxp JOIN product prod ON prodxoxp.product_id = prod.id WHERE prodxoxp.Object_x_ip = oxp.id AND prod.deleteddatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = prod.id AND sot.Type = 'Product' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = prod.id AND sot.Type = 'Product' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = prod.id AND sot.Type = 'Product' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Project
            condition.append(" OR EXISTS (SELECT 1 FROM project_x_objectxpeople projxoxp JOIN project proj ON projxoxp.project_id = proj.id WHERE projxoxp.object_x_ip = oxp.id AND proj.deletedatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = proj.id AND sot.Type = 'Project' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = proj.id AND sot.Type = 'Project' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = proj.id AND sot.Type = 'Project' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // BusinessArea
            condition.append(" OR EXISTS (SELECT 1 FROM businessarea_x_objectxpeople baxoxp JOIN business_area ba ON baxoxp.BusinessAreaID = ba.id WHERE baxoxp.Object_x_ipid = oxp.id AND ba.deletedatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = ba.id AND sot.Type = 'BusinessConnection' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = ba.id AND sot.Type = 'BusinessConnection' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = ba.id AND sot.Type = 'BusinessConnection' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Client
            condition.append(" OR EXISTS (SELECT 1 FROM client_x_objectxpeople cxoxp JOIN client c ON cxoxp.ClientID = c.id WHERE cxoxp.Object_x_ipid = oxp.id AND c.DeleteDatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = c.id AND sot.Type = 'Client' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = c.id AND sot.Type = 'Client' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = c.id AND sot.Type = 'Client' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Committee
            condition.append(" OR EXISTS (SELECT 1 FROM committee_x_objectxpeople comxoxp JOIN committee com ON comxoxp.Committee_ID = com.id WHERE comxoxp.Object_x_ipid = oxp.id AND com.DeleteDatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = com.id AND sot.Type = 'Committee' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = com.id AND sot.Type = 'Committee' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = com.id AND sot.Type = 'Committee' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Legal (LegalEntity)
            condition.append(" OR EXISTS (SELECT 1 FROM legal_x_objectxpeople lexoxp JOIN legal le ON lexoxp.Legal_ID = le.id WHERE lexoxp.Object_x_ip = oxp.id AND le.DeleteDatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = le.id AND sot.Type = 'Legal' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = le.id AND sot.Type = 'Legal' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = le.id AND sot.Type = 'Legal' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Capability
            condition.append(" OR EXISTS (SELECT 1 FROM capability_x_objectxpeople capxoxp JOIN capability cap ON capxoxp.CapabilityID = cap.id WHERE capxoxp.Object_x_ipid = oxp.id AND cap.DeletedDatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = cap.id AND sot.Type = 'Capability' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = cap.id AND sot.Type = 'Capability' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = cap.id AND sot.Type = 'Capability' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Regulation
            condition.append(" OR EXISTS (SELECT 1 FROM regulation_x_objectxpeople regxoxp JOIN regulation reg ON regxoxp.RegulationID = reg.id WHERE regxoxp.Object_x_ipid = oxp.id AND reg.DeletedDatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = reg.id AND sot.Type = 'Regulation' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = reg.id AND sot.Type = 'Regulation' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = reg.id AND sot.Type = 'Regulation' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            // Attribute
            condition.append(" OR EXISTS (SELECT 1 FROM attribute_x_objectxpeople axoxp JOIN attribute a ON axoxp.AttributeID = a.id WHERE axoxp.Object_x_ipid = oxp.id AND a.DeletedDatetime IS NULL");
            if (enterpriseSelected) {
                condition.append(" AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = a.id AND sot.Type = 'Attribute' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL) OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = a.id AND sot.Type = 'Attribute' AND sxr.Deleted_At IS NULL)))");
            } else {
                condition.append(" AND EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = a.id AND sot.Type = 'Attribute' AND sxr.Segment_ID IN (").append(segmentIds).append(") AND sxr.Deleted_At IS NULL)");
            }
            condition.append(")");
            
            condition.append(")");
            
            String result = condition.toString();
            System.out.println("[RoleDashboard] buildSegmentFilterForRoles: Built condition length = " + result.length());
            System.out.println("[RoleDashboard] buildSegmentFilterForRoles: Condition preview = " + (result.length() > 200 ? result.substring(0, 200) + "..." : result));
            return result;
        } catch (SQLException e) {
            System.err.println("Error building role segment filter: " + e.getMessage());
            e.printStackTrace();
            // Return anonymous filter as fallback instead of empty string
            return buildAnonymousRoleSegmentFilter();
        } catch (Exception e) {
            System.err.println("Unexpected error building role segment filter: " + e.getMessage());
            e.printStackTrace();
            // Return anonymous filter as fallback instead of empty string
            return buildAnonymousRoleSegmentFilter();
        }
    }
    
    /**
     * Build segment filter for anonymous users (Enterprise only)
     */
    private String buildAnonymousRoleSegmentFilter() {
        // For anonymous users, only show roles linked to Enterprise objects
        return " AND (" +
            // Dataset - Enterprise only
            "(EXISTS (SELECT 1 FROM dataset_x_objectxpeople dxoxp JOIN dataset d ON dxoxp.Dataset_ID = d.ID WHERE dxoxp.Object_x_ipid = oxp.id AND d.DeletedDatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = d.ID AND sot.Type = 'Dataset' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = d.ID AND sot.Type = 'Dataset' AND sxr.Deleted_At IS NULL)))) " +
            // System - Enterprise only
            "OR EXISTS (SELECT 1 FROM system_x_objectxpeople sxoxp JOIN system s ON sxoxp.SystemID = s.id WHERE sxoxp.Object_x_ipid = oxp.id AND s.Deleted_datetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = s.id AND sot.Type = 'System' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = s.id AND sot.Type = 'System' AND sxr.Deleted_At IS NULL))) " +
            // Interface - Enterprise only
            "OR EXISTS (SELECT 1 FROM interface_x_objectxpeople ixoxp JOIN interface i ON ixoxp.InterfaceID = i.id WHERE ixoxp.Object_x_ipid = oxp.id AND i.deleted_datetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = i.id AND sot.Type = 'SystemInterface' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = i.id AND sot.Type = 'SystemInterface' AND sxr.Deleted_At IS NULL))) " +
            // Glossary - Enterprise only
            "OR EXISTS (SELECT 1 FROM glossary_x_objectxpeople gxoxp JOIN glossary g ON gxoxp.GlossaryID = g.ID WHERE gxoxp.Object_x_ipid = oxp.id AND g.Deleted_datetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' AND sxr.Deleted_At IS NULL))) " +
            // Process - Enterprise only
            "OR EXISTS (SELECT 1 FROM process_x_objectxpeople prcxoxp JOIN process p ON prcxoxp.process_id = p.id WHERE prcxoxp.object_x_ip = oxp.id AND p.deleteddatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = p.id AND sot.Type = 'Process' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = p.id AND sot.Type = 'Process' AND sxr.Deleted_At IS NULL))) " +
            // Policy - Enterprise only
            "OR EXISTS (SELECT 1 FROM policy_x_objectxpeople polxoxp JOIN policy pol ON polxoxp.Policy_ID = pol.id WHERE polxoxp.Object_X_IP = oxp.id AND pol.DeletedDatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = pol.ID AND sot.Type = 'Policy' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = pol.ID AND sot.Type = 'Policy' AND sxr.Deleted_At IS NULL))) " +
            // Product - Enterprise only
            "OR EXISTS (SELECT 1 FROM product_x_objectxpeople prodxoxp JOIN product prod ON prodxoxp.product_id = prod.id WHERE prodxoxp.Object_x_ip = oxp.id AND prod.deleteddatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = prod.id AND sot.Type = 'Product' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = prod.id AND sot.Type = 'Product' AND sxr.Deleted_At IS NULL))) " +
            // Project - Enterprise only
            "OR EXISTS (SELECT 1 FROM project_x_objectxpeople projxoxp JOIN project proj ON projxoxp.project_id = proj.id WHERE projxoxp.object_x_ip = oxp.id AND proj.deletedatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = proj.id AND sot.Type = 'Project' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = proj.id AND sot.Type = 'Project' AND sxr.Deleted_At IS NULL))) " +
            // BusinessArea - Enterprise only
            "OR EXISTS (SELECT 1 FROM businessarea_x_objectxpeople baxoxp JOIN business_area ba ON baxoxp.BusinessAreaID = ba.id WHERE baxoxp.Object_x_ipid = oxp.id AND ba.deletedatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = ba.id AND sot.Type = 'BusinessConnection' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = ba.id AND sot.Type = 'BusinessConnection' AND sxr.Deleted_At IS NULL))) " +
            // Client - Enterprise only
            "OR EXISTS (SELECT 1 FROM client_x_objectxpeople cxoxp JOIN client c ON cxoxp.ClientID = c.id WHERE cxoxp.Object_x_ipid = oxp.id AND c.DeleteDatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = c.id AND sot.Type = 'Client' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = c.id AND sot.Type = 'Client' AND sxr.Deleted_At IS NULL))) " +
            // Committee - Enterprise only
            "OR EXISTS (SELECT 1 FROM committee_x_objectxpeople comxoxp JOIN committee com ON comxoxp.Committee_ID = com.id WHERE comxoxp.Object_x_ipid = oxp.id AND com.DeleteDatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = com.id AND sot.Type = 'Committee' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = com.id AND sot.Type = 'Committee' AND sxr.Deleted_At IS NULL))) " +
            // Legal - Enterprise only
            "OR EXISTS (SELECT 1 FROM legal_x_objectxpeople lexoxp JOIN legal le ON lexoxp.Legal_ID = le.id WHERE lexoxp.Object_x_ip = oxp.id AND le.DeleteDatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = le.id AND sot.Type = 'Legal' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = le.id AND sot.Type = 'Legal' AND sxr.Deleted_At IS NULL))) " +
            // Capability - Enterprise only
            "OR EXISTS (SELECT 1 FROM capability_x_objectxpeople capxoxp JOIN capability cap ON capxoxp.CapabilityID = cap.id WHERE capxoxp.Object_x_ipid = oxp.id AND cap.DeletedDatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = cap.id AND sot.Type = 'Capability' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = cap.id AND sot.Type = 'Capability' AND sxr.Deleted_At IS NULL))) " +
            // Regulation - Enterprise only
            "OR EXISTS (SELECT 1 FROM regulation_x_objectxpeople regxoxp JOIN regulation reg ON regxoxp.RegulationID = reg.id WHERE regxoxp.Object_x_ipid = oxp.id AND reg.DeletedDatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = reg.id AND sot.Type = 'Regulation' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = reg.id AND sot.Type = 'Regulation' AND sxr.Deleted_At IS NULL))) " +
            // Attribute - Enterprise only
            "OR EXISTS (SELECT 1 FROM attribute_x_objectxpeople axoxp JOIN attribute a ON axoxp.AttributeID = a.id WHERE axoxp.Object_x_ipid = oxp.id AND a.DeletedDatetime IS NULL " +
            "AND (EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = a.id AND sot.Type = 'Attribute' AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL) " +
            "OR NOT EXISTS (SELECT 1 FROM segment_x_resource sxr JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID WHERE orr.Object_ID = a.id AND sot.Type = 'Attribute' AND sxr.Deleted_At IS NULL))) " +
            ")";
    }
    
    /**
     * Get Role Type stats - returns ALL role type options with counts (including 0)
     */
    private List<Map<String, Object>> getRoleTypeStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT ort.primaryname as label, COALESCE(COUNT(oxp.id), 0) as count " +
                     "FROM object_role_type ort " +
                     "LEFT JOIN (SELECT oxp.id, orl.objectroletype_id " +
                     "           FROM object_x_people oxp " +
                     "           JOIN object_role orl ON oxp.roleID = orl.id " +
                     "           LEFT JOIN people p ON oxp.ipid = p.ID " +
                     "           WHERE (p.Deleted_date IS NULL OR p.ID IS NULL)" + filterWhereClause + segmentFilter + ") oxp " +
                     "ON oxp.objectroletype_id = ort.id " +
                     "GROUP BY ort.id, ort.primaryname " +
                     "ORDER BY ort.id";
        
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
     * Get Object Type stats - returns ONLY object types that are actually used (count > 0)
     */
    private List<Map<String, Object>> getObjectTypeStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        String sql = "SELECT m.PrimaryName as label, COUNT(oxp.id) as count " +
                     "FROM object_x_people oxp " +
                     "JOIN object_role orl ON oxp.roleID = orl.id " +
                     "JOIN module m ON orl.module = m.ID " +
                     "LEFT JOIN people p ON oxp.ipid = p.ID " +
                     "WHERE (p.Deleted_date IS NULL OR p.ID IS NULL)" + filterWhereClause + segmentFilter +
                     "GROUP BY m.ID, m.PrimaryName " +
                     "HAVING COUNT(oxp.id) > 0 " +
                     "ORDER BY m.ID";
        
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
     * Get Role Accepted stats - returns Yes/No counts
     */
    private List<Map<String, Object>> getRoleAcceptedStats(Connection conn, String filterWhereClause, String segmentFilter) throws SQLException {
        List<Map<String, Object>> stats = new ArrayList<>();
        
        // Count "Yes" (AcceptedID IS NOT NULL)
        String yesSql = "SELECT COUNT(*) as count FROM object_x_people oxp " +
                        "LEFT JOIN people p ON oxp.ipid = p.ID " +
                        "WHERE (p.Deleted_date IS NULL OR p.ID IS NULL) AND oxp.AcceptedID IS NOT NULL" + filterWhereClause + segmentFilter;
        try (PreparedStatement ps = conn.prepareStatement(yesSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Map<String, Object> yesItem = new HashMap<>();
                yesItem.put("label", "Yes");
                yesItem.put("count", rs.getInt("count"));
                stats.add(yesItem);
            }
        }
        
        // Count "No" (AcceptedID IS NULL)
        String noSql = "SELECT COUNT(*) as count FROM object_x_people oxp " +
                       "LEFT JOIN people p ON oxp.ipid = p.ID " +
                       "WHERE (p.Deleted_date IS NULL OR p.ID IS NULL) AND oxp.AcceptedID IS NULL" + filterWhereClause + segmentFilter;
        try (PreparedStatement ps = conn.prepareStatement(noSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Map<String, Object> noItem = new HashMap<>();
                noItem.put("label", "No");
                noItem.put("count", rs.getInt("count"));
                stats.add(noItem);
            }
        }
        
        return stats;
    }
}

