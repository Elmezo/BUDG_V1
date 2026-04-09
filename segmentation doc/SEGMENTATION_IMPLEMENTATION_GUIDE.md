# Segmentation Implementation Guide

## Table of Contents
1. [Overview](#overview)
2. [Core Concepts](#core-concepts)
3. [Database Structure](#database-structure)
4. [Core Services](#core-services)
5. [Implementation Pattern](#implementation-pattern)
6. [Facet Implementations](#facet-implementations)
7. [API Endpoints](#api-endpoints)
8. [Frontend Integration](#frontend-integration)
9. [How to Add Segment Filtering to a New Facet](#how-to-add-segment-filtering-to-a-new-facet)

---

## Overview

The segmentation system provides controlled access to content within BUDG by creating "fenced areas" (Segments) that restrict which users can view and edit specific objects.

### Key Rules

1. **Segment Types**:
   - **Enterprise Segment**: Default segment, all users have access
   - **Private Segments**: Custom segments with limited user access

2. **Hierarchy Constraints**:
   - Parent-child objects MUST be in the same segment
   - If a child is in Segment A, its parent must also be in Segment A

3. **Cross-Segment Relationships**:
   - Enterprise ↔ Private: **ALLOWED**
   - Private ↔ Private (different segments): **NOT ALLOWED**

4. **Access Control**:
   - Users can only view/edit objects in segments they have access to
   - Super Admins have access to all segments

---

## Core Concepts

### Segment Assignment
- Objects are assigned to segments via the `segment_objects` table
- Users are assigned to segments via the `segment_users` table
- An object not assigned to any segment is considered to be in the Enterprise segment

### Access Determination
1. Check if user is Super Admin → has access to everything
2. Get list of segments the user has access to
3. For each object, check if it's in one of the user's accessible segments
4. Objects in Enterprise segment (no segment assignment) are accessible to all

---

## Database Structure

### Main Tables

```sql
-- Segments table
CREATE TABLE segments (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type ENUM('enterprise', 'private') DEFAULT 'private',
    is_active BOOLEAN DEFAULT TRUE,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Segment-Object assignments
CREATE TABLE segment_objects (
    id INT PRIMARY KEY AUTO_INCREMENT,
    segment_id INT NOT NULL,
    object_id INT NOT NULL,
    object_type VARCHAR(50) NOT NULL,  -- 'Glossary', 'Policy', 'Process', etc.
    assigned_by INT,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (segment_id) REFERENCES segments(id),
    UNIQUE KEY unique_segment_object (segment_id, object_id, object_type)
);

-- Segment-User assignments
CREATE TABLE segment_users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    segment_id INT NOT NULL,
    user_id INT NOT NULL,
    role ENUM('viewer', 'editor', 'admin') DEFAULT 'viewer',
    assigned_by INT,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (segment_id) REFERENCES segments(id),
    UNIQUE KEY unique_segment_user (segment_id, user_id)
);
```

### Object Types Used
- `Glossary`
- `Policy`
- `System`
- `Regulation`
- `Project`
- `Process`
- `Product`
- `Client`
- `OrgUnit`
- `BusinessArea`
- `Capability`
- `Committee`
- `Legal` (LegalEntity)
- `Dataset`

---

## Core Services

### 1. SegmentAccessService

**Location**: `src/main/java/com/example/budg_v2/service/SegmentAccessService.java`

**Purpose**: Handles all segment-based access control logic.

#### Key Methods

```java
/**
 * Check if a user can access a specific object
 * @param userId The user ID
 * @param objectId The object ID
 * @param objectType The type of object (e.g., "Glossary", "Policy")
 * @return true if user can access the object
 */
public static boolean canAccessObject(int userId, int objectId, String objectType) throws SQLException

/**
 * Check if a user can edit a specific object
 * @param userId The user ID
 * @param objectId The object ID
 * @param objectType The type of object
 * @return true if user can edit the object
 */
public static boolean canEditObject(int userId, int objectId, String objectType) throws SQLException

/**
 * Get all segment IDs that a user has access to
 * @param userId The user ID
 * @return Set of segment IDs
 */
public static Set<Integer> getUserAccessibleSegments(int userId) throws SQLException

/**
 * Filter a list of object IDs to only those accessible by the user
 * @param userId The user ID
 * @param objectType The type of objects
 * @param objectIds List of object IDs to filter
 * @return Set of accessible object IDs
 */
public static Set<Integer> getAccessibleObjectIdsInSegments(int userId, String objectType, List<Integer> objectIds) throws SQLException

/**
 * Get the segment ID for a specific object
 * @param objectId The object ID
 * @param objectType The type of object
 * @return The segment ID, or null if not assigned to any segment (Enterprise)
 */
public static Integer getObjectSegmentId(int objectId, String objectType) throws SQLException

/**
 * Check if a segment is an Enterprise segment
 * @param segmentId The segment ID
 * @return true if it's an Enterprise segment
 */
public static boolean isEnterpriseSegment(Integer segmentId) throws SQLException
```

#### Usage Example

```java
// Check if user can access an object
int userId = UserContextUtil.getCurrentUserId(request);
boolean canAccess = SegmentAccessService.canAccessObject(userId, objectId, "Glossary");

// Filter a list of objects by segment access
List<Integer> allIds = objects.stream().map(Object::getId).collect(Collectors.toList());
Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIdsInSegments(userId, "Glossary", allIds);
List<MyObject> filteredObjects = objects.stream()
    .filter(obj -> accessibleIds.contains(obj.getId()))
    .collect(Collectors.toList());
```

---

### 2. SegmentValidationService

**Location**: `src/main/java/com/example/budg_v2/service/SegmentValidationService.java`

**Purpose**: Validates segment hierarchy and cross-segment relationship rules.

#### Key Methods

```java
/**
 * Validate that parent and child are in the same segment
 * @return ValidationResult with isValid flag and error message if invalid
 */
public static ValidationResult validateParentChildSegment(
    int childObjectId, 
    String childObjectType, 
    int parentId, 
    String parentObjectType
) throws SQLException

/**
 * Validate cross-segment relationship rules
 * Enterprise ↔ Private: ALLOWED
 * Private ↔ Private (different): NOT ALLOWED
 * @return ValidationResult with isValid flag and error message if invalid
 */
public static ValidationResult validateCrossSegmentRelationship(
    int sourceObjectId, 
    String sourceObjectType, 
    int targetObjectId, 
    String targetObjectType
) throws SQLException

/**
 * Move an object to a different segment
 */
public static boolean moveObjectToSegment(
    int objectId, 
    String objectType, 
    int newSegmentId, 
    int userId
) throws SQLException
```

#### ValidationResult Class

```java
public static class ValidationResult {
    private boolean valid;
    private String message;
    private String sourceSegmentName;
    private String targetSegmentName;
    private Integer sourceSegmentId;
    private Integer targetSegmentId;
    
    // Getters and setters...
}
```

---

## Implementation Pattern

### Standard Pattern for Adding Segment Filtering to a Facet

#### Step 1: Update the DAO

Add imports:
```java
import com.example.budg_v2.service.SegmentAccessService;
import java.util.Set;
import java.util.stream.Collectors;
```

Add segment-filtered method:
```java
/**
 * Get all [objects] filtered by user's segment access
 */
public List<MyObject> getAllObjects(int userId) throws SQLException {
    List<MyObject> allObjects = getAllObjects(); // Call the original method
    if (allObjects.isEmpty()) {
        return allObjects;
    }
    
    // Get accessible object IDs for this user
    List<Integer> allIds = allObjects.stream()
        .map(MyObject::getId)
        .collect(Collectors.toList());
    Set<Integer> accessibleIds = SegmentAccessService
        .getAccessibleObjectIdsInSegments(userId, "MyObjectType", allIds);
    
    // Filter to only accessible objects
    return allObjects.stream()
        .filter(obj -> accessibleIds.contains(obj.getId()))
        .collect(Collectors.toList());
}
```

#### Step 2: Update the Service

Add the new method that delegates to DAO:
```java
public List<MyObject> getAllObjects(int userId) throws SQLException {
    return myDAO.getAllObjects(userId);
}
```

#### Step 3: Update the Servlet

Add import:
```java
import com.example.budg_v2.util.UserContextUtil;
```

Update the endpoint:
```java
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    // Get current user ID
    int userId = UserContextUtil.getCurrentUserId(request);
    
    // Use segment-filtered method if user is logged in
    List<MyObject> objects = userId > 0 ? 
        service.getAllObjects(userId) : 
        service.getAllObjects();
    
    // Return response...
}
```

---

## Facet Implementations

### 1. Glossary

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/GlossaryDAO.java`
- `src/main/java/com/example/budg_v2/GlossaryListServlet.java`
- `src/main/java/com/example/budg_v2/GlossaryServlet.java`

**Methods Added**:
- `GlossaryDAO.listGlossary(int userId)` - Filtered glossary list
- `GlossaryDAO.getGlossaryHierarchy(int glossaryId, int userId)` - Filtered hierarchy

**Endpoints Updated**:
- `GET /api/glossary` - Returns filtered list
- `GET /api/glossary/list` - Returns filtered list
- `GET /api/glossary/hierarchy/{id}` - Returns filtered hierarchy

---

### 2. Policy

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/PolicyDAO.java`
- `src/main/java/com/example/budg_v2/service/PolicyService.java`
- `src/main/java/com/example/budg_v2/PolicyServlet.java`

**Methods Added**:
- `PolicyDAO.getAllPolicies(int userId)`
- `PolicyDAO.getPoliciesForDropdown(int userId)`
- `PolicyDAO.getPoliciesForParentPicker(int excludeId, int userId)`
- `PolicyService.getAllPolicies(int userId)`
- `PolicyService.getPoliciesForDropdown(int userId)`
- `PolicyService.getPoliciesForParentPicker(int excludeId, int userId)`

**Endpoints Updated**:
- `GET /api/policy` - Returns filtered list
- `GET /api/policy/list` - Returns filtered dropdown list
- `GET /api/policy/parent-picker` - Returns filtered parent picker list

---

### 3. System

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/SystemDAO.java`
- `src/main/java/com/example/budg_v2/service/SystemService.java`
- `src/main/java/com/example/budg_v2/SystemListServlet.java`

**Methods Added**:
- `SystemDAO.listSystems(int userId)`
- `SystemDAO.getSystemsForParentPicker(int excludeId, int userId)`
- `SystemService.listSystems(int userId)`
- `SystemService.getSystemsForParentPicker(int excludeId, int userId)`

**Endpoints Updated**:
- `GET /api/systems` - Returns filtered list
- `GET /api/systems/parent-picker` - Returns filtered parent picker list

---

### 4. Regulation

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/RegulationDAO.java`
- `src/main/java/com/example/budg_v2/service/RegulationService.java`
- `src/main/java/com/example/budg_v2/RegulationServlet.java`

**Methods Added**:
- `RegulationDAO.getAllRegulations(int userId)`
- `RegulationDAO.getRegulationsForDropdown(int userId)`
- `RegulationDAO.getRegulationsForParentPicker(int excludeId, int userId)`
- `RegulationService.getAllRegulations(int userId)`
- `RegulationService.getRegulationsForDropdown(int userId)`
- `RegulationService.getRegulationsForParentPicker(int excludeId, int userId)`

**Endpoints Updated**:
- `GET /api/regulation` - Returns filtered list
- `GET /api/regulation/list` - Returns filtered dropdown list
- `GET /api/regulation/parent-picker` - Returns filtered parent picker list

---

### 5. Project

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/ProjectDAO.java`
- `src/main/java/com/example/budg_v2/service/ProjectService.java`
- `src/main/java/com/example/budg_v2/ProjectServlet.java`

**Methods Added**:
- `ProjectDAO.getAllProjects(int userId)`
- `ProjectDAO.getAllProjectsForDropdown(int userId)`
- `ProjectDAO.getProjectsForParentPicker(int excludeId, int userId)`
- `ProjectService.getAllProjects(int userId)`
- `ProjectService.getAllProjectsForDropdown(int userId)`
- `ProjectService.getProjectsForParentPicker(int excludeId, int userId)`

**Endpoints Updated**:
- `GET /api/project` - Returns filtered list
- `GET /api/project/list` - Returns filtered dropdown list
- `GET /api/project/hierarchy` - Returns filtered hierarchy
- `GET /api/project/parent-picker` - Returns filtered parent picker list

---

### 6. Process

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/ProcessDAO.java`
- `src/main/java/com/example/budg_v2/service/ProcessService.java`
- `src/main/java/com/example/budg_v2/ProcessServlet.java`

**Methods Added**:
- `ProcessDAO.getAllProcesses(int userId)`
- `ProcessDAO.getAllProcessesForDropdown(int userId)`
- `ProcessDAO.getProcessesForParentPicker(int excludeId, int userId)`
- `ProcessService.getAllProcesses(int userId)`
- `ProcessService.getAllProcessesForDropdown(int userId)`
- `ProcessService.getProcessesForParentPicker(int excludeId, int userId)`

**Endpoints Updated**:
- `GET /api/process` - Returns filtered list
- `GET /api/process/list` - Returns filtered dropdown list
- `GET /api/process/parent-picker` - Returns filtered parent picker list

---

### 7. Product

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/ProductDAO.java`
- `src/main/java/com/example/budg_v2/service/ProductService.java`
- `src/main/java/com/example/budg_v2/ProductServlet.java`

**Methods Added**:
- `ProductDAO.getAllProducts(int userId)`
- `ProductDAO.getProductsForDropdown(int userId)`
- `ProductService.getAllProducts(int userId)`
- `ProductService.getProductsForDropdown(int userId)`

**Endpoints Updated**:
- `GET /api/product/list` - Returns filtered list
- `GET /api/product/hierarchy` - Returns filtered hierarchy
- `GET /api/product/parent-picker` - Returns filtered parent picker list

---

### 8. Client

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/ClientDAO.java`
- `src/main/java/com/example/budg_v2/service/ClientService.java`
- `src/main/java/com/example/budg_v2/ClientServlet.java`

**Methods Added**:
- `ClientDAO.getAllClients(int userId)`
- `ClientService.getAllClients(int userId)`
- `ClientService.getParentClients(int userId)`

**Endpoints Updated**:
- `GET /api/client` - Returns filtered list
- `GET /api/client/hierarchy` - Returns filtered hierarchy
- `GET /api/client/parent-clients` - Returns filtered parent clients

---

### 9. OrgUnit

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/OrgUnitDAO.java`
- `src/main/java/com/example/budg_v2/service/OrgUnitService.java`
- `src/main/java/com/example/budg_v2/OrgUnitServlet.java`

**Methods Added**:
- `OrgUnitDAO.getAllOrgUnits(int userId)`
- `OrgUnitService.getAllOrgUnits(int userId)`

**Endpoints Updated**:
- `GET /api/org-units` - Returns filtered list
- `GET /api/org-units/search` - Returns filtered search results

---

### 10. BusinessArea

**Files Modified**:
- `src/main/java/com/example/budg_v2/service/BusinessAreaService.java`
- `src/main/java/com/example/budg_v2/BusinessAreaServlet.java`

**Methods Added**:
- `BusinessAreaService.getAllBusinessAreas(int userId)`
- `BusinessAreaService.getBusinessAreasForDropdown(int userId)`

**Endpoints Updated**:
- `GET /api/business-areas` - Returns filtered list
- `GET /api/business-areas/list` - Returns filtered list
- `GET /api/business-areas/hierarchy` - Returns filtered hierarchy
- `GET /api/business-areas/dropdown` - Returns filtered dropdown list

---

### 11. Capability

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/CapabilityDAO.java`
- `src/main/java/com/example/budg_v2/service/CapabilityService.java`
- `src/main/java/com/example/budg_v2/CapabilityServlet.java`

**Methods Added**:
- `CapabilityDAO.getAllCapabilities(int userId)`
- `CapabilityDAO.getAllCapabilitiesForDropdown(int userId)`
- `CapabilityService.getAllCapabilities(int userId)`
- `CapabilityService.getAllCapabilitiesForDropdown(int userId)`

**Endpoints Updated**:
- `GET /api/capabilities` - Returns filtered list
- `GET /api/capabilities/list` - Returns filtered list
- `GET /api/capabilities/hierarchy` - Returns filtered hierarchy
- `GET /api/capabilities/dropdown` - Returns filtered dropdown list

---

### 12. Committee

**Files Modified**:
- `src/main/java/com/example/budg_v2/CommitteeServlet.java`

**Methods Added**:
- `CommitteeServlet.getAllCommittees(Connection conn, int userId)` - Private method with segment filtering

**Endpoints Updated**:
- `GET /api/committee` - Returns filtered list
- `GET /api/committee/hierarchy` - Returns filtered hierarchy

---

### 13. LegalEntity

**Files Modified**:
- `src/main/java/com/example/budg_v2/dao/LegalDAO.java`
- `src/main/java/com/example/budg_v2/service/LegalService.java`
- `src/main/java/com/example/budg_v2/LegalEntityServlet.java`

**Methods Added**:
- `LegalDAO.getAllLegals(int userId)`
- `LegalService.getAllLegals(int userId)`

**Endpoints Updated**:
- `GET /api/LegalEntity` - Returns filtered list
- `GET /api/LegalEntity/hierarchy` - Returns filtered hierarchy

---

## API Endpoints

### Segment Management Endpoints

**Base URL**: `/api/segments`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/segments` | Get all segments |
| GET | `/api/segments/{id}` | Get segment by ID |
| POST | `/api/segments` | Create new segment |
| PUT | `/api/segments/{id}` | Update segment |
| DELETE | `/api/segments/{id}` | Delete segment |
| POST | `/api/segments/validate-hierarchy` | Validate parent-child segment constraint |
| POST | `/api/segments/validate-relationship` | Validate cross-segment relationship |
| POST | `/api/segments/move-parent` | Move parent object to child's segment |
| GET | `/api/segments/{id}/users` | Get users in a segment |
| POST | `/api/segments/{id}/users` | Add user to segment |
| DELETE | `/api/segments/{id}/users/{userId}` | Remove user from segment |
| GET | `/api/segments/{id}/objects` | Get objects in a segment |
| POST | `/api/segments/{id}/objects` | Add object to segment |
| DELETE | `/api/segments/{id}/objects/{objectType}/{objectId}` | Remove object from segment |

### Validation Request Bodies

**Validate Hierarchy**:
```json
{
  "childObjectId": 123,
  "childObjectType": "Glossary",
  "parentId": 456,
  "parentObjectType": "Glossary"
}
```

**Validate Cross-Segment Relationship**:
```json
{
  "sourceObjectId": 123,
  "sourceObjectType": "Glossary",
  "targetObjectId": 456,
  "targetObjectType": "Policy"
}
```

---

## Frontend Integration

### Segment Field Component

**Location**: `src/main/webapp/assets/js/segment-field.js`

This reusable component handles:
- Segment selection dropdown
- Hierarchy validation when selecting a parent
- Warning dialogs for segment conflicts
- Moving parent to child's segment if user confirms

#### Usage in Edit Forms

```javascript
// Initialize segment field
const segmentField = new SegmentField({
    containerId: 'segment-container',
    objectId: currentObjectId,
    objectType: 'Glossary',
    onSegmentChange: (segmentId) => {
        // Handle segment change
    }
});

// Validate hierarchy when parent is selected
async function onParentSelected(parentId, parentSegmentId) {
    const result = await segmentField.validateHierarchy(
        currentObjectId, 
        'Glossary', 
        parentId, 
        parentSegmentId
    );
    
    if (!result.valid) {
        // Show warning dialog
        segmentField.showHierarchyConflictDialog(
            currentObjectName,
            currentSegmentName,
            parentName,
            parentSegmentName
        );
    }
}
```

---

## How to Add Segment Filtering to a New Facet

### Complete Example: Adding Segment Filtering to "MyNewFacet"

#### Step 1: Update the DAO

```java
// File: src/main/java/com/example/budg_v2/dao/MyNewFacetDAO.java

package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.MyNewFacet;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MyNewFacetDAO {
    
    // Existing method - no changes needed
    public List<MyNewFacet> getAllItems() throws SQLException {
        List<MyNewFacet> items = new ArrayList<>();
        String sql = "SELECT * FROM my_new_facet WHERE deleted_at IS NULL ORDER BY name";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        }
        return items;
    }
    
    // NEW: Add segment-filtered version
    /**
     * Get all items filtered by user's segment access
     */
    public List<MyNewFacet> getAllItems(int userId) throws SQLException {
        List<MyNewFacet> allItems = getAllItems();
        if (allItems.isEmpty()) {
            return allItems;
        }
        
        // Get accessible item IDs for this user
        List<Integer> allIds = allItems.stream()
            .map(MyNewFacet::getId)
            .collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService
            .getAccessibleObjectIdsInSegments(userId, "MyNewFacet", allIds);
        
        // Filter to only accessible items
        return allItems.stream()
            .filter(item -> accessibleIds.contains(item.getId()))
            .collect(Collectors.toList());
    }
    
    // If you have dropdown/parent-picker methods, add filtered versions too
    public List<MyNewFacet> getItemsForDropdown() throws SQLException {
        // Original implementation...
    }
    
    public List<MyNewFacet> getItemsForDropdown(int userId) throws SQLException {
        List<MyNewFacet> allItems = getItemsForDropdown();
        if (allItems.isEmpty()) {
            return allItems;
        }
        
        List<Integer> allIds = allItems.stream()
            .map(MyNewFacet::getId)
            .collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService
            .getAccessibleObjectIdsInSegments(userId, "MyNewFacet", allIds);
        
        return allItems.stream()
            .filter(item -> accessibleIds.contains(item.getId()))
            .collect(Collectors.toList());
    }
    
    private MyNewFacet mapResultSetToItem(ResultSet rs) throws SQLException {
        MyNewFacet item = new MyNewFacet();
        item.setId(rs.getInt("id"));
        item.setName(rs.getString("name"));
        // ... map other fields
        return item;
    }
}
```

#### Step 2: Update the Service

```java
// File: src/main/java/com/example/budg_v2/service/MyNewFacetService.java

package com.example.budg_v2.service;

import com.example.budg_v2.dao.MyNewFacetDAO;
import com.example.budg_v2.model.MyNewFacet;

import java.sql.SQLException;
import java.util.List;

public class MyNewFacetService {
    
    private final MyNewFacetDAO dao;
    
    public MyNewFacetService() {
        this.dao = new MyNewFacetDAO();
    }
    
    // Existing method
    public List<MyNewFacet> getAllItems() throws SQLException {
        return dao.getAllItems();
    }
    
    // NEW: Add segment-filtered version
    public List<MyNewFacet> getAllItems(int userId) throws SQLException {
        return dao.getAllItems(userId);
    }
    
    // Existing method
    public List<MyNewFacet> getItemsForDropdown() throws SQLException {
        return dao.getItemsForDropdown();
    }
    
    // NEW: Add segment-filtered version
    public List<MyNewFacet> getItemsForDropdown(int userId) throws SQLException {
        return dao.getItemsForDropdown(userId);
    }
}
```

#### Step 3: Update the Servlet

```java
// File: src/main/java/com/example/budg_v2/MyNewFacetServlet.java

package com.example.budg_v2;

import com.example.budg_v2.model.MyNewFacet;
import com.example.budg_v2.service.MyNewFacetService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.UserContextUtil;  // NEW: Import this
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/my-new-facet/*")
public class MyNewFacetServlet extends HttpServlet {
    
    private MyNewFacetService service;
    
    @Override
    public void init() {
        this.service = new MyNewFacetService();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
            String pathInfo = request.getPathInfo();
            
            // NEW: Get current user ID for segment filtering
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (pathInfo == null || "/".equals(pathInfo) || "/list".equals(pathInfo)) {
                // NEW: Use segment-filtered method
                List<MyNewFacet> items = userId > 0 ? 
                    service.getAllItems(userId) : 
                    service.getAllItems();
                    
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("success", true);
                jsonResponse.addProperty("count", items.size());
                // Add items to response...
                response.getWriter().write(jsonResponse.toString());
                
            } else if ("/dropdown".equals(pathInfo)) {
                // NEW: Use segment-filtered method
                List<MyNewFacet> items = userId > 0 ? 
                    service.getItemsForDropdown(userId) : 
                    service.getItemsForDropdown();
                // Return response...
                
            } else if ("/hierarchy".equals(pathInfo)) {
                // NEW: Use segment-filtered method
                List<MyNewFacet> items = userId > 0 ? 
                    service.getAllItems(userId) : 
                    service.getAllItems();
                // Build and return hierarchy response...
            }
            
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), 
                "Database error: " + e.getMessage(), 500);
        }
    }
}
```

#### Step 4: Register the Object Type in SegmentAccessService (if needed)

If your object type isn't already recognized, you may need to add it to the `getAccessibleObjectIdsInSegments` method in `SegmentAccessService`:

```java
// The method already handles any object type dynamically via the segment_objects table,
// so no changes are typically needed. The object_type column stores strings like:
// "Glossary", "Policy", "MyNewFacet", etc.
```

---

## Checklist for New Facet Implementation

- [ ] Add imports to DAO: `SegmentAccessService`, `Set`, `Collectors`
- [ ] Add `getAllXxx(int userId)` method to DAO
- [ ] Add `getXxxForDropdown(int userId)` method to DAO (if applicable)
- [ ] Add `getXxxForParentPicker(int excludeId, int userId)` method to DAO (if applicable)
- [ ] Add corresponding methods to Service class
- [ ] Add `UserContextUtil` import to Servlet
- [ ] Update Servlet to get `userId` from request
- [ ] Update all list/dropdown/hierarchy endpoints to use filtered methods
- [ ] Test with users in different segments
- [ ] Test with user not assigned to any segment (should see Enterprise content only)
- [ ] Test with Super Admin (should see all content)

---

## Troubleshooting

### Common Issues

1. **User sees no data**: Check if user is assigned to any segments
2. **User sees all data**: Check if user is Super Admin or if objects are in Enterprise segment
3. **Filtering not working**: Verify the object_type string matches exactly (case-sensitive)
4. **NullPointerException**: Ensure userId is properly retrieved from request

### Debug Logging

Add debug logging to trace segment access:
```java
System.out.println("User " + userId + " requesting " + objectType + " list");
System.out.println("Accessible segment IDs: " + accessibleSegmentIds);
System.out.println("Total objects: " + allObjects.size());
System.out.println("Filtered objects: " + filteredObjects.size());
```

---

## Version History

- **v7.0**: Initial segmentation implementation
- **v7.1**: Added cross-segment relationship validation
- **v7.2**: Applied segment filtering to all facets

---

*Last Updated: November 2024*

