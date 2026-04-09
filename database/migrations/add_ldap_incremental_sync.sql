-- ============================================
-- Add Incremental Sync Settings to system_settings table
-- This migration adds settings for incremental LDAP synchronization
-- ============================================

-- Insert Incremental Sync Settings (group: "LDAP Settings")
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type, created_at, updated_at)
VALUES 
    ('LDAP Settings', 'incrementalSyncEnabled', 'false', 'boolean', NOW(), NOW()),
    ('LDAP Settings', 'lastSyncTimestamp', NULL, 'string', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    setting_value = VALUES(setting_value),
    updated_at = NOW();

-- Note: 
-- - incrementalSyncEnabled: When true, only users modified since lastSyncTimestamp will be synced
-- - lastSyncTimestamp: ISO 8601 format timestamp (e.g., "2024-01-15T10:30:00Z")
--   This will be automatically updated after each successful sync

