package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.repository.PersonFollowRepository;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.UserContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Supplies data for the Activity Stream tab.
 */
@WebServlet("/api/people/activity/*")
public class PersonActivityServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;


    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Person ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int personId = Integer.parseInt(pathInfo.substring(1));
            
            // Get current user ID for segment access checking
            Integer currentUserId = getCurrentUserId(request);
            
            Map<String, Object> payload = loadActivityData(personId, currentUserId);

            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), payload);
        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid person ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
            sendError(response, "Database error: " + sqlEx.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get current user ID from request (via JWT token)
     */
    private Integer getCurrentUserId(HttpServletRequest request) {
        // Try request attribute first (set by AuthFilter)
        Integer userId = UserContextUtil.getCurrentUserIdOrNull(request);
        if (userId != null) {
            return userId;
        }
        
        // Fallback: get from JWT token in cookie
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                return com.example.budg_v2.util.JwtUtil.getUserIdFromToken(token);
            }
        } catch (Exception e) {
            // Ignore - will return null
        }
        return null;
    }
    
    /**
     * Get cookie value by name
     */
    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private Map<String, Object> loadActivityData(int personId, Integer currentUserId) throws SQLException {
        Map<String, Object> payload = new HashMap<>();
        
        // Get current user's segment access info
        Set<Integer> accessibleSegments = null;
        boolean isSuperAdmin = false;
        
        if (currentUserId != null) {
            try {
                isSuperAdmin = SegmentAccessService.isSuperAdmin(currentUserId);
                if (!isSuperAdmin) {
                    accessibleSegments = SegmentAccessService.getAccessibleSegmentIds(currentUserId);
                }
            } catch (SQLException e) {
                System.err.println("Error checking segment access: " + e.getMessage());
            }
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            payload.put("notification", fetchNotificationFrequency(conn, personId));
            List<Map<String, Object>> stakeholderRecords = fetchStakeholderActivity(conn, personId);
            
            // Apply segment access masking to stakeholder records
            applySegmentMasking(conn, stakeholderRecords, isSuperAdmin, accessibleSegments);
            payload.put("stakeholder", stakeholderRecords);
        }

        Map<String, Object> followData = PersonFollowRepository.getFollowingData(personId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allFollowRecords = (List<Map<String, Object>>) followData.getOrDefault("records", List.of());
        // Include ALL followed objects. Objects whose lastUpdated is null (e.g. never edited
        // through the system) are still valid follows and must not be silently dropped.
        List<Map<String, Object>> updatedFollowRecords = new ArrayList<>(allFollowRecords);

        // Apply segment access masking to following records
        try (Connection conn = DatabaseConnection.getConnection()) {
            applySegmentMasking(conn, updatedFollowRecords, isSuperAdmin, accessibleSegments);
        }
        
        payload.put("following", updatedFollowRecords);
        // Rebuild facets for filtered records
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Map<String, Object> rec : updatedFollowRecords) {
            String type = String.valueOf(rec.getOrDefault("type", "Other"));
            counts.merge(type, 1, (a, b) -> (a != null ? a : 0) + (b != null ? b : 0));
        }
        List<Map<String, Object>> filteredFacets = new ArrayList<>();
        filteredFacets.add(Map.of("type", "All", "count", updatedFollowRecords.size()));
        counts.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> filteredFacets.add(Map.of("type", e.getKey(), "count", e.getValue())));
        payload.put("followingFacets", filteredFacets);

        return payload;
    }
    
    /**
     * Apply segment access masking to records.
     * If user doesn't have access to an object's segment, mask the name as "XXXXXXX"
     */
    private void applySegmentMasking(Connection conn, List<Map<String, Object>> records, 
                                      boolean isSuperAdmin, Set<Integer> accessibleSegments) {
        if (isSuperAdmin) {
            // Super Admin sees all - no masking needed
            for (Map<String, Object> record : records) {
                record.put("isMasked", false);
                record.put("hasSegmentAccess", true);
            }
            return;
        }
        
        for (Map<String, Object> record : records) {
            try {
                String objectType = (String) record.get("type");
                Integer objectId = (Integer) record.get("objectId");
                
                if (objectType == null || objectId == null) {
                    record.put("isMasked", false);
                    record.put("hasSegmentAccess", true);
                    continue;
                }
                
                // Get the segment ID for this object
                int segmentId = getObjectSegmentId(conn, objectId, objectType);
                record.put("segmentId", segmentId);
                
                // Check if user has access
                // Enterprise segment (ID=1) is accessible to all
                boolean hasAccess = segmentId == 1 || 
                                   (accessibleSegments != null && accessibleSegments.contains(segmentId));
                
                if (!hasAccess) {
                    // Mask the object name
                    record.put("name", "XXXXXXX");
                    record.put("isMasked", true);
                    record.put("hasSegmentAccess", false);
                } else {
                    record.put("isMasked", false);
                    record.put("hasSegmentAccess", true);
                }
            } catch (Exception e) {
                // On error, don't mask
                record.put("isMasked", false);
                record.put("hasSegmentAccess", true);
            }
        }
    }
    
    /**
     * Get the segment ID for an object
     */
    private int getObjectSegmentId(Connection conn, int objectId, String objectType) {
        // Map object type to segment_object_type.Type value
        String segmentObjectType = mapObjectTypeToSegmentType(objectType);
        if (segmentObjectType == null) {
            return 1; // Default to Enterprise
        }
        
        String sql = """
            SELECT sxr.Segment_ID
            FROM segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            WHERE orr.Object_ID = ?
            AND sot.Type = ?
            AND sxr.Deleted_At IS NULL
            LIMIT 1
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            ps.setString(2, segmentObjectType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Segment_ID");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting segment ID for " + objectType + " " + objectId + ": " + e.getMessage());
        }
        
        // Default to Enterprise if no segment assigned
        return 1;
    }
    
    /**
     * Map object type name to segment_object_type.Type value
     */
    private String mapObjectTypeToSegmentType(String objectType) {
        if (objectType == null) return null;
        
        return switch (objectType.toLowerCase()) {
            case "glossary" -> "Glossary";
            case "dataset", "data set" -> "Dataset";
            case "system" -> "System";
            case "interface", "system interface", "systeminterface" -> "SystemInterface";
            case "process" -> "Process";
            case "policy" -> "Policy";
            case "product" -> "Product";
            case "project" -> "Project";
            case "business area", "businessarea" -> "BusinessArea";
            case "client" -> "Client";
            case "committee" -> "Committee";
            case "legal entity", "legalentity" -> "LegalEntity";
            case "capability" -> "Capability";
            case "regulation" -> "Regulation";
            case "attribute" -> "Attribute";
            case "geography" -> "Geography";
            case "regulator" -> "Regulator";
            case "regulatory theme", "regulatorytheme" -> "RegulatoryTheme";
            default -> null;
        };
    }

    private Map<String, Object> fetchNotificationFrequency(Connection conn, int personId) throws SQLException {
        final String sql = """
            SELECT nf.PrimaryName,
                   nf.Last_UpdateDatetime,
                   nf.Last_updateUserID,
                   p.First_Name,
                   p.Last_Name
            FROM note_frequency nf
            LEFT JOIN people p ON nf.Last_updateUserID = p.ID
            WHERE nf.Last_updateUserID = ?
            ORDER BY nf.Last_UpdateDatetime DESC
            LIMIT 1
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("value", safeTrim(rs.getString("PrimaryName")));
                    result.put("updatedAt", toIso(rs.getTimestamp("Last_UpdateDatetime")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    result.put("updatedBy", updater.isEmpty() ? null : updater);
                    return result;
                }
            }
        }

        return null;
    }

    private List<Map<String, Object>> fetchStakeholderActivity(Connection conn, int personId) throws SQLException {
        List<Map<String, Object>> records = new ArrayList<>();

        // Step 1: Get objectXipid and Role IDs from object_x_people for the person
        List<Map<String, Object>> personRoles = getPersonObjectRoles(conn, personId);
        
        // Step 2: Get module IDs from object_role using the Role IDs
        Map<Integer, String> moduleMap = getModuleNames(conn, personRoles);
        
        // Step 3: Process each module type for all facets
        for (Map<String, Object> personRole : personRoles) {
            Integer objectXipid = (Integer) personRole.get("objectXipid");
            Integer roleId = (Integer) personRole.get("roleId");
            String moduleName = moduleMap.get(roleId);
            
            if (moduleName != null) {
                switch (moduleName) {
                    case "Glossary":
                        processGlossaryStakeholder(conn, objectXipid, records);
                        break;
                    case "Dataset":
                        processDatasetStakeholder(conn, objectXipid, records);
                        break;
                    case "System":
                        processSystemStakeholder(conn, objectXipid, records);
                        break;
                    case "Interface":
                        processInterfaceStakeholder(conn, objectXipid, records);
                        break;
                    case "Business Area":
                        processBusinessAreaStakeholder(conn, objectXipid, records);
                        break;
                    case "Capability":
                        processCapabilityStakeholder(conn, objectXipid, records);
                        break;
                    case "Client":
                        processClientStakeholder(conn, objectXipid, records);
                        break;
                    case "Legal Entity":
                        processLegalEntityStakeholder(conn, objectXipid, records);
                        break;
                    case "Product":
                        processProductStakeholder(conn, objectXipid, records);
                        break;
                    case "Policy":
                        processPolicyStakeholder(conn, objectXipid, records);
                        break;
                    case "Process":
                        processProcessStakeholder(conn, objectXipid, records);
                        break;
                    case "Project":
                        processProjectStakeholder(conn, objectXipid, records);
                        break;
                    case "Committee":
                        processCommitteeStakeholder(conn, objectXipid, records);
                        break;
                    case "Attribute":
                        processAttributeStakeholder(conn, objectXipid, records);
                        break;
                }
            }
        }
        return records;
    }

                private String toIso(Timestamp timestamp) {
        if (timestamp == null) return null;
        return timestamp.toInstant().atOffset(ZoneOffset.UTC).toString();
    }

    private String buildFullName(String firstName, String lastName) {
        String first = safeTrim(firstName);
        String last = safeTrim(lastName);
        return (first + " " + last).trim();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Check if events map has any non-zero counts
     */
    private boolean hasEvents(Map<String, Integer> events) {
        if (events == null) return false;
        return events.getOrDefault("detailsAdded", 0) > 0 ||
               events.getOrDefault("detailsUpdated", 0) > 0 ||
               events.getOrDefault("detailsDeleted", 0) > 0 ||
               events.getOrDefault("relationshipsAdded", 0) > 0 ||
               events.getOrDefault("relationshipsUpdated", 0) > 0 ||
               events.getOrDefault("relationshipsDeleted", 0) > 0;
    }

    /**
     * Calculate event counts from audit history for the Activity Stream Events column.
     *
     * Logic:
     * - Object details: object field matches the module name (e.g., "Glossary" for Glossary module)
     *   → Counts as Details Added / Updated / Deleted.
     * - Relationships: ANY other object value (e.g., "Stakeholders", "Dependencies",
     *   "Object X ...") → Counts as Relationships Added / Updated / Deleted.
     *
     * Buckets (by updateType) differ slightly between details and relationships:
     * - Details:       Added = "Added"/"Status Change"; Updated = "Updated"/"Modified".
     *   (a creation-time status/lifecycle value is counted as Added)
     * - Relationships: Added = "Added"; Updated = "Updated"/"Modified"/"Status Change"/"Accepted".
     *   (changing or accepting the status of an EXISTING link is an update, not a new link)
     * - Deleted = "Deleted" for both.
     *
     * Notes:
     * - Only changes on/after the stakeholder link date (stakeholderSince) are counted.
     *   When stakeholderSince is null, full object history is counted.
     * - Every audit row counts as exactly one event (no de-duplication, no
     *   no-change/blank filtering) so the summary reconciles 1:1 with the full
     *   history detail table the user expands (see HistoryAuditServlet, which
     *   returns every row for the object).
     */
    private Map<String, Integer> calculateEvents(Connection conn, String moduleName, int objectId, Timestamp stakeholderSince) throws SQLException {
        Map<String, Integer> events = new HashMap<>();
        events.put("detailsAdded", 0);
        events.put("detailsUpdated", 0);
        events.put("detailsDeleted", 0);
        events.put("relationshipsAdded", 0);
        events.put("relationshipsUpdated", 0);
        events.put("relationshipsDeleted", 0);

        String auditTable = getAuditTableName(moduleName);
        if (auditTable == null) {
            return events;
        }

        // Check if table exists
        String checkTableSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        boolean tableExists = false;
        try (PreparedStatement ps = conn.prepareStatement(checkTableSql)) {
            ps.setString(1, auditTable);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    tableExists = true;
                }
            }
        }

        if (!tableExists) {
            return events;
        }

        // Query audit history for this object, restricted to changes since the user
        // became a stakeholder of it (Rule 1: timestamp >= linked_at / stakeholderSince).
        // We intentionally do NOT filter out from=to / blank rows here: the history
        // detail table (HistoryAuditServlet) shows every row, and the Events summary
        // must reconcile 1:1 with it.
        String sql = "SELECT `object`, `updateType`, `date` FROM `" + auditTable + "` WHERE `id` = ?";
        // Only count changes that happened on/after the stakeholder link date.
        // When stakeholderSince is null we have no link date, so count full history.
        // Rows whose `date` is NULL are kept: several audit inserts (stakeholder links,
        // legacy/DAO writes) omit the `date` column, and a "since" filter cannot
        // meaningfully exclude a change whose timestamp is unknown.
        sql += " AND (? IS NULL OR `date` IS NULL OR `date` >= ?)";
        sql += " ORDER BY `date` DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            ps.setTimestamp(2, stakeholderSince);
            ps.setTimestamp(3, stakeholderSince);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objectField = rs.getString("object");
                    String updateType = rs.getString("updateType");
                    
                    if (objectField == null) continue;
                    
                    // Normalize module name for comparison
                    String normalizedModuleName = moduleName.trim();
                    
                    // Classify the row (Rules 2 & 3):
                    //  - Details:      object field matches the module name (e.g., "Capability")
                    //  - Relationship: ANY other object value (e.g., "Stakeholders",
                    //                  "Dependencies", "Capability X Process"). Anything that
                    //                  is not the main object type counts as a relationship.
                    String objectFieldTrim = objectField.trim();
                    boolean isDetails = objectFieldTrim.equalsIgnoreCase(normalizedModuleName);
                    boolean isRelationship = !isDetails;
                    
                    // Determine the operation bucket (Rule 4). "Deleted" is the same
                    // for both; Added/Updated differ between details and relationships.
                    String ut = updateType == null ? "" : updateType.trim();
                    boolean isDeleted = "Deleted".equalsIgnoreCase(ut);
                    
                    if (isDetails) {
                        // Object details changes (when object field = module name).
                        // Counted one event per audit row.
                        //  - Added bucket   = "Added" or "Status Change"
                        //    (a creation-time status/lifecycle value is part of "Added")
                        //  - Updated bucket = "Updated" or "Modified"
                        boolean isAdded = "Added".equalsIgnoreCase(ut) ||
                                          "Status Change".equalsIgnoreCase(ut);
                        boolean isUpdated = "Updated".equalsIgnoreCase(ut) ||
                                            "Modified".equalsIgnoreCase(ut);
                        if (isAdded) {
                            events.put("detailsAdded", events.get("detailsAdded") + 1);
                        } else if (isUpdated) {
                            events.put("detailsUpdated", events.get("detailsUpdated") + 1);
                        } else if (isDeleted) {
                            events.put("detailsDeleted", events.get("detailsDeleted") + 1);
                        }
                    } else if (isRelationship) {
                        // Relationship rows (Stakeholder, Dependencies, cross-facet links, ...).
                        // Counted one event per audit row, like details.
                        //  - Added bucket   = "Added" (a brand-new relationship link)
                        //  - Updated bucket = "Updated"/"Modified" plus "Status Change"
                        //    and "Accepted": changing/accepting the status of an
                        //    EXISTING relationship is an update, not a new link.
                        boolean isAdded = "Added".equalsIgnoreCase(ut);
                        boolean isUpdated = "Updated".equalsIgnoreCase(ut) ||
                                            "Modified".equalsIgnoreCase(ut) ||
                                            "Status Change".equalsIgnoreCase(ut) ||
                                            "Accepted".equalsIgnoreCase(ut);
                        if (isAdded) {
                            events.put("relationshipsAdded", events.get("relationshipsAdded") + 1);
                        } else if (isUpdated) {
                            events.put("relationshipsUpdated", events.get("relationshipsUpdated") + 1);
                        } else if (isDeleted) {
                            events.put("relationshipsDeleted", events.get("relationshipsDeleted") + 1);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // If table doesn't exist or query fails, return empty events
            System.err.println("Error calculating events for " + auditTable + ": " + e.getMessage());
        }

        return events;
    }

    /**
     * Get audit table name from module name
     */
    private String getAuditTableName(String moduleName) {
        if (moduleName == null) return null;
        
        // Map module names to audit table names
        switch (moduleName.toLowerCase()) {
            case "glossary":
                return "glossary_audit_history";
            case "dataset":
            case "data sets":
                return "dataset_audit_history";
            case "system":
                return "system_audit_history";
            case "interface":
            case "systeminterface":
                return "interface_audit_history";
            case "process":
            case "processes":
                return "process_audit_history";
            case "project":
            case "projects":
                return "project_audit_history";
            case "product":
            case "products":
                return "product_audit_history";
            case "policy":
            case "policies":
                return "policy_audit_history";
            case "business area":
            case "businessarea":
                return "business_area_audit_history";
            case "capability":
                return "capability_audit_history";
            case "client":
                return "client_audit_history";
            case "committee":
                return "committee_audit_history";
            case "legal entity":
            case "legalentity":
            case "legal":
                return "legal_audit_history";
            case "attribute":
            case "attributes":
                return "attribute_audit_history";
            case "regulation":
                return "regulation_audit_history";
            default:
                return null;
        }
    }

    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), error);
    }

    // Step 1: Get objectXipid and Role IDs from object_x_people for the person
    private List<Map<String, Object>> getPersonObjectRoles(Connection conn, int personId) throws SQLException {
        List<Map<String, Object>> personRoles = new ArrayList<>();
        
        String sql = """
            SELECT id as objectXipid, roleID as roleId
            FROM object_x_people 
            WHERE ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> role = new HashMap<>();
                    role.put("objectXipid", rs.getInt("objectXipid"));
                    role.put("roleId", rs.getInt("roleId"));
                    personRoles.add(role);
                }
            }
        }
        
        return personRoles;
    }

    // Step 2: Get module names from object_role and module tables
    private Map<Integer, String> getModuleNames(Connection conn, List<Map<String, Object>> personRoles) throws SQLException {
        Map<Integer, String> moduleMap = new HashMap<>();
        
        if (personRoles.isEmpty()) {
            return moduleMap;
        }
        
        // Build IN clause for role IDs
        StringBuilder roleIds = new StringBuilder();
        for (int i = 0; i < personRoles.size(); i++) {
            if (i > 0) roleIds.append(",");
            roleIds.append("?");
        }
        
        String sql = "SELECT orole.id as roleId, m.primaryname as moduleName " +
                    "FROM object_role orole " +
                    "JOIN module m ON orole.module = m.id " +
                    "WHERE orole.id IN (" + roleIds.toString() + ")";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            for (Map<String, Object> personRole : personRoles) {
                ps.setInt(paramIndex++, (Integer) personRole.get("roleId"));
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    moduleMap.put(rs.getInt("roleId"), rs.getString("moduleName"));
                }
            }
        }
        
        return moduleMap;
    }

    // Steps 4-6: Process Glossary stakeholder data
    private void processGlossaryStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                g.ID AS object_id,
                g.Ref_Number AS reference,
                g.Name AS name,
                g.Description AS description,
                g.Last_Updated_Datetime AS last_updated,
                g.Last_updated_userID AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                gxop.CreateDatetime AS stakeholder_since
            FROM glossary_x_objectxpeople gxop
            JOIN glossary g ON gxop.GlossaryID = g.ID
            LEFT JOIN people p ON g.Last_updated_userID = p.ID
            WHERE gxop.Object_x_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Glossary");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Glossary", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Dataset stakeholder data
    private void processDatasetStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                d.ID AS object_id,
                d.RefNumber AS reference,
                d.PrimaryName AS name,
                d.definition AS description,
                d.LastUpdateDatetime AS last_updated,
                d.LastUpdateUser_id AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                dxop.CreateDatetime AS stakeholder_since
            FROM dataset_x_objectxpeople dxop
            JOIN dataset d ON dxop.Dataset_ID = d.ID
            LEFT JOIN people p ON d.LastUpdateUser_id = p.ID
            WHERE dxop.Object_x_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Dataset");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Dataset", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process System stakeholder data
    private void processSystemStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                s.id AS object_id,
                s.AssetID AS reference,
                s.Name AS name,
                s.Description AS description,
                s.Last_Updated_Datetime AS last_updated,
                s.Last_updated_UserID AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                sxop.CreateDatetime AS stakeholder_since
            FROM system_x_objectxpeople sxop
            JOIN system s ON sxop.SystemID = s.id
            LEFT JOIN people p ON s.Last_updated_UserID = p.ID
            WHERE sxop.Object_x_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "System");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "System", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Interface stakeholder data
    private void processInterfaceStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                i.id AS object_id,
                i.Ref_number AS reference,
                i.Name AS name,
                i.Description AS description,
                i.last_updatedtime AS last_updated,
                i.last_updateuser_id AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                ixop.CreateDatetime AS stakeholder_since
            FROM interface_x_objectxpeople ixop
            JOIN interface i ON ixop.InterfaceID = i.id
            LEFT JOIN people p ON i.last_updateuser_id = p.ID
            WHERE ixop.Object_x_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Interface");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Interface", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Business Area stakeholder data
    private void processBusinessAreaStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                ba.ID AS object_id,
                CONCAT('BA-', ba.ID) AS reference,
                ba.PrimaryName AS name,
                ba.Description AS description,
                ba.LastUpdateDatetime AS last_updated,
                ba.LastUpdate_UserID AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                baxop.CreateDatetime AS stakeholder_since
            FROM businessarea_x_objectxpeople baxop
            JOIN business_area ba ON baxop.BusinessAreaID = ba.ID
            LEFT JOIN people p ON ba.LastUpdate_UserID = p.ID
            WHERE baxop.Object_x_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Business Area");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Business Area", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Capability stakeholder data
    private void processCapabilityStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                c.ID AS object_id,
                c.RefNumber AS reference,
                c.PrimaryName AS name,
                c.Description AS description,
                c.LastUpdateDatetime AS last_updated,
                c.LastUpdateUser_ID AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                cxop.CreateDatetime AS stakeholder_since
            FROM capability_x_objectxpeople cxop
            JOIN capability c ON cxop.CapabilityID = c.ID
            LEFT JOIN people p ON c.LastUpdateUser_ID = p.ID
            WHERE cxop.Object_x_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Capability");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Capability", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Client stakeholder data
    private void processClientStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                c.ID AS object_id,
                CONCAT('CLIENT-', c.ID) AS reference,
                c.PrimaryName AS name,
                c.Description AS description,
                c.LastUpdateDatetime AS last_updated,
                c.LastUpdate_UserID AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                cxop.CreateDatetime AS stakeholder_since
            FROM client_x_objectxpeople cxop
            JOIN client c ON cxop.ClientID = c.ID
            LEFT JOIN people p ON c.LastUpdate_UserID = p.ID
            WHERE cxop.Object_x_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Client");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Client", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Legal Entity stakeholder data
    private void processLegalEntityStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                l.ID AS object_id,
                l.ShortName AS reference,
                l.LongName AS name,
                l.Description AS description,
                l.LastUpdateDatetime AS last_updated,
                l.LastUpdate_UserID AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                lxop.CreateDatetime AS stakeholder_since
            FROM legal_x_objectxpeople lxop
            JOIN legal l ON lxop.Legal_ID = l.ID
            LEFT JOIN people p ON l.LastUpdate_UserID = p.ID
            WHERE lxop.Object_X_IP = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Legal Entity");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Legal Entity", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Product stakeholder data
    private void processProductStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                p.id AS object_id,
                p.refnumber AS reference,
                p.primaryname AS name,
                p.description AS description,
                p.lastupdatedatetime AS last_updated,
                p.lastupdate_userid AS last_update_user_id,
                pe.First_Name,
                pe.Last_Name,
                pxop.CreateDatetime AS stakeholder_since
            FROM product_x_objectxpeople pxop
            JOIN product p ON pxop.product_id = p.id
            LEFT JOIN people pe ON p.lastupdate_userid = pe.ID
            WHERE pxop.Object_x_ip = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Product");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Product", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Policy stakeholder data
    private void processPolicyStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                p.ID AS object_id,
                p.refNumber AS reference,
                p.PrimaryName AS name,
                p.Description AS description,
                p.LastUpdateDatetime AS last_updated,
                p.LastUpdateUser_ID AS last_update_user_id,
                pe.First_Name,
                pe.Last_Name,
                pxop.CreateDatetime AS stakeholder_since
            FROM policy_x_objectxpeople pxop
            JOIN policy p ON pxop.Policy_ID = p.ID
            LEFT JOIN people pe ON p.LastUpdateUser_ID = pe.ID
            WHERE pxop.Object_x_ip = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Policy");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Policy", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Process stakeholder data
    private void processProcessStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                pr.id AS object_id,
                pr.refnumber AS reference,
                pr.primaryname AS name,
                pr.description AS description,
                pr.lastupdatedatetime AS last_updated,
                pr.lastupdateuser_id AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                pxop.CreateDatetime AS stakeholder_since
            FROM process_x_objectxpeople pxop
            JOIN process pr ON pxop.process_id = pr.id
            LEFT JOIN people p ON pr.lastupdateuser_id = p.ID
            WHERE pxop.Object_x_ip = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Process");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Process", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Project stakeholder data
    private void processProjectStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                p.id AS object_id,
                p.refnumber AS reference,
                p.primaryname AS name,
                p.description AS description,
                p.lastupdatedatetime AS last_updated,
                p.lastupdateuser_id AS last_update_user_id,
                pe.First_Name,
                pe.Last_Name,
                pxop.CreateDatetime AS stakeholder_since
            FROM project_x_objectxpeople pxop
            JOIN project p ON pxop.project_id = p.id
            LEFT JOIN people pe ON p.lastupdateuser_id = pe.ID
            WHERE pxop.Object_x_ip = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Project");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Project", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Committee stakeholder data
    private void processCommitteeStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                c.ID AS object_id,
                c.RefNumber AS reference,
                c.PrimaryName AS name,
                c.Description AS description,
                c.LastUpdateDatetime AS last_updated,
                c.LastUpdate_UserID AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                cxop.CreateDatetime AS stakeholder_since
            FROM committee_x_objectxpeople cxop
            JOIN committee c ON cxop.Committee_ID = c.ID
            LEFT JOIN people p ON c.LastUpdate_UserID = p.ID
            WHERE cxop.Object_X_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Committee");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Committee", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }

    // Steps 4-6: Process Attribute stakeholder data
    private void processAttributeStakeholder(Connection conn, int objectXipid, List<Map<String, Object>> records) throws SQLException {
        String sql = """
            SELECT 
                a.ID AS object_id,
                a.RefNumber AS reference,
                a.PrimaryName AS name,
                a.Definition AS description,
                a.Last_UpdateDatetime AS last_updated,
                a.Last_UpdatedUser_ID AS last_update_user_id,
                p.First_Name,
                p.Last_Name,
                axop.CreateDatetime AS stakeholder_since
            FROM attribute_x_objectxpeople axop
            JOIN attribute a ON axop.AttributeID = a.ID
            LEFT JOIN people p ON a.Last_UpdatedUser_ID = p.ID
            WHERE axop.Object_x_ipid = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXipid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("type", "Attribute");
                    record.put("objectId", rs.getInt("object_id"));
                    record.put("reference", safeTrim(rs.getString("reference")));
                    record.put("name", safeTrim(rs.getString("name")));
                    record.put("description", safeTrim(rs.getString("description")));
                    record.put("lastUpdated", toIso(rs.getTimestamp("last_updated")));
                    String updater = buildFullName(rs.getString("First_Name"), rs.getString("Last_Name"));
                    record.put("lastUpdatedBy", updater.isEmpty() ? null : updater);
                    
                    // Get timestamp when user became stakeholder
                    Timestamp stakeholderSince = rs.getTimestamp("stakeholder_since");
                    
                    // Store stakeholderSince in record for frontend filtering
                    record.put("stakeholderSince", stakeholderSince != null ? toIso(stakeholderSince) : null);
                    
                    // Calculate events from audit history (only after becoming stakeholder)
                    Map<String, Integer> events = calculateEvents(conn, "Attribute", rs.getInt("object_id"), stakeholderSince);
                    record.put("events", events);
                    
                    // Only add record if there are events (non-zero counts)
                    if (hasEvents(events)) {
                        records.add(record);
                    }
                }
            }
        }
    }


}







