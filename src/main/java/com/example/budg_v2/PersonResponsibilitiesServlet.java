package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
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
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet("/api/responsibilities/*")
public class PersonResponsibilitiesServlet extends HttpServlet {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            // Extract person ID from URL path
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || !pathInfo.startsWith("/")) {
                sendErrorResponse(response, "Invalid person ID", 400);
                return;
            }
            
            // Path format: /{personId}
            String personIdStr = pathInfo.substring(1);
            if (personIdStr.isEmpty()) {
                sendErrorResponse(response, "Person ID is required", 400);
                return;
            }
            
            int personId = Integer.parseInt(personIdStr);
            
            // Get current user ID for segment access checking
            Integer currentUserId = getCurrentUserId(request);
            
            // Get responsibilities for the person with segment access masking
            List<Map<String, Object>> responsibilities = getPersonResponsibilities(personId, currentUserId);
            
            // Send response
            response.setStatus(200);
            objectMapper.writeValue(response.getWriter(), responsibilities);
            
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid person ID format", 400);
        } catch (Exception e) {
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
            e.printStackTrace();
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
    
    /**
     * Get all responsibilities for a person across ALL facets
     * Updated to include ALL 19+ facets from the database
     * Masks object names if current user doesn't have access to the object's segment
     * 
     * @param personId The person whose responsibilities to fetch
     * @param currentUserId The current logged-in user (for segment access checking)
     */
    private List<Map<String, Object>> getPersonResponsibilities(int personId, Integer currentUserId) throws SQLException {
        List<Map<String, Object>> responsibilities = new ArrayList<>();
        // Deduplicate by (object_x_people_id, objectType, objectId).
        // A single assignment record can legitimately link to multiple different objects
        // (e.g., one role-assignment row linked to several clients via client_x_objectxpeople).
        // Using only object_x_people_id as the key incorrectly drops every occurrence
        // after the first, causing fewer results than actually assigned.
        Set<String> seenResponsibilityKeys = new HashSet<>();
        
        // Get current user's accessible segments for masking check
        Set<Integer> accessibleSegments = null;
        boolean isSuperAdmin = false;
        
        if (currentUserId != null) {
            try {
                isSuperAdmin = SegmentAccessService.isSuperAdmin(currentUserId);
                if (!isSuperAdmin) {
                    accessibleSegments = SegmentAccessService.getAccessibleSegmentIds(currentUserId);
                }
            } catch (SQLException e) {
                // On error, default to no masking
                System.err.println("Error checking segment access: " + e.getMessage());
            }
        }
        
        String sql = """
         SELECT DISTINCT
                oxp.id                AS object_x_people_id,
                oxp.RoleID,
                oxp.ipid              AS person_id,
                oxp.statusID,
                oxp.AcceptedID,
                oxp.isDelegateOF,
                oxp.createdatetime,
                oxp.lastupdatedatetime,
                oxp.lastupdateuser_id,

                orole.primaryname     AS role_name,
                orole.description     AS role_description,
                orole.defaultrole,
                orole.objectroletype_id,

                ort.primaryname       AS role_type_name,
                ort.description       AS role_type_description,

                m.primaryname         AS facet_name,
                m.id                  AS facet_id,

                -- Get object name from all possible facets
                COALESCE(
                    d.primaryname,          -- Dataset
                    s.name,                 -- System
                    i.name,                 -- Interface
                    g.name,                 -- Glossary
                    p.primaryname,          -- Process
                    pol.primaryname,        -- Policy
                    prod.primaryname,       -- Product
                    proj.primaryname,       -- Project
                    ba.primaryname,         -- Business Area
                    c.primaryname,          -- Client
                    com.primaryname,        -- Committee
                    le.ShortName,           -- Legal Entity
                    cap.primaryname,        -- Capability
                    reg.primaryname,        -- Regulation
                    a.primaryname           -- Attribute
                ) AS object_name,

                -- Get object ID from all possible facets
                -- First try from main object table, then fallback to linking table (for deleted objects)
                COALESCE(
                    d.ID,                   -- Dataset (from main table)
                    dxoxp.Dataset_ID,      -- Dataset (from linking table - for deleted objects)
                    s.ID,                   -- System (from main table)
                    sxoxp.SystemID,        -- System (from linking table - for deleted objects)
                    i.ID,                   -- Interface (from main table)
                    ixoxp.InterfaceID,     -- Interface (from linking table - for deleted objects)
                    g.ID,                   -- Glossary (from main table)
                    gxoxp.GlossaryID,      -- Glossary (from linking table - for deleted objects)
                    p.ID,                   -- Process (from main table)
                    prcxoxp.process_id,     -- Process (from linking table - for deleted objects)
                    pol.ID,                 -- Policy (from main table)
                    polxoxp.Policy_ID,     -- Policy (from linking table - for deleted objects)
                    prod.ID,                -- Product (from main table)
                    prodxoxp.product_id,   -- Product (from linking table - for deleted objects)
                    proj.ID,                -- Project (from main table)
                    projxoxp.project_id,   -- Project (from linking table - for deleted objects)
                    ba.ID,                  -- Business Area (from main table)
                    baxoxp.BusinessAreaID, -- Business Area (from linking table - for deleted objects)
                    c.ID,                   -- Client (from main table)
                    cxoxp.ClientID,         -- Client (from linking table - for deleted objects)
                    com.ID,                 -- Committee (from main table)
                    comxoxp.Committee_ID,   -- Committee (from linking table - for deleted objects)
                    le.ID,                  -- Legal Entity (from main table)
                    lexoxp.Legal_ID,        -- Legal Entity (from linking table - for deleted objects)
                    cap.ID,                 -- Capability (from main table)
                    capxoxp.CapabilityID,   -- Capability (from linking table - for deleted objects)
                    reg.ID,                 -- Regulation (from main table)
                    regxoxp.RegulationID,   -- Regulation (from linking table - for deleted objects)
                    a.ID,                   -- Attribute (from main table)
                    axoxp.AttributeID       -- Attribute (from linking table - for deleted objects)
                ) AS object_id,

                -- Determine object type
                CASE
                    WHEN dxoxp.Object_x_ipid    IS NOT NULL THEN 'Data Set'
                    WHEN sxoxp.Object_x_ipid    IS NOT NULL THEN 'System'
                    WHEN ixoxp.Object_x_ipid    IS NOT NULL THEN 'System Interface'
                    WHEN gxoxp.Object_x_ipid    IS NOT NULL THEN 'Glossary'
                    WHEN prcxoxp.object_x_ip    IS NOT NULL THEN 'Process'
                    WHEN polxoxp.Object_X_IP    IS NOT NULL THEN 'Policy'
                    WHEN prodxoxp.Object_x_ip   IS NOT NULL THEN 'Product'
                    WHEN projxoxp.object_x_ip   IS NOT NULL THEN 'Project'
                    WHEN baxoxp.Object_x_ipid   IS NOT NULL THEN 'Business Area'
                    WHEN cxoxp.Object_x_ipid    IS NOT NULL THEN 'Client'
                    WHEN comxoxp.Object_x_ipid  IS NOT NULL THEN 'Committee'
                    WHEN lexoxp.Object_x_ip     IS NOT NULL THEN 'Legal Entity'
                    WHEN capxoxp.Object_x_ipid  IS NOT NULL THEN 'Capability'
                    WHEN regxoxp.Object_x_ipid  IS NOT NULL THEN 'Regulation'
                    WHEN axoxp.Object_x_ipid    IS NOT NULL THEN 'Attribute'
                    ELSE 'Unknown'
                END AS object_type,

                st.primaryname AS role_status,

                delegate_p.first_name AS delegate_first_name,
                delegate_p.last_name  AS delegate_last_name,
                delegate_p.email      AS delegate_email,        
                ra.Message AS role_accepted_message,
                
                -- Segment information (using correlated subquery)
                COALESCE((
                    SELECT seg_sub.ID
                    FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    JOIN segment seg_sub ON sxr.Segment_ID = seg_sub.ID AND seg_sub.Deleted_At IS NULL
                    WHERE orr.Object_ID = COALESCE(d.ID, s.ID, i.ID, g.ID, p.ID, pol.ID, prod.ID, proj.ID, ba.ID, c.ID, com.ID, le.ID, cap.ID, reg.ID, a.ID)
                    AND sot.Type = CASE
                        WHEN d.ID IS NOT NULL THEN 'Dataset'
                        WHEN s.ID IS NOT NULL THEN 'System'
                        WHEN i.ID IS NOT NULL THEN 'SystemInterface'
                        WHEN g.ID IS NOT NULL THEN 'Glossary'
                        WHEN p.ID IS NOT NULL THEN 'Process'
                        WHEN pol.ID IS NOT NULL THEN 'Policy'
                        WHEN prod.ID IS NOT NULL THEN 'Product'
                        WHEN proj.ID IS NOT NULL THEN 'Project'
                        WHEN ba.ID IS NOT NULL THEN 'BusinessArea'
                        WHEN c.ID IS NOT NULL THEN 'Client'
                        WHEN com.ID IS NOT NULL THEN 'Committee'
                        WHEN le.ID IS NOT NULL THEN 'LegalEntity'
                        WHEN cap.ID IS NOT NULL THEN 'Capability'
                        WHEN reg.ID IS NOT NULL THEN 'Regulation'
                        WHEN a.ID IS NOT NULL THEN 'Attribute'
                        ELSE NULL
                    END
                    AND sxr.Deleted_At IS NULL
                    LIMIT 1
                ), 1) AS segment_id,
                COALESCE((
                    SELECT seg_sub.Name
                    FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    JOIN segment seg_sub ON sxr.Segment_ID = seg_sub.ID AND seg_sub.Deleted_At IS NULL
                    WHERE orr.Object_ID = COALESCE(d.ID, s.ID, i.ID, g.ID, p.ID, pol.ID, prod.ID, proj.ID, ba.ID, c.ID, com.ID, le.ID, cap.ID, reg.ID, a.ID)
                    AND sot.Type = CASE
                        WHEN d.ID IS NOT NULL THEN 'Dataset'
                        WHEN s.ID IS NOT NULL THEN 'System'
                        WHEN i.ID IS NOT NULL THEN 'SystemInterface'
                        WHEN g.ID IS NOT NULL THEN 'Glossary'
                        WHEN p.ID IS NOT NULL THEN 'Process'
                        WHEN pol.ID IS NOT NULL THEN 'Policy'
                        WHEN prod.ID IS NOT NULL THEN 'Product'
                        WHEN proj.ID IS NOT NULL THEN 'Project'
                        WHEN ba.ID IS NOT NULL THEN 'BusinessArea'
                        WHEN c.ID IS NOT NULL THEN 'Client'
                        WHEN com.ID IS NOT NULL THEN 'Committee'
                        WHEN le.ID IS NOT NULL THEN 'LegalEntity'
                        WHEN cap.ID IS NOT NULL THEN 'Capability'
                        WHEN reg.ID IS NOT NULL THEN 'Regulation'
                        WHEN a.ID IS NOT NULL THEN 'Attribute'
                        ELSE NULL
                    END
                    AND sxr.Deleted_At IS NULL
                    LIMIT 1
                ), 'Enterprise') AS segment_name

                FROM `object_x_people` AS oxp
                
                -- Core tables
                LEFT JOIN `object_role`         AS orole    ON oxp.`RoleID` = orole.`id`
                LEFT JOIN `object_role_type`    AS ort      ON orole.`objectroletype_id` = ort.`id`
                LEFT JOIN `module`              AS m        ON orole.`module` = m.`id`
                LEFT JOIN `object_x_ip_status`  AS st       ON oxp.`statusID` = st.`id`
                LEFT JOIN `roleaccepted`        AS ra       ON oxp.`AcceptedID` = ra.`ID`
                LEFT JOIN `people`              AS delegate_p ON oxp.`isDelegateOF` = delegate_p.`id`

                -- Dataset
                LEFT JOIN `dataset_x_objectxpeople`   AS dxoxp   ON oxp.`id` = dxoxp.`Object_x_ipid`
                LEFT JOIN `dataset`                   AS d       ON dxoxp.`Dataset_ID` = d.`id` AND d.`DeletedDatetime` IS NULL

                -- System
                LEFT JOIN `system_x_objectxpeople`    AS sxoxp   ON oxp.`id` = sxoxp.`Object_x_ipid`
                LEFT JOIN `system`                    AS s       ON sxoxp.`SystemID` = s.`id` AND s.`Deleted_datetime` IS NULL

                -- Interface
                LEFT JOIN `interface_x_objectxpeople` AS ixoxp   ON oxp.`id` = ixoxp.`Object_x_ipid`
                LEFT JOIN `interface`                 AS i       ON ixoxp.`InterfaceID` = i.`id` AND i.`deleted_datetime` IS NULL

                -- Glossary
                LEFT JOIN `glossary_x_objectxpeople`  AS gxoxp   ON oxp.`id` = gxoxp.`Object_x_ipid`
                LEFT JOIN `glossary`                  AS g       ON gxoxp.`GlossaryID` = g.`id` AND g.`Deleted_datetime` IS NULL

                -- Process
                LEFT JOIN `process_x_objectxpeople`   AS prcxoxp ON oxp.`id` = prcxoxp.`object_x_ip`
                LEFT JOIN `process`                   AS p       ON prcxoxp.`process_id` = p.`id` AND p.`DeletedDatetime` IS NULL

                -- Policy
                LEFT JOIN `policy_x_objectxpeople`    AS polxoxp ON oxp.`id` = polxoxp.`Object_X_IP`
                LEFT JOIN `policy`                    AS pol     ON polxoxp.`Policy_ID` = pol.`id` AND pol.`DeletedDatetime` IS NULL

                -- Product
                LEFT JOIN `product_x_objectxpeople`   AS prodxoxp ON oxp.`id` = prodxoxp.`Object_x_ip`
                LEFT JOIN `product`                   AS prod     ON prodxoxp.`product_id` = prod.`id` AND prod.`DeletedDatetime` IS NULL

                -- Project
                LEFT JOIN `project_x_objectxpeople`   AS projxoxp ON oxp.`id` = projxoxp.`object_x_ip`
                LEFT JOIN `project`                   AS proj     ON projxoxp.`project_id` = proj.`id` AND proj.`deletedatetime` IS NULL

                -- Business Area
                LEFT JOIN `businessarea_x_objectxpeople` AS baxoxp ON oxp.`id` = baxoxp.`Object_x_ipid`
                LEFT JOIN `business_area`                AS ba     ON baxoxp.`BusinessAreaID` = ba.`id` AND ba.`deletedatetime` IS NULL

                -- Client
                LEFT JOIN `client_x_objectxpeople`    AS cxoxp   ON oxp.`id` = cxoxp.`Object_x_ipid`
                LEFT JOIN `client`                    AS c       ON cxoxp.`ClientID` = c.`id` AND c.`DeleteDatetime` IS NULL

                -- Committee
                LEFT JOIN `committee_x_objectxpeople` AS comxoxp ON oxp.`id` = comxoxp.`Object_x_ipid`
                LEFT JOIN `committee`                 AS com     ON comxoxp.`Committee_ID` = com.`id` AND com.`DeleteDatetime` IS NULL

                -- Legal Entity
                LEFT JOIN `legal_x_objectxpeople`     AS lexoxp  ON oxp.`id` = lexoxp.`Object_x_ip`
                LEFT JOIN `legal`               AS le      ON lexoxp.`Legal_ID` = le.`id` AND le.`DeleteDatetime` IS NULL

                -- Capability
                LEFT JOIN `capability_x_objectxpeople` AS capxoxp ON oxp.`id` = capxoxp.`Object_x_ipid`
                LEFT JOIN `capability`                 AS cap     ON capxoxp.`CapabilityID` = cap.`id` AND cap.`DeletedDatetime` IS NULL

                -- Regulation
                LEFT JOIN `regulation_x_objectxpeople` AS regxoxp ON oxp.`id` = regxoxp.`Object_x_ipid`
                LEFT JOIN `regulation`                 AS reg     ON regxoxp.`RegulationID` = reg.`id` AND reg.`DeletedDatetime` IS NULL

                -- Attribute
                LEFT JOIN `attribute_x_objectxpeople` AS axoxp   ON oxp.`id` = axoxp.`Object_x_ipid`
                LEFT JOIN `attribute`                 AS a       ON axoxp.`AttributeID` = a.`id` AND a.`DeletedDatetime` IS NULL

                WHERE oxp.`ipid` = ?
                AND (
                    dxoxp.`Object_x_ipid`    IS NOT NULL OR
                    sxoxp.`Object_x_ipid`    IS NOT NULL OR
                    ixoxp.`Object_x_ipid`    IS NOT NULL OR
                    gxoxp.`Object_x_ipid`    IS NOT NULL OR
                    prcxoxp.`object_x_ip`    IS NOT NULL OR
                    polxoxp.`Object_X_IP`    IS NOT NULL OR
                    prodxoxp.`Object_x_ip`   IS NOT NULL OR
                    projxoxp.`object_x_ip`   IS NOT NULL OR
                    baxoxp.`Object_x_ipid`   IS NOT NULL OR
                    cxoxp.`Object_x_ipid`    IS NOT NULL OR
                    comxoxp.`Object_x_ipid`  IS NOT NULL OR
                    lexoxp.`Object_x_ip`     IS NOT NULL OR
                    capxoxp.`Object_x_ipid`  IS NOT NULL OR
                    regxoxp.`Object_x_ipid`  IS NOT NULL OR
                    axoxp.`Object_x_ipid`    IS NOT NULL
                )
                -- Filter out deleted objects: object_name should not be NULL
                AND COALESCE(
                    d.primaryname,
                    s.name,
                    i.name,
                    g.name,
                    p.primaryname,
                    pol.primaryname,
                    prod.primaryname,
                    proj.primaryname,
                    ba.primaryname,
                    c.primaryname,
                    com.primaryname,
                    le.ShortName,
                    cap.primaryname,
                    reg.primaryname,
                    a.primaryname
                ) IS NOT NULL
                ORDER BY object_type, object_name;
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, personId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int objectXPeopleId = rs.getInt("object_x_people_id");
                    String objectType    = rs.getString("object_type");
                    int    objectId      = rs.getInt("object_id");

                    // Skip genuine duplicates (same assignment + same facet + same object).
                    // SELECT DISTINCT in the SQL already collapses identical rows; this guard
                    // catches any residual duplicates without discarding rows where the same
                    // assignment record is linked to multiple distinct objects.
                    String dedupKey = objectXPeopleId + "|" + (objectType != null ? objectType : "") + "|" + objectId;
                    if (seenResponsibilityKeys.contains(dedupKey)) {
                        continue;
                    }
                    seenResponsibilityKeys.add(dedupKey);
                    
                    Map<String, Object> responsibility = new HashMap<>();
                    
                    // Basic information
                    responsibility.put("id", objectXPeopleId);
                    responsibility.put("personId", rs.getInt("person_id"));
                    responsibility.put("roleId", rs.getInt("RoleID"));
                    responsibility.put("statusId", rs.getInt("statusID"));
                    responsibility.put("acceptedId", rs.getInt("AcceptedID"));
                    responsibility.put("isDelegateOf", rs.getInt("isDelegateOF"));
                    responsibility.put("createdAt", rs.getTimestamp("createdatetime"));
                    responsibility.put("lastUpdatedAt", rs.getTimestamp("lastupdatedatetime"));
                    responsibility.put("lastUpdatedUserId", rs.getInt("lastupdateuser_id"));
                    
                    // Role information
                    responsibility.put("roleName", rs.getString("role_name"));
                    responsibility.put("roleDescription", rs.getString("role_description"));
                    responsibility.put("defaultRole", rs.getBoolean("defaultrole"));
                    responsibility.put("objectRoleTypeId", rs.getInt("objectroletype_id"));
                    
                    // Role Type information (from object_role_type table)
                    responsibility.put("roleType", rs.getString("role_type_name"));
                    responsibility.put("roleTypeDescription", rs.getString("role_type_description"));
                    
                    // Facet information
                    responsibility.put("facet", rs.getString("facet_name"));
                    responsibility.put("facetId", rs.getInt("facet_id"));
                    
                    // Segment information (get this first for masking check)
                    int segmentId = rs.getInt("segment_id");
                    boolean segmentWasNull = rs.wasNull();
                    String segmentName = rs.getString("segment_name");
                    if (segmentWasNull) {
                        segmentId = 1; // Default to Enterprise if no segment assigned
                        segmentName = "Enterprise";
                    }
                    responsibility.put("segmentId", segmentId);
                    responsibility.put("segmentName", segmentName);
                    
                    // Check if current user has access to this segment
                    // Super Admins have access to all segments
                    // Enterprise segment (ID=1) is accessible to all
                    boolean hasAccess = isSuperAdmin || 
                                        segmentId == 1 || 
                                        (accessibleSegments != null && accessibleSegments.contains(segmentId));
                    
                    // Object information - mask if no access
                    String objectName = rs.getString("object_name");
                    if (!hasAccess) {
                        objectName = "XXXXXXX"; // Masked name
                        responsibility.put("isMasked", true);
                    } else {
                        responsibility.put("isMasked", false);
                    }
                    responsibility.put("objectName", objectName);
                    responsibility.put("objectId", rs.getInt("object_id"));
                    responsibility.put("objectType", rs.getString("object_type"));
                    responsibility.put("hasSegmentAccess", hasAccess);
                    
                    // Status information
                    responsibility.put("roleStatus", rs.getString("role_status"));
                    
                    // Delegate information
                    if (rs.getString("delegate_first_name") != null) {
                        Map<String, String> delegate = new HashMap<>();
                        delegate.put("firstName", rs.getString("delegate_first_name"));
                        delegate.put("lastName", rs.getString("delegate_last_name"));
                        delegate.put("email", rs.getString("delegate_email"));
                        responsibility.put("delegate", delegate);
                    }
                    
                    // Role accepted status (from roleaccepted table)
                    responsibility.put("roleAccepted", rs.getString("role_accepted_message")); // "Yes", "No", or null
                    responsibility.put("roleAcceptedOn", rs.getTimestamp("createdatetime"));
                    
                    responsibilities.add(responsibility);
                }
            }
        }
        
        return responsibilities;
    }
    
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("status", statusCode);
        
        objectMapper.writeValue(response.getWriter(), error);
    }
}
