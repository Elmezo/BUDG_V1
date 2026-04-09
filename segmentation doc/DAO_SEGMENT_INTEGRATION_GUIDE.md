# SEGMENT INTEGRATION INTO DAOS - IMPLEMENTATION GUIDE

## Overview
This guide shows how to integrate segments into your existing DAO classes using the views and services created.

---

## Step 1: Add ObjectSegmentService Dependency

All DAOs can now use `ObjectSegmentService` to manage segment assignments.

---

## Step 2: DatasetDAO Integration Example

### 2.1 Update INSERT Method

**Before:**
```java
public int insert(Dataset dataset, int userId) throws SQLException {
    String sql = "INSERT INTO dataset (...) VALUES (...)";
    // ... existing code
    int datasetId = rs.getInt(1);
    return datasetId;
}
```

**After:**
```java
import com.example.budg_v2.service.ObjectSegmentService;

public int insert(Dataset dataset, int userId) throws SQLException {
    String sql = "INSERT INTO dataset (...) VALUES (...)";
    // ... existing code to insert dataset
    int datasetId = rs.getInt(1);
    
    // NEW: Assign to segment
    Long segmentId = dataset.getSegmentId() != null ? dataset.getSegmentId() : 1L;
    try {
        ObjectSegmentService.assignObjectToSegment(
            (long) datasetId,
            "Dataset",
            segmentId,
            userId
        );
    } catch (SQLException e) {
        System.err.println("Warning: Failed to assign dataset to segment: " + e.getMessage());
        // Continue anyway - dataset is created, just not assigned to segment
    }
    
    return datasetId;
}
```

### 2.2 Update SELECT/LIST Methods to Use View

**Before:**
```java
public List<Map<String, Object>> getAllDatasets(int userId) throws SQLException {
    String sql = "SELECT * FROM dataset WHERE DeletedDatetime IS NULL";
    // ...
}
```

**After (Option 1 - Use View):**
```java
import com.example.budg_v2.service.SegmentAccessService;

public List<Map<String, Object>> getAllDatasets(int userId) throws SQLException {
    // Use the view that includes segment info
    String sql = """
        SELECT d.*, vds.Segment_ID, vds.Segment_Name
        FROM v_dataset_with_segment vds
        JOIN dataset d ON vds.ID = d.ID
        WHERE d.DeletedDatetime IS NULL
          AND vds.Segment_ID IN (
              SELECT segment_id FROM v_user_accessible_segments WHERE user_id = ?
          )
        ORDER BY d.PrimaryName
    """;
    
    List<Map<String, Object>> datasets = new ArrayList<>();
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setInt(1, userId);
        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> dataset = new HashMap<>();
                dataset.put("id", rs.getInt("ID"));
                dataset.put("name", rs.getString("PrimaryName"));
                dataset.put("definition", rs.getString("definition"));
                dataset.put("segment_id", rs.getInt("Segment_ID")); // NEW
                dataset.put("segment_name", rs.getString("Segment_Name")); // NEW
                // ... other fields
                datasets.add(dataset);
            }
        }
    }
    return datasets;
}
```

**After (Option 2 - Simpler, Direct View):**
```java
public List<Map<String, Object>> getAllDatasets(int userId) throws SQLException {
    String sql = """
        SELECT * FROM v_dataset_with_segment
        WHERE DeletedDatetime IS NULL
          AND Segment_ID IN (
              SELECT segment_id FROM v_user_accessible_segments WHERE user_id = ?
          )
        ORDER BY PrimaryName
    """;
    
    // Process results including Segment_ID and Segment_Name columns
}
```

### 2.3 Update GET BY ID to Include Segment

```java
public Map<String, Object> getDatasetById(int id, int userId) throws SQLException {
    // Check user has access to the segment
    String sql = """
        SELECT vds.* 
        FROM v_dataset_with_segment vds
        WHERE vds.ID = ?
          AND vds.Segment_ID IN (
              SELECT segment_id FROM v_user_accessible_segments WHERE user_id = ?
          )
    """;
    
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setInt(1, id);
        pstmt.setInt(2, userId);
        
        try (ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                Map<String, Object> dataset = new HashMap<>();
                // ... map all fields including Segment_ID and Segment_Name
                dataset.put("segment_id", rs.getInt("Segment_ID"));
                dataset.put("segment_name", rs.getString("Segment_Name"));
                return dataset;
            }
        }
    }
    return null; // Not found or no access
}
```

### 2.4 Update UPDATE Method

```java
public boolean update(Dataset dataset, int userId) throws SQLException {
    // ... existing update code for dataset table
    
    boolean updated = /* existing update logic */;
    
    if (updated && dataset.getSegmentId() != null) {
        // Update segment assignment if changed
        try {
            ObjectSegmentService.assignObjectToSegment(
                (long) dataset.getId(),
                "Dataset",
                dataset.getSegmentId(),
                userId
            );
        } catch (SQLException e) {
            System.err.println("Warning: Failed to update segment: " + e.getMessage());
        }
    }
    
    return updated;
}
```

---

## Step 3: DatasetServlet Integration

