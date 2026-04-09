-- Account Locking Feature - Database Schema Changes
-- Add lock tracking columns to people table

USE budg_v2;

-- Add new columns for account locking
ALTER TABLE people 
ADD COLUMN is_locked TINYINT(1) DEFAULT 0 COMMENT 'Account lock status: 0=unlocked, 1=locked',
ADD COLUMN locked_date DATETIME DEFAULT NULL COMMENT 'Timestamp when account was locked',
ADD COLUMN lock_reason VARCHAR(255) DEFAULT NULL COMMENT 'Reason for account lock';

-- Add index for efficient locked user queries
CREATE INDEX idx_is_locked ON people(is_locked, locked_date);

-- Verify changes
DESCRIBE people;
