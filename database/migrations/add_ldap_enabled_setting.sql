-- ============================================
-- Add LDAP_ENABLED setting to Environment group
-- This setting controls whether LDAP authentication is enabled
-- ============================================

-- Insert LDAP_ENABLED setting with default value 'true'
-- The value will be synced with .env file when changed via System Settings
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('Environment', 'LDAP_ENABLED', 'true', 'boolean')
ON DUPLICATE KEY UPDATE 
    data_type = 'boolean';

-- Note: The actual value should match the .env file (LDAP_ENABLED=true or LDAP_ENABLED=false)
-- This migration sets a default of 'true', but administrators should verify it matches their .env file

