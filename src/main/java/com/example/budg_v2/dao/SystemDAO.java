package com.example.budg_v2.dao;

import com.example.budg_v2.audit.AuditHistoryWriter;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ModuleResolver;

import java.sql.*;
import java.util.*;

public class SystemDAO {

    public List<Map<String, Object>> listSystems() throws SQLException {
        return listSystemsForGuest();
    }

    /**
     * List systems for guest users (public, Enterprise only, not deleted)
     */
    public List<Map<String, Object>> listSystemsForGuest() throws SQLException {
        String guestFilter = com.example.budg_v2.service.SegmentAccessService.buildGuestFilterClause("System", "s", "s.id");
        Set<Integer> excludedIds = getActiveNObjectIds();
        String excludeClause = "";
        if (!excludedIds.isEmpty()) {
            String idsList = excludedIds.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            excludeClause = " AND s.id NOT IN (" + idsList + ")";
        }
        String sql;
        if (guestFilter != null) {
            sql = "SELECT s.id, s.Name, s.Long_Name, s.Description, s.parent_id, s.Type, st.Name as typeName " +
                    "FROM system s " +
                    "LEFT JOIN system_type st ON s.Type = st.id " +
                    "WHERE " + guestFilter + excludeClause + " ORDER BY s.id";
        } else {
            sql = "SELECT s.id, s.Name, s.Long_Name, s.Description, s.parent_id, s.Type, st.Name as typeName " +
                    "FROM system s " +
                    "LEFT JOIN system_type st ON s.Type = st.id " +
                    "WHERE s.Deleted_datetime IS NULL " + excludeClause + " ORDER BY s.id";
        }
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("Name"));
                row.put("longName", rs.getString("Long_Name"));
                row.put("description", rs.getString("Description"));
                row.put("parent_id", rs.getObject("parent_id"));
                row.put("type", rs.getObject("Type"));
                row.put("typeName", rs.getString("typeName"));
                results.add(row);
            }
            return results;
        }
    }

    /**
     * Returns true if any system other than excluded ids has the given AssetID.
     */
    public boolean existsSystemWithAssetIdExcluding(String assetId, int excludeId, Integer excludeId2) throws SQLException {
        if (assetId == null || assetId.isBlank()) {
            return false;
        }
        String trimmed = assetId.trim();
        if (excludeId2 != null) {
            String sql = "SELECT 1 FROM system WHERE AssetID = ? AND id NOT IN (?, ?) AND Deleted_datetime IS NULL LIMIT 1";
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trimmed);
                ps.setInt(2, excludeId);
                ps.setInt(3, excludeId2);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
        String sql = "SELECT 1 FROM system WHERE AssetID = ? AND id != ? AND Deleted_datetime IS NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trimmed);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Get active nobject_id values (temporary cloned rows) for system facet.
     */
    private Set<Integer> getActiveNObjectIds() {
        try {
            FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
            return facetChangesDAO.getActiveNObjectIdsForFacet("system");
        } catch (Exception e) {
            // Log but don't fail - if we can't get excluded IDs, just return empty set
            System.err.println("[SystemDAO] Error getting active nobject_id: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * List systems filtered by user's SELECTED segments (cube filter).
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they
     * have selected in the cube.
     * 
     * @param userId The user ID to filter for
     * @return List of systems in user's selected segments
     */
    public List<Map<String, Object>> listSystemsBySegmentAccess(int userId) throws SQLException {
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "System", "s.id");

        String sql = "SELECT s.id, s.Name, s.Long_Name, s.Description, s.parent_id, s.Type, st.Name as typeName " +
                "FROM system s " +
                "LEFT JOIN system_type st ON s.Type = st.id " +
                "WHERE s.Deleted_datetime IS NULL AND " + segmentFilter +
                " ORDER BY s.Name";

        System.out.println("📋 [SystemDAO] listSystemsBySegmentAccess SQL for user " + userId + ": " + sql);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("Name"));
                row.put("longName", rs.getString("Long_Name"));
                row.put("description", rs.getString("Description"));
                row.put("parent_id", rs.getObject("parent_id"));
                row.put("parentId", rs.getObject("parent_id"));
                row.put("type", rs.getObject("Type"));
                row.put("typeName", rs.getString("typeName"));
                results.add(row);
            }
            System.out.println("📋 [SystemDAO] Found " + results.size() + " accessible systems for user " + userId);
            return results;
        }
    }

    /**
     * List systems for impact/dataset pickers with an additional constraint when a
     * source segment ({@code datasetSegmentId}) is provided:
     * Enterprise/unassigned systems OR systems in that same segment — never
     * unrelated private segments. Always applies this on top of the user's cube
     * filter.
     */
    public List<Map<String, Object>> listSystemsBySegmentAccessAndDatasetSegment(int userId, int datasetSegmentId)
            throws SQLException {
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "System", "s.id");

        if (segmentFilter == null || segmentFilter.trim().isEmpty()) {
            throw new SQLException("Segment filter clause is null or empty for user " + userId);
        }

        String allowedSegmentSql = buildEnterpriseOrSameSegmentConstraintSql(datasetSegmentId);
        String sql = "SELECT s.id, s.Name, s.Long_Name, s.Description, s.parent_id, s.Type, st.Name as typeName " +
                "FROM system s " +
                "LEFT JOIN system_type st ON s.Type = st.id " +
                "WHERE s.Deleted_datetime IS NULL AND " + segmentFilter + " AND " + allowedSegmentSql +
                " ORDER BY s.Name";

        System.out.println("📋 [SystemDAO] listSystemsBySegmentAccessAndDatasetSegment SQL for user " + userId
                + ", datasetSegmentId " + datasetSegmentId + ": " + sql);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            idx = bindEnterpriseOrSameSegmentConstraintParams(ps, idx, datasetSegmentId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("name", rs.getString("Name"));
                    row.put("longName", rs.getString("Long_Name"));
                    row.put("description", rs.getString("Description"));
                    row.put("parent_id", rs.getObject("parent_id"));
                    row.put("parentId", rs.getObject("parent_id"));
                    row.put("type", rs.getObject("Type"));
                    row.put("typeName", rs.getString("typeName"));
                    results.add(row);
                }
                System.out.println("📋 [SystemDAO] listSystemsBySegmentAccessAndDatasetSegment returned "
                        + results.size() + " systems for user " + userId + ", datasetSegmentId " + datasetSegmentId);
                // Check if System ID 1 is in results
                boolean hasSystem1 = results.stream().anyMatch(r -> {
                    Object id = r.get("id");
                    return id != null && (id instanceof Integer ? ((Integer) id) == 1 : id.toString().equals("1"));
                });
                System.out.println("📋 [SystemDAO] System ID 1 in results: " + hasSystem1);
                return results;
            }
        }
    }

    /**
     * Same as listSystemsBySegmentAccessAndDatasetSegment but without user cube
     * filter (anonymous / fallback).
     */
    public List<Map<String, Object>> listSystemsByDatasetSegment(int datasetSegmentId) throws SQLException {
        String allowedSegmentSql = buildEnterpriseOrSameSegmentConstraintSql(datasetSegmentId);

        String sql = "SELECT s.id, s.Name, s.Long_Name, s.Description, s.parent_id, s.Type, st.Name as typeName " +
                "FROM system s " +
                "LEFT JOIN system_type st ON s.Type = st.id " +
                "WHERE s.Deleted_datetime IS NULL AND " + allowedSegmentSql +
                " ORDER BY s.Name";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            idx = bindEnterpriseOrSameSegmentConstraintParams(ps, idx, datasetSegmentId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("name", rs.getString("Name"));
                    row.put("longName", rs.getString("Long_Name"));
                    row.put("description", rs.getString("Description"));
                    row.put("parent_id", rs.getObject("parent_id"));
                    row.put("type", rs.getObject("Type"));
                    row.put("typeName", rs.getString("typeName"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * SQL condition for "Enterprise OR selected segment" systems.
     * - Enterprise means segment 1 OR unassigned (no segment_x_resource row).
     * - If datasetSegmentId == 1, only enterprise/unassigned is allowed.
     */
    private String buildEnterpriseOrSameSegmentConstraintSql(int datasetSegmentId) {
        // NOTE: Uses the same segmentation mapping as getSegmentIdForSystem()
        // Unassigned systems should be treated as Enterprise for dropdown filtering.
        return "(" +
                "NOT EXISTS (" +
                "  SELECT 1 FROM segment_x_resource sxr " +
                "  JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                "  JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                "  WHERE orr.Object_ID = s.id AND sot.Type = 'System' AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR EXISTS (" +
                "  SELECT 1 FROM segment_x_resource sxr " +
                "  JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                "  JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                "  WHERE orr.Object_ID = s.id AND sot.Type = 'System' AND sxr.Deleted_At IS NULL " +
                "    AND sxr.Segment_ID IN (" + (datasetSegmentId == 1 ? "?" : "?, ?") + ")" +
                ")" +
                ")";
    }

    private int bindEnterpriseOrSameSegmentConstraintParams(PreparedStatement ps, int startIndex, int datasetSegmentId)
            throws SQLException {
        // Always include Enterprise segment id = 1
        ps.setInt(startIndex++, 1);
        if (datasetSegmentId != 1) {
            ps.setInt(startIndex++, datasetSegmentId);
        }
        return startIndex;
    }

    public Map<String, Object> getSystemDetailsById(int id) throws SQLException {
        // Get active nobject_id values to exclude from parent lookup
        Set<Integer> excludedIds = getActiveNObjectIds();
        String parentExcludeClause = "";
        if (!excludedIds.isEmpty()) {
            String idsList = excludedIds.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            parentExcludeClause = " AND p.id NOT IN (" + idsList + ")";
        }

        String sql = "SELECT s.id, s.parent_id, s.Name, s.Long_Name, s.Description, s.URL, s.AssetID, s.External, s.DQ_Automation, "
                +
                "s.Status, stt.primaryname as statusName, s.Lifecycle, sl.Name as lifecycleName, s.Type, st.Name as typeName, "
                +
                "s.Classification, sc.Name as classificationName, s.is_Public, v.Name as viewingName, " +
                "s.Confidentiality_Rating, crC.Values as ciaC, s.Integrity_Rating, crI.Values as ciaI, s.Availability_Rating, crA.Values as ciaA, "
                +
                "s.Created_Datetime, s.Last_Updated_Datetime, s.CreatedBy_ID, s.Last_updated_UserID, " +
                "CONCAT(cb.First_Name, ' ', cb.Last_Name) AS createdByName, " +
                "CONCAT(lub.First_Name, ' ', lub.Last_Name) AS lastUpdatedByName, " +
                "p.Name as parentName " +
                "FROM system s " +
                "LEFT JOIN system p ON p.id = s.parent_id AND p.Deleted_datetime IS NULL AND p.id != s.id "
                + parentExcludeClause +
                "LEFT JOIN system_type st ON st.id = s.Type " +
                "LEFT JOIN system_classification sc ON sc.id = s.Classification " +
                "LEFT JOIN system_lifecycle sl ON sl.id = s.Lifecycle " +
                "LEFT JOIN viewing v ON v.id = s.is_Public " +
                "LEFT JOIN status stt ON stt.ID = s.Status " +
                "LEFT JOIN cia_rating crC ON crC.id = s.Confidentiality_Rating " +
                "LEFT JOIN cia_rating crI ON crI.id = s.Integrity_Rating " +
                "LEFT JOIN cia_rating crA ON crA.id = s.Availability_Rating " +
                "LEFT JOIN people cb ON cb.ID = s.CreatedBy_ID " +
                "LEFT JOIN people lub ON lub.ID = s.Last_updated_UserID " +
                "WHERE s.id = ? AND s.Deleted_datetime IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return null;
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("Name"));
                row.put("longName", rs.getString("Long_Name"));
                row.put("description", rs.getString("Description"));
                row.put("url", rs.getString("URL"));
                row.put("assetId", rs.getString("AssetID"));
                row.put("external", (Object) rs.getObject("External"));
                row.put("dqAutomation", (Object) rs.getObject("DQ_Automation"));
                row.put("status", (Object) rs.getObject("Status"));
                row.put("statusName", rs.getString("statusName"));
                row.put("lifecycle", (Object) rs.getObject("Lifecycle"));
                row.put("lifecycleName", rs.getString("lifecycleName"));
                row.put("Type", (Object) rs.getObject("Type")); // Frontend expects "Type" with capital T
                row.put("type", (Object) rs.getObject("Type")); // Keep lowercase for compatibility
                row.put("typeName", rs.getString("typeName"));
                row.put("classification", (Object) rs.getObject("Classification"));
                row.put("classificationName", rs.getString("classificationName"));
                row.put("isPublic", (Object) rs.getObject("is_Public"));
                row.put("viewingName", rs.getString("viewingName"));
                row.put("confidentialityRating", (Object) rs.getObject("Confidentiality_Rating"));
                row.put("integrityRating", (Object) rs.getObject("Integrity_Rating"));
                row.put("availabilityRating", (Object) rs.getObject("Availability_Rating"));
                // Handle null values for CIA ratings
                Object ciaC = rs.getObject("ciaC");
                Object ciaI = rs.getObject("ciaI");
                Object ciaA = rs.getObject("ciaA");

                Map<String, Object> ciaMap = new HashMap<>();
                ciaMap.put("c", ciaC);
                ciaMap.put("i", ciaI);
                ciaMap.put("a", ciaA);
                row.put("cia", ciaMap);

                // Audit fields
                java.sql.Timestamp createdDatetime = rs.getTimestamp("Created_Datetime");
                java.sql.Timestamp lastUpdatedDatetime = rs.getTimestamp("Last_Updated_Datetime");
                row.put("createdDatetime", createdDatetime);
                // If no updates happened, fall back to created date
                row.put("lastUpdatedDatetime", lastUpdatedDatetime != null ? lastUpdatedDatetime : createdDatetime);
                row.put("createdById", (Object) rs.getObject("CreatedBy_ID"));
                Integer lastUpdatedUserId = (Integer) rs.getObject("Last_updated_UserID");
                row.put("lastUpdatedUserId", lastUpdatedUserId != null ? lastUpdatedUserId : rs.getObject("CreatedBy_ID"));
                row.put("createdByName", rs.getString("createdByName"));
                String lastUpdatedByName = rs.getString("lastUpdatedByName");
                row.put("lastUpdatedByName", (lastUpdatedByName != null && !lastUpdatedByName.trim().isEmpty()) ? lastUpdatedByName : rs.getString("createdByName"));

                Integer parentId = (Integer) rs.getObject("parent_id");
                String parentName = rs.getString("parentName");

                // Exclude parent if it's the same as current system (circular reference) or if
                // it's an excluded nobject_id
                if (parentId != null && (parentId.equals(id) || excludedIds.contains(parentId))) {
                    System.out.println(
                            "[SystemDAO] getSystemDetailsById() - Excluding parent (circular reference or nobject_id): parentId="
                                    + parentId + ", systemId=" + id);
                    parentId = null;
                    parentName = null;
                }

                Map<String, Object> hierarchy = new HashMap<>();
                hierarchy.put("parentId", parentId);
                hierarchy.put("parentName", parentName);
                
                // Build full parent chain (from root down to immediate parent) for hierarchy breadcrumb with arrows
                List<Map<String, Object>> parentChain = new ArrayList<>();
                if (parentId != null) {
                    Integer walkId = parentId;
                    int safetyLimit = 20; // prevent infinite loops
                    while (walkId != null && safetyLimit-- > 0) {
                        try (PreparedStatement pChain = conn.prepareStatement(
                                "SELECT s.id, s.parent_id, s.Name FROM system s WHERE s.id = ? AND s.Deleted_datetime IS NULL")) {
                            pChain.setInt(1, walkId);
                            try (ResultSet pRs = pChain.executeQuery()) {
                                if (pRs.next()) {
                                    Map<String, Object> ancestor = new HashMap<>();
                                    ancestor.put("id", pRs.getInt("id"));
                                    ancestor.put("name", pRs.getString("Name"));
                                    parentChain.add(0, ancestor); // Insert at beginning so root is first
                                    walkId = (Integer) pRs.getObject("parent_id");
                                    // Skip if parent points to itself or to an excluded ID
                                    if (walkId != null && (walkId.equals(pRs.getInt("id")) || excludedIds.contains(walkId))) {
                                        walkId = null;
                                    }
                                } else {
                                    walkId = null;
                                }
                            }
                        }
                    }
                }
                hierarchy.put("parentChain", parentChain);
                row.put("hierarchy", hierarchy);

                // Get segment info for this system (same pattern as DatasetDAO)
                Map<String, Object> segmentInfo = getSystemSegment(id);
                row.put("segmentId", segmentInfo.get("segmentId"));
                row.put("segmentName", segmentInfo.get("segmentName"));

                return row;
            }
        }
    }

    public List<Map<String, Object>> getInterfacesForSystem(int systemId) throws SQLException {
        String sql = """
                    SELECT
                        i.id,
                        i.Source_systemID AS fromId,
                        i.Target_systemID AS toId,
                        s1.Name AS fromName,
                        s2.Name AS toName,
                        i.Name AS interfaceName,
                        i.Description AS description,
                        ia.Name AS automationName,
                        ifr.Name AS frequencyName,
                        i.Synchronisation_Control AS syncControl,
                        itm.Name AS transferMethodName,
                        itf.Name AS transferFormatName
                    FROM interface i
                    LEFT JOIN system s1 ON s1.id = i.Source_systemID
                    LEFT JOIN system s2 ON s2.id = i.Target_systemID
                    LEFT JOIN interface_automation ia ON ia.id = i.Automation_ID
                    LEFT JOIN interface_frequency ifr ON ifr.id = i.Frequency_ID
                    LEFT JOIN interface_transfer itm ON itm.id = i.Transfer_Method_ID
                    LEFT JOIN interface_transfer_format itf ON itf.id = i.Transfer_Format_ID
                    WHERE (i.Source_systemID = ? OR i.Target_systemID = ?)
                    AND i.deleted_datetime IS NULL
                    ORDER BY s1.Name, s2.Name, i.Name
                """;

        String countSql = """
                    SELECT COUNT(*) AS cnt
                    FROM attribute_x_attribute axa
                    WHERE axa.Relation_Method = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            ps.setInt(2, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    int interfaceId = rs.getInt("id");
                    row.put("id", interfaceId);

                    // Include system IDs for proper mapping
                    Integer fromId = rs.getObject("fromId") != null ? rs.getInt("fromId") : null;
                    Integer toId = rs.getObject("toId") != null ? rs.getInt("toId") : null;
                    row.put("fromId", fromId);
                    row.put("toId", toId);
                    row.put("sourceSystemId", fromId); // Alternative name for compatibility
                    row.put("targetSystemId", toId); // Alternative name for compatibility

                    row.put("from", rs.getString("fromName"));
                    row.put("to", rs.getString("toName"));
                    row.put("fromSystem", rs.getString("fromName")); // Alternative name for compatibility
                    row.put("toSystem", rs.getString("toName")); // Alternative name for compatibility
                    row.put("name", rs.getString("interfaceName"));
                    row.put("description", rs.getString("description"));
                    row.put("automation", rs.getString("automationName"));
                    row.put("frequency", rs.getString("frequencyName"));
                    row.put("syncControl", rs.getString("syncControl"));
                    row.put("transferMethod", rs.getString("transferMethodName"));
                    row.put("transferFormat", rs.getString("transferFormatName"));

                    // Count data attributes (attribute lineage) for this interface
                    try (PreparedStatement cps = conn.prepareStatement(countSql)) {
                        cps.setInt(1, interfaceId);
                        try (ResultSet crs = cps.executeQuery()) {
                            row.put("dataAttributes", crs.next() ? crs.getInt("cnt") : 0);
                        }
                    }

                    results.add(row);
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> getDataContentSummaryForSystem(int systemId) throws SQLException {
        String sql = """
                     SELECT
                         gxs.ID                 AS linkId,
                         gxs.GlossaryID         AS glossaryId,
                         g.Name                 AS glossaryName,
                         g.Description          AS definition,
                         gt.Name                AS glossaryType,
                         grt.ID                 AS relationTypeId,
                         grt.PrimaryName        AS relationType,
                         (
                             SELECT GROUP_CONCAT(gan.Name SEPARATOR '||')
                             FROM glossary_alias_names gan
                             WHERE gan.Glossary_id = gxs.GlossaryID
                         ) AS aliasNames
                     FROM glossary_x_system gxs
                     JOIN glossary g ON g.ID = gxs.GlossaryID
                     LEFT JOIN glossary_type gt ON gt.ID = g.Type
                     LEFT JOIN glossary_x_system_relationtype grt ON grt.ID = gxs.Relation_TypeID AND grt.TypeID=1
                     WHERE gxs.SystemID = ? AND (gxs.Link_Source = 'system' OR gxs.Link_Source IS NULL)
                     ORDER BY g.Name    \s
                \s""";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("linkId"));
                    row.put("glossaryId", (Object) rs.getObject("glossaryId"));
                    row.put("glossary", rs.getString("glossaryName"));
                    row.put("definition", rs.getString("definition"));
                    row.put("glossaryType", rs.getString("glossaryType"));
                    row.put("relationshipType", rs.getString("relationType"));
                    // Relationship Status intentionally blank per requirements
                    row.put("relationshipStatus", "");

                    String aliases = rs.getString("aliasNames");
                    if (aliases != null && !aliases.isEmpty()) {
                        String[] parts = aliases.split("\\|\\|");
                        List<String> list = new ArrayList<>(parts.length);
                        list.addAll(Arrays.asList(parts));
                        row.put("aliasNames", list);
                    } else {
                        row.put("aliasNames", java.util.Collections.emptyList());
                    }

                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * Returns glossary IDs that are already linked to this system from the Glossary's Strategic Source (Link_Source = 'glossary').
     * These should be excluded from the Data Content Summary "Add glossary" dropdown.
     */
    public List<Integer> getGlossaryIdsExcludedFromDataContent(int systemId) throws SQLException {
        String sql = "SELECT GlossaryID FROM glossary_x_system WHERE SystemID = ? AND Link_Source = 'glossary'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(rs.getInt("GlossaryID"));
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> listRelationTypesForDataContent() throws SQLException {
        String sql = "SELECT ID, PrimaryName FROM glossary_x_system_relationtype WHERE TypeID = 1 ORDER BY PrimaryName";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("PrimaryName"));
                results.add(row);
            }
            return results;
        }
    }

    public void replaceDataContentForSystem(int systemId, List<Map<String, Object>> rows) throws SQLException {
        // Safety: if no valid rows provided, do nothing (avoid accidental wipe)
        if (rows == null)
            return;
        List<Map<String, Object>> valid = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Integer glossaryId = getInt(r.get("glossaryId"));
            Integer relationTypeId = getInt(r.get("relationTypeId"));
            if (glossaryId != null && relationTypeId != null)
                valid.add(r);
        }
        if (valid.isEmpty())
            return;

        String deleteSql = "DELETE FROM glossary_x_system WHERE SystemID = ?";
        String insertSql = "INSERT INTO glossary_x_system (GlossaryID, SystemID, Relation_TypeID, Link_Source) VALUES (?, ?, ?, 'system')";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setInt(1, systemId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                for (Map<String, Object> r : valid) {
                    int glossaryId = getInt(r.get("glossaryId"));
                    int relationTypeId = getInt(r.get("relationTypeId"));
                    ins.setInt(1, glossaryId);
                    ins.setInt(2, systemId);
                    ins.setInt(3, relationTypeId);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        }
    }

    public void syncDataContentForSystem(int systemId, List<Map<String, Object>> rows) throws SQLException {
        if (rows == null)
            return;
        // Build desired set
        Set<String> desired = new HashSet<>();
        for (Map<String, Object> r : rows) {
            Integer g = getInt(r.get("glossaryId"));
            Integer t = getInt(r.get("relationTypeId"));
            if (g != null && t != null)
                desired.add(g + ":" + t);
        }
        if (desired.isEmpty())
            return; // nothing to add; keep existing

        String selectSql = "SELECT GlossaryID, Relation_TypeID FROM glossary_x_system WHERE SystemID = ?";
        String insertSql = "INSERT INTO glossary_x_system (GlossaryID, SystemID, Relation_TypeID, Link_Source) VALUES (?, ?, ?, 'system')";
        String deleteSql = "DELETE FROM glossary_x_system WHERE SystemID = ? AND GlossaryID = ? AND Relation_TypeID = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            // Load current set
            Set<String> current = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, systemId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int g = rs.getInt(1);
                        int t = rs.getInt(2);
                        current.add(g + ":" + t);
                    }
                }
            }

            // Compute diffs
            Set<String> toInsert = new HashSet<>(desired);
            toInsert.removeAll(current);
            Set<String> toDelete = new HashSet<>(current);
            toDelete.removeAll(desired);

            // Apply deletes
            if (!toDelete.isEmpty()) {
                try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                    for (String key : toDelete) {
                        String[] parts = key.split(":");
                        int g = Integer.parseInt(parts[0]);
                        int t = Integer.parseInt(parts[1]);
                        del.setInt(1, systemId);
                        del.setInt(2, g);
                        del.setInt(3, t);
                        del.addBatch();
                    }
                    del.executeBatch();
                }
            }

            // Apply inserts
            if (!toInsert.isEmpty()) {
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    for (String key : toInsert) {
                        String[] parts = key.split(":");
                        int g = Integer.parseInt(parts[0]);
                        int t = Integer.parseInt(parts[1]);
                        ins.setInt(1, g);
                        ins.setInt(2, systemId);
                        ins.setInt(3, t);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }

            conn.commit();
        }
    }

    public void applyDataContentChanges(int systemId,
            List<Map<String, Object>> inserts,
            List<Map<String, Object>> updates,
            List<Integer> deletes) throws SQLException {
        applyDataContentChanges(systemId, inserts, updates, deletes, null);
    }

    /**
     * Apply data content changes and optionally return IDs of newly inserted
     * relationships
     * 
     * @param systemId    System ID to apply changes to
     * @param inserts     List of new relationships to insert
     * @param updates     List of relationships to update
     * @param deletes     List of relationship IDs to delete
     * @param insertedIds Optional list to populate with IDs of newly inserted
     *                    relationships
     */
    public void applyDataContentChanges(int systemId,
            List<Map<String, Object>> inserts,
            List<Map<String, Object>> updates,
            List<Integer> deletes,
            List<Integer> insertedIds) throws SQLException {
        String insertSql = "INSERT INTO glossary_x_system (GlossaryID, SystemID, Relation_TypeID, Link_Source) VALUES (?, ?, ?, 'system')";
        String updateSql = "UPDATE glossary_x_system SET GlossaryID = ?, Relation_TypeID = ?, Link_Source = 'system' WHERE ID = ? AND SystemID = ?";
        String deleteSql = "DELETE FROM glossary_x_system WHERE ID = ? AND SystemID = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            if (deletes != null && !deletes.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    for (Integer id : deletes) {
                        if (id == null)
                            continue;
                        ps.setInt(1, id);
                        ps.setInt(2, systemId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            if (updates != null && !updates.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    for (Map<String, Object> row : updates) {
                        Integer id = getInt(row.get("id"));
                        Integer glossaryId = getInt(row.get("glossaryId"));
                        Integer relationTypeId = getInt(row.get("relationTypeId"));
                        if (id == null || glossaryId == null || relationTypeId == null)
                            continue;
                        ps.setInt(1, glossaryId);
                        ps.setInt(2, relationTypeId);
                        ps.setInt(3, id);
                        ps.setInt(4, systemId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            if (inserts != null && !inserts.isEmpty()) {
                // Use RETURN_GENERATED_KEYS to get IDs of newly inserted relationships
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    for (Map<String, Object> row : inserts) {
                        Integer glossaryId = getInt(row.get("glossaryId"));
                        Integer relationTypeId = getInt(row.get("relationTypeId"));
                        if (glossaryId == null || relationTypeId == null)
                            continue;
                        ps.setInt(1, glossaryId);
                        ps.setInt(2, systemId);
                        ps.setInt(3, relationTypeId);
                        ps.addBatch();
                    }
                    ps.executeBatch();

                    // Get generated keys for newly inserted relationships
                    if (insertedIds != null) {
                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            while (generatedKeys.next()) {
                                insertedIds.add(generatedKeys.getInt(1));
                            }
                        }
                    }
                }
            }

            conn.commit();
        }
    }

    public int insertSystem(
            String name,
            Integer parentId,
            String description,
            Integer typeId,
            Integer external,
            String longName,
            String url,
            Integer statusId,
            Integer lifecycleId,
            Integer isPublicId,
            Integer confidentialityId,
            Integer integrityId,
            Integer availabilityId,
            String assetId,
            Integer classificationId,
            Integer dqAutomation,
            Integer createdById) throws SQLException {
        String sql = "INSERT INTO system (parent_id, is_Public, status, Lifecycle, Type, Classification, " +
                "Confidentiality_Rating, Integrity_Rating, Availability_Rating, Name, Long_Name, AssetID, External, Description, Created_Datetime, Last_Updated_Datetime, URL, DQ_Automation, CreatedBy_ID, Last_updated_UserID) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NULL, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // 1 parent_id
            if (parentId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, parentId);
            }
            // 2 is_Public
            if (isPublicId == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, isPublicId);
            }
            // 3 status
            if (statusId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, statusId);
            }
            // 4 Lifecycle
            if (lifecycleId == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, lifecycleId);
            }
            // 5 Type
            if (typeId == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, typeId);
            }
            // 6 Classification
            if (classificationId == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, classificationId);
            }
            // 7 Confidentiality_Rating
            if (confidentialityId == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, confidentialityId);
            }
            // 8 Integrity_Rating
            if (integrityId == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, integrityId);
            }
            // 9 Availability_Rating
            if (availabilityId == null) {
                ps.setNull(9, Types.INTEGER);
            } else {
                ps.setInt(9, availabilityId);
            }
            // 10 Name
            ps.setString(10, name);
            // 11 Long_Name
            if (longName == null || longName.isEmpty()) {
                ps.setNull(11, Types.VARCHAR);
            } else {
                ps.setString(11, longName);
            }
            // 12 AssetID
            if (assetId == null || assetId.isEmpty()) {
                ps.setNull(12, Types.VARCHAR);
            } else {
                ps.setString(12, assetId);
            }
            // 13 External
            if (external == null) {
                ps.setNull(13, Types.TINYINT);
            } else {
                ps.setInt(13, external);
            }
            // 14 Description
            if (description == null || description.isEmpty()) {
                ps.setNull(14, Types.VARCHAR);
            } else {
                ps.setString(14, description);
            }
            // 15 URL
            if (url == null || url.isEmpty()) {
                ps.setNull(15, Types.VARCHAR);
            } else {
                ps.setString(15, url);
            }
            // 16 DQ_Automation
            if (dqAutomation == null) {
                ps.setNull(16, Types.TINYINT);
            } else {
                ps.setInt(16, dqAutomation);
            }
            // 17 CreatedBy_ID
            if (createdById == null) {
                ps.setNull(17, Types.INTEGER);
            } else {
                ps.setInt(17, createdById);
            }
            // 18 Last_updated_UserID (set to creator on create)
            if (createdById == null) {
                ps.setNull(18, Types.INTEGER);
            } else {
                ps.setInt(18, createdById);
            }

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public Integer findSystemTypeIdByName(String typeName) throws SQLException {
        if (typeName == null || typeName.isEmpty())
            return null;
        String sql = "SELECT id FROM system_type WHERE Name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, typeName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("id");
            }
        }
        return null;
    }

    public boolean updateSystem(
            int id,
            String name,
            Integer parentId,
            String description,
            Integer typeId,
            Integer external,
            String longName,
            String url,
            Integer statusId,
            Integer lifecycleId,
            Integer isPublicId,
            Integer confidentialityId,
            Integer integrityId,
            Integer availabilityId,
            String assetId,
            Integer classificationId,
            Integer dqAutomation,
            Integer userId) throws SQLException {
        // الخطوة 1: احصل على البيانات القديمة قبل التحديث
        Map<String, Object> oldSystem = getSystemDetailsById(id);
        if (oldSystem == null) {
            throw new SQLException("System not found with ID: " + id);
        }

        String sql = "UPDATE system SET parent_id=?, is_Public=?, status=?, Lifecycle=?, Type=?, Classification=?, " +
                "Confidentiality_Rating=?, Integrity_Rating=?, Availability_Rating=?, Name=?, Long_Name=?, AssetID=?, External=?, Description=?, URL=?, DQ_Automation=?, Last_Updated_Datetime = NOW(), Last_updated_UserID = ? "
                +
                "WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            // 1 parent_id
            if (parentId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, parentId);
            }
            // 2 is_Public
            if (isPublicId == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, isPublicId);
            }
            // 3 status
            if (statusId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, statusId);
            }
            // 4 Lifecycle
            if (lifecycleId == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, lifecycleId);
            }
            // 5 Type
            if (typeId == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, typeId);
            }
            // 6 Classification
            if (classificationId == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, classificationId);
            }
            // 7 Confidentiality_Rating
            if (confidentialityId == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, confidentialityId);
            }
            // 8 Integrity_Rating
            if (integrityId == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, integrityId);
            }
            // 9 Availability_Rating
            if (availabilityId == null) {
                ps.setNull(9, Types.INTEGER);
            } else {
                ps.setInt(9, availabilityId);
            }
            // 10 Name
            ps.setString(10, name != null ? name : "");
            // 11 Long_Name
            if (longName == null || longName.isEmpty()) {
                ps.setNull(11, Types.VARCHAR);
            } else {
                ps.setString(11, longName);
            }
            // 12 AssetID
            if (assetId == null || assetId.isEmpty()) {
                ps.setNull(12, Types.VARCHAR);
            } else {
                ps.setString(12, assetId);
            }
            // 13 External
            if (external == null) {
                ps.setNull(13, Types.TINYINT);
            } else {
                ps.setInt(13, external);
            }
            // 14 Description
            if (description == null || description.isEmpty()) {
                ps.setNull(14, Types.VARCHAR);
            } else {
                ps.setString(14, description);
            }
            // 15 URL
            if (url == null || url.isEmpty()) {
                ps.setNull(15, Types.VARCHAR);
            } else {
                ps.setString(15, url);
            }
            // 16 DQ_Automation
            if (dqAutomation == null) {
                ps.setNull(16, Types.TINYINT);
            } else {
                ps.setInt(16, dqAutomation);
            }
            // 17 Last_updated_UserID
            if (userId == null) {
                ps.setNull(17, Types.INTEGER);
            } else {
                ps.setInt(17, userId);
            }
            // where id
            ps.setInt(18, id);

            int affectedRows = ps.executeUpdate();

            if (affectedRows > 0) {
                // الخطوة 2: إنشاء snapshot جديد في system_audit أولاً
                try {
                    createSystemUpdateAuditSnapshot(id);
                    // system.out.println("✅ SystemDAO: system_audit update snapshot created for ID:
                    // " + id);
                } catch (Exception e) {
                    System.err.println("❌ Error creating system_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }

                // الخطوة 3: إنشاء audit records للتحديثات
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

                    // إنشاء Map للبيانات الجديدة
                    Map<String, Object> newSystem = new java.util.HashMap<>();
                    newSystem.put("name", name);
                    newSystem.put("parentId", parentId);
                    newSystem.put("description", description);
                    newSystem.put("Type", typeId);
                    newSystem.put("external", external);
                    newSystem.put("longName", longName);
                    newSystem.put("url", url);
                    newSystem.put("status", statusId);
                    newSystem.put("lifecycle", lifecycleId);
                    newSystem.put("isPublic", isPublicId);
                    newSystem.put("confidentialityRating", confidentialityId);
                    newSystem.put("integrityRating", integrityId);
                    newSystem.put("availabilityRating", availabilityId);
                    newSystem.put("assetId", assetId);
                    newSystem.put("classification", classificationId);
                    newSystem.put("dqAutomation", dqAutomation);

                    createSystemUpdateAuditRecords(id, oldSystem, newSystem, userName);
                    // system.out.println("✅ System update audit records created for ID: " + id + "
                    // with author: " + userName);
                } catch (Exception e) {
                    System.err.println("❌ Error creating system update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }
            }

            return affectedRows > 0;
        }
    }

    public List<Map<String, Object>> getDirectStakeholdersForSystem(int systemId) throws SQLException {
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
                    FROM system_x_objectxpeople sx
                    JOIN object_x_people oxp ON sx.Object_x_ipid = oxp.ID
                    JOIN object_role r ON oxp.RoleID = r.ID
                    JOIN people p ON oxp.ipid = p.ID
                    JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                    LEFT JOIN object_x_people d_oxp ON oxp.isDelegateOF = d_oxp.ID
                    LEFT JOIN people delegate_p ON d_oxp.ipid = delegate_p.ID
                    WHERE sx.SystemID = ?
                    ORDER BY r.PrimaryName, p.Last_Name, p.First_Name
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
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

    public List<Map<String, Object>> getRolesForSystem(int systemId) throws SQLException {
        // Get module ID dynamically based on entity type
        int moduleId = ModuleResolver.getModuleId("system");
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

    public List<Map<String, Object>> getUsersByRole(int systemId, int roleId) throws SQLException {
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

    public List<Map<String, Object>> getStatusesForSystem(int systemId) throws SQLException {
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

    public void saveStakeholdersChanges(int systemId, Map<String, Object> changes, int currentUserId)
            throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Extract arrays
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> inserts = (List<Map<String, Object>>) changes.getOrDefault("inserts",
                        java.util.Collections.emptyList());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> updates = (List<Map<String, Object>>) changes.getOrDefault("updates",
                        java.util.Collections.emptyList());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> deletes = (List<Map<String, Object>>) changes.getOrDefault("deletes",
                        java.util.Collections.emptyList());

                // Inserts
                for (Map<String, Object> row : inserts) {
                    validateRequired(row);
                    int objectXPeopleId = createObjectXPeople(conn, convertRow(row), currentUserId);
                    linkStakeholderToSystem(conn, systemId, objectXPeopleId);
                }

                // Updates
                for (Map<String, Object> row : updates) {
                    validateRequired(row);
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null)
                        throw new IllegalArgumentException("Missing objectXPeopleId in update row");
                    updateObjectXPeople(conn, oxpId, convertRow(row));
                }

                // Deletes
                for (Map<String, Object> row : deletes) {
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null)
                        throw new IllegalArgumentException("Missing objectXPeopleId in delete row");
                    unlinkStakeholderFromSystem(conn, systemId, oxpId);
                    if (!hasAnyOtherLink(conn, oxpId)) {
                        deleteObjectXPeople(conn, oxpId);
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException)
                    throw (SQLException) e;
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
        if (acc != null) {
            // 1 => Active (True), 0 => Inactive (False)
            return acc == 1 ? 1 : 0;
        }
        return sid;
    }

    private void validateRequired(Map<String, Object> row) {
        if (getInt(row.get("roleId")) == null)
            throw new IllegalArgumentException("Role is required");
        if (getInt(row.get("ipid")) == null)
            throw new IllegalArgumentException("Name is required");
    }

    private Integer getInt(Object v) {
        if (v == null)
            return null;
        if (v instanceof Number)
            return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create object_x_people record for stakeholder
     * Always creates a NEW record (no reuse)
     */
    public int createObjectXPeople(Connection conn, Map<String, Object> stakeholder, int currentUserId)
            throws SQLException {
        Integer statusId = getInt(stakeholder.get("statusId"));
        if (statusId == null) {
            statusId = 1;
        }
        String sql = """
                    INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
                    VALUES (NULL, ?, ?, 2, ?, NOW(), ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, (Integer) stakeholder.get("userId")); // ipid
            ps.setInt(2, (Integer) stakeholder.get("roleId")); // RoleID
            ps.setInt(3, statusId); // statusID from map
            ps.setInt(4, currentUserId); // lastupdateuser_id

            ps.executeUpdate();
            // system.out.println("✅ Created object_x_people record, rows affected=" +
            // rowsAffected);

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    // system.out.println("✅ Generated object_x_people ID: " + newId);
                    return newId;
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    private void updateObjectXPeople(Connection conn, int objectXPeopleId, Map<String, Object> stakeholder)
            throws SQLException {
        String sql = """
                     UPDATE object_x_people\s
                     SET RoleID = ?, ipid = ?, AcceptedID = 2, statusID = ?, isDelegateOF = ?, lastupdatedatetime = NOW()
                     WHERE ID = ?
                \s""";

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
                if (rs.next())
                    return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /**
     * Link stakeholder to system via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToSystem(Connection conn, int systemId, int objectXPeopleId) throws SQLException {
        String sql = """
                    INSERT INTO system_x_objectxpeople (Object_x_ipid, SystemID, Last_UpdateUser_ID)
                    VALUES (?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            ps.setInt(2, systemId);
            ps.setNull(3, java.sql.Types.INTEGER); // Last_UpdateUser_ID can be null
            try {
                ps.executeUpdate();
                // system.out.println("✅ Successfully linked stakeholder to system: SystemID=" +
                // systemId + ", Object_x_ipid=" + objectXPeopleId + ", rows affected=" +
                // rowsAffected);
            } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
                // system.out.println("⚠️ Link already exists: SystemID=" + systemId + ",
                // Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }

    private void unlinkStakeholderFromSystem(Connection conn, int systemId, int objectXPeopleId) throws SQLException {
        String sql = "DELETE FROM system_x_objectxpeople WHERE SystemID = ? AND Object_x_ipid = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            ps.setInt(2, objectXPeopleId);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No rows deleted from system_x_objectxpeople for SystemID: " + systemId
                        + ", Object_x_ipid: " + objectXPeopleId);
            }
        }
    }

    public boolean deleteById(int id) throws SQLException {
        String sql = "DELETE FROM system WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            return rows > 0;
        }
    }

    public int countLinkedInterfaces(int systemId) throws SQLException {
        String sql = "SELECT (SELECT COUNT(*) FROM interface WHERE Source_systemID = ?) + (SELECT COUNT(*) FROM interface WHERE Target_systemID = ?) AS cnt";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            ps.setInt(2, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("cnt");
            }
        }
        return 0;
    }

    public List<Map<String, Object>> getSystemHierarchy(int systemId) throws SQLException {
        // Get active nobject_id values to exclude (temporary cloned rows from active
        // CRs)
        Set<Integer> excludedIds = getActiveNObjectIds();

        String excludeClause = "";
        if (!excludedIds.isEmpty()) {
            String idsList = excludedIds.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            excludeClause = " AND s.id NOT IN (" + idsList + ")";
            System.out.println("[SystemDAO] getSystemHierarchy() - Excluding " + excludedIds.size() + " nobject_id values: " + idsList);
        } else {
            System.out.println("[SystemDAO] getSystemHierarchy() - No nobject_id values to exclude");
        }

        // Get complete hierarchy: ancestors (parents) + current + descendants
        // (children)
        // IMPORTANT: Exclude the current system from ancestors and descendants to
        // prevent it from appearing as its own parent
        String sql = "WITH RECURSIVE " +
                // Step 1: Walk up from current system to find the root ancestor
                "ancestor_chain AS (" +
                "    SELECT s.id, s.parent_id, 0 as depth " +
                "    FROM system s " +
                "    WHERE s.id = ? AND s.Deleted_datetime IS NULL " + excludeClause +
                "    UNION ALL " +
                "    SELECT s.id, s.parent_id, ac.depth + 1 " +
                "    FROM system s " +
                "    INNER JOIN ancestor_chain ac ON s.id = ac.parent_id " +
                "    WHERE s.Deleted_datetime IS NULL AND ac.depth < 20 " + excludeClause +
                "), " +
                // Step 2: Pick the topmost ancestor (root of the tree)
                "root_node AS (" +
                "    SELECT id FROM ancestor_chain ORDER BY depth DESC LIMIT 1" +
                "), " +
                // Step 3: Get ALL descendants from root (full tree including siblings, cousins, etc.)
                "full_tree AS (" +
                "    SELECT s.id, s.parent_id, s.Name, s.Long_Name, s.Description, st.Name as typeName, sc.Name as classificationName, 0 as level, " +
                "           CASE WHEN s.id = ? THEN 'current' ELSE 'relative' END as relation " +
                "    FROM system s " +
                "    LEFT JOIN system_type st ON st.id = s.Type " +
                "    LEFT JOIN system_classification sc ON sc.id = s.Classification " +
                "    WHERE s.id = (SELECT id FROM root_node) AND s.Deleted_datetime IS NULL " + excludeClause +
                "    UNION ALL " +
                "    SELECT s.id, s.parent_id, s.Name, s.Long_Name, s.Description, st.Name as typeName, sc.Name as classificationName, ft.level + 1, " +
                "           CASE WHEN s.id = ? THEN 'current' ELSE 'relative' END " +
                "    FROM system s " +
                "    INNER JOIN full_tree ft ON s.parent_id = ft.id " +
                "    LEFT JOIN system_type st ON st.id = s.Type " +
                "    LEFT JOIN system_classification sc ON sc.id = s.Classification " +
                "    WHERE s.Deleted_datetime IS NULL AND ft.level < 20 " + excludeClause +
                ") " +
                "SELECT id, parent_id, Name, Long_Name, Description, typeName, classificationName, level, relation FROM full_tree " +
                "ORDER BY level, Name";

        System.out.println("[SystemDAO] getSystemHierarchy() SQL (full tree): " + sql);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);  // For ancestor_chain base case
            ps.setInt(2, systemId);  // For CASE in full_tree base (mark current system)
            ps.setInt(3, systemId);  // For CASE in full_tree recursive (mark current system)
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    int id = rs.getInt("id");
                    row.put("id", id);
                    row.put("parentId", rs.getObject("parent_id"));
                    row.put("name", rs.getString("Name"));
                    row.put("longName", rs.getString("Long_Name"));
                    row.put("description", rs.getString("Description"));
                    row.put("typeName", rs.getString("typeName"));
                    row.put("classificationName", rs.getString("classificationName"));
                    row.put("level", rs.getInt("level"));
                    row.put("relation", rs.getString("relation"));

                    // Log if we accidentally included an excluded ID
                    if (excludedIds.contains(id)) {
                        System.err.println(
                                "[SystemDAO] WARNING: getSystemHierarchy() returned excluded nobject_id: " + id);
                    }

                    results.add(row);
                }
                System.out.println("[SystemDAO] getSystemHierarchy() - Returning " + results.size()
                        + " hierarchy items (excluded " + excludedIds.size() + " nobject_id values)");
                return results;
            }
        }
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int systemId, String updateType,
            String field, String value, String userName) throws SQLException {
        // Skip recording if value is empty or null
        if (value == null || value.trim().isEmpty()) {
            return -1; // Don't create audit record for empty values
        }

        auditStmt.setInt(1, systemId); // id
        auditStmt.setString(2, updateType); // updateType
        auditStmt.setString(3, field); // field
        auditStmt.setString(4, value); // to
        auditStmt.setString(5, userName); // author
        auditStmt.executeUpdate();

        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit records للنظام الجديد
     * يتم استدعاء هذا method بعد إنشاء النظام بنجاح
     */
    public void createSystemAuditRecords(int systemId, String userName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            createSystemAuditRecords(systemId, userName, conn);
        }
    }

    public void createSystemAuditRecords(int systemId, String userName, Connection conn) throws SQLException {
        String sql = "SELECT * FROM system WHERE id = ?";
        String auditSql = """
                    INSERT INTO system_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, 'System', 'Details', ?, ?, NULL, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
                PreparedStatement auditStmt = conn.prepareStatement(auditSql,
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, systemId);
            try (ResultSet systemRs = ps.executeQuery()) {
                if (systemRs.next()) {
                    AuditHistoryWriter.logCreatedBy(conn, "system_audit_history", systemId, "System", userName);
                    // Create audit records for all fields
                    // Name
                    String name = systemRs.getString("Name");
                    if (name != null && !name.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, systemId, "Added", "Name", name, userName);
                    }

                    // Long Name
                    String longName = systemRs.getString("Long_Name");
                    if (longName != null && !longName.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, systemId, "Added", "Long Name", longName, userName);
                    }

                    // Description
                    String description = systemRs.getString("Description");
                    if (description != null && !description.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, systemId, "Added", "Description", description, userName);
                    }

                    // Asset ID
                    String assetId = systemRs.getString("AssetID");
                    if (assetId != null && !assetId.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, systemId, "Added", "Asset Id", assetId, userName);
                    }

                    // URL
                    String url = systemRs.getString("URL");
                    if (url != null && !url.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, systemId, "Added", "URI", url, userName);
                    }

                    // External
                    Boolean external = systemRs.getObject("External", Boolean.class);
                    if (external != null) {
                        createNewAuditRecord(conn, auditStmt, systemId, "Added", "External", external ? "Yes" : "No",
                                userName);
                    }

                    // DQ Automation
                    Boolean dqAutomation = systemRs.getObject("DQ_Automation", Boolean.class);
                    if (dqAutomation != null) {
                        createNewAuditRecord(conn, auditStmt, systemId, "Added", "DQ Automation",
                                dqAutomation ? "Yes" : "No", userName);
                    }

                    // Type
                    Integer typeId = (Integer) systemRs.getObject("Type");
                    if (typeId != null) {
                        String typeName = getLookupName(conn, "system_type", typeId);
                        if (typeName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "System Type", typeName, userName);
                        }
                    }

                    // Status
                    Integer statusId = (Integer) systemRs.getObject("Status");
                    if (statusId != null) {
                        String statusName = getLookupName(conn, "status", statusId);
                        if (statusName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Status", statusName, userName);
                        }
                    }

                    // Lifecycle
                    Integer lifecycleId = (Integer) systemRs.getObject("Lifecycle");
                    if (lifecycleId != null) {
                        String lifecycleName = getLookupName(conn, "system_lifecycle", lifecycleId);
                        if (lifecycleName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Lifecycle", lifecycleName,
                                    userName);
                        }
                    }

                    // Classification
                    Integer classificationId = (Integer) systemRs.getObject("Classification");
                    if (classificationId != null) {
                        String classificationName = getLookupName(conn, "system_classification", classificationId);
                        if (classificationName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Classification",
                                    classificationName, userName);
                        }
                    }

                    // Viewing
                    Integer viewingId = (Integer) systemRs.getObject("is_Public");
                    if (viewingId != null) {
                        String viewingName = getLookupName(conn, "viewing", viewingId);
                        if (viewingName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Viewing", viewingName, userName);
                        }
                    }

                    // CIA Ratings
                    Integer cId = (Integer) systemRs.getObject("Confidentiality_Rating");
                    if (cId != null) {
                        String cName = getCiaRatingValue(conn, cId);
                        if (cName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Confidentiality", cName,
                                    userName);
                        }
                    }

                    Integer iId = (Integer) systemRs.getObject("Integrity_Rating");
                    if (iId != null) {
                        String iName = getCiaRatingValue(conn, iId);
                        if (iName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Integrity", iName, userName);
                        }
                    }

                    Integer aId = (Integer) systemRs.getObject("Availability_Rating");
                    if (aId != null) {
                        String aName = getCiaRatingValue(conn, aId);
                        if (aName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Availability", aName, userName);
                        }
                    }

                    // Parent
                    Integer parentId = (Integer) systemRs.getObject("parent_id");
                    if (parentId != null) {
                        String parentName = getSystemName(conn, parentId);
                        if (parentName != null) {
                            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Parent System", parentName,
                                    userName);
                        }
                    }

                    // Created By is written as the first row via AuditHistoryWriter.logCreatedBy.

                    // Audit ratings - done above
                    // Commit logic removed as transaction is managed by caller

                    auditStmt.executeBatch();
                }
            }
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء النظام
     * يتم استدعاء هذا method بعد إنشاء النظام بنجاح
     */
    public void createStakeholderAuditRecords(int systemId, String userName, String userFullName, int roleId)
            throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            createStakeholderAuditRecords(systemId, userName, userFullName, roleId, conn);
        }
    }

    public void createStakeholderAuditRecords(int systemId, String userName, String userFullName, int roleId,
            Connection conn) throws SQLException {
        PreparedStatement auditStmt = null;

        try {
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                        INSERT INTO system_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                        VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
                    """;
            auditStmt = conn.prepareStatement(auditSql, java.sql.Statement.RETURN_GENERATED_KEYS);

            // 3. إدراج 3 سجلات للـ stakeholder
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(conn, systemId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }

            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getLookupName(conn, "object_role", actualRoleId);
            if (roleName == null)
                roleName = "System Owner"; // fallback

            // Role
            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Role", roleName, userName);

            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(conn, systemId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(conn, statusId);
                if (fetchedStatusName != null)
                    statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Role Status", statusName, userName);

            // Name
            createNewAuditRecord(conn, auditStmt, systemId, "Added", "Name", userFullName, userName);

            // Commit is NOT handled here, it's handled by the caller

        } finally {
            if (auditStmt != null)
                auditStmt.close();
        }
    }

    public Map<String, Object> getSystemState(Connection conn, int systemId) throws SQLException {
        String sql = "SELECT * FROM system WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> state = new HashMap<>();
                    state.put("name", rs.getString("Name"));
                    state.put("longName", rs.getString("Long_Name"));
                    state.put("description", rs.getString("Description"));
                    state.put("assetId", rs.getString("AssetID"));
                    state.put("url", rs.getString("URL"));
                    state.put("external", rs.getObject("External"));
                    state.put("dqAutomation", rs.getObject("DQ_Automation"));
                    state.put("parentId", rs.getObject("parent_id"));
                    state.put("isPublic", rs.getObject("is_Public"));
                    state.put("status", rs.getObject("status"));
                    state.put("lifecycle", rs.getObject("Lifecycle"));
                    state.put("Type", rs.getObject("Type"));
                    state.put("classification", rs.getObject("Classification"));
                    state.put("confidentialityRating", rs.getObject("Confidentiality_Rating"));
                    state.put("integrityRating", rs.getObject("Integrity_Rating"));
                    state.put("availabilityRating", rs.getObject("Availability_Rating"));
                    return state;
                }
            }
        }
        return null;
    }

    private String getPersonFullName(int personId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getPersonFullName(conn, personId);
        }
    }

    // Overloaded helper methods taking Connection for transaction safety
    private String getLookupName(Connection conn, String table, int id) throws SQLException {
        String column = "Name";
        if (table.equalsIgnoreCase("status") || table.equalsIgnoreCase("object_role")) {
            column = "primaryname";
        }
        String sql = "SELECT " + column + " FROM " + table + " WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString(column);
            }
        }
        return null;
    }

    private String getCiaRatingValue(Connection conn, int ratingId) throws SQLException {
        String sql = "SELECT `Values` FROM cia_rating WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ratingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("Values");
            }
        }
        return null;
    }

    private String getSystemName(Connection conn, int systemId) throws SQLException {
        String sql = "SELECT Name FROM system WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("Name");
            }
        }
        return null;
    }

    private String getPersonFullName(Connection conn, int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("fullName");
            }
        }
        return null;
    }

    // Get actual roleID from object_x_people for the stakeholder
    private Integer getStakeholderRoleId(Connection conn, int systemId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                "JOIN system_x_objectxpeople sxo ON sxo.Object_x_ipid = oxp.ID " +
                "WHERE sxo.SystemID = ? " +
                "ORDER BY sxo.ID DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("roleID");
            }
        }
        return null;
    }

    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(Connection conn, int systemId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                "JOIN system_x_objectxpeople sxo ON sxo.Object_x_ipid = oxp.ID " +
                "WHERE sxo.SystemID = ? " +
                "ORDER BY sxo.ID DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("statusID");
            }
        }
        return null;
    }

    // Get status name from object_x_ip_status by statusID
    @SuppressWarnings("unused")
    private String getStatusNameById(int statusId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getStatusNameById(conn, statusId);
        }
    }

    private String getStatusNameById(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * إنشاء سجل في جدول system_audit بعد إنشاء النظام
     * يتم استدعاء هذا method بعد إنشاء النظام بنجاح
     */
    public void createSystemAuditRecord(int systemId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            createSystemAuditRecord(systemId, conn);
        }
    }

    public void createSystemAuditRecord(int systemId, Connection conn) throws SQLException {
        String sql = """
                    INSERT INTO system_audit (
                        id, parent_id, is_Public, status, Lifecycle, Type, Classification,
                        Confidentiality_Rating, Integrity_Rating, Availability_Rating,
                        Name, Long_Name, AssetID, External, Descritpion, Created_Datetime,
                        Last_Updated_Datetime, URL, DQ_Automation, CreatedBy_ID, Last_updated_UserID, rev_type
                    )
                    SELECT
                        id, parent_id, is_Public, status, Lifecycle, Type, Classification,
                        Confidentiality_Rating, Integrity_Rating, Availability_Rating,
                        Name, Long_Name, AssetID, External, Description, Created_Datetime,
                        Last_Updated_Datetime, URL, DQ_Automation, CreatedBy_ID, Last_updated_UserID, 'Added'
                    FROM system
                    WHERE id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            ps.executeUpdate();
        }
    }

    /**
     * إنشاء audit records عند تحديث النظام
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createSystemUpdateAuditRecords(int systemId, Map<String, Object> oldSystem,
            Map<String, Object> newSystem, String userName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                createSystemUpdateAuditRecords(systemId, oldSystem, newSystem, userName, conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void createSystemUpdateAuditRecords(int systemId, Map<String, Object> oldSystem,
            Map<String, Object> newSystem, String userName, Connection conn) throws SQLException {
        PreparedStatement auditStmt = null;

        try {
            String auditSql = """
                        INSERT INTO System_Audit_history (id, object, event, updateType, field, `from`, `to`, author)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);

            // Short Name (Name)
            String oldName = (String) oldSystem.get("name");
            String newName = (String) newSystem.get("name");
            if (!isEqual(oldName, newName)) {
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Short Name", oldName, newName, userName);
            }

            // Long Name
            String oldLongName = (String) oldSystem.get("longName");
            String newLongName = (String) newSystem.get("longName");
            if (!isEqual(oldLongName, newLongName)) {
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Long Name", oldLongName, newLongName, userName);
            }

            // Description
            String oldDescription = (String) oldSystem.get("description");
            String newDescription = (String) newSystem.get("description");
            if (!isEqual(oldDescription, newDescription)) {
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Description", oldDescription, newDescription, userName);
            }

            // Asset ID
            String oldAssetId = (String) oldSystem.get("assetId");
            String newAssetId = (String) newSystem.get("assetId");
            if (!isEqual(oldAssetId, newAssetId)) {
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Asset Id", oldAssetId, newAssetId, userName);
            }

            // URL (URI)
            String oldUrl = (String) oldSystem.get("url");
            String newUrl = (String) newSystem.get("url");
            if (!isEqual(oldUrl, newUrl)) {
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "URI", oldUrl, newUrl, userName);
            }

            // External
            Integer oldExternalInt = getIntValue(oldSystem.get("external"));
            Integer newExternalInt = getIntValue(newSystem.get("external"));
            if (!isEqual(oldExternalInt, newExternalInt)) {
                String oldExternalStr = oldExternalInt != null && oldExternalInt == 1 ? "Yes" : "No";
                String newExternalStr = newExternalInt != null && newExternalInt == 1 ? "Yes" : "No";
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "External", oldExternalStr, newExternalStr, userName);
            }

            // DQ Automation
            Integer oldDqAutomationInt = getIntValue(oldSystem.get("dqAutomation"));
            Integer newDqAutomationInt = getIntValue(newSystem.get("dqAutomation"));
            if (!isEqual(oldDqAutomationInt, newDqAutomationInt)) {
                String oldDqStr = oldDqAutomationInt != null && oldDqAutomationInt == 1 ? "Yes" : "No";
                String newDqStr = newDqAutomationInt != null && newDqAutomationInt == 1 ? "Yes" : "No";
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Automatically Control Local Rules", oldDqStr, newDqStr, userName);
            }

            // Parent System
            Integer oldParentId = null;
            if (oldSystem.get("hierarchy") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> hierarchy = (Map<String, Object>) oldSystem.get("hierarchy");
                oldParentId = getIntValue(hierarchy.get("parentId"));
            }
            Integer newParentId = getIntValue(newSystem.get("parentId"));
            if (!isEqual(oldParentId, newParentId)) {
                String oldParentName = oldParentId != null ? getSystemName(conn, oldParentId) : null;
                String newParentName = newParentId != null ? getSystemName(conn, newParentId) : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Parent Short Name", oldParentName, newParentName, userName);
            }

            // BUDG Viewing (is_Public)
            Integer oldIsPublic = getIntValue(oldSystem.get("isPublic"));
            Integer newIsPublic = getIntValue(newSystem.get("isPublic"));
            if (!isEqual(oldIsPublic, newIsPublic)) {
                String oldViewingName = oldIsPublic != null ? getLookupName(conn, "viewing", oldIsPublic) : null;
                String newViewingName = newIsPublic != null ? getLookupName(conn, "viewing", newIsPublic) : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Status Change", "BUDG Viewing", oldViewingName, newViewingName, userName);
            }

            // BUDG Status
            Integer oldStatus = getIntValue(oldSystem.get("status"));
            Integer newStatus = getIntValue(newSystem.get("status"));
            if (!isEqual(oldStatus, newStatus)) {
                String oldStatusName = oldStatus != null ? getLookupName(conn, "status", oldStatus) : null;
                String newStatusName = newStatus != null ? getLookupName(conn, "status", newStatus) : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Status Change", "BUDG Status", oldStatusName, newStatusName, userName);
            }

            // Lifecycle
            Integer oldLifecycle = getIntValue(oldSystem.get("lifecycle"));
            Integer newLifecycle = getIntValue(newSystem.get("lifecycle"));
            if (!isEqual(oldLifecycle, newLifecycle)) {
                String oldLifecycleName = oldLifecycle != null ? getLookupName(conn, "system_lifecycle", oldLifecycle)
                        : null;
                String newLifecycleName = newLifecycle != null ? getLookupName(conn, "system_lifecycle", newLifecycle)
                        : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Status Change", "Lifecycle", oldLifecycleName, newLifecycleName, userName);
            }

            // Type
            Integer oldType = getIntValue(oldSystem.get("Type"));
            Integer newType = getIntValue(newSystem.get("Type"));
            if (!isEqual(oldType, newType)) {
                String oldTypeName = oldType != null ? getLookupName(conn, "system_type", oldType) : null;
                String newTypeName = newType != null ? getLookupName(conn, "system_type", newType) : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Type", oldTypeName, newTypeName, userName);
            }

            // Classification
            Integer oldClassification = getIntValue(oldSystem.get("classification"));
            Integer newClassification = getIntValue(newSystem.get("classification"));
            if (!isEqual(oldClassification, newClassification)) {
                String oldClassificationName = oldClassification != null
                        ? getLookupName(conn, "system_classification", oldClassification)
                        : null;
                String newClassificationName = newClassification != null
                        ? getLookupName(conn, "system_classification", newClassification)
                        : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Status Change", "Classification", oldClassificationName, newClassificationName, userName);
            }

            // Confidentiality Rating
            Integer oldConfRating = getIntValue(oldSystem.get("confidentialityRating"));
            Integer newConfRating = getIntValue(newSystem.get("confidentialityRating"));
            if (!isEqual(oldConfRating, newConfRating)) {
                String oldConfValue = oldConfRating != null ? getCiaRatingValue(conn, oldConfRating) : null;
                String newConfValue = newConfRating != null ? getCiaRatingValue(conn, newConfRating) : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Confidentiality", oldConfValue, newConfValue, userName);
            }

            // Integrity Rating
            Integer oldIntegRating = getIntValue(oldSystem.get("integrityRating"));
            Integer newIntegRating = getIntValue(newSystem.get("integrityRating"));
            if (!isEqual(oldIntegRating, newIntegRating)) {
                String oldIntegValue = oldIntegRating != null ? getCiaRatingValue(conn, oldIntegRating) : null;
                String newIntegValue = newIntegRating != null ? getCiaRatingValue(conn, newIntegRating) : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Integrity", oldIntegValue, newIntegValue, userName);
            }

            // Availability Rating
            Integer oldAvailRating = getIntValue(oldSystem.get("availabilityRating"));
            Integer newAvailRating = getIntValue(newSystem.get("availabilityRating"));
            if (!isEqual(oldAvailRating, newAvailRating)) {
                String oldAvailValue = oldAvailRating != null ? getCiaRatingValue(conn, oldAvailRating) : null;
                String newAvailValue = newAvailRating != null ? getCiaRatingValue(conn, newAvailRating) : null;
                createUpdateAuditRecord(conn, auditStmt, systemId, "System", "Details",
                        "Updated", "Availability", oldAvailValue, newAvailValue, userName);
            }

        } finally {
            if (auditStmt != null)
                auditStmt.close();
        }
    }

    /**
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt,
            int systemId, String object, String event, String updateType,
            String field, String fromValue, String toValue, String userName) throws SQLException {

        // Skip recording if values are effectively the same (e.g., "No" to "No")
        if (isEqual(fromValue, toValue)) {
            // system.out.println(" ⏭️ SKIPPED: [" + field + "] - No change detected ('" +
            // fromValue + "' to '" + toValue + "')");
            return -1; // Don't create audit record for no-change scenarios
        }

        // system.out.println(" 📝 Update audit: [" + field + "] from '" + fromValue +
        // "' to '" + toValue + "'");

        auditStmt.setInt(1, systemId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue); // from
        auditStmt.setString(7, toValue); // to
        auditStmt.setString(8, userName);

        auditStmt.executeUpdate();
        // system.out.println(" ✓ Update audit record inserted");

        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Method لإنشاء snapshot جديد في system_audit عند الـ update
     */
    public void createSystemUpdateAuditSnapshot(int systemId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            createSystemUpdateAuditSnapshot(systemId, conn);
        }
    }

    public void createSystemUpdateAuditSnapshot(int systemId, Connection conn) throws SQLException {
        // system.out.println("🔍 SystemDAO.createSystemUpdateAuditSnapshot - Creating
        // update snapshot for ID: " + systemId);
        String sql = """
                    INSERT INTO system_audit (
                        id, parent_id, is_Public, status, Lifecycle, Type, Classification,
                        Confidentiality_Rating, Integrity_Rating, Availability_Rating,
                        Name, Long_Name, AssetID, External, Descritpion, Created_Datetime,
                        Last_Updated_Datetime, URL, DQ_Automation, CreatedBy_ID, Last_updated_UserID, rev_type
                    )
                    SELECT
                        id, parent_id, is_Public, status, Lifecycle, Type, Classification,
                        Confidentiality_Rating, Integrity_Rating, Availability_Rating,
                        Name, Long_Name, AssetID, External, Description, Created_Datetime,
                        Last_Updated_Datetime, URL, DQ_Automation, CreatedBy_ID, Last_updated_UserID, 'Updated'
                    FROM system
                    WHERE id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null)
            return true;
        if (obj1 == null || obj2 == null)
            return false;

        // Handle Boolean vs Integer comparison (for database boolean fields)
        // External field can be Boolean from DB or Integer from form
        if ((obj1 instanceof Boolean || obj1 instanceof Integer) &&
                (obj2 instanceof Boolean || obj2 instanceof Integer)) {

            int val1 = 0;
            int val2 = 0;

            if (obj1 instanceof Boolean) {
                val1 = ((Boolean) obj1) ? 1 : 0;
            } else if (obj1 instanceof Integer) {
                val1 = (Integer) obj1;
            }

            if (obj2 instanceof Boolean) {
                val2 = ((Boolean) obj2) ? 1 : 0;
            } else if (obj2 instanceof Integer) {
                val2 = (Integer) obj2;
            }

            return val1 == val2;
        }

        // Handle Integer comparison
        if (obj1 instanceof Integer && obj2 instanceof Integer) {
            return obj1.equals(obj2);
        }

        // Handle String comparison (case-insensitive and trim)
        if (obj1 instanceof String && obj2 instanceof String) {
            String str1 = ((String) obj1).trim();
            String str2 = ((String) obj2).trim();

            // Handle empty strings as equivalent
            if (str1.isEmpty() && str2.isEmpty())
                return true;

            // Handle common boolean-like values that shouldn't be recorded as changes
            if (str1.equalsIgnoreCase("No") && str2.equalsIgnoreCase("No"))
                return true;
            if (str1.equalsIgnoreCase("Yes") && str2.equalsIgnoreCase("Yes"))
                return true;
            if (str1.equalsIgnoreCase("False") && str2.equalsIgnoreCase("False"))
                return true;
            if (str1.equalsIgnoreCase("True") && str2.equalsIgnoreCase("True"))
                return true;
            if (str1.equalsIgnoreCase("0") && str2.equalsIgnoreCase("0"))
                return true;
            if (str1.equalsIgnoreCase("1") && str2.equalsIgnoreCase("1"))
                return true;

            // Handle null-like values
            if (str1.equalsIgnoreCase("null") && str2.equalsIgnoreCase("null"))
                return true;
            if (str1.equalsIgnoreCase("none") && str2.equalsIgnoreCase("none"))
                return true;

            return str1.equalsIgnoreCase(str2);
        }

        // Default comparison
        return obj1.equals(obj2);
    }

    /**
     * Helper method لتحويل Object إلى Integer
     * يتعامل مع Boolean، Integer، Number، وString
     */
    private Integer getIntValue(Object obj) {
        if (obj == null)
            return null;

        // Handle Boolean (TINYINT from MySQL returns Boolean via rs.getObject())
        if (obj instanceof Boolean) {
            return ((Boolean) obj) ? 1 : 0;
        }

        if (obj instanceof Integer)
            return (Integer) obj;
        if (obj instanceof Number)
            return ((Number) obj).intValue();

        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * حذف النظام مع تسجيل audit records
     */
    public boolean deleteSystemWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE system SET Deleted_datetime = NOW() WHERE id = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();

            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                            INSERT INTO system_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                            VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                        """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "System");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "System");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();

                // الخطوة 3: إنشاء snapshot في system_audit
                String snapshotSql = """
                            INSERT INTO system_audit (
                                id, parent_id, is_Public, status, Lifecycle, Type, Classification,
                                Confidentiality_Rating, Integrity_Rating, Availability_Rating,
                                Name, Long_Name, AssetID, External, Descritpion, Created_Datetime,
                                Last_Updated_Datetime, URL, DQ_Automation, CreatedBy_ID, Last_updated_UserID, rev_type
                            )
                            SELECT
                                id, parent_id, is_Public, status, Lifecycle, Type, Classification,
                                Confidentiality_Rating, Integrity_Rating, Availability_Rating,
                                Name, Long_Name, AssetID, External, Description, Created_Datetime,
                                Last_Updated_Datetime, URL, DQ_Automation, CreatedBy_ID, Last_updated_UserID, 'Deleted'
                            FROM system
                            WHERE id = ?
                        """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }

                // system.out.println("✅ System deleted with audit for ID: " + id);
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
                if (deleteStmt != null)
                    deleteStmt.close();
                if (auditStmt != null)
                    auditStmt.close();
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
     * Get the segment ID for a system
     * 
     * @param systemId The system ID
     * @return The segment ID or null if not assigned
     */
    /**
     * Get segment info for a system (same pattern as DatasetDAO.getDatasetSegment)
     * 
     * @param systemId The system ID
     * @return Map with segmentId and segmentName (null/"Not Specified" if not
     *         assigned)
     */
    private Map<String, Object> getSystemSegment(int systemId) throws SQLException {
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
                    AND sot.Type = 'System'
                    AND sxr.Deleted_At IS NULL
                    AND s.Deleted_At IS NULL
                    LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("segmentId", rs.getInt("segment_id"));
                    result.put("segmentName", rs.getString("segment_name"));
                }
            }
        }

        return result;
    }

}
