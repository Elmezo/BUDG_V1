-- ============================================
-- Phase 4: SLA Migration Script
-- Adds Due_At, Is_Overdue, Escalated_At columns
-- Migrates existing tasks
-- ============================================

-- Add new SLA columns
ALTER TABLE workflow_instance_task
ADD COLUMN IF NOT EXISTS Due_At datetime DEFAULT NULL,
ADD COLUMN IF NOT EXISTS Is_Overdue tinyint(1) DEFAULT 0,
ADD COLUMN IF NOT EXISTS Escalated_At datetime DEFAULT NULL;

-- Add index for SLA queries
CREATE INDEX IF NOT EXISTS idx_sla_evaluation 
ON workflow_instance_task(Status, Due_At, Is_Overdue, Escalated_At);

-- Migration for existing tasks:
-- Copy Due_Date to Due_At if Due_Date exists and Due_At is NULL
UPDATE workflow_instance_task 
SET Due_At = Due_Date 
WHERE Due_Date IS NOT NULL AND Due_At IS NULL;

-- For tasks without Due_Date, set default based on Assigned_At + default dueDays (5 days)
-- This ensures all existing tasks have a Due_At value
UPDATE workflow_instance_task 
SET Due_At = DATE_ADD(Assigned_At, INTERVAL 5 DAY)
WHERE Due_At IS NULL AND Assigned_At IS NOT NULL AND Status IN ('Pending', 'InProgress');

-- Mark existing overdue tasks
UPDATE workflow_instance_task 
SET Is_Overdue = 1 
WHERE Due_At IS NOT NULL 
  AND Due_At < NOW() 
  AND Status IN ('Pending', 'InProgress') 
  AND Is_Overdue = 0;

-- Set Escalated_At = NULL for all existing tasks (escalation only applies to new overdue tasks)
UPDATE workflow_instance_task 
SET Escalated_At = NULL 
WHERE Escalated_At IS NOT NULL;

