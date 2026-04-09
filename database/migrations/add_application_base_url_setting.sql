-- Migration: Add application_base_url setting to system_settings
-- Purpose: Configure base URL for email links in role notifications

-- Insert application base URL setting (default: http://localhost:8080)
-- Admin can update this via System Settings in Admin Panel
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('Environment', 'application_base_url', 'http://localhost:8080', 'string')
ON DUPLICATE KEY UPDATE 
    setting_value = COALESCE(setting_value, 'http://localhost:8080'),
    data_type = 'string';
