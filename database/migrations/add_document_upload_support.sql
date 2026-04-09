-- Migration: Add Document Upload Support
-- Description: Creates document type lookup table and facet-specific document tables
-- Date: 2026-01-13

-- ============================================
-- 1. Create document type lookup table
-- ============================================
CREATE TABLE IF NOT EXISTS `document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) NOT NULL,
  `Description` text DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_document_user` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_document_user` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================
-- 2. Insert document types
-- ============================================
INSERT INTO `document` (`PrimaryName`, `Description`) VALUES
('Data Documentation', 'Documentation related to data structures and definitions'),
('Policy Documentation', 'Policy-related documentation'),
('Process Documentation', 'Process flow and procedure documentation'),
('Reference Documentation', 'Reference materials and guides'),
('Sample Report', 'Sample reports and templates'),
('Project Documentation', 'Project-related documentation'),
('System Documentation', 'Technical system documentation'),
('Interface Documentation', 'API and interface specifications'),
('Data Requirements Documentation', 'Data requirement specifications'),
('Sample Data', 'Sample datasets and examples'),
('BRD', 'Business Requirements Document'),
('FSD', 'Functional Specification Document'),
('System Functional Architecture', 'System functional architecture documentation'),
('System Technical Architecture', 'System technical architecture documentation'),
('Project Charter', 'Project charter documents'),
('Process Control Documentation', 'Process control and monitoring documentation'),
('Interface Design Documentation', 'Interface design specifications'),
('Message Format Specification', 'Message format and protocol specifications');

-- ============================================
-- 3. Create facet-specific document tables
-- ============================================

-- System Documents
CREATE TABLE IF NOT EXISTS `system_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `System_ID` int(11) NOT NULL,
  `Document_Type_ID` int(11) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Description` text NOT NULL,
  `File_Path` varchar(500) NOT NULL COMMENT 'File path for uploaded files or URL for linked documents',
  `File_Name` varchar(255) NOT NULL,
  `Is_URL` tinyint(1) DEFAULT 0 COMMENT '1 if URL, 0 if uploaded file',
  `File_Size` bigint DEFAULT NULL COMMENT 'Size in bytes, NULL for URLs',
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_sys_doc_system` (`System_ID`),
  KEY `fk_sys_doc_type` (`Document_Type_ID`),
  KEY `fk_sys_doc_created` (`CreatedBy`),
  KEY `fk_sys_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_sys_doc_system` FOREIGN KEY (`System_ID`) REFERENCES `system` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sys_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_sys_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_sys_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Dataset Documents
CREATE TABLE IF NOT EXISTS `dataset_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Dataset_ID` int(11) NOT NULL,
  `Document_Type_ID` int(11) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Description` text NOT NULL,
  `File_Path` varchar(500) NOT NULL,
  `File_Name` varchar(255) NOT NULL,
  `Is_URL` tinyint(1) DEFAULT 0,
  `File_Size` bigint DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_ds_doc_dataset` (`Dataset_ID`),
  KEY `fk_ds_doc_type` (`Document_Type_ID`),
  KEY `fk_ds_doc_created` (`CreatedBy`),
  KEY `fk_ds_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_ds_doc_dataset` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_ds_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_ds_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_ds_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Glossary Documents
CREATE TABLE IF NOT EXISTS `glossary_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Glossary_ID` int(11) NOT NULL,
  `Document_Type_ID` int(11) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Description` text NOT NULL,
  `File_Path` varchar(500) NOT NULL,
  `File_Name` varchar(255) NOT NULL,
  `Is_URL` tinyint(1) DEFAULT 0,
  `File_Size` bigint DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_gls_doc_glossary` (`Glossary_ID`),
  KEY `fk_gls_doc_type` (`Document_Type_ID`),
  KEY `fk_gls_doc_created` (`CreatedBy`),
  KEY `fk_gls_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_gls_doc_glossary` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_gls_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_gls_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_gls_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Process Documents
CREATE TABLE IF NOT EXISTS `process_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Process_ID` int(11) NOT NULL,
  `Document_Type_ID` int(11) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Description` text NOT NULL,
  `File_Path` varchar(500) NOT NULL,
  `File_Name` varchar(255) NOT NULL,
  `Is_URL` tinyint(1) DEFAULT 0,
  `File_Size` bigint DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_proc_doc_process` (`Process_ID`),
  KEY `fk_proc_doc_type` (`Document_Type_ID`),
  KEY `fk_proc_doc_created` (`CreatedBy`),
  KEY `fk_proc_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_proc_doc_process` FOREIGN KEY (`Process_ID`) REFERENCES `process` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_proc_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_proc_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_proc_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Project Documents
CREATE TABLE IF NOT EXISTS `project_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Project_ID` int(11) NOT NULL,
  `Document_Type_ID` int(11) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Description` text NOT NULL,
  `File_Path` varchar(500) NOT NULL,
  `File_Name` varchar(255) NOT NULL,
  `Is_URL` tinyint(1) DEFAULT 0,
  `File_Size` bigint DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_proj_doc_project` (`Project_ID`),
  KEY `fk_proj_doc_type` (`Document_Type_ID`),
  KEY `fk_proj_doc_created` (`CreatedBy`),
  KEY `fk_proj_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_proj_doc_project` FOREIGN KEY (`Project_ID`) REFERENCES `project` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_proj_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_proj_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_proj_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Capability Documents
CREATE TABLE IF NOT EXISTS `capability_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) NOT NULL,
  `Document_Type_ID` int(11) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Description` text NOT NULL,
  `File_Path` varchar(500) NOT NULL,
  `File_Name` varchar(255) NOT NULL,
  `Is_URL` tinyint(1) DEFAULT 0,
  `File_Size` bigint DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_cap_doc_capability` (`Capability_ID`),
  KEY `fk_cap_doc_type` (`Document_Type_ID`),
  KEY `fk_cap_doc_created` (`CreatedBy`),
  KEY `fk_cap_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_cap_doc_capability` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_cap_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_cap_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_cap_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Change Request Documents
CREATE TABLE IF NOT EXISTS `changerequest_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `ChangeRequest_ID` int(11) NOT NULL,
  `Document_Type_ID` int(11) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Description` text NOT NULL,
  `File_Path` varchar(500) NOT NULL,
  `File_Name` varchar(255) NOT NULL,
  `Is_URL` tinyint(1) DEFAULT 0,
  `File_Size` bigint DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_cr_doc_changerequest` (`ChangeRequest_ID`),
  KEY `fk_cr_doc_type` (`Document_Type_ID`),
  KEY `fk_cr_doc_created` (`CreatedBy`),
  KEY `fk_cr_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_cr_doc_changerequest` FOREIGN KEY (`ChangeRequest_ID`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_cr_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_cr_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_cr_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Interface Documents
CREATE TABLE IF NOT EXISTS `interface_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Interface_ID` int(11) NOT NULL,
  `Document_Type_ID` int(11) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Description` text NOT NULL,
  `File_Path` varchar(500) NOT NULL,
  `File_Name` varchar(255) NOT NULL,
  `Is_URL` tinyint(1) DEFAULT 0,
  `File_Size` bigint DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT CURRENT_TIMESTAMP(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_int_doc_interface` (`Interface_ID`),
  KEY `fk_int_doc_type` (`Document_Type_ID`),
  KEY `fk_int_doc_created` (`CreatedBy`),
  KEY `fk_int_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_int_doc_interface` FOREIGN KEY (`Interface_ID`) REFERENCES `interface` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_int_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_int_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_int_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
