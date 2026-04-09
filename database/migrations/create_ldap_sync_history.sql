-- ============================================
-- Create LDAP Sync History Table
-- This table stores history of LDAP synchronization runs
-- ============================================

CREATE TABLE IF NOT EXISTS ldap_sync_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    job_id INT NOT NULL COMMENT 'Reference to job table',
    started_at DATETIME NOT NULL COMMENT 'When sync started',
    completed_at DATETIME NULL COMMENT 'When sync completed (NULL if still running or failed)',
    status VARCHAR(20) NOT NULL COMMENT 'Status: Running, Completed, Failed, Cancelled',
    users_fetched INT DEFAULT 0 COMMENT 'Number of users fetched from LDAP',
    users_added INT DEFAULT 0 COMMENT 'Number of new users added',
    users_updated INT DEFAULT 0 COMMENT 'Number of existing users updated',
    users_skipped INT DEFAULT 0 COMMENT 'Number of users skipped',
    users_disabled INT DEFAULT 0 COMMENT 'Number of users disabled (missing from LDAP)',
    org_units_created INT DEFAULT 0 COMMENT 'Number of org units created',
    org_units_updated INT DEFAULT 0 COMMENT 'Number of org units updated',
    error_message TEXT NULL COMMENT 'Error message if sync failed',
    created_by INT NOT NULL COMMENT 'User ID who initiated the sync',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_job_id (job_id),
    INDEX idx_completed_at (completed_at),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (job_id) REFERENCES job(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
COMMENT='History of LDAP synchronization runs';

