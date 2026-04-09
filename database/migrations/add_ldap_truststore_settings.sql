-- ============================================
-- Add Truststore Settings to system_settings table
-- This migration adds settings for LDAPS certificate truststore/keystore
-- ============================================

-- Insert Truststore Settings (group: "LDAP Settings")
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type, created_at, updated_at)
VALUES 
    ('LDAP Settings', 'truststorePath', NULL, 'string', NOW(), NOW()),
    ('LDAP Settings', 'truststorePassword', NULL, 'string', NOW(), NOW()),
    ('LDAP Settings', 'trustAllCertificates', 'false', 'boolean', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    setting_value = VALUES(setting_value),
    updated_at = NOW();

-- Note:
-- - truststorePath: Path to truststore file (JKS or PKCS12 format)
-- - truststorePassword: Password for truststore file
-- - trustAllCertificates: When true, accepts all certificates (not recommended for production)
--   Default: false (requires valid certificates or truststore)

