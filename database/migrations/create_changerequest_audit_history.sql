-- Migration: Create changerequest_audit_history table
-- Description: Stores audit history for Change Request field changes (Field, From, To, Author, Date)
-- Date: 2026-03-10

CREATE TABLE IF NOT EXISTS `changerequest_audit_history` (
  `id` int(11) NOT NULL COMMENT 'FK to changerequest.ID',
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(500) DEFAULT NULL,
  `to` varchar(500) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`auditidpk`),
  KEY `idx_cr_audit_id` (`id`),
  CONSTRAINT `fk_cr_audit_cr` FOREIGN KEY (`id`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
