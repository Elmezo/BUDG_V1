package com.example.budg_v2.dao;

import com.example.budg_v2.audit.AuditHistoryWriter;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ModuleResolver;

import java.sql.*;
import java.util.*;

public class GlossaryDAO {

    public Map<String, Object> getById(int id) throws SQLException {
        String sql = "SELECT g.ID, g.Name, g.Description, g.Format, g.LDM, g.Business_Logic, g.Examples, " +
                "g.Status, st.primaryname AS statusName, g.Lifecycle, gl.Name AS lifecycleName, " +
                "g.Is_Public, v.Name AS viewingName, g.Type, gt.Name AS typeName, g.Security_Classification, sc.Name AS securityName, " +
                "g.Confidentiality_Rating, g.Integrity_Rating, g.Availability_Rating, g.Ref_Number, g.KDE, " +
                "g.Format_type, ft.Name AS formatTypeName, " +
                "g.Parent_ID, p.Name AS parentName, " +
                "g.CreatedBy_ID, g.Last_updated_userID, g.Created_Datetime, g.Last_Updated_Datetime, " +
                "CONCAT(cb.First_Name, ' ', cb.Last_Name) AS createdByName, CONCAT(ub.First_Name, ' ', ub.Last_Name) AS updatedByName " +
                "FROM glossary g " +
                "LEFT JOIN status st ON st.ID = g.Status " +
                "LEFT JOIN glossary_lifecycle gl ON gl.ID = g.Lifecycle " +
                "LEFT JOIN viewing v ON v.id = g.Is_Public " +
                "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "LEFT JOIN security_classification sc ON sc.ID = g.Security_Classification " +
                "LEFT JOIN glossary_format_type ft ON ft.ID = g.Format_type " +
                "LEFT JOIN glossary p ON p.ID = g.Parent_ID " +
                "LEFT JOIN people cb ON cb.ID = g.CreatedBy_ID " +
                "LEFT JOIN people ub ON ub.ID = g.Last_updated_userID " +
                "WHERE g.ID = ? AND g.Deleted_datetime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("Name"));
                row.put("description", rs.getString("Description"));
                row.put("format", rs.getString("Format"));
                row.put("ldm", rs.getString("LDM"));
                row.put("businessLogic", rs.getString("Business_Logic"));
                row.put("examples", rs.getString("Examples"));
                row.put("formatTypeName", rs.getString("formatTypeName"));
                row.put("statusName", rs.getString("statusName"));
                row.put("lifecycleName", rs.getString("lifecycleName"));
                row.put("viewingName", rs.getString("viewingName"));
                row.put("typeName", rs.getString("typeName"));
                row.put("securityName", rs.getString("securityName"));
                row.put("ref", rs.getString("Ref_Number"));
                row.put("parentId", rs.getObject("Parent_ID"));
                row.put("Parent_ID", rs.getObject("Parent_ID"));
                row.put("parentName", rs.getString("parentName"));
                
                // Add missing fields that are selected in SQL but not mapped
                row.put("Status", rs.getObject("Status"));
                row.put("Lifecycle", rs.getObject("Lifecycle"));
                row.put("Is_Public", rs.getObject("Is_Public"));
                row.put("Type", rs.getObject("Type"));
                row.put("Security_Classification", rs.getObject("Security_Classification"));
                row.put("Format_type", rs.getObject("Format_type"));
                row.put("Confidentiality_Rating", rs.getObject("Confidentiality_Rating"));
                row.put("Integrity_Rating", rs.getObject("Integrity_Rating"));
                row.put("Availability_Rating", rs.getObject("Availability_Rating"));
                row.put("KDE", rs.getObject("KDE"));
                row.put("CreatedBy_ID", rs.getObject("CreatedBy_ID"));
                row.put("Last_updated_userID", rs.getObject("Last_updated_userID"));
                row.put("Created_Datetime", rs.getTimestamp("Created_Datetime"));
                row.put("Last_Updated_Datetime", rs.getTimestamp("Last_Updated_Datetime"));
                // Get user names from JOIN (handle nulls and case sensitivity)
                String createdByName = null;
                String updatedByName = null;
                try {
                    // Try exact case first
                    createdByName = rs.getString("createdByName");
                } catch (SQLException e) {
                    try {
                        // Try alternative case
                        createdByName = rs.getString("CreatedByName");
                    } catch (SQLException e2) {
                        // Column might not exist, that's okay
                    }
                }
                try {
                    // Try exact case first
                    updatedByName = rs.getString("updatedByName");
                } catch (SQLException e) {
                    try {
                        // Try alternative case
                        updatedByName = rs.getString("UpdatedByName");
                    } catch (SQLException e2) {
                        // Column might not exist, that's okay
                    }
                }
                row.put("createdByName", createdByName);
                row.put("createdById", rs.getObject("CreatedBy_ID"));
                row.put("updatedByName", updatedByName);
                row.put("updatedById", rs.getObject("Last_updated_userID"));
                Timestamp created = rs.getTimestamp("Created_Datetime");
                Timestamp lastUpdated = rs.getTimestamp("Last_Updated_Datetime");
                row.put("created", created);
                row.put("lastUpdated", lastUpdated);
                // Debug output
                //system.out.println("GlossaryDAO.getById - createdByName: " + createdByName + ", updatedByName: " + updatedByName);
                //system.out.println("GlossaryDAO.getById - created: " + created + ", lastUpdated: " + lastUpdated);
                
                // Get segment info for this glossary (same pattern as DatasetDAO)
                Map<String, Object> segmentInfo = getGlossarySegment(id);
                row.put("segmentId", segmentInfo.get("segmentId"));
                row.put("segmentName", segmentInfo.get("segmentName"));
                
                return row;
            }
        }
    }

    public List<String> getGlossaryAliases(int glossaryId) throws SQLException {
        String sql = "SELECT Name FROM glossary_alias_names WHERE Glossary_id = ? ORDER BY Name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> aliases = new ArrayList<>();
                while (rs.next()) {
                    aliases.add(rs.getString("Name"));
                }
                return aliases;
            }
        }
    }

    public List<Map<String, Object>> listGlossary() throws SQLException {
        // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
        Set<Integer> excludedIds = getActiveNObjectIds();
        
        String excludeClause = "";
        if (!excludedIds.isEmpty()) {
            String idsList = excludedIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
            excludeClause = " AND g.ID NOT IN (" + idsList + ")";
        }
        
        String sql = "SELECT g.ID, g.Name, g.Description, g.Parent_ID, gt.Name as typeName " +
                     "FROM glossary g " +
                     "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                     "WHERE g.Deleted_datetime IS NULL " + excludeClause +
                     " ORDER BY g.ID";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("Name"));
                row.put("description", rs.getString("Description"));
                row.put("Parent_ID", rs.getObject("Parent_ID"));
                row.put("typeName", rs.getString("typeName"));
                results.add(row);
            }
            return results;
        }
    }
    
    /**
     * Get active nobject_id values (temporary cloned rows) for glossary facet.
     */
    private Set<Integer> getActiveNObjectIds() {
        try {
            FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
            Set<Integer> excludedIds = facetChangesDAO.getActiveNObjectIdsForFacet("glossary");
            System.out.println("[GlossaryDAO] Found " + excludedIds.size() + " active nobject_id values to exclude: " + excludedIds);
            return excludedIds;
        } catch (Exception e) {
            // Log but don't fail - if we can't get excluded IDs, just return empty set
            System.err.println("[GlossaryDAO] Error getting active nobject_id: " + e.getMessage());
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    /**
     * List glossaries filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
     * 
     * @param userId The user ID to filter for
     * @return List of accessible glossaries
     */
    public List<Map<String, Object>> listGlossaryBySegmentAccess(int userId) throws SQLException {
        // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
        Set<Integer> excludedIds = getActiveNObjectIds();
        
        // Get the segment filter clause
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Glossary", "g.ID");
        
        String excludeClause = "";
        if (!excludedIds.isEmpty()) {
            String idsList = excludedIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
            excludeClause = " AND g.ID NOT IN (" + idsList + ")";
        }
        
        String sql = "SELECT g.ID, g.Name, g.Description, g.Parent_ID, gt.Name as typeName " +
                     "FROM glossary g " +
                     "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                     "WHERE g.Deleted_datetime IS NULL AND " + segmentFilter + excludeClause +
                     " ORDER BY g.Name";
        
        System.out.println("📋 [GlossaryDAO] listGlossaryBySegmentAccess SQL for user " + userId + ": " + sql);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("Name"));
                row.put("description", rs.getString("Description"));
                row.put("Parent_ID", rs.getObject("Parent_ID"));
                row.put("parentId", rs.getObject("Parent_ID"));
                row.put("typeName", rs.getString("typeName"));
                results.add(row);
            }
            System.out.println("📋 [GlossaryDAO] Found " + results.size() + " accessible glossaries for user " + userId);
            return results;
        }
    }

    /**
     * List glossaries for Dataset create/edit "Glossary" dropdown with additional constraint:
     * - Only Enterprise glossaries (segment 1 OR unassigned) OR glossaries in the selected dataset segment
     * - Still respects user's selected segments (cube filter)
     */
    public List<Map<String, Object>> listGlossaryBySegmentAccessAndDatasetSegment(int userId, int datasetSegmentId) throws SQLException {
        // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
        Set<Integer> excludedIds = getActiveNObjectIds();
        
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Glossary", "g.ID");

        String allowedSegmentSql = buildEnterpriseOrSameSegmentConstraintSql(datasetSegmentId);
        
        String excludeClause = "";
        if (!excludedIds.isEmpty()) {
            String idsList = excludedIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
            excludeClause = " AND g.ID NOT IN (" + idsList + ")";
        }

        String sql = "SELECT g.ID, g.Name, g.Description, g.Parent_ID, gt.Name as typeName " +
                "FROM glossary g " +
                "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "WHERE g.Deleted_datetime IS NULL AND " + segmentFilter + " AND " + allowedSegmentSql + excludeClause +
                " ORDER BY g.Name";

        System.out.println("📋 [GlossaryDAO] listGlossaryBySegmentAccessAndDatasetSegment SQL for user " + userId + ", datasetSegmentId " + datasetSegmentId + ": " + sql);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            idx = bindEnterpriseOrSameSegmentConstraintParams(ps, idx, datasetSegmentId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("Name"));
                    row.put("description", rs.getString("Description"));
                    row.put("Parent_ID", rs.getObject("Parent_ID"));
                    row.put("parentId", rs.getObject("Parent_ID"));
                    row.put("typeName", rs.getString("typeName"));
                    results.add(row);
                }
                System.out.println("📋 [GlossaryDAO] listGlossaryBySegmentAccessAndDatasetSegment returned " + results.size() + " glossaries for user " + userId + ", datasetSegmentId " + datasetSegmentId);
                // Check if Glossary ID 1 is in results
                boolean hasGlossary1 = results.stream().anyMatch(r -> {
                    Object id = r.get("id");
                    return id != null && (id instanceof Integer ? ((Integer) id) == 1 : id.toString().equals("1"));
                });
                System.out.println("📋 [GlossaryDAO] Glossary ID 1 in results: " + hasGlossary1);
                return results;
            }
        }
    }

    /**
     * Same as listGlossaryBySegmentAccessAndDatasetSegment but without user cube filter (anonymous / fallback).
     */
    public List<Map<String, Object>> listGlossaryByDatasetSegment(int datasetSegmentId) throws SQLException {
        // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
        Set<Integer> excludedIds = getActiveNObjectIds();
        
        String allowedSegmentSql = buildEnterpriseOrSameSegmentConstraintSql(datasetSegmentId);
        
        String excludeClause = "";
        if (!excludedIds.isEmpty()) {
            String idsList = excludedIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
            excludeClause = " AND g.ID NOT IN (" + idsList + ")";
        }

        String sql = "SELECT g.ID, g.Name, g.Description, g.Parent_ID, gt.Name as typeName " +
                "FROM glossary g " +
                "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "WHERE g.Deleted_datetime IS NULL AND " + allowedSegmentSql + excludeClause +
                " ORDER BY g.Name";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            idx = bindEnterpriseOrSameSegmentConstraintParams(ps, idx, datasetSegmentId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("Name"));
                    row.put("description", rs.getString("Description"));
                    row.put("Parent_ID", rs.getObject("Parent_ID"));
                    row.put("typeName", rs.getString("typeName"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * SQL condition for "Enterprise OR selected segment" glossaries.
     * - Enterprise means segment 1 OR unassigned (no segment_x_resource row).
     * - If datasetSegmentId == 1, only enterprise/unassigned is allowed.
     */
    private String buildEnterpriseOrSameSegmentConstraintSql(int datasetSegmentId) {
        return "(" +
                "NOT EXISTS (" +
                "  SELECT 1 FROM segment_x_resource sxr " +
                "  JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                "  JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                "  WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR EXISTS (" +
                "  SELECT 1 FROM segment_x_resource sxr " +
                "  JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                "  JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                "  WHERE orr.Object_ID = g.ID AND sot.Type = 'Glossary' AND sxr.Deleted_At IS NULL " +
                "    AND sxr.Segment_ID IN (" + (datasetSegmentId == 1 ? "?" : "?, ?") + ")" +
                ")" +
                ")";
    }

    private int bindEnterpriseOrSameSegmentConstraintParams(PreparedStatement ps, int startIndex, int datasetSegmentId) throws SQLException {
        ps.setInt(startIndex++, 1);
        if (datasetSegmentId != 1) {
            ps.setInt(startIndex++, datasetSegmentId);
        }
        return startIndex;
    }

    public List<Map<String, Object>> getGlossaryHierarchy(int glossaryId) throws SQLException {
        System.out.println("[GlossaryDAO] getGlossaryHierarchy called for glossaryId: " + glossaryId);
        
        // Get complete hierarchy: parents (ancestors) + current + siblings + children (descendants) + siblings' children
        // First, verify the glossary exists and get its Parent_ID
        Integer currentParentId = null;
        boolean glossaryExists = false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Parent_ID FROM glossary WHERE ID = ? AND Deleted_datetime IS NULL")) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    glossaryExists = true;
                    currentParentId = rs.getObject("Parent_ID", Integer.class);
                    System.out.println("[GlossaryDAO] Glossary exists, Parent_ID: " + currentParentId);
                } else {
                    System.err.println("[GlossaryDAO] Glossary with ID " + glossaryId + " not found or deleted");
                    return new ArrayList<>();
                }
            }
        }
        
        if (!glossaryExists) {
            return new ArrayList<>();
        }
        
        String sql = "WITH RECURSIVE " +
                // Get current glossary's parent ID
                "current_parent AS (" +
                "    SELECT Parent_ID FROM glossary WHERE ID = ? AND Deleted_datetime IS NULL " +
                "), " +
                // Get all ancestors (parents up the hierarchy)
                "ancestors AS (" +
                "    SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, -1 as level, 'ancestor' as relation, " +
                "           g.Last_Updated_Datetime, " +
                "           (SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                "           (SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                "           (SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                "    FROM glossary g " +
                "    CROSS JOIN current_parent cp " +
                "    LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "    WHERE g.ID = cp.Parent_ID AND g.ID IS NOT NULL AND g.Deleted_datetime IS NULL " +
                "    UNION ALL " +
                "    SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, a.level - 1, 'ancestor', " +
                "           g.Last_Updated_Datetime, " +
                "           (SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                "           (SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                "           (SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                "    FROM glossary g " +
                "    INNER JOIN ancestors a ON g.ID = a.Parent_ID " +
                "    LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "    WHERE a.level > -10 AND g.Deleted_datetime IS NULL " + // Prevent infinite recursion
                "), " +
                // Get siblings (other glossaries with the same Parent_ID as current)
                // Siblings must share the same parent ID - if current has no parent, it has no siblings
                "siblings AS (" +
                "    SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, 0 as level, 'sibling' as relation, " +
                "           g.Last_Updated_Datetime, " +
                "           (SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                "           (SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                "           (SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                "    FROM glossary g " +
                "    CROSS JOIN current_parent cp " +
                "    LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "    WHERE cp.Parent_ID IS NOT NULL AND g.Parent_ID = cp.Parent_ID " +
                "    AND g.ID != ? AND g.Deleted_datetime IS NULL " +
                "), " +
                // Get all descendants of current glossary (children down the hierarchy)
                "descendants AS (" +
                "    SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, 1 as level, 'descendant' as relation, " +
                "           g.Last_Updated_Datetime, " +
                "           (SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                "           (SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                "           (SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                "    FROM glossary g " +
                "    LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "    WHERE g.Parent_ID = ? AND g.Deleted_datetime IS NULL " +
                "    UNION ALL " +
                "    SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, d.level + 1, 'descendant', " +
                "           g.Last_Updated_Datetime, " +
                "           (SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                "           (SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                "           (SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                "    FROM glossary g " +
                "    INNER JOIN descendants d ON g.Parent_ID = d.ID " +
                "    LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "    WHERE d.level < 10 AND g.Deleted_datetime IS NULL " + // Prevent infinite recursion
                "), " +
                // Get children of siblings (siblings' descendants)
                "sibling_children AS (" +
                "    SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, 1 as level, 'sibling_child' as relation, " +
                "           g.Last_Updated_Datetime, " +
                "           (SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                "           (SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                "           (SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                "    FROM glossary g " +
                "    LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "    WHERE g.Parent_ID IN (SELECT ID FROM siblings) AND g.Deleted_datetime IS NULL " +
                "    UNION ALL " +
                "    SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, sc.level + 1, 'sibling_child', " +
                "           g.Last_Updated_Datetime, " +
                "           (SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                "           (SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                "           (SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                "    FROM glossary g " +
                "    INNER JOIN sibling_children sc ON g.Parent_ID = sc.ID " +
                "    LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "    WHERE sc.level < 10 AND g.Deleted_datetime IS NULL " + // Prevent infinite recursion
                ") " +
                // Combine: ancestors + current + siblings + descendants + siblings' children
                "SELECT combined.ID, combined.Parent_ID, combined.Name, combined.Description, combined.typeName, combined.level, combined.relation, " +
                "       combined.Last_Updated_Datetime, combined.strategicSourceSystem, combined.dataItems, combined.dataAttributes, " +
                "       combined.strategicSourceIds " +
                "FROM (" +
                "    SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, 0 as level, 'current' as relation, " +
                "           g.Last_Updated_Datetime, " +
                "           (SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                "           (SELECT GROUP_CONCAT(DISTINCT CONCAT(s.id, ':', s.Name) ORDER BY s.Name SEPARATOR '|') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceIds, " +
                "           (SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                "           (SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                "    FROM glossary g " +
                "    LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "    WHERE g.ID = ? AND g.Deleted_datetime IS NULL " +
                "    UNION ALL " +
                "    SELECT a.ID, a.Parent_ID, a.Name, a.Description, a.typeName, a.level, a.relation, a.Last_Updated_Datetime, " +
                "           a.strategicSourceSystem, " +
                "           (SELECT GROUP_CONCAT(DISTINCT CONCAT(s.id, ':', s.Name) ORDER BY s.Name SEPARATOR '|') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system s ON s.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = a.ID AND s.id IS NOT NULL) as strategicSourceIds, " +
                "           a.dataItems, a.dataAttributes FROM ancestors a " +
                "    UNION ALL " +
                "    SELECT s.ID, s.Parent_ID, s.Name, s.Description, s.typeName, s.level, s.relation, s.Last_Updated_Datetime, " +
                "           s.strategicSourceSystem, " +
                "           (SELECT GROUP_CONCAT(DISTINCT CONCAT(sys.id, ':', sys.Name) ORDER BY sys.Name SEPARATOR '|') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system sys ON sys.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = s.ID AND sys.id IS NOT NULL) as strategicSourceIds, " +
                "           s.dataItems, s.dataAttributes FROM siblings s " +
                "    UNION ALL " +
                "    SELECT d.ID, d.Parent_ID, d.Name, d.Description, d.typeName, d.level, d.relation, d.Last_Updated_Datetime, " +
                "           d.strategicSourceSystem, " +
                "           (SELECT GROUP_CONCAT(DISTINCT CONCAT(sys.id, ':', sys.Name) ORDER BY sys.Name SEPARATOR '|') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system sys ON sys.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = d.ID AND sys.id IS NOT NULL) as strategicSourceIds, " +
                "           d.dataItems, d.dataAttributes FROM descendants d " +
                "    UNION ALL " +
                "    SELECT sc.ID, sc.Parent_ID, sc.Name, sc.Description, sc.typeName, sc.level, sc.relation, sc.Last_Updated_Datetime, " +
                "           sc.strategicSourceSystem, " +
                "           (SELECT GROUP_CONCAT(DISTINCT CONCAT(sys.id, ':', sys.Name) ORDER BY sys.Name SEPARATOR '|') " +
                "            FROM glossary_x_system gxs " +
                "            LEFT JOIN system sys ON sys.id = gxs.SystemID " +
                "            WHERE gxs.GlossaryID = sc.ID AND sys.id IS NOT NULL) as strategicSourceIds, " +
                "           sc.dataItems, sc.dataAttributes FROM sibling_children sc " +
                ") combined " +
                "ORDER BY level, relation, Name";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);  // For current_parent CTE (line 333)
            ps.setInt(2, glossaryId);  // For siblings query - exclude current (line 377)
            ps.setInt(3, glossaryId);  // For descendants query - get children (line 391)
            ps.setInt(4, glossaryId);  // For current glossary query (line 452)
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("parentId", rs.getObject("Parent_ID"));
                    row.put("name", rs.getString("Name"));
                    row.put("description", rs.getString("Description"));
                    row.put("typeName", rs.getString("typeName"));
                    row.put("level", rs.getInt("level"));
                    row.put("relation", rs.getString("relation"));
                    row.put("lastUpdated", rs.getTimestamp("Last_Updated_Datetime"));
                    row.put("strategicSourceSystem", rs.getString("strategicSourceSystem"));
                    String strategicSourceIds = rs.getString("strategicSourceIds");
                    row.put("strategicSourceIds", strategicSourceIds != null ? strategicSourceIds : ""); // Format: "id1:name1|id2:name2"
                    row.put("dataItems", rs.getInt("dataItems"));
                    row.put("dataAttributes", rs.getInt("dataAttributes"));
                    results.add(row);
                }
                // If no results, at least return the current glossary
                if (results.isEmpty()) {
                    System.out.println("[GlossaryDAO] Main query returned no results, using fallback for glossaryId: " + glossaryId);
                    // Fallback: get just the current glossary
                    String fallbackSql = "SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, 0 as level, 'current' as relation, " +
                            "g.Last_Updated_Datetime, " +
                            "(SELECT GROUP_CONCAT(DISTINCT CONCAT(s.id, ':', s.Name) ORDER BY s.Name SEPARATOR '|') " +
                            "FROM glossary_x_system gxs LEFT JOIN system s ON s.id = gxs.SystemID WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceIds, " +
                            "(SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                            "FROM glossary_x_system gxs LEFT JOIN system s ON s.id = gxs.SystemID WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                            "(SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                            "(SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                            "FROM glossary g LEFT JOIN glossary_type gt ON gt.ID = g.Type WHERE g.ID = ? AND g.Deleted_datetime IS NULL";
                    try (PreparedStatement fallbackPs = conn.prepareStatement(fallbackSql)) {
                        fallbackPs.setInt(1, glossaryId);
                        try (ResultSet fallbackRs = fallbackPs.executeQuery()) {
                            if (fallbackRs.next()) {
                                Map<String, Object> row = new HashMap<>();
                                row.put("id", fallbackRs.getInt("ID"));
                                row.put("parentId", fallbackRs.getObject("Parent_ID"));
                                row.put("name", fallbackRs.getString("Name"));
                                row.put("description", fallbackRs.getString("Description"));
                                row.put("typeName", fallbackRs.getString("typeName"));
                                row.put("level", fallbackRs.getInt("level"));
                                row.put("relation", fallbackRs.getString("relation"));
                                row.put("lastUpdated", fallbackRs.getTimestamp("Last_Updated_Datetime"));
                                row.put("strategicSourceSystem", fallbackRs.getString("strategicSourceSystem"));
                                String fallbackStrategicSourceIds = fallbackRs.getString("strategicSourceIds");
                                row.put("strategicSourceIds", fallbackStrategicSourceIds != null ? fallbackStrategicSourceIds : "");
                                row.put("dataItems", fallbackRs.getInt("dataItems"));
                                row.put("dataAttributes", fallbackRs.getInt("dataAttributes"));
                                results.add(row);
                                System.out.println("[GlossaryDAO] Fallback query succeeded, returned glossary: " + row.get("name"));
                            } else {
                                System.err.println("[GlossaryDAO] Fallback query also returned no results for glossaryId: " + glossaryId);
                            }
                        }
                    }
                }
                System.out.println("[GlossaryDAO] Returning " + results.size() + " hierarchy items for glossaryId: " + glossaryId);
                return results;
            }
        } catch (SQLException e) {
            // Log error and try fallback
            System.err.println("[GlossaryDAO] SQLException in getGlossaryHierarchy for glossaryId " + glossaryId + ": " + e.getMessage());
            e.printStackTrace();
            
            // Try simple fallback query
            try (Connection conn = DatabaseConnection.getConnection()) {
                String fallbackSql = "SELECT g.ID, g.Parent_ID, g.Name, g.Description, gt.Name as typeName, 0 as level, 'current' as relation, " +
                        "g.Last_Updated_Datetime, " +
                        "(SELECT GROUP_CONCAT(DISTINCT CONCAT(s.id, ':', s.Name) ORDER BY s.Name SEPARATOR '|') " +
                        "FROM glossary_x_system gxs LEFT JOIN system s ON s.id = gxs.SystemID WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceIds, " +
                        "(SELECT GROUP_CONCAT(DISTINCT s.Name ORDER BY s.Name SEPARATOR ', ') " +
                        "FROM glossary_x_system gxs LEFT JOIN system s ON s.id = gxs.SystemID WHERE gxs.GlossaryID = g.ID AND s.id IS NOT NULL) as strategicSourceSystem, " +
                        "(SELECT COUNT(DISTINCT d.ID) FROM dataset d WHERE d.glossary = g.ID) as dataItems, " +
                        "(SELECT COUNT(DISTINCT a.ID) FROM attribute a WHERE a.Glossary_ID = g.ID) as dataAttributes " +
                        "FROM glossary g LEFT JOIN glossary_type gt ON gt.ID = g.Type WHERE g.ID = ? AND g.Deleted_datetime IS NULL";
                try (PreparedStatement fallbackPs = conn.prepareStatement(fallbackSql)) {
                    fallbackPs.setInt(1, glossaryId);
                    try (ResultSet fallbackRs = fallbackPs.executeQuery()) {
                        List<Map<String, Object>> fallbackResults = new ArrayList<>();
                        if (fallbackRs.next()) {
                            Map<String, Object> row = new HashMap<>();
                            row.put("id", fallbackRs.getInt("ID"));
                            row.put("parentId", fallbackRs.getObject("Parent_ID"));
                            row.put("name", fallbackRs.getString("Name"));
                            row.put("description", fallbackRs.getString("Description"));
                            row.put("typeName", fallbackRs.getString("typeName"));
                            row.put("level", fallbackRs.getInt("level"));
                            row.put("relation", fallbackRs.getString("relation"));
                            row.put("lastUpdated", fallbackRs.getTimestamp("Last_Updated_Datetime"));
                            row.put("strategicSourceSystem", fallbackRs.getString("strategicSourceSystem"));
                            String fallbackStrategicSourceIds = fallbackRs.getString("strategicSourceIds");
                            row.put("strategicSourceIds", fallbackStrategicSourceIds != null ? fallbackStrategicSourceIds : "");
                            row.put("dataItems", fallbackRs.getInt("dataItems"));
                            row.put("dataAttributes", fallbackRs.getInt("dataAttributes"));
                            fallbackResults.add(row);
                            System.out.println("[GlossaryDAO] Exception fallback succeeded, returned glossary: " + row.get("name"));
                            return fallbackResults;
                        }
                    }
                }
            } catch (SQLException fallbackError) {
                System.err.println("[GlossaryDAO] Fallback query also failed: " + fallbackError.getMessage());
                fallbackError.printStackTrace();
            }
            // Return empty list if all queries fail
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getGlossaryStrategicSource(int glossaryId) throws SQLException {
        String sql = "SELECT " +
                "gxs.ID as id, " +
                "rt.PrimaryName as relationshipType, " +
                "s.Name as systemName, " +
                "d.PrimaryName as datasetName, " +
                "gxs.SystemID as systemId, " +
                "gxs.Strategic_DatasetID as datasetId, " +
                "gxs.Relation_TypeID as relationTypeId " +
                "FROM glossary_x_system gxs " +
                "LEFT JOIN glossary_x_system_relationtype rt ON rt.ID = gxs.Relation_TypeID " +
                "LEFT JOIN system s ON s.id = gxs.SystemID " +
                "LEFT JOIN dataset d ON d.ID = gxs.Strategic_DatasetID " +
                "WHERE gxs.GlossaryID = ? " +
                "ORDER BY rt.PrimaryName, s.Name, d.PrimaryName";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("relationshipType", rs.getString("relationshipType"));
                    row.put("systemName", rs.getString("systemName"));
                    row.put("datasetName", rs.getString("datasetName"));
                    row.put("systemId", rs.getObject("systemId"));
                    row.put("datasetId", rs.getObject("datasetId"));
                    row.put("relationTypeId", rs.getObject("relationTypeId"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public boolean updateStrategicSource(int glossaryId, List<Map<String, Object>> strategicSourceData) throws SQLException {
        String deleteSql = "DELETE FROM glossary_x_system WHERE GlossaryID = ?";
        String insertSql = "INSERT INTO glossary_x_system (GlossaryID, SystemID, Strategic_DatasetID, Relation_TypeID, " +
                          "LastUpdate_Datetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, NULL, 1)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Delete existing records
                try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                    deletePs.setInt(1, glossaryId);
                    deletePs.executeUpdate();
                }
                
                // Insert new records
                if (strategicSourceData != null && !strategicSourceData.isEmpty()) {
                    try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                        for (Map<String, Object> row : strategicSourceData) {
                            insertPs.setInt(1, glossaryId);
                            
                            Object systemId = row.get("systemId");
                            if (systemId != null && !systemId.toString().isEmpty()) {
                                insertPs.setInt(2, Integer.parseInt(systemId.toString()));
                            } else {
                                insertPs.setNull(2, Types.INTEGER);
                            }
                            
                            Object datasetId = row.get("datasetId");
                            if (datasetId != null && !datasetId.toString().isEmpty()) {
                                insertPs.setInt(3, Integer.parseInt(datasetId.toString()));
                            } else {
                                insertPs.setNull(3, Types.INTEGER);
                            }
                            
                            Object relationTypeId = row.get("relationTypeId");
                            if (relationTypeId != null && !relationTypeId.toString().isEmpty()) {
                                insertPs.setInt(4, Integer.parseInt(relationTypeId.toString()));
                            } else {
                                insertPs.setNull(4, Types.INTEGER);
                            }
                            
                            insertPs.addBatch();
                        }
                        insertPs.executeBatch();
                    }
                }
                
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean update(int id, String name, String description, String format, String ldm, 
                         String businessLogic, String examples, String refNumber, 
                         Integer formatType, Integer parentId, List<String> aliases,
                         Integer status, Integer lifecycle, Integer isPublic, Integer type,
                         Integer securityClassification, Integer kde, Integer confidentialityRating,
                         Integer integrityRating, Integer availabilityRating, Integer userId) throws SQLException {
        
        // Validate RefNumber uniqueness for update (exclude current ID)
        if (refNumber != null && !refNumber.trim().isEmpty()) {
            if (!isRefNumberUniqueForUpdate(refNumber, id)) {
                throw new SQLException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        // الخطوة 1: الحصول على البيانات القديمة قبل التحديث للمقارنة
        Map<String, Object> oldGlossary = null;
        try {
            oldGlossary = getById(id);
            if (oldGlossary == null) {
                System.err.println("❌ GlossaryDAO.update - Glossary not found with ID: " + id);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting old glossary data for audit: " + e.getMessage());
            // Continue with update even if we can't get old data for audit
        }
        
        String sql = "UPDATE glossary SET Name = ?, Description = ?, Format = ?, LDM = ?, " +
                     "Business_Logic = ?, Examples = ?, Ref_Number = ?, Format_type = ?, " +
                     "Parent_ID = ?, Status = ?, Lifecycle = ?, Is_Public = ?, Type = ?, " +
                     "Security_Classification = ?, KDE = ?, Confidentiality_Rating = ?, " +
                     "Integrity_Rating = ?, Availability_Rating = ?, Last_Updated_Datetime = NOW(), " +
                     "Last_updated_userID = ? WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setString(3, format);
                ps.setString(4, ldm);
                ps.setString(5, businessLogic);
                ps.setString(6, examples);
                ps.setString(7, refNumber);
                if (formatType == null) ps.setNull(8, Types.INTEGER); else ps.setInt(8, formatType);
                if (parentId == null) ps.setNull(9, Types.INTEGER); else ps.setInt(9, parentId);
                if (status == null) ps.setNull(10, Types.INTEGER); else ps.setInt(10, status);
                if (lifecycle == null) ps.setNull(11, Types.INTEGER); else ps.setInt(11, lifecycle);
                if (isPublic == null) ps.setNull(12, Types.INTEGER); else ps.setInt(12, isPublic);
                if (type == null) ps.setNull(13, Types.INTEGER); else ps.setInt(13, type);
                if (securityClassification == null) ps.setNull(14, Types.INTEGER); else ps.setInt(14, securityClassification);
                if (kde == null) ps.setNull(15, Types.INTEGER); else ps.setInt(15, kde);
                if (confidentialityRating == null) ps.setNull(16, Types.INTEGER); else ps.setInt(16, confidentialityRating);
                if (integrityRating == null) ps.setNull(17, Types.INTEGER); else ps.setInt(17, integrityRating);
                if (availabilityRating == null) ps.setNull(18, Types.INTEGER); else ps.setInt(18, availabilityRating);
                // Last_updated_userID
                if (userId == null) ps.setNull(19, Types.INTEGER); else ps.setInt(19, userId);
                // WHERE ID
                ps.setInt(20, id);
                
                int rowsAffected = ps.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Update aliases
                    updateAliases(conn, id, aliases);
                    conn.commit();
                    
                    // الخطوة 2: إنشاء audit records للتحديثات (فقط إذا كانت البيانات القديمة متاحة)
                    if (oldGlossary != null) {
                        try {
                            String userName = "System"; // Default fallback
                            if (userId != null) {
                                String fullName = getPersonFullName(userId);
                                if (fullName != null && !fullName.trim().isEmpty()) {
                                    userName = fullName;
                                } else {
                                    // إذا لم نجد الاسم، استخدم User ID كبديل أفضل من "System"
                                    userName = "User ID: " + userId;
                                }
                            }
                            
                            createGlossaryUpdateAuditRecords(id, oldGlossary, name, description, format, ldm,
                                businessLogic, examples, refNumber, formatType, parentId, status, lifecycle,
                                isPublic, type, securityClassification, kde, confidentialityRating,
                                integrityRating, availabilityRating, userName);
                            //system.out.println("✅ Glossary update audit records created for ID: " + id + " with author: " + userName);
                        } catch (Exception e) {
                            System.err.println("❌ Error creating glossary update audit records: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the update if audit fails
                        }

                        // الخطوة 3: إنشاء snapshot جديد في glossary_audit
                        try {
                            createGlossaryUpdateAuditSnapshot(id);
                            //system.out.println("✅ GlossaryDAO: glossary_audit update snapshot created for ID: " + id);
                        } catch (Exception e) {
                            System.err.println("❌ Error creating glossary_audit update snapshot: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the update if audit fails
                        }
                    }
                    
                    return true;
                }
                return false;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
    
    private void updateAliases(Connection conn, int glossaryId, List<String> aliases) throws SQLException {
        // Delete existing aliases
        String deleteSql = "DELETE FROM glossary_alias_names WHERE Glossary_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, glossaryId);
            ps.executeUpdate();
        }
        
        // Insert new aliases
        if (aliases != null && !aliases.isEmpty()) {
            String insertSql = "INSERT INTO glossary_alias_names (Glossary_id, Name, Last_updated_Datetime, Last_updated_UserID) VALUES (?, ?, NULL, 1)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (String alias : aliases) {
                    if (alias != null && !alias.trim().isEmpty()) {
                        ps.setInt(1, glossaryId);
                        ps.setString(2, alias.trim());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
        }
    }



public List<Map<String, Object>> getDirectStakeholdersForGlossary(int glossaryId) throws SQLException {
        String sql = """
            SELECT
                oxp.ID as object_x_ipid,
                oxp.ipid AS people_id,
                oxp.RoleID AS role_id,
                oxp.statusID AS status_id,
                oxp.isDelegateOF AS delegate_of_id,
                r.PrimaryName AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                ou.Name AS org_unit,
                CASE oxp.AcceptedID WHEN 1 THEN 'True' ELSE 'False' END AS role_accepted,
                CONCAT(delegate_p.First_Name, ' ', delegate_p.Last_Name) AS delegate_of,
                oxp.createdatetime AS date_accepted
            FROM glossary_x_objectxpeople gx
            JOIN object_x_people oxp ON gx.Object_x_ipid = oxp.ID
            JOIN object_role r ON oxp.RoleID = r.ID
            JOIN people p ON oxp.ipid = p.ID
            JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
            LEFT JOIN object_x_people d_oxp ON oxp.isDelegateOF = d_oxp.ID
            LEFT JOIN people delegate_p ON d_oxp.ipid = delegate_p.ID
            WHERE gx.GlossaryID = ?
            ORDER BY r.PrimaryName, p.Last_Name, p.First_Name
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("object_x_ipid", rs.getInt("object_x_ipid"));
                    row.put("peopleId", (Object) rs.getObject("people_id"));
                    row.put("roleId", (Object) rs.getObject("role_id"));
                    row.put("statusId", (Object) rs.getObject("status_id"));
                    row.put("delegateOfId", (Object) rs.getObject("delegate_of_id"));
                    row.put("role", rs.getString("role"));
                    row.put("name", rs.getString("name"));
                    row.put("orgUnit", rs.getString("org_unit"));
                    row.put("roleAccepted", rs.getString("role_accepted"));
                    row.put("delegateOf", rs.getString("delegate_of"));
                    row.put("dateAccepted", rs.getTimestamp("date_accepted"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> getRolesForGlossary(int glossaryId) throws SQLException {
        // Get module ID dynamically based on entity type
        int moduleId = ModuleResolver.getModuleId("glossary");
        String sql = """
            SELECT DISTINCT r.id, r.primaryname
            FROM object_role r
            WHERE r.module = ?
            ORDER BY r.primaryname
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("name", rs.getString("primaryname"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> getUsersByRole(int glossaryId, int roleId) throws SQLException {
        String sql = """
            SELECT DISTINCT p.ID, CONCAT(p.First_Name, ' ', p.Last_Name) AS name, p.Email
            FROM people p
            JOIN role_assignment ra ON FIND_IN_SET(p.ID, REPLACE(REPLACE(ra.users, '[', ''), ']', '')) > 0
            WHERE ra.objectroleid = ?
            ORDER BY p.Last_Name, p.First_Name
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("name"));
                    row.put("email", rs.getString("Email"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> getStatusesForGlossary(int glossaryId) throws SQLException {
        String sql = """
            SELECT ID, primaryname
            FROM status
            ORDER BY primaryname
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("primaryname"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public void saveStakeholdersChanges(int glossaryId, Map<String, Object> changes, int currentUserId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                @SuppressWarnings("unchecked") List<Map<String, Object>> inserts = (List<Map<String, Object>>) changes.getOrDefault("inserts", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked") List<Map<String, Object>> updates = (List<Map<String, Object>>) changes.getOrDefault("updates", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked") List<Map<String, Object>> deletes = (List<Map<String, Object>>) changes.getOrDefault("deletes", java.util.Collections.emptyList());

                for (Map<String, Object> row : inserts) {
                    validateRequired(row);
                    int oxpId = createObjectXPeople(convertRow(row), currentUserId);
                    linkStakeholderToGlossary(conn, glossaryId, oxpId, currentUserId);
                }

                for (Map<String, Object> row : updates) {
                    validateRequired(row);
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null) throw new IllegalArgumentException("Missing objectXPeopleId in update row");
                    updateObjectXPeople(conn, oxpId, convertRow(row));
                }

                for (Map<String, Object> row : deletes) {
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null) throw new IllegalArgumentException("Missing objectXPeopleId in delete row");
                    unlinkStakeholderFromGlossary(conn, glossaryId, oxpId);
                    if (!hasAnyOtherLink(conn, oxpId)) {
                        deleteObjectXPeople(conn, oxpId);
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException(e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private Map<String, Object> convertRow(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>();
        m.put("roleId", getInt(row.get("roleId")));
        m.put("userId", getInt(row.get("ipid")));
        m.put("statusId", normalizeAcceptedOrStatus(row.get("accepted"), row.get("statusId")));
        m.put("delegateOfId", getInt(row.get("delegateIpId")));
        return m;
    }

    private Integer normalizeAcceptedOrStatus(Object accepted, Object statusId) {
        Integer sid = getInt(statusId);
        Integer acc = getInt(accepted);
        if (acc != null) return acc == 1 ? 1 : 0;
        return sid;
    }

    private void validateRequired(Map<String, Object> row) {
        if (getInt(row.get("roleId")) == null) throw new IllegalArgumentException("Role is required");
        if (getInt(row.get("ipid")) == null) throw new IllegalArgumentException("Name is required");
    }

    private Integer getInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    /**
     * Create object_x_people record for stakeholder with existing connection
     * Always creates a NEW record (no reuse)
     */
    public int createObjectXPeople(Connection conn, Map<String, Object> stakeholder, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
            VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setInt(1, (Integer) stakeholder.get("userId")); // ipid
            ps.setInt(2, (Integer) stakeholder.get("roleId")); // RoleID
            ps.setInt(3, currentUserId); // lastupdateuser_id
            
            ps.executeUpdate();
            
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    private int createObjectXPeople(Map<String, Object> stakeholder, int currentUserId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return createObjectXPeople(conn, stakeholder, currentUserId);
        }
    }
    
    private void updateObjectXPeople(Connection conn, int objectXPeopleId, Map<String, Object> stakeholder) throws SQLException {
        String sql = """
            UPDATE object_x_people 
            SET RoleID = ?, ipid = ?, AcceptedID = 2, statusID = ?, isDelegateOF = ?, lastupdatedatetime = NOW()
            WHERE ID = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, (Integer) stakeholder.get("roleId"));
            ps.setInt(2, (Integer) stakeholder.get("userId"));
            
            Integer statusId = (Integer) stakeholder.get("statusId");
            if (statusId != null) {
                ps.setInt(3, statusId);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            
            // Handle delegateOf
            Integer delegateOfId = (Integer) stakeholder.get("delegateOfId");
            if (delegateOfId != null) {
                ps.setInt(4, delegateOfId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            
            ps.setInt(5, objectXPeopleId);
            
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No rows updated for object_x_people ID: " + objectXPeopleId);
            }
        }
    }
    
    private void deleteObjectXPeople(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = "DELETE FROM object_x_people WHERE ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No rows deleted for object_x_people ID: " + objectXPeopleId);
            }
        }
    }
    
    /**
     * Link stakeholder to glossary via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToGlossary(Connection conn, int glossaryId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO glossary_x_objectxpeople (GlossaryID, Object_x_ipid, Last_UpdateUser_ID, CreateDatetime)
            VALUES (?, ?, ?, NOW())
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            ps.executeUpdate();
        }
    }
    
    private void unlinkStakeholderFromGlossary(Connection conn, int glossaryId, int objectXPeopleId) throws SQLException {
        String sql = "DELETE FROM glossary_x_objectxpeople WHERE GlossaryID = ? AND Object_x_ipid = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.setInt(2, objectXPeopleId);
            
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No rows deleted from glossary_x_objectxpeople for GlossaryID: " + glossaryId + ", Object_x_ipid: " + objectXPeopleId);
            }
        }
    }

    private boolean hasAnyOtherLink(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = "SELECT ("
                + " (SELECT COUNT(*) FROM system_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM dataset_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM interface_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM glossary_x_objectxpeople WHERE Object_x_ipid=?)"
                + ") AS cnt";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, objectXPeopleId);
            ps.setInt(4, objectXPeopleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int glossaryId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, glossaryId);        // id
        auditStmt.setString(2, object);         // object
        auditStmt.setString(3, event);          // event
        auditStmt.setString(4, updateType);     // updateType
        auditStmt.setString(5, field);          // field
        auditStmt.setString(6, value);          // to
        auditStmt.setString(7, userName);       // author
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit records للـ glossary الجديد
     * يتم استدعاء هذا method بعد إنشاء الـ glossary بنجاح
     */
    public void createGlossaryAuditRecords(int glossaryId, String userName) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            createGlossaryAuditRecords(conn, glossaryId, userName);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
            conn.close();
        }
    }

    /**
     * Same as {@link #createGlossaryAuditRecords(int, String)} but uses the caller's connection
     * (same transaction). Required for bulk upload where the glossary row is not yet committed.
     */
    public void createGlossaryAuditRecords(Connection conn, int glossaryId, String userName) throws SQLException {
        String glossaryDataSql = "SELECT * FROM glossary WHERE ID = ?";
        String auditSql = """
            INSERT INTO glossary_audit_history (id, object, event, updateType, field, `from`, `to`, author)
            VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
        try (PreparedStatement glossaryStmt = conn.prepareStatement(glossaryDataSql)) {
            glossaryStmt.setInt(1, glossaryId);
            try (ResultSet glossaryRs = glossaryStmt.executeQuery()) {
                if (!glossaryRs.next()) {
                    throw new SQLException("Glossary not found with ID: " + glossaryId);
                }
                AuditHistoryWriter.logCreatedBy(conn, "glossary_audit_history", glossaryId, "Glossary", userName);
                try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                // Name
                String name = glossaryRs.getString("Name");
                if (name != null && !name.trim().isEmpty()) {
                    createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Name", name,
                            userName);
                }

                // Reference Number
                String refNumber = glossaryRs.getString("Ref_Number");
                if (refNumber != null && !refNumber.trim().isEmpty()) {
                    createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added",
                            "Reference Number", refNumber, userName);
                }

                // Description
                String description = glossaryRs.getString("Description");
                if (description != null && !description.trim().isEmpty()) {
                    createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Description",
                            description, userName);
                }

                // Format
                String format = glossaryRs.getString("Format");
                if (format != null && !format.trim().isEmpty()) {
                    createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Format", format,
                            userName);
                }

                // LDM
                String ldm = glossaryRs.getString("LDM");
                if (ldm != null && !ldm.trim().isEmpty()) {
                    createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "LDM", ldm,
                            userName);
                }

                // Business Logic
                String businessLogic = glossaryRs.getString("Business_Logic");
                if (businessLogic != null && !businessLogic.trim().isEmpty()) {
                    createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Business Logic",
                            businessLogic, userName);
                }

                // Examples
                String examples = glossaryRs.getString("Examples");
                if (examples != null && !examples.trim().isEmpty()) {
                    createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Examples",
                            examples, userName);
                }

                // Parent Glossary
                Integer parentId = glossaryRs.getObject("Parent_ID", Integer.class);
                if (parentId != null) {
                    String parentName = getGlossaryName(parentId);
                    if (parentName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added",
                                "Parent Glossary", parentName, userName);
                    }
                }

                // Status
                Integer statusId = glossaryRs.getObject("Status", Integer.class);
                if (statusId != null) {
                    String statusName = getStatusPrimaryName(statusId);
                    if (statusName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Status Change",
                                "Status", statusName, userName);
                    }
                }

                // Lifecycle
                Integer lifecycleId = glossaryRs.getObject("Lifecycle", Integer.class);
                if (lifecycleId != null) {
                    String lifecycleName = getGlossaryLifecycleName(lifecycleId);
                    if (lifecycleName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Lifecycle",
                                lifecycleName, userName);
                    }
                }

                // Type
                Integer typeId = glossaryRs.getObject("Type", Integer.class);
                if (typeId != null) {
                    String typeName = getGlossaryTypeName(typeId);
                    if (typeName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Type",
                                typeName, userName);
                    }
                }

                // Format Type
                Integer formatTypeId = glossaryRs.getObject("Format_type", Integer.class);
                if (formatTypeId != null) {
                    String formatTypeName = getGlossaryFormatTypeName(formatTypeId);
                    if (formatTypeName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Format Type",
                                formatTypeName, userName);
                    }
                }

                // Security Classification
                Integer securityClassificationId = glossaryRs.getObject("Security_Classification", Integer.class);
                if (securityClassificationId != null) {
                    String securityClassificationName = getSecurityClassificationName(securityClassificationId);
                    if (securityClassificationName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added",
                                "Security Classification", securityClassificationName, userName);
                    }
                }

                // Is Public
                Integer isPublicId = glossaryRs.getObject("Is_Public", Integer.class);
                if (isPublicId != null) {
                    String isPublicName = getViewingName(isPublicId);
                    if (isPublicName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "Is Public",
                                isPublicName, userName);
                    }
                }

                // KDE
                Integer kdeId = glossaryRs.getObject("KDE", Integer.class);
                if (kdeId != null) {
                    String kdeName = getKdeTypeName(kdeId);
                    if (kdeName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added", "KDE",
                                kdeName, userName);
                    }
                }

                // Confidentiality Rating
                Integer confidentialityRatingId = glossaryRs.getObject("Confidentiality_Rating", Integer.class);
                if (confidentialityRatingId != null) {
                    String confidentialityRatingName = getCiaRatingName(confidentialityRatingId);
                    if (confidentialityRatingName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added",
                                "Confidentiality Rating", confidentialityRatingName, userName);
                    }
                }

                // Integrity Rating
                Integer integrityRatingId = glossaryRs.getObject("Integrity_Rating", Integer.class);
                if (integrityRatingId != null) {
                    String integrityRatingName = getCiaRatingName(integrityRatingId);
                    if (integrityRatingName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added",
                                "Integrity Rating", integrityRatingName, userName);
                    }
                }

                // Availability Rating
                Integer availabilityRatingId = glossaryRs.getObject("Availability_Rating", Integer.class);
                if (availabilityRatingId != null) {
                    String availabilityRatingName = getCiaRatingName(availabilityRatingId);
                    if (availabilityRatingName != null) {
                        createNewAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", "Added",
                                "Availability Rating", availabilityRatingName, userName);
                    }
                }

                // Created By is written as the first row via AuditHistoryWriter.logCreatedBy.
                }
            }
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إضافته للـ glossary
     * يتم استدعاء هذا method عند إضافة stakeholder جديد
     */
    public void createStakeholderAuditRecords(int glossaryId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            createStakeholderAuditRecords(conn, glossaryId, userName, userFullName, roleId);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
            conn.close();
        }
    }

    /**
     * Same as {@link #createStakeholderAuditRecords(int, String, String, int)} using the caller's connection
     * so stakeholder link rows are visible before commit.
     */
    public void createStakeholderAuditRecords(Connection conn, int glossaryId, String userName, String userFullName,
            int roleId) throws SQLException {
        String auditSql = """
            INSERT INTO glossary_audit_history (id, object, event, updateType, field, `from`, `to`, author)
            VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
        try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
            Integer actualRoleId = getStakeholderRoleId(conn, glossaryId);
            if (actualRoleId == null) {
                actualRoleId = roleId;
            }

            String roleName = getRoleName(actualRoleId);
            if (roleName == null)
                roleName = "Glossary Owner";

            createNewAuditRecord(conn, auditStmt, glossaryId, "Stakeholder", "link", "Added", "Role", roleName,
                    userName);

            Integer statusId = getStakeholderStatusId(conn, glossaryId);
            String statusName = "Active";
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null)
                    statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, glossaryId, "Stakeholder", "link", "Added", "Role Status", statusName,
                    userName);

            createNewAuditRecord(conn, auditStmt, glossaryId, "Stakeholder", "link", "Added", "Name", userFullName,
                    userName);
        }
    }

    /**
     * إنشاء سجل في جدول glossary_audit بعد إنشاء الـ glossary
     * يتم استدعاء هذا method بعد إنشاء الـ glossary بنجاح
     */
    public void createGlossaryAuditRecord(int glossaryId) throws SQLException {
        String sql = """
            INSERT INTO glossary_audit (
                ID, Parent_ID, Is_Public, Status, Lifecycle, Format_type, KDE,
                Security_Classification, Type, Confidentiality_Rating, Integrity_Rating,
                Availability_Rating, Name, Description, Ref_Number, Examples,
                Business_Logic, Format, Created_Datetime, Last_Updated_Datetime,
                LDM, Last_updated_userID, CreatedBy_ID, Rev_Type
            )
            SELECT 
                ID, Parent_ID, Is_Public, Status, Lifecycle, Format_type, KDE,
                Security_Classification, Type, Confidentiality_Rating, Integrity_Rating,
                Availability_Rating, Name, Description, Ref_Number, Examples,
                Business_Logic, Format, Created_Datetime, Last_Updated_Datetime,
                LDM, Last_updated_userID, CreatedBy_ID, 'Added'
            FROM glossary 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.executeUpdate();
        }
    }

    private String getRoleName(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        return null;
    }
    
    private Integer getStakeholderRoleId(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                "JOIN glossary_x_objectxpeople gxo ON gxo.Object_x_ipid = oxp.ID " +
                "WHERE gxo.GlossaryID = ? " +
                "ORDER BY gxo.ID DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("roleID");
            }
        }
        return null;
    }

    private Integer getStakeholderStatusId(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                "JOIN glossary_x_objectxpeople gxo ON gxo.Object_x_ipid = oxp.ID " +
                "WHERE gxo.GlossaryID = ? " +
                "ORDER BY gxo.ID DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("statusID");
            }
        }
        return null;
    }
    
    // Get status name from object_x_ip_status by statusID
    private String getStatusNameById(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }


    // Helper methods للحصول على الأسماء
    private String getGlossaryName(int glossaryId) throws SQLException {
        String sql = "SELECT Name FROM glossary WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM status WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getGlossaryLifecycleName(int lifecycleId) throws SQLException {
        String sql = "SELECT Name FROM glossary_lifecycle WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getGlossaryTypeName(int typeId) throws SQLException {
        String sql = "SELECT Name FROM glossary_type WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getGlossaryFormatTypeName(int formatTypeId) throws SQLException {
        String sql = "SELECT Name FROM glossary_format_type WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, formatTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getSecurityClassificationName(int securityClassificationId) throws SQLException {
        String sql = "SELECT Name FROM security_classification WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, securityClassificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getViewingName(int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getPersonFullName(int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

    /**
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int glossaryId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, glossaryId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue);
        auditStmt.setString(7, toValue);
        auditStmt.setString(8, userName);
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Helper method للمقارنة الآمنة بين القيم
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    /**
     * Helper method للحصول على اسم الـ CIA Rating
     */
    private String getCiaRatingName(int ratingId) throws SQLException {
        String sql = "SELECT name FROM cia_rating WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ratingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        }
        return null;
    }

    /**
     * Helper method للحصول على اسم الـ KDE Type
     */
    private String getKdeTypeName(int kdeId) throws SQLException {
        String sql = "SELECT Name FROM glossary_kde_type WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, kdeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    /**
     * إنشاء audit records للـ glossary بعد التحديث
     * يتم تسجيل فقط الحقول التي تغيرت فعلياً
     */
    public void createGlossaryUpdateAuditRecords(int glossaryId, Map<String, Object> oldGlossary, 
            String newName, String newDescription, String newFormat, String newLdm, 
            String newBusinessLogic, String newExamples, String newRefNumber, 
            Integer newFormatType, Integer newParentId, Integer newStatus, Integer newLifecycle, 
            Integer newIsPublic, Integer newType, Integer newSecurityClassification, Integer newKde,
            Integer newConfidentialityRating, Integer newIntegrityRating, Integer newAvailabilityRating,
            String userName) throws SQLException {
        
        //system.out.println("🔍 GlossaryDAO.createGlossaryUpdateAuditRecords - START for ID: " + glossaryId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO glossary_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Name
            if (!isEqual(oldGlossary.get("name"), newName)) {
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Name", (String) oldGlossary.get("name"), newName, userName);
            }
            
            // Description
            if (!isEqual(oldGlossary.get("description"), newDescription)) {
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Description", (String) oldGlossary.get("description"), newDescription, userName);
            }
            
            // Format
            if (!isEqual(oldGlossary.get("format"), newFormat)) {
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Format", (String) oldGlossary.get("format"), newFormat, userName);
            }
            
            // LDM
            if (!isEqual(oldGlossary.get("ldm"), newLdm)) {
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "LDM", (String) oldGlossary.get("ldm"), newLdm, userName);
            }
            
            // Business Logic
            if (!isEqual(oldGlossary.get("businessLogic"), newBusinessLogic)) {
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Business Logic", (String) oldGlossary.get("businessLogic"), newBusinessLogic, userName);
            }
            
            // Examples
            if (!isEqual(oldGlossary.get("examples"), newExamples)) {
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Examples", (String) oldGlossary.get("examples"), newExamples, userName);
            }
            
            // Reference Number
            if (!isEqual(oldGlossary.get("ref"), newRefNumber)) {
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Reference Number", (String) oldGlossary.get("ref"), newRefNumber, userName);
            }
            
            // Format Type
            if (!isEqual(oldGlossary.get("Format_type"), newFormatType)) {
                String oldFormatTypeName = oldGlossary.get("Format_type") != null ? 
                    getGlossaryFormatTypeName((Integer) oldGlossary.get("Format_type")) : null;
                String newFormatTypeName = newFormatType != null ? getGlossaryFormatTypeName(newFormatType) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Format Type", oldFormatTypeName, newFormatTypeName, userName);
            }
            
            // Parent Glossary
            if (!isEqual(oldGlossary.get("Parent_ID"), newParentId)) {
                String oldParentName = oldGlossary.get("Parent_ID") != null ? 
                    getGlossaryName((Integer) oldGlossary.get("Parent_ID")) : null;
                String newParentName = newParentId != null ? getGlossaryName(newParentId) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Parent Glossary", oldParentName, newParentName, userName);
            }
            
            // Status (يستخدم "Status Change" كـ updateType)
            if (!isEqual(oldGlossary.get("Status"), newStatus)) {
                String oldStatusName = oldGlossary.get("Status") != null ? 
                    getStatusPrimaryName((Integer) oldGlossary.get("Status")) : null;
                String newStatusName = newStatus != null ? getStatusPrimaryName(newStatus) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Lifecycle
            if (!isEqual(oldGlossary.get("Lifecycle"), newLifecycle)) {
                String oldLifecycleName = oldGlossary.get("Lifecycle") != null ? 
                    getGlossaryLifecycleName((Integer) oldGlossary.get("Lifecycle")) : null;
                String newLifecycleName = newLifecycle != null ? getGlossaryLifecycleName(newLifecycle) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Lifecycle", oldLifecycleName, newLifecycleName, userName);
            }
            
            // Is Public
            if (!isEqual(oldGlossary.get("Is_Public"), newIsPublic)) {
                String oldIsPublicName = oldGlossary.get("Is_Public") != null ? 
                    getViewingName((Integer) oldGlossary.get("Is_Public")) : null;
                String newIsPublicName = newIsPublic != null ? getViewingName(newIsPublic) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Is Public", oldIsPublicName, newIsPublicName, userName);
            }
            
            // Type
            if (!isEqual(oldGlossary.get("Type"), newType)) {
                String oldTypeName = oldGlossary.get("Type") != null ? 
                    getGlossaryTypeName((Integer) oldGlossary.get("Type")) : null;
                String newTypeName = newType != null ? getGlossaryTypeName(newType) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Type", oldTypeName, newTypeName, userName);
            }
            
            // Security Classification
            if (!isEqual(oldGlossary.get("Security_Classification"), newSecurityClassification)) {
                String oldSecurityName = oldGlossary.get("Security_Classification") != null ? 
                    getSecurityClassificationName((Integer) oldGlossary.get("Security_Classification")) : null;
                String newSecurityName = newSecurityClassification != null ? 
                    getSecurityClassificationName(newSecurityClassification) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Security Classification", oldSecurityName, newSecurityName, userName);
            }
            
            // KDE
            if (!isEqual(oldGlossary.get("KDE"), newKde)) {
                String oldKdeName = oldGlossary.get("KDE") != null ? 
                    getKdeTypeName((Integer) oldGlossary.get("KDE")) : null;
                String newKdeName = newKde != null ? getKdeTypeName(newKde) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "KDE", oldKdeName, newKdeName, userName);
            }
            
            // Confidentiality Rating
            if (!isEqual(oldGlossary.get("Confidentiality_Rating"), newConfidentialityRating)) {
                String oldConfName = oldGlossary.get("Confidentiality_Rating") != null ? 
                    getCiaRatingName((Integer) oldGlossary.get("Confidentiality_Rating")) : null;
                String newConfName = newConfidentialityRating != null ? 
                    getCiaRatingName(newConfidentialityRating) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Confidentiality Rating", oldConfName, newConfName, userName);
            }
            
            // Integrity Rating
            if (!isEqual(oldGlossary.get("Integrity_Rating"), newIntegrityRating)) {
                String oldIntegName = oldGlossary.get("Integrity_Rating") != null ? 
                    getCiaRatingName((Integer) oldGlossary.get("Integrity_Rating")) : null;
                String newIntegName = newIntegrityRating != null ? 
                    getCiaRatingName(newIntegrityRating) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Integrity Rating", oldIntegName, newIntegName, userName);
            }
            
            // Availability Rating
            if (!isEqual(oldGlossary.get("Availability_Rating"), newAvailabilityRating)) {
                String oldAvailName = oldGlossary.get("Availability_Rating") != null ? 
                    getCiaRatingName((Integer) oldGlossary.get("Availability_Rating")) : null;
                String newAvailName = newAvailabilityRating != null ? 
                    getCiaRatingName(newAvailabilityRating) : null;
                createUpdateAuditRecord(conn, auditStmt, glossaryId, "Glossary", "Details", 
                    "Updated", "Availability Rating", oldAvailName, newAvailName, userName);
            }
            
            conn.commit();
            //system.out.println("✅ GlossaryDAO.createGlossaryUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ GlossaryDAO.createGlossaryUpdateAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * إنشاء snapshot جديد في جدول glossary_audit بعد التحديث
     */
    public void createGlossaryUpdateAuditSnapshot(int glossaryId) throws SQLException {
        //system.out.println("🔍 GlossaryDAO.createGlossaryUpdateAuditSnapshot - Creating update snapshot for ID: " + glossaryId);
        String sql = """
            INSERT INTO glossary_audit (
                ID, Parent_ID, Is_Public, Status, Lifecycle, Format_type, KDE,
                Security_Classification, Type, Confidentiality_Rating, Integrity_Rating,
                Availability_Rating, Name, Description, Ref_Number, Examples,
                Business_Logic, Format, Created_Datetime, Last_Updated_Datetime,
                LDM, Last_updated_userID, CreatedBy_ID, Rev_Type
            )
            SELECT 
                ID, Parent_ID, Is_Public, Status, Lifecycle, Format_type, KDE,
                Security_Classification, Type, Confidentiality_Rating, Integrity_Rating,
                Availability_Rating, Name, Description, Ref_Number, Examples,
                Business_Logic, Format, Created_Datetime, Last_Updated_Datetime,
                LDM, Last_updated_userID, CreatedBy_ID, 'Updated'
            FROM glossary 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.executeUpdate();
            //system.out.println("✅ Update snapshot created, rows affected: " + rows);
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * حذف Glossary مع تسجيل audit records
     */
    public boolean deleteGlossaryWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE glossary SET Deleted_datetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO glossary_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Glossary");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Glossary");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في glossary_audit
                String snapshotSql = """
                    INSERT INTO glossary_audit (
                        ID, Parent_ID, Is_Public, Status, Lifecycle, Format_type, KDE,
                        Security_Classification, Type, Confidentiality_Rating, Integrity_Rating,
                        Availability_Rating, Name, Description, Ref_Number, Examples,
                        Business_Logic, Format, Created_Datetime, Last_Updated_Datetime,
                        LDM, Last_updated_userID, CreatedBy_ID, Rev_Type
                    )
                    SELECT 
                        ID, Parent_ID, Is_Public, Status, Lifecycle, Format_type, KDE,
                        Security_Classification, Type, Confidentiality_Rating, Integrity_Rating,
                        Availability_Rating, Name, Description, Ref_Number, Examples,
                        Business_Logic, Format, Created_Datetime, Last_Updated_Datetime,
                        LDM, Last_updated_userID, CreatedBy_ID, 'Deleted'
                    FROM glossary 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Glossary deleted with audit for ID: " + id);
            }
            
            conn.commit();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error during rollback: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            try {
                if (deleteStmt != null) deleteStmt.close();
                if (auditStmt != null) auditStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing statement: " + e.getMessage());
            }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("❌ Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Get segment info for a glossary (same pattern as DatasetDAO.getDatasetSegment)
     * @param glossaryId The glossary ID
     * @return Map with segmentId and segmentName (null/"Not Specified" if not assigned)
     */
    private Map<String, Object> getGlossarySegment(int glossaryId) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        result.put("segmentId", null); // null if not assigned - show "Not Specified"
        result.put("segmentName", "Not Specified");
        
        String sql = """
            SELECT s.ID AS segment_id, s.Name AS segment_name
            FROM segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            JOIN segment s ON sxr.Segment_ID = s.ID
            WHERE orr.Object_ID = ?
            AND sot.Type = 'Glossary'
            AND sxr.Deleted_At IS NULL
            AND s.Deleted_At IS NULL
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("segmentId", rs.getInt("segment_id"));
                    result.put("segmentName", rs.getString("segment_name"));
                }
            }
        }
        
        return result;
    }

    /**
     * Get glossary by Ref_Number excluding a specific ID (for update validation)
     * @param refNumber Reference number to search for
     * @param excludeId ID to exclude from search
     * @return Glossary ID if found, null otherwise
     * @deprecated Use RefNumberValidator.isRefNumberUniqueForUpdate() instead for consistent validation
     */
    @Deprecated
    public Integer getGlossaryByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        String sql = "SELECT ID FROM glossary WHERE LOWER(Ref_Number) = LOWER(?) AND ID != ? AND Deleted_datetime IS NULL LIMIT 1";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, refNumber.trim());
            pstmt.setInt(2, excludeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        return null;
    }

    /**
     * Check if Ref_Number is unique for update (excluding current ID).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     * Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
     * @param refNumber Reference number to check
     * @param excludeId ID to exclude from check
     * @return true if unique, false if duplicate exists
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Glossary", refNumber, excludeId);
    }
}



