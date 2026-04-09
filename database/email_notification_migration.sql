-- ============================================
-- Email Notification System Migration
-- Adds email delivery support to workflow notifications
-- ============================================

-- 1️⃣ Add delivery_mode to workflow_notification_rule table
ALTER TABLE workflow_notification_rule 
ADD COLUMN delivery_mode ENUM('IMMEDIATE', 'BATCHED') DEFAULT 'IMMEDIATE' 
AFTER channels;

-- 2️⃣ Create email_settings table for SMTP configuration
CREATE TABLE IF NOT EXISTS email_settings (
    id INT NOT NULL AUTO_INCREMENT,
    smtp_host VARCHAR(255) NOT NULL COMMENT 'SMTP server hostname',
    smtp_port INT NOT NULL COMMENT 'SMTP server port (e.g., 587 for TLS, 465 for SSL)',
    smtp_username VARCHAR(255) NOT NULL COMMENT 'SMTP authentication username',
    smtp_password VARCHAR(255) NOT NULL COMMENT 'SMTP authentication password (should be encrypted)',
    encryption ENUM('SSL', 'TLS') DEFAULT 'TLS' COMMENT 'Encryption method',
    enabled BOOLEAN DEFAULT FALSE COMMENT 'Global email enable/disable flag',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Insert default email settings (disabled by default)
INSERT INTO email_settings (smtp_host, smtp_port, smtp_username, smtp_password, encryption, enabled)
VALUES ('smtp.gmail.com', 587, '', '', 'TLS', FALSE)
ON DUPLICATE KEY UPDATE smtp_host = smtp_host;

-- 3️⃣ Create email_queue table for batched email delivery
CREATE TABLE IF NOT EXISTS email_queue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT DEFAULT NULL COMMENT 'FK to workflow_notification',
    recipient_user_id INT NOT NULL COMMENT 'Target user ID',
    subject VARCHAR(255) NOT NULL COMMENT 'Email subject',
    body TEXT NOT NULL COMMENT 'Email body (HTML)',
    status ENUM('PENDING', 'SENT', 'FAILED') DEFAULT 'PENDING',
    scheduled_at DATETIME NOT NULL COMMENT 'When to send this email (based on user frequency)',
    sent_at DATETIME DEFAULT NULL COMMENT 'When email was actually sent',
    error_message TEXT DEFAULT NULL COMMENT 'Error message if sending failed',
    retry_count INT DEFAULT 0 COMMENT 'Number of retry attempts',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_recipient_status (recipient_user_id, status, scheduled_at),
    KEY idx_scheduled (scheduled_at, status),
    KEY idx_notification (notification_id),
    CONSTRAINT fk_email_queue_notification FOREIGN KEY (notification_id) 
        REFERENCES workflow_notification(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 4️⃣ Enhance workflow_notification table
ALTER TABLE workflow_notification 
ADD COLUMN email_sent BOOLEAN DEFAULT FALSE 
AFTER channel;

ALTER TABLE workflow_notification 
ADD COLUMN email_sent_at DATETIME DEFAULT NULL 
AFTER email_sent;

-- 5️⃣ Update existing rules to have IMMEDIATE delivery mode (if column was just added)
UPDATE workflow_notification_rule 
SET delivery_mode = 'IMMEDIATE' 
WHERE delivery_mode IS NULL;

-- ============================================
-- End of Email Notification Migration
-- ============================================

































































