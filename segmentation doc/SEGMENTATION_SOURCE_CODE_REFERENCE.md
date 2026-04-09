# Segmentation Source Code Reference

This document contains the complete source code of the core segmentation services for reference.

---

## Table of Contents
1. [SegmentAccessService.java](#segmentaccessservicejava)
2. [SegmentValidationService.java](#segmentvalidationservicejava)
3. [getAccessibleObjectIdsInSegments Method](#getaccessibleobjectidsinsegments-method)

---

## SegmentAccessService.java

**File**: `src/main/java/com/example/budg_v2/service/SegmentAccessService.java`

```java
package com.example.budg_v2.service;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.HashSet;

/**
 * Service for managing segment access control
 * Determines which segments a user can access based on:
 * 1. Enterprise segment (always accessible to all)
 * 2. Direct user assignment (segment_x_identity) - Role 'admin' or 'user'
 * 3. Org unit assignment (segment_x_resource)
 * 
 * NOTE: Database uses PascalCase (Segment_ID, Created_By, etc.)
 *       Compatible with project_db.sql structure
 */
public class SegmentAccessService {
    
    /**
     * Get all segments accessible by a user based on their role:
     * - Super Admins: All segments including Enterprise
     * - Admins: Enterprise + segments where they are admin or have access
     * - Web Users: Enterprise + segments they have access to
     * Only returns segments that exist (have been created)
     * 
     * @param userId The user ID
     * @return List of accessible segments
     */
    public static List<Map<String, Object>> getUserAccessibleSegments(int userId) throws SQLException {
        System.out.println("🔍 Getting accessible segments for user ID: " + userId);
        
        // Check if user is SuperAdmin
        boolean userIsSuperAdmin = isSuperAdmin(userId);
        System.out.println("👤 User " + userId + " is SuperAdmin: " + userIsSuperAdmin);
        
        List<Map<String, Object>> segments = new ArrayList<>();
        
        if (userIsSuperAdmin) {
            // Super Admins: see ALL segments including Enterprise
            String sql = """
                SELECT 
                    s.ID AS segment_id,
                    s.Name AS segment_name,
                    s.Description AS segment_description
                FROM segment s
                WHERE s.Deleted_At IS NULL
                ORDER BY s.ID
            """;
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> segment = new HashMap<>();
                        segment.put("id", rs.getInt("segment_id"));
                        segment.put("name", rs.getString("segment_name"));
                        segment.put("description", rs.getString("segment_description"));
                        segments.add(segment);
                    }
                }
            }
            
            // Always ensure Enterprise exists
            boolean hasEnterprise = segments.stream().anyMatch(s -> (Integer)s.get("id") == 1);
            if (!hasEnterprise) {
                Map<String, Object> enterprise = new HashMap<>();
                enterprise.put("id", 1);
                enterprise.put("name", "Enterprise");
                enterprise.put("description", "Default enterprise-wide segment accessible to all users");
                segments.add(0, enterprise);
            }
            
            System.out.println("✅ SuperAdmin - Loaded " + segments.size() + " segments");
        } else {
            // Regular users: get accessible segments from view
            String sql = """
                SELECT DISTINCT
                    vas.segment_id,
                    vas.segment_name,
                    vas.segment_description
                FROM v_user_accessible_segments vas
                WHERE vas.user_id = ?
                ORDER BY vas.segment_id
            """;
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> segment = new HashMap<>();
                        segment.put("id", rs.getInt("segment_id"));
                        segment.put("name", rs.getString("segment_name"));
                        segment.put("description", rs.getString("segment_description"));
                        segments.add(segment);
                    }
                }
            }
            
            // Always ensure Enterprise is included
            boolean hasEnterprise = segments.stream().anyMatch(s -> (Integer)s.get("id") == 1);
            if (!hasEnterprise) {
                Map<String, Object> enterprise = new HashMap<>();
                enterprise.put("id", 1);
                enterprise.put("name", "Enterprise");
                enterprise.put("description", "Default enterprise-wide segment accessible to all users");
                segments.add(0, enterprise);
            }
            
            System.out.println("✅ User - Loaded " + segments.size() + " accessible segments");
        }
        
        return segments;
    }
    
    /**
     * Check if user is a Super Admin (full access, no assignment needed)
     */
    public static boolean isSuperAdmin(int userId) throws SQLException {
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
                    if (roleName == null) return false;
                    return roleName.toLowerCase().trim().equals("super admin");
                }
            }
        }
        return false;
    }
    
    /**
     * Get IDs of segments accessible by user
     */
    public static Set<Integer> getAccessibleSegmentIds(int userId) throws SQLException {
        List<Map<String, Object>> segments = getUserAccessibleSegments(userId);
        return segments.stream()
                .map(s -> (Integer) s.get("id"))
                .collect(Collectors.toSet());
    }
    
    /**
     * Check if user can access an object based on its segment
     */
    public static boolean canAccessObject(int userId, int objectId, String objectType) throws SQLException {
        // Super Admin can access everything
        if (isSuperAdmin(userId)) {
            return true;
        }
        
        // Get the segment of the object
        int objectSegmentId = getObjectSegmentId(objectId, objectType);
        
        // Enterprise segment (ID=1) is accessible to all
        if (objectSegmentId == 1) {
            return true;
        }
        
        // Check if user has access to the object's segment
        Set<Integer> accessibleSegments = getAccessibleSegmentIds(userId);
        return accessibleSegments.contains(objectSegmentId);
    }
    
    /**
     * Get the segment ID of an object
     * Returns 1 (Enterprise) if not assigned to any segment
     */
    public static int getObjectSegmentId(int objectId, String objectType) throws SQLException {
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
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, objectId);
            pstmt.setString(2, objectType);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Segment_ID");
                }
            }
        }
        
        // Default to Enterprise if not assigned
        return 1;
    }
    
    /**
     * Check if user can edit an object based on segment and role
     */
    public static boolean canEditObject(int userId, int objectId, String objectType) throws SQLException {
        // First check if user can access the object
        if (!canAccessObject(userId, objectId, objectType)) {
            return false;
        }
        
        // Get user's role
        String roleName = getUserRole(userId);
        if (roleName == null) {
            return false;
        }
        
        String normalizedRole = roleName.toLowerCase().trim();
        
        // Super Admin can edit everything
        if (normalizedRole.equals("super admin")) {
            return true;
        }
        
        // Admin can edit objects in Enterprise and their assigned segments
        if (normalizedRole.equals("admin")) {
            return true; // Already verified access above
        }
        
        // WebUser cannot edit
        return false;
    }
    
    /**
     * Get user's role name
     */
    public static String getUserRole(int userId) throws SQLException {
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
                    return rs.getString("role_name");
                }
            }
        }
        return null;
    }
}
```

---

## getAccessibleObjectIdsInSegments Method

This is the **KEY METHOD** used by all facets to filter objects by segment access. Add this to `SegmentAccessService.java` if not present:

```java
/**
 * Filter a list of object IDs to only those accessible by the user.
 * This is the main method used by facet DAOs to implement segment filtering.
 * 
 * @param userId The user ID
 * @param objectType The type of objects (e.g., "Glossary", "Policy", "Process")
 * @param objectIds List of all object IDs to filter
 * @return Set of object IDs that the user can access
 */
public static Set<Integer> getAccessibleObjectIdsInSegments(
        int userId, 
        String objectType, 
        List<Integer> objectIds) throws SQLException {
    
    Set<Integer> accessibleIds = new HashSet<>();
    
    // Super Admin can access everything
    if (isSuperAdmin(userId)) {
        return new HashSet<>(objectIds);
    }
    
    // Get user's accessible segment IDs
    Set<Integer> accessibleSegments = getAccessibleSegmentIds(userId);
    
    // For each object, check if it's accessible
    for (Integer objectId : objectIds) {
        int objectSegmentId = getObjectSegmentId(objectId, objectType);
        
        // Object is accessible if:
        // 1. It's in Enterprise segment (ID=1)
        // 2. It's in one of the user's accessible segments
        // 3. It has no segment assignment (objectSegmentId returns 1 by default)
        if (objectSegmentId == 1 || accessibleSegments.contains(objectSegmentId)) {
            accessibleIds.add(objectId);
        }
    }
    
    return accessibleIds;
}
```

---

## SegmentValidationService.java

**File**: `src/main/java/com/example/budg_v2/service/SegmentValidationService.java`

```java
package com.example.budg_v2.service;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Segment Validation Service
 * 
 * 1. HIERARCHY RULE: Parent-child relationships must stay within the SAME segment
 *    - A child object cannot be in a different segment than its parent
 *    - Entire hierarchy (parent → child → grandchild) must be in same segment
 * 
 * 2. CROSS-SEGMENT RELATIONSHIP RULES:
 *    - Enterprise ↔ Enterprise: ✅ ALLOWED
 *    - Enterprise ↔ Private: ✅ ALLOWED
 *    - Private ↔ Enterprise: ✅ ALLOWED
 *    - Private1 ↔ Private1 (same): ✅ ALLOWED
 *    - Private1 ↔ Private2 (different): ❌ NOT ALLOWED
 * 
 * Enterprise Segment ID = 1 (always)
 */
public class SegmentValidationService {

    private static final int ENTERPRISE_SEGMENT_ID = 1;
    private final SegmentDAO segmentDAO = new SegmentDAO();

    /**
     * Validation result with details
     */
    public static class ValidationResult {
        public boolean isValid;
        public String message;
        public String warningType; // "hierarchy", "cross_segment", "visibility"
        public int parentSegmentId;
        public int childSegmentId;
        public String parentSegmentName;
        public String childSegmentName;
        public boolean canProceedWithWarning;

        public ValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
            this.canProceedWithWarning = false;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, "Valid");
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public static ValidationResult hierarchyConflict(
                int parentSegmentId, String parentSegmentName,
                int childSegmentId, String childSegmentName) {
            ValidationResult result = new ValidationResult(false,
                String.format("Hierarchy Constraint: Parent is in segment '%s' but this object is in segment '%s'. " +
                    "Parent-child relationships must be within the same segment. " +
                    "To continue, the parent will be moved to the same segment as this object.",
                    parentSegmentName, childSegmentName));
            result.warningType = "hierarchy";
            result.parentSegmentId = parentSegmentId;
            result.childSegmentId = childSegmentId;
            result.parentSegmentName = parentSegmentName;
            result.childSegmentName = childSegmentName;
            result.canProceedWithWarning = true;
            return result;
        }

        public static ValidationResult crossSegmentNotAllowed(
                String sourceSegmentName, String targetSegmentName) {
            ValidationResult result = new ValidationResult(false,
                String.format("Cross-Segment Relationship Not Allowed: Cannot create relationship between " +
                    "Private segment '%s' and Private segment '%s'. " +
                    "Relationships are only allowed between Enterprise↔Private or within the same segment.",
                    sourceSegmentName, targetSegmentName));
            result.warningType = "cross_segment";
            result.canProceedWithWarning = false;
            return result;
        }
    }

    /**
     * Check if segment is the Enterprise segment
     */
    public boolean isEnterpriseSegment(int segmentId) {
        return segmentId == ENTERPRISE_SEGMENT_ID;
    }

    /**
     * Get segment name by ID
     */
    public String getSegmentName(int segmentId) throws SQLException {
        if (segmentId == ENTERPRISE_SEGMENT_ID) {
            return "Enterprise";
        }
        String sql = "SELECT Name FROM segment WHERE ID = ? AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        }
        return "Unknown";
    }

    /**
     * HIERARCHY RULE VALIDATION
     * 
     * Validates that a child object can be assigned to a segment given its parent's segment.
     */
    public ValidationResult validateParentChildSegment(Integer parentId, int childSegmentId, String objectType) 
            throws SQLException {
        // If no parent, always valid
        if (parentId == null || parentId <= 0) {
            return ValidationResult.success();
        }

        // Get parent's segment
        int parentSegmentId = segmentDAO.getObjectSegmentId(parentId, objectType);
        
        // Same segment = valid
        if (parentSegmentId == childSegmentId) {
            return ValidationResult.success();
        }

        // Different segments = hierarchy conflict
        String parentSegmentName = getSegmentName(parentSegmentId);
        String childSegmentName = getSegmentName(childSegmentId);
        
        return ValidationResult.hierarchyConflict(
            parentSegmentId, parentSegmentName,
            childSegmentId, childSegmentName
        );
    }

    /**
     * CROSS-SEGMENT RELATIONSHIP VALIDATION
     * 
     * Rules:
     * - Enterprise ↔ Enterprise: ✅ ALLOWED
     * - Enterprise ↔ Private: ✅ ALLOWED  
     * - Private ↔ Enterprise: ✅ ALLOWED
     * - Private1 ↔ Private1: ✅ ALLOWED (same segment)
     * - Private1 ↔ Private2: ❌ NOT ALLOWED (different private segments)
     */
    public ValidationResult validateCrossSegmentRelationship(
            int sourceObjectId, String sourceObjectType,
            int targetObjectId, String targetObjectType) throws SQLException {
        
        int sourceSegmentId = segmentDAO.getObjectSegmentId(sourceObjectId, sourceObjectType);
        int targetSegmentId = segmentDAO.getObjectSegmentId(targetObjectId, targetObjectType);
        
        // Same segment - always allowed
        if (sourceSegmentId == targetSegmentId) {
            return ValidationResult.success();
        }
        
        // Enterprise ↔ Anything - always allowed
        if (isEnterpriseSegment(sourceSegmentId) || isEnterpriseSegment(targetSegmentId)) {
            return ValidationResult.success();
        }
        
        // Different private segments - NOT allowed
        String sourceSegmentName = getSegmentName(sourceSegmentId);
        String targetSegmentName = getSegmentName(targetSegmentId);
        
        return ValidationResult.crossSegmentNotAllowed(sourceSegmentName, targetSegmentName);
    }

    /**
     * Move parent to child's segment (when user confirms to proceed with hierarchy conflict)
     */
    public void moveParentToChildSegment(int parentId, int childSegmentId, String objectType, int userId) 
            throws SQLException {
        int currentSegmentId = segmentDAO.getObjectSegmentId(parentId, objectType);
        
        if (currentSegmentId != childSegmentId) {
            // Remove from current segment
            if (currentSegmentId > 0) {
                segmentDAO.removeObjectFromSegment(currentSegmentId, parentId, objectType, userId);
            }
            // Add to new segment
            segmentDAO.assignObjectToSegment(childSegmentId, parentId, objectType, userId);
            System.out.println("✅ Moved parent " + parentId + " from segment " + currentSegmentId + 
                " to segment " + childSegmentId);
        }
    }

    /**
     * Get the table name for child objects based on object type
     */
    private String getChildTableName(String objectType) {
        return switch (objectType) {
            case "Glossary" -> "glossary";
            case "Policy" -> "policy";
            case "System" -> "system";
            case "Regulation" -> "regulation";
            case "Project" -> "project";
            case "Process" -> "process";
            case "OrgUnit" -> "org_unit";
            case "Client" -> "client";
            case "Product" -> "product";
            case "BusinessArea" -> "business_area";
            case "Capability" -> "capability";
            case "Committee" -> "committee";
            case "Legal" -> "legal";
            default -> null;
        };
    }

    /**
     * Get the parent column name based on object type
     */
    private String getParentColumnName(String objectType) {
        return switch (objectType) {
            case "Glossary" -> "Parent_ID";
            case "Policy" -> "ParentID";
            case "System" -> "parent_id";
            case "Regulation" -> "parent_id";
            case "Project" -> "parent_id";
            case "Process" -> "parentid";
            case "OrgUnit" -> "Parent_ID";
            case "Client" -> "Parent_ID";
            case "Product" -> "parent_id";
            case "BusinessArea" -> "Parent_ID";
            case "Capability" -> "Parent_ID";
            case "Committee" -> "Parent_ID";
            case "Legal" -> "Parent_ID";
            default -> null;
        };
    }
}
```

---

## Example: Complete DAO Implementation Pattern

Here's a complete example of how a DAO should implement segment filtering:

```java
package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.MyFacet;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MyFacetDAO {
    
    private static final String SELECT_ALL = """
        SELECT id, name, description, parent_id, status 
        FROM my_facet 
        WHERE deleted_at IS NULL 
        ORDER BY name
    """;
    
    private static final String SELECT_FOR_DROPDOWN = """
        SELECT id, name, description 
        FROM my_facet 
        WHERE deleted_at IS NULL 
        ORDER BY name
    """;
    
    // ========== ORIGINAL METHODS (no filtering) ==========
    
    public List<MyFacet> getAllItems() throws SQLException {
        List<MyFacet> items = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        }
        return items;
    }
    
    public List<MyFacet> getItemsForDropdown() throws SQLException {
        List<MyFacet> items = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                MyFacet item = new MyFacet();
                item.setId(rs.getInt("id"));
                item.setName(rs.getString("name"));
                item.setDescription(rs.getString("description"));
                items.add(item);
            }
        }
        return items;
    }
    
    // ========== SEGMENT-FILTERED METHODS ==========
    
    /**
     * Get all items filtered by user's segment access
     * 
     * @param userId The ID of the current user
     * @return List of items the user can access
     */
    public List<MyFacet> getAllItems(int userId) throws SQLException {
        // Step 1: Get all items (unfiltered)
        List<MyFacet> allItems = getAllItems();
        
        // Step 2: If empty, return immediately
        if (allItems.isEmpty()) {
            return allItems;
        }
        
        // Step 3: Extract all IDs
        List<Integer> allIds = allItems.stream()
                .map(MyFacet::getId)
                .collect(Collectors.toList());
        
        // Step 4: Get accessible IDs from SegmentAccessService
        // IMPORTANT: Use the correct object type string
        Set<Integer> accessibleIds = SegmentAccessService
                .getAccessibleObjectIdsInSegments(userId, "MyFacet", allIds);
        
        // Step 5: Filter and return
        return allItems.stream()
                .filter(item -> accessibleIds.contains(item.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get items for dropdown filtered by user's segment access
     */
    public List<MyFacet> getItemsForDropdown(int userId) throws SQLException {
        List<MyFacet> allItems = getItemsForDropdown();
        
        if (allItems.isEmpty()) {
            return allItems;
        }
        
        List<Integer> allIds = allItems.stream()
                .map(MyFacet::getId)
                .collect(Collectors.toList());
        
        Set<Integer> accessibleIds = SegmentAccessService
                .getAccessibleObjectIdsInSegments(userId, "MyFacet", allIds);
        
        return allItems.stream()
                .filter(item -> accessibleIds.contains(item.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get items for parent picker (excluding current item) filtered by segment
     */
    public List<MyFacet> getItemsForParentPicker(int excludeId, int userId) throws SQLException {
        // First get all items except the one being edited
        List<MyFacet> allItems = getItemsForDropdown().stream()
                .filter(item -> item.getId() != excludeId)
                .collect(Collectors.toList());
        
        if (allItems.isEmpty()) {
            return allItems;
        }
        
        List<Integer> allIds = allItems.stream()
                .map(MyFacet::getId)
                .collect(Collectors.toList());
        
        Set<Integer> accessibleIds = SegmentAccessService
                .getAccessibleObjectIdsInSegments(userId, "MyFacet", allIds);
        
        return allItems.stream()
                .filter(item -> accessibleIds.contains(item.getId()))
                .collect(Collectors.toList());
    }
    
    // ========== HELPER METHODS ==========
    
    private MyFacet mapResultSetToItem(ResultSet rs) throws SQLException {
        MyFacet item = new MyFacet();
        item.setId(rs.getInt("id"));
        item.setName(rs.getString("name"));
        item.setDescription(rs.getString("description"));
        item.setParentId(rs.getObject("parent_id", Integer.class));
        item.setStatus(rs.getInt("status"));
        return item;
    }
}
```

---

## Example: Complete Servlet Implementation Pattern

```java
package com.example.budg_v2;

import com.example.budg_v2.model.MyFacet;
import com.example.budg_v2.service.MyFacetService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/my-facet/*")
public class MyFacetServlet extends HttpServlet {
    
    private MyFacetService service;
    
    @Override
    public void init() {
        this.service = new MyFacetService();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        // Set response headers
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
            String pathInfo = request.getPathInfo();
            
            // STEP 1: Get current user ID from request
            int userId = UserContextUtil.getCurrentUserId(request);
            System.out.println("MyFacetServlet: userId = " + userId);
            
            // STEP 2: Use segment-filtered methods based on userId
            if (pathInfo == null || "/".equals(pathInfo) || "/list".equals(pathInfo)) {
                // Return all items (segment-filtered)
                List<MyFacet> items = userId > 0 ? 
                        service.getAllItems(userId) :  // Filtered
                        service.getAllItems();          // Unfiltered fallback
                        
                sendJsonResponse(response, items);
                
            } else if ("/hierarchy".equals(pathInfo)) {
                // Return hierarchy (segment-filtered)
                List<MyFacet> items = userId > 0 ? 
                        service.getAllItems(userId) : 
                        service.getAllItems();
                        
                sendHierarchyResponse(response, items);
                
            } else if ("/dropdown".equals(pathInfo) || "/parent-picker".equals(pathInfo)) {
                // Return dropdown list (segment-filtered)
                String excludeIdParam = request.getParameter("excludeId");
                int excludeId = excludeIdParam != null ? Integer.parseInt(excludeIdParam) : 0;
                
                List<MyFacet> items;
                if (excludeId > 0) {
                    items = userId > 0 ? 
                            service.getItemsForParentPicker(excludeId, userId) : 
                            service.getItemsForParentPicker(excludeId);
                } else {
                    items = userId > 0 ? 
                            service.getItemsForDropdown(userId) : 
                            service.getItemsForDropdown();
                }
                
                sendJsonResponse(response, items);
                
            } else if (pathInfo.matches("/\\d+")) {
                // Get single item by ID
                int id = Integer.parseInt(pathInfo.substring(1));
                MyFacet item = service.getItemById(id);
                
                if (item != null) {
                    // Optionally check segment access for single item
                    if (userId > 0 && !service.canUserAccessItem(userId, id)) {
                        JsonUtil.sendErrorResponse(response.getWriter(), 
                                "Access denied: You don't have permission to view this item", 403);
                        return;
                    }
                    sendSingleItemResponse(response, item);
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Item not found", 404);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        }
    }
    
    private void sendJsonResponse(HttpServletResponse response, List<MyFacet> items) 
            throws IOException {
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", items.size());
        
        com.google.gson.JsonArray dataArray = new com.google.gson.JsonArray();
        for (MyFacet item : items) {
            com.google.gson.JsonObject itemJson = new com.google.gson.JsonObject();
            itemJson.addProperty("id", item.getId());
            itemJson.addProperty("name", item.getName());
            itemJson.addProperty("description", item.getDescription());
            itemJson.addProperty("parentId", item.getParentId());
            dataArray.add(itemJson);
        }
        jsonResponse.add("data", dataArray);
        
        response.getWriter().write(jsonResponse.toString());
    }
    
    private void sendHierarchyResponse(HttpServletResponse response, List<MyFacet> items) 
            throws IOException {
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (MyFacet item : items) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("id", item.getId());
            o.addProperty("name", item.getName());
            o.addProperty("description", item.getDescription());
            o.addProperty("parentId", item.getParentId());
            arr.add(o);
        }
        response.getWriter().write(arr.toString());
    }
    
    private void sendSingleItemResponse(HttpServletResponse response, MyFacet item) 
            throws IOException {
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        // Add item data...
        response.getWriter().write(jsonResponse.toString());
    }
}
```

---

## Object Type String Reference

When calling `SegmentAccessService.getAccessibleObjectIdsInSegments()`, use these exact strings for `objectType`:

| Facet | Object Type String |
|-------|-------------------|
| Glossary | `"Glossary"` |
| Policy | `"Policy"` |
| System | `"System"` |
| Regulation | `"Regulation"` |
| Project | `"Project"` |
| Process | `"Process"` |
| Product | `"Product"` |
| Client | `"Client"` |
| OrgUnit | `"OrgUnit"` |
| BusinessArea | `"BusinessArea"` |
| Capability | `"Capability"` |
| Committee | `"Committee"` |
| LegalEntity | `"Legal"` |
| Dataset | `"Dataset"` |

---

*Last Updated: November 2024*

