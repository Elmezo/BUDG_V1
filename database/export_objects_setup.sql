-- Export Objects Feature - Database Setup
-- Add configuration for People export functionality

-- Insert default export configuration (disabled by default)
-- Note: app_config table only has config_key and definition columns
INSERT INTO app_config (config_key, definition)
VALUES ('EXPORT_PEOPLE_ENABLED', 'false')
ON DUPLICATE KEY UPDATE definition = definition;

-- Verify the configuration was added
SELECT * FROM app_config WHERE config_key = 'EXPORT_PEOPLE_ENABLED';
