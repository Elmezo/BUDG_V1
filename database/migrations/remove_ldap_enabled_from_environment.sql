-- ============================================
-- Remove LDAP_ENABLED setting from Environment group
-- This setting is now only available in LDAP Settings group (as ldapEnabled)
-- ============================================

-- Delete LDAP_ENABLED setting from Environment group
DELETE FROM system_settings 
WHERE setting_group = 'Environment' AND setting_key = 'LDAP_ENABLED';

-- Note: LDAP enable/disable functionality is now exclusively managed through
-- the "LDAP Settings" group with the key "ldapEnabled"

