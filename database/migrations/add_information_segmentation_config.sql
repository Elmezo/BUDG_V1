-- ============================================
-- Add Information Segmentation configuration to app_config table
-- This flag controls Information Segmentation feature
-- Default: false (disabled)
-- ============================================

INSERT INTO app_config (config_key, definition)
VALUES ('INFORMATION_SEGMENTATION_ENABLED', 'false')
ON DUPLICATE KEY UPDATE definition = 'false';

-- ============================================
-- Add Enterprise Segment Default configuration to app_config table
-- This flag controls if Enterprise Segment is the default segment while viewing BUDG content
-- Default: false (disabled)
-- ============================================

INSERT INTO app_config (config_key, definition)
VALUES ('ENTERPRISE_SEGMENT_DEFAULT', 'false')
ON DUPLICATE KEY UPDATE definition = 'false';

-- ============================================
-- Add Assigned Segments Default configuration to app_config table
-- This flag controls if Assigned Segments is the default segment while viewing BUDG content
-- Default: false (disabled)
-- ============================================

INSERT INTO app_config (config_key, definition)
VALUES ('ASSIGNED_SEGMENTS_DEFAULT', 'false')
ON DUPLICATE KEY UPDATE definition = 'false';

-- ============================================
-- Add Default Segment settings to system_settings table
-- These settings are managed through Admin Panel → System Settings → Default Segment group
-- ============================================

INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('DefaultSegment', 'enterprise_segment_default', 'false', 'boolean')
ON DUPLICATE KEY UPDATE setting_value = 'false';

INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('DefaultSegment', 'assigned_segments_default', 'false', 'boolean')
ON DUPLICATE KEY UPDATE setting_value = 'false';

