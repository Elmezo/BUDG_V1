package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
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
import java.util.*;

/**
 * Provides segment information for a person based on their role.
 * - Any user: Can see their own Assigned Segments tab
 * - Super Admins: All private segments created (excluding Enterprise)
 * - Admins: Private segments they're admin on OR private segments they have access to
 * - Web Users: Only private segments they have access to
 * Note: Enterprise segment (ID=1) is excluded from this list - only private segments are shown
 */
@WebServlet("/api/people/segments/*")
public class PersonSegmentsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Person ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int personId = Integer.parseInt(pathInfo.substring(1));
            
            // Get current user ID to verify they're viewing their own profile
            // Try to get from request attribute first, then from JWT token
            Integer currentUserId = UserContextUtil.getCurrentUserIdOrNull(request);
            if (currentUserId == null) {
                // Fallback: try to get from JWT token
                try {
                    String token = getCookie(request, "ACCESS_TOKEN");
                    if (token != null) {
                        currentUserId = com.example.budg_v2.util.JwtUtil.getUserIdFromToken(token);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            if (currentUserId == null || currentUserId != personId) {
                sendError(response, "You can only view your own segments", HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            List<Map<String, Object>> segments = loadSegmentsForUser(personId);

            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), segments);
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

    private List<Map<String, Object>> loadSegmentsForUser(int userId) throws SQLException {
        // Check if user is Super Admin
        boolean isSuperAdmin = SegmentAccessService.isSuperAdmin(userId);
        
        Set<Map<String, Object>> segmentsSet = new LinkedHashSet<>();
        
        if (isSuperAdmin) {
            // Super Admins: Get all created segments (excluding Enterprise) with admin information
            // Aggregate multiple admins into comma-separated string
            String sql = """
                SELECT 
                    s.ID AS id,
                    s.Name AS name,
                    s.Description AS description,
                    CASE WHEN s.Deleted_At IS NULL THEN 'Active' ELSE 'Deleted' END AS status,
                    GROUP_CONCAT(DISTINCT CONCAT(admin_p.First_Name, ' ', admin_p.Last_Name) 
                        ORDER BY admin_p.Last_Name, admin_p.First_Name 
                        SEPARATOR ', ') AS admin_name
                FROM segment s
                LEFT JOIN segment_x_identity sxi ON s.ID = sxi.Segment_ID AND sxi.Role = 'admin' AND sxi.Deleted_At IS NULL
                LEFT JOIN object_reference or_ref ON sxi.Object_Ref_ID = or_ref.ID
                LEFT JOIN people admin_p ON or_ref.Object_ID = admin_p.ID
                LEFT JOIN segment_object_type sot ON or_ref.Object_Type_ID = sot.ID AND sot.Type = 'People'
                WHERE s.Deleted_At IS NULL
                AND s.ID != 1
                GROUP BY s.ID, s.Name, s.Description, s.Deleted_At
                ORDER BY s.ID
            """;
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int segmentId = rs.getInt("id");
                        Map<String, Object> segment = new HashMap<>();
                        segment.put("id", segmentId);
                        segment.put("name", rs.getString("name"));
                        segment.put("description", rs.getString("description"));
                        segment.put("type", "private");
                        segment.put("status", rs.getString("status"));
                        segment.put("adminName", rs.getString("admin_name")); // Already comma-separated
                        // Check if current user is admin of this segment
                        boolean isUserAdmin = isUserSegmentAdmin(segmentId, userId);
                        segment.put("isAdmin", isUserAdmin);
                        segmentsSet.add(segment);
                    }
                }
            }
        } else {
            // For Admins and Web Users: Get accessible segments
            List<Map<String, Object>> accessibleSegments = SegmentAccessService.getUserAccessibleSegments(userId);
            
            // For Admins: Also get segments where they are admin (excluding Enterprise)
            if (isAdmin(userId)) {
                String adminSql = """
                    SELECT DISTINCT
                        s.ID AS id,
                        s.Name AS name,
                        s.Description AS description,
                        CASE WHEN s.Deleted_At IS NULL THEN 'Active' ELSE 'Deleted' END AS status,
                        GROUP_CONCAT(DISTINCT CONCAT(admin_p.First_Name, ' ', admin_p.Last_Name) 
                            ORDER BY admin_p.Last_Name, admin_p.First_Name 
                            SEPARATOR ', ') AS admin_name,
                        CASE WHEN sxi.Segment_ID IS NOT NULL THEN 1 ELSE 0 END AS is_admin
                    FROM segment s
                    INNER JOIN segment_x_identity sxi_admin ON s.ID = sxi_admin.Segment_ID 
                        AND sxi_admin.Role = 'admin' 
                        AND sxi_admin.Deleted_At IS NULL
                    INNER JOIN object_reference or_ref_admin ON sxi_admin.Object_Ref_ID = or_ref_admin.ID
                    INNER JOIN people admin_check ON or_ref_admin.Object_ID = admin_check.ID AND admin_check.ID = ?
                    LEFT JOIN segment_x_identity sxi ON s.ID = sxi.Segment_ID AND sxi.Role = 'admin' AND sxi.Deleted_At IS NULL
                    LEFT JOIN object_reference or_ref ON sxi.Object_Ref_ID = or_ref.ID
                    LEFT JOIN people admin_p ON or_ref.Object_ID = admin_p.ID
                    LEFT JOIN segment_object_type sot ON or_ref.Object_Type_ID = sot.ID AND sot.Type = 'People'
                    WHERE s.Deleted_At IS NULL
                    AND s.ID != 1
                    GROUP BY s.ID, s.Name, s.Description, s.Deleted_At, sxi.Segment_ID
                    ORDER BY s.ID
                """;
                
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(adminSql)) {
                    pstmt.setInt(1, userId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            int segmentId = rs.getInt("id");
                            Map<String, Object> segment = new HashMap<>();
                            segment.put("id", segmentId);
                            segment.put("name", rs.getString("name"));
                            segment.put("description", rs.getString("description"));
                            segment.put("type", "private");
                            segment.put("status", rs.getString("status"));
                            segment.put("adminName", rs.getString("admin_name")); // Already comma-separated
                            segment.put("isAdmin", rs.getInt("is_admin") == 1);
                            segmentsSet.add(segment);
                        }
                    }
                }
            }
            
            // Add accessible segments with admin information (excluding Enterprise)
            for (Map<String, Object> seg : accessibleSegments) {
                Object idObj = seg.get("id");
                int segmentId = idObj instanceof Integer ? (Integer) idObj : 
                               idObj instanceof Number ? ((Number) idObj).intValue() : 0;
                
                // Skip Enterprise segment (ID=1) - only show private segments
                if (segmentId == 1) {
                    continue;
                }
                
                // Skip if already added (for Admins who are admin of this segment)
                boolean alreadyAdded = segmentsSet.stream()
                    .anyMatch(s -> ((Integer) s.get("id")).equals(segmentId));
                if (alreadyAdded) {
                    continue;
                }
                
                // Get admin info for this segment
                Map<String, Object> adminInfo = getSegmentAdminInfo(segmentId);
                
                // Check if current user is admin of this segment
                boolean isUserAdmin = isUserSegmentAdmin(segmentId, userId);
                
                // Convert to match format
                Map<String, Object> segment = new HashMap<>();
                segment.put("id", segmentId);
                segment.put("name", seg.get("name"));
                segment.put("description", seg.get("description"));
                segment.put("type", "private");
                segment.put("status", "Active");
                segment.put("adminId", adminInfo.get("adminId"));
                segment.put("adminName", adminInfo.get("adminName"));
                segment.put("isAdmin", isUserAdmin);
                segmentsSet.add(segment);
            }
        }
        
        // Convert set to list, maintaining order
        List<Map<String, Object>> segments = new ArrayList<>(segmentsSet);
        
        // Sort by ID
        segments.sort((a, b) -> {
            Integer idA = (Integer) a.get("id");
            Integer idB = (Integer) b.get("id");
            return idA.compareTo(idB);
        });
        
        return segments;
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getEnterpriseSegmentInfo() throws SQLException {
        Map<String, Object> enterprise = new HashMap<>();
        
        String sql = """
            SELECT 
                s.ID AS id,
                s.Name AS name,
                s.Description AS description,
                CASE WHEN s.Deleted_At IS NULL THEN 'Active' ELSE 'Deleted' END AS status
            FROM segment s
            WHERE s.ID = 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    enterprise.put("id", 1);
                    enterprise.put("name", rs.getString("name") != null ? rs.getString("name") : "Enterprise");
                    enterprise.put("description", rs.getString("description") != null ? rs.getString("description") : "Default enterprise-wide segment");
                    enterprise.put("type", "enterprise");
                    enterprise.put("status", rs.getString("status"));
                    enterprise.put("adminId", null);
                    enterprise.put("adminName", null);
                    
                    // Get admin info for Enterprise (comma-separated)
                    Map<String, Object> adminInfo = getSegmentAdminInfo(1);
                    enterprise.put("adminName", adminInfo.get("adminName"));
                    
                    return enterprise;
                } else {
                    // Enterprise doesn't exist in DB, create default entry
                    enterprise.put("id", 1);
                    enterprise.put("name", "Enterprise");
                    enterprise.put("description", "Default enterprise-wide segment accessible to all users");
                    enterprise.put("type", "enterprise");
                    enterprise.put("status", "Active");
                    enterprise.put("adminId", null);
                    enterprise.put("adminName", null);
                    return enterprise;
                }
            }
        }
    }

    private Map<String, Object> getSegmentAdminInfo(int segmentId) throws SQLException {
        Map<String, Object> adminInfo = new HashMap<>();
        adminInfo.put("adminId", null);
        adminInfo.put("adminName", null);
        
        // Aggregate all admin names into comma-separated string
        String sql = """
            SELECT 
                GROUP_CONCAT(DISTINCT CONCAT(p.First_Name, ' ', p.Last_Name) 
                    ORDER BY p.Last_Name, p.First_Name 
                    SEPARATOR ', ') AS admin_name
            FROM segment_x_identity sxi
            JOIN object_reference or_ref ON sxi.Object_Ref_ID = or_ref.ID
            JOIN people p ON or_ref.Object_ID = p.ID
            JOIN segment_object_type sot ON or_ref.Object_Type_ID = sot.ID AND sot.Type = 'People'
            WHERE sxi.Segment_ID = ?
            AND sxi.Role = 'admin'
            AND sxi.Deleted_At IS NULL
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String adminName = rs.getString("admin_name");
                    if (adminName != null) {
                        adminInfo.put("adminName", adminName);
                    }
                }
            }
        }
        return adminInfo;
    }

    private boolean isUserSegmentAdmin(int segmentId, int userId) throws SQLException {
        String sql = """
            SELECT COUNT(*) as count
            FROM segment_x_identity sxi
            JOIN object_reference or_ref ON sxi.Object_Ref_ID = or_ref.ID
            JOIN people p ON or_ref.Object_ID = p.ID
            JOIN segment_object_type sot ON or_ref.Object_Type_ID = sot.ID AND sot.Type = 'People'
            WHERE sxi.Segment_ID = ?
            AND sxi.Role = 'admin'
            AND sxi.Deleted_At IS NULL
            AND p.ID = ?
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }
        return false;
    }

    private boolean isAdmin(int userId) throws SQLException {
        String sql = """
            SELECT r.primaryname AS role_name
            FROM people p
            LEFT JOIN role r ON p.System_Role = r.id
            WHERE p.ID = ?
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String roleName = rs.getString("role_name");
                    if (roleName != null) {
                        return AppRoleNames.isAdminOrSuperAdminName(roleName);
                    }
                }
            }
        }
        return false;
    }

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

    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), error);
    }
}