### 3.1 Handle segment_id in POST/PUT

```java
@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
        JsonObject json = parseJsonFromRequest(request);
        
        Dataset dataset = new Dataset();
        dataset.setPrimaryName(json.get("name").getAsString());
        dataset.setDefinition(json.get("definition").getAsString());
        
        // NEW: Handle segment_id
        if (json.has("segment_id") && !json.get("segment_id").isJsonNull()) {
            dataset.setSegmentId(json.get("segment_id").getAsLong());
        } else {
            dataset.setSegmentId(1L); // Default to Enterprise
        }
        
        int userId = UserContextUtil.getCurrentUserId(request);
        int datasetId = datasetDAO.insert(dataset, userId);
        
        response.setContentType("application/json");
        response.getWriter().write(gson.toJson(Map.of(
            "success", true,
            "id", datasetId
        )));
        
    } catch (Exception e) {
        e.printStackTrace();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().write(gson.toJson(Map.of(
            "success", false,
            "error", e.getMessage()
        )));
    }
}
```

---

## Step 4: Repeat for All Facets

Apply the same pattern to:
- SystemDAO / SystemServlet
- PolicyDAO / PolicyServlet
- ProcessDAO / ProcessServlet
- GlossaryDAO / GlossaryServlet
- CommitteeDAO / CommitteeServlet
- And all other facets...

---

## Step 5: Search Integration

### Update Search DAO to Filter by Segments

```java
public List<Map<String, Object>> search(String query, int userId) throws SQLException {
    String sql = """
        SELECT 
            d.ID,
            d.PrimaryName AS name,
            d.definition AS description,
            vds.Segment_Name AS segment,
            'Dataset' AS facet_type
        FROM v_dataset_with_segment vds
        JOIN dataset d ON vds.ID = d.ID
        WHERE vds.Segment_ID IN (
            SELECT segment_id FROM v_user_accessible_segments WHERE user_id = ?
        )
        AND (d.PrimaryName LIKE ? OR d.definition LIKE ?)
        AND d.DeletedDatetime IS NULL
        
        UNION ALL
        
        SELECT 
            s.id AS ID,
            s.Name AS name,
            s.Description AS description,
            vss.Segment_Name AS segment,
            'System' AS facet_type
        FROM v_system_with_segment vss
        JOIN system s ON vss.id = s.id
        WHERE vss.Segment_ID IN (
            SELECT segment_id FROM v_user_accessible_segments WHERE user_id = ?
        )
        AND (s.Name LIKE ? OR s.Description LIKE ?)
        AND s.Deleted_datetime IS NULL
        
        -- Add more UNION ALL for other facets
        
        ORDER BY name
        LIMIT 100
    """;
    
    // Execute with userId and search parameters
}
```

---

## Quick Reference

### Object Types for Each Facet
```java
// Use these exact strings in ObjectSegmentService calls
"Dataset"           // for dataset table
"System"            // for system table
"SystemInterface"   // for system_interface table
"Glossary"          // for glossary table
"Policy"            // for policy table
"Process"           // for process table
"Committee"         // for committee table
"Project"           // for project table
"BusinessArea"      // for business_area table
"Capability"        // for capability table
"Client"            // for client table
"LegalEntity"       // for legal_entity table
"Product"           // for product table
"Role"              // for role table
"Regulation"        // for regulation table
"RegulatoryTheme"   // for regulatory_theme table
"Regulator"         // for regulator table
"Geography"         // for geography table
```

### Common Queries

```java
// Assign object to segment
ObjectSegmentService.assignObjectToSegment(objectId, "Dataset", segmentId, userId);

// Get object's segment
Long segmentId = ObjectSegmentService.getObjectSegment(objectId, "Dataset");

// Get all datasets in a segment
List<Long> datasetIds = ObjectSegmentService.getObjectsInSegment(segmentId, "Dataset");

// Count objects by type in segment
Map<String, Integer> counts = ObjectSegmentService.countObjectsByType(segmentId);

// Check if user can access segment
boolean hasAccess = SegmentAccessService.hasSegmentAccess(userId, segmentId);

// Get user's accessible segments
List<Map<String, Object>> segments = SegmentAccessService.getUserAccessibleSegments(userId);
```

---

## Testing Checklist

- [ ] Create a dataset with segment_id = 1 (Enterprise)
- [ ] Create a dataset with a different segment
- [ ] Verify only users with access can see the dataset
- [ ] Test updating dataset's segment
- [ ] Test search filtering by segments
- [ ] Test that Enterprise is always visible
- [ ] Test segment dropdown shows only accessible segments

---

## Performance Tips

1. **Use Views** - They're indexed and optimized
2. **Cache segment access** - Cache getUserSegmentIds() results per request
3. **Batch operations** - Assign multiple objects at once
4. **Index segments** - Ensure views have proper indexes (already done in SQL script)

---

## Complete!

All DAOs can now:
✅ Assign objects to segments
✅ Filter by accessible segments
✅ Show segment info in results
✅ Respect user segment access

The frontend requires **NO changes** - segment_id is just another field! 🎉

