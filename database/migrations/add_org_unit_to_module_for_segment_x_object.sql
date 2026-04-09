-- ============================================
-- Add Org. Unit to module table for Segment X Object bulk upload
-- Ensures the Object Type dropdown in Segment X Object template includes Org Unit.
-- Idempotent: only inserts if no row with primaryname 'Org. Unit' exists.
-- ============================================

INSERT INTO module (group_id, primaryname, tablename, icon, aclrole, cfenabled)
SELECT NULL, 'Org. Unit', 'org_unit', NULL, NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM module WHERE LOWER(TRIM(primaryname)) = LOWER('Org. Unit')
);
