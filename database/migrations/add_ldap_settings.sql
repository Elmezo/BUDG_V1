-- ============================================
-- Add LDAP Settings to system_settings table
-- This migration adds LDAP configuration settings
-- ============================================

-- Insert LDAP Settings (group: "LDAP Settings")
-- Note: bindPassword will be encrypted when saved via UI

INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type, created_at, updated_at)
VALUES 
    ('LDAP Settings', 'ldapEnabled', 'false', 'boolean', NOW(), NOW()),
    ('LDAP Settings', 'ldapUrl', '', 'string', NOW(), NOW()),
    ('LDAP Settings', 'baseDn', '', 'string', NOW(), NOW()),
    ('LDAP Settings', 'bindDn', '', 'string', NOW(), NOW()),
    ('LDAP Settings', 'bindPassword', NULL, 'string', NOW(), NOW()),
    ('LDAP Settings', 'userSearchBase', '', 'string', NOW(), NOW()),
    ('LDAP Settings', 'userSearchFilter', '(uid={0})', 'string', NOW(), NOW()),
    ('LDAP Settings', 'groupSearchBase', '', 'string', NOW(), NOW()),
    ('LDAP Settings', 'connectionTimeout', '5000', 'int', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    setting_value = VALUES(setting_value),
    updated_at = NOW();

-- If environment variables exist, migrate them to database
-- Note: This is a one-time migration. bindPassword from env will need to be encrypted via UI

-- Update ldapEnabled from environment if exists
UPDATE system_settings 
SET setting_value = COALESCE(
    (SELECT CASE 
        WHEN @ldap_enabled IS NOT NULL THEN @ldap_enabled
        ELSE setting_value
    END),
    'false'
)
WHERE setting_group = 'LDAP Settings' AND setting_key = 'ldapEnabled';

-- Note: For production, administrators should:
-- 1. Set LDAP settings via System Settings UI
-- 2. Test connection before enabling LDAP
-- 3. bindPassword will be automatically encrypted when saved via UI

