-- ============================================
-- Add LDAP-specific columns to people table
-- This migration adds columns needed for LDAP user tracking
-- ============================================

-- Add LDAP-specific columns
ALTER TABLE people 
ADD COLUMN external_id VARCHAR(500) NULL COMMENT 'LDAP DN or external identifier',
ADD COLUMN auth_source ENUM('LOCAL', 'LDAP') DEFAULT 'LOCAL' COMMENT 'Authentication source',
ADD COLUMN last_synced_at DATETIME NULL COMMENT 'Last LDAP synchronization timestamp';

-- Add indexes for performance
ALTER TABLE people 
ADD INDEX idx_external_id (external_id),
ADD INDEX idx_auth_source (auth_source),
ADD INDEX idx_last_synced_at (last_synced_at);

-- Update existing LDAP users (if any)
-- Identify LDAP users by password constant or source_id
UPDATE people p
LEFT JOIN people_source ps ON p.source_id = ps.id
SET p.auth_source = 'LDAP'
WHERE p.Password = 'LDAP_AUTH_REQUIRED_#@!$%^&*()' 
   OR (ps.source = 'LDAP' AND ps.source IS NOT NULL);

-- Note: external_id and last_synced_at will be populated during next sync

