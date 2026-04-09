-- ============================================
-- Update Default Segment settings
-- 1. Update group name from "Default Segment" to "DefaultSegment"
-- 2. Remove information_segmentation_enabled setting (no longer needed)
-- 3. Keep only enterprise_segment_default and assigned_segments_default
-- ============================================

-- Update group name from "Default Segment" to "DefaultSegment"
UPDATE system_settings 
SET setting_group = 'DefaultSegment' 
WHERE setting_group = 'Default Segment';

-- Remove information_segmentation_enabled setting (no longer needed)
DELETE FROM system_settings 
WHERE setting_group = 'DefaultSegment' 
AND setting_key = 'information_segmentation_enabled';

-- Ensure enterprise_segment_default exists
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('DefaultSegment', 'enterprise_segment_default', 'false', 'boolean')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- Ensure assigned_segments_default exists
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('DefaultSegment', 'assigned_segments_default', 'false', 'boolean')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

