-- ============================================
-- Create edc_integration_settings table
-- This table stores Enterprise Data Catalog (EDC) integration settings
-- Uses dedicated table to avoid modifying system_settings schema
-- ============================================

CREATE TABLE IF NOT EXISTS edc_integration_settings (
    id INT NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(100) NOT NULL COMMENT 'Setting key identifier (e.g., eic_server_host)',
    setting_value TEXT DEFAULT NULL COMMENT 'Setting value (encrypted for passwords)',
    is_encrypted BOOLEAN DEFAULT FALSE COMMENT 'Whether the value is encrypted',
    updated_by INT DEFAULT NULL COMMENT 'User ID who last updated this setting',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_setting_key (setting_key),
    KEY idx_updated_at (updated_at),
    CONSTRAINT fk_edc_settings_updated_by FOREIGN KEY (updated_by) REFERENCES people(ID) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
COMMENT='Enterprise Data Catalog integration settings';

-- Insert default values for EDC settings
INSERT INTO edc_integration_settings (setting_key, setting_value, is_encrypted) VALUES
('eic_server_host', 'https://edc.local', FALSE),
('eic_server_port', '9185', FALSE),
('eic_server_login_username', 'Administrator', FALSE),
('eic_server_login_password', NULL, FALSE),
('eic_server_login_namespace', 'Native', FALSE),
('eic_axon_resource_name', 'BUDG_Resource_copy', FALSE),
('eic_enable_auto_lineage_recommendation', 'true', FALSE),
('eic_axon_super_admin_email', 'admin@budg.com', FALSE),
('eic_enable_lineage_email_notification', 'true', FALSE),
('eic_enable_custom_attributes', 'true', FALSE),
('eic_enable_cleanup_lineage_recommendations', 'true', FALSE),
('eic_enable_filter', 'false', FALSE),
('eic_update_onboarded_assets', 'true', FALSE),
('eic_default_glossary', '', FALSE),
('eic_request_timeout', '120', FALSE),
('eic_proxy_host', '', FALSE),
('eic_proxy_port', '', FALSE),
('eic_ssl_insecure', 'false', FALSE)
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

