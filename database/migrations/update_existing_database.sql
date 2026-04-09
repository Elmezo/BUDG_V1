-- ============================================
-- SQL Statement to update existing database
-- Based on current data in system_settings and app_config tables
-- ============================================

-- Step 1: Update group name from "Default Segment" to "DefaultSegment" in system_settings
UPDATE system_settings 
SET setting_group = 'DefaultSegment' 
WHERE setting_group = 'Default Segment';

-- Step 2: Remove information_segmentation_enabled setting (no longer needed)
DELETE FROM system_settings 
WHERE setting_group = 'DefaultSegment' 
AND setting_key = 'information_segmentation_enabled';

-- Step 3: Ensure enterprise_segment_default exists with current value
-- (This will keep existing value if it exists, or insert 'false' if it doesn't)
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
SELECT 'DefaultSegment', 'enterprise_segment_default', 
       COALESCE((SELECT setting_value FROM system_settings WHERE setting_group = 'DefaultSegment' AND setting_key = 'enterprise_segment_default'), 'false'),
       'boolean'
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings 
    WHERE setting_group = 'DefaultSegment' 
    AND setting_key = 'enterprise_segment_default'
);

-- Step 4: Ensure assigned_segments_default exists with current value
-- (This will keep existing value if it exists, or insert 'false' if it doesn't)
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
SELECT 'DefaultSegment', 'assigned_segments_default',
       COALESCE((SELECT setting_value FROM system_settings WHERE setting_group = 'DefaultSegment' AND setting_key = 'assigned_segments_default'), 'false'),
       'boolean'
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings 
    WHERE setting_group = 'DefaultSegment' 
    AND setting_key = 'assigned_segments_default'
);

-- Note: The app_config table entries (INFORMATION_SEGMENTATION_ENABLED, ENTERPRISE_SEGMENT_DEFAULT, ASSIGNED_SEGMENTS_DEFAULT) 
-- can remain as they are - they are not used for the new system settings but may be used by other parts of the system.

