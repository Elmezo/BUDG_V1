-- ============================================
-- Phase 5: Parallel Gateway Support Migration
-- Adds Parent_Gateway_ID column to track tasks created by parallel gateways
-- ============================================

-- Add Parent_Gateway_ID column to workflow_instance_task table
ALTER TABLE workflow_instance_task
ADD COLUMN IF NOT EXISTS Parent_Gateway_ID varchar(255) DEFAULT NULL AFTER Bpmn_Node_Id;

-- Add index for efficient queries on parallel gateway tasks
CREATE INDEX IF NOT EXISTS idx_parent_gateway 
ON workflow_instance_task(Parent_Gateway_ID, Workflow_Instance_ID);

-- Note: Parent_Gateway_ID stores the BPMN node ID of the parallel gateway
-- NULL indicates the task was not created by a parallel gateway
-- This maintains backward compatibility with existing tasks

































































