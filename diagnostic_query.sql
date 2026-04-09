-- Diagnostic Query for System ID 128 and User ID 22
-- Run these queries and provide the results (plan: system_128_unison_search_fix)

-- 0. nobject_id exclusion check: is System 128 excluded as a clone in active CR?
--    If this returns rows, 128 is in getActiveNObjectIdsForFacet("system") and is excluded from Unison Search.
SELECT fc.nobject_id, fc.object_id, cr.ID AS cr_id, crs.PrimaryName
FROM system_changes fc
INNER JOIN changerequest cr ON fc.change_request_id = cr.ID
LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID
WHERE fc.nobject_id = 128
  AND cr.Deleted_At IS NULL;
-- If the above returns NO ROWS: nobject_id is NOT the cause. The segment filter (EXISTS/NOT EXISTS) is.

-- 0b. Simulate app segment filter for system 128 (same logic as QueryBuilder)
--     If passes_exists=0 and passes_not_exists=0 then 128 is correctly excluded by the app.
SELECT 
  128 AS system_id,
  (SELECT COUNT(*) FROM segment_x_resource sxr
   JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
   JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
   WHERE orr.Object_ID = 128 AND sot.Type = 'System'
     AND sxr.Segment_ID IN (1) AND sxr.Deleted_At IS NULL) > 0 AS passes_exists,
  (SELECT COUNT(*) FROM segment_x_resource sxr
   JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
   JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
   WHERE orr.Object_ID = 128 AND sot.Type = 'System' AND sxr.Deleted_At IS NULL) = 0 AS passes_not_exists;

-- 0c. All object_reference rows for 128 (any type) and their segment_object_type + segment_x_resource
--     Use this to see if Object_Type_ID matches 'System' and if segment 1 is linked.
SELECT orr.ID AS orr_id, orr.Object_ID, orr.Object_Type_ID, sot.Type AS object_type_name,
       sxr.Segment_ID, sxr.Deleted_At AS sxr_deleted_at
FROM object_reference orr
LEFT JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
LEFT JOIN segment_x_resource sxr ON sxr.Object_Reference_ID = orr.ID AND sxr.Deleted_At IS NULL
WHERE orr.Object_ID = 128
ORDER BY orr.ID, sxr.Segment_ID;

-- 1. Check if System 128 exists
SELECT 
    id,
    Name,
    Description,
    status
FROM system
WHERE id = 128;

-- 2. Get the object_reference record for System 128
SELECT 
    orr.ID,
    orr.Object_ID,
    orr.Object_Type_ID,
    sot.Type
FROM object_reference orr
LEFT JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
WHERE orr.Object_ID = 128
  AND orr.Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'System');

-- 3. Check what segments System 128 is assigned to (via object_reference)
SELECT 
    sxr.Segment_ID,
    s.ID,
    s.Name as segment_name,
    s.Description,
    sxr.Created_At,
    sxr.Deleted_At
FROM segment_x_resource sxr
JOIN segment s ON sxr.Segment_ID = s.ID
WHERE sxr.Object_Reference_ID IN (
    SELECT orr.ID 
    FROM object_reference orr
    WHERE orr.Object_ID = 128
      AND orr.Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'System')
);

-- 4. Check what segments user 22 has access to
SELECT DISTINCT
    segment_id,
    segment_name
FROM v_user_accessible_segments
WHERE user_id = 22
ORDER BY segment_id;

-- 5. Check what segments user 22 has SELECTED in cube filter
SELECT 
    user_id,
    segment_id,
    created_at
FROM user_segment_selection
WHERE user_id = 22
ORDER BY segment_id;

-- 6. Check if segment 1 (Enterprise) exists
SELECT 
    ID,
    Name,
    Description
FROM segment
WHERE ID = 1;

-- 7. Check User 22 details and Org Unit
SELECT 
    ID,
    Name,
    Org_Unit_ID
FROM people
WHERE ID = 22;

-- 8. TEST: Check if System 128 appears in accessible + selected segments
SELECT DISTINCT
    sxr.Segment_ID,
    s.Name as segment_name,
    sys.Name as system_name
FROM segment_x_resource sxr
JOIN segment s ON sxr.Segment_ID = s.ID
JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
JOIN system sys ON orr.Object_ID = sys.id
WHERE sys.id = 128
  AND sxr.Segment_ID IN (
      SELECT DISTINCT vas.segment_id
      FROM v_user_accessible_segments vas
      WHERE vas.user_id = 22
        AND EXISTS (
            SELECT 1 
            FROM user_segment_selection uss
            WHERE uss.user_id = 22 
            AND uss.segment_id = vas.segment_id
        )
  );

-- 9. DIAGNOSTIC: All segment assignments for System 128
SELECT 
    s.ID,
    s.Name,
    sxr.Created_At,
    sxr.Deleted_At,
    IF(sxr.Deleted_At IS NULL, 'Active', 'Deleted') as status
FROM segment_x_resource sxr
JOIN segment s ON sxr.Segment_ID = s.ID
WHERE sxr.Object_Reference_ID IN (
    SELECT orr.ID 
    FROM object_reference orr
    WHERE orr.Object_ID = 128
      AND orr.Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'System')
)
ORDER BY sxr.Created_At DESC;
