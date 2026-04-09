-- ============================================
-- Fix: Data Truncation for event_type
-- ============================================
-- The event_type column was defined as ENUM('ASSIGN', 'OVERDUE', 'ESCALATION')
-- but the application is trying to insert 'MENTION', 'CR_START', 'STEP_COMPLETED'.
-- This migration converts the column to VARCHAR(50) to support dynamic event types.

ALTER TABLE workflow_notification MODIFY COLUMN event_type VARCHAR(50) NOT NULL;
ALTER TABLE workflow_notification_rule MODIFY COLUMN event_type VARCHAR(50) NOT NULL;
