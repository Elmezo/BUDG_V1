-- ============================================
-- Clean Notification Rules Data
-- Remove duplicates and insert clean default rules
-- ============================================

-- Delete all existing rules
DELETE FROM workflow_notification_rule;

-- Reset auto increment
ALTER TABLE workflow_notification_rule AUTO_INCREMENT = 1;

-- Insert clean default notification rules

-- 1. ASSIGN: Notify all users with the task role when a task is assigned
INSERT INTO workflow_notification_rule (module, event_type, recipient_role, recipient_user_id, channels, delivery_mode, active, created_at, updated_at) 
VALUES ('*', 'ASSIGN', NULL, NULL, '["ui", "email"]', 'IMMEDIATE', TRUE, NOW(), NOW());

-- 2. OVERDUE: Notify users with the task role when a task becomes overdue
INSERT INTO workflow_notification_rule (module, event_type, recipient_role, recipient_user_id, channels, delivery_mode, active, created_at, updated_at) 
VALUES ('*', 'OVERDUE', NULL, NULL, '["ui", "email"]', 'IMMEDIATE', TRUE, NOW(), NOW());

-- 3. ESCALATION: Notify supervisors/managers when a task is escalated
INSERT INTO workflow_notification_rule (module, event_type, recipient_role, recipient_user_id, channels, delivery_mode, active, created_at, updated_at) 
VALUES ('*', 'ESCALATION', 'Supervisor', NULL, '["ui", "email"]', 'IMMEDIATE', TRUE, NOW(), NOW());

-- ============================================
-- End of Clean Notification Rules Data
-- ============================================
