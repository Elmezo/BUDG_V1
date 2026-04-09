-- ============================================
-- Add information_segmentation_enabled setting to system_settings table
-- This setting controls Information Segmentation feature from Admin Panel
-- ============================================

INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('DefaultSegment', 'information_segmentation_enabled', 'false', 'boolean')
ON DUPLICATE KEY UPDATE setting_value = 'false';

