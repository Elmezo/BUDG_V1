-- Migration: Client documents junction table (parity with capability_x_document)
-- Run after add_document_upload_support.sql if client_x_document is missing.

CREATE TABLE IF NOT EXISTS `client_x_document` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Client_ID` int(11) NOT NULL,
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
  KEY `fk_client_doc_client` (`Client_ID`),
  KEY `fk_client_doc_type` (`Document_Type_ID`),
  KEY `fk_client_doc_created` (`CreatedBy`),
  KEY `fk_client_doc_updated` (`Last_UpdatedUser_ID`),
  CONSTRAINT `fk_client_doc_client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_client_doc_type` FOREIGN KEY (`Document_Type_ID`) REFERENCES `document` (`ID`),
  CONSTRAINT `fk_client_doc_created` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_client_doc_updated` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
