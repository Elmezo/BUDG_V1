-- ============================================
-- Add category column to workflow_notification table
-- This allows separating notifications by category: workflow, catalog, roles, bulk_upload
-- ============================================

-- Add category column to workflow_notification table
ALTER TABLE workflow_notification 
ADD COLUMN category VARCHAR(50) DEFAULT 'workflow' 
COMMENT 'Category: workflow, catalog, roles, bulk_upload' 
AFTER channel;

-- Update existing notifications to have 'workflow' category
UPDATE workflow_notification 
SET category = 'workflow' 
WHERE category IS NULL OR category = '';

-- Add index for better query performance
CREATE INDEX idx_category_recipient_unread ON workflow_notification(category, recipient_user_id, `read`, created_at);

-- Make category NOT NULL after setting default values
ALTER TABLE workflow_notification 
MODIFY COLUMN category VARCHAR(50) NOT NULL DEFAULT 'workflow';
