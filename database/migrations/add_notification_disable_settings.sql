-- Migration: Add notification disable settings to system_settings
-- Purpose: Allow admins to disable email notifications for Tasks (Workflows) and Change Requests separately

-- Insert notification disable settings for Tasks (Workflows)
-- When enabled (true), no email notifications will be sent for workflow tasks
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('Notifications', 'disable_notification_emails_for_tasks', 'false', 'boolean')
ON DUPLICATE KEY UPDATE 
    setting_value = COALESCE(setting_value, 'false'),
    data_type = 'boolean';

-- Insert notification disable settings for Change Requests
-- When enabled (true), no email notifications will be sent for CR events (raised, cancelled, completed)
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('Notifications', 'disable_notification_emails_for_crs', 'false', 'boolean')
ON DUPLICATE KEY UPDATE 
    setting_value = COALESCE(setting_value, 'false'),
    data_type = 'boolean';

