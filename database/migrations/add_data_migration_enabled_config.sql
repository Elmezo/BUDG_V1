-- ============================================
-- Add DATA_MIGRATION_ENABLED configuration to app_config table
-- This flag controls visibility of Data Migration features
-- Default: false (disabled)
-- ============================================

INSERT INTO app_config (config_key, definition)
VALUES ('DATA_MIGRATION_ENABLED', 'false')
ON DUPLICATE KEY UPDATE definition = 'false';

