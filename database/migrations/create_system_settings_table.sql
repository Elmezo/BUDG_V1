-- ============================================
-- Create system_settings table
-- This table stores system-wide configuration settings organized by groups
-- ============================================

CREATE TABLE IF NOT EXISTS system_settings (
    id INT NOT NULL AUTO_INCREMENT,
    setting_group VARCHAR(50) NOT NULL COMMENT 'Settings group: Environment, General, etc.',
    setting_key VARCHAR(100) NOT NULL COMMENT 'Setting key identifier',
    setting_value VARCHAR(255) DEFAULT NULL COMMENT 'Setting value as string',
    data_type VARCHAR(20) NOT NULL DEFAULT 'string' COMMENT 'Data type: int, string, boolean',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_setting_group_key (setting_group, setting_key),
    KEY idx_setting_group (setting_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
COMMENT='System-wide configuration settings organized by groups';

-- Insert default value for Clear Notifications setting
-- Value of 0 means automatic clearing is disabled
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('Environment', 'clear_notifications_days', '0', 'int')
ON DUPLICATE KEY UPDATE setting_value = '0';
