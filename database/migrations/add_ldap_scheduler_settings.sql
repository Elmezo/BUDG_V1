-- ============================================
-- Add Scheduled Sync Settings to system_settings table
-- This migration adds settings for automatic scheduled LDAP synchronization
-- ============================================

-- Insert Scheduled Sync Settings (group: "LDAP Settings")
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type, created_at, updated_at)
VALUES 
    ('LDAP Settings', 'scheduledSyncEnabled', 'false', 'boolean', NOW(), NOW()),
    ('LDAP Settings', 'scheduledSyncIntervalMinutes', '60', 'int', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    setting_value = VALUES(setting_value),
    updated_at = NOW();

-- Note:
-- - scheduledSyncEnabled: When true, automatic sync will run at configured interval
-- - scheduledSyncIntervalMinutes: Interval in minutes between syncs (default: 60 minutes)
--   Minimum recommended: 15 minutes
--   Maximum recommended: 1440 minutes (24 hours)

