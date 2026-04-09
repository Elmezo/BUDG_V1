-- ============================================
-- Update Default Segment group name from "Default Segment" to "DefaultSegment"
-- This fixes the issue where settings with spaces in group name don't save properly
-- ============================================

UPDATE system_settings 
SET setting_group = 'DefaultSegment' 
WHERE setting_group = 'Default Segment';

