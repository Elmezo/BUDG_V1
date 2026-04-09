-- ============================================
-- Phase 4.2: Workflow Notification Rules Schema
-- Dynamic notification configuration system (BUDG-like)
-- ============================================

-- 1️⃣ Workflow Notification Rules Table
CREATE TABLE IF NOT EXISTS workflow_notification_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    module VARCHAR(255) NOT NULL COMMENT 'Workflow name (Process Definition PrimaryName) or "*" for all workflows',
    event_type VARCHAR(50) NOT NULL COMMENT 'Event type: ASSIGN, OVERDUE, ESCALATION, MENTION, etc',
    recipient_role VARCHAR(100) DEFAULT NULL COMMENT 'Target role name',
    recipient_user_id BIGINT DEFAULT NULL COMMENT 'Specific user ID (optional)',
    channels JSON NOT NULL COMMENT 'Notification channels: ["email","ui","sms"]',
    active BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_module_event (module, event_type, active),
    KEY idx_recipient (recipient_role, recipient_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 2️⃣ Workflow Notifications Table (for UI storage)
CREATE TABLE IF NOT EXISTS workflow_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_rule_id BIGINT DEFAULT NULL COMMENT 'FK to workflow_notification_rule',
    workflow_task_id INT NOT NULL COMMENT 'FK to workflow_instance_task',
    change_request_id INT DEFAULT NULL COMMENT 'FK to changerequest',
    recipient_user_id INT NOT NULL COMMENT 'Target user ID',
    event_type VARCHAR(50) NOT NULL COMMENT 'Event type: ASSIGN, OVERDUE, ESCALATION, MENTION, etc',
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    channel VARCHAR(50) NOT NULL COMMENT 'Channel: ui, email, sms',
    `read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Read flag (reserved word: quoted)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_recipient_unread (recipient_user_id, `read`, created_at),
    KEY idx_task (workflow_task_id),
    KEY idx_change_request (change_request_id),
    CONSTRAINT fk_notification_task FOREIGN KEY (workflow_task_id) 
        REFERENCES workflow_instance_task(ID) ON DELETE CASCADE,
    CONSTRAINT fk_notification_rule FOREIGN KEY (notification_rule_id) 
        REFERENCES workflow_notification_rule(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 3️⃣ Default Notification Rules
-- ASSIGN: Notify all users with the task role
INSERT INTO workflow_notification_rule (module, event_type, recipient_role, recipient_user_id, channels, active) 
VALUES ('*', 'ASSIGN', NULL, NULL, '["ui", "email"]', TRUE);

-- OVERDUE: Notify users with the task role
INSERT INTO workflow_notification_rule (module, event_type, recipient_role, recipient_user_id, channels, active) 
VALUES ('*', 'OVERDUE', NULL, NULL, '["ui", "email"]', TRUE);

-- ESCALATION: Notify supervisors/managers
INSERT INTO workflow_notification_rule (module, event_type, recipient_role, recipient_user_id, channels, active) 
VALUES ('*', 'ESCALATION', 'Supervisor', NULL, '["ui", "email"]', TRUE);

-- ============================================
-- End of Notification Rules Schema
-- ============================================

