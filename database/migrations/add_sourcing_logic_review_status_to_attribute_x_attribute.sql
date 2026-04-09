-- Migration: Add Sourcing_Logic and Review_Status columns to attribute_x_attribute table
-- Date: 2024

-- Add Sourcing_Logic column
ALTER TABLE `attribute_x_attribute` 
ADD COLUMN `Sourcing_Logic` TEXT NULL DEFAULT NULL AFTER `Relation_Method`;

-- Add Review_Status column
ALTER TABLE `attribute_x_attribute` 
ADD COLUMN `Review_Status` VARCHAR(255) NULL DEFAULT NULL AFTER `Sourcing_Logic`;

-- Update audit table to include new columns
ALTER TABLE `attribute_x_attribute_audit` 
ADD COLUMN `Sourcing_Logic` TEXT NULL DEFAULT NULL AFTER `Relation_Method`;

ALTER TABLE `attribute_x_attribute_audit` 
ADD COLUMN `Review_Status` VARCHAR(255) NULL DEFAULT NULL AFTER `Sourcing_Logic`;

