-- ============================================
-- Add User Handling Settings to system_settings table
-- This migration adds settings for handling disabled/deleted LDAP users
-- ============================================

-- Insert User Handling Settings (group: "LDAP Settings")
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type, created_at, updated_at)
VALUES 
    ('LDAP Settings', 'autoDisableMissingUsers', 'false', 'boolean', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    setting_value = VALUES(setting_value),
    updated_at = NOW();

-- Note:
-- - autoDisableMissingUsers: When true, users that are missing from LDAP or disabled in LDAP
--   will be automatically disabled in the local database (status_id set to disabled status)
--   Default: false (users remain enabled even if missing from LDAP)

