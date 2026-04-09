-- Migration script to add missing columns and foreign keys to job table
-- Based on the bulk upload requirements

-- Add Child_Jobs_Order column if it doesn't exist
ALTER TABLE `job` 
ADD COLUMN IF NOT EXISTS `Child_Jobs_Order` int(11) DEFAULT NULL 
COMMENT 'ترتيب الوظائف الفرعية لو العملية كبيرة ومقسومة على أكثر من جزء';

-- Add foreign key columns (optional - relationships exist in child tables)
-- Note: These are optional as the relationships are already established via:
-- - job_resource_filename.JobID -> job.ID
-- - job_progress.Job_ID -> job.ID  
-- - job_report_item.Job_ID -> job.ID
-- If you want explicit foreign keys in job table, uncomment below:

-- ALTER TABLE `job` 
-- ADD COLUMN IF NOT EXISTS `File_Job` int(11) DEFAULT NULL,
-- ADD COLUMN IF NOT EXISTS `Progress_Job` int(11) DEFAULT NULL,
-- ADD COLUMN IF NOT EXISTS `Report_Job` int(11) DEFAULT NULL,
-- ADD COLUMN IF NOT EXISTS `Meta_data_job_id` int(11) DEFAULT NULL,
-- ADD COLUMN IF NOT EXISTS `Parent_job_id` int(11) DEFAULT NULL;

-- Add indexes for foreign keys if columns are added
-- ALTER TABLE `job`
-- ADD KEY IF NOT EXISTS `File_Job` (`File_Job`),
-- ADD KEY IF NOT EXISTS `Progress_Job` (`Progress_Job`),
-- ADD KEY IF NOT EXISTS `Report_Job` (`Report_Job`),
-- ADD KEY IF NOT EXISTS `Meta_data_job_id` (`Meta_data_job_id`),
-- ADD KEY IF NOT EXISTS `Parent_job_id` (`Parent_job_id`);

-- Add foreign key constraints if columns are added
-- ALTER TABLE `job`
-- ADD CONSTRAINT `fk_job_file` FOREIGN KEY (`File_Job`) REFERENCES `job_resource_filename` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
-- ADD CONSTRAINT `fk_job_progress` FOREIGN KEY (`Progress_Job`) REFERENCES `job_progress` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
-- ADD CONSTRAINT `fk_job_report` FOREIGN KEY (`Report_Job`) REFERENCES `job_report_item` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE;

-- Note: Meta_data_job_id and Parent_job_id require job_meta_data and i_batch_job tables
-- which may need to be created separately if they don't exist

