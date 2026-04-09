-- Migration: Add object_id and facet_type columns to workflow_notification table
-- Purpose: Support role notifications with object references

-- Add object_id column (check if exists first to avoid errors on re-run)
SET @dbname = DATABASE();
SET @tablename = 'workflow_notification';
SET @columnname = 'object_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' INT DEFAULT NULL COMMENT ''ID of the object for role notifications (dataset, system, etc.)''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add facet_type column (check if exists first to avoid errors on re-run)
SET @columnname = 'facet_type';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(50) DEFAULT NULL COMMENT ''Type of facet for role notifications (e.g., "Data Set", "System", "Glossary")''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Make workflow_task_id nullable to support role notifications (which don't have workflow tasks)
-- First, drop the foreign key constraint if it exists (required before modifying column)
SET @fkname = 'fk_notification_task';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (CONSTRAINT_NAME = @fkname)
  ) > 0,
  CONCAT('ALTER TABLE ', @tablename, ' DROP FOREIGN KEY ', @fkname),
  'SELECT 1'
));
PREPARE dropFkIfExists FROM @preparedStatement;
EXECUTE dropFkIfExists;
DEALLOCATE PREPARE dropFkIfExists;

-- Now modify workflow_task_id to allow NULL (only if currently NOT NULL)
SET @columnname = 'workflow_task_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) = 'NO',
  CONCAT('ALTER TABLE ', @tablename, ' MODIFY COLUMN ', @columnname, ' INT DEFAULT NULL COMMENT ''FK to workflow_instance_task (NULL for role notifications)'''),
  'SELECT 1'
));
PREPARE modifyIfNotNull FROM @preparedStatement;
EXECUTE modifyIfNotNull;
DEALLOCATE PREPARE modifyIfNotNull;

-- Re-add the foreign key constraint (now allowing NULL values)
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (CONSTRAINT_NAME = @fkname)
  ) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD CONSTRAINT ', @fkname, ' FOREIGN KEY (workflow_task_id) REFERENCES workflow_instance_task(ID) ON DELETE CASCADE'),
  'SELECT 1'
));
PREPARE addFkIfNotExists FROM @preparedStatement;
EXECUTE addFkIfNotExists;
DEALLOCATE PREPARE addFkIfNotExists;

-- Add index for faster queries on role notifications (check if exists first)
SET @indexname = 'idx_workflow_notification_object_facet';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (INDEX_NAME = @indexname)
  ) > 0,
  'SELECT 1',
  CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, ' (object_id, facet_type)')
));
PREPARE createIndexIfNotExists FROM @preparedStatement;
EXECUTE createIndexIfNotExists;
DEALLOCATE PREPARE createIndexIfNotExists;
